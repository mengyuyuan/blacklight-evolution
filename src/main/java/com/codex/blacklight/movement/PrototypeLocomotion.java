package com.codex.blacklight.movement;

/** Pure locomotion classification shared by runtime code and regression tests. */
public final class PrototypeLocomotion {
    public static final int CHARGE_GROUND_GRACE_TICKS = 5;
    public static final double RUN_ANIMATION_SPEED = 0.18D;
    /**
     * Fastest speed still drawn as a walk when no sprint was asked for. Unarmored
     * forward travel is about 0.233: above RUN_ANIMATION_SPEED, so it used to play the
     * sprint clip at 70% cadence (hands flapping at face height, crouched, floaty).
     * It now plays the same walk as armor; only sprinting or real speed shows a run.
     */
    public static final double WALK_VISUAL_MAX = 0.28D;
    public static final float MIN_PLAYBACK_SPEED = 0.55F;
    public static final float MAX_PLAYBACK_SPEED = 2.75F;
    public static final int GAIT_CONFIRM_TICKS = 3;

    public enum Gait { IDLE, WALK, RUN, CHARGING }

    /**
     * Rejects one-frame WALK/RUN/IDLE oscillation while keeping the first movement
     * response and the charged-jump wind-up immediate.
     */
    public static final class Stabilizer {
        private Gait current = Gait.IDLE;
        private Gait pending = Gait.IDLE;
        private int pendingTicks;

        public Gait update(Gait desired) {
            if (desired == current) {
                pending = desired;
                pendingTicks = 0;
                return current;
            }
            int requiredTicks = desired == Gait.CHARGING || current == Gait.IDLE
                    ? 1 : GAIT_CONFIRM_TICKS;
            if (desired != pending) {
                pending = desired;
                pendingTicks = 1;
            } else {
                pendingTicks++;
            }
            if (pendingTicks >= requiredTicks) {
                current = desired;
                pendingTicks = 0;
            }
            return current;
        }

        public Gait current() {
            return current;
        }

        public void reset() {
            current = Gait.IDLE;
            pending = Gait.IDLE;
            pendingTicks = 0;
        }
    }

    private PrototypeLocomotion() {
    }

    public static Gait resolve(boolean charging, boolean onGround, boolean moving,
                               boolean sprinting, double horizontalSpeed) {
        if (charging) return Gait.CHARGING;
        if (!onGround || !moving) return Gait.IDLE;
        return sprinting || horizontalSpeed >= RUN_ANIMATION_SPEED ? Gait.RUN : Gait.WALK;
    }

    public static float playbackSpeed(double horizontalSpeed) {
        if (!Double.isFinite(horizontalSpeed)) return MIN_PLAYBACK_SPEED;
        double scaled = horizontalSpeed / RUN_ANIMATION_SPEED;
        return (float)Math.max(MIN_PLAYBACK_SPEED, Math.min(MAX_PLAYBACK_SPEED, scaled));
    }

    /** Smooth 0..1 intensity for cinematic effects; ordinary walking stays clean. */
    public static float cinematicSpeedRatio(double horizontalSpeed, double maximumSpeed) {
        if (!Double.isFinite(horizontalSpeed) || !Double.isFinite(maximumSpeed)
                || maximumSpeed <= RUN_ANIMATION_SPEED) return 0.0F;
        double raw = (horizontalSpeed - RUN_ANIMATION_SPEED)
                / (maximumSpeed - RUN_ANIMATION_SPEED);
        double clamped = Math.max(0.0D, Math.min(1.0D, raw));
        return (float)(clamped * clamped * (3.0D - 2.0D * clamped));
    }

    /** A sprint always reads on screen; measured speed only supplies the upper 45%. */
    public static float sprintVisualIntensity(double horizontalSpeed, double maximumSpeed) {
        return 0.55F + cinematicSpeedRatio(horizontalSpeed, maximumSpeed) * 0.45F;
    }
}
