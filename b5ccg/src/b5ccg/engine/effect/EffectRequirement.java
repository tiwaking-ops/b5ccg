package b5ccg.engine.effect;

import b5ccg.model.enums.MarkType;

/**
 * WP-10 / B5-0028 (A3): one entry of a card's cost block.
 *
 * <p>Immutable. A requirement is a type and an amount; for mark-based
 * requirements the {@link #getMarkType()} names which mark type the
 * requirement is about (e.g. {@code PURGE_MARKS(4, VORLON)} reads
 * "purge 4 Vorlon Marks"). The subject of a requirement (whose marks,
 * which sponsor) that is not a mark type is carried by the entry's
 * {@link EffectTarget} rather than repeated here.</p>
 *
 * <p>For {@link EffectRequirementType#ROTATE_SPONSOR}, the {@link #getSponsor()}
 * field names which sponsor type is required: AMBASSADOR, INNER_CIRCLE, or ANY.</p>
 */
public final class EffectRequirement {

    private final EffectRequirementType type;
    private final int amount;
    private final MarkType markType;
    private final String sponsor;

    public EffectRequirement(EffectRequirementType type, int amount) {
        this(type, amount, null, null);
    }

    public EffectRequirement(EffectRequirementType type, int amount, MarkType markType) {
        this(type, amount, markType, null);
    }

    public EffectRequirement(EffectRequirementType type, int amount, MarkType markType, String sponsor) {
        if (type == null) {
            throw new IllegalArgumentException("requirement type is null");
        }
        this.type = type;
        this.amount = amount;
        this.markType = markType;
        this.sponsor = sponsor;
    }

    public EffectRequirementType getType() { return type; }

    /** How many. Zero is legal: "purge marks" with no number reads as one by
     *  convention, but the data states its own number rather than relying on
     *  the engine to guess. */
    public int getAmount() { return amount; }

    /** The mark type the requirement is about, or null when the requirement
     *  is not mark-based. */
    public MarkType getMarkType() { return markType; }

    /** The sponsor type for ROTATE_SPONSOR (AMBASSADOR, INNER_CIRCLE, ANY), or null. */
    public String getSponsor() { return sponsor; }

    @Override
    public String toString() {
        String s = type.name() + "(" + amount + ")";
        if (markType != null) s = s + ", " + markType.name();
        if (sponsor != null) s = s + ", sponsor=" + sponsor;
        return s;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EffectRequirement)) return false;
        EffectRequirement other = (EffectRequirement) o;
        return type == other.type && amount == other.amount
                && markType == other.markType && sponsorEquals(other.sponsor);
    }

    private boolean sponsorEquals(String other) {
        if (sponsor == null && other == null) return true;
        if (sponsor == null || other == null) return false;
        return sponsor.equals(other);
    }

    @Override
    public int hashCode() {
        int h = type.hashCode() * 31 + amount;
        h = h * 31 + (markType == null ? 0 : markType.hashCode());
        return h * 31 + (sponsor == null ? 0 : sponsor.hashCode());
    }
}
