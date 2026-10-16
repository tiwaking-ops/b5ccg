package b5ccg.ui;
import b5ccg.model.enums.Loyalty;

import b5ccg.ai.AIPlayer;
import b5ccg.engine.GameController;
import b5ccg.engine.GameStateCallback;
import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.ContingencyCard;
import b5ccg.model.GameAction;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Race;
import b5ccg.model.enums.GamePhase;
import b5ccg.model.enums.Rarity;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

/**
 * B5-2505 - regression for the contingency reveal control.
 *
 * <p>The row's premise ("revealContingencyButton is declared but has
 * no click handler that lets the human select which contingency to
 * reveal and resolve") was already false when it was seeded. The
 * whole chain landed under B5-0381 (button plus listener,
 * MainWindow.java) and was improved the same day with a
 * JComboBox&lt;String&gt; contingencySelector so the player chooses
 * WHICH placed contingency to reveal (not always the first): the
 * selector lists every placed contingency as "&lt;title&gt; (under
 * &lt;host&gt;)", the listener reads getSelectedIndex() onto
 * GameState.getPlacedContingencies(human) and submits
 * GameAction.revealContingency(selected) through
 * controller.submitHumanAction. Nothing asserted that wiring, which
 * is exactly how a false premise survives to seeding: a handler
 * deleted today would leave every suite green. This class is that
 * standing assertion.
 *
 * <p>It locks four things:
 * (1) the control exists - contingencySelector and
 *     revealContingencyButton are constructed, the button is labelled
 *     "Reveal Contingency" and carries a listener, and both start
 *     disabled;
 * (2) the selector lists the human's placed contingencies with their
 *     hosts, and the button is lit on the human ACTION turn with an
 *     unrevealed legal contingency (refreshContingencyRevealControl,
 *     MainWindow.java:2393);
 * (3) the enablement is the engine's verdict - the button goes dark
 *     outside the ACTION phase and when nothing is placed;
 * (4) the selection is EFFECTIVE - the submitted REVEAL_CONTINGENCY
 *     carries the SELECTED contingency, not the first, and the
 *     dispatched reveal marks exactly that card revealed while its
 *     unselected neighbour stays face-down under its host
 *     (GameController.java:830, CardEffects.java:281).
 *
 * <p>Exit codes: 0 all checks passed, 1 a check failed. A headless
 * host skips with exit 0, matching MainWindowForfeitControlTest.
 */
public class MainWindowContingencyRevealControlTest {
    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [CNUI] " + label + ": " + (ok ? "PASS" : "FAIL"));
        if (!ok) failed++;
    }

    private static Player player(String name, Race faction, boolean human) {
        Player player = new Player(name, faction, human);
        CharacterCard ambassador = new CharacterCard("amb_" + name, "Ambassador",
            "CHARACTER_" + faction, Rarity.FIXED, Loyalty.fromRace(faction), CardSet.PREMIERE,
            "x", "text", 3, 3, 3, 3, true);
        player.setAmbassador(ambassador);
        player.getInnerCircle().add(ambassador);
        return player;
    }

    private static GameState twoPlayerState(String humanName, Race humanRace,
                                            String rivalName, Race rivalRace,
                                            GamePhase phase) {
        Player human = player(humanName, humanRace, true);
        Player rival = player(rivalName, rivalRace, false);
        List<Player> players = new ArrayList<Player>();
        players.add(human);
        players.add(rival);
        GameState state = new GameState(players);
        human.setGameState(state);
        rival.setGameState(state);
        state.setPhase(phase);
        return state;
    }

    /** The conformance fixture id: its Event-parity effect grants +1 influence. */
    private static ContingencyCard fixtureContingency(String title) {
        return new ContingencyCard("event_merchandising_b5", title, "CHARACTER",
            Rarity.COMMON, Loyalty.ANY, CardSet.PREMIERE, "x", "fixture",
            "CHARACTER", "NARN", "WHEN_REVEALED_FIXTURE");
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
     * Open the human's action turn.
     *
     * B5-2505, first run: every "the button should be lit" check failed while
     * the negative ones passed, and the suite then died with an NPE inside
     * {@code processAction} because {@code pendingHumanAction} was still null
     * — the click had submitted nothing. The cause was this fixture, not the
     * control: {@code refresh()} computes {@code myTurn} as
     * {@code activePlayer == human && controller.isWaitingForHuman()}
     * (MainWindow.java:2211), and {@code isWaitingForHuman} is only set by
     * {@code waitForHumanAction()} inside the running game loop. With no loop
     * here, {@code actionTurn} was permanently false, so the button could
     * never light no matter how many contingencies were placed.
     *
     * Injecting the flag is the approach MainWindowLeadFleetControlTest and
     * MainWindowAgendaLifecycleControlTest already use, for the same reason.
     */
    private static void setWaitingForHuman(GameController ctrl, boolean v)
            throws Exception {
        Field f = GameController.class.getDeclaredField("waitingForHuman");
        f.setAccessible(true);
        f.set(ctrl, v);
    }

    /** A controller with the human's ACTION turn open. */
    private static GameController controllerOnHumanTurn(GameState state)
            throws Exception {
        GameController ctrl = controller(state);
        setWaitingForHuman(ctrl, true);
        return ctrl;
    }

    /**
     * Place a contingency under its host through the real dispatch
     * (GameController.java:815) so playedBy / placedUnder / hand
     * removal are the engine's own, not a hand-built lookalike.
     */
    private static void placeContingency(GameController controller, Player human,
            ContingencyCard contingency, Card host) throws Exception {
        human.resetActions();
        Method method = GameController.class.getDeclaredMethod(
            "processAction", Player.class, GameAction.class);
        method.setAccessible(true);
        method.invoke(controller, human,
            GameAction.playContingency(contingency, host));
    }

    private static void dispatchReveal(GameController controller, Player human,
            GameAction action) throws Exception {
        human.resetActions();
        Method method = GameController.class.getDeclaredMethod(
            "processAction", Player.class, GameAction.class);
        method.setAccessible(true);
        method.invoke(controller, human, action);
    }

    /**
     * submitHumanAction only QUEUES the action (GameController.java:1543);
     * the running loop drains it via waitForHumanAction(). There is no
     * loop here, so the queued pendingHumanAction is read directly -
     * it is what the listener submitted.
     */
    private static GameAction submittedAction(GameController controller)
            throws Exception {
        Field field = GameController.class.getDeclaredField("pendingHumanAction");
        field.setAccessible(true);
        return (GameAction) field.get(controller);
    }

    private static void clickOnEdt(final JButton button) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                button.doClick();
            }
        });
    }

    private static void selectOnEdt(final JComboBox<String> selector,
            final int index) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                selector.setSelectedIndex(index);
            }
        });
    }

    private static void testControlIsWired() throws Exception {
        System.out.println("  [CNUI] --- the control exists and is wired ---");
        GameState state = twoPlayerState("C2505WireHuman", Race.NARN,
            "C2505WireRival", Race.MINBARI, GamePhase.ACTION);
        MainWindow window = window(controller(state));
        JComboBox<String> selector
            = (JComboBox<String>) field(window, "contingencySelector");
        JButton button = (JButton) field(window, "revealContingencyButton");

        check("contingencySelector is constructed", selector != null);
        check("revealContingencyButton is constructed", button != null);
        check("revealContingencyButton is labelled Reveal Contingency",
            button != null && "Reveal Contingency".equals(button.getText()));
        check("revealContingencyButton carries a click handler",
            button != null && button.getActionListeners() != null
                && button.getActionListeners().length > 0);
        check("revealContingencyButton starts disabled before the first refresh",
            button != null && !button.isEnabled());
        check("contingencySelector starts disabled before the first refresh",
            selector != null && !selector.isEnabled());
        check("revealContingencyButton names its purpose in its tooltip",
            button != null && button.getToolTipText() != null
                && button.getToolTipText().contains("face-down contingency"));
        window.dispose();
    }

    private static void testSelectorListsPlacedContingencies() throws Exception {
        System.out.println("  [CNUI] --- the selector lists the placed contingencies ---");
        GameState state = twoPlayerState("C2505ListHuman", Race.NARN,
            "C2505ListRival", Race.MINBARI, GamePhase.ACTION);
        Player human = state.getPlayers().get(0);
        ContingencyCard first = fixtureContingency("First Fixture Contingency");
        ContingencyCard second = fixtureContingency("Second Fixture Contingency");
        human.addToHand(first);
        human.addToHand(second);
        GameController controller = controllerOnHumanTurn(state);
        placeContingency(controller, human, first, human.getAmbassador());
        placeContingency(controller, human, second, human.getAmbassador());

        MainWindow window = window(controller);
        refresh(window, state);
        JComboBox<String> selector
            = (JComboBox<String>) field(window, "contingencySelector");
        JButton button = (JButton) field(window, "revealContingencyButton");

        check("contingencySelector lists every placed contingency",
            selector.getItemCount() == 2);
        check("a listed contingency is labelled with its host",
            "First Fixture Contingency (under Ambassador)"
                .equals(selector.getItemAt(0)));
        check("the second placed contingency is listed with its host",
            "Second Fixture Contingency (under Ambassador)"
                .equals(selector.getItemAt(1)));
        check("revealContingencyButton ENABLES on the human ACTION turn with unrevealed placed contingencies",
            button.isEnabled());
        check("contingencySelector ENABLES when the human has placed contingencies",
            selector.isEnabled());
        window.dispose();
    }

    private static void testEngineGatesEnablement() throws Exception {
        System.out.println("  [CNUI] --- enablement is the engine's verdict ---");

        // Outside the ACTION phase: dark, even with contingencies placed.
        GameState drawState = twoPlayerState("C2505DrawHuman", Race.NARN,
            "C2505DrawRival", Race.MINBARI, GamePhase.DRAW);
        Player drawHuman = drawState.getPlayers().get(0);
        ContingencyCard drawCard = fixtureContingency("Draw Phase Contingency");
        drawHuman.addToHand(drawCard);
        GameController drawController = controller(drawState);
        placeContingency(drawController, drawHuman, drawCard,
            drawHuman.getAmbassador());
        MainWindow draw = window(drawController);
        refresh(draw, drawState);
        JButton drawButton = (JButton) field(draw, "revealContingencyButton");
        check("revealContingencyButton stays DISABLED outside the ACTION phase",
            !drawButton.isEnabled());
        draw.dispose();

        // Nothing placed: dark, and the selector degrades to "(none)".
        GameState bareState = twoPlayerState("C2505BareHuman", Race.NARN,
            "C2505BareRival", Race.CENTAURI, GamePhase.ACTION);
        MainWindow bare = window(controller(bareState));
        refresh(bare, bareState);
        JComboBox<String> bareSelector
            = (JComboBox<String>) field(bare, "contingencySelector");
        JButton bareButton = (JButton) field(bare, "revealContingencyButton");
        check("revealContingencyButton stays DISABLED when no contingency is placed",
            !bareButton.isEnabled());
        check("contingencySelector stays DISABLED when no contingency is placed",
            !bareSelector.isEnabled());
        check("contingencySelector degrades to a single (none) entry",
            bareSelector.getItemCount() == 1
                && "(none)".equals(bareSelector.getItemAt(0)));
        check("the tooltip names the empty state",
            "No contingencies placed.".equals(bareButton.getToolTipText()));
        bare.dispose();
    }

    private static void testSelectedIndexChoosesWhichContingency()
            throws Exception {
        System.out.println("  [CNUI] --- the selector chooses WHICH contingency is revealed ---");
        GameState state = twoPlayerState("C2505SubHuman", Race.NARN,
            "C2505SubRival", Race.MINBARI, GamePhase.ACTION);
        Player human = state.getPlayers().get(0);
        ContingencyCard first = fixtureContingency("First Fixture Contingency");
        ContingencyCard second = fixtureContingency("Second Fixture Contingency");
        human.addToHand(first);
        human.addToHand(second);
        GameController controller = controllerOnHumanTurn(state);
        placeContingency(controller, human, first, human.getAmbassador());
        placeContingency(controller, human, second, human.getAmbassador());

        MainWindow window = window(controller);
        refresh(window, state);
        JComboBox<String> selector
            = (JComboBox<String>) field(window, "contingencySelector");
        JButton button = (JButton) field(window, "revealContingencyButton");

        // The selector's default selection is index 0. Prove the second
        // card is the one that leaves when the human selects it - the
        // 2026-09-25 improvement over "always the first legal one".
        selectOnEdt(selector, 1);
        clickOnEdt(button);
        GameAction submitted = submittedAction(controller);
        check("selecting the second contingency submits a REVEAL_CONTINGENCY",
            submitted != null
                && submitted.getType() == GameAction.Type.REVEAL_CONTINGENCY);
        check("the submitted action carries the SELECTED contingency, not the first",
            submitted != null && submitted.getCard() == second);
        dispatchReveal(controller, human, submitted);
        check("the selected (second) contingency is revealed",
            second.isRevealed());
        check("the unselected first contingency stays face-down under its host",
            !first.isRevealed() && first.getPlacedUnder() == human.getAmbassador());

        // And the other direction: once the second is revealed, the
        // placed list holds only the first, so index 0 names it.
        selectOnEdt(selector, 0);
        clickOnEdt(button);
        GameAction submittedFirst = submittedAction(controller);
        check("after the first reveal, index 0 submits the remaining contingency",
            submittedFirst != null
                && submittedFirst.getType() == GameAction.Type.REVEAL_CONTINGENCY
                && submittedFirst.getCard() == first);
        dispatchReveal(controller, human, submittedFirst);
        check("the remaining contingency reveals too", first.isRevealed());
        window.dispose();
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("MAIN WINDOW CONTINGENCY REVEAL CONTROL TEST SKIPPED"
                + " (headless environment)");
            return;
        }
        System.out.println("=== MainWindow contingency reveal control regression ===");
        testControlIsWired();
        testSelectorListsPlacedContingencies();
        testEngineGatesEnablement();
        testSelectedIndexChoosesWhichContingency();
        System.out.println(failed == 0
            ? "MAIN WINDOW CONTINGENCY REVEAL CONTROL TEST PASSED (" + checks + " checks)"
            : "MAIN WINDOW CONTINGENCY REVEAL CONTROL TEST FAILED ("
                + failed + " of " + checks + " checks)");
        System.out.flush();
        System.err.flush();
        System.exit(failed == 0 ? 0 : 1);
    }
}
