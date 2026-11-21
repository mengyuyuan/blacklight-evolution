package com.codex.blacklight.client.renderer;

import java.util.List;

/** Source FIG calls for the paired clips actually shipped in the mod. Times are source frames at 30 fps. */
final class ConsumeBridgeTracks {
    enum Source { SPINE_3, HIP_R, PELVIS, SPINE_2, HIP_L }
    enum Target { PELVIS, NECK, HIP_L, SPINE_2, HIP_R, SHOULDER_L, SHOULDER_R, KNEE_R }
    record Track(Source source,Target target,int delayFrames,int segments,double scale,double speed,double circle,double roll,double pitch) {}
    record Cue(int branch,int frame,List<Track> tracks) {}
    private static final List<Track> CHEST=List.of(
        new Track(Source.SPINE_3,Target.PELVIS,0,10,.3,2,.3,60,160),
        new Track(Source.HIP_R,Target.NECK,2,10,.2,4,.3,90,70),
        new Track(Source.PELVIS,Target.HIP_L,3,10,.5,4,.2,45,45),
        new Track(Source.SPINE_2,Target.SPINE_2,4,10,.3,5,.2,-90,45));
    private static final List<Track> STOMP=List.of(
        new Track(Source.SPINE_3,Target.PELVIS,0,10,.4,2,.2,60,120),
        new Track(Source.HIP_R,Target.HIP_R,2,10,.4,4,.2,90,70),
        new Track(Source.PELVIS,Target.HIP_L,3,10,.5,4,.2,45,70),
        new Track(Source.SPINE_2,Target.SPINE_2,4,10,.3,5,.3,-90,70));
    private static final List<Track> BEAT=List.of(
        new Track(Source.SPINE_3,Target.PELVIS,0,10,.3,2,.3,60,120),
        new Track(Source.HIP_L,Target.SHOULDER_L,1,10,.6,4,.3,-90,90),
        new Track(Source.HIP_R,Target.KNEE_R,2,10,.2,4,.3,90,70));
    private static final List<Track> SHOULDER=List.of(
        new Track(Source.SPINE_2,Target.SHOULDER_R,5,5,.5,4,.472,-99.5,90));
    private static final Cue NORMAL=new Cue(92,35,CHEST),HEAD_STOMP=new Cue(93,34,STOMP),
            BEATDOWN=new Cue(94,100,BEAT),KNEE=new Cue(98,30,SHOULDER),
            CLAW_HEAD=new Cue(92,50,CHEST),WHIP_LASH=new Cue(93,51,STOMP),
            HEART=new Cue(92,40,CHEST),HAMMER=new Cue(98,58,SHOULDER),SMUSH=new Cue(98,61,SHOULDER);
    private static final Cue NO_BRIDGE=new Cue(0,0,List.of());
    private ConsumeBridgeTracks(){}
    static Cue forClip(String key){
        return switch(key){
            case "normal" -> NORMAL;
            case "normal_head_stomp" -> HEAD_STOMP;
            case "normal_beatdown" -> BEATDOWN;
            case "normal_knee_head" -> KNEE;
            case "claws_head" -> CLAW_HEAD;
            case "whip_lash" -> WHIP_LASH;
            case "muscle_heart" -> HEART;
            case "hammer" -> HAMMER;
            case "hammer_smush" -> SMUSH;
            case "claws","whip","muscle","blade" -> NO_BRIDGE;
            default -> null;
        };
    }
}
