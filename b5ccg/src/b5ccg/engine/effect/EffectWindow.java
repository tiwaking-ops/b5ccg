package b5ccg.engine.effect;

/**
 * WP-10 / B5-0028 (A3): the window in which an effect may be played or fire.
 *
 * <p>A3's effect-entry shape anchors this field at rulebook chapter III:
 * "Ready, Sustained, Conflict, Action, Resolution, Aftermath, Draw". The seven
 * values below are that list, spelled in the rulebook's own round and step
 * names ({@code RB:III/ready-round}, {@code RB:III/sustained-actions},
 * {@code RB:III/conflict-round}, {@code RB:III/action-round},
 * {@code RB:III/resolution-round}, {@code RB:IV/aftermath-cards},
 * {@code RB:III/draw-round}).</p>
 *
 * <p>A3's principle 6 is "The rulebook's own precedence rules are the
 * evaluation order, not an invention of the engine." That is why this enum
 * names rulebook windows rather than engine phases: the window vocabulary is
 * the rulebook's, and the engine's own {@code GamePhase} stays a separate
 * concept.</p>
 */
public enum EffectWindow {

    /**
     * At any time.
     *
     * <p>Two printed idioms need this value. The first is the card's own
     * window: "Play any time." appears on a large part of the event pool
     * ("Affirm Alliance", "Balance", "Contact with Shadows", "Chaos
     * Reigns"), and that is a statement about when the card may be played.
     * The second is an effect that is simply in force - "That character
     * gains +2 to all stats" has no window at all, because it applies
     * whenever the card is in play. The two readings do not conflict: both
     * mean "not restricted to one step of the turn".</p>
     */
    ANY,

    READY,
    SUSTAINED,
    CONFLICT,
    ACTION,
    RESOLUTION,
    AFTERMATH,
    DRAW;

    /** Parses a window, failing closed. */
    public static EffectWindow parse(String raw, String location) throws EffectLoadException {
        if (raw == null || raw.trim().length() == 0) {
            throw new EffectLoadException(location, "missing effect window");
        }
        String v = raw.trim();
        for (EffectWindow w : values()) {
            if (w.name().equals(v)) {
                return w;
            }
        }
        throw new EffectLoadException(location,
                "unknown effect window '" + v + "'; known windows are " + names());
    }

    /** The known windows, comma-separated, in declaration order. */
    public static String names() {
        StringBuilder sb = new StringBuilder();
        for (EffectWindow w : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(w.name());
        }
        return sb.toString();
    }
}
