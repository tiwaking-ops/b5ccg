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
 * <p>Two primitives: {@code DISCARD} and {@code DISCARD_SOURCE}. The
 * rulebook's discard rules are {@code RB:VI/discards}.</p>
 *
 * <p>Whether the discard is random or chosen is the entry's target, not a
 * parameter of the primitive: "Loser must discard 2 card(s)" and a card that
 * says "discard a card of your choice" differ in who chooses, and the target
 * field is where "whose" lives.</p>
 *
 * <p><b>{@code DISCARD_SOURCE} was added by WP-11 (B5-0034), the Groups
 * pilot.</b> "Motivated Leaders" reads "All your characters in Inner Circle
 * gain +1 Leadership permanently. Discard after this effect triggers." The
 * card that is discarded there is the group itself, and no target filter can
 * say "the card this entry is attached to" - the target field says whose
 * cards, and the source is not a member of any player's hand. A separate
 * primitive says it exactly, and a separate primitive says it exactly rather
 * than approximately, which is the point of the whole exercise.</p>
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

    /**
     * The source card is discarded from play.
     *
     * <p>The rulebook distinguishes three discards ({@code RB:VI/discards}):
     * a card discarded from a player's hand, a card whose effect expires and
     * is placed on the discard pile, and a card "discarded from play". This
     * primitive is the third: the card the effect belongs to leaves play.
     * "Motivated Leaders" is the case - the group triggers, its effect is
     * permanent, and the group itself is then discarded.</p>
     */
    public static final EffectPrimitive DISCARD_SOURCE = new SimplePrimitive(
            "DISCARD_SOURCE", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            return "";
        }
    };

    private DiscardPrimitives() { }

    /** Every primitive in this family. */
    public static List<EffectPrimitive> all() {
        return Collections.unmodifiableList(Arrays.asList(DISCARD, DISCARD_SOURCE));
    }
}
