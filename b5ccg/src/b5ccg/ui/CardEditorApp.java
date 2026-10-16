package b5ccg.ui;

import java.awt.GraphicsEnvironment;

import javax.swing.SwingUtilities;

/**
 * B5-4107: Standalone main entry point for the Card Editor.
 *
 * <p>Launched independently via {@code java -cp out b5ccg.ui.CardEditorApp}.
 * Does NOT touch {@code MainWindow} or {@code Main} — toolbar wiring is a
 * separate follow-up task (B5-4113).
 *
 * <p>Java 6 only: no lambda for the {@code Runnable} passed to
 * {@code SwingUtilities.invokeLater}.
 */
public class CardEditorApp {

    /**
     * Launches the card editor dialog. If the JVM is in headless mode
     * (no display available), the app exits gracefully without throwing
     * a {@link java.awt.HeadlessException}.
     */
    public static void main(String[] args) {
        // B5-4107: headless guard — same pattern as
        // MainWindow.showDeckBuilderDialog (GraphicsEnvironment.isHeadless()).
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Card Editor: headless mode -- no GUI available.");
            return;
        }

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    CardEditorDialog dialog = new CardEditorDialog(null);
                    dialog.setVisible(true);
                } catch (Exception e) {
                    System.err.println("Card Editor failed to start: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }
}
