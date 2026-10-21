package b5ccg.engine.effect;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * WP-10 / B5-0028 (A3): one typed operation in an effect's operation list.
 *
 * <p>A3's effect-entry shape describes the {@code operations} field as an
 * "ordered list of typed primitives: modify ability, gain or lose influence,
 * change tension or unrest, add or purge marks, damage or heal, neutralize,
 * discard or return, draw, enter or exit a state, and so on".</p>
 *
 * <p><b>The order is data.</b> The list is ordered because the rulebook's own
 * precedence rules are the evaluation order (A3 principle 6) - an effect that
 * draws a card and then discards one is not the same effect as one that
 * discards and then draws. Nothing in this class sorts anything.</p>
 *
 * <p><b>Parameters are strings, deliberately.</b> A3 principle 2 is "No
 * expression language, no scripting. Parameters are typed enumerations and
 * small named predicates." The values here are therefore either closed
 * enumerations spelled by name, or small integers spelled as decimal text.
 * Keeping them as strings means the loader can reject an unrecognised value
 * against a closed set rather than coercing it, and it means the JSON is
 * diffable and human-editable.</p>
 */
public final class EffectOperation {

    private final String primitive;
    private final Map<String, String> params;

    public EffectOperation(String primitive, Map<String, String> params) {
        if (primitive == null || primitive.trim().length() == 0) {
            throw new IllegalArgumentException("operation primitive is null or empty");
        }
        this.primitive = primitive.trim();
        Map<String, String> copy = new LinkedHashMap<String, String>();
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (e.getKey() == null) continue;
                copy.put(e.getKey(), e.getValue() == null ? "" : e.getValue());
            }
        }
        this.params = Collections.unmodifiableMap(copy);
    }

    /**
     * The name of the primitive this operation invokes.
     *
     * <p>This is the registry key. The loader refuses an operation whose
     * primitive is not in the registry, so the name is never resolved by
     * reflection or by a string switch somewhere downstream - there is exactly
     * one table that says which primitives exist.</p>
     */
    public String getPrimitive() { return primitive; }

    /** The operation's parameters; never null, possibly empty. */
    public Map<String, String> getParams() { return params; }

    /** One parameter, or null when it is absent. */
    public String get(String key) { return params.get(key); }

    /** One parameter as an integer.
     *
     * @throws EffectLoadException when the parameter is absent or is not an
     *     integer. The message names the parameter, because "amount" is
     *     ambiguous when a primitive has several.
     */
    public int getInt(String key, String location) throws EffectLoadException {
        String raw = params.get(key);
        if (raw == null || raw.trim().length() == 0) {
            throw new EffectLoadException(location,
                    "operation '" + primitive + "' is missing the numeric parameter '" + key + "'");
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException nfe) {
            throw new EffectLoadException(location,
                    "operation '" + primitive + "' parameter '" + key + "' is not an integer: '"
                    + raw + "'");
        }
    }

    /**
     * A canonical, order-stable description of the operation.
     *
     * <p>Parameters are sorted by name so that the same operation always
     * renders the same way. A renderer that is not order-stable cannot be
     * compared against a golden value, which is the whole point of having
     * one.</p>
     */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(primitive).append('(');
        SortedMap<String, String> sorted = new TreeMap<String, String>(params);
        boolean first = true;
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        sb.append(')');
        return sb.toString();
    }

    @Override
    public String toString() {
        return describe();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EffectOperation)) return false;
        EffectOperation other = (EffectOperation) o;
        return primitive.equals(other.primitive) && params.equals(other.params);
    }

    @Override
    public int hashCode() {
        return primitive.hashCode() * 31 + params.hashCode();
    }
}
