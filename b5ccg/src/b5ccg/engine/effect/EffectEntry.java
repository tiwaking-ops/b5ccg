package b5ccg.engine.effect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WP-10 / B5-0028 (A3): one effect entry.
 *
 * <p>A3's "Effect entry: shape" table gives fourteen fields. This class holds
 * all fourteen, in the order the table gives them, plus the entry's identity
 * (the card it belongs to and its title), which the table does not list
 * because a table of fields is not a data format. The identity is recorded
 * here rather than in a side table because an effect entry without the card it
 * describes cannot be checked against anything.</p>
 *
 * <table>
 *   <caption>The fourteen fields, with A3's own meanings</caption>
 *   <tr><td>kind</td>              <td>what sort of effect this is</td></tr>
 *   <tr><td>window</td>            <td>the round or step it can fire in</td></tr>
 *   <tr><td>trigger</td>           <td>the event plus filter that sets it off</td></tr>
 *   <tr><td>requirements</td>      <td>the cost block</td></tr>
 *   <tr><td>target</td>            <td>what it acts on</td></tr>
 *   <tr><td>conditions</td>        <td>the predicates that must hold</td></tr>
 *   <tr><td>operations</td>        <td>the ordered list of typed primitives</td></tr>
 *   <tr><td>duration</td>          <td>how long it lasts</td></tr>
 *   <tr><td>stacking</td>          <td>additive or replacing</td></tr>
 *   <tr><td>protection</td>        <td>irrevocable or boldface</td></tr>
 *   <tr><td>precedenceLayer</td>   <td>which layer of the precedence rules it sits in</td></tr>
 *   <tr><td>sourceBinding</td>     <td>the cut-off purge when its source leaves</td></tr>
 *   <tr><td>provenance</td>        <td>rule-IDs, text hash, printing scope</td></tr>
 * </table>
 *
 * <p><b>Immutable.</b> An entry is a fact about a card, not a thing the engine
 * mutates. The lists are copied on construction and exposed unmodifiable.</p>
 */
public final class EffectEntry {

    private final String cardId;
    private final String title;
    private final EffectKind kind;
    private final EffectWindow window;
    private final EffectTrigger trigger;
    private final List<EffectRequirement> requirements;
    private final EffectTarget target;
    private final List<EffectCondition> conditions;
    private final List<EffectOperation> operations;
    private final EffectDuration duration;
    private final EffectStacking stacking;
    private final EffectProtection protection;
    private final EffectPrecedence precedenceLayer;
    private final EffectSourceBinding sourceBinding;
    private final EffectProvenance provenance;

    public EffectEntry(String cardId,
                       String title,
                       EffectKind kind,
                       EffectWindow window,
                       EffectTrigger trigger,
                       List<EffectRequirement> requirements,
                       EffectTarget target,
                       List<EffectCondition> conditions,
                       List<EffectOperation> operations,
                       EffectDuration duration,
                       EffectStacking stacking,
                       EffectProtection protection,
                       EffectPrecedence precedenceLayer,
                       EffectSourceBinding sourceBinding,
                       EffectProvenance provenance) {
        if (cardId == null || cardId.trim().length() == 0) {
            throw new IllegalArgumentException("effect entry cardId is null or empty");
        }
        if (kind == null) throw new IllegalArgumentException("effect entry kind is null");
        if (window == null) throw new IllegalArgumentException("effect entry window is null");
        if (target == null) throw new IllegalArgumentException("effect entry target is null");
        if (duration == null) throw new IllegalArgumentException("effect entry duration is null");
        if (stacking == null) throw new IllegalArgumentException("effect entry stacking is null");
        if (protection == null) throw new IllegalArgumentException("effect entry protection is null");
        if (precedenceLayer == null) {
            throw new IllegalArgumentException("effect entry precedenceLayer is null");
        }
        if (sourceBinding == null) {
            throw new IllegalArgumentException("effect entry sourceBinding is null");
        }
        this.cardId = cardId.trim();
        this.title = title == null ? "" : title;
        this.kind = kind;
        this.window = window;
        this.trigger = trigger;
        this.requirements = immutableList(requirements);
        this.target = target;
        this.conditions = immutableList(conditions);
        this.operations = immutableList(operations);
        this.duration = duration;
        this.stacking = stacking;
        this.protection = protection;
        this.precedenceLayer = precedenceLayer;
        this.sourceBinding = sourceBinding;
        this.provenance = provenance;
    }

    private static <T> List<T> immutableList(List<T> in) {
        List<T> out = new ArrayList<T>();
        if (in != null) {
            for (T t : in) {
                if (t != null) out.add(t);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** The canonical id of the card this entry describes. */
    public String getCardId() { return cardId; }

    /** The card's printed title, for display. */
    public String getTitle() { return title; }

    public EffectKind getKind() { return kind; }

    public EffectWindow getWindow() { return window; }

    /** The trigger, or null when the effect is not triggered. */
    public EffectTrigger getTrigger() { return trigger; }

    public List<EffectRequirement> getRequirements() { return requirements; }

    public EffectTarget getTarget() { return target; }

    public List<EffectCondition> getConditions() { return conditions; }

    /** The operations, in the order the effect applies them. */
    public List<EffectOperation> getOperations() { return operations; }

    public EffectDuration getDuration() { return duration; }

    public EffectStacking getStacking() { return stacking; }

    public EffectProtection getProtection() { return protection; }

    /** The precedence layer the effect sits in. */
    public EffectPrecedence getPrecedenceLayer() { return precedenceLayer; }

    /** The source binding (the cut-off purge), if any. */
    public EffectSourceBinding getSourceBinding() { return sourceBinding; }

    /** The provenance, or null when the entry carries none. */
    public EffectProvenance getProvenance() { return provenance; }

    @Override
    public String toString() {
        return cardId + "[" + kind.name() + "]";
    }
}
