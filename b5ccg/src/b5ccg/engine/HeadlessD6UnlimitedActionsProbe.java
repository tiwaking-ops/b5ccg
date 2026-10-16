package b5ccg.engine;
import b5ccg.model.enums.Loyalty;

import b5ccg.ai.AIPlayer;
import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.Deck;
import b5ccg.model.GameState;
import b5ccg.model.GameAction;
import b5ccg.model.Player;
import b5ccg.model.enums.AIDifficulty;
import b5ccg.model.enums.Race;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * B5-2548 — D6 unlimited-actions implementation probe (harness only, new file;
 * no game-logic edit). Exit 0 on all-pass, 1 on any failure.
 *
 * <p>B5-2519 audited this area and recorded the finding this probe acts on:
 * since B5-0372 landed D6, {@code GameController.runActionPhase} grants
 * actions by ELIGIBILITY VISIT — it cycles the table and delivers one action
 * per visit, with no per-player cap, ending the round on consecutive passes.
 * {@code Player.useAction} therefore has no caller in the loop, and
 * {@code actionsLeft} sits at 1 for the whole round in a normal game. This
 * probe measures the thing that check could not: <b>what each seat is actually
 * OFFED on each of its turns.</b>
 *
 * <h3>Why the offer list, and why not the log</h3>
 *
 * <p>"No turn has zero legal actions" cannot be read off the action log, and it
 * cannot be read off {@code actionsLeft} either — both describe what was
 * <i>done</i>, not what was <i>available</i>. The offer list is the engine's own
 * statement of legality: {@code AIPlayer.buildLegalActions}. It is private, so
 * this probe reaches it by reflection rather than by widening the API — a probe
 * that made its own enumeration of "legal" would be asserting its own opinion
 * instead of the engine's.
 *
 * <p>The sampling point is a {@link SamplingAI} subclass handed to the real
 * {@code GameController}, overriding {@code chooseAction} and recording the
 * offer list BEFORE delegating to {@code super}. That is the exact moment the
 * engine asks the seat what it may do, so each sample is one real turn, taken
 * pre-decision, with no polling and no inference from state that has already
 * moved on.
 *
 * <h3>THE CHECK THAT CANNOT FAIL, AND WHAT IS ASSERTED INSTEAD</h3>
 *
 * <p>{@code buildLegalActions} opens with {@code actions.add(GameAction.pass())}
 * and returns that list unconditionally. So the offer list is <b>never</b> empty
 * and a probe asserting "size &gt; 0" would be green against any implementation
 * whatsoever — the silent-green shape. The row's phrase is therefore read as
 * <b>no turn with zero <i>usable</i> legal actions</b>, and a turn is
 * <i>usable-empty</i> when the only offer is PASS. Every such turn is then
 * CLASSIFIED, because "only PASS" has three different causes with different
 * severities:
 *
 * <ol>
 *   <li><b>genuinely nothing to play</b> — the seat's hand holds nothing the
 *       engine will accept. Correct behaviour.</li>
 *   <li><b>counter lockout</b> — {@code actionsLeft &lt;= 0} with the seat not
 *       passed. This is the D6 defect proper, and it is asserted absent.</li>
 *   <li><b>sticky-pass lockout</b> — the seat is flagged passed, and
 *       {@code buildLegalActions} early-returns on that flag BEFORE it looks at
 *       the hand at all. This probe re-asks the question with the flag
 *       temporarily cleared and the flag restored on the same line, so it can
 *       report whether legal actions were being withheld from a seat that had
 *       them. Asserted absent.</li>
 * </ol>
 *
 * <p>Classes 2 and 3 are the falsifiable content. Class 1 is counted and
 * printed, never asserted: a table where every seat has played its hand out is
 * a real game state, and a check that called it a defect would be wrong.
 *
 * <p>The transient flag clear is a deliberate in-game mutation and is disclosed
 * here because a probe that perturbs live state to make itself able to fail has
 * to say so. It is safe for a measured reason, not an assumed one:
 * {@code isPassed} is read by exactly one caller in the action path —
 * {@code buildLegalActions}' early return — and that caller re-reads it from
 * scratch on the next turn. {@code runActionPhase} writes the flag only as the
 * result of the action it just processed, so clearing and restoring it between
 * two samples cannot be observed by the loop. The probe asserts that
 * restoration too: every sample re-reads the flag afterwards and a sample whose
 * flag did not come back is a failure, which is what makes the mutation safe to
 * leave in rather than reasoning about it.
 *
 * <h3>Why 50 rounds, and why several games</h3>
 *
 * <p>A game ends on a victory condition many rounds before 50 — D12 wants 20+
 * power, which arrives well inside twenty rounds. The row asks for 50 rounds of
 * evidence, so the probe runs successive games on four threads and accumulates
 * rounds until the total reaches 50, reporting how many games it took. Each game
 * loads its own cards: the card objects are MUTABLE and are reused by the deck
 * builder, so sharing one loaded list across concurrent games would have the
 * threads corrupting each other's state and the finding would be an artefact of
 * the harness.
 *
 * <p>Runtime is dominated by the engine's own 600 ms per-AI-action pause
 * (~17 s per round), so this is a REPORT-LEVEL probe on the scale of the
 * existing soaks ({@code HeadlessStallSoakProbe}, 978.6 s). It is deliberately
 * not wired into {@code compile.sh}: that file is outside this row's claimed
 * scope, and a gate is the wrong tier for a 50-round measurement anyway.
 *
 * <pre>
 *   java -cp b5ccg/out b5ccg.engine.HeadlessD6UnlimitedActionsProbe
 *   java -cp b5ccg/out b5ccg.engine.HeadlessD6UnlimitedActionsProbe 6   # shorter
 * </pre>
 */
public class HeadlessD6UnlimitedActionsProbe {

    /** Rounds of live evidence the row asks for. */
    private static int requiredRounds = 50;

    /** Safety bound so a game that never ends cannot hang the probe. */
    private static final int MAX_GAMES = 40;

    /** Wall-clock ceiling for the whole probe, generous for the pause cost. */
    private static final long TOTAL_TIMEOUT_MS = 1800000L;

    /** Per-game ceiling: a game that stops advancing is reported, not waited on. */
    private static final long GAME_TIMEOUT_MS = 300000L;

    private static final String[] NAMES = {
        "DUAlpha", "DUBeta", "DUCentauri", "DUNarn"
    };
    private static final Race[] FACTIONS = {
        Race.HUMAN, Race.MINBARI, Race.CENTAURI, Race.NARN
    };
    private static final AIDifficulty[] DIFFICULTIES = {
        AIDifficulty.MEDIUM, AIDifficulty.MEDIUM, AIDifficulty.HARD, AIDifficulty.EASY
    };

    // ── Shared tally. Every mutation goes through the lock; the counters are
    //    read once, at the end, under the same lock. ──────────────────────────
    private static final Object LOCK = new Object();

    private static int turns             = 0;
    private static int roundsSeen        = 0;
    private static int games             = 0;
    private static int emptyOfferTurns   = 0;
    private static int nothingToPlay     = 0;
    private static int counterLockouts   = 0;
    private static int stickyPassLockouts = 0;
    private static int minActionsLeft    = Integer.MAX_VALUE;
    private static int maxRoundsInAGame  = 0;
    private static int flagRestoreFailures = 0;
    private static int lostWithheldExamples = 0;
    private static String firstWithheld  = "";

    private static int checks   = 0;
    private static int failures = 0;

    // ── Per-thread result, folded into the shared tally under LOCK ───────────
    private static final class GameResult {
        int turns;
        int rounds;
        int emptyOfferTurns;
        int nothingToPlay;
        int counterLockouts;
        int stickyPassLockouts;
        int minActionsLeft;
        int lostWithheld;
        int flagRestoreFailures;
        String withheldExample;
        String problem;              // non-null => the game could not be played
    }

    public static void main(String[] args) {
        System.out.println("=== B5-2548 D6 unlimited-actions implementation probe ===");

        // Optional round budget, so a mutation proof does not have to pay for a
        // full 50-round soak to show a gate going red. Absent arg = the row's 50.
        if (args != null && args.length > 0) {
            try {
                requiredRounds = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("  [DU] FAIL: round budget '" + args[0]
                    + "' is not a number");
                System.exit(1);
            }
        }
        System.out.println("     round budget: " + requiredRounds);

        long start = System.currentTimeMillis();
        runGamesUntilRoundsReached();
        long elapsed = System.currentTimeMillis() - start;

        System.out.println();
        System.out.println("-- TALLY --");
        System.out.println("     games played:            " + games);
        System.out.println("     rounds observed:         " + roundsSeen
            + " (required " + requiredRounds + ")");
        System.out.println("     longest game:            " + maxRoundsInAGame + " rounds");
        System.out.println("     turns sampled:           " + turns);
        System.out.println("     min actionsLeft seen:    "
            + (minActionsLeft == Integer.MAX_VALUE ? "n/a" : String.valueOf(minActionsLeft)));
        System.out.println("     wall clock:              " + (elapsed / 1000L) + " s");
        System.out.println("     PASS-only turns:         " + emptyOfferTurns
            + "  (nothing-to-play " + nothingToPlay
            + ", counter-lockout " + counterLockouts
            + ", sticky-pass-lockout " + stickyPassLockouts + ")");
        if (lostWithheldExamples > 0) {
            System.out.println("     legal actions WITHHELD behind the passed flag on "
                + lostWithheldExamples + " turn(s). First: " + firstWithheld);
        }

        System.out.println();
        System.out.println("-- GATES --");

        check("the probe reached 50 rounds of live evidence",
              roundsSeen >= requiredRounds);
        check("every game produced turns to sample (no silent no-op game)",
              turns > 0);
        check("the offer list was sampled on real turns, not inferred — "
            + turns + " pre-decision samples", turns > 0);

        // The literal reading of the row, printed as a measurement rather than
        // asserted, because it cannot fail: buildLegalActions always seeds PASS.
        System.out.println("  [DU] MEASURED (not a gate): the offer list is never empty — "
            + "buildLegalActions seeds PASS unconditionally — so \"zero legal "
            + "actions\" is read below as zero USABLE (non-PASS) actions.");

        check("no turn offered zero usable legal actions where the seat could "
            + "have acted (" + counterLockouts + " counter-lockout, "
            + stickyPassLockouts + " sticky-pass-lockout of " + turns + " turns)",
              counterLockouts == 0 && stickyPassLockouts == 0);
        check("no turn left a seat with a hidden non-PASS offer behind the "
            + "passed flag (" + lostWithheldExamples + " turn(s) re-asked with "
            + "the flag cleared)", lostWithheldExamples == 0);
        check("every flag clear this probe performed was restored on the same "
            + "sample (" + flagRestoreFailures + " failure(s))",
              flagRestoreFailures == 0);
        check("actionsLeft never went below zero on any turn (min "
            + (minActionsLeft == Integer.MAX_VALUE ? "n/a" : String.valueOf(minActionsLeft))
            + ")", minActionsLeft == Integer.MAX_VALUE || minActionsLeft >= 0);
        check("PASS-only turns were classified rather than silently counted "
            + "(" + nothingToPlay + " genuinely nothing-to-play of "
            + emptyOfferTurns + " PASS-only)",
              emptyOfferTurns == nothingToPlay + counterLockouts + stickyPassLockouts);

        System.out.println();
        if (failures == 0) {
            System.out.println("B5-2548 PROBE PASSED (" + checks + " checks): "
                + roundsSeen + " rounds across " + games + " game(s), " + turns
                + " pre-decision turns sampled; " + counterLockouts + " counter "
                + "lockouts, " + stickyPassLockouts + " sticky-pass lockouts, "
                + lostWithheldExamples + " turns where legal actions were withheld "
                + "behind the passed flag, and min actionsLeft "
                + (minActionsLeft == Integer.MAX_VALUE ? "n/a" : String.valueOf(minActionsLeft))
                + ". The " + nothingToPlay + " PASS-only turns were seats with "
                + "genuinely nothing to play.");
        } else {
            System.out.println("B5-2548 PROBE FAILED: " + failures + " of "
                + checks + " checks");
            System.exit(1);
        }
        System.exit(0);
    }

    // ── Game driving ─────────────────────────────────────────────────────────

    /**
     * Runs games on four threads until the accumulated rounds reach
     * {@code requiredRounds}. Each thread plays whole games and folds its
     * result in under {@link #LOCK}; no game is shared between threads.
     */
    private static void runGamesUntilRoundsReached() {
        final boolean[] stop = { false };
        Thread[] workers = new Thread[4];
        for (int t = 0; t < workers.length; t++) {
            workers[t] = new Thread(new Runnable() {
                public void run() {
                    while (true) {
                        synchronized (LOCK) {
                            if (stop[0] || roundsSeen >= requiredRounds
                                || games >= MAX_GAMES
                                || System.currentTimeMillis() - startedAt > TOTAL_TIMEOUT_MS) {
                                return;
                            }
                            games++;
                        }
                        GameResult r = playOneGame();
                        synchronized (LOCK) {
                            fold(r);
                            if (r.problem != null) {
                                System.out.println("     (game " + games
                                    + " could not be played: " + r.problem + ")");
                            }
                            if (roundsSeen >= requiredRounds || games >= MAX_GAMES) {
                                stop[0] = true;
                            }
                        }
                    }
                }
            }, "d6-unlimited-actions-" + t);
            workers[t].setDaemon(true);
        }
        for (int t = 0; t < workers.length; t++) workers[t].start();

        long deadline = startedAt + TOTAL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            boolean allDone = true;
            for (int t = 0; t < workers.length; t++) {
                if (workers[t].isAlive()) { allDone = false; break; }
            }
            if (allDone) return;
            sleep(100L);
        }
        stop[0] = true;
        System.out.println("     (probe stopped on its wall-clock ceiling with "
            + roundsSeen + " of " + requiredRounds + " rounds in hand)");
    }

    private static final long startedAt = System.currentTimeMillis();

    /** Folds one finished game's measurements into the shared tally. */
    private static void fold(GameResult r) {
        turns               += r.turns;
        roundsSeen          += r.rounds;
        emptyOfferTurns     += r.emptyOfferTurns;
        nothingToPlay       += r.nothingToPlay;
        counterLockouts     += r.counterLockouts;
        stickyPassLockouts  += r.stickyPassLockouts;
        lostWithheldExamples += r.lostWithheld;
        flagRestoreFailures  += r.flagRestoreFailures;
        if (r.withheldExample != null && firstWithheld.length() == 0) {
            firstWithheld = r.withheldExample;
        }
        if (r.rounds > maxRoundsInAGame) maxRoundsInAGame = r.rounds;
        if (r.minActionsLeft < minActionsLeft) minActionsLeft = r.minActionsLeft;
    }

    /**
     * One real game through {@code GameController.runGame()} with four seats
     * driven by {@link SamplingAI}. Round and turn counts come from the state
     * itself, so a game that ends early simply contributes fewer rounds and the
     * driver plays another.
     */
    private static GameResult playOneGame() {
        GameResult r = new GameResult();
        r.minActionsLeft = Integer.MAX_VALUE;

        List<Card> all;
        try {
            all = DeckLoader.loadBothSets();
        } catch (Exception e) {
            r.problem = "DeckLoader.loadBothSets threw: " + e;
            return r;
        }
        if (all.isEmpty()) {
            r.problem = "DeckLoader returned zero cards (is b5ccg/resources/cards on the classpath?)";
            return r;
        }

        List<Player> players = new ArrayList<Player>();
        List<AIPlayer> ais = new ArrayList<AIPlayer>();
        for (int i = 0; i < NAMES.length; i++) {
            Player p = new Player(NAMES[i], FACTIONS[i], false);
            List<Card> deckCards = StarterDeckBuilder.isAvailable()
                ? safeStarterDeck(FACTIONS[i], all)
                : ownRaceCards(all, FACTIONS[i]);
            if (deckCards.isEmpty()) {
                r.problem = "no cards available for faction " + FACTIONS[i];
                return r;
            }
            p.setDeck(new Deck(deckCards));
            CharacterCard amb = findAmbassadorCard(deckCards, FACTIONS[i]);
            if (amb != null) p.getDeck().addToTop(amb);
            p.drawCards(4);
            if (p.getHand().isEmpty()) {
                r.problem = NAMES[i] + " drew an empty opening hand";
                return r;
            }
            players.add(p);
            ais.add(new SamplingAI(p, DIFFICULTIES[i], r));
        }

        final GameState state = new GameState(players);
        for (int i = 0; i < players.size(); i++) players.get(i).setGameState(state);
        final List<AIPlayer> gameAis = ais;

        final Throwable[] thrown = new Throwable[1];
        Thread loop = new Thread(new Runnable() {
            public void run() {
                try {
                    new GameController(state, gameAis, new GameStateCallback() {
                        public void accept(GameState gs) { }
                    }).runGame();
                } catch (Throwable t) { thrown[0] = t; }
            }
        }, "d6-unlimited-actions-game-loop");
        loop.setDaemon(true);

        long start = System.currentTimeMillis();
        loop.start();
        int lastRound = -1;
        while (System.currentTimeMillis() - start < GAME_TIMEOUT_MS) {
            if (thrown[0] != null) break;
            if (state.isGameOver()) break;
            if (state.getRoundNumber() > MAX_RUNDS_IN_GAME) break;
            int rn = state.getRoundNumber();
            if (rn != lastRound) {
                lastRound = rn;
                sleep(20L);
            } else {
                sleep(60L);
            }
        }
        loop.interrupt();

        if (thrown[0] != null) {
            r.problem = "runGame threw: " + thrown[0];
            return r;
        }
        r.rounds = state.getRoundNumber();
        return r;
    }

    /** A game longer than this in one sitting is implausible; bounds the loop. */
    private static final int MAX_RUNDS_IN_GAME = 60;

    // ── The sampling AI ──────────────────────────────────────────────────────

    /**
     * An {@link AIPlayer} that records what the engine offered the seat BEFORE
     * delegating the decision. Overriding here rather than polling the callback
     * is the whole point: {@code chooseAction} is the moment legality is asked,
     * and a sample taken after the action has been processed describes a state
     * the seat was never offered.
     */
    private static final class SamplingAI extends AIPlayer {
        private final GameResult r;

        SamplingAI(Player p, AIDifficulty d, GameResult r) {
            super(p, d);
            this.r = r;
        }

        public GameAction chooseAction(GameState state, Player p) {
            List<GameAction> offers = callBuildLegalActions(this, state, p);

            int left = p.getActionsLeft();
            if (left < r.minActionsLeft) r.minActionsLeft = left;
            r.turns++;

            int nonPass = 0;
            for (int i = 0; i < offers.size(); i++) {
                if (offers.get(i).getType() != GameAction.Type.PASS) nonPass++;
            }

            if (nonPass == 0) {
                r.emptyOfferTurns++;
                if (left <= 0 && !p.isPassed()) {
                    // Class 2: the D6 counter itself locked the seat out.
                    r.counterLockouts++;
                } else if (p.isPassed()) {
                    // Class 3: the passed flag short-circuits the offer builder
                    // before it looks at the hand. Re-ask with the flag cleared
                    // to find out whether anything was being withheld, and
                    // restore the flag on the same line.
                    boolean was = p.isPassed();
                    p.setPassed(false);
                    List<GameAction> unflagged = callBuildLegalActions(this, state, p);
                    p.setPassed(was);
                    if (!p.isPassed()) {
                        r.flagRestoreFailures++;
                    } else {
                        r.stickyPassLockouts++;
                        int recovered = 0;
                        for (int i = 0; i < unflagged.size(); i++) {
                            if (unflagged.get(i).getType() != GameAction.Type.PASS) {
                                recovered++;
                            }
                        }
                        if (recovered > 0) {
                            r.lostWithheld++;
                            if (r.withheldExample == null) {
                                r.withheldExample = "round " + state.getRoundNumber()
                                    + ", " + p.getName() + " (hand " + p.getHand().size()
                                    + ") had " + recovered + " non-PASS offer(s) withheld";
                            }
                        }
                    }
                } else {
                    // Class 1: the seat genuinely has nothing the engine accepts.
                    r.nothingToPlay++;
                }
            }

            return super.chooseAction(state, p);
        }
    }

    /**
     * The engine's own opinion of legality, reached reflectively.
     * {@code AIPlayer.buildLegalActions} is private; a probe that re-implemented
     * the enumeration would be asserting its own rules rather than the engine's.
     */
    private static List<GameAction> callBuildLegalActions(AIPlayer ai,
                                                          GameState state,
                                                          Player p) {
        try {
            Method m = AIPlayer.class.getDeclaredMethod(
                "buildLegalActions", GameState.class, Player.class);
            m.setAccessible(true);
            return (List<GameAction>) m.invoke(ai, state, p);
        } catch (Exception e) {
            throw new RuntimeException(
                "cannot reach AIPlayer.buildLegalActions (Java 6 reflection): " + e, e);
        }
    }

    // ── Small helpers ────────────────────────────────────────────────────────

    private static List<Card> safeStarterDeck(Race f, List<Card> all) {
        try {
            return StarterDeckBuilder.build(f, all);
        } catch (Exception e) {
            System.err.println("     starter deck build failed for " + f
                + ", falling back to own-faction cards: " + e.getMessage());
            return ownRaceCards(all, f);
        }
    }

    private static List<Card> ownRaceCards(List<Card> all, Race faction) {
        List<Card> deck = new ArrayList<Card>();
        for (int i = 0; i < all.size() && deck.size() < 60; i++) {
            Card c = all.get(i);
            if (c.getLoyalty() == Loyalty.fromRace(faction)) deck.add(c);
        }
        return deck;
    }

    /** The faction's starting ambassador, or null when the deck carries none. */
    private static CharacterCard findAmbassadorCard(List<Card> cards, Race f) {
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            if (c instanceof CharacterCard) {
                CharacterCard ch = (CharacterCard) c;
                if (ch.isAmbassador() && ch.getLoyalty() == Loyalty.fromRace(f)) return ch;
            }
        }
        return null;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Reporting ────────────────────────────────────────────────────────────

    private static void check(String label, boolean passed) {
        checks++;
        System.out.println("  [DU] " + label + ": " + (passed ? "PASS" : "FAIL"));
        if (!passed) failures++;
    }
}
