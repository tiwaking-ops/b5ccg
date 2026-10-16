package b5ccg.model.enums;

public enum Faction {
    HUMAN, MINBARI, CENTAURI, NARN, NEUTRAL, NON_ALIGNED, VORLON, ANY;

    /**
     * Returns true if a card of this faction can be played by playerFaction.
     * Rulebook (Character Cards): "Non-Aligned IS a race name" — NON_ALIGNED
     * cards are loyal to the League race and are NOT universally playable;
     * NEUTRAL characters are playable by everyone. (The double-influence-cost
     * rule for sponsoring another race's loyal characters needs a card cost
     * field the model does not carry yet — audit finding D13 remainder.)
     */
    public boolean isPlayableBy(Faction playerFaction) {
        return this == ANY || this == playerFaction || this == NEUTRAL;
    }
}
