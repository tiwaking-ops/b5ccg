package b5ccg.engine.effect.primitives;

import b5ccg.engine.effect.EffectKind;
import b5ccg.engine.effect.EffectLoadException;
import b5ccg.engine.effect.EffectOperation;
import b5ccg.engine.effect.EffectPrimitive;
import b5ccg.engine.effect.EffectRegistry;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * WP-10 / B5-0028 (A3): the escape hatch.
 *
 * <p>A3 principle 1 is "Hybrid, not pure. Declarative records reference a
 * registry of named primitives implemented in Java. <em>A small keyed escape
 * hatch handles the genuinely bespoke tail.</em>" This family is that hatch,
 * and it is the one primitive in the whole registry that does not describe
 * what it does.</p>
 *
 * <p><b>Keyed, not arbitrary.</b> A {@code BESPOKE} operation names a
 * handler key; the registry holds the table of keys it will honour, and a key
 * that is not in that table is a load error. So the hatch cannot become a
 * place where a data file names arbitrary code: it can only name one of the
 * handlers the engine has actually published, and publishing one is a
 * deliberate act.</p>
 *
 * <p><b>The v0 registry ships zero handlers.</b> That is the strongest
 * statement the schema can make about the size of the bespoke tail: the
 * escape hatch is <em>specified</em> and <em>empty</em>. A card that needs
 * bespoke behaviour cannot be written yet, and that is visible at load time
 * rather than discovered later.</p>
 */
public final class BespokePrimitives {

    /** The family name, as the registry reports it. */
    public static final String FAMILY = "BESPOKE";

    /** The primitive name a data file uses to reach the escape hatch. */
    public static final String PRIMITIVE_NAME = "BESPOKE";

    /** The escape hatch. */
    public static final EffectPrimitive BESPOKE = new SimplePrimitive(
            "BESPOKE", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED,
            EffectKind.REPLACEMENT, EffectKind.RESTRICTION, EffectKind.COST_MODIFIER,
            EffectKind.TRAIT) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            return "handler=" + requiredText(op, "handler", location);
        }

        @Override
        public void checkRegistered(EffectRegistry registry, EffectOperation op, String location)
                throws EffectLoadException {
            String key = op.get("handler");
            if (key == null || key.trim().length() == 0) {
                throw new EffectLoadException(location,
                        "a bespoke operation must name a handler key");
            }
            if (!registry.hasBespokeHandler(key.trim())) {
                throw new EffectLoadException(location,
                        "bespoke handler '" + key.trim() + "' is not registered; the registry holds "
                        + registry.bespokeHandlers().size() + " handler(s): "
                        + registry.knownBespokeHandlerKeys());
            }
        }
    };

    private BespokePrimitives() { }

    /** Every primitive in this family. */
    public static List<EffectPrimitive> all() {
        return Collections.unmodifiableList(Arrays.asList(BESPOKE));
    }
}
