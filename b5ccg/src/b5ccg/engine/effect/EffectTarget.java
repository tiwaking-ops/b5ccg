package b5ccg.engine.effect;

import b5ccg.model.enums.CardType;
import b5ccg.model.enums.Race;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * WP-10 / B5-0028 (A3): the target of an effect.
 *
 * <p>A3's effect-entry shape lists the target fields as "card type; race or
 * loyalty filter (ANY is a filter, not a value); controller relation; row
 * (Inner Circle or Supporting); status (ready, rotated, neutralized); mark or
 * trait filter; scope (card, faction, race, global)", anchored at rulebook
 * :827-:832 and :1160.</p>
 *
 * <p><b>Empty means "no filter", not "filter that matches nothing".</b> An
 * empty {@link #getCardTypes()} means the effect does not care what kind of
 * card it lands on. This is the same distinction A3 draws when it says "ANY is
 * a filter, not a value" - a filter that is present and matches everything is
 * not the same as a filter that is absent, and the data is explicit about
 * which it means. {@link #getControllerRelation()} defaults to
 * {@link EffectControllerRelation#ANY} rather than to null, because "any
 * player" is a positive statement a card can make.</p>
 *
 * <p>Immutable; the sets are copied on construction and exposed
 * unmodifiable.</p>
 */
public final class EffectTarget {

    private final EffectScope scope;
    private final Set<CardType> cardTypes;
    private final Set<Race> races;
    private final Set<Race> racesNot;
    private final EffectControllerRelation controllerRelation;
    private final Set<EffectCardStatus> status;
    private final Set<EffectTrait> traits;
    private final boolean innerCircleOnly;
    private final boolean supportingOnly;
    private final boolean ambassadorOnly;
    private final boolean notAmbassador;
    private final Set<String> relationToTarget;
    private final String condition;
    private final EffectTarget protectedCharacter;

    /**
     * A target with no filters beyond the scope and the controller relation.
     */
    public EffectTarget(EffectScope scope, EffectControllerRelation controllerRelation) {
        this(scope, controllerRelation,
             Collections.<CardType>emptySet(), Collections.<Race>emptySet(),
             Collections.<Race>emptySet(), Collections.<EffectCardStatus>emptySet(),
             Collections.<EffectTrait>emptySet(),
             false, false, false, false,
             Collections.<String>emptySet(), null, null);
    }

    public EffectTarget(EffectScope scope,
                        EffectControllerRelation controllerRelation,
                        Set<CardType> cardTypes,
                        Set<Race> races,
                        Set<Race> racesNot,
                        Set<EffectCardStatus> status,
                        Set<EffectTrait> traits,
                        boolean innerCircleOnly,
                        boolean supportingOnly,
                        boolean ambassadorOnly,
                        boolean notAmbassador,
                        Set<String> relationToTarget,
                        String condition,
                        EffectTarget protectedCharacter) {
        if (scope == null) {
            throw new IllegalArgumentException("target scope is null");
        }
        if (controllerRelation == null) {
            throw new IllegalArgumentException("controller relation is null");
        }
        this.scope = scope;
        this.controllerRelation = controllerRelation;
        this.cardTypes = copy(cardTypes);
        this.races = copy(races);
        this.racesNot = copy(racesNot);
        this.status = copy(status);
        this.traits = copy(traits);
        this.innerCircleOnly = innerCircleOnly;
        this.supportingOnly = supportingOnly;
        this.ambassadorOnly = ambassadorOnly;
        this.notAmbassador = notAmbassador;
        this.relationToTarget = copyStrings(relationToTarget);
        this.condition = condition;
        this.protectedCharacter = protectedCharacter;
    }

    private static <T> Set<T> copy(Set<T> in) {
        Set<T> out = new LinkedHashSet<T>();
        if (in != null) {
            for (T t : in) {
                if (t != null) out.add(t);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    private static Set<String> copyStrings(Set<String> in) {
        Set<String> out = new LinkedHashSet<String>();
        if (in != null) {
            for (String s : in) {
                if (s != null) out.add(s);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    public EffectScope getScope() { return scope; }

    /** The card types the effect may land on; empty means any. */
    public Set<CardType> getCardTypes() { return cardTypes; }

    /** The races the effect may land on; empty means any. */
    public Set<Race> getRaces() { return races; }

    /** The races the effect may NOT land on; empty means no exclusion. */
    public Set<Race> getRacesNot() { return racesNot; }

    public EffectControllerRelation getControllerRelation() { return controllerRelation; }

    /** The card statuses the effect may land on; empty means any. */
    public Set<EffectCardStatus> getStatus() { return status; }

    /** The printed traits the target must carry; empty means any. */
    public Set<EffectTrait> getTraits() { return traits; }

    /** Whether the target must be in the controller's Inner Circle. */
    public boolean isInnerCircleOnly() { return innerCircleOnly; }

    /** Whether the target must be in the controller's Supporting Role. */
    public boolean isSupportingOnly() { return supportingOnly; }

    /**
     * Whether the target must be the controller's ambassador.
     *
     * <p>Rulebook :838: "Marks provided to a faction without specifying a
     * target are attached to the faction's ambassador." That is the default
     * target of a mark grant, and the engine's mark tables already attach
     * agenda, enhancement and event marks to the ambassador
     * ({@code CardEffects}'s {@code MarkGrant.Target.AMBASSADOR}).</p>
     */
    public boolean isAmbassadorOnly() { return ambassadorOnly; }

    /** Whether the target must NOT be an ambassador. */
    public boolean isNotAmbassador() { return notAmbassador; }

    /** The relation of the target to the source card (ATTACKER, DEFENDER, etc.). */
    public Set<String> getRelationToTarget() { return relationToTarget; }

    /** An inline condition key for complex target filters (e.g., WAS_PLAYED_BY_YOU_AND_CONQUERED). */
    public String getCondition() { return condition; }

    /** A secondary target for dual-target effects (e.g., the protected character in Security Training). */
    public EffectTarget getProtectedCharacter() { return protectedCharacter; }

    /**
     * A canonical, order-stable description of the target's filters.
     *
     * <p>Order-stable because {@link TreeSet} is used for the rendering: two
     * entries that name the same filters in a different order must render
     * identically, or the renderer is not canonical and cannot be compared.</p>
     */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(scope.name());
        sb.append("/ctrl=").append(controllerRelation.name());
        appendSet(sb, "type", cardTypes);
        appendSet(sb, "race", races);
        if (!racesNot.isEmpty()) appendSet(sb, "raceNot", racesNot);
        appendSet(sb, "status", status);
        appendSet(sb, "trait", traits);
        if (innerCircleOnly) sb.append("/row=INNER_CIRCLE");
        if (supportingOnly) sb.append("/row=SUPPORTING");
        if (ambassadorOnly) sb.append("/ambassador");
        if (notAmbassador) sb.append("/notAmbassador");
        if (!relationToTarget.isEmpty()) {
            TreeSet<String> sorted = new TreeSet<String>(relationToTarget);
            sb.append("/relation=");
            boolean first = true;
            for (String n : sorted) {
                if (!first) sb.append('+');
                sb.append(n);
                first = false;
            }
        }
        if (condition != null) sb.append("/cond=").append(condition);
        if (protectedCharacter != null) sb.append("/protects=").append(protectedCharacter.describe());
        return sb.toString();
    }

    private static void appendSet(StringBuilder sb, String label, Set<?> set) {
        if (set == null || set.isEmpty()) return;
        TreeSet<String> names = new TreeSet<String>();
        for (Object o : set) {
            names.add(String.valueOf(o));
        }
        sb.append('/').append(label).append('=');
        boolean first = true;
        for (String n : names) {
            if (!first) sb.append('+');
            sb.append(n);
            first = false;
        }
    }

    @Override
    public String toString() {
        return describe();
    }
}
