package com.codex.blacklight.power;

/** Blacklight skill formulas. HP means health points, never heart icons or rounded HUD values. */
public final class AdaptivePassiveRules {
    private static final double HEAL_PER_SPENT_LAYER = 2.4;

    private AdaptivePassiveRules() {}
    public static int mitigation(int layers, double health, double maximum, boolean percentBased) {
        if (layers <= 0 || !Double.isFinite(health) || health <= 0) return 0;
        double denominator = percentBased && Double.isFinite(maximum) && maximum > 0
                ? Math.max(1, Math.ceil(100.0 * health / maximum)) : health;
        double maximumReduction = percentBased ? 65.0 : 50.0;
        return ceil(maximumReduction * layers / (layers + denominator));
    }
    public static double remainingFraction(int layers, double health, double maximum, boolean percentBased) {
        return (100-mitigation(layers,health,maximum,percentBased))/100.0;
    }
    public static int ceil(double amount) {
        return !Double.isFinite(amount) || amount <= 0 ? 0 : (int)Math.min(Integer.MAX_VALUE, Math.ceil(amount));
    }
    private static int gain(double actual) {
        return Double.isFinite(actual) && actual > 0 ? Math.max(1,ceil(actual/6)) : 0;
    }
    public static int adaptationAfterHit(int layers, double actual, double prevented) {
        // An accepted hit can be fully prevented: it still consumes stacks, but earns none.
        return (int)Math.max(0, Math.min(Integer.MAX_VALUE, (long)layers + gain(actual) - Math.max(1,ceil(prevented * .1))));
    }
    public static int regenerationAfterHit(int layers, double actual) {
        return (int)Math.min(Integer.MAX_VALUE, (long)layers + gain(actual));
    }
    public static float healing(int layers, double health, double maximum, boolean percentBased) {
        if(layers<=0||!Double.isFinite(health)||!Double.isFinite(maximum))return 0;
        double missing=Math.max(0,maximum-health);
        if (!percentBased) return (float)Math.min(missing, layers * 0.5);
        double missingFraction = Math.min(0.40, layers * 0.005);
        return (float)Math.min(missing * missingFraction, layers * HEAL_PER_SPENT_LAYER);
    }
    public static int regenerationAfterPulse(int layers, double healed) {
        return Math.max(0, layers - Math.max(1,ceil(healed / HEAL_PER_SPENT_LAYER)));
    }
}
