package b5ccg.ai;

import b5ccg.model.*;
import b5ccg.model.enums.*;

/**
 * B5-2568 — AI difficulty contract drift probe (harness; NEW file only,
 * no game-logic edits, never wired into compile.sh RUN_TESTS).
 *
 * Follow-up to B5-2541: tighter EASY band to catch drift.
 *   EASY   — pass rate within 25-35% over 200 turns ( tightens B5-2541's 20-40% )
 *   MEDIUM — never passes when a non-pass action exists.
 *
 * Run:  java -cp out b5ccg.ai.HeadlessAIDifficultyDriftProbe
 * Exit: 0 all contract checks pass, 1 otherwise.
 */
public class HeadlessAIDifficultyDriftProbe {

    private static int checks  = 0;
    private static int failed  = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [" + (ok ? "PASS" : "FAIL") + "] " + label);
        if (!ok) failed++;
    }

    // ── deterministic fixtures (similar to HeadlessAIDifficultyContractTest) ────
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

    public static void main(String[] args) {
        System.out.println("=== B5-2568 AI difficulty contract drift probe ===");

        // ── Fixture: p leads with two cost-0 plays + pass in the legal set ──
        final Player p     = player("DriftNarn", Faction.NARN);
        final Player rival = player("DriftMinbari", Faction.MINBARI);
        final GameState st = state(p, rival);   // p 10, rival 4: p leads, no catch-up bonus

        // Two otherwise-identical EVENTs (same positional base): zero-cost
        // invariance must hold between THEM. (A group would legitimately
        // out-score an event for a leading player: base 4 vs 2.)
        final EventCard ev0 = event("dr_ev0", "Drift Event Zero");
        final EventCard ev1 = event("dr_ev1", "Drift Event One");
        p.addToHand(ev0);
        p.addToHand(ev1);

        // ── EASY: pass rate within 25-35% over 200 turns (tighter than B5-2541) ────
        AIPlayer easy = new AIPlayer(p, AIDifficulty.EASY);
        check("EASY exposes its difficulty memory through the decision engine",
              easy.getDecisionEngine().getMemory() == easy.getMemory()
              && easy.getMemory().getRiskTolerance() == 1.0
              && easy.getMemory().getCardValuationMultiplier() == 1.0
              && easy.getMemory().getConflictInitiationThreshold() == 0.5);
        int passes = 0;
        final int EASY_RUNS = 200;
        for (int i = 0; i < EASY_RUNS; i++) {
            GameAction a = easy.chooseAction(st, p);
            if (a == null) { /* illegal action, treat as fail? */ continue; }
            if (a.getType() == GameAction.Type.PASS)              passes++;
        }
        double passRate = passes / (double) EASY_RUNS;
        check("EASY pass rate inside 25-35% band (rate " + passRate + " over " + EASY_RUNS + " runs)",
              passRate >= 0.25 && passRate <= 0.35);

        // ── MEDIUM: never passes when a non-pass action exists ─────────────────
        AIPlayer medium = new AIPlayer(p, AIDifficulty.MEDIUM);
        check("MEDIUM uses its distinct difficulty profile",
              medium.getMemory().getRiskTolerance() == 0.7
              && medium.getMemory().getCardValuationMultiplier() == 1.2
              && medium.getMemory().getConflictInitiationThreshold() == 0.6);
        int mediumPasses = 0;
        final int MEDIUM_RUNS = 200;
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
            ? "AI DIFFICULTY CONTRACT DRIFT PROBE VERIFIED"
            : "AI DIFFICULTY CONTRACT DRIFT PROBE VIOLATED");
        System.exit(failed == 0 ? 0 : 1);
    }
}