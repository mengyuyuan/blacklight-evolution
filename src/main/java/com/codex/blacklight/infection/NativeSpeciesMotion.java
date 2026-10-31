package com.codex.blacklight.infection;

/** Native squid clients otherwise wait forever for their original entity's reset packet. */
public final class NativeSpeciesMotion {
    private NativeSpeciesMotion(){}
    public static float squidPhase(long tick,long seed){return (float)((Math.floorMod(tick,1000000)*.16+Math.floorMod(seed,997)*.01)%(Math.PI*2));}
    public static float squidTentacle(long tick,long seed){float phase=squidPhase(tick,seed);if(phase>=Math.PI)return 0;double f=phase/Math.PI;return (float)(Math.sin(f*f*Math.PI)*Math.PI*.25);}
}
