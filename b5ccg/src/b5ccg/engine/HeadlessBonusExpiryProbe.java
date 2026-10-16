package b5ccg.engine;
import b5ccg.model.enums.Loyalty;

import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.Deck;
import b5ccg.model.EventCard;
import b5ccg.model.enums.Race;
import b5ccg.model.FleetCard;
import b5ccg.model.enums.GamePhase;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.StatBonus;
import b5ccg.model.enums.BonusScope;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Expiry;
import b5ccg.model.enums.Rarity;
import b5ccg.model.enums.StatKey;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * B5-2521 — <b>d10/d11 bonus-layer expiry conformance</b> (standalone CLI probe;
 * exit 0 on all-pass, 1 on any failure; deliberately NOT wired into RUN_TESTS —
 * see class note below).
 *
 * <h3>What the row asked for</h3>
 *
 * <p>The proposal (B5-0430) and rulebook d10/d11 require that bonus effects which
 * last "until end of round" expire at the END_ROUND boundary, and that ongoing
 * modifiers never leak into the next round. B5-2521's deliverable is two parts:
 * (1) audit {@link Player#sweepBonusExpiries(int)} against every bonus-granting
 * card effect, and (2) this probe asserting no bonus survives past its DECLARED
 * duration. The runtime seam was closed by B5-2549
 * ({@link Player#sweepEndOfTurnBonusExpiries()} hooked to
 * {@code GameController.endOfTurn}); this probe is the independently-checkable
 * assertion the row names, written as a standalone class so it can be run and
 * read without the full conformance harness.
 *
 * <h3>Audit result this probe encodes</h3>
 *
 * <p>Every production {@code grantBonus} call site in
 * {@code b5ccg/engine/CardEffects.java} (lines 333, 352, 382-416, 436, 929,
 * 1094, 1335) passes {@link Expiry#WHILE_IN_PLAY}. The turn-limited kinds
 * ({@link Expiry#END_OF_TURN}, {@link Expiry#START_OF_NEXT_OWNER_TURN}) are
 * granted only by tests (the BONX fixture), and {@link Expiry#ON_EVENT} is
 * granted nowhere. So the only live expiry contract in production is
 * WHILE_IN_PLAY → removed when the source card leaves play
 * ({@link Player#removeBonusesBySource(String)}). The turn-limited kinds are
 * governed by the two sweeps:
 *
 * <ul>
 *   <li>{@link Expiry#END_OF_TURN} — swept at the owner's TURN end
 *       ({@code sweepEndOfTurnBonusExpiries}, called from
 *       {@code GameController.endOfTurn}) AND at the ROUND boundary
 *       ({@code sweepBonusExpiries(int)}, called from
 *       {@code GameState.advanceRound()}).</li>
 *   <li>{@link Expiry#START_OF_NEXT_OWNER_TURN} — swept ONLY at the ROUND
 *       boundary. It is still owed for the rest of its own owner's turn, so
 *       sweeping it early (at the turn boundary) would expire it a full cycle
 *       early.</li>
 *   <li>{@link Expiry#ON_EVENT} — removed only by an explicit engine call; none
 *       exist, by design. The sweeps must NOT over-reach into it.</li>
 * </ul>
 *
 * <h3>Why this is a standalone probe, not another method in HeadlessConformanceTest</h3>
 *
 * <p>The BONX section in {@code HeadlessConformanceTest} already enforces the
 * same seam through RUN_TESTS=1 (B5-2549). This file is the row's literal
 * "add a HeadlessBonusExpiryProbe" deliverable: a self-contained, directly
 * executable check with a 0/1 exit contract, mirroring the other standalone
 * probes (HeadlessArmisticeTimingProbe, HeadlessPassBiasCascadeProbe). It is
 * verified in this task by direct execution; adding it to the B5-0956 gate list
 * is a separate, deliberately-omitted change so a probe file stays a single
 * concern.
 */
public class HeadlessBonusExpiryProbe {

    private static int checks   = 0;
    private static int failures = 0;

    public static void main(String[] args) {
        System.out.println("=== B5-2521 bonus-layer expiry conformance ===");

        scenarioTurnBoundaryExpiresEndOfTurn();
        scenarioOwnerTurnSurvivesEndOfTurn();
        scenarioOwnerTurnRoundSweep();
        scenarioWhileInPlaySurvivesBothBoundaries();
        scenarioWhileInPlayRemovedBySource();
        scenarioHolisticNoLeakAcrossCycle();
        scenarioOnEventNotSweptByEitherBoundary();

        System.out.println();
        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures == 0) {
            System.out.println("B5-2521 PROBE PASSED");
        } else {
            System.out.println("B5-2521 PROBE FAILED: " + failures + " check(s)");
            System.exit(1);
        }
    }

// ── TURN boundary: END_OF_TURN dies when its owner's turn ends ─────────────

    /**
     * The headline claim (B5-2549's fix). An END_OF_TURN bonus is declared to
     * last "until the owner's turn ends"; the engine boundary for that is
     * GameController.endOfTurn, which now calls sweepEndOfTurnBonusExpiries().
     * The assertion goes through the ENGINE seam, not the model method, because a
     * test calling sweepEndOfTurnBonusExpiries() directly would stay green on a
     * pre-fix tree that had the method but never wired the call — a gate that
     * cannot fail on the defect it was written for.
     */
    private static void scenarioTurnBoundaryExpiresEndOfTurn() {
        System.out.println("== TURN: END_OF_TURN expires at the owner's turn end ==");
        Player owner = player("BXP-owner", Race.NARN);
        FleetCard fleet = fleetCard("bxp_fleet", "FRIGATE");
        owner.getFleets().add(fleet);
        fleet.setOwner(owner);
        GameState st = state(owner, player("BXP-other", Race.MINBARI));

        int printed = fleet.getEffectiveMilitary();

        owner.grantBonus(StatBonus.attached("bxp_turn", StatKey.MILITARY, 2,
                fleet.getId(), Expiry.END_OF_TURN, st.getRoundNumber()));

        // Fixture self-check: if the grant did not land, every later expectation
        // below is vacuously satisfiable and the whole scenario reads green
        // while asserting nothing.
        check("TURN fixture armed the END_OF_TURN bonus",
                fleet.getEffectiveMilitary() == printed + 2
                && owner.getBonuses().size() == 1);

        boolean called = invokeEndOfTurn(st, owner);
        check("TURN engine end-of-turn path runs (gate self-check)", called);
        check("TURN the END_OF_TURN bonus is gone after its owner's turn ends",
                owner.getBonuses().size() == 0);
        check("TURN the expired +2 is gone from the composed stat",
                fleet.getEffectiveMilitary() == printed);
    }

// ── Owner-turn boundary: START_OF_NEXT_OWNER_TURN must SURVIVE it ─────────

    /**
     * The anti-overcorrection. START_OF_NEXT_OWNER_TURN is still owed at the end
     * of its own owner's turn ("the owner's NEXT turn" has not started), so
     * sweeping it at the turn boundary would expire it a full cycle early. The
     * fix deliberately leaves it on the round boundary; this check fails if a
     * later reader "simplifies" the turn sweep by adding it there.
     */
    private static void scenarioOwnerTurnSurvivesEndOfTurn() {
        System.out.println("== TURN: START_OF_NEXT_OWNER_TURN survives its owner's turn end ==");
        Player owner = player("BXP-owner", Race.NARN);
        FleetCard fleet = fleetCard("bxp_fleet", "FRIGATE");
        owner.getFleets().add(fleet);
        fleet.setOwner(owner);
        GameState st = state(owner, player("BXP-other", Race.MINBARI));

        int printed = fleet.getEffectiveMilitary();

        owner.grantBonus(StatBonus.attached("bxp_next", StatKey.MILITARY, 4,
                fleet.getId(), Expiry.START_OF_NEXT_OWNER_TURN, st.getRoundNumber()));

        check("OWN fixture armed the START_OF_NEXT_OWNER_TURN bonus",
                fleet.getEffectiveMilitary() == printed + 4
                && owner.getBonuses().size() == 1);

        boolean called = invokeEndOfTurn(st, owner);
        check("OWN engine end-of-turn path runs (gate self-check)", called);
        check("OWN START_OF_NEXT_OWNER_TURN survives its owner's turn end",
                owner.getBonuses().size() == 1
                && fleet.getEffectiveMilitary() == printed + 4);
    }

// ── ROUND boundary: START_OF_NEXT_OWNER_TURN is swept there ──────────────

    /**
     * The round boundary keeps its own, separate contract: advanceRound() sweeps
     * both turn-limited kinds. START_OF_NEXT_OWNER_TURN is owed only until the
     * owner's next turn, which the round boundary is.
     */
    private static void scenarioOwnerTurnRoundSweep() {
        System.out.println("== ROUND: START_OF_NEXT_OWNER_TURN swept at the round boundary ==");
        Player owner = player("BXP-owner", Race.NARN);
        FleetCard fleet = fleetCard("bxp_fleet", "FRIGATE");
        owner.getFleets().add(fleet);
        fleet.setOwner(owner);
        GameState st = state(owner, player("BXP-other", Race.MINBARI));

        int printed = fleet.getEffectiveMilitary();

        owner.grantBonus(StatBonus.attached("bxp_next", StatKey.MILITARY, 4,
                fleet.getId(), Expiry.START_OF_NEXT_OWNER_TURN, st.getRoundNumber()));

        check("ROUND fixture armed the START_OF_NEXT_OWNER_TURN bonus",
                fleet.getEffectiveMilitary() == printed + 4
                && owner.getBonuses().size() == 1);

        st.advanceRound();
        check("ROUND START_OF_NEXT_OWNER_TURN is gone after the round boundary",
                owner.getBonuses().size() == 0);
        check("ROUND the expired +4 is gone from the composed stat",
                fleet.getEffectiveMilitary() == printed);
    }

// ── WHILE_IN_PLAY survives both boundaries ─────────────────────────────────

    /**
     * A WHILE_IN_PLAY bonus lasts until the source card leaves play, not until a
     * round or turn ends. Both sweeps must leave it untouched. A regression that
     * routed every bonus through one sweep would catch it here.
     */
    private static void scenarioWhileInPlaySurvivesBothBoundaries() {
        System.out.println("== PLAY: WHILE_IN_PLAY survives turn and round boundaries ==");
        Player owner = player("BXP-owner", Race.NARN);
        FleetCard fleet = fleetCard("bxp_fleet", "FRIGATE");
        owner.getFleets().add(fleet);
        fleet.setOwner(owner);
        GameState st = state(owner, player("BXP-other", Race.MINBARI));

        int printed = fleet.getEffectiveMilitary();

        owner.grantBonus(StatBonus.attached("bxp_play", StatKey.MILITARY, 8,
                fleet.getId(), Expiry.WHILE_IN_PLAY, st.getRoundNumber()));

        check("PLAY fixture armed the WHILE_IN_PLAY bonus",
                fleet.getEffectiveMilitary() == printed + 8
                && owner.getBonuses().size() == 1);

        invokeEndOfTurn(st, owner);
        check("PLAY WHILE_IN_PLAY survives its owner's turn end",
                owner.getBonuses().size() == 1
                && fleet.getEffectiveMilitary() == printed + 8);

        st.advanceRound();
        check("PLAY WHILE_IN_PLAY survives the round boundary",
                owner.getBonuses().size() == 1
                && fleet.getEffectiveMilitary() == printed + 8);
    }

// ── WHILE_IN_PLAY removed only by its source leaving play ─────────────────

    /**
     * The production path: every real grant is WHILE_IN_PLAY, and it is removed
     * when the source card leaves play, not by a boundary sweep. This is the
     * contract the d10/d11 layer actually depends on.
     */
    private static void scenarioWhileInPlayRemovedBySource() {
        System.out.println("== PLAY: WHILE_IN_PLAY removed only when its source leaves play ==");
        Player owner = player("BXP-owner", Race.NARN);
        FleetCard fleet = fleetCard("bxp_fleet", "FRIGATE");
        owner.getFleets().add(fleet);
        fleet.setOwner(owner);
        GameState st = state(owner, player("BXP-other", Race.MINBARI));

        int printed = fleet.getEffectiveMilitary();

        owner.grantBonus(StatBonus.attached("bxp_play", StatKey.MILITARY, 8,
                fleet.getId(), Expiry.WHILE_IN_PLAY, st.getRoundNumber()));

        int removed = owner.removeBonusesBySource("bxp_play");
        check("SRC removeBonusesBySource reports exactly one removal",
                removed == 1);
        check("SRC WHILE_IN_PLAY is gone once its source leaves play",
                owner.getBonuses().size() == 0
                && fleet.getEffectiveMilitary() == printed);
    }

// ── Holistic: no bonus leaks past its declared duration across a cycle ────

    /**
     * All three kinds granted together, then the boundaries are driven in order.
     * After the owner's turn ends, only END_OF_TURN is gone. After the round
     * boundary, only WHILE_IN_PLAY remains. On a pre-fix tree (no turn-boundary
     * sweep) the END_OF_TURN bonus would still be present after endOfTurn, so
     * this scenario fails there — it is the single best "did the leak get
     * closed?" assertion.
     */
    private static void scenarioHolisticNoLeakAcrossCycle() {
        System.out.println("== HOLISTIC: a full cycle leaves only WHILE_IN_PLAY ==");
        Player owner = player("BXP-owner", Race.NARN);
        FleetCard fleet = fleetCard("bxp_fleet", "FRIGATE");
        owner.getFleets().add(fleet);
        fleet.setOwner(owner);
        GameState st = state(owner, player("BXP-other", Race.MINBARI));

        int printed = fleet.getEffectiveMilitary();

        owner.grantBonus(StatBonus.attached("bxp_turn", StatKey.MILITARY, 2,
                fleet.getId(), Expiry.END_OF_TURN, st.getRoundNumber()));
        owner.grantBonus(StatBonus.attached("bxp_next", StatKey.MILITARY, 4,
                fleet.getId(), Expiry.START_OF_NEXT_OWNER_TURN, st.getRoundNumber()));
        owner.grantBonus(StatBonus.attached("bxp_play", StatKey.MILITARY, 8,
                fleet.getId(), Expiry.WHILE_IN_PLAY, st.getRoundNumber()));

        int before = fleet.getEffectiveMilitary();
        check("HOL fixture armed all three bonuses before the cycle (gate self-check)",
                before == printed + 2 + 4 + 8 && owner.getBonuses().size() == 3);

        invokeEndOfTurn(st, owner);
        check("HOL after owner turn end: only END_OF_TURN swept (2 remain)",
                owner.getBonuses().size() == 2
                && fleet.getEffectiveMilitary() == printed + 4 + 8);

        st.advanceRound();
        check("HOL after round boundary: only WHILE_IN_PLAY remains (1 left)",
                owner.getBonuses().size() == 1
                && fleet.getEffectiveMilitary() == printed + 8);
    }

// ── ON_EVENT: sweeps must NOT over-reach into it ──────────────────────────

    /**
     * ON_EVENT is declared "removed by an explicit engine call", and no card
     * grants it. This asserts the two sweeps honour that: an ON_EVENT bonus
     * survives both the turn and the round boundary, because nothing is
     * authorised to expire it. A sweep that over-generalised would catch it
     * here. Surviving the boundaries is the CORRECT outcome for this kind.
     */
    private static void scenarioOnEventNotSweptByEitherBoundary() {
        System.out.println("== ON_EVENT: neither sweep expires it (by design) ==");
        Player owner = player("BXP-owner", Race.NARN);
        FleetCard fleet = fleetCard("bxp_fleet", "FRIGATE");
        owner.getFleets().add(fleet);
        fleet.setOwner(owner);
        GameState st = state(owner, player("BXP-other", Race.MINBARI));

        int printed = fleet.getEffectiveMilitary();

        owner.grantBonus(StatBonus.attached("bxp_event", StatKey.MILITARY, 5,
                fleet.getId(), Expiry.ON_EVENT, st.getRoundNumber()));

        check("EVENT fixture armed the ON_EVENT bonus",
                fleet.getEffectiveMilitary() == printed + 5
                && owner.getBonuses().size() == 1);

        invokeEndOfTurn(st, owner);
        check("EVENT ON_EVENT survives its owner's turn end (sweep must not reach it)",
                owner.getBonuses().size() == 1
                && fleet.getEffectiveMilitary() == printed + 5);

        st.advanceRound();
        check("EVENT ON_EVENT survives the round boundary (sweep must not reach it)",
                owner.getBonuses().size() == 1
                && fleet.getEffectiveMilitary() == printed + 5);
    }

    // ── Helpers (mirror HeadlessConformanceTest.player/state/fleetCard) ──────

    /** Drives the engine's real end-of-turn seam by reflection. Returns false
     *  rather than throwing if the seam cannot be reached, so a rename shows up
     *  as a red check naming the gate instead of an aborted run. */
    private static boolean invokeEndOfTurn(GameState st, Player p) {
        try {
            GameController gc = new GameController(st, null, null);
            Class<?>[] sig = new Class[] { Player.class };
            Method m = GameController.class.getDeclaredMethod("endOfTurn", sig);
            m.setAccessible(true);
            m.invoke(gc, new Object[] { p });
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static Player player(String name, Race f) {
        Player p = new Player(name, f, false);
        CharacterCard amb = new CharacterCard("amb_" + name, "Amb " + name,
                "CHARACTER_" + f, Rarity.FIXED, Loyalty.fromRace(f), CardSet.PREMIERE, "x", "text",
                3, 3, 3, 3, true);
        p.setAmbassador(amb);
        p.getInnerCircle().add(amb);
        List<Card> filler = new ArrayList<Card>();
        for (int i = 0; i < 10; i++) {
            filler.add(new EventCard("deck_" + name + "_" + i, "Deck " + i,
                    "EVENT", Rarity.COMMON, Loyalty.ANY, CardSet.PREMIERE, "x", "text"));
        }
        p.setDeck(new Deck(filler));
        return p;
    }

    private static GameState state(Player... players) {
        List<Player> ps = new ArrayList<Player>();
        for (Player p : players) { ps.add(p); }
        GameState s = new GameState(ps);
        for (Player p : players) { p.setGameState(s); }
        s.setPhase(GamePhase.ACTION);
        return s;
    }

    private static FleetCard fleetCard(String id, String fleetClass) {
        FleetCard f = new FleetCard(id, id, "FLEET", Rarity.COMMON, Loyalty.ANY,
                CardSet.PREMIERE, "x", "text", 3);
        f.setFleetClass(fleetClass);
        return f;
    }

    private static void check(String label, boolean passed) {
        checks++;
        if (passed) {
            System.out.println("  [B5-2521] " + label + ": PASS");
            return;
        }
        failures++;
        System.out.println("  [B5-2521] " + label + ": FAIL");
    }
}
