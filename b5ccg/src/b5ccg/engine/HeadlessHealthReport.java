package b5ccg.engine;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * B5-2614 - headless health report: a report-only reading of this tree, not a
 * release verdict. Implements the executable half of
 * {@code docs/proposals/b52614-headless-health-report-proposal.md}, scoped by
 * this pass to four sections: RUNTIME, ARTIFACTS, CARDPOOL, DECKS.
 *
 * <p><b>Three rules the class is built around.</b>
 *
 * <ul>
 *   <li>The report can go red (B5-1019): a FAIL exits 1 by default, and
 *       {@code --report-only} suppresses only an advisory WARN - never a FAIL
 *       and never an UNKNOWN.</li>
 *   <li>Unmeasured is not measured-green (B5-0609, B5-1733): a section that
 *       throws is recorded as UNKNOWN and the run exits 2, so a report that
 *       could not see a condition never reads as clean.</li>
 *   <li>{@link #run} holds no static state and never calls
 *       {@code System.exit}; {@code main} calls it exactly once, so the exit
 *       contract is exercisable in-process and {@code run} is re-entrant.</li>
 * </ul>
 *
 * <p><b>Exit codes.</b>
 * <pre>
 *   0 = every measured threshold met and nothing was unmeasurable
 *   1 = at least one FAIL, or a WARN outside --report-only
 *   2 = harness error (bad argument) or at least one UNKNOWN
 * </pre>
 *
 * <p><b>Not wired into compile.sh</b> (proposal section 9): a report folded
 * into the build would make every build's exit code move with card-data
 * counts, and it would re-assert instrument lists the build already owns in
 * exactly one file. It ships run-on-demand.
 *
 * <p>Java 6 only, stdlib only (AGENTS.md section 2).
 */
public class HeadlessHealthReport {

    private static final class Check {
        private final String verdict;
        private final String section;
        private final String detail;

        Check(String verdict, String section, String detail) {
            this.verdict = verdict;
            this.section = section;
            this.detail = detail;
        }
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (out == null) {
            out = System.out;
        }
        if (err == null) {
            err = System.err;
        }
        if (args == null) {
            args = new String[0];
        }

        String[][] registry = {
            {"RUNTIME", "always PASS - process runtime readings"},
            {"ARTIFACTS", "every .class under the code source is major version 50"},
            {"CARDPOOL", "cards/premiere.json and cards/deluxe.json exist and are non-empty"},
            {"DECKS", "the decks directory exists and holds at least one entry"}
        };

        boolean help = false;
        boolean list = false;
        boolean reportOnly = false;
        boolean json = false;
        boolean quiet = false;
        boolean measured = false;
        boolean sectionsSeen = false;
        String sectionsArg = null;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--help".equals(a) || "-h".equals(a)) {
                help = true;
            } else if ("--list".equals(a)) {
                list = true;
            } else if ("--report-only".equals(a)) {
                reportOnly = true;
                measured = true;
            } else if ("--json".equals(a)) {
                json = true;
                measured = true;
            } else if ("--quiet".equals(a)) {
                quiet = true;
                measured = true;
            } else if ("--sections".equals(a)) {
                if (i + 1 >= args.length) {
                    err.println("ERROR: --sections requires a comma-separated value");
                    return 2;
                }
                i++;
                sectionsArg = args[i];
                sectionsSeen = true;
                measured = true;
            } else {
                err.println("ERROR: unknown argument '" + a + "'");
                err.print(usage(registry));
                return 2;
            }
        }

        if ((help || list) && measured) {
            err.println("ERROR: --help and --list measure nothing, so they cannot be "
                    + "combined with --sections/--report-only/--json/--quiet");
            return 2;
        }
        if (help) {
            out.print(usage(registry));
            return 0;
        }
        if (list) {
            for (int i = 0; i < registry.length; i++) {
                out.println(registry[i][0] + " - " + registry[i][1]);
            }
            return 0;
        }

        int[] selected;
        if (sectionsSeen) {
            selected = parseSections(sectionsArg, registry, err);
            if (selected == null) {
                return 2;
            }
        } else {
            selected = new int[registry.length];
            for (int i = 0; i < registry.length; i++) {
                selected[i] = i;
            }
        }

        List<Check> checks = new ArrayList<Check>();
        File root = codeSourceRoot();
        String userDir = System.getProperty("user.dir");

        for (int s = 0; s < selected.length; s++) {
            String id = registry[selected[s]][0];
            try {
                if ("RUNTIME".equals(id)) {
                    sectionRuntime(id, checks);
                } else if ("ARTIFACTS".equals(id)) {
                    sectionArtifacts(root, id, checks);
                } else if ("CARDPOOL".equals(id)) {
                    sectionCardpool(root, userDir, id, checks);
                } else if ("DECKS".equals(id)) {
                    sectionDecks(root, userDir, id, checks);
                } else {
                    checks.add(new Check("UNKNOWN", id, "section id has no implementation"));
                }
            } catch (Throwable t) {
                String m = t.getMessage();
                checks.add(new Check("UNKNOWN", id, t.getClass().getName()
                        + (m == null || m.length() == 0 ? "" : ": " + m)));
            }
        }

        int pass = 0;
        int warn = 0;
        int fail = 0;
        for (int i = 0; i < checks.size(); i++) {
            String v = checks.get(i).verdict;
            if ("PASS".equals(v)) {
                pass++;
            } else if ("WARN".equals(v)) {
                warn++;
            } else if ("FAIL".equals(v)) {
                fail++;
            }
        }
        int unknown = checks.size() - pass - warn - fail;

        int exit;
        if (unknown > 0) {
            exit = 2;
        } else if (fail > 0) {
            exit = 1;
        } else if (warn > 0 && !reportOnly) {
            exit = 1;
        } else {
            exit = 0;
        }

        if (json) {
            out.println(jsonLine(checks, selected.length, reportOnly,
                    pass, warn, fail, unknown, exit));
        } else {
            for (int i = 0; i < checks.size(); i++) {
                Check c = checks.get(i);
                if (quiet && "PASS".equals(c.verdict)) {
                    continue;
                }
                out.println("[" + c.verdict + "] " + c.section + " - " + c.detail);
            }
            out.println("SUMMARY: sections=" + selected.length + " checks=" + checks.size()
                    + " pass=" + pass + " warn=" + warn + " fail=" + fail
                    + " unknown=" + unknown + " report-only=" + reportOnly + " exit=" + exit);
        }
        return exit;
    }

    // ── Argument handling ────────────────────────────────────────────────────

    private static String usage(String[][] registry) {
        StringBuilder sb = new StringBuilder();
        sb.append("Usage: java -cp <out> b5ccg.engine.HeadlessHealthReport [flags]\n");
        sb.append("  --help            print this usage and exit 0, measuring nothing\n");
        sb.append("  --list            print one line per section and exit 0, measuring nothing\n");
        sb.append("  --sections A,B    run only these sections (exact, case-sensitive ids): ");
        for (int i = 0; i < registry.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(registry[i][0]);
        }
        sb.append('\n');
        sb.append("  --report-only     advisory WARN findings no longer affect the exit code\n");
        sb.append("  --json            print one single-line JSON document instead of the text report\n");
        sb.append("  --quiet           suppress PASS lines; findings and the summary still print\n");
        sb.append("Exit: 0 thresholds met, 1 below threshold, 2 bad argument or UNKNOWN.\n");
        return sb.toString();
    }

    private static int[] parseSections(String spec, String[][] registry, PrintStream err) {
        if (spec == null) {
            err.println("ERROR: --sections requires a comma-separated value");
            return null;
        }
        StringBuilder valid = new StringBuilder();
        for (int j = 0; j < registry.length; j++) {
            if (j > 0) {
                valid.append(',');
            }
            valid.append(registry[j][0]);
        }
        String[] parts = spec.split(",", -1);
        int[] picked = new int[parts.length];
        int n = 0;
        for (int i = 0; i < parts.length; i++) {
            String id = parts[i] == null ? "" : parts[i].trim();
            if (id.length() == 0) {
                err.println("ERROR: --sections has an empty element in '" + spec + "'");
                return null;
            }
            int idx = -1;
            for (int j = 0; j < registry.length; j++) {
                if (registry[j][0].equals(id)) {
                    idx = j;
                    break;
                }
            }
            if (idx < 0) {
                err.println("ERROR: unknown section '" + id + "'; valid ids: " + valid);
                return null;
            }
            boolean dup = false;
            for (int j = 0; j < n; j++) {
                if (picked[j] == idx) {
                    dup = true;
                }
            }
            if (!dup) {
                picked[n++] = idx;
            }
        }
        int[] out = new int[n];
        System.arraycopy(picked, 0, out, 0, n);
        return out;
    }

    // ── The four sections ────────────────────────────────────────────────────
    //
    // Each is called inside run()'s try/catch (Throwable): an instrument that
    // could not see a condition reports UNKNOWN and the run exits 2.

    private static void sectionRuntime(String id, List<Check> checks) {
        TimeZone tz = TimeZone.getDefault();
        String tzName = System.getProperty("user.timezone");
        if (tzName == null || tzName.length() == 0) {
            tzName = tz.getID();
        }
        String detail = "java.version=" + System.getProperty("java.version")
                + " class.version=" + System.getProperty("java.class.version")
                + " os=" + System.getProperty("os.name")
                + " arch=" + System.getProperty("os.arch")
                + " user.dir=" + System.getProperty("user.dir")
                + " tz=" + tzName + " rawOffsetMin=" + (tz.getRawOffset() / 60000);
        checks.add(new Check("PASS", id, detail));
    }

    private static void sectionArtifacts(File root, String id, List<Check> checks) throws IOException {
        if (root == null) {
            checks.add(new Check("UNKNOWN", id,
                    "code source has no file: location (jar or unknown loader)"));
            return;
        }
        int[] tally = new int[2];
        List<String> bad = new ArrayList<String>();
        scan(root, tally, bad);
        if (tally[0] == 0) {
            checks.add(new Check("UNKNOWN", id, "no .class files under " + root.getPath()));
            return;
        }
        if (tally[1] > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bad.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(bad.get(i));
            }
            checks.add(new Check("FAIL", id, tally[1] + " of " + tally[0]
                    + " class file(s) are not major version 50: " + sb));
            return;
        }
        checks.add(new Check("PASS", id, tally[0] + " class file(s), all major version 50"));
    }

    private static void sectionCardpool(File root, String userDir, String id, List<Check> checks) {
        checkCardFile(resolve("cards/premiere.json", root, userDir), "cards/premiere.json", id, checks);
        checkCardFile(resolve("cards/deluxe.json", root, userDir), "cards/deluxe.json", id, checks);
    }

    private static void checkCardFile(File f, String label, String id, List<Check> checks) {
        if (f == null || !f.isFile()) {
            checks.add(new Check("FAIL", id, label + " not found under the searched roots"));
        } else if (f.length() <= 0) {
            checks.add(new Check("FAIL", id, label + " exists but is empty"));
        } else {
            checks.add(new Check("PASS", id, label + " exists, " + f.length() + " byte(s)"));
        }
    }

    private static void sectionDecks(File root, String userDir, String id, List<Check> checks) {
        File dir = resolve("decks", root, userDir);
        if (dir == null || !dir.isDirectory()) {
            checks.add(new Check("FAIL", id, "decks directory not found under the searched roots"));
            return;
        }
        File[] kids = dir.listFiles();
        int n = kids == null ? 0 : kids.length;
        if (n == 0) {
            checks.add(new Check("WARN", id, "decks directory exists but is empty: " + dir.getPath()));
        } else {
            checks.add(new Check("PASS", id, "decks directory holds " + n
                    + " entr(ies): " + dir.getPath()));
        }
    }

    // ── File-system helpers (java.io only, no java.nio) ──────────────────────

    private static File resolve(String rel, File root, String userDir) {
        List<File> bases = new ArrayList<File>();
        if (root != null) {
            bases.add(root);
        }
        if (userDir != null && userDir.length() > 0) {
            bases.add(new File(userDir));
            bases.add(new File(userDir, "b5ccg/out"));
            bases.add(new File(userDir, "out"));
            bases.add(new File(userDir, "b5ccg/resources"));
        }
        for (int i = 0; i < bases.size(); i++) {
            File f = new File(bases.get(i), rel);
            if (f.exists()) {
                return f;
            }
        }
        return null;
    }

    private static File codeSourceRoot() {
        try {
            ProtectionDomain pd = HeadlessHealthReport.class.getProtectionDomain();
            if (pd == null) {
                return null;
            }
            CodeSource cs = pd.getCodeSource();
            if (cs == null) {
                return null;
            }
            URL loc = cs.getLocation();
            if (loc == null || !"file".equalsIgnoreCase(loc.getProtocol())) {
                return null;
            }
            return new File(loc.toURI());
        } catch (Throwable t) {
            return null;
        }
    }

    private static void scan(File dir, int[] tally, List<String> bad) throws IOException {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (int i = 0; i < kids.length; i++) {
            File f = kids[i];
            if (f.isDirectory()) {
                scan(f, tally, bad);
            } else if (f.getName().endsWith(".class")) {
                tally[0]++;
                int major = classFileMajorVersion(f);
                if (major != 50) {
                    tally[1]++;
                    if (bad.size() < 3) {
                        bad.add(f.getName() + "=" + major);
                    }
                }
            }
        }
    }

    private static int classFileMajorVersion(File f) throws IOException {
        InputStream is = new FileInputStream(f);
        try {
            DataInputStream in = new DataInputStream(is);
            int magic = in.readInt();
            if (magic != 0xCAFEBABE) {
                return -2;
            }
            in.readUnsignedShort();
            return in.readUnsignedShort();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                // nothing further can be done about a close failure here
            }
        }
    }

    // ── JSON writer (one line, hand-written, no external library) ────────────

    private static String jsonLine(List<Check> checks, int sections, boolean reportOnly,
            int pass, int warn, int fail, int unknown, int exit) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"tool\":\"HeadlessHealthReport\",\"task\":\"B5-2614\"");
        sb.append(",\"exitCode\":").append(exit);
        sb.append(",\"reportOnly\":").append(reportOnly);
        sb.append(",\"counts\":{\"sections\":").append(sections);
        sb.append(",\"checks\":").append(checks.size());
        sb.append(",\"pass\":").append(pass);
        sb.append(",\"warn\":").append(warn);
        sb.append(",\"fail\":").append(fail);
        sb.append(",\"unknown\":").append(unknown).append('}');
        sb.append(",\"checks\":[");
        for (int i = 0; i < checks.size(); i++) {
            Check c = checks.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"section\":\"").append(esc(c.section));
            sb.append("\",\"verdict\":\"").append(esc(c.verdict));
            sb.append("\",\"detail\":\"").append(esc(c.detail)).append("\"}");
        }
        sb.append('}');
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' || ch == '"') {
                sb.append('\\').append(ch);
            } else if (ch == '\n') {
                sb.append("\\n");
            } else if (ch == '\r') {
                sb.append("\\r");
            } else if (ch == '\t') {
                sb.append("\\t");
            } else if (ch < 0x20) {
                String hex = Integer.toHexString(ch);
                sb.append("\\u");
                for (int k = hex.length(); k < 4; k++) {
                    sb.append('0');
                }
                sb.append(hex);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
