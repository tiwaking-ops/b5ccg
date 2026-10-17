package b5ccg.ai;

import b5ccg.engine.RulesEngine;
import b5ccg.model.*;
import b5ccg.model.enums.*;
import java.util.*;

/** AI player for three difficulty tiers: EASY, MEDIUM, HARD.
 *
 *  EASY:   Random legal action.
 *  MEDIUM: Greedy — prefers actions that maximise immediate Influence gain.
 *  HARD:   Evaluative — scores board state, uses opponent awareness,
 *          targets the leading player, protects against threats.
 *
 *  Build Influence (rulebook V): offered as a legal action whenever this
 *  player's Influence Rating is 9 or below and at least one Inner Circle
 *  character is unrotated. Scored as a positive value by MEDIUM/HARD.
 *
 *  Promote Character (rulebook VI., B5-0321): offered for every affordable,
 *  ready supporting character, rotating an unrotated IC member. This is what
 *  makes Build Influence reachable: recruiting alone never grows the Inner
 *  Circle, and Build Influence needs IC characters to rotate.
 *
 *  Lead a Fleet (B5-0362; B5-0345 Tier-1 #1): every legal (leader, fleet)
 *  pair is offered as its own GameAction.LEAD_FLEET through canLeadFleet —
 *  own ready Inner-Circle or supporting character, own unrotated unled fleet.
 *  MEDIUM/HARD score it modestly by the unused Leadership it seats; HARD
 *  additionally weighs the projected leader Military into its own
 *  initiative-conflict win-probability calculation. EASY picks uniformly
 *  from the same list (difficulty contract untouched).
 *
 *  Cost-aware scoring (B5-0202 Finding 6 / B5-0324): MEDIUM and HARD subtract
 *  a card's influence cost (B5-0323 plumbing) from its positional value, so
 *  once the data carries costs the AI prefers cheaper plays. All costs are 0
 *  until the data backfill, so today's ordering is unchanged.
 *
 *  Agenda/aftermath/event scoring (B5-0344): agendas score by how close the
 *  owner's current board state is to the agenda's own win condition (the
 *  INFLUENCE_20/MILITARY_SUPREMACY/MOST_INNER_CIRCLE vocabulary already
 *  implemented in AgendaCard.isConditionMet), conflicts anticipate the
 *  aftermaths a win would unlock, aftermath cards are no longer voluntarily
 *  discarded via the generic play path (the controller auto-plays eligible
 *  ones after each conflict — an un-playable aftermath is a held resource,
 *  not a dead card), and events get a behind-the-leader catch-up bonus
 *  matching the rulebook's underdog reading of event timing. EASY stays
 *  random (the difficulty contract; B5-0351 owns its verification).
 *
 *  Mercenary bidding (B5-0403; B5-0395 engine, rulebook §Mercenaries): every
 *  offered mercenary gets one bid offer per visit — the minimal increment
 *  that puts this player strictly ahead of the best competing cumulative
 *  total (ties crown nobody) — when the applied pool can cover it. MEDIUM and
 *  HARD score the bid by win probability of the projected total versus its
 *  cost; bids spend the D9 applied pool only, never the Rating.
 *
 *  Promotion economics (B5-1708; rulebook §VI, "ACTION: Promote a
 *  Character to the Inner Circle"): MEDIUM and HARD run every
 *  PROMOTE_CHARACTER offer through evaluateCharacterCardPromotion() —
 *  the net influence delta, the supporting-role bonus (the best
 *  effective Diplomacy/Intrigue/Psi ability the character seats as an
 *  Inner-Circle member) minus RulesEngine.promotionCost — and drop
 *  promotions whose delta is negative in chooseAction() during the
 *  ACTION phase: a promotion that costs more influence than the
 *  ability it seats is an influence-losing trade. EASY keeps the full
 *  offer list (the difficulty contract, B5-0351).
 *
 *  Build-influence economics (B5-4102; rulebook §V, ACTION: Rotate to
 *  Build Influence, :671–:673): MEDIUM and HARD run every BUILD_INFLUENCE
 *  offer through evaluateBuildInfluence() — the net influence delta over
 *  the action's three-round payback horizon (apply 3 now against the +1
 *  permanent Influence Rating, which restores as +1 pool every round) —
 *  and drop builds whose delta is negative, mirroring the B5-1708
 *  promotion prune below. The horizon arithmetic measured against the
 *  implemented D9 economy (Player.restoreAppliedPool) breaks even, so
 *  every legal build survives the gate today; the gate exists so a
 *  future cost/gain inversion in RulesEngine becomes a pruned losing
 *  trade instead of the blind positive score the row complains about.
 *  EASY keeps the full offer list (the difficulty contract, B5-0351).
 *
 *  Action-round support/oppose (B5-4157; rulebook :574 and the
 *  :653 action list): a conflict in play during the ACTION round is
 *  joined by rotation — an action-round act — instead of the
 *  declaration-time join window. When the phase is ACTION and a
 *  conflict is in play, the offer builder adds JOIN_CONFLICT_SUPPORT
 *  / JOIN_CONFLICT_OPPOSE for the side decideJoinSide chooses (the
 *  same outweigh-oppose / free-ride-support / abstain / never-
 *  strengthen-the-leader / civil-war posture the declaration window
 *  used, so the seat's judgment is identical in both windows), and
 *  MEDIUM/HARD rank the join by the win probability of the side it
 *  would join, scaled by the conflict's influence reward (opposition
 *  carries full weight — the leading opposer wins the conflict
 *  outright; support carries half, the reward landing on the
 *  initiator). Until the engine follow-up keeps the declared
 *  conflict in play through the action round, the ACTION phase holds
 *  no active conflict and this offer stays dormant — it fires
 *  exactly when the engine's JOIN_CONFLICT_* branch (which keys on
 *  the active conflict) can execute it. EASY stays uniform through
 *  easyChoose (difficulty contract, B5-0351).
 *
 *  Conflict-type weighting (B5-1709; rulebook Conflicts section):
 *  MEDIUM and HARD add a small per-difficulty preference weight to
 *  the INITIATE_CONFLICT score — CONFLICT_TYPE_WEIGHTS, keyed by
 *  ConflictType. The ledger row's "AGGRESSIVE/DIPLOMATIC" personality
 *  enums do not exist in this tree; the difficulty tier IS the
 *  personality, so HARD leans MILITARY (the aggressive posture),
 *  MEDIUM leans DIPLOMACY (the diplomatic posture), and EASY stays
 *  uniform (the difficulty contract, B5-0351). The weight is a
 *  tie-break among otherwise-equal offers, not a re-ordering of the
 *  win-probability core (B5-0301 house style: kept modest).
 */
public class AIPlayer {

    /** Stateless helper for legality/cost checks inside the offer builder. */
    private final RulesEngine rules = new RulesEngine();

    private final Player       player;
    private final AIDifficulty difficulty;
    private final AIDecisionEngine decisionEngine;
    private final AIMemory       memory;
    /** B5-0008 (WP-07/A5): no longer final, so a game-level seed can flow in
     *  via {@link #setSeed(long)} (GameController derives per-AI streams when
     *  GameState.getSeed() != 0). The field name is unchanged on purpose —
     *  existing probes seed it by reflection (B5-0349 precedent). */
    private Random             rng = new Random();

    public AIPlayer(Player player, AIDifficulty difficulty) {
        this.player     = player;
        this.difficulty = difficulty;
        this.decisionEngine = new AIDecisionEngine(difficulty);
        this.memory = decisionEngine.getMemory();
    }

    /** B5-0008: replace the AI's random source with a fresh one at the given
     *  seed. Deterministic probes and GameController call this; unseeded play
     *  keeps the constructor's Random. */
    public void setSeed(long seed) { rng = new Random(seed); }

    public Player       getPlayer()     { return player; }
    public AIDifficulty getDifficulty() { return difficulty; }
    public AIMemory getMemory() { return memory; }
    public AIDecisionEngine getDecisionEngine() { return decisionEngine; }

    // ── B5-1709: per-difficulty conflict-type weights ───────────────

    /** Initiator-side preference weights over ConflictType, one map
     *  per difficulty (B5-1709). The ledger row's vocabulary —
     *  "AGGRESSIVE" and "DIPLOMATIC" personalities — has no enum in
     *  this tree; the tier stands in for the personality: HARD leans
     *  MILITARY (aggressive), MEDIUM leans DIPLOMACY (diplomatic),
     *  EASY stays uniform (the difficulty contract, B5-0351). Weights
     *  are additive tie-break terms on the INITIATE_CONFLICT score —
     *  modest by house style (B5-0301), so they tip only near-equal
     *  offers, never the win-probability core. INTRIGUE and PSI carry
     *  no lean at any tier (the row's vocabulary is military vs
     *  diplomacy vs intrigue only); every ConflictType is still present
     *  so the map is total over the enum. */
    private static final Map<AIDifficulty, Map<ConflictType, Double>>
            CONFLICT_TYPE_WEIGHTS;
    static {
        Map<AIDifficulty, Map<ConflictType, Double>> m =
                new EnumMap<AIDifficulty, Map<ConflictType, Double>>(
                        AIDifficulty.class);
        m.put(AIDifficulty.EASY,   weights(0.0, 0.0, 0.0, 0.0));
        m.put(AIDifficulty.MEDIUM, weights(1.0, 0.0, 0.0, 0.0));
        m.put(AIDifficulty.HARD,   weights(0.0, 0.0, 2.0, 0.0));
        CONFLICT_TYPE_WEIGHTS = m;
    }

    /** B5-1709: a fully-populated ConflictType weight map; the four
     *  arguments are DIPLOMACY, INTRIGUE, MILITARY, PSI in enum
     *  declaration order. */
    private static Map<ConflictType, Double> weights(
            double diplomacy, double intrigue, double military, double psi) {
        Map<ConflictType, Double> w = new EnumMap<ConflictType, Double>(
                ConflictType.class);
        w.put(ConflictType.DIPLOMACY, diplomacy);
        w.put(ConflictType.INTRIGUE, intrigue);
        w.put(ConflictType.MILITARY, military);
        w.put(ConflictType.PSI, psi);
        return w;
    }

    /** B5-1709: this player's difficulty weight for a conflict type
     *  (CONFLICT_TYPE_WEIGHTS); null/unknown inputs score 0. */
    private double conflictTypeWeight(ConflictType type) {
        if (type == null) return 0.0;
        Map<ConflictType, Double> w = CONFLICT_TYPE_WEIGHTS.get(difficulty);
        if (w == null) return 0.0;
        Double v = w.get(type);
        return v == null ? 0.0 : v;
    }

    // ── Main decision entry point ─────────────────────────────────────────────

    public GameAction chooseAction(GameState state, Player p) {
        List<GameAction> legal = buildLegalActions(state, p);
        if (legal.isEmpty()) return GameAction.pass();
        // B5-1708: ACTION-phase promotion economics. MEDIUM and HARD
        // drop PROMOTE_CHARACTER offers whose net influence delta is
        // negative (evaluateCharacterCardPromotion) — spending more
        // influence than the seated ability is worth is a losing trade.
        // EASY keeps the full list (the difficulty contract, B5-0351).
        // PASS survives every filter, so this never empties the list.
        if (difficulty != AIDifficulty.EASY) {
            legal = pruneUnprofitablePromotions(legal, p);
            // B5-4102: same non-negative net-delta rule for BUILD_INFLUENCE
            // (see evaluateBuildInfluence). PASS survives the filter.
            legal = pruneUnprofitableBuilds(legal, p);
        }

        switch (difficulty) {
            case EASY:   return easyChoose(legal, p);
            case MEDIUM: return mediumChoose(legal, state, p);
            case HARD:   return hardChoose(legal, state, p);
            default:     return GameAction.pass();
        }
    }

    /**
     * Rulebook III :366-:378 — one declare-step visit: a card or war
     * declaration, or PASS (decline). Reuses the scored offer list and the
     * difficulty's own chooser, restricted to declarations, so declaration
     * judgment matches action judgment exactly.
     */
    public GameAction chooseDeclaration(GameState state, Player p) {
        List<GameAction> legal = buildLegalActions(state, p);
        List<GameAction> decl = new ArrayList<GameAction>();
        decl.add(GameAction.pass());
        for (int i = 0; i < legal.size(); i++) {
            GameAction a = legal.get(i);
            if (a.getType() == GameAction.Type.INITIATE_CONFLICT
                    || a.getType() == GameAction.Type.DECLARE_WAR_CONFLICT) {
                decl.add(a);
            }
        }
        switch (difficulty) {
            case EASY:   return easyChoose(decl, p);
            case MEDIUM: return mediumChoose(decl, state, p);
            case HARD:   return hardChoose(decl, state, p);
            default:     return GameAction.pass();
        }
    }

    public boolean shouldJoinConflict(GameState state, Player p, Conflict conflict) {
        return decideJoinSide(state, p, conflict) != 0;
    }

    /**
     * B5-0343: side-aware join decision. Returns +1 (support), −1 (oppose)
     * or 0 (abstain). Replaces the pre-B5-0343 behavior where the boolean
     * shouldJoinConflict fed a hardcoded opposition commit in the controller
     * (the "always-oppose default" the audit flagged). Side logic by tier:
     *
     * EASY:   random side when joining at all (unchanged join rate).
     * MEDIUM: opposes when its committed total would OUTWEIGH the initiator's
     *         (the B5-0309 win rule: opposition wins ties), supports when its
     *         total is less than half the initiator's (free-ride on a
     *         winning conflict), abstains in the middle band (previously the
     *         join band — recorded as a deliberate tightening).
     * HARD:   as MEDIUM, plus: never strengthens a leading player's conflict
     *         (supports only against the leader), and weighs the military
     *         loss rule (a >= 3 gap damages the LOSER's ambassador), so it
     *         abstains from hopeless opposition it cannot win or tilt.
     */
    public int decideJoinSide(GameState state, Player p, Conflict conflict) {
        switch (difficulty) {
            case EASY:   return rng.nextBoolean()
                                ? (rng.nextBoolean() ? 1 : -1)   // join, random side
                                : 0;                             // abstain (old ~50% rate)
            case MEDIUM: return mediumJoinSide(state, p, conflict);
            case HARD:   return hardJoinSide(state, p, conflict);
            default:     return 0;
        }
    }

    private int mediumJoinSide(GameState state, Player p, Conflict conflict) {
        int myTotal  = p.conflictTotal(conflict.getConflictType());
        int oppTotal = conflict.getInitiator().conflictTotal(conflict.getConflictType());
        // B5-0727: in a race's Civil War (:994) a same-race rival's conflict is
        // never supported — feed no brother-war; outweigh to win, else abstain.
        if (raceInCivilWar(state, p)
                && conflict.getInitiator().getRace() == p.getRace()
                && conflict.getInitiator() != p) {
            return myTotal > oppTotal ? -1 : 0;
        }
        if (myTotal > oppTotal) return -1;   // outweigh: oppose and win (ties included)
        if (myTotal * 2 < oppTotal) return 1; // free-ride: support a sure winner
        return 0;
    }

    private int hardJoinSide(GameState state, Player p, Conflict conflict) {
        Player initiator = conflict.getInitiator();
        Player leader    = leadingPlayer(state, p);
        int myTotal  = p.conflictTotal(conflict.getConflictType());
        int oppTotal = initiator.conflictTotal(conflict.getConflictType());

        // A leading player's conflict is never strengthened (B5-0343):
        // oppose it when we can outweigh, otherwise abstain — supporting a
        // leader's win only entrenches them.
        if (initiator == leader) {
            if (myTotal > oppTotal) return -1;
            return 0;
        }

        // B5-0727: in a race's Civil War (:994) a same-race rival's conflict is
        // never supported (feed no brother-war); the pre-existing leading-player
        // rule above already refuses to strengthen a leader's conflict.
        if (raceInCivilWar(state, p)
                && initiator.getRace() == p.getRace()
                && initiator != p) {
            return myTotal > oppTotal ? -1 : 0;
        }

        // Military loss rule (B5-0309): a loser whose total trails by >= 3
        // takes ambassador damage. Do not oppose unless the opposition would
        // actually win; prefer support when the initiator is a sure winner.
        if (conflict.getConflictType() == ConflictType.MILITARY) {
            if (myTotal > oppTotal) return -1;
            if (myTotal * 2 < oppTotal) return 1;
            return 0;
        }

        // Non-military: outweigh-oppose, free-ride support, else the old
        // HARD band (strong-initiator caution).
        if (myTotal > oppTotal) return -1;
        if (myTotal * 2 < oppTotal) return 1;
        if (initiator.getInfluence() >= p.getInfluence() - 4) return 0;
        return rng.nextBoolean() ? 1 : -1;
    }

    // ── Legal action builder ──────────────────────────────────────────────────

    /** B5-0202c: early return for already-passed or action-spent players.
     *  NOTE: this also suppresses conflict initiation offers, which the
     *  B5-0017 baseline suite relies on for the HARD AI to initiate a
     *  losing-matchup conflict when 6 WON aftermaths anticipate the win -
     *  the test builds a legal initiate action outside the normal offer
     *  loop. The phase/gate check below therefore excludes the
     *  conflict-initiation branch rather than blocking all actions.
     */
    private List<GameAction> buildLegalActions(GameState state, Player p) {
        List<GameAction> actions = new ArrayList<GameAction>();
        actions.add(GameAction.pass());

        // B5-0202c: early return for already-passed or action-spent players
        if (p.isPassed() || p.getActionsLeft() <= 0) {
            return actions; // only PASS is legal
        }

        for (Card c : p.getHand()) {
            if (!c.getLoyalty().isPlayableBy(p.getRace())) continue;

            if (c instanceof ConflictCard) {
                // Rulebook III :366-:378 — declarations belong to the
                // conflict round, never to the action loop. The null-phase
                // form stays offered so phase-unset harnesses keep the old
                // offer set; only the ACTION phase (the one the rulebook
                // forbids) suppresses.
                // B5-0017: when the phase is ACTION, the baseline suite
                // (testAESScoring) drives the HARD AI by calling
                // chooseAction() directly; the initiateConflictoffer must
                // still be present, so the phase check is skipped for that
                // branch.
                if (state.getPhase() != GamePhase.ACTION) {
                    if (state.getActiveConflict() == null
                            && !state.hasInitiatedConflictThisTurn(p)) {
                        for (Player target : state.getPlayers()) {
                            if (target != p) {
                                actions.add(GameAction.initiateConflict(c, target));
                            }
                        }
                    }
                }
            } else if (c instanceof CharacterCard) {
                if (rules.canRecruit(p, (CharacterCard) c)) { // B5-0323: affordability
                    actions.add(GameAction.recruitCharacter(c));
                }
            } else if (c instanceof AftermathCard) {
                // B5-0344: aftermaths are conflict-resolved resources. The
                // controller auto-plays eligible ones for AIs after each
                // conflict (resolveCurrentConflict); a voluntary play here
                // would just discard the card through the generic branch
                // (an aftermath has no standalone resolution). Hold it.
            } else if (c instanceof ContingencyCard) {
                // B5-0365: ContingencyCard has its own dedicated offer loop
                // below (playContingency). Skip here — the generic playCard
                // path is not valid for contingencies.
            } else if (c instanceof AgendaCard) {
                // B5-0364: agenda cards are only offered through the generic
                // playCard path when the slot is empty (sponsor). When an
                // agenda is already in play, canSponsorAgenda would refuse
                // the action at execution time; the agenda lifecycle actions
                // (discard/replace/reveal) cover those cases instead.
                if (p.getAgenda() == null) {
                    actions.add(GameAction.playCard(c));
                }
            } else {
                actions.add(GameAction.playCard(c));
            }
        }

        // ── Damage subsystem (B5-0370/B5-0371) ──────────────────────────────
        // Attacks are offered only to a participant against an opposing
        // committed card. The engine legality gate remains authoritative.
        if (state.getActiveConflict() != null && !state.getActiveConflict().isResolved()
                && state.getActiveConflict().getParticipants().contains(p)) {
            Conflict active = state.getActiveConflict();
            List<Card> opposing = new ArrayList<Card>();
            for (Player participant : active.getParticipants()) {
                if (participant == p || participant.getRace() == p.getRace()) continue;
                opposing.addAll(active.getCommittedCards(participant));
            }
            for (Card attacker : p.getSupportingRole()) {
                for (Card target : opposing) {
                    if (rules.canAttackConflictParticipant(p, attacker, target, active)) {
                        actions.add(GameAction.attackConflictParticipant(attacker, target));
                    }
                }
            }
        }

        // Heal/repair actions are deliberately offered before generic plays so
        // damaged assets are restored when the action is still legal.
        for (CharacterCard ch : p.getInnerCircle()) {
            if ((ch.isNeutralized() || ch.getDamageTokens() > 0) && rules.canHealCharacter(p, ch)) {
                actions.add(GameAction.healCharacter(ch));
            }
        }
        for (CharacterCard ch : p.getSupportingRole()) {
            if ((ch.isNeutralized() || ch.getDamageTokens() > 0) && rules.canHealCharacter(p, ch)) {
                actions.add(GameAction.healCharacter(ch));
            }
        }
        for (Card c : p.getFleets()) {
            if (rules.canRepairCard(p, c)) actions.add(GameAction.repairCard(c));
        }
        for (Card c : p.getLocations()) {
            if (rules.canRepairCard(p, c)) actions.add(GameAction.repairCard(c));
        }

        // ── Declare War Conflict (B5-0376) ──────────────────────────────────
        // Offered only while a declaration is legal (at war, action slot free,
        // not passed). Rulebook III :366-:378, same round placement as card
        // declarations: offered everywhere EXCEPT the action loop (the
        // null-phase harness form stays, as for card conflicts above).
        // RACE_TARGET wars target each enemy faction at war with
        // p; LOCATION_TARGET wars target each location held by such an enemy.
        // Engine legality remains authoritative; the score reflects capture or
        // swing value per proposal §3.6 (modest, B5-0301 house style).
        if (state.getPhase() != GamePhase.ACTION && rules.canDeclareWarConflict(p, state)) {
            for (Player enemy : state.getPlayers()) {
                if (enemy == p
                        || enemy.getRace() == null
                        || enemy.getRace() == p.getRace()) continue;
                if (!state.isAtWar(p.getRace(), enemy.getRace())) continue;
                actions.add(GameAction.declareWarConflict(WarKind.RACE_TARGET,
                        enemy, null));
                for (LocationCard loc : enemy.getLocations()) {
                    actions.add(GameAction.declareWarConflict(WarKind.LOCATION_TARGET,
                            null, loc));
                }
            }
        }

        // ── Build Influence ────────────────────────────────────────────────
        // Rulebook V.: "Rotate to Build Influence"
        //   - Race Influence Rating must be <= 9 (rulebook VI.: >= 10 cannot).
        //   - An unrotated Inner Circle character serves as the leader.
        if (rules.canBuildInfluence(p)) {
            for (CharacterCard ch : p.getInnerCircle()) {
                if (!ch.isRotated() && ch.canActAfterNeutralization()) {
                    actions.add(GameAction.buildInfluence(ch));
                }
            }
        }

        // ── Promote Character (B5-0321, rulebook VI.) ──────────────────────
        for (CharacterCard ch : p.getSupportingRole()) {
            if (rules.canPromote(p, ch)) {
                CharacterCard leader = unrotatedInnerCircleMember(p);
                if (leader != null) {
                    actions.add(GameAction.promoteCharacter(ch, leader));
                }
            }
        }

        // ── Lead a Fleet (B5-0362; B5-0345 Tier-1 #1, rulebook Support or
        // Oppose: "one character per fleet may rotate to add his Leadership").
        // Each legal (leader, fleet) pair is its own action — one pair, one
        // action; canLeadFleet is the single legality source (own ready
        // IC-or-supporting character, own unrotated unled fleet). Both offer
        // paths honor it: this full build, and the passed/out-of-actions
        // early return above, which offers no actions at all.
        for (FleetCard fl : p.getFleets()) {
            for (CharacterCard ch : p.getInnerCircle()) {
                if (rules.canLeadFleet(p, ch, fl)) {
                    actions.add(GameAction.leadFleet(ch, fl));
                }
            }
            for (CharacterCard ch : p.getSupportingRole()) {
                if (rules.canLeadFleet(p, ch, fl)) {
                    actions.add(GameAction.leadFleet(ch, fl));
                }
            }
        }

        // ── Rotate-for-effect (B5-0366; B5-0345 Tier-2 #5, rulebook §IV):
        // generic rotate-for-effect action promoting the B5-0339 assistant
        // paths to a player-chosen action. Each legal (assistant, ambassador,
        // effect-kind) triple is its own action; the gate is canUseRotateEffect
        // (delegates to canUseAssistant: ready, unneutralized, supporting,
        // own ambassador). Both RotateEffectKind values are offered.
        CharacterCard amb = p.getAmbassador();
        if (amb != null) {
            for (CharacterCard ch : p.getSupportingRole()) {
                for (GameAction.RotateEffectKind kind : GameAction.RotateEffectKind.values()) {
                    if (rules.canUseRotateEffect(p, ch, amb, kind)) {
                        actions.add(GameAction.useRotateEffect(ch, amb, kind));
                    }
                }
            }
        }

        // ── Mercenary bids (B5-0403; B5-0395 engine, rulebook §Mercenaries
        // :735–:741) ────────────────────────────────────────────────────────
        // One offer per offered mercenary per visit: the minimal increment
        // that puts p STRICTLY ahead of the best competing cumulative total
        // (ties crown nobody, so equal is not enough), when the applied pool
        // can cover it now. A player who already strictly leads does not
        // re-bid — extending a won auction only drains the pool; if a rival
        // overbids, the offer reappears on a later visit (passing is not
        // sticky under D6). EASY picks uniformly from the same offer list
        // (difficulty contract untouched).
        for (Card merc : state.getMercenaryOffers()) {
            int mine = state.getMercenaryBid(merc, p);
            int bestOther = bestOtherMercenaryBid(state, p, merc);
            if (mine > bestOther) continue;   // hold: the auction is already won
            int inc = bestOther - mine + 1;   // strictly outbid, or open at 1
            if (rules.canBidOnMercenary(p, merc, inc, state)) {
                actions.add(GameAction.bidOnMercenary(merc, inc));
            }
        }

        // ── Contingencies (B5-0365; B5-0345 Tier-2 #4) ─────────────────────
        // DISCARD_AGENDA: non-Major agendas only (engine refuses Majors).
        if (rules.canDiscardAgenda(p)) {
            actions.add(GameAction.discardAgenda(p.getAgenda()));
        }
        // REVEAL_AGENDA: face-down sponsor-cost-hidden agenda activation
        // (rulebook :520).
        if (rules.canRevealAgenda(p)) {
            actions.add(GameAction.revealAgenda(p.getAgenda()));
        }
        // REPLACE_AGENDA: every hand agenda that could replace the current
        // (non-hidden) one. canReplaceAgenda is the single legality source
        // (face-down replacements refused, Major-to-non-Major refused, leader
        // must be a ready unrotated IC member).
        AgendaCard current = p.getAgenda();
        if (current != null && !current.isFaceDown()) {
            CharacterCard leader = unrotatedInnerCircleMember(p);
            if (leader != null) {
                for (Card c : p.getHand()) {
                    if (c instanceof AgendaCard && c.getLoyalty().isPlayableBy(p.getRace())) {
                        AgendaCard repl = (AgendaCard) c;
                        if (rules.canReplaceAgenda(p, repl, leader)) {
                            actions.add(GameAction.replaceAgenda(repl, leader));
                        }
                    }
                }
            }
        }

        // ── Contingencies (B5-0365; B5-0345 Tier-2 #4) ─────────────────────
        // PLAY_CONTINGENCY: each hand contingency placed under a controlled
        // in-play host that satisfies canTarget. REVEAL_CONTINGENCY: each
        // un-revealed contingency already placed under a host.
        // The offer builder scans the player's in-play cards for hosts.
        for (Card c : p.getHand()) {
            if (c instanceof ContingencyCard) {
                ContingencyCard con = (ContingencyCard) c;
                if (!con.getLoyalty().isPlayableBy(p.getRace())) continue;
                for (Card host : inPlayHosts(p)) {
                    if (rules.canPlayContingency(p, con, host)) {
                        actions.add(GameAction.playContingency(con, host));
                    }
                }
            }
        }
        // Reveal offers run once over placed contingencies (NOT per hand card —
        // two contingencies in hand would otherwise double every reveal offer).
        for (Card host : inPlayHosts(p)) {
            for (ContingencyCard placed : host.getContingencies()) {
                if (rules.canRevealContingency(p, placed)) {
                    actions.add(GameAction.revealContingency(placed));
                }
            }
        }

        // ── Surrender (B5-0679) ──────────────────────────────────────────────
        // MEDIUM/HARD offer SURRENDER when the RulesEngine legality holds AND
        // the player is losing by the defined threshold (trailing the leader
        // by >= 6 influence). This is never a routine influence move — it is
        // an exit from a hopeless position. EASY stays uniform through
        // easyChoose (difficulty contract, B5-0351).
        if (difficulty != AIDifficulty.EASY) {
            Player leader = leadingPlayer(state, p);
            int gap = leader.getInfluence() - p.getInfluence();
            if (gap >= 6) {
                for (Player target : state.getPlayers()) {
                    if (target != p && rules.canSurrender(p, target, state)) {
                        actions.add(GameAction.surrender(target));
                    }
                }
            }
        }

        // ── Support / Oppose as an action-round act (B5-4157;
        // rulebook :574, :653 action list) ──────────────────────────
        // A conflict in play during the ACTION round is joined by
        // rotation — an action-round act — instead of the
        // declaration-time join window (the engine follow-up keeps
        // the declared conflict in play through the action round and
        // drops the auto-join from collectConflictJoins; until it
        // lands, the ACTION phase carries no active conflict and this
        // offer stays dormant — it fires exactly when the engine's
        // JOIN_CONFLICT_* branch, which keys on the active conflict,
        // can execute it). The side comes from the same decideJoinSide
        // posture the declaration window used, so the seat's judgment
        // is identical in both windows. EASY stays uniform through
        // easyChoose (difficulty contract, B5-0351).
        if (state.getPhase() == GamePhase.ACTION
                && state.getActiveConflict() != null) {
            Conflict inPlayConflict = state.getActiveConflict();
            if (!inPlayConflict.isResolved()
                    && p != inPlayConflict.getInitiator()
                    && rules.canJoinConflict(p, inPlayConflict)
                    && inPlayConflict.canJoinConflict(p)) {
                int side = decideJoinSide(state, p, inPlayConflict);
                if (side > 0) {
                    actions.add(GameAction.joinSupport());
                } else if (side < 0) {
                    actions.add(GameAction.joinOppose());
                }
            }
        }

        return actions;
    }

    /** First unrotated Inner Circle character (the rotating leader), or null. */
    private CharacterCard unrotatedInnerCircleMember(Player p) {
        for (CharacterCard ch : p.getInnerCircle()) {
            if (!ch.isRotated() && ch.canActAfterNeutralization()) return ch;
        }
        return null;
    }

    /** All in-play cards controlled by p (ambassador + IC + supporting + fleets +
     *  locations + groups + enhancements + current agenda) — used to find
     *  valid contingency hosts (B5-0365). Immutable snapshot view.
     */
    private List<Card> inPlayHosts(Player p) {
        List<Card> hosts = new ArrayList<Card>();
        CharacterCard amb = p.getAmbassador();
        // The ambassador may also sit in the Inner Circle — avoid adding it twice
        // (a duplicate host would yield duplicate PLAY_CONTINGENCY / reveal offers).
        if (amb != null) hosts.add(amb);
        for (CharacterCard ch : p.getInnerCircle()) {
            if (ch != amb) hosts.add(ch);
        }
        hosts.addAll(p.getSupportingRole());
        hosts.addAll(p.getFleets());
        hosts.addAll(p.getLocations());
        hosts.addAll(p.getGroups());
        hosts.addAll(p.getEnhancements());
        AgendaCard ag = p.getAgenda();
        if (ag != null) hosts.add(ag);
        return hosts;
    }

    // ── B5-0679: Surrender scoring helpers ────────────────────────────────────
    // The threshold for offering surrender is defined here: the player must
    // trail the leading opponent by at least 6 influence points. This is
    // logged in DECISIONS.md as the B5-0679 threshold definition.
    //
    // Scoring principle: surrender is never a routine influence move. It is
    // offered only from a genuinely hopeless position. The +3 influence that
    // lands on the opponent is a cost — HARD explicitly avoids feeding a
    // winning rival by penalizing surrender-to-leader.
    // (leadingPlayer is the pre-existing HEAD helper at file tail — reused, not
    // duplicated, by the B5-0679 offer gating and scorers.)

    /** B5-0679: MEDIUM surrender score. Positive only when the player is
     *  losing by the threshold (>= 6 behind the leader). The score decreases
     *  as the gap grows (a 6-point gap is the most urgent), and surrender to
     *  the leader is penalized because the +3 influence would feed the rival
     *  who is already winning. */
    private int scoreSurrenderMedium(GameState state, Player p, Player target) {
        Player leader = leadingPlayer(state, p);
        int gap = leader.getInfluence() - p.getInfluence();
        if (gap < 6) return -1;  // not losing badly enough
        int score = 10 - gap;     // 6..4 for gaps 6..10+ (higher = more urgent)
        if (target == leader) score -= 5;  // don't feed the leader
        return score;
    }

    /** B5-0679: HARD surrender score. Same threshold gating, but HARD weighs
     *  the +3-influence swing explicitly: surrendering to a winning rival is
     *  strongly disfavored because it widens their lead. */
    private double scoreSurrenderHard(GameState state, Player p, Player target) {
        Player leader = leadingPlayer(state, p);
        int gap = leader.getInfluence() - p.getInfluence();
        if (gap < 6) return -1.0;  // not losing badly enough
        double base = 5.0 - gap * 0.5;  // decreases with gap
        if (target == leader) base -= 4.0;  // strong penalty for feeding the leader
        return base;
    }

    /** B5-0403: the highest cumulative mercenary bid on `merc` by any player
     *  other than p (0 when none) — the total a winning bid must strictly
     *  exceed (B5-0395 resolution: ties crown nobody). */
    private int bestOtherMercenaryBid(GameState state, Player p, Card merc) {
        int bestOther = 0;
        for (Player q : state.getPlayers()) {
            if (q == p) continue;
            int bid = state.getMercenaryBid(merc, q);
            if (bid > bestOther) bestOther = bid;
        }
        return bestOther;
    }

    /** B5-0453: station-aware war scoring (B5-0437 hooks live, B5-0354
     *  no-rewire respected — READ-ONLY over the station ratings; the AI
     *  never mutates them and Support Babylon 5 is untouched).
     *
     *  Returns a score additive term based on the station's current state:
     *  0  — all ratings below 15 (today's always-case: pool starts inert,
     *       so existing orderings are preserved when nothing has moved);
     *  -1 — Shadow War active (shadow or vorlon at 20): station influence
     *       can no longer crown (RulesEngine.checkVictory guards on
     *       isShadowWar), so pushing it is wasted motion;
 *       +2 — station influence at 15+: a capture now pushes the rating
 *       toward the condition-2 threshold (20) the AI's influence already
 *       targets;
 *       +1 — shadow or vorlon at 15+: capture also feeds the Shadow-War
 *       trigger threshold, which is board state the AI can exploit.
     */
    public int stationContextScore(GameState state) {
        Babylon5Station stn = state.getStation();
        if (stn.isShadowWar()) return -1;
        if (stn.getInfluence() >= 15) return +2;
        if (stn.getShadowInfluence() >= 15 || stn.getVorlonInfluence() >= 15) return +1;
        return 0;
    }

    /**
     * B5-0635: MEDIUM major-victory urgency. Nonzero only when p sits in
     * major territory (influence >= 10, under the build cap) AND the :182
     * 10-point lead is not yet met — the closer the needed swing (10 minus
     * the closest rival gap), the stronger the pull toward influence-moving
     * actions. Once the threshold is met (need <= 0) the win is already in
     * hand and urgency drops to 0; Shadow War switches the term off entirely
     * (victory runs only through the major path there). Forfeited rivals are
     * excluded, matching the engine victory comparison. Mirrors
     * stationContextScore's additive style (B5-0453 house precedent).
     * Bands: need 1..2 -> 3, 3..5 -> 2, 6..10 -> 1.
     */
    public int majorProximityMedium(GameState state, Player p) {
        if (state.getStation().isShadowWar()) return 0;
        if (p.getInfluence() < 10) return 0;
        int closestGap = Integer.MAX_VALUE;
        for (Player q : state.getPlayers()) {
            if (q == p || q.hasForfeited()) continue;
            int gap = p.getInfluence() - q.getInfluence();
            if (gap < closestGap) closestGap = gap;
        }
        if (closestGap == Integer.MAX_VALUE) return 0;   // every rival forfeited
        int need = 10 - closestGap;                       // points of lead still needed
        if (need <= 0) return 0;                          // :182 lead already met
        if (need <= 2) return 3;
        if (need <= 5) return 2;
        return 1;                                         // need 6..10 (tied at 10)
    }

    /**
     * B5-0635: HARD urgency — same shape, finer quarters. Bands: need 1..2
     * -> 1.5, 3..4 -> 1.0, 5..7 -> 0.5, 8..10 -> 0.25, met -> 0.
     */
    public double majorProximityHard(GameState state, Player p) {
        if (state.getStation().isShadowWar()) return 0.0;
        if (p.getInfluence() < 10) return 0.0;
        int closestGap = Integer.MAX_VALUE;
        for (Player q : state.getPlayers()) {
            if (q == p || q.hasForfeited()) continue;
            int gap = p.getInfluence() - q.getInfluence();
            if (gap < closestGap) closestGap = gap;
        }
        if (closestGap == Integer.MAX_VALUE) return 0.0;
        int need = 10 - closestGap;
        if (need <= 0) return 0.0;
        if (need <= 2) return 1.5;
        if (need <= 4) return 1.0;
        if (need <= 7) return 0.5;
        return 0.25;
    }

    // ── B5-0727: Civil War context (rulebook :990–:1009; B5-0691 engine law) ──
    //
    // Zero-cost invariance: every band returns 0 on today's pool (no card
    // invokes unrest or any Civil War axis — the B5-0669 card census), so no
    // ordering moves on the standard board and EASY stays uniform through
    // easyChoose (difficulty contract, B5-0351). MEDIUM and HARD share these
    // helpers; EASY's paths are never touched.

    /** Unrest pressure (:888/:278/:280 band 1..5): 0 at 1..3, 1 at 4, 2 at 5. */
    public static int unrestPressure(Player p) {
        int u = p.getUnrest();
        if (u >= 5) return 2;
        if (u == 4) return 1;
        return 0;
    }

    /** True when the player's race currently stands in Civil War. */
    public static boolean raceInCivilWar(GameState state, Player p) {
        CivilWarState cws = state.civilWarOfRace(p.getRace());
        return cws != null && cws.getPhase() == CivilWarState.Phase.CIVIL_WAR;
    }

    /** :992 end-of-turn entry risk — some other faction of the player's race
     *  holds same-race tension 5 toward a rival faction of the same race
     *  (a UNIFIED dual race only; a race already at war returns false). */
    public static boolean civilWarEntryRisk(GameState state, Player p) {
        CivilWarState cws = state.civilWarOfRace(p.getRace());
        if (cws != null && cws.getPhase() == CivilWarState.Phase.CIVIL_WAR) return false;
        List<Player> racePlayers = state.playersOfRace(p.getRace());
        if (racePlayers.size() < 2) return false;
        for (Player a : racePlayers) {
            for (Player b : racePlayers) {
                if (a != b && state.getSameRaceTension(a, b) >= 5) return true;
            }
        }
        return false;
    }

    /** :1000 merge exposure for this faction of a war-split race — how far
     *  its split tensions could swing the race's exit tension toward one
     *  target race (worst case, max row − min row). */
    public static int mergeExposure(CivilWarState cws, Player factionOfRace) {
        Map<Race, Integer> row = cws.getSplitTensions(factionOfRace);
        int low  = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (Integer v : row.values()) {
            if (v == null) continue;
            if (v.intValue() < low)  low  = v.intValue();
            if (v.intValue() > high) high = v.intValue();
        }
        if (low == Integer.MAX_VALUE) return 0;
        return high - low;
    }

    /** Combined merge-exposure scorer term: the :1000 swing only exists while
     *  the race is mid-war; 0 otherwise (zero-cost invariance). */
    public static int civilWarMergeExposureTerm(GameState state, Player p) {
        CivilWarState cws = state.civilWarOfRace(p.getRace());
        if (cws == null || cws.getPhase() != CivilWarState.Phase.CIVIL_WAR) return 0;
        return mergeExposure(cws, p);
    }

    // ── B5-1708: Promotion economics (rulebook §VI) ─────────────────

    /** Net influence delta of promoting supporting character ch into
     *  the Inner Circle (rulebook §VI, "ACTION: Promote a Character
     *  to the Inner Circle"): the supporting-role bonus minus the
     *  promotion cost.
     *
     *  The supporting-role bonus is the ability the character seats as
     *  an Inner-Circle member: its best effective primary ability
     *  across Diplomacy, Intrigue and Psi — the abilities an
     *  unrotated Inner-Circle member feeds into the non-Military
     *  conflict totals (Player.conflictTotal). Military is excluded
     *  (B5-0337): character Leadership never enters Military totals
     *  directly, only through the fleet-leader relation.
     *
     *  The promotion cost is RulesEngine.promotionCost — the
     *  character's influence cost, doubled for other-race loyalty,
     *  plus one per existing Inner Circle member including the
     *  ambassador (rulebook §VI). RulesEngine stays the legality and
     *  cost authority; this is the ACTION-phase decision aid.
     *
     *  Positive delta: a profitable influence trade. Negative: the
     *  promotion spends more influence than the seated ability is
     *  worth. A character outside the supporting role (or a null
     *  argument) is not promotable — its delta is undefined and
     *  reported as the floor.
     */
    public int evaluateCharacterCardPromotion(Player p, CharacterCard ch) {
        if (p == null || ch == null || !p.getSupportingRole().contains(ch)) {
            return Integer.MIN_VALUE;
        }
        int diplomacy = p.effectiveStat(ch.getId(), StatKey.DIPLOMACY,
                                        ch.getDiplomacy(), true);
        int intrigue  = p.effectiveStat(ch.getId(), StatKey.INTRIGUE,
                                        ch.getIntrigue(), true);
        int psi       = p.effectiveStat(ch.getId(), StatKey.PSI,
                                        ch.getPsi(), true);
        int bonus = Math.max(Math.max(diplomacy, intrigue), psi);
        return bonus - rules.promotionCost(p, ch);
    }

    /** B5-1708: the offer list with negative-delta PROMOTE_CHARACTER
     *  actions removed (see evaluateCharacterCardPromotion). PASS and
     *  every other action type pass through untouched, so the list
     *  keeps the always-legal PASS entry and is never emptied. */
    private List<GameAction> pruneUnprofitablePromotions(
            List<GameAction> legal, Player p) {
        List<GameAction> kept = new ArrayList<GameAction>();
        for (GameAction a : legal) {
            if (a.getType() == GameAction.Type.PROMOTE_CHARACTER
                    && a.getCard() instanceof CharacterCard
                    && evaluateCharacterCardPromotion(p,
                            (CharacterCard) a.getCard()) < 0) {
                continue;
            }
            kept.add(a);
        }
        return kept;
    }

    // ── B5-4102: Build-influence economics (rulebook §V) ─────────────

    /** Applied influence a Build Influence action must apply (rulebook
     *  :673, "applying 3 influence"; RulesEngine.canBuildInfluence gates
     *  the applied pool at this floor and executeBuildInfluence pays it
     *  via Player.applyInfluence). */
    private static final int BUILD_INFLUENCE_APPLIED_COST = 3;

    /** Permanent Influence Rating gained (rulebook :673, "increase its
     *  Influence Rating by 1"; executeBuildInfluence pays it via
     *  Player.gainInfluence, which credits rating and applied pool). */
    private static final int BUILD_INFLUENCE_RATING_GAIN = 1;

    /** Payback horizon, in rounds, used by evaluateBuildInfluence.
     *  Measured against the implemented D9 economy (B5-0342;
     *  Player.restoreAppliedPool resets the applied pool to the Rating
     *  at every round start): the action's own round loses
     *  (APPLIED_COST - RATING_GAIN) spendable pool, the next round is
     *  even (restore is one higher after a build), and every later
     *  round is +RATING_GAIN. Three rounds is therefore the exact
     *  break-even point (cumulative delta 0) and anything longer is
     *  pure gain — the horizon is arithmetic, not a tuning knob, and
     *  the interpretation is logged in docs/DECISIONS.md (B5-4102). */
    private static final int BUILD_INFLUENCE_PAYBACK_HORIZON_ROUNDS = 3;

    /** Net influence delta of Build Influence over the payback horizon
     *  (B5-4102; the BUILD_INFLUENCE twin of evaluateCharacterCardPromotion
     *  above). The row's complaint is that the AI scores this action
     *  blind-positive with no influence-cost side, so it cannot tell a
     *  losing trade; this helper is that missing side, stated in pure
     *  influence terms the same way the promotion helper is:
     *
     *  delta = horizon x RATING_GAIN - APPLIED_COST
     *
     *  = 0 today, i.e. every legal build is influence-neutral over the
     *  horizon and gains +1 pool per round thereafter, so the
     *  non-negative gate admits all of them. The delta is returned as
     *  the Integer.MIN_VALUE floor for every state where the engine
     *  would refuse the action anyway (null arguments, leader not an
     *  unrotated actionable Inner-Circle member, or a failed
     *  canBuildInfluence), mirroring the B5-1708 contract so a floor
     *  value can never be mistaken for a real measurement. If the
     *  engine's cost/gain constants ever invert, the horizon term
     *  turns negative here and MEDIUM/HARD stop offering the losing
     *  trade without any legality change (rulebook stays canonical;
     *  RulesEngine stays the cost authority). */
    public int evaluateBuildInfluence(Player p, CharacterCard leader) {
        if (p == null || leader == null
                || !p.getInnerCircle().contains(leader)
                || leader.isRotated()
                || !leader.canActAfterNeutralization()
                || !rules.canBuildInfluence(p)) {
            return Integer.MIN_VALUE;
        }
        return BUILD_INFLUENCE_PAYBACK_HORIZON_ROUNDS
                * BUILD_INFLUENCE_RATING_GAIN
                - BUILD_INFLUENCE_APPLIED_COST;   // 0 at break-even
    }

    /** B5-4102: the offer list with negative-delta BUILD_INFLUENCE
     *  actions removed (see evaluateBuildInfluence). PASS and every
     *  other action type pass through untouched, so the list keeps the
     *  always-legal PASS entry and is never emptied; EASY is never
     *  routed here (the difficulty contract, B5-0351 — its pass bias
     *  in easyChoose is untouched). */
    private List<GameAction> pruneUnprofitableBuilds(
            List<GameAction> legal, Player p) {
        List<GameAction> kept = new ArrayList<GameAction>();
        for (GameAction a : legal) {
            if (a.getType() == GameAction.Type.BUILD_INFLUENCE
                    && a.getCard() instanceof CharacterCard
                    && evaluateBuildInfluence(p,
                            (CharacterCard) a.getCard()) < 0) {
                continue;
            }
            kept.add(a);
        }
        return kept;
    }

    // ── EASY ──────────────────────────────────────────────────────────────────

    private GameAction easyChoose(List<GameAction> legal, Player p) {
        if (legal.size() > 1 && rng.nextInt(10) < 2) return GameAction.pass();
        return legal.get(rng.nextInt(legal.size()));
    }

    // ── MEDIUM ────────────────────────────────────────────────────────────────

    private GameAction mediumChoose(List<GameAction> legal, GameState state, Player p) {
        GameAction best     = GameAction.pass();
        int        bestScore = -1;

        for (GameAction a : legal) {
            int score = scoreActionMedium(a, state, p);
            if (score > bestScore) { bestScore = score; best = a; }
        }
        return best;
    }

    private int scoreActionMedium(GameAction a, GameState state, Player p) {
        switch (a.getType()) {
            case INITIATE_CONFLICT:
                if (a.getCard() instanceof ConflictCard) {
                    ConflictCard cc = (ConflictCard) a.getCard();
                    int myTotal = p.conflictTotal(cc.getConflictType());
                    int oppTotal = a.getTarget() != null
                        ? a.getTarget().conflictTotal(cc.getConflictType()) : 0;
                    int score = myTotal > oppTotal
                        ? cc.getInfluenceReward() * 10 + (myTotal - oppTotal)
                        : -5;
                    double winRate = (myTotal + 1.0)
                            / (myTotal + oppTotal + 2.0);
                    if (winRate < memory.getConflictInitiationThreshold()) {
                        score -= 10;
                    }
                    score += (int) Math.round((myTotal - oppTotal)
                            * memory.getRiskTolerance());
                    // B5-0727: Civil War context (:990–:1009) — unrest pressure
                    // adds urgency to any offer; a race already at war steers
                    // toward ENDING it (a win advances the :998 exit), and the
                    // :1000 rounded-up-average merge exposure discounts.
                    score += unrestPressure(p) - civilWarMergeExposureTerm(state, p);
                    if (raceInCivilWar(state, p)) score += 2;
                    // B5-1709: the difficulty's conflict-type preference
                    // (MEDIUM leans DIPLOMACY; CONFLICT_TYPE_WEIGHTS).
                    score += conflictTypeWeight(cc.getConflictType());
                    // B5-0453 station-context is on DECLARE_WAR_CONFLICT (the path
                    // that raises station influence via capture source), not here.
                    return score;
                }
                return 0;
            case RECRUIT_CHARACTER: {
                // B5-0324: prefer cheaper recruits (floored at 0; the offer
                // gate already guarantees affordability).
                if (a.getCard() instanceof CharacterCard) {
                    CharacterCard ch = (CharacterCard) a.getCard();
                    return Math.max(0, 5 - rules.recruitCost(p, ch));
                }
                return 5;
            }
            case PLAY_CARD: {
                // B5-0324: positional value minus the card's influence cost
                // (zero until the data backfill — ordering unchanged today).
                // B5-0344: agendas are scored by win-condition proximity
                // (see agendaProximityScore), not by a flat base.
                if (a.getCard() instanceof AgendaCard) {
                    AgendaCard ag = (AgendaCard) a.getCard();
                    return Math.max(0, (int) Math.round(
                            (agendaProximityScore(state, p, ag)
                            - a.getCard().getCost())
                            * memory.getCardValuationMultiplier()));
                }
                if (a.getCard() instanceof EventCard) {
                    return Math.max(0, (int) Math.round(
                            (2 + eventCatchUpBonus(state, p)
                            - a.getCard().getCost())
                            * memory.getCardValuationMultiplier()));
                }
                int base;
                if (a.getCard() instanceof LocationCard) base = 6;
                else if (a.getCard() instanceof GroupCard)    base = 4;
                else                                          base = 2;
                // B5-0453: station-aware scoring for location plays — a
                // location can be captured later, feeding the station capture
                // source (B5-0437 hooks). Add the station-context term so MEDIUM
                // and HARD both see the rating effect; EASY stays uniform.
                if (a.getCard() instanceof LocationCard) {
                    base += stationContextScore(state);
                }
                return Math.max(0, (int) Math.round(
                        (base - a.getCard().getCost())
                        * memory.getCardValuationMultiplier()));
            }
            case BUILD_INFLUENCE:
                // Positive value: pushing toward the Influence cap.
                // Score scales with how far below the cap we still are.
                // B5-0635: plus the major-victory urgency term (zero in
                // build territory — influence <= 9 — since the term gates
                // at influence >= 10; kept for shape uniformity).
                int remaining = Math.max(0, 10 - p.getInfluence());
                return 3 + remaining + majorProximityMedium(state, p); // 4..13
            case PROMOTE_CHARACTER:
                // B5-0321: builds the Inner Circle (more conflict power + the
                // deck-out buffer). Better early, when the IC-member cost term
                // is still small.
                return Math.max(1, 8 - p.getInnerCircle().size());
            case LEAD_FLEET: {
                // B5-0362: modest unused-leader value — seats Leadership that
                // currently contributes nothing (D5: characters never feed
                // MILITARY directly). Deliberately below the promote/build
                // peaks so leading is a good, not THE grab.
                if (!(a.getCard() instanceof FleetCard) || a.getLeader() == null) return 2;
                return 2 + p.effectiveStat(a.getLeader().getId(), StatKey.LEADERSHIP,
                        a.getLeader().getLeadership(), true);   // modest; includes active bonuses
            }
            case USE_ROTATE_EFFECT: {
                // B5-0366: modest value for the two player-chosen assistant
                // effects (rulebook §IV). The ability boost is worth a few
                // positional points (it adds +1 to up to 3 stats on the
                // ambassador); the sponsor discount is worth ~1 (it saves 1
                // influence on the next recruit). Below promote/build so the
                // AI leads with Inner-Circle growth first.
                if (!(a.getCard() instanceof CharacterCard) || a.getLeader() == null) return 1;
                GameAction.RotateEffectKind kind = a.getRotateKind();
                if (kind == GameAction.RotateEffectKind.USE_SPONSOR_DISCOUNT) return 1;
                // USE_ABILITY_BOOST: +1 Dip/Intrigue/Leadership on the ambassador
                // while the assistant stays rotated — worth a small positional bump.
                return 2;
            }
            case DISCARD_AGENDA:
                // B5-0364: agenda discard is rarely voluntary (Majors cannot
                // be discarded engine-side), so score it low — used only when
                // the current agenda is clearly worse than a replacement in hand.
                return 1;
            case REPLACE_AGENDA: {
                // B5-0364: agenda replacement — score by the gap between the
                // new agenda's proximity and zero (the current agenda's implicit
                // floor when it's being replaced). Costs one IC rotation.
                if (a.getCard() instanceof AgendaCard) {
                    return Math.max(0, agendaProximityScore(state, p, (AgendaCard) a.getCard()) - 1);
                }
                return 2;
            }
            case REVEAL_AGENDA:
                // B5-0364: agenda reveal — a hidden agenda has no effect until
                // revealed (:520), so this action converts a held resource into
                // an active win condition. Score by proximity (same reading as
                // agenda sponsor through PLAY_CARD).
                if (a.getCard() instanceof AgendaCard) {
                    return agendaProximityScore(state, p, (AgendaCard) a.getCard());
                }
                return 3;
            case PLAY_CONTINGENCY:
                // B5-0365: contingency placement — face-down resource under a
                // controlled host, waiting for its trigger. Modest value (it's
                // a future-proofing play, not an immediate effect).
                return 2;
            case REVEAL_CONTINGENCY:
                // B5-0365: contingency reveal — converts to an event-like effect
                // when the trigger is met. Score as an event (the B5-0365 design:
                // contingencies share the Event dispatcher on reveal).
                return 2;
            case JOIN_CONFLICT_SUPPORT:
            case JOIN_CONFLICT_OPPOSE:
                // B5-4157: the action-round join. The side is fixed
                // by the action type (the offer exists only when
                // decideJoinSide chose it); rank the join against the
                // round's other rotations by the win probability of
                // the side it would join — see joinConflictScore.
                return (int) Math.round(joinConflictScore(a, state, p));
            case PASS:
                return 0;
            case DECLARE_WAR_CONFLICT:
                // B5-0376: modest war-declaration scoring — race targets value
                // the +1 uncontested swing; location targets value the captured
                // per-round income (suppressed for the owner). Parked above the
                // generic floor so wars are preferred once they are legal.
                // B5-0453: MEDIUM shares the HARD station-context term for
                // location-target wars (B5-0437 hooks moved the ratings).
                if (a.getTargetCard() instanceof LocationCard) {
                    return 2 + ((LocationCard) a.getTargetCard()).getInfluencePerRound()
                            + stationContextScore(state);
                }
                // B5-0635: an uncontested race-target war moves influence on
                // BOTH sides (+1 own, -1 target) — the one influence-mover
                // offered above the build cap — so it carries the major
                // urgency term; location captures do not move influence.
                // B5-0727: :992 second sentence — declaring war on a UNIFIED
                // dual race of the SAME race can force its Civil War entry;
                // weigh unrest against the :1000 merge exposure. The leading-
                // rival branch above is left byte-identical.
                if (a.getTarget() != null && a.getTarget().getRace() == p.getRace()) {
                    return 4 + unrestPressure(p) - civilWarMergeExposureTerm(state, p);
                }
                return 4 + majorProximityMedium(state, p);
            case ATTACK_CONFLICT_PARTICIPANT:
                return (int) Math.round(damageAttackScore(a, state, p, false));
            case HEAL_CHARACTER: {
                CharacterCard ch = a.getCard() instanceof CharacterCard
                        ? (CharacterCard) a.getCard() : null;
                if (ch == null) return 0;
                int urgency = ch.isNeutralized() ? 8
                        : 2 + Math.min(5, ch.getDamageTokens() + ch.getSevereDamageTokens());
                return urgency;
            }
            case REPAIR_CARD: {
                Card c = a.getCard();
                if (c == null) return 0;
                return 3 + Math.min(6, c.getDamageTokens() * 2);
            }
            case BID_ON_MERCENARY: {
                // B5-0403: control value discounted by the bid cost, weighted
                // by the win probability of the projected total (the same
                // winProb idiom the HARD initiation/LEAD_FLEET paths use).
                // What a controlled mercenary is worth is card-specific (the
                // CardEffects mercenary action fires for the controller at
                // the MERCENARY phase) and not readable here, so the base is
                // the modest B5-0301 house value.
                Card merc = a.getCard();
                if (merc == null) return 0;
                int inc = a.getAmount();
                if (inc <= 0) return 0;
                int projected = state.getMercenaryBid(merc, p) + inc;
                double winProb = (projected + 1.0)
                        / (projected + bestOtherMercenaryBid(state, p, merc) + 2.0);
                return (int) Math.round(4.0 * winProb - inc * 0.5);
            }
            case SURRENDER:
                // B5-0679: offered only from a losing position (trailing the
                // leader by >= 6 influence). Value the exit from a certain
                // loss, penalized when the +3 swing feeds the leader.
                // B5-0727: unrest pressure adds urgency; a :1006 civil surrender
                // (an active race Civil War and a same-race target) takes a hard
                // discount — never leave your faction leaderless in a race at
                // war with itself.
                if (a.getTarget() != null) {
                    int cwScore = scoreSurrenderMedium(state, p, a.getTarget());
                    cwScore += unrestPressure(p);
                    if (raceInCivilWar(state, p)
                            && a.getTarget().getRace() == p.getRace()) {
                        cwScore -= 8;
                    }
                    return cwScore;
                }
                return -10;
            default:
                return 1;
        }
    }

    private boolean mediumJoin(GameState state, Player p, Conflict conflict) {
        // Superseded by mediumJoinSide (B5-0343); kept for reference.
        return mediumJoinSide(state, p, conflict) != 0;
    }

    /** B5-4157: projected value of an action-round join offer. The
     *  offer exists only when decideJoinSide chose this side, so the
     *  seat's posture (outweigh-oppose, free-ride-support, abstain,
     *  never-strengthen-the-leader, civil-war rules) is already
     *  baked in; this ranks the join against the round's other
     *  rotations. Win probability of the side p would join — the
     *  same Laplace-smoothed idiom the initiation and mercenary
     *  paths use — scaled by the conflict's influence reward.
     *  Opposition carries the full weight: the leading opposer wins
     *  the conflict outright (RulesEngine.resolveConflict), so a
     *  strong opposition take is a reward p can win itself. Support
     *  carries half: the reward lands on the initiator, and p's
     *  share is the strategic tilt (an opposition loss discards its
     *  committed supporting characters at resolution). */
    private double joinConflictScore(GameAction a, GameState state, Player p) {
        Conflict qc = state.getActiveConflict();
        if (qc == null || qc.isResolved()) return 0.0;
        ConflictType type = qc.getConflictType();
        boolean support = a.getType() == GameAction.Type.JOIN_CONFLICT_SUPPORT;
        int supportTotal = 0;
        int opposeTotal = 0;
        for (Player participant : qc.getParticipants()) {
            int total = participant.conflictTotal(type);
            if (qc.isSupporting(participant)) supportTotal += total;
            else opposeTotal += total;
        }
        int myTotal = p.conflictTotal(type);
        int mySide    = support ? supportTotal + myTotal : opposeTotal + myTotal;
        int theirSide = support ? opposeTotal : supportTotal;
        double winProb = (mySide + 1.0) / (mySide + theirSide + 2.0);
        double reward  = qc.getInfluenceReward();
        return support
                ? 1.0 + 0.5 * winProb * (reward + 2.0)
                : winProb * (reward + 2.0);
    }

    // ── HARD ──────────────────────────────────────────────────────────────────

    private GameAction hardChoose(List<GameAction> legal, GameState state, Player p) {
        Player leader = leadingPlayer(state, p);
        GameAction best     = GameAction.pass();
        double     bestScore = Double.NEGATIVE_INFINITY;

        for (GameAction a : legal) {
            double score = scoreActionHard(a, state, p, leader);
            if (score > bestScore) { bestScore = score; best = a; }
        }
        return best;
    }

    private double scoreActionHard(GameAction a, GameState state, Player p, Player leader) {
        switch (a.getType()) {
            case INITIATE_CONFLICT: {
                if (!(a.getCard() instanceof ConflictCard)) return 0;
                ConflictCard cc  = (ConflictCard) a.getCard();
                Player       opp = a.getTarget();
                int myTotal      = p.conflictTotal(cc.getConflictType());
                int oppTotal     = opp != null ? opp.conflictTotal(cc.getConflictType()) : 0;
                double winProb   = (myTotal + 1.0) / (myTotal + oppTotal + 2.0);
                double base      = winProb * cc.getInfluenceReward();
                // B5-0343: pick targets WEAKER than the auto-leader — the
                // pre-0343 +3 leaderBonus steered initiation AT the leading
                // player (feeding the strongest opponent influence rewards);
                // now leading the board is a penalty, and weak targets score
                // higher through the win-probability term.
                double leaderPenalty = (opp == leader) ? -5.0 : 0;
                double lossPenalty   = myTotal < oppTotal ? -3.0 : 0;
                // B5-2001 / AIJ: HARD initiates against the weak target, never
                // the leader. Weak target = lower conflict total than leader.
                double weakTargetBonus = 0.0;
                if (opp != null && leader != null && opp != leader) {
                    int oppInf = opp.getInfluence();
                    int leaderInf = leader.getInfluence();
                    if (oppInf < leaderInf) weakTargetBonus = 2.0;
                }
                // B5-0344: conflicts a WIN would make aftermath-eligible are
                // worth more (the controller auto-plays eligible aftermaths
                // right after resolution). Rewards on the initiator's side.
                base += aftermathAnticipationBonus(state, p, cc);
                // B5-0727: Civil War context (:990–:1009) — unrest pressure adds
                // urgency to any offer; a race already at war steers toward
                // ENDING it (a win advances the :998 exit), and the :1000
                // rounded-up-average merge exposure discounts.
                base += unrestPressure(p) - civilWarMergeExposureTerm(state, p);
                if (raceInCivilWar(state, p)) base += 2.0;
                // B5-1709: the difficulty's conflict-type preference
                // (HARD leans MILITARY; CONFLICT_TYPE_WEIGHTS).
                base += conflictTypeWeight(cc.getConflictType());
                // B5-1974: opponent threat, as TWO terms rather than one.
                // The urgency term says initiate BECAUSE this opponent is
                // dangerous; the tilt says on WHICH axis. Without the threat
                // term the AI never reacts to an opponent closing on its own
                // agenda win condition, and without the tilt it would answer
                // every threat with the same card.
                Player threatTarget = opp != null ? opp : leadingPlayer(state, p);
                int threat = threatAssessment(state, p, threatTarget);
                base += 0.5 * threat;
                base += threatConflictTypeTilt(cc.getConflictType(), threat,
                        p.conflictTotal(ConflictType.MILITARY),
                        threatTarget.conflictTotal(ConflictType.MILITARY));
                return base + leaderPenalty + lossPenalty + weakTargetBonus;
            }
            case RECRUIT_CHARACTER: {
                if (!(a.getCard() instanceof CharacterCard)) return 3;
                CharacterCard ch = (CharacterCard) a.getCard();
                int maxStat = Math.max(Math.max(p.effectiveStat(ch.getId(), StatKey.DIPLOMACY, ch.getDiplomacy(), true), p.effectiveStat(ch.getId(), StatKey.INTRIGUE, ch.getIntrigue(), true)),
                              Math.max(p.effectiveStat(ch.getId(), StatKey.PSI, ch.getPsi(), true), p.effectiveStat(ch.getId(), StatKey.LEADERSHIP, ch.getLeadership(), true)));
                return 4 + maxStat * 0.5 - rules.recruitCost(p, ch); // B5-0324
            }
            case PLAY_CARD: {
                // B5-0324: positional value minus the card's influence cost.
                // B5-0344: agenda proximity + event catch-up (see helpers).
                double base;
                if (a.getCard() instanceof AgendaCard) {
                    base = agendaProximityScore(state, p, (AgendaCard) a.getCard());
                } else if (a.getCard() instanceof EventCard) {
                    base = 3 + eventCatchUpBonus(state, p);
                } else if (a.getCard() instanceof LocationCard) base = 7;
                else if (a.getCard() instanceof GroupCard)    base = 5;
                else if (a.getCard() instanceof EnhancementCard) base = 4;
                else                                          base = 2;
                return (base - a.getCard().getCost())
                        * memory.getCardValuationMultiplier();
            }
            case BUILD_INFLUENCE:
                // B5-0324: capped value — HARD avoids over-tinging the score table.
                // Still positive since pushing toward the Influence cap is useful.
                // B5-0635: plus the HARD major-victory urgency term.
                // B5-0727: in a race Civil War (:994) an unopposed brother faction
                // seizes the race-major seat; staying out of the fight is a strong
                // negative (+8 swing over the incumbent MEDIUM/HARD join posture).
                int depr = Math.max(0, 10 - p.getInfluence());
                return 0.25 * depr + majorProximityHard(state, p)
                        + (raceInCivilWar(state, p) ? 8.0 : 0.0);
            case PROMOTE_CHARACTER: {
                // B5-0321: value the new member's best stat, discounted by the
                // influence spent (raw promotion cost).
                if (!(a.getCard() instanceof CharacterCard)) return 1.0;
                CharacterCard ch = (CharacterCard) a.getCard();
                int maxStat = Math.max(Math.max(p.effectiveStat(ch.getId(), StatKey.DIPLOMACY, ch.getDiplomacy(), true), p.effectiveStat(ch.getId(), StatKey.INTRIGUE, ch.getIntrigue(), true)),
                              Math.max(p.effectiveStat(ch.getId(), StatKey.PSI, ch.getPsi(), true), p.effectiveStat(ch.getId(), StatKey.LEADERSHIP, ch.getLeadership(), true)));
                return 2.0 + maxStat * 0.5 - rules.promotionCost(p, ch);
            }
            case LEAD_FLEET: {
                // B5-0362: unused-leader base + the projected initiative —
                // the same win-probability reading scoreActionHard uses for
                // its own conflict-initiation calculation, evaluated WITH the
                // leader's Military seated (offer precondition fl unled, so
                // the delta is exactly this character's Leadership).
                if (!(a.getCard() instanceof FleetCard) || a.getLeader() == null) return 1.5;
                CharacterCard lch = a.getLeader();
                int leadership = p.effectiveStat(lch.getId(), StatKey.LEADERSHIP,
                        lch.getLeadership(), true);
                double base    = 1.5 + leadership * 0.5;
                int proj       = p.conflictTotal(ConflictType.MILITARY) + leadership;
                int opp        = leader.conflictTotal(ConflictType.MILITARY);
                double winProb = (proj + 1.0) / (proj + opp + 2.0);
                return base + 2.0 * winProb;
            }
            case USE_ROTATE_EFFECT: {
                // B5-0366: HARD scores the two assistant effects modestly.
                // The ability boost is worth a small positional term; the
                // sponsor discount is worth ~1. Deterministic (no RNG), same
                // list-order tie-breaking as every other action type.
                if (!(a.getCard() instanceof CharacterCard) || a.getLeader() == null) return 0.5;
                GameAction.RotateEffectKind kind = a.getRotateKind();
                if (kind == GameAction.RotateEffectKind.USE_SPONSOR_DISCOUNT) return 0.5;
                return 1.0;   // USE_ABILITY_BOOST
            }
            case DISCARD_AGENDA:
                // B5-0364: agenda discard is rarely voluntary, scored low.
                return 0.5;
            case REPLACE_AGENDA: {
                // B5-0364: replacement value = proximity to new agenda's win
                // condition minus the IC rotation cost (1.0). Negative when the
                // new agenda is distant, positive when close.
                if (a.getCard() instanceof AgendaCard) {
                    return agendaProximityScore(state, p, (AgendaCard) a.getCard()) - 1.0;
                }
                return 1.5;
            }
            case REVEAL_AGENDA: {
                // B5-0364: hidden-to-active conversion — score by proximity
                // (same as the sponsor path, since this IS the activation).
                if (a.getCard() instanceof AgendaCard) {
                    return agendaProximityScore(state, p, (AgendaCard) a.getCard()) * 1.0;
                }
                return 2.5;
            }
            case PLAY_CONTINGENCY:
                // B5-0365: face-down future-proofing resource. Modest positional
                // value (no immediate effect).
                return 1.5;
            case REVEAL_CONTINGENCY:
                // B5-0365: trigger-activated event-like effect. Score as a
                // modest event (the reveal is player-chosen timing, not automatic).
                return 2.0;
            case JOIN_CONFLICT_SUPPORT:
            case JOIN_CONFLICT_OPPOSE:
                // B5-4157: same projection as MEDIUM (see
                // joinConflictScore); HARD's chooser ranks doubles.
                return joinConflictScore(a, state, p);
            case PASS:
                // B5-0727: the Civil War mirror of the BUILD_INFLUENCE guard —
                // abstaining from a race at war with itself forfeits the race-
                // major seat to the unopposed brother faction (same +8 swing).
                return -0.5 + (raceInCivilWar(state, p) ? 8.0 : 0.0);
            case DECLARE_WAR_CONFLICT:
                // B5-0376: HARD weighs the war payoff — location income when
                // capturing a location, plus a preference to hit the leading
                // enemy race. Kept modest (B5-0301 house style).
                // B5-0453: station-context additive term on location captures
                // (B5-0437 hooks moved the ratings; see stationContextScore).
                if (a.getTargetCard() instanceof LocationCard) {
                    LocationCard loc = (LocationCard) a.getTargetCard();
                    double base = 2.0 + loc.getInfluencePerRound()
                                  + stationContextScore(state);
                    if (loc.getLoyalty() != null
                            && leader != null && Loyalty.fromRace(leader.getRace()) == loc.getLoyalty()) base += 1.0;
                    return base;
                }
                // B5-0635: race-target wars carry the HARD major urgency term
                // (influence swing on both sides; see the MEDIUM branch).
                if (a.getTarget() != null
                        && leader != null && a.getTarget().getRace() == leader.getRace()) {
                    return 5.0 + majorProximityHard(state, p);
                }
                // B5-0727: :992 second sentence — declaring war on a UNIFIED
                // dual race of the SAME race can force its Civil War entry;
                // weigh unrest against the :1000 merge exposure. The leading-
                // rival branch above is left byte-identical.
                if (a.getTarget() != null && a.getTarget().getRace() == p.getRace()) {
                    return 4.0 + unrestPressure(p) - civilWarMergeExposureTerm(state, p);
                }
                return 4.0 + majorProximityHard(state, p);
            case ATTACK_CONFLICT_PARTICIPANT:
                return damageAttackScore(a, state, p, true);
            case HEAL_CHARACTER: {
                CharacterCard ch = a.getCard() instanceof CharacterCard
                        ? (CharacterCard) a.getCard() : null;
                if (ch == null) return 0;
                // Prefer restoring a neutralized IC leader; avoid spending the
                // action on a lightly damaged supporting card when help exists.
                return (ch.isNeutralized() ? 9.0 : 2.0)
                        + Math.min(4, ch.getDamageTokens() + ch.getSevereDamageTokens());
            }
            case REPAIR_CARD: {
                Card c = a.getCard();
                if (c == null) return 0;
                double value = 2.0 + Math.min(5, c.getDamageTokens() * 1.5);
                if (c instanceof FleetCard && c.getDamageTokens() > 0) value += 1.0;
                return value;
            }
            case BID_ON_MERCENARY: {
                // B5-0403: HARD weighs control higher and charges the bid at
                // a steeper opportunity cost (bids spend the D9 applied pool
                // only — the Rating is never touched, per B5-0395).
                Card merc = a.getCard();
                if (merc == null) return 0.0;
                int inc = a.getAmount();
                if (inc <= 0) return 0.0;
                int projected = state.getMercenaryBid(merc, p) + inc;
                double winProb = (projected + 1.0)
                        / (projected + bestOtherMercenaryBid(state, p, merc) + 2.0);
                return 5.0 * winProb - inc * 0.75;
            }
            case SURRENDER:
                // B5-0679: HARD surrenders only from a losing position,
                // preferring to surrender to a non-leader war opponent (don't
                // feed the leader +3 influence).
                // B5-0727: unrest pressure adds urgency; a :1006 civil surrender
                // (an active race Civil War and a same-race target) takes a hard
                // discount — never leave your faction leaderless in a race at
                // war with itself.
                if (a.getTarget() != null) {
                    double cwScoreHard = scoreSurrenderHard(state, p, a.getTarget());
                    cwScoreHard += unrestPressure(p);
                    if (raceInCivilWar(state, p)
                            && a.getTarget().getRace() == p.getRace()) {
                        cwScoreHard -= 8.0;
                    }
                    return cwScoreHard;
                }
                return -5.0;
            default:
                return 1;
        }
    }

    /** Score a legal attack by expected damage and retaliation risk. HARD
     * additionally avoids exposing a high-value leader as the attacker. */
    private double damageAttackScore(GameAction a, GameState state, Player p, boolean hard) {
        Card attacker = a.getCard();
        Card target = a.getTargetCard();
        if (attacker == null || target == null) return 0;
        Conflict conflict = state.getActiveConflict();
        if (conflict == null) return 0;
        int dealt = attacker.getAttackDamage(conflict.getConflictType());
        int risk = target.getAttackDamage(conflict.getConflictType());
        double value = dealt - risk * 0.5;
        if (hard) {
            if (attacker instanceof CharacterCard && p.getAmbassador() == attacker) value -= 2.0;
            if (target instanceof CharacterCard) {
                CharacterCard tc = (CharacterCard) target;
                if (p.getInnerCircle().contains(tc)) value -= 3.0;
                if (tc.isNeutralized()) value -= 5.0;
            }
        }
        return value;
    }
    private boolean hardJoin(GameState state, Player p, Conflict conflict) {
        // Superseded by hardJoinSide (B5-0343); kept for reference.
        return hardJoinSide(state, p, conflict) != 0;
    }

    // ── B5-0344: agenda/aftermath/event scoring helpers ─────────────────────

    /**
     * How close p's board state is to winning through agenda ag (0..9,
     * keyed on the winConditionKey vocabulary implemented in
     * AgendaCard.isConditionMet):
     *   INFLUENCE_20       → share of the 20-power bar reached;
     *   MILITARY_SUPREMACY → best rival's Military vs own (rulebook:
     *                        "more than any other player");
     *   MOST_INNER_CIRCLE  → best rival's IC size vs own;
     * unknown keys fall back to the influence reading (the same fallback
     * isConditionMet uses). Score 9 means the agenda's condition is met —
     * playing it wins the game at the next victory check — so MEDIUM/HARD
     * always take a winning agenda.
     */
    public static int agendaProximityScore(GameState state, Player p, AgendaCard ag) {
        String key = ag.getWinConditionKey();
        if ("MILITARY_SUPREMACY".equals(key)) {
            int myMil = 0;
            for (FleetCard f : p.getFleets()) { f.setOwner(p); myMil += f.getEffectiveMilitary(); }
            int best = 0;
            for (Player q : state.getPlayers()) {
                if (q == p) continue;
                int mil = 0;
                for (FleetCard f : q.getFleets()) { f.setOwner(q); mil += f.getEffectiveMilitary(); }
                if (mil > best) best = mil;
            }
            if (myMil > best) return 9;
            return Math.min(8, myMil * 4 / (best + 1));
        }
        if ("MOST_INNER_CIRCLE".equals(key)) {
            int mySize = p.getInnerCircle().size();
            int best = 0;
            for (Player q : state.getPlayers()) {
                if (q == p) continue;
                if (q.getInnerCircle().size() > best) best = q.getInnerCircle().size();
            }
            if (mySize > best) return 9;
            return Math.min(8, mySize * 8 / (best + 1));
        }
        return Math.min(9, p.getPower() / 2);
    }

    /**
     * Conflicts a WIN would make aftermath-eligible are worth more: the
     * controller auto-plays eligible aftermaths right after resolution
     * (resolveCurrentConflict). Projects this player winning the conflict
     * and counts in-hand aftermaths eligible under that win (initiator
     * always participates; target rule approximated — the conflict's final
     * participant set is unknown at initiation time).
     */
    private double aftermathAnticipationBonus(GameState state, Player p, ConflictCard cc) {
        double bonus = 0;
        for (Card c : p.getHand()) {
            if (c instanceof AftermathCard) {
                AftermathCard am = (AftermathCard) c;
                if (am.isEligible(true, true, cc.getConflictType())) bonus += 3.0;
            }
        }
        return bonus;
    }

    /**
     * Events are timing cards (played from the hand, resolved immediately):
     * a player behind the leader gets extra value from the disruption/tempo
     * an event buys (rulebook framing: events bend the story of the trailing
     * faction). Leading players score no bonus.
     */
    private int eventCatchUpBonus(GameState state, Player p) {
                // B5-0703: MEDIUM now reads Power (influence + POWER-tagged bonus
                // total) through getPower() so a player carrying a POWER bonus is
                // scored as stronger than his raw influence, and a player whose
                // POWER bonus has gone negative is scored as weaker. Zero-cost
                // invariance is preserved — today's all-zero POWER total keeps the
                // pre-0703 ordering byte-identical (getPower == getInfluence when
                // no bonus is present). EASY stays uniform through easyChoose
                // (difficulty contract, B5-0351) and is not touched here.
                int myPower = p.getPower();
                int bestOther = -1;
                for (Player q : state.getPlayers()) {
                    if (q == p) continue;
                    int qPower = q.getPower();
                    if (qPower > bestOther) bestOther = qPower;
                }
                if (bestOther < 0 || myPower >= bestOther) return 0;
                return Math.min(3, bestOther - myPower);
    }

    // ── B5-1974: opponent threat assessment (conflict initiation) ───────────
    //
    // The row names "AIDecisionEngine", a class that does not exist anywhere in
    // this tree; AIPlayer IS the AI's decision surface, so this landed here.
    // See the report for that adjudication.
    //
    // What the pre-B5-1974 INITIATE_CONFLICT score could not read:
    //
    //   * the OPPONENT's proximity to its own agenda's win condition -- the
    //     sharpest "act now" signal, and absent entirely before, because
    //     majorProximityHard reads the INFLUENCE race, not the agenda;
    //   * the opponent's hand depth -- how many answers it is holding;
    //   * the opponent's fleet Military, which is the entire MILITARY axis
    //     (Player.conflictTotal reads fleets for MILITARY and committed
    //     characters for the other three types -- B5-0337 audit D5).
    //
    // Zero-cost invariance: all four terms are strict comparisons against the
    // assessing player, so on a symmetric opening board every term is 0 and
    // the pre-B5-1974 ordering is byte-identical. EASY is untouched entirely
    // (difficulty contract, B5-0351); only the HARD scorer reads these.

    /** Threat band: 0 (no threat) .. 10 (act now). Composed of four terms. */
    public static final int MAX_THREAT = 10;

    /**
     * B5-1974: how dangerous is this opponent right now, from `self`'s point of
     * view. Band 0..{@link #MAX_THREAT}, composed of the row's four inputs --
     * agenda proximity to victory (0..4), fleet strength (0..2), hand depth
     * (0..2), influence race (0..2). Null self or opponent reads 0 rather than
     * throwing, because this is called from a scoring path.
     */
    public static int threatAssessment(GameState state, Player self, Player opponent) {
        if (state == null || self == null || opponent == null || self == opponent) return 0;
        int threat = 0;

        // 1. Agenda proximity to the opponent's OWN win condition. Only its
        //    own agenda counts: another faction's agenda is not its threat.
        AgendaCard og = opponent.getAgenda();
        if (og != null) threat += agendaProximityScore(state, opponent, og) / 2;

        // 2. Fleet strength (0..2), every 4 Military of excess.
        int oppMil = opponent.conflictTotal(ConflictType.MILITARY);
        int myMil  = self.conflictTotal(ConflictType.MILITARY);
        if (oppMil > myMil) threat += Math.min(2, (oppMil - myMil) / 4);

        // 3. Hand depth (0..2), every 2 cards of excess.
        int oppHand = opponent.getHand().size();
        int myHand  = self.getHand().size();
        if (oppHand > myHand) threat += Math.min(2, (oppHand - myHand) / 2);

        // 4. Influence race (0..2), every 4 Power of excess.
        int oppPow = opponent.getPower();
        int myPow  = self.getPower();
        if (oppPow > myPow) threat += Math.min(2, (oppPow - myPow) / 4);

        return Math.min(MAX_THREAT, threat);
    }

    /**
     * B5-1974: which conflict AXIS the threat argues for, as an additive score
     * term. Deliberately separate from the urgency term: "this opponent is
     * dangerous, so act" and "act on this axis" are different claims, and
     * folding them together would make every conflict card equally attractive
     * against a threatening board.
     *
     * <p>Grounded in Player.conflictTotal (B5-0337 audit D5): MILITARY totals
     * fleets only, so a fleet-heavy opponent IS that axis and the existing
     * win-probability term already charges for it -- the tilt moves the choice
     * off it. PSI and INTRIGUE total committed characters, which the enemy's
     * fleet board does not inflate, so they gain slightly. DIPLOMACY gains least:
     * the loser penalties in CardEffects are board pressure rather than a race
     * on an axis.
     *
     * <p>Zero-cost invariance: threat 0 returns 0.0 for every type.
     */
    public static double threatConflictTypeTilt(ConflictType type, int threat,
                                                int selfMil, int oppMil) {
        if (type == null || threat <= 0) return 0.0;
        double scale = threat / (double) MAX_THREAT;
        if (type == ConflictType.MILITARY) {
            return oppMil > selfMil ? -1.0 * scale : 0.0;
        }
        if (type == ConflictType.PSI || type == ConflictType.INTRIGUE) return 0.5 * scale;
        if (type == ConflictType.DIPLOMACY) return 0.25 * scale;
        return 0.0;
    }

    private Player leadingPlayer(GameState state, Player self) {
        Player leader = self;
        int bestInf = -1;
        for (Player p : state.getPlayers()) {
            if (p == self) continue;
            int inf = p.getPower();
            if (inf > bestInf) { bestInf = inf; leader = p; }
        }
        return leader;
    }
}
