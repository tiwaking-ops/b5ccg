package b5ccg.engine.effect;

/**
 * WP-10 / B5-0028 (A3): the event that sets an effect off.
 *
 * <p>A3's effect-entry shape describes the {@code trigger} field as "event
 * type plus filter (conflict resolved and initiator won; attack made;
 * influence gained; vote called)", anchored at rulebook :775-:779
 * ({@code RB:VI/triggered-effects}).</p>
 *
 * <p><b>Closed, because the filters are named elsewhere.</b> The event
 * itself is a small closed set - the rulebook's own events - while the
 * filter ("and the initiator won") belongs on the entry's
 * {@link EffectCondition}s, not inside a free-text trigger. A trigger of
 * "CONFLICT_RESOLVED" plus a condition of "the initiator won" says the same
 * thing as a free-text "conflict resolved and initiator won", but the first
 * can be checked against a list and the second cannot.</p>
 */
public enum EffectTrigger {

    /** The card is played. */
    ON_PLAY,

    /** The card resolves. */
    ON_RESOLUTION,

    /** The start of a round. */
    ROUND_START,

    /** The start of a turn. */
    TURN_START,

    /** A conflict begins. */
    CONFLICT_BEGINS,

    /** A conflict resolves. */
    CONFLICT_RESOLVED,

    /** A conflict is won by a player. */
    CONFLICT_WON,

    /** A conflict of the Intrigue type begins. */
    CONFLICT_TYPE_INTRIGUE,

    /** A conflict of the Military type begins. */
    CONFLICT_TYPE_MILITARY,

    /** A conflict of the Diplomacy type begins. */
    CONFLICT_TYPE_DIPLOMACY,

    /**
     * A conflict of the Psi type begins.
     *
     * <p><b>Added by WP-11 (B5-0034), the Groups pilot.</b> The v0 trigger set
     * named three of the rulebook's four conflict types
     * ({@code RB:IV/type-of-conflict} names Military, Diplomatic, Intrigue
     * and Psi), and three group cards - "Military Telepaths", "Imperial
     * Telepaths" and "Psi Corps Intelligence" - rotate during a Psi conflict.
     * A trigger vocabulary that cannot name a Psi conflict cannot express
     * those three cards, so the missing name is added here rather than worked
     * around.</p>
     */
    CONFLICT_TYPE_PSI,

    /** A character is damaged. */
    CHARACTER_DAMAGED,

    /** A card is discarded. */
    CARD_DISCARDED;

    /** Parses a trigger, failing closed. */
    public static EffectTrigger parse(String raw, String location) throws EffectLoadException {
        if (raw == null || raw.trim().length() == 0) {
            throw new EffectLoadException(location, "missing effect trigger");
        }
        String v = raw.trim();
        for (EffectTrigger t : values()) {
            if (t.name().equals(v)) {
                return t;
            }
        }
        throw new EffectLoadException(location,
                "unknown effect trigger '" + v + "'; known triggers are " + names());
    }

    /** The known triggers, comma-separated, in declaration order. */
    public static String names() {
        StringBuilder sb = new StringBuilder();
        for (EffectTrigger t : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.name());
        }
        return sb.toString();
    }
}
