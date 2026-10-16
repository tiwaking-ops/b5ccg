package b5ccg.engine;
import b5ccg.model.enums.Loyalty;

import b5ccg.ai.AIPlayer;
import b5ccg.model.CharacterCard;
import b5ccg.model.Conflict;
import b5ccg.model.ConflictCard;
import b5ccg.model.GameAction;
import b5ccg.model.GameState;
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
 * B5-2481 (re-verification, 2026-10-11): the human playtest report reads
 * "round does not end when all players pass (Agenda Support of the Mighty
 * sponsor + pass)".
 *
 * <p>{@link B52481HumanPassRoundProbe} covers the ACTION-phase loop itself: a
 * human seat that takes a real action and then passes still reaches the
 * consecutive-pass exit, on today's tree, 16/16 checks. What it does NOT cover
 * is the other window the human can be asked a question in, and the one
 * Support of the Mighty makes them reach for: supporting another player's
 * CONFLICT. That question is collected by
 * {@code GameController.waitForHumanConflictJoin()}, not by the action loop.
 *
 * <p>This probe drives the conflict join window directly and asks the only
 * question the report actually needs answered: can the human decline? It walks
 * the exact input the UI produces ({@code GameAction.pass()}, submitted the
 * same way a click submits it, from outside the controller thread) and then
 * measures whether the engine took it.
 *
 * <p>Run: java b5ccg.engine.B52481JoinWindowPassProbe
 */
public class B52481JoinWindowPassProbe {

    private static int checks = 0;
    private static int fails = 0;

    private static void check(String id, String what, boolean ok) {
        checks++;
        if (!ok) fails++;
        System.out.println("  [" + id + "] " + what + ": " + (ok ? "PASS" : "FAIL"));
    }

    private static Player player(String name, Race faction, boolean human) {
        Player p = new Player(name, faction, human);
        CharacterCard amb = new CharacterCard("amb_" + name, "Ambassador",
                "CHARACTER_" + faction, Rarity.FIXED, Loyalty.fromRace(faction), CardSet.PREMIERE,
                "x", "text", 3, 3, 3, 3, true);
        p.setAmbassador(amb);
        p.getInnerCircle().add(amb);
        return p;
    }

    private static CharacterCard assistant(String id, Race faction) {
        return new CharacterCard(id, id, "CHARACTER_" + faction, Rarity.RARE,
                Loyalty.fromRace(faction), CardSet.PREMIERE, "x", "text", 1, 1, 0, 1, false);
    }

    /** A conflict whose only deal is diplomatic influence -- no fleets, no war. */
    private static Conflict conflict(Player initiator) {
        ConflictCard card = new ConflictCard("b52481_join_conflict",
                "Support of the Mighty Try", "CONFLICT_DIPLOMACY", Rarity.COMMON,
                Loyalty.ANY, CardSet.PREMIERE, "x", "text",
                ConflictType.DIPLOMACY, 1);
        return new Conflict(card, initiator);
    }

    private static GameState state(Player human, Player initiator) {
        List<Player> players = new ArrayList<Player>();
        players.add(human);
        players.add(initiator);
        GameState st = new GameState(players);
        human.setGameState(st);
        initiator.setGameState(st);
        st.setPhase(GamePhase.CONFLICT_RESOLUTION);
        return st;
    }

    /** Start resolveCurrentConflict on its own thread, the shape the UI drives. */
    private static Thread resolve(final GameController controller,
                                  final Throwable[] failure) throws Exception {
        final Method m = GameController.class.getDeclaredMethod("resolveCurrentConflict");
        m.setAccessible(true);
        Thread t = new Thread(new Runnable() {
            public void run() {
                try { m.invoke(controller); }
                catch (Throwable e) { failure[0] = e; }
            }
        });
        t.start();
        return t;
    }

    private static boolean await(GameController controller, boolean attack)
            throws Exception {
        long deadline = System.currentTimeMillis() + 3000L;
        while (System.currentTimeMillis() < deadline) {
            boolean open = attack ? controller.isWaitingForHumanConflictAttack()
                                  : controller.isWaitingForHumanConflictJoin();
            if (open) return true;
            Thread.sleep(10L);
        }
        return false;
    }

    /**
     * The same wait B5-2481's own action loop uses, applied to the join window:
     * poll until the window closes, bounded so this probe can FAIL instead of
     * hanging the run. An unbounded read here is what the production method
     * does, which is why a swallowed click was invisible until now.
     */
    private static boolean windowClosedWithin(GameController controller, long ms)
            throws Exception {
        long deadline = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < deadline) {
            if (!controller.isWaitingForHumanConflictJoin()) return true;
            Thread.sleep(10L);
        }
        return false;
    }

    // ── T1: the reported decline -- PASS into the join window ────────────────
    private static void testPassIntoTHEJoinWindow() throws Exception {
        System.out.println("== T1: human declines the conflict join with PASS ==");
        Player human     = player("JwPassHuman", Race.NARN, true);
        Player initiator = player("JwPassInit", Race.MINBARI, false);
        human.getSupportingRole().add(assistant("jw_pass_supporter", Race.NARN));
        initiator.getInnerCircle().add(assistant("jw_pass_target", Race.MINBARI));
        Conflict cf = conflict(initiator);
        cf.commitCard(initiator, initiator.getAmbassador(), true);
        GameState st = state(human, initiator);
        st.setActiveConflict(cf);

        GameController gc = new GameController(st, new ArrayList<AIPlayer>(),
                new GameStateCallback() {
                    public void accept(GameState s) { }
                });
        Throwable[] failure = new Throwable[1];
        Thread resolver = resolve(gc, failure);

        check("B5-2481", "T1: the join window opened for the human seat",
              await(gc, false));
        // This is the click the report describes: the human declines.
        gc.submitHumanAction(GameAction.pass());
        boolean closed = windowClosedWithin(gc, 2000L);
        resolver.join(3000L);
        check("B5-2481", "T1: the engine ACCEPTED the decline (window closed)",
              closed);
        check("B5-2481", "T1: conflict resolution returned -- no unbounded wait",
              !resolver.isAlive() && failure[0] == null);
        check("B5-2481", "T1: the conflict is resolved, not abandoned open",
              st.getActiveConflict() == null);
        check("B5-2481", "T1: the human never committed to the SUPPORT side",
              !cf.isSupporting(human) || !cf.isParticipantCard(
                  human.getAmbassador()));
    }

    // ── T2: control -- choosing a side still works through the same channel ──
    private static void testSupportIntoTHEJoinWindow() throws Exception {
        System.out.println("== T2: control -- human chooses SUPPORT ==");
        Player human     = player("JwSupHuman", Race.NARN, true);
        Player initiator = player("JwSupInit", Race.MINBARI, false);
        initiator.getInnerCircle().add(assistant("jw_sup_target", Race.MINBARI));
        Conflict cf = conflict(initiator);
        cf.commitCard(initiator, initiator.getAmbassador(), true);
        GameState st = state(human, initiator);
        st.setActiveConflict(cf);

        GameController gc = new GameController(st, new ArrayList<AIPlayer>(),
                new GameStateCallback() {
                    public void accept(GameState s) { }
                });
        Throwable[] failure = new Throwable[1];
        Thread resolver = resolve(gc, failure);

        check("B5-2481", "T2: the join window opened for the human seat",
              await(gc, false));
        gc.submitHumanAction(GameAction.joinSupport());
        boolean closed = windowClosedWithin(gc, 2000L);
        resolver.join(3000L);
        check("B5-2481", "T2: SUPPORT closed the window", closed);
        check("B5-2481", "T2: resolution returned", !resolver.isAlive()
              && failure[0] == null);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("B5-2481: can the human decline the conflict join window?");
        testPassIntoTHEJoinWindow();
        testSupportIntoTHEJoinWindow();
        System.out.println();
        if (fails == 0) {
            System.out.println("B5-2481 JOIN-WINDOW PROBE PASSED (" + checks
                    + " checks)");
        } else {
            System.out.println("B5-2481 JOIN-WINDOW PROBE FAILED (" + fails
                    + " of " + checks + ")");
            System.exit(1);
        }
    }
}
