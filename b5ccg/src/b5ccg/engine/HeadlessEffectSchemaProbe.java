package b5ccg.engine;

import b5ccg.engine.effect.EffectDocument;
import b5ccg.engine.effect.EffectEntry;
import b5ccg.engine.effect.EffectKind;
import b5ccg.engine.effect.EffectLoadException;
import b5ccg.engine.effect.EffectLoader;
import b5ccg.engine.effect.EffectOperation;
import b5ccg.engine.effect.EffectPrimitive;
import b5ccg.engine.effect.EffectRegistry;
import b5ccg.engine.effect.EffectRenderer;
import b5ccg.model.Card;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WP-10 / B5-0028 (A3): the probe for the data-driven effect system.
 *
 * <p>The row's acceptance is three clauses, and this probe checks each of
 * them by name:</p>
 * <ol>
 *   <li><b>"schema validates"</b> - the shipped v0 document loads cleanly,
 *       every entry names a card that exists, and every entry's provenance
 *       hash matches the governing printed text. The JSON-Schema half of
 *       this clause is checked separately by
 *       {@code .agent/tools/validate-effect-schema.py}, which is a Python
 *       process that shares no code with the engine; the two are
 *       deliberately independent.</li>
 *   <li><b>"an unknown effect kind fails the load"</b> - the sharp test. The
 *       loader must refuse a document that names a kind it does not know,
 *       and it must say which kind and where.</li>
 *   <li><b>"a renderer exists"</b> - every entry renders to a canonical
 *       line, the same entry renders to the same line every time, and the
 *       renderer never reads the card's printed text (A3 principle 3).</li>
 * </ol>
 *
 * <p><b>Why fail-closed is checked from several directions.</b> One test of
 * "an unknown kind fails" would pass even if the loader happened to reject
 * that one string. So the probe attacks the loader with a bad kind, a bad
 * primitive, a bad parameter, a bad window, an unknown field, a missing
 * field, an unknown schema version, and an unpublished bespoke handler - and
 * requires that each is refused <em>and</em> that the refusal names the
 * offending thing. A loader that refused everything for the wrong reason
 * would still pass the first test; it would not pass these.</p>
 *
 * <p><b>Why the registry's shape is checked here.</b> A3 principle 5 warns
 * that "the registry is where the next god class would form" and asks for one
 * class per primitive family. That is a property of the code, and a property
 * of the code can be asked about: the probe asserts that the registry holds
 * more than one family and that no family has grown past a small bound. A
 * registry that quietly became one class again would be caught here rather
 * than by a reader's opinion.</p>
 *
 * <p>Gate tier: pure 0/1/2 exit contract - 0 clean, 1 findings present,
 * 2 the harness could not build its fixtures (a FALSE PASS, never clean).</p>
 *
 * <p>Java 8 source level (javac --release 8), stdlib only.</p>
 */
public class HeadlessEffectSchemaProbe {

    /** The shipped v0 effect document. */
    private static final String RESOURCE = "/effects/effects-v0.json";

    /** The number of entries the v0 document holds. */
    private static final int EXPECTED_ENTRIES = 29;

    /** The number of distinct cards the v0 document describes. */
    private static final int EXPECTED_CARDS = 17;

    /**
     * The canonical rendering of one known entry.
     *
     * <p>A golden value, not a pattern: the point of a canonical renderer is
     * that the text can be compared, and a comparison against a pattern
     * would not catch a renderer that drifted.</p>
     */
    private static final String GOLDEN_RENDER =
        "enh_babylon5_unrest \"Babylon 5 Unrest\" [COST_MODIFIER] window=ACTION trigger=(none) "
        + "target=GLOBAL/ctrl=ANY ops=[COST_MODIFIER(cost=INFLUENCE, amount=1)] "
        + "duration=END_OF_ROUND stacking=ADDITIVE protection=NONE precedence=CARD_TEXT "
        + "binding=NONE rules=RB:VI/cumulative-effects";

    /**
     * A second golden value, for an entry whose fields are all non-default.
     *
     * <p><b>Why there are two, and not one.</b> The first golden was written
     * for an entry whose source binding is {@code NONE}, and a
     * red-then-green experiment showed that this made the golden blind: a
     * renderer that dropped the source binding entirely produced the
     * <em>same</em> string for that entry, and the probe stayed green while
     * the renderer was wrong. A golden value that cannot distinguish a
     * correct renderer from a broken one is not evidence. This second
     * golden exercises the fields the first one leaves at their defaults:
     * a non-NONE source binding, a card-scoped target with a card-type
     * filter, a permanent duration, and a mark operation.</p>
     *
     * <p>The line below is written out from the entry's own fields and the
     * documented render format, not copied from the renderer's output: if
     * the two disagree, the probe fails and prints the actual, which is the
     * behaviour that makes a golden value worth having.</p>
     */
    private static final String GOLDEN_RENDER_BOUND =
        "event_contact_with_shadows \"Contact with Shadows\" [ONE_SHOT] window=ANY "
        + "trigger=(none) target=CARD/ctrl=SELF/type=CHARACTER "
        + "ops=[MARK_GRANT(mark=SHADOW, count=1)] duration=PERMANENT stacking=ADDITIVE "
        + "protection=NONE precedence=CARD_TEXT binding=PERMANENT_MARK rules=RB:VI/marks";

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable t) {
            System.out.println("HeadlessEffectSchemaProbe: ERROR " + t);
            System.out.println("RESULT: FAIL (exception)");
            System.exit(2);
            return;
        }
        System.out.println("RESULT: " + (failures == 0 ? "PASS" : "FAIL")
                + " (" + checks + " checks, " + failures + " failures)");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void run() throws Exception {
        EffectRegistry registry = EffectRegistry.defaultRegistry();
        EffectRenderer renderer = new EffectRenderer(registry);

        System.out.println("=== EFFECT SCHEMA PROBE (B5-0028 / WP-10) ===");

        // ── The registry ───────────────────────────────────────────────────
        System.out.println("--- the primitive registry ---");
        System.out.println("  primitives: " + registry.size());
        System.out.println("  families:   " + registry.knownFamilies().size()
                + "  " + registry.knownFamilies());
        System.out.println("  bespoke handlers published: "
                + registry.bespokeHandlers().size() + " ("
                + registry.knownBespokeHandlerKeys() + ")");

        check("the registry holds at least 15 primitives",
                registry.size() >= 15, "size " + registry.size());
        check("the registry is modular: at least 10 primitive families",
                registry.knownFamilies().size() >= 10,
                "families " + registry.knownFamilies().size());
        check("the registry is modular: no family holds more than 3 primitives",
                maxFamilySize(registry) <= 3, "largest family " + maxFamilySize(registry));
        check("every effect kind is covered by at least one primitive",
                registry.knownKinds().size() == EffectKind.values().length,
                "covered " + registry.knownKinds() + " of " + EffectKind.values().length);

        // ── Clause 1: the schema validates (the engine's half) ────────────
        System.out.println("--- clause 1: the shipped document validates ---");
        EffectDocument doc = EffectLoader.loadFromResource(RESOURCE, registry);
        check("the shipped effect document loads without error", doc != null, "");
        check("the document's schema version is 1",
                doc.getSchemaVersion() == 1, "version " + doc.getSchemaVersion());
        check("the document holds " + EXPECTED_ENTRIES + " entries",
                doc.size() == EXPECTED_ENTRIES, "entries " + doc.size());
        check("the document describes " + EXPECTED_CARDS + " distinct cards",
                doc.cardIds().size() == EXPECTED_CARDS, "cards " + doc.cardIds().size());

        Map<String, String> pool = governingCardPool();
        check("the card pool was read for the provenance check",
                pool.size() > 400, "cards " + pool.size());

        int missingCard = 0;
        int hashMismatch = 0;
        int noRuleId = 0;
        for (EffectEntry entry : doc.getEntries()) {
            String text = pool.get(entry.getCardId());
            if (text == null) {
                missingCard++;
                System.out.println("  MISSING CARD: " + entry.getCardId());
                continue;
            }
            String expected = sha256(text);
            String actual = entry.getProvenance() == null
                    ? null : entry.getProvenance().getTextSha256();
            if (expected == null ? actual != null : !expected.equals(actual)) {
                hashMismatch++;
                System.out.println("  TEXT HASH MISMATCH: " + entry.getCardId()
                        + " expected " + expected + " got " + actual);
            }
            if (entry.getProvenance() == null || entry.getProvenance().getRuleIds().isEmpty()) {
                noRuleId++;
            }
        }
        check("every entry names a card that exists in the pool",
                missingCard == 0, missingCard + " missing");
        check("every entry's text hash matches the governing printed text",
                hashMismatch == 0, hashMismatch + " mismatched");
        check("every entry's provenance cites at least one rule ID",
                noRuleId == 0, noRuleId + " without a rule ID");

        Set<String> usedPrimitives = new HashSet<String>();
        int bespokeUses = 0;
        for (EffectEntry entry : doc.getEntries()) {
            for (EffectOperation op : entry.getOperations()) {
                usedPrimitives.add(op.getPrimitive());
                if ("BESPOKE".equals(op.getPrimitive())) bespokeUses++;
            }
        }
        check("every primitive the data uses is registered",
                registry.primitiveNames().containsAll(usedPrimitives),
                "unregistered " + (usedPrimitives.toString()));
        check("the v0 data does not use the bespoke escape hatch",
                bespokeUses == 0, bespokeUses + " bespoke operation(s)");

        // ── Clause 2: an unknown effect kind fails the load ───────────────
        System.out.println("--- clause 2: the load fails closed ---");
        expectLoadFailure("an unknown effect kind is refused",
                BASE_DOCUMENT.replace("\"kind\":\"ONE_SHOT\"", "\"kind\":\"NOT_A_KIND\""),
                "unknown effect kind");
        expectLoadFailure("an unknown primitive is refused",
                BASE_DOCUMENT.replace("\"primitive\":\"DRAW\"", "\"primitive\":\"NO_SUCH_PRIMITIVE\""),
                "unknown effect primitive");
        expectLoadFailure("a parameter outside the primitive's closed set is refused",
                BASE_DOCUMENT.replace("\"count\":1", "\"count\":\"MANY\""),
                "is not an integer");
        expectLoadFailure("a parameter outside the primitive's closed set (enum) is refused",
                BASE_DOCUMENT.replace("\"primitive\":\"DRAW\",\"params\":{\"count\":1}",
                        "\"primitive\":\"STAT_MODIFY\",\"params\":{\"stat\":\"NOT_A_STAT\",\"amount\":1}"),
                "must be one of");
        expectLoadFailure("an unknown window is refused",
                BASE_DOCUMENT.replace("\"window\":\"ANY\"", "\"window\":\"SOMETIMES\""),
                "unknown effect window");
        expectLoadFailure("an unknown field is refused",
                BASE_DOCUMENT.replace("\"duration\":\"INSTANT\"",
                        "\"duration\":\"INSTANT\",\"surprise\":true"), "unknown field");
        expectLoadFailure("a missing required field is refused",
                BASE_DOCUMENT.replace("\"stacking\":\"ADDITIVE\",", ""),
                "missing required field");
        expectLoadFailure("an unknown schema version is refused",
                BASE_DOCUMENT.replace("\"schema_version\":1", "\"schema_version\":99"),
                "is not supported");
        expectLoadFailure("an unpublished bespoke handler is refused",
                BASE_DOCUMENT.replace("\"primitive\":\"DRAW\",\"params\":{\"count\":1}",
                        "\"primitive\":\"BESPOKE\",\"params\":{\"handler\":\"NOT_PUBLISHED\"}"),
                "is not registered");

        // A bespoke operation whose handler IS published loads, so the
        // refusal above is about the missing handler and not about the
        // escape hatch being unusable.
        {
            EffectRegistry published = EffectRegistry.defaultRegistry();
            published.registerBespokeHandler("test-only", "someJavaMethod");
            String json = BASE_DOCUMENT.replace("\"primitive\":\"DRAW\",\"params\":{\"count\":1}",
                    "\"primitive\":\"BESPOKE\",\"params\":{\"handler\":\"test-only\"}");
            boolean loaded = true;
            try {
                EffectLoader.parse(json, published);
            } catch (EffectLoadException ele) {
                loaded = false;
                System.out.println("  published bespoke handler was refused: " + ele.getMessage());
            }
            check("a bespoke operation with a PUBLISHED handler loads",
                    loaded, "the escape hatch must be usable when a handler is published");
        }

        // ── Clause 3: a renderer exists ───────────────────────────────────
        System.out.println("--- clause 3: the renderer ---");
        String rendered = renderer.render(doc);
        check("the whole document renders", rendered != null && rendered.length() > 0, "");
        check("the render is stable across two runs",
                rendered.equals(renderer.render(doc)), "the render is not deterministic");
        check("the render has one line per entry",
                countLines(rendered) == EXPECTED_ENTRIES + 1,
                "lines " + countLines(rendered));

        EffectEntry golden = doc.entriesFor("enh_babylon5_unrest").get(0);
        String goldenLine = renderer.render(golden);
        check("a known entry renders to its golden line",
                GOLDEN_RENDER.equals(goldenLine), "got: " + goldenLine);
        check("the same entry renders identically when rendered again",
                goldenLine.equals(renderer.render(golden)), "");

        // The second golden: an entry whose fields are all non-default. The
        // first golden alone was measured to be blind to a renderer that
        // dropped the source binding, because that entry's binding is NONE.
        EffectEntry bound = doc.entriesFor("event_contact_with_shadows").get(2);
        String boundLine = renderer.render(bound);
        check("an entry with non-default fields renders to its second golden line",
                GOLDEN_RENDER_BOUND.equals(boundLine), "got: " + boundLine);

        // The renderer must not read the card's printed text: A3 principle 3
        // says the text is the specification and the display string, and is
        // never parsed at runtime. A renderer that folded the text in would
        // make the later comparison pass compare a thing with itself.
        String shadowStrikeText = pool.get("event_shadow_strike");
        String shadowStrikeLine = renderer.render(doc.entriesFor("event_shadow_strike").get(0));
        check("the renderer does not fold the card's printed text into its output",
                shadowStrikeText != null && shadowStrikeLine.indexOf(shadowStrikeText) < 0,
                "the rendered line contains the printed text");

        // The registry's family split is printed above; make the god-class
        // guard explicit for a reader as well as for the gate.
        for (Map.Entry<String, Integer> e : registry.familySizes().entrySet()) {
            check("family " + e.getKey() + " holds " + e.getValue() + " primitive(s)",
                    e.getValue().intValue() <= 3, "");
        }
    }

    /**
     * A minimal, valid effect document, used as the base for the fail-closed
     * attacks: each test substitutes one field and expects the loader to
     * refuse it and to name the offending thing.
     */
    private static final String BASE_DOCUMENT =
        "{\"schema_version\":1,\"entries\":[{"
        + "\"cardId\":\"probe_card\",\"title\":\"Probe Card\",\"kind\":\"ONE_SHOT\","
        + "\"window\":\"ANY\",\"requirements\":[],"
        + "\"target\":{\"scope\":\"CARD\",\"controllerRelation\":\"ANY\"},"
        + "\"conditions\":[],\"operations\":[{\"primitive\":\"DRAW\",\"params\":{\"count\":1}}],"
        + "\"duration\":\"INSTANT\",\"stacking\":\"ADDITIVE\",\"protection\":\"NONE\","
        + "\"precedenceLayer\":\"CARD_TEXT\",\"sourceBinding\":\"NONE\","
        + "\"provenance\":{\"ruleIds\":[\"RB:I/influence\"],\"printingScope\":\"SINGLE_PRINTING\"}"
        + "}]}";

    /**
     * Asserts that a document is refused, and that the refusal names the
     * offending thing.
     */
    private static void expectLoadFailure(String what, String json, String expectedFragment) {
        EffectRegistry registry = EffectRegistry.defaultRegistry();
        try {
            EffectLoader.parse(json, registry);
            check(what, false, "the document LOADED - it should have been refused");
        } catch (EffectLoadException ele) {
            String message = ele.getMessage();
            boolean named = message != null && message.indexOf(expectedFragment) >= 0;
            check(what, named, "refused, but the message did not name '"
                    + expectedFragment + "': " + message);
        } catch (Exception other) {
            check(what, false, "refused with the wrong exception type: " + other);
        }
    }

    /**
     * The card pool keyed by canonical id, holding the GOVERNING text.
     *
     * <p>D5 (the owner ruling recorded in {@code docs/DECISIONS.md}) makes the
     * Deluxe printing govern a dual title, so the Deluxe twin wins and the
     * Premiere card is used only when there is no Deluxe twin. The
     * {@code de_} prefix is stripped by
     * {@link CardIdNamespaceGuard#canonicalKey(String)}.</p>
     *
     * <p><b>Why the card files are read directly rather than through
     * {@link DeckLoader}.</b> {@code DeckLoader.parseCards} builds
     * {@code Card} objects, and the Deluxe card file uses field names the
     * card classes do not know, so every one of the 383 Deluxe cards
     * prints an "UNKNOWN FIELD" line. That noise is pre-existing and is not
     * this row's to change, but a probe that emits 2 000 lines of it would
     * bury its own verdict in the gate log. This probe needs the id and the
     * text and nothing else, so it reads the two files with the same
     * {@link MiniJson} the shipped game uses and takes exactly what it
     * needs.</p>
     */
    private static Map<String, String> governingCardPool() throws Exception {
        Map<String, String> pool = new HashMap<String, String>();
        readCardTexts("/cards/premiere.json", pool);
        // The Deluxe printing is read second, so it overwrites the Premiere
        // twin: the Deluxe text governs a dual title (D5).
        readCardTexts("/cards/deluxe.json", pool);
        return pool;
    }

    /** Reads the id and text of every card in one card file into the pool. */
    private static void readCardTexts(String path, Map<String, String> pool) throws Exception {
        java.io.InputStream is = EffectLoaderResource(path);
        if (is == null) {
            throw new java.io.FileNotFoundException("card file not found: " + path);
        }
        try {
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = is.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
            }
            String json = new String(buf.toByteArray(), "UTF-8");
            List<Object> cards = MiniJson.parseArray(json);
            for (int i = 0; i < cards.size(); i++) {
                Object o = cards.get(i);
                if (!(o instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> card = (Map<String, Object>) o;
                Object id = card.get("id");
                Object text = card.get("text");
                if (!(id instanceof String)) continue;
                pool.put(CardIdNamespaceGuard.canonicalKey((String) id),
                         text instanceof String ? (String) text : "");
            }
        } finally {
            try { is.close(); } catch (java.io.IOException e) { }
        }
    }

    /** Opens a classpath resource as a stream, or null when it is absent. */
    private static java.io.InputStream EffectLoaderResource(String path) throws Exception {
        java.net.URL url = HeadlessEffectSchemaProbe.class.getResource(path);
        return url == null ? null : url.openStream();
    }

    private static int maxFamilySize(EffectRegistry registry) {
        int max = 0;
        for (Integer size : registry.familySizes().values()) {
            if (size.intValue() > max) max = size.intValue();
        }
        return max;
    }

    private static int countLines(String text) {
        int lines = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') lines++;
        }
        return lines;
    }

    /** The SHA-256 of a string's UTF-8 bytes, lowercase hex. */
    private static String sha256(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(text.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hash.length; i++) {
            int v = hash[i] & 0xff;
            if (v < 16) sb.append('0');
            sb.append(Integer.toHexString(v));
        }
        return sb.toString();
    }

    private static void check(String what, boolean ok, String detail) {
        checks++;
        if (ok) {
            System.out.println("PASS  " + what);
        } else {
            failures++;
            System.out.println("FAIL  " + what + (detail == null || detail.isEmpty()
                    ? "" : (" - " + detail)));
        }
    }
}
