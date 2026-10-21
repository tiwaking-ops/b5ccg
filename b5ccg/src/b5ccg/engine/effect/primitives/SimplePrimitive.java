package b5ccg.engine.effect.primitives;

import b5ccg.engine.effect.EffectKind;
import b5ccg.engine.effect.EffectLoadException;
import b5ccg.engine.effect.EffectOperation;
import b5ccg.engine.effect.EffectPrimitive;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * WP-10 / B5-0028 (A3): the shared implementation behind every primitive.
 *
 * <p>Each primitive family class builds its primitives from this one, so the
 * contract is written down once: a name, the kinds it may appear under, the
 * family it belongs to, and a parameter check that fails closed.</p>
 *
 * <p>Package-private on purpose: the registry is the only thing that should
 * hold primitives, and a caller that reaches past it can bypass the
 * fail-closed load.</p>
 */
abstract class SimplePrimitive implements EffectPrimitive {

    private final String name;
    private final Set<EffectKind> kinds;
    private final String family;

    SimplePrimitive(String name, String family, EffectKind... kinds) {
        this.name = name;
        this.family = family;
        EnumSet<EffectKind> set = EnumSet.noneOf(EffectKind.class);
        for (EffectKind k : kinds) {
            set.add(k);
        }
        this.kinds = Collections.unmodifiableSet(set);
    }

    public String name() { return name; }

    public Set<EffectKind> kinds() { return kinds; }

    public String family() { return family; }

    /**
     * The body of a primitive: check the parameters this primitive needs and
     * return the canonical text.
     *
     * @param params the parameters present, in the order the data gave them
     */
    protected abstract String body(EffectOperation op, String location)
            throws EffectLoadException;

    public String describe(EffectOperation op, String location) throws EffectLoadException {
        if (op == null) {
            throw new EffectLoadException(location, "operation is null");
        }
        return name + "(" + body(op, location) + ")";
    }

    /** A required integer parameter. */
    protected static int requiredInt(EffectOperation op, String key, String location)
            throws EffectLoadException {
        return op.getInt(key, location);
    }

    /**
     * A required parameter whose value must be one of a closed set.
     *
     * @param allowed the accepted values, in the order they should be offered
     *     in an error message
     */
    protected static String requiredEnum(EffectOperation op, String key, String location,
                                         String[] allowed) throws EffectLoadException {
        String raw = op.get(key);
        if (raw == null || raw.trim().length() == 0) {
            throw new EffectLoadException(location,
                    "operation '" + op.getPrimitive() + "' is missing the parameter '" + key
                    + "' (one of " + join(allowed) + ")");
        }
        String v = raw.trim();
        for (String a : allowed) {
            if (a.equals(v)) return v;
        }
        throw new EffectLoadException(location,
                "operation '" + op.getPrimitive() + "' parameter '" + key + "' is '" + v
                + "'; it must be one of " + join(allowed));
    }

    /**
     * A required amount: either a plain integer, or one of a closed set of
     * derived quantities.
     *
     * <p>The derived values exist because some printed text computes its own
     * number - "adds half its Psi (rounded up) to its Diplomacy" is not a
     * constant, and writing it as one would be a lie about the card. A3
     * principle 2 says the parameters are "typed enumerations and small named
     * predicates", and a named derived quantity is exactly that.</p>
     */
    protected static String requiredAmount(EffectOperation op, String key, String location,
                                           String[] derived) throws EffectLoadException {
        String raw = op.get(key);
        if (raw == null || raw.trim().length() == 0) {
            throw new EffectLoadException(location,
                    "operation '" + op.getPrimitive() + "' is missing the parameter '" + key
                    + "' (an integer" + (derived.length == 0 ? "" : " or one of " + join(derived))
                    + ")");
        }
        String v = raw.trim();
        try {
            Integer.parseInt(v);
            return v;
        } catch (NumberFormatException nfe) {
            for (String d : derived) {
                if (d.equals(v)) return v;
            }
            throw new EffectLoadException(location,
                    "operation '" + op.getPrimitive() + "' parameter '" + key + "' is '" + v
                    + "'; it must be an integer" + (derived.length == 0 ? "" : " or one of "
                        + join(derived)));
        }
    }

    /** A required free-text parameter: present, but not drawn from a set. */
    protected static String requiredText(EffectOperation op, String key, String location)
            throws EffectLoadException {
        String raw = op.get(key);
        if (raw == null || raw.trim().length() == 0) {
            throw new EffectLoadException(location,
                    "operation '" + op.getPrimitive() + "' is missing the parameter '" + key + "'");
        }
        return raw.trim();
    }

    /** An optional free-text parameter. */
    protected static String optionalText(EffectOperation op, String key) {
        String raw = op.get(key);
        return raw == null ? null : raw.trim();
    }

    private static String join(String[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(values[i]);
        }
        return sb.toString();
    }
}
