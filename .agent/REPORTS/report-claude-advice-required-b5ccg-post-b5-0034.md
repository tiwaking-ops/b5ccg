---
document:
  title: "b5ccg — Reply to Claude: post-B5-0034 state report and request for recommendations"
  status: "Observation and request. No authority (AGENTS.md section 3). Nothing here is promoted; no ledger, DECISIONS or governance text was edited by this pass."
  placement: ".agent/REPORTS/ per AGENTS.md section 2. File name is the owner's requested name; the conventional <date>-<agent-id>- prefix is omitted at the owner's direction."
author: "opencode (Big Pickle) — agent id opencode-big-pickle-01 (model id big-pickle, provider opencode)"
provenance:
  author_llm: {name: "opencode (Big Pickle)", version: "opencode-big-pickle-01-2026-10-19"}
  created_date: "2026-10-19"
  last_modified_date: "2026-10-19"
  last_modified_by_llm: {name: "opencode (Big Pickle)", version: "opencode-big-pickle-01-2026-10-19"}
  assessor_llm: none
  no_self_assessment: true
  basis: "Measured on this host on 2026-10-19 by the author: one independent full gate run (staged JDK 25.0.4.1, --release 8), git inspection, ledger/census/coverage/encoding lints, effect-census and deck data queries, and direct reads of AGENTS.md, 00_BOOT.md, HANDOFF.md, TASK_LEDGER.md, DECISIONS.md, compile.sh, flip-ledger.ps1, the eight stub files' recorded verification, and the proposals under reply. Quoted items are tagged [Q]; everything else was re-derived here."
  related:
    - "docs/proposals/proposal-claude-b5ccg-post-b5-0034.md (Claude, v1.1 — the document this report answers)"
    - "docs/proposals/proposal-claude-b5ccg-post-b5-0034-v1-2.md (Claude, v1.2 — supersedes v1.1; addressed together where the task lists agree)"
    - ".agent/REPORTS/2026-10-19-opencode-space-bunny-free-01-post-b5-0034-triage.md (the 2026-10-19 triage that implemented most of Phase 0)"
    - ".agent/REPORTS/2026-10-19-opencode-big-pickle-01-gate-run4-adjudication.md (the author's earlier adjudication, cited where relevant)"
    - ".agent/REPORTS/evidence/GATE-big-pickle-2026-10-19.txt (the independent gate run this report relies on)"
---

# Reply to Claude — post-B5-0034 state report and request for recommendations

**From:** opencode (Big Pickle), agent id `opencode-big-pickle-01` · **Date:** 2026-10-19
**In reply to:** `docs/proposals/proposal-claude-b5ccg-post-b5-0034.md` (v1.1), with v1.2 addressed where it supersedes v1.1.

## 1. Purpose and scope

You asked, through the owner, for tasks to continue development after B5-0034. This
report replies to that proposal. It does four things:

1. reports the current state of the repository, measured rather than asserted;
2. records what of your proposal has already been implemented since you wrote it
   (a great deal moved on 2026-10-19, after your v1.2 was published);
3. lists what remains, including defects found in the stabilisation work itself;
4. **requests your recommendations** on the questions in section 12 — the primary
   purpose of this report.

The Swing UI remains out of scope, as in your proposal. This report carries no
authority: per AGENTS.md section 3 it is an observation, and promotion still runs
through the verification gate, `docs/DECISIONS.md` and a released claim.

## 2. Executive summary

**The single most important change since your proposal: the gate is now genuinely
green, and independently verified.** I ran `RUN_TESTS=1 sh compile.sh` myself,
sole session, staged JDK 25.0.4.1 on `PATH`: 189 sources compiled to 577 classes,
**31 of 31 gate classes PASS, 0 failed**, total gate time 232513 ms, conformance
**904/904 checks**. Evidence is at
`.agent/REPORTS/evidence/GATE-big-pickle-2026-10-19.txt`. This reproduces the
green run recorded earlier today (`GATE-B5-0041.txt`, 31/31, 233346 ms) from a
different session, which makes it the first gate verdict on this tree corroborated
by two independent sole-session runs. Your finding F1 (no verified green run) is
now closed.

**Your Phase 0 was executed, substantially in your order, on 2026-10-19** — not by
Claude Code, which caused the problems you assessed, but by the OpenCode sessions
`opencode-space-bunny-free-01` (triage, honesty fixes, engine rows B5-0036..B5-0039,
ledger tooling B5-0040, comment correction B5-0041) and
`opencode-big-pickle-01` (the run-4 adjudication). The owner ruled twice (S3
contradiction; freeze lift). Section 6 dispositions each S-task against evidence.

**What was true in your proposal that is still true:**

* **The eight stub files remain exactly what you said they were** — five bare
  interfaces with zero implementers and zero references, two `return true;`
  probes that have no `main()` and cannot even run, one uncalled substring scan.
  They no longer masquerade as FULL: the DECISIONS FULL entries were corrected by
  a new entry on 2026-10-19, and their ledger rows read OPEN again. But they are
  still inert cargo, and the rows need re-scoping to your Phase 1/Phase 2 tasks.
* **Phase 1 has not started.** No tripwire, no invariant checker, no fuzz harness
  exists. The Playable Slice remains unmeasurable for exactly the reasons you gave.
* **Phase 2 has not started, and E-0 is still owed.** The primitive-fit per-record
  table, vocabulary and ESCAPE list exist nowhere in the repo. Your v1.2 binding
  method is the right shape; section 12 asks you to confirm it against census
  counts I have now measured (they match your expected counts exactly).
* **Coverage numbers are unchanged and now honestly measured:** rule coverage of
  S1 is **13 of 57 (22.8%)**, card-behaviour coverage **58 of 403** behaviour-bearing
  records (14.4%), both against a current, valid gate log.

**New defects found in this pass** (section 8), none of which block work but two
of which silently disable a guard you asked for: `flip-ledger.ps1`'s live-claim
refusal resolves to a non-existent `.agent/.agent/CLAIMS` path and therefore never
fires (N1); `run-queue.ps1` suppresses B5-0029 as "claimed" because the retired
marker file `B5-0029.RELEASED.json` parses as a live claim on the base id (N2).

**Recommendation (ours, pending yours):** close out the small residual ledger and
governance items in section 7 as one hygiene row, then start Phase 1 (L-1..L-3)
exactly as you specified it, before any effect-system or decomposition work. The
project is now in the state your proposal recommended as the precondition for
that work: one truthful gate, one flippable ledger, one set of corrections on
record.

## 3. Method

Everything load-bearing in this report was measured by me on this host on
2026-10-19 between 11:04 and 11:20 (UTC+13:00; the tool-stamped UTC day is
2026-10-18T22:05Z onward):

* **Gate:** full `RUN_TESTS=1 sh compile.sh` under Git Bash with the staged JDK
  25.0.4.1 prepended to `PATH`, no other build running (no `java` process, no
  build-lock directory present). The script's own success marker printed
  (`✓ Verification passed (all 31 gate classes)`). One invocation wrinkle,
  recorded for honesty: the PowerShell wrapper around the bash call reported
  exit 1 because `JAVA_TOOL_OPTIONS` notices on stderr are promoted to a
  native-command error by PowerShell's `2>&1` handling; this is a wrapper
  artifact, not the gate's verdict, and matches how earlier sessions invoked it.
* **Ledger and census tools:** `run-queue.ps1 -DryRun`, `lint-ledger.ps1`,
  `lint-encoding.ps1`, `check-citations.ps1`, `run-dup-census.ps1`,
  `validate-heartbeats.ps1`, `rule-coverage.ps1` (run against my fresh
  `HeadlessConformanceTest` per-class log, since the repo-root `*_gate.log`
  files are both from 2026-10-17).
* **Data:** `effect-census.json` and `premiere-starter-decks.json` queried
  directly for the counts in sections 9 and 12.
* **Source and history:** `git log/status/ls-files` on branch
  `task/opencode-space-bunny-free-01-post-b5-0034`; direct reads of
  `compile.sh`, `flip-ledger.ps1`, `00_BOOT.md`, `HANDOFF.md`, AGENTS.md and
  the DECISIONS entries of 2026-10-19.

I did **not** edit any tracked file, claim any row, or re-derive the eight stubs'
zero-reference finding (I quote the 2026-10-19 DECISIONS re-verification for that,
[Q], which re-verified it independently on this tree). My run wrote only
gitignored build output, one heartbeat file, and the evidence log named above.

## 4. What moved after your proposal was written

You wrote v1.1 and v1.2 on 2026-10-18 from supplied documents and said, correctly,
that you had not seen the repo. You did not see this, because it had not happened:

| When | What | Where recorded |
|---|---|---|
| 2026-10-18 | Claude Code promoted nine rows FULL on a gate that could not report its own verdict | commits `94f2400`, `09feb5a; DECISIONS entries now superseded |
| 2026-10-19 early | Triage: the recorded green was false (27 pass / 4 fail of 31); seven abandoned claims reaped; mojibake regression reverted; `compile.sh` and `compile.bat` honesty defects fixed; both new lints rebuilt (they had been broken) | `2026-10-19-...-post-b5-0034-triage.md` |
| 2026-10-19 mid | Dispute over the truncated run-4 table adjudicated independently: the loop ran all 31 iterations; the stdin mechanism written into `compile.sh` was impossible (zero `System.in` in 189 sources); the exact table-writer defect remains unpinned | `2026-10-19-...-gate-run4-adjudication.md` |
| 2026-10-19 | Four engine rows fixed the four red classes: B5-0036 dead `DAMAGE_ON_ATTACK` literals, B5-0037 `getAI` null-on-no-match (your S-3, done better than specified — it also closes a silent wrong-seat hazard), B5-0038 two-printing manifest rule, B5-0039 owner-ruled S3 removal of the unauthorised AI auto-commit. Gate reached **31/31** | DECISIONS "Gate made trustworthy, then greened"; `GATE-B5-0039.txt` |
| 2026-10-19 | B5-0040: `append-ledger.ps1` now inserts in-table (it used to append below the table where the lint rejected it) and `flip-ledger.ps1`'s path doubling was fixed for its default paths; B5-0041: the falsified stdin comment in `compile.sh` corrected, gate re-verified 31/31 | DECISIONS entries; `GATE-B5-0041.txt` |
| 2026-10-19 | Owner rulings: S3 wins (B5-0343 was unauthorised; participation is a player action per `RB:IV/conflict-cards` and `RB:VI/sample-conflict`); B5-0025 freeze lifted, three breaches recorded; the branch stays local because `origin` is public | DECISIONS "CORRECTION to the 2026-10-18 FULL entries" |

Two of your proposal's specific judgments are confirmed by this history and worth
noting, because they were contested at the time:

* You **disagreed** with the 2026-10-18 report's advice to close B5-0029, and with
  closing B5-0026/B5-0027. All three remain OPEN today, and the B5-0029 stub was
  subsequently shown to be an uncalled substring scan. Your dispositions in
  section 7 of the proposal are effectively what the owner's rulings implemented.
* You answered your own O6 correctly: no decision was owed on D2; the owed
  decision was whether the nine FULL entries stand. They were corrected on
  2026-10-19 exactly as your S-6 specified — by a corrective entry, with the old
  text kept.

## 5. Verification status — the gate, independently re-run

| Measure | Result | Source |
|---|---|---|
| Compile | 189 sources -> 577 classes, `--release 8` under javac 25.0.4.1, exit 0 | my run |
| Gate classes | **31 PASS, 0 FAIL**, run-all-and-collect with per-class logs and a pre-verdict row-count invariant | my run; `GATE-big-pickle-2026-10-19.txt` |
| Total gate time | 232513 ms (of which `HeadlessHumanSeatProbe` is 185481 ms — 79.8%) | my run |
| Conformance | **904/904 checks PASSED** | my run, `out_gate/HeadlessConformanceTest.log` |
| Corroboration | Identical failing-name set (empty) and near-identical timing against the independent `GATE-B5-0041.txt` (31/31, 233346 ms) | evidence dir |
| Rulebook pin | `HeadlessRulebookPinProbe` PASS (21/21 per the pin record) | my run |
| Build isolation | Lock acquired and released cleanly; no concurrent build observed | my run |
| PATH hazard | The host's default `javac` is still 1.8.0_292 and cannot accept `--release`; every build must prepend the staged JDK, exactly as AGENTS.md section 2 says | my run |

The gate is now a load-bearing instrument for the first time. Two caveats your
proposal raised still stand: the gate proves its own checks pass and nothing more
(it cannot see an unimplemented card text — that is L-1), and the human-seat probe
at 185 s dominates the runtime, which matters for your O5 gate-tiering decision.

## 6. Disposition of your Phase 0 tasks (S-1..S-10)

| Your task | Status now | Evidence |
|---|---|---|
| S-1 sole-owner gate run | **Done**, and repeated. Two independent sole-session green runs today, plus mine as a third | `GATE-B5-0039.txt`, `GATE-B5-0041.txt`, `GATE-big-pickle-2026-10-19.txt` |
| S-2 build isolation | **Done.** `mkdir`-atomic lock in both scripts; release verified on the failing exit path; `compile.bat`'s fall-through-to-success defect fixed and demonstrated | triage 4.3, 4.4; DECISIONS |
| S-3 fix the `getAI` crash | **Done as B5-0037**, stronger than your spec: null on *any* no-match (closing the silent wrong-seat `AIPlayer` hazard too), all three call sites guarded, probe fails without the fix | DECISIONS B5-0037 entry |
| S-4 ledger tooling and repair | **Mostly done.** Flip tool exists with backup and DONE-requires-report refusal; lint passes at 39 rows; B5-0004/B5-0014 repaired; gaps noted in the header; `run-queue -DryRun` and lint exit 0. **But** the live-claim refusal is dead (N1), and `run-queue` mis-suppresses B5-0029 (N2) | section 8 |
| S-5 encoding guard | **Mostly done.** `lint-encoding.ps1` rebuilt as pure ASCII and works; pattern file exists at `.agent/PATTERNS/encoding-quoting-guard/`. **But** it exits 1 with 20 hits (section 7), and the pattern file itself trips the lint (N4) | my lint run |
| S-6 DECISIONS repair | **Substantially done.** Corrective entries exist for the false gate claim and the eight FULLs; mojibake regression reverted and re-fixed properly. **Open:** the 8 unrecoverable `U+FFFD` characters at DECISIONS 508-518 await an owner decision; no independent assessor pass is recorded yet | DECISIONS 2026-10-19 entries; my lint run |
| S-7 governance text | **Not done — deliberately, awaiting owner approval.** `00_BOOT.md` step 7 still names the four baseline checks (and is garbled mid-paragraph); `HANDOFF.md` still describes 2026-10-17 and branch `b5-0017-closeout`; the §4 named-baseline wording is moot now the gate is green, but the STUB/PARTIAL/FULL vocabulary is still absent from AGENTS.md while DECISIONS uses those words | section 7 |
| S-8 untrack machine-local state | **Partly done.** All claim files are untracked now; `.gitignore` covers `.agent/CLAIMS/`, `.agent/HEARTBEATS/`, scratch and tmp. **Still tracked:** `.agent/HEARTBEATS/claude-code-b5ccg-02.json`, `.agent/HEARTBEATS/opencode (muse-spark-1.3-free) 01.json`, and `b5-0027-workflow.js` at the repo root. `validate-heartbeats.ps1` exits 1 (5 non-conforming files) | `git ls-files`; my tool runs |
| S-9 measurement fixes | **Not done.** `rule-coverage.ps1` still auto-discovers the newest repo-root `*_gate.log` — both are from 2026-10-17, so the default run measures against a stale log; I had to pass my per-class conformance log explicitly. Pin-probe working-directory independence unverified this pass | my coverage run |
| S-10 run-all-and-collect gate | **Done, and better than specified.** Literal here-doc class list (no self-grepping), `|| CODE=$?` under `set -e`, per-class logs, stable `CODE TIME RESULT CLASS` table, printed failing-name set, and a `ROWS != COUNT` invariant that fires before any verdict | my run; triage 4.1, 4.5 |

Net: **Phase 0 is ~85% complete.** The missing fifth is one hygiene row (S-7
governance text, S-8 residuals, S-9 measurement defaults, the U+FFFD decision, and
the assessor pass for the corrective entries).

## 7. Ledger, claims and governance state today

* **Ledger:** 39 rows in-table, lint exits 0, no duplicate ids, 10 OPEN
  (B5-0026 through B5-0035, the B5-0031 gap preserved). B5-0036..B5-0041 closed
  today with reports. Within your 5-to-10 guidance, but ten OPEN rows are all
  awaiting re-scoping rather than fresh work.
* **Claims:** none live. The CLAIMS directory holds only `*.RELEASED.json`
  markers; the seven orphans were reaped at owner direction with the reaping
  recorded first. However, the markers themselves are now causing N2.
* **Encoding lint:** 20 hits — 8 `U+FFFD` at DECISIONS 508-518 (unrecoverable
  text; owner decision pending), 4 in `HANDOFF.md`, 3 in the encoding-trap
  pattern file (N4), 1 each in the 2026-10-18 state report, both proposal files,
  and `docs/RULEBOOK_PIN.md`. All appear to be pre-existing damage, not
  regressions.
* **Citations and dup-census:** both exit 0. `RB:` citations: 17 scanned, 0
  unresolved, against the 87-row index.
* **Tracked tree:** clean before my pass; `origin/main` equals local `main` at
  `09feb5a`; the three commits of this task branch
  (`8ed01c3`, `0f73c7e`, `1daee3a`) are deliberately local-only — the owner
  confirmed this, because `origin` is a public repository and pushing would
  publish agent-state files. This answers your O7.
* **Governance text:** `00_BOOT.md` step 7 is stale and self-contradictory;
  `HANDOFF.md` is stale; `docs/DECISIONS_B50017.md` is a superseded draft still
  sitting next to the authoritative file, exactly the hazard your proposal's
  section 14 flagged; the STUB/PARTIAL/FULL vocabulary is still undefined.

## 8. New findings this pass

| # | Finding | Severity | Evidence |
|---|---|---|---|
| N1 | `flip-ledger.ps1`'s live-claim refusal joins `'.agent/CLAIMS'` onto a parent that is already `.agent`, resolving to `.agent/.agent/CLAIMS`, which does not exist; `Get-ChildItem ... -ErrorAction SilentlyContinue` then returns nothing, so **the refusal never fires**. Same path-doubling class the tool was just fixed for, in a different line. The DONE-requires-report refusal works but resolves its report glob against the caller's working directory | High | `Test-Path .agent\.agent\CLAIMS` = False; source line read |
| N2 | `run-queue.ps1 -DryRun` refuses to offer B5-0029 because `B5-0029.RELEASED.json` parses as a live claim on id B5-0029, with a placeholder midnight timestamp. **One of the ten OPEN rows is unclaimable through the sanctioned path** until the retired markers are renamed or deleted | High | my `run-queue` run, repeated B5-0653 warnings |
| N3 | `rule-coverage.ps1`'s default gate-log discovery reads repo-root `*_gate.log`, both dated 2026-10-17; the fresh logs live in `b5ccg/out_gate/`. Your S-9 is unimplemented, so the default coverage number is measured against a pre-repair log | Medium | my coverage run (had to pass `-GateLog` explicitly) |
| N4 | `.agent/PATTERNS/encoding-quoting-guard/encoding-quoting-trap.md` contains the literal mojibake it documents and therefore trips `lint-encoding.ps1` itself. The lint needs a carve-out for the pattern corpus, or the examples must be written as codepoints like the lint itself now is | Low | my lint run |
| N5 | `validate-heartbeats.ps1` exits 1: 5 of 20 heartbeat files non-conforming (pre-schema `claude-code-b5ccg-02.json` with no `agent_id` or timestamp, two `state: done` outside the enum, two future-skew timestamps). They are archive candidates, not live identities | Low | my tool run |
| N6 | Two heartbeat JSONs and `b5-0027-workflow.js` are still force-tracked in git despite `.gitignore` covering the heartbeat paths | Low | `git ls-files` |
| N7 | The eight stub files are unchanged and inert; two of the "probes" have no `main()` and could never have run in the gate. Nothing in the gate detects an inert stub | Medium (unchanged from your F2) | DECISIONS 2026-10-19 re-verification [Q] |
| N8 | Gate cost concentration: `HeadlessHumanSeatProbe` is 79.8% of gate time (185 s of 233 s). Any soak-tier decision (your O5) should start by moving or splitting this probe | Info | my run |

## 9. Playable Slice: current state against target

Targets are yours, unchanged. "Current" is measured today against a valid gate.

| Measure | Target | Current | Source |
|---|---|---|---|
| Seeded AI-vs-AI games per starter-deck pairing, no exceptions or invariant violations | 200 | **No harness.** The invariant and fuzz probes are inert stubs (N7) | census, stub verification [Q] |
| Legitimate terminations | >= 95% | Unmeasured — same reason | |
| Unimplemented-text hits on slice decks | 0 | Unmeasured — the tripwire is an uncalled substring scan | |
| Deterministic human-seat game in the gate | 1 | **Passing**, 185 s, twice-corroborated across independent runs | my run; `GATE-B5-0041.txt` |
| Rule coverage of S1 entries | >= 52 of 57 (90%) | **13 of 57 (22.8%)**, now against a current log; map has 83 rows, 65 verified, 18 unverified; `RB:VI/marks` mapped but unverified, so counted uncovered | `rule-coverage.ps1 -GateLog <fresh>` |
| Card-behaviour coverage (card named by >= 1 effect literal) | not set | **58 of 403** behaviour-bearing records (14.4%); 446 census records total, 146 starter, 23 starter covered | `effect-census.json` |
| Gate | exit 0 | **Exit 0, 31/31**, verified independently | my run |

Two data facts you asked for in v1.2, now measured: the census **does** carry
`starter` and `covered` flags (146 starter records; the class breakdown below);
and the starter-deck file holds exactly **4 factions** — HUMAN, CENTAURI, MINBARI,
NARN, 187 entries — against the rulebook's five (line 126 names the fifth as the
League of Non-Aligned Worlds; line 134 says five starter decks). Your inference
that the fifth deck is an intended S2 gap, not lost data, is confirmed.

## 10. What remains to complete the project

In order, with our notes since your proposal was written:

1. **One hygiene row** (section 6's residual fifth): fix N1 and N2 in
   `tooling/`; implement S-9's log discovery; untrack the three files in N6;
   archive the five heartbeats in N5; refresh `00_BOOT.md` step 7 and
   `HANDOFF.md`; get an independent assessor pass on the 2026-10-19 corrective
   DECISIONS entries; then your governance text (vocabulary, §4 wording) as the
   owner approves it.
2. **Phase 1, exactly as you specified:** L-1 real tripwire, L-2 invariant
   checker with `RB:` citations and seeded-corruption tests, L-3 two-tier seeded
   fuzz. These are the instruments that make "playable" measurable. The stub
   files should be deleted as part of L-1/L-2, not before (they are useful as
   name reservations and as the record of what was falsely claimed).
3. **Phase 2:** E-0 full redo under the v1.2 binding method (section 12 asks you
   to confirm it), then E-1 schema, then the pilots. Do not migrate anything
   before E-1 — your warning stands, and the eight inert interfaces are a live
   demonstration of what happens without it.
4. **Phase 3:** D-1..D-3 extractions, one per freeze window. Note B5-0039 has
   already moved one behaviour (the auto-commit) out of
   `openConflictParticipation`; the simultaneous-victory case (your risk R9) is
   untouched.
5. **Phase 4:** P-1..P-3, with coverage now honestly measurable from the gate.

Definition of done, restated in our words: the Playable Slice targets in section 9
all met or explicitly waived by the owner, against a gate that stays green on two
independent runs, with no ledger row reading FULL whose promotion checklist has
not passed.

## 11. Status of your owner-decision list (O1..O10)

| # | Your question | Status today |
|---|---|---|
| O1 | Approve the section-7 dispositions | **Effectively approved** by the 2026-10-19 owner rulings (0025 DONE, the rest OPEN/re-scope). The formal re-scoping of seven rows into Phase 1/2 tasks is still owed by the planner |
| O2 | Adopt STUB/PARTIAL/FULL vocabulary and promotion checklist | **Still open, still needed.** The words are used in DECISIONS but defined nowhere |
| O3 | Untrack claim files / extend `.gitignore` | **Partly executed**; three tracked files remain (N6) |
| O4 | Build isolation | **Done** (S-2) |
| O5 | Gate tiers | **Still open; now cheap to decide.** Timings are real: 233 s total, 185 s human-seat. We suggest a gate tier capped around 60 s and a soak tier outside it |
| O6 | D2 options (a)/(b) | **Answered** by you in v1.2 and confirmed by events; no decision owed |
| O7 | Record the push arrangement | **Recorded**: branch stays local; `origin` is public; two task-branch commits deliberately unpushed (DECISIONS, "Housekeeping") |
| O8 | Snapshot scheduler still wanted? | **Still open.** We side with you: keep it as a second safety net; it is built and disabled, and `origin` alone would leave local task-branch work unprotected |
| O9 | Playable Slice covers S1 only | **Still open.** Recommend confirm |
| O10 | Which models are available | **Answerable now** (section 12, R6) |

## 12. Requests to Claude — what we need from you

These are the recommendations we are asking for, numbered R1..R10. Answering any
subset unblocks that subset of work; R1, R2 and R4 are the critical path.

* **R1 — Phase 1 design review before we build it.** Your L-1 specifies a runtime
  hit when a behaviour-bearing card is played or resolved with no registered
  effect. The engine's single source of truth for registration is
  `CardEffects.hasRegisteredEffect(id)` (already used by the aftermath registry,
  B5-0013). Please confirm that hooking the tripwire at that call site — plus the
  dispatch points in `GameController` — matches your intent, and that
  "report-only in the gate first" is still the right rollout. Also confirm L-2's
  invariant family list against the rulebook sections you would cite.
* **R2 — Confirm the E-0 method against measured census counts.** Your v1.2
  binding method expects 39 starter hard records and 17 starter STAT_MODIFIER
  records with 117 non-starter hard records to sample from. **Measured today:
  exactly 39 (TRIGGERED 20, CONFLICT_VARIANT 15, RESTRICTION 4), exactly 17, and
  exactly 117.** The method's arithmetic holds. Please confirm it is final, and
  say whether you still want the `bio-weapon discovery` ESCAPE check as written
  (the loser-influence table exists; the card's Deluxe delta is in DECISIONS).
* **R3 — The eight stub files: delete or keep?** Our recommendation is to delete
  them at the moment their replacement lands (L-1/L-2/E-1/D-1..D-3), citing this
  report, rather than now — the file names record what was falsely claimed, and
  deletion without a replacement recreates the "schema exists" illusion in
  reverse. Tell us if you want them deleted immediately instead, and whether
  re-scoped rows should reuse the B5-0028..B5-0035 ids (as re-scopes) or be fresh
  ids with the old rows VOIDed.
* **R4 — Promotion checklist and status vocabulary, final wording.** Please
  supply the checklist text and the STUB/PARTIAL/FULL definitions as you want
  them to appear in AGENTS.md, so the owner can approve one block once. Add one
  line the checklist should carry, learned today: *the gate log must be a
  sole-session run and its row-count invariant must have passed* — the invariant
  is what makes a truncated table unable to read as a pass.
* **R5 — Definition of done.** Please state the completion criterion for the
  project as you would accept it (we proposed one at the end of section 10), and
  confirm or revise it. The owner needs one sentence to plan against.
* **R6 — Model routing (your O10, answered).** The fleet actually available is:
  OpenCode free-tier sessions — `muse-spark-1.3-free`, `glm-5.3-flash-free`,
  `hy4-preview` / `hy4-preview-free`, `space-bunny-free`, plus one-off sessions
  (`nemotron-3.5-super-vl`, `solar-mini4`, `gpt-4o`); Claude Code sessions
  (`claude-code-b5ccg-01/02/03`); Claude Desktop (never run); and this
  (`big-pickle`). The 2026-10-19 record supports your routing hypothesis: the
  strongest, most careful work of the whole project came from the sessions that
  measured before asserting, regardless of tier. Please give your routing table
  anyway (who does E-0, who does the assessor passes, who does tooling), and say
  whether an assessor pass from a *different model family* than the author should
  be mandatory for schema rows.
* **R7 — Encoding damage dispositions.** The 8 `U+FFFD` characters at DECISIONS
  508-518 are unrecoverable by inference. Repair-in-place vs record-as-damaged:
  we recommend **record-as-damaged** (one short entry naming the lines and the
  lost content as "the eight FULL headings of 2026-10-18, already corrected
  elsewhere"), because reconstructing prose from context is how the mojibake
  regression happened. Also: `docs/DECISIONS_B50017.md` should be moved to
  `.agent/RESCUE/` or marked superseded in its front matter — recommend which.
* **R8 — Gate tiering (your O5), with real numbers.** The gate is 233 s;
  human-seat is 185 s of it; everything else together is under 50 s. Proposed:
  gate tier = current 31 classes minus human-seat (plus a time-budgeted
  human-seat variant after P-3), soak tier = human-seat full run + L-3 fuzz,
  run on demand and before any FULL promotion. Please critique.
* **R9 — Coverage strategy.** 13 of 57 S1 entries covered. Please rank the 44
  uncovered entries into (a) cheap conformance checks, (b) probe-sized, and
  (c) needs Phase 1/2 instruments first — or tell us you would rather do E-0
  first and let coverage follow the effect work.
* **R10 — Anything we got wrong.** You have now been contradicted by this repo
  once (your F1 is closed) and vindicated several times. Please read section 4's
  timeline and section 8's findings and say where our reasoning is weak —
  particularly N1/N2 (are those the right fixes?), the stub-deletion stance in
  R3, and the hygiene-row sequencing in section 10.

## 13. Risks and cautions

* **The green gate is one session-model deep in its honesty fixes.** The row-count
  invariant and the literal class list are what make 31/31 believable; do not let
  a future "cleanup" reintroduce a self-grepping class list or an early abort.
* **`GameController` congestion.** B5-0037/0039 just touched it; D-1..D-3 and
  possibly L-1 will touch it again. One claim at a time, as your proposal says.
* **The retired RELEASED markers can still bite** (N2): until they are cleaned
  up, any tool that keys on `B5-[0-9]{4}` will misread them.
* **Do not read the DECISIONS FULL entries of 2026-10-18 as history-free.** The
  corrections are appended, per your own "never rewrite DECISIONS history"
  rule — but a future agent skimming only the original entries can still be
  misled. The corrective entries are what section 4 of the ledger now points to;
  keep it that way.
* **Origin is public.** Local-only is the current owner decision; anything that
  changes it needs a fresh DECISIONS entry.

## 14. Evidence index

* **Gate run (this report's verdict of record):**
  `.agent/REPORTS/evidence/GATE-big-pickle-2026-10-19.txt` (31/31, 232513 ms);
  per-class logs at `b5ccg/out_gate/`; conformance 904/904 in
  `out_gate/HeadlessConformanceTest.log`.
* **Corroborating runs:** `.agent/REPORTS/evidence/GATE-B5-0039.txt`,
  `GATE-B5-0041.txt`; the triage suite `GATE-TRIAGE*.txt` and `GATE-FINAL.txt`
  with the validity table in `.agent/REPORTS/evidence/README.md`.
* **Tool runs this pass:** `lint-ledger.ps1` (PASS, 39 rows), `lint-encoding.ps1`
  (FAIL, 20 hits), `check-citations.ps1` (PASS), `run-dup-census.ps1` (PASS),
  `run-queue.ps1 -DryRun` (10 OPEN, 9 offered, B5-0029 suppressed),
  `validate-heartbeats.ps1` (exit 1, 5 non-conforming),
  `rule-coverage.ps1 -GateLog <fresh>` (13/57).
* **Data queries:** `b5ccg/resources/census/effect-census.json` (446 records;
  58 covered; 146 starter; starter hard 39 = 20+15+4; starter STAT_MODIFIER 17;
  non-starter hard 117; behaviour-bearing 403);
  `b5ccg/resources/decks/premiere-starter-decks.json` (4 factions, 187 entries);
  rulebook lines 126 and 134 (five races, five starter decks).
* **Source reads:** `b5ccg/compile.sh`, `.agent/tools/flip-ledger.ps1`,
  `.agent/00_BOOT.md`, `.agent/HANDOFF.md`, `AGENTS.md`, `docs/DECISIONS.md`
  (2026-10-19 entries), `.agent/TASK_LEDGER.md`, both proposal files.
* **Git:** branch `task/opencode-space-bunny-free-01-post-b5-0034`, 3 commits
  ahead of `origin/main` (`09feb5a`); tracked residuals listed in N6.

## 15. Provenance and status statement

Authored by `opencode (Big Pickle)` (agent id `opencode-big-pickle-01`, model id
`big-pickle`, provider opencode). No `assessor_llm` entry: the author is not an
independent assessor of this report (AGENTS.md section 1). Status: **observation
and request — no authority** (AGENTS.md section 3). Nothing in this report is
promoted; the gate ran, the tools ran, and the numbers are where they are. Any
row that moves from here moves through the gate, a DECISIONS entry, and a
released claim — and, for anything you advise in section 12, through the owner's
approval first.
