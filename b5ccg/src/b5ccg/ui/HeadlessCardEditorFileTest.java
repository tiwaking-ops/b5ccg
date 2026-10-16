package b5ccg.ui;

import b5ccg.model.enums.CardType;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.MarkType;
import b5ccg.model.enums.Rarity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

/**
 * B5-4109: headless-load test for CardEditorModel file I/O.
 *
 * <p>Covers the B5-4109 scope without a display: builds a fully-populated
 * model, saves it to a temp .json file via saveToFile, loads it back via
 * fromFile with all fields populated, then round-trips the saved JSON text
 * through the DeckLoader tokenizer shape (splitObjects) to prove a saved
 * card loads through the real loader pipeline. Ends with a fromMap probe
 * in the DeckLoader map shape.
 *
 * <p>Zero AWT/Swing imports: safe under -Djava.awt.headless=true.
 * Runnable as {@code java -cp out b5ccg.ui.HeadlessCardEditorFileTest}.
 * Exit codes: 0 all checks passed, 1 a check failed, 2 environment error.
 *
 * <p>Java 6 only: explicit type arguments, no diamond, no lambdas.
 */
public class HeadlessCardEditorFileTest {

    private static int checks = 0;
    private static int failed = 0;

    private static void check(String name, boolean condition) {
        checks++;
        if (condition) {
            System.out.println("PASS: " + name);
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    /** Builds a fully-populated model exercising every serialized field. */
    private static CardEditorModel populatedModel() {
        CardEditorModel model = new CardEditorModel();
        model.setId("b5-test-4109");
        model.setTitle("Test Envoy 4109");
        model.setType(CardType.CHARACTER);
        model.setSubtype("Ambassador");
        model.setRarity(Rarity.FIXED);
        model.setFaction(Faction.MINBARI);
        model.setSet(CardSet.PREMIERE);
        model.setImageKey("test_envoy_4109");
        model.setText("Draw 1 card.");
        model.setFlavorText("So be it.");
        model.setCost(3);
        model.setMercenary(true);
        model.setDiplomacy(4);
        model.setIntrigue(2);
        model.setPsi(5);
        model.setLeadership(1);
        model.setAmbassador(true);
        model.setMarkCount(MarkType.SHADOW, 2);
        model.setMarkCount(MarkType.VORLON, 0);
        model.setMarkCount(MarkType.DOOM, 1);
        return model;
    }

    private static void assertAllFields(String tag, CardEditorModel m) {
        check(tag + ": id", "b5-test-4109".equals(m.getId()));
        check(tag + ": title", "Test Envoy 4109".equals(m.getTitle()));
        check(tag + ": type", m.getType() == CardType.CHARACTER);
        check(tag + ": subtype", "Ambassador".equals(m.getSubtype()));
        check(tag + ": rarity", m.getRarity() == Rarity.FIXED);
        check(tag + ": faction", m.getFaction() == Faction.MINBARI);
        check(tag + ": set", m.getSet() == CardSet.PREMIERE);
        check(tag + ": imageKey", "test_envoy_4109".equals(m.getImageKey()));
        check(tag + ": text", "Draw 1 card.".equals(m.getText()));
        check(tag + ": flavorText", "So be it.".equals(m.getFlavorText()));
        check(tag + ": cost", m.getCost() == 3);
        check(tag + ": mercenary", m.isMercenary());
        check(tag + ": diplomacy", m.getDiplomacy() == 4);
        check(tag + ": intrigue", m.getIntrigue() == 2);
        check(tag + ": psi", m.getPsi() == 5);
        check(tag + ": leadership", m.getLeadership() == 1);
        check(tag + ": ambassador", m.isAmbassador());
        check(tag + ": marks SHADOW=2", m.getMarkCount(MarkType.SHADOW) == 2);
        check(tag + ": marks DOOM=1", m.getMarkCount(MarkType.DOOM) == 1);
        check(tag + ": marks VORLON=0", m.getMarkCount(MarkType.VORLON) == 0);
    }

    /** Reads a whole file as UTF-8 text (mirrors DeckLoader.readFile). */
    private static String readFile(File file) throws java.io.IOException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"));
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
            return text.toString();
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (java.io.IOException ignored) { }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Headless CardEditorModel file I/O test (B5-4109) ===");

        // 1. File round-trip: save a populated card, load it back, all
        // fields populated.
        File temp = File.createTempFile("cardeditor-4109-", ".json");
        temp.deleteOnExit();
        CardEditorModel original = populatedModel();
        original.saveToFile(temp);
        check("saveToFile creates non-empty file", temp.exists() && temp.length() > 0);

        CardEditorModel loaded = CardEditorModel.fromFile(temp);
        check("fromFile returns non-null model", loaded != null);
        assertAllFields("fromFile", loaded);

        // 2. Generated-card round-trip through the DeckLoader tokenizer:
        // the saved single-object file must parse as one card record when
        // wrapped in an array, proving DeckLoader.splitObjects accepts it.
        // splitObjects is package-private in b5ccg.engine, so reach it via
        // reflection rather than widening engine API for a test.
        String savedText = readFile(temp);
        List<Map<String, String>> records = null;
        String splitError = null;
        try {
            java.lang.reflect.Method split = Class.forName("b5ccg.engine.DeckLoader")
                .getDeclaredMethod("splitObjects", String.class);
            split.setAccessible(true);
            Object result = split.invoke(null, "[" + savedText + "]");
            records = (List<Map<String, String>>) result;
        } catch (Exception e) {
            splitError = String.valueOf(e);
        }
        check("DeckLoader.splitObjects reachable (no reflection error"
            + (splitError != null ? ": " + splitError : "") + ")", splitError == null);
        check("splitObjects yields exactly 1 record", records != null && records.size() == 1);
        if (records != null && records.size() == 1) {
            Map<String, String> record = records.get(0);
            check("record id matches", "b5-test-4109".equals(record.get("id")));
            check("record title matches", "Test Envoy 4109".equals(record.get("title")));
            CardEditorModel viaLoaderMap = CardEditorModel.fromMap(record);
            check("fromMap(record) id matches", "b5-test-4109".equals(viaLoaderMap.getId()));
            check("fromMap(record) title matches",
                "Test Envoy 4109".equals(viaLoaderMap.getTitle()));
            check("fromMap(record) cost matches", viaLoaderMap.getCost() == 3);
        }

        // 3. Headless fromMap probe: the DeckLoader map shape (all values
        // strings, marks as a nested fragment) loads with fields populated.
        java.util.Map<String, String> probe =
            new java.util.LinkedHashMap<String, String>();
        probe.put("id", "b5-test-4109-map");
        probe.put("title", "Map Probe");
        probe.put("type", "CHARACTER");
        probe.put("faction", "MINBARI");
        probe.put("set", "PREMIERE");
        probe.put("rarity", "COMMON");
        probe.put("cost", "2");
        probe.put("diplomacy", "3");
        probe.put("marks", "{\"SHADOW\":1}");
        CardEditorModel fromMapModel = CardEditorModel.fromMap(probe);
        check("fromMap id", "b5-test-4109-map".equals(fromMapModel.getId()));
        check("fromMap title", "Map Probe".equals(fromMapModel.getTitle()));
        check("fromMap cost", fromMapModel.getCost() == 2);
        check("fromMap diplomacy", fromMapModel.getDiplomacy() == 3);
        check("fromMap marks SHADOW=1",
            fromMapModel.getMarkCount(MarkType.SHADOW) == 1);

        if (!temp.delete()) {
            temp.deleteOnExit();
        }
        System.out.println(failed == 0
            ? "HEADLESS CARD EDITOR MODEL TEST PASSED (" + checks + " checks)"
            : "HEADLESS CARD EDITOR MODEL TEST FAILED ("
                + failed + " of " + checks + " checks)");
        System.out.flush();
        System.exit(failed == 0 ? 0 : 1);
    }
}
