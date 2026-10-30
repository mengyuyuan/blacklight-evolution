package com.codex.blacklight.movement;

/** Camera-relative travel expressed in the turning carrier's native input frame.
 * The same yaw drives the player, grip and receiver; changing it must not rotate
 * the requested world-space travel a second time. */
public final class CarrySteering {
    public record Step(float yaw, float forward, float left) { }
    private CarrySteering() { }

    public static Step steer(float cameraYaw, float actorYaw, float forward, float left) {
        if (Math.abs(forward) + Math.abs(left) < 1E-5F) return new Step(actorYaw, forward, left);
        float desired = cameraYaw + (float)Math.toDegrees(Math.atan2(-left, forward));
        float yaw = actorYaw + com.codex.blacklight.util.MathCompat.clamp(wrap(desired - actorYaw), -45F, 45F);
        double delta = Math.toRadians(cameraYaw - yaw);
        float cosine = (float)Math.cos(delta), sine = (float)Math.sin(delta);
        return new Step(wrap(yaw), forward * cosine + left * sine,
                left * cosine - forward * sine);
    }

    private static float wrap(float angle) {
        float wrapped = angle % 360;
        return wrapped >= 180 ? wrapped - 360 : wrapped < -180 ? wrapped + 360 : wrapped;
    }
}
