package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;

/** Quarter-tick history for the first seven ticks of emission. A drop always uses
 * the same world-space frame after birth, including when the receiver is moving. */
final class SprayEmitterPath {
    static final int SUBSTEPS = 4;
    private final Frame[] frames;
    private Frame latest;
    private double latestAge;
    private int filled;

    record Frame(Vec3 point, Vec3 axis, Vec3 tangent, Vec3 bitangent) {
        static Frame of(Vec3 point, Vec3 direction) {
            var axis = direction.lengthSqr() > 1e-8 ? direction.normalize() : new Vec3(0, 1, 0);
            var tangent = axis.cross(Math.abs(axis.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
            return new Frame(point, axis, tangent, axis.cross(tangent));
        }
    }

    SprayEmitterPath(Vec3 point, Vec3 axis, boolean moving) {
        frames = new Frame[moving ? 7 * SUBSTEPS + 1 : 1];
        latest = Frame.of(point, axis);
        frames[0] = latest;
    }

    boolean complete() { return filled + 1 == frames.length; }

    void sample(double ageTicks, Vec3 point, Vec3 axis) {
        if (complete() || ageTicks <= latestAge) return;
        var next = Frame.of(point, axis);
        boolean jumped = latest.point.distanceToSqr(point) > 16;
        while (!complete() && (filled + 1.0) / SUBSTEPS <= ageTicks) {
            double t = ((filled + 1.0) / SUBSTEPS - latestAge) / (ageTicks - latestAge);
            frames[++filled] = jumped ? latest : Frame.of(latest.point.lerp(point, t), latest.axis.lerp(next.axis, t));
        }
        // A teleport ends emission at the old location, with no bridge across the map.
        if (jumped) while (!complete()) frames[++filled] = latest;
        latest = next;
        latestAge = ageTicks;
    }

    Frame last() { return frames[filled]; }

    Frame at(double birthSeconds) {
        return frames[Math.min(filled, Math.max(0, (int)Math.floor(birthSeconds * 20 * SUBSTEPS)))];
    }
}
