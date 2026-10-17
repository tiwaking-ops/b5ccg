package b5ccg.engine;
/** WP-13 / B5-0029 — Unimplemented-text tripwire (A4). Additive; freeze sustained. */
public final class HeadlessUnimplementedTextTripwire {
    public static boolean check(String cardText) {
        return cardText != null && (cardText.contains("unimplemented") || cardText.contains("NOT IMPLEMENTED"));
    }
}
