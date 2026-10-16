package b5ccg.engine;

import b5ccg.ai.AIPlayer;
import b5ccg.model.*;
import b5ccg.model.enums.*;
import java.util.*;
import java.lang.reflect.*;

/**
 * B5-0349 — Seeded multi-round runner (harness-only, new file).
 *
 * Runs N complete games headless with seedable AI Random (via reflection on
 * AIPlayer.rng), then prints a summary: total conflicts initiated/won/lost,
 * promotions, Build Influence uses, aftermaths played, agendas set, and
 * per-game winners. No game-logic files are edited.
 *
 * Usage:
 *   java -cp b5ccg/out b5ccg.engine.HeadlessMultiRoundTest [numGames] [seed] [timeoutSec]
 *
 * Default: 10 games, seed 42, 180s timeout per game. Each game runs until a
 * rulebook winner (state.getWinner != null), the per-action safety cap ends a
 * stalled round, or the per-game timeout fires via GameController.runGame();
 * the log is parsed afterward for action stats. AIPlayer RNG is seeded via
 * reflection so identical seeds reproduce identical games.
 *
 * B5-0444 enhancements: parameterized timeout (was hardcoded 60s), per-round
 * progress lines during each game, and an explicit terminating-condition
 * classification per game (WINNER / ROUND_CAP / TIMEOUT). The B5-0413
 * promotes-counter fix (" promotes " token, no colon) is preserved.
 */
public class HeadlessMultiRoundTest {

    public static void main(String[] args) {
        int numGames = 10;
        long seed = 42;
        long timeoutMs = 180000L;  // B5-0444: was hardcoded 60s; now parameterized.

        if (args.length >= 1) {
            try { numGames = Integer.parseInt(args[0]); }
            catch (NumberFormatException e) {
                System.err.println("Invalid numGames '" + args[0] + "', using default 10");
            }
        }
        if (args.length >= 2) {
            try { seed = Long.parseLong(args[1]); }
            catch (NumberFormatException e) {
                System.err.println("Invalid seed '" + args[1] + "', using default 42");
            }
        }
        if (args.length >= 3) {
            try { timeoutMs = Long.parseLong(args[2]) * 1000L; }
            catch (NumberFormatException e) {
                System.err.println("Invalid timeout '" + args[2] + "', using default 180s");
            }
        }

        System.out.println("=== B5 CCG seeded multi-round runner (B5-0349 / B5-0444) ===");
        System.out.println("Games: " + numGames + ", Seed: " + seed
                + ", Per-game timeout: " + (timeoutMs / 1000) + "s");

        // ── 1. Deck loading ───────────────────────────────────────────────────
        long deckStart = System.currentTimeMillis();
        List<Card> allCards;
        try {
            allCards = DeckLoader.loadBothSets();
        } catch (Exception e) {
            System.err.println("FAILED: " + e.getMessage());
            System.exit(3);
            allCards = new ArrayList<Card>();
        }
        long deckTime = System.currentTimeMillis() - deckStart;
        if (allCards.isEmpty()) {
            System.err.println("FAILED: DeckLoader returned zero cards");
            System.exit(3);
        }
        System.out.println("[1] " + allCards.size() + " cards loaded in " + deckTime + "ms");

        // ── 2. Aggregate counters ─────────────────────────────────────────────
        int totalCi = 0, totalCw = 0, totalCl = 0;
        int totalPr = 0, totalBl = 0, totalAm = 0, totalAg = 0;
        String[] gameWinners = new String[numGames];

        String[] names = {"Alpha", "Beta", "Gamma", "Delta"};
        Faction[] factions = {Faction.HUMAN, Faction.MINBARI, Faction.CENTAURI, Faction.NARN};
        AIDifficulty[] difficulties = {AIDifficulty.EASY, AIDifficulty.MEDIUM,
                                        AIDifficulty.HARD, AIDifficulty.MEDIUM};

        long totalRunMs = 0;

        // ── 3. Run N games ────────────────────────────────────────────────────
        for (int game = 0; game < numGames; game++) {
            long gameSeed = seed + game * 10000L;
            System.out.println("\n--- Game " + (game + 1) + "/" + numGames
                + " (seed " + gameSeed + ") ---");

            List<Player> players = new ArrayList<Player>();
            List<AIPlayer> aiPlayers = new ArrayList<AIPlayer>();

            for (int i = 0; i < names.length; i++) {
                Player p = new Player(names[i], factions[i], false);
                List<Card> deckCards = buildFactionDeck(allCards, factions[i]);
                if (deckCards.isEmpty()) {
                    System.err.println("FAILED: no cards for faction " + factions[i]);
                    System.exit(3);
                }
                p.setDeck(new Deck(deckCards));
                CharacterCard amb = findAmbassadorCard(deckCards, factions[i]);
                if (amb != null) p.getDeck().addToTop(amb);
                p.drawCards(4);
                players.add(p);

                AIPlayer ai = new AIPlayer(p, difficulties[i]);
                try {
                    Field rngField = AIPlayer.class.getDeclaredField("rng");
                    rngField.setAccessible(true);
                    rngField.set(ai, new Random(gameSeed + i * 1000L));
                } catch (Exception e) {
                    System.err.println("WARNING: seed failed for " + names[i] + ": " + e.getMessage());
                }
                aiPlayers.add(ai);
            }

            final GameState state = new GameState(players);
            final GameController controller =
                new GameController(state, aiPlayers, new GameStateCallback() {
                    public void accept(GameState gs) {}
                });

            final boolean[] done = new boolean[1];

            Thread t = new Thread(new Runnable() {
                public void run() {
                    try { controller.runGame(); } catch (Throwable th) { th.printStackTrace(System.err); }
                    finally { done[0] = true; }
                }
            }, "b5-game-" + game);
            t.setDaemon(true);
            long start = System.currentTimeMillis();
            t.start();

            int lastRound = 0;
            while (!done[0] && t.isAlive()) {
                long now = System.currentTimeMillis();
                if (now - start > timeoutMs) {
                    int r = state.getRoundNumber();
                    System.err.println("Game " + (game + 1) + " timed out after "
                            + (timeoutMs / 1000) + "s at round " + r + " — stopping");
                    break;
                }
                int curRound = state.getRoundNumber();
                if (curRound > lastRound && curRound != 0) {
                    lastRound = curRound;
                    System.out.println("    [game " + (game + 1) + "] reached round "
                            + curRound + " after " + (System.currentTimeMillis() - start) / 1000 + "s");
                }
                try { Thread.sleep(250); } catch (InterruptedException e) { break; }
            }
            try { t.join(2000); } catch (InterruptedException e) { /* ignore */ }

            long elapsed = System.currentTimeMillis() - start;
            totalRunMs += elapsed;

            String terminator;
            if (state.getWinner() != null) {
                terminator = "WINNER";
            } else if (!done[0] && !t.isAlive()) {
                terminator = "ROUND_CAP";
            } else {
                terminator = "TIMEOUT";
            }

            // Parse log for stats
            int[] stats = parseLog(state);
            totalCi += stats[0]; totalCw += stats[1]; totalCl += stats[2];
            totalPr += stats[3]; totalBl += stats[4]; totalAm += stats[5]; totalAg += stats[6];

            gameWinners[game] = state.getWinner() != null
                ? state.getWinner().getName() : "stalled";

            System.out.println("  round=" + state.getRoundNumber()
                + " elapsed=" + elapsed + "ms"
                + " winner=" + gameWinners[game]
                + " terminator=" + terminator);
            System.out.println("  conflicts init/won/lost="
                + stats[0] + "/" + stats[1] + "/" + stats[2]
                + " promotes=" + stats[3]
                + " builds=" + stats[4]
                + " aftermaths=" + stats[5]
                + " agendas=" + stats[6]);
        }

        // ── 4. Summary ────────────────────────────────────────────────────────
        System.out.println("\n=== N-game summary ===");
        System.out.println("Games: " + numGames
            + " total deck load: " + deckTime + "ms"
            + " total game time: " + totalRunMs + "ms");
        System.out.println();

        int[] wins = new int[names.length];
        for (int g = 0; g < numGames; g++) {
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(gameWinners[g])) { wins[i]++; break; }
            }
        }
        for (int i = 0; i < names.length; i++) {
            System.out.println(names[i] + " (" + factions[i] + ", " + difficulties[i] + "): "
                + wins[i] + " win" + (wins[i] == 1 ? "" : "s"));
        }

        System.out.println();
        System.out.println("Aggregate across all games:");
        System.out.println("  Conflicts initiated: " + totalCi);
        System.out.println("  Conflicts won:       " + totalCw);
        System.out.println("  Conflicts lost:      " + totalCl);
        System.out.println("  Promotions:          " + totalPr);
        System.out.println("  Build Influence:     " + totalBl);
        System.out.println("  Aftermaths played:   " + totalAm);
        System.out.println("  Agendas set:         " + totalAg);
        System.out.println();

        System.out.println("Per-game winners:");
        for (int g = 0; g < numGames; g++) {
            System.out.println("  Game " + (g + 1) + ": " + gameWinners[g]);
        }

        System.out.println("\nSEEDED RUN COMPLETE");
    }

    /**
     * Parse the game log and return [conflictsInitiated, conflictsWon, conflictsLost,
     * promotions, builds, aftermaths, agendas].
     *
     * Log format (from GameController):
     * - "X: INITIATE_CONFLICT: CardName -> target"
     * - "CardName won by winner (support=N, opposition=M)"
     * - "X: builds influence: CardName rotates, rating now N"
     * - "X promotes CardName to the Inner Circle (...)"  (no colon; B5-0409)
     * - "X plays aftermath: CardName on target"
     * - "X sets agenda: CardName"
     * - "X plays CardName"
     */
    private static int[] parseLog(GameState state) {
        List<String> log = state.getLog();
        int ci = 0, cw = 0, cl = 0, pr = 0, bl = 0, am = 0, ag = 0;

        for (int i = 0; i < log.size(); i++) {
            String line = log.get(i);
            String content = stripRoundPrefix(line);

            if (content.contains(": INITIATE_CONFLICT:")) { ci++; }
            else if (content.contains(" won by ")) {
                int wIdx = content.indexOf(" won by ");
                String winner = content.substring(wIdx + 8);
                int paren = winner.indexOf('(');
                if (paren > 0) winner = winner.substring(0, paren).trim();
                int colon = winner.indexOf(':');
                if (colon > 0) winner = winner.substring(0, colon).trim();

                String initiator = null;
                for (int j = i - 1; j >= 0; j--) {
                    String prev = stripRoundPrefix(log.get(j));
                    if (prev.contains(": INITIATE_CONFLICT:")) {
                        int cbIdx = prev.indexOf(": INITIATE_CONFLICT:");
                        String cand = prev.substring(0, cbIdx).trim();
                        int c = cand.indexOf(':');
                        if (c > 0) cand = cand.substring(0, c).trim();
                        initiator = cand;
                        break;
                    }
                }
                if (initiator != null) {
                    if (initiator.equals(winner)) cw++; else cl++;
                }
            }
            else if (content.contains(" builds influence:")) { bl++; }
            else if (content.contains(" promotes ")) { pr++; }
            else if (content.contains(" plays aftermath:")) { am++; }
            else if (content.contains(" sets agenda:")) { ag++; }
            // B5-0464: the token above has a real emitter since B5-0464 --
            // GameController logs "<p> sets agenda: <title>" on face-up
            // agenda install (hidden sponsors still log their own line, and
            // REPLACE/DISCARD/REVEAL are separate actions with separate
            // emissions; this bucket counts installs only).
        }

        return new int[]{ci, cw, cl, pr, bl, am, ag};
    }

    private static String stripRoundPrefix(String line) {
        if (line == null) return "";
        if (line.startsWith("[R")) {
            int close = line.indexOf("] ");
            if (close >= 0) return line.substring(close + 2);
        }
        return line;
    }

    // ── Deck-building helper (mirrors HeadlessSmokeTest.buildFactionDeck) ─────

    private static List<Card> buildFactionDeck(List<Card> all, Faction faction) {
        if (StarterDeckBuilder.isAvailable()) {
            try {
                return StarterDeckBuilder.build(faction, all);
            } catch (Exception e) {
                System.err.println("Starter deck failed for " + faction
                    + ", using heuristic: " + e.getMessage());
            }
        }
        List<Card> deck = new ArrayList<Card>();
        for (int i = 0; i < all.size() && deck.size() < 60; i++) {
            Card c = all.get(i);
            if (c.getFaction() == faction) deck.add(c);
        }
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
        for (int i = 0; i < all.size() && deck.size() < 60; i++) {
            Card c = all.get(i);
            Faction f = c.getFaction();
            if (f == Faction.ANY || f == Faction.NEUTRAL || f == Faction.NON_ALIGNED) {
                deck.add(c);
            }
        }
        return deck;
    }

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
}
