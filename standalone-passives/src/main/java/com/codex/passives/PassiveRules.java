package com.codex.passives;

/** Sponsor document rules; HP are actual health points, not heart icons. */
public final class PassiveRules {
    private PassiveRules() {}
    public static int ceil(double n) {
        return !Double.isFinite(n) || n <= 0 ? 0 : (int)Math.min(Integer.MAX_VALUE, Math.ceil(n));
    }
    public static int mitigation(int layers, double health, double maximum, int tier) {
        if (layers <= 0 || health <= 0 || !Double.isFinite(health)) return 0;
        double h = tier >= 2 && maximum > 0 ? Math.max(1, Math.ceil(100 * health / maximum)) : health;
        return Math.min(100, ceil(100.0 * layers / (layers + h)));
    }
    public static int gain(double damage) { return damage > 0 ? Math.max(1, ceil(damage / 6)) : 0; }
    public static int adaptationAfter(int layers, double actual, double blocked) {
        return (int)Math.max(0, Math.min(Integer.MAX_VALUE,
                (long)layers + gain(actual) - Math.max(1, ceil(blocked * .1))));
    }
    public static int add(int layers, int gained) { return (int)Math.min(Integer.MAX_VALUE, (long)layers + gained); }
    public static float healing(int layers, double health, double maximum, int tier) {
        double missing = Math.max(0, maximum - health);
        return (float)Math.min(missing, tier >= 2 ? missing * (layers / 100.0) : layers);
    }
    public static int afterHeal(int layers, double healed) { return Math.max(0, layers - Math.max(1, ceil(healed * .2))); }
    public static int killGain(double maximum) { return Math.min(30, ceil(maximum * .06)); }
}
