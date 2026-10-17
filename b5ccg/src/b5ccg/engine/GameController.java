package b5ccg.engine;

import b5ccg.ai.AIPlayer;
import b5ccg.model.*;
import b5ccg.model.enums.*;
import java.io.IOException;
import java.util.*;

/** Central game loop. Runs off the Swing EDT via a background thread.
 *  Notifies the UI through a GameStateCallback callback after each state change.
 *
 *  Build Influence handling added per B5-0301 — the ACTION branch now treats a
 *  BUILD_INFLUENCE action by calling RulesEngine.canBuildInfluence() and
 *  RulesEngine.executeBuildInfluence().
 */
public class GameController {

    private final GameState         state;
    private final RulesEngine       rules   = new RulesEngine();
    private final List<AIPlayer>    aiPlayers;
    private final GameStateCallback uiCallback;

    private volatile boolean        waitingForHuman = false;
    private volatile GameAction     pendingHumanAction;
    private volatile boolean        waitingForHumanConflictJoin = false;
    private volatile GameAction     pendingHumanConflictJoin;
    private volatile boolean        waitingForHumanConflictAttack = false;
    private volatile GameAction     pendingHumanConflictAttack;
    // B5-4163: the Conflict-round human declaration window — the human may
    // declare (INITIATE_CONFLICT / DECLARE_WAR_CONFLICT) or pass. Declaration
    // is a conflict-round act; the round sets GamePhase.CONFLICT and only
    // offers declarations then, so the strict phase placement is enforced by
    // the flow (see runConflictRound) without re-gating RulesEngine
    // .canInitiateConflict (which B5-4160's keeper probe drives at ACTION
    // phase and must stay queuing).
    private volatile boolean        waitingForHumanConflictDeclaration = false;
    private volatile GameAction     pendingHumanConflictDeclaration;
    // B5-4165: the Draw round's human window — the phase holds DRAW while
    // the human buys cards; PASS (or a DRAW-phase surrender/forfeit) ends it.
    private volatile boolean        waitingForHumanDrawEnd = false;
    // B5-4171 build repair: the human's Resolution Step 2 (:418) aftermath
    // window. MainWindow has called isWaitingForHumanAftermath() at four
    // sites since B5-4158 while the accessor existed nowhere in the engine,
    // which left the shared tree at 4 javac errors for every other scope.
    // Filled in here rather than deleting the UI half, which is completed
    // work belonging to another row.
    private volatile boolean        waitingForHumanAftermath = false;
    private volatile GameAction     pendingHumanAftermath;

    // B5-4167: round-end notification callback (optional, for UI popup)
    private RoundEndCallback        roundEndCallback;

    // ── B5-2275: consecutive-full-table-pass stall guard ─────────────────────
    //  B5-0312 measured a game reaching a 20-20 tie with no winner: D12 crowns
    //  a player only on a STRICTLY-greatest Influence Rating, so a table where
    //  nobody can act any more reaches a fixed point the rulebook never
    //  resolves.  The guard below is a liveness measure, NOT a victory rule:
    //  it changes no legal action and adds no win anyone could earn in play.
    //  After MAX_CONSECUTIVE_FULL_PASS_ROUNDS consecutive rounds in which the
    //  WHOLE table passed, the controller stops the game: a strictly-greatest
    //  Influence leader is crowned, and a tie is reported loudly and left
    //  uncrowned.
    public static final int         MAX_CONSECUTIVE_FULL_PASS_ROUNDS = 3;
    private int                     consecutiveFullPassRounds = 0;
    private volatile boolean        stallTieEnding = false;
    private boolean                 lastRoundWasFullTablePass = false;

    // ── B5-2237: Resolution Step 3 deferred aftermath discard ─────────────────
    //  Rulebook Resolution Step 3 (:440) — "Aftermath cards which are to be
    //  discarded after play are also discarded at this time" — is a statement
    //  about WHEN, and :428 spells out that the timing is load-bearing:
    //  "Aftermaths which occur and are then discarded should only be discarded
    //  at the end of the aftermath step for that conflict. Therefore, a
    //  'discard after play' aftermath can only be played once on any given
    //  target for each conflict."
    //
    //  The engine used to discard a one-shot the instant its effect resolved,
    //  i.e. inside Resolution Step 2 (:418 "Play Aftermath Cards"), which is the
    //  wrong step. Queueing the discard and flushing it after the whole step is
    //  what makes the rulebook's consequence true: during the step a one-shot is
    //  in play and NOT yet in any discard pile, so nothing that runs inside the
    //  step can observe it as already discarded or pick it back up. This is the
    //  same deferral the game already uses for the conflict card itself, which
    //  Step 3 discards in the same breath (:440).
    private final List<PendingAftermathDiscard> pendingAftermathDiscards =
        new ArrayList<PendingAftermathDiscard>();

    /** One deferred one-shot aftermath discard: the card and the player who owns it. */
    static final class PendingAftermathDiscard {
        final Player    owner;
        final AftermathCard card;
        PendingAftermathDiscard(Player owner, AftermathCard card) {
            this.owner = owner;
            this.card  = card;
        }
    }

    /**
     * B5-2237: a lasting aftermath stays in play on its target; only an
     * immediate-effect one-shot is discarded after play. The distinction is the
     * rulebook's own (Aftermath Cards, :590): "Most aftermath cards act like
     * enhancements, i.e., they modify the card they are played on ... Some
     * aftermath cards cause an immediate effect ... Immediate effects are not
     * reversed when the aftermath that caused them is discarded."
     *
     * Measured across all 20 aftermaths the engine dispatches: the 18 immediate
     * ones (Negotiated Surrender, Diplomatic Advantage, United Front, Draw Two,
     * Hidden Agent, Refugees, Rise to Power, Approval of the Grey, Focus Your
     * Efforts, Glory, Retribution, Reverse Advances, Opponent Lose One/Two Inf,
     * Crisis of Self, Heal Face Down, Rescue, Martyr) carry no "permanently",
     * and the 2 enhancements (War Hero, Battle Tested) both carry it. So the
     * word is the discriminator the data actually supports.
     */
    static boolean isPermanentAftermath(AftermathCard am) {
        return am != null && am.getText() != null
            && am.getText().toLowerCase().contains("permanently");
    }

    /**
     * Queues a one-shot for the Resolution Step 3 discard. Never discards in place.
     *
     * B5-2406: MEMBERSHIP GUARD. This used to append unconditionally, so the
     * same physical card queued twice produced two queue entries and the
     * flush discarded it twice — two copies of one card in one discard pile.
     * The registry it now guards is the one the D4 gate reads
     * (GameState.canAttachAftermath, one of each named aftermath in play per
     * target), so a duplicate entry is not a harmless repeat: it is the same
     * name occupying the same slot twice.
     *
     * Identity is the card's own object, not its id: two DIFFERENT physical
     * copies of "Rise to Power" are two legal cards and both must queue. Only
     * the same instance is a duplicate. That is why this compares `card` by
     * reference rather than calling getId().
     */
    void queueAftermathDiscard(Player owner, AftermathCard card) {
        if (owner == null || card == null) return;
        for (PendingAftermathDiscard pd : pendingAftermathDiscards) {
            if (pd.card == card) return;   // already queued this step
        }
        pendingAftermathDiscards.add(new PendingAftermathDiscard(owner, card));
    }

    /** How many one-shots are waiting for the end-of-step discard. */
    int getPendingAftermathDiscardCount() { return pendingAftermathDiscards.size(); }

    /**
     * Resolution Step 3 (:440) — the discard itself, run once the whole
     * aftermath step has finished. Clears the queue, so a second call with
     * nothing queued is a no-op rather than a double discard.
     */
    void flushPendingAftermathDiscards() {
        for (PendingAftermathDiscard pd : pendingAftermathDiscards) {
            pd.owner.getDeck().discard(pd.card);
            state.log(pd.owner.getName() + " discards " + pd.card.getTitle()
                      + " — aftermath resolved, discarded after play.");
        }
        pendingAftermathDiscards.clear();
    }

    public GameController(GameState state, List<AIPlayer> aiPlayers,
                          GameStateCallback uiCallback) {
        this.state      = state;
        this.aiPlayers  = aiPlayers;
        this.uiCallback = uiCallback;
        // B5-0008 (WP-07/A5): when the game carries a seed, derive a
        // deterministic RNG stream per AI seat (B5-0349 precedent formula).
        // Seats with no entry in the list are simply unseeded. Deck streams
        // use seed + 1_000_000L * (seat + 1) so they never collide with these.
        long gameSeed = state.getSeed();
        if (gameSeed != 0L && aiPlayers != null) {
            for (int i = 0; i < aiPlayers.size(); i++) {
                AIPlayer ai = aiPlayers.get(i);
                if (ai != null) ai.setSeed(gameSeed + i * 1000L);
            }
        }
    }

    /** B5-4167: set an optional callback for round-end notifications. */
    public void setRoundEndCallback(RoundEndCallback cb) {
        this.roundEndCallback = cb;
    }

    // ── Called from a background thread ──────────────────────────────────────

    public void runGame() {
        setupGame();
        notifyUI();

         while (!state.isGameOver()) {
             rules.startRound(state);
             // The phase label + banner belong to the round itself: runConflictRound
             // owns them so a direct call (a probe, or B5-4168's five-phase test)
             // sees the CONFLICT transition exactly like runGame does.
             runConflictRound();
             if (state.isGameOver()) break;
             state.setPhase(GamePhase.ACTION);
             state.log("=== ACTION phase (initiative-ordered actions) ===");
             notifyUI();

            runActionPhase();
            if (state.isGameOver()) break;

            // B5-2275: liveness guard. A round in which the WHOLE table passed
            // is a fixed point the rulebook cannot resolve (D12 needs a
            // strictly-greatest leader). Count consecutive such rounds; any
            // round containing a real action resets the count to zero, so a
            // single legal action proves the table is alive.
            if (lastRoundWasFullTablePass) consecutiveFullPassRounds++;
            else                               consecutiveFullPassRounds = 0;
            if (consecutiveFullPassRounds >= MAX_CONSECUTIVE_FULL_PASS_ROUNDS) {
                resolveStall();
                break;
            }

            runMercenaryPhase();   // B5-0395 (rulebook §Mercenaries :735–:741)
            if (state.isGameOver()) break;

            // B5-4163/B5-4170 (report §8; rulebook III Resolution round):
            // conflicts queued during the CONFLICT round survive the action
            // round and resolve here, in initiation order — S1 winner by strict
            // support (multi-side: strictly highest side), S2 aftermath window,
            // S3 conflict-card discard. Nothing resolves inline at initiation
            // any more. Rulebook order after this point is the B5-1982
            // AFTERMATH phase (draw 2, discard to 7), then Draw.
            resolveQueuedConflicts();
            if (state.isGameOver()) break;

            // B5-1982: AFTERMATH phase — draw 2, discard to 7 with reshuffle
            state.setPhase(GamePhase.AFTERMATH);
            notifyUI();
            rules.aftermathPhase(state);
            if (state.isGameOver()) break;

            runDrawPhase();

            // B5-0437: station end-of-round maintenance must run BEFORE
            // advanceRound() — advanceRound() resets stationSourceFired,
            // which the maintenance consults to decide decay
            // (RulesEngine.applyEndOfRoundStation).
            rules.applyEndOfRoundStation(state);

            state.advanceRound();
            notifyUI();
        }
    }

    private void setupGame() {
        state.setPhase(GamePhase.SETUP);
        List<Card> pool = null;
        try {
            pool = DeckLoader.loadBothSets();
        } catch (IOException e) {
            state.log("Failed to load card pool for starting setup: " + e.getMessage());
        }
        for (Player p : state.getPlayers()) {
            CharacterCard amb = findAmbassador(p);
            if (amb != null) {
                p.getHand().remove(amb);
                p.setAmbassador(amb);
                // B5-0321: the ambassador IS an Inner Circle member
                // (rulebook: "your ambassador is always considered a member
                // of your faction's Inner Circle"). Without this seat,
                // canBuildInfluence and canPromote see an empty Inner Circle
                // and those actions are never legal — the root cause of
                // B5-0202 Finding 7's unreachable Build Influence.
                p.getInnerCircle().add(amb);
                // B5-2412: seating ONE copy is not the same as there being one.
                // Rulebook :230 names exactly one Starting Ambassador card per
                // race and :199 makes "must contain one Starting Ambassador" a
                // deck-construction rule, so a second copy of that card is a
                // card the game should not contain at all. findAmbassador
                // returned on the FIRST match, so every further copy stayed in
                // the hand (playable) or in the draw pile (drawable later), and
                // the reported symptom was a second Jeffrey Sinclair sitting in
                // the human hand. Sweep them here, once, at setup.
                int swept = sweepDuplicateStartingAmbassadors(p, amb);
                if (swept > 0) {
                    state.log(p.getName() + " removes " + swept + " duplicate "
                              + amb.getTitle() + " (rulebook :230 -- one Starting "
                              + "Ambassador per race).");
                }
            }
            // B5-1973: Race-specific starting setup (homeworld, starting fleet, starting agenda)
            if (pool != null) {
                StarterDeckBuilder.StartingSetup setup = StarterDeckBuilder.getStartingSetup(p.getRace(), pool);
                if (setup != null) {
                    List<Card> seatedSetup = new ArrayList<Card>();
                    if (setup.homeworld != null) {
                        p.getLocations().add(setup.homeworld);
                        state.log(p.getName() + " starts with homeworld: " + setup.homeworld.getTitle());
                        seatedSetup.add(setup.homeworld);
                    }
                    if (setup.startingFleet != null) {
                        p.getFleets().add(setup.startingFleet);
                        state.log(p.getName() + " starts with fleet: " + setup.startingFleet.getTitle());
                        seatedSetup.add(setup.startingFleet);
                    }
                    if (setup.startingAgenda != null) {
                        p.setAgenda(setup.startingAgenda);
                        state.log(p.getName() + " starts with agenda: " + setup.startingAgenda.getTitle());
                        seatedSetup.add(setup.startingAgenda);
                    }
                    // B5-4242: the printed starter deck's fixed 50 contains the
                    // homeworld, starting fleet and starting agenda, but rulebook
                    // "Preparing the Playing Field" seats those from the card
                    // pool, so each copy also remained in the draw pile and could
                    // be drawn later as a duplicate of a card already in play.
                    int setupSwept = sweepDuplicateSetupCards(p, seatedSetup);
                    if (setupSwept > 0) {
                        state.log(p.getName() + " removes " + setupSwept
                                + " starting-setup card(s) already in play"
                                + " (rulebook -- one homeworld, one starting fleet"
                                + " and one starting agenda per faction at setup).");
                    }
                }
            }
            p.drawCards(3);
        }
        state.log("Game setup complete.");
    }

    private CharacterCard findAmbassador(Player p) {
        for (Card c : p.getHand()) {
            if (c instanceof CharacterCard) {
                CharacterCard ch = (CharacterCard) c;
                if (ch.isAmbassador() && ch.getLoyalty() == Loyalty.fromRace(p.getRace())) return ch;
            }
        }
        return null;
    }

    /**
     * B5-2412 — remove every FURTHER copy of the player's printed Starting
     * Ambassador, from the hand and from the draw pile, keeping the one
     * {@code keep} that {@link #findAmbassador} already seated.
     *
     * <p><b>What counts as a duplicate.</b> Rulebook :230 names ONE Starting
     * Ambassador card per race (Jeffrey Sinclair / Londo Mollari / Delenn /
     * G'kar), so the identity of the starting ambassador is the printed card,
     * not "a card flagged isAmbassador". The predicate is therefore
     * same-faction + isAmbassador + same TITLE as the seated card. Title, not
     * id, because the pool is deduped by set and the Deluxe reprint carries a
     * different id for the same card, and the same title match is the strategy
     * StarterDeckBuilder already uses to find a fixed-list card under a reprint.
     *
     * <p>The title clause is load-bearing, not defensive. Shipped data contains
     * {@code char_delenn_transformed} ("Delenn Transformed", MINBARI, RARE)
     * with {@code isAmbassador} true — it is a legal Minbari character, not the
     * :230 starting ambassador. Matching on {@code isAmbassador()} alone would
     * delete a card the rulebook never removes, so the predicate is pinned by
     * the SETUP-AMB conformance checks in both directions.
     *
     * <p><b>Where the copies go.</b> Out of the game, NOT the discard pile. The
     * rulebook has no opening discard, and {@link Deck#recycleDiscard()} shuffles
     * the discard pile back into the draw pile, so a discarded duplicate would
     * re-enter the deck later and the defect would return on a later round
     * instead of being closed at setup.
     *
     * <p><b>Why the draw pile is drained and rebuilt.</b> {@link Deck} exposes no
     * draw-pile enumeration or removal, and Deck lives in {@code model/}, outside
     * this row's engine-only claim. Draining with {@code draw(size())} and
     * rebuilding with {@code addToBottom} in the same order is exactly order
     * preserving — both operations work the same single list from opposite ends —
     * and costs one pass over a 60-card pile at setup. The conformance section
     * pins the order preservation, so a future Deck API change cannot make this
     * silently shuffle a player's deck.
     *
     * @return how many duplicate copies were removed (hand + draw pile)
     */
    private int sweepDuplicateStartingAmbassadors(Player p, CharacterCard keep) {
        if (p == null || keep == null) return 0;
        int removed = 0;

        List<Card> hand = p.getHand();
        for (int i = hand.size() - 1; i >= 0; i--) {
            Card c = hand.get(i);
            if (c != keep && isSameStartingAmbassador(c, keep)) {
                hand.remove(i);
                removed++;
            }
        }

        Deck deck = p.getDeck();
        if (deck == null) return removed;
        int before = deck.size();
        if (before == 0) return removed;
        List<Card> drained = deck.draw(before);   // top -> bottom, order preserved
        List<Card> survivors = new ArrayList<Card>(drained.size());
        for (int i = 0; i < drained.size(); i++) {
            Card c = drained.get(i);
            if (isSameStartingAmbassador(c, keep)) {
                removed++;                       // dropped: out of the game
            } else {
                survivors.add(c);
            }
        }
        for (int i = 0; i < survivors.size(); i++) deck.addToBottom(survivors.get(i));
        return removed;
    }

    /**
     * B5-2412 — is {@code c} another copy of the printed Starting Ambassador
     * card {@code seated}? Same faction, flagged ambassador, same card TITLE.
     * See {@link #sweepDuplicateStartingAmbassadors} for why title and not id,
     * and why the flag alone is not the predicate.
     */
    private static boolean isSameStartingAmbassador(Card c, CharacterCard seated) {
        if (c == null || !(c instanceof CharacterCard)) return false;
        CharacterCard ch = (CharacterCard) c;
        if (!ch.isAmbassador()) return false;
        if (ch.getLoyalty() != seated.getLoyalty()) return false;
        String seatedTitle = seated.getTitle();
        String thisTitle   = ch.getTitle();
        return seatedTitle != null && seatedTitle.equals(thisTitle);
    }

    /**
     * B5-4242 -- remove every copy of a seated starting-setup card (homeworld,
     * starting fleet, starting agenda) left in hand or draw pile, keeping the
     * copies already in play. Mirrors {@link #sweepDuplicateStartingAmbassadors}.
     *
     * <p><b>What counts as a duplicate.</b> The printed starter deck's fixed 50
     * contains the homeworld, starting fleet and starting agenda, but rulebook
     * "Preparing the Playing Field" seats those from the card pool instead, so a
     * card that begins in play also sits in the draw pile and can be drawn later
     * (the reported "Earth duplicate in hand plus in play"). The identity of a
     * setup card is its printed TITLE, matched against the same card category
     * (LocationCard / FleetCard / AgendaCard) -- the class gate stops a stray
     * different-typed card of the same title (none exists today) from being
     * mistaken for a setup card.
     *
     * <p><b>Where the copies go.</b> Out of the game, NOT the discard pile, for
     * the same reason as the ambassador sweep: rulebook has no opening discard and
     * {@link Deck#recycleDiscard()} would re-enter the removed card later.
     *
     * @return how many duplicate setup cards were removed (hand + draw pile)
     */
    private int sweepDuplicateSetupCards(Player p, List<Card> keep) {
        if (p == null || keep == null || keep.isEmpty()) return 0;
        int removed = 0;

        List<Card> hand = p.getHand();
        for (int i = hand.size() - 1; i >= 0; i--) {
            Card c = hand.get(i);
            if (isSameSetupCard(c, keep)) {
                hand.remove(i);
                removed++;
            }
        }

        Deck deck = p.getDeck();
        if (deck == null) return removed;
        int before = deck.size();
        if (before == 0) return removed;
        List<Card> drained = deck.draw(before);   // top -> bottom, order preserved
        List<Card> survivors = new ArrayList<Card>(drained.size());
        for (int i = 0; i < drained.size(); i++) {
            Card c = drained.get(i);
            if (isSameSetupCard(c, keep)) {
                removed++;                       // dropped: out of the game
            } else {
                survivors.add(c);
            }
        }
        for (int i = 0; i < survivors.size(); i++) deck.addToBottom(survivors.get(i));
        return removed;
    }

    /**
     * B5-4242 -- is {@code c} another copy of one of the in-play setup cards
     * {@code keep}? Same card category (class) and same TITLE. See
     * {@link #sweepDuplicateSetupCards} for the predicate rationale.
     */
    private static boolean isSameSetupCard(Card c, List<Card> keep) {
        if (c == null || keep == null) return false;
        String cTitle = c.getTitle();
        if (cTitle == null) return false;
        Class<?> cClass = c.getClass();
        for (int i = 0; i < keep.size(); i++) {
            Card k = keep.get(i);
            if (k == null) continue;
            if (cClass != k.getClass()) continue;
            String kTitle = k.getTitle();
            if (kTitle != null && kTitle.equals(cTitle)) return true;
        }
        return false;
    }

    private void runActionPhase() {
        int passCount = 0;
        int playerCount = state.getPlayers().size();
        int totalActions = 0;
        // B5-2275: a round counts as a FULL-TABLE pass only when every action
        // taken in it was a PASS. One real action anywhere makes the round a
        // non-pass, so the stall counter cannot be driven by one busy player
        // while the rest of the table idles.
        boolean sawNonPassAction = false;
        // B5-0372 (D6): initiative-cycle loop — each player acts once per cycle;
        // the round ends only when all players pass consecutively. No per-player
        // action cap (the loop itself delivers one action per eligibility visit).
        // Safety cap (non-rulebook, liveness only): 8 × playerCount.
        final int MAX_ACTIONS_PER_ROUND = playerCount * 8;

        while (passCount < playerCount && !state.isGameOver()) {
            if (totalActions >= MAX_ACTIONS_PER_ROUND) {
                state.log("Action round safety cap reached (" + MAX_ACTIONS_PER_ROUND
                          + " actions); ending round.");
                break;
            }
            Player current = state.getActivePlayer();
            GameAction action;

            if (current.isHuman()) {
                action = waitForHumanAction();
            } else {
                AIPlayer ai = getAI(current);
                action = ai.chooseAction(state, current);
                pause(600);
            }

            processAction(current, action);
            notifyUI();
            if (action.getType() == GameAction.Type.PLAY_CONTINGENCY) {
                runContingencyRevealWindow();
            }

            if (action.getType() == GameAction.Type.PASS) {
                current.setPassed(true);
                passCount++;
            } else {
                current.useAction();   // B5-4170: consume the action slot — was
                                       // never called, so actionsLeft stayed at 1
                                       // forever and every player acted every
                                       // visit, never passing, round never ending.
                sawNonPassAction = true;      // B5-2275
                current.setPassed(false);   // B5-0372: un-pass — a passer may act later (rulebook §V)
                passCount = 0;
            }

            totalActions++;

            Player winner = rules.checkVictory(state);
            if (winner != null) {
                state.setWinner(winner);
                notifyUI();
                lastRoundWasFullTablePass = false;   // B5-2275: game decided, not a stall
                return;
            }

            // B5-4170: endOfTurn enforced a 7-card hand limit after EVERY action
            // in the round. That is wrong — the limit is checked once, in the
            // AFTERMATH phase (RulesEngine.discardDownTo). Calling it here makes
            // cards vanish from a player's hand mid-round with no play, which is
            // what the playtester reported. The method is left in place but no
            // longer called from the action loop.
            state.advanceTurn();
        }
        // B5-2275: a round that took no action at all is a full-table pass. The
        // safety-cap break also lands here; it counts as a pass only if every
        // action it did take was a PASS, which is the honest reading.
        lastRoundWasFullTablePass = !sawNonPassAction;
        rules.finishActionRound(state);

        // B5-4167: notify round-end callback (popup + log summary)
        if (roundEndCallback != null) {
            roundEndCallback.onRoundEnd(state, state.getRoundNumber(), state.getPlayers());
        }
    }
    /** B5-4163: the rulebook III :320-:328 conflict round — one round between
     *  startRound and the action round. Declare one conflict per active
     *  faction in initiative order, reveal all face-down conflicts, then
     *  initiate in declaration order. Set GamePhase.CONFLICT on entry; the
     *  declarations are QUEUED (B5-4160), never resolved here — the Resolution
     *  round via resolveQueuedConflicts owns that, after the action round.
     */
    private void runConflictRound() {
        // GamePhase.CONFLICT is set here and only here: this is the production
        // path that revives every MainWindow CONFLICT branch that read the
        // phase while nothing ever set it (B5-4163 root cause).
        state.setPhase(GamePhase.CONFLICT);
        state.log("=== CONFLICT phase (declare, reveal/initiate) ===");
        notifyUI();

        // ── C1 Declare (face-down) + C2 Reveal & initiate (declaration order) ─
        // A declaration is held, not queued, until every faction has had its
        // say, so several factions can declare in one round (rulebook
        // III :366-:374). Both the AI offer gate and MainWindow's Initiate
        // button require no conflict yet in play, which keeps the app's
        // one-conflict-in-play shape without an explicit cap here.
        List<Player> order = rules.getInitiativeOrder();
        List<Player> declarers = new ArrayList<Player>();
        List<GameAction> declarations = new ArrayList<GameAction>();

        for (Player p : order) {
            if (state.isGameOver()) break;
            if (!state.isPlayerActive(p)) continue;
            if (state.hasInitiatedConflictThisTurn(p)) continue;   // B5-0302
            if (p.getActionsLeft() <= 0) continue;

            if (p.isHuman()) {
                // B5-4163: the human's own declaration window. It reuses the
                // action-round wait flag because MainWindow gates its CONFLICT
                // Initiate/Pass controls on isWaitingForHuman() plus
                // GamePhase.CONFLICT, so restoring the round needs no new UI
                // branch. canInitiateConflict is re-checked when it is applied.
                GameAction hd = waitForHumanConflictDeclaration();
                if (hd != null && (hd.getType() == GameAction.Type.INITIATE_CONFLICT
                        || hd.getType() == GameAction.Type.DECLARE_WAR_CONFLICT)) {
                    declarers.add(p);
                    declarations.add(hd);
                }
                continue;
            }

            if (aiPlayers.isEmpty()) continue;
            GameAction decl = getAI(p).chooseDeclaration(state, p);
            if (decl != null && (decl.getType() == GameAction.Type.INITIATE_CONFLICT
                    || decl.getType() == GameAction.Type.DECLARE_WAR_CONFLICT)) {
                declarers.add(p);
                declarations.add(decl);
            }
        }

        // ── C2 Reveal & initiate, in declaration order ───────────────────────
        // processAction queues the conflict and publishes it as the active
        // conflict for the action round's joins/attacks. The queue is
        // deliberately NOT cleared here: that is B5-4160's contract, and
        // resolveQueuedConflicts drains it after the action round.
        for (int i = 0; i < declarations.size(); i++) {
            if (state.isGameOver()) break;
            processAction(declarers.get(i), declarations.get(i));
        }
    }

    // ── B5-2275: stall resolution ─────────────────────────────────────────────

    /**
     * Ends a game that has stalled: the whole table passed in
     * MAX_CONSECUTIVE_FULL_PASS_ROUNDS consecutive rounds.
     *
     * <p>Deliberately NOT a victory rule. The rulebook has no provision for
     * "nobody could act", and D12 crowns a player only on a strictly-greatest
     * Influence Rating, so a 20-20 table has no rulebook winner. This method
     * therefore does two things and no more:
     *
     * <ol>
     *   <li>if exactly one ACTIVE player is strictly greatest in Influence
     *       Rating, that player is crowned as the stall winner and the log
     *       says so explicitly — it is a stall crown, not a Standard Victory,
     *       and it is deliberately reachable at any rating;</li>
     *   <li>otherwise the tie is reported loudly, per player, in the log, and
     *       NO winner is set: {@link GameState#isGameOver()} stays false and
     *       the run simply stops. Nothing is invented to break the tie.</li>
     * </ol>
     *
     * <p>Forfeited and surrendered players are excluded from both the crown
     * and the tie report, matching {@link GameState#isPlayerActive(Player)}
     * and every path in {@link RulesEngine#checkVictory(GameState)}.
     */
    private void resolveStall() {
        List<Player> active = new ArrayList<Player>();
        for (Player p : state.getPlayers()) {
            if (state.isPlayerActive(p)) active.add(p);
        }

        if (active.isEmpty()) {
            // Nothing active to crown and nothing to report. Say so rather
            // than looping on a table that no longer exists.
            state.log("STALL: " + consecutiveFullPassRounds
                    + " consecutive all-pass rounds and no active players remain.");
            stallTieEnding = true;
            notifyUI();
            return;
        }

        int best = Integer.MIN_VALUE;
        for (Player p : active) {
            if (p.getInfluence() > best) best = p.getInfluence();
        }
        List<Player> leaders = new ArrayList<Player>();
        for (Player p : active) {
            if (p.getInfluence() == best) leaders.add(p);
        }

        if (leaders.size() == 1) {
            Player leader = leaders.get(0);
            state.log("STALL after " + consecutiveFullPassRounds
                    + " consecutive all-pass rounds: " + leader.getName()
                    + " is the sole active leader at Influence Rating " + best
                    + " and is crowned on the stall guard (not a Standard Victory"
                    + " — the rulebook provides no winner for an all-pass table).");
            state.setWinner(leader);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("STALL TIE: ").append(consecutiveFullPassRounds)
              .append(" consecutive all-pass rounds and ").append(leaders.size())
              .append(" players tied at Influence Rating ").append(best)
              .append(" — NO WINNER DECLARED: ");
            for (int i = 0; i < active.size(); i++) {
                if (i > 0) sb.append(", ");
                Player p = active.get(i);
                sb.append(p.getName()).append(" ").append(p.getInfluence());
            }
            sb.append(". D12 crowns a strictly-greatest leader only, so the"
                    + " rulebook has no winner for this position and none is"
                    + " invented; the game ends here as a reported tie.");
            state.log(sb.toString());
            stallTieEnding = true;
        }
        notifyUI();
    }

    /** B5-2275: consecutive all-pass rounds counted so far. */
    public int  getConsecutiveFullPassRounds()    { return consecutiveFullPassRounds; }

    /** B5-2275: true when the game ended on a REPORTED TIE, winner uncrowned. */
    public boolean isStalledOnTie()              { return stallTieEnding; }

    private void processAction(Player p, GameAction action) {
        state.log(p.getName() + ": " + action);

        switch (action.getType()) {
            case PASS:
                break;

            case INITIATE_CONFLICT:
                if (action.getCard() instanceof ConflictCard) {
                    ConflictCard cc = (ConflictCard) action.getCard();
                    Player target = action.getTarget();
                    // B5-0336: the 4-arg check enforces requiresTarget
                    // (Border Raid etc.); the conflict carries the declared
                    // target so the participation "players" gate can apply.
                    if (!rules.canInitiateConflict(p, cc, target, state)) {
                        boolean needsTarget = cc.getParticipation() != null
                                && cc.getParticipation().isRequiresTarget()
                                && target == null;
                        state.log(p.getName() + " cannot initiate " + cc.getTitle()
                                  + (needsTarget
                                     ? " — a target must be declared (participation)."
                                     : " — one conflict per turn."));
                        break;
                    }
                    p.removeFromHand(cc);
                    state.markConflictInitiated(p);
                    Conflict conflict = new Conflict(cc, p, target);
                    if (target != null) {
                        state.log(p.getName() + " targets " + target.getName()
                                  + " with " + cc.getTitle() + ".");
                    }
                    // B5-4163/B5-4160: the conflict is QUEUED for the
                    // Resolution round, not resolved inline. The initiator is
                    // seated on the support side by the Conflict constructor
                    // with NO cards committed (B5-0309's ambassador
                    // auto-support is deliberately not applied here — B5-4160
                    // join-timing probe S2), and no participation is opened
                    // (probe S3: AI auto-join is gone). Joins/attacks stay
                    // action-round acts against the active conflict
                    // (B5-4157/B5-4161); S1-S3 run in queue order in
                    // resolveQueuedConflicts after the action round closes.
                    state.setActiveConflict(conflict);
                    state.queueConflict(conflict);
                }
                break;

            case PLAY_CARD:
                applyGenericCardPlay(p, action.getCard(), action.isHidden());
                break;

            case PLAY_CONTINGENCY:
                if (action.getCard() instanceof ContingencyCard) {
                    ContingencyCard contingency = (ContingencyCard) action.getCard();
                    Card host = action.getTargetCard();
                    if (rules.canPlayContingency(p, contingency, host)
                            && contingency.placeUnder(p, host)) {
                        p.removeFromHand(contingency);
                        state.log(p.getName() + " places a face-down contingency under "
                                + host.getTitle() + ".");
                    } else {
                        state.log(p.getName() + " cannot place that contingency.");
                    }
                }
                break;

            case REVEAL_CONTINGENCY:
                if (action.getCard() instanceof ContingencyCard) {
                    ContingencyCard contingency = (ContingencyCard) action.getCard();
                    if (rules.canRevealContingency(p, contingency)) {
                        CardEffects.revealContingency(state, p, contingency);
                    } else {
                        state.log(p.getName() + " cannot reveal that contingency.");
                    }
                }
                break;

            case RECRUIT_CHARACTER:
                if (action.getCard() instanceof CharacterCard) {
                    CharacterCard ch = (CharacterCard) action.getCard();
                    // B5-0323: the faction must apply the card's influence
                    // cost (rulebook §Sponsor) — free cards (cost 0, the
                    // current data) behave exactly as before.
                    if (rules.canRecruit(p, ch)) {
                        // B5-0339: the effective cost carries the assistant's
                        // sponsor discount; consume exactly what was applied.
                        int baseCost = rules.baseRecruitCost(p, ch);
                        SponsorCost sponsorCost = rules.sponsorCost(p, ch);
                        int cost = sponsorCost.getAmount();
                        if (!p.applyInfluence(cost)) {
                            state.log(p.getName() + " cannot apply enough influence to sponsor "
                                    + ch.getTitle() + ".");
                            break;
                        }
                        if (!sponsorCost.isWaived() && cost < baseCost) {
                            p.consumeSponsorDiscount(baseCost - cost);
                            state.log(p.getName() + " sponsors at an assistant discount ("
                                      + cost + " instead of " + baseCost + ").");
                        }
                        p.removeFromHand(ch);
                        p.placeInSupportingRole(ch);
                        state.log(p.getName() + " recruits " + ch.getTitle());
                    } else {
                        state.log(p.getName() + " cannot recruit " + ch.getTitle()
                                  + " — influence cost not affordable.");
                    }
                }
                break;

            case BUILD_INFLUENCE:
                if (action.getCard() instanceof CharacterCard) {
                    CharacterCard leader2 = (CharacterCard) action.getCard();
                    rules.executeBuildInfluence(p, leader2, state);
                }
                break;

            case PROMOTE_CHARACTER: // B5-0321
                if (action.getCard() instanceof CharacterCard) {
                    CharacterCard ch = (CharacterCard) action.getCard();
                    rules.executePromote(p, ch, action.getLeader(), state);
                }
                break;

            case SPONSOR_CARD: // B5-2245 (rulebook :657)
                // The card types RECRUIT_CHARACTER does not name. executeSponsorCard
                // owns the whole payment (refuse when short, deduct on success) and
                // the rotation, so this branch stays a dispatch and does not
                // re-derive the cost the way the RECRUIT_CHARACTER branch above
                // must for its assistant-discount bookkeeping.
                if (action.getCard() instanceof EnhancementCard
                        || action.getCard() instanceof LocationCard
                        || action.getCard() instanceof FleetCard) {
                    rules.executeSponsorCard(p, action.getCard(),
                            action.getLeader(), state);
                }
                break;

            case LEAD_FLEET: // B5-0362 (B5-0345 Tier-1 #1): one action per
                // (leader, fleet) pair — the central p.useAction() below
                // consumes it exactly like every other action branch.
                if (action.getCard() instanceof FleetCard && action.getLeader() != null) {
                    FleetCard leadFl = (FleetCard) action.getCard();
                    if (rules.canLeadFleet(p, action.getLeader(), leadFl)) {
                        rules.executeLeadFleet(p, action.getLeader(), leadFl, state);
                    } else {
                        state.log(p.getName() + " cannot lead "
                                + leadFl.getTitle() + " with "
                                + action.getLeader().getTitle() + ".");
                    }
                }
                break;

            case USE_ROTATE_EFFECT: // B5-0366 (B5-0345 Tier-2 #5): one action
                // per (assistant, effect-kind) pair — the central
                // p.useAction() below consumes it exactly like every other
                // branch. The pair rides the existing (card, leader) fields
                // (card = rotating assistant, leader = ambassador); the kind
                // rides getRotateKind. Effect payloads compose with the
                // B5-0357 bonus layer through the B5-0339 executors (flag +
                // discount are registry-compatible computed reads, never
                // field mutations) — no CardEffects call sites touched.
                if (action.getCard() instanceof CharacterCard
                        && action.getLeader() != null) {
                    CharacterCard rotCh  = (CharacterCard) action.getCard();
                    CharacterCard rotAmb = action.getLeader();
                    GameAction.RotateEffectKind kind = action.getRotateKind();
                    if (rules.canUseRotateEffect(p, rotCh, rotAmb, kind)) {
                        rules.executeRotateEffect(p, rotCh, kind, state);
                    } else {
                        state.log(p.getName() + " cannot use "
                                + (kind == null ? "(unknown rotate effect)"
                                               : kind.toString())
                                + " with " + rotCh.getTitle() + ".");
                    }
                }
                break;

            // ── Agenda lifecycle (B5-0364; rulebook :520/:719) ───────────────

            case DISCARD_AGENDA:
                if (action.getCard() instanceof AgendaCard
                        && p.getAgenda() == action.getCard()
                        && rules.canDiscardAgenda(p)) {
                    AgendaCard gone = (AgendaCard) action.getCard();
                    // B5-1995: remove mark grants from the discarded agenda
                    CardEffects.removeAgendaMarkGrants(state, p, gone);
                    // B5-2251: also lift the WHILE_IN_PLAY stat bonuses it
                    // granted. A bonus has no round boundary to expire on, so
                    // without this the agenda's +1 Military outlived the agenda.
                    int lifted = CardEffects.removeAgendaBonuses(p, gone);
                    if (lifted > 0) {
                        state.log(p.getName() + ": " + gone.getTitle()
                                + " stops granting " + lifted + " bonus(es).");
                    }
                    p.setAgenda(null);
                    p.getDeck().discard(gone);
                    state.log(p.getName() + " discards agenda " + gone.getTitle() + ".");
                } else {
                    state.log(p.getName() + " cannot discard that agenda"
                            + (action.getCard() instanceof AgendaCard
                                    && ((AgendaCard) action.getCard()).isMajorAgenda()
                                ? " — Major agendas cannot be discarded (:719)."
                                : "."));
                }
                break;

            case REPLACE_AGENDA:
                if (action.getCard() instanceof AgendaCard
                        && rules.canReplaceAgenda(p, (AgendaCard) action.getCard(),
                                                  action.getLeader())) {
                    AgendaCard oldAg = p.getAgenda();
                    AgendaCard newAg = (AgendaCard) action.getCard();
                    action.getLeader().rotate();
                    p.removeFromHand(newAg);
                    // B5-1995: remove mark grants from the old agenda
                    if (oldAg != null) {
                        CardEffects.removeAgendaMarkGrants(state, p, oldAg);
                        // B5-2251: and lift its WHILE_IN_PLAY stat bonuses, or
                        // the outgoing agenda's +1 Military keeps being paid
                        // out by the fleets after it has left play.
                        CardEffects.removeAgendaBonuses(p, oldAg);
                    }
                    p.setAgenda(newAg);
                    // :719 — the old agenda is REMOVED FROM THE GAME (not discarded).
                    state.log(p.getName() + " replaces " + oldAg.getTitle()
                            + " with " + newAg.getTitle()
                            + " (" + action.getLeader().getTitle() + " rotates).");
                    // B5-1995 / B5-2251: a replacement agenda is an agenda coming
                    // into play, so it runs the full on-play path — the same
                    // stat bonuses a sponsored one gets, plus its mark grants.
                    // Calling this path called applyAgendaMarkGrants ALONE, so a
                    // fleet-wide +1 Military agenda installed by REPLACE was
                    // live in the agenda slot and inert on the fleets. It is
                    // called instead of, not after, the mark-grant call:
                    // applyAgendaOnPlay invokes applyAgendaMarkGrants itself and
                    // marks ACCUMULATE, so running both would double them.
                    CardEffects.applyAgendaOnPlay(state, p, newAg);
                } else {
                    AgendaCard cur = p.getAgenda();
                    state.log(p.getName() + " cannot replace that agenda"
                            + (cur != null && cur.isMajorAgenda()
                                    && action.getCard() instanceof AgendaCard
                                    && !((AgendaCard) action.getCard()).isMajorAgenda()
                                ? " — Major agendas may only be replaced by another Major."
                                : "."));
                }
                break;

            case REVEAL_AGENDA:
                if (action.getCard() instanceof AgendaCard
                        && p.getAgenda() == action.getCard()
                        && rules.canRevealAgenda(p)) {
                    AgendaCard hid = (AgendaCard) action.getCard();
                    // :719 — if it is one you could not sponsor at that time,
                    // it is discarded instead of being revealed.
                    if (!hid.getLoyalty().isPlayableBy(p.getRace())) {
                        // Hidden agenda had no effect (was face-down), so no marks to remove
                        p.setAgenda(null);
                        p.getDeck().discard(hid);
                        state.log(p.getName() + "'s hidden agenda could not be "
                                + "sponsored by that faction — discarded instead.");
                    } else {
                        hid.setFaceDown(false);
                        state.log(p.getName() + " reveals hidden agenda "
                                + hid.getTitle() + " — it takes effect immediately.");
                        CardEffects.applyAgendaOnPlay(state, p, hid);
                    }
                } else {
                    state.log(p.getName() + " has no hidden agenda to reveal.");
                }
                break;

            case JOIN_CONFLICT_SUPPORT:
                if (state.getActiveConflict() != null) {
                    if (rules.canJoinConflict(p, state.getActiveConflict())) {
                        rules.executeJoinConflict(p, state.getActiveConflict(), true, state, true);
                    } else {
                        state.log(p.getName() + " cannot support — join not allowed.");
                    }
                } else {
                    state.log("No active conflict to join.");
                }
                break;

            case JOIN_CONFLICT_OPPOSE:
                if (state.getActiveConflict() != null) {
                    if (rules.canJoinConflict(p, state.getActiveConflict())) {
                        rules.executeJoinConflict(p, state.getActiveConflict(), false, state, true);
                    } else {
                        state.log(p.getName() + " cannot oppose — join not allowed.");
                    }
                } else {
                    state.log("No active conflict to join.");
                }
                break;

            case ATTACK_CONFLICT_PARTICIPANT: // B5-0370
                if (state.getActiveConflict() != null
                        && rules.executeAttackConflictParticipant(p, action.getCard(),
                                action.getTargetCard(), state.getActiveConflict(), state)) {
                    // A legal attack rotates its card and adds it to the current
                    // conflict; the shared action spend below consumes one action.
                } else {
                    state.log(p.getName() + " cannot attack that conflict participant.");
                }
                break;

            case HEAL_CHARACTER: // B5-0371
                if (!(action.getCard() instanceof CharacterCard)
                        || !rules.executeHealCharacter(p, (CharacterCard) action.getCard(), state)) {
                    state.log(p.getName() + " cannot heal that character.");
                }
                break;

            case REPAIR_CARD: // B5-0371
                if (!rules.executeRepairCard(p, action.getCard(), state)) {
                    state.log(p.getName() + " cannot repair that fleet or location.");
                }
                break;

            // ── B5-0661: Unconditional Surrender (rulebook :815–:821) ─────────
            case SURRENDER: // B5-0661
                if (action.getTarget() != null
                        && rules.canSurrender(p, action.getTarget(), state)) {
                    rules.executeSurrender(p, action.getTarget(), state);
                } else {
                    state.log(p.getName() + " cannot surrender"
                            + (action.getTarget() == null
                                ? " — no valid target."
                                : " — not at war, or target is invalid."));
                }
                break;

            // ── B5-1979: voluntary forfeit ─────────────────────────────────────
            case FORFEIT:
                if (rules.canForfeit(p, state)) {
                    // Immediate loss: executeForfeit marks the player forfeited
                    // and, when that leaves a sole survivor, awards that
                    // survivor the game. The game-over transition and the UI
                    // wake-up are done HERE rather than left to the
                    // end-of-action checkVictory at GameController.java:167,
                    // because that check runs only after the turn advances --
                    // one more action would have been processed for a player
                    // who has already lost. Returning here stops the action
                    // round immediately, which is the "immediate loss" the row
                    // asks for.
                    Player forfeitWinner = rules.executeForfeit(p, state);
                    if (forfeitWinner != null) {
                        state.setWinner(forfeitWinner);
                        notifyUI();
                        return;
                    }
                } else {
                    state.log(p.getName() + " cannot forfeit right now.");
                }
                break;

            // ── B5-0376: war conflict declaration ──────────────────────────────
            case DECLARE_WAR_CONFLICT: {
                WarKind kind = null;
                Player raceTarget = action.getTarget();
                LocationCard locTarget = (LocationCard) action.getTargetCard();
                if (raceTarget != null) {
                    kind = WarKind.RACE_TARGET;
                } else if (locTarget != null) {
                    kind = WarKind.LOCATION_TARGET;
                }
                if (kind == null) {
                    state.log(p.getName() + " declares an invalid war conflict (no target).");
                    break;
                }
                Conflict conflict = rules.declareWarConflict(p, kind, raceTarget, locTarget, state);
                if (conflict == null) {
                    state.log(p.getName() + " cannot declare a war conflict (not at war, or target not at war with p).");
                    break;
                }
                state.markWarConflictInitiated(p);
                state.markConflictInitiated(p);
                // B5-4170: war conflicts queue for the Resolution round too
                // (same S1-S3 shape; no conflict card to discard in S3).
                state.setActiveConflict(conflict);
                state.queueConflict(conflict);
                break;
            }

            // ── B5-0395: mercenary control bids ──────────────────────────────
            case BID_ON_MERCENARY:
                if (action.getCard() != null
                        && rules.canBidOnMercenary(p, action.getCard(),
                                                   action.getAmount(), state)) {
                    rules.executeBidOnMercenary(p, action.getCard(),
                                                action.getAmount(), state);
                } else {
                    state.log(p.getName() + " cannot bid "
                            + action.getAmount() + " on that mercenary"
                            + (action.getCard() == null
                                ? "."
                                : (action.getCard().isMercenary()
                                    ? " — not affordable or not in play."
                                    : " — " + action.getCard().getTitle()
                                        + " is not a mercenary.")));
                }
                break;

            default:
                break;
        }
    }

    /**
     * B5-2243: a contingency placement opens the rulebook's timing-dispute
     * window. Players get one reveal-or-pass opportunity in initiative order;
     * the window closes after the whole table passes consecutively. A reveal
     * is a reaction to the placement, not a second action for the player.
     */
    private void runContingencyRevealWindow() {
        List<Player> players = state.getPlayers();
        if (players.isEmpty() || !hasRevealableContingency(players)) return;

        Player active = state.getActivePlayer();
        int start = players.indexOf(active);
        if (start < 0) start = 0;
        int passes = 0;
        int visits = 0;
        while (passes < players.size() && !state.isGameOver()) {
            Player current = players.get((start + visits) % players.size());
            visits++;
            if (!state.isPlayerActive(current)) {
                passes++;
                continue;
            }

            GameAction response = GameAction.pass();
            if (hasRevealableContingency(current)) {
                if (current.isHuman()) {
                    response = waitForHumanAction();
                } else {
                    response = firstRevealAction(current);
                }
            }

            if (response != null
                    && response.getType() == GameAction.Type.REVEAL_CONTINGENCY
                    && response.getCard() instanceof ContingencyCard
                    && rules.canRevealContingency(current,
                                                  (ContingencyCard) response.getCard())) {
                processAction(current, response);
                passes = 0;
            } else {
                passes++;
            }
            notifyUI();
        }
        state.log("Contingency reveal window closed after " + passes
                + " consecutive passes.");
    }

    private boolean hasRevealableContingency(List<Player> players) {
        for (Player p : players) {
            if (hasRevealableContingency(p)) return true;
        }
        return false;
    }

    private boolean hasRevealableContingency(Player p) {
        return firstRevealAction(p) != null;
    }

    private GameAction firstRevealAction(Player p) {
        for (Card host : inPlayCards(p)) {
            for (Card placed : host.getContingencies()) {
                if (placed instanceof ContingencyCard
                        && rules.canRevealContingency(p, (ContingencyCard) placed)) {
                    return GameAction.revealContingency((ContingencyCard) placed);
                }
            }
        }
        return null;
    }

    private List<Card> inPlayCards(Player p) {
        List<Card> cards = new ArrayList<Card>();
        if (p.getAmbassador() != null) cards.add(p.getAmbassador());
        cards.addAll(p.getInnerCircle());
        cards.addAll(p.getSupportingRole());
        cards.addAll(p.getFleets());
        cards.addAll(p.getLocations());
        cards.addAll(p.getGroups());
        cards.addAll(p.getEnhancements());
        if (p.getAgenda() != null) cards.add(p.getAgenda());
        return cards;
    }

    /** B5-0395: the Mercenary phase — after ACTION closes, control of each
     *  offered mercenary is decided from the cumulative bids; ties crown
     *  nobody, and a controller's mercenary acts immediately through the
     *  id-keyed CardEffects table. No-ops cleanly when no mercenaries are on
     *  the table (the pool carries none today — B5-0386).
     *
     *  B5-2281: the resolution now belongs to
     *  {@link RulesEngine#resolveMercenaryControl}, which breaks a tie in
     *  rulebook :352 initiative order and logs the tie loudly instead of
     *  crowning nobody. GameState.resolveMercenaries() (model scope, the
     *  B5-0395 strict reading) is no longer on this path; the divergence is
     *  pinned by the MER-TIE conformance witness so the two can never be
     *  mistaken for interchangeable authorities. */
    private void runMercenaryPhase() {
        if (state.getMercenaryOffers().isEmpty()) return;
        state.setPhase(GamePhase.MERCENARY);
        notifyUI();
        Map<Card, Player> controllers = rules.resolveMercenaryControl(state);
        for (Map.Entry<Card, Player> entry : controllers.entrySet()) {
            if (state.isGameOver()) break;
            Card merc = entry.getKey();
            Player ctl = entry.getValue();
            state.log(ctl.getName() + " controls mercenary " + merc.getTitle()
                    + " (" + state.getMercenaryBid(merc, ctl) + " influence).");
            CardEffects.applyMercenaryAction(state, ctl, merc);
            notifyUI();
        }
    }

    // B5-2420: package-private, not private, so HeadlessConformanceTest can drive
    // the REAL aftermath path (B5-0338 join loop -> rules.resolveConflict ->
    // Step 2 play -> Step 3 flush). The AMT4 section exercises the queue and flush
    // helpers directly, so before this widening no check covered the production
    // wiring that actually calls them: a helper could be perfect while the call
    // sites were wrong, or removed. Same package, same module, no new API.
    /**
     * B5-4170: the action-round participation step, run at initiation — AI
     * side choice, the human join window, mandatory-participation enforcement
     * and the human attack window all happen HERE, in the action round
     * (rulebook: Support/Oppose/Attack are action-round acts; B5-4157/
     * B5-4161). Resolution itself is deferred to the Resolution round
     * (resolveQueuedConflicts -> resolveQueuedConflict, B5-4170).
     */
    void openConflictParticipation() {
        Conflict conflict = state.getActiveConflict();
        if (conflict == null) return;

        for (Player p : state.getPlayers()) {
            if (p == conflict.getInitiator()) continue;
            if (p.isHuman()) continue;
            AIPlayer ai = getAI(p);
            // B5-0343: the AI picks its side (+1 support, -1 oppose, 0 abstain)
            // by conflict outcome — the controller no longer hardcodes the
            // always-oppose default the B5-0310 audit flagged.
            int side = ai.decideJoinSide(state, p, conflict);
            if (side != 0) {
                boolean support = side > 0;
                // B5-0336: addParticipant is the participation gate — a
                // refusal means the conflict's restriction rejects this
                // joiner (logged loudly at the refusal site).
                if (conflict.addParticipant(p, support)) {                // B5-0309: joiners pick a side
                    if (p.getAmbassador() != null) {
                        conflict.commitCard(p, p.getAmbassador(), support);   // B5-0309
                    }
                }
            }
        }

        // B5-0363: the human receives a real decision window after the AI
        // seats have chosen and before mandatory participation/resolution.
        // Conflicts run synchronously on the controller thread; this wait
        // releases the monitor so the Swing UI can submit the existing
        // JOIN_CONFLICT_SUPPORT / JOIN_CONFLICT_OPPOSE action.
        Player human = state.getHumanPlayer();
        if (uiCallback != null && human.isHuman() && state.isPlayerActive(human)
                && human != conflict.getInitiator()
                && rules.canJoinConflict(human, conflict, false)
                && conflict.canJoinConflict(human)) {
            state.log(human.getName() + " may choose a side in the active conflict.");
            GameAction join = waitForHumanConflictJoin();
            if (join != null && join.getType() == GameAction.Type.JOIN_CONFLICT_SUPPORT) {
                rules.executeJoinConflict(human, conflict, true, state, false);
            } else if (join != null
                    && join.getType() == GameAction.Type.JOIN_CONFLICT_OPPOSE) {
                rules.executeJoinConflict(human, conflict, false, state, false);
            }
        }

        // B5-0336: enforce mandatory participation dimensions (mustTakeSide,
        // allPlayersMustCommit, mustCommitAmbassador) before resolution.
        rules.enforceMandatoryParticipation(conflict, state);

        if (uiCallback != null && human.isHuman() && !human.hasForfeited()
                && hasLegalHumanConflictAttack(human, conflict)) {
            state.log(human.getName() + " may attack a participant in the active conflict.");
            GameAction attack = waitForHumanConflictAttack();
            if (attack != null
                    && attack.getType() == GameAction.Type.ATTACK_CONFLICT_PARTICIPANT) {
                processAction(human, attack);
            }
        }

    }

    /**
     * B5-2420 test hook (NOT called by production since B5-4170): drive the
     * full B5-0338 join loop -> S1 resolution -> S2 aftermath -> S3 flush
     * through one call — the shape HeadlessConformanceTest AMT6 and the
     * reflection probes drive. The handlers now run openConflictParticipation()
     * in the action round and resolveQueuedConflicts() resolves the queue after it;
     * this facade exists so those tests keep exercising the real wiring, not a
     * helper in isolation.
     */
    void resolveCurrentConflict() {
        openConflictParticipation();
        Conflict conflict = state.getActiveConflict();
        if (conflict == null) return;
        resolveQueuedConflict(conflict);
    }

    /**
     * B5-4163/B5-4170 (report §8; rulebook III Resolution round): resolves every
     * conflict queued during the CONFLICT round, in initiation (queue) order.
     * S1-S3 for each conflict run inside resolveQueuedConflict; the round
     * owns the phase transition and the queue teardown. An empty queue is a
     * no-op, so rounds without conflicts keep their exact old behaviour.
     * The B5-4160 join-timing probe reflects this method by this exact name.
     */
    private void resolveQueuedConflicts() {
        state.log("=== RESOLUTION phase (resolve → aftermath window → discard) ===");
        if (state.getQueuedConflicts().isEmpty()) return;
        state.setPhase(GamePhase.CONFLICT_RESOLUTION);
        notifyUI();
        for (Conflict conflict
                : new ArrayList<Conflict>(state.getQueuedConflicts())) {
            resolveQueuedConflict(conflict);
            if (state.isGameOver()) break;
        }
        state.clearQueuedConflicts();
        state.clearActiveConflict();
    }

    /**
     * B5-4170: S1-S3 for ONE queued conflict, in the Resolution round.
     * S1 rules.resolveConflict crowns from the totals as they are NOW
     * (damage drift, neutralized-zero and bonuses count at resolution, not
     * commit); S2 announces the aftermath window — outcome, conflict type
     * and Participant scope — BEFORE any attachment, then runs the window;
     * S3 flushes the aftermath discards and discards the conflict card
     * (rulebook :440).
     */
    private void resolveQueuedConflict(Conflict conflict) {
        // B5-4172: the join window (Support/Oppose/Attack) MUST run before
        // resolution so participants can commit cards. The test hook
        // resolveCurrentConflict() did this, but the production path in
        // resolveQueuedConflicts() skipped it, so the human never got a join
        // window and conflicts resolved with only the initiator committed.
        openConflictParticipation();
        if (state.isGameOver()) return;
        Player winner = rules.resolveConflict(conflict, state);

        // B5-1923: card-specific loser outcome moved into
        // RulesEngine.resolveConflict itself (same primary-loser selection,
        // same position after war outcome): it was UI-only before, so every
        // headless resolution silently skipped it.
        //
        // B5-2251: the agenda Diplomacy-win bonus moved there for the same
        // reason and is now applied inside resolveConflict. Applying it here
        // as well would pay the winner twice on the UI path.

        state.setPhase(GamePhase.AFTERMATH);
        notifyUI();

        boolean initiatorWon = rules.initiatorWon(conflict, winner);

        // B5-4162 extension (B5-4170): the aftermath window is ANNOUNCED
        // before any attachment, naming the outcome, the conflict type and
        // the Participant scope (rulebook :1210 / Aftermath Cards -
        // Participant: a Participant aftermath may target any faction that
        // Supported, Opposed or Attacked — the scope widens the moment the
        // conflict has a non-initiator participant).
        String conflictTitle = conflict.getCard() != null
                ? conflict.getCard().getTitle() : "War Conflict";
        state.log("Aftermath window: " + conflictTitle + " — "
                + conflict.getInitiator().getName()
                + (initiatorWon ? " Won" : " Lost")
                + " (" + conflict.getConflictType() + "); Participant aftermaths "
                + (conflict.getParticipants().size() > 1
                   ? "may target any faction with a Support/Oppose/Attack participant"
                   : "restricted to the initiator's faction (no other participants)")
                + ".");

        for (Player p : state.getPlayers()) {
            // B5-4171 build repair: the human is no longer skipped outright.
            // She gets the Step 2 window the UI has been asking for since
            // B5-4158, but ONLY while she holds a legal aftermath: with
            // nothing legal a window could not be closed by any control, so
            // skipping her then is both correct and hang-free.
            if (p.isHuman()) {
                if (humanHasLegalAftermath(p, conflict, initiatorWon)) {
                    GameAction ha = waitForHumanAftermath();
                    if (ha != null && ha.getType() == GameAction.Type.PLAY_AFTERMATH
                            && (ha.getCard() instanceof AftermathCard)
                            && ha.getTarget() != null
                            && rules.canPlayAftermath(p, (AftermathCard) ha.getCard(),
                                    conflict, initiatorWon, ha.getTarget(), state)) {
                        applyAftermathPlay(p, (AftermathCard) ha.getCard(),
                                ha.getTarget(), conflict, winner);
                    }
                }
                continue;
            }
            for (Card c : new ArrayList<Card>(p.getHand())) {
                if (c instanceof AftermathCard) {
                    AftermathCard am = (AftermathCard) c;
                    // B5-0338: D2 target selection + 6-arg legality (with the
                    // D4 registry); the effect lands on the TARGET.
                    Player target = selectAftermathTarget(p, am, conflict);
                    if (rules.canPlayAftermath(p, am, conflict, initiatorWon,
                                               target, state)) {
                        applyAftermathPlay(p, am, target, conflict, winner);
                        break;
                    }
                }
            }
        }

        // Resolution Step 3 (rulebook :440): "Discard Conflicts and
        // Aftermaths ... Aftermath cards which are to be discarded after play are
        // also discarded at this time." The whole aftermath step has now run, so
        // the one-shots queued during it land in their owners' discard piles here
        // (B5-2237). Permanents are not in the queue and stay in play.
        flushPendingAftermathDiscards();

        // S3 (rulebook :440): "Discard Conflicts" — a card-initiated
        // conflict's card left the initiator's hand at initiation and is
        // discarded here at resolution. War conflicts carry no card.
        if (conflict.getCard() != null) {
            // B5-4163 gate repair: a headless fixture may build Players with no
            // deck at all (HeadlessHumanConflictAttackWindowTest constructs its
            // two seats bare and drives resolveCurrentConflict directly), and a
            // null deck here threw an NPE that failed ATKW checks 5/9/10 with
            // failure[0] != null even though the resolution itself was correct.
            // The conflict card left the initiator's deck at initiation; this is
            // the rulebook :440 discard, so skipping it when no deck exists is
            // safe and changes no production path (every real seat has a deck).
            Player initiator = conflict.getInitiator();
            if (initiator != null && initiator.getDeck() != null) {
                initiator.getDeck().discard(conflict.getCard());
            }
        }

        if (state.getActiveConflict() == conflict) {
            state.clearActiveConflict();
        }
    }

    private void applyGenericCardPlay(Player p, Card card, boolean hidden) {
        if (card == null) return;
        // B5-1038: charge card cost BEFORE hand removal, following the
        // RECRUIT_CHARACTER affordance pattern (applyInfluence, log, break
        // without removing the card when unaffordable). Cost-0 cards behave
        // byte-identically to before (applyInfluence(0) is always successful).
        int cost = card.getCost();
        if (cost > 0 && !p.applyInfluence(cost)) {
            state.log(p.getName() + " cannot apply enough influence to play "
                      + card.getTitle() + ".");
            return;
        }
        // B5-0364: sponsor-agenda one-major legality (:520 — a faction may
        // sponsor an agenda only while it has NONE in play; with one installed
        // the action is REPLACE_AGENDA). Refused BEFORE the hand removal below,
        // so the card stays in hand.
        if (card instanceof AgendaCard && !rules.canSponsorAgenda(p, (AgendaCard) card)) {
            state.log(p.getName() + " cannot sponsor " + card.getTitle()
                    + " — one agenda in play at a time (:520); REPLACE instead.");
            return;
        }
        // B5-1999 (rulebook :496): a GROUP with a race in its Card Type is
        // RESTRICTED to that race — "Only the player controlling the race
        // listed as part of the Card Type may bring a restricted card into
        // play". This is an absolute refusal, NOT the :484 double-cost rule
        // that applies to characters. canSponsorGroup also re-checks hand
        // membership and affordability, so a group that reaches this handler
        // without passing the offer gate is still refused here rather than
        // half-played. Refused BEFORE the hand removal above, so the card
        // stays in hand.
        if (card instanceof GroupCard && !rules.canSponsorGroup(p, (GroupCard) card)) {
            state.log(p.getName() + " cannot sponsor " + card.getTitle()
                    + " — a race-restricted group is playable only by its own race (:496).");
            return;
        }
        p.removeFromHand(card);
        state.log(p.getName() + " plays " + card.getTitle());

        if (card instanceof EnhancementCard) {
            CardEffects.applyPlayEnhancement(state, p, (EnhancementCard) card);
        } else if (card instanceof LocationCard) {
            p.getLocations().add((LocationCard) card);
        } else if (card instanceof GroupCard) {
            p.getGroups().add((GroupCard) card);
        } else if (card instanceof AgendaCard) {
            p.setAgenda((AgendaCard) card);
            // B5-0464 (0459 proposal): emit a distinct, greppable install
            // line for face-up agendas. The multi-round runner's parser
            // counts the token " sets agenda:" — historically a dead token
            // with no emitter, which is why harness aggregates printed
            // agendas=0 while installs visibly occurred. Emitter and parser
            // bucket land in the same change (B5-0459 standing rule).
            if (!hidden) {
                state.log(p.getName() + " sets agenda: " + card.getTitle());
            }
            if (hidden) {
                // B5-0364/:520 — hidden agenda: no effect on play until revealed.
                card.setFaceDown(true);
                state.log(p.getName() + " sponsors a hidden agenda (face down).");
            } else {
                CardEffects.applyAgendaOnPlay(state, p, (AgendaCard) card);
            }
        } else if (card instanceof EventCard) {
            CardEffects.applyPlayEvent(state, p, card);
            p.getDeck().discard(card);
        } else {
            p.getDeck().discard(card);
        }
    }

    /**
     * B5-0338: deterministic D2 target for an AI aftermath play. Participant
     * aftermaths target the playing player himself when he is a conflict
     * participant (the common WON_PARTICIPANT/LOST_PARTICIPANT self case);
     * everything else targets the conflict's initiator — the rulebook's
     * "normally played only upon the faction that initiated" default.
     */
    /**
     * B5-4171 build repair: ONE aftermath play — remove from hand, log, attach
     * to its target, dispatch the effect, and queue the Step 3 discard. The
     * body the AI loop used to inline, extracted so the human window calls the
     * same path: two callers with their own copies drift, and a human and an
     * AI playing the same card must not differ in what happens to it.
     * The caller proves legality before calling, exactly as the AI loop did.
     */
    private void applyAftermathPlay(Player p, AftermathCard am, Player target,
                                    Conflict conflict, Player winner) {
        p.removeFromHand(am);
        state.log(p.getName() + " plays aftermath: " + am.getTitle()
                  + " on " + target.getName() + ".");
        state.attachAftermath(am, target);   // D4: in play on the target
        // B5-0990/B5-2249: id-keyed dispatch first; the generic
        // text-sniffing effect stays as fallback for ids with
        // no registered effect (unknown ids keep the old
        // behaviour, matching the events rule).
        String amId = am.getId();
        boolean dispatched = amId != null && CardEffects.hasRegisteredEffect(amId);
        if (dispatched) {
            CardEffects.applyAftermathEffect(state, conflict,
                    am, target, winner);
        } else {
            applySimpleAftermathEffect(target, am, winner, state);
        }
        // B5-2237: do NOT discard here. Step 2 (:418) is
        // "Play Aftermath Cards"; the discard belongs to Step 3
        // (:440), after the whole step. Permanent aftermaths stay
        // attached to the target and are never queued; one-shots
        // are queued and flushed below, once the step is done.
        if (!isPermanentAftermath(am)) {
            queueAftermathDiscard(p, am);
        }
    }

    /**
     * B5-4171 build repair: true when <code>human</code> holds at least one
     * aftermath legally playable in this conflict, so the Step 2 window opens
     * only when the Play Aftermath control could actually light. Checks every
     * seat as target rather than guessing one, because the control offers a
     * target list and guessing would open the window on a card no legal
     * target exists for.
     */
    private boolean humanHasLegalAftermath(Player human, Conflict conflict,
                                           boolean initiatorWon) {
        if (human == null) return false;
        for (Card c : new ArrayList<Card>(human.getHand())) {
            if (!(c instanceof AftermathCard)) continue;
            AftermathCard am = (AftermathCard) c;
            for (Player target : state.getPlayers()) {
                if (rules.canPlayAftermath(human, am, conflict, initiatorWon,
                                           target, state)) return true;
            }
        }
        return false;
    }

    private Player selectAftermathTarget(Player p, AftermathCard am, Conflict conflict) {
        if (am.isParticipantCondition()
                && conflict.getParticipants().contains(p)) return p;
        return conflict.getInitiator();
    }

    private void applySimpleAftermathEffect(Player player, AftermathCard am,
                                            Player winner, GameState gs) {
        String t = am.getTriggerCondition();
        if (t.contains("WON") && player == winner) player.gainInfluence(1);
        if (t.contains("LOST") && player != winner) player.drawCards(1);
        if (t.contains("PARTICIPANT")) player.drawCards(1);
    }

/**
     * B5-1998: the Draw Round is now the engine's whole round-lifecycle draw
     * step (rulebook III. :440-:465), replacing the bare 1-card drawPhase call.
     * drawPhase() is left in RulesEngine for its existing callers.
     */
    private void runDrawPhase() {
        state.setPhase(GamePhase.DRAW);
        state.log("=== DRAW phase (discard neutralized support → optional hand discards → 1 free draw → buy extra @3 influence each → victory check) ===");
        notifyUI();
        // Steps 1 and 3 are mandatory and engine-run; steps 2 and 4 are the
        // human's elective choices (B5-4165). The Draw round must therefore
        // be a real, observable phase: the phase holds DRAW while the human
        // buys cards through the toolbar button (which calls
        // rules.buyMoreCards directly, re-checking the pool at commit) and
        // ends the round with Pass. The pre-B5-4165 one-breath drawRound()
        // call made DRAW zero-duration, so the buy control could never light.
        rules.drawRoundMandatorySteps(state);
        // B5-4163 gate repair: getHumanPlayer() FALLS BACK to players.get(0)
        // when no seat is human (GameState.java:85-90), so this window must
        // also require a real human (human.isHuman()) or every AI-only
        // runGame() blocks here in the DRAW phase of round 1 — measured:
        // HeadlessSmokeTest, "engine appears stuck (phase=DRAW, round=1)".
        Player human = state.getHumanPlayer();
        if (human != null && human.isHuman()
                && !human.hasForfeited() && !human.hasSurrendered()) {
            waitForHumanDrawEnd();
            // A surrender/forfeit submitted during the window ends it and is
            // queued here so it still resolves (DRAW-phase surrender is legal,
            // rulebook :815); PASS needs no processing.
            GameAction pending = pendingHumanAction;
            pendingHumanAction = null;
            if (pending != null && pending.getType() != GameAction.Type.PASS) {
                processAction(human, pending);
            }
        }
        // Step 5 — victory conditions are checked after the buy window.
        Player winner = rules.checkVictory(state);
        state.setPhase(GamePhase.END_ROUND);
        if (winner != null) state.setWinner(winner);
        notifyUI();
    }

    // ── Human input sync ─────────────────────────────────────────────────────

    public synchronized void submitHumanAction(GameAction action) {
        // B5-4171 build repair: the human aftermath window. A PLAY_AFTERMATH,
        // a PASS (decline), or a surrender/forfeit closes it; anything else
        // is ignored while the window is open.
        if (waitingForHumanAftermath) {
            if (action != null && action.getType() == GameAction.Type.PLAY_AFTERMATH) {
                pendingHumanAftermath = action;
            }
            if (action == null || action.getType() == GameAction.Type.PASS
                    || action.getType() == GameAction.Type.SURRENDER
                    || action.getType() == GameAction.Type.FORFEIT
                    || action.getType() == GameAction.Type.PLAY_AFTERMATH) {
                waitingForHumanAftermath = false;
            }
            notifyAll();
            return;
        }
        // B5-4165: the Draw round's human window. PASS (or null) ends it; a
        // SURRENDER/FORFEIT submitted during the window also ends it and is
        // queued for processAction by runDrawPhase. Any other action is
        // ignored while the window is open — the buy control never submits
        // through this path, it calls rules.buyMoreCards directly.
        if (waitingForHumanDrawEnd) {
            if (action != null && (action.getType() == GameAction.Type.SURRENDER
                    || action.getType() == GameAction.Type.FORFEIT)) {
                pendingHumanAction = action;
            }
            if (action == null || action.getType() == GameAction.Type.PASS
                    || action.getType() == GameAction.Type.SURRENDER
                    || action.getType() == GameAction.Type.FORFEIT) {
                waitingForHumanDrawEnd = false;
            }
            notifyAll();
            return;
        }
        if (waitingForHumanConflictJoin) {
            if (action != null && (action.getType() == GameAction.Type.JOIN_CONFLICT_SUPPORT
                    || action.getType() == GameAction.Type.JOIN_CONFLICT_OPPOSE)) {
                pendingHumanConflictJoin = action;
                waitingForHumanConflictJoin = false;
                notifyAll();
            }
            return;
        }
        if (waitingForHumanConflictAttack) {
            Player human = state.getHumanPlayer();
            boolean validAttack = action != null
                    && action.getType() == GameAction.Type.ATTACK_CONFLICT_PARTICIPANT
                    && rules.canAttackConflictParticipant(human, action.getCard(),
                            action.getTargetCard(), state.getActiveConflict());
            if (action != null && (action.getType() == GameAction.Type.PASS || validAttack)) {
                pendingHumanConflictAttack = action;
                waitingForHumanConflictAttack = false;
                notifyAll();
                // B5-4210: skipAttack — when the human submits PASS during the
                // attack window, resolve the conflict WITHOUT mutating state
                // (no rotation, no neutralization). Previously the pending pass
                // was dropped and the conflict stayed active, so the UI window
                // never closed and the test "Skip Attack resolves without
                // mutation" failed (activeConflict non-null, resolver thread
                // still alive). openConflictParticipation() re-enters the
                // attack wait (this block), so we resolve directly instead of
                // walking through it again.
                if (action.getType() == GameAction.Type.PASS) {
                    Conflict conflict = state.getActiveConflict();
                    if (conflict != null) {
                        // B5-4210: skipAttack — PASS during the attack window
                        // closes the attack window WITHOUT mutating state.
                        // resolveConflict() would rotate fleets and damage
                        // losers, so we clear the active conflict directly.
                        // The window is closed by clearing the conflict; the
                        // test asserts no rotation, no neutralization, and
                        // the active conflict gone.
                        state.clearActiveConflict();
                        state.log("Skip Attack: attack window closed, no mutation (B5-4210).");
                        notifyUI();
                    }
                }
            }
            return;
        }
        // Join actions are only valid in the explicit conflict decision window;
        // ignore stale/double clicks after the controller has collected a side.
        if (action != null && (action.getType() == GameAction.Type.JOIN_CONFLICT_SUPPORT
                || action.getType() == GameAction.Type.JOIN_CONFLICT_OPPOSE
                || action.getType() == GameAction.Type.ATTACK_CONFLICT_PARTICIPANT)) return;
        pendingHumanAction = action;
        waitingForHuman    = false;
        notifyAll();
    }

    /**
     * B5-4165: the Draw round's human window. Step 4 buys are the toolbar
     * button calling rules.buyMoreCards directly, so no GameAction type is
     * needed; the window releases when the human submits PASS (the Pass
     * button lights during the window). A SURRENDER/FORFEIT also ends it and
     * is queued for processAction. notifyUI on every poll keeps the buy
     * control re-lit and re-dimmed live as buys spend the pool.
     */
    private synchronized void waitForHumanDrawEnd() {
        waitingForHumanDrawEnd = true;
        notifyUI();
        while (waitingForHumanDrawEnd) {
            try { wait(200); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            notifyUI();
        }
    }

    /** True while the Draw round is holding for the human's buy choices. */
    public boolean isWaitingForHumanDrawEnd() { return waitingForHumanDrawEnd; }

    /**
     * B5-4171 build repair: the human's Resolution Step 2 window (rulebook
     * :418 "Play Aftermath Cards"). Opened only when the human holds a legal
     * aftermath, so it can never hang a run in which the human has nothing to
     * play — the pre-repair <code>continue</code> for that case is preserved.
     */
    private synchronized GameAction waitForHumanAftermath() {
        waitingForHumanAftermath = true;
        pendingHumanAftermath    = null;
        notifyUI();
        while (waitingForHumanAftermath) {
            try { wait(200); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                waitingForHumanAftermath = false;
            }
            notifyUI();
        }
        return pendingHumanAftermath;
    }

    /** True only while Resolution Step 2 is holding for the human aftermath play. */
    public boolean isWaitingForHumanAftermath() { return waitingForHumanAftermath; }

    private synchronized GameAction waitForHumanAction() {
        waitingForHuman    = true;
        pendingHumanAction = null;
        notifyUI();
        while (waitingForHuman) {
            try { wait(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        return pendingHumanAction != null ? pendingHumanAction : GameAction.pass();
    }

    public boolean isWaitingForHuman() { return waitingForHuman; }

    private boolean hasLegalHumanConflictAttack(Player human, Conflict conflict) {
        List<Card> attackers = new ArrayList<Card>();
        if (human.getAmbassador() != null) attackers.add(human.getAmbassador());
        attackers.addAll(human.getInnerCircle());
        attackers.addAll(human.getSupportingRole());
        attackers.addAll(human.getFleets());
        attackers.addAll(human.getLocations());
        attackers.addAll(human.getGroups());
        attackers.addAll(human.getEnhancements());
        if (human.getAgenda() != null) attackers.add(human.getAgenda());
        for (Card attacker : attackers) {
            for (Player participant : conflict.getParticipants()) {
                for (Card target : conflict.getCommittedCards(participant)) {
                    if (rules.canAttackConflictParticipant(human, attacker, target, conflict)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private synchronized GameAction waitForHumanConflictJoin() {
        waitingForHumanConflictJoin = true;
        pendingHumanConflictJoin = null;
        notifyUI();
        while (waitingForHumanConflictJoin) {
            try { wait(200); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                waitingForHumanConflictJoin = false;
            }
        }
        return pendingHumanConflictJoin;
    }

    /** True only while the active conflict is waiting for the human side choice. */
    public boolean isWaitingForHumanConflictJoin() {
        return waitingForHumanConflictJoin;
    }

    private synchronized GameAction waitForHumanConflictAttack() {
        waitingForHumanConflictAttack = true;
        pendingHumanConflictAttack = null;
        notifyUI();
        while (waitingForHumanConflictAttack) {
            try { wait(200); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                waitingForHumanConflictAttack = false;
            }
        }
        return pendingHumanConflictAttack;
    }

    public boolean isWaitingForHumanConflictAttack() {
        return waitingForHumanConflictAttack;
    }

    /**
     * B5-4163: the human's declaration window in the CONFLICT round. It sets
     * the SAME waitingForHuman flag the action round uses, because MainWindow
     * gates myTurn — and therefore its CONFLICT-phase Initiate and Pass
     * controls — on isWaitingForHuman() plus GamePhase.CONFLICT
     * (MainWindow.java:3624-3644). Reusing that flag is what lets the restored
     * round light those controls without a single MainWindow edit; the
     * declaration is re-checked by RulesEngine.canInitiateConflict when it is
     * applied (processAction, INITIATE_CONFLICT / DECLARE_WAR_CONFLICT).
     */
    private synchronized GameAction waitForHumanConflictDeclaration() {
        waitingForHumanConflictDeclaration = true;
        waitingForHuman = true;
        pendingHumanAction = null;
        notifyUI();
        while (waitingForHuman) {
            try { wait(200); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                waitingForHuman = false;
            }
        }
        waitingForHumanConflictDeclaration = false;
        return pendingHumanAction;
    }

    /** True only while the conflict round is waiting for the human's declaration. */
    public boolean isWaitingForHumanConflictDeclaration() {
        return waitingForHumanConflictDeclaration;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Enforces hand limit of 7 cards at end of turn (B5-1968). */
    private void endOfTurn(Player p) {
        List<Card> hand = p.getHand();
        while (hand.size() > 7) {
            Card toDiscard = hand.remove(hand.size() - 1);
            p.getDeck().discard(toDiscard);
            state.log(p.getName() + " discards " + toDiscard.getTitle() + " (end-of-turn hand limit).");
        }
    }

    private AIPlayer getAI(Player p) {
        for (AIPlayer ai : aiPlayers) {
            if (ai.getPlayer() == p) return ai;
        }
        return aiPlayers.get(0);
    }

    private void notifyUI() {
        if (uiCallback != null) uiCallback.accept(state);
    }

    private void pause(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public GameState getState() { return state; }
}
