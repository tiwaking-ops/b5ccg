package b5ccg.engine;

import b5ccg.model.GameState;

/**
 * B5-2533: the engine's error-handling convention, named.
 *
 * <p>The B5-2516 audit measured five mechanisms in use across
 * {@code GameController}, {@code RulesEngine} and {@code CardEffects} and four
 * of them unnamed. This class names them, gives the two that need a runtime
 * representation a type, and provides the one helper each site needs so that
 * "what should this site do about a bad condition" has a written answer.
 *
 * <p><b>FATAL</b> &mdash; no playable state can be constructed; use {@link
 * #fatal}. <b>GUARD-RETURN</b> &mdash; the input is illegal and a caller has
 * already gated the call; use the method's own sentinel return. <b>RECOVERABLE
 * </b> &mdash; a recoverable condition changes what the player can do; use
 * {@link #recoverable}. <b>IMPOSSIBLE</b> &mdash; the engine reached a state it
 * believes cannot happen; use {@link #reportImpossible}. <b>SILENT</b> &mdash; a
 * recovery that is correct and must say so in a comment at the site.
 *
 * <p>The house rule this encodes: throw only where no playable state can be
 * constructed &mdash; load-time validation, and the preconditions of a method
 * that cannot be called at all &mdash; and never in the play engine.
 * Guard-return when the input is illegal and a caller has already gated the
 * call. Log to the player channel when a recoverable condition changes what
 * the player can do. Log to the developer channel, prefixed {@code B5-NNNN:},
 * when the engine hit a state it believes impossible. Never both silently. A
 * silent recovery must be justified in a comment at the site.
 *
 * <p><b>Which channel, decided by audience and not by habit.</b> There are
 * three: {@code state.log} is the only one a player can read ({@code
 * GameState.log} prefixes {@code [R<n>]} and {@code MainWindow} renders the
 * result into the log area), {@code System.err} is the developer channel, and
 * a throw reaches whoever called. Routing a developer diagnostic to the player
 * channel is the mistake this class exists to prevent: a player reads {@code
 * "WARNING: no effect implementation ..."} and can do nothing about it.
 *
 * <p><b>What is deliberately unchanged.</b> The audit found three conventions
 * already correct and this pass preserves all three: the legality-predicate /
 * executor split (a {@code can*} method returns {@code false} silently, an
 * {@code execute*} method logs), the absence of {@code printStackTrace}
 * anywhere in the engine, and the {@code finally} close guards in {@code
 * DeckLoader}, which are the one correctly-silent mechanism and are named here
 * as SILENT for that reason.
 */
public final class EngineErrorHandling {

    /** No playable state can be constructed; the game cannot continue. */
    public static class FatalError extends RuntimeException {
        private static final long serialVersionUID = 1L;
        /** The {@code B5-NNNN} id of the row that made this call fatal. */
        public final String code;

        public FatalError(String code, String message) {
            super(message);
            this.code = code;
        }

        public FatalError(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }
    }

    /**
     * A recoverable condition the player must be told about, because it changes
     * what the player can do. Carries the player-facing sentence separately
     * from the developer detail, so a site cannot report the wrong one to the
     * wrong audience.
     */
    public static class RecoverableError extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public final String code;
        /** Exactly what the player reads. Never null. */
        public final String playerMessage;
        /** Developer detail. Never shown to a player. May be null. */
        public final String developerDetail;

        public RecoverableError(String code, String playerMessage,
                                String developerDetail) {
            super(playerMessage);
            this.code = code;
            this.playerMessage = playerMessage == null ? "" : playerMessage;
            this.developerDetail = developerDetail;
        }
    }

    private EngineErrorHandling() { }

    // EngineErrorHandling continues below (the static helpers).

    /**
     * FATAL. Reports a condition from which no playable state can be
     * constructed and hands it to the caller.
     *
     * <p>Load-time validation, and the preconditions of a method that cannot be
     * called at all. A failed card-pool load at setup belongs here: there is no
     * game to continue into, so continuing builds a game whose seats have no
     * starting fleet and no agenda, and describes it in one sentence a player
     * cannot act on.
     */
    public static FatalError fatal(String code, String message) {
        return new FatalError(code, message);
    }

    /** FATAL, carrying the cause that made the condition unreachable. */
    public static FatalError fatal(String code, String message, Throwable cause) {
        return new FatalError(code, message, cause);
    }

    /**
     * RECOVERABLE. Builds the error and logs the player-facing sentence to the
     * player channel in one call, so the two cannot be separated by a later
     * edit.
     *
     * @return the error, for a caller that must unwind rather than continue
     */
    public static RecoverableError recoverable(GameState state, String code,
                                               String playerMessage,
                                               String developerDetail) {
        RecoverableError e = new RecoverableError(code, playerMessage,
                                                  developerDetail);
        logToPlayer(state, e.playerMessage);
        return e;
    }

    /** The player channel: the only channel a player can read. */
    public static void logToPlayer(GameState state, String message) {
        if (state == null) return;   // no player to tell; see requireState
        state.log(message);
    }

    /**
     * IMPOSSIBLE. The developer channel, in the repo's house format: a {@code
     * B5-NNNN:} prefix plus the thing named. For a state the engine believes
     * cannot happen &mdash; never for anything a player must act on.
     */
    public static void reportImpossible(String code, String detail) {
        System.err.println(prefix(code) + detail);
    }

    /**
     * The {@code B5-NNNN:} prefix in one place. Four of the five existing
     * developer-channel sites carried it and one did not; building it here
     * means the next site cannot forget.
     */
    public static String prefix(String code) {
        return (code == null || code.length() == 0) ? "B5-NNNN: " : code + ": ";
    }

    /**
     * The required-parameter convention, in one call.
     *
     * <p>B5-2516 F2 measured {@code RulesEngine} carrying both "state may be
     * null" and "state must not be null" in adjacent code, with nothing
     * declaring which. A method that means "required" says so here, so the file
     * stops disagreeing with itself, and a caller that passes null is told which
     * rule it broke instead of silently receiving a sentinel.
     *
     * <p>Only for parameters that are genuinely required. A <em>query</em>
     * method with a documented null contract &mdash; "empty for a null state",
     * "null for a game this engine is not bound to" &mdash; keeps its own
     * guard, because there the null is an answer, not a mistake.
     *
     * @return {@code state}, so the call can be inlined into a guard clause
     */
    public static GameState requireState(GameState state, String code,
                                         String method) {
        if (state == null) {
            throw fatal(code, "GameState must not be null in RulesEngine."
                             + method + "()");
        }
        return state;
    }
}