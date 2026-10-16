package b5ccg.engine;

import b5ccg.model.GameState;

/** Java 6-compatible callback for UI state updates. */
public interface GameStateCallback {
    void accept(GameState state);
}
