package b5ccg.ui.side;

import b5ccg.ui.theme.Theme;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Sidebar Legend tab: the conflict-type to ability legend, ported from
 * MainWindow's B5-0329 F9 detachable (same four rows, Theme tokens instead
 * of hardcoded colors). Static reference content; no game state, no refresh.
 */
public class LegendPanel extends JPanel {
    private static final String[][] LEGEND = {
        {"DIPLOMACY", "Diplomacy"},
        {"INTRIGUE",  "Intrigue"},
        {"MILITARY",  "Military (Fleets)"},
        {"PSI",       "Psi"}
    };

    public LegendPanel() {
        super(new GridLayout(LEGEND.length, 2, 4, 2));
        setOpaque(false);
        setAlignmentX(LEFT_ALIGNMENT);
        setMaximumSize(new Dimension(280, 120));
        setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(Theme.PAD, Theme.PAD, Theme.PAD, Theme.PAD),
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.LINE_BRIGHT),
                "Conflict Types \u2192 Abilities", 0, 0, Theme.FONT_SMALL_BOLD, Theme.TEXT)));

        for (int i = 0; i < LEGEND.length; i++) {
            JLabel type = new JLabel(LEGEND[i][0]);
            type.setForeground(Theme.ACCENT);
            type.setFont(Theme.FONT_MONO);
            add(type);

            JLabel ability = new JLabel("\u2192 " + LEGEND[i][1]);
            ability.setForeground(Theme.TEXT_DIM);
            ability.setFont(Theme.FONT_MONO);
            add(ability);
        }
    }
}
