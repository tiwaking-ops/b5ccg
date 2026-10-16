package b5ccg.model;

import b5ccg.model.enums.*;

public class ConflictCard extends Card {
    private final ConflictType conflictType;
    private final int          influenceReward;

    // B5-0336 (proposal §3): optional participation restriction. Null = open
    // participation = the pre-B5-0336 behavior everywhere (§3.4 backward
    // compatibility). Hydrated by DeckLoader from the optional top-level
    // "participation" key on CONFLICT cards.
    private Participation participation;

    public ConflictCard(String id, String title, String subtype,
                        Rarity rarity, Faction faction, CardSet cardSet,
                        String imageKey, String text,
                        ConflictType conflictType, int influenceReward) {
        super(id, title, CardType.CONFLICT, subtype, rarity, faction,
              cardSet, imageKey, text);
        this.conflictType    = conflictType;
        this.influenceReward = influenceReward;
    }

    public ConflictType getConflictType()    { return conflictType; }
    public int          getInfluenceReward() { return influenceReward; }

    /** B5-0336: participation restriction (null = open, see field comment). */
    public Participation getParticipation()                    { return participation; }
    public void          setParticipation(Participation part)  { participation = part; }

    @Override
    public int getPrimaryStatValue(ConflictType type) { return 0; }
}
