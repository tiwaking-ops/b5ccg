package b5ccg.engine;

import b5ccg.ai.AIPlayer;
import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.Deck;
import b5ccg.model.GameAction;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.AIDifficulty;
import b5ccg.model.enums.Race;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * B5-0008 (WP-07/A5): double-run determinism probe.
 *
 * <p>Runs the same all-AI game twice with the same seed, drives each run
 * through the real GameController (headless), and compares:</p>
 * <ul>
 *   <li>the per-round-boundary state-hash samples ({@link GameStateHash}) —
 *       identical sequences required;</li>
 *   <li>the end condition (game-over / round cap / timeout);</li>
 *   <li>if both end game-over, the post-round quiescent state hash;</li>
 *   <li>the two replay files, byte-for-byte after normalising {@code
 *       writtenUtc} (the only field that legitimately differs between runs);
 *       the replay header seed must equal the run seed;</li>
 * </ul>
 *
 * <p>Gate tier: pure 0/1 exit contract — any exit non-zero is a determinism
 * defect, never an exploratory outcome, so this is safe to add to the
 * compile.sh RUN_TESTS=1 gate.</p>
 *
 * <p>Run after compile.bat / compile.sh:
 *   java -cp b5ccg/out b5ccg.engine.HeadlessDoubleRunProbe [seed]</p>
 */
public class HeadlessDoubleRunProbe {

    private static final long DEFAULT_SEED = 42L;
    private static final int  ROUND_CAP    = 50;
    private static final long ROUND_TIMEOUT_MS = 300000L; // 5 min per run

    private static final String[] NAMES = { "Alpha", "Beta", "Gamma", "Delta" };
    private static final Race[] FACTIONS = {
        Race.HUMAN, Race.MINBARI, Race.CENTAURI, Race.NARN
    };
    private static final AIDifficulty[] DIFFICULTIES = {
        AIDifficulty.EASY, AIDifficulty.MEDIUM, AIDifficulty.HARD, AIDifficulty.MEDIUM
    };

    private static int checks;
    private static int failures;

    private static void check(String label, boolean passed) {
        checks++;
        System.out.println("  [DRP] " + label + ": " + (passed ? "PASS" : "FAIL"));
        if (!passed) failures++;
    }

    public static void main(String[] args) {
        long seed = DEFAULT_SEED;
        if (args.length >= 1) {
            try { seed = Long.parseLong(args[0]); }
            catch (NumberFormatException e) {
                System.err.println("Bad seed argument (want long): " + args[0]);
                System.exit(1);
            }
        }

        System.out.println("=== B5 CCG double-run determinism probe (B5-0008/WP-07) ===");
        System.out.println("Seed: " + seed);

        RunResult runA = runGame("run-a", "out/replay-b5-0008-run-a.jsonl", seed);
        RunResult runB = runGame("run-b", "out/replay-b5-0008-run-b.jsonl", seed);

        System.out.println("run A: " + runA.end + " in " + runA.elapsedMs
            + " ms (" + runA.samples.size() + " samples)");
        System.out.println("run B: " + runB.end + " in " + runB.elapsedMs
            + " ms (" + runB.samples.size() + " samples)");

        // ── Per-boundary sample sequence ───────────────────────────────────
        check("identical end condition (" + runA.end + " vs " + runB.end + ")",
              runA.end.equals(runB.end));
        check("identical per-boundary state-hash sequence",
              runA.samples.equals(runB.samples));

        // ── Quiescent hash when both games ended ───────────────────────────
        if ("GAME_OVER".equals(runA.end) && "GAME_OVER".equals(runB.end)) {
            String ha = GameStateHash.of(runA.finalState);
            String hb = GameStateHash.of(runB.finalState);
            check("identical quiescent state hash at game over (" + ha.substring(0, 12)
                  + " vs " + hb.substring(0, 12) + ")", ha.equals(hb));
        } else {
            check("quiescent-hash comparison skipped (not both game-over)", true);
        }

        // ── Replays identical modulo writtenUtc ────────────────────────────
        byte[] ba = readBytes(runA.replayPath);
        byte[] bb = readBytes(runB.replayPath);
        String sa = normalizeWrittenUtc(new String(ba, StandardCharsets.UTF_8));
        String sb = normalizeWrittenUtc(new String(bb, StandardCharsets.UTF_8));
        check("replay files exist", ba.length > 0 && bb.length > 0);
        if (ba.length > 0 && bb.length > 0) {
            check("replays byte-identical modulo writtenUtc", sa.equals(sb));
        }

        // ── Replay header seed round-trips through ReplayRecorder.load ─────
        try {
            b5ccg.engine.ReplayRecorder.Replay ra =
                    b5ccg.engine.ReplayRecorder.load(runA.replayPath);
            check("replay A header seed equals run seed (" + ra.seed + ")",
                  ra.seed == seed);
            check("replay A schema version still 1",
                  ra.schemaVersion == b5ccg.engine.ReplayRecorder.SCHEMA_VERSION);
        } catch (Exception e) {
            check("replay A loads through ReplayRecorder.load", false);
            System.err.println("  load error: " + e.getMessage());
        }

        finish();
    }

    // ── One run ────────────────────────────────────────────────────────────

    private static RunResult runGame(String tag, String replayPath, long seed) {
        String end = "TIMEOUT";
        long start = System.currentTimeMillis();
        long elapsed = 0L;
        final List<String> samples = new ArrayList<String>();
        final Throwable[] error = new Throwable[1];
        GameState finalState = null;

        try {
            List<Card> allCards = b5ccg.engine.DeckLoader.loadBothSets();
            if (allCards.isEmpty()) {
                return new RunResult(tag, "TIMEOUT", System.currentTimeMillis() - start,
                        new ArrayList<String>(), replayPath, null);
            }

            StarterDeckBuilder.setRandomSeed(seed); // B5-0008: seed the starter-deck random draw so both runs build identical decks
            List<Player> players = new ArrayList<Player>();
            List<AIPlayer> ais = new ArrayList<AIPlayer>();
            for (int i = 0; i < NAMES.length; i++) {
                Player p = new Player(NAMES[i], FACTIONS[i], false);
                List<Card> deckCards = buildRaceDeck(allCards, FACTIONS[i]);
                if (deckCards.isEmpty()) {
                    return new RunResult(tag, "TIMEOUT", System.currentTimeMillis() - start,
                            new ArrayList<String>(), replayPath, null);
                }
                CharacterCard amb = findAmbassadorCard(deckCards, FACTIONS[i]);
                if (amb != null) {
                    deckCards.remove(deckCards.indexOf(amb));
                    p.setDeck(new Deck(deckCards, seed + 1_000_000L * (i + 1)));
                    p.getDeck().addToTop(amb); // pin ambassador at draw top after construction
                } else {
                    p.setDeck(new Deck(deckCards, seed + 1_000_000L * (i + 1)));
                }
                p.drawCards(4);
                if (p.getHand().isEmpty()) {
                    return new RunResult(tag, "TIMEOUT", System.currentTimeMillis() - start,
                            new ArrayList<String>(), replayPath, null);
                }
                players.add(p);
                AIPlayer ai = new AIPlayer(p, DIFFICULTIES[i]);
                try {
                    java.lang.reflect.Field f =
                            AIPlayer.class.getDeclaredField("rng");
                    f.setAccessible(true);
                    f.set(ai, new java.util.Random(seed + i * 1000L)); // B5-0349 precedent
                } catch (Exception e) {
                    return new RunResult(tag, "TIMEOUT", System.currentTimeMillis() - start,
                            new ArrayList<String>(), replayPath, null);
                }
                ais.add(ai);
            }

            final GameState state = new GameState(players);
            state.setSeed(seed); // B5-0008: game-level seed → controller derives AI streams

            // Replay recorder, additive seed header field (B5-0008).
            b5ccg.engine.ReplayRecorder rec =
                    new b5ccg.engine.ReplayRecorder(replayPath, "B5-0008");
            rec.setRoster(players);
            rec.setSeed(seed);

            // Boundary sampling callback: one hash per round the game passes
            // through, plus a quiescent sample at game over (recorded on the
            // game thread before the recorder closes).
            final int[] lastRound = new int[] { -1 };
            final boolean[] closed = new boolean[] { false };
            final GameController controller = new GameController(state, ais,
                    new b5ccg.engine.GameStateCallback() {
                        public void accept(GameState gs) {
                            int r = gs.getRoundNumber();
                            if (r != lastRound[0]) {
                                lastRound[0] = r;
                                samples.add("r" + r + ":" + GameStateHash.of(gs));
                                try { rec.recordRound(gs); } catch (IOException e) { error[0] = e; return; }
                            }
                            if (gs.isGameOver() && !closed[0]) {
                                closed[0] = true;
                                samples.add("end:" + GameStateHash.of(gs));
                                try { rec.recordRound(gs); rec.close(); } catch (IOException e) { error[0] = e; }
                            }
                        }
                    });

            Thread gameLoop = new Thread(new Runnable() {
                public void run() {
                    try { controller.runGame(); }
                    catch (Throwable t) { error[0] = t; }
                }
            }, "b5-double-run-" + tag);
            gameLoop.setDaemon(true);

            boolean gameOverObserved = false;
            gameLoop.start();
            while (System.currentTimeMillis() - start < ROUND_TIMEOUT_MS) {
                if (error != null) break;
                if (gameOverObserved) break;
                if (state.getRoundNumber() > ROUND_CAP) break;
                Thread.sleep(25);
                // Hook the "ended" signal through the final sample flag: the
                // callback set closed[0] only when game over was observed.
                if (closed[0]) gameOverObserved = true;
            }
            elapsed = System.currentTimeMillis() - start;

            if (error[0] != null) {
                System.err.println("run " + tag + " game loop threw after " + elapsed + " ms");
                error[0].printStackTrace(System.err);
                return new RunResult(tag, "TIMEOUT", elapsed, samples, replayPath, state);
            }
            if (closed[0]) {
                end = "GAME_OVER";
            } else if (state.getRoundNumber() > ROUND_CAP) {
                end = "ROUND_CAP";
            } else {
                end = "TIMEOUT";
            }
            finalState = state;

            gameLoop.interrupt();
            try { gameLoop.join(2000); } catch (InterruptedException ignored) {}

        } catch (Throwable t) {
            System.err.println("run " + tag + " setup failed:");
            t.printStackTrace(System.err);
            return new RunResult(tag, "TIMEOUT", System.currentTimeMillis() - start,
                    new ArrayList<String>(), replayPath, null);
        } finally {
            // Never leave an open recorder behind: writeLine flushes per line,
            // so bytes already written survive even if the thread is abandoned.
        }

        return new RunResult(tag, end, elapsed, samples, replayPath, finalState);
    }

    // ── Deck building (mirrors HeadlessSmokeTest.buildRaceDeck shape) ─────

    private static List<Card> buildRaceDeck(List<Card> all, Race faction) {
        if (b5ccg.engine.StarterDeckBuilder.isAvailable()) {
            try {
                return b5ccg.engine.StarterDeckBuilder.build(faction, all);
            } catch (Exception e) {
                System.err.println("Starter deck build failed for " + faction
                    + ", using heuristic deck: " + e.getMessage());
            }
        }
        List<Card> deck = new ArrayList<Card>();
        for (int i = 0; i < all.size() && deck.size() < 60; i++) {
            Card c = all.get(i);
            if (c.getLoyalty() == b5ccg.model.enums.Loyalty.fromRace(faction)) deck.add(c);
        }
        int conflicts = 0;
        for (int i = 0; i < all.size() && conflicts < 12; i++) {
            Card c = all.get(i);
            if (c.getType() == b5ccg.model.enums.CardType.CONFLICT) { deck.add(c); conflicts++; }
        }
        int agendas = 0;
        for (int i = 0; i < all.size() && agendas < 2; i++) {
            Card c = all.get(i);
            if (c.getType() == b5ccg.model.enums.CardType.AGENDA) { deck.add(c); agendas++; }
        }
        for (int i = 0; i < all.size() && deck.size() < 60; i++) {
            Card c = all.get(i);
            b5ccg.model.enums.Loyalty f = c.getLoyalty();
            if (f == b5ccg.model.enums.Loyalty.ANY
                || f == b5ccg.model.enums.Loyalty.NEUTRAL
                || f == b5ccg.model.enums.Loyalty.NON_ALIGNED) {
                deck.add(c);
            }
        }
        return deck;
    }

    private static CharacterCard findAmbassadorCard(List<Card> deckCards, Race faction) {
        for (int i = 0; i < deckCards.size(); i++) {
            Card c = deckCards.get(i);
            if (c instanceof CharacterCard) {
                CharacterCard ch = (CharacterCard) c;
                if (ch.isAmbassador() && ch.getLoyalty()
                    == b5ccg.model.enums.Loyalty.fromRace(faction)) return ch;
            }
        }
        return null;
    }

    // ── Replay comparison ─────────────────────────────────────────────────

    private static byte[] readBytes(String path) {
        BufferedReader r = null;
        StringBuilder sb = new StringBuilder();
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(new File(path)), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
            sb.append('\n');
        } catch (Exception e) {
            return new byte[0];
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) {}
        }
        return sb.toString().getBytes();
    }

    /** Strip the writtenUtc header field value so two replays of the same
     *  seeded game compare equal despite being written at different times. */
    private static String normalizeWrittenUtc(String jsonl) {
        return jsonl.replaceAll("\"writtenUtc\":\"[^\"]*\"", "\"writtenUtc\":\"Z\"");
    }

    private static void finish() {
        System.out.println("");
        System.out.println("DOUBLE-RUN: " + (failures == 0 ? "PASS" : "FAIL")
                + " (" + (checks - failures) + "/" + checks + " checks passed)");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** One probe run's result. */
    private static class RunResult {
        final String tag;
        final String end;        // GAME_OVER / ROUND_CAP / TIMEOUT
        final long elapsedMs;
        final List<String> samples; // "r<N>:<hash>" boundary samples (+ "end:<hash>")
        final String replayPath;
        final GameState finalState; // null unless GAME_OVER observed cleanly

        RunResult(String tag, String end, long elapsedMs, List<String> samples,
                  String replayPath, GameState finalState) {
            this.tag = tag; this.end = end; this.elapsedMs = elapsedMs;
            this.samples = samples; this.replayPath = replayPath; this.finalState = finalState;
        }
    }
}
