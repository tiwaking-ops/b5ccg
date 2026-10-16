package b5ccg.ui;

import b5ccg.model.CivilWarState;
import b5ccg.model.*;
import b5ccg.model.enums.ConflictType;
import b5ccg.model.enums.TensionMatrix;
// B5-0429: tension readout iterates the matrix's Faction keys directly.
import b5ccg.model.enums.Faction;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.List;
import b5ccg.ui.board.BoardGeometry;
import b5ccg.ui.TargetFilter;

public class GameBoardPanel extends JPanel {

    private GameState state;

    // B5-0423: board-side selection. Mirrors HandPanel's callback; feeds the
    // board-zone controls (heal/repair/lead-fleet/use-rotate-effect) with the
    // IC / supporting / fleet / location cards that never appear in hand.
    private Card selectedCard;
    private BoardGeometry boardGeom = new BoardGeometry();
    private CardSelectedListener onBoardCardSelected;

    // B5-4216: targeting mode — when active, non-targetable cards are dimmed
    // and click selects a target (replaces JComboBox selectors per B5-4184).
    private boolean targetingMode = false;
    private TargetFilter targetFilter = null;
    private CardSelectedListener onTargetSelected = null;
    private Card selectedTarget = null;

    // B5-0347: outcome banner state. The controller clears the active conflict
    // right after resolution (GameController.resolveCurrentConflict), so the
    // UI holds the last Conflict object: after resolve(winner) it retains
    // isResolved()/getWinner()/side totals — structured outcome data, no log
    // parsing. Shown until a new conflict activates.
    private Conflict lastHeldConflict;
    private String   lastOutcomeTitle;
    private String   lastOutcomeDetail;

    public GameBoardPanel() {
        setBackground(new Color(15, 35, 15));
        setPreferredSize(new Dimension(1280, 560));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                handleClick(e.getX(), e.getY());
            }
        });
        // B5-1971: hover tooltip for board cards (Inner Circle, Supporting Role,
        // Fleets, Groups, Locations, Ambassador).
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                setToolTipText(renderCardTooltip(resolveCardAt(e.getX(), e.getY())));
            }
        });
    }

    /** B5-0361: the UI-held conflict (active, or the last one before the
     *  controller cleared it). MainWindow brokers it to HandPanel so the
     *  eligible-aftermath highlight can consult the real 6-arg legality. */
    public Conflict getLastHeldConflict() { return lastHeldConflict; }

    /**
     * B5-0423: board click. Mirrors HandPanel.handleClick — a hit on a board
     * card selects it locally and notifies the controller; a miss only drops
     * the local highlight (the controller's own selection is untouched, so a
     * hand selection survives a stray click on empty board space).
     * B5-4216: in targeting mode, click selects a target if it passes the filter.
     */
    private void handleClick(int mx, int my) {
        Card hit = resolveCardAt(mx, my);
        if (targetingMode && hit != null && targetFilter != null) {
            if (targetFilter.isTarget(hit)) {
                selectedTarget = hit;
                if (onTargetSelected != null) {
                    onTargetSelected.onCardSelected(hit);
                }
                repaint();
                return;
            }
            // Non-targetable card clicked in targeting mode — ignore
            return;
        }
        selectedCard = hit;
        if (hit != null && onBoardCardSelected != null) {
            onBoardCardSelected.onCardSelected(hit);
        }
        repaint();
    }

    /**
     * B5-0423: translate a panel point to the board card drawn under it. The
     * geometry MUST stay in lockstep with drawZone: one zone per player,
     * zoneW = getWidth() / player count, and each row's card origin/step
     * copied verbatim from its drawMiniCard call. Rows are probed top-down so
     * an overlap resolves to the higher band, matching what the eye sees.
     * Face-down cards are skipped — their identity is hidden from the host.
     */
    private Card resolveCardAt(int mx, int my) {
        if (state == null) return null;
        List<Player> players = state.getPlayers();
        if (players.isEmpty()) return null;
        
        // Use BoardGeometry for single source of truth for board geometry
        BoardGeometry bg = new BoardGeometry();
        bg.layout(getWidth(), getHeight(), players.size());
        
        // Build counts array for hit testing: [zone][row] = number of cards in that row
        int[][] counts = new int[players.size()][BoardGeometry.ROWS];
        for (int zone = 0; zone < players.size(); zone++) {
            Player p = players.get(zone);
            counts[zone][BoardGeometry.ROW_AMBASSADOR] = p.getAmbassador() != null ? 1 : 0;
            counts[zone][BoardGeometry.ROW_INNER_CIRCLE] = p.getInnerCircle().size();
            counts[zone][BoardGeometry.ROW_SUPPORT] = p.getSupportingRole().size();
            counts[zone][BoardGeometry.ROW_FLEET] = p.getFleets().size();
            counts[zone][BoardGeometry.ROW_GROUPS] = p.getGroups().size() + p.getLocations().size();
        }
        
        // Use BoardGeometry to hit test
        BoardGeometry.Hit hit = bg.hitTest(mx, my, counts);
        if (hit == null) {
            return null;
        }
        
        // Get the player for this zone
        Player p = players.get(hit.zone);
        
        // Return the card based on the hit position
        switch (hit.row) {
            case BoardGeometry.ROW_AMBASSADOR:
                Card amb = p.getAmbassador();
                return amb != null && !amb.isFaceDown() ? amb : null;
            case BoardGeometry.ROW_INNER_CIRCLE:
                if (hit.index < p.getInnerCircle().size()) {
                    CharacterCard ch = p.getInnerCircle().get(hit.index);
                    return ch.isFaceDown() ? null : ch;
                }
                break;
            case BoardGeometry.ROW_SUPPORT:
                if (hit.index < p.getSupportingRole().size()) {
                    CharacterCard sch = p.getSupportingRole().get(hit.index);
                    return sch.isFaceDown() ? null : sch;
                }
                break;
            case BoardGeometry.ROW_FLEET:
                if (hit.index < p.getFleets().size()) {
                    FleetCard fl = p.getFleets().get(hit.index);
                    return fl.isFaceDown() ? null : fl;
                }
                break;
            case BoardGeometry.ROW_GROUPS:
                int groupCount = p.getGroups().size();
                if (hit.index < groupCount) {
                    GroupCard gr = p.getGroups().get(hit.index);
                    return gr.isFaceDown() ? null : gr;
                } else {
                    int locIndex = hit.index - groupCount;
                    if (locIndex < p.getLocations().size()) {
                        LocationCard lc = p.getLocations().get(locIndex);
                        return lc.isFaceDown() ? null : lc;
                    }
                }
                break;
        }
        
        return null;
    }

    /** B5-0423: point-in-rect test for a card origin. */
    private boolean hit(int mx, int my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh;
    }

    /**
     * B5-0423: highlight the selected card. A 2px lime stroke inset over the
     * mini-card; the `large` form carries the ambassador's 60x84 footprint.
     * Identity comparison is by reference so two copies of one card title
     * never cross-highlight.
     */
    private void drawSelectionMark(Graphics2D g, Card card, int x, int y) {
        drawSelectionMark(g, card, x, y, false);
    }

    private void drawSelectionMark(Graphics2D g, Card card, int x, int y, boolean large) {
        if (card == null || card != selectedCard) return;
        int w = large ? 60 : 46;
        int h = large ? 84 : 64;
        g.setColor(new Color(120, 255, 120));
        g.setStroke(new BasicStroke(2.0f));
        g.drawRoundRect(x + 1, y + 1, w - 2, h - 2, 6, 6);
    }

    /** B5-0423: board-side selection callback (mirror of HandPanel.set). */
    public void setOnBoardCardSelected(CardSelectedListener cb) { onBoardCardSelected = cb; }

    /** B5-0423: drop the local highlight after a submit. */
    public void clearSelection() {
        selectedCard = null;
        repaint();
    }

    /** B5-4216: enter targeting mode with a filter predicate.
     *  When active, non-targetable cards are dimmed and click selects target.
     *  @param filter predicate that returns true for valid targets
     *  @param onTarget callback when a valid target is clicked
     */
    public void setTargetFilter(TargetFilter filter,
                                 CardSelectedListener onTarget) {
        this.targetingMode = true;
        this.targetFilter = filter;
        this.onTargetSelected = onTarget;
        this.selectedTarget = null;
        repaint();
    }

    /** B5-4216: exit targeting mode, return to normal selection mode. */
    public void clearTargetFilter() {
        this.targetingMode = false;
        this.targetFilter = null;
        this.onTargetSelected = null;
        this.selectedTarget = null;
        repaint();
    }

    /** B5-4216: get the currently selected target (valid only in targeting mode). */
    public Card getSelectedTarget() {
        return selectedTarget;
    }

    /** B5-4216: check if a card is a valid target in current targeting mode. */
    private boolean isValidTarget(Card card) {
        return targetingMode && targetFilter != null && targetFilter.isTarget(card);
    }

    public void update(GameState s) {
        this.state = s;
        selectedCard = null;   // highlight is transient per-refresh (mirrors HandPanel)
        // B5-4216: clear targeting mode on state refresh since target validity may have changed
        clearTargetFilter();
        Conflict active = s.getActiveConflict();
        if (active != null) {
            if (active != lastHeldConflict) {   // a new conflict activated
                lastHeldConflict = active;
                lastOutcomeTitle = null;
                lastOutcomeDetail = null;
            }
        } else if (lastHeldConflict != null) {
            if (lastHeldConflict.isResolved() && lastHeldConflict.getWinner() != null) {
                lastOutcomeTitle  = lastHeldConflict.getCard().getTitle()
                                    + "  —  WON BY " + lastHeldConflict.getWinner().getName();
                lastOutcomeDetail = "support=" + lastHeldConflict.supportTotal()
                                    + "  opposition=" + lastHeldConflict.oppositionTotal()
                                    + "  (" + lastHeldConflict.getInfluenceReward()
                                    + " influence to the winner)";
            } else {
                // cleared without resolution (round advance): drop it
                lastHeldConflict = null;
                lastOutcomeTitle = null;
                lastOutcomeDetail = null;
            }
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() { repaint(); }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (state == null) return;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        List<Player> players = state.getPlayers();
        int cols   = players.size();
        int zoneW  = getWidth() / cols;
        boardGeom.layout(getWidth(), getHeight(), players.size());

        for (int i = 0; i < players.size(); i++) {
            drawZone(g2, players.get(i), i * zoneW, 0, zoneW, getHeight(), boardGeom, i);
        }

        // B5-0427: board-level at-war faction-pairs line, read from the
        // B5-0376 tension matrix. Rendered only when some pair is at war —
        // the normal pool state has no tension sources, so this stays clear.
        String warLine = atWarLine();
        if (warLine.length() > 0) {
            g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            FontMetrics wfm = g2.getFontMetrics();
            int lw = wfm.stringWidth(warLine);
            int wy = getHeight() - 40;
            g2.setColor(new Color(120, 10, 10, 200));
            g2.fillRoundRect((getWidth() - lw) / 2 - 4, wy - 10, lw + 8, 14, 5, 5);
            g2.setColor(new Color(255, 170, 170));
            g2.drawString(warLine, (getWidth() - lw) / 2, wy);
        }

        // B5-0429: station + non-player-forces readout (B5-0340 model API),
        // always rendered — the ratings exist from round one even while inert.
        String station = stationLine();
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        FontMetrics sfm = g2.getFontMetrics();
        int sx = (getWidth() - sfm.stringWidth(station)) / 2;
        int sy = getHeight() - 24;
        g2.setColor(new Color(150, 190, 230));
        g2.drawString(station, sx, sy);
        if (state.isShadowWar()) {
            g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2.setColor(new Color(255, 120, 120));
            g2.drawString("  [SHADOW WAR]", sx + sfm.stringWidth(station), sy);
        }

        // B5-0429: directional tension pairs (B5-0376), only when any pair
        // is nonzero — the pool has no tension sources, so this stays clear
        // in normal games. Drawn above the war pill band to keep the bottom
        // rows distinct from the per-zone deck lines.
        String tension = tensionLine();
        if (tension.length() > 0) {
            g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
            FontMetrics tfm = g2.getFontMetrics();
            g2.setColor(new Color(230, 170, 120));
            g2.drawString(tension, (getWidth() - tfm.stringWidth(tension)) / 2,
                          getHeight() - 58);
        }

        // Active conflict banner
        if (state.getActiveConflict() != null) {
            Conflict c = state.getActiveConflict();
            g2.setColor(new Color(200, 50, 50, 200));
            g2.fillRoundRect(getWidth() / 4, getHeight() / 2 - 20, getWidth() / 2, 40, 10, 10);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            // B5-0329a: the "assistant status overlay" that stood here was
            // removed — it invented five personas and fake task references for
            // the rulebook §IV assistant mechanic, which is not implemented
            // anywhere in model/ (its 8pt font also corrupted this banner).
            String msg = c.isWarConflict()
                ? "WAR: " + warConflictTitle(c)
                : "CONFLICT: " + c.getCard().getTitle()
                    + "  [" + c.getConflictType() + " \u2014 " + conflictTypeAbility(c.getConflictType()) + "]";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2 + 6);

            // Sides readout (B5-0309 / D14): committed-supporters vs committed-opposers,
            // with totals. Blank line added under the banner for separation.
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(Color.LIGHT_GRAY);
            g.drawString("sides committed (B5-0309):", (getWidth() - fm.stringWidth("sides committed (B5-0309):")) / 2, (getHeight() / 2) + 30);
            String sideLine = c.getSupporters().size() + " support (" + c.supportTotal() + ")   |   " + c.getOpposers().size() + " oppose (" + c.oppositionTotal() + ")";
            g.drawString(sideLine, (getWidth() - fm.stringWidth(sideLine)) / 2, (getHeight() / 2) + 46);

            // Per-participant breakdown (B5-0346, D14 decision 2): committed
            // cards per side, under the sides readout. Read-only render of the
            // D14 sides API. Card lists are snapshotted before iteration so a
            // commit landing mid-paint cannot throw a
            // ConcurrentModificationException on the paint thread; iteration
            // goes through state.getPlayers() for a deterministic order.
            java.util.ArrayList<Player> roster = new java.util.ArrayList<Player>(state.getPlayers());
            int lineY = (getHeight() / 2) + 62;
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            FontMetrics detailFm = g.getFontMetrics();
            for (Player pl : roster) {
                boolean support = c.isSupporting(pl);
                boolean oppose  = c.isOpposing(pl);
                if (!support && !oppose) continue;   // not a participant
                java.util.ArrayList<Card> cards =
                    new java.util.ArrayList<Card>(c.getCommittedCards(pl));
                StringBuilder line = new StringBuilder();
                line.append(support ? "[support] " : "[oppose]  ");
                line.append(pl.getName()).append(": ");
                if (cards.isEmpty()) {
                    line.append("(no cards committed)");
                } else {
                    for (int i = 0; i < cards.size(); i++) {
                        if (i > 0) line.append(", ");
                        Card card = cards.get(i);
                        int val = card.getPrimaryStatValue(c.getConflictType());
                        line.append(card.getTitle()).append("(").append(val);
                        if (val == 0 && card.isNeutralized()) {
                            line.append(",NEUT");
                        }
                        line.append(")");
                    }
                    line.append("  [").append(c.playerTotal(pl)).append("]");
                }
                String detail = line.toString();
                g.setColor(support ? new Color(120, 200, 120) : new Color(220, 160, 120));
                g.drawString(detail, (getWidth() - detailFm.stringWidth(detail)) / 2, lineY);
                lineY += 13;
            }
            }   // closes the active-conflict block

            // B5-2006: inter-faction states matrix, painted after the zones
            // and the centred readout lines. It reads model state only and
            // draws nothing when fewer than two races are in play.
            drawInterFactionStates(g2);

            // B5-0347: conflict outcome banner — SIBLING of the active-conflict
            // block: it must render exactly when the active conflict is gone
            // (the controller clears it right after resolution). Placing it
            // inside the active block made it unreachable in its only useful
            // state — caught by the reflection + pixel diagnostic.
            if (lastOutcomeTitle != null) {
                FontMetrics ofm = g2.getFontMetrics();
                g2.setColor(new Color(40, 120, 40, 210));
                g2.fillRoundRect(getWidth() / 4, getHeight() / 2 - 20, getWidth() / 2, 40, 10, 10);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 13));
                g2.drawString(lastOutcomeTitle,
                    (getWidth() - ofm.stringWidth(lastOutcomeTitle)) / 2, getHeight() / 2 + 2);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g2.setColor(new Color(200, 230, 200));
                g2.drawString(lastOutcomeDetail,
                    (getWidth() - ofm.stringWidth(lastOutcomeDetail)) / 2, getHeight() / 2 + 16);
            }
    }

    /** B5-0429: station + shadow/vorlon readout line from the B5-0340 API
     *  (getStation().getInfluence(), getShadowInfluence(), getVorlonInfluence()).
     *  The [SHADOW WAR] marker is appended by the painter, not here, so the
     *  string stays a pure data readout. Public for headless probes. */
    public String stationLine() {
        if (state == null) return "";
        return "Station: " + state.getStation().getInfluence()
             + "  |  Shadow " + state.getShadowInfluence()
             + "  Vorlon " + state.getVorlonInfluence();
    }

    /** B5-0429: directional tension pairs readout (B5-0376), sorted for
     *  deterministic order, one label per nonzero directed pair.
     *  Empty string when every pair is zero. Snapshot-first per the paint-
     *  thread rule (getTensionMap is a live-map view). Public for probes. */
    public String tensionLine() {
        if (state == null) return "";
        java.util.Map<Faction, java.util.Map<Faction, Integer>> map =
            state.getTensionMatrix().getTensionMap();
        java.util.ArrayList<String> labels = new java.util.ArrayList<String>();
        java.util.ArrayList<Faction> sources =
            new java.util.ArrayList<Faction>(map.keySet());
        for (Faction src : sources) {
            java.util.Map<Faction, Integer> row = map.get(src);
            java.util.ArrayList<Faction> targets =
                new java.util.ArrayList<Faction>(row.keySet());
            for (Faction tgt : targets) {
                Integer v = row.get(tgt);
                if (v != null && v.intValue() > 0) {
                    labels.add(src.toString() + " to " + tgt.toString() + " " + v);
                }
            }
        }
        if (labels.isEmpty()) return "";
        java.util.Collections.sort(labels);
        StringBuilder sb = new StringBuilder("Tension: ");
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(labels.get(i));
        }
        return sb.toString();
    }

    /** B5-0427: board-level at-war pairs line from the B5-0376 tension
     *  matrix (getAtWarPairs snapshot, sorted for deterministic order).
     *  Empty string when no pair is at war. Public so a headless paint
     *  probe can assert the readout without parsing pixels. */
    public String atWarLine() {
        if (state == null) return "";
        java.util.Set<TensionMatrix.FactionPair> pairs =
            state.getTensionMatrix().getAtWarPairs();
        if (pairs.isEmpty()) return "";
        java.util.ArrayList<String> labels = new java.util.ArrayList<String>();
        for (TensionMatrix.FactionPair fp : pairs) {
            labels.add(fp.a.toString() + "-" + fp.b.toString());
        }
        java.util.Collections.sort(labels);
        StringBuilder sb = new StringBuilder("At war: ");
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(labels.get(i));
        }
        return sb.toString();
    }

    // ── B5-2006: inter-faction states matrix ─────────────────────────────
    //
    // READOUT ONLY, and deliberately narrow about what it claims. The model
    // holds exactly two inter-faction facts (b5ccg/model/enums/TensionMatrix):
    // an unordered at-war set, and a DIRECTIONAL 0..5 tension score per pair.
    // There is no alliance or trade-pact field anywhere in model/ or engine/ --
    // the rulebook (:799) lists alliance / trade / war as the states a game may
    // have, but none of them is implemented as model state, and this row's
    // scope is ui/ only. So the matrix renders the two facts that DO exist and
    // derives its five labels from them by the explicit mapping below; it
    // asserts no rule the engine does not already hold. Those bands are a UI
    // presentational scale on real tension numbers, not rulebook state names,
    // and a rulebook alliance/trade mechanic would replace them rather than
    // sit beside them.
    //
    // Fence on B5-1969: this is a COMPANION to the InfluenceTrackerPanel in the
    // right sidebar, not a replacement. It shows relations BETWEEN races where
    // the tracker shows each race's own totals against the victory threshold;
    // neither derives the other's numbers, and InfluenceTrackerPanel is left
    // byte-identical by this row.

    /** B5-2006: the pair label when the at-war set contains the pair. */
    public static final String STATE_WAR = "WAR";
    /** B5-2006: high summed directional tension, no formal war. */
    public static final String STATE_HOSTILE = "HOSTILE";
    /** B5-2006: mid summed directional tension, no formal war. */
    public static final String STATE_STRAINED = "STRAINED";
    /** B5-2006: low summed directional tension, no formal war. */
    public static final String STATE_TENSE = "TENSE";
    /** B5-2006: no tension recorded in either direction, no formal war. */
    public static final String STATE_NEUTRAL = "NEUTRAL";

    /**
     * B5-2006: the derived inter-faction state of one unordered pair.
     *
     * WAR wins outright -- it is the only state the model records explicitly
     * (TensionMatrix.enterWar). Otherwise the label is a band on the summed
     * DIRECTIONAL tension, read live from the matrix: the model clamps each
     * direction to 0..5 (TensionMatrix.raiseTension), so the sum spans 0..10
     * and the bands below are fixed points on that scale. NEUTRAL is the
     * no-evidence case: nothing recorded, nothing claimed. A null state, a
     * null faction, or a self pair yields NEUTRAL rather than throwing.
     *
     * Public for headless probes, mirroring atWarLine() / tensionLine().
     */
    public static String factionPairState(GameState s, Faction a, Faction b) {
        if (s == null || a == null || b == null || a == b) return STATE_NEUTRAL;
        if (s.isAtWar(a, b)) return STATE_WAR;
        TensionMatrix m = s.getTensionMatrix();
        int sum = m.getTension(a, b) + m.getTension(b, a);
        if (sum >= 6) return STATE_HOSTILE;
        if (sum >= 3) return STATE_STRAINED;
        if (sum > 0)  return STATE_TENSE;
        return STATE_NEUTRAL;
    }

    /**
     * B5-2006: the colour band for a pair label. Public and static so a
     * headless probe can assert the coding without parsing pixels -- the same
     * reason atWarLine() and tensionLine() are public (B5-0427 / B5-0429).
     * Any unrecognised label falls back to the NEUTRAL band rather than
     * throwing, so a future label cannot blank the matrix.
     */
    public static Color stateColor(String label) {
        if (STATE_WAR.equals(label))      return new Color(200, 40, 40);
        if (STATE_HOSTILE.equals(label))  return new Color(215, 110, 50);
        if (STATE_STRAINED.equals(label)) return new Color(210, 170, 60);
        if (STATE_TENSE.equals(label))    return new Color(110, 170, 110);
        return new Color(90, 110, 90);
    }

    /**
     * B5-2006: the factions worth a row or column -- the races actually in
     * play, plus any faction the at-war set names (war is recorded between
     * races, so a pair may name a race with no seat at the table).
     *
     * NEUTRAL and ANY are never rows: NEUTRAL is the wildcard playable by
     * everyone and ANY is a match-any sentinel, so neither is a party to a
     * relation. NON_ALIGNED IS kept -- rulebook :208 makes the League of
     * Non-Aligned Worlds the fifth playable race, so it is a party like any
     * other. Dedupe is by enum identity, order by ordinal: deterministic
     * across paints, which matters because this feeds the pixel paint.
     */
    public static java.util.List<Faction> boardFactions(GameState s) {
        java.util.List<Faction> out = new java.util.ArrayList<Faction>();
        if (s == null) return out;
        java.util.List<Player> roster =
            new java.util.ArrayList<Player>(s.getPlayers());
        for (int i = 0; i < roster.size(); i++) {
            addFaction(out, roster.get(i).getFaction());
        }
        java.util.Set<TensionMatrix.FactionPair> pairs =
            s.getTensionMatrix().getAtWarPairs();
        for (TensionMatrix.FactionPair fp : pairs) {
            addFaction(out, fp.a);
            addFaction(out, fp.b);
        }
        java.util.Collections.sort(out, new java.util.Comparator<Faction>() {
            @Override public int compare(Faction x, Faction y) {
                return x.ordinal() - y.ordinal();
            }
        });
        return out;
    }

    /** B5-2006: identity-deduped append; drops the wildcard constants. */
    private static void addFaction(java.util.List<Faction> out, Faction f) {
        if (f == null) return;
        if (f == Faction.NEUTRAL || f == Faction.ANY) return;
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i) == f) return;
        }
        out.add(f);
    }

    /**
     * B5-2006: the matrix as text -- a header row of sigils then one row per
     * faction carrying a short label per later faction, so a headless probe
     * can assert the relations the board draws. The upper triangle is blank
     * because the pair state is symmetric (TensionMatrix.isAtWar is, and a sum
     * of two directional tensions is commutative), so the lower triangle
     * states each pair exactly once. Empty string with no state.
     * Public for probes, mirroring atWarLine() / tensionLine().
     */
    public static String interFactionMatrix(GameState s) {
        java.util.List<Faction> fs = boardFactions(s);
        // Fewer than two races means there are no pairs, and a header row
        // naming one race is a readout with nothing in it. Return empty, the
        // same answer drawInterFactionStates gives by not painting -- the two
        // must agree or a probe asserts a matrix the board never draws.
        if (fs.size() < 2) return "";
        StringBuilder sb = new StringBuilder("States:");
        for (int c = 0; c < fs.size(); c++) {
            sb.append(' ').append(sigil(fs.get(c)));
        }
        for (int r = 0; r < fs.size(); r++) {
            sb.append('\n').append(sigil(fs.get(r)));
            for (int c = r + 1; c < fs.size(); c++) {
                sb.append(' ')
                  .append(shortLabel(factionPairState(s, fs.get(r), fs.get(c))));
            }
        }
        return sb.toString();
    }

    /** B5-2006: one-character label so the matrix fits its box. */
    private static String shortLabel(String label) {
        if (STATE_WAR.equals(label))      return "W";
        if (STATE_HOSTILE.equals(label))  return "H";
        if (STATE_STRAINED.equals(label)) return "S";
        if (STATE_TENSE.equals(label))    return "t";
        return "n";
    }

    /**
     * B5-2006: matrix sigil. The NEUTRAL / NON_ALIGNED / ANY constants share
     * leading letters with real races (Narn, Minbari), so those get
     * two-character forms; the four playable races get their initial.
     */
    private static String sigil(Faction f) {
        if (f == null) return "?";
        if (f == Faction.NEUTRAL)     return "Nu";
        if (f == Faction.NON_ALIGNED) return "NA";
        if (f == Faction.ANY)         return "An";
        String s = f.toString();
        return s.length() > 0 ? s.substring(0, 1) : "?";
    }

    /**
     * B5-2006: paint the matrix. Bottom-right, clear of the centred
     * station / tension / at-war readout lines (all centred and short) and of
     * the per-zone left-aligned header and card rows. The background is
     * translucent by design: at a high player count the right-hand zone's
     * cards reach this corner, and an opaque box would hide cards rather than
     * merely overlay them.
     */
    private void drawInterFactionStates(Graphics2D g2) {
        java.util.List<Faction> fs = boardFactions(state);
        if (fs.size() < 2) return;          // fewer than two races: no pairs

        int sigilW = 26;
        int cell   = 14;
        int pad    = 5;
        int headH  = 12;
        int w = pad + sigilW + pad + cell * (fs.size() - 1) + pad;
        int h = pad + headH + pad + cell * fs.size() + pad;
        int x = getWidth() - w - 6;
        int y = getHeight() - h - 6;
        if (x < 0 || y < 0) return;

        // A copy, so the font/stroke/colour set here cannot leak into the
        // caller's Graphics2D (the rest of paintComponent keeps drawing after
        // this). Java 6: no try-with-resources, hence the finally.
        Graphics2D g = (Graphics2D) g2.create();
        try {
            g.setColor(new Color(10, 20, 10, 190));
            g.fillRect(x, y, w, h);
            g.setColor(new Color(80, 120, 80, 200));
            g.drawRect(x, y, w, h);
            g.setFont(new Font("SansSerif", Font.BOLD, 8));

            // Column header: one sigil above each column.
            int cx = x + pad + sigilW;
            for (int c = 0; c < fs.size(); c++) {
                String s = sigil(fs.get(c));
                g.setColor(new Color(180, 200, 180));
                g.drawString(s, cx + cell / 2 - g.getFontMetrics().stringWidth(s) / 2,
                             y + pad + headH - 3);
                cx += cell;
            }

            // One row per faction; the lower triangle carries the pair state.
            for (int r = 0; r < fs.size(); r++) {
                int ry = y + pad + headH + r * cell;
                g.setColor(new Color(180, 200, 180));
                g.drawString(sigil(fs.get(r)), x + pad, ry + cell - 4);
                int bx = x + pad + sigilW;
                for (int c = 0; c < fs.size(); c++) {
                    if (c > r) {
                        g.setColor(stateColor(factionPairState(state,
                                                              fs.get(r), fs.get(c))));
                        g.fillRect(bx + 1, ry + 1, cell - 2, cell - 2);
                    }
                    bx += cell;
                }
            }
        } finally {
            g.dispose();
        }
    }

        /** B5-0376: banner title for a war conflict — the declared kind plus its
         *  target (race or location), since a war conflict carries no ConflictCard. */
    /** B5-2263: human-readable ability name for a conflict type. */
    private String conflictTypeAbility(ConflictType type) {
        if (type == null) return "";
        switch (type) {
            case DIPLOMACY:  return "Diplomacy";
            case INTRIGUE:   return "Intrigue";
            case MILITARY:   return "Military (Fleets)";
            case PSI:        return "Psi";
            default:         return "";
        }
    }

    private String warConflictTitle(Conflict c) {
        if (c.getTargetLocation() != null) {
            return "war on " + c.getTargetLocation().getTitle();
        }
        Player t = c.getTarget();
        return "war on " + (t != null ? t.getName() : "(unknown)")
            + "  [" + c.getConflictType() + " \u2014 " + conflictTypeAbility(c.getConflictType()) + "]";
    }

    private void drawZone(Graphics2D g, Player p, int x, int y, int w, int h, BoardGeometry bg, int zoneIndex) {
        boolean active = state.getActivePlayer() == p;
        g.setColor(active ? new Color(35, 70, 35) : new Color(20, 45, 20));
        g.fillRect(x + 2, y + 2, w - 4, h - 4);

        g.setColor(active ? new Color(100, 220, 100) : new Color(50, 100, 50));
        g.setStroke(new BasicStroke(active ? 2.5f : 1));
        g.drawRect(x + 2, y + 2, w - 4, h - 4);

        // Header
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(factionColor(p));
        g.drawString(p.getName(), x + 8, y + 18);

        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(new Color(200, 200, 120));
        g.drawString("Influence: " + p.getInfluence(), x + 8, y + 33);
        g.drawString(p.getFaction().toString(), x + 8, y + 47);
        // B5-0701: computed Power beside Influence (B5-0677 seam). Power is
        // DERIVED, never stored: getPower() == getInfluence() + POWER-tagged
        // bonus total. Shown only when the two actually differ, so a board with
        // no Power-bearing card in play is not cluttered with a redundant
        // number -- and so the reader is never asked which of two equal numbers
        // is the real one. Placed on the RIGHT of the name row: the left column
        // below y+58 belongs to the ambassador mini-card, and drawing there
        // would overlap it.
        int power = p.getPower();
        if (power != p.getInfluence()) {
            g.setColor(new Color(240, 190, 90));
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.drawString("Power: " + power + " (inf "
                + p.getInfluence() + " "
                + (p.getPowerBonusTotal() >= 0 ? "+ " : "- ")
                + Math.abs(p.getPowerBonusTotal()) + ")",
                x + w - 108, y + 33);
        }

        // B5-0977: the Unrest and Civil War readouts both drew at the SAME
        // origin as the faction string on this row (x + 8, y + 47), so the
        // faction name was painted over by "Unrest: N" whenever unrest > 1 --
        // and "Unrest: N" was itself painted over by the "CIVIL WAR" badge when
        // both conditions held. Three signals, one pixel. Chain them to the
        // RIGHT of the faction string instead, each measuring the label before
        // it, so every readout on this row stays legible at the same font size.
        // This is a paint-order change only: no state is read here that was not
        // already readable, nothing is derived, and no engine call changes.
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        int statusX = x + 8
            + g.getFontMetrics().stringWidth(p.getFaction().toString()) + 10;
        // Unrest readout (B5-0691 / B5-0715)
        int unrest = p.getUnrest();
        if (unrest > 1) {
            g.setColor(new Color(255, 180, 80));
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            String unrestLabel = "Unrest: " + unrest;
            g.drawString(unrestLabel, statusX, y + 47);
            statusX += g.getFontMetrics().stringWidth(unrestLabel) + 10;
        }
        // Civil War state readout (B5-0691 / B5-0715)
        CivilWarState cws = state.civilWarOfRace(p.getFaction());
        if (cws != null && cws.getPhase() == CivilWarState.Phase.CIVIL_WAR) {
            g.setColor(new Color(255, 120, 120));
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            String badge = "CIVIL WAR";
            int badgeW = g.getFontMetrics().stringWidth(badge);
            if (statusX + badgeW <= x + w - 4) {
                g.drawString(badge, statusX, y + 47);
            } else if (x + w - 4 - badgeW > x + 68) {
                // Too narrow to chain on the header row: the rulebook allows
                // "more" players under the alternate faction rules, and at 7+
                // the chain runs past the zone border. Drop the badge to the
                // row below, right-aligned, which is clear of the 60px
                // ambassador mini-card that occupies the left of that row.
                g.drawString(badge, x + w - 4 - badgeW, y + 61);
            } else {
                // Narrower still: keep it on the header row and let the zone
                // border clip it. A clipped badge beats a badge painted over
                // the faction name, which is the defect this row removed.
                g.drawString(badge, statusX, y + 47);
            }
        }

        // Phase indicator
        if (state.getActivePlayer() == p) {
            g.setColor(new Color(255, 220, 60));
            g.setFont(new Font("SansSerif", Font.ITALIC, 10));
            g.drawString("[" + state.getPhase() + "]", x + w - 90, y + 18);
        }

        // Ambassador
        if (p.getAmbassador() != null) {
            int ambassadorCount = 1;
            int ambassadorVisible = bg.visibleCount(zoneIndex, BoardGeometry.ROW_AMBASSADOR, ambassadorCount);
            if (ambassadorVisible > 0) {
                Rectangle ambassadorRect = bg.slotRect(zoneIndex, BoardGeometry.ROW_AMBASSADOR, 0, ambassadorCount);
                drawMiniCard(g, p.getAmbassador(), ambassadorRect.x, ambassadorRect.y, true);
                drawSelectionMark(g, p.getAmbassador(), ambassadorRect.x, ambassadorRect.y, true);
                // B5-0339 assistant state (F10, real mechanic): A+ while the
                // assist bonus is live (assistant rotated), A$ while a sponsor
                // discount is pending. Markers sit over the mini-card's corner.
                boolean ab = p.getAmbassador().isAssistantBonus();
                boolean sd = p.getSponsorDiscount() > 0;
                if (ab || sd) {
                    String mark = (ab ? "A+" : "") + (sd ? "A$" : "");
                    g.setColor(new Color(255, 210, 90));
                    g.setFont(new Font("SansSerif", Font.BOLD, 9));
                    g.drawString(mark, ambassadorRect.x + 46, ambassadorRect.y + 12);
                }
            }
        }

        // B5-0347: agenda slot state + attached aftermaths (B5-0338 registry).
        // Line sits between the IC band and the fleets band, clear of both.
        // B5-4208: track the bottom of this readout so the fleets header can clear it.
        boolean hasAgenda = p.getAgenda() != null;
        // B5-0346 lesson: getAttachedAftermaths returns the LIVE internal list
        // — snapshot before iterating on the paint thread.
        java.util.List<AftermathCard> att =
            new java.util.ArrayList<AftermathCard>(state.getAttachedAftermaths(p));
        int agendaReadoutBottom = y + 306; // default: just above the old fixed fleets header line
        if (hasAgenda || !att.isEmpty()) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 9));
            // B5-0347 probe finding: y+268 sits UNDER the center conflict/outcome
            // banner rect (x w/4..3w/4, y262..296, drawn after zones) — zone 2+ lines
            // were occluded exactly while a conflict banner was up. Draw below the
            // banner band, above the per-participant breakdown lines (y342+).
            int ly = y + 308;
            if (hasAgenda) {
                AgendaCard agenda = p.getAgenda();
                String slot = agenda.isMajorAgenda() ? "MAJOR" : "minor";
                String agendaName = agenda.isFaceDown() ? "[face-down]" : agenda.getTitle();
                String agendaLine = "Agenda (" + slot + "): " + agendaName;
                g.setColor(new Color(220, 180, 60));
                g.drawString(agendaLine, x + 8, ly);
                if (!agenda.isFaceDown() && agenda.isConditionMet(state, p)) {
                    String winMark = " [WIN]";
                    g.setColor(new Color(120, 240, 120));
                    g.drawString(winMark, x + 8 + g.getFontMetrics().stringWidth(agendaLine), ly);
                }
                ly += 11;
            }
            if (!att.isEmpty()) {
                g.setColor(new Color(170, 170, 230));
                String amLine = "Aftermaths attached: " + att.size();
                for (int i = 0; i < att.size() && i < 3; i++) {
                    amLine += ", " + att.get(i).getTitle();
                }
                if (att.size() > 3) amLine += " (…";
                g.drawString(amLine, x + 8, ly);
                ly += 11;
            }
            agendaReadoutBottom = ly; // bottom of the readout (exclusive)
        }

        // Inner circle
        int icTotal = p.getInnerCircle().size();
        int icVisible = bg.visibleCount(zoneIndex, BoardGeometry.ROW_INNER_CIRCLE, icTotal);
        g.setColor(new Color(160, 200, 160));
        g.setFont(new Font("SansSerif", Font.ITALIC, 9));
        g.drawString("Inner Circle:", x + 8, bg.rowY(BoardGeometry.ROW_INNER_CIRCLE) - 4);
        int icDrawn = 0;
        for (int i = 0; i < icVisible; i++) {
            CharacterCard ch = p.getInnerCircle().get(i);
            Rectangle rect = bg.slotRect(zoneIndex, BoardGeometry.ROW_INNER_CIRCLE, i, icTotal);
            drawMiniCard(g, ch, rect.x, rect.y, false);
            drawSelectionMark(g, ch, rect.x, rect.y);
            icDrawn++;
        }
        // B5-0330a: note below the card band — the draft drew it inside the
        // 64px mini-card, colliding with the first card's stats.
        overflowNote(g, icTotal - icDrawn, x, bg.rowY(BoardGeometry.ROW_INNER_CIRCLE));

        // B5-0423: supporting-role chip row — supporting chars render nowhere
        // else, so Use Rotate Effect / Promote had no selectable source. A
        // compact 46x16 chip band sits between the IC row (ends y+239) and the
        // fleets header (y+306), clear of both.
        java.util.List<CharacterCard> supp = p.getSupportingRole();
        int suppTotal = supp.size();
        int suppVisible = bg.visibleCount(zoneIndex, BoardGeometry.ROW_SUPPORT, suppTotal);
        if (suppVisible > 0) {
            g.setFont(new Font("SansSerif", Font.ITALIC, 9));
            g.setColor(new Color(170, 210, 170));
            g.drawString("Support:", x + 8, bg.rowY(BoardGeometry.ROW_SUPPORT) - 4);
            int sx = x + 8;
            for (int i = 0; i < suppVisible; i++) {
                CharacterCard sch = supp.get(i);
                Rectangle rect = bg.slotRect(zoneIndex, BoardGeometry.ROW_SUPPORT, i, suppTotal);
                boolean sel = sch == selectedCard;
                g.setColor(sel ? new Color(30, 90, 40) : new Color(30, 50, 30));
                g.fillRoundRect(rect.x, rect.y, 46, 16, 5, 5);
                g.setColor(sel ? new Color(120, 240, 120) : new Color(120, 150, 120));
                g.setStroke(new BasicStroke(sel ? 2 : 1));
                g.drawRoundRect(rect.x, rect.y, 46, 16, 5, 5);
                String label = sch.getTitle();
                if (label.length() > 9) label = label.substring(0, 8) + "…";
                g.setFont(new Font("SansSerif", Font.PLAIN, 7));
                g.setColor(sel ? new Color(180, 255, 180) : Color.WHITE);
                g.drawString(label, rect.x + 3, rect.y + 11);
                sx += 50;
            }
        }

        // Fleets
        int fleetsTotal = p.getFleets().size();
        int fleetsVisible = bg.visibleCount(zoneIndex, BoardGeometry.ROW_FLEET, fleetsTotal);
        g.setColor(new Color(160, 180, 220));
        // B5-4208: fleets header clears the agenda/aftermath readout above it.
        // The fixed rowY was 310; agenda readout starts at 308 and each line is +11.
        // Place the header at least 2px below the readout bottom, but never above
        // the original fixed position (so plain games are unchanged).
        int fleetsHeaderY = Math.max(bg.rowY(BoardGeometry.ROW_FLEET) - 4, agendaReadoutBottom + 2);
        g.drawString("Fleets:", x + 8, fleetsHeaderY);
        int flDrawn = 0;
        for (int i = 0; i < fleetsVisible; i++) {
            FleetCard fl = p.getFleets().get(i);
            Rectangle rect = bg.slotRect(zoneIndex, BoardGeometry.ROW_FLEET, i, fleetsTotal);
            drawMiniCard(g, fl, rect.x, rect.y, false);
            drawSelectionMark(g, fl, rect.x, rect.y);
            flDrawn++;
        }
        overflowNote(g, fleetsTotal - flDrawn, x, bg.rowY(BoardGeometry.ROW_FLEET));

        // Groups / Locations
        int groupsTotal = p.getGroups().size();
        int locationsTotal = p.getLocations().size();
        int glTotal = groupsTotal + locationsTotal;
        int glVisible = bg.visibleCount(zoneIndex, BoardGeometry.ROW_GROUPS, glTotal);
        g.setColor(new Color(200, 180, 140));
        g.drawString("Groups/Loc:", x + 8, bg.rowY(BoardGeometry.ROW_GROUPS) - 4);
        int glDrawn = 0;
        int glIndex = 0;
        
        // Draw Groups first
        for (int i = 0; i < Math.min(groupsTotal, glVisible); i++) {
            GroupCard gr = p.getGroups().get(i);
            Rectangle rect = bg.slotRect(zoneIndex, BoardGeometry.ROW_GROUPS, glIndex, glTotal);
            drawMiniCard(g, gr, rect.x, rect.y, false);
            drawSelectionMark(g, gr, rect.x, rect.y);
            glDrawn++;
            glIndex++;
        }
        
        // Draw Locations next (continuing where Groups left off)
        for (int i = 0; i < locationsTotal && glIndex < glVisible; i++) {
            LocationCard lc = p.getLocations().get(i);
            Rectangle rect = bg.slotRect(zoneIndex, BoardGeometry.ROW_GROUPS, glIndex, glTotal);
            drawMiniCard(g, lc, rect.x, rect.y, false);
            drawSelectionMark(g, lc, rect.x, rect.y);
            glDrawn++;
            glIndex++;
        }
        
        overflowNote(g, glTotal - glDrawn, x, bg.rowY(BoardGeometry.ROW_GROUPS));

        // Deck count
        g.setColor(Color.LIGHT_GRAY);
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.drawString("Deck: " + p.getDeck().size()
            + "  Hand: " + p.getHand().size(), x + 8, y + h - 10);
    }

    /**
     * B5-0330a: shared "(+ N more)" overflow note, drawn BELOW the row's card
     * band. Mini-cards occupy y0 .. y0+64 and their stat baselines sit at
     * y0+48 / y0+56 (drawMiniCard), so the note's baseline sits at y0+80 —
     * below the card border stroke plus descender clearance. No-op when
     * nothing is hidden.
     */
    private void overflowNote(Graphics2D g, int hiddenCount, int x, int y0) {
        if (hiddenCount <= 0) return;
        g.setColor(new Color(200, 180, 100));
        g.setFont(new Font("SansSerif", Font.PLAIN, 8));
        g.drawString("(+ " + hiddenCount + " more)", x + 8, y0 + 80);
    }

    private void drawMiniCard(Graphics2D g, Card card, int x, int y, boolean large) {
        int w = large ? 60 : 46;
        int h = large ? 84 : 64;

        // B5-4216: dim non-targetable cards in targeting mode
        boolean isTargetable = !targetingMode || isValidTarget(card);
        Color bg = card.isFaceDown() ? new Color(40, 40, 70) : new Color(25, 25, 55);
        if (!isTargetable) {
            bg = new Color(15, 15, 25); // dimmed background
        }
        g.setColor(bg);
        g.fillRoundRect(x, y, w, h, 6, 6);

        Color borderColor = card.isRotated() ? Color.ORANGE
                       : card.isFaceDown() ? Color.DARK_GRAY
                       : new Color(150, 110, 40);
        if (!isTargetable) {
            borderColor = new Color(60, 60, 80); // dimmed border
        }
        g.setColor(borderColor);
        g.setStroke(new BasicStroke(1));
        g.drawRoundRect(x, y, w, h, 6, 6);

        if (!card.isFaceDown()) {
            String abbr = card.getTitle().length() > 12
                ? card.getTitle().substring(0, 11) + "…" : card.getTitle();
            // B5-4216: dim text for non-targetable cards in targeting mode
            Color textColor = isTargetable ? Color.WHITE : new Color(80, 80, 100);
            g.setColor(textColor);
            g.setFont(new Font("SansSerif", Font.PLAIN, 7));
            g.drawString(abbr, x + 2, y + 10);

            g.setFont(new Font("SansSerif", Font.BOLD, 8));
            Color statColor = isTargetable ? new Color(160, 220, 160) : new Color(80, 100, 80);
            g.setColor(statColor);
            if (card instanceof CharacterCard) {
                CharacterCard ch = (CharacterCard) card;
                g.drawString("D" + ch.getDiplomacy() + " I" + ch.getIntrigue(), x + 2, y + h - 18);
                g.drawString("P" + ch.getPsi() + " L" + ch.getLeadership(), x + 2, y + h - 8);
            } else if (card instanceof FleetCard) {
                Color fleetColor = isTargetable ? new Color(160, 180, 220) : new Color(80, 90, 110);
                g.setColor(fleetColor);
                g.drawString("MIL:" + ((FleetCard) card).getMilitary(), x + 2, y + h - 8);
            } else if (card instanceof LocationCard) {
                Color locColor = isTargetable ? new Color(220, 200, 120) : new Color(110, 100, 60);
                g.setColor(locColor);
                g.drawString("+" + ((LocationCard) card).getInfluencePerRound() + " INF", x + 2, y + h - 8);
                // B5-0427: captured-by + effects-suppressed readout from the
                // B5-0376 model API (getCapturedBy, isEffectsSuppressed).
                // Face-up only: a face-down card's identity stays hidden.
                LocationCard loc = (LocationCard) card;
                String capBy = loc.getCapturedBy() != null
                    ? loc.getCapturedBy().getName() : null;
                if (capBy != null && capBy.length() > 6) capBy = capBy.substring(0, 6);
                String mark = (capBy != null ? "CAP:" + capBy : "")
                            + (loc.isEffectsSuppressed()
                               ? (capBy != null ? " " : "") + "SUP" : "");
                if (mark.length() > 0) {
                    Color capColor = isTargetable ? new Color(255, 130, 130) : new Color(130, 65, 65);
                    g.setColor(capColor);
                    g.setFont(new Font("SansSerif", Font.BOLD, 7));
                    g.drawString(mark, x + 2, y + h - 18);
                }
            }
        }

        // B5-0381: the model intentionally exposes a host's contingency count
        // without exposing the face-down card identities. Keep the board
        // readout equally opaque while showing that cards are attached here.
        int contingencyCount = card.getContingencyCount();
        if (contingencyCount > 0) {
            String marker = "C" + contingencyCount;
            Color contColor = isTargetable ? new Color(255, 220, 120) : new Color(128, 110, 60);
            g.setColor(contColor);
            g.setFont(new Font("SansSerif", Font.BOLD, 8));
            g.drawString(marker, x + w - g.getFontMetrics().stringWidth(marker) - 2,
                         y + h - 2);
        }

        // B5-0516: damage-state readout from the B5-0368 Card base-class API
        // (getDamageTokens, getSevereDamageTokens, isNeutralized) so every
        // mini-card qualifies — characters, fleets, groups, locations, and
        // the large ambassador card alike. Face-up only, per the B5-0427
        // captured/suppressed readout precedent: a face-down card's state
        // stays as opaque as its identity (B5-0381 discipline).
        if (!card.isFaceDown()) {
            int dmg = card.getDamageTokens();
            int sev = card.getSevereDamageTokens();
            boolean neutral = card.isNeutralized();
            // Compact form so the marker fits a 46px mini-card and stays clear
            // of the right-aligned B5-0381 C-count badge on the same baseline:
            // "DMG:n+s" (damage, +severe), "NEUT" (neutralized only), or
            // "NEUT n+s" (neutralized at n damage, +s severe).
            String state;
            if (dmg > 0 || sev > 0) {
                state = (neutral ? "NEUT " : "DMG:")
                    + dmg + (sev > 0 ? "+" + sev : "");
            } else {
                state = neutral ? "NEUT" : "";
            }
            if (state.length() > 0) {
                Color dmgColor = isTargetable ? new Color(255, 80, 80) : new Color(128, 40, 40);
                g.setColor(dmgColor);
                g.setFont(new Font("SansSerif", Font.BOLD, 7));
                g.drawString(state, x + 2, y + h - 2);
            }
        }
    }

    private Color factionColor(Player p) {
        switch (p.getFaction()) {
            case HUMAN:    return new Color(100, 160, 220);
            case MINBARI:  return new Color(180, 150, 220);
            case CENTAURI: return new Color(220, 200, 100);
            case NARN:     return new Color(220, 120, 80);
            default:       return Color.WHITE;
        }
    }

    // B5-1971: hover tooltip for board cards. Mirrors HandPanel's tooltip
    // content but without promotion/state context (board cards are in-play).
    private String renderCardTooltip(Card card) {
        if (card == null) return null;
        StringBuilder sb = new StringBuilder("<html><b>");
        sb.append(card.getTitle()).append("</b> (")
          .append(card.getType().toString()).append(")");
        if (card.getCost() > 0) {
            sb.append(" — Cost: ").append(card.getCost()).append(" INF");
        }
        sb.append("<br>Faction: ").append(card.getFaction().toString());
        sb.append(" | Rarity: ").append(card.getRarity().toString());
        String subtype = card.getSubtype();
        if (subtype != null && subtype.length() > 0) {
            sb.append(" | Subtype: ").append(subtype);
        }
        if (card instanceof CharacterCard) {
            CharacterCard ch = (CharacterCard) card;
            sb.append("<br>D").append(ch.getDiplomacy()).append(" I")
              .append(ch.getIntrigue()).append(" P").append(ch.getPsi())
              .append(" L").append(ch.getLeadership());
            if (ch.isAmbassador()) sb.append(" — Ambassador");
        } else if (card instanceof FleetCard) {
            FleetCard fleet = (FleetCard) card;
            sb.append("<br>Military: ").append(fleet.getMilitary());
        } else if (card instanceof ConflictCard) {
            ConflictCard cc = (ConflictCard) card;
            sb.append("<br>").append(cc.getConflictType().toString())
              .append("  +").append(cc.getInfluenceReward()).append(" INF");
        } else if (card instanceof AgendaCard) {
            AgendaCard agenda = (AgendaCard) card;
            sb.append("<br>Agenda (")
              .append(agenda.isMajorAgenda() ? "MAJOR" : "minor").append(")");
            if (agenda.isFaceDown()) sb.append(" [face-down]");
        } else if (card instanceof EnhancementCard) {
            EnhancementCard enh = (EnhancementCard) card;
            boolean first = true;
            if (enh.getDiplomacyBonus() != 0) { sb.append(first ? "<br>Bonuses: " : ", ").append("D").append(enh.getDiplomacyBonus()); first = false; }
            if (enh.getIntrigueBonus() != 0) { sb.append(first ? "<br>Bonuses: " : ", ").append("I").append(enh.getIntrigueBonus()); first = false; }
            if (enh.getPsiBonus() != 0) { sb.append(first ? "<br>Bonuses: " : ", ").append("P").append(enh.getPsiBonus()); first = false; }
            if (enh.getMilitaryBonus() != 0) { sb.append(first ? "<br>Bonuses: " : ", ").append("M").append(enh.getMilitaryBonus()); first = false; }
            if (enh.getLeadershipBonus() != 0) { sb.append(first ? "<br>Bonuses: " : ", ").append("L").append(enh.getLeadershipBonus()); first = false; }
        } else if (card instanceof LocationCard) {
            LocationCard loc = (LocationCard) card;
            sb.append("<br>Influence/round: +").append(loc.getInfluencePerRound()).append(" INF");
        } else if (card instanceof GroupCard) {
            // GroupCard has no special stats beyond what's in text
        } else if (card instanceof EventCard) {
            // EventCard has no special stats beyond what's in text
        } else if (card instanceof AftermathCard) {
            AftermathCard aft = (AftermathCard) card;
            sb.append("<br>Trigger: ").append(aft.getTriggerCondition());
        } else if (card instanceof ContingencyCard) {
            ContingencyCard con = (ContingencyCard) card;
            sb.append("<br>Target: ").append(con.getValidTargetType()).append(" / ").append(con.getValidTargetRace());
            String trigger = con.getTriggerCondition();
            if (trigger != null && trigger.length() > 0) {
                sb.append(" | Trigger: ").append(trigger);
            }
        }
        String text = card.getText();
        if (text != null && text.length() > 0) {
            sb.append("<br><i>").append(text).append("</i>");
        }
        return sb.toString();
    }
}
