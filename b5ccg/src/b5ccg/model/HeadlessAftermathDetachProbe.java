package b5ccg.model;

import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.Rarity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * B5-3867 — GameState.detachAftermath conformance probe (model scope,
 * harness-only, deliberately NOT wired into compile.sh RUN_TESTS; exit 0 on
 * all-pass, 1 on any failure, 2 on harness error).
 *
 * <p>What it pins. GameState grew attachAftermath/canAttachAftermath under
 * B5-0338 and clearAttachedAftermaths under B5-0637, with no removal path for
 * a single card. Consequence, reported NOT FIXED under B5-2406: a one-shot
 * aftermath discarded in conflict Resolution Step 3 keeps its target's name
 * occupied until the round boundary, so canAttachAftermath refuses a second
 * legal copy for the rest of the round, while rulebook :428 states the limit
 * as once per target PER CONFLICT. This probe pins the new removal primitive
 * and every invariant around it.
 *
 * <p>It is deliberately non-vacuous in two ways. (1) The reference-identity
 * cases fail against a title-matching implementation, which is the obvious
 * wrong way to write the removal and the way the sibling discovered/discard
 * registry guards were first written under B5-2406. (2) Three cases assert
 * the OLD behaviour is unchanged: D4 still blocks a same-named copy, the
 * round-boundary clear still frees everything, and two different targets keep
 * independent registries — so a future engine caller cannot regress them.
 *
 * <p>This row adds the primitive only. It proves nothing about the engine
 * discard path, which still never calls it; that wiring is a future row with
 * engine scope, and asserting round-level behaviour here would be a claim
 * about code this row is not allowed to read.
 *
 * <p>Usage:  java -cp b5ccg/out b5ccg.model.HeadlessAftermathDetachProbe
 * Exit: 0 all-pass, 1 failure, 2 harness error.
 */
public class HeadlessAftermathDetachProbe {

    private static int checks = 0;
    private static int failures = 0;

    public static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("  [PASS] " + name);
        } else {
            failures++;
            System.out.println("  [FAIL] " + name);
        }
    }

    /** Two distinct instances carrying the SAME title: two legal cards. */
    private static AftermathCard card(String id, String title) {
        return new AftermathCard(id, title, "AFTERMATH", Rarity.COMMON,
                                 Faction.ANY, CardSet.PREMIERE, "", "", "ANY");
    }

    private static GameState stateWith(Player p1, Player p2) {
        List<Player> ps = new ArrayList<Player>();
        ps.add(p1);
        ps.add(p2);
        return new GameState(ps);
    }

    public static void main(String[] args) {
        try {
            Player target = new Player("target", Faction.HUMAN, false);
            Player other  = new Player("other",  Faction.MINBARI, false);
            GameState st  = stateWith(target, other);

            // ── Section DETACH1: the primitive exists and frees the name ────
            System.out.println("=== DETACH1: attach / detach frees the D4 name slot ===");
            AftermathCard rise1 = card("AM-RISE-1", "Rise to Power");
            AftermathCard rise2 = card("AM-RISE-2", "Rise to Power");

            check("DETACH1.1 attach succeeds on an empty target",
                  st.attachAftermath(rise1, target));
            check("DETACH1.2 one card is registered on the target",
                  st.getAttachedAftermaths(target).size() == 1);
            check("DETACH1.3 D4 blocks a second same-named copy while attached",
                  !st.canAttachAftermath(rise2, target));
            check("DETACH1.4 detach of the attached instance returns true",
                  st.detachAftermath(rise1, target));
            check("DETACH1.5 registry is empty after detach",
                  st.getAttachedAftermaths(target).size() == 0);
            check("DETACH1.6 the D4 name slot is free again in the same round",
                  st.canAttachAftermath(rise2, target));
            check("DETACH1.7 the freed name is actually re-attachable",
                  st.attachAftermath(rise2, target));

            // ── Section DETACH2: reference identity, not title ──────────────
            System.out.println("=== DETACH2: removal matches by instance, not by title ===");
            GameState st2 = stateWith(target, other);
            AftermathCard purgeA = card("AM-PURGE-A", "Purge");
            AftermathCard purgeB = card("AM-PURGE-B", "Purge");
            st2.attachAftermath(purgeA, target);

            check("DETACH2.1 detach of a different same-titled instance returns false",
                  !st2.detachAftermath(purgeB, target));
            check("DETACH2.2 that failed detach left the registry untouched",
                  st2.getAttachedAftermaths(target).size() == 1
                  && st2.getAttachedAftermaths(target).get(0) == purgeA);
            check("DETACH2.3 D4 still blocks the other copy after the refused detach",
                  !st2.canAttachAftermath(purgeB, target));

            GameState st3 = stateWith(target, other);
            AftermathCard x1 = card("AM-X-1", "Aftermath X");
            AftermathCard y1 = card("AM-Y-1", "Aftermath Y");
            check("DETACH2.4 two different titles both attach",
                  st3.attachAftermath(x1, target) && st3.attachAftermath(y1, target));
            check("DETACH2.5 removing one leaves the other instance intact",
                  st3.detachAftermath(x1, target)
                  && st3.getAttachedAftermaths(target).size() == 1
                  && st3.getAttachedAftermaths(target).get(0) == y1);
            check("DETACH2.6 the removed title is free and the retained one is not",
                  st3.canAttachAftermath(card("AM-X-2", "Aftermath X"), target)
                  && !st3.canAttachAftermath(card("AM-Y-2", "Aftermath Y"), target));

            // ── Section DETACH3: no-op safety ───────────────────────────────
            System.out.println("=== DETACH3: a refused detach changes nothing ===");
            AftermathCard never = card("AM-NEVER", "Never Attached");
            int before = st3.getAttachedAftermaths(target).size();
            check("DETACH3.1 detaching an unattached card returns false",
                  !st3.detachAftermath(never, target));
            check("DETACH3.2 detaching an already-removed card returns false",
                  !st3.detachAftermath(x1, target));
            check("DETACH3.3 detaching from the wrong target returns false",
                  !st3.detachAftermath(y1, other));
            check("DETACH3.4 the wrong target's registry stays empty",
                  st3.getAttachedAftermaths(other).size() == 0);
            check("DETACH3.5 a null card is refused, not thrown",
                  !st3.detachAftermath(null, target));
            check("DETACH3.6 a null target is refused, not thrown",
                  !st3.detachAftermath(y1, null));
            check("DETACH3.7 every refusal left the count unchanged",
                  st3.getAttachedAftermaths(target).size() == before);

            // ── Section DETACH4: unchanged neighbours ───────────────────────
            System.out.println("=== DETACH4: pre-existing behaviour is unchanged ===");
            GameState st4 = stateWith(target, other);
            AftermathCard shared1 = card("AM-SHARED-1", "Shared Title");
            AftermathCard shared2 = card("AM-SHARED-2", "Shared Title");
            check("DETACH4.1 targets keep independent registries",
                  st4.attachAftermath(shared1, target)
                  && st4.attachAftermath(shared2, other));
            check("DETACH4.2 detaching from one target leaves the other's attached",
                  st4.detachAftermath(shared1, target)
                  && st4.getAttachedAftermaths(other).size() == 1);

            GameState st5 = stateWith(target, other);
            AftermathCard c1 = card("AM-C-1", "Clearable");
            AftermathCard c2 = card("AM-C-2", "Also Clearable");
            st5.attachAftermath(c1, target);
            st5.attachAftermath(c2, other);
            st5.clearAttachedAftermaths();
            check("DETACH4.3 B5-0637 round-boundary clear still frees every target",
                  st5.getAttachedAftermaths(target).size() == 0
                  && st5.getAttachedAftermaths(other).size() == 0);
            check("DETACH4.4 the registry accepts a fresh attach after the clear",
                  st5.attachAftermath(c1, target));

            System.out.println("");
            System.out.println("AFTERMATH-DETACH PROBE: " + (checks - failures)
                               + "/" + checks + " checks passed");
            if (failures > 0) {
                System.out.println("AFTERMATH-DETACH PROBE: FAILED " + failures
                                   + " of " + checks);
                System.exit(1);
            }
            System.out.println("AFTERMATH-DETACH PROBE: PASSED");
            System.exit(0);
        } catch (Throwable t) {
            System.out.println("AFTERMATH-DETACH PROBE: HARNESS ERROR " + t);
            t.printStackTrace();
            System.exit(2);
        }
    }
}
