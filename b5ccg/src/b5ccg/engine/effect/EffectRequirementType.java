package b5ccg.engine.effect;

/**
 * WP-10 / B5-0028 (A3): the cost block - what a player must do to play a card.
 *
 * <p>A3's effect-entry shape describes the {@code requirements} field as
 * "cost block: apply or lose influence, rotate an Inner Circle sponsor, purge
 * marks, discard, take damage, control N marks or characters", anchored at
 * rulebook :1192.</p>
 *
 * <p>These are requirements, not operations: they are checked <em>before</em>
 * the effect resolves, and failing one means the card is not playable. That
 * distinction is the difference between this enum and
 * {@link EffectOperation}'s primitives, and it is why the two are separate
 * types rather than one enum with a flag.</p>
 */
public enum EffectRequirementType {

    /** "Apply N Influence" - a cost the player pays. */
    APPLY_INFLUENCE,

    /** "Lose N Influence" - a cost paid by losing influence rather than applying it. */
    LOSE_INFLUENCE,

    /** "Rotate an Inner Circle sponsor" - a sponsor must be spent. */
    ROTATE_SPONSOR,

    /** "Apply Influence as a cost for an activated effect" - e.g., Crystal Cities, Pulling Strings. */
    APPLY_INFLUENCE_COST,

    /**
     * "Rotate this card" - the source card itself must rotate to pay for the
     * effect.
     *
     * <p>The Group cards use this. "Secret Police": "Rotate this Group
     * during any Intrigue conflict." The group is the card that rotates, not
     * a sponsor it is played with, so the two rotation requirements are
     * different rules and get different names.</p>
     */
    ROTATE_CARD,

    /** "Purge N marks" - a cost paid by purging marks. */
    PURGE_MARKS,

    /** "Discard N card(s)" - a cost paid by discarding. */
    DISCARD_CARD,

    /** "Take N damage" - a cost paid by suffering damage. */
    TAKE_DAMAGE,

    /** "Control N marks" - a requirement on the board, not a payment. */
    CONTROL_MARKS,

    /** "Control N characters" - a requirement on the board, not a payment. */
    CONTROL_CHARACTERS;

    /** Parses a requirement type, failing closed. */
    public static EffectRequirementType parse(String raw, String location)
            throws EffectLoadException {
        if (raw == null || raw.trim().length() == 0) {
            throw new EffectLoadException(location, "missing requirement type");
        }
        String v = raw.trim();
        for (EffectRequirementType t : values()) {
            if (t.name().equals(v)) {
                return t;
            }
        }
        throw new EffectLoadException(location,
                "unknown requirement type '" + v + "'; known types are " + names());
    }

    /** The known requirement types, comma-separated, in declaration order. */
    public static String names() {
        StringBuilder sb = new StringBuilder();
        for (EffectRequirementType t : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.name());
        }
        return sb.toString();
    }
}
