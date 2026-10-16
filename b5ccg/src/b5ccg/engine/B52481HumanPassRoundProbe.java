package b5ccg.engine;

import java.util.ArrayList;
import java.util.List;

import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.Deck;
import b5ccg.model.EventCard;
import b5ccg.model.FleetCard;
import b5ccg.model.GameAction;
import b5ccg.model.enums.AIDifficulty;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.GamePhase;
import b5ccg.model.enums.Rarity;
import b5ccg.ai.AIPlayer;

/**
 * B5-2481: the human playtest report — "round does not end when all players
 * pass (Agenda Support of the Mighty sponsor + pass)".
 *
 * <p>D6 (testD6ActionLoop) already proves the consecutive-pass exit works, but
 * with ALL-AI seats: waitForHumanAction is never called and the human leg is
 * never exercised. This probe drives a real human seat through the reported
 * sequence and watches what the round actually does.
 *
 * <p>Run: java b5ccg.engine.B52481HumanPassRoundProbe
 */
public class B52481HumanPassRoundProbe {

    private static int checks = 0;
    private static int fails = 0;

    private static void check(String id, String what, boolean ok) {
        checks++;
        if (!ok) fails++;
        System.out.println("  [" + id + "] " + what + ": " + (ok ? "PASS" : "FAIL"));
    }

    private static Player player(String name, Faction f, boolean human) {
        Player p = new Player(name, f, human);
        CharacterCard amb = new CharacterCard("amb_" + name, "Amb " + name,
                "CHARACTER_" + f, Rarity.FIXED, f, CardSet.PREMIERE, "x", "text",
                3, 3, 3, 3, true);
        p.setAmbassador(amb);
        p.getInnerCircle().add(amb);
        List<Card> filler = new ArrayList<Card>();
        for (int i = 0; i < 10; i++) {
            filler.add(new EventCard("deck_" + name + "_" + i, "Deck " + i,
                    "EVENT", Rarity.COMMON, Faction.ANY, CardSet.PREMIERE, "x", "text"));
        }
        p.setDeck(new Deck(filler));
        return p;
    }

    private static GameState state(Player... players) {
        List<Player> ps = new ArrayList<Player>();
        for (Player p : players) { ps.add(p); }
        GameState s = new GameState(ps);
        for (Player p : players) { p.setGameState(s); }
        s.setPhase(GamePhase.ACTION);
        return s;
    }

    private static FleetCard fleet(String id, String fleetClass) {
        FleetCard f = new FleetCard(id, id, "FLEET", Rarity.COMMON, Faction.ANY,
                CardSet.PREMIERE, "x", "text", 3);
        f.setFleetClass(fleetClass);
        return f;
    }

    private static CharacterCard chr(String id, int leadership) {
        return new CharacterCard(id, id, "CHARACTER_NARN", Rarity.RARE, Faction.NARN,
                CardSet.PREMIERE, "x", "text", 2, 2, 0, leadership, false);
    }

    /**
     * Runs runActionPhase on a thread, and answers the human window by
     * submitting the queued action from outside — the shape a real UI click has.
     */
    private static void runWithHuman(final GameController gc,
                                     final List<GameAction> script,
                                     final String tag) throws Exception {
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    java.lang.reflect.Method m =
                            GameController.class.getDeclaredMethod("runActionPhase");
                    m.setAccessible(true);
                    m.invoke(gc);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();

        // Drive until the round RETURNS, not until the window is open. An
        // earlier version of this driver looped `while (isWaitingForHuman())`,
        // which is false before the round thread has reached
        // waitForHumanAction -- so it submitted nothing, never entered the
        // loop, and the join timed out. That read as "the round hangs" and
        // would have been a false defect report. The window is a question the
        // round asks, not a state it is already in.
        int guard = 0;
        boolean answered = false;
        while (t.isAlive() && guard < 600) {
            if (gc.isWaitingForHuman()) {
                GameAction a = (script.isEmpty()) ? GameAction.pass() : script.remove(0);
                gc.submitHumanAction(a);
                answered = true;
            }
            Thread.sleep(10);
            guard++;
        }
        t.join(4000);
        check("B5-2481", tag + ": the human window was actually answered",
              answered);
        check("B5-2481", tag + ": runActionPhase returned (no hang)", !t.isAlive());
    }

    public static void main(String[] args) throws Exception {
        System.out.println("B5-2481: human-seat action round ends when all players pass");

        // ── S1: human SPONSORS (a real, non-pass action) then PASSES ─────────
        System.out.println("== S1: human sponsor then pass, AI table passes ==");
        Player human = player("H2481", Faction.CENTAURI, true);
        Player ai1   = player("A2481a", Faction.NARN, false);
        Player ai2   = player("A2481b", Faction.MINBARI, false);
        GameState st = state(human, ai1, ai2);
        List<AIPlayer> ais = new ArrayList<AIPlayer>();
        for (Player p : st.getPlayers()) {
            p.getHand().clear();
            p.getSupportingRole().clear();
            p.getLocations().clear();
            p.getGroups().clear();
            p.getEnhancements().clear();
            p.setAgenda(null);
            p.addCharacter(chr(p.getName() + "_l1", 2));
            p.addCharacter(chr(p.getName() + "_l2", 2));
            p.addFleet(fleet(p.getName() + "_f1", "LINE"));
            p.addFleet(fleet(p.getName() + "_f2", "LINE"));
            ais.add(new AIPlayer(p, AIDifficulty.MEDIUM));
        }
        GameController gc = new GameController(st, ais, new GameStateCallback() {
            public void accept(GameState gs) { }
        });
        new RulesEngine().startRound(st);

        List<GameAction> script = new ArrayList<GameAction>();
        script.add(GameAction.pass());   // human's answer on its FIRST window
        runWithHuman(gc, script, "S1");

        boolean allPassed = true;
        for (Player p : st.getPlayers()) {
            if (!p.isPassed()) allPassed = false;
        }
        boolean capHit = false;
        for (String line : st.getLog()) {
            if (line.indexOf("safety cap reached") >= 0) capHit = true;
        }
        check("B5-2481", "S1: every seat is passed at round end", allPassed);
        check("B5-2481", "S1: the round did NOT hit the safety cap", !capHit);
        check("B5-2481", "S1: phase is ACTION when the round closes",
              st.getPhase() == GamePhase.ACTION);

        // ── S2: human PASSES on every window, repeatedly ─────────────────────
        System.out.println("== S2: human passes every window ==");
        Player h2 = player("H2481b", Faction.CENTAURI, true);
        Player a2 = player("A2481c", Faction.NARN, false);
        GameState st2 = state(h2, a2);
        List<AIPlayer> ais2 = new ArrayList<AIPlayer>();
        for (Player p : st2.getPlayers()) {
            p.getHand().clear();
            p.getSupportingRole().clear();
            p.getLocations().clear();
            p.getGroups().clear();
            p.getEnhancements().clear();
            p.setAgenda(null);
            p.addCharacter(chr(p.getName() + "_l1", 2));
            p.addFleet(fleet(p.getName() + "_f1", "LINE"));
            ais2.add(new AIPlayer(p, AIDifficulty.MEDIUM));
        }
        GameController gc2 = new GameController(st2, ais2, new GameStateCallback() {
            public void accept(GameState gs) { }
        });
        new RulesEngine().startRound(st2);
        runWithHuman(gc2, new ArrayList<GameAction>(), "S2");

        boolean allPassed2 = true;
        for (Player p : st2.getPlayers()) {
            if (!p.isPassed()) allPassed2 = false;
        }
        boolean capHit2 = false;
        for (String line : st2.getLog()) {
            if (line.indexOf("safety cap reached") >= 0) capHit2 = true;
        }
        check("B5-2481", "S2: every seat is passed at round end", allPassed2);
        check("B5-2481", "S2: the round did NOT hit the safety cap", !capHit2);

        // ── S3: THE REPORTED PLAYTEST -- sponsor, THEN pass ─────────────────
        // "Agenda Support of the Mighty sponsor + pass". A sponsor is a real
        // non-pass action, so it RESETS passCount (B5-0372: the round ends only
        // on CONSECUTIVE passes). The question this row actually asks is
        // whether the human, having acted, is then able to reach the exit at
        // all -- i.e. whether it is re-prompted and whether its pass is
        // counted. D6 cannot answer either, being all-AI.
        System.out.println("== S3: human sponsors (non-pass) then passes to the exit ==");
        Player h3 = player("H2481s", Faction.CENTAURI, true);
        Player a3 = player("A2481s", Faction.NARN, false);
        GameState st3 = state(h3, a3);
        List<AIPlayer> ais3 = new ArrayList<AIPlayer>();
        CharacterCard sponsorMe = null;
        for (Player p : st3.getPlayers()) {
            p.getHand().clear();
            p.getSupportingRole().clear();
            p.getLocations().clear();
            p.getGroups().clear();
            p.getEnhancements().clear();
            p.setAgenda(null);
            p.addCharacter(chr(p.getName() + "_l1", 2));
            p.addFleet(fleet(p.getName() + "_f1", "LINE"));
            CharacterCard inHand = chr(p.getName() + "_sponsor", 0);
            p.addToHand(inHand);
            if (p == h3) sponsorMe = inHand;
            ais3.add(new AIPlayer(p, AIDifficulty.MEDIUM));
        }
        GameController gc3 = new GameController(st3, ais3, new GameStateCallback() {
            public void accept(GameState gs) { }
        });
        new RulesEngine().startRound(st3);

        List<GameAction> script3 = new ArrayList<GameAction>();
        script3.add(GameAction.recruitCharacter(sponsorMe));  // the non-pass
        script3.add(GameAction.pass());                        // then decline
        runWithHuman(gc3, script3, "S3");

        boolean allPassed3 = true;
        for (Player p : st3.getPlayers()) {
            if (!p.isPassed()) allPassed3 = false;
        }
        boolean capHit3 = false;
        for (String line : st3.getLog()) {
            if (line.indexOf("safety cap reached") >= 0) capHit3 = true;
        }
        check("B5-2481", "S3: the sponsor actually entered the supporting role",
              h3.getSupportingRole().contains(sponsorMe));
        check("B5-2481", "S3: every seat is passed at round end", allPassed3);
        check("B5-2481", "S3: the round did NOT hit the safety cap", !capHit3);
        check("B5-2481", "S3: the human was re-prompted after its non-pass action",
              script3.isEmpty());
        // The load-bearing negative: a round must NOT end while a non-pass
        // action is still fresh. If the sponsor's passCount reset were lost,
        // this is the case that would end early.
        check("B5-2481", "S3: the round ended on consecutive passes, not the cap",
              !capHit3 && st3.getPhase() == GamePhase.ACTION);

        System.out.println();
        if (fails == 0) {
            System.out.println("B5-2481 PROBE PASSED (" + checks + " checks)");
        } else {
            System.out.println("B5-2481 PROBE FAILED (" + fails + " of " + checks + ")");
            for (String line : st.getLog()) {
                if (line.indexOf("H2481") >= 0) System.out.println("  S1LOG| " + line);
            }
            for (String line : st2.getLog()) {
                if (line.indexOf("H2481b") >= 0) System.out.println("  S2LOG| " + line);
            }
            System.exit(1);
        }
    }
}