package com.codex.blacklight.combat;

import java.util.ArrayList;
import java.util.List;

/** P1 death Sound tracks and private audio/hydra.p3d Patch identities.
 * This is a client observation cursor, not a second death or damage clock. */
public final class HydraDeathAudioState {
    public enum Cue { ROAR,WHOOSH,DEBRIS }
    public record Event(int tick,Cue cue,float minimumPitch,float maximumPitch,int sourceOffset) {}
    private static final List<Event> NORMAL=List.of(
        new Event(0,Cue.ROAR,.7F,.8F,7200),new Event(30,Cue.WHOOSH,1,1,7292),
        new Event(40,Cue.ROAR,.6F,.7F,7384),new Event(60,Cue.WHOOSH,1,1,7476));
    private static final List<Event> THERMAL=List.of(new Event(0,Cue.ROAR,.7F,.8F,6576));
    public static final int DEBRIS_START=HydraDeath.DIE_TICKS+4;
    private int kind,age;
    public HydraDeathAudioState(int kind,int age){this.kind=kind;this.age=age;}
    public int age(){return age;}
    public int kind(){return kind;}
    public boolean replaced(int nextKind,int nextAge){return age>=0&&(nextAge<age||nextKind!=kind);}
    public List<Event> observe(int nextKind,int nextAge){
        int previous=age;boolean reset=replaced(nextKind,nextAge);
        kind=nextKind;age=nextAge; // Consume before any playback callback.
        if(nextAge<0||reset||!HydraDeath.validKind(nextKind))return List.of();
        // Do not turn a delayed packet or client stall into a pile of old cues.
        // Ordinary one-tick/interpolated skips retain exact event crossings.
        if(nextAge-previous>4)previous=nextAge-2;
        var result=new ArrayList<Event>();
        for(var event:nextKind==HydraDeath.THERMOBARIC?THERMAL:NORMAL)
            if(previous<event.tick&&event.tick<=nextAge)result.add(event);
        return List.copyOf(result);
    }
    public boolean debris(){return kind==HydraDeath.NORMAL&&age>=DEBRIS_START&&age<HydraDeath.TOTAL_TICKS;}
}
