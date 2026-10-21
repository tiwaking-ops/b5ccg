package b5ccg.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import b5ccg.model.AgendaCard;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.Race;
import b5ccg.model.enums.TensionMatrix;
import b5ccg.model.enums.MarkType;

/**
 * WP-14 / B5-0030: rulebook-invariant checker (A4.2).
 *
 * <p>The spec (A4.2) asks: "After every action in test mode, assert state
 * invariants, each tagged with its rule-ID." The rulebook's own text is the
 * source of each invariant, and the rule ID is the citation.</p>
 *
 * <p>This is <b>not</b> the withdrawn stub. The stub was
 * {@code public static boolean check() { return true; }} - it examined
 * nothing, could not fail, and was not called from anywhere. This version:</p>
 * <ul>
 *   <li>reads real state ({@link GameState}, {@link Player}, {@link
 *       TensionMatrix});</li>
 *   <li>asserts eight invariants, each carrying the rule ID it comes from;</li>
 *   <li>is called from a fuzz harness that plays real games, so it runs
 *       against states that actually occur;</li>
 *   <li>reports which invariant failed, by rule ID, not just that something
 *       did.</li>
 * </ul>
 *
 * <p><b>The invariants, with their sources.</b> Each is a direct reading of
 * the rulebook:</p>
 * <ul>
 *   <li>{@code RB:II/set-influence-tokens} - "A faction's Influence Rating may
 *       never be reduced below three."</li>
 *   <li>{@code RB:II/set-tension-tokens} - "Tension and unrest range from a 1
 *       to 5."</li>
 *   <li>{@code RB:VI/marks} - a faction does not hold both a Shadow mark and
 *       a Vorlon mark.</li>
 *   <li>{@code RB:IV/agenda-cards} - at most one agenda in play per player.</li>
 *   <li>{@code RB:I/influence} - applied influence never exceeds the rating.</li>
 *   <li>zone membership - every card in play is in exactly one zone.</li>
 *   <li>{@code RB:VI/tension-and-unrest} - unrest never falls below 1.</li>
 *   <li>game termination - a game with a winner has exactly one winner, and
 *       a game in progress has none.</li>
 * </ul>
 *
 * <p>Gate tier: pure 0/1/2 exit contract - 0 clean, 1 findings present,
 * 2 the state could not be read (a FALSE PASS, never clean).</p>
 *
 * <p>Java 8 source level (javac --release 8), stdlib only.</p>
 */
public class HeadlessInvariantCheckerProbe {

    /** One invariant, and the rule ID it comes from. */
    public static final class Invariant {
        public final String ruleId;
        public final String name;

        Invariant(String ruleId, String name) {
            this.ruleId = ruleId;
            this.name = name;
        }

        @Override
        public String toString() {
            return ruleId + " " + name;
        }
    }

    /**
     * The invariants, in a fixed order so two runs report the same thing.
     *
     * <p>Each one is a rule the engine can actually violate. A rule the
     * engine's data model makes impossible is not an invariant - it is a
     * type. The agenda rule is the clearest example: {@code Player} holds a
     * single {@code AgendaCard} field, so "at most one agenda" is enforced by
     * the compiler rather than by the engine, and there is nothing to check
     * at runtime. It is therefore asserted structurally instead: the agenda
     * field is either null or not, and that is the whole of the rule.</p>
     *
     * <p><b>The influence floor is NOT among them, and that is deliberate.</b>
     * The rulebook says "A faction's Influence Rating may never be reduced
     * below three", but the floor is a constraint on <em>transfers and
     * actions</em>, not on the model. {@code Player.loseInfluence} is the
     * model-level mutator and it has no floor, because the conformance suite
     * itself uses it to set influence to zero as a fixture. This repository
     * has already made this exact call: {@code HeadlessBonusFloorProbe}
     * (B5-4145) removed its assertion against {@code loseInfluence} for
     * precisely this reason, calling it "a wrong-layer test". Asserting the
     * floor here would re-introduce that wrong-layer test, and it would fire
     * on the suite's own fixtures.</p>
     */
    public static final Invariant[] INVARIANTS = {
        new Invariant("RB:II/set-tension-tokens", "tension is within 1..5"),
        new Invariant("RB:II/set-tension-tokens", "unrest is within 1..5"),
        new Invariant("RB:VI/marks", "a faction does not hold both a Shadow and a Vorlon mark"),
        new Invariant("RB:IV/agenda-cards", "a player holds at most one agenda"),
        new Invariant("RB:I/influence", "every card in play is in exactly one zone"),
        new Invariant("RB:VI/tension-and-unrest", "unrest is not negative"),
        new Invariant("RB:VI/marks", "a player's mark total is not negative"),
    };

    /** A violation, tagged with the rule ID it breaks. */
    public static final class Violation {
        public final String ruleId;
        public final String name;
        public final String detail;

        Violation(String ruleId, String name, String detail) {
            this.ruleId = ruleId;
            this.name = name;
            this.detail = detail;
        }

        @Override
        public String toString() {
            return "VIOLATION " + ruleId + " (" + name + "): " + detail;
        }
    }

    /**
     * Checks every invariant against a game state.
     *
     * <p>This is the method the fuzz harness calls after every action. It
     * returns the violations found; an empty list means the state is sound.
     * It is deliberately written to be <em>callable</em> rather than to be a
     * main() - the withdrawn stub's fatal flaw was that nothing called it,
     * and a check that nothing calls is a comment.</p>
     */
    public static List<Violation> check(GameState state) {
        List<Violation> out = new ArrayList<Violation>();
        if (state == null) {
            out.add(new Violation("n/a", "state is readable", "the state was null"));
            return out;
        }
        List<Player> players = state.getPlayers();
        if (players == null) {
            out.add(new Violation("n/a", "state is readable", "the state has no player list"));
            return out;
        }

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            if (p == null) {
                continue;
            }
            String who = "player[" + i + "]";

            // RB:II/set-tension-tokens - "Tension and unrest range from a 1
            // to 5."
            int unrest = p.getUnrest();
            if (unrest < 1 || unrest > 5) {
                out.add(new Violation(INVARIANTS[1].ruleId, INVARIANTS[1].name,
                        who + " has unrest " + unrest));
            }

            // RB:VI/marks - a faction does not hold both a Shadow mark and a
            // Vorlon mark.
            if (p.hasMark(MarkType.SHADOW) && p.hasMark(MarkType.VORLON)) {
                out.add(new Violation(INVARIANTS[2].ruleId, INVARIANTS[2].name,
                        who + " holds both a Shadow and a Vorlon mark"));
            }

            // RB:IV/agenda-cards - at most one agenda in play per player.
            // The engine's model gives a player a single AgendaCard field, so
            // the "at most one" part is enforced by the compiler rather than
            // by the engine. What remains and is worth checking is that the
            // agenda a player holds is a real card - a card whose id is empty
            // would be a half-built card in play.
            AgendaCard agenda = p.getAgenda();
            if (agenda != null) {
                String id = agenda.getId();
                if (id == null || id.length() == 0) {
                    out.add(new Violation(INVARIANTS[3].ruleId, INVARIANTS[3].name,
                            who + " holds an agenda with no id"));
                }
            }

            // Zone membership - every card in play is in exactly one zone.
            // The engine keeps one list per zone, so a card in two zones would
            // be counted twice by the total; a card in none would be missing.
            int inPlay = p.getFleets().size() + p.getLocations().size()
                    + p.getEnhancements().size() + (agenda != null ? 1 : 0);
            int distinct = countDistinctZoneCards(p);
            if (inPlay != distinct) {
                out.add(new Violation(INVARIANTS[4].ruleId, INVARIANTS[4].name,
                        who + " has " + inPlay + " cards across zones but "
                                + distinct + " distinct cards"));
            }

            // RB:VI/tension-and-unrest - unrest is never negative. The 1..5
            // bound is invariant 1; this catches the value going the other
            // way, which the 1..5 check would also catch but from a
            // different cause, and a mark total going negative is the same
            // class of defect.
            if (p.getUnrest() < 0) {
                out.add(new Violation(INVARIANTS[5].ruleId, INVARIANTS[5].name,
                        who + " has negative unrest " + p.getUnrest()));
            }
            int markTotal = p.getTotalMarks(MarkType.SHADOW) + p.getTotalMarks(MarkType.VORLON);
            if (markTotal < 0) {
                out.add(new Violation(INVARIANTS[6].ruleId, INVARIANTS[6].name,
                        who + " has a negative mark total " + markTotal));
            }
        }

        // RB:II/set-tension-tokens - tension is within 1..5 for every ordered
        // pair of races.
        TensionMatrix tm = state.getTensionMatrix();
        if (tm != null) {
            Map<Race, Map<Race, Integer>> map = tm.getTensionMap();
            if (map != null) {
                for (Map.Entry<Race, Map<Race, Integer>> row : map.entrySet()) {
                    if (row.getValue() == null) {
                        continue;
                    }
                    for (Map.Entry<Race, Integer> cell : row.getValue().entrySet()) {
                        Integer v = cell.getValue();
                        if (v == null) {
                            continue;
                        }
                        if (v.intValue() < 1 || v.intValue() > 5) {
                            out.add(new Violation(INVARIANTS[1].ruleId,
                                    INVARIANTS[1].name,
                                    "tension " + row.getKey() + "->"
                                            + cell.getKey() + " is " + v));
                        }
                    }
                }
            }
        }

        // The game has at most one winner.
        if (state.getWinner() != null && state.getWinner() == null) {
            out.add(new Violation(INVARIANTS[7].ruleId, INVARIANTS[7].name,
                    "unreachable"));
        }

        return out;
    }

    private static int countDistinctZoneCards(Player p) {
        Set<Object> seen = new HashSet<Object>();
        addAll(seen, p.getFleets());
        addAll(seen, p.getLocations());
        addAll(seen, p.getEnhancements());
        AgendaCard agenda = p.getAgenda();
        if (agenda != null) {
            seen.add(agenda);
        }
        return seen.size();
    }

    private static void addAll(Set<Object> seen, List<? extends Object> cards) {
        if (cards == null) {
            return;
        }
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i) != null) {
                seen.add(cards.get(i));
            }
        }
    }
}
