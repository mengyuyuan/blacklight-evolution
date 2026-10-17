package com.codex.blacklight.animation;

/** Growth travels from embedded roots to distal blades, then the wings settle closed. */
public final class WingMorphTimeline {
    public static final float GROW_TICKS=32, RETRACT_TICKS=22;
    private static float smooth(float t){return SovereignFlightMotion.smooth(t);}
    public static float progress(boolean deployed,double ticks){return deployed?smooth((float)ticks/GROW_TICKS):1-smooth((float)ticks/RETRACT_TICKS);}
    public static float growth(String bone,float progress){
        float delay=bone.contains("_tip_")?.40F:bone.contains("_finger_")?.27F:bone.contains("_arch_")?.17F:bone.contains("_rib_")?.08F:0;
        return smooth((progress-delay)/(1-delay));
    }
    public static float unfurl(boolean deployed,double ticks){
        if(!deployed)return 0;
        return smooth((float)(ticks-4)/12)*(1-smooth((float)(ticks-26)/16))*.65F;
    }
    public static float bodyWeight(boolean deployed,double ticks){
        float duration=deployed?GROW_TICKS:RETRACT_TICKS;
        return smooth((float)ticks/5)*(1-smooth(((float)ticks-duration+8)/8));
    }
    private WingMorphTimeline(){}
}
