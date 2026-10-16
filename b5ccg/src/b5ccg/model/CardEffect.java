package b5ccg.model;

public interface CardEffect {
    void apply(GameState state, Player owner, Card source);
}
