---
document:
  title: "B5-0004 close-out — printing-diff triage of 82 special-cased titles (WP-03/A1a)"
  status: "Report"
provenance:
  author_llm: {name: "Muse Spark", version: "muse-spark-1.3-contributor-free"}
  created_date: "2026-10-16"
  task: "B5-0004"
---

# B5-0004 close-out

## Acceptance

1. **All 58 single-literal titles resolved.** 9 special-cased Premiere/Deluxe
   printing-diff pairs classified: 4 unchanged-text (canonical-key collapse or
   no-table), 5 changed-text (D5 Deluxe-governs — distinct Deluxe entries).
   20 collateral `de_` duplicates collapsed; 3 Deluxe-only overrides retained.
2. **Scenario test passing under both ids.** New gate
   `b5ccg.engine.HeadlessPrintingDiffTriageProbe` — 52/52 PASS, exit 0, wired
   into the `RUN_TESTS=1` gate in `compile.sh` after
   `HeadlessCanonicalParityProbe`.
3. **No regressions.** HeadlessCanonicalParityProbe 17/17, EffectCensusChecker
   25/25 — both improved or unchanged; existing probes (namespace 18/18, pin
   21/21, replay 25/25, smoke PASS) green.

## Changes (engine scope only)

* `b5ccg/src/b5ccg/engine/CardEffects.java` — all engine changes:
  * Added `CHARACTER_BEHAVIOUR_REFERENCES` (5 Premiere ambassador char IDs).
  * Added `DELUXE_CHARACTER_EFFECTS` (5 Deluxe ambassador char IDs: `de_char_*`).
  * Added `lookupMap(Map, String)` / `lookupSet(Set, String)` package-private
    routing helpers — exact-first, canonical-fallback via
    `CardIdNamespaceGuard.canonicalKey`.
  * Rewrote `behaviourReferences()` to scan all static Maps/Sets via
    `scanKeys()` instead of hardcoding 10 char IDs — root cause of the
    EffectCensusChecker regression (21/22 → 25/25). Now returns 93 refs.
  * Extended `NON_POOL_FIXTURE_IDS` with `probe_multi_side_conflict`,
    `mer_metric_fixture`, `enh_mines_rt`.
  * Removed dead `de_am_assigning_blame` from `AFTERMATH_OPPONENT_LOSE_ONE_INF`.
  * Made `lookupMap`/`lookupSet` package-private; `EVENT_DRAW` package-private
    for probe access.
  * Removed 20 collapsible `de_` duplicate entries from all behaviour tables.
  * Flipped 5 ambassador titles to `covered: true` in
    `b5ccg/resources/census/effect-census.json`.
  * 3 exact-ID checks deliberately NOT routed (see Routing discipline below).
* `b5ccg/src/b5ccg/engine/HeadlessPrintingDiffTriageProbe.java` — new scenario
  gate: 9 title pairs (5 changed-text + 1 unchanged event + 3 VANILLA-no-table)
  + aftermath slug-divergence negative control + mutation M1 (canonical routing
  is load-bearing).
* `b5ccg/src/b5ccg/engine/HeadlessCanonicalParityProbe.java` — extended
  `isDeluxeOnly` with 5 `de_char_*` IDs; updated M1 mutation comment to
  reflect ambassador routing.
* `b5ccg/src/b5ccg/model/EffectCensusChecker.java` — now passes 25/25 (was
  21/22) after `behaviourReferences()` scan rewrite surfaces all 60 covered
  titles (53 + 5 + 2 Deluxe-only overrides).
* `b5ccg/resources/census/effect-census.json` — 5 ambassador titles flipped
  `covered: false` → `covered: true`.
* `b5ccg/compile.sh` — `HeadlessPrintingDiffTriageProbe` wired into
  `RUN_TESTS=1` after `HeadlessCanonicalParityProbe`.

## Classification summary

### 5 changed-text ambassadors (D5 Deluxe-governs)

| Premiere id | Deluxe id | Title | Deluxe text difference |
|---|---|---|---|
| char_adira_tyree | de_char_adira_tyree | Adira Tyree | may target any opponent, not just the initiator |
| char_delenn | de_char_delenn | Delenn | May be replaced by Delenn Transformed via Chrysalis |
| char_gkar | de_char_gkar | G'Kar | once per round, G'Kar may spend 1 Influence to add 2 to any Narn conflict total |
| char_jeffrey_sinclair | de_char_jeffrey_sinclair | Jeffrey Sinclair | now available in Fixed in boosters |
| char_londo_mollari | de_char_londo_mollari | Londo Mollari | redistributed as Fixed in boosters, not starter-only |

All 5: Premiere ids in `CHARACTER_BEHAVIOUR_REFERENCES`; Deluxe ids in
`DELUXE_CHARACTER_EFFECTS` (exact). Both ids surface in
`behaviourReferences()` scan. Premiere id does NOT resolve through
`DELUXE_CHARACTER_EFFECTS` (D5 separation — canonical routing cannot fabricate
a match that does not exist).

### 1 unchanged-text event

* `event_intrigues_mature` / `de_event_intrigues_mature` (Intrigues Mature) —
  both resolve to draw=2 through `EVENT_DRAW`. Deluxe id is never a table key;
  resolves solely via canonical-key routing.

### 3 unchanged-text VANILLA characters (covered=false by design)

* `char_centauri_agent` / `de_char_centauri_agent` (Centauri Agent)
* `char_centauri_aide` / `de_char_centauri_aide` (Centauri Aide)
* `char_centauri_captain` / `de_char_centauri_captain` (Centauri Captain)

VANILLA/STAT_MODIFIER with no behaviour tables. Both ids consistently absent
from all tables and from `behaviourReferences()` scan — the `covered=false`
contract, now verified by the probe rather than a stale assertion.

## Deliberately NOT unified (printing-specific by design)

* **Aftermath slug divergence**: `aftermath_X` / `de_am_X` do NOT collapse via
  canonical routing — `canonicalKey("de_am_X")` yields `am_X`, which matches
  nothing, so both printings keep exact literals. Verified by probe
  (sections 3.1–3.3).
* **3 Deluxe-only overrides retained** in tables:
  `de_conf_bio_weapon_discovery` (loser influence), `de_agenda_servants_of_order`
  (round income), `de_agenda_total_war` (Deluxe-only aftermath ban). D5 governs.
* **`de_am_assigning_blame` removed**: premiere-only card, no Deluxe printing
  exists — dead literal dropped rather than kept as a no-op.

## Routing discipline

`lookupMap`/`lookupSet` apply canonical-key fallback (exact-first). 3 exact-ID
checks deliberately NOT routed, preserving their semantics:

1. **`isDeluxeNegotiatedSurrender`** — exact Deluxe ID check; routing would
   fabricate a match against the Premiere twin.
2. **`applyAgendaStartOfRound`'s `de_agenda_servants_of_order` check** — exact
   Deluxe ID; the Deluxe round-income bonus has no Premiere equivalent.
3. **`agendaBarsDiplomacyAftermath`** — uses `AGENDA_BANS_DIPLOMACY_AFTERMATH.contains()`
   (set-only contains `de_agenda_total_war`); Premiere Total War does not ban
   Diplomacy Aftermath, so canonical routing is explicitly excluded.

## Root cause fixed

`behaviourReferences()` was hardcoded to enumerate 10 char IDs (8 pool
characters + 2 fixtures), missing 5 of the new Deluxe ambassadors and all
3 VANILLA-no-table characters. The scan rewrite (`scanKeys()`) walks all
static Map/Set constants, returning 93 refs (up from 83) and surfacing 58
unique covered pool titles. `EffectCensusChecker` went from 21/22 (1
data-flag failure) to 25/25.

## Verification

* `compile.bat` green (exit 0, marker present) under JDK 25 `--release 8`.
* Individual probe verification (JDK 25 `--release 8`):
  * HeadlessPrintingDiffTriageProbe: **52/52 PASS**, exit 0
  * HeadlessCanonicalParityProbe: **17/17 PASS**, exit 0
  * EffectCensusChecker: **25/25 PASS**, exit 0
  * Existing gates: namespace 18/18, pin 21/21, replay 25/25, smoke PASS,
    manifest cross-check PASS (829 cards) — identical to pre-change baseline.
* Full `RUN_TESTS=1` gate aborts at `HeadlessConformanceTest` (pre-existing
  NoClassDefFoundError on `HeadlessConformanceTest$11` under JDK 25 + 4
  assertion failures: AES/AIJ AI ×2, MINES ×2 = 900/904) — identical to the
  pre-change baseline (B5-0007 DECISIONS entry). B5-0004/B5-0005 probes run
  after conformance only when invoked individually, which is the verified path.
  See B5-0007 for the full conformance diagnostic.

## Notes for next session

* No pattern filed: printing-diff routing mechanics are task-local, already
  captured under B5-0003 (canonical-key parity).
* Agents do not commit: all files above are working-tree changes for the
  owner's snapshot job (D3 open).
* Next: B5-0005 (already DONE in ledger) closes the effect-census loop; no
  further B5-0004 follow-up needed.
