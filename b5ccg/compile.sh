#!/bin/bash
# Compile the B5 CCG Java project (JDK 25 --release 8, stdlib only; Hop 1 per B5-0007/D2).
set -e

# Resolve the directory this script lives in, then work with RELATIVE paths.
# Rationale (B5-0306): the previous version computed absolute POSIX paths
# (/c/...) via `pwd` and passed them to the native Windows javac, which
# cannot read MSYS-style paths; CRLF line endings additionally broke
# stricter shells. Relative paths from the script directory need no
# translation and work under sh, bash, and MSYS/Git Bash alike.
cd "$(dirname "$0")" || exit 1

# B5-XXXX build isolation: per-session output directory. B5CCG_OUT_DIR env var
# overrides the default "out"; unset (default) keeps the shared single-agent
# flow unchanged.
SRC="src"
OUT="${B5CCG_OUT_DIR:-out}"
RES="resources"

# Build isolation lock. Acquired before anything touches $OUT (including rm -rf)
# and released on success AND failure via the trap below. Uses mkdir atomicity
# rather than flock because flock is not guaranteed on this host (Windows/Git
# Bash). A waiting build retries for up to 60s then fails rather than clobbering
# a concurrent build's output.
LOCK_DIR="b5ccg_build.lock.dir"
acquire_lock() {
  local i=0
  while true; do
    if mkdir "$LOCK_DIR" 2>/dev/null; then
      return 0
    fi
    i=$((i+1))
    if [ "$i" -ge 60 ]; then
      echo "ERROR: Could not acquire build lock $LOCK_DIR (another build in progress?)"
      return 1
    fi
    sleep 1
  done
}

release_lock() {
  rm -rf "$LOCK_DIR" 2>/dev/null || true
}

LIST=""
trap 'release_lock; [ -n "${LIST:-}" ] && rm -f "$LIST"' EXIT INT TERM

echo "=== B5 CCG Build ==="
echo "Source: $(pwd)/$SRC"
echo "Output: $(pwd)/$OUT"
if [ -n "${B5CCG_OUT_DIR:-}" ]; then
  echo "Output override: B5CCG_OUT_DIR=$B5CCG_OUT_DIR (session-private output dir)"
fi

# Build isolation: lock before cleaning out/. Serialises concurrent builds so one
# cannot rm -rf out/ under another. Default B5CCG_OUT_DIR=out serialises default
# builds on this lock; a session setting B5CCG_OUT_DIR to its own dir still
# serialises on the single lock (acceptable) and builds into its own directory.
acquire_lock || exit 1

# B5-3487: clean out/ BEFORE compiling, for the same reason compile.bat does.
# javac emits a .class for every source that compiled before the first error
# batch, so a red build leaves out/ half-populated; run.sh's guard can then only
# tell a good build from a bad one by asking whether the build marker is present,
# which is why compile.bat writes it last.
rm -rf "$OUT"
mkdir -p "$OUT"

# B5-0016 (b): the file list is scratch, written INSIDE b5ccg/ as a RELATIVE
# path and removed on both the success and the failure path by the trap below.
LIST="b5ccg_compile_list.tmp"

# Collect all .java files (relative; one per line in the response file)
JAVA_FILES=$(find "$SRC" -name "*.java")

if [ -z "$JAVA_FILES" ]; then
  echo "ERROR: No .java files found in $SRC"
  exit 1
fi

printf '%s\n' "$JAVA_FILES" > "$LIST"

SRC_COUNT=$(echo "$JAVA_FILES" | wc -l | tr -d ' ')
echo "Compiling $SRC_COUNT source files…"
# shellcheck disable=SC2086 — no spaces in source filenames in this repo
# B5-3487: -Xmaxerrs 2000 so a failure reports every error rather than the first
# 100. javac's DEFAULT cap is what made the tree's 248 errors read as "100 errors
# in 13 files", and what let two builds be recorded as agreeing when both had
# simply stopped at the same cap. A capped count is a floor, never a total.
javac --release 8 -encoding UTF-8 -Xmaxerrs 2000 -d "$OUT" @"$LIST"

# B5-0016 (b): LOUD guard for the empty-output failure the B5-3487 marker exists
# to prevent. A javac that reads an unusable response file can exit 0 having
# emitted nothing, and the marker written further down would then stamp an empty
# out/ as "complete" — run.bat guards on that marker, so the next run dies at
# runtime with NoClassDefFoundError instead of reporting the build failure that
# caused it. Class count is the only evidence that the compile actually landed,
# so a zero matches a hard failure: remove out/, write no marker, exit non-zero.
CLASS_COUNT=$(find "$OUT" -name "*.class" 2>/dev/null | wc -l | tr -d ' ')
if [ "$CLASS_COUNT" -eq 0 ]; then
  echo "ERROR: compiled $SRC_COUNT sources but out/ holds 0 class files."
  echo "       The response file was probably not readable by javac. out/ removed so no empty build can be marked complete."
  rm -rf "$OUT"
  exit 1
fi

echo "Copying resources…"
if [ -d "$RES" ]; then
  # B5-3667: index.json is now a shippable resource under cards/. The guard
  # classes load /cards/index.json as a classpath resource, so copy the whole
  # cards directory subtree (cp -r covers it) — cp -f the file explicitly so a
  # mtime-based cp -r never drops it on a stale out/.
  cp -r "$RES"/. "$OUT"/ 2>/dev/null || true
  cp -f "$RES"/cards/index.json "$OUT"/
fi

# B5-3487: written LAST, after javac succeeded and resources landed - same
# contract as compile.bat, which writes the same filename. run.bat checks for it.
echo ok > "$OUT/b5ccg-build-ok.txt"

echo ""
echo "✓ Build successful. Run with: ./run.sh"

# ── Optional verification gate (B5-0308 wiring) ────────────────────────────
# Run with: RUN_TESTS=1 sh compile.sh
# Executes the rulebook-conformance suite (B5-0308) and the headless smoke
# test (B5-0201) after a successful build; any failure exits non-zero so
# rule regressions are caught by the build. Default is off so a plain
# `sh compile.sh` stays a fast compile gate.
if [ "${RUN_TESTS:-0}" = "1" ]; then
  echo ""
  echo "=== Verification (RUN_TESTS=1) ==="

  # ── Why run-all-and-collect, not `set -e` ──────────────────────────────────
  # This list used to be invoked as plain sequential `java` lines under `set -e`,
  # so the FIRST non-zero exit aborted the script and every class after it never
  # ran. That made the gate structurally incapable of reporting a complete
  # verdict: a failure at class 5 hid 26 classes, and once class 5 was repaired
  # the HeadlessJoinTimingProbe S3 failure at class 10 hid the remaining 21,
  # including the canonical-parity, printing-diff, effect-census, rulebook-pin,
  # aftermath-detach probes and all five UI suites. Any claim that "the failing
  # set is empty" from such a run describes only the classes that got to run.
  #
  # The list below is therefore executed in a loop that records every class's
  # exit code and elapsed time, prints one summary table, and exits non-zero if
  # ANY class failed. Comparison is by failing-name set (AGENTS.md §4), never by
  # count, so the table's stable "CODE CLASS TIME RESULT" shape is the contract.
  #
  # The class list is a literal here-doc on purpose. An earlier revision grepped
  # this file's own source for class names, which made the gate depend on the
  # text of its own comments — editing a comment could silently change or drop a
  # gate class. Keep the list here and nowhere else.

  GATE_CLASSES=$(cat <<'GATE_EOF'
b5ccg.engine.HeadlessConformanceTest
b5ccg.engine.HeadlessSmokeTest
b5ccg.engine.B52481HumanPassRoundProbe
b5ccg.engine.HeadlessDoubleRunProbe
b5ccg.engine.HeadlessHumanSeatProbe
b5ccg.engine.HeadlessAIDifficultyContractTest
b5ccg.ai.HeadlessAIScoreFloorTest
b5ccg.engine.HeadlessConflictResolutionProbe
b5ccg.engine.HeadlessDrawRoundProbe
b5ccg.engine.HeadlessJoinTimingProbe
b5ccg.engine.HeadlessHumanConflictAttackWindowTest
b5ccg.engine.HeadlessLeadFleetScenarioProbe
b5ccg.engine.HeadlessParticipationGatesProbe
b5ccg.engine.HeadlessStationVictoryTest
b5ccg.engine.HeadlessWarConflictProbe
b5ccg.engine.HeadlessRaceStateProbe
b5ccg.engine.HeadlessBonusFloorProbe
b5ccg.engine.HeadlessTieBreakProbe
b5ccg.engine.HeadlessCardIdNamespaceProbe
b5ccg.engine.CardSetManifestChecker
b5ccg.engine.CardSetManifestCrossCheck
b5ccg.engine.HeadlessCanonicalParityProbe
b5ccg.engine.HeadlessSortedOutputProbe
b5ccg.engine.HeadlessUnimplementedTextTripwire
b5ccg.engine.HeadlessInvariantCheckerSelfTest
b5ccg.engine.HeadlessPrintingDiffTriageProbe
b5ccg.model.EffectCensusChecker
b5ccg.engine.HeadlessRulebookPinProbe
b5ccg.model.HeadlessAftermathDetachProbe
b5ccg.ui.GameBoardAgendaBandTest
b5ccg.ui.TrackerColumnFitTest
b5ccg.ui.MainWindowToolbarLayoutTest
b5ccg.ui.MainWindowLeadFleetControlTest
b5ccg.ui.MainWindowAgendaLifecycleControlTest
GATE_EOF
)

  # Deliberately NOT wired here, measured the same pass:
  #   HeadlessReportingTiebreakTest  15.4s - its live round-cap leg reads
  #     NOT_APPLICABLE (0/4 players at 20+) on an ordinary run, so the live
  #     check silently does not fire; keep it a report-only probe.
  #   HeadlessMultiRoundTest      1268.6s (21.1 min) - measurement rig; it has
  #     no failure exit at all (0 or 3 only), so it cannot gate anything.
  #   HeadlessStallSoakProbe       978.6s (16.3 min) - its own final line says
  #     "classifications are reporting, not failures"; exit 2/3 are argument
  #     and harness errors, not verdicts.
  #   HeadlessFuzzProbe            475.4s for 24 games (B5-0030, measured
  #     2026-10-21) - a seeded AI-versus-AI fuzz rig. It DOES have pass/fail
  #     semantics (exit 1 on an invariant violation or an engine exception),
  #     so it is not quarantined for that reason; it is quarantined for its
  #     runtime. 20s per game is the GAME's own pace, not the harness's poll
  #     interval: measured at a 2ms poll the 4-game run took 90.2s against
  #     90.6s at 20ms. Run it on demand. It has already earned its keep - it
  #     found a real engine exception (CardEffects.applyAftermathEffect
  #     threw UnsupportedOperationException on seed 20348130) that no other
  #     gate class caught.
  # The last three are soak runners whose runtime alone disqualifies them, and
  # their exit codes carry no pass/fail semantics. Run them on demand.
  #
  # GameBoardCollapseTest is quarantined (B5-3487: 88 of the 248 errors, calling
  # members of types that were never authored) so it is not run here; that
  # leaves five UI suites. GameBoardAgendaBandTest survives because its only
  # references to the quarantined class are javadoc {@link} references, which
  # its own Javadoc text carries rather than importing it.

  COUNT=$(printf '%s\n' "$GATE_CLASSES" | grep -c .)
  echo "Running $COUNT gate classes (run-all-and-collect; a failure no longer hides later classes)."
  echo "Source count: $SRC_COUNT  |  Compiled class count: $CLASS_COUNT"
  echo ""

  # Per-class logs, so a FAIL is diagnosable without re-running by hand.
  # "${OUT}_gate" keeps the out_*/ gitignore rule that out/ itself uses.
  GATE_LOG_DIR="${OUT}_gate"
  rm -rf "$GATE_LOG_DIR"
  mkdir -p "$GATE_LOG_DIR"

  printf '%-6s %-8s %-10s %s\n' "CODE" "TIME" "RESULT" "CLASS"
  echo "----------------------------------------------------------------------------"

  TOTAL_TIME=0
  PASS=0
  FAIL=0
  FAILED_NAMES=""

  printf '%s\n' "$GATE_CLASSES" | grep . | while IFS= read -r cls; do
    [ -n "$cls" ] || continue
    LOG="$GATE_LOG_DIR/${cls##*.}.log"
    START=$(date +%s%N 2>/dev/null || echo 0)

    # `set -e` is still in force for the whole script, so the failing command
    # must be part of an OR-list or the shell exits before CODE is assigned.
    # `java ... || CODE=$?` makes the assignment happen on failure AND keeps
    # `set -e` from aborting, which is the entire point of this loop.
    # `< /dev/null` is DEFENSIVE, not required. An earlier version of this
    # comment claimed it was REQUIRED, and gave a reason that does not hold:
    #
    #   "This loop's body runs with stdin still connected to the printf-grep
    #    pipeline, so java inherits that pipe as its own stdin. Any class that
    #    reads a single byte from stdin consumes the remaining class names,
    #    and read -r cls then hits EOF and the loop stops early."
    #
    # FALSIFIED. No Java source reads System.in: zero occurrences across all
    # 189 files under b5ccg/src, and no Scanner, System.console or readLine.
    # A class that never reads stdin cannot consume the pipeline, so the loop
    # could not have stopped early for the stated reason. The B5-0026
    # measurement that only 4 of 31 classes ran without the redirect was real,
    # but its cause was the table writer/counting race, not this - and that
    # race is what the synchronous .row writes and the pre-verdict invariant
    # above now fix.
    #
    # The redirect is KEPT: it is free, correct, and forecloses a whole class
    # of future surprise, because if some class ever does read stdin it will
    # see EOF instead of eating the pipeline. The falsified justification is
    # recorded rather than silently deleted so the next reader is not misled
    # into re-deriving a false cause. Adjudication:
    # .agent/REPORTS/2026-10-19-opencode-big-pickle-01-gate-run4-adjudication.md
    CODE=0
    java -cp "$OUT" "$cls" < /dev/null > "$LOG" 2>&1 || CODE=$?

    END=$(date +%s%N 2>/dev/null || echo 0)
    DURATION=$(( (END - START) / 1000000 ))
    if [ "$DURATION" -lt 0 ]; then DURATION=0; fi
    if [ "$CODE" -eq 0 ]; then
      RES="PASS"
    else
      RES="FAIL"
    fi
    printf '%-6s %-8s %-10s %s\n' "$CODE" "${DURATION}ms" "$RES" "$cls" > "$GATE_LOG_DIR/.row"
    cat "$GATE_LOG_DIR/.row"
    cat "$GATE_LOG_DIR/.row" >> "$GATE_LOG_DIR/table.txt"
  done
  rm -f "$GATE_LOG_DIR/.row"

  # The loop above runs in a pipeline subshell, so its counters do not survive.
  # Derive them from the recorded table instead of trusting subshell state.
  #
  # `tee` is NOT used to write the table. A previous revision piped the loop into
  # `tee table.txt` and then immediately counted the file, which under MSYS can
  # read it before the final line has been flushed: one real run reported
  # 26 passed + 4 failed against a 31-row table - a sum that does not add up -
  # and nothing noticed, because the FAIL branch exited before the count guard
  # below could fire. Writing each row from the shell itself (`>` then `>>`)
  # makes the file write synchronous with the loop, so the count always sees
  # every row. The table still streams live: this gate takes minutes
  # (HeadlessHumanSeatProbe alone is ~186s), and a gate that prints nothing for
  # four minutes is indistinguishable from a hung one.
  #
  # Counting matches field 3 EXACTLY ("$3==\"PASS\"") rather than grepping for a
  # ' PASS ' substring. Field comparison is deterministic, and the sum invariant
  # below makes any future miscount loud instead of quiet.
  TABLE="$GATE_LOG_DIR/table.txt"
  PASS=$(awk '$3=="PASS"{n++} END{print n+0}' "$TABLE")
  FAIL=$(awk '$3=="FAIL"{n++} END{print n+0}' "$TABLE")
  ROWS=$(awk 'END{print NR+0}' "$TABLE")
  FAILED_NAMES=$(awk '$3=="FAIL"{print $NF}' "$TABLE")
  TOTAL_TIME=$(awk '{t+=substr($2,1,length($2)-2)} END{print t+0}' "$TABLE")

  echo "----------------------------------------------------------------------------"
  echo "Summary: $PASS passed, $FAIL failed (of $COUNT gate classes run)"
  echo "Total gate time: ${TOTAL_TIME}ms"
  echo "Per-class logs: $GATE_LOG_DIR/"

  # Invariant first: a table that does not account for every class is a broken
  # measurement, and must be reported as such BEFORE any verdict is printed.
  if [ "$ROWS" -ne "$COUNT" ] || [ "$((PASS + FAIL))" -ne "$COUNT" ]; then
    echo ""
    echo "ERROR: gate table does not account for every class: $ROWS rows,"
    echo "       $PASS pass + $FAIL fail, expected $COUNT classes."
    echo "       Any verdict from this table would be untrustworthy."
    exit 1
  fi

  if [ "$FAIL" -gt 0 ]; then
    echo ""
    echo "FAILING-NAME SET (compare by name, never by count):"
    printf '%s\n' "$FAILED_NAMES" | sed 's/^/  - /'
    echo ""
    for f in $FAILED_NAMES; do
      short="${f##*.}"
      if [ -f "$GATE_LOG_DIR/$short.log" ]; then
        echo "----- tail of $f ($GATE_LOG_DIR/$short.log) -----"
        tail -n 25 "$GATE_LOG_DIR/$short.log"
        echo "----- end $f -----"
      fi
    done
    echo ""
    echo "ERROR: $FAIL gate class(es) failed. Exit non-zero."
    exit 1
  fi

  if [ "$PASS" -ne "$COUNT" ]; then
    echo "ERROR: table recorded $PASS passes against $COUNT classes; loop did not complete."
    exit 1
  fi
  echo "✓ Verification passed (all $COUNT gate classes)."

  # ── Tooling probes (B5-0044) ────────────────────────────────────────────
  # These are PowerShell, not Java, so they are NOT gate classes and are NOT
  # in GATE_CLASSES. They run here, after the Java table, so the 31-class
  # invariant above is untouched and the summary line still describes exactly
  # the Java classes.
  #
  # They are wired in rather than left as documentation because a probe that
  # nothing runs is a comment. Each one has a proven RED state: the
  # unfixed tools fail them.
  #
  # The probes are skipped when no PowerShell is on PATH, because the gate
  # must still run on a machine that has only a JDK. When it IS present and a
  # probe fails, the gate exits non-zero - a tooling guard that cannot fail
  # the gate is not a guard.
  TOOL_PROBES="../.agent/tools/test-param-guard.ps1 ../.agent/tools/test-flip-ledger.ps1"
  if command -v powershell > /dev/null 2>&1 && [ -f "../.agent/tools/test-param-guard.ps1" ]; then
    echo ""
    echo "=== Tooling probes (B5-0044) ==="
    TOOL_PASS=0
    TOOL_FAIL=0
    TOOL_FAILED=""
    for probe in $TOOL_PROBES; do
      [ -f "$probe" ] || continue
      pname=$(basename "$probe" .ps1)
      plog="$GATE_LOG_DIR/$pname.log"
      pstart=$(date +%s%N 2>/dev/null || echo 0)
      pcode=0
      powershell -NoProfile -ExecutionPolicy Bypass -File "$probe" > "$plog" 2>&1 || pcode=$?
      pend=$(date +%s%N 2>/dev/null || echo 0)
      pdur=$(( (pend - pstart) / 1000000 ))
      [ "$pdur" -lt 0 ] && pdur=0
      if [ "$pcode" -eq 0 ]; then
        printf '%-6s %-8s %-10s %s\n' "0" "${pdur}ms" "PASS" "$pname"
        TOOL_PASS=$(( TOOL_PASS + 1 ))
      else
        printf '%-6s %-8s %-10s %s\n' "$pcode" "${pdur}ms" "FAIL" "$pname"
        TOOL_FAIL=$(( TOOL_FAIL + 1 ))
        TOOL_FAILED="$TOOL_FAILED $pname"
      fi
    done
    echo "Tooling probes: $TOOL_PASS passed, $TOOL_FAIL failed"
    if [ "$TOOL_FAIL" -gt 0 ]; then
      echo ""
      echo "FAILING TOOLING PROBES:"
      for p in $TOOL_FAILED; do
        echo "  - $p"
        if [ -f "$GATE_LOG_DIR/$p.log" ]; then
          echo "----- tail of $p ($GATE_LOG_DIR/$p.log) -----"
          tail -n 25 "$GATE_LOG_DIR/$p.log"
          echo "----- end $p -----"
        fi
      done
      echo ""
      echo "ERROR: $TOOL_FAIL tooling probe(s) failed. Exit non-zero."
      exit 1
    fi
  else
    echo ""
    echo "=== Tooling probes (B5-0044): SKIPPED (no powershell on PATH) ==="
  fi
fi

