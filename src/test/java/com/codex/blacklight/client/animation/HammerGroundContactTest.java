package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HammerGroundContactTest {
    @Test void quakeRecoveryClearsTheActualScaledFistsAndKeepsLowerBody(){
        for(String key:new String[]{"hammer_quake","hammer_quake_charged"}){
            float end=com.codex.blacklight.combat.LocalWeaponMotion.find(key).orElseThrow().contactEndTick()/20F;
            for(float t=end+.2F;t<MotionLibrary.duration(key);t+=1/60F){
                var pose=MotionLibrary.pose(key,t,false);var original=pose.copy();
                var right=com.codex.blacklight.combat.WeaponAttackScales.sample(key,t,false);
                var left=com.codex.blacklight.combat.WeaponAttackScales.sample(key,t,true);
                HammerGroundContact.quakeRecovery(pose,key,t,right,left);
                assertTrue(HammerGroundContact.minimum(pose,false,right)>=.011F,key+" right fist buried at "+t);
                assertTrue(HammerGroundContact.minimum(pose,true,left)>=.011F,key+" left fist buried at "+t);
                for(String name:new String[]{"Root","Thigh_R","Thigh_L","Leg_R","Leg_L"})
                    assertTrue(original.orElseEmpty(name).toMatrix().equals(pose.orElseEmpty(name).toMatrix(),1e-5F),name+" support cannot be lifted");
                var rig=BlacklightSkeleton.get();
                assertTrue(rig.getBoundTransformFor(pose,rig.head).m31()>.28F,"head clears the ground during the source crouch "+key+" "+t);
            }
        }
    }
    @Test void quakeStrikeIsUntouchedAndRecoveryEntersContinuously(){
        String key="hammer_quake_charged";float end=com.codex.blacklight.combat.LocalWeaponMotion.find(key).orElseThrow().contactEndTick()/20F;
        for(float t=0;t<end+.0001F;t+=1/60F){
            var original=MotionLibrary.pose(key,t,false);var pose=original.copy();
            HammerGroundContact.quakeRecovery(pose,key,t,com.codex.blacklight.combat.WeaponAttackScales.sample(key,t,false),com.codex.blacklight.combat.WeaponAttackScales.sample(key,t,true));
            for(var bone:BlacklightSkeleton.get().bones())assertTrue(original.orElseEmpty(bone.getName()).toMatrix().equals(pose.orElseEmpty(bone.getName()).toMatrix(),1e-5F),"contact pose changed");
        }
        float t=end+.001F;var original=MotionLibrary.pose(key,t,false);var pose=original.copy();
        HammerGroundContact.quakeRecovery(pose,key,t,com.codex.blacklight.combat.WeaponAttackScales.sample(key,t,false),com.codex.blacklight.combat.WeaponAttackScales.sample(key,t,true));
        for(var bone:BlacklightSkeleton.get().bones())assertTrue(original.orElseEmpty(bone.getName()).toMatrix().equals(pose.orElseEmpty(bone.getName()).toMatrix(),.002F),"entry jump");
    }
    @Test void ramRecoveryClearsBothEnlargedFistsWithoutMovingHipOrFeet(){
        for(float t=1.466667F;t<MotionLibrary.duration("hammer_ram");t+=1/60F){
            var pose=MotionLibrary.pose("hammer_ram",t,false);var original=pose.copy();
            HammerGroundContact.apply(pose);
            for(boolean left:new boolean[]{false,true})assertTrue(HammerGroundContact.minimum(pose,left)>=.011F,"buried fist at "+t+" left="+left+" low="+HammerGroundContact.minimum(pose,left));
            for(String name:new String[]{"Root","Thigh_R","Thigh_L","Leg_R","Leg_L"})
                assertTrue(original.orElseEmpty(name).toMatrix().equals(pose.orElseEmpty(name).toMatrix(),1e-5F),name+" must stay planted");
        }
    }
}
