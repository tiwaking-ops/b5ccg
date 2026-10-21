package b5ccg.engine.effect;

/**
 * WP-10 / B5-0028 (A3): the predicates an effect checks before it applies.
 *
 * <p>A3's effect-entry shape describes the {@code conditions} field as
 * "predicates over state: tension or unrest thresholds, power or influence
 * thresholds, a state exists, mark counts, ISA exists (later)", anchored at
 * rulebook :799-:817.</p>
 *
 * <p>A condition is a question about the state of the game, asked at the
 * moment the effect would fire. It is not a filter on the target (that is
 * {@link EffectTarget}) and not a cost (that is
 * {@link EffectRequirementType}): if a condition is false, the effect simply
 * does not happen.</p>
 */
public enum EffectConditionType {

    /** "Tension is at least N" (rulebook :284 - tension and unrest run 1-5). */
    TENSION_AT_LEAST,

    /** "Unrest is at least N" (rulebook :284). */
    UNREST_AT_LEAST,

    /** "Power is at least N" - the victory threshold. */
    POWER_AT_LEAST,

    /** "Influence is at least N". */
    INFLUENCE_AT_LEAST,

    /** "You hold at least N marks of a type". */
    MARK_COUNT_AT_LEAST,

    /** "A state exists" - a card in play with a named state. */
    STATE_EXISTS,

    /** "The source card is rotated" - the Group rotation idiom. */
    SOURCE_ROTATED,

    /** "The source card is in play". */
    SOURCE_IN_PLAY,

    /**
     * "You control at least N characters" - the subject is the row when the
     * count is of a particular row.
     *
     * <p>"Servants of Order": "if you have 3 or more characters in your Inner
     * Circle, gain 1 Influence". The count is of characters in a row, so the
     * subject names the row; the row itself is a target filter, and this
     * condition counts within it.</p>
     */
    CONTROL_CHARACTERS,

    /**
     * "A named card is in play" - the subject is the card's canonical id.
     *
     * <p>"Contact with Shadows" reads "Draw 2 cards. If you have Morden in
     * play, draw 3 cards instead." That is a predicate over the board, not a
     * filter on a target, and it names a specific card, so it needs a
     * subject.</p>
     */
    CARD_IN_PLAY,

    /**
     * "You control at most N damaged characters" - the subject is the
     * character set the entry's target names, as with
     * {@link #CONTROL_CHARACTERS}.
     *
     * <p><b>Added by WP-11 (B5-0034), the Groups pilot.</b> "Religious Caste"
     * reads "At the start of each round, if you have no damaged characters,
     * gain 1 Influence." The v0 condition vocabulary could count characters
     * and marks and thresholds, but it had no way to say a count is
     * <em>at most</em> something, and a missing condition would be a missing
     * rule.</p>
     */
    DAMAGED_CHARACTERS_AT_MOST;

    /** Parses a condition type, failing closed. */
    public static EffectConditionType parse(String raw, String location)
            throws EffectLoadException {
        if (raw == null || raw.trim().length() == 0) {
            throw new EffectLoadException(location, "missing condition type");
        }
        String v = raw.trim();
        for (EffectConditionType t : values()) {
            if (t.name().equals(v)) {
                return t;
            }
        }
        throw new EffectLoadException(location,
                "unknown condition type '" + v + "'; known types are " + names());
    }

    /** The known condition types, comma-separated, in declaration order. */
    public static String names() {
        StringBuilder sb = new StringBuilder();
        for (EffectConditionType t : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.name());
        }
        return sb.toString();
    }
}
