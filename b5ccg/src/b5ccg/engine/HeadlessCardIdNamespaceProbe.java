package b5ccg.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B5-2576: headless probe for {@link CardIdNamespaceGuard}.
 *
 * <p>The guard has only ever printed PASS if it is never made to fail, so this
 * probe asserts the guard's ability to FAIL first and foremost: it runs the
 * unmutated shipped data and then three mutated fixtures, each of which must
 * produce a violation naming the specific defect introduced.</p>
 *
 * <p>All fixtures are built in memory against the pure {@code check()} method, so
 * the shipped card files are never written to and the probe cannot contaminate
 * the shared data the other suites read.</p>
 *
 * <p>Java 6 (javac -source 6 -target 6), stdlib only.</p>
 */
public class HeadlessCardIdNamespaceProbe {

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable th) {
            th.printStackTrace(System.err);
            System.out.println("HeadlessCardIdNamespaceProbe: FAILED with exception");
            System.exit(1);
        }
        System.out.println("HeadlessCardIdNamespaceProbe: " + (checks - failures)
            + "/" + checks + " checks passed");
        if (failures > 0) {
            System.out.println("HeadlessCardIdNamespaceProbe: FAILED (" + failures + ")");
            System.exit(1);
        }
        System.out.println("HeadlessCardIdNamespaceProbe: PASSED");
    }

    private static void run() throws IOException {
        List<CardIdNamespaceGuard.Namespace> reg =
            CardIdNamespaceGuard.loadRegistry("/cards/index.json");

        // ── 1. The registry itself must be loadable and non-empty. ──────────
        check("registry declares 2 sets", reg.size() == 2, "got " + reg.size());

        List<CardIdNamespaceGuard.Namespace> deluxeReg = new ArrayList<CardIdNamespaceGuard.Namespace>();
        for (int i = 0; i < reg.size(); i++) {
            CardIdNamespaceGuard.Namespace ns = reg.get(i);
            if ("DELUXE".equals(ns.set)) deluxeReg.add(ns);
        }
        check("registry declares DELUXE", deluxeReg.size() == 1, "got " + deluxeReg.size());
        if (deluxeReg.size() == 1) {
            check("DELUXE prefix is de_", "de_".equals(deluxeReg.get(0).idPrefix),
                "got '" + deluxeReg.get(0).idPrefix + "'");
            check("DELUXE prefix is required", deluxeReg.get(0).idPrefixRequired, "not required");
        }

        // ── 2. The shipped data must be clean, with a NON-ZERO denominator. ─
        Map<String, List<Map<String, String>>> shipped = new HashMap<String, List<Map<String, String>>>();
        for (int i = 0; i < reg.size(); i++) {
            shipped.put(reg.get(i).file,
                CardSetManifestChecker.loadFlatObjects("/cards/" + reg.get(i).file));
        }
        CardIdNamespaceGuard.Result clean = CardIdNamespaceGuard.check(reg, shipped);
        check("shipped data has 0 violations", clean.isClean(),
            clean.violations.size() + ": " + firstFew(clean.violations));
        // A clean verdict over an empty scan is the B5-1730 shape. Pin the
        // denominator so "0 violations" can never be reported without it.
        check("shipped files read = 2 (denominator non-zero)", clean.filesRead == 2,
            "got " + clean.filesRead);
        check("shipped ids inspected = 829 (denominator non-zero)", clean.idsSeen == 829,
            "got " + clean.idsSeen);

        // Every deluxe id must carry the prefix (the C-NAMESPACE-2 premise,
        // asserted directly rather than inferred from the clean verdict).
        List<Map<String, String>> deluxeCards = shipped.get("deluxe.json");
        int unprefixed = 0;
        for (int i = 0; i < deluxeCards.size(); i++) {
            String id = deluxeCards.get(i).get("id");
            if (id != null && !id.startsWith("de_")) unprefixed++;
        }
        check("0 deluxe ids lack the de_ prefix", unprefixed == 0, unprefixed + " unprefixed");

        // ── 3. MUTATION 1: an id colliding across the two files. ───────────
        // Fixture: premiere.json gains a card whose id is a real deluxe id.
        // Contradicts the clean reading on purpose, so the guard's collision
        // branch is exercised rather than assumed.
        Map<String, List<Map<String, String>>> m1 = deepCopy(shipped);
        m1.get("premiere.json").add(card("de_char_gkar", "Colliding Karma Gate", "CHARACTER"));
        CardIdNamespaceGuard.Result r1 = CardIdNamespaceGuard.check(reg, m1);
        check("mutation 1: cross-file collision is caught", !r1.isClean(), "guard stayed clean");
        check("mutation 1: violation is a C-NAMESPACE-1 collision",
            contains(r1.violations, "C-NAMESPACE-1") && contains(r1.violations, "de_char_gkar"),
            firstFew(r1.violations));

        // ── 4. MUTATION 2: a deluxe id with the de_ prefix stripped. ───────
        Map<String, List<Map<String, String>>> m2 = deepCopy(shipped);
        m2.get("deluxe.json").add(card("char_stray_deluxe", "Stray Deluxe Card", "CHARACTER"));
        CardIdNamespaceGuard.Result r2 = CardIdNamespaceGuard.check(reg, m2);
        check("mutation 2: unprefixed deluxe id is caught", !r2.isClean(), "guard stayed clean");
        check("mutation 2: violation names the id and the prefix",
            contains(r2.violations, "char_stray_deluxe") && contains(r2.violations, "de_"),
            firstFew(r2.violations));

        // ── 5. MUTATION 2b: the inverse — a premiere id squatting on de_. ──
        Map<String, List<Map<String, String>>> m2b = deepCopy(shipped);
        m2b.get("premiere.json").add(card("de_char_gkar", "Squatting Karma Gate", "CHARACTER"));
        CardIdNamespaceGuard.Result r2b = CardIdNamespaceGuard.check(reg, m2b);
        check("mutation 2b: premiere id carrying de_ is caught", !r2b.isClean(),
            "guard stayed clean");
        check("mutation 2b: violation names the squatting id",
            contains(r2b.violations, "de_char_gkar") && contains(r2b.violations, "premiere.json"),
            firstFew(r2b.violations));

        // ── 6. MUTATION 3: a registry cardCount that disagrees with the file. ─
        List<CardIdNamespaceGuard.Namespace> badCount = new ArrayList<CardIdNamespaceGuard.Namespace>();
        for (int i = 0; i < reg.size(); i++) {
            CardIdNamespaceGuard.Namespace ns = reg.get(i);
            if ("DELUXE".equals(ns.set)) {
                // Registry says 999; the file really holds 383.
                badCount.add(new CardIdNamespaceGuard.Namespace(
                    ns.file, ns.set, ns.idPrefix, ns.idPrefixRequired, 999));
            } else {
                badCount.add(ns);
            }
        }
        CardIdNamespaceGuard.Result r3 = CardIdNamespaceGuard.check(badCount, shipped);
        check("mutation 3: cardCount disagreement is caught", !r3.isClean(), "guard stayed clean");
        check("mutation 3: violation names the file and both counts",
            contains(r3.violations, "deluxe.json") && contains(r3.violations, "999")
                && contains(r3.violations, "383"),
            firstFew(r3.violations));

        // ── 7. The mutations must not have leaked into the shipped data. ───
        CardIdNamespaceGuard.Result after = CardIdNamespaceGuard.check(reg, shipped);
        check("shipped data still clean after all mutations", after.isClean(),
            after.violations.size() + ": " + firstFew(after.violations));
        check("shipped id count unchanged after mutations", after.idsSeen == 829,
            "got " + after.idsSeen);
    }

    // ── fixture helpers ───────────────────────────────────────────────────

    private static Map<String, String> card(String id, String title, String type) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("id", id);
        m.put("title", title);
        m.put("type", type);
        return m;
    }

    /**
     * Java 6 has no copy constructor on List, and an inner class capturing a
     * mutable fixture needs a final local — so build the copy explicitly.
     */
    private static Map<String, List<Map<String, String>>> deepCopy(
            Map<String, List<Map<String, String>>> src) {
        Map<String, List<Map<String, String>>> out =
            new HashMap<String, List<Map<String, String>>>();
        List<String> keys = new ArrayList<String>(src.keySet());
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            List<Map<String, String>> srcList = src.get(k);
            List<Map<String, String>> copy = new ArrayList<Map<String, String>>();
            for (int j = 0; j < srcList.size(); j++) {
                copy.add(new LinkedHashMap<String, String>(srcList.get(j)));
            }
            out.put(k, copy);
        }
        return out;
    }

    private static boolean contains(List<String> violations, String needle) {
        for (int i = 0; i < violations.size(); i++) {
            if (violations.get(i).indexOf(needle) >= 0) return true;
        }
        return false;
    }

    private static String firstFew(List<String> v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.size() && i < 3; i++) {
            if (i > 0) sb.append(" | ");
            sb.append(v.get(i));
        }
        return sb.toString();
    }

    private static void check(String what, boolean ok, String detail) {
        checks++;
        if (ok) {
            System.out.println("[B5-2576] " + what + ": PASS");
        } else {
            failures++;
            System.out.println("[B5-2576] " + what + ": FAIL -- " + detail);
        }
    }
}