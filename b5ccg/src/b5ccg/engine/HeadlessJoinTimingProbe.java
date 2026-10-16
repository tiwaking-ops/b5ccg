package b5ccg.engine;

import b5ccg.model.*;
import b5ccg.model.enums.*;
import b5ccg.ai.AIPlayer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * B5-4160 — engine join-timing fix: conflicts are queued at declaration
 * (not resolved synchronously) and resolved at the end of the action
 * round via resolveQueuedConflicts, in declaration order. The AI join
 * collection that used to live inside resolveCurrentConflict is removed —
 * joins are action-round rotate acts (B5-4157). The initiator's ambassador
 * is no longer auto-committed at declaration.
 *
 * <p>Run: {@code java -cp out b5ccg.engine.HeadlessJoinTimingProbe}
 * Exit: 0 all checks pass, 1 otherwise.
 */
public class HeadlessJoinTimingProbe {

    private static int checks = 0;
    private static int failed = 0;

    private static void check(String label, boolean ok) {
        checks++;
        System.out.println("  [" + (ok ? "PASS" : "FAIL") + "] " + label);
        if (!ok) failed++;
    }

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

    private static GameState state(Player... seats) {
        List<Player> players = new ArrayList<Player>();
        for (Player p : seats) players.add(p);
        GameState g = new GameState(players);
        for (Player p : seats) p.setGameState(g);
        return g;
    }

    private static ConflictCard conflictCard(String id, int reward) {
        return new ConflictCard(id, "Probe Conflict " + id,
                "CONFLICT_DIPLOMACY", Rarity.COMMON, Faction.ANY,
                CardSet.PREMIERE, "x", "", ConflictType.DIPLOMACY, reward);
    }

    private static Method getProcessAction() throws Exception {
        Method m = GameController.class.getDeclaredMethod(
                "processAction", Player.class, GameAction.class);
        m.setAccessible(true);
        return m;
    }

    private static Method getResolveQueued() throws Exception {
        Method m = GameController.class.getDeclaredMethod("resolveQueuedConflicts");
        m.setAccessible(true);
        return m;
    }

    private static void processAction(GameController gc, Player p, GameAction action) {
        try {
            getProcessAction().invoke(gc, p, action);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void resolveQueued(GameController gc) {
        try {
            getResolveQueued().invoke(gc);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("B5-4160: join-timing — queued declaration, deferred resolution");

        // ── S1: declareConflictCard queues the conflict, does not resolve ──
        {
            Player init = seat("T1Init", Faction.MINBARI, 3);
            GameState g = state(init);
            g.setPhase(GamePhase.ACTION);
            ConflictCard cc = conflictCard("t1", 2);
            init.addToHand(cc);
            RulesEngine rules = new RulesEngine();
            rules.startRound(g);

            GameController gc = new GameController(g,
                    new ArrayList<AIPlayer>(),
                    new GameStateCallback() {
                        public void accept(GameState gs) { }
                    });

            processAction(gc, init, GameAction.initiateConflict(cc, null));

            Conflict c = g.getActiveConflict();
            check("S1: conflict queued (activeConflict set after initiate)",
                    c != null);
            check("S1: conflict is in the queue",
                    g.getQueuedConflicts().size() == 1
                    && g.getQueuedConflicts().get(0) == c);
            check("S1: initiator's hand no longer has the conflict card",
                    !init.getHand().contains(cc));
        }

        // ── S2: initiator ambassador is NOT auto-committed at declaration ──
        {
            Player init = seat("T2Init", Faction.MINBARI, 3);
            GameState g = state(init);
            g.setPhase(GamePhase.ACTION);
            ConflictCard cc = conflictCard("t2", 2);
            init.addToHand(cc);
            RulesEngine rules = new RulesEngine();
            rules.startRound(g);

            GameController gc = new GameController(g,
                    new ArrayList<AIPlayer>(),
                    new GameStateCallback() {
                        public void accept(GameState gs) { }
                    });

            processAction(gc, init, GameAction.initiateConflict(cc, null));

            Conflict c = g.getActiveConflict();
            check("S2: initiator ambassador NOT auto-committed at declaration",
                    c != null && c.getCommittedCards(init).isEmpty());
            check("S2: initiator is on support side but with no committed cards",
                    c != null && c.isSupporting(init)
                    && c.getCommittedCards(init).size() == 0);
        }

        // ── S3: no AI join auto-collected inside resolveCurrentConflict ──
        {
            Player init = seat("T3Init", Faction.MINBARI, 3);
            Player joiner = seat("T3Join", Faction.NARN, 6);
            GameState g = state(init, joiner);
            g.setPhase(GamePhase.ACTION);
            ConflictCard cc = conflictCard("t3", 2);
            init.addToHand(cc);
            RulesEngine rules = new RulesEngine();
            rules.startRound(g);

            List<AIPlayer> ais = new ArrayList<AIPlayer>();
            ais.add(new AIPlayer(init, AIDifficulty.MEDIUM));
            ais.add(new AIPlayer(joiner, AIDifficulty.MEDIUM));

            GameController gc = new GameController(g, ais,
                    new GameStateCallback() {
                        public void accept(GameState gs) { }
                    });

            processAction(gc, init, GameAction.initiateConflict(cc, null));

            Conflict c = g.getActiveConflict();
            check("S3: joiner NOT auto-collected during resolution setup (not yet resolved)",
                    c != null && !c.getParticipants().contains(joiner));

            resolveQueued(gc);

            check("S3: after resolution, joiner still NOT a participant (AI auto-join removed)",
                    !c.getParticipants().contains(joiner));
        }

        // ── S4: declaration order preserved across multiple queued conflicts ──
        // Uses two different initiators so each can initiate without
        // tripping the one-conflict-per-turn gate (hasInitiatedConflictThisTurn).
        {
            Player initA = seat("T4A", Faction.MINBARI, 3);
            Player initB = seat("T4B", Faction.NARN, 3);
            GameState g = state(initA, initB);
            g.setPhase(GamePhase.ACTION);
            ConflictCard cc1 = conflictCard("t4a", 1);
            ConflictCard cc2 = conflictCard("t4b", 1);
            initA.addToHand(cc1);
            initB.addToHand(cc2);
            RulesEngine rules = new RulesEngine();
            rules.startRound(g);

            GameController gc = new GameController(g,
                    new ArrayList<AIPlayer>(),
                    new GameStateCallback() {
                        public void accept(GameState gs) { }
                    });

            // First declaration — queued in slot 0
            processAction(gc, initA, GameAction.initiateConflict(cc1, null));
            Conflict c1 = g.getActiveConflict();
            check("S4: first conflict queued as activeConflict",
                    c1 != null);

            // Second declaration — queued behind the first
            processAction(gc, initB, GameAction.initiateConflict(cc2, null));
            Conflict c2 = g.getActiveConflict();
            check("S4: second conflict queued, becomes activeConflict",
                    c2 != null && c2 != c1);

            check("S4: two conflicts queued in declaration order",
                    g.getQueuedConflicts().size() == 2
                    && g.getQueuedConflicts().get(0) == c1
                    && g.getQueuedConflicts().get(1) == c2);

            resolveQueued(gc);

            check("S4: queue drained after resolveQueuedConflicts",
                    g.getQueuedConflicts().isEmpty());
            check("S4: active conflict is null after resolution",
                    g.getActiveConflict() == null);
        }

        // ── S5: join applied as action-round act during the ACTION phase ──
        {
            Player init = seat("T5Init", Faction.MINBARI, 3);
            Player joiner = seat("T5Join", Faction.NARN, 6);
            GameState g = state(init, joiner);
            g.setPhase(GamePhase.ACTION);
            ConflictCard cc = conflictCard("t5", 2);
            init.addToHand(cc);
            RulesEngine rules = new RulesEngine();
            rules.startRound(g);

            GameController gc = new GameController(g,
                    new ArrayList<AIPlayer>(),
                    new GameStateCallback() {
                        public void accept(GameState gs) { }
                    });

            processAction(gc, init, GameAction.initiateConflict(cc, null));

            Conflict c = g.getActiveConflict();
            check("S5: conflict queued, joiner not yet a participant",
                    c != null && !c.getParticipants().contains(joiner)
                    && c.getCommittedCards(joiner).isEmpty());

            processAction(gc, joiner, GameAction.joinSupport());

            check("S5: joiner joined as support during ACTION phase",
                    c.getParticipants().contains(joiner)
                    && c.isSupporting(joiner));
        }

        // ── S6: phase is ACTION (not CONFLICT) during the action round ──
        {
            Player init = seat("T6Init", Faction.MINBARI, 3);
            GameState g = state(init);
            g.setPhase(GamePhase.ACTION);
            ConflictCard cc = conflictCard("t6", 2);
            init.addToHand(cc);
            RulesEngine rules = new RulesEngine();
            rules.startRound(g);

            GameController gc = new GameController(g,
                    new ArrayList<AIPlayer>(),
                    new GameStateCallback() {
                        public void accept(GameState gs) { }
                    });

            processAction(gc, init, GameAction.initiateConflict(cc, null));

            check("S6: phase stays ACTION during the action round (joins are offered)",
                    g.getPhase() == GamePhase.ACTION);
            check("S6: active conflict is live during ACTION phase",
                    g.getActiveConflict() != null);
        }

        // ── S7: multiple queued conflicts resolved in declaration order ──
        // After resolution, c1 resolves first (initiator A wins on its own
        // commitment), then c2 resolves. Verify both are resolved and cleared.
        {
            Player initA = seat("T7A", Faction.MINBARI, 3);
            Player initB = seat("T7B", Faction.NARN, 3);
            GameState g = state(initA, initB);
            g.setPhase(GamePhase.ACTION);
            ConflictCard cc1 = conflictCard("t7a", 1);
            ConflictCard cc2 = conflictCard("t7b", 1);
            initA.addToHand(cc1);
            initB.addToHand(cc2);
            RulesEngine rules = new RulesEngine();
            rules.startRound(g);

            GameController gc = new GameController(g,
                    new ArrayList<AIPlayer>(),
                    new GameStateCallback() {
                        public void accept(GameState gs) { }
                    });

            processAction(gc, initA, GameAction.initiateConflict(cc1, null));
            processAction(gc, initB, GameAction.initiateConflict(cc2, null));

            check("S7: two conflicts queued before resolution",
                    g.getQueuedConflicts().size() == 2);

            resolveQueued(gc);

            check("S7: queue empty after resolution",
                    g.getQueuedConflicts().isEmpty());
            check("S7: active conflict null after resolution",
                    g.getActiveConflict() == null);
            check("S7: game not over (both sides had equal commitment)",
                    !g.isGameOver());
        }

        // ── Summary ─────────────────────────────────────────────────
        System.out.println("B5-4160 join-timing probe: " + checks
                + " checks, " + failed + " failures.");
        System.exit(failed == 0 ? 0 : 1);
    }
}
