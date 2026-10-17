package b5ccg.ui;
import b5ccg.model.enums.Loyalty;

import b5ccg.ai.AIPlayer;
import b5ccg.engine.GameController;
import b5ccg.engine.GameStateCallback;
import b5ccg.model.CharacterCard;
import b5ccg.model.Conflict;
import b5ccg.model.ConflictCard;
import b5ccg.model.GameState;
import b5ccg.model.Participation;
import b5ccg.model.Player;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.ConflictType;
import b5ccg.model.enums.Race;
import b5ccg.model.enums.GamePhase;
import b5ccg.model.enums.Rarity;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * B5-2577 -- regression for {@link ConflictJoinPickerPanel}.
 *
 * <p>Headless-safe on purpose, unlike the MainWindow*ControlTest classes:
 * the panel is a JPanel, so it constructs with no display and the three checks
 * below run in the compile.sh gate tier without a screen. What they pin is the
 * part of this row that could silently regress:
 *
 * <ol>
 *   <li>the panel lights only while the controller is collecting the human's
 *       side, and goes dark the moment a side is submitted;</li>
 *   <li>Support and Oppose submit the JOIN_CONFLICT_* action types and DO close
 *       the window;</li>
 *   <li>Pass submits and is <b>reported as not taken</b> while the engine's join
 *       window swallows it, with all three options left open — the assertion
 *       that the panel never claims a click the engine ignored.</li>
 * </ol>
 *
 * <p>Check 3 is a negative control on the panel, and it is written to fail if
 * someone "fixes" the panel by having Pass claim success unconditionally. If a
 * future engine/ change makes the join window accept PASS, check 3 flips and
 * this file needs updating in the same pass — which is the point: the change
 * that makes Pass real has to be visible in the test that says it was not.
 */
public class ConflictJoinPickerPanelTest {
    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [JOINPICK] " + label + ": " + (ok ? "PASS" : "FAIL"));
        if (!ok) failed++;
    }

    // ── fixture ───────────────────────────────────────────────────────────────

    private static Player player(String name, Race faction, boolean human) {
        Player p = new Player(name, faction, human);
        CharacterCard ambassador = new CharacterCard("amb_" + name, "Ambassador",
            "CHARACTER_" + faction, Rarity.FIXED, Loyalty.fromRace(faction), CardSet.PREMIERE,
            "x", "text", 3, 3, 3, 3, true);
        p.setAmbassador(ambassador);
        p.getInnerCircle().add(ambassador);
        return p;
    }

    private static ConflictCard conflictCard(String id, Participation part) {
        ConflictCard cc = new ConflictCard(id, "Join Picker " + id,
            "CONFLICT_DIPLOMACY", Rarity.COMMON, Loyalty.ANY, CardSet.PREMIERE,
            "x", "text", ConflictType.DIPLOMACY, 1);
        if (part != null) cc.setParticipation(part);
        return cc;
    }

    /** An open (unrestricted) conflict: passing means not participating. */
    private static GameState openState() {
        return buildState(conflictCard("jp_open", null));
    }

    /**
     * A conflict whose participation restriction compels every eligible player
     * onto a side, so the pass consequence text must say that declining does
     * not keep the human out. Participation's constructor is private, so the
     * restriction is built the way the loader builds it.
     */
    private static GameState compelledState() {
        return buildState(conflictCard("jp_compelled", mustTakeSide()));
    }

    private static Participation mustTakeSide() {
        return Participation.parse("{\"mustTakeSide\":true}");
    }

    private static GameState buildState(ConflictCard card) {
        Player human     = player("JoinPickerHuman", Race.NARN, true);
        Player initiator = player("JoinPickerInitiator", Race.MINBARI, false);
        Conflict conflict = new Conflict(card, initiator);
        List<Player> players = new ArrayList<Player>();
        players.add(human);
        players.add(initiator);
        GameState state = new GameState(players);
        human.setGameState(state);
        initiator.setGameState(state);
        state.setPhase(GamePhase.CONFLICT_RESOLUTION);
        state.setActiveConflict(conflict);
        return state;
    }

    private static GameController controller(GameState state) {
        return new GameController(state, new ArrayList<AIPlayer>(),
            new GameStateCallback() {
                public void accept(GameState s) {
                }
            });
    }

    private static Thread resolve(final GameController controller,
                                  final Throwable[] failure) {
        final Method method;
        try {
            method = GameController.class.getDeclaredMethod("resolveCurrentConflict");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        method.setAccessible(true);
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    method.invoke(controller);
                } catch (Throwable t) {
                    failure[0] = t;
                }
            }
        });
        t.start();
        return t;
    }

    private static boolean awaitJoin(GameController controller) throws Exception {
        long deadline = System.currentTimeMillis() + 3000L;
        while (!controller.isWaitingForHumanConflictJoin()
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        return controller.isWaitingForHumanConflictJoin();
    }

    private static void update(final ConflictJoinPickerPanel panel,
                               final GameState state,
                               final boolean joining) throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                panel.update(state, joining);
            }
        });
    }

    private static void click(final javax.swing.JButton button) throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                button.doClick();
            }
        });
    }

    private static String text(final javax.swing.JLabel label) throws Exception {
        final String[] out = new String[1];
        javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                out[0] = label.getText();
            }
        });
        return out[0];
    }

    private static void reap(Thread resolver) throws Exception {
        if (resolver.isAlive()) {
            resolver.interrupt();
            resolver.join(1000L);
        }
    }

    // ── checks ────────────────────────────────────────────────────────────────

    private static void testPanelLightsOnlyForTheLiveWindow() throws Exception {
        GameState state = openState();
        GameController controller = controller(state);
        ConflictJoinPickerPanel panel = new ConflictJoinPickerPanel(controller);

        update(panel, state, false);
        check("no window: all three options dark",
            !panel.supportButton().isEnabled()
            && !panel.opposeButton().isEnabled()
            && !panel.passButton().isEnabled());

        update(panel, state, true);
        check("open window: all three options lit",
            panel.supportButton().isEnabled()
            && panel.opposeButton().isEnabled()
            && panel.passButton().isEnabled());
        check("prompt names the human and the conflict",
            text(panel.promptLabel()).indexOf("JoinPickerHuman") >= 0
            && text(panel.promptLabel()).indexOf("jp_open") >= 0);
    }

    private static void testSupportClosesTheWindow() throws Exception {
        GameState state = openState();
        // Captured before the resolver runs: RulesEngine clears
        // state.activeConflict on resolution, so the assertion has to read the
        // Conflict itself rather than the slot that held it.
        Conflict conflict = state.getActiveConflict();
        GameController controller = controller(state);
        ConflictJoinPickerPanel panel = new ConflictJoinPickerPanel(controller);
        Throwable[] failure = new Throwable[1];
        Thread resolver = resolve(controller, failure);
        check("join window opens", awaitJoin(controller));
        update(panel, state, true);
        click(panel.supportButton());
        resolver.join(3000L);
        check("Support submits and closes the window",
            !resolver.isAlive() && failure[0] == null
            && !controller.isWaitingForHumanConflictJoin()
            && conflict.isSupporting(state.getHumanPlayer()));
        reap(resolver);
    }

    private static void testOpposeClosesTheWindow() throws Exception {
        GameState state = openState();
        Conflict conflict = state.getActiveConflict();
        GameController controller = controller(state);
        ConflictJoinPickerPanel panel = new ConflictJoinPickerPanel(controller);
        Throwable[] failure = new Throwable[1];
        Thread resolver = resolve(controller, failure);
        check("join window opens", awaitJoin(controller));
        update(panel, state, true);
        click(panel.opposeButton());
        resolver.join(3000L);
        check("Oppose submits and closes the window",
            !resolver.isAlive() && failure[0] == null
            && !controller.isWaitingForHumanConflictJoin()
            && conflict.isOpposing(state.getHumanPlayer()));
        reap(resolver);
    }

    private static void testPassIsReportedNotTaken() throws Exception {
        GameState state = openState();
        GameController controller = controller(state);
        ConflictJoinPickerPanel panel = new ConflictJoinPickerPanel(controller);
        Throwable[] failure = new Throwable[1];
        Thread resolver = resolve(controller, failure);
        check("join window opens", awaitJoin(controller));
        update(panel, state, true);
        click(panel.passButton());
        check("the engine's join window ignores PASS, so the window is still open",
            controller.isWaitingForHumanConflictJoin() && resolver.isAlive());
        check("panel reports the pass as NOT taken",
            panel.statusText().indexOf("NOT taken") >= 0);
        check("all three options stay open so the human is not stranded",
            panel.supportButton().isEnabled()
            && panel.opposeButton().isEnabled()
            && panel.passButton().isEnabled());
        // A side still ends the window, so the fixture is left resolvable.
        click(panel.opposeButton());
        resolver.join(3000L);
        check("a side after the refused pass still resolves",
            !resolver.isAlive() && failure[0] == null
            && !controller.isWaitingForHumanConflictJoin());
        reap(resolver);
    }

    private static void testPassConsequenceReadsParticipation() {
        check("open conflict: passing means not participating",
            ConflictJoinPickerPanel.passConsequence(
                new Conflict(conflictCard("jp_c1", null), player("I", Race.NARN, false)))
                .indexOf("do not participate") >= 0);
        check("must-take-side conflict: passing still puts the human in, opposing",
            ConflictJoinPickerPanel.passConsequence(
                new Conflict(conflictCard("jp_c2", mustTakeSide()),
                             player("I", Race.NARN, false)))
                .indexOf("opposing") >= 0);
        GameState compelled = compelledState();
        check("a compelled conflict still reads as restricted and mandates a side",
            compelled.getActiveConflict().getCard().getParticipation().isRestricted()
            && compelled.getActiveConflict().getCard().getParticipation().isMustTakeSide());
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== ConflictJoinPickerPanel regression ===");
        testPanelLightsOnlyForTheLiveWindow();
        testSupportClosesTheWindow();
        testOpposeClosesTheWindow();
        testPassIsReportedNotTaken();
        testPassConsequenceReadsParticipation();
        System.out.println(failed == 0
            ? "CONFLICT JOIN PICKER PANEL TEST PASSED (" + checks + " checks)"
            : "CONFLICT JOIN PICKER PANEL TEST FAILED ("
                + failed + " of " + checks + " checks)");
        System.out.flush();
        System.err.flush();
        System.exit(failed == 0 ? 0 : 1);
    }
}