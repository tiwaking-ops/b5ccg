# b5-claude-files.md — Consolidated request bundle

This single file consolidates the five items Claude requested, in order:

1. `effect-census.json` class counts
2. `CardEffects.java` (the card-behaviour core)
3. ~30 card records across every type + titles differing between printings
4. `.agent/00_BOOT.md`, `compile.bat`, and the first/last 40 rows of `TASK_LEDGER.md`
5. The headings of `docs/DECISIONS.md`

All content is quoted verbatim or summarized faithfully from the repository at
`C:\temp\projects\b5ccg` (read 2026-10-16).

---

## 1. effect-census.json — class counts

Source: `b5ccg/resources/census/effect-census.json`
(schema_version 1, task B5-0005, 446 records, 14-class taxonomy).

**Class counts (descending):**

| Class | Count |
|---|---|
| ONE_SHOT | 93 |
| TRIGGERED | 67 |
| STAT_MODIFIER | 63 |
| CONFLICT_VARIANT | 59 |
| VANILLA | 43 |
| DECK_HAND | 33 |
| RESTRICTION | 30 |
| AGENDA_WIN | 26 |
| TRAIT_ONLY | 19 |
| COST_MODIFIER | 7 |
| BESPOKE | 6 |
| **TOTAL** | **446** |

Taxonomy (declared in the file):
`VANILLA, TRAIT_ONLY, STAT_MODIFIER, ONE_SHOT, CONFLICT_VARIANT, TRIGGERED,
RESTRICTION, COST_MODIFIER, MARK, STATE, VOTE, AGENDA_WIN, DECK_HAND, BESPOKE`.

Census method (from file): "LLM first pass over effective text (Deluxe governs
dual titles, D5); covered = named by >=1 CardEffects behaviour literal;
starter = member of premiere-starter-decks". 11 of the 14 taxonomy classes
appear in the data — `MARK`, `STATE`, and `VOTE` are declared but have 0 rows.

**Read on size:** the effect-system work is **large**. ~31% of titles
(ONE_SHOT + TRIGGERED = 160) and the long STAT_MODIFIER / CONFLICT_VARIANT
tails mean a behaviour layer rather than a few branches. Only ~30% are
VANILLA/TRAIT_ONLY/covered-trivial, so the uncovered 70% is where the work lives.

---

## 2. CardEffects.java

Path: `b5ccg/src/b5ccg/engine/CardEffects.java`
Total length: **1782 lines**. The full file is too large to inline verbatim;
below is the complete class header/structure map and the key public entry
points, verbatim. The remaining body is a large id-keyed dispatch table plus
per-aftermath effect handlers.

### Class header + doc comment (verbatim, lines 1–30)

```java
package b5ccg.engine;

import b5ccg.model.*;
import b5ccg.model.enums.*;
import java.util.*;

/**
 * Central per-card effect dispatch. Keys effects on the card id exactly as it
 * appears in the card JSON (b5ccg/resources/cards/*.json) — no card-text
 * parsing. Every entry is data-verified: ids come from the JSON, enhancement
 * magnitudes come from the JSON bonus fields, and event/agenda/conflict
 * magnitudes come from the card text stored in that JSON.
 *
 * Wiring (GameController / RulesEngine):
 *   - Events:       applyPlayEvent replaces the generic "draw 1" in
 *                   applyGenericCardPlay; unknown ids keep that behaviour.
 *   - Enhancements: applyPlayEnhancement attaches to a live target and applies
 *                   the JSON bonus fields via CharacterCard.applyStatDelta /
 *                   FleetCard.applyMilitaryDelta (existing model methods).
 *   - Conflicts:    applyConflictOutcome runs loser penalties after the
 *                   winner's influence reward; winner reward stays in
 *                   RulesEngine.resolveConflict.
 *   - Agendas:      applyAgendaStartOfRound (RulesEngine.startRound),
 *                   applyAgendaOnPlay + applyAgendaDiplomacyWin
 *                   (GameController).
 *
 * The model interface CardEffect remains declared; the id-keyed tables below
 * are its implementation. Java 6 only: no lambdas, method refs, streams,
 * diamonds, or try-with-resources.
 */
public final class CardEffects {

    private CardEffects() { }
```

### Sections present (line anchors)

- **`WaiverEffect` enum + `WAIVER_EFFECTS` map** (lines 35–77) — free-action
  join waivers, id-keyed; `conf_non_aligned_support` → `FREE_PARTICIPANT`.
- **`MULTI_SIDE_CONFLICT_IDS` set + `isMultiSideConflict`** (57–65) —
  non-opposable support-multiple-side conflicts; currently
  `probe_multi_side_conflict` only (implemented-but-untriggered).
- **Event tables** (79–109): `EVENT_INFLUENCE`, `EVENT_DRAW`,
  `EVENT_ALL_INFLUENCE` — magnituded by card id from text.
- **Conflict tables** (111–144): `CONFLICT_LOSER_INFLUENCE`,
  `CONFLICT_LOSER_DISCARD`, `CONFLICT_WINNER_STEAL`.
- **Agenda tables** (146–205): `AGENDA_ROUND_INFLUENCE`,
  `AGENDA_DIPLOMACY_WIN`, `AGENDA_FLEET_PLUS1`, `AGENDA_BANS_DIPLOMACY_AFTERMATH`,
  plus `MarkGrant` record + `AGENDA_MARK_GRANTS`.
- **Enhancement / Event mark grants** (186–238): `ENH_LOCATION_INCOME`,
  `ENHANCEMENT_MARK_GRANTS`, `EVENT_MARK_GRANTS`.
- **Public entry points** (240+):
  - `applyPlayEvent(GameState, Player, Card)` (243) — Events; generic draw-1 floor.
  - `revealContingency(...)` (275) — Contingency reveal uses same dispatch.
  - `applyPlayEnhancement(GameState, Player, EnhancementCard)` (303) — attaches
    to best fleet/character or opponent target; routes bonuses through the
    registry (B5-0366/0473/0506 seams).
  - `applyConflictOutcome(...)` (443) — loser penalties + winner steal, plus
    conflict-type generic PSI/INTRIGUE effects.
  - `applyAftermathEffect(...)` (798) — per-id aftermath dispatch (Negotiated
    Surrender, Diplomatic Advantage, United Front, Draw-Two-Intrigue, Hidden
    Agent, Refugees, Rise to Power, War Hero, Approval of the Grey, Focus Your
    Efforts, Glory, Retribution, Reverse Advances, Opponent-Lose-One/Two-Inf,
    Crisis of Self, Heal Face Down, Battle Tested, Rescue, Martyr).
- **Aftermath trigger evaluation (B5-2002)** (1120–1298): `AftermathTrigger`
  immutable situation type, `triggerMet(...)`, `hasRegisteredEffect(...)` (the
  authoritative 20-term membership test), and `fireAttachedAftermathTriggers(...)`
  phase-boundary evaluator with loud no-op on unregistered-but-gated cards.
- **Agenda application** (1300+): `applyAgendaOnPlay`, `applyAgendaStartOfRound`,
  `applyAgendaDiplomacyWin`.

### Notable design facts (for sizing the effect work)

- Every effect is **id-keyed**, not text-parsed; magnitudes come from JSON
  (`bonus` fields) or card text stored in JSON.
- **Deluxe-only deltas** are kept in separate `de_*` registries (e.g.
  `de_conf_bio_weapon_discovery`, `de_agenda_total_war`) so the Premiere twin
  is unchanged "by construction rather than by a second branch" (B5-1045).
- A **single 20-term OR** membership test is duplicated at `GameController:903–923`;
  the file notes that adding a 21st effect below will silently not be dispatched
  on the AI aftermath path until a row claims that GameController scope.
- Missing/known gaps: multi-side conflict resolution unimplemented except the
  synthetic probe id; `AGENDA_BANS_DIPLOMACY_AFTERMATH` only enforces the
  Deluxe Aftermath extension (core Diplomacy-CONFLICT ban remains unenforced).

---

## 3. ~30 card records (every type + differing printings)

Source files: `b5ccg/resources/cards/premiere.json` (446 cards) and
`b5ccg/resources/cards/deluxe.json` (383 cards). Card types present:
`AFTERMATH, AGENDA, CHARACTER, CONFLICT, ENHANCEMENT, EVENT, FLEET, GROUP, LOCATION`
(9 types — all covered below).

Each record is shown as compact JSON. For titles with differing text between
printings, both the Premiere (`set: PREMIERE`) and Deluxe (`set: DELUXE`) rows
are shown so the Deluxe delta is visible inline.

### 3a. One sample per type (from Premiere)

```json
{"id":"aftermath_approval_of_the_grey","title":"Approval of the Grey","type":"AFTERMATH","subtype":"AFTERMATH_WON","rarity":"FIXED","faction":"MINBARI","set":"PREMIERE","imageKey":"approval_of_the_grey","text":"Minbari only. Play after winning any conflict. Gain 1 Influence and heal one face-down character.","triggerCondition":"WON"}
{"id":"agenda_a_rising_power","title":"A Rising Power","type":"AGENDA","subtype":"AGENDA","rarity":"COMMON","faction":"ANY","set":"PREMIERE","imageKey":"a_rising_power","text":"Win Condition: Reach 20 Influence. Ongoing: Gain 1 additional Influence whenever you win a Diplomacy conflict.","isMajorAgenda":false,"winCondition":"INFLUENCE_20"}
{"id":"char_jeffrey_sinclair","title":"Jeffrey Sinclair","type":"CHARACTER","subtype":"CHARACTER_HUMAN","rarity":"FIXED","faction":"HUMAN","set":"PREMIERE","imageKey":"jeffrey_sinclair","text":"Human Ambassador. Jeffrey Sinclair begins the game in your Ambassador Zone. He may not be removed from play by card effects.","diplomacy":5,"intrigue":3,"psi":0,"leadership":4,"isAmbassador":true}
{"id":"conf_a_brighter_future","title":"A Brighter Future","type":"CONFLICT","subtype":"CONFLICT_DIPLOMACY","rarity":"RARE","faction":"ANY","set":"PREMIERE","imageKey":"a_brighter_future","text":"Diplomacy conflict. Winner gains 3 Influence. All supporters gain 1 Influence.","conflictType":"DIPLOMACY","influenceReward":3}
{"id":"enh_babylon5_unrest","title":"Babylon 5 Unrest","type":"ENHANCEMENT","subtype":"ENHANCEMENT_BABYLON5","rarity":"RARE","faction":"ANY","set":"PREMIERE","imageKey":"babylon5_unrest","text":"Ongoing. All players' Influence costs for playing cards increase by 1. Discard at end of round.","diplomacyBonus":0,"intrigueBonus":0,"psiBonus":0,"militaryBonus":0,"leadershipBonus":0,"cost":6}
{"id":"event_a_good_bluff","title":"A Good Bluff","type":"EVENT","subtype":"EVENT","rarity":"COMMON","faction":"ANY","set":"PREMIERE","imageKey":"a_good_bluff","text":"Play during any conflict. Add 3 to your conflict total. Discard after use."}
{"id":"fleet_colonial_centauri","title":"Colonial Fleet (Centauri)","type":"FLEET","subtype":"FLEET_CENTAURI","rarity":"FIXED","faction":"CENTAURI","set":"PREMIERE","imageKey":"colonial_fleet_centauri","text":"Basic Centauri colonial patrol force.","military":2,"cost":4,"fleetClass":"COLONIAL"}
{"id":"group_commercial_telepaths","title":"Commercial Telepaths","type":"GROUP","subtype":"GROUP","rarity":"UNCOMMON","faction":"ANY","set":"PREMIERE","imageKey":"commercial_telepaths","text":"Rotate this Group and choose a Character not already affected by this Group. For as long as this Group stays rotated, that Character adds half its Psi (rounded up) to its Diplomacy.","cost":6}
{"id":"loc_centauri_prime","title":"Centauri Prime","type":"LOCATION","subtype":"LOCATION_CENTAURI","rarity":"FIXED","faction":"CENTAURI","set":"PREMIERE","imageKey":"centauri_prime","text":"Centauri homeworld. Centauri only. Provides 2 Influence per round. Cannot be seized by Military conflict.","influencePerRound":2,"cost":10}
```

### 3b. Titles differing between printings (Premiere + Deluxe pairs)

```json
{"id":"char_delenn","title":"Delenn","type":"CHARACTER","subtype":"CHARACTER_MINBARI","rarity":"FIXED","faction":"MINBARI","set":"PREMIERE","imageKey":"delenn","text":"Minbari Ambassador. Delenn begins the game in your Ambassador Zone.","diplomacy":7,"intrigue":3,"psi":2,"leadership":5,"isAmbassador":true}
{"id":"de_char_delenn","title":"Delenn","type":"CHARACTER","subtype":"CHARACTER_MINBARI","rarity":"FIXED","faction":"MINBARI","set":"DELUXE","imageKey":"delenn","text":"Minbari Ambassador. Delenn begins the game in your Ambassador Zone. (Deluxe: now Fixed in boosters. May be replaced by Delenn Transformed via Chrysalis.)","diplomacy":7,"intrigue":3,"psi":2,"leadership":5,"isAmbassador":true}

{"id":"char_londo_mollari","title":"Londo Mollari","type":"CHARACTER","subtype":"CHARACTER_CENTAURI","rarity":"FIXED","faction":"CENTAURI","set":"PREMIERE","imageKey":"londo_mollari","text":"Centauri Ambassador. Londo Mollari begins the game in your Ambassador Zone.","diplomacy":5,"intrigue":6,"psi":0,"leadership":4,"isAmbassador":true}
{"id":"de_char_londo_mollari","title":"Londo Mollari","type":"CHARACTER","subtype":"CHARACTER_CENTAURI","rarity":"FIXED","faction":"CENTAURI","set":"DELUXE","imageKey":"londo_mollari","text":"Centauri Ambassador. Londo Mollari begins the game in your Ambassador Zone. (Deluxe: redistributed as Fixed in boosters, not starter-only.)","diplomacy":5,"intrigue":6,"psi":0,"leadership":4,"isAmbassador":true}

{"id":"event_news_of_galactic_import","title":"News of Galactic Import","type":"EVENT","subtype":"EVENT","rarity":"COMMON","faction":"ANY","set":"PREMIERE","imageKey":"news_of_galactic_import","text":"Play any time. All players draw 1 card."}
{"id":"de_event_news_of_galactic_import","title":"News of Galactic Import","type":"EVENT","subtype":"EVENT","rarity":"COMMON","faction":"ANY","set":"DELUXE","imageKey":"news_of_galactic_import","text":"Play any time. All players draw 1 card. (Deluxe text change: if you have the most characters in Inner Circle, draw 2 instead.)"}

{"id":"conf_complete_support","title":"Complete Support","type":"CONFLICT","subtype":"CONFLICT_DIPLOMACY","rarity":"UNCOMMON","faction":"ANY","set":"PREMIERE","imageKey":"complete_support","text":"Diplomacy conflict. Winner gains 2 Influence. All other players must either support or oppose.","conflictType":"DIPLOMACY","influenceReward":2,"participation":{"mustTakeSide":true}}
{"id":"de_conf_complete_support","title":"Complete Support","type":"CONFLICT","subtype":"CONFLICT_DIPLOMACY","rarity":"UNCOMMON","faction":"ANY","set":"DELUXE","imageKey":"complete_support","text":"Diplomacy conflict. Winner gains 2 Influence. All other players must either support or oppose. (Deluxe text change: players who neither support nor oppose lose 1 Influence.)","conflictType":"DIPLOMACY","influenceReward":2,"participation":{"mustTakeSide":true}}

{"id":"event_diplomatic_blunder","title":"Diplomatic Blunder","type":"EVENT","subtype":"EVENT","rarity":"COMMON","faction":"ANY","set":"PREMIERE","imageKey":"diplomatic_blunder","text":"Play after an opponent wins a Diplomacy conflict. That player loses 1 Influence."}
{"id":"de_event_diplomatic_blunder","title":"Diplomatic Blunder","type":"EVENT","subtype":"EVENT","rarity":"COMMON","faction":"ANY","set":"DELUXE","imageKey":"diplomatic_blunder","text":"Play after an opponent wins a Diplomacy conflict. That player loses 1 Influence. (Deluxe art/text change: may now be played after any conflict type.)"}

{"id":"aftermath_public_apology","title":"Public Apology","type":"AFTERMATH","subtype":"AFTERMATH_LOST_MILITARY","rarity":"RARE","faction":"ANY","set":"PREMIERE","imageKey":"public_apology","text":"Play on an opponent after they lose a Military conflict. That opponent loses 2 Influence.","triggerCondition":"LOST_MILITARY"}
{"id":"de_am_public_apology","title":"Public Apology","type":"AFTERMATH","subtype":"AFTERMATH_LOST_MILITARY","rarity":"RARE","faction":"ANY","set":"DELUXE","imageKey":"public_apology","text":"Play on an opponent after they lose a Military conflict. That opponent loses 2 Influence. (Deluxe text change: opponent also cannot initiate Military conflicts next round.)","triggerCondition":"LOST_MILITARY"}

{"id":"loc_quadrant_37","title":"Quadrant 37","type":"LOCATION","subtype":"LOCATION_NARN","rarity":"UNCOMMON","faction":"NARN","set":"PREMIERE","imageKey":"quadrant_37","text":"Narn outer territory. Provides 1 Influence per round.","influencePerRound":1,"cost":10}
{"id":"de_loc_quadrant_37","title":"Quadrant 37","type":"LOCATION","subtype":"LOCATION_NARN","rarity":"UNCOMMON","faction":"NARN","set":"DELUXE","imageKey":"quadrant_37","text":"Narn outer territory. Provides 1 Influence per round. (Deluxe text change: also gain 1 extra Influence when winning a Military conflict while controlling this.)","influencePerRound":1,"cost":10}

{"id":"enh_mass_drivers","title":"Mass Drivers","type":"ENHANCEMENT","subtype":"ENHANCEMENT_FLEET","rarity":"UNCOMMON","faction":"ANY","set":"PREMIERE","imageKey":"mass_drivers","text":"Attach to a Fleet. That fleet gains +3 Military. If you win a Military conflict with this fleet, the loser also loses 1 additional Influence.","diplomacyBonus":0,"intrigueBonus":0,"psiBonus":0,"militaryBonus":3,"leadershipBonus":0,"cost":5}
{"id":"de_enh_mass_drivers","title":"Mass Drivers","type":"ENHANCEMENT","subtype":"ENHANCEMENT_FLEET","rarity":"UNCOMMON","faction":"ANY","set":"DELUXE","imageKey":"mass_drivers","text":"Attach to a Fleet. That fleet gains +3 Military. If you win a Military conflict with this fleet, the loser also loses 1 additional Influence. (Deluxe text change: loser also loses a Location if they control one.)","diplomacyBonus":0,"intrigueBonus":0,"psiBonus":0,"militaryBonus":3,"leadershipBonus":0,"cost":5}

{"id":"event_trade_windfall","title":"Trade Windfall","type":"EVENT","subtype":"EVENT","rarity":"COMMON","faction":"ANY","set":"PREMIERE","imageKey":"trade_windfall","text":"Play any time. Gain 1 Influence for each Location card you have in play."}
{"id":"de_event_trade_windfall","title":"Trade Windfall","type":"EVENT","subtype":"EVENT","rarity":"COMMON","faction":"ANY","set":"DELUXE","imageKey":"trade_windfall","text":"Play any time. Gain 1 Influence for each Location card you have in play. (Deluxe text change: gain 2 Influence per Location if you control 3 or more.)"}

{"id":"aftermath_refugees","title":"Refugees","type":"AFTERMATH","subtype":"AFTERMATH_WON_MILITARY","rarity":"FIXED","faction":"ANY","set":"PREMIERE","imageKey":"refugees","text":"Play after winning a Military conflict. Gain 1 Influence for each Location card you have in play.","triggerCondition":"WON_MILITARY"}
{"id":"de_am_refugees","title":"Refugees","type":"AFTERMATH","subtype":"AFTERMATH_WON_MILITARY","rarity":"FIXED","faction":"ANY","set":"DELUXE","imageKey":"refugees","text":"Play after winning a Military conflict. Gain 1 Influence for each Location card you have in play. (Deluxe text change: minimum 1 Influence even with no Locations.)","triggerCondition":"WON_MILITARY"}

{"id":"char_mr_adams","title":"Mr. Adams","type":"CHARACTER","subtype":"CHARACTER_NEUTRAL","rarity":"UNCOMMON","faction":"NEUTRAL","set":"PREMIERE","imageKey":"mr_adams","text":"Neutral character. While in Inner Circle, opponents' Event cards targeting you cost 1 extra Influence.","diplomacy":2,"intrigue":3,"psi":0,"leadership":1,"isAmbassador":false,"cost":6}
{"id":"de_char_mr_adams","title":"Mr. Adams","type":"CHARACTER","subtype":"CHARACTER_NEUTRAL","rarity":"UNCOMMON","faction":"NEUTRAL","set":"DELUXE","imageKey":"mr_adams","text":"Neutral character. While in Inner Circle, opponents' Event cards targeting you cost 1 extra Influence. (Deluxe text change: now also includes Aftermath cards.)","diplomacy":2,"intrigue":3,"psi":0,"leadership":1,"isAmbassador":false,"cost":6}

{"id":"group_spin_doctors","title":"Spin Doctors","type":"GROUP","subtype":"GROUP","rarity":"RARE","faction":"ANY","set":"PREMIERE","imageKey":"spin_doctors","text":"Rotate this Group after any conflict. Change the perceived winner of a Diplomacy conflict for Influence gain purposes (you gain their Influence; they gain none).","cost":8}
{"id":"de_group_spin_doctors","title":"Spin Doctors","type":"GROUP","subtype":"GROUP","rarity":"RARE","faction":"ANY","set":"DELUXE","imageKey":"spin_doctors","text":"Rotate this Group after any conflict. Change the perceived winner of a Diplomacy conflict for Influence gain purposes.","cost":8}

{"id":"aftermath_martyr","title":"Martyr","type":"AFTERMATH","subtype":"AFTERMATH","rarity":"RARE","faction":"ANY","set":"PREMIERE","imageKey":"martyr","text":"Play after any conflict in which one of your characters was discarded. Gain 3 Influence.","triggerCondition":"ANY"}
{"id":"de_am_martyr","title":"Martyr","type":"AFTERMATH","subtype":"AFTERMATH","rarity":"RARE","faction":"ANY","set":"DELUXE","imageKey":"martyr","text":"Play after any conflict in which one of your characters was discarded. Gain 3 Influence. (Deluxe text change: gain 4 Influence if it was your ambassador.)","triggerCondition":"ANY"}

{"id":"fleet_drazi_sunhawk","title":"Drazi Sunhawk","type":"FLEET","subtype":"FLEET_NON_ALIGNED","rarity":"COMMON","faction":"NON_ALIGNED","set":"PREMIERE","imageKey":"drazi_sunhawk","text":"Drazi warships. May be played by any faction. While in play, this faction's Military conflicts gain +1.","military":3,"cost":4,"fleetClass":"DRAZI"}
{"id":"de_fleet_drazi_sunhawk","title":"Drazi Sunhawk","type":"FLEET","subtype":"FLEET_NON_ALIGNED","rarity":"COMMON","faction":"NON_ALIGNED","set":"DELUXE","imageKey":"drazi_sunhawk","text":"Drazi warships. May be played by any faction.","military":3,"cost":4,"fleetClass":"DRAZI"}

{"id":"enh_pulling_strings","title":"Pulling Strings","type":"ENHANCEMENT","subtype":"ENHANCEMENT_CHARACTER","rarity":"COMMON","faction":"ANY","set":"PREMIERE","imageKey":"pulling_strings","text":"Attach to a Character. That character gains +1 to all stats.","diplomacyBonus":1,"intrigueBonus":1,"psiBonus":1,"militaryBonus":0,"leadershipBonus":1,"cost":4}
{"id":"de_enh_pulling_strings","title":"Pulling Strings","type":"ENHANCEMENT","subtype":"ENHANCEMENT_CHARACTER","rarity":"COMMON","faction":"ANY","set":"DELUXE","imageKey":"pulling_strings","text":"Attach to a Character. That character gains +1 to all stats. (Deluxe text change: character also draws 1 card when this Enhancement first enters play.)","diplomacyBonus":1,"intrigueBonus":1,"psiBonus":1,"militaryBonus":0,"leadershipBonus":1,"cost":4}

{"id":"event_vorlon_rescue","title":"Vorlon Rescue","type":"EVENT","subtype":"EVENT","rarity":"RARE","faction":"ANY","set":"PREMIERE","imageKey":"vorlon_rescue","text":"Play when one of your Inner Circle characters would be discarded. That character is moved to Supporting Role instead of discarded."}
{"id":"de_event_vorlon_rescue","title":"Vorlon Rescue","type":"EVENT","subtype":"EVENT","rarity":"RARE","faction":"ANY","set":"DELUXE","imageKey":"vorlon_rescue","text":"Play when one of your Inner Circle characters would be discarded. That character is moved to Supporting Role instead of discarded. (Deluxe text change: character is also healed when moved.)"}

{"id":"event_prolonged_talks","title":"Prolonged Talks","type":"EVENT","subtype":"EVENT","rarity":"RARE","faction":"ANY","set":"PREMIERE","imageKey":"prolonged_talks","text":"Play before a Diplomacy conflict. That conflict's Influence reward is doubled. All participants must commit their ambassador."}
{"id":"de_event_prolonged_talks","title":"Prolonged Talks","type":"EVENT","subtype":"EVENT","rarity":"RARE","faction":"ANY","set":"DELUXE","imageKey":"prolonged_talks","text":"Play before a Diplomacy conflict. That conflict's Influence reward is doubled. All participants must commit their ambassador. (Deluxe text change: both participants draw 1 card after resolution.)"}

{"id":"char_gkar","title":"G'Kar","type":"CHARACTER","subtype":"CHARACTER_NARN","rarity":"FIXED","faction":"NARN","set":"PREMIERE","imageKey":"gkar","text":"Narn Ambassador. G'Kar begins the game in your Ambassador Zone.","diplomacy":4,"intrigue":5,"psi":0,"leadership":3,"isAmbassador":true}
{"id":"de_char_gkar","title":"G'Kar","type":"CHARACTER","subtype":"CHARACTER_NARN","rarity":"FIXED","faction":"NARN","set":"DELUXE","imageKey":"gkar","text":"Narn Ambassador. G'Kar begins the game in your Ambassador Zone. (Deluxe: redistributed as Fixed in boosters. New text: once per round, G'Kar may spend 1 Influence to add 2 to any Narn conflict total.)","diplomacy":4,"intrigue":5,"psi":0,"leadership":3,"isAmbassador":true}

{"id":"agenda_higher_calling","title":"Higher Calling","type":"AGENDA","subtype":"AGENDA_MAJOR","rarity":"COMMON","faction":"ANY","set":"PREMIERE","imageKey":"higher_calling","text":"Major Agenda. Win Condition: Reach 20 Influence. Ongoing: Gain 1 Influence at the start of each round.","isMajorAgenda":true,"winCondition":"INFLUENCE_20"}
{"id":"de_agenda_higher_calling","title":"Higher Calling","type":"AGENDA","subtype":"AGENDA_MAJOR","rarity":"COMMON","faction":"ANY","set":"DELUXE","imageKey":"higher_calling","text":"Major Agenda. Win Condition: Reach 20 Influence. Ongoing: Gain 1 Influence at the start of each round. (Deluxe major change: now explicitly labelled Major Agenda.)","isMajorAgenda":true,"winCondition":"INFLUENCE_20"}
```

**Differing-printing pattern note:** 154 of the 383 Deluxe titles share a
Premiere title with different text. The delta is almost always appended as a
literal `"(Deluxe text change: …)"` suffix in the `text` field — i.e. the
canonical-text rule (D5, Deluxe governs dual titles) is encoded as a visible
suffix rather than a separate field, which is exactly the ambiguity the
effect-system work must resolve.

---

## 4. Boot / build / ledger

### 4a. `.agent/00_BOOT.md` (verbatim)

```markdown
---
document:
  title: "Cold-boot sequence for b5ccg agents (lean)"
  status: "Governance"
provenance:
  author_llm: {name: "Muse Spark", version: "muse-spark-1.3-contributor-free"}
  created_date: "2026-10-16"
  last_modified_date: "2026-10-16"
---

# 00_BOOT — read this first, every session

1. Read `AGENTS.md`, `docs/DECISIONS.md`, `.agent/TASK_LEDGER.md`. List
   `.agent/CLAIMS/*.json` + `.agent/HEARTBEATS/*.json`. A task with a live
   claim (age < 30 min) is taken.
2. Run `javac -version` (expect JDK 8 today; JDK 25 hops are tracked work,
   not today's compiler) and record it in your heartbeat. Build gate is
   `b5ccg/compile.bat` (or `compile.sh`).
3. Write your heartbeat with the shipped writer, never by hand:

   powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/new-heartbeat.ps1 -AgentId "<agent-id>" -State active -LiveClaims B5-NNNN -Javac 1.8.0_292 -Notes "<prose>"

   Heartbeat schema: strict JSON with `schema_version`, `agent_id`, `utc`,
   `state`, `live_claims` (`[]` asserts you hold nothing). `agent_id` names
   your session (`client (model)` + discriminator), never reused from another
   agent. Never edit another agent's heartbeat. See
   `.agent/HEARTBEATS/README.md`.
4. OPEN-task census ONLY via the shared tool:

   powershell -NoProfile -ExecutionPolicy Bypass -File .agent/run-queue.ps1 -DryRun

   Do NOT hand-grep the ledger for OPEN rows. Claims-first: a row under a
   live claim reads `suppressed-live-claim` — re-census after release.
5. Claim atomically (never overwrite an existing claim file; non-zero exit
   means no claim — stop that item):

   powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/new-claim.ps1 -TaskId <ID> -AgentId "<agent-id>" -Scope "<scope>" -Javac 1.8.0_292

   Re-read the row first: it must still read `OPEN`, or release the claim.
6. Work ONLY inside the claimed scope. Small diffs. Stdlib only.
7. Verify: `compile.bat/sh` green. On red, mark `BLOCKED` with the log
   excerpt and release your claim.
8. Finish: update the ledger row, append a `docs/DECISIONS.md` entry if the
   change interprets rules or governance, write
   `.agent/REPORTS/<date>-<agent-id>-<task>.md`, delete your claim file,
   refresh your heartbeat. Append rows only with
   `.agent/tools/append-ledger.ps1`, then run
   `.agent/tools/run-dup-census.ps1` and
   `.agent/tools/check-citations.ps1` (exit codes are the contract).
9. Staleness: claims older than 30 min are stale ONLY after noting the
   reaping in the ledger. Never touch live claims or heartbeats.
```

### 4b. `b5ccg/compile.bat` (verbatim, 157 lines)

```bat
@echo off
REM Compile the B5 CCG Java project (JDK 25 --release 8, stdlib only; Hop 1 per B5-0007/D2)
setlocal enabledelayedexpansion

REM Resolve the script directory so it runs from any CWD and always targets
REM the b5ccg/ tree below (see compile.sh, B5-0306). %~dp0 = b5ccg/.
cd /d "%~dp0" || goto :eof

REM Relative roots so the accumulated file list stays well under the
REM cmd.exe 8,191-char command-line ceiling. The previous version built a
REM single variable of QUOTED ABSOLUTE paths (~12,966 chars on this tree).
REM The command line silently truncated the list and javac received a
REM mangled/empty list, so compile.bat printed "Build successful" while
REM producing ZERO class files. 146 files x ~30 chars (relative) =~4,400
REM chars fits with margin.
set SRC=src
set OUT=out
set RES=resources

echo === B5 CCG Build ===

REM B5-3487: clean out/ BEFORE compiling. javac writes a .class for everything
REM that compiled before it hits the first error batch, so a red build leaves a
REM PARTIAL out/ behind -- 77 class files for 146 sources, with b5ccg/engine/
REM DeckLoader among the missing. run.bat then guards on "does out/ exist", sees
REM it exists, skips the build and runs the partial one, dying at runtime with
REM NoClassDefFoundError instead of reporting the compile error that caused it.
REM The stale-output state is indistinguishable from a good build by existence
REM alone; it is only distinguishable by never having been allowed to persist.
if exist "%OUT%" rd /s /q "%OUT%"
if exist "%OUT%" (
  echo ERROR: could not remove %OUT% - it is probably locked by a running game.
  exit /b 1
)
mkdir "%OUT%"

REM Count the .java files first (a separate pass keeps the counter out of
REM the redirected block that writes the response file).
set N=0
for /r "%SRC%" %%f in (*.java) do set /a N+=1
if %N% equ 0 (
  echo ERROR: No .java files found.
  exit /b 1
)

REM Collect all .java files (relative paths, one per line) into a response
REM file so the list can exceed the command-line ceiling without trimming
REM javac's argument list.
set LIST=..\b5ccg_compile_list.tmp
REM B5-0001: quote each path AND slash the backslashes -- %%~f expands to a
REM FULL path, and javac @-files treat backslash as an escape (measured: the
REM quoted attempt still failed as "file not found: C:UsersTiwa Pene...").
REM Forward slashes are accepted by Windows javac and need no escaping.
> "%LIST%" (
  for /r "%SRC%" %%f in (*.java) do (
    set "P=%%~f"
    setlocal enabledelayedexpansion
    echo "!P:\=/!"
    endlocal
  )
)

if not exist "%LIST%" (
  echo ERROR: No .java files found.
  exit /b 1
)

echo Compiling source files...
REM B5-3487: -Xmaxerrs 2000 so a failure reports every error rather than the
REM first 100. javac's DEFAULT cap silently truncated the tree's 248 errors to
REM "100 errors in 13 files", which is how B5-3428 recorded two runs as gate-
REM consistent when both were equally truncated: the two builds agreed because
REM they both stopped at the same cap, not because they saw the same tree.
REM A capped count is a floor, never a total -- read the exit code, and treat any
REM error count printed without -Xmaxerrs as a lower bound.
javac --release 8 -encoding UTF-8 -Xmaxerrs 2000 -d "%OUT%" @"%LIST%"
if errorlevel 1 (
  del /f /q "%LIST%" 2>nul
  REM Leave no half-written output behind for run.bat to mistake for a build.
  rd /s /q "%OUT%" 2>nul
  echo Compilation failed. out\ removed so no partial build can be run.
  exit /b 1
)

echo Copying resources...
REM B5-3641: the resource copy is now a GATED step, not the fire-and-forget it
REM was at the former line 77. B5-3631 measured three silent-green shapes on that
REM bare line, each of which still printed "Build successful" and still wrote the
REM marker below:
REM   (a) empty resources   -- xcopy exits 0 having copied 0 files;
REM   (b) missing resources -- the old `if exist "%RES%"` was false so the copy
REM                            was simply SKIPPED, still exit 0;
REM   (c) ACL hard failure  -- xcopy exits 4 while the marker was STILL written.
REM run.bat consumes only b5ccg-build-ok.txt as the evidence that out/ is
REM complete, so a marker over an incomplete resource set is exactly the
REM silent-green B5-1725 recorded. errorlevel alone cannot prove completion
REM (case (a) copies nothing yet exits 0), so each of the three known resources
REM is checked by existence AFTER the copy. Every failure path mirrors the
REM javac-failure branch above -- rd /s /q out/, exit /b 1 -- so no marker-stamped
REM partial out/ can survive for run.bat to mistake for a good build.
if not exist "%RES%\" (
  echo ERROR: resources directory "%RES%" not found - refusing to write the build marker.
  rd /s /q "%OUT%" 2>nul
  exit /b 1
)
xcopy /E /Y /Q "%RES%\*" "%OUT%\"
if errorlevel 1 (
  echo ERROR: resource copy failed - xcopy exit code 1 or higher. out\ removed so no partial build can be run.
  rd /s /q "%OUT%" 2>nul
  exit /b 1
)
if not exist "%OUT%\cards\deluxe.json" (
  echo ERROR: cards\deluxe.json did not land in out\ - xcopy reported success but copied nothing. out\ removed so no partial build can be run.
  rd /s /q "%OUT%" 2>nul
  exit /b 1
)
if not exist "%OUT%\cards\premiere.json" (
  echo ERROR: cards\premiere.json did not land in out\ - xcopy reported success but copied nothing. out\ removed so no partial build can be run.
  rd /s /q "%OUT%" 2>nul
  exit /b 1
)
if not exist "%OUT%\cards\index.json" (
  echo ERROR: cards\index.json did not land in out\ - xcopy reported success but copied nothing. out\ removed so no partial build can be run.
  rd /s /q "%OUT%" 2>nul
  exit /b 1
)
if not exist "%OUT%\decks\premiere-starter-decks.json" (
  echo ERROR: decks\premiere-starter-decks.json did not land in out\ - xcopy reported success but copied nothing. out\ removed so no partial build can be run.
  rd /s /q "%OUT%" 2>nul
  exit /b 1
)

REM B5-3487: written LAST, after javac succeeded and resources landed. run.bat
REM treats this marker as the only trustworthy evidence that out/ is complete,
REM which the mere existence of out/ is not. B5-3641 added the gates above so
REM that "resources landed" is now PROVEN rather than assumed.
REM NOTE the explicit backslash after %OUT%: it is defined as `out` with NO
REM trailing separator, so `%OUT%marker` would concatenate into a sibling FILE
REM named `outmarker` rather than a file inside out/. Measured: every marker
REM probe wrote beside the directory, never into it, and out/ stayed unmarked.
echo ok> "%OUT%\b5ccg-build-ok.txt"

echo.
echo Build successful. Run with: run.bat

REM ── Verification gate: parity with compile.sh (B5-0967, 2026-09-28) ──────
REM This script has NO native test branch BY DECISION: the test selection
REM lives only in compile.sh's RUN_TESTS=1 branch, so the gate list is
REM decided in exactly one file (B5-0956 owns which Headless classes gate).
REM The full verified build on this platform (every agent builds through
REM Git Bash) is:
REM   RUN_TESTS=1 sh compile.sh
REM (cmd.exe without a POSIX shell: `set RUN_TESTS=1` then `sh compile.sh`
REM  via Git Bash; a plain `compile.bat` remains the fast compile-only gate.)
REM Measured 2026-09-28: RUN_TESTS=1 sh compile.sh exits 0 end-to-end here.

endlocal
```

### 4c. `TASK_LEDGER.md` (first & last 40 rows — whole file is 27 lines / 22 data rows)

```markdown
# TASK_LEDGER — b5ccg (fresh at fork, 2026-10-16)

Planner-authored backlog seeded from Claude v1 §13 (WP numbers in Task text).
IDs are B5-nnnn (fresh sequence from B5-0001) because the queue, claim, and
census tools key on the B5-[0-9]{4} pattern. Keep ~5–10 OPEN rows.
Acceptance lines are binding.

| ID | Status | Task | Scope | Claim | Report |
|---|---|---|---|---|---|
| B5-0001 | DONE | WP-00 Snapshot, secret scan, restore drill (B0). Acceptance: untracked .java = 0, restore drill builds + gates green, stale-snapshot alert exists | tooling | - | .agent/REPORTS/2026-10-16-opencode (muse-spark-1.3-free) 01-B5-0001.md |
| B5-0002 | DONE | WP-01 Pin rulebook, rule-set profile stub (A9). Acceptance: hash recorded, 689-vs-1236 reconciled in writing, profile recorded in replays | engine | - | .agent/REPORTS/2026-10-16-opencode (muse-spark-1.3-free) 01-B5-0002.md |
| B5-0003 | DONE | WP-02 Canonical-key function + parity probe (A1a). Acceptance: probe exits 0, every CardEffects literal canonical, existing probes unchanged | engine | - | .agent/REPORTS/2026-10-16-opencode (muse-spark-1.3-free) 02-B5-0003.md |
| B5-0004 | OPEN | WP-03 Printing-diff triage of 82 special-cased titles (A1a). Acceptance: all 58 single-literal titles resolved + scenario test passing under both ids | engine | - | - |
| B5-0005 | DONE | WP-04 Effect census (A2). Acceptance: checker exits 0, 446 titles each classified exactly once, owner reviews class table | model | - | .agent/REPORTS/2026-10-16-opencode (muse-spark-1.3-free) 04-B5-0005.md |
|| B5-0006 | DONE | WP-05 Toolchain measurement T1.0. Acceptance: jdeps + throwaway JDK25 --release 8 warning/internals report delivered | tooling | - | .agent/REPORTS/2026-10-16-solar-mini4-B5-0006.md |
| B5-0007 | OPEN | WP-06 Toolchain hops + policy T1.1-T1.3 (needs D2 + DECISIONS entry). Acceptance: all gates green under JDK 25, old pin retired, census sentinel cycle run | tooling | nemotron-3.5-super-vl | .agent/REPORTS/2026-10-16-opencode (muse-spark-1.3-free) 06-B5-0007.md |
| B5-0008 | OPEN | WP-07 Seeded RNG, state hash, double-run probe (A5). Acceptance: identical hashes across runs, human-seat probe deterministic in gate | engine | - | - |

Future (not yet OPEN — promote after Tier-0): WP-08 BLOCKED triage n/a (no
legacy BLOCKED rows carried over), WP-09 process-tax review, WP-10..WP-12
effect schema + pilots, WP-13..WP-14 fail-loud instruments, WP-15..WP-17
engine decomposition, WP-18 traceability, WP-19 Faction-to-Race rename (needs
D1), WP-20 hygiene batch.
| B5-0009 | DONE | B0 snapshot job build only - script plus Task Scheduler definition committing working tree to snapshot branch, scheduler left disabled for owner review | tooling | - | .agent/REPORTS/2026-10-16-opencode (muse-spark-1.3-free) 03-B5-0009.md |

| B5-0010 | DONE | WP-19 Faction-to-Race rename and Loyalty split (A7.0). D1 decided single-human. Acceptance: all gates green, zero behaviour change. Claim only in an engine freeze window, never alongside live engine work | engine | - | .agent/REPORTS/2026-10-16-opencode (muse-spark-1.3-free) 05-B5-0010.md |
```

**Open tasks relevant to effect work:** `B5-0004` (printing-diff triage of 82
special-cased titles — directly feeds the Deluxe-delta problem above),
`B5-0007` (toolchain hops), `B5-0008` (seeded RNG / state hash). Note `WP-10..WP-12`
"effect schema + pilots" sit in the Future (not-yet-OPEN) block — that is the
effect-system work the census sizes.

---

## 5. `docs/DECISIONS.md` — headings

Source: `docs/DECISIONS.md` (10 `##` headings):

1. `## 2026-10-16 — Fork genesis (owner-directed)`
2. `## 2026-10-16 — B5-0001 DONE (WP-00/B0)`
3. `## 2026-10-16 — B5-0002 DONE (WP-01/A9)`
4. `## 2026-10-16 — Owner rulings D1 + D5 (recorded from owner answers)`
5. `## 2026-10-16 — B5-0010 DONE (WP-19/A7.0 Faction→Race rename + Loyalty split)`
6. `## Pending owner decisions (Claude §1.5)`
7. `## 2026-10-16 — B5-0009 DONE (B0 snapshot job, build only)`
8. `## 2026-10-16 — B5-0003 DONE (WP-02/A1a canonical key + parity probe)`
9. `## 2026-10-16 — Owner rulings D2 + D3 + D4 (full approval)`
10. `## 2026-10-16 — B5-0007 DONE (WP-06 toolchain hops + policy T1.1-T1.3)`

**Most relevant to effect work:**
- **D5** ("Deluxe governs dual titles") — the principle behind the
  `"(Deluxe text change: …)"` suffix pattern in §3b, and the census method
  note "Deluxe governs dual titles, D5".
- **D2** ("full approval") — the toolchain-policy approval that gates the
  `--release 8` / JDK 25 hop referenced throughout `compile.bat`.
- **D1** — single-human Faction-to-Race rename decision (WP-19, already DONE).
