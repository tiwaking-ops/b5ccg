package b5ccg.ui;

import b5ccg.model.Card;

/**
 * Java 6-compatible predicate for filtering targetable cards.
 * <p>
 * Replaces java.util.function.Predicate (Java 8) per the project's Java 6
 * compatibility requirement. Used by the B5-4216 targeting-mode system
 * on board and hand panels.
 * </p>
 */
public interface TargetFilter {
    /**
     * Returns true if the given card is a valid target.
     *
     * @param card the card to test
     * @return true if the card is a valid target
     */
    boolean isTarget(Card card);
}
