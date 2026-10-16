package b5ccg.engine;

import b5ccg.model.Card;
import b5ccg.model.enums.Faction;
import java.io.IOException;
import java.util.*;

/**
 * B5-1823: Behavioral control test - load all starter decks through StarterDeckBuilder
 * and compare with static deck JSON analysis.
 * 
 * This is a one-off diagnostic tool, not a permanent test.
 */
public class B51823DeckCensus {

    public static void main(String[] args) {
        System.out.println("=== B5-1823 Behavioral Control: StarterDeckBuilder Load Test ===\n");
        
        Faction[] factions = {Faction.HUMAN, Faction.CENTAURI, Faction.MINBARI, Faction.NARN};
        
        for (Faction faction : factions) {
            System.out.println("--- " + faction + " ---");
            try {
                List<Card> deck = StarterDeckBuilder.buildForFaction(faction);
                System.out.println("  Loaded " + deck.size() + " cards");
                
                // Count fixed vs random
                int fixedCount = 0;
                int randomCount = 0;
                Set<String> fixedIds = new HashSet<String>();
                
                // Load the fixed entries to know which IDs are fixed
                try {
                    List<Map<String, String>> entries = StarterDeckBuilder.loadEntriesForTest(faction);
                    for (Map<String, String> e : entries) {
                        fixedIds.add(e.get("id"));
                    }
                } catch (Exception e) {
                    // ignore
                }
                
                for (Card c : deck) {
                    if (fixedIds.contains(c.getId())) {
                        fixedCount++;
                    } else {
                        randomCount++;
                    }
                }
                
                System.out.println("  Fixed cards (by Premiere ID): " + fixedCount);
                System.out.println("  Random cards: " + randomCount);
                
                // Check for any cards that failed to resolve (should not happen)
                for (Card c : deck) {
                    if (c.getTitle() == null || c.getTitle().isEmpty()) {
                        System.out.println("  WARNING: Card with empty title: " + c.getId());
                    }
                }
                
            } catch (IOException e) {
                System.out.println("  ERROR: " + e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("  ERROR: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("\n=== Test Complete ===");
    }
}