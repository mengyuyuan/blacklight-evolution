package com.codex.blacklight.animation;

import java.util.function.DoubleFunction;

/** A tick captures one state; render passes cannot observe half of the next network update.
 * Three subintervals preserve the 60 Hz authored curves on a 20 Hz game clock. */
final class CommittedPoseInterval {
    private final BlacklightPose[] frames = new BlacklightPose[4];
    private long tick = Long.MIN_VALUE;
    boolean ready() { return frames[0] != null; }
    void commit(long at, DoubleFunction<BlacklightPose> sampler) {
        if (at <= tick) return;
        for (int i=0;i<frames.length;i++) frames[i]=sampler.apply(i/3.0).copy();
        tick=at;
    }
    BlacklightPose sample(float partial) {
        float cursor=Math.max(0,Math.min(1,partial))*3;
        int index=Math.min(2,(int)cursor);
        return BlacklightPose.interpolatePose(frames[index],frames[index+1],cursor-index);
    }
}
