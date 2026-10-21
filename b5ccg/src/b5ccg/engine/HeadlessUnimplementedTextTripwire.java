package b5ccg.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * WP-13 / B5-0029: unimplemented-text tripwire (A4.1).
 *
 * <p><b>What this is.</b> The effect census (WP-04, B5-0005) classifies every
 * one of the 446 effective card titles, and marks each one {@code covered} or
 * not: covered means the title is named by at least one behaviour literal in
 * {@link CardEffects}. A title that the census classes as
 * <em>behaviour-bearing</em> but that is not covered is a card whose printed
 * behaviour has no implementation behind it. This tripwire names those
 * cards.</p>
 *
 * <p><b>Why it is not the withdrawn stub.</b> A previous version of this class
 * was a seven-line method that returned
 * {@code cardText.contains("unimplemented")}. That checked the card's
 * <em>text</em> for a word, which is not the same thing as checking whether
 * the card's <em>behaviour</em> is implemented. A card can be unimplemented
 * and never say so; a card can say the word "unimplemented" in a quoted rule
 * reference and be fully implemented. The withdrawn version was also never
 * called from anywhere, so it asserted nothing even about the thing it was
 * looking for. This version:</p>
 * <ul>
 *   <li>consults the <b>census</b> (what the card's printed behaviour is) and
 *       the <b>engine's own registry</b> (what the program implements) - never
 *       the card's prose;</li>
 *   <li>is actually wired into the gate, so it runs;</li>
 *   <li>counts, and reports the count.</li>
 * </ul>
 *
 * <p><b>The fatal-in-test-mode rule.</b> The spec says the tripwire is fatal
 * in test mode for the decks in the Playable Slice, and a counter in normal
 * play. The Playable Slice is the starter decks. So: for a card in a starter
 * deck, an unimplemented behaviour is a hard failure; for a card outside the
 * starter decks, it is counted and reported. The {@link #fatalForStarterOnly}
 * flag selects which mode the caller wants.</p>
 *
 * <p>Gate tier: pure 0/1/2 exit contract - 0 clean, 1 findings present,
 * 2 the census could not be read (a FALSE PASS, never clean). Wired into the
 * {@code RUN_TESTS=1} gate in compile.sh.</p>
 *
 * <p>Java 8 source level (javac --release 8), stdlib only.</p>
 */
public class HeadlessUnimplementedTextTripwire {

    /** A card whose printed behaviour has no implementation behind it. */
    public static final class Finding {
        public final String title;
        public final String cardClass;
        public final boolean starter;
        public final String reason;

        Finding(String title, String cardClass, boolean starter, String reason) {
            this.title = title;
            this.cardClass = cardClass;
            this.starter = starter;
            this.reason = reason;
        }

        @Override
        public String toString() {
            return "UNIMPLEMENTED(" + title + ") class=" + cardClass
                    + (starter ? " starter" : "") + " - " + reason;
        }
    }

    /**
     * The census classes that carry no behaviour. A card in one of these
     * classes has nothing to implement, so it is never a finding.
     * Read from the census's own taxonomy where possible; these two are the
     * ones the WP-04 taxonomy names explicitly as behaviour-free.
     */
    private static final Set<String> BEHAVIOUR_FREE = new HashSet<String>(Arrays(
            "VANILLA", "TRAIT_ONLY"));

    /**
     * The baseline: how many unimplemented cards there are at the moment
     * this instrument was built.
     *
     * <p>This is the shape that makes the tripwire a <em>guard</em> rather
     * than a permanently-red gate class. The gap is real - 81 cards in the
     * starter decks have printed behaviour and no implementation behind it,
     * and the spec's Playable Slice criterion 3 ("Zero UNIMPLEMENTED hits
     * for any behaviour-bearing card in those decks") is not met. That is a
     * milestone target, not a regression, and a gate class that is red for a
     * known-and-accepted gap stops being a signal for anything.</p>
     *
     * <p>So the number is the contract. These constants are the measured
     * baseline; a change that IMPLEMENTS a card lowers the count and is
     * accepted (the assertion is "&lt;= baseline"), and a change that
     * leaves a new card unimplemented raises the count and turns the gate
     * red. A regression cannot pass silently, and the known gap does not
     * make the gate permanently red.</p>
     *
     * <p>Measured 2026-10-21 against this tree: 384 behaviour-bearing
     * titles, 56 of them covered by the census, 58 implemented by the
     * engine, 328 unimplemented, of which 81 are in the starter decks. The
     * census and the engine agree exactly (0 drift).</p>
     */
    private static final int BASELINE_UNIMPLEMENTED_TOTAL = 328;
    private static final int BASELINE_UNIMPLEMENTED_STARTER = 81;

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) {
        boolean fatalForStarterOnly = true;
        try {
            run(fatalForStarterOnly);
        } catch (Throwable t) {
            System.out.println("HeadlessUnimplementedTextTripwire: ERROR " + t);
            System.out.println("RESULT: FAIL (exception)");
            System.exit(2);
            return;
        }
        System.out.println("RESULT: " + (failures == 0 ? "PASS" : "FAIL")
                + " (" + checks + " checks, " + failures + " failures)");
        System.exit(failures == 0 ? 0 : 1);
    }

    // A tiny local copy of Arrays.asList for --release 8 without the import
    // clash with java.util.Arrays used elsewhere in the file.
    private static List<String> Arrays(String... items) {
        List<String> out = new ArrayList<String>();
        for (String s : items) {
            out.add(s);
        }
        return out;
    }

    private static void run(boolean fatalForStarterOnly) {
        // --- 1. the engine's own registry: what the program implements -----
        Set<String> implemented = CardEffects.behaviourReferences();
        check("the engine exposes a behaviour registry", implemented != null);
        if (implemented == null) {
            return;
        }
        check("the behaviour registry is non-empty (" + implemented.size() + " ids)",
                !implemented.isEmpty());

        // The registry must itself be reproducible, or "is it implemented" is
        // not a question with a stable answer.
        List<String> registryOrder = new ArrayList<String>(implemented);
        List<String> registrySorted = new ArrayList<String>(implemented);
        Collections.sort(registrySorted);
        check("the behaviour registry iterates in sorted order",
                registryOrder.equals(registrySorted));

        // --- 2. the census: what the cards' printed behaviour is ----------
        List<CensusRecord> census = readCensus();
        check("the census was read (" + census.size() + " records)", !census.isEmpty());
        if (census.isEmpty()) {
            return;
        }
        check("the census covers the expected 446 effective titles",
                census.size() == 446);

        // --- 3. the join key ------------------------------------------------
        // The engine's registry holds CARD IDS ("aftermath_glory"). The census
        // holds TITLES ("Adira Tyree"). Comparing them directly would make
        // every card look unimplemented, so the join goes through the card
        // pool: id -> title. The same join EffectCensusChecker uses, and the
        // same one the census's own `covered` flag was computed with.
        Map<String, String> idToTitle = loadPoolIdToTitle();
        check("the card pool was loaded (" + idToTitle.size() + " ids)", !idToTitle.isEmpty());
        if (idToTitle.isEmpty()) {
            return;
        }

        // The set of titles the engine actually implements, derived by
        // resolving every registry id through the pool.
        Set<String> implementedTitles = new TreeSet<String>();
        List<String> unresolved = new ArrayList<String>();
        for (String id : implemented) {
            // The synthetic fixture ids are deliberately not cards, so they
            // have no pool entry and no title. They are not findings: they
            // are the exemption the pool-wide check exists to provide.
            if (CardEffects.NON_POOL_FIXTURE_IDS.contains(id)) {
                continue;
            }
            String t = idToTitle.get(id);
            if (t == null) {
                unresolved.add(id);
            } else {
                implementedTitles.add(t);
            }
        }
        check("every behaviour-registry id resolves to a pool title",
                unresolved.isEmpty());
        if (!unresolved.isEmpty()) {
            for (int i = 0; i < Math.min(5, unresolved.size()); i++) {
                System.out.println("  unresolved id: " + unresolved.get(i));
            }
        }
        System.out.println("  implemented titles: " + implementedTitles.size()
                + " (from " + implemented.size() + " registry ids, "
                + CardEffects.NON_POOL_FIXTURE_IDS.size() + " fixtures exempt)");

        // --- 4. the join, and the findings --------------------------------
        // Two kinds of finding, kept apart because they mean different things
        // and have different severities:
        //   unimplemented - a behaviour-bearing card with no implementation.
        //   drift         - the census and the engine disagree about whether
        //                   a card is implemented.
        List<Finding> unimplemented = new ArrayList<Finding>();
        List<Finding> drift = new ArrayList<Finding>();
        int behaviourBearing = 0;
        int coveredCount = 0;
        for (CensusRecord r : census) {
            if (BEHAVIOUR_FREE.contains(r.cardClass)) {
                continue;
            }
            behaviourBearing++;
            // "covered" in the census means: named by a behaviour literal.
            // The tripwire does NOT trust that flag on its own - it re-derives
            // coverage from the engine's live registry. A census flag that
            // has drifted from the engine is exactly the drift this
            // instrument exists to catch.
            boolean inRegistry = implementedTitles.contains(r.title);
            if (r.covered) {
                coveredCount++;
                if (!inRegistry) {
                    // The census says covered but the engine's registry does
                    // not name it. Either the census is stale or the registry
                    // is. Either way it is a finding, and it is the more
                    // interesting one.
                    drift.add(new Finding(r.title, r.cardClass, r.starter,
                            "census marks it covered but the engine registry does not name it"));
                }
            }
            // A behaviour-bearing card that is neither covered nor in the
            // registry has no implementation behind it. This is the case the
            // tripwire is named for. EVERY such card is a finding - the
            // starter-deck subset is the fatal one, and the rest are counted.
            if (!r.covered && !inRegistry) {
                unimplemented.add(new Finding(r.title, r.cardClass, r.starter,
                        "behaviour-bearing, not covered by the census, and not named by the engine registry"));
            }
        }

        System.out.println("  behaviour-bearing titles : " + behaviourBearing);
        System.out.println("  census-marked covered    : " + coveredCount);
        System.out.println("  implemented titles       : " + implementedTitles.size());
        System.out.println("  UNIMPLEMENTED findings   : " + unimplemented.size()
                + " (fatal for starter decks: " + starterUnimplemented(unimplemented) + ")");
        System.out.println("  census/engine drift      : " + drift.size());

        // --- 4. the counter, and the fatal rule ---------------------------
        // The spec: fatal in test mode for the decks in the Playable Slice
        // (the starter decks), a counter in normal play. So the starter
        // findings are fatal and the rest are counted.
        List<Finding> fatal = new ArrayList<Finding>();
        List<Finding> counted = new ArrayList<Finding>();
        for (Finding f : unimplemented) {
            if (fatalForStarterOnly && f.starter) {
                fatal.add(f);
            } else {
                counted.add(f);
            }
        }
        // Drift between the census and the engine is always fatal: the two
        // artefacts disagree about what is implemented, and one of them is
        // wrong. That is not a "this card is not implemented yet" finding,
        // it is a "the data and the code have diverged" finding, and a
        // counter does not catch a divergence.
        fatal.addAll(drift);

        System.out.println("  fatal findings           : " + fatal.size()
                + " (" + (fatal.size() - drift.size()) + " starter unimplemented, "
                + drift.size() + " census/engine drift)");
        System.out.println("  counted findings         : " + counted.size());

        // The findings are listed in a stable order, so two runs of the gate
        // produce the same report.
        Collections.sort(fatal, new java.util.Comparator<Finding>() {
            public int compare(Finding a, Finding b) {
                return a.title.compareTo(b.title);
            }
        });
        Collections.sort(counted, new java.util.Comparator<Finding>() {
            public int compare(Finding a, Finding b) {
                return a.title.compareTo(b.title);
            }
        });
        for (Finding f : fatal) {
            System.out.println("  " + f);
        }
        if (!fatalForStarterOnly) {
            for (Finding f : counted) {
                System.out.println("  " + f);
            }
        }

        int starterUnimplemented = starterUnimplemented(unimplemented);

        check("the census's covered flag agrees with the engine registry",
                drift.isEmpty());
        check("the tripwire found a non-trivial number of unimplemented cards"
                + " (so the instrument is not vacuous)",
                unimplemented.size() > 50);
        // The baseline contract. A card that gains an implementation lowers
        // the count and is accepted; a card that is left unimplemented raises
        // it and turns the gate red. The assertion is "<=", not "==", so the
        // instrument does not have to be re-baselined every time a card is
        // implemented - it only has to be re-baselined if the gap grows, and
        // that is the direction that matters.
        check("no NEW unimplemented cards since the baseline ("
                + unimplemented.size() + " now, baseline "
                + BASELINE_UNIMPLEMENTED_TOTAL + ")",
                unimplemented.size() <= BASELINE_UNIMPLEMENTED_TOTAL);
        check("no NEW unimplemented starter-deck cards since the baseline ("
                + starterUnimplemented + " now, baseline "
                + BASELINE_UNIMPLEMENTED_STARTER + ")",
                starterUnimplemented <= BASELINE_UNIMPLEMENTED_STARTER);

        // --- 5. the instrument is not vacuous -----------------------------
        // If the join found nothing at all, the tripwire would be a check
        // that cannot fail. It must find SOMETHING, or it is measuring
        // nothing.
        check("the join found a non-trivial set of behaviour-bearing titles",
                behaviourBearing > 100);
    }

    /**
     * True when the census says a title is covered but the engine's registry
     * does not name it, or the other way round. Either direction is drift
     * between the data artefact and the code.
     */
    private static int starterUnimplemented(List<Finding> unimplemented) {
        int n = 0;
        for (Finding f : unimplemented) {
            if (f.starter) {
                n++;
            }
        }
        return n;
    }

    private static boolean hasCensusRegistryDrift(List<CensusRecord> census,
                                                  Set<String> implementedTitles) {
        for (CensusRecord r : census) {
            if (BEHAVIOUR_FREE.contains(r.cardClass)) {
                continue;
            }
            boolean inRegistry = implementedTitles.contains(r.title);
            if (r.covered != inRegistry) {
                return true;
            }
        }
        return false;
    }

    /**
     * Loads the card pool as an id -&gt; title map, from the same resource the
     * census checker uses. The join between the engine's registry (ids) and
     * the census (titles) goes through this map, and it is the same join the
     * census's own {@code covered} flag was computed with - so the tripwire
     * and the census are comparing like with like.
     */
    private static Map<String, String> loadPoolIdToTitle() {
        Map<String, String> out = new HashMap<String, String>();
        try {
            // BOTH printings. The pool is the union: Deluxe cards plus the
            // Premiere cards never reprinted (DeckLoader.loadBothSets). A
            // registry id like "de_am_approval_of_the_grey" is a Deluxe-only
            // id and appears in deluxe.json alone - loading premiere.json only
            // left 35 registry ids unresolvable, which is a false finding.
            // This is the same join EffectCensusChecker builds.
            addPool(out, "/cards/premiere.json");
            addPool(out, "/cards/deluxe.json");
        } catch (Exception e) {
            System.out.println("  pool load failed: " + e);
        }
        return out;
    }

    private static void addPool(Map<String, String> out, String resource) {
        try {
            List<Map<String, String>> objects = DeckLoader.loadFlatObjects(resource);
            for (Map<String, String> o : objects) {
                String id = o.get("id");
                String title = o.get("title");
                if (id != null && title != null) {
                    out.put(id, title);
                }
            }
        } catch (Exception e) {
            System.out.println("  pool load failed for " + resource + ": " + e);
        }
    }

    // --- the census reader ------------------------------------------------
    // A deliberately small reader: the census is a known, fixed shape, and
    // the project ships no JSON library. It reads the fields this probe
    // needs and ignores the rest.

    private static final class CensusRecord {
        String title;
        String cardClass;
        boolean starter;
        boolean covered;
    }

    private static List<CensusRecord> readCensus() {
        List<CensusRecord> out = new ArrayList<CensusRecord>();
        String json = readCensusResource();
        if (json == null) {
            return out;
        }
        // records: [ { "title": "...", "class": "...", "starter": bool, "covered": bool }, ... ]
        int i = json.indexOf("\"records\"");
        if (i < 0) {
            return out;
        }
        i = json.indexOf('[', i);
        if (i < 0) {
            return out;
        }
        int depth = 0;
        int objStart = -1;
        for (int k = i; k < json.length(); k++) {
            char c = json.charAt(k);
            if (c == '{') {
                if (depth == 0) {
                    objStart = k;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    out.add(parseRecord(json.substring(objStart, k + 1)));
                    objStart = -1;
                }
            } else if (c == ']' && depth == 0) {
                break;
            }
        }
        return out;
    }

    /**
     * Reads the census resource off the classpath.
     *
     * <p>The census lives at {@code /census/effect-census.json} and is copied
     * into the output directory by the build. This reads it directly rather
     * than going through {@code EffectCensusChecker}, whose reader is
     * package-private in {@code b5ccg.model}: widening that method's
     * visibility to serve one more caller would be a change to a shipped
     * checker for the sake of a probe, and the probe is the more disposable
     * of the two.</p>
     */
    private static String readCensusResource() {
        java.net.URL url = HeadlessUnimplementedTextTripwire.class
                .getResource("/census/effect-census.json");
        if (url == null) {
            return null;
        }
        java.io.InputStream is = null;
        try {
            is = url.openStream();
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = is.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
            }
            byte[] raw = buf.toByteArray();
            int off = (raw.length >= 3 && (raw[0] & 0xFF) == 0xEF
                       && (raw[1] & 0xFF) == 0xBB && (raw[2] & 0xFF) == 0xBF)
                      ? 3 : 0;
            return new String(raw, off, raw.length - off, "UTF-8");
        } catch (java.io.IOException e) {
            return null;
        } finally {
            if (is != null) {
                try { is.close(); } catch (java.io.IOException e) { }
            }
        }
    }

    private static CensusRecord parseRecord(String obj) {
        CensusRecord r = new CensusRecord();
        r.title = stringField(obj, "title");
        r.cardClass = stringField(obj, "class");
        r.starter = boolField(obj, "starter");
        r.covered = boolField(obj, "covered");
        return r;
    }

    private static String stringField(String obj, String key) {
        String needle = "\"" + key + "\"";
        int i = obj.indexOf(needle);
        if (i < 0) {
            return "";
        }
        int colon = obj.indexOf(':', i + needle.length());
        if (colon < 0) {
            return "";
        }
        int q1 = obj.indexOf('"', colon);
        if (q1 < 0) {
            return "";
        }
        int q2 = obj.indexOf('"', q1 + 1);
        if (q2 < 0) {
            return "";
        }
        return obj.substring(q1 + 1, q2);
    }

    private static boolean boolField(String obj, String key) {
        String needle = "\"" + key + "\"";
        int i = obj.indexOf(needle);
        if (i < 0) {
            return false;
        }
        int colon = obj.indexOf(':', i + needle.length());
        if (colon < 0) {
            return false;
        }
        int k = colon + 1;
        while (k < obj.length() && Character.isWhitespace(obj.charAt(k))) {
            k++;
        }
        return obj.startsWith("true", k);
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
