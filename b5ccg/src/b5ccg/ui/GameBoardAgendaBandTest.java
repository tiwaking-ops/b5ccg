package b5ccg.ui;
import b5ccg.model.enums.Loyalty;

import b5ccg.model.AftermathCard;
import b5ccg.model.AgendaCard;
import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.Deck;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Race;
import b5ccg.model.enums.Rarity;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import javax.swing.SwingUtilities;

/**
 * B5-2753: the board canvas' agenda readout and the fleets header must never share
 * a baseline, and the agenda must be printed exactly once per zone.
 *
 * <p>B5-2753 shipped all three of its sub-parts with no regression gate. This is
 * the gate for the two paint-order ones. {@link GameBoardCollapseTest} covers the
 * third (zone-width split) only because that was B5-2426's subject; nothing in the
 * repo asserted anything about the agenda band, which is the B5-1725 shape — a fix
 * that nothing can fail is indistinguishable from a fix that never broke.
 *
 * <p>Measured, not asserted. Both properties are pixel facts about a rendered zone,
 * so both are read off a real paint into a {@link BufferedImage}. Reading the source
 * instead would re-state the fix rather than test it, and a copy of the geometry is
 * exactly what drifts (the B5-2426 lesson, recorded in
 * {@code GameBoardCollapseTest}'s header).
 *
 * <p>The two colours are the contract. The agenda line is drawn in
 * {@code (220,180,60)} and the "Fleets:" header in {@code (160,180,220)}
 * ({@code GameBoardPanel} lines 1150 and 1226). Counting exact-colour pixels is
 * brittle if either colour is reused, so {@link #COLOR_ROLES} names every use and
 * the checks below assert the roles are still distinct — a future edit that reuses
 * the fleets colour for the agenda line fails here with a legible message instead of
 * silently passing.
 *
 * <p>Exit contract 0/1. Not in the {@code compile.sh} gate list (B5-0956's, lives
 * in {@code engine/}); run directly:
 *
 * <pre>java -cp out b5ccg.ui.GameBoardAgendaBandTest</pre>
 */
public final class GameBoardAgendaBandTest {

    private static final int W = 1280;
    private static final int H = 560;

    /** The agenda readout line, GameBoardPanel.java:1150. */
    private static final int AGENDA_RGB = 0xDCB43C;   // 220, 180, 60
    /** The "Fleets:" header, GameBoardPanel.java:1226. */
    private static final int FLEETS_RGB = 0xA0B4DC;   // 160, 180, 220
    /** The aftermaths line, GameBoardPanel.java:1160. */
    private static final int AFTERMATH_RGB = 0xAAAAE6; // 170, 170, 230

    /**
     * Every role this suite reads must be REACHABLE, not merely distinct. The first
     * draft of this file carried {@code 0xAAAAD6} for the aftermath line — a
     * mistyped hex, one nibble off {@code 0xAAAAE6} — and the distinctness check
     * passed anyway, because a colour that is never painted is still distinct from
     * every colour that is. The two aftermath assertions then passed while measuring
     * nothing: green over a gap is green.
     *
     * <p>So distinctness is not enough; each role is also asserted to actually
     * appear in a render that is known to draw it. That is the precondition check
     * below, and it is the check that would have caught the typo.
     *
     * <p>Named roles, kept adjacent to the constants so a reuse edit is a one-place
     * change and a wrong constant fails loudly rather than silently.
     */
    private static final int[][] COLOR_ROLES = {
        {AGENDA_RGB, 1150},
        {FLEETS_RGB, 1226},
        {AFTERMATH_RGB, 1160},
    };

    private static int failures = 0;
    private static int checks = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("B5-2753: agenda band and fleets header must not collide (ui)");

        // ── 0. the colour roles this suite reads by are still distinct ──────────
        // A gate that silently stopped discriminating is worse than no gate: it
        // would report green while measuring nothing. Checked before any paint.
        for (int i = 0; i < COLOR_ROLES.length; i++) {
            for (int j = i + 1; j < COLOR_ROLES.length; j++) {
                check("colour roles are distinct: line " + COLOR_ROLES[i][1]
                        + " vs line " + COLOR_ROLES[j][1],
                        COLOR_ROLES[i][0] != COLOR_ROLES[j][0]);
            }
        }

        // ── 1. no agenda, no aftermaths: the fleets header keeps its old slot ──
        // The control. Without it, a "the bands do not collide" pass could be an
        // artifact of the header simply having moved somewhere harmless.
        Player plain = player("Ivanova", Race.HUMAN, true);
        BufferedImage plainImg = render(state(plain));
        int plainFleetsTop = topmostRowOf(plainImg, FLEETS_RGB);
        check("control: the fleets header is actually painted", plainFleetsTop >= 0);
        check("control: no agenda line is painted without an agenda",
                topmostRowOf(plainImg, AGENDA_RGB) < 0);

        // ── 2. THE FIX: agenda present pushes the fleets header clear ───────────
        // Before B5-2753 the agenda line sat at y+308 and the header at y+306, so
        // every player holding an agenda printed one glyph row on top of the other.
        // The fix derives fleetsTop from what the readout consumed (y + 294 + 12*n
        // + 6), so the header must move DOWN by at least one line, never up and
        // never not at all.
        Player withAgenda = player("Ivanova", Race.HUMAN, true);
        withAgenda.setAgenda(agenda("Agenda Under Test"));
        BufferedImage agendaImg = render(state(withAgenda));

        int agendaRow = topmostRowOf(agendaImg, AGENDA_RGB);
        int agendaFleetsTop = topmostRowOf(agendaImg, FLEETS_RGB);
        check("the agenda line is painted when the player holds an agenda", agendaRow >= 0);
        check("the fleets header is still painted with an agenda", agendaFleetsTop >= 0);
        check("the fleets header moves DOWN when an agenda is added",
                agendaFleetsTop > plainFleetsTop);
        check("the fleets header clears the agenda line entirely",
                agendaFleetsTop > agendaRow);
        check("the fleets header never moves UP onto the agenda line",
                agendaFleetsTop >= plainFleetsTop);

        // ── 3. aftermaths stack too: two lines must clear two lines ────────────
        // fleetsTop is derived from what the readout consumed, so a second readout
        // line has to push the header again. A gate that only tested the one-line
        // case would still pass against a hard-coded +12.
        Player stacked = player("Ivanova", Race.HUMAN, true);
        stacked.setAgenda(agenda("Agenda Under Test"));
        GameState stackedState = state(stacked);
        stackedState.attachAftermath(aftermath("First Aftermath"), stacked);
        BufferedImage stackedImg = render(stackedState);
        check("precondition: the aftermaths line is painted when one is attached",
                topmostRowOf(stackedImg, AFTERMATH_RGB) >= 0);
        check("precondition: that line is painted exactly once",
                countRowsOf(stackedImg, AFTERMATH_RGB) == 1);
        check("the fleets header moves DOWN again for a second readout line",
                topmostRowOf(stackedImg, FLEETS_RGB) > agendaFleetsTop);
        check("the fleets header still clears the agenda line with both readouts",
                topmostRowOf(stackedImg, FLEETS_RGB) > topmostRowOf(stackedImg, AGENDA_RGB));

        // ── 4. THE OTHER HALF: exactly ONE agenda readout per zone ──────────────
        // The duplicate at y+h-22 restated what the band above already printed and
        // overlapped the deck/hand line. "Not painted at y+h-22" is asserted as a
        // row-band test so it fails on a reintroduced footer copy at any x.
        int agendaRows = countRowsOf(agendaImg, AGENDA_RGB);
        check("exactly one agenda line band is painted (found " + agendaRows + ")",
                agendaRows == 1);
        // The footer copy would land in the last few rows of the zone. An empty
        // agenda band there is the direct statement that nothing draws there now.
        check("nothing agenda-coloured is painted in the deck/hand footer band",
                countRowsOf(agendaImg, AGENDA_RGB, H - 30, H) == 0);

        // ── 5. the two bands never share a pixel row, per zone ─────────────────
        // The property itself, asserted over every row rather than over the two
        // extremes: an edit that reintroduced the overlap while keeping the topmost
        // rows apart would pass checks 2 alone.
        check("no row carries both the agenda line and the fleets header",
                noSharedRow(agendaImg, AGENDA_RGB, FLEETS_RGB));
        check("no row carries both the agenda line and the aftermaths line",
                noSharedRow(stackedImg, AGENDA_RGB, AFTERMATH_RGB));

        System.out.println();
        System.out.println("checks run: " + checks + ", failures: " + failures);
        if (failures > 0) {
            System.out.println("FAIL: GameBoardAgendaBandTest");
            System.exit(1);
        }
        System.out.println("PASS: GameBoardAgendaBandTest");
    }

    /** Topmost row containing an exact-colour pixel, or -1 when the colour is absent. */
    private static int topmostRowOf(BufferedImage img, int rgb) {
        for (int y = 0; y < img.getHeight(); y++) {
            if (rowHas(img, y, 0, img.getWidth(), rgb)) return y;
        }
        return -1;
    }

    /**
     * How many distinct bands of rows carry the colour. Consecutive rows count once,
     * so one line of text is one band however many pixel rows its glyphs occupy —
     * which is what makes "printed exactly once" a countable claim.
     */
    private static int countRowsOf(BufferedImage img, int rgb) {
        return countRowsOf(img, rgb, 0, img.getHeight());
    }

    private static int countRowsOf(BufferedImage img, int rgb, int y0, int y1) {
        int bands = 0;
        boolean inBand = false;
        for (int y = Math.max(0, y0); y < Math.min(img.getHeight(), y1); y++) {
            boolean here = rowHas(img, y, 0, img.getWidth(), rgb);
            if (here && !inBand) bands++;
            inBand = here;
        }
        return bands;
    }

    /** True when no single row carries both colours — the collision, stated directly. */
    private static boolean noSharedRow(BufferedImage img, int a, int b) {
        for (int y = 0; y < img.getHeight(); y++) {
            if (rowHas(img, y, 0, img.getWidth(), a) && rowHas(img, y, 0, img.getWidth(), b)) {
                return false;
            }
        }
        return true;
    }

    private static boolean rowHas(BufferedImage img, int y, int x0, int x1, int rgb) {
        for (int x = x0; x < x1; x++) {
            if ((img.getRGB(x, y) & 0xFFFFFF) == rgb) return true;
        }
        return false;
    }

    /** Renders the panel off-screen on the EDT, with no display present. */
    private static BufferedImage render(final GameState state) throws Exception {
        final GameBoardPanel board = new GameBoardPanel();
        board.setSize(W, H);
        board.update(state);
        final BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                Graphics2D g = img.createGraphics();
                try {
                    board.paint(g);
                } finally {
                    g.dispose();
                }
            }
        });
        return img;
    }

    /**
     * A paintable player. {@code drawZone} reads {@code p.getDeck().size()} with no
     * null guard, so a fixture without a deck NPEs inside the paint pass before any
     * pixel can be measured — the B5-2506 fixture requirement.
     */
    private static Player player(String name, Race faction, boolean isHuman) {
        Player p = new Player(name, faction, isHuman);
        CharacterCard amb = character(name);
        p.setAmbassador(amb);
        p.getInnerCircle().add(amb);
        p.setDeck(new Deck(new ArrayList<Card>()));
        return p;
    }

    private static CharacterCard character(String name) {
        return new CharacterCard("test_" + name, name, "leader", Rarity.COMMON,
                Loyalty.HUMAN, CardSet.PREMIERE, null, "test character", 6, 6, 6, 6, false);
    }

    /**
     * A face-up minor agenda. Face-up matters: the paint path skips the [WIN] probe
     * for a face-down card, and this suite is about band placement, not win state.
     */
    private static AgendaCard agenda(String title) {
        return new AgendaCard("test_agenda", title, "minor", Rarity.COMMON,
                Loyalty.HUMAN, CardSet.PREMIERE, null, "test agenda", false, null);
    }

    private static AftermathCard aftermath(String title) {
        return new AftermathCard("test_aftermath", title, "minor", Rarity.COMMON,
                Loyalty.HUMAN, CardSet.PREMIERE, null, "test aftermath", null);
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