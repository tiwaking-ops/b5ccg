package b5ccg.engine.effect.primitives;

import b5ccg.engine.effect.EffectKind;
import b5ccg.engine.effect.EffectLoadException;
import b5ccg.engine.effect.EffectOperation;
import b5ccg.engine.effect.EffectPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * WP-10 / B5-0028 (A3): the state family.
 *
 * <p>Two primitives: {@code STATE_ENTER} and {@code STATE_EXIT}. The rulebook
 * defines states in {@code RB:VI/states} - a named status a card carries
 * while it is in play.</p>
 *
 * <p>The state name is a free-text parameter rather than a closed enum
 * because the rulebook's state vocabulary is open-ended by design: any card
 * may say "this character is <em>under suspicion</em>", and the schema cannot
 * enumerate a language the cards invent. What the primitive does check is
 * that a name is <em>present</em> - a state operation with no state is a
 * statement about nothing.</p>
 */
public final class StatePrimitives {

    /** The family name, as the registry reports it. */
    public static final String FAMILY = "STATE";

    /** A card enters a state. */
    public static final EffectPrimitive STATE_ENTER = new SimplePrimitive(
            "STATE_ENTER", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            return "state=" + requiredText(op, "state", location);
        }
    };

    /** A card leaves a state. */
    public static final EffectPrimitive STATE_EXIT = new SimplePrimitive(
            "STATE_EXIT", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            return "state=" + requiredText(op, "state", location);
        }
    };

    private StatePrimitives() { }

    /** Every primitive in this family. */
    public static List<EffectPrimitive> all() {
        return Collections.unmodifiableList(Arrays.asList(STATE_ENTER, STATE_EXIT));
    }
}
