package b5ccg;

import b5ccg.ai.AIPlayer;
import b5ccg.engine.*;
import b5ccg.model.*;
import b5ccg.model.enums.*;
import b5ccg.ui.GameBoardPanel;
import b5ccg.ui.HandPanel;
import b5ccg.ui.MainWindow;
import b5ccg.ui.StartingGameFlowModel;
import b5ccg.ui.shell.ActionDock;
import b5ccg.ui.shell.GameView;
import b5ccg.ui.shell.MainFrame;
import b5ccg.ui.shell.PhaseStrip;
import b5ccg.ui.shell.PromptHost;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;
import java.util.*;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {}

                try {
                    startGame();
                } catch (Throwable t) {
                    // Include the exception class name — many crashes (NPE,
                    // HeadlessException, etc.) have a null getMessage(), so
                    // showing only the message makes the dialog say
                    // "Fatal error starting game: null" with no clue what
                    // actually failed.
                    String detail = t.getClass().getName();
                    if (t.getMessage() != null) detail += ": " + t.getMessage();
                    t.printStackTrace();
                    try {
                        JOptionPane.showMessageDialog(null,
                            "Fatal error starting game:\n" + detail,
                            "B5 CCG Error", JOptionPane.ERROR_MESSAGE);
                    } catch (Throwable t2) {
                        // If the dialog itself fails (e.g. headless), at least
                        // the stack trace above is on stderr.
                    }
                }
            }
        });
    }

    private static void startGame() throws Exception {
        // ── Load all cards ────────────────────────────────────────────────────
        List<Card> allCards;
        try {
            allCards = DeckLoader.loadBothSets();
            System.out.println("Loaded " + allCards.size() + " cards.");
        } catch (Exception e) {
            System.err.println("Card load failed, using minimal test set: " + e.getMessage());
            allCards = buildMinimalTestSet();
        }

        // ── Create players ────────────────────────────────────────────────────
        Faction humanFaction = chooseFaction();
        List<Faction> aiFactions = otherFactions(humanFaction);

        Player human  = new Player("You",       humanFaction,          true);
        // B5-4170: use the rulebook-named ambassador for each faction instead of
        // hardcoding names — the previous literals "G'Kar"/"Londo" were assigned
        // to aiFactions.get(1)/get(2) in positional order, which swaps them when
        // the human faction is Human (order is MINBARI, CENTAURI, NARN), putting
        // G'Kar (the Narn ambassador) on the Centauri seat and Londo on the Narn.
        Player ai1    = new Player(StartingGameFlowModel.ambassadorName(aiFactions.get(0)),  aiFactions.get(0),     false);
        Player ai2    = new Player(StartingGameFlowModel.ambassadorName(aiFactions.get(1)),  aiFactions.get(1),     false);
        Player ai3    = new Player(StartingGameFlowModel.ambassadorName(aiFactions.get(2)),  aiFactions.get(2),     false);

        List<Player> players = Arrays.asList(human, ai1, ai2, ai3);

        // ── Build and deal faction decks ──────────────────────────────────────
        for (Player p : players) {
            List<Card> factionCards = buildFactionDeck(allCards, p.getFaction());
            Deck deck = new Deck(factionCards);
            // B5-0319: pin the race ambassador to the draw-pile top so the
            // fixed starter deck's ambassador reaches the hand (GameController
            // .setupGame extracts it from the hand only). Deck shuffles in its
            // constructor, so pin AFTER construction.
            CharacterCard amb = StarterDeckBuilder.findAmbassador(factionCards, p.getFaction());
            if (amb != null) deck.addToTop(amb);
            p.setDeck(deck);
            p.drawCards(4); // initial hand
        }

        // ── AI players ────────────────────────────────────────────────────────
        List<AIPlayer> aiPlayers = Arrays.asList(
            new AIPlayer(ai1, AIDifficulty.MEDIUM),
            new AIPlayer(ai2, AIDifficulty.HARD),
            new AIPlayer(ai3, AIDifficulty.EASY)
        );

        // ── Game state & controller ───────────────────────────────────────────
        GameState state = new GameState(players);
        final MainWindow[] windowHolder = new MainWindow[1];
        // B5-4212: the shell path's fan-out target set. One holder, filled only
        // when the shell flag is on, so the controller callback below has one
        // thing to ask "is there a shell?" of.
        final ShellUI shell = new ShellUI();
        final GameController[] controllerHolder = new GameController[1];

        GameController controller = new GameController(state, aiPlayers,
            new GameStateCallback() {
                @Override public void accept(GameState gs) {
                    if (shell.frame != null) {
                        pushShellState(shell, controllerHolder[0], gs);
                    } else if (windowHolder[0] != null) {
                        windowHolder[0].onStateUpdate(gs);
                    }
                }
            });
        controllerHolder[0] = controller;

        final GameController fController = controller;
        if (useShellApp()) {
            buildShellFrame(controller, shell);
            pushShellState(shell, controller, state);
            shell.frame.setVisible(true);
        } else {
            MainWindow window = new MainWindow(controller);
            windowHolder[0] = window;
            window.setVisible(true);
        }

        // ── Run game loop on background thread ───────────────────────────────
        Thread gameThread = new Thread(new Runnable() {
            @Override public void run() { fController.runGame(); }
        }, "GameLoop");
        gameThread.setDaemon(true);
        gameThread.start();
    }

    // ── Shell frame migration (B5-4212) ─────────────────────────────────────

    /**
     * The pieces one shell state push fans out to. Board and hand are not
     * StateView implementations yet (that is M5 wiring, not this row), so they
     * ride along here with their existing update() inputs.
     */
    private static final class ShellUI {
        MainFrame frame;
        GameBoardPanel board;
        HandPanel hand;
    }

    /**
     * B5-4212 migration flag: off by default, so Main still builds the working
     * MainWindow and nothing about the shipped app changes. Start with
     * {@code -Db5ccg.ui.shell=true} to run the new MainFrame instead. MainWindow
     * stays live until the M5 sunset row retires it.
     */
    private static boolean useShellApp() {
        return Boolean.getBoolean("b5ccg.ui.shell");
    }

    /** Builds MainFrame and its panels; fills @a ui. Nothing here touches RulesEngine. */
    private static void buildShellFrame(final GameController controller, ShellUI ui) {
        ActionListener onNext = new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                // Same single pass action MainWindow's passButton submits; every
                // one of its five meanings (pass / skip attack / decline join /
                // decline conflict / end draws) rides this one action.
                controller.submitHumanAction(GameAction.pass());
            }
        };
        PhaseStrip top = new PhaseStrip(phaseNames(), onNext);
        ActionDock dock = new ActionDock();
        PromptHost prompts = new PromptHost();
        ui.board = new GameBoardPanel();
        ui.hand = new HandPanel();
        // M5 owns the sidebar; until then the shell carries an honest placeholder
        // rather than pretending the seven detachables have been consolidated.
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setOpaque(false);
        sidebar.add(new JLabel("Sidebar — pending M5 consolidation"), BorderLayout.NORTH);

        ui.frame = new MainFrame("Babylon 5 CCG — Shell (B5-4212)",
                top, ui.board, dock, prompts, ui.hand, sidebar);
        // B5-4213: SPACE drives the same one button at window level. Bound on
        // the frame AFTER assembly, because MainFrame only then has a root pane
        // to hang WHEN_IN_FOCUSED_WINDOW bindings on.
        top.installKeys(ui.frame.getRootPane());
    }

    /** The ONE fan-out: build one GameView from GameState and push it on the EDT. */
    private static void pushShellState(final ShellUI ui, final GameController controller,
            final GameState gs) {
        final GameView view = new ShellGameView(controller, gs);
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                ui.frame.refresh(view);
                ui.board.update(gs);
                Player human = gs.getHumanPlayer();
                if (human != null) ui.hand.update(human.getHand());
            }
        });
    }

    private static String[] phaseNames() {
        GamePhase[] values = GamePhase.values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) names[i] = values[i].name();
        return names;
    }

    /**
     * Interim state-only GameView (B5-4212). It deliberately imports no
     * RulesEngine: legality authority belongs to the EngineGameView adapter, so
     * every legality answer here is the honest "not migrated yet" rather than a
     * second, competing guess. Registering dock actions belongs to the ActionDock
     * row; inline prompts belong to the PromptHost row.
     */
    private static final class ShellGameView implements GameView {
        private final GameController controller;
        private final GameState state;

        ShellGameView(GameController controller, GameState state) {
            this.controller = controller;
            this.state = state;
        }

        public String phaseName() {
            GamePhase p = state.getPhase();
            return p == null ? "-" : p.name();
        }

        public String statusText() {
            Player active = state.getActivePlayer();
            return "Round " + state.getRoundNumber() + " | " + phaseName()
                    + " | " + (active == null ? "-" : active.getName());
        }

        public String passLabel() {
            if (controller.isWaitingForHumanConflictAttack()) return "Skip Attack";
            if (controller.isWaitingForHumanConflictJoin()) return "Decline Join";
            if (controller.isWaitingForHumanDrawEnd()) return "End Buys";
            if (state.getPhase() == GamePhase.CONFLICT) return "Pass (no conflict)";
            return "Pass Turn";
        }

        public boolean isPassEnabled() {
            // Mirrors MainWindow's five-way pass predicate exactly, by identity
            // of the active player rather than by re-deriving turn ownership.
            boolean myTurn = (state.getActivePlayer() == state.getHumanPlayer());
            GamePhase p = state.getPhase();
            return ((p == GamePhase.ACTION || p == GamePhase.CONFLICT) && myTurn)
                    || controller.isWaitingForHumanConflictAttack()
                    || controller.isWaitingForHumanConflictJoin()
                    || controller.isWaitingForHumanDrawEnd();
        }

        public boolean hasSelection() { return false; }

        public boolean isLegal(String actionId) { return false; }

        public String refusalReason(String actionId) {
            return "not migrated: no action is registered in the shell dock yet";
        }

        public String whyNoActions() {
            return "No card selected; the shell dock has no specs registered yet.";
        }

        public boolean hasPendingDecision() { return false; }

        public String pendingDecisionId() { return null; }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Faction chooseFaction() {
        Object[] options = { "Human", "Minbari", "Centauri", "Narn" };
        int choice = JOptionPane.showOptionDialog(null,
            "Choose your faction:", "Babylon 5 CCG — Choose Faction",
            JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
            null, options, options[0]);
        switch (choice) {
            case 1: return Faction.MINBARI;
            case 2: return Faction.CENTAURI;
            case 3: return Faction.NARN;
            default: return Faction.HUMAN;
        }
    }

    private static List<Faction> otherFactions(Faction chosen) {
        List<Faction> all = new ArrayList<Faction>(
            Arrays.asList(Faction.HUMAN, Faction.MINBARI, Faction.CENTAURI, Faction.NARN));
        all.remove(chosen);
        return all;
    }

    private static List<Card> buildFactionDeck(List<Card> all, Faction faction) {
        // B5-0319: use the real printed Premier starter deck (50 fixed + 10
        // random uncommons/rares) when the deck resource is available; fall
        // back to the legacy heuristic for a minimal/partial card pool.
        if (StarterDeckBuilder.isAvailable()) {
            try {
                return StarterDeckBuilder.build(faction, all);
            } catch (Exception e) {
                System.err.println("Starter deck build failed for " + faction
                    + ", using heuristic deck: " + e.getMessage());
            }
        }
        List<Card> deck = new ArrayList<Card>();
        // 1. Faction-specific cards first
        for (Card c : all) {
            if (c.getFaction() == faction && deck.size() < 30) deck.add(c);
        }
        // 2. Neutral and ANY faction fillers
        for (Card c : all) {
            if (deck.size() >= 60) break;
            Faction f = c.getFaction();
            if (f == Faction.ANY || f == Faction.NEUTRAL || f == Faction.NON_ALIGNED) {
                deck.add(c);
            }
        }
        // 3. Pad to 60 if needed
        Collections.shuffle(deck);
        while (deck.size() < 60 && !all.isEmpty()) {
            deck.add(all.get(new Random().nextInt(all.size())));
        }
        return deck.subList(0, Math.min(60, deck.size()));
    }

    /** Minimal fallback card set used if JSON files are not on the classpath. */
    private static List<Card> buildMinimalTestSet() {
        List<Card> cards = new ArrayList<Card>();
        cards.add(new CharacterCard("test_sinclair","Jeffrey Sinclair","CHARACTER_HUMAN",
            Rarity.FIXED, Faction.HUMAN, CardSet.PREMIERE,"jeffrey_sinclair",
            "Human Ambassador.",5,3,0,4,true));
        cards.add(new CharacterCard("test_gkar","G'Kar","CHARACTER_NARN",
            Rarity.FIXED, Faction.NARN, CardSet.PREMIERE,"gkar",
            "Narn Ambassador.",4,5,0,3,true));
        cards.add(new CharacterCard("test_delenn","Delenn","CHARACTER_MINBARI",
            Rarity.FIXED, Faction.MINBARI, CardSet.PREMIERE,"delenn",
            "Minbari Ambassador.",7,3,2,5,true));
        cards.add(new CharacterCard("test_londo","Londo Mollari","CHARACTER_CENTAURI",
            Rarity.FIXED, Faction.CENTAURI, CardSet.PREMIERE,"londo_mollari",
            "Centauri Ambassador.",5,6,0,4,true));
        for (int i = 0; i < 15; i++) {
            cards.add(new ConflictCard("test_dip_" + i, "Campaign for Support",
                "CONFLICT_DIPLOMACY", Rarity.COMMON, Faction.ANY, CardSet.PREMIERE,
                "campaign_for_support", "Diplomacy conflict. Winner gains 2 Influence.",
                ConflictType.DIPLOMACY, 2));
            cards.add(new ConflictCard("test_mil_" + i, "Border Raid",
                "CONFLICT_MILITARY", Rarity.FIXED, Faction.ANY, CardSet.PREMIERE,
                "border_raid", "Military conflict. Winner gains 2 Influence.",
                ConflictType.MILITARY, 2));
            cards.add(new FleetCard("test_fleet_" + i, "Picket Fleet",
                "FLEET_HUMAN", Rarity.FIXED, Faction.ANY, CardSet.PREMIERE,
                "picket_fleet_human", "Basic patrol fleet.", 3));
            cards.add(new EventCard("test_evt_" + i, "Short Term Goals",
                "EVENT", Rarity.FIXED, Faction.ANY, CardSet.PREMIERE,
                "short_term_goals", "Gain 1 Influence immediately."));
        }
        return cards;
    }
}
