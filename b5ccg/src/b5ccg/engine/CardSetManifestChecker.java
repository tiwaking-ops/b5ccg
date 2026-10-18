package b5ccg.engine;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Card-set manifest cross-check tool.
 * Loads premiere.json and deluxe.json and asserts:
 *   - Every card has non-null id, title, type, faction, and set
 *   - Every id is unique across both sets
 *   - A title appearing in both sets is legal ONLY as a Premiere/Deluxe
 *     printing pair (B5-0038). Two different cards sharing a title is not.
 * Prints a diff report of any violations.
 *
 * Compiles under Java 6 (javac -source 6 -target 6).
 */
public class CardSetManifestChecker {

    public static void main(String[] args) {
        CardSetManifestChecker checker = new CardSetManifestChecker();
        try {
            List<String> violations = checker.checkManifests();
            if (violations.isEmpty()) {
                System.out.println("MANIFEST CHECK: PASS — no violations found.");
            } else {
                System.out.println("MANIFEST CHECK: FAIL — " + violations.size() + " violation(s):");
                for (String v : violations) {
                    System.out.println("  " + v);
                }
                System.exit(1);
            }
        } catch (IOException e) {
            System.err.println("IO error during manifest check: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }

    /**
     * Runs the cross-check and returns a list of violation strings.
     * Empty list means all checks pass.
     */
    public List<String> checkManifests() throws IOException {
        List<String> violations = new ArrayList<String>();

        // Load both JSON files using DeckLoader's parser
        List<Map<String, String>> premiereCards = loadFlatObjects("/cards/premiere.json");
        List<Map<String, String>> deluxeCards = loadFlatObjects("/cards/deluxe.json");

        // Track IDs and titles across both sets
        Map<String, String> idToSet = new HashMap<String, String>(); // id -> set name
        Map<String, String> titleToSet = new HashMap<String, String>(); // title -> set name
        Map<String, List<String>> titleToIds = new HashMap<String, List<String>>(); // title -> every id carrying it

        // Check premiere cards
        checkSetCards(premiereCards, "PREMIERE", idToSet, titleToSet, titleToIds, violations);
        // Check deluxe cards
        checkSetCards(deluxeCards, "DELUXE", idToSet, titleToSet, titleToIds, violations);

        // B5-0038 cross-set title check.
        //
        // This used to assert that every title appears in exactly ONE set. That
        // is a pre-two-printing invariant and it is false by design: a title
        // legitimately exists in both Premiere and Deluxe as two printings of
        // one card, which is the whole basis of the `de_` id namespace and of
        // D5 (Deluxe governs where printings differ). B5-0004 already recorded
        // that this rule "contradicts the two-printing data model", and it
        // flagged 24 correct printing pairs as violations.
        //
        // What is worth protecting is the OTHER failure: two genuinely
        // DIFFERENT cards sharing a title. So a repeated title is now legal
        // only as a printing pair - exactly two entries, one per set, whose
        // ids canonicalise to the same key, with the Deluxe one namespaced.
        // Anything else is still a violation.
        for (Map.Entry<String, List<String>> entry : titleToIds.entrySet()) {
            String title = entry.getKey();
            List<String> ids = entry.getValue();
            if (ids.size() < 2) continue;

            String reason = printingPairDefect(ids);
            if (reason != null) {
                violations.add("Title '" + title + "' appears " + ids.size()
                    + " times but is not a clean Premiere/Deluxe printing pair: " + reason);
            }
        }

        return violations;
    }

    /**
     * B5-0038. Returns null when {@code ids} is an acceptable printing pair, or
     * a short reason string when it is not. Acceptable means: exactly two
     * entries; exactly one bare id and one `de_`-prefixed id; and the two
     * canonicalise to the same key, i.e. they really are two printings of one
     * card rather than two different cards that happen to share a title.
     */
    static String printingPairDefect(List<String> ids) {
        if (ids.size() != 2) return "expected exactly 2 entries, found " + ids.size();

        String bare = null;
        String deluxe = null;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (id.startsWith("de_")) {
                if (deluxe != null) return "two Deluxe ids: " + deluxe + " and " + id;
                deluxe = id;
            } else {
                if (bare != null) return "two Premiere ids: " + bare + " and " + id;
                bare = id;
            }
        }
        if (bare == null) return "no Premiere id";
        if (deluxe == null) return "no de_-prefixed Deluxe id";

        String kb = CardIdNamespaceGuard.canonicalKey(bare);
        String kd = CardIdNamespaceGuard.canonicalKey(deluxe);
        if (!kb.equals(kd)) {
            // Aftermath slug divergence, by design. A reprint of an aftermath
            // is `de_am_X` against Premiere's `aftermath_X`; the slugs differ
            // in the am_/aftermath_ portion, so canonicalKey does NOT collapse
            // them and both printings keep exact pool-id literals. This is the
            // same exception HeadlessCanonicalParityProbe applies, and it is
            // documented in CardEffects.behaviourReferences().
            if (deluxe.startsWith("de_am_") && kb.equals("aftermath_" + deluxe.substring(6))) {
                return null;
            }
            return "canonical keys differ (" + kb + " vs " + kd + ") - different cards sharing a title";
        }
        return null;
    }

    /**
     * Validates cards within a single set.
     */
    private void checkSetCards(List<Map<String, String>> cards, String setName,
                               Map<String, String> idToSet, Map<String, String> titleToSet,
                               Map<String, List<String>> titleToIds, List<String> violations) {
        for (int i = 0; i < cards.size(); i++) {
            Map<String, String> card = cards.get(i);

            // Check required fields are non-null and non-empty
            String id = card.get("id");
            String title = card.get("title");
            String type = card.get("type");
            String faction = card.get("faction");
            String set = card.get("set");

            if (id == null || id.trim().length() == 0) {
                violations.add(setName + " card at index " + i + ": missing or empty 'id'");
            } else {
                // Check ID uniqueness across both sets
                if (idToSet.containsKey(id)) {
                    violations.add("Duplicate id '" + id + "' found in " + setName
                        + " (also in " + idToSet.get(id) + ")");
                } else {
                    idToSet.put(id, setName);
                }
            }

            if (title == null || title.trim().length() == 0) {
                violations.add(setName + " card " + (id != null ? id : "at index " + i) + ": missing or empty 'title'");
            } else {
                // B5-0038: collect every id carrying this title, so a repeated title can be
                // validated as a printing pair rather than banned outright.
                List<String> idsForTitle = titleToIds.get(title);
                if (idsForTitle == null) {
                    idsForTitle = new ArrayList<String>();
                    titleToIds.put(title, idsForTitle);
                }
                if (id != null) idsForTitle.add(id);
                if (!titleToSet.containsKey(title)) titleToSet.put(title, setName);
            }

            if (type == null || type.trim().length() == 0) {
                violations.add(setName + " card " + (id != null ? id : "at index " + i) + ": missing or empty 'type'");
            }

            if (faction == null || faction.trim().length() == 0) {
                violations.add(setName + " card " + (id != null ? id : "at index " + i) + ": missing or empty 'faction'");
            }

            if (set == null || set.trim().length() == 0) {
                violations.add(setName + " card " + (id != null ? id : "at index " + i) + ": missing or empty 'set'");
            } else if (!set.equals(setName)) {
                violations.add(setName + " card " + (id != null ? id : "at index " + i)
                    + ": 'set' field value '" + set + "' does not match expected '" + setName + "'");
            }
        }
    }

    /**
     * Reuse DeckLoader's JSON parsing logic (package-private static method).
     */
    static List<Map<String, String>> loadFlatObjects(String resourcePath) throws IOException {
        URL url = CardSetManifestChecker.class.getResource(resourcePath);
        if (url == null) throw new FileNotFoundException("Resource not found: " + resourcePath);
        InputStream is = null;
        try {
            is = url.openStream();
            return splitObjects(readAll(is));
        } finally {
            if (is != null) { try { is.close(); } catch (IOException e) {} }
        }
    }

    private static String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        try {
            while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
        } finally {
            // Java 6: URL connection handles cleanup
        }
        return buf.toString(StandardCharsets.UTF_8.name());
    }

    static List<Map<String, String>> splitObjects(String json) {
        List<Map<String, String>> result = new ArrayList<Map<String, String>>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    result.add(parseObject(json.substring(start, i + 1)));
                    start = -1;
                }
            }
        }
        return result;
    }

    private static Map<String, String> parseObject(String obj) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        obj = obj.trim();
        if (obj.startsWith("{")) obj = obj.substring(1);
        if (obj.endsWith("}"))   obj = obj.substring(0, obj.length() - 1);

        int i = 0;
        while (i < obj.length()) {
            int ks = obj.indexOf('"', i);
            if (ks < 0) break;
            int ke = obj.indexOf('"', ks + 1);
            if (ke < 0) break;
            String key = obj.substring(ks + 1, ke);
            i = ke + 1;

            int colon = obj.indexOf(':', i);
            if (colon < 0) break;
            i = colon + 1;

            while (i < obj.length() && Character.isWhitespace(obj.charAt(i))) i++;

            String value;
            if (i < obj.length() && obj.charAt(i) == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < obj.length()) {
                    char c = obj.charAt(i);
                    if (c == '\\' && i + 1 < obj.length()) {
                        char next = obj.charAt(i + 1);
                        if (next == '"') { sb.append('"'); i += 2; continue; }
                        if (next == '\\') { sb.append('\\'); i += 2; continue; }
                        if (next == 'n') { sb.append('\n'); i += 2; continue; }
                        sb.append(c); i++; continue;
                    }
                    if (c == '"') { i++; break; }
                    sb.append(c); i++;
                }
                value = sb.toString();
            } else {
                int end = i;
                int depth = 0;
                while (end < obj.length()) {
                    char v = obj.charAt(end);
                    if (v == '"') {
                        end++;
                        while (end < obj.length() && obj.charAt(end) != '"') {
                            if (obj.charAt(end) == '\\' && end + 1 < obj.length()) end++;
                            end++;
                        }
                        if (end < obj.length()) end++;
                        continue;
                    }
                    if (v == '{' || v == '[') depth++;
                    else if (v == '}' || v == ']') {
                        if (depth == 0) break;
                        depth--;
                    } else if (v == ',' && depth == 0) break;
                    end++;
                }
                value = obj.substring(i, end).trim();
                i = end;
            }
            map.put(key, value);
        }
        return map;
    }
}