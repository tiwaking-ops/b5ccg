package b5ccg.engine;

import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.AgendaCard;
import b5ccg.model.FleetCard;
import b5ccg.model.LocationCard;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.Rarity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Builds the printed Premier Edition race starter decks.
 *
 * The canonical rulebook ("Starter Decks") states each race-specific Premier
 * starter deck is 50 fixed common cards tailored to the race plus 10 randomly
 * drawn uncommons/rares. The 50-card fixed lists are sourced (advisory) from a
 * contemporaneous fan compilation and verified against
 * {@code b5ccg/resources/cards/premiere.json}; see
 * {@code investigations/b5-premier-starter-deck-fixed-lists-2026-09-21.md}.
 * The deck definitions live in {@code b5ccg/resources/decks/premiere-starter-decks.json}.
 *
 * Interpretation (logged in docs/DECISIONS.md): the 10 random cards are distinct
 * Premiere UNCOMMON/RARE cards playable by the faction (race-loyal + NEUTRAL +
 * ANY), excluding cards already in the fixed 50. If fewer than 10 distinct
 * candidates exist the remainder repeats (should not occur: 160+ candidates per
 * faction).
 *
 * Card-pool note (human ruling 2026-09-21, Q6 revoked): the pool is ALL Deluxe
 * plus Premiere-never-reprinted (see DeckLoader.loadBothSets). Fixed deck
 * lists carry Premiere ids, so lookup falls back to title match — the Deluxe
 * reprint of the same title fills the slot. Randoms are drawn from any set
 * (reprinted uncommons/rares now exist as Deluxe records), excluding fixed
 * cards by TITLE, since a Deluxe reprint has a different id than the fixed
 * Premiere original.
 *
 * Java 6 only - stdlib only.
 */
public final class StarterDeckBuilder {

    public static final String DECK_RESOURCE = "/decks/premiere-starter-decks.json";
    public static final int FIXED_TARGET = 50;
    public static final int RANDOM_COUNT = 10;
    public static final int DECK_SIZE = FIXED_TARGET + RANDOM_COUNT;

    /** Cached deck-definition rows; null until first load. */
    private static List<Map<String, String>> deckEntries = null;

    /**
     * Cached Premiere id to title map; null until first load. Fixed deck
     * lists carry Premiere ids, so this resolves a fixed id to the title the
     * deduped pool holds (possibly on the Deluxe reprint).
     */
    private static Map<String, String> premiereTitles = null;

    /** 0 = non-deterministic (default); non-zero lets tests seed the draw. */
    private static long randomSeed = 0L;

    /**
     * Faction-specific starting board state: homeworld location, starting fleet,
     * and starting agenda. Per rulebook "Preparing the Playing Field", these
     * cards begin in play at game start (homeworld and fleet as Supporting Cards,
     * agenda to the left of the ambassador).
     */
    public static final class StartingSetup {
        public final LocationCard homeworld;
        public final FleetCard startingFleet;
        public final AgendaCard startingAgenda;

        public StartingSetup(LocationCard homeworld, FleetCard startingFleet, AgendaCard startingAgenda) {
            this.homeworld = homeworld;
            this.startingFleet = startingFleet;
            this.startingAgenda = startingAgenda;
        }
    }

    private StarterDeckBuilder() {}

    /** Seed the random uncommons/rares draw (tests). 0 restores default. */
    public static void setRandomSeed(long seed) { randomSeed = seed; }

    /** True if the starter-deck resource is on the classpath. */
    public static boolean isAvailable() {
        try {
            return DeckLoader.class.getResource(DECK_RESOURCE) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Convenience: load both card sets and build the faction's starter deck. */
    public static List<Card> buildForFaction(Faction faction) throws IOException {
        return build(faction, DeckLoader.loadBothSets());
    }

    /**
     * Get the faction-specific starting board state (homeworld location,
     * starting fleet, and starting agenda) from an already-loaded card pool.
     * Returns null if the pool is missing any of the required cards.
     * The card IDs are sourced from {@code premiere-starter-decks.json}.
     */
    public static StartingSetup getStartingSetup(Faction faction, List<Card> pool) {
        if (faction == null || pool == null || pool.isEmpty()) return null;

        Map<String, Card> byId = new HashMap<String, Card>();
        for (int i = 0; i < pool.size(); i++) {
            Card c = pool.get(i);
            byId.put(c.getId(), c);
        }

        String homeworldId = null;
        String fleetId = null;
        String agendaId = null;

        if (faction == Faction.HUMAN) {
            homeworldId = "loc_earth";
            fleetId = "fleet_homeworld_human";
            agendaId = "agenda_alliance_of_races";
        } else if (faction == Faction.CENTAURI) {
            homeworldId = "loc_centauri_prime";
            fleetId = "fleet_homeworld_centauri";
            agendaId = "agenda_knowledge_is_power";
        } else if (faction == Faction.MINBARI) {
            homeworldId = "loc_minbar";
            fleetId = "fleet_homeworld_minbari";
            agendaId = "agenda_finish_the_war";
        } else if (faction == Faction.NARN) {
            homeworldId = "loc_narn_homeworld";
            fleetId = "fleet_homeworld_narn";
            agendaId = "agenda_never_again";
        } else {
            return null; // NON_ALIGNED not supported
        }

        Card hw = byId.get(homeworldId);
        Card fl = byId.get(fleetId);
        Card ag = byId.get(agendaId);

        // Playtest fix: the deduped pool drops Premiere cards reprinted in
        // Deluxe (DeckLoader.loadBothSets, title-keyed), so a Premiere setup
        // id can be absent while its Deluxe reprint is present — today this
        // voids the WHOLE setup for CENTAURI and MINBARI (their agendas).
        // Fall back to the same title match build() uses, so those factions
        // receive a starting setup instead of none.
        if (hw == null || fl == null || ag == null) {
            Map<String, Card> byTitle = new HashMap<String, Card>();
            for (int i = 0; i < pool.size(); i++) {
                Card c = pool.get(i);
                if (!byTitle.containsKey(c.getTitle())) byTitle.put(c.getTitle(), c);
            }
            if (hw == null) hw = findFixedByTitle(byTitle, homeworldId);
            if (fl == null) fl = findFixedByTitle(byTitle, fleetId);
            if (ag == null) ag = findFixedByTitle(byTitle, agendaId);
        }

        if (hw == null || fl == null || ag == null) return null;

        if (!(hw instanceof LocationCard)) return null;
        if (!(fl instanceof FleetCard)) return null;
        if (!(ag instanceof AgendaCard)) return null;

        return new StartingSetup((LocationCard) hw, (FleetCard) fl, (AgendaCard) ag);
    }

    /**
     * Build the starter deck for {@code faction} from an already-loaded card
     * pool (both sets): 50 fixed cards in resource order, then 10 random
     * uncommons/rares. Missing fixed ids or a non-50 fixed count are reported to
     * stderr but do not throw, so a partial pool still yields a playable deck;
     * the assembled result is then validated by {@link #validateDeck}, which
     * throws on a short, unplayable or ambassador-less deck (B5-1714), so a
     * silent short deck can no longer reach the game (the B5-1517 class).
     */
    public static List<Card> build(Faction faction, List<Card> pool) throws IOException {
        if (faction == null) throw new IllegalArgumentException("faction is null");
        if (pool == null || pool.isEmpty()) throw new IllegalArgumentException("card pool is empty");

        Map<String, Card> byId = new HashMap<String, Card>();
        Map<String, Card> byTitle = new HashMap<String, Card>();
        for (int i = 0; i < pool.size(); i++) {
            Card c = pool.get(i);
            byId.put(c.getId(), c);
            if (!byTitle.containsKey(c.getTitle())) byTitle.put(c.getTitle(), c);
        }

        List<Card> deck = new ArrayList<Card>();
        Set<String> fixedIds = new HashSet<String>();
        Set<String> fixedTitles = new HashSet<String>();
        List<String> missing = new ArrayList<String>();
        int fixedCount = 0;

        List<Map<String, String>> entries = loadEntries();
        for (int i = 0; i < entries.size(); i++) {
            Map<String, String> e = entries.get(i);
            if (!faction.name().equals(e.get("deck"))) continue;
            String id = e.get("id");
            Card c = byId.get(id);
            if (c == null) {
                // Fixed lists carry Premiere ids; under the deduped pool the
                // slot may hold the Deluxe reprint — match by title instead.
                c = findFixedByTitle(byTitle, id);
            }
            if (c == null) { missing.add(id); continue; }
            int n = parseInt(e.get("count"), 1);
            for (int k = 0; k < n; k++) {
                deck.add(c);
                fixedCount++;
            }
            fixedIds.add(c.getId());
            fixedTitles.add(c.getTitle());
        }
        if (!missing.isEmpty()) {
            System.err.println("StarterDeckBuilder: " + faction
                + " fixed ids missing from pool: " + missing);
        }
        if (fixedCount != FIXED_TARGET) {
            System.err.println("StarterDeckBuilder: " + faction + " fixed count="
                + fixedCount + " (expected " + FIXED_TARGET + ")");
        }

        deck.addAll(drawRandomUncommonsRares(faction, pool, fixedIds));
        validateDeck(deck, faction);
        return deck;
    }

    /**
     * Validate a built starter deck against the faction minimums, throwing on
     * the first batch of violations (B5-1714). A starter deck must carry the
     * 50-fixed-plus-10-random structure: at least {@link #DECK_SIZE} cards,
     * every card playable by the faction, and the faction ambassador present
     * (the deck must seat one; the B5-0313 ambassador guarantee).
     *
     * @throws IllegalStateException naming every violation when the deck is
     *         null, short of {@link #DECK_SIZE}, holds a card the faction
     *         cannot play, or lacks the faction ambassador
     */
    public static void validateDeck(List<Card> deck, Faction faction) {
        if (faction == null) throw new IllegalArgumentException("faction is null");
        if (deck == null || deck.isEmpty()) {
            throw new IllegalStateException(
                "StarterDeckBuilder: starter deck for " + faction + " is null or empty");
        }
        List<String> problems = new ArrayList<String>();
        if (deck.size() < DECK_SIZE) {
            problems.add("size " + deck.size() + " < minimum " + DECK_SIZE);
        }
        int unplayable = 0;
        for (int i = 0; i < deck.size(); i++) {
            Card c = deck.get(i);
            if (c == null) {
                problems.add("null card at index " + i);
                continue;
            }
            if (!c.getFaction().isPlayableBy(faction)) unplayable++;
        }
        if (unplayable > 0) {
            problems.add(unplayable + " card(s) not playable by " + faction);
        }
        if (findAmbassador(deck, faction) == null) {
            problems.add("faction ambassador missing");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("StarterDeckBuilder: invalid " + faction
                + " starter deck: " + problems);
        }
    }

    /** The printed 10 random uncommons/rares for a faction (see class note). */
    private static List<Card> drawRandomUncommonsRares(Faction faction, List<Card> pool,
                                                       Set<String> fixedIds) {
        Set<String> fixedTitles = new HashSet<String>();
        for (int i = 0; i < pool.size(); i++) {
            Card c = pool.get(i);
            if (fixedIds.contains(c.getId())) fixedTitles.add(c.getTitle());
        }
        List<Card> candidates = new ArrayList<Card>();
        for (int i = 0; i < pool.size(); i++) {
            Card c = pool.get(i);
            Rarity r = c.getRarity();
            if (r != Rarity.UNCOMMON && r != Rarity.RARE) continue;
            if (!c.getFaction().isPlayableBy(faction)) continue;
            if (fixedTitles.contains(c.getTitle())) continue;
            candidates.add(c);
        }
        Random rng = (randomSeed != 0L)
            ? new Random(randomSeed + faction.ordinal())
            : new Random();
        Collections.shuffle(candidates, rng);

        List<Card> picked = new ArrayList<Card>();
        for (int i = 0; i < candidates.size() && picked.size() < RANDOM_COUNT; i++) {
            picked.add(candidates.get(i));
        }
        int idx = 0;
        while (picked.size() < RANDOM_COUNT && !candidates.isEmpty()) {
            picked.add(candidates.get(idx % candidates.size()));
            idx++;
        }
        return picked;
    }

    /** The race ambassador inside a deck list, or null (mirror of
     *  {@code GameController.findAmbassador}). */
    public static CharacterCard findAmbassador(List<Card> deckCards, Faction faction) {
        if (deckCards == null) return null;
        for (int i = 0; i < deckCards.size(); i++) {
            Card c = deckCards.get(i);
            if (c instanceof CharacterCard) {
                CharacterCard ch = (CharacterCard) c;
                if (ch.isAmbassador() && ch.getFaction() == faction) return ch;
            }
        }
        return null;
    }

    private static List<Map<String, String>> loadEntries() throws IOException {
        if (deckEntries == null) {
            deckEntries = DeckLoader.loadFlatObjects(DECK_RESOURCE);
        }
        return deckEntries;
    }

    /** Test helper: load fixed-list entries for a specific faction. */
    static List<Map<String, String>> loadEntriesForTest(Faction faction) throws IOException {
        List<Map<String, String>> all = loadEntries();
        List<Map<String, String>> filtered = new ArrayList<Map<String, String>>();
        for (int i = 0; i < all.size(); i++) {
            Map<String, String> e = all.get(i);
            if (faction.name().equals(e.get("deck"))) {
                filtered.add(e);
            }
        }
        return filtered;
    }

    /**
     * Resolve a fixed-list Premiere id to the pool card with the same title
     * (the Deluxe reprint under the deduped pool). Null if unresolvable.
     */
    private static Card findFixedByTitle(Map<String, Card> byTitle, String premiereId) {
        try {
            if (premiereTitles == null) {
                premiereTitles = new HashMap<String, String>();
                List<Card> premiere = DeckLoader.loadFromResource("/cards/premiere.json");
                for (int i = 0; i < premiere.size(); i++) {
                    Card c = premiere.get(i);
                    premiereTitles.put(c.getId(), c.getTitle());
                }
            }
        } catch (IOException e) {
            return null;
        }
        String title = premiereTitles.get(premiereId);
        if (title == null) return null;
        return byTitle.get(title);
    }

    private static int parseInt(String s, int def) {
        if (s == null) return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
