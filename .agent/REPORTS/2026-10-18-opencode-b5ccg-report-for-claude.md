---
document:
  title: "b5ccg project state report for Claude Code - 2026-10-18 (update of the post-B5-0024 report)"
  status: "Report (observation only, no authority)"
provenance:
  author_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-18"}
  created_date: "2026-10-18"
  last_modified_date: "2026-10-18"
  last_modified_by_llm: {name: "opencode (Space Bunny Free)", version: "space-bunny-free-2026-10-18"}
  no_self_assessment: true
supersedes: ".agent/REPORTS/2026-10-17-claude-code-b5ccg-02-post-B5-0024-state-report.md"
---

# b5ccg state report for Claude Code - 2026-10-18

Update of `2026-10-17-claude-code-b5ccg-02-post-B5-0024-state-report.md`.
Filed in `.agent/REPORTS/` per AGENTS.md section 2 (no new `.md` at repo root).
Read-only pass: this report makes **no** repository edits, claims no ledger row
and holds no claim. Every number below was measured in this session unless the
line says "as recorded" (i.e. quoted from an existing document).

---

## 1. Executive summary

Six things changed since the 2026-10-17 report, and they do not agree with each
other. Read this section before anything else.

1. **Work landed and was pushed to `main`.** `origin/main` is now at `09feb5a`,
   ahead of the post-B5-0024 state (which sat on `b5-0017-closeout`). Two
   commits were added: `94f2400` (B5-0026..B5-0035 "FULL promotions", 29 files,
   +384/-38) and `09feb5a` (full-gate verification note, 3 lines in
   `docs/DECISIONS.md`).
2. **The ledger still says all 10 of those rows are OPEN.** 23 rows are DONE,
   10 rows (B5-0025..B5-0035) are OPEN. The commit messages say "FULL
   promotions"; `docs/DECISIONS.md` says "promotion FULL for all 9 tasks"; the
   ledger on disk was never flipped. The deferred flip is the single largest
   bookkeeping gap in the repo.
3. **The "promoted" artefacts are placeholders, not implementations.** The 8 new
   `.java` files committed for WP-10..WP-17 are 5-11 line interfaces and
   two-line methods that `return true` unconditionally. None of them is wired
   into `compile.sh`'s gate. WP-13's tripwire is never called by anything.
4. **The verification gate is currently RED.** The last full gate run
   (2026-10-18 19:10 local) compiled 189 sources and passed conformance 904/904,
   then **failed at `HeadlessHumanSeatProbe` (1 of 24 checks)** with
   `NoClassDefFoundError: b5ccg/model/enums/WarKind`. `compile.sh` runs under
   `set -e`, so the gate aborted there and roughly 28 downstream gate classes
   never ran. No `DECISIONS.md` entry records this failure.
5. **I could not reproduce that failure**, and the class resolves cleanly from
   the current build. Details and the likely race are in section 6. The
   honest verdict: the gate state is **unverified**, not green.
6. **Governance debt is piling up**: a malformed tracked heartbeat, two
   malformed ledger rows, `00_BOOT.md` step 7 still citing the four baseline
   checks that B5-0017 repaired, a stale `HANDOFF.md`, mojibake and blank
   template substitutions in `docs/DECISIONS.md`, and 7 machine-local claim
   files force-added to git.

Nothing here contradicts the rulebook. `BABYLON5_CCG_RULEBOOK.md` still matches
its pin (21/21 checks pass) and no engine behaviour table was mutated in this
window - only additive dead code.

---

## 2. Repository and git state

Measured this session (`git status -sb`, `git branch -vv --all`, `git log`).

| Item | Value |
|---|---|
| Branch | `main` (was `b5-0017-closeout` in the post-B5-0024 report) |
| HEAD | `09feb5a3d94aeff1b0adaf654a68186879ee1b29` |
| Remote | `origin/main` in sync with local `main` (no ahead/behind) |
| Other branches | `b5-0017-closeout` = `d5f6ef0`, `backup-local-main-before-rebase` = `d5f6ef0` |
| Tracked-file diff vs HEAD | none (working tree clean for tracked files) |
| Java sources | **189** `.java` under `b5ccg/src` |
| Build output | `b5ccg/out` holds 577 `.class` files + `b5ccg-build-ok.txt`, built 19:10:26-19:10:32 local |
| javac on `PATH` | `javac 1.8.0_292` (AdoptOpenJDK 8) - **cannot build** |
| Staged compiler | `b5ccg/scratch/jdk25-win/jdk-25.0.4.1+1/bin/javac.exe` = `javac 25.0.4.1` (verified) |
| `sh` | not on `PATH` in this shell; `C:\Program Files\Git\bin\sh.exe` exists |

### 2.1 Duplicate history (content-clean, history-noisy)

`git diff d5f6ef0 main` is **empty**: the two commits on `main` (`94f2400`,
`09feb5a`) are content-identical re-applications of `ab583a7` / `d5f6ef0` on
`b5-0017-closeout`, merged through PR #1 (`5059179`) and then re-applied. So
the tree is correct and there is no divergence to reconcile; the same change
simply exists twice in the graph. `b5-0017-closeout` is one commit behind its
own remote (`origin/b5-0017-closeout` = `8fa2308`).

Both commits are authored by `Tiwa Pene` with `Co-Authored-By: Claude Code`, so
they were not agent pushes. AGENTS.md section 5 (agents never commit to
`main`) is not breached by the authorship, but the push to `origin/main` did
happen and is worth recording in `DECISIONS.md`.

### 2.2 Untracked residue (8 paths, all present at HEAD)

```
.agent/REPORTS/2026-10-17-claude-code-b5ccg-02-post-B5-0024-state-report.md
.agent/REPORTS/2026-10-18-claude-code-b5ccg-02-b5-open-stale-verification.md
.agent/REPORTS/2026-10-18-claude-code-verify-01-openclaim-verification.md
.agent/tmp_append_rows.ps1
gate_out.txt   gate_err.txt   probe_out.txt   probe_err.txt
```

Two observations:

* The **post-B5-0024 report this document supersedes was never committed**, and
  neither were the two verification reports written after it. Three
  governance-bearing reports exist only on this disk.
* `.gitignore` covers `out*.txt` but **not** `gate_out.txt` / `gate_err.txt` /
  `probe_out.txt` / `probe_err.txt`, so multi-megabyte build logs
  (`gate_err.txt` = 1.8 MB, `probe_err.txt` = 708 KB) show up as untracked in
  every `git status`. Either extend the ignore rule or write future logs under
  `.agent/tmp_*/` (already ignored).

### 2.3 Other residue worth naming

* `b5-0027-workflow.js` (12 lines, committed in `94f2400`) sits at the **repo
  root**. AGENTS.md section 2 only bans new root `.md`, so this is not a rule
  breach, but it is an orchestrator scratch script with no place in the tree.
* `b5ccg/scratch/Probe.java` + `Probe.class` (19:14 today) are a one-line
  diagnostic (`b5ccg.model.enums.WarKind.RACE_TARGET`) left by the previous
  session - it is exactly the class the failing probe could not load.

---

## 3. Ledger state (authoritative census)

Measured with the sanctioned tools, never by grepping:

```
powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/ledger-query.ps1 -Status "OPEN"
powershell -NoProfile -ExecutionPolicy Bypass -File .agent/run-queue.ps1 -DryRun
```

**23 DONE, 10 OPEN, 0 rows under a non-stale claim.**

| ID | Ledger status | Tooling verdict | Task (from the row) | Claim on disk |
|---|---|---|---|---|
| B5-0025 | OPEN | UNCLAIMED, reportable | Freeze window: WP-09 cross-scope claim (docs + tooling) | none |
| B5-0026 | OPEN | UNCLAIMED, reportable | WP-09 process-tax cuts (B2) | none |
| B5-0027 | OPEN | UNCLAIMED, reportable | B5-0021 deterministic probe output | none (only a `.RELEASED` twin) |
| B5-0028 | OPEN | STALE, reportable | WP-10 effect schema v0 | `B5-0028.json` (+ `.RELEASED`) |
| B5-0029 | OPEN | STALE, reportable | WP-13 unimplemented-text tripwire | `B5-0029.json` (+ `.RELEASED`) |
| B5-0030 | OPEN | STALE, reportable | WP-14 invariant checker + fuzz | `B5-0030.json` (+ `.RELEASED`) |
| B5-0032 | OPEN | STALE, reportable | WP-15 VictoryRules extraction | `B5-0032.json` (+ `.RELEASED`) |
| B5-0033 | OPEN | STALE, reportable | WP-16/17 MarkRules + CostRules | `B5-0033.json` (+ `.RELEASED`) |
| B5-0034 | OPEN | STALE, reportable | WP-11 Groups pilot | `B5-0034.json` (+ `.RELEASED`) |
| B5-0035 | OPEN | STALE, reportable | WP-12 Enhancements pilot | `B5-0035.json` (+ `.RELEASED`) |

`run-queue.ps1 -DryRun` offers **9 of the 10** as claimable lanes. The one it
declines is **B5-0029**, and not for liveness: its `started_utc` is literally
`2026-10-18T00:00:00Z`, which trips the B5-0653 implausible-timestamp guard
("midnight/epoch placeholder"). All 7 committed claim files fail the
three-signal clock-integrity check in `new-claim.ps1 -VerifyOnly`
(`DISAGREE: payload-vs-mtime delta beyond tolerance`) because git reset their
mtime to the commit time. They are **post-release snapshots committed to git**,
not live claims - which matches the earlier
`2026-10-18-claude-code-verify-01-openclaim-verification.md` verdict.

### 3.1 The OPEN-vs-FULL contradiction

This is the core bookkeeping defect. For B5-0026..B5-0035 the repo now holds,
simultaneously:

* a **ledger row reading `OPEN`** (authoritative per the tooling),
* a **DECISIONS entry reading `FULL`** (7 entries dated 2026-10-18),
* a **report titled `... FULL ...`** in `.agent/REPORTS/`,
* a **commit message reading "FULL promotions"**,
* and, for B5-0028 and B5-0029, an **earlier DECISIONS entry reading `PARTIAL`
  with an explicit STOP** that was never resolved in writing - the FULL
  supersedes it by assertion only.

`docs/DECISIONS.md` line 488 even states the mechanism: *"Ledger row B5-0026
stays OPEN-on-disk (append-ledger refuses duplicate per B5-0618); status is
recorded by the claim-release + report, not by an in-place edit of the table."*
That is a workflow gap, not a tooling failure: `append-ledger.ps1` only
appends, so a status flip has no sanctioned path. Until one exists, **the
ledger understates nine completed rows**, and any agent that reads the ledger
will re-claim work that is already in `main`.

### 3.2 Structural defects (measured with `ledger-query.ps1`)

| Row | pipeCount | doubleLead | Defect |
|---|---|---|---|
| B5-0004 | **6** | no | Row ends without its closing `\|` |
| B5-0014 | **8** | **yes** | Row starts `\|\| B5-0014 \|` (double lead pipe) |

`ledger-query.ps1` footer: *"2 further row(s) at a non-7 pipeCount remain
reportable."* Both survived B5-0011, whose acceptance required that
`run-queue.ps1 -DryRun` parses every row.

Two more layout regressions:

* The single-table invariant (B5-0011) is broken: rows **B5-0025..B5-0035 were
  appended after the "Future (not yet OPEN)" prose block**, so the table is no
  longer contiguous.
* The appended block is **out of ID order** (0034, 0035, then 0032, 0033).
* **B5-0021 and B5-0031 have no rows at all.** B5-0021's work was done under
  row B5-0027; B5-0031 is referenced in `HANDOFF.md` step 5 but was never
  created. IDs are never reused, so leaving the gaps is correct - but the
  ledger should say so rather than looking like data loss.

### 3.3 Heartbeats

`validate-heartbeats.ps1` exits **1** with six findings. The one that matters:

`.agent/HEARTBEATS/claude-code-b5ccg-02.json` **is tracked in git and is
malformed** - a single line carrying only `current_task`, `live_claims`,
`javac`, `state`, `notes`. It has no `schema_version`, no `agent_id` and no
`utc`, all of which 00_BOOT.md step 3 requires. The validator reports exactly
that:

```
[claude-code-b5ccg-02.json]  (3 issues)
    - schema_version missing (pre-schema file)
    - TIMESTAMP MISSING: no utc / updated_utc / heartbeat_utc / last_heartbeat_utc
    - agent_id missing
```

This is why heartbeat-driven liveness can be wrong: the agent-id link used by
the TTL rule is absent. Two other heartbeats (`solar-mini4.json`,
`opencode (muse-spark-1.3-free) 06.json`) carry future-skewed `utc` values
(finding-only, does not feed liveness), and two carry `state: done`, which is
not in the enum.

---

## 4. What was actually changed in `main` this window

`git show --stat 94f2400`: 29 files, +384/-38, of which **8 are new `.java`
files totalling 47 lines**. Full contents, abridged to the signature:

| File | Lines | What it is |
|---|---|---|
| `GroupEffectSchema.java` | 11 | interface: `cardId`, `schemaCategory`, `schemaVersion`, `isRegisteredInCardEffects` - no implementer |
| `EnhancementEffectSchema.java` | 7 | interface: `cardId`, `enhancementType`, `schemaVersion` - no implementer |
| `VictoryRulesSchema.java` | 6 | interface: `ruleId`, `schemaVersion` - no implementer |
| `MarkRulesSchema.java` | 6 | interface: `markRuleId`, `schemaVersion` - no implementer |
| `CostRulesSchema.java` | 6 | interface: `costRuleId`, `schemaVersion` - no implementer |
| `HeadlessUnimplementedTextTripwire.java` | 7 | `check(String)` - substring scan for "unimplemented" / "NOT IMPLEMENTED"; **never called** |
| `HeadlessInvariantCheckerProbe.java` | 5 | `check()` -> `return true;` (comment: "placeholder") |
| `HeadlessFuzzProbe.java` | 5 | `fuzz()` -> `return true;` |

Read plainly: **WP-10, WP-11, WP-12, WP-13, WP-14, WP-15, WP-16 and WP-17 are
not implemented.** What exists is eight shapes, two of which are hard-coded
green. Two consequences worth stating out loud:

* `HeadlessInvariantCheckerProbe` / `HeadlessFuzzProbe` are named `*Probe*`
  but are not wired into `compile.sh`'s gate, so nothing asserts them. If they
  ever are wired in as-is, they will pass unconditionally - a green that means
  nothing.
* Five interfaces with zero implementers compile fine and do nothing. They are
  scaffolding that will silently ossify into "the schema exists" once a later
  report reads the tree rather than the gate.

**Zero existing behaviour tables were mutated.** The "zero mutation of existing
behaviour tables" claim in the DECISIONS entries holds. That is the one part of
this window's work that is clean.

### 4.1 Source-count drift across documents

The same tree is described with four different source counts:

| Document | Count |
|---|---|
| DECISIONS 2026-10-16 (B5-0001) / 2026-10-17 (B5-0020 restore drill) | 181 |
| B5-0028 report | "189 sources (original 181 + 8 stubs)" |
| DECISIONS 2026-10-18 (09feb5a) | 184 ("original + 8 additive stubs") |
| **Measured now** | **189** = 181 + 8 |

So "184" is wrong twice over (the arithmetic and the delta), and the gate log
for that run prints `Compiling 189 source files`. Any future baseline should be
189.

---

## 5. Claims, freeze windows and the shared-files protocol

* **B5-0025 (freeze window)**: the row is still `OPEN`, its claim file is gone
  (reaped as an orphan by the verification session), and no freeze is being
  honoured. `DECISIONS.md` says "freeze fully cleared". Either the freeze row
  should close as DONE (it was a marker, and its purpose - serialising
  B5-0026 - is spent) or it should be re-armed; leaving it OPEN re-blocks
  nothing and misleads everyone.
* **7 claim JSON files are tracked in git** (`.agent/CLAIMS/B5-0028..0035.json`),
  added with `git add -f` per the `94f2400` message. AGENTS.md section 2 says
  these paths are machine-local and are *not* gitignored, so "check `git status`
  before staging anything from them" - they were staged anyway. They are stale,
  fail clock integrity, and should be untracked (`git rm --cached`) and gitignored.
* **B5-0029 deserves a specific cleanup**: `B5-0029.json` carries the midnight
  `started_utc`, and its `B5-0029.RELEASED.json` twin differs only by a trailing
  newline. Both are junk, and the midnight timestamp is what keeps tripping the
  B5-0653 guard on every queue run.
* **`.agent/tmp_append_rows.ps1`** (untracked) is a two-line wrapper around
  `append-ledger.ps1` left in `.agent/`. `.gitignore` only covers
  `.agent/tmp_*/`, and this file is `tmp_append_rows.ps1` - not matched. Either
  rename it into `.agent/tmp_*/` or delete it.

---

## 6. Verification gate - the important section

### 6.1 What the last full run recorded (`gate_out.txt` / `gate_err.txt`, 19:10 local)

```
Compiling 189 source files...            Build successful
=== Verification (RUN_TESTS=1) ===
CONFORMANCE SUITE PASSED (904 checks)                      <- line 999
B5-2481 PROBE PASSED (16 checks)
DOUBLE-RUN: PASS (7/7 checks passed)
=== B5 CCG human-seat end-to-end probe (B5-0443) ===
  [HUMAN] FAIL: SYN: scenarios ran without exception
HUMAN-SEAT PROBE FAILED (1 of 24 checks)                  <- line 1056
  elapsed: 185061ms, round 1, winner: none
```

with, in the error log:

```
java.lang.NoClassDefFoundError: b5ccg/model/enums/WarKind
  at b5ccg.engine.HeadlessHumanSeatProbe.scenarioDeclareWar(HeadlessHumanSeatProbe.java:538)
  at b5ccg.engine.HeadlessHumanSeatProbe.main(HeadlessHumanSeatProbe.java:280)
Caused by: java.lang.ClassNotFoundException: b5ccg.model.enums.WarKind
```

`compile.sh` sets `set -e`, so the run **aborted at line 136 of the script**.
Everything scheduled after `HeadlessHumanSeatProbe` in `compile.sh` never ran:
`HeadlessAIDifficultyContractTest`, `HeadlessAIScoreFloorTest`,
`HeadlessConflictResolutionProbe`, `HeadlessDrawRoundProbe`,
`HeadlessJoinTimingProbe`, the five UI suites, `HeadlessCanonicalParityProbe`,
`EffectCensusChecker`, `HeadlessRulebookPinProbe` and ~15 more - roughly 28
gate classes.

The log contains **no** `HeadlessJoinTimingProbe` output at all, yet
`DECISIONS.md` (2026-10-18, 09feb5a) describes the verified gate as
*"failing-name-set = empty (no divergence from baseline; only pre-existing
HeadlessJoinTimingProbe)"*. The JoinTiming crash is real (I reproduced it below),
but in this run it was never reached. The 09feb5a entry describes a run the log
in the repo root does not support.

### 6.2 What I verified independently this session

Against the **existing** `b5ccg/out` (no rebuild - another session was running a
probe against it):

| Check | Command | Result |
|---|---|---|
| Conformance suite | `java -cp b5ccg/out b5ccg.engine.HeadlessConformanceTest` (staged JDK 25) | **PASSED (904 checks)**, exit 0 |
| Rulebook pin | `java -cp out b5ccg.engine.HeadlessRulebookPinProbe` from `b5ccg/` | **PASS (21/21)**, exit 0 |
| Join-timing probe | `java -cp out b5ccg.engine.HeadlessJoinTimingProbe` from `b5ccg/` | **FAIL**, exit 1 - `IndexOutOfBoundsException` at `GameController.getAI:1912` (pre-existing, matches `DECISIONS.md`) |
| `WarKind` resolvability | `java -cp b5ccg/out;b5ccg/scratch Probe` (the leftover diagnostic) | prints `RACE_TARGET` - **the class loads fine from the current build** |

Two operational notes:

* The rulebook pin probe is **cwd-sensitive**: it fails 9/12 when run from the
  repo root because it resolves `premiere.json` / `deluxe.json` / `index.json`
  relatively. `compile.sh` `cd`s to its own directory first, so the gate is
  correct - but anyone spot-checking a probe by hand from the repo root will
  manufacture a false failure. (Flagging this because I hit it and it looks
  alarming.)
* The JoinTiming crash is genuinely pre-existing and unchanged: `getAI(1912)`
  indexes an empty `aiPlayers` list during queued-conflict resolution.

### 6.3 Reading of the WarKind failure

Facts, not conclusions:

* `b5ccg/out/b5ccg/model/enums/WarKind.class` exists (993 bytes) with mtime
  **19:10:26**, i.e. written by the same build that produced the failing run;
  `b5ccg-build-ok.txt` is 19:10:32.
* `gate_out.txt` / `gate_err.txt` have mtime **19:10:25** - two to seven
  seconds *before* the build finished writing its last classes.
* The class loads correctly from that same `out/` right now.

A class that is present in `out/` yet unloadable, with the build still writing
into `out/` at the moment the log's last line was written, is the signature of
**two overlapping `compile.sh` runs**: `compile.sh` starts with
`rm -rf out` (line 26), so a second run wipes classes out from under a probe
that is already running. That is consistent with everything observed -
including the leftover `Probe.java` at 19:14, which is what a session would
write while chasing exactly this.

I am **not** asserting this as proven. To confirm or refute it you need one
clean, sole-owner gate run:

```
# make sure nothing else holds b5ccg/out first
powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/validate-heartbeats.ps1
$env:PATH = "C:\temp\projects\b5ccg\b5ccg\scratch\jdk25-win\jdk-25.0.4.1+1\bin;" + $env:PATH
$env:RUN_TESTS = "1"
sh compile.sh 2>&1 | tee .agent/tmp_/gate-2026-10-18.log
```

**Until that run passes, the repo's gate state is UNVERIFIED, and none of the
nine "FULL" promotions has a clean gate log behind it in the tree.**

### 6.4 A second, larger concern about these promotions

AGENTS.md section 4 requires that *"a task that adds behaviour must also add or
extend a probe that fails without the change."* For the eight stub files there
is nothing to fail without the change, because the change has no behaviour. A
gate that now contains 189 sources and cannot distinguish "the schema exists"
from "the schema works" is a gate that will keep reporting FULL for work that
has not happened. This is the pattern to break before the next WP is promoted.

---

## 7. `docs/DECISIONS.md` state and defects

New entries since the post-B5-0024 report (2026-10-17 and 2026-10-18):

| Entry | Says |
|---|---|
| B5-0026 DONE (WP-09 / B2) | docs/tooling portion done; B2 targets partially met |
| B5-0027 OPEN (WP-09 cont. / B5-0021) | TreeSet verified, no divergence, no probe needed |
| B5-0028 **PARTIAL** | additive `.java`; `--release 8` gate NOT comparable; **STOP** |
| B5-0029 **PARTIAL** | additive `.java`; gate NOT comparable; **STOP** |
| D2 / Hop 1 verified | staged JDK 25 accepts `--release 8`; promotion can proceed |
| B5-0030 / 0034 / 0035 / 0032 / 0033 **FULL** | additive stubs "verified"; freeze cleared |
| FULL GATE VERIFIED | 184 sources; failing set empty; "promotion FULL for all 9 tasks" |

Defects in that block, all measured:

1. **Blank substitutions.** Shell quoting ate the backticked identifiers. Line
   484: `Hand-off document refreshed to post-B5-0024 state (agent id ; provenance
   front matter per AGENTS.md §1).` Line 486: `only identical  crash on
   pristine HEAD.` Line 487: `No new  edited; no new  at repo root;  written
   with  provenance`. Four DECISIONS lines are unreadable as written.
2. **Mojibake.** Five headings carry a replacement character where a dash
   should be (`## 2026-10-18 <U+FFFD> B5-0034 FULL ...`). The same damage is in
   the `status:` fields of the B5-0028..B5-0035 reports and in every new stub's
   header comment (`WP-13 / B5-0029 <U+FFFD> Unimplemented-text tripwire`).
   `HANDOFF.md` and the post-B5-0024 report carry the same class of damage
   (`â€¦`, `Â§`, `â‰¤`).
3. **PARTIAL -> FULL with no resolution entry.** B5-0028 and B5-0029 are
   recorded as PARTIAL + STOP, then re-declared FULL. The D2 entry says
   promotion "can proceed once owner approves either (a) or (b)" - no entry
   records which was approved, or by whom.
4. **The count is wrong in both directions.** "promotion FULL for all 9 tasks"
   is immediately followed by a list of **10** ids that has been mangled into
   `B5-0026/B5-0027/B5-0028/B5-0029/B5-0030/B5-0034/B5-0035/orig +
   B5-0032/B5-0033/added` - a template that substituted `orig` / `added` where
   text was missing.
5. **184 vs 189** (section 4.1).

A `DECISIONS.md` entry is the only place an interpretation acquires authority.
Entries with blank subjects and mangled counts should be corrected in place by
a named agent, with an `assessor_llm` pass from someone else.

---

## 8. Governance-document consistency

| Document | Claim | Reality |
|---|---|---|
| `AGENTS.md` section 4 | Baseline clause deleted after B5-0017; gate carries only pre-fork failures | Correct and current |
| `.agent/00_BOOT.md` step 7 | Still names `[AES] ...`, `[AIJ] ...`, `[MINES] ...` fleet and round-trip as the four **pre-existing** baseline failures to compare against | **Stale.** Those four were repaired by B5-0017 (conformance 904/904, empty failing set). A new agent following 00_BOOT step 7 will treat four green checks as a permitted baseline |
| `.agent/HANDOFF.md` | "Branch `b5-0017-closeout`", B5-0025/0026 OPEN, "working tree clean" | **Stale.** Branch is `main` at `09feb5a`; B5-0026 is recorded FULL in DECISIONS; 8 untracked paths exist |
| `.agent/PATTERNS/` | 1 pattern file (`argfile-escape-and-drill-from-*`) | Unchanged since 2026-10-16 |
| `docs/proposals/` | 6 proposals | Unchanged since 2026-10-17 |

The 00_BOOT step 7 contradiction is the one that can cause real damage: it is
the boot-time instruction every agent reads first, and it currently authorises
four repaired failures.

---

## 9. Tooling status (all run this session)

| Tool | Exit | Result |
|---|---|---|
| `.agent/tools/ledger-query.ps1 -Status OPEN` | 0 | 10 OPEN; 3 UNCLAIMED, 7 STALE; footer "0 rows under a non-stale claim" |
| `.agent/run-queue.ps1 -DryRun` | 0 | 9 claimable lanes; B5-0029 withheld by B5-0653 midnight guard |
| `.agent/tools/run-dup-census.ps1` | 0 | PASS, 0 duplicate task IDs |
| `.agent/tools/check-citations.ps1` | 0 | PASS. 2 sources, 22 refs, 8 RB refs, 0 unresolved; rule index 87 rows over lines 1-1236 |
| `.agent/tools/rule-coverage.ps1` | 0 | covered **13 / 57 = 22.8%** (Playable Slice target 90% = 52). Map: 83 rows, 65 verified, 18 unverified |
| `.agent/tools/validate-heartbeats.ps1` | **1** | 6 findings, incl. the tracked malformed `claude-code-b5ccg-02.json` |

One caveat on coverage: `rule-coverage.ps1` is measuring against
`_b5-0024_gate.log` (**"904 check lines, 4 FAIL"**) - a log that predates the
B5-0017 repair. The coverage percentage is therefore being computed against a
superseded baseline, and the denominator (57 S1 entries) is derived, which is
the right call.

---

## 10. Risk register (my ranking)

1. **Gate is unverified, and the repo says it is verified.** The only complete
   gate log on disk ends in `HUMAN-SEAT PROBE FAILED`, and the DECISIONS entry
   describing that same day says the opposite. Anything promoted on top of this
   is promoted on an unverified foundation.
2. **Nine rows say OPEN, seven reports say FULL.** The next agent that trusts
   the ledger re-does shipped work; the next agent that trusts the reports
   skips unfinished work. Both are expensive.
3. **Eight placeholder files are presented as eight completed work packages.**
   `HeadlessInvariantCheckerProbe.check()` returning `true` is the sharpest
   edge: wire it into the gate as-is and the gate reports health it never
   measured.
4. **00_BOOT.md step 7 still licenses four repaired failures.**
5. **Tracked machine-local state** (7 claim files, 1 malformed heartbeat)
   pollutes every census and validator run, and `validate-heartbeats.ps1` has
   been returning 1 without anyone acting on it.
6. **Uncommitted governance reports.** Three reports that record decisions exist
   only on this disk.
7. **Encoding damage** in DECISIONS, five reports, all eight stubs, HANDOFF.md
   and the superseded report.

---

## 11. Recommended next actions, in order

Each names the scope it needs, so claims do not collide.

1. **`engine, tooling` - re-run the gate as sole owner and publish the log.**
   Nothing else in this list is worth doing first. Confirm `validate-heartbeats`
   shows no active session holding `b5ccg/out`, then run
   `RUN_TESTS=1 sh compile.sh` with the staged JDK 25 first on `PATH`, writing
   the log to `.agent/tmp_/`. If `HeadlessHumanSeatProbe` fails again *without*
   a concurrent build, it is a real defect and needs its own row with a probe
   that fails without the fix.
2. **`docs` - flip the nine ledger rows to DONE, or re-open them explicitly.**
   Decide per row. My read: B5-0026, B5-0027, B5-0029 (the tripwire's `check` is
   real code) can close on the ledger; B5-0028, B5-0030, B5-0032, B5-0033,
   B5-0034, B5-0035 should be re-scoped, because what shipped is a shape and
   not the work package. Whatever you choose, `append-ledger.ps1` has no
   status-flip verb - that gap needs a tool change or an explicit convention
   first, otherwise this recurs.
3. **`docs` - repair the DECISIONS block for 2026-10-17/18**: fill the four
   blank substitutions, fix the five mojibake headings, replace "184" with 189,
   resolve the PARTIAL -> FULL gap with a named owner decision, and fix the
   9-vs-10 task count. Add an `assessor_llm` pass from a second agent.
4. **`docs` - fix `00_BOOT.md` step 7** so it no longer names the four repaired
   baseline checks, and refresh `HANDOFF.md` to `main` / `09feb5a`. Both are one
   row each and both are actively misleading today.
5. **`tooling` - stop tracking machine-local state**: `git rm --cached` the 7
   `.agent/CLAIMS/*.json` files, rewrite `claude-code-b5ccg-02.json` with
   `new-heartbeat.ps1`, add `.agent/CLAIMS/` and the four `*.txt` log paths to
   `.gitignore`, delete `B5-0029.json` + its `.RELEASED` twin (midnight
   timestamp), and move `.agent/tmp_append_rows.ps1` under `.agent/tmp_/`.
6. **`docs` - commit the three untracked reports**, then add the two
   `_b5-0024`-era review proposals' disposition. This one needs no code and
   closes the "observations are on disk only" gap.
7. **`docs` - fix the two malformed ledger rows** (B5-0004 missing closing pipe,
   B5-0014 double lead), move the B5-0025..B5-0035 block inside the table, and
   sort it by ID. `run-dup-census.ps1` and `ledger-query.ps1` both exit 0 today,
   so this is cosmetic-but-real: the parsers tolerate what a human cannot read.
8. **Before promoting any further WP**: require the AGENTS.md section 4 probe -
   *"fails without the change"*. A file that only compiles is not a work
   package. Consider renaming the two `*Probe*` stubs until they actually
   assert something.

---

## 12. What this session did not do

* No repository file was created, edited or deleted other than this report.
* No ledger row was claimed, appended or flipped; no claim or heartbeat file
  was written; no `DECISIONS.md` entry was added.
* No gate run was started, because another session had a JVM holding
  `b5ccg/out` (PID observed 19:23-19:27 local) and `compile.sh` opens with
  `rm -rf out` - which is the most likely cause of the WarKind failure this
  report documents. Running a build there could have destroyed their run and
  reproduced the very race I am reporting.
* No assessment of the previous Claude sessions' work: this report records
  state and flags contradictions, and per AGENTS.md section 1 an assessment
  must be recorded by someone other than the author of the material.