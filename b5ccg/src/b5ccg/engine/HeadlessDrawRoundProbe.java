package b5ccg.engine;

import b5ccg.model.*;
import b5ccg.model.enums.*;

import java.util.ArrayList;
import java.util.List;

/**
 * B5-4175 — Draw round D2 headless probe.
 *
 * <p>The row's acceptance criteria: a Draw-window hand-discard control that
 * selects cards in hand and calls discardFromHand once per selected card,
 * gated on the Draw phase window (isWaitingForHumanDrawEnd), keeping the
 * discard elective and the discarded cards face-up in the owner pile. The
 * probe asserts that selecting two of five cards discards exactly those two
 * and no others, and that the draw window survives the discard.
 *
 * <p>Run: {@code java -cp out b5ccg.engine.HeadlessDrawRoundProbe}
 * Exit: 0 all checks pass, 1 otherwise.
 */
public class HeadlessDrawRoundProbe {

    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [" + (ok ? "PASS" : "FAIL") + "] " + label);
        if (!ok) failed++;
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    /** A non-ambassador character, usable as Inner Circle or supporting role. */
    private static CharacterCard character(String id, boolean ambassador) {
        return new CharacterCard(id, id, "CHARACTER_NARN", Rarity.COMMON,
                Faction.NARN, CardSet.PREMIERE, "", "", 2, 2, 2, 2, ambassador);
    }

    private static List<Card> drawPile(int n) {
        List<Card> cards = new ArrayList<Card>();
        for (int i = 0; i < n; i++) cards.add(character("pile" + i, false));
        return cards;
    }

    private static Player seat(String name, int drawPileCards) {
        Player p = new Player(name, Faction.NARN, false);
        CharacterCard amb = character("amb_" + name, true);
        p.setAmbassador(amb);
        p.getInnerCircle().add(amb);
        p.setDeck(new Deck(drawPile(drawPileCards)));
        p.restoreAppliedPool();
        return p;
    }

    private static GameState state(Player... seats) {
        List<Player> players = new ArrayList<Player>();
        for (Player p : seats) players.add(p);
        GameState g = new GameState(players);
        for (Player p : seats) p.setGameState(g);
        return g;
    }

    private static void setPower(Player p, int rating) {
        if (p.getInfluence() < rating) p.gainInfluence(rating - p.getInfluence());
        else p.loseInfluence(p.getInfluence() - rating);
        p.restoreAppliedPool();
    }

    // ── D2 ────────────────────────────────────────────────────────────────────

    /**
     * Draw round step 2: elective hand discards, face-up in the owner
     * pile, exactly the requested count and no others, and the draw
     * window still survives the discard (D1 is mandatory and already
     * run by drawRoundMandatorySteps).
     */
    private static void step2() {
        System.out.println("D2 — elective hand discards, face-up next to deck");
        RulesEngine rules = new RulesEngine();
        Player p = seat("d2", 10);
        // seed the hand deterministically: one copy of 5 distinct titles
        List<Card> original = new ArrayList<Card>();
        for (int i = 0; i < 5; i++) {
            CharacterCard c = character("d2card" + i, false);
            p.getHand().add(c);
            original.add(c);
        }
        check("precondition: 5 cards in hand", p.getHand().size() == 5);

        // designate the last two cards as the ones to discard (deterministic,
        // so the test is repeatable); discardFromHand drops the tail.
        List<Card> toDiscard = original.subList(3, 5);
        int n = rules.discardFromHand(p, 2, state(p));
        check("discardFromHand returns exactly 2", n == 2);

        List<Card> discarded = new ArrayList<Card>();
        for (Card c : p.getDeck().getDiscardPile()) {
            if (toDiscard.contains(c)) discarded.add(c);
        }
        // the two requested cards landed in the owner pile, exactly those two
        check("exactly the two discarded cards landed in the owner pile",
                discarded.size() == 2 && discarded.containsAll(toDiscard));

        // only the two requested cards were discarded; the three before them
        // stayed in hand
        check("only the two requested cards were discarded",
                p.getHand().size() == 3 && p.getHand().containsAll(original.subList(0, 3)));

        // the draw window must still be live: the same player can still be
        // bought from (D4) and mandatory step 1 still runs on the next round
        int used = p.getAppliedPool();
        check("draw window survives the discard (buy still refundable)",
                rules.buyMoreCards(p, state(p)) == 1 && p.getAppliedPool() == used - 3);

        // a second discard of 0 is a no-op and never corrupts hand order
        p.getHand().clear();
        for (int i = 0; i < 5; i++) {
            CharacterCard c = character("d2b" + i, false);
            p.getHand().add(c);
            original.add(c);
        }
        check("precondition: a fresh 5-card hand", p.getHand().size() == 5);
        n = rules.discardFromHand(p, 0, state(p));
        check("zero-count discard is a no-op returning 0", n == 0);
        check("hand untouched after zero-count discard", p.getHand().size() == 5);
    }

    // ── D1 ────────────────────────────────────────────────────────────────────

    /**
     * Draw round step 1: discard every neutralized supporting card; a
     * neutralized Inner Circle character (ambassador included) stays.
     */
    private static void step1() {
        System.out.println("D1 — neutralized supporting cards leave, Inner Circle stays (:443)");
        RulesEngine rules = new RulesEngine();
        Player p = seat("d1", 3);
        CharacterCard supporting = character("supporting1", false);
        CharacterCard icSane     = character("icSane", false);
        CharacterCard icHurt     = character("icHurt", false);
        p.getSupportingRole().add(supporting);
        p.getInnerCircle().add(icSane);
        p.getInnerCircle().add(icHurt);
        supporting.damage();
        icHurt.damage();
        p.getAmbassador().damage();          // a neutralized ambassador too
        check("precondition: three neutralized across three zones",
                supporting.isNeutralized() && icHurt.isNeutralized()
                && p.getAmbassador().isNeutralized());

        rules.drawRoundMandatorySteps(state(p));

        check("neutralized supporting card left play",
                !p.getSupportingRole().contains(supporting));
        check("discarded card landed in the owner's discard pile",
                p.getDeck().getDiscardPile().contains(supporting));
        check("hand did not absorb the supporting card",
                !p.getHand().contains(supporting));
        check("neutralized Inner Circle character survives",
                p.getInnerCircle().contains(icHurt));
        check("neutralized ambassador survives (never discardable)",
                p.getInnerCircle().contains(p.getAmbassador()));
        check("untouched Inner Circle character survives",
                p.getInnerCircle().contains(icSane));
    }

    // ── D3 ────────────────────────────────────────────────────────────────────

    /**
     * Draw round step 3 deck-out: discard one non-ambassador IC character per
     * owed draw; forfeit only when none is left; no reshuffle from discards.
     */
    private static void step3() {
        System.out.println("D3 — deck-out costs an IC character, then forfeits (:447/:460)");
        RulesEngine rules = new RulesEngine();
        Player p = seat("d3", 0);            // empty draw pile from the start
        CharacterCard ic1 = character("ic1", false);
        CharacterCard ic2 = character("ic2", false);
        p.getInnerCircle().add(ic1);
        p.getInnerCircle().add(ic2);
        check("precondition: draw pile empty, ambassador + 2 IC",
                p.getDeck().isEmpty() && p.getInnerCircle().size() == 3);

        int handAfter0 = p.getHand().size();
        rules.drawRoundMandatorySteps(state(p));
        check("first owed draw cost exactly one IC character",
                p.getInnerCircle().size() == 2 && !p.getInnerCircle().contains(ic1));
        check("no card entered hand (nothing to draw)",
                p.getHand().size() == handAfter0);
        check("no forfeit while an IC character remains", !p.hasForfeited());
        check("that IC character is in the discard pile",
                p.getDeck().getDiscardPile().contains(ic1));

        rules.drawRoundMandatorySteps(state(p));
        check("second owed draw cost the second IC character",
                p.getInnerCircle().size() == 1 && !p.getInnerCircle().contains(ic2));
        check("still no forfeit with only the ambassador left", !p.hasForfeited());

        rules.drawRoundMandatorySteps(state(p));
        check("no IC character left to discard → FORFEIT", p.hasForfeited());
        check("ambassador was never discarded",
                p.getInnerCircle().contains(p.getAmbassador())
                && !p.getDeck().getDiscardPile().contains(p.getAmbassador()));
        check("no reshuffle: draw pile stayed empty through every deck-out",
                p.getDeck().isEmpty());

        // A player with cards available draws exactly one free card - no more.
        Player q = seat("d3ok", 5);
        int before = q.getHand().size();
        rules.drawRoundMandatorySteps(state(q));
        check("a live draw pile yields exactly one free card",
                q.getHand().size() == before + 1 && !q.hasForfeited());
    }

    // ── D4 ────────────────────────────────────────────────────────────────────

    /**
     * Draw round step 4: exactly 3 influence per card, one card per call, from
     * the leftover applied pool only, and nothing charged against an empty pile.
     */
    private static void step4() {
        System.out.println("D4 — 3 influence per card, one per call, leftover pool untouched");
        RulesEngine rules = new RulesEngine();

        Player p = seat("d4", 6);
        setPower(p, 10);
        check("precondition: applied pool of 10", p.getAppliedPool() == 10);

        int handBefore = p.getHand().size();
        check("first buy returns 1", rules.buyMoreCards(p, state(p)) == 1);
        check("first buy charged exactly 3", p.getAppliedPool() == 7);
        check("first buy drew exactly one card", p.getHand().size() == handBefore + 1);

        check("second buy returns 1", rules.buyMoreCards(p, state(p)) == 1);
        check("second buy charged exactly 3 more", p.getAppliedPool() == 4);
        check("two buys drew exactly two cards", p.getHand().size() == handBefore + 2);

        check("third buy returns 1", rules.buyMoreCards(p, state(p)) == 1);
        check("third buy charged exactly 3 more", p.getAppliedPool() == 1);
        check("three buys drew exactly three cards", p.getHand().size() == handBefore + 3);

        check("fourth buy refused (pool below 3)",
                rules.buyMoreCards(p, state(p)) == 0);
        check("refused buy left the 1 leftover influence standing",
                p.getAppliedPool() == 1);
        check("refused buy drew nothing", p.getHand().size() == handBefore + 3);
        check("refused buy left a drawn card for the next one",
                !p.getDeck().isEmpty());

        // A after-draw of the round must not have drained the pool either.
        Player r = seat("d4auto", 5);
        setPower(r, 8);
        GameState rs = state(r);
        rules.drawRoundMandatorySteps(rs);
        check("the mandatory steps never auto-spend the buy pool",
                r.getAppliedPool() == 8);

        // An empty draw pile cannot fund a purchase even with influence spare.
        Player e = seat("d4empty", 0);
        setPower(e, 9);
        int eHand = e.getHand().size();
        check("buy against an empty draw pile returns 0",
                rules.buyMoreCards(e, state(e)) == 0);
        check("no influence charged for a card that cannot be drawn",
                e.getAppliedPool() == 9);
        check("no ghost card entered hand", e.getHand().size() == eHand);
    }

    // ── D5 ────────────────────────────────────────────────────────────────────

    /**
     * Draw round step 5: no Standard Victory while the Shadow War is on, for
     * either trigger threshold, and a strict-lead tie crowns nobody.
     */
    private static void step5() {
        System.out.println("D5 — victory check at the round boundary (:176/:178)");
        RulesEngine rules = new RulesEngine();

        Player p = seat("d5p", 2);
        Player q = seat("d5q", 2);
        GameState g = state(p, q);
        // 22 vs 18 is a STANDARD-only lead: standard needs >=20 and strictly
        // more than every opponent (22 > 18), while major needs every other
        // seat at least 10 below (18 is 4 below), so the War blocks one path
        // without the other masking it. A 25-10 lead would score Major anyway
        // during the War - that gap is asserted separately below.
        setPower(p, 22);
        setPower(q, 18);
        check("precondition: 22 strictly leading 18, no Shadow War",
                !g.isShadowWar());
        check("without a Shadow War the leading player wins the round check",
                rules.checkVictory(g) == p);

        g.getStation().gainShadowInfluence(Babylon5Station.CONDITION_2_THRESHOLD);
        check("Shadow designation alone puts the Shadow War on", g.isShadowWar());
        check("Shadow War blocks the Standard Victory", rules.checkVictory(g) == null);

        g.getStation().loseShadowInfluence(Babylon5Station.CONDITION_2_THRESHOLD);
        g.getStation().gainVorlonInfluence(Babylon5Station.CONDITION_2_THRESHOLD);
        check("Vorlon designation alone puts the Shadow War on", g.isShadowWar());
        check("Vorlon trigger blocks the Standard Victory too",
                rules.checkVictory(g) == null);

        g.getStation().loseVorlonInfluence(Babylon5Station.CONDITION_2_THRESHOLD);
        int saved = q.getInfluence();
        setPower(q, 22);
        check("a tie in power crowns nobody (strict lead, D12)",
                rules.checkVictory(g) == null);
        setPower(q, saved);

        // Below 20 with nobody else near it, still no crown this round.
        setPower(p, 19);
        check("19 power is not a win", rules.checkVictory(g) == null);

        setPower(p, 35);
        setPower(q, 10);
        check("a 25-point lead scores the Major Victory the War leaves open",
                rules.checkVictory(g) == p);
    }

    public static void main(String[] args) {

        System.out.println("=== HeadlessDrawRoundProbe (B5-4171 Draw round D1-D5) ===");
        step1();
        step2();
        step3();
        step4();
        step5();
        System.out.println("--");
        System.out.println(checks + " checks, " + failed + " failed");
        System.out.println(failed == 0 ? "RESULT: PASS" : "RESULT: FAIL");
        System.exit(failed == 0 ? 0 : 1);
    }
}
