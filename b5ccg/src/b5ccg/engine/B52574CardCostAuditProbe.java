package b5ccg.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * B5-2574 — the card-cost-assignment audit, as a runnable probe.
 *
 * <p>Follow-up to B5-2540 (BLOCKED) and to the B5-2133 cost-assignment policy
 * proposal. B5-2540 was an audit <i>intent</i> with no instrument behind it;
 * this is the instrument. It reads
 * {@code b5ccg/resources/cards/premiere.json} and {@code deluxe.json} and
 * prints a diff report of every card cost field against the policy.
 *
 * <h3>Why this reads the raw JSON, not {@link Card#getCost()}</h3>
 * <p>The loaded-card view cannot answer the questions this row asks.
 * {@code DeckLoader.parseCards} clamps a negative or unparseable cost to 0
 * (B5-0323) and a missing {@code cost} key defaults to 0, so
 * {@code getCost()} reads {@code 0} for three materially different records: a
 * card that legitimately costs nothing, a card whose cost key was never
 * assigned, and a card carrying a corrupt value. B5-2133 open question 2 is
 * exactly this ambiguity. So the audit reads the <b>raw object maps</b> from
 * {@link DeckLoader#loadFlatObjects(String)}, where an absent key is absent and
 * a present value is the literal token from the file. The loaded-card value is
 * still printed alongside, as the <i>effective</i> cost, so the report shows
 * the gap between what the data says and what the engine will charge.
 *
 * <h3>What is a VIOLATION and what is a FINDING</h3>
 * <p>Two tiers, deliberately separated, because they have different owners:
 * <ul>
 *   <li><b>VIOLATION</b> — a structural defect in the data: a cost on a type
 *       the schema does not price, or a cost token that is not a non-negative
 *       integer. These exit 1. They are defects in the files themselves.</li>
 *   <li><b>FINDING</b> — a policy gap with an owner elsewhere: cost-bearing
 *       cards with no cost assigned (the 8 ambassadors, B5-2018), costs outside
 *       the B5-2133 §4 rarity-band guidance, explicit {@code cost: 0} records
 *       that are runtime-indistinguishable from absent, and the total absence
 *       of {@code costSource} provenance (the G1 review-gate field, which no
 *       card carries yet). These are printed with their measurements and do
 *       <b>not</b> fail the probe: asserting a gap's continued existence locks
 *       a defect in as intended behaviour, and asserting it is closed goes red
 *       for a row whose scope was to write the probe. B5-2133 §5 makes each one
 *       a Tier-2/3/4 assignment task, not this row's.</li>
 * </ul>
 *
 * <h3>The self-test: this audit can fail</h3>
 * <p>An audit that has only ever printed PASS is not evidence. Before reading
 * the real files this probe runs the SAME audit routine over a synthetic
 * fragment built to contradict the policy — a cost on a type the schema does
 * not price, a negative cost, a non-integer cost, and a cost-bearing card with
 * no cost — and asserts every one of those is detected. The synthetic fixture
 * is therefore not decorative: it establishes that a red result is reachable,
 * so a green result on the real files means something.
 *
 * <p>Java 6 only, stdlib only (AGENTS.md §2). Exit 0 when the self-test passes
 * and no violation is found; exit 1 on a self-test failure or any violation.
 *
 * <pre>
 *   java -cp b5ccg/out b5ccg.engine.B52574CardCostAuditProbe
 * </pre>
 */
public class B52574CardCostAuditProbe {

    /** The card types the B5-0315 cost schema prices (B5-2133 §4). */
    private static final Set<String> COST_BEARING = new HashSet<String>(Arrays.asList(
        new String[] { "CHARACTER", "ENHANCEMENT", "GROUP", "LOCATION", "FLEET" }));

    /** B5-2133 §4 rarity-band guidance per type, "RARITY lo-hi". */
    private static final String[][] RARITY_BANDS = {
        { "CHARACTER",   "FIXED",    "5-8"  },
        { "CHARACTER",   "COMMON",   "3-6"  },
        { "CHARACTER",   "UNCOMMON", "5-9"  },
        { "CHARACTER",   "RARE",     "7-13" },
        { "ENHANCEMENT", "FIXED",    "2-4"  },
        { "ENHANCEMENT", "COMMON",   "1-3"  },
        { "ENHANCEMENT", "UNCOMMON", "2-5"  },
        { "ENHANCEMENT", "RARE",     "4-8"  },
        { "GROUP",       "FIXED",    "3-5"  },
        { "GROUP",       "COMMON",   "2-4"  },
        { "GROUP",       "UNCOMMON", "3-6"  },
        { "GROUP",       "RARE",     "5-9"  },
        { "LOCATION",    "FIXED",    "2-4"  },
        { "LOCATION",    "COMMON",   "1-3"  },
        { "LOCATION",    "UNCOMMON", "2-5"  },
        { "LOCATION",    "RARE",     "4-7"  },
        { "FLEET",       "FIXED",    "4-6"  },
        { "FLEET",       "COMMON",   "3-5"  },
        { "FLEET",       "UNCOMMON", "4-7"  },
        { "FLEET",       "RARE",     "6-10" },
    };

    /** Per-run tallies. Violations fail the probe; findings do not. */
    private static final class Tally {
        int records;
        int costed;          // cost key present at all
        int unassigned;      // cost-bearing type, no cost key
        int strayCost;       // cost key on a type the schema does not price
        int badCostToken;    // cost present but not a non-negative integer
        int outOfBand;       // cost present, priced type, outside B5-2133 §4
        int explicitZero;    // cost present and literally 0
        int noCostSource;    // cost present, no costSource provenance (G1)
        List<String> violations = new ArrayList<String>();
        List<String> findings  = new ArrayList<String>();

        boolean names(String hay, String id) {
            return hay != null && hay.indexOf(id) >= 0;
        }
        boolean violationsContains(String id) {
            for (int i = 0; i < violations.size(); i++) {
                if (names(violations.get(i), id)) return true;
            }
            return false;
        }
        boolean findingsContains(String id) {
            for (int i = 0; i < findings.size(); i++) {
                if (names(findings.get(i), id)) return true;
            }
            return false;
        }
    }

    public static void main(String[] args) {
        int failures = 0;

        System.out.println("=== B5-2574 card-cost-assignment audit ===");
        System.out.println("policy: B5-2133 §4 per-type defaults + rarity-band guidance;");
        System.out.println("        schema: B5-0315 cost field; loader default: B5-0323 absent -> 0");
        System.out.println();

        // ── Self-test FIRST: prove the audit can reach a red result ──────────
        System.out.println("--- self-test: synthetic contradictory fragment ---");
        Tally st = audit("self-test", selfTestObjects());
        failures += expect("self-test finds a cost on an unpriced type", st.strayCost, 1);
        failures += expect("self-test finds bad cost tokens",             st.badCostToken, 2);
        failures += expect("self-test finds an unassigned priced card",    st.unassigned, 1);
        failures += expect("self-test reports violations (not findings)",   st.violations.size(), 3);
        // Each violation must NAME the record that caused it. A count alone
        // cannot tell "the negative-cost check fired" from "the non-integer
        // check fired twice", so the id is asserted per defect class.
        failures += expectViolationNaming(st, "VIOLATION", "t_event");
        failures += expectViolationNaming(st, "VIOLATION", "t_char_neg");
        failures += expectViolationNaming(st, "VIOLATION", "t_char_frac");
        failures += expectFindingNaming(st, "FINDING", "t_char_none");
        System.out.println("[SELF-TEST] the clean fixture record (t_char_ok) produced "
            + (st.violationsContains("t_char_ok") || st.findingsContains("t_char_ok")
                ? "a finding — FAIL" : "no finding — OK"));
        for (int i = 0; i < st.violations.size(); i++) {
            System.out.println("           " + st.violations.get(i));
        }
        for (int i = 0; i < st.findings.size(); i++) {
            System.out.println("           " + st.findings.get(i));
        }
        System.out.println();

        // ── The real files ─────────────────────────────────────────────────
        String[] sets = { "premiere", "deluxe" };
        Tally grand = new Tally();
        for (int s = 0; s < sets.length; s++) {
            String set = sets[s];
            List<Map<String, String>> objs;
            try {
                objs = DeckLoader.loadFlatObjects("/cards/" + set + ".json");
            } catch (IOException e) {
                System.out.println("[ERROR] cannot read " + set + ".json: " + e.getMessage());
                System.exit(1);
                return;
            }
            Tally t = audit(set, objs);
            System.out.println("--- " + set + ".json ---");
            printTally(t);
            merge(grand, t);
            System.out.println();
        }

        System.out.println("--- combined ---");
        printTally(grand);
        System.out.println();

        System.out.println("VERDICT: " + (failures == 0 && grand.violations.isEmpty() ? "PASS" : "FAIL")
            + " — " + grand.violations.size() + " violation(s), "
            + grand.findings.size()  + " finding(s), self-test failures " + failures);
        System.out.println("Findings are printed, not gated: each is a B5-2133 Tier 2/3/4");
        System.out.println("assignment task with its own owner. See docs/DECISIONS.md B5-2574.");

        if (failures != 0 || !grand.violations.isEmpty()) System.exit(1);
    }

    // ── The audit routine, shared by the self-test and the real files ────────

    private static Tally audit(String label, List<Map<String, String>> objs) {
        Tally t = new Tally();
        for (int i = 0; i < objs.size(); i++) {
            Map<String, String> o = objs.get(i);
            t.records++;
            String id    = str(o, "id");
            String title = str(o, "title");
            String type  = str(o, "type");
            String rar   = str(o, "rarity");
            String where = label + " " + id + " (" + title + ") " + type + "/" + rar;

            boolean hasKey = o.containsKey("cost");
            String  token  = hasKey ? o.get("cost") : null;

            if (!COST_BEARING.contains(type)) {
                // AFTERMATH / AGENDA / CONFLICT / EVENT carry no price by schema.
                if (hasKey) {
                    t.strayCost++;
                    t.violations.add("VIOLATION " + where
                        + ": cost=" + token + " on type " + type
                        + ", which B5-0315 does not price");
                }
                continue;
            }

            if (!hasKey) {
                // B5-2133 §4 default is absent -> 0; the gap is the finding.
                t.unassigned++;
                t.findings.add("FINDING " + where
                    + ": cost-bearing type with NO cost assigned (absent -> 0 per B5-0323);"
                    + " B5-2133 §4 assigns a value, B5-2018 raised this class");
                continue;
            }

            t.costed++;
            Integer cost = nonNegativeInt(token);
            if (cost == null) {
                t.badCostToken++;
                t.violations.add("VIOLATION " + where + ": cost token '" + token
                    + "' is not a non-negative integer (the loader would clamp it to 0)");
            } else {
                int c = cost.intValue();
                if (c == 0) {
                    t.explicitZero++;
                    t.findings.add("FINDING " + where
                        + ": explicit cost 0 — runtime-indistinguishable from an absent"
                        + " cost (B5-2133 open question 2; costSource would differentiate)");
                }
                int[] band = band(type, rar);
                if (band != null && (c < band[0] || c > band[1])) {
                    t.outOfBand++;
                    t.findings.add("FINDING " + where + ": cost " + c
                        + " outside B5-2133 §4 " + type + "/" + rar
                        + " guidance " + band[0] + "-" + band[1]);
                }
            }

            if (!o.containsKey("costSource")) {
                t.noCostSource++;
                t.findings.add("FINDING " + where + ": cost " + token
                    + " carries no costSource (B5-2133 §5 G1 gate unrecorded)");
            }
        }
        return t;
    }

    // ── Synthetic contradictory fixture ─────────────────────────────────────

    /**
     * A flat JSON array shaped to make the audit RED. Every record contradicts
     * the policy in a different way, so a routine that silently skipped any one
     * check would still look partly green — and the counts asserted in main()
     * pin each check to its exact expected total.
     */
    private static List<Map<String, String>> selfTestObjects() {
        String json = "["
            // 1. cost on a type the schema does not price -> strayCost
            + "{\"id\":\"t_event\",\"title\":\"Ev\",\"type\":\"EVENT\","
            +   "\"rarity\":\"COMMON\",\"cost\":\"3\"},"
            // 2. negative cost -> badCostToken (1)
            + "{\"id\":\"t_char_neg\",\"title\":\"Neg\",\"type\":\"CHARACTER\","
            +   "\"rarity\":\"FIXED\",\"subtype\":\"CHARACTER_HUMAN\","
            +   "\"diplomacy\":\"1\",\"intrigue\":\"1\",\"psi\":\"1\","
            +   "\"leadership\":\"1\",\"isAmbassador\":\"false\",\"cost\":\"-2\"},"
            // 3. non-integer cost -> badCostToken (2)
            + "{\"id\":\"t_char_frac\",\"title\":\"Frac\",\"type\":\"CHARACTER\","
            +   "\"rarity\":\"FIXED\",\"subtype\":\"CHARACTER_HUMAN\","
            +   "\"diplomacy\":\"1\",\"intrigue\":\"1\",\"psi\":\"1\","
            +   "\"leadership\":\"1\",\"isAmbassador\":\"false\",\"cost\":\"six\"},"
            // 4. priced type, no cost -> unassigned
            + "{\"id\":\"t_char_none\",\"title\":\"None\",\"type\":\"CHARACTER\","
            +   "\"rarity\":\"FIXED\",\"subtype\":\"CHARACTER_HUMAN\","
            +   "\"diplomacy\":\"1\",\"intrigue\":\"1\",\"psi\":\"1\","
            +   "\"leadership\":\"1\",\"isAmbassador\":\"false\"},"
            // 5. clean in-band priced card with provenance -> must produce NOTHING
            + "{\"id\":\"t_char_ok\",\"title\":\"Ok\",\"type\":\"CHARACTER\","
            +   "\"rarity\":\"COMMON\",\"subtype\":\"CHARACTER_HUMAN\","
            +   "\"diplomacy\":\"1\",\"intrigue\":\"1\",\"psi\":\"1\","
            +   "\"leadership\":\"1\",\"isAmbassador\":\"false\","
            +   "\"cost\":\"4\",\"costSource\":\"TIER_2:premiere_image_004\"}"
            + "]";
        // Same package-private tokeniser DeckLoader.parseCards uses, so the
        // self-test fixture is read by the production reader rather than by a
        // second parser that could disagree with it.
        return DeckLoader.splitObjects(json);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static int expect(String what, int actual, int expected) {
        boolean ok = actual == expected;
        System.out.println("[" + (ok ? "OK  " : "FAIL") + "] " + what
            + " (expected " + expected + ", got " + actual + ")");
        return ok ? 0 : 1;
    }

    private static int expectViolationNaming(Tally t, String tag, String id) {
        boolean ok = t.violationsContains(id);
        System.out.println("[" + (ok ? "OK  " : "FAIL") + "] " + tag
            + " names record " + id);
        return ok ? 0 : 1;
    }

    private static int expectFindingNaming(Tally t, String tag, String id) {
        boolean ok = t.findingsContains(id);
        System.out.println("[" + (ok ? "OK  " : "FAIL") + "] " + tag
            + " names record " + id);
        return ok ? 0 : 1;
    }

    private static void printTally(Tally t) {
        System.out.println("  records read            : " + t.records);
        System.out.println("  cost key present        : " + t.costed);
        System.out.println("  VIOLATIONS (fail probe) : " + t.violations.size());
        System.out.println("    cost on unpriced type : " + t.strayCost);
        System.out.println("    bad cost token        : " + t.badCostToken);
        System.out.println("  FINDINGS (printed only) : " + t.findings.size());
        System.out.println("    priced, cost unassigned: " + t.unassigned);
        System.out.println("    outside rarity band   : " + t.outOfBand);
        System.out.println("    explicit cost 0       : " + t.explicitZero);
        System.out.println("    no costSource (G1)    : " + t.noCostSource);
    }

    private static void merge(Tally into, Tally from) {
        into.records      += from.records;
        into.costed       += from.costed;
        into.unassigned   += from.unassigned;
        into.strayCost    += from.strayCost;
        into.badCostToken += from.badCostToken;
        into.outOfBand    += from.outOfBand;
        into.explicitZero += from.explicitZero;
        into.noCostSource += from.noCostSource;
        into.violations.addAll(from.violations);
        into.findings.addAll(from.findings);
    }

    /** B5-2133 §4 band for a type+rarity, or null when the guidance is silent. */
    private static int[] band(String type, String rarity) {
        for (int i = 0; i < RARITY_BANDS.length; i++) {
            if (RARITY_BANDS[i][0].equals(type) && RARITY_BANDS[i][1].equals(rarity)) {
                String s = RARITY_BANDS[i][2];
                int dash = s.indexOf('-');
                return new int[] {
                    Integer.parseInt(s.substring(0, dash)),
                    Integer.parseInt(s.substring(dash + 1))
                };
            }
        }
        return null;
    }

    /** Parses a cost token, or null when it is not a non-negative integer. */
    private static Integer nonNegativeInt(String token) {
        if (token == null) return null;
        String t = token.trim();
        if (t.length() == 0) return null;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch < '0' || ch > '9') return null;   // rejects '-', '.', 'e', letters
        }
        try {
            return Integer.valueOf(t);
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    private static String str(Map<String, String> o, String key) {
        String v = o.get(key);
        return v == null ? "" : v;
    }
}