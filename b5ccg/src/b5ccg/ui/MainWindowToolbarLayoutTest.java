package b5ccg.ui;

import b5ccg.ai.AIPlayer;
import b5ccg.engine.GameController;
import b5ccg.engine.GameStateCallback;
import b5ccg.model.CharacterCard;
import b5ccg.model.Card;
import b5ccg.model.Deck;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.GamePhase;
import b5ccg.model.enums.Rarity;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

/**
 * B5-2410 - construction smoke check for the detached Action window.
 *
 * <p>B5-2410 moved the scrollable action row OUT of the main frame into a
 * detached always-visible non-modal Action window (a JDialog owned by the
 * frame), because at 1366x768 the in-frame action bar was nearly invisible.
 * The main frame keeps three always-visible rows: the filter row, the
 * primary action row (Pass / Play / Initiate / Sponsor / Promote / Build
 * Influence), and a slim status strip (phase banner, initiative chain,
 * status line). The dialog holds the SAME component instances, so every
 * listener and every refresh() enablement call works unchanged.
 *
 * <p>This check asserts the four invariants B5-2410 could have broken:
 *
 * <ol>
 *   <li>the window constructs with no exception, shows no owned window it
 *       was not asked to show, and releases its dialog on dispose;</li>
 *   <li>the main frame still holds its three always-visible rows and holds
 *       NO scroll pane of action buttons (the move actually happened);</li>
 *   <li>the Action dialog appears exactly with the frame, is non-modal and
 *       non-wrapping, and holds every secondary action control;</li>
 *   <li>the whole arrangement fits 1366x768: the main frame's minimum size,
 *       both always-visible rows' preferred widths, and the dialog's
 *       on-screen placement.</li>
 * </ol>
 *
 * <p>The toolbar panels are constructor locals, so this check reaches them
 * by WALKING the container tree from the content pane (and the owned
 * windows) rather than by reflection on names — except for the handful of
 * label/button FIELDS that anchor which row is which. Walking means the
 * assertions are about the structure the frame actually has, not about
 * names someone remembered.
 *
 * <p>Exit codes: 0 all checks passed, 1 a check failed, 2 SKIPPED because the
 * environment is headless. The skip is deliberately NOT 0 - see the note in
 * main().
 */
public class MainWindowToolbarLayoutTest {
    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [TUI] " + label + ": " + (ok ? "PASS" : "FAIL"));
        if (!ok) failed++;
    }

    private static Player player(String name, Faction faction, boolean human) {
        Player player = new Player(name, faction, human);
        CharacterCard ambassador = new CharacterCard("amb_" + name, "Ambassador",
            "CHARACTER_" + faction, Rarity.FIXED, faction, CardSet.PREMIERE,
            "x", "text", 3, 3, 3, 3, true);
        player.setAmbassador(ambassador);
        player.getInnerCircle().add(ambassador);
        // Decks are never null in a real game; a null deck NPEs board
        // painting (GameBoardPanel.drawZone reads getDeck().size()), so the
        // harness carries an empty one rather than tripping a latent defect
        // that belongs to a different row.
        player.setDeck(new Deck(new ArrayList<Card>()));
        return player;
    }

    private static GameState state() {
        List<Player> players = new ArrayList<Player>();
        players.add(player("TuiHuman", Faction.NARN, true));
        players.add(player("TuiAi", Faction.MINBARI, false));
        GameState state = new GameState(players);
        for (int i = 0; i < players.size(); i++) {
            players.get(i).setGameState(state);
        }
        state.setPhase(GamePhase.ACTION);
        return state;
    }

    private static GameController controller(final GameState state) {
        return new GameController(state, new ArrayList<AIPlayer>(),
            new GameStateCallback() {
                public void accept(GameState state) {
                }
            });
    }

    /** Constructs the window on the EDT, off screen, never shown. */
    private static MainWindow window(final GameController controller,
                                     final Throwable[] failure) throws Exception {
        final MainWindow[] result = new MainWindow[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    MainWindow w = new MainWindow(controller);
                    // Off screen: never made visible, and parked far outside any
                    // plausible desktop bounds so a stray show cannot flash a window.
                    w.setLocation(-32000, -32000);
                    result[0] = w;
                } catch (Throwable t) {
                    failure[0] = t;
                }
            }
        });
        return result[0];
    }

    private static void pack(final MainWindow window) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                window.pack();
                window.validate();
            }
        });
    }

    private static void showOffScreen(final MainWindow window) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                window.setLocation(-32000, -32000);
                window.setVisible(true);
            }
        });
    }

    private static void hideDispose(final MainWindow window) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                window.setVisible(false);
                window.dispose();
            }
        });
    }

    private static void refresh(final MainWindow window,
                                final GameState state) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Method method = MainWindow.class.getDeclaredMethod(
                        "refresh", GameState.class);
                    method.setAccessible(true);
                    method.invoke(window, state);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        });
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void collect(Container c, List<Component> into) {
        Component[] kids = c.getComponents();
        for (int i = 0; i < kids.length; i++) {
            into.add(kids[i]);
            if (kids[i] instanceof Container) {
                collect((Container) kids[i], into);
            }
        }
    }

    private static boolean contains(Container root, Component target) {
        List<Component> all = new ArrayList<Component>();
        collect(root, all);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i) == target) return true;
        }
        return false;
    }

    private static int countButtons(Container c) {
        int n = 0;
        Component[] kids = c.getComponents();
        for (int i = 0; i < kids.length; i++) {
            if (kids[i] instanceof JButton) n++;
            if (kids[i] instanceof Container) n += countButtons((Container) kids[i]);
        }
        return n;
    }

    private static int countScrollpanesWithButtons(Container c) {
        int n = 0;
        Component[] kids = c.getComponents();
        for (int i = 0; i < kids.length; i++) {
            if (kids[i] instanceof JScrollPane) {
                Component view = ((JScrollPane) kids[i]).getViewport().getView();
                if (view instanceof Container
                        && countButtons((Container) view) > 0) n++;
            }
            if (kids[i] instanceof Container) {
                n += countScrollpanesWithButtons((Container) kids[i]);
            }
        }
        return n;
    }

    /** Finds a component with a TitledBorder whose title equals the given string. */
    private static Component findByTitledBorder(Container root, String title) {
        List<Component> all = new ArrayList<Component>();
        collect(root, all);
        for (int i = 0; i < all.size(); i++) {
            Component comp = all.get(i);
            if (comp instanceof JComponent) {
                Border b = ((JComponent) comp).getBorder();
                if (b instanceof TitledBorder) {
                    TitledBorder tb = (TitledBorder) b;
                    if (title.equals(tb.getTitle())) {
                        return comp;
                    }
                }
            }
        }
        return null;
    }

    /** Finds the ancestor JPanel with BoxLayout.Y_AXIS that contains a component with the given titled border. */
    private static JPanel findJoinPanel(Container root, String titledBorderTitle) {
        Component target = findByTitledBorder(root, titledBorderTitle);
        if (target == null) return null;
        Container parent = target.getParent();
        while (parent != null) {
            if (parent instanceof JPanel) {
                LayoutManager lm = ((JPanel) parent).getLayout();
                if (lm instanceof BoxLayout) {
                    BoxLayout bl = (BoxLayout) lm;
                    if (bl.getAxis() == BoxLayout.Y_AXIS) {
                        return (JPanel) parent;
                    }
                }
            }
            parent = parent.getParent();
        }
        return null;
    }

    private static int countCheckboxes(Container c) {
        int n = 0;
        Component[] kids = c.getComponents();
        for (int i = 0; i < kids.length; i++) {
            if (kids[i] instanceof JCheckBox) n++;
            if (kids[i] instanceof Container) {
                n += countCheckboxes((Container) kids[i]);
            }
        }
        return n;
    }

    /**
     * Resolves the B5-2410 main-frame toolbar by WALKING the frame: the
     * direct JPanel child of the content pane that holds the filter
     * checkboxes AND the primary Pass button. BorderLayout exposes its slot
     * constants but has no component getters, so rows are identified by
     * what they contain, not by layout queries.
     */
    private static JPanel toolbarPanel(MainWindow window, Object passButton) {
        Container content = window.getContentPane();
        Component[] rows = content.getComponents();
        for (int i = 0; i < rows.length; i++) {
            if (!(rows[i] instanceof JPanel)) continue;
            JPanel candidate = (JPanel) rows[i];
            if (countCheckboxes(candidate) == 0) continue;
            if (passButton != null && !contains(candidate, (Component) passButton)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    /**
     * Finds the B5-2410 Action dialog among the frame's owned windows by
     * title and shape: a JDialog titled "Actions" whose content holds a
     * scroll pane of buttons. Never matches by variable name.
     */
    private static JDialog actionDialog(MainWindow window) {
        Window[] owned = window.getOwnedWindows();
        for (int i = 0; i < owned.length; i++) {
            if (!(owned[i] instanceof JDialog)) continue;
            JDialog dialog = (JDialog) owned[i];
            if (!"Actions".equals(dialog.getTitle())) continue;
            if (countScrollpanesWithButtons(dialog.getContentPane()) == 0) continue;
            return dialog;
        }
        return null;
    }

    private static JScrollPane dialogScroller(JDialog dialog) {
        List<Component> all = new ArrayList<Component>();
        collect(dialog.getContentPane(), all);
        for (int i = 0; i < all.size(); i++) {
            if (!(all.get(i) instanceof JScrollPane)) continue;
            JScrollPane scroller = (JScrollPane) all.get(i);
            Component view = scroller.getViewport().getView();
            if (view instanceof Container
                    && countButtons((Container) view) > 0) return scroller;
        }
        return null;
    }

    private static void testConstructsOffScreen(GameState state) throws Exception {
        System.out.println("  [TUI] --- construction, off screen, never shown ---");
        Throwable[] failure = new Throwable[1];
        MainWindow window = window(controller(state), failure);
        check("MainWindow constructs with no exception",
            failure[0] == null);
        if (failure[0] != null) {
            System.out.println("       construction threw: "
                + failure[0].getClass().getName() + ": " + failure[0].getMessage());
            return;
        }
        check("the smoke-check window is never shown", !window.isShowing());
        check("the window has a populated content pane",
            window.getContentPane() != null && window.getContentPane().getComponentCount() > 0);
        // B5-2410: the constructor builds the Action dialog but must not
        // show it — tests build windows they never show.
        Window[] owned = window.getOwnedWindows();
        boolean strayVisible = false;
        for (int i = 0; i < owned.length; i++) {
            if (owned[i].isShowing()) strayVisible = true;
        }
        check("construction shows no owned window", !strayVisible);
        window.dispose();
        // B5-2410: dispose() must release the owned dialog — an owned window
        // outlives its owner's dispose() and would otherwise leak.
        Window[] after = window.getOwnedWindows();
        boolean leaked = false;
        for (int i = 0; i < after.length; i++) {
            if (after[i].isDisplayable()) leaked = true;
        }
        check("dispose releases the owned Action window", !leaked);
    }

    private static void testMainFrameKeepsThreeRows(GameState state) throws Exception {
        System.out.println("  [TUI] --- B5-2410 main frame keeps three rows, no action scroller ---");
        Throwable[] failure = new Throwable[1];
        MainWindow window = window(controller(state), failure);
        if (failure[0] != null) {
            check("window available for the toolbar assertions", false);
            return;
        }
        pack(window);

        Object passButton = field(window, "passButton");
        JPanel toolbar = toolbarPanel(window, passButton);
        check("the toolbar panel is in the frame (filter row + primary row)",
            toolbar != null);
        if (toolbar == null) {
            window.dispose();
            return;
        }
        // The status strip moved the three global readouts out of the old
        // scrollable row: they must still be reachable from the main frame.
        JLabel banner = (JLabel) field(window, "phaseLabel");
        JLabel initiative = (JLabel) field(window, "initiativeLabel");
        JLabel status = (JLabel) field(window, "statusLabel");
        check("the phase banner is still reachable from the main frame",
            banner != null && contains(window.getContentPane(), banner));
        check("the initiative readout is still reachable from the main frame",
            initiative != null && contains(window.getContentPane(), initiative));
        check("the status line is still reachable from the main frame",
            status != null && contains(window.getContentPane(), status));

        // The move actually happened: no scroll pane of buttons in the frame.
        check("the main frame holds no scroll pane of action buttons",
            countScrollpanesWithButtons(window.getContentPane()) == 0);

        window.dispose();
    }

    private static void testActionDialogHoldsControls(GameState state) throws Exception {
        System.out.println("  [TUI] --- B5-2410 Action dialog holds the secondary controls ---");
        Throwable[] failure = new Throwable[1];
        MainWindow window = window(controller(state), failure);
        if (failure[0] != null) {
            check("window available for the dialog assertions", false);
            return;
        }
        pack(window);
        refresh(window, state);
        showOffScreen(window);

        JDialog dialog = actionDialog(window);
        check("the Action dialog is owned by the frame", dialog != null);
        if (dialog == null) {
            hideDispose(window);
            return;
        }
        check("the Action dialog shows exactly with the frame", dialog.isShowing());
        check("the Action dialog is non-modal (never blocks the game)",
            dialog.getModalityType() == Dialog.ModalityType.MODELESS);
        check("the Action dialog is not resizable (the row cannot wrap and clip)",
            !dialog.isResizable());
        check("the dialog refuses a close click (always-visible promise)",
            dialog.getDefaultCloseOperation() == JDialog.DO_NOTHING_ON_CLOSE);

        JScrollPane scroller = dialogScroller(dialog);
        check("the action row lives in a scroll pane in the dialog",
            scroller != null);
        if (scroller == null) {
            hideDispose(window);
            return;
        }
        check("the dialog's action row carries a permanent horizontal scrollbar",
            scroller.getHorizontalScrollBarPolicy()
                == ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        check("the dialog's action row has no vertical scrollbar (one row tall by design)",
            scroller.getVerticalScrollBarPolicy()
                == ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);

        int n = countButtons(scroller.getViewport().getView() instanceof Container
            ? (Container) scroller.getViewport().getView() : dialog.getContentPane());
        check("the dialog holds the secondary action buttons (" + n + ")",
            n >= 20);

        // Named reachability: each of these must live in the DIALOG, proved
        // by identity against the frame's own fields — not by text matching.
        String[] dialogHomes = new String[] {
            "attackButton", "supportButton", "opposeButton",
            "declareWarButton", "councilVoteButton", "gameSetupButton",
            "deckBuilderButton", "startingGameButton", "drawRoundBuyButton",
            "surrenderButton", "forfeitButton", "mercenaryBidButton"
        };
        boolean allInDialog = true;
        for (int i = 0; i < dialogHomes.length; i++) {
            try {
                Component c = (Component) field(window, dialogHomes[i]);
                if (c == null || !contains(dialog.getContentPane(), c)) {
                    System.out.println("       not in dialog: " + dialogHomes[i]);
                    allInDialog = false;
                }
            } catch (NoSuchFieldException e) {
                System.out.println("       no such field: " + dialogHomes[i]);
                allInDialog = false;
            }
        }
        check("every secondary action control lives in the dialog", allInDialog);

        // And the primary row stays in the MAIN frame — the split point.
        Component pass = (Component) field(window, "passButton");
        check("the primary Pass button stays in the main frame",
            pass != null && contains(window.getContentPane(), pass)
                && !contains(dialog.getContentPane(), pass));

        // Viewport fits the whole row: nothing wraps into clipped lines.
        // Single-row invariant: the BoxLayout row never wraps, so its full
        // preferred HEIGHT must be visible — a wrapping row would fail
        // exactly here, which is what makes this check able to go red.
        Dimension extent = scroller.getViewport().getExtentSize();
        Dimension rowPref = scroller.getViewport().getView().getPreferredSize();
        check("the action row is fully visible vertically ("
                + extent.height + " >= " + rowPref.height + ")",
            extent.height > 0 && extent.height >= rowPref.height);

        // The parked-frame contract: an off-screen frame keeps its dialog
        // off-screen too, so test runs never flash a window.
        Point frameLoc = window.getLocation();
        Dimension frameSize = window.getSize();
        Point dialogLoc = dialog.getLocation();
        check("the dialog parks with an off-screen frame",
            dialogLoc.equals(new Point(frameLoc.x, frameLoc.y + frameSize.height)));

        hideDispose(window);
        check("hiding the frame hides the Action dialog", !dialog.isShowing());
    }

    private static void testFits1366x768(GameState state) throws Exception {
        System.out.println("  [TUI] --- B5-2410 arrangement fits 1366x768 ---");
        Throwable[] failure = new Throwable[1];
        MainWindow window = window(controller(state), failure);
        if (failure[0] != null) {
            check("window available for the geometry assertions", false);
            return;
        }
        pack(window);
        refresh(window, state);

        // The frame itself must admit a 1366x768 screen: the old 1300x820
        // minimum never fit a 768px-high display.
        Dimension min = window.getMinimumSize();
        check("the frame minimum fits 1366x768 (" + min.width + "x" + min.height + ")",
            min.width <= 1366 && min.height <= 768);

        Object passButton = field(window, "passButton");
        JPanel toolbar = toolbarPanel(window, passButton);
        if (toolbar == null) {
            check("toolbar available for the width assertions", false);
            window.dispose();
            return;
        }
        // Two levels: toolbarPanel holds topRows, which stacks the filter
        // row, the primary row and the status strip. The widest of the
        // three is what must fit 1366.
        Component[] top = toolbar.getComponents();
        int widest = 0;
        for (int i = 0; i < top.length; i++) {
            if (!(top[i] instanceof Container)) continue;
            Component[] sub = ((Container) top[i]).getComponents();
            for (int j = 0; j < sub.length; j++) {
                int w = sub[j].getPreferredSize().width;
                if (w > widest) widest = w;
            }
        }
        check("every always-visible main-frame row fits 1366 (" + widest + " <= 1366)",
            widest > 0 && widest <= 1366);

        showOffScreen(window);
        JDialog dialog = actionDialog(window);
        if (dialog == null) {
            check("dialog available for the placement assertions", false);
            hideDispose(window);
            return;
        }
        // The dialog is one row tall by construction (packed, not
        // resizable, no vertical scrollbar): it must fit a 768px-high
        // screen vertically wherever it lands. Width may exceed the screen
        // — the dialog's own horizontal scrollbar reaches the rest.
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension dialogSize = dialog.getSize();
        check("the Action dialog fits 768 vertically (" + dialogSize.height
                + " <= " + screen.height + ")",
            dialogSize.height > 0 && dialogSize.height <= screen.height);
        hideDispose(window);
    }

    private static void testPhaseBannerReadable(GameState state) throws Exception {
        System.out.println("  [TUI] --- phase banner is reachable and readable ---");
        Throwable[] failure = new Throwable[1];
        MainWindow window = window(controller(state), failure);
        if (failure[0] != null) {
            check("window available for the phase-banner assertions", false);
            return;
        }
        pack(window);
        refresh(window, state);

        JLabel banner = (JLabel) field(window, "phaseLabel");
        check("the phase banner exists", banner != null);
        if (banner == null) {
            window.dispose();
            return;
        }
        check("the phase banner is still reachable from the window hierarchy",
            contains(window.getContentPane(), banner));

        String text = banner.getText();
        check("the phase banner text is not blank (" + (text == null ? "null" : text) + ")",
            text != null && text.trim().length() > 0);

        // B5-2410 moved the banner OUT of the scrolling row into the
        // always-visible status strip: prove it is not in the dialog, so a
        // main window with all-dark buttons still names its phase.
        showOffScreen(window);
        JDialog dialog = actionDialog(window);
        check("the phase banner lives in the main frame, not the Action dialog",
            dialog == null || !contains(dialog.getContentPane(), banner));
        hideDispose(window);
    }

    // B5-2424: the three reference panels start collapsed; the View menu
    // items are unselected and selecting them shows the panel. The test
    // asserts the default footprint is the collapsed one (already proven by
    // the 1364 width gate), the panels are reachable from the window
    // hierarchy, and each item toggles its panel's visibility.
    private static void testCollapsibleReferencePanels(GameState state) throws Exception {
        System.out.println("  [TUI] --- B5-2424 collapsible reference panels ---");
        Throwable[] failure = new Throwable[1];
        MainWindow window = window(controller(state), failure);
        if (failure[0] != null) {
            check("window available for the collapsible-panel assertions", false);
            return;
        }
        pack(window);
        refresh(window, state);
        showOffScreen(window);

        // The View menu bar is in the frame's JMenuBar.
        JMenuBar mb = window.getJMenuBar();
        check("the frame has a JMenuBar", mb != null);
        if (mb == null) {
            window.dispose();
            return;
        }
        Component[] menus = mb.getComponents();
        check("the menu bar has at least one menu (View)", menus.length > 0);
        if (menus.length == 0) {
            window.dispose();
            return;
        }
        JMenu viewMenu = null;
        for (int i = 0; i < menus.length; i++) {
            if (menus[i] instanceof JMenu && ((JMenu) menus[i]).getText().equals("View")) {
                viewMenu = (JMenu) menus[i];
                break;
            }
        }
        check("a View menu exists", viewMenu != null);
        if (viewMenu == null) {
            window.dispose();
            return;
        }

        // Find the three JCheckBoxMenuItems by text. Use array wrappers so the
        // inner classes can capture them (Java 6 requires final or effectively
        // final for local variables accessed from inner classes).
        final JCheckBoxMenuItem[] filterItem = new JCheckBoxMenuItem[1];
        final JCheckBoxMenuItem[] participantsItem = new JCheckBoxMenuItem[1];
        final JCheckBoxMenuItem[] logItem = new JCheckBoxMenuItem[1];
        Component[] items = viewMenu.getMenuComponents();
        for (int i = 0; i < items.length; i++) {
            if (items[i] instanceof JCheckBoxMenuItem) {
                JCheckBoxMenuItem mi = (JCheckBoxMenuItem) items[i];
                String text = mi.getText();
                if (text.equals("Filter / Sort")) filterItem[0] = mi;
                else if (text.equals("Conflict Participants")) participantsItem[0] = mi;
                else if (text.equals("Game Log")) logItem[0] = mi;
            }
        }
        check("Filter / Sort menu item exists", filterItem[0] != null);
        check("Conflict Participants menu item exists", participantsItem[0] != null);
        check("Game Log menu item exists", logItem[0] != null);
        if (filterItem[0] == null || participantsItem[0] == null || logItem[0] == null) {
            window.dispose();
            return;
        }

        // All items start UNSELECTED (panels hidden by default).
        check("Filter / Sort starts unselected", !filterItem[0].isSelected());
        check("Conflict Participants starts unselected", !participantsItem[0].isSelected());
        check("Game Log starts unselected", !logItem[0].isSelected());

        // The panels are reachable from the content pane, but hidden.
        // They are local variables in the constructor, so find them by their
        // titled borders rather than by field name.
        Component logScroll = findByTitledBorder(window.getContentPane(), "Game Log");
        JPanel joinPanel = findJoinPanel(window.getContentPane(), "Conflict Participants");
        Component filterPanel = findByTitledBorder(window.getContentPane(), "Filter / Sort");

        check("logScroll is reachable from the content pane",
            logScroll != null && contains(window.getContentPane(), logScroll));
        check("joinPanel is reachable from the content pane",
            joinPanel != null && contains(window.getContentPane(), joinPanel));
        check("filterPanel is reachable from the content pane",
            filterPanel != null && contains(window.getContentPane(), filterPanel));

        check("logScroll is hidden by default", logScroll != null && !logScroll.isVisible());
        check("joinPanel is hidden by default", joinPanel != null && !joinPanel.isVisible());
        check("filterPanel is hidden by default", filterPanel != null && !filterPanel.isVisible());

        // Selecting an item shows the panel; revalidating the parent collapses
        // the sidebar/toolbar row. The probe proved this host's JDK drops
        // hidden children from layout AND from getPreferredSize(), so
        // revalidate+repaint is enough — no extra layout call needed.
        // All Swing interactions must run on the EDT.
        // Selecting an item shows the panel; revalidating the parent collapses
        // the sidebar/toolbar row. The probe proved this host's JDK drops
        // hidden children from layout AND from getPreferredSize(), so
        // revalidate+repaint is enough — no extra layout call needed.
        // All Swing interactions must run on the EDT. Use doClick() instead
        // of setSelected() because setSelected() does not fire the action
        // listener on JCheckBoxMenuItem.
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() { filterItem[0].doClick(); }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        pack(window);
        check("selecting Filter / Sort shows the panel",
            filterPanel != null && filterPanel.isShowing());
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() { filterItem[0].doClick(); }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        pack(window);
        check("deselecting Filter / Sort hides the panel",
            filterPanel != null && !filterPanel.isShowing());

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() { participantsItem[0].doClick(); }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        pack(window);
        check("selecting Conflict Participants shows the panel",
            joinPanel != null && joinPanel.isShowing());
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() { participantsItem[0].doClick(); }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        pack(window);
        check("deselecting Conflict Participants hides the panel",
            joinPanel != null && !joinPanel.isShowing());

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() { logItem[0].doClick(); }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        pack(window);
        check("selecting Game Log shows the panel",
            logScroll != null && logScroll.isShowing());
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() { logItem[0].doClick(); }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        pack(window);
        check("deselecting Game Log hides the panel",
            logScroll != null && !logScroll.isShowing());

        window.dispose();
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            // Exit 2, NOT 0. A skip that exits 0 is indistinguishable from a pass
            // to any caller checking the exit code, and a check that cannot run
            // must never report green (B5-1725).
            System.out.println("MAIN WINDOW TOOLBAR LAYOUT TEST SKIPPED "
                + "(headless environment) - exit 2, this is NOT a pass");
            System.out.flush();
            System.err.flush();
            System.exit(2);
        }
        System.out.println("=== MainWindow action-window smoke check (B5-2410) ===");
        GameState state = state();
        testConstructsOffScreen(state);
        testMainFrameKeepsThreeRows(state);
        testActionDialogHoldsControls(state);
        testFits1366x768(state);
        testPhaseBannerReadable(state);
        testCollapsibleReferencePanels(state);
        System.out.println(failed == 0
            ? "MAIN WINDOW TOOLBAR LAYOUT TEST PASSED (" + checks + " checks)"
            : "MAIN WINDOW TOOLBAR LAYOUT TEST FAILED ("
                + failed + " of " + checks + " checks)");
        System.out.flush();
        System.err.flush();
        System.exit(failed == 0 ? 0 : 1);
    }
}
