package com.codex.blacklight.movement;

/** Bounded catch-up for delayed carried-body packets, independent of entity IDs. */
public final class CarryInterpolation {
    private CarryInterpolation() { }
    public static double correctionLimit(double speed,double travelled) {
        if(!Double.isFinite(speed)||!Double.isFinite(travelled)||speed<0||travelled<0||travelled>4)return 0;
        // Ordinary entity tracking can trail a fast carrier by several ticks.
        // A world-space teleport still breaks prediction, and block sweeps remain authoritative.
        return Math.min(12,4+Math.max(speed,travelled)*8);
    }
}
