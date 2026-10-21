package b5ccg.engine.effect;

import java.util.Set;

/**
 * WP-10 / B5-0028 (A3): a named primitive the effect registry implements.
 *
 * <p>A3 principle 1 is "Hybrid, not pure. Declarative records reference a
 * registry of named primitives implemented in Java." This interface is the
 * Java side of that registry: a primitive is a name, the effect kinds it may
 * appear under, the family it belongs to, and the canonical text a renderer
 * produces for it.</p>
 *
 * <p><b>One class per family, not one class per primitive.</b> A3 principle 5
 * is "Keep the primitive registry modular (one class per primitive family)",
 * and its reason is stated: "Forge's ability factory itself grew past 1,200
 * lines, so the registry is where the next god class would form." A registry
 * that grew one class per primitive would be a class explosion; a registry
 * with one giant class would be the god class the principle warns about. The
 * family is the unit, and {@link #family()} is what makes that checkable from
 * outside rather than a matter of opinion.</p>
 */
public interface EffectPrimitive {

    /** The registry key: the name a data file uses to invoke this primitive. */
    String name();

    /** The effect kinds this primitive may appear under. */
    Set<EffectKind> kinds();

    /**
     * The name of the primitive family this primitive belongs to.
     *
     * <p>Every primitive of one family returns the same string. The family
     * name is what the registry uses to answer "is the registry still
     * modular?" - a question the gate asks and a reader can check.</p>
     */
    String family();

    /**
     * Validates the operation and returns its canonical text.
     *
     * <p>Validation and rendering are one method on purpose. If they were
     * separate, a primitive would have to be written twice - once to check
     * its parameters and once to print them - and the two could disagree
     * about what a valid operation looks like. A missing or unrecognised
     * parameter is a load error (A3 principle 4), not a rendering
     * inconvenience.</p>
     *
     * @param op the operation to check
     * @param location where the operation sits in the document, for the error
     * @return the canonical text, e.g. {@code STAT_MODIFY(stat=DIPLOMACY, amount=2)}
     * @throws EffectLoadException when the operation's parameters are missing
     *     or are not one of the closed set the primitive accepts
     */
    String describe(EffectOperation op, String location) throws EffectLoadException;

    /**
     * A second check, run after {@link #describe}, that depends on the
     * registry rather than on the operation alone.
     *
     * <p>Only the escape hatch needs it: a bespoke operation names a Java
     * handler, and the handler must be one the registry actually holds. A
     * bespoke operation whose handler is not registered is a load error, not
     * a call to a handler that does not exist.</p>
     *
     * <p>The default implementation checks nothing, which is correct for
     * every primitive that is fully described by its own parameters.</p>
     *
     * @param registry the registry the operation is being loaded against
     * @param op the operation being checked
     * @param location where the operation sits in the document
     */
    default void checkRegistered(EffectRegistry registry, EffectOperation op, String location)
            throws EffectLoadException {
    }
}
