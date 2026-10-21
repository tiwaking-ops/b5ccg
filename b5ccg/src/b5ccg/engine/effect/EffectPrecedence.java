package b5ccg.engine.effect;

/**
 * WP-10 / B5-0028 (A3): the layer of the precedence rules an effect sits in.
 *
 * <p>A3's effect-entry shape anchors this field at rulebook :866-:870, :1146
 * and :1156 ({@code RB:VI/rules-of-precedence}): "card text over rules, except
 * ambassador departure; 'cannot' overrides any enabling effect; multipliers
 * before additive changes".</p>
 *
 * <p><b>Named, so the precedence is data.</b> A3 principle 6 is "The
 * rulebook's own precedence rules are the evaluation order, not an invention
 * of the engine." Recording the layer in the entry means the engine reads
 * the rulebook's ordering off the data rather than deciding it, and a card
 * whose text is a "cannot" says so in the entry that carries it.</p>
 */
public enum EffectPrecedence {

    /** The card's own text governs over the general rules. */
    CARD_TEXT,

    /**
     * The card's text governs, except where the rulebook gives the general
     * rules priority - the ambassador-departure case the proposal names.
     */
    CARD_TEXT_EXCEPT_AMBASSADOR_DEPARTURE,

    /** The general rules govern. */
    RULES,

    /** A multiplier applies before an additive change. */
    MULTIPLIER,

    /** A prohibition; it overrides any enabling effect. */
    CANNOT_OVERRIDES;

    /** Parses a precedence layer, failing closed. */
    public static EffectPrecedence parse(String raw, String location)
            throws EffectLoadException {
        if (raw == null || raw.trim().length() == 0) {
            throw new EffectLoadException(location, "missing precedence layer");
        }
        String v = raw.trim();
        for (EffectPrecedence p : values()) {
            if (p.name().equals(v)) {
                return p;
            }
        }
        throw new EffectLoadException(location,
                "unknown precedence layer '" + v + "'; known layers are " + names());
    }

    /** The known layers, comma-separated, in declaration order. */
    public static String names() {
        StringBuilder sb = new StringBuilder();
        for (EffectPrecedence p : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p.name());
        }
        return sb.toString();
    }
}
