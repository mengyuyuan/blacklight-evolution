package com.codex.blacklight.infection;

/** Shared visual timing in ticks. Old infections enter the fully grown pose. */
public final class InfectionTimeline {
    public static final float DURATION = 48;
    private InfectionTimeline() {}
    public static float progress(long start, long now, float partial) {
        return start < 0 ? 1 : com.codex.blacklight.util.MathCompat.clamp((now-start+partial)/DURATION,0,1);
    }
    public static float stage(float t,float start,float end) {
        float u=com.codex.blacklight.util.MathCompat.clamp((t-start)/(end-start),0,1);return u*u*(3-2*u);
    }
    public static float growth(String joint,float t) {
        return switch(joint) {
            case "Chest" -> stage(t,0,.45F);
            case "Arm_R" -> stage(t,.12F,.62F);
            case "Hand_R" -> stage(t,.32F,.94F);
            case "Head" -> stage(t,.24F,.74F);
            default -> stage(t,.4F,1);
        };
    }
    public static float spasm(float t) {
        return t<=0 || t>=1 ? 0 : (float)Math.sin(Math.PI*t)*(float)Math.sin(t*46)*.13F;
    }
}
