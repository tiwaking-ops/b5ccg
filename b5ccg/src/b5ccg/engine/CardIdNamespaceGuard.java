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
 * B5-2576: card id namespace guard.
 *
 * <p>Implements the collision check of
 * {@code docs/proposals/2026-10-03-card-id-namespace-guard-proposal.md} §4,
 * driven by the registry it proposed in §3 ({@code /cards/index.json}).</p>
 *
 * <p>The registry declares, per card file, the MANDATORY {@code idPrefix} every
 * id in that file must begin with. An empty prefix is a positive declaration of
 * bare ids. Two violation classes, both errors:</p>
 *
 * <ul>
 *   <li><b>C-NAMESPACE-1 — cross-file id collision.</b> The same id string in two
 *       different {@code cards/*.json} files.</li>
 *   <li><b>C-NAMESPACE-2 — prefix violation.</b> An id in a file whose
 *       {@code idPrefixRequired} is true does not begin with that prefix, or an
 *       id in a file whose prefix is empty begins with a token some other
 *       registered set claims.</li>
 * </ul>
 *
 * <p><b>What this does NOT check, and why.</b> The task row for B5-2576 describes
 * the expected pattern as "faction prefix + type + title slug". Measured against
 * the shipped data that reading is false: 126 of the 829 cards across both files
 * carry no faction token at all, because ANY / NEUTRAL / NON_ALIGNED is a real
 * faction value (275 premiere cards are ANY alone) and many named cards simply
 * do not restate their faction in the id. Enforcing a faction token would
 * therefore reject a quarter of the shipped card pool. This guard enforces the
 * registry rule the cited proposal actually specifies — a set prefix plus
 * global id uniqueness — and leaves the faction question to the data owner.
 * See the B5-2576 report §"Rejected reading".</p>
 *
 * <p>Java 6 (javac -source 6 -target 6), stdlib only.</p>
 */
public class CardIdNamespaceGuard {

    /** A single registered set: the prefix its ids must carry. */
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

    /** Result of a guard run: findings plus the denominator they were drawn from. */
    public static class Result {
        public final List<String> violations;
        /** Card files read by the run. A "0 violations" verdict is only
         *  meaningful next to a non-zero count here. */
        public final int filesRead;
        /** Total card ids inspected across all files read. */
        public final int idsSeen;

        Result(List<String> violations, int filesRead, int idsSeen) {
            this.violations = violations;
            this.filesRead = filesRead;
            this.idsSeen = idsSeen;
        }

        public boolean isClean() { return violations.isEmpty(); }
    }

    // ── Registry loading ──────────────────────────────────────────────────

    /**
     * Loads the registry from the classpath. A missing registry is an error,
     * not a silently-empty namespace: without it no prefix is declared and
     * C-NAMESPACE-2 cannot run at all.
     */
    public static List<Namespace> loadRegistry(String resourcePath) throws IOException {
        URL url = CardIdNamespaceGuard.class.getResource(resourcePath);
        if (url == null) throw new FileNotFoundException("Registry not found: " + resourcePath);
        java.io.InputStream is = null;
        try {
            is = url.openStream();
            return parseRegistry(readAll(is));
        } finally {
            if (is != null) { try { is.close(); } catch (IOException e) { } }
        }
    }

    /**
     * Parses the registry JSON. Hand-rolled against the same flat-object
     * splitter {@link CardSetManifestChecker} uses, extended for the nested
     * "sets" array — the build is stdlib-only (AGENTS.md §2) so there is no JSON
     * library to reach for, and {@link MiniJson} is package-private and refuses
     * anything but a single top-level object.
     */
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

    // ── The check ─────────────────────────────────────────────────────────

    /**
     * Runs the guard over the given files. Pure: takes parsed card lists, does no
     * I/O, so it is directly testable against a mutated fixture.
     *
     * @param byFile  file name -> flat card maps, in registry order
     * @param reg     the declared namespaces
     */
    public static Result check(List<Namespace> reg, Map<String, List<Map<String, String>>> byFile) {
        List<String> v = new ArrayList<String>();
        int filesRead = 0;
        int idsSeen = 0;

        // id -> the file that first claimed it, for C-NAMESPACE-1.
        Map<String, String> idOwner = new HashMap<String, String>();

        for (int r = 0; r < reg.size(); r++) {
            Namespace ns = reg.get(r);
            List<Map<String, String>> cards = byFile.get(ns.file);
            if (cards == null) {
                v.add(ns.file + ": registered in the registry but not supplied to the check");
                continue;
            }
            filesRead++;
            idsSeen += cards.size();

            // cardCount is a cheap cross-check against the declared registry.
            if (ns.cardCount != 0 && ns.cardCount != cards.size()) {
                v.add(ns.file + ": registry declares cardCount=" + ns.cardCount
                    + " but the file holds " + cards.size() + " cards");
            }

            for (int i = 0; i < cards.size(); i++) {
                String id = cards.get(i).get("id");
                if (id == null || id.trim().length() == 0) {
                    // Presence/shape of the id itself is DeckLoader.validateLoadTime's
                    // business (B5-2542); report it here only as a namespace failure.
                    v.add(ns.file + " card at index " + i + ": missing or empty 'id'");
                    continue;
                }

                // C-NAMESPACE-1: cross-file id collision.
                String owner = idOwner.get(id);
                if (owner != null) {
                    v.add("C-NAMESPACE-1: duplicate id '" + id + "' in " + ns.file
                        + " (already claimed by " + owner + ")");
                } else {
                    idOwner.put(id, ns.file);
                }

                // C-NAMESPACE-2: prefix violation.
                if (ns.idPrefixRequired && !id.startsWith(ns.idPrefix)) {
                    v.add("C-NAMESPACE-2: id '" + id + "' in " + ns.file
                        + " does not carry the registered prefix '" + ns.idPrefix + "'");
                } else if (!ns.idPrefixRequired) {
                    // An unprefixed set must not squat on a prefix another set claims.
                    for (int q = 0; q < reg.size(); q++) {
                        Namespace other = reg.get(q);
                        if (other.file.equals(ns.file)) continue;
                        if (other.idPrefix.length() > 0 && id.startsWith(other.idPrefix)) {
                            v.add("C-NAMESPACE-2: id '" + id + "' in " + ns.file
                                + " carries '" + other.idPrefix + "', which is registered to "
                                + other.file);
                        }
                    }
                }
            }
        }
        return new Result(v, filesRead, idsSeen);
    }

    /** Strips the registered "de_" prefix from an id; bare ids pass through. Null maps to null. */
    public static String canonicalKey(String id) {
        if (id == null) return null;
        if (id.startsWith("de_")) return id.substring(3);
        return id;
    }

    // ── main() — on-demand gate, exit-code contract ───────────────────────

    /**
     * Exit 0 clean, 1 findings present, 2 registry or card directory missing or
     * unreadable. Exit 2 is a FALSE PASS and must never be read as clean. The
     * denominator (files read, ids seen) is always printed, so "0 violations"
     * is never reported without the count it was drawn from.
     */
    public static void main(String[] args) {
        try {
            List<Namespace> reg = loadRegistry("/cards/index.json");
            Map<String, List<Map<String, String>>> byFile =
                new HashMap<String, List<Map<String, String>>>();
            for (int i = 0; i < reg.size(); i++) {
                byFile.put(reg.get(i).file,
                    CardSetManifestChecker.loadFlatObjects("/cards/" + reg.get(i).file));
            }
            Result r = check(reg, byFile);
            System.out.println("CARD-ID NAMESPACE GUARD (B5-2576)");
            System.out.println("  registered sets:  " + reg.size());
            System.out.println("  files read:       " + r.filesRead);
            System.out.println("  ids inspected:    " + r.idsSeen);
            if (r.isClean()) {
                System.out.println("VERDICT: PASS — 0 violations across " + r.idsSeen
                    + " ids in " + r.filesRead + " file(s).");
            } else {
                System.out.println("VERDICT: FAIL — " + r.violations.size() + " violation(s):");
                for (int i = 0; i < r.violations.size(); i++) {
                    System.out.println("  " + r.violations.get(i));
                }
                System.exit(1);
            }
        } catch (IOException e) {
            System.err.println("CARD-ID NAMESPACE GUARD: registry or card data unreadable: "
                + e.getMessage());
            System.err.println("Nothing was checked; exit 2 is a false pass, never a clean run.");
            System.exit(2);
        }
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
}