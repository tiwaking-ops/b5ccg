package b5ccg.model;

import b5ccg.model.enums.*;
import java.util.*;
import b5ccg.model.enums.TensionMatrix;

public class GameState {
    private final List<Player> players;
    private       int          currentPlayerIndex = 0;
    private       int          roundNumber        = 1;
    private       GamePhase    phase              = GamePhase.SETUP;
    private       Conflict     activeConflict;
    private       Player       winner;
    private final Set<Player> conflictsInitiatedThisTurn = new HashSet<Player>();

    // B5-0008 (WP-07/A5): game-level RNG seed. 0 = unseeded (legacy behaviour);
    // non-zero makes GameController derive per-AIPlayer streams (seed + i*1000L,
    // B5-0349 precedent) and lets harnesses seed per-player decks deliberately.
    // Recorded in the replay header (ReplayRecorder.setSeed). Set BEFORE
    // constructing the GameController.
    private long seed = 0L;
    public long getSeed()       { return seed; }
    public void setSeed(long s) { seed = s; }

    // ── Non-player forces (B5-0340 + 0428) ───────────────────────────────
    //    Shadow/Vorlon ratings and condition-2 state live on the station
    //    (B5-0340 + 0428 proposal); see Babylon5Station for the full surface.
    private final Babylon5Station station = new Babylon5Station();
    /** 0428 proposal §Sinks: set true when any station source fires this round;
     *  at the next round boundary, skip decay when true. */
    private boolean stationSourceFired = false;

    // B5-0338 (audit D4): aftermaths "in play" on a target, keyed by target
    // Player identity. The current engine resolves aftermath effects
    // immediately and discards the card, so nothing persists yet — the
    // registry exists so the persistent variant gets the one-named-per-target
    // guard the moment effects can attach; attachAftermath is authoritative.
    // B5-0637: the registry is no longer append-only — advanceRound() clears
    // it at the round boundary, so entries act as the D4 legality gate within
    // their own round and free their name at the boundary. The D4
    // one-named-per-target guard itself is unchanged.
    private final Map<Player, List<AftermathCard>> attachedAftermaths = new LinkedHashMap<Player, List<AftermathCard>>();

    // B5-1997: sustained actions registry — effects that persist across rounds
    // while the source card remains rotated. Keyed by source card ID for
    // efficient lookup when checking if a card is sustaining an action.
    private final Map<String, SustainedAction> sustainedActions = new LinkedHashMap<String, SustainedAction>();

    private final List<String> log = new ArrayList<String>();

    // B5-2553: structured game log (JSON per line) — toggleable, backward-compatible
    private final List<LogEntry> logEntries = new ArrayList<LogEntry>();
    private boolean structuredLogEnabled = false;

    public boolean isStationSourceFired() { return stationSourceFired; }
    public void setStationSourceFired(boolean v) { stationSourceFired = v; }

    // ── Structured log (B5-2553) ──────────────────────────────────────────────
    /** Enable/disable JSON-per-line structured log. When enabled, every call to
     *  log(String) also creates a LogEntry with Result.INFO. Explicit logEntry
     *  calls are recorded regardless of this flag. */
    public void setStructuredLogEnabled(boolean v) { structuredLogEnabled = v; }
    public boolean isStructuredLogEnabled() { return structuredLogEnabled; }

    /** Return unmodifiable snapshot of structured log entries. */
    public List<LogEntry> getLogEntries() {
        synchronized (this) {
            return Collections.unmodifiableList(new ArrayList<LogEntry>(logEntries));
        }
    }

    /** Record a structured log entry. Called by engine code at key game events. */
    public void logEntry(LogEntry.Result result, String actor, String action,
                         String target, String detail) {
        LogEntry e = new LogEntry(roundNumber, phase, actor, action, target, result, detail);
        synchronized (this) {
            logEntries.add(e);
        }
    }

    /** Overload for callers that want to omit target (pass ""). */
    public void logEntry(LogEntry.Result result, String actor, String action,
                         String detail) {
        logEntry(result, actor, action, "", detail);
    }

    public GameState(List<Player> players) {
        this.players = new ArrayList<Player>(players);
    }

    // ── Players ───────────────────────────────────────────────────────────────
    public List<Player> getPlayers()       { return Collections.unmodifiableList(players); }
    public Player getActivePlayer()        { return players.get(currentPlayerIndex); }
    public Player getHumanPlayer() {
        for (Player p : players) {
            if (p.isHuman()) return p;
        }
        return players.get(0);
    }

    public void advanceTurn() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
    }

    // ── Round ─────────────────────────────────────────────────────────────────
    public int       getRoundNumber() { return roundNumber; }
    public void      advanceRound()   { roundNumber++; currentPlayerIndex = 0; conflictsInitiatedThisTurn.clear(); stationSourceFired = false; clearAttachedAftermaths(); for (Player p : players) p.sweepBonusExpiries(roundNumber); }

    // ── Phase ─────────────────────────────────────────────────────────────────
    public GamePhase getPhase()          { return phase; }
    public void      setPhase(GamePhase p) { phase = p; log("Phase → " + p); }

    // ── Conflict ──────────────────────────────────────────────────────────────
    public Conflict getActiveConflict()              { return activeConflict; }
    public void     setActiveConflict(Conflict c)    { activeConflict = c; }
    public void     clearActiveConflict()            { activeConflict = null; }

    // ── Declared-conflict queue (rulebook III :366-:378) ───────────────────
    // The conflict round declares (then reveals/initiates) every conflict,
    // but nothing resolves until the Resolution round, which settles the
    // queue in declaration order. The single activeConflict slot above is the
    // conflict currently being declared or resolved — never the set of live
    // ones, which is what this queue is.
    private final List<Conflict> conflictQueue = new ArrayList<Conflict>();
    public List<Conflict> getQueuedConflicts()      { return conflictQueue; }
    public void           queueConflict(Conflict c) { if (c != null) conflictQueue.add(c); }
    public void           clearQueuedConflicts()    { conflictQueue.clear(); }

    // ── One-conflict-per-turn tracking (B5-0302; rulebook "Conflicts":
    //    "Each faction may normally initiate only one conflict per turn")
    public boolean hasInitiatedConflictThisTurn(Player p) { return conflictsInitiatedThisTurn.contains(p); }
    public void    markConflictInitiated(Player p)        { conflictsInitiatedThisTurn.add(p); }

    // ── War conflict tracking (B5-0376) ─────────────────────────────────────
    private final Set<Player> warConflictsInitiatedThisTurn = new HashSet<Player>();
    public boolean hasInitiatedWarConflictThisTurn(Player p) { return warConflictsInitiatedThisTurn.contains(p); }
    public void    markWarConflictInitiated(Player p)        { warConflictsInitiatedThisTurn.add(p); }
    public void    clearWarConflictInitiated(Player p)       { warConflictsInitiatedThisTurn.remove(p); }

    // ── Surrender tracking (B5-0661; rulebook :815–:821) ────────────────────
    private final Set<Player> surrenderedPlayers = new HashSet<Player>();
    public boolean hasSurrendered(Player p)      { return surrenderedPlayers.contains(p); }
    public void    markSurrendered(Player p)     { surrenderedPlayers.add(p); }
    public int     countSurrendered()            { return surrenderedPlayers.size(); }

    /** Returns the count of players who have neither forfeited nor surrendered. */
    private final TensionMatrix tensionMatrix = new TensionMatrix();
    /** B5-0691: Civil War machine per race — created on first dual-race entry
     *  attempt; absent for single-faction races, so the standard game never
     *  allocates one. */
    private final Map<Race, CivilWarState> civilWars =
            new LinkedHashMap<Race, CivilWarState>();
    public TensionMatrix getTensionMatrix()               { return tensionMatrix; }

    // ── B5-0691: Civil War machinery (rulebook :990–:1009; B5-0669 proposal
    //    §5.2 option b, §5.3, §5.4). Inert in the standard single-faction
    //    game: entry requires a dual race (two players of one faction), and
    //    the race-level TensionMatrix above is byte-identical to its pre-0691
    //    behaviour. ──

    /** Same-race tension substrate (§5.2 option b): parallel matrix keyed on
     *  player names — stable within a game — so same-race tension between two
     *  factions of one race is expressible without touching the landed
     *  race-level enum matrix (whose == identity discipline the B5-0358
     *  header documents). Clamped 1..5 per :280/:972 (starts at 2, :972). */
    private final Map<String, Map<String, Integer>> sameRaceTension =
            new LinkedHashMap<String, Map<String, Integer>>();

    private void sameRaceRow(Player source) {
        Map<String, Integer> row = sameRaceTension.get(source.getName());
        if (row == null) {
            row = new LinkedHashMap<String, Integer>();
            sameRaceTension.put(source.getName(), row);
        }
    }

    /** Raise same-race tension source→target (same Race players only),
     *  clamped 1..5. No-op across different races or for self. */
    public void raiseSameRaceTension(Player source, Player target, int delta) {
        if (source == null || target == null || source == target
                || source.getRace() != target.getRace()) return;
        sameRaceRow(source);
        Map<String, Integer> row = sameRaceTension.get(source.getName());
        Integer cur = row.get(target.getName());
        int v = (cur == null ? 2 : cur.intValue()) + delta;   // :972 start 2
        row.put(target.getName(), Integer.valueOf(Math.max(1, Math.min(5, v))));
    }

    /** Current same-race tension source→target (start value 2 when unset, :972). */
    public int getSameRaceTension(Player source, Player target) {
        if (source == null || target == null || source == target
                || source.getRace() != target.getRace()) return 0;
        Map<String, Integer> row = sameRaceTension.get(source.getName());
        Integer v = row == null ? null : row.get(target.getName());
        return v == null ? 2 : v.intValue();
    }

    /** Rulebook :974 — Non-Aggression auto-state between same-race factions
     *  while same-race tension <= 3 and the race is not in Civil War. */
    public boolean isNonAggression(Player a, Player b) {
        if (a == null || b == null || a.getRace() != b.getRace()) return false;
        CivilWarState cws = civilWarOfRace(a.getRace());
        if (cws != null && cws.getPhase() == CivilWarState.Phase.CIVIL_WAR) return false;
        return getSameRaceTension(a, b) <= 3;
    }

    /** The Civil War machine of one race, or null when the race has never
     *  had one built (single-faction races never do). */
    public CivilWarState civilWarOfRace(Race race) { return civilWars.get(race); }

    /** All races currently in Civil War (read-only view). */
    public Set<Race> civilWarRaces() { return Collections.unmodifiableSet(civilWars.keySet()); }

    /** All players of one race (synthetic dual-race boards expressible). */
    public List<Player> playersOfRace(Race race) {
        List<Player> out = new ArrayList<Player>();
        for (Player p : players) {
            if (p.getRace() == race) out.add(p);
        }
        return out;
    }

    /**
     * Rulebook :992 — attempt Civil War entry for `race`. Fires when two or
     * more players share the race, the race is UNIFIED, and some faction of
     * the race holds same-race tension 5 toward a rival faction of the same
     * race at the end of a turn. On entry: unrest +1 for every faction of the
     * race (:994) and the race-level tension snapshot per :1000.
     */
    public boolean enterCivilWarIfTriggered(Race race, int round) {
        List<Player> racePlayers = playersOfRace(race);
        if (racePlayers.size() < 2) return false;
        CivilWarState cws = civilWars.get(race);
        if (cws == null) {
            cws = new CivilWarState(race, racePlayers);
            civilWars.put(race, cws);
        }
        if (cws.getPhase() != CivilWarState.Phase.UNIFIED) return false;
        boolean atFive = false;
        for (Player a : racePlayers) {
            for (Player b : racePlayers) {
                if (a != b && getSameRaceTension(a, b) >= 5) atFive = true;
            }
        }
        if (!cws.enterCivilWar(round, atFive)) return false;
        cws.raiseUnrestOfRace();
        Map<Race, Integer> snapshot = new LinkedHashMap<Race, Integer>();
        for (Race f : Race.values()) {
            if (f != race) snapshot.put(f, Integer.valueOf(tensionMatrix.getTension(race, f)));
        }
        cws.snapshotSplitTensions(snapshot);
        log(race + " enters Civil War (rulebook :992); unrest +1 for every faction of the race.");
        return true;
    }

    /**
     * Rulebook :992 second sentence — direct entry by war declaration or any
     * other same-race war effect (the tension-5 end-of-turn path is
     * {@link #enterCivilWarIfTriggered}). Same preconditions otherwise: dual
     * race, currently UNIFIED. On entry: unrest +1 per faction (:994) and the
     * :1000 race-level tension snapshot.
     */
    public boolean forceCivilWarEntry(Race race, int round) {
        List<Player> racePlayers = playersOfRace(race);
        if (racePlayers.size() < 2) return false;
        CivilWarState cws = civilWars.get(race);
        if (cws == null) {
            cws = new CivilWarState(race, racePlayers);
            civilWars.put(race, cws);
        }
        if (!cws.enterByDeclaration(round)) return false;
        cws.raiseUnrestOfRace();
        Map<Race, Integer> snapshot = new LinkedHashMap<Race, Integer>();
        for (Race f : Race.values()) {
            if (f != race) snapshot.put(f, Integer.valueOf(tensionMatrix.getTension(race, f)));
        }
        cws.snapshotSplitTensions(snapshot);
        log(race + " enters Civil War by declaration (rulebook :992); unrest +1 for every faction of the race.");
        return true;
    }

    /**
     * Rulebook :998 — exit by war end: unrest −1 for every faction and the
     * split tensions merge as the ROUNDED-UP AVERAGE back into the race-level
     * matrix per target race (:1000). Returns the merged map (empty when the
     * race was not in Civil War).
     */
    public Map<Race, Integer> exitCivilWarByWarEnd(Race race) {
        CivilWarState cws = civilWars.get(race);
        if (cws == null || cws.getPhase() != CivilWarState.Phase.CIVIL_WAR) {
            return Collections.emptyMap();
        }
        Map<Race, Integer> merged = cws.exitByWarEnd();
        for (Map.Entry<Race, Integer> e : merged.entrySet()) {
            tensionMatrix.raiseTension(race, e.getKey(),
                    e.getValue().intValue() - tensionMatrix.getTension(race, e.getKey()));
        }
        log(race + " leaves Civil War; unrest -1 for every faction; split tensions merged (rounded-up average).");
        return merged;
    }

    /**
     * Rulebook :1006 — exit by unconditional surrender leaving one faction of
     * the race: unrest −1, no merge (the survivor's split row governs).
     */
    public Map<Race, Integer> exitCivilWarBySurrender(Race race, Player survivor) {
        CivilWarState cws = civilWars.get(race);
        if (cws == null || cws.getPhase() != CivilWarState.Phase.CIVIL_WAR) {
            return Collections.emptyMap();
        }
        Map<Race, Integer> row = cws.exitBySurrender(survivor);
        if (!row.isEmpty()) {
            log(race + " leaves Civil War by unconditional surrender; unrest -1; no tension merge.");
        }
        return row;
    }

    /**
     * Rulebook :980 — Joint Effects, the steady-state of the split: while a
     * race is UNIFIED, a military-conflict influence loss to one faction of
     * the race spills to EVERY faction of that race; while CIVIL_WAR, factions
     * are separate races and the loss lands on the loser alone.
     */
    public void applyRaceJointInfluenceLoss(Player loser, int amount) {
        if (amount <= 0) return;
        CivilWarState cws = civilWarOfRace(loser.getRace());
        if (cws != null && cws.getPhase() == CivilWarState.Phase.CIVIL_WAR) {
            if (isPlayerActive(loser)) {
                loser.loseInfluence(amount);
            }
            return;
        }
        List<Player> racePlayers = playersOfRace(loser.getRace());
        for (Player p : racePlayers) {
            if (isPlayerActive(p)) {
                p.loseInfluence(amount);
            }
        }
    }

    /**
     * Returns true if the player is still active in the game (has not forfeited
     * and has not surrendered). Consolidates the forfeited/surrendered check
     * used in multiple locations.
     */
    public boolean isPlayerActive(Player p) {
        return p != null && !p.hasForfeited() && !p.hasSurrendered();
    }

    public boolean        isAtWar(Race a, Race b)   { return tensionMatrix.isAtWar(a, b); }
    public boolean isAtWar(Race faction) {
        // true when faction is at war with any other faction
        for (Player p : players) {
            if (p.getRace() != null && p.getRace() != faction
                    && tensionMatrix.isAtWar(faction, p.getRace())) return true;
        }
        return false;
    }

    /** B5-0661: returns the number of players who are still active (neither
     *  forfeited nor surrendered). Used by checkVictory for the last-standing
     *  and all-surrendered Major Victory checks. */
    public int activePlayersCount() {
        int count = 0;
        for (Player p : players) {
            if (!p.hasForfeited() && !p.hasSurrendered()) count++;
        }
        return count;
    }
    public void raiseTension(Race source, Race target, int delta) {
        tensionMatrix.raiseTension(source, target, delta);
    }
    /** Returns the player who controls the given location, or null. */
    /** B5-0376 Phase B: current controller of a location — the capturing
     *  occupier when captured, else the owning player in a locations list. */
    public Player findLocationOwner(LocationCard loc) {
        Player capturer = loc.getCapturedBy();
        if (capturer != null) return capturer;
        for (Player p : players) {
            if (p.getLocations().contains(loc)) return p;
        }
        return null;
    }

    // ── Aftermaths in play on a target (B5-0338; rulebook Aftermath Cards,
    //    Bonuses and Blanking: "only one of each named aftermath may be in
    //    play on the same target" — audit D4). Empty until persistent
    //    effects exist; the attach guard is authoritative the moment they do.
    public List<AftermathCard> getAttachedAftermaths(Player target) {
        List<AftermathCard> l = attachedAftermaths.get(target);
        return l != null ? l : Collections.<AftermathCard>emptyList();
    }

    /** True when the named aftermath is NOT already in play on the target. */
    public boolean canAttachAftermath(AftermathCard a, Player target) {
        for (AftermathCard in : getAttachedAftermaths(target)) {
            if (in.getTitle().equals(a.getTitle())) return false;
        }
        return true;
    }

    /**
     * Attaches the aftermath to the target, enforcing D4 (one of each named
     * aftermath per target). Returns false (state unchanged) when a same-named
     * aftermath is already attached.
     */
    public boolean attachAftermath(AftermathCard a, Player target) {
        if (!canAttachAftermath(a, target)) return false;
        List<AftermathCard> l = attachedAftermaths.get(target);
        if (l == null) {
            l = new ArrayList<AftermathCard>();
            attachedAftermaths.put(target, l);
        }
        l.add(a);
        return true;
    }

    /**
     * B5-3867: detaches the given aftermath instance from the target,
     * freeing the D4 name slot (rulebook :428 limit is once per target PER
     * CONFLICT, not per round). Removal matches by instance identity, never
     * by title: two distinct copies of one aftermath are two legal cards, so
     * detaching one must never remove the other. Returns false (state
     * unchanged) when the card is null, the target is null, the target
     * holds no registry, or the exact instance is not attached.
     */
    public boolean detachAftermath(AftermathCard a, Player target) {
        if (a == null || target == null) return false;
        List<AftermathCard> l = attachedAftermaths.get(target);
        if (l == null) return false;
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i) == a) {
                l.remove(i);
                return true;
            }
        }
        return false;
    }

    /**
     * B5-0637: clears the aftermath registry (round-boundary hook, called
     * from advanceRound). Engine aftermath effects resolve immediately, so
     * entries persist only as the D4 legality gate within their round; the
     * boundary frees every attached name. Direct calls are idempotent.
     */
    public void clearAttachedAftermaths() { attachedAftermaths.clear(); }

    // ── Sustained actions (B5-1997) ───────────────────────────────────────────
    /**
     * Registers a new sustained action. Returns false if the source card is
     * already sustaining an action (one sustained action per source card).
     */
    public boolean registerSustainedAction(SustainedAction action) {
        if (action == null || action.getSourceCard() == null) return false;
        String sourceId = action.getSourceCard().getId();
        if (sustainedActions.containsKey(sourceId)) return false;
        sustainedActions.put(sourceId, action);
        return true;
    }

    /** Returns the sustained action for the given source card ID, or null. */
    public SustainedAction getSustainedAction(String sourceCardId) {
        return sustainedActions.get(sourceCardId);
    }

    /** True when the given card is currently sustaining an action. */
    public boolean isSustaining(Card sourceCard) {
        if (sourceCard == null || sourceCard.getId() == null) return false;
        return sustainedActions.containsKey(sourceCard.getId());
    }

    /**
     * Ends the sustained action for the given source card. Returns the
     * removed action, or null if none was registered.
     */
    public SustainedAction endSustainedAction(Card sourceCard) {
        if (sourceCard == null || sourceCard.getId() == null) return null;
        return sustainedActions.remove(sourceCard.getId());
    }

    /** Clears all sustained actions (called at game end). */
    public void clearAllSustainedActions() { sustainedActions.clear(); }

    /**
     * B5-2248: true when some live sustained action of the given type is
     * TARGETING this card.
     *
     * <p>Needed because the registry is keyed by SOURCE card id, so
     * {@link #getSustainedAction(String)} and {@link #isSustaining(Card)} can
     * only answer questions about the card doing the sustaining, never about
     * the card receiving the bonus. Rulebook :492 makes that distinction load
     * bearing: an assistant's ability bonus lasts only while the assistant
     * remains rotated, so the round-boundary reset has to know whether a
     * bonus it is about to drop was actually sustained by somebody.
     *
     * <p>Identity, not id, is the comparison: the registry holds the live card
     * objects, and a caller asking about a card it is holding must get the
     * same answer the sustaining path registered.
     */
    public boolean hasSustainedActionTargeting(Card target, SustainedActionType type) {
        if (target == null || type == null) return false;
        for (SustainedAction sa : sustainedActions.values()) {
            if (sa != null && sa.getType() == type && sa.getTargetCard() == target) {
                return true;
            }
        }
        return false;
    }

    // ── Contingencies in play (B5-0394) ─────────────────────────────────────
    /** Returns all face-down contingencies placed by the given player. */
    public List<ContingencyCard> getPlacedContingencies(Player p) {
        List<ContingencyCard> result = new ArrayList<ContingencyCard>();
        if (p == null) return result;
        collectContingenciesFromZone(p.getInnerCircle(), p, result);
        collectContingenciesFromZone(p.getSupportingRole(), p, result);
        collectContingenciesFromZone(p.getFleets(), p, result);
        collectContingenciesFromZone(p.getLocations(), p, result);
        collectContingenciesFromZone(p.getGroups(), p, result);
        collectContingenciesFromZone(p.getEnhancements(), p, result);
        Card agenda = p.getAgenda();
        if (agenda != null) collectContingenciesFromCard(agenda, p, result);
        return result;
    }

    /** Returns all face-down contingencies on the board (all players). */
    public List<ContingencyCard> getAllPlacedContingencies() {
        List<ContingencyCard> result = new ArrayList<ContingencyCard>();
        for (Player p : players) {
            result.addAll(getPlacedContingencies(p));
        }
        return result;
    }

    private void collectContingenciesFromZone(List<? extends Card> zone, Player owner, List<ContingencyCard> out) {
        if (zone == null) return;
        for (Card c : zone) {
            collectContingenciesFromCard(c, owner, out);
        }
    }

    private void collectContingenciesFromCard(Card c, Player owner, List<ContingencyCard> out) {
        if (c == null) return;
        List<ContingencyCard> contingencies = c.getContingencies();
        if (contingencies != null) {
            for (ContingencyCard cc : contingencies) {
                if (cc.getPlayedBy() == owner && !cc.isRevealed()) {
                    out.add(cc);
                }
            }
        }
    }

    // ── Mercenaries (B5-0395; rulebook §Mercenaries :735–:741) ──────────────
    // "Some cards in the game may be used each turn only by the player who
    // applies the most influence to control them." Control is per-turn:
    // bids accumulate through the ACTION phase, the highest cumulative total
    // controls at the MERCENARY phase, and everything clears at the next
    // round boundary (startRound). The offer list is a game-setup surface
    // (currently hydratable only by tests/synthetic fixtures — the pool
    // carries zero mercenary cards per B5-0386). Interpretation recorded in
    // DECISIONS: bids are turn-ordered actions (each bid is an action during
    // ACTION), cumulative per player; a tie for the highest total crowns
    // NOBODY (the mercenary does not act) — the rulebook is silent, and the
    // no-controller default matches the D12 "ties crown nobody" discipline.
    private final List<Card> mercenaryOffers = new ArrayList<Card>();
    private final Map<Card, Map<Player, Integer>> mercenaryBids =
            new LinkedHashMap<Card, Map<Player, Integer>>();
    private final Map<Card, Player> mercenaryControllers =
            new LinkedHashMap<Card, Player>();

    /** The cards currently offered for bidding (unmodifiable). */
    public List<Card> getMercenaryOffers() {
        return Collections.unmodifiableList(mercenaryOffers);
    }

    /** Offers a mercenary-flagged card for bidding this game. Refuses
     *  non-mercenary cards, nulls and duplicates (loudly for non-mercenary —
     *  a data/reference error the engine must not hide). */
    public boolean addMercenaryOffer(Card c) {
        if (c == null || mercenaryOffers.contains(c)) return false;
        if (!c.isMercenary()) {
            System.err.println("B5-0395: refused to offer non-mercenary card "
                    + c.getId() + " (mercenary flag absent).");
            return false;
        }
        mercenaryOffers.add(c);
        return true;
    }

    public boolean isMercenaryInPlay(Card c) {
        return c != null && mercenaryOffers.contains(c);
    }

    /** Cumulative total a single player has bid on c this turn. */
    public int getMercenaryBid(Card c, Player p) {
        if (c == null || p == null) return 0;
        Map<Player, Integer> per = mercenaryBids.get(c);
        Integer v = (per == null) ? null : per.get(p);
        return v == null ? 0 : v.intValue();
    }

    /** Total influence bid on c across all players this turn. */
    public int totalMercenaryBids(Card c) {
        if (c == null) return 0;
        Map<Player, Integer> per = mercenaryBids.get(c);
        if (per == null) return 0;
        int total = 0;
        for (Integer v : per.values()) total += v.intValue();
        return total;
    }

    /** Records a cumulative bid (amount > 0) from p on c. Returns false when
     *  the card is not offered or the amount is non-positive (engine callers
     *  gate affordability before reaching here). */
    public boolean placeMercenaryBid(Card c, Player p, int amount) {
        if (c == null || p == null || amount <= 0 || !isMercenaryInPlay(c)) return false;
        Map<Player, Integer> per = mercenaryBids.get(c);
        if (per == null) {
            per = new LinkedHashMap<Player, Integer>();
            mercenaryBids.put(c, per);
        }
        int prior = per.containsKey(p) ? per.get(p).intValue() : 0;
        per.put(p, Integer.valueOf(prior + amount));
        return true;
    }

    /** Rolls back the most recent `amount` bid from p on c — used only by the
     *  engine's defensive spend-failure path in executeBidOnMercenary. */
    public void placeMercenaryBidRollback(Card c, Player p, int amount) {
        if (c == null || p == null || amount <= 0) return;
        Map<Player, Integer> per = mercenaryBids.get(c);
        if (per == null || !per.containsKey(p)) return;
        int now = per.get(p).intValue();
        if (now <= amount) {
            per.remove(p);
        } else {
            per.put(p, Integer.valueOf(now - amount));
        }
    }

    /** Resolves control for every offered mercenary at the MERCENARY phase:
     *  the single player with the strictly-highest cumulative bid controls;
     *  ties (or no bids) crown nobody. Returns the resolved controllers as a
     *  snapshot copy for safe iteration while the phase runs. */
    public Map<Card, Player> resolveMercenaries() {
        Map<Card, Player> resolved = new LinkedHashMap<Card, Player>();
        for (Card c : mercenaryOffers) {
            Player controller = null;
            int best = 0;
            boolean tie = false;
            for (Player p : players) {
                int bid = getMercenaryBid(c, p);
                if (bid > best) {
                    best = bid;
                    controller = p;
                    tie = false;
                } else if (bid == best && bid > 0) {
                    tie = true;
                }
            }
            if (tie || best <= 0) controller = null;
            if (controller != null) {
                mercenaryControllers.put(c, controller);
                resolved.put(c, controller);
            }
        }
        return resolved;
    }

    /** The controller of mercenary c for the current turn (after resolution),
     *  or null when none (tie / no bids / not yet resolved). */
    public Player getMercenaryController(Card c) {
        return mercenaryControllers.get(c);
    }

    /** Current controllers for all resolved mercenaries (unmodifiable). */
    public Map<Card, Player> getMercenaryControllers() {
        return Collections.unmodifiableMap(mercenaryControllers);
    }

    /** Clears bids + control for a new turn; the offer list persists (it is a
     *  game-setup surface). Called from RulesEngine.startRound. */
    public void clearMercenaryState() {
        mercenaryBids.clear();
        mercenaryControllers.clear();
    }

    // ── Non-player forces (B5-0340 + 0428) ───────────────────────────────
    /** The Babylon 5 station (rulebook :153); exactly one per game.
     *  Shadow/Vorlon ratings and condition-2 state live on the station (B5-0340
     *  + 0428 proposal); GameState delegates to the station. */
    public Babylon5Station getStation()   { return station; }

    // ── 0428 proposal: shadow/vorlon delegation (station-owned) ───────────
    public int  getShadowInfluence()      { return station.getShadowInfluence(); }
    public int  getVorlonInfluence()      { return station.getVorlonInfluence(); }
    public void setShadowInfluence(int n) { station.setShadowInfluence(n); }
    public void setVorlonInfluence(int n) { station.setVorlonInfluence(n); }
    /** 0428 proposal: condition-2 state queries, delegated to station. */
    public boolean isShadowActive()  { return station.isShadowActive(); }
    public boolean isVorlonActive()  { return station.isVorlonActive(); }
    public boolean isHumanSecured()  { return station.isHumanSecured(); }
    public boolean isUncontested()   { return station.isUncontested(); }

    // ── B5-0340: Shadow War guard (condition 2 input) ─────────────────────
    // Delegate: shadowWar is now a station predicate, not a local field.
    public boolean isShadowWar() { return station.isShadowWar(); }

    // ── Victory ───────────────────────────────────────────────────────────────
    public Player  getWinner()            { return winner; }
    public boolean isGameOver()           { return winner != null; }
    public void    setWinner(Player p)    { winner = p; log("WINNER: " + p.getName()); }

    // ── Log ───────────────────────────────────────────────────────────────────
    // B5-3649 (re-applied 2026-10-12 after the periodic git reset --hard wiped the
    // first application): both log endpoints lock the SAME monitor (this). log()
    // appends under the lock; getLog() returns an unmodifiable SNAPSHOT copy taken
    // under the lock, so a concurrent append can never raise
    // ConcurrentModificationException in any reader. Single-threaded readers see an
    // identical unmodifiable view, so behavior is preserved exactly. Verified by a
    // two-thread harness (50k appends vs 100k snapshot iterations, 0 CME).
    public void log(String msg) {
        synchronized (this) {
            log.add("[R" + roundNumber + "] " + msg);
            if (structuredLogEnabled) {
                logEntries.add(new LogEntry(roundNumber, phase, "SYSTEM", "LOG", "", LogEntry.Result.INFO, msg));
            }
        }
    }
    public List<String> getLog()         { synchronized (this) { return Collections.unmodifiableList(new ArrayList<String>(log)); } }
    public String getLastLogEntry()      { return log.isEmpty() ? "" : log.get(log.size() - 1); }
}
