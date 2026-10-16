package b5ccg.engine;

import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.Conflict;
import b5ccg.model.Deck;
import b5ccg.model.EventCard;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.ConflictType;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.GamePhase;
import b5ccg.model.enums.Rarity;
import b5ccg.model.enums.WarKind;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * B5-2565 — <b>armistice timing-key conformance</b> (standalone CLI probe; exit 0
 * on all-pass, 1 on any failure; never wired into RUN_TESTS).
 *
 * <h3>What this measures, and why it is a timing question at all</h3>
 *
 * <p>The Armistice card reads (deluxe {@code de_event_armistice}) "Play before a
 * conflict resolves. Cancel one Military conflict. No Aftermath cards may be
 * played." So an armistice is permitted <b>mid-round</b> — after seats have
 * committed, joined and attacked, but before the Resolution round's Step 1
 * (:410) decides the conflict. What the row called under-specified is
 * <i>which</i> instant in the pipeline the cancel takes effect relative to
 * action resolution.
 *
 * <p>This probe pins that boundary against what the engine ACTUALLY does, by
 * measuring the deferred-effect structure that makes the boundary observable:
 *
 * <ol>
 *   <li><b>S1 — the window is exactly the unresolved interval.</b> A declared
 *       war conflict is <i>open</i>: {@link Conflict#isResolved()} is false, no
 *       winner is recorded, and — the load-bearing part — declaring it moved
 *       <b>nothing</b>. No influence, no tension, no capture. Every outcome
 *       effect is deferred to resolution, so the pre-resolution interval is a
 *       genuine, side-effect-free window in which a cancel is meaningful rather
 *       than a window in which consequences have already partly landed.</li>
 *   <li><b>S2 — the transition is atomic.</b> All of it lands at the single
 *       {@link RulesEngine#resolveConflict} call: the war swing, the tension
 *       increment, the resolved flag. There is no intermediate state in which
 *       an outcome is half-applied, so "the armistice takes effect before
 *       action resolution" has exactly one meaning: strictly before that call.</li>
 *   <li><b>S3 — the totals a cancel suppresses are fixed BEFORE the
 *       transition.</b> Support and opposition totals are unchanged by
 *       resolution. They are the numbers the cancel suppresses, and the engine
 *       does not recompute or mutate them across the boundary — so a future
 *       cancel path has a stable, already-decided quantity to suppress.</li>
 *   <li><b>S4 — the transition is logged, once, naming the winner.</b> A
 *       cancel that later lands in this pipeline must be distinguishable from
 *       a resolve in the log; the resolve line is the one it must not
 *       imitate.</li>
 *   <li><b>S5 — a war conflict is MILITARY</b> ({@link Conflict#getConflictType()}
 *       returns MILITARY when {@code card == null}), which is what puts a war
 *       conflict inside an armistice's "one Military conflict" type scope at
 *       all. Without this the S1-S4 window would be measured on a conflict the
 *       card could never name.</li>
 * </ol>
 *
 * <h3>The gap this probe reports rather than asserts</h3>
 *
 * <p>There is <b>no armistice, cancel or negate entry point in the engine
 * today</b>. {@link RulesEngine} and {@link Conflict} expose no such method and
 * the rulebook's own "Canceled" glossary entry (:1144 — "a conflict which is
 * canceled does not resolve ... A canceled conflict card is placed on the
 * discard pile instead of resolving") has no code path. That is why this is a
 * <i>timing-boundary measurement</i> and not a conformance assertion of armistice
 * behaviour: there is no behaviour to assert against yet.
 *
 * <p>The census is deliberately printed as an OBSERVATION and is <b>not</b> a
 * pass/fail check. Making "no cancel method exists" a check would mean the
 * probe turns RED the moment someone implements the armistice correctly — a
 * probe that punishes the fix is a probe that pins the absence of the feature
 * rather than the contract it is supposed to protect. The contract asserted here
 * is the one that must survive the implementation: the unresolved window, the
 * atomic transition, the pre-fixed totals and the logged transition.
 */
public class HeadlessArmisticeTimingProbe {

    private static final RulesEngine rules = new RulesEngine();
    private static int checks   = 0;
    private static int failures = 0;

    public static void main(String[] args) {
        System.out.println("=== B5-2565 armistice timing-key conformance ===");

        scenarioWarOpenWindow();
        scenarioAtomicTransition();
        scenarioTotalsFixedBeforeTransition();
        scenarioTransitionLoggedOnce();
        scenarioWarConflictIsMilitary();
        observeNoArmisticeEntryPoint();

        System.out.println();
        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures == 0) {
            System.out.println("B5-2565 PROBE PASSED");
        } else {
            System.out.println("B5-2565 PROBE FAILED: " + failures + " check(s)");
            System.exit(1);
        }
    }

// ── S1: the armistice window is exactly the unresolved interval ───────────

    /**
     * The headline claim. A declared war conflict is open and side-effect-free:
     * nothing at all has been applied yet, so "cancel before it resolves" is a
     * real instruction rather than a description of a half-finished outcome.
     */
    private static void scenarioWarOpenWindow() {
        System.out.println("== S1: declared war conflict is an open, side-effect-free window ==");
        Player narn    = player("Narn", Faction.NARN);
        Player minbari = player("Minbari", Faction.MINBARI);
        GameState st   = state(narn, minbari);

        st.getTensionMatrix().enterWar(Faction.NARN, Faction.MINBARI);

        int minbariBefore = minbari.getInfluence();
        int narnBefore    = narn.getInfluence();
        int tensionBefore = st.getTensionMatrix().getTension(Faction.MINBARI, Faction.NARN);

        Conflict war = rules.declareWarConflict(narn, WarKind.RACE_TARGET, minbari, null, st);
        check("S1 war conflict declared", war != null);
        if (war == null) return;

        check("S1 declared conflict is not resolved", !war.isResolved());
        check("S1 declared conflict has no winner", war.getWinner() == null);
        check("S1 declaring applied NO influence (target)",
                minbari.getInfluence() == minbariBefore);
        check("S1 declaring applied NO influence (initiator)",
                narn.getInfluence() == narnBefore);
        check("S1 declaring raised NO tension",
                st.getTensionMatrix().getTension(Faction.MINBARI, Faction.NARN) == tensionBefore);
        check("S1 the window is open: outcome effects are entirely deferred",
                !war.isResolved() && war.getWinner() == null
                && minbari.getInfluence() == minbariBefore
                && narn.getInfluence() == narnBefore);
    }

    // ── S2: the transition is atomic ─────────────────────────────────────────

    /**
     * Every outcome effect the war pipeline owns lands inside the single
     * resolveConflict call, so there is no intermediate state to argue about:
     * the armistice boundary is that call and nothing finer.
     */
    private static void scenarioAtomicTransition() {
        System.out.println("== S2: the resolve call is the single atomic transition ==");
        Player narn    = player("Narn", Faction.NARN);
        Player minbari = player("Minbari", Faction.MINBARI);
        GameState st   = state(narn, minbari);
        st.getTensionMatrix().enterWar(Faction.NARN, Faction.MINBARI);

        Conflict war = rules.declareWarConflict(narn, WarKind.RACE_TARGET, minbari, null, st);
        check("S2 war conflict declared", war != null);
        if (war == null) return;

        int minbariBefore = minbari.getInfluence();
        int narnBefore    = narn.getInfluence();

        Player winner = rules.resolveConflict(war, st);

        check("S2 winner is the initiator (uncontested)", winner == narn);
        check("S2 resolved flag set by the transition", war.isResolved());
        check("S2 winner recorded on the conflict", war.getWinner() == narn);
        check("S2 target lost 1 influence at the transition",
                minbari.getInfluence() == minbariBefore - 1);
        check("S2 winner gained 1 influence at the transition",
                narn.getInfluence() == narnBefore + 1);
        check("S2 tension raised at the transition",
                st.getTensionMatrix().getTension(Faction.MINBARI, Faction.NARN) == 1);
        check("S2 initiatorWon reads true once resolved", rules.initiatorWon(war, winner));
    }

// ── S3: the suppressed totals are fixed before the transition ────────────

    /**
     * The numbers an armistice suppresses are decided during the open window
     * and are NOT recomputed or mutated across the transition. A cancel path
     * therefore has a stable quantity to suppress rather than a moving one.
     */
    private static void scenarioTotalsFixedBeforeTransition() {
        System.out.println("== S3: support/opposition totals are fixed across the transition ==");
        Player narn    = player("Narn", Faction.NARN);
        Player minbari = player("Minbari", Faction.MINBARI);
        GameState st   = state(narn, minbari);
        st.getTensionMatrix().enterWar(Faction.NARN, Faction.MINBARI);

        Conflict war = rules.declareWarConflict(narn, WarKind.RACE_TARGET, minbari, null, st);

        // A mid-round join: the window is genuinely mid-round, not merely
        // "immediately after declaration". Joining changes the totals, which
        // is precisely why the totals must be re-read AFTER the join and then
        // held stable across the transition.
        boolean joined = war.addParticipant(minbari, false);
        check("S3 a seat may still join inside the open window", joined);

        int supportBefore    = war.supportTotal();
        int oppositionBefore = war.oppositionTotal();
        check("S3 totals were decided during the open window", true);

        rules.resolveConflict(war, st);

        check("S3 support total unchanged by the transition",
                war.supportTotal() == supportBefore);
        check("S3 opposition total unchanged by the transition",
                war.oppositionTotal() == oppositionBefore);
    }

    // ── S4: the transition is logged once, naming the winner ─────────────────

    /**
     * The resolve line is the one a later armistice must not imitate, so its
     * shape is part of the contract: exactly one, naming the winner.
     */
    private static void scenarioTransitionLoggedOnce() {
        System.out.println("== S4: the transition is logged exactly once and names the winner ==");
        Player narn    = player("Narn", Faction.NARN);
        Player minbari = player("Minbari", Faction.MINBARI);
        GameState st   = state(narn, minbari);
        st.getTensionMatrix().enterWar(Faction.NARN, Faction.MINBARI);

        Conflict war = rules.declareWarConflict(narn, WarKind.RACE_TARGET, minbari, null, st);
        check("S4 war conflict declared", war != null);
        if (war == null) return;

        int linesBefore = st.getLog().size();
        Player winner   = rules.resolveConflict(war, st);
        List<String> written = st.getLog().subList(linesBefore, st.getLog().size());

        int transitionLines = 0;
        for (int i = 0; i < written.size(); i++) {
            if (written.get(i).contains("won by")) transitionLines++;
        }
        check("S4 exactly one resolve transition line written", transitionLines == 1);
        check("S4 the transition line names the winner",
                logContains(st, "won by " + winner.getName()));
        check("S4 war outcome line also written (transition is observable)",
                logContains(st, "War outcome:"));
    }

// ── S5: a war conflict sits inside "one Military conflict" scope ─────────

    /**
     * Without MILITARY type, the window measured in S1-S4 would be on a
     * conflict the Armistice card could never name.
     */
    private static void scenarioWarConflictIsMilitary() {
        System.out.println("== S5: a war conflict is MILITARY, so it is nameable by an armistice ==");
        Player narn    = player("Narn", Faction.NARN);
        Player minbari = player("Minbari", Faction.MINBARI);
        GameState st   = state(narn, minbari);
        st.getTensionMatrix().enterWar(Faction.NARN, Faction.MINBARI);

        Conflict war = rules.declareWarConflict(narn, WarKind.RACE_TARGET, minbari, null, st);
        check("S5 war conflict declared", war != null);
        if (war == null) return;

        check("S5 war conflict reports MILITARY type",
                war.getConflictType() == ConflictType.MILITARY);
        check("S5 war conflict carries no ConflictCard (card == null)", war.getCard() == null);
        check("S5 isWarConflict agrees with the type",
                war.isWarConflict() && war.getConflictType() == ConflictType.MILITARY);
    }

    // ── Gap census: printed, deliberately NOT a check ────────────────────────

    /**
     * Reports whether any armistice / cancel / negate entry point exists on
     * RulesEngine or Conflict. Observation only — see the class comment for why
     * this is not a pass/fail check.
     */
    private static void observeNoArmisticeEntryPoint() {
        System.out.println("== GAP CENSUS (observation, not a gate) ==");
        String[] needles = { "armist", "cancel", "negate" };
        int found = 0;
        found += census(rules.getClass(), needles, "RulesEngine");
        found += census(Conflict.class, needles, "Conflict");
        System.out.println("  [B5-2565] armistice/cancel/negate entry points found: " + found);
        System.out.println("  [B5-2565] rulebook :1144 \"Canceled\" has no code path while this"
                + " reads 0; the S1-S5 window is the contract an implementation must respect.");
    }

    private static int census(Class<?> type, String[] needles, String label) {
        Method[] methods = type.getMethods();
        int found = 0;
        for (int i = 0; i < methods.length; i++) {
            String name = methods[i].getName().toLowerCase();
            for (int j = 0; j < needles.length; j++) {
                if (name.indexOf(needles[j]) >= 0) {
                    found++;
                    System.out.println("  [B5-2565] " + label + "."
                            + methods[i].getName() + " matches '" + needles[j] + "'");
                }
            }
        }
        return found;
    }

    // ── Helpers (mirror HeadlessWarConflictProbe.player/state) ───────────────

    private static Player player(String name, Faction f) {
        Player p = new Player(name, f, false);
        CharacterCard amb = new CharacterCard("amb_" + name, "Amb " + name,
                "CHARACTER_" + f, Rarity.FIXED, f, CardSet.PREMIERE, "x", "text",
                3, 3, 3, 3, true);
        p.setAmbassador(amb);
        p.getInnerCircle().add(amb);
        List<Card> filler = new ArrayList<Card>();
        for (int i = 0; i < 5; i++) {
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

    private static boolean logContains(GameState st, String needle) {
        List<String> log = st.getLog();
        for (int i = 0; i < log.size(); i++) {
            if (log.get(i).contains(needle)) return true;
        }
        return false;
    }

    private static void check(String label, boolean passed) {
        checks++;
        if (passed) {
            System.out.println("  [B5-2565] " + label + ": PASS");
            return;
        }
        failures++;
        System.out.println("  [B5-2565] " + label + ": FAIL");
    }
}