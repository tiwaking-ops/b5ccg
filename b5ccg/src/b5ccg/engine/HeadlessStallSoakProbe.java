package b5ccg.engine;

import b5ccg.ai.AIPlayer;
import b5ccg.model.*;
import b5ccg.model.enums.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * B5-0589 — Stall-rate soak probe (harness-only, new file).
 *
 * Runs N seeded games headless (one per base seed, mirroring the
 * HeadlessMultiRoundTest construction: same factions, difficulties,
 * quota'd faction decks and per-seat RNG seeding) and prints a per-seed
 * table: rounds reached, winner (or none), winning condition, terminator
 * classification, and end-of-run influence standings. Exit code is nonzero
 * ONLY on harness exceptions (deck load failure, constructor failure) so
 * soak drivers such as the B5-0577 measurement can loop seeds without
 * touching game logic — a stalled or timed-out game is a reported
 * classification, not a failure.
 *
 * Usage:
 *   java -cp b5ccg/out b5ccg.engine.HeadlessStallSoakProbe [numSeeds] [baseSeed] [timeoutSec]
 *   java -cp b5ccg/out b5ccg.engine.HeadlessStallSoakProbe 10,42,44,... [timeoutSec]
 *
 * Default: 10 seeds from 42, 180s timeout per game.
 *
 * Java 6 only. No game-logic files are edited (B5-0201 harness precedent).
 */
public class HeadlessStallSoakProbe {

    private static final String[] NAMES = {"Alpha", "Beta", "Gamma", "Delta"};
    private static final Faction[] FACTIONS =
        {Faction.HUMAN, Faction.MINBARI, Faction.CENTAURI, Faction.NARN};
    private static final AIDifficulty[] DIFFICULTIES =
        {AIDifficulty.EASY, AIDifficulty.MEDIUM, AIDifficulty.HARD, AIDifficulty.MEDIUM};

    public static void main(String[] args) {
        // ── Argument parsing (B5-0349 style, Java 6) ─────────────────────────
        List<Long> seeds = new ArrayList<Long>();
        long timeoutMs = 180000L;
        try {
            if (args.length >= 1) {
                if (args[0].indexOf(',') >= 0) {
                    StringTokenizer tok = new StringTokenizer(args[0], ",");
                    while (tok.hasMoreTokens()) {
                        seeds.add(Long.valueOf(Long.parseLong(tok.nextToken().trim())));
                    }
                } else {
                    int n = Integer.parseInt(args[0]);
                    long base = (args.length >= 2) ? Long.parseLong(args[1]) : 42L;
                    for (int i = 0; i < n; i++) seeds.add(Long.valueOf(base + i));
                }
                if (args.length >= 2 && args[0].indexOf(',') < 0) {
                    timeoutMs = Long.parseLong(args[2]) * 1000L;
                } else if (args.length >= 2 && args[0].indexOf(',') >= 0) {
                    timeoutMs = Long.parseLong(args[1]) * 1000L;
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid argument: " + e.getMessage());
            usage();
            System.exit(2);
        }
        if (seeds.isEmpty()) {
            for (int i = 0; i < 10; i++) seeds.add(Long.valueOf(42L + i));
        }

        System.out.println("=== B5 CCG stall-rate soak probe (B5-0589) ===");
        System.out.println("Seeds: " + seeds + ", per-game timeout: " + (timeoutMs / 1000) + "s");

        // ── Deck load (failure is the only hard exit before the table) ───────
        List<Card> allCards;
        try {
            allCards = DeckLoader.loadBothSets();
        } catch (Exception e) {
            System.err.println("HARNESS-EXCEPTION: deck load failed: " + e.getMessage());
            System.exit(3);
            return;
        }
        if (allCards.isEmpty()) {
            System.err.println("HARNESS-EXCEPTION: DeckLoader returned zero cards");
            System.exit(3);
            return;
        }
        System.out.println("[" + allCards.size() + " cards loaded]");

        // ── Per-seed run + table rows ─────────────────────────────────────────
        int winners = 0, stalls = 0;
        StringBuilder table = new StringBuilder();
        for (int g = 0; g < seeds.size(); g++) {
            long seed = seeds.get(g).longValue();
            GameResult r;
            try {
                r = runOneSeed(allCards, seed, timeoutMs);
            } catch (Throwable th) {
                System.err.println("HARNESS-EXCEPTION at seed " + seed + ": " + th);
                th.printStackTrace(System.err);
                System.exit(3);
                return;
            }
            if ("WINNER".equals(r.terminator)) winners++; else stalls++;
            table.append(pad(Long.toString(seed), 7))
                 .append(pad(Integer.toString(r.rounds), 7))
                 .append(pad(r.terminator, 10))
                 .append(pad(r.winnerName, 12))
                 .append(pad(r.condition, 26))
                 .append(r.standings)
                 .append("\n");
            System.out.println("  seed " + seed + ": round " + r.rounds
                + ", terminator=" + r.terminator
                + (r.winnerName.length() > 0 ? (", winner=" + r.winnerName + " (" + r.condition + ")") : ", winner=none"));
        }

        System.out.println();
        System.out.println("Per-seed table:");
        System.out.println("seed   rounds  terminator winner       condition                 standings(inf)");
        System.out.print(table);
        System.out.println();
        System.out.println("Summary: " + seeds.size() + " seeds, " + winners + " WINNER, "
            + stalls + " stalled/window-capped (" + pct(stalls, seeds.size())
            + " upper-bound at this window)");
        System.out.println("STALL-SOAK PROBE COMPLETE (classifications are reporting, not failures)");
    }

    private static void usage() {
        System.err.println("Usage: HeadlessStallSoakProbe [numSeeds] [baseSeed] [timeoutSec]");
        System.err.println("       HeadlessStallSoakProbe seed,seed,... [timeoutSec]");
    }

    /** Result carrier for one seeded game. */
    private static class GameResult {
        int rounds;
        String terminator = "UNKNOWN";
        String winnerName = "";
        String condition = "";
        String standings = "";
    }

    /**
     * Runs one seeded game on a daemon thread (HeadlessMultiRoundTest pattern)
     * and classifies the outcome. Never throws on game internals — the runGame
     * thread swallows Throwable; only construction problems propagate.
     */
    private static GameResult runOneSeed(List<Card> allCards, long seed, long timeoutMs)
            throws Exception {
        GameResult result = new GameResult();

        List<Player> players = new ArrayList<Player>();
        List<AIPlayer> aiPlayers = new ArrayList<AIPlayer>();
        for (int i = 0; i < NAMES.length; i++) {
            Player p = new Player(NAMES[i], FACTIONS[i], false);
            List<Card> deckCards = buildFactionDeck(allCards, FACTIONS[i]);
            if (deckCards.isEmpty()) {
                throw new IllegalStateException("no cards for faction " + FACTIONS[i]);
            }
            p.setDeck(new Deck(deckCards));
            CharacterCard amb = findAmbassadorCard(deckCards, FACTIONS[i]);
            if (amb != null) p.getDeck().addToTop(amb);
            p.drawCards(4);
            players.add(p);

            AIPlayer ai = new AIPlayer(p, DIFFICULTIES[i]);
            try {
                Field rngField = AIPlayer.class.getDeclaredField("rng");
                rngField.setAccessible(true);
                rngField.set(ai, new Random(seed + i * 1000L));
            } catch (Exception e) {
                System.err.println("WARNING: seed failed for " + NAMES[i] + ": " + e.getMessage());
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
                try { controller.runGame(); }
                catch (Throwable th) { th.printStackTrace(System.err); }
                finally { done[0] = true; }
            }
        }, "b5-soak-" + seed);
        t.setDaemon(true);
        long start = System.currentTimeMillis();
        t.start();

        while (!done[0] && t.isAlive()) {
            if (System.currentTimeMillis() - start > timeoutMs) break;
            try { Thread.sleep(250); } catch (InterruptedException e) { break; }
        }
        try { t.join(2000); } catch (InterruptedException e) { /* ignore */ }

        long elapsed = System.currentTimeMillis() - start;
        result.rounds = state.getRoundNumber();

        Player winner = state.getWinner();
        if (winner != null) {
            result.terminator = "WINNER";
            result.winnerName = winner.getName();
            result.condition = winningCondition(state, winner);
        } else if (done[0] && !t.isAlive()) {
            result.terminator = "ROUND_CAP";
        } else {
            result.terminator = "TIMEOUT";
        }
        result.standings = standings(state, elapsed);
        return result;
    }

    /**
     * Winning-condition classifier (B5-0577 requirement): re-derives which
     * rulebook path crowned the winner from the post-game state. Order mirrors
     * RulesEngine.checkVictory so the label matches the engine's decision.
     */
    static String winningCondition(GameState state, Player winner) {
        // Last-standing: every other player forfeited.
        int remaining = 0;
        for (int i = 0; i < state.getPlayers().size(); i++) {
            Player p = state.getPlayers().get(i);
            if (!p.hasForfeited()) remaining++;
        }
        if (remaining == 1 && !winner.hasForfeited()) return "LAST_STANDING";

        // Agenda-driven win (face-down agendas cannot win per B5-0364).
        AgendaCard agenda = winner.getAgenda();
        if (agenda != null && !agenda.isFaceDown() && agenda.isConditionMet(state, winner)) {
            return "AGENDA_WIN";
        }
        // Standard victory: 20+ influence, strictly greatest (D12).
        if (winner.getInfluence() >= 20) {
            boolean strictlyGreatest = true;
            for (int i = 0; i < state.getPlayers().size(); i++) {
                Player q = state.getPlayers().get(i);
                if (q == winner || q.hasForfeited()) continue;
                if (q.getInfluence() >= winner.getInfluence()) { strictlyGreatest = false; break; }
            }
            if (strictlyGreatest) return "STANDARD_VICTORY";
        }
        // Station victory: condition 2 via the engine's own classifier.
        try {
            Method m = RulesEngine.class.getDeclaredMethod("stationVictory", GameState.class);
            m.setAccessible(true);
            Player stationLeader = (Player) m.invoke(new RulesEngine(), state);
            if (stationLeader == winner) return "STATION_VICTORY";
        } catch (Exception e) {
            return "ENGINE_WINNER (unclassified: " + e.getClass().getSimpleName() + ")";
        }
        return "ENGINE_WINNER (unclassified)";
    }

    /** End-of-run influence standings line for the table. */
    private static String standings(GameState state, long elapsedMs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < state.getPlayers().size(); i++) {
            Player p = state.getPlayers().get(i);
            sb.append(p.getName()).append("=").append(p.getInfluence());
            if (i < state.getPlayers().size() - 1) sb.append("/");
        }
        sb.append(" [").append(elapsedMs / 1000).append("s]");
        return sb.toString();
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) return s + " ";
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    private static String pct(int part, int total) {
        if (total == 0) return "0%";
        return (part * 100 / total) + "%";
    }

    // ── Deck construction (verbatim HeadlessMultiRoundTest helpers) ──────────

    /** Faction deck: starter decks when available, heuristic fallback. */
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
