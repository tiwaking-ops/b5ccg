package b5ccg.model;

import b5ccg.model.enums.*;
import java.util.*;

public class AgendaCard extends Card {
    private final boolean isMajorAgenda;
    private final String  winConditionKey;

    /** B5-1995: per-agenda mark counts. Keyed by MarkType. */
    private final Map<MarkType, Integer> marks = new HashMap<MarkType, Integer>();

    public AgendaCard(String id, String title, String subtype,
                      Rarity rarity, Faction faction, CardSet cardSet,
                      String imageKey, String text,
                      boolean isMajorAgenda, String winConditionKey) {
        super(id, title, CardType.AGENDA, subtype, rarity, faction,
              cardSet, imageKey, text);
        this.isMajorAgenda   = isMajorAgenda;
        this.winConditionKey = winConditionKey != null ? winConditionKey : "INFLUENCE_20";
    }

    public boolean isMajorAgenda()      { return isMajorAgenda; }
    public String  getWinConditionKey() { return winConditionKey; }

    public boolean isConditionMet(GameState state, Player owner) {
        if ("INFLUENCE_20".equals(winConditionKey)) {
            return owner.getInfluence() >= 20;
        }
        if ("MILITARY_SUPREMACY".equals(winConditionKey)) {
            int ownerMil = 0;
            for (FleetCard f : owner.getFleets()) { f.setOwner(owner); ownerMil += f.getEffectiveMilitary(); }
            boolean supreme = true;
            for (Player p : state.getPlayers()) {
                if (p == owner) continue;
                int mil = 0;
                for (FleetCard f : p.getFleets()) { f.setOwner(p); mil += f.getEffectiveMilitary(); }
                if (mil >= ownerMil) { supreme = false; break; }
            }
            return supreme;
        }
        if ("MOST_INNER_CIRCLE".equals(winConditionKey)) {
            int ownerSize = owner.getInnerCircle().size();
            if (ownerSize == 0) return false;
            boolean most = true;
            for (Player p : state.getPlayers()) {
                if (p == owner) continue;
                if (p.getInnerCircle().size() >= ownerSize) { most = false; break; }
            }
            return most;
        }
        return owner.getInfluence() >= 20;
    }

    @Override
    public int getPrimaryStatValue(ConflictType type) { return 0; }

    // ── B5-1995: Mark operations ──────────────────────────────────────────────

    /** Returns the count of a specific mark type on this agenda. */
    public int getMarkCount(MarkType type) {
        Integer count = marks.get(type);
        return count == null ? 0 : count.intValue();
    }

    /** Sets the count of a specific mark type on this agenda. */
    public void setMarkCount(MarkType type, int count) {
        if (count <= 0) {
            marks.remove(type);
        } else {
            marks.put(type, Integer.valueOf(count));
        }
    }

    /** Adds marks of a specific type to this agenda. Returns the new count. */
    public int addMarks(MarkType type, int count) {
        if (count <= 0) return getMarkCount(type);
        int current = getMarkCount(type);
        int next = current + count;
        marks.put(type, Integer.valueOf(next));
        return next;
    }

    /** Removes marks of a specific type from this agenda. Returns the new count (never negative). */
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

    /** Returns true if this agenda has at least one mark of the given type. */
    public boolean hasMark(MarkType type) {
        return getMarkCount(type) > 0;
    }

    /** Returns the total number of marks on this agenda across all types. */
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
