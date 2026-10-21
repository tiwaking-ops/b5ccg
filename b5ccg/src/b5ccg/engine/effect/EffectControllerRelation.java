package b5ccg.engine.effect;

/**
 * WP-10 / B5-0028 (A3): whose cards an effect may act on.
 *
 * <p>A3's effect-entry shape names "controller relation" among the target
 * filters, anchored at rulebook :827-:832. "ANY" is a <em>filter</em>, not a
 * value - A3 says so explicitly - so it is modelled as a member of this enum
 * rather than as the absence of a value. That distinction matters: an entry
 * that says "ANY" is making a statement, and a loader that dropped the field
 * would be asserting something the card did not.</p>
 */
public enum EffectControllerRelation {

    /** Any player may be the target. */
    ANY,

    /** Only the player who played the source card. */
    SELF,

    /** Only a player other than the source card's controller. */
    OPPONENT;

    /** Parses a controller relation, failing closed. */
    public static EffectControllerRelation parse(String raw, String location)
            throws EffectLoadException {
        if (raw == null || raw.trim().length() == 0) {
            throw new EffectLoadException(location, "missing controller relation");
        }
        String v = raw.trim();
        for (EffectControllerRelation r : values()) {
            if (r.name().equals(v)) {
                return r;
            }
        }
        throw new EffectLoadException(location,
                "unknown controller relation '" + v + "'; known values are " + names());
    }

    /** The known relations, comma-separated, in declaration order. */
    public static String names() {
        StringBuilder sb = new StringBuilder();
        for (EffectControllerRelation r : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(r.name());
        }
        return sb.toString();
    }
}
