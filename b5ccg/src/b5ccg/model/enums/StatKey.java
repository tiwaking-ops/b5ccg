package b5ccg.model.enums;

/** B5-0366 (B5-0357 proposal §3.1): the stats a bonus can modify.
 *  INFLUENCE is deliberately absent — D9/B5-0342 owns the
 *  rating-vs-applied-influence pool, it is not a combat stat here.
 */
public enum StatKey {
    DIPLOMACY,
    INTRIGUE,
    PSI,
    LEADERSHIP,
    MILITARY,
    /** B5-0677 (rulebook :171/:1158, B5-0667 proposal §3.2): a Power add-on.
     *  Power is a PLAYER-level derived quantity (getPower() = influence + the
     *  sum of POWER-tagged bonuses), never a card stat; no pool card grants it
     *  today, so the seam is exercised only through synthetic fixtures. */
    POWER
}
