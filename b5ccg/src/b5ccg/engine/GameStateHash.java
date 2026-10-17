package b5ccg.engine;

import b5ccg.model.Card;
import b5ccg.model.CivilWarState;
import b5ccg.model.Conflict;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.Race;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * B5-0008 (WP-07/A5): deterministic hash of observable game state.
 *
 * <p>Walks {@link GameState} public accessors only, in a canonical order that
 * does not depend on hash-map iteration (pairs iterate {@code Race.values()},
 * players iterate in table order, zones in list order), and reduces the dump
 * to a SHA-256 hex string. Two runs of the same seeded game must produce the
 * identical sequence of hashes at identical points — the property the
 * double-run probe ({@code HeadlessDoubleRunProbe}) enforces and the property
 * golden-master work (A6) builds on.</p>
 *
 * <p>Coverage: round, phase, active player, winner; station scalars and
 * condition flags; the full tension matrix and at-war set; non-aggression
 * pairs; civil-war state per race; per-player scalars, zone contents in
 * order, deck draw pile and discard pile, ambassador; attached aftermaths;
 * placed contingencies; initiated-this-turn flags; active and queued
 * conflicts (sides and committed cards per table-order player); mercenary
 * offers, bids and controllers; sustained-action membership for every card in
 * play.</p>
 *
 * <p>Deliberately excluded: the log lines (noise, and they duplicate state),
 * and anything without a public accessor. Exclusions are safe for the
 * double-run contract as long as they stay excluded in BOTH runs.</p>
 */
public class GameStateHash {

    private GameStateHash() { /* static utility */ }

    /** SHA-256 hex of the canonical dump of the given state. */
    public static String of(GameState state) {
        return sha256Hex(dump(state));
    }

    /** The canonical dump itself — one line per fact, for diagnostics. */
    public static String dump(GameState state) {
        StringBuilder sb = new StringBuilder();
        List<Player> players = state.getPlayers();

        sb.append("round=").append(state.getRoundNumber()).append('\n');
        sb.append("phase=").append(state.getPhase() == null ? "-" : state.getPhase().name()).append('\n');
        sb.append("active=").append(playerKey(state, state.getActivePlayer())).append('\n');
        sb.append("winner=").append(state.getWinner() == null ? "-" : state.getWinner().getName()).append('\n');
        sb.append("station.rating=").append(state.getStation().getInfluence()).append('\n');
        sb.append("station.shadow=").append(state.getShadowInfluence()).append('\n');
        sb.append("station.vorlon=").append(state.getVorlonInfluence()).append('\n');
        sb.append("station.sourceFired=").append(state.isStationSourceFired()).append('\n');
        sb.append("station.shadowActive=").append(state.isShadowActive()).append('\n');
        sb.append("station.vorlonActive=").append(state.isVorlonActive()).append('\n');
        sb.append("station.humanSecured=").append(state.isHumanSecured()).append('\n');
        sb.append("station.uncontested=").append(state.isUncontested()).append('\n');
        sb.append("station.shadowWar=").append(state.isShadowWar()).append('\n');

        // Tension + war: iterate Race.values() pairs — never a hash-map order.
        Race[] races = Race.values();
        for (int a = 0; a < races.length; a++) {
            for (int b = 0; b < races.length; b++) {
                if (a == b) continue;
                sb.append("tension.").append(races[a].name()).append('>')
                  .append(races[b].name()).append('=')
                  .append(state.getTensionMatrix().getTension(races[a], races[b])).append('\n');
            }
        }
        for (int a = 0; a < races.length; a++) {
            for (int b = a + 1; b < races.length; b++) {
                if (state.isAtWar(races[a], races[b])) {
                    sb.append("war.").append(races[a].name()).append('/')
                      .append(races[b].name()).append("=1\n");
                }
            }
        }

        // Civil war per race (absent = UNIFIED-untracked).
        for (int r = 0; r < races.length; r++) {
            CivilWarState cws = state.civilWarOfRace(races[r]);
            if (cws != null) {
                sb.append("civilwar.").append(races[r].name())
                  .append('=').append(cws.getPhase())
                  .append('@').append(cws.getEnteredRound()).append('\n');
            }
        }

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            String key = "p" + i + "." + p.getName();
            sb.append(key).append(".race=").append(p.getRace() == null ? "-" : p.getRace().name()).append('\n');
            sb.append(key).append(".influence=").append(p.getInfluence()).append('\n');
            sb.append(key).append(".applied=").append(p.getAppliedPool()).append('\n');
            sb.append(key).append(".unrest=").append(p.getUnrest()).append('\n');
            sb.append(key).append(".actionsLeft=").append(p.getActionsLeft()).append('\n');
            sb.append(key).append(".passed=").append(p.isPassed()).append('\n');
            sb.append(key).append(".forfeited=").append(p.hasForfeited()).append('\n');
            sb.append(key).append(".surrendered=").append(p.hasSurrendered()).append('\n');
            sb.append(key).append(".initiatedConflict=").append(state.hasInitiatedConflictThisTurn(p)).append('\n');
            sb.append(key).append(".initiatedWar=").append(state.hasInitiatedWarConflictThisTurn(p)).append('\n');
            zone(sb, key + ".hand",         p.getHand());
            zone(sb, key + ".innerCircle",  p.getInnerCircle());
            zone(sb, key + ".supporting",   p.getSupportingRole());
            zone(sb, key + ".fleets",       p.getFleets());
            zone(sb, key + ".locations",    p.getLocations());
            zone(sb, key + ".groups",       p.getGroups());
            sb.append(key).append(".ambassador=")
              .append(p.getAmbassador() == null ? "-" : p.getAmbassador().getId()).append('\n');
            if (p.getDeck() != null) {
                zone(sb, key + ".drawPile", p.getDeck().peekTop(p.getDeck().size()));
                zone(sb, key + ".discard",  p.getDeck().getDiscardPile());
            }
            zone(sb, key + ".aftermaths",   state.getAttachedAftermaths(p));
            zone(sb, key + ".contingencies", state.getPlacedContingencies(p));
        }

        // Non-aggression pairs (Player-keyed, so walk the table).
        for (int a = 0; a < players.size(); a++) {
            for (int b = a + 1; b < players.size(); b++) {
                if (state.isNonAggression(players.get(a), players.get(b))) {
                    sb.append("nonaggression.").append(a).append('/').append(b).append("=1\n");
                }
            }
        }

        conflict(sb, "active", state.getActiveConflict(), players);
        List<Conflict> queued = state.getQueuedConflicts();
        for (int q = 0; q < queued.size(); q++) {
            conflict(sb, "queued" + q, queued.get(q), players);
        }

        // Mercenaries: offers in list order, bids per offer per table-order player.
        List<Card> offers = state.getMercenaryOffers();
        for (int o = 0; o < offers.size(); o++) {
            Card c = offers.get(o);
            sb.append("merc.offer.").append(o).append('=').append(c.getId()).append('\n');
            Player controller = state.getMercenaryController(c);
            sb.append("merc.controller.").append(o).append('=')
              .append(controller == null ? "-" : controller.getName()).append('\n');
            for (int pi = 0; pi < players.size(); pi++) {
                sb.append("merc.bid.").append(o).append('.').append(pi).append('=')
                  .append(state.getMercenaryBid(c, players.get(pi))).append('\n');
            }
        }

        // Sustained actions: no registry accessor, so record membership for
        // every card in play (any zone of any player).
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            appendSustained(sb, state, p.getHand());
            appendSustained(sb, state, p.getInnerCircle());
            appendSustained(sb, state, p.getSupportingRole());
            appendSustained(sb, state, p.getFleets());
            appendSustained(sb, state, p.getLocations());
            appendSustained(sb, state, p.getGroups());
        }

        return sb.toString();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** "pN.Name" for a player found in the table, or its name if unwired. */
    private static String playerKey(GameState state, Player p) {
        if (p == null) return "-";
        List<Player> players = state.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i) == p) return "p" + i + "." + p.getName();
        }
        return p.getName();
    }

    /** One ordered zone line: comma-separated card IDs in list order. */
    private static void zone(StringBuilder sb, String key, List<? extends Card> cards) {
        sb.append(key).append('[');
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(cards.get(i).getId());
        }
        sb.append("]\n");
    }

    private static void appendSustained(StringBuilder sb, GameState state, List<? extends Card> cards) {
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            if (state.isSustaining(c)) sb.append("sustaining.").append(c.getId()).append("=1\n");
        }
    }

    /** One conflict's canonical facts: card, parties, sides and committed
     *  cards walked in TABLE order (never the map's own order), outcome. */
    private static void conflict(StringBuilder sb, String key, Conflict c, List<Player> players) {
        if (c == null) {
            sb.append(key).append("=null\n");
            return;
        }
        sb.append(key).append(".card=")
          .append(c.getCard() == null ? "-" : c.getCard().getId()).append('\n');
        sb.append(key).append(".initiator=").append(playerKeyNamed(c.getInitiator(), players)).append('\n');
        sb.append(key).append(".target=")
          .append(c.getTarget() == null ? "-" : playerKeyNamed(c.getTarget(), players)).append('\n');
        sb.append(key).append(".targetLocation=")
          .append(c.getTargetLocation() == null ? "-" : c.getTargetLocation().getId()).append('\n');
        sb.append(key).append(".warKind=")
          .append(c.getWarKind() == null ? "-" : c.getWarKind().name()).append('\n');
        sb.append(key).append(".resolved=").append(c.isResolved()).append('\n');
        sb.append(key).append(".winner=")
          .append(c.getWinner() == null ? "-" : playerKeyNamed(c.getWinner(), players)).append('\n');
        sb.append(key).append(".attackOccurred=").append(c.anyAttackOccurred()).append('\n');
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            if (!c.getParticipants().contains(p)) continue;
            sb.append(key).append(".side.").append(i).append('=')
              .append(c.isSupporting(p) ? "S" : (c.isOpposing(p) ? "O" : "?")).append('\n');
            zone(sb, key + ".committed." + i, c.getCommittedCards(p));
        }
    }

    /** Player key by identity against the table, falling back to the name. */
    private static String playerKeyNamed(Player p, List<Player> players) {
        if (p == null) return "-";
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i) == p) return "p" + i + "." + p.getName();
        }
        return p.getName();
    }

    /** Lower-case hex SHA-256 of a UTF-8 string. */
    private static String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (int i = 0; i < d.length; i++) {
                int v = d[i] & 0xFF;
                if (v < 0x10) sb.append('0');
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("B5-0008: SHA-256 unavailable", e);
        }
    }
}
