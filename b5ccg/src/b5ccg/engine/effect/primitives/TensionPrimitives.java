package b5ccg.engine.effect.primitives;

import b5ccg.engine.effect.EffectKind;
import b5ccg.engine.effect.EffectLoadException;
import b5ccg.engine.effect.EffectOperation;
import b5ccg.engine.effect.EffectPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * WP-10 / B5-0028 (A3): the tension and unrest family.
 *
 * <p>Two primitives: {@code TENSION_CHANGE} and {@code UNREST_CHANGE}. The
 * rulebook keeps tension and unrest between 1 and 5
 * ({@code RB:VI/tension-and-unrest}, and the invariant at :284 that the
 * B5-0030 checker already asserts).</p>
 *
 * <p>They are two primitives rather than one with a "which tracker" parameter
 * because the two trackers have different owners and different effects on
 * play, and a primitive that can move either one invites a caller to move
 * both.</p>
 */
public final class TensionPrimitives {

    /** The family name, as the registry reports it. */
    public static final String FAMILY = "TENSION";

    /** The tension tracker moves. */
    public static final EffectPrimitive TENSION_CHANGE = new SimplePrimitive(
            "TENSION_CHANGE", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            return "amount=" + requiredInt(op, "amount", location);
        }
    };

    /** The unrest tracker moves. */
    public static final EffectPrimitive UNREST_CHANGE = new SimplePrimitive(
            "UNREST_CHANGE", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            return "amount=" + requiredInt(op, "amount", location);
        }
    };

    private TensionPrimitives() { }

    /** Every primitive in this family. */
    public static List<EffectPrimitive> all() {
        return Collections.unmodifiableList(Arrays.asList(TENSION_CHANGE, UNREST_CHANGE));
    }
}
