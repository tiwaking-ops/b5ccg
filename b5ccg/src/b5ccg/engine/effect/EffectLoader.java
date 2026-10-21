package b5ccg.engine.effect;

import b5ccg.engine.MiniJson;
import b5ccg.model.enums.CardType;
import b5ccg.model.enums.Race;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WP-10 / B5-0028 (A3): the effect-data loader.
 *
 * <p><b>This is where A3 principle 4 lives.</b> "Fail closed. An unknown
 * effect kind is a load error." The loader is the only way an effect document
 * becomes an {@link EffectDocument}, and it refuses a document it does not
 * fully understand. It refuses, specifically:</p>
 * <ul>
 *   <li>an effect kind outside {@link EffectKind};</li>
 *   <li>an operation naming a primitive the registry does not hold;</li>
 *   <li>an operation whose parameters are not one of the closed set its
 *       primitive accepts;</li>
 *   <li>a requirement, condition, window, duration, stacking rule, scope,
 *       controller relation, card type, race, status or trait outside its
 *       closed set;</li>
 *   <li>a missing required field;</li>
 *   <li>a field the schema does not define - an unknown key is an error, not
 *       a comment, because a typo in a key name would otherwise silently
 *       disable the effect it was meant to name;</li>
 *   <li>a document whose {@code schema_version} this build does not
 *       understand.</li>
 * </ul>
 *
 * <p><b>Every refusal names where it happened.</b> The message reads
 * "entries[3].operations[0].primitive: unknown effect primitive 'FOO'; the
 * registry holds 20 primitive(s): ..." - a data author can act on that
 * without reading the source.</p>
 *
 * <p><b>Nothing is guessed.</b> A missing field is an error, not a default.
 * The one exception is a field whose absence is a meaningful statement rather
 * than an omission - an absent {@code requirements} list means "no cost
 * block", which is what an empty list would also mean, so absence and
 * emptiness are the same thing and are recorded as the same thing.</p>
 */
public final class EffectLoader {

    /** The keys an effect document may carry. */
    private static final String[] DOCUMENT_KEYS = { "schema_version", "entries" };

    /** The keys an effect entry may carry. */
    private static final String[] ENTRY_KEYS = {
        "cardId", "title", "kind", "window", "trigger", "requirements", "target",
        "conditions", "operations", "duration", "stacking", "protection",
        "precedenceLayer", "sourceBinding", "provenance"
    };

    /** The keys a target may carry. */
    private static final String[] TARGET_KEYS = {
        "scope", "controllerRelation", "cardTypes", "races", "status", "traits",
        "innerCircleOnly", "supportingOnly", "ambassadorOnly"
    };

    /** The keys an operation may carry. */
    private static final String[] OPERATION_KEYS = { "primitive", "params", "optional" };

    /** The keys a requirement may carry. */
    private static final String[] REQUIREMENT_KEYS = { "type", "amount" };

    /** The keys a condition may carry. */
    private static final String[] CONDITION_KEYS = { "type", "amount", "subject" };

    /** The keys a provenance block may carry. */
    private static final String[] PROVENANCE_KEYS = { "ruleIds", "textSha256", "printingScope" };

    private EffectLoader() { }

    /**
     * Loads an effect document from a classpath resource.
     *
     * @param path the resource path, e.g. {@code /effects/effects-v0.json}
     * @param registry the registry the document's primitives are resolved
     *     against
     */
    public static EffectDocument loadFromResource(String path, EffectRegistry registry)
            throws IOException, EffectLoadException {
        URL url = EffectLoader.class.getResource(path);
        if (url == null) throw new FileNotFoundException("Effect data not found: " + path);
        InputStream is = null;
        try {
            is = url.openStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = is.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
            }
            String json = new String(buf.toByteArray(), StandardCharsets.UTF_8);
            return parse(json, registry);
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException e) { }
            }
        }
    }

    /**
     * Parses an effect document from text.
     *
     * @throws IOException when the text is not well-formed JSON
     * @throws EffectLoadException when the document is well-formed JSON but
     *     says something the engine will not guess at
     */
    public static EffectDocument parse(String json, EffectRegistry registry)
            throws IOException, EffectLoadException {
        if (registry == null) {
            throw new EffectLoadException("document", "no registry supplied");
        }
        Object root = MiniJson.parseValue(json);
        if (!(root instanceof Map)) {
            throw new EffectLoadException("document", "expected a JSON object at the top level");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = (Map<String, Object>) root;
        return parseDocument(raw, registry);
    }

    /**
     * Builds a document from an already-parsed object.
     */
    public static EffectDocument parseDocument(Map<String, Object> raw, EffectRegistry registry)
            throws EffectLoadException {
        rejectUnknownKeys(raw, DOCUMENT_KEYS, "document");

        Object versionRaw = raw.get("schema_version");
        if (versionRaw == null) {
            throw new EffectLoadException("document", "missing 'schema_version'");
        }
        int version = requireInt(versionRaw, "document.schema_version");
        if (version != EffectDocument.SUPPORTED_SCHEMA_VERSION) {
            throw new EffectLoadException("document.schema_version",
                    "schema version " + version + " is not supported; this build loads version "
                    + EffectDocument.SUPPORTED_SCHEMA_VERSION);
        }

        Object entriesRaw = raw.get("entries");
        if (entriesRaw == null) {
            throw new EffectLoadException("document", "missing 'entries'");
        }
        if (!(entriesRaw instanceof List)) {
            throw new EffectLoadException("document.entries", "expected an array");
        }
        List<?> entryList = (List<?>) entriesRaw;
        List<EffectEntry> entries = new ArrayList<EffectEntry>();
        for (int i = 0; i < entryList.size(); i++) {
            String location = "entries[" + i + "]";
            Object e = entryList.get(i);
            if (!(e instanceof Map)) {
                throw new EffectLoadException(location, "expected an object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> entryRaw = (Map<String, Object>) e;
            entries.add(parseEntry(entryRaw, registry, location));
        }
        return new EffectDocument(version, entries);
    }

    /**
     * Builds one entry from an already-parsed object.
     */
    public static EffectEntry parseEntry(Map<String, Object> raw, EffectRegistry registry,
                                         String location) throws EffectLoadException {
        rejectUnknownKeys(raw, ENTRY_KEYS, location);

        String cardId = requireString(raw.get("cardId"), location + ".cardId");
        String title = requireString(raw.get("title"), location + ".title");
        EffectKind kind = EffectKind.parse(requireString(raw.get("kind"), location + ".kind"),
                                           location + ".kind");
        EffectWindow window = EffectWindow.parse(
                requireString(raw.get("window"), location + ".window"), location + ".window");
        EffectDuration duration = EffectDuration.parse(
                requireString(raw.get("duration"), location + ".duration"), location + ".duration");
        EffectStacking stacking = EffectStacking.parse(
                requireString(raw.get("stacking"), location + ".stacking"), location + ".stacking");
        EffectProtection protection = EffectProtection.parse(
                requireString(raw.get("protection"), location + ".protection"),
                location + ".protection");

        EffectTrigger triggerValue = null;
        if (raw.get("trigger") != null) {
            triggerValue = EffectTrigger.parse(
                    requireString(raw.get("trigger"), location + ".trigger"), location + ".trigger");
        }
        EffectPrecedence precedence = EffectPrecedence.parse(
                requireString(raw.get("precedenceLayer"), location + ".precedenceLayer"),
                location + ".precedenceLayer");
        EffectSourceBinding binding = EffectSourceBinding.parse(
                requireString(raw.get("sourceBinding"), location + ".sourceBinding"),
                location + ".sourceBinding");

        Object targetRaw = raw.get("target");
        if (targetRaw == null) {
            throw new EffectLoadException(location, "missing 'target'");
        }
        if (!(targetRaw instanceof Map)) {
            throw new EffectLoadException(location + ".target", "expected an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> targetMap = (Map<String, Object>) targetRaw;
        EffectTarget target = parseTarget(targetMap, location + ".target");

        List<EffectRequirement> requirements = new ArrayList<EffectRequirement>();
        Object reqRaw = raw.get("requirements");
        if (reqRaw != null) {
            if (!(reqRaw instanceof List)) {
                throw new EffectLoadException(location + ".requirements", "expected an array");
            }
            List<?> reqList = (List<?>) reqRaw;
            for (int i = 0; i < reqList.size(); i++) {
                String rl = location + ".requirements[" + i + "]";
                Object r = reqList.get(i);
                if (!(r instanceof Map)) {
                    throw new EffectLoadException(rl, "expected an object");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> rMap = (Map<String, Object>) r;
                rejectUnknownKeys(rMap, REQUIREMENT_KEYS, rl);
                EffectRequirementType t = EffectRequirementType.parse(
                        requireString(rMap.get("type"), rl + ".type"), rl + ".type");
                int amount = requireInt(rMap.get("amount"), rl + ".amount");
                requirements.add(new EffectRequirement(t, amount));
            }
        }

        List<EffectCondition> conditions = new ArrayList<EffectCondition>();
        Object condRaw = raw.get("conditions");
        if (condRaw != null) {
            if (!(condRaw instanceof List)) {
                throw new EffectLoadException(location + ".conditions", "expected an array");
            }
            List<?> condList = (List<?>) condRaw;
            for (int i = 0; i < condList.size(); i++) {
                String cl = location + ".conditions[" + i + "]";
                Object c = condList.get(i);
                if (!(c instanceof Map)) {
                    throw new EffectLoadException(cl, "expected an object");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> cMap = (Map<String, Object>) c;
                rejectUnknownKeys(cMap, CONDITION_KEYS, cl);
                EffectConditionType t = EffectConditionType.parse(
                        requireString(cMap.get("type"), cl + ".type"), cl + ".type");
                int amount = 0;
                if (cMap.get("amount") != null) {
                    amount = requireInt(cMap.get("amount"), cl + ".amount");
                }
                String subject = optionalString(cMap.get("subject"));
                conditions.add(new EffectCondition(t, amount, subject));
            }
        }

        Object opsRaw = raw.get("operations");
        if (opsRaw == null) {
            throw new EffectLoadException(location, "missing 'operations'");
        }
        if (!(opsRaw instanceof List)) {
            throw new EffectLoadException(location + ".operations", "expected an array");
        }
        List<?> opsList = (List<?>) opsRaw;
        List<EffectOperation> operations = new ArrayList<EffectOperation>();
        for (int i = 0; i < opsList.size(); i++) {
            String ol = location + ".operations[" + i + "]";
            Object o = opsList.get(i);
            if (!(o instanceof Map)) {
                throw new EffectLoadException(ol, "expected an object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> oMap = (Map<String, Object>) o;
            rejectUnknownKeys(oMap, OPERATION_KEYS, ol);
            String primitive = requireString(oMap.get("primitive"), ol + ".primitive");
            Map<String, String> params = new LinkedHashMap<String, String>();
            Object paramsRaw = oMap.get("params");
            if (paramsRaw != null) {
                if (!(paramsRaw instanceof Map)) {
                    throw new EffectLoadException(ol + ".params", "expected an object");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> pMap = (Map<String, Object>) paramsRaw;
                for (Map.Entry<String, Object> e : pMap.entrySet()) {
                    Object v = e.getValue();
                    if (v == null) continue;
                    if (!(v instanceof String) && !(v instanceof Integer)) {
                        throw new EffectLoadException(ol + ".params." + e.getKey(),
                                "a parameter must be a string or an integer");
                    }
                    params.put(e.getKey(), String.valueOf(v));
                }
            }
            EffectOperation op = new EffectOperation(primitive, params,
                    optionalBoolean(oMap.get("optional"), ol + ".optional"));
            // Resolve against the registry NOW: an unknown primitive, or a
            // parameter outside the primitive's closed set, is a load error.
            registry.describe(op, ol);
            operations.add(op);
        }

        Object provRaw = raw.get("provenance");
        if (provRaw == null) {
            throw new EffectLoadException(location, "missing 'provenance'");
        }
        if (!(provRaw instanceof Map)) {
            throw new EffectLoadException(location + ".provenance", "expected an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> provMap = (Map<String, Object>) provRaw;
        rejectUnknownKeys(provMap, PROVENANCE_KEYS, location + ".provenance");
        List<String> ruleIds = new ArrayList<String>();
        Object idsRaw = provMap.get("ruleIds");
        if (idsRaw == null) {
            throw new EffectLoadException(location + ".provenance", "missing 'ruleIds'");
        }
        if (!(idsRaw instanceof List)) {
            throw new EffectLoadException(location + ".provenance.ruleIds", "expected an array");
        }
        List<?> idsList = (List<?>) idsRaw;
        for (int i = 0; i < idsList.size(); i++) {
            Object id = idsList.get(i);
            if (!(id instanceof String)) {
                throw new EffectLoadException(location + ".provenance.ruleIds[" + i + "]",
                        "expected a string");
            }
            ruleIds.add((String) id);
        }
        String textSha = optionalString(provMap.get("textSha256"));
        String printing = requireString(provMap.get("printingScope"),
                                        location + ".provenance.printingScope");
        EffectProvenance provenance = new EffectProvenance(ruleIds, textSha, printing);

        return new EffectEntry(cardId, title, kind, window, triggerValue, requirements, target,
                               conditions, operations, duration, stacking, protection,
                               precedence, binding, provenance);
    }

    private static EffectTarget parseTarget(Map<String, Object> raw, String location)
            throws EffectLoadException {
        rejectUnknownKeys(raw, TARGET_KEYS, location);
        EffectScope scope = EffectScope.parse(
                requireString(raw.get("scope"), location + ".scope"), location + ".scope");
        EffectControllerRelation relation = EffectControllerRelation.parse(
                requireString(raw.get("controllerRelation"), location + ".controllerRelation"),
                location + ".controllerRelation");

        Set<CardType> cardTypes = new LinkedHashSet<CardType>();
        for (String s : stringList(raw.get("cardTypes"), location + ".cardTypes")) {
            cardTypes.add(parseCardType(s, location + ".cardTypes"));
        }
        Set<Race> races = new LinkedHashSet<Race>();
        for (String s : stringList(raw.get("races"), location + ".races")) {
            races.add(parseRace(s, location + ".races"));
        }
        Set<EffectCardStatus> status = new LinkedHashSet<EffectCardStatus>();
        for (String s : stringList(raw.get("status"), location + ".status")) {
            status.add(EffectCardStatus.parse(s, location + ".status"));
        }
        Set<EffectTrait> traits = new LinkedHashSet<EffectTrait>();
        for (String s : stringList(raw.get("traits"), location + ".traits")) {
            traits.add(EffectTrait.parse(s, location + ".traits"));
        }
        boolean innerCircle = optionalBoolean(raw.get("innerCircleOnly"), location + ".innerCircleOnly");
        boolean supporting = optionalBoolean(raw.get("supportingOnly"), location + ".supportingOnly");
        boolean ambassador = optionalBoolean(raw.get("ambassadorOnly"), location + ".ambassadorOnly");

        return new EffectTarget(scope, relation, cardTypes, races, status, traits,
                                innerCircle, supporting, ambassador);
    }

    private static CardType parseCardType(String raw, String location) throws EffectLoadException {
        try {
            return CardType.valueOf(raw.trim());
        } catch (IllegalArgumentException iae) {
            throw new EffectLoadException(location,
                    "unknown card type '" + raw + "'; known types are " + cardTypeNames());
        }
    }

    private static Race parseRace(String raw, String location) throws EffectLoadException {
        try {
            return Race.valueOf(raw.trim());
        } catch (IllegalArgumentException iae) {
            throw new EffectLoadException(location,
                    "unknown race '" + raw + "'; known races are " + raceNames());
        }
    }

    private static String cardTypeNames() {
        StringBuilder sb = new StringBuilder();
        for (CardType t : CardType.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.name());
        }
        return sb.toString();
    }

    private static String raceNames() {
        StringBuilder sb = new StringBuilder();
        for (Race r : Race.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(r.name());
        }
        return sb.toString();
    }

    // ── raw-value helpers ───────────────────────────────────────────────────

    private static void rejectUnknownKeys(Map<String, Object> raw, String[] allowed, String location)
            throws EffectLoadException {
        List<String> allowedSet = Arrays.asList(allowed);
        for (String key : raw.keySet()) {
            if (!allowedSet.contains(key)) {
                throw new EffectLoadException(location,
                        "unknown field '" + key + "'; this schema defines " + allowedSet);
            }
        }
    }

    private static String requireString(Object value, String location) throws EffectLoadException {
        if (value == null) {
            throw new EffectLoadException(location, "missing required field");
        }
        if (!(value instanceof String)) {
            throw new EffectLoadException(location, "expected a string");
        }
        String s = (String) value;
        if (s.trim().length() == 0) {
            throw new EffectLoadException(location, "must not be empty");
        }
        return s.trim();
    }

    private static String optionalString(Object value) {
        if (value == null) return null;
        if (!(value instanceof String)) return null;
        String s = (String) value;
        return s.trim().length() == 0 ? null : s.trim();
    }

    private static int requireInt(Object value, String location) throws EffectLoadException {
        if (value == null) {
            throw new EffectLoadException(location, "missing required field");
        }
        if (value instanceof Integer) {
            return ((Integer) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException nfe) {
                throw new EffectLoadException(location, "expected an integer");
            }
        }
        throw new EffectLoadException(location, "expected an integer");
    }

    private static boolean optionalBoolean(Object value, String location) throws EffectLoadException {
        if (value == null) return false;
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        if (value instanceof String) {
            String s = ((String) value).trim();
            if ("true".equalsIgnoreCase(s)) return true;
            if ("false".equalsIgnoreCase(s)) return false;
        }
        throw new EffectLoadException(location, "expected a boolean");
    }

    private static List<String> stringList(Object value, String location) throws EffectLoadException {
        List<String> out = new ArrayList<String>();
        if (value == null) return out;
        if (!(value instanceof List)) {
            throw new EffectLoadException(location, "expected an array");
        }
        List<?> list = (List<?>) value;
        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            if (!(o instanceof String)) {
                throw new EffectLoadException(location + "[" + i + "]", "expected a string");
            }
            out.add(((String) o).trim());
        }
        return out;
    }
}
