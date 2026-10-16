package b5ccg.model;

import b5ccg.model.enums.*;

public class GameAction {
    public enum Type {
        PLAY_CARD,
        INITIATE_CONFLICT,
        JOIN_CONFLICT_SUPPORT,
        JOIN_CONFLICT_OPPOSE,
        // B5-0370: card=attacker, targetCard=conflict participant being attacked.
        ATTACK_CONFLICT_PARTICIPANT,
        PLAY_AFTERMATH,
RECRUIT_CHARACTER,
        // B5-2245 (rulebook :657): the rulebook's single "Sponsor a Supporting
        // Card" action for the card types RECRUIT_CHARACTER does not cover —
        // Enhancement, Location and Fleet. card = the sponsored card, leader =
        // the Inner Circle member that rotates to sponsor it (:657 "Any
        // character in the Inner Circle may rotate to bring a new supporting
        // ... into play"). Separate from RECRUIT_CHARACTER because the two
        // charge differently: :663 makes a different-race CHARACTER dearer
        // while :664/:665 make a different-race card of any other type
        // ILLEGAL, so one Type cannot carry both rules.
        SPONSOR_CARD,
        BUILD_INFLUENCE,
        PROMOTE_CHARACTER,
        // B5-0371: rotate a character to heal, or a fleet/location to repair.
        HEAL_CHARACTER,
        REPAIR_CARD,
        // B5-0362: rotate a ready character to lead an own fleet (B5-0345
        // Tier-1 #1) — card = the fleet, leader = the rotating character.
        LEAD_FLEET,
        // B5-0366: generic rotate-for-effect (B5-0345 Tier-2 #5, rulebook
        // §IV) — card = the rotating assistant, leader = the ambassador it
        // assists, targetCard = unused (reserved for future Vir-style effects
        // whose payload names a third card).
        USE_ROTATE_EFFECT,
        // B5-0364: agenda lifecycle (B5-0345 Tier-1 #3, rulebook :719).
        DISCARD_AGENDA,
        REPLACE_AGENDA,
        REVEAL_AGENDA,
        PLAY_CONTINGENCY,
        REVEAL_CONTINGENCY,
        // B5-4175: elective discard of cards from the human's hand (Draw round
        // step D2); n = number of cards discarded.
        DISCARD_HAND,
        // B5-0376: declare a war conflict (card==null; warKind + target in the
        // action; engine resolves through the war outcome path).
        DECLARE_WAR_CONFLICT,
        // B5-0395 (rulebook §Mercenaries): apply influence as a bid to control
        // a mercenary card this turn — card = the mercenary, amount = the bid
        // in applied-pool influence (bids cumulate; the highest total controls
        // at the mercenary phase).
        BID_ON_MERCENARY,
        // B5-0661 (rulebook :815–:819): a player at war may surrender during
        // the discard round; the opponent gains 3 influence and receives an
        // asylum copy of the ambassador as a supporting character.
        SURRENDER,
        // B5-1979: voluntary forfeit. Taken as an ACTION-phase action, it ends
        // this player's participation immediately (loss) and awards victory to
        // the opponent(s). Distinct from SURRENDER, which is a DRAW-phase
        // negotiation that leaves the surrendering player merely out of play
        // without the discard-to-discard ambassador loss this path performs,
        // and distinct from the involuntary draw-deck forfeit that
        // Player.drawCards sets when no Inner Circle character remains to
        // discard (rulebook :460, "he must forfeit the game").
        FORFEIT,
        PASS
    }

    /**
     * B5-0366: the player-chosen rotate-for-effect vocabulary (B5-0345
     * Tier-2 #5, rulebook §IV), promoted from the wired B5-0339 special
     * cases. USE_ABILITY_BOOST rotates a ready supporting assistant to flag
     * his ambassador (+1 Diplomacy/Intrigue/Leadership while rotated, Psi
     * untouched); USE_SPONSOR_DISCOUNT rotates the same kind of assistant so
     * the ambassador sponsors 1 influence cheaper later this turn. Lives in
     * the model (not RulesEngine) so GameAction factories need no
     * model→engine import. Future Vir-style rotate effects add kinds here
     * with new execute branches in RulesEngine.
     */
    public enum RotateEffectKind {
        USE_ABILITY_BOOST,
        USE_SPONSOR_DISCOUNT
    }

    private final Type   type;
    private final Card   card;
    private final Player target;
    // B5-0321: the rotating Inner Circle member that performs the action
    // (promotion needs BOTH the promoted character and the rotating member).
    private final CharacterCard leader;
    private final Card targetCard;
    // B5-0364: PLAY_CARD variant — sponsor an agenda FACE-DOWN as a hidden
    // agenda (rulebook :520/:719 "may be brought into play face-down");
    // non-final so the dedicated factory below can set it.
    private boolean hidden = false;
    // B5-0366: the chosen rotate effect for USE_ROTATE_EFFECT (null = none).
    private RotateEffectKind rotateKind = null;
    // B5-0395: the influence bid for BID_ON_MERCENARY (0 = none).
    private int amount = 0;

    public GameAction(Type type, Card card, Player target) {
        this(type, card, target, null);
    }

    public GameAction(Type type, Card card, Player target, CharacterCard leader) {
        this(type, card, target, leader, null);
    }

    private GameAction(Type type, Card card, Player target, CharacterCard leader, Card targetCard) {
        this.type   = type;
        this.card   = card;
        this.target = target;
        this.leader = leader;
        this.targetCard = targetCard;
    }

    // ── Factory methods ──────────────────────────────────────────────────────

    public static GameAction pass()                              { return new GameAction(Type.PASS, null, null); }
    public static GameAction playCard(Card c)                    { return new GameAction(Type.PLAY_CARD, c, null); }
    public static GameAction initiateConflict(Card c, Player t)  { return new GameAction(Type.INITIATE_CONFLICT, c, t); }
    public static GameAction joinSupport()                       { return new GameAction(Type.JOIN_CONFLICT_SUPPORT, null, null); }
    public static GameAction joinOppose()                        { return new GameAction(Type.JOIN_CONFLICT_OPPOSE, null, null); }
    public static GameAction attackConflictParticipant(Card attacker, Card target) {
        return new GameAction(Type.ATTACK_CONFLICT_PARTICIPANT, attacker, null, null, target);
    }
    public static GameAction recruitCharacter(Card c)            { return new GameAction(Type.RECRUIT_CHARACTER, c, null); }
    public static GameAction buildInfluence(CharacterCard leader){ return new GameAction(Type.BUILD_INFLUENCE, leader, null); }
    public static GameAction healCharacter(CharacterCard ch) { return new GameAction(Type.HEAL_CHARACTER, ch, null); }
    public static GameAction repairCard(Card card) { return new GameAction(Type.REPAIR_CARD, card, null); }
    /** B5-0321: promote ch into the Inner Circle, rotating the IC member leader. */
    public static GameAction promoteCharacter(CharacterCard ch, CharacterCard leader) {
        return new GameAction(Type.PROMOTE_CHARACTER, ch, null, leader);
    }
    /** B5-2245: leader rotates to sponsor card (Enhancement/Location/Fleet)
     *  into play as a Supporting Card — the same leader/card field pair
     *  PROMOTE_CHARACTER and LEAD_FLEET use, so no new GameAction field. */
    public static GameAction sponsorCard(Card c, CharacterCard leader) {
        return new GameAction(Type.SPONSOR_CARD, c, null, leader);
    }
    /** B5-0362: leader rotates to lead fleet — the pair rides the existing
     *  leader/card fields (card = fleet, leader = rotating character), no new
     *  GameAction fields. */
    public static GameAction leadFleet(CharacterCard leader, FleetCard fleet) {
        return new GameAction(Type.LEAD_FLEET, fleet, null, leader);
    }
    /** B5-0364: sponsor an agenda face-down as a hidden agenda (:520/:719);
     *  it has no effect on play until revealed. */
    public static GameAction playAgendaFaceDown(Card c) {
        GameAction a = new GameAction(Type.PLAY_CARD, c, null);
        a.hidden = true;
        return a;
    }
    /** B5-0364: discard the current agenda (Major agendas refused engine-side). */
    public static GameAction discardAgenda(AgendaCard ag) {
        return new GameAction(Type.DISCARD_AGENDA, ag, null);
    }
    /** B5-0364: replace the current agenda with ag, rotating the IC leader
     *  (:520 rotate, :719 new agenda from hand that you can sponsor). */
    public static GameAction replaceAgenda(AgendaCard ag, CharacterCard leader) {
        return new GameAction(Type.REPLACE_AGENDA, ag, null, leader);
    }
    /** B5-0364: reveal the face-down hidden agenda — it takes effect
     *  immediately (:520). */
    public static GameAction revealAgenda(AgendaCard ag) {
        return new GameAction(Type.REVEAL_AGENDA, ag, null);
    }
    /** B5-0366: rotate ch to assist amb with the named effect kind.
     *  The kind rides the existing (card, leader) pair fields (card = the
     *  rotating assistant, leader = the ambassador); the payload target list
     *  is the leader field today, extended via targetCard when future effects
     *  name a third card. Refused kinds still cost the action at the
     *  controller (same as every other refused action branch). */
    public static GameAction useRotateEffect(CharacterCard ch, CharacterCard amb,
                                             RotateEffectKind kind) {
        GameAction a = new GameAction(Type.USE_ROTATE_EFFECT, ch, null, amb);
        a.rotateKind = kind;
        return a;
    }
    /** B5-0365: play a contingency face-down under its chosen host. */
    public static GameAction playContingency(ContingencyCard contingency, Card host) {
        return new GameAction(Type.PLAY_CONTINGENCY, contingency, null, null, host);
    }
    /** B5-0365: reveal a contingency when its printed trigger is met. */
    public static GameAction revealContingency(ContingencyCard contingency) {
        return new GameAction(Type.REVEAL_CONTINGENCY, contingency, null, null, null);
    }

    /** B5-0376: declare a war conflict. The war kind and target ride the
     *  existing (card, target, targetCard) fields: card==null, target=race
     *  target (RACE_TARGET), targetCard=location target (LOCATION_TARGET). */
    public static GameAction declareWarConflict(WarKind kind, Player raceTarget, LocationCard locTarget) {
        Card loc = (locTarget != null) ? locTarget : null;
        return new GameAction(Type.DECLARE_WAR_CONFLICT, null, raceTarget, null, loc);
    }

    /** B5-0395: apply `amount` applied-pool influence as a cumulative bid to
     *  control mercenary `merc` for this turn. */
    public static GameAction bidOnMercenary(Card merc, int amount) {
        GameAction a = new GameAction(Type.BID_ON_MERCENARY, merc, null);
        a.amount = amount;
        return a;
    }

    /** B5-4175: discard exactly `n` cards from the human's hand (Draw round
     *  step D2). Elective: the engine returns how many were actually discarded;
     *  the caller submits the action only when at least one card was discarded. */
    public static GameAction discardHandCards(int n) {
        GameAction a = new GameAction(Type.DISCARD_HAND, null, null);
        a.amount = n;
        return a;
    }

    /** B5-0661 (rulebook :815–:819): unconditional surrender during the
     *  discard round. The surrendering player declares a war opponent; that
     *  opponent gains 3 influence and receives an asylum copy of the
     *  surrendering player's ambassador as a supporting character. */
    public static GameAction surrender(Player target) {
        return new GameAction(Type.SURRENDER, null, target);
    }

    /** B5-1979: voluntary forfeit, legal during the ACTION phase. The player
     *  is the actor, so no card and no target: forfeiting is a self-declared
     *  loss and there is nothing to aim at. */
    public static GameAction forfeit() {
        return new GameAction(Type.FORFEIT, null, null);
    }
    /** B5-4158: play an aftermath card during the AFTERMATH phase.
     *  card = the aftermath card, target = the player it attaches to. */
    public static GameAction playAftermath(AftermathCard am, Player target) {
        return new GameAction(Type.PLAY_AFTERMATH, am, target);
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public Type   getType()   { return type; }
    public Card   getCard()   { return card; }
    public Player getTarget() { return target; }
    public CharacterCard getLeader() { return leader; }
    public Card getTargetCard() { return targetCard; }
    /** B5-0364: true when a PLAY_CARD carries a face-down hidden agenda. */
    public boolean isHidden() { return hidden; }
    /** B5-0366: the chosen rotate effect (null when not a rotate action). */
    public RotateEffectKind getRotateKind() { return rotateKind; }
    /** B5-0395: the influence bid for BID_ON_MERCENARY (0 when not a bid). */
    public int getAmount() { return amount; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type);
        if (card != null) {
            sb.append(": ").append(card.getTitle());
        }
        if (target != null) {
            sb.append(" -> ").append(target.getName());
        }
        if (targetCard != null) {
            sb.append(" attacks ").append(targetCard.getTitle());
        }
        if (leader != null) {
            sb.append(" (leader: ").append(leader.getTitle()).append(")");
        }
        if (type == Type.BID_ON_MERCENARY) {
            sb.append(" (bid: ").append(amount).append(")");
        }
        return sb.toString();
    }
}
