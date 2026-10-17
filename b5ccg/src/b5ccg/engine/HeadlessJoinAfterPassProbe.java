package b5ccg.engine;
import b5ccg.model.enums.Loyalty;

import b5ccg.model.Conflict;
import b5ccg.model.ConflictCard;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.ConflictType;
import b5ccg.model.enums.Race;
import b5ccg.model.enums.Rarity;

import java.util.ArrayList;
import java.util.List;

/**
 * B5-4154: the human conflict-join window must open even when the human seat
 * passed earlier in the action round or has no actions left.
 *
 * <p>Joining is a conflict-window decision, not an action-round act: the
 * pre-B5-4154 {@code RulesEngine.canJoinConflict} refused passed and
 * zero-action players, so {@code GameController.resolveCurrentConflict}
 * skipped the join window for exactly those seats and MainWindow lit no
 * Support/Oppose buttons — while the AI join path never checked these terms.
 * This probe pins the corrected gate: passed and zero-action players may
 * join, and the exact conjunction the controller evaluates for the human
 * window holds for a passed human.
 *
 * <p>Run after compile.bat / compile.sh:
 * {@code java -cp b5ccg/out b5ccg.engine.HeadlessJoinAfterPassProbe}
 */
public class HeadlessJoinAfterPassProbe {

    private static int checks;
    private static int failures;

    private static void check(String label, boolean passed) {
        checks++;
        System.out.println("  [JOIN-PROBE] " + label + ": "
                + (passed ? "PASS" : "FAIL"));
        if (!passed) failures++;
    }

    private static ConflictCard conflict(String id, String title,
            Race faction, int reward) {
        return new ConflictCard(id, title, "CONFLICT_DIPLOMACY",
                Rarity.COMMON, Loyalty.fromRace(faction), CardSet.PREMIERE,
                "x", "", ConflictType.DIPLOMACY, reward);
    }

    private static GameState state(Player a, Player b, Player c) {
        List<Player> players = new ArrayList<Player>();
        players.add(a);
        players.add(b);
        if (c != null) players.add(c);
        GameState g = new GameState(players);
        for (int i = 0; i < players.size(); i++) {
            players.get(i).setGameState(g);
        }
        return g;
    }

    public static void main(String[] args) {
        RulesEngine rules = new RulesEngine();

        // ── S1: a passed player may still join ────────────────────────────
        {
            Player init = new Player("Init1", Race.NARN, false);
            Player joiner = new Player("Join1", Race.HUMAN, false);
            GameState g = state(init, joiner, null);
            Conflict c = new Conflict(
                    conflict("cc1", "Probe Conflict 1", Race.NARN, 2),
                    init, joiner);
            joiner.setPassed(true);
            check("S1: passed player is still an active seat",
                    g.isPlayerActive(joiner));
            check("S1: passed player may join",
                    rules.canJoinConflict(joiner, c, false));
            rules.executeJoinConflict(joiner, c, true, g, false);
            check("S1: passed player joins through the ordinary join path",
                    c.getParticipants().contains(joiner)
                    && c.isSupporting(joiner));
        }

        // ── S2: a zero-actions player may still join ──────────────────────
        {
            Player init = new Player("Init2", Race.NARN, false);
            Player joiner = new Player("Join2", Race.HUMAN, false);
            GameState g = state(init, joiner, null);
            Conflict c = new Conflict(
                    conflict("cc2", "Probe Conflict 2", Race.NARN, 2),
                    init, joiner);
            joiner.useAction();
            check("S2: fixture really has zero actions left",
                    joiner.getActionsLeft() == 0);
            check("S2: zero-actions player may join",
                    rules.canJoinConflict(joiner, c, false));
            rules.executeJoinConflict(joiner, c, false, g, false);
            check("S2: zero-actions player joins on the oppose side",
                    c.getParticipants().contains(joiner)
                    && c.isOpposing(joiner));
        }

        // ── S3: the controller's human-window conjunction holds for a ─────
        // passed human (GameController.resolveCurrentConflict gate shape).
        {
            Player init = new Player("Init3", Race.MINBARI, false);
            Player human = new Player("Human3", Race.HUMAN, true);
            GameState g = state(init, human, null);
            Conflict c = new Conflict(
                    conflict("cc3", "Probe Conflict 3", Race.MINBARI, 2),
                    init, human);
            human.setPassed(true);
            human.useAction();
            boolean window = human.isHuman() && g.isPlayerActive(human)
                    && human != c.getInitiator()
                    && rules.canJoinConflict(human, c, false)
                    && c.canJoinConflict(human);
            check("S3: join window opens for a passed zero-action human",
                    window);
        }

        // ── S4: the remaining refusals still hold (controls) ──────────────
        {
            Player init = new Player("Init4", Race.NARN, false);
            Player joiner = new Player("Join4", Race.HUMAN, false);
            GameState g = state(init, joiner, null);
            Conflict c = new Conflict(
                    conflict("cc4", "Probe Conflict 4", Race.NARN, 2),
                    init, joiner);
            check("S4: no conflict refuses",
                    !rules.canJoinConflict(joiner, null));
            check("S4: null player refuses",
                    !rules.canJoinConflict(null, c));
            rules.executeJoinConflict(joiner, c, true, g, true);
            check("S4: an existing participant may not re-join",
                    !rules.canJoinConflict(joiner, c));
            joiner.setHasForfeited(true);
            check("S4: model gate still refuses a forfeited player",
                    !c.canJoinConflict(joiner));
        }

        // ── Summary ───────────────────────────────────────────────────────
        System.out.println("Join-after-pass probe: " + checks
                + " checks, " + failures + " failures.");
        System.exit(failures == 0 ? 0 : 1);
    }
}
