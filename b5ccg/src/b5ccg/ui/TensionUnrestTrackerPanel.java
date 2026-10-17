package b5ccg.ui;

import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.Race;
import b5ccg.model.enums.TensionMatrix;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * B5-2277 -- per-race tension plus per-faction unrest readout for the right
 * sidebar, sitting under the B5-1969 influence tracker.
 *
 * <h3>READOUT ONLY, and deliberately not a second authority</h3>
 * This panel renders three things and derives nothing:
 *
 * <ul>
 *   <li>the pairwise race tension already in {@link TensionMatrix}, read
 *       through {@link GameState#getTensionMatrix()} -- the model/enums class
 *       that has owned tension since B5-0358;</li>
 *   <li>the at-war fact from the same matrix, so the war marker can never
 *       disagree with {@code GameState.isAtWar};</li>
 *   <li>each seat's unrest from {@link Player#getUnrest()}, which the model
 *       already clamps to the rulebook 1..5 band (:280).</li>
 * </ul>
 *
 * <p>It raises nothing. There is no button here and no call to
 * {@code raiseTension} / {@code raiseUnrest}: a display that can change the
 * thing it displays is a second authority, and the rulebook tension and
 * unrest paths (:274, :278-:280, :992-:1006) stay where they are.
 *
 * <h3>Opening values: shown, but labelled as openings</h3>
 * A fresh {@link TensionMatrix} has no entry for a pair, and
 * {@code getTension} answers 0 for a pair it has never heard of -- which is
 * indistinguishable from a pair the engine deliberately wound back to 0. This
 * panel therefore distinguishes the two by consulting
 * {@link GameState#getTensionMatrix()}{@code .getTensionMap()} for the pair's
 * presence: absent means the engine has never recorded a value, so the row
 * renders the rulebook :274 opening transcribed by
 * {@link MainWindow#initialTension} (Narn/Centauri 4, Human/Centauri 1,
 * Human/Minbari 3, every other pair 2) and marks it "(open)". Present means the
 * engine owns the number and this panel renders it as-is, including 0.
 *
 * <p>That opening table is a transcription, not a source of truth: it was
 * transcribed once for the B5-2008 setup dialog and is reused here rather than
 * copied a second time, so the two readouts cannot drift apart. If the rulebook
 * value moves, {@code MainWindow.initialTension} is the single place to move.
 *
 * <h3>Unrest</h3>
 * Unrest starts at 1 for every faction, at 2 for Non-Aligned (:278, :888), and
 * is clamped 1..5 by the model itself. This panel re-clamps on display only,
 * as a belt-and-braces guard against a bar running off its own track, and does
 * not touch the value.
 */
public class TensionUnrestTrackerPanel extends JPanel {

    /** Rulebook :280 -- unrest band, mirrored for the display clamp only. */
    public static final int UNREST_MIN = 1;
    public static final int UNREST_MAX = 5;

    /** Rulebook :274 -- tension band, mirrored for the display clamp only. */
    public static final int TENSION_MAX = 5;

    private static final int BAR_UNITS = 5;

    private final JLabel headingLabel;
    private final JPanel rows;
    private final List<Row> rowPool = new ArrayList<Row>();

    private static final class Row {
        JLabel icon;
        JLabel name;
        JLabel value;
        JLabel bar;
    }

    public TensionUnrestTrackerPanel() {
        setLayout(new GridBagLayout());
        setBackground(new Color(10, 20, 10));
        setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(80, 120, 80)),
            "Tension (1-" + TENSION_MAX + ")  and  Unrest (" + UNREST_MIN + "-"
                + UNREST_MAX + ")",
            0, 0, new Font("SansSerif", Font.BOLD, 10), new Color(180, 200, 180)));
        setAlignmentX(LEFT_ALIGNMENT);
        setMaximumSize(new Dimension(800, 200));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(1, 6, 1, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        headingLabel = new JLabel("No game state.");
        headingLabel.setForeground(new Color(180, 200, 180));
        headingLabel.setFont(new Font("Monospaced", Font.PLAIN, 9));
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 4;
        add(headingLabel, c);

        rows = new JPanel(new GridBagLayout());
        rows.setBackground(new Color(10, 20, 10));
        c.gridy = 1;
        add(rows, c);
    }

    /**
     * B5-2277 -- re-read the model and repaint. Called from MainWindow.refresh()
     * on the EDT like every other readout, and safe off the EDT (it defers
     * rather than mutating Swing state from a worker thread).
     */
    public void update(final GameState state) {
        if (SwingUtilities.isEventDispatchThread()) {
            applyUpdate(state);
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    applyUpdate(state);
                }
            });
        }
    }

    private void applyUpdate(GameState state) {
        if (state == null) {
            headingLabel.setText("No game state.");
            return;
        }

        // Build the render list first: seats first (unrest), then one row per
        // unordered pair of the races actually at the table.
        List<Race> roster = raceRoster(state);
        int needed = state.getPlayers().size()
            + pairCount(roster);

        while (rowPool.size() < needed) {
            rowPool.add(newRow());
        }
        for (int i = rowPool.size() - 1; i >= needed; i--) {
            Row dead = rowPool.remove(i);
            rows.remove(dead.icon);
            rows.remove(dead.name);
            rows.remove(dead.value);
            rows.remove(dead.bar);
        }

        headingLabel.setText("Seat            Unrest  " + UNREST_MIN + "-" + UNREST_MAX
            + "      Race pair    Tension" + BAR_UNITS + "  State");

        int slot = 0;
        List<Player> players = state.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            Row r = rowPool.get(slot++);
            r.icon.setText(InfluenceTrackerPanel.factionIcon(p.getRace()));
            r.name.setText(p.getName() + " " + shortName(p.getRace()));
            r.value.setText(pad(clamp(p.getUnrest(), UNREST_MIN, UNREST_MAX)));
            r.bar.setText(bar(p.getUnrest(), UNREST_MIN, UNREST_MAX));
        }

        TensionMatrix matrix = state.getTensionMatrix();
        for (int i = 0; i < roster.size(); i++) {
            for (int j = i + 1; j < roster.size(); j++) {
                Race a = roster.get(i);
                Race b = roster.get(j);
                boolean atWar = state.isAtWar(a, b);
                boolean recorded = isRecorded(matrix, a, b);
                int shown = recorded ? matrix.getTension(a, b)
                                    : MainWindow.initialTension(a, b);
                Row r = rowPool.get(slot++);
                r.icon.setText(atWar ? "!" : " ");
                r.name.setText(shortName(a) + " / " + shortName(b));
                r.value.setText(recorded ? String.valueOf(clamp(shown, 0, TENSION_MAX))
                                        : pad(shown) + " (open)");
                r.bar.setText(bar(shown, 0, TENSION_MAX));
            }
        }
    }

    /**
     * The distinct races seated in this game, in table order. Only races with a
     * seat get a tension row: an unseatable race has no pair the engine could
     * hold tension for, so a row for it would be pure decoration.
     */
    static List<Race> raceRoster(GameState state) {
        Set<Race> seen = new LinkedHashSet<Race>();
        List<Player> players = state.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            Race f = players.get(i).getRace();
            if (f != null) seen.add(f);
        }
        return new ArrayList<Race>(seen);
    }

    private static int pairCount(List<Race> roster) {
        int n = roster.size();
        return n * (n - 1) / 2;
    }

    /**
     * True when the matrix has ever recorded a value for the pair in either
     * direction. Directional entries are raised independently, so a pair is
     * "recorded" if either direction has a row -- an engine that recorded 0 for
     * one direction and nothing for the other is still an engine statement, not
     * an unstarted pair.
     */
    static boolean isRecorded(TensionMatrix m, Race a, Race b) {
        if (m == null || a == null || b == null || a == b) return false;
        java.util.Map<Race, java.util.Map<Race, Integer>> map = m.getTensionMap();
        return map.containsKey(a) && map.get(a).containsKey(b)
            || map.containsKey(b) && map.get(b).containsKey(a);
    }

    /** B5-2277 -- the tension / unrest bar, clamped to its own band. */
    static String bar(int value, int lo, int hi) {
        int span = hi - lo + 1;
        int units = value - lo;
        if (units < 0) units = 0;
        if (units > span) units = span;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < span; i++) {
            sb.append(i < units ? '#' : '.');
        }
        return sb.toString();
    }

    static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private static String pad(int v) {
        return v < 10 ? " " + v : String.valueOf(v);
    }

    private Row newRow() {
        Row r = new Row();
        r.icon = mkLabel(10, Font.BOLD, new Color(220, 180, 100));
        r.name = mkLabel(9, Font.PLAIN, new Color(180, 200, 180));
        r.value = mkLabel(9, Font.PLAIN, new Color(200, 220, 200));
        r.bar = mkLabel(9, Font.PLAIN, new Color(140, 170, 140));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 2, 0, 2);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.gridy = rowPool.size();
        c.gridx = 0;
        rows.add(r.icon, c);
        c.gridx = 1;
        rows.add(r.name, c);
        c.gridx = 2;
        rows.add(r.value, c);
        c.gridx = 3;
        rows.add(r.bar, c);
        return r;
    }

    private JLabel mkLabel(int size, int style, Color fg) {
        JLabel l = new JLabel("-");
        l.setForeground(fg);
        l.setFont(new Font("Monospaced", style, size));
        l.setPreferredSize(new Dimension(150, 14));
        return l;
    }

    /** A three-letter race tag; stdlib-only, so no name table is loaded. */
    static String shortName(Race f) {
        if (f == null) return "???";
        String s = f.name();
        return s.length() <= 3 ? s : s.substring(0, 3);
    }
}