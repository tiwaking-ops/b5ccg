package b5ccg.ui;

import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.Deck;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.CardType;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.StatKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * B5-2265: the starting-game flow -- faction select, starting ambassador, the
 * three typed cards beside it, shuffle and cut, and initiative order -- with no
 * Swing in it.
 *
 * <p>WHY A SEPARATE CLASS, which is the B5-2287 shape applied a second time. Every
 * rule of the pre-game flow is a question with an answer: which races may be
 * seated, whose ambassador is it, is this three-card set legal, who acts first.
 * Inside a {@code JDialog} those are unanswerable without a display, because the
 * dialog cannot be constructed headlessly -- the defect B5-2287 recorded for the
 * deck builder, where the rules had zero automated coverage for exactly that
 * reason. So the rules live here and {@link MainWindow} holds the pixels.
 *
 * <p>NO SECOND AUTHORITY. Two things this class deliberately does NOT do. It does
 * not re-implement the rulebook :208 one-race-per-player test or the :284
 * influence floor -- it calls {@link MainWindow#validateSetup} for those, which
 * already owns them. And it does not decide who wins a conflict or when a round
 * ends; the engine owns that and this class only reports what the rulebook says
 * the opening turn looks like.
 *
 * <p>THE GAP THIS FLOW EXISTS TO CLOSE, measured not assumed. Rulebook :228 says
 * each player <em>selects</em> a starting hand of four, :230-:244 names the
 * ambassador, and :246 constrains the other three. What the shipped boot path
 * actually does is {@code Main.drawCards(4)} after pinning the ambassador to the
 * top of the deck, followed by {@code GameController.setupGame()} drawing three
 * more -- so the hand a player actually begins with is seven cards drawn at
 * random, with no type constraint whatsoever. {@link #handDeviation} measures
 * that gap rather than asserting it, and {@link #applyStartingGame} reconciles it
 * while the opening window is still open.
 *
 * <p>Java 6 only: explicit type arguments, no diamond, no lambdas, index loops.
 */
public final class StartingGameFlowModel {

    /** Rulebook :228 -- "a starting hand of four cards". */
    public static final int STARTING_HAND_SIZE = 4;

    /** Rulebook :246 -- "The other three remaining cards". */
    public static final int TYPED_REST_SIZE = STARTING_HAND_SIZE - 1;

    /** Rulebook :284 -- four tokens is the starting Influence Rating. */
    public static final int DEFAULT_STARTING_INFLUENCE = 4;

    /**
     * Rulebook :208 -- the four Premier player races. NON_ALIGNED is deliberately
     * absent for the same reason B5-2008 recorded: it needs the :884/:890 second
     * species ambassador, which this flow does not build, so offering it would be
     * a control that promises something this window cannot deliver.
     */
    public static final Faction[] PLAYER_RACES = new Faction[] {
        Faction.HUMAN, Faction.MINBARI, Faction.CENTAURI, Faction.NARN
    };

    /**
     * Rulebook :246's own example, used as the PREFERENCE order for which three
     * distinct types to propose. The rule itself is only "may not be of the same
     * type"; the example "one additional character, one fleet and one agenda" is
     * the rulebook naming a legal set, so proposing that set is a choice this
     * class makes and says it makes, not a rule it is enforcing.
     */
    private static final CardType[] TYPED_REST_PREFERENCE = new CardType[] {
        CardType.CHARACTER, CardType.FLEET, CardType.AGENDA
    };

    private final List<Player> players;
    private final List<Card>   pool;
    private Random random;

    /**
     * @param players the seated players, in seat order.
     * @param pool    the authored card pool. Held so the ambassador and the typed
     *                 three can be resolved by name and by type without touching
     *                 the classpath again.
     */
    public StartingGameFlowModel(List<Player> players, List<Card> pool) {
        this.players = new ArrayList<Player>();
        if (players != null) {
            for (int i = 0; i < players.size(); i++) {
                if (players.get(i) != null) this.players.add(players.get(i));
            }
        }
        this.pool = new ArrayList<Card>();
        if (pool != null) {
            for (int i = 0; i < pool.size(); i++) {
                if (pool.get(i) != null) this.pool.add(pool.get(i));
            }
        }
        this.random = new Random();
    }

    /** Deterministic mode for tests and probes: a non-zero seed pins every draw. */
    public void setSeed(long seed) {
        this.random = (seed == 0L) ? new Random() : new Random(seed);
    }

    // ── Faction select (rulebook :208, delegated to MainWindow) ───────────────

    /** The races a seat may be given. */
    public List<Faction> offeredRaces() {
        List<Faction> out = new ArrayList<Faction>();
        for (int i = 0; i < PLAYER_RACES.length; i++) out.add(PLAYER_RACES[i]);
        return out;
    }

    /**
     * The one-race-per-player rule and the :284 influence floor, DELEGATED to
     * {@link MainWindow#validateSetup} rather than reimplemented. Returns an empty
     * list when the proposed setup is legal.
     */
    public List<String> setupProblems(Faction[] races, int[] startingInfluence) {
        List<String> problems = new ArrayList<String>();
        String verdict = MainWindow.validateSetup(races, startingInfluence);
        if (verdict != null) problems.add(verdict);
        return problems;
    }

    public static boolean isPlayerRace(Faction race) {
        for (int i = 0; i < PLAYER_RACES.length; i++) {
            if (PLAYER_RACES[i] == race) return true;
        }
        return false;
    }

    // ── Starting ambassador (rulebook :230-:244, :266, :486) ─────────────────

    /**
     * The ambassador rulebook :230-:244 names for a race. A NAME, not a Card: the
     * card itself is resolved from the pool by this name, so the rulebook stays
     * the authority on who the ambassador is and the authored data stays the
     * authority on that card's stats. NON_ALIGNED returns null because :884 says
     * there is no single ambassador for the League.
     */
    public static String ambassadorName(Faction race) {
        if (race == Faction.HUMAN)    return "Jeffrey Sinclair";
        if (race == Faction.CENTAURI) return "Londo Mollari";
        if (race == Faction.MINBARI)  return "Delenn";
        if (race == Faction.NARN)     return "G'Kar";
        return null;
    }

    /**
     * The rulebook-named ambassador for a race, resolved out of a candidate list
     * (a deck, or the pool). Falls back to any ambassador-flagged character of
     * that faction when the named card is absent, and returns null when neither
     * exists -- a named lookup that reports failure beats a fallback that quietly
     * seats the wrong leader.
     */
    public static CharacterCard resolveAmbassador(Faction race, List<Card> candidates) {
        if (race == null || candidates == null) return null;
        String wanted = ambassadorName(race);
        CharacterCard fallback = null;
        for (int i = 0; i < candidates.size(); i++) {
            Card c = candidates.get(i);
            if (!(c instanceof CharacterCard)) continue;
            CharacterCard ch = (CharacterCard) c;
            if (!ch.isAmbassador()) continue;
            if (ch.getFaction() != race) continue;
            if (wanted != null && wanted.equals(ch.getTitle())) return ch;
            if (fallback == null) fallback = ch;
        }
        return fallback;
    }

    // ── The three typed cards (rulebook :246) ────────────────────────────────

    /**
     * Proposes the three non-ambassador starting cards: three cards of three
     * DIFFERENT types, each playable by the seat's race, drawn from
     * {@code candidates} excluding the ambassador. Preference order is
     * {@link #TYPED_REST_PREFERENCE}, then any unused type in enum order.
     *
     * <p>Returns fewer than three when the candidate list cannot supply three
     * distinct playable types. It never pads by repeating a type: a short result
     * is the honest one, and {@link #typedRestProblems} will reject the hand.
     */
    public List<Card> proposeTypedRest(Faction race, List<Card> candidates, CharacterCard ambassador) {
        List<Card> chosen = new ArrayList<Card>();
        if (candidates == null) return chosen;

        for (int p = 0; p < TYPED_REST_PREFERENCE.length; p++) {
            Card c = firstOfType(candidates, TYPED_REST_PREFERENCE[p], race, ambassador, chosen);
            if (c != null) chosen.add(c);
            if (chosen.size() == TYPED_REST_SIZE) return chosen;
        }
        CardType[] all = CardType.values();
        for (int t = 0; t < all.length && chosen.size() < TYPED_REST_SIZE; t++) {
            if (alreadyChosenType(chosen, all[t])) continue;
            Card c = firstOfType(candidates, all[t], race, ambassador, chosen);
            if (c != null) chosen.add(c);
        }
        return chosen;
    }

    private static boolean alreadyChosenType(List<Card> chosen, CardType type) {
        for (int i = 0; i < chosen.size(); i++) {
            if (chosen.get(i).getType() == type) return true;
        }
        return false;
    }

    private static Card firstOfType(List<Card> candidates, CardType type, Faction race,
                                    CharacterCard ambassador, List<Card> chosen) {
        for (int i = 0; i < candidates.size(); i++) {
            Card c = candidates.get(i);
            if (c.getType() != type) continue;
            if (c == ambassador) continue;
            if (race != null && c.getFaction() != null && !c.getFaction().isPlayableBy(race)) continue;
            boolean taken = false;
            for (int j = 0; j < chosen.size(); j++) {
                if (chosen.get(j) == c) taken = true;
            }
            if (!taken) return c;
        }
        return null;
    }

    /**
     * Rulebook :246 over a proposed hand -- the three non-ambassador cards must
     * not be of the same type, i.e. no type may appear twice. The count and the
     * ambassador presence are checked too so a caller can validate a whole
     * four-card hand in one call.
     *
     * <p>Deliberately NOT checked here: that the cards are playable by the race,
     * and that none of them is the ambassador. Playability is a per-deck question
     * {@link b5ccg.engine.DeckLoader#validatePlayDeck} already answers, and the
     * ambassador is checked by its own rule (:488 -- an ambassador may never be
     * sponsored, so it is not one of the three).
     */
    public List<String> typedRestProblems(List<Card> rest) {
        List<String> problems = new ArrayList<String>();
        if (rest == null) {
            problems.add("no starting cards proposed");
            return problems;
        }
        if (rest.size() != TYPED_REST_SIZE) {
            problems.add("starting cards: " + rest.size() + " proposed, rulebook :246 allows "
                + TYPED_REST_SIZE);
        }
        for (int i = 0; i < rest.size(); i++) {
            Card a = rest.get(i);
            if (a == null) { problems.add("null starting card at index " + i); continue; }
            if (a instanceof CharacterCard && ((CharacterCard) a).isAmbassador()) {
                problems.add("starting card " + a.getTitle()
                    + " is an ambassador; :488 forbids sponsoring one and :246 counts only the other three");
            }
            for (int j = i + 1; j < rest.size(); j++) {
                Card b = rest.get(j);
                if (b != null && a.getType() == b.getType()) {
                    problems.add("two starting cards of type " + a.getType()
                        + " (" + a.getTitle() + ", " + b.getTitle()
                        + "); rulebook :246 -- may not be of the same type");
                }
            }
        }
        return problems;
    }

    // ── Shuffle and cut (rulebook :258-:260) ─────────────────────────────────

    /**
     * "All players shuffle their decks thoroughly and allow an opponent to cut
     * their deck."
     *
     * <p>A cut is implemented with only {@link Deck}'s public surface: draw
     * {@code cutSize} from the top and put each card at the bottom, which is
     * exactly the physical cut, and cannot see or reorder anything it does not
     * move. A cut size outside 1..size-1 is a NO-OP rather than a clamp, because
     * clamping would silently perform a different cut than the caller asked for.
     */
    public void shuffleAndCut(Deck deck, int cutSize) {
        if (deck == null || deck.isEmpty()) return;
        deck.shuffle();
        int size = deck.size();
        if (cutSize < 1 || cutSize >= size) return;
        List<Card> lifted = new ArrayList<Card>();
        for (int i = 0; i < cutSize; i++) lifted.add(deck.draw());
        for (int i = 0; i < lifted.size(); i++) {
            if (lifted.get(i) != null) deck.addToBottom(lifted.get(i));
        }
    }

    /** A legal cut size for a deck of this size, drawn from this model's RNG. */
    public int chooseCutSize(Deck deck) {
        if (deck == null || deck.size() < 2) return 0;
        return 1 + random.nextInt(deck.size() - 1);
    }

    // ── Initiative (rulebook :316, :352, :302) ──────────────────────────────

    /**
     * Rulebook :352 -- ranked by Influence Rating, the LOWEST acts first. On a tie
     * the player with the highest Diplomacy on his ambassador <em>acts last</em>,
     * so within a tie the lower Diplomacy acts earlier; the same applies in turn
     * to Intrigue, Psi and then Leadership. A residual tie is broken by seat
     * order, which is a DETERMINISTIC stand-in for :352's "determine initiative
     * order between them randomly" -- chosen so the display cannot flicker
     * between two refreshes of the same position.
     *
     * @return true when {@code a} acts before {@code b}.
     */
    public static boolean actsBefore(Player a, Player b) {
        if (a == b) return false;
        int cmp = a.getInfluence() - b.getInfluence();
        if (cmp != 0) return cmp < 0;
        cmp = compareAbility(a, b, StatKey.DIPLOMACY);
        if (cmp != 0) return cmp < 0;
        cmp = compareAbility(a, b, StatKey.INTRIGUE);
        if (cmp != 0) return cmp < 0;
        cmp = compareAbility(a, b, StatKey.PSI);
        if (cmp != 0) return cmp < 0;
        cmp = compareAbility(a, b, StatKey.LEADERSHIP);
        if (cmp != 0) return cmp < 0;
        return a.getName().compareTo(b.getName()) < 0;
    }

    private static int compareAbility(Player a, Player b, StatKey stat) {
        return ability(a, stat) - ability(b, stat);
    }

    /** Effective ability off the ambassador, 0 when the seat has none (:316). */
    public static int ability(Player p, StatKey stat) {
        if (p == null) return 0;
        CharacterCard amb = p.getAmbassador();
        if (amb == null || amb.isFaceDown()) return 0;
        return amb.getEffectiveStat(stat);
    }

    /** The seats in the order they act: index 0 acts first. */
    public List<Player> initiativeOrder() {
        List<Player> ordered = new ArrayList<Player>(players);
        Collections.sort(ordered, new java.util.Comparator<Player>() {
            @Override public int compare(Player a, Player b) {
                return actsBefore(a, b) ? -1 : (actsBefore(b, a) ? 1 : 0);
            }
        });
        return ordered;
    }

    /** Rulebook :352 as one readable line, lowest initiative first. */
    public String initiativeDescription() {
        List<Player> ordered = initiativeOrder();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ordered.size(); i++) {
            if (i > 0) sb.append("  ->  ");
            Player p = ordered.get(i);
            sb.append(p.getName()).append(" (").append(p.getInfluence());
            if (p.getAmbassador() != null) {
                sb.append(", D").append(ability(p, StatKey.DIPLOMACY))
                  .append("/I").append(ability(p, StatKey.INTRIGUE))
                  .append("/P").append(ability(p, StatKey.PSI))
                  .append("/L").append(ability(p, StatKey.LEADERSHIP));
            }
            sb.append(")");
        }
        return sb.toString();
    }

    /**
     * Rulebook :302 -- "start with the first Conflict round. The player with the
     * lowest initiative may declare a conflict first." True when this table's
     * lowest-initiative seat is the human, which is the only case in which the
     * human is entitled to open the game.
     */
    public boolean humanMayDeclareFirstConflict() {
        List<Player> ordered = initiativeOrder();
        return !ordered.isEmpty() && ordered.get(0).isHuman();
    }

    // ── The opening window, and the deviation this flow measures ─────────────

    /**
     * True while the opening window is still open: round 1, no conflict declared
     * by anyone, and no conflict in play. This is the GATE that can go red -- once
     * the first conflict lands, the starting game is history and this flow must
     * refuse rather than rewrite a hand somebody has already played from.
     */
    public static boolean isOpeningWindowOpen(GameState state) {
        if (state == null) return false;
        if (state.getRoundNumber() != 1) return false;
        if (state.getActiveConflict() != null) return false;
        List<Player> all = state.getPlayers();
        for (int i = 0; i < all.size(); i++) {
            if (state.hasInitiatedConflictThisTurn(all.get(i))) return false;
        }
        return true;
    }

    /**
     * What the shipped boot path actually dealt for a seat, against what
     * rulebook :228/:246 asks for. Returned as prose so it can go straight into
     * the game log, and deliberately counted from live state rather than
     * hardcoded from reading Main.java.
     *
     * @return one line per seat, or a single line naming why it could not be
     *         measured.
     */
    public List<String> handDeviation(GameState state) {
        List<String> lines = new ArrayList<String>();
        if (state == null) {
            lines.add("hand deviation: no state to measure");
            return lines;
        }
        List<Player> all = state.getPlayers();
        for (int i = 0; i < all.size(); i++) {
            Player p = all.get(i);
            List<Card> hand = p.getHand();
            lines.add(p.getName() + ": dealt " + hand.size()
                + " (rulebook :228 selects " + STARTING_HAND_SIZE + ")");
        }
        return lines;
    }

    // ── Applying the reconciled starting game ───────────────────────────────

    /**
     * Reconciles the live table's starting hands to rulebook :228/:246 and performs
     * the :258-:260 shuffle and cut, then reports the :302 opening.
     *
     * <p>Refuses outright when {@link #isOpeningWindowOpen} is false, and returns
     * the refusal as its first line rather than throwing: this is called from a
     * dialog, and a dialog that crashes on a late click is worse than one that
     * explains itself.
     *
     * <p>What it changes, per seat, and only what :228/:246/:258-:260 authorise:
     * the hand is trimmed to exactly four cards (ambassador plus three), every
     * surplus card goes to the BOTTOM of that seat's own deck rather than being
     * discarded, the deck is shuffled and cut, and each seat's ambassador is
     * seated in the Inner Circle if it was not already (:266, :486). Nothing else
     * about the seat is touched -- influence, tension, board and agenda are left
     * exactly as the engine set them.
     */
    public List<String> applyStartingGame(GameState state) {
        List<String> receipt = new ArrayList<String>();
        if (!isOpeningWindowOpen(state)) {
            receipt.add("starting-game flow refused: the opening window is closed "
                + "(round " + (state == null ? "?" : String.valueOf(state.getRoundNumber()))
                + ", a conflict is declared or in play). The starting hand is "
                + "already history and this flow will not rewrite it.");
            return receipt;
        }

        List<Player> all = state.getPlayers();
        for (int i = 0; i < all.size(); i++) {
            Player p = all.get(i);
            List<String> seatReceipt = reconcileSeat(p);
            for (int j = 0; j < seatReceipt.size(); j++) receipt.add(seatReceipt.get(j));
        }

        receipt.add("Shuffle and cut (rulebook :258) complete for "
            + all.size() + " decks.");
        List<Player> ordered = initiativeOrder();
        if (!ordered.isEmpty()) {
            receipt.add("Initiative (rulebook :352, lowest first): " + initiativeDescription());
            receipt.add("Rulebook :302 -- play begins with the first Conflict round; "
                + ordered.get(0).getName() + " has the lowest initiative"
                + (ordered.get(0).isHuman() ? " and may declare first."
                                            : " and may declare first."));
        }
        return receipt;
    }

    /** The per-seat half of {@link #applyStartingGame}. */
    private List<String> reconcileSeat(Player p) {
        List<String> lines = new ArrayList<String>();
        if (p == null) return lines;

        List<Card> hand = p.getHand();

        // :266/:486 -- the ambassador sits in front of the faction and is an
        // Inner Circle member. GameController.setupGame already seats it; this
        // only fills the gap when it did not, and never seats a second one.
        if (p.getAmbassador() == null) {
            CharacterCard amb = resolveAmbassador(p.getFaction(), hand);
            if (amb != null) {
                p.setAmbassador(amb);
                p.getInnerCircle().add(amb);
                lines.add(p.getName() + ": ambassador seated from hand -- " + amb.getTitle());
            } else {
                lines.add(p.getName() + ": NO ambassador found in hand for "
                    + ambassadorName(p.getFaction()) + "; hand left as dealt");
                return lines;
            }
        }

        // Build the rulebook hand from what was ACTUALLY dealt: the ambassador,
        // then three cards of distinct types. Candidates come from the hand alone,
        // never from the library -- this flow reconciles a hand somebody was dealt,
        // and quietly substituting better cards would be a second dealing
        // mechanism competing with the boot path's.
        List<Card> typed = proposeTypedRest(p.getFaction(), hand, p.getAmbassador());
        List<String> typedProblems = typedRestProblems(typed);
        for (int k = 0; k < typedProblems.size(); k++) {
            lines.add(p.getName() + ": " + typedProblems.get(k));
        }

        List<Card> keep = new ArrayList<Card>();
        keep.add(p.getAmbassador());
        for (int t = 0; t < typed.size(); t++) {
            if (!keep.contains(typed.get(t))) keep.add(typed.get(t));
        }

        // Trim to :228, returning every surplus card to the BOTTOM of this seat's
        // own deck. Not discarded: the rulebook has no opening discard, and a card
        // put back is recoverable where a card thrown away is not.
        Deck deck = p.getDeck();
        int dealt = hand.size();
        List<Card> returning = new ArrayList<Card>();
        for (int h = 0; h < hand.size(); h++) {
            Card c = hand.get(h);
            if (!keep.contains(c)) returning.add(c);
        }
        if (!returning.isEmpty()) {
            for (int h = 0; h < returning.size(); h++) {
                if (deck != null) deck.addToBottom(returning.get(h));
            }
            hand.retainAll(keep);
            lines.add(p.getName() + ": dealt " + dealt + " cards at random; kept "
                + hand.size() + " per rulebook :228 and returned " + returning.size()
                + " to the bottom of the deck");
        }

        // :258-:260 -- shuffle thoroughly, then cut.
        int cut = chooseCutSize(deck);
        shuffleAndCut(deck, cut);
        if (deck != null && cut > 0) {
            lines.add(p.getName() + ": shuffled and cut " + cut + " (rulebook :260)");
        }
        return lines;
    }
}