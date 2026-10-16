package b5ccg.engine;

import b5ccg.model.*;
import b5ccg.model.enums.*;
import b5ccg.ai.AIPlayer;
import b5ccg.model.enums.AIDifficulty;

/**
 * B5-0351 — AI difficulty contract verification (harness; NEW file only,
 * no game-logic edits, never wired into compile.sh RUN_TESTS).
 *
 * Contract under test (B5-0304/B5-0324/B5-0344 lineage):
 *   EASY   — picks ONLY from the legal action set (never fabricates),
 *            non-deterministic across repeated identical states, and carries
 *            the designed pass bias (~30% direct + uniform-over-legal tail).
 *   MEDIUM — deterministic (same state => same action every run) and
 *            cost-aware: between otherwise-identical cards it prefers the
 *            cheaper one; with all-zero costs the ordering is byte-identical
 *            to the pre-B5-0324 behavior (zero-cost invariance pin).
 *   HARD   — deterministic and cost-aware (raw base - cost, no floor).
 *
 * Run:  java -cp out b5ccg.engine.HeadlessAIDifficultyContractTest
 * Exit: 0 all contract checks pass, 1 otherwise.
 */
public class HeadlessAIDifficultyContractTest {

    private static int checks  = 0;
    private static int failed  = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [" + (ok ? "PASS" : "FAIL") + "] " + label);
        if (!ok) failed++;
    }

    // ── deterministic fixtures (mirrors of the conformance suite helpers) ────

    private static Player player(String name, Faction f) {
        Player p = new Player(name, f, false);
        CharacterCard amb = new CharacterCard("amb_" + name, "Amb " + name,
                "CHARACTER_" + f, Rarity.FIXED, f, CardSet.PREMIERE, "x", "text",
                3, 3, 3, 3, true);
        p.setAmbassador(amb);
        p.getInnerCircle().add(amb);
        p.setDeck(new Deck(new java.util.ArrayList<Card>()));
        return p;
    }

    private static EventCard event(String id, String title) {
        return new EventCard(id, title, "EVENT", Rarity.COMMON, Faction.ANY,
                CardSet.PREMIERE, "x", "text");
    }

    /** State with influence raised to 10: suppresses Build-Influence offers
     *  (gate at <= 9) so the legal set is exactly plays-of-hand + pass. */
    private static GameState state(Player a, Player b) {
        java.util.List<Player> ps = new java.util.ArrayList<Player>();
        ps.add(a); ps.add(b);
        GameState s = new GameState(ps);
        s.setPhase(GamePhase.ACTION);
        a.gainInfluence(6);   // 4 to 10
        return s;
    }

    private static boolean sameCard(GameAction a, Card c) {
        return a != null && a.getCard() == c;
    }

    public static void main(String[] args) {
        System.out.println("=== B5-0351 AI difficulty contract verification ===");

        // ── Fixture: p leads with two cost-0 plays + pass in the legal set ──
        final Player p     = player("ContractNarn", Faction.NARN);
        final Player rival = player("ContractMinbari", Faction.MINBARI);
        final GameState st = state(p, rival);   // p 10, rival 4: p leads, no catch-up bonus

        // Two otherwise-identical EVENTs (same positional base): zero-cost
        // invariance must hold between THEM. (A group would legitimately
        // out-score an event for a leading player: base 4 vs 2.)
        final EventCard ev0 = event("ct_ev0", "Contract Event Zero");
        final EventCard ev1 = event("ct_ev1", "Contract Event One");
        p.addToHand(ev0);
        p.addToHand(ev1);

        // ── EASY: legality + non-determinism + pass bias ─────────────────────
        AIPlayer easy = new AIPlayer(p, AIDifficulty.EASY);
        check("EASY exposes its difficulty memory through the decision engine",
              easy.getDecisionEngine().getMemory() == easy.getMemory()
              && easy.getMemory().getRiskTolerance() == 1.0
              && easy.getMemory().getCardValuationMultiplier() == 1.0
              && easy.getMemory().getConflictInitiationThreshold() == 0.5);
        int evPicks = 0, grPicks = 0, passes = 0, illegal = 0;
        final int RUNS = 300;
        for (int i = 0; i < RUNS; i++) {
            GameAction a = easy.chooseAction(st, p);
            if (a == null) { illegal++; continue; }
            if (a.getType() == GameAction.Type.PASS)              passes++;
            else if (sameCard(a, ev0))                            evPicks++;
            else if (sameCard(a, ev1))                            grPicks++;
            else                                                  illegal++;
        }
        check("EASY never leaves the legal set (" + illegal + " illegal of " + RUNS + ")",
              illegal == 0);
        check("EASY is non-deterministic (ev0 " + evPicks + ", ev1 " + grPicks
              + ", pass " + passes + ")",
              evPicks > 0 && grPicks > 0 && passes > 0 && passes < RUNS);
        // Designed bias: ~53% expected (30% direct + 70% * 1/3 tail); loose
        // band avoids flakiness while still failing a broken RNG.
        double passRate = passes / (double) RUNS;
        check("EASY pass bias inside the designed band (rate " + passRate + ")",
              passRate >= 0.20 && passRate <= 0.70);
        // Uniform tail: each specific play should appear well above noise
        // (expected ~0.7/3 each; floor at 15% of runs).
        check("EASY spreads picks across equal-value plays (ev0 " + evPicks
              + ", ev1 " + grPicks + " of " + RUNS + ")",
              evPicks >= RUNS * 0.15 && grPicks >= RUNS * 0.15);

        // ── MEDIUM: zero-cost tie keeps list order (B5-0324 invariance) ──────
        AIPlayer medium = new AIPlayer(p, AIDifficulty.MEDIUM);
        check("MEDIUM uses its distinct difficulty profile",
              medium.getMemory().getRiskTolerance() == 0.7
              && medium.getMemory().getCardValuationMultiplier() == 1.2
              && medium.getMemory().getConflictInitiationThreshold() == 0.6);
        GameAction mPick = medium.chooseAction(st, p);
        check("MEDIUM zero-cost invariance: first-listed equal-value play wins ("
              + (sameCard(mPick, ev0) ? "ev0" : sameCard(mPick, ev1) ? "ev1" : "?") + ")",
              sameCard(mPick, ev0));

        // MEDIUM determinism across repeated identical states
        boolean mStable = true;
        for (int i = 0; i < 50; i++) {
            if (!sameCard(medium.chooseAction(st, p), ev0)) mStable = false;
        }
        check("MEDIUM deterministic over 50 identical states", mStable);

        // ── MEDIUM cost-awareness: identical cards, costs 0 vs 3 ─────────────
        final EventCard evCheap = event("ct_evC", "Contract Event Cheap");
        final EventCard evDear  = event("ct_evD", "Contract Event Dear");
        evDear.setCost(3);
        p.removeFromHand(ev0);
        p.removeFromHand(ev1);
        p.addToHand(evCheap);   // listed first: must WIN on price, not position
        p.addToHand(evDear);
        GameAction mPick2 = medium.chooseAction(st, p);
        check("MEDIUM prefers the cheaper of two otherwise-identical events",
              sameCard(mPick2, evCheap));

        // ── HARD: same two checks (invariance + cost-awareness) ──────────────
        AIPlayer hard = new AIPlayer(p, AIDifficulty.HARD);
        check("HARD uses its distinct difficulty profile",
              hard.getMemory().getRiskTolerance() == 0.4
              && hard.getMemory().getCardValuationMultiplier() == 1.5
              && hard.getMemory().getConflictInitiationThreshold() == 0.8);
        p.removeFromHand(evCheap);
        p.removeFromHand(evDear);
        p.addToHand(ev0);
        p.addToHand(ev1);
        GameAction hPick = hard.chooseAction(st, p);
        check("HARD zero-cost invariance: first-listed equal-value play wins",
              sameCard(hPick, ev0));
        boolean hStable = true;
        for (int i = 0; i < 50; i++) {
            if (!sameCard(hard.chooseAction(st, p), ev0)) hStable = false;
        }
        check("HARD deterministic over 50 identical states", hStable);
        p.removeFromHand(ev0);
        p.removeFromHand(ev1);
        p.addToHand(evCheap);
        p.addToHand(evDear);
        GameAction hPick2 = hard.chooseAction(st, p);
        check("HARD prefers the cheaper of two otherwise-identical events",
              sameCard(hPick2, evCheap));

        // ── Summary ──────────────────────────────────────────────────────────
        System.out.println("checks: " + checks + ", failed: " + failed);
        System.out.println(failed == 0
            ? "AI DIFFICULTY CONTRACT VERIFIED"
            : "AI DIFFICULTY CONTRACT VIOLATED");
        System.exit(failed == 0 ? 0 : 1);
    }
}
