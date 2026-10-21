package b5ccg.engine.effect.primitives;

import b5ccg.engine.effect.EffectKind;
import b5ccg.engine.effect.EffectLoadException;
import b5ccg.engine.effect.EffectOperation;
import b5ccg.engine.effect.EffectPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * WP-10 / B5-0028 (A3): the draw family.
 *
 * <p>One primitive: {@code DRAW}. A3 lists "draw" among the operations, and
 * the rulebook's draw step is {@code RB:III/draw-round}.</p>
 *
 * <p><b>Replacement is the entry's stacking rule, not a second primitive.</b>
 * "Draw 2 cards. If you have Morden in play, draw 3 cards instead." is two
 * entries against one card: one with stacking REPLACING, one with stacking
 * ADDITIVE. Modelling it as one primitive with a "maybe 3" parameter would
 * put a conditional into a parameter, which is the scripting trap A3
 * principle 2 warns against.</p>
 */
public final class DrawPrimitives {

    /** The family name, as the registry reports it. */
    public static final String FAMILY = "DRAW";

    /** Cards are drawn. */
    public static final EffectPrimitive DRAW = new SimplePrimitive(
            "DRAW", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED,
            EffectKind.REPLACEMENT) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            return "count=" + requiredInt(op, "count", location);
        }
    };

    private DrawPrimitives() { }

    /** Every primitive in this family. */
    public static List<EffectPrimitive> all() {
        return Collections.unmodifiableList(Arrays.asList(DRAW));
    }
}
