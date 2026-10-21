package b5ccg.model;

import b5ccg.model.enums.MarkType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WP-17 / B5-0033 (A6.3): a card's cost, where UNDEFINED is a distinct
 * value and not zero.
 *
 * <p><b>The problem this solves.</b> {@link Card#getCost()} is an
 * {@code int}, so it reads {@code 0} for three materially different records:
 * a card that legitimately costs nothing, a card whose cost key was never
 * assigned, and a card carrying a corrupt value. The existing cost-audit
 * probe ({@code B52574CardCostAuditProbe}) documents this at length and works
 * around it by reading the raw object maps rather than the loaded cards,
 * because the loaded-card view cannot answer the question. The rulebook's
 * own text ({@code RB:VI/cards-with-no-printed-cost}, :1058) makes the same
 * point: "A card that does not have an orange cost bubble in the lower right
 * hand corner has a cost that is undefined as far as other cards are
 * concerned. Undefined does not mean zero."</p>
 *
 * <p><b>The fix.</b> A cost is a value, and the value can be
 * {@link #UNDEFINED}. This class is that value: it carries the numeric cost
 * <em>or</em> the fact that no cost was assigned, and it can say which. A
 * caller that wants the number asks for it and must handle the case where
 * there isn't one.</p>
 *
 * <p><b>Value semantics.</b> This class overrides {@code equals} and
 * {@code hashCode}, because a value object that compares by identity cannot
 * answer the question "is this the same cost as that one?". Two defined costs
 * are equal when they carry the same number; an undefined cost is equal only
 * to an undefined cost, and never to a cost of zero. The distinction is the
 * whole point of the class, so it has to survive a comparison.</p>
 *
 * <p>The numeric cost itself is not changed by this row. The engine still
 * charges 0 for a card with no cost, because that is the engine's current
 * behaviour and changing it would be a behaviour change this row does not
 * authorise. What this row does is make the distinction <em>expressible</em>,
 * so that the cost rules can be written against it and the audit can read it
 * without re-deriving it from raw JSON.</p>
 *
 * <p>Java 8 source level (javac --release 8), stdlib only.</p>
 */
public final class CardCost {

    /**
     * The sentinel for "no cost was assigned".
     *
     * <p>Distinct from zero, and deliberately not a number: a caller cannot
     * accidentally treat it as a cost of 0 by arithmetic. Every operation
     * that would need the number says so.</p>
     */
    public static final CardCost UNDEFINED = new CardCost(-1, false, "no cost assigned");

    /** A cost of zero - a card that genuinely costs nothing. */
    public static final CardCost ZERO = new CardCost(0, true, "cost 0");

    private final int value;
    private final boolean defined;
    private final String provenance;

    private CardCost(int value, boolean defined, String provenance) {
        this.value = value;
        this.defined = defined;
        this.provenance = provenance;
    }

    /** A defined cost of the given value. */
    public static CardCost of(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("a cost cannot be negative: " + value);
        }
        return new CardCost(value, true, "cost " + value);
    }

    /**
     * The cost of a record, given whether the record carried a cost key and
     * what it said.
     *
     * @param hasCostKey whether the record carried a cost key at all.
     * @param value the value, when it did.
     * @return {@link #UNDEFINED} when the key was absent, otherwise a defined
     *         cost.
     */
    public static CardCost fromRecord(boolean hasCostKey, int value) {
        if (!hasCostKey) {
            return UNDEFINED;
        }
        return of(value);
    }

    /** True when a cost was assigned. */
    public boolean isDefined() {
        return defined;
    }

    /** True when no cost was assigned. */
    public boolean isUndefined() {
        return !defined;
    }

    /**
     * The numeric cost.
     *
     * @throws IllegalStateException when the cost is undefined. Callers that
     *         can proceed with an absent cost should use
     *         {@link #isDefined()} first.
     */
    public int getValue() {
        if (!defined) {
            throw new IllegalStateException("the cost is undefined; getValue() would"
                    + " silently turn an absent cost into a zero");
        }
        return value;
    }

    /**
     * The numeric cost, or the supplied default when the cost is undefined.
     *
     * <p>This is the honest way to ask "what will the engine charge", because
     * it makes the substitution visible at the call site.</p>
     */
    public int getValueOrDefault(int fallback) {
        return defined ? value : fallback;
    }

    /** Where this cost came from, for a report. */
    public String getProvenance() {
        return provenance;
    }

    /**
     * Value equality.
     *
     * <p>Two costs are equal when they are both defined and carry the same
     * number, or when they are both undefined. An undefined cost is never
     * equal to a cost of zero, and never to any other number: that
     * distinction is the reason this class exists, and an identity-based
     * comparison would let it be lost in a comparison the author believed
     * was checking it.</p>
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CardCost)) return false;
        CardCost o = (CardCost) other;
        if (this.defined != o.defined) return false;
        return !this.defined || this.value == o.value;
    }

    @Override
    public int hashCode() {
        // An undefined cost must not hash like a defined cost of -1: the
        // sentinel value is an implementation detail, and two undefined costs
        // must agree with equals().
        if (!defined) return 0;
        return value;
    }

    @Override
    public String toString() {
        return defined ? ("CardCost[" + value + "]") : "CardCost[UNDEFINED]";
    }
}
