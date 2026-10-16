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
 *   - Every title appears in exactly one set (no cross-set title duplicates)
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
        Map<String, Integer> titleCounts = new HashMap<String, Integer>(); // title -> count across both sets

        // Check premiere cards
        checkSetCards(premiereCards, "PREMIERE", idToSet, titleToSet, titleCounts, violations);
        // Check deluxe cards
        checkSetCards(deluxeCards, "DELUXE", idToSet, titleToSet, titleCounts, violations);

        // Cross-set title uniqueness: every title appears in exactly one set
        for (Map.Entry<String, Integer> entry : titleCounts.entrySet()) {
            String title = entry.getKey();
            int count = entry.getValue().intValue();
            if (count > 1) {
                violations.add("Title appears in multiple sets: '" + title + "' (count=" + count + ")");
            }
        }

        return violations;
    }

    /**
     * Validates cards within a single set.
     */
    private void checkSetCards(List<Map<String, String>> cards, String setName,
                               Map<String, String> idToSet, Map<String, String> titleToSet,
                               Map<String, Integer> titleCounts, List<String> violations) {
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
                // Track title counts for cross-set check
                Integer prevCount = titleCounts.get(title);
                titleCounts.put(title, new Integer(prevCount != null ? prevCount.intValue() + 1 : 1));
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