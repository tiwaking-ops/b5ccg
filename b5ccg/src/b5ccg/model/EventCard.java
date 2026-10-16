package b5ccg.model;

import b5ccg.model.enums.*;

public class EventCard extends Card {

    public EventCard(String id, String title, String subtype,
                     Rarity rarity, Faction faction, CardSet cardSet,
                     String imageKey, String text) {
        super(id, title, CardType.EVENT, subtype, rarity, faction,
              cardSet, imageKey, text);
    }

    @Override
    public int getPrimaryStatValue(ConflictType type) { return 0; }
}
