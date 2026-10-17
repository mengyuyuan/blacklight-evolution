package com.codex.blacklight.animation;

/** Back emergence gesture. Wings own their unfurl; the actor only braces and opens his arms. */
public final class WingTransformationPose {
    public static void apply(BlacklightPose pose, boolean deployed, double ticks) {
        float weight=WingMorphTimeline.bodyWeight(deployed,ticks);
        if(weight<=0)return;
        float duration=deployed?WingMorphTimeline.GROW_TICKS:WingMorphTimeline.RETRACT_TICKS;
        float t=(float)com.codex.blacklight.forge.WingMath.clamp(ticks/duration,0,1);
        // Brace first, then release the shoulder blades as the distal wings emerge.
        float release=SovereignFlightMotion.smooth((t-.18F)/.42F);
        float open=(deployed?release:1-release)*weight;
        rotate(pose,"Torso",weight*(1-release)*7,0,0);
        rotate(pose,"Chest",-open*4,0,0);
        rotate(pose,"Arm_R",-open*12,0,-open*22);
        rotate(pose,"Arm_L",-open*12,0,open*22);
        rotate(pose,"Hand_R",open*18,0,0);
        rotate(pose,"Hand_L",open*18,0,0);
    }
    private static void rotate(BlacklightPose pose,String bone,float x,float y,float z) {
        float radians=(float)Math.PI/180;
        pose.orElseEmpty(bone).rotation().rotateXYZ(x*radians,y*radians,z*radians);
    }
    private WingTransformationPose(){}
}
