package b5ccg.engine;

import b5ccg.model.Card;
import b5ccg.model.GameState;
import b5ccg.model.GroupCard;
import b5ccg.model.Player;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.Rarity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * B5-3655 — before/after probe for the applyGenericCardPlay influence-charge
 * ordering.
 *
 * The bug measured 2026-10-09: applyGenericCardPlay CHARGED the influence
 * (applyInfluence) BEFORE the two legality refusals (agenda one-major, group
 * race-restriction), so an illegal card left the player's pool already drained
 * while the card stayed in hand and no refund was issued. The fix moves both
 * refusals before the charge, keeping the accepting path byte-identical.
 *
 * This probe drives the private method directly via reflection (submitHumanAction
 * only parks the action and clears waitingForHuman, so a synthetic probe with no
 * consuming game thread measures nothing). It asserts:
 *   REFUSAL path  — a race-restricted MINBARI group held by a HUMAN player is
 *                   refused with NO change to appliedPool, hand, or groups.
 *   ACCEPT path   — an ANY-faction group held by a HUMAN player is charged its
 *                   cost, removed from hand, and added to groups (economy +1).
 *
 * New-file-only; no game logic is modified. Run after compile:
 *   java -cp b5ccg/out b5ccg.engine.HeadlessGenericPlayOrderingProbe
 */
public class HeadlessGenericPlayOrderingProbe {

    /** Influence we preload so affordability is never the reason for refusal. */
    private static final int SEED_INFLUENCE = 20; // default pool 4 + 20 = 24
    private static final int GROUP_COST     = 6;

    public static void main(String[] args) {
        int failures = 0;
        failures += runRefusalCase();
        failures += runAcceptCase();
        if (failures == 0) {
            System.out.println("B5-3655 PROBE: PASS — both paths correct.");
            System.exit(0);
        } else {
            System.out.println("B5-3655 PROBE: FAILED — " + failures + " assertion(s).");
            System.exit(1);
        }
    }

    /** Reflection handle to the private applyGenericCardPlay(Player,Card,boolean). */
    private static Method resolveApplyMethod() throws Exception {
        Method m = GameController.class.getDeclaredMethod(
                "applyGenericCardPlay", Player.class, Card.class, boolean.class);
        m.setAccessible(true);
        return m;
    }

    private static GameController buildController(Player p) {
        List<Player> players = new ArrayList<Player>();
        players.add(p);
        GameState state = new GameState(players);
        // aiPlayers + uiCallback are unused by applyGenericCardPlay.
        return new GameController(state, null, null);
    }

    private static GroupCard makeGroup(String id, Faction faction) {
        GroupCard g = new GroupCard(id, "probe-group-" + id, "Group",
                Rarity.COMMON, faction, CardSet.PREMIERE, "img", "probe");
        g.setCost(GROUP_COST);
        return g;
    }

    /** REFUSAL case: MINBARI group in a HUMAN hand must not spend influence. */
    private static int runRefusalCase() {
        int failures = 0;
        Player p = new Player("HumanRefusal", Faction.HUMAN, true);
        p.gainInfluence(SEED_INFLUENCE); // appliedPool 24
        GroupCard minbari = makeGroup("MINBARI", Faction.MINBARI);
        p.addToHand(minbari);
        int beforePool = p.getAppliedPool();
        int beforeGroups = p.getGroups().size();
        boolean inHandBefore = p.getHand().contains(minbari);

        try {
            Method m = resolveApplyMethod();
            m.invoke(buildController(p), p, minbari, false);
        } catch (Throwable t) {
            System.err.println("REFUSAL case threw: " + t);
            return 1;
        }

        if (p.getAppliedPool() != beforePool) {
            System.err.println("REFUSAL FAIL: appliedPool " + beforePool
                    + " -> " + p.getAppliedPool() + " (must be unchanged)");
            failures++;
        }
        if (p.getGroups().size() != beforeGroups) {
            System.err.println("REFUSAL FAIL: groups " + beforeGroups
                    + " -> " + p.getGroups().size() + " (must be unchanged)");
            failures++;
        }
        if (p.getHand().contains(minbari) != inHandBefore || !inHandBefore) {
            System.err.println("REFUSAL FAIL: card left hand (must stay)");
            failures++;
        }
        if (failures == 0) {
            System.out.println("REFUSAL OK: appliedPool " + beforePool
                    + " unchanged, card in hand, groups " + beforeGroups);
        }
        return failures;
    }

    /** ACCEPT case: ANY-faction group in a HUMAN hand must charge + play. */
    private static int runAcceptCase() {
        int failures = 0;
        Player p = new Player("HumanAccept", Faction.HUMAN, true);
        p.gainInfluence(SEED_INFLUENCE); // appliedPool 24
        GroupCard any = makeGroup("ANY", Faction.ANY);
        p.addToHand(any);
        int beforePool = p.getAppliedPool();
        int beforeGroups = p.getGroups().size();
        boolean inHandBefore = p.getHand().contains(any);

        try {
            Method m = resolveApplyMethod();
            m.invoke(buildController(p), p, any, false);
        } catch (Throwable t) {
            System.err.println("ACCEPT case threw: " + t);
            return 1;
        }

        if (p.getAppliedPool() != beforePool - GROUP_COST) {
            System.err.println("ACCEPT FAIL: appliedPool " + beforePool
                    + " -> " + p.getAppliedPool() + " (expected "
                    + (beforePool - GROUP_COST) + ")");
            failures++;
        }
        if (p.getGroups().size() != beforeGroups + 1
                || !p.getGroups().contains(any)) {
            System.err.println("ACCEPT FAIL: group not added (groups="
                    + p.getGroups().size() + ")");
            failures++;
        }
        if (p.getHand().contains(any)) {
            System.err.println("ACCEPT FAIL: card still in hand");
            failures++;
        }
        if (failures == 0) {
            System.out.println("ACCEPT OK: appliedPool " + beforePool + " -> "
                    + p.getAppliedPool() + ", group added, card left hand "
                    + inHandBefore);
        }
        return failures;
    }
}
