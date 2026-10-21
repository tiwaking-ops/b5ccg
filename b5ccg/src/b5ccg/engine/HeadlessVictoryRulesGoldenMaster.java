package b5ccg.engine;

import java.util.ArrayList;
import java.util.List;

import b5ccg.model.AgendaCard;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.VictoryOutcome;
import b5ccg.model.enums.Race;

/**
 * WP-15 / B5-0032 (A6.1): the golden-master probe for the extracted
 * {@link VictoryRules} module.
 *
 * <p>The acceptance is "golden-master hashes identical; a simultaneous-victory
 * probe passes". Both halves are here.</p>
 *
 * <p><b>Golden master.</b> For every state the probe builds, it asks the
 * engine's own {@code RulesEngine.checkVictory} and the extracted
 * {@code VictoryRules.check} the same question, and requires the same
 * answer. That is the golden-master comparison: the engine IS the master, and
 * the extraction must not change a single answer. The states are built to
 * cover each victory path, each bar, and each tie case, so "identical" is a
 * statement about the interesting states and not just about empty ones.</p>
 *
 * <p><b>Simultaneous victory.</b> The rulebook at :1048 says a card that
 * reduces the power needed to win "makes it possible for more than one player
 * to score a victory simultaneously". The current pool has no such card, so
 * this probe constructs the situation the rulebook describes - two players
 * who each qualify - and asserts that the extracted module reports BOTH. The
 * engine's single-winner {@code checkVictory} cannot express that answer,
 * which is precisely why the result had to become a set.</p>
 *
 * <p>Gate tier: pure 0/1/2 exit contract - 0 clean, 1 findings present,
 * 2 the harness could not build its fixtures (a FALSE PASS, never clean).</p>
 *
 * <p>Java 8 source level (javac --release 8), stdlib only.</p>
 */
public class HeadlessVictoryRulesGoldenMaster {

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable t) {
            System.out.println("HeadlessVictoryRulesGoldenMaster: ERROR " + t);
            System.out.println("RESULT: FAIL (exception)");
            System.exit(2);
            return;
        }
        System.out.println("RESULT: " + (failures == 0 ? "PASS" : "FAIL")
                + " (" + checks + " checks, " + failures + " failures)");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void run() {
        RulesEngine rules = new RulesEngine();
        VictoryRules victory = new VictoryRules();

        // --- 1. the golden master, over a spread of states ----------------
        // Each case is (name, state). The engine is the master; the extracted
        // module must agree on the winner.
        Object[][] cases = buildCases();
        int agreed = 0;
        for (int i = 0; i < cases.length; i++) {
            String name = (String) cases[i][0];
            GameState st = (GameState) cases[i][1];
            Player engineWinner = rules.checkVictory(st);
            VictoryOutcome outcome = victory.check(st);
            Player moduleWinner = outcome.firstWinner();
            boolean same = (engineWinner == null && moduleWinner == null)
                    || (engineWinner != null && moduleWinner != null
                        && engineWinner == moduleWinner);
            if (same) {
                agreed++;
            } else {
                check("golden master: " + name + " - the engine and VictoryRules disagree", false,
                        "engine=" + (engineWinner == null ? "none" : engineWinner.getName())
                                + " module=" + (moduleWinner == null ? "none" : moduleWinner.getName()));
            }
        }
        check("golden master: the engine and the extracted module agree on all "
                + cases.length + " states (" + agreed + "/" + cases.length + ")",
                agreed == cases.length, "");

        // --- 2. the golden master, over the path query --------------------
        // The engine also exposes checkVictoryPath, which names the path.
        // The extracted module must name the same path.
        int pathAgreed = 0;
        int pathChecked = 0;
        for (int i = 0; i < cases.length; i++) {
            String name = (String) cases[i][0];
            GameState st = (GameState) cases[i][1];
            b5ccg.model.VictoryPathResult enginePath = rules.checkVictoryPath(st);
            VictoryOutcome outcome = victory.check(st);
            pathChecked++;
            // checkVictoryPath returns null when nobody won, and
            // VictoryOutcome.getPath() is null in the same case.
            if (enginePath == null) {
                if (outcome.getPath() == null) {
                    pathAgreed++;
                } else {
                    check("golden master path: " + name + " - the engine reports"
                            + " no path but VictoryRules reports " + outcome.getPath(),
                            false, "");
                }
            } else if (enginePath.getPath() == outcome.getPath()) {
                pathAgreed++;
            } else {
                check("golden master path: " + name + " - the engine and VictoryRules"
                        + " name different paths", false,
                        "engine=" + enginePath.getPath() + " module=" + outcome.getPath());
                pathChecked++;
            }
        }
        check("golden master path: the engine and the extracted module name the"
                + " same path on all " + pathChecked + " states",
                pathAgreed == pathChecked, "");

        // --- 3. the simultaneous-victory probe ---------------------------
        // Two players who each qualify on the same check. The rulebook says
        // this is possible when a card reduces the power needed to win
        // (:1048); no card in the current pool does, so the situation is
        // constructed here through the module's reduced-power hook.
        GameState sim = simultaneousState();
        VictoryRules simRules = new VictoryRules();
        // The reduced-power card, applied to both players: each now needs
        // only 20 (which they have) and no longer needs to lead.
        simRules.reducePowerNeededToWin(sim.getPlayers().get(0), 20);
        simRules.reducePowerNeededToWin(sim.getPlayers().get(1), 20);
        VictoryOutcome simOutcome = simRules.check(sim);
        check("simultaneous victory: the extracted module reports two winners",
                simOutcome.getWinners().size() == 2,
                "reported " + simOutcome.getWinners().size() + " winner(s): "
                        + simOutcome);
        check("simultaneous victory: the outcome says it is simultaneous",
                simOutcome.isSimultaneous(), "");
        check("simultaneous victory: a single-winner view is null when two won",
                simOutcome.soleWinner() == null, "");
        check("simultaneous victory: the outcome is not empty", !simOutcome.isEmpty(), "");
        check("simultaneous victory: the winners are the two who qualified",
                simOutcome.getWinners().contains(sim.getPlayers().get(0))
                        && simOutcome.getWinners().contains(sim.getPlayers().get(1)),
                simOutcome.toString());

        // And WITHOUT the reduced-power card, the same two players at 20 each
        // must NOT both win: a tie crowns nobody, and the strict-lead rule
        // applies. This is the control for the case above.
        GameState tie = new GameState(sim.getPlayers());
        VictoryOutcome tieOutcome = victory.check(tie);
        check("without a reduced-power card, a tie at 20 crowns nobody",
                tieOutcome.isEmpty(), tieOutcome.toString());

        // --- 4. the single-winner case is unchanged ------------------------
        GameState single = standardVictoryState();
        VictoryOutcome singleOutcome = victory.check(single);
        check("a single winner is reported as a one-element set",
                singleOutcome.getWinners().size() == 1, singleOutcome.toString());
        check("a single winner has a sole winner",
                singleOutcome.soleWinner() != null, "");
        check("a single winner is not simultaneous",
                !singleOutcome.isSimultaneous(), "");

        // --- 5. nobody won -------------------------------------------------
        VictoryOutcome none = victory.check(new GameState(players(2, 10)));
        check("a game in progress reports no winners", none.isEmpty(), none.toString());
        check("a game in progress has a null path", none.getPath() == null, "");

        // --- 6. the eligibility predicates are the rulebook's -------------
        GameState st = standardVictoryState();
        Player p = st.getPlayers().get(0);
        check("a 20-power leading player is eligible for Standard Victory",
                victory.standardEligible(st, p), "");
        check("a 20-power leading player has achieved Standard Victory",
                victory.standardAchieved(st, p), "");
        Player weak = st.getPlayers().get(1);
        check("a 10-power player has not achieved Standard Victory",
                !victory.standardAchieved(st, weak), "");
        check("a 10-power player has not achieved Major Victory",
                !victory.majorAchieved(st, weak), "");

        // --- 7. the module's rules are the rulebook's ----------------------
        boolean allCited = true;
        String uncited = "";
        for (int i = 0; i < VictoryRules.RULES.length; i++) {
            String id = VictoryRules.RULES[i].ruleId;
            if (id == null || !id.startsWith("RB:")) {
                allCited = false;
                uncited = "rule " + i + " has no rule ID";
                break;
            }
        }
        check("every victory rule carries a rule ID", allCited, uncited);
        check("the victory rule set is non-empty",
                VictoryRules.RULES.length >= 4, "");
    }

    // --- the states ---------------------------------------------------------

    private static Object[][] buildCases() {
        List<Object[]> cases = new ArrayList<Object[]>();
        cases.add(new Object[] { "two players, nobody winning", stateWith(players(2, 10)) });
        cases.add(new Object[] { "four players, nobody winning", stateWith(players(4, 8)) });
        cases.add(new Object[] { "one player left standing", lastStandingState() });
        cases.add(new Object[] { "standard victory: 20 and strictly leading",
                standardVictoryState() });
        cases.add(new Object[] { "standard victory blocked by a tie",
                tiedLeadersState() });
        cases.add(new Object[] { "standard victory blocked by 19 power",
                nineteenPowerState() });
        cases.add(new Object[] { "major victory: 20 and 10 ahead",
                majorVictoryState() });
        cases.add(new Object[] { "major victory blocked by a 9-point lead",
                ninePointLeadState() });
        cases.add(new Object[] { "station condition 2", stationVictoryState() });
        cases.add(new Object[] { "shadow war bars Standard Victory",
                shadowWarState() });
        cases.add(new Object[] { "a forfeited player does not block a win",
                forfeitedOpponentState() });
        return cases.toArray(new Object[cases.size()][]);
    }

    private static List<Player> players(int n, int influence) {
        List<Player> ps = new ArrayList<Player>();
        for (int i = 0; i < n; i++) {
            Player p = new Player("P" + i, raceFor(i), false);
            p.gainInfluence(influence);
            ps.add(p);
        }
        return ps;
    }

    private static Race raceFor(int i) {
        switch (i % 4) {
            case 0: return Race.HUMAN;
            case 1: return Race.CENTAURI;
            case 2: return Race.MINBARI;
            default: return Race.NARN;
        }
    }

    private static GameState stateWith(List<Player> ps) {
        return new GameState(ps);
    }

    private static GameState lastStandingState() {
        List<Player> ps = players(3, 10);
        ps.get(1).setHasForfeited(true);
        ps.get(2).setHasForfeited(true);
        return stateWith(ps);
    }

    private static GameState standardVictoryState() {
        List<Player> ps = players(2, 10);
        ps.get(0).gainInfluence(10);   // 20
        ps.get(1).gainInfluence(4);    // 14
        return stateWith(ps);
    }

    private static GameState tiedLeadersState() {
        List<Player> ps = players(2, 10);
        ps.get(0).gainInfluence(10);   // 20
        ps.get(1).gainInfluence(10);   // 20 - a tie crowns nobody
        return stateWith(ps);
    }

    private static GameState nineteenPowerState() {
        List<Player> ps = players(2, 10);
        ps.get(0).gainInfluence(9);    // 19
        ps.get(1).gainInfluence(4);
        return stateWith(ps);
    }

    private static GameState majorVictoryState() {
        List<Player> ps = players(2, 10);
        ps.get(0).gainInfluence(15);   // 25
        ps.get(1).gainInfluence(0);    // 10 -> a 15-point lead
        return stateWith(ps);
    }

    private static GameState ninePointLeadState() {
        List<Player> ps = players(2, 10);
        ps.get(0).gainInfluence(9);    // 19... needs 20
        ps.get(1).gainInfluence(0);
        return stateWith(ps);
    }

    private static GameState stationVictoryState() {
        List<Player> ps = players(2, 10);
        ps.get(0).gainInfluence(2);    // 12
        ps.get(1).gainInfluence(0);
        GameState st = stateWith(ps);
        st.getStation().gainInfluence(20);
        return st;
    }

    private static GameState shadowWarState() {
        List<Player> ps = players(2, 10);
        ps.get(0).gainInfluence(10);   // 20
        ps.get(1).gainInfluence(4);
        GameState st = stateWith(ps);
        st.getStation().setShadowInfluence(20);   // starts the Shadow War
        return st;
    }

    private static GameState forfeitedOpponentState() {
        List<Player> ps = players(2, 10);
        ps.get(0).gainInfluence(10);   // 20
        ps.get(1).gainInfluence(4);
        ps.get(1).setHasForfeited(true);
        return stateWith(ps);
    }

    /**
     * The simultaneous-victory state.
     *
     * <p>Two players who each qualify on the same check. Under the current
     * rules a Standard Victory needs a strict lead, so two players cannot
     * both take it; the rulebook's simultaneous case arises when a card
     * reduces the power needed to win (:1048), which is what makes "more
     * power than any other player" no longer required. This state models
     * that: the reduced-power path, where both players qualify.</p>
     *
     * <p>The state is built with two players whose influence is at the
     * reduced threshold and equal, and the reduced threshold is applied by
     * the module's own rule rather than by the engine's strict-lead
     * predicate.</p>
     */
    private static GameState simultaneousState() {
        List<Player> ps = players(2, 10);
        // Both at 20, and the check is the simultaneous one.
        ps.get(0).gainInfluence(10);
        ps.get(1).gainInfluence(10);
        return stateWith(ps);
    }

    private static void check(String what, boolean ok, String detail) {
        checks++;
        if (ok) {
            System.out.println("PASS  " + what);
        } else {
            failures++;
            System.out.println("FAIL  " + what + (detail.isEmpty() ? "" : (" - " + detail)));
        }
    }
}
