package b5ccg.model.enums;
import b5ccg.model.enums.Race;

import java.util.*;

/**
 * B5-0358 proposal §2.1: pairwise tension (0–5) plus an explicit at-war set.
 * Engine-owned game state, not card data. Tension is directional: source→target
 * is distinct from target→source. War is entered explicitly via enterWar.
 *
 * ⚠️ IMPORTANT: Race is a bare enum (b5ccg.model.enums.Race) with values
 * HUMAN, MINBARI, CENTAURI, NARN, NEUTRAL, NON_ALIGNED, VORLON, ANY. It has NO
 * getName() method — its toString() returns the enum constant name (e.g. "NARN").
 * All faction identity comparisons MUST use == (reference equality), never
 * getName().compareTo(...). This file was written to use toString() for ordering
 * in RacePair, but the Race type in GameState/Conflict/RulesEngine is the
 * b5ccg.model.Race wrapper (Player.getRace()), which DOES have getName().
 * These are DIFFERENT types. The GameState.isAtWar/findLocationOwner/raiseTension
 * methods bridge between them.
 */
public class TensionMatrix {
    /** source → (target → tension). */
    private final Map<Race, Map<Race, Integer>> tension =
            new HashMap<Race, Map<Race, Integer>>();
    /** Unordered pairs currently at war. */
    private final Set<RacePair> atWar = new HashSet<RacePair>();

    /** Clamp to 0..5; no-op when source==target or either is null. */
    public void raiseTension(Race source, Race target, int delta) {
        if (source == null || target == null || source == target) return;
        Map<Race, Integer> row = tension.get(source);
        if (row == null) {
            row = new HashMap<Race, Integer>();
            tension.put(source, row);
        }
        int cur = row.containsKey(target) ? row.get(target) : 0;
        int nxt = cur + delta;
        if (nxt < 0) nxt = 0;
        if (nxt > 5) nxt = 5;
        row.put(target, nxt);
    }

    /** Current tension from source toward target (0 if never raised). */
    public int getTension(Race source, Race target) {
        if (source == null || target == null || source == target) return 0;
        Map<Race, Integer> row = tension.get(source);
        if (row == null) return 0;
        Integer v = row.get(target);
        return v != null ? v : 0;
    }

    /** Enter war between a and b (both directions recorded). */
    public void enterWar(Race a, Race b) {
        if (a == null || b == null || a == b) return;
        atWar.add(new RacePair(a, b));
    }

    /** Exit war between a and b. */
    public void exitWar(Race a, Race b) {
        if (a == null || b == null || a == b) return;
        atWar.remove(new RacePair(a, b));
    }

    /** True when a and b are currently at war. */
    public boolean isAtWar(Race a, Race b) {
        if (a == null || b == null || a == b) return false;
        return atWar.contains(new RacePair(a, b));
    }

    /** True when faction is at war with at least one other faction. */
    public boolean isAtWar(Race faction) {
        if (faction == null) return false;
        for (RacePair fp : atWar) {
            if (fp.a == faction || fp.b == faction) return true;
        }
        return false;
    }

    /** Immutable snapshot of the at-war set. */
    public Set<Race> getAtWarRaces() {
        Set<Race> r = new HashSet<Race>();
        for (RacePair fp : atWar) {
            r.add(fp.a);
            r.add(fp.b);
        }
        return Collections.unmodifiableSet(r);
    }

    /** Directed tension map: source → (target → value). */
    public Map<Race, Map<Race, Integer>> getTensionMap() {
        return Collections.unmodifiableMap(tension);
    }

    /** Mutable snapshot of the at-war set (for tests). */
    public Set<RacePair> getAtWarPairs() {
        return new HashSet<RacePair>(atWar);
    }

    /** Canonical unordered pair of two factions (by enum identity). */
    public static class RacePair {
        public final Race a;
        public final Race b;

        public RacePair(Race a, Race b) {
            if (a == null || b == null) throw new IllegalArgumentException();
            // Order by enum ordinal so (a,b) == (b,a).
            int oa = a.ordinal();
            int ob = b.ordinal();
            if (oa < ob) {
                this.a = a;
                this.b = b;
            } else {
                this.a = b;
                this.b = a;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RacePair)) return false;
            RacePair that = (RacePair) o;
            return this.a == that.a && this.b == that.b;
        }

        @Override
        public int hashCode() {
            return a.hashCode() * 31 + b.hashCode();
        }
    }
}
