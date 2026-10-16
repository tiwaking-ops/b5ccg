package b5ccg.engine;

import b5ccg.model.*;
import b5ccg.model.enums.*;
import java.util.ArrayList;
import java.util.List;

/**
 * B5-0383 — participation-gates SCENARIO probe (standalone CLI, never wired
 * into RUN_TESTS; exit 0 on all-pass, 1 on any failure).
 *
 * Unlike the suite's PAR unit checks (synthetic Participation objects), this
 * probe loads the REAL card data via DeckLoader and drives the three
 * restricted conflict cards through the live engine paths:
 *
 *   Border Raid      INITIATOR_TARGET + requiresTarget + FLEET-only
 *                    + perPlayerQuota FLEET=1 + leadersIncluded
 *   Limited Strike   FLEET-only + fleetSubtypes gate — the reject-unproven
 *                    rule (§3.6): every fleet in the data has fleetClass
 *                    NULL today, so the gate must refuse them all
 *   Complete Support mustTakeSide — mandatory participation via the engine
 *
 * New-file-only task: no game-logic edits, never touches
 * HeadlessConformanceTest.java. Java 6 only.
 */
public class HeadlessParticipationGatesProbe {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        List<Card> pool = DeckLoader.loadBothSets();
        // Dedup-effective rows are looked up by TITLE: loadBothSets keeps the
        // DELUXE copy of any title both sets share, so title — not the premiere
        // id — identifies what the engine would actually serve in a game.
        ConflictCard borderRaid = byTitle(pool, "Border Raid");
        ConflictCard limited    = byTitle(pool, "Limited Strike");
        ConflictCard complete   = byTitle(pool, "Complete Support");

        scenarioBorderRaid(borderRaid);
        scenarioLimitedStrike(pool, limited);
        scenarioCompleteSupport(complete);

        System.out.println();
        if (failures == 0) {
            System.out.println("B5-0383 PROBE PASSED (all participation-gate scenarios)");
        } else {
            System.out.println("B5-0383 PROBE FAILED: " + failures + " check(s)");
            System.exit(1);
        }
    }

    // ── Border Raid ───────────────────────────────────────────────────────────

    private static void scenarioBorderRaid(ConflictCard raid) {
        System.out.println("== Border Raid: INITIATOR_TARGET + requiresTarget + FLEET quota=1 + leadersIncluded ==");
        if (!"INITIATOR_TARGET".equals(part(raid).getPlayers())
                || !part(raid).isRequiresTarget()) {
            fail("data shape changed: Border Raid no longer INITIATOR_TARGET/requiresTarget");
            return;
        }
        Player init = new Player("BRIinit", raid.getFaction(), false);
        Player tgt  = new Player("BRItgt",  Faction.MINBARI, false);
        Player out  = new Player("BRIout",  Faction.CENTAURI, false);
        GameState st = state(init, tgt, out);

        // (a) requiresTarget: initiation illegal without, legal with a target.
        init.getHand().add(raid);
        boolean noTarget  = RulesEngineHolder.rules.canInitiateConflict(init, raid, null, st);
        boolean withTgt   = RulesEngineHolder.rules.canInitiateConflict(init, raid, tgt, st);
        check("BR initiation without target is illegal (requiresTarget)", !noTarget);
        check("BR initiation with the declared target is legal", withTgt);

        // (b) players gate: only initiator/target may join.
        Conflict c = new Conflict(raid, init, tgt);
        check("BR outsider join refused (INITIATOR_TARGET)", !c.addParticipant(out, false));
        check("BR declared target join accepted", c.addParticipant(tgt, false));

        // (c) cardTypes: events refuse, fleets accept (no fleetSubtypes here).
        EventCard ev = new EventCard("br_ev", "BR Event", "EVENT", Rarity.COMMON,
                Faction.ANY, CardSet.PREMIERE, "x", "text");
        FleetCard f1 = new FleetCard("br_f1", "BR Fleet 1", "FLEET", Rarity.COMMON,
                Faction.ANY, CardSet.PREMIERE, "x", "text", 3);
        check("BR event commit refused (FLEET-only)", !c.commitCard(tgt, ev, false));
        check("BR first fleet commit accepted", c.commitCard(tgt, f1, false));

        // (d) perPlayerQuota FLEET=1: a second fleet refuses.
        FleetCard f2 = new FleetCard("br_f2", "BR Fleet 2", "FLEET", Rarity.COMMON,
                Faction.ANY, CardSet.PREMIERE, "x", "text", 4);
        check("BR second fleet commit refused (quota FLEET=1)", !c.commitCard(tgt, f2, false));

        // (e) leadersIncluded: a character commits ONLY alongside the
        //     committed fleet. Target has fleet f1 committed, so allowed;
        //     the initiator has no fleet, so refused.
        CharacterCard tChar = new CharacterCard("br_tc", "BR Target Char",
                "CHARACTER_MINBARI", Rarity.RARE, Faction.MINBARI, CardSet.PREMIERE,
                "x", "text", 2, 2, 0, 3, false);
        CharacterCard iChar = new CharacterCard("br_ic", "BR Init Char",
                "CHARACTER_NARN", Rarity.RARE, raid.getFaction(), CardSet.PREMIERE,
                "x", "text", 2, 2, 0, 3, false);
        check("BR character commits alongside the allowed fleet (leadersIncluded)",
                c.commitCard(tgt, tChar, false));
        check("BR character without a committed fleet refused", !c.commitCard(init, iChar, true));
    }

    // ── Limited Strike ────────────────────────────────────────────────────────

    private static void scenarioLimitedStrike(List<Card> pool, ConflictCard limited) {
        System.out.println("== Limited Strike: fleetSubtypes gate, reject-unproven on unmapped fleetClass ==");
        Participation part = part(limited);
        if (part.getFleetSubtypes().isEmpty()) {
            fail("data shape changed: Limited Strike lost its fleetSubtypes filter");
            return;
        }
        Player init = new Player("LSinit", Faction.NARN, false);
        Player out  = new Player("LSout",  Faction.MINBARI, false);
        GameState st = state(init, out);
        Conflict c = new Conflict(limited, init);

        // (a) reject-unproven: a REAL loaded fleet with a fleetClass OUTSIDE
        //     the Limited Strike subtypes (PICKET/COLONIAL/UTILITY) is refused.
        //     All 80 fleets now carry fleetClass values (B5-0392); pick one
        //     whose class is not in the Limited Strike filter set.
        FleetCard realFleet = firstRealFleetOutsideSubtypes(pool,
                new java.util.HashSet<java.lang.String>(java.util.Arrays.asList(
                        "PICKET", "COLONIAL", "UTILITY")));
        if (realFleet == null) { fail("no out-of-subtype FLEET card found in loaded data"); return; }
        check("loaded out-of-subtype fleet has non-matching fleetClass",
                realFleet.getFleetClass() != null
                && !part.getFleetSubtypes().contains(realFleet.getFleetClass()));
        c.addParticipant(out, false);
        check("out-of-subtype class fleet commit refused (reject-unproven)",
                !c.commitCard(out, realFleet, false));

        // (b) an in-subtype class passes; an out-of-subtype class refuses.
        FleetCard picket = new FleetCard("ls_p", "LS Picket", "FLEET", Rarity.COMMON,
                Faction.ANY, CardSet.PREMIERE, "x", "text", 3);
        picket.setFleetClass("PICKET");
        check("PICKET-class fleet commit accepted", c.commitCard(out, picket, false));
        FleetCard raider = new FleetCard("ls_r", "LS Raider", "FLEET", Rarity.COMMON,
                Faction.ANY, CardSet.PREMIERE, "x", "text", 3);
        raider.setFleetClass("raider");
        check("out-of-subtype fleet commit refused", !c.commitCard(out, raider, false));

        // (c) cardTypes filter still applies alongside subtypes.
        EventCard ev = new EventCard("ls_ev", "LS Event", "EVENT", Rarity.COMMON,
                Faction.ANY, CardSet.PREMIERE, "x", "text");
        check("event commit refused (FLEET-only)", !c.commitCard(out, ev, false));
        check("RulesEngine seen consistent: state untouched by refusals",
                st.getActiveConflict() == null);
    }

    // ── Complete Support ──────────────────────────────────────────────────────

    private static void scenarioCompleteSupport(ConflictCard complete) {
        System.out.println("== Complete Support: mustTakeSide mandatory participation ==");
        Participation eff = complete.getParticipation();
        check("complete participation data present", eff != null);
        check("complete participation is mustTakeSide", eff != null && eff.isMustTakeSide());
        Participation mandate = Participation.parse("{\"mustTakeSide\":true}");
        ConflictCard csCard = new ConflictCard("probe_cs", "CS (mandate semantics)",
                "CONFLICT_DIPLOMACY", Rarity.COMMON, Faction.ANY, CardSet.PREMIERE,
                "x", "text", ConflictType.DIPLOMACY, 1);
        csCard.setParticipation(mandate);

        // Players need ambassadors for the mandatory-commit path (the suite's
        // player() helper does the same; bare Player has none).
        Player init = playerWithAmbassador("CSinit", Faction.NARN);
        Player p2   = playerWithAmbassador("CSp2",   Faction.MINBARI);
        Player p3   = playerWithAmbassador("CSp3",   Faction.CENTAURI);
        GameState st = state(init, p2, p3);

        Conflict c = new Conflict(csCard, init);
        c.commitCard(init, init.getAmbassador(), true);   // initiator supports
        RulesEngineHolder.rules.enforceMandatoryParticipation(c, st);

        boolean p2In  = c.getParticipants().contains(p2);
        boolean p3In  = c.getParticipants().contains(p3);
        boolean p2Amb = !c.getCommittedCards(p2).isEmpty();
        boolean p3Amb = !c.getCommittedCards(p3).isEmpty();
        check("both other players compelled to participate", p2In && p3In);
        check("compelled participants committed their ambassadors", p2Amb && p3Amb);
        check("initiator on support side", c.isSupporting(init));
        check("compelled players on the opposition side", !c.isSupporting(p2) && !c.isSupporting(p3));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Indirection so the probe compiles against RulesEngine cleanly. */
    private static final class RulesEngineHolder {
        static final RulesEngine rules = new RulesEngine();
    }

    private static Participation part(ConflictCard cc) {
        Participation p = cc.getParticipation();
        if (p == null) fail(cc.getId() + " loaded WITHOUT a participation object");
        return p;
    }

    private static ConflictCard byTitle(List<Card> pool, String title) {
        for (Card c : pool) {
            if (title.equals(c.getTitle()) && c instanceof ConflictCard) return (ConflictCard) c;
        }
        fail("conflict card not found in loaded data: " + title);
        return null;
    }

    private static FleetCard firstRealFleet(List<Card> pool) {
        for (Card c : pool) {
            if (c instanceof FleetCard && CardSet.PREMIERE.equals(c.getCardSet())) {
                return (FleetCard) c;
            }
        }
        for (Card c : pool) {
            if (c instanceof FleetCard) return (FleetCard) c;
        }
        return null;
    }

    /** A real loaded fleet whose fleetClass is NOT in the allowed set. */
    private static FleetCard firstRealFleetOutsideSubtypes(List<Card> pool,
            java.util.Set<String> allowedClasses) {
        for (Card c : pool) {
            if (c instanceof FleetCard) {
                FleetCard f = (FleetCard) c;
                String cls = f.getFleetClass();
                if (cls != null && !allowedClasses.contains(cls)) {
                    return f;
                }
            }
        }
        return null;
    }

    /** Player with an ambassador in the Inner Circle (mirrors the suite helper). */
    private static Player playerWithAmbassador(String name, Faction f) {
        Player p = new Player(name, f, false);
        CharacterCard amb = new CharacterCard("amb_" + name, "Amb " + name,
                "CHARACTER_" + f, Rarity.FIXED, f, CardSet.PREMIERE, "x", "text",
                3, 3, 3, 3, true);
        p.setAmbassador(amb);
        p.getInnerCircle().add(amb);
        return p;
    }

    private static GameState state(Player... players) {
        List<Player> ps = new ArrayList<Player>();
        for (Player p : players) ps.add(p);
        GameState s = new GameState(ps);
        s.setPhase(GamePhase.ACTION);
        return s;
    }

    private static void check(String label, boolean ok) {
        System.out.println((ok ? "  [PGR] " : "  [PGR] FAIL — ") + label + (ok ? ": PASS" : ""));
        if (!ok) failures++;
    }

    private static void fail(String why) {
        System.out.println("  [PGR] FAIL — " + why);
        failures++;
    }
}
