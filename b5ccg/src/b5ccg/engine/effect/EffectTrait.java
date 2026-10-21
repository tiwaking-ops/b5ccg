package b5ccg.engine.effect;

/**
 * WP-10 / B5-0028 (A3): the printed traits that can filter a target.
 *
 * <p>A3 says outright that "{@code subtype} and {@code text} are not
 * sufficient substrates" - the shipped {@code subtype} encodes card type and
 * race, not printed traits. The traits that must become structured data are
 * named there: "Limited, Unique, Multiple, Kha'Ri, Species Ambassador, Ranger,
 * ISA, Home Faction or Psi Corps loyalty".</p>
 *
 * <p>Two of the values below are not printed traits but printed <em>rules</em>
 * that read like target filters, and they are here because the schema needs a
 * home for them rather than because the trait vocabulary is complete:</p>
 * <ul>
 *   <li>{@link #NOT_AFFECTED_BY_SOURCE} - "choose a Character <em>not already
 *       affected by this Group</em>" (Commercial Telepaths, premiere text).
 *       This is a real restriction, and the Deluxe printing dropped it, so it
 *       belongs in the data rather than in the engine.</li>
 *   <li>{@link #IN_INNER_CIRCLE} / {@link #IN_SUPPORTING_ROLE} - the "row"
 *       filter of A3's target field, kept here so that the target vocabulary
 *       lives in one place.</li>
 * </ul>
 *
 * <p>Every value here is a <em>filter</em>. A trait that a card grants is
 * carried by the card's own data, not by an effect entry.</p>
 */
public enum EffectTrait {

    /** The Limited trait: at most one copy in play (rulebook :1166). */
    LIMITED,

    /** The Unique trait. */
    UNIQUE,

    /** The Multiple trait. */
    MULTIPLE,

    /** Kha'Ri (rulebook :1120 area, {@code RB:VI/khari-characters}). */
    KHARI,

    /** Species Ambassador. */
    AMBASSADOR,

    /** Ranger (rulebook :1182). */
    RANGER,

    /** ISA - Interstellar Alliance. */
    ISA,

    /** Psi Corps. */
    PSI_CORPS,

    /** Home-faction loyalty. */
    HOME_FACTION_LOYALTY,

    /** Not already affected by the source card. */
    NOT_AFFECTED_BY_SOURCE,

    /** In the controller's Inner Circle. */
    IN_INNER_CIRCLE,

    /** In the controller's Supporting Role. */
    IN_SUPPORTING_ROLE;

    /** Parses a trait, failing closed. */
    public static EffectTrait parse(String raw, String location) throws EffectLoadException {
        if (raw == null || raw.trim().length() == 0) {
            throw new EffectLoadException(location, "missing trait");
        }
        String v = raw.trim();
        for (EffectTrait t : values()) {
            if (t.name().equals(v)) {
                return t;
            }
        }
        throw new EffectLoadException(location,
                "unknown trait '" + v + "'; known traits are " + names());
    }

    /** The known traits, comma-separated, in declaration order. */
    public static String names() {
        StringBuilder sb = new StringBuilder();
        for (EffectTrait t : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.name());
        }
        return sb.toString();
    }
}
