package b5ccg.engine;

import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.enums.Faction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * B5-2501: load-time starter-deck composition validator.
 *
 * Verifies the five deck rules from the B5-0319 starter deck audit while the
 * deck is being assembled — before a deck can reach the game — by the five
 * checks the row names:
 *
 * <ol>
 *   <li><b>Exactly 60 cards.</b> The starter deck is a retail product of 60
 *       (50 fixed + 10 random uncommons/rares), rulebook :134, the row's
 *       wording — not the 45-card play-deck floor of :132, which
 *       {@link DeckLoader#validatePlayDeck} owns for custom decks and which is
 *       deliberately absent here.</li>
 *   <li><b>Ambassador present.</b> The deck must seat its own faction's
 *       ambassador (:134, :199, the B5-0313 guarantee). Presence, not count:
 *       :199 "must contain one Starting Ambassador" is this project's
 *       at-least-one reading (DeckLoader.java :195 note), and the ambassador
 *       pin's duplicate (B5-2177 F2) is inserted into the Deck AFTER build()
 *       returns, so it is outside this validator's input on purpose.</li>
 *   <li><b>No duplicate non-ambassador cards beyond allowance.</b> A
 *       non-ambassador title may appear only as many times as the deck
 *       definition declares for that faction (the fixed-list {@code count});
 *       titles the definition does not carry — the 10 random uncommons/rares —
 *       are allowed 1 each, which is the B5-0319 "distinct draw"
 *       interpretation logged in docs/DECISIONS.md. Counted by TITLE because
 *       the deduped pool resolves a fixed Premiere id to its Deluxe reprint
 *       (different id, same title), and the pool's dedup key is title
 *       (DeckLoader.loadBothSets). Ambassadors are skipped per the row's
 *       "non-ambassador" wording.</li>
 *   <li><b>Faction legality.</b> Every card must be playable by the deck's
 *       faction ({@link Faction#isPlayableBy}, B5-1965 — race-restricted
 *       fleets/locations/agendas plus characters).</li>
 *   <li><b>Character race matching.</b> Every character's race must be the
 *       deck's race or NEUTRAL (rulebook :205–206: eliminate cards that list
 *       a race different from your starting ambassador's).</li>
 * </ol>
 *
 * <p><b>Why checks 4 and 5 are separate and not one predicate.</b>
 * {@code isPlayableBy} accepts {@code ANY}, which is the right answer for
 * conflict and agenda cards — they genuinely carry no race. But {@code ANY} is
 * not a race a character can be loyal to, so check 4 passes a character record
 * whose race reads ANY while check 5 does not; the probe pins exactly that
 * mutant. Conversely a race-restricted NARN fleet in a HUMAN deck trips only
 * check 4 (it is not a character). An off-race character trips both, because
 * two rules are broken at once.
 *
 * <p>Returns every violation found, so a caller can present them together
 * (the {@link DeckLoader#validatePlayDeck} precedent); an empty list means the
 * composition is clean. All five checks run even after one fails, so the
 * message list is a full picture rather than a first error. A missing or
 * unusable input (null deck/faction, absent deck definition, unreadable
 * premiere.json id-to-title map) is itself reported as a violation — a check
 * that cannot run must never read as a check that passed.
 *
 * <p>Java 6 only — stdlib only.
 */
public final class DeckCompositionValidator {

    private DeckCompositionValidator() {}

    /**
     * Premiere id to title, loaded lazily once. The deck definition carries
     * Premiere ids while the deduped pool may hold Deluxe reprints of the
     * same card, and allowance math is per title, so the mapping is required
     * rather than optional. Mirrors (does not share) the private cache inside
     * {@link StarterDeckBuilder}.
     */
    private static Map<String, String> premiereIdToTitle = null;

    /** Non-null once the premiere.json map failed to load; never retried. */
    private static String premiereIdToTitleError = null;

    /** Package-private for the probe: the id-to-title map behind allowance. */
    static Map<String, String> premiereIdToTitle() {
        if (premiereIdToTitle == null) {
            premiereIdToTitle = new HashMap<String, String>();
            try {
                List<Card> premiere = DeckLoader.loadFromResource("/cards/premiere.json");
                for (int i = 0; i < premiere.size(); i++) {
                    Card c = premiere.get(i);
                    premiereIdToTitle.put(c.getId(), c.getTitle());
                }
                premiereIdToTitleError = null;
            } catch (IOException e) {
                premiereIdToTitleError = String.valueOf(e.getMessage());
            }
        }
        return premiereIdToTitle;
    }

    /**
     * Allowed copy count per title for one faction's deck definition: the sum
     * of {@code count} over this faction's rows whose Premiere id resolves to
     * that title. Titles absent from the definition are allowed 1 (the
     * distinct-random-draw rule) — callers apply that default when comparing;
     * this map carries only the declared allowances. Empty when the
     * definition cannot be read or the faction has no rows; the caller
     * reports that as a violation rather than defaulting to silence.
     *
     * <p>Public so the conformance probe selects targets and asserts the
     * allowance values it is testing against — one source of truth for the
     * allowance, not a second copy of the arithmetic.
     */
    public static Map<String, Integer> allowedCopies(Faction faction,
            List<Map<String, String>> definition) {
        Map<String, Integer> allowed = new HashMap<String, Integer>();
        if (faction == null || definition == null) return allowed;
        Map<String, String> idToTitle = premiereIdToTitle();
        for (int i = 0; i < definition.size(); i++) {
            Map<String, String> e = definition.get(i);
            if (e == null || !faction.name().equals(e.get("deck"))) continue;
            String title = idToTitle.get(e.get("id"));
            if (title == null) continue; // unresolvable id: shows up as a size defect
            int n = parseInt(e.get("count"), 1);
            Integer prev = allowed.get(title);
            allowed.put(title, Integer.valueOf(prev == null ? n : prev.intValue() + n));
        }
        return allowed;
    }

    /**
     * Validate a built starter deck's composition (B5-2501). Returns every
     * violation; empty means clean.
     *
     * @param deck       the assembled deck (StarterDeckBuilder.build output)
     * @param faction    the deck's faction
     * @param definition the starter-deck definition rows
     *                   ({@code StarterDeckBuilder.DECK_RESOURCE} via
     *                   {@code DeckLoader.loadFlatObjects}), needed for the
     *                   duplicate allowance
     */
    public static List<String> validate(List<Card> deck, Faction faction,
            List<Map<String, String>> definition) {
        List<String> problems = new ArrayList<String>();
        if (faction == null) {
            problems.add("faction is null");
            return problems;
        }
        if (deck == null || deck.isEmpty()) {
            problems.add("deck is null or empty");
            return problems;
        }

        // 1. Exactly 60 cards (:134 — 50 fixed + 10 random product size).
        if (deck.size() != StarterDeckBuilder.DECK_SIZE) {
            problems.add("deck size " + deck.size() + " != required "
                + StarterDeckBuilder.DECK_SIZE
                + " (50 fixed + 10 random, rulebook :134)");
        }

        // 2. The faction's own ambassador is present (:134/:199, B5-0313).
        if (StarterDeckBuilder.findAmbassador(deck, faction) == null) {
            problems.add("faction ambassador missing (:134/:199)");
        }

        // 3. No duplicate non-ambassador card beyond its allowance.
        addDuplicateProblems(problems, deck, faction, definition);

        // 4. Faction legality for every card (B5-1965).
        for (int i = 0; i < deck.size(); i++) {
            Card c = deck.get(i);
            if (c == null) {
                problems.add("null card at index " + i);
                continue;
            }
            if (!c.getFaction().isPlayableBy(faction)) {
                problems.add("card " + c.getId() + " (" + c.getTitle()
                    + ") not playable by faction " + faction
                    + " (card faction: " + c.getFaction() + ")");
            }
        }

        // 5. Character race matching (:205-206): characters only — see the
        //    class javadoc for why this is a separate pass from check 4.
        for (int i = 0; i < deck.size(); i++) {
            Card c = deck.get(i);
            if (!(c instanceof CharacterCard)) continue;
            Faction race = c.getFaction();
            if (race != faction && race != Faction.NEUTRAL) {
                problems.add("character " + c.getId() + " (" + c.getTitle()
                    + ") race " + race + " does not match deck race " + faction
                    + " (:205-206 character race matching)");
            }
        }

        return problems;
    }

    /** Check 3: count non-ambassador titles and compare with allowance. */
    private static void addDuplicateProblems(List<String> problems, List<Card> deck,
            Faction faction, List<Map<String, String>> definition) {
        if (definition == null) {
            problems.add("deck definition not provided: duplicate allowance unverifiable");
            return;
        }
        int rows = 0;
        for (int i = 0; i < definition.size(); i++) {
            Map<String, String> e = definition.get(i);
            if (e != null && faction.name().equals(e.get("deck"))) rows++;
        }
        if (rows == 0) {
            problems.add("no deck definition rows for faction " + faction
                + ": duplicate allowance unverifiable");
            return;
        }
        premiereIdToTitle(); // populate the cache before reading its error state
        if (premiereIdToTitleError != null) {
            problems.add("premiere.json id-to-title map unavailable ("
                + premiereIdToTitleError + "): duplicate allowance unverifiable");
            return;
        }

        Map<String, Integer> allowed = allowedCopies(faction, definition);
        Map<String, Integer> actual = new HashMap<String, Integer>();
        for (int i = 0; i < deck.size(); i++) {
            Card c = deck.get(i);
            if (c == null) continue;
            if (c instanceof CharacterCard && ((CharacterCard) c).isAmbassador()) {
                continue; // the row fences this rule to non-ambassador cards
            }
            String title = c.getTitle();
            Integer prev = actual.get(title);
            actual.put(title, Integer.valueOf(prev == null ? 1 : prev.intValue() + 1));
        }

        // Report in deck order so the message list is deterministic.
        Set<String> reported = new HashSet<String>();
        for (int i = 0; i < deck.size(); i++) {
            Card c = deck.get(i);
            if (c == null) continue;
            if (c instanceof CharacterCard && ((CharacterCard) c).isAmbassador()) continue;
            String title = c.getTitle();
            if (!reported.add(title)) continue;
            int n = actual.get(title).intValue();
            Integer a = allowed.get(title);
            int allow = (a == null) ? 1 : a.intValue(); // undeclared title: 1 (distinct draw)
            if (n > allow) {
                problems.add("card '" + title + "' appears " + n + " times; allowed "
                    + allow + " (non-ambassador duplicate beyond allowance)");
            }
        }
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
