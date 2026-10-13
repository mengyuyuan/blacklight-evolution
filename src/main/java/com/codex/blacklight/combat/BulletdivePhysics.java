package com.codex.blacklight.combat;

/** Minecraft collision physics driven by the source SupportingSurface transition.
 * The original gravity scalar is not decoded; these world-unit constants are
 * explicit adaptation. There is no terminal speed, height cap or timed landing.
 */
public final class BulletdivePhysics {
    public static final double INITIAL_SPEED=.9, ACCELERATION=.36, JERK=.04;
    private BulletdivePhysics() { }
    private static double nonNegativeFinite(double value){return Double.isFinite(value)?Math.max(0,value):0;}
    public static double downSpeed(double flightTicks) {
        double t=nonNegativeFinite(flightTicks);
        // Increasing acceleration, evaluated from the shared action clock (not
        // accumulated from quantized velocity packets). Only numeric overflow
        // saturates; there is no gameplay height or terminal-speed threshold.
        return Math.min(Double.MAX_VALUE,INITIAL_SPEED+t*(ACCELERATION+.5*JERK*t));
    }
    public static double damageMultiplier(double actualDrop) {
        return 1+Math.sqrt(nonNegativeFinite(actualDrop)/30);
    }
    /** Optical response may saturate for legibility; physical energy never uses it. */
    public static double visualIntensity(double speed,double drop) {
        return Math.log1p(nonNegativeFinite(speed)+Math.sqrt(nonNegativeFinite(drop))*.15);
    }
}
