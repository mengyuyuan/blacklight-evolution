package com.codex.blacklight.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackwatchRigPoseTest {
    private static Object solve(double yaw,double pitch,boolean left,double reload,double weight) throws Exception {
        Class<?> type;
        try{type=Class.forName("com.codex.blacklight.client.model.BlackwatchRigPose");}
        catch(ClassNotFoundException absent){fail("A production reference rig solver must drive the real split-arm model");return null;}
        return type.getMethod("solve",double.class,double.class,boolean.class,double.class,double.class).invoke(null,yaw,pitch,left,reload,weight);
    }
    private static void apply(BlackwatchModel model,Object pose) throws Exception {model.getClass().getMethod("applyReferencePose",pose.getClass()).invoke(model,pose);}
    private static Vector3f point(float x,float y,float z,ModelPart... parts){
        var stack=new PoseStack();for(var part:parts)part.translateAndRotate(stack);
        return stack.last().pose().transformPosition(new Vector3f(x/16,y/16,z/16)).mul(16);
    }
    @Test void realBakedModelHasBothSixUnitForearmsAndOneCommonWeaponBasis(){
        var root=BlackwatchModel.layer(false,false).bakeRoot();
        for(String arm:new String[]{"right_arm","left_arm"}){
            assertTrue(root.getChild(arm).hasChild("forearm"),"The arm must bend at its actual elbow: "+arm);
            assertEquals(6,root.getChild(arm).getChild("forearm").y,1e-5);
        }
        assertTrue(root.getChild("body").hasChild("carry"),"A shared carry group must own both grip anchors");
    }
    @Test void actualForwardKinematicsKeepBothWristsOnTheirWeaponGrips() throws Exception {
        for(boolean left:new boolean[]{false,true})for(double yaw:new double[]{-.12,0,.12})for(double pitch:new double[]{-.12,0,.12}){
            var root=BlackwatchModel.layer(false,false).bakeRoot();var model=new BlackwatchModel(root,false);var pose=solve(yaw,pitch,left,0,1);apply(model,pose);
            var carry=root.getChild("body").getChild("carry");
            var main=point(0,0,0,root.getChild("body"),carry,carry.getChild("main_grip"));
            var support=point(0,0,0,root.getChild("body"),carry,carry.getChild("support_grip"));
            var mainArm=root.getChild(left?"left_arm":"right_arm");var supportArm=root.getChild(left?"right_arm":"left_arm");
            assertTrue(point(0,6,0,mainArm,mainArm.getChild("forearm")).distance(main)<.15,"Main wrist must hold the actual model grip");
            assertTrue(point(0,6,0,supportArm,supportArm.getChild("forearm")).distance(support)<.15,"Support wrist must remain on the fore-end while aiming");
        }
    }
    @Test void reloadReleasesTheSupportHandAndReturnsItToTheSameGrip() throws Exception {
        Object start=solve(0,0,false,0,1),middle=solve(0,0,false,.5,1),end=solve(0,0,false,1,1);
        var accessor=start.getClass().getMethod("supportWeight");
        assertEquals(1,((Number)accessor.invoke(start)).doubleValue(),1e-6);
        assertTrue(((Number)accessor.invoke(middle)).doubleValue()<.05,"Support hand must leave the grip during authored reload");
        assertEquals(1,((Number)accessor.invoke(end)).doubleValue(),1e-6);
    }
    @Test void hidingGearClearsPreviousElbowAndCarryTransforms() throws Exception {
        var root=BlackwatchModel.layer(false,false).bakeRoot();var model=new BlackwatchModel(root,false);
        apply(model,solve(.12,-.12,false,.5,1));apply(model,solve(0,0,false,0,0));
        assertFalse(root.getChild("body").getChild("carry").visible);
        for(String arm:new String[]{"right_arm","left_arm"}){
            var elbow=root.getChild(arm).getChild("forearm");
            assertEquals(0,elbow.xRot,1e-7);assertEquals(0,elbow.yRot,1e-7);assertEquals(0,elbow.zRot,1e-7);
        }
    }
    @Test void adjacentReloadFramesDoNotSnapTheSupportElbow() throws Exception {
        var root=BlackwatchModel.layer(false,false).bakeRoot();var model=new BlackwatchModel(root,false);Vector3f previous=null;
        for(int frame=0;frame<=100;frame++){
            apply(model,solve(0,0,false,frame/100d,1));var arm=root.getChild("left_arm");var wrist=point(0,6,0,arm,arm.getChild("forearm"));
            if(previous!=null)assertTrue(previous.distance(wrist)<.9,"Reload blend must not teleport the hand between adjacent frames");previous=wrist;
        }
    }
    @Test void realLookAnglesKeepBothShoulderRootsAttachedToTheTorso() throws Exception {
        for(boolean left:new boolean[]{false,true})for(double yaw:new double[]{-1.2,-.6,0,.6,1.2})for(double pitch:new double[]{-.8,0,.8}){
            var root=BlackwatchModel.layer(false,false).bakeRoot();var model=new BlackwatchModel(root,false);apply(model,solve(yaw,pitch,left,0,1));
            for(boolean right:new boolean[]{false,true}){
                var shoulder=point(0,0,0,root.getChild(right?"right_arm":"left_arm"));
                var socket=point(right?-6:6,0,0,root.getChild("body"));
                assertTrue(shoulder.distance(socket)<.02,"Aiming must never orbit a detached shoulder around the gun");
            }
        }
    }
    @Test void fullLookRangeKeepsFixedShouldersAndBothActualGripsReachable() throws Exception {
        for(boolean left:new boolean[]{false,true})for(double yaw:new double[]{-1.2,-.6,0,.6,1.2})for(double pitch:new double[]{-.8,0,.8}){
            var root=BlackwatchModel.layer(false,false).bakeRoot();var model=new BlackwatchModel(root,false);apply(model,solve(yaw,pitch,left,0,1));
            var body=root.getChild("body");var carry=body.getChild("carry");
            for(boolean main:new boolean[]{false,true}){
                var arm=root.getChild(main==left?"left_arm":"right_arm");
                // Physical arm selection: native right is the main arm for a right-handed actor.
                if(main)arm=root.getChild(left?"left_arm":"right_arm");else arm=root.getChild(left?"right_arm":"left_arm");
                var grip=point(0,0,0,body,carry,carry.getChild(main?"main_grip":"support_grip"));
                assertTrue(point(0,6,0,arm,arm.getChild("forearm")).distance(grip)<.15,"Both real wrist endpoints must reach the rendered weapon at full look angles");
            }
        }
    }
}
