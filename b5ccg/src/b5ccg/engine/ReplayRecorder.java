package b5ccg.engine;

import b5ccg.model.GameAction;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.Card;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * B5-1975: replay system. Serializes a {@link GameState} snapshot once per
 * round to a replay file, and reads that file back.
 *
 * <p><b>Format: JSON Lines</b> — one self-contained JSON object per line, UTF-8,
 * newline-terminated. A file is a stream of records, so a truncated or
 * half-written tail costs at most the last round rather than the whole file,
 * which is the property a per-round snapshot wants.</p>
 *
 * <p>Record sequence:</p>
 * <ul>
 *   <li>one <code>header</code> record (schema version, task, write time, and
 *       the player roster in table order), then</li>
 *   <li>one <code>round</code> record per round: <code>round</code> (number),
 *       <code>phase</code>, <code>actions</code> (every
 *       {@link GameAction} offered since the previous round record),
 *       <code>state</code> (the snapshot) and <code>stateDelta</code> (what
 *       moved since the previous round record).</li>
 * </ul>
 *
 * <p>Round 1's delta is measured against an all-zero baseline, so every delta
 * in the file is a real arithmetic difference between two snapshots rather
 * than a special case the reader has to know about.</p>
 *
 * <p><b>Scope note.</b> This class reads the live model through its public
 * accessors only and mutates nothing: recording a round is observation. It is
 * an engine-side surface, so it lives in <code>b5ccg.engine</code>; a UI or
 * replay-player surface would call {@link #recordAction} /
 * {@link #recordRound} and {@link #load}. No rule, effect or victory path is
 * touched.</p>
 *
 * <p><b>Java 6.</b> No diamond, no lambdas, no try-with-resources, no
 * try-with-anything: readers and writers are closed in <code>finally</code>
 * blocks like the rest of the engine.</p>
 *
 * <p>Wiring (harness-side, deliberately not auto-wired into
 * {@code GameController.runGame} so no existing behaviour changes):</p>
 * <pre>
 *   ReplayRecorder rec = new ReplayRecorder("out/replay-b5-1975.jsonl", "B5-1975");
 *   rec.recordAction(action);              // as the round proceeds
 *   rec.recordRound(state);                // at the round boundary
 *   rec.close();
 *   List&lt;ReplayRecorder.ReplayRound&gt; loaded = ReplayRecorder.load("out/replay-b5-1975.jsonl");
 * </pre>
 */
public class ReplayRecorder {

    /** Schema version stamped into every header record. Bump on a breaking
     *  change to the record shape; readers should refuse a version they do not
     *  know rather than silently mis-reading a file. */
    public static final int SCHEMA_VERSION = 1;

    /** Record-type discriminators. */
    public static final String REC_HEADER = "header";
    public static final String REC_ROUND  = "round";

    private final File    file;
    private final String  taskId;
    private Writer        out;
    private boolean      closed;
    /** Whether the header record has been written. Kept separate from
     *  {@link #out}: `out` answers "is the writer open", this answers "has the
     *  header been emitted", and conflating them silently skips the header on
     *  every file. */
    private boolean      headerWritten;

    /** Player names in table order; the index is each snapshot's stable key,
     *  so a delta can be a positional walk rather than a name lookup. */
    private final List<String> roster = new ArrayList<String>();

    /** Actions offered since the last {@link #recordRound}. */
    private final List<GameAction> pending = new ArrayList<GameAction>();

    /** The previous round's snapshot, null before the first round record. */
    private Map<String, Object> previous;

    public ReplayRecorder(String path, String taskId) throws IOException {
        this.file = new File(path);
        this.taskId = taskId;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("B5-1975: cannot create replay directory " + parent.getPath());
        }
        this.out = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
    }

    /** Player order this recorder keys snapshots by. Must be called before the
     *  header is written (i.e. before the first round record) or not at all. */
    public void setRoster(List<Player> players) {
        if (headerWritten) return;
        roster.clear();
        for (Player p : players) roster.add(p.getName());
    }

    /** Queues an action for the round currently being played. */
    public void recordAction(GameAction action) {
        if (action == null) return;
        pending.add(action);
    }

    /** Writes one round record for the given state and clears the pending
     *  actions. Call once per round, at the round boundary. */
    public void recordRound(GameState state) throws IOException {
        if (closed) throw new IOException("B5-1975: recorder already closed");
        if (state == null) return;
        if (roster.isEmpty()) setRoster(state.getPlayers());
        if (!headerWritten) { writeHeader(state); headerWritten = true; }

        Map<String, Object> snapshot = snapshot(state);
        Map<String, Object> delta    = delta(previous, snapshot);

        StringBuilder sb = new StringBuilder();
        sb.append("{\"record\":\"").append(REC_ROUND).append("\"");
        sb.append(",\"round\":").append(state.getRoundNumber());
        sb.append(",\"phase\":\"").append(esc(state.getPhase().name())).append("\"");
        sb.append(",\"actions\":[");
        for (int i = 0; i < pending.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(actionJson(pending.get(i)));
        }
        sb.append(']');
        sb.append(",\"state\":").append(mapJson(snapshot));
        sb.append(",\"stateDelta\":").append(mapJson(delta));
        sb.append('}');
        writeLine(sb.toString());

        previous = snapshot;
        pending.clear();
    }

    private void writeHeader(GameState state) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"record\":\"").append(REC_HEADER).append("\"");
        sb.append(",\"schemaVersion\":").append(SCHEMA_VERSION);
        sb.append(",\"task\":\"").append(esc(taskId)).append("\"");
        sb.append(",\"writtenUtc\":\"").append(nowUtc()).append("\"");
        sb.append(",\"players\":[");
        for (int i = 0; i < roster.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(esc(roster.get(i))).append('"');
        }
        sb.append(']');
        sb.append(",\"startingRound\":").append(state.getRoundNumber());
        sb.append('}');
        writeLine(sb.toString());
    }

    // ── Snapshots ───────────────────────────────────────────────────────────

    /** The serialisable surface of a state: one row per roster player, plus the
     *  station and win condition. Deliberately a flat map of scalars — the
     *  replay is for comparison across runs, and a flat map is diffable. */
    private Map<String, Object> snapshot(GameState state) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        List<Player> players = state.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            String key = key(i, p.getName());
            m.put(key + ".influence",  Integer.valueOf(p.getInfluence()));
            m.put(key + ".applied",    Integer.valueOf(p.getAppliedPool()));
            m.put(key + ".unrest",     Integer.valueOf(p.getUnrest()));
            m.put(key + ".actionsLeft",Integer.valueOf(p.getActionsLeft()));
            m.put(key + ".hand",       Integer.valueOf(p.getHand().size()));
            m.put(key + ".innerCircle",Integer.valueOf(p.getInnerCircle().size()));
            m.put(key + ".supporting", Integer.valueOf(p.getSupportingRole().size()));
            m.put(key + ".fleets",     Integer.valueOf(p.getFleets().size()));
            m.put(key + ".locations",  Integer.valueOf(p.getLocations().size()));
            m.put(key + ".groups",     Integer.valueOf(p.getGroups().size()));
            m.put(key + ".passed",     Boolean.valueOf(p.isPassed()));
            m.put(key + ".forfeited",  Boolean.valueOf(p.hasForfeited()));
            m.put(key + ".surrendered",Boolean.valueOf(p.hasSurrendered()));
        }
        // The station's own rating (Babylon5Station.getInfluence) is separate
        // from the Shadow/Vorlon split and moves independently of it, so it is
        // its own key rather than folded into either.
        m.put("station.rating",      Integer.valueOf(state.getStation().getInfluence()));
        m.put("station.shadow", Integer.valueOf(state.getShadowInfluence()));
        m.put("station.vorlon", Integer.valueOf(state.getVorlonInfluence()));
        m.put("station.sourceFired", Boolean.valueOf(state.isStationSourceFired()));
        m.put("game.over", Boolean.valueOf(state.isGameOver()));
        if (state.getWinner() != null) m.put("game.winner", state.getWinner().getName());
        return m;
    }

    /** Arithmetic difference between two snapshots, with **every delta value
     *  an {@link Integer}**. A mixed Integer/Boolean/String delta would force
     *  every reader to branch on value type; normalising here keeps "what
     *  moved by how much" a single numeric field.
     *
     *  <p>Normalisation, applied to both operands before subtracting:</p>
     *  <ul>
     *    <li>{@link Integer} — itself (the common case: influence, counts).</li>
     *    <li>{@link Boolean} — 1 for true, 0 for false, so a flag flipping reads
     *        as +/-1 in the same units as everything else.</li>
     *    <li>anything else (the single {@code game.winner} name) — 1 when
     *        present, 0 when absent, so a winner's arrival is +1 and a key
     *        leaving is -1 rather than the transition being hidden.</li>
     *  </ul>
     *
     *  <p>An absent key is 0, which is also what makes round 1's delta exactly
     *  its own snapshot with no reader-side special case.</p>
     */
    private Map<String, Object> delta(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        List<String> keys = new ArrayList<String>();
        if (before != null) keys.addAll(before.keySet());
        for (String k : after.keySet()) if (!keys.contains(k)) keys.add(k);
        for (String k : keys) {
            Object a = (before == null) ? null : before.get(k);
            Object b = after.get(k);
            out.put(k, Integer.valueOf(scalar(b) - scalar(a)));
        }
        return out;
    }

    /** One snapshot value reduced to the integer a delta is computed in: numbers
     *  as themselves, {@link Boolean} as 1/0, any other present value as 1,
     *  and absent (null) as 0. Public because the delta contract is the
     *  record format's contract, and a verifier has to reproduce it rather
     *  than re-guess it. */
    public static int scalar(Object v) {
        if (v == null)             return 0;
        if (v instanceof Integer)  return ((Integer) v).intValue();
        if (v instanceof Boolean)  return ((Boolean) v).booleanValue() ? 1 : 0;
        return 1;
    }

    /** Roster key. Uses the roster index so two players with the same name
     *  cannot collide; falls back to the bare name if the roster is empty. */
    private String key(int index, String name) {
        int at = roster.indexOf(name);
        int n = (at >= 0) ? at : index;
        return "p" + n + "." + name;
    }

    // ── JSON writing ────────────────────────────────────────────────────────

    private void writeLine(String line) throws IOException {
        out.write(line);
        out.write("\n");
        out.flush();
    }

    private String actionJson(GameAction a) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"").append(a.getType().name()).append("\"");
        putCard(sb, "card", a.getCard());
        if (a.getTarget() != null) {
            sb.append(",\"target\":\"").append(esc(a.getTarget().getName())).append("\"");
        }
        putCard(sb, "leader", a.getLeader());
        putCard(sb, "targetCard", a.getTargetCard());
        if (a.getRotateKind() != null) {
            sb.append(",\"rotateKind\":\"").append(a.getRotateKind().name()).append("\"");
        }
        sb.append(",\"hidden\":").append(a.isHidden());
        sb.append(",\"amount\":").append(a.getAmount());
        sb.append('}');
        return sb.toString();
    }

    private void putCard(StringBuilder sb, String field, Card c) {
        if (c == null) return;
        sb.append(",\"").append(field).append("\":{\"id\":\"")
          .append(esc(c.getId())).append("\",\"title\":\"")
          .append(esc(c.getTitle())).append("\"}");
    }

    private String mapJson(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(esc(e.getKey())).append("\":");
            sb.append(valueJson(e.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    private String valueJson(Object v) {
        if (v == null)       return "null";
        if (v instanceof Integer) return v.toString();
        if (v instanceof Boolean) return v.toString();
        return "\"" + esc(v.toString()) + "\"";
    }

    /** Minimal JSON string escaping. Control characters go out as \\uXXXX
     *  (which is also what keeps a JSON Lines file one-object-per-line when a
     *  title or player name carries a newline). */
    static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (ch < 0x20) {
                        String h = Integer.toHexString(ch);
                        sb.append("\\u");
                        for (int k = h.length(); k < 4; k++) sb.append('0');
                        sb.append(h);
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }

    private static String nowUtc() {
        java.text.SimpleDateFormat f =
            new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new java.util.Date());
    }

    /** Flushes and closes the file. Idempotent. */
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        if (out != null) { out.flush(); out.close(); out = null; }
    }

    // ── Loading ─────────────────────────────────────────────────────────────

    /** One round record as read back from a replay file. */
    public static class ReplayRound {
        public final int                          round;
        public final String                       phase;
        public final int                          actionCount;
        public final List<String>                 actionTypes;
        public final Map<String, Object>          state;
        public final Map<String, Object>          delta;

        ReplayRound(int round, String phase, int actionCount, List<String> actionTypes,
                    Map<String, Object> state, Map<String, Object> delta) {
            this.round       = round;
            this.phase       = phase;
            this.actionCount = actionCount;
            this.actionTypes = actionTypes;
            this.state       = state;
            this.delta       = delta;
        }
    }

    /** A loaded replay file: its header fields plus every round record. */
    public static class Replay {
        public int                      schemaVersion;
        public String                   task      = "";
        public String                   writtenUtc = "";
        public final List<String>       players   = new ArrayList<String>();
        public final List<ReplayRound>  rounds    = new ArrayList<ReplayRound>();
    }

    /**
     * Reads a replay file back. Throws on a missing file, a schema version this
     * build does not know, or a malformed line — a load that silently skipped
     * a broken line would report a short game as a whole one.
     */
    public static Replay load(String path) throws IOException {
        File f = new File(path);
        if (!f.exists()) throw new IOException("B5-1975: no replay file at " + path);
        Replay replay = new Replay();
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            int lineNo = 0;
            while ((line = r.readLine()) != null) {
                lineNo++;
                String trimmed = line.trim();
                if (trimmed.length() == 0) continue;
                if (trimmed.charAt(0) != '{' || trimmed.charAt(trimmed.length() - 1) != '}') {
                    throw new IOException("B5-1975: replay line " + lineNo
                            + " is not a single JSON object (JSON Lines violation)");
                }
                Map<String, Object> obj = MiniJson.parseObject(trimmed);
                String kind = str(obj.get("record"));
                if (REC_HEADER.equals(kind)) {
                    Integer v = (Integer) obj.get("schemaVersion");
                    if (v == null) throw new IOException("B5-1975: header without schemaVersion");
                    replay.schemaVersion = v.intValue();
                    if (v.intValue() != SCHEMA_VERSION) {
                        throw new IOException("B5-1975: replay schema " + v.intValue()
                                + " != this build's " + SCHEMA_VERSION);
                    }
                    replay.task      = str(obj.get("task"));
                    replay.writtenUtc = str(obj.get("writtenUtc"));
                    List<?> ps = (List<?>) obj.get("players");
                    if (ps != null) for (Object p : ps) replay.players.add(str(p));
                } else if (REC_ROUND.equals(kind)) {
                    replay.rounds.add(readRound(obj));
                } else {
                    throw new IOException("B5-1975: unknown record type '" + kind
                            + "' on replay line " + lineNo);
                }
            }
        } finally {
            if (r != null) r.close();
        }
        return replay;
    }

    @SuppressWarnings("unchecked")
    private static ReplayRound readRound(Map<String, Object> obj) throws IOException {
        Integer rn = (Integer) obj.get("round");
        if (rn == null) throw new IOException("B5-1975: round record without a round number");
        String phase = str(obj.get("phase"));
        List<String> types = new ArrayList<String>();
        int count = 0;
        Object acts = obj.get("actions");
        if (acts instanceof List) {
            List<Object> l = (List<Object>) acts;
            count = l.size();
            for (Object a : l) {
                if (a instanceof Map) types.add(str(((Map<String, Object>) a).get("type")));
            }
        }
        Map<String, Object> state = (obj.get("state")    instanceof Map) ? (Map<String, Object>) obj.get("state")    : new LinkedHashMap<String, Object>();
        Map<String, Object> delta = (obj.get("stateDelta") instanceof Map) ? (Map<String, Object>) obj.get("stateDelta") : new LinkedHashMap<String, Object>();
        return new ReplayRound(rn.intValue(), phase, count, types, state, delta);
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }
}
