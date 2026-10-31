package b5ccg.engine.effect.primitives;

import b5ccg.engine.effect.EffectKind;
import b5ccg.engine.effect.EffectLoadException;
import b5ccg.engine.effect.EffectOperation;
import b5ccg.engine.effect.EffectPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * WP-10 / B5-0028 (A3): the influence family.
 *
 * <p>Two primitives: {@code INFLUENCE_GAIN} and {@code INFLUENCE_LOSE}. The
 * rulebook treats influence as a resource that moves between players
 * ({@code RB:I/influence}), and the shipped {@code CardEffects} already
 * carries four separate influence tables - events the player gains it from,
 * events every player gains it from, influence the loser of a conflict loses,
 * and influence the winner steals.</p>
 *
 * <p><b>Who gains or loses is the target's job, not the primitive's.</b>
 * "All players gain 1 Influence" and "That player loses 1 Influence" use the
 * same two primitives; they differ in the entry's {@code target}, which
 * carries the scope and the controller relation. That separation is what
 * keeps the primitive list small, and A3's decision rule asks for about
 * twenty-five primitives across the whole pool.</p>
 *
 * <p>The rating-modification primitives ({@code INFLUENCE_RATING_MODIFY},
 * {@code INFLUENCE_CHANGE}) are in the {@link InfluenceRatingPrimitives} family
 * to keep this family <= 3 (B5-0050).</p>
 */
public final class InfluencePrimitives {

    /** The family name, as the registry reports it. */
    public static final String FAMILY = "INFLUENCE";

    /** The player gains influence. */
    public static final EffectPrimitive INFLUENCE_GAIN = new SimplePrimitive(
            "INFLUENCE_GAIN", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            return "amount=" + requiredInt(op, "amount", location);
        }
    };

    /** The player loses influence. */
    public static final EffectPrimitive INFLUENCE_LOSE = new SimplePrimitive(
            "INFLUENCE_LOSE", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            return "amount=" + requiredInt(op, "amount", location);
        }
    };

    private InfluencePrimitives() { }

    /** Every primitive in this family. */
    public static List<EffectPrimitive> all() {
        return Collections.unmodifiableList(Arrays.asList(INFLUENCE_GAIN, INFLUENCE_LOSE));
    }
}
