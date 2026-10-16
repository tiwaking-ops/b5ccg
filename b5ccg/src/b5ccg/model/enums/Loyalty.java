package b5ccg.model.enums;
import b5ccg.model.enums.Race;

/**
 * B5-0010 (WP-19/A7.0, owner D1 single-human): card loyalty.
 *
 * Split out of the old {@code Loyalty} enum alongside {@link Race}.
 * {@code Loyalty} carries the card-data meaning: the race a card is loyal
 * to, {@code NEUTRAL} for printed Neutral cards, or {@code ANY} for cards
 * with no marked loyalty (531 of the 829 loaded records). It is the type
 * of every card's loyalty ({@code Card} upward) and of the hand/editor
 * loyalty filters, which range over exactly these values.
 *
 * <p>Declaration order is byte-identical to the old {@code Loyalty}
 * order, so every ordinal-indexed structure (hand loyalty filters)
 * keeps working unchanged.
 *
 * <p>Zero-behaviour-change bridges to {@link Race}:
 * {@link #fromRace} (null-safe), {@link #toRace} (NEUTRAL/ANY map to
 * null), and {@link #isPlayableBy}, whose truth table matches the old
 * {@code Loyalty.isPlayableBy} for every loyalty, including a null
 * player race.
 *
 * <p>Java 6 only, stdlib only.
 */
public enum Loyalty {
    HUMAN, MINBARI, CENTAURI, NARN, NEUTRAL, NON_ALIGNED, VORLON, ANY;

    /**
     * The loyalty of a card loyal to the given race, or null when the
     * race is null. Loyalty constants share the race names, so this is
     * an exact mapping, never a guess: NEUTRAL and ANY are never equal
     * to any race's loyalty.
     */
    public static Loyalty fromRace(Race race) {
        if (race == null) return null;
        return Loyalty.valueOf(race.name());
    }

    /**
     * This loyalty as a race, or null for NEUTRAL and ANY, which address
     * no race. Null-safe on the caller side: compare with
     * {@code == Loyalty.fromRace(race)} rather than dereferencing this.
     */
    public Race toRace() {
        if (this == NEUTRAL || this == ANY) return null;
        return Race.valueOf(name());
    }

    /**
     * Returns true if a card of this loyalty can be played by a player
     * of the given race. Rulebook (Character Cards): "Non-Aligned IS a
     * race name" — NON_ALIGNED cards are loyal to the League race and
     * are NOT universally playable; NEUTRAL characters are playable by
     * everyone. (The double-influence-cost rule for sponsoring another
     * race's loyal characters needs a card cost field the model does
     * not carry yet — audit finding D13 remainder.)
     *
     * <p>Truth table identical to the old
     * {@code Loyalty.isPlayableBy}: ANY and NEUTRAL are playable by any
     * race (including a null one); a race loyalty is playable only by
     * that same race.
     */
    public boolean isPlayableBy(Race playerRace) {
        if (this == ANY || this == NEUTRAL) return true;
        return playerRace != null && this == fromRace(playerRace);
    }
}
