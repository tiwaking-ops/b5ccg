package b5ccg.engine.effect;

/**
 * WP-10 / B5-0028 (A3): how long an effect lasts.
 *
 * <p>A3's effect-entry shape anchors this field at rulebook :854-:858: "instant,
 * until end of turn, until end of Resolution Round, while source in play
 * (expires on discard, replace or blank), permanent (the 'when' class, which
 * survives agenda loss), until a condition ends".</p>
 *
 * <p><b>The one addition to the proposal's list.</b> {@link #END_OF_ROUND} is
 * not in A3's six. It is here because shipped card text needs it:
 * "Babylon 5 Unrest" reads "Ongoing. All players' Influence costs for playing
 * cards increase by 1. <b>Discard at end of round.</b>" - an effect that ends
 * when the card is discarded, which is neither "until end of turn" nor "while
 * source in play" (the card IS in play until then, so "while source in play"
 * would describe the wrong moment). The proposal says its field table is "a
 * hypothesis derived from the rulebook - the census may revise it"; this is
 * such a revision, and it is recorded as an interpretation in
 * {@code docs/DECISIONS.md}.</p>
 */
public enum EffectDuration {

    /** Resolves once and is gone. */
    INSTANT,

    /** Until the end of the current turn. */
    END_OF_TURN,

    /** Until the end of the current round. */
    END_OF_ROUND,

    /** Until the end of the Resolution Round. */
    END_OF_RESOLUTION_ROUND,

    /**
     * While the source card is in play; expires on discard, replace or blank
     * (rulebook :844 - the cut-off purge).
     */
    WHILE_SOURCE_IN_PLAY,

    /** Survives the loss of its source; the "when" class (rulebook :858). */
    PERMANENT,

    /** Until a named condition ends. */
    UNTIL_CONDITION_ENDS;

    /** Parses a duration, failing closed. */
    public static EffectDuration parse(String raw, String location) throws EffectLoadException {
        if (raw == null || raw.trim().length() == 0) {
            throw new EffectLoadException(location, "missing effect duration");
        }
        String v = raw.trim();
        for (EffectDuration d : values()) {
            if (d.name().equals(v)) {
                return d;
            }
        }
        throw new EffectLoadException(location,
                "unknown effect duration '" + v + "'; known durations are " + names());
    }

    /** The known durations, comma-separated, in declaration order. */
    public static String names() {
        StringBuilder sb = new StringBuilder();
        for (EffectDuration d : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(d.name());
        }
        return sb.toString();
    }
}
