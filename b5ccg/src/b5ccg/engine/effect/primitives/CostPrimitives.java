package b5ccg.engine.effect.primitives;

import b5ccg.engine.effect.EffectKind;
import b5ccg.engine.effect.EffectLoadException;
import b5ccg.engine.effect.EffectOperation;
import b5ccg.engine.effect.EffectPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * WP-10 / B5-0028 (A3): the cost-modifier family.
 *
 * <p>One primitive: {@code COST_MODIFIER}, which changes what a card costs.
 * The rulebook's cost rules are {@code RB:I/anatomy-of-a-card} and
 * {@code RB:VI/cumulative-effects}; the "cost reductions" combinator is one
 * of the terms A3 says needs new schema vocabulary.</p>
 *
 * <p>The {@code stat} parameter is a closed set of the two things a cost can
 * be increased: the influence cost of playing a card, and the cost of
 * initiating a conflict. "Babylon 5 Unrest" names both - "All players'
 * Influence costs for playing cards increase by 1" in the Premiere printing,
 * and the Deluxe printing adds "also increases conflict initiation cost by
 * 1". One primitive, two entries, because a printing difference is a data
 * difference and not a new operation.</p>
 */
public final class CostPrimitives {

    /** The family name, as the registry reports it. */
    public static final String FAMILY = "COST";

    /** The costs a modifier may name. */
    private static final String[] COSTS = { "INFLUENCE", "CONFLICT_INITIATION", "SPONSOR" };

    /** A cost is changed. */
    public static final EffectPrimitive COST_MODIFIER = new SimplePrimitive(
            "COST_MODIFIER", FAMILY, EffectKind.COST_MODIFIER, EffectKind.ONGOING) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            String cost = requiredEnum(op, "cost", location, COSTS);
            return "cost=" + cost + ", amount=" + requiredInt(op, "amount", location);
        }
    };

    private CostPrimitives() { }

    /** Every primitive in this family. */
    public static List<EffectPrimitive> all() {
        return Collections.unmodifiableList(Arrays.asList(COST_MODIFIER));
    }
}
