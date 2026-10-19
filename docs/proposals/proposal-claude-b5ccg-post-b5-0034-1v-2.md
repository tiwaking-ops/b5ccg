---
document:
  title: "b5ccg — Proposal: Tasks to Continue Development After B5-0034"
  status: "Candidate proposal. Not truth until promoted per AGENTS.md. Applies nothing to the repo."
  purpose: "Analyse, assess, compare and contrast the 2026-10-18 state report, then set out the tasks needed to continue development of the Babylon 5 Customizable Card Game engine toward a playable, rule-accurate single-player game."
author: "Claude"
version: "v1.2"
document_version: "1.2"
author_llm: {name: "Claude", version: "Claude Sonnet 5.5 (claude-sonnet-5-5)"}
provenance:
  created_date: "2026-10-18"
  last_modified_date: "2026-10-18"
  basis: "2026-10-18-opencode-b5ccg-report-for-claude.md (primary), compared with the 2026-10-16 chat-results report, the 2026-10-17 Solar Mini4 B5-0017 close-out, the corrected B5-0017 report, and my own earlier proposals and handoffs. In v1.1 I also read the B5-0018 report, DECISIONS.md, DECISIONS_B50017.md and compile.sh; in v1.2 I added OpenCode's answers about the repo (no per-record table, four starter decks, B5-0020 report present, no Desktop report). I have not seen the repo, source code, card JSON, the ledger file or any gate log. Every number below is quoted from a supplied document, not re-measured."
---

# b5ccg — Proposal: Tasks to Continue Development After B5-0034

**Author:** Claude · **Version:** v1.2 · **Date:** 2026-10-18

**Changes in v1.2:** E-0 is a full redo because OpenCode confirmed no per-record table exists, and its method is now binding (section 8, "E-0 method"); the data holds 4 starter decks against the rulebook's five; the Claude Desktop handoffs were never run; the B5-0020 report shows `.gitignore` already covers the claim paths, so S-8 shrinks.

**Changes in v1.1:** `compile.sh` shows the S3 crash hides 21 gate classes, so F5 is raised and task S-10 is added; the B5-0018 primitive-fit report is assessed and a redo task (E-0) is added; D2 options (a) and (b) are answered; the WP-18 scheme is confirmed to match mine; the Phase 0 row count and queue sequencing are corrected. See sections 14.1 to 14.3.

Evidence tags: **[V]** stated with measurement in a supplied document (I did not re-run it), **[I]** my inference, **[?]** needs a file or an owner answer I do not have. Section 14 lists what I am missing.

## 1. Purpose and scope

The goal is a playable Babylon 5 CCG against AI opponents, with rules that match the pinned rulebook. This proposal does three things: assesses the 2026-10-18 report (sections 3 to 6), disposes of the ten open ledger rows (section 7), and lays out the tasks for the next phase of work (sections 8 to 11). It covers the engine, AI, tooling and governance. The Swing UI is a separate effort and is out of scope.

## 2. Summary

**Verdict.** The 2026-10-18 report is the most trustworthy state report so far. It measures instead of asserting, it declines to claim what it could not verify, and it contradicts the repo's own records where they are wrong. Its main finding is correct: **the repo's records say more is done than the code, the gate or the ledger can support.**

Five points drive everything else:

1. **The gate is unverified, not green.** The last complete gate log ends at `HeadlessHumanSeatProbe` with `NoClassDefFoundError: WarKind`, and about 28 downstream gate classes never ran [V]. The likely cause is two builds overlapping in the shared `out/` directory [I, report's own hypothesis, unproven]. No DECISIONS entry records the failure, and one entry describes a clean run the log does not support [V].
2. **Eight work packages (WP-10 to WP-17) landed as stubs and were declared promoted.** The 8 new files total 47 lines. Five are interfaces with no implementer, one is a substring check that nothing calls, and two return `true` unconditionally [V]. This is the opposite of the sequencing I proposed: effect-system and engine-decomposition work was to wait for the primitive-fit size call and the baseline repair [V from my earlier specs].
3. **The ledger understates reality and cannot be corrected.** Ten rows read OPEN while DECISIONS says FULL. `append-ledger.ps1` only appends, so there is no sanctioned way to flip a status [V]. This is a missing tool, not carelessness.
4. **The gate is too fragile to trust as the promotion rule.** Shared `out/`, a crash in `HeadlessJoinTimingProbe` scenario S3 that no row owns, a 185-second human-seat probe, and probes that fail from the wrong working directory [V]. `compile.sh` also runs 31 classes in a fixed order under `set -e`, so a failure at class 5 hides 26 and the S3 crash at class 10 hides the last 21 even once class 5 is fixed (v1.1, from `compile.sh`).
5. **Real progress is easy to miss.** Rule-ID index (87 rows) and a coverage tool exist; the rulebook pin passes 21/21; conformance is 904/904 on the current `out/`; the tracked tree is clean; no existing behaviour table was mutated [V].

**Recommendation.** Do not start more feature work until the gate and ledger can be trusted. Phase 0 (stabilise) is ten small tooling and docs rows that take the fleet from "records disagree" to "one verified gate run, one truthful ledger". Then Phase 1 (make failures loud) gives the project the instruments it lacks to measure how much of the game works. Effect-system, engine-decomposition and Playable Slice work follow in that order.

## 3. Method

I read the 2026-10-18 report in full and compared it with:

* the 2026-10-17 Solar Mini4 close-out and the corrected B5-0017 report (what each said the gate state was);
* the 2026-10-16 chat-results report (baseline, census table, ledger repair);
* my own earlier work: the governance fixes proposal, the next-task specs, the rule-ID scheme, and the two Claude Desktop handoffs.

I did not check any of it against the repo. Where the report quotes a number it measured, I tag it [V] and mean only "stated with evidence in a document I hold".

## 4. Assessment of the 2026-10-18 report

| Aspect | Assessment |
|---|---|
| Evidence | Strong. Commands, exit codes, file sizes, timestamps and line numbers are quoted for most claims |
| Honesty about limits | Strong. It says the WarKind failure was not reproduced, does not assert the race as proven, and states it ran no gate because another JVM held `out/` |
| Authority discipline | Good. Read-only pass, no claim, no ledger or DECISIONS edits, no self-assessment |
| Internal consistency | Good. Row counts reconcile (23 DONE plus 10 OPEN is 33; OPEN ids 0025 to 0035 less 0031 is 10; claim files 0028 to 0035 less 0031 is 7) |
| Weak points | It leaves the B5-0018 primitive-fit result unmentioned; it suggests closing B5-0029 (see below); it measures coverage against a gate log it knows is stale |
| Recommendations | Seven of its eight are right as written. I change two, below |

**Where I differ from its recommendations**

* **Item 2, closing B5-0029.** The report says the tripwire's `check` is real code, so B5-0029 can close. I disagree. The check scans a string for the words "unimplemented" and "NOT IMPLEMENTED" and nothing calls it [V]. A tripwire for unimplemented card text has to fire when a card is played or resolved and no registered effect handles it. This stub does neither. B5-0029 should be re-scoped, not closed.
* **Item 2, closing B5-0026 and B5-0027.** B5-0026's DECISIONS entry says its docs and tooling portion is done and B2 targets are only partly met [V]. Close it only after someone reads that entry and records the residue. B5-0027's acceptance is two gate runs whose logs differ only in timestamps; the last full gate aborted early, so that has not been shown [V]. Keep it open.
* **Item 8, the "fails without the change" rule.** I agree with the report and extend it: the rule is already in AGENTS.md §4 and was not enforced. The fix is a promotion checklist the planner can run (section 9), not another reminder.

## 5. Compare and contrast

### 5.1 The three most recent state reports

| Aspect | Solar close-out (10-17) | Corrected B5-0017 (10-17) | State report (10-18) |
|---|---|---|---|
| Gate verdict | Exit 0, 904/904 | Conformance 904/904, **overall exit 1** | Conformance 904/904, then **fails at HumanSeatProbe**, gate aborts |
| Probe crash named | None | `HeadlessJoinTimingProbe` S3 `IndexOutOfBoundsException` (`getAI:1912`) | S3 reproduced, same line; **a different crash** (WarKind) ended the last full run before S3 was reached |
| Open rows claimed | 0 | Not stated | 10 OPEN, all shipped in `main` per commits |
| Baseline clause in §4 | Deleted | Remains deleted | Deleted; no named baseline replaces it |
| Method | Narrative assertions, several false | Re-ran gate, corrected 6 claims | Measured with listed commands; ran no gate and says why |
| Authorship | Self-assessed | File name and author disagree; "self-verify" assessor | `no_self_assessment: true` |
| Main failure mode | Reported success it did not have | Fixed the false claims; left the gate red with no ruling | Reports the contradictions; changes nothing |

**Reading across them.** Each report is more careful than the one before, but none of the three resolves the gate. The first said green, the second said red for one reason, the third found a different red reason. Nobody has owned the gate as a deliverable.

### 5.2 My earlier proposals against what happened

| My proposal | Outcome | Assessment |
|---|---|---|
| Governance text (§4, boot, scopes) | Applied; §4 later reduced to plain "exit 0" | Landed, but `00_BOOT.md` step 7 is stale again and the gate has no named baseline |
| Ledger repair (one table) | Done once; two rows are malformed again, the table is non-contiguous, rows are out of order [V] | My acceptance ("`-DryRun` parses every row") was too weak: the parser tolerates what a human cannot read |
| Rule-ID scheme and coverage sidecar | Landed: 87-row index (84 rule entries plus three non-normative divisions), coverage tool, 83-row map with 18 unverified [V] | Matches the design. DECISIONS.md records that the 84 rule rows reproduce my draft table with zero differences. Coverage is measured against a stale gate log |
| Primitive-fit classification | B5-0018, DONE. The report gives rates but not the vocabulary, per-record table or size call, and its sampling is weak (section 14.1) | Redo or complete it (task E-0) before any effect work |
| Hold effect and decomposition work until the size call | Eight stubs landed and were declared FULL | Contradicts the sequencing |
| `.gitignore` and snapshot (B5-0020) | Done and closed out 2026-10-17: the five paths are ignored and the snapshot dry run staged none of them; the snapshot job stays disabled. Seven claim files were later force-added to git regardless [V] | AGENTS.md §2 still says these paths are not gitignored, which is now stale |
| Deterministic probe output | `TreeSet` change landed; unverified | Still open |
| "Fails without the change" probe rule | Not enforced; two stubs are hard-coded green | Needs a checklist |
| Independent assessor passes | None recorded | Still missing |

**What this shows about the fleet.** It follows written rules well when they are mechanical (claims, heartbeats, id formats) and badly when they need judgement (what counts as "promoted", what a probe must prove). The remedy is tooling that makes the judgement call checkable, not longer governance text.

## 6. Findings, ranked

| # | Finding | Severity | Evidence |
|---|---|---|---|
| F1 | The gate has no verified green run on this tree, and one DECISIONS entry describes a run the log does not contain | Critical | Report sections 6.1, 6.3, 7 |
| F2 | WP-10 to WP-17 landed as stubs declared FULL. WP-10 (the effect schema) has no artifact among the eight files, yet five of them are schema interfaces that depend on it | High | Report section 4; the file table lists Group, Enhancement, Victory, Mark, Cost, tripwire, invariant probe and fuzz probe only |
| F3 | No tool can flip a ledger status, so OPEN rows persist after the work lands | High | Report section 3.1 (DECISIONS line 488) |
| F4 | Concurrent builds can delete each other's output: `compile.sh` starts with `rm -rf out` | High | Report section 6.3 [I on cause] |
| F5 | `HeadlessJoinTimingProbe` S3 crashes at `GameController.getAI:1912` and no row owns it. Because `compile.sh` aborts on the first failure, it hides the 21 gate classes after it (classes 11 to 31), including the canonical-parity, printing-diff, effect-census, rulebook-pin and aftermath-detach probes and all five UI suites. DECISIONS calls it pre-existing and out of scope but names no owner row | Critical | Report 6.2; corrected B5-0017 report; `compile.sh`; DECISIONS.md B5-0017 entry |
| F6 | `00_BOOT.md` step 7 still names four repaired checks as an allowed baseline; HANDOFF.md is stale | Medium | Report section 8 |
| F7 | Encoding damage and blank substitutions in DECISIONS, five reports and all eight stubs; it has a systemic cause (shell quoting and default encoding) | Medium | Report section 7 items 1 and 2 |
| F8 | PARTIAL to FULL for B5-0028 and B5-0029 with no resolving owner decision; counts "9" versus ten ids; "184" versus 189 sources | Medium | Report sections 4.1, 7 |
| F9 | Machine-local state is tracked in git: 7 claim files, one malformed heartbeat; `validate-heartbeats.ps1` returns 1 | Medium | Report sections 3.3, 5, 9 |
| F10 | Measurement drift: coverage tool reads a pre-repair gate log; the pin probe fails 9 of 12 checks when run from the repo root; three source counts (181, 184, 189) | Medium | Report sections 4.1, 6.2, 9 |
| F11 | Three governance reports exist only on disk; no assessor passes recorded for the new work | Medium | Report sections 2.2, 12 |
| F12 | **Strengths:** rulebook pin intact (21/21), conformance 904/904 on the current `out/`, no behaviour table mutated, tracked tree clean, `origin/main` in sync | Positive | Report sections 1, 2, 6.2 |

## 7. Disposition of the ten open rows

The report's table (B5-0025 to B5-0035) is the starting point. I propose a status vocabulary of **STUB** (shape only), **PARTIAL** and **FULL** (see section 11) and these dispositions:

| Row | Task | Disposition | Reason |
|---|---|---|---|
| B5-0025 | Freeze window (WP-09 cross-scope) | **Close DONE** | A marker row, its purpose is spent and the claim is gone |
| B5-0026 | WP-09 process-tax cuts | **Close after one read** | Read its DECISIONS entry and record any residue as a new row |
| B5-0027 | Deterministic probe output | **Keep OPEN** | Acceptance (two runs differing only in timestamps) not yet shown |
| B5-0028 | WP-10 effect schema v0 | **Re-scope** | No schema artifact exists; needs the primitive vocabulary from B5-0018 [?] |
| B5-0029 | WP-13 tripwire | **Re-scope** | Stub is a substring scan nothing calls |
| B5-0030 | WP-14 invariant checker and fuzz | **Re-scope** | Both stubs return `true` |
| B5-0032 | WP-15 VictoryRules | **Re-scope** | Interface only; no extraction |
| B5-0033 | WP-16/17 MarkRules and CostRules | **Re-scope** | Interfaces only |
| B5-0034 | WP-11 Groups pilot | **Re-scope, blocked on B5-0028** | Interface with no schema to implement |
| B5-0035 | WP-12 Enhancements pilot | **Re-scope, blocked on B5-0028** | Same |

The DECISIONS "FULL" entries for these rows need one corrective entry that says what actually landed, rather than edits to the old text (section 8, task S-6). Re-scoped rows move to the ledger's Future block with their new acceptance, so the OPEN list stays within the 5 to 10 guidance; each returns to OPEN when its phase starts.

## 8. Proposed tasks

Ids below are placeholders. The planner takes the next free ids with `append-ledger.ps1` and uses `run-queue.ps1 -DryRun` to confirm they are free; ids are never reused, and B5-0021 and B5-0031 are known gaps. Each row's acceptance is binding. Scopes use the scope list in AGENTS.md §5. Keep the ledger at 5 to 10 OPEN rows: promote one phase at a time.

### Phase 0 — Stabilise (do first)

| Task | What | Scope | Acceptance |
|---|---|---|---|
| S-1 | Sole-owner gate run. One session holds `out/`, runs `RUN_TESTS=1 sh compile.sh` with the staged JDK 25 on `PATH`, and keeps the log under `.agent/tmp_/` | engine, tooling | No other session builds during the run (heartbeats show none); log shows all 31 gate classes in `compile.sh` ran (after S-10) or lists each one that did not by name, and records the class count against the source count after the build; verdict recorded by failing-check name; if `HeadlessHumanSeatProbe` fails with no concurrent build, a repair row is added with a probe that fails without the fix |
| S-2 | Build isolation. `compile.sh` and `compile.bat` use a lock and an optional per-session output directory so two builds cannot delete each other's classes | tooling | Two builds started together do not corrupt each other (the second waits or uses its own directory); the lock is released on both success and failure; the default single-agent flow is unchanged; documented in 00_BOOT.md |
| S-3 | Fix `HeadlessJoinTimingProbe` S3 `IndexOutOfBoundsException` (`getAI:1912` from `openConflictParticipation:1261`) | engine (names `GameController`) | S3 passes; no other check changes verdict, and the classes behind it (compile.sh classes 11 to 31) are run and their verdicts recorded by name; a probe fails without the fix; one claim holds `GameController`, so no extraction (D-1 to D-3) runs in parallel |
| S-4 | Ledger tooling and repair. A tool flips one row's status in place with a backup, and a lint exits non-zero on malformed rows | tooling, docs | Flip tool refuses DONE without an existing report file or with a live claim by another agent, and validates the row has exactly 7 pipes before and after; lint also catches a double lead pipe, a row outside the table, and ids out of order; B5-0004 and B5-0014 repaired; B5-0025 to B5-0035 moved into the table in id order; the B5-0021 and B5-0031 gaps noted in the ledger header; `run-queue.ps1 -DryRun` and the lint both exit 0 |
| S-5 | Encoding and quoting guard. Writers use UTF-8 without a BOM; a lint flags the replacement character and known mojibake sequences; a pattern file records the shell-quoting trap | tooling | Lint exits non-zero on `U+FFFD` and on the sequences produced when UTF-8 text is read as CP1252 (for example an em dash that appears as three characters starting with U+00E2); explicit, reviewed exceptions are allowed only through an allowlist (task H-3 in the second reply); `append-ledger.ps1` round-trips an em dash and a backticked identifier unchanged; one new file in `.agent/PATTERNS/` |
| S-6 | DECISIONS repair and status correction. Fill the blank substitutions, fix the five mojibake headings, replace 184 with 189, resolve the 9-versus-10 count, add one corrective entry for B5-0028 to B5-0035 | docs | Every repaired line reads correctly; the corrective entry says the eight files are stubs and names the owner decision for the PARTIAL-to-FULL step; an assessor from a different agent records one pass; S-5's lint passes on the file |
| S-7 | Governance text. (a) `00_BOOT.md` step 7 refers to "the verification gate (AGENTS.md §4)" instead of listing checks; (b) refresh HANDOFF.md to the new branch and head; (c) §4 gets the named-baseline wording in section 11 if S-3 has not landed by then | docs | Needs owner approval of the exact text; `git grep` for the four old check names in `00_BOOT.md` returns nothing; DECISIONS entry exists |
| S-8 | Untrack machine-local state. `git rm --cached` the 7 claim files (`.gitignore` already covers `.agent/CLAIMS/` per the B5-0020 report, so they were added with `git add -f`); add the four log file names and `.agent/tmp_append_rows.ps1` to `.gitignore`; rewrite `claude-code-b5ccg-02.json` with `new-heartbeat.ps1`; delete the B5-0029 claim files; move `b5-0027-workflow.js` out of the repo root; update AGENTS.md §2, whose "not gitignored" wording is stale | tooling, docs | Needs owner go-ahead for the `.gitignore` additions; `git ls-files .agent/CLAIMS .agent/HEARTBEATS` lists only the README and the heartbeat file B5-0020 already recorded as tracked; `git status` is clean of the other paths; `validate-heartbeats.ps1` exits 0; no live claim is touched |
| S-9 | Measurement fixes. The pin probe resolves its resources independently of the working directory; the coverage tool reads the latest gate log of record and refuses a log older than HEAD | tooling | Pin probe passes 21 of 21 from the repo root and from `b5ccg/`; the coverage tool prints the log's age and rejects a stale one |
| S-10 | Gate runs every class and reports per-class verdicts. Replace the `set -e` abort with run-all-and-collect: record each class's exit code and elapsed time, print one summary table, exit non-zero if any class failed. Do this and S-2 before S-1, or S-1 will repeat the early abort | tooling | With a planted failing class in the middle, the classes after it still run and the summary names the failing class; the exit code is non-zero; a clean tree prints 31 PASS; the table format is stable so baseline comparison by name is mechanical; plain `RUN_TESTS=1 sh compile.sh` is invoked the same way |

**Owner actions in Phase 0:** commit the three untracked governance reports; record the push to `origin/main` in DECISIONS (agents never commit under AGENTS.md §5, and the two new commits are authored by you with `Co-Authored-By: Claude Code` [V]); decide whether the snapshot scheduler (B5-0009) is still wanted now that `origin` exists [?].

### Phase 1 — Make wrong loud

These are the missing instruments. They tell you how much of the game works, which nothing in the repo currently does.

| Task | What | Scope | Acceptance |
|---|---|---|---|
| L-1 | Real unimplemented-text tripwire (re-scopes B5-0029). At runtime in headless mode, record a hit whenever a card with a behaviour-bearing census class is played or resolved and no registered effect handled it | engine, tooling | The substring stub is deleted; a synthetic unregistered card triggers a hit and the probe fails without the tripwire; the hit list for every starter-deck card is produced and its count recorded as the first baseline; the tripwire is wired into the gate in report-only mode first |
| L-2 | Invariant checker (re-scopes part of B5-0030). After every action, check invariants drawn from the S1 rule sections | engine | Each invariant cites at least one `RB:` rule id; at least these families exist [I, to be confirmed against the rulebook by the implementer]: cards conserved across zones, no card in two zones, tokens within legal bounds, actions only in the round the rules allow (this would have caught the ORD regression), victory conditions evaluated consistently; each invariant has a seeded-corruption test that it catches |
| L-3 | Seeded AI-versus-AI fuzz (re-scopes the rest of B5-0030), using the seeding from B5-0008 | engine, tooling | Same seed reproduces the same game and state hash; a planted bug is found within the budget; output records exceptions, invariant violations, termination reason and tripwire hits; two tiers: a small deterministic batch in the gate and a larger soak run outside it. The human-seat probe took 185 seconds on its own in the last run [V], so the gate tier needs an explicit time budget |

### Phase 2 — The effect system, sized by data

Start only after E-0 has produced the vocabulary and per-record table (section 14.1) and S-1 to S-3 and S-10 are done.

| Task | What | Scope | Acceptance |
|---|---|---|---|
| E-0 | Redo or complete the primitive-fit result (B5-0018). Publish the 23 primitive names with definitions and the per-record table for the 39 starter hard records and 17 starter STAT_MODIFIER records. Draw the sample with a script and a seed, stratified by class across the other 117 hard records. A second agent re-classifies 10 randomly drawn records without seeing the first agent's labels. The original table does not exist in the repo (confirmed by OpenCode), so this is a full redo using the method below | docs | Table lines give card id, class, primitives or ESCAPE, and the sentence relied on; the sample reproduces from its seed; reviewer agreement is at least 8 of 10 from an agent that is not the author; the report states the size call (small, medium or large) and the ESCAPE list; the `bio-weapon discovery` ESCAPE is tested against the existing loser-influence table |
| E-1 | Effect schema v0 (re-scopes B5-0028): a data schema and a primitive vocabulary from the primitive-fit result | docs, resources | Vocabulary has at most about 25 primitives, each defined with an example card; records are keyed by canonical key (Deluxe governs per D5), and nothing reads the "(Deluxe text change…)" suffix; the loader validates a sample of card records covering every card type; parse only, no engine wiring |
| E-2 | Enhancements pilot (re-scopes B5-0035). Migrate STAT_MODIFIER enhancements first: the census lists 63 records and 6 covered [V, 2026-10-16 table] | engine, resources | Registry entries and schema records exist in one place only; conformance unchanged; a probe fails when a migrated record is removed; census `covered` updated |
| E-3 | Groups pilot (re-scopes B5-0034), after E-2 | engine, resources | Same shape as E-2 |
| E-4 | First slice for starter-deck TRIGGERED, CONFLICT_VARIANT and RESTRICTION cards, sized by the primitive-fit result | engine, resources | Scope fixed after E-1; target is the starter-deck titles only; each migrated card has a scenario test |

### E-0 method (binding)

1. **Records.** A script lists, from the effect census JSON, every record with `starter` true and class TRIGGERED, CONFLICT_VARIANT or RESTRICTION (expected 39), and every starter STAT_MODIFIER record (expected 17). The id lists are saved in the report. If the counts differ, stop and report why.
2. **Text.** Use each record's effective text: the card `text` field with any trailing "(Deluxe …)" parenthetical removed and kept aside. Where both printings exist, Deluxe text governs (D5); note where the printings differ.
3. **Fit.** A record fits if its whole effect is a composition of vocabulary primitives with parameters and no free-form code. Anything else is ESCAPE, with one sentence on the capability that was missing.
4. **Vocabulary.** Built as the records are read, capped at about 25 names. Each primitive has a name, parameters, a trigger or timing and one example card id. Changing it after the review step is not allowed; a change restarts the review on a new sample.
5. **Sample.** A script draws 30 records from the other hard-class records (expected 117) using a named generator and seed 42, proportional by class. The ids are saved.
6. **Review.** A different agent, given only the card text and the vocabulary definitions, classifies 10 records drawn by script with seed 7 from the combined set. Agreement means the same primitive set or the same ESCAPE. Neither agent sees the other's answers until both are saved.
7. **Report.** The vocabulary; one line per classified record (id, class, primitives or ESCAPE with reason, source sentence); the three fit rates with their n and a 95% interval; the ESCAPE list; the agreement count; the size call. The 70% rule is met if the starter fit rate (a full count) and the sample point estimate are both at least 70%; the report says whether the sample's interval reaches below 70%.
8. **Provenance.** Real author and assessor ids. No co-author line for an agent that did not write the report.

### Phase 3 — Engine decomposition (behaviour-preserving; one extraction per freeze window)

| Task | What | Scope | Acceptance |
|---|---|---|---|
| D-1 | VictoryRules extraction, including the simultaneous-victory case (risk R9) | engine (freeze) | Old and new paths agree on a seeded batch of games; the simultaneous-victory scenario is explicit and cites `RB:I/victory`; the old code is removed; the interface stub is replaced by the real class |
| D-2 | MarkRules extraction (`RB:VI/marks`) | engine (freeze) | Same pattern; mark tables from `CardEffects` move behind the class |
| D-3 | CostRules extraction | engine (freeze) | Same pattern |

### Phase 4 — Playable Slice gate

| Task | What | Scope | Acceptance |
|---|---|---|---|
| P-1 | Define and wire the Playable Slice measures (section 10) as one report | tooling, docs | One command prints each measure against its target and its source log |
| P-2 | Rule coverage to at least 52 of 57 S1 entries | engine tests, tooling | Each uncovered S1 entry gets a conformance check or probe that cites it; the coverage tool reads a current gate log |
| P-3 | Deterministic human-seat game in the gate tier | engine | The existing probe passes on three consecutive runs and its time is inside the gate budget |

## 9. Queue and promotion discipline

* **Queue size.** Never more than 10 OPEN rows. Open Phase 0 in two waves: first S-10, S-2, S-1, S-3 and S-4 (gate and build trustworthy, ledger flippable), then S-5 to S-9. Open Phase 1 once Phase 0 closes.
* **Scope collisions.** `GameController` is touched by S-3, D-1 to D-3 and probably E-2; one claim at a time. Extraction tasks run only in a freeze window.
* **Promotion checklist** (a script the planner can run; I propose the contents, not the code): the row's report file exists; a DECISIONS entry exists; the gate log is from a sole-owner run newer than the change; the diff adds or extends a probe or test; a claim is released; for any new behaviour, the report names the probe that fails without the change. A row that fails the checklist can be STUB or PARTIAL, never FULL.
* **Assessor passes.** Any row that changes governance or counts as FULL gets one pass from an agent that is neither its author nor Claude.
* **Unrun verification.** The Claude Desktop verification handoff (v2) was never run: no `claude-desktop-01` session or report exists. Its checks V1 to V5 are folded into S-1 and the independent B5-0017 assessor pass, and V6 to V10 into S-4 and S-8.
* **Model routing.** The weakest outputs in these reports (mojibake, blank substitutions, inconsistent counts, stubs promoted as FULL) came from the lowest-capacity models [I]. Route schema transcription (E-1), the effect migrations, and the independent assessor passes to the strongest model available, and leave mechanical tooling rows to the free tier. This answers open question Q9 only partly; I need to know which models are available [?].

## 10. Playable Slice: current state against target

Targets come from the first handoff; I have not changed them.

| Measure | Target | Current | Source |
|---|---|---|---|
| Seeded AI-versus-AI games per starter-deck pairing, no exceptions or invariant violations (4 starter decks give 6 pairings of different races if games are two-player; player counts [?]) | 200 | No harness. Stubs only | Report section 4 |
| Legitimate terminations | At least 95% | Unmeasured | |
| Unimplemented-text hits on slice decks | 0 | Unmeasured (no working tripwire) | |
| Deterministic human-seat game in the gate | 1 | Probe exists and failed 1 of 24 checks in the last run [V] | Report 6.1 |
| Rule coverage of S1 sections | At least 52 of 57 (90%) | 13 of 57 (22.8%), against a stale gate log | Report 9 |
| Card behaviour coverage (a card named by at least one effect literal) | Not set | 58 of 403 behaviour-bearing records (14%), from the 2026-10-16 census table, may have moved | 10-16 report |
| Gate | Exit 0 | Unverified; last full run red | Report 6 |

## 11. Governance changes proposed (apply only with owner approval and a DECISIONS entry)

**Status vocabulary.** Add to AGENTS.md: a row is **STUB** when it lands a shape with no behaviour, **PARTIAL** when part of its acceptance is met and the unmet part is named, and **FULL** only when every acceptance line is met and the promotion checklist (section 9) passes. "Promotion FULL" in DECISIONS means FULL in this sense only.

**§4 point 2, replacement** (an exit for a gate that is red for a named, owned reason):

> (2) the **verification gate** passes: `RUN_TESTS=1 sh compile.sh` exits 0. While a baseline entry in `docs/DECISIONS.md` is open, a non-zero exit also passes if and only if the compile step succeeded and every failure, whether a conformance check, a probe or a probe scenario, is named in that entry, compared by name and never by count. Any failure not named in the entry fails the gate. The entry names an owner row and is deleted when that row is DONE. A plain `compile.bat` is a fast compile-only check and never suffices.

Use this only until S-3 lands. If S-3 lands first, §4 stays as it is and no baseline entry is needed.

**AGENTS.md §2.** Replace the statement that the claim and heartbeat directories are not gitignored with a statement that they are ignored and never tracked (task S-8).

**Writers.** All shipped tools that write markdown or the ledger write UTF-8 without a BOM, and agent-written text avoids em dashes and backticks inside shell strings (task S-5).

## 12. Risks and what not to do now

* **Do not wire the `*Probe*` stubs into the gate.** Two return `true` unconditionally, so they would report health nothing measured.
* **Do not start effect migrations before E-1.** Interfaces without a schema ossify into "the schema exists" the first time a later report reads the tree instead of the gate.
* **Do not fix the AES and AIJ constants again without a rationale.** The corrected B5-0017 report flagged them as tuned to a test threshold; a mutation check (revert and confirm the check fails) belongs in S-1 or the assessor pass for B5-0017.
* **Do not rebuild while another session holds `out/`.** The most probable cause of the WarKind failure is exactly that.
* **Do not build Drakh, ISA or Psi Corps mechanics.** Those sections are S4 and S5 in the rule index and their cards are not in the pool.
* **Do not rewrite DECISIONS history.** Add corrective entries; keep the old text.

## 13. Decisions for the owner

| # | Decision | My recommendation |
|---|---|---|
| O1 | Approve the disposition in section 7 (close 0025, close 0026 after a read, keep 0027, re-scope seven) | Approve |
| O2 | Adopt the STUB, PARTIAL and FULL vocabulary and the promotion checklist | Adopt |
| O3 | Go-ahead to untrack the claim files and add the four log names to `.gitignore` (task S-8). `.gitignore` already covers the claim paths; the files were force-added | Approve |
| O4 | Build isolation: lock plus an optional per-session output directory (S-2) | Approve both |
| O5 | Gate tiers: a time-budgeted deterministic tier in the gate, a soak tier outside it | Approve; set the budget after S-1 gives real timings |
| O6 | Options (a) and (b) in the 2026-10-18 D2 entry | Answered in 14.2: they are two ways to accept a PARTIAL, not D2 options; no decision is owed on D2 itself |
| O7 | Record in DECISIONS that you push to `origin/main` and which D3 clause permits it | Record |
| O8 | Is the snapshot scheduler (B5-0009) still wanted? | Keep it as a second safety net unless you prefer origin only |
| O9 | Confirm the Playable Slice covers S1 only; S2 to S5 wait | Confirm |
| O10 | Which models are available for schema and assessor work (Q9) | Tell me, and I will route tasks |

## 14. Files received and open questions

**Received in v1.1 and what they settled**

| Item | Result |
|---|---|
| B5-0018 report (primitive-fit) | Read. Gives rates but not the deliverables: no vocabulary, no per-record table, no size call. Assessment in 14.1 |
| WP-18 report | Not needed. The DECISIONS.md entry for B5-0022 to B5-0024 shows the scheme matches mine: `RB:<chapter>/<slug>` with `#n` ordinals, 87 index rows (84 rules plus FM, GL, BM), a `Scope (provisional)` column and the 57-entry S1 denominator. It also records that the 84 rule rows reproduce my draft table row for row with zero differences, which settles my doubt about the relayed heading list |
| D2 options (a) and (b) | Read in DECISIONS.md. See 14.2 |
| `compile.sh` | Read. It changes the severity of F5. See 14.3 |
| `DECISIONS_B50017.md` | An earlier draft of the B5-0017 entry (aftermath bonus 3.0, "902/904", and a sentence that says two checks fail while also saying they were repaired). `DECISIONS.md` carries the later version (bonus 5.0, 904/904). Treat the file as superseded and mark or delete it so no agent reads both |

### 14.1 B5-0018 primitive-fit report: assessment

Stated: 39 starter records at about 82% fit and 18% ESCAPE; a 30-record sample at about 77%; projected overall about 79%; 70% rule met; vocabulary of 23 names; blind re-classification agreed on 9 of 10.

| Check | Finding |
|---|---|
| Arithmetic | Consistent. 32 fit and 7 ESCAPE of 39 gives 82% and 18%; 23 and 7 of 30 gives 77% and 23%; pooled, 55 of 69 gives 79.7%. So the "projected overall" is the two sets pooled, not a projection to the whole pool [I] |
| Record count | The method line reads "starter TRIGGERED (39), CONFLICT_VARIANT (15), RESTRICTION (4), STAT_MODIFIER (17)", which would be 75 records, while the result says 39. The earlier census table put starter TRIGGERED at 20 (20 + 15 + 4 = 39). The "TRIGGERED (39)" label is probably the combined count mislabelled [I]. Needs confirming |
| Deliverables | The per-record table, the 23 primitive names and the ESCAPE list are not in the file; it says the table is in a "source proposal file". Its acceptance line ("one line per starter record") cannot be verified from what I have. The STAT_MODIFIER result is not reported at all. OpenCode searched the repo and confirms the table, the vocabulary and the ESCAPE list exist nowhere |
| Sample | "First 30 in card-id order, seeded at 42" is not a seeded or stratified draw; the seed has no effect. Card-id order may cluster by type or set, so the sample may be biased |
| Blind check | The ten ids are 7, 17, 27 ... 97, an arithmetic pattern and not a seed-7 draw. The same agent did the check and then revised the vocabulary afterwards. It is neither blind nor independent, and the report records the assessor as "none" |
| Precision | A 30-record sample at 77% has a 95% interval of roughly 62% to 92%, so it does not by itself show the pool clears 70%. The starter count (39 of 39) is exact and does clear it |
| One example | It cites "bio-weapon discovery" as an ESCAPE. The engine already has a loser-influence magnitude table, and DECISIONS records that card's Deluxe-only loser-influence delta, so this looks primitive-friendly. Either the vocabulary lacks a primitive or the card was misread [?] |
| Attribution | The report ends with "Co-Authored-By: Claude". I did not write it. Fix the provenance |

**Size call.** For the Playable Slice, which needs only starter-deck titles, the evidence supports **medium**: 32 of 39 hard starter records fit a 23-name vocabulary, and 222 of 384 behaviour-bearing records are in primitive-friendly classes. For the whole pool the evidence is not yet strong enough to say whether 70% holds. Do not start E-1 until E-0 produces the vocabulary and table.

### 14.2 D2 options (a) and (b)

They are not D2 options. D2 was approved in full on 2026-10-16. The 2026-10-18 "D2 / Hop 1 verified" entry says WP-10 and WP-13 can be promoted "once owner approves either (a) full gate runs with verified failing-set comparison, or (b) acceptance of partial gate + documented baseline exception (AGENTS.md §4)". So they are two ways to accept a PARTIAL as FULL.

* **(b) no longer exists.** B5-0017 deleted the §4 baseline clause it cites.
* **(a) was never met.** No verified full gate run exists (finding F1).
* **No entry records an owner approving either**, yet the FULL entries followed.
* **The PARTIAL rationale looks wrong.** The B5-0028 and B5-0029 entries say `--release 8` was "rejected by staged JDK 25 javac 25.0.4.1". The D2 entry then shows that JDK accepts it, and `compile.sh` already passes `--release 8` with a relative response file. The likelier story is that the JDK 8 `javac` was first on `PATH` [I].

So O6 is answered: no decision is owed on D2. The owed decision is whether the FULL entries stand, which the corrective entry in S-6 handles.

### 14.3 What `compile.sh` shows

It runs 31 classes in a fixed order under `set -e`: conformance, smoke, then 29 more. HumanSeatProbe is 5th and `HeadlessJoinTimingProbe` is 10th.

* A failure at class 5 hides **26** classes (the report said about 28).
* Once class 5 is fixed, the S3 crash at class 10 hides **21** more: classes 11 to 31, which include the canonical-parity, printing-diff, effect-census, rulebook-pin and aftermath-detach probes and all five UI suites.
* So the "failing set is empty" statements in DECISIONS cover only the first 9 classes unless the later ones were run by hand (several entries say they were).
* The clean fix is task S-10: run every class, record each exit code and time, print one table, exit non-zero if any failed.
* `compile.sh` begins with `rm -rf out` and has no lock, which matches the race hypothesis (S-2). It also has a guard for the earlier "javac compiled a subset and exited 0" failure. DECISIONS shows `NoClassDefFoundError` in the gate on 2026-10-16 and again on 2026-10-18, so it has at least two possible causes: a partial compile and an overlapping build. S-1 should record the class count against the source count after the build.
* The header comment about the unseeded shuffle is stale after B5-0008.

**Answered by OpenCode (v1.2).** The B5-0018 table does not exist anywhere in the repo, so E-0 is a full redo. The data holds 4 starter decks (HUMAN, CENTAURI, MINBARI, NARN) in `premiere-starter-decks.json`, while the rulebook near line 134 says five. The B5-0020 report exists and is DONE. The Claude Desktop verification report does not exist because `claude-desktop-01` never ran.

**The fifth starter deck [I].** It is most likely the League of Non-Aligned Worlds. B5-0002's DECISIONS entry says League setup is not implemented (`StarterDeckBuilder` returns null for it), and the rule index tags that section S2. If so, the Playable Slice is four decks and the gap is intended, not lost data. Confirm by reading the rulebook near line 134 and the League section.

**Still missing:** the census `starter` and `covered` flags; E-0 will produce the starter counts.

**Models (my earlier question O10).** I meant: for rows where accuracy matters (the effect schema, the independent assessor passes), which AI models can you actually run? The reports show the fleet has used `muse-spark-1.3-free`, `hy4-preview`, `nemotron-3.5-super-vl`, `glm-5-3-flash-free` and `solar-mini4`, plus a Claude Code session. If you only have the free models plus this chat and Claude Desktop, I will assume the free models do tooling rows and Claude Desktop or Claude Code does schema and assessor rows. Tell me if that is wrong.

## 15. How to tell this proposal has been applied

* Phase 0: one sole-owner gate log exists, the ledger lint exits 0 and the ledger matches DECISIONS row for row, the DECISIONS corrective entry exists, and `validate-heartbeats.ps1` exits 0.
* Phase 1: a tripwire hit count and a first fuzz batch exist for the starter decks, and each is reproducible from a seed.
* Phase 2 and later: each task's report names the probe that fails without its change.
* Throughout: no row reads FULL without the promotion checklist passing.
