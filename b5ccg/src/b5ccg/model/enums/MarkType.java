package b5ccg.model.enums;

/**
 * Mark types per rulebook section VI "Marks".
 * A faction cannot have both Shadow and Vorlon marks (they are "opposing").
 * Otherwise a faction may possess any number of marks in any combination.
 */
public enum MarkType {
    SHADOW,
    VORLON,
    DOOM,
    STRIFE,
    CONSPIRACY,
    DESTINY
}