package b5ccg.engine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * B5-0004 (WP-03): printing-diff triage scenario probe for the 9 single-literal
 * titles that carry a Premiere/Deluxe id pair.
 *
 * <p>Verifies that both ids in each pair resolve consistently under
 * canonical-key routing ({@link CardIdNamespaceGuard#canonicalKey},
 * {@link CardEffects#lookupMap}, {@link CardEffects#lookupSet}): the Deluxe
 * id collapses onto its Premiere twin when text is identical, and stands on its
 * own exact entry when text differs (D5 Deluxe-governs).</p>
 *
 * <p>Three scenario groups, matching the B5-0004 classification:</p>
 * <ul>
 *   <li><b>5 changed-text ambassadors</b> (Adira Tyree, Delenn, G'Kar, Jeffrey
 *       Sinclair, Londo Mollari): both ids resolve through
 *       {@link CardEffects#CHARACTER_BEHAVIOUR_REFERENCES} — Premiere exact,
 *       Deluxe via canonical fallback. Deluxe ids also resolve through
 *       {@link CardEffects#DELUXE_CHARACTER_EFFECTS} (exact), and Premiere ids
 *       correctly do <em>not</em> — confirming D5 separation: the Premiere text
 *       differs, so the Premiere id has no entry in the Deluxe table.</li>
 *   <li><b>1 unchanged-text event</b> (Intrigues Mature): both ids resolve
 *       through {@link CardEffects#EVENT_DRAW} to the same value (2) —
 *       Premiere exact, Deluxe via canonical routing. The Deluxe id is never a
 *       table key (canonical routing handles it at lookup time).</li>
 *   <li><b>3 unchanged-text VANILLA characters</b> (Centauri Agent, Centauri
 *       Aide, Centauri Captain): both ids are consistently absent from every
 *       behaviour table — these are covered=false by design (no behaviour to
 *       table). The probe asserts neither id appears in the scan result.</li>
 * </ul>
 *
 * <p>Negative controls:</p>
 * <ul>
 *   <li>Aftermath slug divergence: {@code aftermath_X} / {@code de_am_X} do NOT
 *       collapse via canonical routing — {@code canonicalKey("de_am_X")} yields
 *       {@code "am_X"}, which matches neither {@code "aftermath_X"} nor any
 *       table entry. Both printings keep exact literals by design.</li>
 * </ul>
 *
 * <p>Mutation M1 proves canonical routing is load-bearing: a Premiere-only
 * table entry resolves the Deluxe id through {@link CardEffects#lookupMap} but
 * not through a direct {@code Map.get} — without the routing the Deluxe twin
 * silently fails to resolve.</p>
 *
 * <p>Gate tier: pure 0/1 exit contract — 0 clean, 1 findings present. Wired
 * into the {@code RUN_TESTS=1} gate in compile.sh after
 * HeadlessCanonicalParityProbe.</p>
 *
 * <p>Java 6 (javac -source 6 -target 6), stdlib only.</p>
 */
public class HeadlessPrintingDiffTriageProbe {

    private static int checks = 0;
    private static int failures = 0;

    /**
     * The 9 single-literal printing-diff titles, grouped by B5-0004 classification.
     * Each row: {premiereId, deluxeId, title}.
     */

    // ── 5 changed-text ambassadors (D5 Deluxe-governs) ──────────────────────────
    // Premiere ids live in CHARACTER_BEHAVIOUR_REFERENCES; Deluxe ids live in
    // DELUXE_CHARACTER_EFFECTS. Canonical routing lets the Premiere id resolve
    // through the Deluxe table too (it doesn't — D5 separation), and the Deluxe
    // id resolve through the Premiere table (it does — canonical fallback).
    private static final String[][] CHANGED_TEXT_AMBASSADORS = {
        {"char_adira_tyree",         "de_char_adira_tyree",         "Adira Tyree"},
        {"char_delenn",              "de_char_delenn",              "Delenn"},
        {"char_gkar",                "de_char_gkar",                "G'Kar"},
        {"char_jeffrey_sinclair",    "de_char_jeffrey_sinclair",    "Jeffrey Sinclair"},
        {"char_londo_mollari",       "de_char_londo_mollari",       "Londo Mollari"},
    };

    // ── 1 unchanged-text event with a table entry ──────────────────────────────
    // Premiere id in EVENT_DRAW; Deluxe id resolves solely via canonical routing
    // (the Deluxe card's text is identical, so no Deluxe literal is kept).
    private static final String[][] UNCHANGED_TEXT_EVENT = {
        {"event_intrigues_mature", "de_event_intrigues_mature", "Intrigues Mature"},
    };

    // ── 3 unchanged-text VANILLA characters (no behaviour table entry) ─────────
    // covered=false by design — these cards have no behaviour effect to table.
    private static final String[][] VANILLA_NO_TABLE = {
        {"char_centauri_agent",    "de_char_centauri_agent",    "Centauri Agent"},
        {"char_centauri_aide",     "de_char_centauri_aide",     "Centauri Aide"},
        {"char_centauri_captain",  "de_char_centauri_captain",  "Centauri Captain"},
    };

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable th) {
            th.printStackTrace(System.err);
            System.out.println("HeadlessPrintingDiffTriageProbe: FAILED with exception");
            System.exit(1);
        }
        System.out.println("HeadlessPrintingDiffTriageProbe: " + (checks - failures)
            + "/" + checks + " checks passed");
        if (failures > 0) {
            System.out.println("HeadlessPrintingDiffTriageProbe: FAILED (" + failures + ")");
            System.exit(1);
        }
        System.out.println("HeadlessPrintingDiffTriageProbe: PASSED");
    }

    private static void run() {
        System.out.println("PRINTING-DIFF TRIAGE PROBE (B5-0004)");

        // ── 1. Changed-text ambassadors: D5 Deluxe-governs ───────────────────────
        for (int i = 0; i < CHANGED_TEXT_AMBASSADORS.length; i++) {
            String prem = CHANGED_TEXT_AMBASSADORS[i][0];
            String delx = CHANGED_TEXT_AMBASSADORS[i][1];
            String title = CHANGED_TEXT_AMBASSADORS[i][2];

            // 1a. Both ids resolve through CHARACTER_BEHAVIOUR_REFERENCES.
            //     Premiere: exact match.  Deluxe: canonical fallback.
            check("[" + title + "] Premiere id resolves through CHARACTER_BEHAVIOUR_REFERENCES",
                CardEffects.lookupSet(CardEffects.CHARACTER_BEHAVIOUR_REFERENCES, prem),
                prem + " not found (Premiere exact lookup failed)");
            check("[" + title + "] Deluxe id resolves through CHARACTER_BEHAVIOUR_REFERENCES "
                + "(canonical routing)",
                CardEffects.lookupSet(CardEffects.CHARACTER_BEHAVIOUR_REFERENCES, delx),
                delx + " not found (Deluxe canonical fallback failed)");

            // 1b. Deluxe id resolves through DELUXE_CHARACTER_EFFECTS (exact).
            check("[" + title + "] Deluxe id resolves through DELUXE_CHARACTER_EFFECTS",
                CardEffects.lookupSet(CardEffects.DELUXE_CHARACTER_EFFECTS, delx),
                delx + " not found in Deluxe table");

            // 1c. Premiere id must NOT resolve through DELUXE_CHARACTER_EFFECTS —
            //     D5 separation: the text differs, so the Premiere id has no
            //     entry there, and canonical routing must not fabricate one.
            //     canonicalKey("char_adira_tyree") == "char_adira_tyree" (no de_ prefix),
            //     so the canonical fallback also cannot match.
            check("[" + title + "] Premiere id does NOT resolve through "
                + "DELUXE_CHARACTER_EFFECTS (D5 separation)",
                !CardEffects.lookupSet(CardEffects.DELUXE_CHARACTER_EFFECTS, prem),
                prem + " falsely resolved in Deluxe table via canonical routing");

            // 1d. Both ids are surfaced by the scan (behaviourReferences).
            Set<String> refs = CardEffects.behaviourReferences();
            check("[" + title + "] Premiere id in behaviourReferences() scan",
                refs.contains(prem), prem + " absent from scan");
            check("[" + title + "] Deluxe id in behaviourReferences() scan",
                refs.contains(delx), delx + " absent from scan");
        }

        // ── 2. Unchanged-text event: canonical routing unifies the twin ──────────
        // event_intrigues_mature sits in EVENT_DRAW; de_event_intrigues_mature
        // is never a table key (identical text), so it must resolve through
        // canonical fallback alone.
        for (int i = 0; i < UNCHANGED_TEXT_EVENT.length; i++) {
            String prem = UNCHANGED_TEXT_EVENT[i][0];
            String delx = UNCHANGED_TEXT_EVENT[i][1];
            String title = UNCHANGED_TEXT_EVENT[i][2];

            Integer premVal = CardEffects.lookupMap(CardEffects.EVENT_DRAW, prem);
            Integer delxVal = CardEffects.lookupMap(CardEffects.EVENT_DRAW, delx);

            check("[" + title + "] Premiere id resolves through EVENT_DRAW",
                premVal != null, prem + " -> null (expected a draw value)");
            check("[" + title + "] Deluxe id resolves through EVENT_DRAW "
                + "(canonical routing)",
                delxVal != null, delx + " -> null (canonical fallback failed)");
            check("[" + title + "] both ids resolve to the SAME value in EVENT_DRAW",
                premVal != null && premVal.equals(delxVal),
                prem + " -> " + premVal + " vs " + delx + " -> " + delxVal);

            // The Deluxe id is never a table key — it only resolves via routing.
            check("[" + title + "] Deluxe id is NOT a direct EVENT_DRAW key "
                + "(resolved only via canonical routing)",
                !CardEffects.EVENT_DRAW.containsKey(delx),
                delx + " unexpectedly present as exact key");
        }

        // ── 3. VANILLA characters: consistently absent ─────────────────────────
        // Centauri Agent/Aide/Captain are VANILLA/STAT_MODIFIER with no behaviour
        // table entry. Both printings must be absent from behaviourReferences()
        // — this is the covered=false contract, verified by this probe rather
        // than relying on a stale assertion.
        Set<String> refs = CardEffects.behaviourReferences();
        for (int i = 0; i < VANILLA_NO_TABLE.length; i++) {
            String prem = VANILLA_NO_TABLE[i][0];
            String delx = VANILLA_NO_TABLE[i][1];
            String title = VANILLA_NO_TABLE[i][2];

            check("[" + title + "] Premiere id absent from behaviourReferences()",
                !refs.contains(prem), prem + " unexpectedly in scan");
            check("[" + title + "] Deluxe id absent from behaviourReferences()",
                !refs.contains(delx), delx + " unexpectedly in scan");

            // Both must also be absent from every lookup table tested above.
            check("[" + title + "] Premiere id absent from "
                + "CHARACTER_BEHAVIOUR_REFERENCES",
                !CardEffects.lookupSet(CardEffects.CHARACTER_BEHAVIOUR_REFERENCES, prem),
                prem + " unexpectedly present");
            check("[" + title + "] Deluxe id absent from "
                + "CHARACTER_BEHAVIOUR_REFERENCES",
                !CardEffects.lookupSet(CardEffects.CHARACTER_BEHAVIOUR_REFERENCES, delx),
                delx + " unexpectedly present");
        }

        // ── 4. Negative control: aftermath slug divergence ───────────────────
        // aftermath_X and de_am_X do NOT collapse via canonical routing.
        // canonicalKey("de_am_rise_to_power") = "am_rise_to_power",
        // which differs from "aftermath_rise_to_power" — so both printings
        // keep exact literals by design.
        String premAm = "aftermath_rise_to_power";
        String delxAm = "de_am_rise_to_power";
        String canonPrem = CardIdNamespaceGuard.canonicalKey(premAm);
        String canonDelx = CardIdNamespaceGuard.canonicalKey(delxAm);
        check("aftermath slug divergence: canonical keys differ",
            !canonPrem.equals(canonDelx),
            "canon(" + premAm + ")=" + canonPrem + " == canon(" + delxAm + ")=" + canonDelx
            + " — slugs collapsed, which would silently unify the two printings");
        check("aftermath slug divergence: canon(aftermath_*) is unchanged",
            premAm.equals(canonPrem),
            "got " + canonPrem);
        check("aftermath slug divergence: canon(de_am_*) strips to am_* not aftermath_*",
            canonDelx.equals("am_rise_to_power"),
            "got " + canonDelx);

        // ── 5. Mutation M1: canonical routing is load-bearing ───────────────────
        // Build a Premiere-only table (mirroring the EVENT_DRAW unchanged-text
        // twin pattern). Without canonical routing, the Deluxe id cannot resolve.
        Map<String, Integer> m1 = new HashMap<String, Integer>();
        m1.put("event_intrigues_mature", Integer.valueOf(2));
        // Direct get — no routing — must miss the Deluxe id.
        check("mutation 1: direct Map.get misses Deluxe id (no routing)",
            m1.get("de_event_intrigues_mature") == null,
            "direct get returned " + m1.get("de_event_intrigues_mature"));
        // CardEffects.lookupMap — with routing — must resolve it.
        check("mutation 1: lookupMap resolves Deluxe id via canonical routing",
            CardEffects.lookupMap(m1, "de_event_intrigues_mature") != null,
            "lookupMap returned null — routing not engaged");
        check("mutation 1: lookupMap resolves to the same value as Premiere",
            Integer.valueOf(2).equals(CardEffects.lookupMap(m1, "de_event_intrigues_mature")),
            "mismatch in resolved value");
    }

    private static void check(String what, boolean ok, String detail) {
        checks++;
        if (ok) {
            System.out.println("[B5-0004] " + what + ": PASS");
        } else {
            failures++;
            System.out.println("[B5-0004] " + what + ": FAIL -- " + detail);
        }
    }
}
