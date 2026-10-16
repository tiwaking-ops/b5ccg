package b5ccg.engine;
import b5ccg.model.enums.Loyalty;

import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.FleetCard;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Race;
import b5ccg.model.enums.Rarity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * B5-2501: conformance probe for {@link DeckCompositionValidator}.
 *
 * Harness only. Proves the five load-time deck rules fire — and fire on the
 * right input — with a pure 0/1 exit contract (non-zero means a real defect
 * in the validator or the deck data, never an exploratory outcome), in the
 * house style of the headless gate tier (B5-0956).
 *
 * Three layers of evidence, cheapest first:
 * <ol>
 *   <li><b>Clean path:</b> four live StarterDeckBuilder builds (seeded, so
 *       the draw is reproducible) must come out of the load-time gate with
 *       zero violations, with sizes and denominators printed.</li>
 *   <li><b>Wiring:</b> build() itself must throw the composition gate's
 *       message when the deck really is malformed — proven by handing the
 *       builder a pool short one fixed card's title, which produces a
 *       59-card deck. This is what separates "the validator is correct" from
 *       "the validator runs at load time".</li>
 *   <li><b>Non-vacuity:</b> six mutant decks, each engineered to violate
 *       exactly ONE rule, so a check that never fires can never pass this
 *       probe (a test never observed red is not evidence).</li>
 * </ol>
 *
 * Not wired into compile.sh's RUN_TESTS gate tier: which classes gate is a
 * per-class decision owned by B5-0956 (see B5-2515 finding one), and this
 * row's scope is engine/ source only. Run it on demand.
 *
 * Run after compile.bat / compile.sh:
 *   java -cp b5ccg/out b5ccg.engine.HeadlessDeckValidationProbe
 *
 * Java 6 only — stdlib only.
 */
public class HeadlessDeckValidationProbe {

    private static int checks;
    private static int failures;

    private static void check(String label, boolean passed) {
        checks++;
        System.out.println("  [DECK-VALIDATION] " + label + ": "
                + (passed ? "PASS" : "FAIL"));
        if (!passed) failures++;
    }

    private static int countContaining(List<String> problems, String token) {
        int n = 0;
        for (int i = 0; i < problems.size(); i++) {
            if (problems.get(i).indexOf(token) >= 0) n++;
        }
        return n;
    }

    private static String join(List<String> problems) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < problems.size(); i++) {
            if (i > 0) sb.append(" | ");
            sb.append(problems.get(i));
        }
        return sb.toString();
    }

    private static int distinctTitles(List<Card> deck) {
        Set<String> titles = new HashSet<String>();
        for (int i = 0; i < deck.size(); i++) titles.add(deck.get(i).getTitle());
        return titles.size();
    }

    private static int countTitle(List<Card> deck, String title) {
        int n = 0;
        for (int i = 0; i < deck.size(); i++) {
            if (deck.get(i).getTitle().equals(title)) n++;
        }
        return n;
    }

    /** Every pool card whose title matches, so a title is gone for good. */
    private static List<Card> poolWithoutTitle(List<Card> pool, String title) {
        List<Card> out = new ArrayList<Card>();
        for (int i = 0; i < pool.size(); i++) {
            if (!pool.get(i).getTitle().equals(title)) out.add(pool.get(i));
        }
        return out;
    }

    /** First non-ambassador HUMAN fixed card's title (definition file order). */
    private static String firstFixedNonAmbassadorTitle(List<Card> pool,
            List<Map<String, String>> definition) {
        for (int i = 0; i < definition.size(); i++) {
            Map<String, String> e = definition.get(i);
            if (e == null || !"HUMAN".equals(e.get("deck"))) continue;
            String title = DeckCompositionValidator.premiereIdToTitle().get(e.get("id"));
            if (title == null) continue;
            for (int j = 0; j < pool.size(); j++) {
                Card c = pool.get(j);
                if (!c.getTitle().equals(title)) continue;
                if (c instanceof CharacterCard && ((CharacterCard) c).isAmbassador()) break;
                return title;
            }
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        Race[] FACTIONS = {
            Race.HUMAN, Race.CENTAURI, Race.MINBARI, Race.NARN
        };

        List<Card> pool = DeckLoader.loadBothSets();
        List<Map<String, String>> definition =
            DeckLoader.loadFlatObjects(StarterDeckBuilder.DECK_RESOURCE);
        // B5-2083: without a seed the builder's Random() makes every run a
        // different deck; 42 makes this probe's evidence reproducible.
        StarterDeckBuilder.setRandomSeed(42L);

        System.out.println("=== B5-2501 deck composition validation ===");
        System.out.println("pool=" + pool.size() + " card records, definition="
                + definition.size() + " rows");

        // ---- Layer 1: clean path through the load-time gate ----------------
        System.out.println("-- live builds (the gate runs inside build) --");
        List<Card> human = null;
        for (int i = 0; i < FACTIONS.length; i++) {
            Race f = FACTIONS[i];
            List<Card> deck = StarterDeckBuilder.build(f, pool);
            if (f == Race.HUMAN) human = deck;
            List<String> problems =
                DeckCompositionValidator.validate(deck, f, definition);
            check(f + " live build passes all five composition rules",
                    problems.isEmpty());
            if (!problems.isEmpty()) System.out.println("      -> " + join(problems));
            Card amb = StarterDeckBuilder.findAmbassador(deck, f);
            check(f + " live build is exactly 60 cards (actual " + deck.size() + ")",
                    deck.size() == 60);
            check(f + " live build seats its ambassador", amb != null);
            System.out.println("  [DECK-VALIDATION]   " + f + " deck: size="
                    + deck.size() + "/60 distinctTitles=" + distinctTitles(deck)
                    + "/60 ambassador=" + (amb == null ? "NONE" : amb.getTitle()));
        }

        // Allowance control: a fixed card allowed >= 2 copies must sit AT its
        // allowance in a clean deck without flagging — proof the allowance is
        // an allowance and not a de-facto "no duplicates ever" rule.
        Map<String, Integer> allowedHuman = DeckCompositionValidator
                .allowedCopies(Race.HUMAN, definition);
        String multiTitle = null;
        int multiAllowed = 0;
        for (Map.Entry<String, Integer> e : new TreeMap<String, Integer>(allowedHuman).entrySet()) {
            if (e.getValue().intValue() >= 2 && multiTitle == null) {
                multiTitle = e.getKey();
                multiAllowed = e.getValue().intValue();
            }
        }
        int multiActual = (multiTitle == null) ? -1 : countTitle(human, multiTitle);
        check("allowance control: '" + multiTitle + "' declared x" + multiAllowed
                + " appears " + multiActual + " time(s) in the clean deck and is not flagged",
                multiTitle != null && multiActual == multiAllowed);

        // ---- Layer 2: build() really fires the gate -------------------------
        System.out.println("-- wiring: build() throws when the composition fails --");
        String fixedTitle = firstFixedNonAmbassadorTitle(pool, definition);
        check("fixture target resolved (first non-ambassador HUMAN fixed card)",
                fixedTitle != null);
        List<Card> shortPool = poolWithoutTitle(pool, fixedTitle == null ? "" : fixedTitle);
        check("fixture pool is short the titled cards (pool "
                + pool.size() + " -> " + shortPool.size() + ")",
                shortPool.size() < pool.size());
        boolean threw = false;
        String msg = null;
        try {
            StarterDeckBuilder.build(Race.HUMAN, shortPool);
        } catch (IllegalStateException e) {
            threw = true;
            msg = String.valueOf(e.getMessage());
        }
        if (msg != null) System.out.println("  [DECK-VALIDATION]   thrown: " + msg);
        check("build() refuses the 59-card deck at load time via the composition gate",
                threw && msg != null
                    && msg.indexOf("deck failed composition validation") >= 0
                    && msg.indexOf("deck size") >= 0);

        StarterDeckBuilder.setRandomSeed(0L); // restore the default draw

        // ---- Layer 3: mutants, each violating exactly one rule -------------
        System.out.println("-- mutants (each must fire exactly its own rule) --");

        // M1: 59 cards — drop a random (index 59; randoms are never ambassadors).
        List<Card> m1 = new ArrayList<Card>(human);
        m1.remove(m1.size() - 1);
        List<String> p1 = DeckCompositionValidator.validate(m1, Race.HUMAN, definition);
        check("M1 (59 cards) fires ONLY the size rule [" + join(p1) + "]",
                p1.size() == 1 && countContaining(p1, "deck size") == 1);

        // M2: ambassador removed, replaced by a neutral fleet (keeps 60, keeps
        // everything else legal) — only the ambassador rule may fire.
        List<Card> m2 = new ArrayList<Card>(human);
        m2.remove(StarterDeckBuilder.findAmbassador(human, Race.HUMAN));
        m2.add(new FleetCard("probe_b52501_neutral_fleet",
                "Probe B52501 Neutral Fleet", "FLEET_NEUTRAL", Rarity.COMMON,
                Loyalty.NEUTRAL, CardSet.PREMIERE, "b52501", "", 1));
        List<String> p2 = DeckCompositionValidator.validate(m2, Race.HUMAN, definition);
        check("M2 (ambassador removed) fires ONLY the ambassador rule [" + join(p2) + "]",
                p2.size() == 1 && countContaining(p2, "ambassador missing") == 1);

        // M3: a second copy of a declared x1 card, replacing a random — only
        // the duplicate rule may fire.
        Card dupTarget = null;
        for (int i = 0; i < human.size(); i++) {
            Card c = human.get(i);
            if (c instanceof CharacterCard && ((CharacterCard) c).isAmbassador()) continue;
            Integer a = allowedHuman.get(c.getTitle());
            if (a == null || a.intValue() == 1) { dupTarget = c; break; }
        }
        List<Card> m3 = new ArrayList<Card>(human);
        m3.add(dupTarget);
        m3.remove(m3.size() - 2); // the pre-existing random just before the appended copy
        List<String> p3 = DeckCompositionValidator.validate(m3, Race.HUMAN, definition);
        check("M3 (x1 card duplicated) fires ONLY the duplicate rule ["
                + join(p3) + "]", p3.size() == 1
                && countContaining(p3, "beyond allowance") == 1
                && (dupTarget == null || countContaining(p3, dupTarget.getTitle()) == 1));

        // M4: a race-restricted NARN fleet in a HUMAN deck — only the faction
        // legality rule (it is not a character, so race matching must stay quiet).
        FleetCard narnFleet = null;
        for (int i = 0; i < pool.size(); i++) {
            Card c = pool.get(i);
            if (c instanceof FleetCard && c.getLoyalty() == Loyalty.NARN) {
                narnFleet = (FleetCard) c;
                break;
            }
        }
        check("fixture: real NARN fleet card found", narnFleet != null);
        List<Card> m4 = new ArrayList<Card>(human);
        m4.add(narnFleet);
        m4.remove(m4.size() - 2);
        List<String> p4 = DeckCompositionValidator.validate(m4, Race.HUMAN, definition);
        check("M4 (NARN fleet in HUMAN deck) fires ONLY faction legality ["
                + join(p4) + "]", p4.size() == 1
                && countContaining(p4, "not playable by faction") == 1
                && countContaining(p4, "does not match deck race") == 0);

        // M5: a character whose race reads ANY — playable by everyone (check 4
        // passes it), but ANY is not a race a character can be loyal to, so
        // ONLY the character race rule may fire. This is the mutant that keeps
        // checks 4 and 5 from collapsing into one predicate.
        CharacterCard anyChar = new CharacterCard("probe_b52501_any_character",
                "Probe B52501 ANY Character", "CHARACTER_ANY", Rarity.RARE,
                Loyalty.ANY, CardSet.PREMIERE, "b52501", "", 1, 1, 0, 2, false);
        List<Card> m5 = new ArrayList<Card>(human);
        m5.add(anyChar);
        m5.remove(m5.size() - 2);
        List<String> p5 = DeckCompositionValidator.validate(m5, Race.HUMAN, definition);
        check("M5 (character race ANY) fires ONLY character race matching ["
                + join(p5) + "]", p5.size() == 1
                && countContaining(p5, "does not match deck race") == 1
                && countContaining(p5, "not playable by faction") == 0);

        // M6: an off-race NARN character — two rules are broken at once, so
        // both check 4 and check 5 must fire and nothing else may.
        CharacterCard narnChar = null;
        for (int i = 0; i < pool.size(); i++) {
            Card c = pool.get(i);
            if (c instanceof CharacterCard && c.getLoyalty() == Loyalty.NARN
                    && !((CharacterCard) c).isAmbassador()) {
                narnChar = (CharacterCard) c;
                break;
            }
        }
        check("fixture: real non-ambassador NARN character card found", narnChar != null);
        List<Card> m6 = new ArrayList<Card>(human);
        m6.add(narnChar);
        m6.remove(m6.size() - 2);
        List<String> p6 = DeckCompositionValidator.validate(m6, Race.HUMAN, definition);
        check("M6 (NARN character in HUMAN deck) fires legality AND race, nothing else ["
                + join(p6) + "]", p6.size() == 2
                && countContaining(p6, "not playable by faction") == 1
                && countContaining(p6, "does not match deck race") == 1);

        System.out.println("HeadlessDeckValidationProbe: " + checks + " checks, "
                + failures + " failures.");
        System.exit(failures == 0 ? 0 : 1);
    }
}
