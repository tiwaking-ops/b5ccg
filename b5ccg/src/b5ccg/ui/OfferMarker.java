package b5ccg.ui;

/**
 * B5-2552 — one offer marker attached to an item the agent has offered to the
 * human player (an action or a card choice).
 *
 * <p>Implements the four-state lifecycle proposed in B5-2538
 * (`docs/proposals/2026-10-04-offer-marker-lifecycle-convention-proposal.md`
 * section 2) and implemented here as the first code that actually enforces it:
 *
 * <ul>
 *   <li>{@link #CREATED} — the marker exists, attached to the offered item,
 *       waiting for a human decision.</li>
 *   <li>{@link #ACCEPTED} — the human selected the offered item. Transient:
 *       the marker is retired as soon as the action is processed.</li>
 *   <li>{@link #DECLINED} — the human rejected the offer, or selected a
 *       different item. Transient: it lives only until the round concludes.</li>
 *   <li>{@link #EXPIRED} — the round boundary swept it. Terminal.</li>
 * </ul>
 *
 * <p>The marker is deliberately <b>immutable apart from its state</b>: it
 * carries the round it was created in, and that round is what the registry's
 * boundary sweep uses. A marker therefore knows nothing about wall-clock time,
 * which is what makes the lifecycle testable headlessly and makes "never
 * persists past its round" a structural property rather than a timing
 * accident.
 *
 * <p>Java 6 only: no enums (an int state with named constants keeps the
 * constant names readable at the call sites and matches the rest of the UI
 * package's idiom), no diamond, no lambdas.
 */
public final class OfferMarker {

    /** Offered and awaiting a human decision (B5-2538 section 2.1). */
    public static final int CREATED   = 0;
    /** The human selected the offered item (B5-2538 section 2.2). */
    public static final int ACCEPTED  = 1;
    /** The human rejected the offer, or picked something else (B5-2538 section 2.3). */
    public static final int DECLINED  = 2;
    /** Swept at the round boundary (B5-2538 section 2.4). */
    public static final int EXPIRED   = 3;

    private final String offerId;
    private final Object item;          // the offered Card/action, opaque to this class
    private final String label;         // what the UI paints on the marker
    private final int    createdRound;
    private int          state;

    /**
     * A marker always starts {@link #CREATED}: a marker that was born already
     * accepted would skip the state the human's decision actually produces.
     *
     * @param offerId     stable id for this offer, unique within its round
     * @param item        the offered item (typically a Card); opaque here
     * @param label       short text the UI paints on the marker
     * @param createdRound the round in which the offer was made
     */
    public OfferMarker(String offerId, Object item, String label, int createdRound) {
        if (offerId == null) {
            throw new IllegalArgumentException("offerId must not be null");
        }
        this.offerId     = offerId;
        this.item        = item;
        this.label       = (label == null) ? "" : label;
        this.createdRound = createdRound;
        this.state       = CREATED;
    }

    public String getOfferId()     { return offerId; }
    public Object getItem()        { return item; }
    public String getLabel()       { return label; }
    public int    getCreatedRound(){ return createdRound; }
    public int    getState()       { return state; }

    /**
     * True while the marker still denotes a live offer awaiting or resolving a
     * human decision. ACCEPTED is live (it is still on screen awaiting action
     * processing); DECLINED and EXPIRED are not.
     */
    public boolean isLive() {
        return state == CREATED || state == ACCEPTED;
    }

    /** True once the round boundary has swept this marker (terminal state). */
    public boolean isExpired() { return state == EXPIRED; }

    /** Package-private: only the registry drives transitions, never the UI. */
    void setState(int state) { this.state = state; }

    /** Human-readable state name, for probe output and tooltips. */
    public String getStateName() {
        switch (state) {
            case CREATED:  return "CREATED";
            case ACCEPTED: return "ACCEPTED";
            case DECLINED: return "DECLINED";
            case EXPIRED:  return "EXPIRED";
            default:       return "UNKNOWN";
        }
    }

    @Override
    public String toString() {
        return "OfferMarker[" + offerId + " r" + createdRound + " " + getStateName() + "]";
    }
}