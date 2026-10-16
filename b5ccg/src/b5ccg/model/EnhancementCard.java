package b5ccg.model;

import b5ccg.model.enums.*;

import java.util.*;

public class EnhancementCard extends Card {
    private final int diplomacyBonus;
    private final int intrigueBonus;
    private final int psiBonus;
    private final int militaryBonus;
    private final int leadershipBonus;

    // B5-0468: opponent-targeted enhancement seam shape
    // Default to self-target semantics (null = no explicit target, legacy behavior)
    private String targetCardId = null;
    private String targetOwnerName = null;

    /** B5-1995: per-enhancement mark counts. Keyed by MarkType. */
    private final Map<MarkType, Integer> marks = new HashMap<MarkType, Integer>();

    public EnhancementCard(String id, String title, String subtype,
                           Rarity rarity, Faction faction, CardSet cardSet,
                           String imageKey, String text,
                           int diplomacyBonus, int intrigueBonus,
                           int psiBonus, int militaryBonus, int leadershipBonus) {
        super(id, title, CardType.ENHANCEMENT, subtype, rarity, faction,
              cardSet, imageKey, text);
        this.diplomacyBonus  = diplomacyBonus;
        this.intrigueBonus   = intrigueBonus;
        this.psiBonus        = psiBonus;
        this.militaryBonus   = militaryBonus;
        this.leadershipBonus = leadershipBonus;
    }

    public int getDiplomacyBonus()  { return diplomacyBonus; }
    public int getIntrigueBonus()   { return intrigueBonus; }
    public int getPsiBonus()        { return psiBonus; }
    public int getMilitaryBonus()   { return militaryBonus; }
    public int getLeadershipBonus() { return leadershipBonus; }

    // ── B5-0468: opponent-targeted enhancement seam shape ───────────────────────

    /** Returns the card ID this enhancement targets, or null for self-target. */
    public String getTargetCardId() { return targetCardId; }

    /** Returns the target player name (for opponent-targeted bonuses), or null. */
    public String getTargetOwnerName() { return targetOwnerName; }

    /** True when an explicit opponent target has been set (not self-target). */
    public boolean hasExplicitTarget() { return targetCardId != null; }

    /** Sets an opponent target for this enhancement. */
    public void setOpponentTarget(String targetCardId, String targetOwnerName) {
        this.targetCardId = targetCardId;
        this.targetOwnerName = targetOwnerName;
    }

    /** Returns the per-stat bonus value (ignoring expiry/caster for caller to
     * determine the correct application context). */
    public int bonusFor(StatKey stat) {
        switch (stat) {
            case DIPLOMACY:    return diplomacyBonus;
            case INTRIGUE:     return intrigueBonus;
            case PSI:          return psiBonus;
            case MILITARY:     return militaryBonus;
            case LEADERSHIP:   return leadershipBonus;
            default:           return 0;
        }
    }

    /**
     * B5-0486: construct a StatBonus for this enhancement with an optional
     * minimum-floor value (0 = no floor, the default).
     * Returns null if there is no explicit target (self-target effects are
     * applied differently via the card's base bonus fields).
     */
    public StatBonus toAttachedBonus(StatKey stat, Expiry expiry, int createdRound) {
        if (!hasExplicitTarget()) return null;
        return toAttachedBonus(stat, expiry, createdRound, 0);
    }

    public StatBonus toAttachedBonus(StatKey stat, Expiry expiry, int createdRound, int floor) {
        if (!hasExplicitTarget()) return null;
        return StatBonus.attached(this.getId(), stat, bonusFor(stat),
                                  targetCardId, expiry, createdRound, floor);
    }

    @Override
    public int getPrimaryStatValue(ConflictType type) { return 0; }

    // ── B5-1995: Mark operations ──────────────────────────────────────────────

    /** Returns the count of a specific mark type on this enhancement. */
    public int getMarkCount(MarkType type) {
        Integer count = marks.get(type);
        return count == null ? 0 : count.intValue();
    }

    /** Sets the count of a specific mark type on this enhancement. */
    public void setMarkCount(MarkType type, int count) {
        if (count <= 0) {
            marks.remove(type);
        } else {
            marks.put(type, Integer.valueOf(count));
        }
    }

    /** Adds marks of a specific type to this enhancement. Returns the new count. */
    public int addMarks(MarkType type, int count) {
        if (count <= 0) return getMarkCount(type);
        int current = getMarkCount(type);
        int next = current + count;
        marks.put(type, Integer.valueOf(next));
        return next;
    }

    /** Removes marks of a specific type from this enhancement. Returns the new count (never negative). */
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

    /** Returns true if this enhancement has at least one mark of the given type. */
    public boolean hasMark(MarkType type) {
        return getMarkCount(type) > 0;
    }

    /** Returns the total number of marks on this enhancement across all types. */
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