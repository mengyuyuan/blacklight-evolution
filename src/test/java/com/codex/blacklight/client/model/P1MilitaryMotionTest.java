package com.codex.blacklight.client.model;

import com.codex.blacklight.animation.P1MilitaryMotion;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class P1MilitaryMotionTest {
    @TempDir Path directory;
    private JsonObject fixture(){
        var root=new JsonObject();root.addProperty("schema",1);root.addProperty("sourceSha256","0".repeat(64));
        var joints=new JsonArray();
        for(String name:List.of("Motion_Root","Spine_3","Neck","Head","Wrist_R","Wrist_L")){
            var joint=new JsonObject();joint.addProperty("name",name);joint.addProperty("parent",0);
            joint.add("position",JsonParser.parseString("[0,0,0]"));joint.add("rotation",JsonParser.parseString("[1,0,0,0,1,0,0,0,1]"));joints.add(joint);
        }root.add("joints",joints);var clips=new JsonObject();
        for(String key:List.of("aim","fire","reload","reload_kneel","melee")){
            var clip=new JsonObject();clip.addProperty("frames",20);clip.addProperty("fps",30);clip.addProperty("cyclic",false);clip.add("bones",new JsonObject());clips.add(key,clip);
        }root.add("clips",clips);return root;
    }
    private P1MilitaryMotion read(JsonObject fixture)throws Exception{var file=directory.resolve("motion.json");Files.writeString(file,fixture.toString());return P1MilitaryMotion.read(file);}
    @Test void samplesKeysAndShortestQuaternionArcWithoutChangingReference()throws Exception{
        var data=fixture();var bones=data.getAsJsonObject("clips").getAsJsonObject("melee").getAsJsonObject("bones");
        bones.add("Wrist_R",JsonParser.parseString("{\"position\":{\"times\":[0,20],\"values\":[[0,0,0],[2,4,6]]},\"rotation\":{\"times\":[0,20],\"values\":[[0,0,0,1],[0,0,0,-1]]}}"));
        var motion=read(data);var halfway=motion.sample("melee",10);
        assertEquals(1,halfway.joint("Wrist_R").position().x,1e-9);assertEquals(2,halfway.joint("Wrist_R").position().y,1e-9);
        assertEquals(1,Math.abs(halfway.joint("Wrist_R").rotation().w),1e-9);assertEquals(0,halfway.rest("Wrist_R").position().x,1e-9);
        assertEquals(2,motion.sample("melee",999).joint("Wrist_R").position().x,1e-9);
    }
    @Test void refusesCyclicHierarchy() {var data=fixture();data.getAsJsonArray("joints").get(2).getAsJsonObject().addProperty("parent",2);assertThrows(IllegalArgumentException.class,()->read(data));}
    @Test void crossfadeRotatesHierarchyWithoutShorteningTheChildBone()throws Exception{
        var data=fixture();data.getAsJsonArray("joints").get(4).getAsJsonObject().add("position",JsonParser.parseString("[2,0,0]"));
        data.getAsJsonObject("clips").getAsJsonObject("melee").getAsJsonObject("bones").add("Motion_Root",JsonParser.parseString("{\"rotation\":{\"times\":[0],\"values\":[[0,0,1,0]]}}"));
        var motion=read(data);var frame=motion.blend("aim",0,"melee",0,.5);
        assertEquals(2,frame.joint("Wrist_R").position().length(),1e-9);
        assertEquals(0,frame.joint("Wrist_R").position().x,1e-9);
        assertEquals(2,Math.abs(frame.joint("Wrist_R").position().y),1e-9);
        assertEquals(2,motion.sample("aim",0).joint("Wrist_R").position().x,1e-9);
    }
    @Test void refusesUnorderedKeys(){var data=fixture();data.getAsJsonObject("clips").getAsJsonObject("melee").getAsJsonObject("bones").add("Wrist_R",JsonParser.parseString("{\"position\":{\"times\":[10,0],\"values\":[[0,0,0],[1,0,0]]}}"));assertThrows(IllegalArgumentException.class,()->read(data));}
    @Test void disabledOriginalMotionKeepsAuthoredRig(){var base=BlackwatchRigPose.solve(0,0,false,0,1);assertSame(base,BlackwatchRigPose.original(base,null,1,0,0,0));}
    @Test void verifiesLocalOriginalAllClipFramesAndRetargetedLimbs()throws Exception{
        String path=System.getProperty("blacklight.test.p1MilitaryMotion");Assumptions.assumeTrue(path!=null,"Private original source is not part of test distribution");
        var motion=P1MilitaryMotion.read(Path.of(path));int checked=0;
        for(String clip:List.of("aim","fire","reload","reload_kneel","melee","walk","run","radio","hit","crouch","fire_kneel"))for(double f=0;f<=motion.frames(clip);f+=.5){
            var frame=motion.sample(clip,f);assertEquals(67,frame.joints().size());
            for(var j:frame.joints().values()){assertTrue(j.position().isFinite());assertEquals(1,j.rotation().lengthSquared(),1e-6);}
            for(boolean left:List.of(false,true)){
                double reload=clip.startsWith("reload")?f/motion.frames(clip):0;
                var base=BlackwatchRigPose.animated(0,0,left,reload,1,0,0,0,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,1);
                var pose=BlackwatchRigPose.original(base,frame,1,reload,0,0);
                for(var arm:List.of(pose.left(),pose.right()))for(var v:List.of(arm.shoulder(),arm.upper(),arm.forearm()))assertTrue(Double.isFinite(v.x()+v.y()+v.z()));
                assertTrue(pose.carryPosition().length()<40,"Weapon stayed near torso");
            }checked++;
        }assertTrue(checked>800);
    }
    @Test void originalStrikeBlendKeepsBothHandsOnActualGunAtEveryFrame()throws Exception{
        String path=System.getProperty("blacklight.test.p1MilitaryMotion");Assumptions.assumeTrue(path!=null);
        var motion=P1MilitaryMotion.read(Path.of(path));
        for(boolean left:List.of(false,true)){
            var root=BlackwatchModel.layer(false,false).bakeRoot();var model=new BlackwatchModel(root,false);
            for(int f=0;f<=60;f++){
                double age=f/60d,influence=Math.max(0,Math.min(1,Math.min(age/.1,(1-age)/.1)));
                var base=BlackwatchRigPose.animated(.2,-.1,left,0,1,0,0,0,Double.POSITIVE_INFINITY,age,0);
                model.applyReferencePose(BlackwatchRigPose.original(base,motion.sample("melee",Math.min(44,age*45)),influence,0,.2,-.1));
                var body=root.getChild("body");var carry=body.getChild("carry");
                for(boolean main:List.of(false,true)){
                    var arm=root.getChild(main?(left?"left_arm":"right_arm"):(left?"right_arm":"left_arm"));
                    assertTrue(point(0,6,0,arm,arm.getChild("forearm")).distance(point(0,0,0,body,carry,carry.getChild(main?"main_grip":"support_grip")))<.15,"Original blend keeps rigid weapon grip at frame "+f);
                }
            }
        }
    }
    @Test void originalStrikeFeetFollowSourceContactWhileHipAndKneeStayAttached()throws Exception{
        String path=System.getProperty("blacklight.test.p1MilitaryMotion");Assumptions.assumeTrue(path!=null);
        var motion=P1MilitaryMotion.read(Path.of(path));double maximumKnee=0;
        for(boolean mirror:List.of(false,true)){
            var root=BlackwatchModel.layer(false,false).bakeRoot();var model=new BlackwatchModel(root,false);
            for(String clip:List.of("melee","reload","reload_kneel","crouch","fire_kneel"))for(double f=0;f<=(clip.equals("melee")?44:motion.frames(clip));f+=.5){
                var frame=motion.sample(clip,f);var base=BlackwatchRigPose.solve(0,0,mirror,0,1);
                model.applyReferencePose(BlackwatchRigPose.original(base,frame,1,0,0,0));
                for(boolean right:List.of(false,true)){
                    var thigh=root.getChild(right?"right_leg":"left_leg");var shin=thigh.getChild("shin");var foot=shin.getChild("foot");
                    assertTrue(point(0,0,0,thigh).distance(point(right?-1.9F:1.9F,12,0,root.getChild("body")))<.03,"Hip remains on waist");
                    assertEquals(6,point(0,0,0,thigh).distance(point(0,0,0,thigh,shin)),.001);
                    assertEquals(5,point(0,0,0,thigh,shin).distance(point(0,0,0,thigh,shin,foot)),.001);
                    String side=right!=mirror?"R":"L";
                    var wantedRotation=new org.joml.Quaterniond(frame.joint("Ankle_"+side).rotation()).mul(new org.joml.Quaterniond(frame.rest("Ankle_"+side).rotation()).invert());
                    wantedRotation.set(-wantedRotation.x,wantedRotation.y,-wantedRotation.z,wantedRotation.w);if(mirror)wantedRotation.set(wantedRotation.x,-wantedRotation.y,-wantedRotation.z,wantedRotation.w);
                    var actualRotation=new org.joml.Quaterniond().rotationZYX(thigh.zRot,thigh.yRot,thigh.xRot).mul(new org.joml.Quaterniond().rotationZYX(shin.zRot,shin.yRot,shin.xRot)).mul(new org.joml.Quaterniond().rotationZYX(foot.zRot,foot.yRot,foot.xRot));
                    assertEquals(1,Math.abs(actualRotation.dot(wantedRotation)),.0001,"Independent boot follows source orientation");
                    for(float x:new float[]{-2.1F,2.1F})for(float z:new float[]{-2.55F,2.2F})
                        assertTrue(point(x,1.05F,z,thigh,shin,foot).y<=24.065,"Sole does not penetrate support plane at "+f+" : "+point(x,1.05F,z,thigh,shin,foot));
                    var ankle=new org.joml.Vector3d(frame.joint("Ankle_"+side).position()).sub(frame.rest("Ankle_"+side).position());
                    var travel=new org.joml.Vector3d(frame.joint("Motion_Root").position()).sub(frame.rest("Motion_Root").position());
                    var actual=point(0,0,0,thigh,shin,foot);
                    assertEquals((right?-1.9:1.9)+(ankle.x*13-travel.x*16)*(mirror?-1:1),actual.x,.15,"Root travel removed from foot X");
                    assertEquals(ankle.z*13-travel.z*16,actual.z,.15,"Root travel removed from foot Z at "+f);
                    maximumKnee=Math.max(maximumKnee,Math.abs(shin.xRot));
                }
            }
        }assertTrue(maximumKnee>.15,"The source strike actually bends its knees");
    }
    @Test void fallbackAndVehicleSeatsResetBothKneesAndAnkles()throws Exception{
        var root=BlackwatchModel.layer(false,false).bakeRoot();var model=new BlackwatchModel(root,false);
        for(String side:List.of("right_leg","left_leg")){var shin=root.getChild(side).getChild("shin");shin.setRotation(1,.2F,.1F);shin.getChild("foot").setRotation(.3F,.4F,.5F);}
        model.applyReferencePose(BlackwatchRigPose.solve(0,0,false,0,0));
        for(String side:List.of("right_leg","left_leg")){var shin=root.getChild(side).getChild("shin");assertEquals(0,shin.xRot);assertEquals(0,shin.getChild("foot").xRot);shin.xRot=1;shin.getChild("foot").xRot=.5F;}
        model.applyVehicleSeatPose(true);
        for(String side:List.of("right_leg","left_leg")){var shin=root.getChild(side).getChild("shin");assertEquals(0,shin.xRot);assertEquals(0,shin.getChild("foot").xRot);}
    }
    @Test void kneelingReloadReturnsToCrouchWithoutStandingBeforeTheServer()throws Exception{
        String path=System.getProperty("blacklight.test.p1MilitaryMotion");Assumptions.assumeTrue(path!=null);
        var motion=P1MilitaryMotion.read(Path.of(path));
        var crouch=motion.sample("crouch",11);var end=BlackwatchModel.kneelingFrame(motion,1,1-1e-9,Double.POSITIVE_INFINITY);
        for(String name:List.of("Pelvis","Ankle_R","Ankle_L","Spine_3"))assertTrue(end.joint(name).position().distance(crouch.joint(name).position())<.00001,"Reload ends in crouch, not the standing fallback");
        BlackwatchRigPose.Vec previous=null;
        for(int f=0;f<=12;f++){
            double reload=.9+f/120d;reload=Math.min(1-1e-9,reload);
            var base=BlackwatchRigPose.animated(0,0,false,reload,1,0,0,1,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,0);
            var pose=BlackwatchRigPose.original(base,BlackwatchModel.kneelingFrame(motion,1,reload,Double.POSITIVE_INFINITY),1,reload,0,0);
            if(previous!=null)assertTrue(pose.torsoPosition().sub(previous).length()<.7,"No one-frame body pop at reload completion");previous=pose.torsoPosition();
        }
    }
    private static org.joml.Vector3f point(float x,float y,float z,net.minecraft.client.model.geom.ModelPart... parts){
        var stack=new com.mojang.blaze3d.vertex.PoseStack();for(var part:parts)part.translateAndRotate(stack);
        return stack.last().pose().transformPosition(new org.joml.Vector3f(x/16,y/16,z/16)).mul(16);
    }
}
