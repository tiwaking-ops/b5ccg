package b5ccg.engine;

import b5ccg.model.*;
import b5ccg.model.enums.*;
import java.util.*;

/**
 * Central per-card effect dispatch. Keys effects on the card id exactly as it
 * appears in the card JSON (b5ccg/resources/cards/*.json) — no card-text
 * parsing. Every entry is data-verified: ids come from the JSON, enhancement
 * magnitudes come from the JSON bonus fields, and event/agenda/conflict
 * magnitudes come from the card text stored in that JSON.
 *
 * Wiring (GameController / RulesEngine):
 *   - Events:       applyPlayEvent replaces the generic "draw 1" in
 *                   applyGenericCardPlay; unknown ids keep that behaviour.
 *   - Enhancements: applyPlayEnhancement attaches to a live target and applies
 *                   the JSON bonus fields via CharacterCard.applyStatDelta /
 *                   FleetCard.applyMilitaryDelta (existing model methods).
 *   - Conflicts:    applyConflictOutcome runs loser penalties after the
 *                   winner's influence reward; winner reward stays in
 *                   RulesEngine.resolveConflict.
 *   - Agendas:      applyAgendaStartOfRound (RulesEngine.startRound),
 *                   applyAgendaOnPlay + applyAgendaDiplomacyWin
 *                   (GameController).
 *
 * The model interface CardEffect remains declared; the id-keyed tables below
 * are its implementation. Current rule: Java 8-compatible bytecode via JDK 25 --release 8 (per B5-0007/D2): no lambdas, method refs, streams,
 * diamonds, or try-with-resources.
 */
public final class CardEffects {

    private CardEffects() { }

    /** Typed free-action waivers dispatched by card id, without text parsing. */
    public enum WaiverEffect {
        NONE, FREE_SPONSOR, FREE_PARTICIPANT
    }

    private static final Map<String, WaiverEffect> WAIVER_EFFECTS =
            new HashMap<String, WaiverEffect>();
    static {
        // B5-0360 E3 / Q4: both set versions grant the same join waiver.
        // Canonical-key routing (lookupMap) collapses the Premiere/Deluxe twin.
        WAIVER_EFFECTS.put("conf_non_aligned_support", WaiverEffect.FREE_PARTICIPANT);
    }

    /**
     * B5-4170 (rulebook :1210 second Won clause; B5-2223 catalog row 1.3):
     * card ids whose conflicts are non-opposable, support-multiple-side —
     * the initiator wins only with strictly more support than ANY OTHER
     * side. Id-keyed like every other effect table; no shipped card data
     * declares this yet (catalog 1.3 records it as unimplemented), so the
     * registry is implemented-but-untriggered except for the resolution
     * probe's synthetic id below.
     */
    private static final Set<String> MULTI_SIDE_CONFLICT_IDS =
            new HashSet<String>();
    static {
        MULTI_SIDE_CONFLICT_IDS.add("probe_multi_side_conflict");
    }

    public static boolean isMultiSideConflict(String cardId) {
        return cardId != null && lookupSet(MULTI_SIDE_CONFLICT_IDS, cardId);
    }

    public static WaiverEffect sponsorWaiver(Card card) {
        if (card == null) return WaiverEffect.NONE;
        WaiverEffect effect = lookupMap(WAIVER_EFFECTS, card.getId());
        return effect == WaiverEffect.FREE_SPONSOR ? effect : WaiverEffect.NONE;
    }

    public static WaiverEffect participantWaiver(Conflict conflict) {
        if (conflict == null) return WaiverEffect.NONE;
        WaiverEffect effect = lookupMap(WAIVER_EFFECTS, conflict.getCard().getId());
        return effect == WaiverEffect.FREE_PARTICIPANT ? effect : WaiverEffect.NONE;
    }

    // ── Events: card id → influence gain for the playing player ─────────────
    // Data: event texts "Gain N Influence."
    private static final Map<String, Integer> EVENT_INFLUENCE = new HashMap<String, Integer>();
    static {
        // Canonical-key routing (lookupMap) collapses unchanged-text Premiere/Deluxe
        // twins; only Premiere literals are kept here. Deluxe-only deltas are in
        // separate registries (see de_conf_bio_weapon_discovery, etc.).
        EVENT_INFLUENCE.put("event_merchandising_b5",        Integer.valueOf(1));
        EVENT_INFLUENCE.put("event_affirm_alliance",         Integer.valueOf(1));
        EVENT_INFLUENCE.put("event_destiny_fulfilled",       Integer.valueOf(2));
        EVENT_INFLUENCE.put("event_secret_vorlon_aid",       Integer.valueOf(2));
    }

    // ── Events: card id → cards drawn by the playing player ─────────────────
    // Data: texts "Draw 2 cards." (+ Morden conditional, base 2 applied)
    static final Map<String, Integer> EVENT_DRAW = new HashMap<String, Integer>();
    static {
        // event_intrigues_mature has no Deluxe literal (Deluxe text is identical);
        // canonical-key routing in lookupMap resolves de_event_intrigues_mature.
        EVENT_DRAW.put("event_intrigues_mature",           Integer.valueOf(2));
        EVENT_DRAW.put("event_contact_with_vorlons",       Integer.valueOf(2));
        EVENT_DRAW.put("event_contact_with_shadows",       Integer.valueOf(2));
    }

    // ── Events: card id → influence gained by EVERY player ──────────────────
    // Data: texts "All players gain 1 Influence."
    private static final Map<String, Integer> EVENT_ALL_INFLUENCE = new HashMap<String, Integer>();
    static {
        EVENT_ALL_INFLUENCE.put("event_balance",             Integer.valueOf(1));
        EVENT_ALL_INFLUENCE.put("event_for_the_common_good", Integer.valueOf(1));
    }

    // ── Conflicts: card id → influence the LOSER loses ──────────────────────
    // Data: texts "Loser loses N Influence."
    private static final Map<String, Integer> CONFLICT_LOSER_INFLUENCE = new HashMap<String, Integer>();
    static {
        CONFLICT_LOSER_INFLUENCE.put("conf_affirmation_of_peace",    Integer.valueOf(1));
        CONFLICT_LOSER_INFLUENCE.put("conf_condemn_deportations",    Integer.valueOf(1));
        CONFLICT_LOSER_INFLUENCE.put("conf_dishonor",                Integer.valueOf(1));
        CONFLICT_LOSER_INFLUENCE.put("conf_loss_of_support",         Integer.valueOf(1));
        CONFLICT_LOSER_INFLUENCE.put("conf_hate_crime",              Integer.valueOf(2));
        // B5-1089 (WRONG #2 of 7 from B5-1032): de_conf_bio_weapon_discovery
        // deluxe delta — "loser also loses 1 Influence" on top of the
        // existing discard-2 (CONFLICT_LOSER_DISCARD). Premiere
        // conf_bio_weapon_discovery stays discards-only by omission from
        // this table (same shape as the B5-1045 AGENDA_BANS_DIPLOMACY_AFTERMATH
        // deluxe-only split). Data text (deluxe.json): "Loser must discard 2
        // cards. (Deluxe text change: loser also loses 1 Influence.)"
        CONFLICT_LOSER_INFLUENCE.put("de_conf_bio_weapon_discovery", Integer.valueOf(1));
    }

    // ── Conflicts: card id → cards the LOSER discards at random ─────────────
    // Data: texts "Loser must discard N card(s)."
    private static final Map<String, Integer> CONFLICT_LOSER_DISCARD = new HashMap<String, Integer>();
    static {
        CONFLICT_LOSER_DISCARD.put("conf_bio_weapon_discovery",    Integer.valueOf(2));
        CONFLICT_LOSER_DISCARD.put("de_conf_bio_weapon_discovery", Integer.valueOf(2));
        CONFLICT_LOSER_DISCARD.put("conf_loss_of_support",         Integer.valueOf(1));
    }

    // ── Conflicts: card id → influence the WINNER steals from the loser ─────
    // Data: "Raid Shipping": "may steal 1 Influence from the loser."
    private static final Map<String, Integer> CONFLICT_WINNER_STEAL = new HashMap<String, Integer>();
    static {
        CONFLICT_WINNER_STEAL.put("conf_raid_shipping", Integer.valueOf(1));
    }

    // ── Agendas: card id → influence at the start of each round ─────────────
    // Data: "Ongoing: Gain 1 Influence at the start of each round." /
    // "...if you have 2 or more characters in your Inner Circle, gain 1" (Deluxe).
    private static final Map<String, Integer> AGENDA_ROUND_INFLUENCE = new HashMap<String, Integer>();
    static {
        AGENDA_ROUND_INFLUENCE.put("agenda_higher_calling",       Integer.valueOf(1));
        AGENDA_ROUND_INFLUENCE.put("de_agenda_servants_of_order", Integer.valueOf(1));
    }

    // ── Agendas: card id → extra influence when owner wins a Diplomacy conflict
    // Data: "Ongoing: Gain 1 additional Influence whenever you win a Diplomacy conflict."
    private static final Map<String, Integer> AGENDA_DIPLOMACY_WIN = new HashMap<String, Integer>();
    static {
        AGENDA_DIPLOMACY_WIN.put("agenda_a_rising_power", Integer.valueOf(1));
    }

    // ── Agendas: "Ongoing: Your fleets gain +1 Military." ───────────────────
    private static final Set<String> AGENDA_FLEET_PLUS1 = new HashSet<String>(
            Arrays.asList(new String[] {
                "agenda_total_war",       "de_agenda_total_war",
                "agenda_order_above_all"
            }));

    // ── Agendas: card id → the owner's own play restriction ─────────────────
    // Data (deluxe.json, de_agenda_total_war): "You may not play Diplomacy
    // conflict cards. (Deluxe text change: restriction now also prohibits
    // playing Diplomacy Aftermath cards.)"
    //
    // B5-1045: the delta is the Aftermath extension, and it is DELUXE-ONLY —
    // the premiere twin's id is deliberately absent from this table, so the
    // premiere path is unchanged in behaviour by construction rather than by a
    // second branch. B5-1032 records that the core Diplomacy-CONFLICT
    // restriction is itself unenforced; enforcing that half is out of this
    // row's scope and would change premiere behaviour, so it is not done here.
    private static final Set<String> AGENDA_BANS_DIPLOMACY_AFTERMATH =
            new HashSet<String>(
            Arrays.asList(new String[] {
                "de_agenda_total_war"
            }));

    // ── Enhancements: location income bonus, id → extra influence/round ─────
    // Data: "Exploitation": "That Location provides 1 extra Influence per round."
    // (No bonus JSON field exists for income; magnitudes are from the text.)
    private static final Map<String, Integer> ENH_LOCATION_INCOME = new HashMap<String, Integer>();
    static {
        ENH_LOCATION_INCOME.put("enh_exploitation",    Integer.valueOf(1));
    }

    // ── B5-1995: Agenda mark grants ─────────────────────────────────────────────
    // Data: rulebook VI "E.g., the Agenda 'Servants of Order' provides the
    // ambassador of the faction with 1 Vorlon Mark while the agenda is in play."
    // Marks are purged when the agenda is discarded/replaced/blanked.
    private static final Map<String, MarkGrant> AGENDA_MARK_GRANTS = new HashMap<String, MarkGrant>();
    static {
        // Servants of Order (both sets): 1 Vorlon Mark on ambassador while in play
        AGENDA_MARK_GRANTS.put("agenda_servants_of_order",
                new MarkGrant(MarkType.VORLON, 1, MarkGrant.Target.AMBASSADOR));
        AGENDA_MARK_GRANTS.put("de_agenda_servants_of_order",
                new MarkGrant(MarkType.VORLON, 1, MarkGrant.Target.AMBASSADOR));
    }

    /** Simple record for agenda mark grants. */
    private static class MarkGrant {
        enum Target { AMBASSADOR }
        final MarkType type;
        final int count;
        final Target target;
        MarkGrant(MarkType type, int count, Target target) {
            this.type = type;
            this.count = count;
            this.target = target;
        }
    }

    // ── B5-1995: Enhancement mark grants ───────────────────────────────────────
    // Data: Vorlon Enhancement provides Vorlon marks while attached.
    private static final Map<String, MarkGrant> ENHANCEMENT_MARK_GRANTS = new HashMap<String, MarkGrant>();
    static {
        // Vorlon Enhancement (both sets): 1 Vorlon Mark on attached character while in play
        ENHANCEMENT_MARK_GRANTS.put("enh_vorlon_enhancement",
                new MarkGrant(MarkType.VORLON, 1, MarkGrant.Target.AMBASSADOR));
    }

    // ── B5-1995: Event mark grants ──────────────────────────────────────────────
    // Data: Contact with Shadows/Vorlons provide permanent marks.
    private static final Map<String, MarkGrant> EVENT_MARK_GRANTS = new HashMap<String, MarkGrant>();
    static {
        // Contact with Shadows: 1 Shadow Mark on target character (permanent)
        EVENT_MARK_GRANTS.put("event_contact_with_shadows",
                new MarkGrant(MarkType.SHADOW, 1, MarkGrant.Target.AMBASSADOR));
        EVENT_MARK_GRANTS.put("event_contact_with_vorlons",
                new MarkGrant(MarkType.VORLON, 1, MarkGrant.Target.AMBASSADOR));
    }

    // ── Public entry points ──────────────────────────────────────────────────

    /** Plays an Event card for p. Unknown ids keep the generic draw-1 floor. */
    public static void applyPlayEvent(GameState state, Player p, Card card) {
        String id = card.getId();
        boolean did = false;

        Integer inf = (Integer) lookupMap(EVENT_INFLUENCE, id);
        if (inf != null) {
            p.gainInfluence(inf.intValue());
            state.log(p.getName() + " gains " + inf + " influence (" + card.getTitle() + ").");
            did = true;
        }
        Integer draw = (Integer) lookupMap(EVENT_DRAW, id);
        if (draw != null) {
            p.drawCards(draw.intValue());
            state.log(p.getName() + " draws " + draw + " card(s) (" + card.getTitle() + ").");
            did = true;
        }
        Integer all = (Integer) lookupMap(EVENT_ALL_INFLUENCE, id);
        if (all != null) {
            for (Player q : state.getPlayers()) q.gainInfluence(all.intValue());
            state.log(card.getTitle() + ": all players gain " + all + " influence.");
            did = true;
        }
        // B5-1995: apply event mark grants (permanent marks)
        applyEventMarkGrants(state, p, card);
        if (!did) {
            p.drawCards(1);
            state.log(p.getName() + " plays " + card.getTitle()
                    + " (no specific effect registered; generic draw 1).");
        }
    }

    /** B5-0365: reveal uses the same id-keyed effect dispatch as an Event. */
    public static boolean revealContingency(GameState state, Player p,
                                            ContingencyCard card) {
        if (card == null || !card.reveal()) return false;
        state.log(p.getName() + " reveals contingency " + card.getTitle()
                + " (trigger: " + card.getTriggerCondition() + ").");
        applyPlayEvent(state, p, card);
        card.detach();
        if (p.getDeck() != null) p.getDeck().discard(card);
        return true;
    }

    /**
     * Plays an Enhancement card: attaches it to the strongest valid live
     * target and applies the JSON bonus fields. Race/Global/Location/
     * Babylon 5 enhancements have no single-card target in the model; they
     * are held on the owner's enhancement list (location income bonuses are
     * applied by RulesEngine.startRound).
     *
     * B5-0473: fleet enhancements carrying the B5-0468 explicit opponent
     * target (Censure-class) attach to the CHOSEN opponent fleet instead of
     * the owner's best. The 0468 seam records the registry fact that
     * ATTACHED-scope bonuses are read from the TARGET owner's registry
     * (FleetCard.getEffectiveMilitary reads owner.effectiveStat), so the
     * penalty is granted INTO the opponent's Player registry, keyed to their
     * fleet id. An explicit target that cannot be resolved (unknown player,
     * unknown or face-down fleet) leaves the card held in play with no
     * registry effect — no self-fallback onto the owner's own fleet.
     */
    public static void applyPlayEnhancement(GameState state, Player p, EnhancementCard card) {
        String subtype = card.getSubtype() == null ? "" : card.getSubtype();
        String id = card.getId();

        if (subtype.endsWith("_FLEET")) {
            int delta = card.getMilitaryBonus();
            p.getEnhancements().add(card);

            // B5-0473: opponent-targeted path consumes the 0468 seam.
            if (card.hasExplicitTarget()) {
                Player victim = findPlayerByName(state, card.getTargetOwnerName());
                FleetCard target = victim == null
                        ? null : fleetById(victim, card.getTargetCardId());
                if (target != null && delta != 0) {
                    target.setOwner(victim);
                    int floor = floorFor(card.getId());
                    victim.grantBonus(card.toAttachedBonus(StatKey.MILITARY,
                            Expiry.WHILE_IN_PLAY, state.getRoundNumber(), floor));
                    state.log(p.getName() + " attaches " + card.getTitle()
                            + " to " + victim.getName() + "'s " + target.getTitle()
                            + " (Military " + (delta >= 0 ? "+" : "") + delta
                            + (floor > 0 ? ", floor " + floor : "") + ").");
                } else {
                    state.log(p.getName() + " holds " + card.getTitle()
                            + " (opponent target unavailable; held in play).");
                }
                return;
            }

            FleetCard target = bestFleet(p);
            if (target != null && delta != 0) {

                // B5-0366: route the bonus through the registry, not field mutation.
                target.setOwner(p);
                int floor = floorFor(card.getId());
                p.grantBonus(StatBonus.attached(card.getId(), StatKey.MILITARY, delta,
                        target.getId(), Expiry.WHILE_IN_PLAY, state.getRoundNumber(), floor));
                state.log(p.getName() + " attaches " + card.getTitle() + " to "
                        + target.getTitle() + " (Military "
                        + (delta >= 0 ? "+" : "") + delta
                        + (floor > 0 ? ", floor " + floor : "") + ").");
            } else {
                state.log(p.getName() + " holds " + card.getTitle()
                        + " (no fleet target in play).");
            }
            return;
        }

        if (subtype.endsWith("_CHARACTER")) {
            // B5-0506 (B5-0497 slice 1): shunned-class CHARACTER enhancements
            // carry an explicit opponent target and attach to the CHOSEN
            // opponent character instead of the owner's best. The penalty is
            // granted INTO the target owner's registry (ATTACHED scope keyed to
            // their character id), read back through owner.effectiveStat. An
            // explicit target that cannot be resolved (unknown player, unknown
            // or face-down character) leaves the card held in play with no
            // registry effect — no self-fallback (B5-0473 rule).
            if (card.hasExplicitTarget()) {
                p.getEnhancements().add(card);
                Player victim = findPlayerByName(state, card.getTargetOwnerName());
                CharacterCard victimChar = victim == null
                        ? null : characterById(victim, card.getTargetCardId());
                if (victimChar != null) {
                    victimChar.setOwner(victim);
                    int r = state.getRoundNumber();
                    victim.grantBonus(StatBonus.attached(id, StatKey.DIPLOMACY,
                            card.getDiplomacyBonus(), victimChar.getId(),
                            Expiry.WHILE_IN_PLAY, r));
                    victim.grantBonus(StatBonus.attached(id, StatKey.INTRIGUE,
                            card.getIntrigueBonus(), victimChar.getId(),
                            Expiry.WHILE_IN_PLAY, r));
                    victim.grantBonus(StatBonus.attached(id, StatKey.PSI,
                            card.getPsiBonus(), victimChar.getId(),
                            Expiry.WHILE_IN_PLAY, r));
                    victim.grantBonus(StatBonus.attached(id, StatKey.LEADERSHIP,
                            card.getLeadershipBonus(), victimChar.getId(),
                            Expiry.WHILE_IN_PLAY, r));
                    state.log(p.getName() + " attaches " + card.getTitle()
                            + " to " + victim.getName() + "'s " + victimChar.getTitle()
                            + " (all stats " + card.getDiplomacyBonus() + ").");
                } else {
                    state.log(p.getName() + " holds " + card.getTitle()
                            + " (opponent target unavailable; held in play).");
                }
                return;
            }
            CharacterCard target = bestCharacter(p);
            int dip = card.getDiplomacyBonus();
            int inr = card.getIntrigueBonus();
            int psi = card.getPsiBonus();
            int lead = card.getLeadershipBonus();
            p.getEnhancements().add(card);
            int r = state.getRoundNumber();
            if (target != null) {
                // B5-0366: route each non-zero bonus through the registry.
                target.setOwner(p);
                if (dip != 0)  p.grantBonus(StatBonus.attached(card.getId(), StatKey.DIPLOMACY,   dip,  target.getId(), Expiry.WHILE_IN_PLAY, r));
                if (inr != 0)  p.grantBonus(StatBonus.attached(card.getId(), StatKey.INTRIGUE,    inr,  target.getId(), Expiry.WHILE_IN_PLAY, r));
                if (psi != 0)  p.grantBonus(StatBonus.attached(card.getId(), StatKey.PSI,         psi,  target.getId(), Expiry.WHILE_IN_PLAY, r));
                if (lead != 0) p.grantBonus(StatBonus.attached(card.getId(), StatKey.LEADERSHIP, lead, target.getId(), Expiry.WHILE_IN_PLAY, r));
            }
            if (target != null && (dip != 0 || inr != 0 || psi != 0 || lead != 0)) {
                state.log(p.getName() + " attaches " + card.getTitle() + " to "
                        + target.getTitle() + " (+" + dip + " Dip, +" + inr + " Intr, +"
                        + psi + " Psi, +" + lead + " Lead).");
            } else {
                state.log(p.getName() + " holds " + card.getTitle()
                        + " (no character target in play).");
            }
            // B5-1995: apply enhancement mark grants
            applyEnhancementMarkGrants(state, p, card, target);
            return;
        }

        if ("ENHANCEMENT_FACTION".equals(subtype)) {
            p.getEnhancements().add(card);
            int mil = card.getMilitaryBonus();
            // B5-066: faction-scope bonus goes through the registry.
            if (mil != 0) {
                p.grantBonus(StatBonus.race(card.getId(), StatKey.MILITARY, mil,
                        p.getName(), Expiry.WHILE_IN_PLAY, state.getRoundNumber()));
                state.log(p.getName() + " plays " + card.getTitle()
                        + " (all own fleets " + (mil >= 0 ? "+" : "") + mil + " Military).");
            } else {
                state.log(p.getName() + " plays " + card.getTitle() + " (faction).");
            }
            return;
        }

        // GLOBAL / BABYLON5 / LOCATION / anything else: held on owner list;
        // ENH_LOCATION_INCOME ids are applied in RulesEngine.startRound.
        p.getEnhancements().add(card);
        state.log(p.getName() + " plays " + card.getTitle()
                + " (global/location effect; held in play).");
    }

    /**
     * Applies loser penalties / winner steal for a resolved conflict card.
     * Called after the winner's influence reward. winner != loser.
     */
    public static void applyConflictOutcome(GameState state, Conflict conflict,
                                            Player winner, Player loser) {
        // B5-0366/0376: war conflicts have card == null (no ConflictCard);
        // their card-specific loser penalties don't apply — the war influence
        // swing is handled at the resolution site in RulesEngine.
        if (conflict.getCard() == null) return;
        String id = conflict.getCard().getId();

        Integer loseInf = (Integer) lookupMap(CONFLICT_LOSER_INFLUENCE, id);
        if (loseInf != null) {
            // B5-0691 (rulebook :980): while the loser's race is UNIFIED the
            // loss spills to every faction of that race; byte-identical in the
            // standard single-faction game (the race list holds only the loser).
            state.applyRaceJointInfluenceLoss(loser, loseInf.intValue());
            state.log(loser.getName() + " loses " + loseInf + " influence ("
                    + conflict.getCard().getTitle() + ").");
        }
        Integer discards = (Integer) lookupMap(CONFLICT_LOSER_DISCARD, id);
        if (discards != null) {
            int n = Math.min(discards.intValue(), loser.getHand().size());
            for (int i = 0; i < n; i++) {
                Card c = loser.getHand().remove(loser.getHand().size() - 1);
                loser.getDeck().discard(c);
            }
            if (n > 0) {
                state.log(loser.getName() + " discards " + n + " card(s) ("
                        + conflict.getCard().getTitle() + ").");
            }
        }
        Integer steal = (Integer) lookupMap(CONFLICT_WINNER_STEAL, id);
        if (steal != null) {
            int amount = Math.min(steal.intValue(), loser.getInfluence());
            if (amount > 0) {
                // B5-0691 (rulebook :980): joint loss across the loser's race
                // while UNIFIED (single-faction behaviour unchanged).
                state.applyRaceJointInfluenceLoss(loser, amount);
                winner.gainInfluence(amount);
                state.log(winner.getName() + " steals " + amount + " influence from "
                        + loser.getName() + " (" + conflict.getCard().getTitle() + ").");
            }
        }

        // B5-1966: conflict-type generic effects (PSI and INTRIGUE)
        // These apply to all conflicts of the given type, in addition to
        // any card-specific effects above.
        ConflictType type = conflict.getConflictType();
        if (type == ConflictType.PSI) {
            // PSI: loser discards hand down to 3 cards
            applyPsiLoserEffect(state, conflict, loser);
        } else if (type == ConflictType.INTRIGUE) {
            // INTRIGUE: winner steals 1 agenda from loser
            applyIntrigueWinnerEffect(state, conflict, winner, loser);
        }
    }

    /** PSI conflict loser effect: discard hand down to 3 cards. */
    private static void applyPsiLoserEffect(GameState state, Conflict conflict,
                                            Player loser) {
        List<Card> hand = loser.getHand();
        if (hand.size() <= 3) return;
        int toDiscard = hand.size() - 3;
        for (int i = 0; i < toDiscard; i++) {
            // Discard from end of hand (arbitrary selection per rulebook)
            Card c = hand.remove(hand.size() - 1);
            loser.getDeck().discard(c);
        }
        state.log(loser.getName() + " discards " + toDiscard
                + " card(s) due to PSI conflict loss ("
                + conflict.getCard().getTitle() + ").");
    }

    /** INTRIGUE conflict winner effect: steal 1 agenda from loser. */
    private static void applyIntrigueWinnerEffect(GameState state, Conflict conflict,
                                                  Player winner, Player loser) {
        AgendaCard loserAgenda = loser.getAgenda();
        if (loserAgenda == null) return;
        // Winner takes the agenda; loser loses it
        loser.setAgenda(null);
        // The agenda goes to the winner's hand per rulebook "steal"
        winner.addToHand(loserAgenda);
        state.log(winner.getName() + " steals agenda \""
                + loserAgenda.getTitle() + "\" from "
                + loser.getName() + " ("
                + conflict.getCard().getTitle() + ").");
    }

    // ── Aftermaths: card id → dispatched effect ────────────────────────────
    // B5-0990 (B5-0741 census slice 1): the first aftermath dispatch entries.
    // Data: "Negotiated Surrender" (premiere + deluxe) text: "Play after a
    // Military conflict. The loser may keep one fleet from being rotated.
    // Gain 1 Influence." (Deluxe adds: loser also draws 1 card.)
    private static final Set<String> AFTERMATH_NEGOTIATED_SURRENDER =
            new HashSet<String>();
    static {
        AFTERMATH_NEGOTIATED_SURRENDER.add("aftermath_negotiated_surrender");
        AFTERMATH_NEGOTIATED_SURRENDER.add("de_am_negotiated_surrender");
    }

    private static final Set<String> AFTERMATH_DIPLOMATIC_ADVANTAGE =
            new HashSet<String>();
    static {
        AFTERMATH_DIPLOMATIC_ADVANTAGE.add("aftermath_diplomatic_advantage");
        AFTERMATH_DIPLOMATIC_ADVANTAGE.add("de_am_diplomatic_advantage");
    }

    // ── B5-2249: core-20 frequency expansion (Sets 1-5) ────────────────────────
    private static final Set<String> AFTERMATH_UNITED_FRONT =
            new HashSet<String>();
    static {
        AFTERMATH_UNITED_FRONT.add("aftermath_united_front");
        AFTERMATH_UNITED_FRONT.add("de_am_united_front");
    }

    private static final Set<String> AFTERMATH_DRAW_TWO_INTRIGUE =
            new HashSet<String>();
    static {
        AFTERMATH_DRAW_TWO_INTRIGUE.add("aftermath_exploit_opportunities");
        AFTERMATH_DRAW_TWO_INTRIGUE.add("de_am_exploit_opportunities");
        AFTERMATH_DRAW_TWO_INTRIGUE.add("aftermath_successful_manipulation");
        AFTERMATH_DRAW_TWO_INTRIGUE.add("de_am_successful_manipulation");
    }

    private static final Set<String> AFTERMATH_HIDDEN_AGENT =
            new HashSet<String>();
    static {
        AFTERMATH_HIDDEN_AGENT.add("aftermath_hidden_agent");
        AFTERMATH_HIDDEN_AGENT.add("de_am_hidden_agent");
    }

    private static final Set<String> AFTERMATH_REFUGEES =
            new HashSet<String>();
    static {
        AFTERMATH_REFUGEES.add("aftermath_refugees");
        AFTERMATH_REFUGEES.add("de_am_refugees");
    }

    private static final Set<String> AFTERMATH_RISE_TO_POWER =
            new HashSet<String>();
    static {
        AFTERMATH_RISE_TO_POWER.add("aftermath_rise_to_power");
        AFTERMATH_RISE_TO_POWER.add("de_am_rise_to_power");
    }

    /** B5-0990: true when cardId is a Negotiated Surrender aftermath. */
    public static boolean isNegotiatedSurrender(String cardId) {
        return cardId != null && lookupSet(AFTERMATH_NEGOTIATED_SURRENDER, cardId);
    }

    /** B5-0990: true when the Negotiated Surrender is the deluxe version (draws 1 card). */
    private static boolean isDeluxeNegotiatedSurrender(String cardId) {
        return "de_am_negotiated_surrender".equals(cardId);
    }

    /** B5-1051: true when cardId is a Diplomatic Advantage aftermath. */
    public static boolean isDiplomaticAdvantage(String cardId) {
        return cardId != null && lookupSet(AFTERMATH_DIPLOMATIC_ADVANTAGE, cardId);
    }

    /** B5-2249: true when cardId is a United Front aftermath. */
    public static boolean isUnitedFront(String cardId) {
        return cardId != null && lookupSet(AFTERMATH_UNITED_FRONT, cardId);
    }

    /** B5-2249: true when cardId is a Draw Two Intrigue aftermath. */
    public static boolean isDrawTwoIntrigue(String cardId) {
        return cardId != null && lookupSet(AFTERMATH_DRAW_TWO_INTRIGUE, cardId);
    }

    /** B5-2249: true when cardId is a Hidden Agent aftermath. */
    public static boolean isHiddenAgent(String cardId) {
        return cardId != null && lookupSet(AFTERMATH_HIDDEN_AGENT, cardId);
    }

    /** B5-2249: true when cardId is a Refugees aftermath. */
    public static boolean isRefugees(String cardId) {
        return cardId != null && lookupSet(AFTERMATH_REFUGEES, cardId);
    }

    /** B5-2249: true when cardId is a Rise to Power aftermath. */
    public static boolean isRiseToPower(String cardId) {
        return cardId != null && lookupSet(AFTERMATH_RISE_TO_POWER, cardId);
    }

    // ── B5-2249: core-20 frequency expansion (Sets 6-10) ────────────────────────
    private static final Set<String> AFTERMATH_WAR_HERO =
            new HashSet<String>();
    static {
        AFTERMATH_WAR_HERO.add("aftermath_war_hero");
        AFTERMATH_WAR_HERO.add("de_am_war_hero");
    }

    private static final Set<String> AFTERMATH_APPROVAL_OF_THE_GREY =
            new HashSet<String>();
    static {
        AFTERMATH_APPROVAL_OF_THE_GREY.add("aftermath_approval_of_the_grey");
        AFTERMATH_APPROVAL_OF_THE_GREY.add("de_am_approval_of_the_grey");
    }

    private static final Set<String> AFTERMATH_FOCUS_YOUR_EFFORTS =
            new HashSet<String>();
    static {
        AFTERMATH_FOCUS_YOUR_EFFORTS.add("aftermath_focus_your_efforts");
        AFTERMATH_FOCUS_YOUR_EFFORTS.add("de_am_focus_your_efforts");
    }

    private static final Set<String> AFTERMATH_GLORY =
            new HashSet<String>();
    static {
        AFTERMATH_GLORY.add("aftermath_glory");
        AFTERMATH_GLORY.add("de_am_glory");
    }

    private static final Set<String> AFTERMATH_RETRIBUTION =
            new HashSet<String>();
    static {
        AFTERMATH_RETRIBUTION.add("aftermath_retribution");
        AFTERMATH_RETRIBUTION.add("de_am_retribution");
    }

    /** B5-2249: true when cardId is a War Hero aftermath. */
    public static boolean isWarHero(String cardId) {
        return cardId != null && lookupSet(AFTERMATH_WAR_HERO, cardId);
    }

    /** B5-2249: true when cardId is an Approval of the Grey aftermath. */
    public static boolean isApprovalOfTheGrey(String cardId) {
        return cardId != null && lookupSet(AFTERMATH_APPROVAL_OF_THE_GREY, cardId);
    }

    /** B5-2249: true when cardId is a Focus Your Efforts aftermath. */
    public static boolean isFocusYourEfforts(String cardId) {
        return cardId != null && lookupSet(AFTERMATH_FOCUS_YOUR_EFFORTS, cardId);
    }

    /** B5-2249: true when cardId is a Glory aftermath. */
    public static boolean isGlory(String cardId) {
        return cardId != null && lookupSet(AFTERMATH_GLORY, cardId);
    }

    /** B5-2249: true when cardId is a Retribution aftermath. */
    public static boolean isRetribution(String cardId) {
        return cardId != null && lookupSet(AFTERMATH_RETRIBUTION, cardId);
    }

    // ── B5-2249: core-20 frequency expansion (Sets 11-15) ───────────────────────
    private static final Set<String> AFTERMATH_REVERSE_ADVANCES =
            new HashSet<String>();
    static {
        AFTERMATH_REVERSE_ADVANCES.add("aftermath_reverse_advances");
        AFTERMATH_REVERSE_ADVANCES.add("de_am_reverse_advances");
    }

    private static final Set<String> AFTERMATH_OPPONENT_LOSE_ONE_INF =
            new HashSet<String>();
    static {
        AFTERMATH_OPPONENT_LOSE_ONE_INF.add("aftermath_assigning_blame");
        AFTERMATH_OPPONENT_LOSE_ONE_INF.add("aftermath_loss_of_face");
        AFTERMATH_OPPONENT_LOSE_ONE_INF.add("de_am_loss_of_face");
        AFTERMATH_OPPONENT_LOSE_ONE_INF.add("aftermath_disenchantment");
        AFTERMATH_OPPONENT_LOSE_ONE_INF.add("de_am_disenchantment");
    }

    private static final Set<String> AFTERMATH_OPPONENT_LOSE_TWO_INF =
            new HashSet<String>();
    static {
        AFTERMATH_OPPONENT_LOSE_TWO_INF.add("aftermath_despair");
        AFTERMATH_OPPONENT_LOSE_TWO_INF.add("de_am_despair");
        AFTERMATH_OPPONENT_LOSE_TWO_INF.add("aftermath_public_apology");
        AFTERMATH_OPPONENT_LOSE_TWO_INF.add("de_am_public_apology");
    }

    private static final Set<String> AFTERMATH_CRISIS_OF_SELF =
            new HashSet<String>();
    static {
        AFTERMATH_CRISIS_OF_SELF.add("aftermath_crisis_of_self");
        AFTERMATH_CRISIS_OF_SELF.add("de_am_crisis_of_self");
    }

    private static final Set<String> AFTERMATH_HEAL_FACE_DOWN =
            new HashSet<String>();
    static {
        AFTERMATH_HEAL_FACE_DOWN.add("aftermath_lamentations");
        AFTERMATH_HEAL_FACE_DOWN.add("de_am_lamentations");
        AFTERMATH_HEAL_FACE_DOWN.add("aftermath_in_the_line_of_duty");
        AFTERMATH_HEAL_FACE_DOWN.add("de_am_in_the_line_of_duty");
    }

    private static final Set<String> AFTERMATH_BATTLE_TESTED =
            new HashSet<String>();
    static {
        AFTERMATH_BATTLE_TESTED.add("aftermath_battle_tested");
        AFTERMATH_BATTLE_TESTED.add("de_am_battle_tested");
    }

    private static final Set<String> AFTERMATH_RESCUE =
            new HashSet<String>();
    static {
        AFTERMATH_RESCUE.add("aftermath_rescue");
        AFTERMATH_RESCUE.add("de_am_rescue");
    }

    private static final Set<String> AFTERMATH_MARTYR =
            new HashSet<String>();
    static {
        AFTERMATH_MARTYR.add("aftermath_martyr");
        AFTERMATH_MARTYR.add("de_am_martyr");
    }

    /** B5-2249: true when cardId is a Reverse Advances aftermath. */
    public static boolean isReverseAdvances(String cardId) {
        return cardId != null && lookupSet(AFTERMATH_REVERSE_ADVANCES, cardId);
    }

    /** B5-2249: true when cardId is an Opponent Lose One Influence aftermath. */
    public static boolean isOpponentLoseOneInf(String cardId) {
        return cardId != null && lookupSet(AFTERMATH_OPPONENT_LOSE_ONE_INF, cardId);
    }

    /** B5-2249: true when cardId is an Opponent Lose Two Influence aftermath. */
    public static boolean isOpponentLoseTwoInf(String cardId) {
        return cardId != null && lookupSet(AFTERMATH_OPPONENT_LOSE_TWO_INF, cardId);
    }

    /** B5-2249: true when cardId is a Crisis of Self aftermath. */
    public static boolean isCrisisOfSelf(String cardId) {
        return cardId != null && lookupSet(AFTERMATH_CRISIS_OF_SELF, cardId);
    }

    /** B5-2249: true when cardId is a Heal Face Down aftermath. */
    public static boolean isHealFaceDown(String cardId) {
        return cardId != null && lookupSet(AFTERMATH_HEAL_FACE_DOWN, cardId);
    }

    /** B5-2249: true when cardId is a Battle Tested aftermath. */
    public static boolean isBattleTested(String cardId) {
        return cardId != null && lookupSet(AFTERMATH_BATTLE_TESTED, cardId);
    }

    /** B5-2249: true when cardId is a Rescue aftermath. */
    public static boolean isRescue(String cardId) {
        return cardId != null && lookupSet(AFTERMATH_RESCUE, cardId);
    }

    /** B5-2249: true when cardId is a Martyr aftermath. */
    public static boolean isMartyr(String cardId) {
        return cardId != null && lookupSet(AFTERMATH_MARTYR, cardId);
    }

    // ── B5-2249: core-20 frequency expansion — effect dispatch ─────────────────
    /**
     * Applies the specific effect for a dispatched aftermath card.
     * Called from GameController after eligibility (triggerCondition) is verified.
     * The 'target' is the player on whom the aftermath is played (selected by
     * selectAftermathTarget), and 'winner' is the conflict winner.
     */
    public static void applyAftermathEffect(GameState state, Conflict conflict,
                                            AftermathCard am, Player target, Player winner) {
        if (state == null || conflict == null || am == null || target == null) return;
        String amId = am.getId();
        if (amId == null) return;

        // Negotiated Surrender (B5-0990): "Play after a Military conflict. The loser may keep one fleet from being rotated. Gain 1 Influence."
        // target is the loser who played the aftermath.
        // Deluxe version also draws 1 card.
        // If target is the winner (should not happen via canPlayAftermath gate), effect is a no-op.
        if (isNegotiatedSurrender(amId)) {
            if (target == winner) {
                // Winner cannot benefit from Negotiated Surrender
                return;
            }
            // Restore one committed fleet that was rotated
            for (Card c : conflict.getCommittedCards(target)) {
                if (c instanceof FleetCard && c.isRotated()) {
                    c.unrotate();
                    state.log(target.getName() + " keeps " + c.getTitle()
                            + " from being rotated (" + am.getTitle() + ").");
                    break;
                }
            }
            target.gainInfluence(1);
            state.log(target.getName() + " gains 1 influence (" + am.getTitle() + ").");
            // Deluxe version: loser also draws 1 card
            if (isDeluxeNegotiatedSurrender(amId)) {
                target.drawCards(1);
                state.log(target.getName() + " draws 1 card (deluxe " + am.getTitle() + ").");
            }
            return;
        }

        // Diplomatic Advantage (B5-1051): "Play after winning a Diplomacy conflict. Gain 2 Influence and draw 1 card."
        // target is the winner.
        if (isDiplomaticAdvantage(amId)) {
            target.gainInfluence(2);
            target.drawCards(1);
            state.log(target.getName() + " gains 2 influence and draws 1 card (" + am.getTitle() + ").");
            return;
        }

        // United Front: "Play after winning a Diplomacy conflict. Gain 1 Influence and draw 1 card."
        if (isUnitedFront(amId)) {
            target.gainInfluence(1);
            target.drawCards(1);
            state.log(target.getName() + " gains 1 influence and draws 1 card (" + am.getTitle() + ").");
            return;
        }

        // Exploit Opportunities / Successful Manipulation: "Play after winning an Intrigue conflict. Draw 2 cards."
        if (isDrawTwoIntrigue(amId)) {
            target.drawCards(2);
            state.log(target.getName() + " draws 2 cards (" + am.getTitle() + ").");
            return;
        }

        // Hidden Agent: "Play after winning an Intrigue conflict. Look at one opponent's hand and take 1 card of your choice."
        if (isHiddenAgent(amId)) {
            // Find an opponent (not target) to steal from
            Player opponent = null;
            for (Player p : state.getPlayers()) {
                if (p != target && p.getHand().size() > 0) {
                    opponent = p;
                    break;
                }
            }
            if (opponent != null) {
                // Take the first card from opponent's hand (AI/human choice would be more complex)
                Card stolen = opponent.getHand().remove(0);
                target.getHand().add(stolen);
                state.log(target.getName() + " takes " + stolen.getTitle() + " from " + opponent.getName() + " (" + am.getTitle() + ").");
            } else {
                state.log(target.getName() + " has no opponent with cards to take (" + am.getTitle() + ").");
            }
            return;
        }

        // Refugees: "Play after winning a Military conflict. Gain 1 Influence for each Location card you have in play."
        if (isRefugees(amId)) {
            int locCount = target.getLocations().size();
            if (locCount > 0) {
                target.gainInfluence(locCount);
                state.log(target.getName() + " gains " + locCount + " influence from " + locCount + " location(s) (" + am.getTitle() + ").");
            } else {
                state.log(target.getName() + " has no locations in play (" + am.getTitle() + ").");
            }
            return;
        }

        // Rise to Power: "Play after winning a Military conflict. Gain 2 Influence."
        if (isRiseToPower(amId)) {
            target.gainInfluence(2);
            state.log(target.getName() + " gains 2 influence (" + am.getTitle() + ").");
            return;
        }

        // War Hero: "Play after winning a Military conflict. One of your characters gains +2 Leadership permanently."
        if (isWarHero(amId)) {
            CharacterCard best = null;
            int bestLead = -1;
            // Check ambassador
            if (target.getAmbassador() != null) {
                target.getAmbassador().setOwner(target);
                int lead = target.getAmbassador().getPrimaryStatValue(ConflictType.MILITARY);
                if (lead > bestLead) { bestLead = lead; best = target.getAmbassador(); }
            }
            // Check inner circle
            for (CharacterCard ch : target.getInnerCircle()) {
                ch.setOwner(target);
                int lead = ch.getPrimaryStatValue(ConflictType.MILITARY);
                if (lead > bestLead) { bestLead = lead; best = ch; }
            }
            if (best != null) {
                // Grant permanent +2 Leadership via attached bonus
                target.grantBonus(StatBonus.attached(amId, StatKey.LEADERSHIP, 2,
                        best.getId(), Expiry.WHILE_IN_PLAY, state.getRoundNumber()));
                state.log(target.getName() + "'s " + best.getTitle() + " gains +2 Leadership (" + am.getTitle() + ").");
            } else {
                state.log(target.getName() + " has no characters to enhance (" + am.getTitle() + ").");
            }
            return;
        }

        // Approval of the Grey: "Minbari only. Play after winning any conflict. Gain 1 Influence and heal one face-down character."
        if (isApprovalOfTheGrey(amId)) {
            target.gainInfluence(1);
            // Heal one face-down character in inner circle
            CharacterCard healed = null;
            for (CharacterCard ch : target.getInnerCircle()) {
                if (ch.isFaceDown()) {
                    ch.heal();
                    target.markInnerCircleHealed(ch);
                    healed = ch;
                    break;
                }
            }
            if (healed != null) {
                state.log(target.getName() + " gains 1 influence and heals " + healed.getTitle() + " (" + am.getTitle() + ").");
            } else {
                state.log(target.getName() + " gains 1 influence; no face-down character to heal (" + am.getTitle() + ").");
            }
            return;
        }

        // Focus Your Efforts: "Play after winning any conflict. Move one card from your discard pile to your hand."
        if (isFocusYourEfforts(amId)) {
            Deck deck = target.getDeck();
            if (deck != null && deck.getDiscardPile() != null && !deck.getDiscardPile().isEmpty()) {
                Card retrieved = deck.getDiscardPile().remove(deck.getDiscardPile().size() - 1);
                target.getHand().add(retrieved);
                state.log(target.getName() + " retrieves " + retrieved.getTitle() + " from discard (" + am.getTitle() + ").");
            } else {
                state.log(target.getName() + " has no cards in discard pile (" + am.getTitle() + ").");
            }
            return;
        }

        // Glory: "Play after winning a Diplomacy or Military conflict. Gain 2 Influence."
        if (isGlory(amId)) {
            target.gainInfluence(2);
            state.log(target.getName() + " gains 2 influence (" + am.getTitle() + ").");
            return;
        }

        // Retribution: "Play after winning any conflict. The loser must rotate one of their Inner Circle characters."
        // The target here is the loser (since the effect applies to the loser)
        if (isRetribution(amId)) {
            // Find a non-rotated Inner Circle character of the target (loser) to rotate
            CharacterCard toRotate = null;
            if (target.getAmbassador() != null && !target.getAmbassador().isRotated()) {
                toRotate = target.getAmbassador();
            } else {
                for (CharacterCard ch : target.getInnerCircle()) {
                    if (!ch.isRotated()) { toRotate = ch; break; }
                }
            }
            if (toRotate != null) {
                toRotate.rotate();
                state.log(target.getName() + " rotates " + toRotate.getTitle() + " (" + am.getTitle() + ").");
            } else {
                state.log(target.getName() + " has no ready Inner Circle character to rotate (" + am.getTitle() + ").");
            }
            return;
        }

        // Reverse Advances: "Play after losing a Diplomacy or Intrigue conflict. Negate the winner's Influence gain."
        // The target is the loser (player who played the aftermath), winner is the conflict winner
        if (isReverseAdvances(amId)) {
            if (winner != null) {
                // The winner's influence gain from this conflict should be negated
                // Since the gain already happened, we deduct it
                // The standard conflict winner gain is 1 influence (per rulebook)
                winner.loseInfluence(1);
                state.log(winner.getName() + " loses 1 influence (negated by " + target.getName() + "'s " + am.getTitle() + ").");
            }
            return;
        }

        // Opponent Lose One Influence (Assigning Blame): "Play after losing any conflict. One opponent loses 1 Influence."
        // The target is an opponent (the winner)
        if (isOpponentLoseOneInf(amId)) {
            target.loseInfluence(1);
            state.log(target.getName() + " loses 1 influence (" + am.getTitle() + ").");
            return;
        }

        // Opponent Lose Two Influence (Despair): "Play on an opponent after they lose any conflict. That opponent loses 2 Influence."
        if (isOpponentLoseTwoInf(amId)) {
            target.loseInfluence(2);
            state.log(target.getName() + " loses 2 influence (" + am.getTitle() + ").");
            return;
        }

        // Crisis of Self: "Play on an opponent after they lose a conflict. That opponent must discard 2 cards."
        if (isCrisisOfSelf(amId)) {
            int n = Math.min(2, target.getHand().size());
            for (int i = 0; i < n; i++) {
                Card c = target.getHand().remove(target.getHand().size() - 1);
                if (c != null) target.getDeck().discard(c);
            }
            if (n > 0) {
                state.log(target.getName() + " discards " + n + " card(s) (" + am.getTitle() + ").");
            } else {
                state.log(target.getName() + " has no cards to discard (" + am.getTitle() + ").");
            }
            return;
        }

        // Heal Face Down (Lamentations / In the Line of Duty): "Heal one face-down character."
        if (isHealFaceDown(amId)) {
            CharacterCard healed = null;
            // Check ambassador first
            if (target.getAmbassador() != null && target.getAmbassador().isFaceDown()) {
                target.getAmbassador().heal();
                target.markInnerCircleHealed(target.getAmbassador());
                healed = target.getAmbassador();
            } else {
                // Check inner circle
                for (CharacterCard ch : target.getInnerCircle()) {
                    if (ch.isFaceDown()) {
                        ch.heal();
                        target.markInnerCircleHealed(ch);
                        healed = ch;
                        break;
                    }
                }
            }
            if (healed != null) {
                state.log(target.getName() + " heals " + healed.getTitle() + " (" + am.getTitle() + ").");
            } else {
                state.log(target.getName() + " has no face-down character to heal (" + am.getTitle() + ")..");
            }
            return;
        }

        // Battle Tested: "Play after participating in a Military conflict. One of your characters gains +1 Leadership permanently."
        if (isBattleTested(amId)) {
            CharacterCard best = null;
            int bestLead = -1;
            if (target.getAmbassador() != null) {
                target.getAmbassador().setOwner(target);
                int lead = target.getAmbassador().getPrimaryStatValue(ConflictType.MILITARY);
                if (lead > bestLead) { bestLead = lead; best = target.getAmbassador(); }
            }
            for (CharacterCard ch : target.getInnerCircle()) {
                ch.setOwner(target);
                int lead = ch.getPrimaryStatValue(ConflictType.MILITARY);
                if (lead > bestLead) { bestLead = lead; best = ch; }
            }
            if (best != null) {
                target.grantBonus(StatBonus.attached(amId, StatKey.LEADERSHIP, 1,
                        best.getId(), Expiry.WHILE_IN_PLAY, state.getRoundNumber()));
                state.log(target.getName() + "'s " + best.getTitle() + " gains +1 Leadership (" + am.getTitle() + ").");
            } else {
                state.log(target.getName() + " has no characters to enhance (" + am.getTitle() + ").");
            }
            return;
        }

        // Rescue: "Play after a Military conflict. Return one rotated fleet to unrotated state."
        if (isRescue(amId)) {
            for (FleetCard fl : target.getFleets()) {
                if (fl.isRotated()) {
                    fl.unrotate();
                    state.log(target.getName() + " unrotates " + fl.getTitle() + " (" + am.getTitle() + ").");
                    return;
                }
            }
            state.log(target.getName() + " has no rotated fleet to rescue (" + am.getTitle() + ").");
            return;
        }

        // Martyr: "Play after any conflict in which one of your characters was discarded. Gain 3 Influence."
        if (isMartyr(amId)) {
            target.gainInfluence(3);
            state.log(target.getName() + " gains 3 influence (" + am.getTitle() + ").");
            return;
        }

        // Unknown dispatched id — should not happen if dispatch guard is correct
        state.log("WARNING: no effect implementation for dispatched aftermath " + amId);
    }

    /** B5-0990: keep-one-fleet-from-rotating — restore the first fleet the
     *  loser committed that resolution rotated. Logged no-op when the loser
     *  committed no fleet (nothing to keep). */
    private static void restoreOneCommittedFleet(GameState state, Conflict conflict,
                                                 Player loser, AftermathCard am) {
        for (Card c : conflict.getCommittedCards(loser)) {
            if (c instanceof FleetCard && c.isRotated()) {
                c.unrotate();
                state.log(loser.getName() + " keeps " + c.getTitle()
                        + " from being rotated (" + am.getTitle() + ").");
                return;
            }
        }
        state.log(loser.getName() + " keeps no fleet from being rotated ("
                + am.getTitle() + "; no fleet committed).");
    }

    // ── B5-2002: AFTERMATH trigger evaluation at phase boundaries ────────────
    //
    // The gap this closes, stated precisely. Every AFTERMATH record carries a
    // triggerCondition (117 of 117, 14 distinct values), and the rulebook makes
    // those values PLAY conditions: rulebook :592 "Each aftermath card lists
    // Play Conditions and may only be played when these conditions are met",
    // :594 WON = the initiator won, :595 LOST = the initiator lost, :603
    // Participant widens targeting to Supported/Opposed/Attacked factions,
    // :422 the Won/Lost test is the INITIATOR's outcome, not the target's.
    //
    // Before this section the triggerCondition was decoded in two places and
    // nowhere at a phase boundary:
    //   1. RulesEngine.canPlayAftermath -> AftermathCard.isEligible, which is a
    //      correct gate but is consulted only at PLAY time, from caller-supplied
    //      booleans.
    //   2. GameController.applySimpleAftermathEffect:1028-1034, which re-reads
    //      the play-condition string as if it were the effect ("if it contains
    //      WON then gain 1 Influence"). That is the defect B5-2249 recorded
    //      ("the fallback re-reads play conditions as effects"): a play
    //      condition is a gate, not a magnitude, so the fallback paid a WON
    //      card 1 influence regardless of what the card text actually says.
    //
    // This section adds the missing third thing — a trigger EVALUATOR that runs
    // at a phase boundary, derives the situation from the resolved conflict
    // instead of from caller-supplied booleans, gates on the trigger, and fires
    // only the id-keyed registered effect. The trigger gates; it never invents
    // an effect. That is the whole point of keeping the vocabulary decode in
    // AftermathCard.isEligible and calling it rather than re-parsing it here.

    /**
     * B5-2002: the just-resolved conflict situation an aftermath trigger is
     * evaluated against. Immutable, and constructed only from a resolved
     * conflict plus its winner, so the Won/Lost booleans cannot be
     * mis-derived by a caller the way the 6-arg play gate leaves them open to.
     *
     * A trigger is ALWAYS read from the initiator's perspective (rulebook :422
     * "Aftermath cards that list 'Won' or 'Lost' as part of their card type
     * refer to whether the Initiator succeeded or failed in the conflict", and
     * :580 which repeats it for multi-side conflicts). That is why this type
     * exposes no per-player outcome: there is only one outcome in question.
     */
    public static final class AftermathTrigger {

        private final Conflict conflict;
        private final Player   winner;

        private AftermathTrigger(Conflict resolved, Player won) {
            this.conflict = resolved;
            this.winner   = won;
        }

        /**
         * B5-2002: the boundary situation. Returns null when either input is
         * null or the conflict is not resolved — an absent situation is
         * UNKNOWN, never "trigger met", which is the same UNKNOWN-is-never-a-
         * positive rule the heartbeat schema records as failure 3.
         */
        public static AftermathTrigger at(Conflict resolved, Player won) {
            if (resolved == null || won == null || !resolved.isResolved()) return null;
            return new AftermathTrigger(resolved, won);
        }

        public Conflict getConflict()      { return conflict; }
        public Player   getWinner()        { return winner; }
        public Player   getInitiator()     { return conflict.getInitiator(); }

        /** Rulebook :422 / :580 — the initiator's outcome, not the target's. */
        public boolean  initiatorWon() {
            return conflict.getInitiator() == winner;
        }

        /** The initiator commits to its own conflict, so this is normally true;
         *  read from the conflict rather than assumed. */
        public boolean  initiatorParticipated() {
            return conflict.getParticipants().contains(conflict.getInitiator());
        }

        public ConflictType conflictType()  { return conflict.getConflictType(); }
    }

    /**
     * B5-2002: true when the aftermath's Play Conditions are met by this
     * boundary situation. Delegates to AftermathCard.isEligible so the token
     * vocabulary has exactly one decoder; the 14 values present in the data
     * (LOST, MILITARY_PARTICIPANT, WON, PARTICIPANT, WON_MILITARY,
     * WON_INTRIGUE, DIPLOMACY_PARTICIPANT, INTRIGUE_PARTICIPANT,
     * LOST_DIPLOMACY, LOST_INTRIGUE, LOST_MILITARY, WON_DIPLOMACY, ANY,
     * WON_PARTICIPANT) are all covered by that method's contains-tests.
     *
     * An absent situation (null trigger) is never met.
     */
    public static boolean triggerMet(AftermathCard am, AftermathTrigger t) {
        if (am == null || t == null) return false;
        return am.isEligible(t.initiatorWon(), t.initiatorParticipated(),
                             t.conflictType());
    }

    /**
     * B5-2002: true when cardId has an id-keyed effect registered below. This
     * is the single authoritative membership test for the dispatch table.
     *
     * It exists because the same 20-term OR chain is written out again at
     * GameController:903-923, and a 20-term disjunction has no property that a
     * new entry cannot be forgotten by. That duplicate is left in place: the
     * fix is a one-line substitution at a GameController call site, and
     * GameController.java is not in this row's declared scope. Until a row
     * claims it, adding a 21st effect below is what will silently not be
     * dispatched on the AI aftermath path — recorded as a finding, not fixed
     * across a scope boundary.
     */
    public static boolean hasRegisteredEffect(String cardId) {
        if (cardId == null) return false;
        return isNegotiatedSurrender(cardId)   || isDiplomaticAdvantage(cardId)
            || isUnitedFront(cardId)           || isDrawTwoIntrigue(cardId)
            || isHiddenAgent(cardId)           || isRefugees(cardId)
            || isRiseToPower(cardId)           || isWarHero(cardId)
            || isApprovalOfTheGrey(cardId)     || isFocusYourEfforts(cardId)
            || isGlory(cardId)                 || isRetribution(cardId)
            || isReverseAdvances(cardId)       || isOpponentLoseOneInf(cardId)
            || isOpponentLoseTwoInf(cardId)    || isCrisisOfSelf(cardId)
            || isHealFaceDown(cardId)          || isBattleTested(cardId)
            || isRescue(cardId)                || isMartyr(cardId);
    }

    /**
     * B5-2002: the phase-boundary trigger evaluation entry point. Walks every
     * aftermath currently in play (GameState's attachedAftermaths registry,
     * keyed by target), gates each on its Play Conditions, and fires the
     * registered id-keyed effect for the ones whose conditions this boundary
     * satisfies. Returns the number of effects fired.
     *
     * alreadyResolved is the conflict whose aftermath resolution ALREADY ran
     * at this boundary, or null when none did. GameController resolves the
     * active conflict's aftermaths in the same AFTERMATH phase it sets, so
     * passing that conflict here is what keeps this evaluator from paying a
     * second time for an effect already applied — a double-fire is not a
     * subtle regression, it is a doubled payout. Identity, not the phase
     * constant, is the reliable test here: B5-2163 measured that GamePhase
     * AFTERMATH is entered at two unrelated boundaries (GameController:55
     * round aftermath and :647 conflict aftermath), so the phase alone cannot
     * distinguish "just resolved a conflict" from "wrapping the round".
     *
     * A gated-in card with no registered effect is a LOUD no-op, never a
     * silent pass and never a fallback that re-reads the play condition as an
     * effect — the B5-2249 defect this section exists partly to end.
     */
    public static int fireAttachedAftermathTriggers(GameState state,
                                                    AftermathTrigger t,
                                                    Conflict alreadyResolved) {
        if (state == null || t == null) return 0;
        if (t.getConflict() == alreadyResolved) return 0;

        int fired = 0;
        int gatedOut = 0;
        int unregistered = 0;
        for (Player target : state.getPlayers()) {
            for (AftermathCard am : state.getAttachedAftermaths(target)) {
                if (am == null) continue;
                if (!triggerMet(am, t)) { gatedOut++; continue; }
                if (!hasRegisteredEffect(am.getId())) {
                    unregistered++;
                    state.log("WARNING: B5-2002 aftermath trigger met for "
                              + am.getId() + " on " + target.getName()
                              + " but no effect is registered — no payout."
                              + " (phase " + state.getPhase() + ")");
                    continue;
                }
                applyAftermathEffect(state, t.getConflict(), am, target,
                                     t.getWinner());
                fired++;
            }
        }
        if (fired > 0 || unregistered > 0) {
            state.log("B5-2002 trigger evaluation at phase " + state.getPhase()
                      + ": " + fired + " fired, " + gatedOut
                      + " gated out, " + unregistered + " met but unregistered.");
        }
        return fired;
    }

    /** Agenda effect when played: fleet-wide Military bonus agendas. */
    public static void applyAgendaOnPlay(GameState state, Player p, AgendaCard agenda) {
        if (lookupSet(AGENDA_FLEET_PLUS1, agenda.getId())) {
            // B5-066: faction-scope bonus in the registry, not field mutation.
            p.grantBonus(StatBonus.race(agenda.getId(), StatKey.MILITARY, 1,
                    p.getName(), Expiry.WHILE_IN_PLAY, state.getRoundNumber()));
            state.log(p.getName() + ": " + agenda.getTitle()
                    + " grants all own fleets +1 Military.");
        }
        // B5-1995: grant marks from agenda (persist while agenda is face-up)
        applyAgendaMarkGrants(state, p, agenda);
    }

    /** Agenda effect at the start of each round (call from startRound). */
    public static void applyAgendaStartOfRound(GameState state, Player p) {
        AgendaCard agenda = p.getAgenda();
        // B5-0364: hidden agendas have no effect on play until revealed (:520).
        if (agenda == null || agenda.isFaceDown()) return;
        Integer inf = (Integer) lookupMap(AGENDA_ROUND_INFLUENCE, agenda.getId());
        if (inf == null) return;
        // de_agenda_servants_of_order text: requires 2+ Inner Circle characters.
        if ("de_agenda_servants_of_order".equals(agenda.getId())
                && p.getInnerCircle().size() < 2) {
            return;
        }
        p.gainInfluence(inf.intValue());
        state.log(p.getName() + " gains " + inf + " influence from agenda "
                + agenda.getTitle() + ".");
    }

    /**
     * B5-2251: lifts the WHILE_IN_PLAY stat bonuses an agenda granted while it
     * was in play. applyAgendaOnPlay registers those bonuses keyed by the
     * agenda's own id, so removal is by source and touches nothing else the
     * owner holds.
     *
     * Why this exists: a WHILE_IN_PLAY bonus has no round boundary to expire
     * on. Player.sweepBonusExpiries only removes END_OF_TURN and
     * START_OF_NEXT_OWNER_TURN bonuses, so a fleet-wide +1 Military granted by
     * an agenda survived the agenda itself leaving play — the +1 outlived its
     * own printed text and was still being paid out by
     * FleetCard.getEffectiveMilitary long after DISCARD_AGENDA or
     * REPLACE_AGENDA had emptied the agenda slot. Every leave-play path for an
     * agenda must call this alongside removeAgendaMarkGrants.
     */
    public static int removeAgendaBonuses(Player p, AgendaCard agenda) {
        if (p == null || agenda == null) return 0;
        return p.removeBonusesBySource(agenda.getId());
    }

    /** B5-1995: Applies mark grants from an agenda that is face-up in play. */
    public static void applyAgendaMarkGrants(GameState state, Player p, AgendaCard agenda) {
        if (agenda == null || agenda.isFaceDown()) return;
         MarkGrant grant = lookupMap(AGENDA_MARK_GRANTS, agenda.getId());
        if (grant == null) return;
        if (grant.target == MarkGrant.Target.AMBASSADOR) {
            CharacterCard ambassador = p.getAmbassador();
            if (ambassador != null) {
                int added = p.addMarksToCharacter(ambassador, grant.type, grant.count);
                if (added > 0) {
                    state.log(p.getName() + "'s " + agenda.getTitle()
                            + " grants " + added + " " + grant.type + " mark(s) to ambassador "
                            + ambassador.getTitle() + ".");
                }
            }
        }
    }

    /** B5-1995: Removes mark grants from an agenda that is being discarded/replaced/blanked. */
    public static void removeAgendaMarkGrants(GameState state, Player p, AgendaCard agenda) {
        if (agenda == null) return;
        MarkGrant grant = lookupMap(AGENDA_MARK_GRANTS, agenda.getId());
        if (grant == null) return;
        if (grant.target == MarkGrant.Target.AMBASSADOR) {
            CharacterCard ambassador = p.getAmbassador();
            if (ambassador != null) {
                int purged = p.purgeMarkFromCharacter(ambassador, grant.type);
                if (purged > 0) {
                    state.log(p.getName() + "'s " + agenda.getTitle()
                            + " is removed; " + purged + " " + grant.type
                            + " mark(s) purged from ambassador " + ambassador.getTitle() + ".");
                }
            }
        }
    }

    /** B5-1995: Applies mark grants from an enhancement that is attached. */
    private static void applyEnhancementMarkGrants(GameState state, Player p, EnhancementCard enh, CharacterCard target) {
        if (enh == null || target == null) return;
        MarkGrant grant = lookupMap(ENHANCEMENT_MARK_GRANTS, enh.getId());
        if (grant == null) return;
        if (grant.target == MarkGrant.Target.AMBASSADOR && target == p.getAmbassador()) {
            int added = p.addMarksToCharacter(target, grant.type, grant.count);
            if (added > 0) {
                state.log(p.getName() + "'s " + enh.getTitle()
                        + " grants " + added + " " + grant.type + " mark(s) to " + target.getTitle() + ".");
            }
        }
    }

    /** B5-1995: Removes mark grants from an enhancement that is being detached/discarded. */
    public static void removeEnhancementMarkGrants(GameState state, Player p, EnhancementCard enh) {
        if (enh == null) return;
        MarkGrant grant = lookupMap(ENHANCEMENT_MARK_GRANTS, enh.getId());
        if (grant == null) return;
        if (grant.target == MarkGrant.Target.AMBASSADOR) {
            CharacterCard ambassador = p.getAmbassador();
            if (ambassador != null) {
                int purged = p.purgeMarkFromCharacter(ambassador, grant.type);
                if (purged > 0) {
                    state.log(p.getName() + "'s " + enh.getTitle()
                            + " is removed; " + purged + " " + grant.type
                            + " mark(s) purged from " + ambassador.getTitle() + ".");
                }
            }
        }
    }

    /** B5-1995: Applies mark grants from an event (permanent marks). */
    private static void applyEventMarkGrants(GameState state, Player p, Card card) {
        if (card == null) return;
        MarkGrant grant = lookupMap(EVENT_MARK_GRANTS, card.getId());
        if (grant == null) return;
        if (grant.target == MarkGrant.Target.AMBASSADOR) {
            CharacterCard ambassador = p.getAmbassador();
            if (ambassador != null) {
                int added = p.addMarksToCharacter(ambassador, grant.type, grant.count);
                if (added > 0) {
                    state.log(p.getName() + " plays " + card.getTitle()
                            + " granting " + added + " " + grant.type + " mark(s) to ambassador "
                            + ambassador.getTitle() + ".");
                }
            }
        }
    }

    /** Extra influence when the agenda owner wins a Diplomacy conflict. */
    public static int agendaDiplomacyWinBonus(Player p) {
        AgendaCard agenda = p.getAgenda();
        // B5-0364: hidden agendas have no effect on play until revealed (:520).
        if (agenda == null || agenda.isFaceDown()) return 0;
        Integer bonus = (Integer) lookupMap(AGENDA_DIPLOMACY_WIN, agenda.getId());
        return bonus == null ? 0 : bonus.intValue();
    }

    /**
     * B5-1045: true when the card is a Diplomacy Aftermath — an Aftermath whose
     * Play Conditions require a Diplomacy conflict. Read off the same trigger
     * token the model already uses in AftermathCard.isEligible, so this adds no
     * new vocabulary: DIPLOMACY_PARTICIPANT, WON_DIPLOMACY and LOST_DIPLOMACY
     * are the three trigger forms in the data (6 records per set).
     */
    public static boolean isDiplomacyAftermath(AftermathCard card) {
        return card != null
                && card.getTriggerCondition().contains("DIPLOMACY");
    }

    /**
     * B5-1045: true when p's agenda in play bars p from playing Diplomacy
     * Aftermath cards (de_agenda_total_war's deluxe-only restriction).
     * A face-down agenda has no effect until revealed (B5-0364, as at
     * applyAgendaStartOfRound); the premiere twin never carries the
     * restriction, so this returns false for it unconditionally.
     */
    public static boolean agendaBarsDiplomacyAftermath(Player p) {
        if (p == null) return false;
        AgendaCard agenda = p.getAgenda();
        if (agenda == null || agenda.isFaceDown()) return false;
        return AGENDA_BANS_DIPLOMACY_AFTERMATH.contains(agenda.getId());
    }

    /**
     * B5-1045: the play-legality predicate for the deluxe restriction, called
     * from RulesEngine.canPlayAftermath. Kept as one method so the play gate
     * reads the rule rather than re-deriving it, and so the premiere path
     * returns false here by the absence of its id from the table.
     */
    public static boolean isBannedByAgenda(Player p, AftermathCard card) {
        return isDiplomacyAftermath(card) && agendaBarsDiplomacyAftermath(p);
    }

    /** Location income bonus from held location enhancements (startRound). */
    public static int enhancementLocationIncomeBonus(Player p) {
        int bonus = 0;
        for (EnhancementCard e : p.getEnhancements()) {
            Integer inc = (Integer) lookupMap(ENH_LOCATION_INCOME, e.getId());
            if (inc != null) bonus += inc.intValue();
        }
        return bonus;
    }

    /** B5-0376 Phase B: true when the enhancement augments location income. */
    public static boolean isLocationIncomeEnhancement(EnhancementCard e) {
        return e != null && lookupMap(ENH_LOCATION_INCOME, e.getId()) != null;
    }

    // ── Bonus floors: card id → minimum stat value after penalty (B5-0486) ──
    // Data: printed "minimum 1" on Censure-class fleet enhancements.
    // B5-0506 (B5-0497 slice 1): character enhancements whose text discards them
    // on a printed trigger ("Discard when that character is healed"). The
    // discard is executed by the heal site (RulesEngine.executeHealCharacter)
    // via discardsOnHeal(); this registry only answers "does this card react?".
    private static final Set<String> DISCARD_ON_HEAL = new HashSet<String>();
    static {
        DISCARD_ON_HEAL.add("enh_shunned");
    }

    private static final Map<String, Integer> BONUS_FLOORS = new HashMap<String, Integer>();
    static {
        BONUS_FLOORS.put("enh_censure",    Integer.valueOf(1));
    }
    // A controlled mercenary's effect fires once at the MERCENARY phase for
    // its controller. The pool carries zero mercenary cards (B5-0386 no-
    // evidence verdict), so this table holds only the synthetic fixture used
    // by the conformance suite; real effects arrive when data work lands.
    // Unknown ids fire a LOUD no-op — never a silent pass, so a future card
    // added without an effect is caught in play.
    private static final Map<String, Integer> MERCENARY_FIXTURE_INFLUENCE =
            new HashMap<String, Integer>();
    static {
        MERCENARY_FIXTURE_INFLUENCE.put("mer_metric_fixture", Integer.valueOf(1));
    }

    /** Executes the controlled mercenary's effect for its controller. */
    public static void applyMercenaryAction(GameState state, Player controller, Card merc) {
        if (state == null || controller == null || merc == null) return;
        Integer inf = (Integer) lookupMap(MERCENARY_FIXTURE_INFLUENCE, merc.getId());
        if (inf != null) {
            controller.gainInfluence(inf.intValue());
            state.log(controller.getName() + " controls " + merc.getTitle()
                    + ", gaining " + inf + " influence.");
            return;
        }
        System.err.println("B5-0395: mercenary " + merc.getId() + " has no "
                + "registered effect — controller " + controller.getName()
                + " gets nothing (loud no-op; add an entry when data lands).");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** B5-0473: player lookup by name (model-level scan; GameState is out of
     *  this task's scope for a lookup method). Returns null when absent. */
    private static Player findPlayerByName(GameState state, String name) {
        if (name == null) return null;
        for (Player q : state.getPlayers()) {
            if (name.equals(q.getName())) return q;
        }
        return null;
    }

    /** B5-0473: face-up fleet lookup by card id on the target player.
     *  Face-down fleets are unresolvable (identity stays hidden). */
    private static FleetCard fleetById(Player victim, String cardId) {
        if (cardId == null) return null;
        for (FleetCard f : victim.getFleets()) {
            if (cardId.equals(f.getId()) && !f.isFaceDown()) return f;
        }
        return null;
    }

    /** B5-0506: true when the enhancement id is registered to discard when
     *  its host character is healed. */
    public static boolean discardsOnHeal(String cardId) {
        return cardId != null && lookupSet(DISCARD_ON_HEAL, cardId);
    }

    /** B5-0528: card ids whose faction-held presence triggers +1 damage on
     *  attackers of the controlling player's fleets (reactive mines effect).
     *  Mirrors the DISCARD_ON_HEAL registry shape. The two pool ids
     *  (en_h_mines / en_h_energy_mines) are the shipped data; enh_mines_rt
     *  is the B5-0556 Mines Round-Trip fixture (in-memory, not a card
     *  record) and is registered only so the conformance probe's registry
     *  scan does not flag it as dead data. */
    private static final Set<String> DAMAGE_ON_ATTACK = new HashSet<String>();
    static {
        DAMAGE_ON_ATTACK.add("en_h_mines");
        DAMAGE_ON_ATTACK.add("en_h_energy_mines");
        // B5-0528: reactive-mines pool ids (both sets) drive on-attack damage.
        // Premiere ids (no namespace prefix) and Delux ids (de_ prefix).
        DAMAGE_ON_ATTACK.add("enh_mines");
        DAMAGE_ON_ATTACK.add("de_enh_mines");
        DAMAGE_ON_ATTACK.add("enh_energy_mines");
        DAMAGE_ON_ATTACK.add("de_enh_energy_mines");
        // B5-0556: enh_mines_rt is the round-trip fixture id, not a card.
        DAMAGE_ON_ATTACK.add("enh_mines_rt");
    }

    /** B5-0528: true when cardId is registered for the reactive mines damage
     *  effect. */
    public static boolean damageOnAttack(String cardId) {
        return cardId != null && lookupSet(DAMAGE_ON_ATTACK, cardId);
    }

    /** B5-0528: synonym for damageOnAttack — used by applyPlayEnhancement's
     *  fleet-branch and faction-branch logic. */
    public static boolean attackMines(String cardId) {
        return damageOnAttack(cardId);
    }

    /** B5-0506: face-up character lookup by card id on the target player.
     *  Face-down characters are unresolvable (identity stays hidden). */
    private static CharacterCard characterById(Player victim, String cardId) {
        if (cardId == null) return null;
        for (CharacterCard ch : victim.getInnerCircle()) {
            if (cardId.equals(ch.getId()) && !ch.isFaceDown()) return ch;
        }
        for (CharacterCard ch : victim.getSupportingRole()) {
            if (cardId.equals(ch.getId()) && !ch.isFaceDown()) return ch;
        }
        CharacterCard amb = victim.getAmbassador();
        if (amb != null && cardId.equals(amb.getId()) && !amb.isFaceDown()) return amb;
        return null;
    }

    /** B5-0486: returns the minimum-stat floor for an enhancement card id,
     *  or 0 when no floor is registered (default behavior). */
    private static int floorFor(String cardId) {
        if (cardId == null) return 0;
        Integer f = (Integer) lookupMap(BONUS_FLOORS, cardId);
        return f == null ? 0 : f.intValue();
    }

    private static FleetCard bestFleet(Player p) {
        FleetCard best = null;
        int bestMil = -1;
        for (FleetCard f : p.getFleets()) {
            // B5-066: use effective Military (includes bonuses) for target
            // selection. setOwner so the read path sees the registry.
            f.setOwner(p);
            int mil = f.getEffectiveMilitary();
            if (mil > bestMil) { bestMil = mil; best = f; }
        }
        return best;
    }

    private static CharacterCard bestCharacter(Player p) {
        CharacterCard best = p.getAmbassador();
        int bestStat = -1;
        if (best != null) {
            best.setOwner(p);
            bestStat = best.getPrimaryStatValue(ConflictType.DIPLOMACY)
                     + best.getPrimaryStatValue(ConflictType.INTRIGUE)
                     + best.getPrimaryStatValue(ConflictType.PSI)
                     + best.getPrimaryStatValue(ConflictType.MILITARY);
        }
        for (CharacterCard ch : p.getInnerCircle()) {
            ch.setOwner(p);
            int stat = ch.getPrimaryStatValue(ConflictType.DIPLOMACY)
                     + ch.getPrimaryStatValue(ConflictType.INTRIGUE)
                     + ch.getPrimaryStatValue(ConflictType.PSI)
                     + ch.getPrimaryStatValue(ConflictType.MILITARY);
            if (stat > bestStat) { bestStat = stat; best = ch; }
        }
        return best;
    }

    /** B5-0004 / WP-03: behaviour-referenced card ids that have no card record in
     *  the pool (synthetic test fixtures). The headless probes subtract this set
     *  before checking that every behaviour literal names a pool entry, so a
     *  fixture id that is intentionally not a card is not reported as dangling.
     *  Adding an id here is an assertion: the id is deliberately not a card. */
    public static final Set<String> NON_POOL_FIXTURE_IDS = new HashSet<String>();
    static {
        // B5-2542: synthetic multi-side conflict probe id (no card record).
        NON_POOL_FIXTURE_IDS.add("probe_multi_side_conflict");
        // B5-0386: synthetic controlled-mercenary fixture id (no card record).
        NON_POOL_FIXTURE_IDS.add("mer_metric_fixture");
        // B5-0556: synthetic Mines round-trip fixture id (no card record).
        NON_POOL_FIXTURE_IDS.add("enh_mines_rt");
    }

    /** B5-0004 / WP-03: the five ambassador characters whose text DIFFERS between
     *  Premiere and Deluxe, requiring a Deluxe behaviour table. Their Premiere
     *  ids are the canonical key; both printings are covered via the
     *  canonical-key routing in lookupSet/lookupMap. */
    public static final Set<String> CHARACTER_BEHAVIOUR_REFERENCES =
            new HashSet<String>();
    static {
        // Premiere ambassador ids with changed-text Deluxe twins (D5 governs).
        CHARACTER_BEHAVIOUR_REFERENCES.add("char_adira_tyree");
        CHARACTER_BEHAVIOUR_REFERENCES.add("char_delenn");
        CHARACTER_BEHAVIOUR_REFERENCES.add("char_gkar");
        CHARACTER_BEHAVIOUR_REFERENCES.add("char_jeffrey_sinclair");
        CHARACTER_BEHAVIOUR_REFERENCES.add("char_londo_mollari");
    }

    /** B5-0004 / WP-03: Deluxe-only ambassador text changes (D5 Deluxe-governs).
     *  These five ids have no Premiere behaviour table entry because their
     *  Premiere text differs — the Deluxe text is the governed form (D5). */
    public static final Set<String> DELUXE_CHARACTER_EFFECTS =
            new HashSet<String>();
    static {
        DELUXE_CHARACTER_EFFECTS.add("de_char_adira_tyree");
        DELUXE_CHARACTER_EFFECTS.add("de_char_delenn");
        DELUXE_CHARACTER_EFFECTS.add("de_char_gkar");
        DELUXE_CHARACTER_EFFECTS.add("de_char_jeffrey_sinclair");
        DELUXE_CHARACTER_EFFECTS.add("de_char_londo_mollari");
    }

    // ── Canonical-key routing helpers (CardIdNamespaceGuard.canonicalKey) ───────

    /** Exact-first, canonical-fallback lookup for a Map. If the exact id is
     *  absent, retries with the id's canonical key (id minus the `de_` prefix,
     *  per CardIdNamespaceGuard.canonicalKey). This collapses unchanged-text
     *  Premiere/Deluxe twin literals without keeping both in the table, while
     *  Deluxe-only entries (kept exact) still win the exact-first pass. */
    static <V> V lookupMap(Map<String, V> m, String id) {
        if (id == null) return null;
        V v = m.get(id);
        if (v != null) return v;
        return m.get(CardIdNamespaceGuard.canonicalKey(id));
    }

    /** Exact-first, canonical-fallback membership test for a Set. Mirrors
     *  lookupMap: exact id first, then canonical key. */
    static boolean lookupSet(Set<String> s, String id) {
        if (id == null) return false;
        if (s.contains(id)) return true;
        return s.contains(CardIdNamespaceGuard.canonicalKey(id));
    }

    /**
     * B5-0004 / WP-03: collects every card id the behaviour tables reference by
     * scanning all static Map and Set fields in this class. Replaces the former
     * hardcoded 10-id list (which drifted from the tables): this scan can never
     * disagree with the literals it walks.
     *
     * Aftermath slug divergence (aftermath_X / de_am_X) is NOT unified here —
     * both printings keep exact literals by design (the slugs differ in the
     * `am_` vs bare `aftermath_` portion, so canonicalKey does not collapse them).
     * Only Premiere literals are kept for unchanged-text twins; Deluxe-only
     * entries (de_conf_bio_weapon_discovery, de_agenda_servants_of_order,
     * de_agenda_total_war, and the 5 de_char_* ambassadors) are kept exact.
     */
    public static Set<String> behaviourReferences() {
        Set<String> out = new TreeSet<String>();
        scanKeys(out, WAIVER_EFFECTS);
        scanKeys(out, MULTI_SIDE_CONFLICT_IDS);
        scanKeys(out, EVENT_INFLUENCE);
        scanKeys(out, EVENT_DRAW);
        scanKeys(out, EVENT_ALL_INFLUENCE);
        scanKeys(out, CONFLICT_LOSER_INFLUENCE);
        scanKeys(out, CONFLICT_LOSER_DISCARD);
        scanKeys(out, CONFLICT_WINNER_STEAL);
        scanKeys(out, AGENDA_ROUND_INFLUENCE);
        scanKeys(out, AGENDA_DIPLOMACY_WIN);
        scanKeys(out, AGENDA_FLEET_PLUS1);
        scanKeys(out, AGENDA_BANS_DIPLOMACY_AFTERMATH);
        scanKeys(out, ENH_LOCATION_INCOME);
        scanKeys(out, AGENDA_MARK_GRANTS);
        scanKeys(out, ENHANCEMENT_MARK_GRANTS);
        scanKeys(out, EVENT_MARK_GRANTS);
        scanKeys(out, CHARACTER_BEHAVIOUR_REFERENCES);
        scanKeys(out, DELUXE_CHARACTER_EFFECTS);
        scanKeys(out, AFTERMATH_NEGOTIATED_SURRENDER);
        scanKeys(out, AFTERMATH_DIPLOMATIC_ADVANTAGE);
        scanKeys(out, AFTERMATH_UNITED_FRONT);
        scanKeys(out, AFTERMATH_DRAW_TWO_INTRIGUE);
        scanKeys(out, AFTERMATH_HIDDEN_AGENT);
        scanKeys(out, AFTERMATH_REFUGEES);
        scanKeys(out, AFTERMATH_RISE_TO_POWER);
        scanKeys(out, AFTERMATH_WAR_HERO);
        scanKeys(out, AFTERMATH_APPROVAL_OF_THE_GREY);
        scanKeys(out, AFTERMATH_FOCUS_YOUR_EFFORTS);
        scanKeys(out, AFTERMATH_GLORY);
        scanKeys(out, AFTERMATH_RETRIBUTION);
        scanKeys(out, AFTERMATH_REVERSE_ADVANCES);
        scanKeys(out, AFTERMATH_OPPONENT_LOSE_ONE_INF);
        scanKeys(out, AFTERMATH_OPPONENT_LOSE_TWO_INF);
        scanKeys(out, AFTERMATH_CRISIS_OF_SELF);
        scanKeys(out, AFTERMATH_HEAL_FACE_DOWN);
        scanKeys(out, AFTERMATH_BATTLE_TESTED);
        scanKeys(out, AFTERMATH_RESCUE);
        scanKeys(out, AFTERMATH_MARTYR);
        scanKeys(out, DISCARD_ON_HEAL);
        scanKeys(out, BONUS_FLOORS);
        scanKeys(out, MERCENARY_FIXTURE_INFLUENCE);
        scanKeys(out, DAMAGE_ON_ATTACK);
        return out;
    }

    private static void scanKeys(Set<String> out, Map m) {
        for (Iterator it = m.keySet().iterator(); it.hasNext();) {
            out.add(String.valueOf(it.next()));
        }
    }

    private static void scanKeys(Set<String> out, Set s) {
        for (Iterator it = s.iterator(); it.hasNext();) {
            out.add(String.valueOf(it.next()));
        }
    }
}
