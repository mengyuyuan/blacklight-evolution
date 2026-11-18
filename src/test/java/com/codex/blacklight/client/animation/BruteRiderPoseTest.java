package com.codex.blacklight.client.animation;
import com.codex.blacklight.animation.*;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.PowerForm;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BruteRiderPoseTest {
    private static boolean previousLoaded;
    @org.junit.jupiter.api.BeforeAll static void headlessDefaultTuners()throws Exception{
        // The pure pose test has no Minecraft instance or client config directory.
        // Use the class's authored defaults without asking its live file loader to run.
        var field=com.codex.blacklight.client.tuning.ClawTransformSettings.class.getDeclaredField("loaded");field.setAccessible(true);
        previousLoaded=field.getBoolean(null);field.setBoolean(null,true);
    }
    @org.junit.jupiter.api.AfterAll static void restoreLoader()throws Exception{
        var field=com.codex.blacklight.client.tuning.ClawTransformSettings.class.getDeclaredField("loaded");field.setAccessible(true);field.setBoolean(null,previousLoaded);
    }
    private static double gap(BlacklightPose pose,String name,Vec3 actor,float yaw,float[][] body){
        var rig=BlacklightSkeleton.get();var point=rig.getBoundTransformFor(pose,rig.searchJointByName(name)).getTranslation(new Vector3f());
        new Matrix4f().rotateY((float)Math.toRadians(180-yaw)).scale(.9375F).transformPosition(point);
        var world=actor.add(point.x,point.y,point.z);
        return BruteContact.body(body,Vec3.ZERO,yaw).stream().filter(s->s.bone().equals("torso")).mapToDouble(s->s.box().nearest(world).distanceTo(world)).min().orElseThrow();
    }
    private static Vec3 anchor(float yaw){var p=new Matrix4f().rotateY((float)Math.toRadians(180-yaw)).transformPosition(new Vector3f(0,.85F,.97F));return new Vec3(p.x,p.y,p.z);}
    private static float length(BlacklightPose pose,String from,String to){var rig=BlacklightSkeleton.get();return rig.getBoundTransformFor(pose,rig.searchJointByName(from)).getTranslation(new Vector3f()).distance(rig.getBoundTransformFor(pose,rig.searchJointByName(to)).getTranslation(new Vector3f()));}
    @Test void holdPlacesWristsAndKneesAtTheActualBack(){
        var body=CreaturePoseTable.BRUTE.sample("idle",0);
        for(var form:new PowerForm[]{PowerForm.NONE,PowerForm.CLAWS,PowerForm.MUSCLE_MASS})for(float yaw:new float[]{0,37,90,180}){
            var pose=new BlacklightPose();var actor=anchor(yaw);
            BruteRiderPose.apply(pose,actor,Vec3.ZERO,yaw,body,false,0,form);
            for(String side:new String[]{"R","L"}){
                double wrist=gap(pose,"Tool_"+side,actor,yaw,body);
                if(form==PowerForm.NONE)assertEquals(.07,wrist,.025,"wrist "+side+" yaw="+yaw);
                else assertTrue(wrist>=.069&&wrist<=.48,"bounded mesh grip "+form+" "+side+" yaw="+yaw);
                assertEquals(length(new BlacklightPose(),"Arm_"+side,"Hand_"+side),length(pose,"Arm_"+side,"Hand_"+side),1e-5);
                assertEquals(length(new BlacklightPose(),"Hand_"+side,"Tool_"+side),length(pose,"Hand_"+side,"Tool_"+side),1e-5);
                assertEquals(.14,gap(pose,"Leg_"+side,actor,yaw,body),.04,"knee "+side+" yaw="+yaw);
            }
        }
    }
    @Test void consumeKeepsSupportingHandAndKneesWhileActionArmIsFree(){
        var body=CreaturePoseTable.BRUTE.sample("idle",0);var actor=anchor(180);
        for(String key:new String[]{"consume_large_claws_actor","consume_large_muscle_actor"})for(float t=0;t<1;t+=.025F){
            var pose=MotionLibrary.pose(key,t*MotionLibrary.duration(key),false);var arm=pose.orElseEmpty("Arm_R").copy();var hand=pose.orElseEmpty("Hand_R").copy();
            float upper=length(pose,"Arm_L","Hand_L"),lower=length(pose,"Hand_L","Tool_L");
            BruteRiderPose.apply(pose,actor,Vec3.ZERO,180,body,true,t,key.contains("claws")?PowerForm.CLAWS:PowerForm.MUSCLE_MASS);
            double wrist=gap(pose,"Tool_L",actor,180,body);
            assertTrue(wrist>=.069&&wrist<=.48,key+" bounded supporting hand t="+t+" gap="+wrist);
            assertEquals(upper,length(pose,"Arm_L","Hand_L"),1e-5);assertEquals(lower,length(pose,"Hand_L","Tool_L"),1e-5);
            for(String side:new String[]{"R","L"})assertEquals(.14,gap(pose,"Leg_"+side,actor,180,body),.04,key+" knee "+side+" t="+t);
            if(t>=.25F&&t<=.8F){assertTrue(arm.toMatrix().equals(pose.orElseEmpty("Arm_R").toMatrix(),1e-5F));assertTrue(hand.toMatrix().equals(pose.orElseEmpty("Hand_R").toMatrix(),1e-5F));}
        }
    }
}
