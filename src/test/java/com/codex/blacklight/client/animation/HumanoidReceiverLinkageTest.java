package com.codex.blacklight.client.animation;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HumanoidReceiverLinkageTest {
    private static void close(Vector3f expected,Vector3f actual){
        assertTrue(expected.distance(actual)<2e-5F,"joint separation: "+expected+" / "+actual);
    }
    @Test void fallTorsoStaysAttachedToHipsNeckAndSlimShouldersThroughoutBlend(){
        var waist=new Vector3f(0,12,0);var shoulderR=new Vector3f(-5,2.5F,0);var shoulderL=new Vector3f(5,2.5F,0);
        var legR=new Vector3f(-1.9F,12,0);var legL=new Vector3f(1.9F,12,0);
        var targetBody=new Quaternionf().rotationZYX(.21F,-.18F,1.46F);
        // Regression reproducer: rotations-only moves the real torso's bottom
        // away from the two unchanged vanilla thigh origins by > 10 pixels.
        assertTrue(targetBody.transform(new Vector3f(waist)).distance(waist)>10);
        for(float weight:new float[]{0,.1F,.3F,.65F,1}){
            var bodyRotation=new Quaternionf().slerp(targetBody,weight);
            var hipsRotation=new Quaternionf().slerp(new Quaternionf().rotationZYX(-.24F,.33F,.9F),weight);
            var linked=NativeReceiverPose.linkHumanoid(new Vector3f(),new Quaternionf(),waist,bodyRotation,hipsRotation,
                    new Vector3f(),new Vector3f(),shoulderR,shoulderL,legR,legL);
            var torsoBottom=bodyRotation.transform(new Vector3f(0,12,0)).add(linked.body());
            var hipMidpoint=new Vector3f(linked.rightLeg()).add(linked.leftLeg()).mul(.5F);
            close(torsoBottom,hipMidpoint);close(linked.body(),linked.head());close(linked.head(),linked.hat());
            var torsoWorld=new Matrix4f().translation(linked.body()).rotate(bodyRotation);
            close(torsoWorld.transformPosition(new Vector3f(shoulderR)),linked.rightArm());
            close(torsoWorld.transformPosition(new Vector3f(shoulderL)),linked.leftArm());
            assertEquals(3.8F,linked.rightLeg().distance(linked.leftLeg()),2e-5F);
        }
    }
    @Test void unchangedSourcePosePreservesSetupAnimReferencePivotsAndOffsets(){
        var body=new Vector3f(1.2F,3.2F,-.7F);var rotation=new Quaternionf().rotationZYX(.12F,-.27F,.5F);
        var head=new Vector3f(0,4.2F,0);var hat=new Vector3f(.1F,4.2F,.2F);
        var armR=new Vector3f(-4.7F,5.2F,.8F);var armL=new Vector3f(5.3F,5.2F,-.4F);
        var legR=new Vector3f(-1.9F,12.2F,4);var legL=new Vector3f(1.9F,12.2F,4);
        var linked=NativeReceiverPose.linkHumanoid(body,rotation,new Vector3f(0,12,0),rotation,new Quaternionf(),head,hat,armR,armL,legR,legL);
        close(body,linked.body());close(head,linked.head());close(hat,linked.hat());close(armR,linked.rightArm());close(armL,linked.leftArm());close(legR,linked.rightLeg());close(legL,linked.leftLeg());
    }
    @Test void youngHeadAndBodyRenderScalesKeepTheSamePhysicalNeckAndWaist(){
        var body=NativeReceiverPose.renderPosition(0,0,0,true,false);
        var head=NativeReceiverPose.renderPosition(0,0,0,true,true);
        var hat=NativeReceiverPose.renderPosition(0,0,0,true,false);
        var armR=NativeReceiverPose.renderPosition(-5,2,0,true,false);var armL=NativeReceiverPose.renderPosition(5,2,0,true,false);
        var legR=NativeReceiverPose.renderPosition(-1.9F,12,0,true,false);var legL=NativeReceiverPose.renderPosition(1.9F,12,0,true,false);
        var rotation=new Quaternionf().rotationZYX(.2F,.11F,1.4F);
        var linked=NativeReceiverPose.linkHumanoid(body,new Quaternionf(),new Vector3f(0,6,0),rotation,new Quaternionf(),head,hat,armR,armL,legR,legL);
        var localHead=NativeReceiverPose.modelPosition(linked.head(),true,true);var localHat=NativeReceiverPose.modelPosition(linked.hat(),true,false);
        close(linked.body(),NativeReceiverPose.renderPosition(localHead.x,localHead.y,localHead.z,true,true));
        close(linked.body(),NativeReceiverPose.renderPosition(localHat.x,localHat.y,localHat.z,true,false));
        close(rotation.transform(new Vector3f(0,6,0)).add(linked.body()),new Vector3f(linked.rightLeg()).add(linked.leftLeg()).mul(.5F));
    }
}
