package b5ccg.model;

import b5ccg.model.enums.*;
import java.util.*;

public abstract class Card {
    private final String   id;
    private final String   title;
    private final CardType type;
    private final String   subtype;
    private final Rarity   rarity;
    private final Faction  faction;
    private final CardSet  cardSet;
    private final String   imageKey;
    private final String   text;

    private boolean faceDown = false;
    private boolean rotated  = false;
    private int damageTokens = 0;
    private int severeDamageTokens = 0;
    private boolean neutralized = false;
    private boolean neutralizedThisTurn = false;
    private int strifeMarks = 0;

    private int cost = 0;
    // B5-0395 (rulebook §Mercenaries): flagged cards may each turn be used only
    // by the player who applied the most influence to control them. A boolean
    // flag per B5-0386's schema rec (absent = false); NO subtype overload — a
    // mercenary can be any card type. The current data carries zero such cards
    // (B5-0386 no-evidence verdict), so this is a latent surface for synthetic
    // fixtures and future data work.
    private boolean mercenary = false;
    private final List<ContingencyCard> contingencies = new ArrayList<ContingencyCard>();

    protected Card(String id, String title, CardType type, String subtype,
                   Rarity rarity, Faction faction, CardSet cardSet,
                   String imageKey, String text) {
        this.id       = id;
        this.title    = title;
        this.type     = type;
        this.subtype  = subtype;
        this.rarity   = rarity;
        this.faction  = faction;
        this.cardSet  = cardSet;
        this.imageKey = imageKey;
        this.text     = text;
    }

    public String   getId()       { return id; }
    public String   getTitle()    { return title; }
    public CardType getType()     { return type; }
    public String   getSubtype()  { return subtype; }
    public Rarity   getRarity()   { return rarity; }
    public Faction  getFaction()  { return faction; }
    public CardSet  getCardSet()  { return cardSet; }
    public String   getImageKey() { return imageKey; }
    public String   getText()     { return text; }

    public boolean isFaceDown() { return faceDown; }
    public boolean isRotated()  { return rotated; }

    public void setFaceDown(boolean v) { faceDown = v; }
    public void setRotated(boolean v)  { rotated  = v; }
    public int getStrifeMarks() { return strifeMarks; }
    public void setStrifeMarks(int count) { strifeMarks = Math.max(0, count); }

    public int  getCost()      { return cost; }
    public void setCost(int c) { cost = Math.max(0, c); }

    public boolean isMercenary() { return mercenary; }
    public void    setMercenary(boolean v) { mercenary = v; }

    public void rotate()   { rotated  = true; }
    public void unrotate() { rotated  = false; }
    public void damage()   { faceDown = true; neutralized = true; neutralizedThisTurn = true; onNeutralized(); }
    public void heal()     { faceDown = false; neutralized = false; damageTokens = 0; severeDamageTokens = 0; }

    public int getDamageTokens() { return damageTokens; }
    public int getSevereDamageTokens() { return severeDamageTokens; }
    public boolean isNeutralized() { return neutralized; }
    public boolean wasNeutralizedThisTurn() { return neutralizedThisTurn; }
    public boolean canActAfterNeutralization() { return !neutralizedThisTurn; }
    public void resetNeutralizedTurnLock() { neutralizedThisTurn = false; }

    public int getGreatestAbility() { return 0; }

    public int applyDamage(int amount) {
        if (amount <= 0 || neutralized) return 0;
        damageTokens += amount;
        return reconcileDamage();
    }

    public int getAttackDamage(ConflictType type) {
        return Math.max(0, getPrimaryStatValue(type)) + 2 * strifeMarks;
    }

    public int applySevereDamage(int amount) {
        if (amount <= 0 || !neutralized) return 0;
        severeDamageTokens += amount;
        return amount;
    }

    public int reconcileDamage() {
        int greatest = Math.max(1, getGreatestAbility());
        if (neutralized || damageTokens < greatest) return 0;
        int overflow = damageTokens - greatest;
        damageTokens = 0;
        severeDamageTokens += overflow;
        neutralized = true;
        neutralizedThisTurn = true;
        faceDown = true;
        onNeutralized();
        return overflow;
    }

    protected void onNeutralized() { }

    public void neutralizeFromFleet() {
        if (neutralized) return;
        neutralized = true;
        neutralizedThisTurn = true;
        faceDown = true;
        onNeutralized();
    }

    public int healDamage() {
        if (neutralized) {
            if (severeDamageTokens > 0) severeDamageTokens--;
            else {
                neutralized = false;
                faceDown = false;
                damageTokens = 0;
            }
            return 1;
        }
        int healed = damageTokens;
        damageTokens = 0;
        return healed;
    }

    public int repairDamage(int amount) {
        if (amount <= 0 || neutralized) return 0;
        int repaired = Math.min(amount, damageTokens);
        damageTokens -= repaired;
        return repaired;
    }

    public int getContingencyCount() { return contingencies.size(); }

    public List<ContingencyCard> getContingencies() {
        return Collections.unmodifiableList(contingencies);
    }

    boolean addContingency(ContingencyCard contingency) {
        if (contingency == null || contingencies.contains(contingency)) return false;
        contingencies.add(contingency);
        return true;
    }
    boolean removeContingency(ContingencyCard contingency) {
        return contingencies.remove(contingency);
    }

    public abstract int getPrimaryStatValue(ConflictType type);

    @Override
    public String toString() { return title + " [" + type + "/" + rarity + "]"; }
}
