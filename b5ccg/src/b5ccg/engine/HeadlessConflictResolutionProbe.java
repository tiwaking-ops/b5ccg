package b5ccg.engine;

import b5ccg.model.CharacterCard;
import b5ccg.model.Conflict;
import b5ccg.model.ConflictCard;
import b5ccg.model.enums.Faction;
import b5ccg.model.GameState;
import b5ccg.model.Player;

import java.util.ArrayList;
import java.util.List;

import b5ccg.model.enums.Rarity;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.ConflictType;
import b5ccg.model.enums.WarKind;

/**
 * B5-0534: standalone scenario probe for conflict resolution sides rule.
 *
 * Verifies B5-0309: initiator wins iff support > opposition;
 * equal-or-more opposition -> leading opposer wins. Three scenarios:
 * initiator wins, initiator loses, tie goes to leading opposer.
 * Harness only — no game-logic edits.
 *
 * Run after compile.bat / compile.sh:
 *   java -cp b5ccg/out b5ccg.engine.HeadlessConflictResolutionProbe
 */
public class HeadlessConflictResolutionProbe {

    private static int checks;
    private static int failures;

    private static void check(String label, boolean passed) {
        checks++;
        System.out.println("  [CONFLICT-PROBE] " + label + ": "
                + (passed ? "PASS" : "FAIL"));
        if (!passed) failures++;
    }

    private static ConflictCard conflict(String id, String title,
            Faction faction, int reward) {
        return new ConflictCard(id, title, "CONFLICT_DIPLOMACY",
                Rarity.COMMON, faction, CardSet.PREMIERE,
                "x", "", ConflictType.DIPLOMACY, reward);
    }

    private static CharacterCard amb(String id, String title,
            Faction faction, int leadership, Player owner) {
        CharacterCard c = new CharacterCard(id, title, "CHARACTER_"
                + faction.name(), Rarity.COMMON, faction, CardSet.PREMIERE,
                "x", "", 1, 1, 0, leadership, false);
        c.setRotated(true);
        owner.getInnerCircle().add(c);
        owner.setAmbassador(c);
        return c;
    }

    public static void main(String[] args) {
        // ── Scenario 1: initiator wins (support > opposition) ──────────────
        {
            Player init = new Player("Init1", Faction.HUMAN, false);
            Player opp  = new Player("Opp1", Faction.MINBARI, false);
            List<Player> players = new ArrayList<Player>();
            players.add(init);
            players.add(opp);
            GameState g = new GameState(players);

            amb("i1amb", "Init Amb", Faction.HUMAN, 4, init);
            amb("o1amb", "Opp Amb", Faction.MINBARI, 4, opp);

            ConflictCard cc = conflict("cc1", "Probe Conflict 1", Faction.HUMAN, 2);
            Conflict c = new Conflict(cc, init, opp);

            // Commit ambassadors on their sides — needed for non-zero
            // support/opposition totals (the engine sums committed-card
            // primary stats, not side flags alone).
            c.commitCard(init, init.getAmbassador(), true);
            c.addParticipant(init, true);
            g.setActiveConflict(c);

            check("S1: conflict created with initiator + target",
                    c.getCard() == cc && c.getInitiator() == init && c.getTarget() == opp);
            check("S1: initiator supports own conflict",
                    c.getSupporters().contains(init)
                    && c.getOpposers().isEmpty());

            Player winner = new RulesEngine().resolveConflict(c, g);
            check("S1: initiator wins (1 support > 0 opposition)",
                    winner == init);
            check("S1: game not over", !g.isGameOver());
        }

        // ── Scenario 2: initiator loses (opposition > support) ─────────────
        {
            Player init = new Player("Init2", Faction.CENTAURI, false);
            Player opp  = new Player("Opp2", Faction.NARN, false);
            List<Player> players = new ArrayList<Player>();
            players.add(init);
            players.add(opp);
            GameState g = new GameState(players);

            amb("i2amb", "Init2 Amb", Faction.CENTAURI, 4, init);
            amb("o2amb", "Opp2 Amb", Faction.NARN, 4, opp);

            ConflictCard cc = conflict("cc2", "Probe Conflict 2", Faction.CENTAURI, 3);
            Conflict c = new Conflict(cc, init, opp);

            // Add a third player as second opposer.
            Player opp3 = new Player("Opp3", Faction.MINBARI, false);
            amb("o3amb", "Opp3 Amb", Faction.MINBARI, 4, opp3);

            c.commitCard(init, init.getAmbassador(), true);
            c.commitCard(opp, opp.getAmbassador(), false);
            c.commitCard(opp3, opp3.getAmbassador(), false);
            c.addParticipant(init, true);
            c.addParticipant(opp, false);
            c.addParticipant(opp3, false);
            g.setActiveConflict(c);

            check("S2: two opposers vs one supporter",
                    c.getSupporters().size() == 1
                    && c.getOpposers().size() == 2);

            Player winner = new RulesEngine().resolveConflict(c, g);
            check("S2: initiator loses (1 support < 2 opposition)",
                    winner == opp);
            check("S2: game not over", !g.isGameOver());
        }

        // ── Scenario 3: tie (support == opposition -> no crown) ────────────
        {
            Player init = new Player("Init3", Faction.CENTAURI, false);
            Player opp  = new Player("Opp3", Faction.NARN, false);
            List<Player> players = new ArrayList<Player>();
            players.add(init);
            players.add(opp);
            GameState g = new GameState(players);

            amb("i3amb", "Init3 Amb", Faction.CENTAURI, 4, init);
            amb("o4amb", "Opp3 Amb", Faction.NARN, 4, opp);

            ConflictCard cc = conflict("cc3", "Probe Conflict 3", Faction.CENTAURI, 3);
            Conflict c = new Conflict(cc, init, opp);

            c.commitCard(init, init.getAmbassador(), true);
            c.commitCard(opp, opp.getAmbassador(), false);
            c.addParticipant(init, true);
            c.addParticipant(opp, false);
            g.setActiveConflict(c);

            check("S3: tied sides (1 support, 1 oppose)",
                    c.getSupporters().size() == 1
                    && c.getOpposers().size() == 1);

            Player winner = new RulesEngine().resolveConflict(c, g);
            check("S3: tie -> leading opposer wins (not initiator)",
                    winner != null && winner == opp);
            check("S3: game not over after tied conflict", !g.isGameOver());
        }

        // ── Scenario 4 (B5-1705): a ceased-play player may not join ────────
        {
            System.out.println("== S4: forfeit/surrender gate on joining a conflict ==");
            Player init   = new Player("Init4", Faction.NARN, false);
            Player joiner = new Player("Join4", Faction.MINBARI, false);
            Player surr   = new Player("Surr4", Faction.CENTAURI, false);
            Player forfeit3 = new Player("Forfeit4", Faction.CENTAURI, false);
            List<Player> players = new ArrayList<Player>();
            players.add(init);
            players.add(joiner);
            players.add(surr);
            players.add(forfeit3);
            GameState g = new GameState(players);
            // Wire the back-reference so the gate reads GameState.isPlayerActive,
            // the same path the live game takes.
            for (Player p : players) p.setGameState(g);

            ConflictCard cc = conflict("cc4", "Probe Conflict 4", Faction.NARN, 2);
            Conflict c = new Conflict(cc, init, joiner);

            // Under-gate control, asserted BEFORE any status is set: an active
            // player may join. Without this a gate that returned false
            // unconditionally would pass every "must refuse" assert below.
            check("S4: active player may join (under-gate control)",
                    c.canJoinConflict(forfeit3));
            check("S4: active player is added by addParticipant",
                    c.addParticipant(forfeit3, true)
                    && c.getParticipants().contains(forfeit3));

            // The gate, forfeited.
            forfeit3.setHasForfeited(true);
            check("S4: forfeited player may not join", !c.canJoinConflict(forfeit3));
            check("S4: forfeited player is not added by addParticipant",
                    !c.addParticipant(forfeit3, false)
                    && c.getOpposers().isEmpty());
            CharacterCard forfeitAmb =
                    amb("f4amb", "Forfeit Amb", Faction.CENTAURI, 3, forfeit3);
            check("S4: forfeited player may not commit into the conflict",
                    !c.canCommitCard(forfeit3, forfeitAmb));

            // The gate, surrendered — a distinct flag, same rule (:817).
            surr.setHasSurrendered(true);
            check("S4: surrendered player may not join", !c.canJoinConflict(surr));

            // Over-gate control: an active player is still allowed in the SAME
            // state, proving the gate is status-conditional and not a blanket
            // refusal.
            check("S4: active player still allowed after the gate (over-gate control)",
                    c.canJoinConflict(joiner));
            check("S4: initiator is still allowed after the gate (over-gate control)",
                    c.canJoinConflict(init));

            // The war-conflict branch is the one the row names: a player at war
            // with the initiator is accepted when active, refused when forfeited.
            g.getTensionMatrix().enterWar(Faction.NARN, Faction.MINBARI);
            Conflict war = new Conflict(WarKind.RACE_TARGET, init, joiner);
            check("S4: at-war active player may join the war conflict",
                    war.canJoinConflict(joiner));
            joiner.setHasForfeited(true);
            check("S4: at-war forfeited player may not join the war conflict",
                    !war.canJoinConflict(joiner));
            joiner.setHasForfeited(false);
            check("S4: at-war player may rejoin once the status clears",
                    war.canJoinConflict(joiner));
        }

        // ── Summary ─────────────────────────────────────────────────────────
        System.out.println("Conflict resolution scenario probe: " + checks
                + " checks, " + failures + " failures.");
        System.exit(failures == 0 ? 0 : 1);
    }
}
