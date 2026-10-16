package b5ccg.model;

import b5ccg.model.enums.*;

public class AftermathCard extends Card {
    private final String triggerCondition;

    public AftermathCard(String id, String title, String subtype,
                         Rarity rarity, Faction faction, CardSet cardSet,
                         String imageKey, String text, String triggerCondition) {
        super(id, title, CardType.AFTERMATH, subtype, rarity, faction,
              cardSet, imageKey, text);
        this.triggerCondition = triggerCondition != null ? triggerCondition.toUpperCase() : "ANY";
    }

    public String getTriggerCondition() { return triggerCondition; }

    /**
     * B5-0338 (audit D2): true when the card's play conditions include
     * "Participant" — the rulebook's key that widens targeting from the
     * initiating faction to "any faction that either Supported, Opposed or
     * Attacked" (Aftermath Cards / Participant). Non-Participant aftermaths
     * may target only the faction that initiated the resolved conflict.
     */
    public boolean isParticipantCondition() { return triggerCondition.contains("PARTICIPANT"); }

    public boolean isEligible(boolean playerWon, boolean playerParticipated,
                              ConflictType conflictType) {
        String t = triggerCondition;
        if (t.contains("WON")      && !playerWon)         return false;
        if (t.contains("LOST")     && playerWon)          return false;
        if (t.contains("PARTICIPANT") && !playerParticipated) return false;
        if (t.contains("MILITARY")  && conflictType != ConflictType.MILITARY)  return false;
        if (t.contains("DIPLOMACY") && conflictType != ConflictType.DIPLOMACY) return false;
        if (t.contains("INTRIGUE")  && conflictType != ConflictType.INTRIGUE)  return false;
        if (t.contains("PSI")       && conflictType != ConflictType.PSI)       return false;
        return true;
    }

    @Override
    public int getPrimaryStatValue(ConflictType type) { return 0; }
}
