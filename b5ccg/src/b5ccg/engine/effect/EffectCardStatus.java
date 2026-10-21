package b5ccg.engine.effect;

/**
 * WP-10 / B5-0028 (A3): the status filter on a target card.
 *
 * <p>A3's effect-entry shape lists "status (ready, rotated, neutralized)" as a
 * target filter, anchored at rulebook :827-:832. These are the three states
 * the rulebook gives a card in play that decide whether it can act
 * ({@code RB:III/ready-and-rotated-cards}) and whether it has been knocked
 * out ({@code RB:VI/bonuses-and-blanking}).</p>
 */
public enum EffectCardStatus {

    /** Ready to act. */
    READY,

    /** Rotated: spent for the round, or rotated by an effect. */
    ROTATED,

    /** Neutralised: damaged and turned face down. */
    NEUTRALIZED,

    /** In play at all, whatever its readiness. */
    IN_PLAY;

    /** Parses a status, failing closed. */
    public static EffectCardStatus parse(String raw, String location) throws EffectLoadException {
        if (raw == null || raw.trim().length() == 0) {
            throw new EffectLoadException(location, "missing card status");
        }
        String v = raw.trim();
        for (EffectCardStatus s : values()) {
            if (s.name().equals(v)) {
                return s;
            }
        }
        throw new EffectLoadException(location,
                "unknown card status '" + v + "'; known statuses are " + names());
    }

    /** The known statuses, comma-separated, in declaration order. */
    public static String names() {
        StringBuilder sb = new StringBuilder();
        for (EffectCardStatus s : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s.name());
        }
        return sb.toString();
    }
}
