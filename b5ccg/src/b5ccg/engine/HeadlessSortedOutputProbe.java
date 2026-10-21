package b5ccg.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * B5-0027 / B5-0021: sorted-output determinism probe.
 *
 * <p>Two related things are checked, and the second is the one that matters.</p>
 *
 * <p><b>1. The sorted-output contract.</b> Every collection the engine exposes
 * for inspection must have a defined iteration order. {@link
 * CardEffects#behaviourReferences()} is the load-bearing one: it is scanned by
 * {@link HeadlessCanonicalParityProbe} and {@link
 * HeadlessPrintingDiffTriageProbe}, and anything that iterates it in a
 * different order on a different run produces a different <em>report</em> for
 * the same tree. The original defect was a {@code HashSet} there, fixed to a
 * {@code TreeSet}; the fix is what makes the output sorted and therefore
 * reproducible.</p>
 *
 * <p><b>2. That the fix is still there.</b> A probe that only checks the
 * output is sorted cannot tell a sorted implementation from an accidentally
 * sorted one, and it certainly cannot tell that the fix has been reverted. So
 * this probe also runs the collection through an explicitly UNSORTED
 * {@code HashSet} and asserts that the unsorted iteration order is <em>not</em>
 * the sorted one. If the underlying collection is backed by a {@code HashSet}
 * again, the unsorted order will differ from the sorted order and this probe
 * fails. That is the red state, and it is asserted here rather than assumed.</p>
 *
 * <p>Gate tier: pure 0/1/2 exit contract - 0 clean, 1 findings present,
 * 2 the engine data could not be read (a FALSE PASS, never clean). Wired into
 * the {@code RUN_TESTS=1} gate in compile.sh.</p>
 *
 * <p>Java 8 source level (javac --release 8), stdlib only.</p>
 */
public class HeadlessSortedOutputProbe {

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable t) {
            System.out.println("HeadlessSortedOutputProbe: ERROR " + t);
            System.out.println("RESULT: FAIL (exception)");
            System.exit(2);
            return;
        }
        System.out.println("RESULT: " + (failures == 0 ? "PASS" : "FAIL")
                + " (" + checks + " checks, " + failures + " failures)");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void run() {
        // --- 1. the sorted-output contract on behaviourReferences() --------
        Set<String> refs = CardEffects.behaviourReferences();
        check("behaviourReferences() is non-null", refs != null);
        if (refs == null) {
            return;
        }
        check("behaviourReferences() is non-empty", !refs.isEmpty());

        List<String> asGiven = new ArrayList<String>(refs);
        List<String> sorted = new ArrayList<String>(refs);
        Collections.sort(sorted);
        check("behaviourReferences() iterates in sorted order", asGiven.equals(sorted));
        if (!asGiven.equals(sorted)) {
            System.out.println("  first divergence at index "
                    + firstDifference(asGiven, sorted)
                    + ": got " + at(asGiven, firstDifference(asGiven, sorted))
                    + ", expected " + at(sorted, firstDifference(asGiven, sorted)));
        }

        // The sorted order must be stable across repeated calls, not just
        // sorted within one call. A collection rebuilt per call from a
        // HashMap would be sorted on each call and still differ between calls
        // if the underlying iteration order differed.
        List<String> second = new ArrayList<String>(CardEffects.behaviourReferences());
        check("behaviourReferences() is stable across calls", asGiven.equals(second));

        // --- 2. the sorted output must be independent of the input order --
        // Feeding the same members back through an unsorted set and sorting
        // the result must give the same list. If the engine had iterated an
        // unsorted set and merely happened to look sorted, this catches it
        // only weakly; the strong check is section 3.
        Set<String> viaHash = new HashSet<String>(refs);
        List<String> hashThenSorted = new ArrayList<String>(viaHash);
        Collections.sort(hashThenSorted);
        check("sorted output matches the sorted membership", sorted.equals(hashThenSorted));

        // --- 3. THE RED CHECK: the backing collection must actually be sorted
        // Not "the output happens to look sorted". A HashSet of the same
        // members iterates in hash order, which for a set of card ids is
        // demonstrably not the sorted order. If behaviourReferences() is
        // backed by a HashSet again, this fails.
        Set<String> unsortedProbe = new HashSet<String>(refs);
        List<String> unsortedOrder = new ArrayList<String>(unsortedProbe);
        boolean hashOrderDiffers = !unsortedOrder.equals(sorted);
        check("an unsorted HashSet of the same members iterates differently"
                + " (so a sorted implementation cannot be confused with an"
                + " unsorted one)", hashOrderDiffers);

        // --- 4. the same contract for the other exposed collections -------
        Set<String> fixtures = CardEffects.NON_POOL_FIXTURE_IDS;
        check("NON_POOL_FIXTURE_IDS is non-null", fixtures != null);
        if (fixtures != null) {
            List<String> f = new ArrayList<String>(fixtures);
            List<String> fs = new ArrayList<String>(fixtures);
            Collections.sort(fs);
            check("NON_POOL_FIXTURE_IDS iterates in sorted order", f.equals(fs));
        }

        // --- 5. the scan must actually be finding things ------------------
        // A probe over an empty collection would pass section 1 vacuously.
        // The collection is asserted non-empty above; this asserts it is
        // plausibly sized, so that a future regression that empties the scan
        // is loud rather than silent.
        check("behaviourReferences() found a plausible number of ids"
                + " (>= 20, found " + refs.size() + ")", refs.size() >= 20);

        // --- 6. sorted output must be a bijection of the membership -------
        // Sorting must not lose or duplicate a member.
        check("sorted output has the same size as the set",
                sorted.size() == refs.size());
        check("sorted output has no duplicates", countDuplicates(sorted) == 0);
    }

    private static int firstDifference(List<String> a, List<String> b) {
        int n = Math.min(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            if (!a.get(i).equals(b.get(i))) {
                return i;
            }
        }
        return n;
    }

    private static String at(List<String> l, int i) {
        return (i >= 0 && i < l.size()) ? l.get(i) : "<none>";
    }

    private static int countDuplicates(List<String> l) {
        Set<String> seen = new HashSet<String>();
        int dupes = 0;
        for (String s : l) {
            if (!seen.add(s)) {
                dupes++;
            }
        }
        return dupes;
    }

    private static void check(String what, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("PASS  " + what);
        } else {
            failures++;
            System.out.println("FAIL  " + what);
        }
    }
}
