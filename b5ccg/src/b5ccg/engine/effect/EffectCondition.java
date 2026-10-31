package b5ccg.engine.effect;

/**
 * WP-10 / B5-0028 (A3): one predicate an effect checks before it applies.
 *
 * <p>Immutable. A condition is a type, an amount, and an optional subject
 * string - the subject being the thing the predicate is about when the type
 * needs one (the state name for {@link EffectConditionType#STATE_EXISTS}, the
 * mark type for {@link EffectConditionType#MARK_COUNT_AT_LEAST}).</p>
 *
 * <p>For {@link EffectConditionType#MARK_COUNT_AT_LEAST}, the {@link #isInvert()}
 * field indicates whether the condition is inverted (no mark vs has mark).
 * Pulling Strings: "If target character does not have a Destiny Mark" = invert=true.
 * Stealth Technology: "as long as your faction has no Doom Marks" = invert=true.</p>
 */
public final class EffectCondition {

    private final EffectConditionType type;
    private final int amount;
    private final String subject;
    private final boolean invert;

    public EffectCondition(EffectConditionType type, int amount, String subject) {
        this(type, amount, subject, false);
    }

    public EffectCondition(EffectConditionType type, int amount, String subject, boolean invert) {
        if (type == null) {
            throw new IllegalArgumentException("condition type is null");
        }
        this.type = type;
        this.amount = amount;
        this.subject = subject;
        this.invert = invert;
    }

    public EffectConditionType getType() { return type; }

    /** The threshold. Meaningless for the existence conditions, where it is 0. */
    public int getAmount() { return amount; }

    /** The subject the predicate is about, or null when it needs none. */
    public String getSubject() { return subject; }

    /** Whether the predicate is inverted (e.g., "does NOT have mark"). */
    public boolean isInvert() { return invert; }

    @Override
    public String toString() {
        String s = type.name() + "(" + amount + ")";
        if (subject != null) s = s + "[" + subject + "]";
        if (invert) s = s + " INVERT";
        return s;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EffectCondition)) return false;
        EffectCondition other = (EffectCondition) o;
        if (type != other.type || amount != other.amount || invert != other.invert) return false;
        return subject == null ? other.subject == null : subject.equals(other.subject);
    }

    @Override
    public int hashCode() {
        int h = type.hashCode() * 31 + amount;
        h = h * 31 + (subject == null ? 0 : subject.hashCode());
        return h * 31 + (invert ? 1 : 0);
    }
}
