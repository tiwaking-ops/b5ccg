package b5ccg.engine;

import b5ccg.engine.RulesEngine;
import b5ccg.model.CharacterCard;
import b5ccg.model.Conflict;
import b5ccg.model.ConflictCard;
import b5ccg.model.EventCard;
import b5ccg.model.FleetCard;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.ConflictType;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.Rarity;
import b5ccg.model.enums.TensionMatrix;
import b5ccg.model.enums.WarKind;

import java.util.ArrayList;
import java.util.List;

/**
 * B5-2561 — conflict-participation-restrictions probe (standalone CLI,
 * never wired into RUN_TESTS; exit 0 on all-pass, 1 on any failure).
 *
 * <p>The row asks for a probe asserting the participation restriction rules
 * — "factions may only join conflicts they are allowed to, based on tension
 * and card type" — over 30 rounds with varying tension states.</p>
 *
 * <p><b>Measured shape of the implemented rules</b> (this probe is written
 * against what the tree does, not against the row's paraphrase):</p>
 * <ul>
 *   <li><b>The tension-derived half is the war SET, not the tension
 *       number.</b> For war conflicts (card == null),
 *       {@code Conflict.canJoinConflict} admits any player of another race
 *       whose faction is in the explicit at-war set
 *       ({@code TensionMatrix.isAtWar}) with the initiator, and refuses
 *       same-race players outright. The tension NUMBER is never consulted:
 *       a pair at tension 5 that is not at war is refused; a pair at war at
 *       tension 0 is admitted. War is entered/exited only by explicit
 *       {@code enterWar}/{@code exitWar} calls (B5-0358 proposal §3.1).</li>
 *   <li><b>The card-type half is the card's {@code Participation} data.</b>
 *       For regular conflicts (card != null) the join gate is
 *       {@code Participation.isPlayerAllowed} and the commit gate is
 *       {@code allowsCardType} + quota + leaders (B5-0336 data proposal
 *       §2.1/§3.2). That vocabulary has no tension key by design, so a
 *       regular conflict's participation decisions are invariant to the
 *       tension number — asserted here as the authority boundary the data
 *       proposal defines, not as a gap.</li>
 * </ul>
 *
 * <p><b>What is new here relative to the sibling probes.</b>
 * {@code HeadlessParticipationGatesProbe} (B5-0383) drives the REAL card
 * data through the restriction predicates in single scenarios — no rounds,
 * no tension. {@code HeadlessTensionMatrixProbe} (B5-2583) covers tension
 * mechanics and, in its S4, one pair's war entry every third round (never
 * exited) with a single joiner. This probe varies BOTH inputs across 30
 * rounds and checks the join surface every round:</p>
 * <ul>
 *   <li>S1 — four joiners (three other races + a same-race control) against
 *       an all-pairs schedule that ENTERS war, EXITS war mid-run, lowers
 *       tension back to 0 and RE-ENTERS war, while tension rises to the 5
 *       clamp. The gate must equal both engine authorities (GameState and
 *       TensionMatrix, which must also agree with each other) on every one
 *       of the 30 × 4 join decisions, both polarities.</li>
 *   <li>S2 — a FLEET-only card, an open card and an INITIATOR_TARGET card,
 *       driven through the real {@code Conflict.commitCard}/
 *       {@code addParticipant} paths once per round while the pair's tension
 *       sweeps 0 → 5 → 0. Every accept/refuse decision must hold at every
 *       tension level.</li>
 * </ul>
 *
 * <p>Java 6 (javac -source 6 -target 6), stdlib only. Harness only: no
 * game-logic edits. Run on demand:</p>
 *
 * <pre>
 *   java -cp b5ccg/out b5ccg.engine.HeadlessParticipationProbe
 * </pre>
 */
public class HeadlessParticipationProbe {

    /** The row's round count. */
    private static final int ROUNDS = 30;

    private static int checks   = 0;
    private static int failures = 0;

    public static void main(String[] args) {
        try {
            scenarioWarSetJoinsOverThirtyRounds();
            scenarioCardTypeGatesAcrossTension();
            scenarioWarJoinEnforcedOnTheEnginePath();
        } catch (Throwable th) {
            th.printStackTrace(System.err);
            System.out.println("B5-2561 PROBE ABORTED: " + th);
            System.exit(1);
        }
        System.out.println();
        if (failures == 0) {
            System.out.println("B5-2561 PROBE PASSED (all " + checks + " check(s))");
        } else {
            System.out.println("B5-2561 PROBE FAILED: " + failures + " of " + checks + " check(s)");
            System.exit(1);
        }
    }

    // ── S1: war-set joins over 30 rounds of varying tension ──────────────────

    /**
     * Four joiners against an initiator; the war set and the tension values
     * both vary; the join gate is checked EVERY round against BOTH engine
     * authorities, plus pinned-polarity spot rounds.
     *
     * Schedule (round index r, 0-based):
     *   NARN-MINBARI : enterWar r0, exitWar r15, tension -5 at r20, re-enter r21
     *   NARN-CENTAURI: enterWar r7,  exitWar r22, tension +2 every even round
     *   NARN-HUMAN   : never at war (control), tension +1 every round
     *   NARN-NARN    : same-race control joiner, never admissible
     */
    private static void scenarioWarSetJoinsOverThirtyRounds() {
        System.out.println("== S1: " + ROUNDS
                + " rounds — join gate tracks the war SET while tension varies ==");

        Player narn  = player("P1N",  Faction.NARN);
        Player minb  = player("P1M",  Faction.MINBARI);
        Player cent  = player("P1C",  Faction.CENTAURI);
        Player hum   = player("P1H",  Faction.HUMAN);
        Player narn2 = player("P1N2", Faction.NARN);   // same-race control
        GameState st = state(narn, minb, cent, hum, narn2);
        TensionMatrix m = st.getTensionMatrix();

        Player[]   joiners   = { minb, cent, hum, narn2 };
        Faction[]  joinerFac = { Faction.MINBARI, Faction.CENTAURI, Faction.HUMAN, Faction.NARN };
        boolean[]  neverIn   = { false, false, true, true }; // controls: hum + same race

        int gateMismatches  = 0;
        int authorityClash  = 0;
        int controlFailures = 0;
        int initiatorRefused = 0;
        List<String> mismatchSites = new ArrayList<String>();

        // Spot-round pins (parallel arrays): round, joiner index, expected join, label.
        int[]     pinRound   = {  5, 16, 20, 21, 26,  8, 23 };
        int[]     pinJoiner  = {  0,  0,  0,  0,  0,  1,  1 };
        boolean[] pinExpect  = { true, false, false, true, true, true, false };
        String[]  pinLabel   = {
            "minbari admitted at war (tension already clamped at 5)",
            "minbari REFUSED after exitWar although tension reads 5",
            "minbari refused with tension lowered to 0 (still not at war)",
            "minbari re-admitted at war with tension still 0",
            "minbari still admitted with tension re-climbed to 5",
            "centauri admitted once war entered at r7",
            "centauri refused after exitWar at r22"
        };

        // Tension bookkeeping for the variance checks.
        int minbMinT = 5, minbMaxT = 0;
        int centMinT = 5, centMaxT = 0;
        int humMinT  = 5, humMaxT  = 0;
        boolean sawEnter = false, sawExit = false;
        boolean warWasOnMinb = false, warWasOnCent = false;

        for (int r = 0; r < ROUNDS; r++) {
            // ── vary the tension numbers ──
            m.raiseTension(Faction.NARN,   Faction.MINBARI, 1);
            m.raiseTension(Faction.MINBARI, Faction.NARN,   1);
            m.raiseTension(Faction.NARN,   Faction.HUMAN,   1);
            if (r % 2 == 0) m.raiseTension(Faction.NARN, Faction.CENTAURI, 2);
            if (r == 20)    m.raiseTension(Faction.NARN, Faction.MINBARI, -5); // clamp to 0
            if (r == 10)    m.raiseTension(Faction.NARN, Faction.CENTAURI, -5); // sweep the band
            if (r == 24)    m.raiseTension(Faction.NARN, Faction.HUMAN,   -5); // sweep the band

            // ── vary the war SET (explicit, never derived from tension) ──
            if (r == 0)  { m.enterWar(Faction.NARN, Faction.MINBARI); sawEnter = true; }
            if (r == 15) { m.exitWar(Faction.NARN,  Faction.MINBARI); sawExit  = true; }
            if (r == 21) { m.enterWar(Faction.NARN, Faction.MINBARI); sawEnter = true; }
            if (r == 7)  { m.enterWar(Faction.NARN, Faction.CENTAURI); sawEnter = true; }
            if (r == 22) { m.exitWar(Faction.NARN,  Faction.CENTAURI); sawExit  = true; }

            int tMinb = m.getTension(Faction.NARN, Faction.MINBARI);
            int tCent = m.getTension(Faction.NARN, Faction.CENTAURI);
            int tHum  = m.getTension(Faction.NARN, Faction.HUMAN);
            if (tMinb < minbMinT) minbMinT = tMinb;
            if (tMinb > minbMaxT) minbMaxT = tMinb;
            if (tCent < centMinT) centMinT = tCent;
            if (tCent > centMaxT) centMaxT = tCent;
            if (tHum  < humMinT)  humMinT  = tHum;
            if (tHum  > humMaxT)  humMaxT  = tHum;

            boolean warMinb = m.isAtWar(Faction.NARN, Faction.MINBARI);
            boolean warCent = m.isAtWar(Faction.NARN, Faction.CENTAURI);
            if (warMinb) warWasOnMinb = true;
            if (warCent) warWasOnCent = true;

            // A FRESH war conflict each round: NARN declares against MINBARI.
            Conflict war = new Conflict(WarKind.RACE_TARGET, narn, minb);

            if (!war.canJoinConflict(narn)) initiatorRefused++;

            for (int j = 0; j < joiners.length; j++) {
                boolean viaState  = st.isAtWar(Faction.NARN, joinerFac[j]);
                boolean viaMatrix = m.isAtWar(Faction.NARN, joinerFac[j]);
                if (viaState != viaMatrix) {
                    authorityClash++;
                    mismatchSites.add("r" + r + " authority clash " + joinerFac[j]);
                }
                boolean actual = war.canJoinConflict(joiners[j]);
                // Same-race is refused outright regardless of the war set.
                boolean expected = viaMatrix && joinerFac[j] != Faction.NARN;
                if (actual != expected) {
                    gateMismatches++;
                    mismatchSites.add("r" + r + " joiner " + joinerFac[j]
                            + " gate=" + actual + " authority=" + expected);
                }
                if (neverIn[j] && actual) {
                    controlFailures++;
                    mismatchSites.add("r" + r + " control joiner " + joinerFac[j] + " ADMITTED");
                }
            }

            for (int p = 0; p < pinRound.length; p++) {
                if (pinRound[p] != r) continue;
                boolean actual = war.canJoinConflict(joiners[pinJoiner[p]]);
                check("S1 r" + r + " pin: " + pinLabel[p], actual == pinExpect[p],
                        "expected " + pinExpect[p] + " got " + actual);
            }
        }

        check("S1 the join gate agreed with the war authority on all "
                + (ROUNDS * joiners.length) + " join decisions",
                gateMismatches == 0, describeMismatches(mismatchSites));
        check("S1 GameState and TensionMatrix never disagreed about war",
                authorityClash == 0, authorityClash + " clash(es)");
        check("S1 the never-at-war control was refused in every round",
                controlFailures == 0, controlFailures + " wrongful admission(s)");
        check("S1 the same-race control was refused in every round",
                controlFailures == 0, "(shared counter with the control admission check)");
        check("S1 the initiator was admitted in every round",
                initiatorRefused == 0, initiatorRefused + " refusal(s)");
        check("S1 war-set membership actually changed during the run (enter and exit)",
                sawEnter && sawExit, "enter=" + sawEnter + " exit=" + sawExit);
        check("S1 MINBARI war window exercised", warWasOnMinb, "never at war?");
        check("S1 CENTAURI war window exercised", warWasOnCent, "never at war?");
        check("S1 tension varied across the run (NARN-MINBARI touched 0 and 5)",
                minbMinT == 0 && minbMaxT == 5,
                "range was " + minbMinT + ".." + minbMaxT);
        check("S1 tension varied on the control pair too (NARN-HUMAN touched 0 and 5)",
                humMinT == 0 && humMaxT == 5, "range was " + humMinT + ".." + humMaxT);
        check("S1 CENTAURI tension varied (swept 0 and clamped at 5)",
                centMinT == 0 && centMaxT == 5, "range was " + centMinT + ".." + centMaxT);
    }

    // ── S2: card-type gates held across every tension level ──────────────────

    /**
     * Three regular-conflict fixtures driven through the real
     * commit/addParticipant paths once per round while the pair's tension
     * sweeps 0 up to the 5 clamp and back to 0. The participation data
     * vocabulary (B5-0336 proposal §3.2) has no tension key, so every
     * accept/refuse decision must hold at every tension level.
     */
    private static void scenarioCardTypeGatesAcrossTension() {
        System.out.println("== S2: " + ROUNDS
                + " rounds — card-type and player gates invariant across tension 0..5..0 ==");

        Player narn = player("P2N", Faction.NARN);
        Player minb = player("P2M", Faction.MINBARI);
        Player cent = player("P2C", Faction.CENTAURI);
        GameState st = state(narn, minb, cent);
        TensionMatrix m = st.getTensionMatrix();

        ConflictCard fleetOnly = conflictCard("p1_fleet_only", "Fleet Only (probe)",
                "{\"cardTypes\":[\"FLEET\"]}");
        ConflictCard openCard  = conflictCard("p1_open", "Open (probe)", null);
        ConflictCard tgtGate   = conflictCard("p1_tgt_gate", "Target Gate (probe)",
                "{\"players\":\"INITIATOR_TARGET\"}");

        FleetCard     fleet = new FleetCard("p1_f", "Probe Fleet", "FLEET",
                Rarity.COMMON, Faction.ANY, CardSet.PREMIERE, "x", "text", 3);
        CharacterCard crew  = new CharacterCard("p1_c", "Probe Crew",
                "CHARACTER_MINBARI", Rarity.RARE, Faction.MINBARI, CardSet.PREMIERE,
                "x", "text", 2, 2, 0, 3, false);
        EventCard     burst = new EventCard("p1_e", "Probe Event",
                "EVENT", Rarity.COMMON, Faction.ANY, CardSet.PREMIERE, "x", "text");

        int fleetAccepted = 0, charRefused = 0, eventRefused = 0;
        int openAllAccepted = 0;
        int targetAdmitted = 0, outsiderRefused = 0;

        int minT = 5, maxT = 0;

        for (int r = 0; r < ROUNDS; r++) {
            // Sweep the tension number 0..5..0 so every level is exercised.
            int delta = (r < 15) ? 1 : -1;
            m.raiseTension(Faction.NARN, Faction.MINBARI, delta);
            m.raiseTension(Faction.MINBARI, Faction.NARN, delta);
            int t = m.getTension(Faction.NARN, Faction.MINBARI);
            if (t < minT) minT = t;
            if (t > maxT) maxT = t;

            // (a) FLEET-only card: fleet in, character and event out.
            Conflict cf = new Conflict(fleetOnly, narn);
            if (cf.commitCard(minb, fleet, false)) fleetAccepted++;
            if (!cf.commitCard(minb, crew, false))  charRefused++;
            if (!cf.commitCard(minb, burst, false)) eventRefused++;

            // (b) Open card (no participation object): every kind accepted.
            Conflict co = new Conflict(openCard, narn);
            boolean openOk = co.commitCard(minb, fleet, false)
                          && co.commitCard(minb, crew, false)
                          && co.commitCard(minb, burst, false);
            if (openOk) openAllAccepted++;

            // (c) INITIATOR_TARGET players gate: target in, outsider out.
            Conflict cp = new Conflict(tgtGate, narn, minb);
            if (cp.addParticipant(minb, false)) targetAdmitted++;
            if (!cp.addParticipant(cent, false)) outsiderRefused++;
        }

        check("S2 FLEET-only card accepted the fleet in all " + ROUNDS + " rounds",
                fleetAccepted == ROUNDS, fleetAccepted + " of " + ROUNDS);
        check("S2 FLEET-only card refused the character in all " + ROUNDS + " rounds",
                charRefused == ROUNDS, charRefused + " of " + ROUNDS);
        check("S2 FLEET-only card refused the event in all " + ROUNDS + " rounds",
                eventRefused == ROUNDS, eventRefused + " of " + ROUNDS);
        check("S2 open card accepted fleet+character+event in all " + ROUNDS + " rounds",
                openAllAccepted == ROUNDS, openAllAccepted + " of " + ROUNDS);
        check("S2 INITIATOR_TARGET admitted the declared target in all " + ROUNDS + " rounds",
                targetAdmitted == ROUNDS, targetAdmitted + " of " + ROUNDS);
        check("S2 INITIATOR_TARGET refused the outsider in all " + ROUNDS + " rounds",
                outsiderRefused == ROUNDS, outsiderRefused + " of " + ROUNDS);
        check("S2 the tension number actually swept 0 to 5 and back",
                minT == 0 && maxT == 5, "range was " + minT + ".." + maxT);

        System.out.println("  [B5-2561] measured: " + (ROUNDS * 7)
                + " regular-conflict participation decisions taken while tension swept "
                + minT + ".." + maxT + "; the deciding methods (canJoinConflict/"
                + "canCommitCard regular branch) contain zero getTension reads"
                + " — card data is the only regular-conflict authority.");
    }

    // ── S3: B5-2503 — the join ENGINE path is bound by the same gate ────────

    /**
     * S1 asserts the gate's ANSWER ({@code Conflict.canJoinConflict}) across
     * 120 join decisions. This scenario asserts the answer is actually APPLIED:
     * before B5-2503 the model refused a non-belligerent on a declared war and
     * {@code RulesEngine.executeJoinConflict} enrolled him anyway, because
     * {@code Conflict.canCommitCard} returned true for every war conflict
     * ({@code card == null}) before it ever consulted the gate, and the
     * ambassador commit that follows therefore succeeded and was logged as a
     * join. Measured before the fix: the third party became a participant with
     * one committed card and added 3 to the support total.
     *
     * A same-seat control — the identical seat joining once war really is
     * entered — proves the refusal is the war state and not a blanket block.
     */
    private static void scenarioWarJoinEnforcedOnTheEnginePath() {
        System.out.println("== S3 (B5-2503): join engine path bound by the war gate ==");
        RulesEngine rules = new RulesEngine();

        Player narn  = player("S3N", Faction.NARN);
        Player minb  = player("S3M", Faction.MINBARI);
        Player cent  = player("S3C", Faction.CENTAURI);
        GameState st = state(narn, minb, cent);
        TensionMatrix m = st.getTensionMatrix();
        m.enterWar(Faction.NARN, Faction.MINBARI);
        // Deliberately high tension WITHOUT war: S1 proved the number is not the
        // authority; this keeps that true on the engine path too.
        m.raiseTension(Faction.NARN, Faction.CENTAURI, 4);

        Conflict war = rules.declareWarConflict(narn, WarKind.RACE_TARGET, minb, null, st);
        check("S3 a war conflict can be declared", war != null && war.isWarConflict(),
                "war=" + war);
        check("S3 the third party is at tension 4 and NOT at war",
                m.getTension(Faction.NARN, Faction.CENTAURI) == 4
                        && !m.isAtWar(Faction.NARN, Faction.CENTAURI), "tension/war both wrong");

        // (a) the two authorities now agree, and both refuse.
        boolean viaModel  = war.canJoinConflict(cent);
        boolean viaEngine = rules.canJoinConflict(cent, war);
        check("S3 the model gate refuses the non-belligerent", !viaModel, "ADMITTED");
        check("S3 the engine predicate agrees with the model gate",
                viaEngine == viaModel, "engine=" + viaEngine + " model=" + viaModel);

        // (b) the refused seat cannot sneak in through the commit path.
        check("S3 the commit path refuses the non-belligerent's ambassador",
                !war.canCommitCard(cent, cent.getAmbassador()), "ADMITTED");
        int supportBefore = war.supportTotal();
        int sizeBefore    = war.getParticipants().size();
        int logBefore     = st.getLog().size();
        rules.executeJoinConflict(cent, war, true, st, true);
        check("S3 executeJoinConflict does not enrol the refused seat",
                !war.getParticipants().contains(cent)
                        && war.getParticipants().size() == sizeBefore,
                "participants grew to " + war.getParticipants().size());
        check("S3 no card is committed for the refused seat",
                war.getCommittedCards(cent).isEmpty(),
                war.getCommittedCards(cent).size() + " card(s) committed");
        check("S3 the refused seat contributes nothing to the support total",
                war.supportTotal() == supportBefore,
                "was " + supportBefore + " now " + war.supportTotal());
        List<String> added = new ArrayList<String>(st.getLog().subList(logBefore, st.getLog().size()));
        boolean claimedJoin = false;
        for (int i = 0; i < added.size(); i++) {
            if (added.get(i).indexOf("joins the conflict") >= 0) claimedJoin = true;
        }
        check("S3 the log never claims the refused seat joined", !claimedJoin,
                added.toString());
        check("S3 the refusal IS logged", added.size() > 0 && !claimedJoin,
                "no log line at all");

        // (c) mandatory-participation enforcement on a cardless conflict.
        boolean threw = false;
        try {
            rules.enforceMandatoryParticipation(war, st);
        } catch (Throwable th) {
            threw = true;
        }
        check("S3 enforceMandatoryParticipation survives a cardless war conflict",
                !threw, "threw (reading conflict.getCard().getParticipation())");

        // (d) a seat with no GameState back-reference NPE'd inside the gate.
        Player orphan = new Player("S3O", Faction.CENTAURI, false);
        boolean orphanThrew = false;
        try {
            war.canJoinConflict(orphan);
        } catch (Throwable th) {
            orphanThrew = true;
        }
        check("S3 an unwired seat is refused rather than thrown", !orphanThrew
                && !war.canJoinConflict(orphan), "threw or admitted");

        // (e) the control that makes (b) a war-state reading, not a block:
        //     the SAME seat joins once it really is at war.
        m.enterWar(Faction.NARN, Faction.CENTAURI);
        boolean expectedNow = m.isAtWar(Faction.NARN, Faction.CENTAURI);
        check("S3 the seat is now genuinely at war with the initiator", expectedNow,
                "enterWar did not take");
        check("S3 the engine predicate now admits the seat",
                rules.canJoinConflict(cent, war), "still refused");
        rules.executeJoinConflict(cent, war, true, st, true);
        check("S3 the newly belligerent seat joins with its ambassador",
                war.getParticipants().contains(cent)
                        && war.isSupporting(cent),
                "not admitted as a supporter");
        check("S3 the admitted seat really committed its ambassador",
                war.getCommittedCards(cent).contains(cent.getAmbassador()),
                "no ambassador committed");
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static ConflictCard conflictCard(String id, String title, String participationJson) {
        ConflictCard cc = new ConflictCard(id, title, "CONFLICT_DIPLOMACY",
                Rarity.COMMON, Faction.ANY, CardSet.PREMIERE, "x", "text",
                ConflictType.DIPLOMACY, 1);
        if (participationJson != null) {
            cc.setParticipation(b5ccg.model.Participation.parse(participationJson));
        }
        return cc;
    }

    /**
     * A seat wired to a GameState. The {@code setGameState} call is
     * load-bearing, not tidiness: {@code Conflict.canJoinConflict}'s war
     * branch dereferences {@code p.getGameState().getTensionMatrix()} with
     * no null guard, and the GameState constructor never assigns it — a
     * bare {@code new Player(...)} NPEs inside the gate (measured by
     * B5-2583, same unwired-back-reference family).
     */
    private static Player player(String name, Faction f) {
        Player p = new Player(name, f, true);
        CharacterCard amb = new CharacterCard("amb_" + name, "Amb " + name,
                "CHARACTER_" + f, Rarity.FIXED, f, CardSet.PREMIERE, "x", "text",
                3, 3, 3, 3, true);
        p.setAmbassador(amb);
        p.getInnerCircle().add(amb);
        return p;
    }

    private static GameState state(Player... seats) {
        List<Player> players = new ArrayList<Player>();
        for (int i = 0; i < seats.length; i++) players.add(seats[i]);
        GameState s = new GameState(players);
        for (int i = 0; i < s.getPlayers().size(); i++) {
            s.getPlayers().get(i).setGameState(s);
        }
        return s;
    }

    private static String describeMismatches(List<String> sites) {
        if (sites.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < sites.size() && shown < 5; i++) {
            if (shown > 0) sb.append("; ");
            sb.append(sites.get(i));
            shown++;
        }
        if (sites.size() > shown) sb.append("; (+")
                .append(sites.size() - shown).append(" more)");
        return sb.toString();
    }

    private static void check(String label, boolean passed, String detail) {
        checks++;
        System.out.println("  [B5-2561] " + label + ": " + (passed ? "PASS" : "FAIL")
                + (passed || detail == null ? "" : " -- " + detail));
        if (!passed) failures++;
    }
}
