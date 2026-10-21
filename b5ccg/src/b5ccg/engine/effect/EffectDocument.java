package b5ccg.engine.effect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * WP-10 / B5-0028 (A3): a loaded effect document.
 *
 * <p>A document is a versioned set of effect entries. The version is the
 * document's own {@code schema_version}, and the loader refuses a document
 * whose version it does not understand - the same fail-closed rule that
 * governs an unknown effect kind, applied one level up.</p>
 *
 * <p>Immutable: the loader builds it and hands it over; nothing mutates an
 * entry afterwards, so a renderer can be run over the same document twice
 * and be expected to produce the same text both times.</p>
 */
public final class EffectDocument {

    /** The schema version this build loads. */
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final int schemaVersion;
    private final List<EffectEntry> entries;

    public EffectDocument(int schemaVersion, List<EffectEntry> entries) {
        this.schemaVersion = schemaVersion;
        List<EffectEntry> copy = new ArrayList<EffectEntry>();
        if (entries != null) {
            for (EffectEntry e : entries) {
                if (e != null) copy.add(e);
            }
        }
        this.entries = Collections.unmodifiableList(copy);
    }

    public int getSchemaVersion() { return schemaVersion; }

    /** The entries, in document order. */
    public List<EffectEntry> getEntries() { return entries; }

    /** How many entries the document holds. */
    public int size() { return entries.size(); }

    /**
     * The entries that belong to one card.
     *
     * <p>A card may carry more than one entry - "Contact with Shadows" draws
     * cards <em>and</em> provides a mark, and "Babylon 5 Unrest" changes two
     * different costs - so this returns a list, not a single entry.</p>
     */
    public List<EffectEntry> entriesFor(String cardId) {
        List<EffectEntry> out = new ArrayList<EffectEntry>();
        for (EffectEntry e : entries) {
            if (e.getCardId().equals(cardId)) out.add(e);
        }
        return out;
    }

    /** The distinct card ids the document describes, sorted. */
    public List<String> cardIds() {
        TreeSet<String> ids = new TreeSet<String>();
        for (EffectEntry e : entries) {
            ids.add(e.getCardId());
        }
        return Collections.unmodifiableList(new ArrayList<String>(ids));
    }

    @Override
    public String toString() {
        return "EffectDocument(v" + schemaVersion + ", " + entries.size() + " entries)";
    }
}
