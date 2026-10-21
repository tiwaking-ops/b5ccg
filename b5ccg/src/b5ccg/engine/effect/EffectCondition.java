package b5ccg.engine.effect;

/**
 * WP-10 / B5-0028 (A3): one predicate an effect checks before it applies.
 *
 * <p>Immutable. A condition is a type, an amount, and an optional subject
 * string - the subject being the thing the predicate is about when the type
 * needs one (the state name for {@link EffectConditionType#STATE_EXISTS}, the
 * mark type for {@link EffectConditionType#MARK_COUNT_AT_LEAST}).</p>
 */
public final class EffectCondition {

    private final EffectConditionType type;
    private final int amount;
    private final String subject;

    public EffectCondition(EffectConditionType type, int amount, String subject) {
        if (type == null) {
            throw new IllegalArgumentException("condition type is null");
        }
        this.type = type;
        this.amount = amount;
        this.subject = subject;
    }

    public EffectConditionType getType() { return type; }

    /** The threshold. Meaningless for the existence conditions, where it is 0. */
    public int getAmount() { return amount; }

    /** The subject the predicate is about, or null when it needs none. */
    public String getSubject() { return subject; }

    @Override
    public String toString() {
        String s = type.name() + "(" + amount + ")";
        return subject == null ? s : s + "[" + subject + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EffectCondition)) return false;
        EffectCondition other = (EffectCondition) o;
        if (type != other.type || amount != other.amount) return false;
        return subject == null ? other.subject == null : subject.equals(other.subject);
    }

    @Override
    public int hashCode() {
        int h = type.hashCode() * 31 + amount;
        h = h * 31 + (subject == null ? 0 : subject.hashCode());
        return h;
    }
}
