package b5ccg.ui;

import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.FleetCard;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.CardType;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.Rarity;

import java.util.ArrayList;
import java.util.List;

/**
 * B5-4143: headless regression test for the B5-1980 pool filter API that
 * B5-4131 landed on {@link DeckBuilderModel}.
 *
 * <p>Why this file exists: the six filter methods went in with no test of their
 * own -- only the ad-hoc probe B5-4131 ran by hand and the three DB checks in
 * the conformance suite. Six hunk-defining behaviours were therefore unpinned
 * at the moment the tree went green, and an edit to any one of them (a {@code >}
 * becoming a {@code >=}, a query becoming case-sensitive, a filter being applied
 * destructively to the source list) would have been invisible to every gate.
 *
 * <p>Covered contracts, each with the future edit it is meant to catch:
 * <ul>
 *   <li>every setter defaults to no constraint, and a fresh model's pool is
 *       exactly the faction-filtered source it always was;</li>
 *   <li>type, rarity, cost and query combine <em>conjunctively</em> (a filter
 *       that widens instead of narrowing is caught);</li>
 *   <li>the cost limit is inclusive at the bound;</li>
 *   <li>query matches title OR rules text, case-insensitively, and null/empty
 *       query both mean no constraint;</li>
 *   <li>a card with a null title and null rules text never throws, whatever the
 *       query -- this is the NPE a naive {@code getTitle().contains()} ships;</li>
 *   <li>{@code hasPoolFilters()} is true iff at least one constraint is set;</li>
 *   <li>filtering is non-destructive: clearing every constraint restores the
 *       unfiltered pool card-for-card, and switching faction widens it again;</li>
 *   <li>filtering never mutates the deck.</li>
 * </ul>
 *
 * <p>Zero AWT/Swing imports: safe under {@code -Djava.awt.headless=true}.
 * Runnable as {@code java -cp out b5ccg.ui.HeadlessDeckBuilderFilterTest}.
 * Exit codes: 0 all checks passed, 1 a check failed.
 *
 * <p>Java 6 only: explicit type arguments, no diamond, no lambdas, index loops.
 */
public class HeadlessDeckBuilderFilterTest {

    private static int checks = 0;
    private static int failed = 0;

    private static void check(String name, boolean condition) {
        checks++;
        if (condition) {
            System.out.println("PASS: " + name);
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    // ---- fixture -----------------------------------------------------------
    // Five cards, deliberately overlapping on every filter axis: two cost-3
    // cards of different types, one cost-5 card, one cost-0 card with a null
    // title and null text, and one card of a faction the model cannot run.

    private static Card character(String id, String title, Rarity rarity,
                                  Faction faction, String text, int cost) {
        CharacterCard c = new CharacterCard(id, title, "Ambassador", rarity,
                faction, CardSet.PREMIERE, "key-" + id, text,
                1, 1, 1, 1, false);
        c.setCost(cost);
        return c;
    }

    private static List<Card> fixture() {
        List<Card> cards = new ArrayList<Card>();
        // "Alpha Envoy": cost 3, COMMON, MINBARI (the model's faction).
        cards.add(character("t1", "Alpha Envoy", Rarity.COMMON,
                Faction.MINBARI, "Draw one card.", 3));
        // "Beta Scout": cost 5, RARE, NEUTRAL so playable by everyone.
        cards.add(character("t2", "Beta Scout", Rarity.RARE,
                Faction.NEUTRAL, "Gain 1 influence.", 5));
        // A FLEET so the type filter has something to select against; its text
        // carries the query term so the text half of query is exercised.
        FleetCard fleet = new FleetCard("t3", "Gamma Fleet", "War",
                Rarity.COMMON, Faction.ANY, CardSet.PREMIERE, "key-t3",
                "Alpha wing escort.", 4);
        fleet.setCost(3);
        cards.add(fleet);
        // Null title and null text: the NPE shape.
        cards.add(character("t4", null, Rarity.COMMON, Faction.MINBARI,
                null, 0));
        // Not playable by MINBARI: proves the faction filter still runs
        // underneath every other filter.
        cards.add(character("t5", "Narn Diplomat", Rarity.COMMON,
                Faction.NARN, "Treaty text.", 2));
        return cards;
    }

    private static boolean containsId(List<Card> cards, String id) {
        for (int i = 0; i < cards.size(); i++) {
            if (id.equals(cards.get(i).getId())) return true;
        }
        return false;
    }

    private static String ids(List<Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(cards.get(i).getId());
        }
        return sb.toString();
    }

    /** Asserts model.pool() holds exactly the given ids, in any order. */
    private static void checkPool(String name, DeckBuilderModel model, String expectedIds) {
        List<Card> pool = model.pool();
        boolean same = pool.size() == splitCount(expectedIds);
        if (same) {
            String[] want = expectedIds.split(",");
            for (int i = 0; i < want.length && same; i++) {
                if (!"".equals(want[i]) && !containsId(pool, want[i])) same = false;
            }
        }
        check(name + " -> [" + expectedIds + "] got [" + ids(pool) + "]", same);
    }

    private static int splitCount(String csv) {
        if (csv == null || csv.length() == 0) return 0;
        return csv.split(",").length;
    }

    private static void clearAll(DeckBuilderModel m) {
        m.setTypeFilter(null);
        m.setRarityFilter(null);
        m.clearCostLimit();
        m.setQuery(null);
    }

    public static void main(String[] args) {
        DeckBuilderModel m = new DeckBuilderModel(fixture(), Faction.MINBARI);

        // ---- defaults ------------------------------------------------------
        check("default: hasPoolFilters is false on a fresh model",
                !m.hasPoolFilters());
        checkPool("default: pool is the faction-filtered source (NARN t5 out)",
                m, "t1,t2,t3,t4");

        // ---- type filter ---------------------------------------------------
        m.setTypeFilter(CardType.CHARACTER);
        check("type: hasPoolFilters true once a constraint is set",
                m.hasPoolFilters());
        checkPool("type: CHARACTER narrows to characters", m, "t1,t2,t4");
        m.setTypeFilter(CardType.FLEET);
        checkPool("type: FLEET selects the fleet", m, "t3");
        m.setTypeFilter(null);
        checkPool("type: null clears the constraint", m, "t1,t2,t3,t4");

        // ---- rarity filter -------------------------------------------------
        m.setRarityFilter(Rarity.COMMON);
        checkPool("rarity: COMMON selects the commons", m, "t1,t3,t4");
        m.setRarityFilter(null);
        checkPool("rarity: null clears the constraint", m, "t1,t2,t3,t4");

        // ---- cost filter (inclusive at the bound) --------------------------
        m.setCostLimit(3);
        checkPool("cost: limit 3 is inclusive and drops the cost-5 card",
                m, "t1,t3,t4");
        m.setCostLimit(0);
        checkPool("cost: limit 0 keeps only the zero-cost card", m, "t4");
        m.clearCostLimit();
        checkPool("cost: clearCostLimit restores the pool", m, "t1,t2,t3,t4");

        // ---- query ----------------------------------------------------------
        m.setQuery("alpha");
        checkPool("query: lowercase matches title and rules text", m, "t1,t3");
        m.setQuery("ALPHA");
        checkPool("query: uppercase is case-insensitive into both fields",
                m, "t1,t3");
        m.setQuery("zzz");
        checkPool("query: a term nothing carries yields the empty pool", m, "");
        m.setQuery("");
        checkPool("query: empty string means no constraint", m, "t1,t2,t3,t4");
        m.setQuery(null);
        checkPool("query: null means no constraint", m, "t1,t2,t3,t4");
        check("query: hasPoolFilters false when only an empty query was set",
                !m.hasPoolFilters());

        // Every query ran over a card with a null title and null rules text
        // (t4). Reaching this line at all is the no-NPE evidence; assert it
        // explicitly so coverage is visible rather than implicit.
        m.setQuery("alpha");
        boolean noThrow = true;
        try {
            m.pool();
        } catch (Throwable t) {
            noThrow = false;
            System.out.println("  ( threw " + t.getClass().getName() + " )");
        }
        check("query: a null title and null text never throw", noThrow);
        clearAll(m);

        // ---- conjunction ----------------------------------------------------
        m.setTypeFilter(CardType.CHARACTER);
        m.setRarityFilter(Rarity.COMMON);
        checkPool("conjunction: type AND rarity is an intersection not a union",
                m, "t1,t4");
        m.setCostLimit(3);
        m.setQuery("alpha");
        checkPool("conjunction: all four constraints together", m, "t1");
        clearAll(m);
        checkPool("conjunction: clearing all four restores the pool",
                m, "t1,t2,t3,t4");

        // ---- non-destructive ------------------------------------------------
        // The source list is never narrowed at load time, so a faction switch
        // has something to widen from.
        m.addCard(m.sourceCards().get(0));
        m.setTypeFilter(CardType.FLEET);
        m.setQuery("alpha");
        checkPool("non-destructive: filters active", m, "t3");
        check("non-destructive: filtering never mutates the deck",
                m.deckSize() == 1 && "t1".equals(m.deck().get(0).getId()));
        clearAll(m);
        m.setFaction(Faction.NARN);
        checkPool("non-destructive: switching faction widens the pool (MINBARI out)",
                m, "t2,t3,t5");
        m.setFaction(Faction.MINBARI);
        checkPool("non-destructive: switching back recovers every card",
                m, "t1,t2,t3,t4");

        System.out.println();
        System.out.println("HEADLESS DECK BUILDER FILTER TEST: " + (checks - failed)
                + " of " + checks + " checks passed");
        if (failed > 0) {
            System.out.println(failed + " CHECK(S) FAILED");
            System.exit(1);
        }
        System.out.println("HEADLESS DECK BUILDER FILTER TEST PASSED");
        System.exit(0);
    }
}
