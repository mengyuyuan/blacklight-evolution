package com.codex.blacklight.client.animation;

import com.codex.blacklight.power.PowerForm;

/** Continuous material amounts survive a retarget; no rendered-frame history or wall clock. */
public final class WeaponMorphTimeline {
    private final float[] initial=new float[PowerForm.values().length];
    private PowerForm target,from;
    private double onset,retargetedAt;
    private float duration,initialFlow;
    private boolean longTransform;
    public WeaponMorphTimeline(PowerForm initialForm,double now) {
        target=from=initialForm;onset=now;duration=0;
        if(WeaponMorphTiming.hasMorph(initialForm))initial[initialForm.ordinal()]=1;
    }
    public void retarget(PowerForm next,double now,double requestedOnset,float ticks,boolean longTransform) {
        float carriedFlow=flow(now);
        var previous=weights(now);System.arraycopy(previous,0,initial,0,initial.length);
        from=PowerForm.NONE;float largest=0;
        for(var form:PowerForm.values())if(previous[form.ordinal()]>largest){from=form;largest=previous[form.ordinal()];}
        target=next;retargetedAt=now;onset=Math.max(now,requestedOnset);duration=Math.max(1,ticks);this.longTransform=longTransform;initialFlow=carriedFlow;
    }
    public float phase(double now){return duration<=0?1:clamp((float)((now-onset)/duration));}
    public boolean transitioning(double now){return now<onset+duration&&duration>0;}
    public PowerForm target(){return target;}
    public PowerForm from(){return from;}
    public boolean longTransform(){return longTransform;}
    public float flow(double now) {
        if(!transitioning(now))return 0;
        float phase=phase(now);
        // A delayed source cue must not freeze the previous liquid pulse for the
        // entire preparation. Settle the carried flow without moving its mesh.
        float carried=initialFlow*(1-smooth((float)((now-retargetedAt)/6)));
        return Math.max(4*phase*(1-phase),carried);
    }
    public float[] weights(double now) {
        float phase=phase(now),in=smooth(phase),out=smooth(phase/.72F);
        var result=new float[initial.length];
        for(var form:PowerForm.values()) {
            if(!WeaponMorphTiming.hasMorph(form))continue;
            float start=initial[form.ordinal()];
            result[form.ordinal()]=form==target?start+(1-start)*in:start*(1-out);
        }
        return result;
    }
    private static float smooth(float value){float t=clamp(value);return t*t*(3-2*t);}
    private static float clamp(float value){return Math.max(0,Math.min(1,value));}
}
