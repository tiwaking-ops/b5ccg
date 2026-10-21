package b5ccg.engine.effect.primitives;

import b5ccg.engine.effect.EffectKind;
import b5ccg.engine.effect.EffectLoadException;
import b5ccg.engine.effect.EffectOperation;
import b5ccg.engine.effect.EffectPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * WP-11 / B5-0034 (A3): the information family.
 *
 * <p>Three primitives: {@code REVEAL_HAND}, {@code VIEW_HAND} and
 * {@code REORDER_DRAW_PILE}.</p>
 *
 * <p><b>Why this family exists.</b> The v0 registry had no way to say
 * anything about information, and the Groups pilot found three group cards
 * whose whole effect is information: "ISN" ("Force one opponent to reveal
 * their hand to all players"), "Observers" ("Look at all players' hands
 * simultaneously") and "Rangers Surveillance" ("Look at the top 3 cards of
 * any player's draw pile and rearrange them").</p>
 *
 * <p><b>They are not bespoke.</b> The escape hatch is for behaviour that has
 * no general shape, and "show a hand" has a perfectly general shape: it is a
 * thing several cards do, in a closed set of variants. A card that reveals a
 * hand is not a one-off rule. Putting these three in their own family keeps
 * the escape hatch honest - it is the size of the <em>bespoke</em> tail that
 * the pilot gate measures, and padding the registry with one-off handlers
 * would make that number a lie.</p>
 *
 * <p><b>Reveal is not view.</b> "Reveal" makes the information public to
 * every player; "view" leaves it with the controller. "Observers" says
 * "Look at all players' hands simultaneously" and the deluxe change adds "may
 * also discard 1 card from any one hand" - the controller sees the hands, the
 * other players do not. One primitive with a flag would have hidden that
 * difference, so they are two primitives.</p>
 */
public final class InformationPrimitives {

    /** The family name, as the registry reports it. */
    public static final String FAMILY = "INFORMATION";

    /**
     * The target's hand is revealed to every player.
     *
     * <p>"ISN": "Rotate this Group any time. Force one opponent to reveal
     * their hand to all players." The target names whose hand; this
     * primitive says that the whole table sees it.</p>
     */
    public static final EffectPrimitive REVEAL_HAND = new SimplePrimitive(
            "REVEAL_HAND", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            return "";
        }
    };

    /**
     * The controller may look at the target's hand, unrevealed.
     *
     * <p>"Observers": "Rotate this Group after any conflict. Look at all
     * players' hands simultaneously." The information goes to the controller
     * only.</p>
     */
    public static final EffectPrimitive VIEW_HAND = new SimplePrimitive(
            "VIEW_HAND", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            return "";
        }
    };

    /**
     * The controller may look at the top N cards of the target's draw pile and
     * reorder them.
     *
     * <p>"Rangers Surveillance": "Rotate this Group any time. Look at the top
     * 3 cards of any player's draw pile and rearrange them." The deluxe
     * printing changes the number to 5, which is why the count is a parameter
     * and not a constant: the two printings are one rule with two numbers,
     * and D5 makes the deluxe one govern.</p>
     */
    public static final EffectPrimitive REORDER_DRAW_PILE = new SimplePrimitive(
            "REORDER_DRAW_PILE", FAMILY,
            EffectKind.ONE_SHOT, EffectKind.ONGOING, EffectKind.TRIGGERED) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            return "count=" + requiredInt(op, "count", location);
        }
    };

    private InformationPrimitives() { }

    /** Every primitive in this family. */
    public static List<EffectPrimitive> all() {
        return Collections.unmodifiableList(
                Arrays.asList(REVEAL_HAND, VIEW_HAND, REORDER_DRAW_PILE));
    }
}
