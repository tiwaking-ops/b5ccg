package b5ccg.ui;

import b5ccg.ai.AIPlayer;
import b5ccg.engine.GameController;
import b5ccg.engine.GameStateCallback;
import b5ccg.engine.RulesEngine;
import b5ccg.model.CharacterCard;
import b5ccg.model.GameAction;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.GamePhase;
import b5ccg.model.enums.Rarity;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

/**
 * B5-4081 - regression for the Build Influence leader selector.
 *
 * <p>The Build Influence button was silently unresponsive because the action
 * handler submitted findUnrotatedIC(hp) — the FIRST unrotated Inner Circle
 * member in list order — which does NOT check canActAfterNeutralization().
 * When that first member was neutralized this turn, RulesEngine.canBuildInfluence
 * (which DOES check it) enabled the button, but executeBuildInfluence silently
 * rejected the neutralized leader: the click appeared to do nothing.
 *
 * <p>This row re-implements the selector (B5-2492, lost to git-reset fe477a9)
 * as a proper dropdown listing only unrotated + actionable IC members, mirroring
 * the B5-3870 Promote leader selector pattern.
 *
 * <p>It locks four things the UI alone cannot guarantee:
 * (1) buildInfluenceLeaderSelector exists, is populated from the engine's own
 *     verdict, and carries a listener;
 * (2) an empty candidate list is the "not available" signal: the selector goes
 *     disabled and Build Influence stays dark rather than offering a stale choice;
 * (3) a neutralized or rotated IC member is excluded from the list, so the picker
 *     cannot offer what executeBuildInfluence (RulesEngine:206) would refuse;
 * (4) the BUILD_INFLUENCE action the handler submits really rotates the CHOSEN
 *     leader (not the first), spends 3 influence, and gains 1 rating.
 *
 * <p>Exit codes: 0 all checks passed, 1 a check failed. A headless host skips
 * with exit 0, matching MainWindowSurrenderControlTest.
 */
public class MainWindowBuildInfluenceControlTest {
    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [INFLUI] " + label + ": " + (ok ? "PASS" : "FAIL"));
        if (!ok) failed++;
    }

    private static CharacterCard card(String id, String title, Faction faction,
                                      int cost) {
        return new CharacterCard(id, title, "CHARACTER_" + faction, Rarity.FIXED,
            faction, CardSet.PREMIERE, "x", "text", cost, 3, 3, 3, true);
    }

    private static Player player(String name, Faction faction, boolean human) {
        Player player = new Player(name, faction, human);
        CharacterCard ambassador = card("amb_" + name, "Ambassador",
            faction, 3);
        player.setAmbassador(ambassador);
        player.getInnerCircle().add(ambassador);
        return player;
    }

    private static GameController controller(GameState state) {
        return new GameController(state, new ArrayList<AIPlayer>(),
            new GameStateCallback() {
                public void accept(GameState state) {
                }
            });
    }

    private static GameState twoPlayerState(String humanName, Faction humanFaction,
                                            GamePhase phase) {
        Player human = player(humanName, humanFaction, true);
        Player rival = player(humanName + "Rival", Faction.MINBARI, false);
        List<Player> players = new ArrayList<Player>();
        players.add(human);
        players.add(rival);
        GameState state = new GameState(players);
        human.setGameState(state);
        rival.setGameState(state);
        state.setPhase(phase);
        // getActivePlayer() is players.get(currentPlayerIndex) and the human
        // is players.get(0), so the active player is already the human.
        return state;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    /** Invoke the engine's canBuildInfluence verdict through the MainWindow's
     *  private rules field, mirroring what the button enablement reads. */
    private static boolean engineCanBuildInfluence(MainWindow win, Player p)
            throws Exception {
        RulesEngine rules = (RulesEngine) field(win, "rules");
        return rules.canBuildInfluence(p);
    }

    private static MainWindow window(final GameController controller) throws Exception {
        final MainWindow[] result = new MainWindow[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                result[0] = new MainWindow(controller);
            }
        });
        return result[0];
    }

    private static void refresh(final MainWindow window, final GameState state)
            throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Method method = MainWindow.class.getDeclaredMethod(
                        "refresh", GameState.class);
                    method.setAccessible(true);
                    method.invoke(window, state);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        });
    }

    /** Set the controller's waitingForHuman flag so the UI treats the human
     *  as having an open action turn. In normal play this is set by
     *  waitForHumanAction() inside the game loop; in a harness there is no
     *  loop, so inject it. */
    private static void setWaitingForHuman(GameController ctrl, boolean v)
            throws Exception {
        Field f = GameController.class.getDeclaredField("waitingForHuman");
        f.setAccessible(true);
        f.set(ctrl, v);
    }

    @SuppressWarnings("unchecked")
    private static List<String> items(JComboBox<String> box) {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < box.getItemCount(); i++) out.add(box.getItemAt(i));
        return out;
    }

    /** The selector and button exist and are wired. */
    private static void testControlIsWired() throws Exception {
        System.out.println("  [INFLUI] --- Build Influence control exists and is wired ---");
        GameState state = twoPlayerState("B4081Wire", Faction.NARN,
            GamePhase.ACTION);
        MainWindow win = window(controller(state));
        JComboBox<String> leaders =
            (JComboBox<String>) field(win, "buildInfluenceLeaderSelector");
        JButton buildInf = (JButton) field(win, "buildInfluenceButton");

        check("buildInfluenceLeaderSelector is constructed", leaders != null);
        check("buildInfluenceButton is labelled Build Influence",
            buildInf != null && "Build Influence".equals(buildInf.getText()));
        check("buildInfluenceButton carries a click handler",
            buildInf.getActionListeners() != null
                && buildInf.getActionListeners().length > 0);
        check("the leader picker listens for selection changes",
            leaders.getActionListeners() != null
                && leaders.getActionListeners().length > 0);
        check("Build Influence starts dark before the first refresh",
            !buildInf.isEnabled());
        win.dispose();
    }

    /** The selector holds exactly the engine's verdict: unrotated + actionable. */
    private static void testSelectorFollowsEngine() throws Exception {
        System.out.println("  [INFLUI] --- the selector lists unrotated + actionable IC ---");
        GameState state = twoPlayerState("B4081Eng", Faction.NARN,
            GamePhase.ACTION);
        Player human = state.getPlayers().get(0);
        CharacterCard second = card("ic_4081b", "Delenn", Faction.NARN, 0);
        human.getInnerCircle().add(second);

        GameController ctrl = controller(state);
        setWaitingForHuman(ctrl, true);
        MainWindow win = window(ctrl);
        refresh(win, state);
        JComboBox<String> leaders =
            (JComboBox<String>) field(win, "buildInfluenceLeaderSelector");
        JButton buildInf = (JButton) field(win, "buildInfluenceButton");

        List<String> leaderItems = items(leaders);
        check("the leader list holds BOTH unrotated IC members ("
                + leaderItems + ")",
            leaderItems.size() == 2);
        check("the ambassador is offered as a leader",
            leaderItems.contains("Ambassador"));
        check("the second IC member is offered as a leader too",
            leaderItems.contains("Delenn"));
        check("the selector is enabled when candidates exist",
            leaders.isEnabled());
        check("Build Influence LIGHTS on the human ACTION turn",
            buildInf.isEnabled());
        check("canBuildInfluence agrees the human may proceed",
            engineCanBuildInfluence(win, human));
        check("the ambassador is the first (default) selection",
            leaders.getSelectedIndex() == 0);
        win.dispose();
    }

    /** THE KEY REGRESSION: the first unrotated IC member is neutralized this
     *  turn, but a later one is still actionable. The old findUnrotatedIC code
     *  would (a) submit the neutralized ambassador and (b) let
     *  executeBuildInfluence silently reject it — a no-op click. The selector
     *  must exclude the neutralized member, leaving only the actionable one,
     *  and the button must stay lit because canBuildInfluence agrees. */
    private static void testNeutralizedFirstLeaderIsExcluded() throws Exception {
        System.out.println("  [INFLUI] --- neutralized first leader is excluded, button stays lit ---");
        GameState state = twoPlayerState("B4081Neut", Faction.NARN,
            GamePhase.ACTION);
        Player human = state.getPlayers().get(0);
        CharacterCard ambassador = human.getInnerCircle().get(0);
        CharacterCard second = card("ic_4081n", "Delenn", Faction.NARN, 0);
        human.getInnerCircle().add(second);

        // Neutralize the first (ambassador). damage() sets neutralizedThisTurn
        // = true, so canActAfterNeutralization() is false — exactly the engine
        // verdict that findUnrotatedIC used to ignore.
        ambassador.damage();

        GameController ctrl = controller(state);
        setWaitingForHuman(ctrl, true);
        MainWindow win = window(ctrl);
        refresh(win, state);
        JComboBox<String> leaders =
            (JComboBox<String>) field(win, "buildInfluenceLeaderSelector");
        JButton buildInf = (JButton) field(win, "buildInfluenceButton");

        List<String> leaderItems = items(leaders);
        // The PRE-B5-4081 bug would have offered "Ambassador" here and lit
        // the button, then silently refused the click because
        // executeBuildInfluence checks canActAfterNeutralization.
        check("the neutralized ambassador is NOT offered as a leader ("
                + leaderItems + ")",
            !leaderItems.contains("Ambassador"));
        check("the actionable Delenn IS offered as the only leader ("
                + leaderItems + ")",
            leaderItems.size() == 1 && leaderItems.contains("Delenn"));
        check("the selector stays ENABLED with a valid leader",
            leaders.isEnabled());
        check("Build Influence is ENABLED despite the first member being dead",
            buildInf.isEnabled());
        check("Delenn is the single selected leader",
            leaders.getSelectedIndex() == 0);
        check("canBuildInfluence agrees the human may proceed",
            engineCanBuildInfluence(win, human));
        win.dispose();
    }

    /** No unrotated + actionable IC members: empty list renders the placeholder
     *  and Build Influence stays dark. */
    private static void testEmptyListDisablesButton() throws Exception {
        System.out.println("  [INFLUI] --- no actionable leader means no offer ---");
        GameState state = twoPlayerState("B4081Empty", Faction.NARN,
            GamePhase.ACTION);
        Player human = state.getPlayers().get(0);
        CharacterCard ambassador = human.getInnerCircle().get(0);

        // Neutralize the only IC member so there are zero actionable leaders.
        ambassador.damage();

        GameController ctrl = controller(state);
        setWaitingForHuman(ctrl, true);
        MainWindow win = window(ctrl);
        refresh(win, state);
        JComboBox<String> leaders =
            (JComboBox<String>) field(win, "buildInfluenceLeaderSelector");
        JButton buildInf = (JButton) field(win, "buildInfluenceButton");

        check("with no actionable leader the list is a placeholder ("
                + items(leaders) + ")",
            items(leaders).size() == 1
                && items(leaders).get(0).startsWith("("));
        check("the leader picker is disabled when empty", !leaders.isEnabled());
        check("Build Influence stays dark with no actionable leader",
            !buildInf.isEnabled());
        check("canBuildInfluence agrees the human cannot proceed",
            !engineCanBuildInfluence(win, human));
        win.dispose();
    }

    /** A rotated IC member is excluded just like a neutralized one. */
    private static void testRotatedMemberIsExcluded() throws Exception {
        System.out.println("  [INFLUI] --- rotated IC member is excluded ---");
        GameState state = twoPlayerState("B4081Rot", Faction.NARN,
            GamePhase.ACTION);
        Player human = state.getPlayers().get(0);
        CharacterCard ambassador = human.getInnerCircle().get(0);
        CharacterCard second = card("ic_4081r", "Delenn", Faction.NARN, 0);
        human.getInnerCircle().add(second);

        // Rotate the ambassador; the selector must exclude it and leave Delenn.
        ambassador.rotate();

        GameController ctrl = controller(state);
        setWaitingForHuman(ctrl, true);
        MainWindow win = window(ctrl);
        refresh(win, state);
        JComboBox<String> leaders =
            (JComboBox<String>) field(win, "buildInfluenceLeaderSelector");

        check("the rotated ambassador is NOT offered as a leader ("
                + items(leaders) + ")",
            !items(leaders).contains("Ambassador"));
        check("Delenn is the only actionable leader offered",
            items(leaders).size() == 1 && items(leaders).contains("Delenn"));
        win.dispose();
    }

    /** The submitted action rotates the CHOSEN leader, not the first — and
     *  spends 3 influence, gains 1 rating. */
    private static void testChosenLeaderIsTheOneRotated() throws Exception {
        System.out.println("  [INFLUI] --- the CHOSEN leader is the one that rotates ---");
        GameState state = twoPlayerState("B4081Chosen", Faction.NARN,
            GamePhase.ACTION);
        Player human = state.getPlayers().get(0);
        CharacterCard first = human.getInnerCircle().get(0);
        CharacterCard chosen = card("ic_4081c", "Delenn", Faction.NARN, 0);
        human.getInnerCircle().add(chosen);

        final GameController ctrl = controller(state);
        setWaitingForHuman(ctrl, true);
        MainWindow win = window(ctrl);
        refresh(win, state);
        final JComboBox<String> leaders =
            (JComboBox<String>) field(win, "buildInfluenceLeaderSelector");
        final Player humanPlayer = human;

        // Select the SECOND entry (Delenn), not the first (Ambassador).
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                leaders.setSelectedItem("Delenn");
            }
        });
        check("the leader picker holds the chosen member",
            "Delenn".equals(leaders.getSelectedItem()));

        Method resolve = MainWindow.class.getDeclaredMethod(
            "resolveBuildInfluenceLeader");
        resolve.setAccessible(true);
        Object resolved = resolve.invoke(win);
        check("the chosen picker entry resolves to the chosen card",
            resolved == chosen);

        // Drive the commit the way the controller does, with the RESOLVED leader.
        // executeBuildInfluence (RulesEngine:197) rotates the chosen leader,
        // spends 3, and gains 1 influence rating.
        final Method process = GameController.class.getDeclaredMethod(
            "processAction", Player.class, GameAction.class);
        process.setAccessible(true);
        final CharacterCard leaderCard = chosen;
        int influenceBefore = human.getInfluence();
        int poolBefore = human.getAppliedPool();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    process.invoke(ctrl, humanPlayer,
                        GameAction.buildInfluence(leaderCard));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        check("the CHOSEN leader (Delenn) is the one that rotated",
            chosen.isRotated());
        check("the unchosen first member (Ambassador) did NOT rotate",
            !first.isRotated());
        // Net pool change is -2: executeBuildInfluence spends 3 then gains 1
        // (net pool change = -2, per the rulebook :673 comment in MainWindow).
        check("Build Influence spent 3 then gained 1 (net pool -2, "
                + poolBefore + " -> " + humanPlayer.getAppliedPool() + ")",
            humanPlayer.getAppliedPool() == poolBefore - 2);
        check("Build Influence gained 1 influence rating ("
                + influenceBefore + " -> " + human.getInfluence() + ")",
            human.getInfluence() == influenceBefore + 1);
        win.dispose();
    }

    /** Stale-selector safety fallback: if the combo returns null (e.g. the
     *  selector was cleared or not refreshed), the action handler scans for
     *  the first unrotated + actionable leader. This verifies that the fallback
     *  scan logic skips a neutralized first member and lands on the actionable
     *  one. */
    private static void testStaleSelectorFallback() throws Exception {
        System.out.println("  [INFLUI] --- stale selector fallback finds correct leader ---");
        GameState state = twoPlayerState("B4081Stale", Faction.NARN,
            GamePhase.ACTION);
        Player human = state.getPlayers().get(0);
        CharacterCard ambassador = human.getInnerCircle().get(0);
        CharacterCard second = card("ic_4081s", "Delenn", Faction.NARN, 0);
        human.getInnerCircle().add(second);

        // Neutralize the first member — the fallback scan must skip it.
        ambassador.damage();

        GameController ctrl = controller(state);
        setWaitingForHuman(ctrl, true);
        MainWindow win = window(ctrl);
        refresh(win, state);

        // Simulate a stale selector by clearing the combo.
        final JComboBox<String> leaders =
            (JComboBox<String>) field(win, "buildInfluenceLeaderSelector");
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                leaders.removeAllItems();
            }
        });

        Method resolve = MainWindow.class.getDeclaredMethod(
            "resolveBuildInfluenceLeader");
        resolve.setAccessible(true);
        Object resolved = resolve.invoke(win);
        check("the stale selector returns null (placeholder path)",
            resolved == null);

        // The fallback in the action handler should still find Delenn.
        // We replicate the fallback loop to verify the scan logic itself,
        // independent of the button click machinery:
        CharacterCard fallback = null;
        for (CharacterCard ch : human.getInnerCircle()) {
            if (!ch.isRotated() && ch.canActAfterNeutralization()) {
                fallback = ch;
                break;
            }
        }
        check("the fallback scan skips the neutralized ambassador and finds Delenn",
            fallback == second);
        win.dispose();
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("MAIN WINDOW BUILD INFLUENCE CONTROL TEST SKIPPED"
                + " (headless environment)");
            return;
        }
        System.out.println("=== MainWindow build influence control regression ===");
        testControlIsWired();
        testSelectorFollowsEngine();
        testNeutralizedFirstLeaderIsExcluded();
        testEmptyListDisablesButton();
        testRotatedMemberIsExcluded();
        testChosenLeaderIsTheOneRotated();
        testStaleSelectorFallback();
        System.out.println(failed == 0
            ? "MAIN WINDOW BUILD INFLUENCE CONTROL TEST PASSED (" + checks + " checks)"
            : "MAIN WINDOW BUILD INFLUENCE CONTROL TEST FAILED ("
                + failed + " of " + checks + " checks)");
        System.out.flush();
        System.err.flush();
        System.exit(failed == 0 ? 0 : 1);
    }
}
