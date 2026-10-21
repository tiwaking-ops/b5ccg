package b5ccg.engine.effect.primitives;

import b5ccg.engine.effect.EffectKind;
import b5ccg.engine.effect.EffectLoadException;
import b5ccg.engine.effect.EffectOperation;
import b5ccg.engine.effect.EffectPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * WP-10 / B5-0028 (A3): the discard family.
 *
 * <p>One primitive: {@code DISCARD}. The rulebook's discard rules are
 * {@code RB:VI/discards}.</p>
 *
 * <p>Whether the discard is random or chosen is the entry's target, not a
 * parameter of the primitive: "Loser must discard 2 card(s)" and a card that
 * says "discard a card of your choice" differ in who chooses, and the target
 * field is where "whose" lives.</p>
 */
public final class DiscardPrimitives {

    /** The family name, as the registry reports it. */
    public static final String FAMILY = "DISCARD";

    /** Cards are discarded. */
    public static final EffectPrimitive DISCARD = new SimplePrimitive(
            "DISCARD", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED,
            EffectKind.RESTRICTION) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            return "count=" + requiredInt(op, "count", location);
        }
    };

    private DiscardPrimitives() { }

    /** Every primitive in this family. */
    public static List<EffectPrimitive> all() {
        return Collections.unmodifiableList(Arrays.asList(DISCARD));
    }
}
