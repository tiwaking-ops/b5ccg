package b5ccg.model.enums;

public enum GamePhase {
    SETUP,
    // Rulebook III :320-:328 — each turn runs READY (startRound) then
    // CONFLICT (declare + reveal/initiate) then ACTION, Mercenaries, the
    // Resolution round, Aftermath and Draw. Declarations live here, never in
    // the action loop.
    CONFLICT,
    ACTION,
    // B5-0395 (rulebook §Mercenaries): "Mercenaries act after all players have
    // passed, but before the beginning of the Resolution round" — mapped to a
    // phase between ACTION end and conflict resolution, in enum order.
    MERCENARY,
    CONFLICT_RESOLUTION,
    AFTERMATH,
    DRAW,
    END_ROUND
}
