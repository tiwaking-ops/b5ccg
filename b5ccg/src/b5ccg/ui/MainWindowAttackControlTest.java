package b5ccg.ui;

import b5ccg.ai.AIPlayer;
import b5ccg.engine.GameController;
import b5ccg.engine.GameStateCallback;
import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.Conflict;
import b5ccg.model.ConflictCard;
import b5ccg.model.GameAction;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.*;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.SwingUtilities;

public class MainWindowAttackControlTest {
    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [ATKUI] " + label + ": " + (ok ? "PASS" : "FAIL"));
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

    private static CharacterCard character(String id, Faction faction, int value) {
        return new CharacterCard(id, id, "CHARACTER_" + faction, Rarity.RARE,
            faction, CardSet.PREMIERE, "x", "text", value, 1, 0, 1, false);
    }

    private static Fixture fixture() {
        Player human = player("AtkUiHuman", Faction.NARN, true);
        Player initiator = player("AtkUiInitiator", Faction.MINBARI, false);
        CharacterCard attacker = character("atk_ui_attacker", Faction.NARN, 3);
        CharacterCard firstTarget = character("atk_ui_first_target", Faction.MINBARI, 5);
        CharacterCard chosenTarget = character("atk_ui_chosen_target", Faction.MINBARI, 1);
        human.getSupportingRole().add(attacker);
        initiator.getInnerCircle().add(firstTarget);
        initiator.getInnerCircle().add(chosenTarget);
        ConflictCard conflictCard = new ConflictCard("atk_ui_conflict", "Attack UI Conflict",
            "CONFLICT_DIPLOMACY", Rarity.COMMON, Faction.ANY, CardSet.PREMIERE,
            "x", "text", ConflictType.DIPLOMACY, 1);
        Conflict conflict = new Conflict(conflictCard, initiator);
        firstTarget.rotate();
        conflict.commitCard(initiator, firstTarget, true);
        chosenTarget.rotate();
        conflict.commitCard(initiator, chosenTarget, true);
        List<Player> players = new ArrayList<Player>();
        players.add(human);
        players.add(initiator);
        GameState state = new GameState(players);
        human.setGameState(state);
        initiator.setGameState(state);
        state.setPhase(GamePhase.CONFLICT_RESOLUTION);
        state.setActiveConflict(conflict);
        return new Fixture(attacker, firstTarget, chosenTarget, state);
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
                try {
                    method.invoke(controller);
                } catch (Throwable t) {
                    failure[0] = t;
                }
            }
        });
        thread.start();
        return thread;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void refresh(final MainWindow window,
                                final GameState state) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Method method = MainWindow.class.getDeclaredMethod("refresh", GameState.class);
                    method.setAccessible(true);
                    method.invoke(window, state);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        });
    }

    private static void select(final MainWindow window,
                               final Card card) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Method method = MainWindow.class.getDeclaredMethod(
                        "applyCardSelection", Card.class, boolean.class);
                    method.setAccessible(true);
                    method.invoke(window, card, Boolean.FALSE);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        });
    }

    private static void chooseTarget(final MainWindow window,
                                     final Card target) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    // B5-4216: in targeting mode a click on a valid target card
                    // routes through onTargetSelected, which sets
                    // selectedAttackTarget and enables the Attack button. That is
                    // the replacement for the old attackTargetSelector combo.
                    Method m = MainWindow.class.getDeclaredMethod(
                        "onTargetSelected", Card.class);
                    m.setAccessible(true);
                    m.invoke(window, target);
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

    private static GameController controller(Fixture fixture) {
        return new GameController(fixture.state, new ArrayList<AIPlayer>(),
            new GameStateCallback() {
                public void accept(GameState state) {
                }
            });
    }

    private static void testChosenTarget() throws Exception {
        Fixture fixture = fixture();
        GameController controller = controller(fixture);
        MainWindow window = window(controller);
        Throwable[] failure = new Throwable[1];
        Thread resolver = resolve(controller, failure);
        check("join window opens", awaitJoin(controller));
        controller.submitHumanAction(GameAction.joinOppose());
        check("attack window opens", awaitAttack(controller));
        refresh(window, fixture.state);
        JButton pass = (JButton) field(window, "passButton");
        JButton attack = (JButton) field(window, "attackButton");
        check("live window enables Skip Attack",
            pass.isEnabled() && "Skip Attack".equals(pass.getText()));
        check("no attacker selection keeps Attack disabled",
            !attack.isEnabled());
        select(window, fixture.attacker);
        check("selected attacker with legal targets enables Attack",
            attack.isEnabled());
        click(attack);
        check("Attack click enters targeting mode",
            (Boolean) field(window, "targetingMode"));
        chooseTarget(window, fixture.chosenTarget);
        check("chosen target selects Attack target and keeps Attack enabled",
            attack.isEnabled()
            && field(window, "selectedAttackTarget") == fixture.chosenTarget);
        click(attack);
        resolver.join(3000L);
        check("selected target executes and closes the window",
            !resolver.isAlive() && failure[0] == null
            && fixture.state.getActiveConflict() == null
            && fixture.chosenTarget.isNeutralized()
            && fixture.firstTarget.getDamageTokens() == 0);
        check("post-submit target state clears",
            field(window, "selectedAttackTarget") == null
            && !attack.isEnabled()
            && !(Boolean) field(window, "targetingMode"));
        if (resolver.isAlive()) {
            resolver.interrupt();
            resolver.join(1000L);
        }
        window.dispose();
    }

    private static void testPass() throws Exception {
        Fixture fixture = fixture();
        GameController controller = controller(fixture);
        MainWindow window = window(controller);
        Throwable[] failure = new Throwable[1];
        Thread resolver = resolve(controller, failure);
        awaitJoin(controller);
        controller.submitHumanAction(GameAction.joinOppose());
        awaitAttack(controller);
        refresh(window, fixture.state);
        select(window, fixture.attacker);
        JButton pass = (JButton) field(window, "passButton");
        JButton attack = (JButton) field(window, "attackButton");
        click(attack);
        check("Attack click enters targeting mode before Skip",
            (Boolean) field(window, "targetingMode"));
        click(pass);
        resolver.join(3000L);
        check("Skip Attack resolves without mutation",
            !resolver.isAlive() && failure[0] == null
            && fixture.state.getActiveConflict() == null
            && !fixture.attacker.isRotated()
            && !fixture.chosenTarget.isNeutralized()
            && !attack.isEnabled());
        if (resolver.isAlive()) {
            resolver.interrupt();
            resolver.join(1000L);
        }
        window.dispose();
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("MAIN WINDOW ATTACK CONTROL TEST SKIPPED (headless environment)");
            return;
        }
        System.out.println("=== MainWindow attack control regression ===");
        testChosenTarget();
        testPass();
        System.out.println(failed == 0
            ? "MAIN WINDOW ATTACK CONTROL TEST PASSED (" + checks + " checks)"
            : "MAIN WINDOW ATTACK CONTROL TEST FAILED ("
                + failed + " of " + checks + " checks)");
        System.out.flush();
        System.err.flush();
        System.exit(failed == 0 ? 0 : 1);
    }

    private static final class Fixture {
        final CharacterCard attacker;
        final CharacterCard firstTarget;
        final CharacterCard chosenTarget;
        final GameState state;

        Fixture(CharacterCard attacker, CharacterCard firstTarget,
                CharacterCard chosenTarget, GameState state) {
            this.attacker = attacker;
            this.firstTarget = firstTarget;
            this.chosenTarget = chosenTarget;
            this.state = state;
        }
    }
}
