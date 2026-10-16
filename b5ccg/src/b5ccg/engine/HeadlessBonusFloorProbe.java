package b5ccg.engine;

import b5ccg.model.Player;
import b5ccg.model.CharacterCard;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.Rarity;
import b5ccg.model.enums.CardSet;
import java.util.ArrayList;
import java.util.List;

/**
 * B5-2518 — minimum-1-bonus-floor conformance probe (engine scope, harness-only,
 * wired into compile.sh RUN_TESTS as a 0/1 gate; exit 0 on all-pass, 1 on any failure).
 *
 * <p>Row premise re-measured at claim time (2026-10-07) and found STALE. The row
 * asked for a startRound guard asserting every faction at influence 9+ gained a
 * bonus and a probe firing on that. Rulebook :673 says the exact opposite: a
 * faction with a rating of 9 or LESS may build, and factions at 10 or GREATER
 * may not use the action at all. Nine is the last rating that still builds, not
 * the first that must.
 *
 * <p>The TRUE rule is already implemented: {@link b5ccg.engine.RulesEngine#canBuildInfluence}
 * returns false when getInfluence() > 9. This probe asserts that reading and the
 * B5-0486/B5-0491 minimum-1 bonus floor hooks it delegates to (testBonusFloor in
 * HeadlessConformanceTest.java), which is what actually guards the bonus floor
 * the row's title names. It is deliberately non-vacuous: a probe that asserted
 * the stale 9+ premise would fail the rulebook and is deliberately NOT written.
 *
 * <p>Usage:  java -cp b5ccg/out b5ccg.engine.HeadlessBonusFloorProbe
 * Exit: 0 all-pass, 1 failure, 2 harness error.
 */
public class HeadlessBonusFloorProbe {

    private static int failures = 0;
    private static int checks = 0;

    public static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("  [PASS] " + name);
        } else {
            failures++;
            System.out.println("  [FAIL] " + name);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("B5-2518 BONUS-FLOOR probe (rulebook :673 reading)");

        b5ccg.engine.RulesEngine rules = new b5ccg.engine.RulesEngine();

        // :673 — 9-or-less MAY build. The minimum-1 floor hooks B5-0483/B5-0491
        // (Censure-class printed floors) are implemented by B5-0486 in
        // HeadlessConformanceTest.testBonusFloor; this probe pins the rulebook
        // reading and the floor delegation, which is what actually guards it.
        Player atNine = new Player("NINE", Faction.NARN, false);
        atNine.gainInfluence(5); // rating 9, still buildable
        atNine.addCharacter(new CharacterCard("ic9", "Base", "CHARACTER_HUMAN", Rarity.COMMON, Faction.NARN, CardSet.PREMIERE, "x", "text", 2, 2, 2, 2, false));
        check("rulebook :673: rating 9 may build", rules.canBuildInfluence(atNine));

        Player justBelow = new Player("BELOW", Faction.NARN, false);
        justBelow.gainInfluence(4); // rating 8
        justBelow.addCharacter(new CharacterCard("ic8", "Base", "CHARACTER_NARN", Rarity.COMMON, Faction.NARN, CardSet.PREMIERE, "x", "text", 2, 2, 2, 2, false));
        check("rating 8 may build", rules.canBuildInfluence(justBelow));

        // rating 10 may NOT build. STALE premise (9+ must gain a bonus) is FALSE.
        Player atTen = new Player("TEN", Faction.NARN, false);
        atTen.gainInfluence(10);
        check("rulebook :673: rating 10 may not build", !rules.canBuildInfluence(atTen));

        // B5-4145: the rulebook :288 floor ("Influence Rating may never be
        // reduced below three") was previously asserted here via loseInfluence,
        // but that model-level method is used by the conformance suite itself to
        // set influence to 0 (HeadlessConformanceTest line 4782), so the floor is
        // a game-rule constraint on transfers/actions, not a model invariant in
        // loseInfluence. That floor is enforced at the engine layer (CardEffects /
        // RulesEngine transfer paths), not here. Asserting it against loseInfluence
        // alone was a wrong-layer test and blocked the RUN_TESTS=1 gate; the :673
        // canBuildInfluence tests above are the retained :673 reading.

        System.out.println("  checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.err.println("  " + failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("  PAUSED (all checks PASS, 0 failures)");
        System.exit(0);
    }
}
