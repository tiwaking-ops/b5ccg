package b5ccg.engine;

import b5ccg.model.*;
import b5ccg.model.enums.*;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Minimal JSON parser for card data — no external libraries.
 * Parses the flat JSON array produced in premiere.json / deluxe.json.
 */
public class DeckLoader {

    // ══ B5-1992: rulebook play-deck floor (rulebook section II) ═════════════
    //
    // :128 and :193 -- "Each player must play with a minimum of 45 cards in
    // his deck" / "Each must have a minimum of 45 cards", and :128 continues
    // "There is no maximum number of cards that may be in a play deck."
    // :195 -- "Must contain one Starting Ambassador."
    //
    // WHAT THIS IS NOT. loadFromResource / loadBothSets return the CARD POOL,
    // not a play deck, so the floor is deliberately NOT applied to them: a pool
    // is a few hundred cards and gating it on "a deck" would be a category
    // error. validatePlayDeck is a separate, explicitly-named entry point that
    // a deck BUILDER calls.
    //
    // WHAT IS DELIBERATELY ABSENT, and why. :194 -- "It may have a maximum of
    // 3 of any card" -- is NOT enforced here: B5-1965 owns max-3-copies and
    // faction playability, and this row is fenced to keep that work separate.
    // There is also no maximum, because the rulebook states there is none.
    //
    // RELATION TO StarterDeckBuilder.validateDeck. That is a DIFFERENT
    // question, not a duplicate: it checks a starter deck against DECK_SIZE
    // (the 50-fixed + 10-random structure) and against faction playability, and
    // throws. This one checks the rulebook's play-deck floor for a custom deck
    // and RETURNS its violations so a builder can show them all at once. The
    // ambassador test differs deliberately -- StarterDeckBuilder requires the
    // card to be the FACTION's ambassador (findAmbassador matches
    // ch.getFaction() == faction), while :195 only requires the deck to
    // CONTAIN one Starting Ambassador. For NON_ALIGNED (:884 -- "there is no
    // single 'ambassador' for the League ... the player chooses a character
    // listed as an ambassador for one of the Non-Aligned species") the
    // faction-scoped test has no single answer, so the deck-wide test below is
    // the correct one and returns true for any species ambassador.

    /** Rulebook :128 / :193 -- the minimum play-deck size. */
    public static final int MIN_PLAY_DECK_SIZE = 45;

    /**
     * Rulebook II:194 -- max copies of any one card in a play deck.
     * FIXED-rarity cards (Starting Ambassadors and other unique fixed-list
     * cards) are exempt from this limit per B5-1965.
     */
    public static final int MAX_COPIES_PER_CARD = 3;

    /**
     * B5-1992 + B5-1965: the rulebook play-deck validation. Returns every
     * violation found, so a builder can present them together; an empty list
     * means legal. Returns the single "deck is null" violation rather than
     * throwing, because this is called from a validation surface that must
     * report rather than crash.
     *
     * <p>Nulls inside the deck are counted as cards for the size test (the
     * rulebook counts cards, and a null is a card slot that cannot be
     * validated) and reported by index, so a malformed entry is visible rather
     * than silently shrinking the count.
     *
     * <p>Checks performed:
     * <ul>
     *   <li>Deck size >= 45 (rulebook :128/:193)</li>
     *   <li>Contains at least one Starting Ambassador (rulebook :195)</li>
     *   <li>No more than 3 copies of any card by ID, except FIXED-rarity cards (rulebook :194 + B5-1965)</li>
     *   <li>Every card's faction is playable by the given player faction via {@link Faction#isPlayableBy} (rulebook Character Cards, B5-1965)</li>
     * </ul>
     *
     * @param deck the list of cards to validate
     * @param playerFaction the faction of the player building the deck; used for
     *     faction-playability checks. Pass {@code null} to skip faction checks.
     */
    public static List<String> validatePlayDeck(List<Card> deck, Faction playerFaction) {
        List<String> problems = new ArrayList<String>();
        if (deck == null) {
            problems.add("deck is null");
            return problems;
        }
        if (deck.size() < MIN_PLAY_DECK_SIZE) {
            problems.add("size " + deck.size() + " < rulebook minimum "
                + MIN_PLAY_DECK_SIZE + " (:128/:193)");
        }

        // Count copies per card ID for the max-3 rule (B5-1965).
        Map<String, Integer> idCounts = new HashMap<String, Integer>();
        boolean hasAmbassador = false;
        for (int i = 0; i < deck.size(); i++) {
            Card c = deck.get(i);
            if (c == null) {
                problems.add("null card at index " + i);
                continue;
            }

            // :195 "Must contain one Starting Ambassador" -- any Starting
            // Ambassador in the deck. Deck-wide, not faction-scoped; see the
            // NON_ALIGNED note above.
            if (!hasAmbassador && c instanceof CharacterCard
                    && ((CharacterCard) c).isAmbassador()) {
                hasAmbassador = true;
            }

            // Count copies by card ID.
            String id = c.getId();
            int count = 1;
            Integer prev = idCounts.get(id);
            if (prev != null) count = prev.intValue() + 1;
            idCounts.put(id, new Integer(count));

            // Faction playability check (B5-1965).
            if (playerFaction != null && !c.getFaction().isPlayableBy(playerFaction)) {
                problems.add("card " + id + " (" + c.getTitle() + ") not playable by faction "
                    + playerFaction + " (card faction: " + c.getFaction() + ")");
            }
        }
        if (!hasAmbassador) {
            problems.add("no Starting Ambassador in deck (:195)");
        }

        // Max 3 copies per card (rulebook :194), except FIXED-rarity cards.
        for (Map.Entry<String, Integer> e : idCounts.entrySet()) {
            int n = e.getValue().intValue();
            if (n > MAX_COPIES_PER_CARD) {
                // Find the card to check its rarity.
                Card sample = null;
                for (int i = 0; i < deck.size(); i++) {
                    Card c = deck.get(i);
                    if (c != null && c.getId().equals(e.getKey())) {
                        sample = c;
                        break;
                    }
                }
                if (sample == null || sample.getRarity() != Rarity.FIXED) {
                    problems.add("card " + e.getKey() + " appears " + n
                        + " times; rulebook maximum is " + MAX_COPIES_PER_CARD
                        + " (:194)");
                }
            }
        }

        return problems;
    }

    /**
     * Overload for backward compatibility: validates without faction checks.
     * @deprecated Use {@link #validatePlayDeck(List, Faction)} for full validation.
     */
    public static List<String> validatePlayDeck(List<Card> deck) {
        return validatePlayDeck(deck, null);
    }

    /** Convenience form of {@link #validatePlayDeck}: true when legal. */
    public static boolean isPlayDeckLegal(List<Card> deck) {
        return validatePlayDeck(deck).isEmpty();
    }

    /** Convenience form with faction: true when legal for that faction. */
    public static boolean isPlayDeckLegal(List<Card> deck, Faction playerFaction) {
        return validatePlayDeck(deck, playerFaction).isEmpty();
    }

    public static List<Card> loadFromResource(String resourcePath) throws IOException {
        URL url = DeckLoader.class.getResource(resourcePath);
        if (url == null) throw new FileNotFoundException("Resource not found: " + resourcePath);
        InputStream is = null;
        try {
            is = url.openStream();
            String json = readAll(is);
            return parseCards(json);
        } finally {
            if (is != null) { try { is.close(); } catch (IOException e) {} }
        }
    }

    /**
     * Card-pool rule (human ruling 2026-09-21, revokes Q6 no-Premiere default):
     * the pool is ALL Deluxe cards plus every Premiere card never reprinted.
     * A Premiere card counts as reprinted (a duplicate) when its title also
     * appears in Deluxe; the Deluxe version wins. Title and imageKey agree on
     * 100% of the current overlap (383/383), so title is the operative key.
     */
    public static List<Card> loadBothSets() throws IOException {
        List<Card> premiere = loadFromResource("/cards/premiere.json");
        List<Card> deluxe = loadFromResource("/cards/deluxe.json");
        List<Card> all = new ArrayList<Card>(deluxe);
        for (int i = 0; i < premiere.size(); i++) {
            Card c = premiere.get(i);
            if (!hasTitle(deluxe, c.getTitle())) all.add(c);
        }
        return all;
    }

    private static boolean hasTitle(List<Card> cards, String title) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).getTitle().equals(title)) return true;
        }
        return false;
    }

    /**
     * Load a resource that is a flat JSON array of objects (no nesting) and
     * return one {@code Map} per object. Used by {@link StarterDeckBuilder} for
     * the starter-deck definition file, reusing the same tolerant tokeniser as
     * card loading.
     */
    public static List<Map<String, String>> loadFlatObjects(String resourcePath) throws IOException {
        URL url = DeckLoader.class.getResource(resourcePath);
        if (url == null) throw new FileNotFoundException("Resource not found: " + resourcePath);
        InputStream is = null;
        try {
            is = url.openStream();
            return splitObjects(readAll(is));
        } finally {
            if (is != null) { try { is.close(); } catch (IOException e) {} }
        }
    }

    // ── Internal JSON parsing ─────────────────────────────────────────────────

    private static String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        try {
            while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
        } finally {
            // is will be closed by caller via try-with-resources in Java 7+;
            // for Java 6 we simply leave it open — the URL connection handles cleanup.
        }
        return buf.toString(StandardCharsets.UTF_8.name());
    }

    static List<Card> parseCards(String json) {
        List<Card> cards = new ArrayList<Card>();
        List<Map<String, String>> objects = splitObjects(json);
        for (Map<String, String> obj : objects) {
            try {
                Card c = buildCard(obj);
                // B5-0323: optional "cost" key (rulebook §Anatomy item 2).
                // Absent → 0, preserving current behavior for the existing
                // data (which carries no cost field — B5-0311 C1). Negative
                // or unparseable values clamp to the default 0.
                String costStr = obj.get("cost");
                if (costStr != null && costStr.length() > 0) {
                    try { c.setCost(Integer.parseInt(costStr.trim())); }
                    catch (NumberFormatException nfe) { /* keep default 0 */ }
                }
                // B5-0336: optional nested "participation" object on CONFLICT
                // cards (proposal §3). Absent → null = open participation
                // (§3.4 backward compatibility — no current card carries it).
                // Malformed fragments are logged loudly and left open (§5.2).
                String partStr = obj.get("participation");
                if (partStr != null && partStr.trim().startsWith("{")
                        && c instanceof ConflictCard) {
                    try { ((ConflictCard) c).setParticipation(Participation.parse(partStr)); }
                    catch (RuntimeException re) {
                        System.err.println("B5-0336: bad participation for " + obj.get("id")
                            + " — open participation kept (" + re.getMessage() + ")");
                    }
                }
                // B5-0395: optional boolean "mercenary" flag (rulebook
                // §Mercenaries). Absent → false, per B5-0386's schema rec —
                // the current data carries no such flag, so this is purely a
                // latent surface for fixtures and future data work.
                String mercStr = obj.get("mercenary");
                if (mercStr != null) {
                    c.setMercenary("true".equalsIgnoreCase(mercStr.trim()));
                }
                cards.add(c);
            } catch (Exception e) {
                System.err.println("Skipping card, parse error: " + e.getMessage()
                    + " | data=" + obj.get("id"));
            }
        }
        return cards;
    }

    /** Split the top-level JSON array into individual object maps. */
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

    /** Parse a single JSON object { "key":"value", ... } into a Map. */
    private static Map<String, String> parseObject(String obj) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        // Strip outer braces
        obj = obj.trim();
        if (obj.startsWith("{")) obj = obj.substring(1);
        if (obj.endsWith("}"))   obj = obj.substring(0, obj.length() - 1);

        // Simple tokenisation — handles string and primitive values.
        int i = 0;
        while (i < obj.length()) {
            // Find key
            int ks = obj.indexOf('"', i);
            if (ks < 0) break;
            int ke = obj.indexOf('"', ks + 1);
            if (ke < 0) break;
            String key = obj.substring(ks + 1, ke);
            i = ke + 1;

            // Find colon
            int colon = obj.indexOf(':', i);
            if (colon < 0) break;
            i = colon + 1;

            // Skip whitespace
            while (i < obj.length() && Character.isWhitespace(obj.charAt(i))) i++;

            String value;
            if (i < obj.length() && obj.charAt(i) == '"') {
                // String value — handle escaped quotes
                StringBuilder sb = new StringBuilder();
                i++; // skip opening quote
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
                // Primitive (number/boolean) — B5-0336: a value may also be a
                // nested object/array (e.g. "participation"); scan to the
                // matching close so the map keeps the raw fragment. DeckLoader
                // has no tree parser — consumers parse the fragment.
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
                        if (depth == 0) break;   // this object's closing brace
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

    private static Card buildCard(Map<String, String> m) {
        String id       = req(m, "id");
        String title    = req(m, "title");
        String typeStr  = req(m, "type");
        String subtype  = getOrDefault(m, "subtype", "");
        String rarStr   = getOrDefault(m, "rarity", "COMMON");
        String facStr   = getOrDefault(m, "faction", "ANY");
        String setStr   = getOrDefault(m, "set", "PREMIERE");
        String imgKey   = getOrDefault(m, "imageKey", id);
        String text     = getOrDefault(m, "text", "");

        CardType type    = CardType.valueOf(typeStr.toUpperCase());
        Rarity   rarity  = parseRarity(rarStr);
        Faction  faction = parseFaction(facStr);
        CardSet  cardSet = CardSet.valueOf(setStr.toUpperCase());

        // B5-1055: validate that record carries only expected fields for its type
        validateFields(m, type, id);

        switch (type) {
            case CHARACTER: {
                int dip  = intVal(m, "diplomacy",  0);
                int intr = intVal(m, "intrigue",   0);
                int psi  = intVal(m, "psi",        0);
                int lead = intVal(m, "leadership", 0);
                boolean isAmb = boolVal(m, "isAmbassador", false);
                return new CharacterCard(id, title, subtype, rarity, faction, cardSet,
                                         imgKey, text, dip, intr, psi, lead, isAmb);
            }
            case FLEET: {
                int mil = intVal(m, "military", 1);
                FleetCard fleet = new FleetCard(id, title, subtype, rarity, faction, cardSet,
                                                imgKey, text, mil);
                // B5-0336: optional fleetClass (proposal §3.6) — consumed by
                // participation fleetSubtypes filters. Absent → null (unknown).
                fleet.setFleetClass(getOrDefault(m, "fleetClass", null));
                return fleet;
            }
            case CONFLICT: {
                ConflictType ct = parseConflictType(m, id);
                int reward = intVal(m, "influenceReward", 1);
                return new ConflictCard(id, title, subtype, rarity, faction, cardSet,
                                        imgKey, text, ct, reward);
            }
            case AGENDA: {
                boolean major  = boolVal(m, "isMajorAgenda", false);
                String  winKey = getOrDefault(m, "winCondition", "INFLUENCE_20");
                return new AgendaCard(id, title, subtype, rarity, faction, cardSet,
                                      imgKey, text, major, winKey);
            }
            case AFTERMATH: {
                String trigger = getOrDefault(m, "triggerCondition", "ANY");
                return new AftermathCard(id, title, subtype, rarity, faction, cardSet,
                                         imgKey, text, trigger);
            }
            case EVENT:
                return new EventCard(id, title, subtype, rarity, faction, cardSet, imgKey, text);
            case CONTINGENCY:
                return new ContingencyCard(id, title, subtype, rarity, faction,
                        cardSet, imgKey, text, getOrDefault(m, "validTargetType", "ANY"),
                        getOrDefault(m, "validTargetRace", "ANY"),
                        getOrDefault(m, "triggerCondition", ""));
            case ENHANCEMENT: {
                int dip  = intVal(m, "diplomacyBonus",  0);
                int intr = intVal(m, "intrigueBonus",   0);
                int psi  = intVal(m, "psiBonus",        0);
                int mil  = intVal(m, "militaryBonus",   0);
                int lead = intVal(m, "leadershipBonus", 0);
                return new EnhancementCard(id, title, subtype, rarity, faction, cardSet,
                                           imgKey, text, dip, intr, psi, mil, lead);
            }
            case GROUP:
                return new GroupCard(id, title, subtype, rarity, faction, cardSet, imgKey, text);
            case LOCATION: {
                int income = intVal(m, "influencePerRound", 1);
                int military = intVal(m, "military", 0);
                return new LocationCard(id, title, subtype, rarity, faction, cardSet,
                                        imgKey, text, income, military);
            }
            default:
                throw new IllegalArgumentException("Unknown card type: " + type);
        }
    }

    /**
     * B5-1055: Validate that a record has all required fields and reports unknown fields.
     * Per schema contract B5-1025:
     * - Required fields: must be present, missing = reject (throw)
     * - Optional fields: may be present or absent
     * - Forbidden/unknown fields: present but not allowed = report (log to stderr)
     * An "unknown field" is present but not in the expected set (required + optional).
     */
    private static void validateFields(Map<String, String> m, CardType type, String recordId) {
        // Always required: id, title, type
        String[] alwaysRequired = {"id", "title", "type"};
        for (String field : alwaysRequired) {
            if (!m.containsKey(field)) {
                throw new IllegalArgumentException("Missing required field: " + field + " on card " + recordId);
            }
        }

        // Build the set of all expected fields for this card type
        // Always optional/common fields (per B5-1025 schema contract "Common to every type")
        Set<String> expected = new java.util.HashSet<String>(java.util.Arrays.asList(
            "subtype", "rarity", "faction", "set", "imageKey", "text", "cost", "mercenary"
        ));

        // Type-specific required and optional fields per schema contract B5-1025 table
        switch (type) {
            case CHARACTER:
                // Required: diplomacy, intrigue, psi, leadership, isAmbassador
                // Optional: cost
                expected.add("diplomacy");
                expected.add("intrigue");
                expected.add("psi");
                expected.add("leadership");
                expected.add("isAmbassador");
                // Forbidden: timing, participation, fleetClass, conflictType, etc.
                break;
            case FLEET:
                // Required: military
                // Optional: cost, fleetClass
                expected.add("military");
                expected.add("fleetClass");
                break;
            case CONFLICT:
                // Required: conflictType, influenceReward
                // Optional: cost, participation
                expected.add("conflictType");
                expected.add("influenceReward");
                expected.add("participation");
                break;
            case AGENDA:
                // Required: isMajorAgenda, winCondition
                // Optional: cost
                // Forbidden: cost, stats
                break;
            case AFTERMATH:
                // Required: triggerCondition
                // Optional: cost
                break;
            case EVENT:
                // Required: none beyond common fields
                // Optional: cost
                // Forbidden: timing (one record has it, de_event_armistice - will be reported as unknown)
                break;
            case CONTINGENCY:
                // Required: validTargetType, validTargetRace, triggerCondition
                // Optional: cost
                expected.add("validTargetType");
                expected.add("validTargetRace");
                expected.add("triggerCondition");
                break;
            case ENHANCEMENT:
                // Required: diplomacyBonus, intrigueBonus, psiBonus, militaryBonus, leadershipBonus
                // Optional: cost, participation
                expected.add("diplomacyBonus");
                expected.add("intrigueBonus");
                expected.add("psiBonus");
                expected.add("militaryBonus");
                expected.add("leadershipBonus");
                expected.add("participation");
                break;
            case GROUP:
                // Required: none beyond common fields
                // Optional: cost
                break;
            case LOCATION:
                // Required: influencePerRound
                // Optional: cost, military
                expected.add("influencePerRound");
                expected.add("military");
                break;
            default:
                break;
        }

        // Check for missing required type-specific fields (per B5-1025 schema contract)
        switch (type) {
            case CHARACTER:
                for (String field : new String[]{"diplomacy", "intrigue", "psi", "leadership", "isAmbassador"}) {
                    if (!m.containsKey(field)) {
                        throw new IllegalArgumentException("Missing required field: " + field + " on card " + recordId);
                    }
                }
                break;
            case FLEET:
                if (!m.containsKey("military")) {
                    throw new IllegalArgumentException("Missing required field: military on card " + recordId);
                }
                break;
            case CONFLICT:
                for (String field : new String[]{"conflictType", "influenceReward"}) {
                    if (!m.containsKey(field)) {
                        throw new IllegalArgumentException("Missing required field: " + field + " on card " + recordId);
                    }
                }
                break;
            case AGENDA:
                for (String field : new String[]{"isMajorAgenda", "winCondition"}) {
                    if (!m.containsKey(field)) {
                        throw new IllegalArgumentException("Missing required field: " + field + " on card " + recordId);
                    }
                }
                break;
            case AFTERMATH:
                if (!m.containsKey("triggerCondition")) {
                    throw new IllegalArgumentException("Missing required field: triggerCondition on card " + recordId);
                }
                break;
            case CONTINGENCY:
                for (String field : new String[]{"validTargetType", "validTargetRace", "triggerCondition"}) {
                    if (!m.containsKey(field)) {
                        throw new IllegalArgumentException("Missing required field: " + field + " on card " + recordId);
                    }
                }
                break;
            case ENHANCEMENT:
                for (String field : new String[]{"diplomacyBonus", "intrigueBonus", "psiBonus", "militaryBonus", "leadershipBonus"}) {
                    if (!m.containsKey(field)) {
                        throw new IllegalArgumentException("Missing required field: " + field + " on card " + recordId);
                    }
                }
                break;
            case LOCATION:
                if (!m.containsKey("influencePerRound")) {
                    throw new IllegalArgumentException("Missing required field: influencePerRound on card " + recordId);
                }
                break;
            default:
                break;
        }

        // Check for unknown fields (present but not in expected set)
        for (String key : m.keySet()) {
            if (!expected.contains(key)) {
                System.err.println("UNKNOWN FIELD: " + key + " on card " + recordId);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String req(Map<String, String> m, String key) {
        String v = m.get(key);
        if (v == null) throw new IllegalArgumentException("Missing required field: " + key);
        return v;
    }

    private static String getOrDefault(Map<String, String> m, String key, String def) {
        String v = m.get(key);
        return v != null ? v : def;
    }

    private static int intVal(Map<String, String> m, String key, int def) {
        String v = m.get(key);
        if (v == null) return def;
        try { return Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private static boolean boolVal(Map<String, String> m, String key, boolean def) {
        String v = m.get(key);
        if (v == null) return def;
        return "true".equalsIgnoreCase(v.trim());
    }

    private static Rarity parseRarity(String s) {
        try { return Rarity.valueOf(s.toUpperCase().replace("-", "_")); }
        catch (IllegalArgumentException e) { return Rarity.COMMON; }
    }

    private static Faction parseFaction(String s) {
        try { return Faction.valueOf(s.toUpperCase().replace("-", "_")); }
        catch (IllegalArgumentException e) { return Faction.ANY; }
    }

    /**
     * B5-1047: validated ConflictType read — replaces the bare valueOf at the
     * CONFLICT card-build site (line 270 pre-edit) that threw IllegalArgumentException
     * on any unexpected value, dropping the card at load with only a stderr line.
     * Logs loudly (stderr, names the record id and the bad value) and falls back to
     * DIPLOMACY so the card stays reachable and playable. Legal mapping is unchanged.
     */
    private static ConflictType parseConflictType(Map<String, String> m, String id) {
        String raw = getOrDefault(m, "conflictType", "DIPLOMACY");
        String upper = raw.toUpperCase();
        try {
            return ConflictType.valueOf(upper);
        } catch (IllegalArgumentException e) {
            System.err.println("DECKLOADER-B5-1047: unknown conflictType '"
                + raw + "' on record id '" + id + "', defaulting to DIPLOMACY");
            return ConflictType.DIPLOMACY;
        }
    }
}
