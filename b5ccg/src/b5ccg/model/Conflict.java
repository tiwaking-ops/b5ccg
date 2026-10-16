package b5ccg.model;

import b5ccg.model.enums.ConflictType;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.WarKind;
import java.util.*;

/**
 * A conflict in progress. Tracks committed cards per participant and — since
 * B5-0309 — the SIDE each participant is on. Rulebook ("Conflicts"): the
 * initiator wins only if the conflict receives MORE support than opposition;
 * equal or more opposition means the initiator loses. The initiator starts
 * on the support side of his own conflict.
 *
 * B5-0376: war conflicts carry card==null, a warKind (RACE_TARGET or
 * LOCATION_TARGET), and optionally a targetLocation. Exactly one of target
 * (Player) and targetLocation (LocationCard) is non-null for a war conflict.
 * Regular conflicts continue to carry a non-null ConflictCard.
 */
public class Conflict {
    private final ConflictCard      card;
    private final Player            initiator;
    private       Player            target;   // B5-0336: declared target (null = none)
    private       LocationCard      targetLocation; // B5-0376: location target (null for race target / regular)
    private final Map<Player, List<Card>> committed = new LinkedHashMap<Player, List<Card>>();
    private final Set<Player>       supporters = new LinkedHashSet<Player>();
    private final Set<Player>       opposers   = new LinkedHashSet<Player>();
    private       Player            winner;
    private       WarKind           warKind;  // B5-0376: null for regular conflicts
    private       boolean           resolved = false;
    private       boolean           attackOccurred = false; // B5-0376 Phase C

    public Conflict(ConflictCard card, Player initiator) {
        this(card, initiator, null, null, null);
    }

    /** B5-0336: conflicts whose participation carries requiresTarget:true are
     *  initiated with an explicit target (Border Raid: "Target another faction"). */
    public Conflict(ConflictCard card, Player initiator, Player target) {
        this(card, initiator, target, null, null);
    }

    /** B5-0376: war conflict targeting a race. card==null, targetLocation==null, warKind!=null. */
    public Conflict(WarKind warKind, Player initiator, Player target) {
        this(null, initiator, target, null, warKind);
    }

    /** B5-0376: war conflict targeting a location. card==null, target==null, targetLocation!=null, warKind!=null. */
    public Conflict(WarKind warKind, Player initiator, LocationCard targetLocation) {
        this(null, initiator, null, targetLocation, warKind);
    }

    /** Internal constructor for all conflict types. */
    private Conflict(ConflictCard card, Player initiator, Player target,
                     LocationCard targetLocation, WarKind warKind) {
        this.card          = card;
        this.initiator    = initiator;
        this.target       = target;
        this.targetLocation = targetLocation;
        this.warKind      = warKind;
        committed.put(initiator, new ArrayList<Card>());
        supporters.add(initiator);   // the initiator supports his own conflict
    }

    public ConflictCard   getCard()              { return card; }
    public Player         getInitiator()         { return initiator; }
    public Player         getTarget()            { return target; }
    public LocationCard   getTargetLocation()    { return targetLocation; }
    public WarKind        getWarKind()           { return warKind; }
    public boolean        isWarConflict()        { return warKind != null; }

    /** Regular conflicts carry a card type; war conflicts are always MILITARY. */
    public ConflictType   getConflictType()      {
        if (card != null) return card.getConflictType();
        return ConflictType.MILITARY;
    }

    /** Regular conflicts carry the card's reward; war conflicts carry 0 (the
     *  resolution bonuses are applied separately by resolveWarOutcome). */
    public int            getInfluenceReward()  {
        if (card != null) return card.getInfluenceReward();
        return 0;
    }

    public boolean        isResolved()           { return resolved; }
    public Player         getWinner()            { return winner; }

    /** Convenience: commit on the initiator's (support) side. */
    public boolean commitCard(Player player, Card c) {
        return commitCard(player, c, true);
    }

    /** Commit a card on the given side. B5-0376: war conflicts have no
     *  ConflictCard participation rules — the card-kind/quota filters apply
     *  only when a real ConflictCard is present. */
    public boolean commitCard(Player player, Card c, boolean support) {
        if (!canCommitCard(player, c)) return false;
        if (!committed.containsKey(player)) committed.put(player, new ArrayList<Card>());
        committed.get(player).add(c);
        setSide(player, support);
        return true;
    }

    /** B5-0336: engine-mandated commit. B5-0376: refused for war conflicts
     *  (no ConflictCard to mandate from). */
    public boolean commitMandatory(Player player, Card c, boolean support) {
        if (card == null) return false; // B5-0376: no card to mandate from
        Participation part = card.getParticipation();
        if (part != null && !part.isPlayerAllowed(initiator, target, player)) return false;
        if (!committed.containsKey(player)) committed.put(player, new ArrayList<Card>());
        committed.get(player).add(c);
        setSide(player, support);
        return true;
    }

    /** Convenience: add a participant on the initiator's (support) side. */
    public boolean addParticipant(Player player) {
        return addParticipant(player, true);
    }

    /** Adds a participant on the given side. B5-0376: war conflicts accept
     *  any player whose race is at war with the initiator's race (the
     *  existing canJoinConflict gate still applies for regular conflicts). */
    public boolean addParticipant(Player player, boolean support) {
        if (!canJoinConflict(player)) return false;
        if (!committed.containsKey(player)) committed.put(player, new ArrayList<Card>());
        setSide(player, support);
        return true;
    }

    // ── B5-0336 + B5-0376: participation enforcement ──────────────────────────

    /** True when p is allowed to be a participant at all. B5-0376: war
     *  conflicts accept any player whose race is at war with the initiator's
     *  race; regular conflicts consult the ConflictCard participation rules.
     *
     *  B5-1705: a player who has forfeited or surrendered has ceased play
     *  (rulebook :454 "loses the game, and ceases play"; :817 "Pick up your
     *  cards and go home") and may not join a conflict. The status gate sits
     *  here, at the lowest shared predicate, so it covers every join path
     *  at once: addParticipant, canCommitCard, RulesEngine's eligible-participant
     *  scan (line ~1313) and the mandatory-participation enforcement loop.
     *  The primary test is GameState.isPlayerActive, the same consolidated
     *  definition used by the B5-1825 declaration gate, so the family shares
     *  one meaning of "still playing". The Player-flag fallback covers the case
     *  where the GameState back-reference is unwired: Player.gameState is only
     *  ever set by an explicit setGameState call and is never populated by the
     *  GameState constructor, so a hand-built Conflict can carry players with
     *  a null back-reference. */
    public boolean canJoinConflict(Player p) {
        if (p == null) return false;
        GameState st = p.getGameState();
        boolean stillPlaying = (st != null) ? st.isPlayerActive(p)
                                            : (!p.hasForfeited() && !p.hasSurrendered());
        if (!stillPlaying) return false;
        if (card == null) {
            // B5-0376: war conflict — accept any player at war with initiator
            if (p == initiator) return true;
            Faction initiatorRace = initiator.getFaction();
            Faction playerRace = p.getFaction();
            return initiatorRace != null && playerRace != null
                && initiatorRace != playerRace
                && p.getGameState().getTensionMatrix().isAtWar(initiatorRace, playerRace);
        }
        Participation part = card.getParticipation();
        if (part == null) return true;
        return part.isPlayerAllowed(initiator, target, p);
    }

    /** True when player may commit card c here. B5-0376: war conflicts have
     *  no card-kind/quota filters (no ConflictCard); any committed card is
     *  accepted. */
    public boolean canCommitCard(Player player, Card c) {
        if (c == null || !c.canActAfterNeutralization() || c.isFaceDown()) return false;
        if (card == null) return true; // B5-0376: no ConflictCard → no filters
        if (!canJoinConflict(player)) return false;
        Participation part = card.getParticipation();
        if (part == null) return true;
        if (c instanceof CharacterCard && part.isLeadersIncluded()
                && !part.getCardTypes().contains(c.getType())) {
            List<Card> mine = committed.containsKey(player) ? committed.get(player)
                                                            : Collections.<Card>emptyList();
            boolean alongside = false;
            for (Card cc : mine) {
                if (cc instanceof FleetCard && part.allowsCardType(cc)) { alongside = true; break; }
            }
            if (!alongside) return false;
        } else if (!part.allowsCardType(c)) return false;
        Integer quota = part.quotaFor(c);
        if (quota != null) {
            int n = 0;
            List<Card> mine = committed.containsKey(player) ? committed.get(player)
                                                            : Collections.<Card>emptyList();
            for (Card cc : mine) {
                if (cc.getType() == c.getType()) n++;
            }
            if (n >= quota.intValue()) return false;
        }
        return true;
    }

    private void setSide(Player player, boolean support) {
        if (support) {
            supporters.add(player);
            opposers.remove(player);
        } else {
            opposers.add(player);
            supporters.remove(player);
        }
    }

    public Set<Player>    getParticipants()          { return committed.keySet(); }
    public Set<Player>    getSupporters()            { return Collections.unmodifiableSet(supporters); }
    public Set<Player>    getOpposers()              { return Collections.unmodifiableSet(opposers); }

    /** True if the participant is on the initiator's (support) side. */
    public boolean isSupporting(Player p) { return supporters.contains(p); }

    /** True if the participant is on the opposition side. */
    public boolean isOpposing(Player p)   { return opposers.contains(p); }

    public List<Card>     getCommittedCards(Player p) {
        if (committed.containsKey(p)) return committed.get(p);
        return Collections.emptyList();
    }

    /** True when the card has supported, opposed, or attacked in this conflict. */
    public boolean isParticipantCard(Card card) {
        for (List<Card> cards : committed.values()) {
            if (cards.contains(card)) return true;
            for (Card committedCard : cards)
                if (committedCard instanceof FleetCard
                        && ((FleetCard) committedCard).getLeader() == card) return true;
        }
        return false;
    }

    /** Fleet leaders are participants through their relation, but may not be attacked. */
    public boolean isLeaderOfParticipantFleet(Card card) {
        if (!(card instanceof CharacterCard)) return false;
        for (List<Card> cards : committed.values()) {
            for (Card committedCard : cards) {
                if (committedCard instanceof FleetCard
                        && ((FleetCard) committedCard).getLeader() == card) return true;
            }
        }
        return false;
    }

    public Map<Player, List<Card>> getAllCommitted()  {
        return Collections.unmodifiableMap(committed);
    }

    /** Total stat value of all cards committed in support of the initiator. */
    public int supportTotal() {
        int total = 0;
        for (Player p : supporters) total += sideTotal(p);
        return total;
    }

    /** Total stat value of all cards committed in opposition. */
    public int oppositionTotal() {
        int total = 0;
        for (Player p : opposers) total += sideTotal(p);
        return total;
    }

    /** Returns the stat total for one player's committed cards. */
    public int playerTotal(Player p) {
        return sideTotal(p);
    }

    private int sideTotal(Player p) {
        int total = 0;
        List<Card> cards = committed.get(p);
        if (cards != null) {
            for (Card c : cards) {
                total += c.getPrimaryStatValue(getConflictType());
            }
        }
        return total;
    }

    public void resolve(Player winner) {
        this.winner   = winner;
        this.resolved = true;
    }

    /** B5-0376: true when at least one attack action has been resolved against
     *  a committed participant this conflict (set via markAttackOccurred by the
     *  engine's executeAttackConflictParticipant). Makes a war outcome
     *  "contested" so the uncontested race-war swing does not apply. */
    public boolean anyAttackOccurred() {
        return attackOccurred;
    }

    /** B5-0376 Phase C: record that an attack action resolved this conflict. */
    public void markAttackOccurred() {
        this.attackOccurred = true;
    }

    /** B5-0376: returns the faction of the player who controls this location,
     *  or null if the location is unowned. */
    public Faction getTargetLocationOwner() {
        if (targetLocation == null) return null;
        Player owner = targetLocation.getCapturedBy();
        return owner != null ? owner.getFaction() : null;
    }
}
