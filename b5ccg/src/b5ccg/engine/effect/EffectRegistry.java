package b5ccg.engine.effect;

import b5ccg.engine.effect.bespoke.SpinDoctorsPerceivedWinner;
import b5ccg.engine.effect.primitives.AbilityAsPrimitives;
import b5ccg.engine.effect.primitives.BespokePrimitives;
import b5ccg.engine.effect.primitives.ConflictExtraPrimitives;
import b5ccg.engine.effect.primitives.ConflictPrimitives;
import b5ccg.engine.effect.primitives.ConvertInfluencePrimitives;
import b5ccg.engine.effect.primitives.CostPrimitives;
import b5ccg.engine.effect.primitives.DamageCombatPrimitives;
import b5ccg.engine.effect.primitives.DamagePrimitives;
import b5ccg.engine.effect.primitives.DiscardPrimitives;
import b5ccg.engine.effect.primitives.DrawPrimitives;
import b5ccg.engine.effect.primitives.GrantPrimitives;
import b5ccg.engine.effect.primitives.InfluencePrimitives;
import b5ccg.engine.effect.primitives.InfluenceRatingPrimitives;
import b5ccg.engine.effect.primitives.InformationPrimitives;
import b5ccg.engine.effect.primitives.MarkPrimitives;
import b5ccg.engine.effect.primitives.RestrictionPrimitives;
import b5ccg.engine.effect.primitives.RotationPrimitives;
import b5ccg.engine.effect.primitives.StatDerivedPrimitives;
import b5ccg.engine.effect.primitives.StatPrimitives;
import b5ccg.engine.effect.primitives.StatePrimitives;
import b5ccg.engine.effect.primitives.TensionControlPrimitives;
import b5ccg.engine.effect.primitives.TensionPrimitives;
import b5ccg.engine.effect.primitives.VotePrimitives;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * WP-10 / B5-0028 (A3): the primitive registry.
 *
 * <p>A3 principle 1: "Declarative records reference a registry of named
 * primitives implemented in Java." This is that registry, and it is the one
 * place that knows which primitive names exist. A data file names a
 * primitive; the registry resolves it; nothing is resolved by reflection, by
 * a string switch, or by a naming convention.</p>
 *
 * <p><b>Fail closed.</b> {@link #primitive(String)} throws
 * {@link EffectLoadException} for a name the registry does not hold. A
 * primitive that is not in the table is not a primitive that is silently
 * skipped - it is a document that will not load.</p>
 *
 * <p><b>Modularity is checkable.</b> A3 principle 5 warns that "the registry
 * is where the next god class would form", and the mitigation the proposal
 * asks for is one class per primitive family. That is a property of the code
 * that a reader can only take on trust, so this class exposes
 * {@link #knownFamilies()} and {@link #familySizes()}: the registry can be
 * asked how its primitives are distributed, and the answer is part of the
 * gate rather than a claim in a comment.</p>
 *
 * <p><b>The bespoke table is no longer empty.</b> In v0 it published zero
 * handlers, which said the bespoke tail was empty without having measured
 * it. WP-11 (B5-0034) measured it - the Groups pilot - and the tail is
 * one card: "Spin Doctors", whose effect changes who a Diplomacy
 * conflict's winner <em>is</em>, which no operation on a card or a player
 * can express. The handler is published here, under a key, so a data file
 * that names it loads; the class it names is a pure rule, and the engine
 * does not yet execute effect entries, so the call site is still to
 * come.</p>
 */
public final class EffectRegistry {

    private final Map<String, EffectPrimitive> primitives =
            new LinkedHashMap<String, EffectPrimitive>();
    private final Map<String, String> bespokeHandlers =
            new LinkedHashMap<String, String>();

    /** An empty registry. */
    public EffectRegistry() {
    }

    /**
     * The shipped registry: every family, and the bespoke handlers the
     * shipped data names.
     *
     * <p>Building it is explicit rather than reflective, so the set of
     * primitives is visible in this method and cannot drift when a family is
     * added elsewhere.</p>
     *
     * <p><b>The bespoke table is no longer empty.</b> In v0 it published zero
     * handlers, which said the bespoke tail was empty without having measured
     * it. WP-11 (B5-0034) measured it - the Groups pilot - and the tail is
     * one card: "Spin Doctors", whose effect changes who a Diplomacy
     * conflict's winner <em>is</em>, which no operation on a card or a player
     * can express. The handler is published here, under a key, so a data file
     * that names it loads; the class it names is a pure rule, and the engine
     * does not yet execute effect entries, so the call site is still to
     * come.</p>
     */
    public static EffectRegistry defaultRegistry() {
        EffectRegistry reg = new EffectRegistry();
        try {
            reg.addAll(StatPrimitives.all());
            reg.addAll(StatDerivedPrimitives.all());
            reg.addAll(InfluencePrimitives.all());
            reg.addAll(InfluenceRatingPrimitives.all());
            reg.addAll(MarkPrimitives.all());
            reg.addAll(DrawPrimitives.all());
            reg.addAll(DiscardPrimitives.all());
            reg.addAll(DamagePrimitives.all());
            reg.addAll(DamageCombatPrimitives.all());
            reg.addAll(TensionPrimitives.all());
            reg.addAll(TensionControlPrimitives.all());
            reg.addAll(StatePrimitives.all());
            reg.addAll(CostPrimitives.all());
            reg.addAll(ConflictPrimitives.all());
            reg.addAll(ConflictExtraPrimitives.all());
            reg.addAll(RestrictionPrimitives.all());
            reg.addAll(RotationPrimitives.all());
            reg.addAll(InformationPrimitives.all());
            reg.addAll(BespokePrimitives.all());
            reg.addAll(AbilityAsPrimitives.all());
            reg.addAll(GrantPrimitives.all());
            reg.addAll(ConvertInfluencePrimitives.all());
            reg.addAll(VotePrimitives.all());
        } catch (EffectLoadException ele) {
            // Unreachable in practice: the shipped families have distinct
            // names. If one day they do not, that is a defect in THIS file
            // and not in any data file, so it is a programming error rather
            // than a load error - and saying so here keeps the call sites
            // free of a catch that can never fire.
            throw new IllegalStateException(
                    "the shipped v0 effect registry has a duplicate primitive name: "
                    + ele.getMessage());
        }
        reg.registerBespokeHandler(SpinDoctorsPerceivedWinner.HANDLER_KEY,
                SpinDoctorsPerceivedWinner.HANDLER_NAME);
        return reg;
    }

    private void addAll(List<EffectPrimitive> family) throws EffectLoadException {
        for (int i = 0; i < family.size(); i++) {
            register(family.get(i));
        }
    }

    /**
     * Adds a primitive to the registry.
     *
     * @throws EffectLoadException when the name is already registered. A
     *     duplicate is a defect, not an override: two families claiming one
     *     name would make the registry's answer depend on registration order.
     */
    public void register(EffectPrimitive primitive) throws EffectLoadException {
        if (primitive == null) {
            throw new EffectLoadException("registry", "null primitive");
        }
        if (primitives.containsKey(primitive.name())) {
            throw new EffectLoadException("registry",
                    "duplicate primitive name '" + primitive.name() + "'");
        }
        primitives.put(primitive.name(), primitive);
    }

    /**
     * Publishes a bespoke handler under a key.
     *
     * <p>This is the only way an escape-hatch operation becomes loadable.
     * The v0 registry publishes none.</p>
     */
    public void registerBespokeHandler(String key, String javaHandler) {
        if (key == null || key.trim().length() == 0) {
            throw new IllegalArgumentException("bespoke handler key is null or empty");
        }
        bespokeHandlers.put(key.trim(), javaHandler);
    }

    /**
     * Looks up a primitive by name.
     *
     * @throws EffectLoadException when the name is not registered. This is
     *     A3 principle 4 in code: an unknown effect kind is a load error, and
     *     so is an unknown primitive.
     */
    public EffectPrimitive primitive(String name, String location) throws EffectLoadException {
        if (name == null || name.trim().length() == 0) {
            throw new EffectLoadException(location, "missing primitive name");
        }
        EffectPrimitive p = primitives.get(name.trim());
        if (p == null) {
            throw new EffectLoadException(location,
                    "unknown effect primitive '" + name.trim() + "'; the registry holds "
                    + primitives.size() + " primitive(s): " + knownPrimitiveNames());
        }
        return p;
    }

    /** Whether a primitive name is registered. */
    public boolean knows(String name) {
        return name != null && primitives.containsKey(name.trim());
    }

    /**
     * Validates an operation against the registry and returns its canonical
     * text.
     *
     * @throws EffectLoadException when the primitive is unknown, when its
     *     parameters are not one of the closed set it accepts, or when a
     *     bespoke operation names a handler the registry does not hold.
     */
    public String describe(EffectOperation op, String location) throws EffectLoadException {
        EffectPrimitive p = primitive(op == null ? null : op.getPrimitive(), location);
        String text = p.describe(op, location);
        p.checkRegistered(this, op, location);
        return text;
    }

    /** Whether a bespoke handler key is published. */
    public boolean hasBespokeHandler(String key) {
        return key != null && bespokeHandlers.containsKey(key.trim());
    }

    /** The published bespoke handlers, key to Java handler name. */
    public Map<String, String> bespokeHandlers() {
        return Collections.unmodifiableMap(bespokeHandlers);
    }

    /** The published bespoke handler keys, comma-separated. */
    public String knownBespokeHandlerKeys() {
        if (bespokeHandlers.isEmpty()) return "(none)";
        TreeSet<String> keys = new TreeSet<String>(bespokeHandlers.keySet());
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(k);
        }
        return sb.toString();
    }

    /** The registered primitive names, comma-separated and sorted. */
    public String knownPrimitiveNames() {
        if (primitives.isEmpty()) return "(none)";
        TreeSet<String> names = new TreeSet<String>(primitives.keySet());
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(n);
        }
        return sb.toString();
    }

    /** The registered primitive names, sorted. */
    public Set<String> primitiveNames() {
        return Collections.unmodifiableSet(new TreeSet<String>(primitives.keySet()));
    }

    /** The family names present, sorted. */
    public Set<String> knownFamilies() {
        TreeSet<String> families = new TreeSet<String>();
        for (EffectPrimitive p : primitives.values()) {
            families.add(p.family());
        }
        return Collections.unmodifiableSet(families);
    }

    /** How many primitives each family holds, sorted by family name. */
    public Map<String, Integer> familySizes() {
        Map<String, Integer> sizes = new TreeMap<String, Integer>();
        for (EffectPrimitive p : primitives.values()) {
            Integer prev = sizes.get(p.family());
            sizes.put(p.family(), Integer.valueOf(prev == null ? 1 : prev.intValue() + 1));
        }
        return Collections.unmodifiableMap(sizes);
    }

    /** The effect kinds the registered primitives cover, sorted. */
    public Set<EffectKind> knownKinds() {
        TreeSet<EffectKind> kinds = new TreeSet<EffectKind>();
        for (EffectPrimitive p : primitives.values()) {
            kinds.addAll(p.kinds());
        }
        return Collections.unmodifiableSet(kinds);
    }

    /** The number of registered primitives. */
    public int size() {
        return primitives.size();
    }

    /** The registered primitives, in registration order. */
    public List<EffectPrimitive> primitives() {
        return Collections.unmodifiableList(new ArrayList<EffectPrimitive>(primitives.values()));
    }
}