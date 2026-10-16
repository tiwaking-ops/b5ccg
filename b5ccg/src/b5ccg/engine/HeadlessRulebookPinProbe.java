package b5ccg.engine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * B5-0002 (WP-01/A9): rulebook pin + encoding probe. Verifies the canonical
 * rulebook bytes pinned in {@link RuleSetProfile} and scans the card JSON
 * for encoding damage.
 *
 * <p>Gate tier: pure 0/1 exit contract — a non-zero exit is a pin or
 * encoding defect, never an exploratory outcome, so this is safe in the
 * build gate ({@code RUN_TESTS=1 sh compile.sh}).</p>
 *
 * <p>Rulebook checks (all against the pinned working-tree bytes, see
 * {@code docs/RULEBOOK_PIN.md}):</p>
 * <ul>
 *   <li>SHA-256 equals the pin (catches any edit, re-save or
 *       normalisation of the canonical file);</li>
 *   <li>byte size and LF-line total equal the pin (689-vs-1236
 *       reconciliation: 689 is the <i>non-blank</i> count, 1,236 the
 *       <i>total</i> — same bytes, different denominators);</li>
 *   <li>the UTF-8 BOM is present (it is part of the pinned bytes);</li>
 *   <li>the mojibake set is exactly the three known
 *       {@code [emailÂ protected]} lines (1228, 1230, 1236) — a fix,
 *       a re-scrape or new damage all fail loudly here so the pin note
 *       gets updated deliberately, never silently.</li>
 * </ul>
 *
 * <p>Card checks ({@code premiere.json}, {@code deluxe.json},
 * {@code index.json}): strict-UTF-8 decodable with no {@code U+00C2}
 * mojibake marker and no replacement character. Sizes and line counts are
 * deliberately NOT pinned — card data grows under later work packages
 * (A1b adds a key field) and pinning it here would turn every data task
 * red for no reason.</p>
 *
 * <p>Deliberately NOT asserted: CRLF counts. The git blob is pure LF while
 * a Windows checkout carries 15 CRLF lines ({@code core.autocrlf}); LF
 * totals are stable across both, CR counts are not.</p>
 *
 * <p>Run after compile.sh / compile.bat:
 *   java -cp b5ccg/out b5ccg.engine.HeadlessRulebookPinProbe</p>
 *
 * <p><b>Java 6.</b> No diamond, no strings-in-switch, no
 * try-with-resources: streams close in {@code finally} blocks.</p>
 */
public class HeadlessRulebookPinProbe {

    /** 1-based numbers of the lines carrying the known
     *  {@code [emailÂ protected]} mojibake (Cloudflare
     *  email-decode artefact, U+00C2 U+00A0). */
    private static final int[] KNOWN_DAMAGE_LINES = new int[] { 1228, 1230, 1236 };

    private static int checks;
    private static int failures;

    private static void check(String label, boolean passed) {
        checks++;
        System.out.println("  [PIN] " + label + ": " + (passed ? "PASS" : "FAIL"));
        if (!passed) failures++;
    }

    public static void main(String[] args) {
        System.out.println("=== B5-0002 rulebook pin probe ===");

        File rulebook = firstExisting("BABYLON5_CCG_RULEBOOK.md",
                                      "../BABYLON5_CCG_RULEBOOK.md",
                                      "../../BABYLON5_CCG_RULEBOOK.md");
        check("rulebook file found", rulebook != null);
        if (rulebook != null) {
            System.out.println("Rulebook: " + rulebook.getPath());
            pinChecks(rulebook);
        }

        cardChecks(firstExisting("resources/cards/premiere.json",
                                 "../resources/cards/premiere.json",
                                 "../../resources/cards/premiere.json",
                                 "out/cards/premiere.json"),
                   "premiere.json");
        cardChecks(firstExisting("resources/cards/deluxe.json",
                                 "../resources/cards/deluxe.json",
                                 "../../resources/cards/deluxe.json",
                                 "out/cards/deluxe.json"),
                   "deluxe.json");
        cardChecks(firstExisting("resources/cards/index.json",
                                 "../resources/cards/index.json",
                                 "../../resources/cards/index.json",
                                 "out/cards/index.json"),
                   "index.json");

        System.out.println("");
        System.out.println("RULEBOOK PIN: " + (failures == 0 ? "PASS" : "FAIL")
                + " (" + (checks - failures) + "/" + checks + " checks passed)");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ── Rulebook pin ────────────────────────────────────────────────────

    private static void pinChecks(File f) {
        byte[] bytes;
        try {
            bytes = readAll(f);
            check("rulebook readable", true);
        } catch (Exception e) {
            check("rulebook readable", false);
            System.out.println("  read error: " + e.getMessage());
            return;
        }

        check("rulebook is " + RuleSetProfile.RULEBOOK_BYTES + " bytes",
                bytes.length == RuleSetProfile.RULEBOOK_BYTES);

        String sha;
        try {
            sha = sha256Hex(bytes);
            check("rulebook SHA-256 equals the pin",
                    RuleSetProfile.RULEBOOK_SHA256.equals(sha));
        } catch (Exception e) {
            check("rulebook SHA-256 equals the pin", false);
            System.out.println("  digest error: " + e.getMessage());
            sha = "";
        }
        if (!RuleSetProfile.RULEBOOK_SHA256.equals(sha)) {
            System.out.println("  actual: " + sha);
        }

        int lf = 0;
        for (int i = 0; i < bytes.length; i++) if (bytes[i] == (byte) '\n') lf++;
        check("rulebook holds " + RuleSetProfile.RULEBOOK_LINES_TOTAL + " LF lines",
                lf == RuleSetProfile.RULEBOOK_LINES_TOTAL);

        check("rulebook carries the UTF-8 BOM (part of the pinned bytes)",
                bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF);

        String text;
        try {
            text = strictUtf8(bytes);
            check("rulebook is strict-UTF-8 decodable", true);
        } catch (Exception e) {
            check("rulebook is strict-UTF-8 decodable", false);
            System.out.println("  decode error: " + e.getMessage());
            return;
        }

        // Line numbering must match the pin note's Python census: strip the
        // BOM, split on \n, strip one trailing \r — line 1 stays line 1.
        if (text.length() > 0 && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        String[] lines = text.split("\n", -1);
        int nonEmpty = 0;
        List<Integer> c2Lines = new ArrayList<Integer>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
            if (line.trim().length() > 0) nonEmpty++;
            if (line.indexOf('\u00C2') >= 0) c2Lines.add(Integer.valueOf(i + 1));
        }
        // A trailing newline leaves one empty tail element after the split;
        // it is not a line (Python's splitlines agrees: 1,236, not 1,237).
        int total = lines.length;
        if (total > 0 && lines[lines.length - 1].length() == 0) total--;
        check("rulebook line total reconciles (" + total + " total, "
                + nonEmpty + " non-blank)",
                total == RuleSetProfile.RULEBOOK_LINES_TOTAL
                && nonEmpty == RuleSetProfile.RULEBOOK_LINES_NONEMPTY);

        boolean damageOk = c2Lines.size() == KNOWN_DAMAGE_LINES.length;
        if (damageOk) {
            for (int i = 0; i < KNOWN_DAMAGE_LINES.length; i++) {
                if (c2Lines.get(i).intValue() != KNOWN_DAMAGE_LINES[i]) damageOk = false;
            }
        }
        check("mojibake set is exactly the three known email-protection lines",
                damageOk);
        if (!damageOk) System.out.println("  actual U+00C2 lines: " + c2Lines);
    }

    // ── Card JSON encoding ──────────────────────────────────────────────

    private static void cardChecks(File f, String label) {
        check(label + " found", f != null);
        if (f == null) return;
        byte[] bytes;
        try {
            bytes = readAll(f);
        } catch (Exception e) {
            check(label + " readable", false);
            return;
        }
        String text;
        try {
            text = strictUtf8(bytes);
            check(label + " is strict-UTF-8 decodable", true);
        } catch (Exception e) {
            check(label + " is strict-UTF-8 decodable", false);
            System.out.println("  decode error: " + e.getMessage());
            return;
        }
        check(label + " carries no U+00C2 mojibake marker",
                text.indexOf('\u00C2') < 0);
        check(label + " carries no U+FFFD replacement character",
                text.indexOf('\uFFFD') < 0);
    }

    // ── IO helpers ──────────────────────────────────────────────────────

    private static File firstExisting(String p1, String p2, String p3) {
        String[] ps = new String[] { p1, p2, p3 };
        for (int i = 0; i < ps.length; i++) {
            File f = new File(ps[i]);
            if (f.isFile()) return f;
        }
        return null;
    }

    private static File firstExisting(String p1, String p2, String p3, String p4) {
        File f = firstExisting(p1, p2, p3);
        if (f != null) return f;
        File g = new File(p4);
        return g.isFile() ? g : null;
    }

    private static byte[] readAll(File f) throws java.io.IOException {
        InputStream in = null;
        ByteArrayOutputStream out = null;
        try {
            in = new FileInputStream(f);
            out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { /* read-only */ }
        }
    }

    private static String strictUtf8(byte[] bytes) throws CharacterCodingException {
        return Charset.forName("UTF-8").newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digest.length; i++) {
            int b = digest[i] & 0xFF;
            if (b < 0x10) sb.append('0');
            sb.append(Integer.toHexString(b));
        }
        return sb.toString();
    }
}
