package b5ccg.engine;

import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.Faction;

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
public class B51996FactionStateProbe {

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
        FactionStateBook book = FactionStateBook.forState(g);
        check("fresh pair has no alliance", !book.isAllied(Faction.HUMAN, Faction.NARN));
        check("fresh pair has no trade pact", !book.isTrading(Faction.HUMAN, Faction.NARN));
        check("fresh pair is not at war", !book.isAtWar(Faction.HUMAN, Faction.NARN));
        check("fresh pair's state set is empty",
              book.getStates(Faction.HUMAN, Faction.NARN).isEmpty());
    }

    /** :803 — alliances and trade pacts are the named common states. */
    private static void scenarioAllianceAndTradePact() {
        GameState g = game();
        FactionStateBook book = FactionStateBook.forState(g);
        check("alliance entered", book.enterState(Faction.HUMAN, Faction.NARN,
                FactionState.ALLIANCE));
        check("alliance reads back", book.isAllied(Faction.HUMAN, Faction.NARN));
        // States are a property of the PAIR, so argument order is irrelevant.
        check("alliance is order-independent",
              book.isAllied(Faction.NARN, Faction.HUMAN));
        check("trade pact entered", book.enterState(Faction.HUMAN, Faction.NARN,
                FactionState.TRADE_PACT));
        check("trade pact reads back", book.isTrading(Faction.HUMAN, Faction.NARN));
        check("alliance survives alongside the trade pact",
              book.isAllied(Faction.HUMAN, Faction.NARN));
        check("both states are in force for the pair",
              book.getStates(Faction.HUMAN, Faction.NARN).size() == 2);
        // A third party is unaffected: the state is pairwise, not global.
        check("an unrelated pair gains nothing",
              !book.isAllied(Faction.HUMAN, Faction.CENTAURI));
        // Re-entry is refused rather than silently duplicating.
        check("re-entering the alliance is refused", !book.enterState(Faction.HUMAN,
                Faction.NARN, FactionState.ALLIANCE));
    }

    /** :807 — "when races enter a state of War, all other states between the
     *  races are cancelled". This is the clause the whole task turns on. */
    private static void scenarioWarCancelsOtherStates() {
        GameState g = game();
        FactionStateBook book = FactionStateBook.forState(g);
        book.enterState(Faction.HUMAN, Faction.NARN, FactionState.ALLIANCE);
        book.enterState(Faction.HUMAN, Faction.NARN, FactionState.TRADE_PACT);
        check("two states in force before the war",
              book.getStates(Faction.HUMAN, Faction.NARN).size() == 2);
        check("war entered", book.enterWar(Faction.HUMAN, Faction.NARN));
        check("war reads back", book.isAtWar(Faction.HUMAN, Faction.NARN));
        check("alliance cancelled by the war (:807)",
              !book.isAllied(Faction.HUMAN, Faction.NARN));
        check("trade pact cancelled by the war (:807)",
              !book.isTrading(Faction.HUMAN, Faction.NARN));
        check("only war remains for the pair",
              book.getStates(Faction.HUMAN, Faction.NARN).size() == 1
              && book.getStates(Faction.HUMAN, Faction.NARN).contains(FactionState.WAR));
        // A third party keeps its own states: cancellation is pairwise.
        book.enterState(Faction.CENTAURI, Faction.NARN, FactionState.ALLIANCE);
        book.enterWar(Faction.HUMAN, Faction.MINBARI);
        check("an unrelated alliance survives an unrelated war",
              book.isAllied(Faction.CENTAURI, Faction.NARN));
    }

    /** :807 makes war exclusive, so no other state may be re-added while at
     *  war — the pair's other states were cancelled, not suspended. */
    private static void scenarioWarIsExclusiveOnEntry() {
        GameState g = game();
        FactionStateBook book = FactionStateBook.forState(g);
        book.enterWar(Faction.HUMAN, Faction.NARN);
        check("alliance refused while at war",
              !book.enterState(Faction.HUMAN, Faction.NARN, FactionState.ALLIANCE));
        check("trade pact refused while at war",
              !book.enterState(Faction.HUMAN, Faction.NARN, FactionState.TRADE_PACT));
        check("the refusals changed nothing",
              book.getStates(Faction.HUMAN, Faction.NARN).size() == 1);
        check("re-entering war is refused (idempotent)",
              !book.enterWar(Faction.HUMAN, Faction.NARN));
    }

    /** War is owned by the TensionMatrix (B5-0376 territory). The book must
     *  read and write THROUGH, never keep a second copy that can drift. */
    private static void scenarioWarReadsThroughToTheMatrix() {
        GameState g = game();
        FactionStateBook book = FactionStateBook.forState(g);
        // War written straight to the matrix, as every existing test does.
        g.getTensionMatrix().enterWar(Faction.NARN, Faction.MINBARI);
        check("a matrix-entered war is visible through the book",
              book.isAtWar(Faction.NARN, Faction.MINBARI));
        check("matrix-entered war appears in the pair's states",
              book.getStates(Faction.NARN, Faction.MINBARI).contains(FactionState.WAR));
        check("the matrix's at-war set is reachable from the book",
              book.getWarPairs().size() == 1);
        // And the reverse direction: book-entered war lands in the matrix.
        book.enterWar(Faction.HUMAN, Faction.CENTAURI);
        check("a book-entered war is visible on the matrix",
              g.isAtWar(Faction.HUMAN, Faction.CENTAURI));
    }

    /** Leaving a state is explicit for alliance/trade; war has its own exit. */
    private static void scenarioExitPaths() {
        GameState g = game();
        FactionStateBook book = FactionStateBook.forState(g);
        book.enterState(Faction.HUMAN, Faction.NARN, FactionState.ALLIANCE);
        book.enterState(Faction.HUMAN, Faction.NARN, FactionState.TRADE_PACT);
        check("alliance exited", book.exitState(Faction.HUMAN, Faction.NARN,
                FactionState.ALLIANCE));
        check("alliance no longer in force",
              !book.isAllied(Faction.HUMAN, Faction.NARN));
        check("trade pact untouched by the alliance exit",
              book.isTrading(Faction.HUMAN, Faction.NARN));
        check("exiting a state that is not in force is refused",
              !book.exitState(Faction.HUMAN, Faction.NARN, FactionState.ALLIANCE));
        // exitState must not stand in for war's own exit.
        check("exitState does not exit war",
              !book.exitState(Faction.HUMAN, Faction.NARN, FactionState.WAR));

        book.enterWar(Faction.HUMAN, Faction.CENTAURI);
        check("war exited", book.exitWar(Faction.HUMAN, Faction.CENTAURI));
        check("no state remains after the peace",
              book.getStates(Faction.HUMAN, Faction.CENTAURI).isEmpty());
        check("exiting a war that is not in force is refused",
              !book.exitWar(Faction.HUMAN, Faction.CENTAURI));
    }

    /** A self-relationship or a null is not a pair. Every transition and
     *  query must refuse it so no caller has to pre-check. */
    private static void scenarioSelfAndNullPairsRefused() {
        GameState g = game();
        FactionStateBook book = FactionStateBook.forState(g);
        check("self-alliance refused", !book.enterState(Faction.HUMAN, Faction.HUMAN,
                FactionState.ALLIANCE));
        check("null-pair alliance refused", !book.enterState(Faction.HUMAN, null,
                FactionState.ALLIANCE));
        check("null-state refused", !book.enterState(Faction.HUMAN, Faction.NARN, null));
        check("self-war refused", !book.enterWar(Faction.NARN, Faction.NARN));
        check("self-pair reads no alliance",
              !book.isAllied(Faction.NARN, Faction.NARN));
        check("self-pair reads no war", !book.isAtWar(Faction.NARN, Faction.NARN));
        check("null-pair state set is empty",
              book.getStates(null, Faction.NARN).isEmpty());
        check("null-state query is false", !book.isInState(Faction.HUMAN, Faction.NARN, null));
        // A refused self-pair must not have created a row.
        check("no self-relationship was recorded",
              book.getStateMap().get(Faction.HUMAN) == null);
    }

    /** The RulesEngine facade must persist state across calls and refuse to
     *  answer for a second game. */
    private static void scenarioEngineFacadeBindsOneGame() {
        GameState g = game();
        RulesEngine rules = new RulesEngine();
        check("facade alliance entered",
              rules.enterFactionState(Faction.HUMAN, Faction.NARN,
                      FactionState.ALLIANCE, g));
        check("facade alliance reads back on a later call",
              rules.areAllied(Faction.HUMAN, Faction.NARN, g));
        check("facade trade pact entered",
              rules.enterFactionState(Faction.HUMAN, Faction.NARN,
                      FactionState.TRADE_PACT, g));
        check("facade reports the pair as trading",
              rules.areTrading(Faction.HUMAN, Faction.NARN, g));
        check("facade exits the alliance",
              rules.exitFactionState(Faction.HUMAN, Faction.NARN,
                      FactionState.ALLIANCE, g));
        check("facade no longer reports the alliance",
              !rules.areAllied(Faction.HUMAN, Faction.NARN, g));
        // One engine instance, one game.
        check("facade refuses a second game", rules.getFactionStates(game()) == null);
        check("facade refuses a null game", rules.getFactionStates(null) == null);
    }

    /** getFactionsInState answers "who is in this state", from a roster. */
    private static void scenarioRosterQuery() {
        GameState g = game();
        FactionStateBook book = FactionStateBook.forState(g);
        book.enterState(Faction.HUMAN, Faction.NARN, FactionState.ALLIANCE);
        book.enterWar(Faction.CENTAURI, Faction.MINBARI);
        java.util.Set<Faction> allied =
                book.getFactionsInState(FactionState.ALLIANCE, roster());
        check("allied set is the two factions in an alliance",
              allied.size() == 2 && allied.contains(Faction.HUMAN)
                                 && allied.contains(Faction.NARN));
        java.util.Set<Faction> atWar =
                book.getFactionsInState(FactionState.WAR, roster());
        check("at-war set is the two factions at war",
              atWar.size() == 2 && atWar.contains(Faction.CENTAURI)
                                && atWar.contains(Faction.MINBARI));
        check("empty roster yields an empty set",
              book.getFactionsInState(FactionState.ALLIANCE,
                      new java.util.ArrayList<Faction>()).isEmpty());
        check("null roster yields an empty set",
              book.getFactionsInState(FactionState.ALLIANCE, null).isEmpty());
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private static GameState game() {
        List<Player> players = new java.util.ArrayList<Player>();
        players.add(new Player("Human",   Faction.HUMAN,   false));
        players.add(new Player("Minbari", Faction.MINBARI, false));
        players.add(new Player("Centauri", Faction.CENTAURI, false));
        players.add(new Player("Narn",    Faction.NARN,    false));
        return new GameState(players);
    }

    private static List<Faction> roster() {
        List<Faction> r = new java.util.ArrayList<Faction>();
        r.add(Faction.HUMAN);
        r.add(Faction.MINBARI);
        r.add(Faction.CENTAURI);
        r.add(Faction.NARN);
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
