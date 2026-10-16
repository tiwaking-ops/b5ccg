package b5ccg.ui.shell;

import javax.swing.JComponent;

/** One inline decision (conflict join, aftermath, mercenary bid, ...). */
public interface PromptPanel {
    /** Must equal GameView.pendingDecisionId() while this decision is pending. */
    String id();

    JComponent component();

    /** Update labels/enabled state from the view. Never recompute legality here. */
    void refresh(GameView view);
}
