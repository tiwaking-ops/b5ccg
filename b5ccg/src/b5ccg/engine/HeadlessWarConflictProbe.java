package b5ccg.engine;

import b5ccg.model.*;
import b5ccg.model.enums.*;
import java.util.ArrayList;
import java.util.List;

/**
 * B5-0419 — DECLARE_WAR_CONFLICT SCENARIO probe (standalone CLI, never wired
 * into RUN_TESTS; exit 0 on all-pass, 1 on any failure).
 *
 * Drives the B5-0376 war-conflict pipeline END TO END on REAL loaded card
 * data (via DeckLoader.loadBothSets) through the same seams the controller
 * uses — declaration legality, the tension increment (clamped at 5), real
 * location capture and recapture, and the "all-supported uncontested" read
 * both ways (opposers present vs attack occurred). New-file-only task:
 * no game-logic edits, never touches HeadlessConformanceTest.java. Java 6
 * only.
 *
 * B5-0405 precedent applies where exact combat math is required: the war
 * itself (declaration, targets, tension, capture) is driven on real loaded
 * data; commit fleets are synthetic fixtures with chosen military values so
 * the support-vs-opposition math is exact.
 */
public class HeadlessWarConflictProbe {

    private static RulesEngine rules = new RulesEngine();
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        List<Card> pool = DeckLoader.loadBothSets();
        if (pool == null || pool.isEmpty()) {
            fail("DeckLoader.loadBothSets() returned no cards");
            System.exit(1);
        }

        scenarioDeclarationLegality();
        scenarioTensionIncrementClamped();
        scenarioLocationCaptureAndRecapture(pool);
        scenarioUncontestedReadBothWays();
        scenarioForfeitedRaceMemberJointLoss();

        System.out.println();
        if (failures == 0) {
            System.out.println("B5-0419 PROBE PASSED (all DECLARE_WAR_CONFLICT scenarios)");
        } else {
            System.out.println("B5-0419 PROBE FAILED: " + failures + " check(s)");
            System.exit(1);
        }
    }

    // ── Scenario 1: declaration legality, end to end ─────────────────────────

    private static void scenarioDeclarationLegality() {
        System.out.println("== S1: DECLARE_WAR_CONFLICT legality (at peace -> at war -> wrong target) ==");
        Player narn     = player("Narn", Faction.NARN);
        Player minbari  = player("Minbari", Faction.MINBARI);
        Player centauri = player("Centauri", Faction.CENTAURI);
        GameState st = state(narn, minbari, centauri);

        // (a) at peace: cannot declare any war conflict.
        check("S1 cannot declare war conflict with no war at all", !rules.canDeclareWarConflict(narn, st));
        check("S1 cannot declare vs race not at war", !rules.canInitiateWarConflict(narn, WarKind.RACE_TARGET, minbari, null, st));

        // (b) at war: both sides may declare.
        st.getTensionMatrix().enterWar(Faction.NARN, Faction.MINBARI);
        check("S1 can declare war conflict when at war", rules.canDeclareWarConflict(narn, st));
        check("S1 war partner can also declare", rules.canDeclareWarConflict(minbari, st));

        // (c) the GameAction the controller would process (B5-0376 factory).
        GameAction action = GameAction.declareWarConflict(WarKind.RACE_TARGET, minbari, null);
        check("S1 action type DECLARE_WAR_CONFLICT", action.getType() == GameAction.Type.DECLARE_WAR_CONFLICT);
        check("S1 action carries null card (no synthetic card)", action.getCard() == null);
        check("S1 action race target is minbari", action.getTarget() == minbari);
        check("S1 action no location target", action.getTargetCard() == null);

        // (d) engine acceptance of the action's seams.
        check("S1 canInitiateWarConflict accepts the declared target",
                rules.canInitiateWarConflict(narn, WarKind.RACE_TARGET, minbari, null, st));
        Conflict war = rules.declareWarConflict(narn, WarKind.RACE_TARGET, minbari, null, st);
        check("S1 declareWarConflict produced a war conflict", war != null && war.isWarConflict());
        check("S1 war conflict has null card", war != null && war.getCard() == null);
        check("S1 war conflict targets minbari", war != null && war.getTarget() == minbari);
        check("S1 war conflict type is MILITARY",
                war != null && war.getConflictType() == ConflictType.MILITARY);
        check("S1 war conflict carries zero influence reward",
                war != null && war.getInfluenceReward() == 0);

        // (e) participation: at-war races may join, peace-bound may not.
        check("S1 initiator can join own war conflict", war.canJoinConflict(narn));
        check("S1 at-war target can join", war.canJoinConflict(minbari));
        check("S1 peaceful third party cannot join", !war.canJoinConflict(centauri));
        st.getTensionMatrix().enterWar(Faction.NARN, Faction.CENTAURI);
        check("S1 third party joins once at war", war.canJoinConflict(centauri));
        st.getTensionMatrix().exitWar(Faction.NARN, Faction.CENTAURI);

        // (f) declaring against a race no longer at war is refused.
        Conflict invalid = rules.declareWarConflict(narn, WarKind.RACE_TARGET, centauri, null, st);
        check("S1 declaration against peace-bound race refused", invalid == null);
    }

    // ── Scenario 2: tension increment, clamped at 5 ──────────────────────────

    private static void scenarioTensionIncrementClamped() {
        System.out.println("== S2: tension increment on uncontested outcome, clamped at 5 ==");
        Player narn     = player("Narn", Faction.NARN);
        Player minbari  = player("Minbari", Faction.MINBARI);
        GameState st = state(narn, minbari);
        st.getTensionMatrix().enterWar(Faction.NARN, Faction.MINBARI);
        check("S2 tension starts at 0",
                st.getTensionMatrix().getTension(Faction.MINBARI, Faction.NARN) == 0);

        // First uncontested race war: target -1, winner +1, tension +1.
        Conflict war = rules.declareWarConflict(narn, WarKind.RACE_TARGET, minbari, null, st);
        int minbariInf = minbari.getInfluence();
        int narnInf = narn.getInfluence();
        rules.resolveConflict(war, st);
        if (minbari.getInfluence() != minbariInf - 1) {
            fail("S2 target influence delta not -1 (expected " + (minbariInf - 1)
                    + ", got " + minbari.getInfluence() + ")");
        } else {
            check("S2 uncontested race war lowers the target's influence by 1", true);
        }
        if (narn.getInfluence() != narnInf + 1) {
            fail("S2 winner influence delta not +1 (expected " + (narnInf + 1)
                    + ", got " + narn.getInfluence() + ")");
        } else {
            check("S2 uncontested race war raises the winner's influence by 1", true);
        }
        boolean tensionOne = st.getTensionMatrix().getTension(Faction.MINBARI, Faction.NARN) == 1;
        check("S2 tension target->initiator incremented to 1", tensionOne);

        // Clamp: drive tension to the cap, then resolve again — must stay 5.
        st.raiseTension(Faction.MINBARI, Faction.NARN, 4);
        check("S2 pre-clamp tension raised to 5",
                st.getTensionMatrix().getTension(Faction.MINBARI, Faction.NARN) == 5);
        Conflict war2 = rules.declareWarConflict(narn, WarKind.RACE_TARGET, minbari, null, st);
        rules.resolveConflict(war2, st);
        boolean clamped = st.getTensionMatrix().getTension(Faction.MINBARI, Faction.NARN) == 5;
        check("S2 tension increment clamped at 5 (not 6)", clamped);
    }

    // ── Scenario 3: location capture and recapture on real loaded data ────────

    private static void scenarioLocationCaptureAndRecapture(List<Card> pool) {
        System.out.println("== S3: LOCATION_TARGET capture + recapture on real loaded location ==");
        LocationCard loc = firstLocationOfFaction(pool, Faction.CENTAURI);
        if (loc == null) {
            loc = firstLocationOfFaction(pool, Faction.MINBARI);
        }
        if (loc == null) {
            loc = firstLocationOfFaction(pool, Faction.HUMAN);
        }
        if (loc == null) {
            fail("no LOCATION card with a faction among NARN/MINBARI/CENTAURI/HUMAN in loaded data");
            return;
        }

        Player narn     = player("Narn", Faction.NARN);
        Player locOwner = player("Centauri owner", loc.getFaction());
        Player minbari  = player("Minbari", Faction.MINBARI);
        GameState st = state(narn, locOwner, minbari);
        st.getTensionMatrix().enterWar(locOwner.getFaction(), Faction.NARN);

        locOwner.getLocations().add(loc);
        int origIncome = loc.getInfluencePerRound();
        int origMil    = loc.getMilitary();

        check("S3 loaded location is a real data card",
                loc.getId() != null && loc.getTitle() != null && !loc.getTitle().isEmpty());

        // (a) declaration legality against a location owned by an at-war faction.
        check("S3 canInitiateWarConflict accepts the real location target",
                rules.canInitiateWarConflict(narn, WarKind.LOCATION_TARGET, null, loc, st));
        Conflict cap = rules.declareWarConflict(narn, WarKind.LOCATION_TARGET, null, loc, st);
        check("S3 location-target war conflict declared", cap != null);
        check("S3 conflict carries the location target",
                cap != null && cap.getTargetLocation() == loc);
        check("S3 conflict has no race target", cap != null && cap.getTarget() == null);

        // (b) initiator wins the uncontested war -> capture + suppression.
        Player winner = rules.resolveConflict(cap, st);
        check("S3 win goes to the initiator", winner == narn);
        check("S3 location captured by winning initiator", loc.getCapturedBy() == narn);
        check("S3 captured location effects suppressed", loc.isEffectsSuppressed());
        check("S3 captured location income suppressed to 0", loc.getInfluencePerRound() == 0);
        check("S3 captured location military suppressed to 0", loc.getMilitary() == 0);
        check("S3 LOCATION_TARGET tension runs toward printed-faction owner",
                st.getTensionMatrix().getTension(loc.getFaction(), Faction.NARN) > 0);

        // (c) recapture by the printed-faction owner (winner faction == loc faction).
        check("S3 owner may declare recapture against the occupier",
                rules.canInitiateWarConflict(locOwner, WarKind.LOCATION_TARGET, null, loc, st));
        Conflict rec = rules.declareWarConflict(locOwner, WarKind.LOCATION_TARGET, null, loc, st);
        check("S3 recapture war conflict declared", rec != null);
        Player recWinner = rules.resolveConflict(rec, st);
        check("S3 recapture win goes to the printed-faction owner", recWinner == locOwner);
        check("S3 recaptured location cleared of capture marker", loc.getCapturedBy() == null);
        check("S3 recaptured location effects restored", !loc.isEffectsSuppressed());
        check("S3 recaptured location income restored", loc.getInfluencePerRound() == origIncome);
        check("S3 recaptured location military restored", loc.getMilitary() == origMil);
    }

    // ── Scenario 4: the "all-supported uncontested" read, both ways ───────────

    private static void scenarioUncontestedReadBothWays() {
        System.out.println("== S4: uncontested read both ways (opposers vs attack) ==");

        // (a) truly uncontested: empty opposition, no attack -> swing applies.
        Player narn = player("Narn", Faction.NARN);
        Player minbari = player("Minbari", Faction.MINBARI);
        GameState stA = state(narn, minbari);
        stA.getTensionMatrix().enterWar(Faction.NARN, Faction.MINBARI);
        Conflict warA = rules.declareWarConflict(narn, WarKind.RACE_TARGET, minbari, null, stA);
        int narnA = narn.getInfluence();
        int minA = minbari.getInfluence();
        rules.resolveConflict(warA, stA);
        check("S4a uncontested read: target loses 1", minbari.getInfluence() == minA - 1);
        check("S4a uncontested read: winner gains 1", narn.getInfluence() == narnA + 1);

        // (b) contested by OPPOSERS while the initiator still wins: no swing.
        Player narnB = player("Narn", Faction.NARN);
        Player minB  = player("Minbari", Faction.MINBARI);
        GameState stB = state(narnB, minB);
        stB.getTensionMatrix().enterWar(Faction.NARN, Faction.MINBARI);
        Conflict warB = rules.declareWarConflict(narnB, WarKind.RACE_TARGET, minB, null, stB);
        FleetCard sup1 = fleetCard("s4b_sup1", 3);
        FleetCard sup2 = fleetCard("s4b_sup2", 3);
        FleetCard opp1 = fleetCard("s4b_opp1", 2);
        narnB.addFleet(sup1);
        narnB.addFleet(sup2);
        minB.addFleet(opp1);
        warB.commitCard(narnB, sup1, true);
        warB.commitCard(narnB, sup2, true);
        warB.addParticipant(minB, false);
        warB.commitCard(minB, opp1, false);
        int narnBInf = narnB.getInfluence();
        int minBInf  = minB.getInfluence();
        Player winnerB = rules.resolveConflict(warB, stB);
        check("S4b initiator still wins with greater support", winnerB == narnB);
        check("S4b contested-by-opposer read: no target swing",
                minB.getInfluence() == minBInf);
        check("S4b contested-by-opposer read: no winner swing",
                narnB.getInfluence() == narnBInf);

        // (c) contested by an ATTACK (real engine path marks the conflict):
        //     initiator wins on support but the attack makes it contested.
        Player narnC = player("Narn", Faction.NARN);
        Player minC  = player("Minbari", Faction.MINBARI);
        GameState stC = state(narnC, minC);
        stC.getTensionMatrix().enterWar(Faction.NARN, Faction.MINBARI);
        Conflict warC = rules.declareWarConflict(narnC, WarKind.RACE_TARGET, minC, null, stC);
        FleetCard cSup1 = fleetCard("s4c_sup1", 3);
        FleetCard cSup2 = fleetCard("s4c_sup2", 3);
        FleetCard cSup3 = fleetCard("s4c_sup3", 3);
        FleetCard cOpp  = fleetCard("s4c_opp", 3);
        FleetCard cAtk  = fleetCard("s4c_atk", 3);
        narnC.addFleet(cSup1);
        narnC.addFleet(cSup2);
        narnC.addFleet(cSup3);
        minC.addFleet(cOpp);
        minC.addFleet(cAtk);
        warC.commitCard(narnC, cSup1, true);
        warC.commitCard(narnC, cSup2, true);
        warC.commitCard(narnC, cSup3, true);
        warC.addParticipant(minC, false);
        warC.commitCard(minC, cOpp, false);
        check("S4c attack action resolves against a committed participant",
                rules.executeAttackConflictParticipant(minC, cAtk, cSup1, warC, stC));
        check("S4c attack marks the war conflict contested", warC.anyAttackOccurred());
        int narnCInf = narnC.getInfluence();
        int minCInf  = minC.getInfluence();
        Player winnerC = rules.resolveConflict(warC, stC);
        // The mutual damage neutralizes both cSup1 and cAtk (damage 3 >=
        // ability 3), so resolved support is 6 vs opposition 3.
        check("S4c initiator wins support after mutual attack neutralization", winnerC == narnC);
        check("S4c contested-by-attack read: no target swing",
                minC.getInfluence() == minCInf);
        check("S4c contested-by-attack read: no winner swing",
                narnC.getInfluence() == narnCInf);
    }

    // ── Scenario 5: forfeited race member should not lose influence in joint loss (B5-1706) ────────────────
    // Rulebook :980 — while UNIFIED, loss spills to every faction of the race.
    // Forfeited/surrendered players are out of the game and should not be debited.
    private static void scenarioForfeitedRaceMemberJointLoss() {
        System.out.println("== S5: Forfeited race member excluded from joint influence loss ==");
        // Three players: two Narn (same race), one Minbari
        Player narn1 = player("Narn1", Faction.NARN);
        Player narn2 = player("Narn2", Faction.NARN);
        Player minbari = player("Minbari", Faction.MINBARI);
        GameState st = state(narn1, narn2, minbari);
        st.getTensionMatrix().enterWar(Faction.NARN, Faction.MINBARI);

        // Narn1 initiates uncontested race war against Minbari
        Conflict war = rules.declareWarConflict(narn1, WarKind.RACE_TARGET, minbari, null, st);
        int narn1Before = narn1.getInfluence();
        int narn2Before = narn2.getInfluence();
        int minbariBefore = minbari.getInfluence();

        // Narn2 forfeits (simulates deck-out or voluntary forfeit)
        narn2.setHasForfeited(true);

        // Resolve: Minbari loses 1, Narn1 gains 1, Narn2 (forfeited) should NOT lose 1
        Player winner = rules.resolveConflict(war, st);

        check("S5 winner is Narn1 (initiator won uncontested)", winner == narn1);
        check("S5 Minbari loses 1 influence", minbari.getInfluence() == minbariBefore - 1);
        check("S5 Narn1 gains 1 influence", narn1.getInfluence() == narn1Before + 1);
        check("S5 Forfeited Narn2 does NOT lose influence (joint loss filter)",
                narn2.getInfluence() == narn2Before);

        // Also test surrendered player in same scenario (initiator wins, target is surrendered race member)
        Player narn3 = player("Narn3", Faction.NARN);
        Player narn4 = player("Narn4", Faction.NARN);
        Player minbari2 = player("Minbari2", Faction.MINBARI);
        GameState st2 = state(narn3, narn4, minbari2);
        st2.getTensionMatrix().enterWar(Faction.NARN, Faction.MINBARI);
        Conflict war2 = rules.declareWarConflict(narn3, WarKind.RACE_TARGET, minbari2, null, st2);
        int narn3Before = narn3.getInfluence();
        int narn4Before = narn4.getInfluence();
        int minbari2Before = minbari2.getInfluence();

        // Narn4 (race member of winner) surrenders
        narn4.setHasSurrendered(true);

        Player winner2 = rules.resolveConflict(war2, st2);
        check("S5b winner is Narn3 (initiator won uncontested)", winner2 == narn3);
        check("S5b Minbari2 loses 1 influence", minbari2.getInfluence() == minbari2Before - 1);
        check("S5b Narn3 gains 1 influence", narn3.getInfluence() == narn3Before + 1);
        check("S5b Surrendered Narn4 does NOT lose influence (joint loss filter)",
                narn4.getInfluence() == narn4Before);
    }

    // ── Helpers (mirror HeadlessConformanceTest.player/state) ─────────────────

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

    private static FleetCard fleetCard(String id, int military) {
        return new FleetCard(id, id, "FLEET", Rarity.COMMON, Faction.ANY,
                CardSet.PREMIERE, "x", "text", military);
    }

    /** First real loaded LOCATION whose printed faction matches f, else null. */
    private static LocationCard firstLocationOfFaction(List<Card> pool, Faction f) {
        for (Card c : pool) {
            if (c instanceof LocationCard) {
                LocationCard loc = (LocationCard) c;
                if (loc.getFaction() == f) return loc;
            }
        }
        return null;
    }

    private static void check(String label, boolean ok) {
        System.out.println((ok ? "  [WAR] " : "  [WAR] FAIL — ") + label + (ok ? ": PASS" : ""));
        if (!ok) failures++;
    }

    private static void fail(String why) {
        System.out.println("  [WAR] FAIL — " + why);
        failures++;
    }
}