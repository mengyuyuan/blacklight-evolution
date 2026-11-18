package com.codex.blacklight.military;

/** P1 AimingAtTarget's XZ dot-product gate, with bounded Minecraft aiming. */
public final class MilitaryAimControl {
    // startup_fig @315176; native 101e5990..101e5a0e uses 1-dot, NOT radians.
    public static final double DOT_ERROR = .10000000149011612;
    // Existing soldier LookControl limit, retained as a Minecraft adaptation.
    public static final float TURN_DEGREES = 30;
    private MilitaryAimControl() {}

    public static float wrap(float degrees) {
        float wrapped = degrees % 360;
        return wrapped >= 180 ? wrapped - 360 : wrapped < -180 ? wrapped + 360 : wrapped;
    }

    public static float turn(float from, float to) {
        if (!Float.isFinite(from) || !Float.isFinite(to)) return Float.NaN;
        return wrap(from + Math.max(-TURN_DEGREES, Math.min(TURN_DEGREES, wrap(to - from))));
    }

    public static float bearing(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || Math.hypot(x, z) < 1e-9) return Float.NaN;
        return (float) Math.toDegrees(Math.atan2(-x, z));
    }

    public static boolean aligned(float yaw, double x, double z) {
        double length = Math.hypot(x, z);
        if (!Float.isFinite(yaw) || !Double.isFinite(length) || length < 1e-9) return false;
        double angle = Math.toRadians(yaw);
        double dot = (-Math.sin(angle) * x + Math.cos(angle) * z) / length;
        return 1 - Math.max(-1, Math.min(1, dot)) <= DOT_ERROR;
    }

    public static float interpolate(float before, float current, float partial) {
        if (!Float.isFinite(current)) return Float.NaN;
        if (!Float.isFinite(before)) return current;
        return wrap(before + wrap(current - before) * Math.max(0, Math.min(1, partial)));
    }
}
