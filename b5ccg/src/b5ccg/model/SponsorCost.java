package b5ccg.model;

/** Result of evaluating an action's sponsor cost and rotation requirement. */
public final class SponsorCost {
    private final int amount;
    private final boolean requiresRotation;
    private final boolean waived;

    public SponsorCost(int amount, boolean requiresRotation, boolean waived) {
        this.waived = waived;
        this.amount = waived ? 0 : Math.max(0, amount);
        this.requiresRotation = waived ? false : requiresRotation;
    }

    public static SponsorCost waived() {
        return waived(0);
    }

    public static SponsorCost waived(int unwaivedAmount) {
        return new SponsorCost(unwaivedAmount, true, true);
    }

    public int getAmount() { return amount; }
    public boolean requiresRotation() { return requiresRotation; }
    public boolean isWaived() { return waived; }
}
