package b5ccg.engine.effect.primitives;

import b5ccg.engine.effect.EffectKind;
import b5ccg.engine.effect.EffectLoadException;
import b5ccg.engine.effect.EffectOperation;
import b5ccg.engine.effect.EffectPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * WP-10 / B5-0028 (A3): the restriction family.
 *
 * <p>One primitive: {@code RESTRICT}, which forbids something the rulebook
 * would otherwise allow. The rulebook's precedence rules say a "cannot"
 * overrides any enabling effect ({@code RB:VI/rules-of-precedence}), and
 * "Shadow Strike" carries one: "Your characters cannot be healed after this
 * conflict if you lose."</p>
 *
 * <p>The {@code restriction} parameter names the restriction. It is free
 * text rather than a closed enum, for the same reason the state family uses
 * free text: the set of things a card can forbid is the set of things the
 * rulebook allows, and the rulebook does not close it.</p>
 */
public final class RestrictionPrimitives {

    /** The family name, as the registry reports it. */
    public static final String FAMILY = "RESTRICTION";

    /** Something is forbidden. */
    public static final EffectPrimitive RESTRICT = new SimplePrimitive(
            "RESTRICT", FAMILY, EffectKind.RESTRICTION, EffectKind.ONGOING) {
        @Override
        protected String body(EffectOperation op, String location) throws EffectLoadException {
            return "restriction=" + requiredText(op, "restriction", location);
        }
    };

    private RestrictionPrimitives() { }

    /** Every primitive in this family. */
    public static List<EffectPrimitive> all() {
        return Collections.unmodifiableList(Arrays.asList(RESTRICT));
    }
}
