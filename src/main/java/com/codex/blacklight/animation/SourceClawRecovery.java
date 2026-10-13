package com.codex.blacklight.animation;

/** Decoded prototype_claws transition_to_idle owners 19820, 24016 and 28472.
 * The exported attack contains its recovery frames, but omitted the overlapping
 * alex_claw_amb_fight track. Restore that overlap without changing hit timing. */
final class SourceClawRecovery {
    private SourceClawRecovery() { }
    static float weight(String key,float seconds) {
        if(key.endsWith("_charged"))key=key.substring(0,key.length()-8);
        float begin,blend;
        switch(key) {
            case "claw_auto1" -> {begin=1F/30+.6F+.2F;blend=.1F;}
            case "claw_auto2" -> {begin=1F/30+.5F+8F/30;blend=7F/30;}
            case "claw_auto3" -> {begin=.05F+.6F+8F/30;blend=7F/30;}
            default -> {return 0;}
        }
        return Math.max(0,Math.min(1,(seconds-begin)/blend));
    }
    static BlacklightPose apply(String key,float seconds,BlacklightPose pose) {
        float weight=weight(key,seconds);
        return weight<=0?pose:BlacklightPose.interpolatePose(pose,
                MotionLibrary.pose("basic_idle_claw",0,false),weight);
    }
}
