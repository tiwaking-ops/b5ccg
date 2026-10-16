package b5ccg.ui.shell;

import b5ccg.ui.theme.Theme;
import b5ccg.ui.theme.ThemedButton;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/**
 * Contextual action bar. Shows ONLY actions that are legal now (card-scoped ones
 * only with a selection). When a card is selected but nothing is legal, shows why.
 */
public class ActionDock extends JPanel implements StateView {
    private final List<ActionSpec> specs = new ArrayList<ActionSpec>();
    private final Map<String, ThemedButton> buttons =
            new LinkedHashMap<String, ThemedButton>();
    private final JLabel reason = new JLabel(" ");
    private GameView lastView;

    public ActionDock() {
        super(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 4));
        setBackground(Theme.BG_PANEL);
        setBorder(Theme.panelBorder("Actions"));
        reason.setFont(Theme.FONT_SMALL);
        reason.setForeground(Theme.TEXT_DIM);
        add(reason); // always last; buttons are inserted before it
    }

    public void addAction(final ActionSpec spec) {
        specs.add(spec);
        ThemedButton b = new ThemedButton(spec.label(), ThemedButton.NORMAL);
        b.setToolTipText(spec.tooltip());
        b.setVisible(false);
        b.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                spec.perform();
            }
        });
        buttons.put(spec.id(), b);
        add(b, getComponentCount() - 1);
    }

    /** Binds ActionSpec.keyCode() shortcuts; each fires only if currently legal. */
    public void installKeys(JComponent root) {
        for (final ActionSpec s : specs) {
            if (s.keyCode() == 0) {
                continue;
            }
            KeyBindings.bind(root, "dock." + s.id(),
                    KeyStroke.getKeyStroke(s.keyCode(), 0),
                    KeyBindings.guarded(new AbstractAction() {
                        public void actionPerformed(ActionEvent e) {
                            if (lastView != null && lastView.isLegal(s.id())) {
                                s.perform();
                            }
                        }
                    }));
        }
    }

    public void refresh(GameView v) {
        lastView = v;
        boolean cardActionShown = false;
        for (ActionSpec s : specs) {
            ThemedButton b = buttons.get(s.id());
            boolean legal = v.isLegal(s.id());
            boolean cardScoped = s.scope() == ActionSpec.SCOPE_CARD;
            boolean show = legal && (!cardScoped || v.hasSelection());
            b.setVisible(show);
            b.setEnabled(legal);
            if (show) {
                b.setToolTipText(s.tooltip());
                if (cardScoped) {
                    cardActionShown = true;
                }
            }
        }
        if (v.hasSelection() && !cardActionShown) {
            String why = v.whyNoActions();
            reason.setText(why == null ? " " : why);
        } else {
            reason.setText(" ");
        }
        revalidate();
        repaint();
    }
}
