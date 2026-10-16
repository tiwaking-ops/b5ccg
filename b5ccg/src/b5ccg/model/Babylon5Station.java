package b5ccg.model;

/** B5-0340 — the Babylon 5 station as a non-player influence force
 *  (rulebook :153 "Influence": "As Babylon 5's influence grows, the galaxy
 *  becomes a safer place"; B5-0354 research report).
 *
 * The rulebook prints no starting rating and no drift schedule, and no card
 * in the current data moves station influence (B5-0354: grep "station" = 0
 * in resources/). The start value is therefore a recorded design decision
 * (DECISIONS B5-0340): start 0, no scheduled drift — Standard Victory
 * condition 2 (rulebook :176) stays inert until a real station-influence
 * source exists. The named constants keep the decision greppable.
 */
public class Babylon5Station {

    /** Design decision (B5-0354 research, DECISIONS B5-0340): the rulebook
     *  names no start value; 0 is the most conservative choice. */
    public static final int STATION_START_INFLUENCE = 0;

    /** Standard Victory condition 2 threshold (rulebook :176). */
    public static final int CONDITION_2_THRESHOLD = 20;

    /** Maximum rating value (0428 proposal: 0–100 scale). */
    public static final int MAX_RATING = 100;

    /** 0428 proposal: shadow-active threshold. */
    public static final int SHADOW_ACTIVE_THRESHOLD = 60;

    /** 0428 proposal: vorlon-active threshold. */
    public static final int VORLON_ACTIVE_THRESHOLD = 60;

    /** 0428 proposal: cold-war floor (both above this, neither active). */
    public static final int SHADOW_COLD_THRESHOLD = 40;

    /** 0428 proposal: human-secured threshold. */
    public static final int HUMAN_SECURED_THRESHOLD = 50;

    /** 0428 proposal: neutral/uncontested ceiling. */
    public static final int NEUTRAL_THRESHOLD = 20;

    private int influence = STATION_START_INFLUENCE;
    private int shadowInfluence = 0;
    private int vorlonInfluence = 0;

    public int  getInfluence()       { return influence; }
    public void gainInfluence(int n) { influence = Math.min(MAX_RATING, influence + n); }
    public void loseInfluence(int n) { influence = Math.max(0, influence - n); }

    // ── 0428 proposal: Shadow/Vorlon ratings ────────────────────────────────
    public int  getShadowInfluence()  { return shadowInfluence; }
    public int  getVorlonInfluence()  { return vorlonInfluence; }
    public void gainShadowInfluence(int n) { shadowInfluence = Math.min(MAX_RATING, shadowInfluence + n); }
    public void loseShadowInfluence(int n) { shadowInfluence = Math.max(0, shadowInfluence - n); }
    public void gainVorlonInfluence(int n) { vorlonInfluence = Math.min(MAX_RATING, vorlonInfluence + n); }
    public void loseVorlonInfluence(int n) { vorlonInfluence = Math.max(0, vorlonInfluence - n); }
    /** Absolute set (B5-0340/0428 delegation surface). */
    public void setShadowInfluence(int n) { shadowInfluence = Math.max(0, Math.min(MAX_RATING, n)); }
    /** Absolute set (B5-0340/0428 delegation surface). */
    public void setVorlonInfluence(int n) { vorlonInfluence = Math.max(0, Math.min(MAX_RATING, n)); }

    // ── B5-0340: Shadow War guard (condition 2 input) ──────────────────────
    public boolean isShadowWar() {
        return shadowInfluence >= CONDITION_2_THRESHOLD
            || vorlonInfluence >= CONDITION_2_THRESHOLD;
    }

    // ── 0428 proposal: condition-2 state queries ───────────────────────────
    /** Shadow-active state: shadow >60 AND vorlon <60 (proposal §Shadow-War trigger). */
    public boolean isShadowActive() {
        return shadowInfluence > SHADOW_ACTIVE_THRESHOLD && vorlonInfluence < VORLON_ACTIVE_THRESHOLD;
    }
    /** Vorlon-active state: vorlon >60 AND shadow <60. */
    public boolean isVorlonActive() {
        return vorlonInfluence > VORLON_ACTIVE_THRESHOLD && shadowInfluence < SHADOW_ACTIVE_THRESHOLD;
    }
    /** Human-secured state: human influence >50 AND no shadow/vorlon active. */
    public boolean isHumanSecured() {
        return influence > HUMAN_SECURED_THRESHOLD && !isShadowActive() && !isVorlonActive();
    }
    /** Uncontested state: all three ratings below 20. */
    public boolean isUncontested() {
        return influence < NEUTRAL_THRESHOLD && shadowInfluence < NEUTRAL_THRESHOLD
            && vorlonInfluence < NEUTRAL_THRESHOLD;
    }
}
