package b5ccg.engine;

import b5ccg.ai.AIPlayer;
import b5ccg.model.*;
import b5ccg.model.enums.*;
import java.util.*;

/**
 * B5-0350 - Option C reporting tiebreak for round-cap stalls.
 *
 * Harness / report layer ONLY: this file adds no game logic and never mutates
 * game state. Design source:
 * docs/proposals/tiebreak-agenda-victory-design-proposal.md (section 2,
 * Option C).
 *
 * When a ROUND CAP is reached with no winner and 2 or more players at
 * 20+ influence, report a fixed, deterministic chain:
 *
 *   step 1: most total fleet Military - Player.conflictTotal(MILITARY), the
 *           engine's own fleet-based Military total (B5-0337 / audit D5:
 *           ready fleets at effective value incl. a seated leader; rotated
 *           fleets contribute 0). The engine keeps no cross-round "committed"
 *           card registry (conflicts resolve within a round), so this is the
 *           only computable fleet-Military total without engine changes -
 *           that interpretation is recorded in DECISIONS.
 *   step 2: most Inner Circle members - Player.getInnerCircle().size().
 *   step 3: most influence gained over the run - current influence minus the
 *           baseline the harness captures at run start.
 *   step 4: declared SHARED victory among the remaining tied players.
 *
 * Precedence: the rulebook engine always goes first. If state.getWinner() is
 * set or RulesEngine.checkVictory(state) returns a player (strict standard
 * lead, agenda win, station condition 2, last standing), the reporter records
 * ENGINE_WINNER and applies NO tiebreak. checkVictory stays rulebook-pure.
 *
 * Trigger gate: with fewer than 2 players at 20+ the report is
 * NOT_APPLICABLE (some other stall, e.g. everyone passing - not the
 * 20-20 stall Option C exists for).
 *
 * Run after compile.bat / compile.sh:
 *   java -cp b5ccg/out b5ccg.engine.HeadlessReportingTiebreakTest
 * Exit 0 only if every synthetic fixture check and the live round-cap
 * integration check pass.
 */
public class HeadlessReportingTiebreakTest {

    /** Outcome kind of a round-cap reporting evaluation. */
    public enum Kind {
        /** The rulebook engine decided (recorded winner / checkVictory). */
        ENGINE_WINNER,
        /** Fewer than 2 players at 20+: not the Option C stall. */
        NOT_APPLICABLE,
        /** Decided uniquely at chain step 1, 2 or 3. */
        SINGLE,
        /** Still tied after step 3: shared victory declared at step 4. */
        SHARED
    }

    /**
     * Immutable outcome of evaluateAtRoundCap (safe to publish across
     * threads - the live round-cap phase captures it on the game thread).
     */
    public static final class TiebreakReport {
        public final Kind kind;
        public final int step;             // 1..4 for SINGLE/SHARED, else -1
        public final List<Player> winners; // unmodifiable; empty only for NOT_APPLICABLE
        public final String detail;

        TiebreakReport(Kind kind, int step, List<Player> winners, String detail) {
            this.kind = kind;
            this.step = step;
            this.winners = Collections.unmodifiableList(new ArrayList<Player>(winners));
            this.detail = detail;
        }

        /** Structural self-consistency of the report (asserted by checks). */
        public boolean isWellFormed() {
            if (kind == null || detail == null || winners == null) return false;
            switch (kind) {
                case ENGINE_WINNER:  return step == -1 && winners.size() == 1;
                case NOT_APPLICABLE: return step == -1 && winners.isEmpty();
                case SINGLE:         return step >= 1 && step <= 3 && winners.size() == 1;
                case SHARED:         return step == 4 && winners.size() >= 2;
                default:             return false;
            }
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(kind);
            if (step != -1) sb.append(" step=").append(step);
            sb.append(" [");
            for (int i = 0; i < winners.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(winners.get(i).getName());
            }
            sb.append("] - ").append(detail);
            return sb.toString();
        }
    }

    // ------------------------------------------------------------------
    // Round-cap evaluation (the report layer proper)
    // ------------------------------------------------------------------

    /**
     * Evaluates the Option C reporting tiebreak for a state observed at the
     * round cap. READ-ONLY: never mutates state or players, never outranks
     * the engine.
     *
     * @param state           the GameState at the round cap
     * @param rules           the engine whose checkVictory gets first say
     * @param startInfluence  baseline captured at run start, parallel to
     *                        state.getPlayers() order: startInfluence[i] is
     *                        players.get(i).getInfluence() before round 1
     * @return an immutable TiebreakReport (never null)
     */
    public static TiebreakReport evaluateAtRoundCap(GameState state, RulesEngine rules,
                                                    int[] startInfluence) {
        if (state == null || rules == null) {
            throw new IllegalArgumentException("state and rules are required");
        }
        List<Player> players = state.getPlayers();
        if (startInfluence == null || startInfluence.length != players.size()) {
            throw new IllegalArgumentException("startInfluence must be parallel to "
                    + "state.getPlayers() (expected length " + players.size() + ", got "
                    + (startInfluence == null ? "null" : "" + startInfluence.length) + ")");
        }

        // 0. Engine first - a rulebook winner ends reporting immediately.
        Player engineWinner = state.getWinner();
        boolean recorded = engineWinner != null;
        if (engineWinner == null) engineWinner = rules.checkVictory(state);
        if (engineWinner != null) {
            return new TiebreakReport(Kind.ENGINE_WINNER, -1,
                    Collections.singletonList(engineWinner),
                    "engine decided (" + (recorded ? "state.getWinner" : "RulesEngine.checkVictory")
                    + "): " + engineWinner.getName() + " - reporting tiebreak not applied");
        }

        // 1. Trigger gate: the Option C stall is "2+ players at 20+, no winner".
        List<Player> contenders = new ArrayList<Player>();
        for (Player p : players) {
            if (!p.hasForfeited() && p.getInfluence() >= 20) contenders.add(p);
        }
        if (contenders.size() < 2) {
            return new TiebreakReport(Kind.NOT_APPLICABLE, -1, new ArrayList<Player>(),
                    "round-cap report: " + contenders.size() + " of " + players.size()
                    + " players at 20+ - Option C stall does not apply");
        }

        // 2. Deterministic chain: fleet Military -> Inner Circle size ->
        //    influence gained -> shared victory. Each step narrows the pool
        //    to the players tied on the maximum value seen so far.
        Metric[] metrics = new Metric[] {
            new Metric() {
                public int valueFor(Player p, int baseline) {
                    return p.conflictTotal(ConflictType.MILITARY);
                }
            },
            new Metric() {
                public int valueFor(Player p, int baseline) {
                    return p.getInnerCircle().size();
                }
            },
            new Metric() {
                public int valueFor(Player p, int baseline) {
                    return p.getInfluence() - baseline;
                }
            }
        };
        String[] labels = { "fleet Military", "Inner Circle size", "influence gained over the run" };

        List<Player> pool = contenders;
        for (int step = 1; step <= 3; step++) {
            pool = keepMax(pool, players, startInfluence, metrics[step - 1]);
            if (pool.size() == 1) {
                Player w = pool.get(0);
                int val = metrics[step - 1].valueFor(
                        w, startInfluence[identityIndexOf(players, w)]);
                return new TiebreakReport(Kind.SINGLE, step, Collections.singletonList(w),
                        "step " + step + "/4 " + labels[step - 1]
                        + " decided: " + w.getName() + "=" + val);
            }
        }
        return new TiebreakReport(Kind.SHARED, 4, pool,
                "step 4/4 shared victory declared (fleet Military, Inner Circle size "
                + "and influence gained all tied): " + joinNames(pool));
    }

    /** One tiebreak metric: a value derived from a player (baseline = run-start influence). */
    private interface Metric {
        int valueFor(Player p, int baseline);
    }

    /** Keeps only the pool players tied on the metric's maximum value. */
    private static List<Player> keepMax(List<Player> pool, List<Player> order,
                                        int[] startInfluence, Metric m) {
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < pool.size(); i++) {
            Player p = pool.get(i);
            int v = m.valueFor(p, startInfluence[identityIndexOf(order, p)]);
            if (v > max) max = v;
        }
        List<Player> out = new ArrayList<Player>();
        for (int i = 0; i < pool.size(); i++) {
            Player p = pool.get(i);
            if (m.valueFor(p, startInfluence[identityIndexOf(order, p)]) == max) out.add(p);
        }
        return out;
    }

    private static int identityIndexOf(List<Player> order, Player p) {
        for (int i = 0; i < order.size(); i++) if (order.get(i) == p) return i;
        return -1;
    }

    private static String joinNames(List<Player> ps) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ps.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(ps.get(i).getName());
        }
        return sb.toString();
    }

    private static boolean samePlayers(List<Player> x, List<Player> y) {
        if (x.size() != y.size()) return false;
        for (int i = 0; i < x.size(); i++) if (x.get(i) != y.get(i)) return false;
        return true;
    }

    // ------------------------------------------------------------------
    // Check bookkeeping (mirrors HeadlessConformanceTest)
    // ------------------------------------------------------------------

    private static int checks = 0;
    private static int failed = 0;

    private static void check(String rule, String label, boolean ok) {
        checks++;
        System.out.println("  [" + rule + "] " + label + ": " + (ok ? "PASS" : "FAIL"));
        if (!ok) failed++;
    }

    // ------------------------------------------------------------------
    // Fixture helpers (mirrors HeadlessConformanceTest)
    // ------------------------------------------------------------------

    /** Fresh player: ambassador 3/3/3/3 seated in the Inner Circle, filler deck. */
    private static Player player(String name, Faction f) {
        Player p = new Player(name, f, false);
        CharacterCard amb = new CharacterCard("amb_" + name, "Amb " + name,
                "CHARACTER_" + f, Rarity.FIXED, f, CardSet.PREMIERE, "x", "text",
                3, 3, 3, 3, true);
        p.setAmbassador(amb);
        p.getInnerCircle().add(amb);
        List<Card> filler = new ArrayList<Card>();
        for (int i = 0; i < 10; i++) {
            filler.add(new EventCard("deck_" + name + "_" + i, "Deck " + i,
                    "EVENT", Rarity.COMMON, Faction.ANY, CardSet.PREMIERE, "x", "text"));
        }
        p.setDeck(new Deck(filler));
        return p;
    }

    private static GameState state(Player... players) {
        List<Player> ps = new ArrayList<Player>();
        for (Player p : players) ps.add(p);
        GameState s = new GameState(ps);
        s.setPhase(GamePhase.ACTION);
        return s;
    }

    private static FleetCard fleet(String id, int military) {
        return new FleetCard(id, id, "FLEET", Rarity.COMMON, Faction.ANY,
                CardSet.PREMIERE, "x", "text", military);
    }

    private static CharacterCard icChar(String id, int leadership) {
        return new CharacterCard(id, id, "CHARACTER_NARN", Rarity.RARE, Faction.NARN,
                CardSet.PREMIERE, "x", "text", 2, 2, 0, leadership, false);
    }

    // ------------------------------------------------------------------
    // Synthetic round-cap fixtures (deterministic, no threads)
    // ------------------------------------------------------------------

    private static void testSyntheticRoundCap() {
        System.out.println("SYN: Option C chain on synthetic round-cap states");
        RulesEngine rules = new RulesEngine();

        // 0. Engine purity baseline: a 20-20 tie with no agenda crowns
        //    nobody (strict standard victory, D12) - the stall Option C targets.
        Player a = player("Alpha", Faction.NARN);
        Player b = player("Beta", Faction.MINBARI);
        GameState st = state(a, b);
        a.gainInfluence(16);              // 4 -> 20
        b.gainInfluence(16);              // 4 -> 20
        int[] base44 = new int[] { 4, 4 };
        check("SYN", "20-20 with no agenda: checkVictory stays null (engine pure)",
                rules.checkVictory(st) == null);

        // 1. Identical boards -> declared shared victory at step 4.
        TiebreakReport r = evaluateAtRoundCap(st, rules, base44);
        check("SYN", "well-formed report", r.isWellFormed());
        check("SYN", "identical 20-20 boards -> SHARED at step 4",
                r.kind == Kind.SHARED && r.step == 4);
        check("SYN", "shared victory lists both players",
                r.winners.size() == 2 && r.winners.contains(a) && r.winners.contains(b));
        check("SYN", "reporter is read-only: engine still null, influence unchanged",
                rules.checkVictory(st) == null
                && a.getInfluence() == 20 && b.getInfluence() == 20);
        TiebreakReport r2 = evaluateAtRoundCap(st, rules, base44);
        check("SYN", "deterministic: second evaluation identical kind/step/winners",
                r2.kind == r.kind && r2.step == r.step && samePlayers(r2.winners, r.winners));

        // 2. Step 1: fleet Military decides outright.
        FleetCard fa = fleet("tb_fleet_a", 5);
        FleetCard fb = fleet("tb_fleet_b", 3);
        a.getFleets().add(fa);
        b.getFleets().add(fb);
        check("SYN", "metric guard: fleet Military 5 vs 3 (fleet-based, D5)",
                a.conflictTotal(ConflictType.MILITARY) == 5
                && b.conflictTotal(ConflictType.MILITARY) == 3);
        r = evaluateAtRoundCap(st, rules, base44);
        check("SYN", "step 1 fleet Military -> SINGLE winner Alpha",
                r.kind == Kind.SINGLE && r.step == 1
                && r.winners.size() == 1 && r.winners.get(0) == a);

        // 3. Step 1 rotation semantics: a rotated (spent) fleet reads 0.
        fa.rotate();
        check("SYN", "rotated fleet contributes 0 Military",
                a.conflictTotal(ConflictType.MILITARY) == 0);
        r = evaluateAtRoundCap(st, rules, base44);
        check("SYN", "ready fleet beats rotated fleet at step 1 -> Beta",
                r.kind == Kind.SINGLE && r.step == 1 && r.winners.get(0) == b);
        a.getFleets().remove(fa);
        b.getFleets().remove(fb);

        // 4. Step 2: Inner Circle size, reached by step-1 narrowing (3 players).
        Player c = player("Gamma", Faction.CENTAURI);
        GameState st3 = state(a, b, c);
        c.gainInfluence(16);              // Gamma 4 -> 20
        a.getFleets().add(fleet("tb_t2_a", 3));
        b.getFleets().add(fleet("tb_t2_b", 3));
        c.getFleets().add(fleet("tb_t2_c", 1));
        CharacterCard b1 = icChar("tb_ic1", 4);
        CharacterCard b2 = icChar("tb_ic2", 3);
        b.getInnerCircle().add(b1);       // Beta Inner Circle: 3
        b.getInnerCircle().add(b2);
        check("SYN", "IC additions do not move the Military metric (D5)",
                b.conflictTotal(ConflictType.MILITARY) == 3);
        r = evaluateAtRoundCap(st3, rules, new int[] { 4, 4, 4 });
        check("SYN", "step 1 narrows 3->2, step 2 Inner Circle size -> SINGLE Beta",
                r.kind == Kind.SINGLE && r.step == 2
                && r.winners.size() == 1 && r.winners.get(0) == b);

        // Clean the boards back to parity for the later steps.
        a.getFleets().clear();
        b.getFleets().clear();
        c.getFleets().clear();
        b.getInnerCircle().remove(b1);
        b.getInnerCircle().remove(b2);

        // 5. Step 3: influence gained over the run (baseline-sensitive).
        //    Boards are tied at steps 1-2 (no fleets, 1 IC member each);
        //    Alpha gained 20-4=16, Beta 20-12=8.
        r = evaluateAtRoundCap(st, rules, new int[] { 4, 12 });
        check("SYN", "step 3 influence gained (16 vs 8) -> SINGLE Alpha",
                r.kind == Kind.SINGLE && r.step == 3
                && r.winners.size() == 1 && r.winners.get(0) == a);

        // 6. Engine precedence: a strict standard lead at the cap.
        a.gainInfluence(1);               // Alpha 21, Beta 20
        check("SYN", "21-20: engine checkVictory crowns the strict leader",
                rules.checkVictory(st) == a);
        r = evaluateAtRoundCap(st, rules, new int[] { 4, 12 });
        check("SYN", "strict leader -> ENGINE_WINNER, no reporting tiebreak",
                r.kind == Kind.ENGINE_WINNER && r.step == -1
                && r.winners.size() == 1 && r.winners.get(0) == a);
        a.loseInfluence(1);               // back to 20

        // 7. Engine precedence: an INFLUENCE_20 agenda win beats the tiebreak
        //    (proposal section 4: agenda win at 20 still wins first).
        a.setAgenda(new AgendaCard("tb_agenda", "Reach Twenty", "AGENDA", Rarity.RARE,
                Faction.NARN, CardSet.PREMIERE, "x", "text", false, "INFLUENCE_20"));
        check("SYN", "agenda win at 20-20: engine checkVictory crowns the agenda owner",
                rules.checkVictory(st) == a);
        r = evaluateAtRoundCap(st, rules, new int[] { 4, 12 });
        check("SYN", "agenda win -> ENGINE_WINNER (agenda precedes any tiebreak)",
                r.kind == Kind.ENGINE_WINNER && r.winners.size() == 1
                && r.winners.get(0) == a);
        a.setAgenda(null);

        // 8. Trigger gate: fewer than 2 players at 20+ is not the Option C stall.
        a.loseInfluence(1);               // 19-19
        b.loseInfluence(1);
        check("SYN", "19-19: engine has no winner", rules.checkVictory(st) == null);
        r = evaluateAtRoundCap(st, rules, new int[] { 4, 12 });
        check("SYN", "under 2 players at 20+ -> NOT_APPLICABLE, no tiebreak applied",
                r.kind == Kind.NOT_APPLICABLE && r.step == -1 && r.winners.isEmpty()
                && r.isWellFormed());
        a.gainInfluence(1);               // restore 20
        b.gainInfluence(1);

        // 9. A single player at 20 is an engine decision (strict standard win),
        //    never a reporting tiebreak.
        b.loseInfluence(1);               // Alpha 20, Beta 19
        check("SYN", "20-19: engine crowns the single 20+ leader",
                rules.checkVictory(st) == a);
        r = evaluateAtRoundCap(st, rules, new int[] { 4, 12 });
        check("SYN", "one player at 20+ -> ENGINE_WINNER",
                r.kind == Kind.ENGINE_WINNER && r.winners.size() == 1
                && r.winners.get(0) == a);
        b.gainInfluence(1);               // restore 20

        // 10. Three-way full tie -> shared victory for all three.
        r = evaluateAtRoundCap(st3, rules, new int[] { 4, 4, 4 });
        check("SYN", "three-way tie -> SHARED at step 4 with all three players",
                r.kind == Kind.SHARED && r.step == 4 && r.winners.size() == 3
                && r.winners.contains(a) && r.winners.contains(b) && r.winners.contains(c));
    }

    // ------------------------------------------------------------------
    // Live round-cap phase: apply the reporter at a real round boundary
    // ------------------------------------------------------------------

    /** Round at which the reporting tiebreak is evaluated in the live run. */
    private static final int ROUND_CAP = 1;
    /** Wall-clock budget for reaching the cap (smoke-test parity). */
    private static final long ROUND_TIMEOUT_MS = 120000L;

    private static final String[] NAMES = { "Alpha", "Beta", "Gamma", "Delta" };
    private static final Faction[] FACTIONS = {
        Faction.HUMAN, Faction.MINBARI, Faction.CENTAURI, Faction.NARN
    };
    private static final AIDifficulty[] DIFFICULTIES = {
        AIDifficulty.EASY, AIDifficulty.MEDIUM, AIDifficulty.HARD, AIDifficulty.MEDIUM
    };

    // Written once on the game thread at the round boundary (or on the main
    // thread after a settled game-over); read from the main thread.
    private static volatile TiebreakReport liveReport = null;
    private static volatile Throwable gameLoopError = null;
    private static final int[] stateUpdates = new int[1];

    private static void testLiveRoundCap() throws Exception {
        System.out.println("LIVE: reporting tiebreak at a real round-cap boundary (cap="
                + ROUND_CAP + ")");
        final RulesEngine rules = new RulesEngine();

        // 1. Decks + all-AI players (smoke-test setup, ambassador pinned).
        List<Card> allCards = DeckLoader.loadBothSets();
        check("LIVE", "DeckLoader loaded cards", !allCards.isEmpty());
        if (allCards.isEmpty()) return;

        List<Player> players = new ArrayList<Player>();
        List<AIPlayer> aiPlayers = new ArrayList<AIPlayer>();
        for (int i = 0; i < NAMES.length; i++) {
            Player p = new Player(NAMES[i], FACTIONS[i], false);
            List<Card> deckCards = buildFactionDeck(allCards, FACTIONS[i]);
            if (deckCards.isEmpty()) {
                check("LIVE", "deck available for faction " + FACTIONS[i], false);
                return;
            }
            p.setDeck(new Deck(deckCards));
            CharacterCard amb = findAmbassadorCard(deckCards, FACTIONS[i]);
            if (amb != null) p.getDeck().addToTop(amb);
            p.drawCards(4);
            players.add(p);
            aiPlayers.add(new AIPlayer(p, DIFFICULTIES[i]));
        }
        // Baseline for chain step 3, captured before the first round.
        final int[] start = new int[players.size()];
        for (int i = 0; i < players.size(); i++) {
            start[i] = players.get(i).getInfluence();
        }

        final GameState state = new GameState(players);
        final GameController controller = new GameController(state, aiPlayers,
            new GameStateCallback() {
                public void accept(GameState gs) {
                    stateUpdates[0]++;
                    // First callback after advanceRound past the cap = the
                    // round boundary: evaluate THERE, on the game thread, so
                    // the report is a clean boundary snapshot.
                    if (liveReport == null && gs.getRoundNumber() > ROUND_CAP) {
                        liveReport = evaluateAtRoundCap(gs, rules, start);
                    }
                }
            });

        Thread gameLoop = new Thread(new Runnable() {
            public void run() {
                try {
                    controller.runGame();
                } catch (Throwable t) {
                    gameLoopError = t;
                }
            }
        }, "b5-tiebreak-game-loop");
        gameLoop.setDaemon(true);

        long startMs = System.currentTimeMillis();
        gameLoop.start();

        boolean settled = false;
        while (System.currentTimeMillis() - startMs < ROUND_TIMEOUT_MS) {
            if (gameLoopError != null || liveReport != null || state.isGameOver()) {
                settled = true;
                break;
            }
            Thread.sleep(25);
        }
        long elapsed = System.currentTimeMillis() - startMs;

        if (gameLoopError != null) {
            System.err.println("      game loop threw after " + elapsed + " ms");
            gameLoopError.printStackTrace(System.err);
            check("LIVE", "GameController.runGame() completed without exception", false);
            return;
        }
        if (!settled) {
            check("LIVE", "round-cap evaluation or game end within " + ROUND_TIMEOUT_MS
                    + " ms (engine appears stuck; phase=" + state.getPhase()
                    + ", round=" + state.getRoundNumber() + ")", false);
            return;
        }
        if (liveReport == null) {
            // Game ended before reaching the cap: evaluate the settled state.
            gameLoop.join(5000L);
            liveReport = evaluateAtRoundCap(state, rules, start);
        }

        TiebreakReport rep = liveReport;
        check("LIVE", "round-cap report captured at/after round " + (ROUND_CAP + 1)
                + " in " + elapsed + " ms", rep != null);
        check("LIVE", "report is well-formed (kind="
                + (rep == null ? "null" : rep.kind.name())
                + ", step=" + (rep == null ? "?" : "" + rep.step) + ")",
                rep != null && rep.isWellFormed());
        check("LIVE", "GameStateCallback fired during the run", stateUpdates[0] > 0);
        if (rep != null) System.out.println("      report: " + rep.detail);
    }

    // ------------------------------------------------------------------
    // Live-phase deck helpers (mirrors HeadlessSmokeTest)
    // ------------------------------------------------------------------

    /** Real Premier starter deck when available; else faction-first heuristic. */
    private static List<Card> buildFactionDeck(List<Card> all, Faction faction) {
        if (StarterDeckBuilder.isAvailable()) {
            try {
                return StarterDeckBuilder.build(faction, all);
            } catch (Exception e) {
                System.err.println("Starter deck build failed for " + faction
                        + ", using heuristic deck: " + e.getMessage());
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

    /** The faction's ambassador card inside a deck list, or null. */
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

    public static void main(String[] args) {
        try {
            System.out.println("=== B5 CCG reporting tiebreak suite (B5-0350, Option C) ===");
            testSyntheticRoundCap();
            testLiveRoundCap();
            System.out.println();
            System.out.println(failed == 0
                    ? "TIEBREAK SUITE PASSED (" + checks + " checks)"
                    : "TIEBREAK SUITE FAILED (" + failed + " of " + checks + " checks)");
        } catch (Throwable t) {
            System.err.println();
            System.err.println("TIEBREAK SUITE FAILED - unexpected exception:");
            t.printStackTrace(System.err);
            failed++;
        }
        System.out.flush();
        System.err.flush();
        System.exit(failed == 0 ? 0 : 1);
    }
}
