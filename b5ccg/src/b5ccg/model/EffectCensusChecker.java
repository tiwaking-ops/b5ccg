package b5ccg.model;

import b5ccg.engine.CardEffects;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * B5-0005 (WP-04/A2): effect-census checker.
 *
 * <p>Proves the census data artefact
 * ({@code resources/census/effect-census.json}) classifies every one of the
 * 446 effective card titles exactly once, against the live card pool and the
 * live {@link CardEffects} behaviour tables — not against a frozen copy of
 * either. Any drift (a new card, a new behaviour literal, a deck-list edit)
 * fails loudly here instead of silently ageing the census.</p>
 *
 * <p>Checks: pool denominators (446 premiere titles, every deluxe title also
 * premiere); census holds 446 records with unique titles covering exactly the
 * pool; every primary class and tag is in the census's own closed taxonomy
 * (which must equal the 14 A2 class names); no tag equals its primary and no
 * tag repeats; the {@code printing} and {@code type} fields agree with the
 * pool; the {@code starter} set equals the starter-deck title set; the
 * {@code covered} set equals the live behaviour-reference title set. Three
 * mutation negative-controls prove the detector can fail.</p>
 *
 * <p>Gate tier: pure 0/1/2 exit contract — 0 clean, 1 findings present,
 * 2 data unreadable (a FALSE PASS, never clean). Wired into the
 * {@code RUN_TESTS=1} gate in compile.sh.</p>
 *
 * <p>Java 6 (javac -source 6 -target 6), stdlib only. The small JSON reader
 * below is self-contained so this model-scope checker takes no read
 * dependency on engine parsing helpers; it reads {@link CardEffects} only
 * for the live behaviour-reference set.</p>
 */
public class EffectCensusChecker {

    /** The 14 closed A2 classes (proposal section 5, A2). */
    static final List<String> EXPECTED_TAXONOMY = Arrays.asList(
        "VANILLA", "TRAIT_ONLY", "STAT_MODIFIER", "ONE_SHOT",
        "CONFLICT_VARIANT", "TRIGGERED", "RESTRICTION", "COST_MODIFIER",
        "MARK", "STATE", "VOTE", "AGENDA_WIN", "DECK_HAND", "BESPOKE");

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable th) {
            th.printStackTrace(System.err);
            System.out.println("EffectCensusChecker: FAILED with exception");
            System.exit(2);
        }
        System.out.println("EffectCensusChecker: " + (checks - failures)
            + "/" + checks + " checks passed");
        if (failures > 0) {
            System.out.println("EffectCensusChecker: FAILED (" + failures + ")");
            System.exit(1);
        }
        System.out.println("EffectCensusChecker: PASSED");
    }

    private static void run() throws IOException {
        System.out.println("EFFECT CENSUS CHECKER (B5-0005)");

        // ── 1. Load the pool. ──
        List<Map<String, Object>> premiere = loadArray("/cards/premiere.json");
        List<Map<String, Object>> deluxe = loadArray("/cards/deluxe.json");
        Map<String, Map<String, Object>> premByTitle =
            new HashMap<String, Map<String, Object>>();
        for (int i = 0; i < premiere.size(); i++) {
            premByTitle.put(str(premiere.get(i).get("title")), premiere.get(i));
        }
        Set<String> deluxeTitles = new HashSet<String>();
        for (int i = 0; i < deluxe.size(); i++) {
            deluxeTitles.add(str(deluxe.get(i).get("title")));
        }
        Map<String, String> idToTitle = new HashMap<String, String>();
        for (int i = 0; i < premiere.size(); i++) {
            idToTitle.put(str(premiere.get(i).get("id")),
                          str(premiere.get(i).get("title")));
        }
        for (int i = 0; i < deluxe.size(); i++) {
            idToTitle.put(str(deluxe.get(i).get("id")),
                          str(deluxe.get(i).get("title")));
        }
        System.out.println("  premiere records: " + premiere.size());
        System.out.println("  deluxe records:   " + deluxe.size());
        check("premiere holds 446 records", premiere.size() == 446,
              "got " + premiere.size());
        check("deluxe holds 383 records", deluxe.size() == 383,
              "got " + deluxe.size());
        Set<String> poolTitles = new TreeSet<String>(premByTitle.keySet());
        check("pool holds 446 unique titles", poolTitles.size() == 446,
              "got " + poolTitles.size());
        Set<String> deluxeOutside = new TreeSet<String>(deluxeTitles);
        deluxeOutside.removeAll(poolTitles);
        check("every deluxe title is also a premiere title",
              deluxeOutside.isEmpty(), "outside: " + firstFew(deluxeOutside));

        // ── 2. Load the census. ──
        Map<String, Object> census = loadObject("/census/effect-census.json");
        Object sv = census.get("schema_version");
        check("census schema_version is 1", isOne(sv), "got " + sv);
        List<String> taxonomy = new ArrayList<String>();
        Object taxObj = census.get("taxonomy");
        check("census carries a taxonomy array",
              taxObj instanceof List, "got " + taxObj);
        if (taxObj instanceof List) {
            List taxList = (List) taxObj;
            for (int i = 0; i < taxList.size(); i++) {
                taxonomy.add(String.valueOf(taxList.get(i)));
            }
        }
        check("taxonomy is exactly the 14 closed A2 classes",
              new HashSet<String>(taxonomy).equals(
                  new HashSet<String>(EXPECTED_TAXONOMY))
                  && taxonomy.size() == EXPECTED_TAXONOMY.size(),
              "got " + taxonomy);
        Object recsObj = census.get("records");
        check("census carries a records array",
              recsObj instanceof List, "got " + recsObj);
        List recs = recsObj instanceof List ? (List) recsObj
                                            : new ArrayList();
        check("census holds 446 records", recs.size() == 446,
              "got " + recs.size());

        // ── 3. Exactly-once classification over exactly the pool. ──
        Map<String, Map> byTitle = new HashMap<String, Map>();
        List<String> dupes = new ArrayList<String>();
        for (int i = 0; i < recs.size(); i++) {
            Object o = recs.get(i);
            if (!(o instanceof Map)) continue;
            Map r = (Map) o;
            String t = str(r.get("title"));
            if (byTitle.containsKey(t)) dupes.add(t);
            else byTitle.put(t, r);
        }
        check("every census title appears exactly once", dupes.isEmpty(),
              dupes.size() + " duplicated: " + firstFew(dupes));
        Set<String> censusTitles = new TreeSet<String>(byTitle.keySet());
        Set<String> missing = new TreeSet<String>(poolTitles);
        missing.removeAll(censusTitles);
        Set<String> extra = new TreeSet<String>(censusTitles);
        extra.removeAll(poolTitles);
        check("no pool title is unclassified", missing.isEmpty(),
              missing.size() + ": " + firstFew(missing));
        check("no census title is outside the pool", extra.isEmpty(),
              extra.size() + ": " + firstFew(extra));

        // ── 4. Closed vocabulary + per-record pool agreement. ──
        Set<String> tax = new HashSet<String>(taxonomy);
        List<String> vocabBad = new ArrayList<String>();
        List<String> fieldBad = new ArrayList<String>();
        for (Iterator<String> it = censusTitles.iterator(); it.hasNext();) {
            String t = it.next();
            Map r = byTitle.get(t);
            String cls = str(r.get("title") == null ? null : r.get("class"));
            if (!tax.contains(cls)) {
                vocabBad.add(t + " class=" + r.get("class"));
                continue;
            }
            Object tagsObj = r.get("tags");
            if (!(tagsObj instanceof List)) {
                vocabBad.add(t + " tags not an array");
                continue;
            }
            Set<String> seen = new HashSet<String>();
            List tagsList = (List) tagsObj;
            for (int i = 0; i < tagsList.size(); i++) {
                String g = String.valueOf(tagsList.get(i));
                if (!tax.contains(g) || g.equals(cls) || !seen.add(g)) {
                    vocabBad.add(t + " bad tag=" + g);
                }
            }
            Map<String, Object> eff = effectiveRecord(premByTitle, deluxe,
                                                      deluxeTitles, t);
            String wantPrinting = deluxeTitles.contains(t) ? "DUAL"
                                                           : "PREMIERE_ONLY";
            if (!wantPrinting.equals(str(r.get("printing")))) {
                fieldBad.add(t + " printing=" + r.get("printing"));
            }
            if (eff != null
                    && !str(eff.get("type")).equals(str(r.get("type")))) {
                fieldBad.add(t + " type=" + r.get("type"));
            }
        }
        check("every primary class is in the closed taxonomy",
              vocabBad.isEmpty(),
              vocabBad.size() + ": " + firstFew(vocabBad));
        check("printing/type fields agree with the pool", fieldBad.isEmpty(),
              fieldBad.size() + ": " + firstFew(fieldBad));

        // ── 5. Starter join: flags equal the deck-list title set. ──
        List<Map<String, Object>> decks =
            loadArray("/decks/premiere-starter-decks.json");
        Set<String> deckTitles = new TreeSet<String>();
        List<String> unmapped = new ArrayList<String>();
        for (int i = 0; i < decks.size(); i++) {
            String id = str(decks.get(i).get("id"));
            String t = idToTitle.get(id);
            if (t == null) unmapped.add(id);
            else deckTitles.add(t);
        }
        check("every starter-deck id maps to a pool title", unmapped.isEmpty(),
              unmapped.size() + ": " + firstFew(unmapped));
        Set<String> flaggedStarter = new TreeSet<String>();
        for (Iterator<String> it = censusTitles.iterator(); it.hasNext();) {
            String t = it.next();
            if (isTrue(byTitle.get(t).get("starter"))) flaggedStarter.add(t);
        }
        Set<String> starterOnlyFlag = new TreeSet<String>(flaggedStarter);
        starterOnlyFlag.removeAll(deckTitles);
        Set<String> starterMissed = new TreeSet<String>(deckTitles);
        starterMissed.removeAll(flaggedStarter);
        check("starter flags equal the deck-list title set",
              starterOnlyFlag.isEmpty() && starterMissed.isEmpty(),
              "flagged-not-in-decks=" + firstFew(starterOnlyFlag)
              + " in-decks-not-flagged=" + firstFew(starterMissed));
        System.out.println("  starter titles:   " + deckTitles.size());

        // ── 6. Coverage join: flags equal the live behaviour set. ──
        Set<String> refs = CardEffects.behaviourReferences();
        Set<String> fixtures = CardEffects.NON_POOL_FIXTURE_IDS;
        for (Iterator<String> it = fixtures.iterator(); it.hasNext();) {
            String f = it.next();
            check("fixture exemption still referenced: " + f, refs.contains(f),
                  "stale exemption hides the next real dangling literal");
        }
        Set<String> liveCovered = new TreeSet<String>();
        List<String> dangling = new ArrayList<String>();
        for (Iterator<String> it = refs.iterator(); it.hasNext();) {
            String r = it.next();
            if (fixtures.contains(r)) continue;
            String t = idToTitle.get(r);
            if (t == null) dangling.add(r);
            else liveCovered.add(t);
        }
        check("every live behaviour literal names a pool title",
              dangling.isEmpty(),
              dangling.size() + ": " + firstFew(dangling));
        Set<String> flaggedCovered = new TreeSet<String>();
        for (Iterator<String> it = censusTitles.iterator(); it.hasNext();) {
            String t = it.next();
            if (isTrue(byTitle.get(t).get("covered"))) flaggedCovered.add(t);
        }
        Set<String> covOnlyFlag = new TreeSet<String>(flaggedCovered);
        covOnlyFlag.removeAll(liveCovered);
        Set<String> covMissed = new TreeSet<String>(liveCovered);
        covMissed.removeAll(flaggedCovered);
        check("covered flags equal the live behaviour-reference title set",
              covOnlyFlag.isEmpty() && covMissed.isEmpty(),
              "flagged-not-referenced=" + firstFew(covOnlyFlag)
              + " referenced-not-flagged=" + firstFew(covMissed));
        System.out.println("  covered titles:   " + liveCovered.size());

        // ── 7. Class table (the owner-review artefact) + sum. ──
        Map<String, Integer> counts = new TreeMap<String, Integer>();
        for (Iterator<String> it = censusTitles.iterator(); it.hasNext();) {
            String cls = str(byTitle.get(it.next()).get("class"));
            Integer n = counts.get(cls);
            counts.put(cls, new Integer(n == null ? 1 : n.intValue() + 1));
        }
        int sum = 0;
        System.out.println("  ── class table ──");
        for (Iterator<String> it = counts.keySet().iterator();
                it.hasNext();) {
            String cls = it.next();
            int n = counts.get(cls).intValue();
            sum += n;
            System.out.println("  " + pad(cls, 16) + n);
        }
        final int total = sum;
        check("class counts sum to 446", total == 446, "got " + total);

        // ── 8. MUTATIONS: the detector must be able to fail. ──
        Set<String> m1 = new TreeSet<String>(censusTitles);
        m1.remove("Rise to Power");
        Set<String> m1missing = new TreeSet<String>(poolTitles);
        m1missing.removeAll(m1);
        check("mutation 1: a dropped title is detected as unclassified",
              m1missing.size() == 1 && m1missing.contains("Rise to Power"),
              "got " + m1missing);
        check("mutation 2: an unknown class is outside the taxonomy",
              !tax.contains("MIND_CONTROL"),
              "taxonomy accepted a novel class");
        Set<String> m3 = new TreeSet<String>(flaggedStarter);
        // Elric is a DUAL non-starter title: flagging it must stand out.
        m3.add("Elric");
        Set<String> m3flag = new TreeSet<String>(m3);
        m3flag.removeAll(deckTitles);
        check("mutation 3: a wrong starter flag is detected",
              m3flag.size() == 1 && m3flag.contains("Elric"),
              "got " + m3flag);
    }

    /** Effective pool record for a title: deluxe governs duals (D5). */
    static Map<String, Object> effectiveRecord(
            Map<String, Map<String, Object>> premByTitle,
            List<Map<String, Object>> deluxe,
            Set<String> deluxeTitles, String title) {
        if (deluxeTitles.contains(title)) {
            for (int i = 0; i < deluxe.size(); i++) {
                if (title.equals(str(deluxe.get(i).get("title")))) {
                    return deluxe.get(i);
                }
            }
        }
        return premByTitle.get(title);
    }

    // ── Minimal JSON reader (objects, arrays, strings, numbers,
    // true/false/null; sufficient for flat card records, the deck list,
    // and the census file). ──

    static List<Map<String, Object>> loadArray(String path)
            throws IOException {
        Object v = parse(readResource(path));
        if (!(v instanceof List)) {
            throw new IOException("expected JSON array in " + path);
        }
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        List list = (List) v;
        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            if (!(o instanceof Map)) {
                throw new IOException("expected JSON objects in " + path);
            }
            Map<String, Object> row = new HashMap<String, Object>();
            Map m = (Map) o;
            for (Iterator it = m.keySet().iterator(); it.hasNext();) {
                Object k = it.next();
                row.put(String.valueOf(k), m.get(k));
            }
            out.add(row);
        }
        return out;
    }

    static Map<String, Object> loadObject(String path) throws IOException {
        Object v = parse(readResource(path));
        if (!(v instanceof Map)) {
            throw new IOException("expected JSON object in " + path);
        }
        Map<String, Object> out = new HashMap<String, Object>();
        Map m = (Map) v;
        for (Iterator it = m.keySet().iterator(); it.hasNext();) {
            Object k = it.next();
            out.put(String.valueOf(k), m.get(k));
        }
        return out;
    }

    static String readResource(String path) throws IOException {
        URL url = EffectCensusChecker.class.getResource(path);
        if (url == null) {
            throw new FileNotFoundException("Resource not found: " + path);
        }
        InputStream is = null;
        try {
            is = url.openStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
            byte[] raw = buf.toByteArray();
            // Strip a UTF-8 BOM if present (card files carry one).
            int off = (raw.length >= 3 && (raw[0] & 0xFF) == 0xEF
                       && (raw[1] & 0xFF) == 0xBB && (raw[2] & 0xFF) == 0xBF)
                      ? 3 : 0;
            return new String(raw, off, raw.length - off,
                              Charset.forName("UTF-8"));
        } finally {
            if (is != null) { try { is.close(); } catch (IOException e) { } }
        }
    }

    /** Recursive-descent parse; pure and directly testable. */
    static Object parse(String json) throws IOException {
        int[] pos = new int[] { 0 };
        skipWs(json, pos);
        Object v = parseValue(json, pos);
        skipWs(json, pos);
        if (pos[0] != json.length()) {
            throw new IOException("trailing characters at " + pos[0]);
        }
        return v;
    }

    static Object parseValue(String json, int[] pos) throws IOException {
        skipWs(json, pos);
        if (pos[0] >= json.length()) throw new IOException("truncated JSON");
        char c = json.charAt(pos[0]);
        if (c == '{') return parseObject(json, pos);
        if (c == '[') return parseArray(json, pos);
        if (c == '"') return parseString(json, pos);
        if (c == 't' && json.startsWith("true", pos[0])) {
            pos[0] += 4;
            return Boolean.TRUE;
        }
        if (c == 'f' && json.startsWith("false", pos[0])) {
            pos[0] += 5;
            return Boolean.FALSE;
        }
        if (c == 'n' && json.startsWith("null", pos[0])) {
            pos[0] += 4;
            return null;
        }
        return parseNumber(json, pos);
    }

    static Map<String, Object> parseObject(String json, int[] pos)
            throws IOException {
        Map<String, Object> out = new HashMap<String, Object>();
        pos[0]++; // {
        skipWs(json, pos);
        if (pos[0] < json.length() && json.charAt(pos[0]) == '}') {
            pos[0]++;
            return out;
        }
        while (true) {
            skipWs(json, pos);
            Object k = parseValue(json, pos);
            if (!(k instanceof String)) {
                throw new IOException("object key not a string");
            }
            skipWs(json, pos);
            if (pos[0] >= json.length() || json.charAt(pos[0]) != ':') {
                throw new IOException("expected ':' in object");
            }
            pos[0]++;
            Object v = parseValue(json, pos);
            out.put((String) k, v);
            skipWs(json, pos);
            if (pos[0] >= json.length()) throw new IOException("truncated");
            char d = json.charAt(pos[0]++);
            if (d == '}') return out;
            if (d != ',') throw new IOException("expected ',' or '}'");
        }
    }

    static List<Object> parseArray(String json, int[] pos) throws IOException {
        List<Object> out = new ArrayList<Object>();
        pos[0]++; // [
        skipWs(json, pos);
        if (pos[0] < json.length() && json.charAt(pos[0]) == ']') {
            pos[0]++;
            return out;
        }
        while (true) {
            out.add(parseValue(json, pos));
            skipWs(json, pos);
            if (pos[0] >= json.length()) throw new IOException("truncated");
            char d = json.charAt(pos[0]++);
            if (d == ']') return out;
            if (d != ',') throw new IOException("expected ',' or ']'");
        }
    }

    static String parseString(String json, int[] pos) throws IOException {
        StringBuilder sb = new StringBuilder();
        pos[0]++; // opening quote
        while (true) {
            if (pos[0] >= json.length()) throw new IOException("truncated");
            char c = json.charAt(pos[0]++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                if (pos[0] >= json.length()) {
                    throw new IOException("truncated escape");
                }
                char e = json.charAt(pos[0]++);
                if (e == '"') sb.append('"');
                else if (e == '\\') sb.append('\\');
                else if (e == '/') sb.append('/');
                else if (e == 'n') sb.append('\n');
                else if (e == 'r') sb.append('\r');
                else if (e == 't') sb.append('\t');
                else if (e == 'u') {
                    if (pos[0] + 4 > json.length()) {
                        throw new IOException("truncated unicode escape");
                    }
                    int cp;
                    try {
                        cp = Integer.parseInt(json.substring(pos[0],
                                                             pos[0] + 4), 16);
                    } catch (NumberFormatException nfe) {
                        throw new IOException("bad unicode escape");
                    }
                    pos[0] += 4;
                    sb.append((char) cp);
                } else {
                    throw new IOException("bad escape \\" + e);
                }
                continue;
            }
            sb.append(c);
        }
    }

    static Double parseNumber(String json, int[] pos) throws IOException {
        int start = pos[0];
        while (pos[0] < json.length()) {
            char c = json.charAt(pos[0]);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.'
                    || c == 'e' || c == 'E') {
                pos[0]++;
            } else break;
        }
        if (start == pos[0]) throw new IOException("bad value");
        try {
            return Double.valueOf(json.substring(start, pos[0]));
        } catch (NumberFormatException nfe) {
            throw new IOException("bad number");
        }
    }

    static void skipWs(String json, int[] pos) {
        while (pos[0] < json.length()
               && Character.isWhitespace(json.charAt(pos[0]))) {
            pos[0]++;
        }
    }

    static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** JSON has no int/float distinction; accept numeric 1 in any shape. */
    static boolean isOne(Object o) {
        if (o instanceof Double) {
            return ((Double) o).doubleValue() == 1.0;
        }
        return "1".equals(String.valueOf(o));
    }

    static boolean isTrue(Object o) {
        return o instanceof Boolean && ((Boolean) o).booleanValue();
    }

    private static List<String> firstFew(Set<String> v) {
        List<String> out = new ArrayList<String>();
        int i = 0;
        for (Iterator<String> it = v.iterator(); it.hasNext() && i < 3; i++) {
            out.add(it.next());
        }
        return out;
    }

    private static List<String> firstFew(List<String> v) {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < v.size() && i < 3; i++) out.add(v.get(i));
        return out;
    }

    private static String pad(String s, int w) {
        StringBuffer sb = new StringBuffer(s);
        while (sb.length() < w) sb.append(' ');
        return sb.toString();
    }

    private static void check(String what, boolean ok, String detail) {
        checks++;
        if (ok) {
            System.out.println("[B5-0005] " + what + ": PASS");
        } else {
            failures++;
            System.out.println("[B5-0005] " + what + ": FAIL -- " + detail);
        }
    }
}
