package com.codex.blacklight.combat;

/** One-shot paired clips clamp at the end; they never restart while a body is held. */
public final class PairedMotionClock {
    private PairedMotionClock(){}
    public static float progress(long elapsedTicks,float partial,float transitionSeconds,float clipSeconds){
        return Math.max(0,Math.min(1,((elapsedTicks+partial)/20F-transitionSeconds)/Math.max(.05F,clipSeconds)));
    }
}
