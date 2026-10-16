package b5ccg.ui.shell;

import b5ccg.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;

/**
 * Assembly only. All state reaches widgets through refresh(GameView).
 * No legality logic, no RulesEngine import.
 */
public class MainFrame extends JFrame {
    private final List<StateView> views = new ArrayList<StateView>();

    public MainFrame(String title, PhaseStrip top, JComponent board,
            ActionDock dock, PromptHost prompts, JComponent hand, JComponent sidebar) {
        super(title);
        getContentPane().setBackground(Theme.BG_APP);
        setLayout(new BorderLayout(Theme.GAP, Theme.GAP));

        JPanel promptAndDock = new JPanel(new BorderLayout(0, Theme.GAP));
        promptAndDock.setOpaque(false);
        promptAndDock.add(prompts, BorderLayout.NORTH);
        promptAndDock.add(dock, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(0, Theme.GAP));
        south.setOpaque(false);
        south.add(promptAndDock, BorderLayout.NORTH);
        south.add(hand, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, Theme.GAP));
        center.setOpaque(false);
        center.add(board, BorderLayout.CENTER);
        center.add(south, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(sidebar, BorderLayout.EAST);

        // B5-4213: the Space accelerator for the single Next/Pass button is bound
        // on the PhaseStrip (with the KeyBindings text-field guard) -- install it
        // on the frame root so the primary scroll row's replacement is reachable
        // without the mouse. Enablement is carried by PhaseStrip.refresh() reading
        // GameView.isPassEnabled(), so no legality lives here.
        top.installKeys(getRootPane());

        views.add(top);
        views.add(prompts);
        views.add(dock);

        setMinimumSize(new Dimension(1100, 760));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    /** Register panels (sidebar trackers, hand, board...) that implement StateView. */
    public void addView(StateView v) {
        views.add(v);
    }

    /** The ONLY method that pushes game state into widgets. Call on the EDT. */
    public void refresh(GameView view) {
        for (StateView v : views) {
            v.refresh(view);
        }
    }
}
