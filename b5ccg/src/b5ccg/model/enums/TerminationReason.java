package b5ccg.model.enums;

/**
 * B5-2543 (terminology map from the B5-0383 proposal, close-out of the
 * documentation half recorded in B5-2517): why a player left the game.
 *
 * <p>The rulebook defines two different ways out and one engine-only third,
 * and the model used to record only two of them as unrelated booleans
 * ({@code Player.hasForfeited} / {@code Player.hasSurrendered}), which is why
 * {@code GameState} had to answer "did this player surrender?" by consulting a
 * second source of truth ({@code GameState.surrenderedPlayers}) that could
 * disagree with the flag it was meant to mirror. This enum gives the question
 * one answer.
 *
 * <p>The distinctions the rulebook actually draws:
 *
 * <ul>
 *   <li><b>SURRENDER</b> — rulebook :815&#8211;:823, Unconditional Surrender. A
 *       <i>voluntary</i> exit offered during the discard round, and only to a
 *       player of a race you are at war with. It is <b>not</b> a loss
 *       condition: the opponent who receives the surrender <i>gains</i> 3
 *       influence (:821), and your ambassador survives into asylum on the
 *       recipient's side (:823) instead of being lost. The receiving player
 *       gains something, which is what makes it a surrender and not a forfeit.</li>
 *   <li><b>FORFEIT</b> — rulebook :826 and Draw Round Step 3. Losing the game
 *       for cause: failing to draw with nothing left to discard (:460, :826),
 *       breaking a rule, or (since B5-1979) choosing to forfeit mid-turn. Your
 *       cards go home and the ambassador is <b>discarded</b>, not handed over;
 *       nobody gains influence. A forfeiting player has lost; a surrendering
 *       player has left.</li>
 *   <li><b>TIMEOUT</b> — declared for completeness by B5-2517's map. Nothing
 *       in the rulebook or the engine produces it today: there is no wall-clock
 *       deadline and no missed-turn penalty anywhere in the model, so no code
 *       sets it. It is here so that a future timer has a distinct value to
 *       record instead of overloading FORFEIT — a missed turn is not a broken
 *       rule, and conflating them would make the distinction unrecoverable once
 *       written. Recorded, not invented: see the report's "declared but
 *       unassigned" note.</li>
 * </ul>
 *
 * <p><b>Ordering carries no meaning.</b> Unlike {@link VictoryPath}, where the
 * declaration order is the order checkVictory tests the paths in and a reorder
 * would silently change which path wins an overlap, nothing here branches on
 * ordinal or on declaration order. Do not sort by ordinal; switch on the value.
 */
public enum TerminationReason {
    /** Voluntary exit to a war opponent; the recipient gains 3 influence
     *  (:821) and receives your ambassador into asylum (:823). */
    SURRENDER,
    /** Loss for cause: could not draw with nothing to discard (:460, :826),
     *  rule-breaking, or the B5-1979 voluntary forfeit. Ambassador discarded,
     *  nobody gains influence. */
    FORFEIT,
    /** Declared-but-unassigned (see above). No code path sets this today. */
    TIMEOUT
}
