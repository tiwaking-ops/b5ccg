package b5ccg.engine;

import b5ccg.model.GameState;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.TensionMatrix;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * B5-1996: per-pair inter-faction relationship state, keyed on the
 * unordered pair of factions and layered over the tension matrix.
 *
 * <p>Rulebook section "States" (:801-803) is the whole specification this
 * class implements: a pair's relationship is primarily its tension, and
 * races "may enter into additional relationships with each other. Such
 * relationships are called states" -- alliances, trade pacts, and war.
 * The engine tracked none of them: {@code TensionMatrix} carried a bare
 * at-war set that only test code ever wrote (every production war path
 * read it, none entered it), and alliance and trade had no
 * representation at all.
 *
 * <h3>War is delegated, never duplicated</h3>
 * The at-war fact stays owned by {@link TensionMatrix}, so the DONE
 * B5-0376 war-conflict resolution and every existing
 * {@code GameState.isAtWar} reader keep working byte-identically. This
 * book does not shadow the war flag with a second copy that could drift
 * out of agreement with it -- {@link #isAtWar} reads through to the
 * matrix, and {@link #enterWar} writes through to it. Alliance and trade
 * have no prior owner and are stored here.
 *
 * <h3>Exclusivity of war</h3>
 * Rulebook :807: "Unless otherwise specified, when races enter a state of
 * War, all other states between the races are cancelled (with any
 * penalties applied as indicated on the appropriate cards)." Entering war
 * therefore clears that pair's alliance and trade pacts. The rulebook
 * gives the penalty as card-dependent, so no influence adjustment is
 * applied here -- the book records the cancellation and leaves the
 * penalties to the cards that state them, which is the "unless otherwise
 * specified" the sentence itself carves out.
 *
 * <h3>Nothing here is inferred from tension</h3>
 * No transition is derived from a tension threshold. The rulebook defines
 * these states as entered by card play and gives no tension value that
 * implies a state, so every entry is an explicit call. Tension remains
 * what :801 says it is: the primary relationship measure, untouched.
 *
 * <p>Java 6 only, stdlib only. No diamond, no lambdas, no enhanced for
 * over anything but an Iterable/array.
 */
public class FactionStateBook {

    /** Unordered pair of factions carrying the non-war states. */
    private static final class Pair {
        final Faction a;
        final Faction b;

        Pair(Faction x, Faction y) {
            // Order by ordinal so (x,y) and (y,x) are the same key, matching
            // TensionMatrix.FactionPair's own canonicalisation.
            if (x.ordinal() <= y.ordinal()) { this.a = x; this.b = y; }
            else                           { this.a = y; this.b = x; }
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Pair)) return false;
            Pair that = (Pair) o;
            return this.a == that.a && this.b == that.b;
        }

        @Override
        public int hashCode() {
            return a.hashCode() * 31 + b.hashCode();
        }
    }

    /** pair -> the non-war states in force. War lives in TensionMatrix. */
    private final Map<Pair, Set<FactionState>> states =
            new HashMap<Pair, Set<FactionState>>();

    /** The game whose TensionMatrix owns the war fact. */
    private final GameState state;

    public FactionStateBook(GameState state) {
        this.state = state;
    }

    /** The book attached to a game, or null when the game is null. */
    public static FactionStateBook forState(GameState state) {
        return state == null ? null : new FactionStateBook(state);
    }

    /** True when this book is the one bound to the given game. Identity, not
     *  equals: a book carries mutable relationship state, so a different
     *  GameState instance is a different game even if it compared equal. */
    public boolean forSameGame(GameState other) {
        return state != null && state == other;
    }

    // ── queries ─────────────────────────────────────────────────────────────

    /**
     * True when the two races are in the named state. War is answered from
     * the TensionMatrix rather than from local storage, so this can never
     * disagree with {@code GameState.isAtWar}.
     */
    public boolean isInState(Faction x, Faction y, FactionState s) {
        if (!valid(x, y) || s == null) return false;
        if (s == FactionState.WAR) return isAtWar(x, y);
        Set<FactionState> set = states.get(new Pair(x, y));
        return set != null && set.contains(s);
    }

    public boolean isAllied(Faction x, Faction y) {
        return isInState(x, y, FactionState.ALLIANCE);
    }

    public boolean isTrading(Faction x, Faction y) {
        return isInState(x, y, FactionState.TRADE_PACT);
    }

    /** Reads through to the TensionMatrix, which owns the at-war fact. */
    public boolean isAtWar(Faction x, Faction y) {
        if (state == null || !valid(x, y)) return false;
        return state.isAtWar(x, y);
    }

    /**
     * Every state in force between the pair, war included. An unremarkable
     * pair yields an empty set, never null -- "no state" is the absence of
     * every constant, not a fourth value.
     */
    public Set<FactionState> getStates(Faction x, Faction y) {
        Set<FactionState> out = EnumSet.noneOf(FactionState.class);
        if (!valid(x, y)) return out;
        Pair key = new Pair(x, y);
        Set<FactionState> set = states.get(key);
        if (set != null) out.addAll(set);
        if (isAtWar(x, y)) out.add(FactionState.WAR);
        return out;
    }

    /** Pairs currently in the named state, for "who is at war with whom" queries. */
    public Set<Faction> getFactionsInState(FactionState s, Iterable<Faction> roster) {
        Set<Faction> out = EnumSet.noneOf(Faction.class);
        if (s == null || roster == null) return out;
        java.util.List<Faction> members = new java.util.ArrayList<Faction>();
        for (Faction f : roster) if (f != null) members.add(f);
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                if (isInState(members.get(i), members.get(j), s)) {
                    out.add(members.get(i));
                    out.add(members.get(j));
                }
            }
        }
        return out;
    }

    // ── transitions ─────────────────────────────────────────────────────────

    /**
     * Enter a non-war state. Refuses while the pair is at war, because
     * :807 makes war exclusive -- a pair at war has had every other state
     * between the races cancelled, so re-adding one would resurrect a state
     * the rulebook says no longer exists. Returns false and changes nothing
     * in that case.
     */
    public boolean enterState(Faction x, Faction y, FactionState s) {
        if (!valid(x, y) || s == null) return false;
        if (s == FactionState.WAR) return enterWar(x, y);
        if (isAtWar(x, y)) return false;
        Pair key = new Pair(x, y);
        Set<FactionState> set = states.get(key);
        if (set == null) {
            set = EnumSet.noneOf(FactionState.class);
            states.put(key, set);
        }
        if (!set.add(s)) return false;
        log(x, y, "enters " + s + " with " + y);
        return true;
    }

    /**
     * Enter war, cancelling every other state between the pair (rulebook
     * :807). Writes through to the TensionMatrix so the at-war fact has one
     * owner. Penalties are not applied here: the rulebook makes them
     * "as indicated on the appropriate cards".
     *
     * <p>Returns false when the pair was already at war, which makes the
     * call idempotent rather than a silent re-entry.
     */
    public boolean enterWar(Faction x, Faction y) {
        if (state == null || !valid(x, y)) return false;
        if (state.isAtWar(x, y)) return false;
        clearOtherStates(x, y);
        state.getTensionMatrix().enterWar(x, y);
        log(x, y, "declares war on " + y);
        return true;
    }

    /**
     * Leave war. The rulebook gives no automatic successor state, so the
     * pair simply has no state afterwards: tension remains, per :801.
     */
    public boolean exitWar(Faction x, Faction y) {
        if (state == null || !valid(x, y)) return false;
        if (!state.isAtWar(x, y)) return false;
        state.getTensionMatrix().exitWar(x, y);
        log(x, y, "makes peace with " + y);
        return true;
    }

    /** Leave a non-war state. */
    public boolean exitState(Faction x, Faction y, FactionState s) {
        if (!valid(x, y) || s == null || s == FactionState.WAR) return false;
        Pair key = new Pair(x, y);
        Set<FactionState> set = states.get(key);
        if (set == null || !set.remove(s)) return false;
        if (set.isEmpty()) states.remove(key);
        log(x, y, "ends " + s + " with " + y);
        return true;
    }

    /** Every non-war state between the pair, cancelled by entering war (:807). */
    private void clearOtherStates(Faction x, Faction y) {
        Pair key = new Pair(x, y);
        Set<FactionState> set = states.get(key);
        if (set == null || set.isEmpty()) return;
        states.remove(key);
        if (state != null) {
            state.log("States between " + x + " and " + y + " cancelled ("
                    + set.size() + ") by the war.");
        }
    }

    private void log(Faction x, Faction y, String what) {
        if (state != null) state.log(x + " " + what);
    }

    /**
     * A pair needs two distinct, non-null factions. Self-relationships and
     * nulls are refused by every transition and answered false by every
     * query, so no caller has to pre-check.
     */
    private static boolean valid(Faction x, Faction y) {
        return x != null && y != null && x != y;
    }

    /** Unmodifiable view of the non-war pairs, for inspection and tests. */
    public Map<Faction, Map<Faction, Set<FactionState>>> getStateMap() {
        Map<Faction, Map<Faction, Set<FactionState>>> out =
                new HashMap<Faction, Map<Faction, Set<FactionState>>>();
        for (Iterator<Map.Entry<Pair, Set<FactionState>>> it = states.entrySet().iterator();
             it.hasNext(); ) {
            Map.Entry<Pair, Set<FactionState>> e = it.next();
            Map<Faction, Set<FactionState>> row = out.get(e.getKey().a);
            if (row == null) {
                row = new HashMap<Faction, Set<FactionState>>();
                out.put(e.getKey().a, row);
            }
            row.put(e.getKey().b, Collections.unmodifiableSet(
                    EnumSet.copyOf(e.getValue())));
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * The at-war pairs, sourced from the TensionMatrix rather than from
     * this book, so the two can never report different wars.
     */
    public Set<TensionMatrix.FactionPair> getWarPairs() {
        if (state == null) return Collections.emptySet();
        return state.getTensionMatrix().getAtWarPairs();
    }
}
