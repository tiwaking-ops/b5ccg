package b5ccg.model;

import b5ccg.model.enums.CardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * B5-0336 — conflict participation restrictions (proposal §3.2,
 * docs/proposals/conflict-participation-restrictions-data-proposal.md).
 *
 * Immutable value object parsed from the optional top-level "participation"
 * key on CONFLICT cards. Absent/null participation = open participation =
 * the pre-B5-0336 behavior everywhere (proposal §3.4 backward compatibility).
 *
 * Each key maps to exactly one engine-checkable predicate (proposal §5.1):
 *   players               who may join/commit (ALL, INITIATOR_TARGET,
 *                         INITIATOR, TARGET)
 *   requiresTarget        initiation is illegal without a declared target
 *   cardTypes             card kinds allowed to commit (superset filter)
 *   fleetSubtypes         fleet classes allowed when cardTypes has FLEET;
 *                         a fleet without a fleetClass cannot be proven
 *                         eligible and is excluded (proposal §3.6)
 *   perPlayerQuota        max committed cards per player per card kind
 *   leadersIncluded       a character may commit alongside an allowed fleet
 *                         that the player already committed
 *   mustCommitAmbassador  eligible players must commit their ambassador
 *   allPlayersMustCommit  every player must commit N cards of one kind
 *   mustTakeSide          every other player must commit cards of any kind
 *
 * Unknown vocabulary values are data errors: they are reported loudly on
 * System.err and skipped (proposal §5.2), never silently coerced.
 */
public class Participation {

    private final String                 players;
    private final boolean                requiresTarget;
    private final Set<CardType>          cardTypes;
    private final Set<String>            fleetSubtypes;
    private final Map<CardType, Integer> perPlayerQuota;
    private final boolean                leadersIncluded;
    private final boolean                mustCommitAmbassador;
    private final CardType               allPlayersCardType;
    private final int                    allPlayersCount;
    private final boolean                mustTakeSide;
    private final boolean                restricted;

    private Participation(String players, boolean requiresTarget,
                          List<CardType> cardTypes, List<String> fleetSubtypes,
                          Map<CardType, Integer> perPlayerQuota,
                          boolean leadersIncluded, boolean mustCommitAmbassador,
                          CardType allPlayersCardType, int allPlayersCount,
                          boolean mustTakeSide) {
        this.players              = players;
        this.requiresTarget       = requiresTarget;
        this.cardTypes            = Collections.unmodifiableSet(
                                        new LinkedHashSet<CardType>(cardTypes));
        this.fleetSubtypes        = Collections.unmodifiableSet(
                                        new LinkedHashSet<String>(fleetSubtypes));
        this.perPlayerQuota       = Collections.unmodifiableMap(perPlayerQuota);
        this.leadersIncluded      = leadersIncluded;
        this.mustCommitAmbassador = mustCommitAmbassador;
        this.allPlayersCardType   = allPlayersCardType;
        this.allPlayersCount      = allPlayersCount;
        this.mustTakeSide         = mustTakeSide;
        this.restricted =
                (players != null && !"ALL".equals(players))
                || requiresTarget
                || !this.cardTypes.isEmpty()
                || !this.fleetSubtypes.isEmpty()
                || !this.perPlayerQuota.isEmpty()
                || mustCommitAmbassador
                || allPlayersCardType != null
                || mustTakeSide;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Raw players value; null = ALL (the rulebook default). */
    public String                 getPlayers()             { return players; }
    public boolean                isRequiresTarget()       { return requiresTarget; }
    public Set<CardType>          getCardTypes()           { return cardTypes; }
    public Set<String>            getFleetSubtypes()       { return fleetSubtypes; }
    public Map<CardType, Integer> getPerPlayerQuota()      { return perPlayerQuota; }
    public boolean                isLeadersIncluded()      { return leadersIncluded; }
    public boolean                isMustCommitAmbassador() { return mustCommitAmbassador; }
    public boolean                hasAllPlayersMustCommit(){ return allPlayersCardType != null; }
    public CardType               getAllPlayersCardType()  { return allPlayersCardType; }
    public int                    getAllPlayersCount()     { return allPlayersCount; }
    public boolean                isMustTakeSide()         { return mustTakeSide; }

    /** False only when every dimension carries its default (open conflict). */
    public boolean isRestricted() { return restricted; }

    /**
     * True when p is among the players allowed to participate in a conflict
     * with this restriction. target may be null for conflicts that were
     * initiated without one (legal whenever requiresTarget is false).
     */
    public boolean isPlayerAllowed(Player initiator, Player target, Player p) {
        if (players == null || "ALL".equals(players)) return true;
        if ("INITIATOR".equals(players))       return p == initiator;
        if ("TARGET".equals(players))          return p != null && p == target;
        if ("INITIATOR_TARGET".equals(players)) return p == initiator || p == target;
        System.err.println("B5-0336: unknown participation players value: "
                + players + " — treating as ALL");
        return true;
    }

    /**
     * True when card c passes the cardTypes and fleetSubtypes filters.
     * A FLEET without a fleetClass is excluded from any fleetSubtypes
     * filter (proposal §3.6: "cannot be proven eligible").
     */
    public boolean allowsCardType(Card c) {
        if (!cardTypes.isEmpty() && !cardTypes.contains(c.getType())) return false;
        if (c instanceof FleetCard && !fleetSubtypes.isEmpty()) {
            String fc = ((FleetCard) c).getFleetClass();
            if (fc == null || !fleetSubtypes.contains(fc.toUpperCase())) return false;
        }
        return true;
    }

    /** Quota for the card's kind, or null when the kind has no quota. */
    public Integer quotaFor(Card c) { return perPlayerQuota.get(c.getType()); }

    // ── Parsing (self-contained mini JSON-object reader, Java 6) ──────────────

    /**
     * Parses the raw JSON fragment of one participation object, e.g.
     * {"players":"INITIATOR_TARGET","cardTypes":["FLEET"]}. The fragment is
     * the balanced-brace value DeckLoader captured for the key.
     */
    public static Participation parse(String json) {
        if (json == null) throw new IllegalArgumentException("participation is null");
        String s = json.trim();
        if (!s.startsWith("{") || !s.endsWith("}")) {
            throw new IllegalArgumentException("participation must be a JSON object: " + json);
        }
        String body = s.substring(1, s.length() - 1);

        String                 players    = null;
        boolean                reqTarget  = false;
        List<CardType>         types      = new ArrayList<CardType>();
        List<String>           subtypes   = new ArrayList<String>();
        Map<CardType, Integer> quota      = new LinkedHashMap<CardType, Integer>();
        boolean                leaders    = false;
        boolean                mustAmb    = false;
        CardType               allType    = null;
        int                    allCount   = 0;
        boolean                mustSide   = false;

        Cursor c = new Cursor();
        int i = 0;
        while (true) {
            int ks = body.indexOf('"', i);
            if (ks < 0) break;
            int ke = body.indexOf('"', ks + 1);
            if (ke < 0) throw err(body);
            String key = body.substring(ks + 1, ke);
            int colon = body.indexOf(':', ke + 1);
            if (colon < 0) throw err(body);
            c.i = colon + 1;
            skipWs(body, c);

            if (key.equals("players")) {
                readString(body, c);
                players = c.str;
            } else if (key.equals("requiresTarget")) {
                readBool(body, c);
                reqTarget = c.b;
            } else if (key.equals("cardTypes")) {
                readStringArray(body, c);
                for (String v : c.list) {
                    CardType t = cardType(v);
                    if (t != null) types.add(t);
                }
            } else if (key.equals("fleetSubtypes")) {
                readStringArray(body, c);
                for (String v : c.list) subtypes.add(v.trim().toUpperCase());
            } else if (key.equals("perPlayerQuota")) {
                readObjectPairs(body, c);
                for (Map.Entry<String, String> e : c.map.entrySet()) {
                    CardType t = cardType(e.getKey());
                    if (t == null) continue;
                    try { quota.put(t, Integer.valueOf(Integer.parseInt(e.getValue().trim()))); }
                    catch (NumberFormatException nfe) {
                        System.err.println("B5-0336: bad perPlayerQuota value for "
                                + e.getKey() + ": " + e.getValue());
                    }
                }
            } else if (key.equals("leadersIncluded")) {
                readBool(body, c);
                leaders = c.b;
            } else if (key.equals("mustCommitAmbassador")) {
                readBool(body, c);
                mustAmb = c.b;
            } else if (key.equals("allPlayersMustCommit")) {
                readObjectPairs(body, c);
                String t = c.map.get("cardType");
                if (t != null) allType = cardType(t);
                String n = c.map.get("count");
                if (n != null) {
                    try { allCount = Integer.parseInt(n.trim()); }
                    catch (NumberFormatException nfe) {
                        System.err.println("B5-0336: bad allPlayersMustCommit count: " + n);
                    }
                }
            } else if (key.equals("mustTakeSide")) {
                readBool(body, c);
                mustSide = c.b;
            } else {
                System.err.println("B5-0336: unknown participation key skipped: " + key);
                skipValue(body, c);
            }
            i = c.i;
            // Consume one separating comma if present.
            while (i < body.length()
                    && (Character.isWhitespace(body.charAt(i)) || body.charAt(i) == ',')) i++;
        }

        if (allType != null && mustSide) {
            System.err.println("B5-0336: both allPlayersMustCommit and mustTakeSide"
                    + " present — they are alternatives (proposal §5.4); keeping both");
        }
        return new Participation(players, reqTarget, types, subtypes, quota,
                leaders, mustAmb, allType, allCount, mustSide);
    }

    private static IllegalArgumentException err(String body) {
        return new IllegalArgumentException("malformed participation object: " + body);
    }

    private static void skipWs(String s, Cursor c) {
        while (c.i < s.length() && Character.isWhitespace(s.charAt(c.i))) c.i++;
    }

    private static CardType cardType(String name) {
        try { return CardType.valueOf(name.trim().toUpperCase()); }
        catch (IllegalArgumentException e) {
            System.err.println("B5-0336: unknown cardType in participation: "
                    + name + " — element skipped");
            return null;
        }
    }

    private static void readString(String s, Cursor c) {
        skipWs(s, c);
        int i = c.i;
        if (i >= s.length() || s.charAt(i) != '"') throw err(s);
        StringBuilder sb = new StringBuilder();
        i++;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) { sb.append(s.charAt(i + 1)); i += 2; continue; }
            if (ch == '"') { i++; break; }
            sb.append(ch);
            i++;
        }
        c.i = i;
        c.str = sb.toString();
    }

    private static void readBool(String s, Cursor c) {
        skipWs(s, c);
        if (s.startsWith("true", c.i))       { c.b = true;  c.i += 4; }
        else if (s.startsWith("false", c.i)) { c.b = false; c.i += 5; }
        else throw err(s);
    }

    private static void readStringArray(String s, Cursor c) {
        skipWs(s, c);
        if (c.i >= s.length() || s.charAt(c.i) != '[') throw err(s);
        c.i++;
        c.list.clear();
        while (true) {
            skipWs(s, c);
            if (c.i >= s.length()) throw err(s);
            if (s.charAt(c.i) == ']') { c.i++; break; }
            readString(s, c);
            c.list.add(c.str);
            skipWs(s, c);
            if (c.i < s.length() && s.charAt(c.i) == ',') c.i++;
        }
    }

    /** Reads one flat { "k": string-or-primitive, ... } object into c.map. */
    private static void readObjectPairs(String s, Cursor c) {
        skipWs(s, c);
        if (c.i >= s.length() || s.charAt(c.i) != '{') throw err(s);
        c.i++;
        c.map.clear();
        while (true) {
            skipWs(s, c);
            if (c.i >= s.length()) throw err(s);
            if (s.charAt(c.i) == '}') { c.i++; break; }
            readString(s, c);
            String key = c.str;
            skipWs(s, c);
            if (c.i >= s.length() || s.charAt(c.i) != ':') throw err(s);
            c.i++;
            skipWs(s, c);
            if (c.i < s.length() && s.charAt(c.i) == '"') {
                readString(s, c);
                c.map.put(key, c.str);
            } else {
                int end = c.i;
                while (end < s.length() && s.charAt(end) != ',' && s.charAt(end) != '}') end++;
                c.map.put(key, s.substring(c.i, end).trim());
                c.i = end;
            }
            skipWs(s, c);
            if (c.i < s.length() && s.charAt(c.i) == ',') c.i++;
        }
    }

    /** Skips one value of any shape (used for unknown keys). */
    private static void skipValue(String s, Cursor c) {
        skipWs(s, c);
        if (c.i >= s.length()) return;
        char ch = s.charAt(c.i);
        if (ch == '"') {
            readString(s, c);
        } else if (ch == '[' || ch == '{') {
            char open = ch, close = (ch == '[') ? ']' : '}';
            int depth = 0;
            while (c.i < s.length()) {
                char v = s.charAt(c.i);
                if (v == '"') { readString(s, c); continue; }
                if (v == open) depth++;
                else if (v == close) {
                    depth--;
                    c.i++;
                    if (depth == 0) break;
                    continue;
                }
                c.i++;
            }
        } else {
            while (c.i < s.length() && s.charAt(c.i) != ',' && s.charAt(c.i) != '}') c.i++;
        }
    }

    /** Mutable parse cursor. */
    private static final class Cursor {
        int    i;
        String str;
        boolean b;
        final List<String>         list = new ArrayList<String>();
        final Map<String, String>  map  = new LinkedHashMap<String, String>();
    }
}
