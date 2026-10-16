package b5ccg.model.enums;

/** B5-0366 (B5-0357 proposal §3.1): where a StatBonus applies.
 *  ATTACHED  — to a specific card (by cardId, e.g. an enhancement on a fleet).
 *  FACTION   — to all of a player's cards of a given stat (e.g. "all own
 *              fleets +1 Military").
 */
public enum BonusScope {
    ATTACHED,
    FACTION,
    /** B5-0486: marks an ATTACKED-scope bonus for the damage-after-floor path. */
    ATTACKED
}
