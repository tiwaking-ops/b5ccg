package b5ccg.engine;

import b5ccg.model.GameState;
import b5ccg.model.Babylon5Station;
import b5ccg.model.Player;
import b5ccg.model.enums.Faction;
import java.util.ArrayList;
import java.util.List;

/**
 * B5-0382: focused scenario probe for Babylon 5 station victory condition 2.
 *
 * Harness only. Exercises the live RulesEngine against synthetic player states
 * and does not edit game state implementation or production rules.
 *
 * Run after compile.bat or compile.sh:
 *   java -cp b5ccg/out b5ccg.engine.HeadlessStationVictoryTest
 */
public class HeadlessStationVictoryTest {

    private static int checks;
    private static int failures;

    private static void check(String label, boolean passed) {
        checks++;
        System.out.println("  [STA] " + label + ": " + (passed ? "PASS" : "FAIL"));
        if (!passed) failures++;
    }

    private static GameState state(Player first, Player second) {
        List<Player> players = new ArrayList<Player>();
        players.add(first);
        players.add(second);
        return new GameState(players);
    }

    public static void main(String[] args) {
        RulesEngine rules = new RulesEngine();
        Player leader = new Player("Station Leader", Faction.NARN, false);
        Player rival = new Player("Rival", Faction.MINBARI, false);
        GameState game = state(leader, rival);

        leader.gainInfluence(1); // 5 to 4: strict lead, below player victory threshold.
        game.getStation().gainInfluence(19);
        check("19 station influence with strict leader does not crown",
                rules.checkVictory(game) == null);

        game.getStation().gainInfluence(1);
        check("20 station influence crowns the unique strict leader",
                rules.checkVictory(game) == leader);

        rival.gainInfluence(1); // 5 to 5.
        check("20 station influence with tied leaders crowns nobody",
                rules.checkVictory(game) == null);

        rival.loseInfluence(1);
        game.setShadowInfluence(Babylon5Station.CONDITION_2_THRESHOLD);
        check("Shadow War suppresses station condition 2",
                rules.checkVictory(game) == null);

        game.setShadowInfluence(0);
        game.setVorlonInfluence(Babylon5Station.CONDITION_2_THRESHOLD);
        check("Vorlon-triggered Shadow War suppresses station condition 2",
                rules.checkVictory(game) == null);

        game.setVorlonInfluence(0);
        check("clearing Shadow War restores station condition 2",
                rules.checkVictory(game) == leader);

        System.out.println("Station victory scenario probe: " + checks + " checks, "
                + failures + " failures.");
        System.exit(failures == 0 ? 0 : 1);
    }
}
