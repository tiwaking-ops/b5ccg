package b5ccg.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * B5-2552 — lifecycle owner for offer markers (B5-2538 section 2).
 *
 * <p>This is the class that makes the B5-2538 invariant mechanical rather
 * than aspirational: <b>no marker persists past the round in which it was
 * created</b>. Every transition funnels through this registry, and the
 * round-boundary sweep is the only thing that can move a marker to
 * {@link OfferMarker#EXPIRED}.
 *
 * <p>Why the sweep is driven by an explicit round number rather than by
 * polling: the UI does not own the turn clock — {@code GameState} does — and
 * the UI layer is not allowed to reach into it. The UI is handed the current
 * round on every refresh ({@link #enterRound}) and the registry expires
 * everything left over from an earlier round at that moment. That is the same
 * pattern the engine already uses for attached aftermaths
 * ({@code GameState.advanceRound()}) and for bonus expiries
 * ({@code Player.sweepBonusExpiries(round)}): the boundary is a named event,
 * not a timer.
 *
 * <p>Java 6 only. No diamond, no lambdas, no concurrent collections — UI state
 * is touched on the EDT and read by a headless probe on the main thread, and
 * this class is deliberately confined to single-threaded use per call site.
 */
public final class OfferMarkerRegistry {

    private final List<OfferMarker> markers = new ArrayList<OfferMarker>();
    private int currentRound = 1;

    /** The round this registry is currently displaying markers for. */
    public int getCurrentRound() { return currentRound; }

    /**
     * Called by the UI on every refresh, carrying the round number the game
     * is now in. Expiring on this call is what makes the boundary the sweep
     * point: by the time the UI paints round N+1, every marker from round N
     * is already gone.
     *
     * <p>Going backwards is a no-op rather than a sweep, so a stale refresh
     * arriving out of order cannot resurrect or prematurely expire markers.
     *
     * @return the number of markers this call expired
     */
    public int enterRound(int round) {
        if (round <= currentRound) return 0;
        currentRound = round;
        return expireMarkersBefore(round);
    }

    /**
     * Records a new offer, putting its marker into {@link OfferMarker#CREATED}.
     *
     * <p>A marker from an earlier round is expired first, so an offer made
     * after a boundary can never coexist with a stale one from the round it
     * just replaced.
     *
     * @return the created marker
     */
    public OfferMarker offer(String offerId, Object item, String label) {
        expireMarkersBefore(currentRound);
        OfferMarker marker = new OfferMarker(offerId, item, label, currentRound);
        markers.add(marker);
        return marker;
    }

    /**
     * The human selected the offered item → {@link OfferMarker#ACCEPTED}.
     * Only a CREATED marker may be accepted, and only by its own id, so a
     * stale click on a marker that already resolved is refused rather than
     * reviving it.
     *
     * @return true when the transition happened
     */
    public boolean accept(String offerId) {
        OfferMarker marker = find(offerId);
        if (marker == null || marker.getState() != OfferMarker.CREATED) return false;
        marker.setState(OfferMarker.ACCEPTED);
        return true;
    }

    /**
     * The human rejected the offer, or picked a different item →
     * {@link OfferMarker#DECLINED}. A declined marker stays visible (the
     * proposal's transient rejection readout, section 2.3) but is no longer
     * live, and it is removed at the round boundary by the sweep.
     *
     * @return true when the transition happened
     */
    public boolean decline(String offerId) {
        OfferMarker marker = find(offerId);
        if (marker == null || marker.getState() != OfferMarker.CREATED) return false;
        marker.setState(OfferMarker.DECLINED);
        return true;
    }

    /**
     * Drops an ACCEPTED marker once the accepted action has been processed
     * (B5-2538 section 2.2 "post-state: marker is removed as the action is no
     * longer offered but executed"). This is the fast path — it does not wait
     * for the round to end. Only an accepted marker may retire early.
     *
     * @return true when the marker was removed
     */
    public boolean retire(String offerId) {
        OfferMarker marker = find(offerId);
        if (marker == null || marker.getState() != OfferMarker.ACCEPTED) return false;
        markers.remove(marker);
        return true;
    }
    /** Number of markers currently held, live or not. */
    public int size() { return markers.size(); }

    /** Every marker currently held, in creation order. */
    public List<OfferMarker> getMarkers() {
        return Collections.unmodifiableList(new ArrayList<OfferMarker>(markers));
    }

    /** Markers still denoting a live offer (CREATED or ACCEPTED). */
    public List<OfferMarker> getLiveMarkers() {
        List<OfferMarker> live = new ArrayList<OfferMarker>();
        for (int i = 0; i < markers.size(); i++) {
            if (markers.get(i).isLive()) live.add(markers.get(i));
        }
        return Collections.unmodifiableList(live);
    }

    /** The marker for an offer id, or null when there is none. */
    public OfferMarker get(String offerId) { return find(offerId); }

    /**
     * The live marker attached to a given item, or null. This is the lookup
     * the UI paints from: a card may be offered at most once per round, so
     * "which marker is on this card" is a single-value question.
     */
    public OfferMarker markerForItem(Object item) {
        for (int i = 0; i < markers.size(); i++) {
            OfferMarker marker = markers.get(i);
            if (marker.isLive() && marker.getItem() == item) return marker;
        }
        return null;
    }

    /** Drops every marker, live or not. Used by the probe between scenarios. */
    public void clear() { markers.clear(); }

    private OfferMarker find(String offerId) {
        if (offerId == null) return null;
        for (int i = 0; i < markers.size(); i++) {
            if (offerId.equals(markers.get(i).getOfferId())) return markers.get(i);
        }
        return null;
    }

    /**
     * The B5-2538 invariant, in one place: every marker created strictly
     * before {@code round} is expired and removed. Markers created in
     * {@code round} itself are untouched — a marker lives to the END of the
     * round it was created in, not past it.
     */
    private int expireMarkersBefore(int round) {
        int expired = 0;
        for (int i = markers.size() - 1; i >= 0; i--) {
            OfferMarker marker = markers.get(i);
            if (marker.getCreatedRound() < round) {
                marker.setState(OfferMarker.EXPIRED);
                markers.remove(i);
                expired++;
            }
        }
        return expired;
    }
}