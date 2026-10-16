package b5ccg.engine;

/**
 * B5-1996: the inter-faction relationship states of rulebook section
 * "States" (BABYLON5_CCG_RULEBOOK.md:801-803).
 *
 * The rulebook is explicit that a race pair's relationship is defined
 * "primarily by their tension" and that races "may enter into additional
 * relationships with each other. Such relationships are called states" --
 * naming the common ones as "various forms of alliances, trade pacts
 * (a state of free trade), and war".
 *
 * <p>Tension itself is deliberately NOT a member of this enum: it is a
 * continuous 0-5 directional quantity already owned by
 * {@code b5ccg.model.enums.TensionMatrix}, and the rulebook says states
 * are the <em>additional</em> relationships layered on top of tension.
 * {@code NONE} is not a constant here either: "no state" is the absence of
 * every constant, which is what {@link FactionStateBook} reports.
 *
 * <p>A pair may hold more than one of these at once EXCEPT war, which the
 * rulebook makes exclusive by the "all other states ... are cancelled"
 * clause at :807. That exclusion is enforced in
 * {@link FactionStateBook#enterWar}, not here, because it is a
 * transition and not a property of the value.
 *
 * Java 6 only. Enum, no external libraries.
 */
public enum FactionState {
    /** Rulebook :803 "various forms of alliances". */
    ALLIANCE,
    /** Rulebook :803 "trade pacts (a state of free trade)". */
    TRADE_PACT,
    /** Rulebook :805-817, section "War". */
    WAR
}
