package b5ccg.ai;

import b5ccg.model.*;
import b5ccg.model.enums.*;

/**
 * B5-2541 — AI memory difficulty contract verification (harness; NEW file only,
 * no game-logic edits, never wired into compile.sh RUN_TESTS).
 *
 * Contract under test (follow-up to B5-2497):
 *   EASY   — pass rate within 20-40% over 100 turns in a state with pass and
 *            non-pass actions available.
 *   MEDIUM — never passes when a non-pass action exists.
 *
 * Run:  java -cp out b5ccg.ai.HeadlessAIMemoryDifficultyContractTest
 * Exit: 0 all contract checks pass, 1 otherwise.
 */
public class HeadlessAIMemoryDifficultyContractTest {

    private static int checks  = 0;
    private static int failed  = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [" + (ok ? "PASS" : "FAIL") + "] " + label);
        if (!ok) failed++;
    }

    // ── deterministic fixtures (similar to HeadlessAIDifficultyContractTest) ────
    private static Player player(String name, Race f) {
        Player p = new Player(name, f, false);
        CharacterCard amb = new CharacterCard("amb_" + name, "Amb " + name,
                "CHARACTER_" + f, Rarity.FIXED, Loyalty.fromRace(f), CardSet.PREMIERE, "x", "text",
                3, 3, 3, 3, true);
        p.setAmbassador(amb);
        p.getInnerCircle().add(amb);
        p.setDeck(new Deck(new java.util.ArrayList<Card>()));
        return p;
    }

    private static EventCard event(String id, String title) {
        return new EventCard(id, title, "EVENT", Rarity.COMMON, Loyalty.ANY,
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
        System.out.println("=== B5-2541 AI memory difficulty contract verification ===");

        // ── Fixture: p leads with two cost-0 plays + pass in the legal set ──
        final Player p     = player("ContractNarn", Race.NARN);
        final Player rival = player("ContractMinbari", Race.MINBARI);
        final GameState st = state(p, rival);   // p 10, rival 4: p leads, no catch-up bonus

        // Two otherwise-identical EVENTs (same positional base): zero-cost
        // invariance must hold between THEM. (A group would legitimately
        // out-score an event for a leading player: base 4 vs 2.)
        final EventCard ev0 = event("ct_ev0", "Contract Event Zero");
        final EventCard ev1 = event("ct_ev1", "Contract Event One");
        p.addToHand(ev0);
        p.addToHand(ev1);

        // ── EASY: pass rate within 20-40% over 100 turns ───────────────────────
        AIPlayer easy = new AIPlayer(p, AIDifficulty.EASY);
        check("EASY exposes its difficulty memory through the decision engine",
              easy.getDecisionEngine().getMemory() == easy.getMemory()
              && easy.getMemory().getRiskTolerance() == 1.0
              && easy.getMemory().getCardValuationMultiplier() == 1.0
              && easy.getMemory().getConflictInitiationThreshold() == 0.5);
        int passes = 0;
        final int EASY_RUNS = 100;
        for (int i = 0; i < EASY_RUNS; i++) {
            GameAction a = easy.chooseAction(st, p);
            if (a == null) { /* illegal action, treat as fail? */ continue; }
            if (a.getType() == GameAction.Type.PASS)              passes++;
        }
        double passRate = passes / (double) EASY_RUNS;
        check("EASY pass rate inside 20-40% band (rate " + passRate + " over " + EASY_RUNS + " runs)",
              passRate >= 0.20 && passRate <= 0.40);

        // ── MEDIUM: never passes when a non-pass action exists ─────────────────
        AIPlayer medium = new AIPlayer(p, AIDifficulty.MEDIUM);
        check("MEDIUM uses its distinct difficulty profile",
              medium.getMemory().getRiskTolerance() == 0.7
              && medium.getMemory().getCardValuationMultiplier() == 1.2
              && medium.getMemory().getConflictInitiationThreshold() == 0.6);
        int mediumPasses = 0;
        final int MEDIUM_RUNS = 100;
        for (int i = 0; i < MEDIUM_RUNS; i++) {
            GameAction a = medium.chooseAction(st, p);
            if (a == null) { /* illegal action, treat as fail? */ continue; }
            if (a.getType() == GameAction.Type.PASS)              mediumPasses++;
        }
        check("MEDIUM never passes when non-pass action exists (passes " + mediumPasses + " of " + MEDIUM_RUNS + ")",
              mediumPasses == 0);

        // ── Summary ──────────────────────────────────────────────────────────
        System.out.println("checks: " + checks + ", failed: " + failed);
        System.out.println(failed == 0
            ? "AI MEMORY DIFFICULTY CONTRACT VERIFIED"
            : "AI MEMORY DIFFICULTY CONTRACT VIOLATED");
        System.exit(failed == 0 ? 0 : 1);
    }
}