package com.codex.blacklight.combat;

/** Goliath's walking phase follows actual travel. Vanilla hit reactions also
 * write to WalkAnimationState speed, which is not a movement measurement. */
public final class GoliathGait {
    private static final double STRIDE_SPEED = .18 * GoliathPresentation.SCALE;
    private static final double MAX_STEP_SAMPLE = 2;
    private static final float BLEND_PER_TICK = .18F;

    private double previousPhase, phase;
    private float previousWeight, weight;

    public void tick(double horizontalDistance, boolean locomoting) {
        previousPhase = phase;
        previousWeight = weight;
        if (!Double.isFinite(horizontalDistance) || horizontalDistance > MAX_STEP_SAMPLE) {
            reset();
            return;
        }
        double travel = locomoting && horizontalDistance > .006 ? horizontalDistance : 0;
        float target = (float)Math.min(1, travel / .14);
        weight += Math.max(-BLEND_PER_TICK, Math.min(BLEND_PER_TICK, target - weight));
        phase += travel / STRIDE_SPEED;
    }

    public float weight(float partial) {
        return previousWeight + (weight - previousWeight)*partial;
    }

    public float phase(float partial, float loopTicks) {
        return (float)((previousPhase + (phase - previousPhase)*partial) % loopTicks);
    }

    public void reset() {
        previousPhase = phase = 0;
        previousWeight = weight = 0;
    }
}
