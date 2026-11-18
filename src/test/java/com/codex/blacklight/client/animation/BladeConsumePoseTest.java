package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.*;
import com.codex.blacklight.combat.WeaponCutContact;
import com.codex.blacklight.power.PowerForm;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BladeConsumePoseTest {
    @Test void realBladeEdgeReachesTissueWithoutStretchingOrMovingTheOtherArm(){
        var rig=BlacklightSkeleton.get();
        for(var target:new Vector3f[]{new Vector3f(.35F,1.0F,-.8F),new Vector3f(-.1F,.8F,-1.1F),new Vector3f(.7F,1.3F,-.5F)}){
            var original=new BlacklightPose();original.orElseEmpty("Tool_R").rotation().rotateXYZ(.9F,-.4F,.2F);
            original.orElseEmpty("Arm_L").rotation().rotateX(.7F);
            var shoulder=rig.getBoundTransformFor(original,rig.armR).getTranslation(new Vector3f());
            float armLength=shoulder.distance(rig.getBoundTransformFor(original,rig.handR).getTranslation(new Vector3f()));
            var fit=BladeConsumePose.fit(rig,original,target);var hand=rig.getBoundTransformFor(fit,rig.handR);
            double distance=Double.POSITIVE_INFINITY;
            for(var line:WeaponCutContact.sharpProfiles(PowerForm.BLADE,"Hand_R").values())for(int i=1;i<line.size();i++){
                var a=hand.transformPosition(line.get(i-1).toVector3f());var b=hand.transformPosition(line.get(i).toVector3f());var ab=b.sub(a,new Vector3f());
                float t=net.minecraft.util.Mth.clamp(target.sub(a,new Vector3f()).dot(ab)/ab.lengthSquared(),0,1);
                distance=Math.min(distance,new Vector3f(a).fma(t,ab).distance(target));
            }
            assertTrue(distance<.025,"Actual sharp profile gap: "+distance);
            assertEquals(armLength,shoulder.distance(hand.getTranslation(new Vector3f())),1e-5);
            assertEquals(original.orElseEmpty("Tool_R").rotation(),fit.orElseEmpty("Tool_R").rotation());
            assertEquals(original.orElseEmpty("Arm_L").rotation(),fit.orElseEmpty("Arm_L").rotation());
            assertEquals(original.orElseEmpty("Root").rotation(),fit.orElseEmpty("Root").rotation());
        }
    }
    @Test void actualAuthoredSliceFitsAReachablePointThroughTheWholeContactWindow(){
        var rig=BlacklightSkeleton.get();
        for(float time:new float[]{.6F,.75F,.833333F}){
            var original=MotionLibrary.pose("consume_blade_actor",time,false).copy();
            var shoulder=rig.getBoundTransformFor(original,rig.armR).getTranslation(new Vector3f());
            for(var delta:new Vector3f[]{new Vector3f(.3F,-.3F,-1.1F),new Vector3f(-.5F,-.1F,-1.3F)}){
                var target=new Vector3f(shoulder).add(delta);var fit=BladeConsumePose.fit(rig,original,target);
                var hand=rig.getBoundTransformFor(fit,rig.handR);float gap=Float.POSITIVE_INFINITY;
                for(var line:WeaponCutContact.sharpProfiles(PowerForm.BLADE,"Hand_R").values())for(int i=1;i<line.size();i++){
                    var a=hand.transformPosition(line.get(i-1).toVector3f());var b=hand.transformPosition(line.get(i).toVector3f());var ab=b.sub(a,new Vector3f());
                    float t=net.minecraft.util.Mth.clamp(target.sub(a,new Vector3f()).dot(ab)/ab.lengthSquared(),0,1);
                    gap=Math.min(gap,new Vector3f(a).fma(t,ab).distance(target));
                }
                assertTrue(gap<.025F,"Authored time="+time+" gap="+gap+" target="+target);
                assertEquals(original.orElseEmpty("Root").rotation(),fit.orElseEmpty("Root").rotation());
            }
        }
    }
    @Test void originalSeverMarkerIsInsideFullContactAndTheFitReleasesForRecovery(){
        float cut=.833333F;
        assertEquals(0,BladeConsumePose.weight(0,cut));
        assertEquals(1,BladeConsumePose.weight(cut,cut),1e-6);
        assertEquals(0,BladeConsumePose.weight(cut+.22F,cut));
        assertEquals(0,BladeConsumePose.weight(1,Float.NaN));
    }
}
