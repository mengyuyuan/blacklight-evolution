package com.codex.blacklight.animation;

import java.util.Objects;

/** Tick-owned pose handoff. Render passes only sample; they never restart a blend. */
final class CombatPoseTransition {
    private Object identity;
    private long tick = Long.MIN_VALUE, changed;
    private double duration;
    private BlacklightPose from, endpoint;

    void commit(Object next, long at, BlacklightPose initial, BlacklightPose targetEnd, double blendTicks) {
        if (at <= tick) return;
        if (!Objects.equals(identity, next)) {
            // The previous interval's blended endpoint also handles interrupted transitions.
            from = endpoint == null ? initial.copy() : endpoint.copy();
            changed = at;
            duration = Math.max(.001, blendTicks);
        }
        identity = next;
        tick = at;
        endpoint = sample(targetEnd, 1).copy();
        if (at - changed >= duration) from = null;
    }

    BlacklightPose sample(BlacklightPose target, float partial) {
        if (from == null) return target;
        float t = (float)Math.max(0, Math.min(1, (tick - changed + Math.max(0, Math.min(1, partial))) / duration));
        // Ease both ends without changing the authored attack clock or hit windows.
        t = t * t * (3 - 2 * t);
        return BlacklightPose.interpolatePose(from, target, t);
    }
}
