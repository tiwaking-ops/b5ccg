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

SRC="src"
OUT="out"
RES="resources"

echo "=== B5 CCG Build ==="
echo "Source: $(pwd)/$SRC"
echo "Output: $(pwd)/$OUT"

# B5-3487: clean out/ BEFORE compiling, for the same reason compile.bat does.
# javac emits a .class for every source that compiled before the first error
# batch, so a red build leaves out/ half-populated; run.sh's guard can then only
# tell a good build from a bad one by asking whether the build marker is present,
# which is why compile.bat writes it last.
rm -rf "$OUT"
mkdir -p "$OUT"

# B5-0016 (b): the file list is scratch, written INSIDE b5ccg/ as a RELATIVE
# path and removed on both the success and the failure path by the trap below.
#
# Why relative, and not ${TMPDIR}: TMPDIR on this host expands to a MSYS
# short-name path (/c/Users/TIWAPE~1/AppData/Local/Temp). Handed to the NATIVE
# Windows javac, that path is not reliably resolvable, and the failure is
# SILENT — measured on this tree, javac compiled only a subset of the list,
# exited 0, and compiled-sources later died at runtime with
# NoClassDefFoundError: b5ccg/engine/RulesEngine over a partially-populated
# out/ that still carried the build marker. This script already refuses absolute
# POSIX paths for the same reason (B5-0306), so the temp list follows that rule.
LIST="b5ccg_compile_list.tmp"
trap 'rm -f "$LIST"' EXIT INT TERM

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
  java -cp "$OUT" b5ccg.engine.HeadlessConformanceTest
  java -cp "$OUT" b5ccg.engine.HeadlessSmokeTest

  # ── Headless gate tier (B5-0956; timing wording corrected B5-0993) ────────
  # These seven are GATES: each has a pure 0/1 exit contract (a non-zero exit
  # means a real engine-or-data defect, never an exploratory outcome), and this
  # gate enforces EXIT CODES ONLY. Timing is deliberately not asserted: the
  # B5-0982 battery (8 runs per class, 56 total) measured medians 247-580ms
  # but 3 of 7 classes exceeded 1s on at least one run under host load (max
  # 2014ms), so the earlier "under 1s" wording was retired to keep it from
  # hardening into a timing assertion that would flake every compile. The
  # 8/8-green exit contract held 56/56 against the unseeded deck shuffle
  # (b5ccg/model/Deck.java uses Collections.shuffle with no seed, so this is
  # the real variance source, not a pinned one). `set -e` above aborts the
  # script on the first non-zero exit, so each exit code is honoured without
  # extra plumbing.
  #
  # Deliberately NOT wired here, measured the same pass:
  #   HeadlessReportingTiebreakTest  15.4s - its live round-cap leg reads
  #     NOT_APPLICABLE (0/4 players at 20+) on an ordinary run, so the live
  #     check silently does not fire; keep it a report-only probe.
  #   HeadlessMultiRoundTest      1268.6s (21.1 min) - measurement rig; it has
  #     no failure exit at all (0 or 3 only), so it cannot gate anything.
  #   HeadlessStallSoakProbe       978.6s (16.3 min) - its own final line says
  #     "classifications are reporting, not failures"; exit 2/3 are argument
  #     and harness errors, not verdicts.
  # The last three are soak runners whose runtime alone disqualifies them, and
  # their exit codes carry no pass/fail semantics. Run them on demand.
  java -cp "$OUT" b5ccg.engine.B52481HumanPassRoundProbe
  # B5-0008 (WP-07/A5): determinism gates — seeded double-run probe plus the
  # now-deterministic human-seat probe (Deck seeded, controller derives AI
  # streams from GameState.getSeed(), seed recorded in replay header).
  java -cp "$OUT" b5ccg.engine.HeadlessDoubleRunProbe
  java -cp "$OUT" b5ccg.engine.HeadlessHumanSeatProbe
  # B5-3487: B52494AgendaMightyCostProbe and B52495AgendaPowerVictoryProbe were
  # removed from this gate list, not from the repo. Both called agenda-effect
  # members of RulesEngine/Player/VictoryPathResult that were never authored, so
  # they were 8 of the 248 compile errors and could not compile at all. They are
  # preserved verbatim in quarantine/b5ccg_compile_broken_2026-10-08/ and return
  # here only if the agenda-effect feature is actually built. Leaving the lines in
  # would make RUN_TESTS=1 fail on a class that does not exist.
  java -cp "$OUT" b5ccg.engine.HeadlessAIDifficultyContractTest
  # B5-4146: AIPlayer scoring-floor sentinel test (B5-4136 creation), pure 0/1
  # exit contract, never wired into the gate until now. Ships alongside its
  # sibling AI contract test above.
  java -cp "$OUT" b5ccg.ai.HeadlessAIScoreFloorTest
  java -cp "$OUT" b5ccg.engine.HeadlessConflictResolutionProbe
  # B5-4171: Draw round D1-D5 conformance probe (45 checks, 0/1 exit).
  java -cp "$OUT" b5ccg.engine.HeadlessDrawRoundProbe
  # B5-4160: join-timing conformance probe (queued declaration, deferred resolution)
  java -cp "$OUT" b5ccg.engine.HeadlessJoinTimingProbe
  java -cp "$OUT" b5ccg.engine.HeadlessHumanConflictAttackWindowTest
  java -cp "$OUT" b5ccg.engine.HeadlessLeadFleetScenarioProbe
  java -cp "$OUT" b5ccg.engine.HeadlessParticipationGatesProbe
  java -cp "$OUT" b5ccg.engine.HeadlessStationVictoryTest
  java -cp "$OUT" b5ccg.engine.HeadlessWarConflictProbe
  java -cp "$OUT" b5ccg.engine.HeadlessFactionStateProbe
  # B5-2518: minimum-1-bonus-floor conformance probe (rulebook :673 reading).
  java -cp "$OUT" b5ccg.engine.HeadlessBonusFloorProbe
  # 13th Headless class triaged under B5-2718 / B5-0956: pure 0/1 gate contract, wired in.
  java -cp "$OUT" b5ccg.engine.HeadlessTieBreakProbe
  # B5-3667: the card-id namespace guards shipped as untracked sources and load
  # /cards/index.json at runtime; they are pure Java 6 gates with a 0/1 exit
  # contract, so observe them in the RUN_TESTS=1 pass before anything else.
  java -cp "$OUT" b5ccg.engine.HeadlessCardIdNamespaceProbe
  java -cp "$OUT" b5ccg.engine.CardSetManifestChecker
  java -cp "$OUT" b5ccg.engine.CardSetManifestCrossCheck
  # B5-0003: canonical-key parity probe (WP-02/A1a, 0/1/2 exit contract).
  java -cp "$OUT" b5ccg.engine.HeadlessCanonicalParityProbe
  # B5-0004: printing-diff triage scenario probe (WP-03, 0/1 exit contract).
  # Verifies both ids resolve for all 9 single-literal printing-diff titles
  # under canonical-key routing (5 changed-text ambassadors, 1 unchanged event,
  # 3 VANILLA-no-table characters) plus aftermath slug-divergence negative control.
  java -cp "$OUT" b5ccg.engine.HeadlessPrintingDiffTriageProbe
  # B5-0005: effect-census checker (WP-04/A2, 0/1/2 exit contract).
  java -cp "$OUT" b5ccg.model.EffectCensusChecker
  # B5-0002: rulebook pin + encoding probe (0/1 exit contract).
  java -cp "$OUT" b5ccg.engine.HeadlessRulebookPinProbe
  # B5-4114: GameState.detachAftermath primitive pinned by this probe (24 checks,
  # DETACH1-D4) but was deliberately not wired when shipped; now observed in the
  # gate so the D4 name-slot release invariant is continuously verified.
  java -cp "$OUT" b5ccg.model.HeadlessAftermathDetachProbe
  # ── UI gate tier (B5-2757) ─────────────────────────────────────────────────
  # Six UI regression suites, each probed individually to confirm deterministic
  # green before wiring: GameBoardAgendaBandTest, GameBoardCollapseTest,
  # TrackerColumnFitTest, MainWindowToolbarLayoutTest,
  # B5-3487: GameBoardCollapseTest is quarantined (88 of the 248 errors, calling
  # members of types that were never authored) so it is no longer run here; that
  # leaves five UI suites. GameBoardAgendaBandTest survives because its only
  # references to the quarantined class are javadoc {@link} references, which
  # GameBoardAgendaBandTest's own Javadoc text carries rather than importing it.
  # MainWindowLeadFleetControlTest, MainWindowAgendaLifecycleControlTest. Every
  # one has a pure 0/1 exit contract, so set -e honours real exit codes with no
  # extra plumbing. They all pass deterministically here with no display: each
  # uses SwingUtilities.invokeAndWait to paint/dispatch off-screen on the EDT
  # (see b5ccg/ui/MainWindowLeadFleetControlTest for the headless pattern).
  java -cp "$OUT" b5ccg.ui.GameBoardAgendaBandTest
  java -cp "$OUT" b5ccg.ui.TrackerColumnFitTest
  java -cp "$OUT" b5ccg.ui.MainWindowToolbarLayoutTest
  java -cp "$OUT" b5ccg.ui.MainWindowLeadFleetControlTest
  java -cp "$OUT" b5ccg.ui.MainWindowAgendaLifecycleControlTest
  echo "✓ Verification passed."
fi
