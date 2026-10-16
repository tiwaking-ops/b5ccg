package b5ccg.ui;

import b5ccg.engine.DeckLoader;
import b5ccg.engine.GameController;
import b5ccg.engine.RoundEndCallback;
import b5ccg.engine.RulesEngine;
import b5ccg.model.*;
import b5ccg.model.enums.*;
import b5ccg.ui.dialog.ValidatedDialog;
import b5ccg.ui.theme.Theme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class MainWindow extends JFrame implements RoundEndCallback {

    private final GameController  controller;
    private final GameBoardPanel  boardPanel;
    private final HandPanel       handPanel;

    // B5-1969: real-time influence tracker panel (readout only).
    private final InfluenceTrackerPanel influenceTrackerPanel;

    // B5-2277: per-race tension plus per-seat unrest readout (readout only).
    private final TensionUnrestTrackerPanel tensionUnrestTrackerPanel;

    // B5-4155: the join picker, written by B5-2253 and never constructed.
    // It carries Support / Oppose / Pass — the one conflict decision the
    // join window forces — and now appears on its own when that window opens.
    private ConflictJoinPickerPanel joinPickerPanel;
    private Detachable               joinPicker;
    private final JTextArea       logArea;
    private final JLabel          statusLabel;
    private final JButton         passButton;
    private JButton playCardButton;

    // B5-0326 F3: action-set buttons
    private JButton sponsorButton;
    private JButton promoteButton;
    private JComboBox<String> promoteLeaderSelector;
    private JButton buildInfluenceButton;

    // B5-4081: Build Influence leader selector — lists unrotated + actionable IC
    // members so the human chooses which character rotates, closing the
    // stale-leader hole where findUnrotatedIC picked a non-actionable member.
    private JComboBox<String> buildInfluenceLeaderSelector;
    private boolean buildInfluenceSelectorPopulating = false;

    // B5-4164: Group gets its own Sponsor Group control (its dispatch branch
    // is PLAY_CARD -> applyGenericCardPlay, not SPONSOR_CARD), and the
    // existing Sponsor control widens to Enhancement / Location / Fleet via
    // SPONSOR_CARD with the rotating IC member chosen in sponsorLeaderSelector.
    private JButton sponsorGroupButton;
    private JComboBox<String> sponsorLeaderSelector;

    // B5-0380: agenda lifecycle actions for the human player's agenda slot.
    private JButton discardAgendaButton;
    private JButton replaceAgendaButton;
    private JButton revealAgendaButton;

// B5-0381: contingency reveal control. The human player gets a dropdown of
    // their placed (face-down) contingencies plus a Reveal button that submits
    // REVEAL_CONTINGENCY through the existing human-action pipeline.
    private JComboBox<String> contingencySelector;
    private JButton revealContingencyButton;

    // B5-0701: unconditional-surrender availability readout (B5-0661 landed the
    // engine law; nothing in the UI surfaced it). READOUT ONLY in the sense that
    // the engine remains the sole authority: this panel never re-derives
    // legality, it calls RulesEngine.canSurrender and renders the answer. The
    // +3 influence consequence shown here is the rulebook :817 grant, which
    // goes to the TARGET, not to the surrendering player.
    private JComboBox<String> surrenderTargetSelector;
    private JButton          surrenderButton;
    private JLabel           surrenderHintLabel;
    private boolean          surrenderSelectorPopulating = false;

    // B5-2267: voluntary forfeit button (rulebook :815). Unlike surrender,
    // forfeit has no target — the player simply exits the game, discards their
    // ambassador, and the engine awards victory if this leaves a sole survivor.
    // Requires a confirmation dialog because it is irrevocable.
    private JButton          forfeitButton;
    private JLabel           forfeitHintLabel;

    // B5-2267: draw-round buy-cards offer (rulebook :440-:465 step 4).
    // Shows during DRAW phase: the human player may draw additional cards
    // at 3 influence each from their applied pool. The engine auto-buys for
    // all players, but this UI surfaces the offer and shows remaining pool.
    private JLabel           drawRoundBuyLabel;
    private JButton          drawRoundBuyButton;

    // B5-0326 F5: cost preview readout
    private JLabel  costLabel;

    // B5-0348: hand filter/sort controls
    private JCheckBox typeCharBox;
    private JCheckBox typeFleetBox;
    private JCheckBox typeConflictBox;
    private JCheckBox typeAgendaBox;
    private JCheckBox typeAftermathBox;
    private JCheckBox typeEventBox;
    private JCheckBox typeEnhancementBox;
    private JCheckBox typeGroupBox;
    private JCheckBox typeLocationBox;
    private JCheckBox fascHumanBox;
    private JCheckBox fascMinbariBox;
    private JCheckBox fascCentauriBox;
    private JCheckBox fascNarnBox;
    private JCheckBox fascNeutralBox;
    private JCheckBox fascNonAlignedBox;
    private JCheckBox fascVorlonBox;
    private JCheckBox fascAnyBox;
    private JRadioButton sortUnsorted;
    private JRadioButton sortCostAsc;
    private JRadioButton sortCostDesc;
    private ButtonGroup sortGroup;
    private JCheckBox showUnplayable;

    // B5-0325 F2: conflict target selector
    private JComboBox<String> targetSelector;
    private JButton supportButton;
    private JButton opposeButton;
    private Player selectedTarget;
    // B5-0452 (B5-0451 F1): suppresses the target-selector listener while the
    // selector is being (re)populated — a JComboBox auto-selects its first
    // item and fires the listener during population, which set selectedTarget
    // programmatically and satisfied the B5-0325 explicit-target gate without
    // any user action.
    private boolean targetSelectorPopulating = false;

    // The picker below lists only conflicts the engine would let the human
    // initiate right now, and the target dropdown above is now populated from
    // legalConflictTargets() so it can only offer legal targets. Engine is
    // read-only here: every legality question is asked of RulesEngine, never
    // re-derived in the UI.

    // B5-4172 install: live refusal-reason readout. Installed at the bottom of
    // the toolbar so a dark button explains itself without adding a row or
    // crowding the action bar — alignment and baseline unchanged, the real
    // baseline for the row. Names the engine's own predicates and the reason
    // straight out of RulesEngine so a control that is disabled by a rule
    // still tells the human why rather than reading as dead UI. Facts read
    // from RulesEngine (the authority), not transcribed from prose. One writer
    // per scope; this row is ui/ alone and engine/ owns the predicates, which
    // this file only reads.
    //
    // The four controls this ships with were all measured OPEN and untouched
    // by any other writer in this pass, so nothing here collides with an
    // in-flight task. Ownership, not the code, is what keeps this row clean:
    // engine/ reasons live only in RulesEngine, and a reason is not a gate —
    // a disabled control is still a control, not a verdict. */
    private JLabel reasonLabel;
    private JComboBox<String> conflictPickerSelector;
    private JLabel            conflictPickerLabel;
    private final java.util.ArrayList<Card> conflictPickerCards =
        new java.util.ArrayList<Card>();
    private boolean conflictPickerPopulating = false;
    // True when the human deliberately picked the "(no target)" sentinel, i.e.
    // a conflict whose participation does NOT require a declared target. This
    // is an explicit user choice, never the B5-0452 auto-fallback.
    private boolean noTargetChosen = false;

    /** Sentinel items shared with the war/surrender pickers' convention. */
    private static final String NO_TARGET_ITEM   = "(no target)";
    private static final String NO_LEGAL_TARGET = "(no legal targets)";

    // B5-0379: read-only participant list for the human join window. Rows are
    // snapshots of the active Conflict's D14 sides API (B5-0309); the panel
    // never mutates model state — Support/Oppose buttons remain the only
    // commit path (they submit JOIN_CONFLICT_* into the B5-0363 collect).
    private DefaultListModel participantListModel;
    private JList participantList;
    private JLabel joinPromptLabel;

    // B5-0327 F4: split Play/Initiate into separate buttons
    private JButton playCardOnlyButton;
    private JButton initiateConflictButton;

    // B5-0327 F8: initiative order display
    private JLabel initiativeLabel;

    // B5-2261 (B5-0310 F6): phase banner. Renders the CURRENT round phase as
    // one of the five phases the player acts in (READY, CONFLICT, ACTION,
    // AFTERMATH, DRAW) so an all-dark toolbar is distinguishable from a broken
    // one: the player can see it is not their phase rather than guessing.
    // SETUP / MERCENARY / END_ROUND have no human-action window at all and are
    // shown as such rather than folded into a phase that has controls.
    private JLabel phaseLabel;

    // B5-0407: declare-war UI
    private JComboBox<String> warTargetSelector;
    private JButton declareWarButton;
    private JLabel warStatusLabel;

    // B5-0404: mercenary bid control
    private static final String[] BID_AMOUNTS = new String[] { "1", "2", "3", "5", "10" };
    // B5-4175: hand-discard amounts for Draw round step D2 (elective, up to 5).
    private static final String[] HAND_DISCARD_AMOUNTS = new String[] { "1", "2", "3", "4", "5" };
    private JComboBox<String> mercenaryBidAmountSelector;
    private JLabel mercenaryOfferLabel;
    private JLabel mercenaryControllerLabel;
    private JButton mercenaryBidButton;
    // B5-4175: hand-discard amount selector (Draw round step D2).
    private JComboBox<String> handDiscardAmountSelector;
    private JButton handDiscardButton;

    // B5-0487: Censure opponent-fleet target picker. When the human selects an
    // Enhancement FLEET card (Censure-class) from hand, this dropdown lists the
    // opponent factions' face-up fleets; picking one sets the explicit opponent
    // target via the B5-0468 seam before the card is played. If no target is
    // picked (or none are legal), the engine's held-in-play policy applies —
    // no self-fallback onto the owner's own fleet.
    private JComboBox<String> censureTargetSelector;
    private JLabel             censureTargetLabel;
    private JButton            censurePlayButton;
    private final java.util.ArrayList<FleetCard> censureTargetCards = new java.util.ArrayList<FleetCard>();
    private final java.util.ArrayList<String>    censureTargetOwnerNames = new java.util.ArrayList<String>();
    private FleetCard selectedCensureTarget;
    private String    selectedCensureTargetOwner;

    // B5-0522: opponent-CHARACTER target picker (shunned-class today). Mirrors
    // the B5-0487 fleet picker: when the human selects an Enhancement CHARACTER
    // card carrying the B5-0468 explicit-target seam (shunned-class today),
    // this dropdown lists opponent factions' face-up characters (inner circle,
    // supporting role, ambassador — the B5-0506 characterById scope); picking
    // one sets the explicit opponent target via the seam before the card is
    // played. If no target is picked (or none are legal), the engine's
    // held-in-play policy applies — no self-fallback onto the owner's own
    // character. Non-targeted CHARACTER enhancements keep the normal path.
    private JComboBox<String> charCensureTargetSelector;
    private JLabel             charCensureTargetLabel;
    private JButton            charCensurePlayButton;
    private final java.util.ArrayList<CharacterCard> charCensureTargetCards = new java.util.ArrayList<CharacterCard>();
    private final java.util.ArrayList<String>        charCensureTargetOwnerNames = new java.util.ArrayList<String>();
    private CharacterCard selectedCharCensureTarget;
    private String        selectedCharCensureTargetOwner;

    // B5-0401: Tier-1 remainder action UI — Lead Fleet + Use Rotate Effect
    private JButton leadFleetButton;
    private JComboBox<String> leadFleetSelector;
    private FleetCard selectedFleet;
    private JButton useRotateEffectButton;
    private JComboBox<String> rotateEffectKindSelector;
    private CharacterCard selectedAssistant;

    // B5-0402: Tier-3 action UI — Attack + Heal + Repair
    private JButton attackButton;
    private Card selectedAttackTarget;
    private Card selectedWarTarget;
    private Player selectedWarTargetPlayer;
    private LocationCard selectedWarTargetLocation;
    private JButton healButton;
    private JButton repairButton;

    // B5-4158: Aftermath play UI — selector for aftermath cards, target, and play/pass buttons
    private JComboBox<String> aftermathCardSelector;
    private JLabel aftermathCardLabel;
    private JComboBox<String> aftermathTargetSelector;
    private JLabel aftermathTargetLabel;
    private JButton playAftermathButton;
    private JButton passAftermathButton;
    private final java.util.ArrayList<AftermathCard> aftermathCards = new java.util.ArrayList<AftermathCard>();
    private final java.util.ArrayList<Player> aftermathTargets = new java.util.ArrayList<Player>();
    private AftermathCard selectedAftermathCard;
    private Player selectedAftermathTarget;

    private Card selectedCard;

    // B5-4216: targeting mode state
    private boolean targetingMode = false;
    private String targetingAction = null; // "attack", "heal", "repair", "censure", "war", "aftermath", "leadfleet", "rotateeffect"
    private TargetFilter targetingFilter = null;

    private RulesEngine rules;

    // B5-2008: the game setup dialog. Race pick with the rulebook :208
    // one-race-per-player rule, per-seat starting influence, and the
    // rulebook :274 initial board tension readout shown before round 1.
    private JButton gameSetupButton;

    // B5-2004: the Council vote dialog. Enabled only while the engine holds an
    // open agenda-vote session (RulesEngine.isAgendaVoteOpen), which is the
    // phase-independent seam B5-1970 documented for the future
    // GamePhase.AGENDA_VOTE constant -- see refreshCouncilVoteControl below.
    private JButton councilVoteButton;

    // B5-2265: opens the starting-game flow -- faction select, the starting
    // ambassador, the three typed cards beside it, shuffle and cut, and the
    // initiative order that opens the first conflict round. The rules live in
    // StartingGameFlowModel so they are reachable without a display; this
    // button is only the affordance.
    private JButton startingGameButton;

    // B5-2007: opens the Deck Builder dialog for deck construction with
    // live validation feedback (45-card floor, ambassador rule, max-3-copies,
    // faction playability).
    private JButton deckBuilderButton;

    public MainWindow(GameController controller) {
        super("Babylon 5 CCG — Single Player");
        this.controller = controller;
        this.rules = new RulesEngine();

        // B5-4167: register for round-end notifications
        controller.setRoundEndCallback(this);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(4, 4));
        getContentPane().setBackground(new Color(10, 20, 10));

        // ── B5-4155: View menu ─────────────────────────────────────────────
        // Every panel that can float has one checkbox here, so hiding or
        // moving it never strands it off screen: the View menu reopens it.
        // The board and the hand are deliberately absent — see class doc.
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(new Color(10, 20, 10));
        menuBar.setForeground(new Color(180, 200, 180));
        viewMenu = new JMenu("View");
        viewMenu.setBackground(new Color(10, 20, 10));
        viewMenu.setForeground(new Color(180, 200, 180));
        viewMenu.setMnemonic(java.awt.event.KeyEvent.VK_V);
        viewMenu.setToolTipText("Show, hide or detach every panel except the main board and the hand");
        menuBar.add(viewMenu);
        setJMenuBar(menuBar);

        // ── Board ─────────────────────────────────────────────────────────────
        boardPanel = new GameBoardPanel();
        add(boardPanel, BorderLayout.CENTER);

        // ── Right sidebar: log + conflict-type legend (F9) ─────────────────────
        logArea = new JTextArea(10, 24);
        logArea.setEditable(false);
        logArea.setBackground(new Color(10, 15, 30));
        logArea.setForeground(new Color(180, 200, 180));
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(80, 120, 80)),
            "Game Log", 0, 0, new Font("SansSerif", Font.BOLD, 10),
            new Color(180, 200, 180)));
        logScroll.getViewport().setBackground(new Color(10, 15, 30));

        // B5-0379: right sidebar hosts the join-window participant list plus
        // the log and the legend. One container only — BorderLayout.EAST holds
        // a single child, so the three panels stack inside this box.
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(new Color(10, 20, 10));

        // B5-0379: read-only participant list. Refreshed from refresh() on the
        // EDT via refreshParticipantList(); never writes model state — the
        // Support/Oppose buttons stay the sole commit path into the B5-0363
        // offer-collect sequence.
        joinPromptLabel = new JLabel("No active conflict.");
        joinPromptLabel.setForeground(new Color(220, 200, 120));
        joinPromptLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        joinPromptLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        participantListModel = new DefaultListModel();
        participantList = new JList(participantListModel);
        participantList.setEnabled(false);
        participantList.setFocusable(false);
        participantList.setBackground(new Color(10, 15, 30));
        participantList.setForeground(new Color(180, 200, 180));
        participantList.setFont(new Font("Monospaced", Font.PLAIN, 10));
        participantList.setVisibleRowCount(8);
        JScrollPane participantScroll = new JScrollPane(participantList);
        participantScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        participantScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(80, 120, 80)),
            "Conflict Participants", 0, 0, new Font("SansSerif", Font.BOLD, 10),
            new Color(180, 200, 180)));
        participantScroll.getViewport().setBackground(new Color(10, 15, 30));
        JPanel joinPanel = new JPanel();
        joinPanel.setLayout(new BoxLayout(joinPanel, BoxLayout.Y_AXIS));
        joinPanel.setBackground(new Color(10, 20, 10));
        joinPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        joinPromptLabel.setMaximumSize(new Dimension(280, 20));
        participantScroll.setMaximumSize(new Dimension(280, 140));
        participantScroll.setPreferredSize(new Dimension(280, 140));
        joinPanel.add(joinPromptLabel);
        joinPanel.add(participantScroll);
        sidebar.add(joinPanel);
        // B5-4155: detachable — default OFF so the always-visible frame fits
        // 1366 wide; reopen from View > Conflict Participants or it floats.
        registerDetachable("Conflict Participants", joinPanel, sidebar, null, false);

        // B5-1969: real-time influence tracker. Readout only -- it renders the
        // model's own Influence/Power and the Standard-Victory threshold, and
        // never re-derives victory. Updated from refresh() below.
        influenceTrackerPanel = new InfluenceTrackerPanel();
        sidebar.add(influenceTrackerPanel);
        registerDetachable("Influence Tracker", influenceTrackerPanel, sidebar, null, true);

        // B5-2277: tension + unrest readout. Like the influence tracker it renders
        // the model's own values and raises nothing, so it is not a second
        // authority on either axis.
        tensionUnrestTrackerPanel = new TensionUnrestTrackerPanel();
        sidebar.add(tensionUnrestTrackerPanel);
        registerDetachable("Tension / Unrest", tensionUnrestTrackerPanel, sidebar, null, true);

        sidebar.add(logScroll);
        // B5-4155: detachable — default OFF (reference panel, not a live
        // readout); reopen from View > Game Log or float it beside the board.
        registerDetachable("Game Log", logScroll, sidebar, null, false);

        // B5-0329 F9: conflict-type Ã¢â€ â€™ ability legend
        JPanel legendPanel = new JPanel(new GridLayout(4, 2, 4, 2));
        legendPanel.setBackground(new Color(10, 20, 10));
        legendPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(80, 130, 80)),
            "Conflict Types Ã¢â€ â€™ Abilities", 0, 0, new Font("SansSerif", Font.BOLD, 9),
            new Color(180, 200, 180)));
        String[][] legendData = {
            {"DIPLOMACY", "Diplomacy"},
            {"INTRIGUE",  "Intrigue"},
            {"MILITARY",  "Military (Fleets)"},
            {"PSI",       "Psi"}
        };
        for (String[] row : legendData) {
            JLabel typeLabel = new JLabel(row[0]);
            typeLabel.setForeground(new Color(220, 180, 100));
            typeLabel.setFont(new Font("Monospaced", Font.BOLD, 10));
            JLabel abilLabel = new JLabel("Ã¢â€ â€™ " + row[1]);
            abilLabel.setForeground(new Color(180, 200, 180));
            abilLabel.setFont(new Font("Monospaced", Font.PLAIN, 10));
            legendPanel.add(typeLabel);
            legendPanel.add(abilLabel);
        }
        legendPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        legendPanel.setMaximumSize(new Dimension(280, 120));
        sidebar.add(legendPanel);
        registerDetachable("Conflict Types", legendPanel, sidebar, null, true);
        add(sidebar, BorderLayout.EAST);

        // ── Top toolbar (B5-2375 split rows, B5-2377 primary row) ───────────────

        statusLabel = new JLabel("InitialisingÃ¢â‚¬Â¦");
        statusLabel.setForeground(new Color(200, 220, 200));
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

        // B5-0327 F8: initiative order display
        initiativeLabel = new JLabel("Initiative: —");
        initiativeLabel.setForeground(new Color(180, 200, 220));
        initiativeLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));

        // B5-2261 (B5-0310 F6): the phase banner sits immediately before the
        // initiative readout so the two facts the player needs before touching
        // anything — which phase, whose turn — are adjacent in the toolbar.
        phaseLabel = new JLabel("Phase: —");
        phaseLabel.setForeground(new Color(220, 200, 140));
        phaseLabel.setFont(new Font("Monospaced", Font.BOLD, 11));
        phaseLabel.setToolTipText(
            "Round phase. READY/CONFLICT/ACTION/AFTERMATH/DRAW are the phases "
            + "you act in; every other control is dark in the other phases "
            + "(B5-0310 F6, rulebook III 'Each turn, play progresses through "
            + "the following rounds').");

        passButton = makeButton("Pass Turn", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (MainWindow.this.controller.isWaitingForHumanConflictAttack()) {
                    clearSelection();
                }
                MainWindow.this.controller.submitHumanAction(GameAction.pass());
            }
        });

        // B5-0328 F4: split Play/Initiate into two separate buttons, each with
        // its own dispatch so a conflict card can never fire playCard().
        playCardOnlyButton = makeButton("Play Card", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MainWindow.this.playOnly();
            }
        });
        playCardOnlyButton.setEnabled(false);

        initiateConflictButton = makeButton("Initiate Conflict", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MainWindow.this.initiateOnly();
            }
        });
        initiateConflictButton.setEnabled(false);
        // B5-2253 (B5-0310 F2): the target dropdown has no label of its own
        // where it sits in the toolbar, so the button names the requirement.
        initiateConflictButton.setToolTipText("Declare the selected conflict. "
            + "Requires a target picked in the dropdown for conflicts whose "
            + "participation demands one; conflicts that need none declare "
            + "against the '(no target)' row (B5-2253).");

        // B5-0326 F3: sponsor / promote / build-influence buttons
        // B5-4164: the single Sponsor control now covers BOTH sponsor routes.
        // A selected CharacterCard keeps the B5-2257 recruit path; a selected
        // Enhancement, Location or Fleet routes to SPONSOR_CARD with the
        // leader chosen in sponsorLeaderSelector, so a lit button dispatches
        // with everything executeSponsorCard re-checks already true.
        sponsorButton = makeButton("Sponsor", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // B5-2257: humanPlayer() is read ONCE and null-checked. The
                // pre-B5-2257 form called rules.canRecruit(humanPlayer(), ch)
                // unguarded, and canRecruit dereferences p on its first line
                // (RulesEngine:453, p.getHand()), so a click with no human seat
                // was a latent NPE on the EDT.
                Player hp = humanPlayer();
                Card sel = selectedCard;
                if (hp == null || sel == null) return;
                if (sel instanceof CharacterCard) {
                    CharacterCard ch = (CharacterCard) sel;
                    if (rules.canRecruit(hp, ch)) {
                        MainWindow.this.controller.submitHumanAction(
                            GameAction.recruitCharacter(ch));
                    } else {
                        // B5-2257: a refusal used to be a SILENT no-op. The
                        // engine logs its own refusal, but the player is
                        // looking at a toolbar, not at the log. This branch is
                        // reachable only on a stale render.
                        statusLabel.setText("Cannot sponsor " + ch.getTitle()
                            + " — needs " + rules.sponsorCost(hp, ch).getAmount()
                            + " INF applied; pool has " + hp.getAppliedPool() + ".");
                    }
                } else if (sel instanceof EnhancementCard
                        || sel instanceof LocationCard
                        || sel instanceof FleetCard) {
                    Card c = sel;
                    CharacterCard leader = resolveSponsorLeader(hp);
                    if (leader == null) {
                        statusLabel.setText("Cannot sponsor " + c.getTitle()
                            + " — choose an Inner Circle member in the "
                            + "sponsor-leader dropdown to rotate.");
                    } else if (!rules.canSponsorCard(hp, c)) {
                        // Mirror the engine's own refusal reasons
                        // (RulesEngine.executeSponsorCard): not in hand,
                        // restricted race (:664/:665), or pool short.
                        if (!hp.getHand().contains(c)) {
                            statusLabel.setText("Cannot sponsor " + c.getTitle()
                                + " — it is no longer in hand.");
                        } else if (!c.getFaction().isPlayableBy(hp.getFaction())) {
                            statusLabel.setText("Cannot sponsor " + c.getTitle()
                                + " — a race-restricted card is playable only "
                                + "by its own race (:664/:665).");
                        } else {
                            statusLabel.setText("Cannot sponsor " + c.getTitle()
                                + " — needs " + rules.sponsorshipCost(hp, c)
                                + " INF applied; pool has "
                                + hp.getAppliedPool() + ".");
                        }
                    } else {
                        MainWindow.this.controller.submitHumanAction(
                            GameAction.sponsorCard(c, leader));
                    }
                }
            }
        });
        sponsorButton.setEnabled(false);
        sponsorButton.setToolTipText("Sponsor the selected supporting character, "
            + "or — for a selected Enhancement, Location or Fleet in hand — "
            + "rotate the Inner Circle member chosen in the sponsor-leader "
            + "dropdown to bring it into play (B5-4164).");

        // B5-4164: Group gets its own control because its dispatch branch is
        // PLAY_CARD -> applyGenericCardPlay (GameController:1273-1286), not
        // SPONSOR_CARD — no leader rotates for a group (:496 route). The lit
        // state is rules.canSponsorGroup, the exact predicate that branch
        // re-checks, so a lit button can never refuse on click.
        sponsorGroupButton = makeButton("Sponsor Group", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Player hp = humanPlayer();
                Card sel = selectedCard;
                if (hp == null || !(sel instanceof GroupCard)) return;
                GroupCard grp = (GroupCard) sel;
                if (rules.canSponsorGroup(hp, grp)) {
                    MainWindow.this.controller.submitHumanAction(
                        GameAction.playCard(grp));
                } else if (!hp.getHand().contains(grp)) {
                    statusLabel.setText("Cannot sponsor " + grp.getTitle()
                        + " — it is no longer in hand.");
                } else if (!grp.getFaction().isPlayableBy(hp.getFaction())) {
                    statusLabel.setText("Cannot sponsor " + grp.getTitle()
                        + " — a race-restricted group is playable only by its "
                        + "own race (:496).");
                } else {
                    statusLabel.setText("Cannot sponsor " + grp.getTitle()
                        + " — needs " + rules.groupSponsorshipCost(grp)
                        + " INF applied; pool has " + hp.getAppliedPool() + ".");
                }
            }
        });
        sponsorGroupButton.setEnabled(false);
        sponsorGroupButton.setToolTipText("Sponsor the selected Group into play "
            + "(rulebook :496).");

        promoteButton = makeButton("Promote", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // B5-2257: same single-read null-check as Sponsor. The
                // pre-B5-2257 form called findUnrotatedIC(hp) with an
                // unchecked hp. B5-4149: use the leader selector so the human
                // chooses which unrotated + actionable IC member backs the
                // promotion, closing the stale-leader hole.
                Player hp = humanPlayer();
                CharacterCard ch = (selectedCard instanceof CharacterCard)
                    ? (CharacterCard) selectedCard : null;
                CharacterCard leader = (hp == null) ? null : resolvePromoteLeader(hp);
                if (hp != null && ch != null) {
                    if (leader == null) {
                        statusLabel.setText("Cannot promote " + ch.getTitle()
                            + " — no actionable leader to back it.");
                        promoteButton.setToolTipText("Select a supporting-role "
                            + "character to enable Promote.");
                    } else if (!rules.canPromote(hp, ch)) {
                        int need = rules.promotionCost(hp, ch);
                        int have = hp.getAppliedPool();
                        if (have < need) {
                            statusLabel.setText("Cannot promote " + ch.getTitle()
                                + " — short pool; needs " + need
                                + " INF applied, pool has " + have + ".");
                        } else {
                            statusLabel.setText("Cannot promote " + ch.getTitle()
                                + " — chosen leader or card not eligible for promotion.");
                        }
                        promoteButton.setToolTipText("Select a supporting-role "
                            + "character to enable Promote.");
                    } else {
                        MainWindow.this.controller.submitHumanAction(
                            GameAction.promoteCharacter(ch, leader));
                        promoteButton.setToolTipText("Promote the selected "
                            + "supporting-role character.");
                    }
                }
            }
        });
        promoteButton.setEnabled(false);

        // B5-4149: promote leader selector — lists unrotated + actionable IC members
        // so the human can choose which leader backs the promotion, closing the
        // stale-leader hole where findUnrotatedIC picked a member that
        // executePromote would refuse (RulesEngine:294). Mirrors the B5-4081
        // Build Influence selector pattern.
        promoteLeaderSelector = new JComboBox<String>(new String[] { "(select leader)" });
        promoteLeaderSelector.setEnabled(false);
        promoteLeaderSelector.setMaximumSize(new Dimension(180, 24));
        promoteLeaderSelector.setToolTipText("Choose an unrotated Inner Circle character to back the promotion.");

        // B5-4164: sponsor leader selector — the B5-4149 selector pattern
        // applied to the widened Sponsor control: an Enhancement, Location or
        // Fleet needs an Inner Circle member to rotate (:657), and the human
        // chooses which one rotates.
        sponsorLeaderSelector = new JComboBox<String>(new String[] { "(select leader)" });
        sponsorLeaderSelector.setEnabled(false);
        sponsorLeaderSelector.setMaximumSize(new Dimension(180, 24));
        sponsorLeaderSelector.setToolTipText("Choose an unrotated Inner Circle character to rotate when sponsoring an Enhancement, Location or Fleet.");

        // B5-4081: Build Influence leader selector — picks the IC member that
        // rotates. Populated from the engine's canBuildInfluence verdict so the
        // dropdown can never offer a rotated or neutralized member.
        buildInfluenceLeaderSelector = new JComboBox<String>(new String[] { "(select leader)" });
        buildInfluenceLeaderSelector.setEnabled(false);
        buildInfluenceLeaderSelector.setMaximumSize(new Dimension(180, 24));
        buildInfluenceLeaderSelector.setToolTipText("Choose an unrotated Inner Circle character to build influence with.");
        buildInfluenceLeaderSelector.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (buildInfluenceSelectorPopulating) return;
                refreshBuildInfluenceButton();
            }
        });

        buildInfluenceButton = makeButton("Build Influence", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Player hp = humanPlayer();
                CharacterCard leader = resolveBuildInfluenceLeader();
                if (leader == null && hp != null) {
                    // Stale selector fallback: scan for the first unrotated +
                    // actionable IC member — the same predicate the engine uses
                    // in canBuildInfluence (RulesEngine:190-192).
                    for (CharacterCard ic : hp.getInnerCircle()) {
                        if (!ic.isRotated() && !ic.isFaceDown()
                                && ic.canActAfterNeutralization()) {
                            leader = ic;
                            break;
                        }
                    }
                }
                if (leader != null && rules.canBuildInfluence(hp)) {
                    MainWindow.this.controller.submitHumanAction(
                        GameAction.buildInfluence(leader));
                }
            }
        });
        buildInfluenceButton.setEnabled(false);

        // B5-0701: surrender availability. Target-first, then commit: surrender
        // needs a TARGET player, so a single button with no selector would have
        // nothing legal to submit. The selector lists only players the engine
        // currently accepts, so an empty list IS the "not available" signal.
        surrenderTargetSelector = new JComboBox<String>();
        surrenderTargetSelector.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (surrenderSelectorPopulating) return;
                refreshSurrenderButton();
            }
        });
        surrenderTargetSelector.setEnabled(false);

        surrenderButton = makeButton("Surrender", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Player hp = humanPlayer();
                Player target = selectedSurrenderTarget();
                if (hp == null || target == null) return;
                // Engine is the authority: re-check at the commit point, exactly
                // as the sponsor/promote/build-influence handlers do, so a stale
                // enabled button can never submit an illegal action.
                if (rules.canSurrender(hp, target,
                        MainWindow.this.controller.getState())) {
                    MainWindow.this.controller.submitHumanAction(
                        GameAction.surrender(target));
                }
            }
        });
        surrenderButton.setEnabled(false);
        surrenderButton.setToolTipText(
            "Unconditionally surrender (rulebook :815). You go out of the game; "
            + "the target gains 3 influence and receives your ambassador as an "
            + "asylum character.");

        surrenderHintLabel = new JLabel(" ");
        // NOTE: added to the toolbar in the assembly block below, beside the
        // other B5-0407-style target+button pairs, not here.

        // B5-2267: forfeit button — no target needed, but requires confirmation.
        forfeitButton = makeButton("Forfeit", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Player hp = humanPlayer();
                if (hp == null) return;
                // Confirmation dialog — forfeit is irrevocable and discards ambassador.
                int confirm = JOptionPane.showConfirmDialog(
                    MainWindow.this,
                    "Forfeit the game?\n\n"
                    + "You will exit the game immediately.\n"
                    + "Your ambassador is discarded (not transferred).\n"
                    + "If this leaves a sole survivor, they win.\n\n"
                    + "This action cannot be undone.",
                    "Confirm Forfeit",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) return;
                // Engine is the authority: re-check at commit point.
                if (rules.canForfeit(hp, MainWindow.this.controller.getState())) {
                    MainWindow.this.controller.submitHumanAction(
                        GameAction.forfeit());
                }
            }
        });
        forfeitButton.setEnabled(false);
        forfeitButton.setToolTipText(
            "Voluntarily forfeit the game (rulebook :815). You exit immediately; "
            + "your ambassador is discarded. If this leaves a sole survivor, they win.");

        forfeitHintLabel = new JLabel(" ");

        // B5-2267: draw-round buy-cards offer. Visible only during DRAW phase.
        drawRoundBuyLabel = new JLabel(" ");
        drawRoundBuyLabel.setForeground(new Color(200, 220, 140));
        drawRoundBuyLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        drawRoundBuyButton = makeButton("Buy Card (3 INF)", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Player hp = humanPlayer();
                if (hp == null) return;
                GameState state = MainWindow.this.controller.getState();
                // Only allow during DRAW phase
                if (state.getPhase() != GamePhase.DRAW) return;
                // Engine is the authority: exactly one card for exactly 3
                // applied influence, re-checked at commit (B5-4165). The
                // engine narrates the purchase once; this handler must not
                // add a duplicate line. The engine's runDrawPhase poll
                // re-notifies the UI so the control re-dims below 3.
                rules.buyMoreCards(hp, state);
            }
        });
        drawRoundBuyButton.setEnabled(false);
        drawRoundBuyButton.setToolTipText(
            "Draw an additional card for 3 influence from your applied pool "
            + "(rulebook :440-:465, draw round step 4). Available during the Draw Round.");

        discardAgendaButton = makeButton("Discard Agenda", new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                Player hp = humanPlayer();
                if (hp != null && rules.canDiscardAgenda(hp)) {
                    MainWindow.this.controller.submitHumanAction(
                        GameAction.discardAgenda(hp.getAgenda()));
                }
            }
        });
        discardAgendaButton.setEnabled(false);
        discardAgendaButton.setToolTipText("Minor agendas may be discarded; Major agendas must be replaced.");

        replaceAgendaButton = makeButton("Replace Agenda", new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                Player hp = humanPlayer();
                CharacterCard leader = hp == null ? null : findUnrotatedIC(hp);
                if (hp != null && selectedCard instanceof AgendaCard
                        && rules.canReplaceAgenda(hp, (AgendaCard) selectedCard, leader)) {
                    MainWindow.this.controller.submitHumanAction(
                        GameAction.replaceAgenda((AgendaCard) selectedCard, leader));
                    clearSelection();
                }
            }
        });
        replaceAgendaButton.setEnabled(false);
        replaceAgendaButton.setToolTipText("Select an eligible agenda in hand; an Inner Circle character rotates.");

        revealAgendaButton = makeButton("Reveal Agenda", new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                Player hp = humanPlayer();
                if (hp != null && rules.canRevealAgenda(hp)) {
                    MainWindow.this.controller.submitHumanAction(
                        GameAction.revealAgenda(hp.getAgenda()));
                }
            }
        });
        revealAgendaButton.setEnabled(false);
        revealAgendaButton.setToolTipText("Reveal your face-down agenda; it takes effect now.");

        // B5-0381: reveal control for face-down contingencies placed under in-play hosts.
        // The selector lists the human player's unrevealed contingencies via B5-0394
        // accessors (GameState.getPlacedContingencies); the Reveal button submits
        // REVEAL_CONTINGENCY for the selected one through the existing human pipeline.
        String[] empty = new String[] { "(none)" };
        contingencySelector = new JComboBox<String>(empty);
        contingencySelector.setEnabled(false);
        contingencySelector.setMaximumSize(new Dimension(180, 24));

        revealContingencyButton = makeButton("Reveal Contingency", new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                Player hp = humanPlayer();
                if (hp == null) return;
                GameState gs = MainWindow.this.controller.getState();
                java.util.List<ContingencyCard> placed = gs.getPlacedContingencies(hp);
                if (placed.isEmpty()) return;
                int sel = contingencySelector.getSelectedIndex();
                if (sel < 0 || sel >= placed.size()) return;
                ContingencyCard cc = placed.get(sel);
                if (rules.canRevealContingency(hp, cc)) {
                    MainWindow.this.controller.submitHumanAction(
                        GameAction.revealContingency(cc));
                }
            }
        });
        revealContingencyButton.setEnabled(false);
        revealContingencyButton.setToolTipText("Reveal a face-down contingency placed under one of your in-play hosts.");

        // B5-0401: Lead Fleet control — select a ready character (IC or supporting)
        // and an unrotated fleet to rotate the character and lead the fleet.
        leadFleetSelector = new JComboBox<String>(new String[] { "(select fleet)" });
        leadFleetSelector.setEnabled(false);
        leadFleetSelector.setMaximumSize(new Dimension(180, 24));
        leadFleetSelector.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (leadFleetSelector.getSelectedItem() != null) {
                    String name = (String) leadFleetSelector.getSelectedItem();
                    Player hp = humanPlayer();
                    if (hp != null) {
                        for (FleetCard fl : hp.getFleets()) {
                            if (fl.getTitle().equals(name)) {
                                selectedFleet = fl;
                                break;
                            }
                        }
                    }
                    GameState gs = MainWindow.this.controller.getState();
                    boolean actionTurn = gs.getPhase() == GamePhase.ACTION
                        && gs.getActivePlayer() == hp && MainWindow.this.controller.isWaitingForHuman();
                    updateLeadFleetButton(actionTurn);
                }
            }
        });

        leadFleetButton = makeButton("Lead Fleet", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Player hp = humanPlayer();
                CharacterCard leader = (selectedCard instanceof CharacterCard)
                    ? (CharacterCard) selectedCard : null;
                if (hp != null && leader != null && selectedFleet != null
                        && rules.canLeadFleet(hp, leader, selectedFleet)) {
                    MainWindow.this.controller.submitHumanAction(
                        GameAction.leadFleet(leader, selectedFleet));
                    clearSelection();
                }
            }
        });
        leadFleetButton.setEnabled(false);
        leadFleetButton.setToolTipText("Rotate a ready IC/supporting character to lead an unrotated fleet (B5-0362).");

        // B5-0401: Use Rotate Effect control — select an assistant and effect kind.
        rotateEffectKindSelector = new JComboBox<String>(
            new String[] { "Ability Boost (+1 D/I/L)", "Sponsor Discount (-1 INF)" });
        rotateEffectKindSelector.setEnabled(false);
        rotateEffectKindSelector.setMaximumSize(new Dimension(180, 24));

        useRotateEffectButton = makeButton("Use Rotate Effect", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Player hp = humanPlayer();
                CharacterCard assistant = (selectedAssistant instanceof CharacterCard)
                    ? (CharacterCard) selectedAssistant : null;
                if (hp != null && assistant != null) {
                    GameAction.RotateEffectKind kind = rotateEffectKindSelector.getSelectedIndex() == 0
                        ? GameAction.RotateEffectKind.USE_ABILITY_BOOST
                        : GameAction.RotateEffectKind.USE_SPONSOR_DISCOUNT;
                    CharacterCard amb = hp.getAmbassador();
                    if (rules.canUseRotateEffect(hp, assistant, amb, kind)) {
                        MainWindow.this.controller.submitHumanAction(
                            GameAction.useRotateEffect(assistant, amb, kind));
                        clearSelection();
                    }
                }
            }
        });
        useRotateEffectButton.setEnabled(false);
        useRotateEffectButton.setToolTipText("Rotate a ready supporting assistant to boost ambassador or reduce sponsor cost (B5-0366).");

        // B5-0325 F2: conflict target selector
        targetSelector = new JComboBox<String>();
        targetSelector.setEnabled(false);
        targetSelector.setMaximumSize(new Dimension(180, 24));
        // B5-2253 (B5-0310 F2): the dropdown names itself, so the only signal
        // it was a target picker is not transient status-bar prose.
        targetSelector.setToolTipText("Target for the selected conflict. Only "
            + "targets the engine will accept are listed (B5-2253).");
        targetSelector.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // B5-0452 (B5-0451 F1): ignore events fired by programmatic
                // population — only a real user change may set the target.
                if (targetSelectorPopulating) return;
                Object item = targetSelector.getSelectedItem();
                // B5-2253: a null pick must CLEAR a previous target. The old
                // listener left selectedTarget untouched when the selection was
                // null or a sentinel, so a stale player survived a re-pick and
                // Initiate fired against a target no longer on offer.
                if (item == null) {
                    selectedTarget = null;
                    noTargetChosen = false;
                    updatePlayInitiateButtons();
                    return;
                }
                String name = (String) item;
                if (NO_TARGET_ITEM.equals(name) || NO_LEGAL_TARGET.equals(name)
                        || "(select target)".equals(name)) {
                    selectedTarget = null;
                    noTargetChosen = NO_TARGET_ITEM.equals(name);
                    updatePlayInitiateButtons();
                    return;
                }
                selectedTarget = null;
                noTargetChosen = false;
                for (Player p : MainWindow.this.controller.getState().getPlayers()) {
                    if (!p.isHuman() && p.getName().equals(name)) {
                        selectedTarget = p;
                        break;
                    }
                }
                // B5-2253: the list is legal-targets-only, but the underlying
                // player set can change under a live selector (a player passes,
                // a POWER source drops them below the B5-0677 target gate).
                // Re-ask the engine for THIS pair and drop an illegal pick
                // rather than letting Initiate dispatch it.
                if (selectedTarget != null && !isLegalConflictTarget(selectedTarget)) {
                    selectedTarget = null;
                    statusLabel.setText("Selected: "
                        + ((selectedCard == null) ? "" : selectedCard.getTitle())
                        + "  |  Target: " + name
                        + " is not a legal target right now.");
                }
                updatePlayInitiateButtons();
            }
        });

        // B5-2253 (B5-0310 F2): the conflict picker. Lists the in-hand conflicts
        // the engine would let the human initiate this turn and nothing else;
        // picking one selects it exactly as a HandPanel click would.
        conflictPickerLabel = new JLabel("Declare conflict:");
        conflictPickerLabel.setForeground(new Color(200, 220, 200));
        conflictPickerLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        conflictPickerLabel.setMaximumSize(new Dimension(120, 24));

        conflictPickerSelector = new JComboBox<String>();
        conflictPickerSelector.setEnabled(false);
        conflictPickerSelector.setMaximumSize(new Dimension(200, 24));
        conflictPickerSelector.setToolTipText("Conflict cards in your hand you "
            + "can initiate right now. Picking one declares it as the selected "
            + "card (B5-2253).");
        conflictPickerSelector.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (conflictPickerPopulating) return;
                int index = conflictPickerSelector.getSelectedIndex();
                if (index < 0 || index >= conflictPickerCards.size()) return;
                Card chosen = conflictPickerCards.get(index);
                if (chosen == selectedCard) return;
                MainWindow.this.applyCardSelection(chosen, true);
            }
        });

        // B5-0325 F1: support / oppose join buttons. RESTORED by the B5-4161
        // triage pass (2026-10-13): a half-finished edit had deleted these two
        // initialisations while leaving the toolbar add() and the refresh()
        // enable toggle in place, so MainWindow's constructor threw an NPE.
        // The card-targeted replacement for this prompt is B5-4161 proper and
        // is still gated on B5-4160 -- see the triage report before removing
        // these again, and remove the add() and setEnabled() consumers too.
        supportButton = makeButton("Support", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MainWindow.this.controller.submitHumanAction(GameAction.joinSupport());
            }
        });
        supportButton.setEnabled(false);
        supportButton.setToolTipText("Join the active conflict on the support side. "
            + "A dark button says why it is off (no active conflict, already joined, no actions left, passed).");
        opposeButton = makeButton("Oppose", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MainWindow.this.controller.submitHumanAction(GameAction.joinOppose());
            }
        });
        opposeButton.setEnabled(false);
        opposeButton.setToolTipText("Join the active conflict on the oppose side. "
            + "A dark button says why it is off (no active conflict, already joined, no actions left, passed).");

        // B5-0402: Tier-3 action UI — Attack + Heal + Repair
        attackButton = makeButton("Attack", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final Player hp = humanPlayer();
                final Conflict conflict = MainWindow.this.controller.getState().getActiveConflict();
                if (!MainWindow.this.controller.isWaitingForHumanConflictAttack()
                        || hp == null || conflict == null || selectedCard == null) return;
                final Card attacker = selectedCard;
                // B5-4216: if a target is already selected via targeting mode, submit
                if (selectedAttackTarget != null
                        && rules.canAttackConflictParticipant(hp, attacker, selectedAttackTarget, conflict)) {
                    MainWindow.this.controller.submitHumanAction(
                        GameAction.attackConflictParticipant(attacker, selectedAttackTarget));
                    clearSelection();
                } else if (!targetingMode) {
                    // B5-4216: enter targeting mode to pick an attack target
                    enterTargetingMode("attack", new TargetFilter() {
                        @Override public boolean isTarget(Card t) {
                            return rules.canAttackConflictParticipant(hp, attacker, t, conflict);
                        }
                    });
                }
            }
        });
        attackButton.setEnabled(false);
        attackButton.setToolTipText("Attack a selected participant in the live conflict window (B5-0370).");


        healButton = makeButton("Heal", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final Player hp = humanPlayer();
                // B5-4216: if a target was selected via targeting mode, use it
                if (targetingMode && selectedCard instanceof CharacterCard) {
                    CharacterCard ch = (CharacterCard) selectedCard;
                    if (rules.canHealCharacter(hp, ch)) {
                        MainWindow.this.controller.submitHumanAction(
                            GameAction.healCharacter(ch));
                        clearSelection();
                        return;
                    }
                }
                if (hp != null && selectedCard instanceof CharacterCard) {
                    CharacterCard ch = (CharacterCard) selectedCard;
                    if (rules.canHealCharacter(hp, ch)) {
                        MainWindow.this.controller.submitHumanAction(
                            GameAction.healCharacter(ch));
                        clearSelection();
                    } else if (!targetingMode) {
                        enterTargetingMode("heal", new TargetFilter() {
                            @Override public boolean isTarget(Card t) {
                                return t instanceof CharacterCard
                                    && rules.canHealCharacter(hp, (CharacterCard) t);
                            }
                        });
                    }
                } else if (!targetingMode && hp != null) {
                    enterTargetingMode("heal", new TargetFilter() {
                        @Override public boolean isTarget(Card t) {
                            return t instanceof CharacterCard
                                && rules.canHealCharacter(hp, (CharacterCard) t);
                        }
                    });
                }
            }
        });
        healButton.setEnabled(false);
        healButton.setToolTipText("Rotate to heal a damaged IC/supporting character (B5-0371).");

        repairButton = makeButton("Repair", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final Player hp = humanPlayer();
                // B5-4216: if a target was selected via targeting mode, use it
                if (targetingMode && selectedCard != null) {
                    if (selectedCard instanceof FleetCard) {
                        FleetCard fl = (FleetCard) selectedCard;
                        if (rules.canRepairCard(hp, fl)) {
                            MainWindow.this.controller.submitHumanAction(
                                GameAction.repairCard(fl));
                            clearSelection();
                            return;
                        }
                    } else if (selectedCard instanceof LocationCard) {
                        LocationCard loc = (LocationCard) selectedCard;
                        if (rules.canRepairCard(hp, loc)) {
                            MainWindow.this.controller.submitHumanAction(
                                GameAction.repairCard(loc));
                            clearSelection();
                            return;
                        }
                    }
                }
                if (hp != null && selectedCard instanceof FleetCard) {
                    FleetCard fl = (FleetCard) selectedCard;
                    if (rules.canRepairCard(hp, fl)) {
                        MainWindow.this.controller.submitHumanAction(
                            GameAction.repairCard(fl));
                        clearSelection();
                    } else if (!targetingMode) {
                        enterTargetingMode("repair", new TargetFilter() {
                            @Override public boolean isTarget(Card t) {
                                if (t instanceof FleetCard) return rules.canRepairCard(hp, (FleetCard) t);
                                if (t instanceof LocationCard) return rules.canRepairCard(hp, (LocationCard) t);
                                return false;
                            }
                        });
                    }
                } else if (hp != null && selectedCard instanceof LocationCard) {
                    LocationCard loc = (LocationCard) selectedCard;
                    if (rules.canRepairCard(hp, loc)) {
                        MainWindow.this.controller.submitHumanAction(
                            GameAction.repairCard(loc));
                        clearSelection();
                    } else if (!targetingMode) {
                        enterTargetingMode("repair", new TargetFilter() {
                            @Override public boolean isTarget(Card t) {
                                if (t instanceof FleetCard) return rules.canRepairCard(hp, (FleetCard) t);
                                if (t instanceof LocationCard) return rules.canRepairCard(hp, (LocationCard) t);
                                return false;
                            }
                        });
                    }
                } else if (!targetingMode && hp != null) {
                    enterTargetingMode("repair", new TargetFilter() {
                        @Override public boolean isTarget(Card t) {
                            if (t instanceof FleetCard) return rules.canRepairCard(hp, (FleetCard) t);
                            if (t instanceof LocationCard) return rules.canRepairCard(hp, (LocationCard) t);
                            return false;
                        }
                    });
                }
            }
        });
        repairButton.setEnabled(false);
        repairButton.setToolTipText("Pool-paid repair of a damaged fleet or location (B5-0371).");

        // B5-4158: Aftermath play UI
        aftermathCardLabel = new JLabel("Play aftermath:");
        aftermathCardLabel.setForeground(new Color(200, 220, 200));
        aftermathCardLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        aftermathCardLabel.setMaximumSize(new Dimension(100, 24));

        aftermathCardSelector = new JComboBox<String>(new String[] { "(no aftermaths)" });
        aftermathCardSelector.setEnabled(false);
        aftermathCardSelector.setMaximumSize(new Dimension(200, 24));
        aftermathCardSelector.setToolTipText("Aftermath cards in your hand you can play this conflict.");
        aftermathCardSelector.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (aftermathCardSelector.getSelectedIndex() < 0) return;
                int index = aftermathCardSelector.getSelectedIndex();
                if (index >= 0 && index < aftermathCards.size()) {
                    selectedAftermathCard = aftermathCards.get(index);
                } else {
                    selectedAftermathCard = null;
                }
                updateAftermathControls();
            }
        });

        aftermathTargetLabel = new JLabel("Target:");
        aftermathTargetLabel.setForeground(new Color(200, 220, 200));
        aftermathTargetLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        aftermathTargetLabel.setMaximumSize(new Dimension(60, 24));

        aftermathTargetSelector = new JComboBox<String>(new String[] { "(select target)" });
        aftermathTargetSelector.setEnabled(false);
        aftermathTargetSelector.setMaximumSize(new Dimension(150, 24));
        aftermathTargetSelector.setToolTipText("Legal targets for the selected aftermath.");
        aftermathTargetSelector.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int index = aftermathTargetSelector.getSelectedIndex();
                if (index > 0 && index - 1 < aftermathTargets.size()) {
                    selectedAftermathTarget = aftermathTargets.get(index - 1);
                } else {
                    selectedAftermathTarget = null;
                }
                updateAftermathControls();
            }
        });

        playAftermathButton = makeButton("Play Aftermath", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Player hp = humanPlayer();
                if (hp == null || selectedAftermathCard == null || selectedAftermathTarget == null) return;
                if (!MainWindow.this.controller.isWaitingForHumanAftermath()) return;
                if (rules.canPlayAftermath(hp, selectedAftermathCard,
                        MainWindow.this.controller.getState().getActiveConflict(),
                        rules.initiatorWon(MainWindow.this.controller.getState().getActiveConflict(),
                                MainWindow.this.controller.getState().getActiveConflict().getWinner()),
                        selectedAftermathTarget, MainWindow.this.controller.getState())) {
                    MainWindow.this.controller.submitHumanAction(
                        GameAction.playAftermath(selectedAftermathCard, selectedAftermathTarget));
                }
            }
        });
        playAftermathButton.setEnabled(false);
        playAftermathButton.setToolTipText("Play the selected aftermath on the chosen target (B5-4158).");

        passAftermathButton = makeButton("Pass", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!MainWindow.this.controller.isWaitingForHumanAftermath()) return;
                MainWindow.this.controller.submitHumanAction(GameAction.pass());
            }
        });
        passAftermathButton.setEnabled(false);
        passAftermathButton.setToolTipText("Pass on playing an aftermath this conflict.");

        // B5-0404: mercenary bid control
        mercenaryBidAmountSelector = new JComboBox<String>(BID_AMOUNTS);
        mercenaryBidAmountSelector.setEnabled(false);
        mercenaryBidAmountSelector.setMaximumSize(new Dimension(80, 24));
        mercenaryBidButton = makeButton("Bid", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Player hp = humanPlayer();
                if (hp == null) return;
                GameState gs = MainWindow.this.controller.getState();
                List<Card> offers = gs.getMercenaryOffers();
                if (offers.isEmpty()) return;
                int amount = Integer.parseInt((String) mercenaryBidAmountSelector.getSelectedItem());
                if (amount <= 0) return;
                // Bid on the first offered mercenary (single-mercenary display assumption)
                Card merc = offers.get(0);
                if (rules.canBidOnMercenary(hp, merc, amount, gs)) {
                    MainWindow.this.controller.submitHumanAction(
                        GameAction.bidOnMercenary(merc, amount));
                }
            }
        });
        mercenaryBidButton.setEnabled(false);
        mercenaryBidButton.setToolTipText("Bid influence to control an offered mercenary (B5-0395).");

        // B5-4175: elective hand-discard selector + button for Draw round step D2.
        // Gated on isWaitingForHumanDrawEnd(): enabled only while the engine is
        // holding a human-draw-end window, mirroring the mercenary bid control's
        // phase gating in the same constructor.
        handDiscardAmountSelector = new JComboBox<String>(HAND_DISCARD_AMOUNTS);
        handDiscardAmountSelector.setEnabled(false);
        handDiscardAmountSelector.setMaximumSize(new Dimension(80, 24));
        handDiscardButton = makeButton("Discard", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Player hp = humanPlayer();
                if (hp == null) return;
                if (!MainWindow.this.controller.isWaitingForHumanDrawEnd()) return;
                GameState gs = MainWindow.this.controller.getState();
                int amount = Integer.parseInt((String) handDiscardAmountSelector.getSelectedItem());
                if (amount <= 0) return;
                int discarded = rules.discardFromHand(hp, amount, gs);
                if (discarded > 0) {
                    MainWindow.this.controller.submitHumanAction(GameAction.discardHandCards(discarded));
                }
            }
        });
        handDiscardButton.setEnabled(false);
        handDiscardButton.setToolTipText("Discard cards from hand as Draw round step D2 (B5-4170).");
        mercenaryOfferLabel = new JLabel("(no mercenary offers)");
        mercenaryOfferLabel.setForeground(new Color(200, 220, 200));
        mercenaryOfferLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        mercenaryOfferLabel.setMaximumSize(new Dimension(160, 24));
        mercenaryControllerLabel = new JLabel("(no bids)");
        mercenaryControllerLabel.setForeground(new Color(200, 220, 200));
        mercenaryControllerLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        mercenaryControllerLabel.setMaximumSize(new Dimension(280, 24));

        // B5-0487: Censure opponent-fleet target picker
        censureTargetSelector = new JComboBox<String>(new String[] { "(select opponent fleet)" });
        censureTargetSelector.setEnabled(false);
        censureTargetSelector.setMaximumSize(new Dimension(200, 24));
        censureTargetSelector.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateCensurePlayButton();
            }
        });

        censureTargetLabel = new JLabel("Opponent fleet target:");
        censureTargetLabel.setForeground(new Color(200, 220, 200));
        censureTargetLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        censureTargetLabel.setMaximumSize(new Dimension(140, 24));

        censurePlayButton = makeButton("Play (opponent target)", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Player hp = humanPlayer();
                if (hp == null) return;
                if (!(selectedCard instanceof EnhancementCard)) return;
                EnhancementCard enh = (EnhancementCard) selectedCard;
                if (!(enh.getSubtype() != null && enh.getSubtype().endsWith("_FLEET"))) return;
                // B5-0522 same-class fix (B5-0435/0490 precedent, disclosed):
                // route on the PICKER's selection, not enh.hasExplicitTarget() —
                // the seam is set inside playCensureWithTarget right before
                // submit, so the old check was never true and every targeted
                // click fell through to the self-target path.
                if (selectedCensureTarget == null || selectedCensureTargetOwner == null) {
                    // No opponent fleet picked (button should be disabled) —
                    // normal self-target play path.
                    MainWindow.this.controller.submitHumanAction(GameAction.playCard(enh));
                    clearSelection();
                    return;
                }
                // Explicit opponent target chosen — submit with the target
                playCensureWithTarget();
            }
        });
        censurePlayButton.setEnabled(false);
        censurePlayButton.setToolTipText("Play this Enhancement targeting the selected opponent fleet (B5-0487).");

        // B5-0522: opponent-CHARACTER target picker (shunned-class seam cards)
        charCensureTargetSelector = new JComboBox<String>(new String[] { "(select opponent character)" });
        charCensureTargetSelector.setEnabled(false);
        charCensureTargetSelector.setMaximumSize(new Dimension(200, 24));
        charCensureTargetSelector.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateCharCensurePlayButton();
            }
        });

        charCensureTargetLabel = new JLabel("Opponent character target:");
        charCensureTargetLabel.setForeground(new Color(200, 220, 200));
        charCensureTargetLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        charCensureTargetLabel.setMaximumSize(new Dimension(150, 24));

        charCensurePlayButton = makeButton("Play (opponent char target)", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Player hp = humanPlayer();
                if (hp == null) return;
                if (!(selectedCard instanceof EnhancementCard)) return;
                EnhancementCard enh = (EnhancementCard) selectedCard;
                if (!(enh.getSubtype() != null && enh.getSubtype().endsWith("_CHARACTER"))) return;
                // B5-0522: route on the PICKER's selection, not enh
                // hasExplicitTarget() — the seam is set by playCharCensureWithTarget
                // itself right before submit, so a fresh hand card never tests true
                // here. (The B5-0487 fleet handler's hasExplicitTarget() check sent
                // every targeted click down the self-path; same-class fix applied
                // to that handler in this same claim, disclosed in the close-out.)
                if (selectedCharCensureTarget == null || selectedCharCensureTargetOwner == null) {
                    // No opponent character picked (button should be disabled) —
                    // normal self-target play path.
                    MainWindow.this.controller.submitHumanAction(GameAction.playCard(enh));
                    clearSelection();
                    return;
                }
                // Explicit opponent target chosen — submit with the target
                playCharCensureWithTarget();
            }
        });
        charCensurePlayButton.setEnabled(false);
        charCensurePlayButton.setToolTipText("Play this Enhancement targeting the selected opponent character (B5-0522).");

        // B5-0407: declare-war UI
        warTargetSelector = new JComboBox<String>(new String[] { "(select target)" });
        warTargetSelector.setEnabled(false);
        warTargetSelector.setMaximumSize(new Dimension(200, 24));
        warTargetSelector.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateDeclareWarButton();
            }
        });

        declareWarButton = makeButton("Declare War", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Player hp = humanPlayer();
                if (hp == null) return;
                GameState gs = MainWindow.this.controller.getState();
                String sel = (String) warTargetSelector.getSelectedItem();
                if (sel == null || sel.equals("(select target)")
                        || sel.equals("(no legal targets)")) return;
                // Determine if this is a race target or location target
                Player raceTarget = null;
                LocationCard locTarget = null;
                // Look up the target by name
                for (Player p : gs.getPlayers()) {
                    if (!p.isHuman() && p.getName().equals(sel)) {
                        raceTarget = p;
                        break;
                    }
                }
                if (raceTarget == null) {
                    // Must be a location — find by scanning all players' locations
                    for (Player p : gs.getPlayers()) {
                        if (p.isHuman()) continue;
                        for (LocationCard loc : p.getLocations()) {
                            if (loc.getTitle().equals(sel)) {
                                locTarget = loc;
                                break;
                            }
                        }
                        if (locTarget != null) break;
                    }
                }
                if (raceTarget == null && locTarget == null) return;
                WarKind kind = raceTarget != null ? WarKind.RACE_TARGET : WarKind.LOCATION_TARGET;
                if (rules.canInitiateWarConflict(hp, kind, raceTarget, locTarget, gs)) {
                    MainWindow.this.controller.submitHumanAction(
                            GameAction.declareWarConflict(kind, raceTarget, locTarget));
                }
            }
        });
        declareWarButton.setEnabled(false);
        declareWarButton.setToolTipText("Declare a war conflict against a race or location at war with your faction (B5-0376).");

        warStatusLabel = new JLabel("");
        warStatusLabel.setForeground(new Color(200, 220, 200));
        warStatusLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        warStatusLabel.setMaximumSize(new Dimension(240, 24));

        // B5-0326 F5: cost preview readout
        costLabel = new JLabel("  ");
        costLabel.setForeground(new Color(200, 220, 200));
        costLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));

        // B5-0348: hand filter/sort panel
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        filterPanel.setBackground(new Color(10, 20, 10));
        filterPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(80, 120, 80)),
            "Filter / Sort", 0, 0, new Font("SansSerif", Font.BOLD, 9),
            new Color(180, 200, 180)));

        // Type filters
        typeCharBox = makeTinyCheckBox("Char");
        typeFleetBox = makeTinyCheckBox("Fleet");
        typeConflictBox = makeTinyCheckBox("Conflict");
        typeAgendaBox = makeTinyCheckBox("Agenda");
        typeAftermathBox = makeTinyCheckBox("Aftermath");
        typeEventBox = makeTinyCheckBox("Event");
        typeEnhancementBox = makeTinyCheckBox("Enh");
        typeGroupBox = makeTinyCheckBox("Group");
        typeLocationBox = makeTinyCheckBox("Loc");
        typeCharBox.setSelected(true);
        typeFleetBox.setSelected(true);
        typeConflictBox.setSelected(true);
        typeAgendaBox.setSelected(true);
        typeAftermathBox.setSelected(true);
        typeEventBox.setSelected(true);
        typeEnhancementBox.setSelected(true);
        typeGroupBox.setSelected(true);
        typeLocationBox.setSelected(true);

        // Faction filters
        fascHumanBox = makeTinyCheckBox("Human");
        fascMinbariBox = makeTinyCheckBox("Minbari");
        fascCentauriBox = makeTinyCheckBox("Centauri");
        fascNarnBox = makeTinyCheckBox("Narn");
        fascNeutralBox = makeTinyCheckBox("Neut");
        fascNonAlignedBox = makeTinyCheckBox("NonAl");
        fascVorlonBox = makeTinyCheckBox("Vorlon");
        fascAnyBox = makeTinyCheckBox("ANY");
        fascHumanBox.setSelected(true);
        fascMinbariBox.setSelected(true);
        fascCentauriBox.setSelected(true);
        fascNarnBox.setSelected(true);
        fascNeutralBox.setSelected(true);
        fascNonAlignedBox.setSelected(true);
        fascVorlonBox.setSelected(true);
        fascAnyBox.setSelected(true);

        // Sort controls
        sortUnsorted = new JRadioButton("Order");
        sortCostAsc = new JRadioButton("CostÃ¢â€ â€˜");
        sortCostDesc = new JRadioButton("CostÃ¢â€ â€œ");
        sortUnsorted.setSelected(true);
        sortUnsorted.setFocusPainted(false);
        sortCostAsc.setFocusPainted(false);
        sortCostDesc.setFocusPainted(false);
        sortGroup = new ButtonGroup();
        sortGroup.add(sortUnsorted);
        sortGroup.add(sortCostAsc);
        sortGroup.add(sortCostDesc);
        sortUnsorted.setFont(new Font("SansSerif", Font.PLAIN, 9));
        sortCostAsc.setFont(new Font("SansSerif", Font.PLAIN, 9));
        sortCostDesc.setFont(new Font("SansSerif", Font.PLAIN, 9));

        // Show unplayable toggle
        showUnplayable = makeTinyCheckBox("Show dimmed");
        showUnplayable.setSelected(true);

        filterPanel.add(new JSeparator(JSeparator.VERTICAL));
        filterPanel.add(new JLabel("Type:"));
        filterPanel.add(typeCharBox);
        filterPanel.add(typeFleetBox);
        filterPanel.add(typeConflictBox);
        filterPanel.add(typeAgendaBox);
        filterPanel.add(typeAftermathBox);
        filterPanel.add(typeEventBox);
        filterPanel.add(typeEnhancementBox);
        filterPanel.add(typeGroupBox);
        filterPanel.add(typeLocationBox);
        filterPanel.add(new JSeparator(JSeparator.VERTICAL));
        filterPanel.add(new JLabel("Faction:"));
        filterPanel.add(fascHumanBox);
        filterPanel.add(fascMinbariBox);
        filterPanel.add(fascCentauriBox);
        filterPanel.add(fascNarnBox);
        filterPanel.add(fascNeutralBox);
        filterPanel.add(fascNonAlignedBox);
        filterPanel.add(fascVorlonBox);
        filterPanel.add(fascAnyBox);
        filterPanel.add(new JSeparator(JSeparator.VERTICAL));
        filterPanel.add(sortUnsorted);
        filterPanel.add(sortCostAsc);
        filterPanel.add(sortCostDesc);
        filterPanel.add(new JSeparator(JSeparator.VERTICAL));
        filterPanel.add(showUnplayable);

        // B5-2375: Split toolbar layout — filterPanel is always visible in its own row, actions/buttons in scrollable panel below.
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        buttonPanel.setBackground(new Color(10, 20, 10));

        // B5-2377: primary action row — the six human ACTION-turn controls
        // (Pass Turn, Play Card, Initiate Conflict, Sponsor, Sponsor Group,
        // Promote, Build Influence) live in their own always-visible row OUTSIDE the
        // scrollable buttonPanel, so they stay on screen without scrolling
        // at 1920 width no matter how far the rest of the toolbar scrolls.
        JPanel primaryActionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        primaryActionPanel.setBackground(new Color(10, 20, 10));
        primaryActionPanel.add(passButton);
        primaryActionPanel.add(Box.createHorizontalStrut(8));
        primaryActionPanel.add(playCardOnlyButton);
        primaryActionPanel.add(Box.createHorizontalStrut(4));
        primaryActionPanel.add(initiateConflictButton);
        primaryActionPanel.add(Box.createHorizontalStrut(12));
        primaryActionPanel.add(sponsorButton);
        primaryActionPanel.add(sponsorGroupButton);
        primaryActionPanel.add(promoteButton);
        primaryActionPanel.add(Box.createHorizontalStrut(4));
        primaryActionPanel.add(promoteLeaderSelector);
        primaryActionPanel.add(sponsorLeaderSelector);
        primaryActionPanel.add(buildInfluenceLeaderSelector);
        primaryActionPanel.add(buildInfluenceButton);

        // All remaining controls go to the scrollable buttonPanel below.
        buttonPanel.add(Box.createHorizontalStrut(12));
        buttonPanel.add(discardAgendaButton);
        buttonPanel.add(replaceAgendaButton);
        buttonPanel.add(revealAgendaButton);
        buttonPanel.add(contingencySelector);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(revealContingencyButton);
        buttonPanel.add(Box.createHorizontalStrut(12));
        buttonPanel.add(leadFleetSelector);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(leadFleetButton);
        buttonPanel.add(Box.createHorizontalStrut(12));
        buttonPanel.add(rotateEffectKindSelector);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(useRotateEffectButton);
        buttonPanel.add(Box.createHorizontalStrut(12));
        buttonPanel.add(costLabel);
        buttonPanel.add(Box.createHorizontalStrut(12));
        // B5-2253 (B5-0310 F2): the conflict picker sits immediately before the
        // target dropdown it feeds, so the declare-then-target sequence reads
        // left to right in the toolbar.
        buttonPanel.add(conflictPickerLabel);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(conflictPickerSelector);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(targetSelector);
        buttonPanel.add(Box.createHorizontalStrut(12));
        buttonPanel.add(supportButton);
        buttonPanel.add(opposeButton);
        buttonPanel.add(Box.createHorizontalStrut(12));
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(attackButton);
        buttonPanel.add(healButton);
        buttonPanel.add(repairButton);
        buttonPanel.add(Box.createHorizontalStrut(12));
        // B5-4158: aftermath play controls
        buttonPanel.add(aftermathCardLabel);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(aftermathCardSelector);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(aftermathTargetLabel);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(aftermathTargetSelector);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(playAftermathButton);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(passAftermathButton);
        buttonPanel.add(Box.createHorizontalStrut(8));
        buttonPanel.add(mercenaryBidAmountSelector);
        buttonPanel.add(mercenaryBidButton);
        buttonPanel.add(Box.createHorizontalStrut(8));
        buttonPanel.add(handDiscardAmountSelector);
        buttonPanel.add(handDiscardButton);
        buttonPanel.add(Box.createHorizontalStrut(8));
        buttonPanel.add(mercenaryOfferLabel);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(mercenaryControllerLabel);
        buttonPanel.add(Box.createHorizontalStrut(8));
        buttonPanel.add(censureTargetLabel);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(censureTargetSelector);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(censurePlayButton);
        buttonPanel.add(Box.createHorizontalStrut(8));
        buttonPanel.add(charCensureTargetLabel);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(charCensureTargetSelector);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(charCensurePlayButton);
        buttonPanel.add(Box.createHorizontalStrut(8));
        buttonPanel.add(warTargetSelector);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(declareWarButton);
        buttonPanel.add(Box.createHorizontalStrut(8));
        buttonPanel.add(warStatusLabel);
        buttonPanel.add(Box.createHorizontalStrut(12));
        // B5-0701: surrender availability readout. Sits with the other
        // target+button pairs; the hint label carries the +3-influence
        // consequence readout the row asks for.
        buttonPanel.add(surrenderTargetSelector);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(surrenderButton);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(surrenderHintLabel);
        buttonPanel.add(Box.createHorizontalStrut(8));

        // B5-2267: forfeit button — no target selector needed; confirmation
        // dialog handles the irrevocable commit. Sits beside surrender.
        buttonPanel.add(forfeitButton);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(forfeitHintLabel);
        buttonPanel.add(Box.createHorizontalStrut(12));

        // B5-4155: phase / initiative / status moved OUT of this row — they
        // are readouts of where the turn is, they belong to the frame, and
        // they now live in the always-visible status strip below.

        // B5-2267: draw-round buy-cards offer. Shows during DRAW phase only.
        buttonPanel.add(drawRoundBuyLabel);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(drawRoundBuyButton);
        buttonPanel.add(Box.createHorizontalStrut(12));

        // B5-2008: opens the setup dialog seeded with the live game's seats.
        gameSetupButton = makeButton("Game Setup", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showGameSetup();
            }
        });
        // B5-2377: the Game Setup and Council Vote buttons were added to the
        // pre-B5-2375 toolbar panel, which B5-2375 stopped adding to the frame,
        // so both were orphaned and unreachable. They belong with the other
        // dialog openers in the scrollable buttonPanel.
        buttonPanel.add(gameSetupButton);

        // B5-2004: opens the Council vote ballot. Enabled only while the engine
        // holds an open vote session; see refreshCouncilVoteControl.
        councilVoteButton = makeButton("Council Vote", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showCouncilVote();
            }
        });
        councilVoteButton.setToolTipText(
            "Cast your ambassador's Council vote on the measure now on the floor.");
        // B5-2377: same orphan repair as Game Setup above — Council Vote was
        // added to the detached pre-B5-2375 toolbar panel and never displayed.
        buttonPanel.add(councilVoteButton);

        // B5-2007: opens the Deck Builder dialog for deck construction with
        // live validation feedback.
        deckBuilderButton = makeButton("Deck Builder", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showDeckBuilder();
            }
        });
        deckBuilderButton.setToolTipText(
            "Build a custom deck with live validation (45-card minimum, ambassador rule, max 3 copies, faction playability).");
        // B5-2265: the starting-game flow. Enabled only while the opening window is
        // still open (see refreshStartingGameControl); once the first conflict is
        // declared the starting hand is history and this must not rewrite it.
        startingGameButton = makeButton("Starting Game", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showStartingGame();
            }
        });
        startingGameButton.setToolTipText(
            "Set up the opening: starting ambassador, the three typed cards beside it, "
            + "shuffle and cut, and the initiative order for the first conflict round.");
        buttonPanel.add(startingGameButton);

        buttonPanel.add(deckBuilderButton);

        // B5-2375: Insert new layout wrapper for two-row toolbar.
        // B5-2377: the primary action row sits between the filter row and the
        // scroller, outside the scroll pane, so it never scrolls away.
        JPanel toolbarPanel = new JPanel();
        toolbarPanel.setLayout(new BorderLayout());
        toolbarPanel.setBackground(new Color(10, 20, 10));
        JPanel topRows = new JPanel();
        topRows.setLayout(new BorderLayout());
        topRows.setBackground(new Color(10, 20, 10));
        topRows.add(filterPanel, BorderLayout.NORTH);
        // B5-4155: the filter row is reachability-by-default too wide for a
        // 1366 frame, so it starts hidden and re-attaches here on demand.
        registerDetachable("Filter / Sort", filterPanel, topRows, BorderLayout.NORTH, false);
        topRows.add(primaryActionPanel, BorderLayout.CENTER);

        // B5-4155: always-visible status strip. Phase, initiative and status
        // used to sit at the far end of the scrolling action row, so the
        // frame's only readout of where the turn was scrolled out of sight.
        JPanel statusStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        statusStrip.setBackground(new Color(10, 20, 10));
        statusStrip.add(phaseLabel);
        statusStrip.add(initiativeLabel);
        statusStrip.add(statusLabel);
        topRows.add(statusStrip, BorderLayout.SOUTH);
        toolbarPanel.add(topRows, BorderLayout.NORTH);
        add(toolbarPanel, BorderLayout.NORTH);

        // B5-4155: the secondary action row LEAVES the frame.
        // Measured before this change: the row needs 5,627px of width, so at
        // 1366 every one of these controls was a scroll away and the ones at
        // the end (Surrender, Forfeit, Game Setup) were effectively
        // unreachable. Shrinking them only trades scrolling for illegibility.
        // The scroller is reparented into a floating dialog rather than copied,
        // so every listener and every refresh() enablement still lands on the
        // same button instances.
        JScrollPane buttonScroller = new JScrollPane(buttonPanel,
            JScrollPane.VERTICAL_SCROLLBAR_NEVER,
            JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        buttonScroller.setBorder(BorderFactory.createEmptyBorder());
        // B5-4170 playtest fix: without a constrained preferred size the
        // FlowLayout buttonPanel makes the scroll pane's preferred width span
        // all 50+ buttons (~5000px), so pack() on the ActionWindow creates a
        // dialog wider than a 1366px screen — the buttons exist but are off the
        // right edge and the horizontal scrollbar can't scroll them into view
        // because the viewport is already as wide as the content. Clamping the
        // preferred width forces the viewport to a visible width and lets the
        // scrollbar do its job.
        buttonScroller.setPreferredSize(new Dimension(
            Math.min(buttonPanel.getPreferredSize().width, 900), 120));
        actionWindow = new ActionWindow(this, buttonScroller);

        // B5-4159: the Actions window holds the whole secondary action row
        // (Support/Oppose included) and its close button hides it, so the
        // View menu must offer it back. Before this entry a hidden Actions
        // window was gone for the rest of the session with no reopen path,
        // which is exactly how Support/Oppose went permanently missing.
        JMenuItem actionsItem = new JMenuItem("Actions");
        actionsItem.setBackground(new Color(10, 20, 10));
        actionsItem.setForeground(new Color(180, 200, 180));
        actionsItem.setToolTipText("Reopen the floating Actions window (Support/Oppose live there)");
        actionsItem.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                if (actionWindow != null) actionWindow.showWindow();
            }
        });
        viewMenu.add(actionsItem);

        // ── Hand ──────────────────────────────────────────────────────────────
        handPanel = new HandPanel();
        handPanel.setOnCardSelected(new CardSelectedListener() {
            @Override
            public void onCardSelected(Card card) {
                applyCardSelection(card, true);
            }
        });
        // B5-0423: board-side selection. The heal/repair/lead-fleet/rotate-effect
        // controls read selectedCard, but those cards (IC, supporting, fleets,
        // locations) never appear in a hand — without this the buttons could
        // never light for a legal target. Shares applyCardSelection with the
        // hand so target-population and enablement rules stay identical.
        boardPanel.setOnBoardCardSelected(new CardSelectedListener() {
            @Override
            public void onCardSelected(Card card) {
                applyCardSelection(card, false);
            }
        });
        // B5-0348: give HandPanel access to rules + human faction for affordability checks
        handPanel.setRules(rules, controller.getState().getHumanPlayer().getFaction());
        add(handPanel, BorderLayout.SOUTH);

        // B5-0348: wire filter/sort controls
        typeCharBox.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setTypeFilter(CardType.CHARACTER, typeCharBox.isSelected()); }
        });
        typeFleetBox.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setTypeFilter(CardType.FLEET, typeFleetBox.isSelected()); }
        });
        typeConflictBox.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setTypeFilter(CardType.CONFLICT, typeConflictBox.isSelected()); }
        });
        typeAgendaBox.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setTypeFilter(CardType.AGENDA, typeAgendaBox.isSelected()); }
        });
        typeAftermathBox.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setTypeFilter(CardType.AFTERMATH, typeAftermathBox.isSelected()); }
        });
        typeEventBox.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setTypeFilter(CardType.EVENT, typeEventBox.isSelected()); }
        });
        typeEnhancementBox.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setTypeFilter(CardType.ENHANCEMENT, typeEnhancementBox.isSelected()); }
        });
        typeGroupBox.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setTypeFilter(CardType.GROUP, typeGroupBox.isSelected()); }
        });
        typeLocationBox.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setTypeFilter(CardType.LOCATION, typeLocationBox.isSelected()); }
        });
        fascHumanBox.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setFactionFilter(Faction.HUMAN, fascHumanBox.isSelected()); }
        });
        fascMinbariBox.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setFactionFilter(Faction.MINBARI, fascMinbariBox.isSelected()); }
        });
        fascCentauriBox.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setFactionFilter(Faction.CENTAURI, fascCentauriBox.isSelected()); }
        });
        fascNarnBox.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setFactionFilter(Faction.NARN, fascNarnBox.isSelected()); }
        });
        fascNeutralBox.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setFactionFilter(Faction.NEUTRAL, fascNeutralBox.isSelected()); }
        });
        fascNonAlignedBox.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setFactionFilter(Faction.NON_ALIGNED, fascNonAlignedBox.isSelected()); }
        });
        fascVorlonBox.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setFactionFilter(Faction.VORLON, fascVorlonBox.isSelected()); }
        });
        fascAnyBox.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setFactionFilter(Faction.ANY, fascAnyBox.isSelected()); }
        });
        sortUnsorted.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setSortMode(0); }
        });
        sortCostAsc.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setSortMode(1); }
        });
        sortCostDesc.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setSortMode(2); }
        });
        showUnplayable.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { handPanel.setShowUnplayable(showUnplayable.isSelected()); }
        });

        // B5-4155: wire the orphaned join picker. It has no home slot in the
        // frame — the join window is not a standing row — so it floats only.
        // View > Conflict Join reaches it the same way the rest do.
        joinPickerPanel = new ConflictJoinPickerPanel(controller);
        registerDetachable("Conflict Join", joinPickerPanel, null, null, false);
        joinPicker = detachablePanels.get(detachablePanels.size() - 1);

        // B5-4155: the floating action row takes the width pressure off, so
        // the frame's floor can drop to a real 1366x768 laptop (was 1300x820,
        // which did not fit the target height at all).
        pack();
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(1100, 760));
    }

    /**
     * B5-4155: the floating action row shows and hides with the frame.
     * Doing this in {@code setVisible} rather than at construction is what
     * keeps construction side-effect free: a test harness (or a headless
     * build) that builds a MainWindow must never pop a window onto the
     * desktop, and must never leave one after {@code dispose()}.
     */
    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (actionWindow != null) {
            if (visible) {
                actionWindow.updatePosition();
                actionWindow.setVisible(true);
            } else {
                actionWindow.setVisible(false);
            }
        }
    }

    // ── B5-4155: detachable panels ──────────────────────────────────────────
    // Every visual panel except the board and the hand can float in its own
    // window; each one has a View-menu checkbox to bring it back.

    private JMenu viewMenu;
    private ActionWindow actionWindow;
    private final List<Detachable> detachablePanels = new ArrayList<Detachable>();

    /** One View-menu entry plus the window it controls. */
    private final class Detachable {
        final String title;
        final JComponent panel;
        final Container host;
        final Object constraint;
        final int index;
        final JCheckBoxMenuItem item;
        final DetachWindow window;
        final boolean visibleByDefault;
        boolean detached = false;

        Detachable(String title, JComponent panel, Container host, Object constraint,
                   int index, JCheckBoxMenuItem item, DetachWindow window,
                   boolean visibleByDefault) {
            this.title = title;
            this.panel = panel;
            this.host = host;
            this.constraint = constraint;
            this.index = index;
            this.item = item;
            this.window = window;
            this.visibleByDefault = visibleByDefault;
        }

        /** Moves the panel out of its host into the floating window. */
        void floatPanel() {
            detached = true;
            // B5-4159: hostless panels (the Conflict Join picker has no home
            // slot) must travel the same detach path as hosted ones. The old
            // host==null branch only showed an empty window and never moved
            // the content into the body, so the auto-shown join window came
            // up blank with no Support/Oppose/Pass reachable. detach() takes
            // the host only for symmetry and never reads it, so one path
            // serves both cases with no behaviour change for hosted panels.
            window.detach(host);
        }

        /** Returns the panel to its home slot and hides the window. B5-4166:
         *  dock restores the panel's REGISTERED default visibility, never a
         *  hard-coded false. For the visibleByDefault=true panels (Influence
         *  Tracker, Tension / Unrest, Conflict Types) docking back with false
         *  left the panel in its host INVISIBLE, and the View checkbox — whose
         *  off-path had no way to express back-in-the-frame-and-visible — could
         *  never recover it, so detach-then-close permanently deleted the panel.
         *  Docking is the inverse of floating, so it must put back exactly what
         *  floating took away. */
        void dock() {
            detached = false;
            if (host == null) {
                window.setVisible(false);
            } else {
                window.attach(host, constraint, index, visibleByDefault);
            }
        }
    }

    /**
     * Registers one detachable panel. {@code visibleByDefault} false leaves
     * the panel in its host but not shown — present, so the frame can still
     * lay it out on demand, but costing no width until asked for.
     */
    private void registerDetachable(final String title, final JComponent panel,
                                    final Container host, final Object constraint,
                                    boolean visibleByDefault) {
        if (!visibleByDefault) {
            panel.setVisible(false);
        }
        final DetachWindow window = new DetachWindow(MainWindow.this, title, panel);
        final JCheckBoxMenuItem item = new JCheckBoxMenuItem(title, false);
        item.setBackground(new Color(10, 20, 10));
        item.setForeground(new Color(180, 200, 180));
        final Detachable handle = new Detachable(title, panel, host, constraint,
            host == null ? -1 : host.getComponentCount() - 1, item, window,
            visibleByDefault);
        window.setHiddenListener(new DetachWindow.HiddenListener() {
            @Override public void windowHidden(DetachWindow source) {
                // Hiding is not closing: the panel goes home, still reachable.
                if (handle.host != null) {
                    handle.dock();
                }
                handle.detached = false;
                item.setSelected(false);
            }
        });
        item.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                if (item.isSelected()) {
                    handle.floatPanel();
                } else {
                    handle.dock();
                }
                MainWindow.this.revalidate();
                MainWindow.this.repaint();
            }
        });
        viewMenu.add(item);
        detachablePanels.add(handle);
    }

    /** Brings a detachable panel back on screen from outside the UI plumbing. */
    public void showDetachable(String title) {
        for (int i = 0; i < detachablePanels.size(); i++) {
            Detachable handle = detachablePanels.get(i);
            if (handle.title.equals(title)) {
                handle.item.setSelected(true);
                handle.floatPanel();
                // B5-4159: the View-menu path revalidates after floating;
                // this outside path must too, or a hosted panel leaves a
                // stale layout behind it.
                revalidate();
                repaint();
                return;
            }
        }
    }

    private Player humanPlayer() {
        return MainWindow.this.controller.getState().getHumanPlayer();
    }

    private CharacterCard findUnrotatedIC(Player p) {
        for (CharacterCard ch : p.getInnerCircle()) {
            if (!ch.isRotated()) return ch;
        }
        return null;
    }

    /** B5-0404: refresh the mercenary bid control from current state.
     *  Shows offered mercenaries, updates the amount selector enablement and
     *  the Bid button. The button is enabled when it is the human's Action
     *  turn, a mercenary is offered, and the selected bid amount is affordable.
     */
    private void refreshMercenaryBidControl(Player human, boolean actionTurn) {
        if (human == null) {
            mercenaryBidAmountSelector.setEnabled(false);
            mercenaryBidButton.setEnabled(false);
            mercenaryOfferLabel.setText("(no mercenary offers)");
            mercenaryControllerLabel.setText("(no bids)");
            return;
        }
        GameState gs = MainWindow.this.controller.getState();
        List<Card> offers = gs.getMercenaryOffers();
        if (offers.isEmpty()) {
            mercenaryBidAmountSelector.setEnabled(false);
            mercenaryBidButton.setEnabled(false);
            mercenaryOfferLabel.setText("(no mercenary offers)");
            mercenaryControllerLabel.setText("(no bids)");
            return;
        }
        // The current toolbar supports the first offered mercenary; show its
        // live cumulative totals and leader beside the existing bid control.
        Card merc = offers.get(0);
        mercenaryOfferLabel.setText(merc.getTitle());
        mercenaryControllerLabel.setText(mercenaryBidStatus(gs, merc));

        // Enablement: Action phase, my turn, a mercenary is offered
        mercenaryBidAmountSelector.setEnabled(actionTurn && !offers.isEmpty());

        // Bid button: enabled when affordable at the selected amount
        int selectedAmount = Integer.parseInt((String) mercenaryBidAmountSelector.getSelectedItem());
        boolean canAfford = actionTurn
            && rules.canBidOnMercenary(human, merc, selectedAmount, gs);
        mercenaryBidButton.setEnabled(canAfford);
    }

    /** B5-4175: refresh the hand-discard control during the Draw round.
     *  Shows the elective discard-amount selector and toggles the Discard button
     *  on only while the engine is waiting for the human to close the Draw-round
     *  window (isWaitingForHumanDrawEnd), mirroring refreshMercenaryBidControl.
     *  The actual discard is performed by rules.discardFromHand(hp, n, gs) and
     *  submitted on the button action; the selector is left disabled unless the
     *  window is open so the engine always knows the current amount.
     */
    private void refreshHandDiscardControl(Player human, boolean actionTurn) {
        if (human == null || !MainWindow.this.controller.isWaitingForHumanDrawEnd()) {
            handDiscardAmountSelector.setEnabled(false);
            handDiscardButton.setEnabled(false);
            return;
        }
        GameState gs = MainWindow.this.controller.getState();
        int handSize = human.getHand().size();
        if (handSize == 0) {
            handDiscardAmountSelector.setEnabled(false);
            handDiscardButton.setEnabled(false);
            return;
        }
        int selectedAmount = Integer.parseInt((String) handDiscardAmountSelector.getSelectedItem());
        boolean canDiscard = actionTurn && handSize >= selectedAmount;
        handDiscardAmountSelector.setEnabled(actionTurn && handSize > 0);
        handDiscardButton.setEnabled(canDiscard);
    }

    /**
     * B5-2279 -- read-only bid projection for the offered mercenary. The
     * cumulative totals come from GameState, and table order is retained for
     * deterministic rendering. A tie is shown explicitly because the engine
     * crowns nobody when the highest totals are equal.
     */
    static String mercenaryBidStatus(GameState state, Card merc) {
        if (state == null || merc == null) return "(no bids)";
        StringBuilder totals = new StringBuilder("Bids: ");
        int best = 0;
        int leaders = 0;
        String leader = null;
        for (Player p : state.getPlayers()) {
            int bid = state.getMercenaryBid(merc, p);
            if (bid > 0) {
                if (totals.length() > 6) totals.append(", ");
                totals.append(p.getName()).append("=").append(bid);
            }
            if (bid > best) {
                best = bid;
                leader = p.getName();
                leaders = 1;
            } else if (bid == best && bid > 0) {
                leaders++;
            }
        }
        if (totals.length() == 6) totals.append("(none)");
        totals.append(" | High: ");
        if (best <= 0) {
            totals.append("(none)");
        } else if (leaders > 1) {
            totals.append("(tie at ").append(best).append(")");
        } else {
            totals.append(leader).append("=").append(best);
        }
        return totals.toString();
    }

    /** B5-0407: refresh the declare-war control from current state.
     *  Populates the target selector with legal war targets (races at war,
     *  then locations owned by factions at war), updates the status label,
     *  and enables/disables the controls based on phase and turn.
     */
    private void refreshDeclareWarControl(Player human, boolean actionTurn) {
        if (human == null) {
            warTargetSelector.setEnabled(false);
            declareWarButton.setEnabled(false);
            warStatusLabel.setText("");
            return;
        }
        GameState gs = MainWindow.this.controller.getState();
        // Only available when at war
        if (!gs.isAtWar(human.getFaction())) {
            warTargetSelector.setEnabled(false);
            declareWarButton.setEnabled(false);
            warStatusLabel.setText("Not at war — no war conflicts available.");
            return;
        }
        // Build target list: races at war first, then locations
        java.util.ArrayList<String> targets = new java.util.ArrayList<String>();
        java.util.ArrayList<String> targetTypes = new java.util.ArrayList<String>();
        for (Player p : gs.getPlayers()) {
            if (p == human || p.getFaction() == null) continue;
            if (gs.isAtWar(human.getFaction(), p.getFaction())) {
                targets.add(p.getName());
                targetTypes.add("race");
            }
        }
        for (Player p : gs.getPlayers()) {
            if (p == human || p.getFaction() == null) continue;
            if (!gs.isAtWar(human.getFaction(), p.getFaction())) continue;
            for (LocationCard loc : p.getLocations()) {
                targets.add(loc.getTitle());
                targetTypes.add("location");
            }
        }
        if (targets.isEmpty()) {
            warTargetSelector.setEnabled(false);
            declareWarButton.setEnabled(false);
            warStatusLabel.setText("No war targets available.");
            return;
        }
        warTargetSelector.removeAllItems();
        for (int i = 0; i < targets.size(); i++) {
            String label = targets.get(i);
            if (targetTypes.get(i).equals("location")) {
                label += " [loc]";
            }
            warTargetSelector.addItem(label);
        }
        warTargetSelector.setEnabled(actionTurn);
        warStatusLabel.setText(targets.size() + " war target(s) available.");
        updateDeclareWarButton();
    }

    /** Updates the Declare War button enablement based on current selection. */
    private void updateDeclareWarButton() {
        if (warTargetSelector == null || declareWarButton == null) return;
        Player hp = humanPlayer();
        if (hp == null) { declareWarButton.setEnabled(false); return; }
        GameState gs = MainWindow.this.controller.getState();
        String sel = (String) warTargetSelector.getSelectedItem();
        if (sel == null || sel.equals("(select target)")
                || sel.equals("(no legal targets)")) {
            declareWarButton.setEnabled(false);
            return;
        }
        // Determine target type
        Player raceTarget = null;
        LocationCard locTarget = null;
        for (Player p : gs.getPlayers()) {
            if (!p.isHuman() && p.getName().equals(sel)) {
                raceTarget = p;
                break;
            }
        }
        if (raceTarget == null) {
            for (Player p : gs.getPlayers()) {
                if (p.isHuman()) continue;
                for (LocationCard loc : p.getLocations()) {
                    if (loc.getTitle().equals(sel)) {
                        locTarget = loc;
                        break;
                    }
                }
                if (locTarget != null) break;
            }
        }
        if (raceTarget == null && locTarget == null) {
            declareWarButton.setEnabled(false);
            return;
        }
        WarKind kind = raceTarget != null ? WarKind.RACE_TARGET : WarKind.LOCATION_TARGET;
        boolean actionTurn = gs.getPhase() == GamePhase.ACTION
            && gs.getActivePlayer() == hp
            && MainWindow.this.controller.isWaitingForHuman();
        declareWarButton.setEnabled(actionTurn
            && rules.canInitiateWarConflict(hp, kind, raceTarget, locTarget, gs));
    }

    /**
     * B5-2253 (B5-0310 F2): the opponents the engine would accept as the target
     * of THIS conflict, right now. Every predicate is the engine's own —
     * canInitiateConflict(p, c, target, state) is the same call
     * GameController makes before it accepts INITIATE_CONFLICT, and
     * canAffectTarget is the B5-0677 Negative Power target gate. Neither is
     * re-derived here; the UI reads the answer and renders it.
     *
     * A null return means the conflict cannot be initiated at all, which is
     * distinct from "legal but no targets": an empty list is the shape that
     * needs a visible "(no legal targets)" row so the human can tell a
     * selector that is empty on purpose from one that never got filled.
     */
    private java.util.List<Player> legalConflictTargets(ConflictCard conflict) {
        Player hp = humanPlayer();
        GameState st = MainWindow.this.controller.getState();
        if (hp == null || conflict == null || st.getActiveConflict() != null) {
            return null;
        }
        // requiresTarget: without a candidate there is nothing to offer, so the
        // caller shows the empty state rather than a target it must then use.
        Participation part = conflict.getParticipation();
        boolean needsTarget = (part != null && part.isRequiresTarget());
        java.util.List<Player> legal = new java.util.ArrayList<Player>();
        for (Player p : st.getPlayers()) {
            if (p == null || p.isHuman()) continue;
            if (!rules.canInitiateConflict(hp, conflict, p, st)) continue;
            if (!rules.canAffectTarget(conflict, p)) continue;
            legal.add(p);
        }
        if (legal.isEmpty() && needsTarget) return null;
        return legal;
    }

    /**
     * B5-2253: single-pair legality question, asked of the engine. Used both to
     * build the dropdown and to re-check a pick made against a list built
     * earlier.
     */
    private boolean isLegalConflictTarget(Player target) {
        if (target == null) return false;
        if (!(selectedCard instanceof ConflictCard)) return false;
        Player hp = humanPlayer();
        GameState st = MainWindow.this.controller.getState();
        if (hp == null) return false;
        return rules.canInitiateConflict(hp, (ConflictCard) selectedCard, target, st)
            && rules.canAffectTarget((ConflictCard) selectedCard, target);
    }

    /**
     * B5-2253: the in-hand conflicts the engine would let the human initiate
     * this turn. Filtered by the same canInitiateConflict call the engine makes
     * at submit time (via the null-target 3-arg form for conflicts that do not
     * require one, and per-candidate for the ones that do), so the picker can
     * never offer a card Initiate would refuse.
     */
    private java.util.List<Card> declarableConflicts() {
        java.util.List<Card> out = new java.util.ArrayList<Card>();
        Player hp = humanPlayer();
        GameState st = MainWindow.this.controller.getState();
        if (hp == null || st.getActiveConflict() != null) return out;
        // B5-4163: conflicts are declared during the CONFLICT round (the rulebook
        // II.3 turn shape), not just the ACTION phase. The old ACTION-only gate
        // left the picker empty during the conflict round, so the Initiate button
        // could never fire and the human was stuck. Accepting CONFLICT here
        // reunites the picker with the restored runConflictRound.
        if (st.getPhase() != GamePhase.ACTION && st.getPhase() != GamePhase.CONFLICT) return out;
        // B5-4163: during the CONFLICT round the active-player index is not
        // advanced to the declarer (runConflictRound iterates initiative order),
        // so check the conflict-declaration wait flag as well.
        boolean humanDeclWindow = MainWindow.this.controller.isWaitingForHumanConflictDeclaration();
        if (!humanDeclWindow && (st.getActivePlayer() != hp || !MainWindow.this.controller.isWaitingForHuman())) {
            return out;
        }
        for (Card c : hp.getHand()) {
            if (!(c instanceof ConflictCard)) continue;
            ConflictCard cc = (ConflictCard) c;
            if (rules.canInitiateConflict(hp, cc, st)) { out.add(cc); continue; }
            // requiresTarget: legal only against some target the engine accepts.
            java.util.List<Player> targets = legalConflictTargets(cc);
            if (targets != null && !targets.isEmpty()) out.add(cc);
        }
        return out;
    }

    /**
     * B5-2253: repopulate the conflict picker from live state, preserving the
     * current selection where it is still declarable. Rebuilding on every
     * refresh is what keeps it honest — the hand, the phase and the turn all
     * move without the human touching the control.
     */
    private void refreshConflictPicker() {
        java.util.List<Card> cards = declarableConflicts();
        Card previous = null;
        int previousIndex = conflictPickerSelector.getSelectedIndex();
        if (previousIndex >= 0 && previousIndex < conflictPickerCards.size()) {
            previous = conflictPickerCards.get(previousIndex);
        }
        conflictPickerPopulating = true;
        try {
            conflictPickerSelector.removeAllItems();
            conflictPickerCards.clear();
            for (Card c : cards) {
                conflictPickerCards.add(c);
                conflictPickerSelector.addItem(c.getTitle());
            }
            if (cards.isEmpty()) {
                conflictPickerSelector.setEnabled(false);
                conflictPickerSelector.setToolTipText("No conflict card in your "
                    + "hand can be initiated right now.");
            } else {
                int reselect = (previous == null) ? -1 : conflictPickerCards.indexOf(previous);
                if (reselect >= 0) conflictPickerSelector.setSelectedIndex(reselect);
                conflictPickerSelector.setEnabled(true);
                conflictPickerSelector.setToolTipText("Conflict cards in your "
                    + "hand you can initiate right now. Picking one declares it "
                    + "as the selected card (B5-2253).");
            }
        } finally {
            conflictPickerPopulating = false;
        }
    }

    private void refreshAttackControl(Player human, boolean attackWindow) {
        Card previous = selectedAttackTarget;
        selectedAttackTarget = null;
        if (!attackWindow || human == null || selectedCard == null) {
            attackButton.setEnabled(false);
            updateAttackButton();
            return;
        }
        Conflict conflict = MainWindow.this.controller.getState().getActiveConflict();
        if (conflict == null) {
            attackButton.setEnabled(false);
            updateAttackButton();
            return;
        }
        java.util.ArrayList<Player> participants =
            new java.util.ArrayList<Player>(conflict.getParticipants());
        boolean anyTarget = false;
        for (int i = 0; i < participants.size() && !anyTarget; i++) {
            Player participant = participants.get(i);
            if (participant == human) continue;
            java.util.ArrayList<Card> committed =
                new java.util.ArrayList<Card>(conflict.getCommittedCards(participant));
            for (int j = 0; j < committed.size(); j++) {
                Card target = committed.get(j);
                if (rules.canAttackConflictParticipant(human, selectedCard, target, conflict)) {
                    anyTarget = true;
                    break;
                }
            }
        }
        // B5-4216: the attack target is now chosen by clicking in targeting
        // mode, so the button is enabled whenever at least one legal target
        // exists (a click then enters targeting mode).
        attackButton.setEnabled(anyTarget);
        if (previous != null
                && rules.canAttackConflictParticipant(human, selectedCard, previous, conflict)) {
            selectedAttackTarget = previous;
        }
    }

    private void updateAttackButton() {
        Player human = humanPlayer();
        Conflict conflict = MainWindow.this.controller.getState().getActiveConflict();
        boolean canAttack = MainWindow.this.controller.isWaitingForHumanConflictAttack()
            && human != null && conflict != null && selectedCard != null
            && selectedAttackTarget != null
            && rules.canAttackConflictParticipant(human, selectedCard, selectedAttackTarget, conflict);
        attackButton.setEnabled(canAttack);
    }

    /** B5-4216: update heal button enablement based on current target selection. */
    private void updateHealButton() {
        Player human = humanPlayer();
        boolean canHeal = human != null && selectedCard instanceof CharacterCard
            && rules.canHealCharacter(human, (CharacterCard) selectedCard);
        healButton.setEnabled(canHeal);
    }

    /** B5-4216: update repair button enablement based on current target selection. */
    private void updateRepairButton() {
        Player human = humanPlayer();
        boolean canRepair = human != null;
        if (selectedCard instanceof FleetCard) {
            canRepair = canRepair && rules.canRepairCard(human, (FleetCard) selectedCard);
        } else if (selectedCard instanceof LocationCard) {
            canRepair = canRepair && rules.canRepairCard(human, (LocationCard) selectedCard);
        } else {
            canRepair = false;
        }
        repairButton.setEnabled(canRepair);
    }

    /** B5-4216: update aftermath play buttons based on current target selection. */
    private void updateAftermathButtons() {
        Player human = humanPlayer();
        Conflict conflict = MainWindow.this.controller.getState().getActiveConflict();
        boolean canPlay = false;
        if (human != null && conflict != null && selectedAftermathCard != null
                && selectedAftermathTarget != null) {
            boolean initiatorWon = rules.initiatorWon(conflict, conflict.getWinner());
            canPlay = rules.canPlayAftermath(human, selectedAftermathCard, conflict,
                initiatorWon, selectedAftermathTarget, MainWindow.this.controller.getState());
        }
        playAftermathButton.setEnabled(canPlay);
    }

    /** B5-4158: refresh the aftermath play controls during the AFTERMATH phase. */
    private void refreshAftermathControl(Player human, boolean aftermathWindow) {
        selectedAftermathCard = null;
        selectedAftermathTarget = null;
        aftermathCards.clear();
        aftermathTargets.clear();
        aftermathCardSelector.removeAllItems();
        aftermathCardSelector.addItem("(no aftermaths)");
        aftermathTargetSelector.removeAllItems();
        aftermathTargetSelector.addItem("(select target)");

        if (!aftermathWindow || human == null) {
            aftermathCardSelector.setEnabled(false);
            aftermathTargetSelector.setEnabled(false);
            playAftermathButton.setEnabled(false);
            passAftermathButton.setEnabled(false);
            return;
        }

        Conflict conflict = MainWindow.this.controller.getState().getActiveConflict();
        if (conflict == null) {
            aftermathCardSelector.setEnabled(false);
            aftermathTargetSelector.setEnabled(false);
            playAftermathButton.setEnabled(false);
            passAftermathButton.setEnabled(false);
            return;
        }

        boolean initiatorWon = rules.initiatorWon(conflict, conflict.getWinner());
        boolean hasPlayable = false;

        // Find all playable aftermath cards in hand
        for (Card c : human.getHand()) {
            if (c instanceof AftermathCard) {
                AftermathCard am = (AftermathCard) c;
                // Check if there's at least one legal target for this aftermath
                for (Player target : conflict.getParticipants()) {
                    if (rules.canPlayAftermath(human, am, conflict, initiatorWon, target, MainWindow.this.controller.getState())) {
                        aftermathCards.add(am);
                        aftermathCardSelector.addItem(am.getTitle() + " (" + am.getSubtype() + ")");
                        hasPlayable = true;
                        break;
                    }
                }
                // Also check initiator as target for non-participant aftermaths
                if (!conflict.getParticipants().contains(conflict.getInitiator())) {
                    if (rules.canPlayAftermath(human, am, conflict, initiatorWon, conflict.getInitiator(), MainWindow.this.controller.getState())) {
                        if (!aftermathCards.contains(am)) {
                            aftermathCards.add(am);
                            aftermathCardSelector.addItem(am.getTitle() + " (" + am.getSubtype() + ")");
                            hasPlayable = true;
                        }
                    }
                }
            }
        }

        if (!hasPlayable) {
            aftermathCardSelector.setEnabled(false);
            aftermathTargetSelector.setEnabled(false);
            playAftermathButton.setEnabled(false);
            passAftermathButton.setEnabled(true); // Can still pass
            return;
        }

        aftermathCardSelector.setEnabled(true);
        passAftermathButton.setEnabled(true);
        // Target selector will be enabled when a card is selected
        updateAftermathControls();
    }

    private void updateAftermathControls() {
        Player human = humanPlayer();
        Conflict conflict = MainWindow.this.controller.getState().getActiveConflict();
        if (conflict == null || human == null) {
            aftermathTargetSelector.setEnabled(false);
            playAftermathButton.setEnabled(false);
            return;
        }

        boolean initiatorWon = rules.initiatorWon(conflict, conflict.getWinner());
        aftermathTargets.clear();
        aftermathTargetSelector.removeAllItems();
        aftermathTargetSelector.addItem("(select target)");

        if (selectedAftermathCard == null) {
            aftermathTargetSelector.setEnabled(false);
            playAftermathButton.setEnabled(false);
            return;
        }

        // Find legal targets for the selected aftermath
        for (Player target : conflict.getParticipants()) {
            if (rules.canPlayAftermath(human, selectedAftermathCard, conflict, initiatorWon, target, MainWindow.this.controller.getState())) {
                aftermathTargets.add(target);
                aftermathTargetSelector.addItem(target.getName());
            }
        }
        // Also check initiator for non-participant aftermaths
        if (!conflict.getParticipants().contains(conflict.getInitiator())) {
            if (rules.canPlayAftermath(human, selectedAftermathCard, conflict, initiatorWon, conflict.getInitiator(), MainWindow.this.controller.getState())) {
                aftermathTargets.add(conflict.getInitiator());
                aftermathTargetSelector.addItem(conflict.getInitiator().getName());
            }
        }

        if (aftermathTargets.isEmpty()) {
            aftermathTargetSelector.setEnabled(false);
            playAftermathButton.setEnabled(false);
            return;
        }

        aftermathTargetSelector.setEnabled(true);
        // Auto-select first target if none selected
        if (selectedAftermathTarget == null && !aftermathTargets.isEmpty()) {
            selectedAftermathTarget = aftermathTargets.get(0);
            aftermathTargetSelector.setSelectedIndex(1);
        }
        updatePlayAftermathButton();
    }

    private void updatePlayAftermathButton() {
        Player human = humanPlayer();
        Conflict conflict = MainWindow.this.controller.getState().getActiveConflict();
        boolean canPlay = MainWindow.this.controller.isWaitingForHumanAftermath()
            && human != null && conflict != null
            && selectedAftermathCard != null && selectedAftermathTarget != null
            && rules.canPlayAftermath(human, selectedAftermathCard, conflict,
                    rules.initiatorWon(conflict, conflict.getWinner()),
                    selectedAftermathTarget, MainWindow.this.controller.getState());
        playAftermathButton.setEnabled(canPlay);
    }

    /** B5-0326 F5: show preview of the cost for the currently selected card. */
    private void refreshCostPreview() {
        Player hp = humanPlayer();
        if (hp == null) {
            costLabel.setText("  ");
            return;
        }
        GamePhase phase = MainWindow.this.controller.getState().getPhase();
        if (phase != GamePhase.ACTION) {
            costLabel.setText("  ");
            return;
        }
        StringBuilder sb = new StringBuilder();
        // B5-1049: surface the generic-play cost gate in the UI
        // Show unaffordable cards (non-character cards with cost that player cannot afford)
        java.util.List<String> unaffordableCards = new java.util.ArrayList<String>();
        for (Card c : hp.getHand()) {
            // Skip characters - they have sponsor/promote costs handled in B5-0326 section
            if (c instanceof CharacterCard) continue;
            if (!c.getFaction().isPlayableBy(hp.getFaction())) continue;
            int cost = c.getCost();
            if (cost > 0 && hp.getAppliedPool() < cost) {
                // B5-1038: affordability precheck - card is in hand but cannot afford the cost
                unaffordableCards.add(c.getTitle() + " (" + cost + " INF need)");
            }
        }
        if (!unaffordableCards.isEmpty()) {
            sb.append("UNAFFORDABLE: ");
            for (int i = 0; i < unaffordableCards.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(unaffordableCards.get(i));
            }
            sb.append("  |  ");
        }
        // B5-0326 F5: show preview of the cost for the currently selected character card
        CharacterCard ch = (selectedCard instanceof CharacterCard)
            ? (CharacterCard) selectedCard : null;
        if (ch == null && sb.length() == 0) {
            // No character selected AND no unaffordable cards - clear display
            costLabel.setText("  ");
            return;
        }
        // Sponsor (RECRUIT) cost
        if (ch != null && hp.getHand().contains(ch) && !ch.isRotated() && !ch.isFaceDown()) {
            int rc = rules.recruitCost(hp, ch);
            sb.append("Sponsor cost: " + rc + "  |  ");
        }
        // Promote cost
        if (ch != null && hp.getSupportingRole().contains(ch) && !ch.isRotated() && !ch.isFaceDown()) {
            int pc = rules.promotionCost(hp, ch);
            sb.append("Promote cost: " + pc + "  |  ");
        }
        // Build Influence not card-specific, but show if available
        if (rules.canBuildInfluence(hp)) {
            sb.append("Build Inf: -3 (+1 rating)  |  ");
        }
        if (sb.length() == 0) {
            costLabel.setText("  ");
        } else {
            // trim trailing "  |  "
            String txt = sb.toString();
            if (txt.endsWith("  |  ")) txt = txt.substring(0, txt.length() - 6);
            costLabel.setText("  " + txt);
        }
    }

    /** Called from the game controller (off EDT) whenever state changes. */
    public void onStateUpdate(final GameState state) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() { refresh(state); }
        });
    }

    private void refresh(GameState state) {
        boardPanel.update(state);
        Player human = state.getHumanPlayer();
        // B5-0361: broker the UI-held conflict + state into the hand panel so
        // the eligible-aftermath highlight consults the real 6-arg legality
        // (D2 targets + D4 registry) instead of guessing.
        handPanel.setResolvedConflictContext(boardPanel.getLastHeldConflict(), state);
        handPanel.update(human.getHand(), rules, human);
        // B5-1969: keep the influence tracker in step with every other readout.
        influenceTrackerPanel.update(state);
        tensionUnrestTrackerPanel.update(state);
        // B5-2265: the starting-game flow is a once-only step; this is what makes
        // its button go dead once the opening window closes.
        refreshStartingGameControl(state);

        // Append new log lines
        java.util.List<String> log = state.getLog();
        logArea.setText("");
        String currentPhasePrefix = null;
        int currentRound = -1;
        for (String line : log) {
            // B5-0331 F12: detect round/phase grouping from log prefixes
            String prefix = "";
            String content = line;
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0 && colonIdx < 20) {
                String candidate = line.substring(0, colonIdx).trim();
                // B5-0331a: escape-immune prefix tests. The regex literals
                // previously here ended up double-escaped (matching a literal
                // backslash) so round/phase grouping could never fire.
                if (MainWindow.isRoundPrefix(candidate)
                    || MainWindow.isPhasePrefix(candidate)) {
                    prefix = candidate + ":";
                    content = line.substring(colonIdx + 1).trim();
                }
            }
            // Insert phase/group headers when they change
            if (!prefix.isEmpty()) {
                if (!prefix.startsWith("Round")) {
                    // Phase line — insert phase header before this line
                    if (currentPhasePrefix == null || !currentPhasePrefix.equals(prefix)) {
                        logArea.append("\n── " + prefix + " ──\n");
                        currentPhasePrefix = prefix;
                    }
                } else {
                    // Round line — insert round header before this line
                    if (currentRound == -1 || !prefix.equals("Round " + currentRound)) {
                        logArea.append("\n═══════════════════════════════\n");
                        logArea.append(prefix + "\n");
                        logArea.append("═══════════════════════════════\n");
                        try {
                            currentRound = Integer.parseInt(prefix.substring(6).trim());
                        } catch (NumberFormatException e) {
                            currentRound = -1;
                        }
                    }
                }
            }
            logArea.append(content + "\n");
        }
        logArea.setCaretPosition(logArea.getDocument().getLength());

        // B5-4163: during the CONFLICT round the active-player index is not
        // advanced to the declarer, so also accept the conflict-declaration
        // wait flag — otherwise Pass and the Initiate button stay dark during
        // the conflict round even though the human is waiting to declare.
        boolean myTurn = (state.getActivePlayer() == human
            && MainWindow.this.controller.isWaitingForHuman())
            || MainWindow.this.controller.isWaitingForHumanConflictDeclaration();
        boolean activeConflict = state.getActiveConflict() != null;
        boolean attackWindow = activeConflict
            && MainWindow.this.controller.isWaitingForHumanConflictAttack();
        GamePhase phase = state.getPhase();

        // B5-0363: join controls are enabled only while the controller is
        // collecting this human seat's side for an active, legal conflict.
        // B5-2261 (B5-0310 F6): the join and pass windows carried NO phase term
        // at all before this row. They were correct only because the predicate
        // they do use is phase-exclusive BY CONSTRUCTION in the engine — a
        // gap B5-2123 named as R2 and recorded as "an unasserted gate, not a
        // wrong one". Asserting it here makes the phase gate explicit and
        // local: if the engine ever opens a join window outside
        // CONFLICT_RESOLUTION, the UI now goes dark rather than lighting a
        // control the engine will refuse.
        boolean conflictPhase = (phase == GamePhase.CONFLICT_RESOLUTION
                                 || phase == GamePhase.CONFLICT);
        boolean joiningConflict = conflictPhase
            && activeConflict
            && MainWindow.this.controller.isWaitingForHumanConflictJoin()
            && rules.canJoinConflict(human, state.getActiveConflict())
            && state.getActiveConflict().canJoinConflict(human);
        supportButton.setEnabled(joiningConflict);
        opposeButton.setEnabled(joiningConflict);
        refreshParticipantList(state, human, joiningConflict);
        // B5-4155: the join picker mirrors the join window exactly. Auto-show
        // only once the frame itself is on screen, so headless harnesses and
        // construction-time refreshes never throw a window at the desktop.
        if (joinPickerPanel != null) {
            joinPickerPanel.update(state, joiningConflict);
            if (joiningConflict && isShowing() && joinPicker != null && !joinPicker.detached) {
                joinPicker.item.setSelected(true);
                joinPicker.floatPanel();
            }
        }

        passButton.setText(attackWindow ? "Skip Attack" : "Pass Turn");
        // Pass in the action round is an ACTION-phase act; Skip Attack belongs
        // to the conflict attack window. In the CONFLICT round Pass declines
        // the declaration. All three terms are now named.
        // B5-4159: the join window gets the fourth term. During a join wait
        // the controller is NOT in waitForHumanAction, so myTurn is false and
        // the old predicate left Pass dark exactly when the human needed a
        // decline path — with the Actions window hidden and the join picker
        // blank, the conflict round could never end. Pass submits
        // GameAction.pass(), which submitHumanAction accepts as a join
        // decline, so this is always a live exit from the wait.
        // B5-4165: the Draw round gets the fifth term — the human's buy
        // window. Pass ends the elective buys and releases the engine to
        // victory check; without it the observable Draw phase could never
        // end. Same submitHumanAction(GameAction.pass()) path as above.
        passButton.setEnabled(((phase == GamePhase.ACTION) && myTurn)
                              || ((phase == GamePhase.CONFLICT) && myTurn)
                              || attackWindow
                              || joiningConflict
                              || MainWindow.this.controller.isWaitingForHumanDrawEnd());

        // B5-0328 F4: split Play/Initiate — enablement lives in one authority,
        // updatePlayInitiateButtons(); dispatch in playOnly() / initiateOnly().
        updatePlayInitiateButtons();

        // B5-2253 (B5-0310 F2): rebuild the conflict picker from live state on
        // every refresh, so it lists what is declarable NOW. It sits after
        // updatePlayInitiateButtons because both read the selection the picker
        // can change; the picker's own listener is suppressed during the
        // rebuild, so this never re-enters selection.
        refreshConflictPicker();

        // B5-2261 (B5-0310 F6 + F8): the two readouts a player needs before
        // touching the toolbar — which phase, and who acts in which order.
        //
        // F8 was PARTIAL until now: the label carried only the ACTIVE player
        // ("whose turn is it"), which answers F8's question only when the table
        // has one player. DONE B5-2125 and B5-2135 both established that
        // initiative order is COMPUTED NOWHERE in the engine — turn order is
        // GameState.currentPlayerIndex walking the player list in construction
        // order — so a UI-computed order could mint a second notion of turn
        // order that contradicts the engine.
        //
        // That is why the chain below is LABELLED as the rulebook order and
        // not as "the order turns happen in": it is sorted lowest-Influence
        // first per rulebook III :382 and :302, with the ambassador ability
        // tie-break (:382 Diplomacy, Intrigue, Psi, then Leadership, higher
        // equalling higher initiative). It is a READ-ONLY projection of
        // player data and it drives no dispatch. The authoritative
        // whose-turn fact is still state.getActivePlayer(), shown separately,
        // so the two can never be confused for each other. Closing the gap
        // between them is an engine row, not this one (ui-only scope).
        phaseLabel.setText(phaseBannerText(state, myTurn));
        initiativeLabel.setText(initiativeChainText(state));
        boolean actionPhase = (phase == GamePhase.ACTION);
        CharacterCard ch = (selectedCard instanceof CharacterCard)
            ? (CharacterCard) selectedCard : null;

        // B5-0487: Censure opponent-fleet target picker
        refreshCensureControl(human, actionPhase && myTurn);

        // B5-2379: the Sponsor / Promote / Build Influence gates read the
        // selection, so they live in refreshSponsorPromoteBuildInfluence
        // alongside the refresh() call below — applyCardSelection() and
        // clearSelection() share it instead of each deriving their own.
        refreshSponsorPromoteBuildInfluence(human, ch, actionPhase, myTurn);

        // B5-0701: surrender availability. The engine decides; this only renders.
        refreshSurrenderControl(human);

        // B5-2267: forfeit availability. The engine decides; this only renders.
        refreshForfeitControl(human);

        // B5-0402: Attack - enabled only for a selected legal pair in the live conflict window
        Card selCard = selectedCard;
        // B5-2261: attackWindow came from the engine wait-flag alone; it now
        // also names its phase, same assertion as the join window above.
        refreshAttackControl(human, conflictPhase && attackWindow);

        // B5-4158: aftermath play controls during AFTERMATH phase
        boolean aftermathPhase = (phase == GamePhase.AFTERMATH);
        boolean aftermathWindow = aftermathPhase
            && MainWindow.this.controller.isWaitingForHumanAftermath();
        refreshAftermathControl(human, aftermathWindow);

        // Heal / Repair: the engine is the authority (B5-0423). The action
        // handlers already gate on rules.canHealCharacter / canRepairCard, so
        // duplicating a partial predicate here only let the two drift — the
        // hand-rolled version omitted !isRotated and the undamaged-IC aid
        // path, so it disabled a legal move. One predicate, one answer.
        boolean canHeal = actionPhase && myTurn && ch != null
            && rules.canHealCharacter(human, ch);
        healButton.setEnabled(canHeal);

        boolean canRepair = actionPhase && myTurn && selCard != null
            && (selCard instanceof FleetCard || selCard instanceof LocationCard)
            && rules.canRepairCard(human, selCard);
        repairButton.setEnabled(canRepair);

        // B5-0380: lifecycle controls are action-phase-only. The disabled
        // discard control remains visible for a Major agenda and its tooltip
        // explains why replacement is the available path.
        refreshAgendaControls(human, actionPhase && myTurn);

        // B5-0381: contingency reveal control — enabled when the human has
        // unrevealed contingencies placed under in-play hosts and it is
        // the human's Action turn.
        refreshContingencyRevealControl(human, actionPhase && myTurn);

        // B5-0401: Tier-1 remainder action UI — Lead Fleet + Use Rotate Effect
        refreshLeadFleetAndRotateEffect(human, actionPhase && myTurn);

        // B5-0487: Censure opponent-fleet target picker
        refreshCensureControl(human, actionPhase && myTurn);

        // B5-0404: mercenary bid control
        refreshMercenaryBidControl(human, actionPhase && myTurn);

        // B5-4175: elective hand-discard control (Draw round step D2). Gated on
        // isWaitingForHumanDrawEnd(), mirroring refreshMercenaryBidControl;
        // the selector is left disabled unless the window is open.
        refreshHandDiscardControl(human, actionPhase && myTurn);

        // B5-0407: declare-war control
        refreshDeclareWarControl(human, actionPhase && myTurn);

        // B5-0487: Censure opponent-fleet target picker
        refreshCensureControl(human, actionPhase && myTurn);

        // B5-0326 F5: refresh cost preview
        refreshCostPreview();

        // B5-2004: the Council ballot is offered exactly while the engine holds
        // an open vote session and this player has not yet voted.
        refreshCouncilVoteControl(state);

        // B5-2267: draw-round buy-cards offer.
        refreshDrawRoundBuyControl(human);

        if (state.isGameOver()) {
            VictoryPathResult vpr = rules.checkVictoryPath(state);
            if (vpr != null && vpr.getPath() != null) {
                Player winner = state.getWinner();
                StringBuilder sb = new StringBuilder("GAME OVER — ");
                sb.append(vpr.getPath()).append(" victory");
                if (winner != null) {
                    sb.append("  |  ").append(winner.getName());
                    sb.append(" (Power: ").append(vpr.getPower()).append(")");
                    // Show victory type distinction for Major vs Standard
                    if (vpr.getPath() == VictoryPath.MAJOR) {
                        sb.append(" — MAJOR (20+ Power, lead \u226510)");
                    } else if (vpr.getPath() == VictoryPath.STANDARD) {
                        sb.append(" — STANDARD (20+ Power, strictly greatest)");
                    }
                    // Show leader powers context
                    int nextHighest = 0;
                    for (Player p : state.getPlayers()) {
                        if (p != winner && !p.hasForfeited() && !p.hasSurrendered()) {
                            if (p.getInfluence() > nextHighest) nextHighest = p.getInfluence();
                        }
                    }
                    if (nextHighest > 0) {
                        sb.append("  |  Next: ").append(nextHighest);
                    }
                }
                switch (vpr.getPath()) {
                    case AGENDA_CONDITION:
                        sb.append("  |  Agenda: ").append(vpr.getQualifierAsString());
                        break;
                    case LAST_STANDING:
                        sb.append("  |  Sole survivor");
                        break;
                    case STATION_CONDITION_2:
                        sb.append("  |  Station influence: ").append(vpr.getQualifierAsInt());
                        break;
                    case MAJOR:
                    case STANDARD:
                        sb.append("  |  Lead: ").append(vpr.getQualifierAsInt());
                        break;
                }
                statusLabel.setText(sb.toString());
            } else {
                statusLabel.setText("GAME OVER — No winner");
            }
            passButton.setEnabled(false);
            playCardOnlyButton.setEnabled(false);
            initiateConflictButton.setEnabled(false);
        } else if (MainWindow.this.controller.isStalledOnTie()) {
            // Playtest fix: the B5-2275 stall guard stops the run WITHOUT a
            // winner on a repeated all-pass tie, leaving isGameOver() false.
            // Without this branch that dead stop rendered as a frozen round
            // with a live board and a Pass button that submits nowhere.
            statusLabel.setText("STALLED — repeated all-pass rounds with no strictly-greatest "
                + "leader; the rulebook names no winner (see log).");
        } else {
            if (attackWindow) {
                statusLabel.setText("Round " + state.getRoundNumber()
                    + "  |  Conflict: select attacker and target, or Pass");
            } else if (joiningConflict) {
                statusLabel.setText("Round " + state.getRoundNumber()
                    + "  |  Conflict: choose Support, Oppose, or Pass to sit out");
            } else if (state.getPhase() == GamePhase.CONFLICT) {
                statusLabel.setText("Round " + state.getRoundNumber()
                    + "  |  CONFLICT round: declare one conflict or Pass"
                    + (myTurn ? "  ← YOUR DECLARATION" : ""));
            } else if (MainWindow.this.controller.isWaitingForHumanDrawEnd()) {
                // B5-4165: the Draw round's human window is the only DRAW
                // state where the human is expected to act — buy cards at 3
                // INF each or Pass to end the round.
                statusLabel.setText("Round " + state.getRoundNumber()
                    + "  |  DRAW round: buy cards at 3 INF or Pass to end the round");
            } else {
                // Playtest fix: show the pass tally. The D6 action round ends
                // only on a consecutive full-table pass, so one player's Pass
                // merely accumulates while rivals act — without this readout a
                // human's Pass looks like it should end the round.
                int passed = 0;
                for (Player pl : state.getPlayers()) {
                    if (pl.isPassed()) passed++;
                }
                statusLabel.setText("Round " + state.getRoundNumber()
                    + "  |  " + state.getPhase()
                    + "  |  Active: " + state.getActivePlayer().getName()
                    + (myTurn ? "  ← YOUR TURN" : "")
                    + "  |  Passed: " + passed + "/" + state.getPlayers().size());
            }
        }
    }

    /** B5-0380: refresh the agenda lifecycle affordances from current state. */
    private void refreshAgendaControls(Player human, boolean actionTurn) {
        if (human == null) {
            discardAgendaButton.setEnabled(false);
            replaceAgendaButton.setEnabled(false);
            revealAgendaButton.setEnabled(false);
            revealContingencyButton.setEnabled(false);
            return;
        }
        AgendaCard current = human.getAgenda();
        boolean major = current != null && current.isMajorAgenda();
        discardAgendaButton.setText(major ? "Discard Agenda (Major)" : "Discard Agenda");
        discardAgendaButton.setToolTipText(major
            ? "Major agendas cannot be discarded; select another Major agenda and use Replace Agenda."
            : "Discard your current minor agenda.");
        discardAgendaButton.setEnabled(actionTurn && rules.canDiscardAgenda(human));

        CharacterCard leader = findUnrotatedIC(human);
        boolean validReplacement = selectedCard instanceof AgendaCard
            && rules.canReplaceAgenda(human, (AgendaCard) selectedCard, leader);
        replaceAgendaButton.setEnabled(actionTurn && validReplacement);
        replaceAgendaButton.setToolTipText(major
            ? "Select a Major agenda in hand; an Inner Circle character rotates and the current Major is removed from the game."
            : "Select an eligible agenda in hand; an Inner Circle character rotates and the current agenda is removed from the game.");

        revealAgendaButton.setEnabled(actionTurn && rules.canRevealAgenda(human));
        revealAgendaButton.setToolTipText("Reveal your face-down agenda; it takes effect now.");
    }

    /** B5-0381: refresh the contingency reveal control from current state. */
    private void refreshContingencyRevealControl(Player human, boolean actionTurn) {
        if (human == null || contingencySelector == null) {
            if (contingencySelector != null) {
                contingencySelector.removeAllItems();
                contingencySelector.addItem("(none)");
                contingencySelector.setEnabled(false);
            }
            revealContingencyButton.setEnabled(false);
            return;
        }
        GameState gs = MainWindow.this.controller.getState();
        java.util.List<ContingencyCard> placed = gs.getPlacedContingencies(human);
        boolean hasUnrevealed = false;
        contingencySelector.removeAllItems();
        for (int i = 0; i < placed.size(); i++) {
            ContingencyCard cc = placed.get(i);
            if (!cc.isRevealed() && rules.canRevealContingency(human, cc)) {
                hasUnrevealed = true;
            }
            String label = cc.getTitle();
            Card host = cc.getPlacedUnder();
            if (host != null) label += " (under " + host.getTitle() + ")";
            contingencySelector.addItem(label);
        }
        if (placed.isEmpty()) {
            contingencySelector.addItem("(none)");
            revealContingencyButton.setEnabled(false);
            revealContingencyButton.setToolTipText("No contingencies placed.");
        } else {
            contingencySelector.setEnabled(true);
            revealContingencyButton.setEnabled(actionTurn && hasUnrevealed);
            int unrevealed = 0;
            for (ContingencyCard cc : placed) {
                if (!cc.isRevealed()) unrevealed++;
            }
            revealContingencyButton.setToolTipText("Reveal a face-down contingency (" + unrevealed + " remaining).");
        }
}
 
    /** B5-0401: refresh Lead Fleet and Use Rotate Effect controls. */
    private void refreshLeadFleetAndRotateEffect(Player human, boolean actionTurn) {
        if (human == null || leadFleetSelector == null || rotateEffectKindSelector == null) {
            if (leadFleetSelector != null) {
                leadFleetSelector.removeAllItems();
                leadFleetSelector.addItem("(select fleet)");
                leadFleetSelector.setEnabled(false);
            }
            if (rotateEffectKindSelector != null) {
                rotateEffectKindSelector.setEnabled(false);
            }
            leadFleetButton.setEnabled(false);
            useRotateEffectButton.setEnabled(false);
            return;
        }
 
        // Lead Fleet: populate fleet selector if a valid leader character is selected
        CharacterCard leader = (selectedCard instanceof CharacterCard)
            ? (CharacterCard) selectedCard : null;
        boolean hasValidLeader = leader != null
            && !leader.isFaceDown() && !leader.isRotated()
            && (human.getInnerCircle().contains(leader) || human.getSupportingRole().contains(leader))
            && leader.canActAfterNeutralization();
 
        leadFleetSelector.removeAllItems();
        int fleetCount = 0;
        if (hasValidLeader) {
            for (FleetCard fl : human.getFleets()) {
                if (!fl.isRotated() && fl.canActAfterNeutralization() && fl.getLeader() == null) {
                    leadFleetSelector.addItem(fl.getTitle());
                    fleetCount++;
                }
            }
        }
        if (fleetCount == 0) {
            leadFleetSelector.addItem("(no eligible fleet)");
        }
        leadFleetSelector.setEnabled(fleetCount > 0);
        updateLeadFleetButton(actionTurn);
 
        // Use Rotate Effect: enabled when a supporting assistant is selected and ambassador exists
        CharacterCard assistant = (selectedAssistant instanceof CharacterCard)
            ? (CharacterCard) selectedAssistant : null;
        boolean hasValidAssistant = assistant != null
            && !assistant.isFaceDown() && !assistant.isRotated()
            && human.getSupportingRole().contains(assistant)
            && assistant.canActAfterNeutralization()
            && human.getAmbassador() != null;
 
        rotateEffectKindSelector.setEnabled(hasValidAssistant);
        useRotateEffectButton.setEnabled(actionTurn && hasValidAssistant);
    }
 
    /** Updates the Lead Fleet button enablement based on current fleet selection. */
    private void updateLeadFleetButton(boolean actionTurn) {
        if (leadFleetSelector == null || leadFleetButton == null) return;
        String sel = (String) leadFleetSelector.getSelectedItem();
        boolean hasFleet = sel != null && !sel.equals("(select fleet)") && !sel.equals("(no eligible fleet)");
        Player hp = humanPlayer();
        CharacterCard leader = (selectedCard instanceof CharacterCard)
            ? (CharacterCard) selectedCard : null;
        leadFleetButton.setEnabled(actionTurn && hasFleet && leader != null && hp != null
            && rules.canLeadFleet(hp, leader, selectedFleet));
    }

    // ── B5-0701: unconditional surrender availability (ui/, readout only) ──────

    /**
     * B5-0701: refresh the surrender control from the engine's own verdict.
     *
     * Design note — the engine is the ONLY authority here. This method does not
     * re-derive any part of the legality rule (phase, surrender/forfeit flags,
     * self-target, at-war, ambassador-in-play). It asks
     * {@link RulesEngine#canSurrender} once per candidate and renders the
     * answer, for the same reason the B5-0423 heal/repair controls were changed:
     * a hand-rolled partial predicate here is what let those two drift and
     * disable a legal move.
     *
     * An empty candidate list IS the "not available" signal, which is why the
     * readout is honest by construction — it cannot show a stale "available"
     * for a state the engine has since rejected.
     */
    private void refreshSurrenderControl(Player human) {
        if (surrenderTargetSelector == null || surrenderButton == null) return;
        GameState state = controller.getState();

        java.util.List<Player> legal = new java.util.ArrayList<Player>();
        if (human != null && !human.hasSurrendered() && !human.hasForfeited()) {
            for (Player p : state.getPlayers()) {
                if (rules.canSurrender(human, p, state)) legal.add(p);
            }
        }

        // Repopulate under the B5-0452 guard: a JComboBox auto-selects its
        // first item and fires the listener, which would otherwise set a
        // selection with no user action.
        surrenderSelectorPopulating = true;
        try {
            surrenderTargetSelector.removeAllItems();
            for (Player p : legal) {
                surrenderTargetSelector.addItem(p.getName() + " (+3)");
            }
            if (legal.isEmpty()) {
                surrenderTargetSelector.addItem("(surrender unavailable)");
                surrenderTargetSelector.setSelectedIndex(0);
                surrenderTargetSelector.setEnabled(false);
            } else {
                surrenderTargetSelector.setSelectedIndex(0);
                surrenderTargetSelector.setEnabled(true);
            }
        } finally {
            surrenderSelectorPopulating = false;
        }

        surrenderButton.setEnabled(!legal.isEmpty());
        refreshSurrenderHint(legal);
    }

    /**
     * B5-0701: the +3-influence consequence readout. The grant goes to the
     * TARGET (rulebook :817), so the label states the direction explicitly —
     * "you gain 3" would be a lie, and this is exactly the kind of detail a
     * readout exists to get right.
     */
    private void refreshSurrenderHint(java.util.List<Player> legal) {
        if (surrenderHintLabel == null) return;
        if (legal.isEmpty()) {
            surrenderHintLabel.setText(" ");
            surrenderHintLabel.setToolTipText(
                "Surrender is offered only during the draw round, to a race you "
                + "are at war with, and requires an ambassador in play "
                + "(rulebook :815-:819).");
            return;
        }
        Player t = legal.get(0);
        surrenderHintLabel.setText(t.getName() + " gains +3 inf");
        surrenderHintLabel.setToolTipText(
            "Surrendering ends YOUR game. " + t.getName() + " gains 3 influence "
            + "(" + t.getInfluence() + " -> " + (t.getInfluence() + 3) + ") and "
            + "receives your ambassador as an asylum character.");
    }

    /** B5-0701: enable/disable the commit button from the current selection. */
    private void refreshSurrenderButton() {
        if (surrenderButton == null) return;
        Player hp = humanPlayer();
        Player target = selectedSurrenderTarget();
        surrenderButton.setEnabled(hp != null && target != null
            && rules.canSurrender(hp, target, controller.getState()));
    }

    /** B5-0701: resolve the selected combo entry back to its Player. */
    private Player selectedSurrenderTarget() {
        if (surrenderTargetSelector == null) return null;
        Object sel = surrenderTargetSelector.getSelectedItem();
        if (sel == null) return null;
        String label = sel.toString();
        int plus = label.indexOf(" (+3)");
        String name = (plus >= 0) ? label.substring(0, plus) : label;
        if (name.startsWith("(")) return null;   // placeholder entry
        for (Player p : controller.getState().getPlayers()) {
            if (p.getName().equals(name)) return p;
        }
        return null;
    }

    // ── B5-2267: voluntary forfeit control (ui/, readout only) ────────────────────

    /**
     * B5-2267: refresh the forfeit control from the engine's own verdict.
     * Forfeit has no target — it is available when the engine says canForfeit
     * returns true (ACTION phase, not game over, player active, at least one
     * other active player exists). The engine is the ONLY authority.
     */
    private void refreshForfeitControl(Player human) {
        if (forfeitButton == null) return;
        GameState state = controller.getState();
        boolean canForfeit = false;
        if (human != null && !human.hasForfeited() && !human.hasSurrendered()) {
            canForfeit = rules.canForfeit(human, state);
        }
        forfeitButton.setEnabled(canForfeit);
        if (forfeitHintLabel != null) {
            if (canForfeit) {
                forfeitHintLabel.setText("Forfeit available");
                forfeitHintLabel.setToolTipText(
                    "Voluntarily forfeit (rulebook :815). You exit the game; "
                    + "your ambassador is discarded. If this leaves a sole "
                    + "survivor, they win.");
            } else {
                forfeitHintLabel.setText(" ");
                forfeitHintLabel.setToolTipText(
                    "Forfeit is only available during your Action turn, "
                    + "while the game is not over, and at least one other "
                    + "player is still active.");
            }
        }
    }

    // ── B5-2267: draw-round buy-cards offer (ui/, readout only) ────────────────────

    /**
     * B5-2267: refresh the draw-round buy-cards offer.
     * Visible only during DRAW phase. Shows the human player's applied influence
     * and enables the buy button when they have >=3 influence in their applied pool.
     * The engine auto-buys for all players in drawRound(), but this UI lets the
     * human manually trigger additional buys if they have pool remaining.
     */
    private void refreshDrawRoundBuyControl(Player human) {
        if (drawRoundBuyLabel == null || drawRoundBuyButton == null) return;
        GameState state = controller.getState();
        boolean isDrawPhase = (state.getPhase() == GamePhase.DRAW);
        
        if (!isDrawPhase || human == null || human.hasForfeited() || human.hasSurrendered()) {
            drawRoundBuyLabel.setText(" ");
            drawRoundBuyButton.setEnabled(false);
            return;
        }
        
        int appliedPool = human.getAppliedPool();
        drawRoundBuyLabel.setText("Draw Round: " + appliedPool + " INF available");
        drawRoundBuyButton.setEnabled(appliedPool >= 3);
        drawRoundBuyLabel.setToolTipText(
            "Draw Round step 4: may draw additional cards at 3 influence each "
            + "(rulebook :440-:465). Your applied pool: " + appliedPool + " INF.");
        drawRoundBuyButton.setToolTipText(
            "Buy one card for 3 influence from your applied pool. "
            + "Enabled when you have 3+ influence available.");
    }

    /** B5-0487: refresh the Censure opponent-fleet target picker from current state. */
    private void refreshCensureControl(Player human, boolean actionTurn) {
        // B5-0522: the parallel opponent-character picker refreshes alongside.
        refreshCharCensureControl(human, actionTurn);
        if (human == null || censureTargetSelector == null) {
            if (censureTargetSelector != null) {
                censureTargetSelector.setEnabled(false);
            }
            censurePlayButton.setEnabled(false);
            return;
        }
        // Only relevant when an Enhancement FLEET card is selected and it carries
        // the explicit-target seam (Censure-class today).
        if (selectedCard == null || !(selectedCard instanceof EnhancementCard)) {
            censureTargetSelector.setEnabled(false);
            censurePlayButton.setEnabled(false);
            censureTargetLabel.setText("");
            return;
        }
        EnhancementCard enh = (EnhancementCard) selectedCard;
        if (enh.getSubtype() == null || !enh.getSubtype().endsWith("_FLEET")) {
            censureTargetSelector.setEnabled(false);
            censurePlayButton.setEnabled(false);
            censureTargetLabel.setText("");
            return;
        }
        // Populate opponent fleets — face-up, unrotated fleets owned by non-human players.
        censureTargetCards.clear();
        censureTargetOwnerNames.clear();
        censureTargetSelector.removeAllItems();
        censureTargetSelector.addItem("(select opponent fleet)");
        GameState gs = MainWindow.this.controller.getState();
        for (Player p : gs.getPlayers()) {
            if (p.isHuman() || p.hasForfeited()) continue;
            for (FleetCard fl : p.getFleets()) {
                if (fl.isFaceDown() || fl.isRotated()) continue;
                censureTargetCards.add(fl);
                censureTargetOwnerNames.add(p.getName());
                censureTargetSelector.addItem(p.getName() + " — " + fl.getTitle());
            }
        }
        if (censureTargetCards.isEmpty()) {
            censureTargetSelector.setEnabled(false);
            censurePlayButton.setEnabled(false);
            censureTargetLabel.setText("No opponent fleets available.");
            return;
        }
        // Restore previous selection if it is still present.
        if (selectedCensureTarget != null) {
            int idx = censureTargetCards.indexOf(selectedCensureTarget);
            if (idx >= 0) {
                censureTargetSelector.setSelectedIndex(idx + 1);
            } else {
                censureTargetSelector.setSelectedIndex(0);
                selectedCensureTarget = null;
                selectedCensureTargetOwner = null;
            }
        } else {
            censureTargetSelector.setSelectedIndex(0);
        }
        censureTargetSelector.setEnabled(actionTurn);
        updateCensurePlayButton();
    }

    /**
     * B5-0522: refresh the opponent-CHARACTER target picker from current state.
     * Mirrors refreshCensureControl (B5-0487) for Enhancement CHARACTER cards
     * carrying the B5-0468 seam (shunned-class today). Reads only: face-up
     * characters across inner circle, supporting role, and ambassador of every
     * non-human, non-forfeited player — the B5-0506 characterById resolution
     * scope, so anything listed here is resolvable by the engine path.
     */
    private void refreshCharCensureControl(Player human, boolean actionTurn) {
        if (human == null || charCensureTargetSelector == null) {
            if (charCensureTargetSelector != null) {
                charCensureTargetSelector.setEnabled(false);
            }
            if (charCensurePlayButton != null) {
                charCensurePlayButton.setEnabled(false);
            }
            return;
        }
        if (selectedCard == null || !(selectedCard instanceof EnhancementCard)) {
            charCensureTargetSelector.setEnabled(false);
            charCensurePlayButton.setEnabled(false);
            charCensureTargetLabel.setText("");
            return;
        }
        EnhancementCard enh = (EnhancementCard) selectedCard;
        if (enh.getSubtype() == null || !enh.getSubtype().endsWith("_CHARACTER")) {
            charCensureTargetSelector.setEnabled(false);
            charCensurePlayButton.setEnabled(false);
            charCensureTargetLabel.setText("");
            return;
        }
        // Populate opponent characters — face-up, owned by non-human players.
        // Scope mirrors CardEffects.characterById (B5-0506): inner circle,
        // supporting role, then ambassador. No rotate check — the engine
        // resolves characters by face-up only.
        charCensureTargetCards.clear();
        charCensureTargetOwnerNames.clear();
        charCensureTargetSelector.removeAllItems();
        charCensureTargetSelector.addItem("(select opponent character)");
        GameState gs = MainWindow.this.controller.getState();
        for (Player p : gs.getPlayers()) {
            if (p.isHuman() || p.hasForfeited()) continue;
            for (CharacterCard ch : p.getInnerCircle()) {
                if (ch.isFaceDown()) continue;
                charCensureTargetCards.add(ch);
                charCensureTargetOwnerNames.add(p.getName());
                charCensureTargetSelector.addItem(p.getName() + " — " + ch.getTitle());
            }
            for (CharacterCard ch : p.getSupportingRole()) {
                if (ch.isFaceDown()) continue;
                charCensureTargetCards.add(ch);
                charCensureTargetOwnerNames.add(p.getName());
                charCensureTargetSelector.addItem(p.getName() + " — " + ch.getTitle());
            }
            CharacterCard amb = p.getAmbassador();
            if (amb != null && !amb.isFaceDown()) {
                charCensureTargetCards.add(amb);
                charCensureTargetOwnerNames.add(p.getName());
                charCensureTargetSelector.addItem(p.getName() + " — " + amb.getTitle());
            }
        }
        if (charCensureTargetCards.isEmpty()) {
            charCensureTargetSelector.setEnabled(false);
            charCensurePlayButton.setEnabled(false);
            charCensureTargetLabel.setText("No opponent characters available.");
            return;
        }
        // Restore previous selection if it is still present.
        if (selectedCharCensureTarget != null) {
            int idx = charCensureTargetCards.indexOf(selectedCharCensureTarget);
            if (idx >= 0) {
                charCensureTargetSelector.setSelectedIndex(idx + 1);
            } else {
                charCensureTargetSelector.setSelectedIndex(0);
                selectedCharCensureTarget = null;
                selectedCharCensureTargetOwner = null;
            }
        } else {
            charCensureTargetSelector.setSelectedIndex(0);
        }
        charCensureTargetSelector.setEnabled(actionTurn);
        updateCharCensurePlayButton();
    }

    /** B5-0522: enable the character-target play button when a character is explicitly selected. */
    private void updateCharCensurePlayButton() {
        if (charCensureTargetSelector == null || charCensurePlayButton == null) return;
        int idx = charCensureTargetSelector.getSelectedIndex();
        boolean hasSelection = idx > 0 && idx - 1 < charCensureTargetCards.size();
        if (hasSelection) {
            selectedCharCensureTarget = charCensureTargetCards.get(idx - 1);
            selectedCharCensureTargetOwner = charCensureTargetOwnerNames.get(idx - 1);
        } else {
            selectedCharCensureTarget = null;
            selectedCharCensureTargetOwner = null;
        }
        // Must be ACTION phase, my turn, and an opponent character is
        // explicitly selected (the target seam is set BY the user's pick, so
        // gating on hasExplicitTarget() here would never enable — same reason
        // as the B5-0487 fleet picker).
        Player hp = humanPlayer();
        GameState gs = MainWindow.this.controller.getState();
        boolean actionTurn = gs.getPhase() == GamePhase.ACTION
            && gs.getActivePlayer() == hp
            && MainWindow.this.controller.isWaitingForHuman();
        charCensurePlayButton.setEnabled(actionTurn && hasSelection);
        charCensureTargetLabel.setText(hasSelection
            ? "Target: " + selectedCharCensureTargetOwner + " — " + selectedCharCensureTarget.getTitle()
            : "Opponent character target:");
    }

    /** B5-0522: play the selected Enhancement targeting the chosen opponent character. */
    private void playCharCensureWithTarget() {
        if (selectedCharCensureTarget == null || selectedCharCensureTargetOwner == null) return;
        if (!(selectedCard instanceof EnhancementCard)) return;
        EnhancementCard enh = (EnhancementCard) selectedCard;
        // Set the explicit opponent target via the B5-0468 seam.
        enh.setOpponentTarget(selectedCharCensureTarget.getId(), selectedCharCensureTargetOwner);
        MainWindow.this.controller.submitHumanAction(GameAction.playCard(enh));
        clearSelection();
    }

    /** B5-0487: enable the Censure play button when a fleet is explicitly selected. */
    private void updateCensurePlayButton() {
        if (censureTargetSelector == null || censurePlayButton == null) return;
        int idx = censureTargetSelector.getSelectedIndex();
        boolean hasSelection = idx > 0 && idx - 1 < censureTargetCards.size();
        if (hasSelection) {
            selectedCensureTarget = censureTargetCards.get(idx - 1);
            selectedCensureTargetOwner = censureTargetOwnerNames.get(idx - 1);
        } else {
            selectedCensureTarget = null;
            selectedCensureTargetOwner = null;
        }
        // Must be ACTION phase, my turn, and an opponent fleet is explicitly
        // selected (the target seam is set BY the user's pick, so gating on
        // hasExplicitTarget() here would never enable — the card has no target
        // until the picker fires).
        Player hp = humanPlayer();
        GameState gs = MainWindow.this.controller.getState();
        boolean actionTurn = gs.getPhase() == GamePhase.ACTION
            && gs.getActivePlayer() == hp
            && MainWindow.this.controller.isWaitingForHuman();
        censurePlayButton.setEnabled(actionTurn && hasSelection);
        censureTargetLabel.setText(hasSelection
            ? "Target: " + selectedCensureTargetOwner + " — " + selectedCensureTarget.getTitle()
            : "Opponent fleet target:");
    }

    /** B5-0487: play the selected Enhancement targeting the chosen opponent fleet. */
    private void playCensureWithTarget() {
        if (selectedCensureTarget == null || selectedCensureTargetOwner == null) return;
        if (!(selectedCard instanceof EnhancementCard)) return;
        EnhancementCard enh = (EnhancementCard) selectedCard;
        // Set the explicit opponent target via the B5-0468 seam.
        enh.setOpponentTarget(selectedCensureTarget.getId(), selectedCensureTargetOwner);
        MainWindow.this.controller.submitHumanAction(GameAction.playCard(enh));
        clearSelection();
    }
 
    /**
     * B5-0379: rebuild the read-only join-window participant list from the
     * live Conflict D14 sides API (B5-0309). Runs on the EDT inside
     * refresh(); reads only, so it never mutates model or engine state.
     * Committed lists are snapshotted before iteration so a commit landing
     * mid-refresh cannot throw ConcurrentModificationException.
     */
    private void refreshParticipantList(GameState state, Player human, boolean joiningConflict) {
        if (participantListModel == null || joinPromptLabel == null) return;
        participantListModel.removeAllElements();
        Conflict c = state.getActiveConflict();
        if (c == null) {
            joinPromptLabel.setText("No active conflict.");
            return;
        }
        StringBuffer title = new StringBuffer();
        title.append(c.getCard().getTitle());
        title.append(" [").append(c.getConflictType()).append("]");
        if (c.getInitiator() != null) {
            title.append(" by ").append(c.getInitiator().getName());
        }
        if (c.getTarget() != null) {
            title.append(" targets ").append(c.getTarget().getName());
        }
        title.append(" : support ").append(c.supportTotal());
        title.append(" vs oppose ").append(c.oppositionTotal());
        joinPromptLabel.setText(title.toString());
        java.util.List players = state.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            Player pl = (Player) players.get(i);
            boolean support = c.isSupporting(pl);
            boolean oppose = c.isOpposing(pl);
            if (!support && !oppose) continue;
            java.util.ArrayList cards = new java.util.ArrayList(c.getCommittedCards(pl));
            StringBuffer line = new StringBuffer();
            if (support) {
                line.append("[support] ");
            } else {
                line.append("[oppose]  ");
            }
            line.append(pl.getName());
            if (pl == human) line.append(" (you)");
            line.append(": ");
            if (cards.isEmpty()) {
                line.append("(no cards committed)");
            } else {
                for (int j = 0; j < cards.size(); j++) {
                    if (j > 0) line.append(", ");
                    line.append(((Card) cards.get(j)).getTitle());
                }
                line.append("  (total ").append(c.playerTotal(pl)).append(")");
            }
            participantListModel.addElement(line.toString());
        }
        if (joiningConflict && human != null && !c.isSupporting(human) && !c.isOpposing(human)) {
            participantListModel.addElement("-- choose Support or Oppose --");
        }
        if (participantListModel.isEmpty()) {
            participantListModel.addElement("(no participants yet)");
        }
    }


    /**
     * B5-0328 F4: single authority for both split buttons' enablement. Reads
     * live state + current selection; never mutates selection, so it is safe
     * from state refreshes AND from programmatic selector changes.
     */
    /**
     * B5-2379: Sponsor / Promote / Build Influence enablement, one authority.
     * These three gates read the selection, but they used to be derived in
     * refresh() only, so selecting a hand card never lit Sponsor or Promote
     * until the next engine state change — measured live: every Sponsor
     * conjunct true including rules.canRecruit, button still dark. Callers:
     * refresh() on every state change, applyCardSelection() on every
     * hand/board/picker selection, clearSelection() after a submit so a spent
     * selection goes dark at once instead of lingering lit.
     */
    /**
     * B5-4149: populate the promote leader selector with unrotated + actionable
     * Inner Circle members. The selector IS the availability signal — an empty
     * candidate list means no leader can back the promotion, so the selector
     * shows a placeholder and the Promote button stays dark. Mirrors the
     * B5-4081 Build Influence selector pattern.
     */
    private void populatePromoteLeaderSelector(Player human,
            CharacterCard ch, boolean actionPhase, boolean myTurn) {
        promoteLeaderSelector.removeAllItems();
        boolean relevant = actionPhase && myTurn
            && ch != null && ch instanceof CharacterCard
            && !ch.isFaceDown() && !ch.isRotated()
            && human.getSupportingRole().contains(ch);
        if (!relevant) {
            promoteLeaderSelector.addItem("(select leader)");
            promoteLeaderSelector.setEnabled(false);
            return;
        }
        for (CharacterCard ic : human.getInnerCircle()) {
            if (!ic.isRotated() && ic.canActAfterNeutralization()) {
                promoteLeaderSelector.addItem(ic.getTitle());
            }
        }
        if (promoteLeaderSelector.getItemCount() == 0) {
            promoteLeaderSelector.addItem("(no actionable leader)");
            promoteLeaderSelector.setEnabled(false);
        } else {
            promoteLeaderSelector.setEnabled(true);
        }
    }

    /**
     * B5-4149: resolve the chosen leader from the promote selector. Returns the
     * CharacterCard for the currently selected leader title, or null if the
     * selector holds a placeholder or the title no longer matches any IC member.
     * The action handler falls back to a fresh scan if this returns null.
     */
    private CharacterCard resolvePromoteLeader(Player human) {
        Object sel = promoteLeaderSelector.getSelectedItem();
        if (sel == null) return null;
        String title = (String) sel;
        if (title.startsWith("(")) return null; // placeholder
        for (CharacterCard ic : human.getInnerCircle()) {
            if (ic.getTitle().equals(title)
                    && !ic.isRotated() && ic.canActAfterNeutralization()) {
                return ic;
            }
        }
        return null;
    }

    /**
     * B5-4164: mirror of populatePromoteLeaderSelector for the widened Sponsor
     * control — lists the unrotated, actionable Inner Circle members that
     * executeSponsorCard would accept as the rotating leader (:657).
     */
    private void populateSponsorLeaderSelector(Player human) {
        sponsorLeaderSelector.removeAllItems();
        if (human == null) {
            sponsorLeaderSelector.addItem("(select leader)");
            sponsorLeaderSelector.setEnabled(false);
            return;
        }
        for (CharacterCard ic : human.getInnerCircle()) {
            if (!ic.isRotated() && ic.canActAfterNeutralization()) {
                sponsorLeaderSelector.addItem(ic.getTitle());
            }
        }
        if (sponsorLeaderSelector.getItemCount() == 0) {
            sponsorLeaderSelector.addItem("(no actionable leader)");
            sponsorLeaderSelector.setEnabled(false);
        } else {
            sponsorLeaderSelector.setEnabled(true);
        }
    }

    /**
     * B5-4164: resolve the chosen sponsor leader from the sponsor selector.
     * Returns null for a placeholder or a title that no longer matches an
     * actionable IC member — the same contract as resolvePromoteLeader.
     */
    private CharacterCard resolveSponsorLeader(Player human) {
        Object sel = sponsorLeaderSelector.getSelectedItem();
        if (sel == null) return null;
        String title = (String) sel;
        if (title.startsWith("(")) return null; // placeholder
        for (CharacterCard ic : human.getInnerCircle()) {
            if (ic.getTitle().equals(title)
                    && !ic.isRotated() && ic.canActAfterNeutralization()) {
                return ic;
            }
        }
        return null;
     }

    /**
     * B5-4081: populate the build influence leader selector with unrotated +
     * actionable Inner Circle members. The selector IS the availability signal
     * — an empty candidate list means no leader can build influence, so the
     * selector shows a placeholder and the Build Influence button stays dark.
     */
    private void populateBuildInfluenceLeaderSelector(Player human) {
        buildInfluenceSelectorPopulating = true;
        buildInfluenceLeaderSelector.removeAllItems();
        if (human == null) {
            buildInfluenceLeaderSelector.addItem("(select leader)");
            buildInfluenceLeaderSelector.setEnabled(false);
        } else {
            for (CharacterCard ic : human.getInnerCircle()) {
                if (!ic.isRotated() && ic.canActAfterNeutralization()) {
                    buildInfluenceLeaderSelector.addItem(ic.getTitle());
                }
            }
            if (buildInfluenceLeaderSelector.getItemCount() == 0) {
                buildInfluenceLeaderSelector.addItem("(no actionable leader)");
                buildInfluenceLeaderSelector.setEnabled(false);
            } else {
                buildInfluenceLeaderSelector.setEnabled(true);
            }
        }
        buildInfluenceSelectorPopulating = false;
    }

    /**
     * B5-4081: resolve the chosen leader from the build influence selector.
     * Returns the CharacterCard for the currently selected leader title, or
     * null if the selector holds a placeholder or the title no longer matches
     * an actionable IC member. The action handler falls back to a fresh scan
     * if this returns null.
     */
    private CharacterCard resolveBuildInfluenceLeader() {
        Player human = humanPlayer();
        if (human == null) return null;
        Object sel = buildInfluenceLeaderSelector.getSelectedItem();
        if (sel == null) return null;
        String title = (String) sel;
        if (title.startsWith("(")) return null; // placeholder
        for (CharacterCard ic : human.getInnerCircle()) {
            if (ic.getTitle().equals(title)
                    && !ic.isRotated() && ic.canActAfterNeutralization()) {
                return ic;
            }
        }
        return null;
    }

    /** Re-evaluate the Build Influence button after a selector change. */
    private void refreshBuildInfluenceButton() {
        Player hp = humanPlayer();
        GameState st = MainWindow.this.controller.getState();
        boolean actionPhase = (st.getPhase() == GamePhase.ACTION);
        boolean myTurn = (st.getActivePlayer() == hp
            && MainWindow.this.controller.isWaitingForHuman());
        boolean hasLeader = buildInfluenceLeaderSelector.getItemCount() > 0
            && !((String) buildInfluenceLeaderSelector.getItemAt(0)).startsWith("(");
        buildInfluenceButton.setEnabled(actionPhase && myTurn
            && hasLeader && rules.canBuildInfluence(hp));
    }

    private void refreshSponsorPromoteBuildInfluence(Player human,
            CharacterCard ch, boolean actionPhase, boolean myTurn) {
        // B5-4164: derive the sponsor target from the live selection. The ch
        // parameter carries the character branch; non-character sponsorship
        // reads selectedCard directly because applyCardSelection passes null
        // for every non-character selection.
        Card sel = selectedCard;
        boolean sponsorableOther = sel instanceof EnhancementCard
            || sel instanceof LocationCard || sel instanceof FleetCard;
        boolean selInHand = sel != null && human != null
            && human.getHand().contains(sel);

        // Populate the sponsor leader selector first: an Enhancement, Location
        // or Fleet needs an IC member to rotate (:657), and the widened gate
        // requires a real, currently-actionable pick so a lit Sponsor can
        // never refuse on click — executeSponsorCard re-checks exactly this
        // conjunction at its own commit point.
        populateSponsorLeaderSelector(human);
        boolean sponsorLeaderChosen = resolveSponsorLeader(human) != null;

        // Sponsor: ACTION phase, my turn, selected card is a ready character in
        // hand, AND the engine says the faction can afford it.
        // B5-2257: the conjunction with rules.canRecruit is what makes the lit
        // button agree with the branch it dispatches to. GameController's
        // RECRUIT_CHARACTER branch re-checks canRecruit at its own commit point
        // (GameController:384), so a lit button whose predicate is false is a
        // button that refuses on click — and pre-B5-2257 that refusal was a
        // silent no-op. The four SHAPE terms stay because canRecruit is
        // membership plus affordability ONLY (RulesEngine:452-455): dropping them
        // would WIDEN the gate, which is the B5-0423 hazard this file already
        // records for a hand-rolled Heal/Repair predicate.
        boolean characterSponsor = actionPhase && myTurn
            && ch != null && ch instanceof CharacterCard
            && !ch.isFaceDown() && !ch.isRotated()
            && human.getHand().contains(ch)
            && rules.hasReadySponsor(human)
            && rules.canRecruit(human, ch);

        // B5-4164: Sponsor (Enhancement / Location / Fleet): the SPONSOR_CARD
        // route. Gate = canSponsorCard plus an actionable leader chosen —
        // exactly what executeSponsorCard re-checks (canSponsorCard and the
        // leader-in-IC/not-rotated/actionable triple), so a lit button can
        // never refuse on click.
        boolean cardSponsor = actionPhase && myTurn
            && sponsorableOther && selInHand
            && sponsorLeaderChosen
            && rules.canSponsorCard(human, sel);

        sponsorButton.setEnabled(characterSponsor || cardSponsor);

        // B5-4164: Sponsor Group: the PLAY_CARD route through
        // applyGenericCardPlay, which re-checks canSponsorGroup for a group
        // (GameController:1273-1286) — the exact gate here. A group therefore
        // lights neither this-branch Sponsor (ch is null, sel is not an
        // Enhancement/Location/Fleet) nor generic Play (excluded below in
        // updatePlayInitiateButtons), leaving one control per sponsor route.
        boolean groupSponsor = actionPhase && myTurn
            && sel instanceof GroupCard && selInHand
            && rules.canSponsorGroup(human, (GroupCard) sel);
        sponsorGroupButton.setEnabled(groupSponsor);

        // Promote: populate the leader selector first, then enable the button
        // only when a supporting character is selected AND the selector has
        // at least one actionable leader. The selector itself gates availability.
        populatePromoteLeaderSelector(human, ch, actionPhase, myTurn);
        boolean hasActionableLeader = promoteLeaderSelector.getItemCount() > 0
            && !((String) promoteLeaderSelector.getItemAt(0)).startsWith("(");
        promoteButton.setEnabled(actionPhase && myTurn
            && ch != null && ch instanceof CharacterCard
            && !ch.isFaceDown() && !ch.isRotated()
            && human.getSupportingRole().contains(ch)
            && hasActionableLeader
            && rules.canPromote(human, ch));

        // B5-4081: populate the build influence leader selector from the engine's
        // verdict, then gate the button on having an actionable leader.
        populateBuildInfluenceLeaderSelector(human);
        boolean hasBuildInfluenceLeader = buildInfluenceLeaderSelector.getItemCount() > 0
            && !((String) buildInfluenceLeaderSelector.getItemAt(0)).startsWith("(");

        // Build Influence: ACTION phase, my turn, has unrotated + actionable IC
        buildInfluenceButton.setEnabled(actionPhase && myTurn
            && hasBuildInfluenceLeader
            && rules.canBuildInfluence(human));
    }

    private void updatePlayInitiateButtons() {
        GameState st = MainWindow.this.controller.getState();
        if (st.isGameOver()) return;
        // B5-4163: during the CONFLICT round the active-player index is not
        // advanced to the declarer, so also accept the conflict-declaration
        // wait flag. Without this the Initiate button is always dark in the
        // conflict round even though the human is waiting to declare.
        boolean myTurn = (st.getActivePlayer() == humanPlayer()
            && MainWindow.this.controller.isWaitingForHuman())
            || MainWindow.this.controller.isWaitingForHumanConflictDeclaration();
        boolean activeConflict = st.getActiveConflict() != null;
        GamePhase phase = st.getPhase();
        boolean conflictSelected = (selectedCard instanceof ConflictCard);
        // B5-2261 (B5-0310 F6): the pre-B5-2261 phaseAllowsAction listed four
        // phases — ACTION, CONFLICT_RESOLUTION, AFTERMATH, DRAW — while
        // canPlay also requires myTurn, which per the engine's own
        // construction implies ACTION. Three of the four were therefore
        // unreachable through the conjunction: inherited draft breadth that
        // B5-0458 recorded as deliberately untightened because tightening Play
        // was out of THAT row's scope. It is in this row's scope, and F6 is
        // precisely "disable off-phase controls", so the honest ACTION-only
        // term is written. Rulebook III :380: "Within the Action Round all
        // players act"; playing a card is an act of that round.
        boolean phaseAllowsAction = (phase == GamePhase.ACTION);
        // Declarations belong to the conflict round (rulebook III :366-:378),
        // never to the action loop: the Initiate Conflict button gates on
        // CONFLICT only. Previously ACTION-only (B5-0458/B5-2261); that term
        // described the old declare-inside-action flow this row replaces.
        boolean phaseAllowsInitiation = (phase == GamePhase.CONFLICT);
        boolean canInitiate = myTurn && conflictSelected && !activeConflict
            && phaseAllowsInitiation
            // B5-2253: ask the engine whether THIS (card, target) pair is legal
            // instead of inferring it from the selector's state. Pre-B5-2253
            // enablement was myTurn + phase + not-conflict, so a lit Initiate
            // could still be refused at submit. The 4-arg form carries the
            // declared target; a "(no target)" pick takes the 3-arg form,
            // which is precisely the question "does this conflict need one".
            && ((selectedTarget != null)
                  ? isLegalConflictTarget(selectedTarget)
                  : (noTargetChosen && rules.canInitiateConflict(
                        humanPlayer(), (ConflictCard) selectedCard, st)));
        // B5-0325 F2: when the selector is enabled for a conflict card, require
        // an explicit target so Initiate can't fire on a stale auto-fallback.
        // B5-2253: the explicit choice may now be "(no target)", which is legal
        // exactly when the engine's own 3-arg canInitiateConflict says the
        // conflict needs none — the same call the engine makes at submit time.
        // Reading enablement as a proxy (the pre-B5-2253 form) is gone: a
        // board-sourced conflict left it vacuously true.
        boolean targetChosen = (selectedTarget != null || noTargetChosen);
        boolean targetReady = !conflictSelected || targetChosen;
        boolean agendaNeedsLifecycleAction = selectedCard instanceof AgendaCard
            && !rules.canSponsorAgenda(humanPlayer(), (AgendaCard) selectedCard);
        // B5-0423: Play reads a card out of the hand, so require hand
        // containment. Without it, a board selection (IC / supporting / fleet /
        // location) is a non-ConflictCard and would light Play and dispatch an
        // illegal playCard for a card that is not in hand.
        boolean inHand = selectedCard != null
            && humanPlayer() != null
            && humanPlayer().getHand().contains(selectedCard);
        boolean canPlay = myTurn && inHand && !conflictSelected
            && !agendaNeedsLifecycleAction && phaseAllowsAction
            // B5-1171: the generic Play gate reads no cost and no type route;
            // a lit Play on a character can only reach the corrupting generic
            // dispatch, so characters never light it.
            && !(selectedCard instanceof CharacterCard)
            // B5-4164: a group's route is its own Sponsor Group control
            // (PLAY_CARD through applyGenericCardPlay with the B5-1999
            // canSponsorGroup gate); generic Play must not offer a second
            // path that bypasses that gate.
            && !(selectedCard instanceof GroupCard)
            // B5-1177: affordability precheck for non-conflict cards; the
            // B5-1038 generic play path charges raw cost so unaffordable
            // cards must be excluded at the button, not after click.
            && rules.canPlayCard(humanPlayer(), selectedCard);
        playCardOnlyButton.setEnabled(canPlay);
        initiateConflictButton.setEnabled(canInitiate && targetReady);
        refreshAgendaControls(humanPlayer(), myTurn && phase == GamePhase.ACTION);
    }

    /**
     * B5-2261 (B5-0310 F6): the phase banner text.
     *
     * The row asks for READY / CONFLICT / ACTION / AFTERMATH / DRAW. Those are
     * the five PHASES THE HUMAN ACTS IN, and they are not the five values of
     * GamePhase — the enum carries seven (SETUP, ACTION, MERCENARY,
     * CONFLICT_RESOLUTION, AFTERMATH, DRAW, END_ROUND). Mapping the enum onto
     * the five named phases by renaming would be a lie about the model, so the
     * mapping is stated explicitly and the two enum values with no human-action
     * window at all (SETUP, MERCENARY, END_ROUND) are shown under their own
     * names with a "(no actions)" marker rather than folded into a phase that
     * has live controls.
     *
     * SETUP -> READY: the pre-game window. The rulebook's opening round is the
     * Conflict round and the software's SETUP phase precedes it (:302 "start
     * with the first Conflict round"), so READY is the honest name for "cards
     * ready, nobody acting yet".
     * CONFLICT_RESOLUTION -> CONFLICT: the join / attack window. This is the
     * one that MUST stay distinguishable from ACTION, because Support, Oppose
     * and Attack are enabled here and are the only controls that are.
     * AFTERMATH -> AFTERMATH: engine-only today (GameController.java:872 sets
     * it and notifyUI()s without asking the human anything), so the banner is
     * how the player learns the table is resolving rather than hung.
     *
     * The myTurn suffix distinguishes "your phase, not your turn" from "your
     * phase, your turn" — the distinction F6's original wording ("gating knows
     * my-turn only") was about.
     */
    private String phaseBannerText(GameState state, boolean myTurn) {
        GamePhase p = state.getPhase();
        String name;
        String note;
        if (p == GamePhase.ACTION) {
            name = "ACTION";
            note = myTurn ? "your turn" : state.getActivePlayer().getName() + "'s turn";
        } else if (p == GamePhase.CONFLICT) {
            // Rulebook III :366-:378 — the declare step. Each seat declares
            // at most one conflict or passes; nothing resolves here.
            name = "CONFLICT";
            note = myTurn ? "declare one conflict or pass"
                          : state.getActivePlayer().getName() + " declares";
        } else if (p == GamePhase.CONFLICT_RESOLUTION) {
            name = "CONFLICT";
            // The conflict window is the one phase whose authority is the
            // ENGINE wait-flag, not the phase value: resolveCurrentConflict
            // opens the join window and then the attack window, both under this
            // phase (GameController.java:341/343 and :666/668).
            boolean window = MainWindow.this.controller.isWaitingForHumanConflictJoin()
                || MainWindow.this.controller.isWaitingForHumanConflictAttack();
            note = window ? "your window" : "resolving";
        } else if (p == GamePhase.AFTERMATH) {
            name = "AFTERMATH";
            note = "resolving";
        } else if (p == GamePhase.DRAW) {
            name = "DRAW";
            note = "resolving";
        } else if (p == GamePhase.SETUP) {
            name = "READY";
            note = "no actions";
        } else if (p == GamePhase.MERCENARY) {
            name = "MERCENARY";
            note = "no actions";
        } else if (p == GamePhase.END_ROUND) {
            name = "END OF ROUND";
            note = "no actions";
        } else {
            name = String.valueOf(p);
            note = "no actions";
        }
        return "Phase: " + name + "  (" + note + ")";
    }

    /**
     * B5-2261 (B5-0310 F8): the initiative chain, LOWEST FIRST.
     *
     * Rulebook III :382 — "Within the Action round ... play proceeds in
     * initiative order, i.e., each player alternates playing cards and taking
     * actions, in Initiative Order, from lowest to highest. The higher a
     * player's Influence Rating, the higher his initiative. Players with the
     * same Influence Rating are ranked in initiative order by comparing the
     * abilities of their ambassadors, with higher abilities equaling higher
     * initiative. Compare abilities in the following order: Diplomacy, Intrigue,
     * Psi, then Leadership." :302 repeats it for the opening round ("The player
     * with the lowest initiative may declare a conflict first").
     *
     * So the sort key is ASCENDING: lowest Influence Rating first, and on a tie
     * the LOWER ambassador ability value comes first, because higher ability
     * means HIGHER initiative means LATER. Getting that direction backwards is
     * the whole bug this row exists to prevent, so the comparison is written
     * out per-component rather than as a negated total.
     *
     * A player with no ambassador is ranked as all-zero abilities, which is the
     * neutral reading: an absent card is not a high ability. Ties beyond that
     * keep the engine's own player-list order (stable insertion sort), so the
     * display is deterministic and never invents an order the data does not
     * support.
     *
     * This is a READ-ONLY projection. It does not reorder players, does not
     * touch GameState, and drives no dispatch — see the note at the call site
     * for why a UI row must not become the second notion of turn order.
     */
    private String initiativeChainText(GameState state) {
        List<Player> ordered = new ArrayList<Player>(state.getPlayers());
        // Insertion sort: stable, Java 6, no Comparator/lambda needed.
        for (int i = 1; i < ordered.size(); i++) {
            Player cur = ordered.get(i);
            int j = i - 1;
            while (j >= 0 && initiativeKeyCompare(ordered.get(j), cur) > 0) {
                ordered.set(j + 1, ordered.get(j));
                j--;
            }
            ordered.set(j + 1, cur);
        }
        Player active = state.getActivePlayer();
        StringBuilder sb = new StringBuilder("Initiative (lowest first): ");
        for (int i = 0; i < ordered.size(); i++) {
            if (i > 0) sb.append(" > ");
            Player p = ordered.get(i);
            sb.append(p.getName());
            sb.append(" [inf ").append(p.getInfluence()).append("]");
            if (p.isHuman()) sb.append(" (you)");
            if (p == active) sb.append(" *now*");
        }
        return sb.toString();
    }

    /**
     * B5-2261: negative when {@code a} acts BEFORE {@code b} in rulebook
     * initiative order (lowest first). Influence Rating first, then the
     * ambassador abilities in the rulebook's comparison order — Diplomacy,
     * Intrigue, Psi, then Leadership — each ASCENDING.
     */
    private static int initiativeKeyCompare(Player a, Player b) {
        int c = a.getInfluence() - b.getInfluence();
        if (c != 0) return c;
        CharacterCard aa = a.getAmbassador();
        CharacterCard ab = b.getAmbassador();
        int a1 = (aa == null) ? 0 : aa.getDiplomacy();
        int b1 = (ab == null) ? 0 : ab.getDiplomacy();
        if (a1 != b1) return a1 - b1;
        int a2 = (aa == null) ? 0 : aa.getIntrigue();
        int b2 = (ab == null) ? 0 : ab.getIntrigue();
        if (a2 != b2) return a2 - b2;
        int a3 = (aa == null) ? 0 : aa.getPsi();
        int b3 = (ab == null) ? 0 : ab.getPsi();
        if (a3 != b3) return a3 - b3;
        int a4 = (aa == null) ? 0 : aa.getLeadership();
        int b4 = (ab == null) ? 0 : ab.getLeadership();
        return a4 - b4;
    }

    /** B5-0328 F4: dispatch for the "Play Card" button (never initiates). */
    private void playOnly() {
        // B5-1171: refuse characters the way ConflictCard is refused. B5-1109
        // measured that a dispatched CharacterCard reaching
        // applyGenericCardPlay is charged raw cost and discarded without ever
        // entering the supporting role; characters belong to the sponsor and
        // promote buttons, which gate on canRecruit/canPromote.
        if (selectedCard == null || selectedCard instanceof ConflictCard
                || selectedCard instanceof CharacterCard) return;
        // B5-0487: if the selected card is a Censure-class enhancement with an
        // explicit opponent target seam, the censure play button is the dedicated
        // path; the generic Play Card button stays for non-targeted plays.
        if (selectedCard instanceof EnhancementCard) {
            EnhancementCard enh = (EnhancementCard) selectedCard;
            // B5-0522: CHARACTER seam cards (shunned-class) now route through
            // their own picker button too, mirroring the B5-0487 fleet rule.
            if (enh.getSubtype() != null
                    && (enh.getSubtype().endsWith("_FLEET")
                        || enh.getSubtype().endsWith("_CHARACTER"))
                    && enh.hasExplicitTarget()) {
                return;   // censure play buttons own these paths
            }
        }
        MainWindow.this.controller.submitHumanAction(GameAction.playCard(selectedCard));
        clearSelection();
    }

    /** B5-0328 F4: dispatch for the "Initiate Conflict" button (never plays).
     *  B5-0452 (B5-0451 F3): the highest-influence auto-target fallback was
     *  removed — it was unreachable under the targetReady gate (the gate is
     *  false exactly when selectedTarget is null, the only case it covered)
     *  and implied a choice the user never made. The button can only fire
     *  with an explicit user-picked target now. */
    private void initiateOnly() {
        if (!(selectedCard instanceof ConflictCard)) return;
        // B5-0325 F2 as repaired by B5-0452: explicit user-selected target only.
        // B5-2253: the explicit choice may be a real player OR "(no target)";
        // re-ask the engine at dispatch so the click can never submit a pair
        // the engine refuses. A refusal clears the pick and leaves the card
        // selected rather than firing silently.
        Player target = selectedTarget;
        if (target != null && !isLegalConflictTarget(target)) {
            selectedTarget = null;
            noTargetChosen = false;
            statusLabel.setText("Cannot declare " + selectedCard.getTitle()
                + " against that target — choose another from the dropdown.");
            updatePlayInitiateButtons();
            return;
        }
        if (target == null && !noTargetChosen) return;
        if (target == null
                && !rules.canInitiateConflict(humanPlayer(),
                        (ConflictCard) selectedCard,
                        MainWindow.this.controller.getState())) {
            noTargetChosen = false;
            statusLabel.setText(selectedCard.getTitle()
                + " must declare a target — pick one from the dropdown.");
            updatePlayInitiateButtons();
            return;
        }
        MainWindow.this.controller.submitHumanAction(
            GameAction.initiateConflict(selectedCard, target));
        clearSelection();
    }

    /**
     * B5-0423: shared selection handler for hand and board clicks. `fromHand`
     * only decides whether the human target dropdown is populated — that is a
     * hand-only affordance (a board card is never a conflict to attack), so a
     * board selection leaves the dropdown disabled. Everything downstream
     * (assistant tracking, button enablement, cost preview, Tier-1 remainder
     * controls) is identical for both sources.
     */
    private void applyCardSelection(Card card, boolean fromHand) {
        selectedCard = card;
        refreshAttackControl(humanPlayer(),
            MainWindow.this.controller.isWaitingForHumanConflictAttack());
        if (fromHand && card instanceof ConflictCard) {
            // B5-0452 (B5-0451 F1+F2): repopulate with the listener suppressed
            // so the auto-selection fires nothing, and clear any stale target
            // from a previous card selection — the target must be an explicit
            // user choice for THIS conflict before Initiate enables.
            //
            // B5-2253: the list is now legal-targets-only, asked of the engine
            // per candidate, and a conflict that does not require a target gets
            // an explicit "(no target)" row so it stays declarable without one.
            // A requiresTarget conflict whose candidates are all illegal gets
            // "(no legal targets)" and stays disabled — the human sees why
            // instead of clicking a lit-for-nothing Initiate.
            noTargetChosen = false;
            java.util.List<Player> targets = legalConflictTargets((ConflictCard) card);
            targetSelectorPopulating = true;
            try {
                targetSelector.removeAllItems();
                if (targets == null) {
                    targetSelector.addItem(NO_LEGAL_TARGET);
                    targetSelector.setSelectedIndex(0);
                } else {
                    Participation part = ((ConflictCard) card).getParticipation();
                    boolean needsTarget = (part != null && part.isRequiresTarget());
                    if (!needsTarget) targetSelector.addItem(NO_TARGET_ITEM);
                    for (Player p : targets) targetSelector.addItem(p.getName());
                }
            } finally {
                targetSelectorPopulating = false;
            }
            selectedTarget = null;
            if (targets != null) {
                targetSelector.setEnabled(true);
                Participation part = ((ConflictCard) card).getParticipation();
                boolean needsTarget = (part != null && part.isRequiresTarget());
                boolean hasPick = needsTarget || !targets.isEmpty();
                statusLabel.setText("Selected: " + card.getTitle()
                    + "  |  Target: choose from dropdown"
                    + (hasPick ? "" : "  |  no opponent is a legal target — pick (no target)"));
            } else {
                targetSelector.setEnabled(false);
                statusLabel.setText("Selected: " + card.getTitle()
                    + "  |  No legal target for this conflict.");
            }
        } else {
            targetSelector.setEnabled(false);
            statusLabel.setText("Selected: " + card.getTitle()
                + "  |  " + card.getText());
        }
        // B5-0401: Track assistant selection for Use Rotate Effect
        Player hp = humanPlayer();
        if (card instanceof CharacterCard) {
            CharacterCard ch = (CharacterCard) card;
            if (hp != null && hp.getSupportingRole().contains(ch)) {
                selectedAssistant = ch;
            } else {
                selectedAssistant = null;
            }
        } else {
            selectedAssistant = null;
        }
        // B5-0401: Refresh Tier-1 remainder action UI
        GameState gs = controller.getState();
        // B5-0487: refresh censure opponent-fleet picker when a card is selected
        refreshCensureControl(hp, gs.getPhase() == GamePhase.ACTION
            && gs.getActivePlayer() == hp && controller.isWaitingForHuman());
        updatePlayInitiateButtons();
        // B5-2379: Sponsor / Promote / Build Influence read the selection, so
        // they re-derive here on the real selection path — refresh() alone
        // left them dark until the next engine state change.
        refreshSponsorPromoteBuildInfluence(hp,
            (card instanceof CharacterCard) ? (CharacterCard) card : null,
            gs.getPhase() == GamePhase.ACTION,
            gs.getActivePlayer() == hp && controller.isWaitingForHuman());
        // B5-0326 F5: refresh cost preview immediately
        refreshCostPreview();
        refreshLeadFleetAndRotateEffect(hp, gs.getPhase() == GamePhase.ACTION
            && gs.getActivePlayer() == hp && controller.isWaitingForHuman());
    }

    /** B5-0328 F4: drop the selection after a submit; buttons stay disabled. */
    private void clearSelection() {
        // B5-4216: exit targeting mode on clear
        exitTargetingMode();
        selectedCard = null;
        // B5-2379: a spent selection must go dark at once — without this the
        // Sponsor / Promote / Build Influence buttons linger lit on the
        // previous selection until the next engine refresh.
        refreshSponsorPromoteBuildInfluence(humanPlayer(), null,
            controller.getState().getPhase() == GamePhase.ACTION,
            controller.getState().getActivePlayer() == humanPlayer()
            && controller.isWaitingForHuman());
        selectedTarget = null;
        selectedFleet = null;
        selectedAssistant = null;
        targetSelector.setSelectedIndex(-1);
        targetSelector.setEnabled(false);
        // B5-2253: clearSelection dropped only the two target fields; the
        // "(no target)" pick is a third explicit choice and must reset with
        // them, or the next conflict selection inherits it and lights Initiate
        // with no target of the new card's own.
        noTargetChosen = false;
        targetSelector.removeAllItems();
        selectedAttackTarget = null;
        attackButton.setEnabled(false);
        if (leadFleetSelector != null) {
            leadFleetSelector.setSelectedIndex(-1);
            leadFleetSelector.setEnabled(false);
        }
        if (rotateEffectKindSelector != null) {
            rotateEffectKindSelector.setSelectedIndex(0);
            rotateEffectKindSelector.setEnabled(false);
        }
        // B5-0487: Censure opponent-fleet target picker reset
        if (censureTargetSelector != null) {
            censureTargetSelector.setSelectedIndex(-1);
            censureTargetSelector.setEnabled(false);
            censureTargetCards.clear();
            censureTargetOwnerNames.clear();
            selectedCensureTarget = null;
            selectedCensureTargetOwner = null;
            censurePlayButton.setEnabled(false);
        }
        // B5-0423: these three were populated by the submit that just resolved
        // but never reset, so the next turn started showing the previous card's
        // target / bid amount / contingency. Reset them to a harmless
        // non-actionable state. Each refresh path already re-derives the list
        // and re-enables on the next tick, so this is only a stale-display fix.
        if (warTargetSelector != null) {
            warTargetSelector.removeAllItems();
            warTargetSelector.addItem("(select target)");
            warTargetSelector.setEnabled(false);
        }
        if (mercenaryBidAmountSelector != null) {
            // Index 0, not -1: refreshMercenaryBid parses getSelectedItem()
            // with no null guard, and a -1 index makes it null.
            mercenaryBidAmountSelector.setSelectedIndex(0);
            mercenaryBidAmountSelector.setEnabled(false);
        }
        if (contingencySelector != null) {
            contingencySelector.removeAllItems();
            contingencySelector.addItem("(none)");
            contingencySelector.setEnabled(false);
        }
        // Drop the board highlight too, or the lime stroke outlives the
        // selection that produced it.
        boardPanel.clearSelection();
        playCardOnlyButton.setEnabled(false);
        initiateConflictButton.setEnabled(false);
        leadFleetButton.setEnabled(false);
        useRotateEffectButton.setEnabled(false);
        attackButton.setEnabled(false);
        healButton.setEnabled(false);
        repairButton.setEnabled(false);
    }

    /** B5-4216: enter targeting mode for the specified action.
     *  Sets up board and hand panels to dim non-targetable cards and
     *  handle click-to-select targeting.
     *  @param action the action type ("attack", "heal", "repair", "censure", "war", "aftermath")
     *  @param filter TargetFilter that returns true for valid targets
     */
    private void enterTargetingMode(String action, TargetFilter filter) {
        this.targetingMode = true;
        this.targetingAction = action;
        this.targetingFilter = filter;
        
        // Set up board and hand for targeting mode
        boardPanel.setTargetFilter(filter, new CardSelectedListener() {
            @Override public void onCardSelected(Card target) {
                onTargetSelected(target);
            }
        });
        handPanel.setTargetFilter(filter, new CardSelectedListener() {
            @Override public void onCardSelected(Card target) {
                onTargetSelected(target);
            }
        });
        statusLabel.setText("Targeting mode: click a valid target for " + action);
    }

    /** B5-4216: exit targeting mode, return to normal selection mode. */
    private void exitTargetingMode() {
        this.targetingMode = false;
        this.targetingAction = null;
        this.targetingFilter = null;
        boardPanel.clearTargetFilter();
        handPanel.clearTargetFilter();
        // Restore normal status
        if (selectedCard != null) {
            statusLabel.setText("Selected: " + selectedCard.getTitle() + "  |  " + selectedCard.getText());
        }
    }

    /** B5-4216: callback when a target is selected in targeting mode. */
    private void onTargetSelected(Card target) {
        if (!targetingMode || targetingFilter == null) return;
        if (!targetingFilter.isTarget(target)) return;
        
        // Handle the selected target based on the action type
        if ("attack".equals(targetingAction)) {
            selectedAttackTarget = target;
            updateAttackButton();
        } else if ("heal".equals(targetingAction)) {
            selectedCard = target; // heal uses selectedCard as the target
            updateHealButton();
        } else if ("repair".equals(targetingAction)) {
            selectedCard = target; // repair uses selectedCard as the target
            updateRepairButton();
        } else if ("censureFleet".equals(targetingAction)) {
            if (target instanceof FleetCard) {
                selectedCensureTarget = (FleetCard) target;
                selectedCensureTargetOwner = findOwnerName(target);
                updateCensurePlayButton();
            }
        } else if ("censureChar".equals(targetingAction)) {
            if (target instanceof CharacterCard) {
                selectedCharCensureTarget = (CharacterCard) target;
                selectedCharCensureTargetOwner = findOwnerName(target);
                updateCharCensurePlayButton();
            }
        } else if ("warPlayer".equals(targetingAction)) {
            // War targeting on board zones — find the Player by zone
            Player targetPlayer = findTargetPlayer(target);
            if (targetPlayer != null) {
                selectedWarTargetPlayer = targetPlayer;
                updateDeclareWarButton();
            }
        } else if ("warLocation".equals(targetingAction)) {
            // War targeting on locations
            if (target instanceof LocationCard) {
                selectedWarTargetLocation = (LocationCard) target;
                updateDeclareWarButton();
            }
        } else if ("aftermath".equals(targetingAction)) {
            // Aftermath targeting — find the Player that owns the selected card
            Player targetPlayer = findTargetPlayer(target);
            if (targetPlayer != null) {
                selectedAftermathTarget = targetPlayer;
                updateAftermathButtons();
            }
        }
        exitTargetingMode();
    }

    /** B5-4216: find the owner name of a card by searching game state players. */
    private String findOwnerName(Card card) {
        GameState gs = controller.getState();
        for (Player p : gs.getPlayers()) {
            if (p.getInnerCircle().contains(card) || p.getSupportingRole().contains(card)
                    || p.getFleets().contains(card) || p.getLocations().contains(card)) {
                return p.getName();
            }
        }
        return null;
    }

    /** B5-4216: find the Player that owns a card by searching game state players. */
    private Player findTargetPlayer(Card card) {
        GameState gs = controller.getState();
        for (Player p : gs.getPlayers()) {
            if (p.getInnerCircle().contains(card) || p.getSupportingRole().contains(card)
                    || p.getFleets().contains(card) || p.getLocations().contains(card)) {
                return p;
            }
        }
        return null;
    }

/** B5-4216: check if currently in targeting mode. */
    private boolean isTargetingMode() {
        return targetingMode;
    }

    /**
     * B5-0331a: true when candidate is a "Round N" log prefix (N = digits).
     * Char-scanning instead of a regex literal so backslash escaping can
     * never silently disable it.
     */
    public static boolean isRoundPrefix(String candidate) {
        if (candidate == null || !candidate.startsWith("Round ")) return false;
        String rest = candidate.substring(6).trim();
        if (rest.length() == 0) return false;
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /**
     * B5-0331a: true when candidate is a "Phase X" log prefix (X = word
     * characters). Char-scanning instead of a regex literal so backslash
     * escaping can never silently disable it.
     */
    public static boolean isPhasePrefix(String candidate) {
        if (candidate == null || !candidate.startsWith("Phase ")) return false;
        String rest = candidate.substring(6).trim();
        if (rest.length() == 0) return false;
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            boolean wordChar = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '_';
            if (!wordChar) return false;
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // B5-2008 -- Game setup: race pick, starting influence, initial tension
    //
    // WHAT THIS IS, and what it deliberately is not. It is a CONFIGURATION
    // SURFACE: it collects a proposed pre-game setup, enforces the rulebook
    // :208 one-race-per-player rule and the rulebook :284 influence floor, and
    // renders the rulebook :274 initial board tension for the chosen race set.
    // It mutates no game state and submits no GameAction, so -- like the
    // B5-0701 surrender readout and the B5-1978 F1 overlay -- it is not a
    // second authority and cannot be made illegal by game state.
    //
    // Player.faction is final and a live game already holds constructed
    // Players, so changing a seat's race mid-game is not something this
    // window can do; the dialog therefore reports its validated receipt into
    // the game log rather than pretending to apply it. Applying a setup is a
    // pre-game job for whoever constructs the Players, and MainWindow exposes
    // showSetupDialog(Component, SetupConfig) as that entry point.
    //
    // The tension table below is a transcription of rulebook :274, not a
    // second source of truth for tension: TensionMatrix (model/enums) owns
    // tension state, and this dialog only renders the opening values the
    // rulebook prescribes. Every line cites its rulebook anchor.
    // ─────────────────────────────────────────────────────────────────────────

    /** Rulebook :284 -- "Each player should now take four tokens ... This
     *  represents the starting Influence Rating of each faction." */
    static final int DEFAULT_STARTING_INFLUENCE = 4;

    /** Rulebook :284 -- "A faction's Influence Rating may never be reduced
     *  below three", so a proposed setup below 3 is rejected even though the
     *  spinner's numeric range deliberately starts lower and lets the user
     *  see the rule bite. */
    static final int MINIMUM_INFLUENCE_RATING = 3;

    /** Rulebook :208 -- "No two players may control the same race in the same
     *  game, unless you are using the alternate faction rules. In the premier
     *  edition ... there are four player races to choose from: Earth Alliance,
     *  Centauri Republic, Minbari Federation, and the Narn Regime."
     *
     *  NON_ALIGNED (the Great War League) is deliberately NOT offered: Main
     *  builds its AI seats from the same four, and offering a fifth selectable
     *  race the surrounding setup path cannot seat would be a control that
     *  promises something the window cannot deliver. */
    static final Faction[] PLAYER_RACES = new Faction[] {
        Faction.HUMAN, Faction.MINBARI, Faction.CENTAURI, Faction.NARN
    };

    /** Human-readable race name for the combo boxes (rulebook :208/:212). */
    static String raceLabel(Faction race) {
        if (race == null) return "(none)";
        if (race == Faction.HUMAN)      return "Earth Alliance";
        if (race == Faction.MINBARI)    return "Minbari Federation";
        if (race == Faction.CENTAURI)   return "Centauri Republic";
        if (race == Faction.NARN)       return "Narn Regime";
        return race.name();
    }

    /** True when the faction is one of the four Premier player races. */
    static boolean isPlayerRace(Faction race) {
        if (race == null) return false;
        for (int i = 0; i < PLAYER_RACES.length; i++) {
            if (PLAYER_RACES[i] == race) return true;
        }
        return false;
    }

    /**
     * B5-2008: the rulebook :274 opening tension between two races --
     * "Narn/Centauri tension starts at 4, the Human/Centauri tension begins at
     * 1, the Human/Minbari tension is at 3, and all other tensions begin at
     * 2." Symmetric, because :274 says the two directions "begin at the same
     * level" and only diverge later in play. A race toward itself has no
     * tension axis and reads 0.
     */
    static int initialTension(Faction a, Faction b) {
        if (a == null || b == null || a == b) return 0;
        boolean pair =
            (a == Faction.NARN     && b == Faction.CENTAURI) ||
            (a == Faction.CENTAURI && b == Faction.NARN);
        if (pair) return 4;
        pair =
            (a == Faction.HUMAN    && b == Faction.CENTAURI) ||
            (a == Faction.CENTAURI && b == Faction.HUMAN);
        if (pair) return 1;
        pair =
            (a == Faction.HUMAN    && b == Faction.MINBARI) ||
            (a == Faction.MINBARI  && b == Faction.HUMAN);
        if (pair) return 3;
        return 2;
    }

    /**
     * B5-2008: the rulebook :284 validation gate. Returns null when the
     * proposed setup is legal, otherwise a human-readable reason naming the
     * offending seat. Pure and static so it is testable headlessly, which is
     * the point: the dialog's Start button is disabled from THIS answer and
     * never from its own re-derivation of the rule.
     *
     * <p>Duplicate races are rejected here, not merely flagged: rulebook :208
     * is a hard uniqueness rule and the dialog's whole job is to enforce it.
     */
    static String validateSetup(Faction[] races, int[] startingInfluence) {
        if (races == null || races.length < 2) {
            return "A game needs at least two seats.";
        }
        if (startingInfluence == null || startingInfluence.length != races.length) {
            return "Starting influence must be set for every seat.";
        }
        for (int i = 0; i < races.length; i++) {
            if (!isPlayerRace(races[i])) {
                return "Seat " + (i + 1) + " has no race selected.";
            }
            if (startingInfluence[i] < MINIMUM_INFLUENCE_RATING) {
                return "Seat " + (i + 1) + " starts at " + startingInfluence[i]
                    + " influence; the rulebook (:284) floor is "
                    + MINIMUM_INFLUENCE_RATING + ".";
            }
            if (startingInfluence[i] > 99) {
                return "Seat " + (i + 1) + " starting influence is out of range.";
            }
            for (int j = 0; j < i; j++) {
                if (races[j] == races[i]) {
                    return "Seat " + (i + 1) + " repeats "
                        + raceLabel(races[i])
                        + "; the rulebook (:208) allows one race per player.";
                }
            }
        }
        return null;
    }

    /**
     * B5-2008: the initial board tension readout for the chosen race set,
     * rulebook :274. Reads every unordered pair once, since the opening values
     * are symmetric. Never throws: an unusable array yields a short reason
     * rather than an exception, because this is a rendering path.
     */
    static String buildInitialTensionReport(Faction[] races) {
        if (races == null || races.length < 2) return "(pick at least two races)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < races.length; i++) {
            if (races[i] == null) continue;
            for (int j = i + 1; j < races.length; j++) {
                if (races[j] == null || races[j] == races[i]) continue;
                if (sb.length() > 0) sb.append("\n");
                sb.append(raceLabel(races[i]));
                sb.append("  ->  ");
                sb.append(raceLabel(races[j]));
                sb.append("   : ");
                sb.append(initialTension(races[i], races[j]));
            }
        }
        return sb.length() == 0 ? "(no distinct races chosen)" : sb.toString();
    }

    /** B5-2008: an immutable proposed setup. */
    public static final class SetupConfig {
        private final Faction[] races;
        private final int[]    startingInfluence;

        SetupConfig(Faction[] races, int[] startingInfluence) {
            this.races            = races.clone();
            this.startingInfluence = startingInfluence.clone();
        }

        public int    size()                  { return races.length; }
        public Faction getRace(int seat)      { return races[seat]; }
        public int    getStartingInfluence(int seat) { return startingInfluence[seat]; }

        /** One receipt line per seat plus the validation verdict. */
        public String describe() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < races.length; i++) {
                if (i > 0) sb.append("; ");
                sb.append("Seat ").append(i + 1).append(": ")
                  .append(raceLabel(races[i]))
                  .append(" @ ").append(startingInfluence[i]).append(" influence");
            }
            return sb.toString();
        }
    }

    /**
     * B5-2008: the setup dialog. Modal, so the frame behind it cannot take
     * input while it is up, and a second copy cannot stack a second instance.
     *
     * <p>The Start button is enabled from validateSetup's answer alone and the
     * tension readout from buildInitialTensionReport's, both re-evaluated on
     * every widget change -- the dialog has exactly one rule engine (the
     * static gate) and one renderer (the static report), so the label and the
     * button can never disagree.
     */
    private static final class GameSetupDialog extends ValidatedDialog {
        private final JComboBox[] raceSelectors;
        private final JSpinner[] influenceSpinners;
        private final JTextArea  tensionArea;

        private SetupConfig result = null;

        GameSetupDialog(Frame owner, SetupConfig seed) {
            super(owner, "Babylon 5 CCG -- Game Setup", "Start Game");

            int seats = seed != null ? seed.size() : 4;
            raceSelectors = new JComboBox[seats];
            influenceSpinners = new JSpinner[seats];

            for (int i = 0; i < seats; i++) {
                JComboBox raceBox = new JComboBox();
                for (int r = 0; r < PLAYER_RACES.length; r++) {
                    raceBox.addItem(raceLabel(PLAYER_RACES[r]));
                }
                Faction seeded = seed != null ? seed.getRace(i) : null;
                raceBox.setSelectedIndex(indexOfPlayerRace(seeded));
                raceSelectors[i] = raceBox;

                JSpinner influence = new JSpinner(
                    new SpinnerNumberModel(DEFAULT_STARTING_INFLUENCE, 0, 99, 1));
                if (seed != null) {
                    influence.setValue(new Integer(seed.getStartingInfluence(i)));
                }
                influenceSpinners[i] = influence;

                raceBox.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        revalidateForm();
                    }
                });
                influence.addChangeListener(new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        revalidateForm();
                    }
                });
            }

            tensionArea = new JTextArea(buildInitialTensionReport(collectRaces()));
            tensionArea.setEditable(false);
            tensionArea.setBackground(new Color(10, 15, 30));
            tensionArea.setForeground(new Color(180, 200, 180));
            tensionArea.setFont(new Font("Monospaced", Font.PLAIN, 11));

            init();
        }

        private int indexOfPlayerRace(Faction race) {
            if (race != null) {
                for (int i = 0; i < PLAYER_RACES.length; i++) {
                    if (PLAYER_RACES[i] == race) return i;
                }
            }
            boolean[] taken = new boolean[PLAYER_RACES.length];
            for (int i = 0; i < raceSelectors.length; i++) {
                if (raceSelectors[i] != null) {
                    int idx = raceSelectors[i].getSelectedIndex();
                    if (idx >= 0 && idx < taken.length) taken[idx] = true;
                }
            }
            for (int i = 0; i < taken.length; i++) {
                if (!taken[i]) return i;
            }
            return 0;
        }

        private Faction[] collectRaces() {
            Faction[] races = new Faction[raceSelectors.length];
            for (int i = 0; i < races.length; i++) {
                races[i] = PLAYER_RACES[raceSelectors[i].getSelectedIndex()];
            }
            return races;
        }

        private int[] collectInfluence() {
            int[] influence = new int[influenceSpinners.length];
            for (int i = 0; i < influence.length; i++) {
                influence[i] = ((Number) influenceSpinners[i].getValue()).intValue();
            }
            return influence;
        }

        @Override
        protected JComponent buildBody() {
            int seats = raceSelectors.length;

            JPanel grid = new JPanel(new GridLayout(seats + 1, 3, 6, 4));
            grid.setBackground(new Color(10, 20, 10));
            grid.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(80, 130, 80)),
                "Seats  (rulebook :208 -- one race per player)",
                0, 0, new Font("SansSerif", Font.BOLD, 10),
                new Color(180, 200, 180)));

            JLabel raceHead = new JLabel("Seat");
            JLabel raceCol  = new JLabel("Race");
            JLabel inflCol  = new JLabel("Starting Influence");
            raceHead.setForeground(new Color(200, 220, 200));
            raceCol.setForeground(new Color(200, 220, 200));
            inflCol.setForeground(new Color(200, 220, 200));
            grid.add(raceHead);
            grid.add(raceCol);
            grid.add(inflCol);

            for (int i = 0; i < seats; i++) {
                JLabel seatLabel = new JLabel("Seat " + (i + 1));
                seatLabel.setForeground(new Color(200, 220, 200));
                grid.add(seatLabel);
                grid.add(raceSelectors[i]);
                grid.add(influenceSpinners[i]);
            }

            JScrollPane tensionScroll = new JScrollPane(tensionArea);
            tensionScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(80, 130, 80)),
                "Initial Board Tension  (rulebook :274)", 0, 0,
                new Font("SansSerif", Font.BOLD, 10), new Color(180, 200, 180)));
            tensionScroll.getViewport().setBackground(new Color(10, 15, 30));
            tensionScroll.setPreferredSize(new Dimension(460, 130));

            JPanel root = new JPanel(new BorderLayout(8, 8));
            root.add(grid, BorderLayout.NORTH);
            root.add(tensionScroll, BorderLayout.CENTER);
            return root;
        }

        @Override
        protected List<String> problems() {
            String problem = validateSetup(collectRaces(), collectInfluence());
            if (problem == null) {
                return Collections.emptyList();
            } else {
                return Collections.singletonList(problem);
            }
        }

        @Override
        protected void onAccept() {
            result = new SetupConfig(collectRaces(), collectInfluence());
        }

        SetupConfig getResult() { return result; }
    }

    // ── B5-2265: starting-game flow ─────────────────────────────────────────

    /**
     * B5-2265: runs the starting-game flow against the live table and logs the
     * receipt. Everything the flow decides lives in {@link StartingGameFlowModel};
     * this method is only the entry point plus the log write, so the flow can be
     * exercised headlessly by a probe without a dialog in the way.
     *
     * <p>The receipt goes to the game log rather than only to a dialog, because
     * the log is the one place a player who did not open the dialog can still
     * see why the table is ordered the way it is.
     */
    public List<String> runStartingGameFlow() {
        List<Card> pool;
        try {
            pool = DeckLoader.loadBothSets();
        } catch (java.io.IOException e) {
            List<String> failure = new ArrayList<String>();
            failure.add("Starting-game flow could not run: card pool failed to load ("
                + e.getMessage() + ")");
            stateLog(failure);
            return failure;
        }
        GameState state = controller.getState();
        StartingGameFlowModel model =
            new StartingGameFlowModel(state.getPlayers(), pool);
        List<String> receipt = model.applyStartingGame(state);
        stateLog(receipt);
        refresh(state);
        return receipt;
    }

    /** Writes receipt lines into the game log, so they survive the dialog closing. */
    private void stateLog(List<String> lines) {
        GameState state = controller.getState();
        for (int i = 0; i < lines.size(); i++) state.log(lines.get(i));
    }

    /**
     * B5-2265: the toolbar affordance. Opens the dialog when there is a display
     * and runs the flow directly when there is not, so the flow is reachable in
     * a headless probe instead of being silently skipped the way a dialog-only
     * path is.
     */
    private void showStartingGame() {
        if (GraphicsEnvironment.isHeadless()) {
            runStartingGameFlow();
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        StartingGameDialog dialog = new StartingGameDialog(owner, controller.getState());
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        refresh(controller.getState());
    }

    /**
     * B5-2265: the gate behind the toolbar button. The starting game is a
     * once-only event: it is legal only while round 1 is untouched and nobody has
     * declared a conflict, and after that the button goes dead rather than
     * offering to rewrite a hand somebody has already played from.
     */
    private void refreshStartingGameControl(GameState state) {
        if (startingGameButton == null) return;
        boolean open = StartingGameFlowModel.isOpeningWindowOpen(state);
        startingGameButton.setEnabled(open);
        startingGameButton.setToolTipText(open
            ? "Set up the opening: starting ambassador, the three typed cards beside it, "
              + "shuffle and cut, and the initiative order for the first conflict round."
            : "The starting game has already been set up (rulebook :228). This is a "
              + "once-only step and is closed once a conflict has been declared.");
    }

    /**
     * B5-2265: the starting-game dialog. A view and nothing more -- every
     * question it answers is answered by {@link StartingGameFlowModel}, which is
     * the same split B5-2287 made for the deck builder, for the same reason:
     * rules inside a JDialog cannot be reached without a display.
     */
    private static final class StartingGameDialog extends JDialog {
        private final JTextArea report;

        StartingGameDialog(Window owner, GameState state) {
            super(owner, "Babylon 5 CCG -- Starting Game", ModalityType.APPLICATION_MODAL);
            setLayout(new BorderLayout(8, 8));

            List<String> lines = new ArrayList<String>();
            lines.add("B5-2265 starting-game flow -- read from live state, not assumed.");
            lines.add("");
            lines.add("Rulebook :228 selects a starting hand of four; here is what was dealt:");
            StartingGameFlowModel flow = new StartingGameFlowModel(state.getPlayers(), null);
            List<String> deviation = flow.handDeviation(state);
            for (int i = 0; i < deviation.size(); i++) lines.add("  " + deviation.get(i));
            lines.add("");
            lines.add("Rulebook :258 -- shuffle, then allow an opponent to cut.");
            lines.add("");
            lines.add("Rulebook :352 -- initiative order, lowest first:");
            lines.add("  " + flow.initiativeDescription());
            lines.add("");
            lines.add("Rulebook :302 -- play begins with the first Conflict round; "
                + (flow.humanMayDeclareFirstConflict()
                    ? "you hold the lowest initiative and may declare first."
                    : "an opponent holds the lowest initiative and declares first."));
            lines.add("");
            lines.add("Press Begin to seat the ambassador, trim each hand to four, "
                + "shuffle and cut every deck.");

            report = new JTextArea();
            report.setEditable(false);
            report.setText(join(lines));
            report.setCaretPosition(0);
            report.setBackground(new Color(10, 15, 30));
            report.setForeground(new Color(180, 200, 180));
            report.setFont(new Font("Monospaced", Font.PLAIN, 10));
            report.setRows(18);
            report.setColumns(72);
            add(new JScrollPane(report), BorderLayout.CENTER);

            JButton begin = new JButton("Begin");
            begin.addActionListener(new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) { dispose(); }
            });
            JPanel south = new JPanel();
            south.add(begin);
            add(south, BorderLayout.SOUTH);
            pack();
        }

        private static String join(List<String> lines) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) sb.append(lines.get(i)).append("\n");
            return sb.toString();
        }
    }

    /**
     * B5-2008: the pre-game entry point. Returns the accepted setup, or null
     * if the dialog was cancelled. Returns null headlessly rather than
     * throwing, so a headless caller degrades to "no setup" instead of
     * crashing on a JDialog (HeadlessException).
     */
    public static SetupConfig showSetupDialog(Component parent, SetupConfig seed) {
        if (GraphicsEnvironment.isHeadless()) return null;
        // Component.getWindow() does not exist; SwingUtilities.getWindowAncestor
        // is the null-safe walk up to the owning Window and returns null when
        // parent is null, which is exactly the "unowned dialog" case.
        Window ownerWin = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        Frame owner = (ownerWin instanceof Frame) ? (Frame) ownerWin : null;
        GameSetupDialog dialog = new GameSetupDialog(owner, seed);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return dialog.getResult();
    }

    /** B5-2008: the live game's seats as a seed proposal for the dialog. */
    public SetupConfig currentSetupConfig() {
        List<Player> players = controller.getState().getPlayers();
        int seats = players.size();
        Faction[] races = new Faction[seats];
        int[] influence = new int[seats];
        for (int i = 0; i < seats; i++) {
            Player p = players.get(i);
            races[i] = p.getFaction();
            influence[i] = p.getInfluence();
        }
        return new SetupConfig(races, influence);
    }

    // ── B5-2004: Council vote dialog (rulebook :791-:797) ──────────────────
    //
    // The dialog is a BALLOT SURFACE ONLY. It renders the rulebook's pass rule
    // (:797 "at least one more Yes than No"), the League tie-break (:791 "the
    // League ... may cast one vote to break any tie") and the unplayed-race
    // abstention (:793 "ambassadors from races not currently being played are
    // considered to be part of the vote, but by default they abstain"), then
    // hands the human's chosen ballot to the engine. It never decides whether
    // the measure passed: that is B5-1993's resolveCouncilVote, and re-deriving
    // it here would give the queue two authorities for one rule. It also never
    // tallots a verdict of its own -- the running counts it displays are the
    // engine's own RulesEngine.countAgendaVotes.
    //
    // SCOPE DEPENDENCY (recorded, not worked around): the row asks for this to
    // appear "when the AGENDA_VOTE phase that B5-1970 introduces begins", but
    // GamePhase is an enum in b5ccg/src/b5ccg/model/enums/ and this claim's
    // scope is ui/ alone, so the constant cannot be added here. B5-1970 built
    // its engine surface phase-independently and documented exactly this seam:
    // it gates on its own open-session state. This dialog gates on the same
    // authority (RulesEngine.isAgendaVoteOpen), so when the later model/-scoped
    // row adds AGENDA_VOTE and the round driver calls openAgendaVote, the
    // button lights with no change to this file.

    /** The Council's five voting races plus the League tie-break seat
     *  (rulebook :791: "Earth, Minbari, Centauri, Narn and Vorlon. In addition,
     *  the League of Non-Aligned worlds ... may cast one vote to break any
     *  tie."). Listed for DISPLAY; which seats are actually filled is read from
     *  the live game, never assumed. */
    static final Faction[] COUNCIL_RACES = new Faction[] {
        Faction.HUMAN, Faction.MINBARI, Faction.CENTAURI, Faction.NARN,
        Faction.VORLON, Faction.NON_ALIGNED
    };

    /** Display name per Council seat, including the League's special name. */
    static String councilSeatLabel(Faction race) {
        if (race == Faction.NON_ALIGNED) return "League of Non-Aligned Worlds";
        return raceLabel(race);
    }

    /** The player seated at `race` in the live game, or null when that race is
     *  not in play this game. Identity is by faction, not seat index. */
    static Player councilSeatHolder(Faction race, GameState state) {
        if (state == null || race == null) return null;
        List<Player> players = state.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            if (p != null && p.getFaction() == race) return p;
        }
        return null;
    }

    /**
     * B5-2004: enablement for the Council ballot. One authority answers it --
     * the engine's open-session flag plus its own per-player eligibility and
     * already-voted check. The toolbar never re-derives any of it, for the
     * same reason refresh() stopped duplicating the heal/repair predicates.
     */
    private void refreshCouncilVoteControl(GameState state) {
        boolean voteOpen  = rules.isAgendaVoteOpen();
        boolean humanSeat = state != null && state.getHumanPlayer() != null;
        boolean canVote   = humanSeat
            && rules.isEligibleAgendaVoter(state.getHumanPlayer())
            && rules.getAgendaVote(state.getHumanPlayer()) == null;
        councilVoteButton.setEnabled(voteOpen && canVote);
        councilVoteButton.setToolTipText(voteOpen
            ? "Cast your ambassador's Council vote on the measure now on the floor."
            : "No Council vote is open.");
    }

    /**
     * B5-2004: the toolbar affordance. Opens the ballot seeded from the live
     * game, and on submit hands the chosen ballot to the engine. Nothing is
     * cast until the human presses Submit, so cancelling costs nothing -- the
     * engine charges vote cost only on a real castAgendaVote.
     */
    private void showCouncilVote() {
        showCouncilVoteDialog(this, controller.getState(), rules,
            controller.getState().getHumanPlayer());
        refresh(controller.getState());
    }
/**
     * B5-2007: the toolbar affordance. Opens the Deck Builder dialog for
     * deck construction with live validation feedback (45-card floor,
     * one-Starting-Ambassador rule, max-3-copies limits, faction playability).
     */
    private void showDeckBuilder() {
        showDeckBuilderDialog(this);
        refresh(controller.getState());
    }

    /**
     * B5-2007: the dialog entry point. Returns the constructed deck (or null
     * when cancelled). Degrades to null headlessly rather than throwing, so
     * a headless caller never hits a HeadlessException.
     */
    static List<Card> showDeckBuilderDialog(Component parent) {
        if (GraphicsEnvironment.isHeadless()) return null;
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        DeckBuilderDialog dialog = new DeckBuilderDialog(owner);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return dialog.getConstructedDeck();
    }

    /**
     * B5-2247: the Deck Builder dialog, the class the B5-2007 entry point at
     * :2919 was calling and which was never written. It is a nested private
     * static class for the same reason GameSetupDialog (:2605) and
     * CouncilVoteDialog (:2954) are: the toolbar affordance is the only
     * caller, and all three keep their construction next to their entry
     * point rather than in a separate file.
     *
     * <p>The dialog exists to close a dead seam rather than to add a screen.
     * {@link DeckLoader#validatePlayDeck(List, Faction)} had ZERO callers
     * tree-wide (B5-2183 finding L1), so the rulebook's own deck contract --
     * the 45-card floor (rulebook :128/:193), the one Starting Ambassador
     * (:195), the max-3-copies limit (:194) with its FIXED-rarity exemption,
     * and faction playability -- was written, unit-shaped, and never
     * consulted by anything a player could reach. This dialog is the
     * production caller.
     *
     * <p>Construct is enabled only while the deck is legal, so the button is
     * a gate that can actually go red: an illegal deck cannot be handed to
     * the engine from here, and the reason it cannot is written in the
     * problem list rather than swallowed.
     */
    private static final class DeckBuilderDialog extends JDialog {
        private final DefaultListModel poolModel = new DefaultListModel();
        private final DefaultListModel deckModel = new DefaultListModel();
        private final JList poolList = new JList(poolModel);
        private final JList deckList = new JList(deckModel);
        private final JComboBox factionBox = new JComboBox();
        private final JTextArea problemArea = new JTextArea();
        private final JLabel countLabel = new JLabel(" ");
        private final JButton constructButton = new JButton("Use This Deck");

        // B5-1980: pool filter controls. The rules live in DeckBuilderModel; these
        // are only the pixels that set that model's state and re-read it, so nothing
        // here decides what a filter means.
        private final JTextField searchField = new JTextField(14);
        private final JComboBox typeBox = new JComboBox();
        private final JComboBox rarityBox = new JComboBox();
        private final JComboBox costBox = new JComboBox();
        private final JLabel filterNote = new JLabel(" ");
        private final JButton clearFiltersButton = new JButton("Clear Filters");

        /** Sentinel for "no constraint on this axis" in a JComboBox. JComboBox will
         *  happily hold null but renders it as the text "null", so the absence of a
         *  filter is carried by a real object instead. No CardType or Rarity name
         *  equals it, so it cannot collide with a genuine selection. */
        private static final String ALL_FILTER = "All";

        /**
         * B5-2287: the rules live in {@link DeckBuilderModel}, not here. This
         * dialog is the view; the model is what HeadlessConformanceTest covers,
         * because a JDialog cannot be constructed in a headless JVM and the whole
         * of this class used to be unreachable without a human clicking it.
         *
         * <p>The list models remain because they are the selection mechanism, but
         * they are no longer the deck: {@link #syncDeckFromModel} is the only way
         * deck contents change, and the model is the authority.
         */
        private DeckBuilderModel model;
        private List<Card> constructedDeck = null;

        DeckBuilderDialog(Window owner) {
            super(owner, "Babylon 5 CCG -- Deck Builder", ModalityType.APPLICATION_MODAL);
            setLayout(new BorderLayout(8, 8));

            // The player faction drives both the pool and the faction
            // playability check, so it is chosen first and everything else
            // refreshes from it. ANY and NEUTRAL are card-side values, not
            // races a human plays, so DeckBuilderModel.playableFactions()
            // does not offer them.
            List<Faction> offered = DeckBuilderModel.playableFactions();
            for (int i = 0; i < offered.size(); i++) {
                factionBox.addItem(offered.get(i));
            }
            factionBox.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (model != null) {
                        model.setFaction(chosenFaction());
                        refreshPool();
                        refreshValidation();
                    }
                }
            });

            loadModel();

            JLabel head = new JLabel("Build a play deck -- " + DeckLoader.MIN_PLAY_DECK_SIZE
                + "-card minimum (:128/:193), one Starting Ambassador (:195), max "
                + DeckLoader.MAX_COPIES_PER_CARD + " copies (:194), faction playability");
            head.setForeground(new Color(230, 210, 130));
            head.setFont(new Font("SansSerif", Font.BOLD, 11));

            JPanel north = new JPanel();
            north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
            north.setBackground(new Color(10, 20, 10));
            north.add(head);
            north.add(new JLabel("Your faction:"));
            north.add(factionBox);
            // B5-1980: the filter row. It is a sibling of the faction box, not part
            // of it, because faction decides what is PLAYABLE while these decide what
            // is VISIBLE -- widening a filter must never change legality.
            north.add(buildFilterRow());
            north.add(filterNote);
            add(north, BorderLayout.NORTH);

            poolList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            deckList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            poolList.setBackground(new Color(18, 28, 18));
            deckList.setBackground(new Color(18, 28, 18));
            poolList.setVisibleRowCount(12);
            deckList.setVisibleRowCount(12);

            JPanel centre = new JPanel(new GridLayout(1, 2, 8, 8));
            centre.setBackground(new Color(10, 20, 10));
            centre.add(titled(poolList, "Card pool (playable by your faction)"));
            centre.add(titled(deckList, "Your deck (copies may repeat)"));
            add(centre, BorderLayout.CENTER);

            problemArea.setEditable(false);
            problemArea.setLineWrap(true);
            problemArea.setWrapStyleWord(true);
            problemArea.setBackground(new Color(28, 18, 18));
            problemArea.setForeground(new Color(230, 170, 150));
            problemArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
            JPanel south = new JPanel(new BorderLayout(6, 6));
            south.setBackground(new Color(10, 20, 10));
            south.add(new JScrollPane(problemArea), BorderLayout.CENTER);

            countLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
            countLabel.setForeground(new Color(200, 220, 200));
            JPanel southSouth = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
            southSouth.setBackground(new Color(10, 20, 10));
            southSouth.add(countLabel);
            JButton addButton = new JButton("Add Selected");
            addButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) { addSelected(); }
            });
            southSouth.add(addButton);
            JButton removeButton = new JButton("Remove Selected");
            removeButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) { removeSelected(); }
            });
            southSouth.add(removeButton);
            JButton clearButton = new JButton("Clear");
            clearButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (model != null) model.clear();
                    syncDeckFromModel();
                    refreshValidation();
                }
            });
            southSouth.add(clearButton);
            constructButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    // Guarded by refreshValidation: this is only reachable
                    // while the deck is legal. Re-checked rather than trusted,
                    // because a stale enabled flag would hand an illegal deck
                    // to the engine.
                    List<String> problems = currentProblems();
                    if (!problems.isEmpty()) return;
                    constructedDeck = model.constructedDeck();
                    dispose();
                }
            });
            southSouth.add(constructButton);
            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) { dispose(); }
            });
            southSouth.add(cancelButton);
            south.add(southSouth, BorderLayout.SOUTH);
            add(south, BorderLayout.SOUTH);

            setSize(760, 620);
            setLocationRelativeTo(null);
            refreshValidation();
        }

        /**
         * Returns the constructed deck, or null when the human cancelled.
         * The null-on-cancel shape is the same one showCouncilVoteDialog
         * (:2934) and showSetupDialog use, so every caller already handles it.
         */
        List<Card> getConstructedDeck() {
            return constructedDeck;
        }

        private static JPanel titled(JComponent inner, String title) {
            JPanel p = new JPanel(new BorderLayout());
            p.setBackground(new Color(10, 20, 10));
            p.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(80, 130, 80)),
                title, 0, 0, new Font("SansSerif", Font.BOLD, 10),
                new Color(180, 200, 180)));
            p.add(new JScrollPane(inner), BorderLayout.CENTER);
            return p;
        }

        private void loadModel() {
            try {
                model = DeckBuilderModel.fromShippedSets();
                model.setFaction(chosenFaction());
            } catch (java.io.IOException e) {
                problemArea.setText("Card pool could not be loaded: " + e.getMessage());
                return;
            }
            refreshPool();
        }

        private Faction chosenFaction() {
            Object sel = factionBox.getSelectedItem();
            return (sel instanceof Faction) ? (Faction) sel : Faction.HUMAN;
        }

        // ---- B5-1980 pool filters -------------------------------------------------
        // Everything below is view wiring. The meaning of each filter lives in
        // DeckBuilderModel; this code only moves values between widgets and that model.

        /**
         * Builds the one filter row. Populating the cost list from the authored pool
         * rather than hardcoding a range means the offered bounds cannot drift from
         * the data -- there is no "0..9" here to fall out of step with a card that
         * costs 12.
         */
        private JPanel buildFilterRow() {
            typeBox.addItem(ALL_FILTER);
            CardType[] types = CardType.values();
            for (int i = 0; i < types.length; i++) typeBox.addItem(types[i]);

            rarityBox.addItem(ALL_FILTER);
            Rarity[] rarities = Rarity.values();
            for (int i = 0; i < rarities.length; i++) rarityBox.addItem(rarities[i]);

            costBox.addItem("Any cost");
            int maxCost = 0;
            if (model != null) {
                List<Card> authored = model.sourceCards();
                for (int i = 0; i < authored.size(); i++) {
                    if (authored.get(i).getCost() > maxCost) maxCost = authored.get(i).getCost();
                }
            }
            for (int c = 0; c <= maxCost; c++) costBox.addItem(Integer.toString(c));

            ActionListener relayout = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) { applyFiltersToModel(); }
            };
            typeBox.addActionListener(relayout);
            rarityBox.addActionListener(relayout);
            costBox.addActionListener(relayout);

            // Search updates on every keystroke, not on Enter: a filter that needs a
            // submit action to take effect is a filter people forget they set.
            searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFiltersToModel(); }
                @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFiltersToModel(); }
                @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFiltersToModel(); }
            });

            clearFiltersButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    // Clear the widgets as well as the model, or the combos would keep
                    // showing a constraint that is no longer applied -- a control lying
                    // about state is worse than no control.
                    searchField.setText("");
                    typeBox.setSelectedIndex(0);
                    rarityBox.setSelectedIndex(0);
                    costBox.setSelectedIndex(0);
                    applyFiltersToModel();
                }
            });

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            row.setBackground(new Color(10, 20, 10));
            searchField.setToolTipText("Search card name or rules text");
            searchField.setBackground(new Color(18, 28, 18));
            searchField.setForeground(new Color(220, 230, 220));
            row.add(new JLabel("Search:"));
            row.add(searchField);
            row.add(new JLabel("Type:"));
            row.add(typeBox);
            row.add(new JLabel("Rarity:"));
            row.add(rarityBox);
            row.add(new JLabel("Max cost:"));
            row.add(costBox);
            row.add(clearFiltersButton);
            return row;
        }

        private CardType chosenType() {
            Object sel = typeBox.getSelectedItem();
            return (sel instanceof CardType) ? (CardType) sel : null;
        }

        private Rarity chosenRarity() {
            Object sel = rarityBox.getSelectedItem();
            return (sel instanceof Rarity) ? (Rarity) sel : null;
        }

        /**
         * Pushes the current widget state into the model and redraws the pool.
         *
         * <p>Note what is NOT touched: the deck and the faction. Filtering the pool
         * must never remove cards the player already picked, which is the same
         * non-destructive property setFaction documents.
         */
        private void applyFiltersToModel() {
            if (model == null) return;
            model.setTypeFilter(chosenType());
            model.setRarityFilter(chosenRarity());
            Object costSel = costBox.getSelectedItem();
            if (costSel instanceof String && "Any cost".equals(costSel)) {
                model.clearCostLimit();
            } else {
                try {
                    model.setCostLimit(Integer.parseInt(String.valueOf(costSel)));
                } catch (NumberFormatException nfe) {
                    model.clearCostLimit();
                }
            }
            model.setQuery(searchField.getText());
            refreshPool();
        }

        /**
         * Reports how many cards the filters are showing, and says so plainly when
         * the answer is zero -- a silently empty pool reads as a bug.
         */
        private void refreshFilterNote() {
            if (model == null) {
                filterNote.setText(" ");
                clearFiltersButton.setEnabled(false);
                return;
            }
            List<Card> shown = model.pool();
            if (model.hasPoolFilters()) {
                filterNote.setText("Filters: " + shown.size() + " of "
                    + model.sourceCards().size() + " authored cards shown");
            } else {
                filterNote.setText(shown.size() + " cards playable by your faction");
            }
            clearFiltersButton.setEnabled(model.hasPoolFilters());
        }

        /**
         * Repopulates the pool from the model's filtered view for the current
         * faction. The model keeps the unfiltered source, so switching faction
         * widens the pool again rather than having nothing to widen from.
         */
        private void refreshPool() {
            poolModel.clear();
            if (model == null) {
                refreshFilterNote();
                return;
            }
            List<Card> cards = model.pool();
            for (int i = 0; i < cards.size(); i++) {
                poolModel.addElement(cards.get(i));
            }
            refreshFilterNote();
        }

        /** Mirrors the model's deck into the selection list. One-way: the model
         *  is authoritative and the list is a rendering of it. */
        private void syncDeckFromModel() {
            deckModel.clear();
            if (model == null) return;
            List<Card> cards = model.deck();
            for (int i = 0; i < cards.size(); i++) {
                deckModel.addElement(cards.get(i));
            }
        }

        private void addSelected() {
            if (model == null) return;
            Object[] sel = poolList.getSelectedValues();
            for (int i = 0; i < sel.length; i++) {
                if (sel[i] instanceof Card) model.addCard((Card) sel[i]);
            }
            syncDeckFromModel();
            refreshValidation();
        }

        private void removeSelected() {
            if (model == null) return;
            // getSelectedIndices() already ascends; the model removes from the
            // high end, which keeps the not-yet-removed indices valid.
            model.removeIndices(deckList.getSelectedIndices());
            syncDeckFromModel();
            refreshValidation();
        }

        private List<Card> deckCards() {
            return (model == null) ? new ArrayList<Card>() : model.deck();
        }

        private List<String> currentProblems() {
            if (model == null) return java.util.Collections.singletonList("Card pool not loaded.");
            return model.problems();
        }

        /**
         * The live gate. Re-runs the rulebook contract on every mutation and
         * enables Construct only on an empty problem list. The count and the
         * verdict come from the model, so this method renders rather than
         * decides.
         */
        private void refreshValidation() {
            if (model == null) {
                constructButton.setEnabled(false);
                return;
            }
            List<String> problems = currentProblems();
            int n = model.deckSize();
            countLabel.setText(n + " / " + DeckLoader.MIN_PLAY_DECK_SIZE + " cards");
            StringBuilder sb = new StringBuilder();
            if (problems.isEmpty()) {
                sb.append("Legal play deck.");
            } else {
                for (int i = 0; i < problems.size(); i++) {
                    sb.append(problems.get(i)).append('\n');
                }
            }
            problemArea.setText(sb.toString());
            constructButton.setEnabled(problems.isEmpty());
        }
    }

    /**
     * B5-2004: the dialog entry point. Returns the ballot the human submitted,
     * or null when cancelled or when there is nothing to vote on. Degrades to
     * null headlessly rather than throwing, the same shape as
     * showSetupDialog, so a headless caller never hits a HeadlessException.
     */
    static RulesEngine.AgendaVote showCouncilVoteDialog(Component parent,
            GameState state, RulesEngine rules, Player human) {
        if (GraphicsEnvironment.isHeadless()) return null;
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        CouncilVoteDialog dialog = new CouncilVoteDialog(owner, state, rules, human);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return dialog.getSubmitted();
    }

    /**
     * B5-2004: the ballot. One radio group per castable seat (the human's own
     * ambassador, rulebook :793 "Each player may cast his own ambassador's
     * vote"), the Council roster read from the live game, and the three rules a
     * player needs in front of them while voting. The cost spinner defaults to
     * 0 because the rulebook states no generic price for calling a vote
     * (:797 "Some cards may also list other requirements") -- the engine takes
     * the cost as a parameter for exactly that reason, and inventing a default
     * of 1 here would charge a rule the rulebook does not contain.
     */
    private static final class CouncilVoteDialog extends JDialog {
        private final RulesEngine rules;
        private final GameState  state;
        private final Player     human;
        private final JLabel     tallyLabel;
        private final JLabel     costLabel;
        private final JSpinner   costSpinner;
        private final ButtonGroup ballot = new ButtonGroup();
        private final JRadioButton yesButton = new JRadioButton("Yes");
        private final JRadioButton noButton  = new JRadioButton("No");
        private final JRadioButton abstainButton = new JRadioButton("Abstain");

        private RulesEngine.AgendaVote submitted = null;

        CouncilVoteDialog(Window owner, GameState state, RulesEngine rules, Player human) {
            super(owner, "Babylon 5 CCG -- Council Vote", ModalityType.APPLICATION_MODAL);
            this.rules = rules;
            this.state  = state;
            this.human  = human;
            setLayout(new BorderLayout(8, 8));

            AgendaCard underVote = rules != null ? rules.getAgendaUnderVote() : null;
            Player head = (rules != null && state != null)
                ? rules.councilHead(underVote, state) : null;

            JLabel title = new JLabel("Measure on the floor: "
                + (underVote != null ? underVote.getTitle() : "(none)"));
            title.setForeground(new Color(230, 210, 130));
            title.setFont(new Font("SansSerif", Font.BOLD, 12));
            JLabel headLabel = new JLabel("Head of the council (rulebook :795): "
                + (head != null ? head.getName() : "(none -- cannot call the vote)"));
            headLabel.setForeground(new Color(200, 220, 200));
            headLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

            JPanel north = new JPanel();
            north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
            north.setBackground(new Color(10, 20, 10));
            north.add(title);
            north.add(headLabel);
            add(north, BorderLayout.NORTH);

            // The roster: every Council seat, filled or not. An unfilled seat
            // is shown as abstaining BY DEFAULT (rulebook :793) and that is
            // exactly what B5-1993 applies -- this panel labels the seat, it
            // does not cast a ballot on a race nobody is playing.
            JPanel roster = new JPanel(new GridLayout(COUNCIL_RACES.length + 1, 2, 6, 2));
            roster.setBackground(new Color(10, 20, 10));
            roster.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(80, 130, 80)),
                "Council of Races  (rulebook :791)",
                0, 0, new Font("SansSerif", Font.BOLD, 10), new Color(180, 200, 180)));

            JLabel seatCol = new JLabel("Race");
            JLabel whoCol  = new JLabel("Ambassador in play");
            seatCol.setForeground(new Color(200, 220, 200));
            whoCol.setForeground(new Color(200, 220, 200));
            roster.add(seatCol);
            roster.add(whoCol);

            for (int i = 0; i < COUNCIL_RACES.length; i++) {
                Faction race = COUNCIL_RACES[i];
                JLabel seat = new JLabel(councilSeatLabel(race));
                seat.setForeground(new Color(220, 200, 140));
                seat.setFont(new Font("Monospaced", Font.PLAIN, 11));
                Player holder = councilSeatHolder(race, state);
                JLabel who = new JLabel(holder != null
                    ? holder.getName()
                    : "(race not in play - abstains, rulebook :793)");
                who.setForeground(holder != null
                    ? new Color(180, 200, 180)
                    : new Color(150, 150, 150));
                who.setFont(new Font("Monospaced", Font.PLAIN, 11));
                roster.add(seat);
                roster.add(who);
            }
            JPanel centre = new JPanel(new BorderLayout(6, 6));
            centre.setBackground(new Color(10, 20, 10));
            centre.add(roster, BorderLayout.NORTH);
            centre.add(buildBallotPanel(), BorderLayout.CENTER);
            add(centre, BorderLayout.CENTER);

            tallyLabel = new JLabel(" ");
            tallyLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
            costLabel = new JLabel(" ");
            costLabel.setForeground(new Color(200, 220, 200));
            costLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
            costSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 99, 1));
            costSpinner.addChangeListener(new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                    refreshCostLabel();
                }
            });

            JPanel south = new JPanel(new BorderLayout(6, 4));
            south.setBackground(new Color(10, 20, 10));
            south.add(tallyLabel, BorderLayout.CENTER);
            JPanel southSouth = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
            southSouth.setBackground(new Color(10, 20, 10));
            southSouth.add(costLabel);
            southSouth.add(new JLabel("cost"));
            southSouth.add(costSpinner);
            south.add(southSouth, BorderLayout.SOUTH);

            JButton submit = new JButton("Submit Ballot");
            submit.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    submitBallot();
                }
            });
            JButton cancel = new JButton("Cancel");
            cancel.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    submitted = null;
                    dispose();
                }
            });
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
            buttons.setBackground(new Color(10, 20, 10));
            buttons.add(cancel);
            buttons.add(submit);
            south.add(buttons, BorderLayout.EAST);
            add(south, BorderLayout.SOUTH);

            refreshCostLabel();
            refreshTally();
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            pack();
        }

        /** The ballot widget plus the three rules a voter needs on screen. */
        private JPanel buildBallotPanel() {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(new Color(10, 20, 10));

            JLabel prompt = new JLabel("Your ambassador's ballot (rulebook :795):");
            prompt.setForeground(new Color(220, 200, 140));
            prompt.setFont(new Font("SansSerif", Font.BOLD, 10));
            prompt.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(prompt);

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
            row.setBackground(new Color(10, 20, 10));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            styleBallotButton(yesButton);
            styleBallotButton(noButton);
            styleBallotButton(abstainButton);
            ballot.add(yesButton);
            ballot.add(noButton);
            ballot.add(abstainButton);
            row.add(yesButton);
            row.add(noButton);
            row.add(abstainButton);
            // Abstain is the pre-selection: an unplayed race abstains by
            // default (:793), so the safe default for a player who does not
            // touch the ballot is the same posture.
            abstainButton.setSelected(true);
            panel.add(row);

            JLabel rule = new JLabel("<html>"
                + "Passes only with at least one more Yes than No (rulebook :797).<br>"
                + "A tie is broken by the League of Non-Aligned Worlds (rulebook :791).<br>"
                + "Races not in play are part of the vote but abstain by default (rulebook :793)."
                + "</html>");
            rule.setForeground(new Color(180, 200, 180));
            rule.setFont(new Font("SansSerif", Font.PLAIN, 10));
            rule.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(rule);
            return panel;
        }

        private void styleBallotButton(JRadioButton b) {
            b.setBackground(new Color(10, 20, 10));
            b.setForeground(new Color(200, 230, 200));
            b.setFocusPainted(false);
            b.setFont(new Font("SansSerif", Font.BOLD, 11));
        }

        /** Engine-backed, never a re-tally: the counts are the engine's own. */
        private void refreshTally() {
            int yes      = rules.countAgendaVotes(RulesEngine.AgendaVote.YES);
            int no       = rules.countAgendaVotes(RulesEngine.AgendaVote.NO);
            int abstain  = rules.countAgendaVotes(RulesEngine.AgendaVote.ABSTAIN);
            tallyLabel.setForeground(new Color(180, 200, 180));
            tallyLabel.setText("Ballots cast: Yes " + yes + "  |  No " + no
                + "  |  Abstain " + abstain
                + "   (no verdict here -- resolution is the engine's, rulebook :797)");
        }

        /** The cost line is an affordability readout from the engine's own
         *  applied-pool rule, re-evaluated on every spinner change. */
        private void refreshCostLabel() {
            int cost = ((Number) costSpinner.getValue()).intValue();
            if (human == null) {
                costLabel.setText("no human seat");
                return;
            }
            boolean affordable = rules.canCastAgendaVote(human, selectedBallot(), cost, state);
            costLabel.setText("applied pool " + human.getAppliedPool()
                + (affordable ? " -- affordable" : " -- cannot cover this vote"));
        }

        private RulesEngine.AgendaVote selectedBallot() {
            if (yesButton.isSelected())      return RulesEngine.AgendaVote.YES;
            if (noButton.isSelected())       return RulesEngine.AgendaVote.NO;
            return RulesEngine.AgendaVote.ABSTAIN;
        }

        /**
         * The single commit path. Legality is asked of the engine
         * (canCastAgendaVote) and the cast is the engine's own call, so the
         * dialog cannot record a ballot the engine would have refused. A
         * refused ballot leaves the dialog open with the reason, rather than
         * closing and losing the voter's intent.
         */
        private void submitBallot() {
            if (human == null || state == null) {
                submitted = null;
                dispose();
                return;
            }
            int cost = ((Number) costSpinner.getValue()).intValue();
            RulesEngine.AgendaVote chosen = selectedBallot();
            if (!rules.canCastAgendaVote(human, chosen, cost, state)) {
                tallyLabel.setForeground(new Color(230, 130, 130));
                tallyLabel.setText("Ballot refused by the rules engine -- not cast. "
                    + "Choose a ballot you can afford.");
                return;
            }
            if (rules.castAgendaVote(human, chosen, cost, state)) {
                submitted = chosen;
                dispose();
            } else {
                tallyLabel.setForeground(new Color(230, 130, 130));
                tallyLabel.setText("Ballot refused by the rules engine -- not cast.");
            }
        }

        RulesEngine.AgendaVote getSubmitted() { return submitted; }
    }

    /**
     * B5-2008: the toolbar affordance. Opens the dialog seeded with the live
     * seats and, on accept, writes the validated receipt into the game log.
     * It deliberately does NOT mutate the game: Player.faction is final and a
     * live game already holds constructed Players, so reporting the accepted
     * setup honestly is the only truthful outcome from inside this window.
     */
    private void showGameSetup() {
        SetupConfig accepted = showSetupDialog(this, currentSetupConfig());
        if (accepted == null) return;
        String problem = validateSetup(raceArrayOf(accepted), influenceArrayOf(accepted));
        controller.getState().log("Game setup (B5-2008): " + accepted.describe()
            + (problem == null ? " -- valid." : " -- REJECTED: " + problem));
    }

    private static Faction[] raceArrayOf(SetupConfig config) {
        Faction[] races = new Faction[config.size()];
        for (int i = 0; i < races.length; i++) races[i] = config.getRace(i);
        return races;
    }

    private static int[] influenceArrayOf(SetupConfig config) {
        int[] influence = new int[config.size()];
        for (int i = 0; i < influence.length; i++) {
            influence[i] = config.getStartingInfluence(i);
        }
        return influence;
    }

    // B5-4174 (M0): the literal colours that used to live here are now Theme
    // tokens, so this method and ThemedButton cannot drift apart. Still a plain
    // JButton on purpose -- swapping the class itself to ThemedButton changes
    // the paint path and is not a visual-diff-only edit, which is what this row
    // scopes. Behaviour is unchanged: same listener, same focus policy.
    private JButton makeButton(String label, ActionListener al) {
        JButton btn = new JButton(label);
        btn.setBackground(Theme.BG_RAISED);
        btn.setForeground(Theme.TEXT);
        btn.setFocusPainted(false);
        btn.setFont(Theme.FONT_SMALL_BOLD);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.LINE_BRIGHT),
            BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        btn.addActionListener(al);
        return btn;
    }

    // B5-0348: tiny checkbox for filter panel
    private JCheckBox makeTinyCheckBox(String label) {
        JCheckBox cb = new JCheckBox(label);
        cb.setFont(new Font("SansSerif", Font.PLAIN, 9));
        cb.setFocusPainted(false);
        cb.setForeground(new Color(200, 220, 200));
        cb.setBackground(new Color(10, 20, 10));
        return cb;
    }

    // ── B5-4167: RoundEndCallback implementation ───────────────────────────────

    /**
     * Called at the close of an action round. Freezes a per-seat snapshot on
     * the engine thread — by the time the EDT renders, the next Ready round
     * may already have refilled applied pools — logs the same snapshot to the
     * game log for headless playtests, then shows a modeless dismissible
     * popup on the EDT. The popup never blocks the engine thread: a modal
     * dialog here is the B5-2433 hang this ledger already recorded.
     */
    @Override
    public void onRoundEnd(final GameState state, final int roundNumber,
                           final java.util.List<Player> players) {
        List<String> hintLines    = new ArrayList<String>();
        List<String[]> seatRows   = new ArrayList<String[]>();
        StringBuilder logSummary  = new StringBuilder();
        logSummary.append("=== Round ").append(roundNumber).append(" Ended ===");
        int maxInfluence = Integer.MIN_VALUE;
        int leaderCount  = 0;
        for (Player p : players) {
            if (!state.isPlayerActive(p)) continue;
            String seat = p.getName() + " (" + p.getFaction() + ")";
            String[] row = new String[] {
                seat,
                String.valueOf(p.getInfluence()),
                String.valueOf(p.getAppliedPool()),
                String.valueOf(p.getPower()),
                String.valueOf(p.getInnerCircle().size()),
                String.valueOf(p.getHand().size())
            };
            seatRows.add(row);
            logSummary.append("\n  ").append(seat)
                      .append(": Inf=").append(row[1])
                      .append(", Applied=").append(row[2])
                      .append(", Power=").append(row[3])
                      .append(", IC=").append(row[4])
                      .append(", Hand=").append(row[5]);
            if (p.getInfluence() > maxInfluence) {
                maxInfluence = p.getInfluence();
                leaderCount  = 1;
            } else if (p.getInfluence() == maxInfluence) {
                leaderCount++;
            }
            if (p.getInfluence() >= 20) {
                hintLines.add("★ " + p.getName()
                              + " has Influence ≥ 20 (Station Victory possible)");
            }
        }
        if (leaderCount > 1) {
            hintLines.add("⚠ Strict-lead tie: no winner (D12 requires "
                          + "strictly-greatest Influence)");
        }
        if (maxInfluence >= 18) {
            hintLines.add("★ Shadow War threshold (Influence 20) approaching");
        }
        final String         logText  = logSummary.toString();
        final List<String[]> snapshot = seatRows;
        final List<String>   hints    = hintLines;

        // Model write on the engine thread, like every other state mutation;
        // the UI log rebuilds itself from state.getLog() on the next refresh,
        // so the summary prints exactly once and headless playtests can
        // assert on it via state.getLog().
        state.log(logText);

        // Modeless popup on the EDT, rendering the frozen snapshot only.
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                showRoundSummaryPopup(roundNumber, snapshot, hints);
            }
        });
    }

    /**
     * Shows a modeless, dismissible popup with the round-end snapshot.
     * B5-4158 shipped in-window aftermath controls, not a dialog, so this is
     * the closest house pattern: a non-modal JDialog that never blocks the
     * engine thread (the B5-2433 lesson) and renders only the frozen
     * snapshot — no live model reads on the EDT.
     */
    private void showRoundSummaryPopup(int roundNumber,
                                       List<String[]> seatRows,
                                       List<String> hintLines) {
        // Avoid spawning multiple popups - dispose any existing round summary popup
        if (roundSummaryPopup != null && roundSummaryPopup.isVisible()) {
            roundSummaryPopup.dispose();
        }

        roundSummaryPopup = new JDialog(this, "Round " + roundNumber + " Complete", false);
        roundSummaryPopup.setLayout(new BorderLayout(8, 8));
        roundSummaryPopup.setBackground(new Color(10, 20, 10));
        roundSummaryPopup.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // Title
        JLabel title = new JLabel("Round " + roundNumber + " — All Seated Players Passed");
        title.setForeground(new Color(220, 200, 120));
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));
        roundSummaryPopup.add(title, BorderLayout.NORTH);

        // Summary table, rendered from the round-end snapshot
        JPanel summaryPanel = new JPanel();
        summaryPanel.setLayout(new BoxLayout(summaryPanel, BoxLayout.Y_AXIS));
        summaryPanel.setBackground(new Color(15, 25, 15));
        summaryPanel.setBorder(BorderFactory.createEmptyBorder(4, 12, 8, 12));

        for (String[] row : seatRows) {
            JPanel seat = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            seat.setBackground(new Color(15, 25, 15));
            seat.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Player name and faction
            JLabel nameLabel = new JLabel(row[0]);
            nameLabel.setForeground(new Color(200, 230, 200));
            nameLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
            seat.add(nameLabel);

            // Stats
            JLabel statsLabel = new JLabel(
                    "Inf: " + row[1] +
                    "  |  Applied: " + row[2] +
                    "  |  Power: " + row[3] +
                    "  |  IC: " + row[4] +
                    "  |  Hand: " + row[5]);
            statsLabel.setForeground(new Color(180, 200, 180));
            statsLabel.setFont(new Font("Monospaced", Font.PLAIN, 10));
            seat.add(statsLabel);

            summaryPanel.add(seat);
        }

        // Victory-condition hints (rulebook 11.6 and 13 item 6), from the
        // same frozen snapshot
        if (!hintLines.isEmpty()) {
            JPanel hintsPanel = new JPanel();
            hintsPanel.setLayout(new BoxLayout(hintsPanel, BoxLayout.Y_AXIS));
            hintsPanel.setBackground(new Color(15, 25, 15));
            hintsPanel.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(new Color(80, 120, 80)),
                    "Victory Conditions", 0, 0,
                    new Font("SansSerif", Font.BOLD, 10), new Color(180, 200, 180)));
            for (String hint : hintLines) {
                JLabel hintLabel = new JLabel(hint);
                hintLabel.setForeground(new Color(230, 180, 130));
                hintLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
                hintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                hintsPanel.add(hintLabel);
            }
            summaryPanel.add(Box.createVerticalStrut(8));
            summaryPanel.add(hintsPanel);
        }

        JScrollPane scroll = new JScrollPane(summaryPanel);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(80, 120, 80)));
        scroll.getViewport().setBackground(new Color(15, 25, 15));
        scroll.setPreferredSize(new Dimension(380, 300));
        roundSummaryPopup.add(scroll, BorderLayout.CENTER);

        // Dismiss button
        JButton dismissBtn = new JButton("Dismiss");
        dismissBtn.setBackground(new Color(40, 70, 40));
        dismissBtn.setForeground(new Color(200, 230, 200));
        dismissBtn.setFocusPainted(false);
        dismissBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        dismissBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                roundSummaryPopup.dispose();
            }
        });
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttonPanel.setBackground(new Color(10, 20, 10));
        buttonPanel.add(dismissBtn);
        roundSummaryPopup.add(buttonPanel, BorderLayout.SOUTH);

        roundSummaryPopup.pack();
        roundSummaryPopup.setLocationRelativeTo(this);
        roundSummaryPopup.setVisible(true);
    }

    // Field to track the round summary popup (B5-4167)
    private JDialog roundSummaryPopup = null;
}
