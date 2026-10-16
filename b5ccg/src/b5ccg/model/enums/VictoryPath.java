package b5ccg.model.enums;

/**
 * B5-0663: the five distinct victory paths the engine evaluates, in the
 * exact order checkVictory tests them (B5-0641 measurement). A reorder
 * would silently change which path fires when two paths qualify on the
 * same state — e.g. a 30-vs-15 Major+Standard overlap resolves to Major
 * only because Major is tested first.
 */
public enum VictoryPath {
    /** Rulebook :170 — only one non-forfeited player remains. */
    LAST_STANDING,
    /** Rulebook :176 condition 2 — Babylon 5 influence >= 20 and exactly
     *  one non-forfeited, non-major-agenda player strictly leads. */
    STATION_CONDITION_2,
    /** Rulebook :170/:174 — the player's revealed agenda win condition is met. */
    AGENDA_CONDITION,
    /** Rulebook :182 — at least 20 Power and at least 10 more than every
     *  other non-forfeited player. */
    MAJOR,
    /** Rulebook :170/:176 condition 1 — at least 20 Power and strictly more
     *  than every other non-forfeited player, no major agenda in play. */
    STANDARD
}
