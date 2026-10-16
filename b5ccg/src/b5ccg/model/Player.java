package b5ccg.model;

import b5ccg.model.enums.*;
import java.util.*;

public class Player {
    private final String  name;
    private final Faction faction;
    private final boolean isHuman;

    private int influence = 4;
    private int appliedPool = 4;

    private CharacterCard        ambassador;

    // B5-0357/B5-0366: stat bonus registry — one per player, the single
    // source of truth for non-permanent (and currently permanent) stat
    // bonuses. Replaces the permanent mutation in CharacterCard.applyStatDelta
    // and FleetCard.applyMilitaryDelta. Bonuses are keyed by sourceCardId for
    // remove-by-source (blanking/discard) and swept at the round boundary.
    private final List<StatBonus> bonuses = new ArrayList<StatBonus>();
    private final List<CharacterCard>  innerCircle     = new ArrayList<CharacterCard>();
    private final Set<CharacterCard> healedInnerCircleThisTurn = new HashSet<CharacterCard>();
    private final List<CharacterCard>  supportingRole  = new ArrayList<CharacterCard>();
    private final List<FleetCard>      fleets          = new ArrayList<FleetCard>();
    private final List<LocationCard>   locations       = new ArrayList<LocationCard>();
    private final List<GroupCard>      groups          = new ArrayList<GroupCard>();
    private final List<EnhancementCard> enhancements   = new ArrayList<EnhancementCard>();
    private       AgendaCard           agenda;

    private       GameState            gameState; // back-reference (B5-0376 Phase A)

    private final List<Card> hand = new ArrayList<Card>();
    private       Deck       deck;

    private boolean passed       = false;
    private int     actionsLeft  = 1;
    private boolean hasForfeited = false;

    /** B5-0661 (rulebook :815–:819): set when this player has unconditionally
     *  surrendered; a surrendered player is out of the game and may not act
     *  or win. Distinct from forfeiture. */
    private boolean hasSurrendered = false;

    /** B5-0661: characters placed in asylum on this player's side.
     *  Asylum characters may not be elevated to the Inner Circle. */
    private final List<CharacterCard> asylumCards = new ArrayList<CharacterCard>();

    // B5-0339 (rulebook §IV): 1 while the ambassador "may apply 1 influence
    // less than usual when sponsoring a card" later this turn — granted by
    // executing the assistant's sponsor-discount ability, consumed by the
    // first recruit and cleared with the turn at startRound.
    private int sponsorDiscount = 0;

    /** B5-0691 (rulebook :968/:278/:280; B5-0669 proposal §5.1): per-faction
     *  unrest — government/public hostility. Starts at 1 (:278; :888 gives
     *  Non-Aligned 2), clamped 1..5 (:280). Its own axis, never the tension
     *  matrix. No pool card references it today (B5-0669 census). Set in the
     *  constructor (faction must be assigned first). */
    private int unrest = 1;

    public Player(String name, Faction faction, boolean isHuman) {
        this.name     = name;
        this.faction  = faction;
        this.isHuman  = isHuman;
        this.unrest   = faction == Faction.NON_ALIGNED ? 2 : 1;   // B5-0691 (:278, :888)
    }

    // ── Influence ────────────────────────────────────────────────────────────
    public int  getInfluence()          { return influence; }

    /** B5-0677 (rulebook :171/:1158; B5-0667 proposal §3.2): Power is
     *  COMPUTED, never stored — influence plus the sum of POWER-tagged
     *  StatBonus entries. With no POWER bonus in play this equals
     *  getInfluence() exactly, byte-identical to the pre-seam behaviour.
     *  Every economy path (gains, spends, rewards) stays on getInfluence();
     *  only power-referencing rules read this. */
    public int  getPower() {
        return influence + getPowerBonusTotal();
    }

    /** Sum of this player's POWER-tagged bonus deltas (may be negative).
     *  Rides the existing StatBonus channel, so expiry sweeps
     *  (sweepBonusExpiries) and source removal (removeBonusesBySource)
     *  clear Power add-ons through the same path as every other bonus. */
    public int  getPowerBonusTotal() {
        int total = 0;
        for (StatBonus b : bonuses) {
            if (b.stat == StatKey.POWER) total += b.delta;
        }
        return total;
    }
    public int  getAppliedPool()        { return appliedPool; }
    public void gainInfluence(int n)    { if (n > 0) { influence += n; appliedPool += n; } }
    public void loseInfluence(int n) {
        if (n <= 0) return;
        int lost = Math.min(influence, n);
        influence -= lost;
        appliedPool = Math.max(0, appliedPool - lost);
    }
    public boolean applyInfluence(int n) {
        if (n < 0 || appliedPool < n) return false;
        appliedPool -= n;
        return true;
    }
    /** Compatibility name; spending always consumes the per-turn pool. */
    public boolean spendInfluence(int n) { return applyInfluence(n); }
    public void restoreAppliedPool() { appliedPool = influence; }

    // ── Identity ─────────────────────────────────────────────────────────────
    public String  getName()    { return name; }
    public Faction getFaction() { return faction; }
    public boolean isHuman()    { return isHuman; }

    // ── Ambassador ───────────────────────────────────────────────────────────
    public CharacterCard getAmbassador()              { return ambassador; }
    public void          setAmbassador(CharacterCard a) { ambassador = a; if (a != null) a.setOwner(this); }

    // ── Zones ────────────────────────────────────────────────────────────────
    public List<CharacterCard>   getInnerCircle()    { return innerCircle; }
    public List<CharacterCard>   getSupportingRole() { return supportingRole; }
    public List<FleetCard>       getFleets()         { return fleets; }
    public List<LocationCard>    getLocations()      { return locations; }
    public List<GroupCard>       getGroups()         { return groups; }
    public List<EnhancementCard> getEnhancements()   { return enhancements; }

    /** B5-0371: tracks IC heal actions until the end of the action round. */
    public void markInnerCircleHealed(CharacterCard ch) { healedInnerCircleThisTurn.add(ch); }
    public boolean allInnerCircleHealed() {
        if (innerCircle.isEmpty()) return false;
        return healedInnerCircleThisTurn.containsAll(innerCircle);
    }
    public void resetInnerCircleHealing() { healedInnerCircleThisTurn.clear(); }

    /** Links a card to this player's bonus registry when it enters a zone. */
    public void addCharacter(CharacterCard ch) { if (ch != null) { ch.setOwner(this); innerCircle.add(ch); } }
    public void addFleet(FleetCard fl) { if (fl != null) { fl.setOwner(this); fleets.add(fl); } }

    public boolean controlsCard(Card card) {
        return card != null && (card == ambassador || innerCircle.contains(card)
                || supportingRole.contains(card) || fleets.contains(card)
                || locations.contains(card) || groups.contains(card)
                || enhancements.contains(card) || agenda == card);
    }

    /** Clears the once-neutralized action lock at the next action-round start. */
    public void resetNeutralizedTurnLocks() {
        if (ambassador != null) ambassador.resetNeutralizedTurnLock();
        for (CharacterCard ch : innerCircle) ch.resetNeutralizedTurnLock();
        for (CharacterCard ch : supportingRole) ch.resetNeutralizedTurnLock();
        for (FleetCard fl : fleets) fl.resetNeutralizedTurnLock();
        for (LocationCard loc : locations) loc.resetNeutralizedTurnLock();
        for (GroupCard gr : groups) gr.resetNeutralizedTurnLock();
    }

    public AgendaCard getAgenda()           { return agenda; }
    public void       setAgenda(AgendaCard a) { agenda = a; }

    /** B5-0376 Phase A: back-reference for tension-matrix lookups in Conflict. */
    public GameState  getGameState()        { return gameState; }
    public void       setGameState(GameState s) { gameState = s; }

    // ── Hand & Deck ──────────────────────────────────────────────────────────
    public List<Card> getHand() { return hand; }
    public Deck       getDeck() { return deck; }
    public void       setDeck(Deck d) { deck = d; }

    public void addToHand(Card c)      { hand.add(c); }
    public void removeFromHand(Card c) { hand.remove(c); }

    /**
     * Draws n cards. When the draw pile is empty, the rulebook (Draw Round,
     * Step 3) forbids reshuffling: the player must instead discard one Inner
     * Circle character, and loses the game if he cannot (the ambassador may
     * never be discarded). Sets hasForfeited() instead of throwing.
     */
    public void drawCards(int n) {
        for (int i = 0; i < n; i++) {
            Card c = (deck == null) ? null : deck.draw();
            if (c == null) {
                // Deck-out: discard one non-ambassador Inner Circle character.
                CharacterCard discardMe = null;
                for (CharacterCard ch : innerCircle) {
                    if (ch != ambassador) { discardMe = ch; break; }
                }
                if (discardMe != null) {
                    innerCircle.remove(discardMe);
                    if (deck != null) deck.discard(discardMe);
                } else {
                    hasForfeited = true; // no Inner Circle character left to discard
                    return;
                }
            }
            if (c != null) hand.add(c);
        }
    }

    /** True once this player could not draw and had no Inner Circle
     *  character left to discard (rulebook Draw Round Step 3: he loses). */
    public boolean hasForfeited() { return hasForfeited; }
    public void setHasForfeited(boolean v) { hasForfeited = v; }

    /** B5-0661: true once this player has unconditionally surrendered
     *  (rulebook :815–:819). A surrendered player is out of the game. */
    public boolean hasSurrendered() { return hasSurrendered; }
    public void setHasSurrendered(boolean v) { hasSurrendered = v; }

    /** B5-0661: characters in asylum on this player's side (may not be
     *  elevated to the Inner Circle). */
    public List<CharacterCard> getAsylumCards() { return asylumCards; }
    public void addAsylumCard(CharacterCard ch) { if (ch != null) asylumCards.add(ch); }

    // ── Inner Circle management ──────────────────────────────────────────────
    public void recruitToInnerCircle(CharacterCard ch) {
        supportingRole.remove(ch);
        if (!innerCircle.contains(ch)) innerCircle.add(ch);
    }

    public void placeInSupportingRole(CharacterCard ch) {
        innerCircle.remove(ch);
        if (!supportingRole.contains(ch)) supportingRole.add(ch);
    }

    // ── Total stat contribution in a conflict ─────────────────────────────────
    public int conflictTotal(ConflictType type) {
        int total = 0;
        if (type != ConflictType.MILITARY) {
            if (ambassador != null) ambassador.setOwner(this);
            if (ambassador != null && !ambassador.isFaceDown() && !ambassador.isRotated())
                total += ambassador.getPrimaryStatValue(type);
            for (CharacterCard ch : innerCircle) {
                ch.setOwner(this);
                if (ch == ambassador) continue; // already counted above (B5-0321 seat)
                if (!ch.isFaceDown() && !ch.isRotated())
                    total += ch.getPrimaryStatValue(type);
            }
        }
        if (type == ConflictType.MILITARY) {
            // B5-0337 (audit D5): Military totals are fleet-based only.
            // Rulebook (Action Details, Support or Oppose): "Characters do
            // not have the Military ability… one character per fleet may
            // rotate to add his Leadership Ability to the Military Ability
            // of any fleet" — character Leadership enters ONLY through the
            // fleet-leader relation (carried by the fleet's effective
            // value); every unled character's Leadership is excluded here.
            // Previously every IC character's Leadership fed the total
            // directly, double-counting it against the fleet values.
            for (FleetCard fl : fleets) {
                fl.setOwner(this);
                if (!fl.isRotated())
                    total += fl.getEffectiveMilitary();
            }
        }
        return total;
    }

     // ── Location income ──────────────────────────────────────────────────────
    public int collectLocationIncome() {
        int income = 0;
        for (LocationCard loc : locations) income += loc.getInfluencePerRound();
        gainInfluence(income);
        return income;
    }

    // ── Stat bonus registry (B5-0366) ──────────────────────────────────────────

    /** All bonuses belonging to this player (faction-scope + attached ones
     *  sourced by the player's cards). Unmodifiable view. */
    public List<StatBonus> getBonuses() {
        return Collections.unmodifiableList(bonuses);
    }

    /** Adds a bonus to this player's registry. */
    public void addBonus(StatBonus b) {
        bonuses.add(b);
    }

    /** Adds the bonus and returns it (for immediate-effect callers). */
    public StatBonus grantBonus(StatBonus b) {
        if (b == null) return null;
        if (!b.cumulative) {
            Iterator<StatBonus> old = bonuses.iterator();
            while (old.hasNext()) {
                StatBonus prior = old.next();
                if (prior.sourceCardId.equals(b.sourceCardId) && prior.stat == b.stat
                        && prior.scope == b.scope
                        && eq(prior.targetCardId, b.targetCardId)
                        && eq(prior.ownerName, b.ownerName)) old.remove();
            }
        }
        bonuses.add(b);
        return b;
    }

    private static boolean eq(Object a, Object b) { return a == null ? b == null : a.equals(b); }

    /** Removes turn-limited bonuses whose expiry boundary has been reached. */
    public int sweepBonusExpiries(int roundNumber) {
        int removed = 0;
        Iterator<StatBonus> it = bonuses.iterator();
        while (it.hasNext()) {
            StatBonus b = it.next();
            if ((b.expiry == Expiry.END_OF_TURN || b.expiry == Expiry.START_OF_NEXT_OWNER_TURN)
                    && b.createdRound < roundNumber) { it.remove(); removed++; }
        }
        return removed;
    }

    /**
     * Removes every bonus sourced by the named card id from this player's
     * registry (discard / blanking / leaving play). Returns how many were
     * removed.
     */
    public int removeBonusesBySource(String cardId) {
        if (cardId == null) return 0;
        int removed = 0;
        Iterator<StatBonus> it = bonuses.iterator();
        while (it.hasNext()) {
            if (it.next().sourceCardId.equals(cardId)) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    // ── B5-0468: attached-bonus probe for the opponent-targeted enhancement seam ──

    /**
     * B5-0468 probe: returns true if this player's bonus registry contains an
     * ATTACHED-scope bonus from the given source card targeting the given card.
     * Used by conformance tests to verify the seam closes correctly (grant → check).
     * Returns false if either argument is null (defensive).
     */
    public boolean hasAttachedBonusFrom(String sourceCardId, String targetCardId) {
        if (sourceCardId == null || targetCardId == null) return false;
        for (StatBonus b : bonuses) {
            if (b.scope == BonusScope.ATTACHED
                    && b.sourceCardId != null && b.sourceCardId.equals(sourceCardId)
                    && b.targetCardId != null && b.targetCardId.equals(targetCardId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Effective value of a card's stat, accounting for this player's attached
     * bonuses. The formula (B5-0357 proposal §3.2):
     *   effective = printedBase + sum(ATTACHED bonuses matching card+stat)
     * The computed overlays (B5-0337 leader, B5-0339 assistant) are NOT
     * applied here — they live inside the card's own getPrimaryStatValue.
     *
     * For ATTACHED-scope bonuses the targetCardId must match the given card's
     * id. For FACTION-scope bonuses the ownerName must match this player's name
     * AND the stat must match — but FACTION bonuses apply uniformly and are
     * read by the card's getPrimaryStatValue (which calls here). This method
     * only sums ATTACHED bonuses; faction-scope bonuses are handled by the
     * card reading its own player's registry.
     *
     * @param cardId the card being evaluated
     * @param stat   the stat key
     * @param printedBase the raw printed stat value
     * @return printedBase + applicable ATTACHED bonuses (pre-clamp)
     */
    public int effectiveStat(String cardId, StatKey stat, int printedBase) {
        return effectiveStat(cardId, stat, printedBase, false);
    }

    public int effectiveStat(String cardId, StatKey stat, int printedBase, boolean includeFaction) {
        int bonus = 0;
        int floor = 0;
        boolean psiUnlocked = false;
        if (stat == StatKey.PSI && printedBase == 0) {
            for (StatBonus b : bonuses) {
                if (b.stat == StatKey.PSI && b.psiFromZero && appliesTo(b, cardId, includeFaction)) {
                    psiUnlocked = true;
                    break;
                }
            }
        }
        for (StatBonus b : bonuses) {
            if (stat == b.stat && appliesTo(b, cardId, includeFaction)) {
                if (stat != StatKey.PSI || printedBase != 0 || psiUnlocked) bonus += b.delta;
                if (b.floor > floor) floor = b.floor;
            }
        }
        int raw = printedBase + bonus;
        if (floor > 0) {
            raw = Math.max(raw, Math.min(printedBase, floor));
        }
        return raw;
    }

    private boolean appliesTo(StatBonus b, String cardId, boolean includeFaction) {
        if (b.scope == BonusScope.ATTACHED)
            return b.targetCardId != null && b.targetCardId.equals(cardId);
        return includeFaction && b.scope == BonusScope.FACTION && name != null && name.equals(b.ownerName);
    }

    /**
     * Effective value of a card's stat including FACTION-scope bonuses for this
     * player. The full read path:
     *   effective = printedBase + sum(ATTACHED to card) + sum(FACTION owner=me)
     * Final clamp to 0 happens at the consumer; this returns the raw sum.
     */

    // ── Turn state ────────────────────────────────────────────────────────────
    public boolean isPassed()      { return passed; }
    public void    setPassed(boolean v) { passed = v; }
    public int     getActionsLeft()    { return actionsLeft; }
    public void    resetActions()      { actionsLeft = 1; passed = false; }
    public void    useAction()         { actionsLeft = Math.max(0, actionsLeft - 1); }
    public void    grantExtraAction()  { actionsLeft++; }

    // ── Assistant sponsor discount (B5-0339; rulebook §IV) ────────────
    public int getSponsorDiscount()               { return sponsorDiscount; }
    public void grantSponsorDiscount(int n)       { sponsorDiscount = Math.max(sponsorDiscount, n); }
    /** Consumes up to n points of discount; returns what was applied. */
    public int consumeSponsorDiscount(int n) {
        int used = Math.min(n, sponsorDiscount);
        sponsorDiscount -= used;
        return used;
    }

    // ── Unrest (B5-0691; rulebook :968/:278/:280) ─────────────────────────
    public int  getUnrest()   { return unrest; }
    /** Raise unrest, clamped to the rulebook 1..5 band (:280). */
    public void raiseUnrest(int n)   { unrest = Math.min(5, unrest + Math.max(0, n)); }
    /** Lower unrest, clamped to the rulebook 1..5 band (:280). */
    public void lowerUnrest(int n)   { unrest = Math.max(1, unrest - Math.max(0, n)); }

    // ── B5-1995: Per-faction Mark tracking ────────────────────────────────────
    /**
     * Returns the total count of a mark type across all characters, fleets, and
     * locations this faction controls. Rulebook VI: "If a card refers to a
     * player's marks, or a faction's marks, then it includes all marks attached
     * to all characters in that faction."
     */
    public int getTotalMarks(MarkType type) {
        int total = 0;
        for (CharacterCard ch : innerCircle) {
            if (ch != null) total += ch.getMarkCount(type);
        }
        for (CharacterCard ch : supportingRole) {
            if (ch != null) total += ch.getMarkCount(type);
        }
        for (CharacterCard ch : asylumCards) {
            if (ch != null) total += ch.getMarkCount(type);
        }
        for (FleetCard fl : fleets) {
            if (fl != null) total += fl.getMarkCount(type);
        }
        for (LocationCard loc : locations) {
            if (loc != null) total += loc.getMarkCount(type);
        }
        // Enhancement cards can also have marks (e.g., Vorlon Enhancement)
        for (EnhancementCard enh : enhancements) {
            if (enh != null) total += enh.getMarkCount(type);
        }
        if (agenda != null) total += agenda.getMarkCount(type);
        return total;
    }

    /** Returns true if this faction has at least one mark of the given type. */
    public boolean hasMark(MarkType type) {
        return getTotalMarks(type) > 0;
    }

    /** Returns true if this faction has any Shadow marks. */
    public boolean hasShadowMarks() {
        return hasMark(MarkType.SHADOW);
    }

    /** Returns true if this faction has any Vorlon marks. */
    public boolean hasVorlonMarks() {
        return hasMark(MarkType.VORLON);
    }

    /**
     * Rulebook VI: "A faction cannot have both Shadow and Vorlon marks.
     * If one of these marks is already attached to a character in one faction,
     * any effect attaching an opposing mark to any character in the same faction
     * is ignored."
     * Returns true if the faction can gain marks of the given type.
     */
    public boolean canGainMark(MarkType type) {
        if (type == MarkType.SHADOW) {
            return !hasVorlonMarks();
        }
        if (type == MarkType.VORLON) {
            return !hasShadowMarks();
        }
        return true; // Other mark types have no cross-type restriction
    }

    /**
     * Adds marks of a specific type to a target character/fleet/location.
     * Enforces the Shadow/Vorlon mutual exclusion rule.
     * Returns the number of marks actually added (0 if blocked by exclusion).
     */
    public int addMarksToCharacter(CharacterCard target, MarkType type, int count) {
        if (target == null || count <= 0) return 0;
        if (!canGainMark(type)) return 0;
        return target.addMarks(type, count);
    }

    public int addMarksToFleet(FleetCard target, MarkType type, int count) {
        if (target == null || count <= 0) return 0;
        if (!canGainMark(type)) return 0;
        return target.addMarks(type, count);
    }

    public int addMarksToLocation(LocationCard target, MarkType type, int count) {
        if (target == null || count <= 0) return 0;
        if (!canGainMark(type)) return 0;
        return target.addMarks(type, count);
    }

    /**
     * Purges marks of a specific type from all cards in this faction.
     * Rulebook VI: "If a character is cut off from a source of a mark
     * (for example, if a faction switches agendas or if an aftermath or
     * enhancement is discarded or blanked) then that character must purge
     * a mark of that type."
     * Returns the total number of marks purged.
     */
    public int purgeMarks(MarkType type) {
        int totalPurged = 0;
        for (CharacterCard ch : innerCircle) {
            if (ch != null) totalPurged += ch.removeMarks(type, 1);
        }
        for (CharacterCard ch : supportingRole) {
            if (ch != null) totalPurged += ch.removeMarks(type, 1);
        }
        for (CharacterCard ch : asylumCards) {
            if (ch != null) totalPurged += ch.removeMarks(type, 1);
        }
        for (FleetCard fl : fleets) {
            if (fl != null) totalPurged += fl.removeMarks(type, 1);
        }
        for (LocationCard loc : locations) {
            if (loc != null) totalPurged += loc.removeMarks(type, 1);
        }
        for (EnhancementCard enh : enhancements) {
            if (enh != null) totalPurged += enh.removeMarks(type, 1);
        }
        if (agenda != null) totalPurged += agenda.removeMarks(type, 1);
        return totalPurged;
    }

    /**
     * Purges marks of a specific type from a specific target card.
     * Returns the number of marks purged (0 or 1 per rulebook "purge a mark").
     */
    public int purgeMarkFromCharacter(CharacterCard target, MarkType type) {
        if (target == null) return 0;
        return target.removeMarks(type, 1);
    }

    public int purgeMarkFromFleet(FleetCard target, MarkType type) {
        if (target == null) return 0;
        return target.removeMarks(type, 1);
    }

    public int purgeMarkFromLocation(LocationCard target, MarkType type) {
        if (target == null) return 0;
        return target.removeMarks(type, 1);
    }

    @Override
    public String toString() {
        return name + " (" + faction + ") INF=" + influence;
    }
}
