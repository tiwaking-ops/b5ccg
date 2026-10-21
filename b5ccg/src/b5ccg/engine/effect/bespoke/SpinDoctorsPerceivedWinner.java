package b5ccg.engine.effect.bespoke;

/**
 * WP-11 / B5-0034 (A3): the bespoke handler for the "Spin Doctors" group.
 *
 * <p><b>This is the escape hatch, used once.</b> A3 principle 1 says "A small
 * keyed escape hatch handles the genuinely bespoke tail", and the v0 registry
 * published zero handlers, which was the strongest statement the schema could
 * make about the size of that tail. The Groups pilot measured the tail and
 * found it: of the 26 group titles, exactly one - "Spin Doctors" - is a
 * behaviour that no general primitive can express, and the effect census
 * (B5-0005) independently classified that same card as BESPOKE. This class
 * is that card's handler.</p>
 *
 * <p><b>What the card says.</b> "Rotate this Group after any conflict. Change
 * the perceived winner of a Diplomacy conflict for Influence gain purposes.
 * (Premiere printing: you gain their Influence; they gain none.)" The
 * controller of the group is deemed the winner of a Diplomacy conflict for
 * the one purpose of gaining Influence, and the player who actually won it
 * gains nothing for it.</p>
 *
 * <p><b>Why it is bespoke and not a primitive.</b> The rule is not an
 * operation on a card or a player; it changes <em>who a conflict's outcome
 * belongs to</em>, and then lets the ordinary influence-gain rule run against
 * that answer. There is no operation "the winner of the last conflict is
 * somebody else": the winner is a fact about a conflict, not a piece of
 * state a card can modify. A primitive that took an amount, a target and a
 * trigger would be able to say "you gain 1 influence" but not "the winner is
 * you, and therefore the real winner gains nothing" - the second half is the
 * whole card, and it is a statement about the rule, not about a number.</p>
 *
 * <p><b>What this class is, and is not.</b> It is a pure rule: it takes the
 * controller and the actual winner and returns the perceived winner. It has
 * no dependency on {@code GameState} and it changes nothing by itself, which
 * is the shape A6 asks for when it extracts a module from the engine
 * ("Build a pure module with no dependency on GameState, taking value
 * parameters and returning a decision object"). It is not the engine's
 * dispatch: the registry records this class's name under the handler key
 * (see {@link #HANDLER_KEY}), and the engine's own effect execution - the
 * strangler that consults the registry first and falls back to the existing
 * {@code CardEffects} predicate - is the work that will call it. Publishing
 * the key is what makes the data loadable; the call site is still to come,
 * and saying so is better than implying the card is already playable.</p>
 */
public final class SpinDoctorsPerceivedWinner {

    /**
     * The handler key a data file names in a BESPOKE operation.
     *
     * <p>The key is what the registry publishes; a key that is not published
     * is a load error, which is what keeps the hatch from becoming a place
     * where a data file names arbitrary code.</p>
     */
    public static final String HANDLER_KEY = "SPIN_DOCTORS_PERCEIVED_WINNER";

    /**
     * The name of the Java handler the key resolves to.
     *
     * <p>Recorded in the registry's handler table. The name is a label for
     * the handler, and the strangler will resolve it when the engine starts
     * executing effect entries.</p>
     */
    public static final String HANDLER_NAME = "SpinDoctorsPerceivedWinner";

    private SpinDoctorsPerceivedWinner() { }

    /**
     * The perceived winner of a Diplomacy conflict while this group is in
     * play.
     *
     * @param controller the player who controls the "Spin Doctors" group
     * @param actualWinner the player who actually won the conflict
     * @return the player who is deemed to have won it for Influence purposes
     */
    public static String perceivedWinner(String controller, String actualWinner) {
        if (controller == null || controller.trim().length() == 0) {
            throw new IllegalArgumentException("the controller of the group is null or empty");
        }
        // The card changes the perceived winner, not the amount: the ordinary
        // Diplomacy influence-gain rule runs against this answer.
        return controller.trim();
    }

    /**
     * Whether the player who actually won the conflict still gains the
     * Diplomacy influence.
     *
     * <p>"you gain their Influence; they gain none" - so the real winner
     * gains nothing, unless the real winner <em>is</em> the controller, in
     * which case there is no one else to take the gain away from and the
     * card's effect is a no-op.</p>
     */
    public static boolean actualWinnerGains(String controller, String actualWinner) {
        if (actualWinner == null) return false;
        return actualWinner.trim().equals(perceivedWinner(controller, actualWinner));
    }
}
