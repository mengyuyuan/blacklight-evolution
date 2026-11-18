package com.codex.blacklight.combat;

/** Minecraft wall reaction assembled from the source biped leg-stun clip.
 * This does not reproduce the source FIG trigger or its prone counterattack. */
public final class GoliathRecovery {
    private static final int LEGACY_WALL_TICKS = 40;
    public static final int WALL_TICKS = 56;
    private static final int MAX_TICKS = 140;
    private static final int FALL_END = 7;
    private static final int LAND_END = 15;
    private static final int RISE_TICKS = 24;
    private static final float FALL_FRAME = 218;
    private static final float LAND_FRAME = 230;
    private static final float DOWN_FRAME = 242;
    private static final float END_FRAME = 290;

    static final int IMPACT_BLEND_TICKS = 6;
    static final int RISE_BLEND_TICKS = 8;
    static final int STAND_BLEND_TICKS = 6;
    static final int DAMAGE_STAND_BLEND_TICKS = 12;

    private GoliathRecovery() {}

    static boolean wallReaction(int duration) {
        return duration == WALL_TICKS || duration == LEGACY_WALL_TICKS;
    }

    /** A synchronized sample of the last source phase before the charge is cleared. */
    public static long packImpactSample(int phase, float age) {
        if ((phase != 3 && phase != 4) || !Float.isFinite(age) || age < 0 || age > 60) return 0;
        return ((long)phase << 32) | Integer.toUnsignedLong(Float.floatToRawIntBits(age));
    }

    static int impactPhase(long sample) {
        return (int)(sample >>> 32);
    }

    static float impactAge(long sample) {
        return Float.intBitsToFloat((int)sample);
    }

    public static long sanitizeImpactSample(long sample) {
        return packImpactSample(impactPhase(sample), impactAge(sample));
    }

    /** Restore an absolute game-time interval without reviving an expired or invalid action. */
    public static long restoredEnd(long now, long start, long end) {
        return start >= 0 && start <= now && end > now && end - start <= MAX_TICKS ? end : 0;
    }

    /** Source biped leg-stun clip position. Wall recovery holds the ground-contact
     * pose before the separate source prone-to-biped clip takes over. */
    public static float poseProgress(float age, int duration) {
        if (!wallReaction(duration)) {
            // Ordinary damage uses the biped stumble and brace before the
            // separate ground fall/windup/counterattack authored later in this clip.
            float braceAge = Math.max(0, Math.min(age, duration - DAMAGE_STAND_BLEND_TICKS));
            return Math.min(213, braceAge * 1.5F) / END_FRAME;
        }
        if (age <= FALL_END) return mix(FALL_FRAME, LAND_FRAME, age, FALL_END) / END_FRAME;
        return mix(LAND_FRAME, DOWN_FRAME, age - FALL_END, LAND_END - FALL_END) / END_FRAME;
    }

    static int riseStart(int duration) {
        return Math.max(LAND_END + 8, duration - RISE_TICKS);
    }

    static float riseFrame(float age, int duration) {
        int start = riseStart(duration);
        return mix(16, 80, age - start, duration - start);
    }

    private static float mix(float first, float last, float age, int ticks) {
        return first + (last - first) * Math.max(0, Math.min(1, age / ticks));
    }
}
