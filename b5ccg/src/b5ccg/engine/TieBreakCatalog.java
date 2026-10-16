package b5ccg.engine;

import b5ccg.model.CharacterCard;
import b5ccg.model.Conflict;
import b5ccg.model.Player;
import b5ccg.model.enums.StatKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * B5-2554 — the single place the engine's tie-breaking rules live.
 *
 * <p>Before this class every tie-break was written at the point of use: the :352
 * chain sat in {@code RulesEngine.compareInitiativeKey}, the conflict-participation
 * rule sat inline in {@code resolveConflict}, the League vote sat inline in
 * {@code resolveCouncilVote}, and the "a tie crowns nobody" victories sat in
 * {@code strictlyLeads}. Nothing named them as one family, so nothing could check
 * that they still agreed with each other or with the rulebook. This class is that
 * family: one vocabulary ({@link Context}) plus one implementation per rule, with
 * {@code RulesEngine} delegating every tie-break call to it.
 *
 * <h3>What a "tie-break" is here, and what it is not</h3>
 * A tie-break is a rule whose outcome DEPENDS ON A COMPARISON BEING EQUAL. Rules
 * that only ever compare unequal values are not here. What matters is not the
 * size of the rule but the consequence of getting it wrong: a tie-break is the one
 * place where the rulebook, read carelessly, produces a silent wrong answer rather
 * than an obviously broken one.
 *
 * <h3>RECORDED, NOT INVENTED: where the rulebook is SILENT</h3>
 * {@link Context#rulebookNamesTieBreak()} is {@code false} for
 * {@link Context#MERCENARY_CONTROL}. Rulebook :739 decides mercenary control by
 * "the faction which applied the most influence during the turn (bids are
 * cumulative)" and says nothing at all about equal totals. B5-0395 read that
 * silence as "a tie crowns nobody"; B5-2281 replaced that with a tie broken in :352
 * initiative order and logged. This class does NOT convert that reading into a
 * rulebook claim — {@code MERCENARY_CONTROL.rulebookNamesTieBreak()} stays false and
 * {@link #tieBreakByInitiativeOrder} is documented as the adopted substitute, so a
 * future reader can tell an invented rule from a quoted one by asking the catalog
 * rather than by reading a comment and hoping. The same is true of the two victory
 * margins: :176 and :182 DO name their comparisons, but the STRICTNESS is the
 * reading ("more than any other player", "at least 10 more than each other"), and
 * {@link #isStrictlyLeading} is where that reading lives.
 *
 * <h3>DETERMINISM, precisely</h3>
 * "Deterministic" means the same inputs produce the same outcome on every run. Most
 * of the rules here are stronger than that: they are <em>permutation-invariant</em>,
 * so the result cannot depend on the order the seats were added to the game.
 * {@link #leadingOpposer} is deliberately NOT permutation-invariant — rulebook :550
 * nominates a side, and a tie between two equally-strong opposers is broken by
 * participation order, which is a real fact about the game rather than an artefact
 * of iteration. {@code HeadlessTieBreakProbe} asserts which of the two each rule is,
 * because a rule that is accidentally permutation-invariant is as much a defect as
 * one that is accidentally not.
 *
 * <p>Java 6 only. No external libraries.
 */
public final class TieBreakCatalog {

    private TieBreakCatalog() { }

    // ── The vocabulary ──────────────────────────────────────────────────────

    /**
     * Every tie-break the engine plays by, with the rulebook clause it comes from
     * and whether that clause actually names the tie-break.
     *
     * <p>The vocabulary lives HERE, beside the implementations, so a new tie-break
     * cannot be added as a bare inline comparison somewhere and then discovered by a
     * player. Adding an enum constant without implementing it is caught by
     * {@code HeadlessTieBreakProbe} (every constant must have a non-empty citation
     * and a non-null resolver), and implementing a rule without a constant is caught
     * by the fact that {@link RulesEngine} may only reach a tie-break through this
     * class. This is the B5-2500 "one rule, two implementations" shape closed.
     */
    public enum Context {
        /**
         * Rulebook :352 — turn order. The chain is explicit and total until its
         * last step: Influence Rating, then Diplomacy, Intrigue, Psi and Leadership
         * on the ambassador (higher ability = acts LATER, so the LOWER value acts
         * first within a tie), and finally "If two players are still tied,
         * determine initiative order between them randomly."
         */
        INITIATIVE_ORDER(":352", true,
                "Influence Rating, then ambassador Diplomacy, Intrigue, Psi, "
                + "Leadership; the residual \"determine initiative order between "
                + "them randomly\" is stood in for by seat name order so a replay "
                + "reproduces."),

        /**
         * Rulebook :550 — conflict participation. "If a conflict receives more
         * support than opposition, then the faction which initiated it wins. If a
         * conflict receives the same amount or more opposition than support, then
         * the faction which initiated it loses." The rule names the tie and its
         * answer, and the answer is the initiator LOSES.
         */
        CONFLICT_PARTICIPATION(":550", true,
                "More support than opposition wins for the initiator; equal or "
                + "more opposition loses for the initiator. A tie among equally "
                + "strong opposers goes to the first participant who opposed."),

        /**
         * Rulebook :791 and :908 — agenda voting. "the League of Non-Aligned worlds
         * (acting as if it were a single race) may cast one vote to break any
         * tie" / "If the voting is ever tied after the five races (including the
         * Vorlons) have cast their votes or abstained, the League vote must be
         * called."
         */
        COUNCIL_VOTE(":791 :908", true,
                "A measure passes on Yes >= No + 1. On an exact Yes == No tie the "
                + "League of Non-Aligned Worlds must be called and votes Yes, so a "
                + "tied measure PASSES."),

        /**
         * Rulebook :739 — mercenary control. SILENT on ties: the clause names the
         * winning total and nothing about equal totals. See the class javadoc.
         */
        MERCENARY_CONTROL(":739", false,
                "Highest cumulative influence wins. The rulebook is SILENT on "
                + "equal totals, so the adopted substitute is :352 initiative "
                + "order (B5-2281), announced by name."),

        /**
         * Rulebook :176 — Standard Victory, "Have 20 Power, and more than any
         * other player". The clause names the comparison; the STRICTNESS is the
         * reading, and it is what makes a tie crown nobody.
         */
        STANDARD_VICTORY(":176", true,
                "20+ influence AND strictly more than every other player still in "
                + "the game. An equal total crowns nobody."),

        /**
         * Rulebook :182 — Major Victory, "Have at least 20 Power, and at least 10
         * more than each other player". A 9-point lead or an equal total crowns
         * nobody.
         */
        MAJOR_VICTORY(":182", true,
                "20+ influence AND at least 10 more than every other player still "
                + "in the game. A 9-point lead or an equal total crowns nobody.");

        private final String rulebook;
        private final boolean rulebookNamesIt;
        private final String rule;

        Context(String rulebook, boolean rulebookNamesIt, String rule) {
            this.rulebook = rulebook;
            this.rulebookNamesIt = rulebookNamesIt;
            this.rule = rule;
        }

        /** The rulebook line(s) this tie-break is read from. Never empty. */
        public String rulebook() { return rulebook; }

        /** Whether the cited clause ITSELF names the tie-break, as opposed to this
         *  class reading one into a silence. False is a standing disclosure. */
        public boolean rulebookNamesTieBreak() { return rulebookNamesIt; }

        /** The rule in one sentence. Never empty. */
        public String rule() { return rule; }
    }

    /**
     * True for a name that is a {@link Context} constant. The vocabulary check the
     * loader and the probe both ask, so "is this a tie-break we play by" has one
     * answer rather than one answer per caller.
     */
    public static boolean isKnownContext(String name) {
        if (name == null) return false;
        Context[] all = Context.values();
        for (int i = 0; i < all.length; i++) {
            if (all[i].name().equals(name)) return true;
        }
        return false;
    }

    // ── INITIATIVE_ORDER (rulebook :352) ────────────────────────────────────

    /**
     * Rulebook :352 ordering key: NEGATIVE when {@code a} acts BEFORE {@code b},
     * i.e. when {@code a} has the LOWER initiative and acts first.
     *
     * <p>Influence Rating first. On a tie :352 says the player with the highest
     * Diplomacy on his ambassador "wins (acts last)", so within a tie the LOWER
     * ability acts earlier; the same applies in turn to Intrigue, Psi and then
     * Leadership. Abilities are read through {@code getEffectiveStat}, so a
     * rotation/damage/bonus state cannot make the displayed order disagree with
     * the order played by.
     *
     * <p>Returns 0 when the chain is exhausted, which is the signal that :352's
     * final step applies. It is NOT "these two players are interchangeable" —
     * {@link #actsBefore} still separates them, by seat name.
     */
    public static int compareInitiativeKey(Player a, Player b) {
        if (a == null || b == null) return (a == b) ? 0 : (a == null ? -1 : 1);
        int c = a.getInfluence() - b.getInfluence();
        if (c != 0) return c;
        c = ambassadorAbility(a, StatKey.DIPLOMACY) - ambassadorAbility(b, StatKey.DIPLOMACY);
        if (c != 0) return c;
        c = ambassadorAbility(a, StatKey.INTRIGUE) - ambassadorAbility(b, StatKey.INTRIGUE);
        if (c != 0) return c;
        c = ambassadorAbility(a, StatKey.PSI) - ambassadorAbility(b, StatKey.PSI);
        if (c != 0) return c;
        return ambassadorAbility(a, StatKey.LEADERSHIP)
             - ambassadorAbility(b, StatKey.LEADERSHIP);
    }

    /** The ambassador ability :352 ranks, 0 when the seat has none or it is face
     *  down (a hidden ambassador has no readable ability to rank). */
    private static int ambassadorAbility(Player p, StatKey stat) {
        if (p == null) return 0;
        CharacterCard amb = p.getAmbassador();
        if (amb == null || amb.isFaceDown()) return 0;
        return amb.getEffectiveStat(stat);
    }

    /**
     * True when {@code a} acts BEFORE {@code b} in rulebook :352 order, i.e.
     * {@code a} holds the lower initiative.
     *
     * <p>Where {@link #compareInitiativeKey} returns 0 — the :352 chain is exhausted
     * and the rulebook says "determine initiative order between them randomly" —
     * seat NAME order decides instead. Two reasons, both recorded at
     * {@link Context#INITIATIVE_ORDER}: a random draw would make the mercenary
     * phase irreproducible, and ReplayRecorder exists to replay a game and compare
     * it against the recorded run.
     *
     * <p>This is the ONE place a total order is asserted rather than derived, so it
     * is also the only one that can be violated: two seats with the SAME name
     * compare equal here, and {@link #initiativeOrder} then leaves their relative
     * order down to the sort's stability. That is a property of seat naming, not a
     * rule, and the probe asserts it rather than papering over it.
     */
    public static boolean actsBefore(Player a, Player b) {
        if (a == b) return false;
        int c = compareInitiativeKey(a, b);
        if (c != 0) return c < 0;
        return a.getName().compareTo(b.getName()) < 0;
    }

    /**
     * The seats in the order they act, index 0 acting FIRST (lowest initiative,
     * rulebook :352). Unmodifiable; empty for null seats and for a null list.
     * Null members are dropped, matching what the callers iterate.
     */
    public static List<Player> initiativeOrder(List<Player> seats) {
        List<Player> ordered = new ArrayList<Player>();
        if (seats == null) return Collections.unmodifiableList(ordered);
        for (Player p : seats) {
            if (p != null) ordered.add(p);
        }
        Collections.sort(ordered, new Comparator<Player>() {
            @Override public int compare(Player a, Player b) {
                return actsBefore(a, b) ? -1 : (actsBefore(b, a) ? 1 : 0);
            }
        });
        return Collections.unmodifiableList(ordered);
    }

    // ── MERCENARY_CONTROL (rulebook :739 SILENT; B5-2281 substitute) ──────────

    /**
     * The winner among seats that tied on a cumulative total, chosen in rulebook
     * :352 initiative order.
     *
     * <p>The rulebook names no tie-break here, so this is the ADOPTED substitute
     * (B5-2281), not a quotation. It takes the tied seats and the frozen order
     * separately rather than assuming the tied list was built by walking the order,
     * because "the tied list happens to be pre-sorted" is exactly the kind of
     * unstated precondition that stops being true the moment a second caller exists.
     *
     * <p>Returns null when {@code tied} is empty or nothing in it appears in
     * {@code order}; a null {@code order} therefore yields null rather than an
     * arbitrary first element, because an undefined order is not a licence to
     * invent one.
     */
    public static Player tieBreakByInitiativeOrder(List<Player> tied, List<Player> order) {
        if (tied == null || tied.isEmpty() || order == null) return null;
        for (int i = 0; i < order.size(); i++) {
            Player candidate = order.get(i);
            if (candidate != null && tied.contains(candidate)) return candidate;
        }
        return null;
    }

    /**
     * True when the :352 chain could not separate the two seats, i.e. the rulebook's
     * final "determine initiative order between them randomly" step was the one that
     * decided. A tie-break resolved by an invented rule is the kind of outcome a
     * player must be able to audit after the fact, so callers announce it.
     */
    public static boolean initiativeChainExhausted(Player a, Player b) {
        return compareInitiativeKey(a, b) == 0;
    }

    // ── CONFLICT_PARTICIPATION (rulebook :550) ──────────────────────────────

    /**
     * Rulebook :550 — the initiator wins a conflict ONLY on strictly more support
     * than opposition. Equal totals are a loss for the initiator, which is the
     * clause's own wording ("the same amount or more opposition than support,
     * then the faction which initiated it loses"), not an interpretation.
     *
     * <p>An uncontested conflict (no opposition at all) is a win for the initiator
     * even at 0-0, which is the degenerate case of the same rule and is named here
     * so the second clause of the {@code if} is not read as a second rule.
     */
    public static boolean initiatorWinsConflict(int support, int opposition) {
        return support > opposition || opposition == 0;
    }

    /**
     * The opposition participant with the highest committed total, or null when
     * nobody opposed.
     *
     * <p>NOT permutation-invariant, deliberately. :550 nominates the opposing SIDE as
     * the winner but says nothing about which of two equally-strong opposers takes
     * it, so participation order — a real, recorded fact about the game — decides,
     * and the first participant to oppose wins. That makes the outcome stable for a
     * given sequence of joins (which is what a replay reproduces) without inventing
     * a rule the rulebook does not contain.
     */
    public static Player leadingOpposer(List<Player> opposers, Map<Player, Integer> totals) {
        if (opposers == null) return null;
        Player best    = null;
        int    bestVal = -1;
        for (Player p : opposers) {
            if (p == null) continue;
            int v = (totals != null && totals.containsKey(p))
                    ? totals.get(p).intValue() : 0;
            if (v > bestVal) { bestVal = v; best = p; }
        }
        return best;
    }

    /** The winner of a resolved conflict: the initiator when :550 says so, else the
     *  leading opposer. Returns null only for a null conflict. */
    public static Player resolveConflictWinner(Conflict conflict,
                                               Map<Player, Integer> totals) {
        if (conflict == null) return null;
        if (initiatorWinsConflict(conflict.supportTotal(), conflict.oppositionTotal())) {
            return conflict.getInitiator();
        }
        return leadingOpposer(oppositionsOf(conflict), totals);
    }

    private static List<Player> oppositionsOf(Conflict conflict) {
        return new ArrayList<Player>(conflict.getOpposers());
    }

    // ── COUNCIL_VOTE (rulebook :791 :908) ───────────────────────────────────

    /**
     * The Yes tally after the League of Non-Aligned Worlds has cast its one
     * tie-breaking vote, or {@code yes} unchanged when no vote was due.
     *
     * <p>:908 makes the call MANDATORY on an exact tie ("the League vote must be
     * called") and the League votes Yes. So a tied measure passes. An abstention is
     * not a vote: :908 gives the League its own abstention when ITS OWN species
     * ambassadors are tied, which is a different question and not reachable from a
     * Yes/No tally.
     */
    public static int leagueYesVote(int yes, int no) {
        return (yes == no) ? (yes + 1) : yes;
    }

    /** True when the League must be called, i.e. the measure is exactly tied. */
    public static boolean leagueMustBreakTie(int yes, int no) {
        return yes == no;
    }

    /** Whether a Council measure passes, after {@link #leagueYesVote}. */
    public static boolean councilPasses(int yes, int no) {
        return leagueYesVote(yes, no) >= (no + 1);
    }

    // ── STANDARD_VICTORY / MAJOR_VICTORY (rulebook :176 :182) ───────────────

    /**
     * The shared strict-leader predicate behind both victory paths.
     *
     * <p>{@code others} is the comparison list the CALLER has already filtered — a
     * surrendered or forfeited player is out of the game (B5-0661) and a
     * Standard-Victory-eligible seat is not compared against (rulebook :178). That
     * filtering is an eligibility question, not a tie-break, so it stays at the
     * call site; the STRICTNESS — equal crowns nobody — is the tie-break and lives
     * here.
     *
     * <p>Excludes {@code p} from the comparison by identity, so a candidate is never
     * blocked by itself.
     */
    public static boolean isStrictlyLeading(Player p, List<Player> others) {
        if (p == null) return false;
        if (others == null) return true;
        for (Player q : others) {
            if (q == null || q == p) continue;
            if (q.getInfluence() >= p.getInfluence()) return false;
        }
        return true;
    }

    /**
     * The single candidate among {@code candidates} that strictly leads
     * {@code all}, or null when none does or more than one does.
     *
     * <p>The "more than one" arm cannot happen for two distinct candidates — a
     * strict lead is antisymmetric — but it is counted rather than assumed, because
     * {@code stationVictory}'s eligibility filter differs from its comparison filter
     * and that asymmetry is precisely where a second leader could appear.
     */
    public static Player soleStrictLeader(List<Player> candidates, List<Player> all) {
        if (candidates == null) return null;
        Player leader = null;
        int leaders   = 0;
        for (Player p : candidates) {
            if (p == null) continue;
            if (isStrictlyLeading(p, all)) { leader = p; leaders++; }
        }
        return (leaders == 1) ? leader : null;
    }

    /**
     * Rulebook :182 — "at least 10 more than each other player". A 9-point lead or
     * an equal total crowns nobody. {@code others} is caller-filtered as in
     * {@link #isStrictlyLeading}.
     */
    public static boolean clearsMarginByTen(Player p, List<Player> others) {
        if (p == null) return false;
        if (others == null) return true;
        for (Player q : others) {
            if (q == null || q == p) continue;
            if (q.getInfluence() > p.getInfluence() - 10) return false;
        }
        return true;
    }
}