package b5ccg.model;

import b5ccg.model.enums.*;
import java.util.*;

public class LocationCard extends Card {
    private final int influencePerRound;
    private final int military;
    private Player capturedBy; // B5-0376 Phase A stub
    private boolean effectsSuppressed; // B5-0376 Phase B: true while captured

    /** B5-1995: per-location mark counts. Keyed by MarkType.
     *  Rulebook VI: marks occasionally attach to locations. */
    private final Map<MarkType, Integer> marks = new HashMap<MarkType, Integer>();

    /** B5-0376 Phase A: record captured-by owner. Stub — no gameplay effect yet. */
    public Player getCapturedBy() { return capturedBy; }
    public void setCapturedBy(Player owner) { this.capturedBy = owner; }

    /** B5-0376 Phase B: returns true when location effects (income/military)
     *  are currently suppressed because the location is under enemy capture. */
    public boolean isEffectsSuppressed() { return effectsSuppressed; }
    public void setEffectsSuppressed(boolean suppressed) { this.effectsSuppressed = suppressed; }

    public LocationCard(String id, String title, String subtype,
                        Rarity rarity, Faction faction, CardSet cardSet,
                        String imageKey, String text, int influencePerRound) {
        this(id, title, subtype, rarity, faction, cardSet, imageKey, text,
                influencePerRound, 0);
    }

    public LocationCard(String id, String title, String subtype,
                        Rarity rarity, Faction faction, CardSet cardSet,
                        String imageKey, String text, int influencePerRound,
                        int military) {
        super(id, title, CardType.LOCATION, subtype, rarity, faction,
              cardSet, imageKey, text);
        this.influencePerRound = influencePerRound;
        this.military = military;
    }

    public int getInfluencePerRound() { return (isRotated() || effectsSuppressed) ? 0 : influencePerRound; }

    public int getMilitary() { reconcileDamage(); return effectsSuppressed ? 0 : Math.max(0, military - getDamageTokens()); }

    @Override public int getGreatestAbility() { return military; }

    @Override
    public int getPrimaryStatValue(ConflictType type) {
        if (effectsSuppressed) return 0;
        reconcileDamage();
        if (isFaceDown() || type != ConflictType.MILITARY) return 0;
        return getMilitary();
    }

    // ── B5-1995: Mark operations ──────────────────────────────────────────────

    /** Returns the count of a specific mark type on this location. */
    public int getMarkCount(MarkType type) {
        Integer count = marks.get(type);
        return count == null ? 0 : count.intValue();
    }

    /** Sets the count of a specific mark type on this location. */
    public void setMarkCount(MarkType type, int count) {
        if (count <= 0) {
            marks.remove(type);
        } else {
            marks.put(type, Integer.valueOf(count));
        }
    }

    /** Adds marks of a specific type to this location. Returns the new count. */
    public int addMarks(MarkType type, int count) {
        if (count <= 0) return getMarkCount(type);
        int current = getMarkCount(type);
        int next = current + count;
        marks.put(type, Integer.valueOf(next));
        return next;
    }

    /** Removes marks of a specific type from this location. Returns the new count (never negative). */
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

    /** Returns true if this location has at least one mark of the given type. */
    public boolean hasMark(MarkType type) {
        return getMarkCount(type) > 0;
    }

    /** Returns the total number of marks on this location across all types. */
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
