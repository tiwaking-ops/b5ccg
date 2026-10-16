package b5ccg.ui.shell;

/** One player action. Legality is NOT here; it comes from GameView.isLegal(id). */
public interface ActionSpec {
    int SCOPE_GLOBAL = 0; // visible whenever legal (e.g. Build Influence)
    int SCOPE_CARD = 1;   // visible only with a selection, and only when legal

    String id();
    String label();
    String tooltip();
    int scope();

    /** java.awt.event.KeyEvent VK_ constant, or 0 for none. */
    int keyCode();

    /** Called on the EDT when the player triggers the action. */
    void perform();
}
