package b5ccg.model;

import b5ccg.model.enums.*;

public class SustainedAction {
    private final Card sourceCard;
    private final Card targetCard;
    private final SustainedActionType type;
    private final Player controller;
    private int roundStarted;

    public SustainedAction(Card sourceCard, Card targetCard, SustainedActionType type,
                           Player controller, int roundStarted) {
        this.sourceCard = sourceCard;
        this.targetCard = targetCard;
        this.type = type;
        this.controller = controller;
        this.roundStarted = roundStarted;
    }

    public Card getSourceCard() { return sourceCard; }
    public Card getTargetCard() { return targetCard; }
    public SustainedActionType getType() { return type; }
    public Player getController() { return controller; }
    public int getRoundStarted() { return roundStarted; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        SustainedAction other = (SustainedAction) obj;
        return sourceCard != null ? sourceCard.getId().equals(other.sourceCard.getId()) : other.sourceCard == null;
    }

    @Override
    public int hashCode() {
        return sourceCard != null ? sourceCard.getId().hashCode() : 0;
    }

    @Override
    public String toString() {
        return "SustainedAction{" + (sourceCard != null ? sourceCard.getTitle() : "null")
                + " -> " + (targetCard != null ? targetCard.getTitle() : "null")
                + " (" + type + ")}";
    }
}