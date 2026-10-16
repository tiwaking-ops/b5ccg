package b5ccg.engine;

import b5ccg.model.AgendaCard;
import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.Deck;
import b5ccg.model.EventCard;
import b5ccg.model.FleetCard;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.GamePhase;
import b5ccg.model.enums.Rarity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * B5-2566 - GATING variant of the round-cap reporting tiebreak (Option C).
 *
 * <p>Why this file exists. {@link HeadlessReportingTiebreakTest} is a
 * report-only probe: B5-0822 recorded that it does not gate, and
 * <code>compile.sh</code> carries the measurement for why it cannot -
 * 15.4 s, and its live round-cap leg reads NOT_APPLICABLE (0/4 players at
 * 20+) on an ordinary run because an AI game almost never produces the
 * 20-20 round-cap stall the chain is written for. A probe whose interesting
 * arm does not fire is not a gate; it is a report that prints PASS.
 *
 * <p>What this file asks instead. The one property that makes the reporting
 * chain trustworthy is that it RESOLVES DETERMINISTICALLY: given the same
 * board it must always answer the same way, because it exists to replace a
 * coin-flip with a reproducible chain (steps 1-3, then a declared shared
 * victory). So this gate evaluates each of seven synthetic fixtures
 * {@link #REPS} times and demands the answer be byte-identical every time:
 * same kind, same step, same winners in the same order, same detail line.
 * Any divergence is a failure and the process exits non-zero.
 *
 * <p>Why synthetic-only, deliberately. A live {@code GameController} run
 * cannot be a determinism fixture: <code>b5ccg/model/Deck.java</code>
 * shuffles its draw pile from an unseeded <code>Collections.shuffle</code>,
 * so two live runs differ before any rule executes. This gate therefore
 * takes no threads, no real decks, no I/O and no wall-clock budget - it is
 * hundreds of cheap evaluations of {@link
 * HeadlessReportingTiebreakTest#evaluateAtRoundCap} over hand-built boards,
 * so its runtime is milliseconds and its exit code is pure: 0 clean, 1 any
 * non-deterministic result. Nothing here edits that test, the reporter, or
 * any other engine class; this file only CALLS the published static method.
 *
 * <p>The three ways this gate could lie to itself, each closed below:
 * <ul>
 *   <li><b>A comparator that can never say "different".</b> {@code
 *       sectionSensitivity} feeds the SAME comparison five input pairs one
 *       step apart and requires them to read different - including the pair
 *       where only one point of fleet Military moved, which is the whole
 *       point of chain step 1.</li>
 *   <li><b>A sweep that can never go red.</b> {@code sectionSelfTest} pushes
 *       a deliberately alternating outcome through the identical sweep loop
 *       and requires it to be REPORTED as non-deterministic, then pushes a
 *       constant outcome and requires it NOT to be. Both receipts print on
 *       every run; a gate never observed red is unproven (B5-1725).</li>
 *   <li><b>A fixture that never distinguishes the answers.</b> Every
 *       fixture pins its expected kind, step and winner list explicitly
 *       ({@code expect}), so a change that quietly moved an outcome from
 *       SHARED to SINGLE - the interesting direction, since it is what the
 *       chain is for - fails here rather than passing silently.</li>
 * </ul>
 *
 * <p>New file. Java 6 only (no lambdas, no diamond, no try-with-resources,
 * no strings in switch). Run after a build:
 * <pre>
 *   java -cp b5ccg/out b5ccg.engine.HeadlessReportingTiebreakGate
 * </pre>
 * Exit 0 clean, 1 non-deterministic or otherwise failed. Not wired into
 * <code>compile.sh</code> by this row: that file is outside the claimed
 * scope, so the wiring is left for its own row.
 */
public class HeadlessReportingTiebreakGate {

    /** Evaluations per fixture in the determinism sweep. */
    private static final int REPS = 200;

    private static int failures = 0;
    private static int checks = 0;
    private static int evaluations = 0;
    private static int nullReports = 0;

    public static void main(String[] args) {
        System.out.println("=== B5 CCG reporting-tiebreak DETERMINISM gate (B5-2566) ===");
        System.out.println("reps per fixture: " + REPS
                + " - synthetic boards only, no threads, no live game loop, no unseeded deck");

        List<Summary> anchors = new ArrayList<Summary>();
        sectionFixtures(anchors);
        sectionKindCoverage(anchors);
        sectionSeatOrder();
        sectionSensitivity();
        sectionContract();

        int realFailures = failures;
        int realEvaluations = evaluations;
        failures = 0;
        boolean selfTestOk = sectionSelfTest();
        failures = realFailures;
        evaluations = realEvaluations;

        System.out.println();
        System.out.println("checks: " + checks + ", tiebreak evaluations: " + evaluations);
        System.out.println("real failures: " + realFailures
                + ", self-test: " + (selfTestOk
                        ? "OK - non-determinism IS reported, stability is not"
                        : "FAILED - the gate could not prove it can go red"));
        System.out.println(realFailures == 0 && selfTestOk
                ? "REPORTING-TIEBREAK DETERMINISM GATE PASSED"
                : "REPORTING-TIEBREAK DETERMINISM GATE FAILED (" + realFailures + " failure(s))");
        System.out.flush();
        System.err.flush();
        System.exit(realFailures == 0 && selfTestOk ? 0 : 1);
    }

    // ==================================================================
    // 1. Fixtures: each pinned expectation plus a REPS-wide sweep
    // ==================================================================

    private static void sectionFixtures(List<Summary> anchors) {
        head("Fixtures: each board's expected chain outcome, and " + REPS
                + " evaluations of it");
        Scenario[] all = new Scenario[] {
            sharedPair(), sharedTriple(), militaryDecides(), innerCircleDecides(),
            influenceGainedDecides(), engineAgendaWinner(), belowTriggerGate()
        };
        for (int i = 0; i < all.length; i++) {
            Summary first = sweep(all[i]);
            anchors.add(first);
            expect(all[i], first);
        }
    }

    /** Winner set (order-independent) invariance across every rotation of seat order. */
    private static void sectionSeatOrder() {
        head("Seat order: the ANSWER must not ride on how the seats were built");
        Scenario[] multi = new Scenario[] {
            sharedPair(), sharedTriple(), militaryDecides(), innerCircleDecides(),
            influenceGainedDecides(), engineAgendaWinner(), belowTriggerGate()
        };
        // A second kind of determinism: the same three boards built in every
        // rotation of construction order must still agree on WHO is in the
        // winner set, even though the winner LIST order follows seat order
        // (so an implementation picking "first in list" would move the answer).
        for (int i = 0; i < multi.length; i++) {
            String expected = null;
            boolean invariant = true;
            int seats = multi[i].seats();
            String observed = null;
            for (int r = 0; r < seats; r++) {
                Summary s = multi[i].observe(r).summary;
                String key = s.winnerSet();
                if (expected == null) {
                    expected = key;
                } else if (!expected.equals(key)) {
                    invariant = false;
                    observed = key;
                }
            }
            check(multi[i].label + ": winner SET invariant across " + seats
                    + " seat rotations", invariant);
            if (!invariant) {
                System.out.println("      expected set: " + expected
                        + "\n      rotated set:  " + observed);
            }
        }
    }

    /**
     * The anti-silent-green half: every pair below differs by exactly one
     * turn's worth of input, and each MUST read differently through the same
     * comparison the sweep uses. If sameAs were a constant true, every sweep
     * above would have passed and said nothing.
     */
    private static void sectionSensitivity() {
        head("Sensitivity: fixtures one step apart must be SEEN as different");
        Summary tiedPair = sharedPair().observe().summary;
        Comparison[] pairs = new Comparison[] {
            pair("same board evaluated twice", tiedPair, sharedPair().observe().summary, true),
            pair("one ready fleet added (step 1 moves)", tiedPair,
                    militaryDecides().observe().summary, false),
            pair("a higher run-start baseline for one seat (step 3 moves)", tiedPair,
                    influenceGainedDecides().observe().summary, false),
            pair("an agenda win precedes the whole chain", tiedPair,
                    engineAgendaWinner().observe().summary, false),
            pair("both seats dropped under 20 (chain becomes N/A)", tiedPair,
                    belowTriggerGate().observe().summary, false),
            pair("a third tied seat widens the shared win", tiedPair,
                    sharedTriple().observe().summary, false),
            pair("a different deciding step (1 vs 2)",
                    militaryDecides().observe().summary,
                    innerCircleDecides().observe().summary, false)
        };
        int distinguishable = 0;
        for (int i = 0; i < pairs.length; i++) {
            boolean equal = pairs[i].a.sameAs(pairs[i].b);
            check(pairs[i].label + " -> compared " + (equal ? "EQUAL" : "DIFFERENT")
                    + " (expected " + (pairs[i].expectEqual ? "EQUAL" : "DIFFERENT") + ")",
                    equal == pairs[i].expectEqual);
            if (!equal) distinguishable++;
            else if (!pairs[i].expectEqual && pairs[i].a != null && pairs[i].b != null) {
                System.out.println("      both sides: " + pairs[i].a.line());
            }
        }
        // The number itself is the point: sameAs returning "equal" for
        // everything would print every line above as EQUAL and fail here.
        check("sameAs is not a constant: " + distinguishable + " of " + pairs.length
                + " pairs read DIFFERENT (expected " + (pairs.length - 1) + ")",
                distinguishable == pairs.length - 1);
    }

    /** Argument contract of the reporter, and the read-only promise it makes. */
    private static void sectionContract() {
        head("Contract: refusal is deterministic too, and the board is not touched");
        check("a null state is refused, not silently tolerated", throwsOnNullState());
        check("a baseline that is not parallel to the seats is refused", throwsOnBadBaseline());

        // Determinism of the refusal itself: ten calls, ten throws.
        boolean everyTime = true;
        for (int i = 0; i < 10; i++) if (!throwsOnNullState()) everyTime = false;
        check("the refusal is deterministic (10 attempts, 10 refusals)", everyTime);

        // Read-only: the reporting layer may inspect, never mutate.
        Player a = seat("Alpha", Faction.NARN);
        Player b = seat("Beta", Faction.MINBARI);
        toInfluence(a, 20);
        toInfluence(b, 20);
        a.addFleet(fleet("gate_ro_a", 5));
        b.addFleet(fleet("gate_ro_b", 3));
        GameState st = state(order(0, a, b));
        int[] base = baselines(st.getPlayers(), "Alpha", 4, "Beta", 4);
        String before = boardSignature(st);
        Summary first = null;
        boolean stable = true;
        for (int i = 0; i < 100; i++) {
            Summary s = new Summary(HeadlessReportingTiebreakTest.evaluateAtRoundCap(
                    st, new RulesEngine(), base));
            evaluations++;
            if (first == null) {
                first = s;
            } else if (!first.sameAs(s)) {
                stable = false;
            }
        }
        check("100 evaluations of one frozen board are identical", stable);
        check("the board is unchanged by the reporting layer", before.equals(boardSignature(st)));
        check("no winner was recorded into GameState by the reporter", st.getWinner() == null);
    }

    /** Every Kind the reporter can emit is exercised by some fixture here. */
    private static void sectionKindCoverage(List<Summary> anchors) {
        head("Coverage: every Kind the reporter can emit is reached by a fixture");
        HeadlessReportingTiebreakTest.Kind[] values = HeadlessReportingTiebreakTest.Kind.values();
        List<String> missing = new ArrayList<String>();
        for (int i = 0; i < values.length; i++) {
            boolean seen = false;
            for (int j = 0; j < anchors.size(); j++) {
                if (anchors.get(j).kind == values[i]) seen = true;
            }
            if (!seen) missing.add(values[i].name());
        }
        check("all " + values.length + " Kinds are covered by these fixtures (missing: "
                + (missing.isEmpty() ? "none" : join(missing, ", ")) + ")", missing.isEmpty());

        boolean everyFormed = true;
        int nulls = 0;
        for (int j = 0; j < anchors.size(); j++) {
            Summary s = anchors.get(j);
            if (s == null) nulls++;
            else if (!s.wellFormed) everyFormed = false;
        }
        check("every anchored report is well-formed", everyFormed);
        check("evaluateAtRoundCap never returned null (" + nulls + " null anchor(s), "
                + nullReports + " null call(s))", nulls == 0 && nullReports == 0);
    }

    // ==================================================================
    // 2. Self-test: prove the sweep can go red, and that it does not cry wolf
    // ==================================================================

    private static boolean sectionSelfTest() {
        head("SELF-TEST: the sweep must go red on a moved answer, and stay green on none");

        Scenario flip = new Scenario("self-test: a source that alternates every call",
                HeadlessReportingTiebreakTest.Kind.SINGLE, 1, "Alpha") {
            private int calls = 0;
            Observation observe(int rotation) {
                calls++;
                Summary s = (calls % 2 == 1)
                        ? new Summary(HeadlessReportingTiebreakTest.Kind.SINGLE, 1,
                                new String[] { "Alpha" }, "self-test answer A", true)
                        : new Summary(HeadlessReportingTiebreakTest.Kind.SINGLE, 1,
                                new String[] { "Beta" }, "self-test answer B", true);
                return new Observation(s, null);
            }
            int seats() { return 1; }
        };
        int before = failures;
        sweep(flip);
        boolean caught = failures > before;
        failures = before;
        check("an alternating outcome IS reported as non-deterministic (red is reachable)",
                caught);

        Scenario steady = new Scenario("self-test: a source that never changes",
                HeadlessReportingTiebreakTest.Kind.SHARED, 4, "Alpha,Beta") {
            Observation observe(int rotation) {
                return new Observation(new Summary(
                        HeadlessReportingTiebreakTest.Kind.SHARED, 4,
                        new String[] { "Alpha", "Beta" }, "self-test steady answer", true), null);
            }
            int seats() { return 2; }
        };
        before = failures;
        sweep(steady);
        boolean clean = failures == before;
        failures = before;
        check("an unchanging outcome is NOT reported as non-deterministic (no false positive)",
                clean);

        return caught && clean;
    }

    // ==================================================================
    // 3. The sweep itself
    // ==================================================================

    /**
     * Evaluates one fixture {@link #REPS} times - each {@code observe()} call
     * builds a FRESH board unless the fixture deliberately reuses one - and
     * requires every answer to match the first exactly.
     */
    private static Summary sweep(Scenario scn) {
        Summary first = null;
        String expectedLine = null;
        String divergentLine = null;
        int divergedAt = -1;
        boolean formed = true;
        boolean stable = true;
        for (int i = 0; i < REPS; i++) {
            Observation ob = scn.observe();
            if (ob.summary == null) { formed = false; continue; }
            if (!ob.summary.wellFormed) formed = false;
            if (first == null) {
                first = ob.summary;
                expectedLine = first.line();
            } else if (!first.sameAs(ob.summary)) {
                stable = false;
                if (divergedAt < 0) {
                    divergedAt = i;
                    divergentLine = ob.summary.line();
                }
            }
        }
        check(scn.label + ": the reporter never returns a malformed report", formed);
        check(scn.label + ": " + REPS
                + " evaluations identical in kind, step, winners and detail", stable);
        if (!stable) {
            System.out.println("      first divergence at repetition " + divergedAt);
            System.out.println("      expected: " + expectedLine);
            System.out.println("      observed: " + divergentLine);
        }
        return first;
    }

    /** Pins the outcome a fixture is built to produce, so silence cannot hide a move. */
    private static void expect(Scenario scn, Summary actual) {
        boolean ok = actual != null && actual.kind == scn.expectedKind
                && actual.step == scn.expectedStep
                && actual.winnerSet().equals(sortedCsv(scn.expectedWinners));
        check(scn.label + ": resolves as " + scn.expectedKind + " step=" + scn.expectedStep
                + " winners={" + csv(scn.expectedWinners) + "}", ok);
        if (!ok && actual != null) System.out.println("      actual: " + actual.line());
    }

    // ==================================================================
    // 4. Fixtures
    // ==================================================================

    /** Two identical 20-20 boards: nothing separates them, step 4 shares the win. */
    private static Scenario sharedPair() {
        return new Scenario("SHARED pair (identical 20-20 boards)",
                HeadlessReportingTiebreakTest.Kind.SHARED, 4, "Alpha,Beta") {
            Observation observe(int rotation) {
                Player a = seat("Alpha", Faction.NARN);
                Player b = seat("Beta", Faction.MINBARI);
                toInfluence(a, 20);
                toInfluence(b, 20);
                List<Player> seats = order(rotation, a, b);
                GameState st = state(seats);
                return report(st, baselines(seats, "Alpha", 4, "Beta", 4));
            }
            int seats() { return 2; }
        };
    }

    /** Three identical boards: the shared win must name all three. */
    private static Scenario sharedTriple() {
        return new Scenario("SHARED triple (three identical boards)",
                HeadlessReportingTiebreakTest.Kind.SHARED, 4, "Alpha,Beta,Gamma") {
            Observation observe(int rotation) {
                Player a = seat("Alpha", Faction.NARN);
                Player b = seat("Beta", Faction.MINBARI);
                Player c = seat("Gamma", Faction.CENTAURI);
                toInfluence(a, 20);
                toInfluence(b, 20);
                toInfluence(c, 20);
                List<Player> seats = order(rotation, a, b, c);
                GameState st = state(seats);
                return report(st, baselines(seats, "Alpha", 4, "Beta", 4, "Gamma", 4));
            }
            int seats() { return 3; }
        };
    }

    /**
     * Step 1. The winner is deliberately the SECOND seat, so an
     * implementation that answered with the first contender would have to
     * move past this fixture to be wrong.
     */
    private static Scenario militaryDecides() {
        return new Scenario("SINGLE step 1 (fleet Military, second seat wins)",
                HeadlessReportingTiebreakTest.Kind.SINGLE, 1, "Beta") {
            Observation observe(int rotation) {
                Player a = seat("Alpha", Faction.NARN);
                Player b = seat("Beta", Faction.MINBARI);
                toInfluence(a, 20);
                toInfluence(b, 20);
                a.addFleet(fleet("gate_mil_a", 3));
                b.addFleet(fleet("gate_mil_b", 5));
                List<Player> seats = order(rotation, a, b);
                GameState st = state(seats);
                return report(st, baselines(seats, "Alpha", 4, "Beta", 4));
            }
            int seats() { return 2; }
        };
    }

    /** Step 2, reached only after step 1 has narrowed three seats to two. */
    private static Scenario innerCircleDecides() {
        return new Scenario("SINGLE step 2 (Inner Circle size after step-1 narrowing)",
                HeadlessReportingTiebreakTest.Kind.SINGLE, 2, "Beta") {
            Observation observe(int rotation) {
                Player a = seat("Alpha", Faction.NARN);
                Player b = seat("Beta", Faction.MINBARI);
                Player c = seat("Gamma", Faction.CENTAURI);
                toInfluence(a, 20);
                toInfluence(b, 20);
                toInfluence(c, 20);
                a.addFleet(fleet("gate_ic_a", 3));
                b.addFleet(fleet("gate_ic_b", 3));
                c.addFleet(fleet("gate_ic_c", 1));
                b.addCharacter(member("gate_ic_m1", 4));
                b.addCharacter(member("gate_ic_m2", 3));
                List<Player> seats = order(rotation, a, b, c);
                GameState st = state(seats);
                return report(st, baselines(seats, "Alpha", 4, "Beta", 4, "Gamma", 4));
            }
            int seats() { return 3; }
        };
    }

    /**
     * Step 3. Boards are identical at 20 influence; only the run-start
     * baseline differs, so the chain must read "who gained more this run".
     */
    private static Scenario influenceGainedDecides() {
        return new Scenario("SINGLE step 3 (influence gained over the run)",
                HeadlessReportingTiebreakTest.Kind.SINGLE, 3, "Alpha") {
            Observation observe(int rotation) {
                Player a = seat("Alpha", Faction.NARN);
                Player b = seat("Beta", Faction.MINBARI);
                toInfluence(a, 20);
                toInfluence(b, 20);
                List<Player> seats = order(rotation, a, b);
                GameState st = state(seats);
                // Alpha started at 4 (gained 16); Beta started higher, at 12 (gained 8).
                return report(st, baselines(seats, "Alpha", 4, "Beta", 12));
            }
            int seats() { return 2; }
        };
    }

    /** Engine precedence: an agenda win at 20 must outrank the whole chain. */
    private static Scenario engineAgendaWinner() {
        return new Scenario("ENGINE_WINNER (agenda INFLUENCE_20 precedes the chain)",
                HeadlessReportingTiebreakTest.Kind.ENGINE_WINNER, -1, "Alpha") {
            Observation observe(int rotation) {
                Player a = seat("Alpha", Faction.NARN);
                Player b = seat("Beta", Faction.MINBARI);
                toInfluence(a, 20);
                toInfluence(b, 20);
                a.setAgenda(agendaTwenty());
                List<Player> seats = order(rotation, a, b);
                GameState st = state(seats);
                return report(st, baselines(seats, "Alpha", 4, "Beta", 4));
            }
            int seats() { return 2; }
        };
    }

    /** Trigger gate: below two seats at 20+ this is not the Option C stall. */
    private static Scenario belowTriggerGate() {
        return new Scenario("NOT_APPLICABLE (fewer than two seats at 20+)",
                HeadlessReportingTiebreakTest.Kind.NOT_APPLICABLE, -1, "") {
            Observation observe(int rotation) {
                Player a = seat("Alpha", Faction.NARN);
                Player b = seat("Beta", Faction.MINBARI);
                toInfluence(a, 19);
                toInfluence(b, 19);
                List<Player> seats = order(rotation, a, b);
                GameState st = state(seats);
                return report(st, baselines(seats, "Alpha", 4, "Beta", 4));
            }
            int seats() { return 2; }
        };
    }

    // ==================================================================
    // 5. Harness
    // ==================================================================

    /** One cast possible outcome of evaluateAtRoundCap, compared by VALUE not identity. */
    private static final class Summary {
        final HeadlessReportingTiebreakTest.Kind kind;
        final int step;
        final List<String> winners;
        final String detail;
        final boolean wellFormed;

        Summary(HeadlessReportingTiebreakTest.TiebreakReport rep) {
            this.kind = rep.kind;
            this.step = rep.step;
            this.detail = rep.detail;
            this.wellFormed = rep.isWellFormed();
            this.winners = new ArrayList<String>();
            for (int i = 0; i < rep.winners.size(); i++) {
                this.winners.add(rep.winners.get(i).getName());
            }
        }

        /** Self-test source only: a Summary no TiebreakReport ever produced. */
        Summary(HeadlessReportingTiebreakTest.Kind kind, int step, String[] winners,
                String detail, boolean wellFormed) {
            this.kind = kind;
            this.step = step;
            this.detail = detail;
            this.wellFormed = wellFormed;
            this.winners = new ArrayList<String>();
            for (int i = 0; i < winners.length; i++) {
                if (winners[i].length() > 0) this.winners.add(winners[i]);
            }
        }

        boolean sameAs(Summary other) {
            if (other == null) return false;
            return kind == other.kind && step == other.step
                    && detail.equals(other.detail) && winners.equals(other.winners);
        }

        /** Order-independent winner key (seat-invariant part of the answer). */
        String winnerSet() {
            List<String> sorted = new ArrayList<String>(winners);
            Collections.sort(sorted);
            return join(sorted, ",");
        }

        String line() {
            return kind + " step=" + step + " winners=" + join(winners, ",")
                    + " detail={" + detail + "}";
        }
    }

    /** Two answers plus how they are expected to compare (used by sectionSensitivity). */
    private static final class Comparison {
        final String label;
        final Summary a;
        final Summary b;
        final boolean expectEqual;
        Comparison(String label, Summary a, Summary b, boolean expectEqual) {
            this.label = label;
            this.a = a;
            this.b = b;
            this.expectEqual = expectEqual;
        }
    }

    private static final class Observation {
        final Summary summary;
        final GameState state;
        Observation(Summary summary, GameState state) {
            this.summary = summary;
            this.state = state;
        }
    }

    /**
     * A board plus the answer expected from it. {@code observe} builds a FRESH
     * board on every call, so the sweep exercises reconstruction as well as
     * repeated evaluation - a hidden dependence on anything constructed along
     * the way (an unseeded deck shuffle, for example) would surface here.
     */
    private abstract static class Scenario {
        final String label;
        final HeadlessReportingTiebreakTest.Kind expectedKind;
        final int expectedStep;
        final String[] expectedWinners;

        Scenario(String label, HeadlessReportingTiebreakTest.Kind expectedKind,
                 int expectedStep, String csvWinners) {
            this.label = label;
            this.expectedKind = expectedKind;
            this.expectedStep = expectedStep;
            this.expectedWinners = splitCsv(csvWinners);
        }

        abstract Observation observe(int rotation);

        Observation observe() { return observe(0); }

        /** How many distinct seat orders this fixture can be built in. */
        abstract int seats();
    }

    /** One evaluation of the reporting chain, counted and compared by VALUE. */
    private static Observation report(GameState st, int[] baseline) {
        evaluations++;
        HeadlessReportingTiebreakTest.TiebreakReport rep =
                HeadlessReportingTiebreakTest.evaluateAtRoundCap(st, new RulesEngine(), baseline);
        if (rep == null) {
            // Asserted once per section rather than per evaluation: a null here
            // would otherwise print REPS identical FAIL lines and bury everything.
            nullReports++;
            return new Observation(null, st);
        }
        return new Observation(new Summary(rep), st);
    }

    // ------------------------------------------------------------------
    // Modeled board helpers
    // ------------------------------------------------------------------

    private static Player seat(String name, Faction f) {
        Player p = new Player(name, f, false);
        CharacterCard amb = new CharacterCard("gate_amb_" + name, "Amb " + name,
                "CHARACTER_" + f, Rarity.FIXED, f, CardSet.PREMIERE, "x", "text",
                3, 3, 3, 3, true);
        p.setAmbassador(amb);
        p.getInnerCircle().add(amb);
        List<Card> filler = new ArrayList<Card>();
        for (int i = 0; i < 10; i++) {
            filler.add(new EventCard("gate_deck_" + name + "_" + i, "Deck " + i, "EVENT",
                    Rarity.COMMON, Faction.ANY, CardSet.PREMIERE, "x", "text"));
        }
        p.setDeck(new Deck(filler));
        return p;
    }

    /** Moves a seat to an exact influence rating (Player starts at 4). */
    private static void toInfluence(Player p, int target) {
        int need = target - p.getInfluence();
        if (need > 0) p.gainInfluence(need);
        else if (need < 0) p.loseInfluence(-need);
    }

    private static FleetCard fleet(String id, int military) {
        return new FleetCard(id, id, "FLEET", Rarity.COMMON, Faction.ANY,
                CardSet.PREMIERE, "x", "text", military);
    }

    private static CharacterCard member(String id, int leadership) {
        return new CharacterCard(id, id, "CHARACTER_NARN", Rarity.RARE, Faction.NARN,
                CardSet.PREMIERE, "x", "text", 2, 2, 0, leadership, false);
    }

    private static AgendaCard agendaTwenty() {
        return new AgendaCard("gate_agenda20", "Reach Twenty", "AGENDA", Rarity.RARE,
                Faction.NARN, CardSet.PREMIERE, "x", "text", false, "INFLUENCE_20");
    }

    private static GameState state(List<Player> players) {
        GameState s = new GameState(players);
        s.setPhase(GamePhase.ACTION);
        return s;
    }

    /** The same seats, rotated r positions in construction order. */
    private static List<Player> order(int rotation, Player... seats) {
        List<Player> src = Arrays.asList(seats);
        List<Player> out = new ArrayList<Player>();
        for (int i = 0; i < src.size(); i++) {
            out.add(src.get((i + rotation) % src.size()));
        }
        return out;
    }

    /**
     * Run-start baselines, keyed by SEAT NAME so a rotation cannot smuggle a
     * different baseline into a different seat. A missing key is refused
     * rather than defaulted: a silent default here would invent a run history.
     * Arguments alternate name / baseline, which is why they arrive as
     * Object rather than String.
     */
    private static int[] baselines(List<Player> players, Object... nameValuePairs) {
        Map<String, Integer> table = new HashMap<String, Integer>();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            table.put((String) nameValuePairs[i],
                    Integer.valueOf(((Integer) nameValuePairs[i + 1]).intValue()));
        }
        int[] out = new int[players.size()];
        for (int i = 0; i < players.size(); i++) {
            String key = players.get(i).getName();
            Integer v = table.get(key);
            if (v == null) {
                throw new IllegalArgumentException(
                        "no baseline declared for seat " + key);
            }
            out[i] = v.intValue();
        }
        return out;
    }

    /** One sensitivity pair: two answers and whether they should compare equal. */
    private static Comparison pair(String label, Summary a, Summary b, boolean expectEqual) {
        return new Comparison(label, a, b, expectEqual);
    }

    /** Everything the reporter could plausibly write to, as one string. */
    private static String boardSignature(GameState st) {
        StringBuilder sb = new StringBuilder();
        List<Player> ps = st.getPlayers();
        for (int i = 0; i < ps.size(); i++) {
            Player p = ps.get(i);
            sb.append(p.getName()).append(":").append(p.getInfluence())
              .append(":fleets=").append(p.getFleets().size())
              .append(":ic=").append(p.getInnerCircle().size())
              .append(":agenda=").append(p.getAgenda() == null ? "none" : p.getAgenda().getId())
              .append(";");
        }
        sb.append("round=").append(st.getRoundNumber()).append(":phase=").append(st.getPhase());
        return sb.toString();
    }

    private static boolean throwsOnNullState() {
        try {
            HeadlessReportingTiebreakTest.evaluateAtRoundCap(null, new RulesEngine(),
                    new int[] { 4, 4 });
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean throwsOnBadBaseline() {
        Player a = seat("Alpha", Faction.NARN);
        Player b = seat("Beta", Faction.MINBARI);
        toInfluence(a, 20);
        toInfluence(b, 20);
        try {
            HeadlessReportingTiebreakTest.evaluateAtRoundCap(state(order(0, a, b)),
                    new RulesEngine(), new int[] { 4, 4, 4 });
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    // ------------------------------------------------------------------
    // Output plumbing
    // ------------------------------------------------------------------

    private static void head(String title) {
        System.out.println();
        System.out.println("== " + title + " ==");
    }

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println((ok ? "  [PASS] " : "  [FAIL] ") + label);
        if (!ok) failures++;
    }

    private static String[] splitCsv(String csv) {
        if (csv == null || csv.length() == 0) return new String[0];
        return csv.split(",");
    }

    private static String csv(String[] names) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(names[i]);
        }
        return sb.toString();
    }

    private static String sortedCsv(String[] names) {
        List<String> sorted = new ArrayList<String>();
        for (int i = 0; i < names.length; i++) sorted.add(names[i]);
        Collections.sort(sorted);
        return join(sorted, ",");
    }

    private static String join(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
