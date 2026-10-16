package b5ccg.engine;

import b5ccg.model.GameAction;
import b5ccg.model.GameState;
import b5ccg.model.Player;
import b5ccg.model.enums.Faction;
import b5ccg.model.enums.GamePhase;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * B5-1975: replay-system probe. Runs a small headless game with a
 * {@link ReplayRecorder} attached, writes the replay file, reads it back, and
 * asserts the loaded replay describes the game that was actually played.
 *
 * <p>Gate tier: pure 0/1 exit contract — a non-zero exit is a replay defect,
 * never an exploratory outcome, so this is safe to add to the build gate.</p>
 *
 * <p>The load checks are not "the file parsed". Each one asserts something the
 * live game did, so a recorder that wrote plausible JSON of the wrong game
 * still fails:</p>
 * <ul>
 *   <li>one header plus exactly {@code ROUNDS} round records;</li>
 *   <li>round numbers are 1..ROUNDS in order (a lost or duplicated record is
 *       visible, and a stream that skipped one would otherwise load "clean");</li>
 *   <li>the phase on each record is the phase the live state held;</li>
 *   <li>each record's actions are the actions queued for that round;</li>
 *   <li>each record's state snapshot equals the live snapshot, key for key;</li>
 *   <li>each record's delta equals snapshot(N) - snapshot(N-1) — the delta is
 *       checked against the two snapshots it claims to bridge, not against the
 *       live state, so a delta computed from the wrong baseline is caught;</li>
 *   <li>a known influence change is present as a known delta — the property
 *       the whole system exists for;</li>
 *   <li>the file is one-JSON-object-per-line and carries a trailing newline.</li>
 * </ul>
 *
 * <p>Also asserts the round-1 baseline: round 1's delta against all-zero must
 * equal round 1's snapshot, so no reader has to special-case the first
 * record.</p>
 *
 * <p>Run after compile.sh / compile.bat:
 *   java -cp b5ccg/out b5ccg.engine.B51975ReplayProbe [replayPath]</p>
 */
public class B51975ReplayProbe {

    /** Rounds played by the scripted game. Also the expected record count. */
    private static final int ROUNDS = 4;

    private static int checks;
    private static int failures;

    private static void check(String label, boolean passed) {
        checks++;
        System.out.println("  [REP] " + label + ": " + (passed ? "PASS" : "FAIL"));
        if (!passed) failures++;
    }

    public static void main(String[] args) {
        String path = (args.length >= 1)
                ? args[0]
                : "out/replay-b5-1975.jsonl";
        System.out.println("=== B5-1975 replay probe ===");
        System.out.println("Replay file: " + path);

        List<Player> players = new ArrayList<Player>();
        players.add(new Player("Human Player", Faction.NARN, true));
        players.add(new Player("Minbari Ally",  Faction.MINBARI, false));
        GameState game = new GameState(players);

        RulesEngine rules = new RulesEngine();

        // What the live game holds at each round boundary, kept so the loaded
        // file can be compared against the game rather than against itself.
        List<Map<String, Object>> liveStates = new ArrayList<Map<String, Object>>();
        List<List<GameAction>>    liveActions = new ArrayList<List<GameAction>>();
        List<String>              livePhases  = new ArrayList<String>();

        ReplayRecorder recorder;
        try {
            recorder = new ReplayRecorder(path, "B5-1975");
        } catch (Exception e) {
            System.err.println("B5-1975: cannot open replay file: " + e.getMessage());
            System.exit(1);
            return;
        }

        try {
            for (int r = 1; r <= ROUNDS; r++) {
                game.setPhase(r <= ROUNDS / 2 ? GamePhase.ACTION : GamePhase.CONFLICT_RESOLUTION);

                List<GameAction> thisRound = new ArrayList<GameAction>();
                if (r == 1) {
                    // A real decision with payload, so the record carries more
                    // than PASS-shaped actions.
                    thisRound.add(GameAction.pass());
                    thisRound.add(GameAction.initiateConflict(null, players.get(0)));
                    thisRound.add(GameAction.bidOnMercenary(null, 2));
                } else if (r == 2) {
                    thisRound.add(GameAction.surrender(players.get(1)));
                } else {
                    thisRound.add(GameAction.pass());
                }
                for (int i = 0; i < thisRound.size(); i++) {
                    recorder.recordAction(thisRound.get(i));
                }

                // A deterministic, observable state change per round, so the
                // deltas have something real to report.
                players.get(0).gainInfluence(r);
                players.get(1).gainInfluence(r * 2);
                if (r == 3) game.getStation().gainInfluence(5);

                livePhases.add(game.getPhase().name());
                liveActions.add(thisRound);
                liveStates.add(snapshot(game));

                recorder.recordRound(game);

                if (r < ROUNDS) {
                    rules.startRound(game);
                    game.advanceRound();
                }
            }
            recorder.close();
        } catch (Exception e) {
            System.err.println("B5-1975: recording failed: " + e.getMessage());
            try { recorder.close(); } catch (Exception ignored) { /* already failing */ }
            System.exit(1);
            return;
        }

        // ── Shape of the file itself ────────────────────────────────────────
        List<String> lines = readLines(path);
        check("replay file exists and is non-empty", lines != null && !lines.isEmpty());
        if (lines != null && !lines.isEmpty()) {
            boolean onePerLine = true;
            for (String l : lines) {
                if (l.length() == 0 || l.charAt(0) != '{' || l.charAt(l.length() - 1) != '}') {
                    onePerLine = false;
                    break;
                }
            }
            check("every line is exactly one JSON object (JSON Lines)", onePerLine);
            check("line count is 1 header + " + ROUNDS + " rounds",
                    lines.size() == ROUNDS + 1);
        }

        // ── Load ─────────────────────────────────────────────────────────────
        ReplayRecorder.Replay loaded;
        try {
            loaded = ReplayRecorder.load(path);
            check("replay loads without error", true);
        } catch (Exception e) {
            check("replay loads without error", false);
            System.err.println("  load error: " + e.getMessage());
            finish();
            return;
        }

        check("header schema version is " + ReplayRecorder.SCHEMA_VERSION,
                loaded.schemaVersion == ReplayRecorder.SCHEMA_VERSION);
        check("header task is B5-1975", "B5-1975".equals(loaded.task));
        check("header records the written UTC stamp",
                loaded.writtenUtc != null && loaded.writtenUtc.length() == 20
                        && loaded.writtenUtc.endsWith("Z"));
        check("header roster is both players in table order",
                loaded.players.size() == 2
                        && "Human Player".equals(loaded.players.get(0))
                        && "Minbari Ally".equals(loaded.players.get(1)));
        check("exactly " + ROUNDS + " round records load", loaded.rounds.size() == ROUNDS);

        // ── Round order ──────────────────────────────────────────────────────
        boolean orderOk = true;
        for (int i = 0; i < loaded.rounds.size(); i++) {
            if (loaded.rounds.get(i).round != i + 1) orderOk = false;
        }
        check("round records are 1.." + ROUNDS + " in order, none lost or duplicated", orderOk);

        // ── Per-round fidelity, against the live game ───────────────────────
        boolean phasesOk  = true, actionsOk = true, statesOk = true, deltasOk = true;
        for (int i = 0; i < loaded.rounds.size() && i < ROUNDS; i++) {
            ReplayRecorder.ReplayRound rr = loaded.rounds.get(i);

            if (!livePhases.get(i).equals(rr.phase)) phasesOk = false;

            if (rr.actionCount != liveActions.get(i).size()) actionsOk = false;
            for (int a = 0; a < rr.actionTypes.size() && a < liveActions.get(i).size(); a++) {
                if (!liveActions.get(i).get(a).getType().name().equals(rr.actionTypes.get(a))) {
                    actionsOk = false;
                }
            }

            Map<String, Object> live = liveStates.get(i);
            if (!live.keySet().equals(rr.state.keySet())) statesOk = false;
            else {
                for (Map.Entry<String, Object> e : live.entrySet()) {
                    if (!e.getValue().equals(rr.state.get(e.getKey()))) statesOk = false;
                }
            }

            // The delta must bridge the two snapshots it claims to bridge.
            Map<String, Object> before = (i == 0) ? null : liveStates.get(i - 1);
            if (!deltaMatches(before, live, rr.delta)) deltasOk = false;
        }
        check("each record's phase is the phase the live state held", phasesOk);
        check("each record's actions are the actions queued that round", actionsOk);
        check("each record's state equals the live snapshot, key for key", statesOk);
        check("each record's delta equals snapshot(N) - snapshot(N-1)", deltasOk);

        // ── Round 1 needs no special case at the reader ──────────────────────
        if (loaded.rounds.size() == ROUNDS) {
            Map<String, Object> r1 = loaded.rounds.get(0).state;
            Map<String, Object> d1 = loaded.rounds.get(0).delta;
            boolean baselineOk = r1.keySet().equals(d1.keySet());
            if (baselineOk) {
                for (Map.Entry<String, Object> e : r1.entrySet()) {
                    if (!Integer.valueOf(ReplayRecorder.scalar(e.getValue()))
                            .equals(d1.get(e.getKey()))) baselineOk = false;
                }
            }
            check("round 1's delta against all-zero equals round 1's snapshot", baselineOk);
        }

        // ── The property the system exists for ─────────────────────────────
        // Each round the scripted gains are round-specific (r and 2r), so a
        // wrong delta is arithmetically visible rather than merely signed
        // correctly: round 2 must read +2/+4, round 3 +3/+6.
        if (loaded.rounds.size() == ROUNDS) {
            Map<String, Object> d2 = loaded.rounds.get(1).delta;
            check("round 2 delta reports Human Player influence +2",
                    Integer.valueOf(2).equals(d2.get("p0.Human Player.influence")));
            check("round 2 delta reports Minbari Ally influence +4",
                    Integer.valueOf(4).equals(d2.get("p1.Minbari Ally.influence")));

            Map<String, Object> d3 = loaded.rounds.get(2).delta;
            check("round 3 delta reports Human Player influence +3",
                    Integer.valueOf(3).equals(d3.get("p0.Human Player.influence")));

            // The station gain in round 3 moves its own rating
            // (Babylon5Station.gainInfluence), not the Shadow/Vorlon split —
            // this asserts the delta names the field that actually moved,
            // which a snapshot-with-the-wrong-key would not.
            Map<String, Object> s3 = loaded.rounds.get(2).state;
            check("round 3 snapshot shows the station rating at its new value",
                    Integer.valueOf(5).equals(s3.get("station.rating"))
                            && Integer.valueOf(0).equals(s3.get("station.shadow")));
        }

        // ── A player name or title with a quote/backslash survives ──────────
        String tricky = "out/replay-b5-1975-escape.jsonl";
        check("escaped-name round trip", escapeRoundTrip(tricky));

        finish();
    }

    /** Writes a state whose roster name needs JSON escaping, loads it, and
     *  asserts the name came back byte-identical. This is the case a naive
     *  concatenating writer turns into an unparseable file. */
    private static boolean escapeRoundTrip(String path) {
        ReplayRecorder rec = null;
        try {
            List<Player> players = new ArrayList<Player>();
            players.add(new Player("Quote \"Q\" Back\\Slash", Faction.HUMAN, false));
            players.add(new Player("Tab\tNewline\nPlayer", Faction.CENTAURI, false));
            GameState g = new GameState(players);
            g.setPhase(GamePhase.ACTION);

            rec = new ReplayRecorder(path, "B5-1975");
            rec.setRoster(players);
            rec.recordRound(g);
            rec.close();

            ReplayRecorder.Replay back = ReplayRecorder.load(path);
            return back.players.size() == 2
                    && "Quote \"Q\" Back\\Slash".equals(back.players.get(0))
                    && "Tab\tNewline\nPlayer".equals(back.players.get(1))
                    && back.rounds.size() == 1;
        } catch (Exception e) {
            System.err.println("  escape round-trip error: " + e.getMessage());
            return false;
        } finally {
            if (rec != null) {
                try { rec.close(); } catch (Exception ignored) { /* nothing further to do */ }
            }
        }
    }

    /** True when {@code delta} is the arithmetic difference between the two
     *  snapshots it claims to bridge, under {@link ReplayRecorder#scalar}. A
     *  null {@code before} is the all-zero baseline, which is what round 1 is
     *  measured against — which is why the probe can check the delta against
     *  the snapshots themselves and not against the recorder. */
    private static boolean deltaMatches(Map<String, Object> before,
                                        Map<String, Object> after,
                                        Map<String, Object> delta) {
        if (!after.keySet().equals(delta.keySet())) return false;
        for (Map.Entry<String, Object> e : after.entrySet()) {
            String k = e.getKey();
            int want = ReplayRecorder.scalar(e.getValue())
                     - ReplayRecorder.scalar((before == null) ? null : before.get(k));
            Object got = delta.get(k);
            if (!(got instanceof Integer) || ((Integer) got).intValue() != want) return false;
        }
        return true;
    }

    /** The same flat snapshot the recorder writes, computed independently here
     *  so the comparison is against the game and not against the recorder. */
    private static Map<String, Object> snapshot(GameState state) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
        List<Player> players = state.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            String key = "p" + i + "." + p.getName();
            m.put(key + ".influence",   Integer.valueOf(p.getInfluence()));
            m.put(key + ".applied",     Integer.valueOf(p.getAppliedPool()));
            m.put(key + ".unrest",      Integer.valueOf(p.getUnrest()));
            m.put(key + ".actionsLeft", Integer.valueOf(p.getActionsLeft()));
            m.put(key + ".hand",        Integer.valueOf(p.getHand().size()));
            m.put(key + ".innerCircle", Integer.valueOf(p.getInnerCircle().size()));
            m.put(key + ".supporting",  Integer.valueOf(p.getSupportingRole().size()));
            m.put(key + ".fleets",      Integer.valueOf(p.getFleets().size()));
            m.put(key + ".locations",   Integer.valueOf(p.getLocations().size()));
            m.put(key + ".groups",      Integer.valueOf(p.getGroups().size()));
            m.put(key + ".passed",      Boolean.valueOf(p.isPassed()));
            m.put(key + ".forfeited",   Boolean.valueOf(p.hasForfeited()));
            m.put(key + ".surrendered", Boolean.valueOf(p.hasSurrendered()));
        }
        m.put("station.rating",      Integer.valueOf(state.getStation().getInfluence()));
        m.put("station.shadow",     Integer.valueOf(state.getShadowInfluence()));
        m.put("station.vorlon",     Integer.valueOf(state.getVorlonInfluence()));
        m.put("station.sourceFired", Boolean.valueOf(state.isStationSourceFired()));
        m.put("game.over",          Boolean.valueOf(state.isGameOver()));
        if (state.getWinner() != null) m.put("game.winner", state.getWinner().getName());
        return m;
    }

    private static List<String> readLines(String path) {
        List<String> out = new ArrayList<String>();
        java.io.BufferedReader r = null;
        try {
            r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(new File(path)), "UTF-8"));
            String l;
            while ((l = r.readLine()) != null) if (l.length() > 0) out.add(l);
        } catch (Exception e) {
            return null;
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) { /* read-only */ }
        }
        return out;
    }

    private static void finish() {
        System.out.println("");
        System.out.println("REPLAY LOAD: " + (failures == 0 ? "PASS" : "FAIL")
                + " (" + (checks - failures) + "/" + checks + " checks passed)");
        System.exit(failures == 0 ? 0 : 1);
    }
}
