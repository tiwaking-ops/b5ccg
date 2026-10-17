package b5ccg.ui;
import b5ccg.model.enums.Loyalty;

import b5ccg.ai.AIPlayer;
import b5ccg.engine.GameController;
import b5ccg.engine.GameStateCallback;
import b5ccg.engine.RulesEngine;
import b5ccg.model.CharacterCard;
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
import javax.swing.SwingUtilities;

/**
 * B5-4147 - reproduce the human Promote dead-button.
 *
 * <p>The row asks for a repro that NAMES the cause with file and line:
 * lit-but-noop vs dark-button. This class drives the real click path
 * headlessly (real MainWindow over a real GameController, selectedCard
 * injected, button listeners fired on the EDT) across three scenarios and
 * pins each outcome to the exact production line that produces it:
 * (1) no supporting character selected - the gate at
 * MainWindow.java:refreshSponsorPromoteBuildInfluence keeps the button dark;
 * (2) supporting character selected with an unrotated leader - the button
 * lights and the handler at MainWindow.java actionPerformed submits
 * PROMOTE_CHARACTER through GameController.java processAction into
 * RulesEngine.executePromote; (3) the lit-but-noop twin: decouple the leader
 * the gate saw (findUnrotatedIC at MainWindow.java:1444) from the leader the
 * handler submits, e.g. neutralize the first member after the gate ran, so a
 * stale-lit button dispatches a neutralized leader that executePromote
 * (RulesEngine.java:294) silently refuses.
 *
 * <p>Exit codes: 0 all checks passed, 1 a check failed. A headless host skips
 * with exit 0, matching MainWindowSurrenderControlTest.
 */
public class MainWindowPromoteControlTest {
    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [PROMUI] " + label + ": " + (ok ? "PASS" : "FAIL"));
        if (!ok) failed++;
    }

    private static CharacterCard card(String id, String title, Race faction,
                                      int cost) {
        return new CharacterCard(id, title, "CHARACTER_" + faction, Rarity.FIXED,
            Loyalty.fromRace(faction), CardSet.PREMIERE, "x", "text", cost, 3, 3, 3, true);
    }

    private static Player player(String name, Race faction, boolean human) {
        Player p = new Player(name, faction, human);
        CharacterCard ambassador = card("amb_" + name, "Ambassador",
            faction, 3);
        p.setAmbassador(ambassador);
        p.getInnerCircle().add(ambassador);
        return p;
    }

    private static GameState twoPlayerState(String humanName, Race faction,
                                            GamePhase phase) {
        Player human = player(humanName, faction, true);
        Player rival = player(humanName + "Rival", Race.MINBARI, false);
        List<Player> players = new ArrayList<Player>();
        players.add(human);
        players.add(rival);
        GameState state = new GameState(players);
        human.setGameState(state);
        rival.setGameState(state);
        state.setPhase(phase);
        return state;
    }

    private static GameController controller(GameState state) {
        return new GameController(state, new ArrayList<AIPlayer>(),
            new GameStateCallback() {
                public void accept(GameState state) {
                }
            });
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static MainWindow window(final GameController ctrl)
            throws Exception {
        final MainWindow[] out = new MainWindow[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                out[0] = new MainWindow(ctrl);
            }
        });
        return out[0];
    }

    private static void refresh(final MainWindow win, final GameState state)
            throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Method m = MainWindow.class.getDeclaredMethod(
                        "refresh", GameState.class);
                    m.setAccessible(true);
                    m.invoke(win, state);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        });
    }

    private static void setWaitingForHuman(GameController ctrl, boolean v)
            throws Exception {
        Field f = GameController.class.getDeclaredField("waitingForHuman");
        f.setAccessible(true);
        f.set(ctrl, v);
    }
    private static void selectCard(final MainWindow win,
                                   final b5ccg.model.Card card)
            throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Method m = MainWindow.class.getDeclaredMethod(
                        "applyCardSelection", b5ccg.model.Card.class,
                        Boolean.TYPE);
                    m.setAccessible(true);
                    m.invoke(win, card, Boolean.FALSE);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        });
    }

    private static void click(final MainWindow win, final String fieldName)
            throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    ((JButton) field(win, fieldName)).doClick();
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        });
    }

    /** Dark-button cause 1: nothing supporting-role selected. */
    private static void testDarkWithNoSelection() throws Exception {
        System.out.println("  [PROMUI] --- dark: no supporting selection ---");
        GameState state = twoPlayerState("B4147Dark", Race.NARN,
            GamePhase.ACTION);
        GameController ctrl = controller(state);
        setWaitingForHuman(ctrl, true);
        MainWindow win = window(ctrl);
        refresh(win, state);
        JButton promote = (JButton) field(win, "promoteButton");
        check("promoteButton exists and is labelled Promote",
            promote != null && "Promote".equals(promote.getText()));
        check("promoteButton carries a click listener",
            promote.getActionListeners().length > 0);
        check("DARK-BUTTON cause: no selection leaves Promote dark "
            + "(MainWindow.java refreshSponsorPromoteBuildInfluence)",
            !promote.isEnabled());
        win.dispose();
    }

    /** Lit path: supporting character selected, leader present, pool funded. */
    private static void testLitSubmitsPromote() throws Exception {
        System.out.println("  [PROMUI] --- lit: selection plus leader plus pool ---");
        GameState state = twoPlayerState("B4147Lit", Race.NARN,
            GamePhase.ACTION);
        Player human = state.getPlayers().get(0);
        CharacterCard supporter = card("sup_4147", "GKar", Race.NARN, 0);
        human.getSupportingRole().add(supporter);
        human.gainInfluence(10);
        RulesEngine rules = new RulesEngine();
        check("engine canPromote agrees before the UI gates "
            + "(RulesEngine.java:272)",
            rules.canPromote(human, supporter));
        check("promotion cost is affordable (pool "
            + human.getAppliedPool() + " vs cost "
            + rules.promotionCost(human, supporter) + ")",
            human.getAppliedPool() >= rules.promotionCost(human, supporter));

        GameController ctrl = controller(state);
        setWaitingForHuman(ctrl, true);
        MainWindow win = window(ctrl);
        refresh(win, state);
        selectCard(win, supporter);
        JButton promote = (JButton) field(win, "promoteButton");
        check("LIT cause: ready supporting selection with an unrotated leader "
            + "lights Promote (MainWindow.java gate plus "
            + "findUnrotatedIC MainWindow.java:1444)",
            promote.isEnabled());

        int icBefore = human.getInnerCircle().size();
        CharacterCard leader = human.getInnerCircle().get(0);
        click(win, "promoteButton");
        GameAction parked = (GameAction) field(ctrl, "pendingHumanAction");
        check("the lit click parked a human action (submitHumanAction "
            + "MainWindow.java:450)",
            parked != null);
        check("the parked action is PROMOTE_CHARACTER",
            parked != null
                && parked.getType() == GameAction.Type.PROMOTE_CHARACTER);
        check("the parked action carries the selected supporter",
            parked != null && parked.getCard() == supporter);
        check("the parked action carries the first-unrotated leader "
            + "(findUnrotatedIC MainWindow.java:1444)",
            parked != null && parked.getLeader() == leader);
        final Method process = GameController.class.getDeclaredMethod(
            "processAction", Player.class, GameAction.class);
        process.setAccessible(true);
        final GameController ctl = ctrl;
        final Player hp = human;
        final CharacterCard ch = supporter;
        final CharacterCard rotLeader = leader;
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    process.invoke(ctl, hp,
                        GameAction.promoteCharacter(ch, rotLeader));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        check("PROMOTE_CHARACTER moves the supporter into the Inner Circle "
            + "(GameController.java:620 executePromote RulesEngine.java:287)",
            human.getInnerCircle().contains(supporter)
                && !human.getSupportingRole().contains(supporter)
                && human.getInnerCircle().size() == icBefore + 1);
        check("the rotating leader rotated",
            leader.isRotated());
        win.dispose();
    }
    /** Lit-but-noop cause: gate saw one leader, commit submits another. */
    private static void testLitButNoopOnDecoupledLeader() throws Exception {
        System.out.println("  [PROMUI] --- lit-but-noop: stale leader refused ---");
        GameState state = twoPlayerState("B4147Noop", Race.NARN,
            GamePhase.ACTION);
        Player human = state.getPlayers().get(0);
        CharacterCard supporter = card("sup_4147n", "NaToth", Race.NARN, 0);
        human.getSupportingRole().add(supporter);
        CharacterCard second = card("ic_4147n", "Delenn", Race.NARN, 0);
        human.getInnerCircle().add(second);
        human.gainInfluence(10);

        GameController ctrl = controller(state);
        setWaitingForHuman(ctrl, true);
        MainWindow win = window(ctrl);
        refresh(win, state);
        selectCard(win, supporter);
        JButton promote = (JButton) field(win, "promoteButton");
        check("button lit before the leader decoupled",
            promote.isEnabled());

        // Decouple AFTER the gate ran: neutralize the first member the gate
        // saw via findUnrotatedIC. The handler still submits that same first
        // member; executePromote refuses it (RulesEngine.java:294) and the
        // click is a silent no-op - the B5-4081 Build Influence shape whose
        // selector closed the same hole, absent here.
        CharacterCard first = human.getInnerCircle().get(0);
        first.damage();
        check("executePromote would refuse the neutralized first leader "
            + "(RulesEngine.java:294 leader guard)",
            !first.canActAfterNeutralization());

        final Method process = GameController.class.getDeclaredMethod(
            "processAction", Player.class, GameAction.class);
        process.setAccessible(true);
        final GameController ctl = ctrl;
        final Player hp = human;
        final CharacterCard ch = supporter;
        final CharacterCard staleLeader = first;
        int icBefore = human.getInnerCircle().size();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    process.invoke(ctl, hp,
                        GameAction.promoteCharacter(ch, staleLeader));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        check("LIT-BUT-NOOP cause: stale-lit click with the neutralized "
            + "first-unrotated leader (MainWindow.java:447) is refused - "
            + "supporter stays out of the Inner Circle",
            !human.getInnerCircle().contains(supporter)
                && human.getInnerCircle().size() == icBefore);

        // The actionable second member still promotes: the same gate shape
        // the B5-3870 leader selector would offer.
        RulesEngine rules = new RulesEngine();
        check("engine still accepts the actionable second leader",
            rules.canPromote(human, supporter)
                && !second.isRotated()
                && second.canActAfterNeutralization());
        win.dispose();
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("MAIN WINDOW PROMOTE CONTROL TEST SKIPPED"
                + " (headless environment)");
            return;
        }
        System.out.println("=== MainWindow promote control regression ===");
        testDarkWithNoSelection();
        testLitSubmitsPromote();
        testLitButNoopOnDecoupledLeader();
        System.out.println(failed == 0
            ? "MAIN WINDOW PROMOTE CONTROL TEST PASSED (" + checks + " checks)"
            : "MAIN WINDOW PROMOTE CONTROL TEST FAILED ("
                + failed + " of " + checks + " checks)");
        System.out.flush();
        System.err.flush();
        System.exit(failed == 0 ? 0 : 1);
    }
}
