package b5ccg.engine.effect;

/**
 * WP-10 / B5-0028 (A3): the error the effect loader raises when an effect
 * document cannot be trusted.
 *
 * <p><b>Why a checked exception.</b> A3's design principle 4 is "Fail closed.
 * An unknown effect kind is a load error." A <em>checked</em> exception is the
 * mechanism that makes the failure hard to ignore: a caller that does not
 * declare or catch it will not compile, so "the loader swallowed the bad entry
 * and carried on" is not a mistake that can be made by accident. An unchecked
 * exception would make fail-closed a convention rather than a constraint.</p>
 *
 * <p><b>What it is not.</b> It is not a parse error - a malformed JSON document
 * is an {@link java.io.IOException}. It is specifically the verdict "this
 * document is well-formed JSON, but it says something the engine does not
 * understand, and the engine will not guess".</p>
 */
public class EffectLoadException extends Exception {

    private static final long serialVersionUID = 1L;

    /** Where in the document the failure happened, e.g. "entries[3].kind". */
    private final String location;

    public EffectLoadException(String location, String message) {
        super(location + ": " + message);
        this.location = location;
    }

    /** The document position at which the load was refused. */
    public String getLocation() {
        return location;
    }
}
