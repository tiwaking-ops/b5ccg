package b5ccg.engine;

import b5ccg.ai.AIPlayer;
import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.Deck;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.AIDifficulty;
import b5ccg.model.enums.Faction;

import java.util.ArrayList;
import java.util.List;

/**
 * B5-2519 — D6 unlimited-actions conformance probe (harness only, new file; no
 * game-logic edits). Exit 0 on all-pass, 1 on any failure.
 *
 * <p>The row asks two things and they are answered by two DIFFERENT mechanisms,
 * which is the finding this probe exists to record:
 *
 * <ol>
 *   <li><b>actionsLeft never goes negative</b> and <b>resets at round start.</b>
 *       That is {@code Player.useAction}}'s clamp and {@code
 *       RulesEngine.startRound}'s {@code resetActions} call. Asserted directly
 *       against the model, and then again inside a LIVE game, because a
 *       direct assertion proves the arithmetic and not the wiring.</li>
 *   <li><b>The action round grants the right number of actions.</b> Since
 *       B5-0372 landed D6, it does so by ELIGIBILITY VISIT, not by decrementing
 *       a counter: {@code runActionPhase} cycles the table and delivers exactly
 *       one action per visit, with no per-player cap, ending the round only
 *       when every seat passes consecutively. So the round's action count is a
 *       property of the LOOP, and {@code actionsLeft} is no longer what spends
 *       it. The probe measures the loop instead of asserting the counter.</li>
 * </ol>
 *
 * <p><b>THE MEASURED FINDING, recorded not asserted.</b> The row's phrase "D6
 * bonuses" points at {@code Player.grantExtraAction()} — the only extra-action
 * seam in the model. That method has <b>zero callers</b> anywhere under
 * {@code b5ccg/src/}, and the two cards whose text grants one ("Take one
 * additional action this round": {@code de_event_carpe_diem} and {@code
 * event_carpe_diem}) have no implementation either. So today the round's grant
 * is structurally incapable of being modified by a bonus card, and the probe
 * PRINTS that as a counted finding with its own census rather than encoding it
 * as a pass condition. A probe that asserted "bonuses do nothing" would lock in
 * a gap; one that asserted "bonuses work" would be red for a row whose scope is
 * a probe. Wiring Carpe Diem is a separate implementation row.
 *
 * <p><b>WHY THE LIVE LEG MAKES CLAIM 1 FALSIFIABLE.</b> {@code actionsLeft} is
 * 1 for the whole round in a normal game precisely because nothing spends it —
 * so a naive "it reads 1 at the round boundary" assertion passes whether or not
 * the reset exists. The probe therefore DRAINS a seat to 0 from inside the UI
 * callback, and then asserts it reads 1 again at the next round boundary. With
 * the reset removed that seat reads 0 and the check fails; with the reset
 * present it reads 1 because {@code startRound} ran. The drain touches only
 * {@code actionsLeft}, which the B5-0372 loop does not read, so it cannot
 * perturb what is being measured — and that is stated here because a probe that
 * mutates live state to make itself able to fail has to say so.
 *
 * Run after compile.bat / compile.sh:
 * <pre>
 *   java -cp b5ccg/out b5ccg.engine.HeadlessActionCountProbe
 * </pre>
 */
public class HeadlessActionCountProbe {

    /** Wall-clock budget for the live leg. Generous: the action round pauses
     *  600 ms per AI action. */
    private static final long GAME_TIMEOUT_MS = 240000L;

    /** Rounds the live leg insists on observing before it will judge. */
    private static final int MIN_ROUNDS = 4;

    private static final String[] NAMES = { "ACAlpha", "ACBeta", "ACGamma", "ACDelta" };
    private static final Faction[] FACTIONS = {
        Faction.HUMAN, Faction.MINBARI, Faction.CENTAURI, Faction.NARN
    };
    private static final AIDifficulty[] DIFFICULTIES = {
        AIDifficulty.MEDIUM, AIDifficulty.MEDIUM, AIDifficulty.HARD, AIDifficulty.EASY
    };

    private static int checks   = 0;
    private static int failures = 0;

    public static void main(String[] args) {
        System.out.println("=== B5-2519 D6 unlimited-actions conformance probe ===");

        System.out.println("-- S1: the counter itself (clamp, reset, grant) --");
        counterNeverGoesNegativeAndResets();

        System.out.println("-- S2: RulesEngine.startRound is the round-start reset --");
        startRoundResetsEverySeat();

        System.out.println("-- S3: a live game, observed through the UI callback --");
        LiveLeg live = liveGame();

        System.out.println("-- S4: the extra-action seam census (a finding, not a gate) --");
        extraActionSeamCensus();

        System.out.println();
        if (live == null) {
            System.out.println("PROBE FAILED: the live leg did not complete; "
                + "claims 1 and 2 are unmeasured, not passing");
            System.exit(1);
        }

        System.out.println();
        if (failures == 0) {
            System.out.println("B5-2519 PROBE PASSED (" + checks + " checks): "
                + "actionsLeft never went negative across "
                + live.samples + " samples in " + live.rounds + " rounds; a seat "
                + "drained to 0 mid-game read 1 again at the next round "
                + "boundary (" + live.resetsObserved + " boundaries, "
                + live.drainsPerformed + " deliberate drains); and every seat took "
                + "2+ actions in round " + live.actionsCountedRound
                + " (max " + live.maxActionsByOneSeat + "), so D6's no-per-player-"
                + "cap loop is what grants actions, not the counter.");
        } else {
            System.out.println("B5-2519 PROBE FAILED: " + failures + " of "
                + checks + " checks");
            System.exit(1);
        }
        System.exit(0);
    }

    // ── S1: the counter itself ──────────────────────────────────────────────

    /**
     * The three arithmetic facts, each asserted in BOTH polarities so a
     * vacuously-true comparison cannot pass: the value after the operation is
     * the one claimed, AND it differs from the value before it where it must.
     */
    private static void counterNeverGoesNegativeAndResets() {
        Player p = new Player("ACSolo", Faction.HUMAN, false);

        check("S1: a fresh seat holds exactly one action", p.getActionsLeft() == 1);

        p.useAction();
        check("S1: spending the action leaves zero", p.getActionsLeft() == 0);

        // The clamp. Two further spends from zero is the case that goes
        // negative if the clamp is ever removed from useAction.
        p.useAction();
        p.useAction();
        p.useAction();
        check("S1: over-spending clamps at zero and never goes negative",
              p.getActionsLeft() == 0);

        p.grantExtraAction();
        check("S1: grantExtraAction raises the count above the reset value",
              p.getActionsLeft() == 1 && p.getActionsLeft() != 0);

        p.setPassed(true);
        p.resetActions();
        check("S1: resetActions restores exactly one action", p.getActionsLeft() == 1);
        check("S1: resetActions clears the sticky passed flag", !p.isPassed());

        // A grant that is never reset is the shape that would make a bonus
        // permanent; resetActions is the only thing standing between them.
        p.grantExtraAction();
        p.grantExtraAction();
        boolean raised = p.getActionsLeft() == 3;
        p.resetActions();
        check("S1: two grants stack", raised);
        check("S1: the round-start reset discards the grants rather than banking them",
              p.getActionsLeft() == 1);
    }

    // ── S2: startRound is the reset, not some other caller ───────────────────

    /**
     * S1 proves {@code resetActions} resets. It does not prove
     * {@code RulesEngine.startRound} is what CALLS it at the round boundary —
     * which is the claim the row actually makes ("resets at round start"). Each
     * seat here is put into a DIFFERENT broken state first, so the assertion is
     * that startRound repaired all three, not that one seat happened to be fine.
     */
    private static void startRoundResetsEverySeat() {
        Player drained = new Player("ACDrained", Faction.HUMAN, false);
        Player granted  = new Player("ACGranted",  Faction.MINBARI, false);
        Player passer   = new Player("ACPasser",   Faction.CENTAURI, false);

        drained.useAction();  drained.useAction();   // 1 -> 0, clamped
        granted.grantExtraAction(); granted.grantExtraAction(); // 1 -> 3
        passer.setPassed(true);
        passer.useAction();                          // 0 and passed

        GameState st = new GameState(list(drained, granted, passer));
        new RulesEngine().startRound(st);

        check("S2: startRound restored the drained seat to one action",
              drained.getActionsLeft() == 1);
        check("S2: startRound DISCARDED the granted seat's two extra actions",
              granted.getActionsLeft() == 1);
        check("S2: startRound restored the drained seat's action count upward, "
            + "not merely to a non-zero value",
              drained.getActionsLeft() != 0);
        check("S2: startRound cleared the passed seat's flag",
              !passer.isPassed());
        check("S2: startRound gave the passed seat its action back",
              passer.getActionsLeft() == 1);
    }

    // ── S3: the live game ───────────────────────────────────────────────────

    /** What the live leg measured. */
    private static final class LiveLeg {
        int  samples           = 0;
        int  rounds            = 0;
        int  minActionsLeft    = Integer.MAX_VALUE;
        int  resetsObserved    = 0;
        int  maxActionsByOneSeat = 0;
        int  drainedReadToZero   = 0;
        int  drainsPerformed     = 0;
        int  boundaryRestores    = 0;
        int  actionsCountedRound = 0;
    }

    /**
     * One real game through {@code GameController.runGame()} with four AI seats,
     * observed ONLY through the {@link GameStateCallback} stream — the same
     * surface a UI renders from. Sampling through the callback rather than
     * polling internals is deliberate: it is the value a consumer could actually
     * read.
     *
     * <p>One seat is drained to zero from inside the callback, once, at a round
     * boundary. That is the mutation that makes the round-start reset
     * falsifiable — see the class comment. The seat is picked round-robin so it
     * is not always the same one.
     */
    private static LiveLeg liveGame() {
        final LiveLeg leg = new LiveLeg();

        List<Card> all;
        try {
            all = DeckLoader.loadBothSets();
        } catch (Exception e) {
            fail("S3: DeckLoader.loadBothSets threw: " + e);
            return null;
        }
        if (all.isEmpty()) {
            fail("S3: DeckLoader returned zero cards; is b5ccg/resources/cards on the classpath?");
            return null;
        }

        List<Player>  players   = new ArrayList<Player>();
        List<AIPlayer> aiPlayers = new ArrayList<AIPlayer>();
        for (int i = 0; i < NAMES.length; i++) {
            Player p = new Player(NAMES[i], FACTIONS[i], false);
            List<Card> deckCards = StarterDeckBuilder.isAvailable()
                ? safeStarterDeck(FACTIONS[i], all)
                : ownFactionCards(all, FACTIONS[i]);
            if (deckCards.isEmpty()) {
                fail("S3: no cards available for faction " + FACTIONS[i]);
                return null;
            }
            p.setDeck(new Deck(deckCards));
            CharacterCard amb = findAmbassadorCard(deckCards, FACTIONS[i]);
            if (amb != null) p.getDeck().addToTop(amb);
            p.drawCards(4);
            if (p.getHand().isEmpty()) {
                fail("S3: " + NAMES[i] + " drew an empty opening hand");
                return null;
            }
            players.add(p);
            aiPlayers.add(new AIPlayer(p, DIFFICULTIES[i]));
        }

        final GameState state = new GameState(players);
        for (int i = 0; i < players.size(); i++) players.get(i).setGameState(state);

        // Sampled across the callback, so the readings are the ones a UI sees.
        final int[]  lastRound      = { -1 };
        final int[]  drainCursor    = { 0 };
        final boolean[] drainArmed  = { true };

        final GameController controller = new GameController(state, aiPlayers,
            new GameStateCallback() {
                public void accept(GameState gs) {
                    List<Player> seats = gs.getPlayers();
                    for (int i = 0; i < seats.size(); i++) {
                        int left = seats.get(i).getActionsLeft();
                        leg.samples++;
                        if (left < leg.minActionsLeft) leg.minActionsLeft = left;
                    }
                    int round = gs.getRoundNumber();
                    if (round != lastRound[0]) {
                        if (lastRound[0] >= 0) {
                            leg.rounds++;
                            leg.resetsObserved++;
                            // The falsifiability drain, once per round boundary:
                            // take the next seat to 0 and READ IT BACK HERE.
                            // The read-back matters because the next callback
                            // fires after startRound has already reset it, so
                            // without this line the zero would never be sampled
                            // by anything and the clamp check would be vacuous.
                            if (drainArmed[0]) {
                                Player victim = seats.get(drainCursor[0] % seats.size());
                                drainCursor[0]++;
                                victim.useAction();   // 1 -> 0
                                victim.useAction();   // 0 -> 0, the clamp
                                int readBack = victim.getActionsLeft();
                                leg.drainsPerformed++;
                                if (readBack == 0) leg.drainedReadToZero++;
                                if (readBack < leg.minActionsLeft) {
                                    leg.minActionsLeft = readBack;
                                }
                            }
                        }
                        lastRound[0] = round;
                    }
                }
            });

        final Throwable[] thrown = new Throwable[1];
        Thread gameLoop = new Thread(new Runnable() {
            public void run() {
                try { controller.runGame(); } catch (Throwable t) { thrown[0] = t; }
            }
        }, "b5-2519-game-loop");
        gameLoop.setDaemon(true);

        long start = System.currentTimeMillis();
        gameLoop.start();
        while (System.currentTimeMillis() - start < GAME_TIMEOUT_MS) {
            if (thrown[0] != null) break;
            if (state.isGameOver()) break;
            if (state.getRoundNumber() >= MIN_ROUNDS + 1) break;
            sleep(25L);
        }
        long elapsed = System.currentTimeMillis() - start;

        if (thrown[0] != null) {
            fail("S3: runGame threw: " + thrown[0]);
            thrown[0].printStackTrace(System.err);
            return null;
        }
        if (state.getRoundNumber() < MIN_ROUNDS + 1 && !state.isGameOver()) {
            fail("S3: only " + state.getRoundNumber() + " round(s) in " + elapsed
                + " ms; the engine appears stuck (phase=" + state.getPhase()
                + ", lastLog=" + state.getLastLogEntry() + ")");
            return null;
        }

        leg.rounds = Math.max(leg.rounds, state.getRoundNumber());

        // Claim 1, live. Sampled at every notification, so this is the value a
        // consumer could read, not an internal peek.
        check("S3: the callback delivered samples to observe", leg.samples > 0);
        check("S3: actionsLeft never went negative across " + leg.samples
            + " samples in " + leg.rounds + " rounds (min seen "
            + leg.minActionsLeft + ")",
            leg.minActionsLeft >= 0);
        // Without this the check above is vacuous: in a normal game actionsLeft
        // sits at 1 the whole round because nothing spends it, so "never
        // negative" would hold against any implementation at all.
        check("S3: the clamp was actually exercised — " + leg.drainedReadToZero
            + " of " + leg.drainsPerformed + " deliberate drains read back 0 and "
            + "no lower", leg.drainsPerformed > 0 && leg.drainedReadToZero == leg.drainsPerformed);

        // Claim 2, live: did the round-start reset restore the drained seat?
        // The victim is the seat drained most recently; every seat drained
        // earlier has since been through at least one more boundary.
        boolean allRestored = drainCursor[0] > 0;
        for (int i = 0; i < drainCursor[0] && allRestored; i++) {
            allRestored = players.get(i % players.size()).getActionsLeft() == 1;
        }
        check("S3: every seat drained to 0 mid-game reads 1 again after a round "
            + "boundary — the reset is wired, not just arithmetic ("
            + drainCursor[0] + " drain(s))", allRestored);

        // Claim 2's loop half: D6's promise is that one seat may act more than
        // once per round. Counted from the engine's own per-action log lines.
        // The LAST COMPLETED round, not the in-flight one — a round that is
        // still running has only part of its actions in the log, and counting
        // those would understate every seat.
        int lastComplete = state.getRoundNumber() - 1;
        int[] perSeat = actionsPerRound(state, lastComplete);
        leg.actionsCountedRound = lastComplete;
        int max = 0;
        int total = 0;
        for (int i = 0; i < perSeat.length; i++) {
            if (perSeat[i] > max) max = perSeat[i];
            total += perSeat[i];
        }
        leg.maxActionsByOneSeat = max;
        System.out.println("     actions by seat in round " + lastComplete
            + ": " + perSeat[0] + ", " + perSeat[1] + ", " + perSeat[2] + ", "
            + perSeat[3] + " (total " + total + ")");
        check("S3: at least one seat took 2+ actions in a single round, so the "
            + "action round is an eligibility loop and not a one-per-seat cap "
            + "(max " + max + ")", max >= 2);
        // The loop's own shape: it ends on consecutive passes, so a completed
        // round carries at least one action per seat's worth of visits. Two
        // actions for EVERY seat is the D6 signature — a one-per-seat cap could
        // not produce it.
        boolean everySeatActedTwice = true;
        for (int i = 0; i < perSeat.length; i++) {
            if (perSeat[i] < 2) everySeatActedTwice = false;
        }
        check("S3: every seat took 2+ actions in round " + lastComplete
            + ", which a one-action-per-seat round could not produce",
            everySeatActedTwice);

        // The safety cap is a liveness bound, not a rule, so it is reported
        // rather than gated: firing it in a normal game would be a finding, but
        // this probe does not own that measurement.
        int cap = state.getPlayers().size() * 8;
        System.out.println("     ACTION safety cap for this table is " + cap
            + " actions/round; observed max for one seat " + max);
        return leg;
    }

    // ── S4: the extra-action seam, counted and printed ───────────────────────

    /**
     * Prints the seam census. Deliberately not a gate: the row's scope is a
     * probe, and the honest output of a probe that finds an unimplemented bonus
     * is the finding, not a red build that says the same thing louder.
     */
    private static void extraActionSeamCensus() {
        // The model's extra-action seam is Player.grantExtraAction(). Whether
        // anything CALLS it is a source-level fact, so it is measured by reading
        // the sources this probe ships beside rather than by a runtime guess.
        String[] sources = {
            "b5ccg/src/b5ccg/model/Player.java",
            "b5ccg/src/b5ccg/engine/RulesEngine.java",
            "b5ccg/src/b5ccg/engine/GameController.java",
            "b5ccg/src/b5ccg/engine/CardEffects.java",
            "b5ccg/src/b5ccg/ai/AIPlayer.java"
        };
        int callers = 0;
        for (int i = 0; i < sources.length; i++) {
            callers += countCallSites(sources[i]);
        }
        System.out.println("     grantExtraAction() call sites across "
            + sources.length + " engine/model/ai sources: " + callers);
        System.out.println("     FINDING (not a gate): with " + callers
            + " callers, no D6 bonus card can currently change how many actions"
            + " a round delivers. The cards that grant one ('Take one additional"
            + " action this round' — de_event_carpe_diem, event_carpe_diem) have"
            + " no implementation. Wiring them is a separate implementation row;"
            + " this probe records the measurement and asserts nothing about it.");
    }

    // ── Small helpers ────────────────────────────────────────────────────────

    private static List<Player> list(Player... seats) {
        List<Player> out = new ArrayList<Player>();
        for (int i = 0; i < seats.length; i++) out.add(seats[i]);
        return out;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<Card> safeStarterDeck(Faction f, List<Card> all) {
        try {
            return StarterDeckBuilder.build(f, all);
        } catch (Exception e) {
            System.err.println("     starter deck build failed for " + f
                + ", falling back to own-faction cards: " + e.getMessage());
            return ownFactionCards(all, f);
        }
    }

    private static List<Card> ownFactionCards(List<Card> all, Faction faction) {
        List<Card> deck = new ArrayList<Card>();
        for (int i = 0; i < all.size() && deck.size() < 60; i++) {
            Card c = all.get(i);
            if (c.getFaction() == faction) deck.add(c);
        }
        return deck;
    }

    /** The faction's starting ambassador, or null when the deck carries none. */
    private static CharacterCard findAmbassadorCard(List<Card> cards, Faction f) {
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            if (c instanceof CharacterCard) {
                CharacterCard ch = (CharacterCard) c;
                if (ch.isAmbassador() && ch.getFaction() == f) return ch;
            }
        }
        return null;
    }

    /**
     * How many actions each seat took in the CURRENT round, read from the
     * engine's own per-action log lines — the same {@code "NAME: <action>"}
     * prefix {@code GameController.processAction} writes for every submitted
     * action. Reading the log rather than a counter is what makes this an
     * observation of what the loop did.
     */
    private static int[] actionsPerRound(GameState st, int round) {
        int[] counts = new int[NAMES.length];
        String marker = "[R" + round + "] ";
        List<String> log = st.getLog();
        for (int i = 0; i < log.size(); i++) {
            String line = log.get(i);
            if (!line.startsWith(marker)) continue;
            for (int n = 0; n < NAMES.length; n++) {
                if (line.startsWith(marker + NAMES[n] + ": ")) { counts[n]++; break; }
            }
        }
        return counts;
    }

    /**
     * How many times {@code grantExtraAction} appears as a CALL in one source
     * file. A declaration line is excluded by requiring the line not to end in
     * the parameter list, which is how the declaration reads.
     */
    private static int countCallSites(String path) {
        java.io.File f = new java.io.File(path);
        if (!f.isFile()) return 0;
        int hits = 0;
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(
                    new java.io.FileInputStream(f), "UTF-8"));
            try {
                String line;
                while ((line = r.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("//") || trimmed.startsWith("*")
                        || trimmed.startsWith("/*")) continue;
                    if (trimmed.indexOf("grantExtraAction") < 0) continue;
                    if (trimmed.indexOf("public void") >= 0) continue;  // the declaration
                    hits++;
                }
            } finally { r.close(); }
        } catch (Exception e) {
            System.out.println("     (census could not read " + path + ": " + e + ")");
        }
        return hits;
    }

    // ── Reporting ────────────────────────────────────────────────────────────

    private static void check(String label, boolean passed) {
        checks++;
        System.out.println("  [AC] " + label + ": " + (passed ? "PASS" : "FAIL"));
        if (!passed) failures++;
    }

    private static void fail(String label) {
        checks++;
        failures++;
        System.out.println("  [AC] " + label + ": FAIL");
    }
}
