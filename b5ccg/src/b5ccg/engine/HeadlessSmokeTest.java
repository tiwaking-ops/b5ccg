package b5ccg.engine;

import b5ccg.ai.AIPlayer;
import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.Deck;
import b5ccg.model.GameAction;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.AIDifficulty;
import b5ccg.model.enums.CardType;
import b5ccg.model.enums.Faction;

import java.util.ArrayList;
import java.util.List;

/**
 * B5-0201 — headless smoke test for the game engine.
 *
 * Drives the real engine end to end with no Swing/GUI: DeckLoader loads both
 * card sets off the classpath, four all-AI players are dealt decks, and
 * GameController runs a full round of AI turns on a background thread while this
 * class watches the observable GameState. It then asks each AIPlayer for a
 * decision on the live state and checks the result is legal.
 *
 * Fails loudly (non-zero exit + stack trace) on any exception, on an empty card
 * load, on a stalled round, or on an illegal AI choice.
 *
 * Run after compile.bat / compile.sh:
 *   java -cp b5ccg/out b5ccg.engine.HeadlessSmokeTest
 */
public class HeadlessSmokeTest {

    /** Wall-clock budget for one full round of AI turns. */
    private static final long ROUND_TIMEOUT_MS = 120000L;

    private static final String[] NAMES = { "Alpha", "Beta", "Gamma", "Delta" };
    private static final Faction[] FACTIONS = {
        Faction.HUMAN, Faction.MINBARI, Faction.CENTAURI, Faction.NARN
    };
    private static final AIDifficulty[] DIFFICULTIES = {
        AIDifficulty.EASY, AIDifficulty.MEDIUM, AIDifficulty.HARD, AIDifficulty.MEDIUM
    };

    private static volatile Throwable gameLoopError = null;
    private static final int[] stateUpdates = new int[1];

    public static void main(String[] args) {
        boolean passed = false;
        try {
            passed = runSmokeTest();
        } catch (Throwable t) {
            System.err.println();
            System.err.println("SMOKE TEST FAILED - unexpected exception:");
            t.printStackTrace(System.err);
        }
        System.out.flush();
        System.err.flush();
        // The game loop is a daemon thread; exit immediately so a still-running
        // (or stalled) game can never hang the test run.
        System.exit(passed ? 0 : 1);
    }

    private static boolean runSmokeTest() throws Exception {
        System.out.println("=== B5 CCG headless smoke test (B5-0201) ===");
        System.out.println("java.version = " + System.getProperty("java.version"));

        // ── 1. Deck loading ───────────────────────────────────────────────────
        System.out.print("[1/5] DeckLoader.loadBothSets() ... ");
        List<Card> allCards = DeckLoader.loadBothSets();
        if (allCards.isEmpty()) {
            return fail("DeckLoader returned zero cards - is b5ccg/resources/cards/ on the classpath? Run compile.bat/sh first.");
        }
        System.out.println(allCards.size() + " cards");

        // ── 2. All-AI players with faction decks ──────────────────────────────
        System.out.print("[2/5] building 4 all-AI players ... ");
        List<Player> players = new ArrayList<Player>();
        List<AIPlayer> aiPlayers = new ArrayList<AIPlayer>();
        for (int i = 0; i < NAMES.length; i++) {
            Player p = new Player(NAMES[i], FACTIONS[i], false);
            List<Card> deckCards = buildFactionDeck(allCards, FACTIONS[i]);
            if (deckCards.isEmpty()) {
                return fail("no cards available for faction " + FACTIONS[i]);
            }
            p.setDeck(new Deck(deckCards));
            // B5-0313: guarantee the faction ambassador is in the opening
            // hand — GameController.setupGame() extracts it from the hand
            // only, and without it every conflict resolves 0 vs 0 (B5-0312
            // playtest finding). Deck shuffles in its constructor, so pin
            // the ambassador to the draw pile's top AFTER construction.
            CharacterCard amb = findAmbassadorCard(deckCards, FACTIONS[i]);
            if (amb != null) p.getDeck().addToTop(amb);
            p.drawCards(4);
            if (p.getHand().isEmpty()) {
                return fail("player " + NAMES[i] + " drew an empty opening hand");
            }
            players.add(p);
            aiPlayers.add(new AIPlayer(p, DIFFICULTIES[i]));
        }
        System.out.println(players.size() + " players, "
            + players.get(0).getHand().size() + " opening cards each");

        final GameState state = new GameState(players);

        // ── 3. One full round through the real GameController, headless ───────
        System.out.println("[3/5] running one full AI round headless (no GUI) ...");
        final GameController controller = new GameController(state, aiPlayers,
            new GameStateCallback() {
                public void accept(GameState gs) { stateUpdates[0]++; }
            });

        Thread gameLoop = new Thread(new Runnable() {
            public void run() {
                try {
                    controller.runGame();
                } catch (Throwable t) {
                    gameLoopError = t;
                }
            }
        }, "b5-smoke-game-loop");
        gameLoop.setDaemon(true);

        long start = System.currentTimeMillis();
        gameLoop.start();

        boolean roundCompleted = false;
        while (System.currentTimeMillis() - start < ROUND_TIMEOUT_MS) {
            if (gameLoopError != null) break;
            if (state.isGameOver() || state.getRoundNumber() > 1) {
                roundCompleted = true;
                break;
            }
            Thread.sleep(25);
        }
        long elapsed = System.currentTimeMillis() - start;

        if (gameLoopError != null) {
            System.err.println("      game loop threw after " + elapsed + " ms");
            gameLoopError.printStackTrace(System.err);
            return fail("GameController.runGame() raised an exception");
        }
        if (!roundCompleted) {
            return fail("no round completed within " + ROUND_TIMEOUT_MS
                + " ms - engine appears stuck (phase=" + state.getPhase()
                + ", round=" + state.getRoundNumber()
                + ", lastLog=" + state.getLastLogEntry() + ")");
        }
        System.out.println("      " + (state.isGameOver() ? "game ended (victory)" : "round 1 completed")
            + " in " + elapsed + " ms; phase=" + state.getPhase()
            + ", round=" + state.getRoundNumber());

        // ── 4. Verify the AIs actually took turns and the state is coherent ───
        System.out.print("[4/5] verifying AI turns + state integrity ... ");
        int[] turnsTaken = new int[NAMES.length];
        int totalTurns = 0;
        for (int i = 0; i < state.getLog().size(); i++) {
            String line = stripRoundPrefix(state.getLog().get(i));
            for (int n = 0; n < NAMES.length; n++) {
                if (line.startsWith(NAMES[n] + ": ")) {
                    turnsTaken[n]++;
                    totalTurns++;
                }
            }
        }
        if (totalTurns == 0) {
            return fail("no AI actions were logged - no player ever took a turn"
                + logExcerpt(state));
        }
        if (stateUpdates[0] == 0) {
            return fail("GameStateCallback never fired - the game loop did not notify the UI layer");
        }
        if (!state.isGameOver()) {
            for (int n = 0; n < NAMES.length; n++) {
                if (turnsTaken[n] == 0) {
                    return fail("player " + NAMES[n] + " never took a turn in the completed round"
                        + logExcerpt(state));
                }
            }
        }
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            if (p.getDeck() == null) {
                return fail("player " + p.getName() + " lost its deck");
            }
            if (p.getHand() == null) {
                return fail("player " + p.getName() + " lost its hand");
            }
        }
        System.out.println(totalTurns + " AI actions, " + stateUpdates[0]
            + " UI callbacks, log=" + state.getLog().size() + " lines");

        // ── 5. AIPlayer decisions on the live state must be legal ─────────────
        System.out.print("[5/5] AIPlayer.chooseAction() legality on live state ... ");
        for (int i = 0; i < aiPlayers.size(); i++) {
            AIPlayer ai = aiPlayers.get(i);
            Player p = players.get(i);
            GameAction action = ai.chooseAction(state, p);
            if (action == null) {
                return fail("AIPlayer(" + NAMES[i] + ", " + DIFFICULTIES[i]
                    + ") returned a null action");
            }
            if (action.getType() == null) {
                return fail("AIPlayer(" + NAMES[i] + ") returned an action with a null type");
            }
            Card card = action.getCard();
            if (card != null) {
                // Action payloads for these types are controlled cards, not hand
                // cards. The GameController/RulesEngine enforce their real gates.
                // B5-0505: the agenda lifecycle (B5-0364) carries the IN-PLAY
                // agenda as payload; heal/repair/lead-fleet carry table cards;
                // mercenary bids carry pool cards; attack carries the committed
                // participant; reveal-contingency carries the placed card. The
                // old three-type exclusion list missed these families, so any
                // live offer of them failed the hand-membership check
                // (draw-dependent ~1-in-8 flake, triaged in B5-0495).
                // REPLACE_AGENDA and PLAY_CONTINGENCY stay checked: their
                // payloads ARE hand cards.
                boolean slotPayload =
                        action.getType() == GameAction.Type.BUILD_INFLUENCE
                     || action.getType() == GameAction.Type.PROMOTE_CHARACTER
                     || action.getType() == GameAction.Type.USE_ROTATE_EFFECT
                     || action.getType() == GameAction.Type.DISCARD_AGENDA
                     || action.getType() == GameAction.Type.REVEAL_AGENDA
                     || action.getType() == GameAction.Type.HEAL_CHARACTER
                     || action.getType() == GameAction.Type.REPAIR_CARD
                     || action.getType() == GameAction.Type.LEAD_FLEET
                     || action.getType() == GameAction.Type.BID_ON_MERCENARY
                     || action.getType() == GameAction.Type.ATTACK_CONFLICT_PARTICIPANT
                     || action.getType() == GameAction.Type.REVEAL_CONTINGENCY;
                if (!slotPayload && !p.getHand().contains(card)) {
                    return fail("AIPlayer(" + NAMES[i] + ") chose " + action
                        + " but that card is not in its hand");
                }
                if (!card.getFaction().isPlayableBy(p.getFaction())) {
                    return fail("AIPlayer(" + NAMES[i] + ") chose illegal card "
                        + card.getTitle() + " (faction " + card.getFaction()
                        + " for player faction " + p.getFaction() + ")");
                }
            }
        }
        System.out.println("OK (" + aiPlayers.size() + " decisions legal)");

        System.out.println();
        System.out.println("SMOKE TEST PASSED - DeckLoader, GameState and a full AI round ran headless.");
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Faction cards first, then quota guarantees, then neutral/ANY fillers,
     *  capped at 60 cards.
     *
     *  B5-0313: the previous file-order filler cut excluded every conflict
     *  card (all 108 are faction ANY and sit at filler position #42+), so
     *  the smoke scenario could never produce a conflict (B5-0312 headline
     *  finding). Quota passes guarantee conflicts and agendas enter each
     *  AI deck, so the run exercises the conflict pipeline. Harness-only:
     *  game-logic files are untouched. */
    private static List<Card> buildFactionDeck(List<Card> all, Faction faction) {
        // B5-0319: use the real printed Premier starter deck (50 fixed + 10
        // random uncommons/rares) when the deck resource is available. The
        // fixed lists already contain conflict cards + an agenda, so the
        // B5-0313 quota passes are only needed for the heuristic fallback.
        if (StarterDeckBuilder.isAvailable()) {
            try {
                return StarterDeckBuilder.build(faction, all);
            } catch (Exception e) {
                System.err.println("Starter deck build failed for " + faction
                    + ", using heuristic deck: " + e.getMessage());
            }
        }
        List<Card> deck = new ArrayList<Card>();
        // 1. Own-faction cards (in file order).
        for (int i = 0; i < all.size() && deck.size() < 60; i++) {
            Card c = all.get(i);
            if (c.getFaction() == faction) deck.add(c);
        }
        // 2. Quota guarantees: 12 conflicts + 2 agendas per deck.
        int conflicts = 0;
        for (int i = 0; i < all.size() && conflicts < 12; i++) {
            Card c = all.get(i);
            if (c.getType() == CardType.CONFLICT) { deck.add(c); conflicts++; }
        }
        int agendas = 0;
        for (int i = 0; i < all.size() && agendas < 2; i++) {
            Card c = all.get(i);
            if (c.getType() == CardType.AGENDA) { deck.add(c); agendas++; }
        }
        // 3. Neutral/ANY fillers, in file order, capped at 60.
        for (int i = 0; i < all.size() && deck.size() < 60; i++) {
            Card c = all.get(i);
            Faction f = c.getFaction();
            if (f == Faction.ANY || f == Faction.NEUTRAL || f == Faction.NON_ALIGNED) {
                deck.add(c);
            }
        }
        return deck;
    }

    /** The faction's ambassador card inside a deck list (harness mirror of
     *  GameController.findAmbassador's rule), or null. */
    private static CharacterCard findAmbassadorCard(List<Card> deckCards, Faction faction) {
        for (int i = 0; i < deckCards.size(); i++) {
            Card c = deckCards.get(i);
            if (c instanceof CharacterCard) {
                CharacterCard ch = (CharacterCard) c;
                if (ch.isAmbassador() && ch.getFaction() == faction) return ch;
            }
        }
        return null;
    }

    /** GameState.log() prefixes entries with "[R<round>] "; strip it to find the actor. */
    private static String stripRoundPrefix(String line) {
        if (line == null) return "";
        if (line.startsWith("[R")) {
            int close = line.indexOf("] ");
            if (close >= 0) return line.substring(close + 2);
        }
        return line;
    }

    /** First few log lines, indented, for failure diagnostics. */
    private static String logExcerpt(GameState state) {
        List<String> log = state.getLog();
        StringBuilder sb = new StringBuilder();
        int n = Math.min(8, log.size());
        for (int i = 0; i < n; i++) {
            sb.append("\n        ").append(log.get(i));
        }
        return sb.toString();
    }

    private static boolean fail(String message) {
        System.err.println();
        System.err.println("SMOKE TEST FAILED: " + message);
        return false;
    }
}
