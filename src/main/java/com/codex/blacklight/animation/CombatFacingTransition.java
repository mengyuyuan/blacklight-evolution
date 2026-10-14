package com.codex.blacklight.animation;

import java.util.Objects;

/** Body and attached effects share this tick-owned shortest-angle handoff. */
final class CombatFacingTransition {
    private long tick=Long.MIN_VALUE, changed;
    private Object identity;
    private float start, end, from, endpoint;
    private double duration;
    private boolean blending;
    private static float angle(float from,float to,float weight) {
        float delta=(to-from)%360;
        if(delta>=180)delta-=360;
        if(delta< -180)delta+=360;
        return from+delta*weight;
    }
    void commit(Object next,long at,float initial,float target,double ticks) {
        if(at<=tick)return;
        start=tick==Long.MIN_VALUE?initial:end;
        if(!Objects.equals(identity,next)) {
            from=tick==Long.MIN_VALUE?initial:endpoint;
            changed=at;duration=Math.max(.001,ticks);blending=true;
        }
        identity=next;end=target;tick=at;
        endpoint=sample(1);
        if(at-changed>=duration)blending=false;
    }
    boolean ready(){return tick!=Long.MIN_VALUE;}
    float sample(float partial) {
        partial=Math.max(0,Math.min(1,partial));
        float target=angle(start,end,partial);
        if(!blending)return target;
        float weight=(float)Math.max(0,Math.min(1,(tick-changed+partial)/duration));
        weight=weight*weight*(3-2*weight);
        return angle(from,target,weight);
    }
}
