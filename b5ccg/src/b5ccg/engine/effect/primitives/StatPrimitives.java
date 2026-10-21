package b5ccg.engine.effect.primitives;

import b5ccg.engine.effect.EffectKind;
import b5ccg.engine.effect.EffectLoadException;
import b5ccg.engine.effect.EffectOperation;
import b5ccg.engine.effect.EffectPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * WP-10 / B5-0028 (A3): the stat-modifier family.
 *
 * <p>One family, one primitive: {@code STAT_MODIFY}, which changes a card's
 * ability. It is the single most common operation in the card pool and the
 * one the proposal's migration analysis singles out, because
 * "Vorlon Enhancement" ("That character gains +2 to all stats") and
 * "Triluminary" ("That character gains +2 Psi and +2 Diplomacy") are the same
 * operation with different parameters, and are currently two special-cased
 * literals in {@code CardEffects}.</p>
 *
 * <p>The {@code stat} parameter is a closed set: a {@code StatKey} name, or
 * {@code ALL} for "all stats" (rulebook :506-:518, the enhancement rules).
 * The {@code amount} parameter is an integer or one of the named derived
 * quantities - "Commercial Telepaths" adds half its Psi (rounded up), which
 * is not a constant.</p>
 */
public final class StatPrimitives {

    /** The family name, as the registry reports it. */
    public static final String FAMILY = "STAT";

    /** The stats a modification may name, plus {@code ALL}. */
    private static final String[] STATS = {
        "ALL", "DIPLOMACY", "INTRIGUE", "PSI", "LEADERSHIP", "MILITARY", "POWER"
    };

    /** The derived amounts a stat modification may name. */
    private static final String[] DERIVED = { "HALF_PSI_ROUNDED_UP" };

    /** The single primitive of this family. */
    public static final EffectPrimitive STAT_MODIFY = new SimplePrimitive(
            "STAT_MODIFY", FAMILY,
            EffectKind.ONGOING, EffectKind.REPLACEMENT, EffectKind.TRIGGERED) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            String stat = requiredEnum(op, "stat", location, STATS);
            String amount = requiredAmount(op, "amount", location, DERIVED);
            return "stat=" + stat + ", amount=" + amount;
        }
    };

    private StatPrimitives() { }

    /** Every primitive in this family. */
    public static List<EffectPrimitive> all() {
        return Collections.unmodifiableList(Arrays.asList(STAT_MODIFY));
    }
}
