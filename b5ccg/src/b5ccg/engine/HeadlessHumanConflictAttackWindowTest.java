package b5ccg.engine;

import b5ccg.ai.AIPlayer;
import b5ccg.model.*;
import b5ccg.model.enums.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class HeadlessHumanConflictAttackWindowTest {
    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [ATKW] " + label + ": " + (ok ? "PASS" : "FAIL"));
        if (!ok) failed++;
    }

    private static Player player(String name, Faction faction, boolean human) {
        Player p = new Player(name, faction, human);
        CharacterCard ambassador = new CharacterCard("amb_" + name, "Ambassador",
                "CHARACTER_" + faction, Rarity.FIXED, faction, CardSet.PREMIERE,
                "x", "text", 3, 3, 3, 3, true);
        p.setAmbassador(ambassador);
        p.getInnerCircle().add(ambassador);
        return p;
    }

    private static GameState state(Player human, Player initiator) {
        List<Player> players = new ArrayList<Player>();
        players.add(human);
        players.add(initiator);
        GameState state = new GameState(players);
        human.setGameState(state);
        initiator.setGameState(state);
        state.setPhase(GamePhase.CONFLICT_RESOLUTION);
        return state;
    }

    private static Conflict conflict(Player initiator) {
        ConflictCard card = new ConflictCard("atk_window_conflict", "Window Conflict",
                "CONFLICT_DIPLOMACY", Rarity.COMMON, Faction.ANY, CardSet.PREMIERE,
                "x", "text", ConflictType.DIPLOMACY, 1);
        return new Conflict(card, initiator);
    }

    private static CharacterCard character(String id, Faction faction,
                                          int diplomacy, int leadership) {
        return new CharacterCard(id, id, "CHARACTER_" + faction, Rarity.RARE,
                faction, CardSet.PREMIERE, "x", "text", diplomacy, 1, 0,
                leadership, false);
    }

    private static boolean awaitJoin(GameController controller) throws Exception {
        long deadline = System.currentTimeMillis() + 3000L;
        while (!controller.isWaitingForHumanConflictJoin()
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        return controller.isWaitingForHumanConflictJoin();
    }

    private static boolean awaitAttack(GameController controller) throws Exception {
        long deadline = System.currentTimeMillis() + 3000L;
        while (!controller.isWaitingForHumanConflictAttack()
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        return controller.isWaitingForHumanConflictAttack();
    }

    private static Thread resolve(final GameController controller,
                                  final Throwable[] failure) throws Exception {
        final Method method = GameController.class.getDeclaredMethod("resolveCurrentConflict");
        method.setAccessible(true);
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try { method.invoke(controller); }
                catch (Throwable t) { failure[0] = t; }
            }
        });
        thread.start();
        return thread;
    }

    private static Fixture fixture() {
        Player human = player("AtkWindowHuman", Faction.NARN, true);
        Player initiator = player("AtkWindowInitiator", Faction.MINBARI, false);
        CharacterCard attacker = character("atk_window_attacker", Faction.NARN, 3, 1);
        CharacterCard target = character("atk_window_target", Faction.MINBARI, 2, 1);
        human.getSupportingRole().add(attacker);
        initiator.getInnerCircle().add(target);
        Conflict conflict = conflict(initiator);
        target.rotate();
        conflict.commitCard(initiator, target, true);
        GameState state = state(human, initiator);
        state.setActiveConflict(conflict);
        return new Fixture(human, initiator, attacker, target, conflict, state);
    }

    private static void testValidAttack() throws Exception {
        Fixture fixture = fixture();
        GameController controller = new GameController(fixture.state,
                new ArrayList<AIPlayer>(),
                new GameStateCallback() { public void accept(GameState state) { } });
        Throwable[] failure = new Throwable[1];
        Thread resolver = resolve(controller, failure);
        check("human join window opens before attack", awaitJoin(controller));
        controller.submitHumanAction(GameAction.joinOppose());
        check("attack window opens after join", awaitAttack(controller));
        controller.submitHumanAction(GameAction.attackConflictParticipant(
                fixture.attacker, fixture.human.getAmbassador()));
        Thread.sleep(50L);
        check("invalid attack keeps the window open",
                controller.isWaitingForHumanConflictAttack());
        controller.submitHumanAction(GameAction.attackConflictParticipant(
                fixture.attacker, fixture.target));
        resolver.join(3000L);
        check("valid attack resolves and closes the window",
                !resolver.isAlive() && failure[0] == null
                && !controller.isWaitingForHumanConflictAttack()
                && fixture.state.getActiveConflict() == null);
        check("attack keeps the human on the joined opposition side",
                fixture.conflict.isOpposing(fixture.human)
                && !fixture.conflict.isSupporting(fixture.human));
        check("valid attack executes the existing B5-0370 mutation path",
                fixture.attacker.isRotated()
                && fixture.conflict.isParticipantCard(fixture.attacker)
                && fixture.attacker.getDamageTokens() == 2
                && fixture.target.isNeutralized());
        if (resolver.isAlive()) {
            resolver.interrupt();
            resolver.join(1000L);
        }
    }

    private static void testPass() throws Exception {
        Fixture fixture = fixture();
        GameController controller = new GameController(fixture.state,
                new ArrayList<AIPlayer>(),
                new GameStateCallback() { public void accept(GameState state) { } });
        Throwable[] failure = new Throwable[1];
        Thread resolver = resolve(controller, failure);
        awaitJoin(controller);
        controller.submitHumanAction(GameAction.joinOppose());
        check("pass path reaches the attack window", awaitAttack(controller));
        controller.submitHumanAction(GameAction.pass());
        resolver.join(3000L);
        check("pass closes the window without attacking",
                !resolver.isAlive() && failure[0] == null
                && !controller.isWaitingForHumanConflictAttack()
                && !fixture.attacker.isRotated()
                && !fixture.conflict.isParticipantCard(fixture.attacker));
        if (resolver.isAlive()) {
            resolver.interrupt();
            resolver.join(1000L);
        }
    }

    private static void testNoOffer() throws Exception {
        Player human = player("AtkWindowSolo", Faction.NARN, true);
        CharacterCard attacker = character("atk_window_solo_attacker", Faction.NARN, 3, 1);
        human.getSupportingRole().add(attacker);
        Conflict conflict = conflict(human);
        List<Player> players = new ArrayList<Player>();
        players.add(human);
        GameState state = new GameState(players);
        human.setGameState(state);
        state.setPhase(GamePhase.CONFLICT_RESOLUTION);
        state.setActiveConflict(conflict);
        GameController controller = new GameController(state,
                new ArrayList<AIPlayer>(),
                new GameStateCallback() { public void accept(GameState state) { } });
        Throwable[] failure = new Throwable[1];
        Thread resolver = resolve(controller, failure);
        resolver.join(300L);
        boolean offered = controller.isWaitingForHumanConflictAttack();
        if (offered) controller.submitHumanAction(GameAction.pass());
        resolver.join(3000L);
        check("no legal opposing participant means no attack wait",
                !offered && !resolver.isAlive() && failure[0] == null
                && !controller.isWaitingForHumanConflictAttack());
        if (resolver.isAlive()) {
            resolver.interrupt();
            resolver.join(1000L);
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("=== Human conflict-attack window conformance ===");
            testValidAttack();
            testPass();
            testNoOffer();
            System.out.println(failed == 0
                    ? "ATTACK WINDOW SUITE PASSED (" + checks + " checks)"
                    : "ATTACK WINDOW SUITE FAILED (" + failed + " of " + checks + " checks)");
        } catch (Throwable t) {
            failed++;
            t.printStackTrace(System.err);
        }
        System.out.flush();
        System.err.flush();
        System.exit(failed == 0 ? 0 : 1);
    }

    private static final class Fixture {
        final Player human;
        final Player initiator;
        final CharacterCard attacker;
        final CharacterCard target;
        final Conflict conflict;
        final GameState state;

        Fixture(Player human, Player initiator, CharacterCard attacker,
                CharacterCard target, Conflict conflict, GameState state) {
            this.human = human;
            this.initiator = initiator;
            this.attacker = attacker;
            this.target = target;
            this.conflict = conflict;
            this.state = state;
        }
    }
}
