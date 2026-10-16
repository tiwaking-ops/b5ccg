package b5ccg.ai;

import b5ccg.model.enums.AIDifficulty;

/**
 * AI player memory/state holder.
 * Stores the difficulty tier and any per-turn or per-game AI state.
 * 
 * This is infrastructure support for AIDecisionEngine (B5-1964).
 * The difficulty enum governs risk tolerance, card valuation, and conflict thresholds.
 */
public class AIMemory {
    
    private final AIDifficulty difficulty;
    
    /** Risk tolerance factor (0.0 = risk-averse, 1.0 = risk-neutral, >1.0 = risk-seeking) */
    private double riskTolerance;
    
    /** Card valuation multiplier for current decision */
    private double cardValuationMultiplier;
    
    /** Conflict initiation threshold (lower = more aggressive) */
    private double conflictInitiationThreshold;
    
    public AIMemory(AIDifficulty difficulty) {
        this.difficulty = difficulty;
        initializeDifficultyDefaults();
    }
    
    /**
     * Initialize difficulty-specific defaults per B5-1964.
     * These values govern AI behavior at each tier.
     */
    private void initializeDifficultyDefaults() {
        switch (difficulty) {
            case EASY:
                // EASY: Random, high variance, low strategic depth
                riskTolerance = 1.0;
                cardValuationMultiplier = 1.0;
                conflictInitiationThreshold = 0.5;
                break;
                
            case MEDIUM:
                // MEDIUM: Greedy, focuses on immediate gains
                riskTolerance = 0.7;
                cardValuationMultiplier = 1.2;
                conflictInitiationThreshold = 0.6;
                break;
                
            case HARD:
                // HARD: Strategic, evaluates long-term outcomes
                riskTolerance = 0.4;
                cardValuationMultiplier = 1.5;
                conflictInitiationThreshold = 0.8;
                break;
                
            default:
                // Default to EASY for safety
                riskTolerance = 1.0;
                cardValuationMultiplier = 1.0;
                conflictInitiationThreshold = 0.5;
        }
    }
    
    /** Get the difficulty tier */
    public AIDifficulty getDifficulty() {
        return difficulty;
    }
    
    /** Get current risk tolerance */
    public double getRiskTolerance() {
        return riskTolerance;
    }
    
    /** Set risk tolerance */
    public void setRiskTolerance(double riskTolerance) {
        this.riskTolerance = riskTolerance;
    }
    
    /** Get current card valuation multiplier */
    public double getCardValuationMultiplier() {
        return cardValuationMultiplier;
    }
    
    /** Set card valuation multiplier */
    public void setCardValuationMultiplier(double cardValuationMultiplier) {
        this.cardValuationMultiplier = cardValuationMultiplier;
    }
    
    /** Get current conflict initiation threshold */
    public double getConflictInitiationThreshold() {
        return conflictInitiationThreshold;
    }
    
    /** Set conflict initiation threshold */
    public void setConflictInitiationThreshold(double conflictInitiationThreshold) {
        this.conflictInitiationThreshold = conflictInitiationThreshold;
    }
    
    /** Reset to difficulty defaults for a new decision cycle */
    public void resetToDifficultyDefaults() {
        initializeDifficultyDefaults();
    }
}