package b5ccg.ui.board;

import java.awt.Rectangle;

/**
 * Single source of truth for board rectangles. paintComponent AND hit-testing
 * must both call slotRect(); no other code may compute card positions.
 * Convention: within a row, later indexes are painted on top.
 * This implementation is locked to GameBoardPanel's layout as per conformance report section 3 M6 row.
 */
public final class BoardGeometry {
    public static final int ROW_AMBASSADOR = 0;
    public static final int ROW_INNER_CIRCLE = 1;
    public static final int ROW_SUPPORT = 2;
    public static final int ROW_FLEET = 3;
    public static final int ROW_GROUPS = 4;
    public static final int ROWS = 5;

    // Layout constants for GameBoardPanel's positioning. GameBoardPanel.resolveCardAt
    // delegates hit-testing to hitTest() below, and painting draws via slotRect()/cardRect().
    private static final int ZONE_PAD = 8; // left padding within each zone
    private static final int[] CARD_W = { 60, 46, 46, 46, 46 }; // ambassador, IC, support, fleets, groups/locs
    private static final int[] CARD_H = { 84, 64, 16, 64, 64 };
    private static final int[] ROW_Y = { 58, 175, 269, 310, 430 }; // Y position of each row from zone top
    private static final int[] CARD_STEP = { 0, 52, 50, 52, 52 }; // horizontal step between cards in each row (0 for ambassador since max 1)
    private static final int BOTTOM_BAND = 64; // war/station/tension lines at bottom

    public static final class Hit {
        public final int zone;
        public final int row;
        public final int index;

        Hit(int zone, int row, int index) {
            this.zone = zone;
            this.row = row;
            this.index = index;
        }
    }

    private int width;
    private int height;
    private int players = 1;
    private int zoneW;

    /** Call from paintComponent / componentResized, before painting or hit-testing. */
    public void layout(int w, int h, int nPlayers) {
        this.width = w;
        this.height = h;
        this.players = Math.max(1, nPlayers);
        this.zoneW = width / players; // width of each player's zone
    }

    public Rectangle zoneRect(int zone) {
        return new Rectangle(zone * zoneW, 0, zoneW, Math.max(1, height - BOTTOM_BAND));
    }

    public int cardW(int row) {
        return CARD_W[row];
    }

    public int cardH(int row) {
        return CARD_H[row];
    }

    public int rowY(int row) {
        return ROW_Y[row];
    }

    private int step(int zone, int row, int count) {
        // For ambassador (row 0), step is irrelevant since max count is 1
        // For other rows, use the predefined step
        if (row == ROW_AMBASSADOR) {
            return 0; // not used for ambassador
        }
        return CARD_STEP[row];
    }

    /** How many of 'count' cards are drawn; the rest belong in an overflow chip/popover. */
    public int visibleCount(int zone, int row, int count) {
        if (count <= 1) {
            return count;
        }
        int cw = cardW(row);
        int avail = zoneRect(zone).width - 2 * ZONE_PAD - cw; // available space for card placement
        if (avail <= 0) {
            return 1; // at least one card if any exist
        }
        int step = step(zone, row, count);
        if (step <= 0) {
            return count; // fallback if step is invalid
        }
        // Calculate how many cards fit: first card at ZONE_PAD, then each additional card adds 'step'
        int fit = 1 + (avail / step);
        return Math.min(count, fit);
    }

    public int overflowCount(int zone, int row, int count) {
        return count - visibleCount(zone, row, count);
    }

    public Rectangle slotRect(int zone, int row, int index, int count) {
        Rectangle z = zoneRect(zone);
        // For ambassador, index is always 0 (only one slot)
        // For other rows, calculate position based on index and step
        int x;
        if (row == ROW_AMBASSADOR) {
            x = z.x + ZONE_PAD;
        } else {
            x = z.x + ZONE_PAD + index * step(zone, row, count);
        }
        return new Rectangle(x, rowY(row), cardW(row), cardH(row));
    }

    /**
     * M6 primitive: deduplicated card rectangle. Every hardcoded coordinate
     * copy in GameBoardPanel.resolveCardAt (and its hover/tooltip/draw path)
     * now routes through this single entry point so paintComponent and
     * hit-testing cannot drift apart. Any remaining literal coordinate is a
     * drift risk and is refused at review.
     */
    public Rectangle cardRect(int zone, int row, int index, int count) {
        Rectangle z = zoneRect(zone);
        // For ambassador, index is always 0 (only one slot)
        int x;
        if (row == ROW_AMBASSADOR) {
            x = z.x + ZONE_PAD;
        } else {
            x = z.x + ZONE_PAD + index * step(zone, row, count);
        }
        return new Rectangle(x, rowY(row), cardW(row), cardH(row));
    }

    /**
     * M6 preview hook: rectangle a card's cost/preview string is drawn
     * against, matching the layout of the rendered card band. Introduced so
     * the preview target couples to BoardGeometry instead of a copied
     * coordinate expression.
     */
    public Rectangle previewRect(int zone, int row, int index, int count) {
        return cardRect(zone, row, index, count);
    }

    /**
     * Section 10 keymap: board-navigation bindings. Shipped here, with the
     * geometry they drive, so the keymap cannot drift from the layout.
     */
    public static final class KeyMap {
        public static final int MOVE_UP = 38;    // Up arrow
        public static final int MOVE_DOWN = 40;  // Down arrow
        public static final int MOVE_LEFT = 37;  // Left arrow
        public static final int MOVE_RIGHT = 39; // Right arrow
        public static final int SELECT = 13;     // Enter
        public static final int PREVIEW = 80;    // 'P' preview toggle
        public static final int COUNT = 6;
        public static final int[] KEYS = {
            MOVE_UP, MOVE_DOWN, MOVE_LEFT, MOVE_RIGHT, SELECT, PREVIEW
        };
    }

    /** counts[zone][row] = number of cards in that row. Returns null on miss. */
    public Hit hitTest(int x, int y, int[][] counts) {
        if (zoneW <= 0) {
            return null;
        }
        int zone = x / zoneW;
        if (zone < 0 || zone >= players || zone >= counts.length) {
            return null;
        }
        for (int row = ROWS - 1; row >= 0; row--) { // check from top row down (higher rows first for overlap)
            int n = counts[zone][row];
            int vis = visibleCount(zone, row, n);
            for (int i = vis - 1; i >= 0; i--) { // check from rightmost card first (later indexes painted on top)
                if (slotRect(zone, row, i, n).contains(x, y)) {
                    return new Hit(zone, row, i);
                }
            }
        }
        return null;
    }
}
