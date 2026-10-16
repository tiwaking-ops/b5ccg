package b5ccg.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * B5-2410: Detached always-visible Action window.
 * At 1366x768 the main toolbar's action bar is nearly invisible so the game
 * is hard to play. This window holds the scrollable secondary action row —
 * Support/Oppose, Attack, Censure, War, Surrender, the dialog openers — in a
 * compact floating panel that stays reachable without scrolling.
 *
 * <p><b>B5-4155: the six primary controls deliberately stayed in the frame.</b>
 * This class was written (and B5-2410 never wired it up) to host the six
 * primary ACTION-turn buttons. That arrangement is not what the fleet ships:
 * the primary row is the one row the main frame must keep always visible, and
 * a Swing component has exactly one parent, so handing those six buttons to a
 * dialog would empty the frame's primary row. This row therefore re-points the
 * same class at the SECONDARY row instead — the row that actually scrolled —
 * leaving Pass/Play/Initiate/Sponsor/Promote/Build Influence in the frame and
 * moving everything behind them into this window.
 *
 * The window is modeless, always-on-top, and positioned just below the main
 * frame. It can be dragged and will remember its position during the session.
 * The components are the SAME instances used in the main toolbar so their
 * enablement state stays perfectly in sync — no duplicate logic.
 */
public class ActionWindow extends JDialog {

    private final MainWindow mainWindow;
    private final JComponent actionRow;

    // Drag support
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    /**
     * @param owner     the frame this dialog belongs to
     * @param actionRow the scroll pane wrapping the secondary button row;
     *                  it is reparented into this dialog, never copied
     */
    public ActionWindow(MainWindow owner, JComponent actionRow) {
        super(owner, "Actions", false); // modeless: never blocks play
        this.mainWindow = owner;
        this.actionRow = actionRow;

        setLayout(new BorderLayout(4, 4));
        getContentPane().setBackground(new Color(10, 20, 10));

        // Title bar with close button (compact)
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(30, 50, 30));
        titleBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        JLabel titleLabel = new JLabel("Actions");
        titleLabel.setForeground(new Color(180, 220, 180));
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 10));

        JButton closeBtn = new JButton("×");
        closeBtn.setMargin(new Insets(0, 0, 0, 0));
        closeBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        closeBtn.setForeground(new Color(220, 180, 180));
        closeBtn.setBackground(new Color(30, 50, 30));
        closeBtn.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        closeBtn.setFocusPainted(false);
        closeBtn.setToolTipText("Hide action window (reopen from View menu)");
        closeBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        });

        titleBar.add(titleLabel, BorderLayout.WEST);
        titleBar.add(closeBtn, BorderLayout.EAST);
        add(titleBar, BorderLayout.NORTH);

        // The secondary action row, reparented (not copied) so every listener
        // and every refresh() enablement call keeps working unchanged.
        if (actionRow != null) {
            add(actionRow, BorderLayout.CENTER);
        }

        // Window behavior
        setUndecorated(true); // no native title bar; we use our own compact one
        setAlwaysOnTop(true);
        setBackground(new Color(10, 20, 10));
        setResizable(false);
        // B5-4155: the row is the game's action bar, not a helper popup — a
        // stray close must never take it away for the rest of the session.
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        pack();

        // Default position: below the main frame
        updatePosition();

        // Drag to move (on title bar)
        titleBar.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                dragOffsetX = e.getX();
                dragOffsetY = e.getY();
            }
        });
        titleBar.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                Point p = getLocation();
                setLocation(p.x + e.getX() - dragOffsetX, p.y + e.getY() - dragOffsetY);
            }
        });

        // Close on Escape
        getRootPane().registerKeyboardAction(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { setVisible(false); }
        }, KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Listen for main window close to dispose
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                // Do not dispose — just hide; the main window will dispose us
                setVisible(false);
            }
        });
    }

    /**
     * Updates position to sit directly beneath the main frame, so an
     * off-screen frame keeps this dialog off screen with it (a headless
     * test run must never flash a window).
     */
    public void updatePosition() {
        if (mainWindow != null && mainWindow.isVisible()) {
            Rectangle bounds = mainWindow.getBounds();
            setLocation(bounds.x, bounds.y + bounds.height);
        } else {
            // Fallback: screen lower-right
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            setLocation(screen.width - getWidth() - 20, screen.height - getHeight() - 60);
        }
    }

    /** Call when the main window moves/resizes to keep the action window aligned. */
    public void onMainWindowMoved() {
        if (isVisible()) updatePosition();
    }

    /** Shows the action window, creating it if needed (idempotent). */
    public void showWindow() {
        updatePosition();
        setVisible(true);
        toFront(); // ensure on top
    }
}