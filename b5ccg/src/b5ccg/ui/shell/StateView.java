package b5ccg.ui.shell;

/** One refresh gate per panel. Called on the EDT only; must not recompute legality. */
public interface StateView {
    void refresh(GameView view);
}
