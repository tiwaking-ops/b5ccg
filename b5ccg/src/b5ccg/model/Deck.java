package b5ccg.model;

import java.util.*;

public class Deck {
    private final LinkedList<Card> drawPile    = new LinkedList<Card>();
    private final List<Card>       discardPile = new ArrayList<Card>();
    /** B5-0008 (WP-07/A5): per-deck shuffle source. null = legacy unseeded
     *  Collections.shuffle, so every existing unseeded caller is unchanged. */
    private Random rng = null;

    public Deck(List<Card> cards) {
        drawPile.addAll(cards);
        shuffle();
    }

    /** B5-0008: seeded constructor — same deal order for the same seed.
     *  Derivation convention: seed + 1_000_000L * (seatIndex + 1), so deck
     *  streams never collide with AIPlayer streams (seed + i * 1000L,
     *  B5-0349 precedent) or StarterDeckBuilder's (randomSeed + faction
     *  ordinal). */
    public Deck(List<Card> cards, long seed) {
        rng = new Random(seed);
        drawPile.addAll(cards);
        shuffle();
    }

    /** B5-0008: (re)seed this deck's shuffle source and re-shuffle the draw
     *  pile, for harnesses that build a deck before choosing a seed. */
    public void setSeed(long seed) {
        rng = new Random(seed);
        shuffle();
    }

    public void shuffle() {
        if (rng != null) Collections.shuffle(drawPile, rng);
        else             Collections.shuffle(drawPile);
    }

    public Card draw() {
        return drawPile.isEmpty() ? null : drawPile.removeFirst();
    }

    public List<Card> draw(int n) {
        List<Card> hand = new ArrayList<Card>();
        for (int i = 0; i < n && !drawPile.isEmpty(); i++) {
            hand.add(drawPile.removeFirst());
        }
        return hand;
    }

    public void discard(Card c) {
        discardPile.add(c);
    }

    public void discardAll(Collection<Card> cards) {
        discardPile.addAll(cards);
    }

    /** Shuffle discard pile back into draw pile. B5-0008: uses the deck's
     *  shuffle source, so a seeded deck recycles deterministically too. */
    public void recycleDiscard() {
        drawPile.addAll(discardPile);
        discardPile.clear();
        shuffle();
    }

    /** Peek at the top N cards without removing them. */
    public List<Card> peekTop(int n) {
        List<Card> result = new ArrayList<Card>();
        Iterator<Card> it = drawPile.iterator();
        for (int i = 0; i < n && it.hasNext(); i++) result.add(it.next());
        return result;
    }

    public void addToBottom(Card c)  { drawPile.addLast(c); }
    public void addToTop(Card c)     { drawPile.addFirst(c); }

    public int              size()        { return drawPile.size(); }
    public boolean          isEmpty()     { return drawPile.isEmpty(); }
    public List<Card>       getDiscardPile() { return Collections.unmodifiableList(discardPile); }
}
