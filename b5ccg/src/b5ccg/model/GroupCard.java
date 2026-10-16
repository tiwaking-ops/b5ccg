package b5ccg.model;

import b5ccg.model.enums.*;

/**
 * B5-1999 — rulebook §IV "Group Cards" (:496).
 *
 * The four rules this class now states, each one measured red before it was
 * added (see the B5-1999 report for the probe output):
 *
 *   1. "Groups are limited unless otherwise specified" — the Limited default
 *      is TRUE and only an explicit "Multiple" on the card lifts it, exactly
 *      as the Glossary defines both terms (:1166 "Characters, groups,
 *      locations and fleets are assumed to be limited unless otherwise
 *      specified"; :1168 "More than one copy of a 'Multiple' card can be in
 *      play at the same time"). No shipped group carries the word, so all 51
 *      records (premiere 26, deluxe 25) are Limited.
 *   2. "They do not normally have abilities" — a group contributes nothing to
 *      a conflict total and is NOT a participant card. The rulebook's
 *      participant vocabulary is fleet and character only (:434, :607), so a
 *      group can never be committed to a conflict.
 *   3. "Groups are Supporting Cards and may not be promoted to the Inner
 *      Circle" — the promotion cost for a group is therefore not a number but
 *      a refusal. See RulesEngine.canPromote(Player, Card).
 *   4. "If a race name is part of a group's Card Type, then the card is
 *      restricted to that race. Only the player controlling the race listed as
 *      part of the Card Type may bring a restricted card into play" — an
 *      ABSOLUTE playability restriction, not the double-cost rule that
 *      §IV:484 applies to characters. A race-restricted group is unplayable
 *      by another race; it is never playable at double cost.
 */
public class GroupCard extends Card {

    /** B5-1999: the Glossary "Multiple" opt-out of the :496 Limited default.
     *  Absent from every shipped record, so the default false (= Limited)
     *  stands; no data field populates it yet (recorded in DECISIONS). */
    private boolean multiple = false;

    public GroupCard(String id, String title, String subtype,
                     Rarity rarity, Faction faction, CardSet cardSet,
                     String imageKey, String text) {
        super(id, title, CardType.GROUP, subtype, rarity, faction,
              cardSet, imageKey, text);
    }

    /**
     * B5-1999: a group has no ability, so it never contributes to a conflict
     * total of any type (:496 "They do not normally have abilities").
     * Kept explicit rather than inherited from Card so the reason travels
     * with the zero.
     */
    @Override
    public int getPrimaryStatValue(ConflictType type) { return 0; }

    /**
     * B5-1999: the :496 Limited default. True unless the card states
     * "Multiple". A second copy of a Limited card determined to be in play is
     * discarded (:1166).
     */
    public boolean isLimited()  { return !multiple; }

    /** B5-1999: the Glossary "Multiple" opt-out (:1168). */
    public boolean isMultiple() { return multiple; }
    public void    setMultiple(boolean v) { multiple = v; }

    /**
     * B5-1999: a group is never a conflict participant. The rulebook builds
     * participant status from fleets and characters (:434 "Any fleet that
     * supports, opposes or attacks ... becomes a 'participant fleet'. Any
     * character who supports, opposes, attacks, or leads a fleet that
     * participates ... becomes a 'participant character'") and :607 defines
     * "Participant Character" the same way. A group's own printed effects
     * ("Rotate this Group during any Military conflict. Add 2 to your
     * Military total") are a rotate-for-effect, not a commitment of the card.
     */
    public boolean canParticipateInConflict() { return false; }

    /**
     * B5-1999: groups are Supporting Cards and "may not be promoted to the
     * Inner Circle" (:496). The same sentence governs fleets (:500) and
     * locations (:504).
     */
    public boolean canBePromoted() { return false; }

    /**
     * B5-1999: "If a race name is part of a group's Card Type, then the card
     * is restricted to that race" (:496). This is an ABSOLUTE restriction —
     * only the controlling player of that race may play it — and NOT the
     * §IV:484 double-cost treatment that characters loyal to another race get.
     */
    public boolean isRaceRestricted() {
        Faction f = getFaction();
        return f != null
            && f != Faction.ANY
            && f != Faction.NEUTRAL;
    }
}
