package b5ccg.engine.effect.primitives;

import b5ccg.engine.effect.EffectKind;
import b5ccg.engine.effect.EffectLoadException;
import b5ccg.engine.effect.EffectOperation;
import b5ccg.engine.effect.EffectPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * WP-11 / B5-0034 (A3): the rotation family.
 *
 * <p>Two primitives: {@code ROTATE_TARGET} and {@code UNROTATE_TARGET}.</p>
 *
 * <p><b>Why the family exists, and why it holds both.</b> In v0 the
 * {@code ROTATE_TARGET} primitive lived in the CONFLICT family, which is
 * where a "the loser must also rotate one character" operation naturally
 * sat. But rotation is not a conflict concept: it is a card-state concept
 * that the rulebook uses everywhere ({@code RB:III/ready-and-rotated-cards}),
 * and the Groups pilot needed its inverse - "Unrotate one of your fleets"
 * ("Damage Control Team") - which is not a conflict operation at all. Putting
 * the two in one family says what they are: the two directions of the same
 * card-state verb. A3 principle 5 asks for one class per primitive family,
 * and a family of one that only exists to hold a single operation is the
 * god-class smell the principle warns about, so {@code ROTATE_TARGET} moved
 * here rather than being duplicated.</p>
 *
 * <p><b>Unrotating is not readying a card for its owner.</b> The rulebook's
 * Ready round readies every rotated card at the start of the turn
 * ({@code RB:III/ready-round}); this primitive is a card <em>effect</em> that
 * makes a card ready, outside that step. The target is chosen by the entry's
 * target filter, and the operation itself carries no target of its own.</p>
 */
public final class RotationPrimitives {

    /** The family name, as the registry reports it. */
    public static final String FAMILY = "ROTATION";

    /** A target card is rotated - it is spent. */
    public static final EffectPrimitive ROTATE_TARGET = new SimplePrimitive(
            "ROTATE_TARGET", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            return "count=" + requiredInt(op, "count", location);
        }
    };

    /**
     * A target card is made ready - it is unrotated.
     *
     * <p>"Damage Control Team": "Rotate this Group after any Military
     * conflict. Unrotate one of your fleets." The rulebook calls a card that
     * has been rotated a card that is "set on its side", and calls it ready
     * when it is "set upright" again ({@code RB:III/ready-and-rotated-cards}),
     * so the operation is the inverse of {@code ROTATE_TARGET} and takes no
     * count: one card is made ready.</p>
     */
    public static final EffectPrimitive UNROTATE_TARGET = new SimplePrimitive(
            "UNROTATE_TARGET", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            return "";
        }
    };

    private RotationPrimitives() { }

    /** Every primitive in this family. */
    public static List<EffectPrimitive> all() {
        return Collections.unmodifiableList(
                Arrays.asList(ROTATE_TARGET, UNROTATE_TARGET));
    }
}
