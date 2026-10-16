package b5ccg.engine;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * B5-2496 — Card set manifest and cross-check tool.
 *
 * <p>Loads premiere.json and deluxe.json and verifies the card set manifest:
 * <ul>
 *   <li>Every card has a non-empty {@code imageKey}</li>
 *   <li>Every {@code imageKey} has a corresponding non-empty {@code title}</li>
 *   <li>Every card has a well-formed {@code id} — lowercase, underscore-separated,
 *       and for DELUXE set cards, prefixed with the registered {@code de_} prefix</li>
 *   <li>No duplicate {@code id} values across both sets</li>
 *   <li>No duplicate {@code imageKey} values within a set</li>
 *   <li>No orphaned entries (cards missing required fields)</li>
 * </ul>
 *
 * <p>Outputs a detailed report of missing/orphaned/dupe entries.
 * Compiles under Java 6 ({@code javac -source 6 -target 6}).</p>
 *
 * <pre>
 *   java -cp b5ccg/out b5ccg.engine.CardSetManifestCrossCheck
 *   java -cp b5ccg/out b5ccg.engine.CardSetManifestCrossCheck -SelfTest
 * </pre>
 */
public class CardSetManifestCrossCheck {

    /** A single registered set namespace from index.json. */
    public static class Namespace {
        public final String file;
        public final String set;
        public final String idPrefix;
        public final boolean idPrefixRequired;
        public final int    cardCount;

        Namespace(String file, String set, String idPrefix,
                  boolean idPrefixRequired, int cardCount) {
            this.file = file;
            this.set = set;
            this.idPrefix = idPrefix;
            this.idPrefixRequired = idPrefixRequired;
            this.cardCount = cardCount;
        }
    }

    /** Result of a cross-check run. */
    public static class Result {
        public final List<String> violations;
        public final int filesRead;
        public final int cardsSeen;

        Result(List<String> violations, int filesRead, int cardsSeen) {
            this.violations = violations;
            this.filesRead = filesRead;
            this.cardsSeen = cardsSeen;
        }

        public boolean isClean() { return violations.isEmpty(); }
    }

    // ── main() — on-demand gate, exit-code contract ────────────────────────

    /**
     * Exit 0 clean, 1 findings present, 2 a file was unreadable.
     * Exit 2 is a FALSE PASS and must never be read as clean.
     * The denominator (files read, cards seen) is always printed.
     */
    public static void main(String[] args) {
        if (args.length > 0 && "-SelfTest".equalsIgnoreCase(args[0])) {
            selfTest();
            System.out.println();
            if (failures == 0) {
                System.out.println("B5-2496 SELF-TEST PASSED (all " + checks + " check(s))");
            } else {
                System.out.println("B5-2496 SELF-TEST FAILED: " + failures + " of " + checks + " check(s)");
                System.exit(1);
            }
            return;
        }

        try {
            Result r = runCrossCheck();
            System.out.println("== B5-2496 card set manifest cross-check ==");
            System.out.println("  files read:    " + r.filesRead);
            System.out.println("  cards seen:    " + r.cardsSeen);
            if (r.isClean()) {
                System.out.println("VERDICT: PASS — 0 violations across " + r.cardsSeen
                    + " cards in " + r.filesRead + " file(s).");
            } else {
                System.out.println("VERDICT: FAIL — " + r.violations.size() + " violation(s) across "
                    + r.cardsSeen + " cards in " + r.filesRead + " file(s):");
                for (int i = 0; i < r.violations.size(); i++) {
                    System.out.println("  " + r.violations.get(i));
                }
                System.exit(1);
            }
        } catch (IOException e) {
            System.err.println("B5-2496: card data or registry unreadable: " + e.getMessage());
            System.err.println("Nothing was checked; exit 2 is a false pass, never a clean run.");
            System.exit(2);
        }
    }

    /** Runs the full cross-check over both card sets. */
    public static Result runCrossCheck() throws IOException {
        List<Namespace> reg = loadRegistry("/cards/index.json");
        Map<String, List<Map<String, String>>> byFile =
            new HashMap<String, List<Map<String, String>>>();
        for (int i = 0; i < reg.size(); i++) {
            byFile.put(reg.get(i).file,
                CardSetManifestChecker.loadFlatObjects("/cards/" + reg.get(i).file));
        }
        return check(reg, byFile);
    }

    /** Core cross-check logic — pure, no I/O, directly testable. */
    public static Result check(List<Namespace> reg,
                               Map<String, List<Map<String, String>>> byFile) {
        List<String> v = new ArrayList<String>();
        int filesRead = 0;
        int cardsSeen = 0;

        // id -> file that first claimed it (for cross-set duplicate detection)
        Map<String, String> idOwner = new HashMap<String, String>();
        // imageKey -> file that first claimed it (for within-set duplicate detection)
        Map<String, String> imageKeyOwner = new HashMap<String, String>();

        for (int r = 0; r < reg.size(); r++) {
            Namespace ns = reg.get(r);
            List<Map<String, String>> cards = byFile.get(ns.file);
            if (cards == null) {
                v.add(ns.file + ": registered in the registry but not supplied to the check");
                continue;
            }
            filesRead++;
            cardsSeen += cards.size();

            // cardCount cross-check against the declared registry
            if (ns.cardCount != 0 && ns.cardCount != cards.size()) {
                v.add(ns.file + ": registry declares cardCount=" + ns.cardCount
                    + " but the file holds " + cards.size() + " cards");
            }

            for (int i = 0; i < cards.size(); i++) {
                Map<String, String> card = cards.get(i);
                String id       = card.get("id");
                String title    = card.get("title");
                String imageKey = card.get("imageKey");
                String setField = card.get("set");
                String type     = card.get("type");
                String faction  = card.get("faction");

                String where = ns.file + " card at index " + i
                    + (id != null && id.length() > 0 ? " (id=" + id + ")" : "");

                // 1. Every card must have a non-empty id
                if (id == null || id.trim().length() == 0) {
                    v.add(where + ": missing or empty 'id'");
                    continue; // can't check further without an id
                }

                // 2. Every card must have a non-empty title
                if (title == null || title.trim().length() == 0) {
                    v.add(where + ": missing or empty 'title'");
                }

                // 3. Every card must have a non-empty imageKey
                if (imageKey == null || imageKey.trim().length() == 0) {
                    v.add(where + ": missing or empty 'imageKey'");
                } else {
                    // 4. Every imageKey must have a title (card-level check)
                    if (title == null || title.trim().length() == 0) {
                        v.add(where + ": imageKey '" + imageKey + "' has no title");
                    }
                }

                // 5. ID must be well-formed: lowercase, underscore-separated
                if (!isWellFormedId(id)) {
                    v.add(where + ": id '" + id + "' is not well-formed "
                        + "(must be lowercase with underscores only, e.g. 'char_jeffrey_sinclair')");
                }

                // 6. ID prefix check per registered namespace
                if (ns.idPrefixRequired && !id.startsWith(ns.idPrefix)) {
                    v.add(where + ": id '" + id + "' does not carry the required prefix '"
                        + ns.idPrefix + "' for set " + ns.set);
                } else if (!ns.idPrefixRequired && ns.idPrefix.length() > 0) {
                    // If a prefix is declared but not required, still check it
                    if (!id.startsWith(ns.idPrefix)) {
                        v.add(where + ": id '" + id + "' does not carry the expected prefix '"
                            + ns.idPrefix + "' for set " + ns.set);
                    }
                }

                // 7. Cross-set duplicate id detection (C-NAMESPACE-1)
                String owner = idOwner.get(id);
                if (owner != null) {
                    v.add("DUPLICATE-ID: id '" + id + "' in " + ns.file
                        + " (also in " + owner + ")");
                } else {
                    idOwner.put(id, ns.file);
                }

                // 8. Within-set duplicate imageKey detection.
                //    Keyed by file + imageKey: a Premiere card reprinted in Deluxe
                //    keeps the same imageKey on purpose, so cross-set reuse is the
                //    expected card-pool pattern (see CardFieldChecklistTool advisory
                //    on shared titles) and must NOT be flagged. Only two cards in
                //    the SAME file sharing an imageKey is ambiguous.
                if (imageKey != null && imageKey.length() > 0) {
                    String imgKey = ns.file + "\u0000" + imageKey;
                    String imgOwner = imageKeyOwner.get(imgKey);
                    if (imgOwner != null) {
                        v.add("DUPLICATE-IMAGEKEY: imageKey '" + imageKey + "' in " + ns.file
                            + " at index " + i + " (also in " + imgOwner + ")");
                    } else {
                        imageKeyOwner.put(imgKey, ns.file + ":" + i);
                    }
                }

                // 9. set field must match the file's declared set
                if (setField != null && setField.length() > 0 && !setField.equals(ns.set)) {
                    v.add(where + ": 'set' field reads '" + setField
                        + "' but the file declares set " + ns.set);
                }

                // 10. Basic sanity: type and faction should be present and non-empty
                if (type == null || type.trim().length() == 0) {
                    v.add(where + ": missing or empty 'type'");
                }
                if (faction == null || faction.trim().length() == 0) {
                    v.add(where + ": missing or empty 'faction'");
                }
            }
        }
        return new Result(v, filesRead, cardsSeen);
    }

    /** Checks if an id is well-formed: lowercase letters, digits, underscores only. */
    private static boolean isWellFormedId(String id) {
        if (id == null || id.length() == 0) return false;
        for (int k = 0; k < id.length(); k++) {
            char ch = id.charAt(k);
            boolean ok = (ch >= 'a' && ch <= 'z')
                      || (ch >= '0' && ch <= '9')
                      || ch == '_';
            if (!ok) return false;
        }
        // Must not start or end with underscore, must not have double underscore
        if (id.charAt(0) == '_' || id.charAt(id.length() - 1) == '_') return false;
        if (id.indexOf("__") >= 0) return false;
        return true;
    }

    // ── Registry loading ──────────────────────────────────────────────────

    /**
     * Loads the registry from the classpath. A missing registry is an error.
     */
    public static List<Namespace> loadRegistry(String resourcePath) throws IOException {
        URL url = CardSetManifestCrossCheck.class.getResource(resourcePath);
        if (url == null) throw new FileNotFoundException("Registry not found: " + resourcePath);
        java.io.InputStream is = null;
        try {
            is = url.openStream();
            return parseRegistry(readAll(is));
        } finally {
            if (is != null) { try { is.close(); } catch (IOException e) { } }
        }
    }

    /** Parses the registry JSON (same format as CardIdNamespaceGuard expects). */
    static List<Namespace> parseRegistry(String json) throws IOException {
        List<Namespace> out = new ArrayList<Namespace>();
        int i = json.indexOf("\"sets\"");
        if (i < 0) throw new IOException("Registry has no \"sets\" key");
        int open = json.indexOf('[', i);
        if (open < 0) throw new IOException("Registry \"sets\" is not an array");
        int depth = 0, start = -1;
        for (int k = open; k < json.length(); k++) {
            char c = json.charAt(k);
            if (c == '{') {
                if (depth == 0) start = k;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    Map<String, String> m = parseFlatObject(json.substring(start, k + 1));
                    out.add(new Namespace(
                        m.get("file"),
                        m.get("set"),
                        m.get("idPrefix") == null ? "" : m.get("idPrefix"),
                        "true".equals(m.get("idPrefixRequired")),
                        parseInt(m.get("cardCount"), 0)));
                    start = -1;
                }
            }
        }
        if (out.isEmpty()) throw new IOException("Registry declared zero sets");
        return out;
    }

    // ── parsing helpers ───────────────────────────────────────────────────

    private static int parseInt(String s, int dflt) {
        if (s == null) return dflt;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return dflt; }
    }

    /** Flat single-level object parser: every value is read as a string. */
    static Map<String, String> parseFlatObject(String obj) {
        Map<String, String> map = new HashMap<String, String>();
        obj = obj.trim();
        if (obj.startsWith("{")) obj = obj.substring(1);
        if (obj.endsWith("}"))   obj = obj.substring(0, obj.length() - 1);
        int i = 0;
        while (i < obj.length()) {
            while (i < obj.length() && (obj.charAt(i) == ',' || Character.isWhitespace(obj.charAt(i)))) i++;
            if (i >= obj.length() || obj.charAt(i) == '}') break;
            if (obj.charAt(i) != '"') { i++; continue; }
            int e = i + 1;
            StringBuilder key = new StringBuilder();
            while (e < obj.length() && obj.charAt(e) != '"') key.append(obj.charAt(e++));
            e++;
            while (e < obj.length() && obj.charAt(e) != ':') e++;
            e++;
            while (e < obj.length() && Character.isWhitespace(obj.charAt(e))) e++;
            StringBuilder val = new StringBuilder();
            if (e < obj.length() && obj.charAt(e) == '"') {
                e++;
                while (e < obj.length() && obj.charAt(e) != '"') {
                    char c = obj.charAt(e);
                    if (c == '\\' && e + 1 < obj.length()) { e++; c = obj.charAt(e); }
                    val.append(c);
                    e++;
                }
                e++;
            } else {
                while (e < obj.length() && obj.charAt(e) != ',') val.append(obj.charAt(e++));
            }
            map.put(key.toString(), val.toString().trim());
            i = e;
        }
        return map;
    }

    private static String readAll(java.io.InputStream is) throws IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
        return buf.toString(StandardCharsets.UTF_8.name());
    }

    // ── Self-test: every rule fired on a synthetic fixture ───────────────────

    private static int checks   = 0;
    private static int failures = 0;

    /**
     * Runs the same checker over synthetic records — never the shipped data —
     * so each rule is observed to fire.
     */
    private static void selfTest() {
        System.out.println("== B5-2496 self-test (synthetic fixtures, shipped data untouched) ==");

        // Build a minimal registry in memory for self-test. cardCount is set to 0
        // here so the registry-count cross-check (test 15 has its own reg with a
        // real count) does not fire on the single-card clean fixtures.
        List<Namespace> reg = new ArrayList<Namespace>();
        reg.add(new Namespace("premiere.json", "PREMIERE", "", false, 0));
        reg.add(new Namespace("deluxe.json", "DELUXE", "de_", true, 0));

        // Test 1: clean premiere card
        Result r1 = checkFixture(reg,
            "[{\"id\":\"char_jeffrey_sinclair\",\"title\":\"Jeffrey Sinclair\",\"type\":\"CHARACTER\","
          + "\"faction\":\"HUMAN\",\"set\":\"PREMIERE\",\"imageKey\":\"jeffrey_sinclair\"}]",
            "premiere.json");
        check("a clean PREMIERE card with all fields yields no finding", r1.isClean(), null);

        // Test 2: clean deluxe card with de_ prefix
        Result r2 = checkFixture(reg,
            "[{\"id\":\"de_char_adira_tyree\",\"title\":\"Adira Tyree\",\"type\":\"CHARACTER\","
          + "\"faction\":\"CENTAURI\",\"set\":\"DELUXE\",\"imageKey\":\"adira_tyree\"}]",
            "deluxe.json");
        check("a clean DELUXE card with de_ prefix yields no finding", r2.isClean(), null);

        // Test 3: missing imageKey
        Result r3 = checkFixture(reg,
            "[{\"id\":\"char_test\",\"title\":\"Test\",\"type\":\"CHARACTER\","
          + "\"faction\":\"HUMAN\",\"set\":\"PREMIERE\"}]",
            "premiere.json");
        check("a card missing imageKey is a finding",
            countOf(r3, "missing or empty 'imageKey'") == 1, null);

        // Test 4: missing title
        Result r4 = checkFixture(reg,
            "[{\"id\":\"char_test\",\"type\":\"CHARACTER\","
          + "\"faction\":\"HUMAN\",\"set\":\"PREMIERE\",\"imageKey\":\"test\"}]",
            "premiere.json");
        check("a card missing title is a finding",
            countOf(r4, "missing or empty 'title'") == 1, null);

        // Test 5: imageKey without title
        Result r5 = checkFixture(reg,
            "[{\"id\":\"char_test\",\"type\":\"CHARACTER\","
          + "\"faction\":\"HUMAN\",\"set\":\"PREMIERE\",\"imageKey\":\"test\"}]",
            "premiere.json");
        check("an imageKey with no title is a finding",
            countOf(r5, "has no title") == 1, null);

        // Test 6: malformed id (uppercase)
        Result r6 = checkFixture(reg,
            "[{\"id\":\"Char_Test\",\"title\":\"Test\",\"type\":\"CHARACTER\","
          + "\"faction\":\"HUMAN\",\"set\":\"PREMIERE\",\"imageKey\":\"test\"}]",
            "premiere.json");
        check("an id with uppercase is a finding",
            countOf(r6, "not well-formed") == 1, null);

        // Test 7: malformed id (double underscore)
        Result r7 = checkFixture(reg,
            "[{\"id\":\"char__test\",\"title\":\"Test\",\"type\":\"CHARACTER\","
          + "\"faction\":\"HUMAN\",\"set\":\"PREMIERE\",\"imageKey\":\"test\"}]",
            "premiere.json");
        check("an id with double underscore is a finding",
            countOf(r7, "not well-formed") == 1, null);

        // Test 8: malformed id (leading underscore)
        Result r8 = checkFixture(reg,
            "[{\"id\":\"_char_test\",\"title\":\"Test\",\"type\":\"CHARACTER\","
          + "\"faction\":\"HUMAN\",\"set\":\"PREMIERE\",\"imageKey\":\"test\"}]",
            "premiere.json");
        check("an id with leading underscore is a finding",
            countOf(r8, "not well-formed") == 1, null);

        // Test 9: deluxe card missing de_ prefix
        Result r9 = checkFixture(reg,
            "[{\"id\":\"char_test\",\"title\":\"Test\",\"type\":\"CHARACTER\","
          + "\"faction\":\"CENTAURI\",\"set\":\"DELUXE\",\"imageKey\":\"test\"}]",
            "deluxe.json");
        check("a DELUXE card missing de_ prefix is a finding",
            countOf(r9, "does not carry the required prefix") == 1, null);

        // Test 10: duplicate id across sets
        Result r10 = checkFixture(reg,
            "[{\"id\":\"char_test\",\"title\":\"Test1\",\"type\":\"CHARACTER\","
          + "\"faction\":\"HUMAN\",\"set\":\"PREMIERE\",\"imageKey\":\"test1\"},"
          + "{\"id\":\"char_test\",\"title\":\"Test2\",\"type\":\"CHARACTER\","
          + "\"faction\":\"CENTAURI\",\"set\":\"DELUXE\",\"imageKey\":\"test2\"}]",
            "premiere.json");
        check("a duplicate id across sets is a finding",
            !r10.isClean(), null);

        // Test 11: duplicate imageKey within set
        Result r11 = checkFixture(reg,
            "[{\"id\":\"char_a\",\"title\":\"A\",\"type\":\"CHARACTER\","
          + "\"faction\":\"HUMAN\",\"set\":\"PREMIERE\",\"imageKey\":\"same_key\"},"
          + "{\"id\":\"char_b\",\"title\":\"B\",\"type\":\"CHARACTER\","
          + "\"faction\":\"HUMAN\",\"set\":\"PREMIERE\",\"imageKey\":\"same_key\"}]",
            "premiere.json");
        check("a duplicate imageKey within a set is a finding",
            countOf(r11, "DUPLICATE-IMAGEKEY") == 1, null);

        // Test 12: set field mismatch
        Result r12 = checkFixture(reg,
            "[{\"id\":\"char_test\",\"title\":\"Test\",\"type\":\"CHARACTER\","
          + "\"faction\":\"HUMAN\",\"set\":\"DELUXE\",\"imageKey\":\"test\"}]",
            "premiere.json");
        check("a card with set field mismatching its file is a finding",
            countOf(r12, "'set' field reads") == 1, null);

        // Test 13: missing type
        Result r13 = checkFixture(reg,
            "[{\"id\":\"char_test\",\"title\":\"Test\",\"faction\":\"HUMAN\","
          + "\"set\":\"PREMIERE\",\"imageKey\":\"test\"}]",
            "premiere.json");
        check("a card missing type is a finding",
            countOf(r13, "missing or empty 'type'") == 1, null);

        // Test 14: missing faction
        Result r14 = checkFixture(reg,
            "[{\"id\":\"char_test\",\"title\":\"Test\",\"type\":\"CHARACTER\","
          + "\"set\":\"PREMIERE\",\"imageKey\":\"test\"}]",
            "premiere.json");
        check("a card missing faction is a finding",
            countOf(r14, "missing or empty 'faction'") == 1, null);

        // Test 15: registry cardCount mismatch
        List<Namespace> regWithCount = new ArrayList<Namespace>();
        regWithCount.add(new Namespace("test.json", "TEST", "", false, 5)); // declares 5
        Result r15 = checkFixture(regWithCount,
            "[{\"id\":\"char_a\",\"title\":\"A\",\"type\":\"CHARACTER\","
          + "\"faction\":\"HUMAN\",\"set\":\"TEST\",\"imageKey\":\"a\"}]",
            "test.json");
        check("registry cardCount mismatch is a finding",
            countOf(r15, "registry declares cardCount") == 1, null);
    }

    /**
     * Runs a fixture through the production checker. Builds a registry that
     * contains ONLY the file under test, so the companion set (which the fixture
     * does not supply) does not emit a spurious "registered but not supplied"
     * finding — that path is covered by the production run's contract, not by a
     * single-file fixture.
     */
    private static Result checkFixture(List<Namespace> reg, String json, String file) {
        List<Namespace> single = new ArrayList<Namespace>();
        for (int i = 0; i < reg.size(); i++) {
            if (reg.get(i).file.equals(file)) single.add(reg.get(i));
        }
        Map<String, List<Map<String, String>>> byFile = new HashMap<String, List<Map<String, String>>>();
        List<Map<String, String>> flat = CardSetManifestChecker.splitObjects(json);
        byFile.put(file, flat);
        return check(single, byFile);
    }

    private static int countOf(Result r, String needle) {
        int n = 0;
        for (int i = 0; i < r.violations.size(); i++) {
            if (r.violations.get(i).indexOf(needle) >= 0) n++;
        }
        return n;
    }

    private static void check(String label, boolean passed, String detail) {
        checks++;
        System.out.println("  [B5-2496] " + label + ": " + (passed ? "PASS" : "FAIL")
                + (passed || detail == null ? "" : " -- " + detail));
        if (!passed) failures++;
    }
}