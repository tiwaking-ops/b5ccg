package b5ccg.ui;

import b5ccg.ai.AIPlayer;
import b5ccg.engine.GameController;
import b5ccg.engine.GameStateCallback;
import b5ccg.model.CharacterCard;
import b5ccg.model.GameAction;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.GamePhase;
import b5ccg.model.enums.Rarity;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.SwingUtilities;

/**
 * B5-2584 - regression for the voluntary forfeit control.
 *
 * <p>The forfeit button and its click handler were added by B5-2267 on top of
 * the B5-1979 engine path, so this row's premise ("forfeitButton is declared
 * but has no click handler") was already stale when it was seeded. Nothing
 * asserted the wiring, which is why it could be believed missing: this class
 * is that standing assertion.
 *
 * <p>It locks three things the UI alone cannot guarantee:
 * (1) forfeitButton exists, is labelled "Forfeit", and carries a listener;
 * (2) its enablement is the ENGINE's verdict, not a UI re-derivation - it is
 *     lit on the human ACTION turn with a live rival and dark in DRAW phase
 *     and with no rival (RulesEngine.canForfeit, RulesEngine.java:1309);
 * (3) the FORFEIT action the listener submits really forfeits the player and
 *     awards the sole survivor the game (GameController.java:1098).
 *
 * <p>Exit codes: 0 all checks passed, 1 a check failed. A headless host skips
 * with exit 0, matching MainWindowAttackControlTest.
 */
public class MainWindowForfeitControlTest {
    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [FRFTUI] " + label + ": " + (ok ? "PASS" : "FAIL"));
        if (!ok) failed++;
    }

    private static Player player(String name, Faction faction, boolean human) {
        Player player = new Player(name, faction, human);
        CharacterCard ambassador = new CharacterCard("amb_" + name, "Ambassador",
            "CHARACTER_" + faction, Rarity.FIXED, faction, CardSet.PREMIERE,
            "x", "text", 3, 3, 3, 3, true);
        player.setAmbassador(ambassador);
        player.getInnerCircle().add(ambassador);
        return player;
    }

    private static GameState twoPlayerState(String humanName, Faction humanFaction,
                                            String rivalName, Faction rivalFaction,
                                            GamePhase phase) {
        Player human = player(humanName, humanFaction, true);
        Player rival = player(rivalName, rivalFaction, false);
        List<Player> players = new ArrayList<Player>();
        players.add(human);
        players.add(rival);
        GameState state = new GameState(players);
        human.setGameState(state);
        rival.setGameState(state);
        state.setPhase(phase);
        return state;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void refresh(final MainWindow window, final GameState state)
            throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Method method = MainWindow.class.getDeclaredMethod(
                        "refresh", GameState.class);
                    method.setAccessible(true);
                    method.invoke(window, state);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        });
    }

    private static MainWindow window(final GameController controller) throws Exception {
        final MainWindow[] result = new MainWindow[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                result[0] = new MainWindow(controller);
            }
        });
        return result[0];
    }

    private static GameController controller(GameState state) {
        return new GameController(state, new ArrayList<AIPlayer>(),
            new GameStateCallback() {
                public void accept(GameState state) {
                }
            });
    }

    /**
     * submitHumanAction only QUEUES the action; the running loop drains it via
     * waitForHumanAction() and dispatches into processAction
     * (GameController.java:394). There is no loop here, so drive processAction
     * directly - it is where the FORFEIT case the listener submits to lives.
     */
    private static void dispatchForfeit(GameController controller, Player human)
            throws Exception {
        Method method = GameController.class.getDeclaredMethod(
            "processAction", Player.class, GameAction.class);
        method.setAccessible(true);
        method.invoke(controller, human, GameAction.forfeit());
    }
private static void testControlIsWired() throws Exception {
        System.out.println("  [FRFTUI] --- the forfeit control exists and is wired ---");
        GameState state = twoPlayerState("F2584Human", Faction.NARN,
            "F2584Rival", Faction.MINBARI, GamePhase.ACTION);
        MainWindow window = window(controller(state));
        JButton forfeit = (JButton) field(window, "forfeitButton");

        check("forfeitButton is constructed", forfeit != null);
        check("forfeitButton is labelled Forfeit",
            "Forfeit".equals(forfeit.getText()));
        check("forfeitButton carries a click handler",
            forfeit.getActionListeners() != null
                && forfeit.getActionListeners().length > 0);
        check("forfeitButton starts disabled before the first refresh",
            !forfeit.isEnabled());
        window.dispose();
    }

    private static void testEngineGatesEnablement() throws Exception {
        System.out.println("  [FRFTUI] --- enablement is the engine's verdict ---");
        GameState actionPhase = twoPlayerState("F2584ActHuman", Faction.NARN,
            "F2584ActRival", Faction.MINBARI, GamePhase.ACTION);
        MainWindow lit = window(controller(actionPhase));
        JButton litButton = (JButton) field(lit, "forfeitButton");
        refresh(lit, actionPhase);
        check("forfeitButton ENABLES on the human ACTION turn with a live rival",
            litButton.isEnabled());
        lit.dispose();

        GameState drawPhase = twoPlayerState("F2584DrawHuman", Faction.NARN,
            "F2584DrawRival", Faction.CENTAURI, GamePhase.DRAW);
        MainWindow draw = window(controller(drawPhase));
        JButton drawButton = (JButton) field(draw, "forfeitButton");
        refresh(draw, drawPhase);
        check("forfeitButton stays DISABLED outside the ACTION phase",
            !drawButton.isEnabled());
        draw.dispose();

        Player solo = player("F2584Solo", Faction.NARN, true);
        List<Player> soloList = new ArrayList<Player>();
        soloList.add(solo);
        GameState soloState = new GameState(soloList);
        solo.setGameState(soloState);
        soloState.setPhase(GamePhase.ACTION);
        MainWindow alone = window(controller(soloState));
        JButton soloButton = (JButton) field(alone, "forfeitButton");
        refresh(alone, soloState);
        check("forfeitButton stays DISABLED when no rival is active",
            !soloButton.isEnabled());
        alone.dispose();
    }

    private static void testSubmittedActionForfeits() throws Exception {
        System.out.println("  [FRFTUI] --- the submitted FORFEIT action really forfeits ---");
        GameState state = twoPlayerState("F2584SubHuman", Faction.NARN,
            "F2584SubRival", Faction.MINBARI, GamePhase.ACTION);
        Player human = state.getPlayers().get(0);
        Player rival = state.getPlayers().get(1);

        check("the human has not forfeited before submitting",
            !human.hasForfeited());
        dispatchForfeit(controller(state), human);
        check("the FORFEIT action marks the human forfeited",
            human.hasForfeited());
        check("forfeiting leaves the sole rival as the winner",
            state.getWinner() == rival);
        check("a forfeited player is no longer active",
            !state.isPlayerActive(human));

        MainWindow window = window(controller(state));
        JButton forfeit = (JButton) field(window, "forfeitButton");
        refresh(window, state);
        check("forfeitButton DISABLES again once the player has forfeited",
            !forfeit.isEnabled());
        window.dispose();
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("MAIN WINDOW FORFEIT CONTROL TEST SKIPPED"
                + " (headless environment)");
            return;
        }
        System.out.println("=== MainWindow forfeit control regression ===");
        testControlIsWired();
        testEngineGatesEnablement();
        testSubmittedActionForfeits();
        System.out.println(failed == 0
            ? "MAIN WINDOW FORFEIT CONTROL TEST PASSED (" + checks + " checks)"
            : "MAIN WINDOW FORFEIT CONTROL TEST FAILED ("
                + failed + " of " + checks + " checks)");
        System.out.flush();
        System.err.flush();
        System.exit(failed == 0 ? 0 : 1);
    }
}