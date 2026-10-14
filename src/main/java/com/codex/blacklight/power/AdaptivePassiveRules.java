package com.codex.blacklight.power;

/** Sponsor passive formulas. HP means health points, never heart icons or rounded HUD values. */
public final class AdaptivePassiveRules {
    private AdaptivePassiveRules() {}
    public static double remainingFraction(int layers, double health, double maximum, boolean percentBased) {
        if (layers <= 0) return 1;
        if (!Double.isFinite(health) || health <= 0) return 1;
        double denominator = percentBased && Double.isFinite(maximum) && maximum > 0
                ? Math.min(100, health / maximum * 100) : health;
        return denominator / (layers + denominator);
    }
    public static int rounded(double amount) {
        return !Double.isFinite(amount) || amount <= 0 ? 0 : (int)Math.min(Integer.MAX_VALUE, Math.floor(amount + .5));
    }
    public static int adaptationAfterHit(int layers, double actual, double prevented) {
        // One stack per effective hit, NOT actual damage / 6. Keep this distinct from regeneration.
        return (int)Math.max(0, Math.min(Integer.MAX_VALUE, (long)layers + (actual > 0 ? 1 : 0) - rounded(prevented * .1)));
    }
    public static int regenerationAfterHit(int layers, double beforeAdaptation) {
        return (int)Math.min(Integer.MAX_VALUE, (long)layers + rounded(beforeAdaptation / 6));
    }
    public static int regenerationAfterPulse(int layers) {
        // Exact requested rounding: one or two remaining stacks do not decay to zero.
        return Math.max(0, layers - rounded(layers * .2));
    }
}
