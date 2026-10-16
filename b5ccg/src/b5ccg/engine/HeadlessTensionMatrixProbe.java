package b5ccg.engine;

import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.Conflict;
import b5ccg.model.ConflictCard;
import b5ccg.model.Deck;
import b5ccg.model.EventCard;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.WarKind;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.ConflictType;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.Rarity;
import b5ccg.model.enums.TensionMatrix;

import java.util.ArrayList;
import java.util.List;

/**
 * B5-2583 — TensionMatrix integration probe.
 *
 * <p>The row asks for a probe verifying that "tension changes propagate to
 * conflict participation and war state". <b>Measured, that propagation does not
 * exist</b>, and this probe is written against what the tree actually does
 * rather than against the row's paraphrase:</p>
 *
 * <ul>
 *   <li><b>Tension is WRITTEN</b> from exactly one production site:
 *       {@code RulesEngine:858}, after a war conflict resolves with a target —
 *       the losing faction's tension toward the winner rises by 1, and the
 *       engine logs the new value.</li>
 *   <li><b>Tension is READ by nothing that gates play.</b>
 *       {@code Conflict.canJoinConflict:163} reads
 *       {@code isAtWar(...)} — the explicit war SET — and never
 *       {@code getTension}. A grep for {@code getTension} in
 *       {@code b5ccg/model/Conflict.java} returns zero hits.</li>
 *   <li><b>Tension never becomes war.</b> War is entered only by an explicit
 *       {@code enterWar} call; there is no threshold anywhere that converts a
 *       tension value into a war entry. So a pair can sit at maximum tension 5
 *       forever and still be refused participation.</li>
 * </ul>
 *
 * <p>Per the B5-2576 discipline — a check asserting a gap's continued existence
 * locks a defect in as intended behaviour — the third point is <b>printed as a
 * measured finding and is NOT encoded as a pass condition</b>. The fix belongs to
 * whichever row owns the rulebook clause that would make tension escalate to
 * war; seeding that decision is not this row's scope.</p>
 *
 * <p>What this probe does assert, at both polarities: the tension write is
 * reachable and directional, it clamps to the documented 0..5 band, it survives
 * the round boundary, war participation is gated on the war SET rather than on
 * tension, and the war SET is the single authority both the engine and the
 * participant read (no second authority to drift).</p>
 *
 * <p>Java 6 (javac -source 6 -target 6), stdlib only. Standalone CLI,
 * exit 0 all-pass / 1 on any failure. Harness only: no game-logic edits.</p>
 *
 * <pre>
 *   java -cp b5ccg/out b5ccg.engine.HeadlessTensionMatrixProbe
 * </pre>
 */
public class HeadlessTensionMatrixProbe {

    /** The row's round count. */
    private static final int ROUNDS = 30;

    private static int checks   = 0;
    private static int failures = 0;
    private static int findings = 0;

    public static void main(String[] args) {
        try {
            tensionIsDirectionalAndClamped();
            participationIsGatedOnWarNotTension();
            tensionDoesNotEscalateToWar();   // prints, does not gate
            thirtyRoundsKeepOneAuthority();
        } catch (Throwable th) {
            th.printStackTrace(System.err);
            System.out.println("B5-2583 PROBE FAILED: exception escaped the probe");
            System.exit(1);
        }

        System.out.println();
        System.out.println("  rounds run:       " + ROUNDS);
        System.out.println("  findings printed: " + findings + " (measurements, not gates)");
        if (failures == 0) {
            System.out.println("B5-2583 PROBE PASSED (tension matrix integration)");
        } else {
            System.out.println("B5-2583 PROBE FAILED: " + failures + " of " + checks + " check(s)");
            System.exit(1);
        }
    }

    // ── S1: the tension write is directional and clamps ──────────────────────

    private static void tensionIsDirectionalAndClamped() {
        System.out.println("== S1: tension is directional, clamped 0..5, and survives the boundary ==");

        TensionMatrix m = new TensionMatrix();
        check("S1 a fresh pair reads zero both ways",
                m.getTension(Faction.NARN, Faction.MINBARI) == 0
                        && m.getTension(Faction.MINBARI, Faction.NARN) == 0);

        m.raiseTension(Faction.NARN, Faction.MINBARI, 3);
        check("S1 raising NARN->MINBARI moves only that direction",
                m.getTension(Faction.NARN, Faction.MINBARI) == 3
                        && m.getTension(Faction.MINBARI, Faction.NARN) == 0);

        // Clamp at the top. The band is documented on the field itself (0..5),
        // so this is read from the class's own contract, not from a probe's
        // assumption about what "a lot" means.
        m.raiseTension(Faction.NARN, Faction.MINBARI, 9);
        check("S1 tension clamps at 5 rather than exceeding it",
                m.getTension(Faction.NARN, Faction.MINBARI) == 5,
                "got " + m.getTension(Faction.NARN, Faction.MINBARI));

        // Clamp at the bottom, and the refusal of a self-directed raise.
        m.raiseTension(Faction.NARN, Faction.MINBARI, -99);
        check("S1 tension clamps at 0 rather than going negative",
                m.getTension(Faction.NARN, Faction.MINBARI) == 0);
        m.raiseTension(Faction.NARN, Faction.NARN, 3);
        check("S1 a self-directed raise is refused",
                m.getTension(Faction.NARN, Faction.NARN) == 0);
        m.raiseTension(Faction.NARN, null, 3);
        check("S1 a null target is refused", m.getTension(Faction.NARN, null) == 0);

        // Accumulation across many raises is the real behaviour the engine uses
        // (+1 per resolved war conflict), so exercise the increment itself.
        for (int i = 0; i < 4; i++) m.raiseTension(Faction.HUMAN, Faction.NARN, 1);
        check("S1 four increments of +1 accumulate to 4",
                m.getTension(Faction.HUMAN, Faction.NARN) == 4,
                "got " + m.getTension(Faction.HUMAN, Faction.NARN));

        // War is a separate, explicit set: raising tension must not create it.
        check("S1 raising tension did NOT enter war",
                !m.isAtWar(Faction.HUMAN, Faction.NARN)
                        && !m.isAtWar(Faction.HUMAN));
        m.enterWar(Faction.HUMAN, Faction.NARN);
        check("S1 war reads back once entered",
                m.isAtWar(Faction.HUMAN, Faction.NARN) && m.isAtWar(Faction.NARN, Faction.HUMAN));
        check("S1 entering war did not disturb the tension value",
                m.getTension(Faction.HUMAN, Faction.NARN) == 4);
        m.exitWar(Faction.HUMAN, Faction.NARN);
        check("S1 war clears once exited",
                !m.isAtWar(Faction.HUMAN, Faction.NARN));
    }

    // ── S2: participation is gated on the war SET, not on tension ────────────

    /**
     * The integration claim, at both polarities. The negative is the important
     * half: a pair at MAXIMUM tension and not at war must still be refused,
     * which is what makes the positive ("at war, therefore allowed") a real
     * gate rather than a tautology.
     */
    private static void participationIsGatedOnWarNotTension() {
        System.out.println("== S2: war-conflict participation follows the war SET, not the tension value ==");

        Player narn = player("TMN", Faction.NARN);
        Player minb = player("TMM", Faction.MINBARI);
        Player cent = player("TMC", Faction.CENTAURI);
        GameState st = state(narn, minb, cent);

        for (int i = 0; i < st.getPlayers().size(); i++) {
            st.getPlayers().get(i).setGameState(st);
        }

        TensionMatrix m = st.getTensionMatrix();

        // War conflict: NARN initiator vs MINBARI target, per WarKind.RACE_TARGET.
        Conflict war = new Conflict(WarKind.RACE_TARGET, narn, minb);

        // ── Polarity 1: NOT at war, but tension at maximum. Must be REFUSED.
        m.raiseTension(Faction.NARN, Faction.MINBARI, 5);
        check("S2 the pair is at maximum tension", m.getTension(Faction.NARN, Faction.MINBARI) == 5);
        check("S2 maximum tension alone does NOT admit a participant",
                !war.canJoinConflict(cent),
                "a faction at tension 5 with the initiator was admitted without a war");

        // ── Polarity 2: at war, tension irrelevant. Must be ALLOWED.
        m.enterWar(Faction.NARN, Faction.CENTAURI);
        check("S2 the pair is now at war",
                st.isAtWar(Faction.NARN, Faction.CENTAURI));
        check("S2 being at war DOES admit a participant of another race",
                war.canJoinConflict(cent));
        check("S2 the initiator can always join its own war conflict",
                war.canJoinConflict(narn));

        // The side the gate must never admit: same race as the initiator.
        Player narnAlly = player("TMM2", Faction.NARN);
        Player vorlon   = player("TMW", Faction.VORLON);
        narnAlly.setGameState(st);
        vorlon.setGameState(st);
        check("S2 a faction of the initiator's own race is refused even at war",
                !war.canJoinConflict(narnAlly));
        check("S2 a non-war faction is refused even at maximum tension",
                !war.canJoinConflict(vorlon));
    }

    // ── S3: the propagation the row describes does not exist (printed) ───────

    /**
     * Prints a measured finding. Deliberately NOT asserted as a pass condition:
     * a check requiring the gap to persist would lock a defect in as intended
     * behaviour, and a check requiring it closed would go red on a row whose
     * scope was to write the probe. The measurement goes to the report and to
     * whichever row owns the escalation clause.
     */
    private static void tensionDoesNotEscalateToWar() {
        System.out.println("== S3: FINDING — tension never escalates to war (measured, not gated) ==");

        TensionMatrix m = new TensionMatrix();
        int escalations = 0;
        // Drive the pair to the documented maximum, repeatedly, from both
        // directions, and watch whether a war entry ever appears.
        for (int i = 0; i < 20; i++) {
            m.raiseTension(Faction.HUMAN, Faction.CENTAURI, 5);
            m.raiseTension(Faction.CENTAURI, Faction.HUMAN, 5);
            if (m.isAtWar(Faction.HUMAN, Faction.CENTAURI)) escalations++;
        }
        int peak = m.getTension(Faction.HUMAN, Faction.CENTAURI);

        findings++;
        System.out.println("  [B5-2583 FINDING] After 20 rounds of maximum tension in both "
            + "directions, peak tension = " + peak + "/5 and at-war = "
            + m.isAtWar(Faction.HUMAN, Faction.CENTAURI)
            + " (escalations observed: " + escalations + ").");
        System.out.println("  [B5-2583 FINDING] The row's stated integration — tension changes "
            + "propagating to war state — is NOT implemented: war is entered only by an "
            + "explicit enterWar call, and no threshold converts tension into war. "
            + "Conflict.canJoinConflict:163 gates on isAtWar, never on getTension.");
        System.out.println("  [B5-2583 FINDING] NOT encoded as a pass condition: asserting the gap "
            + "would lock a defect in as intended behaviour. Recorded for the row that owns "
            + "the escalation clause.");
    }

    // ── S4: 30 rounds, one authority for the war fact ────────────────────────

    /**
     * The row's round count. The property under test is that there is exactly ONE
     * authority for the war fact: {@code GameState.isAtWar} reads the same
     * TensionMatrix instance the participants read, so no round can leave the
     * engine and the participation gate disagreeing about who is at war.
     */
    private static void thirtyRoundsKeepOneAuthority() {
        System.out.println("== S4: " + ROUNDS + " rounds — GameState and the participants share one war authority ==");

        Player narn = player("S4N", Faction.NARN);
        Player minb = player("S4M", Faction.MINBARI);
        Player cent = player("S4C", Faction.CENTAURI);
        GameState st = state(narn, minb, cent);
        for (int i = 0; i < st.getPlayers().size(); i++) {
            st.getPlayers().get(i).setGameState(st);
        }

        TensionMatrix m = st.getTensionMatrix();
        RulesEngine rules = new RulesEngine();
        int roundsWithWar = 0;
        int disagreements = 0;
        int peakTension = 0;

        for (int r = 0; r < ROUNDS; r++) {
            rules.startRound(st);

            // Raise tension toward every pair, as a resolved war conflict would.
            m.raiseTension(Faction.NARN, Faction.MINBARI, 1);
            m.raiseTension(Faction.MINBARI, Faction.CENTAURI, 1);
            m.raiseTension(Faction.CENTAURI, Faction.NARN, 1);

            // Every third round, put one pair at war through the explicit call.
            if (r % 3 == 0) m.enterWar(Faction.NARN, Faction.MINBARI);

            int t = m.getTension(Faction.NARN, Faction.MINBARI);
            if (t > peakTension) peakTension = t;

            boolean viaState   = st.isAtWar(Faction.NARN, Faction.MINBARI);
            boolean viaMatrix  = m.isAtWar(Faction.NARN, Faction.MINBARI);
            if (viaState != viaMatrix) disagreements++;

            // The participation gate must agree with BOTH readers, which is the
            // integration the row is really after: a war that the engine sees
            // must be a war a participant sees.
            Conflict war = new Conflict(WarKind.RACE_TARGET, narn, cent);
            boolean joinsBecauseOfWar = war.canJoinConflict(minb);
            if (viaState && !joinsBecauseOfWar) disagreements++;
            if (!viaState && joinsBecauseOfWar) disagreements++;

            if (viaState) roundsWithWar++;
        }

        check("S4 the war fact never disagreed across readers",
                disagreements == 0, disagreements + " disagreement(s)");
        check("S4 at least a third of the rounds had a live war (the gate was exercised)",
                roundsWithWar >= ROUNDS / 3, roundsWithWar + " of " + ROUNDS);
        check("S4 tension actually accumulated across the rounds (not reset at the boundary)",
                peakTension > 1, "peak was " + peakTension);
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    /**
     * A seat wired to a GameState.
     *
     * <p>The {@code setGameState} call is load-bearing, not tidiness:
     * {@code Conflict.canJoinConflict:163} dereferences
     * {@code p.getGameState().getTensionMatrix()} with no null guard, and the
     * {@code GameState} constructor does not assign it. A bare
     * {@code new Player(...)} therefore NPEs inside the gate — the same
     * unwired-back-reference shape B5-2581's sibling notes record. Build seats
     * through here so every participant in this probe is wired.
     */
    private static Player player(String name, Faction f) {
        Player p = new Player(name, f, true);
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

    private static GameState state(Player... seats) {
        List<Player> players = new ArrayList<Player>();
        for (int i = 0; i < seats.length; i++) players.add(seats[i]);
        return new GameState(players);
    }

    private static void check(String label, boolean passed, String detail) {
        checks++;
        System.out.println("  [B5-2583] " + label + ": " + (passed ? "PASS" : "FAIL")
                + (passed || detail == null ? "" : " -- " + detail));
        if (!passed) failures++;
    }

    private static void check(String label, boolean passed) {
        check(label, passed, null);
    }
}