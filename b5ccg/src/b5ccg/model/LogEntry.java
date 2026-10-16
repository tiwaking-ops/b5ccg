package b5ccg.model;

import b5ccg.model.enums.GamePhase;

public class LogEntry {
    private final int round;
    private final GamePhase phase;
    private final String actor;
    private final String action;
    private final String target;
    private final Result result;
    private final String detail;

    public LogEntry(int round, GamePhase phase, String actor, String action,
                    String target, Result result, String detail) {
        this.round = round;
        this.phase = phase;
        this.actor = actor != null ? actor : "";
        this.action = action != null ? action : "";
        this.target = target != null ? target : "";
        this.result = result != null ? result : Result.INFO;
        this.detail = detail != null ? detail : "";
    }

    public int getRound() { return round; }
    public GamePhase getPhase() { return phase; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getTarget() { return target; }
    public Result getResult() { return result; }
    public String getDetail() { return detail; }

    public String render() {
        return "[R" + round + "] " + detail;
    }

    public static enum Result {
        OK,
        REFUSED,
        INFO
    }
}