package b5ccg.model.enums;
import b5ccg.model.enums.Race;

/**
 * B5-0010 (WP-19/A7.0, owner D1 single-human): player race.
 *
 * Split out of the old {@code Loyalty} enum, which carried three different
 * concepts: a <em>player race</em> (HUMAN, MINBARI, CENTAURI, NARN,
 * NON_ALIGNED, VORLON), a <em>card loyalty</em> (a race or NEUTRAL), and a
 * <em>query wildcard</em> (ANY). {@code Race} carries only the first meaning:
 * the race seated at a player slot, addressing tension, states, war,
 * unrest, starter decks and council seats. It has no NEUTRAL and no ANY,
 * so neither can be seated as a player race or keyed into the tension
 * matrix by construction.
 *
 * <p>Card data keeps the other meanings in {@link Loyalty}. The 531 card
 * records (of 829) carrying the {@code ANY} loyalty stay exactly as
 * loaded; only the type carrying them changed.
 *
 * <p>Declaration order mirrors the old {@code Loyalty} race positions
 * (HUMAN, MINBARI, CENTAURI, NARN, NON_ALIGNED, VORLON), so relative
 * ordering (pair canonicalisation, roster sorts) is unchanged. Absolute
 * ordinals differ for NON_ALIGNED/VORLON (the old enum interleaved
 * NEUTRAL at 4), so anything seeded by an ordinal must use
 * {@link #legacyOrdinal}, never {@link #ordinal}.
 *
 * <p>Java 6 only, stdlib only.
 */
public enum Race {
    HUMAN, MINBARI, CENTAURI, NARN, NON_ALIGNED, VORLON;

    /**
     * The old {@code Loyalty} ordinal for this race (HUMAN 0, MINBARI 1,
     * CENTAURI 2, NARN 3, NON_ALIGNED 5, VORLON 6; NEUTRAL held 4, ANY 7).
     * The per-race starter-deck Random seed
     * ({@code StarterDeckBuilder}) was computed from that ordinal, so the
     * seed uses this, not {@link #ordinal}, and deck builds stay
     * byte-identical.
     */
    public int legacyOrdinal() {
        if (this == NON_ALIGNED) return 5;
        if (this == VORLON) return 6;
        return ordinal();
    }
}
