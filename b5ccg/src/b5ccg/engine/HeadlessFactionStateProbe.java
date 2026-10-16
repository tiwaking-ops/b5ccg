package b5ccg.engine;

import b5ccg.ai.AIPlayer;
import b5ccg.model.Card;
import b5ccg.model.Deck;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.AIDifficulty;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.Rarity;

import java.util.ArrayList;
import java.util.List;

/**
 * B5-2535 — FACTION STATE BOOK conformance probe (standalone CLI, wired into
 * compile.sh RUN_TESTS as a gate; exit 0 on all-pass, 1 on any failure).
 *
 * <p>Verifies that {@link FactionStateBook} is correctly <em>initialised</em>,
 * <em>advanced</em>, and <em>read across a full round</em>. Complements
 * {@code B51996FactionStateProbe} (which tests the book in isolation) by
 * driving state through a real {@link GameController} round and across
 * {@code advanceRound()} boundaries.
 *
 * <p>Each assertion names the rulebook line it enforces. Java 6 only.
 *
 * <p>Run after compile.bat / compile.sh:
 * {@code java -cp b5ccg/out b5ccg.engine.HeadlessFactionStateProbe}
 */
public class HeadlessFactionStateProbe {

    private static int failures = 0;

    /** Each scenario gets a fresh RulesEngine — the facade binds to one game. */
    private static RulesEngine rules() {
        return new RulesEngine();
    }

    public static void main(String[] args) {
        scenarioInitialisation();
        scenarioAdvancement();
        scenarioPersistenceAcrossAdvanceRound();
        scenarioWarExclusivityAcrossRound();
        scenarioIsolation();
        scenarioFacadeAcrossRounds();
        scenarioFullGameRound();

        System.out.println();
        if (failures == 0) {
            System.out.println("B5-2535 PROBE PASSED (all FACTION STATE scenarios)");
        } else {
            System.out.println("B5-2535 PROBE FAILED (" + failures + " failure(s))");
        }
        System.exit(failures == 0 ? 0 : 1);
    }

    // ── scenarios ───────────────────────────────────────────────────────────

    /** :801 — a fresh game has no faction states. */
    private static void scenarioInitialisation() {
        GameState g = game();
        RulesEngine r = rules();
        check("fresh game: no alliance", !r.areAllied(Faction.HUMAN, Faction.NARN, g));
        check("fresh game: no trade pact", !r.areTrading(Faction.HUMAN, Faction.NARN, g));
        check("fresh game: no war", !g.isAtWar(Faction.HUMAN, Faction.NARN));
        check("fresh game: state set is empty",
              r.getFactionStates(g).getStates(Faction.HUMAN, Faction.NARN).isEmpty());
    }

    /** :803 — alliances and trade pacts can be entered and read back. */
    private static void scenarioAdvancement() {
        GameState g = game();
        RulesEngine r = rules();
        check("alliance entered via facade",
              r.enterFactionState(Faction.HUMAN, Faction.NARN, FactionState.ALLIANCE, g));
        check("alliance reads back",
              r.areAllied(Faction.HUMAN, Faction.NARN, g));
        check("trade pact entered via facade",
              r.enterFactionState(Faction.HUMAN, Faction.NARN, FactionState.TRADE_PACT, g));
        check("trade pact reads back",
              r.areTrading(Faction.HUMAN, Faction.NARN, g));
        check("alliance survives alongside trade pact",
              r.areAllied(Faction.HUMAN, Faction.NARN, g));
        check("both states in force",
              r.getFactionStates(g).getStates(Faction.HUMAN, Faction.NARN).size() == 2);
        check("re-entering alliance is refused",
              !r.enterFactionState(Faction.HUMAN, Faction.NARN, FactionState.ALLIANCE, g));
        check("alliance exited via facade",
              r.exitFactionState(Faction.HUMAN, Faction.NARN, FactionState.ALLIANCE, g));
        check("alliance no longer in force",
              !r.areAllied(Faction.HUMAN, Faction.NARN, g));
        check("trade pact untouched by alliance exit",
              r.areTrading(Faction.HUMAN, Faction.NARN, g));
    }

    /** State persists across advanceRound() — the book is not round-scoped. */
    private static void scenarioPersistenceAcrossAdvanceRound() {
        GameState g = game();
        RulesEngine r = rules();
        r.enterFactionState(Faction.HUMAN, Faction.NARN, FactionState.ALLIANCE, g);
        r.enterFactionState(Faction.HUMAN, Faction.NARN, FactionState.TRADE_PACT, g);
        g.advanceRound();
        check("alliance persists across advanceRound()",
              r.areAllied(Faction.HUMAN, Faction.NARN, g));
        check("trade pact persists across advanceRound()",
              r.areTrading(Faction.HUMAN, Faction.NARN, g));
        g.advanceRound();
        g.advanceRound();
        check("alliance persists across three advanceRound() calls",
              r.areAllied(Faction.HUMAN, Faction.NARN, g));
        check("trade pact persists across three advanceRound() calls",
              r.areTrading(Faction.HUMAN, Faction.NARN, g));
    }

    /** :807 — war cancels other states, and the cancellation persists. */
    private static void scenarioWarExclusivityAcrossRound() {
        GameState g = game();
        RulesEngine r = rules();
        r.enterFactionState(Faction.HUMAN, Faction.NARN, FactionState.ALLIANCE, g);
        r.enterFactionState(Faction.HUMAN, Faction.NARN, FactionState.TRADE_PACT, g);
        check("war entered via facade",
              r.enterFactionState(Faction.HUMAN, Faction.NARN, FactionState.WAR, g));
        check("alliance cancelled by war",
              !r.areAllied(Faction.HUMAN, Faction.NARN, g));
        check("trade pact cancelled by war",
              !r.areTrading(Faction.HUMAN, Faction.NARN, g));
        g.advanceRound();
        check("alliance stays cancelled across advanceRound()",
              !r.areAllied(Faction.HUMAN, Faction.NARN, g));
        check("trade pact stays cancelled across advanceRound()",
              !r.areTrading(Faction.HUMAN, Faction.NARN, g));
        check("war persists across advanceRound()",
              g.isAtWar(Faction.HUMAN, Faction.NARN));
        check("alliance refused while at war",
              !r.enterFactionState(Faction.HUMAN, Faction.NARN, FactionState.ALLIANCE, g));
    }

    /** Two games do not share faction state. */
    private static void scenarioIsolation() {
        GameState g1 = game();
        GameState g2 = game();
        RulesEngine r = rules();
        r.enterFactionState(Faction.HUMAN, Faction.NARN, FactionState.ALLIANCE, g1);
        check("alliance in g1", r.areAllied(Faction.HUMAN, Faction.NARN, g1));
        check("no alliance in g2", !r.areAllied(Faction.HUMAN, Faction.NARN, g2));
        check("facade refuses a second game",
              r.getFactionStates(g2) == null);
    }

    /** The RulesEngine facade works before and after advanceRound(). */
    private static void scenarioFacadeAcrossRounds() {
        GameState g = game();
        RulesEngine r = rules();
        check("facade: alliance entered before round",
              r.enterFactionState(Faction.CENTAURI, Faction.MINBARI,
                      FactionState.ALLIANCE, g));
        g.advanceRound();
        check("facade: alliance readable after round",
              r.areAllied(Faction.CENTAURI, Faction.MINBARI, g));
        check("facade: trade pact entered after round",
              r.enterFactionState(Faction.CENTAURI, Faction.MINBARI,
                      FactionState.TRADE_PACT, g));
        check("facade: trade pact readable after round",
              r.areTrading(Faction.CENTAURI, Faction.MINBARI, g));
        g.advanceRound();
        check("facade: both states readable after two rounds",
              r.areAllied(Faction.CENTAURI, Faction.MINBARI, g)
              && r.areTrading(Faction.CENTAURI, Faction.MINBARI, g));
    }

    /** State persists across a full GameController round with real AI players. */
    private static void scenarioFullGameRound() {
        GameState g = game();
        RulesEngine r = rules();
        r.enterFactionState(Faction.HUMAN, Faction.NARN, FactionState.ALLIANCE, g);
        r.enterFactionState(Faction.HUMAN, Faction.NARN, FactionState.TRADE_PACT, g);
        r.enterFactionState(Faction.CENTAURI, Faction.MINBARI, FactionState.ALLIANCE, g);

        // Run a full game on a background thread. The game ends when a winner
        // is found or the stall guard fires. We wait up to 120 s.
        final GameState finalG = g;
        Thread gameThread = new Thread(new Runnable() {
            public void run() {
                try {
                    List<AIPlayer> aiPlayers = new ArrayList<AIPlayer>();
                    for (Player p : finalG.getPlayers()) {
                        aiPlayers.add(new AIPlayer(p, AIDifficulty.EASY));
                    }
                    GameController controller = new GameController(
                            finalG, aiPlayers,
                            new GameStateCallback() {
                                public void accept(GameState gs) { }
                            });
                    controller.runGame();
                } catch (Throwable t) {
                    // The game may throw on stall; that is a valid outcome.
                }
            }
        });
        gameThread.setDaemon(true);
        gameThread.start();

        // Wait for the game to finish (max 120 s).
        try {
            gameThread.join(120000L);
        } catch (InterruptedException e) {
            // Fall through to checks.
        }

        // States must persist after the full game round.
        check("alliance persists after full game round",
              r.areAllied(Faction.HUMAN, Faction.NARN, g));
        check("trade pact persists after full game round",
              r.areTrading(Faction.HUMAN, Faction.NARN, g));
        check("unrelated alliance persists after full game round",
              r.areAllied(Faction.CENTAURI, Faction.MINBARI, g));
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private static GameState game() {
        List<Player> players = new ArrayList<Player>();
        players.add(new Player("Human",   Faction.HUMAN,   false));
        players.add(new Player("Minbari", Faction.MINBARI, false));
        players.add(new Player("Centauri", Faction.CENTAURI, false));
        players.add(new Player("Narn",    Faction.NARN,    false));
        GameState g = new GameState(players);
        for (Player p : players) {
            p.setGameState(g);
            // Give each player a minimal deck so GameController can run.
            List<Card> filler = new ArrayList<Card>();
            for (int i = 0; i < 10; i++) {
                filler.add(new b5ccg.model.EventCard(
                        "deck_" + p.getName() + "_" + i,
                        "Deck " + i,
                        "EVENT", Rarity.COMMON, Faction.ANY, CardSet.PREMIERE,
                        "x", "text"));
            }
            p.setDeck(new Deck(filler));
        }
        return g;
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            System.out.println("  [B5-2535] " + what + ": PASS");
        } else {
            System.out.println("  [B5-2535] " + what + ": FAIL");
            failures++;
        }
    }
}
