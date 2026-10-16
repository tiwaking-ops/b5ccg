package b5ccg.ui;

import b5ccg.ai.AIPlayer;
import b5ccg.engine.GameController;
import b5ccg.engine.GameStateCallback;
import b5ccg.engine.RulesEngine;
import b5ccg.model.AgendaCard;
import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.Deck;
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
 * B5-2506 - standing assertion for the agenda lifecycle UI controls.
 *
 * <p>The row's premise ("discardAgendaButton, replaceAgendaButton and
 * revealAgendaButton are declared but have no click handlers") was already
 * false when it was seeded. The whole chain landed under B5-0380:
 * MainWindow.java:579/:591/:606 construct all three buttons with
 * ActionListeners that submit GameAction.discardAgenda /
 * replaceAgenda / revealAgenda through controller.submitHumanAction, and
 * refreshAgendaControls (MainWindow.java:2364) renders their enablement from
 * the ENGINE predicates rather than re-deriving them. Nothing asserted that
 * wiring, which is exactly how a false premise survives to seeding: a handler
 * deleted today would leave every suite green, because
 * MainWindowToolbarLayoutTest only names the controls in its dialog-home
 * list, which asserts REACHABILITY. This class is that standing assertion.
 *
 * <p>It locks four things:
 * (1) the three controls exist, carry their labels, carry click listeners, and
 *     all start disabled before the first refresh;
 * (2) enablement is the ENGINE's verdict - discard lights only for a minor
 *     agenda, reveal only for a face-down one, replace only for a selected
 *     legal replacement (RulesEngine.canDiscardAgenda / canRevealAgenda /
 *     canReplaceAgenda, RulesEngine.java:634/:663/:646);
 * (3) the negatives carry the contract - all three stay dark outside the
 *     human's ACTION turn, a Major agenda darkens and RELABELS discard,
 *     and a consumed selection darkens replace again;
 * (4) the submitted actions are EFFECTIVE - the queued action carries the
 *     right type and payload, and dispatching it really discards, reveals, or
 *     replaces with the IC leader rotating (GameController.java:943/:970/:1012).
 *
 * <p>Exit codes: 0 all checks passed, 1 a check failed. A headless host skips
 * with exit 0, matching MainWindowForfeitControlTest and
 * MainWindowLeadFleetControlTest.
 */
public class MainWindowAgendaLifecycleControlTest {
    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [AGUI] " + label + ": " + (ok ? "PASS" : "FAIL"));
        if (!ok) failed++;
    }

    private static Player player(String name, Faction faction, boolean human) {
        Player player = new Player(name, faction, human);
        CharacterCard ambassador = new CharacterCard("amb_" + name, "Ambassador",
            "CHARACTER_" + faction, Rarity.FIXED, faction, CardSet.PREMIERE,
            "x", "text", 3, 3, 3, 3, true);
        player.setAmbassador(ambassador);
        player.getInnerCircle().add(ambassador);
        // The DISCARD_AGENDA / REVEAL_AGENDA branches call p.getDeck().discard
        // without the null guard Player's own draw path uses
        // (Player.java:270/:279), so a fixture player needs a deck or the
        // dispatch half of this test NPEs inside the engine before it can be
        // asserted. Recorded as an observation in the B5-2506 report; the fix
        // is an engine row, not this one.
        player.setDeck(new Deck(new ArrayList<Card>()));
        return player;
    }

    /** An agenda fixture. winConditionKey null = no win condition (B5-2488). */
    private static AgendaCard agenda(String id, String title, Faction faction,
                                     boolean major) {
        return new AgendaCard(id, title, "AGENDA_" + faction, Rarity.COMMON,
            faction, CardSet.PREMIERE, "x", "fixture text", major, null);
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
        // B5-0372: the ACTION-phase loop grants one action per player; give the
        // human their full action so no running loop is needed here.
        human.resetActions();
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

    private static void select(final MainWindow window, final Card card)
            throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Method method = MainWindow.class.getDeclaredMethod(
                        "applyCardSelection", Card.class, boolean.class);
                    method.setAccessible(true);
                    method.invoke(window, card, Boolean.TRUE);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        });
    }

    private static void click(final JButton button) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                button.doClick();
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
     * Set the controller's waitingForHuman flag so the UI treats the human as
     * having an open action turn. In normal play waitForHumanAction() sets it
     * inside the running loop (GameController.java:1598); a harness has no
     * loop, so inject it - the same convention as
     * MainWindowLeadFleetControlTest.setWaitingForHuman.
     */
    private static void setWaitingForHuman(GameController ctrl, boolean v)
            throws Exception {
        Field f = GameController.class.getDeclaredField("waitingForHuman");
        f.setAccessible(true);
        f.set(ctrl, v);
    }

    private static RulesEngine rules(GameController ctrl) throws Exception {
        Field f = GameController.class.getDeclaredField("rules");
        f.setAccessible(true);
        return (RulesEngine) f.get(ctrl);
    }

    /**
     * submitHumanAction only QUEUES the action; a running loop drains it via
     * waitForHumanAction() and dispatches into processAction. There is no loop
     * here, so the queued pendingHumanAction is read directly - it is what the
     * listener submitted - and processAction is driven reflectively where the
     * effect lives.
     */
    private static GameAction submittedAction(GameController controller)
            throws Exception {
        Field f = GameController.class.getDeclaredField("pendingHumanAction");
        f.setAccessible(true);
        return (GameAction) f.get(controller);
    }

    private static void dispatch(GameController controller, Player human,
                                 GameAction action) throws Exception {
        human.resetActions();
        Method method = GameController.class.getDeclaredMethod(
            "processAction", Player.class, GameAction.class);
        method.setAccessible(true);
        method.invoke(controller, human, action);
    }

    /**
     * Dispatch what the listener queued, or record the failure and stop.
     *
     * <p>A deleted handler leaves pendingHumanAction null, and driving
     * processAction with a null action throws inside the engine: the run dies
     * with a stack trace instead of a verdict. The measured mutant (the reveal
     * listener neutered to {@code if (false)}) did exactly that, so the guard
     * is there to make a broken handler a CLEAN red - one FAIL line and exit 1
     * - which is the shape a gate has to have to be read as evidence.
     */
    private static boolean dispatchSubmitted(GameController controller,
                                             Player human, GameAction submitted,
                                             String label) throws Exception {
        if (submitted == null) {
            check(label + ": nothing was queued to dispatch", false);
            return false;
        }
        dispatch(controller, human, submitted);
        return true;
    }

    private static JButton discard(MainWindow w) throws Exception {
        return (JButton) field(w, "discardAgendaButton");
    }

    private static JButton replace(MainWindow w) throws Exception {
        return (JButton) field(w, "replaceAgendaButton");
    }

    private static JButton reveal(MainWindow w) throws Exception {
        return (JButton) field(w, "revealAgendaButton");
    }

    /** The first unrotated Inner Circle character - what findUnrotatedIC picks. */
    private static CharacterCard unrotatedIc(Player p) {
        for (CharacterCard ch : p.getInnerCircle()) {
            if (!ch.isRotated()) return ch;
        }
        return null;
    }

    private static void testControlIsWired() throws Exception {
        System.out.println("  [AGUI] --- the agenda controls exist and are wired ---");
        GameState state = twoPlayerState("AgWireH", Faction.NARN,
            "AgWireR", Faction.MINBARI, GamePhase.ACTION);
        MainWindow w = window(controller(state));
        JButton discardButton = discard(w);
        JButton replaceButton = replace(w);
        JButton revealButton = reveal(w);

        check("discardAgendaButton is constructed", discardButton != null);
        check("replaceAgendaButton is constructed", replaceButton != null);
        check("revealAgendaButton is constructed", revealButton != null);
        check("discardAgendaButton is labelled Discard Agenda",
            "Discard Agenda".equals(discardButton.getText()));
        check("replaceAgendaButton is labelled Replace Agenda",
            "Replace Agenda".equals(replaceButton.getText()));
        check("revealAgendaButton is labelled Reveal Agenda",
            "Reveal Agenda".equals(revealButton.getText()));
        check("discardAgendaButton carries a click handler",
            discardButton.getActionListeners() != null
                && discardButton.getActionListeners().length > 0);
        check("replaceAgendaButton carries a click handler",
            replaceButton.getActionListeners() != null
                && replaceButton.getActionListeners().length > 0);
        check("revealAgendaButton carries a click handler",
            revealButton.getActionListeners() != null
                && revealButton.getActionListeners().length > 0);
        check("all three agenda controls start disabled before the first refresh",
            !discardButton.isEnabled() && !replaceButton.isEnabled()
                && !revealButton.isEnabled());
        w.dispose();
    }

    private static void testEngineGatesEnablement() throws Exception {
        System.out.println("  [AGUI] --- enablement is the engine's verdict ---");

        // A face-up MINOR agenda on the human ACTION turn: discard is legal,
        // reveal is not (the agenda is not face-down), replace needs a selection.
        GameState state = twoPlayerState("AgGateH", Faction.NARN,
            "AgGateR", Faction.MINBARI, GamePhase.ACTION);
        Player human = state.getHumanPlayer();
        AgendaCard minor = agenda("ag_minor", "Minor Agenda", Faction.NARN, false);
        human.setAgenda(minor);
        AgendaCard replacement = agenda("ag_replacement", "Replacement Agenda",
            Faction.NARN, false);
        human.addToHand(replacement);

        GameController ctrl = controller(state);
        setWaitingForHuman(ctrl, true);
        MainWindow w = window(ctrl);
        JButton discardButton = discard(w);
        JButton replaceButton = replace(w);
        JButton revealButton = reveal(w);
        refresh(w, state);

        RulesEngine engine = rules(ctrl);
        check("canDiscardAgenda is true for the installed minor agenda",
            engine != null && engine.canDiscardAgenda(human));
        check("canRevealAgenda is false for a face-up agenda",
            engine != null && !engine.canRevealAgenda(human));
        check("discardAgendaButton ENABLES for a minor agenda on the ACTION turn",
            discardButton.isEnabled());
        check("revealAgendaButton stays DISABLED for a face-up agenda",
            !revealButton.isEnabled());
        check("replaceAgendaButton stays DISABLED with no agenda selected",
            !replaceButton.isEnabled());

        // Selecting a legal replacement agenda is what lights Replace.
        select(w, replacement);
        refresh(w, state);
        check("canReplaceAgenda is true for the selected in-hand agenda",
            engine != null
                && engine.canReplaceAgenda(human, replacement, unrotatedIc(human)));
        check("replaceAgendaButton ENABLES once a legal replacement is selected",
            replaceButton.isEnabled());
        w.dispose();

        // A face-down agenda: reveal becomes legal. Discard stays legal too,
        // because the engine's canDiscardAgenda asks only about Major-ness.
        GameState hiddenState = twoPlayerState("AgHidH", Faction.NARN,
            "AgHidR", Faction.MINBARI, GamePhase.ACTION);
        Player hiddenHuman = hiddenState.getHumanPlayer();
        AgendaCard hidden = agenda("ag_hidden", "Hidden Agenda", Faction.NARN, false);
        hidden.setFaceDown(true);
        hiddenHuman.setAgenda(hidden);
        GameController hiddenCtrl = controller(hiddenState);
        setWaitingForHuman(hiddenCtrl, true);
        MainWindow hw = window(hiddenCtrl);
        refresh(hw, hiddenState);
        check("revealAgendaButton ENABLES for a face-down agenda",
            reveal(hw).isEnabled());
        check("discardAgendaButton is still lit for a hidden MINOR agenda",
            discard(hw).isEnabled());
        hw.dispose();

        // Negative: a MAJOR agenda cannot be discarded - the control goes dark
        // AND relabels, so the disabled button still explains itself.
        GameState majorState = twoPlayerState("AgMajH", Faction.NARN,
            "AgMajR", Faction.MINBARI, GamePhase.ACTION);
        Player majorHuman = majorState.getHumanPlayer();
        majorHuman.setAgenda(agenda("ag_major", "Major Agenda", Faction.NARN, true));
        GameController majorCtrl = controller(majorState);
        setWaitingForHuman(majorCtrl, true);
        MainWindow mw = window(majorCtrl);
        refresh(mw, majorState);
        JButton majorDiscard = discard(mw);
        check("canDiscardAgenda is false for a Major agenda",
            rules(majorCtrl) != null && !rules(majorCtrl).canDiscardAgenda(majorHuman));
        check("discardAgendaButton DISABLES for a Major agenda",
            !majorDiscard.isEnabled());
        check("discardAgendaButton RELABELS itself for a Major agenda",
            "Discard Agenda (Major)".equals(majorDiscard.getText()));
        check("the disabled Major discard control names the replacement path",
            majorDiscard.getToolTipText() != null
                && majorDiscard.getToolTipText().contains("Replace Agenda"));
        mw.dispose();
    }

    private static void testPhaseGatesEnablement() throws Exception {
        System.out.println("  [AGUI] --- outside the human ACTION turn everything is dark ---");

        // DRAW phase with a legal minor agenda installed and a legal
        // replacement selected: only the phase can turn these dark, so this is
        // the negative that proves the UI renders the engine's verdict rather
        // than the agenda state alone.
        GameState drawState = twoPlayerState("AgDrawH", Faction.NARN,
            "AgDrawR", Faction.MINBARI, GamePhase.DRAW);
        Player drawHuman = drawState.getHumanPlayer();
        drawHuman.setAgenda(agenda("ag_draw_minor", "Draw Minor Agenda",
            Faction.NARN, false));
        AgendaCard drawReplacement = agenda("ag_draw_repl", "Draw Replacement",
            Faction.NARN, false);
        drawHuman.addToHand(drawReplacement);
        GameController drawCtrl = controller(drawState);
        setWaitingForHuman(drawCtrl, true);
        MainWindow dw = window(drawCtrl);
        refresh(dw, drawState);
        select(dw, drawReplacement);
        refresh(dw, drawState);
        check("the engine WOULD allow the discard outside ACTION phase",
            rules(drawCtrl) != null && rules(drawCtrl).canDiscardAgenda(drawHuman));
        check("discardAgendaButton stays DISABLED in DRAW phase",
            !discard(dw).isEnabled());
        check("replaceAgendaButton stays DISABLED in DRAW phase",
            !replace(dw).isEnabled());
        check("revealAgendaButton stays DISABLED in DRAW phase",
            !reveal(dw).isEnabled());
        dw.dispose();

        // Negative: not the human's turn at all (waitingForHuman false).
        GameState idleState = twoPlayerState("AgIdleH", Faction.NARN,
            "AgIdleR", Faction.MINBARI, GamePhase.ACTION);
        idleState.getHumanPlayer().setAgenda(
            agenda("ag_idle", "Idle Agenda", Faction.NARN, false));
        GameController idleCtrl = controller(idleState);
        MainWindow iw = window(idleCtrl);
        refresh(iw, idleState);
        check("discardAgendaButton stays DISABLED when the seat is not asked",
            !discard(iw).isEnabled());
        iw.dispose();

        // Negative: nothing installed - all three predicates are false.
        GameState bareState = twoPlayerState("AgBareH", Faction.NARN,
            "AgBareR", Faction.CENTAURI, GamePhase.ACTION);
        GameController bareCtrl = controller(bareState);
        setWaitingForHuman(bareCtrl, true);
        MainWindow bw = window(bareCtrl);
        refresh(bw, bareState);
        check("discardAgendaButton stays DISABLED with no agenda installed",
            !discard(bw).isEnabled());
        check("replaceAgendaButton stays DISABLED with no agenda installed",
            !replace(bw).isEnabled());
        check("revealAgendaButton stays DISABLED with no agenda installed",
            !reveal(bw).isEnabled());
        bw.dispose();
    }

    private static void testSubmittedDiscardDiscards() throws Exception {
        System.out.println("  [AGUI] --- the submitted DISCARD_AGENDA really discards ---");
        GameState state = twoPlayerState("AgDisH", Faction.NARN,
            "AgDisR", Faction.MINBARI, GamePhase.ACTION);
        Player human = state.getHumanPlayer();
        AgendaCard minor = agenda("ag_dis_minor", "Discarded Agenda", Faction.NARN, false);
        human.setAgenda(minor);
        GameController ctrl = controller(state);
        setWaitingForHuman(ctrl, true);
        MainWindow w = window(ctrl);
        refresh(w, state);
        click(discard(w));

        GameAction submitted = submittedAction(ctrl);
        check("clicking Discard submits a DISCARD_AGENDA",
            submitted != null
                && submitted.getType() == GameAction.Type.DISCARD_AGENDA);
        check("the submitted action carries the INSTALLED agenda",
            submitted != null && submitted.getCard() == minor);
        check("the agenda is still installed before dispatch",
            human.getAgenda() == minor);

        if (!dispatchSubmitted(ctrl, human, submitted, "DISCARD_AGENDA")) {
            w.dispose();
            return;
        }
        check("dispatch clears the agenda slot", human.getAgenda() == null);
        check("dispatch puts the discarded agenda in the discard pile",
            human.getDeck().getDiscardPile().contains(minor));
        refresh(w, state);
        check("discardAgendaButton goes dark once the slot is empty",
            !discard(w).isEnabled());
        w.dispose();
    }

    private static void testSubmittedRevealReveals() throws Exception {
        System.out.println("  [AGUI] --- the submitted REVEAL_AGENDA really reveals ---");
        GameState state = twoPlayerState("AgRevH", Faction.NARN,
            "AgRevR", Faction.MINBARI, GamePhase.ACTION);
        Player human = state.getHumanPlayer();
        AgendaCard hidden = agenda("ag_rev_hidden", "Revealed Agenda", Faction.NARN, false);
        hidden.setFaceDown(true);
        human.setAgenda(hidden);
        GameController ctrl = controller(state);
        setWaitingForHuman(ctrl, true);
        MainWindow w = window(ctrl);
        refresh(w, state);
        click(reveal(w));

        GameAction submitted = submittedAction(ctrl);
        check("clicking Reveal submits a REVEAL_AGENDA",
            submitted != null
                && submitted.getType() == GameAction.Type.REVEAL_AGENDA);
        check("the submitted action carries the face-down agenda",
            submitted != null && submitted.getCard() == hidden);
        check("the agenda is still face-down before dispatch", hidden.isFaceDown());

        if (!dispatchSubmitted(ctrl, human, submitted, "REVEAL_AGENDA")) {
            w.dispose();
            return;
        }
        check("dispatch turns the agenda face up", !hidden.isFaceDown());
        check("the revealed agenda stays installed", human.getAgenda() == hidden);
        refresh(w, state);
        check("revealAgendaButton goes dark once the agenda is face up",
            !reveal(w).isEnabled());
        w.dispose();
    }

    private static void testSubmittedReplaceReplaces() throws Exception {
        System.out.println("  [AGUI] --- the submitted REPLACE_AGENDA really replaces ---");
        GameState state = twoPlayerState("AgRepH", Faction.NARN,
            "AgRepR", Faction.MINBARI, GamePhase.ACTION);
        Player human = state.getHumanPlayer();
        AgendaCard current = agenda("ag_rep_current", "Current Agenda", Faction.NARN, false);
        human.setAgenda(current);
        AgendaCard replacement = agenda("ag_rep_new", "New Agenda", Faction.NARN, false);
        human.addToHand(replacement);
        CharacterCard leader = unrotatedIc(human);

        GameController ctrl = controller(state);
        setWaitingForHuman(ctrl, true);
        MainWindow w = window(ctrl);
        refresh(w, state);
        select(w, replacement);
        refresh(w, state);
        click(replace(w));

        GameAction submitted = submittedAction(ctrl);
        check("clicking Replace submits a REPLACE_AGENDA",
            submitted != null
                && submitted.getType() == GameAction.Type.REPLACE_AGENDA);
        check("the submitted action carries the SELECTED agenda",
            submitted != null && submitted.getCard() == replacement);
        check("the submitted action carries the unrotated Inner Circle leader",
            submitted != null && submitted.getLeader() == leader);
        check("the leader has not rotated before dispatch", !leader.isRotated());
        check("the selected agenda is still in hand before dispatch",
            human.getHand().contains(replacement));

        if (!dispatchSubmitted(ctrl, human, submitted, "REPLACE_AGENDA")) {
            w.dispose();
            return;
        }
        check("dispatch rotates the Inner Circle leader", leader.isRotated());
        check("dispatch installs the new agenda", human.getAgenda() == replacement);
        check("dispatch removes the new agenda from hand",
            !human.getHand().contains(replacement));
        check("the replaced agenda is no longer installed", human.getAgenda() != current);
        refresh(w, state);
        check("replaceAgendaButton goes dark again once the selection is consumed",
            !replace(w).isEnabled());
        w.dispose();
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("MAIN WINDOW AGENDA LIFECYCLE CONTROL TEST SKIPPED"
                + " (headless environment)");
            return;
        }
        System.out.println("=== MainWindow agenda lifecycle control regression ===");
        testControlIsWired();
        testEngineGatesEnablement();
        testPhaseGatesEnablement();
        testSubmittedDiscardDiscards();
        testSubmittedRevealReveals();
        testSubmittedReplaceReplaces();
        System.out.println(failed == 0
            ? "MAIN WINDOW AGENDA LIFECYCLE CONTROL TEST PASSED (" + checks + " checks)"
            : "MAIN WINDOW AGENDA LIFECYCLE CONTROL TEST FAILED ("
                + failed + " of " + checks + " checks)");
        System.out.flush();
        System.err.flush();
        System.exit(failed == 0 ? 0 : 1);
    }
}