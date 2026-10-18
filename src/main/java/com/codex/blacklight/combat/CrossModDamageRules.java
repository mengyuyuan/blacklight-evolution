package com.codex.blacklight.combat;

/** Pure arithmetic shared by both loaders. Damage budgets are measured in final HP units. */
public final class CrossModDamageRules {
    private CrossModDamageRules() {}

    public static boolean externalNamespace(String namespace) {
        return namespace != null && !namespace.isEmpty() && !namespace.equals("minecraft")
                && !namespace.equals("blacklight");
    }

    public static float multiply(float damage, double multiplier) {
        if (Float.isNaN(damage) || damage <= 0 || multiplier <= 0) return 0;
        return (float)Math.min(Float.MAX_VALUE, (double)damage * multiplier);
    }

    public record Budget(long start, float spent) {
        public Result accept(long now, int ticks, float amount, float limit) {
            boolean reset = now < start || now - start >= ticks;
            float previous = reset ? 0 : spent;
            float accepted = Math.max(0, Math.min(amount, limit - previous));
            return new Result(accepted, new Budget(reset ? now : start, previous + accepted));
        }
    }
    public record Result(float damage, Budget budget) {}
}
