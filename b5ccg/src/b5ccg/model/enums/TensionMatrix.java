package b5ccg.model.enums;

import java.util.*;

/**
 * B5-0358 proposal §2.1: pairwise tension (0–5) plus an explicit at-war set.
 * Engine-owned game state, not card data. Tension is directional: source→target
 * is distinct from target→source. War is entered explicitly via enterWar.
 *
 * ⚠️ IMPORTANT: Faction is a bare enum (b5ccg.model.enums.Faction) with values
 * HUMAN, MINBARI, CENTAURI, NARN, NEUTRAL, NON_ALIGNED, VORLON, ANY. It has NO
 * getName() method — its toString() returns the enum constant name (e.g. "NARN").
 * All faction identity comparisons MUST use == (reference equality), never
 * getName().compareTo(...). This file was written to use toString() for ordering
 * in FactionPair, but the Faction type in GameState/Conflict/RulesEngine is the
 * b5ccg.model.Faction wrapper (Player.getFaction()), which DOES have getName().
 * These are DIFFERENT types. The GameState.isAtWar/findLocationOwner/raiseTension
 * methods bridge between them.
 */
public class TensionMatrix {
    /** source → (target → tension). */
    private final Map<Faction, Map<Faction, Integer>> tension =
            new HashMap<Faction, Map<Faction, Integer>>();
    /** Unordered pairs currently at war. */
    private final Set<FactionPair> atWar = new HashSet<FactionPair>();

    /** Clamp to 0..5; no-op when source==target or either is null. */
    public void raiseTension(Faction source, Faction target, int delta) {
        if (source == null || target == null || source == target) return;
        Map<Faction, Integer> row = tension.get(source);
        if (row == null) {
            row = new HashMap<Faction, Integer>();
            tension.put(source, row);
        }
        int cur = row.containsKey(target) ? row.get(target) : 0;
        int nxt = cur + delta;
        if (nxt < 0) nxt = 0;
        if (nxt > 5) nxt = 5;
        row.put(target, nxt);
    }

    /** Current tension from source toward target (0 if never raised). */
    public int getTension(Faction source, Faction target) {
        if (source == null || target == null || source == target) return 0;
        Map<Faction, Integer> row = tension.get(source);
        if (row == null) return 0;
        Integer v = row.get(target);
        return v != null ? v : 0;
    }

    /** Enter war between a and b (both directions recorded). */
    public void enterWar(Faction a, Faction b) {
        if (a == null || b == null || a == b) return;
        atWar.add(new FactionPair(a, b));
    }

    /** Exit war between a and b. */
    public void exitWar(Faction a, Faction b) {
        if (a == null || b == null || a == b) return;
        atWar.remove(new FactionPair(a, b));
    }

    /** True when a and b are currently at war. */
    public boolean isAtWar(Faction a, Faction b) {
        if (a == null || b == null || a == b) return false;
        return atWar.contains(new FactionPair(a, b));
    }

    /** True when faction is at war with at least one other faction. */
    public boolean isAtWar(Faction faction) {
        if (faction == null) return false;
        for (FactionPair fp : atWar) {
            if (fp.a == faction || fp.b == faction) return true;
        }
        return false;
    }

    /** Immutable snapshot of the at-war set. */
    public Set<Faction> getAtWarFactions() {
        Set<Faction> r = new HashSet<Faction>();
        for (FactionPair fp : atWar) {
            r.add(fp.a);
            r.add(fp.b);
        }
        return Collections.unmodifiableSet(r);
    }

    /** Directed tension map: source → (target → value). */
    public Map<Faction, Map<Faction, Integer>> getTensionMap() {
        return Collections.unmodifiableMap(tension);
    }

    /** Mutable snapshot of the at-war set (for tests). */
    public Set<FactionPair> getAtWarPairs() {
        return new HashSet<FactionPair>(atWar);
    }

    /** Canonical unordered pair of two factions (by enum identity). */
    public static class FactionPair {
        public final Faction a;
        public final Faction b;

        public FactionPair(Faction a, Faction b) {
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
            if (!(o instanceof FactionPair)) return false;
            FactionPair that = (FactionPair) o;
            return this.a == that.a && this.b == that.b;
        }

        @Override
        public int hashCode() {
            return a.hashCode() * 31 + b.hashCode();
        }
    }
}
