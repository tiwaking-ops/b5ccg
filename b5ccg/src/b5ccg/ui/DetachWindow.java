package b5ccg.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * B5-4155: the floating half of a detachable panel.
 *
 * <p>At 1366x768 the main frame's right sidebar and toolbar rows compete for
 * vertical space, so several panels are only reachable by scrolling the very
 * area they were meant to summarise. This window gives any one sidebar or
 * toolbar panel its own always-visible modeless frame: detach moves the
 * panel into this window, attach moves the SAME instance back into its old
 * host at its old index.
 *
 * <p><b>Same instance, never a copy.</b> The panel the window shows is the
 * object {@code MainWindow.refresh} already updates, so a detached influence
 * tracker keeps tracking and a detached log keeps logging. Duplicating the
 * component instead of reparenting it is the obvious alternative and is
 * wrong twice over: the copy would need its own update call, and two
 * instances of one panel can disagree.
 *
 * <p><b>Hiding is not closing.</b> {@code windowClosing} hides and calls the
 * listener back (which unchecks the View menu item) but never disposes, so
 * reopening restores position and content rather than rebuilding them. The
 * owning frame disposes this window alongside itself.
 */
public class DetachWindow extends JDialog {

    /** Called when the user hides the window, so the View menu can uncheck. */
    public interface HiddenListener {
        void windowHidden(DetachWindow source);
    }

    private final MainWindow owner;
    private final JComponent content;
    private HiddenListener hiddenListener;
    private JPanel body;

    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public DetachWindow(MainWindow owner, String title, JComponent content) {
        super(owner, title, false); // modeless: playtesting must never block
        this.owner = owner;
        this.content = content;

        setLayout(new BorderLayout(2, 2));
        getContentPane().setBackground(new Color(10, 20, 10));

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(30, 50, 30));
        titleBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(new Color(180, 220, 180));
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 11));

        JButton closeBtn = new JButton("\u00d7");
        closeBtn.setMargin(new Insets(0, 0, 0, 0));
        closeBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        closeBtn.setForeground(new Color(220, 180, 180));
        closeBtn.setBackground(new Color(30, 50, 30));
        closeBtn.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        closeBtn.setFocusPainted(false);
        closeBtn.setToolTipText("Re-dock this panel (reopen from the View menu)");
        closeBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                hideAndNotify();
            }
        });

        titleBar.add(titleLabel, BorderLayout.WEST);
        titleBar.add(closeBtn, BorderLayout.EAST);
        add(titleBar, BorderLayout.NORTH);

        // B5-4155: the body is created eagerly but the panel is NOT reparented
        // here. DetachWindow registers into panels still living in their host,
        // so eagerly adding the content would yank every detachable out of the
        // frame at construction and orphan it in an invisible dialog.
        body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(new Color(10, 20, 10));
        add(body, BorderLayout.CENTER);

        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                hideAndNotify();
            }
        });

        // Drag anywhere on the title bar.
        titleBar.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                dragOffsetX = e.getX();
                dragOffsetY = e.getY();
            }
        });
        titleBar.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                if (!isShowing()) return;
                Point p = getLocation();
                setLocation(p.x + e.getX() - dragOffsetX, p.y + e.getY() - dragOffsetY);
            }
        });

        getRootPane().registerKeyboardAction(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { hideAndNotify(); }
        }, KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    public void setHiddenListener(HiddenListener listener) {
        this.hiddenListener = listener;
    }

    /** The panel this window floats. Identity is stable for the session. */
    public JComponent getContent() {
        return content;
    }

    /** The body panel the content is hung from when detached. */

    /**
     * Moves the panel out of its host into this window and shows it.
     */
    public void detach(Container host) {
        Container current = content.getParent();
            if (current != null && current != body) {
                current.remove(content);
            }
            if (content.getParent() != body) {
                body.add(content);
            }
            content.setVisible(true);
            pack();
            place();
            setVisible(true);
            toFront();
        }

    /**
     * Put the panel back into {@code host} and hide this window. When
     * {@code visible} is false the panel stays in the host but is not shown,
     * which is how the View menu's default-off entries keep the frame's
     * footprint small without losing the component.
     *
     * @param constraint a layout constraint for hosts whose layout needs one
     *        (BorderLayout slot); null means "insert at {@code index}", which
     *        is right for the BoxLayout sidebar
     */
    public void attach(Container host, Object constraint, int index, boolean visible) {
        Container current = content.getParent();
        if (current != null) {
            current.remove(content);
        }
        if (host != null) {
            if (constraint != null) {
                host.add(content, constraint);
            } else if (index >= 0 && index <= host.getComponentCount()) {
                host.add(content, index);
            } else {
                host.add(content);
            }
        }
        content.setVisible(visible);
        setVisible(false);
        if (host != null) {
            host.revalidate();
            host.repaint();
        }
    }

    private void hideAndNotify() {
        setVisible(false);
        if (hiddenListener != null) {
            hiddenListener.windowHidden(this);
        }
    }

    /**
     * Stacks the window below the main frame, one step down per already-open
     * detachable, so several opened at once do not land on top of each other.
     *
     * B5-4166: BOTH branches clamp against the screen on both axes. The owner
     * branch used to compute {@code y = origin.y + owner.getHeight() + cascade}
     * with no ceiling: with the frame at y=0 and the 1024x700 minimum frame
     * size, a detached window landed at y=724 or below — entirely beneath the
     * visible area of a 768-tall screen. The panel was still there and still
     * tracking, which to a playtester is indistinguishable from deleted. The
     * fallback branch already clamped; this makes the two agree.
     */
    public void place() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        if (owner != null && owner.isShowing()) {
            Point origin = owner.getLocation();
            int x = origin.x + 24 * (cascadeSlot() % 8);
            int y = origin.y + owner.getHeight() + 24 * (cascadeSlot() % 8);
            if (y > screen.height - 120) y = 40;
            if (x > screen.width - 160) x = 40;
            if (y < 0) y = 40;
            if (x < 0) x = 40;
            setLocation(x, y);
        } else {
            int x = 40 + 24 * (cascadeSlot() % 8);
            int y = 40 + 24 * (cascadeSlot() % 8);
            if (y > screen.height - 120) y = 40;
            if (x > screen.width - 160) x = 40;
            setLocation(x, y);
        }
    }

    private int cascadeSlot() {
        if (owner == null) return 0;
        Window[] owned = owner.getOwnedWindows();
        int n = 0;
        for (int i = 0; i < owned.length; i++) {
            if (owned[i] instanceof DetachWindow && owned[i] != this) n++;
        }
        return n;
    }

    /** Keeps detached windows pinned while the main frame moves. */
    public void onMainWindowMoved() {
        if (isShowing()) place();
    }

    /** True while this window currently holds the panel rather than its host. */
    public boolean isDetached() {
        return content.getParent() != null && SwingUtilities.isDescendingFrom(content, getContentPane());
    }
}
