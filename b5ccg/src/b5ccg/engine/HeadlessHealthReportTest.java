package b5ccg.engine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * B5-2614 - headless harness for {@link HeadlessHealthReport}.
 *
 * <p>JUnit-free by design (AGENTS.md section 2: stdlib only). Compile and run
 * with the same {@code -source 6 -target 6} gate that owns the SUT:
 *
 * <pre>
 *   javac -source 6 -target 6 -d b5ccg/out \
 *       b5ccg/src/b5ccg/engine/HeadlessHealthReport.java \
 *       b5ccg/src/b5ccg/engine/HeadlessHealthReportTest.java
 *   java -cp b5ccg/out b5ccg.engine.HeadlessHealthReportTest
 * </pre>
 *
 * <p>{@code main} drives {@link HeadlessHealthReport#run} in-process (the SUT
 * holds no static state and never calls {@code System.exit}, per its contract),
 * captures the SUT's stdout/stderr into buffers, and asserts the documented
 * exit-code and output behaviour. Exits 0 only when every named check holds;
 * otherwise 1. This file adds no game logic and mutates no shared state.
 */
public class HeadlessHealthReportTest {

    private static int failed = 0;

    /** Captured result of one in-process {@code run(...)} invocation. */
    private static final class Result {
        private final int exit;
        private final String out;
        private final String err;

        Result(int exit, String out, String err) {
            this.exit = exit;
            this.out = out;
            this.err = err;
        }
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            System.out.println("  [ok]   " + name);
        } else {
            failed++;
            System.out.println("  [FAIL] " + name);
        }
    }

    /** Runs the report in-process against isolated buffers; never throws. */
    private static Result invoke(String[] args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream po = new PrintStream(out);
        PrintStream pe = new PrintStream(err);
        int exit = HeadlessHealthReport.run(args, po, pe);
        po.flush();
        pe.flush();
        return new Result(exit, out.toString(), err.toString());
    }

    public static void main(String[] args) {
        System.out.println("=== HeadlessHealthReport suite (B5-2614) ===");

        // bad flag -> exit 2, nothing written to stdout
        Result bad = invoke(new String[]{"--bogus"});
        check("bad-flag-exit-2", bad.exit == 2);
        check("bad-flag-clean-stdout", bad.out.length() == 0);

        // --help -> exit 0, usage text on stdout
        Result help = invoke(new String[]{"--help"});
        check("help-exit-0", help.exit == 0);
        check("help-usage", help.out.indexOf("Usage") >= 0);

        // --list -> exit 0, section ids on stdout
        Result list = invoke(new String[]{"--list"});
        check("list-exit-0", list.exit == 0);
        check("list-runtime", list.out.indexOf("RUNTIME") >= 0);

        // unknown section -> exit 2, reason on stderr
        Result unk = invoke(new String[]{"--sections", "NOPE"});
        check("unknown-section-exit-2", unk.exit == 2);
        check("unknown-section-err", unk.err.indexOf("unknown section") >= 0);

        // empty sections value -> exit 2, reason on stderr
        Result empty = invoke(new String[]{"--sections", ""});
        check("empty-section-exit-2", empty.exit == 2);
        check("empty-section-err", empty.err.indexOf("empty element") >= 0);

        // --help + --json -> exit 2, clean stdout (measured flags collide)
        Result helpJson = invoke(new String[]{"--help", "--json"});
        check("help-json-exit-2", helpJson.exit == 2);
        check("help-json-clean-stdout", helpJson.out.length() == 0);

        // full run -> SUMMARY present; a rerun is byte-identical (deterministic)
        Result full1 = invoke(new String[0]);
        Result full2 = invoke(new String[0]);
        check("full-summary", full1.out.indexOf("SUMMARY") >= 0);
        check("full-rerun-identical", full1.out.equals(full2.out));

        // --sections RUNTIME -> exit 0, PASS line for RUNTIME
        Result rt = invoke(new String[]{"--sections", "RUNTIME"});
        check("sections-runtime-exit-0", rt.exit == 0);
        check("sections-runtime-pass", rt.out.indexOf("[PASS] RUNTIME") >= 0);

        // --json -> one line; opens with the tool field
        Result jsonRun = invoke(new String[]{"--json"});
        String jsonTrimmed = jsonRun.out.trim();
        check("json-single-line", jsonTrimmed.indexOf('\n') < 0
                && jsonTrimmed.indexOf('\r') < 0);
        check("json-tool-field",
                jsonTrimmed.indexOf("{\"tool\":\"HeadlessHealthReport") >= 0);

        // null args -> no throw; treated as the full run (prints SUMMARY)
        Result nullArgs = invoke((String[]) null);
        check("null-args-no-throw",
                nullArgs != null && nullArgs.out.indexOf("SUMMARY") >= 0);

        // --quiet suppresses PASS lines; the same line shows without it
        Result quiet = invoke(new String[]{"--quiet"});
        Result normal = invoke(new String[0]);
        check("quiet-suppresses-runtime-pass",
                quiet.out.indexOf("[PASS] RUNTIME") < 0);
        check("normal-shows-runtime-pass",
                normal.out.indexOf("[PASS] RUNTIME") >= 0);

        System.out.println();
        System.out.println(failed == 0
                ? "HEADLESS HEALTH REPORT SUITE PASSED"
                : "HEADLESS HEALTH REPORT SUITE FAILED (" + failed + " check(s))");
        System.out.println("checks failed: " + failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}
