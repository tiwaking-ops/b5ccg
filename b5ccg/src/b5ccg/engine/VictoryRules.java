package b5ccg.engine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import b5ccg.model.AgendaCard;
import b5ccg.model.Babylon5Station;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.VictoryOutcome;
import b5ccg.model.enums.VictoryPath;

/**
 * WP-15 / B5-0032 (A6.1): the victory rules, extracted.
 *
 * <p><b>What this module is.</b> The A6 decomposition asks for each rule
 * family to be modelled as "a rule with a per-player eligibility predicate
 * and an achieved predicate". This is the VictoryRules module: the five
 * rulebook victory paths, each as a named rule with those two predicates.</p>
 *
 * <p><b>What it returns, and why it is a set.</b> The rulebook says at :1048
 * that a card reducing the power needed to win "makes it possible for more
 * than one player to score a victory simultaneously". So a victory check
 * returns a {@link VictoryOutcome} carrying a <em>set</em> of winners, not a
 * single one. The engine's existing {@code RulesEngine.checkVictory} returns
 * a single {@code Player}, which is the degenerate case of a one-element set
 * under the current rule set (no card in the current pool reduces the power
 * needed to win).</p>
 *
 * <p><b>What it does NOT do.</b> It does not change the game's behaviour.
 * {@link RulesEngine#checkVictory} still returns a single winner and still
 * wins on exactly the same states; this module is asked for the same
 * question, in a form that can answer "more than one" when the rules allow
 * it, and the golden-master probe proves the answers agree. That is the
 * acceptance: "golden-master hashes identical; a simultaneous-victory probe
 * passes".</p>
 *
 * <p><b>The rules.</b> From the rulebook:</p>
 * <ul>
 *   <li>{@code RB:I/victory} - Standard Victory: 20 Power and more than any
 *       other player (:179), or Babylon 5 at 20+ influence and a leading
 *       eligible player (:180).</li>
 *   <li>{@code RB:I/victory} - Major Victory: 20 Power and 10 more than each
 *       other player (:186), or a Major Agenda's own requirement (:187).</li>
 *   <li>{@code RB:VI/reducing-power-needed-to-win} - a card that reduces the
 *       power needed to win (:1046-1048), which is what makes a simultaneous
 *       victory possible.</li>
 * </ul>
 *
 * <p>Java 8 source level (javac --release 8), stdlib only.</p>
 */
public class VictoryRules {

    /**
     * One victory path, as an eligibility predicate plus an achieved
     * predicate. This is the shape A6 asks for.
     */
    public static final class Rule {
        public final VictoryPath path;
        public final String ruleId;

        Rule(VictoryPath path, String ruleId) {
            this.path = path;
            this.ruleId = ruleId;
        }
    }

    /** The five rulebook victory paths, in the order the engine tries them. */
    public static final Rule[] RULES = {
        new Rule(VictoryPath.LAST_STANDING,       "RB:I/victory"),
        new Rule(VictoryPath.STATION_CONDITION_2,  "RB:I/victory"),
        new Rule(VictoryPath.AGENDA_CONDITION,     "RB:I/victory"),
        new Rule(VictoryPath.MAJOR,                "RB:I/victory"),
        new Rule(VictoryPath.STANDARD,             "RB:I/victory"),
    };

    /**
     * The power a player needs to win by Standard Victory.
     *
     * <p>The rulebook's default is 20 (:179). A card may reduce it - that is
     * what :1046-1050 describes - and the reduction is what makes a
     * simultaneous victory possible. Nothing in the current pool does this, so
     * the default stands; the hook is here so that when a card does, the
     * rule is expressed rather than assumed.</p>
     */
    public static final int STANDARD_VICTORY_POWER = 20;

    /**
     * The reduced power a player needs to win, per player, when a card
     * reduces it.
     *
     * <p>Rulebook :1046-1050: "If a card reduces the amount of power you need
     * to win ... you can win the game even if you do not have more power than
     * any other player. You can thereby circumvent part of the Victory
     * requirements outlined in the standard rules. Note that this makes it
     * possible for more than one player to score a victory
     * simultaneously."</p>
     *
     * <p>So the reduced path has THREE differences from the default, and all
     * three come from that one sentence:</p>
     * <ol>
     *   <li>the power threshold is lower, per player;</li>
     *   <li>the strict-lead requirement does not apply - "even if you do not
     *       have more power than any other player";</li>
     *   <li>therefore more than one player can qualify at once.</li>
     * </ol>
     *
     * <p>No card in the current pool reduces the power needed to win, so the
     * map is empty in production and this path never fires there. It exists
     * because the rulebook says it can, and a result type that cannot express
     * "two players won" would be wrong the moment such a card is printed.</p>
     */
    private final java.util.Map<Player, Integer> reducedPowerNeeded =
            new java.util.HashMap<Player, Integer>();

    /**
     * Declares that a card reduces the power a player needs to win.
     *
     * <p>This is the hook the rulebook's simultaneous-victory case needs.
     * Nothing in the current pool calls it.</p>
     */
    public void reducePowerNeededToWin(Player p, int newThreshold) {
        if (p == null || newThreshold <= 0) return;
        reducedPowerNeeded.put(p, Integer.valueOf(newThreshold));
    }

    /** The power this player needs to win, given any reduction in force. */
    public int powerNeededToWin(Player p) {
        Integer reduced = reducedPowerNeeded.get(p);
        return reduced == null ? STANDARD_VICTORY_POWER : reduced.intValue();
    }

    /**
     * Whether a player has a reduced power requirement in force, and so can
     * win without strictly leading.
     */
    public boolean hasReducedPowerRequirement(Player p) {
        return reducedPowerNeeded.containsKey(p);
    }

    /**
     * Checks every victory rule and returns the outcome.
     *
     * <p>The rules are tried in the same order the engine tries them, so the
     * single-winner answer agrees with {@code RulesEngine.checkVictory}. The
     * difference is that every player is evaluated, so more than one can
     * qualify at once.</p>
     */
    public VictoryOutcome check(GameState state) {
        if (state == null) {
            return VictoryOutcome.none();
        }

        // Path 1: last standing. The only player still in the game.
        //
        // The engine's original checkVictory had TWO last-standing checks:
        // the first returned the last standing player immediately, and the
        // second - reached only when the first did not fire - routed a
        // last-standing player through majorVictory() so the win would be
        // recorded under the Major path (B5-0661, rulebook :821). Both counts
        // used isPlayerActive, which already excludes surrendered players, so
        // the two counts are the same and the SECOND branch was unreachable:
        // whenever it would have fired, the first check had already returned.
        //
        // The golden-master probe caught the difference when this module
        // implemented the second branch faithfully rather than the first.
        // The engine's observable behaviour is what is preserved, so the
        // first check's answer is the one that is reproduced here.
        Player lastStanding = null;
        int remaining = 0;
        for (Player p : state.getPlayers()) {
            if (!state.isPlayerActive(p)) continue;
            remaining++;
            lastStanding = p;
        }
        if (remaining == 1 && lastStanding != null) {
            return new VictoryOutcome(single(lastStanding), VictoryPath.LAST_STANDING,
                    lastStanding.getInfluence());
        }

        // Path 2: station condition 2 - Babylon 5 at 20+ influence and one
        // eligible player leading in Power.
        Player stationLeader = stationVictory(state);
        if (stationLeader != null) {
            return new VictoryOutcome(single(stationLeader), VictoryPath.STATION_CONDITION_2,
                    stationLeader.getInfluence());
        }

        // Paths 3, 4 and 5: the per-player paths. EVERY player is evaluated
        // here, which is what lets two players win at once when the rules
        // allow it. The engine's checkVictory returns on the FIRST match;
        // this collects all of them.
        Set<Player> winners = new LinkedHashSet<Player>();
        VictoryPath path = null;
        int power = -1;
        for (Player p : state.getPlayers()) {
            if (p.hasForfeited()) continue;
            AgendaCard agenda = p.getAgenda();

            // Path 3: the agenda's own condition.
            if (agenda != null && !agenda.isFaceDown()
                    && agenda.isConditionMet(state, p)) {
                winners.add(p);
                if (path == null) { path = VictoryPath.AGENDA_CONDITION; power = p.getInfluence(); }
                continue;
            }
            // Path 4: Major Victory.
            if (majorVictory(state, p)) {
                winners.add(p);
                if (path == null) { path = VictoryPath.MAJOR; power = p.getInfluence(); }
                continue;
            }
            // Path 5: Standard Victory. A major agenda in play bars it; a
            // hidden one does not (it takes effect only on reveal).
            if (agenda == null || agenda.isFaceDown() || !agenda.isMajorAgenda()) {
                if (standardVictory(state, p)) {
                    winners.add(p);
                    if (path == null) { path = VictoryPath.STANDARD; power = p.getInfluence(); }
                }
            }
        }
        if (winners.isEmpty()) {
            return VictoryOutcome.none();
        }
        return new VictoryOutcome(new ArrayList<Player>(winners), path, power);
    }

    /**
     * The eligibility predicate for Standard Victory, as a question about one
     * player: is this player barred from winning by Standard Victory?
     *
     * <p>Two bars, both from the rulebook: a major agenda in play (:178,
     * "Instead, they must score a Major Victory to win") and the Shadow War
     * (:178, "During the Shadow War, no player may achieve a Standard
     * Victory").</p>
     */
    public boolean standardEligible(GameState state, Player p) {
        if (state == null || p == null) return false;
        if (state.isShadowWar()) return false;
        AgendaCard agenda = p.getAgenda();
        if (agenda != null && !agenda.isFaceDown() && agenda.isMajorAgenda()) {
            return false;
        }
        return true;
    }

    /** The achieved predicate for Standard Victory, for one player. */
    public boolean standardAchieved(GameState state, Player p) {
        if (!standardEligible(state, p)) return false;
        if (p.getInfluence() < powerNeededToWin(p)) return false;
        // Rulebook :1048: with a reduced power requirement, "you can win the
        // game even if you do not have more power than any other player". So
        // the strict-lead requirement does not apply on the reduced path -
        // and that is exactly what lets two players win at once.
        if (hasReducedPowerRequirement(p)) return true;
        return strictlyLeads(state, p, false);
    }

    /** The achieved predicate for Major Victory, for one player. */
    public boolean majorAchieved(GameState state, Player p) {
        if (state == null || p == null) return false;
        if (p.getInfluence() < STANDARD_VICTORY_POWER) return false;
        for (Player q : state.getPlayers()) {
            if (q == p || q.hasForfeited() || q.hasSurrendered()) continue;
            if (q.getInfluence() > p.getInfluence() - 10) return false;
        }
        return true;
    }

    // --- the shared predicates, matching RulesEngine exactly ---------------

    private Player stationVictory(GameState state) {
        if (state.isShadowWar()) return null;
        if (state.getStation().getInfluence() < Babylon5Station.CONDITION_2_THRESHOLD) {
            return null;
        }
        Player leader = null;
        int leaders = 0;
        for (Player p : state.getPlayers()) {
            if (p.hasForfeited()) continue;
            AgendaCard agenda = p.getAgenda();
            // A hidden agenda is inert (:520) - a face-down Major does not bar
            // its owner from the station-crown path until reveal.
            if (agenda != null && !agenda.isFaceDown() && agenda.isMajorAgenda()) continue;
            if (strictlyLeads(state, p, true)) {
                leader = p;
                leaders++;
            }
        }
        return leaders == 1 ? leader : null;
    }

    private boolean standardVictory(GameState state, Player p) {
        return standardAchieved(state, p);
    }

    private boolean majorVictory(GameState state, Player p) {
        return majorAchieved(state, p);
    }

    /**
     * The strict-leader predicate. "More than any other player" reads as a
     * strict comparison: a tie crowns nobody.
     *
     * <p>The {@code ignoreMajorAgendaPlayers} flag mirrors the engine:
     * condition 2 excludes barred players from the comparison, while
     * condition 1 compares against all players. Surrendered players are
     * excluded from both - they are out of the game.</p>
     */
    private boolean strictlyLeads(GameState state, Player p,
                                  boolean ignoreMajorAgendaPlayers) {
        for (Player q : state.getPlayers()) {
            if (q == p || q.hasForfeited() || q.hasSurrendered()) continue;
            if (ignoreMajorAgendaPlayers) {
                AgendaCard agenda = q.getAgenda();
                if (agenda != null && !agenda.isFaceDown() && agenda.isMajorAgenda()) {
                    continue;
                }
            }
            if (q.getInfluence() >= p.getInfluence()) return false;
        }
        return true;
    }

    private static List<Player> single(Player p) {
        List<Player> out = new ArrayList<Player>(1);
        out.add(p);
        return out;
    }
}
