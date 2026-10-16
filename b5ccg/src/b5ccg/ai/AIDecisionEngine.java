package b5ccg.ai;

import b5ccg.model.enums.AIDifficulty;

/**
 * AI decision engine for selecting actions based on difficulty tier.
 * 
 * This is infrastructure support for B5-1964.
 * Wired to AIMemory for difficulty-specific behavior.
 * 
 * This class provides the decision-making infrastructure that can be used
 * to drive AI behavior through difficulty tiers (Easy/Medium/Hard).
 */
public class AIDecisionEngine {
    
    private final AIMemory memory;
    
    public AIDecisionEngine(AIDifficulty difficulty) {
        this.memory = new AIMemory(difficulty);
    }
    
    public AIDecisionEngine(AIMemory memory) {
        this.memory = memory;
    }
    
    /**
     * Get the AI memory for this decision engine.
     * Allows external code to inspect and modify difficulty-based parameters.
     */
    public AIMemory getMemory() {
        return memory;
    }
    
    /**
     * Get the current difficulty tier.
     */
    public AIDifficulty getDifficulty() {
        return memory.getDifficulty();
    }
    
    /**
     * Get the current risk tolerance factor.
     * Lower values = more risk-averse, higher values = more risk-seeking.
     */
    public double getRiskTolerance() {
        return memory.getRiskTolerance();
    }
    
    /**
     * Get the current card valuation multiplier.
     * Higher values = more aggressive evaluation of card plays.
     */
    public double getCardValuationMultiplier() {
        return memory.getCardValuationMultiplier();
    }
    
    /**
     * Get the current conflict initiation threshold.
     * Lower values = more willing to initiate conflicts.
     */
    public double getConflictInitiationThreshold() {
        return memory.getConflictInitiationThreshold();
    }
}