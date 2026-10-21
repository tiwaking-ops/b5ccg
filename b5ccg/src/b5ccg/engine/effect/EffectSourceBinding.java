package b5ccg.engine.effect;

/**
 * WP-10 / B5-0028 (A3): how an effect is bound to the card that grants it.
 *
 * <p>A3's effect-entry shape anchors this field at rulebook :844
 * ({@code RB:VI/marks}): "source binding | mark or bonus provision with
 * cut-off purge when the source leaves".</p>
 *
 * <p>The rulebook's rule is that "The source of any individual mark is not
 * recorded", and that when a character is cut off from a source of a mark
 * "then that character must purge a mark of that type". So a mark-granting
 * effect is bound to its source in exactly one way - it is purged when the
 * source goes - and that is what {@link #CUTOFF_PURGE} records. The engine
 * already implements the purge in {@code Player.purgeMarks} (fixed by
 * B5-0033); this enum says which effects are subject to it.</p>
 */
public enum EffectSourceBinding {

    /** The effect is not bound to a source: nothing is purged when it ends. */
    NONE,

    /** The effect provides a mark, and the mark is purged when the source leaves. */
    CUTOFF_PURGE,

    /**
     * The effect provides a mark that cannot be cut off.
     *
     * <p>Rulebook :844: "Many events, such as Contact with Shadows, provide
     * a permanent mark and do not remain in play. A character with such a
     * mark cannot be 'cut off' from its source."</p>
     */
    PERMANENT_MARK;

    /** Parses a source binding, failing closed. */
    public static EffectSourceBinding parse(String raw, String location)
            throws EffectLoadException {
        if (raw == null || raw.trim().length() == 0) {
            throw new EffectLoadException(location, "missing source binding");
        }
        String v = raw.trim();
        for (EffectSourceBinding b : values()) {
            if (b.name().equals(v)) {
                return b;
            }
        }
        throw new EffectLoadException(location,
                "unknown source binding '" + v + "'; known bindings are " + names());
    }

    /** The known bindings, comma-separated, in declaration order. */
    public static String names() {
        StringBuilder sb = new StringBuilder();
        for (EffectSourceBinding b : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(b.name());
        }
        return sb.toString();
    }
}
