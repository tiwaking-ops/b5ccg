package b5ccg.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import b5ccg.model.enums.VictoryPath;

/**
 * WP-15 / B5-0032 (A6.1): the result of a victory check.
 *
 * <p><b>Why this is a set and not a single winner.</b> The rulebook says it
 * plainly, at :1048: a card that reduces the power needed to win "makes it
 * possible for more than one player to score a victory simultaneously". So
 * the result of a victory check is a <em>set</em> of winners, and the
 * engine's existing single-winner {@code checkVictory} is the degenerate
 * case of a set with one element.</p>
 *
 * <p>Earlier work in this repository (B5-0663) added a single-winner
 * {@code VictoryPathResult} and a {@code RulesEngine.checkVictoryPath} query.
 * That is left untouched: it is additive, and the conformance suite asserts
 * on it. This class is the multi-winner form, and it is what the extracted
 * {@link b5ccg.engine.VictoryRules} module returns.</p>
 *
 * <p>Java 8 source level (javac --release 8), stdlib only.</p>
 */
public final class VictoryOutcome {

    /** No winner: the empty outcome. */
    public static final VictoryOutcome NONE =
            new VictoryOutcome(Collections.<Player>emptyList(), null, -1);

    private final List<Player> winners;
    private final VictoryPath path;
    private final int power;

    /**
     * @param winners the players who won, in a stable order. Empty means
     *        nobody won.
     * @param path the path that produced the win, or null when nobody won.
     * @param power the winning Power total, or -1 when nobody won.
     */
    public VictoryOutcome(List<Player> winners, VictoryPath path, int power) {
        this.winners = Collections.unmodifiableList(new ArrayList<Player>(winners));
        this.path = path;
        this.power = power;
    }

    /** Nobody won. */
    public static VictoryOutcome none() {
        return NONE;
    }

    /** The single winner, or null when there is none or more than one. */
    public Player soleWinner() {
        return winners.size() == 1 ? winners.get(0) : null;
    }

    /** The first winner, or null. For the single-winner call sites only. */
    public Player firstWinner() {
        return winners.isEmpty() ? null : winners.get(0);
    }

    /** Every winner. Never null; empty when nobody won. */
    public List<Player> getWinners() {
        return winners;
    }

    /** True when more than one player won on the same check. */
    public boolean isSimultaneous() {
        return winners.size() > 1;
    }

    /** True when nobody won. */
    public boolean isEmpty() {
        return winners.isEmpty();
    }

    /** Which rulebook path produced the win, or null when nobody won. */
    public VictoryPath getPath() {
        return path;
    }

    /** The winning Power total, or -1 when nobody won. */
    public int getPower() {
        return power;
    }

    @Override
    public String toString() {
        if (winners.isEmpty()) {
            return "VictoryOutcome[nobody]";
        }
        StringBuilder sb = new StringBuilder("VictoryOutcome[");
        for (int i = 0; i < winners.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(winners.get(i).getName());
        }
        sb.append(" path=").append(path).append(" power=").append(power).append("]");
        return sb.toString();
    }
}
