package b5ccg.model.enums;

/** B5-0366 (B5-0357 proposal §3.1): the lifetime of a StatBonus.
 *  WHILE_IN_PLAY         — until the source card leaves play.
 *  END_OF_TURN           — swept when the owner's turn ends.
 *  START_OF_NEXT_OWNER   — swept at the start of the owner's NEXT turn.
 *  ON_EVENT              — removed by an explicit engine call (deferred;
 *                          no current card grants this).
 */
public enum Expiry {
    WHILE_IN_PLAY,
    END_OF_TURN,
    START_OF_NEXT_OWNER_TURN,
    ON_EVENT
}
