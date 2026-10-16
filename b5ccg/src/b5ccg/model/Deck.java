package b5ccg.model;

import java.util.*;

public class Deck {
    private final LinkedList<Card> drawPile    = new LinkedList<Card>();
    private final List<Card>       discardPile = new ArrayList<Card>();

    public Deck(List<Card> cards) {
        drawPile.addAll(cards);
        shuffle();
    }

    public void shuffle() {
        Collections.shuffle(drawPile);
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

    /** Shuffle discard pile back into draw pile. */
    public void recycleDiscard() {
        drawPile.addAll(discardPile);
        discardPile.clear();
        Collections.shuffle(drawPile);
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
