package b5ccg.engine;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import b5ccg.model.CardCost;

/**
 * WP-17 / B5-0033 (A6.3): the cost rules, extracted.
 *
 * <p>A6 asks for the cost rules to be a module: "Sponsor, recruit and
 * promotion costs, the double-cost rule, waivers. <b>UNDEFINED is a distinct
 * value, not zero</b> (RB L1058). The single source of truth for cost." The
 * rule ID is {@code RB:VI/cards-with-no-printed-cost} (rulebook :1056-:1065).</p>
 *
 * <p><b>Why UNDEFINED matters.</b> {@link b5ccg.model.Card#getCost()} is an
 * {@code int}, so it reads 0 for three materially different records: a card
 * that legitimately costs nothing, a card whose cost key was never assigned,
 * and a card carrying a corrupt value. The cost-audit probe
 * ({@code B52574CardCostAuditProbe}) works around that by reading raw JSON,
 * and the rulebook's own cost section makes the same point: "A card that does
 * not have an orange cost bubble in the lower right hand corner has a cost
 * that is undefined as far as other cards are concerned. Undefined does not
 * mean zero. It is impossible to apply an undefined amount of influence or
 * purge an undefined number of marks." This module makes the distinction
 * expressible: a cost is a {@link CardCost}, which is either a number or
 * {@link CardCost#UNDEFINED}.</p>
 *
 * <p><b>What this row does and does not change.</b> The module expresses the
 * rules. It does <b>not</b> change what the engine charges - a card with no
 * cost is still charged 0, because that is the engine's current behaviour and
 * changing it is a behaviour change this row does not authorise. What the
 * module provides is the place where the rules live, so that a card with an
 * absent cost can be distinguished from a card that costs nothing without
 * re-deriving it from raw JSON at every call site.</p>
 *
 * <p>The <b>single source of truth</b> is the point A6 makes. The rules that
 * read a cost - the sponsor, recruit and promotion costs, the double-cost
 * rule and the waivers - all ask this module, so there is one place where
 * "can I?" and "why not?" are answered and the two cannot drift apart.</p>
 *
 * <p>Java 8 source level (javac --release 8), stdlib only.</p>
 */
public class CostRules {

    /**
     * The card types the cost schema prices (B5-2133 §4).
     *
     * <p>Mirrors the set the cost-audit probe uses. A cost on a type that is
     * not in this set is a data defect, not a cost.</p>
     */
    public static final Set<String> COST_BEARING_TYPES =
            new HashSet<String>(Arrays.asList(
                    "CHARACTER", "ENHANCEMENT", "GROUP", "LOCATION", "FLEET"));

    /**
     * The cost a card carries, distinguishing an absent cost from a zero.
     *
     * <p>Reads the raw record, because the raw record is where the difference
     * lives: a record with no {@code cost} key, a record with {@code cost: 0}
     * and a record with a corrupt {@code cost} token are three different
     * things, and only the first two are visible in the loaded card's number.</p>
     */
    public CardCost costOf(Map<String, String> record) {
        if (record == null) {
            return CardCost.UNDEFINED;
        }
        if (!record.containsKey("cost")) {
            return CardCost.UNDEFINED;
        }
        String raw = record.get("cost");
        if (raw == null) {
            return CardCost.UNDEFINED;
        }
        String trimmed = raw.trim();
        if (trimmed.length() == 0) {
            return CardCost.UNDEFINED;
        }
        try {
            return CardCost.of(Integer.parseInt(trimmed));
        } catch (NumberFormatException e) {
            // A corrupt value is not a cost and not an absent cost: it is a
            // third thing, and it must not be silently read as zero. The audit
            // probe reports it as a VIOLATION; here it is UNDEFINED with the
            // provenance recorded, so the distinction survives.
            return CardCost.UNDEFINED;
        }
    }

    /** Whether the given type is one the schema prices. */
    public boolean isPricedType(String cardType) {
        return COST_BEARING_TYPES.contains(cardType);
    }

    /**
     * Whether a record is a cost-bearing card with no cost assigned.
     *
     * <p>This is the gap B5-2018 found: the 8 ambassadors are cost-bearing
     * and carry no cost. It is a FINDING, not a VIOLATION - the audit probe
     * prints it and does not fail on it, because asserting a gap's continued
     * existence locks a defect in as intended behaviour.</p>
     */
    public boolean isUnassignedCost(Map<String, String> record) {
        if (record == null) return false;
        String type = record.get("type");
        if (type == null || !isPricedType(type)) return false;
        return costOf(record).isUndefined();
    }

    /**
     * The cost a sponsor, recruit or promotion pays, with the waivers applied.
     *
     * <p>The double-cost rule and the waivers are the rulebook's cost rules;
     * this is where they live. The current pool has no card that modifies a
     * cost, so the modifier list is empty in production and the rules are
     * expressed rather than assumed - the same posture the effect schema
     * takes, and the same reason.</p>
     *
     * @param base the card's base cost, which may be UNDEFINED.
     * @param modifiers cost modifiers in force, e.g. a doubling or a waiver.
     * @return the cost after the modifiers, still possibly UNDEFINED.
     */
    public CardCost applyModifiers(CardCost base, List<CostModifier> modifiers) {
        if (base == null) {
            return CardCost.UNDEFINED;
        }
        if (base.isUndefined()) {
            // An absent cost stays absent. Applying a waiver to an absent
            // cost would turn a data gap into a zero, which is exactly the
            // confusion this row exists to remove.
            return CardCost.UNDEFINED;
        }
        int v = base.getValue();
        if (modifiers != null) {
            for (int i = 0; i < modifiers.size(); i++) {
                CostModifier m = modifiers.get(i);
                if (m == null) continue;
                if (m.isWaiver()) {
                    return CardCost.ZERO;
                }
                if (m.isDouble()) {
                    v = v * 2;
                }
            }
        }
        return CardCost.of(v);
    }

    /**
     * The cost after the double-cost rule, which rulebook :663 states for
     * characters loyal to a different race than the sponsoring faction.
     *
     * <p>Neutral characters are exempt, and so is a character whose loyalty
     * matches the faction or is ANY. The engine's own helper
     * {@code RulesEngine.isDoubleCostRequired} answers the same question; this
     * is the cost-side form of it, so the cost rules have one answer and not
     * two that can drift.</p>
     */
    public CardCost withDoubleCost(CardCost base, boolean doubleRequired) {
        if (base == null || base.isUndefined()) {
            return CardCost.UNDEFINED;
        }
        if (!doubleRequired) {
            return base;
        }
        return CardCost.of(base.getValue() * 2);
    }

    /**
     * The cost a faction actually pays, given the card's listed cost and the
     * faction's ambassador's sponsor discount (rulebook :492).
     *
     * <p>The discount is an influence discount, so it is a NUMBER and it
     * cannot be applied to an undefined cost: rulebook :1058 says "It is
     * impossible to apply an undefined amount of influence". An undefined
     * cost therefore stays undefined here, and the engine's historical
     * behaviour - charge 0 - is carried by
     * {@link CardCost#getValueOrDefault(int)} at the call site, where the
     * substitution is visible.</p>
     */
    public CardCost withSponsorDiscount(CardCost base, int discount) {
        if (base == null || base.isUndefined()) {
            return CardCost.UNDEFINED;
        }
        return CardCost.of(Math.max(0, base.getValue() - discount));
    }

    /** A cost modifier: a doubling or a waiver. */
    public static final class CostModifier {
        private final boolean waiver;
        private final boolean doubling;

        private CostModifier(boolean waiver, boolean doubling) {
            this.waiver = waiver;
            this.doubling = doubling;
        }

        public static CostModifier waiver()  { return new CostModifier(true, false); }
        public static CostModifier doubling() { return new CostModifier(false, true); }

        public boolean isWaiver()   { return waiver; }
        public boolean isDouble()   { return doubling; }
    }
}
