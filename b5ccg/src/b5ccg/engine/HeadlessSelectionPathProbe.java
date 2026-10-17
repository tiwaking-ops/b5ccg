package b5ccg.engine;

import b5ccg.model.*;
import b5ccg.model.enums.*;
import java.util.List;
import java.util.ArrayList;

/**
 * B5-4184 selection-path probe part 1.
 * See class javadoc in part 2 for the full verdict table.
 */
public class HeadlessSelectionPathProbe {

    static int checks = 0;
    static int failed = 0;

    static void check(String label, boolean ok) {
        checks++;
        if (!ok) {
            failed++;
            System.out.println("  [SELPATH] FAIL: " + label);
        } else {
            System.out.println("  [SELPATH] pass: " + label);
        }
    }

    /** Mirrors the healButton handler: selection only, engine gate. */
    static boolean modelHealSubmit(RulesEngine rules, Player hp, Card sel) {
        if (hp == null || !(sel instanceof CharacterCard)) return false;
        CharacterCard ch = (CharacterCard) sel;
        if (!rules.canHealCharacter(hp, ch)) return false;
        GameAction a = GameAction.healCharacter(ch);
        return a != null && a.getType() == GameAction.Type.HEAL_CHARACTER;
    }

    /** Mirrors the repairButton handler: selection only, engine gate. */
    static boolean modelRepairSubmit(RulesEngine rules, Player hp, Card sel) {
        if (hp == null || sel == null) return false;
        if (!(sel instanceof FleetCard) && !(sel instanceof LocationCard)) return false;
        if (!rules.canRepairCard(hp, sel)) return false;
        GameAction a = GameAction.repairCard(sel);
        return a != null && a.getType() == GameAction.Type.REPAIR_CARD;
    }

    /**
     * Mirrors the leadFleetButton handler: leader from selectedCard, fleet
     * from selectedFleet (the leadFleetSelector combo). True iff more than
     * one fleet pairs legally, so one click underdetermines the submit.
     */
    static boolean modelLeadNeedsFleetCombo(RulesEngine rules, Player hp,
                                            CharacterCard leaderSel) {
        if (hp == null || leaderSel == null) return false;
        int legalFleets = 0;
        for (FleetCard fl : hp.getFleets()) {
            if (rules.canLeadFleet(hp, leaderSel, fl)) legalFleets++;
        }
        return legalFleets > 1;
    }

    /** Mirrors applyCardSelection assistant tracking: supporting pick derives. */
    static CharacterCard modelDeriveAssistant(Player hp, Card card) {
        if (card instanceof CharacterCard) {
            CharacterCard ch = (CharacterCard) card;
            if (hp != null && hp.getSupportingRole().contains(ch)) return ch;
        }
        return null;
    }

    /**
     * B5-4184 verdict driver.
     *
     * Redesign report section 13 item 3 (brief section 1 row 3 + section 5
     * triage): board/hand selection already drives lead-fleet / rotate-effect
     * / heal / repair through MainWindow.applyCardSelection, so the per-rule
     * combo boxes look redundant. M4 must not delete a combo until a headless
     * probe proves every legal action of that kind is submittable from a
     * selection, or a named counterexample keeps the combo.
     *
     * Selection model (MainWindow.java): applyCardSelection sets selectedCard
     * and derives selectedAssistant iff the card is a supporting-role
     * character (:4022-:4033). Board and hand share the handler (:1710-:1726);
     * GameBoardPanel.resolveCardAt covers ambassador, IC, supporting, fleets,
     * groups, locations (:80-:149).
     *
     * A selection path means one single-card selection supplies every card
     * input the submit handler needs.
     */
    public static void main(String[] args) {
        System.out.println("=== B5-4184 selection-path probe ===");
        RulesEngine rules = new RulesEngine();

        Player human = new Player("Human", Race.HUMAN, true);
        List<Player> players = new ArrayList<Player>();
        players.add(human);
        GameState state = new GameState(players);
        human.setGameState(state);

        CharacterCard amb = charCard("sel_amb", "Probe Ambassador", true);
        human.setAmbassador(amb);
        CharacterCard leader = charCard("sel_leader", "Probe Leader", false);
        human.getInnerCircle().add(leader);
        CharacterCard leader2 = charCard("sel_leader2", "Probe Leader Two", false);
        human.getInnerCircle().add(leader2);
        CharacterCard assistant = charCard("sel_asst", "Probe Assistant", false);
        human.getSupportingRole().add(assistant);
        FleetCard fleet = fleetCard("sel_fleet", "Probe Fleet");
        human.addFleet(fleet);
        FleetCard fleet2 = fleetCard("sel_fleet2", "Probe Fleet Two");
        human.addFleet(fleet2);
        LocationCard loc = locCard("sel_loc", "Probe Location");
        human.getLocations().add(loc);
        LocationCard loc2 = locCard("sel_loc2", "Probe Location Two");
        human.getLocations().add(loc2);

        leader.applyDamage(1);
        check("heal: damaged IC leader is engine-legal",
                rules.canHealCharacter(human, leader));
        check("heal: single-card selection submits a legal heal",
                modelHealSubmit(rules, human, leader));

        fleet.applyDamage(2);
        human.gainInfluence(10);
        check("repair: damaged funded fleet is engine-legal",
                rules.canRepairCard(human, fleet));
        check("repair: single-card selection submits a legal repair",
                modelRepairSubmit(rules, human, fleet));
        loc2.applyDamage(1);
        check("repair: damaged funded location is engine-legal",
                rules.canRepairCard(human, loc2));
        check("repair: single-card selection submits a legal location repair",
                modelRepairSubmit(rules, human, loc2));

        check("lead: (leader2, fleet2) pair is engine-legal",
                rules.canLeadFleet(human, leader2, fleet2));
        check("lead: single-card selection CANNOT name the fleet (combo required)",
                modelLeadNeedsFleetCombo(rules, human, leader2));

        boolean boostLegal = rules.canUseRotateEffect(human, assistant, amb,
                GameAction.RotateEffectKind.USE_ABILITY_BOOST);
        boolean discountLegal = rules.canUseRotateEffect(human, assistant, amb,
                GameAction.RotateEffectKind.USE_SPONSOR_DISCOUNT);
        check("rotate: ability-boost kind is engine-legal", boostLegal);
        check("rotate: sponsor-discount kind is engine-legal", discountLegal);
        check("rotate: single-card selection CANNOT name the kind (combo required)",
                boostLegal && discountLegal);

        check("rotate: supporting-role pick derives selectedAssistant",
                modelDeriveAssistant(human, assistant) == assistant);
        check("rotate: IC pick clears selectedAssistant",
                modelDeriveAssistant(human, leader2) == null);

        check("zones: IC leader lives in a selectable zone",
                human.getInnerCircle().contains(leader2));
        check("zones: supporting assistant lives in a selectable zone",
                human.getSupportingRole().contains(assistant));
        check("zones: fleet lives in a selectable zone",
                human.getFleets().contains(fleet2));
        check("zones: location lives in a selectable zone",
                human.getLocations().contains(loc2));

        System.out.println();
        System.out.println("  checks: " + checks + ", failed: " + failed);
        System.out.println("  verdict: heal selection-sufficient; repair selection-sufficient;");
        System.out.println("           lead-fleet combo RETAINED (fleet is a second card input);");
        System.out.println("           rotate-effect combo RETAINED (kind is a non-card input).");
        if (failed == 0) {
            System.out.println("SELPATH PROBE PASSED (" + checks + " checks)");
        } else {
            System.out.println("SELPATH PROBE FAILED (" + failed + " of " + checks + " checks)");
        }
        System.exit(failed == 0 ? 0 : 1);
    }

    static CharacterCard charCard(String id, String title, boolean amb) {
        return new CharacterCard(id, title, "OFFICER", Rarity.COMMON,
                Loyalty.HUMAN, CardSet.PREMIERE, "x", "probe",
                1, 1, 0, 3, amb);
    }

    static FleetCard fleetCard(String id, String title) {
        return new FleetCard(id, title, "LINE", Rarity.COMMON,
                Loyalty.HUMAN, CardSet.PREMIERE, "x", "probe", 3);
    }

    static LocationCard locCard(String id, String title) {
        return new LocationCard(id, title, "BASE", Rarity.COMMON,
                Loyalty.HUMAN, CardSet.PREMIERE, "x", "probe", 1, 3);
    }
}
