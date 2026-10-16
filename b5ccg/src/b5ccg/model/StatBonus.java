package b5ccg.model;

import b5ccg.model.enums.*;

/** B5-0366 (B5-0357 proposal §3.1): an immutable description of a single
 *  stat bonus sourced by a card. Replaces the permanent mutation in
 *  CharacterCard.applyStatDelta / FleetCard.applyMilitaryDelta.
 *
 *  Java 6 — final fields, no lambdas/streams/diamonds beyond 6.
 */
public final class StatBonus {
    /** The card that granted this bonus ("" for engine-granted, e.g. leader). */
    public final String       sourceCardId;
    /** The stat this bonus modifies. */
    public final StatKey      stat;
    /** The modifier (may be negative for penalties). */
    public final int          delta;
    /** Minimum value this bonus lifts its host stat to (0 = no floor). */
    public final int          floor;
    /** Where the bonus applies (ATTACHED to a card, or FACTION-wide). */
    public final BonusScope   scope;
    /** The cardId the ATTACHED scope points at (null for FACTION). */
    public final String       targetCardId;
    /** The owning player's name the FACTION scope points at (null for ATTACHED). */
    public final String       ownerName;
    /** Lifetime until swept or removed. */
    public final Expiry       expiry;
    /** True only for "specifically increases Psi" cards (Psi-from-zero rule). */
    public final boolean      psiFromZero;
    /** False = same-source replacement semantics (non-cumulative). */
    public final boolean      cumulative;
    /** GameState.getRoundNumber() when granted — for expiry sweeps. */
    public final int          createdRound;

    public StatBonus(String sourceCardId, StatKey stat, int delta,
                     BonusScope scope, String targetCardId, String ownerName,
                     Expiry expiry, boolean psiFromZero, boolean cumulative,
                     int createdRound, int floor) {
        this.sourceCardId = sourceCardId == null ? "" : sourceCardId;
        this.stat        = stat;
        this.delta       = delta;
        this.floor       = floor;
        this.scope       = scope;
        this.targetCardId = targetCardId;
        this.ownerName   = ownerName;
        this.expiry      = expiry;
        this.psiFromZero = psiFromZero;
        this.cumulative  = cumulative;
        this.createdRound = createdRound;
    }

    /** Convenience factory for an ATTACHED non-Psi bonus (default floor 0). */
    public static StatBonus attached(String sourceId, StatKey stat, int delta,
                                     String targetCardId, Expiry expiry,
                                     int createdRound) {
        return attached(sourceId, stat, delta, targetCardId, expiry, createdRound, 0);
    }

    /** Convenience factory for an ATTACHED non-Psi bonus with an explicit floor. */
    public static StatBonus attached(String sourceId, StatKey stat, int delta,
                                     String targetCardId, Expiry expiry,
                                     int createdRound, int floor) {
        return new StatBonus(sourceId, stat, delta, BonusScope.ATTACHED,
                             targetCardId, null, expiry, false, true, createdRound, floor);
    }

    /** Convenience factory for a FACTION-scope bonus (default floor 0). */
    public static StatBonus faction(String sourceId, StatKey stat, int delta,
                                    String ownerName, Expiry expiry,
                                    int createdRound) {
        return faction(sourceId, stat, delta, ownerName, expiry, createdRound, 0);
    }

    /** Convenience factory for a FACTION-scope bonus with an explicit floor. */
    public static StatBonus faction(String sourceId, StatKey stat, int delta,
                                    String ownerName, Expiry expiry,
                                    int createdRound, int floor) {
        return new StatBonus(sourceId, stat, delta, BonusScope.FACTION,
                             null, ownerName, expiry, false, true, createdRound, floor);
    }
}
