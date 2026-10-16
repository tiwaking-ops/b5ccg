package b5ccg.ui.side;

import b5ccg.ui.shell.GameView;
import b5ccg.ui.shell.StateView;
import b5ccg.ui.theme.Theme;
import java.awt.GridLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

/**
 * Sidebar Preview tab. Shows only what GameView actually offers: phase,
 * status text, and whether a card is selected (plus the no-action reason
 * when one exists). No legality logic, no fabricated facts; anything not in
 * GameView renders as an em dash until the adapter carries it (B5-4219).
 */
public class PreviewPane extends JPanel implements StateView {
    private final JLabel phaseValue = new LabelValue();
    private final JLabel statusValue = new LabelValue();
    private final JLabel selectionValue = new LabelValue();
    private final JTextArea reason = new JTextArea();

    public PreviewPane() {
        super(new GridLayout(0, 1, 0, Theme.GAP));
        setOpaque(false);
        setBorder(new EmptyBorder(Theme.PAD, Theme.PAD, Theme.PAD, Theme.PAD));

        add(new LabelKey("Phase"));
        add(phaseValue);
        add(new LabelKey("Status"));
        add(statusValue);
        add(new LabelKey("Selection"));
        add(selectionValue);

        reason.setEditable(false);
        reason.setLineWrap(true);
        reason.setWrapStyleWord(true);
        reason.setBackground(Theme.BG_PANEL);
        reason.setForeground(Theme.TEXT_DIM);
        reason.setFont(Theme.FONT_SMALL);
        reason.setBorder(new EmptyBorder(2, 0, 0, 0));
        add(new JScrollPane(reason));
    }

    /** EDT only; must not recompute legality. */
    public void refresh(GameView view) {
        phaseValue.setText(view.phaseName());
        statusValue.setText(orDash(view.statusText()));
        if (view.hasSelection()) {
            selectionValue.setText("selected");
            String why = view.whyNoActions();
            reason.setText(why == null ? "" : why);
        } else {
            selectionValue.setText("\u2014");
            reason.setText("");
        }
    }

    private static String orDash(String s) {
        return (s == null || s.length() == 0) ? "\u2014" : s;
    }

    private static class LabelKey extends JLabel {
        LabelKey(String text) {
            super(text);
            setForeground(Theme.TEXT_DIM);
            setFont(Theme.FONT_SMALL_BOLD);
        }
    }

    private static class LabelValue extends JLabel {
        LabelValue() {
            setForeground(Theme.TEXT);
            setFont(Theme.FONT_BASE);
        }
    }
}
