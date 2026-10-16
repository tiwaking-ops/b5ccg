package b5ccg.model;

import b5ccg.model.enums.VictoryPath;

/**
 * B5-0663: the result of a victory-path query — which path crowned a winner,
 * the winner's Power total, and exactly one qualifier that explains that path.
 * Returned by {@link b5ccg.engine.RulesEngine#checkVictoryPath(GameState)}.
 * Purely additive; the existing {@link b5ccg.engine.RulesEngine#checkVictory(GameState)}
 * return type and every caller of it are untouched.
 *
 * A null path (not a sentinel enum value) means nobody has won yet.
 */
public final class VictoryPathResult {

    private final VictoryPath path;
    private final int power;
    private final Object qualifier; // Integer or String — path-specific

    public VictoryPathResult(VictoryPath path, int power, Object qualifier) {
        this.path = path;
        this.power = power;
        this.qualifier = qualifier;
    }

    /** Which of the five rulebook paths fired, or null when nobody has won. */
    public VictoryPath getPath()     { return path; }
    /** Winner's current Power (influence). */
    public int         getPower()    { return power; }
    /**
     * Path-specific qualifier:
     * <ul>
     *   <li>{@link VictoryPath#LAST_STANDING} — Integer: remaining non-forfeited player count (always 1 when this path fires).</li>
     *   <li>{@link VictoryPath#STATION_CONDITION_2} — Integer: Babylon 5 station influence at the round boundary.</li>
     *   <li>{@link VictoryPath#AGENDA_CONDITION} — String: agenda {@code winConditionKey} (e.g. {@code INFLUENCE_20}, {@code MILITARY_SUPREMACY}, {@code MOST_INNER_CIRCLE}).</li>
     *   <li>{@link VictoryPath#MAJOR} — Integer: lead margin in Power vs the next-highest non-forfeited player (>= 10).</li>
     *   <li>{@link VictoryPath#STANDARD} — Integer: lead margin in Power vs the next-highest non-forfeited player (>= 1).</li>
     * </ul>
     */
    public Object      getQualifier(){ return qualifier; }

    /** Convenience: qualifier as int (for paths whose qualifier is an Integer).
     *  Callers must verify the path first. */
    public int getQualifierAsInt() {
        return qualifier instanceof Integer ? ((Integer) qualifier).intValue() : -1;
    }

    /** Convenience: qualifier as String (for AGENDA_CONDITION).
     *  Callers must verify the path first. */
    public String getQualifierAsString() {
        return qualifier instanceof String ? (String) qualifier : null;
    }
}
