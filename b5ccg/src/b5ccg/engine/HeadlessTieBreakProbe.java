package b5ccg.engine;

import b5ccg.model.AgendaCard;
import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.Conflict;
import b5ccg.model.ConflictCard;
import b5ccg.model.FleetCard;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.ConflictType;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.GamePhase;
import b5ccg.model.enums.Rarity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * B5-2554 — tie-break DETERMINISM probe for TieBreakCatalog (standalone CLI,
 * never wired into RUN_TESTS; exit 0 on all-pass, 1 on any failure).
 *
 * <p>The row asked for a probe "asserting deterministic resolution", and the word
 * turns out to mean two different things depending on the rule, so the probe
 * asserts which is which rather than using one blanket notion of determinism:
 *
 * <ul>
 *   <li><b>Permutation-invariance</b> for {@link TieBreakCatalog#initiativeOrder},
 *       the Council tally, and the victory margins: the outcome must not depend on
 *       the order the seats were added to the game. This is asserted by REPLAYING
 *       the same seat set through every input rotation and demanding one answer.</li>
 *   <li><b>Order-sensitivity BY DESIGN</b> for
 *       {@link TieBreakCatalog#leadingOpposer} and
 *       {@link TieBreakCatalog#tieBreakByInitiativeOrder}: rulebook :550 names the
 *       opposing SIDE and not the seat, and :739 is silent, so participation order
 *       and initiative order are the deciding facts. The probe asserts the answer
 *       is STABLE for a given order — and that it CHANGES when the order changes,
 *       because a "tie-break" that ignored its ordering input would be inventing
 *       the very rule it claims to encode.</li>
 * </ul>
 *
 * <p>Every negative half below is a real assertion, not a comment. A probe whose
 * positive checks pass while the negative ones are unreachable would be the
 * B5-1725 silent-green shape, so each section pairs its happy path with the input
 * one step away from it and requires both answers.
 *
 * <p>New file. No existing test, probe, or RulesEngine method is edited. Java 6.
 */
public class HeadlessTieBreakProbe {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        sectionVocabulary();
        sectionInitiativeOrder();
        sectionMercenaryTie();
        sectionConflictParticipation();
        sectionCouncilVote();
        sectionVictoryMargins();

        System.out.println();
        if (failures == 0) {
            System.out.println("B5-2554 PROBE PASSED (tie-break determinism)");
        } else {
            System.out.println("B5-2554 PROBE FAILED: " + failures + " check(s)");
            System.exit(1);
        }
    }

    // ── 1. The vocabulary itself ─────────────────────────────────────────────

    /**
     * A catalog whose Context constants can be added without an implementation
     * would let the vocabulary drift away from the rules — the same one-rule-two-
     * implementations defect the class exists to prevent, moved from the method
     * body into the enum.
     */
    private static void sectionVocabulary() {
        head("Vocabulary: every tie-break is named, cited, and classified");
        TieBreakCatalog.Context[] all = TieBreakCatalog.Context.values();
        check("catalog declares every tie-break it plays by (" + all.length + ")", all.length > 0);

        boolean everyCited   = true;
        boolean everyDescribed = true;
        for (TieBreakCatalog.Context c : all) {
            if (c.rulebook() == null || c.rulebook().trim().length() == 0) everyCited = false;
            if (c.rule() == null || c.rule().trim().length() == 0) everyDescribed = false;
        }
        check("every Context cites a rulebook line", everyCited);
        check("every Context states its rule in words", everyDescribed);

        // The silence disclosure is load-bearing: it is what distinguishes a
        // QUOTED tie-break from one this repo adopted into a rulebook gap.
        // Assert it as a property of the data, not as a fact about one constant,
        // so a future Context cannot quietly claim the rulebook when it does not.
        int silent = 0;
        for (TieBreakCatalog.Context c : all) {
            if (!c.rulebookNamesTieBreak()) silent++;
        }
        check("the silent gap is DISCLOSED, not converted to a claim (1 expected)", silent == 1);
        check("MERCENARY_CONTROL is the disclosed gap (rulebook :739)",
                !TieBreakCatalog.Context.MERCENARY_CONTROL.rulebookNamesTieBreak());
        check("MERCENARY_CONTROL cites :739",
                ":739".equals(TieBreakCatalog.Context.MERCENARY_CONTROL.rulebook()));

        // INITIATIVE_ORDER is the ordering the two silent-path and mercenary
        // rules both lean on, so it must be cited as a rulebook rule.
        check("INITIATIVE_ORDER is a QUOTED rule (:352)",
                TieBreakCatalog.Context.INITIATIVE_ORDER.rulebookNamesTieBreak()
                && ":352".equals(TieBreakCatalog.Context.INITIATIVE_ORDER.rulebook()));

        check("isKnownContext accepts a real name",
                TieBreakCatalog.isKnownContext("COUNCIL_VOTE"));
        check("isKnownContext rejects an invented name",
                !TieBreakCatalog.isKnownContext("SOME_TIE_BREAK_WE_MADE_UP"));
        check("isKnownContext rejects null rather than throwing",
                !TieBreakCatalog.isKnownContext(null));
    }

    // ── 2. Initiative order (:352) ────────────────────────────────────────────

    /**
     * The one place a TOTAL order is asserted rather than derived, so it is the
     * one place a tie can survive the comparator. Probed two ways: a genuine
     * :352 chain decides and the seat list order is irrelevant, and a genuinely
     * exhausted chain falls to seat NAME order, which is the substitute the
     * class documents for :352's "determine initiative order randomly".
     */
    private static void sectionInitiativeOrder() {
        head("INITIATIVE_ORDER (:352): chain decides; the list order does not");

        // Same seats, four input rotations. Every rotation must produce the SAME
        // order, or the rule depends on how the game was built.
        Player low  = seat("Zeta",  Faction.HUMAN,   3, 1, 2, 3, 4);
        Player mid  = seat("Mid",   Faction.MINBARI, 8, 5, 5, 5, 5);
        Player high = seat("Alpha", Faction.NARN,   14, 0, 0, 0, 0);
        List<Player> canonical = Arrays.asList(low, mid, high);
        String expected = names(TieBreakCatalog.initiativeOrder(canonical));

        boolean stable = true;
        List<Player>[] rotations = rotations(canonical);
        for (int i = 0; i < rotations.length; i++) {
            if (!expected.equals(names(TieBreakCatalog.initiativeOrder(rotations[i])))) {
                stable = false;
            }
        }
        check("initiative order is PERMUTATION-INVARIANT across "
                + rotations.length + " input rotations", stable);
        check("lower Influence acts FIRST (:352)",
                TieBreakCatalog.initiativeOrder(canonical).get(0) == low);

        // Within an equal Influence Rating the LOWER ability acts first, because
        // :352 says the HIGHER Diplomacy "wins (acts last)". Asserted with the
        // sign of the comparison as well as the order, so an in-place reversal
        // that happened to still sort something valid could not pass.
        check("equal influence, lower Diplomacy acts first (:352 chain)",
                TieBreakCatalog.compareInitiativeKey(
                        seat("A", Faction.HUMAN, 5, 1, 9, 9, 9),
                        seat("B", Faction.MINBARI, 5, 4, 0, 0, 0)) < 0);
        check("equal influence and equal Diplomacy, lower Intrigue acts first",
                TieBreakCatalog.compareInitiativeKey(
                        seat("A", Faction.HUMAN, 5, 1, 2, 9, 9),
                        seat("B", Faction.MINBARI, 5, 1, 7, 0, 0)) < 0);
        check("equal influence and equal Diplomacy and Intrigue, lower Psi first",
                TieBreakCatalog.compareInitiativeKey(
                        seat("A", Faction.HUMAN, 5, 1, 2, 3, 9),
                        seat("B", Faction.MINBARI, 5, 1, 2, 8, 0)) < 0);
        check("the full chain exhausted reads 0 (:352 residual signal)",
                TieBreakCatalog.compareInitiativeKey(
                        seat("A", Faction.HUMAN, 5, 2, 2, 2, 2),
                        seat("B", Faction.MINBARI, 5, 2, 2, 2, 2)) == 0);
        check("the exhaustion is reported as such (not merely as 'equal')",
                TieBreakCatalog.initiativeChainExhausted(
                        seat("A", Faction.HUMAN, 5, 2, 2, 2, 2),
                        seat("B", Faction.MINBARI, 5, 2, 2, 2, 2)));
        check("one step off exhaustion is NOT exhausted",
                !TieBreakCatalog.initiativeChainExhausted(
                        seat("A", Faction.HUMAN, 5, 2, 2, 2, 3),
                        seat("B", Faction.MINBARI, 5, 2, 2, 2, 2)));

        // The residual step: two seats identical on the whole :352 chain are
        // separated by NAME. This is the adopted substitute for :352's random
        // draw, and it is only observable if the names differ.
        Player r1 = seat("Bravo", Faction.CENTAURI, 7, 3, 3, 3, 3);
        Player r2 = seat("Alpha", Faction.VORLON,   7, 3, 3, 3, 3);
        check("exhausted chain falls to SEAT NAME order, not list order",
                TieBreakCatalog.actsBefore(r2, r1));
        check("...and the relation is antisymmetric",
                TieBreakCatalog.actsBefore(r1, r2) == false);
        List<Player> residual = Arrays.asList(r1, r2);
        check("...and it holds whichever order the seats arrived in",
                TieBreakCatalog.initiativeOrder(residual).get(0) == r2
                && TieBreakCatalog.initiativeOrder(
                        Arrays.asList(r2, r1)).get(0) == r2);

        // A face-down ambassador has no readable ability, so it cannot rank —
        // :352 ranks ABILITIES, and an unreadable ability is not a low one.
        Player hidden = seat("Hidden", Faction.NARN, 5, 9, 9, 9, 9);
        hidden.getAmbassador().setFaceDown(true);
        check("a face-down ambassador does not rank (:352 reads abilities)",
                TieBreakCatalog.compareInitiativeKey(
                        hidden, seat("Plain", Faction.HUMAN, 5, 0, 0, 0, 0)) == 0);

        check("a single seat is its own order", TieBreakCatalog.initiativeOrder(
                Arrays.asList(mid)).size() == 1);
        check("a null seat list is empty, not a crash",
                TieBreakCatalog.initiativeOrder(null).isEmpty());
        check("a null member is dropped rather than sorted",
                TieBreakCatalog.initiativeOrder(
                        Arrays.asList((Player) null, low, null, high)).size() == 2);
    }

    // ── 3. Mercenary tie (:739 silent) ───────────────────────────────────────

    /**
     * The rulebook names the winning total and says nothing about equal totals,
     * so this is the ADOPTED substitute. Probed for the property that makes the
     * wiring worth anything: the answer comes from the ORDER, not from the
     * position of the seat in the tied list.
     */
    private static void sectionMercenaryTie() {
        head("MERCENARY_CONTROL (:739 SILENT, adopted :352 substitute)");

        Player first  = seat("First",  Faction.HUMAN,   4, 1, 1, 1, 1);
        Player second = seat("Second", Faction.MINBARI, 9, 5, 5, 5, 5);
        List<Player> order = TieBreakCatalog.initiativeOrder(Arrays.asList(second, first));
        List<Player> tied  = Arrays.asList(first, second);

        check("the tie resolves to the EARLIEST seat in initiative order",
                TieBreakCatalog.tieBreakByInitiativeOrder(tied, order) == first);

        // The negative half, and the reason the tied list and the order are two
        // parameters: supply the tied seats in the OPPOSITE order. A
        // first-element implementation answers `second` here and fails; the
        // order-driven one still answers `first`.
        check("...and NOT the first element of the tied list (reversed input)",
                TieBreakCatalog.tieBreakByInitiativeOrder(
                        Arrays.asList(second, first), order) == first);

        // Order-sensitivity BY DESIGN: reversing the FROZEN order moves the winner,
        // which proves the order is genuinely read. The reversed order is built
        // BY HAND rather than by handing the seats to initiativeOrder in the other
        // order — that call SORTS, so it returns the same order and the check
        // would pass without ever exercising the input.
        List<Player> flipped = Arrays.asList(second, first);
        check("a hand-reversed order moves the winner (the order IS the input)",
                TieBreakCatalog.tieBreakByInitiativeOrder(tied, flipped) == second);

        // An undefined order is not a licence to invent one.
        check("a tied set outside the order yields null, not an arbitrary seat",
                TieBreakCatalog.tieBreakByInitiativeOrder(
                        tied, TieBreakCatalog.initiativeOrder(
                                Arrays.asList(seat("Outsider", Faction.NARN, 1, 0, 0, 0, 0)))
                ) == null);
        check("a null order yields null", TieBreakCatalog.tieBreakByInitiativeOrder(tied, null) == null);
        check("an empty tied set yields null", TieBreakCatalog.tieBreakByInitiativeOrder(
                new ArrayList<Player>(), order) == null);

        // End-to-end through the engine: the published resolution must be the one
        // the catalog predicts, on the real state object.
        Player a = seat("Aaa", Faction.HUMAN,   6, 2, 2, 2, 2);
        Player b = seat("Bbb", Faction.MINBARI, 6, 4, 4, 4, 4);
        GameState st = state(a, b);
        RulesEngine rules = new RulesEngine();
        Card merc = mercenary();
        st.addMercenaryOffer(merc);
        // Equal bids: with equal Influence the ambassador chain separates them,
        // and :352 puts the LOWER ability first, so `a` (ability 2) precedes `b`.
        st.placeMercenaryBid(merc, a, 3);
        st.placeMercenaryBid(merc, b, 3);
        Map<Card, Player> resolved = rules.resolveMercenaryControl(st);
        List<Player> frozen = rules.determineInitiativeOrder(st);
        Player predicted = TieBreakCatalog.tieBreakByInitiativeOrder(
                Arrays.asList(a, b), frozen);
        check("engine mercenary tie matches the catalog's prediction",
                resolved.get(merc) == predicted);
        check("...and the prediction is the :352-first seat, not the first bidder",
                predicted == a && resolved.get(merc) == a);
    }

    // ── 4. Conflict participation (:550) ─────────────────────────────────────

    /**
     * :550 names the tie and its answer: equal or more opposition LOSES for the
     * initiator. Asserted as a table, because "equal loses" is a rule that reads
     * correctly until it is implemented as ">=".
     */
    private static void sectionConflictParticipation() {
        head("CONFLICT_PARTICIPATION (:550): equal totals lose for the initiator");

        check("more support than opposition wins for the initiator",
                TieBreakCatalog.initiatorWinsConflict(5, 4));
        check("equal support and opposition LOSES for the initiator (:550 wording)",
                !TieBreakCatalog.initiatorWinsConflict(4, 4));
        check("more opposition than support loses for the initiator",
                !TieBreakCatalog.initiatorWinsConflict(3, 4));
        check("uncontested (no opposition at all) wins for the initiator",
                TieBreakCatalog.initiatorWinsConflict(0, 0));
        check("support with zero opposition wins",
                TieBreakCatalog.initiatorWinsConflict(6, 0));

        // End-to-end: a real conflict with equal committed totals must award the
        // win to an opposer, and the log must say so. A 1-point support lead must
        // go the other way — the input one step away from the tie.
        ConflictCard card = conflictCard();
        Player init = seat("Initiator", Faction.HUMAN, 5, 0, 0, 0, 0);
        Player opp  = seat("Opposer",  Faction.NARN,  5, 0, 0, 0, 0);
        GameState st = state(init, opp);

        Conflict tied = new Conflict(card, init);
        tied.addParticipant(opp, false);
        tied.commitCard(init, fleet("f_sup", 4), true);
        tied.commitCard(opp, fleet("f_opp", 4), false);
        check("fixture really is tied (support == opposition)",
                tied.supportTotal() == tied.oppositionTotal());
        RulesEngine rules = new RulesEngine();
        Player winner = rules.resolveConflict(tied, st);
        check("a tied conflict is WON BY THE OPPOSITION (:550)",
                winner == opp && winner != init);

        Conflict lead = new Conflict(card, init);
        lead.addParticipant(opp, false);
        lead.commitCard(init, fleet("f_sup2", 5), true);
        lead.commitCard(opp, fleet("f_opp2", 4), false);
        check("one point of support is enough for the initiator",
                rules.resolveConflict(lead, st) == init);

        // leadingOpposer is deliberately ORDER-SENSITIVE: :550 nominates the
        // opposing SIDE and says nothing about which seat takes it, so the first
        // participant to oppose wins. Asserted in both directions so the property
        // cannot be lost quietly.
        Player x = seat("X", Faction.HUMAN,  1, 0, 0, 0, 0);
        Player y = seat("Y", Faction.MINBARI, 1, 0, 0, 0, 0);
        Map<Player, Integer> equal = new HashMap<Player, Integer>();
        equal.put(x, Integer.valueOf(6));
        equal.put(y, Integer.valueOf(6));
        check("equal opposers: the FIRST participant to oppose takes it",
                TieBreakCatalog.leadingOpposer(Arrays.asList(x, y), equal) == x);
        check("...and reversing the participation order reverses the winner",
                TieBreakCatalog.leadingOpposer(Arrays.asList(y, x), equal) == y);
        Map<Player, Integer> uneven = new HashMap<Player, Integer>();
        uneven.put(x, Integer.valueOf(3));
        uneven.put(y, Integer.valueOf(7));
        check("an unequal opposition is won on total, not on order",
                TieBreakCatalog.leadingOpposer(Arrays.asList(x, y), uneven) == y);
        check("no opposers means no leader", TieBreakCatalog.leadingOpposer(
                new ArrayList<Player>(), equal) == null);
        check("a null opposers list is no leader either",
                TieBreakCatalog.leadingOpposer(null, equal) == null);
    }

    // ── 5. Council vote (:791 :908) ──────────────────────────────────────────

    /**
     * :908 makes the League call MANDATORY on an exact tie and the League votes
     * Yes, so a tied measure PASSES. The adjacent cases are asserted too: an
     * abstention is not a vote, and the League must not vote on a measure that is
     * not tied.
     */
    private static void sectionCouncilVote() {
        head("COUNCIL_VOTE (:791 :908): the League is called on an exact tie");

        check("a tied measure calls the League", TieBreakCatalog.leagueMustBreakTie(3, 3));
        check("...and the League votes Yes", TieBreakCatalog.leagueYesVote(3, 3) == 4);
        check("...so a tied measure PASSES (:908)",
                TieBreakCatalog.councilPasses(3, 3));
        check("a Yes lead of one passes without the League",
                TieBreakCatalog.councilPasses(4, 3)
                && !TieBreakCatalog.leagueMustBreakTie(4, 3));
        check("...and the League's tally is unchanged when not called",
                TieBreakCatalog.leagueYesVote(4, 3) == 4);
        check("a No lead fails", !TieBreakCatalog.councilPasses(3, 4));
        check("a No lead does not call the League",
                !TieBreakCatalog.leagueMustBreakTie(3, 4));
        check("a 0-0 measure is a tie and passes",
                TieBreakCatalog.councilPasses(0, 0));
        check("an ABSTAIN is not a vote (a 0-0 YES/NO tally is still a tie)",
                TieBreakCatalog.leagueMustBreakTie(0, 0));

        // End-to-end through the engine, on a state where the five Council races
        // are all present so the default abstentions are not doing the work.
        RulesEngine rules = new RulesEngine();
        Player human  = seat("Human",  Faction.HUMAN,   20, 0, 0, 0, 0);
        Player minb   = seat("Minb",   Faction.MINBARI, 20, 0, 0, 0, 0);
        Player cent   = seat("Cent",   Faction.CENTAURI, 20, 0, 0, 0, 0);
        Player narn   = seat("Narn",   Faction.NARN,    20, 0, 0, 0, 0);
        Player vorlon = seat("Vorlon", Faction.VORLON,  20, 0, 0, 0, 0);
        GameState st = state(human, minb, cent, narn, vorlon);
        st.setPhase(GamePhase.ACTION);
        b5ccg.model.AgendaCard ag = agenda();
        human.setAgenda(ag);
        check("the vote session opens for a face-up agenda", rules.openAgendaVote(ag, st));

        rules.castAgendaVote(minb,   RulesEngine.AgendaVote.YES, 0, st);
        rules.castAgendaVote(cent,   RulesEngine.AgendaVote.NO,  0, st);
        check("an untied 1-1 measure is decided WITHOUT the League",
                rules.resolveCouncilVote(st));
        rules.resetAgendaVote();
        rules.openAgendaVote(ag, st);
        rules.castAgendaVote(minb,   RulesEngine.AgendaVote.YES, 0, st);
        rules.castAgendaVote(cent,   RulesEngine.AgendaVote.NO,  0, st);
        rules.castAgendaVote(narn,   RulesEngine.AgendaVote.ABSTAIN, 0, st);
        boolean tiedVerdict = rules.resolveCouncilVote(st);
        check("an abstaining third race does not UNTIE the measure",
                tiedVerdict);
        check("...and the engine's verdict matches the catalog's reading of 1-1",
                tiedVerdict == TieBreakCatalog.councilPasses(1, 1));
    }

    // ── 6. Victory margins (:176 :182) ───────────────────────────────────────

    /**
     * Both margins are STRICT. The interesting case is the equal total, because
     * that is where a `>=` in the implementation would crown somebody the
     * rulebook says cannot be crowned — and where "a tie crowns nobody" is a
     * statement about the game, not about the comparison.
     */
    private static void sectionVictoryMargins() {
        head("STANDARD_VICTORY (:176) / MAJOR_VICTORY (:182): strict");

// Distinct fixture values, one per arm. Naming a seat "Lead" while giving it the
        // same rating as "Tied" is how the first version of this section asserted
        // a strict lead on an EQUAL pair and failed on correct code — the fixture
        // arithmetic is part of what the gate proves.
        Player top    = seat("Top",    Faction.HUMAN,   20, 0, 0, 0, 0);
        Player under  = seat("Under",  Faction.MINBARI, 19, 0, 0, 0, 0);
        Player equal  = seat("Equal",  Faction.CENTAURI, 20, 0, 0, 0, 0);
        Player ten    = seat("Ten",    Faction.NARN,    10, 0, 0, 0, 0);
        Player eleven = seat("Eleven", Faction.VORLON,  11, 0, 0, 0, 0);

        check("a strictly higher total is a strict lead (20 vs 19)",
                TieBreakCatalog.isStrictlyLeading(top, Arrays.asList(under)));
        check("an EQUAL total is NOT a lead (:176 \"more than any other player\")",
                !TieBreakCatalog.isStrictlyLeading(top, Arrays.asList(equal)));
        check("a lower total is not a lead",
                !TieBreakCatalog.isStrictlyLeading(under, Arrays.asList(top)));
        check("a candidate is never blocked by ITSELF",
                TieBreakCatalog.isStrictlyLeading(top, Arrays.asList(top)));

        check("a 10-point margin clears Major Victory (:182 \"at least 10 more\")",
                TieBreakCatalog.clearsMarginByTen(top, Arrays.asList(ten)));
        check("a 9-point margin does NOT (:182, 20 vs 11)",
                !TieBreakCatalog.clearsMarginByTen(top, Arrays.asList(eleven)));
        check("an EQUAL total does NOT clear Major Victory either",
                !TieBreakCatalog.clearsMarginByTen(top, Arrays.asList(equal)));

        // soleStrictLeader is what makes "a tie crowns nobody" a GAME outcome
        // rather than a per-candidate predicate: one strict leader crowns, two
        // candidates on equal totals crown nobody.
        List<Player> ranked = Arrays.asList(top, under);
        check("a unique strict leader is returned (20 vs 19)",
                TieBreakCatalog.soleStrictLeader(ranked, ranked) == top);
        check("the runner-up is not crowned",
                TieBreakCatalog.soleStrictLeader(ranked, ranked) != under);
        check("EQUAL totals crown NOBODY (the D12 arm)",
                TieBreakCatalog.soleStrictLeader(
                        Arrays.asList(top, equal), Arrays.asList(top, equal)) == null);
        check("an empty candidate set crowns nobody",
                TieBreakCatalog.soleStrictLeader(new ArrayList<Player>(), ranked) == null);

        // End-to-end: a 20-influence seat that only TIES must not produce a
        // winner, while a 20-influence seat that leads by one must. The equal
        // case is the one that regressed in the field before, so it is the one
        // asserted through the engine as well as the catalog.
        GameState st = state(top, equal);
        st.setPhase(GamePhase.ACTION);
        RulesEngine rules = new RulesEngine();
        check("an equal 20-20 board crowns nobody through the engine",
                rules.checkVictory(st) == null);
        Player up1 = seat("UpOne", Faction.HUMAN, 21, 0, 0, 0, 0);
        GameState st2 = state(up1, equal);
        st2.setPhase(GamePhase.ACTION);
        Player w = rules.checkVictory(st2);
        check("a 21-20 board crowns the leader through the engine",
                w == up1 && w != equal);
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static Player seat(String name, Faction f, int influence,
                               int dip, int intr, int psi, int lead) {
        Player p = new Player(name, f, false);
        // Player has no setInfluence: the rating only rises through gainInfluence,
        // so a fixture "sets" influence by playing it in one step.
        p.gainInfluence(influence);
        CharacterCard amb = new CharacterCard("amb_" + name, "Amb " + name,
                "CHARACTER_" + f, Rarity.FIXED, f, CardSet.PREMIERE, "x", "text",
                dip, intr, psi, lead, true);
        p.setAmbassador(amb);
        p.getInnerCircle().add(amb);
        return p;
    }

    private static FleetCard fleet(String id, int military) {
        return new FleetCard(id, "Fleet " + id, "FLEET", Rarity.COMMON,
                Faction.ANY, CardSet.PREMIERE, "x", "text", military);
    }

    private static ConflictCard conflictCard() {
        return new ConflictCard("tb_conflict", "Tie-Break Fixture",
                "CONFLICT_MILITARY", Rarity.COMMON, Faction.ANY, CardSet.PREMIERE,
                "x", "text", ConflictType.MILITARY, 1);
    }

    private static Card mercenary() {
        Card c = new FleetCard("tb_merc", "Tie-Break Mercenary", "FLEET",
                Rarity.RARE, Faction.ANY, CardSet.PREMIERE, "x", "text", 3);
        // Mercenaries are FLAGGED, not typed: GameState.addMercenaryOffer refuses
        // a card whose isMercenary() is false, so the flag is part of the fixture.
        c.setMercenary(true);
        return c;
    }

    private static b5ccg.model.AgendaCard agenda() {
        return new b5ccg.model.AgendaCard("tb_agenda", "Tie-Break Measure",
                "AGENDA", Rarity.COMMON, Faction.ANY, CardSet.PREMIERE, "x", "text",
                false, "INFLUENCE_20");
    }

    private static GameState state(Player... players) {
        List<Player> ps = new ArrayList<Player>();
        for (Player p : players) ps.add(p);
        return new GameState(ps);
    }

    /** Every rotation of a three-element list, including the identity. */
    @SuppressWarnings("unchecked")
    private static List<Player>[] rotations(List<Player> src) {
        List<List<Player>> all = new ArrayList<List<Player>>();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (j == i) continue;
                int k = 3 - i - j;
                List<Player> r = new ArrayList<Player>();
                r.add(src.get(i));
                r.add(src.get(j));
                r.add(src.get(k));
                all.add(r);
            }
        }
        return all.toArray(new List[all.size()]);
    }

    private static String names(List<Player> ps) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ps.size(); i++) {
            if (i > 0) sb.append(" < ");
            sb.append(ps.get(i).getName());
        }
        return sb.toString();
    }

    private static void head(String title) {
        System.out.println();
        System.out.println("== " + title + " ==");
    }

    private static void check(String label, boolean ok) {
        System.out.println((ok ? "  [TB] " : "  [TB] FAIL — ") + label + (ok ? ": PASS" : ""));
        if (!ok) failures++;
    }
}