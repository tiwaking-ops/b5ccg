package b5ccg.ui;

import b5ccg.ai.AIPlayer;
import b5ccg.engine.GameController;
import b5ccg.engine.GameStateCallback;
import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.Deck;
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
import javax.swing.SwingUtilities;

/**
 * B5-2586 - regression for the draw-round buy-cards control.
 *
 * <p>The B5-2586 row's premise ("drawRoundBuyButton is declared but has no
 * click handler") was already stale when it was seeded: B5-2267 added the
 * button together with its handler (MainWindow.java, the
 * "B5-2267: draw-round buy-cards offer" block), the DRAW-phase guard, the
 * enablement refresh (refreshDrawRoundBuyControl, called from refresh), and
 * the toolbar assembly. Nothing asserted the wiring, which is why it could
 * be believed missing: this class is that standing assertion. Same shape as
 * B5-2585 (surrender) and B5-2560 (conflict picker), both closed by
 * verification with the test as the deliverable.
 *
 * <p>It locks three things the UI alone cannot guarantee:
 * (1) drawRoundBuyButton exists, is labelled "Buy Card (3 INF)", and carries
 *     a listener;
 * (2) its enablement is DRAW phase plus a 3-influence applied pool, dark
 *     otherwise (ACTION phase, pool below 3, surrendered human);
 * (3) the click routes through the ENGINE's buyMoreCards, so funds are
 *     re-checked by applyInfluence and one click charges exactly 3 — since
 *     B5-4165 a click buys exactly ONE card (pool 7 buys 1 and leaves 4;
 *     pool 2 buys nothing and the pool is untouched), the purchase is
 *     narrated exactly ONCE (B5-2586 removed the handler's duplicate
 *     state.log, which repeated the engine's line verbatim), and a click
 *     outside DRAW is a no-op.
 *
 * <p>Deliberately NOT asserted here: a GameAction.Type BUY_CARD routed via
 * submitHumanAction/processAction. No such action exists (grep of
 * GameAction.java for buy returns zero hits) and adding one would be
 * model/ + engine/ scope, outside this ui/-only row. The handler calls
 * rules.buyMoreCards directly, which is the same engine authority
 * GameController.runDrawPhase's buy window uses (RulesEngine.buyMoreCards).
 *
 * <p>Exit codes: 0 all checks passed, 1 a check failed. A headless host skips
 * with exit 0, matching MainWindowSurrenderControlTest.
 */
public class MainWindowDrawBuyControlTest {
    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [DRAWBUY] " + label + ": " + (ok ? "PASS" : "FAIL"));
        if (!ok) failed++;
    }

    private static Player player(String name, Faction faction, boolean human) {
        Player player = new Player(name, faction, human);
        CharacterCard ambassador = new CharacterCard("amb_" + name, "Ambassador",
            "CHARACTER_" + faction, Rarity.FIXED, faction, CardSet.PREMIERE,
            "x", "text", 3, 3, 3, 3, true);
        player.setAmbassador(ambassador);
        player.getInnerCircle().add(ambassador);
        return player;
    }

    private static void giveDeck(Player player, String tag, int cards) {
        List<Card> pile = new ArrayList<Card>();
        for (int i = 0; i < cards; i++) {
            pile.add(new CharacterCard("drawbuy_" + tag + "_" + i, "Filler " + i,
                "CHARACTER_NARN", Rarity.COMMON, Faction.NARN, CardSet.PREMIERE,
                "x", "text", 1, 1, 1, 1, false));
        }
        player.setDeck(new Deck(pile));
    }

    private static GameState twoPlayerState(String humanName, Faction humanFaction,
                                            String rivalName, Faction rivalFaction,
                                            GamePhase phase) {
        Player human = player(humanName, humanFaction, true);
        Player rival = player(rivalName, rivalFaction, false);
        List<Player> players = new ArrayList<Player>();
        players.add(human);
        players.add(rival);
        GameState state = new GameState(players);
        human.setGameState(state);
        rival.setGameState(state);
        state.setPhase(phase);
        return state;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
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

    private static MainWindow window(final GameController controller) throws Exception {
        final MainWindow[] result = new MainWindow[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                result[0] = new MainWindow(controller);
            }
        });
        return result[0];
    }

    private static GameController controller(GameState state) {
        return new GameController(state, new ArrayList<AIPlayer>(),
            new GameStateCallback() {
                public void accept(GameState state) {
                }
            });
    }

    private static void click(final JButton button) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                button.doClick();
            }
        });
    }

    private static int buyLogLines(GameState state) {
        int lines = 0;
        List<String> log = state.getLog();
        for (int i = 0; i < log.size(); i++) {
            String line = log.get(i);
            if (line.indexOf("buys ") >= 0
                    && line.indexOf("(draw round step 4)") >= 0) {
                lines++;
            }
        }
        return lines;
    }

    private static void testControlIsWired() throws Exception {
        System.out.println("  [DRAWBUY] --- the draw-buy control exists and is wired ---");
        GameState state = twoPlayerState("D2586Human", Faction.NARN,
            "D2586Rival", Faction.MINBARI, GamePhase.DRAW);
        MainWindow window = window(controller(state));
        JButton buy = (JButton) field(window, "drawRoundBuyButton");

        check("drawRoundBuyButton is constructed", buy != null);
        check("drawRoundBuyButton is labelled Buy Card (3 INF)",
            "Buy Card (3 INF)".equals(buy.getText()));
        check("drawRoundBuyButton carries a click handler",
            buy.getActionListeners() != null
                && buy.getActionListeners().length > 0);
        check("drawRoundBuyButton starts disabled before the first refresh",
            !buy.isEnabled());
        window.dispose();
    }

    private static void testEnablementIsDrawPlusFunds() throws Exception {
        System.out.println("  [DRAWBUY] --- enablement is DRAW phase plus 3 INF ---");

        // Enabled: DRAW phase, fresh pool of 4 covers one purchase.
        GameState funded = twoPlayerState("D2586FundHuman", Faction.NARN,
            "D2586FundRival", Faction.MINBARI, GamePhase.DRAW);
        MainWindow lit = window(controller(funded));
        JButton litButton = (JButton) field(lit, "drawRoundBuyButton");
        refresh(lit, funded);
        check("drawRoundBuyButton ENABLES in DRAW with 4 INF in the pool",
            litButton.isEnabled());
        lit.dispose();

        // Disabled: DRAW phase but only 2 INF left in the pool.
        GameState broke = twoPlayerState("D2586BrokeHuman", Faction.NARN,
            "D2586BrokeRival", Faction.MINBARI, GamePhase.DRAW);
        broke.getPlayers().get(0).applyInfluence(2);
        MainWindow poor = window(controller(broke));
        JButton poorButton = (JButton) field(poor, "drawRoundBuyButton");
        refresh(poor, broke);
        check("drawRoundBuyButton stays DISABLED in DRAW with only 2 INF",
            !poorButton.isEnabled());
        poor.dispose();

        // Disabled: funded but outside the DRAW phase.
        GameState action = twoPlayerState("D2586ActHuman", Faction.NARN,
            "D2586ActRival", Faction.MINBARI, GamePhase.ACTION);
        MainWindow act = window(controller(action));
        JButton actButton = (JButton) field(act, "drawRoundBuyButton");
        refresh(act, action);
        check("drawRoundBuyButton stays DISABLED outside the DRAW phase",
            !actButton.isEnabled());
        act.dispose();

        // Disabled: DRAW phase, funded, but the human already surrendered.
        // The engine's real path (RulesEngine.executeSurrender) sets BOTH the
        // Player flag and the GameState set; the UI gates on the Player flag,
        // so the fixture must too or the button lights on a "surrendered"
        // player that hasSurrendered() cannot see.
        GameState surr = twoPlayerState("D2586SurrHuman", Faction.NARN,
            "D2586SurrRival", Faction.MINBARI, GamePhase.DRAW);
        Player sHuman = surr.getPlayers().get(0);
        sHuman.setHasSurrendered(true);
        surr.markSurrendered(sHuman);
        MainWindow sur = window(controller(surr));
        JButton surButton = (JButton) field(sur, "drawRoundBuyButton");
        refresh(sur, surr);
        check("drawRoundBuyButton stays DISABLED when human surrendered",
            !surButton.isEnabled());
        sur.dispose();
    }

    private static void testClickBuysThroughEngineOnce() throws Exception {
        System.out.println("  [DRAWBUY] --- the click buys through the engine, once ---");
        GameState state = twoPlayerState("D2586BuyHuman", Faction.NARN,
            "D2586BuyRival", Faction.MINBARI, GamePhase.DRAW);
        Player human = state.getPlayers().get(0);
        human.gainInfluence(3);
        giveDeck(human, "buy", 10);
        int poolBefore = human.getAppliedPool();
        int handBefore = human.getHand().size();
        check("fixture holds 7 INF so one purchase fits", poolBefore == 7);

        MainWindow window = window(controller(state));
        JButton buy = (JButton) field(window, "drawRoundBuyButton");
        refresh(window, state);
        check("drawRoundBuyButton is lit before the click", buy.isEnabled());
        click(buy);

        // B5-4165: one click is one card for exactly 3 influence — the
        // pre-fix drain loop that bought 2 here is the defect under test.
        check("one click charges exactly 3 and buys exactly one card",
            human.getHand().size() == handBefore + 1);
        check("the applied pool is debited 3, leaving 4",
            human.getAppliedPool() == poolBefore - 3);
        check("the purchase is narrated exactly ONCE, not twice",
            buyLogLines(state) == 1);
        check("buying does not forfeit the player", !human.hasForfeited());
        window.dispose();
    }

    private static void testClickOutsideDrawIsNoOp() throws Exception {
        System.out.println("  [DRAWBUY] --- a click outside DRAW is a no-op ---");
        GameState state = twoPlayerState("D2586NoOpHuman", Faction.NARN,
            "D2586NoOpRival", Faction.MINBARI, GamePhase.ACTION);
        Player human = state.getPlayers().get(0);
        human.gainInfluence(3);
        giveDeck(human, "noop", 10);
        int poolBefore = human.getAppliedPool();
        int handBefore = human.getHand().size();

        MainWindow window = window(controller(state));
        JButton buy = (JButton) field(window, "drawRoundBuyButton");
        refresh(window, state);
        check("drawRoundBuyButton is dark in ACTION even when funded",
            !buy.isEnabled());
        click(buy);

        check("no card is drawn outside DRAW",
            human.getHand().size() == handBefore);
        check("no influence is spent outside DRAW",
            human.getAppliedPool() == poolBefore);
        check("no purchase is narrated outside DRAW", buyLogLines(state) == 0);
        window.dispose();
    }

    private static void testClickWithoutFundsIsNoOp() throws Exception {
        System.out.println("  [DRAWBUY] --- a click without funds is a no-op ---");
        GameState state = twoPlayerState("D2586PoorHuman", Faction.NARN,
            "D2586PoorRival", Faction.MINBARI, GamePhase.DRAW);
        Player human = state.getPlayers().get(0);
        human.applyInfluence(2);
        giveDeck(human, "poor", 10);
        int handBefore = human.getHand().size();
        check("fixture holds 2 INF so no purchase fits",
            human.getAppliedPool() == 2);

        MainWindow window = window(controller(state));
        JButton buy = (JButton) field(window, "drawRoundBuyButton");
        refresh(window, state);
        click(buy);

        check("no card is drawn without funds",
            human.getHand().size() == handBefore);
        check("the 2 INF pool is untouched", human.getAppliedPool() == 2);
        check("no purchase is narrated without funds", buyLogLines(state) == 0);
        window.dispose();
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("MAIN WINDOW DRAW BUY CONTROL TEST SKIPPED"
                + " (headless environment)");
            return;
        }
        System.out.println("=== MainWindow draw-buy control regression ===");
        testControlIsWired();
        testEnablementIsDrawPlusFunds();
        testClickBuysThroughEngineOnce();
        testClickOutsideDrawIsNoOp();
        testClickWithoutFundsIsNoOp();
        System.out.println(failed == 0
            ? "MAIN WINDOW DRAW BUY CONTROL TEST PASSED (" + checks + " checks)"
            : "MAIN WINDOW DRAW BUY CONTROL TEST FAILED ("
                + failed + " of " + checks + " checks)");
        System.out.flush();
        System.err.flush();
        System.exit(failed == 0 ? 0 : 1);
    }
}
