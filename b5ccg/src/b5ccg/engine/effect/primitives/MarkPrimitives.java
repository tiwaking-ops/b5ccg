package b5ccg.engine.effect.primitives;

import b5ccg.engine.effect.EffectKind;
import b5ccg.engine.effect.EffectLoadException;
import b5ccg.engine.effect.EffectOperation;
import b5ccg.engine.effect.EffectPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * WP-10 / B5-0028 (A3): the mark family.
 *
 * <p>Two primitives: {@code MARK_GRANT} and {@code MARK_PURGE}. The rulebook
 * at :836-:844 ({@code RB:VI/marks}) is the anchor: "Many cards provide marks
 * to characters. E.g., the Agenda 'Servants of Order' provides the ambassador
 * of the faction with 1 Vorlon Mark while the agenda is in play."</p>
 *
 * <p><b>The rulebook supplements the card text, and the data has to say so.</b>
 * "Contact with Shadows" reads "Play any time. Draw 2 cards. If you have
 * Morden in play, draw 3 cards instead." - it says nothing about a mark. Yet
 * rulebook :844 names it: "Many events, such as Contact with Shadows, provide
 * a permanent mark and do not remain in play." So the mark this card provides
 * is real, and it is not in the card's text. An effect entry that is written
 * from the card text alone would be <em>wrong</em>, and the only thing that
 * stops that is the provenance field's rule-IDs pointing at the paragraph
 * that supplies the missing piece.</p>
 */
public final class MarkPrimitives {

    /** The family name, as the registry reports it. */
    public static final String FAMILY = "MARK";

    /** The mark types a mark operation may name. */
    private static final String[] MARKS = {
        "SHADOW", "VORLON", "DOOM", "STRIFE", "CONSPIRACY", "DESTINY"
    };

    /** A mark is attached. */
    public static final EffectPrimitive MARK_GRANT = new SimplePrimitive(
            "MARK_GRANT", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            String mark = requiredEnum(op, "mark", location, MARKS);
            return "mark=" + mark + ", count=" + requiredInt(op, "count", location);
        }
    };

    /** A mark is purged. */
    public static final EffectPrimitive MARK_PURGE = new SimplePrimitive(
            "MARK_PURGE", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED,
            EffectKind.RESTRICTION) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            String mark = requiredEnum(op, "mark", location, MARKS);
            return "mark=" + mark + ", count=" + requiredInt(op, "count", location);
        }
    };

    private MarkPrimitives() { }

    /** Every primitive in this family. */
    public static List<EffectPrimitive> all() {
        return Collections.unmodifiableList(Arrays.asList(MARK_GRANT, MARK_PURGE));
    }
}
