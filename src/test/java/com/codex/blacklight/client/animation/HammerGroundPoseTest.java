package com.codex.blacklight.client.animation;
import com.codex.blacklight.animation.*;
import com.codex.blacklight.combat.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HammerGroundPoseTest {
    @Test void comboFistGrowsThroughDescentAndRetractsWithRecovery() {
        var normal=WeaponMove.HAMMER_COMBO_SPECIAL;
        for(var move:new WeaponMove[]{normal,WeaponMove.HAMMER_COMBO_SPECIAL_CHARGED}) {
            float landing=LocalWeaponMotion.find(move.id).orElseThrow().landing().orElseThrow().impactTime();
            var shape=WeaponAttackScales.sample(move.id,landing,false);
            assertTrue(shape.px()>2.3F&&shape.py()>2.3F&&shape.fx()>1.8F);
            assertEquals(shape,WeaponAttackScales.sample(move.id,landing-.1F,false));
            assertEquals(WeaponAttackScales.Shape.IDENTITY,WeaponAttackScales.sample(move.id,landing,true));
            assertEquals(WeaponAttackScales.Shape.IDENTITY,WeaponAttackScales.sample(move.id,MotionLibrary.duration(move.id),false));
        }
        var charge=LocalWeaponMotion.find(normal.id).orElseThrow().charge().orElseThrow();
        assertEquals(WeaponAttackScales.sample(charge.animation(),1F,false),
                WeaponAttackScales.sample("hammer_combo_special_charged",charge.releaseTime(),false));
    }

    @Test void firstContactDoesNotSnapThePalmToAnotherShoulderAngle() {
        var rig=BlacklightSkeleton.get();
        for(String key:new String[]{"hammer_quake","hammer_quake_charged"}) {
            org.joml.Vector3f previous=null;
            boolean palmTouched=false;
            for(int frame=2520;frame<=2650;frame++) {
                float t=frame/2000F;
                var pose=MotionLibrary.pose(key,t,false);
                var right=WeaponAttackScales.sample(key,t,false);
                var left=WeaponAttackScales.sample(key,t,true);
                HammerGroundPose.stabilize(pose,right,left,.012F,.012F,0);
                var palm=rig.getBoundTransformFor(pose,rig.handR).transformPosition(
                        right.point(new org.joml.Vector3f(0,.73F,.095F)));
                if(previous!=null)assertTrue(previous.distance(palm)<.10F,key+" palm discontinuity at "+t);
                if(t>=1.3F)palmTouched|=HammerGroundPose.fist(pose,right,false).stream().anyMatch(v->v.y<.065F);
                previous=palm;
            }
            assertTrue(palmTouched,key+" fist did not touch the ground");
        }
    }
    @Test void groundedBackswingKeepsOneSoleOnTheFloorAndTracksTheStep() {
        var rig=BlacklightSkeleton.get();
        var bind=rig.getPoseMatrices(new BlacklightPose());
        var left=rig.searchJointByName("Knee_L").getId();
        var right=rig.searchJointByName("Knee_R").getId();
        for(String key:new String[]{"hammer_quake","hammer_quake_charged"}){
            for(int frame=7;frame<=155;frame++){
                float time=frame/120F;
                var world=rig.getPoseMatrices(MotionLibrary.pose(key,time,false));
                float lowest=Float.POSITIVE_INFINITY;
                for(int index:new int[]{left,right}){
                    var sole=new org.joml.Matrix4f(world[index]).mul(new org.joml.Matrix4f(bind[index]).invert());
                    float x=index==left?-1.9F/16F:1.9F/16F;
                    float foot=Float.POSITIVE_INFINITY;
                    for(float dx:new float[]{-.125F,.125F})for(float z:new float[]{-.125F,.125F}){
                        foot=Math.min(foot,sole.transformPosition(new org.joml.Vector3f(x+dx,.001F,z)).y);
                    }
                    assertTrue(foot>-.009F,key+" buried boot "+time);
                    lowest=Math.min(lowest,foot);
                }
                assertTrue(lowest<.016F,key+" both soles are above ground before impact "+time);
            }
        }
    }
    @Test void sourceRecoveryTransfersWeightAcrossThePlantedFistBeforeStanding() {
        var rig=BlacklightSkeleton.get();
        for(String key:new String[]{"hammer_quake","hammer_quake_charged"}) {
            var overhead=rig.getPoseMatrices(MotionLibrary.pose(key,1.10F,false));
            assertTrue(overhead[rig.handR.getId()].m31()>overhead[rig.head.getId()].m31());
            var low=rig.getPoseMatrices(MotionLibrary.pose(key,1.32F,false));
            assertTrue(low[rig.head.getId()].m31()>.28F,"the larger MC head needs clearance");
            var transfer=rig.getPoseMatrices(MotionLibrary.pose(key,1.55F,false));
            var landed=rig.getPoseMatrices(MotionLibrary.pose(key,2.10F,false));
            var leftFoot=rig.searchJointByName("Knee_L").getId();
            assertTrue(transfer[leftFoot].m31()>landed[leftFoot].m31()+.32F,"rear foot must pass over the support hand");
            assertTrue(landed[rig.head.getId()].m31()<rig.getPoseMatrices(MotionLibrary.pose(key,3.24F,false))[rig.head.getId()].m31()-.28F);
        }
    }

    @Test void completeScaledStrikeStopsAtGroundWithoutMovingHipAndLegs(){
        for(String key:new String[]{"hammer_quake","hammer_quake_charged"})for(float t=0;t<MotionLibrary.duration(key);t+=1/60F){
            var pose=MotionLibrary.pose(key,t,false);var original=pose.copy();
            var right=WeaponAttackScales.sample(key,t,false);var left=WeaponAttackScales.sample(key,t,true);
            HammerGroundPose.stabilize(pose,right,left,.012F,.012F,0);
            assertTrue(original.orElseEmpty("Torso").toMatrix().equals(pose.orElseEmpty("Torso").toMatrix(),1e-5F),"ground fitting preserves the authored spine");
            assertTrue(HammerGroundPose.minimum(pose,false,right)>=.011F,key+" right "+t);
            assertTrue(HammerGroundPose.minimum(pose,true,left)>=.011F,key+" left "+t);
            if(t>=1.325F&&t<=1.35F)assertTrue(HammerGroundPose.fist(pose,right,false).stream().anyMatch(v->v.y<.065F),"striking fist must reach the ground");
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
