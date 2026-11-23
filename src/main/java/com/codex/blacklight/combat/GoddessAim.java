package com.codex.blacklight.combat;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/** Measured server positions include ordinary walking sent by the client. Knockback velocity alone does not. */
final class GoddessAim {
    private UUID target;
    private Vec3 last = Vec3.ZERO, velocity = Vec3.ZERO;
    private long tick;

    void observe(UUID id, Vec3 at, long now) {
        long dt = now - tick;
        if (id.equals(target) && dt == 0) return;
        Vec3 step = at.subtract(last).multiply(1, 0, 1);
        if (!id.equals(target) || dt <= 0 || dt > 6 || step.lengthSqr() > dt * dt) {
            velocity = Vec3.ZERO;
        } else {
            velocity = velocity.lerp(step.scale(1.0 / dt), 1 - Math.pow(.4, dt));
        }
        target = id;
        tick = now;
        last = at;
    }

    Vec3 lead(Vec3 center, double ticks) {
        Vec3 offset = velocity.scale(Math.min(44, Math.max(0, ticks)));
        return center.add(offset.lengthSqr() > 64 ? offset.normalize().scale(8) : offset);
    }

    // Airborne bone needles retain .99 of their velocity per tick.
    static double flightTicks(double range, double muzzleSpeed) {
        return Math.log1p(-Math.min(.95, range * .01 / muzzleSpeed)) / Math.log(.99);
    }
}
