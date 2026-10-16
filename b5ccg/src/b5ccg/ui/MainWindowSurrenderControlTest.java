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
 * B5-2585 - regression for the surrender control.
 *
 * <p>The surrender button and its click handler were added by B5-0661 (engine)
 * and B5-0701 (UI), so this row's premise ("surrenderButton is declared but
 * has no click handler") was already stale when it was seeded. Nothing
 * asserted the wiring, which is why it could be believed missing: this class
 * is that standing assertion.
 *
 * <p>It locks three things the UI alone cannot guarantee:
 * (1) surrenderButton exists, is labelled "Surrender", and carries a listener;
 * (2) its enablement is the ENGINE's verdict, not a UI re-derivation - it is
 *     lit on the human DRAW turn at war with an ambassador and dark otherwise
 *     (RulesEngine.canSurrender, RulesEngine.java:1231);
 * (3) the SURRENDER action the listener submits really surrenders the player,
 *     grants the target +3 influence, and marks them as terminated
 *     (GameController.java:1085, RulesEngine.executeSurrender).
 *
 * <p>Exit codes: 0 all checks passed, 1 a check failed. A headless host skips
 * with exit 0, matching MainWindowForfeitControlTest.
 */
public class MainWindowSurrenderControlTest {
    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [SURRUI] " + label + ": " + (ok ? "PASS" : "FAIL"));
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
                                            GamePhase phase, boolean atWar) {
        Player human = player(humanName, humanFaction, true);
        Player rival = player(rivalName, rivalFaction, false);
        List<Player> players = new ArrayList<Player>();
        players.add(human);
        players.add(rival);
        GameState state = new GameState(players);
        human.setGameState(state);
        rival.setGameState(state);
        state.setPhase(phase);
        if (atWar) {
            state.getTensionMatrix().enterWar(humanFaction, rivalFaction);
        }
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
     * directly - it is where the SURRENDER case the listener submits to lives.
     */
    private static void dispatchSurrender(GameController controller,
                                          Player human, Player target)
            throws Exception {
        Method method = GameController.class.getDeclaredMethod(
            "processAction", Player.class, GameAction.class);
        method.setAccessible(true);
        method.invoke(controller, human, GameAction.surrender(target));
    }

    private static void testControlIsWired() throws Exception {
        System.out.println("  [SURRUI] --- the surrender control exists and is wired ---");
        GameState state = twoPlayerState("S2585Human", Faction.NARN,
            "S2585Rival", Faction.MINBARI, GamePhase.DRAW, true);
        MainWindow window = window(controller(state));
        JButton surrender = (JButton) field(window, "surrenderButton");

        check("surrenderButton is constructed", surrender != null);
        check("surrenderButton is labelled Surrender",
            "Surrender".equals(surrender.getText()));
        check("surrenderButton carries a click handler",
            surrender.getActionListeners() != null
                && surrender.getActionListeners().length > 0);
        check("surrenderButton starts disabled before the first refresh",
            !surrender.isEnabled());
        window.dispose();
    }

    private static void testEngineGatesEnablement() throws Exception {
        System.out.println("  [SURRUI] --- enablement is the engine's verdict ---");

        // Enabled: DRAW phase, at war, human has ambassador
        GameState drawAtWar = twoPlayerState("S2585ActHuman", Faction.NARN,
            "S2585ActRival", Faction.MINBARI, GamePhase.DRAW, true);
        MainWindow lit = window(controller(drawAtWar));
        JButton litButton = (JButton) field(lit, "surrenderButton");
        refresh(lit, drawAtWar);
        check("surrenderButton ENABLES on the human DRAW turn at war with ambassador",
            litButton.isEnabled());
        lit.dispose();

        // Disabled: outside DRAW phase
        GameState actionPhase = twoPlayerState("S2585ActionHuman", Faction.NARN,
            "S2585ActionRival", Faction.MINBARI, GamePhase.ACTION, true);
        MainWindow action = window(controller(actionPhase));
        JButton actionButton = (JButton) field(action, "surrenderButton");
        refresh(action, actionPhase);
        check("surrenderButton stays DISABLED outside the DRAW phase",
            !actionButton.isEnabled());
        action.dispose();

        // Disabled: not at war
        GameState drawNoWar = twoPlayerState("S2585NoWarHuman", Faction.NARN,
            "S2585NoWarRival", Faction.CENTAURI, GamePhase.DRAW, false);
        MainWindow nowar = window(controller(drawNoWar));
        JButton nowarButton = (JButton) field(nowar, "surrenderButton");
        refresh(nowar, drawNoWar);
        check("surrenderButton stays DISABLED when not at war with rival",
            !nowarButton.isEnabled());
        nowar.dispose();

        // Disabled: human has no ambassador
        GameState noAmb = twoPlayerState("S2585NoAmbHuman", Faction.NARN,
            "S2585NoAmbRival", Faction.MINBARI, GamePhase.DRAW, true);
        Player human = noAmb.getPlayers().get(0);
        human.setAmbassador(null); // remove ambassador
        human.getInnerCircle().clear();
        MainWindow noamb = window(controller(noAmb));
        JButton noambButton = (JButton) field(noamb, "surrenderButton");
        refresh(noamb, noAmb);
        check("surrenderButton stays DISABLED when human has no ambassador",
            !noambButton.isEnabled());
        noamb.dispose();

        // Disabled: human already surrendered
        GameState surrendered = twoPlayerState("S2585SurrHuman", Faction.NARN,
            "S2585SurrRival", Faction.MINBARI, GamePhase.DRAW, true);
        Player sHuman = surrendered.getPlayers().get(0);
        surrendered.markSurrendered(sHuman);
        MainWindow surr = window(controller(surrendered));
        JButton surrButton = (JButton) field(surr, "surrenderButton");
        refresh(surr, surrendered);
        check("surrenderButton stays DISABLED when human already surrendered",
            !surrButton.isEnabled());
        surr.dispose();
    }

    private static void testSubmittedActionSurrenders() throws Exception {
        System.out.println("  [SURRUI] --- the submitted SURRENDER action really surrenders ---");
        GameState state = twoPlayerState("S2585SubHuman", Faction.NARN,
            "S2585SubRival", Faction.MINBARI, GamePhase.DRAW, true);
        Player human = state.getPlayers().get(0);
        Player rival = state.getPlayers().get(1);
        int rivalInfluenceBefore = rival.getInfluence();

        check("the human has not surrendered before submitting",
            !human.hasSurrendered());
        dispatchSurrender(controller(state), human, rival);
        check("the SURRENDER action marks the human surrendered",
            human.hasSurrendered());
        check("the target gains 3 influence from the surrender",
            rival.getInfluence() == rivalInfluenceBefore + 3);
        check("the human is no longer active",
            !state.isPlayerActive(human));

        MainWindow window = window(controller(state));
        JButton surrender = (JButton) field(window, "surrenderButton");
        refresh(window, state);
        check("surrenderButton DISABLES again once the player has surrendered",
            !surrender.isEnabled());
        window.dispose();
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("MAIN WINDOW SURRENDER CONTROL TEST SKIPPED"
                + " (headless environment)");
            return;
        }
        System.out.println("=== MainWindow surrender control regression ===");
        testControlIsWired();
        testEngineGatesEnablement();
        testSubmittedActionSurrenders();
        System.out.println(failed == 0
            ? "MAIN WINDOW SURRENDER CONTROL TEST PASSED (" + checks + " checks)"
            : "MAIN WINDOW SURRENDER CONTROL TEST FAILED ("
                + failed + " of " + checks + " checks)");
        System.out.flush();
        System.err.flush();
        System.exit(failed == 0 ? 0 : 1);
    }
}
