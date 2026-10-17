package b5ccg.engine;

import b5ccg.model.*;
import b5ccg.model.enums.*;
import java.util.*;

/** Enforces official B5 CCG rules for conflict initiation, resolution,
 *  aftermath eligibility, and victory checking.
 *
 *  Build Influence (rulebook V. / VI.):
 *    - A faction may spend an action to "Build Influence" only when its
 *      Influence Rating is <= 9. The action requires rotating an Inner
 *      Circle character, spends 3 influence (raised from the faction pool,
 *      which means net rating change is -3 + 1 = -2 influence entering the
 *      pool, but the rating itself goes up by 1 — implemented here as
 *      spend 3, then +1 rating token), and raises the rating by 1.
 *      Rulebook VI.: factions with a Rating >= 10 may not use this action.
 */
public class RulesEngine {

    // B5-0371: healing a ready character clears normal damage. A neutralized
    // IC member may heal severe damage one token at a time, then flip up.
    public boolean canHealCharacter(Player p, CharacterCard ch) {
        if (p == null || ch == null || ch.isRotated() || !ch.canActAfterNeutralization()) return false;
        boolean inner = p.getInnerCircle().contains(ch);
        boolean supporting = p.getSupportingRole().contains(ch);
        if (!inner && !supporting) return false;
        if (ch.isNeutralized()) {
            return inner;
        }
        if (ch.isFaceDown()) return false;
        // IC characters may rotate undamaged to provide aid to their ambassador.
        return inner || ch.getDamageTokens() > 0;
    }

    public boolean executeHealCharacter(Player p, CharacterCard ch, GameState state) {
        if (!canHealCharacter(p, ch)) return false;
        ch.rotate();
        int healed = ch.healDamage();
        if (p.getInnerCircle().contains(ch)) p.markInnerCircleHealed(ch);
        state.log(p.getName() + " rotates " + ch.getTitle() + " to heal (" + healed + " step).");
        // B5-0506 (B5-0497 slice 1): shunned-class reactive discard -- after a
        // successful heal, the discard-on-heal enhancement ATTACHED TO THIS
        // character is discarded from WHOEVER holds it (its playing player,
        // typically the opponent) and its granted bonuses are lifted. Matching
        // is by targetCardId == the healed character, so other held
        // discard-on-heal cards attached to other characters are untouched.
        for (Player holder : state.getPlayers()) {
            Iterator<EnhancementCard> eit = holder.getEnhancements().iterator();
            while (eit.hasNext()) {
                EnhancementCard enh = eit.next();
                if (CardEffects.discardsOnHeal(enh.getId())
                        && ch.getId().equals(enh.getTargetCardId())) {
                    eit.remove();
                    for (Player r : state.getPlayers()) {
                        r.removeBonusesBySource(enh.getId());
                    }
                    state.log(holder.getName() + "'s " + enh.getTitle()
                            + " is discarded (" + ch.getTitle() + " healed).");
                }
            }
        }
        return true;
    }

    // Repairs affect normal damage only. Severe damage and neutralization
    // require healing rules and are never purchased with influence.
    public boolean canRepairCard(Player p, Card card) {
        if (p == null || card == null || card.isRotated() || card.isNeutralized()
                || card.isFaceDown() || !card.canActAfterNeutralization()
                || card.getDamageTokens() <= 0) return false;
        if (!(card instanceof FleetCard) && !(card instanceof LocationCard)) return false;
        return p.controlsCard(card) && p.getAppliedPool() >= card.getDamageTokens();
    }

    public boolean executeRepairCard(Player p, Card card, GameState state) {
        if (!canRepairCard(p, card)) return false;
        int amount = card.getDamageTokens();
        if (!p.applyInfluence(amount)) return false;
        int repaired = card.repairDamage(amount);
        if (repaired != amount) {
            p.gainInfluence(amount - repaired);
            return false;
        }
        card.rotate();
        state.log(p.getName() + " repairs " + card.getTitle() + " (" + repaired
                + " damage for " + repaired + " influence from pool).");
        return true;
    }

    /** End-of-action-round ambassador aid: all IC members must have healed. */
    public void finishActionRound(GameState state) {
        for (Player p : state.getPlayers()) {
            if (p.allInnerCircleHealed() && p.getAmbassador() != null) {
                p.getAmbassador().heal();
                state.log(p.getName() + " fully heals the ambassador with Inner Circle aid.");
            }
            p.resetInnerCircleHealing();
        }
    }

    // ── Enhancement attachment / detachment (B5-1977) ─────────────────────────
    //
    // Rulebook :510 — "Enhancement cards remain in play so long as the game
    // entity they modify remains in play. They are discarded if the card they
    // modify is discarded." and :846 — cards "discarded from play" include
    // "supporting characters who have been neutralized and any aftermaths or
    // enhancements attached to that character."
    //
    // CardEffects.applyPlayEnhancement (B5-0468/B5-0506) already GRANTS the
    // attached bonuses; what nothing did was lift them when the character they
    // modify leaves play. Both attachment spellings are honoured here:
    //   1. the explicit B5-0468 opponent target (EnhancementCard.targetCardId),
    //   2. the self-target path, which selects bestCharacter(p) inside
    //      CardEffects and therefore leaves targetCardId null on the card. That
    //      attachment is recorded only in the bonus registry as an ATTACHED
    //      StatBonus whose sourceCardId is the enhancement and whose
    //      targetCardId is the character, so the registry IS the record and is
    //      read back here rather than second-guessed with a new field.

    /** Returns every enhancement held by ANY player that modifies {@code ch}.
     *  Matching is by card id (explicit opponent target) or by an ATTACHED
     *  registry bonus sourced by the enhancement and keyed to the character.
     *  Returns an empty list, never null. */
    public List<EnhancementCard> enhancementsAttachedTo(GameState state, CharacterCard ch) {
        List<EnhancementCard> attached = new ArrayList<EnhancementCard>();
        if (state == null || ch == null || ch.getId() == null) return attached;
        for (Player holder : state.getPlayers()) {
            for (EnhancementCard enh : holder.getEnhancements()) {
                if (isAttachedTo(state, enh, ch) && !attached.contains(enh)) {
                    attached.add(enh);
                }
            }
        }
        return attached;
    }

    private boolean isAttachedTo(GameState state, EnhancementCard enh, CharacterCard ch) {
        if (enh == null) return false;
        if (ch.getId().equals(enh.getTargetCardId())) return true;   // B5-0468 seam
        for (Player r : state.getPlayers()) {
            for (StatBonus b : r.getBonuses()) {
                if (b.scope == BonusScope.ATTACHED
                        && enh.getId().equals(b.sourceCardId)
                        && ch.getId().equals(b.targetCardId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * B5-1977: discards every enhancement modifying {@code ch} and lifts the
     * bonuses it granted, from whichever player holds it and to whichever
     * player's registry they were granted into (the B5-0468 opponent-targeted
     * case grants into the TARGET owner's registry, not the holder's).
     * Returns how many enhancements were discarded. Safe to call for a
     * character no enhancement modifies: returns 0 and logs nothing.
     */
    public int detachEnhancementsFromCharacter(GameState state, CharacterCard ch) {
        if (state == null || ch == null) return 0;
        int detached = 0;
        // Snapshot first: the sweep mutates the holders' enhancement lists.
        List<EnhancementCard> attached =
                new ArrayList<EnhancementCard>(enhancementsAttachedTo(state, ch));
        for (EnhancementCard enh : attached) {
            Player holder = null;
            for (Player candidate : state.getPlayers()) {
                if (candidate.getEnhancements().remove(enh)) { holder = candidate; break; }
            }
            // B5-1995: remove mark grants from the detached enhancement
            CardEffects.removeEnhancementMarkGrants(state, holder != null ? holder : ch.getOwner(), enh);
            for (Player r : state.getPlayers()) r.removeBonusesBySource(enh.getId());
            if (holder != null && holder.getDeck() != null) holder.getDeck().discard(enh);
            state.log(enh.getTitle() + " is discarded (" + ch.getTitle()
                    + " discarded from play; rulebook :510).");
            detached++;
        }
        return detached;
    }

    // ── Build Influence action ────────────────────────────────────────────────

    /** Returns true when player p may Build Influence:
     *  Influence Rating 1..9 AND at least one unrotated Inner Circle character. */
    public boolean canBuildInfluence(Player p) {
        if (p.getInfluence() > 9) return false;
        if (p.getAppliedPool() < 3) return false;
        for (CharacterCard ch : p.getInnerCircle()) {
            if (!ch.isRotated() && ch.canActAfterNeutralization()) return true;
        }
        return false;
    }

    /** Rotate the chosen leader, spend 3 influence, then raise rating by 1. */
    public void executeBuildInfluence(Player p, CharacterCard leader, GameState state) {
        if (!canBuildInfluence(p)) {
            state.log(p.getName() + " tried to Build Influence but cannot.");
            return;
        }
        if (!p.getInnerCircle().contains(leader)) {
            state.log(p.getName() + " tried to Build Influence with non-IC character.");
            return;
        }
        if (leader.isRotated() || !leader.canActAfterNeutralization()) {
            state.log(p.getName() + " tried to Build Influence with already-rotated "
                      + leader.getTitle() + ".");
            return;
        }

        if (!p.applyInfluence(3)) return;
        leader.rotate();
        p.gainInfluence(1);           // rating does +1 (net pool change = -2)

        state.log(p.getName() + " builds influence: "
                  + leader.getTitle() + " rotates, rating now " + p.getInfluence());
    }

    // ── Promote Character to Inner Circle (rulebook VI., B5-0321) ───────────

    /**
     * Cost to promote supporting character ch into p's Inner Circle
     * (rulebook: ACTION - Promote a Character to the Inner Circle):
     * the character's influence cost (doubled if loyal to a different race)
     * PLUS one additional influence for each character already in the Inner
     * Circle. "Plus one for each character that is already a member" — the
     * ambassador IS a member, so this is simply innerCircle.size().
     *
     * B5-0323 note: cards carry no cost field yet, so the influence cost is
     * currently 0 for every card. The double-cost rule still composes when a
     * cost exists; the +IC-member term is live today.
     */
    public int promotionCost(Player p, CharacterCard ch) {
        int base = ch.getCost();   // B5-0323: the card's influence cost
        if (ch.getLoyalty() != Loyalty.fromRace(p.getRace())
                && ch.getLoyalty() != Loyalty.NEUTRAL
                && ch.getLoyalty() != Loyalty.ANY) {
            base = base * 2;   // double-cost for other-race loyal characters
        }
        return base + p.getInnerCircle().size();
    }

    /**
     * B5-1999 (rulebook §IV "Group Cards", :496): groups are Supporting Cards
     * and "may not be promoted to the Inner Circle". The same sentence governs
     * fleets (:500) and locations (:504). The CharacterCard-typed canPromote
     * below could never receive a GroupCard, so this Card-typed gate states
     * the rule in the rulebook's own terms instead of leaving it implied by a
     * Java signature: the promotion COST for a group is not a number, it is a
     * refusal. Callers holding a bare Card (the controller's PROMOTE_CHARACTER
     * branch reads one) use this form; a group is refused before any cost is
     * computed, so promotionCost is never reached for one.
     *
     * Note the row premise said groups "act as one unit for conflicts" and
     * carry "promotion costs of member personalities". Both are the opposite of
     * the printed rule — :496 says groups have no abilities and may never be
     * promoted, and the participant vocabulary (:434, :607) is fleet and
     * character only. The rulebook governs; see DECISIONS.
     */
    public boolean canPromote(Player p, Card c) {
        if (c instanceof GroupCard)  return false;   // :496
        if (c instanceof FleetCard)  return false;   // :500
        if (c instanceof LocationCard) return false; // :504
        if (!(c instanceof CharacterCard)) return false;
        return canPromote(p, (CharacterCard) c);
    }

    /** Returns true when p may promote ch into the Inner Circle now:
     *  ch is a ready supporting character, an unrotated Inner Circle member
     *  exists to rotate, and p can afford promotionCost. */
    public boolean canPromote(Player p, CharacterCard ch) {
        if (ch == null || !p.getSupportingRole().contains(ch)) return false;
        if (ch.isRotated() || ch.isFaceDown() || !ch.canActAfterNeutralization()) return false;
        if (ch instanceof AsylumCharacterCard) return false; // B5-0661
        boolean hasLeader = false;
        for (CharacterCard ic : p.getInnerCircle()) {
            if (!ic.isRotated() && ic.canActAfterNeutralization()) { hasLeader = true; break; }
        }
        if (!hasLeader) return false;
        return p.getAppliedPool() >= promotionCost(p, ch);
    }

    /** Rotate the chosen IC leader, apply the promotion cost, move ch into
     *  the Inner Circle (ready — the rulebook only requires the sponsor to
     *  rotate, not the promoted character). */
    public void executePromote(Player p, CharacterCard ch, CharacterCard leader,
                               GameState state) {
        if (!canPromote(p, ch)) {
            state.log(p.getName() + " tried to promote "
                      + (ch == null ? "(null)" : ch.getTitle()) + " but cannot.");
            return;
        }
        if (leader == null || !p.getInnerCircle().contains(leader)
                || leader.isRotated() || !leader.canActAfterNeutralization()) {
            state.log(p.getName() + " tried to promote with an invalid leader.");
            return;
        }

        int cost = promotionCost(p, ch);
        if (!p.applyInfluence(cost)) return;
        leader.rotate();
        p.recruitToInnerCircle(ch);

        // B5-2245: the logged cost is the `cost` local, NOT a second
        // promotionCost(p, ch) call. promotionCost reads innerCircle.size(), and
        // recruitToInnerCircle has already grown it by one, so the re-read
        // reported cost+1 — the log contradicted the amount actually charged by
        // exactly the IC term that makes promotion cost what it does. The
        // charge was always right; only the record of it was wrong, which is
        // worse than useless here because this line is the audit trail.
        state.log(p.getName() + " promotes " + ch.getTitle()
                  + " to the Inner Circle (" + leader.getTitle() + " rotates, cost "
                  + cost + " = " + (cost - p.getInnerCircle().size()) + " listed + "
                  + (p.getInnerCircle().size() - 1) + " per existing IC member"
                  + "); IC now " + p.getInnerCircle().size());
    }

    // ── Group cards (B5-1999; rulebook §IV "Group Cards", :496) ─────────────

    /**
     * B5-1999: the sponsorship cost of bringing a GROUP into play.
     *
     * Rulebook :657 ("Any character in the Inner Circle may rotate to bring a
     * new supporting Character, Enhancement, Group, Location or Fleet into
     * play from your hand ... Your faction must apply the required influence
     * cost listed on the sponsored card") fixes the cost at the card's LISTED
     * cost. There is deliberately no double-cost term and no Inner-Circle-size
     * term here, and that is a rulebook distinction rather than an omission:
     *
     *   - the double-cost rule belongs to CHARACTERS (§IV:484 "Sponsoring
     *     loyal or neutral characters requires applying their listed influence
     *     cost. Sponsoring characters loyal to a different race requires
     *     applying double the character's listed influence cost"). Its sibling
     *     rules restate the same shape for fleets (:500) and locations (:504).
     *   - a group with a race in its Card Type is RESTRICTED, not dearer:
     *     :496 "Only the player controlling the race listed as part of the Card
     *     Type may bring a restricted card into play." So the race check is an
     *     absolute legality gate (canSponsorGroup), never a multiplier — which
     *     also matches :1034, where a faction may sponsor "groups, enhancements
     *     and agendas which are not restricted to a race, though not Human
     *     groups which are non-Psi Corps groups".
     *
     * The Inner-Circle-size promotion term (:484) belongs to promotion, and a
     * group cannot be promoted at all — see canPromote(Player, Card).
     */
    public int groupSponsorshipCost(GroupCard gr) {
        return (gr == null) ? 0 : Math.max(0, gr.getCost());
    }

    /**
     * B5-1999: may p bring group gr into play as a Supporting Card now?
     * Three gates, in rulebook order:
     *   1. in hand (:657 "from your hand");
     *   2. the influence cost is affordable (:657 "must apply the required
     *      influence cost ... or this action may not be performed");
     *   3. race restriction — an absolute refusal, not a doubled cost (:496).
     *
     * The Limited rule (:496 "Groups are limited unless otherwise specified",
     * Glossary :1166 "If a second copy of a limited card is determined to be in
     * play ... the additional copy is discarded") is a post-play resolution of
     * an already-bad board state rather than a play-time gate, so it is NOT
     * checked here: refusing the play would leave the player holding a card he
     * may never legally play. See discardLimitedGroupCopies.
     */
    public boolean canSponsorGroup(Player p, GroupCard gr) {
        if (p == null || gr == null) return false;
        if (!p.getHand().contains(gr)) return false;
        if (p.getAppliedPool() < groupSponsorshipCost(gr)) return false;
        return gr.getLoyalty().isPlayableBy(p.getRace());   // :496 absolute
    }

    /**
     * B5-1999: can this card be committed to the conflict as a participant?
     *
     * Rulebook :496 gives groups no abilities, and the participant vocabulary is
     * fleet-and-character only: :434 "Any fleet that supports, opposes or attacks
     * during a conflict becomes a 'participant fleet'. Any character who
     * supports, opposes, attacks, or leads a fleet that participates during a
     * conflict becomes a 'participant character'", and :607 defines "Participant
     * Character" identically. A group's printed effects ("Rotate this Group
     * during any Military conflict. Add 2 to your Military total") are a
     * rotate-for-effect on the card's controller's total, NOT a commitment of
     * the group itself — which is why the group contributes 0 and must not
     * appear among the committed cards.
     *
     * B5-1999 measured this red first: an OPEN conflict (no participation
     * filter) and a WAR conflict (ConflictCard absent, so no filters at all)
     * both accepted a GroupCard commit and then reported isParticipantCard
     * true for it.
     *
     * SCOPE NOTE, deliberate: Conflict.canCommitCard is the participation
     * authority and lives in model/, which is outside this row's claimed scope
     * (engine/ plus model/GroupCard.java). This gate is therefore expressed in
     * engine/ and must be consulted by every engine commit path; closing it at
     * the model layer too is the natural follow-up and is recorded in the
     * report rather than done here.
     */
    public boolean canParticipateInConflict(Card c) {
        if (c instanceof GroupCard) return false;   // :496 / :434 / :607
        return true;
    }

    // ── Recruit (sponsor) a supporting character (B5-0323) ────────────────

    /** Cost to recruit (sponsor) supporting character ch from hand
     *  (rulebook §Sponsor): the character's influence cost, doubled when the
     *  character is loyal to a different race; neutral characters at no
     *  additional cost. */
    /** Base sponsor cost (rulebook §Sponsor): card cost, doubled for other-race
     *  loyalty; neutral characters at no additional cost. */
    public int baseRecruitCost(Player p, CharacterCard ch) {
        int base = ch.getCost();
        if (isDoubleCostRequired(ch, p.getRace())) {
            base = base * 2;
        }
        return base;
    }

    /**
     * Rulebook §Sponsor (:659): loyal characters whose race differs from the
     * sponsoring faction cost double; neutral characters are exempt.
     */
    public boolean isDoubleCostRequired(CharacterCard ch, Race faction) {
        if (ch == null || faction == null) return false;
        Loyalty cardRace = ch.getLoyalty();
        return cardRace != Loyalty.fromRace(faction) && cardRace != Loyalty.NEUTRAL
                && cardRace != Loyalty.ANY;
    }

    /** B5-0339: effective sponsor cost — the base minus the ambassador's
     *  assistant sponsor discount (rulebook §IV), floored at 0. */
    public int recruitCost(Player p, CharacterCard ch) {
        return Math.max(0, baseRecruitCost(p, ch) - p.getSponsorDiscount());
    }

    /**
     * Sponsor evaluation keeps the full cost and waiver semantics together.
     * A free sponsor waives the entire listed cost, including any race-based
     * doubling; normal recruiting retains the existing discount calculation.
     */
    public SponsorCost sponsorCost(Player p, CharacterCard ch) {
        if (CardEffects.sponsorWaiver(ch) == CardEffects.WaiverEffect.FREE_SPONSOR) {
            return SponsorCost.waived(baseRecruitCost(p, ch));
        }
        return new SponsorCost(recruitCost(p, ch), true, false);
    }

    /** True when p may recruit ch from hand now: the card is in hand and
     *  the faction can apply its influence cost (rulebook: "apply the
     *  required influence cost ... or this action may not be performed"). */
    public boolean canRecruit(Player p, CharacterCard ch) {
        if (ch == null || !p.getHand().contains(ch)) return false;
        return p.getAppliedPool() >= sponsorCost(p, ch).getAmount();
    }

    // ── Sponsor: listed cost, refuse when short, deduct on success ───────────
    //
    // B5-2245. Rulebook :657 defines ONE action — "Any character in the Inner
    // Circle may rotate to bring a new supporting Character, Enhancement,
    // Group, Location or Fleet into play from your hand. ... Your faction must
    // apply the required influence cost listed on the sponsored card being
    // brought into play or this action may not be performed."
    //
    // Before this section the rulebook's single Sponsor action was split across
    // three paths that disagreed about what it costs, and the card types the
    // rulebook names were not all covered by any of them:
    //   - Character  -> RECRUIT_CHARACTER, cost correct (doubling :663, assistant
    //     discount :492), but NO Inner-Circle rotation is checked.
    //   - Group      -> canSponsorGroup, listed cost + absolute race gate.
    //   - Enhancement / Location / Fleet -> reachable only via PLAY_CARD's
    //     generic path, which charges the bare listed cost with no race
    //     restriction and no rotation.
    // So an other-race restricted enhancement or a hostile-race location could
    // be brought into play for its listed cost when the rulebook refuses it
    // outright (:665 "only the player controlling that race may play such
    // cards"). That is the payment defect this row names.
    //
    // The two cost terms are NOT interchangeable, and conflating them is the
    // trap: :663 makes a different-race CHARACTER dearer (double), while :664
    // and :665 make a different-race card of any OTHER type ILLEGAL (an
    // absolute refusal, never a doubled price). A single "off-race multiplier"
    // cannot express both, so this method keeps them apart by card type.

    /**
     * B5-2245: what p pays to sponsor card c under rulebook :657 — the listed
     * influence cost, less the ambassador's assistant sponsor discount
     * (:492 "allow your ambassador, later that turn, to apply 1 influence less
     * than usual when sponsoring a card", which is scoped to "a card" and not
     * to characters), floored at 0.
     *
     * Character and Group delegate to the paths that already encode their
     * extra rules, so there is one cost per type rather than two that can
     * drift. There is deliberately NO Inner-Circle term here: :657 charges the
     * listed cost for sponsorship, while :669 adds "one additional influence for
     * each character that is already a member" for PROMOTION only. A sponsor
     * cost carrying an IC term would overcharge every sponsorship.
     */
    public int sponsorshipCost(Player p, Card c) {
        if (p == null || c == null) return 0;
        if (c instanceof CharacterCard) {
            return sponsorCost(p, (CharacterCard) c).getAmount();
        }
        if (c instanceof GroupCard) {
            return groupSponsorshipCost((GroupCard) c);
        }
        return Math.max(0, c.getCost() - p.getSponsorDiscount());
    }

    /** The listed cost before any assistant discount, for the consumption
     *  bookkeeping in executeSponsorCard. */
    public int sponsorshipListedCost(Card c) {
        return (c == null) ? 0 : Math.max(0, c.getCost());
    }

    /** True when p has an unrotated, unneutralized Inner Circle character able
     *  to sponsor — rulebook :657 "Any character in the Inner Circle may
     *  rotate to bring a new supporting [card] into play". The ambassador counts
     *  as an Inner Circle member (rulebook :488). */
    public boolean hasReadySponsor(Player p) {
        if (p == null) return false;
        for (CharacterCard ic : p.getInnerCircle()) {
            if (!ic.isRotated() && ic.canActAfterNeutralization()) return true;
        }
        return false;
    }

    /**
     * B5-2245: may p sponsor card c into play as a Supporting Card now?
     * Gates in rulebook order:
     *   1. the card type is one the rulebook names (:657);
     *   2. it is in hand (:657 "from your hand");
     *   3. a ready Inner Circle member exists to rotate (:657);
     *   4. the race restriction, which for every type but characters is an
     *      ABSOLUTE refusal rather than a doubled price (:664 restricted
     *      enhancements, :665 "only the player controlling that race may play
     *      such cards"). A character loyal to another race is deliberately NOT
     *      refused here — :663 makes it dearer instead;
     *   5. affordability (:657 "or this action may not be performed").
     *
     * This does NOT replace canRecruit or canSponsorGroup, which remain the
     * gates for the RECRUIT_CHARACTER and PLAY_CARD paths; it is the gate for
     * the sponsorship of the card types that had none.
     */
    public boolean canSponsorCard(Player p, Card c) {
        if (p == null || c == null) return false;
        if (!(c instanceof EnhancementCard || c instanceof LocationCard
                || c instanceof FleetCard)) return false;
        if (!p.getHand().contains(c)) return false;
        if (!hasReadySponsor(p)) return false;
        if (!c.getLoyalty().isPlayableBy(p.getRace())) return false;   // :664 / :665 absolute
        return p.getAppliedPool() >= sponsorshipCost(p, c);
    }

    /**
     * B5-2245: sponsors card c for p. Refuses when short, and deducts on
     * success — the two halves of :657's "must apply the required influence
     * cost ... or this action may not be performed".
     *
     * Order matters and is the point of this method: the cost is applied BEFORE
     * anything moves, so a refusal leaves the hand, the Inner Circle and the
     * influence pool exactly as they were. Player.applyInfluence already refuses
     * without deducting when the pool is short, so the refund case cannot arise.
     */
    public void executeSponsorCard(Player p, Card c, CharacterCard leader,
                                    GameState state) {
        if (p == null || state == null || c == null) return;
        if (!canSponsorCard(p, c)) {
            state.log(p.getName() + " cannot sponsor "
                      + c.getTitle() + " (not in hand, no ready Inner Circle member, restricted race, or not enough applied influence).");
            return;
        }
        if (leader == null || !p.getInnerCircle().contains(leader)
                || leader.isRotated() || !leader.canActAfterNeutralization()) {
            state.log(p.getName() + " cannot sponsor " + c.getTitle()
                      + " with that Inner Circle member.");
            return;
        }

        int listed = sponsorshipListedCost(c);
        int cost   = sponsorshipCost(p, c);
        if (!p.applyInfluence(cost)) {
            state.log(p.getName() + " cannot apply enough influence to sponsor "
                      + c.getTitle() + ".");
            return;
        }
        int discounted = listed - cost;
        if (discounted > 0) {
            p.consumeSponsorDiscount(discounted);
            state.log(p.getName() + " sponsors at an assistant discount ("
                      + cost + " instead of " + listed + ").");
        }

        leader.rotate();
        p.removeFromHand(c);
        if (c instanceof EnhancementCard) {
            CardEffects.applyPlayEnhancement(state, p, (EnhancementCard) c);
        } else if (c instanceof LocationCard) {
            p.getLocations().add((LocationCard) c);
            state.log(p.getName() + " sponsors location " + c.getTitle() + ".");
        } else if (c instanceof FleetCard) {
            p.getFleets().add((FleetCard) c);
            state.log(p.getName() + " sponsors fleet " + c.getTitle() + ".");
        }
        state.log(p.getName() + " sponsors " + c.getTitle() + " for " + cost
                  + " influence (" + leader.getTitle() + " rotates).");
    }

    // ── Agenda lifecycle (B5-0364; rulebook :520/:719, B5-0345 Tier-1 #3) ───

    /**
     * Sponsor legality (one-major rule, rulebook :520 "sponsor a new one if
     * your faction does not currently have an agenda card in play"; row scope
     * = this gate only — the :719 sponsor ROTATION cost is a pre-existing
     * gap recorded for a later task). Race playability stays with the
     * offer layers (AI gate) as today.
     */
    public boolean canSponsorAgenda(Player p, AgendaCard ag) {
        if (p == null || ag == null) return false;
        return p.getAgenda() == null;
    }

    /** Discard (:719 "considered an action"; :522 Major agendas cannot be
     *  discarded — they must be REPLACEd). */
    public boolean canDiscardAgenda(Player p) {
        AgendaCard a = p.getAgenda();
        if (a == null) return false;
        return !a.isMajorAgenda();
    }

    /**
     * Replace (:520 an Inner Circle character rotates to replace; :719 the
     * new agenda comes from your hand and must be one you can sponsor, a
     * Major Agenda may only be replaced by another Major, and a hidden
     * agenda may never be the replacement).
     */
    public boolean canReplaceAgenda(Player p, AgendaCard replacement,
                                    CharacterCard leader) {
        AgendaCard current = p.getAgenda();
        if (current == null || replacement == null) return false;
        if (current == replacement) return false;
        if (!p.getHand().contains(replacement)) return false;
        if (replacement.isFaceDown()) return false;
        if (!replacement.getLoyalty().isPlayableBy(p.getRace())) return false;
        if (current.isMajorAgenda() && !replacement.isMajorAgenda()) return false;
        if (leader == null || !p.getInnerCircle().contains(leader)) return false;
        if (leader.isRotated() || leader.isFaceDown() || !leader.canActAfterNeutralization()) return false;
        return true;
    }

    /** Reveal (:520 hidden agendas have no effect until revealed; :719
     *  reveal is an action). The sponsor-at-that-time check lives in the
     *  controller ("could not sponsor at that time, so discard instead"). */
    public boolean canRevealAgenda(Player p) {
        AgendaCard a = p.getAgenda();
        return a != null && a.isFaceDown();
    }

    // ── Conflict resolution ──────────────────────────────────────────────────

    public Player resolveConflict(Conflict conflict, GameState state) {
        ConflictType type = conflict.getConflictType();
        HashMap<Player, Integer> totals = new HashMap<Player, Integer>();

        for (Player p : conflict.getParticipants()) {
            int total = 0;
            for (Card c : conflict.getCommittedCards(p)) {
                total += c.getPrimaryStatValue(type);
            }
            if (p.getAmbassador() != null && !p.getAmbassador().isFaceDown()
                    && !conflict.getCommittedCards(p).contains(p.getAmbassador())) {
                total += p.getAmbassador().getPrimaryStatValue(type);
            }
            totals.put(p, Math.max(0, total));
        }

        // Rulebook ("Conflicts"): the initiator wins only if the conflict
        // receives MORE support than opposition; equal or more opposition
        // means the initiator loses. Before B5-0309 the Conflict could not
        // express sides and this loop fed a highest-total-wins heuristic
        // (audit deviation D14).
        //
        // B5-4170 (report §8 S1): the crown is decided from the totals as
        // they are AT RESOLUTION TIME — the sums above read the live card
        // stats, so damage drift, neutralized-zero and bonus changes after
        // commit count now, not at initiation. Three families:
        //   war        — unchanged shape: resolveWarOutcome swings
        //                RACE/LOCATION targets on the uncontested crown;
        //   multi-side (rulebook :1210 second Won clause; B5-2223 catalog
        //                1.3; CardEffects registry) — the initiator wins
        //                iff his support strictly exceeds EVERY other side;
        //   regular    — strict exceed only: ties lose (:1210 first Won
        //                clause). The 0-0 uncontested escape hatch is kept
        //                verbatim from B5-0309: the engine requires a
        //                winner and with no opposer there is no one else to
        //                crown — every contested comparison is strict.
        Player winner;
        int    winVal;
        if (conflict.isWarConflict()) {
            if (conflict.supportTotal() > conflict.oppositionTotal()
                    || conflict.oppositionTotal() == 0) {
                winner = conflict.getInitiator();
            } else {
                winner = leadingOpposer(conflict, totals);
            }
        } else if (isMultiSideConflict(conflict)) {
            winner = strictHighestSide(conflict, totals);
        } else {
            if (conflict.supportTotal() > conflict.oppositionTotal()
                    || conflict.oppositionTotal() == 0) {
                winner = conflict.getInitiator();
            } else {
                winner = leadingOpposer(conflict, totals);
            }
        }
        winVal = totals.containsKey(winner) ? totals.get(winner) : 0;

        conflict.resolve(winner);
        String cardTitle = conflict.getCard() != null
                ? conflict.getCard().getTitle()
                : "War Conflict";
        state.log(cardTitle + " won by " + winner.getName()
                  + " (support=" + conflict.supportTotal()
                  + ", opposition=" + conflict.oppositionTotal() + ")");

        winner.gainInfluence(conflict.getInfluenceReward());

        for (Player p : conflict.getParticipants()) {
            for (Card c : conflict.getCommittedCards(p)) {
                if (c instanceof FleetCard) c.rotate();
            }
        }

        for (Player p : conflict.getParticipants()) {
            if (p != winner) {
                int diff = winVal - (totals.containsKey(p) ? totals.get(p) : 0);
                // B5-0309 note (audit D10): the pre-0309 code damaged ANY
                // loser's ambassador (≥3-point gap) regardless of conflict
                // type; damage is a MILITARY-resolution consequence.
                if (diff >= 3 && p.getAmbassador() != null
                        && conflict.getConflictType() == ConflictType.MILITARY) {
                    p.getAmbassador().damage();
                    state.log(p.getName() + "'s ambassador is damaged.");
                }
                // Supporting-role characters that participated are discarded on loss
                List<Card> pCards = new ArrayList<Card>(conflict.getCommittedCards(p));
                for (Card c : pCards) {
                    if (c instanceof CharacterCard) {
                        CharacterCard ch = (CharacterCard) c;
                        if (p.getSupportingRole().contains(ch)) {
                            p.getSupportingRole().remove(ch);
                            // B5-1977 (rulebook :510/:846): enhancements
                            // modifying a character discarded from play are
                            // discarded with it.
                            detachEnhancementsFromCharacter(state, ch);
                            p.getDeck().discard(ch);
                            state.log(p.getName() + ": " + ch.getTitle()
                                      + " discarded (supporting role loss).");
                        }
                    }
                }
            }
        }

        // B5-0376: war-conflict outcome resolution — influence swing,
        // location capture, and tension increment.
        if (conflict.isWarConflict()) {
            resolveWarOutcome(conflict, winner, state);
        }

        // B5-1923: card-specific loser outcome (discard, influence loss, steal)
        // belongs to resolution itself, not to whichever caller remembers it:
        // GameController applied it after resolveConflict while every headless
        // caller silently skipped it, so engine resolution never produced the
        // rulebook loser penalties (B5-1032 WRONG #2: Bio-Weapon discard-2 never
        // fired). Single primary loser, selected exactly as the controller did:
        // the initiator when the initiator lost, else the first non-winner
        // participant. War conflicts exit early inside applyConflictOutcome on
        // their null card, exactly as before.
        Player primaryLoser = null;
        if (conflict.getInitiator() != winner) {
            primaryLoser = conflict.getInitiator();
        } else {
            for (Player q : conflict.getParticipants()) {
                if (q != winner) { primaryLoser = q; break; }
            }
        }
        if (primaryLoser != null) {
            CardEffects.applyConflictOutcome(state, conflict, winner, primaryLoser);
        }

        // B5-2251: an agenda in play that grants a Diplomacy-win bonus pays it
        // here, in resolution itself. It was applied in GameController after
        // resolveConflict returned, so it was UI-only: every headless
        // resolution — the conformance suite, the probes, the replay harness —
        // silently skipped a rulebook bonus. Same defect class and the same
        // fix as B5-1923 above.
        if (conflict.getConflictType() == ConflictType.DIPLOMACY) {
            int agendaBonus = CardEffects.agendaDiplomacyWinBonus(winner);
            if (agendaBonus > 0) {
                winner.gainInfluence(agendaBonus);
                state.log(winner.getName() + " gains " + agendaBonus
                        + " influence from agenda (Diplomacy win).");
            }
        }

        return winner;
    }

    // ── B5-0376: war conflict outcome resolution ──────────────────────────────
    /** B5-0376 Phase B: resolve a war-conflict outcome — influence swing,
     *  location capture/suppression, and tension increment. Made public
     *  so conformance tests can drive resolution directly.
     *  uncontested RACE_TARGET, location capture for LOCATION_TARGET won by
     *  the initiator, and a +1 tension increment toward the target (clamped
     *  at 5). Proposal §3.3.
     */
    public void resolveWarOutcome(Conflict conflict, Player winner, GameState state) {
        Player initiator = conflict.getInitiator();
        if (conflict.getWarKind() == WarKind.RACE_TARGET) {
            boolean initiatorWon = winner == initiator;
            boolean contested = !conflict.getOpposers().isEmpty()
                    || conflict.anyAttackOccurred();
            if (initiatorWon && !contested) {
                Player target = conflict.getTarget();
                if (target != null && target != winner) {
                    // B5-0691 (rulebook :980): joint loss across the target's
                    // race while UNIFIED (:1000 exempts Civil War). Note the
                    // winner's gain stays single-faction — only losses spill.
                    state.applyRaceJointInfluenceLoss(target, 1);
                    winner.gainInfluence(1);
                    state.log("War outcome: " + target.getName()
                            + " loses 1, " + winner.getName()
                            + " gains 1 (uncontested race war).");
                }
            }
        }
        if (conflict.getWarKind() == WarKind.LOCATION_TARGET
                && conflict.getTargetLocation() != null
                && winner == initiator) {
            LocationCard loc = conflict.getTargetLocation();
            boolean wasSuppressed = loc.isEffectsSuppressed();
            if (wasSuppressed && loc.getLoyalty() == Loyalty.fromRace(winner.getRace())) {
                loc.setCapturedBy(null);
                loc.setEffectsSuppressed(false);
                state.log("War outcome: " + winner.getName()
                        + " recaptures " + loc.getTitle()
                        + " (effects restored).");
            } else {
                loc.setCapturedBy(initiator);
                loc.setEffectsSuppressed(true);
                removeLocationIncomeEnhancements(initiator);
                int suppressIncome = loc.getInfluencePerRound();
                state.log("War outcome: " + initiator.getName()
                        + " captures " + loc.getTitle() + " ("
                        + suppressIncome + "/round suppressed).");
                // B5-0437: station capture is a source — raise the capturing
                // faction's station influence rating (B5-0428 proposal
                // §Sources). Only the human-side station rating is wired
                // today; shadow/vorlon ratings stay inert until a card hook
                // lands (B5-0354 discipline).
                state.getStation().gainInfluence(1);
                state.setStationSourceFired(true);
            }
        }

        Player target;
        if (conflict.getWarKind() == WarKind.LOCATION_TARGET) {
            // Tension runs toward the location's original faction owner (the
            // printed faction), not the current occupier — capture mutates
            // capturedBy before this block, so read the card's faction here.
            Loyalty owner = conflict.getTargetLocation() != null
                    ? conflict.getTargetLocation().getLoyalty() : null;
            target = null;
            if (owner != null) {
                for (Player pp : state.getPlayers()) {
                    if (Loyalty.fromRace(pp.getRace()) == owner) { target = pp; break; }
                }
            }
        } else {
            target = conflict.getTarget();
        }
        if (target != null) {
            Race targetRace = target.getRace();
            Race initiatorRace = initiator.getRace();
            if (targetRace != null && initiatorRace != null
                    && targetRace != initiatorRace) {
                state.raiseTension(targetRace, initiatorRace, 1);
                state.log("Tension: " + targetRace + " toward "
                        + initiatorRace + " raised to "
                        + state.getTensionMatrix().getTension(targetRace, initiatorRace)
                        + " (war outcome).");
            }
        }
    }

    /** B5-0376 Phase B: clear player-wide location-income enhancements after a
     *  location capture. Enhancements are player-scoped (not attached to a
     *  specific location), so all held location-income-augmenting enhancements
     *  are discarded. Lives in the engine (not model) to keep the model layer
     *  free of engine dependencies. */
    private void removeLocationIncomeEnhancements(Player p) {
        Iterator<EnhancementCard> it = p.getEnhancements().iterator();
        while (it.hasNext()) {
            if (CardEffects.isLocationIncomeEnhancement(it.next())) it.remove();
        }
    }

    // ── B5-0376: war conflict legality ────────────────────────────────────────
    /**
     * Returns true when p may declare a war conflict now: p has an action
     * remaining, has not passed, has not initiated any conflict this turn
     * (one-conflict-per-turn gate), has neither forfeited nor surrendered
     * (B5-1825), and p's faction is at war with at least
     * one other faction. Proposal §3.2.
     */
    public boolean canDeclareWarConflict(Player p, GameState state) {
        if (p.isPassed()) return false;
        // B5-1825: a player who has forfeited or surrendered has ceased play
        // (rulebook :454 "loses the game, and ceases play"; :817 "Pick up your
        // cards and go home") and may not start a war conflict. The B5-1609
        // audit measured both statuses returning true here with the faction held
        // constant. Uses the existing consolidated GameState.isPlayerActive
        // check rather than re-testing the two flags, so this gate, and the
        // B5-1705 / B5-1706 siblings, share one definition of "still playing".
        if (!state.isPlayerActive(p)) return false;
        if (p.getActionsLeft() <= 0) return false;
        if (state.hasInitiatedConflictThisTurn(p)) return false;
        return state.isAtWar(p.getRace());
    }

    /**
     * Returns true when p may initiate the specific war conflict described by
     * {@code kind}, {@code raceTarget}, and {@code locTarget}. For
     * RACE_TARGET the race target must be non-null and at war with p; for
     * LOCATION_TARGET the location must be non-null and owned by a faction
     * at war with p. Proposal §3.2.
     */
    public boolean canInitiateWarConflict(Player p, WarKind kind,
                                           Player raceTarget, LocationCard locTarget,
                                           GameState state) {
        if (!canDeclareWarConflict(p, state)) return false;
        if (kind == WarKind.RACE_TARGET) {
            if (raceTarget == null) return false;
            return state.isAtWar(p.getRace(), raceTarget.getRace());
        }
        if (kind == WarKind.LOCATION_TARGET) {
            if (locTarget == null) return false;
            Player owner = state.findLocationOwner(locTarget);
            if (owner == null) return false;
            return state.isAtWar(p.getRace(), owner.getRace());
        }
        return false;
    }

    /**
     * Creates and returns a new war conflict after legality checks pass.
     * The caller (GameController) is responsible for setting it as the
     * active conflict and resolving it.
     */
    public Conflict declareWarConflict(Player p, WarKind kind,
                                        Player raceTarget, LocationCard locTarget,
                                        GameState state) {
        if (!canInitiateWarConflict(p, kind, raceTarget, locTarget, state)) {
            return null;
        }
        Conflict conflict;
        if (kind == WarKind.RACE_TARGET) {
            conflict = new Conflict(kind, p, raceTarget);
        } else {
            conflict = new Conflict(kind, p, locTarget);
        }
        return conflict;
    }

    /** The opposition participant with the highest total (insertion order
     *  breaks ties); only called when opposition strictly exceeds support. */
    private Player leadingOpposer(Conflict conflict, Map<Player, Integer> totals) {
        Player best    = null;
        int    bestVal = -1;
        for (Player p : conflict.getOpposers()) {
            int v = totals.containsKey(p) ? totals.get(p).intValue() : 0;
            if (v > bestVal) { bestVal = v; best = p; }
        }
        return best;
    }

    /**
     * B5-4170 (rulebook :1210 second Won clause; B5-2223 catalog row 1.3):
     * true when the conflict card is registered multi-side (support-only,
     * non-opposable, "any player may be supported"). Keyed by card id in
     * CardEffects like every other per-card rule; no shipped card data
     * declares this yet, so the registry is provable only via the synthetic
     * probe id (catalog 1.3 "not implemented" -> "implemented, untriggered
     * by shipped cards").
     */
    private boolean isMultiSideConflict(Conflict conflict) {
        return conflict.getCard() != null
                && CardEffects.isMultiSideConflict(conflict.getCard().getId());
    }

    /**
     * B5-4170: crowns the STRICTLY highest side of a multi-side conflict.
     * The initiator Wins iff his own total strictly exceeds every other
     * participant's; a tie at the top (including initiator == best other)
     * is a loss for the initiator and crowns the first participant to reach
     * the best other-total (committed-map insertion order, mirroring
     * leadingOpposer's tie behaviour). Never returns null: with only the
     * initiator committed, his total strictly exceeds the implicit -1 floor
     * and he is crowned uncontested.
     */
    private Player strictHighestSide(Conflict conflict, Map<Player, Integer> totals) {
        Player initiator = conflict.getInitiator();
        int    initValue = totals.containsKey(initiator)
                ? totals.get(initiator).intValue() : 0;
        Player bestOther    = null;
        int    bestOtherVal = -1;
        for (Player p : conflict.getParticipants()) {
            if (p == initiator) continue;
            int value = totals.containsKey(p) ? totals.get(p).intValue() : 0;
            if (value > bestOtherVal) { bestOtherVal = value; bestOther = p; }
        }
        if (initValue > bestOtherVal) return initiator;
        return bestOther;
    }

    // ── Victory check ────────────────────────────────────────────────────────

    public Player checkVictory(GameState state) {
        Player lastStanding = null;
        int remaining = 0;
        for (Player p : state.getPlayers()) {
            if (!state.isPlayerActive(p)) continue;
            remaining++;
            lastStanding = p;
        }
        if (remaining == 1) return lastStanding;

        // B5-0661 (rulebook :821): if every other player has surrendered and
        // the last remaining active player still stands, that player scores a
        // Major Victory — routed through majorVictory() so the win is recorded
        // as a Major Victory path rather than a second divergent victory type.
        // Surrendered players are NOT forfeited, so the last-standing check
        // above does not catch this; we count active (non-forfeited AND
        // non-surrendered) players here.
        int activeRemaining = 0;
        Player lastActive = null;
        for (Player p : state.getPlayers()) {
            if (!state.isPlayerActive(p)) continue;
            activeRemaining++;
            lastActive = p;
        }
        if (activeRemaining == 1 && lastActive != null) {
            if (majorVictory(state, lastActive)) return lastActive;
        }

        // Standard Victory condition 2 (rulebook :176): if Babylon 5 has an
        // Influence Rating of 20 or more at the end of a turn — and one player
        // eligible to win a standard victory is leading in Power — then that
        // player wins. checkVictory runs at the round boundary (the engine's
        // "end of turn"; interpretation recorded in DECISIONS B5-0340).
        // Eligibility (rulebook :170 "eligible to win by scoring a Standard
        // Victory" = not BARRED): no major agenda, and no Shadow War
        // (rulebook :178 — during the Shadow War NO Standard Victory is
        // possible, so condition 2 is inert there; B5-0354 finding). The
        // "leading in Power" predicate is strictly-greatest, single winner —
        // a tie crowns nobody (D12 discipline).
        Player stationLeader = stationVictory(state);
        if (stationLeader != null) return stationLeader;

        for (Player p : state.getPlayers()) {
            if (p.hasForfeited()) continue;
            AgendaCard agenda = p.getAgenda();
            // B5-0364: a hidden (face-down) agenda has NO effect on play
            // until revealed (rulebook :520) — neither its win condition nor
            // its major-agenda standard bar applies while face-down.
            // Agenda-driven win: the agenda's own condition governs
            // (e.g. "Reach 20 Influence", Military Supremacy, Most Inner Circle).
            if (agenda != null && !agenda.isFaceDown()
                    && agenda.isConditionMet(state, p)) {
                return p;
            }
            // B5-0629: Major Victory path 1 (rulebook :182), wired after the
            // agenda-condition scan and before the Standard path — a Major
            // Victory is precisely the path a major-agenda holder is entitled
            // to at :178 ("Instead, they must score a Major Victory to win"),
            // so it must fire where the Standard path is barred, and it
            // carries no bar of its own. Path 2 (the agenda's own requirement)
            // is the agenda-condition scan above.
            if (majorVictory(state, p)) return p;
            // Standard victory (rulebook: Victory): 20 power AND more than any
            // other player. A major agenda in play blocks the standard path
            // (a hidden one does not — it takes effect only on reveal).
            if (agenda == null || agenda.isFaceDown() || !agenda.isMajorAgenda()) {
                if (standardVictory(state, p)) return p;
            }
        }
        return null;
    }

    // ── B5-0663: victory-path query (additive, preserves checkVictory) ────────

    /**
     * B5-0663 — query WHICH victory path crowned a winner, plus the winner's
     * Power total and exactly one path-specific qualifier that explains the win.
     * Purely additive: {@link #checkVictory(GameState)} and every caller of it
     * are untouched. Returns null when nobody has won yet (distinct from a
     * sentinel enum — a not-yet-over game is not a "no winner" game).
     *
     * Path order mirrors {@link #checkVictory(GameState)} exactly (B5-0641):
     * last standing → station condition 2 → agenda condition → major → standard.
     * A reorder would silently change which path fires when two paths qualify on
     * the same state (e.g. 30-vs-15 Major+Standard overlap — Major wins because
     * it is tested first in checkVictory).
     */
    public VictoryPathResult checkVictoryPath(GameState state) {
        Player winner = checkVictory(state);
        if (winner == null) return null;
        int power = winner.getInfluence();

        // Path 1: last standing (B5-0641 path 1, checkVictory lines 560–568).
        // checkVictory returns the sole non-forfeited player when remaining == 1.
        int remaining = 0;
        for (Player p : state.getPlayers()) {
            if (!p.hasForfeited()) remaining++;
        }
        if (remaining == 1) {
            return new VictoryPathResult(VictoryPath.LAST_STANDING, power,
                    Integer.valueOf(remaining));
        }

        // Path 2: station condition 2 (B5-0641 path 2, checkVictory line 599,
        // stationVictory lines 644–661).
        Babylon5Station station = state.getStation();
        if (!state.isShadowWar()
                && station.getInfluence() >= Babylon5Station.CONDITION_2_THRESHOLD) {
            Player stationLeader = null;
            int leaders = 0;
            for (Player p : state.getPlayers()) {
                if (p.hasForfeited()) continue;
                AgendaCard agenda = p.getAgenda();
                if (agenda != null && !agenda.isFaceDown() && agenda.isMajorAgenda()) continue;
                if (strictlyLeads(state, p, true)) {
                    stationLeader = p;
                    leaders++;
                }
            }
            if (leaders == 1 && stationLeader == winner) {
                return new VictoryPathResult(VictoryPath.STATION_CONDITION_2, power,
                        Integer.valueOf(station.getInfluence()));
            }
        }

        // Path 3: agenda condition (B5-0641 path 3, checkVictory lines 610–613).
        AgendaCard agenda = winner.getAgenda();
        if (agenda != null && !agenda.isFaceDown() && agenda.isConditionMet(state, winner)) {
            return new VictoryPathResult(VictoryPath.AGENDA_CONDITION, power,
                    agenda.getWinConditionKey());
        }

        // Path 4: major victory (B5-0641 path 4, checkVictory line 621,
        // majorVictory lines 691–698). Includes the B5-0661 surrender case
        // (activeRemaining == 1 && majorVictory) — that routes through this
        // same majorVictory check, so it is the MAJOR path, not last-standing.
        if (majorVictory(state, winner)) {
            int nextHighest = 0;
            for (Player q : state.getPlayers()) {
                if (q == winner || q.hasForfeited() || q.hasSurrendered()) continue;
                if (q.getInfluence() > nextHighest) nextHighest = q.getInfluence();
            }
            return new VictoryPathResult(VictoryPath.MAJOR, power,
                    Integer.valueOf(power - nextHighest));
        }

        // Path 5: standard victory (B5-0641 path 5, checkVictory lines 625–626,
        // standardVictory lines 673–677).
        if (standardVictory(state, winner)) {
            int nextHighest = 0;
            for (Player q : state.getPlayers()) {
                if (q == winner || q.hasForfeited() || q.hasSurrendered()) continue;
                if (q.getInfluence() > nextHighest) nextHighest = q.getInfluence();
            }
            return new VictoryPathResult(VictoryPath.STANDARD, power,
                    Integer.valueOf(power - nextHighest));
        }

        // Unreachable: checkVictory returned a winner but no path matched.
        // Defensive fallback — the B5-0663 VPS section asserts this line is
        // never reached on any state checkVictory can produce.
        return new VictoryPathResult(VictoryPath.STANDARD, power,
                Integer.valueOf(power));
    }

    /**
     * B5-0340 — Standard Victory condition 2 (rulebook :176). Fires only when
     * the station's Influence Rating is 20+ AND exactly one non-forfeited,
     * standard-ELIGIBLE player strictly leads in Power; a tie crowns nobody.
     * Eligibility = not barred: no major agenda (rulebook :178 "Other cards
     * in play may also make a player ineligible ... e.g. playing a Major
     * Agenda") and no Shadow War (rulebook :178 — no Standard Victory during
     * the Shadow War, and condition 2 IS a Standard Victory). Runs before the
     * per-player scan; today it can never fire (station influence starts at
     * 0 and nothing moves it — B5-0354 research), which is the intended
     * rulebook-conservative default.
     */
    private Player stationVictory(GameState state) {
        if (state.isShadowWar()) return null;
        if (state.getStation().getInfluence() < Babylon5Station.CONDITION_2_THRESHOLD) return null;
        Player leader = null;
        int leaders = 0;
        for (Player p : state.getPlayers()) {
            if (p.hasForfeited()) continue;
            AgendaCard agenda = p.getAgenda();
            // B5-0364: hidden agendas are inert (:520) — a face-down Major
            // does not bar its owner from the station-crown path until reveal.
            if (agenda != null && !agenda.isFaceDown() && agenda.isMajorAgenda()) continue;
            if (strictlyLeads(state, p, true)) {
                leader = p;
                leaders++;
            }
        }
        return leaders == 1 ? leader : null;
    }

    /**
     * Standard victory: at least 20 power and STRICTLY more than every other
     * non-forfeited player ("Have 20 Power, and more than any other player").
     * A tie with any opponent blocks the win; forfeited players do not count.
     * B5-0629 (rulebook :178): during the Shadow War NO Standard Victory is
     * possible — condition 1 included. Condition 2's stationVictory already
     * carried this guard from day one (B5-0354); condition 1 did not, which
     * let a 20+ strictly-leading player be crowned mid-Shadow-War (proven RED
     * by the B5-0629 MJR probe before this fix).
     */
    private boolean standardVictory(GameState state, Player p) {
        if (state.isShadowWar()) return false;   // B5-0629: :178 guard (was condition-2-only)
        if (p.getInfluence() < 20) return false;
        return strictlyLeads(state, p, false);
    }

    /**
     * B5-0629 — Major Victory path 1 (rulebook :182): "Have at least 20
     * Power, and at least 10 more than each other player". Interpreted as:
     * every OTHER non-forfeited non-surrendered player sits at least 10 Power
     * below the candidate; a 9-point lead or a tie crowns nobody. Forfeited
     * AND surrendered players are excluded from the comparison (a surrendered
     * player is out of the game — B5-0661), matching standardVictory/
     * strictlyLeads ("each other player" reads as players still in the game;
     * interpretation recorded in DECISIONS B5-0629). No major-agenda bar and
     * no Shadow War gate: :178 names Major Victory as the path that REMAINS
     * when Standard Victory is barred or the War has begun.
     */
    private boolean majorVictory(GameState state, Player p) {
        if (p.getInfluence() < 20) return false;
        for (Player q : state.getPlayers()) {
            if (q == p || q.hasForfeited() || q.hasSurrendered()) continue;
            if (q.getInfluence() > p.getInfluence() - 10) return false;
        }
        return true;
    }

    /** Shared D12 strict-leader predicate. Condition 2 excludes barred players
     *  from candidate and tie-blocking comparisons; condition 1 compares all.
     *  B5-0661: surrendered players are also excluded (out of the game). */
    private boolean strictlyLeads(GameState state, Player p,
                                  boolean ignoreMajorAgendaPlayers) {
        for (Player q : state.getPlayers()) {
            if (q == p || q.hasForfeited() || q.hasSurrendered()) continue;
            if (ignoreMajorAgendaPlayers) {
                AgendaCard agenda = q.getAgenda();
                if (agenda != null && !agenda.isFaceDown() && agenda.isMajorAgenda()) continue;
            }
            if (q.getInfluence() >= p.getInfluence()) return false;
        }
        return true;
    }

    // ── B5-0661: Unconditional Surrender (rulebook :815–:821) ────────────────

    /**
     * B5-0661: surrender is legal when the phase is the discard round
     * (GamePhase.DRAW), the surrendering player is not already surrendered or
     * forfeited, the target is another non-surrendered non-forfeited player,
     * and the two players' factions are at war (rulebook :817: "You must
     * surrender to a player of a race with whom you are at war").
     */
    public boolean canSurrender(Player p, Player target, GameState state) {
        if (state.getPhase() != GamePhase.DRAW) return false;
        if (p.hasSurrendered() || p.hasForfeited()) return false;
        if (target == null || target == p) return false;
        if (target.hasSurrendered() || target.hasForfeited()) return false;
        Race myRace = p.getRace();
        Race theirRace = target.getRace();
        if (myRace == null || theirRace == null) return false;
        if (!state.isAtWar(myRace, theirRace)) return false;
        // B5-0661: the surrendering player must have an ambassador in play to
        // place in asylum (rulebook :819).
        if (p.getAmbassador() == null) return false;
        return true;
    }

    /**
     * B5-0661: execute unconditional surrender.
     * (1) Mark the surrendering player as surrendered (out of the game).
     * (2) Grant the target 3 influence (rulebook :817: "gains 3 influence").
     * (3) Create an asylum copy of the surrendering player's ambassador as a
     *     supporting character on the target's side, starting Clean (no
     *     aftermath/enhancement attachments carried over — the copy is a fresh
     *     card with no bonus registry) and refused elevation to the Inner
     *     Circle (rulebook :819).
     * Distinct from forfeiture: this does NOT discard the ambassador or set
     * hasForfeited, and the +3 influence grants to the OPPONENT, not discards
     * from the surrendering player.
     */
    public void executeSurrender(Player p, Player target, GameState state) {
        // (1) Mark surrendered
        p.setHasSurrendered(true);
        state.markSurrendered(p);
        state.log(p.getName() + " unconditionally surrenders to "
                  + target.getName() + ".");

        // (2) Grant 3 influence to the target
        target.gainInfluence(3);
        state.log(target.getName() + " gains 3 influence from the surrender ("
                  + target.getInfluence() + " total).");

        // (3) Asylum copy of the ambassador
        CharacterCard originalAmb = p.getAmbassador();
        if (originalAmb != null) {
            // Fresh copy with same printed stats, NOT an ambassador, in asylum
            CharacterCard asylumCopy = originalAmb.createAsylumCopy(
                    "asylum_" + p.getName() + "_" + originalAmb.getId());
            asylumCopy.setOwner(target);
            asylumCopy.heal(); // start Clean — no damage, no aftermaths, no enhancements
            asylumCopy.rotate(); // B5-0661 repair: enters as a retired supporting character
            target.placeInSupportingRole(asylumCopy);
            target.addAsylumCard(asylumCopy);
            state.log(target.getName() + " places an asylum copy of " + p.getName()
                      + "'s ambassador (" + originalAmb.getTitle()
                      + ") as a supporting character (Clean, asylum — may not "
                      + "be elevated to the Inner Circle).");
        }
    }

    // ── B5-1979: voluntary forfeit ───────────────────────────────────────────

    /**
     * B5-1979: returns true when p may voluntarily forfeit right now.
     *
     * Legality, in the order tested:
     *  - the phase is ACTION. This is the row's gate: forfeiting is a choice
     *    made mid-turn, not a draw-round negotiation (that is SURRENDER, which
     *    canSurrender gates on GamePhase.DRAW) and not a consequence of the
     *    draw (that is the involuntary path in Player.drawCards).
     *  - the game is not already over.
     *  - p is still playing (neither forfeited nor surrendered), read through
     *    the consolidated GameState.isPlayerActive so this gate and the
     *    B5-1825 war-conflict gate share one definition of "still playing".
     *  - at least one OTHER player is still active. A forfeit that leaves no
     *    opponent standing has no victory to award; checkVictory would return
     *    null and the forfeiting player would simply exit with the game
     *    undecided, which is not the "immediate loss, victory to the opponent"
     *    contract this row states.
     */
    public boolean canForfeit(Player p, GameState state) {
        if (p == null || state == null) return false;
        if (state.getPhase() != GamePhase.ACTION) return false;
        if (state.isGameOver()) return false;
        if (!state.isPlayerActive(p)) return false;
        for (Player q : state.getPlayers()) {
            if (q != p && state.isPlayerActive(q)) return true;
        }
        return false;
    }

    /**
     * B5-1979: execute a voluntary forfeit.
     *
     * (1) Mark the player forfeited, which is what every existing
     *     hasForfeited() reader already honours, so this needs no new state:
     *     isPlayerActive, activePlayersCount, checkVictory's last-standing
     *     check and the station/tiebreak win conditions all exclude the player
     *     from here on (RulesEngine.java:682, :698, :774, :861, :877).
     * (2) Log the forfeit. The player also picks up their cards and leaves:
     *     the ambassador is discarded to the discard pile rather than retained,
     *     which is what distinguishes a forfeit from an unconditional
     *     surrender (B5-0661), where the ambassador survives and an asylum
     *     copy of it is handed to the winner (rulebook :819).
     * (3) Award victory, but ONLY when this forfeit actually left a sole
     *     survivor. In a two-player game that is the opponent. In a game with
     *     three or more players still standing, the forfeit removes one player
     *     and the game continues -- awarding a win here would hand victory to
     *     every remaining player and end a game the row does not say is over.
     *     checkVictory is the single authority for "is somebody winning now"
     *     and is reused rather than reimplemented, so this path can never
     *     disagree with the end-of-action check at GameController.java:167.
     *
     * Returns the winner if the forfeit ended the game, else null.
     */
    public Player executeForfeit(Player p, GameState state) {
        p.setHasForfeited(true);
        CharacterCard amb = p.getAmbassador();
        if (amb != null) {
            p.getInnerCircle().remove(amb);
            Deck deck = p.getDeck();
            if (deck != null) deck.discard(amb);
            p.setAmbassador(null);
            state.log(p.getName() + " picks up their cards; the ambassador is lost.");
        }
        state.log(p.getName() + " forfeits the game.");
        Player winner = checkVictory(state);
        if (winner != null) {
            state.log("Forfeit leaves " + winner.getName() + " the sole survivor.");
        }
        return winner;
    }

    // ── Legality checks ──────────────────────────────────────────────────────

    public boolean canInitiateConflict(Player p, ConflictCard c, GameState state) {
        return canInitiateConflict(p, c, null, state);
    }

    /** Public reason surface for the one-conflict-per-faction-per-turn cap
     *  (rulebook III :366-:378, Section 5 of the formal game-loop report).
     *  Routine observation only: named values used for display are data, not
     *  gate logic, so this method carries no game-state mutations and only
     *  reads the same predicates the engine already applies. */
    public String getConflictInitiationReason(Player p, ConflictCard c,
                                              GameState state) {
        if (p == null || c == null || state == null) {
            return "cannot compute reason: missing player, card, or game state";
        }
        if (p.isPassed()) {
            return "your turn is already spent — pass to use your next action";
        }
        if (p.getActionsLeft() <= 0) {
            return "no actions left this turn";
        }
        if (state.hasInitiatedConflictThisTurn(p)) {
            return "you have already initiated one conflict this turn";
        }
        if (!p.getHand().contains(c)) {
            return c.getTitle() + " is no longer in your hand";
        }
        if (!c.getLoyalty().isPlayableBy(p.getRace())) {
            return "a race-restricted card is playable only by its own faction";
        }
        Participation part = c.getParticipation();
        if (part != null && part.isRequiresTarget() && state.getActiveConflict() != null) {
            return "this conflict requires a declared target";
        }
        if (state.getActiveConflict() != null
                && state.getActiveConflict().getInitiator() == p) {
            return "your faction has already declared a conflict this round";
        }
        return null;
    }

    /** Shared reason-string builder for the shareable support/oppose
     *  predicates (B5-4172 item 1), used by the join, attack, support and
     *  oppose controls. Returns null when the predicate is true and the
     *  control may fire. */
    public String getJoinReason(Player p, Conflict conflict) {
        if (p == null || conflict == null) {
            return "cannot compute reason: missing player or conflict";
        }
        if (p.isPassed()) {
            return "your turn is already spent — pass to use your next action";
        }
        if (p.getActionsLeft() <= 0) {
            return "no actions left this turn";
        }
        if (conflict.isResolved()) {
            return "this conflict is already resolved";
        }
        if (conflict.getParticipants().contains(p)) {
            return "you have already joined this conflict";
        }
        return null;
    }

    /** Shared reason-string builder for the attack predicate
     *  (B5-4172 item 5), used by the attack control only. */
    public String getAttackReason(Player p, Card attacker, Card target,
                                  Conflict conflict, GameState state) {
        if (p == null || attacker == null || target == null
                || conflict == null || state == null) {
            return "cannot compute reason: missing player, attacker, target, conflict or game state";
        }
        if (conflict.isResolved()) {
            return "this conflict is already resolved";
        }
        if (!conflict.getParticipants().contains(p)) {
            return "you are not a participant in this conflict";
        }
        if (p.isPassed()) {
            return "your turn is already spent — pass to use your next action";
        }
        if (p.getActionsLeft() <= 0) {
            return "no actions left this turn";
        }
        if (!p.controlsCard(attacker) || attacker == target) {
            return "the selected card is not yours or is the target itself";
        }
        if (attacker.isRotated() || attacker.isFaceDown()
                || !attacker.canActAfterNeutralization()) {
            return "the attacker is rotated, face-down, or still under its "
                    + "neutralization lock";
        }
        if (conflict.isParticipantCard(attacker)) {
            return "that card is already committed as a participant";
        }
        if (!conflict.isParticipantCard(target)
                || conflict.isLeaderOfParticipantFleet(target)) {
            return "that target is not a committed participant, a leader, or is "
                    + "not an attackable faction";
        }
        if (target.isNeutralized() || target.isFaceDown()) {
            return "the target is neutralized and cannot be attacked";
        }
        Player targetOwner = ownerOfCard(target, conflict);
        if (targetOwner == null || targetOwner.getRace() == p.getRace()) {
            return "the target belongs to your own faction";
        }
        if (attacker.getPrimaryStatValue(conflict.getConflictType()) <= 0) {
            return "the attacker has no " + conflict.getConflictType()
                    + " ability left to spend";
        }
        if (!canParticipateInConflict(attacker)) {
            return "a group has no conflict ability and cannot be the attacker";
        }
        if (!conflict.canCommitCard(p, attacker)) {
            return "your faction is not allowed to commit that card to this conflict";
        }
        return null;
    }

    /** Shared damage-preview builder for the attack predicate. Two reads of
     *  the same live board — the only one that is honest — the first being
     *  the engine's own applyDamage, the second being the card model's own
     *  attacker getAttackDamage() that each side would pay. */
    public String buildAttackPreview(Player p, Card attacker, Card target,
                                     Conflict conflict, GameState state) {
        if (p == null || attacker == null || target == null
                || conflict == null || state == null) {
            return null;
        }
        if (conflict.isResolved()) {
            return null;
        }
        if (!conflict.getParticipants().contains(p)) {
            return null;
        }
        if (attacker.isRotated() || attacker.isFaceDown()
                || !attacker.canActAfterNeutralization()) {
            return null;
        }
        if (!conflict.isParticipantCard(target)
                || conflict.isLeaderOfParticipantFleet(target)) {
            return null;
        }
        if (target.isNeutralized() || target.isFaceDown()) {
            return null;
        }
        if (!conflict.canCommitCard(p, attacker)) {
            return null;
        }
        int attackDamage = attacker.getAttackDamage(conflict.getConflictType());
        int returnDamage = target.getAttackDamage(conflict.getConflictType());
        return attacker.getTitle() + " deals " + attackDamage
                + " (" + attacker.getPrimaryStatValue(conflict.getConflictType())
                + " ability + " + (2 * attacker.getStrifeMarks()) + " Strife) while "
                + target.getTitle() + " returns " + returnDamage
                + " (" + target.getPrimaryStatValue(conflict.getConflictType())
                + " ability + " + (2 * target.getStrifeMarks()) + " Strife).";
    }

    /** Shared aftermath-window reason builder for the engine's
     *  resolveActiveConflict log (B5-4172 item 2). Arbitrarily returns the
     *  named outcome and scope only — no state mutation: the log line is the
     *  artifact, and the same combination of booleans the engine uses sits
     *  right there at resolve time. */
    public String getAftermathWindowReason(Conflict conflict, Player winner) {
        if (conflict == null) {
            return null;
        }
        if (conflict.isResolved()) {
            return "conflict already resolved";
        }
        if (winner == null) {
            return "no winner recorded for this conflict";
        }
        boolean initiatorWon = initiatorWon(conflict, winner);
        String outcome = initiatorWon ? "won" : "lost";
        char scopeLetter = initiatorWon ? 'W' : 'L';
        return "aftermath window: initiator " + outcome
                + " the conflict; participant scope applies to any "
                + scopeLetter + "-type aftermath card.";
    }

    /**
     * B5-0336 overload: initiation with a declared target. Conflicts whose
     * participation carries requiresTarget:true are ILLEGAL to initiate
     * without one (proposal §3.2; rulebook Conflict round step 2, §III).
     * The pre-B5-0336 3-arg form delegates with target null, which keeps
     * every existing caller and test behaving exactly as before.
     */
    public boolean canInitiateConflict(Player p, ConflictCard c, Player target,
                                       GameState state) {
        if (p.isPassed()) return false;
        if (p.getActionsLeft() <= 0) return false;
        if (state.hasInitiatedConflictThisTurn(p)) return false;   // B5-0302
        if (!p.getHand().contains(c)) return false;
        if (!c.getLoyalty().isPlayableBy(p.getRace())) return false;
        Participation part = c.getParticipation();
        if (part != null && part.isRequiresTarget() && target == null) return false;
        return true;
    }

    public boolean canPlayCard(Player p, Card c) {
        if (!p.getHand().contains(c)) return false;
        // B5-1038: affordability gate — the generic play path now charges
        // card cost, so a card the player cannot afford is not playable.
        if (p.getAppliedPool() < c.getCost()) return false;
        return c.getLoyalty().isPlayableBy(p.getRace());
    }

    /**
     * B5-0677 (rulebook :1034, B5-0667 proposal §3.4): the Negative Power
     * target gate. A card that "refers to only counting influence as power"
     * cannot affect a player whose Power is lower than his Influence.
     * Evaluated ONCE, at effect-application time, as a target gate beside the
     * other target-legality gates — never re-checkable after the fact (the
     * rule protects a player from being targeted, not from having been
     * affected). With no POWER-tagged bonus in play, Power equals Influence
     * and the protection can never fire (the B5-0667 measured vacuity); it
     * becomes exercisable exactly when a source pushes Power below Influence.
     */
    public boolean canAffectTarget(Card source, Player target) {
        if (source == null || target == null) return true;
        return target.getPower() >= target.getInfluence();
    }

    /** B5-0365: contingencies may only attach to a valid in-play host controlled by the player. */
    public boolean canPlayContingency(Player p, ContingencyCard contingency,
                                      Card host) {
        if (p == null || contingency == null || host == null
                || p.isPassed() || p.getActionsLeft() <= 0
                || !p.getHand().contains(contingency)
                || !contingency.getLoyalty().isPlayableBy(p.getRace())
                || !contingency.canTarget(host)) return false;
        return controlsInPlay(p, host);
    }

    private boolean controlsInPlay(Player p, Card host) {
        if (p.getAmbassador() == host || p.getInnerCircle().contains(host)
                || p.getSupportingRole().contains(host) || p.getFleets().contains(host)
                || p.getLocations().contains(host) || p.getGroups().contains(host)
                || p.getEnhancements().contains(host) || p.getAgenda() == host) return true;
        return false;
    }

    /** Reveal trigger surface: only the player who placed it may reveal it. */
    public boolean canRevealContingency(Player p, ContingencyCard contingency) {
        return p != null && contingency != null && contingency.getPlayedBy() == p
                && !contingency.isRevealed() && contingency.getPlacedUnder() != null;
    }

    public boolean initiatorWon(Conflict resolved, Player winner) {
        return resolved.getInitiator() == winner;
    }

    public boolean canPlayAftermath(Player p, AftermathCard a,
                                    Conflict resolved, boolean initiatorWon) {
        // Pre-B5-0338 form: self-target (the engine played aftermaths on the
        // playing player himself) and no D4 registry check. Kept as a
        // delegate so every existing caller and test behaves exactly as
        // before; the live play site uses the 6-arg form.
        return canPlayAftermath(p, a, resolved, initiatorWon, p, null);
    }

    /**
     * B5-0338: aftermath play with an explicit target. Play conditions
     * (Won/Lost/Participant, conflict type) are still evaluated for the
     * PLAYING player; the rulebook's target rule (audit D2) is evaluated
     * for the target — Aftermath Cards / Participant: aftermaths "may
     * normally be played only upon the faction that initiated the just
     * resolved conflict... if 'Participant' is one of the Play Conditions,
     * then appropriate aftermath cards may target cards in play for any
     * faction that either Supported, Opposed or Attacked". The engine
     * represents Supported/Opposed/Attacked as the resolved conflict's
     * participants ("Attacked" has no distinct model representation —
     * recorded). D4: one of each named aftermath per target, enforced via
     * the GameState registry when a state is supplied.
     */
    public boolean canPlayAftermath(Player p, AftermathCard a, Conflict resolved,
                                    boolean initiatorWon, Player target,
                                    GameState state) {
        if (!p.getHand().contains(a)) return false;
        // B5-1045: de_agenda_total_war's deluxe-only restriction — its owner
        // may not play Diplomacy Aftermath cards. Checked before the general
        // play conditions so the refusal is the agenda's and not the trigger's.
        if (CardEffects.isBannedByAgenda(p, a)) return false;
        boolean participated = resolved.getParticipants().contains(p);
        if (!a.isEligible(initiatorWon, participated, resolved.getConflictType()))
            return false;
        if (target == null) return false;
        if (!a.isParticipantCondition()) {
            if (target != resolved.getInitiator()) return false;   // D2 normal rule
        } else if (!resolved.getParticipants().contains(target)) {
            return false;   // D2 Participant: supported/opposed/attacked only
        }
        if (state != null && !state.canAttachAftermath(a, target)) return false;   // D4
        return true;
    }

    // ── B5-1994: legal-target validation ─────────────────────────────────────

    /** B5-1994: legal-target validation for card play. */
    public boolean isLegalTarget(Player p, Card card, Card target) {
        if (p == null || card == null) return false;
        // Rivalry probe: requires two IC characters
        if ("Rivalry".equalsIgnoreCase(card.getTitle())) {
            int icCount = 0;
            for (CharacterCard ch : p.getInnerCircle()) {
                if (ch != p.getAmbassador()) {
                    icCount++;
                }
            }
            return icCount >= 2;
        }
        return true;
    }

    // ── Join conflict action (B5-0322) ─────────────────────────────────────────

    /** Returns true when p may join the active conflict on the given side:
     *  p has not passed, there is an active conflict, p is not already a
     *  participant, and p's faction may play the conflict card. If
     *  requireAction is true (action-round joins), p must also have an action
     *  remaining. Conflict-resolution joins call with false so the human can
     *  join after the action round without an action token. */
    public boolean canJoinConflict(Player p, Conflict conflict, boolean requireAction) {
        if (p == null) return false;
        if (requireAction) {
            if (p.isPassed()) return false;
            if (p.getActionsLeft() <= 0) return false;
        }
        if (conflict == null) return false;
        if (conflict.isResolved()) return false;
        if (conflict.getParticipants().contains(p)) return false;
        return true;
    }

    /** Overload for action-round joins (requireAction = true). */
    public boolean canJoinConflict(Player p, Conflict conflict) {
        return canJoinConflict(p, conflict, true);
    }

    /** Adds p to the given side of the active conflict, commits p's ambassador
     *  if face-up, and logs the join. The conflict's resolveConflict() handles
     *  ambassador damage based on the final result; this method does not resolve. */
    public void executeJoinConflict(Player p, Conflict conflict, boolean support,
                                    GameState state, boolean requireAction) {
        if (!canJoinConflict(p, conflict, requireAction)) {
            state.log(p.getName() + " cannot join — already joined or no active conflict.");
            return;
        }
        conflict.addParticipant(p, support);
        if (p.getAmbassador() != null && !p.getAmbassador().isFaceDown()) {
            conflict.commitCard(p, p.getAmbassador(), support);
        }
        String side = support ? "support" : "oppose";
        boolean freeParticipant = CardEffects.participantWaiver(conflict)
                == CardEffects.WaiverEffect.FREE_PARTICIPANT;
        if (freeParticipant) {
            // Non-Aligned Support grants one ready Non-Aligned fleet entry;
            // it does not consume influence or rotate the fleet.
            for (FleetCard fleet : p.getFleets()) {
                if (fleet.getLoyalty() == Loyalty.NON_ALIGNED
                        && !fleet.isRotated() && conflict.canCommitCard(p, fleet)) {
                    conflict.commitCard(p, fleet, support);
                    break;
                }
            }
            state.log(p.getName() + " joins the conflict as " + side
                    + " under its free-participant waiver.");
        } else {
            state.log(p.getName() + " joins the conflict as " + side + ".");
        }
    }

    /**
     * An attack uses the same conflict ability as the target. The attacker
     * must be controlled by an existing participant, ready and unneutralized;
     * the target must already participate and belong to another faction.
     * Conflict.canCommitCard is the participation-restriction authority for
     * the newly participating attacker.
     */
    public boolean canAttackConflictParticipant(Player p, Card attacker,
                                                Card target, Conflict conflict) {
        if (p == null || attacker == null || target == null || conflict == null) return false;
        if (conflict.isResolved() || !conflict.getParticipants().contains(p)) return false;
        if (!p.controlsCard(attacker) || attacker == target) return false;
        if (attacker.isRotated() || attacker.isFaceDown()
                || !attacker.canActAfterNeutralization()) return false;
        if (conflict.isParticipantCard(attacker)) return false;
        if (!conflict.isParticipantCard(target) || conflict.isLeaderOfParticipantFleet(target)) return false;
        target.reconcileDamage();
        if (target.isNeutralized() || target.isFaceDown()) return false;
        Player targetOwner = ownerOfCard(target, conflict);
        if (targetOwner == null || targetOwner.getRace() == p.getRace()) return false;
        if (attacker.getPrimaryStatValue(conflict.getConflictType()) <= 0) return false;
        // B5-1999: a group has no ability (:496) and is never a participant
        // (:434/:607), so it can never be the attacking card either. Stated
        // explicitly because the stat gate above would already refuse it (a
        // group reads 0 for every conflict type) — this names the reason at
        // the site that makes it true.
        if (!canParticipateInConflict(attacker)) return false;
        return conflict.canCommitCard(p, attacker);
    }

    /** Executes simultaneous attack damage, then rotates/commits the attacker. */
    public boolean executeAttackConflictParticipant(Player p, Card attacker,
                                                    Card target, Conflict conflict,
                                                    GameState state) {
        if (!canAttackConflictParticipant(p, attacker, target, conflict)) return false;
        ConflictType ability = conflict.getConflictType();
        int attackDamage = attacker.getAttackDamage(ability);
        int returnDamage = target.getAttackDamage(ability);
        // B5-0528: faction-held mines + energy_mines trigger +1 damage on
        // attacking fleets (reactive effect checked at attack-resolution site).
        if (target != null) {
            Player targetOwner = ownerOfCard(target, conflict);
            if (targetOwner != null) {
                for (Card enh : targetOwner.getEnhancements()) {
                    if (enh instanceof EnhancementCard
                            && CardEffects.damageOnAttack(((EnhancementCard) enh).getId())) {
                        returnDamage += 1;
                        break;
                    }
                }
            }
        }
        boolean support = conflict.isSupporting(p);
        if (!conflict.commitCard(p, attacker, support)) return false;
        conflict.markAttackOccurred(); // B5-0376 Phase C: attack resolves → contested
        attacker.rotate();
        // B5-4170 (§11.5 item 5; B5-4162 readout extension): announce the
        // mutual attack damage BEFORE either side applies it, so an attack is
        // not an arbitrary act in engine/headless readouts (the UI damage
        // preview panel is B5-4172's surface). Strife shows its +2-per-mark
        // contribution on each side, matching the B5-4162 post-attack line.
        state.log("attack preview: " + attacker.getTitle() + " deals "
                + attackDamage + " (" + attacker.getPrimaryStatValue(ability)
                + " ability + " + (2 * attacker.getStrifeMarks()) + " Strife); "
                + target.getTitle() + " returns " + returnDamage + " ("
                + target.getPrimaryStatValue(ability) + " ability + "
                + (2 * target.getStrifeMarks()) + " Strife)");
        int targetOverflow = target.applyDamage(attackDamage);
        int attackerOverflow = attacker.applyDamage(returnDamage);
        // Attack both sides: attack side (ability, Strife bonus, resulting damage tokens/overflow) and return side (return damage, post-damage tokens/overflow, severe overflow). Rules unchanged; smoke + suite green.
        state.log(attacker.getTitle() + " attacks " + target.getTitle()
                + " using " + ability + " (" + attackDamage + " damage; "
                + returnDamage + " damage returned; attacker: ability " + ability + ", strife " + attacker.getStrifeMarks() + " => +" + (2 * attacker.getStrifeMarks()) + " damage, " + attacker.getDamageTokens() + " damage tokens, severe overflow " + attackerOverflow + "); target: " + target.getTitle() + " post-damage tokens " + target.getDamageTokens() + ", severe overflow " + targetOverflow + ")." );
        if (target.isNeutralized()) state.log(target.getTitle() + " is neutralized"
                + (targetOverflow > 0 ? " (" + targetOverflow + " severe damage)." : "."));
        if (attacker.isNeutralized()) state.log(attacker.getTitle() + " is neutralized"
                + (attackerOverflow > 0 ? " (" + attackerOverflow + " severe damage)." : "."));
        return true;
    }

    private Player ownerOfCard(Card card, Conflict conflict) {
        for (Player candidate : conflict.getParticipants())
            if (candidate.controlsCard(card)) return candidate;
        return null;
    }

    // ── Fleet leadership (B5-0337; rulebook Action Details, Support or
    //    Oppose / audit D5) ─────────────────────────────────────────────────

    /**
     * True when character ch may rotate to lead fleet fl for player p
     * (rulebook: "one character per fleet may rotate to add his Leadership
     * Ability to the Military Ability of any fleet"): ch is p's ready
     * (unrotated, face-up) Inner Circle or supporting-role character, fl is
     * p's unrotated fleet, and fl has no leader yet. The check lives here;
     * the player-facing ACTION plumbing is the lead-fleet slice of B5-0345.
     */
    public boolean canLeadFleet(Player p, CharacterCard ch, FleetCard fl) {
        if (p == null || ch == null || fl == null) return false;
        if (!p.getInnerCircle().contains(ch)
                && !p.getSupportingRole().contains(ch)) return false;
        if (!p.getFleets().contains(fl)) return false;
        if (ch.isRotated() || ch.isFaceDown() || !ch.canActAfterNeutralization()) return false;
        if (fl.isRotated() || !fl.canActAfterNeutralization()) return false;
        if (fl.getLeader() != null) return false;   // one leader per fleet
        return true;
    }

    /**
     * Rotates ch to lead fl, adding his Leadership to the fleet's effective
     * Military until startRound clears the relation. Leading is a rotation,
     * not a zone change and not an influence spend.
     */
    public void executeLeadFleet(Player p, CharacterCard ch, FleetCard fl,
                                 GameState state) {
        if (!canLeadFleet(p, ch, fl)) {
            state.log(p.getName() + " cannot lead "
                      + (fl == null ? "(null fleet)" : fl.getTitle()) + " with "
                      + (ch == null ? "(null character)" : ch.getTitle()) + ".");
            return;
        }
        ch.rotate();
        fl.setLeader(ch);
        state.log(ch.getTitle() + " leads " + fl.getTitle()
                  + " (Military now " + fl.getEffectiveMilitary() + ").");
    }

    // ── Ambassador's assistant (B5-0339; rulebook §IV) ─────────────

    /**
     * True when ch may use an assistant ability for p's ambassador
     * (rulebook §IV "Your Ambassador's Assistant": rotate the assistant to
     * give the ambassador +1 Diplomacy/Intrigue/Leadership while he remains
     * rotated, or let the ambassador sponsor 1 influence cheaper later that
     * turn; the assistant must be "ready and unneutralized", and he is
     * "attached to a specific ambassador"). The model has no card-ownership
     * graph, so a player's supporting-role assistant assists that player's
     * own ambassador (recorded in DECISIONS).
     */
    public boolean canUseAssistant(Player p, CharacterCard ch, CharacterCard amb) {
        if (p == null || ch == null || amb == null) return false;
        if (!p.getSupportingRole().contains(ch)) return false;
        if (ch.isRotated() || ch.isFaceDown() || !ch.canActAfterNeutralization()) return false;
        if (p.getAmbassador() == null || p.getAmbassador() != amb) return false;
        return true;
    }

    /**
     * Rotates the assistant to boost the ambassador: +1 Diplomacy, Intrigue
     * and Leadership computed by CharacterCard.getPrimaryStatValue while the
     * bonus flag is up — it expires with the round (startRound), i.e. while
     * the assistant remains rotated. The flag is binary: a second assisting
     * assistant does not stack (rulebook silent on stacking; conservative).
     */
    public void executeAssistantAbilityBoost(Player p, CharacterCard ch,
                                             GameState state) {
        CharacterCard amb = (p == null) ? null : p.getAmbassador();
        if (!canUseAssistant(p, ch, amb)) {
            state.log(p.getName() + " cannot use "
                      + (ch == null ? "(null)" : ch.getTitle()) + " as an assistant.");
            return;
        }
        ch.rotate();
        amb.setAssistantBonus(true);
        state.log(ch.getTitle() + " assists " + amb.getTitle()
                  + " (+1 Diplomacy/Intrigue/Leadership while rotated).");
    }

    /**
     * Rotates the assistant so the ambassador may sponsor 1 influence
     * cheaper later this turn (§IV); the discount is consumed by the first
     * recruit and expires with the turn.
     */
    public void executeAssistantSponsorDiscount(Player p, CharacterCard ch,
                                                GameState state) {
        CharacterCard amb = (p == null) ? null : p.getAmbassador();
        if (!canUseAssistant(p, ch, amb)) {
            state.log(p.getName() + " cannot use "
                      + (ch == null ? "(null)" : ch.getTitle()) + " as an assistant.");
            return;
        }
        ch.rotate();
        p.grantSponsorDiscount(1);
        state.log(ch.getTitle() + " assists " + amb.getTitle()
                  + " (sponsor 1 influence cheaper this turn).");
    }

    // ── Sustained actions (B5-1997; rulebook III "Sustained Actions") ─────────

    /**
     * Checks whether a rotate-to-boost action can be sustained.
     * Per rulebook III: "To be sustainable, an action must have no other cost
     * besides rotating a card - there should be no influence cost, no marks
     * need to be purged, etc. In addition, the action should provide a bonus
     * to a card's ability. However, any effect which states that it lasts
     * while a card remains rotated can be sustained."
     */
    public boolean canSustainAction(Player p, Card sourceCard, Card targetCard,
                                    SustainedActionType type) {
        if (p == null || sourceCard == null || targetCard == null) return false;
        // Source must be controlled by player
        boolean controlsSource = false;
        if (sourceCard instanceof CharacterCard) {
            CharacterCard ch = (CharacterCard) sourceCard;
            controlsSource = p.getInnerCircle().contains(ch) || p.getSupportingRole().contains(ch)
                    || (p.getAmbassador() != null && p.getAmbassador().equals(ch));
        } else if (sourceCard instanceof FleetCard) {
            controlsSource = p.getFleets().contains(sourceCard);
        } else if (sourceCard instanceof GroupCard) {
            controlsSource = p.getGroups().contains(sourceCard);
        }
        if (!controlsSource) return false;
        // Source must be ready and able to act
        if (sourceCard.isRotated() || sourceCard.isFaceDown()) return false;
        if (sourceCard instanceof CharacterCard) {
            CharacterCard ch = (CharacterCard) sourceCard;
            if (!ch.canActAfterNeutralization()) return false;
        }
        // Target must be controlled by player (for bonuses to own cards)
        boolean controlsTarget = false;
        if (targetCard instanceof CharacterCard) {
            controlsTarget = p.getInnerCircle().contains(targetCard)
                    || p.getSupportingRole().contains(targetCard)
                    || (p.getAmbassador() != null && p.getAmbassador().equals(targetCard));
        } else if (targetCard instanceof FleetCard) {
            controlsTarget = p.getFleets().contains(targetCard);
        } else if (targetCard instanceof GroupCard) {
            controlsTarget = p.getGroups().contains(targetCard);
        }
        if (!controlsTarget) return false;
        // Target must be face-up and not neutralized (for it to receive bonus)
        if (targetCard.isFaceDown()) return false;
        // Type-specific checks
        if (type == SustainedActionType.LEADERSHIP_BOOST) {
            if (!(sourceCard instanceof CharacterCard)) return false;
            if (!(targetCard instanceof FleetCard)) return false;
            FleetCard fl = (FleetCard) targetCard;
            if (fl.getLeader() != null) return false;
            CharacterCard ch = (CharacterCard) sourceCard;
            if (ch.getLeadership() <= 0) return false;
        } else if (type == SustainedActionType.ASSISTANT_BONUS) {
            if (!(sourceCard instanceof CharacterCard)) return false;
            if (!(targetCard instanceof CharacterCard)) return false;
            CharacterCard assistant = (CharacterCard) sourceCard;
            CharacterCard ambassador = (CharacterCard) targetCard;
            if (p.getAmbassador() == null || !p.getAmbassador().equals(ambassador)) return false;
            if (!p.getSupportingRole().contains(assistant)) return false;
        }
        return true;
    }

    /**
     * Begins a sustained action by rotating the source card and registering
     * the sustained action in the game state.
     */
    public void beginSustainedAction(Player p, Card sourceCard, Card targetCard,
                                     SustainedActionType type, GameState state) {
        if (!canSustainAction(p, sourceCard, targetCard, type)) {
            state.log(p.getName() + " cannot begin sustained action: "
                      + (sourceCard == null ? "(null)" : sourceCard.getTitle())
                      + " -> " + (targetCard == null ? "(null)" : targetCard.getTitle()));
            return;
        }
        sourceCard.rotate();
        SustainedAction action = new SustainedAction(sourceCard, targetCard, type,
                p, state.getRoundNumber());
        if (state.registerSustainedAction(action)) {
            // Apply the immediate effect based on type
            if (type == SustainedActionType.LEADERSHIP_BOOST) {
                FleetCard fl = (FleetCard) targetCard;
                CharacterCard ch = (CharacterCard) sourceCard;
                fl.setLeader(ch);
                state.log(ch.getTitle() + " leads " + fl.getTitle()
                          + " (sustained: Military now " + fl.getEffectiveMilitary() + ").");
            } else if (type == SustainedActionType.ASSISTANT_BONUS) {
                CharacterCard amb = (CharacterCard) targetCard;
                amb.setAssistantBonus(true);
                state.log(sourceCard.getTitle() + " assists " + amb.getTitle()
                          + " (sustained: +1 Diplomacy/Intrigue/Leadership while rotated).");
            } else {
                state.log(sourceCard.getTitle() + " begins sustained action on "
                          + targetCard.getTitle() + " (" + type + ").");
            }
        } else {
            // Rollback rotation if registration failed
            sourceCard.unrotate();
            state.log(p.getName() + " tried to sustain but source already sustaining.");
        }
    }

    /**
     * Ends a sustained action explicitly. The source card is unrotated and
     * the sustained action is removed from the registry.
     */
    public void endSustainedAction(Player p, Card sourceCard, GameState state) {
        if (sourceCard == null) return;
        SustainedAction action = state.endSustainedAction(sourceCard);
        if (action != null) {
            sourceCard.unrotate();
            // Clear the effect based on type
            if (action.getType() == SustainedActionType.LEADERSHIP_BOOST) {
                if (action.getTargetCard() instanceof FleetCard) {
                    FleetCard fl = (FleetCard) action.getTargetCard();
                    fl.setLeader(null);
                }
            } else if (action.getType() == SustainedActionType.ASSISTANT_BONUS) {
                if (action.getTargetCard() instanceof CharacterCard) {
                    CharacterCard amb = (CharacterCard) action.getTargetCard();
                    amb.setAssistantBonus(false);
                }
            }
            state.log(p.getName() + " ends sustained action: "
                      + sourceCard.getTitle() + " no longer sustaining.");
        }
    }

    /** Returns true if the given source card is currently sustaining an action. */
    public boolean isSustaining(Card sourceCard, GameState state) {
        return state.isSustaining(sourceCard);
    }

    /** Clears all sustained actions for a player (used at game end). */
    public void clearPlayerSustainedActions(Player p, GameState state) {
        // Find all sustained actions controlled by this player
        java.util.List<String> toRemove = new java.util.ArrayList<String>();
        if (p.getAmbassador() != null && state.isSustaining(p.getAmbassador())) {
            toRemove.add(p.getAmbassador().getId());
        }
        for (CharacterCard ch : p.getInnerCircle()) {
            if (state.isSustaining(ch)) toRemove.add(ch.getId());
        }
        for (CharacterCard ch : p.getSupportingRole()) {
            if (state.isSustaining(ch)) toRemove.add(ch.getId());
        }
        for (FleetCard fl : p.getFleets()) {
            if (state.isSustaining(fl)) toRemove.add(fl.getId());
        }
        for (GroupCard gr : p.getGroups()) {
            if (state.isSustaining(gr)) toRemove.add(gr.getId());
        }
        for (String id : toRemove) {
            Card c = findCardById(p, id);
            if (c != null) endSustainedAction(p, c, state);
        }
    }

    private Card findCardById(Player p, String id) {
        if (p.getAmbassador() != null && id.equals(p.getAmbassador().getId())) return p.getAmbassador();
        for (CharacterCard ch : p.getInnerCircle()) if (id.equals(ch.getId())) return ch;
        for (CharacterCard ch : p.getSupportingRole()) if (id.equals(ch.getId())) return ch;
        for (FleetCard fl : p.getFleets()) if (id.equals(fl.getId())) return fl;
        for (GroupCard gr : p.getGroups()) if (id.equals(gr.getId())) return gr;
        return null;
    }

    // ── Rotate-for-effect (B5-0366; B5-0345 Tier-2 #5, rulebook §IV) ────────────

    /**
     * B5-0366: the two player-chosen assistant effects, promoted from the
     * wired B5-0339 special cases to a generic rotate-for-effect vocabulary
     * (the kind enum lives on GameAction so no model→engine import is
     * needed). Both ride the existing canUseAssistant gate (ready,
     * unneutralized, supporting, own ambassador) and expire at the round
     * boundary (B5-0339 startRound). Future Vir-style rotate effects reuse
     * this vocabulary with new execute branches; they do NOT touch the
     * B5-0339 flags.
     */

    /**
     * True when ch may rotate for the named effect now: the effect kind is
     * known and the B5-0339 readiness gate passes (ready supporting assistant,
     * own ambassador). One gate for every kind today — per-kind legality
     * diverges here, not in the executors, when future effects land.
     */
    public boolean canUseRotateEffect(Player p, CharacterCard ch,
                                      CharacterCard amb,
                                      GameAction.RotateEffectKind kind) {
        if (kind == null) return false;
        return canUseAssistant(p, ch, amb);
    }

    /**
     * Rotates the assistant for the named effect. Refusals log and leave
     * state unchanged (same contract as the B5-0339 executors they delegate
     * to — the rotation IS the cost, consumed even on an already-flagged
     * ambassador for the boost, exactly as B5-0339 behaved).
     */
    public void executeRotateEffect(Player p, CharacterCard ch,
                                    GameAction.RotateEffectKind kind,
                                    GameState state) {
        if (kind == null) {
            state.log(p.getName() + " cannot use a rotate effect (unknown kind).");
            return;
        }
        if (kind == GameAction.RotateEffectKind.USE_ABILITY_BOOST) {
            executeAssistantAbilityBoost(p, ch, state);
        } else {
            executeAssistantSponsorDiscount(p, ch, state);
        }
    }

    // ── B5-0336: mandatory participation enforcement ──────────────────────

    /**
     * Enforces the mandatory-commit dimensions of a conflict's participation
     * restriction BEFORE resolution (proposal §3.2): mustCommitAmbassador,
     * allPlayersMustCommit, mustTakeSide. Players not yet participating are
     * pulled in on the opposition side — the same side AI joiners take
     * (B5-0309 join path) — and mandated ambassadors commit via
     * Conflict.commitMandatory, which bypasses card-kind/quota filters
     * because the mandate itself names the commitment. Eligibility still
     * respects the "players" gate and forfeits. Null/absent participation
     * is a no-op (§3.4 open default).
     */
    public void enforceMandatoryParticipation(Conflict conflict, GameState state) {
        Participation part = conflict.getCard().getParticipation();
        if (part == null || !part.isRestricted()) return;

        List<Player> eligible = new ArrayList<Player>();
        for (Player p : state.getPlayers()) {
            if (p.hasForfeited()) continue;
            if (conflict.canJoinConflict(p)) eligible.add(p);
        }

        if (part.isMustTakeSide() || part.hasAllPlayersMustCommit()
                || part.isMustCommitAmbassador()) {
            for (Player p : eligible) {
                if (p == conflict.getInitiator()) continue;   // already participates
                if (conflict.getParticipants().contains(p)) continue;
                if (conflict.addParticipant(p, false)) {
                    state.log(p.getName() + " is compelled to participate in "
                            + conflict.getCard().getTitle() + " (opposes).");
                }
            }
        }

        boolean commitMandate = part.isMustCommitAmbassador()
                || part.hasAllPlayersMustCommit() || part.isMustTakeSide();
        if (commitMandate) {
            for (Player p : conflict.getParticipants()) {
                Card amb = p.getAmbassador();
                if (amb == null || amb.isFaceDown()) continue;
                if (conflict.getCommittedCards(p).contains(amb)) continue;
                if (part.hasAllPlayersMustCommit()
                        && part.getAllPlayersCardType() != CardType.CHARACTER) {
                    // The mandate names a kind the engine cannot auto-commit
                    // (no card-picking semantics yet) — loud, never silent.
                    System.err.println("B5-0336: allPlayersMustCommit "
                            + part.getAllPlayersCardType() + " not auto-satisfiable for "
                            + conflict.getCard().getTitle());
                    continue;
                }
                if (conflict.commitMandatory(p, amb, conflict.isSupporting(p))) {
                    state.log(p.getName() + " commits their Ambassador to "
                            + conflict.getCard().getTitle() + " (mandatory).");
                }
            }
            if (part.hasAllPlayersMustCommit() && part.getAllPlayersCount() > 1) {
                System.err.println("B5-0336: allPlayersMustCommit count "
                        + part.getAllPlayersCount() + " exceeds engine auto-commit"
                        + " (ambassador only) for " + conflict.getCard().getTitle());
            }
        }
    }

    // ── Mercenary control (B5-0395; rulebook §Mercenaries :735–:741) ────────
    // Bidding spends APPLIED-POOL influence only (applied_pool, per D9 —
    // never the influence Rating). A bid is a turn-ordered ACTION: one action
    // per player per visit, cumulative per player per turn, capped by what the
    // pool holds at the moment of each bid. Control is decided at the
    // MERCENARY phase as the single highest cumulative total — a tie crowns
    // nobody (DECISIONS: conservative default, D12 discipline; the rulebook
    // is silent on ties).

    /** A bid is legal when the card is a mercenary that is offered (i.e. in
     *  play), the amount is positive, and the player's applied pool can cover
     *  it NOW (cumulative bids may drain the pool across the round). */
    public boolean canBidOnMercenary(Player p, Card merc, int amount, GameState state) {
        if (p == null || merc == null || state == null) return false;
        if (amount <= 0) return false;
        if (!merc.isMercenary() || !state.isMercenaryInPlay(merc)) return false;
        return p.getAppliedPool() >= amount;
    }

    /** Applies the bid: records it on merc, then spends `amount` from p's
     *  applied pool. Record-then-spend: canBidOnMercenary has already proven
     *  afford-now and offered/positive, and nothing else can change the pool
     *  between the two calls (single-threaded controller), so a mid-way
     *  failure is impossible by construction — if it ever happens, it is
     *  logged loudly, never silently refunded. */
    public boolean executeBidOnMercenary(Player p, Card merc, int amount, GameState state) {
        if (!canBidOnMercenary(p, merc, amount, state)) return false;
        if (!state.placeMercenaryBid(merc, p, amount)) {
            System.err.println("B5-0395: bid on " + merc.getId() + " could not be "
                    + "recorded despite legality checks — aborting without "
                    + "charging the pool.");
            return false;
        }
        if (!p.applyInfluence(amount)) {   // defensive: pool changed under us
            state.placeMercenaryBidRollback(merc, p, amount);
            return false;
        }
        state.log(p.getName() + " bids " + amount + " influence to control "
                + merc.getTitle() + " (total " + state.getMercenaryBid(merc, p) + ").");
        return true;
    }

    // ── B5-2281: initiative order (rulebook :350-:352) + mercenary tie-break ──
    //
    // Rulebook :739 decides mercenary control by "the faction which applied the
    // most influence during the turn (bids are cumulative)" and says NOTHING
    // about equal totals. B5-0395 resolved that silence conservatively: a tie
    // crowned nobody (the D12 discipline). This block replaces that reading
    // with a tie-break, on the authority of the row, and the change is recorded
    // in docs/DECISIONS.md so it can be reversed in one place.
    //
    // WHY INITIATIVE ORDER IS THE RIGHT KEY rather than an invented one: :352
    // is the repo's canonical statement of seat precedence ("the player with
    // the lowest Influence Rating must act first during each round"), so a
    // tie broken "in initiative order" needs no new rule at all -- it reuses
    // the one the READY round already runs.
    //
    // WHY IT IS FROZEN AT READY AND NOT RECOMPUTED AT THE MERCENARY PHASE:
    // :350-:352 makes "Determine Initiative" STEP 3 of the READY round and says
    // "Initiative order for the turn is now determined". Recomputing it at
    // MERCENARY would let a mid-turn influence GAIN silently reorder the
    // tie-break, so the same bids could resolve two ways depending on when the
    // key was read. The order is therefore frozen in startRound (the READY
    // hook) and held for the turn, which MER-TIE check MERTB-04 pins.
    //
    // DUPLICATION, NAMED NOT HIDDEN: MainWindow.initiativeKeyCompare and
    // StartingGameFlowModel.actsBefore each hold their own copy of the :352
    // chain. This is the third, and it is the only one the ENGINE plays by.
    // Collapsing the three is a ui-scope row, out of this row's fence.
    private List<Player> initiativeOrder;
    private GameState   initiativeState;

    /**
     * Rulebook :352 ordering key: NEGATIVE when {@code a} acts BEFORE {@code b},
     * i.e. when {@code a} has the LOWER initiative and acts first.
     *
     * Influence Rating first. On a tie :352 says the player with the highest
     * Diplomacy on his ambassador "wins (acts last)", so within a tie the
     * LOWER ability acts earlier; the same applies in turn to Intrigue, Psi and
     * then Leadership. Abilities are read through getEffectiveStat, so a
     * rotation/damage/bonus state cannot make the displayed order disagree with
     * the order played by -- the same reading StartingGameFlowModel.ability uses.
     *
     * Returns 0 when the chain is exhausted, which is the signal that :352's
     * final step applies: "If two players are still tied, determine initiative
     * order between them randomly."
     */
    static int compareInitiativeKey(Player a, Player b) {
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

    /** The ambassador ability :352 ranks, 0 when the seat has none or it is
     *  face down (a hidden ambassador has no readable ability to rank). */
    private static int ambassadorAbility(Player p, StatKey stat) {
        if (p == null) return 0;
        CharacterCard amb = p.getAmbassador();
        if (amb == null || amb.isFaceDown()) return 0;
        return amb.getEffectiveStat(stat);
    }

    /**
     * True when {@code a} acts BEFORE {@code b} in rulebook :352 order, i.e.
     * {@code a} holds the lower initiative. Public because the MERCENARY
     * tie-break and its conformance witness both need to ask the question the
     * engine plays by; the sort below is built from it so there is one
     * comparator, not two that can drift.
     */
    public static boolean actsBefore(Player a, Player b) {
        if (a == b) return false;
        int c = compareInitiativeKey(a, b);
        if (c != 0) return c < 0;
        return a.getName().compareTo(b.getName()) < 0;
    }

    /**
     * The seats in the order they act, index 0 acting FIRST (lowest initiative,
     * rulebook :352). Unmodifiable; empty for a null state.
     *
     * The residual :352 step ("determine initiative order between them
     * randomly") is resolved here by seat NAME order rather than a random draw,
     * for two reasons: a random draw would make the mercenary phase
     * irreproducible, and ReplayRecorder/B5-51975 exist to replay a game and
     * compare it against the recorded run. StartingGameFlowModel.actsBefore
     * makes the same substitution for the same reason, so the order this
     * freezes and the order the setup flow displays cannot disagree.
     */
    public List<Player> determineInitiativeOrder(GameState state) {
        List<Player> ordered = new ArrayList<Player>();
        if (state == null) return Collections.unmodifiableList(ordered);
        for (Player p : state.getPlayers()) {
            if (p != null) ordered.add(p);
        }
        Collections.sort(ordered, new Comparator<Player>() {
            @Override public int compare(Player a, Player b) {
                return actsBefore(a, b) ? -1 : (actsBefore(b, a) ? 1 : 0);
            }
        });
        return Collections.unmodifiableList(ordered);
    }

    /** The order frozen for the current turn, or an empty list before any
     *  READY round has run. Never null. */
    public List<Player> getInitiativeOrder() {
        if (initiativeOrder != null && initiativeState != null) return initiativeOrder;
        return Collections.emptyList();
    }

    /** The frozen order for {@code state}, computing it on first use so a
     *  caller that never drove the READY round still gets a DEFINED order
     *  rather than silently resolving every tie by seat order instead. */
    private List<Player> initiativeOrderFor(GameState state) {
        if (state == null) return Collections.emptyList();
        if (initiativeState != state || initiativeOrder == null) {
            initiativeState = state;
            initiativeOrder = determineInitiativeOrder(state);
        }
        return initiativeOrder;
    }

    /**
     * B5-2281: resolve control of every offered mercenary for the MERCENARY
     * phase (rulebook :739). Cumulative per-player bids, the strictly highest
     * total controls, and a tie is BROKEN IN INITIATIVE ORDER (:352) and
     * announced, naming every tied seat and the step that decided it.
     *
     * Two departures from the B5-0395 reading, both deliberate and both
     * recorded in docs/DECISIONS.md:
     * <ul>
     *   <li>a tie no longer crowns nobody;</li>
     *   <li>a forfeited or surrendered seat cannot take control, read through
     *       the consolidated GameState.isPlayerActive so this phase and every
     *       checkVictory path share ONE definition of "still playing"
     *       (B5-1705/B5-1706/B5-1825 precedent). A seat that has left the game
     *       is logged as excluded rather than dropped in silence.</li>
     * </ul>
     * A 0 bid is NOT a bid, so a table where nobody bid produces an empty
     * result rather than a tie between everyone.
     *
     * <p>Returns a fresh LinkedHashMap in offer order; callers may iterate it
     * while the phase runs. This method is the ENGINE's authority for mercenary
     * control. It does not write GameState's mercenaryControllers, because
     * that map has exactly one writer (GameState.resolveMercenaries, a
     * model-scope method this row's engine fence does not reach); see the
     * divergence witness in the MER-TIE conformance section.
     */
    public Map<Card, Player> resolveMercenaryControl(GameState state) {
        Map<Card, Player> resolved = new LinkedHashMap<Card, Player>();
        if (state == null) return resolved;
        List<Player> order = initiativeOrderFor(state);
        for (Card merc : state.getMercenaryOffers()) {
            if (merc == null) continue;
            int best = 0;
            List<Player> tied = new ArrayList<Player>();
            for (Player p : order) {
                if (p == null) continue;
                if (!state.isPlayerActive(p)) {
                    if (state.getMercenaryBid(merc, p) > 0) {
                        state.log("MERCENARY " + merc.getTitle() + ": " + p.getName()
                                + " bid " + state.getMercenaryBid(merc, p)
                                + " but has left the game (forfeited or"
                                + " surrendered), so the bid does not reach"
                                + " control.");
                    }
                    continue;
                }
                int bid = state.getMercenaryBid(merc, p);
                if (bid <= 0) continue;              // a 0 bid is not a bid
                if (bid > best) {
                    best = bid;
                    tied.clear();
                    tied.add(p);
                } else if (bid == best) {
                    tied.add(p);
                }
            }
            if (tied.isEmpty()) continue;            // nobody bid: nobody controls
            Player winner = tied.get(0);             // tied[] is walked in initiative order
            resolved.put(merc, winner);
            if (tied.size() > 1) logMercenaryTie(state, merc, best, tied, winner, order);
        }
        return resolved;
    }

    /**
     * The "loudly" half of B5-2281. A tie resolved by an invented rule is the
     * kind of outcome a player must be able to audit after the fact, so the
     * line names the mercenary, the shared total, EVERY tied seat, the step
     * that separated them, and says so when the :352 chain was exhausted and
     * seat name order decided instead.
     */
    private void logMercenaryTie(GameState state, Card merc, int total,
                                 List<Player> tied, Player winner,
                                 List<Player> order) {
        StringBuilder sb = new StringBuilder();
        sb.append("MERCENARY TIE on ").append(merc.getTitle()).append(" at ")
          .append(total).append(" influence: ");
        for (int i = 0; i < tied.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(tied.get(i).getName());
        }
        sb.append(". Rulebook :739 names no tie-break, so initiative order")
          .append(" (rulebook :352, lowest first) decides: ");
        int rank = order.indexOf(winner);
        sb.append(winner.getName()).append(" acts ")
          .append(rank < 0 ? "first" : ("" + (rank + 1) + " of " + order.size()))
          .append(" and takes control.");
        boolean residual = false;
        for (int i = 0; i < tied.size(); i++) {
            if (tied.get(i) != winner
                    && compareInitiativeKey(tied.get(i), winner) == 0) {
                residual = true;
                break;
            }
        }
        if (residual) {
            sb.append(" The :352 ability chain could not separate them, so seat")
              .append(" name order stood in for its final \"determine initiative")
              .append(" order between them randomly\" step.");
        }
        state.log(sb.toString());
    }

    // ── Round start ──────────────────────────────────────────────────────────

    public void startRound(GameState state) {
        for (Player p : state.getPlayers()) {
            p.resetNeutralizedTurnLocks();
            p.restoreAppliedPool();
            p.resetActions();

            // Unrotate ambassador unless sustaining
            if (p.getAmbassador() != null && !state.isSustaining(p.getAmbassador())) {
                p.getAmbassador().unrotate();
            }

            // B5-2248: an assistant's ability bonus lasts only while the
            // assistant remains rotated (rulebook :492). Unrotating the
            // assistant above is NOT sufficient on its own, because the flag
            // lives on the ambassador rather than on the assistant: before
            // this, a one-turn +1 survived the round boundary and kept
            // boosting Diplomacy/Intrigue/Leadership with nobody rotated to
            // pay for it. Cleared here unless a live sustained
            // ASSISTANT_BONUS action is still targeting this ambassador,
            // which rulebook :492 explicitly permits ("An ability bonus
            // conferred by an ambassador's assistant may be sustained").
            if (p.getAmbassador() != null
                    && !state.hasSustainedActionTargeting(p.getAmbassador(),
                            SustainedActionType.ASSISTANT_BONUS)) {
                p.getAmbassador().setAssistantBonus(false);
            }

            // Unrotate Inner Circle characters unless sustaining
            for (CharacterCard ch : p.getInnerCircle()) {
                if (!state.isSustaining(ch)) {
                    ch.unrotate();
                } else {
                    // Sustained action continues — keep rotated, do not clear
                    // assistant bonus if it's a sustained assistant action
                    SustainedAction sa = state.getSustainedAction(ch.getId());
                    if (sa == null || sa.getType() != SustainedActionType.ASSISTANT_BONUS) {
                        ch.setAssistantBonus(false);
                    }
                }
            }

            // Unrotate Supporting Role characters unless sustaining
            for (CharacterCard ch : p.getSupportingRole()) {
                if (!state.isSustaining(ch)) {
                    ch.unrotate();
                } else {
                    SustainedAction sa = state.getSustainedAction(ch.getId());
                    if (sa == null || sa.getType() != SustainedActionType.ASSISTANT_BONUS) {
                        ch.setAssistantBonus(false);
                    }
                }
            }

            // Sponsor discount always expires (not a sustained action)
            p.consumeSponsorDiscount(p.getSponsorDiscount());

            // Unrotate fleets unless sustaining (fleets don't typically sustain,
            // but the rulebook allows any "effect which states that it lasts
            // while a card remains rotated" to be sustained)
            for (FleetCard fl : p.getFleets()) {
                if (!state.isSustaining(fl)) {
                    fl.unrotate();
                }
                // Fleet leadership: clear unless the leader is sustaining a
                // leadership action on this fleet
                if (fl.getLeader() != null) {
                    boolean leaderSustaining = state.isSustaining(fl.getLeader())
                            && state.getSustainedAction(fl.getLeader().getId()) != null
                            && state.getSustainedAction(fl.getLeader().getId()).getType() == SustainedActionType.LEADERSHIP_BOOST
                            && fl.equals(state.getSustainedAction(fl.getLeader().getId()).getTargetCard());
                    if (!leaderSustaining) {
                        fl.setLeader(null);
                    }
                }
            }

            // Unrotate groups unless sustaining
            for (GroupCard gr : p.getGroups()) {
                if (!state.isSustaining(gr)) {
                    gr.unrotate();
                }
            }

            p.collectLocationIncome();
            CardEffects.applyAgendaStartOfRound(state, p);
            int enhIncome = CardEffects.enhancementLocationIncomeBonus(p);
            if (enhIncome > 0) {
                p.gainInfluence(enhIncome);
                state.log(p.getName() + " gains " + enhIncome
                        + " influence from location enhancement(s).");
            }
        }
        // B5-0395: mercenary control is per-turn — bids and controllers die
        // with the round; the offer list (a game-setup surface) persists.
        state.clearMercenaryState();
        // B5-2281: rulebook :350-:352 makes "Determine Initiative" STEP 3 of
        // the READY round, which startRound implements. Freeze it HERE, after
        // the restore/income loop above and after the bid wipe, so the order
        // the MERCENARY phase breaks ties with is the order the READY round
        // published -- and cannot be reordered later in the turn by a gain.
        initiativeState = state;
        initiativeOrder = determineInitiativeOrder(state);
        state.log("=== Round " + state.getRoundNumber() + " begins ===");
        // B5-4163 build repair: Java 6 only — no stream/lambda here. The
        // initiative order is logged with an explicit loop (this line broke
        // `compile.bat` with "lambda expressions are not supported in
        // -source 1.6" and blocked the whole tree).
        StringBuilder initLog =
                new StringBuilder("Initiative order (lowest acts first): ");
        for (int i = 0; i < initiativeOrder.size(); i++) {
            if (i > 0) initLog.append(" > ");
            initLog.append(initiativeOrder.get(i).getName());
        }
        state.log(initLog.toString());
    }

    // ── B5-0437: station end-of-round maintenance ────────────────────────────
    /** B5-0437 (0428 proposal §Sources/§Sinks), called by GameController at
     *  the END of each round, BEFORE advanceRound() resets the source marker
     *  (state mutation order matters: the marker must still be readable
     *  here). Sequence: (1) presence-bleed source — a Vorlon player holding
     *  any captured location raises vorlon influence 1 (Shadow has no faction
     *  in the enum, B5-0354, so shadow presence-bleed waits for a card hook);
     *  (2) capture-source decay sink — if NO source fired this round (neither
     *  capture nor bleed), all three ratings decay 1 toward the neutral
     *  baseline; small step so a burst of captures is not erased at once.
     *  Rounds with a capture or a bleed keep their rating (no-source guard). */
    public void applyEndOfRoundStation(GameState state) {
        Babylon5Station stn = state.getStation();
        boolean sourceFired = state.isStationSourceFired();
        for (Player p : state.getPlayers()) {
            if (p.getRace() == Race.VORLON) {
                for (Card c : p.getLocations()) {
                    if (c instanceof LocationCard) {
                        LocationCard lc = (LocationCard) c;
                        if (lc.getCapturedBy() != null) {
                            stn.gainVorlonInfluence(1);
                            sourceFired = true;
                            state.log("Station: Vorlon presence-bleed raises "
                                    + "vorlon influence to "
                                    + stn.getVorlonInfluence() + ".");
                            break;
                        }
                    }
                }
            }
        }
        // The marker's only consumer is the decay decision above; the
        // boundary discharges it so the next round starts clean (capture and
        // bleed hooks re-set it the moment a source fires again).
        state.setStationSourceFired(false);
        if (!sourceFired) {
            if (stn.getInfluence() > 0)        stn.loseInfluence(1);
            if (stn.getShadowInfluence() > 0)  stn.loseShadowInfluence(1);
            if (stn.getVorlonInfluence() > 0)  stn.loseVorlonInfluence(1);
        }
    }

    // ── Draw phase ───────────────────────────────────────────────────────────

    public void drawPhase(GameState state) {
        for (Player p : state.getPlayers()) {
            p.drawCards(1);
        }
    }

// ── Draw Round (rulebook III. "The Draw Round" :440-:465) ─────────────────
    /**
     * B5-1998: the rulebook Draw Round as a single round-lifecycle step.
     * Five steps, in the rulebook's own order:
     *
     *   1. Discard all neutralized supporting cards (NOT neutralized Inner
     *      Circle cards).
     *   2. Players may discard as many cards from hand as they wish. That is
     *      a player choice, so the engine exposes it as
     *      {@link #discardFromHand(Player, int, GameState)} and applies no
     *      automatic discard here.
     *   3. Every player draws one free card. This step does NOT reshuffle
     *      from the discard pile: "Players who draw through their entire play
     *      deck are considered to be out of new options and may not reshuffle
     *      their discards or draw" (:447). A player whose draw pile is empty
     *      discards one non-ambassador Inner Circle character instead, and a
     *      player who cannot forfeits — both already implemented by
     *      {@link Player#drawCards(int)}.
     *   4. Each player with influence remaining may draw another card for
     *      every 3 influence applied, from the per-turn applied pool. That
     *      too is a player choice (B5-4165): the engine NEVER auto-spends
     *      the pool here, and an unspent pool survives into the next turn.
     *      GameController.runDrawPhase opens the human's buy window between
     *      steps 3 and 5; {@link #buyMoreCards(Player, GameState)} buys one
     *      card per call and re-checks the pool at commit.
     *   5. Victory conditions are checked.
     *
     * <p>Note for the record: the B5-1998 ledger row asked for this step to
     * "share B5-1982's empty-deck reshuffle-from-discard approach". The
     * rulebook is canonical (AGENTS.md section 2) and forbids the reshuffle in
     * this specific step, so the reshuffle stays in AFTERMATH only. The
     * interpretation is logged in docs/DECISIONS.md.
     *
     * @return the winner if step 5 finds one, else null.
     */
    public Player drawRound(GameState state) {
        // Steps 1 + 3 are mandatory and engine-run; step 2 and step 4 are
        // player choices. drawRound() performs steps 1+3 then step 5 for
        // callers that host no human buy window; runDrawPhase uses
        // drawRoundMandatorySteps() and checkVictory() with the human's
        // elective window between them.
        drawRoundMandatorySteps(state);
        return checkVictory(state);
    }

    /**
     * B5-4165: Draw Round steps 1 and 3 only — the mandatory, engine-run
     * part of the round. Step 1 discards every neutralized supporting card;
     * step 3 gives each active player one free draw with no reshuffle. The
     * empty-deck consequence is surfaced here because
     * {@link Player#drawCards(int)} performs it silently: a draw from an
     * empty deck discards one non-ambassador Inner Circle character per card
     * owed, and a player with none left forfeits (rulebook :460, game-loop
     * report section 9 step D3). Step 4 (buy) is deliberately absent: since
     * B5-4165 it is the human's elective choice in the Draw window, never an
     * automatic spend.
     */
    public void drawRoundMandatorySteps(GameState state) {
        // Step 1 — discard neutralized supporting cards.
        for (Player p : state.getPlayers()) discardNeutralizedSupporting(p, state);
        // Step 2 is player-elected; see discardFromHand.
        // Step 3 — one free card each, no reshuffle.
        for (Player p : state.getPlayers()) {
            if (p.hasForfeited() || p.hasSurrendered()) continue;
            int icBefore = p.getInnerCircle().size();
            int before = p.getHand().size();
            p.drawCards(1);
            if (p.hasForfeited()) {
                state.log(p.getName() + " draws from an empty deck with no "
                        + "Inner Circle character left and FORFEITS (draw round step 3).");
            } else if (p.getInnerCircle().size() < icBefore) {
                state.log(p.getName() + " draws from an empty deck and discards "
                        + "an Inner Circle character (draw round step 3).");
            } else if (p.getHand().size() > before) {
                state.log(p.getName() + " draws a free card (draw round step 3).");
            }
        }
    }

    /**
     * B5-1998: Draw Round step 1 — discards every neutralized supporting
     * card. Neutralized Inner Circle characters are explicitly NOT discarded
     * (rulebook :443).
     */
    private void discardNeutralizedSupporting(Player p, GameState state) {
        if (p.hasForfeited()) return;
        List<CharacterCard> supporting = p.getSupportingRole();
        List<CharacterCard> doomed = new ArrayList<CharacterCard>();
        for (CharacterCard ch : supporting) {
            if (ch != null && ch.isNeutralized()) doomed.add(ch);
        }
        for (CharacterCard ch : doomed) {
            supporting.remove(ch);
            // B5-1977 (rulebook :510/:846): a neutralized supporting
            // character leaves play with its attached enhancements.
            detachEnhancementsFromCharacter(state, ch);
            p.getDeck().discard(ch);
            state.log(p.getName() + " discards neutralized supporting card "
                    + ch.getTitle() + " (draw round step 1).");
        }
    }

    /**
     * B5-1998: Draw Round step 2 — discards up to {@code n} cards from the
     * player's hand face-up next to their draw deck. Discarding is elective
     * ("may discard as many cards as they wish"), so the caller passes the
     * count; the trailing cards are taken deterministically so tests are
     * repeatable. Returns the number actually discarded.
     */
    public int discardFromHand(Player p, int n, GameState state) {
        if (p == null || n <= 0) return 0;
        List<Card> hand = p.getHand();
        int discarded = 0;
        while (discarded < n && !hand.isEmpty()) {
            Card toDiscard = hand.remove(hand.size() - 1);
            p.getDeck().discard(toDiscard);
            state.log(p.getName() + " discards " + toDiscard.getTitle()
                    + " from hand (draw round step 2).");
            discarded++;
        }
        return discarded;
    }

    /**
     * B5-1998/B5-4165: Draw Round step 4 — one card for exactly 3 applied
     * influence from the per-turn applied pool. Since B5-4165 the unit of
     * purchase is ONE card per call: the toolbar click charges exactly 3 and
     * buys exactly one card, and repeating the offer is the caller's loop.
     * The pre-B5-4165 while-loop drained the WHOLE pool here, which is the
     * defect: the engine force-spent the pool the elective buy was supposed
     * to preserve, and the buy button could only ever find it empty. The
     * pool is re-checked at commit (this is the predicate the UI control
     * lights off), and the draw pile is checked BEFORE charging — no
     * reshuffle from the discard pile (rulebook :447), so an empty pile
     * cannot fund a purchase and a paid-for ghost card can never exist.
     * Returns 1 when a card was drawn, 0 otherwise.
     */
    public int buyMoreCards(Player p, GameState state) {
        if (p == null || p.hasForfeited() || p.hasSurrendered()) return 0;
        if (p.getDeck() == null || p.getDeck().isEmpty()) return 0;
        if (!p.applyInfluence(3)) return 0;
        p.drawCards(1);
        state.log(p.getName() + " buys 1 card at 3 influence (draw round step 4).");
        return 1;
    }

    // ── AFTERMATH phase ────────────────────────────────────────────────────────
    /**
     * B5-1982: AFTERMATH phase card draw — each player draws 2 cards, then
     * discards down to 7. Empty deck reshuffles from discard pile (unlike the
     * Draw Round which forbids the reshuffle, per rulebook :447).
     */
    public void aftermathPhase(GameState state) {
        for (Player p : state.getPlayers()) {
            if (p.hasForfeited() || p.hasSurrendered()) continue;
            // Draw 2 cards with reshuffle from discard pile
            drawWithReshuffle(p, 2, state);
            // Discard down to 7
            discardDownTo(p, 7, state);
        }
    }

    /** Draws n cards, reshuffling discard pile into draw pile when empty. */
    private void drawWithReshuffle(Player p, int n, GameState state) {
        Deck deck = p.getDeck();
        if (deck == null) return;
        for (int i = 0; i < n; i++) {
            Card c = deck.draw();
            if (c == null) {
                // Reshuffle discard pile into draw pile
                deck.recycleDiscard();
                c = deck.draw();
            }
            if (c != null) {
                p.addToHand(c);
                state.log(p.getName() + " draws " + c.getTitle() + " (aftermath).");
            }
        }
    }

    /** Discards random cards from hand until hand size <= maxHandSize. */
    private void discardDownTo(Player p, int maxHandSize, GameState state) {
        List<Card> hand = p.getHand();
        while (hand.size() > maxHandSize) {
            // Discard the last card (deterministic for testing; AI can override)
            Card toDiscard = hand.remove(hand.size() - 1);
            p.getDeck().discard(toDiscard);
            state.log(p.getName() + " discards " + toDiscard.getTitle() + " (aftermath hand limit).");
        }
    }

    // ── B5-1995: Mark operations ──────────────────────────────────────────────

    /**
     * Adds marks of a specific type to a character. Enforces Shadow/Vorlon
     * mutual exclusion (rulebook VI). Returns the number of marks added.
     */
    public int addMarksToCharacter(GameState state, Player p, CharacterCard target, MarkType type, int count) {
        if (p == null || target == null || count <= 0) return 0;
        if (!p.canGainMark(type)) {
            if (state != null) state.log(p.getName() + " cannot gain " + type + " marks (opposing marks present).");
            return 0;
        }
        int added = target.addMarks(type, count);
        if (added > 0 && state != null) {
            state.log(p.getName() + " gains " + added + " " + type + " mark(s) on " + target.getTitle() + ".");
        }
        return added;
    }

    /**
     * Adds marks of a specific type to a fleet. Enforces Shadow/Vorlon
     * mutual exclusion (rulebook VI). Returns the number of marks added.
     */
    public int addMarksToFleet(GameState state, Player p, FleetCard target, MarkType type, int count) {
        if (p == null || target == null || count <= 0) return 0;
        if (!p.canGainMark(type)) {
            if (state != null) state.log(p.getName() + " cannot gain " + type + " marks (opposing marks present).");
            return 0;
        }
        int added = target.addMarks(type, count);
        if (added > 0 && state != null) {
            state.log(p.getName() + " gains " + added + " " + type + " mark(s) on " + target.getTitle() + ".");
        }
        return added;
    }

    /**
     * Adds marks of a specific type to a location. Enforces Shadow/Vorlon
     * mutual exclusion (rulebook VI). Returns the number of marks added.
     */
    public int addMarksToLocation(GameState state, Player p, LocationCard target, MarkType type, int count) {
        if (p == null || target == null || count <= 0) return 0;
        if (!p.canGainMark(type)) {
            if (state != null) state.log(p.getName() + " cannot gain " + type + " marks (opposing marks present).");
            return 0;
        }
        int added = target.addMarks(type, count);
        if (added > 0 && state != null) {
            state.log(p.getName() + " gains " + added + " " + type + " mark(s) on " + target.getTitle() + ".");
        }
        return added;
    }

    /**
     * Purges marks of a specific type from all cards in a faction.
     * Rulebook VI: "If a character is cut off from a source of a mark
     * (for example, if a faction switches agendas or if an aftermath or
     * enhancement is discarded or blanked) then that character must purge
     * a mark of that type."
     * Returns the total number of marks purged.
     */
    public int purgeMarks(GameState state, Player p, MarkType type) {
        if (p == null) return 0;
        int purged = p.purgeMarks(type);
        if (purged > 0 && state != null) {
            state.log(p.getName() + " purges " + purged + " " + type + " mark(s) (source cut off).");
        }
        return purged;
    }

    /**
     * Purges one mark of a specific type from a specific character.
     * Returns the number of marks purged (0 or 1).
     */
    public int purgeMarkFromCharacter(GameState state, Player p, CharacterCard target, MarkType type) {
        if (p == null || target == null) return 0;
        int purged = target.removeMarks(type, 1);
        if (purged > 0 && state != null) {
            state.log(p.getName() + " purges 1 " + type + " mark from " + target.getTitle() + ".");
        }
        return purged;
    }

    /**
     * Checks if a faction has at least the required number of marks of a type.
     * Used for card play requirements (e.g., "requires 3 Vorlon marks").
     */
    public boolean hasRequiredMarks(Player p, MarkType type, int required) {
        if (p == null) return false;
        return p.getTotalMarks(type) >= required;
    }

    /**
     * Returns the total count of a mark type for a faction.
     */
    public int getTotalMarks(Player p, MarkType type) {
        if (p == null) return 0;
        return p.getTotalMarks(type);
    }

    // ── B5-1970: AgendaCard voting phase (rulebook §Votes :789-:797) ─────────
    //
    // This block is the VOTING-PHASE surface only. It opens the session,
    // decides who may vote, records each ambassador's ballot, and charges
    // the influence a card's vote costs. It deliberately does NOT decide
    // whether the measure passed: the one-more-Yes-than-No rule, the League
    // tie-break and unplayed-race abstention are B5-1993's `resolveCouncilVote`
    // (that row fences this one and names them), and the player-facing ballot
    // is B5-2004's. Building the tally here too would give the queue two
    // authorities for one rule. countAgendaVotes() exposes the raw counts so
    // B5-1993 reads one source rather than re-deriving it.
    //
    // SCOPE DEPENDENCY (recorded, not worked around): the row asks for a new
    // GamePhase.AGENDA_VOTE between ACTION and CONFLICT_RESOLUTION, but
    // GamePhase is an enum in b5ccg/src/b5ccg/model/enums/ and this claim's
    // scope is engine/RulesEngine.java alone, so the constant is not added
    // here. The engine surface is deliberately phase-independent: it gates on
    // its own open-session state, so a later model/-scoped row adds
    // `AGENDA_VOTE` to the enum (after MERCENARY) and calls openAgendaVote /
    // closeAgendaVote from the round driver with no change here. MERCENARY
    // already occupies the ACTION -> CONFLICT_RESOLUTION slot, so the new
    // constant goes after it.

    /** The three ballots the Council may cast (rulebook :795: "each ambassador
     *  must vote \"Yes\", \"No\", or \"Abstain\""). */
    public enum AgendaVote { YES, NO, ABSTAIN }

    /** The agenda whose measure is on the floor, or null when no vote is open. */
    private AgendaCard agendaUnderVote = null;
    /** Ballots cast so far, keyed by the voting player. Identity-keyed, never
     *  index-keyed: the Council is the five races' ambassadors, not seat
     *  order. */
    private final Map<Player, AgendaVote> agendaVotes =
            new HashMap<Player, AgendaVote>();

    /** B5-1970: the head of the council (rulebook :795) — "If there is an Earth
     *  Alliance ambassador to Babylon 5 in the game, that player \"heads\" the
     *  council; if not, the player of the card which requires a vote \"heads\"
     *  the council." The head calls the vote in whatever order he wishes, so
     *  this is the ordering surface the UI (B5-2004) and the resolution
     *  (B5-1993) both key on. Returns null when neither rule finds a head. */
    public Player councilHead(AgendaCard requiring, GameState state) {
        if (state == null) return null;
        for (Player p : state.getPlayers()) {
            if (p == null || !isEligibleAgendaVoter(p)) continue;
            if (p.getRace() == Race.HUMAN) return p;
        }
        if (requiring != null) {
            for (Player p : state.getPlayers()) {
                if (p == null || !isEligibleAgendaVoter(p)) continue;
                if (p.getAgenda() == requiring) return p;
            }
        }
        return null;
    }

    /** A player may vote through their own ambassador (rulebook :793) and only
     *  while still in the game: "Card status (such as being rotated or
     *  neutralized) has no effect on an ambassador's ability to vote", so this
     *  gate deliberately reads no card state. A forfeited or surrendered
     *  player has ceased play (:454, :815) and cannot vote. */
    public boolean isEligibleAgendaVoter(Player p) {
        return p != null && !p.hasForfeited() && !p.hasSurrendered();
    }

    /** True when a measure on `ag` may be put to a vote right now. A hidden
     *  (face-down) agenda is excluded: it "has no effect on play until
     *  revealed" (:524), so it cannot put a measure to the Council. One vote
     *  is open at a time — the Council has a single floor. */
    public boolean canOpenAgendaVote(AgendaCard ag, GameState state) {
        if (ag == null || state == null) return false;
        if (ag.isFaceDown()) return false;
        if (agendaUnderVote != null) return false;
        return councilHead(ag, state) != null;
    }

    /** Opens the voting session for `ag`. No-op (and logged) when
     *  canOpenAgendaVote is false, so a caller cannot put a hidden agenda or a
     *  second measure to the Council. Returns true when the session opened. */
    public boolean openAgendaVote(AgendaCard ag, GameState state) {
        if (!canOpenAgendaVote(ag, state)) {
            if (state != null && ag != null) {
                state.log("Vote not called: " + ag.getTitle()
                        + " cannot be put to the Council now.");
            }
            return false;
        }
        agendaUnderVote = ag;
        agendaVotes.clear();
        Player head = councilHead(ag, state);
        if (head != null) {
            state.log(head.getName() + " heads the council on the vote ("
                    + ag.getTitle() + ").");
        }
        return true;
    }

    /** True while a measure is on the floor. */
    public boolean isAgendaVoteOpen() {
        return agendaUnderVote != null;
    }

    /** The agenda under vote, or null. */
    public AgendaCard getAgendaUnderVote() {
        return agendaUnderVote;
    }

    /** A ballot is legal when the session is open, the player is an eligible
     *  voter, they have not already voted this session, and they can cover
     *  `cost` influence from the APPLIED POOL (D9 discipline — the same pool
     *  mercenary bidding spends, never the influence Rating).
     *
     *  <p>`cost` is a parameter, not a constant: the rulebook states no
     *  generic price for calling a vote ("Some cards may also list other
     *  requirements for a vote to succeed", :797), so the engine takes what
     *  the calling card charges and callers with no stated cost pass 0. A
     *  hard-coded 1 would invent a rule the rulebook does not contain. */
    public boolean canCastAgendaVote(Player p, AgendaVote vote, int cost,
                                     GameState state) {
        if (vote == null || cost < 0) return false;
        if (agendaUnderVote == null || state == null) return false;
        if (!isEligibleAgendaVoter(p)) return false;
        if (agendaVotes.containsKey(p)) return false;
        return p.getAppliedPool() >= cost;
    }

    /** Records p's ballot and charges the cost. No-op returning false when
     *  canCastAgendaVote is false; a second cast by the same player is
     *  rejected, not overwritten, so a vote cannot be re-run by re-prompting
     *  one player. Abstain is a real ballot and is recorded like any other —
     *  unplayed races abstaining BY DEFAULT is B5-1993's rule to apply, not a
     *  silent omission here. */
    public boolean castAgendaVote(Player p, AgendaVote vote, int cost,
                                  GameState state) {
        if (!canCastAgendaVote(p, vote, cost, state)) return false;
        agendaVotes.put(p, vote);
        if (cost > 0 && !p.applyInfluence(cost)) {
            // Defensive: canCastAgendaVote proved afford-now and nothing
            // between the two calls can drain the pool (single-threaded
            // controller). Logged loudly, never silently refunded.
            System.err.println("B5-1970: vote by " + p.getName() + " could not be "
                    + "charged " + cost + " despite legality checks — ballot stands, "
                    + "pool unspent.");
            return true;
        }
        state.log(p.getName() + " votes " + vote + " on \""
                + agendaUnderVote.getTitle() + "\""
                + (cost > 0 ? " (" + cost + " influence)." : "."));
        return true;
    }

    /** The ballot p cast this session, or null if p has not voted. */
    public AgendaVote getAgendaVote(Player p) {
        if (p == null) return null;
        return agendaVotes.get(p);
    }

    /** How many players cast `vote` this session. A raw count, never a verdict:
     *  whether the measure passed is B5-1993's resolveCouncilVote. */
    public int countAgendaVotes(AgendaVote vote) {
        if (vote == null) return 0;
        int n = 0;
        for (AgendaVote cast : agendaVotes.values()) {
            if (cast == vote) n++;
        }
        return n;
    }

    /** The ballots cast this session, as an unmodifiable snapshot. The live map
     *  is never handed out: a caller mutating it would desynchronise the
     *  counts from the session. */
    public Map<Player, AgendaVote> getAgendaVotes() {
        return Collections.unmodifiableMap(
                new HashMap<Player, AgendaVote>(agendaVotes));
    }

    /** Closes the session and clears it. Callers resolve the measure through
     *  B5-1993's resolveCouncilVote BEFORE calling this — closing discards
     *  the ballots, so resolving afterwards would tally an empty council. */
    public void closeAgendaVote(GameState state) {
        if (agendaUnderVote == null) return;
        if (state != null) {
            state.log("The vote on \"" + agendaUnderVote.getTitle()
                    + "\" is closed (" + agendaVotes.size() + " ballot(s) cast).");
        }
        agendaUnderVote = null;
        agendaVotes.clear();
    }

    /** Drops any open session without logging — for round teardown and for a
     *  new-round reset, where the previous measure's ballots are spent. */
    public void resetAgendaVote() {
        agendaUnderVote = null;
        agendaVotes.clear();
    }

    // ── B5-1993: Council vote resolution (rulebook §VI Votes :789-:797) ─────────
    /**
     * Resolves the current Council vote per rulebook section VI.
     * <p>
     * The Council consists of five races (Earth/HUMAN, Minbari, Centauri, Narn,
     * Vorlon) plus the League of Non-Aligned Worlds (NON_ALIGNED) which may cast
     * one vote to break a tie. Each player in the game votes through their
     * ambassador. Races not currently being played are considered part of the
     * vote but abstain by default. A measure passes when there is at least one
     * more "Yes" than "No" vote (Yes >= No + 1). The League's tie-breaking vote
     * is applied only when Yes == No after all other votes are counted.
     *
     * @param state the game state (for logging and player enumeration)
     * @return true if the measure passes, false otherwise
     * @throws IllegalStateException if no vote session is open
     */
    public boolean resolveCouncilVote(GameState state) {
        if (agendaUnderVote == null) {
            throw new IllegalStateException("No agenda vote session is open");
        }
        if (state == null) {
            throw new IllegalArgumentException("GameState must not be null");
        }

        // Council races that are always part of the vote (rulebook :791)
        final Race[] councilRaces = new Race[] {
            Race.HUMAN, Race.MINBARI, Race.CENTAURI, Race.NARN, Race.VORLON
        };

        // Tally votes from players who cast ballots
        int yes = 0;
        int no = 0;
        int abstain = 0;

        for (Map.Entry<Player, AgendaVote> entry : agendaVotes.entrySet()) {
            AgendaVote vote = entry.getValue();
            if (vote == AgendaVote.YES) yes++;
            else if (vote == AgendaVote.NO) no++;
            else if (vote == AgendaVote.ABSTAIN) abstain++;
        }

        // Add default ABSTAIN for council races not represented by a player
        // (rulebook :793: "Ambassadors from races not currently being played
        // are considered to be part of the vote, but by default they abstain")
        for (Race race : councilRaces) {
            boolean hasPlayer = false;
            for (Player p : state.getPlayers()) {
                if (p.getRace() == race) {
                    hasPlayer = true;
                    break;
                }
            }
            if (!hasPlayer) {
                abstain++;
            }
        }

        // League of Non-Aligned Worlds tie-break (rulebook :791:
        // "the League of Non-Aligned worlds (acting as if it were a single race)
        // may cast one vote to break any tie")
        // The League votes only when Yes == No after all other votes.
        // The League's vote is not pre-cast; it breaks the tie by voting Yes
        // if that would make the measure pass, otherwise No.
        // Since the rule says "may cast one vote to break any tie", we interpret
        // this as: if Yes == No, the League votes Yes to pass the measure.
        // If Yes != No, the League does not vote.
        if (yes == no) {
            yes++; // League breaks tie in favor of the measure
            state.log("League of Non-Aligned Worlds breaks tie with Yes vote.");
        }

        boolean passed = (yes >= no + 1);

        if (state != null) {
            state.log("Council vote on \"" + agendaUnderVote.getTitle()
                    + "\": Yes=" + yes + " No=" + no + " Abstain=" + abstain
                    + (passed ? " — PASSED" : " — FAILED"));
        }

        return passed;
    }

    // ── B5-1996: inter-faction relationship states ───────────────────────────
    /** The state book for one engine instance's game. Bound on first use so
     *  that alliance and trade pacts persist across calls: handing out a
     *  fresh book per call would make every query read an empty map and every
     *  transition a no-op, which is the "absence of an error is not presence
     *  of a value" failure. One RulesEngine serves one game, which is how
     *  GameController and AIPlayer already hold it. */
    private RaceStateBook factionStates;

    /** Rulebook :801-803 "States": a pair's relationship is primarily its
     *  tension, plus any additional relationships -- alliances, trade pacts,
     *  war -- the races have entered. The at-war fact stays owned by the
     *  TensionMatrix, so B5-0376's war resolution and every existing isAtWar
     *  reader are untouched.
     *
     *  Returns null for a game this engine is not bound to, and for a second
     *  distinct game: the book holds mutable relationship state and one
     *  engine instance must not answer for two games at once. */
    public RaceStateBook getRaceStates(GameState state) {
        if (state == null) return null;
        if (factionStates == null) factionStates = new RaceStateBook(state);
        return factionStates.forSameGame(state) ? factionStates : null;
    }

    /** True when the pair is in the named state (rulebook :801-803). */
    public boolean areInState(Race a, Race b, RaceState s, GameState state) {
        RaceStateBook book = getRaceStates(state);
        return book != null && book.isInState(a, b, s);
    }

    public boolean areAllied(Race a, Race b, GameState state) {
        return areInState(a, b, RaceState.ALLIANCE, state);
    }

    public boolean areTrading(Race a, Race b, GameState state) {
        return areInState(a, b, RaceState.TRADE_PACT, state);
    }

    /** Enter an alliance or a trade pact between two races. */
    public boolean enterRaceState(Race a, Race b, RaceState s, GameState state) {
        RaceStateBook book = getRaceStates(state);
        return book != null && book.enterState(a, b, s);
    }

    public boolean exitRaceState(Race a, Race b, RaceState s, GameState state) {
        RaceStateBook book = getRaceStates(state);
        return book != null && book.exitState(a, b, s);
    }
}
