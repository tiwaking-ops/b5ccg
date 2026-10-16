package b5ccg.model;

import b5ccg.model.enums.*;

/** A face-down card placed under a controlled host until its trigger is met. */
public class ContingencyCard extends Card {
    private final String validTargetType;
    private final String validTargetRace;
    private final String triggerCondition;
    private Card placedUnder;
    private Player playedBy;
    private boolean revealed;

    public ContingencyCard(String id, String title, String subtype, Rarity rarity,
                           Faction faction, CardSet cardSet, String imageKey,
                           String text, String validTargetType,
                           String validTargetRace, String triggerCondition) {
        super(id, title, CardType.CONTINGENCY, subtype, rarity, faction,
              cardSet, imageKey, text);
        this.validTargetType = validTargetType;
        this.validTargetRace = validTargetRace;
        this.triggerCondition = triggerCondition == null ? "" : triggerCondition;
        setFaceDown(true);
    }

    public String getValidTargetType() { return validTargetType; }
    public String getValidTargetRace() { return validTargetRace; }
    public String getTriggerCondition() { return triggerCondition; }
    public Card getPlacedUnder() { return placedUnder; }
    public Player getPlayedBy() { return playedBy; }
    public boolean isRevealed() { return revealed; }

    public boolean canTarget(Card host) {
        if (host == null) return false;
        if (validTargetType != null && validTargetType.length() > 0
                && !"ANY".equalsIgnoreCase(validTargetType)
                && !host.getType().name().equalsIgnoreCase(validTargetType)) return false;
        if (validTargetRace != null && validTargetRace.length() > 0
                && !"ANY".equalsIgnoreCase(validTargetRace)
                && !hostRace(host).equalsIgnoreCase(validTargetRace)) return false;
        return true;
    }

    /** Race is a card subtype property, distinct from its controlling faction. */
    private String hostRace(Card host) {
        String subtype = host.getSubtype();
        if (subtype == null) return "";
        String upper = subtype.toUpperCase();
        if (upper.startsWith("CHARACTER_")) return upper.substring(10);
        int split = upper.lastIndexOf('_');
        return split >= 0 ? upper.substring(split + 1) : upper;
    }

    public boolean placeUnder(Player owner, Card host) {
        if (owner == null || placedUnder != null || revealed || !canTarget(host)) return false;
        if (!host.addContingency(this)) return false;
        playedBy = owner;
        placedUnder = host;
        setFaceDown(true);
        return true;
    }

    /** Reveal is an explicit trigger surface; callers decide when triggerCondition is met. */
    public boolean reveal() {
        if (placedUnder == null || revealed) return false;
        revealed = true;
        setFaceDown(false);
        return true;
    }

    public void detach() {
        if (placedUnder != null) placedUnder.removeContingency(this);
        placedUnder = null;
    }

    @Override
    public int getPrimaryStatValue(ConflictType type) { return 0; }
}
