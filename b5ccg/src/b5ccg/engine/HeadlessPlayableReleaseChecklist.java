package b5ccg.engine;

import b5ccg.ai.AIPlayer;
import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.Deck;
import b5ccg.model.GameAction;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.AIDifficulty;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.GamePhase;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * B5-2532 — playable-release checklist conformance (standalone CLI, new file).
 *
 * Implements the executable half of
 * {@code docs/proposals/2026-10-03-b5-2283-playable-release-checklist-proposal.md}.
 * The proposal defines "playable" as a conjunction of conditions; this harness
 * is the instrument that decides that conjunction, and it is deliberately
 * explicit about which conditions it can measure itself.
 *
 * <p><b>Two classes of condition, and the difference is the whole point.</b>
 *
 * <ul>
 *   <li><b>ASSERTED</b> — measured here, from real loaded data, on this JVM.
 *       A failure is a non-zero exit.</li>
 *   <li><b>EXTERNAL</b> — not observable from inside a running Java process:
 *       the compile exit code, the seven headless gate exit codes, the
 *       standing Java 6 construct census, and the repo-wide citation check.
 *       These are measured by the shipped instruments named beside each one.
 *       This harness will NOT report a playable release while any of them is
 *       unattested, because "I could not see it" is not "it is green" — the
 *       B5-0609/AGENT_LOOP lesson that an absent signal is UNKNOWN, never a
 *       pass.</li>
 * </ul>
 *
 * <p>Attest an external condition with {@code --attest-external}; the harness
 * then records it as operator-attested (and says so in its output). Omitting
 * the flag is not a failure of a condition — it is an unattested condition,
 * and the exit code distinguishes the two.
 *
 * <p><b>Exit codes.</b>
 * <pre>
 *   0 = every ASSERTED condition passed and every EXTERNAL condition attested
 *   1 = at least one ASSERTED condition FAILED (a real defect signal)
 *   2 = harness error (resources missing) or at least one EXTERNAL condition
 *       is UNATTESTED — deliberately distinct from 0, because a run that could
 *       not see a condition must never report as a clean release (B5-1733's
 *       "exit 2 is a false pass that must never be read as clean")
 * </pre>
 *
 * <p><b>Why this is not wired into compile.sh.</b> The seven headless gates are
 * wired there already; this harness asserts their <i>presence</i> from the
 * classpath and delegates their <i>exit codes</i> to that run, so wiring it in
 * would assert a condition from inside a process that is itself one of the
 * things being asserted. Run it after a green build:
 *
 * <pre>
 *   java -cp b5ccg/out b5ccg.engine.HeadlessPlayableReleaseChecklist
 *   java -cp b5ccg/out b5ccg.engine.HeadlessPlayableReleaseChecklist --attest-external
 * </pre>
 *
 * <p><b>Card-count vocabulary (B5-2532 measurement).</b> The ledger row asks
 * for "deck loads 829 cards". Measured: premiere.json holds 446 records and
 * deluxe.json 383, so 829 is the <i>raw both-sets record total</i>; the pool the
 * engine actually serves, after {@link DeckLoader#loadBothSets()} drops the 383
 * reprinted Premiere titles in favour of the Deluxe copy, is <b>446</b>. Both
 * numbers are asserted here and both are printed, so a later data change that
 * moves either one is caught and the reading is never ambiguous.
 *
 * Java 6 only. No game-logic file is edited (B5-0201 harness precedent).
 */
public class HeadlessPlayableReleaseChecklist {

    // ── Conditions ────────────────────────────────────────────────────────────

    /** The seven headless gates compile.sh runs under RUN_TESTS=1 (B5-0956). */
    private static final String[] HEADLESS_GATES = {
        "b5ccg.engine.HeadlessAIDifficultyContractTest",
        "b5ccg.engine.HeadlessConflictResolutionProbe",
        "b5ccg.engine.HeadlessHumanConflictAttackWindowTest",
        "b5ccg.engine.HeadlessLeadFleetScenarioProbe",
        "b5ccg.engine.HeadlessParticipationGatesProbe",
        "b5ccg.engine.HeadlessStationVictoryTest",
        "b5ccg.engine.HeadlessWarConflictProbe"
    };

    /** The two classes compile.sh runs before the gate tier (B5-0308, B5-0201). */
    private static final String[] PREFLIGHT_CLASSES = {
        "b5ccg.engine.HeadlessConformanceTest",
        "b5ccg.engine.HeadlessSmokeTest"
    };

    /** Classes sampled for the Java 6 class-file target level. */
    // Absolute resource paths (leading slash). Class.getResourceAsStream
    // resolves a leading-slash name against the classpath root; a relative name
    // resolves against THIS class's own package and silently returns null — which
    // is what the first run did. The class files are present in the build output,
    // so a null here means the lookup was wrong, not the build, and a harness
    // that cannot tell those apart is a harness that reports nonsense.
    private static final String[] TARGET_SAMPLES = {
        "/b5ccg/engine/RulesEngine.class",
        "/b5ccg/model/Player.class",
        "/b5ccg/ai/AIPlayer.class",
        "/b5ccg/ui/MainWindow.class"
    };

    /** Raw record counts per set file, as of B5-2532. */
    private static final int EXPECTED_PREMIERE_RECORDS = 446;
    private static final int EXPECTED_DELUXE_RECORDS   = 383;
    /** 446 + 383: the raw both-sets total the ledger row names. */
    private static final int EXPECTED_RAW_TOTAL        = 829;
    /** What loadBothSets() actually serves: the 383 reprints collapse onto Deluxe. */
    private static final int EXPECTED_POOL             = 446;

    private static final Faction[] PLAYABLE_FACTIONS = {
        Faction.HUMAN, Faction.MINBARI, Faction.CENTAURI, Faction.NARN
    };

    // ── Tallies ───────────────────────────────────────────────────────────────

    private static int asserted  = 0;
    private static int failures  = 0;
    private static final List<String> failureLines = new ArrayList<String>();
    private static final List<String> unattested   = new ArrayList<String>();

    private static boolean attestExternal = false;

    public static void main(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--attest-external".equals(args[i])) {
                attestExternal = true;
            } else {
                System.err.println("HARNESS ERROR: unknown argument '" + args[i] + "'");
                System.err.println("Usage: java -cp out b5ccg.engine.HeadlessPlayableReleaseChecklist [--attest-external]");
                System.exit(2);
                return;
            }
        }

        System.out.println("=== B5-2532 playable-release checklist ===");
        System.out.println("java.version = " + System.getProperty("java.version")
                + ", java.class.version = " + System.getProperty("java.class.version"));
        System.out.println();

        try {
            assertJava6TargetLevel();   // PR-01
            assertPreflightClasses();  // PR-02
            assertHeadlessGates();     // PR-03
            List<Card> pool = loadAndAssertPool(); // PR-04, PR-05
            assertStarterDecks(pool);  // PR-06
            assertHumanLegalAction(pool); // PR-07
        } catch (IOException e) {
            System.err.println("HARNESS ERROR: card resources unavailable: " + e.getMessage());
            System.exit(2);
            return;
        }

        reportExternalConditions();

        System.out.println();
        System.out.println("ASSERTED: " + asserted + " condition(s), " + failures + " failed.");
        if (!unattested.isEmpty()) {
            System.out.println("UNATTESTED EXTERNAL: " + unattested.size() + " condition(s) — "
                    + "this run CANNOT report a playable release.");
        }

        System.out.println();
        if (failures > 0) {
            for (int i = 0; i < failureLines.size(); i++) {
                System.out.println("  FAILED: " + failureLines.get(i));
            }
            System.out.println();
            System.out.println("PLAYABLE-RELEASE CHECKLIST FAILED: " + failures + " asserted condition(s) failed.");
            System.exit(1);
            return;
        }
        if (!unattested.isEmpty()) {
            System.out.println("PLAYABLE-RELEASE CHECKLIST INCOMPLETE: every asserted condition passed, "
                    + "but " + unattested.size() + " external condition(s) are unattested. "
                    + "Not a failure; not a pass either.");
            System.exit(2);
            return;
        }
        System.out.println("PLAYABLE-RELEASE CHECKLIST PASSED: every asserted condition passed and every "
                + "external condition was attested. Scope note: this certifies the automated preflight "
                + "only — the proposal's human 30-minute session remains mandatory.");
    }

    // ── PR-01: the build really is Java 6 bytecode ───────────────────────────
    //
    // javac is authoritative on syntax; this is the API/target dimension read
    // back off the shipped class files. A class-file major version above 50
    // means something was compiled without -source 6 -target 6, which a source
    // grep cannot see.

    private static void assertJava6TargetLevel() throws IOException {
        section("PR-01  class-file target level is Java 6 (major version 50)");
        for (int i = 0; i < TARGET_SAMPLES.length; i++) {
            int major = classFileMajorVersion(TARGET_SAMPLES[i]);
            check(TARGET_SAMPLES[i] + " major version " + major + " == 50 (Java 6)",
                  major == 50);
        }
    }

    private static int classFileMajorVersion(String resourcePath) throws IOException {
        InputStream is = HeadlessPlayableReleaseChecklist.class.getResourceAsStream(resourcePath);
        if (is == null) return -1;
        try {
            DataInputStream in = new DataInputStream(is);
            int magic = in.readInt();
            if (magic != 0xCAFEBABE) return -2;
            in.readUnsignedShort(); // minor
            return in.readUnsignedShort(); // major
        } finally {
            try { is.close(); } catch (IOException e) { /* ignore */ }
        }
    }

    // ── PR-02 / PR-03: the gates the checklist depends on are actually present ─

    private static void assertPreflightClasses() {
        section("PR-02  conformance suite and smoke test are on the classpath");
        for (int i = 0; i < PREFLIGHT_CLASSES.length; i++) {
            check(PREFLIGHT_CLASSES[i] + " resolves with a main(String[])",
                  resolvesWithMain(PREFLIGHT_CLASSES[i]));
        }
    }

    private static void assertHeadlessGates() {
        section("PR-03  all 7 headless gates are on the classpath");
        check("gate tier is exactly 7 classes", HEADLESS_GATES.length == 7);
        for (int i = 0; i < HEADLESS_GATES.length; i++) {
            check(HEADLESS_GATES[i] + " resolves with a main(String[])",
                  resolvesWithMain(HEADLESS_GATES[i]));
        }
    }

    private static boolean resolvesWithMain(String className) {
        try {
            Class<?> c = Class.forName(className);
            c.getMethod("main", String[].class);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ── PR-04 / PR-05: the card pool loads and is well-formed ─────────────────

    private static List<Card> loadAndAssertPool() throws IOException {
        section("PR-04  card pool loads from the build output (829 raw records, 446 in pool)");
        List<Card> premiere = DeckLoader.loadFromResource("/cards/premiere.json");
        List<Card> deluxe   = DeckLoader.loadFromResource("/cards/deluxe.json");
        List<Card> pool     = DeckLoader.loadBothSets();

        check("premiere.json records == " + EXPECTED_PREMIERE_RECORDS
                + " (got " + premiere.size() + ")", premiere.size() == EXPECTED_PREMIERE_RECORDS);
        check("deluxe.json records == " + EXPECTED_DELUXE_RECORDS
                + " (got " + deluxe.size() + ")", deluxe.size() == EXPECTED_DELUXE_RECORDS);
        check("raw both-sets total == " + EXPECTED_RAW_TOTAL
                + " (got " + (premiere.size() + deluxe.size()) + ")",
              premiere.size() + deluxe.size() == EXPECTED_RAW_TOTAL);
        check("deduplicated pool == " + EXPECTED_POOL
                + " (got " + pool.size() + ")", pool.size() == EXPECTED_POOL);
        System.out.println("  NOTE: 829 is the raw both-sets record total; "
                + pool.size() + " is what the engine serves after reprint collapse.");

        section("PR-05  every pool card has a unique id and a non-empty title");
        Set<String> ids = new HashSet<String>();
        int blankTitle = 0;
        int nullId = 0;
        for (int i = 0; i < pool.size(); i++) {
            Card c = pool.get(i);
            if (c.getId() == null || c.getId().length() == 0) nullId++;
            else if (!ids.add(c.getId())) { blankTitle++; } // reuse of an id
            if (c.getTitle() == null || c.getTitle().length() == 0) blankTitle++;
        }
        check("no card has a null or empty id (got " + nullId + ")", nullId == 0);
        check("no card has a null/empty title and no id is reused (got " + blankTitle + ")",
              blankTitle == 0);
        return pool;
    }

    // ── PR-06: each faction can actually be dealt a legal deck ────────────────

    private static void assertStarterDecks(List<Card> pool) throws IOException {
        section("PR-06  all four playable factions build a legal starter deck");
        StarterDeckBuilder.setRandomSeed(42L);
        try {
            for (int i = 0; i < PLAYABLE_FACTIONS.length; i++) {
                Faction f = PLAYABLE_FACTIONS[i];
                List<Card> deck = StarterDeckBuilder.build(f, pool);
                check(f + " starter deck is non-empty (got " + deck.size() + ")", !deck.isEmpty());
                boolean legal;
                try {
                    StarterDeckBuilder.validateDeck(deck, f);
                    legal = true;
                } catch (RuntimeException e) {
                    legal = false;
                    System.out.println("    validateDeck(" + f + "): " + e.getMessage());
                }
                check(f + " starter deck passes StarterDeckBuilder.validateDeck", legal);
            }
        } finally {
            StarterDeckBuilder.setRandomSeed(0L); // restore the default draw
        }
    }

    // ── PR-07: the human seat has at least one legal action in ACTION ─────────
    //
    // This is the checklist's "a new player is never stuck" condition, reduced
    // to what is measurable headlessly: at the opening ACTION window, the
    // engine's own offer builder (AIPlayer.buildLegalActions — the same seam
    // AIPlayer.chooseAction reads, and the human dispatcher mirrors) must offer
    // the human seat something other than PASS. Reflection on the private
    // method follows the B5-0349 reflection precedent already used by
    // HeadlessHumanSeatProbe to seed AIPlayer.rng; no game-logic file changes.

    private static void assertHumanLegalAction(List<Card> pool) throws IOException {
        section("PR-07  the human seat has at least one non-PASS legal action in ACTION phase");

        Method buildLegalActions;
        try {
            buildLegalActions = AIPlayer.class.getDeclaredMethod(
                    "buildLegalActions", GameState.class, Player.class);
            buildLegalActions.setAccessible(true);
        } catch (NoSuchMethodException e) {
            check("AIPlayer.buildLegalActions(GameState, Player) is reachable for reflection "
                    + "(a rename here must fail the checklist, not pass it)", false);
            return;
        }
        check("AIPlayer.buildLegalActions(GameState, Player) is reachable for reflection", true);

        StarterDeckBuilder.setRandomSeed(42L);
        try {
            for (int i = 0; i < PLAYABLE_FACTIONS.length; i++) {
                Faction f = PLAYABLE_FACTIONS[i];
                List<Player> players = new ArrayList<Player>();
                for (int j = 0; j < PLAYABLE_FACTIONS.length; j++) {
                    Faction fj = PLAYABLE_FACTIONS[j];
                    List<Card> deck = new ArrayList<Card>(StarterDeckBuilder.build(fj, pool));
                    Player p = new Player("Checklist" + fj, fj, fj == f);
                    CharacterCard amb = findAmbassador(deck, fj);
                    if (amb != null) {
                        deck.remove(deck.indexOf(amb));
                        p.setAmbassador(amb);
                        p.getInnerCircle().add(amb);
                    }
                    p.setDeck(new Deck(deck));
                    p.drawCards(4); // the opening hand
                    players.add(p);
                }
                GameState st = new GameState(players);
                for (int j = 0; j < players.size(); j++) players.get(j).setGameState(st);
                st.setPhase(GamePhase.ACTION);

                Player human = players.get(0);
                // The offer builder is stateless with respect to the seat it is
                // asked about; any AIPlayer instance supplies the same rules.
                // MEDIUM specifically: AIPlayer's constructor builds an
                // AIDecisionEngine, whose AIMemory dereferences the difficulty
                // in its constructor, so a null tier NPEs here. Difficulty does
                // not enter buildLegalActions at all — the offer list is the
                // same for every tier, which is the difficulty contract
                // (B5-0351) — so MEDIUM is an arbitrary legal choice, not a
                // behavioural choice.
                AIPlayer offerer = new AIPlayer(human, AIDifficulty.MEDIUM);
                List<?> offers = (List<?>) invokeLegalActions(buildLegalActions, offerer, st, human);
                if (offers == null) {
                    check(f + " legal-action offer list is obtainable", false);
                    continue;
                }
                int nonPass = 0;
                for (int j = 0; j < offers.size(); j++) {
                    Object o = offers.get(j);
                    if (o instanceof GameAction
                            && ((GameAction) o).getType() != GameAction.Type.PASS) {
                        nonPass++;
                    }
                }
                System.out.println("  " + f + ": hand " + human.getHand().size()
                        + " card(s), " + offers.size() + " legal offer(s), "
                        + nonPass + " non-PASS.");
                check(f + " human seat has >= 1 non-PASS legal action in ACTION (got " + nonPass + ")",
                      nonPass >= 1);
            }
        } finally {
            StarterDeckBuilder.setRandomSeed(0L);
        }
    }

    private static Object invokeLegalActions(Method m, Object target, GameState st, Player p) {
        try {
            return m.invoke(target, new Object[] { st, p });
        } catch (Exception e) {
            return null;
        }
    }

    private static CharacterCard findAmbassador(List<Card> deck, Faction f) {
        for (int i = 0; i < deck.size(); i++) {
            Card c = deck.get(i);
            if (c instanceof CharacterCard && c.getFaction() == f) {
                CharacterCard ch = (CharacterCard) c;
                if (ch.isAmbassador()) return ch;
            }
        }
        return null;
    }

    // ── EXTERNAL conditions ───────────────────────────────────────────────────
    //
    // Each carries the shipped instrument that measures it. None of them is
    // measurable from inside this JVM, and none is silently assumed green.

    private static void reportExternalConditions() {
        section("EXTERNAL  conditions this harness cannot observe from inside the JVM");
        external("EXT-01  compile green (javac -source 6 -target 6, exit 0)",
                 "cd b5ccg && sh compile.sh");
        external("EXT-02  all 7 headless gates PASS on their exit codes",
                 "cd b5ccg && RUN_TESTS=1 sh compile.sh");
        external("EXT-03  no Java 6 construct violations in tracked b5ccg/src",
                 "PYTHONIOENCODING=utf-8 py .agent/tools/census-b50960.py");
        external("EXT-04  no dangling citations across the coordination files",
                 "powershell -NoProfile -ExecutionPolicy Bypass -File .agent/tools/check-citations.ps1");
    }

    private static void external(String condition, String instrument) {
        System.out.println("  " + (attestExternal ? "ATTESTED " : "UNATTESTED ")
                + condition);
        System.out.println("      instrument: " + instrument);
        if (!attestExternal) unattested.add(condition);
    }

    // ── Reporting helpers ─────────────────────────────────────────────────────

    private static void section(String title) {
        System.out.println("-- " + title);
    }

    private static void check(String label, boolean ok) {
        asserted++;
        if (ok) {
            System.out.println("  [PR] PASS  " + label);
        } else {
            failures++;
            failureLines.add(label);
            System.out.println("  [PR] FAIL  " + label);
        }
    }
}
