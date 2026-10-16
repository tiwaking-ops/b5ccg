package b5ccg.ai;

import b5ccg.model.*;
import b5ccg.model.enums.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * B5-2523 — pass-bias cascade audit probe (harness; NEW file only, never
 * wired into compile.sh RUN_TESTS, no game-logic edits).
 *
 * <p>Audit target: {@code AIPlayer.buildLegalActions} (B5-2523, from the
 * B5-0422 pass-bias cascade proposal). Two questions, in order:
 *
 * <ol>
 *   <li><b>Can a turn have ZERO legal actions?</b> The offer builder seeds
 *       {@code PASS} as its first element (AIPlayer.java:266) before any
 *       gate runs, so the returned list is structurally non-empty. The one
 *       early return (AIPlayer.java:269, {@code p.isPassed() ||
 *       p.getActionsLeft() <= 0}) returns the pass-only singleton. This probe
 *       asserts both: 50 consecutive EASY turns each see a non-empty legal
 *       list, and a pass-only list occurs ONLY under that early return's
 *       stated preconditions.</li>
 *   <li><b>Is a non-pass action offered whenever one is possible?</b> Every
 *       non-pass action type consumes the shared action spend in
 *       {@code GameController.processAction}, so the
 *       {@code getActionsLeft() <= 0} early return is the correct gate, not
 *       an over-eager one. While the action slot is live the probe asserts a
 *       non-pass offer is present on every turn — a pass-only list with a
 *       live action slot is the defect class the row names, and it is the
 *       only shape that would make the cascade unavoidable.</li>
 * </ol>
 *
 * <p>What the probe deliberately does NOT assert: any pass-rate band. The
 * B5-0351 difficulty contract owns the EASY band, and B5-0422 records that
 * retuning it is a human go/no-go. This probe measures the band and prints it.
 *
 * <p>Run: {@code java -cp out b5ccg.ai.HeadlessPassBiasCascadeProbe}
 * Exit: 0 all checks pass, 1 otherwise.
 */
public class HeadlessPassBiasCascadeProbe {

    private static final int TURNS = 50;

    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [" + (ok ? "PASS" : "FAIL") + "] " + label);
        if (!ok) failed++;
    }

    // ── fixtures ─────────────────────────────────────────────────────��────────

    /** A player with an ambassador seated in the Inner Circle and a stocked
     *  deck, so the offer builder has real material to scan every turn. */
    private static Player player(String name, Race f, List<Card> deckCards) {
        Player p = new Player(name, f, false);
        CharacterCard amb = new CharacterCard("amb_" + name, "Amb " + name,
                "CHARACTER_" + f, Rarity.FIXED, Loyalty.fromRace(f), CardSet.PREMIERE, "x", "text",
                3, 3, 3, 3, true);
        p.setAmbassador(amb);
        p.getInnerCircle().add(amb);
        p.setDeck(new Deck(new ArrayList<Card>(deckCards)));
        p.restoreAppliedPool();
        return p;
    }

    private static EventCard event(String id, String title) {
        return new EventCard(id, title, "EVENT", Rarity.COMMON, Loyalty.ANY,
                CardSet.PREMIERE, "x", "text");
    }

    private static GroupCard group(String id, String title) {
        return new GroupCard(id, title, "GROUP", Rarity.COMMON, Loyalty.ANY,
                CardSet.PREMIERE, "x", "text");
    }

    private static LocationCard location(String id, String title) {
        return new LocationCard(id, title, "LOCATION", Rarity.COMMON, Loyalty.ANY,
                CardSet.PREMIERE, "x", "text", 1);
    }

    private static CharacterCard character(String id, String title) {
        return new CharacterCard(id, title, "CHARACTER_ANY", Rarity.COMMON,
                Loyalty.ANY, CardSet.PREMIERE, "x", "text", 2, 2, 2, 2, false);
    }

    /** Deck contents: a mix of card types so the builder's generic play path,
     *  the recruit path and the (implicit) build-influence path all have
     *  material on some turns. Costs default to 0, so nothing is gated out by
     *  affordability. */
    private static List<Card> deckCards() {
        List<Card> cards = new ArrayList<Card>();
        for (int i = 0; i < 40; i++) {
            cards.add(event("pb_ev_" + i, "Cascade Event " + i));
            cards.add(group("pb_gr_" + i, "Cascade Group " + i));
            cards.add(location("pb_lo_" + i, "Cascade Location " + i));
            cards.add(character("pb_ch_" + i, "Cascade Character " + i));
        }
        return cards;
    }

    private static GameState state(Player a, Player b) {
        List<Player> ps = new ArrayList<Player>();
        ps.add(a);
        ps.add(b);
        GameState s = new GameState(ps);
        s.setPhase(GamePhase.ACTION);
        return s;
    }

    /** buildLegalActions is private; the conformance suite reads it the same
     *  way (HeadlessConformanceTest.java:2187). */
    @SuppressWarnings("unchecked")
    private static List<GameAction> legal(AIPlayer ai, GameState st, Player p) {
        try {
            Method m = AIPlayer.class.getDeclaredMethod(
                    "buildLegalActions", GameState.class, Player.class);
            m.setAccessible(true);
            return (List<GameAction>) m.invoke(ai, st, p);
        } catch (Exception e) {
            throw new RuntimeException("B5-2523: cannot reflect buildLegalActions", e);
        }
    }

    private static void seed(AIPlayer ai, long seedValue) {
        try {
            Field f = AIPlayer.class.getDeclaredField("rng");
            f.setAccessible(true);
            f.set(ai, new Random(seedValue));
        } catch (Exception e) {
            System.out.println("  [WARN] rng seed unavailable: " + e.getMessage());
        }
    }

    private static int nonPassCount(List<GameAction> actions) {
        int n = 0;
        for (int i = 0; i < actions.size(); i++) {
            if (actions.get(i).getType() != GameAction.Type.PASS) n++;
        }
        return n;
    }

    // ── main ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("=== B5-2523 pass-bias cascade audit probe (" + TURNS + " EASY turns) ===");

        final Player p     = player("CascadeNarn", Race.NARN, deckCards());
        final Player rival = player("CascadeMinbari", Race.MINBARI, deckCards());
        final GameState st = state(p, rival);
        final AIPlayer easy = new AIPlayer(p, AIDifficulty.EASY);
        seed(easy, 2523L);

        int emptyLists     = 0;   // turns with ZERO legal actions
        int passOnlyWhileLive = 0; // pass-only list while the action slot was live
        int passes         = 0;
        int nonPassTurns   = 0;
        int totalOffers    = 0;
        int minOffers      = Integer.MAX_VALUE;
        int maxOffers      = 0;

        for (int turn = 1; turn <= TURNS; turn++) {
            // Start-of-turn reset, exactly what the controller does before
            // offering an ACTION visit.
            p.resetActions();
            p.restoreAppliedPool();
            p.drawCards(2);

            boolean slotLive = !p.isPassed() && p.getActionsLeft() > 0;
            List<GameAction> offers = legal(easy, st, p);

            if (offers == null || offers.isEmpty()) {
                emptyLists++;
                continue;
            }

            int nonPass = nonPassCount(offers);
            int size    = offers.size();
            totalOffers += size;
            if (size < minOffers) minOffers = size;
            if (size > maxOffers) maxOffers = size;
            if (nonPass > 0) nonPassTurns++;
            if (slotLive && nonPass == 0) passOnlyWhileLive++;

            GameAction chosen = easy.chooseAction(st, p);
            if (chosen != null && chosen.getType() == GameAction.Type.PASS) {
                passes++;
                p.setPassed(true);
            } else {
                p.useAction();
            }
        }

        // ── 1. The cascade precondition: never zero legal actions ─────────────
        check("no turn had zero legal actions over " + TURNS + " EASY turns (observed "
              + emptyLists + ")", emptyLists == 0);

        // ── 2. At least one non-pass action whenever one is possible ─────────
        //     The defect shape the row names: a pass-only list while the
        //     action slot is still live. Any occurrence is a finding.
        check("no pass-only legal list while the action slot was live (observed "
              + passOnlyWhileLive + ")", passOnlyWhileLive == 0);

        // ── 3. The pass-only list IS reachable, and only under its stated
        //        preconditions — so finding 2 is a real property, not a
        //        fixture that never produces the interesting state. ─────────
        p.resetActions();
        p.setPassed(true);
        List<GameAction> passedOffers = legal(easy, st, p);
        check("a passed player receives exactly the pass-only list (size "
              + passedOffers.size() + ")", passedOffers.size() == 1
              && passedOffers.get(0).getType() == GameAction.Type.PASS);

        p.resetActions();
        while (p.getActionsLeft() > 0) p.useAction();
        List<GameAction> spentOffers = legal(easy, st, p);
        check("an action-spent player receives exactly the pass-only list (size "
              + spentOffers.size() + ")", spentOffers.size() == 1
              && spentOffers.get(0).getType() == GameAction.Type.PASS);

        // ── 4. Coverage: the 50 turns actually exercised the builder ─────────
        check("every one of the " + TURNS + " turns saw at least one non-pass offer ("
              + nonPassTurns + "/" + TURNS + ")", nonPassTurns == TURNS);
        check("offer-list size varied across the run (min " + minOffers
              + ", max " + maxOffers + ", total " + totalOffers + ")",
              minOffers > 0 && maxOffers > minOffers);

        // ── 5. Measurement, not assertion: the EASY pass band. The B5-0351
        //        contract owns the band; B5-0422 makes retuning it a human
        //        ruling. Printed so the audit carries the number. ───────────
        double passRate = passes / (double) TURNS;
        System.out.println("  [INFO] EASY pass rate " + passRate + " over " + TURNS
                           + " turns (measured, not gated)");

        System.out.println("checks: " + checks + ", failed: " + failed);
        System.out.println(failed == 0
            ? "PASS-BIAS CASCADE AUDIT VERIFIED"
            : "PASS-BIAS CASCADE AUDIT VIOLATED");
        System.exit(failed == 0 ? 0 : 1);
    }
}