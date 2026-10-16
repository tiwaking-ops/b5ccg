package b5ccg.ui.side;

import b5ccg.ui.theme.Theme;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * Sidebar Participants tab (the detached "Conflict Participants" panel's
 * tab successor, M5). Content-agnostic on this row: conflict participants
 * are not a GameView fact, so the wiring row (M5) feeds names via
 * setParticipants. Nothing here reads the engine.
 */
public class ParticipantsPanel extends JPanel {
    private final DefaultListModel model = new DefaultListModel();
    private final JList list;

    public ParticipantsPanel() {
        super(new BorderLayout());
        setOpaque(false);

        list = new JList(model);
        list.setBackground(Theme.BG_PANEL);
        list.setForeground(Theme.TEXT);
        list.setFont(Theme.FONT_BASE);

        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    /** Replace the participant lines; caller supplies them. EDT only. */
    public void setParticipants(List<String> lines) {
        model.clear();
        if (lines == null) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            model.addElement(lines.get(i));
        }
    }

    public void clear() {
        model.clear();
    }
}
