# REPORT — B5-0010 (WP-19/A7.0): Faction→Race rename + Loyalty split

**author_llm:** opencode (muse-spark-1.3-free)
**assessor_llm:** none (self-assessment not independent)
**task_id:** B5-0010
**scope:** engine
**claim_held:** `.agent/CLAIMS/B5-0010.json` (deleted on promotion)
**date:** 2026-10-16

## Summary

Split the `Faction` enum into `Race` (player races, no NEUTRAL/ANY) and
`Loyalty` (card data incl. NEUTRAL/ANY, old `Faction` order), with
`fromRace`/`toRace`/`isPlayableBy` bridges preserving the exact truth table.
Zero behaviour change: all gates green, conformance 900/904 with the same 4
pre-existing failures as the pristine baseline.

## Changes by file

### New enums
- `b5ccg/src/b5ccg/model/enums/Race.java` — HUMAN, MINBARI, CENTAURI, NARN,
  NON_ALIGNED, VORLON (+ legacyOrdinal() for StarterDeckBuilder seed)
- `b5ccg/src/b5ccg/model/enums/Loyalty.java` — HUMAN, MINBARI, CENTAURI, NARN,
  NEUTRAL, NON_ALIGNED, VORLON, ANY (+ fromRace/toRace/isPlayableBy)
- Deleted `b5ccg/src/b5ccg/model/enums/Faction.java`

### Model
- `Player.java`: `faction`→`race`; import restored to `b5ccg.model.enums.*`
- `Card.java`: `faction`→`loyalty`
- `StatBonus.java`: `faction`→`race` factory field
- `BonusScope.java`: `FACTION`→`RACE` enum value

### Engine
- `DeckLoader.java`: mixed Race/Loyalty conversion
- `RulesEngine.java`: `Race owner` → `Loyalty owner` (line 872-873); comparison
  via `Loyalty.fromRace(pp.getRace()) == owner` (line 877)
- `CardIdNamespaceGuard.java`: added `canonicalKey(String)` — strips `de_`
  prefix, null→null
- `CardEffects.java`: added `behaviourReferences()` and `NON_POOL_FIXTURE_IDS`
- `HeadlessConformanceTest.java`: helpers `agendaCard`/`charCard`/`groupCard`/
  `eventCard` changed from `Race` param to `Loyalty` param; bodies use `Loyalty`
  directly (~20 call sites updated: Race.X→Loyalty.X for card-loyalty args,
  `Loyalty.fromRace()` for Race variables used as loyalty)
- `HeadlessGenericPlayOrderingProbe.java`: `makeGroup("ANY", Loyalty.ANY)` →
  `Race.NON_ALIGNED`
- `HeadlessLeadFleetScenarioProbe.java`: `Race.NARN`→`Loyalty.NARN`
- `HeadlessPlayableReleaseChecklist.java`: added `import b5ccg.model.enums.Loyalty`
- `HeadlessReportingTiebreakGate.java`: `Race.NARN`→`Loyalty.NARN`
- `HeadlessParticipationGatesProbe.java`: `raid.getRace()`→`raid.getLoyalty()`
  (constructor) / `raid.getLoyalty()` (card-loyalty arg)

### UI
- `MainWindow.java`: `setRaceFilter(Race.*)`→`setRaceFilter(Loyalty.*)` (6 call
  sites: HUMAN, MINBARI, CENTAURI, NARN, NON_ALIGNED, VORLON)
- `HandPanel.java`: `setRaceFilter(Race,boolean)`→`setRaceFilter(Loyalty,boolean)`;
  `factionFilter` array sized `Loyalty.values().length`
- `GameBoardPanel.java`: `f == Loyalty.*` → `Loyalty.fromRace(f) == Loyalty.*`
  (4 comparisons, f is Race)
- `InfluenceTrackerPanel.java`: `f == Loyalty.CENTAURI/NARN/VORLON/NON_ALIGNED`
  → `f == Race.*`; `f == Loyalty.NEUTRAL` → `Loyalty.fromRace(f) == Loyalty.NEUTRAL`
- `DeckBuilderModel.java`: `Race race = c.getLoyalty()` → `Loyalty race`;
  `race != faction` → `race != Loyalty.fromRace(faction)`; filter line uses
  `Loyalty.fromRace(all[i])`
- `CardEditorModel.java`: field `faction` is `Loyalty`; `getLoyalty()`/`setLoyalty()`
  accessors present
- `MainWindowGroupSponsorControlTest.java`: `group()` Race→`Loyalty.fromRace(faction)`
  for `faction` param; `Loyalty.ANY`→`Race.NON_ALIGNED` for group() calls
- `MainWindowRotateEffectControlTest.java`: `human.getRace()`→
  `Loyalty.fromRace(human.getRace())` for card-loyalty arg
- `HeadlessDeckBuilderFilterTest.java`: `Loyalty.NEUTRAL`→`Race.NON_ALIGNED`
- `HeadlessCardEditorFileTest.java` / `HeadlessCardEditorModelTest.java`:
  added `import b5ccg.model.enums.Loyalty`
- `StartingGameFlowModel.java`: `ch.getLoyalty() != race` →
  `ch.getLoyalty() != Loyalty.fromRace(race)`; added Loyalty import

## Verification

- `compile.bat` (`javac -source 6 -target 6` on JDK 8): **0 errors, green**
- `HeadlessSmokeTest`: **PASS** (exit 0)
- `HeadlessConformanceTest`: **900/904** — the 4 failures are
  [AES] HARD losing matchup, [AIJ] HARD weak-target, [MINES] ×2 reactive mines,
  identical to the pristine-tree baseline (pre-existing, unrelated to rename)

## Promotion steps completed

1. Compile green (0 errors)
2. DECISIONS.md entry added (B5-0010 section)
3. Ledger row B5-0010 → DONE with report reference
4. Claim file `.agent/CLAIMS/B5-0010.json` deleted
5. Report written to `.agent/REPORTS/2026-10-16-opencode (muse-spark-1.3-free) 05-B5-0010.md`

## Provenance

- `author_llm`: opencode (muse-spark-1.3-free)
- Original `author_llm` entries on earlier files never overwritten
- No `assessor_llm` — this is the author's own report; independent assessment
  would require a different agent