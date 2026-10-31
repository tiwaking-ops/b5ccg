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
 * <p>Two primitives: {@code STAT_MODIFY}, {@code SET_STAT}. The single most
 * common operation in the card pool.</p>
 *
 * <p>The {@code stat} parameter is a closed set: a {@code StatKey} name, or
 * {@code ALL} for "all stats" (rulebook :506-:518, the enhancement rules).
 * The {@code amount} parameter is an integer or one of the named derived
 * quantities.</p>
 *
 * <p>The derived/reduction primitives ({@code STAT_MODIFY_DERIVED},
 * {@code STAT_REDUCE}) are in the {@link StatDerivedPrimitives} family to
 * keep this family <= 3 (B5-0050).</p>
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

    /**
     * The base primitive: changes a stat by a delta.
     *
     * <p><b>ONE_SHOT was added to the allowed kinds in B5-0052.</b> The v0
     * declaration was {ONGOING, REPLACEMENT, TRIGGERED}, and "Motivated
     * Leaders" - a one-shot: "All your characters in Inner Circle gain +1
     * Leadership permanently. Discard after this effect triggers." - was
     * therefore a kind violation, one of 59 operations in the shipped
     * documents (measured by the B5-0051 verification). A one-shot stat
     * bonus is an ordinary card effect, so the primitive's declaration was
     * the thing that was wrong, not the data. The declaration is now
     * enforced by the loader, so this cannot drift again.</p>
     */
    public static final EffectPrimitive STAT_MODIFY = new SimplePrimitive(
            "STAT_MODIFY", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.REPLACEMENT,
            EffectKind.TRIGGERED) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            String stat = requiredEnum(op, "stat", location, STATS);
            String amount = requiredAmount(op, "amount", location, DERIVED);
            return "stat=" + stat + ", amount=" + amount;
        }
    };

    /**
     * Sets a stat to an absolute value, floor-only: the stat is raised to the
     * named value only when it is currently below it, never lowered.
     *
     * <p>Vorlon Enhancement prints "The character's Psi is raised to 15" -
     * the v0 vocabulary had STAT_MODIFY (a delta: "+2"), which cannot express
     * "set to 15" and cannot promise floor semantics. SET_STAT expresses both.</p>
     *
     * <p>Parameters: {@code stat} (a closed set from {@link #STATS}) and
     * {@code value} (an integer). Floor-only is built into the primitive's
     * meaning - it is not a flag - so the data cannot accidentally lower a
     * stat it was meant to raise.</p>
     */
    public static final EffectPrimitive SET_STAT = new SimplePrimitive(
            "SET_STAT", FAMILY,
            EffectKind.ONGOING, EffectKind.REPLACEMENT) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            String stat = requiredEnum(op, "stat", location, STATS);
            String value = requiredIntAsString(op, "value", location);
            return "stat=" + stat + ", value=" + value + ", floorOnly=true";
        }
    };

    private StatPrimitives() { }

    /** Every primitive in this family. */
    public static List<EffectPrimitive> all() {
        return Collections.unmodifiableList(Arrays.asList(STAT_MODIFY, SET_STAT));
    }
}
