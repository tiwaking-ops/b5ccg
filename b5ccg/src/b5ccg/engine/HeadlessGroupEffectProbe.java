package b5ccg.engine;

import b5ccg.engine.effect.EffectCardStatus;
import b5ccg.engine.effect.EffectCondition;
import b5ccg.engine.effect.EffectConditionType;
import b5ccg.engine.effect.EffectDocument;
import b5ccg.engine.effect.EffectDuration;
import b5ccg.engine.effect.EffectEntry;
import b5ccg.engine.effect.EffectKind;
import b5ccg.engine.effect.EffectLoadException;
import b5ccg.engine.effect.EffectLoader;
import b5ccg.engine.effect.EffectOperation;
import b5ccg.engine.effect.EffectPrecedence;
import b5ccg.engine.effect.EffectProtection;
import b5ccg.engine.effect.EffectRegistry;
import b5ccg.engine.effect.EffectRenderer;
import b5ccg.engine.effect.EffectRequirementType;
import b5ccg.engine.effect.EffectSourceBinding;
import b5ccg.engine.effect.EffectStacking;
import b5ccg.engine.effect.EffectTrait;
import b5ccg.engine.effect.EffectTrigger;
import b5ccg.engine.effect.EffectWindow;
import b5ccg.engine.effect.bespoke.SpinDoctorsPerceivedWinner;
import b5ccg.model.enums.CardType;
import b5ccg.model.enums.Race;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * WP-11 / B5-0034 (A3): the probe for the Groups pilot.
 *
 * <p>The row's acceptance is the proposal's pilot gate, applied to Groups:
 * "at least 70% of pilot cards expressible without the escape hatch, zero
 * UNIMPLEMENTED hits for pilot cards in fuzz runs, no regression in the
 * existing probes" (A3, "Pilot and migration"). This probe makes the parts
 * of that gate that can be measured, measured, and says plainly which part
 * cannot.</p>
 *
 * <p><b>What is measured here.</b></p>
 * <ul>
 *   <li><b>Coverage.</b> Every group title in the card pool has at least one
 *       effect entry, and the pilot document contains nothing that is not a
 *       group. The pilot gate's "zero UNIMPLEMENTED hits for pilot cards"
 *       is the tripwire's question - "is this behaviour-bearing card
 *       implemented?" - and at the data level that is the same question
 *       asked of the data instead of of the engine's behaviour registry.
 *       See "what is NOT measured" below.</li>
 *   <li><b>The bespoke tail.</b> The share of pilot cards that need the
 *       escape hatch, measured against the 70% threshold, and cross-checked
 *       against the effect census (B5-0005), which classified the same cards
 *       independently. The two must agree in both directions: a card the
 *       census calls bespoke must be a card the data marks bespoke, and a
 *       card the data marks bespoke must be one the census calls
 *       bespoke.</li>
 *   <li><b>Provenance.</b> Every entry's text hash matches the governing
 *       printed text, computed from the card files at probe time, and every
 *       entry cites at least one rule ID.</li>
 *   <li><b>The vocabulary.</b> The registry is still modular, and the
 *       trigger vocabulary names all four conflict types the rulebook names
 *       ({@code RB:IV/type-of-conflict}).</li>
 *   <li><b>The two halves agree.</b> The JSON Schema and the engine's closed
 *       sets are compared, set for set, in this probe. They are written in
 *       two languages and are the two independent halves of the schema
 *       contract, and nothing else checks that they still describe the same
 *       vocabulary.</li>
 * </ul>
 *
 * <p><b>What is NOT measured here, and why.</b> The pilot gate's third
 * clause - "zero UNIMPLEMENTED hits for pilot cards <em>in fuzz runs</em>" -
 * has two preconditions that do not exist yet: the engine does not execute
 * effect entries (the legacy strangler is not built), and there are no fuzz
 * runs in the gate. So the clause is <em>not</em> met in its literal form,
 * and this probe does not pretend otherwise: it measures the data-level
 * equivalent, and the existing tripwire's baseline - which counts the
 * engine's behaviour registry - is unchanged by this row, because the
 * engine's behaviour is unchanged. The row's report says the same thing in
 * prose.</p>
 *
 * <p><b>Why the golden values are written out by hand.</b> The same argument
 * the v0 probe makes: a golden value that cannot distinguish a correct
 * renderer from a broken one is not evidence. Both lines below are written
 * from the entry's own fields and the documented render format, not copied
 * from the renderer's output - so if the renderer and this file disagree,
 * the probe fails and prints the actual.</p>
 *
 * <p>Gate tier: pure 0/1/2 exit contract - 0 clean, 1 findings present,
 * 2 the harness could not build its fixtures (a FALSE PASS, never clean).</p>
 *
 * <p>Java 8 source level (javac --release 8), stdlib only.</p>
 */
public class HeadlessGroupEffectProbe {

    /** The Groups pilot document. */
    private static final String PILOT_RESOURCE = "/effects/effects-groups-v1.json";

    /** The v0 skeleton, which must still load. */
    private static final String V0_RESOURCE = "/effects/effects-v0.json";

    /** The card pool files, and the census, read as classpath resources. */
    private static final String PREMIERE = "/cards/premiere.json";
    private static final String DELUXE = "/cards/deluxe.json";
    private static final String CENSUS = "/census/effect-census.json";

    /**
     * The pilot gate's threshold: at least this share of the pilot cards
     * must be expressible without the escape hatch (A3: "at least 70% of
     * pilot cards expressible without the escape hatch").
     */
    private static final int MIN_WITHOUT_BESPOKE_PERCENT = 70;

    /**
     * The canonical rendering of a group entry whose fields are all
     * non-default: a conflict-type trigger, a faction-scoped target, a
     * conflict bonus and a three-ID rule list.
     *
     * <p>Written out from the entry's own fields and the documented render
     * format, not copied from the renderer's output.</p>
     */
    private static final String GOLDEN_RENDER =
        "group_secret_police \"Secret Police\" [TRIGGERED] window=CONFLICT "
        + "trigger=CONFLICT_TYPE_INTRIGUE target=FACTION/ctrl=SELF "
        + "ops=[CONFLICT_BONUS(stat=INTRIGUE, amount=3)] duration=INSTANT "
        + "stacking=ADDITIVE protection=NONE precedence=CARD_TEXT binding=NONE "
        + "rules=RB:IV/group-cards+RB:IV/type-of-conflict+RB:III/conflict-round";

    /**
     * A second golden value, for an entry with an optional operation and two
     * operations in order.
     *
     * <p>The "?" marker is the one the renderer puts on an operation the
     * printed text permits rather than requires - the rulebook's "may".
     * A renderer that dropped the marker would render a permitted choice as
     * a compulsory effect, and this golden would catch it.</p>
     */
    private static final String GOLDEN_RENDER_OPTIONAL =
        "group_observers \"Observers\" [TRIGGERED] window=RESOLUTION "
        + "trigger=CONFLICT_RESOLVED target=FACTION/ctrl=ANY "
        + "ops=[VIEW_HAND(); DISCARD(count=1)?] duration=INSTANT "
        + "stacking=ADDITIVE protection=NONE precedence=CARD_TEXT binding=NONE "
        + "rules=RB:IV/group-cards+RB:III/resolution-round+RB:VI/discards";

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable t) {
            System.out.println("HeadlessGroupEffectProbe: ERROR " + t);
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

        System.out.println("=== GROUP EFFECT PROBE (B5-0034 / WP-11) ===");

        // ── the registry ───────────────────────────────────────────────────
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

        // The rulebook names four conflict types (RB:IV/type-of-conflict).
        // A trigger vocabulary that cannot name one of them cannot express
        // the cards that use it, so the vocabulary is checked against the
        // rulebook rather than against itself.
        String[] conflictTypes = { "MILITARY", "DIPLOMACY", "INTRIGUE", "PSI" };
        Set<String> triggerNames = names(EffectTrigger.values());
        List<String> missingTypes = new ArrayList<String>();
        for (String t : conflictTypes) {
            if (!triggerNames.contains("CONFLICT_TYPE_" + t)) {
                missingTypes.add(t);
            }
        }
        check("the trigger vocabulary names all four rulebook conflict types",
                missingTypes.isEmpty(), "missing " + missingTypes);

        // ── the documents ──────────────────────────────────────────────────
        System.out.println("--- the pilot document ---");
        EffectDocument pilot = EffectLoader.loadFromResource(PILOT_RESOURCE, registry);
        check("the Groups pilot document loads without error", pilot != null, "");
        check("the pilot document's schema version is 1",
                pilot.getSchemaVersion() == 1, "version " + pilot.getSchemaVersion());

        EffectDocument v0 = EffectLoader.loadFromResource(V0_RESOURCE, registry);
        check("the v0 skeleton still loads without error", v0 != null, "");

        // ── the card pool ──────────────────────────────────────────────────
        Map<String, String> governingText = governingCardPool();
        check("the card pool was read (" + governingText.size() + " cards)",
                governingText.size() > 400, "cards " + governingText.size());

        Set<String> groupIds = groupCardIds();
        check("the card pool holds 26 group titles",
                groupIds.size() == 26, "group cards " + groupIds.size());

        // ── coverage: the pilot gate's "zero UNIMPLEMENTED hits" ──────────
        System.out.println("--- coverage ---");
        Set<String> covered = new HashSet<String>();
        for (EffectEntry entry : pilot.getEntries()) {
            covered.add(entry.getCardId());
        }
        List<String> uncovered = new ArrayList<String>();
        for (String id : groupIds) {
            if (!covered.contains(id)) uncovered.add(id);
        }
        check("every group title has at least one effect entry ("
                + (groupIds.size() - uncovered.size()) + "/" + groupIds.size() + ")",
                uncovered.isEmpty(), "uncovered " + uncovered);
        List<String> notGroups = new ArrayList<String>();
        for (String id : covered) {
            if (!groupIds.contains(id)) notGroups.add(id);
        }
        check("the pilot document describes group cards and nothing else",
                notGroups.isEmpty(), "not groups " + notGroups);
        check("the pilot document describes as many cards as the pool has group titles",
                covered.size() == groupIds.size(),
                "cards " + covered.size() + " vs " + groupIds.size());

        // ── provenance ─────────────────────────────────────────────────────
        System.out.println("--- provenance ---");
        int hashMismatch = 0;
        int noRuleId = 0;
        for (EffectEntry entry : pilot.getEntries()) {
            String text = governingText.get(entry.getCardId());
            String expected = text == null ? null : sha256(text);
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
        check("every entry's text hash matches the governing printed text",
                hashMismatch == 0, hashMismatch + " mismatched");
        check("every entry's provenance cites at least one rule ID",
                noRuleId == 0, noRuleId + " without a rule ID");

        // ── the bespoke tail ───────────────────────────────────────────────
        System.out.println("--- the bespoke tail ---");
        Set<String> bespokeCards = new HashSet<String>();
        int bespokeOps = 0;
        for (EffectEntry entry : pilot.getEntries()) {
            for (EffectOperation op : entry.getOperations()) {
                if ("BESPOKE".equals(op.getPrimitive())) {
                    bespokeOps++;
                    bespokeCards.add(entry.getCardId());
                }
            }
        }
        int withoutBespoke = groupIds.size() - bespokeCards.size();
        int percent = groupIds.isEmpty() ? 0
                : (withoutBespoke * 100) / groupIds.size();
        System.out.println("  pilot cards: " + groupIds.size());
        System.out.println("  expressible without the escape hatch: " + withoutBespoke
                + " (" + percent + "%)");
        System.out.println("  bespoke cards: " + bespokeCards.size() + " " + bespokeCards);
        System.out.println("  bespoke operations: " + bespokeOps);
        check("at least " + MIN_WITHOUT_BESPOKE_PERCENT
                + "% of the pilot cards are expressible without the escape hatch ("
                + percent + "%)", percent >= MIN_WITHOUT_BESPOKE_PERCENT, "percent " + percent);
        check("the escape hatch is used by at most one pilot card",
                bespokeCards.size() <= 1, "bespoke cards " + bespokeCards.size());
        check("the registry publishes the bespoke handler the data names",
                registry.hasBespokeHandler(SpinDoctorsPerceivedWinner.HANDLER_KEY),
                "published " + registry.knownBespokeHandlerKeys());

        // The census is an independent classification of the same cards. If
        // the data and the census disagree about which card is bespoke, one
        // of them is wrong, and that is worth a red gate.
        Map<String, String> censusClasses = censusClasses();
        check("the census was read (" + censusClasses.size() + " group records)",
                !censusClasses.isEmpty(), "records " + censusClasses.size());
        Set<String> censusBespoke = new HashSet<String>();
        for (Map.Entry<String, String> e : censusClasses.entrySet()) {
            if ("BESPOKE".equals(e.getValue())) censusBespoke.add(e.getKey());
        }
        check("the census and the card pool agree on the number of group titles",
                censusClasses.size() == groupIds.size(),
                "census " + censusClasses.size() + " vs pool " + groupIds.size());
        check("the cards the data marks bespoke are exactly the cards the census "
                + "classifies as bespoke",
                bespokeCards.equals(censusBespoke),
                "data " + bespokeCards + " vs census " + censusBespoke);

        // ── the bespoke rule ───────────────────────────────────────────────
        System.out.println("--- the bespoke rule ---");
        check("the group's controller is the perceived winner of the conflict",
                "minbari".equals(SpinDoctorsPerceivedWinner.perceivedWinner("minbari", "centauri")),
                "perceived winner "
                        + SpinDoctorsPerceivedWinner.perceivedWinner("minbari", "centauri"));
        check("the real winner gains nothing when the controller is the loser",
                !SpinDoctorsPerceivedWinner.actualWinnerGains("minbari", "centauri"), "");
        check("the real winner still gains when the controller is the winner",
                SpinDoctorsPerceivedWinner.actualWinnerGains("minbari", "minbari"), "");

        // ── the conflict-type scope (B5-0052) ─────────────────────────────
        // Three group cards print "if you win" and one prints "a Diplomacy
        // conflict". The v0 trigger vocabulary could say the outcome
        // (CONFLICT_WON) or the conflict class (CONFLICT_TYPE_*) but not
        // both, and the pilot wrote the three riders against the bare
        // outcome - a broader rule than the card prints. The conjunction is
        // now expressible (a conflict-class trigger plus a
        // CONTROLLER_WON_CONFLICT condition), and the checks below are what
        // keep it that way.
        System.out.println("--- the conflict-type scope ---");
        List<String> bareWon = new ArrayList<String>();
        for (EffectEntry entry : pilot.getEntries()) {
            if ("CONFLICT_WON".equals(entry.getTrigger())) {
                bareWon.add(entry.getCardId());
            }
        }
        check("no pilot entry uses the bare CONFLICT_WON trigger", bareWon.isEmpty(),
                "bare " + bareWon);

        String[][] wonRiders = {
            { "group_diplomatic_corps", "CONFLICT_TYPE_DIPLOMACY" },
            { "group_secret_police", "CONFLICT_TYPE_INTRIGUE" },
            { "group_imperial_telepaths", "CONFLICT_TYPE_PSI" }
        };
        for (String[] rider : wonRiders) {
            EffectEntry entry = null;
            for (EffectEntry candidate : pilot.entriesFor(rider[0])) {
                if ("RESOLUTION".equals(candidate.getWindow().name())) entry = candidate;
            }
            boolean ok = entry != null
                    && rider[1].equals(entry.getTrigger() == null ? "" : entry.getTrigger().name())
                    && hasCondition(entry, "CONTROLLER_WON_CONFLICT");
            check("the \"if you win\" rider of " + rider[0]
                    + " names its conflict class (" + rider[1] + ") and the outcome filter",
                    ok, entry == null ? "(no such entry)"
                            : ("trigger=" + entry.getTrigger() + " conditions=" + conditionsOf(entry)));
        }

        EffectEntry spin = firstEntryFor(pilot, "group_spin_doctors");
        check("\"Spin Doctors\" is scoped to a Diplomacy conflict",
                spin != null && spin.getTrigger() != null
                        && "CONFLICT_TYPE_DIPLOMACY".equals(spin.getTrigger().name()),
                spin == null ? "(no such entry)" : ("trigger=" + spin.getTrigger()));

        // ── the loader refuses a kind the primitive does not allow ────────
        // EffectPrimitive.kinds() declares the effect kinds a primitive may
        // appear under, and that declaration used to fail OPEN: nothing read
        // it, and one shipped operation (STAT_MODIFY under a ONE_SHOT entry)
        // violated it without the loader noticing.
        System.out.println("--- the kind constraint fails closed ---");
        expectLoadFailure("the loader refuses an operation whose primitive does not "
                + "allow the entry's kind",
                "{\"schema_version\":1,\"entries\":[{"
                + "\"cardId\":\"probe_card\",\"title\":\"Probe Card\",\"kind\":\"TRAIT\","
                + "\"window\":\"ANY\",\"requirements\":[],"
                + "\"target\":{\"scope\":\"CARD\",\"controllerRelation\":\"ANY\"},"
                + "\"conditions\":[],\"operations\":[{\"primitive\":\"DRAW\",\"params\":{\"count\":1}}],"
                + "\"duration\":\"INSTANT\",\"stacking\":\"ADDITIVE\",\"protection\":\"NONE\","
                + "\"precedenceLayer\":\"CARD_TEXT\",\"sourceBinding\":\"NONE\","
                + "\"provenance\":{\"ruleIds\":[\"RB:I/influence\"],\"printingScope\":\"SINGLE_PRINTING\"}"
                + "}]}",
                "does not allow effect kind");

        // ── the renderer ───────────────────────────────────────────────────
        System.out.println("--- the renderer ---");
        String rendered = renderer.render(pilot);
        check("the whole pilot document renders",
                rendered != null && rendered.length() > 0, "");
        check("the render is stable across two runs",
                rendered.equals(renderer.render(pilot)), "the render is not deterministic");
        check("the render has one line per entry",
                countLines(rendered) == pilot.size() + 1,
                "lines " + countLines(rendered));

        EffectEntry golden = firstEntryFor(pilot, "group_secret_police");
        check("a known group entry renders to its golden line",
                golden != null && GOLDEN_RENDER.equals(renderer.render(golden)),
                "got: " + (golden == null ? "(no such entry)" : renderer.render(golden)));

        EffectEntry optional = firstEntryFor(pilot, "group_observers");
        check("an entry with an optional operation renders to its second golden line",
                optional != null && GOLDEN_RENDER_OPTIONAL.equals(renderer.render(optional)),
                "got: " + (optional == null ? "(no such entry)" : renderer.render(optional)));

        // ── the two halves of the schema contract agree ────────────────────
        System.out.println("--- the schema and the engine agree ---");
        Map<String, Object> schema = readSchema();
        check("the JSON Schema was read", schema != null, "");
        if (schema != null) {
            checkDefEnum(schema, "effectKind", names(EffectKind.values()));
            checkDefEnum(schema, "effectWindow", names(EffectWindow.values()));
            checkDefEnum(schema, "effectTrigger", names(EffectTrigger.values()));
            checkDefEnum(schema, "effectDuration", names(EffectDuration.values()));
            checkDefEnum(schema, "effectStacking", names(EffectStacking.values()));
            checkDefEnum(schema, "effectProtection", names(EffectProtection.values()));
            checkDefEnum(schema, "effectPrecedence", names(EffectPrecedence.values()));
            checkDefEnum(schema, "effectSourceBinding", names(EffectSourceBinding.values()));
            checkPropertyEnum(schema, "requirement", "type", names(EffectRequirementType.values()));
            checkPropertyEnum(schema, "condition", "type", names(EffectConditionType.values()));
            checkItemsEnum(schema, "target", "cardTypes", names(CardType.values()));
            checkItemsEnum(schema, "target", "races", names(Race.values()));
            checkItemsEnum(schema, "target", "status", names(EffectCardStatus.values()));
            checkItemsEnum(schema, "target", "traits", names(EffectTrait.values()));
            check("the schema allows the operation's optional flag",
                    schemaHasProperty(schema, "operation", "optional"), "");
        }
    }

    // ── the enum name sets ────────────────────────────────────────────────

    private static Set<String> names(Enum<?>[] values) {
        Set<String> out = new TreeSet<String>();
        for (Enum<?> v : values) out.add(v.name());
        return out;
    }

    // ── the card pool ───────────────────────────────────────────────────────

    /** A card in the pool: its canonical id, its type and its governing text. */
    private static final class PoolCard {
        String id;
        String type;
        String title;
        String text;
    }

    private static List<PoolCard> poolCards() throws Exception {
        List<PoolCard> out = new ArrayList<PoolCard>();
        readPoolCards(PREMIERE, out);
        // The Deluxe printing is read second, so it overwrites the Premiere
        // twin: the Deluxe text governs a dual title (D5).
        readPoolCards(DELUXE, out);
        return out;
    }

    private static void readPoolCards(String path, List<PoolCard> out) throws Exception {
        String json = readResource(path);
        if (json == null) throw new java.io.FileNotFoundException("not found: " + path);
        List<Object> cards = MiniJson.parseCardArray(json);
        for (int i = 0; i < cards.size(); i++) {
            Object o = cards.get(i);
            if (!(o instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> card = (Map<String, Object>) o;
            Object id = card.get("id");
            if (!(id instanceof String)) continue;
            PoolCard pc = new PoolCard();
            pc.id = CardIdNamespaceGuard.canonicalKey((String) id);
            Object type = card.get("type");
            pc.type = type instanceof String ? (String) type : "";
            Object title = card.get("title");
            pc.title = title instanceof String ? (String) title : "";
            Object text = card.get("text");
            pc.text = text instanceof String ? (String) text : "";
            out.add(pc);
        }
    }

    /** The governing text of every card, keyed by canonical id. */
    private static Map<String, String> governingCardPool() throws Exception {
        Map<String, String> pool = new HashMap<String, String>();
        for (PoolCard c : poolCards()) {
            pool.put(c.id, c.text);
        }
        return pool;
    }

    /** Every card's title, keyed by canonical id. */
    private static Map<String, String> cardTitles() throws Exception {
        Map<String, String> titles = new HashMap<String, String>();
        for (PoolCard c : poolCards()) {
            titles.put(c.id, c.title);
        }
        return titles;
    }

    /** The canonical ids of the group cards. */
    private static Set<String> groupCardIds() throws Exception {
        Set<String> ids = new TreeSet<String>();
        for (PoolCard c : poolCards()) {
            if ("GROUP".equals(c.type)) ids.add(c.id);
        }
        return ids;
    }

    // ── the census ─────────────────────────────────────────────────────────

    /** The census's class for each group card, keyed by canonical id. */
    private static Map<String, String> censusClasses() throws Exception {
        Map<String, String> out = new HashMap<String, String>();
        String json = readResource(CENSUS);
        if (json == null) return out;
        Object root = MiniJson.parseValue(json);
        if (!(root instanceof Map)) return out;
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) root;
        Object recordsRaw = map.get("records");
        if (!(recordsRaw instanceof List)) return out;
        List<?> records = (List<?>) recordsRaw;
        Map<String, String> idToTitle = cardTitles();
        Map<String, String> titleToId = new HashMap<String, String>();
        for (Map.Entry<String, String> e : idToTitle.entrySet()) {
            titleToId.put(e.getValue(), e.getKey());
        }
        for (int i = 0; i < records.size(); i++) {
            Object o = records.get(i);
            if (!(o instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> rec = (Map<String, Object>) o;
            Object type = rec.get("type");
            if (!"GROUP".equals(type)) continue;
            Object title = rec.get("title");
            if (!(title instanceof String)) continue;
            String id = titleToId.get(title);
            if (id == null) continue;
            Object cls = rec.get("class");
            out.put(id, cls instanceof String ? (String) cls : "");
        }
        return out;
    }

    // ── the JSON Schema ────────────────────────────────────────────────────

    /**
     * Reads the JSON Schema from the repository.
     *
     * <p>It is a dev-tooling file, not a shipped resource, so it is not on
     * the classpath. The gate runs the probe from the {@code b5ccg}
     * directory, so the relative path is the repository's {@code .agent}
     * directory one level up; the repository-root spelling is tried as well.
     * If neither exists the probe cannot check the half of the contract that
     * only this file can, and the caller exits 2 - a harness failure, never a
     * clean pass.</p>
     */
    private static Map<String, Object> readSchema() throws Exception {
        String[] candidates = {
            "../.agent/tools/schemas/effect-document.schema.json",
            ".agent/tools/schemas/effect-document.schema.json"
        };
        for (String path : candidates) {
            File f = new File(path);
            if (!f.isFile()) continue;
            InputStream is = new FileInputStream(f);
            try {
                java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int n;
                while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
                Object root = MiniJson.parseValue(new String(buf.toByteArray(), "UTF-8"));
                if (root instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) root;
                    return map;
                }
            } finally {
                try { is.close(); } catch (java.io.IOException e) { }
            }
        }
        throw new java.io.FileNotFoundException(
                "the JSON Schema was not found at any of " + Arrays.toString(candidates));
    }

    /** The enum list a schema node declares. */
    private static List<String> enumOf(Object node) {
        if (!(node instanceof Map)) return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) node;
        Object enums = map.get("enum");
        if (!(enums instanceof List)) return null;
        List<?> list = (List<?>) enums;
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            if (o instanceof String) out.add((String) o);
        }
        return out;
    }

    /** The schema node at a dotted path under {@code $defs}. */
    private static Object nodeAt(Map<String, Object> schema, String path) {
        Object current = schema;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) current;
            current = map.get(part);
        }
        return current;
    }

    /** Whether a schema definition declares a property. */
    private static boolean schemaHasProperty(Map<String, Object> schema, String def,
                                              String property) {
        Object props = nodeAt(schema, "$defs." + def + ".properties");
        if (!(props instanceof Map)) return false;
        @SuppressWarnings("unchecked")
        Map<String, Object> pmap = (Map<String, Object>) props;
        return pmap.containsKey(property);
    }

    /**
     * Compares one closed set: the schema's enum list against the engine's.
     *
     * <p>The two halves of the schema contract are written in two languages
     * and share no code, which is what makes them independent. It also
     * means nothing but this check notices when one of them gains a value
     * the other does not have - and a document the tooling accepts and the
     * engine refuses is a real failure, in whichever direction it happens.</p>
     */
    private static void compare(String what, List<String> declared, Set<String> engineValues) {
        if (declared == null) {
            check(what, false, "the schema does not declare this list");
            return;
        }
        Set<String> schemaValues = new TreeSet<String>(declared);
        Set<String> onlyEngine = new TreeSet<String>(engineValues);
        onlyEngine.removeAll(schemaValues);
        Set<String> onlySchema = new TreeSet<String>(schemaValues);
        onlySchema.removeAll(engineValues);
        check(what, onlyEngine.isEmpty() && onlySchema.isEmpty(),
                "engine only " + onlyEngine + ", schema only " + onlySchema);
    }

    /** Compares a definition's own enum list. */
    private static void checkDefEnum(Map<String, Object> schema, String def,
                                     Set<String> engineValues) {
        compare("the schema and the engine agree on " + def,
                enumOf(nodeAt(schema, "$defs." + def)), engineValues);
    }

    /** Compares a property's enum list. */
    private static void checkPropertyEnum(Map<String, Object> schema, String def,
                                          String property, Set<String> engineValues) {
        compare("the schema and the engine agree on " + def + "." + property,
                enumOf(nodeAt(schema, "$defs." + def + ".properties." + property)),
                engineValues);
    }

    /** Compares an array property's item enum list. */
    private static void checkItemsEnum(Map<String, Object> schema, String def,
                                        String property, Set<String> engineValues) {
        compare("the schema and the engine agree on " + def + "." + property,
                enumOf(nodeAt(schema, "$defs." + def + ".properties." + property + ".items")),
                engineValues);
    }

    // ── small helpers ──────────────────────────────────────────────────────

    private static EffectEntry firstEntryFor(EffectDocument doc, String cardId) {
        List<EffectEntry> entries = doc.entriesFor(cardId);
        return entries.isEmpty() ? null : entries.get(0);
    }

    /** Whether an entry carries a condition of the named type. */
    private static boolean hasCondition(EffectEntry entry, String type) {
        for (EffectCondition c : entry.getConditions()) {
            if (type.equals(c.getType().name())) return true;
        }
        return false;
    }

    /** An entry's condition types, comma-separated, for a failure message. */
    private static String conditionsOf(EffectEntry entry) {
        StringBuilder sb = new StringBuilder();
        for (EffectCondition c : entry.getConditions()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(c.getType().name());
        }
        return sb.length() == 0 ? "(none)" : sb.toString();
    }

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

    private static String readResource(String path) throws Exception {
        java.net.URL url = HeadlessGroupEffectProbe.class.getResource(path);
        if (url == null) return null;
        InputStream is = url.openStream();
        try {
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
            return new String(buf.toByteArray(), "UTF-8");
        } finally {
            try { is.close(); } catch (java.io.IOException e) { }
        }
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
