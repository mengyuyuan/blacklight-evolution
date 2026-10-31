package com.codex.blacklight.firearms;

import org.junit.jupiter.api.Test;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.*;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;

class FirearmHandPoseTest {
    private static Object hand(String weapon,boolean left,boolean support,double progress)throws Exception{
        Class<?> type;
        try{type=Class.forName("com.codex.blacklight.firearms.FirearmHandPose");}
        catch(ClassNotFoundException missing){fail("The live renderer and asset preview need one shared real hand transform");return null;}
        return type.getMethod(support?"support":"primary",String.class,boolean.class,double.class).invoke(null,weapon,left,progress);
    }
    private static double number(Object hand,String name)throws Exception{return ((Number)hand.getClass().getMethod(name).invoke(hand)).doubleValue();}
    @Test void sourceFamilyAndRealJsonGripPointsControlTheHandSpacing()throws Exception{
        var main=hand("military_rifle",false,false,0);var support=hand("military_rifle",false,true,0);
        assertTrue(number(support,"z")<number(main,"z")-.2,"Support hand belongs on the actual forward handguard");
        var launcher=hand("rocket_launcher",false,true,0);
        assertNotEquals(number(support,"y"),number(launcher,"y"),.001,"A launcher's forward grip is lower than a rifle's handguard");
    }
    @Test void physicalHandTransformsMirrorBothPlayerArmTypes()throws Exception{
        for(boolean support:new boolean[]{false,true}){
            var right=hand("military_rifle",false,support,0);var left=hand("military_rifle",true,support,0);
            assertEquals(-number(right,"x"),number(left,"x"),1e-7);assertEquals(number(right,"y"),number(left,"y"),1e-7);assertEquals(number(right,"z"),number(left,"z"),1e-7);
            assertNotEquals(right.getClass().getMethod("left").invoke(right),left.getClass().getMethod("left").invoke(left));
        }
    }
    @Test void actualSupportHandLeavesItsGripAndReturnsWithoutMovingThePrimaryHand()throws Exception{
        var start=hand("military_rifle",false,true,0);var middle=hand("military_rifle",false,true,.5);var end=hand("military_rifle",false,true,1);
        assertTrue(Math.abs(number(start,"z")-number(middle,"z"))>.15);assertEquals(number(start,"z"),number(end,"z"),1e-7);
        assertEquals(number(hand("military_rifle",false,false,0),"z"),number(hand("military_rifle",false,false,.5),"z"),1e-7);
    }
    @Test void actualStandardAndSlimPlayerArmsReachTheirRenderedGripPoints()throws Exception{
        String[] weapons={"military_rifle","military_carbine","military_machine_gun","grenade_launcher","rocket_launcher","missile_launcher","thermobaric_launcher","infection_launcher"};
        for(boolean slim:new boolean[]{false,true})for(boolean leftMain:new boolean[]{false,true})for(boolean support:new boolean[]{false,true})for(String weapon:weapons){
            var h=hand(weapon,leftMain,support,.5);boolean left=(boolean)h.getClass().getMethod("left").invoke(h);
            var root=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot();var arm=root.getChild(left?"left_arm":"right_arm");
            arm.setRotation(.17F,-.09F,.1F);arm.xScale=1.05F;
            var stack=new PoseStack();var transforms=Class.forName("com.codex.blacklight.firearms.FirearmHandTransforms");
            transforms.getMethod("apply",PoseStack.class,h.getClass(),net.minecraft.client.model.geom.ModelPart.class,boolean.class).invoke(null,stack,h,arm,slim);
            arm.translateAndRotate(stack);
            var actual=stack.last().pose().transformPosition(new Vector3f((left?1:-1)*(slim?.5F:1F)/16,10/16F,0));
            var expected=new Vector3f((float)number(h,"x"),(float)number(h,"y"),(float)number(h,"z"));
            assertTrue(actual.distance(expected)<1e-5,"Actual standard/slim arm FK must place the wrist on the exact grip");
        }
    }
    @Test void bothForearmsApproachTheWeaponFromBelowInsteadOfCoveringItsSightline()throws Exception{
        for(boolean slim:new boolean[]{false,true})for(boolean leftMain:new boolean[]{false,true})for(boolean support:new boolean[]{false,true}){
            var h=hand("military_rifle",leftMain,support,0);boolean left=(boolean)h.getClass().getMethod("left").invoke(h);
            var root=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot();var arm=root.getChild(left?"left_arm":"right_arm");
            arm.resetPose();arm.setRotation(0,0,0);var stack=new PoseStack();var transforms=Class.forName("com.codex.blacklight.firearms.FirearmHandTransforms");
            transforms.getMethod("apply",PoseStack.class,h.getClass(),net.minecraft.client.model.geom.ModelPart.class,boolean.class).invoke(null,stack,h,arm,slim);arm.translateAndRotate(stack);
            var shoulder=stack.last().pose().transformPosition(new Vector3f((left?1:-1)*(slim?.5F:1F)/16,0,0));
            assertTrue(shoulder.y<number(h,"y")-.15,"The real forearm must rise from below the grip, leaving the sightline clear");
        }
    }
}
