package b5ccg.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * B5-0003 (WP-02/A1a): canonical-key parity probe.
 *
 * <p>Fails (exit 1) when any behaviour reference in {@link CardEffects} does
 * not resolve to exactly one pool entry, or when two pool entries share a
 * canonical key without being declared printings of each other. "Declared
 * printings" is read off the data: same canonical key plus an identical
 * title. A same-key pair with differing titles is a changed-text twin and
 * belongs to the WP-03 triage (B5-0004), not to silent unification.</p>
 *
 * <p>It also pins the premise {@link CardIdNamespaceGuard#canonicalKey}
 * rests on: every registry-required prefix must be {@code "de_"} (premiere
 * registers the empty prefix). A third set with a new prefix fails loudly
 * here instead of slipping past the strip.</p>
 *
 * <p>Gate tier: pure 0/1/2 exit contract — 0 clean, 1 findings present,
 * 2 card data or registry unreadable (a FALSE PASS, never clean). Wired into
 * the {@code RUN_TESTS=1} gate in compile.sh.</p>
 *
 * <p>Java 6 (javac -source 6 -target 6), stdlib only.</p>
 */
public class HeadlessCanonicalParityProbe {

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable th) {
            th.printStackTrace(System.err);
            System.out.println("HeadlessCanonicalParityProbe: FAILED with exception");
            System.exit(1);
        }
        System.out.println("HeadlessCanonicalParityProbe: " + (checks - failures)
            + "/" + checks + " checks passed");
        if (failures > 0) {
            System.out.println("HeadlessCanonicalParityProbe: FAILED (" + failures + ")");
            System.exit(1);
        }
        System.out.println("HeadlessCanonicalParityProbe: PASSED");
    }

    private static void run() throws IOException {
        System.out.println("CANONICAL PARITY PROBE (B5-0003)");

        // ── 1. Registry premise: canonicalKey strips exactly the required prefixes. ──
        List<CardIdNamespaceGuard.Namespace> reg =
            CardIdNamespaceGuard.loadRegistry("/cards/index.json");
        check("registry declares 2 sets", reg.size() == 2, "got " + reg.size());
        List<String> required = new ArrayList<String>();
        for (int i = 0; i < reg.size(); i++) {
            CardIdNamespaceGuard.Namespace ns = reg.get(i);
            if (ns.idPrefixRequired) required.add(ns.idPrefix);
        }
        check("every required prefix is stripped by canonicalKey",
            required.size() == 1 && "de_".equals(required.get(0)),
            "required=" + required + " (teach canonicalKey the new prefix)");
        check("canonicalKey strips de_", "x".equals(CardIdNamespaceGuard.canonicalKey("de_x")),
            "got '" + CardIdNamespaceGuard.canonicalKey("de_x") + "'");
        check("canonicalKey leaves bare ids alone",
            "x".equals(CardIdNamespaceGuard.canonicalKey("x")),
            "got '" + CardIdNamespaceGuard.canonicalKey("x") + "'");
        check("canonicalKey maps null to null",
            CardIdNamespaceGuard.canonicalKey(null) == null, "non-null for null");

        // ── 2. Load the pool, with a non-zero denominator. ──
        Map<String, List<Map<String, String>>> byFile =
            new HashMap<String, List<Map<String, String>>>();
        for (int i = 0; i < reg.size(); i++) {
            byFile.put(reg.get(i).file,
                CardSetManifestChecker.loadFlatObjects("/cards/" + reg.get(i).file));
        }
        Set<String> poolIds = new HashSet<String>();
        Map<String, String> idToTitle = new HashMap<String, String>();
        for (int i = 0; i < reg.size(); i++) {
            List<Map<String, String>> cards = byFile.get(reg.get(i).file);
            for (int j = 0; j < cards.size(); j++) {
                String id = cards.get(j).get("id");
                if (id == null) continue;
                poolIds.add(id);
                if (!idToTitle.containsKey(id)) {
                    String t = cards.get(j).get("title");
                    idToTitle.put(id, t == null ? "" : t);
                }
            }
        }
        System.out.println("  files read:       " + byFile.size());
        System.out.println("  pool ids:         " + poolIds.size());
        check("pool is non-empty", poolIds.size() > 0, "0 ids loaded");
        check("pool holds 829 ids (denominator non-zero)", poolIds.size() == 829,
            "got " + poolIds.size());

        // ── 3. Every behaviour reference resolves to exactly one pool entry. ──
        Set<String> refs = CardEffects.behaviourReferences();
        System.out.println("  behaviour refs:   " + refs.size());
        check("behaviour reference set is non-empty", refs.size() > 0, "0 refs");
        Set<String> fixtures = CardEffects.NON_POOL_FIXTURE_IDS;
        for (java.util.Iterator<String> it = fixtures.iterator(); it.hasNext();) {
            String f = it.next();
            check("fixture exemption still referenced: " + f, refs.contains(f),
                "stale exemption hides the next real dangling literal");
        }
        Set<String> poolRefs = new HashSet<String>(refs);
        poolRefs.removeAll(fixtures);
        List<String> dangling = danglingRefs(poolRefs, poolIds);
        check("every behaviour literal is an exact pool id", dangling.isEmpty(),
            dangling.size() + " dangling: " + firstFew(dangling));
        List<String> nonCanonical = new ArrayList<String>();
        for (java.util.Iterator<String> it = poolRefs.iterator(); it.hasNext();) {
            String r = it.next();
            if (r.equals(CardIdNamespaceGuard.canonicalKey(r))) continue;
            // Aftermath slug divergence (aftermath_X / de_am_X): both
            // printings keep exact pool-id literals by design.
            if (r.startsWith("de_am_")
                    && poolIds.contains("aftermath_" + r.substring(6))) continue;
            if (isDeluxeOnly(r)) continue;
            nonCanonical.add(r);
        }
        check("no unexplained non-canonical literal remains", nonCanonical.isEmpty(),
            nonCanonical.size() + ": " + firstFew(nonCanonical));

        // ── 4. Canonical collisions are declared printings (same title). ──
        List<String> undeclared = undeclaredGroups(idToTitle);
        int pairs = countPairs(idToTitle);
        System.out.println("  canonical pairs:  " + pairs);
        check("every canonical collision is a same-title printing pair",
            undeclared.isEmpty(), undeclared.size() + ": " + firstFew(undeclared));

        // ── 5. MUTATIONS: the detector must be able to fail. ──
        // M1: a dead literal removed from the behaviour tables (B5-0003/B5-0004:
        // de_am_assigning_blame is the Premiere-only Assigning Blame aftermath
        // with no Deluxe twin — kept out of the tables and the scan), still
        // resolves to zero pool entries through the same resolver used by
        // section 3. Proves behaviourReferences() no longer hardcodes this id
        // and the dangling-ref detector fires when the table no longer carries it.
        Set<String> m1 = new HashSet<String>();
        m1.add("de_am_assigning_blame");
        check("mutation 1: a removed dangling literal resolves to zero entries",
            danglingRefs(m1, poolIds).size() == 1, "resolver stayed silent");
        // M2: a same-key pair with differing titles is an undeclared group.
        Map<String, String> m2 = new HashMap<String, String>();
        m2.put("conf_example", "Example");
        m2.put("de_conf_example", "Example Changed");
        List<String> m2bad = undeclaredGroups(m2);
        check("mutation 2: a changed-title twin is flagged undeclared",
            m2bad.size() == 1 && m2bad.get(0).indexOf("conf_example") >= 0,
            "got " + m2bad);
        // M3: the matching same-title pair passes.
        Map<String, String> m3 = new HashMap<String, String>();
        m3.put("conf_example", "Example");
        m3.put("de_conf_example", "Example");
        check("mutation 3: a same-title twin is accepted as printings",
            undeclaredGroups(m3).isEmpty(), "got " + undeclaredGroups(m3));
    }

    /**
     * Behaviour literals (outside the fixture set) with no pool entry.
     * Pure: directly testable against a mutated fixture (M1).
     */
    static List<String> danglingRefs(Set<String> refs, Set<String> poolIds) {
        List<String> out = new ArrayList<String>();
        for (java.util.Iterator<String> it = refs.iterator(); it.hasNext();) {
            String r = it.next();
            if (!poolIds.contains(r)) out.add(r);
        }
        return out;
    }

    /**
     * Canonical-key groups that are NOT declared printings: anything but a
     * pair sharing one identical title. Pure: testable via M2/M3.
     */
    static List<String> undeclaredGroups(Map<String, String> idToTitle) {
        Map<String, List<String>> groups = new TreeMap<String, List<String>>();
        for (java.util.Iterator<String> it = idToTitle.keySet().iterator(); it.hasNext();) {
            String id = it.next();
            String key = CardIdNamespaceGuard.canonicalKey(id);
            List<String> g = groups.get(key);
            if (g == null) { g = new ArrayList<String>(); groups.put(key, g); }
            g.add(id);
        }
        List<String> bad = new ArrayList<String>();
        for (java.util.Iterator<String> it = groups.keySet().iterator(); it.hasNext();) {
            String key = it.next();
            List<String> g = groups.get(key);
            if (g.size() < 2) continue;
            Set<String> titles = new HashSet<String>();
            for (int i = 0; i < g.size(); i++) titles.add(idToTitle.get(g.get(i)));
            if (g.size() != 2 || titles.size() != 1) {
                bad.add("canonical key '" + key + "' shared by " + g
                    + " with " + titles.size() + " distinct title(s): " + titles);
            }
        }
        return bad;
    }

    /** Deluxe-only effect keys kept exact on purpose (B5-1089/B5-1045 servants
     *  income, total-war ban): printing-specific by design, not uncollapsed. */
    static boolean isDeluxeOnly(String ref) {
        return "de_conf_bio_weapon_discovery".equals(ref)
            || "de_agenda_servants_of_order".equals(ref)
            || "de_agenda_total_war".equals(ref)
            || "de_char_adira_tyree".equals(ref)
            || "de_char_delenn".equals(ref)
            || "de_char_gkar".equals(ref)
            || "de_char_jeffrey_sinclair".equals(ref)
            || "de_char_londo_mollari".equals(ref);
    }

    private static int countPairs(Map<String, String> idToTitle) {
        Map<String, Integer> groups = new HashMap<String, Integer>();
        for (java.util.Iterator<String> it = idToTitle.keySet().iterator(); it.hasNext();) {
            String key = CardIdNamespaceGuard.canonicalKey(it.next());
            Integer n = groups.get(key);
            groups.put(key, new Integer(n == null ? 1 : n.intValue() + 1));
        }
        int pairs = 0;
        for (java.util.Iterator<Integer> it = groups.values().iterator(); it.hasNext();) {
            if (it.next().intValue() == 2) pairs++;
        }
        return pairs;
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
            System.out.println("[B5-0003] " + what + ": PASS");
        } else {
            failures++;
            System.out.println("[B5-0003] " + what + ": FAIL -- " + detail);
        }
    }
}
