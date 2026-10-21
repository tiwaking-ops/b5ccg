package b5ccg.engine.effect.primitives;

import b5ccg.engine.effect.EffectKind;
import b5ccg.engine.effect.EffectLoadException;
import b5ccg.engine.effect.EffectOperation;
import b5ccg.engine.effect.EffectPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * WP-10 / B5-0028 (A3): the conflict family.
 *
 * <p>Two primitives: {@code CONFLICT_BONUS} and {@code ROTATE_TARGET}.</p>
 *
 * <p>{@code CONFLICT_BONUS} is the "add N to your total" operation - "Secret
 * Police" adds 2 to your Intrigue total, "Shadow Strike" adds 4 to your
 * total, "War College" adds 2 to your Military total. The {@code stat}
 * parameter is a closed set: a {@code StatKey} name, or {@code ANY} for
 * "your total" with no stat named.</p>
 *
 * <p>{@code ROTATE_TARGET} is the "the loser must also rotate one character"
 * operation - the same verb the rulebook uses for a card being spent, and the
 * same word that appears in a Group's own rotation rule.</p>
 */
public final class ConflictPrimitives {

    /** The family name, as the registry reports it. */
    public static final String FAMILY = "CONFLICT";

    /** The stats a conflict bonus may name, plus {@code ANY}. */
    private static final String[] STATS = {
        "ANY", "DIPLOMACY", "INTRIGUE", "PSI", "LEADERSHIP", "MILITARY", "POWER"
    };

    /** The conflict types a participation grant may name, plus {@code ANY}. */
    private static final String[] CONFLICT_TYPES = {
        "ANY", "DIPLOMACY", "INTRIGUE", "PSI", "MILITARY"
    };

    /** A total in a conflict is increased. */
    public static final EffectPrimitive CONFLICT_BONUS = new SimplePrimitive(
            "CONFLICT_BONUS", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            String stat = requiredEnum(op, "stat", location, STATS);
            return "stat=" + stat + ", amount=" + requiredInt(op, "amount", location);
        }
    };

    /** A target card is rotated. */
    public static final EffectPrimitive ROTATE_TARGET = new SimplePrimitive(
            "ROTATE_TARGET", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            return "count=" + requiredInt(op, "count", location);
        }
    };

    /**
     * A card may take part in a conflict type it was otherwise barred from.
     *
     * <p>"Vorlon Enhancement": "That character gains +2 to all stats <em>and
     * the ability to participate in any conflict type</em>." That is a
     * permission, not a bonus and not a prohibition, so it does not fit
     * CONFLICT_BONUS (a total) and it is not a RESTRICT (which forbids). It
     * gets its own primitive rather than a direction flag on RESTRICT,
     * because a permission and a prohibition are different rules and the
     * rulebook's precedence rules treat them differently
     * ({@code RB:VI/rules-of-precedence}).</p>
     */
    public static final EffectPrimitive PARTICIPATION_GRANT = new SimplePrimitive(
            "PARTICIPATION_GRANT", FAMILY,
            EffectKind.ONGOING, EffectKind.REPLACEMENT) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            String type = requiredEnum(op, "conflictType", location, CONFLICT_TYPES);
            return "conflictType=" + type;
        }
    };

    private ConflictPrimitives() { }

    /** Every primitive in this family. */
    public static List<EffectPrimitive> all() {
        return Collections.unmodifiableList(
                Arrays.asList(CONFLICT_BONUS, ROTATE_TARGET, PARTICIPATION_GRANT));
    }
}
