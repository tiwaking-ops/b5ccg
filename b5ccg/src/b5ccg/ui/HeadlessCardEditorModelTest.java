package b5ccg.ui;

import b5ccg.model.enums.CardType;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.MarkType;
import b5ccg.model.enums.Rarity;

import java.util.ArrayList;
import java.util.List;

/**
 * B5-4111: headless JSON round-trip test for CardEditorModel.
 *
 * <p>Covers the B5-4111 scope without a display: builds a fully-populated
 * model, emits {@code toJson()}, parses that text back with {@code fromJson},
 * and round-trips strings, numbers, booleans and the nested marks fragment.
 * Also verifies {@code validateRequired()} on the populated model plus the
 * missing-field report on an empty one, and that {@code flavor_text} (the
 * Caption field B5-4110 wired) survives the round-trip identically.
 *
 * <p>Zero AWT/Swing imports: safe under -Djava.awt.headless=true.
 * Runnable as {@code java -cp out b5ccg.ui.HeadlessCardEditorModelTest}.
 * Exit codes: 0 all checks passed, 1 a check failed.
 *
 * <p>Java 6 only: explicit type arguments, no diamond, no lambdas.
 */
public class HeadlessCardEditorModelTest {

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
        model.setId("b5-test-4111");
        model.setTitle("Test Envoy 4111");
        model.setType(CardType.CHARACTER);
        model.setSubtype("Ambassador");
        model.setRarity(Rarity.FIXED);
        model.setFaction(Faction.MINBARI);
        model.setSet(CardSet.PREMIERE);
        model.setImageKey("test_envoy_4111");
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
        model.setMarkCount(MarkType.DOOM, 1);
        return model;
    }

    /** Asserts every common + CHARACTER field against a rebuilt model. */
    private static void assertAllFields(String tag, CardEditorModel m) {
        check(tag + ": id", "b5-test-4111".equals(m.getId()));
        check(tag + ": title", "Test Envoy 4111".equals(m.getTitle()));
        check(tag + ": type", m.getType() == CardType.CHARACTER);
        check(tag + ": subtype", "Ambassador".equals(m.getSubtype()));
        check(tag + ": rarity", m.getRarity() == Rarity.FIXED);
        check(tag + ": faction", m.getFaction() == Faction.MINBARI);
        check(tag + ": set", m.getSet() == CardSet.PREMIERE);
        check(tag + ": imageKey", "test_envoy_4111".equals(m.getImageKey()));
        check(tag + ": text", "Draw 1 card.".equals(m.getText()));
        check(tag + ": flavorText", "So be it.".equals(m.getFlavorText()));
        check(tag + ": cost", m.getCost() == 3);
        check(tag + ": mercenary", m.isMercenary());
        check(tag + ": diplomacy", m.getDiplomacy() == 4);
        check(tag + ": intrigue", m.getIntrigue() == 2);
        check(tag + ": psi", m.getPsi() == 5);
        check(tag + ": leadership", m.getLeadership() == 1);
        check(tag + ": isAmbassador", m.isAmbassador());
        check(tag + ": marks SHADOW", m.getMarkCount(MarkType.SHADOW) == 2);
        check(tag + ": marks DOOM", m.getMarkCount(MarkType.DOOM) == 1);
    }

    private static void assertMissingFields(String tag, List<String> missing) {
        // The default constructor defaults type=CHARACTER (never null), so a
        // fresh model never reports type as missing; the editor's gate is the
        // two free-text fields id and title (CardEditorModel.validateRequired
        // javadoc). Assert exactly that pair.
        List<String> expected = new ArrayList<String>();
        expected.add("id");
        expected.add("title");
        check(tag + ": missing=={id,title} (type defaulted)", missing != null
            && missing.size() == 2
            && expected.containsAll(missing)
            && missing.containsAll(expected));
    }

    public static void main(String[] args) throws Exception {
        // 1. Round-trip: toJson() -> fromJson() -> assert every field.
        CardEditorModel original = populatedModel();
        String json = original.toJson();
        CardEditorModel rebuilt = CardEditorModel.fromJson(json);
        assertAllFields("toJson->fromJson", rebuilt);

        // 2. flavor_text (Caption) must survive the round-trip identically.
        check("flavor_text survives round-trip",
            "So be it.".equals(rebuilt.getFlavorText()));

        // 3. marks fragment round-trips (nonzero types only, DeckLoader shape).
        check("marks SHADOW survives round-trip",
            rebuilt.getMarkCount(MarkType.SHADOW) == 2);
        check("marks DOOM survives round-trip",
            rebuilt.getMarkCount(MarkType.DOOM) == 1);
        check("marks VORLON absent (nonzero-only emit)",
            rebuilt.getMarkCount(MarkType.VORLON) == 0);

        // 4. validateRequired() on the populated model: nothing missing.
        List<String> missingOnPopulated = original.validateRequired();
        check("populated model has no missing required fields",
            missingOnPopulated == null || missingOnPopulated.isEmpty());

        // 5. validateRequired() on an empty model: reports exactly id/title/type.
        CardEditorModel emptyModel = new CardEditorModel();
        List<String> emptyMissing = emptyModel.validateRequired();
        assertMissingFields("empty model", emptyMissing);

        // 6. fromMap with the nested marks fragment only (DeckLoader map shape).
        java.util.Map<String, String> probe =
            new java.util.LinkedHashMap<String, String>();
        probe.put("id", "probe-4111");
        probe.put("title", "Probe");
        probe.put("type", "CHARACTER");
        probe.put("faction", "MINBARI");
        probe.put("set", "PREMIERE");
        probe.put("rarity", "COMMON");
        probe.put("marks", "{\"SHADOW\":1,\"DOOM\":2}");
        CardEditorModel probeModel = CardEditorModel.fromMap(probe);
        check("probe id", "probe-4111".equals(probeModel.getId()));
        check("probe marks SHADOW=1",
            probeModel.getMarkCount(MarkType.SHADOW) == 1);
        check("probe marks DOOM=2",
            probeModel.getMarkCount(MarkType.DOOM) == 2);
        check("probe marks VORLON=0",
            probeModel.getMarkCount(MarkType.VORLON) == 0);

        System.out.println(failed == 0
            ? "HEADLESS CARD EDITOR MODEL TEST PASSED (" + checks + " checks)"
            : "HEADLESS CARD EDITOR MODEL TEST FAILED ("
                + failed + " of " + checks + " checks)");
        System.out.flush();
        System.exit(failed == 0 ? 0 : 1);
    }
}
