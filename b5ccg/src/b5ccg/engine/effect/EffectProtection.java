package b5ccg.engine.effect;

/**
 * WP-10 / B5-0028 (A3): whether an effect can be negated, blanked or reversed.
 *
 * <p>A3's effect-entry shape anchors this field at rulebook :1142 and :1164:
 * an effect is <em>irrevocable</em> or <em>boldface</em> - "cannot be blanked,
 * negated or reversed".</p>
 *
 * <p>Boldface is the printed-text convention: the words that matter are set
 * in bold on the card. The schema records which of the two protections an
 * effect carries so the engine does not have to re-derive it from card text
 * at runtime (A3 principle 3: "Text is the specification and the display
 * string, never parsed at runtime").</p>
 */
public enum EffectProtection {

    /** Ordinary effect: blankable, negatable, reversible. */
    NONE,

    /** Cannot be blanked, negated or reversed (rulebook :1142). */
    IRREVOCABLE,

    /** Set in bold on the card: cannot be blanked, negated or reversed (:1164). */
    BOLDFACE;

    /** Parses a protection, failing closed. */
    public static EffectProtection parse(String raw, String location) throws EffectLoadException {
        if (raw == null || raw.trim().length() == 0) {
            throw new EffectLoadException(location, "missing effect protection");
        }
        String v = raw.trim();
        for (EffectProtection p : values()) {
            if (p.name().equals(v)) {
                return p;
            }
        }
        throw new EffectLoadException(location,
                "unknown effect protection '" + v + "'; known values are " + names());
    }

    /** The known protections, comma-separated, in declaration order. */
    public static String names() {
        StringBuilder sb = new StringBuilder();
        for (EffectProtection p : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p.name());
        }
        return sb.toString();
    }
}
