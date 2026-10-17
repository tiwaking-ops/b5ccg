package b5ccg.engine;

/** WP-10 / B5-0028 (Effect schema v0 pilot) — additive interface only.
 * Zero mutation to existing behavior tables; freeze-window claim B5-0028 active.
 * Pilot scope: 51 GROUP + 78 ENHANCEMENT cards; full rollout deferred to WP-11/12. */
public interface GroupEffectSchema {
    String cardId();
    String schemaCategory();
    int schemaVersion(); // v0
    boolean isRegisteredInCardEffects();
}
