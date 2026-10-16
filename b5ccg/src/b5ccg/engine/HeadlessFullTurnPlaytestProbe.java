package b5ccg.engine;

import b5ccg.ai.AIPlayer;
import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.ConflictCard;
import b5ccg.model.Deck;
import b5ccg.model.FleetCard;
import b5ccg.model.GameAction;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.AIDifficulty;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.GamePhase;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * B5-2273 — Human-seat scripted FULL-TURN playtest harness
 * (harness-only, new file; no game-logic edits).
 *
 * <p>Distinct from its two siblings, deliberately:
 * <ul>
 *   <li><b>B5-0443 HeadlessHumanSeatProbe</b> is an ACTION-COVERAGE probe: it
 *       drives a human seat to termination and asserts every action family was
 *       submitted at least once. It says nothing about the ROUND structure.</li>
 *   <li><b>B5-0589 HeadlessStallSoakProbe</b> is a soak rig reporting rounds
 *       reached, winner, terminator classification and influence standings over
 *       N seeds. By its own final line its classifications are "reporting, not
 *       failures" and its exit codes carry no pass/fail semantics, so it cannot
 *       gate anything.</li>
 *   <li><b>This probe</b> is the missing third thing: a bounded, GATEABLE
 *       playtest of the ROUND LIFECYCLE, driven only through the two surfaces a
 *       UI actually uses — {@link GameController#submitHumanAction(GameAction)}
 *       for the human seat, and the {@link GameStateCallback} stream for what the
 *       UI can see.</li>
 * </ul>
 *
 * <p>Because the UI's only refresh trigger is that callback, the probe measures
 * two DIFFERENT things and refuses to conflate them:
 * <ul>
 *   <li><b>engine-side</b> which phases the engine entered, read from the game
 *       log ({@code GameState.setPhase} logs every transition), and</li>
 *   <li><b>UI-side</b> which phases were actually delivered through the
 *       callback.</li>
 * </ul>
 * A phase the engine entered but never notified is invisible to every consumer of
 * the callback, and the difference between the two sets is reported as a counted
 * FINDING rather than silently folded into the pass condition.
 *
 * <p><b>GATES (exit 1 on any failure)</b> — all in scope for a harness-only row:
 * <ol>
 *   <li>zero stalls, from five independent signals so none is load-bearing: a
 *       no-observation watchdog, a per-round deadline, a per-game deadline, the
 *       engine's own ACTION safety cap, and the B5-2275 consecutive-full-pass
 *       guard;</li>
 *   <li>at least the requested number of COMPLETED rounds, where a completed
 *       round is one whose UI stream ran ACTION … AFTERMATH … END_ROUND — the
 *       round-entry observation is excluded, because the controller advances the
 *       counter while still displaying the previous round's END_ROUND;</li>
 *   <li>per completed round, the engine-side log shows the full lifecycle
 *       ACTION → AFTERMATH → DRAW → END_ROUND in that order;</li>
 *   <li>per completed round the UI stream shows ACTION → AFTERMATH → END_ROUND in
 *       that order;</li>
 *   <li>the victory check RAN at every round boundary and AGREED with the
 *       engine's own winner — re-evaluated independently through
 *       {@code RulesEngine.checkVictory} (read-only) at the END_ROUND
 *       notification, which the controller emits only after
 *       {@code RulesEngine.drawRound} has already consulted it;</li>
 *   <li>the human seat was actually driven, and at least one CONFLICT_RESOLUTION
 *       span was exercised.</li>
 * </ol>
 *
 * <p><b>FINDINGS (exit 4)</b> — measured, counted and printed, never folded into
 * the pass condition and never asserted here, because each one lives in a
 * game-logic file this row's scope forbids editing:
 * <ul>
 *   <li><b>UI-NOTIFY-GAP</b>: a phase the engine entered (proved by the log) that
 *       the callback never delivered (proved by its absence from the stream).</li>
 *   <li><b>ROUND-TRUNCATION</b>: a round ended by the engine's
 *       {@code MAX_ACTIONS_PER_ROUND} safety cap rather than by the whole table
 *       passing. The consequence is still gated — a truncated round must
 *       complete the lifecycle — but the truncation itself is a game-logic
 *       decision, not a harness defect.</li>
 * </ul>
 *
 * <p>Round-structure mapping (rulebook round → engine phases), stated so the
 * assertions are not a guess: {@code RulesEngine.startRound} implements the READY
 * step (its own ordering-freeze comments say so) and the controller advances the
 * counter <em>before</em> {@code setPhase(ACTION)}, so the UI first sees round N
 * while still displaying round N-1's END_ROUND. That entry observation is what
 * "ready observed" asserts; for round 1 it is SETUP. MERCENARY is deliberately
 * NOT required: {@code runMercenaryPhase} returns before {@code setPhase} when
 * there are no mercenary offers, so an ordinary game never enters it.
 *
 * <p>Usage:
 * <pre>
 *   java -cp b5ccg/out b5ccg.engine.HeadlessFullTurnPlaytestProbe [seed] [rounds]
 * </pre>
 * Default seed 42, 8 rounds. <b>Exit codes:</b> 0 = gates green and no findings;
 * 1 = an in-scope gate failed; 2 = harness error (card pool, deck load, bad
 * arguments); 4 = every in-scope gate passed but N out-of-scope findings were
 * recorded (see above). 4 is deliberately distinct from 0 so a caller can tell
 * "green" from "green with a pending engine finding", and distinct from 1 so an
 * out-of-scope finding is never scored as a defect of this harness.
 *
 * <p><b>Not wired into compile.sh's RUN_TESTS gate</b>, on the B5-0956
 * measurement already recorded for its siblings: GameController
 * {@code pause(600)}s after every AI action, so a table costs tens of seconds
 * against a gate tier whose classes run in hundreds of milliseconds. Run it on
 * demand, as HeadlessHumanSeatProbe / HeadlessMultiRoundTest /
 * HeadlessStallSoakProbe are.
 *
 * <p>Java 6 only. No game-logic file is edited (B5-0201 harness precedent).
 */
public class HeadlessFullTurnPlaytestProbe {

    // ── Tunables ─────────────────────────────────────────────────────────────
    /** Rounds the playtest must complete across the whole run. */
    private static int targetRounds = 8;
    /** Bounded attempts: a game may legitimately end by victory before the target. */
    private static final int  MAX_GAMES       = 4;
    /** No UI observation at all for this long is a wedged game thread. */
    private static final long OBS_TIMEOUT_MS  = 20000L;
    /** One round must complete inside this, counted from the previous advance. */
    private static final long ROUND_TIMEOUT_MS = 120000L;
    /** Whole-game deadline. Generous: it must not be the thing that fires. */
    private static final long GAME_TIMEOUT_MS  = 600000L;
    /** Nothing at all may arrive for this long after the game thread starts. */
    private static final long STARTUP_GRACE_MS = 90000L;
    /** Driver poll period; the controller's own wait granularity is 200 ms. */
    private static final long DRIVER_POLL_MS   = 25L;

    private static final String[] NAMES = {"Human", "BrAVo", "GDelta", "DLtte"};
    private static final Faction[] FACTIONS =
        {Faction.HUMAN, Faction.MINBARI, Faction.CENTAURI, Faction.NARN};
    private static final AIDifficulty[] DIFFICULTIES =
        {null, AIDifficulty.MEDIUM, AIDifficulty.HARD, AIDifficulty.MEDIUM};

    // ── The SCRIPT ───────────────────────────────────────────────────────────
    private static final int SCRIPT_PLAY_CARD       = 0;
    private static final int SCRIPT_INITIATE        = 1;
    private static final int SCRIPT_RECRUIT         = 2;
    private static final int SCRIPT_BUILD_INFLUENCE = 3;
    private static final int SCRIPT_LEAD_FLEET      = 4;
    private static final int SCRIPT_PROMOTE         = 5;
    private static final int SCRIPT_LEN             = 6;

    // ── Assertions ───────────────────────────────────────────────────────────
    private static int checks = 0;
    private static int failed = 0;
    private static final List<String> failures = new ArrayList<String>();
    /** Out-of-scope, game-logic findings: measured, counted, never asserted here. */
    private static int findings = 0;
    private static final List<String> findingLines = new ArrayList<String>();

    private static void check(String label, boolean ok) {
        checks++;
        if (!ok) {
            failed++;
            failures.add(label);
            System.out.println("  GATE FAIL: " + label);
        }
    }

    private static void finding(String label) {
        findings++;
        findingLines.add(label);
        System.out.println("  FINDING (game-logic, not fixed here): " + label);
    }

    // ── Run-wide tallies ─────────────────────────────────────────────────────
    private static int totalRounds      = 0;
    private static int gamesPlayed      = 0;
    private static int humanSubmits     = 0;
    private static int victoryChecksRun = 0;
    private static int conflictSpans    = 0;
    private static int cappedRounds     = 0;
    private static final Set<String> uiPhasesSeen     = new TreeSet<String>();
    private static final Set<String> enginePhasesSeen = new TreeSet<String>();

    public static void main(String[] args) throws Exception {
        long seed = 42L;
        if (args.length >= 1) {
            try { seed = Long.parseLong(args[0].trim()); }
            catch (NumberFormatException e) {
                System.err.println("HARNESS ERROR: bad seed '" + args[0] + "'");
                System.exit(2);
                return;
            }
        }
        if (args.length >= 2) {
            try { targetRounds = Integer.parseInt(args[1].trim()); }
            catch (NumberFormatException e) {
                System.err.println("HARNESS ERROR: bad round target '" + args[1] + "'");
                System.exit(2);
                return;
            }
        }
        if (targetRounds < 1) {
            System.err.println("HARNESS ERROR: round target must be >= 1");
            System.exit(2);
            return;
        }

        System.out.println("=== B5 CCG human-seat scripted full-turn playtest (B5-2273) ===");
        System.out.println("Seed: " + seed + ", target rounds: " + targetRounds
                           + ", max games: " + MAX_GAMES);
        System.out.println();

        List<Card> pool;
        try {
            pool = DeckLoader.loadBothSets();
        } catch (Exception e) {
            System.err.println("HARNESS ERROR: card pool load failed: " + e);
            System.exit(2);
            return;
        }
        check("card pool loaded (446 records), got " + pool.size(), pool.size() == 446);

        // Seed the starter-deck random draw (B5-0482 public-setter precedent) so
        // deck — and therefore hand — composition is reproducible at a fixed seed.
        StarterDeckBuilder.setRandomSeed(seed);

        while (totalRounds < targetRounds && gamesPlayed < MAX_GAMES) {
            gamesPlayed++;
            long gameSeed = seed + gamesPlayed * 7919L;
            System.out.println("-- game " + gamesPlayed + " (seed " + gameSeed + ") --");
            playOneGame(pool, gameSeed);
            System.out.println("   cumulative completed rounds: " + totalRounds);
            System.out.println();
        }

        StarterDeckBuilder.setRandomSeed(0L); // restore default draw behaviour

        // ── Run-wide gates ───────────────────────────────────────────────────
        check("playtest completed at least " + targetRounds + " completed rounds across"
              + " <= " + MAX_GAMES + " games (got " + totalRounds + " in " + gamesPlayed + ")",
              totalRounds >= targetRounds);
        check("a human seat was driven through submitHumanAction (" + humanSubmits
              + " submits)", humanSubmits > 0);
        check("the victory check ran exactly once per completed round (" + victoryChecksRun
              + " checks over " + totalRounds + " rounds)",
              victoryChecksRun == totalRounds);
        check("at least one CONFLICT_RESOLUTION span was driven (" + conflictSpans + ")",
              conflictSpans > 0);

        System.out.println("-- phases the ENGINE entered (from the game log) --");
        System.out.println("   " + enginePhasesSeen);
        System.out.println("-- phases DELIVERED to the UI (from the callback stream) --");
        System.out.println("   " + uiPhasesSeen);
        if (!enginePhasesSeen.containsAll(uiPhasesSeen)) {
            finding("the UI callback stream carried a phase the engine log never"
                    + " recorded: " + minus(uiPhasesSeen, enginePhasesSeen));
        }
        emitGapFindings();
        check("the engine-side log recorded the full lifecycle "
              + "(ACTION, AFTERMATH, DRAW, END_ROUND)",
              enginePhasesSeen.contains("ACTION") && enginePhasesSeen.contains("AFTERMATH")
              && enginePhasesSeen.contains("DRAW") && enginePhasesSeen.contains("END_ROUND"));

        System.out.println();
        System.out.println("checks: " + checks + ", gate failures: " + failed
                           + ", out-of-scope findings: " + findings);
        System.out.println("games: " + gamesPlayed + ", completed rounds: " + totalRounds
                           + ", human submits: " + humanSubmits
                           + ", victory checks: " + victoryChecksRun
                           + ", conflict spans: " + conflictSpans
                           + ", cap-truncated rounds: " + cappedRounds);
        if (failed > 0) {
            System.out.println("B5-2273 PLAYTEST FAILED: " + failed + " in-scope gate(s)");
            for (int i = 0; i < failures.size(); i++) {
                System.out.println("  " + (i + 1) + ") " + failures.get(i));
            }
            if (findings > 0) {
                System.out.println("  plus " + findings + " out-of-scope finding(s):");
                for (int i = 0; i < findingLines.size(); i++) {
                    System.out.println("   - " + findingLines.get(i));
                }
            }
            System.exit(1);
        } else if (findings > 0) {
            System.out.println("B5-2273 PLAYTEST GATES GREEN with " + findings
                               + " out-of-scope finding(s):");
            for (int i = 0; i < findingLines.size(); i++) {
                System.out.println("   - " + findingLines.get(i));
            }
            System.exit(4);
        } else {
            System.out.println("B5-2273 PLAYTEST PASSED "
                               + "(zero stalls, full round lifecycle, victory check green)");
            System.exit(0);
        }
    }

    private static Set<String> minus(Set<String> a, Set<String> b) {
        Set<String> out = new TreeSet<String>(a);
        out.removeAll(b);
        return out;
    }

    // ── UI-notify gap accumulation (one finding per phase, with a count) ─────
    private static final java.util.Map<String, List<Integer>> gapRounds =
        new java.util.TreeMap<String, List<Integer>>();
    private static final java.util.Map<String, String> gapWhere =
        new java.util.TreeMap<String, String>();

    private static void noteGap(GamePhase phase, int round) {
        String key = String.valueOf(phase);
        List<Integer> rs = gapRounds.get(key);
        if (rs == null) {
            rs = new ArrayList<Integer>();
            gapRounds.put(key, rs);
        }
        rs.add(Integer.valueOf(round));
        if (phase == GamePhase.DRAW) {
            gapWhere.put(key, "GameController.runDrawPhase sets DRAW, runs drawRound,"
                + " sets END_ROUND and then issues ONE notifyUI() at the end, so the"
                + " callback fires only while the phase is already END_ROUND. No"
                + " consumer of GameStateCallback can ever observe DRAW, so any UI"
                + " affordance gated on GamePhase.DRAW is unreachable — which is"
                + " exactly the shape of the B5-2267 draw-round buy-cards control.");
        } else {
            gapWhere.put(key, "the engine entered MERCENARY but the callback stream"
                + " never carried it; runMercenaryPhase notifies immediately after"
                + " setPhase, so this would indicate a notify placed outside the"
                + " phase rather than after it.");
        }
    }

    private static void emitGapFindings() {
        for (java.util.Iterator<String> it = gapRounds.keySet().iterator(); it.hasNext(); ) {
            String key = it.next();
            List<Integer> rs = gapRounds.get(key);
            finding("UI-NOTIFY-GAP: GamePhase." + key + " was entered by the engine and"
                + " is recorded in the game log, but never delivered through the"
                + " callback — measured in " + rs.size() + " completed round(s)"
                + " across the run (round numbers restart per game): " + rs
                + ". " + gapWhere.get(key));
        }
    }

    // ── One seeded game ──────────────────────────────────────────────────────

    private static void playOneGame(List<Card> pool, long seed) throws Exception {
        List<Player> players = new ArrayList<Player>();
        List<AIPlayer> ais    = new ArrayList<AIPlayer>();
        for (int i = 0; i < 4; i++) {
            List<Card> deck = StarterDeckBuilder.build(FACTIONS[i], pool);
            if (deck.isEmpty()) {
                System.err.println("HARNESS ERROR: no starter deck for " + FACTIONS[i]);
                System.exit(2);
                return;
            }
            Player p = new Player(NAMES[i], FACTIONS[i], i == 0);
            CharacterCard amb = findAmbassador(deck, FACTIONS[i]);
            if (amb != null) {
                deck.remove(deck.indexOf(amb));
                p.setAmbassador(amb);
                p.getInnerCircle().add(amb);
            }
            p.setDeck(new Deck(deck));
            p.drawCards(4);
            players.add(p);
            if (i > 0) {
                AIPlayer ai = new AIPlayer(p, DIFFICULTIES[i]);
                try {
                    java.lang.reflect.Field f = AIPlayer.class.getDeclaredField("rng");
                    f.setAccessible(true);
                    f.set(ai, new Random(seed + i * 1000L));   // B5-0349 precedent
                } catch (Exception e) {
                    System.err.println("HARNESS ERROR: AI rng seed seed failed: " + e);
                    System.exit(2);
                    return;
                }
                ais.add(ai);
            }
        }

        final GameState state = new GameState(players);
        for (int i = 0; i < players.size(); i++) players.get(i).setGameState(state);

        final Recorder recorder = new Recorder(state);

        // The UI callback IS the observation instrument: the controller notifies
        // it at every state change, exactly the payload MainWindow renders.
        // Recording that stream, rather than polling internals, is what makes
        // this a UI-level playtest instead of a white-box inspection.
        final GameController controller = new GameController(state, ais,
                new GameStateCallback() {
                    public void accept(GameState gs) {
                        recorder.observe(gs.getRoundNumber(), gs.getPhase());
                    }
                });

        final boolean[] done        = new boolean[1];
        final Throwable[] thrown    = new Throwable[1];
        final int[] joinWindows     = new int[1];
        final int[] scriptAt        = new int[1];
        final boolean[] stopScript  = new boolean[1];

        Thread gameThread = new Thread(new Runnable() {
            public void run() {
                try { controller.runGame(); }
                catch (Throwable t) { thrown[0] = t; }
                finally { done[0] = true; recorder.markFinished(); }
            }
        }, "b5-2273-game");
        gameThread.setDaemon(true);

        Thread driver = new Thread(new Runnable() {
            public void run() {
                while (!recorder.isFinished() && !stopScript[0]) {
                    if (controller.isWaitingForHuman()) {
                        GameAction a = scriptedAction(state, scriptAt[0]);
                        scriptAt[0] = (scriptAt[0] + 1) % SCRIPT_LEN;
                        submit(controller, a);
                    } else if (controller.isWaitingForHumanConflictJoin()) {
                        // Deterministic side choice: support on even join windows.
                        submit(controller, (joinWindows[0] % 2 == 0)
                                ? GameAction.joinSupport() : GameAction.joinOppose());
                        joinWindows[0]++;
                    } else if (controller.isWaitingForHumanConflictAttack()) {
                        // B5-0432 window. PASS is accepted here and declines the
                        // attack; this probe proves the round lifecycle, not
                        // attack coverage, which B5-0443 already owns.
                        submit(controller, GameAction.pass());
                    }
                    try { Thread.sleep(DRIVER_POLL_MS); }
                    catch (InterruptedException e) { return; }
                }
            }
        }, "b5-2273-driver");
        driver.setDaemon(true);

        long start = System.currentTimeMillis();
        gameThread.start();
        driver.start();

        // ── Watchdogs + target detection, on the real clock ──────────────────
        String stall = null;
        int  lastRound       = 0;
        long lastRoundChange = start;
        long lastObservation = start;
        int  lastObsCount    = 0;
        long graceEnds       = start + STARTUP_GRACE_MS;

        while (stall == null && !done[0] && !recorder.targetReached(targetRounds)) {
            long now = System.currentTimeMillis();
            int obs  = recorder.observationCount();
            if (obs != lastObsCount) {
                lastObsCount    = obs;
                lastObservation = now;
            }
            int round = state.getRoundNumber();
            if (round != lastRound) {
                lastRound       = round;
                lastRoundChange = now;
            }
            if (now > graceEnds && now - lastObservation > OBS_TIMEOUT_MS) {
                stall = "no UI observation for " + ((now - lastObservation) / 1000L)
                      + "s at round " + round + " — the game thread is wedged"
                      + " (observations " + obs + ", log tail \"" + tail(state) + "\")";
            } else if (round > 1 && now - lastRoundChange > ROUND_TIMEOUT_MS) {
                stall = "round " + round + " did not complete within "
                      + (ROUND_TIMEOUT_MS / 1000L) + "s — round-level stall"
                      + " (log tail \"" + tail(state) + "\")";
            } else if (now - start > GAME_TIMEOUT_MS) {
                stall = "game exceeded the " + (GAME_TIMEOUT_MS / 1000L)
                      + "s whole-game deadline at round " + round;
            }
            try { Thread.sleep(50L); } catch (InterruptedException e) { break; }
        }

        stopScript[0] = true;
        recorder.freeze();
        driver.interrupt();
        // Deliberately NO gameThread.interrupt(): waitForHumanAction() re-sets its
        // interrupt flag inside the catch and re-enters wait(), so interrupting a
        // game thread parked in a human window livelocks it instead of stopping
        // it. The game thread is a daemon, so System.exit in main reaps it.

        // ── Per-game gates ───────────────────────────────────────────────────
        String tag = "game " + gamesPlayed;
        if (thrown[0] != null) {
            check(tag + ": the game thread ran to completion without throwing", false);
            thrown[0].printStackTrace(System.err);
        }
        if (stall != null) {
            check(tag + ": ZERO STALKS — " + stall, false);
        }
        check(tag + ": the B5-2275 consecutive-full-pass guard stayed under its limit"
              + " (counter " + controller.getConsecutiveFullPassRounds() + " < "
              + GameController.MAX_CONSECUTIVE_FULL_PASS_ROUNDS + ")",
              controller.getConsecutiveFullPassRounds()
                  < GameController.MAX_CONSECUTIVE_FULL_PASS_ROUNDS);
        check(tag + ": the game did not end on the B5-2275 reported-tie path",
              !controller.isStalledOnTie());

        // The engine's own ACTION cap, read from its log rather than inferred.
        Set<Integer> capped = cappedRounds(state);

        List<RoundRecord> rounds = recorder.completedRounds();
        for (int i = 0; i < rounds.size(); i++) {
            assertRound(tag, rounds.get(i), enginePhasesOf(state), capped);
        }
        totalRounds += rounds.size();
        cappedRounds += capped.size();
        if (!capped.isEmpty()) {
            finding(tag + ": the ACTION round was truncated by the engine's"
                    + " MAX_ACTIONS_PER_ROUND safety cap in round(s) " + capped
                    + " — the round ended on the cap rather than on a whole-table"
                    + " pass (GameController.runActionPhase; B5-0372 leaves the"
                    + " cycle unbounded and the cap is the only bound). Lifecycle"
                    + " completion is still gated above.");
        }

        System.out.println("   " + tag + ": " + rounds.size() + " round(s) completed, "
                           + recorder.partialRounds() + " cut short by game-over, "
                           + capped.size() + " cap-truncated, "
                           + recorder.observationCount() + " UI observations, winner "
                           + name(state.getWinner())
                           + (done[0] ? "" : " (harness stopped it at the round target)"));
    }

    /**
     * Rounds whose ACTION loop was cut off by the engine's own safety cap.
     * The cap line is logged by runActionPhase with the round prefix, so this is
     * a measurement of the engine's log, not an inference from timing.
     */
    private static Set<Integer> cappedRounds(GameState state) {
        Set<Integer> out = new java.util.TreeSet<Integer>();
        List<String> log = state.getLog();
        for (int i = 0; i < log.size(); i++) {
            String line = log.get(i);
            if (line.indexOf("Action round safety cap reached") >= 0) {
                Integer r = roundOfLogLine(line);
                if (r != null) out.add(r);
            }
        }
        return out;
    }

    /**
     * Engine-side phase history, bucketed by the round number the log line
     * carries. {@code GameState.setPhase} logs every transition, so this is the
     * engine's own record of which phases it entered — independent of whether
     * the UI callback was told about them.
     */
    private static java.util.Map<Integer, Set<String>> enginePhasesOf(GameState state) {
        java.util.Map<Integer, Set<String>> out =
            new java.util.TreeMap<Integer, Set<String>>();
        List<String> log = state.getLog();
        for (int i = 0; i < log.size(); i++) {
            String line = log.get(i);
            Integer r = roundOfLogLine(line);
            if (r == null) continue;
            int at = line.indexOf("] Phase");
            if (at < 0) continue;
            // GameState.setPhase logs "Phase <U+2192> <NAME>". The arrow glyph is
            // deliberately NOT matched literally — a harness that hard-codes the
            // separator breaks the day someone replaces it with an ASCII one, and
            // the silent failure is an empty phase set, i.e. every engine-side
            // assertion vacuously red. Take the token after the LAST space.
            String rest  = line.substring(at + "] Phase".length()).trim();
            int    sp    = rest.lastIndexOf(' ');
            String token = (sp >= 0) ? rest.substring(sp + 1).trim() : rest;
            GamePhase p = phaseOrNull(token);
            if (p == null) continue;
            Set<String> bucket = out.get(r);
            if (bucket == null) {
                bucket = new TreeSet<String>();
                out.put(r, bucket);
            }
            bucket.add(String.valueOf(p));
            enginePhasesSeen.add(String.valueOf(p));
        }
        return out;
    }

    private static GamePhase phaseOrNull(String token) {
        GamePhase[] all = GamePhase.values();
        for (int i = 0; i < all.length; i++) {
            if (all[i].name().equals(token)) return all[i];
        }
        return null;
    }

    /** "[R7] ..." -> 7; anything else -> null. */
    private static Integer roundOfLogLine(String line) {
        if (line == null || line.length() < 4) return null;
        if (line.charAt(0) != '[' || line.charAt(1) != 'R') return null;
        int end = line.indexOf(']');
        if (end < 3) return null;
        try {
            return Integer.valueOf(Integer.parseInt(line.substring(2, end)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** One completed round: gates + the two-tier engine/UI lifecycle comparison. */
    private static void assertRound(String tag, RoundRecord rr,
                                    java.util.Map<Integer, Set<String>> enginePhases,
                                    Set<Integer> capped) {
        String at = tag + " round " + rr.round + ": ";
        int iAction    = rr.firstIndexOf(GamePhase.ACTION);
        int iAftermath = rr.firstIndexOf(GamePhase.AFTERMATH);
        int iEndRound  = rr.firstIndexOf(GamePhase.END_ROUND);
        int iConflict  = rr.firstIndexOf(GamePhase.CONFLICT_RESOLUTION);

        // ── UI-side gates ────────────────────────────────────────────────────
        check(at + "the round was entered (ready observed, entry phase "
              + rr.entryPhase + ")", rr.readyObserved);
        check(at + "ACTION was delivered to the UI (index " + iAction + ")", iAction >= 0);
        check(at + "AFTERMATH was delivered to the UI (index " + iAftermath + ")",
              iAftermath >= 0);
        check(at + "END_ROUND was delivered to the UI (index " + iEndRound + ")",
              iEndRound >= 0);
        if (iAction >= 0 && iAftermath >= 0) {
            check(at + "UI order ACTION < AFTERMATH (" + iAction + " < " + iAftermath + ")",
                  iAction < iAftermath);
        }
        if (iAftermath >= 0 && iEndRound >= 0) {
            check(at + "UI order AFTERMATH < END_ROUND (" + iAftermath + " < "
                  + iEndRound + ")", iAftermath < iEndRound);
        }
        if (iConflict >= 0) {
            conflictSpans++;
            check(at + "CONFLICT_RESOLUTION is nested inside the ACTION span, not"
                  + " after it (conflict " + iConflict + ", action " + iAction + ")",
                  iConflict > iAction);
        }

        // ── Engine-side gate: the log must show the full lifecycle, in order ──
        Set<String> eng = enginePhases.get(Integer.valueOf(rr.round));
        check(at + "the engine log recorded ACTION for this round",
              eng != null && eng.contains("ACTION"));
        check(at + "the engine log recorded AFTERMATH for this round",
              eng != null && eng.contains("AFTERMATH"));
        check(at + "the engine log recorded DRAW for this round",
              eng != null && eng.contains("DRAW"));
        check(at + "the engine log recorded END_ROUND for this round",
              eng != null && eng.contains("END_ROUND"));

        // ── Two-tier comparison: entered by the engine, delivered to the UI? ──
        //
        // Aggregated per run rather than per round: the same gap repeating every
        // round is ONE defect with a count, and printing it 8 times would bury
        // the second finding under it.
        if (eng != null) {
            if (eng.contains("DRAW") && rr.firstIndexOf(GamePhase.DRAW) < 0) {
                noteGap(GamePhase.DRAW, rr.round);
            }
            if (eng.contains("MERCENARY") && rr.firstIndexOf(GamePhase.MERCENARY) < 0) {
                noteGap(GamePhase.MERCENARY, rr.round);
            }
        }
        if (capped.contains(Integer.valueOf(rr.round))) {
            // The truncation itself is a finding (emitted once per game); what is
            // IN SCOPE is that a truncated round still completed its lifecycle.
            check(at + "a cap-truncated ACTION round still completed the lifecycle"
                  + " (AFTERMATH and END_ROUND both delivered)", iAftermath >= 0);
        }

        // ── "Victory check runs", made falsifiable ───────────────────────────
        // The harness re-evaluates the single victory authority
        // (RulesEngine.checkVictory, read-only) at the round boundary and requires
        // it to agree with the engine's own view. A disagreement is a real defect:
        // the controller consults checkVictory after every action and
        // RulesEngine.drawRound returns it, so an independent non-null winner that
        // state.getWinner() does not carry is a missed win.
        check(at + "the victory check RAN at the round boundary", rr.victoryCheckRan);
        if (rr.victoryCheckRan) {
            check(at + "the victory check agreed with the engine's winner"
                  + " (independent=" + name(rr.victoryCheckWinner)
                  + ", engine=" + name(rr.engineWinner) + ")",
                  rr.victoryCheckWinner == rr.engineWinner);
        }
    }

    private static String name(Player p) { return p == null ? "none" : p.getName(); }

    private static String tail(GameState state) {
        List<String> log = state.getLog();
        if (log.isEmpty()) return "<empty log>";
        return log.get(log.size() - 1);
    }

    // ── Scripted human driver ────────────────────────────────────────────────

    private static void submit(GameController controller, GameAction a) {
        humanSubmits++;
        controller.submitHumanAction(a == null ? GameAction.pass() : a);
    }

    /** Resolve one script step against the open window, walking a fixed fallback. */
    private static GameAction scriptedAction(GameState state, int scriptStep) {
        Player human = state.getHumanPlayer();
        if (human == null || !human.isHuman()) return GameAction.pass();
        if (state.getActiveConflict() != null) return GameAction.pass();
        RulesEngine rules = new RulesEngine();
        int step = scriptStep % SCRIPT_LEN;
        // Scripted preference first, then every other step in a FIXED order, then
        // PASS. The fixed order is what makes the run reproducible, and PASS is
        // always accepted, so the driver can never be the reason a round stalls.
        for (int k = 0; k < SCRIPT_LEN; k++) {
            GameAction a = resolve(rules, state, human, (step + k) % SCRIPT_LEN);
            if (a != null) return a;
        }
        return GameAction.pass();
    }

    private static GameAction resolve(RulesEngine rules, GameState state,
                                      Player human, int step) {
        switch (step) {
            case SCRIPT_PLAY_CARD: {
                for (int i = 0; i < human.getHand().size(); i++) {
                    Card c = human.getHand().get(i);
                    if (c == null) continue;
                    if (c instanceof CharacterCard) continue;   // its own script step
                    if (c instanceof ConflictCard) continue;    // its own script step
                    if (rules.canPlayCard(human, c)) return GameAction.playCard(c);
                }
                return null;
            }
            case SCRIPT_INITIATE: {
                if (state.hasInitiatedConflictThisTurn(human)) return null;
                Player target = firstEnemy(human, state);
                if (target == null) return null;
                for (int i = 0; i < human.getHand().size(); i++) {
                    Card c = human.getHand().get(i);
                    if (!(c instanceof ConflictCard)) continue;
                    ConflictCard cc = (ConflictCard) c;
                    if (rules.canInitiateConflict(human, cc, target, state)) {
                        return GameAction.initiateConflict(cc, target);
                    }
                }
                return null;
            }
            case SCRIPT_RECRUIT: {
                for (int i = 0; i < human.getHand().size(); i++) {
                    Card c = human.getHand().get(i);
                    if (!(c instanceof CharacterCard)) continue;
                    CharacterCard ch = (CharacterCard) c;
                    if (rules.canRecruit(human, ch)) return GameAction.recruitCharacter(ch);
                }
                return null;
            }
            case SCRIPT_BUILD_INFLUENCE: {
                if (!rules.canBuildInfluence(human)) return null;
                for (int i = 0; i < human.getInnerCircle().size(); i++) {
                    Card c = human.getInnerCircle().get(i);
                    if (!(c instanceof CharacterCard)) continue;
                    CharacterCard ch = (CharacterCard) c;
                    if (!ch.isRotated() && ch.canActAfterNeutralization()) {
                        return GameAction.buildInfluence(ch);
                    }
                }
                return null;
            }
            case SCRIPT_LEAD_FLEET: {
                for (int i = 0; i < human.getFleets().size(); i++) {
                    Card f = human.getFleets().get(i);
                    if (!(f instanceof FleetCard)) continue;
                    FleetCard fl = (FleetCard) f;
                    for (int j = 0; j < human.getInnerCircle().size(); j++) {
                        Card c = human.getInnerCircle().get(j);
                        if (!(c instanceof CharacterCard)) continue;
                        CharacterCard ch = (CharacterCard) c;
                        if (rules.canLeadFleet(human, ch, fl)) {
                            return GameAction.leadFleet(ch, fl);
                        }
                    }
                }
                return null;
            }
            case SCRIPT_PROMOTE: {
                for (int i = 0; i < human.getSupportingRole().size(); i++) {
                    Card c = human.getSupportingRole().get(i);
                    if (!(c instanceof CharacterCard)) continue;
                    CharacterCard ch = (CharacterCard) c;
                    if (!rules.canPromote(human, ch)) continue;
                    for (int j = 0; j < human.getInnerCircle().size(); j++) {
                        Card l = human.getInnerCircle().get(j);
                        if (!(l instanceof CharacterCard)) continue;
                        CharacterCard leader = (CharacterCard) l;
                        if (!leader.isRotated()) {
                            return GameAction.promoteCharacter(ch, leader);
                        }
                    }
                }
                return null;
            }
            default:
                return null;
        }
    }

    private static Player firstEnemy(Player p, GameState state) {
        for (int i = 0; i < state.getPlayers().size(); i++) {
            Player q = state.getPlayers().get(i);
            if (q == p) continue;
            if (!state.isPlayerActive(q)) continue;
            if (q.getFaction() == p.getFaction()) continue;
            return q;
        }
        return null;
    }

    private static CharacterCard findAmbassador(List<Card> deck, Faction f) {
        for (int i = 0; i < deck.size(); i++) {
            Card c = deck.get(i);
            if (c instanceof CharacterCard) {
                CharacterCard ch = (CharacterCard) c;
                if (ch.isAmbassador() && ch.getFaction() == f) return ch;
            }
        }
        return null;
    }

    // ── Observation recorder (the UI-level instrument) ───────────────────────

    /** One round as the UI saw it. */
    static class RoundRecord {
        int    round;
        final List<GamePhase> phases = new ArrayList<GamePhase>();
        GamePhase entryPhase;
        boolean readyObserved;
        boolean victoryCheckRan;
        Player  victoryCheckWinner;
        Player  engineWinner;

        int firstIndexOf(GamePhase p) {
            for (int i = 0; i < phases.size(); i++) if (phases.get(i) == p) return i;
            return -1;
        }

        int countOf(GamePhase p) {
            int n = 0;
            for (int i = 0; i < phases.size(); i++) if (phases.get(i) == p) n++;
            return n;
        }

        public String toString() { return "R" + round + phases; }
    }

    /**
     * Records the (round, phase) stream the controller delivers through the UI
     * callback, and runs the victory check at each round boundary.
     *
     * <p>The ENTRY observation of a round is recorded as {@code entryPhase} and
     * deliberately NOT appended to {@code phases}: the controller advances the
     * round counter while still displaying the previous round's END_ROUND, so
     * counting that observation as part of the round would make a round that was
     * cut short by game-over look like it had completed a lifecycle.
     *
     * <p>{@link #observe} runs on the GAME thread (the callback is invoked from
     * inside {@code runGame}) while main polls the counters, so the record and
     * the counters are guarded by the instance monitor rather than left to a
     * data race.
     */
    static class Recorder {

        private final GameState state;
        private final RulesEngine rules = new RulesEngine();
        private final List<RoundRecord> records = new ArrayList<RoundRecord>();
        private List<RoundRecord> frozen = new ArrayList<RoundRecord>();
        private boolean finished = false;
        private boolean froze    = false;
        private int  obsCount    = 0;

        Recorder(GameState state) { this.state = state; }

        synchronized void observe(int round, GamePhase phase) {
            obsCount++;
            uiPhasesSeen.add(String.valueOf(phase));
            RoundRecord cur = null;
            for (int i = records.size() - 1; i >= 0; i--) {
                if (records.get(i).round == round) { cur = records.get(i); break; }
            }
            boolean isEntry = (cur == null);
            if (isEntry) {
                cur = new RoundRecord();
                cur.round      = round;
                cur.entryPhase = phase;
                // The counter is advanced while the phase is still the previous
                // round's tail phase (advanceRound() runs before the next
                // setPhase(ACTION)); for round 1 the entry phase is SETUP. Either
                // way the UI saw the counter enter this round, which is the
                // observable form of the READY step.
                cur.readyObserved = (phase == GamePhase.END_ROUND
                                     || phase == GamePhase.SETUP);
                records.add(cur);
            } else {
                cur.phases.add(phase);
            }

            // Victory check at the round boundary: run on the END_ROUND
            // notification that TERMINATES this round's lifecycle, which
            // GameController.runDrawPhase emits AFTER RulesEngine.drawRound has
            // already consulted checkVictory. The ENTRY observation is excluded
            // deliberately — for every round past the first the entry phase IS
            // END_ROUND (the previous round's tail), and checking victory there
            // would score the previous round's boundary, not this one's.
            if (!isEntry && phase == GamePhase.END_ROUND && !cur.victoryCheckRan) {
                cur.victoryCheckRan    = true;
                victoryChecksRun++;
                cur.victoryCheckWinner = rules.checkVictory(state);
                cur.engineWinner       = state.getWinner();
            }
        }

        synchronized void markFinished()          { finished = true; }
        synchronized boolean isFinished()          { return finished; }
        synchronized int  observationCount()      { return obsCount; }

        /** True once at least {@code n} rounds have been fully observed. */
        synchronized boolean targetReached(int n) {
            return completedRoundsLocked().size() >= n;
        }

        /** Snapshot the records so main's assertions cannot race the game thread. */
        synchronized void freeze() {
            if (froze) return;
            froze = true;
            frozen = new ArrayList<RoundRecord>(records);
        }

        /**
         * Rounds that reached END_ROUND after their own ACTION. A round cut short
         * by game-over is excluded (a legitimate ending, not a lifecycle
         * violation) but is still counted by {@link #partialRounds()} so the
         * exclusion is visible rather than silent.
         */
        synchronized List<RoundRecord> completedRounds() {
            return completedRoundsLocked();
        }

        private List<RoundRecord> completedRoundsLocked() {
            List<RoundRecord> source = froze ? frozen : records;
            List<RoundRecord> done = new ArrayList<RoundRecord>();
            for (int i = 0; i < source.size(); i++) {
                RoundRecord r = source.get(i);
                if (r.countOf(GamePhase.END_ROUND) >= 1 && r.countOf(GamePhase.ACTION) >= 1) {
                    done.add(r);
                }
            }
            return done;
        }

        /** Rounds the UI began but never saw END_ROUND for (game-over cut). */
        synchronized int partialRounds() {
            List<RoundRecord> source = froze ? frozen : records;
            int n = 0;
            for (int i = 0; i < source.size(); i++) {
                RoundRecord r = source.get(i);
                if (r.countOf(GamePhase.END_ROUND) < 1 && r.countOf(GamePhase.ACTION) >= 1) n++;
            }
            return n;
        }
    }
}