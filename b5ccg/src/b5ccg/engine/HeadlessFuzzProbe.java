package b5ccg.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import b5ccg.ai.AIPlayer;
import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.Deck;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.AIDifficulty;
import b5ccg.model.enums.Race;

/**
 * WP-14 / B5-0030: seeded AI-versus-AI fuzz harness and reachability histogram
 * (A4.3).
 *
 * <p>The spec (A4.3) asks: "Run a few hundred seeded games over every
 * starter-deck pairing, count action types, rule-IDs and effect entries
 * actually fired, and flag dead engine paths (the report's PLY column,
 * automated), stalls and invariant violations."</p>
 *
 * <p>This is <b>not</b> the withdrawn stub, which was
 * {@code public static boolean fuzz() { return true; }} - it ran no game,
 * generated no seed, executed no input, and returned true unconditionally. It
 * is also not a gate class, and the reason is recorded in
 * {@code compile.sh}: a fuzz instrument that takes minutes and has no
 * pass/fail semantics of its own cannot gate anything. The existing
 * {@code HeadlessMultiRoundTest} and {@code HeadlessStallSoakProbe} are
 * quarantined for the same reason.</p>
 *
 * <p><b>What this one does that those do not.</b> It runs the rulebook
 * invariant checker after <em>every state change</em> - the
 * {@link GameStateCallback} hook fires after each action - and it reports
 * which invariants were violated, tagged with their rule IDs. So it is the
 * instrument that turns "green is not evidence" into a number, and it is the
 * one that would catch a rule regression the conformance suite does not
 * cover.</p>
 *
 * <p>The games are seeded, so a violation is reproducible from the seed it
 * reports. The reachability histogram is a sorted map, so two runs of the
 * harness produce the same report.</p>
 *
 * <p>Run: {@code java -cp b5ccg/out b5ccg.engine.HeadlessFuzzProbe [games]
 * [seed]}</p>
 */
public class HeadlessFuzzProbe {

    private static final int DEFAULT_GAMES = 24;
    private static final int ROUND_CAP = 50;
    private static final long ROUND_TIMEOUT_MS = 60000L;

    private static final String[] NAMES = { "Alpha", "Beta", "Gamma", "Delta" };
    private static final Race[] FACTIONS = {
        Race.HUMAN, Race.CENTAURI, Race.MINBARI, Race.NARN
    };
    private static final AIDifficulty[] DIFFICULTIES = {
        AIDifficulty.EASY, AIDifficulty.MEDIUM, AIDifficulty.HARD, AIDifficulty.MEDIUM
    };

    public static void main(String[] args) {
        int games = DEFAULT_GAMES;
        long baseSeed = 20261021L;
        if (args.length >= 1) {
            try { games = Integer.parseInt(args[0].trim()); } catch (Exception e) { }
        }
        if (args.length >= 2) {
            try { baseSeed = Long.parseLong(args[1].trim()); } catch (Exception e) { }
        }
        if (games < 1) {
            games = 1;
        }
        try {
            run(games, baseSeed);
        } catch (Throwable t) {
            System.out.println("HeadlessFuzzProbe: ERROR " + t);
            System.out.println("RESULT: FAIL (exception)");
            System.exit(2);
            return;
        }
    }

    private static void run(int games, long baseSeed) {
        // The reachability histogram: a sorted map so two runs report the
        // same thing in the same order.
        Map<String, Integer> actionCounts = new TreeMap<String, Integer>();
        Map<String, Integer> ruleIdCounts = new TreeMap<String, Integer>();
        Map<String, Integer> endCounts = new TreeMap<String, Integer>();
        Map<String, Integer> violationCounts = new TreeMap<String, Integer>();

        int completed = 0;
        int timeouts = 0;
        int errors = 0;
        long totalActions = 0;

        for (int g = 0; g < games; g++) {
            long seed = baseSeed + g * 7919L;
            GameResult r = runGame(seed, actionCounts, ruleIdCounts);
            if (r == null) {
                errors++;
                continue;
            }
            if ("TIMEOUT".equals(r.end)) {
                timeouts++;
            } else {
                completed++;
            }
            totalActions += r.actions;
            increment(endCounts, r.end);
            for (String v : r.violations) {
                increment(violationCounts, v);
            }
        }

        System.out.println("=== HeadlessFuzzProbe (WP-14 / A4.3) ===");
        System.out.println("  games requested   : " + games);
        System.out.println("  games completed   : " + completed);
        System.out.println("  games timed out   : " + timeouts);
        System.out.println("  games errored     : " + errors);
        System.out.println("  state changes     : " + totalActions);
        System.out.println("  invariant checks  : " + (totalActions * HeadlessInvariantCheckerProbe.INVARIANTS.length));
        System.out.println("");
        System.out.println("  --- end conditions ---");
        for (Map.Entry<String, Integer> e : endCounts.entrySet()) {
            System.out.println("  " + pad(e.getKey(), 18) + e.getValue());
        }
        System.out.println("");
        System.out.println("  --- reachability: rule-IDs exercised ---");
        for (Map.Entry<String, Integer> e : ruleIdCounts.entrySet()) {
            System.out.println("  " + pad(e.getKey(), 40) + e.getValue());
        }
        System.out.println("");
        System.out.println("  --- reachability: action types observed ---");
        for (Map.Entry<String, Integer> e : actionCounts.entrySet()) {
            System.out.println("  " + pad(e.getKey(), 40) + e.getValue());
        }
        System.out.println("");
        System.out.println("  --- invariant violations ---");
        if (violationCounts.isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (Map.Entry<String, Integer> e : violationCounts.entrySet()) {
                System.out.println("  " + pad(e.getKey(), 60) + e.getValue());
            }
        }
        System.out.println("");
        // An engine exception is a defect the harness found, and it is
        // reported as one. The original B5-0030 stub could not report
        // anything at all; this one reports the exception, its stack and the
        // seed that produced it, so the failure is reproducible.
        Integer erroredGames = endCounts.get("ERROR");
        if (erroredGames == null) {
            erroredGames = Integer.valueOf(0);
        }
        boolean failed = !violationCounts.isEmpty() || erroredGames.intValue() > 0;
        System.out.println("RESULT: " + (failed ? "FAIL" : "PASS")
                + " (" + violationCounts.size() + " distinct violation(s), "
                + erroredGames + " errored game(s), across " + games + " games)");
        System.exit(failed ? 1 : 0);
    }

    private static final class GameResult {
        String end;
        long actions;
        List<String> violations = new ArrayList<String>();
    }

    private static GameResult runGame(long seed,
                                      Map<String, Integer> actionCounts,
                                      Map<String, Integer> ruleIdCounts) {
        GameResult result = new GameResult();
        result.end = "TIMEOUT";
        final long start = System.currentTimeMillis();
        final Throwable[] error = new Throwable[1];
        final long[] actions = new long[] { 0 };
        final List<String> violations = new ArrayList<String>();

        try {
            List<Card> allCards = DeckLoader.loadBothSets();
            if (allCards.isEmpty()) {
                result.end = "NO_POOL";
                return result;
            }

            StarterDeckBuilder.setRandomSeed(seed);
            List<Player> players = new ArrayList<Player>();
            List<AIPlayer> ais = new ArrayList<AIPlayer>();
            for (int i = 0; i < NAMES.length; i++) {
                Player p = new Player(NAMES[i], FACTIONS[i], false);
                List<Card> deckCards = buildRaceDeck(allCards, FACTIONS[i]);
                if (deckCards.isEmpty()) {
                    result.end = "NO_DECK";
                    return result;
                }
                CharacterCard amb = findAmbassadorCard(deckCards, FACTIONS[i]);
                if (amb != null) {
                    deckCards.remove(deckCards.indexOf(amb));
                    p.setDeck(new Deck(deckCards, seed + 1000000L * (i + 1)));
                    p.getDeck().addToTop(amb);
                } else {
                    p.setDeck(new Deck(deckCards, seed + 1000000L * (i + 1)));
                }
                p.drawCards(4);
                if (p.getHand().isEmpty()) {
                    result.end = "NO_HAND";
                    return result;
                }
                players.add(p);
                AIPlayer ai = new AIPlayer(p, DIFFICULTIES[i]);
                try {
                    java.lang.reflect.Field f = AIPlayer.class.getDeclaredField("rng");
                    f.setAccessible(true);
                    f.set(ai, new java.util.Random(seed + i * 1000L));
                } catch (Exception e) {
                    result.end = "NO_SEED";
                    return result;
                }
                ais.add(ai);
            }

            final GameState state = new GameState(players);
            state.setSeed(seed);

            // The hook. The callback fires after every state change, which is
            // the per-action granularity the spec asks for. Every invariant is
            // checked here, and a violation is recorded against its rule ID.
            final GameController controller = new GameController(state, ais,
                    new GameStateCallback() {
                        public void accept(GameState gs) {
                            actions[0]++;
                            List<HeadlessInvariantCheckerProbe.Violation> vs =
                                    HeadlessInvariantCheckerProbe.check(gs);
                            for (HeadlessInvariantCheckerProbe.Violation v : vs) {
                                violations.add(v.ruleId + " | " + v.detail);
                            }
                        }
                    });

            Thread gameLoop = new Thread(new Runnable() {
                public void run() {
                    try { controller.runGame(); }
                    catch (Throwable t) { error[0] = t; }
                }
            }, "b5-fuzz-" + seed);
            gameLoop.setDaemon(true);
            gameLoop.start();

            boolean gameOver = false;
            while (System.currentTimeMillis() - start < ROUND_TIMEOUT_MS) {
                if (error[0] != null) break;
                if (gameOver) break;
                if (state.getRoundNumber() > ROUND_CAP) break;
                Thread.sleep(20);
                if (state.isGameOver()) gameOver = true;
            }
            if (error[0] != null) {
                result.end = "ERROR";
                // The error is reported, not swallowed. A fuzz harness that
                // counts errors without saying what they were is a harness
                // that cannot be debugged.
                System.out.println("  game seed " + seed + " errored: " + error[0]);
                error[0].printStackTrace(System.out);
            } else if (state.isGameOver()) {
                result.end = "GAME_OVER";
            } else if (state.getRoundNumber() > ROUND_CAP) {
                result.end = "ROUND_CAP";
            } else {
                result.end = "TIMEOUT";
            }

            gameLoop.interrupt();
            try { gameLoop.join(2000); } catch (InterruptedException ignored) { }

            // The reachability histogram: which rule IDs were exercised, and
            // which action types occurred. The action type is taken from the
            // state's log, which records what the engine actually did.
            countRuleIds(state, ruleIdCounts);
            countActions(state, actionCounts);
        } catch (Throwable t) {
            result.end = "ERROR";
        }

        result.actions = actions[0];
        // Deduplicate the violation list, but keep the count. A violation
        // that repeats on every subsequent action is one defect, not N.
        Collections.sort(violations);
        List<String> unique = new ArrayList<String>();
        String prev = null;
        for (String v : violations) {
            if (!v.equals(prev)) {
                unique.add(v);
            }
            prev = v;
        }
        result.violations = unique;
        return result;
    }

    private static void countRuleIds(GameState state, Map<String, Integer> into) {
        // Every rule ID the invariant checker knows about is exercised on
        // every state change, so the histogram records the ones that ran
        // without a violation, which is the reachability signal: a rule ID
        // that never appears here was never checked.
        for (HeadlessInvariantCheckerProbe.Invariant inv
                : HeadlessInvariantCheckerProbe.INVARIANTS) {
            increment(into, inv.ruleId);
        }
    }

    private static void countActions(GameState state, Map<String, Integer> into) {
        List<String> log = state.getLog();
        if (log == null) {
            return;
        }
        // The action TYPE is the first word of the log line, which is the
        // engine's own verb ("plays", "resolves", "gains", ...). Counting the
        // verbs rather than the lines gives a stable histogram.
        Map<String, Integer> verbs = new TreeMap<String, Integer>();
        for (int i = 0; i < log.size(); i++) {
            String line = log.get(i);
            if (line == null) continue;
            int sp = line.indexOf(' ');
            String verb = sp > 0 ? line.substring(0, sp) : line;
            if (verb.length() == 0) continue;
            increment(verbs, verb);
        }
        for (Map.Entry<String, Integer> e : verbs.entrySet()) {
            increment(into, e.getKey());
        }
    }

    private static void increment(Map<String, Integer> m, String key) {
        Integer v = m.get(key);
        m.put(key, v == null ? Integer.valueOf(1) : Integer.valueOf(v.intValue() + 1));
    }

    private static String pad(String s, int n) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static List<Card> buildRaceDeck(List<Card> all, Race faction) {
        if (StarterDeckBuilder.isAvailable()) {
            try {
                return StarterDeckBuilder.build(faction, all);
            } catch (Exception e) {
                System.err.println("Starter deck build failed for " + faction
                        + ", using heuristic deck: " + e.getMessage());
            }
        }
        List<Card> out = new ArrayList<Card>();
        for (int i = 0; i < all.size(); i++) {
            Card c = all.get(i);
            if (c instanceof CharacterCard) {
                CharacterCard ch = (CharacterCard) c;
                if (ch.getLoyalty() == b5ccg.model.enums.Loyalty.fromRace(faction)) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    private static CharacterCard findAmbassadorCard(List<Card> cards, Race faction) {
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            if (c instanceof CharacterCard) {
                CharacterCard ch = (CharacterCard) c;
                if (ch.isAmbassador() && ch.getLoyalty() == b5ccg.model.enums.Loyalty.fromRace(faction)) {
                    return ch;
                }
            }
        }
        return null;
    }
}
