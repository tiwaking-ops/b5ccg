package b5ccg.engine;

import b5ccg.model.CivilWarState;
import b5ccg.model.Player;
import b5ccg.model.enums.Race;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B5-2563 — CIVIL WAR STATE MACHINE conformance probe (standalone CLI; exit 0
 * on all-pass, 1 on any failure). Wired conceptually alongside the other
 * engine Headless probes; this file is engine/ only and touches no game logic.
 *
 * <p>Verifies that {@link CivilWarState} (B5-0691; rulebook :990–:1009)
 * transitions correctly across a simulated dual-race entry scenario. The class
 * tracks civil war phases for ONE race; its players list is injected, never
 * derived (class doc), so the probe builds a dual race — two Players of the
 * same faction — and drives the machine through its public methods directly.
 * Each assertion names the rulebook line it enforces.
 *
 * <p>Java 6 only: no lambdas, streams, diamonds, try-with-resources.
 *
 * <p>Run after compile.bat / compile.sh:
 * {@code java -cp b5ccg/out b5ccg.engine.HeadlessCivilWarProbe}
 */
public class HeadlessCivilWarProbe {

    private static int failures = 0;

    public static void main(String[] args) {
        scenarioFreshState();
        scenarioEntryGuard();
        scenarioEntryByDeclaration();
        scenarioUnrestBumpOnEntry();
        scenarioSnapshotAndSplitTensionDivergence();
        scenarioExitByWarEndMerge();
        scenarioExitBySurrender();
        scenarioWarRelation();
        scenarioReentryRefused();
        scenarioNonDualRaceRefused();

        System.out.println();
        if (failures == 0) {
            System.out.println("B5-2563 PROBE PASSED (all CIVIL WAR STATE MACHINE scenarios)");
        } else {
            System.out.println("B5-2563 PROBE FAILED (" + failures + " failure(s))");
        }
        System.exit(failures == 0 ? 0 : 1);
    }

    // ── scenarios ───────────────────────────────────────────────────────────

    /** :990 / :956 — a freshly built machine is UNIFIED, has no entry round,
     *  and reports dual only when two factions of the race were injected. */
    private static void scenarioFreshState() {
        List<Player> race = dualRacePlayers(Race.CENTAURI);
        CivilWarState cw = new CivilWarState(Race.CENTAURI, race);
        check(":990 fresh machine starts UNIFIED",
              cw.getPhase() == CivilWarState.Phase.UNIFIED);
        check(":992 fresh machine has no entry round",
              cw.getEnteredRound() == -1);
        check(":956 dual race reports dual",
              cw.isDualRace());
        check("race is CENTAURI", cw.getRace() == Race.CENTAURI);
        check("two race players were injected",
              cw.getRacePlayers().size() == 2);
    }

    /** :992 — entry is legal only from UNIFIED, only for a dual race, and only
     *  when same-race tension 5 is present; a missing precondition fails
     *  softly (returns false) rather than throwing. */
    private static void scenarioEntryGuard() {
        List<Player> race = dualRacePlayers(Race.CENTAURI);
        CivilWarState cw = new CivilWarState(Race.CENTAURI, race);
        check(":992 entry refused without same-race tension 5",
              !cw.enterCivilWar(3, false));
        check(":992 phase still UNIFIED after the refused entry",
              cw.getPhase() == CivilWarState.Phase.UNIFIED);
        check(":992 entry fires at same-race tension 5",
              cw.enterCivilWar(3, true));
        check(":992 phase is now CIVIL_WAR",
              cw.getPhase() == CivilWarState.Phase.CIVIL_WAR);
        check(":992 entry round recorded",
              cw.getEnteredRound() == 3);
    }

    /** :992 second sentence — a war declaration triggers entry without the
     *  tension-5 precondition (duality still required). */
    private static void scenarioEntryByDeclaration() {
        List<Player> race = dualRacePlayers(Race.NARN);
        CivilWarState cw = new CivilWarState(Race.NARN, race);
        check(":992 declaration entry fires",
              cw.enterByDeclaration(11));
        check(":992 declaration sets phase + round",
              cw.getPhase() == CivilWarState.Phase.CIVIL_WAR
              && cw.getEnteredRound() == 11);
    }

    /** :994 — every faction of the race gets +1 unrest on entry (the caller's
     *  responsibility once the machine is in CIVIL_WAR; the bump is clamped
     *  at 5). The machine's own raiseUnrestOfRace performs it. */
    private static void scenarioUnrestBumpOnEntry() {
        List<Player> race = dualRacePlayers(Race.CENTAURI);
        CivilWarState cw = new CivilWarState(Race.CENTAURI, race);
        cw.enterCivilWar(1, true);
        int beforeA = race.get(0).getUnrest();
        int beforeB = race.get(1).getUnrest();
        cw.raiseUnrestOfRace();
        check(":994 unrest +1 for every faction of the race on entry",
              race.get(0).getUnrest() == beforeA + 1
              && race.get(1).getUnrest() == beforeB + 1);
        // Clamp at the rulebook 1..5 band (:280).
        race.get(0).raiseUnrest(9);   // already 2 -> clamped 5
        int at5 = race.get(0).getUnrest();
        cw.raiseUnrestOfRace();
        check(":280 unrest clamps at 5 on the entry bump",
              race.get(0).getUnrest() == at5 && at5 == 5);
    }

    /** :1000 — at entry each faction snapshots the race-level tension row;
     *  during the war the factions diverge per target race; the clamps 0..5. */
    private static void scenarioSnapshotAndSplitTensionDivergence() {
        List<Player> race = dualRacePlayers(Race.CENTAURI);
        CivilWarState cw = new CivilWarState(Race.CENTAURI, race);
        cw.enterCivilWar(1, true);

        Map<Race, Integer> raceLevel = new LinkedHashMap<Race, Integer>();
        raceLevel.put(Race.NARN, 3);
        raceLevel.put(Race.MINBARI, 1);
        cw.snapshotSplitTensions(raceLevel);

        check(":1000 each faction snapshot the full race-level row",
              cw.getSplitTensions(race.get(0)).size() == 2
              && cw.getSplitTensions(race.get(1)).size() == 2);

        // Diverge; setSplitTension clamps 0..5.
        cw.setSplitTension(race.get(0), Race.NARN, 5);
        cw.setSplitTension(race.get(0), Race.MINBARI, -2);   // clamp -> 0
        check(":1000 divergence is per-faction, per-target",
              cw.getSplitTensions(race.get(0)).get(Race.NARN).intValue() == 5);
        check(":1000 split tension clamps at 0 (:280 band)",
              cw.getSplitTensions(race.get(0)).get(Race.MINBARI).intValue() == 0);
        // A target race absent from the snapshot is not a key to write.
        check(":1000 only snapshot targets are writable",
              cw.getSplitTensions(race.get(0)).get(Race.HUMAN) == null);
    }

    /** :998 / :1000 — exit by war end merges the split rows as the ROUNDED-UP
     *  AVERAGE, drops unrest -1 for every faction, and returns to UNIFIED. */
    private static void scenarioExitByWarEndMerge() {
        List<Player> race = dualRacePlayers(Race.CENTAURI);
        CivilWarState cw = new CivilWarState(Race.CENTAURI, race);
        cw.enterCivilWar(4, true);
        cw.raiseUnrestOfRace();   // 1 -> 2 for both

        Map<Race, Integer> raceLevel = new LinkedHashMap<Race, Integer>();
        raceLevel.put(Race.NARN, 3);
        raceLevel.put(Race.MINBARI, 1);
        cw.snapshotSplitTensions(raceLevel);
        // CA->NARN 2, CB->NARN 5 -> (2+5+1)/2 = 4 ; MINBARI stays 1,1 -> 1.
        cw.setSplitTension(race.get(0), Race.NARN, 2);
        cw.setSplitTension(race.get(1), Race.NARN, 5);

        int unrestBeforeA = race.get(0).getUnrest();
        int unrestBeforeB = race.get(1).getUnrest();

        Map<Race, Integer> merged = cw.exitByWarEnd();
        check(":1000 merge is the rounded-up average (2 and 5 -> 4)",
              merged.get(Race.NARN) != null
              && merged.get(Race.NARN).intValue() == 4);
        check(":1000 unaffected target averages unchanged (1 and 1 -> 1)",
              merged.get(Race.MINBARI) != null
              && merged.get(Race.MINBARI).intValue() == 1);
        check(":998 unrest -1 for every faction of the race on exit",
              race.get(0).getUnrest() == unrestBeforeA - 1
              && race.get(1).getUnrest() == unrestBeforeB - 1);
        check(":990 race left the Civil War state",
              cw.getPhase() == CivilWarState.Phase.UNIFIED
              && cw.getEnteredRound() == -1);

        // Exit precondition: from UNIFIED the merge returns nothing.
        check(":998 exit by war end refused when not at war",
              cw.exitByWarEnd().isEmpty());
    }

    /** :1006 — exit by unconditional surrender of all but one faction: the
     *  losers drop unrest -1, NO merge happens, and the survivor's own split
     *  row is returned. */
    private static void scenarioExitBySurrender() {
        List<Player> race = dualRacePlayers(Race.CENTAURI);
        CivilWarState cw = new CivilWarState(Race.CENTAURI, race);
        cw.enterByDeclaration(9);
        cw.raiseUnrestOfRace();   // both -> 2

        Map<Race, Integer> raceLevel = new LinkedHashMap<Race, Integer>();
        raceLevel.put(Race.NARN, 2);
        cw.snapshotSplitTensions(raceLevel);
        cw.setSplitTension(race.get(0), Race.NARN, 3);   // survivor diverges

        int survivorUnrest = race.get(0).getUnrest();
        int loserUnrest = race.get(1).getUnrest();

        Map<Race, Integer> row = cw.exitBySurrender(race.get(0));
        check(":1006 survivor's own split row returned (no merge)",
              row.get(Race.NARN) != null
              && row.get(Race.NARN).intValue() == 3);
        check(":1006 loser unrest -1 on surrender",
              race.get(1).getUnrest() == loserUnrest - 1);
        check(":1006 survivor unrest untouched",
              race.get(0).getUnrest() == survivorUnrest);
        check(":990 race left the Civil War state",
              cw.getPhase() == CivilWarState.Phase.UNIFIED);

        // Surrender precondition: a survivor not in the race returns empty and
        // leaves the machine in CIVIL_WAR.
        cw.enterByDeclaration(9);
        Player outsider = new Player("Outsider", Race.MINBARI, false);
        check(":1006 surrender refused for a non-race survivor",
              cw.exitBySurrender(outsider).isEmpty());
        check(":1006 refused surrender leaves the machine at war",
              cw.getPhase() == CivilWarState.Phase.CIVIL_WAR);
    }

    /** :994 — every faction of the race is at war with each other faction
     *  while the machine is in CIVIL_WAR, and never before. */
    private static void scenarioWarRelation() {
        List<Player> race = dualRacePlayers(Race.CENTAURI);
        CivilWarState cw = new CivilWarState(Race.CENTAURI, race);
        check(":994 no war relation before entry",
              !cw.isRaceAtWarWithRival(race.get(0)));
        cw.enterCivilWar(1, true);
        check(":994 a race faction is at war with its rival (:994)",
              cw.isRaceAtWarWithRival(race.get(0)));
        check(":994 the other race faction is also at war with its rival",
              cw.isRaceAtWarWithRival(race.get(1)));
        Player other = new Player("Other", Race.MINBARI, false);
        check(":994 a non-race faction is never a rival",
              !cw.isRaceAtWarWithRival(other));
    }

    /** :992 — once CIVIL_WAR, entry is idempotently refused (it is not a
     *  re-trigger), for both the tension and the declaration paths. */
    private static void scenarioReentryRefused() {
        List<Player> race = dualRacePlayers(Race.CENTAURI);
        CivilWarState cw = new CivilWarState(Race.CENTAURI, race);
        cw.enterCivilWar(1, true);
        check(":992 re-entry by tension refused while at war",
              !cw.enterCivilWar(2, true));
        check(":992 re-entry by declaration refused while at war",
              !cw.enterByDeclaration(2));
        check(":992 round unchanged by the refused re-entry",
              cw.getEnteredRound() == 1);
    }

    /** :992 / :956 — a single-faction race can never enter the machine. */
    private static void scenarioNonDualRaceRefused() {
        List<Player> lone = new ArrayList<Player>();
        lone.add(new Player("Solo", Race.CENTAURI, false));
        CivilWarState cw = new CivilWarState(Race.CENTAURI, lone);
        check(":956 single-faction race is not dual",
              !cw.isDualRace());
        check(":992 entry refused for a non-dual race",
              !cw.enterCivilWar(1, true));
        check(":992 declaration refused for a non-dual race",
              !cw.enterByDeclaration(1));
        check(":992 no split tensions without entry",
              cw.exitByWarEnd().isEmpty());
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    /** Two Players of the same faction — the synthetic dual race (:956). */
    private static List<Player> dualRacePlayers(Race f) {
        List<Player> players = new ArrayList<Player>();
        players.add(new Player(f + "-A", f, false));
        players.add(new Player(f + "-B", f, false));
        return players;
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            System.out.println("  [B5-2563] " + what + ": PASS");
        } else {
            System.out.println("  [B5-2563] " + what + ": FAIL");
            failures++;
        }
    }
}
