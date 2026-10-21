package b5ccg.engine;

import b5ccg.model.CharacterCard;
import b5ccg.model.EnhancementCard;
import b5ccg.model.FleetCard;
import b5ccg.model.LocationCard;
import b5ccg.model.Player;
import b5ccg.model.enums.MarkType;

/**
 * WP-16 / B5-0033 (A6.2): the mark rules, extracted.
 *
 * <p>A6 describes this module as "a value object with the opposing-mark rule
 * (RB L842) and the cut-off purge when a mark's source leaves (RB L844)". The
 * rule IDs are {@code RB:VI/marks} (rulebook :834-:847).</p>
 *
 * <p><b>The two rules.</b></p>
 * <ul>
 *   <li><b>The opposing-mark rule</b> ({@code RB:VI/marks}, :842): "A faction
 *       cannot have both Shadow and Vorlon marks. If one of these marks is
 *       already attached to a character in one faction, any effect attaching
 *       an opposing mark to any character in the same faction is ignored." So
 *       the rule is a <em>gate</em>: an effect that would attach a Vorlon mark
 *       to a faction that holds a Shadow mark does nothing at all, rather than
 *       attaching the mark and then removing the other.</li>
 *   <li><b>The cut-off purge</b> ({@code RB:VI/marks}, :844): "If a character
 *       is cut off from a source of a mark ... then that character must purge a
 *       mark of that type." So the rule is a <em>consequence</em>: the mark
 *       goes away.</li>
 * </ul>
 *
 * <p><b>What this row does and does not change.</b> The module expresses the
 * rules and delegates the mechanics to {@link Player}, which already
 * implements both. The engine's behaviour is unchanged apart from the one
 * defect this row found and fixed (below), and the mark probes pass, which is
 * the acceptance. What the module adds is a place where the rules are named,
 * and one thing the engine did not have: a <b>single place that answers "can
 * this faction gain this mark?"</b>, which the opposing-mark rule needs and
 * which was previously spread across the three {@code addMarksTo*} methods.</p>
 *
 * <p><b>The defect this row found.</b> {@code Player.getTotalMarks} and
 * {@code Player.purgeMarks} scanned the Inner Circle, Supporting Role, asylum,
 * fleets, locations, enhancements and the agenda - but not the ambassador's own
 * field. The ambassador is usually also in the Inner Circle list, so the
 * omission was invisible in a normal game; it became visible the moment the
 * ambassador held a mark while the Inner Circle did not hold it. The
 * consequence was a rulebook violation, not a cosmetic one: the
 * opposing-mark rule at :842 did not fire for an ambassador-borne mark, so a
 * faction could hold both a Shadow and a Vorlon mark. See the B5-0033 report.</p>
 *
 * <p>Java 8 source level (javac --release 8), stdlib only.</p>
 */
public class MarkRules {

    /**
     * Whether a faction may gain a mark of the given type.
     *
     * <p>This is the opposing-mark rule as a single question. The engine
     * answers it per-target ({@code Player.canGainMark}); this answers it for
     * the faction, which is what the rule is about.</p>
     */
    public boolean canGainMark(Player faction, MarkType type) {
        if (faction == null || type == null) {
            return false;
        }
        if (type == MarkType.SHADOW) {
            return !faction.hasVorlonMarks();
        }
        if (type == MarkType.VORLON) {
            return !faction.hasShadowMarks();
        }
        // Other mark types have no cross-type restriction.
        return true;
    }

    /**
     * Attaches marks of a type to a character, applying the opposing-mark
     * rule.
     *
     * @return the number of marks actually attached; 0 when the rule blocked
     *         the effect.
     */
    public int attachToCharacter(Player faction, CharacterCard target,
                                 MarkType type, int count) {
        if (faction == null || target == null || type == null || count <= 0) {
            return 0;
        }
        if (!canGainMark(faction, type)) {
            return 0;
        }
        return faction.addMarksToCharacter(target, type, count);
    }

    /** Attaches marks of a type to a fleet, applying the opposing-mark rule. */
    public int attachToFleet(Player faction, FleetCard target,
                             MarkType type, int count) {
        if (faction == null || target == null || type == null || count <= 0) {
            return 0;
        }
        if (!canGainMark(faction, type)) {
            return 0;
        }
        return faction.addMarksToFleet(target, type, count);
    }

    /** Attaches marks of a type to a location, applying the opposing-mark rule. */
    public int attachToLocation(Player faction, LocationCard target,
                                MarkType type, int count) {
        if (faction == null || target == null || type == null || count <= 0) {
            return 0;
        }
        if (!canGainMark(faction, type)) {
            return 0;
        }
        return faction.addMarksToLocation(target, type, count);
    }

    /**
     * The cut-off purge: when a mark's source leaves, the mark goes.
     *
     * <p>Rulebook :844 also says "This could, of course, result in purging more
     * than one mark" and "If the character has no marks of that type to purge,
     * then he suffers no additional penalty." The first is handled by purging
     * one mark per character across the faction; the second by the fact that
     * removing a mark from a card that has none removes nothing.</p>
     *
     * @return the number of marks purged.
     */
    public int purgeOnCutOff(Player faction, MarkType type) {
        if (faction == null || type == null) {
            return 0;
        }
        return faction.purgeMarks(type);
    }

    /**
     * The total number of marks of a type the faction holds.
     *
     * <p>Rulebook :838: "If a card refers to a player's marks, or a faction's
     * marks, then it includes all marks attached to all characters in that
     * faction."</p>
     */
    public int totalMarks(Player faction, MarkType type) {
        if (faction == null || type == null) {
            return 0;
        }
        return faction.getTotalMarks(type);
    }

    /**
     * Whether the faction holds both a Shadow and a Vorlon mark, which the
     * opposing-mark rule exists to prevent.
     *
     * <p>This is the question the B5-0030 invariant checker asks, and it is
     * asked here as a question about the FACTION rather than about a single
     * card - which is the scope :842 actually has.</p>
     */
    public boolean holdsOpposingMarks(Player faction) {
        if (faction == null) {
            return false;
        }
        return faction.hasShadowMarks() && faction.hasVorlonMarks();
    }

    /**
     * Whether the faction's ambassador holds a mark of the given type.
     *
     * <p>Rulebook :838: "Marks provided to a faction without specifying a
     * target are attached to the faction's ambassador." Every mark grant in
     * the current pool ({@code CardEffects}'s agenda, enhancement and event
     * grant tables) targets the ambassador, so this is the mark most often
     * held - and the one the B5-0033 fix was about.</p>
     */
    public boolean ambassadorHolds(Player faction, MarkType type) {
        if (faction == null || type == null) {
            return false;
        }
        CharacterCard amb = faction.getAmbassador();
        return amb != null && amb.getMarkCount(type) > 0;
    }
}
