package com.codex.blacklight.client.model;

import com.codex.blacklight.animation.P1MilitaryMotion;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class BlackwatchGaitTest {
    private P1MilitaryMotion source()throws Exception{
        var path=System.getProperty("blacklight.test.p1MilitaryMotion");Assumptions.assumeTrue(path!=null);return P1MilitaryMotion.read(Path.of(path));
    }
    @Test void stationaryFeetDoNotCycleAndTeleportDoesNotInventStride(){
        var gait=new BlackwatchGait();for(int t=0;t<=80;t++)gait.update(0,t*.07,t,true,null);
        double phase=gait.phase();for(int t=81;t<=160;t++)gait.update(0,5.6,t,true,null);
        assertEquals(phase,gait.phase(),1e-9);assertTrue(gait.weight()<.001);
        gait.update(100,100,161,true,null);assertEquals(phase,gait.phase(),1e-9);assertEquals(0,gait.weight());
    }
    @Test void distanceClockIsStableAcrossRenderRatesAndActors(){
        var slow=new BlackwatchGait();var fast=new BlackwatchGait();var stationary=new BlackwatchGait();
        for(int i=0;i<=180;i++){double t=i/3d;slow.update(0,t*.11,t,true,null);stationary.update(0,0,t,true,null);}
        for(int i=0;i<=360;i++){double t=i/6d;fast.update(0,t*.11,t,true,null);}
        double difference=Math.abs(slow.phase()-fast.phase());assertTrue(Math.min(difference,1-difference)<.015);
        assertEquals(slow.running(),fast.running(),.001);assertEquals(0,stationary.phase());
    }
    @Test void airborneAndReacquiredActorsNeverFastForwardAnUnseenPath(){
        var gait=new BlackwatchGait();gait.update(0,0,0,true,null);gait.update(0,.1,1,true,null);double phase=gait.phase();
        for(int t=2;t<=30;t++)gait.update(0,t*.1,t,false,null);assertEquals(phase,gait.phase());
        gait.update(0,10,100,true,null);assertEquals(phase,gait.phase());assertEquals(0,gait.weight());
        gait.reset();gait.update(50,50,110,true,null);assertEquals(phase,gait.phase());assertEquals(0,gait.weight());
    }
    @Test void originalStridesAlignLeadingFeetAndCloseBothLoopSeams()throws Exception{
        var motion=source();assertTrue(motion.rootTravel("run")>motion.rootTravel("walk"));
        for(double run:new double[]{0,.5,1}){
            var start=motion.locomotion(0,run);assertTrue(start.joint("Ankle_R").position().z>start.joint("Ankle_L").position().z,"same leading foot while changing speed");
            for(double seam:new double[]{.5,1}){
                var a=motion.locomotion(seam-1e-6,run);var b=motion.locomotion(seam+1e-6,run);
                for(String joint:List.of("Ankle_R","Ankle_L","Wrist_R","Wrist_L","Pelvis")){
                    var pa=new org.joml.Vector3d(a.joint(joint).position()).sub(a.joint("Motion_Root").position());
                    var pb=new org.joml.Vector3d(b.joint(joint).position()).sub(b.joint("Motion_Root").position());
                    assertTrue(pa.distance(pb)<.0005,joint+" positional loop seam");assertEquals(1,Math.abs(a.joint(joint).rotation().dot(b.joint(joint).rotation())),1e-5);
                }
            }
        }
    }
    @Test void retargetedLocomotionRemovesAnyRootWorldOffsetAndKeepsFeetAboveGround()throws Exception{
        var motion=source();var root=BlackwatchModel.layer(false,false).bakeRoot();var model=new BlackwatchModel(root,false);
        for(boolean left:List.of(false,true))for(double run:new double[]{0,.5,1})for(int i=0;i<40;i++){
            var frame=motion.locomotion(i/40d,run);var base=BlackwatchRigPose.original(BlackwatchRigPose.solve(0,0,left,0,1),frame,1,0,0,0,0);
            var pose=BlackwatchLegPose.locomotion(base,frame,1);var shifted=new HashMap<String,P1MilitaryMotion.Transform>();
            frame.joints().forEach((name,t)->shifted.put(name,new P1MilitaryMotion.Transform(new org.joml.Vector3d(t.position()).add(100,0,-70),new org.joml.Quaterniond(t.rotation()))));
            var same=BlackwatchLegPose.locomotion(base,new P1MilitaryMotion.Frame(shifted,frame.reference()),1);
            assertEquals(pose.rightLegRotation().x(),same.rightLegRotation().x(),1e-8);assertEquals(pose.torsoPosition().z(),same.torsoPosition().z(),1e-8);
            model.applyReferencePose(pose);
            for(String side:List.of("right_leg","left_leg")){
                var thigh=root.getChild(side);var shin=thigh.getChild("shin");var boot=shin.getChild("foot");
                assertEquals(6,point(0,0,0,thigh).distance(point(0,0,0,thigh,shin)),.001);
                assertEquals(5,point(0,0,0,thigh,shin).distance(point(0,0,0,thigh,shin,boot)),.001);
                for(float x:new float[]{-2.1F,2.1F})for(float z:new float[]{-2.55F,2.2F})assertTrue(point(x,1.05F,z,thigh,shin,boot).y<=24.065,"sole at run="+run+" phase="+i);
            }
        }
    }
    @Test void radioReleasesOnlySupportHandAndOriginalHandCanReachTheHead()throws Exception{
        var motion=source();double nearest=100,release=0;String closest="";
        for(boolean left:List.of(false,true)){
            var root=BlackwatchModel.layer(false,false).bakeRoot();var model=new BlackwatchModel(root,false);
            for(int f=0;f<=108;f+=2){
                var pose=BlackwatchRigPose.radioOriginal(BlackwatchRigPose.solve(0,0,left,0,1),motion,f/motion.frames("radio"),1,0,0);model.applyReferencePose(pose);
                var main=root.getChild(left?"left_arm":"right_arm");var support=root.getChild(left?"right_arm":"left_arm");var body=root.getChild("body");var carry=body.getChild("carry");
                assertTrue(point(0,6,0,main,main.getChild("forearm")).distance(point(0,0,0,body,carry,carry.getChild("main_grip")))<.15);
                var hand=point(0,6,0,support,support.getChild("forearm"));release=Math.max(release,hand.distance(point(0,0,0,body,carry,carry.getChild("support_grip"))));
                double distance=hand.distance(point(left?-4:4,-3.5F,0,root.getChild("head")));
                if(distance<nearest){nearest=distance;closest=" frame="+f+" left="+left+" hand="+hand+" ear="+point(left?-4:4,-3.5F,0,root.getChild("head"))+" pose="+pose.left();}
            }
        }assertTrue(release>3,"support hand leaves firearm");assertTrue(nearest<1.5,"source support hand approaches head: "+nearest+closest);
    }
    @Test void authoredRadioAndImpactPreserveGunGripAndWaistWithoutPrivateData(){
        for(boolean left:List.of(false,true)){
            var root=BlackwatchModel.layer(false,false).bakeRoot();var model=new BlackwatchModel(root,false);var base=BlackwatchRigPose.solve(.15,-.1,left,0,1);
            for(double weight:new double[]{0,.25,.5,.75,1}){
                model.applyReferencePose(BlackwatchRigPose.flinch(BlackwatchRigPose.radio(base,weight),.7));
                var body=root.getChild("body");var carry=body.getChild("carry");var main=root.getChild(left?"left_arm":"right_arm");
                assertTrue(point(0,6,0,main,main.getChild("forearm")).distance(point(0,0,0,body,carry,carry.getChild("main_grip")))<.15);
                assertTrue(point(0,12,0,body).distance(new org.joml.Vector3f(0,12,0))<.03);
            }
        }
    }
    @Test void standingHitRecoversWithoutFoldingTheTorsoOrDetachingMovingFeet()throws Exception{
        var motion=source();
        for(boolean left:List.of(false,true))for(double run:new double[]{0,.5,1})for(int i=0;i<24;i++){
            var frame=motion.locomotion(i/24d,run);var base=BlackwatchRigPose.original(BlackwatchRigPose.solve(0,0,left,0,1),frame,1,0,0,0,0);
            for(double elapsed:new double[]{-1,0,1,3,10,18,19.99,20,59,Double.POSITIVE_INFINITY}){
                var hit=BlackwatchRigPose.standingHit(base,elapsed,0);
                if(elapsed<=0||elapsed>=20)assertSame(base,hit,"finished reaction returns to ongoing gait");
                assertEquals(base.rightLegRotation(),hit.rightLegRotation());assertEquals(base.leftLegRotation(),hit.leftLegRotation());
                var root=BlackwatchModel.layer(false,false).bakeRoot();var model=new BlackwatchModel(root,false);model.applyReferencePose(BlackwatchLegPose.locomotion(hit,frame,1));
                assertTrue(point(0,-4,0,root.getChild("head")).y<8,"standing head stays above the waist through hit and gait");
                var body=root.getChild("body");var carry=body.getChild("carry");
                for(boolean primary:List.of(true,false)){
                    var arm=root.getChild((primary==left)?"left_arm":"right_arm");
                    assertTrue(point(0,6,0,arm,arm.getChild("forearm")).distance(point(0,0,0,body,carry,carry.getChild(primary?"main_grip":"support_grip")))<.15,"both hands remain on firearm during standing hit");
                }
                for(String side:List.of("right_leg","left_leg")){
                    var thigh=root.getChild(side);var shin=thigh.getChild("shin");var foot=shin.getChild("foot");
                    for(float x:new float[]{-2.1F,2.1F})for(float z:new float[]{-2.55F,2.2F})assertTrue(point(x,1.05F,z,thigh,shin,foot).y<=24.065);
                }
            }
        }
    }
    @Test void authoredStrideSolesStaySupportedWithoutMovingHipSocketsOrChangingLimbLengths(){
        var root=BlackwatchModel.layer(false,false).bakeRoot();var model=new BlackwatchModel(root,false);
        for(double run:new double[]{0,.5,1})for(double crouch:new double[]{0,.5,1})for(int i=0;i<36;i++){
            var base=BlackwatchRigPose.animated(.15,-.1,false,0,1,i/20d,.5,crouch,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,.3,i/36d,run);
            var supported=BlackwatchLegPose.support(BlackwatchRigPose.standingHit(base,6,0));
            assertEquals(base.rightLegPosition(),supported.rightLegPosition());assertEquals(base.leftLegPosition(),supported.leftLegPosition());model.applyReferencePose(supported);
            for(String side:List.of("right_leg","left_leg")){
                var thigh=root.getChild(side);var shin=thigh.getChild("shin");var boot=shin.getChild("foot");
                assertEquals(6,point(0,0,0,thigh).distance(point(0,0,0,thigh,shin)),.001);
                assertEquals(5,point(0,0,0,thigh,shin).distance(point(0,0,0,thigh,shin,boot)),.001);
                for(float x:new float[]{-2.1F,2.1F})for(float z:new float[]{-2.55F,2.2F})assertTrue(point(x,1.05F,z,thigh,shin,boot).y<=24.065,"supported authored sole at run="+run+" crouch="+crouch+" phase="+i);
            }
        }
    }
    private static org.joml.Vector3f point(float x,float y,float z,net.minecraft.client.model.geom.ModelPart... parts){
        var stack=new com.mojang.blaze3d.vertex.PoseStack();for(var part:parts)part.translateAndRotate(stack);
        return stack.last().pose().transformPosition(new org.joml.Vector3f(x/16,y/16,z/16)).mul(16);
    }
}
