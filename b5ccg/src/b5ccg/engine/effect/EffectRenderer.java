package b5ccg.engine.effect;

import java.util.List;

/**
 * WP-10 / B5-0028 (A3): the canonical renderer.
 *
 * <p>A3's validation paragraph asks for two things the engine did not have:
 * "A renderer produces a canonical description from each effect entry, and a
 * separate pass compares it with the printed text: the data equivalent of a
 * probe." This class is the first of the two.</p>
 *
 * <p><b>Canonical means stable, not pretty.</b> The same entry always renders
 * to the same string, whatever order the data file happened to list its
 * filters in: the target's filters are sorted, the operation's parameters are
 * sorted, and the operations themselves keep their order, because the
 * operation order is data (A3 principle 6 - the rulebook's precedence rules
 * are the evaluation order).</p>
 *
 * <p><b>The renderer never reads the card's text.</b> A3 principle 3 is "Text
 * is the specification and the display string, never parsed at runtime." A
 * renderer that parsed the printed text would be doing exactly what principle
 * 3 forbids, and the comparison pass would be comparing a thing with itself.
 * The renderer works from the entry alone; the comparison is a separate pass
 * that reads the text and the render side by side.</p>
 *
 * <p>The format is one line per entry:</p>
 * <pre>
 * &lt;cardId&gt; [&lt;KIND&gt;] window=&lt;W&gt; trigger=&lt;T&gt; target=&lt;...&gt; ops=[...] duration=&lt;D&gt; stacking=&lt;S&gt; protection=&lt;P&gt; precedence=&lt;L&gt; binding=&lt;B&gt; rules=&lt;ids&gt;
 * </pre>
 */
public final class EffectRenderer {

    /** The version of the render format. Bump it when the format changes. */
    public static final int RENDER_FORMAT_VERSION = 1;

    private final EffectRegistry registry;

    public EffectRenderer(EffectRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry is null");
        }
        this.registry = registry;
    }

    /**
     * Renders one entry.
     *
     * @throws EffectLoadException when an operation names a primitive or
     *     parameters the registry does not accept. A document that loaded
     *     successfully always renders; this exception means the entry was
     *     built by hand, not loaded.
     */
    public String render(EffectEntry entry) throws EffectLoadException {
        if (entry == null) {
            throw new EffectLoadException("render", "null entry");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(entry.getCardId());
        sb.append(" \"").append(entry.getTitle()).append('"');
        sb.append(" [").append(entry.getKind().name()).append(']');
        sb.append(" window=").append(entry.getWindow().name());
        sb.append(" trigger=")
          .append(entry.getTrigger() == null ? "(none)" : entry.getTrigger().name());
        sb.append(" target=").append(entry.getTarget().describe());
        sb.append(" ops=[");
        List<EffectOperation> ops = entry.getOperations();
        for (int i = 0; i < ops.size(); i++) {
            if (i > 0) sb.append("; ");
            sb.append(registry.describe(ops.get(i), "render[" + entry.getCardId() + "]"));
            // A permitted choice ("may") is marked, so that it never renders
            // the same way as a compulsory effect ("must").
            if (ops.get(i).isOptional()) sb.append('?');
        }
        sb.append(']');
        sb.append(" duration=").append(entry.getDuration().name());
        sb.append(" stacking=").append(entry.getStacking().name());
        sb.append(" protection=").append(entry.getProtection().name());
        sb.append(" precedence=").append(entry.getPrecedenceLayer().name());
        sb.append(" binding=").append(entry.getSourceBinding().name());
        sb.append(" rules=");
        EffectProvenance prov = entry.getProvenance();
        if (prov == null || prov.getRuleIds().isEmpty()) {
            sb.append("(none)");
        } else {
            for (int i = 0; i < prov.getRuleIds().size(); i++) {
                if (i > 0) sb.append('+');
                sb.append(prov.getRuleIds().get(i));
            }
        }
        return sb.toString();
    }

    /**
     * Renders a whole document, one line per entry, in document order.
     */
    public String render(EffectDocument document) throws EffectLoadException {
        if (document == null) {
            throw new EffectLoadException("render", "null document");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("effect-document v").append(document.getSchemaVersion())
          .append(" entries=").append(document.size()).append('\n');
        List<EffectEntry> entries = document.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            sb.append(render(entries.get(i))).append('\n');
        }
        return sb.toString();
    }
}
