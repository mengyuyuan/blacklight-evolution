package com.codex.blacklight.animation;

/** Tick-owned charge interval. Rendering cannot seek, advance or rewind it. */
public final class ChargePoseClock {
    private int previous,current;
    public void commit(int charge, int maximum) {
        int next=com.codex.blacklight.util.MathCompat.clamp(charge,0,Math.max(1,maximum));
        previous=next<current?next:current;
        current=next;
    }
    public float ratio(float partial,int maximum) {
        return (previous+(current-previous)*com.codex.blacklight.util.MathCompat.clamp(partial,0,1))/Math.max(1,maximum);
    }
}
