package b5ccg.ui;

import b5ccg.ai.AIPlayer;
import b5ccg.engine.GameController;
import b5ccg.engine.GameStateCallback;
import b5ccg.engine.RulesEngine;
import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.GameAction;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.ConflictType;
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
 * B5-2508 - standing assertion for the Use Rotate Effect UI control.
 *
 * <p>The rotate-effect button, the effect-kind selector and their handlers were
 * already wired into MainWindow by B5-0401; B5-2587 later retyped the selector
 * from String to {@link GameAction.RotateEffectKind} and made the handler cast
 * the enum directly. The engine path (RulesEngine.canUseRotateEffect /
 * executeRotateEffect, GameAction.useRotateEffect, the GameController
 * USE_ROTATE_EFFECT branch) has been in place since B5-0366. B5-2508's premise
 * ("useRotateEffectButton and rotateEffectKindSelector are declared but have no
 * handler") was already stale when the row was seeded, exactly as B5-2507's was
 * for Lead Fleet.
 *
 * <p>Two things in the seeded row do not exist and were not created here,
 * because inventing them would be worse than recording them:
 *
 * <ul>
 *   <li>The token is {@code USE_ROTATE_EFFECT}, not {@code ROTATE_EFFECT}.
 *   <li>The row's "engine validating the effect kind against available Inner
 *       Circle characters" mis-describes the rule. The rotating character is a
 *       SUPPORTING-role assistant, never an Inner Circle member - GameBoardPanel
 *       renders supporting-role cards in their own chip row precisely because
 *       they appear nowhere else, and MainWindow.applyCardSelection only sets
 *       selectedAssistant for a card in getSupportingRole(). The engine's
 *       canUseAssistant enforces exactly that. So the engine does validate, but
 *       against the supporting role and not the Inner Circle. Check 20 below
 *       pins that distinction so the wrong reading cannot come back.
 * </ul>
 *
 * <p>What this test asserts:
 *
 * (1) the control exists, is labelled, carries a click handler, and starts
 *     dark; (2) the selector lists exactly the RotateEffectKind enum values;
 * (3) enablement is the ENGINE's verdict, not a UI re-derivation - the polarity
 *     pairs below each hold one variable still and move one; (4) the kind the
 *     player picked in the combo is the kind that reaches the queued action,
 *     with the assistant on getCard() and the ambassador on getLeader(); and
 * (5) dispatching that action really rotates the assistant and applies the
 * matching effect, while an Inner-Circle character is refused by the engine.
 *
 * <p>Note on check 21: submitHumanAction only QUEUES for a running game loop, so
 * the queued action is read from the controller's pendingHumanAction field and
 * processAction is then driven reflectively - processAction is where the
 * USE_ROTATE_EFFECT case lives (GameController.java:394 drains the queue).
 *
 * <p>Exit codes: 0 all checks passed, 1 a check failed. A headless host skips
 * with exit 0, matching MainWindowLeadFleetControlTest.
 */
public class MainWindowRotateEffectControlTest {
    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [REUI] " + label + ": " + (ok ? "PASS" : "FAIL"));
        if (!ok) failed++;
    }

    private static CharacterCard ambassador(Faction f) {
        return new CharacterCard("amb_rotateeffect", "Ambassador",
            "CHARACTER_" + f, Rarity.FIXED, f, CardSet.PREMIERE,
            "x", "text", 3, 3, 3, 3, true);
    }

    /** A ready supporting-role assistant with Leadership 2. */
    private static CharacterCard assistant(Faction f) {
        return new CharacterCard("rotateeffect_assistant", "Standing Assistant",
            "CHARACTER_" + f, Rarity.RARE, f, CardSet.PREMIERE,
            "x", "text", 1, 1, 0, 2, false);
    }

    /**
     * The human player owns an ambassador, an Inner Circle member, a supporting
     * assistant, and optionally a fleet. Putting a character in Inner Circle but
     * NOT in supportingRole is the "wrong zone" fixture used by checks 19-20.
     */
    private static GameState twoPlayerState(String humanName, Faction humanFaction,
                                            String rivalName, Faction rivalFaction,
                                            GamePhase phase) {
        Player human = new Player(humanName, humanFaction, true);
        Player rival = new Player(rivalName, rivalFaction, false);
        CharacterCard amb = ambassador(humanFaction);
        CharacterCard supp = assistant(humanFaction);
        human.setAmbassador(amb);
        human.getInnerCircle().add(amb);
        human.getSupportingRole().add(supp);
        List<Player> players = new ArrayList<Player>();
        players.add(human);
        players.add(rival);
        GameState state = new GameState(players);
        human.setGameState(state);
        rival.setGameState(state);
        state.setPhase(phase);
        // B5-0372: the ACTION-phase loop grants one action per player; give the
        // human their full action so the dispatch path does not also need a
        // running loop.
        human.resetActions();
        return state;
    }

    /** The human's supporting-role assistant. */
    private static CharacterCard supportingAssistant(Player human) {
        for (CharacterCard ch : human.getSupportingRole()) {
            if (!ch.isAmbassador()) return ch;
        }
        return null;
    }

    /**
     * Adds a character that is in the human's Inner Circle but deliberately NOT
     * in the supporting role - the "wrong zone" fixture. twoPlayerState puts
     * only the ambassador in Inner Circle, so a plain lookup cannot find a
     * non-ambassador IC member; this is how the wrong-zone card is created, and
     * it is the card that proves the rule is the SUPPORTING role and not
     * "an Inner Circle character" as the seeded row's wording claimed.
     */
    private static CharacterCard addWrongZoneInnerCircleMember(Player human) {
        CharacterCard icMember = new CharacterCard("rotateeffect_icmember",
            "Inner Circle Only", "CHARACTER_" + human.getFaction(), Rarity.COMMON,
            human.getFaction(), CardSet.PREMIERE, "x", "text", 2, 2, 2, 2, false);
        human.getInnerCircle().add(icMember);
        return icMember;
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

    private static void select(final MainWindow window, final Card card)
            throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Method method = MainWindow.class.getDeclaredMethod(
                        "applyCardSelection", Card.class, boolean.class);
                    method.setAccessible(true);
                    method.invoke(window, card, Boolean.FALSE);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
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

    private static void choose(final JComboBox<?> box, final Object item)
            throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                box.setSelectedItem(item);
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

    /**
     * controller.submitHumanAction only QUEUES an action for a running game
     * loop; the loop drains it via waitForHumanAction() and dispatches into
     * processAction (GameController.java:394). There is no loop here, so read
     * the queued action and then drive processAction directly - it is where the
     * USE_ROTATE_EFFECT case lives.
     */
    private static GameAction pending(GameController ctrl) throws Exception {
        Field f = GameController.class.getDeclaredField("pendingHumanAction");
        f.setAccessible(true);
        return (GameAction) f.get(ctrl);
    }

    private static void dispatchRotateEffect(GameController ctrl, Player human,
                                             CharacterCard assistant, CharacterCard amb,
                                             GameAction.RotateEffectKind kind)
            throws Exception {
        Method method = GameController.class.getDeclaredMethod(
            "processAction", Player.class, GameAction.class);
        method.setAccessible(true);
        method.invoke(ctrl, human, GameAction.useRotateEffect(assistant, amb, kind));
    }

    private static void setWaitingForHuman(GameController ctrl, boolean v)
            throws Exception {
        Field f = GameController.class.getDeclaredField("waitingForHuman");
        f.setAccessible(true);
        f.set(ctrl, v);
    }

    private static RulesEngine getRules(GameController ctrl) throws Exception {
        Field f = GameController.class.getDeclaredField("rules");
        f.setAccessible(true);
        return (RulesEngine) f.get(ctrl);
    }

    private static void testControlIsWired() throws Exception {
        System.out.println("  [REUI] --- the rotate-effect control exists and is wired ---");
        GameState state = twoPlayerState("ReHuman", Faction.NARN,
            "ReRival", Faction.MINBARI, GamePhase.ACTION);
        MainWindow w = window(controller(state));
        JButton btn = (JButton) field(w, "useRotateEffectButton");
        JComboBox<GameAction.RotateEffectKind> kindSel =
            (JComboBox<GameAction.RotateEffectKind>) field(w, "rotateEffectKindSelector");

        check("useRotateEffectButton is constructed", btn != null);
        check("useRotateEffectButton is labelled Use Rotate Effect",
            "Use Rotate Effect".equals(btn.getText()));
        check("useRotateEffectButton carries a click handler",
            btn.getActionListeners() != null && btn.getActionListeners().length > 0);
        check("useRotateEffectButton starts disabled before the first refresh",
            !btn.isEnabled());
        check("rotateEffectKindSelector is constructed", kindSel != null);
        // B5-2587 moved this off String index mapping; assert the combo really
        // tracks the enum, so a regression to a positional list is caught here
        // rather than as a wrong kind at click time.
        GameAction.RotateEffectKind[] kinds = GameAction.RotateEffectKind.values();
        boolean sameOrder = kindSel.getItemCount() == kinds.length;
        for (int i = 0; sameOrder && i < kinds.length; i++) {
            sameOrder = kinds[i] == kindSel.getItemAt(i);
        }
        check("rotateEffectKindSelector lists exactly the RotateEffectKind values",
            sameOrder);
        w.dispose();
    }

    /**
     * Enablement is the engine's verdict. Every leg below changes exactly ONE
     * thing and leaves the rest of the fixture alone, so a refusal is
     * attributable - an engine that simply refused everything would pass a
     * negative-only fixture (the B5-2434 failure shape).
     */
    private static void testEngineGatesEnablement() throws Exception {
        System.out.println("  [REUI] --- enablement is the engine's verdict ---");

        // Positive leg: ACTION, ready supporting assistant, ambassador present.
        GameState state = twoPlayerState("ReActH", Faction.NARN,
            "ReActR", Faction.MINBARI, GamePhase.ACTION);
        Player human = state.getHumanPlayer();
        CharacterCard supp = supportingAssistant(human);
        GameController ctrl = controller(state);
        setWaitingForHuman(ctrl, true);
        MainWindow w = window(ctrl);
        JButton btn = (JButton) field(w, "useRotateEffectButton");
        JComboBox<GameAction.RotateEffectKind> kindSel =
            (JComboBox<GameAction.RotateEffectKind>) field(w, "rotateEffectKindSelector");
        refresh(w, state);

        check("no assistant selected yet -> button DISABLED", !btn.isEnabled());
        check("no assistant selected yet -> kind selector DISABLED", !kindSel.isEnabled());

        select(w, supp);
        check("ACTION + ready supporting assistant -> button ENABLES", btn.isEnabled());
        check("ACTION + ready supporting assistant -> kind selector ENABLES",
            kindSel.isEnabled());
        w.dispose();

        // Negative leg 1: phase only. Same assistant, same ambassador, same
        // everything else; DRAW is the single variable.
        GameState drawState = twoPlayerState("ReDrawH", Faction.NARN,
            "ReDrawR", Faction.MINBARI, GamePhase.DRAW);
        Player drawHuman = drawState.getHumanPlayer();
        GameController drawCtrl = controller(drawState);
        setWaitingForHuman(drawCtrl, true);
        MainWindow dw = window(drawCtrl);
        JButton drawBtn = (JButton) field(dw, "useRotateEffectButton");
        refresh(dw, drawState);
        select(dw, supportingAssistant(drawHuman));
        check("DRAW phase (only the phase changed) -> button DISABLED",
            !drawBtn.isEnabled());
        dw.dispose();

        // Negative leg 2: readiness only. Phase back to ACTION; the assistant
        // is rotated, which canUseAssistant refuses.
        GameState rotState = twoPlayerState("ReRotH", Faction.NARN,
            "ReRotR", Faction.MINBARI, GamePhase.ACTION);
        Player rotHuman = rotState.getHumanPlayer();
        CharacterCard rotSupp = supportingAssistant(rotHuman);
        rotSupp.rotate();
        GameController rotCtrl = controller(rotState);
        setWaitingForHuman(rotCtrl, true);
        MainWindow rw = window(rotCtrl);
        JButton rotBtn = (JButton) field(rw, "useRotateEffectButton");
        refresh(rw, rotState);
        select(rw, rotSupp);
        check("rotated assistant (only readiness changed) -> button DISABLED",
            !rotBtn.isEnabled());
        rw.dispose();

        // Negative leg 3: ambassador only. Everything else identical to the
        // positive leg, but the human has no ambassador to assist.
        GameState noAmbState = twoPlayerState("ReNoAmbH", Faction.NARN,
            "ReNoAmbR", Faction.MINBARI, GamePhase.ACTION);
        Player noAmbHuman = noAmbState.getHumanPlayer();
        CharacterCard noAmbSupp = supportingAssistant(noAmbHuman);
        noAmbHuman.setAmbassador(null);
        GameController noAmbCtrl = controller(noAmbState);
        setWaitingForHuman(noAmbCtrl, true);
        MainWindow naw = window(noAmbCtrl);
        JButton noAmbBtn = (JButton) field(naw, "useRotateEffectButton");
        JComboBox<GameAction.RotateEffectKind> noAmbSel =
            (JComboBox<GameAction.RotateEffectKind>) field(naw, "rotateEffectKindSelector");
        refresh(naw, noAmbState);
        select(naw, noAmbSupp);
        // The assistant is still selected - only the ambassador is gone - so the
        // button goes dark purely on the engine's verdict.
        check("no ambassador (only the ambassador changed) -> button DISABLED",
            !noAmbBtn.isEnabled());
        check("no ambassador -> kind selector DISABLED", !noAmbSel.isEnabled());
        naw.dispose();

        // Checks 19-20: the zone. The row's wording said "Inner Circle
        // characters"; the rule is the SUPPORTING role. A character that sits
        // in the Inner Circle but NOT in supportingRole must be refused by the
        // engine, and the UI must not adopt it as an assistant.
        GameState zoneState = twoPlayerState("ReZoneH", Faction.NARN,
            "ReZoneR", Faction.MINBARI, GamePhase.ACTION);
        Player zoneHuman = zoneState.getHumanPlayer();
        CharacterCard icMember = addWrongZoneInnerCircleMember(zoneHuman);
        check("fixture: the wrong-zone card is in Inner Circle but not supporting",
            zoneHuman.getInnerCircle().contains(icMember)
                && !zoneHuman.getSupportingRole().contains(icMember));
        GameController zoneCtrl = controller(zoneState);
        setWaitingForHuman(zoneCtrl, true);
        MainWindow zw = window(zoneCtrl);
        JButton zoneBtn = (JButton) field(zw, "useRotateEffectButton");
        refresh(zw, zoneState);
        select(zw, icMember);
        check("selecting an Inner Circle member leaves selectedAssistant null",
            field(zw, "selectedAssistant") == null);
        check("Inner Circle member in the wrong zone -> button DISABLED",
            !zoneBtn.isEnabled());
        check("engine refuses an Inner Circle member that is not an assistant",
            !getRules(zoneCtrl).canUseRotateEffect(zoneHuman, icMember,
                zoneHuman.getAmbassador(),
                GameAction.RotateEffectKind.USE_ABILITY_BOOST));
        zw.dispose();
    }

    /**
     * The kind chosen in the combo is the kind that reaches the engine, and the
     * action carries the assistant as its card and the ambassador as its leader.
     */
    private static void testSelectorKindReachesTheAction() throws Exception {
        System.out.println("  [REUI] --- the selected kind is the kind submitted ---");
        GameState state = twoPlayerState("ReKindH", Faction.NARN,
            "ReKindR", Faction.MINBARI, GamePhase.ACTION);
        Player human = state.getHumanPlayer();
        CharacterCard supp = supportingAssistant(human);
        CharacterCard amb = human.getAmbassador();
        GameController ctrl = controller(state);
        setWaitingForHuman(ctrl, true);
        MainWindow w = window(ctrl);
        JButton btn = (JButton) field(w, "useRotateEffectButton");
        JComboBox<GameAction.RotateEffectKind> kindSel =
            (JComboBox<GameAction.RotateEffectKind>) field(w, "rotateEffectKindSelector");
        refresh(w, state);
        select(w, supp);

        choose(kindSel, GameAction.RotateEffectKind.USE_SPONSOR_DISCOUNT);
        click(btn);
        GameAction a = pending(ctrl);
        check("a click queues an action", a != null);
        check("queued action type is USE_ROTATE_EFFECT",
            a != null && a.getType() == GameAction.Type.USE_ROTATE_EFFECT);
        check("queued action carries the chosen kind (USE_SPONSOR_DISCOUNT)",
            a != null && a.getRotateKind()
                == GameAction.RotateEffectKind.USE_SPONSOR_DISCOUNT);
        check("queued action card is the selected assistant",
            a != null && a.getCard() == supp);
        check("queued action leader is the player's ambassador",
            a != null && a.getLeader() == amb);
        w.dispose();
    }

    /** Dispatching the queued action really rotates the assistant and applies
     *  the matching effect. Both polarities, so a no-op engine cannot pass. */
    private static void testSubmittedActionRotates() throws Exception {
        System.out.println("  [REUI] --- the submitted USE_ROTATE_EFFECT really rotates ---");

        // Kind 1: ability boost.
        GameState state = twoPlayerState("ReBoostH", Faction.NARN,
            "ReBoostR", Faction.MINBARI, GamePhase.ACTION);
        Player human = state.getHumanPlayer();
        CharacterCard supp = supportingAssistant(human);
        CharacterCard amb = human.getAmbassador();
        GameController ctrl = controller(state);
        RulesEngine rules = getRules(ctrl);

        check("assistant is unrotated before dispatch", !supp.isRotated());
        check("canUseRotateEffect is true before dispatch",
            rules.canUseRotateEffect(human, supp, amb,
                GameAction.RotateEffectKind.USE_ABILITY_BOOST));
        // The +1 surfaces through getPrimaryStatValue, not getEffectiveStat:
        // getEffectiveStat is "printed base plus ATTACHED/FACTION bonuses minus
        // damage" and never reads assistantBonus, so asserting on it would
        // measure the wrong quantity and pass against an engine that applied no
        // boost at all.
        int baseDip = amb.getPrimaryStatValue(ConflictType.DIPLOMACY);

        dispatchRotateEffect(ctrl, human, supp, amb,
            GameAction.RotateEffectKind.USE_ABILITY_BOOST);

        check("dispatch rotates the assistant", supp.isRotated());
        check("ability boost sets the ambassador assistantBonus flag",
            amb.isAssistantBonus());
        check("ability boost gives the ambassador +1 Diplomacy",
            amb.getPrimaryStatValue(ConflictType.DIPLOMACY) == baseDip + 1);
        check("ability boost grants no sponsor discount",
            human.getSponsorDiscount() == 0);

        // Kind 2: sponsor discount, on a fresh fixture.
        GameState discState = twoPlayerState("ReDiscH", Faction.NARN,
            "ReDiscR", Faction.MINBARI, GamePhase.ACTION);
        Player discHuman = discState.getHumanPlayer();
        CharacterCard discSupp = supportingAssistant(discHuman);
        CharacterCard discAmb = discHuman.getAmbassador();
        GameController discCtrl = controller(discState);

        dispatchRotateEffect(discCtrl, discHuman, discSupp, discAmb,
            GameAction.RotateEffectKind.USE_SPONSOR_DISCOUNT);

        check("sponsor discount rotates the assistant", discSupp.isRotated());
        check("sponsor discount grants exactly 1 sponsor discount",
            discHuman.getSponsorDiscount() == 1);
        check("sponsor discount sets no assistantBonus flag",
            !discAmb.isAssistantBonus());

        // Negative: an Inner Circle member is refused by the dispatch branch,
        // which logs "cannot use ..." and must NOT rotate the card.
        GameState icState = twoPlayerState("ReIcRefuseH", Faction.NARN,
            "ReIcRefuseR", Faction.MINBARI, GamePhase.ACTION);
        Player icHuman = icState.getHumanPlayer();
        CharacterCard icMember = addWrongZoneInnerCircleMember(icHuman);
        GameController icCtrl = controller(icState);

        dispatchRotateEffect(icCtrl, icHuman, icMember, icHuman.getAmbassador(),
            GameAction.RotateEffectKind.USE_ABILITY_BOOST);

        check("engine refuses to rotate an Inner Circle member",
            !icMember.isRotated());
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("MAIN WINDOW ROTATE EFFECT CONTROL TEST SKIPPED"
                + " (headless environment)");
            return;
        }
        System.out.println("=== MainWindow rotate effect control regression ===");
        testControlIsWired();
        testEngineGatesEnablement();
        testSelectorKindReachesTheAction();
        testSubmittedActionRotates();
        System.out.println(failed == 0
            ? "MAIN WINDOW ROTATE EFFECT CONTROL TEST PASSED (" + checks + " checks)"
            : "MAIN WINDOW ROTATE EFFECT CONTROL TEST FAILED ("
                + failed + " of " + checks + " checks)");
        System.out.flush();
        System.err.flush();
        System.exit(failed == 0 ? 0 : 1);
    }
}