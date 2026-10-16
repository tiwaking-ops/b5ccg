package b5ccg.engine;

import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.Loyalty;
import b5ccg.model.enums.Race;

import java.util.List;

/**
 * B5-1996 — INTER-FACTION RELATIONSHIP STATES probe (standalone CLI, never
 * wired into compile.sh RUN_TESTS; exit 0 on all-pass, 1 on any failure).
 *
 * Rulebook section "States" (:801-803) plus the war-exclusivity clause at
 * :807. New-file-only task: no game-logic edits beyond the additive
 * RulesEngine facade, never touches HeadlessConformanceTest.java, and
 * leaves the DONE B5-0376 war resolution and TensionMatrix alone.
 *
 * Each assertion names the rulebook line it enforces, and each negative
 * assertion is there because the query that cannot fail is decoration:
 * a book that always answered true would pass a probe of only positives.
 */
public class B51996RaceStateProbe {

    private static int failures = 0;

    public static void main(String[] args) {
        scenarioPairStartsWithNoState();
        scenarioAllianceAndTradePact();
        scenarioWarCancelsOtherStates();
        scenarioWarIsExclusiveOnEntry();
        scenarioWarReadsThroughToTheMatrix();
        scenarioExitPaths();
        scenarioSelfAndNullPairsRefused();
        scenarioEngineFacadeBindsOneGame();
        scenarioRosterQuery();

        System.out.println();
        if (failures == 0) {
            System.out.println("B5-1996 PROBE PASSED (all INTER-FACTION STATE scenarios)");
        } else {
            System.out.println("B5-1996 PROBE FAILED (" + failures + " failure(s))");
        }
        System.exit(failures == 0 ? 0 : 1);
    }

    // ── scenarios ───────────────────────────────────────────────────────────

    /** :801 — tension is primary, so an untouched pair has no state at all. */
    private static void scenarioPairStartsWithNoState() {
        GameState g = game();
        RaceStateBook book = RaceStateBook.forState(g);
        check("fresh pair has no alliance", !book.isAllied(Race.HUMAN, Race.NARN));
        check("fresh pair has no trade pact", !book.isTrading(Race.HUMAN, Race.NARN));
        check("fresh pair is not at war", !book.isAtWar(Race.HUMAN, Race.NARN));
        check("fresh pair's state set is empty",
              book.getStates(Race.HUMAN, Race.NARN).isEmpty());
    }

    /** :803 — alliances and trade pacts are the named common states. */
    private static void scenarioAllianceAndTradePact() {
        GameState g = game();
        RaceStateBook book = RaceStateBook.forState(g);
        check("alliance entered", book.enterState(Race.HUMAN, Race.NARN,
                RaceState.ALLIANCE));
        check("alliance reads back", book.isAllied(Race.HUMAN, Race.NARN));
        // States are a property of the PAIR, so argument order is irrelevant.
        check("alliance is order-independent",
              book.isAllied(Race.NARN, Race.HUMAN));
        check("trade pact entered", book.enterState(Race.HUMAN, Race.NARN,
                RaceState.TRADE_PACT));
        check("trade pact reads back", book.isTrading(Race.HUMAN, Race.NARN));
        check("alliance survives alongside the trade pact",
              book.isAllied(Race.HUMAN, Race.NARN));
        check("both states are in force for the pair",
              book.getStates(Race.HUMAN, Race.NARN).size() == 2);
        // A third party is unaffected: the state is pairwise, not global.
        check("an unrelated pair gains nothing",
              !book.isAllied(Race.HUMAN, Race.CENTAURI));
        // Re-entry is refused rather than silently duplicating.
        check("re-entering the alliance is refused", !book.enterState(Race.HUMAN,
                Race.NARN, RaceState.ALLIANCE));
    }

    /** :807 — "when races enter a state of War, all other states between the
     *  races are cancelled". This is the clause the whole task turns on. */
    private static void scenarioWarCancelsOtherStates() {
        GameState g = game();
        RaceStateBook book = RaceStateBook.forState(g);
        book.enterState(Race.HUMAN, Race.NARN, RaceState.ALLIANCE);
        book.enterState(Race.HUMAN, Race.NARN, RaceState.TRADE_PACT);
        check("two states in force before the war",
              book.getStates(Race.HUMAN, Race.NARN).size() == 2);
        check("war entered", book.enterWar(Race.HUMAN, Race.NARN));
        check("war reads back", book.isAtWar(Race.HUMAN, Race.NARN));
        check("alliance cancelled by the war (:807)",
              !book.isAllied(Race.HUMAN, Race.NARN));
        check("trade pact cancelled by the war (:807)",
              !book.isTrading(Race.HUMAN, Race.NARN));
        check("only war remains for the pair",
              book.getStates(Race.HUMAN, Race.NARN).size() == 1
              && book.getStates(Race.HUMAN, Race.NARN).contains(RaceState.WAR));
        // A third party keeps its own states: cancellation is pairwise.
        book.enterState(Race.CENTAURI, Race.NARN, RaceState.ALLIANCE);
        book.enterWar(Race.HUMAN, Race.MINBARI);
        check("an unrelated alliance survives an unrelated war",
              book.isAllied(Race.CENTAURI, Race.NARN));
    }

    /** :807 makes war exclusive, so no other state may be re-added while at
     *  war — the pair's other states were cancelled, not suspended. */
    private static void scenarioWarIsExclusiveOnEntry() {
        GameState g = game();
        RaceStateBook book = RaceStateBook.forState(g);
        book.enterWar(Race.HUMAN, Race.NARN);
        check("alliance refused while at war",
              !book.enterState(Race.HUMAN, Race.NARN, RaceState.ALLIANCE));
        check("trade pact refused while at war",
              !book.enterState(Race.HUMAN, Race.NARN, RaceState.TRADE_PACT));
        check("the refusals changed nothing",
              book.getStates(Race.HUMAN, Race.NARN).size() == 1);
        check("re-entering war is refused (idempotent)",
              !book.enterWar(Race.HUMAN, Race.NARN));
    }

    /** War is owned by the TensionMatrix (B5-0376 territory). The book must
     *  read and write THROUGH, never keep a second copy that can drift. */
    private static void scenarioWarReadsThroughToTheMatrix() {
        GameState g = game();
        RaceStateBook book = RaceStateBook.forState(g);
        // War written straight to the matrix, as every existing test does.
        g.getTensionMatrix().enterWar(Race.NARN, Race.MINBARI);
        check("a matrix-entered war is visible through the book",
              book.isAtWar(Race.NARN, Race.MINBARI));
        check("matrix-entered war appears in the pair's states",
              book.getStates(Race.NARN, Race.MINBARI).contains(RaceState.WAR));
        check("the matrix's at-war set is reachable from the book",
              book.getWarPairs().size() == 1);
        // And the reverse direction: book-entered war lands in the matrix.
        book.enterWar(Race.HUMAN, Race.CENTAURI);
        check("a book-entered war is visible on the matrix",
              g.isAtWar(Race.HUMAN, Race.CENTAURI));
    }

    /** Leaving a state is explicit for alliance/trade; war has its own exit. */
    private static void scenarioExitPaths() {
        GameState g = game();
        RaceStateBook book = RaceStateBook.forState(g);
        book.enterState(Race.HUMAN, Race.NARN, RaceState.ALLIANCE);
        book.enterState(Race.HUMAN, Race.NARN, RaceState.TRADE_PACT);
        check("alliance exited", book.exitState(Race.HUMAN, Race.NARN,
                RaceState.ALLIANCE));
        check("alliance no longer in force",
              !book.isAllied(Race.HUMAN, Race.NARN));
        check("trade pact untouched by the alliance exit",
              book.isTrading(Race.HUMAN, Race.NARN));
        check("exiting a state that is not in force is refused",
              !book.exitState(Race.HUMAN, Race.NARN, RaceState.ALLIANCE));
        // exitState must not stand in for war's own exit.
        check("exitState does not exit war",
              !book.exitState(Race.HUMAN, Race.NARN, RaceState.WAR));

        book.enterWar(Race.HUMAN, Race.CENTAURI);
        check("war exited", book.exitWar(Race.HUMAN, Race.CENTAURI));
        check("no state remains after the peace",
              book.getStates(Race.HUMAN, Race.CENTAURI).isEmpty());
        check("exiting a war that is not in force is refused",
              !book.exitWar(Race.HUMAN, Race.CENTAURI));
    }

    /** A self-relationship or a null is not a pair. Every transition and
     *  query must refuse it so no caller has to pre-check. */
    private static void scenarioSelfAndNullPairsRefused() {
        GameState g = game();
        RaceStateBook book = RaceStateBook.forState(g);
        check("self-alliance refused", !book.enterState(Race.HUMAN, Race.HUMAN,
                RaceState.ALLIANCE));
        check("null-pair alliance refused", !book.enterState(Race.HUMAN, null,
                RaceState.ALLIANCE));
        check("null-state refused", !book.enterState(Race.HUMAN, Race.NARN, null));
        check("self-war refused", !book.enterWar(Race.NARN, Race.NARN));
        check("self-pair reads no alliance",
              !book.isAllied(Race.NARN, Race.NARN));
        check("self-pair reads no war", !book.isAtWar(Race.NARN, Race.NARN));
        check("null-pair state set is empty",
              book.getStates(null, Race.NARN).isEmpty());
        check("null-state query is false", !book.isInState(Race.HUMAN, Race.NARN, null));
        // A refused self-pair must not have created a row.
        check("no self-relationship was recorded",
              book.getStateMap().get(Race.HUMAN) == null);
    }

    /** The RulesEngine facade must persist state across calls and refuse to
     *  answer for a second game. */
    private static void scenarioEngineFacadeBindsOneGame() {
        GameState g = game();
        RulesEngine rules = new RulesEngine();
        check("facade alliance entered",
              rules.enterRaceState(Race.HUMAN, Race.NARN,
                      RaceState.ALLIANCE, g));
        check("facade alliance reads back on a later call",
              rules.areAllied(Race.HUMAN, Race.NARN, g));
        check("facade trade pact entered",
              rules.enterRaceState(Race.HUMAN, Race.NARN,
                      RaceState.TRADE_PACT, g));
        check("facade reports the pair as trading",
              rules.areTrading(Race.HUMAN, Race.NARN, g));
        check("facade exits the alliance",
              rules.exitRaceState(Race.HUMAN, Race.NARN,
                      RaceState.ALLIANCE, g));
        check("facade no longer reports the alliance",
              !rules.areAllied(Race.HUMAN, Race.NARN, g));
        // One engine instance, one game.
        check("facade refuses a second game", rules.getRaceStates(game()) == null);
        check("facade refuses a null game", rules.getRaceStates(null) == null);
    }

    /** getRacesInState answers "who is in this state", from a roster. */
    private static void scenarioRosterQuery() {
        GameState g = game();
        RaceStateBook book = RaceStateBook.forState(g);
        book.enterState(Race.HUMAN, Race.NARN, RaceState.ALLIANCE);
        book.enterWar(Race.CENTAURI, Race.MINBARI);
        java.util.Set<Race> allied =
                book.getRacesInState(RaceState.ALLIANCE, roster());
        check("allied set is the two factions in an alliance",
              allied.size() == 2 && allied.contains(Race.HUMAN)
                                 && allied.contains(Race.NARN));
        java.util.Set<Race> atWar =
                book.getRacesInState(RaceState.WAR, roster());
        check("at-war set is the two factions at war",
              atWar.size() == 2 && atWar.contains(Race.CENTAURI)
                                && atWar.contains(Race.MINBARI));
        check("empty roster yields an empty set",
              book.getRacesInState(RaceState.ALLIANCE,
                      new java.util.ArrayList<Race>()).isEmpty());
        check("null roster yields an empty set",
              book.getRacesInState(RaceState.ALLIANCE, null).isEmpty());
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private static GameState game() {
        List<Player> players = new java.util.ArrayList<Player>();
        players.add(new Player("Human",   Race.HUMAN,   false));
        players.add(new Player("Minbari", Race.MINBARI, false));
        players.add(new Player("Centauri", Race.CENTAURI, false));
        players.add(new Player("Narn",    Race.NARN,    false));
        return new GameState(players);
    }

    private static List<Race> roster() {
        List<Race> r = new java.util.ArrayList<Race>();
        r.add(Race.HUMAN);
        r.add(Race.MINBARI);
        r.add(Race.CENTAURI);
        r.add(Race.NARN);
        return r;
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            System.out.println("  [B5-1996] " + what + ": PASS");
        } else {
            System.out.println("  [B5-1996] " + what + ": FAIL");
            failures++;
        }
    }
}
