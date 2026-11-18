package com.codex.blacklight.client.animation;
import com.codex.blacklight.animation.*;
import com.codex.blacklight.combat.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HammerGroundPoseTest {
    @Test void completeScaledStrikeStopsAtGroundWithoutMovingHipAndLegs(){
        for(String key:new String[]{"hammer_quake","hammer_quake_charged"})for(float t=0;t<MotionLibrary.duration(key);t+=1/60F){
            var pose=MotionLibrary.pose(key,t,false);var original=pose.copy();
            var right=WeaponAttackScales.sample(key,t,false);var left=WeaponAttackScales.sample(key,t,true);
            HammerGroundPose.stabilize(pose,right,left,.012F,.012F,1);
            assertTrue(HammerGroundPose.minimum(pose,false,right)>=.011F,key+" right "+t);
            assertTrue(HammerGroundPose.minimum(pose,true,left)>=.011F,key+" left "+t);
            for(String bone:new String[]{"Root","Thigh_R","Thigh_L","Leg_R","Leg_L"})assertTrue(original.orElseEmpty(bone).toMatrix().equals(pose.orElseEmpty(bone).toMatrix(),1e-5F),"support changed");
        }
    }
    @Test void absentGroundDoesNotRaiseAFreeFist(){
        var pose=MotionLibrary.pose("hammer_quake_charged",1.4F,false);var original=pose.copy();
        var shape=WeaponAttackScales.sample("hammer_quake_charged",1.4F,false);
        HammerGroundPose.stabilize(pose,shape,shape,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,0);
        for(var bone:BlacklightSkeleton.get().bones())assertTrue(original.orElseEmpty(bone.getName()).toMatrix().equals(pose.orElseEmpty(bone.getName()).toMatrix(),1e-5F));
    }
}
