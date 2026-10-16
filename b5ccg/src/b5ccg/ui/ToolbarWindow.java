package b5ccg.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * B5-3657: Detached toolbar window for filter/sort row and secondary action buttons.
 * At 1366x768 the main toolbar's filter/sort row and secondary buttons are hard to
 * reach or collapse vertically. This window holds both in a compact floating panel
 * that stays on top and never scrolls away.
 *
 * The window is modeless, always-on-top, and positioned in the lower-right corner
 * by default. It can be dragged and will remember its position during the session.
 * The components are the SAME instances used in the main toolbar so their state
 * stays perfectly in sync — no duplicate logic.
 */
public class ToolbarWindow extends JDialog {

    private final MainWindow mainWindow;
    private final JPanel filterPanel;
    private final JPanel buttonPanel;
    private final JScrollPane buttonScroller;

    // Drag support
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public ToolbarWindow(MainWindow owner,
                         JPanel filterPanel,
                         JPanel buttonPanel,
                         JScrollPane buttonScroller) {
        super(owner, "Toolbar", false); // modeless
        this.mainWindow = owner;
        this.filterPanel = filterPanel;
        this.buttonPanel = buttonPanel;
        this.buttonScroller = buttonScroller;

        setLayout(new BorderLayout(4, 4));
        getContentPane().setBackground(new Color(10, 20, 10));

        // Title bar with close button (compact)
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(30, 50, 30));
        titleBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        JLabel titleLabel = new JLabel("Filter & Secondary Actions");
        titleLabel.setForeground(new Color(180, 220, 180));
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 10));

        JButton closeBtn = new JButton("×");
        closeBtn.setMargin(new Insets(0, 0, 0, 0));
        closeBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        closeBtn.setForeground(new Color(220, 180, 180));
        closeBtn.setBackground(new Color(30, 50, 30));
        closeBtn.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        closeBtn.setFocusPainted(false);
        closeBtn.setToolTipText("Hide toolbar window (reopen from View menu)");
        closeBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        });

        titleBar.add(titleLabel, BorderLayout.WEST);
        titleBar.add(closeBtn, BorderLayout.EAST);
        add(titleBar, BorderLayout.NORTH);

        // Content: filter panel on top, button scroller below
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(new Color(10, 20, 10));

        // Add filter panel
        contentPanel.add(filterPanel);

        // Add button scroller - make it take remaining space
        contentPanel.add(buttonScroller);

        add(contentPanel, BorderLayout.CENTER);

        // Window behavior
        setUndecorated(true); // no native title bar; we use our own compact one
        setAlwaysOnTop(true);
        setBackground(new Color(10, 20, 10));
        setResizable(false);
        pack();

        // Default position: lower-right corner of the main window
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

    /** Updates position to lower-right of main window (or screen if main not visible). */
    public void updatePosition() {
        if (mainWindow != null && mainWindow.isVisible()) {
            Rectangle bounds = mainWindow.getBounds();
            int x = bounds.x + bounds.width - getWidth() - 12;
            int y = bounds.y + bounds.height - getHeight() - 40; // above taskbar-ish
            setLocation(x, y);
        } else {
            // Fallback: screen lower-right
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            setLocation(screen.width - getWidth() - 20, screen.height - getHeight() - 60);
        }
    }

    /** Call when the main window moves/resizes to keep the toolbar window aligned. */
    public void onMainWindowMoved() {
        if (isVisible()) updatePosition();
    }

    /** Shows the toolbar window, creating it if needed (idempotent). */
    public void showWindow() {
        updatePosition();
        setVisible(true);
        toFront(); // ensure on top
    }
}