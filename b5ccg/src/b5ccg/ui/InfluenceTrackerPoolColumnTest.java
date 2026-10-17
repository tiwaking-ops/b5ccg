package b5ccg.ui;

import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.Race;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * B5-2486: the Influence Tracker must show this round's SPENDABLE POOL, not
 * only the Influence Rating.
 *
 * <p>The defect this guards is a readouts-that-look-live-while-missing-a-number
 * one. Sponsor, promote and buy-card all debit
 * {@link Player#getAppliedPool()} and never touch the rating, so before this
 * column a player could spend and watch the Influence figure sit unchanged.
 *
 * <p><b>Headless-safe by construction, not by a skip guard.</b> The panel
 * extends {@code JPanel}, which needs no display, so this constructs it
 * directly and reads the real {@code JLabel} tree -- there is no
 * {@code GraphicsEnvironment.isHeadless()} early exit that could turn this
 * into a green line that asserts nothing. The labels are located by walking the
 * container hierarchy rather than by a test-only accessor on the panel, so no
 * production method exists only for this test.
 *
 * <p>Exit contract 0/1. Run directly (it is not in the {@code compile.sh} gate
 * list, which is B5-0956's and lives in {@code engine/}):
 *
 * <pre>java -cp out b5ccg.ui.InfluenceTrackerPoolColumnTest</pre>
 */
public final class InfluenceTrackerPoolColumnTest {

    /**
     * Labels per player row, in the order {@code newRow()} adds them. Kept as a
     * named constant so the test fails loudly if a column is inserted rather
     * than reading plausible text out of a shifted index.
     */
    private static final int LABELS_PER_ROW = 6;

    private static int failures = 0;
    private static int checks = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("B5-2486: Influence Tracker spendable-pool column (ui)");

        // InfluenceTrackerPanel.update() defers to the EDT when it is called off
        // it (SwingUtilities.invokeLater), so reading the tree straight after the
        // call sees the PRE-update state and every assertion fails on an empty
        // panel. The panel is right to do that — mutating Swing off the EDT is the
        // bug it is avoiding — so the test joins the EDT rather than reaching past
        // the deferral. invokeAndWait, not invokeLater: the checks must not race
        // the repaint.
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                runChecks();
            }
        });

        System.out.println("");
        System.out.println("checks run: " + checks + ", failures: " + failures);
        if (failures > 0) {
            System.out.println("FAIL: InfluenceTrackerPoolColumnTest");
            System.exit(1);
        }
        System.out.println("PASS: InfluenceTrackerPoolColumnTest");
        System.exit(0);
    }

    private static void runChecks() {

        // ── 1. the header names the column, so the number is readable ──────────
        InfluenceTrackerPanel panel = new InfluenceTrackerPanel();
        Player a = player("Ivanova", Race.HUMAN);
        Player b = player("Londo", Race.NARN);
        GameState state = state(a, b);

        panel.update(state);
        List<JLabel> labels = labelsOf(panel);

        check("heading label present", !labels.isEmpty());
        String heading = labels.get(0).getText();
        check("header still names Influence", heading.indexOf("Influence") >= 0);
        check("header still names Power", heading.indexOf("Power") >= 0);
        check("header now names the Pool column", heading.indexOf("Pool") >= 0);
        check("header still names the victory threshold",
                heading.indexOf("/ " + InfluenceTrackerPanel.STANDARD_VICTORY_THRESHOLD) >= 0);

        // ── 2. the pool is read, and is a DIFFERENT number from the rating ──────
        // Rating 12, pool deliberately 5: if the panel printed the rating twice
        // the happy path would look correct.
        withInfluence(a, 12);
        drainPool(a, 7); // 12 rating, 5 spendable
        withInfluence(b, 8);

        panel.update(state);
        labels = labelsOf(panel);

        check("six labels per player row", (labels.size() - 1) == 2 * LABELS_PER_ROW);

        // Row layout after the heading: icon, name, influence, power, pool, bar.
        int rowA = 1;
        int rowB = 1 + LABELS_PER_ROW;

        check("row A icon", "H".equals(labels.get(rowA + 0).getText()));
        check("row A name carries the faction",
                labels.get(rowA + 1).getText().indexOf("HUMAN") >= 0);
        check("row A influence is the rating",
                "12".equals(labels.get(rowA + 2).getText()));
        check("row A power is the rating with no bonus, so equal to it",
                "12".equals(labels.get(rowA + 3).getText()));
        check("row A pool is the SPENDABLE pool, not the rating",
                "5".equals(labels.get(rowA + 4).getText()));
        check("row A bar is drawn from power",
                InfluenceTrackerPanel.bar(12).equals(labels.get(rowA + 5).getText()));

        check("row B pool equals its own rating when nothing was spent",
                "8".equals(labels.get(rowB + 4).getText()));

        // ── 3. THE NEGATIVE THAT MATTERS: spending must move the pool column ────
        // A panel that recomputed the pool from the rating would pass every
        // check above. Only a live re-read fails here.
        check("precondition: pool below rating", a.getAppliedPool() < a.getInfluence());

        check("applying 3 to the pool lowers the pool and leaves the rating",
                a.applyInfluence(3) && a.getAppliedPool() == 2 && a.getInfluence() == 12);
        panel.update(state);
        labels = labelsOf(panel);
        check("panel shows the reduced pool", "2".equals(labels.get(rowA + 4).getText()));
        check("panel still shows the unchanged rating",
                "12".equals(labels.get(rowA + 2).getText()));

        check("over-spending is refused by the model",
                !a.applyInfluence(99) && a.getAppliedPool() == 2);
        panel.update(state);
        labels = labelsOf(panel);
        check("a refused spend leaves the pool column alone",
                "2".equals(labels.get(rowA + 4).getText()));

        // ── 4. the round boundary re-primes the pool from the rating ────────────
        // Player.restoreAppliedPool() is what the engine calls at the round
        // start. Without this the column would read as a permanent haircut.
        a.restoreAppliedPool();
        panel.update(state);
        labels = labelsOf(panel);
        check("round boundary re-primes the pool to the rating",
                a.getAppliedPool() == a.getInfluence()
                        && String.valueOf(a.getAppliedPool())
                                .equals(labels.get(rowA + 4).getText()));

        // ── 5. row pool reuse: shrinking the roster must not leave a stale row ──
        // The panel pools Row objects; a shrinking player list must remove the
        // surplus labels or the extra pool column lingers with old numbers.
        GameState onePlayer = state(a);
        panel.update(onePlayer);
        labels = labelsOf(panel);
        check("shrinking to one player leaves exactly one row",
                (labels.size() - 1) == LABELS_PER_ROW);
        check("the surviving row still shows a pool", !"".equals(labels.get(1 + 4).getText()));

        GameState threePlayers = state(a, b, player("Kosh", Race.VORLON));
        panel.update(threePlayers);
        labels = labelsOf(panel);
        check("growing to three players adds a whole row of labels",
                (labels.size() - 1) == 3 * LABELS_PER_ROW);
        check("the new row's pool is populated",
                !"".equals(labels.get(1 + 2 * LABELS_PER_ROW + 4).getText()));

        // ── 6. the null-state path still holds ──────────────────────────────────
        panel.update((GameState) null);
        labels = labelsOf(panel);
        check("null state resets the heading", "No game state.".equals(labels.get(0).getText()));
    }

    private static void check(String label, boolean ok) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL " + label);
        } else {
            System.out.println("  ok   " + label);
        }
    }

    /** Every {@code JLabel} under {@code c}, in container-add order. */
    private static List<JLabel> labelsOf(Container c) {
        List<JLabel> out = new ArrayList<JLabel>();
        collectLabels(c, out);
        return out;
    }

    private static void collectLabels(Container c, List<JLabel> out) {
        for (int i = 0; i < c.getComponentCount(); i++) {
            Component child = c.getComponent(i);
            if (child instanceof JLabel) {
                out.add((JLabel) child);
            } else if (child instanceof Container) {
                collectLabels((Container) child, out);
            }
        }
    }

    private static Player player(String name, Race faction) {
        return new Player(name, faction, false);
    }

    /** A Player starts at 4 Influence (the ambassador), not 0. */
    private static void withInfluence(Player p, int n) {
        p.gainInfluence(n - 4);
    }

    /**
     * Spends down the per-round pool without touching the rating, which is
     * exactly what {@code applyInfluence} does — the whole point of the
     * distinction this test guards.
     */
    private static void drainPool(Player p, int spend) {
        p.applyInfluence(spend);
    }

    private static GameState state(Player... players) {
        return new GameState(new ArrayList<Player>(java.util.Arrays.asList(players)));
    }
}