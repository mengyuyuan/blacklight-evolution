package com.codex.blacklight.combat;

/** Timing envelope for Blacklight's own fallback clips. Damage remains governed by weapon ticks. */
public record WeaponMotionProfile(String source, float contactIn, float contactOut, float step, float elbowWeight,
                                  float wristRoll) {
    public static WeaponMotionProfile forMove(WeaponMove move) {
        if(move.fallback()!=move)return forMove(move.fallback());
        return switch (move) {
            // Grounded single-hand slashes preserve the free arm as a counterweight.
            case BLADE_CUT -> nativeMotion(move, .10F, .18F, .30F, .88F, -65);
            case BLADE_RETURN -> nativeMotion(move, .10F, .1833F, .28F, .88F, -65);
            // Single advancing strike when no explicit local source motion is installed.
            case BLADE_RUSH -> nativeMotion(move, .15F, .30F, 2.4F, .88F, -65);
            case HAMMER_JAB -> nativeMotion(move, .20F, .35F, .40F, .72F, 0);
            case HAMMER_HOOK -> nativeMotion(move, .15F, .30F, .45F, .72F, 0);
            case MUSCLE_JAB -> nativeMotion(move, .05F, .1333F, .24F, 1, 0);
            case MUSCLE_CROSS -> nativeMotion(move, .05F, .1333F, .28F, 1, 0);
            case MUSCLE_UPPERCUT -> nativeMotion(move, .40F, .4833F, .34F, .90F, 0);
            case MUSCLE_RUSH -> nativeMotion(move, .10F, .25F, 1.10F, 1, 0);
            case MUSCLE_AIR -> nativeMotion(move, .0667F, .2167F, 0, 1, 0);
            default -> null;
        };
    }
    private static WeaponMotionProfile nativeMotion(WeaponMove move,float in,float out,float step,float elbow,float roll) {
        return new WeaponMotionProfile(move.id,in,out,step,elbow,roll);
    }
    /** Account for the one-tick blend-in, then align source contact and recovery to server time. */
    public static float sourceTime(WeaponMove move,float target,float sourceLength,float in,float out) {
        return sourceTime(move.start,move.end,move.duration,target,sourceLength,in,out);
    }
    static float sourceTime(int start,int contactEnd,int duration,float target,float sourceLength,float in,float out) {
        // Single-tick contacts need a full tick to map both contact endpoints.
        // Longer windows keep their existing last-contact sampling time.
        float begin=(start-1)/20F, end=Math.max(start,contactEnd-2)/20F, finish=(duration-1)/20F;
        target=com.codex.blacklight.util.MathCompat.clamp(target,0,finish);
        if(target<=begin)return begin>0?in*target/begin:in;
        if(target<=end)return in+(out-in)*(target-begin)/(end-begin);
        return out+(sourceLength-out)*(target-end)/(finish-end);
    }
    public float distanceAt(WeaponMove move,float time) {
        float begin=Math.max(0,(move.start-4)/20F),end=(move.end-1)/20F;
        float t=com.codex.blacklight.util.MathCompat.clamp((time-begin)/(end-begin),0,1);
        return step*t*t*(3-2*t);
    }
}
