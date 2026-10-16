package b5ccg.model;

import b5ccg.model.enums.*;
import java.util.*;

/**
 * B5-0691 (rulebook :990–:1009; B5-0669 proposal §5.1/§5.3/§5.4): the Civil
 * War state machine for ONE race. Synthetic dual-race boards (two Players of
 * the same Faction) are expressible today, so the machine operates on the
 * players of a race without the parked multi-faction identity slice — the
 * players list is injected, never derived, and the standard single-faction
 * game never enters the machine (entry requires same-race tension at 5,
 * which needs a second faction of the race to exist).
 *
 * Java 6 only: no lambdas, streams, diamonds, try-with-resources.
 */
public class CivilWarState {

    public enum Phase { UNIFIED, CIVIL_WAR }

    private final Faction race;
    /** The players of this race, in board order (injected by GameState). */
    private final List<Player> racePlayers;
    private Phase phase = Phase.UNIFIED;
    /** Round number when Civil War was entered (rulebook :992 end-of-turn). */
    private int enteredRound = -1;
    /**
     * Split-era tension toward each OTHER race, per faction of this race:
     * factionName → (target race → tension). Populated at entry from the
     * race-level matrix (:1000 "Each other race now has a tension toward each
     * of the new races equal to their old tension toward the race as a
     * whole") and merged back at non-surrender exit as the rounded-up average.
     */
    private final Map<String, Map<Faction, Integer>> splitTensions =
            new LinkedHashMap<String, Map<Faction, Integer>>();

    public CivilWarState(Faction race, List<Player> racePlayers) {
        this.race = race;
        this.racePlayers = new ArrayList<Player>(racePlayers);
    }

    public Faction       getRace()         { return race; }
    public Phase         getPhase()        { return phase; }
    public int           getEnteredRound() { return enteredRound; }
    public List<Player>  getRacePlayers()  { return Collections.unmodifiableList(racePlayers); }

    /** True when the race has two or more player factions (:956 dual race). */
    public boolean isDualRace() { return racePlayers.size() >= 2; }

    /**
     * Rulebook :992 — entry. Legal only from UNIFIED, only for a dual race,
     * and only when some faction of the race holds same-race tension 5 toward
     * another faction of the same race (evaluated against the same-race
     * matrix by the caller, which passes the fact in).
     */
    public boolean enterCivilWar(int round, boolean sameRaceTensionAtFive) {
        if (phase != Phase.UNIFIED || !isDualRace() || !sameRaceTensionAtFive) {
            return false;
        }
        phase = Phase.CIVIL_WAR;
        enteredRound = round;
        return true;
    }

    /** Direct entry used when a war-declaration effect triggers :992's second sentence. */
    public boolean enterByDeclaration(int round) {
        if (phase != Phase.UNIFIED || !isDualRace()) return false;
        phase = Phase.CIVIL_WAR;
        enteredRound = round;
        return true;
    }

    /**
     * Rulebook :1000 — snapshot each faction's race-level tension toward every
     * OTHER race at entry, so the Civil-War-era factions can diverge
     * individually and be merged at exit. Called by GameState after a
     * successful enter (the caller supplies the race-level matrix read).
     */
    public void snapshotSplitTensions(Map<Faction, Integer> raceLevelTensions) {
        splitTensions.clear();
        for (Player p : racePlayers) {
            splitTensions.put(p.getName(), new LinkedHashMap<Faction, Integer>(raceLevelTensions));
        }
    }

    /** Civil-War-era divergence: one faction's tension toward one target race. */
    public void setSplitTension(Player factionOfRace, Faction targetRace, int value) {
        Map<Faction, Integer> row = splitTensions.get(factionOfRace.getName());
        if (row != null && row.containsKey(targetRace)) {
            row.put(targetRace, Math.max(0, Math.min(5, value)));
        }
    }

    public Map<Faction, Integer> getSplitTensions(Player factionOfRace) {
        Map<Faction, Integer> row = splitTensions.get(factionOfRace.getName());
        return row == null
                ? Collections.<Faction, Integer>emptyMap()
                : Collections.unmodifiableMap(row);
    }

    /**
     * Rulebook :998 — exit by war end (non-surrender): Civil War ends, unrest
     * −1 for every faction of the race, and per :1000 the split factions'
     * individually tracked tensions toward each target race merge as the
     * ROUNDED-UP AVERAGE, which becomes the reunified race's tension.
     * Returns the merged tensions (empty when the exit precondition fails).
     */
    public Map<Faction, Integer> exitByWarEnd() {
        if (phase != Phase.CIVIL_WAR) {
            return Collections.emptyMap();
        }
        Map<Faction, Integer> merged = mergeSplitTensions();
        lowerUnrestOfRace();
        phase = Phase.UNIFIED;
        enteredRound = -1;
        return merged;
    }

    /**
     * Rulebook :1006 — exit by unconditional surrender of all but one faction
     * of the race: unrest −1 for the survivor, states per :1002/:1004 restore
     * or cancel (out of engine scope here — no state objects exist yet), and
     * NO merge happens (the surrendered factions' tensions are not averaged;
     * the survivor's own split row governs).
     */
    public Map<Faction, Integer> exitBySurrender(Player survivor) {
        if (phase != Phase.CIVIL_WAR || !racePlayers.contains(survivor)) {
            return Collections.emptyMap();
        }
        for (Player p : racePlayers) {
            if (p != survivor) p.lowerUnrest(1);
        }
        phase = Phase.UNIFIED;
        enteredRound = -1;
        Map<Faction, Integer> row = splitTensions.get(survivor.getName());
        return row == null
                ? Collections.<Faction, Integer>emptyMap()
                : new LinkedHashMap<Faction, Integer>(row);
    }

    /** Rounded-up average of the split factions' tensions per target race (:1000). */
    private Map<Faction, Integer> mergeSplitTensions() {
        Map<Faction, Integer> merged = new LinkedHashMap<Faction, Integer>();
        if (splitTensions.isEmpty()) return merged;
        Set<Faction> targets = splitTensions.values().iterator().next().keySet();
        for (Faction target : targets) {
            int sum = 0;
            for (Map<Faction, Integer> row : splitTensions.values()) {
                Integer v = row.get(target);
                sum += v == null ? 0 : v.intValue();
            }
            // Rounded-up average: (sum + n - 1) / n with integer math.
            int n = splitTensions.size();
            merged.put(target, Integer.valueOf((sum + n - 1) / n));
        }
        return merged;
    }

    /** Rulebook :994 — every faction of the race is at war with each other faction. */
    public boolean isFactionAtWarWithRival(Player factionOfRace) {
        if (phase != Phase.CIVIL_WAR) return false;
        return racePlayers.contains(factionOfRace);
    }

    /** Rulebook :994 — entry unrest bump: every faction of the race +1 (clamped 5). */
    public void raiseUnrestOfRace() {
        for (Player p : racePlayers) {
            p.raiseUnrest(1);
        }
    }

    /** Rulebook :998 — exit unrest drop: every faction of the race −1 (clamped 1). */
    public void lowerUnrestOfRace() {
        for (Player p : racePlayers) {
            p.lowerUnrest(1);
        }
    }
}
