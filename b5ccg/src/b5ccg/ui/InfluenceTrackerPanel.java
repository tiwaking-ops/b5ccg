package b5ccg.ui;

import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.Faction;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * B5-1969 -- real-time influence tracker for the right sidebar.
 *
 * READOUT ONLY. This panel never re-derives a rule: it renders
 * {@link Player#getInfluence()}, {@link Player#getPower()} and the
 * Standard-Victory threshold straight off the model, so the engine stays the
 * sole authority on who has actually won. In particular it does NOT decide
 * victory: the Standard path (RulesEngine:799-803) also requires strictly
 * leading every other player, which this panel deliberately does not attempt
 * to mirror. The threshold shown is the 20-Power floor of that path, not a
 * promise that crossing it wins.
 *
 * The threshold is mirrored here as a constant rather than read from the
 * engine because RulesEngine.standardVictory() is private and the row's scope
 * is ui/ only. If the engine constant ever moves, this copy is the thing that
 * will be stale -- 20 is the rulebook value recorded at RulesEngine:801.
 */
public class InfluenceTrackerPanel extends JPanel {

    /** Rulebook Standard-Victory power floor (RulesEngine:801). */
    public static final int STANDARD_VICTORY_THRESHOLD = 20;

    private static final int MAX_BAR_UNITS = 10;

    private final JLabel headingLabel;
    private final JPanel rows;
    private final java.util.List<Row> rowPool = new java.util.ArrayList<Row>();

    private static final class Row {
        JLabel icon;
        JLabel name;
        JLabel influence;
        JLabel power;
        JLabel bar;
    }

    public InfluenceTrackerPanel() {
        setLayout(new GridBagLayout());
        setBackground(new Color(10, 20, 10));
        setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(80, 120, 80)),
            "Influence Tracker  (Standard Victory at "
                + STANDARD_VICTORY_THRESHOLD + " Power)",
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
        c.gridwidth = 5;
        add(headingLabel, c);

        rows = new JPanel(new GridBagLayout());
        rows.setBackground(new Color(10, 20, 10));
        c.gridy = 1;
        add(rows, c);
    }

    /**
     * B5-1969 -- re-read the model and repaint. Called from MainWindow.refresh()
     * on the EDT like every other panel there, but kept safe when called off
     * the EDT (it defers to the EDT rather than mutating Swing state from a
     * worker thread).
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
        java.util.List<Player> players = state.getPlayers();
        headingLabel.setText("Faction        Influence (Rating)  Power   Pool (This round)   Power / " + STANDARD_VICTORY_THRESHOLD);

        while (rowPool.size() < players.size()) {
            rowPool.add(newRow());
        }
        for (int i = rowPool.size() - 1; i >= players.size(); i--) {
            Row dead = rowPool.remove(i);
            rows.remove(dead.icon);
            rows.remove(dead.name);
            rows.remove(dead.influence);
            rows.remove(dead.power);
            rows.remove(dead.bar);
        }

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            Row r = rowPool.get(i);
            r.icon.setText(factionIcon(p.getFaction()));
            r.name.setText(p.getName() + " (" + p.getFaction() + ")");
            r.influence.setText(String.valueOf(p.getInfluence()));
            r.power.setText(String.valueOf(p.getPower()));
            r.bar.setText(bar(p.getPower()));
        }
    }

    private Row newRow() {
        Row r = new Row();
        r.icon = mkLabel(10, Font.BOLD, new Color(220, 180, 100));
        r.name = mkLabel(9, Font.PLAIN, new Color(180, 200, 180));
        r.influence = mkLabel(10, Font.BOLD, new Color(200, 220, 200));
        r.power = mkLabel(10, Font.PLAIN, new Color(180, 200, 220));
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
        rows.add(r.influence, c);
        c.gridx = 3;
        rows.add(r.power, c);
        c.gridx = 4;
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

    /**
     * B5-1969 -- the threshold marker. Pure text arithmetic on the Power total
     * already read off the model; clamps above the threshold so a 40-Power
     * board cannot run the bar off the panel.
     */
    static String bar(int power) {
        int units = power;
        if (units < 0) units = 0;
        if (units > MAX_BAR_UNITS) units = MAX_BAR_UNITS;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MAX_BAR_UNITS; i++) {
            sb.append(i < units ? '#' : '.');
        }
        if (power >= STANDARD_VICTORY_THRESHOLD) {
            sb.append("*");
        }
        return sb.toString();
    }

    /**
     * B5-1969 -- a short per-faction sigil. The build is stdlib-only and
     * Java 6, so no image assets are loaded; these are text glyphs on the
     * panel's own palette.
     */
    static String factionIcon(Faction f) {
        if (f == null) return "?";
        if (f == Faction.HUMAN)       return "H";
        if (f == Faction.MINBARI)     return "M";
        if (f == Faction.CENTAURI)    return "C";
        if (f == Faction.NARN)        return "N";
        if (f == Faction.VORLON)      return "V";
        if (f == Faction.NON_ALIGNED) return "-";
        if (f == Faction.NEUTRAL)     return "0";
        return "?";
    }
}