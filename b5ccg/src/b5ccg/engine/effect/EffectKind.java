package b5ccg.engine.effect;

/**
 * WP-10 / B5-0028 (A3): the closed set of effect <em>kinds</em>.
 *
 * <p>A3's effect-entry shape names the {@code kind} field first, and anchors
 * it at rulebook :530 and :775. The values below are the seven the proposal
 * lists: "one-shot, ongoing, triggered, replacement, restriction, cost
 * modifier, trait".</p>
 *
 * <p><b>Why a closed enum and not a free string.</b> Principle 4 of A3 is
 * "Fail closed. An unknown effect kind is a load error." A free string cannot
 * fail closed: the loader would have to decide whether an unrecognised word is
 * a typo (tolerable), a new kind (forward-compatible, tolerable) or a
 * mis-typed existing kind (fatal). An enum forces that decision to be made
 * once, here, and it is then enforced by the compiler everywhere else.</p>
 *
 * <p>The set is deliberately <em>not</em> the census's 14 classes: the census
 * classes describe what a card <strong>is</strong> (B5-0005's
 * {@code effect-census.json}), while a kind describes what an effect
 * <strong>does</strong>. A card may carry more than one effect entry, and two
 * entries of different kinds.</p>
 */
public enum EffectKind {

    /** Fires once, when the card is played. */
    ONE_SHOT,

    /** Stays in effect while the source is in play. */
    ONGOING,

    /** Fires when a named event occurs. */
    TRIGGERED,

    /** Replaces what would otherwise happen ("draw 2 instead of 1"). */
    REPLACEMENT,

    /** Forbids or permits something. */
    RESTRICTION,

    /** Changes what a card costs. */
    COST_MODIFIER,

    /** Grants a printed trait rather than behaviour. */
    TRAIT;

    /**
     * Parses a kind, failing closed.
     *
     * @throws EffectLoadException if the value is not a known kind. The message
     *     names the offending value and the known set, because the whole point
     *     of failing closed is that the reader is told what was refused.
     */
    public static EffectKind parse(String raw, String location) throws EffectLoadException {
        if (raw == null || raw.trim().length() == 0) {
            throw new EffectLoadException(location, "missing effect kind");
        }
        String v = raw.trim();
        for (EffectKind k : values()) {
            if (k.name().equals(v)) {
                return k;
            }
        }
        throw new EffectLoadException(location,
                "unknown effect kind '" + v + "'; known kinds are " + names());
    }

    /** The known kinds, comma-separated, in declaration order. */
    public static String names() {
        StringBuilder sb = new StringBuilder();
        for (EffectKind k : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(k.name());
        }
        return sb.toString();
    }
}
