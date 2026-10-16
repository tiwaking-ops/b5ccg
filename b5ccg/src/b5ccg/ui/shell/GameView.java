package b5ccg.ui.shell;

/**
 * The ONLY thing UI panels know about the game. Implemented once, in an adapter
 * over GameState + RulesEngine (e.g. b5ccg.ui.adapter.EngineGameView).
 * Panels never import RulesEngine; legality always comes from here.
 */
public interface GameView {
    String phaseName();
    String statusText();

    /** Text for the Next button, e.g. "End Actions", "Skip Attack". */
    String passLabel();
    boolean isPassEnabled();

    boolean hasSelection();

    /** True if the action is legal now (for the current selection, if card-scoped). */
    boolean isLegal(String actionId);

    /** RulesEngine refusal text for the action, or null if legal. */
    String refusalReason(String actionId);

    /** Why a selected card has no legal action. Used when the dock would be empty. */
    String whyNoActions();

    boolean hasPendingDecision();
    String pendingDecisionId();
}
