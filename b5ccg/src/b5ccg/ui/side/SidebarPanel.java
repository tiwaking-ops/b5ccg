package b5ccg.ui.side;

import b5ccg.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.border.EmptyBorder;

/**
 * The one fixed sidebar (redesign report section 7 / conformance section 3 row 5).
 * Sits EAST of {@code MainFrame}, which already reserves that slot. Replaces
 * nothing on this row: the detachables stay live until the M5 sunset row.
 *
 * Tabs per row B5-4217: Preview, Log, Participants, Legend.
 * The influence and tension/unrest trackers stay in {@code b5ccg.ui} on this
 * row (they are wired into MainWindow; moving them is M5 scope).
 *
 * Panels here never import RulesEngine; they see only GameView (shell) or
 * plain content fed by the wiring row. Implements StateView so the single
 * MainFrame refresh fan-out reaches the sidebar once it is wired.
 */
public class SidebarPanel extends JPanel implements b5ccg.ui.shell.StateView {
    private final PreviewPane preview;

    public SidebarPanel() {
        super(new BorderLayout());
        setOpaque(false);

        preview = new PreviewPane();
        LogPanel log = new LogPanel();
        ParticipantsPanel participants = new ParticipantsPanel();
        LegendPanel legend = new LegendPanel();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Preview", preview);
        tabs.addTab("Log", log);
        tabs.addTab("Participants", participants);
        tabs.addTab("Legend", legend);
        tabs.setBackground(Theme.BG_PANEL);
        tabs.setForeground(Theme.TEXT);
        tabs.setFont(Theme.FONT_SMALL_BOLD);

        setBorder(new EmptyBorder(Theme.GAP, Theme.GAP, Theme.GAP, Theme.GAP));
        add(tabs, BorderLayout.CENTER);
        setPreferredSize(new Dimension(280, 0));
    }

    /** EDT only; fans the refresh into the tabs that consume GameView. */
    public void refresh(b5ccg.ui.shell.GameView view) {
        preview.refresh(view);
        // Log and Participants have no GameView fact yet (log stream and
        // conflict participants are adapter scope, B5-4219); they render
        // content fed by setters and are deliberately left untouched here.
    }
}
