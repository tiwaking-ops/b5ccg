package b5ccg.engine;

import b5ccg.model.*;
import b5ccg.model.enums.*;
import java.util.*;

/**
 * B5-1047 scratch probe — tracked (not git-ignored).
 * Drives an unknown conflictType value through DeckLoader.parseCards and asserts
 * the card stays reachable (not dropped) while the B5-1047 guard fires on stderr.
 */
public class B51047ConflictTypeProbe {
    public static void main(String[] args) throws Exception {
        // A single CONFLICT card with an unknown conflictType value.
        // Field names match DeckLoader.buildCard expectations (type, set, imageKey).
        String badJson = "["
            + "{\"id\":\"probe-b5-1047-x\",\"title\":\"Probe Conflict Bad Type\","
            + "\"type\":\"CONFLICT\",\"subtype\":\"CONFLICT_HUMAN\",\"rarity\":\"COMMON\","
            + "\"faction\":\"ANY\",\"set\":\"PREMIERE\",\"imageKey\":\"probe\",\"text\":\"probe\","
            + "\"conflictType\":\"NONEXISTENT\",\"influenceReward\":\"1\"}"
            + "]";

        // Capture stderr to verify the B5-1047 guard fired.
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.PrintStream oldErr = System.err;
        System.setErr(new java.io.PrintStream(baos));

        List<Card> cards = DeckLoader.parseCards(badJson);

        System.setErr(oldErr);
        String stderr = baos.toString();

        // Assert 1: the card is reachable (was not dropped)
        if (cards.size() != 1) {
            System.err.println("PROBE FAIL: expected 1 card, got " + cards.size());
            System.exit(1);
        }
        Card c = cards.get(0);
        if (!(c instanceof ConflictCard)) {
            System.err.println("PROBE FAIL: card is not a ConflictCard");
            System.exit(1);
        }
        ConflictCard cc = (ConflictCard) c;
        if (cc.getConflictType() != ConflictType.DIPLOMACY) {
            System.err.println("PROBE FAIL: expected DIPLOMACY fallback, got "
                + cc.getConflictType());
            System.exit(1);
        }

        // Assert 2: the B5-1047 guard logged loudly with the record id
        if (!stderr.contains("DECKLOADER-B5-1047")) {
            System.err.println("PROBE FAIL: stderr missing B5-1047 marker: "
                + stderr);
            System.exit(1);
        }
        if (!stderr.contains("probe-b5-1047-x")) {
            System.err.println("PROBE FAIL: stderr missing record id: "
                + stderr);
            System.exit(1);
        }
        if (!stderr.contains("NONEXISTENT")) {
            System.err.println("PROBE FAIL: stderr missing bad value: "
                + stderr);
            System.exit(1);
        }

        // Assert 3: legal values still parse (DIPLOMACY unchanged)
        String goodJson = "["
            + "{\"id\":\"probe-b5-1047-good\",\"title\":\"Probe Conflict Good\","
            + "\"type\":\"CONFLICT\",\"subtype\":\"CONFLICT_HUMAN\",\"rarity\":\"COMMON\","
            + "\"faction\":\"ANY\",\"set\":\"PREMIERE\",\"imageKey\":\"probe\",\"text\":\"probe\","
            + "\"conflictType\":\"MILITARY\",\"influenceReward\":\"2\"}"
            + "]";
        List<Card> good = DeckLoader.parseCards(goodJson);
        if (good.size() != 1) {
            System.err.println("PROBE FAIL: good json failed, size="
                + good.size());
            System.exit(1);
        }
        ConflictCard gc = (ConflictCard) good.get(0);
        if (gc.getConflictType() != ConflictType.MILITARY) {
            System.err.println("PROBE FAIL: legal MILITARY not preserved");
            System.exit(1);
        }

        System.out.println("PROBE PASS: B5-1047 conflictType guard verified — "
            + "unknown value falls back to DIPLOMACY with loud stderr, "
            + "legal values unchanged, card reachable.");
        System.exit(0);
    }
}
