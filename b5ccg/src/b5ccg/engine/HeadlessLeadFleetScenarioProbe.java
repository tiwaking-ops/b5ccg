package b5ccg.engine;

import b5ccg.ai.AIPlayer;
import b5ccg.model.CharacterCard;
import b5ccg.model.FleetCard;
import b5ccg.model.GameAction;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * B5-0384: standalone scenario probe for the lead-a-fleet action.
 *
 * Harness only. Exercises the live engine through the action handler and
 * verifies that the leader's rotation and fleet link expire at startRound.
 *
 * Run after compile.bat or compile.sh:
 *   java -cp b5ccg/out b5ccg.engine.HeadlessLeadFleetScenarioProbe
 */
public class HeadlessLeadFleetScenarioProbe {

    private static int checks;
    private static int failures;

    private static void check(String label, boolean passed) {
        checks++;
        System.out.println("  [LEAD-PROBE] " + label + ": "
                + (passed ? "PASS" : "FAIL"));
        if (!passed) failures++;
    }

    private static GameState state(Player first, Player second) {
        List<Player> players = new ArrayList<Player>();
        players.add(first);
        players.add(second);
        return new GameState(players);
    }

    public static void main(String[] args) throws Exception {
        Player owner = new Player("Lead Probe", Faction.NARN, false);
        Player rival = new Player("Rival", Faction.MINBARI, false);
        GameState game = state(owner, rival);

        CharacterCard leader = new CharacterCard("probe_leader", "Probe Leader",
                "CHARACTER_NARN", Rarity.COMMON, Faction.NARN,
                CardSet.PREMIERE, "x", "", 1, 1, 0, 4, false);
        CharacterCard otherLeader = new CharacterCard("probe_other_leader",
                "Other Leader", "CHARACTER_NARN", Rarity.COMMON, Faction.NARN,
                CardSet.PREMIERE, "x", "", 1, 1, 0, 2, false);
        FleetCard fleet = new FleetCard("probe_fleet", "Probe Fleet", "FLEET_NARN",
                Rarity.COMMON, Faction.NARN, CardSet.PREMIERE, "x", "", 3);
        owner.getInnerCircle().add(leader);
        owner.getInnerCircle().add(otherLeader);
        owner.getFleets().add(fleet);

        RulesEngine rules = new RulesEngine();
        check("ready leader and own unled fleet form a legal pair",
                rules.canLeadFleet(owner, leader, fleet));

        GameAction action = GameAction.leadFleet(leader, fleet);
        check("action preserves the selected leader and fleet",
                action.getType() == GameAction.Type.LEAD_FLEET
                && action.getLeader() == leader && action.getCard() == fleet);

        GameController controller = new GameController(game,
                new ArrayList<AIPlayer>(), new GameStateCallback() {
                    public void accept(GameState state) { }
                });
        Method processAction = GameController.class.getDeclaredMethod(
                "processAction", Player.class, GameAction.class);
        processAction.setAccessible(true);
        processAction.invoke(controller, owner, action);

        check("executing the action rotates the leader and links the fleet",
                leader.isRotated() && fleet.getLeader() == leader);
        check("leader Leadership adds to fleet Military", 
                fleet.getEffectiveMilitary() == 7
                && owner.conflictTotal(ConflictType.MILITARY) == 7);
        // B5-0372 (D6): processAction no longer calls useAction() — the loop
        // delivers one action per eligibility visit, so actionsLeft is not
        // decremented by a direct handler call. Action consumption is owned
        // by the GameController loop and covered by the conformance suite
        // (FLR section); this probe verifies the handler itself.
        check("handler rotates leader + links fleet (D6: no per-handler spend)",
                leader.isRotated() && fleet.getLeader() == leader
                && owner.getActionsLeft() == 1);
        check("a second leader cannot lead the already-led fleet",
                !otherLeader.isRotated()
                && !rules.canLeadFleet(owner, otherLeader, fleet));

        rules.startRound(game);
        check("round start clears the fleet link and leader rotation",
                fleet.getLeader() == null && !leader.isRotated());
        check("round expiry restores base Military and makes the pair legal",
                fleet.getEffectiveMilitary() == 3
                && rules.canLeadFleet(owner, leader, fleet));

        // B5-0494 (B5-0489 Finding F1): owner-less fleet reads must not crash.
        // The 0486 floor pass originally iterated owner.getBonuses() outside
        // the owner guard and NPE'd on exactly this shape — pinned here.
        FleetCard orphan = new FleetCard("probe_orphan_fleet", "Orphan Fleet",
                "FLEET_NARN", Rarity.COMMON, Faction.NARN, CardSet.PREMIERE,
                "x", "", 2);
        check("owner-less fleet reads base Military with no registry or crash",
                orphan.getEffectiveMilitary() == 2 && orphan.getGreatestAbility() == 2);

        System.out.println("Lead-a-fleet scenario probe: " + checks + " checks, "
                + failures + " failures.");
        System.exit(failures == 0 ? 0 : 1);
    }
}
