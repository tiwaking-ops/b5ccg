package b5ccg.engine;

import b5ccg.model.GameState;
import b5ccg.model.Player;

/**
 * Callback for round-end notifications. Used to show a round summary
 * popup without blocking the engine thread (B5-4167).
 */
public interface RoundEndCallback {
    /**
     * Called when an action round ends (all players have passed consecutively).
     * Implementations should show a modeless/dismissible popup with the round summary.
     * This method is called on the engine thread, so UI operations must be
     * dispatched to the EDT (e.g., via SwingUtilities.invokeLater).
     *
     * @param state       the game state at round end
     * @param roundNumber the round number that just ended
     * @param players     list of all seated players (for summary data)
     */
    void onRoundEnd(GameState state, int roundNumber, java.util.List<Player> players);
}