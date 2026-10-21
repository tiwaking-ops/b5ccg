package b5ccg.engine.effect;

/**
 * WP-10 / B5-0028 (A3): what an effect acts on.
 *
 * <p>A3's effect-entry shape lists the target fields as "card type; race or
 * loyalty filter (ANY is a filter, not a value); controller relation; row
 * (Inner Circle or Supporting); status (ready, rotated, neutralized); mark or
 * trait filter; scope (card, faction, race, global)", anchored at rulebook
 * :827-:832 and :1160.</p>
 *
 * <p>Each of those is a separate field on {@link EffectTarget}; this enum is
 * the last of them, the <em>scope</em>: how wide the effect reaches.</p>
 */
public enum EffectScope {

    /** One card. */
    CARD,

    /** Every card a faction controls. */
    FACTION,

    /** Every card of one race. */
    RACE,

    /** Every player. */
    GLOBAL;

    /** Parses a scope, failing closed. */
    public static EffectScope parse(String raw, String location) throws EffectLoadException {
        if (raw == null || raw.trim().length() == 0) {
            throw new EffectLoadException(location, "missing effect scope");
        }
        String v = raw.trim();
        for (EffectScope s : values()) {
            if (s.name().equals(v)) {
                return s;
            }
        }
        throw new EffectLoadException(location,
                "unknown effect scope '" + v + "'; known scopes are " + names());
    }

    /** The known scopes, comma-separated, in declaration order. */
    public static String names() {
        StringBuilder sb = new StringBuilder();
        for (EffectScope s : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s.name());
        }
        return sb.toString();
    }
}
