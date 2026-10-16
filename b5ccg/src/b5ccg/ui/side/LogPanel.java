package b5ccg.ui.side;

import b5ccg.ui.theme.Theme;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Sidebar Log tab (the detached "Game Log" logArea's tab successor, M5).
 * Content-agnostic on this row: GameView carries no log stream yet, so the
 * wiring row (M5) or the adapter (B5-4219) feeds lines via appendLine.
 * Nothing here invents log entries.
 */
public class LogPanel extends JPanel {
    private final JTextArea area;

    public LogPanel() {
        super(new BorderLayout());
        setOpaque(false);

        area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBackground(Theme.BG_PANEL);
        area.setForeground(Theme.TEXT);
        area.setFont(Theme.FONT_MONO);

        add(new JScrollPane(area), BorderLayout.CENTER);
    }

    /** One log line; caller supplies the text. EDT only. */
    public void appendLine(String line) {
        if (area.getText().length() > 0) {
            area.append("\n");
        }
        area.append(line);
        area.setCaretPosition(area.getDocument().getLength());
    }

    public void clear() {
        area.setText("");
    }
}
