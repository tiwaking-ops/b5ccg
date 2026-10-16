package b5ccg.ui;

import b5ccg.model.Card;

/**
 * Java 6-compatible callback for card-selection events.
 * <p>
 * Public so that the board sub-package (b5ccg.ui.board) can reference it
 * in the B5-4216 targeting-mode controller.
 * </p>
 */
public interface CardSelectedListener {
    void onCardSelected(Card card);
}
