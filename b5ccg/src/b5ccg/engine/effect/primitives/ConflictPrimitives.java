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
 * <p>Four primitives: {@code CONFLICT_BONUS}, {@code PARTICIPATION_GRANT},
 * {@code GRANT_EXTRA_CONFLICT}, {@code RECONQUER_LOCATION}.</p>
 *
 * <p>{@code CONFLICT_BONUS} is the "add N to your total" operation - "Secret
 * Police" adds 2 to your Intrigue total, "Shadow Strike" adds 4 to your
 * total, "War College" adds 2 to your Military total. The {@code stat}
 * parameter is a closed set: a {@code StatKey} name, or {@code ANY} for
 * "your total" with no stat named.</p>
 *
 * <p><b>{@code ROTATE_TARGET} moved to the ROTATION family in WP-11
 * (B5-0034).</b> It was here in v0 because "the loser must also rotate one
 * character" reads like a conflict operation, but rotation is a card-state
 * verb that the rulebook uses everywhere, and the Groups pilot needed its
 * inverse in the same place. The name is unchanged, so no data file that used
 * it has to change; only the family it is reported under.</p>
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

    /**
     * A card may take part in a conflict type it was otherwise barred from.
     *
     * <p>"Vorlon Enhancement": "That character gains +2 to all stats <em>and
     * the ability to participate in any conflict type</em>." That is a
     * permission, not a bonus and not a prohibition, so it does not fit
     * CONFLICT_BONUS (a total) and it is not a RESTRICT (which forbids). It
     * gets its own primitive rather than a direction flag on RESTRICT, because
     * a permission and a prohibition are different rules and the rulebook's
     * precedence rules treat them differently ({@code RB:VI/rules-of-precedence}).</p>
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
                Arrays.asList(CONFLICT_BONUS, PARTICIPATION_GRANT));
    }
}