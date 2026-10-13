package com.codex.blacklight.combat;

/** Minecraft collision physics driven by the source SupportingSurface transition.
 * The original gravity scalar is not decoded; these world-unit constants are
 * explicit adaptation. There is no terminal speed, height cap or timed landing.
 */
public final class BulletdivePhysics {
    public static final double INITIAL_SPEED=.45, ACCELERATION=.22;
    private BulletdivePhysics() { }
    private static double nonNegativeFinite(double value){return Double.isFinite(value)?Math.max(0,value):0;}
    public static double downSpeed(double flightTicks) {
        return INITIAL_SPEED+ACCELERATION*nonNegativeFinite(flightTicks);
    }
    public static double damageMultiplier(double actualDrop) {
        return 1+Math.sqrt(nonNegativeFinite(actualDrop)/30);
    }
    /** Optical response may saturate for legibility; physical energy never uses it. */
    public static double visualIntensity(double speed,double drop) {
        return Math.log1p(nonNegativeFinite(speed)+Math.sqrt(nonNegativeFinite(drop))*.15);
    }
}
