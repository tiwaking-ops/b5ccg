package b5ccg.ui;

import b5ccg.engine.DeckLoader;
import b5ccg.model.Card;
import b5ccg.model.enums.CardType;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.Rarity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * B5-2287: the deck builder's state and legality rules, with no Swing in it.
 *
 * <p>This class exists because the deck builder had <em>no automated coverage at
 * all</em>. Every line of its behaviour -- which factions are offered, which cards
 * the pool shows, what add and remove do, when Construct is enabled -- lived
 * inside a {@code JDialog} subclass, and a {@code JDialog} cannot be constructed
 * headlessly, so {@code showDeckBuilderDialog} returns {@code null} before the
 * constructor is ever reached. That is a gate that cannot go red: the only way to
 * reach this logic was a human clicking it.
 *
 * <p>So the rules moved here and the dialog became a view. Nothing about the
 * behaviour changed in the move; the point is that it is now reachable from
 * {@code HeadlessConformanceTest} without a display.
 *
 * <p>The split is deliberately at the seam where the rulebook is: this class owns
 * <em>what the deck is and whether it is legal</em>, and the dialog owns only
 * pixels. The legality call itself is not reimplemented here -- it delegates to
 * {@link DeckLoader#validatePlayDeck(List, Faction)}, which was written under
 * B5-1965 and, before B5-2247 wired it to a production caller, had zero callers
 * tree-wide (B5-2183 finding L1). This class is that production caller's logic;
 * {@link MainWindow} holds the pixels.
 *
 * <p>Java 6 only: explicit type arguments, no diamond, no lambdas, index loops.
 */
public final class DeckBuilderModel {

    private final List<Card> sourceCards;
    private final List<Card> deckCards = new ArrayList<Card>();
    private Faction faction;

    // B5-1980 pool filters. All default to "no constraint", so pool() with
    // no filter set is exactly the faction-filtered pool it always was.
    private CardType typeFilter;
    private Rarity rarityFilter;
    private Integer costLimit;   // null = no cost constraint
    private String query = "";  // empty = no text constraint

    /**
     * @param sourceCards the unfiltered authored pool. Held unfiltered on
     *        purpose: the pool <em>view</em> is filtered by faction, but switching
     *        faction has to be able to widen it again, so the filter is applied per
     *        query rather than applied destructively at load time.
     * @param faction     the starting player faction, or null to take the first
     *        playable race.
     */
    public DeckBuilderModel(List<Card> sourceCards, Faction faction) {
        this.sourceCards = new ArrayList<Card>();
        if (sourceCards != null) {
            for (int i = 0; i < sourceCards.size(); i++) {
                Card c = sourceCards.get(i);
                if (c != null) this.sourceCards.add(c);
            }
        }
        this.faction = (faction != null) ? faction : playableFactions().get(0);
    }

    /**
     * Loads both shipped sets. Separate from the constructor so a test can inject
     * a fixture pool and never touch the classpath, which is the point of the
     * class: {@link DeckLoader#loadBothSets()} reads resources and throws
     * {@link IOException}, neither of which belongs in a unit test of list logic.
     */
    public static DeckBuilderModel fromShippedSets() throws IOException {
        return new DeckBuilderModel(DeckLoader.loadBothSets(), null);
    }

    /**
     * The factions a human may actually build for. {@code ANY} and {@code NEUTRAL}
     * are card-side values rather than races a human plays, so they are not
     * offered. {@code NON_ALIGNED} <em>is</em> offered: it is a real faction with a
     * real deck, and filtering on "is this interesting" rather than "is this a race
     * a human plays" would be a rule this class has no authority to invent.
     */
    public static List<Faction> playableFactions() {
        List<Faction> out = new ArrayList<Faction>();
        Faction[] all = Faction.values();
        for (int i = 0; i < all.length; i++) {
            if (all[i] != Faction.ANY && all[i] != Faction.NEUTRAL) out.add(all[i]);
        }
        return out;
    }

    /** The unfiltered authored pool, for a caller that wants the whole thing. */
    public List<Card> sourceCards() {
        return Collections.unmodifiableList(sourceCards);
    }

    public Faction faction() {
        return faction;
    }

    /**
     * Switches faction. The deck is NOT cleared: a player who picks the wrong
     * faction should get their cards back, and {@link #problems()} immediately
     * reports every card in the deck that the new faction may not run, which is
     * more useful than silently emptying their work.
     */
    public void setFaction(Faction f) {
        if (f != null) faction = f;
    }

    /**
     * The pool as this faction may play it: the faction's own cards, plus NEUTRAL,
     * plus ANY, further narrowed by the active B5-1980 filters. Null-faction
     * cards are excluded, matching what the dialog showed, because a card with no
     * faction cannot be reasoned about for playability. Filtering is applied per
     * query over the unfiltered source, never destructively, so clearing a filter
     * (or switching faction) widens the pool again.
     */
    public List<Card> pool() {
        List<Card> out = new ArrayList<Card>();
        Faction pf = faction;
        for (int i = 0; i < sourceCards.size(); i++) {
            Card c = sourceCards.get(i);
            if (c.getFaction() != null && c.getFaction().isPlayableBy(pf)
                    && passesFilters(c)) {
                out.add(c);
            }
        }
        return out;
    }

    // ---- B5-1980 pool filter API --------------------------------------------
    // The view pushes widget state in, pool() reflects it. Each setter accepts
    // null to mean "clear this constraint"; the deck and the faction are never
    // touched by filtering (a card already picked stays in the deck).

    /** Restricts the pool to one card type; null clears the constraint. */
    public void setTypeFilter(CardType t) {
        typeFilter = t;
    }

    /** Restricts the pool to one rarity; null clears the constraint. */
    public void setRarityFilter(Rarity r) {
        rarityFilter = r;
    }

    /** Restricts the pool to cards costing at most maxCost (inclusive). */
    public void setCostLimit(int maxCost) {
        costLimit = Integer.valueOf(maxCost);
    }

    /** Removes the cost constraint. */
    public void clearCostLimit() {
        costLimit = null;
    }

    /**
     * Restricts the pool to cards whose name or rules text contains q,
     * case-insensitively. Null and empty both mean "no text constraint".
     */
    public void setQuery(String q) {
        query = (q == null) ? "" : q;
    }

    /** True iff any pool filter is currently active. */
    public boolean hasPoolFilters() {
        return typeFilter != null || rarityFilter != null
                || costLimit != null || query.length() > 0;
    }

    /** Whether a single card survives the active filters. */
    private boolean passesFilters(Card c) {
        if (typeFilter != null && c.getType() != typeFilter) return false;
        if (rarityFilter != null && c.getRarity() != rarityFilter) return false;
        if (costLimit != null && c.getCost() > costLimit.intValue()) return false;
        if (query.length() > 0) {
            String q = query.toLowerCase();
            String title = c.getTitle();
            String text = c.getText();
            if (title != null && title.toLowerCase().contains(q)) return true;
            if (text != null && text.toLowerCase().contains(q)) return true;
            return false;
        }
        return true;
    }

    /** The deck under construction. Unmodifiable: mutate through the methods. */
    public List<Card> deck() {
        return Collections.unmodifiableList(deckCards);
    }

    public int deckSize() {
        return deckCards.size();
    }

    /**
     * Adds cards, ignoring nulls so a caller cannot inject a hole into the deck.
     * Copies are permitted: the max-3 rule is a legality check, not an add-time
     * restriction, so that an over-limit deck is reachable and therefore testable.
     */
    public void addCards(List<Card> cards) {
        if (cards == null) return;
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            if (c != null) deckCards.add(c);
        }
    }

    public void addCard(Card c) {
        if (c != null) deckCards.add(c);
    }

    /**
     * Removes by index, descending, so not-yet-removed indices stay valid. An
     * out-of-range index is ignored rather than thrown: the view layer passes
     * whatever Swing handed it, and a stale selection should not crash the dialog.
     */
    public void removeIndices(int[] indices) {
        if (indices == null) return;
        for (int i = indices.length - 1; i >= 0; i--) {
            int idx = indices[i];
            if (idx >= 0 && idx < deckCards.size()) deckCards.remove(idx);
        }
    }

    public void removeCard(Card c) {
        if (c != null) deckCards.remove(c);
    }

    public void clear() {
        deckCards.clear();
    }

    /**
     * The rulebook contract, delegated not reimplemented: the 45-card floor
     * (rulebook :128/:193), one Starting Ambassador (:195), max 3 copies (:194)
     * with its FIXED exemption, and faction playability. Empty means legal.
     */
    public List<String> problems() {
        return DeckLoader.validatePlayDeck(new ArrayList<Card>(deckCards), faction);
    }

    /** The single gate the Construct button hangs off. */
    public boolean isLegal() {
        return problems().isEmpty();
    }

    /**
     * What Construct hands the engine. A copy, so a later mutation of this model
     * cannot retroactively change a deck that was already handed over.
     *
     * <p>Callers must still check {@link #isLegal()} first: this returns whatever
     * is there, exactly as the dialog's own Construct handler did.
     */
    public List<Card> constructedDeck() {
        return new ArrayList<Card>(deckCards);
    }
}
