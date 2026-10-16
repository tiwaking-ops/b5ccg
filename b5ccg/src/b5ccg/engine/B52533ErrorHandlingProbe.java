package b5ccg.engine;
import b5ccg.model.enums.Loyalty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import b5ccg.model.AftermathCard;
import b5ccg.model.CharacterCard;
import b5ccg.model.Conflict;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Race;
import b5ccg.model.enums.MarkType;
import b5ccg.model.enums.Rarity;
import b5ccg.model.enums.WarKind;

/**
 * B5-2533: proves the four findings of the B5-2516 audit are fixed AT RUNTIME.
 *
 * <p>A green compile proves a type. Each check below is about behaviour, and
 * each names the finding it closes. F5 and F4 are the two that a compile
 * could not distinguish from "unchanged": one is a channel change and one is a
 * thread-timing change, and both would have passed a build either way.
 *
 * <p>Run: {@code java -cp out b5ccg.engine.B52533ErrorHandlingProbe}
 */
public class B52533ErrorHandlingProbe {

    private static int checks = 0;
    private static int failed = 0;

    private static void check(String id, String what, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("  [" + id + "] " + what + ": PASS");
        } else {
            failed++;
            System.out.println("  [" + id + "] " + what + ": FAIL");
        }
    }

    private static CharacterCard character(String id) {
        return new CharacterCard(id, "Char " + id, "CHARACTER_HUMAN",
                Rarity.FIXED, Loyalty.HUMAN, CardSet.PREMIERE, "x", "text",
                3, 3, 3, 3, false);
    }

    private static GameState state() {
        List<Player> players = new ArrayList<Player>();
        players.add(new Player("Alpha", Race.HUMAN, true));
        players.add(new Player("Beta", Race.CENTAURI, false));
        return new GameState(players);
    }

    public static void main(String[] args) {
        System.out.println("B5-2533 engine error-handling probe");

        f1_nullStateIsReported();
        f2_stateIsRequired();
        f3_fatalCarriesCodeAndCause();
        f4_actionWindowClearsFlag();
        f5_developerChannelNotPlayerChannel();

        System.out.println();
        System.out.println("checks: " + checks + ", failed: " + failed);
        if (failed > 0) {
            System.out.println("B5-2533 PROBE FAILED");
            System.exit(1);
        }
        System.out.println("B5-2533 PROBE PASSED (F1, F2, F3, F4, F5)");
    }

    // B52533ErrorHandlingProbe continues below (the five finding checks).

    /**
     * F1: the input the caller got wrong must always be the one reported.
     * Before B5-2533 this call reported "No agenda vote session is open" --
     * a true statement about a condition this caller never asked about --
     * while the null it did get wrong went unreported.
     */
    private static void f1_nullStateIsReported() {
        System.out.println("== F1: resolveCouncilVote reports the caller's own mistake ==");
        String reported = "NONE-THROWN";
        try {
            new RulesEngine().resolveCouncilVote(null);
        } catch (EngineErrorHandling.FatalError e) {
            reported = e.getMessage();
        }
        check("F1", "a null state is reported as the null state",
              reported != null && reported.indexOf("GameState must not be null") >= 0);
        System.out.println("       reported: " + reported);

        // The session check must still fire when the state is fine.
        boolean sessionReported = false;
        try {
            new RulesEngine().resolveCouncilVote(state());
        } catch (EngineErrorHandling.FatalError e) {
            sessionReported = e.getMessage().indexOf("No agenda vote session") >= 0;
        }
        check("F1b", "a missing session is still reported as a missing session",
              sessionReported);
    }

    /**
     * F2: `state` is required on the methods that said so. The second half
     * matters as much as the first: requiring it must not have broken the
     * working path, which is what the deleted null guards used to carry.
     */
    private static void f2_stateIsRequired() {
        System.out.println("== F2: RulesEngine state parameter is required ==");
        RulesEngine rules = new RulesEngine();
        boolean named = false;
        try {
            rules.addMarksToCharacter(null, new Player("G", Race.NARN, false),
                    character("c1"), MarkType.SHADOW, 1);
        } catch (EngineErrorHandling.FatalError e) {
            named = e.getMessage().indexOf("addMarksToCharacter") >= 0;
        }
        check("F2", "addMarksToCharacter names itself when state is null", named);

        GameState st = state();
        int added = rules.addMarksToCharacter(st, new Player("G", Race.NARN, false),
                character("c2"), MarkType.SHADOW, 2);
        check("F2b", "the real path still adds marks and still logs to the player",
              added == 2 && st.getLog().toString().indexOf("gains 2 SHADOW mark") >= 0);
    }

    /** F3: a fatal carries the row that made it fatal, and its cause. */
    private static void f3_fatalCarriesCodeAndCause() {
        System.out.println("== F3: FatalError carries its code and its cause ==");
        IOException cause = new IOException("no such file");
        EngineErrorHandling.FatalError e =
                EngineErrorHandling.fatal("B5-2533", "pool load failed", cause);
        check("F3", "FatalError carries the row code and the cause",
              "B5-2533".equals(e.code) && e.getCause() == cause);
    }

    // B52533ErrorHandlingProbe continues below (F4 and F5).

    /**
     * F4: the ACTION window must leave its wait on interrupt.
     *
     * <p>This is the check a compile cannot make. Before B5-2533 the handler
     * re-set the interrupt status and left {@code waitingForHuman} true, so the
     * loop re-entered {@code wait(200)}, which saw the status set, cleared it
     * and threw again -- a tight spin, not a hang. The 4-second join budget IS
     * the assertion: a spin never finishes inside it, so the check fails
     * rather than the suite stalling forever.
     */
    private static void f4_actionWindowClearsFlag() {
        System.out.println("== F4: the action window clears its flag on interrupt ==");
        final GameController gc = new GameController(state(),
                new ArrayList<b5ccg.ai.AIPlayer>(), null);
        final boolean[] returned = new boolean[1];
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    Method m = GameController.class
                            .getDeclaredMethod("waitForHumanAction");
                    m.setAccessible(true);
                    m.invoke(gc);
                } catch (Exception e) {
                    System.out.println("       reflection failed: " + e);
                }
                returned[0] = true;
            }
        });
        t.setDaemon(true);
        t.start();
        sleep(400);
        t.interrupt();
        join(t, 4000);
        boolean flagCleared = Boolean.FALSE.equals(readFlag(gc));
        check("F4", "waitForHumanAction returns instead of spinning, flag cleared",
              returned[0] && flagCleared);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static void join(Thread t, long ms) {
        try { t.join(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static Object readFlag(GameController gc) {
        try {
            Field f = GameController.class.getDeclaredField("waitingForHuman");
            f.setAccessible(true);
            return f.get(gc);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * F5: the unknown-aftermath diagnostic must be on the developer channel
     * and NOT in the player log. Both halves are asserted, because moving it
     * to the console while leaving a copy in the log would satisfy only the
     * first, and deleting it would satisfy neither.
     */
    private static void f5_developerChannelNotPlayerChannel() {
        System.out.println("== F5: the aftermath diagnostic left the player channel ==");
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream realErr = System.err;
        GameState st = state();
        String playerLog;
        try {
            System.setErr(new PrintStream(captured, true));
            CardEffects.applyAftermathEffect(st,
                    new Conflict(WarKind.RACE_TARGET, st.getPlayers().get(0),
                            st.getPlayers().get(1)),
                    new AftermathCard("NO-SUCH-AFTERMATH", "No Such Aftermath",
                            "AFTERMATH", Rarity.FIXED, Loyalty.HUMAN,
                            CardSet.PREMIERE, "x", "text", "ANY"),
                    st.getPlayers().get(0), st.getPlayers().get(1));
            playerLog = st.getLog().toString();
        } finally {
            System.setErr(realErr);
        }
        String errText = captured.toString();
        check("F5", "the warning is NOT in the player-visible log",
              playerLog.indexOf("no effect implementation") < 0);
        check("F5b", "it IS on the developer channel, prefixed B5-2533:",
              errText.indexOf("B5-2533:") >= 0
              && errText.indexOf("no effect implementation") >= 0);
    }
}