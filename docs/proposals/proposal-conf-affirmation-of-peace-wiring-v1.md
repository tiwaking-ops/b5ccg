---
document:
  title: "Proposal: conf_affirmation_of_peace — printed-text wiring into CardEffects / RulesEngine (approved)"
  status: "Approved by owner (2026-11-07); not yet promoted"
_proposal: true
_status: "approved"
_authority: "authoritative-character-data.json"
_authority_note: "The only registered Source of Truth is Character Stats (PREMIERE), per AUTHORITY_INDEX.md. Printed card text has no designated authority (AUTHORITY_INDEX.md B5-0232), so scan-data.json and .agent/CARD-TEXT/ are working evidence, not authority. This proposal wires a card whose printed text is transcribed but never implemented."
owner_approval:
  approved_by: "Tiwa Pene (owner)"
  approved_date: "2026-11-07"
  note: "Owner approved this proposal on 2026-11-07, sanctioning B5-0250 to proceed against this plan. Approval does not promote it: the verification gate, a DECISIONS entry, and claim release are still required before it becomes truth (AGENTS.md §4)."
provenance:
  author_llm: {name: "opencode (DeepSeek V4 Flash Free)", version: "deepseek-v4-flash-free", family: "DeepSeek"}
  created_date: "2026-11-07"
  last_modified_date: "2026-11-07"
  last_modified_by_llm: {name: "opencode (DeepSeek V4 Flash Free)", version: "deepseek-v4-flash-free", family: "DeepSeek"}
  editorial_passes:
    - {name: "opencode (DeepSeek V4 Flash Free)", version: "deepseek-v4-flash-free", family: "DeepSeek", agent_id: "opencode-deepseek-v4-flash-free-02", date: "2026-11-07", note: "Editorial/record pass, NOT an assessment: recorded the owner approval (status/_status -> approved, owner_approval block updated, §1 approval paragraph added). No assessor_llm entry is claimed for this pass."}
---

# Proposal: `conf_affirmation_of_peace` — printed-text wiring into CardEffects / RulesEngine

## 1. Motivation

`conf_affirmation_of_peace` ("Affirmation of Peace") is in the premiere starter
decks — `b5ccg/resources/decks/premiere-starter-decks.json` line 3 (HUMAN,
count 1) and line 98 (MINBARI, count 1). Its printed text is fully transcribed
(`.agent/CARD-TEXT/conf_affirmation_of_peace.md`, extracted 2026-10-23;
`scan-data.json` lines 1216-1257, verdict CHANGED, extractionDate 2026-11-05;
both agree verbatim), yet the engine still runs the old pool-published behavior:

- **Printed text**: "If this conflict succeeds, all tensions for all players are
  lowered by 1. Also, if this conflict succeeds, hold a Babylon 5 vote. If the
  vote passes, you and Babylon 5 each gain 2 influence."
- **Current pool** (`b5ccg/resources/cards/premiere.json` line 2632):
  "Diplomacy conflict. Winner gains 2 Influence. Loser loses 1 Influence."
  (`influenceReward: 2`)
- **Current engine**: winner gains `conflict.getInfluenceReward()` (= 2) at
  `RulesEngine.resolveConflict` line 749, and the loser loses 1 via
  `CardEffects.CONFLICT_LOSER_INFLUENCE` (line 218, `conf_affirmation_of_peace
  = 1`) applied at `applyConflictOutcome` (line 569).

The printed text has **no winner +2** and **no loser −1**. It is a tension-lowering
conflict that calls a Babylon 5 vote, and on a passed vote grants the initiator
and Babylon 5 each +2 influence. The starter-deck proposal
(`docs/proposals/proposal-starter-deck-card-transcription-and-implementation-v1.md`,
owner-approved 2026-11-07) lists the card in neither Appendix A (it carries
scan-data) nor Appendix B (no behavior batch was seeded for it): a genuine
"scanned but not wired" coverage gap. This proposal scopes exactly that gap, so
B5-0250 (the B5-0191 repair re-seed) can be claimed with a concrete plan.

**Owner-approved 2026-11-07** (see the `owner_approval` block in the front
matter). Approval sanctions B5-0250 to proceed against this plan; it does not
promote this proposal — the verification gate, a DECISIONS entry, and claim
release are still required (AGENTS.md §4).

## 2. Evidence

| Source | Verdict | Rules text |
|---|---|---|
| `.agent/CARD-TEXT/conf_affirmation_of_peace.md` (2026-10-23) | CHANGED vs pool, complete | "If this conflict succeeds, all tensions for all players are lowered by 1. Also, if this conflict succeeds, hold a Babylon 5 vote. If the vote passes, you and Babylon 5 each gain 2 influence." |
| `scan-data.json` 1216-1257 (2026-11-05) | CHANGED, transcriptionStatus complete | identical, verbatim |
| `premiere.json` 2632 (current pool) | stale | "Diplomacy conflict. Winner gains 2 Influence. Loser loses 1 Influence." |

Engine facts this change set must touch (all verified on the tree as it stands):

- `RulesEngine.java` line 749 `winner.gainInfluence(conflict.getInfluenceReward())`
  — the flat winner reward, sourced from `ConflictCard.influenceReward` (pool).
- `CardEffects.java` line 218 `CONFLICT_LOSER_INFLUENCE.put("conf_affirmation_of_peace", 1)`
  and line 569 `applyConflictOutcome` — the loser penalty the printed text does
  not contain.
- No tension-lowering surface exists: `GameState.raiseTension` /
  `TensionMatrix.raiseTension` (clamps 0..5, accepts a negative delta) move one
  directed pair; there is no "lower all tensions for all players by 1" API.
- The vote machinery is agenda-only and `AgendaCard`-typed:
  `RulesEngine.canOpenAgendaVote`/`openAgendaVote` (3127), `resolveCouncilVote`
  (3260), `closeAgendaVote` (3227); `GamePhase.AGENDA_VOTE` is missing from the
  enum (scope dependency recorded at RulesEngine.java:3060). `BABYLON5_VOTE` is
  declared in `VotePrimitives.java` with zero engine references.
- `B50191NonStarterConflictBatchProbe.java` (untracked) lists
  `conf_affirmation_of_peace` in `BATCH_CARD_IDS` and requires each entry to
  carry a loser-penalty operation (`ROTATE_TARGET`/`INFLUENCE_LOSE`/`DISCARD`/
  `STAT_MODIFY`, lines 112-113) — a requirement this card's printed text
  contradicts.
- `HeadlessEffectSchemaProbe.java` pins `EXPECTED_ENTRIES = 214`,
  `EXPECTED_CARDS = 204`.

## 3. Change set (the wiring B5-0250 will implement)

### 3.1 v0 effect entry (probe-checked data lane)

Add one entry to `b5ccg/resources/effects/effects-v0.json` for
`conf_affirmation_of_peace`, shaped after the VOTE-window precedent
`event_support_babylon5` (window VOTE, target scope BABYLON5) and the
tension precedent `event_subliminal_influence` (TENSION_CHANGE amount 1):

- `window`: CONFLICT_RESOLUTION (success) — or the nearest existing window that
  fires at conflict resolution; if none exists, the entry declares the
  resolution window the runtime branch keys on.
- `operations`:
  1. `TENSION_CHANGE` — all players, amount −1 (on success). Requires a new
     bulk-lowering primitive/parameter: "all tensions for all players lowered
     by 1" is not `TENSION_CHANGE_ALL_OTHERS` (that targets other *players'*
     trackers, not *all pairs*). Propose `TENSION_CHANGE_ALL` (params:
     `amount: -1`) in the TENSION_CONTROL family, or an `all: true` parameter —
     the implementer names the exact shape and updates the primitive javadoc +
     census class if the census distinguishes it.
  2. `BABYLON5_VOTE` — `onPass`: `[INFLUENCE_CHANGE scope SELF amount 2,
     INFLUENCE_CHANGE scope BABYLON5 amount 2]`, `onFail`: `[]`.
- `provenance.ruleIds`: `RB:VI/tension-and-unrest` + `RB:VI/votes` (both
  resolve in `docs/RULE_INDEX.md`; see §6).
- `textSha256` of the printed rules text, `printingScope` SINGLE_PRINTING
  (premiere only).

### 3.2 Runtime wiring (behaviour lane — hand-wired, per repo reality)

v0 entries are probe-checked data; runtime behaviour is hand-wired in
CardEffects/RulesEngine/GameController. Both lanes are required:

1. **Remove the stale pool behaviour for this card.**
   - Delete the `conf_affirmation_of_peace` entry from
     `CardEffects.CONFLICT_LOSER_INFLUENCE` (line 218). Printed text has no
     loser penalty; the loser-penalty probe contract must be adapted (§3.3).
   - Bypass/replace the flat winner reward at RulesEngine:749 for this card:
     on success the winner's influence comes from the vote's `onPass`
     (+2, if the vote passes), not from `influenceReward`. Set the pool's
     `influenceReward` to 0 **and** update the pool text to the printed text
     (or special-case at resolution); the implementer chooses the least invasive
     correct route and names it in the report. The card's own printed text is
     the governing rule; the stale pool line is corrected, not enforced.
2. **Tension lowering on success.** Add a bulk API, e.g.
   `GameState.lowerAllTensions(int delta)` delegating to
   `TensionMatrix` (iterating all directed source→target pairs, reusing the
   existing 0..5 clamp — a negative delta already lowers). Call it from the
   conflict-resolution success branch for this card before the vote, and log it.
3. **The Babylon 5 vote on success.** The existing vote session is
   `AgendaCard`-typed, so a conflict-time vote cannot reuse it unchanged. Two
   defensible routes, implementer picks and documents:
   - (a) Generalise the vote session to card-agnostic ("measure under vote" with
     a title/context instead of an `AgendaCard`), keeping
     `resolveCouncilVote`'s one-more-Yes-than-No + League tie-break as the sole
     authority (B5-1993); or
   - (b) drive the *existing* agenda-vote machinery with the conflict card as
     the measure when the `AGENDA_VOTE` phase dependency lands, and document
     the phase gap as the reason the vote is prompt-driven in the interim.
   Either way: `BABYLON5_VOTE` becomes a real engine surface for the first
   time; the vote resolves through the B5-1993 rules; on pass, initiator (the
   "you" of the card = the conflict winner on success) gains 2 influence and
   `state.getStation().gainInfluence(2)` applies the Babylon 5 +2 (capped at
   `Babylon5Station.MAX_RATING`). On fail, no influence is gained and the
   tension lowering stands.
4. **HeadlessEffectSchemaProbe re-pin.** `EXPECTED_ENTRIES` 214 → 215,
   `EXPECTED_CARDS` 204 → 205 (one new entry, one new card id). The re-pin is
   part of this change set, not an afterthought: the probe fails until both the
   entry and the count move together.

### 3.3 Probe contract

- Wire `B50191NonStarterConflictBatchProbe` into `compile.sh` `GATE_CLASSES`
  (the B5-0191 delivery never landed — the class is untracked and absent from
  the list at every commit; verified 2026-11-07).
- **Adapt its loser-penalty requirement for this card.** The probe demands a
  loser-penalty operation for every batch id; `conf_affirmation_of_peace`'s
  printed text has none. The probe must exempt this card from the
  loser-penalty assertion and instead assert its real contract: a
  TENSION_CHANGE-all entry and a BABYLON5_VOTE entry with an `onPass` granting
  SELF + BABYLON5 +2 each. The exemption is per-id, named in a comment, and the
  probe's failing-name set must change in exactly that way (red-then-green
  compared by failing-name set per B5-0017).
- A new/extended runtime probe (or an extension of the existing conflict probe)
  must fail without the change: resolve a `conf_affirmation_of_peace` conflict,
  assert all directed tensions drop by 1 on success, no loser −1 is applied,
  no flat +2 lands before the vote, and a passed vote grants the initiator +2
  and Babylon 5 +2 while a failed vote grants neither. This is the
  behaviour-bearing probe the task's acceptance requires.

## 4. Pool and census

- `b5ccg/resources/cards/premiere.json` `conf_affirmation_of_peace`: `text` →
  printed text, `influenceReward` → 0 (per §3.2). This is the same class of
  correction the starter-deck proposal's implementation batches perform; the
  card is not in B5-0209's list, so this row owns it.
- The effect census: if `TENSION_CHANGE_ALL` is added as a new primitive family
  member, the census class table and any primitive-fit classification that
  references the tension family are updated in the same change set (the
  implementer reports the delta).

## 5. Scope and authority

- Scopes touched: `engine` (CardEffects, RulesEngine, TensionMatrix/GameState,
  probes, compile.sh GATE_CLASSES), `resources` (effects-v0.json, premiere.json),
  `docs` (DECISIONS entry). One row claims all three; no other live claim may
  hold any of them.
- Authority: printed card text has **no designated Source of Truth**
  (AUTHORITY_INDEX.md, B5-0232). `scan-data.json` and `.agent/CARD-TEXT/` are
  working evidence, not authority — this proposal cites them as evidence of the
  printed text, not as authority for it. The only registered SoT this change
  touches indirectly is `authoritative-character-data.json` (Character Stats),
  which the Data-Authority Gate keeps matching on the deployed pool; this change
  does not move any character stat.
- Promotion: same as other proposals — verification gate
  (`RUN_TESTS=1 sh compile.sh` exits 0, including the Data-Authority Gate),
  DECISIONS entry, claim release, different-family assessment.

## 6. DECISIONS entry

Appended via `.agent/tools/new-decisions-entry.ps1` when this change set lands:
an interpretation entry for `conf_affirmation_of_peace` citing only IDs that
resolve in `docs/RULE_INDEX.md`:

- `RB:VI/tension-and-unrest` (:972-979) — the tension tracker rules the
  all-players −1 interprets (tension range, and that tension is lowered by
  card effects).
- `RB:VI/votes` (:789-798) — the Council vote rules the card's Babylon 5 vote
  runs under (Yes/No/Abstain, one-more-Yes-than-No, League tie-break), via the
  B5-1993 `resolveCouncilVote` authority.

The entry states the printed text, the old pool behavior it replaces, and the
rulebook anchors; it does not invent an `RB:` token.

## 7. Acceptance

- `effects-v0.json` has a `conf_affirmation_of_peace` entry with a
  TENSION_CHANGE-all and a BABYLON5_VOTE operation with the `onPass`/`onFail`
  split, and provenance citing `RB:VI/tension-and-unrest` + `RB:VI/votes`.
- `CardEffects.CONFLICT_LOSER_INFLUENCE` no longer contains
  `conf_affirmation_of_peace`; the pool text/reward for the card match the
  printed text (reward 0).
- Tensions for all players drop by 1 on a successful resolution; a Babylon 5
  vote is held; passed vote grants the initiator +2 and Babylon 5 +2; failed
  vote grants neither.
- `B50191NonStarterConflictBatchProbe` is in `GATE_CLASSES`, exempts this card
  from the loser-penalty assertion, and passes; `HeadlessEffectSchemaProbe`
  re-pinned to 215/205 and passing.
- `RUN_TESTS=1 sh compile.sh` exits 0 (verification gate + Data-Authority
  Gate), compared by failing-name set per B5-0017.
- DECISIONS entry appended; report filed in `.agent/REPORTS/`; all changes
  remain uncommitted (AGENTS.md §5 — the human commits).
