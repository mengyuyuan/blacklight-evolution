package com.codex.blacklight.combat;

/** Short cosmetic fragment flight, with real downward acceleration and a ground stop. */
public final class CorpseFragmentMotion {
    private CorpseFragmentMotion(){}
    public static float drop(float seconds,float lowestPoint){
        float t=Math.max(0,seconds);
        return Math.max(-lowestPoint,.28F*t-4.9F*t*t);
    }
    public static float shrink(float flow){
        float t=Math.clamp((flow-.16F)/.84F,0,1);return t*t*(3-2*t);
    }
    public static org.joml.Vector3f torsoNormal(org.joml.Quaternionf modelRotation){
        return new org.joml.Quaternionf(-modelRotation.x,-modelRotation.y,modelRotation.z,modelRotation.w)
                .transform(new org.joml.Vector3f(0,1,0)).normalize();
    }
}
