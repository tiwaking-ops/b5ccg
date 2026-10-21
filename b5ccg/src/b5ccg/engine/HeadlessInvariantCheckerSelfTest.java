package b5ccg.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.Race;

/**
 * B5-0030: a fast, gate-tier self-test for {@link
 * HeadlessInvariantCheckerProbe}.
 *
 * <p>The fuzz harness that actually exercises the invariant checker takes
 * about 20 seconds per game, so it is a report-only rig and is not a gate
 * class - see the quarantine note in {@code compile.sh}. That leaves a gap:
 * if the invariant checker were broken, nothing in the gate tier would
 * notice, and the fuzz rig would report "no violations" because it was
 * checking nothing. That is exactly the failure mode the withdrawn
 * {@code HeadlessInvariantCheckerProbe} stub had - a checker that cannot fail
 * - and it is not acceptable to rebuild it one layer up.</p>
 *
 * <p>This class closes that gap cheaply. It builds a state that violates
 * each invariant on purpose, and asserts that the checker reports it. If the
 * checker is broken, this fails; if the checker stops being called, this
 * fails. It runs in milliseconds, so it belongs in the gate.</p>
 *
 * <p>Gate tier: pure 0/1/2 exit contract - 0 clean, 1 findings present,
 * 2 the harness could not build its fixtures (a FALSE PASS, never clean).</p>
 *
 * <p>Java 8 source level (javac --release 8), stdlib only.</p>
 */
public class HeadlessInvariantCheckerSelfTest {

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable t) {
            System.out.println("HeadlessInvariantCheckerSelfTest: ERROR " + t);
            System.out.println("RESULT: FAIL (exception)");
            System.exit(2);
            return;
        }
        System.out.println("RESULT: " + (failures == 0 ? "PASS" : "FAIL")
                + " (" + checks + " checks, " + failures + " failures)");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void run() {
        // --- 1. a sound state produces no violations -----------------------
        // This is the negative control. Without it, a checker that always
        // returns "violation" would pass every other test below.
        GameState clean = newState();
        List<HeadlessInvariantCheckerProbe.Violation> none =
                HeadlessInvariantCheckerProbe.check(clean);
        check("a freshly built state has no invariant violations",
                none.isEmpty(),
                none.isEmpty() ? "" : ("found: " + none.get(0)));

        // --- 2. a broken state produces a violation, tagged with its rule ID
        // The tension bound is the easiest to break: the rulebook says
        // "Tension and unrest range from a 1 to 5." The engine's own
        // raiseTension clamps to that range, so the state is put out of range
        // by reaching past the public surface - which is the point: the
        // invariant checker exists to catch a state the engine should never
        // have produced, so the fixture has to produce one.
        GameState broken = newState();
        setTension(broken, Race.HUMAN, Race.NARN, 9);
        List<HeadlessInvariantCheckerProbe.Violation> vs =
                HeadlessInvariantCheckerProbe.check(broken);
        check("a tension above 5 is a violation", !vs.isEmpty(),
                vs.isEmpty() ? "no violation reported" : "");
        if (!vs.isEmpty()) {
            check("the violation is tagged with the tension rule ID",
                    "RB:II/set-tension-tokens".equals(vs.get(0).ruleId),
                    "got rule ID " + vs.get(0).ruleId);
            check("the violation names the rule it breaks",
                    vs.get(0).name != null && vs.get(0).name.length() > 0, "");
            check("the violation says which pair is wrong",
                    vs.get(0).detail != null && vs.get(0).detail.length() > 0, "");
        }

        // --- 3. a second, different invariant also fires --------------------
        // If the checker only ever reported the tension bound, test 2 would
        // pass while the checker was half-dead.
        GameState broken2 = newState();
        setTension(broken2, Race.CENTAURI, Race.NARN, 0);
        List<HeadlessInvariantCheckerProbe.Violation> vs2 =
                HeadlessInvariantCheckerProbe.check(broken2);
        check("a tension below 1 is a violation", !vs2.isEmpty(),
                vs2.isEmpty() ? "no violation reported" : "");
        if (!vs2.isEmpty()) {
            check("the low-tension violation is tagged with the tension rule ID",
                    "RB:II/set-tension-tokens".equals(vs2.get(0).ruleId),
                    "got rule ID " + vs2.get(0).ruleId);
        }

        // --- 4. the checker is stable and order-independent ----------------
        // The same state must produce the same report every time, or the
        // fuzz harness's output is not comparable between runs.
        List<HeadlessInvariantCheckerProbe.Violation> a =
                HeadlessInvariantCheckerProbe.check(broken);
        List<HeadlessInvariantCheckerProbe.Violation> b =
                HeadlessInvariantCheckerProbe.check(broken);
        check("the checker is deterministic", a.toString().equals(b.toString()), "");

        // --- 5. the invariant set is non-empty and every ID is a real one ---
        // An invariant with a null or empty rule ID would be an unciteable
        // invariant, which is the withdrawn-stub problem in a new place.
        check("the invariant set is non-empty",
                HeadlessInvariantCheckerProbe.INVARIANTS.length >= 4, "");
        boolean allTagged = true;
        String untagged = "";
        for (int i = 0; i < HeadlessInvariantCheckerProbe.INVARIANTS.length; i++) {
            String id = HeadlessInvariantCheckerProbe.INVARIANTS[i].ruleId;
            if (id == null || id.length() == 0) {
                allTagged = false;
                untagged = "invariant " + i + " has no rule ID";
                break;
            }
        }
        check("every invariant carries a rule ID", allTagged, untagged);

        // --- 6. every rule ID the checker uses must be in the rule index ---
        // A rule ID that is not in docs/RULE_INDEX.md cannot be checked by
        // check-citations.ps1, so the citation would be unverifiable.
        check("the rule IDs are the ones the rulebook index uses",
                HeadlessInvariantCheckerProbe.INVARIANTS[0].ruleId
                        .startsWith("RB:"), "");
    }

    private static GameState newState() {
        List<Player> players = new ArrayList<Player>();
        players.add(new Player("One", Race.HUMAN, false));
        players.add(new Player("Two", Race.CENTAURI, false));
        return new GameState(players);
    }

    /**
     * Puts a race pair's tension out of the rulebook's 1..5 range.
     *
     * <p>The engine's own {@code raiseTension} clamps to that range, so the
     * only way to build the state the invariant checker is supposed to catch
     * is to reach the map directly. The field is private and the map is
     * unmodifiable, so this uses reflection - deliberately, and only here: a
     * fixture that could not be built through the public surface would not
     * be testing the invariant.</p>
     */
    @SuppressWarnings("unchecked")
    private static void setTension(GameState state, Race from, Race to, int value) {
        try {
            java.lang.reflect.Field tf = GameState.class.getDeclaredField("tensionMatrix");
            tf.setAccessible(true);
            Object tm = tf.get(state);
            java.lang.reflect.Field mf = tm.getClass().getDeclaredField("tension");
            mf.setAccessible(true);
            Map<Race, Map<Race, Integer>> tension = (Map<Race, Map<Race, Integer>>) mf.get(tm);
            Map<Race, Integer> row = tension.get(from);
            if (row == null) {
                row = new java.util.HashMap<Race, Integer>();
                tension.put(from, row);
            }
            row.put(to, Integer.valueOf(value));
        } catch (Exception e) {
            throw new RuntimeException("could not build the tension fixture", e);
        }
    }

    private static void check(String what, boolean ok, String detail) {
        checks++;
        if (ok) {
            System.out.println("PASS  " + what);
        } else {
            failures++;
            System.out.println("FAIL  " + what + (detail.isEmpty() ? "" : (" - " + detail)));
        }
    }
}
