package b5ccg.model;

import b5ccg.model.enums.*;
import java.util.*;

public abstract class Card {
    private final String   id;
    private final String   title;
    private final CardType type;
    private final String   subtype;
    private final Rarity   rarity;
    private final Loyalty  loyalty;
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
    // B5-0033 (WP-17, A6.3): a cost has THREE states, not two. The rulebook
    // at :1058 (RB:VI/cards-with-no-printed-cost) says "A card that does not
    // have an orange cost bubble ... has a cost that is undefined as far as
    // other cards are concerned. Undefined does not mean zero." The int field
    // above cannot express that, because it reads 0 for an absent cost, for a
    // cost of zero, and for a corrupt cost token alike. The state is recorded
    // here so the distinction survives the load, and the numeric field keeps
    // its historical meaning exactly.
    //
    // The numbers are deliberately not 0/1/2: they are the state ids, and a
    // reader that prints one sees "COST_ABSENT", not a number.
    public static final int COST_ABSENT  = 0;   // no cost key in the record
    public static final int COST_CORRUPT = 1;   // a cost key that is not a usable number
    public static final int COST_DEFINED = 2;   // a cost was assigned
    private int costState = COST_ABSENT;
    // B5-0395 (rulebook §Mercenaries): flagged cards may each turn be used only
    // by the player who applied the most influence to control them. A boolean
    // flag per B5-0386's schema rec (absent = false); NO subtype overload — a
    // mercenary can be any card type. The current data carries zero such cards
    // (B5-0386 no-evidence verdict), so this is a latent surface for synthetic
    // fixtures and future data work.
    private boolean mercenary = false;
    private final List<ContingencyCard> contingencies = new ArrayList<ContingencyCard>();

    protected Card(String id, String title, CardType type, String subtype,
                   Rarity rarity, Loyalty loyalty, CardSet cardSet,
                   String imageKey, String text) {
        this.id       = id;
        this.title    = title;
        this.type     = type;
        this.subtype  = subtype;
        this.rarity   = rarity;
        this.loyalty  = loyalty;
        this.cardSet  = cardSet;
        this.imageKey = imageKey;
        this.text     = text;
    }

    public String   getId()       { return id; }
    public String   getTitle()    { return title; }
    public CardType getType()     { return type; }
    public String   getSubtype()  { return subtype; }
    public Rarity   getRarity()   { return rarity; }
    public Loyalty  getLoyalty()  { return loyalty; }
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

    // ── B5-0033 (WP-17, A6.3): the three states of a cost ───────────────────
    //
    // getCost() keeps its historical contract exactly: it returns the number
    // the engine will charge. For an absent or corrupt cost that number is
    // still 0, because changing what the engine charges is a behaviour change
    // this row does not authorise. What is new is the ability to ASK whether
    // the 0 means "costs nothing" or "costs was never assigned", which the
    // int alone cannot answer.

    /**
     * Whether the card carries a printed cost at all.
     *
     * <p>False for a card with no cost key in the record and for a card whose
     * cost key holds something that is not a usable number. The rulebook's
     * test is the printed cost bubble, and a corrupt token is no more a
     * printed cost than a missing one is.</p>
     */
    public boolean hasDefinedCost() {
        return costState == COST_DEFINED;
    }

    /**
     * The cost as a {@link CardCost}: a number, or {@link CardCost#UNDEFINED}.
     *
     * <p>This is the single place the three states are turned into a value,
     * so a caller never has to re-derive the distinction from the raw
     * record.</p>
     */
    public CardCost getCardCost() {
        if (costState != COST_DEFINED) {
            return CardCost.UNDEFINED;
        }
        return CardCost.of(cost);
    }

    /**
     * Records that the card's cost was never assigned.
     *
     * <p>Called by the loader when the record carries no cost key. The
     * numeric field is left at 0, so {@link #getCost()} is unchanged and
     * every existing caller keeps behaving exactly as before.</p>
     */
    public void markCostAbsent() {
        this.costState = COST_ABSENT;
    }

    /**
     * Records that the card carries a cost key that is not a usable number.
     *
     * <p>The numeric field is clamped to 0 exactly as the loader's old
     * catch-block did, so the engine's behaviour is unchanged; what changes is
     * that the difference from "costs nothing" is now recorded rather than
     * lost.</p>
     */
    public void markCostCorrupt() {
        this.costState = COST_CORRUPT;
        this.cost = 0;
    }

    /**
     * Records that the card carries a usable printed cost.
     *
     * <p>Called by the loader when the record's cost key parses to a
     * non-negative integer.</p>
     */
    public void markCostDefined(int c) {
        this.cost = Math.max(0, c);
        this.costState = COST_DEFINED;
    }

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
