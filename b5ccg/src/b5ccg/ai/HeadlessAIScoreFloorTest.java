package b5ccg.ai;

import b5ccg.model.*;
import b5ccg.model.enums.*;

/**
 * B5-4136 - AI scoring-floor sentinel verification (harness; NEW file only,
 * no game-logic edits, never wired into compile.sh RUN_TESTS).
 *
 * Contract under test (from the AIPlayer Javadoc, B5-1708 plus B5-4102):
 *   evaluateBuildInfluence(Player, CharacterCard)         -> Integer.MIN_VALUE
 *   evaluateCharacterCardPromotion(Player, CharacterCard) -> Integer.MIN_VALUE
 * when the argument pair is refused, and NEVER Integer.MIN_VALUE when it is
 * accepted - otherwise the sentinel can be compared as a real score and
 * pruneUnprofitableBuilds / pruneUnprofitablePromotions will silently drop a
 * profitable action or keep a losing one.
 *
 * Run:  java -cp out b5ccg.ai.HeadlessAIScoreFloorTest
 * Exit: 0 every check passes, 1 otherwise.
 */
public class HeadlessAIScoreFloorTest {

    private static final int FLOOR = Integer.MIN_VALUE;

    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [" + (ok ? "PASS" : "FAIL") + "] " + label);
        if (!ok) failed++;
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private static CharacterCard character(String id, Faction f) {
        return new CharacterCard(id, "Card " + id, "CHARACTER", Rarity.COMMON,
                f, CardSet.PREMIERE, "x", "text", 3, 3, 3, 3, false);
    }

    /** A bare AI is enough: both methods under test read only their arguments
     *  and this instance's RulesEngine cost authority. */
    private static AIPlayer ai(Player p) {
        return new AIPlayer(p, AIDifficulty.MEDIUM);
    }

    /** p owns two unrotated Inner-Circle characters, influence 4 and an
     *  applied pool of 4, so RulesEngine.canBuildInfluence(p) is true and a
     *  refusal can only come from the guard a given case is isolating. */
    private static Player player(String name, Faction f) {
        Player p = new Player(name, f, false);
        CharacterCard leader = character("ic_" + name, f);
        CharacterCard second = character("ic2_" + name, f);
        p.getInnerCircle().add(leader);
        p.getInnerCircle().add(second);
        return p;
    }

    public static void main(String[] args) {
        System.out.println("== B5-4136 AI scoring-floor sentinel ==");

        Player p   = player("narnOne", Faction.NARN);
        AIPlayer ai = ai(p);
        CharacterCard leader = p.getInnerCircle().get(0);
        CharacterCard second = p.getInnerCircle().get(1);
        CharacterCard outsider = character("outsider", Faction.NARN);

        // ── evaluateBuildInfluence refusals ───────────────────────────────────
        check("build: null player is refused with the sentinel",
                ai.evaluateBuildInfluence(null, leader) == FLOOR);
        check("build: null card is refused with the sentinel",
                ai.evaluateBuildInfluence(p, null) == FLOOR);
        check("build: both null is refused with the sentinel",
                ai.evaluateBuildInfluence(null, null) == FLOOR);
        check("build: a card absent from the Inner Circle is refused with the "
                + "sentinel while another valid leader is present",
                ai.evaluateBuildInfluence(p, outsider) == FLOOR);

        leader.setRotated(true);
        check("build: a rotated Inner-Circle card is refused with the sentinel "
                + "while another valid leader is present",
                ai.evaluateBuildInfluence(p, leader) == FLOOR);
        leader.setRotated(false);

        Player rich = player("richNarn", Faction.NARN);
        rich.gainInfluence(6);                       // influence 4 -> 10
        CharacterCard richLeader = rich.getInnerCircle().get(0);
        check("build: influence above 9 is refused with the sentinel even though "
                + "the card is a valid Inner-Circle leader",
                ai(rich).evaluateBuildInfluence(rich, richLeader) == FLOOR);

        // ── evaluateBuildInfluence acceptance ─────────────────────────────────
        int buildScore = ai.evaluateBuildInfluence(p, leader);
        check("build: an accepted pair never returns the sentinel",
                buildScore > FLOOR);
        check("build: the accepted pair scores the recorded break-even 0 "
                + "(horizon 3 x rating gain 1 - applied cost 3, B5-4102)",
                buildScore == 0);
        check("build: the second valid leader scores identically",
                ai.evaluateBuildInfluence(p, second) == buildScore);

        // ── evaluateCharacterCardPromotion refusals ───────────────────────────
        check("promotion: null player is refused with the sentinel",
                ai.evaluateCharacterCardPromotion(null, leader) == FLOOR);
        check("promotion: null card is refused with the sentinel",
                ai.evaluateCharacterCardPromotion(p, null) == FLOOR);
        check("promotion: both null is refused with the sentinel",
                ai.evaluateCharacterCardPromotion(null, null) == FLOOR);
        check("promotion: a card absent from Supporting Role is refused with the "
                + "sentinel even though it is an Inner-Circle member",
                ai.evaluateCharacterCardPromotion(p, leader) == FLOOR);

        // ── evaluateCharacterCardPromotion acceptance ─────────────────────────
        CharacterCard understudy = character("understudy", Faction.NARN);
        p.getSupportingRole().add(understudy);
        int promoScore = ai.evaluateCharacterCardPromotion(p, understudy);
        check("promotion: an accepted pair never returns the sentinel",
                promoScore > FLOOR);
        check("promotion: the accepted pair is finite arithmetic, not any "
                + "sentinel value",
                promoScore > Integer.MIN_VALUE + 1);

        System.out.println("checks=" + checks + " failures=" + failed);
        if (failed == 0) {
            System.out.println("AI SCORE FLOOR TEST PASSED " + checks + " of "
                    + checks + " checks");
        } else {
            System.out.println("AI SCORE FLOOR TEST FAILED " + failed + " of "
                    + checks + " checks");
        }
        System.exit(failed == 0 ? 0 : 1);
    }
}
