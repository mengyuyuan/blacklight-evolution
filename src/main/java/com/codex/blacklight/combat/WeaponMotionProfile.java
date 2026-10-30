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
    public float distanceAt(WeaponMove move,float time) {
        float begin=Math.max(0,(move.start-4)/20F),end=(move.end-1)/20F;
        float t=com.codex.blacklight.forge.WingMath.clamp((time-begin)/(end-begin),0,1);
        return step*t*t*(3-2*t);
    }
}
