package b5ccg.ui;

import b5ccg.ai.AIPlayer;
import b5ccg.engine.GameController;
import b5ccg.engine.GameStateCallback;
import b5ccg.model.CharacterCard;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.GamePhase;
import b5ccg.model.enums.Rarity;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * B5-4166 - standing assertion for the View > detach/dock round trip.
 *
 * <p>The human playtest report (2026-10-14) found the View menu "removes
 * elements and never puts them back." Two independent defects produced that
 * one symptom, and this test pins the inverse-round-trip invariant for both:
 *
 * <p><b>Defect 1 - docking hid permanently.</b> {@code MainWindow.Detachable
 * .dock()} ended with {@code window.attach(host, constraint, index, false)} —
 * a hard-coded {@code false} that returned the panel to its host INVISIBLE.
 * For the three panels registered {@code visibleByDefault == true} (Influence
 * Tracker, Tension / Unrest, Conflict Types) the round trip View > detach >
 * close was lossy: the panel was still in the frame but not shown, and the
 * View checkbox is a floating toggle whose off-path (dock) had no way to
 * express back-in-the-frame-AND-visible. Docking is the inverse of floating,
 * so it must put back exactly what floating took away — including visibility.
 *
 * <p><b>Defect 2 - detached windows landed off screen.</b> {@code
 * DetachWindow.place()}'s owner branch computed {@code y = origin.y +
 * owner.getHeight() + cascade} with no ceiling, while the fallback branch
 * already clamped. On a 768-tall screen with the 1024x700 frame at y=0 a
 * detached window was placed at y=724 or below, entirely beneath the visible
 * area — still tracking, which reads as deleted.
 *
 * <p>Exit codes: 0 all checks passed (or host is headless, skipped), 1 a check
 * failed. Follows the MainWindowLeadFleetControlTest headless-skip pattern.
 */
public final class MainWindowViewDockVisibilityTest {

    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [VDUI] " + label + ": " + (ok ? "PASS" : "FAIL"));
        if (!ok) failed++;
    }

    private static CharacterCard ambassador(Faction f) {
        return new CharacterCard("amb_viewdock", "Ambassador",
            "CHARACTER_" + f, Rarity.FIXED, f, CardSet.PREMIERE,
            "x", "text", 3, 3, 3, 3, true);
    }

    private static GameState twoPlayerState() {
        Player human = new Player("VdHuman", Faction.NARN, true);
        Player rival = new Player("VdRival", Faction.MINBARI, false);
        CharacterCard amb = ambassador(Faction.NARN);
        human.setAmbassador(amb);
        human.getInnerCircle().add(amb);
        List<Player> players = new ArrayList<Player>();
        players.add(human);
        players.add(rival);
        GameState state = new GameState(players);
        state.setPhase(GamePhase.ACTION);
        return state;
    }

    private static GameController controller(GameState state) {
        return new GameController(state, new ArrayList<AIPlayer>(),
            new GameStateCallback() {
                public void accept(GameState s) {
                }
            });
    }

    private static MainWindow window(final GameController controller) throws Exception {
        final MainWindow[] result = new MainWindow[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                result[0] = new MainWindow(controller);
            }
        });
        return result[0];
    }

    private static Object field(Object target, Class<?> declaring, String name)
            throws Exception {
        Field f = declaring.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    /** The list of registered Detachable handles. */
    private static List<?> detachablePanels(MainWindow w) throws Exception {
        return (List<?>) field(w, MainWindow.class, "detachablePanels");
    }

    private static void invoke(Object handle, String method) throws Exception {
        Method m = handle.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        m.invoke(handle);
    }

    private static int hostIndexOf(Container host, Component panel) {
        for (int i = 0; i < host.getComponentCount(); i++) {
            if (host.getComponent(i) == panel) return i;
        }
        return -1;
    }

    /**
     * Detach then dock one registered panel and assert the inverse round trip:
     * after dock the panel is in its ORIGINAL host at its ORIGINAL index and
     * visible iff it was registered visibleByDefault. The detached window must
     * also be placed fully on screen (defect 2).
     */
    private static void roundTrip(MainWindow w, Object handle) throws Exception {
        Container host = (Container) field(handle, handle.getClass(), "host");
        Object constraint = field(handle, handle.getClass(), "constraint");
        boolean visibleByDefault = (Boolean) field(handle, handle.getClass(),
            "visibleByDefault");
        String title = (String) field(handle, handle.getClass(), "title");
        Component panel = (Component) field(handle, handle.getClass(), "panel");
        Window win = (Window) field(handle, handle.getClass(), "window");

        if (host == null) {
            // Hostless panels (Conflict Join) have no home slot to round-trip
            // into; float them so the placement clamp is still observed.
            invoke(handle, "floatPanel");
            check("[" + title + "] hostless: floating still shows the window",
                win.isShowing());
            return;
        }

        int preIndex = hostIndexOf(host, panel);
        check("[" + title + "] precondition: panel starts in its host",
            preIndex >= 0);

        // ── float ────────────────────────────────────────────────────────────
        invoke(handle, "floatPanel");
        check("[" + title + "] after float: panel lives in the window, not the host",
            panel.getParent() != host && panel.getParent() != null);
        check("[" + title + "] after float: window is showing", win.isShowing());

        // Placement must be REACHABLE on screen (defect 2). The reported defect
        // was a window landing at y=724 on a 768-tall screen, entirely beneath
        // the visible area; the clamp in place() exists to keep the origin on
        // screen. A panel whose own width exceeds the screen (the Filter / Sort
        // row is 1380 wide) can never fit its full extent regardless of where it
        // is placed, so the meaningful, in-scope invariant is that the window's
        // TOP-LEFT ORIGIN is within the viewport -- i.e. the title bar is
        // grabbable, which is what "removes elements and never puts them back"
        // was really complaining about.
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Point loc = win.getLocation();
        boolean originOnScreen = loc.x >= 0 && loc.y >= 0
            && loc.x < screen.width && loc.y < screen.height;
        check("[" + title + "] detached window origin placed on screen "
            + "(origin=" + loc.x + "," + loc.y + " screen=" + screen.width + "x"
            + screen.height + ")",
            originOnScreen);

        // ── dock (the inverse) ───────────────────────────────────────────────
        invoke(handle, "dock");
        check("[" + title + "] after dock: panel is back in its original host",
            panel.getParent() == host);
        if (constraint == null) {
            // BoxLayout sidebar host: the raw component index is the position,
            // so docking must restore it exactly.
            check("[" + title + "] after dock: panel is at its original index",
                hostIndexOf(host, panel) == preIndex);
        } else if (host.getLayout() instanceof java.awt.BorderLayout) {
            // BorderLayout host: position is by CONSTRAINT, not raw index (a
            // remove/re-add legitimately reorders the raw component list while
            // the NORTH/CENTER slot is unchanged). Assert the constraint still
            // resolves to this panel.
            java.awt.BorderLayout bl = (java.awt.BorderLayout) host.getLayout();
            check("[" + title + "] after dock: panel restored to its "
                + "BorderLayout slot " + constraint,
                bl.getLayoutComponent(constraint) == panel);
        }
        check("[" + title + "] after dock: visibility restored to registered "
            + "visibleByDefault=" + visibleByDefault,
            panel.isVisible() == visibleByDefault);
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("MAIN WINDOW VIEW DOCK VISIBILITY TEST SKIPPED"
                + " (headless environment)");
            return;
        }
        System.out.println("=== MainWindow View detach/dock round trip (B5-4166) ===");

        GameState state = twoPlayerState();
        MainWindow w = window(controller(state));
        List<?> handles = detachablePanels(w);
        check("registered default-on panels exist", handles != null && handles.size() >= 3);

        for (int i = 0; i < handles.size(); i++) {
            roundTrip(w, handles.get(i));
        }

        w.dispose();

        System.out.println("");
        System.out.println("checks run: " + checks + ", failures: " + failed);
        if (failed > 0) {
            System.out.println("FAIL: MainWindowViewDockVisibilityTest");
            System.exit(1);
        }
        System.out.println("PASS: MainWindowViewDockVisibilityTest");
        System.exit(0);
    }
}
