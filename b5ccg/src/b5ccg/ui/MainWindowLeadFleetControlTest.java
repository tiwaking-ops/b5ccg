package b5ccg.ui;

import b5ccg.ai.AIPlayer;
import b5ccg.engine.GameController;
import b5ccg.engine.GameStateCallback;
import b5ccg.engine.RulesEngine;
import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.FleetCard;
import b5ccg.model.GameAction;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.GamePhase;
import b5ccg.model.enums.Rarity;
import b5ccg.model.enums.StatKey;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

/**
 * B5-2507 - standing assertion for the Lead Fleet UI control.
 *
 * <p>The lead-fleet button, the fleet selector, and their handlers were
 * already wired into MainWindow by B5-0401, and the engine path
 * (RulesEngine.canLeadFleet / executeLeadFleet, GameAction.leadFleet,
 * GameController LEAD_FLEET dispatch) has been in place since B5-0362.
 * B5-2507's premise ("leadFleetButton is declared with a selector but has
 * no handler") was already stale when the row was seeded: the handler at
 * MainWindow.java submits GameAction.leadFleet through
 * controller.submitHumanAction, which dispatches to processAction, which
 * re-checks canLeadFleet before calling executeLeadFleet.
 *
 * <p>This test exists because nothing previously asserted any of that
 * wiring end-to-end. MainWindowToolbarLayoutTest names leadFleetButton in
 * its dialog-home list (reachability only), so a handler deletion today
 * would leave the suite green. This class is that missing assertion:
 *
 * (1) leadFleetButton exists, carries a click listener, and starts disabled;
 * (2) leadFleetSelector exists and is populated from the player's fleets
 *     after a character selection; (3) enablement is the ENGINE's verdict,
 *     not a UI re-derivation — lit on the human ACTION turn with a valid
 *     leader + eligible fleet, dark in DRAW phase, dark with no eligible
 *     fleet; (4) the LEAD_FLEET action the listener submits really rotates
 *     the leader and links the fleet (RulesEngine.executeLeadFleet).
 *
 * <p>Exit codes: 0 all checks passed, 1 a check failed. A headless host
 * skips with exit 0, matching MainWindowAttackControlTest.
 */
public class MainWindowLeadFleetControlTest {
    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [LFCUI] " + label + ": " + (ok ? "PASS" : "FAIL"));
        if (!ok) failed++;
    }

    private static CharacterCard ambassador(Faction f) {
        return new CharacterCard("amb_leadfleet", "Ambassador",
            "CHARACTER_" + f, Rarity.FIXED, f, CardSet.PREMIERE,
            "x", "text", 3, 3, 3, 3, true);
    }

    /** A ready Inner Circle character with Leadership 2. */
    private static CharacterCard leader(Faction f) {
        return new CharacterCard("leadfleet_leader", "Fleet Leader",
            "CHARACTER_" + f, Rarity.RARE, f, CardSet.PREMIERE,
            "x", "text", 1, 1, 0, 2, false);
    }

    /** An unled, unrotated fleet with Military 3. */
    private static FleetCard aFleet(Faction f) {
        return new FleetCard("leadfleet_fleet", "Lead Fleet", "FLEET_" + f,
            Rarity.COMMON, f, CardSet.PREMIERE, "x", "text", 3);
    }

    private static GameState twoPlayerState(String humanName, Faction humanFaction,
                                            String rivalName, Faction rivalFaction,
                                            GamePhase phase) {
        Player human = new Player(humanName, humanFaction, true);
        Player rival = new Player(rivalName, rivalFaction, false);
        CharacterCard amb = ambassador(humanFaction);
        CharacterCard ldr = leader(humanFaction);
        FleetCard fl = aFleet(humanFaction);
        human.setAmbassador(amb);
        human.getInnerCircle().add(amb);
        human.getInnerCircle().add(ldr);
        human.getFleets().add(fl);
        List<Player> players = new ArrayList<Player>();
        players.add(human);
        players.add(rival);
        GameState state = new GameState(players);
        human.setGameState(state);
        rival.setGameState(state);
        state.setPhase(phase);
        // B5-0372: the ACTION-phase loop grants one action per player; give
        // the human their full action so canLeadFleet's caller does not also
        // need a running loop.
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
                    method.invoke(window, card, Boolean.FALSE);
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
     * controller.submitHumanAction only QUEUES an action for a running game
     * loop; the loop drains it via waitForHumanAction() and dispatches into
     * processAction (GameController.java:394). There is no loop here, so drive
     * processAction directly - it is where the LEAD_FLEET case lives.
     */
    private static void dispatchLeadFleet(GameController controller, Player human,
                                          CharacterCard leader, FleetCard fleet)
            throws Exception {
        Method method = GameController.class.getDeclaredMethod(
            "processAction", Player.class, GameAction.class);
        method.setAccessible(true);
        method.invoke(controller, human, GameAction.leadFleet(leader, fleet));
    }

    private static void testControlIsWired() throws Exception {
        System.out.println("  [LFCUI] --- the lead-fleet control exists and is wired ---");
        GameState state = twoPlayerState("LfHuman", Faction.NARN,
            "LfRival", Faction.MINBARI, GamePhase.ACTION);
        MainWindow w = window(controller(state));
        JButton leadFleet = (JButton) field(w, "leadFleetButton");
        JComboBox<String> fleetSel = (JComboBox<String>) field(w, "leadFleetSelector");

        check("leadFleetButton is constructed", leadFleet != null);
        check("leadFleetButton is labelled Lead Fleet",
            "Lead Fleet".equals(leadFleet.getText()));
        check("leadFleetButton carries a click handler",
            leadFleet.getActionListeners() != null
                && leadFleet.getActionListeners().length > 0);
        check("leadFleetButton starts disabled before the first refresh",
            !leadFleet.isEnabled());
        check("leadFleetSelector is constructed", fleetSel != null);
        w.dispose();
    }

    /** Set the controller's waitingForHuman flag so the UI treats the human
     *  as having an open action turn. In normal play this is set by
     *  waitForHumanAction() inside the game loop; in a harness there is no
     *  loop, so inject it. */
    private static void setWaitingForHuman(GameController ctrl, boolean v)
            throws Exception {
        Field f = GameController.class.getDeclaredField("waitingForHuman");
        f.setAccessible(true);
        f.set(ctrl, v);
    }

    private static void testEngineGatesEnablement() throws Exception {
        System.out.println("  [LFCUI] --- enablement is the engine's verdict ---");

        // Action phase: select the leader character, then the button should
        // light once a fleet is chosen in the selector.
        GameState actionState = twoPlayerState("LfActH", Faction.NARN,
            "LfActR", Faction.MINBARI, GamePhase.ACTION);
        Player human = actionState.getHumanPlayer();
        CharacterCard leader = null;
        for (CharacterCard ch : human.getInnerCircle()) {
            if (!ch.isAmbassador()) { leader = ch; break; }
        }
        GameController ctrl = controller(actionState);
        setWaitingForHuman(ctrl, true);
        MainWindow w = window(ctrl);
        JButton leadFleet = (JButton) field(w, "leadFleetButton");
        JComboBox<String> fleetSel = (JComboBox<String>) field(w, "leadFleetSelector");
        refresh(w, actionState);

        // Before selecting a character, no fleets are listed and button is dark.
        check("no fleet selected yet -> button disabled", !leadFleet.isEnabled());
        check("selector disabled before character selection", !fleetSel.isEnabled());

        // Select the leader character — applyCardSelection triggers
        // refreshLeadFleetAndRotateEffect which populates the selector.
        select(w, leader);
        check("character selected -> fleet selector populated with 1 fleet",
            fleetSel.isEnabled() && fleetSel.getItemCount() == 1);

        // The button should be enabled because canLeadFleet is true
        // (engine predicate, not a UI re-derivation).
        check("ACTION phase + valid leader + eligible fleet -> button ENABLES",
            leadFleet.isEnabled());

        // DRAW phase: button must stay dark even though the character and
        // fleet are still selected — the engine says "not your action turn."
        GameState drawState = twoPlayerState("LfDrawH", Faction.NARN,
            "LfDrawR", Faction.MINBARI, GamePhase.DRAW);
        Player drawHuman = drawState.getHumanPlayer();
        CharacterCard drawLeader = null;
        for (CharacterCard ch : drawHuman.getInnerCircle()) {
            if (!ch.isAmbassador()) { drawLeader = ch; break; }
        }
        GameController drawCtrl = controller(drawState);
        MainWindow dw = window(drawCtrl);
        JButton drawButton = (JButton) field(dw, "leadFleetButton");
        refresh(dw, drawState);
        select(dw, drawLeader);
        check("DRAW phase -> button DISABLED (not the human's action turn)",
            !drawButton.isEnabled());
        dw.dispose();

        // No eligible fleet: a rotated fleet cannot be led.
        GameState noFleetState = twoPlayerState("LfNoF", Faction.NARN,
            "LfNoR", Faction.MINBARI, GamePhase.ACTION);
        Player nfHuman = noFleetState.getHumanPlayer();
        FleetCard fleet = nfHuman.getFleets().get(0);
        fleet.rotate();  // B5-0362: a rotated fleet cannot be led
        CharacterCard nfLeader = null;
        for (CharacterCard ch : nfHuman.getInnerCircle()) {
            if (!ch.isAmbassador()) { nfLeader = ch; break; }
        }
        GameController nfCtrl = controller(noFleetState);
        setWaitingForHuman(nfCtrl, true);
        MainWindow nf = window(nfCtrl);
        JButton nfButton = (JButton) field(nf, "leadFleetButton");
        JComboBox<String> nfSel = (JComboBox<String>) field(nf, "leadFleetSelector");
        refresh(nf, noFleetState);
        select(nf, nfLeader);
        check("no eligible (unrotated, unled) fleet -> selector shows '(no eligible fleet)'",
            nfSel.getItemCount() == 1
            && "(no eligible fleet)".equals((String) nfSel.getItemAt(0)));
        check("no eligible fleet -> button DISABLED", !nfButton.isEnabled());
        nf.dispose();

        w.dispose();
    }

    private static void testSubmittedActionLeadsFleet() throws Exception {
        System.out.println("  [LFCUI] --- the submitted LEAD_FLEET action really leads ---");
        GameState state = twoPlayerState("LfSubH", Faction.NARN,
            "LfSubR", Faction.MINBARI, GamePhase.ACTION);
        Player human = state.getHumanPlayer();
        CharacterCard leader = null;
        for (CharacterCard ch : human.getInnerCircle()) {
            if (!ch.isAmbassador()) { leader = ch; break; }
        }
        FleetCard fleet = human.getFleets().get(0);
        GameController ctrl = controller(state);

        check("leader is unrotated and fleet has no leader before submit",
            !leader.isRotated() && fleet.getLeader() == null);
        RulesEngine rules = getRules(ctrl);
        check("canLeadFleet is true before dispatch",
            rules != null && rules.canLeadFleet(human, leader, fleet));

        int baseMilitary = fleet.getEffectiveMilitary();
        int leaderLeadership = leader.getEffectiveStat(StatKey.LEADERSHIP);

        dispatchLeadFleet(ctrl, human, leader, fleet);

        check("dispatch rotates the leader", leader.isRotated());
        check("dispatch links the fleet to the leader", fleet.getLeader() == leader);
        check("fleet effective military now includes leader's Leadership",
            fleet.getEffectiveMilitary() == baseMilitary + leaderLeadership);
    }

    private static RulesEngine getRules(GameController ctrl) throws Exception {
        Field f = GameController.class.getDeclaredField("rules");
        f.setAccessible(true);
        return (RulesEngine) f.get(ctrl);
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("MAIN WINDOW LEAD FLEET CONTROL TEST SKIPPED"
                + " (headless environment)");
            return;
        }
        System.out.println("=== MainWindow lead fleet control regression ===");
        testControlIsWired();
        testEngineGatesEnablement();
        testSubmittedActionLeadsFleet();
        System.out.println(failed == 0
            ? "MAIN WINDOW LEAD FLEET CONTROL TEST PASSED (" + checks + " checks)"
            : "MAIN WINDOW LEAD FLEET CONTROL TEST FAILED ("
                + failed + " of " + checks + " checks)");
        System.out.flush();
        System.err.flush();
        System.exit(failed == 0 ? 0 : 1);
    }
}
