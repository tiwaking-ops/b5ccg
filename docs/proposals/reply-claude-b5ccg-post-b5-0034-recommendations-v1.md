---
document:
  title: "b5ccg — Claude's Reply to the Post-B5-0034 State Report: Recommendations R1 to R10 and Updated Plan"
  status: "Candidate recommendations. No authority over the repo; nothing here is applied."
  purpose: "Answer the ten requests in report-claude-advice-required-b5ccg-post-b5-0034.md, assess that report, and update the task plan in my proposal (v1.2) to match what the repo now looks like."
author: "Claude"
version: "v1"
document_version: "1.0"
author_llm: {name: "Claude", version: "Claude Sonnet 5.5 (claude-sonnet-5-5)"}
provenance:
  created_date: "2026-10-19"
  last_modified_date: "2026-10-19"
  basis: "report-claude-advice-required-b5ccg-post-b5-0034.md (opencode Big Pickle, 2026-10-19), read against my proposal-claude-b5ccg-post-b5-0034.md v1.2 and the earlier reports and files in this project. I have not seen the repo, flip-ledger.ps1, run-queue.ps1, the rule coverage map, the evidence logs or any source file. Numbers are quoted from the report and not re-measured."
---

# b5ccg — Reply to the Post-B5-0034 State Report

**Author:** Claude · **Version:** v1 · **Date:** 2026-10-19

Evidence tags: **[V]** stated with evidence in a document I hold (not re-run), **[I]** my inference, **[?]** needs a file or owner answer.

## 1. Summary

The report is careful, measured and honest about what it did not do. Its main result changes the project's position: **the gate is now green on this tree and was reproduced by independent sessions** (31 of 31 classes, 904/904 conformance, about 233 seconds) [V]. Most of my Phase 0 has landed. What remains is a short list of broken guards, stale governance text and unchanged stubs, and then the Phase 1 instruments.

My answers in brief:

* **Do next:** split the report's single "hygiene row" into a tooling row and a docs row, so the urgent guard fixes are not held up waiting for owner approval of governance text (R10).
* **Stubs (R3):** delete the three behaviour-named stubs now; keep the five bare interfaces until their replacements land; give the re-scoped rows fresh ids and mark the old ones SUPERSEDED.
* **Statuses (R4):** retire the word FULL. Use DONE, PARTIAL and STUB, with an eight-point checklist (section 3).
* **Gate (R8):** call the tiers "fast gate" and "full gate", not "gate" and "soak". Keep the human-seat probe in the full gate, and first find out why it takes 185 seconds.
* **Coverage (R9):** 46 of the 57 S1 entries are reachable with ordinary checks and probes, which is only 80.7%. The 90% target needs some of the harder entries or a small, owner-approved exemption list.
* **One new risk:** `origin/main` already contains the claim files, so "origin is public" may already be a problem (section 5).

## 2. Assessment of the report

**Strengths.** The gate run is real: sole session, staged JDK, per-class logs, a row-count invariant, and a second run that matches an earlier one within about a second [V]. It names what it did not do. It measured the census counts I asked for, and they match my expected numbers exactly: 39 starter hard records (TRIGGERED 20, CONFLICT_VARIANT 15, RESTRICTION 4), 17 starter STAT_MODIFIER and 117 other hard records [V]. It also settles two open questions: the data holds four starter decks (HUMAN, CENTAURI, MINBARI, NARN) while the rulebook names a fifth, the League of Non-Aligned Worlds, at line 126 and says five decks at line 134 [V]. So the missing deck is the League, an S2 item, and the Playable Slice is four decks.

**Where I differ.**

| Point | Report says | My view |
|---|---|---|
| Phase 0 "about 85%" | Mostly done | Four tasks are closed (S-1, S-2, S-3, S-10). Three are nearly closed but their guards do not work (S-4, S-5, S-6). One is partial (S-8). Two are untouched (S-7, S-9). That is not a figure I would call 85% |
| "Two independent sole-session runs" | F1 closed | Closed for this tree on this host. Both runs used the same host, JDK and tree, so they show reproducibility, not independence from the host. Stamp each log with the commit or tree state (R10) so a green log can be tied to the code |
| Branch stays local "because `origin` is public" | Pushing would publish agent-state files | `origin/main` already holds commit `94f2400`, which force-added seven claim files, and the agent reports [V from the 2026-10-18 report]. So some agent state is already public (section 5) |
| One hygiene row | Section 10 item 1 | Split in two (R10) |
| Closing the stubs "when their replacement lands" | R3 | Agree for the interfaces, not for the three that can mislead (R3) |

## 3. Answers to R1 to R10

### R1 — Phase 1 design review

**Hook for the tripwire.** `CardEffects.hasRegisteredEffect(id)` should be the single definition of "registered", used in two stages with different jobs:

* **Stage A, static.** For every card in the four starter decks, report a hit if its census class is behaviour-bearing and `hasRegisteredEffect` is false, using the same canonical-key routed lookup as `EffectCensusChecker`. No game is played, so it is deterministic and takes seconds. The result is the unimplemented backlog, measured. The census says only 23 of the 146 starter records are covered [V], so most of the roughly 100 behaviour-bearing starter cards will hit [I]. That is the point: it turns "unimplemented" into a number.
* **Stage B, runtime.** At the one place the engine records that a card's effect was applied (played, resolved, triggered), record a hit if the card is behaviour-bearing and nothing registered fired. Run in fuzz, it ranks the backlog by how often games reach each card, which tells the effect work where to start. I have not seen `GameController`; if there is no single choke point, adding one is a finding for the decomposition tasks and should come first.

**Limits to state in the tripwire's own output.** "Registered" is not "fully implemented": a card may be registered for one of several clauses. Until the E-1 schema gives clauses their own identity, the tripwire is card-level and must say so.

**Rollout.** Report-only; then a ratchet that fails if a card id enters the unimplemented set that is not in the recorded baseline (compare id sets, never counts); then zero hits on the slice decks. Tests: a synthetic unregistered card hits; a registered one does not; a Deluxe twin of a registered card does not (the routing test); a VANILLA card does not.

**L-2 invariant families, against the rule index** [I from headings; I have not read the rule text]:

| Family | Rulebook sections to cite |
|---|---|
| Cards conserved across zones; no card in two zones | `RB:I/cards-and-tokens`, `RB:II/preparing-the-playing-field`, `RB:III/draw-round`, `RB:VI/discards` |
| Tokens within legal bounds | `RB:I/influence`, `RB:II/set-tension-tokens`, `RB:II/set-influence-tokens`, `RB:II/influence-changes` |
| Actions only in the round the rules allow (the ORD regression) | `RB:III/ready-round`, `RB:III/conflict-round`, `RB:III/action-round`, `RB:III/resolution-round`, `RB:III/draw-round` |
| Ready and rotated state | `RB:III/ready-and-rotated-cards` |
| Conflict participation and outcome | `RB:III/participants`, `RB:IV/conflict-cards`, `RB:IV/aftermath-cards` |
| Victory evaluated consistently | `RB:I/victory` |

Two rules for the implementer: each invariant quotes the rule sentence it relies on, and is labelled `rulebook` (the text states it) or `engine` (internal consistency); an assessor checks each against the pinned text. No invariant may encode a rule the text does not state. Each has a seeded-corruption test showing it catches the corruption.

### R2 — E-0 method against the measured counts

Final, with four amendments:

1. **Confirmed:** the counts match, so the B5-0018 report's "starter TRIGGERED (39)" was a mislabel of the combined 39. Starter TRIGGERED is 20.
2. **Table crosswalk.** Add a step: list every `CardEffects` table (for example the event-influence, conflict-loser, agenda, mark-grant and damage-on-attack tables) and name the primitive each corresponds to. This makes migration mechanical and catches a vocabulary that ignores a concept the engine already has.
3. **Keep the `bio-weapon discovery` check.** Treat it as a vocabulary-completeness test, not a pass or fail: the engine has a loser-influence table and the card has a Deluxe-only loser-influence delta, so an ESCAPE label there means the vocabulary lacks a primitive or the card was misread. The report must say which.
4. **The reviewer must be from a different model family** than the author (R6), and neither may see the other's labels until both are saved.

### R3 — The eight stubs

* **Delete the three that can mislead now:** `HeadlessInvariantCheckerProbe` and `HeadlessFuzzProbe` (both return `true`, so wiring either into the gate would report health nothing measured) and `HeadlessUnimplementedTextTripwire` (a substring scan nothing calls, named like the real thing). Their names record nothing that git history and the corrective DECISIONS entry do not. Cost: one small engine row and a gate re-run; sources go from 189 to 186.
* **Keep the five interfaces** until E-1 and D-1 to D-3 replace them. They are harmless and deleting them adds churn for no gain. Add a one-line stub inventory to DECISIONS so a later report cannot read them as implemented.
* **Fresh ids, not re-scoped rows.** Mark B5-0028 to B5-0035 SUPERSEDED, each naming its replacement, and open new rows with fresh acceptance. Reasons: an acceptance line is the row's contract and rewriting it in place hides that FULL was falsely claimed; the old ids carry stale claim and `.RELEASED` files that already cause N2; and ids are never reused. This needs SUPERSEDED in the lint's status list and the flip tool working (N1).

### R4 — Statuses and promotion checklist, final wording

I am proposing one change from my earlier suggestion: retire the word FULL. It duplicates DONE and invited the inflation seen on 2026-10-18.

> **Statuses.** A ledger row is OPEN, BLOCKED, SUPERSEDED or DONE. A DECISIONS entry labels a result DONE, PARTIAL or STUB. STUB: a shape landed with no behaviour; it is never DONE. PARTIAL: some acceptance lines are met and each unmet line is named. DONE: every acceptance line is met and the checklist below passed. A SUPERSEDED row names the row that replaces it and is never reopened.
>
> **Promotion checklist. A row is DONE only when all hold.** (1) Every scope the change touched was claimed and the claim is released. (2) The report exists under `.agent/REPORTS/` with a provenance block; its author is not its assessor; rows that touch governance, engine or AI behaviour, or that record counts, carry an assessor pass from a different agent. (3) The gate log is from a sole-session run after the last edit, names the commit or tree state it ran on, and its row-count invariant passed; the verdict follows §4. (4) A behaviour change adds or extends a probe that fails without it, and the report names the probe and the failure seen with the change reverted. (5) Every guard or refusal added has a test that makes it fire. (6) Counts quoted in DECISIONS (sources, classes, checks) are copied from tool output, not typed. (7) The ledger status was changed with the flip tool and the lint passes. (8) The encoding lint and the tracked-state check (no claim or heartbeat file in git beyond the README) pass.

Points 3 (log stamping), 5 (guard tests) and 6 (copied counts) come from this report's findings: N1 and N2 are guards that shipped without a test that fires them, and the 184-versus-189 count was typed.

### R5 — Definition of done

Your proposed sentence is close. Two changes: the waiver clause is a loophole, and "Playable Slice" needs its scope named.

> **The Playable Slice is done when, on one commit, two sole-session full-gate runs pass and a seeded soak run over the four starter decks reports zero exceptions, zero invariant violations and zero unimplemented-card hits, with S1 rule coverage of at least 52 of 57 entries.**

A waiver is allowed only through a DECISIONS entry that names the measure, the value reached and the owner; a waived measure is recorded as not met. Before fixing "200 games per pairing", get the per-game time from L-3's first run. Four decks give 6 pairings of different races if games are two-player [?], so 200 each is 1,200 games, which may take hours. Use a smaller number nightly and the full number once, at the milestone. After the Slice, the scope ladder S2 to S5 starts with the League deck.

### R6 — Model routing

I cannot rank the free models, so this routes by task risk, not by name.

| Work | Requirement | Route |
|---|---|---|
| E-0 classification, E-1 schema, L-2 invariant selection | Judgement; errors spread silently | Strongest model you can run. The careful, measuring behaviour in this report is the kind to prefer |
| E-0 review, assessor passes on schema, invariant and governance rows | Different model family from the author | Mandatory. Neither side sees the other's conclusions first |
| Tooling rows (guards, lints, coverage) | Mechanical, testable | Free tier, provided each row's acceptance includes a planted-condition test |
| Effect migrations (E-2 onward) | Oracle exists: conformance plus a probe | Any model, with a different-family assessor on the first batch |
| Claude in this chat | No repo access | Advisory only; never the assessor of its own proposals |

### R7 — Encoding damage

* **The eight `U+FFFD` characters at DECISIONS 508 to 518.** I recommend a minimal in-place repair, not record-as-damaged. By your own wording, they are the separator in the eight FULL headings, where the sibling headings carry an em dash; replacing one character with an ASCII hyphen is not reconstructing prose, which is what caused the earlier regression. One named agent makes the edit, a diff check shows exactly eight characters changed and nothing else, and one correction entry records it. If you prefer to leave history untouched, the fallback is an allowlist of (file, lines, count) in the lint so the lint is green while any new damage still fails. Do not leave the lint permanently red.
* **`docs/DECISIONS_B50017.md`.** Move it to `.agent/RESCUE/` with a one-line superseded header. Marking it in place still leaves a second B5-0017 entry next to the authoritative file, and it contradicts itself (it says two checks fail and also that they were repaired). Do not cite it.
* **N4, the pattern file that trips the lint.** Write its examples in codepoint notation, not as literal damaged text. Do not add a lint carve-out; a carve-out is a hole.

### R8 — Gate tiering

Your numbers are right (232.5 s total, human-seat 185.5 s, everything else about 47 s) [V]. I agree with tiers but would change three things:

1. **Name them "fast gate" and "full gate".** `RUN_TESTS=1` stays the full gate and is what promotion requires; `RUN_TESTS=fast` skips the slow classes for the development loop. A "soak tier" holding the human-seat probe would remove the only continuous check of the human-seat path from promotion.
2. **Reserve the soak tier for L-3 fuzz.** It is not part of promotion until the Slice milestone.
3. **Find out why one probe takes 185 seconds before tiering around it.** The last red log shows it ending at round 1 with no winner after 185 s [V], which suggests waiting or a timeout, not game play. A short profiling row (what the 185 s is spent on) may remove most of the cost, and per-class timing already exists from S-10.

### R9 — Coverage strategy

Classifying the 57 S1 entries by what a check needs [I, from titles only; I have not seen the coverage map, so intersect this with the 13 already covered]:

| Type | Count | Entries |
|---|---|---|
| (a) Cheap conformance check: setup, state, card-type and static rules | 30 | `I/cards-and-tokens`, `I/anatomy-of-a-card`, `I/influence`; nine of the ten `II/` entries (`customizing-your-game-deck` to `beginning-play`); `III/sustained-actions`, `III/ready-and-rotated-cards`, `III/draw-round`; ten `IV/` card-type entries (`status-tracker-card` to `event-cards`, less `agenda-cards`); `VI/personalities`, `VI/discards`, `VI/bonuses-and-blanking`, `VI/cumulative-effects`, `VI/rules-of-precedence` |
| (b) Probe-sized: multi-step scenarios | 16 | `III/ready-round`, `conflict-round`, `action-round`, `resolution-round`, `participants`, `sample-conflict-round`; `IV/agenda-cards`, `conflict-cards`, `aftermath-cards`, `type-of-conflict`, `participant`, `aftermath-example`; `VI/sample-conflict`, `war`, `unconditional-surrender`, `legal-targets` |
| (c) Needs Phase 1 or 2 instruments first | 7 | `I/victory`, `IV/contingency-cards` (no card data), `VI/triggered-effects`, `votes`, `states`, `marks`, and `RB:V` (94 lines, paragraph-level) |
| Prose with little to test (exemption candidates) | 4 | `I/game-overview`, `II/fast-and-loose`, `III/overview`, `III/narrating-the-story` |

Findings from the table:

* **(a) plus (b) is 46 of 57, or 80.7%.** The 90% target (52) cannot be reached without some (c) entries or an exemption list. If the four prose entries are exempted, the denominator is 53 and 90% is 48, so (a), (b) and two (c) entries would do. Exemptions need a DECISIONS entry each and should stay capped; otherwise the target becomes meaningless.
* **Start with the three worked examples** (`III/sample-conflict-round`, `IV/aftermath-example`, `VI/sample-conflict`). The rulebook states their outcomes, so the expected result comes from the text and not from an agent's reading. They are the highest-trust checks available.
* **Order of work:** worked examples, then the nine setup entries in chapter II, then the card-type entries, then the rounds. This can run in parallel with Phase 1 because it needs no new instrument.
* A verified mapping needs someone to read the paragraph. Count only `verified` rows, as the tool already does.

### R10 — What we got wrong, and my own errors

* **N1 and N2 are the right fixes, with two additions.** For N1, resolve the claims and reports paths from the repository root (not from the caller's directory) and fail loudly if the claims directory is missing; do not return nothing silently. Add a planted-live-claim test so the refusal is shown to fire. For N2, make `run-queue.ps1`, `ledger-query.ps1` and `new-claim.ps1` ignore any file named `*.RELEASED.json`, with a planted-marker test. Do not delete the markers; they are the reaping record. Keep the midnight-timestamp guard.
* **My S-4 acceptance was too weak.** It said the flip tool must refuse a live claim but did not require a test that plants one, so the broken refusal passed. Point 5 of the checklist closes that gap.
* **Stub-deletion stance (R3).** Partly right, partly too cautious; see R3.
* **Sequencing.** Do the tooling guard fixes before any Phase 1 claim, because Phase 1 rows use the ledger tools and a silently dead guard can mis-route claims.
* **Stamp every gate log** with the commit hash (or tree hash if uncommitted) and whether the tree was dirty. Without it, "green" cannot be tied to code.

## 4. Updated task plan

Done by the fleet since my v1.2 [V from the report]: S-1, S-2, S-3 (as B5-0037), S-10. The rest of Phase 0 changes as follows. Ids are placeholders; take the next free ones from `append-ledger.ps1`.

| Task | What | Scope | Acceptance |
|---|---|---|---|
| H-1 | Tooling guard fixes: N1, N2, N3 (coverage reads the latest log and rejects a stale one; pin probe independent of working directory), N5 and N6 (archive the five non-conforming heartbeats out of the validated directory; `git rm --cached` the tracked heartbeats; move `b5-0027-workflow.js`), N4 (pattern file in codepoint notation) | tooling | Each guard has a planted-condition test that makes it fire; `validate-heartbeats.ps1`, the ledger lint and the encoding lint exit 0 after H-2's repair; the pin probe passes 21 of 21 from the repo root |
| H-2 | Docs and governance: refresh `00_BOOT.md` step 7 and `HANDOFF.md`; correct the `AGENTS.md` §2 gitignore wording; adopt the R4 block; repair the eight `U+FFFD` headings (R7); move `DECISIONS_B50017.md`; one assessor pass from a different agent on the 2026-10-19 corrective entries | docs | Owner approves the exact text; a DECISIONS entry exists; `git grep` for the four old baseline check names in `00_BOOT.md` finds nothing |
| K-1 | Delete the three behaviour-named stubs | engine | Gate still passes all classes; sources 186; no references remain; stub inventory line added to DECISIONS |
| K-2 | Supersede B5-0028 to B5-0035 and open fresh rows for the Phase 1 and 2 work | docs | Flip tool used; lint passes; each old row names its replacement; the OPEN list stays within 10 |
| G-1 | Profile `HeadlessHumanSeatProbe`; stamp gate logs with commit and dirty flag; add the `RUN_TESTS=fast` mode | tooling, engine | The 185 s is explained; every gate log header names the tree state; fast gate runs in about 60 s or less and excludes only named slow classes |

Then, in this order: **L-1** Stage A (static) in parallel with **E-0** and the first coverage batch (worked examples, then chapter II); then L-1 Stage B and **L-2**; then **L-3** and the first soak run; then E-1 and the effect migrations. Queue discipline is unchanged: no more than 10 OPEN rows, one claim per scope.

## 5. Risks and cautions

* **Origin may already hold agent state.** `origin/main` equals local `main` at `09feb5a` and `94f2400` force-added seven claim files [V from the 2026-10-18 report]. Untracking them later does not remove them from history. Before any decision to rewrite public history, run the snapshot job's secret scan over `origin/main`, and check what personal data the claim, heartbeat and report files contain (host paths, names). The decision to rewrite history belongs to you alone. The local-only rule for task branches is still reasonable; it just does not explain the files already pushed.
* **Do not wire the two `return true` probes into the gate** (K-1 removes the risk).
* **The B5-0017 constants** (aftermath bonus 5.0, AIJ penalty and bonus) were tuned to pass; the mutation check I asked for earlier is still unrun and belongs in an assessor pass.
* **The snapshot job.** Keep it built and disabled until you review it. It does not push by default; confirm that stays true now that `origin` exists.

## 6. Owner decisions, updated

| # | Decision | My recommendation |
|---|---|---|
| O2 | Adopt the R4 status vocabulary (FULL retired) and checklist | Adopt |
| O5 | Fast gate and full gate (R8) | Approve; set the fast-gate budget after G-1 |
| O8 | Snapshot scheduler | Keep as a second safety net, disabled until you review it |
| O9 | Playable Slice is S1 and four decks | Confirmed by the rulebook lines the report cites |
| O10 | Models | Needed from you: which stronger models can you run for E-0, E-1, L-2 and the assessor passes |
| New | Repair the eight `U+FFFD` headings in place (R7) | Approve the minimal repair |
| New | Exemption list for coverage (R9) | Decide after the first coverage batch shows how far (a) and (b) reach |
| New | Whether to audit and possibly scrub public history (section 5) | Audit first; decide after you see what is there |

## 7. What I still need

* `docs/RULE_COVERAGE_MAP.md`, to intersect with the R9 table and say which of the 44 uncovered entries are in each type.
* `flip-ledger.ps1` and `run-queue.ps1`, if you want me to review the N1 and N2 fixes before they are written.
* The answer to O10 (models).
