package b5ccg.ui;

import b5ccg.model.enums.CardType;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.MarkType;
import b5ccg.model.enums.Rarity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B5-4107: Card Editor model — logic-only, no Swing dependencies.
 *
 * <p>Headless test-safe. Handles JSON serialize/parse/validate for a single
 * card record. The model's JSON output uses the flat-object shape that
 * {@code DeckLoader.splitObjects} expects (one JSON object per card), so a
 * saved card is loadable by {@code DeckLoader.parseCards} today (modulo the
 * {@code flavor_text} unknown-key warning tracked as B5-4110).
 *
 * <p>Java 6 only: explicit type arguments, no diamond, no lambdas, index loops.
 */
public final class CardEditorModel {

    // ── Common fields (rulebook Anatomy §1–3, §5, §7) ─────────────────────────

    private String id;
    private String title;                 // §1: Name
    private CardType type;                // §3: Card Type
    private String subtype;
    private Rarity rarity;
    private Faction faction;
    private CardSet cardSet;              // JSON key: "set"
    private String imageKey;
    private String text;                  // §5: Effects (card effect text)
    private String flavorText;            // §7: Caption (card flavor text)
    private int cost;                     // §2: Influence Cost
    private boolean mercenary;

    // ── CHARACTER-specific stats (rulebook §4: Abilities) ────────────────────

    private int diplomacy;
    private int intrigue;
    private int psi;
    private int leadership;
    private boolean isAmbassador;

    // ── Printed marks (rulebook §6: Marks) ───────────────────────────────────
    // Per-MarkType initial-mark counts for this card, keyed by MarkType ordinal.
    // Any number of marks in any combination is legal on a card; the only
    // rulebook constraint is faction-level (Shadow + Vorlon opposing), so the
    // model stores counts and reports the opposing pair via hasOpposingMarks().

    private final int[] marks = new int[MarkType.values().length];

    /**
     * Default constructor. Initializes a CHARACTER card with sensible
     * defaults so the form is never blank on first open.
     */
    public CardEditorModel() {
        this.type = CardType.CHARACTER;
        this.rarity = Rarity.COMMON;
        this.faction = Faction.ANY;
        this.cardSet = CardSet.PREMIERE;
        this.cost = 0;
        this.mercenary = false;
        this.diplomacy = 0;
        this.intrigue = 0;
        this.psi = 0;
        this.leadership = 0;
        this.isAmbassador = false;
    }

    // ── Getters / setters ────────────────────────────────────────────────────

    public String getId()                       { return id; }
    public void setId(String id)                { this.id = id; }

    public String getTitle()                    { return title; }
    public void setTitle(String title)          { this.title = title; }

    public CardType getType()                   { return type; }
    public void setType(CardType type)          { this.type = type != null ? type : CardType.CHARACTER; }

    public String getSubtype()                  { return subtype; }
    public void setSubtype(String subtype)      { this.subtype = subtype; }

    public Rarity getRarity()                   { return rarity; }
    public void setRarity(Rarity rarity)        { this.rarity = rarity != null ? rarity : Rarity.COMMON; }

    public Faction getFaction()                 { return faction; }
    public void setFaction(Faction faction)     { this.faction = faction != null ? faction : Faction.ANY; }

    public CardSet getSet()                     { return cardSet; }
    public void setSet(CardSet cardSet)         { this.cardSet = cardSet != null ? cardSet : CardSet.PREMIERE; }

    public String getImageKey()                 { return imageKey; }
    public void setImageKey(String imageKey)    { this.imageKey = imageKey; }

    public String getText()                     { return text; }
    public void setText(String text)            { this.text = text; }

    public String getFlavorText()               { return flavorText; }
    public void setFlavorText(String flavorText) { this.flavorText = flavorText; }

    public int getCost()                        { return cost; }
    public void setCost(int cost)               { this.cost = Math.max(0, cost); }

    public boolean isMercenary()                { return mercenary; }
    public void setMercenary(boolean v)         { this.mercenary = v; }

    public int getDiplomacy()                   { return diplomacy; }
    public void setDiplomacy(int v)              { this.diplomacy = Math.max(0, v); }

    public int getIntrigue()                    { return intrigue; }
    public void setIntrigue(int v)               { this.intrigue = Math.max(0, v); }

    public int getPsi()                         { return psi; }
    public void setPsi(int v)                    { this.psi = Math.max(0, v); }

    public int getLeadership()                  { return leadership; }
    public void setLeadership(int v)            { this.leadership = Math.max(0, v); }

    public boolean isAmbassador()               { return isAmbassador; }
    public void setAmbassador(boolean v)        { this.isAmbassador = v; }

    // ── Printed marks (rulebook §6) ─────────────────────────────────────────

    /**
     * Returns the printed-mark count for one mark type. Never negative.
     */
    public int getMarkCount(MarkType type) {
        if (type == null) return 0;
        return marks[type.ordinal()];
    }

    /**
     * Sets the printed-mark count for one mark type. Negative values clamp
     * to zero. Null type is ignored.
     */
    public void setMarkCount(MarkType type, int count) {
        if (type == null) return;
        marks[type.ordinal()] = Math.max(0, count);
    }

    /**
     * Returns the total printed marks across all types on this card.
     */
    public int getTotalMarks() {
        int total = 0;
        for (int i = 0; i < marks.length; i++) {
            total += marks[i];
        }
        return total;
    }

    /**
     * Reports the rulebook opposing-marks pair at card level: Shadow and
     * Vorlon marks are opposing, and a faction cannot possess both at the
     * same time (rulebook Anatomy item 6). A single card carrying both is
     * the smallest unit that already violates the faction rule, so the
     * dialog surfaces this as a faction-level warning, never a save block.
     */
    public boolean hasOpposingMarks() {
        return getMarkCount(MarkType.SHADOW) > 0
            && getMarkCount(MarkType.VORLON) > 0;
    }

    // ── JSON serialization ──────────────────────────────────────────────────
    // Produces the flat-object shape DeckLoader.splitObjects expects.
    // String values are quoted; numeric and boolean values are unquoted.

    /**
     * Returns this card as a flat JSON object string. Only fields relevant to
     * the current card type are emitted, matching the on-disk card-data format.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;

        // Always-emitted string fields (in the order premiere.json uses)
        first = emitString(sb, first, "id", id);
        first = emitString(sb, first, "title", title);
        first = emitString(sb, first, "type", type != null ? type.name() : null);
        first = emitString(sb, first, "subtype", nonNull(subtype));
        first = emitString(sb, first, "rarity", rarity != null ? rarity.name() : null);
        first = emitString(sb, first, "faction", faction != null ? faction.name() : null);
        first = emitString(sb, first, "set", cardSet != null ? cardSet.name() : null);
        first = emitString(sb, first, "imageKey", nonNull(imageKey));
        first = emitString(sb, first, "text", nonNull(text));
        first = emitStringOpt(sb, first, "flavor_text", flavorText);

        // Always-emitted numeric fields
        first = emitInt(sb, first, "cost", cost);

        // Printed marks (rulebook §6): nested object, nonzero types only.
        // DeckLoader does not hydrate "marks" today and reports it as an
        // unknown field (same class as flavor_text before B5-4110); emitting
        // only nonzero entries keeps unmarked cards warning-free.
        first = emitMarks(sb, first);

        // CHARACTER-specific stats
        if (type == CardType.CHARACTER) {
            first = emitInt(sb, first, "diplomacy", diplomacy);
            first = emitInt(sb, first, "intrigue", intrigue);
            first = emitInt(sb, first, "psi", psi);
            first = emitInt(sb, first, "leadership", leadership);
            first = emitBool(sb, first, "isAmbassador", isAmbassador);
        }

        // Always-emitted boolean fields
        first = emitBool(sb, first, "mercenary", mercenary);

        sb.append("}");
        return sb.toString();
    }

    /**
     * Returns this card as a {@code Map<String, String>} with JSON key names
     * as keys and all values as strings, matching the format produced by
     * {@code DeckLoader.parseObject}.
     */
    public Map<String, String> toMap() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("id", id);
        m.put("title", title);
        m.put("type", type != null ? type.name() : null);
        m.put("subtype", nonNull(subtype));
        m.put("rarity", rarity != null ? rarity.name() : null);
        m.put("faction", faction != null ? faction.name() : null);
        m.put("set", cardSet != null ? cardSet.name() : null);
        m.put("imageKey", nonNull(imageKey));
        m.put("text", nonNull(text));
        if (flavorText != null && flavorText.length() > 0) {
            m.put("flavor_text", flavorText);
        }
        m.put("cost", Integer.toString(cost));
        String marksJson = buildMarksFragment();
        if (marksJson != null) {
            m.put("marks", marksJson);
        }
        if (type == CardType.CHARACTER) {
            m.put("diplomacy", Integer.toString(diplomacy));
            m.put("intrigue", Integer.toString(intrigue));
            m.put("psi", Integer.toString(psi));
            m.put("leadership", Integer.toString(leadership));
            m.put("isAmbassador", Boolean.toString(isAmbassador));
        }
        m.put("mercenary", Boolean.toString(mercenary));
        return m;
    }

    // ── JSON parsing ──────────────────────────────────────────────────────────

    /**
     * Parses a flat JSON object string into a model.
     * Handles escaped quotes, newlines, and primitive (number/boolean) values.
     */
    public static CardEditorModel fromJson(String json) {
        Map<String, String> m = parseJsonObject(json);
        return fromMap(m);
    }

    /**
     * Builds a model from a map of string keys to string values.
     * Missing keys take their default values.
     */
    public static CardEditorModel fromMap(Map<String, String> m) {
        CardEditorModel model = new CardEditorModel();
        if (m == null || m.isEmpty()) return model;

        model.id = m.get("id");
        model.title = m.get("title");

        String typeStr = m.get("type");
        if (typeStr != null) {
            try {
                model.type = CardType.valueOf(typeStr.toUpperCase().replace("-", "_"));
            } catch (IllegalArgumentException e) {
                model.type = CardType.CHARACTER;
            }
        }

        model.subtype = m.get("subtype");
        model.rarity = parseRarity(m.get("rarity"));
        model.faction = parseFaction(m.get("faction"));
        model.cardSet = parseCardSet(m.get("set"));
        model.imageKey = m.get("imageKey");
        model.text = m.get("text");
        model.flavorText = m.get("flavor_text");

        model.cost = parseIntSafe(m.get("cost"), 0);
        model.mercenary = parseBoolSafe(m.get("mercenary"), false);

        // Printed marks (rulebook §6): nested {"SHADOW":2,...} fragment.
        // Absent or unparseable entries default to zero; negatives clamp.
        parseMarksFragment(model, m.get("marks"));

        // CHARACTER-specific fields
        model.diplomacy = parseIntSafe(m.get("diplomacy"), 0);
        model.intrigue = parseIntSafe(m.get("intrigue"), 0);
        model.psi = parseIntSafe(m.get("psi"), 0);
        model.leadership = parseIntSafe(m.get("leadership"), 0);
        model.isAmbassador = parseBoolSafe(m.get("isAmbassador"), false);

        return model;
    }

    // ── File I/O (B5-4109) ───────────────────────────────────────────────────
    //
    // Headless-load test hooks live here (not in the Swing dialog) so the
    // headless test has zero AWT/Swing dependencies. All reads and writes
    // are UTF-8; Java 6 has no try-with-resources, so streams close in
    // finally blocks (B5-0102 DeckLoader precedent).
    //
    // File shape: one flat JSON object per file (the same shape toJson()
    // emits), so a saved file round-trips through fromFile() directly and
    // also parses through DeckLoader.parseCards("[" + text + "]").

    /**
     * B5-4109: reads a saved card file into a model.
     *
     * <p>Populates every supported field (common fields, CHARACTER stats,
     * marks) via the fromJson path, so a file written by saveToFile loads
     * with all fields populated. Throws java.io.IOException when the file
     * cannot be read.
     */
    public static CardEditorModel fromFile(File file) throws java.io.IOException {
        if (file == null) throw new java.io.IOException("CardEditorModel.fromFile: null file");
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            BufferedReader buffered = new BufferedReader(reader);
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = buffered.readLine()) != null) {
                text.append(line).append('\n');
            }
            return fromJson(text.toString());
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 is mandatory on every JVM; rethrow as IOException so the
            // signature stays honest without importing new exception types.
            throw new java.io.IOException("CardEditorModel.fromFile: UTF-8 unsupported: " + e.getMessage());
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (java.io.IOException ignored) { }
            }
        }
    }

    /**
     * B5-4109: writes this card's JSON form to a file (UTF-8, single flat
     * object). Overwrites an existing file. Throws java.io.IOException
     * when the file cannot be written.
     */
    public void saveToFile(File file) throws java.io.IOException {
        if (file == null) throw new java.io.IOException("CardEditorModel.saveToFile: null file");
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            writer.write(toJson());
            writer.write("\n");
            writer.flush();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new java.io.IOException("CardEditorModel.saveToFile: UTF-8 unsupported: " + e.getMessage());
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (java.io.IOException ignored) { }
            }
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Returns a list of missing required field messages.
     * An empty list means all required fields are present.
     *
     * <p>Always required: {@code id}, {@code title}, {@code type}.
     * For CHARACTER: the five stat fields are always defaulted in the model
     * (0 / false), so they are not "missing" — but a CHARACTER card loaded
     * from JSON without them would be dropped by DeckLoader. This method
     * flags empty {@code id} and {@code title} as the editor's gate.
     */
    public List<String> validateRequired() {
        List<String> missing = new ArrayList<String>();
        if (id == null || id.trim().length() == 0) {
            missing.add("id");
        }
        if (title == null || title.trim().length() == 0) {
            missing.add("title");
        }
        if (type == null) {
            missing.add("type");
        }
        return missing;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private static String nonNull(String s) {
        return s != null ? s : "";
    }

    /** Emits a quoted string field, or skips it if the value is null. */
    private static boolean emitString(StringBuilder sb, boolean first, String key, String value) {
        if (value == null) return first;
        if (!first) sb.append(",");
        sb.append(escapeJson(key)).append(":").append(escapeJson(value));
        return false;
    }

    /** Emits a quoted string field, or skips it if the value is null or empty. */
    private static boolean emitStringOpt(StringBuilder sb, boolean first, String key, String value) {
        if (value == null || value.length() == 0) return first;
        if (!first) sb.append(",");
        sb.append(escapeJson(key)).append(":").append(escapeJson(value));
        return false;
    }

    /** Emits an unquoted integer field. */
    private static boolean emitInt(StringBuilder sb, boolean first, String key, int value) {
        if (!first) sb.append(",");
        sb.append(escapeJson(key)).append(":").append(Integer.toString(value));
        return false;
    }

    /** Emits an unquoted boolean field. */
    private static boolean emitBool(StringBuilder sb, boolean first, String key, boolean value) {
        if (!first) sb.append(",");
        sb.append(escapeJson(key)).append(":").append(value ? "true" : "false");
        return false;
    }

    /**
     * Emits the nested marks object, nonzero types only. Returns the updated
     * first-field flag. Skips the field entirely when the card is unmarked.
     * Java 6 only: index loop over MarkType.values().
     */
    private boolean emitMarks(StringBuilder sb, boolean first) {
        String fragment = buildMarksFragment();
        if (fragment == null) return first;
        if (!first) sb.append(",");
        sb.append(escapeJson("marks")).append(":").append(fragment);
        return false;
    }

    /**
     * Builds the nested marks fragment for this instance, or null when no
     * mark type has a nonzero count. Keys are MarkType names so DeckLoader
     * and the engine enum read them without translation.
     */
    private String buildMarksFragment() {
        MarkType[] types = MarkType.values();
        boolean any = false;
        for (int i = 0; i < types.length; i++) {
            if (marks[types[i].ordinal()] > 0) { any = true; break; }
        }
        if (!any) return null;
        StringBuilder inner = new StringBuilder();
        inner.append("{");
        boolean innerFirst = true;
        for (int i = 0; i < types.length; i++) {
            int count = marks[types[i].ordinal()];
            if (count <= 0) continue;
            if (!innerFirst) inner.append(",");
            inner.append(escapeJson(types[i].name()));
            inner.append(":");
            inner.append(Integer.toString(count));
            innerFirst = false;
        }
        inner.append("}");
        return inner.toString();
    }

    /** Wraps a string in JSON double-quotes with proper escaping. */
    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"')      { sb.append("\\\""); }
            else if (c == '\\') { sb.append("\\\\"); }
            else if (c == '\n') { sb.append("\\n"); }
            else if (c == '\t') { sb.append("\\t"); }
            else if (c == '\r') { sb.append("\\r"); }
            else                { sb.append(c); }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * Minimal flat JSON-object parser. Handles string values (with escape
     * sequences) and primitive values (numbers, booleans). Nested objects
     * and arrays are captured as raw fragments.
     */
    private static Map<String, String> parseJsonObject(String json) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        if (json == null) return map;
        json = json.trim();

        // Allow an array wrapper: [{"...":"..."}] — take the first object.
        if (json.startsWith("[")) {
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
                        return parseSingleObject(json.substring(start, i + 1));
                    }
                }
            }
            return map;
        }

        return parseSingleObject(json);
    }

    private static Map<String, String> parseSingleObject(String obj) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        obj = obj.trim();
        if (obj.startsWith("{")) obj = obj.substring(1);
        if (obj.endsWith("}"))  obj = obj.substring(0, obj.length() - 1);

        int i = 0;
        while (i < obj.length()) {
            // Skip whitespace and commas
            while (i < obj.length() &&
                   (Character.isWhitespace(obj.charAt(i)) || obj.charAt(i) == ',')) {
                i++;
            }
            if (i >= obj.length()) break;

            // Find key (must start with ")
            if (obj.charAt(i) != '"') { i++; continue; }
            int ks = i;
            i++;
            StringBuilder key = new StringBuilder();
            while (i < obj.length()) {
                char c = obj.charAt(i);
                if (c == '\\' && i + 1 < obj.length()) {
                    char next = obj.charAt(i + 1);
                    if (next == '"')        key.append('"');
                    else if (next == '\\')  key.append('\\');
                    else if (next == 'n')   key.append('\n');
                    else if (next == 't')   key.append('\t');
                    else if (next == 'r')   key.append('\r');
                    else { key.append(c); key.append(next); }
                    i += 2;
                } else if (c == '"') {
                    i++;
                    break;
                } else {
                    key.append(c);
                    i++;
                }
            }

            // Find colon
            while (i < obj.length() && Character.isWhitespace(obj.charAt(i))) i++;
            if (i >= obj.length() || obj.charAt(i) != ':') break;
            i++;

            // Skip whitespace
            while (i < obj.length() && Character.isWhitespace(obj.charAt(i))) i++;

            String value;
            if (i < obj.length() && obj.charAt(i) == '"') {
                // String value — handle escaped quotes
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < obj.length()) {
                    char c = obj.charAt(i);
                    if (c == '\\' && i + 1 < obj.length()) {
                        char next = obj.charAt(i + 1);
                        if (next == '"')      sb.append('"');
                        else if (next == '\\') sb.append('\\');
                        else if (next == 'n')  sb.append('\n');
                        else if (next == 't')  sb.append('\t');
                        else if (next == 'r')  sb.append('\r');
                        else { sb.append(c); sb.append(next); }
                        i += 2;
                    } else if (c == '"') {
                        i++;
                        break;
                    } else {
                        sb.append(c);
                        i++;
                    }
                }
                value = sb.toString();
            } else {
                // Primitive (number/boolean) or nested object/array fragment
                int start = i;
                int braceDepth = 0;
                int bracketDepth = 0;
                while (i < obj.length()) {
                    char c = obj.charAt(i);
                    if (c == '"') {
                        // Skip over a quoted string inside this value
                        i++;
                        while (i < obj.length()) {
                            if (obj.charAt(i) == '\\' && i + 1 < obj.length()) i += 2;
                            else if (obj.charAt(i) == '"') { i++; break; }
                            else i++;
                        }
                        continue;
                    }
                    if (c == '{') braceDepth++;
                    else if (c == '}') braceDepth--;
                    else if (c == '[') bracketDepth++;
                    else if (c == ']') bracketDepth--;
                    else if ((c == ',' && braceDepth == 0 && bracketDepth == 0)) break;
                    i++;
                }
                value = obj.substring(start, i).trim();
            }

            if (key.length() > 0) {
                map.put(key.toString(), value);
            }

            // Skip comma
            while (i < obj.length() &&
                   (Character.isWhitespace(obj.charAt(i)) || obj.charAt(i) == ',')) {
                i++;
            }
        }
        return map;
    }

    private static int parseIntSafe(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private static boolean parseBoolSafe(String s, boolean def) {
        if (s == null) return def;
        return "true".equalsIgnoreCase(s.trim());
    }

    /**
     * Parses the nested marks fragment {"SHADOW":2,...} into the model.
     * Unknown keys are skipped loudly (stderr) per the project's loud-no-op
     * standard; unparseable or negative values clamp to zero via setMarkCount.
     * A null or empty fragment leaves all counts at zero.
     */
    private static void parseMarksFragment(CardEditorModel model, String fragment) {
        if (model == null || fragment == null) return;
        String f = fragment.trim();
        if (f.length() == 0) return;
        if (f.startsWith("{")) f = f.substring(1);
        if (f.endsWith("}")) f = f.substring(0, f.length() - 1);
        f = f.trim();
        if (f.length() == 0) return;
        // Split on top-level commas (no nesting inside a marks fragment).
        int start = 0;
        boolean inQuote = false;
        for (int i = 0; i <= f.length(); i++) {
            char c = (i < f.length()) ? f.charAt(i) : ',';
            if (c == '"') {
                inQuote = !inQuote;
            }
            if ((c == ',' && !inQuote) || i == f.length()) {
                String pair = f.substring(start, i).trim();
                start = i + 1;
                if (pair.length() == 0) continue;
                int colon = pair.indexOf(':');
                if (colon < 0) continue;
                String rawKey = pair.substring(0, colon).trim();
                String rawVal = pair.substring(colon + 1).trim();
                String key = stripQuotes(rawKey);
                MarkType type = null;
                try {
                    type = MarkType.valueOf(key.toUpperCase().replace("-", "_"));
                } catch (IllegalArgumentException e) {
                    type = null;
                } catch (NullPointerException e) {
                    type = null;
                }
                if (type == null) {
                    System.err.println("CardEditorModel: unknown mark type skipped: " + rawKey);
                    continue;
                }
                int count = parseIntSafe(stripQuotes(rawVal), 0);
                model.setMarkCount(type, count);
            }
        }
    }

    /** Strips one pair of surrounding double-quotes, if present. */
    private static String stripQuotes(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static Rarity parseRarity(String s) {
        if (s == null) return Rarity.COMMON;
        try {
            return Rarity.valueOf(s.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return Rarity.COMMON;
        }
    }

    private static Faction parseFaction(String s) {
        if (s == null) return Faction.ANY;
        try {
            return Faction.valueOf(s.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return Faction.ANY;
        }
    }

    private static CardSet parseCardSet(String s) {
        if (s == null) return CardSet.PREMIERE;
        try {
            return CardSet.valueOf(s.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return CardSet.PREMIERE;
        }
    }
}
