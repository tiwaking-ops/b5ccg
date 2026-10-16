package b5ccg.ai;

import b5ccg.model.*;
import b5ccg.model.enums.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * B5-4157 — support and oppose as rotational action-round acts
 * (rulebook :574 and the :653 action list): a conflict in play
 * during the ACTION round is joined by rotation, as an action-round
 * act, instead of the declaration-time join window.
 *
 * <p>This pins the AI half of that contract on {@code AIPlayer}:
 * during the ACTION phase the offer builder adds
 * {@code JOIN_CONFLICT_SUPPORT} / {@code JOIN_CONFLICT_OPPOSE} for
 * the side {@code decideJoinSide} chooses (the same outweigh-oppose
 * / free-ride-support / abstain posture the declaration window
 * used), and MEDIUM ranks the join by the win probability of the
 * side it would join. The engine follow-up (keeping the declared
 * conflict in play through the action round) is a separate row; on
 * today's tree the ACTION phase carries no active conflict, so the
 * offer is dormant by construction — this probe drives the
 * post-follow-up shape directly, ACTION phase with a conflict in
 * play, through reflection on {@code buildLegalActions}, the exact
 * surface the choosers consume.
 *
 * <p>Run: {@code java -cp out b5ccg.ai.B54157ActionRoundJoinProbe}
 * Exit: 0 all checks pass, 1 otherwise.
 */
public class B54157ActionRoundJoinProbe {

    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [" + (ok ? "PASS" : "FAIL") + "] " + label);
        if (!ok) failed++;
    }

    // ── fixtures ──────────────────────────────────────────────────

    /** A seat whose only conflict ability is its ambassador's
     *  Diplomacy, so conflictTotal(DIPLOMACY) is exactly dip. */
    private static Player seat(String name, Faction f, int dip) {
        Player p = new Player(name, f, false);
        CharacterCard amb = new CharacterCard("amb_" + name,
                "Ambassador " + name, "CHARACTER_" + f, Rarity.FIXED,
                f, CardSet.PREMIERE, "x", "text",
                dip, 1, 1, 1, true);
        p.setAmbassador(amb);
        p.getInnerCircle().add(amb);
        p.setDeck(new Deck(new ArrayList<Card>()));
        p.restoreAppliedPool();
        return p;
    }

    private static ConflictCard conflictCard(String id, int reward) {
        return new ConflictCard(id, "Probe Conflict " + id,
                "CONFLICT_DIPLOMACY", Rarity.COMMON, Faction.ANY,
                CardSet.PREMIERE, "x", "", ConflictType.DIPLOMACY, reward);
    }

    private static GameState state(Player... seats) {
        List<Player> players = new ArrayList<Player>();
        for (Player p : seats) players.add(p);
        GameState g = new GameState(players);
        for (Player p : seats) p.setGameState(g);
        return g;
    }

    /** A DIPLOMACY conflict in play: the initiator seats its
     *  ambassador on the support side, as declareConflictCard does. */
    private static Conflict inPlay(ConflictCard card, Player initiator) {
        Conflict c = new Conflict(card, initiator);
        if (initiator.getAmbassador() != null
                && !initiator.getAmbassador().isFaceDown()) {
            c.commitCard(initiator, initiator.getAmbassador(), true);
        }
        return c;
    }

    private static List<GameAction> offers(AIPlayer ai, GameState g, Player p)
            throws Exception {
        Method build = AIPlayer.class.getDeclaredMethod(
                "buildLegalActions", GameState.class, Player.class);
        build.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<GameAction> actions = (List<GameAction>) build.invoke(ai, g, p);
        return actions;
    }

    private static boolean offered(List<GameAction> actions,
            GameAction.Type type) {
        for (GameAction a : actions) {
            if (a.getType() == type) return true;
        }
        return false;
    }

    private static int mediumScore(AIPlayer ai, GameAction a,
            GameState g, Player p) throws Exception {
        Method score = AIPlayer.class.getDeclaredMethod("scoreActionMedium",
                GameAction.class, GameState.class, Player.class);
        score.setAccessible(true);
        return (Integer) score.invoke(ai, a, g, p);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("B5-4157: support/oppose as action-round acts (AI)");

        // ── S1: a strong seat rotates to OPPOSE (outweigh) ────────
        // joiner 6 vs initiator 2: mediumJoinSide returns -1.
        {
            Player init = seat("S1Init", Faction.MINBARI, 2);
            Player join = seat("S1Join", Faction.NARN, 6);
            GameState g = state(init, join);
            g.setPhase(GamePhase.ACTION);
            g.setActiveConflict(inPlay(conflictCard("s1", 3), init));
            AIPlayer ai = new AIPlayer(join, AIDifficulty.MEDIUM);
            List<GameAction> actions = offers(ai, g, join);
            check("S1: OPPOSE offered to a stronger seat (outweigh)",
                    offered(actions, GameAction.Type.JOIN_CONFLICT_OPPOSE));
            check("S1: SUPPORT not offered to a stronger seat",
                    !offered(actions, GameAction.Type.JOIN_CONFLICT_SUPPORT));
            check("S1: PASS still offered (difficulty contract)",
                    offered(actions, GameAction.Type.PASS));
        }

        // ── S2: a weak seat rotates to SUPPORT (free-ride) ────────
        // joiner 1 vs initiator 6: 1*2 < 6, mediumJoinSide returns +1.
        {
            Player init = seat("S2Init", Faction.MINBARI, 6);
            Player join = seat("S2Join", Faction.NARN, 1);
            GameState g = state(init, join);
            g.setPhase(GamePhase.ACTION);
            g.setActiveConflict(inPlay(conflictCard("s2", 3), init));
            AIPlayer ai = new AIPlayer(join, AIDifficulty.MEDIUM);
            List<GameAction> actions = offers(ai, g, join);
            check("S2: SUPPORT offered to a weaker seat (free-ride)",
                    offered(actions, GameAction.Type.JOIN_CONFLICT_SUPPORT));
            check("S2: OPPOSE not offered to a weaker seat",
                    !offered(actions, GameAction.Type.JOIN_CONFLICT_OPPOSE));
        }

        // ── S3: the middle band abstains ──────────────────────────
        // joiner 3 vs initiator 3: neither outweigh nor free-ride.
        {
            Player init = seat("S3Init", Faction.MINBARI, 3);
            Player join = seat("S3Join", Faction.NARN, 3);
            GameState g = state(init, join);
            g.setPhase(GamePhase.ACTION);
            g.setActiveConflict(inPlay(conflictCard("s3", 3), init));
            AIPlayer ai = new AIPlayer(join, AIDifficulty.MEDIUM);
            List<GameAction> actions = offers(ai, g, join);
            check("S3: no join offer in the abstain band",
                    !offered(actions, GameAction.Type.JOIN_CONFLICT_SUPPORT)
                    && !offered(actions, GameAction.Type.JOIN_CONFLICT_OPPOSE));
        }

        // ── S4: a seat that already joined may not join again ─────
        {
            Player init = seat("S4Init", Faction.MINBARI, 2);
            Player join = seat("S4Join", Faction.NARN, 6);
            GameState g = state(init, join);
            g.setPhase(GamePhase.ACTION);
            Conflict c = inPlay(conflictCard("s4", 3), init);
            c.addParticipant(join, false);   // joined at declaration time
            g.setActiveConflict(c);
            AIPlayer ai = new AIPlayer(join, AIDifficulty.MEDIUM);
            List<GameAction> actions = offers(ai, g, join);
            check("S4: an existing participant is offered no join",
                    !offered(actions, GameAction.Type.JOIN_CONFLICT_SUPPORT)
                    && !offered(actions, GameAction.Type.JOIN_CONFLICT_OPPOSE));
        }

        // ── S5: the initiator is never offered a join on its own ──
        {
            Player init = seat("S5Init", Faction.MINBARI, 6);
            Player join = seat("S5Join", Faction.NARN, 2);
            GameState g = state(init, join);
            g.setPhase(GamePhase.ACTION);
            g.setActiveConflict(inPlay(conflictCard("s5", 3), init));
            AIPlayer ai = new AIPlayer(init, AIDifficulty.MEDIUM);
            List<GameAction> actions = offers(ai, g, init);
            check("S5: the initiator is offered no join on its own conflict",
                    !offered(actions, GameAction.Type.JOIN_CONFLICT_SUPPORT)
                    && !offered(actions, GameAction.Type.JOIN_CONFLICT_OPPOSE));
        }

        // ── S6: the declaration window owns the CONFLICT phase ────
        {
            Player init = seat("S6Init", Faction.MINBARI, 2);
            Player join = seat("S6Join", Faction.NARN, 6);
            GameState g = state(init, join);
            g.setPhase(GamePhase.CONFLICT);
            g.setActiveConflict(inPlay(conflictCard("s6", 3), init));
            AIPlayer ai = new AIPlayer(join, AIDifficulty.MEDIUM);
            List<GameAction> actions = offers(ai, g, join);
            check("S6: no action-round join offer outside the ACTION phase",
                    !offered(actions, GameAction.Type.JOIN_CONFLICT_SUPPORT)
                    && !offered(actions, GameAction.Type.JOIN_CONFLICT_OPPOSE));
        }

        // ── S7: today's tree — ACTION with no conflict in play ────
        // (dormant until the engine follow-up keeps the conflict in
        // play through the action round).
        {
            Player init = seat("S7Init", Faction.MINBARI, 2);
            Player join = seat("S7Join", Faction.NARN, 6);
            GameState g = state(init, join);
            g.setPhase(GamePhase.ACTION);
            AIPlayer ai = new AIPlayer(join, AIDifficulty.MEDIUM);
            List<GameAction> actions = offers(ai, g, join);
            check("S7: no join offer when no conflict is in play",
                    !offered(actions, GameAction.Type.JOIN_CONFLICT_SUPPORT)
                    && !offered(actions, GameAction.Type.JOIN_CONFLICT_OPPOSE));
        }

        // ── S8: a resolved conflict draws no join ─────────────────
        {
            Player init = seat("S8Init", Faction.MINBARI, 2);
            Player join = seat("S8Join", Faction.NARN, 6);
            GameState g = state(init, join);
            g.setPhase(GamePhase.ACTION);
            Conflict c = inPlay(conflictCard("s8", 3), init);
            c.resolve(init);
            g.setActiveConflict(c);
            AIPlayer ai = new AIPlayer(join, AIDifficulty.MEDIUM);
            List<GameAction> actions = offers(ai, g, join);
            check("S8: a resolved conflict is offered no join",
                    !offered(actions, GameAction.Type.JOIN_CONFLICT_SUPPORT)
                    && !offered(actions, GameAction.Type.JOIN_CONFLICT_OPPOSE));
        }

        // ── S9: the score follows the side the posture chose ──────
        // Strong seat (6 vs 2, reward 3): the opposition take
        // outscores the support take (full weight vs half). Weak seat
        // (1 vs 6, reward 3): the free-ride support outscores a
        // hopeless opposition.
        {
            Player strongInit = seat("S9InitA", Faction.MINBARI, 2);
            Player strongJoin = seat("S9JoinA", Faction.NARN, 6);
            GameState gStrong = state(strongInit, strongJoin);
            gStrong.setPhase(GamePhase.ACTION);
            gStrong.setActiveConflict(
                    inPlay(conflictCard("s9a", 3), strongInit));
            AIPlayer aiStrong = new AIPlayer(strongJoin, AIDifficulty.MEDIUM);
            int opposeScore = mediumScore(aiStrong, GameAction.joinOppose(),
                    gStrong, strongJoin);
            int supportScore = mediumScore(aiStrong, GameAction.joinSupport(),
                    gStrong, strongJoin);
            check("S9: a strong seat scores OPPOSE above SUPPORT",
                    opposeScore > supportScore);
            check("S9: both join sides score positive",
                    opposeScore > 0 && supportScore > 0);

            Player weakInit = seat("S9InitB", Faction.MINBARI, 6);
            Player weakJoin = seat("S9JoinB", Faction.NARN, 1);
            GameState gWeak = state(weakInit, weakJoin);
            gWeak.setPhase(GamePhase.ACTION);
            gWeak.setActiveConflict(
                    inPlay(conflictCard("s9b", 3), weakInit));
            AIPlayer aiWeak = new AIPlayer(weakJoin, AIDifficulty.MEDIUM);
            int weakSupport = mediumScore(aiWeak, GameAction.joinSupport(),
                    gWeak, weakJoin);
            int weakOppose = mediumScore(aiWeak, GameAction.joinOppose(),
                    gWeak, weakJoin);
            check("S9: a weak seat scores SUPPORT above OPPOSE",
                    weakSupport > weakOppose);
        }

        // ── Summary ─────────────────────────────────────────────────
        System.out.println("B5-4157 action-round join probe: " + checks
                + " checks, " + failed + " failures.");
        System.exit(failed == 0 ? 0 : 1);
    }
}
