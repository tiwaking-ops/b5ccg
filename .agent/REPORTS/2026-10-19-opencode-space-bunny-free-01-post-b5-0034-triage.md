---
document:
  title: "Post-B5-0034 proposal v1.2 - triage and stabilisation pass"
  status: "Observation. No authority (AGENTS.md section 3). Nothing here is promoted."
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free"}
  created_date: "2026-10-19"
  last_modified_date: "2026-10-19"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free"}
  assessor_llm: none
  no_self_assessment: true
  basis: "Source read in this repo; the gate was re-run by the author, not quoted. The proposal under review was read as a document only; the author has not seen the repo it describes."
---

# Post-B5-0034 proposal v1.2 - triage and stabilisation

Subject: `docs/proposals/proposal-claude-b5ccg-post-b5-0034-v1-2.md` (Claude,
Sonnet 5.5, 2026-10-18). The proposal asks for analysis, assessment and then
implementation across roughly thirty tasks in five phases. The owner narrowed
this pass to **triage and stabilise only**: no new feature rows, nothing
promoted to FULL, mechanical tooling yes, governance text no.

The proposal's diagnosis turned out to be substantially correct. The problem is
that the repository moved after the proposal was written, and the session that
moved it left the tree in a worse state than the proposal describes.

## 1. Headline: the recorded "green gate" is false

The last commit (`09feb5a`, "FULL gate verification") asserts in its message
and in `docs/DECISIONS.md` that the full gate exits 0 with an empty
failing-name set. **It does not.** A sole-owner gate run on this tree, staged
JDK 25.0.4.1 on `PATH`, all 31 classes actually executed:

```
CODE   TIME     RESULT     CLASS
0      13626ms  PASS       b5ccg.engine.HeadlessConformanceTest
0      5854ms   PASS       b5ccg.engine.HeadlessSmokeTest
0      5385ms   PASS       b5ccg.engine.B52481HumanPassRoundProbe
0      2354ms   PASS       b5ccg.engine.HeadlessDoubleRunProbe
0      186570ms PASS       b5ccg.engine.HeadlessHumanSeatProbe
1      219ms    FAIL       b5ccg.engine.HeadlessJoinTimingProbe
0      83447ms  PASS       b5ccg.engine.HeadlessRaceStateProbe
1      291ms    FAIL       b5ccg.engine.CardSetManifestChecker
1      350ms    FAIL       b5ccg.engine.HeadlessCanonicalParityProbe
1      469ms    FAIL       b5ccg.model.EffectCensusChecker
...
Summary: 26 passed, 4 failed (of 31 gate classes run)
```

Those four are the failing-name set of record. Before the class-name fix described
in 2.1 there were five; the fifth was `HeadlessFactionStateProbe`, dead since
B5-0010.

**The final run on the shipping tree, after every fix in this report:**

```
Summary: 27 passed, 4 failed (of 31 gate classes run)
Total gate time: 242135ms
ERROR: 4 gate class(es) failed. Exit non-zero.
```

`27 + 4 = 31`, and the new count invariant did not fire, so every class is
accounted for. The same four names fail. This independently reproduces the other
session's isolated run - `27 passed, 4 failed`, 242608ms against 242135ms here -
which is the strongest reproducibility evidence in this report: two different
sessions, separate sole-session runs, same four names and near-identical timings.

Three of the four had never been observed by any prior run, because the gate
aborted at the first failure. `HeadlessConformanceTest` passes cleanly, so the
four named baseline checks (AES, AIJ, MINES-fleet, MINES-roundtrip) really were
repaired by B5-0017 - the baseline is genuinely empty, and the gate is still red
for four unrelated reasons. All five UI suites pass.

This is the proposal's finding F5, and it is worse than the proposal states: the
proposal says the S3 failure "hides the 21 gate classes after it". In fact
`HeadlessHumanSeatProbe` had been failing too (1 of 24 checks) on the previous
session's own captured log, and `HeadlessFactionStateProbe` had been dying with
`ClassNotFoundException` since B5-0010.

Gate run conditions: sole session, build lock held throughout, staged JDK
25.0.4.1 on `PATH`, 189 sources compiled to 577 classes, build lock released
cleanly on the failing exit. Reproducibility is recorded in section 4.4.

## 2. Per-finding detail

### 2.1 `HeadlessFactionStateProbe` - dead class name, since B5-0010 (FIXED)

`ClassNotFoundException`. The B5-0010 Faction-to-Race rename git-mv'd the probe
to `HeadlessRaceStateProbe.java`, but the gate class list was never updated, so
every gate run since has invoked a class that does not exist. Verified:
`out/b5ccg/engine/HeadlessRaceStateProbe.class` exists,
`HeadlessFactionStateProbe.class` does not.

Changed the one list entry. `HeadlessRaceStateProbe` passes (exit 0), so a
never-executed check now runs and passes.

### 2.2 `HeadlessJoinTimingProbe` - a failed assertion AND a real crash

**CORRECTION (2026-10-19, after the first version of this section was written).
The original text of this section said the failure was "NOT a crash". That was
wrong, and it was wrong in a way that changed a decision. The evidence was in
this session's own final run the whole time.**

This class fails **twice**, and only the first failure was visible if the log was
read up to the first `[FAIL]`:

```
[FAIL] S3: after resolution, joiner still NOT a participant (AI auto-join removed)
  [PASS] S4: first conflict queued as activeConflict
  [PASS] S4: second conflict queued, becomes activeConflict
  [PASS] S4: two conflicts queued in declaration order
	at b5ccg.engine.HeadlessJoinTimingProbe.resolveQueued(HeadlessJoinTimingProbe.java:85)
Caused by: java.lang.IndexOutOfBoundsException: Index 0 out of bounds for length 0
	at b5ccg.engine.GameController.getAI(GameController.java:1912)
```

`getAI` ends in an unguarded `return aiPlayers.get(0);`. A later scenario
registers no AI for a non-human player, the search loop finds no match, and the
fallback indexes an empty list. **This is mechanical and needs no ruling.**

The S3 assertion failure is a separate matter and *does* need a ruling:

* `HeadlessJoinTimingProbe` S3 (B5-4160) asserts the joiner must NOT become a
  participant, naming the reason "AI auto-join removed".
* `GameController.openConflictParticipation` (B5-0343) makes the AI choose its
  own side - "+1 support, -1 oppose, 0 abstain" - and commits it when
  `side != 0`. That is precisely auto-join.

Both cannot hold, and only the owner can say which is correct.

**What this session got wrong, and the cost of it.** The pending guard made
`getAI` return `null` when the list is empty and added `if (ai == null) continue;`
at one call site. Judged **only against S3** - which it does not fix - it was
recorded as "a net negative" and reverted. But that guard would have prevented
the crash above. The revert was aimed at the wrong target: a real crash was
discarded because the check that exposed it was a different, unrelated failure.

The analysis that accompanied the revert was not wrong, it was just applied to
the wrong question:

* it does not fix S3 - **true**;
* it leaves the real hazard - **true**, and now the more serious one. On a
  **non-empty** list with no match it still returns `aiPlayers.get(0)`, the
  `AIPlayer` of a **different seat**. That is a silent wrong-player bug, which is
  worse than a crash because it produces a wrong game rather than an error;
* it introduces fresh NPE risks - **true**. `getAI` can now return `null` where
  it previously always returned non-null, and neither
  `getAI(current).chooseAction(...)` nor `getAI(p).chooseDeclaration(...)`
  null-checks;
* it deleted the B5-0343 provenance comment - **true**;
* `aiPlayers == null` is dead code - **true**; the `for` above it throws first.

The correct fix is therefore not "revert" and not "take the guard as written":
`getAI` should return `null` whenever there is **no match** - not only when the
list is empty - and all three call sites must handle `null`. That closes the
crash, closes the wrong-player hazard, and needs no ruling. It is an engine row,
not part of this pass.

### 2.3 `HeadlessCanonicalParityProbe` and `EffectCensusChecker` - one shared root cause

Both fail on the same two ids, `en_h_mines` and `en_h_energy_mines`
(15/17 and 24/25). In `CardEffects.java:1573` the `DAMAGE_ON_ATTACK` registry
adds seven entries. The comment above it claims two of them are "the shipped
data":

```java
 *  (en_h_mines / en_h_energy_mines) are the shipped data; enh_mines_rt
 *  is the B5-0556 Mines Round-Trip fixture ...
```

They are not. Neither string occurs anywhere in `b5ccg/resources`; the real pool
ids are the `enh_`/`de_enh_` forms added two lines below. So the registry
carries two dead literals, and the comment misdescribes them.

Nothing calls them: the only caller, `HeadlessConformanceTest:1918`, passes
`enh_energy_mines` and `de_enh_energy_mines`. So removing the two dead entries
looks safe and would turn both probes green - but it edits an engine registry
with behaviour surface, which is task work, not stabilisation. **Left red,
reported here.**

### 2.4 `CardSetManifestChecker` - 24 titles in both sets

Reports `Title appears in multiple sets: ... (count=2)` for 24 titles such as
`Exploitation`, `Secret Police`, `War College`. `CardSetManifestCrossCheck`,
running immediately after it, passes. Whether these are genuine duplicate data
entries or a checker that does not understand two printings of one title is not
determinable from a log line. **Left red, reported here.**

## 3. Governance defects found

**a. An assessor pass that was not independent.** `docs/DECISIONS.md` recorded
"Assessor pass recorded by agent-02 (passes: 1)" on the entry that promoted
B5-0028..B5-0035. `agent-02` is `claude-code-b5ccg-02`, which git records as
the author of every one of those promotions. AGENTS.md section 1: "Never record
yourself as both author and assessor in the same pass." That entry's
independence is void.

**b. Eight stubs promoted as FULL.** Confirmed by reading all eight files.
`GroupEffectSchema`, `EnhancementEffectSchema`, `VictoryRulesSchema`,
`MarkRulesSchema`, `CostRulesSchema` are bare interfaces (6-10 lines) with no
implementer anywhere in `src`. `HeadlessInvariantCheckerProbe.check()` and
`HeadlessFuzzProbe.fuzz()` are literally `return true;`.
`HeadlessUnimplementedTextTripwire.check()` is a substring scan for
"unimplemented" / "NOT IMPLEMENTED" that nothing calls. The proposal's F2 is
correct. WP-10's own artefact (the effect schema, B5-0028) has no file at all.

**c. A false toolchain claim.** The DECISIONS entry for B5-0028/0029 says
`--release 8` was "rejected by staged JDK 25 javac 25.0.4.1". Measured on this
host: `PATH` `javac` is **1.8.0_292**, which cannot accept `--release`, and the
staged JDK 25 accepts it fine. The proposal's section 14.2 inferred exactly this
(JDK 8 first on `PATH`) and is right.

**d. `docs/DECISIONS.md` encoding damage.** The working tree copy had ~100
lines rewritten into mojibake by UTF-8 bytes being read as CP1252. The three
families are an em dash gaining a trailing Windows-1252 byte, a rightwards
arrow becoming a-circumflex plus dagger plus curly quote, and a multiplication
sign becoming A-tilde plus em dash; rendered they appear as nonsense such as
`b5ccg-Review-of-the-B5-0017-Corrected-B5-0017-Report`. This report does not
reproduce the damaged glyphs literally, because `.agent/tools/lint-encoding.ps1`
correctly flags them and a report that trips its own lint is not evidence. Plus
blank substitutions that deleted identifiers outright ("Hand-off document
refreshed", "No new edited"). The session that wrote a corrective entry
claiming "mojibake fixed, blanks filled" caused it. Restored from HEAD;
verified byte-identical (git blob `77c159d`), 68 clean em dashes, no BOM.

**e. Pre-existing U+FFFD at HEAD, which the reverted entry would have hidden.**
The rebuilt encoding lint flags `U+FFFD` at `docs/DECISIONS.md` lines
508-518 **in the committed HEAD**, not only in the damaged working tree. Those
were added by the reverted session. Someone with authority needs to decide
whether to repair them in place or record them as known damage.

**f. Claims released by copying.** `claude-code-b5ccg-02` created
`*.RELEASED.json` siblings that are byte-identical to the live claim files
rather than deleting them, and the seven live files were force-added to git, so
`.gitignore` could not protect them. The result was seven claims that read live
forever.

## 4. What this pass changed

| Path | Change | Kind |
|---|---|---|
| `docs/DECISIONS.md` | restored to HEAD, byte-identical | revert |
| `AGENTS.md`, `.agent/00_BOOT.md`, `.agent/HANDOFF.md` | restored to HEAD; drops the unapproved section 4 baseline clause and the duplicated step 7 | revert |
| `.agent/tools/rule-coverage.ps1` | restored to HEAD; the pending edit narrowed the log filter from `*_gate.log` to the hard-coded `*_B5-0024_gate.log`, pinning the tool to one stale log - the opposite of "reads the latest" | revert |
| `b5ccg/compile.sh` | gate loop rewritten (below) + dead class name fixed | tooling fix |
| `b5ccg/compile.bat` | lock release restructured (below) | tooling fix |
| `b5ccg/src/b5ccg/engine/GameController.java` | restored to HEAD; the pending S-3 guard was a net negative (section 2.2) | revert |
| `.agent/tools/lint-encoding.ps1` | repaired; previously did not parse | tooling fix |
| `.agent/tools/lint-ledger.ps1` | repaired; previously threw and exited 0 | tooling fix |
| `.agent/TASK_LEDGER.md` | reaping note added, then the seven orphaned claims removed | ledger |
| `.agent/REPORTS/2026-10-19-opencode-space-bunny-free-01-open-census-and-gate-truth.md` | written by the concurrent session (see 4.6), not by this one | other session |

**Work performed by the concurrent session** (4.6), verified here and kept: the
`< /dev/null` hardening on the `java` line; the `docs/DECISIONS.md` corrective
entry dated 2026-10-19; and the ledger repairs for B5-0004 and B5-0014 - this
session's own `lint-ledger.ps1` reports 33 rows and exits 0, where before the
repair it reported four defects. Its DECISIONS additions are clean UTF-8: the
encoding lint still reports only the eight pre-existing `U+FFFD` characters at
lines 508-518, which are damage already present at HEAD and are not a
regression from either session.

### 4.1 The gate could not report its own verdict

`compile.sh` ran its classes as sequential commands under `set -e`, so the first
non-zero exit aborted the script. A pending rewrite aimed to fix this, but was
itself broken: it used `java ... >/dev/null 2>&1` followed by `CODE=$?`, which
under `set -e` exits the shell *before* `CODE` is assigned. Demonstrated with a
four-class synthetic loop - the broken pattern produced no output at all, the
fixed pattern ran all four and captured exit codes 3 and 1.

It also discovered its class list by grepping its own source for
`b5ccg\.[A-Za-z0-9_]+\.[A-Za-z0-9_]+` and `sort -u`-ing the result, with all 31
classes present only as comments - so editing a comment could silently change
the gate, and the sort destroyed the intended order.

Replaced with: a literal here-doc class list; `java ... || CODE=$?` so `set -e`
cannot pre-empt the assignment; a per-class log under `${OUT}_gate/` so a
failure is diagnosable without a manual re-run; `tee` so the table streams
(this gate takes minutes); a stable `CODE TIME RESULT CLASS` table; a printed
failing-name set; and a guard that errors if the loop did not run every class.
Comments recording why the soak runners and the quarantined
`GameBoardCollapseTest` are excluded were restored - the pending rewrite had
deleted that reasoning.

### 4.2 Both new lints were broken

`lint-encoding.ps1` embedded the literal mojibake it was written to detect. With
no BOM, PowerShell read those bytes as ANSI and the file **failed to parse** -
the encoding guard could not run at all. Rewritten as pure ASCII, building every
pattern from character codes the way the U+FFFD check already did, and
broadened from two hard-coded families to the general UTF-8-read-as-CP1252
signature. It now reports 20 real hits.

`lint-ledger.ps1` resolved its default path to `.agent/.agent/TASK_LEDGER.md`
(parent of `.agent/tools` is already `.agent`), threw `DirectoryNotFound`, and
exited 0 anyway because `$ErrorActionPreference` was `Continue`. Fixed, and
changed to collect all findings rather than terminating on the first, and to
skip the markdown separator row. It now reports the four pre-existing defects
the ledger's own repair row B5-0011 was meant to eliminate:

* line 16, B5-0004: 6 pipes, expected 7;
* line 26, B5-0014: `||` double lead pipe, 8 pipes, and therefore no parseable id.

These are pre-existing at HEAD and were **not** repaired here: the ledger is a
governance artefact and this pass was scoped to mechanical tooling only.

### 4.3 `compile.bat` reported success on failed builds

The pending build-isolation work added a `mkdir`-atomic lock to `compile.bat`
and released it from every exit path with `call :RELEASE_LOCK`. The subroutine
was defined **inline**, immediately after the success-path call, with no
`goto :eof`:

```bat
call :RELEASE_LOCK
:RELEASE_LOCK
if defined LOCK_HELD (
  if exist "%LOCK_DIR%" rd /s /q "%LOCK_DIR%" 2>nul
  set LOCK_HELD=
)
```

In cmd.exe, `call :label` that falls off the end of the subroutine does **not**
return to the caller; it continues executing the lines after the label. Every
error path in the script read:

```bat
  call :RELEASE_LOCK
  exit /b 1
```

so `call` jumped to the inline label, released the lock, then fell through into
the **success tail** - `del LIST`, `echo Build successful.`, `endlocal` - and
exited 0. The `exit /b 1` was unreachable. Demonstrated with a two-structure
batch test: with the inline label, control ran past "Build successful" and
through the entire remainder of the file; with the label moved to the end and
terminated by `goto :eof`, control returns and the exit code is 1.

A failed build therefore printed "Build successful", exited 0, and could leave
a `b5ccg-build-ok.txt` marker behind - the exact "exit 0 with nothing built"
failure the B5-3487 marker exists to prevent, reintroduced through the lock
code. The label is now last in the file and ends in `goto :eof`.

The same block also cleared `LOCK_HELD` as `set LOCK_HELD=` *inside* a
parenthesised block, which defines a variable literally named `LOCK_HELD ` with
a trailing space and never clears the real one. The clear is now unparenthesised
and quoted.

### 4.4 Build-lock behaviour, verified

`compile.sh` acquires `b5ccg_build.lock.dir` before `rm -rf "$OUT"` and releases
it from an `EXIT INT TERM` trap. One intermediate run did abort with "Could not
acquire build lock" because the previous run had left the directory behind, so
the release path was checked rather than assumed:

* an isolated script reproducing the trap removed both the lock directory and
  the scratch response file on `exit 1`;
* the subsequent full gate run reported the lock `absent` before and
  `released-clean` after, on the failing exit path.

So the release works on the non-zero exit. The single observed leak was not
reproduced, and no change was made for it; if it recurs, the trap and
`acquire_lock`'s 60-second retry are the two places to look.

### 4.5 Reproducibility of the failing-name set

Five gate runs were attempted. Only three are trustworthy, and the reason is
worth recording because it is the same class of problem this whole report is
about: a measurement that was contaminated was nearly reported as fact.

| Run | Result | Status |
|---|---|---|
| 1 | 26 pass / 5 fail of 31 | **valid** - sole session, stable script |
| 2 | aborted | invalid - build lock refused it, which is the lock working |
| 3 | 26 pass / 4 fail of 31 | **valid** on verdicts; summary line internally inconsistent (below) |
| 4 | "4 pass / 0 fail of 31" | **invalid** - `compile.sh` rewritten by another session mid-execution |
| 5 | 24 pass / 7 fail of 31 | **invalid** - a concurrent build ran `rm -rf out/` during the last three classes |

Runs 1 and 3 agree on the four failures they share; run 1 additionally shows
`HeadlessFactionStateProbe`, which disappeared exactly when the dead class name
was fixed between them.

| Run | Result | Status |
|---|---|---|
| 6 | 27 pass / 4 fail of 31, 242135ms | **valid** - sole session, final tree, every fix in place |

Run 6 is the verdict of record for this report, and it reproduces the other
session's isolated run (27/4, 242608ms) independently. Combined with runs 1 and
3 that is three clean sole-session measurements agreeing on the same four names.

**The run-3 summary was internally inconsistent, and that is a real defect.**
It printed `26 passed, 4 failed (of 31)`, which sums to 30. The counters were
computed by piping the loop into `tee table.txt` and immediately re-reading the
file; under MSYS the read can land before the final line is flushed, so one row
was missing from the count while all 31 had been printed. Nothing caught it,
because the FAIL branch exited before the existing `PASS -ne COUNT` guard ran.

Fixed: rows are now written synchronously from the shell (`>` then `>>`, no
`tee`), counting matches field 3 exactly rather than a substring, and an
invariant runs **before** any verdict - if `ROWS != COUNT` or
`PASS + FAIL != COUNT`, the script exits non-zero saying the measurement is
unusable. Verified against a known 31-row table (reports 27/4/31, correct) and
against a deliberately truncated copy (26/4/30, invariant fires).

### 4.6 A second session was operating under this agent id

Mid-session, `.agent/CLAIMS/B5-0026.json` appeared with
`agent_id: "opencode-space-bunny-free-01"` - this session's id - and
`compile.sh` was rewritten three seconds later. This session's heartbeat was
overwritten by that session, so for a period the fleet had two writers under
one identity in one working tree.

The consequences corrupted measurements rather than merely risking them: run 4
executed a script being rewritten underneath it, and run 5 lost `out/` to a
concurrent `rm -rf`. The build lock did its job once (refusing run 2), but a
lock cannot help when the *script itself* changes mid-execution.

Both sessions reached the **same four-name failing set** by independent runs,
and agreed that the gate is red, that the recorded "empty failing set" is
false, that the B5-0017 baseline repair is genuine, that `--release 8` was
blamed on the wrong JDK, and that the `DAMAGE_ON_ATTACK` dead literals are the
cheapest real fix. That convergence is the strongest evidence available here.

One disagreement is recorded deliberately, and **it has since been adjudicated
in this report's favour - while also correcting one of its own arguments.**

This session claimed the other session's stdin-inheritance explanation was
impossible, and offered "31 per-class logs were written while only 4 rows
reached the table" as proof that the loop ran all 31 iterations.

**That leg of the argument was wrong.** `compile.sh` does `rm -rf "$GATE_LOG_DIR"`
at the start of every run, so when this session counted 31 logs in `out_gate\`
it was reading a *later* run's files, not run 4's. The 31-logs observation was
never run-4 evidence. This was caught by the independent adjudication at
`2026-10-19-opencode-big-pickle-01-gate-run4-adjudication.md` and is recorded
here rather than quietly dropped.

The conclusion nevertheless stands, on evidence this session did have:
`GATE-TRIAGE4.log`'s **console table carries all 31 rows**, with
`HeadlessHumanSeatProbe` PASSED at 185707ms followed by 26 further completed
classes. Under the `tee` version of the loop, console and file are fed from one
pipe, so 31 console rows means 31 iterations - the loop cannot have stopped
after four. Independently corroborated by arithmetic: run 4's first four rows sum
to 22796ms against a reported total of 23150ms, a 354ms residual consistent with
a torn fifth line rather than a truncated loop.

The adjudication also tested the *mechanism* this session proposed and **could
not reproduce it**: the `tee` flush race failed 300 trials out of 300. So the
honest status is the adjudicator's, not this session's original phrasing:

> loop fully exonerated; exact table-writer defect not yet pinned down.

The `< /dev/null` redirect is kept. It is harmless defensive hygiene, but the
causal claim written beside it - "HeadlessHumanSeatProbe reads stdin, consumed
the remaining 27 class names" - is an **impossible mechanism**, since `System.in`
appears nowhere in the 189-source tree, and it carries an unsupported
"Measured 2026-10-19 (B5-0026)" attribution. Correcting that comment is an open
`tooling/`-scope item and needs a claim and a DECISIONS entry; it has not been
done here.

## 5. Claims

Reaped at owner direction. Seven claims on B5-0028, B5-0029, B5-0030, B5-0032,
B5-0033, B5-0034 and B5-0035, all `claude-code-b5ccg-02`, all
`started_utc 2026-10-17T10:55:34Z`, about 31.5 h old against a 30-minute TTL.
The reaping was recorded in the ledger **before** removal, as boot step 9
requires. Three facts established abandonment rather than a live claim: the
holder's own heartbeat reports `live_claims: []`; byte-identical
`*.RELEASED.json` siblings exist; no heartbeat since.

The ledger note warns any future claimant to treat those rows as **not
promoted** and to re-derive the verdict from a gate run rather than from the
DECISIONS entry.

## 6. Answers to the proposal's open questions

| Question | Answer, measured here |
|---|---|
| Is the gate green? | No. 4 failures, of 31 classes run. The recorded claim of an empty failing set is false, and reproduced red by two independent sessions. |
| Are the eight files real? | No. Five empty interfaces, two `return true`, one uncalled substring scan. |
| Is S3 an `IndexOutOfBoundsException`? | **I first said no; that was wrong.** The class fails twice: S3 asserts (needs a ruling), and a later scenario crashes at `getAI:1912` on unguarded `aiPlayers.get(0)` (mechanical). See 2.2. |
| Was `--release 8` rejected by JDK 25? | No. JDK 8 was first on `PATH`; the staged JDK 25 accepts it. |
| Are claims live? | No. Seven, all ~63x their TTL, holder reporting `live_claims: []`. |
| Did the mojibake get fixed? | No. The fixing session caused it. Reverted. |
| Can `compile.bat` be trusted? | No, as pending. Its lock release fell through to the success tail, so a failed build printed "Build successful" and exited 0. Now fixed and demonstrated. |

## 7. Left undone, deliberately

* The four remaining red gate classes (2.2 to 2.4). **Revised after the 2.2
  correction: these are not four equally blocked items.** Two are mechanical and
  evidence-backed - the `DAMAGE_ON_ATTACK` dead literals (2.3) and the unguarded
  `getAI` fallback (2.2). Two genuinely need the owner: the S3 contradiction
  between B5-4160 and B5-0343, and the `CardSetManifestChecker` two-printing
  question. Only the engine changes were out of scope for this pass; the other
  two are waiting on a decision, not on permission.
* Ledger row repairs for B5-0004 and B5-0014 - **done by the concurrent
  session**, verified here: `lint-ledger.ps1` exits 0 with 33 rows.
* Governance text: the section 4 named-baseline clause, the STUB/PARTIAL/FULL
  vocabulary, `00_BOOT.md` step 7, and the promotion checklist. All reverted to
  HEAD and all awaiting the owner's decision.
* Untracking the seven force-added claim files and three heartbeat files from
  git, and the ~15 root debris files. Debris was moved to the gitignored
  `.agent/tmp_evidence/`, where the original gate logs are preserved as
  evidence for section 1. Nothing was deleted.
* B5-0018 (E-0) and everything downstream. Untouched.
* No commit, no push, no claim taken. The working tree is dirty by design and
  is fully backed up outside the repo.

## 8. Recommended next step

The gate now tells the truth, which is the precondition the proposal's Phase 0
was reaching for. There are **two mechanical fixes available without any ruling**,
and they are worth doing before anything else:

1. **`DAMAGE_ON_ATTACK` dead literals (2.3).** Remove the entries that name no
   pool title and correct the javadoc that asserts the opposite. This should turn
   `HeadlessCanonicalParityProbe` and `EffectCensusChecker` green together -
   two of the four red classes for one small engine edit.
2. **`getAI` fallback (2.2).** Return `null` on *no match*, not only on an empty
   list, and null-check all three call sites. This removes the crash and, more
   importantly, the silent wrong-seat `AIPlayer` return that is a live hazard
   in its own right.

Two items then remain and both are the owner's: the S3 auto-join contradiction,
and whether `CardSetManifestChecker` should accept one title in two printings.

Until those are opened and cleared, no promotion in `docs/DECISIONS.md` - the
nine FULL entries, the "verified" gate claim, or the assessor pass - should be
treated as supported.