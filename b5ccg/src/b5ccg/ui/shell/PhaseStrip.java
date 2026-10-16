package b5ccg.ui.shell;

import b5ccg.ui.theme.Theme;
import b5ccg.ui.theme.ThemedButton;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/** Phase ladder + status text + the one big Next/Pass button. */
public class PhaseStrip extends JPanel implements StateView {
    /** Raw engine phase keys (GamePhase.name()) - the ONLY thing the highlight compares. */
    private final String[] keys;
    private final JLabel[] chips;
    private final JLabel status = new JLabel(" ");
    private final ThemedButton next = new ThemedButton("Next", ThemedButton.PRIMARY);
    /** Raw key of the chip refresh() last highlighted; null when none matched. */
    private String highlighted = null;

    /**
     * Display label for an engine phase key. Sourced from the GamePhase enum
     * order and its rulebook III comments (model/enums/GamePhase.java), pinned
     * by B5-4183. An UNKNOWN key returns ITSELF rather than an invented label,
     * so a future enum constant can never be silently mislabelled.
     */
    public static String labelFor(String key) {
        if ("SETUP".equals(key)) return "Setup";
        if ("CONFLICT".equals(key)) return "Conflict";
        if ("ACTION".equals(key)) return "Action";
        if ("MERCENARY".equals(key)) return "Mercenaries";
        if ("CONFLICT_RESOLUTION".equals(key)) return "Resolution";
        if ("AFTERMATH".equals(key)) return "Aftermath";
        if ("DRAW".equals(key)) return "Draw";
        if ("END_ROUND".equals(key)) return "End Round";
        return key;
    }

    /** phaseNames are raw engine keys; see labelFor() for what each chip shows. */
    public PhaseStrip(String[] phaseNames, ActionListener onNext) {
        super(new BorderLayout(Theme.GAP, 2));
        this.keys = phaseNames;
        this.chips = new JLabel[phaseNames.length];
        setBackground(Theme.BG_PANEL);
        setBorder(Theme.pad(Theme.GAP));

        JPanel chipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        chipRow.setOpaque(false);
        for (int i = 0; i < phaseNames.length; i++) {
            JLabel c = new JLabel(labelFor(phaseNames[i]));
            c.setOpaque(true);
            c.setFont(Theme.FONT_SMALL_BOLD);
            c.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
            // Defined colours before the first refresh, so a strip that has
            // never seen state is not painted with the LAF's defaults.
            c.setBackground(Theme.BG_RAISED);
            c.setForeground(Theme.TEXT_DIM);
            chips[i] = c;
            chipRow.add(c);
        }
        next.setToolTipText("Next / Pass (Space)");
        next.addActionListener(onNext);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(chipRow, BorderLayout.CENTER);
        top.add(next, BorderLayout.EAST);

        status.setFont(Theme.FONT_BASE);
        status.setForeground(Theme.TEXT);
        add(top, BorderLayout.NORTH);
        add(status, BorderLayout.SOUTH);
    }

    public ThemedButton nextButton() {
        return next;
    }

    /**
     * B5-4213: bind SPACE to the one Next/Pass button at window level, so the
     * primary scroll row's replacement is reachable without the mouse.
     *
     * Wrapped in {@link KeyBindings#guarded} so a space typed into any text
     * field never passes the turn, and gated on <code>isEnabled()</code> so a
     * dark button stays dark: the accelerator must carry exactly the
     * enablement the button itself shows, not a second opinion about legality.
     */
    public void installKeys(JComponent root) {
        KeyBindings.bind(root, "phase.next",
                KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
                KeyBindings.guarded(new AbstractAction() {
                    public void actionPerformed(ActionEvent e) {
                        if (next.isEnabled()) {
                            next.doClick();
                        }
                    }
                }));
    }

    /** Raw engine key of the chip refresh() currently highlights, or null if none matched. */
    public String highlightedKey() {
        return highlighted;
    }

    public void refresh(GameView v) {
        String current = v.phaseName();
        highlighted = null;
        for (int i = 0; i < chips.length; i++) {
            boolean on = keys[i].equals(current);
            chips[i].setBackground(on ? Theme.ACCENT : Theme.BG_RAISED);
            chips[i].setForeground(on ? Theme.BG_APP : Theme.TEXT_DIM);
            if (on) highlighted = keys[i];
        }
        next.setText(v.passLabel());
        next.setEnabled(v.isPassEnabled());
        status.setText(v.statusText());
    }
}
