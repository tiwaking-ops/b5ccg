package b5ccg.ui.shell;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JPanel;

/** Swaps decision prompts in place; collapses to zero height when idle. */
public class PromptHost extends JPanel implements StateView {
    private static final String IDLE = "__idle__";

    private final CardLayout cards = new CardLayout();
    private final Map<String, PromptPanel> panels = new HashMap<String, PromptPanel>();

    public PromptHost() {
        setLayout(cards);
        setOpaque(false);
        JPanel idle = new JPanel();
        idle.setOpaque(false);
        idle.setPreferredSize(new Dimension(0, 0));
        add(idle, IDLE);
    }

    public void register(PromptPanel p) {
        panels.put(p.id(), p);
        add(p.component(), p.id());
    }

    /** Height follows the visible prompt, not the tallest one. */
    @Override
    public Dimension getPreferredSize() {
        for (Component c : getComponents()) {
            if (c.isVisible()) {
                Dimension d = c.getPreferredSize();
                return new Dimension(d.width, d.height);
            }
        }
        return new Dimension(0, 0);
    }

    public void refresh(GameView v) {
        String want = IDLE;
        if (v.hasPendingDecision()) {
            PromptPanel p = panels.get(v.pendingDecisionId());
            if (p != null) {
                p.refresh(v);
                want = p.id();
            }
            // Unknown id: fail safe to idle (log it in real code).
        }
        cards.show(this, want);
        revalidate();
        repaint();
    }
}
