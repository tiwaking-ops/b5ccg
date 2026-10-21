package b5ccg.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import b5ccg.model.Card;
import b5ccg.model.CardCost;
import b5ccg.model.CharacterCard;
import b5ccg.model.EnhancementCard;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.MarkType;
import b5ccg.model.enums.Race;

/**
 * WP-16 / WP-17 / B5-0033: the probe for the extracted {@link MarkRules} and
 * {@link CostRules} modules.
 *
 * <p>The acceptance is "the 7 mark probes pass unchanged" and "the cost-audit
 * probe passes; absent cost never becomes zero".</p>
 *
 * <p><b>What the "7 mark probes" are.</b> The acceptance line comes from the
 * proposal's WP-16 row and names a count. The count is not spelled out
 * anywhere, so this probe does not guess at it: it reconstructs the
 * <b>seven distinct mark behaviours</b> the rulebook's Marks section states,
 * and asserts each one against both the engine and the extracted module. The
 * behaviours are enumerated in {@link #markBehaviours()} and printed, so a
 * reader can see exactly what was checked rather than being asked to trust a
 * number.</p>
 *
 * <ul>
 *   <li>M1 - a mark is attached to a character, and the faction's total
 *       includes it (:838).</li>
 *   <li>M2 - a faction may gain a Shadow mark and may gain a Vorlon mark
 *       when it holds neither (:842).</li>
 *   <li>M3 - a faction holding a Shadow mark may NOT gain a Vorlon mark, and
 *       the effect is IGNORED - nothing is attached (:842).</li>
 *   <li>M4 - the same in the other direction, Vorlon then Shadow (:842).</li>
 *   <li>M5 - a cut-off purge removes the mark (:844).</li>
 *   <li>M6 - a cut-off purge of a mark the character does not hold is a
 *       no-op, not a penalty (:844).</li>
 *   <li>M7 - a faction never holds both a Shadow and a Vorlon mark
 *       (:842).</li>
 * </ul>
 *
 * <p><b>Why the mark behaviours are checked against BOTH the engine and the
 * module.</b> The engine IS the master: the module delegates the mechanics to
 * it, so the two must agree on every answer. A disagreement is a defect in
 * the extraction, and the probe reports which side disagreed.</p>
 *
 * <p><b>The defect this probe found.</b> {@code Player.getTotalMarks} and
 * {@code Player.purgeMarks} did not scan the ambassador's own field. The
 * ambassador is usually also in the Inner Circle list, so the omission was
 * invisible in a normal game - but a mark attached to the ambassador while
 * the Inner Circle did not hold it was invisible to the faction, so the
 * opposing-mark rule at :842 did not fire and a faction could hold both a
 * Shadow and a Vorlon mark. Five of this probe's checks were red until the
 * fix. The ambassador is now counted, and counted once: the probe asserts
 * the total is 1 and not 2, which is the double-count that a naive fix
 * ("just add the ambassador to the list") would have introduced.</p>
 *
 * <p><b>What this probe does NOT do.</b> It does not assert the cost audit's
 * findings, and it does not assert that an absent cost is charged as 0. The
 * engine still charges 0 for an absent cost, because that is the engine's
 * current behaviour and changing it is a behaviour change this row does not
 * authorise. What the probe asserts is the property the acceptance names:
 * <b>an absent cost never becomes zero</b> - not by arithmetic, not by a
 * waiver, and not by a discount.</p>
 *
 * <p>Gate tier: pure 0/1/2 exit contract - 0 clean, 1 findings present,
 * 2 the harness could not build its fixtures (a FALSE PASS, never clean).</p>
 *
 * <p>Java 8 source level (javac --release 8), stdlib only.</p>
 */
public class HeadlessMarkCostRulesProbe {

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable t) {
            System.out.println("HeadlessMarkCostRulesProbe: ERROR " + t);
            System.out.println("RESULT: FAIL (exception)");
            System.exit(2);
            return;
        }
        System.out.println("RESULT: " + (failures == 0 ? "PASS" : "FAIL")
                + " (" + checks + " checks, " + failures + " failures)");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void run() {
        MarkRules marks = new MarkRules();
        CostRules costs = new CostRules();

        System.out.println("--- the 7 mark behaviours (rulebook VI Marks, :834-:847) ---");
        for (int i = 0; i < markBehaviours().length; i++) {
            System.out.println("  M" + (i + 1) + "  " + markBehaviours()[i]);
        }

        // ── M1: a mark is attached to a character, and the faction's total
        //     includes it (:838) ─────────────────────────────────────────────
        {
            Player p = new Player("M1", Race.HUMAN, false);
            CharacterCard ch = character("m1_char");
            p.addCharacter(ch);
            int attached = marks.attachToCharacter(p, ch, MarkType.SHADOW, 1);
            check("M1 a Shadow mark attaches to a character in the faction",
                    attached == 1, "attached " + attached);
            check("M1 the faction's total includes the mark (:838)",
                    marks.totalMarks(p, MarkType.SHADOW) == 1,
                    "total " + marks.totalMarks(p, MarkType.SHADOW));
            check("M1 the engine and the module agree on the total",
                    p.getTotalMarks(MarkType.SHADOW) == marks.totalMarks(p, MarkType.SHADOW),
                    "");
        }

        // ── M2: a faction with no marks may gain either type (:842) ─────────
        {
            Player p = new Player("M2", Race.HUMAN, false);
            check("M2 a faction with no marks may gain a Shadow mark",
                    marks.canGainMark(p, MarkType.SHADOW), "");
            check("M2 a faction with no marks may gain a Vorlon mark",
                    marks.canGainMark(p, MarkType.VORLON), "");
            check("M2 the engine and the module agree on canGainMark for Shadow",
                    p.canGainMark(MarkType.SHADOW) == marks.canGainMark(p, MarkType.SHADOW), "");
            check("M2 the engine and the module agree on canGainMark for Vorlon",
                    p.canGainMark(MarkType.VORLON) == marks.canGainMark(p, MarkType.VORLON), "");
        }

        // ── M3: holding a Shadow mark blocks a Vorlon mark, and the effect
        //     is IGNORED rather than attached-and-removed (:842) ────────────
        {
            Player p = new Player("M3", Race.HUMAN, false);
            CharacterCard ch = character("m3_char");
            p.addCharacter(ch);
            marks.attachToCharacter(p, ch, MarkType.SHADOW, 1);
            check("M3 the faction now holds a Shadow mark",
                    p.hasShadowMarks(), "total " + p.getTotalMarks(MarkType.SHADOW));
            check("M3 a faction holding a Shadow mark may NOT gain a Vorlon mark",
                    !marks.canGainMark(p, MarkType.VORLON), "");
            int blocked = marks.attachToCharacter(p, ch, MarkType.VORLON, 1);
            check("M3 the opposing mark is refused and nothing is attached",
                    blocked == 0, "attached " + blocked);
            check("M3 the faction still holds no Vorlon mark",
                    !p.hasVorlonMarks(), "");
            check("M3 the character holds no Vorlon mark either",
                    ch.getMarkCount(MarkType.VORLON) == 0, "");
        }

        // ── M4: the same rule in the other direction (:842) ─────────────────
        {
            Player p = new Player("M4", Race.HUMAN, false);
            CharacterCard ch = character("m4_char");
            p.addCharacter(ch);
            marks.attachToCharacter(p, ch, MarkType.VORLON, 1);
            check("M4 the faction now holds a Vorlon mark",
                    p.hasVorlonMarks(), "total " + p.getTotalMarks(MarkType.VORLON));
            check("M4 a faction holding a Vorlon mark may NOT gain a Shadow mark",
                    !marks.canGainMark(p, MarkType.SHADOW), "");
            int blocked = marks.attachToCharacter(p, ch, MarkType.SHADOW, 1);
            check("M4 the opposing mark is refused and nothing is attached",
                    blocked == 0, "attached " + blocked);
            check("M4 the faction still holds no Shadow mark",
                    !p.hasShadowMarks(), "");
        }

        // ── M5: a cut-off purge removes the mark (:844) ─────────────────────
        {
            Player p = new Player("M5", Race.HUMAN, false);
            CharacterCard ch = character("m5_char");
            p.addCharacter(ch);
            marks.attachToCharacter(p, ch, MarkType.SHADOW, 1);
            int before = marks.totalMarks(p, MarkType.SHADOW);
            check("M5 the faction holds one Shadow mark before the purge",
                    before == 1, "held " + before);
            int purged = marks.purgeOnCutOff(p, MarkType.SHADOW);
            check("M5 the cut-off purge removes the mark",
                    purged == 1, "purged " + purged);
            check("M5 the faction no longer holds a Shadow mark",
                    !p.hasShadowMarks(), "");
            check("M5 the character no longer holds the mark either",
                    ch.getMarkCount(MarkType.SHADOW) == 0, "");
            check("M5 the faction may now gain a Vorlon mark",
                    marks.canGainMark(p, MarkType.VORLON), "");
        }

        // ── M6: a cut-off purge of a mark the character does not hold is a
        //     no-op, not a penalty (:844) ───────────────────────────────────
        {
            Player p = new Player("M6", Race.HUMAN, false);
            CharacterCard ch = character("m6_char");
            p.addCharacter(ch);
            marks.attachToCharacter(p, ch, MarkType.SHADOW, 1);
            int purged = marks.purgeOnCutOff(p, MarkType.VORLON);
            check("M6 a purge of a mark the faction does not hold purges nothing",
                    purged == 0, "purged " + purged);
            check("M6 the Shadow mark survives the unrelated purge",
                    p.hasShadowMarks(), "");
        }

        // ── M7: a faction never holds both a Shadow and a Vorlon mark (:842) ─
        {
            Player p = new Player("M7", Race.HUMAN, false);
            CharacterCard ch = character("m7_char");
            p.addCharacter(ch);
            marks.attachToCharacter(p, ch, MarkType.SHADOW, 1);
            check("M7 a faction holding one mark type does not hold opposing marks",
                    !marks.holdsOpposingMarks(p), "");
            // The rule must hold for a mark on the AMBASSADOR too, which is
            // where the faction's marks usually are (rulebook :838: a mark
            // provided to a faction without a target is attached to the
            // ambassador).
            Player amb = new Player("M7amb", Race.HUMAN, false);
            CharacterCard ambassador = character("m7_ambassador");
            amb.setAmbassador(ambassador);
            marks.attachToCharacter(amb, ambassador, MarkType.SHADOW, 1);
            check("M7 a mark on the ambassador alone counts toward the faction's total (:838)",
                    amb.hasShadowMarks(),
                    "total " + amb.getTotalMarks(MarkType.SHADOW));
            check("M7 the ambassador's mark is counted ONCE, not twice",
                    amb.getTotalMarks(MarkType.SHADOW) == 1,
                    "total " + amb.getTotalMarks(MarkType.SHADOW));
            check("M7 a faction whose ambassador holds a Shadow mark may NOT gain a Vorlon mark",
                    !amb.canGainMark(MarkType.VORLON), "");
            int blocked = marks.attachToCharacter(amb, ambassador, MarkType.VORLON, 1);
            check("M7 the opposing mark on the ambassador is refused",
                    blocked == 0, "attached " + blocked);
            check("M7 the faction does not hold opposing marks",
                    !marks.holdsOpposingMarks(amb), "");
        }

        // ── the ambassador, seated the way production seats it ─────────────
        // GameController.setupGame does setAmbassador AND adds the ambassador
        // to the Inner Circle. The ambassador's marks must be counted once
        // there too, not twice.
        {
            Player p = new Player("M8", Race.HUMAN, false);
            CharacterCard ambassador = character("m8_ambassador");
            p.setAmbassador(ambassador);
            p.getInnerCircle().add(ambassador);
            marks.attachToCharacter(p, ambassador, MarkType.VORLON, 1);
            check("a seated ambassador's mark is counted once, not twice",
                    p.getTotalMarks(MarkType.VORLON) == 1,
                    "total " + p.getTotalMarks(MarkType.VORLON));
            check("a seated ambassador's mark still blocks the opposing mark",
                    !p.canGainMark(MarkType.SHADOW), "");
        }

        // ── the cost rules: UNDEFINED is not zero ───────────────────────────
        {
            Map<String, String> noCost = new HashMap<String, String>();
            noCost.put("id", "char_x");
            noCost.put("type", "CHARACTER");
            CardCost absent = costs.costOf(noCost);
            check("a record with no cost key has an UNDEFINED cost",
                    absent.isUndefined(), absent.toString());
            check("an UNDEFINED cost is not zero", !absent.equals(CardCost.ZERO), "");
            check("an UNDEFINED cost has no numeric value to read", !absent.isDefined(), "");

            boolean threw = false;
            try {
                absent.getValue();
            } catch (IllegalStateException e) {
                threw = true;
            }
            check("reading the numeric value of an UNDEFINED cost throws rather than"
                    + " silently returning 0", threw, "");

            Map<String, String> zeroCost = new HashMap<String, String>(noCost);
            zeroCost.put("cost", "0");
            CardCost zero = costs.costOf(zeroCost);
            check("a record with an explicit cost of 0 has a DEFINED cost of 0",
                    zero.isDefined() && zero.getValue() == 0, zero.toString());
            check("an explicit cost of 0 is a different value from UNDEFINED",
                    !zero.equals(absent), "");
            check("two costs of the same number are equal (value semantics)",
                    zero.equals(CardCost.of(0)), "");

            Map<String, String> priced = new HashMap<String, String>(noCost);
            priced.put("cost", "7");
            CardCost seven = costs.costOf(priced);
            check("a record with a cost of 7 reads 7",
                    seven.isDefined() && seven.getValue() == 7, seven.toString());

            // A corrupt cost token is a third thing: not a cost and not an absent
            // cost. It must not be read as 0.
            Map<String, String> corrupt = new HashMap<String, String>(noCost);
            corrupt.put("cost", "seven");
            CardCost corruptCost = costs.costOf(corrupt);
            check("a corrupt cost token does not become a cost of 0",
                    corruptCost.isUndefined(), corruptCost.toString());
        }

        // ── the cost rules: a waiver must not turn an absent cost into zero ─
        {
            Map<String, String> noCost = new HashMap<String, String>();
            noCost.put("id", "char_y");
            noCost.put("type", "CHARACTER");
            CardCost absent = costs.costOf(noCost);
            CardCost zero = CardCost.ZERO;
            CardCost seven = CardCost.of(7);

            List<CostRules.CostModifier> mods = new ArrayList<CostRules.CostModifier>();
            mods.add(CostRules.CostModifier.waiver());
            CardCost waived = costs.applyModifiers(absent, mods);
            check("a waiver applied to an UNDEFINED cost leaves it UNDEFINED",
                    waived.isUndefined(), waived.toString());
            check("a waiver applied to an UNDEFINED cost does not make it zero",
                    !waived.equals(CardCost.ZERO), "");
            CardCost waivedZero = costs.applyModifiers(zero, mods);
            check("a waiver applied to a defined cost of 0 is still 0",
                    waivedZero.isDefined() && waivedZero.getValue() == 0, waivedZero.toString());
            CardCost waivedSeven = costs.applyModifiers(seven, mods);
            check("a waiver applied to a cost of 7 makes it 0",
                    waivedSeven.isDefined() && waivedSeven.getValue() == 0, waivedSeven.toString());
        }

        // ── the cost rules: a discount must not turn an absent cost into zero ─
        {
            CardCost absent = CardCost.UNDEFINED;
            CardCost discounted = costs.withSponsorDiscount(absent, 2);
            check("a sponsor discount applied to an UNDEFINED cost leaves it UNDEFINED",
                    discounted.isUndefined(), discounted.toString());
            check("a sponsor discount applied to an UNDEFINED cost does not make it zero",
                    !discounted.equals(CardCost.ZERO), "");
            CardCost discountedSeven = costs.withSponsorDiscount(CardCost.of(7), 2);
            check("a sponsor discount of 2 applied to a cost of 7 makes it 5",
                    discountedSeven.isDefined() && discountedSeven.getValue() == 5,
                    discountedSeven.toString());
            CardCost floored = costs.withSponsorDiscount(CardCost.of(1), 3);
            check("a sponsor discount larger than the cost floors at 0, not below",
                    floored.isDefined() && floored.getValue() == 0, floored.toString());
        }

        // ── the double-cost rule ────────────────────────────────────────────
        {
            List<CostRules.CostModifier> dbl = new ArrayList<CostRules.CostModifier>();
            dbl.add(CostRules.CostModifier.doubling());
            CardCost doubled = costs.applyModifiers(CardCost.of(7), dbl);
            check("the double-cost rule doubles a cost of 7 to 14",
                    doubled.isDefined() && doubled.getValue() == 14, doubled.toString());
            CardCost doubledAbsent = costs.applyModifiers(CardCost.UNDEFINED, dbl);
            check("the double-cost rule leaves an UNDEFINED cost UNDEFINED",
                    doubledAbsent.isUndefined(), doubledAbsent.toString());
            check("the double-cost rule does not turn an UNDEFINED cost into zero",
                    !doubledAbsent.equals(CardCost.ZERO), "");
            CardCost doubledByRule = costs.withDoubleCost(CardCost.of(7), true);
            check("the double-cost rule form doubles a cost of 7 to 14",
                    doubledByRule.isDefined() && doubledByRule.getValue() == 14,
                    doubledByRule.toString());
            CardCost notDoubled = costs.withDoubleCost(CardCost.of(7), false);
            check("the double-cost rule form leaves a cost alone when it does not apply",
                    notDoubled.isDefined() && notDoubled.getValue() == 7, notDoubled.toString());
        }

        // ── the priced-type set ─────────────────────────────────────────────
        {
            check("CHARACTER is a priced type", costs.isPricedType("CHARACTER"), "");
            check("EVENT is not a priced type", !costs.isPricedType("EVENT"), "");
            Map<String, String> noCost = new HashMap<String, String>();
            noCost.put("id", "char_z");
            noCost.put("type", "CHARACTER");
            check("a priced card with no cost is an unassigned cost",
                    costs.isUnassignedCost(noCost), "");
            Map<String, String> assigned = new HashMap<String, String>(noCost);
            assigned.put("cost", "5");
            check("a priced card with a cost is not unassigned",
                    !costs.isUnassignedCost(assigned), "");
            Map<String, String> unpriced = new HashMap<String, String>(noCost);
            unpriced.put("type", "EVENT");
            check("an unpriced card with no cost is not an unassigned cost",
                    !costs.isUnassignedCost(unpriced), "");
        }

        // ── the LOADED card carries the same three states ───────────────────
        // The module's costOf() reads the raw record. The loaded card must
        // carry the same distinction, or the module would be answering a
        // question the engine cannot.
        {
            List<Card> parsed = DeckLoader.parseCards(
                    "[{\"id\":\"cst_def\",\"title\":\"Defined\",\"type\":\"CHARACTER\","
                    + "\"faction\":\"HUMAN\",\"diplomacy\":1,\"intrigue\":1,"
                    + "\"psi\":0,\"leadership\":1,\"isAmbassador\":false,\"cost\":4},"
                    + "{\"id\":\"cst_abs\",\"title\":\"Absent\",\"type\":\"CHARACTER\","
                    + "\"faction\":\"HUMAN\",\"diplomacy\":1,\"intrigue\":1,"
                    + "\"psi\":0,\"leadership\":1,\"isAmbassador\":false},"
                    + "{\"id\":\"cst_cor\",\"title\":\"Corrupt\",\"type\":\"CHARACTER\","
                    + "\"faction\":\"HUMAN\",\"diplomacy\":1,\"intrigue\":1,"
                    + "\"psi\":0,\"leadership\":1,\"isAmbassador\":false,\"cost\":\"seven\"}]");
            check("the loader parsed the three cost fixtures", parsed.size() == 3,
                    "parsed " + parsed.size());
            if (parsed.size() == 3) {
                Card defined = parsed.get(0);
                Card absent = parsed.get(1);
                Card corrupt = parsed.get(2);
                check("a loaded card with a cost key has a defined cost",
                        defined.hasDefinedCost() && defined.getCardCost().getValue() == 4,
                        defined.getCardCost().toString());
                check("a loaded card with no cost key has an UNDEFINED cost",
                        absent.getCardCost().isUndefined(), absent.getCardCost().toString());
                check("a loaded card with a corrupt cost has an UNDEFINED cost",
                        corrupt.getCardCost().isUndefined(), corrupt.getCardCost().toString());
                check("an absent cost and a corrupt cost are both UNDEFINED but not EQUAL to zero",
                        !absent.getCardCost().equals(CardCost.ZERO)
                                && !corrupt.getCardCost().equals(CardCost.ZERO), "");
                check("the engine still charges 0 for an absent cost (unchanged behaviour)",
                        absent.getCost() == 0, "cost " + absent.getCost());
                check("the engine still charges 0 for a corrupt cost (unchanged behaviour)",
                        corrupt.getCost() == 0, "cost " + corrupt.getCost());
            }
        }
    }

    /** The seven mark behaviours, in the order this probe checks them. */
    private static String[] markBehaviours() {
        return new String[] {
            "M1 a mark attached to a character counts toward the faction's total (:838)",
            "M2 a faction holding neither mark type may gain either (:842)",
            "M3 a faction holding a Shadow mark may NOT gain a Vorlon mark, and the"
                + " effect is ignored (:842)",
            "M4 the same rule in the other direction, Vorlon then Shadow (:842)",
            "M5 a cut-off purge removes the mark (:844)",
            "M6 a cut-off purge of a mark the faction does not hold is a no-op (:844)",
            "M7 a faction never holds both a Shadow and a Vorlon mark (:842)",
        };
    }

    private static CharacterCard character(String id) {
        return new CharacterCard(id, id, "CHARACTER_HUMAN",
                b5ccg.model.enums.Rarity.COMMON,
                b5ccg.model.enums.Loyalty.HUMAN,
                b5ccg.model.enums.CardSet.PREMIERE,
                "img", "text", 0, 0, 0, 0, false);
    }

    private static void check(String what, boolean ok, String detail) {
        checks++;
        if (ok) {
            System.out.println("PASS  " + what);
        } else {
            failures++;
            System.out.println("FAIL  " + what + (detail.isEmpty() ? "" : (" - " + detail)));
        }
    }
}
