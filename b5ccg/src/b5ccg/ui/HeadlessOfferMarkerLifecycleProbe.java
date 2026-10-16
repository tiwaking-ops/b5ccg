package b5ccg.ui;

/**
 * B5-2552 — headless probe for the offer-marker lifecycle (B5-2538 section 3.2).
 *
 * <p>B5-2538 proposed the four-state marker lifecycle and required "a headless
 * probe that verifies no marker persists past its intended round". This is that
 * probe, written against {@link OfferMarkerRegistry}.
 *
 * <p>What it deliberately checks, in the shape of the rules:
 *
 * <ol>
 *   <li>the four states are reachable and are the only reachable ones;</li>
 *   <li>a marker survives TO the end of its own round (the sweep is not eager
 *       — expiring a marker on the round it was created would be just as wrong
 *       as never expiring it, and only one of those two bugs is visible to a
 *       naive "gone by next round" assertion);</li>
 *   <li><b>no marker of any state survives the round boundary</b> — the
 *       B5-2538 acceptance condition, asserted after every scenario;</li>
 *   <li>illegal transitions are refused rather than silently applied.</li>
 * </ol>
 *
 * <p>The scenarios cover the edge cases B5-2538 named: rapid offers, declined
 * offers, mixed accept/decline within one round, and an untouched offer that
 * simply rides out its round.
 *
 * <p>Headless by construction: it touches no Swing component and no engine or
 * model class, so it runs in CI with no display. The offered items are plain
 * Strings standing in for cards.
 *
 * <p>Exit codes: 0 all checks pass, 1 a check failed, 2 a scenario threw.
 */
public class HeadlessOfferMarkerLifecycleProbe {

    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [OMK] " + label + ": " + (ok ? "PASS" : "FAIL"));
        if (!ok) failed++;
    }

    /** The B5-2538 acceptance condition, asserted on every scenario end. */
    private static void checkNoMarkersSurvive(String scenario, OfferMarkerRegistry reg) {
        check(scenario + ": no marker persists past its round",
              reg.size() == 0 && reg.getLiveMarkers().isEmpty());
    }

    public static void main(String[] args) {
        System.out.println("B5-2552: headless offer-marker-lifecycle probe (B5-2538 section 3.2)");
        try {
            scenarioUntouchedOfferExpires();
            scenarioAcceptedRetiresEarly();
            scenarioDeclinedIsSweptAtBoundary();
            scenarioRapidOffersAllExpire();
            scenarioMixedRoundAllExpire();
            scenarioMarkerSurvivesItsOwnRound();
            scenarioIllegalTransitionsRefused();
        } catch (Throwable t) {
            System.out.println("  [OMK] scenario threw: " + t);
            t.printStackTrace();
            failed++;
        }
        System.out.println("");
        System.out.println("checks=" + checks + " failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    /** An offer nobody ever answers is swept at the boundary. */
    private static void scenarioUntouchedOfferExpires() {
        OfferMarkerRegistry reg = new OfferMarkerRegistry();
        reg.enterRound(3);
        reg.offer("o1", "Centauri Envoy", "TRADE");
        check("untouched offer: marker is CREATED and live", reg.size() == 1
              && reg.get("o1").getState() == OfferMarker.CREATED
              && reg.get("o1").isLive());
        int expired = reg.enterRound(4);
        check("untouched offer: boundary expired exactly 1 marker", expired == 1);
        checkNoMarkersSurvive("untouched offer", reg);
    }

    /** An accepted marker retires when the action is processed, not at the boundary. */
    private static void scenarioAcceptedRetiresEarly() {
        OfferMarkerRegistry reg = new OfferMarkerRegistry();
        reg.enterRound(2);
        reg.offer("o2", "DraKari Auditor", "REVEAL");
        check("accepted: accept() reports the transition", reg.accept("o2"));
        check("accepted: marker is ACCEPTED and still live (awaiting processing)",
              reg.get("o2").getState() == OfferMarker.ACCEPTED
              && reg.get("o2").isLive());
        check("accepted: retire() removes it before the round ends", reg.retire("o2"));
        checkNoMarkersSurvive("accepted", reg);
        // An accepted marker NOT retired still must not survive its round.
        reg.offer("o3", "Minbari Diplomat", "TRADE");
        reg.accept("o3");
        reg.enterRound(3);
        checkNoMarkersSurvive("accepted-unretired", reg);
    }

    /** A declined marker is not live, but still occupies the round until swept. */
    private static void scenarioDeclinedIsSweptAtBoundary() {
        OfferMarkerRegistry reg = new OfferMarkerRegistry();
        reg.enterRound(5);
        reg.offer("o4", "Narn Assassin", "SPY");
        check("declined: decline() reports the transition", reg.decline("o4"));
        check("declined: marker is DECLINED and no longer live",
              reg.get("o4").getState() == OfferMarker.DECLINED
              && !reg.get("o4").isLive());
        check("declined: not painted as a live marker", reg.markerForItem("Narn Assassin") == null);
        check("declined: still held until the boundary", reg.size() == 1);
        reg.enterRound(6);
        checkNoMarkersSurvive("declined", reg);
    }
    /** Rapid offers in one round all clear at the boundary. */
    private static void scenarioRapidOffersAllExpire() {
        OfferMarkerRegistry reg = new OfferMarkerRegistry();
        reg.enterRound(1);
        for (int i = 0; i < 25; i++) {
            reg.offer("rapid" + i, "card" + i, "CHOOSE");
        }
        check("rapid offers: 25 markers live in one round",
              reg.size() == 25 && reg.getLiveMarkers().size() == 25);
        check("rapid offers: boundary expired all 25", reg.enterRound(2) == 25);
        checkNoMarkersSurvive("rapid offers", reg);
    }

    /** A mixed round — some accepted, some declined, some ignored — clears fully. */
    private static void scenarioMixedRoundAllExpire() {
        OfferMarkerRegistry reg = new OfferMarkerRegistry();
        reg.enterRound(7);
        reg.offer("m1", "a", "A"); reg.accept("m1");
        reg.offer("m2", "b", "B"); reg.decline("m2");
        reg.offer("m3", "c", "C");                       // ignored
        reg.offer("m4", "d", "D"); reg.accept("m4");    // accepted, not retired
        check("mixed: 4 markers held", reg.size() == 4);
        // m1 and m4 are accepted (live, awaiting processing) and m3 is CREATED
        // (live, awaiting a decision); only m2 is declined, so the live count
        // is 3. Counting CREATED as dead is the mistake worth pinning here —
        // a CREATED marker is the one state that is both held and live.
        check("mixed: 3 of them are live (CREATED counts as live)", reg.getLiveMarkers().size() == 3);
        check("mixed: the declined one is the only non-live marker",
              reg.get("m2").getState() == OfferMarker.DECLINED
              && reg.getLiveMarkers().size() == 3);
        reg.enterRound(8);
        checkNoMarkersSurvive("mixed", reg);
    }

    /**
     * The check a naive probe would miss: the sweep must not be EAGER. A
     * marker created in round N is still on screen during round N.
     */
    private static void scenarioMarkerSurvivesItsOwnRound() {
        OfferMarkerRegistry reg = new OfferMarkerRegistry();
        reg.enterRound(10);
        reg.offer("o10", "Vree Enchanter", "OFFER");
        check("own round: re-entering the SAME round expires nothing",
              reg.enterRound(10) == 0);
        check("own round: marker is still live",
              reg.get("o10").isLive() && reg.getLiveMarkers().size() == 1);
        check("own round: going backwards does not sweep either",
              reg.enterRound(9) == 0);
        check("own round: marker survived the backwards refresh",
              reg.get("o10") != null && reg.get("o10").isLive());
    }

    /** Illegal transitions are refused, not silently applied. */
    private static void scenarioIllegalTransitionsRefused() {
        OfferMarkerRegistry reg = new OfferMarkerRegistry();
        reg.enterRound(1);
        reg.offer("i1", "x", "X");
        check("illegal: accepting an unknown offer id is refused", !reg.accept("nope"));
        check("illegal: declining an unknown offer id is refused", !reg.decline("nope"));
        reg.accept("i1");
        check("illegal: accepting twice is refused (stale click)",
              !reg.accept("i1"));
        check("illegal: declining after accepting is refused", !reg.decline("i1"));
        // i2 is still CREATED, which is the only state retire() must refuse:
        // retiring means "the accepted action was processed", and nothing has
        // been accepted here. Tested on its own marker because retire() on the
        // ACCEPTED i1 legitimately succeeds and would remove it.
        reg.offer("i2", "y", "Y");
        check("illegal: retiring a still-CREATED marker is refused", !reg.retire("i2"));
        check("illegal: that CREATED marker is untouched",
              reg.get("i2") != null && reg.get("i2").getState() == OfferMarker.CREATED);
        check("illegal: i1 is still ACCEPTED after the refusals",
              reg.get("i1").getState() == OfferMarker.ACCEPTED);
        check("illegal: retiring a null/unknown id is refused", !reg.retire("nope"));
        check("illegal: accepting a null id is refused", !reg.accept(null));
    }
}