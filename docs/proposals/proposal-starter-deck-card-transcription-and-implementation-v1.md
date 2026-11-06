---
document:
  title: "Proposal: Starter-deck card transcription and implementation batch (approved)"
  status: "Approved by owner (2026-11-07); not yet promoted"
_proposal: true
_status: "approved"
_authority: "authoritative-character-data.json"
_authority_note: "Character Stats (PREMIERE), HUMAN-SCANNED, per AUTHORITY_INDEX.md B5-0086/B5-0090/B5-0079. The effect census is derived and the card pool is a deployed projection; neither is authoritative. Printed card text has no designated authority (AUTHORITY_INDEX.md now says so), so scan-data.json is working evidence, not authority."
owner_approval:
  approved_by: "Tiwa Pene (owner)"
  approved_date: "2026-11-07"
  note: "Owner approved this proposal on 2026-11-07, un-gating B5-0209 ('Depends on approved proposal') and the starter-deck pool rewrite. Approval does not promote it: the verification gate, a DECISIONS entry that agrees with the partition, and claim release are still required before it becomes truth (AGENTS.md §4)."
provenance:
  author_llm: {name: "opencode", version: "beam-1.0-501b (2026-11-04)"}
  created_date: "2026-11-04"
  last_modified_date: "2026-11-07"
  last_modified_by_llm: {name: "opencode (DeepSeek V4 Flash Free)", version: "deepseek-v4-flash-free", family: "DeepSeek"}
  assessor_llm:
    - {name: "Crouton Alpha", version: "crouton-alpha-free-2026-11-05", family: "Crouton Alpha", agent_id: "opencode-crouton-alpha-01", passes: 1, date: "2026-11-05", note: "Independent full audit (proposal + seed rows + spot-checks). Appendix B exact (80), Appendix A stale (102 vs live 91 after B5-0228). REJECT for promotion; ACCEPT as candidate with 6 mandatory corrections. See .agent/REPORTS/2026-11-05-opencode-crouton-alpha-01-assessment-starter-deck-proposal-v1.md"}
    - {name: "opencode (DeepSeek V4.1 Flash Free)", version: "deepseek-v4.1-flash-free", family: "DeepSeek", agent_id: "opencode-deepseek-v4-1-flash-04", passes: 1, date: "2026-11-06", note: "Assessment pass 2, family DeepSeek (distinct from beam, Crouton Alpha and Nemotron). Verified _authority, Appendix A 91, Appendix B 80, overlap 44, remediation disjointness, tripwire 80/330; FAILED section 4(4) (21 rows not 23, B5-0217 DONE) and 7 of 8 RB citations absent from RULE_INDEX. Findings repaired under B5-0232 on 2026-11-06. See .agent/REPORTS/2026-11-06-opencode-deepseek-v4-1-flash-04-assessment-starter-deck-proposal-v2.md"}
  editorial_passes:
    - {name: "Nemotron 3 Ultra", version: "nemotron-3-ultra-2026-11-05", family: "Nemotron", agent_id: "opencode-nemotron-3-ultra-01", date: "2026-11-05", note: "Editorial repair pass, NOT an assessment: corrected _authority, regenerated Appendix A to the live 91, updated counts and overlap, rewrote section 4(4), drafted remediation batches, added the DECISIONS entry. Moved here from assessor_llm on 2026-11-06 (B5-0232) so that assessor_llm lists only independent reviews."}
    - {name: "opencode (DeepSeek V4 Flash Free)", version: "deepseek-v4-flash-free", family: "DeepSeek", agent_id: "opencode-deepseek-v4-flash-free-02", date: "2026-11-07", note: "Editorial/record pass, NOT an assessment: recorded the owner approval (status/_status -> approved, owner_approval block added, last_modified updated). No assessor_llm entry is claimed for this pass."}
---

# Proposal: starter-deck card transcription and implementation batch

## 1. Motivation

Recent playtests report that the live game loop, win/loss conditions, and a
single human player versus three computer players can already reach game
completion once action points are raised to an arbitrarily large value, with
only minor bugs. The remaining practical blocker to the **Playable Slice**
milestone is card coverage: starter decks still contain behaviour-bearing
cards whose printed text has no implementation behind it. This proposal scopes
the next task seeds to **starter-deck cards only**, so implementation and
transcription work directly reduce the unimplemented-starter baseline to zero.

Current unimplemented-text tripwire baseline (starter decks): **80** behaviour-bearing
titles. Implementing the list in Appendix B removes that baseline if each card gains
an engine registry reference and a matching census `covered: true` flag, with no drift.

## 2. Definitions used (candidate data conventions)

- **Starter roster**: unique card ids in `b5ccg/resources/decks/premiere-starter-decks.json`.
  Four starter decks: HUMAN 48 cards, CENTAURI 47, MINBARI 47, NARN 45, totalling **146 unique ids**.
- **Scanned**: a card id present in `scan-data.json` (printed-text record).
- **Behaviour-bearing**: census class not `VANILLA` and not `TRAIT_ONLY`. These classes
  have nothing to implement and are not counted.
- **Logic added**: census `covered: true` AND the card is named by `CardEffects.behaviourReferences()`.
  Census `covered` alone is used here as the proxy for “logic added”; final promotion
  must also verify the registry contains the card.
- **Transcription needed**: starter card absent from `scan-data.json`. This does not imply the
  card has no text in `b5ccg/resources/cards/` or `.agent/CARD-TEXT/`; it means no scan-data
  record is available for authority reconciliation.

## 3. Counts

| Metric | Count |
|---|---:|
| Unique starter-deck cards | 146 |
| Behaviour-free (`VANILLA` + `TRAIT_ONLY`) | 44 |
| Behaviour-bearing, already covered | 22 |
| Behaviour-bearing, currently uncovered (Appendix B) | 80 |
| Missing from `scan-data.json` (Appendix A) | 91 |
| Behaviour-free not applicable | 0 |

Overlap: 44 cards appear in both Appendix A and Appendix B (live re-census 2026-11-05); sequence transcription before
implementation for those where printed text authority is still needed.

> **Note:** Counts updated 2026-11-05 after B5-0228 regenerated `scan-data.json` (446 records, Deluxe-preferred). Previous Appendix A (102) was stale: 34 IDs now present in scan-data, 23 live-missing IDs absent from old Appendix A. Overlap corrected from 56 → 44.

> **Note (2026-11-06):** the seed rows were re-partitioned to this 91 (see §4.1). The ledger previously
> encoded the stale 102, so its transcription ids shared 56 with Appendix B; after re-partition the two
> sets share exactly the 44 above, and the 23 `char_*` ids that the old appendix never listed now have rows.

## 4. Proposed task seeding

1. **Starter transcription batch (candidate)**: create one task row per 10-15 cards from
   Appendix A. For each card, reconcile `scan-data.json` with pool text / `.agent/CARD-TEXT/`
   and, where the pool is empty or differs, add or correct the `text` field via the normal
   card-pool proposal process (no edits to authoritative data without a separate claim).
2. **Starter implementation batch (candidate)**: create one task row per ~10 cards from
   Appendix B, grouped by census class. For each card:
   - wire dispatch/data in `CardEffects` and/or `effects-v0.json` per printed text,
   - extend or add a batch probe,
   - set census `covered: true` only when the registry names the card,
   - keep `RUN_TESTS=1 sh compile.sh` (JDK 25) green.
3. **Verification gate**: `HeadlessUnimplementedTextTripwire` starter count must move from 80 to 0
   with no drift findings; full gate remains green on the recorded baseline exception set.
4. **Correction (2026-11-05, corrected again 2026-11-06):** This proposal seeded **21** task rows
   (`B5-0206..B5-0226`) in `.agent/TASK_LEDGER.md`. None is claimed. Twenty are `BLOCKED`;
   **`B5-0217` (AGENDA_WIN) is `DONE`** on declarative `effects-v0` placeholders that name no card in
   `CardEffects.behaviourReferences()`, so the starter tripwire baseline is unchanged at **80**. The
   transcription rows `B5-0206..B5-0216` carry a `Depends on approved proposal` note in their task text;
   the implementation rows `B5-0218..B5-0224` do not. `B5-0225`/`B5-0226` are remediation rows merged
   into the primary batches and hold no unique scope. Candidates are not truth (§3). Promotion follows
   AGENTS.md §4 after independent assessment.

### 4.1 Batch partition (live 91, re-partitioned 2026-11-06)

Each row below is the exact id set of one `BLOCKED` row in `.agent/TASK_LEDGER.md`. The union is
the live 91; no id appears in two rows; 44 of these ids also appear in Appendix B, and transcription
precedes implementation for those. `B5-0215` and `B5-0216` are retired with no unique scope.

| Row | Families | Count | Card ids |
|---|---|---:|---|
| B5-0206 | aftermath (part 1) | 10 | `aftermath_approval_of_the_grey` `aftermath_exploit_opportunities` `aftermath_hidden_agent` `aftermath_hunted` `aftermath_lamentations` `aftermath_personal_involvement` `aftermath_personal_sacrifice` `aftermath_refugees` `aftermath_renowned_victory` `aftermath_repairing_the_past` |
| B5-0207 | aftermath (part 2) + loc | 8 | `aftermath_retribution` `aftermath_rise_to_power` `aftermath_rivalry` `aftermath_secondary_experience` `aftermath_united_front` `aftermath_war_hero` `loc_quadrant_14` `loc_ragesh_iii` |
| B5-0208 | agenda + enh | 8 | `agenda_finish_the_war` `agenda_knowledge_is_power` `agenda_peace_in_our_time` `agenda_servants_of_order` `enh_energy_mines` `enh_fleet_support_base` `enh_prophecy` `enh_psi_bodyguard` |
| B5-0209 | conf | 9 | `conf_affirmation_of_power` `conf_border_raid` `conf_gunboat_diplomacy` `conf_sabotage` `conf_sleeper_personality` `conf_stop_hostilities` `conf_telepathic_scan` `conf_test_their_mettle` `conf_trade_pact` |
| B5-0210 | char (part 1, scan verification) | 12 | `char_adira_tyree` `char_centauri_agent` `char_centauri_aide` `char_centauri_captain` `char_delenn` `char_frederick_lantz` `char_general_hague` `char_gkar` `char_human_agent` `char_human_aide` `char_human_captain` `char_jadoc` |
| B5-0211 | char (part 2, scan verification) | 11 | `char_jeffrey_sinclair` `char_khamak` `char_londo_mollari` `char_minbari_agent` `char_minbari_aide` `char_minbari_captain` `char_narn_agent` `char_narn_aide` `char_narn_captain` `char_natoth` `char_shal_mayan` |
| B5-0212 | event | 14 | `event_balance` `event_contact_with_vorlons` `event_declaration_of_war` `event_destiny_fulfilled` `event_early_warning` `event_for_the_good_of_all` `event_hidden_knowledge` `event_medical_assistance` `event_short_term_goals` `event_support_babylon5` `event_the_price_of_power` `event_victory_in_my_grasp` `event_you_are_not_ready` `event_you_know_my_reputation` |
| B5-0213 | fleet (part 1) | 10 | `fleet_colonial_centauri` `fleet_colonial_human` `fleet_colonial_minbari` `fleet_colonial_narn` `fleet_deep_space_centauri` `fleet_deep_space_human` `fleet_deep_space_narn` `fleet_first_battle_centauri` `fleet_first_battle_human` `fleet_first_battle_minbari` |
| B5-0214 | fleet (part 2) | 9 | `fleet_first_battle_narn` `fleet_picket_centauri` `fleet_picket_human` `fleet_picket_minbari` `fleet_picket_narn` `fleet_second_battle_centauri` `fleet_second_battle_human` `fleet_second_battle_minbari` `fleet_second_battle_narn` |
| B5-0215 | group | 0 | retired: no group-family starter card is missing |
| B5-0216 | loc | 0 | retired: the 2 live-missing loc cards moved to B5-0207 |


## 5. Scope and authority

- Candidate only. No runtime, card-data, rulebook, or deployed resource changes are proposed here.
- This list is generated from current tree data and may need re-census after each batch.
- Promotion authority: same as other proposals (verification gate, DECISIONS entry, claim release, different-family assessment).

## 6. Remediation corrections (2026-11-05)

Per assessment F3, the original remediation batches B5-0225 (transcription) and B5-0226 (implementation)
contained duplicate IDs already covered by primary batches. After the 2026-11-06 re-partition:

| Duplicate ID | Primary batch | Remediation batch |
|---|---|---|
| `conf_trade_pact` | B5-0209 (conf transcription), B5-0218 (CONFLICT_VARIANT impl) | B5-0225, B5-0226 |
| `event_you_know_my_reputation` | B5-0212 (event transcription), B5-0221 (ONE_SHOT impl) | B5-0225, B5-0226 |
| `loc_ragesh_iii` | B5-0207 (loc transcription), B5-0224 (TRIGGERED impl) | B5-0225, B5-0226 |
| `enh_upgraded_defenses` | B5-0223 (STAT_MODIFIER impl) | B5-0226 |
| `char_hedronn` | B5-0219 (COST_MODIFIER impl) | B5-0226 |
| `char_vir_cotto` | B5-0222 (RESTRICTION impl) | B5-0226 |
| `aftermath_war_hero` | B5-0207 (aftermath transcription) | B5-0225 |
| `agenda_servants_of_order` | B5-0208 (agenda transcription) | B5-0225 |
| `fleet_first_battle_narn` | B5-0213 (fleet transcription) | B5-0225 |
| `fleet_second_battle_narn` | B5-0214 (fleet transcription) | B5-0225 |

`enh_upgraded_defenses` left the transcription set when `scan-data.json` gained its record; only its
implementation duplicate remains.

**Correction (applied 2026-11-06):** the remediation rows are merged into the primary batches and carry
no unique scope — `B5-0225` retains 7 transcription ids, `B5-0226` 6 implementation ids. Both stay
`BLOCKED` as audit rows rather than being deleted.

## 7. DECISIONS entry

A `docs/DECISIONS.md` entry exists: **2026-11-05 — Starter-deck transcription & implementation proposal
corrections (B5-0206..B5-0226)**, rewritten 2026-11-06 under B5-0232. It cites only rule-IDs that
resolve in `docs/RULE_INDEX.md`; the census-to-rulebook mapping is recorded in
`docs/CENSUS_TO_RULE_MAP.md`, and each implementation batch must cite the specific paragraph its own
cards interpret when that batch promotes.

**Owner-approved 2026-11-07** (see the `owner_approval` block in the front matter). Approval un-gates
B5-0209 ("Depends on approved proposal") and the starter-deck pool rewrite; it does not promote this
proposal.

Before promotion the entry must still agree with the partition in §4.1 and with the `_authority`
position recorded in the front matter.


## Appendix A - starter cards missing from `scan-data.json` (91)

### A.aftermath

| Card id |
|---|
| `aftermath_approval_of_the_grey` |
| `aftermath_exploit_opportunities` |
| `aftermath_hidden_agent` |
| `aftermath_hunted` |
| `aftermath_lamentations` |
| `aftermath_personal_involvement` |
| `aftermath_personal_sacrifice` |
| `aftermath_refugees` |
| `aftermath_renowned_victory` |
| `aftermath_repairing_the_past` |
| `aftermath_retribution` |
| `aftermath_rise_to_power` |
| `aftermath_rivalry` |
| `aftermath_secondary_experience` |
| `aftermath_united_front` |
| `aftermath_war_hero` |

### A.agenda

| Card id |
|---|
| `agenda_finish_the_war` |
| `agenda_knowledge_is_power` |
| `agenda_peace_in_our_time` |
| `agenda_servants_of_order` |

### A.char

| Card id |
|---|
| `char_adira_tyree` |
| `char_centauri_agent` |
| `char_centauri_aide` |
| `char_centauri_captain` |
| `char_delenn` |
| `char_frederick_lantz` |
| `char_general_hague` |
| `char_gkar` |
| `char_human_agent` |
| `char_human_aide` |
| `char_human_captain` |
| `char_jadoc` |
| `char_jeffrey_sinclair` |
| `char_khamak` |
| `char_londo_mollari` |
| `char_minbari_agent` |
| `char_minbari_aide` |
| `char_minbari_captain` |
| `char_narn_agent` |
| `char_narn_aide` |
| `char_narn_captain` |
| `char_natoth` |
| `char_shal_mayan` |

### A.conf

| Card id |
|---|
| `conf_affirmation_of_power` |
| `conf_border_raid` |
| `conf_gunboat_diplomacy` |
| `conf_sabotage` |
| `conf_sleeper_personality` |
| `conf_stop_hostilities` |
| `conf_telepathic_scan` |
| `conf_test_their_mettle` |
| `conf_trade_pact` |

### A.enh

| Card id |
|---|
| `enh_energy_mines` |
| `enh_fleet_support_base` |
| `enh_prophecy` |
| `enh_psi_bodyguard` |

### A.event

| Card id |
|---|
| `event_balance` |
| `event_contact_with_vorlons` |
| `event_declaration_of_war` |
| `event_destiny_fulfilled` |
| `event_early_warning` |
| `event_for_the_good_of_all` |
| `event_hidden_knowledge` |
| `event_medical_assistance` |
| `event_short_term_goals` |
| `event_support_babylon5` |
| `event_the_price_of_power` |
| `event_victory_in_my_grasp` |
| `event_you_are_not_ready` |
| `event_you_know_my_reputation` |

### A.fleet

| Card id |
|---|
| `fleet_colonial_centauri` |
| `fleet_colonial_human` |
| `fleet_colonial_minbari` |
| `fleet_colonial_narn` |
| `fleet_deep_space_centauri` |
| `fleet_deep_space_human` |
| `fleet_deep_space_narn` |
| `fleet_first_battle_centauri` |
| `fleet_first_battle_human` |
| `fleet_first_battle_minbari` |
| `fleet_first_battle_narn` |
| `fleet_picket_centauri` |
| `fleet_picket_human` |
| `fleet_picket_minbari` |
| `fleet_picket_narn` |
| `fleet_second_battle_centauri` |
| `fleet_second_battle_human` |
| `fleet_second_battle_minbari` |
| `fleet_second_battle_narn` |

### A.loc

| Card id |
|---|
| `loc_quadrant_14` |
| `loc_ragesh_iii` |

## Appendix B - starter behaviour-bearing cards currently uncovered (80)

### B.aftermath

| Card id | Title | Census class |
|---|---|---|
| `aftermath_hunted` | Hunted | ONE_SHOT |
| `aftermath_personal_involvement` | Personal Involvement | DECK_HAND |
| `aftermath_personal_sacrifice` | Personal Sacrifice | ONE_SHOT |
| `aftermath_renowned_victory` | Renowned Victory | ONE_SHOT |
| `aftermath_repairing_the_past` | Repairing the Past | ONE_SHOT |
| `aftermath_rivalry` | Rivalry | ONE_SHOT |
| `aftermath_secondary_experience` | Secondary Experience | ONE_SHOT |

### B.agenda

| Card id | Title | Census class |
|---|---|---|
| `agenda_alliance_of_races` | Alliance of Races | AGENDA_WIN |
| `agenda_finish_the_war` | Finish the War | AGENDA_WIN |
| `agenda_knowledge_is_power` | Knowledge is Power | AGENDA_WIN |
| `agenda_never_again` | Never Again | AGENDA_WIN |
| `agenda_peace_in_our_time` | Peace In Our Time | AGENDA_WIN |
| `agenda_power_politics` | Power Politics | AGENDA_WIN |
| `agenda_revenge` | Revenge | AGENDA_WIN |

### B.char

| Card id | Title | Census class |
|---|---|---|
| `char_carn_mollari` | Carn Mollari | TRIGGERED |
| `char_centauri_captain` | Centauri Captain | STAT_MODIFIER |
| `char_draal` | Draal | TRIGGERED |
| `char_frederick_lantz` | Frederick Lantz | TRIGGERED |
| `char_general_hague` | General Hague | RESTRICTION |
| `char_hedronn` | Hedronn | COST_MODIFIER |
| `char_human_captain` | Human Captain | STAT_MODIFIER |
| `char_kalain` | Kalain | STAT_MODIFIER |
| `char_khamak` | Kha'Mak | TRIGGERED |
| `char_lady_morella` | Lady Morella | TRIGGERED |
| `char_lennier` | Lennier | TRIGGERED |
| `char_lyta_alexander` | Lyta Alexander | STAT_MODIFIER |
| `char_michael_garibaldi` | Michael Garibaldi | TRIGGERED |
| `char_minbari_captain` | Minbari Captain | STAT_MODIFIER |
| `char_minbari_telepath` | Minbari Telepath | STAT_MODIFIER |
| `char_nakal` | Na'Kal | STAT_MODIFIER |
| `char_narn_captain` | Narn Captain | STAT_MODIFIER |
| `char_natoth` | Na'Toth | STAT_MODIFIER |
| `char_ngrath` | N'Grath | TRIGGERED |
| `char_shal_mayan` | Shal Mayan | STAT_MODIFIER |
| `char_stephen_franklin` | Stephen Franklin | TRIGGERED |
| `char_susan_ivanova` | Susan Ivanova | STAT_MODIFIER |
| `char_talon` | Ta'Lon | RESTRICTION |
| `char_vir_cotto` | Vir Cotto | RESTRICTION |

### B.conf

| Card id | Title | Census class |
|---|---|---|
| `conf_affirmation_of_power` | Affirmation of Power | CONFLICT_VARIANT |
| `conf_border_raid` | Border Raid | CONFLICT_VARIANT |
| `conf_euphrates_treaty` | Euphrates Treaty | CONFLICT_VARIANT |
| `conf_gunboat_diplomacy` | Gunboat Diplomacy | CONFLICT_VARIANT |
| `conf_kidnapping` | Kidnapping | CONFLICT_VARIANT |
| `conf_limited_strike` | Limited Strike | CONFLICT_VARIANT |
| `conf_sabotage` | Sabotage | CONFLICT_VARIANT |
| `conf_sleeper_personality` | Sleeper Personality | CONFLICT_VARIANT |
| `conf_stop_hostilities` | Stop Hostilities | CONFLICT_VARIANT |
| `conf_supplement_security` | Supplement Security | CONFLICT_VARIANT |
| `conf_telepathic_scan` | Telepathic Scan | CONFLICT_VARIANT |
| `conf_test_their_mettle` | Test Their Mettle | CONFLICT_VARIANT |
| `conf_trade_pact` | Trade Pact | CONFLICT_VARIANT |

### B.enh

| Card id | Title | Census class |
|---|---|---|
| `enh_book_of_gquan` | Book of G'Quan | STAT_MODIFIER |
| `enh_crystal_cities` | Crystal Cities | TRIGGERED |
| `enh_fleet_support_base` | Fleet Support Base | STAT_MODIFIER |
| `enh_prophecy` | Prophecy | DECK_HAND |
| `enh_psi_bodyguard` | Psi Bodyguard | STAT_MODIFIER |
| `enh_upgraded_defenses` | Upgraded Defenses | STAT_MODIFIER |

### B.event

| Card id | Title | Census class |
|---|---|---|
| `event_decisive_tactics` | Decisive Tactics | ONE_SHOT |
| `event_declaration_of_war` | Declaration of War | ONE_SHOT |
| `event_early_warning` | Early Warning | DECK_HAND |
| `event_for_the_good_of_all` | For the Good of All | ONE_SHOT |
| `event_hidden_knowledge` | Hidden Knowledge | DECK_HAND |
| `event_level_the_playing_field` | Level the Playing Field | ONE_SHOT |
| `event_medical_assistance` | Medical Assistance | ONE_SHOT |
| `event_popular_support` | Popular Support | ONE_SHOT |
| `event_short_term_goals` | Short Term Goals | ONE_SHOT |
| `event_support_babylon5` | Support Babylon 5 | ONE_SHOT |
| `event_the_price_of_power` | The Price of Power | ONE_SHOT |
| `event_underworld_connections` | Underworld Connections | DECK_HAND |
| `event_victory_in_my_grasp` | Victory in My Grasp | ONE_SHOT |
| `event_you_are_not_ready` | You Are Not Ready | ONE_SHOT |
| `event_you_know_my_reputation` | You Know My Reputation | ONE_SHOT |

### B.group

| Card id | Title | Census class |
|---|---|---|
| `group_psi_corps_intelligence` | Psi Corps Intelligence | TRIGGERED |

### B.loc

| Card id | Title | Census class |
|---|---|---|
| `loc_centauri_prime` | Centauri Prime | TRIGGERED |
| `loc_earth` | Earth | TRIGGERED |
| `loc_mars_colony` | Mars Colony | TRIGGERED |
| `loc_minbar` | Minbar | TRIGGERED |
| `loc_narn_homeworld` | Narn Homeworld | TRIGGERED |
| `loc_quadrant_14` | Quadrant 14 | TRIGGERED |
| `loc_ragesh_iii` | Ragesh III | TRIGGERED |

---
End of proposal.
