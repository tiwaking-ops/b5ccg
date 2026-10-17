package b5ccg.engine;

import java.util.Map;

/**
 * B5-0002 (WP-01/A9): the rule-set profile selected per game.
 *
 * <p>A profile names the rulebook revision a game is played under plus the
 * table-level options the rulebook itself marks optional, so "present but
 * unreachable" is explicit and testable instead of tribal. Every replay
 * header carries one ({@link ReplayRecorder}), which is what makes two
 * replays comparable: same profile means same rules.</p>
 *
 * <p>Defaults record the three first interpretations (A9, logged in
 * {@code docs/DECISIONS.md}, never in the rulebook):</p>
 * <ul>
 *   <li>D5 — where Premiere and Deluxe printings differ, the <b>Deluxe</b>
 *       text governs (rulebook Errata §: "All previous errata have been
 *       absorbed into the Deluxe revision of the cards");</li>
 *   <li>the League's second Species Ambassador is dealt <b>in hand</b>, not
 *       seated in play (rulebook League §: "begins with a second species
 *       ambassador in his starting hand");</li>
 *   <li>the duplicate-setup-card sweep is an <b>engine decision</b>, not a
 *       rulebook rule (no rulebook paragraph requires it; "Removed from the
 *       Game" is defined for replacement).</li>
 * </ul>
 *
 * <p><b>Stub scope.</b> This is the Tier-0 stub: value object plus defaults
 * plus replay serialisation. Alternate-faction stages (F1–F5), Psi Corps,
 * Drakh/ISA and tournament-rules behaviour are later work gated on owner
 * decision D1; the flags exist so replays can already name them.</p>
 *
 * <p><b>Java 6.</b> No diamond, no strings-in-switch, no
 * try-with-resources — like the rest of the engine.</p>
 */
public final class RuleSetProfile {

    /** Rulebook revision this profile is defined against. */
    public static final String RULEBOOK_VERSION = "1.3";

    /** SHA-256 of the pinned working-tree bytes of
     *  {@code BABYLON5_CCG_RULEBOOK.md} (see {@code docs/RULEBOOK_PIN.md}).
     *  Lowercase hex. */
    public static final String RULEBOOK_SHA256 =
        "1c99c44b7b64c8927f1e952a22a49d5886788da2d08e211092b5558e154716d0";

    /** Pinned working-tree size in bytes. */
    public static final long RULEBOOK_BYTES = 134586L;

    /** Pinned line count: every LF-terminated line (Claude's 1,236). */
    public static final int RULEBOOK_LINES_TOTAL = 1236;

    /** Pinned line count: non-blank lines only (the report's 689).
     *  Same bytes as {@link #RULEBOOK_LINES_TOTAL}; different denominator. */
    public static final int RULEBOOK_LINES_NONEMPTY = 689;

    /** Which printing's text governs when printings differ (D5). */
    public static final String PRINTING_DELUXE = "DELUXE";
    public static final String PRINTING_PREMIERE = "PREMIERE";

    private final String  rulebookVersion;
    private final String  rulebookSha256;
    private final boolean alternateRaces;
    private final boolean optionalPowerAndVictory;
    private final boolean tournamentRules;
    private final String  printingGoverns;

    public RuleSetProfile(String rulebookVersion,
                          String rulebookSha256,
                          boolean alternateRaces,
                          boolean optionalPowerAndVictory,
                          boolean tournamentRules,
                          String printingGoverns) {
        if (rulebookVersion == null) throw new NullPointerException("B5-0002: rulebookVersion");
        if (rulebookSha256 == null)  throw new NullPointerException("B5-0002: rulebookSha256");
        if (printingGoverns == null) throw new NullPointerException("B5-0002: printingGoverns");
        this.rulebookVersion        = rulebookVersion;
        this.rulebookSha256         = rulebookSha256;
        this.alternateRaces      = alternateRaces;
        this.optionalPowerAndVictory = optionalPowerAndVictory;
        this.tournamentRules        = tournamentRules;
        this.printingGoverns        = printingGoverns;
    }

    /** The standard game: rulebook 1.3, no optional modules, Deluxe governs. */
    public static RuleSetProfile defaults() {
        return new RuleSetProfile(RULEBOOK_VERSION, RULEBOOK_SHA256,
                                  false, false, false, PRINTING_DELUXE);
    }

    public String  getRulebookVersion()        { return rulebookVersion; }
    public String  getRulebookSha256()         { return rulebookSha256; }
    public boolean isAlternateRaces()       { return alternateRaces; }
    public boolean isOptionalPowerAndVictory() { return optionalPowerAndVictory; }
    public boolean isTournamentRules()         { return tournamentRules; }
    public String  getPrintingGoverns()        { return printingGoverns; }

    /** Compact JSON object for the replay header. Keys are stable;
     *  readers must tolerate missing keys (a v1 header has none). */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"rulebookVersion\":\"").append(esc(rulebookVersion)).append("\"");
        sb.append(",\"rulebookSha256\":\"").append(esc(rulebookSha256)).append("\"");
        sb.append(",\"alternateRaces\":").append(alternateRaces);
        sb.append(",\"optionalPowerAndVictory\":").append(optionalPowerAndVictory);
        sb.append(",\"tournamentRules\":").append(tournamentRules);
        sb.append(",\"printingGoverns\":\"").append(esc(printingGoverns)).append("\"");
        sb.append('}');
        return sb.toString();
    }

    /** Reads back what {@link #toJson} wrote. A null or missing map is the
     *  pre-profile (schema 1) header and yields {@link #defaults()}; a
     *  present-but-partial map fills the gaps with defaults rather than
     *  failing the load. Unknown values fail LOUDLY (null): a profile that
     *  cannot be understood must not silently become the standard game. */
    public static RuleSetProfile fromMap(Map<String, Object> m) {
        if (m == null) return defaults();
        RuleSetProfile d = defaults();
        String ver = str(m.get("rulebookVersion"), d.rulebookVersion);
        String sha = str(m.get("rulebookSha256"), d.rulebookSha256);
        String gov = str(m.get("printingGoverns"), d.printingGoverns);
        if (!PRINTING_DELUXE.equals(gov) && !PRINTING_PREMIERE.equals(gov)) return null;
        return new RuleSetProfile(ver, sha,
                                  bool(m.get("alternateRaces"), d.alternateRaces),
                                  bool(m.get("optionalPowerAndVictory"), d.optionalPowerAndVictory),
                                  bool(m.get("tournamentRules"), d.tournamentRules),
                                  gov);
    }

    private static String str(Object o, String fallback) {
        return (o == null) ? fallback : o.toString();
    }

    private static boolean bool(Object o, boolean fallback) {
        if (o == null) return fallback;
        if (o instanceof Boolean) return ((Boolean) o).booleanValue();
        if (o instanceof Integer) return ((Integer) o).intValue() != 0;
        return fallback;
    }

    private static String esc(String s) {
        return ReplayRecorder.esc(s);
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuleSetProfile)) return false;
        RuleSetProfile p = (RuleSetProfile) o;
        return alternateRaces == p.alternateRaces
            && optionalPowerAndVictory == p.optionalPowerAndVictory
            && tournamentRules == p.tournamentRules
            && rulebookVersion.equals(p.rulebookVersion)
            && rulebookSha256.equals(p.rulebookSha256)
            && printingGoverns.equals(p.printingGoverns);
    }

    public int hashCode() {
        int h = rulebookVersion.hashCode();
        h = 31 * h + rulebookSha256.hashCode();
        h = 31 * h + printingGoverns.hashCode();
        h = 31 * h + (alternateRaces ? 1 : 0);
        h = 31 * h + (optionalPowerAndVictory ? 2 : 0);
        h = 31 * h + (tournamentRules ? 4 : 0);
        return h;
    }

    public String toString() {
        return "RuleSetProfile" + toJson();
    }
}
