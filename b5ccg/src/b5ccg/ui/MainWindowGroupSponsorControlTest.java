package b5ccg.ui;

import b5ccg.ai.AIPlayer;
import b5ccg.engine.GameController;
import b5ccg.engine.GameStateCallback;
import b5ccg.engine.RulesEngine;
import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.GameAction;
import b5ccg.model.GameState;
import b5ccg.model.GroupCard;
import b5ccg.model.Player;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.GamePhase;
import b5ccg.model.enums.Rarity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.SwingUtilities;

/**
 * B5-3653: the Group sponsorship affordance, measured rather than asserted.
 *
 * <p>The user report was "I cant sponsor a group card" against a live human
 * seat. The defect was affordance plus vocabulary, not logic: the engine
 * already exposed {@code RulesEngine.canSponsorGroup} and routed a generic
 * {@code PLAY_CARD} for a Group into {@code p.getGroups()}, and the only
 * control that reached that route was labelled <em>Play Card</em>. This suite
 * pins three things so the symptom cannot come back unnoticed:
 *
 * <ol>
 *   <li>a control named by the rulebook verb — rulebook :496 "Sponsor Group" —
 *       exists and is wired (pre-fix there was no such field at all, so any
 *       test naming it failed with NoSuchFieldException);</li>
 *   <li>it lights <em>exactly</em> when {@code RulesEngine.canSponsorGroup}
 *       answers true, and stays dark for the unaffordable and the
 *       race-restricted group, with no second (generic) route lit beside
 *       it;</li>
 *   <li>its click submits {@code GameAction.Type.PLAY_CARD} carrying that very
 *       card, i.e. the engine's own play path, not a UI-side mutation.</li>
 * </ol>
 *
 * <p>Construction follows MainWindowPromoteControlTest: a real MainWindow over
 * a real GameController, with {@code waitingForHuman} injected because a
 * harness has no game loop to set it, and every UI touch made on the EDT.
 *
 * <p>Exit codes: 0 all checks passed, 1 a check failed.
 */
public class MainWindowGroupSponsorControlTest {

    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [GRP3653] " + label + ": " + (ok ? "PASS" : "FAIL"));
        if (!ok) failed++;
    }

    private static GroupCard group(String id, String title, Faction faction) {
        GroupCard g = new GroupCard(id, title, "GROUP", Rarity.COMMON, faction,
            CardSet.PREMIERE, "x", "text");
        g.setCost(6);
        return g;
    }

    private static Player player(String name, Faction faction, boolean human) {
        Player p = new Player(name, faction, human);
        CharacterCard amb = new CharacterCard("amb_" + name, "Ambassador",
            "CHARACTER", Rarity.FIXED, faction, CardSet.PREMIERE, "x", "text",
            3, 3, 3, 3, true);
        p.setAmbassador(amb);
        p.getInnerCircle().add(amb);
        return p;
    }

    private static GameController controller(GameState state) {
        return new GameController(state, new ArrayList<AIPlayer>(),
            new GameStateCallback() {
                public void accept(GameState state) {
                }
            });
    }

    private static GameState twoPlayerState(String name, Faction faction) {
        Player human = player(name, faction, true);
        Player rival = player(name + "Rival", Faction.MINBARI, false);
        List<Player> players = new ArrayList<Player>();
        players.add(human);
        players.add(rival);
        GameState state = new GameState(players);
        human.setGameState(state);
        rival.setGameState(state);
        state.setPhase(GamePhase.ACTION);
        return state;
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    /** In normal play the game loop sets this inside waitForHumanAction();
     *  a harness has no loop, so inject it. */
    private static void setWaitingForHuman(GameController ctrl, boolean v)
            throws Exception {
        Field f = GameController.class.getDeclaredField("waitingForHuman");
        f.setAccessible(true);
        f.set(ctrl, v);
    }

    private static MainWindow window(final GameController ctrl) throws Exception {
        final MainWindow[] out = new MainWindow[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                out[0] = new MainWindow(ctrl);
            }
        });
        return out[0];
    }

    /** The real user path: a HandPanel click routes through this method. */
    private static void select(final MainWindow win, final Card card)
            throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Method m = MainWindow.class.getDeclaredMethod(
                        "applyCardSelection", Card.class, boolean.class);
                    m.setAccessible(true);
                    m.invoke(win, card, Boolean.TRUE);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        });
    }

    private static void click(final MainWindow win, final String fieldName)
            throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    ((JButton) field(win, fieldName)).doClick();
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        });
    }

    public static void main(String[] args) throws Exception {
        System.out.println("B5-3653 Group Sponsor control test");

        // ── case 1: an affordable, unrestricted group in hand ───────────────
        GameState state = twoPlayerState("G3653A", Faction.NARN);
        Player human = state.getPlayers().get(0);
        GroupCard grp = group("grp_3653a", "Narn Resistance Cell", Faction.ANY);
        human.getHand().add(grp);
        human.gainInfluence(20);
        GameController ctrl = controller(state);
        setWaitingForHuman(ctrl, true);
        MainWindow win = window(ctrl);

        JButton sponsorGroup = (JButton) field(win, "sponsorGroupButton");
        check("sponsorGroupButton exists (field present)", sponsorGroup != null);
        check("the label is the rulebook verb Sponsor Group",
            "Sponsor Group".equals(sponsorGroup.getText()));
        check("it carries a click handler",
            sponsorGroup.getActionListeners().length > 0);
        check("it starts dark before any selection", !sponsorGroup.isEnabled());
        check("engine pre-condition: canSponsorGroup is true here",
            new RulesEngine().canSponsorGroup(human, grp));

        select(win, grp);
        check("lights after selecting an affordable group in hand",
            sponsorGroup.isEnabled());
        check("generic Play Card does NOT light for a group",
            !((JButton) field(win, "playCardOnlyButton")).isEnabled());
        check("character Sponsor does NOT light for a group",
            !((JButton) field(win, "sponsorButton")).isEnabled());

        // ── case 2: the click submits PLAY_CARD for that group ──────────────
        click(win, "sponsorGroupButton");
        GameAction parked = (GameAction) field(ctrl, "pendingHumanAction");
        check("the click parked a human action", parked != null);
        check("the parked action is PLAY_CARD",
            parked != null && parked.getType() == GameAction.Type.PLAY_CARD);
        check("the parked action carries the selected group",
            parked != null && parked.getCard() == grp);
        win.dispose();

        // ── case 3: an unaffordable group stays dark. No hand-rolled
        // affordability here (B5-0423): the engine says no, the button follows.
        GameState poor = twoPlayerState("G3653B", Faction.NARN);
        Player poorHuman = poor.getPlayers().get(0);
        GroupCard dear = group("grp_3653b", "Dear Group", Faction.ANY);
        dear.setCost(99);
        poorHuman.getHand().add(dear);
        GameController ctrl2 = controller(poor);
        setWaitingForHuman(ctrl2, true);
        MainWindow win2 = window(ctrl2);
        select(win2, dear);
        check("engine pre-condition: canSponsorGroup false when unaffordable",
            !new RulesEngine().canSponsorGroup(poorHuman, dear));
        check("stays dark for an unaffordable group",
            !((JButton) field(win2, "sponsorGroupButton")).isEnabled());
        check("generic Play stays dark, so no second route reaches an "
            + "illegal play",
            !((JButton) field(win2, "playCardOnlyButton")).isEnabled());
        win2.dispose();

        // ── case 4: another race's restricted group stays dark (:496) ───────
        GameState rs = twoPlayerState("G3653C", Faction.NARN);
        Player rsHuman = rs.getPlayers().get(0);
        GroupCard alien = group("grp_3653c", "Minbari Group", Faction.MINBARI);
        rsHuman.getHand().add(alien);
        rsHuman.gainInfluence(20);
        GameController ctrl3 = controller(rs);
        setWaitingForHuman(ctrl3, true);
        MainWindow win3 = window(ctrl3);
        select(win3, alien);
        check("engine pre-condition: another race's group is refused (:496)",
            !new RulesEngine().canSponsorGroup(rsHuman, alien));
        check("stays dark for a race-restricted group",
            !((JButton) field(win3, "sponsorGroupButton")).isEnabled());
        win3.dispose();

        System.out.println("B5-3653 Group Sponsor control test: " + checks
            + " checks, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
