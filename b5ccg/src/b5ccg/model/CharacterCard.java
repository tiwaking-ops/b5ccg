package b5ccg.model;

import b5ccg.model.enums.*;
import java.util.*;

public class CharacterCard extends Card {
    private final int diplomacy;
    private final int intrigue;
    private final int psi;
    private final int leadership;
    private final boolean isAmbassador;

    // B5-0339 (rulebook §IV "Your Ambassador's Assistant"): set on a
    // SUPPORTING-ROLE character while he is rotated to assist his faction's
    // ambassador. Not a stat mutation — getPrimaryStatValue reads it as
    // +1 Diplomacy/Intrigue/Leadership while the flag is up and the card is
    // face-up; Psi is untouched (rulebook: Psi cannot be raised from a base
    // of 0 by generic ability bonuses). Cleared by RulesEngine.startRound —
    // the bonus lasts while the assistant remains rotated, and rotations
    // expire at the round boundary. ("Sustained" bonuses and cross-faction
    // assistant ownership need a card-ownership model that does not exist
    // yet; recorded in DECISIONS.)
    private boolean assistantBonus = false;

    /** B5-0661: set on an asylum copy of an ambassador (AsylumCharacterCard).
     *  Asylum cards may not be elevated to the Inner Circle (rulebook :819). */
    private boolean inAsylum = false;

    /** B5-1995: per-character mark counts. Keyed by MarkType.
     *  Rulebook VI: marks attach to characters (or occasionally fleets/locations).
     *  Marks on neutralized characters count toward faction totals. */
    private final Map<MarkType, Integer> marks = new HashMap<MarkType, Integer>();

    public CharacterCard(String id, String title, String subtype,
                         Rarity rarity, Faction faction, CardSet cardSet,
                         String imageKey, String text,
                         int diplomacy, int intrigue, int psi, int leadership,
                         boolean isAmbassador) {
        super(id, title, CardType.CHARACTER, subtype, rarity, faction,
              cardSet, imageKey, text);
        this.diplomacy    = diplomacy;
        this.intrigue     = intrigue;
        this.psi          = psi;
        this.leadership   = leadership;
        this.isAmbassador = isAmbassador;
    }

    public int  getDiplomacy()    { return diplomacy; }
    public int  getIntrigue()     { return intrigue; }
    public int  getPsi()          { return psi; }
    public int  getLeadership()   { return leadership; }
    public boolean isAmbassador() { return isAmbassador; }

    /** B5-0339: assistant assist-bonus flag (see field comment). */
    public boolean isAssistantBonus()          { return assistantBonus; }
    public void    setAssistantBonus(boolean v) { assistantBonus = v; }

    /** B5-0303: the owning player's bonus registry (set by the loader/
     *  engine so this card can read its ATTACHED + FACTION bonuses).
     *  Null until wired; getPrimaryStatValue falls back to printed base when
     *  absent so isolated card use (tests, UI previews) stays correct. */
    private Player owner;

    public void setOwner(Player p) { owner = p; }
    public Player getOwner() { return owner; }

    /**
     * B5-0366: returns the printed (unmutated) stat. Enhancement bonuses live
     * in the owner Player's bonus registry (B5-0357 proposal §3.1), not in
     * this card's fields — see getPrimaryStatValue for the effective read.
     * Kept for migration auditing; new code must not call this for combat
     * resolution.
     */
    private int printedDiplomacy()    { return diplomacy; }
    private int printedIntrigue()     { return intrigue; }
    private int printedPsi()          { return psi; }
    private int printedLeadership()   { return leadership; }

    /**
     * B5-0366: effective stat value — printed base + ATTACHED bonuses from the
     * owner's registry, with the B5-0339 assistant overlay composed on top.
     * Final clamp to 0 at the consumer (conflictTotal / getPrimaryStatValue).
     */
    @Override
    public int getPrimaryStatValue(ConflictType type) {
        reconcileDamage();
        if (isFaceDown()) return 0;
        int bonus = (assistantBonus && !isFaceDown()) ? 1 : 0;
        switch (type) {
            case DIPLOMACY:  return Math.max(0, effective(diplomacy, StatKey.DIPLOMACY) + bonus - getDamageTokens());
            case INTRIGUE:   return Math.max(0, effective(intrigue, StatKey.INTRIGUE) + bonus - getDamageTokens());
            case PSI:        return Math.max(0, effective(psi, StatKey.PSI) - getDamageTokens());
            case MILITARY:   return Math.max(0, effective(leadership, StatKey.LEADERSHIP) + bonus - getDamageTokens());
            default:         return 0;
        }
    }

    /** Sum of ATTACHED + FACTION bonuses from the owner registry for one stat. */
    private int effective(int printed, StatKey stat) {
        if (owner == null) return printed;
        return owner.effectiveStat(getId(), stat, printed, true);
    }

    /** Printed base plus active bonuses, before rotation or face-down suppression. */
    public int getEffectiveStat(StatKey stat) {
        int base;
        switch (stat) {
            case DIPLOMACY: base = diplomacy; break;
            case INTRIGUE: base = intrigue; break;
            case PSI: base = psi; break;
            case LEADERSHIP: base = leadership; break;
            default: return 0;
        }
        reconcileDamage();
        return Math.max(0, effective(base, stat) - getDamageTokens());
    }

    @Override
    public int getGreatestAbility() {
        int dip = effective(diplomacy, StatKey.DIPLOMACY) + (assistantBonus ? 1 : 0);
        int intr = effective(intrigue, StatKey.INTRIGUE) + (assistantBonus ? 1 : 0);
        int ps = effective(psi, StatKey.PSI);
        int lead = effective(leadership, StatKey.LEADERSHIP) + (assistantBonus ? 1 : 0);
        return Math.max(Math.max(dip, intr), Math.max(ps, lead));
    }

    /** B5-0661: asylum cards may not be elevated to the Inner Circle. */
    public boolean isInAsylum() { return inAsylum; }
    public void setInAsylum(boolean v) { inAsylum = v; }

    /** B5-0661: creates an asylum duplicate of this character (same printed
     *  stats, not an ambassador). The caller is responsible for setting the
     *  owner and placing the copy in the target's supporting role. */
    public CharacterCard createAsylumCopy(String newId) {
        // B5-0661 repair: the asylum copy must carry the AsylumCharacterCard type
        // identity (refused Inner Circle elevation via canPromote's instanceof
        // guard), not merely the flag on a plain CharacterCard.
        return new AsylumCharacterCard(newId, getTitle(), getSubtype(),
                getRarity(), getFaction(), getCardSet(), getImageKey(), getText(),
                getDiplomacy(), getIntrigue(), getPsi(), getLeadership(), this);
    }

    // ── B5-1995: Mark operations ──────────────────────────────────────────────

    /** Returns the count of a specific mark type on this character. */
    public int getMarkCount(MarkType type) {
        Integer count = marks.get(type);
        return count == null ? 0 : count.intValue();
    }

    /** Sets the count of a specific mark type on this character. */
    public void setMarkCount(MarkType type, int count) {
        if (count <= 0) {
            marks.remove(type);
        } else {
            marks.put(type, Integer.valueOf(count));
        }
    }

    /** Adds marks of a specific type to this character. Returns the new count. */
    public int addMarks(MarkType type, int count) {
        if (count <= 0) return getMarkCount(type);
        int current = getMarkCount(type);
        int next = current + count;
        marks.put(type, Integer.valueOf(next));
        return next;
    }

    /** Removes marks of a specific type from this character. Returns the new count (never negative). */
    public int removeMarks(MarkType type, int count) {
        if (count <= 0) return getMarkCount(type);
        int current = getMarkCount(type);
        int next = Math.max(0, current - count);
        if (next == 0) {
            marks.remove(type);
        } else {
            marks.put(type, Integer.valueOf(next));
        }
        return next;
    }

    /** Returns true if this character has at least one mark of the given type. */
    public boolean hasMark(MarkType type) {
        return getMarkCount(type) > 0;
    }

    /** Returns the total number of marks on this character across all types. */
    public int getTotalMarks() {
        int total = 0;
        for (Integer count : marks.values()) {
            total += count.intValue();
        }
        return total;
    }

    /** Returns an unmodifiable view of the marks map. */
    public Map<MarkType, Integer> getMarks() {
        return Collections.unmodifiableMap(marks);
    }
}
