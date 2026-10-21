package b5ccg.engine.effect;

/**
 * WP-10 / B5-0028 (A3): how two effects of the same kind combine.
 *
 * <p>A3's effect-entry shape anchors this field at rulebook :860-:862: the
 * stacking rule is either <em>additive</em> ("an additional card") or
 * <em>replacing</em> ("draw 2 instead of 1", non-cumulative). Nothing here
 * decides how a replacement combines with an addition - that is
 * {@code RB:VI/rules-of-precedence}, and it is the engine's job, not the
 * schema's.</p>
 */
public enum EffectStacking {

    /** Cumulative: two effects of this kind both apply. */
    ADDITIVE,

    /** Non-cumulative: a later effect of this kind replaces an earlier one. */
    REPLACING;

    /** Parses a stacking rule, failing closed. */
    public static EffectStacking parse(String raw, String location) throws EffectLoadException {
        if (raw == null || raw.trim().length() == 0) {
            throw new EffectLoadException(location, "missing effect stacking");
        }
        String v = raw.trim();
        for (EffectStacking s : values()) {
            if (s.name().equals(v)) {
                return s;
            }
        }
        throw new EffectLoadException(location,
                "unknown effect stacking '" + v + "'; known values are " + names());
    }

    /** The known stacking rules, comma-separated, in declaration order. */
    public static String names() {
        StringBuilder sb = new StringBuilder();
        for (EffectStacking s : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s.name());
        }
        return sb.toString();
    }
}
