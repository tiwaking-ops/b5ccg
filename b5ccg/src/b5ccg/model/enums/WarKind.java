package b5ccg.model.enums;

/** B5-0376 (B5-0358 proposal §3.2): the kind of target a war conflict
 *  was declared against. A war conflict carries exactly one of these
 *  (or neither, in the interim before location targeting lands).
 */
public enum WarKind {
    /** "the opposing race as a whole" — targets a Player (\u00a7War :807). */
    RACE_TARGET,
    /** "a specific location in play for a faction of that race" \u2014 §War :807. */
    LOCATION_TARGET
}