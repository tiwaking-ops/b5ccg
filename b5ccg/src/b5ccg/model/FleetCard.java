package b5ccg.model;

import b5ccg.model.enums.*;
import java.util.*;

public class FleetCard extends Card {
    private final int military;

    // B5-0336 (proposal §3.6): fleet class for participation "fleetSubtypes"
    // filters (PICKET, COLONIAL, UTILITY, ...). Null/absent = class unknown;
    // such a fleet cannot be proven eligible for a fleetSubtypes-filtered
    // conflict and is excluded from it (proposal §3.6), but is otherwise
    // unaffected. Population of the data field is a separate data task.
    private String fleetClass;

    // B5-0337 (audit D5): the character currently leading this fleet —
    // rulebook (Action Details, Support or Oppose): "one character per fleet
    // may rotate to add his Leadership Ability to the Military Ability of any
    // fleet". Null = unled. Set through RulesEngine.executeLeadFleet (which
    // rotates the leader); cleared by RulesEngine.startRound — the relation
    // is a per-round rotation, not a permanent attachment.
    private CharacterCard leader;

    /** B5-1995: per-fleet mark counts. Keyed by MarkType.
     *  Rulebook VI: marks occasionally attach to fleets. */
    private final Map<MarkType, Integer> marks = new HashMap<MarkType, Integer>();

    public FleetCard(String id, String title, String subtype,
                     Rarity rarity, Faction faction, CardSet cardSet,
                     String imageKey, String text, int military) {
        super(id, title, CardType.FLEET, subtype, rarity, faction,
              cardSet, imageKey, text);
        this.military = military;
    }

    public int getMilitary() { return isRotated() ? 0 : military; }

    /** B5-0366: the owning player, so this card can read its registry bonuses. */
    private Player owner;
    public void setOwner(Player p) { owner = p; }

    /**
     * B5-0337: the fleet's Military with fleet leadership applied. A rotated,
     * face-up leader adds his Leadership (his rotation IS the rulebook
     * requirement for leading); a damaged leader adds 0. The fleet itself
     * stays unrotated while fighting — a rotated fleet contributes 0.
     * B5-0366: PLUS any ATTACHED/FACTION bonuses from the owner registry.
     * Clamped at 0 (the D10 floor-at-1 dies — see proposal §3.2).
     */
    public int getEffectiveMilitary() {
        reconcileDamage();
        if (isFaceDown()) return 0;
        // Rotation pays for participation/actions; it does not erase ability
        // from a card already committed to a conflict.
        int base = military;
        if (leader != null && leader.isRotated() && !leader.isFaceDown()) {
            if (owner != null) leader.setOwner(owner);
            base += leader.getEffectiveStat(StatKey.LEADERSHIP);
        }
        if (owner != null) {
            base = owner.effectiveStat(getId(), StatKey.MILITARY, base, true);
        }
        // B5-0486 minimum-1 floor pass: highest ATTACHED floor for THIS fleet,
        // capped at the printed base. B5-0494: this loop lives INSIDE the
        // owner guard (restored) — the 0486 draft iterated owner.getBonuses()
        // unguarded and NPE'd for owner-less fleets (B5-0489 Finding F1:
        // HeadlessLeadFleetScenarioProbe, deterministic exit 1).
        int floored = base;
        if (owner != null) {
            for (StatBonus b : owner.getBonuses()) {
                if (b.stat == StatKey.MILITARY && b.scope == BonusScope.ATTACHED
                        && b.targetCardId != null && b.targetCardId.equals(getId())
                        && b.floor > 0) {
                    int cap = military; // printed base before leader contribution
                    floored = Math.max(floored, Math.min(cap, b.floor));
                }
            }
        }
        return Math.max(0, floored - getDamageTokens());
    }

    @Override
    public int getGreatestAbility() {
        int base = military;
        if (leader != null && leader.isRotated() && !leader.isFaceDown()) {
            if (owner != null) leader.setOwner(owner);
            base += leader.getEffectiveStat(StatKey.LEADERSHIP);
        }
        if (owner != null) base = owner.effectiveStat(getId(), StatKey.MILITARY, base, true);
        return Math.max(0, base);
    }

    @Override
    protected void onNeutralized() {
        if (leader != null) leader.neutralizeFromFleet();
    }

    /** B5-0336: fleet class accessors (null = unknown, see field comment). */
    public String getFleetClass()            { return fleetClass; }
    public void   setFleetClass(String fc)   { fleetClass = (fc == null || fc.trim().length() == 0) ? null : fc.trim(); }

    @Override
    public int getPrimaryStatValue(ConflictType type) {
        if (type != ConflictType.MILITARY) return 0;
        return getEffectiveMilitary();
    }

    /** B5-0337: fleet-leader relation (null = unled; see field comment). */
    public CharacterCard getLeader()           { return leader; }
    public void          setLeader(CharacterCard ch) { leader = ch; }

    // ── B5-1995: Mark operations ──────────────────────────────────────────────

    /** Returns the count of a specific mark type on this fleet. */
    public int getMarkCount(MarkType type) {
        Integer count = marks.get(type);
        return count == null ? 0 : count.intValue();
    }

    /** Sets the count of a specific mark type on this fleet. */
    public void setMarkCount(MarkType type, int count) {
        if (count <= 0) {
            marks.remove(type);
        } else {
            marks.put(type, Integer.valueOf(count));
        }
    }

    /** Adds marks of a specific type to this fleet. Returns the new count. */
    public int addMarks(MarkType type, int count) {
        if (count <= 0) return getMarkCount(type);
        int current = getMarkCount(type);
        int next = current + count;
        marks.put(type, Integer.valueOf(next));
        return next;
    }

    /** Removes marks of a specific type from this fleet. Returns the new count (never negative). */
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

    /** Returns true if this fleet has at least one mark of the given type. */
    public boolean hasMark(MarkType type) {
        return getMarkCount(type) > 0;
    }

    /** Returns the total number of marks on this fleet across all types. */
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
