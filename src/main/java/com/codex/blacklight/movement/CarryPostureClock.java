package com.codex.blacklight.movement;

/** Tick-owned carry posture; repeated skin/weapon passes cannot advance the blend. */
public final class CarryPostureClock {
    private long tick=Long.MIN_VALUE;
    private float previous,current;
    public float sample(long now,boolean gliding,float partial) {
        if(tick==Long.MIN_VALUE)tick=now;
        if(now>tick) {
            previous=current;
            float step=.25F*Math.min(4,now-tick);
            current+=com.codex.blacklight.forge.WingMath.clamp((gliding?1:0)-current,-step,step);
            tick=now;
        }
        return previous+(current-previous)*com.codex.blacklight.forge.WingMath.clamp(partial,0,1);
    }
    public void reset(){tick=Long.MIN_VALUE;previous=current=0;}
}
