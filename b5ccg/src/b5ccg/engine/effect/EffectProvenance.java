package b5ccg.engine.effect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WP-10 / B5-0028 (A3): where an effect entry came from.
 *
 * <p>A3's effect-entry shape names the last field "provenance: rule-IDs (A8),
 * text hash, printing scope". Three things, and each answers a question that
 * would otherwise be unanswerable:</p>
 * <ul>
 *   <li><b>rule-IDs</b> - which paragraphs of the pinned rulebook the entry
 *       rests on. A8's rule-ID index ({@code RB:<chapter>/<slug>}) is what
 *       makes the entry checkable against the rulebook rather than merely
 *       plausible. AGENTS.md section 5 requires every DECISIONS interpretation
 *       to cite at least one ID; the same discipline applied to data keeps an
 *       effect entry from being a piece of folklore.</li>
 *   <li><b>text hash</b> - the SHA-256 of the governing printed text the
 *       entry was written against. This is the mechanism that lets a later
 *       pass compare the entry with the card it describes, and it is what
 *       detects a data file that has drifted from the cards.</li>
 *   <li><b>printing scope</b> - which printing's text governs. D5 (the owner
 *       ruling recorded in {@code docs/DECISIONS.md}) makes the Deluxe
 *       printing govern a dual title, so an entry written from the Premiere
 *       text of a card whose Deluxe text differs is wrong, and the printing
 *       scope says which text was used.</li>
 * </ul>
 *
 * <p>Immutable.</p>
 */
public final class EffectProvenance {

    /** The printing scope used when a title prints in both sets and the
     *  Deluxe text governs (D5). */
    public static final String PRINTING_DELUXE_GOVERNS = "DELUXE_GOVERNS";

    /** The printing scope used when only one printing exists. */
    public static final String PRINTING_SINGLE = "SINGLE_PRINTING";

    private final List<String> ruleIds;
    private final String textSha256;
    private final String printingScope;

    public EffectProvenance(List<String> ruleIds, String textSha256, String printingScope) {
        List<String> copy = new ArrayList<String>();
        if (ruleIds != null) {
            for (String id : ruleIds) {
                if (id != null && id.trim().length() > 0) copy.add(id.trim());
            }
        }
        this.ruleIds = Collections.unmodifiableList(copy);
        this.textSha256 = textSha256;
        this.printingScope = printingScope;
    }

    /** The rulebook paragraphs this entry rests on; possibly empty, never null. */
    public List<String> getRuleIds() { return ruleIds; }

    /** The SHA-256 of the governing printed text, or null when not recorded. */
    public String getTextSha256() { return textSha256; }

    /** Which printing's text governs, or null when not recorded. */
    public String getPrintingScope() { return printingScope; }

    /** A canonical, order-stable description. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("rules=");
        if (ruleIds.isEmpty()) {
            sb.append("(none)");
        } else {
            for (int i = 0; i < ruleIds.size(); i++) {
                if (i > 0) sb.append('+');
                sb.append(ruleIds.get(i));
            }
        }
        sb.append("/printing=").append(printingScope == null ? "(none)" : printingScope);
        sb.append("/text=").append(textSha256 == null ? "(none)" : textSha256);
        return sb.toString();
    }

    @Override
    public String toString() {
        return describe();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EffectProvenance)) return false;
        EffectProvenance other = (EffectProvenance) o;
        return ruleIds.equals(other.ruleIds)
            && (textSha256 == null ? other.textSha256 == null : textSha256.equals(other.textSha256))
            && (printingScope == null ? other.printingScope == null
                                      : printingScope.equals(other.printingScope));
    }

    @Override
    public int hashCode() {
        int h = ruleIds.hashCode();
        h = h * 31 + (textSha256 == null ? 0 : textSha256.hashCode());
        h = h * 31 + (printingScope == null ? 0 : printingScope.hashCode());
        return h;
    }
}
