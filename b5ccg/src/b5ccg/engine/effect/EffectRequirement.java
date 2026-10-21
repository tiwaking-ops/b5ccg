package b5ccg.engine.effect;

/**
 * WP-10 / B5-0028 (A3): one entry of a card's cost block.
 *
 * <p>Immutable. A requirement is a type and an amount; the subject of a
 * requirement (whose marks, which sponsor) is carried by the entry's
 * {@link EffectTarget} rather than repeated here.</p>
 */
public final class EffectRequirement {

    private final EffectRequirementType type;
    private final int amount;

    public EffectRequirement(EffectRequirementType type, int amount) {
        if (type == null) {
            throw new IllegalArgumentException("requirement type is null");
        }
        this.type = type;
        this.amount = amount;
    }

    public EffectRequirementType getType() { return type; }

    /** How many. Zero is legal: "purge marks" with no number reads as one by
     *  convention, but the data states its own number rather than relying on
     *  the engine to guess. */
    public int getAmount() { return amount; }

    @Override
    public String toString() {
        return type.name() + "(" + amount + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EffectRequirement)) return false;
        EffectRequirement other = (EffectRequirement) o;
        return type == other.type && amount == other.amount;
    }

    @Override
    public int hashCode() {
        return type.hashCode() * 31 + amount;
    }
}
