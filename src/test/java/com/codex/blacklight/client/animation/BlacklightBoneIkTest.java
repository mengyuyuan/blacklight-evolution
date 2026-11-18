package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlacklightBoneIkTest {
    @Test void rigidBladeContactFitsDespiteAuthoredWristRotation(){
        var rig=BlacklightSkeleton.get();var anchor=new Vector3f(-.0636396F,.68F,-.0636396F);
        for(float angle:new float[]{-.9F,0,.7F,1.5F}){
            var pose=new BlacklightPose();pose.orElseEmpty("Tool_R").rotation().rotateXYZ(angle,.6F,-.3F);
            var wrist=pose.orElseEmpty("Tool_R").rotation().get(new org.joml.Quaternionf());
            var shoulder=rig.getBoundTransformFor(pose,rig.armR).getTranslation(new Vector3f());
            var elbow=rig.getBoundTransformFor(pose,rig.handR).getTranslation(new Vector3f());
            var before=rig.getBoundTransformFor(pose,rig.handR).transformPosition(new Vector3f(anchor));
            float upper=shoulder.distance(elbow),lower=elbow.distance(before);
            var target=new Vector3f(shoulder).add(new Vector3f(.3F,.5F,-.6F).normalize().mul((upper+lower)*.8F));
            BlacklightBoneIk.reachHand(rig,pose,"R",target,anchor,48);
            var actual=rig.getBoundTransformFor(pose,rig.handR).transformPosition(new Vector3f(anchor));
            var fittedElbow=rig.getBoundTransformFor(pose,rig.handR).getTranslation(new Vector3f());
            assertTrue(actual.distance(target)<.01F,"Rigid blade gap for wrist "+angle+": "+actual.distance(target));
            assertEquals(upper,shoulder.distance(fittedElbow),1e-5);assertEquals(lower,fittedElbow.distance(actual),1e-5);
            assertEquals(wrist,pose.orElseEmpty("Tool_R").rotation());
        }
    }
    @Test void longClawReachesWithoutStretchingEitherArmSegment(){
        var rig=BlacklightSkeleton.get();var pose=new BlacklightPose();
        pose.orElseEmpty("Tool_R").rotation().rotateXYZ(.3F,-.5F,.2F);
        var tip=rig.searchJointByName("Tool_R");var endpoint=new Vector3f(.01F,1.1F,0);
        var shoulder=rig.getBoundTransformFor(pose,rig.armR).getTranslation(new Vector3f());
        var elbow=rig.getBoundTransformFor(pose,rig.handR).getTranslation(new Vector3f());
        var end=rig.getBoundTransformFor(pose,tip).transformPosition(new Vector3f(endpoint));
        float upper=shoulder.distance(elbow),lower=elbow.distance(end);
        var target=new Vector3f(shoulder).add(new Vector3f(.3F,-.8F,.4F).normalize().mul(Math.max(upper,lower)));
        BlacklightBoneIk.reachTwoBone(rig,pose,"R",target,endpoint);
        var fitted=rig.getBoundTransformFor(pose,tip).transformPosition(new Vector3f(endpoint));
        var joint=rig.getBoundTransformFor(pose,rig.handR).getTranslation(new Vector3f());
        assertEquals(0,fitted.distance(target),1e-4);
        assertEquals(upper,shoulder.distance(joint),1e-5);assertEquals(lower,joint.distance(fitted),1e-5);
    }
    @Test void unreachableTargetKeepsFiniteBonesAndOriginalLengths(){
        var rig=BlacklightSkeleton.get();var pose=new BlacklightPose();var endpoint=new Vector3f(0,1,0);
        var shoulder=rig.getBoundTransformFor(pose,rig.armR).getTranslation(new Vector3f());
        var elbow=rig.getBoundTransformFor(pose,rig.handR).getTranslation(new Vector3f());
        var end=rig.getBoundTransformFor(pose,rig.searchJointByName("Tool_R")).transformPosition(new Vector3f(endpoint));
        float reach=shoulder.distance(elbow)+elbow.distance(end);
        BlacklightBoneIk.reachTwoBone(rig,pose,"R",new Vector3f(shoulder).add(0,100,0),endpoint);
        var fitted=rig.getBoundTransformFor(pose,rig.searchJointByName("Tool_R")).transformPosition(new Vector3f(endpoint));
        assertTrue(fitted.isFinite());assertEquals(reach,shoulder.distance(fitted),2e-4);
    }
}
