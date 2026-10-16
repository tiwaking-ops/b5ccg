package b5ccg.engine;

import java.io.IOException;
import java.util.*;
import b5ccg.model.Card;

public class TestCardLoader {
    public static void main(String[] args) {
        try {
            System.out.println("Testing loadBothSets...");
            java.util.List<Card> cards = DeckLoader.loadBothSets();
            System.out.println("Loaded " + cards.size() + " cards");
            System.out.println("Validation passed");
        } catch (IOException e) {
            System.err.println("IO error: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}