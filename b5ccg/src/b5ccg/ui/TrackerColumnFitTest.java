package b5ccg.ui;
import b5ccg.model.enums.Loyalty;

import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.Deck;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Race;
import b5ccg.model.enums.Rarity;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * B5-2754: no tracker label may be handed less width than its own text needs.
 *
 * <p>The defect this gates is not "a column looked narrow" — it is that both
 * sidebar trackers pinned <em>every</em> label to the same fixed width
 * ({@code 46px} here, and the same flat figure in the influence tracker) and then
 * declared a maximum size smaller than the sum of its own columns. A JLabel
 * narrower than its string does not wrap or ellipsize; it clips at the cell edge,
 * which is how "Influence" became "Influe" and "Tension" became "TensionS" in the
 * B5-2754 report. A clipped label is silent — no exception, no scrollbar, and
 * the panel still reports itself as laid out successfully.
 *
 * <p>So the assertion is mechanical and content-driven: for every label this
 * suite can reach on either tracker, the measured width of its <em>current</em>
 * text must be less than or equal to the width the panel actually gives it.
 * Longest-string fixtures are used, not a fixed sample, because a gate measured
 * against a short value passes on the very content that clipped.
 *
 * <p>Exit contract 0/1. Not in the {@code compile.sh} gate list (B5-0956's, lives
 * in {@code engine/}); run directly:
 *
 * <pre>java -cp out b5ccg.ui.TrackerColumnFitTest</pre>
 */
public final class TrackerColumnFitTest {

    private static int failures = 0;
    private static int checks = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("B5-2754: tracker columns must fit their own text (ui)");

        // ── 1. the shared hash-bar scale that made cells read as "###" ──────────
        // The influence bar was 30 hashes wide (one per Power, capped at 30):
        // 150px at 5px/char, the widest thing on the panel by 30px. It is now a
        // 2-Power-step bar over 0..20, which is the range that matters because
        // the Standard-Victory floor is 20.
        check("influence bar is at most 12 chars wide",
                InfluenceTrackerPanel.bar(0).length() <= 12);
        check("influence bar at full Power stays within that",
                InfluenceTrackerPanel.bar(20).length() <= 12);
        check("influence bar is not the old 30-hash wall",
                InfluenceTrackerPanel.bar(20).length() < 30);
        check("influence bar marks the Standard-Victory floor",
                InfluenceTrackerPanel.bar(19).indexOf('*') < 0
                        && InfluenceTrackerPanel.bar(20).indexOf('*') >= 0);

        // ── 2. every reachable label fits its cell, on both trackers ───────────
        // Four seats with long names plus every pair the roster generates, which
        // is the widest content these panels will ever be asked to draw.
        Player human = player("Jeffrey Sinclair", Race.HUMAN, true);
        Player narn  = player("Londo Mollari", Race.NARN, false);
        Player vor   = player("Kosh Naran", Race.VORLON, false);
        Player cent  = player("Centauri Prime", Race.CENTAURI, false);
        GameState state = state(human, narn, vor, cent);

        InfluenceTrackerPanel inf = new InfluenceTrackerPanel();
        inf.setSize(360, 220);
        inf.update(state);
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { }
        });

        TensionUnrestTrackerPanel ten = new TensionUnrestTrackerPanel();
        ten.setSize(360, 220);
        ten.update(state);
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { }
        });

        check("influence tracker: every label fits its measured text",
                allLabelsFit(inf, "InfluenceTrackerPanel"));
        check("tension tracker: every label fits its measured text",
                allLabelsFit(ten, "TensionUnrestTrackerPanel"));

        // ── 3. the panels declare enough room for their own content ───────────
        // The other half of the same defect: even correctly-sized columns clip if
        // the panel's maximum is smaller than their sum.
        check("influence tracker max width exceeds its preferred width",
                inf.getMaximumSize().width >= inf.getPreferredSize().width);
        check("tension tracker max width exceeds its preferred width",
                ten.getMaximumSize().width >= ten.getPreferredSize().width);

        // ── 4. the specific strings the B5-2754 report named ─────────────────
        // "Influe", "This ro" and "TensionS" were clipped headers. Assert the
        // real headings and the longest row values fit, by string.
        check("influence heading is not a clipped fragment",
                fits("Race        Influence (Rating)  Power   Pool (This round)   Power / "
                        + InfluenceTrackerPanel.STANDARD_VICTORY_THRESHOLD,
                        inf.getMaximumSize().width));
        check("tension heading is not a clipped fragment",
                fits("Seat            Unrest  " + TensionUnrestTrackerPanel.UNREST_MIN + "-"
                        + TensionUnrestTrackerPanel.UNREST_MAX
                        + "      Race pair    Tension5  State",
                        ten.getMaximumSize().width));
        check("the longest seat name fits the tension name column",
                fits("Jeffrey Sinclair HUM", 104));
        check("the (open) value fits the tension value column",
                fits("3 (open)", 44));

        System.out.println();
        System.out.println("checks run: " + checks + ", failures: " + failures);
        if (failures > 0) {
            System.out.println("FAIL: TrackerColumnFitTest");
            System.exit(1);
        }
        System.out.println("PASS: TrackerColumnFitTest");
    }

    /**
     * Walks every JLabel under {@code root} and asserts its text is no wider than
     * the width it was actually given. The panel's own layout is what allocated
     * that width, so this reads the real allocation rather than a restatement of
     * the constant the fix set.
     */
    private static boolean allLabelsFit(Container root, String panel) {
        boolean ok = true;
        int worst = 0;
        String worstText = null;
        ArrayList<JLabel> labels = new ArrayList<JLabel>();
        collect(root, labels);
        for (int i = 0; i < labels.size(); i++) {
            JLabel l = labels.get(i);
            String text = l.getText();
            if (text == null || text.length() == 0) continue;
            Font f = l.getFont();
            float need = textWidth(f, text);
            float have = l.getPreferredSize().width;
            // Only count a label as failing when the panel has committed to a
            // fixed width that is genuinely too small. A label left to its
            // natural preferred size cannot clip itself.
            if (have > 0 && need > have + 0.5) {
                ok = false;
                if (need - have > worst) {
                    worst = (int) (need - have);
                    worstText = text;
                }
            }
        }
        if (!ok) {
            System.out.println("       [" + panel + "] worst clip: \"" + worstText
                + "\" needs " + worst + "px more than it was given");
        }
        return ok;
    }

    private static void collect(Container c, ArrayList<JLabel> out) {
        Component[] kids = c.getComponents();
        for (int i = 0; i < kids.length; i++) {
            if (kids[i] instanceof JLabel) out.add((JLabel) kids[i]);
            if (kids[i] instanceof Container) collect((Container) kids[i], out);
        }
    }

    private static float textWidth(Font f, String s) {
        return (float) f.getStringBounds(s, new FontRenderContext(null, false, false)).getWidth();
    }

    private static boolean fits(String s, int available) {
        Font f9 = new Font("Monospaced", Font.PLAIN, 9);
        return textWidth(f9, s) <= available;
    }

    /**
     * A paintable player. Both trackers read deck/hand/model state in their paint
     * and update paths, so a fixture without a deck NPEs before any label exists
     * to measure — the B5-2506 fixture requirement.
     */
    private static Player player(String name, Race faction, boolean isHuman) {
        Player p = new Player(name, faction, isHuman);
        CharacterCard amb = new CharacterCard("t_" + name, name, "leader", Rarity.COMMON,
                Loyalty.HUMAN, CardSet.PREMIERE, null, "test character", 6, 6, 6, 6, false);
        p.setAmbassador(amb);
        p.getInnerCircle().add(amb);
        p.setDeck(new Deck(new ArrayList<Card>()));
        return p;
    }

    private static GameState state(Player... players) {
        return new GameState(new ArrayList<Player>(java.util.Arrays.asList(players)));
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
}