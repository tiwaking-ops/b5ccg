package b5ccg.engine.effect.primitives;

import b5ccg.engine.effect.EffectKind;
import b5ccg.engine.effect.EffectLoadException;
import b5ccg.engine.effect.EffectOperation;
import b5ccg.engine.effect.EffectPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * WP-10 / B5-0028 (A3): the damage family.
 *
 * <p>Two primitives: {@code DAMAGE} and {@code HEAL}. The rulebook's damage
 * rules live with the character cards ({@code RB:IV/character-cards}) and
 * blanking ({@code RB:VI/bonuses-and-blanking}).</p>
 *
 * <p>Damage and heal are separate primitives rather than one signed
 * {@code AMOUNT}, because "loser loses 1 Influence" style signed amounts
 * invite a negative number where the rulebook has two different words, and
 * A3 principle 2 asks for "typed enumerations" rather than arithmetic on
 * free numbers.</p>
 *
 * <p>The combat-specific primitives ({@code DEAL_PRE_COMBAT_DAMAGE},
 * {@code HALVE_DAMAGE_TAKEN}, {@code REDIRECT_DAMAGE}) are in the
 * {@link DamageCombatPrimitives} family to keep this family <= 3 (B5-0050).</p>
 */
public final class DamagePrimitives {

    /** The family name, as the registry reports it. */
    public static final String FAMILY = "DAMAGE";

    /** Damage is dealt. */
    public static final EffectPrimitive DAMAGE = new SimplePrimitive(
            "DAMAGE", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            return "amount=" + requiredInt(op, "amount", location);
        }
    };

    /** Damage is healed. */
    public static final EffectPrimitive HEAL = new SimplePrimitive(
            "HEAL", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED,
            EffectKind.REPLACEMENT) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            return "amount=" + requiredInt(op, "amount", location);
        }
    };

    private DamagePrimitives() { }

    /** Every primitive in this family. */
    public static List<EffectPrimitive> all() {
        return Collections.unmodifiableList(Arrays.asList(DAMAGE, HEAL));
    }
}
