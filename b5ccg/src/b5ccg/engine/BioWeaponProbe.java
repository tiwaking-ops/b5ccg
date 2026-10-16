package b5ccg.engine;

import b5ccg.model.*;
import b5ccg.model.enums.*;
import java.util.*;

/**
 * Isolated probe for B5-1089: Bio-Weapon Discovery deluxe delta.
 * Run: java -cp b5ccg/out b5ccg.engine.BioWeaponProbe
 * Exit 0 = all assertions pass.
 */
public class BioWeaponProbe {
    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [" + label + "]: " + (ok ? "PASS" : "FAIL"));
        if (!ok) failed++;
    }

    private static GameState makeState(Player... players) {
        List<Player> ps = new ArrayList<Player>();
        for (Player p : players) { ps.add(p); }
        GameState s = new GameState(ps);
        for (Player p : players) { p.setGameState(s); }
        return s;
    }

    public static void main(String[] args) {
        System.out.println("=== B5-1089 Bio-Weapon Discovery deluxe delta — isolated probe ===");

        RulesEngine rules = new RulesEngine();

        // ── A: premiere conf_bio_weapon_discovery — discard-2, NO influence loss ─
        // Fill the LOSER's (initiator's) hand so the discard is observable.

        // ── B: deluxe de_conf_bio_weapon_discovery — discard-2 AND lose 1 influence ─
        Player dOwner = new Player("D-owner", Faction.CENTAURI, false);
        Player dRival = new Player("D-rival", Faction.HUMAN, false);
        GameState dSt = makeState(dOwner, dRival);
        dOwner.setAmbassador(new CharacterCard("d_amb", "Amb", "CHARACTER_CENTAURI",
                Rarity.FIXED, Faction.CENTAURI, CardSet.DELUXE, "x", "text", 3, 3, 3, 3, true));
        dRival.setAmbassador(new CharacterCard("d_ramb", "Amb", "CHARACTER_HUMAN",
                Rarity.FIXED, Faction.HUMAN, CardSet.DELUXE, "x", "text", 3, 3, 3, 3, true));
        for (int i = 0; i < 5; i++) {
            dRival.getHand().add(new EventCard("d_fill_" + i, "Fill", "EVENT",
                    Rarity.COMMON, Faction.ANY, CardSet.DELUXE, "x", "text"));
        }
        ConflictCard dCard = new ConflictCard("de_conf_bio_weapon_discovery",
                "Bio-Weapon Discovery", "CONFLICT_INTRIGUE", Rarity.COMMON,
                Faction.ANY, CardSet.DELUXE, "x",
                "Intrigue conflict. Winner gains 3 Influence. Loser must discard 2 "
                        + "cards. (Deluxe text change: loser also loses 1 Influence.)",
                ConflictType.INTRIGUE, 1);
        Conflict dConflict = new Conflict(dCard, dOwner);
        dConflict.commitCard(dOwner, dOwner.getAmbassador());
        dConflict.commitCard(dRival, dRival.getAmbassador());
        int dRivalInf = dRival.getInfluence();
        int dRivalHand = dRival.getHand().size();
        int dOwnerInf = dOwner.getInfluence();
        rules.resolveConflict(dConflict, dSt);
        check("deluxe loser loses 1 influence AND discards 2",
                dRival.getInfluence() == dRivalInf - 1
                        && dRival.getHand().size() == dRivalHand - 2);
        check("deluxe winner gains reward unchanged",
                dOwner.getInfluence() == dOwnerInf + 1);
        System.out.println("  Deluxe: " + dRival.getInfluence() + " inf, "
                + dRival.getHand().size() + " hand (was " + dRivalHand
                + "; delta -1 infl, -2 hand)");

        // ── C: prior paths unchanged ──
        // conf_loss_of_support: loser loses 1 AND discards 1 (both registries).
        Player lOwner = new Player("L-owner", Faction.NARN, false);
        Player lRival = new Player("L-rival", Faction.MINBARI, false);
        GameState lSt = makeState(lOwner, lRival);
        lOwner.setAmbassador(new CharacterCard("l_amb", "Amb", "CHARACTER_NARN",
                Rarity.FIXED, Faction.NARN, CardSet.PREMIERE, "x", "text", 3, 3, 3, 3, true));
        lRival.setAmbassador(new CharacterCard("l_ramb", "Amb", "CHARACTER_MINBARI",
                Rarity.FIXED, Faction.MINBARI, CardSet.PREMIERE, "x", "text", 3, 3, 3, 3, true));
        lRival.getHand().add(new EventCard("l_fill", "Fill", "EVENT",
                Rarity.COMMON, Faction.ANY, CardSet.PREMIERE, "x", "text"));
        ConflictCard lCard = new ConflictCard("conf_loss_of_support",
                "Loss of Support", "CONFLICT_MILITARY", Rarity.COMMON,
                Faction.ANY, CardSet.PREMIERE, "x", "text", ConflictType.MILITARY, 1);
        Conflict lConflict = new Conflict(lCard, lOwner);
        lConflict.commitCard(lOwner, lOwner.getAmbassador());
        lConflict.commitCard(lRival, lRival.getAmbassador());
        int lRivalInf = lRival.getInfluence();
        int lRivalHand = lRival.getHand().size();
        rules.resolveConflict(lConflict, lSt);
        check("prior path conf_loss_of_support: lose 1 + discard 1",
                lRival.getInfluence() == lRivalInf - 1
                        && lRival.getHand().size() == lRivalHand - 1);
        System.out.println("  Loss-of-Support: " + lRival.getInfluence() + " inf, "
                + lRival.getHand().size() + " hand (was " + lRivalHand
                + "; delta -1 infl, -1 hand)");

        System.out.println();
        System.out.println(failed == 0
                ? "PROBE PASSED (" + checks + " assertions)"
                : "PROBE FAILED (" + failed + " of " + checks + " assertions)");
        System.exit(failed == 0 ? 0 : 1);
    }
}
