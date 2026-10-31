package com.codex.blacklight.infection;

/** Shared, deterministic loaded-time growth. Neither client frame rate nor wall-clock time advances it. */
public final class TissueGrowth {
    public static final int MATURITY_TICKS = 12000;
    public static final int HYPERTROPHY_TICKS = 24000, ANCIENT_TICKS = 48000;
    private TissueGrowth() {}
    public static long clamp(long ticks) { return Math.max(0, ticks); }
    /** Completed growth stages include the initial mature forms, before late genetic remodeling starts. */
    public static long generation(long ticks) {
        long age=clamp(ticks);
        return age<MATURITY_TICKS?0:age<HYPERTROPHY_TICKS?1:age<ANCIENT_TICKS?2:3+(age-ANCIENT_TICKS)/TissueEvolution.GENERATION_TICKS;
    }
    public static long advance(long ticks, long elapsed) {
        long age=clamp(ticks),delta=clamp(elapsed);
        return delta>Long.MAX_VALUE-age?Long.MAX_VALUE:age+delta;
    }
    public static float progress(long ticks) {
        float t = Math.min(MATURITY_TICKS,clamp(ticks)) / (float)MATURITY_TICKS;
        return t * t * (3 - 2 * t);
    }
    public static int stage(long ticks) { return (int)Math.min(3, Math.min(MATURITY_TICKS,clamp(ticks)) * 4 / MATURITY_TICKS); }
    /** Additional shape keys after the original mature form: 0 -> 1 -> 2.
     * Preserve the original ten-minute appearance and ease both later joins. */
    public static float overgrowth(long ticks) {
        long age=Math.min(ANCIENT_TICKS,clamp(ticks));
        if(age<=MATURITY_TICKS)return 0;
        if(age<=HYPERTROPHY_TICKS)return smooth((age-MATURITY_TICKS)/(float)(HYPERTROPHY_TICKS-MATURITY_TICKS));
        return 1+smooth((age-HYPERTROPHY_TICKS)/(float)(ANCIENT_TICKS-HYPERTROPHY_TICKS));
    }
    private static float smooth(float x) { return x*x*(3-2*x); }
    public static long mix(long seed) {
        seed = (seed ^ (seed >>> 30)) * 0xbf58476d1ce4e5b9L;
        seed = (seed ^ (seed >>> 27)) * 0x94d049bb133111ebL;
        return seed ^ (seed >>> 31);
    }
    public static float sample(long seed, int index) {
        return (mix(seed + index * 0x9e3779b97f4a7c15L) >>> 40) / 16777216F;
    }
}
