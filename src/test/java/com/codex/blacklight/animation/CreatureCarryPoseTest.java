package com.codex.blacklight.animation;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreatureCarryPoseTest {
    @Test void sourceCarryContactIsBoundToTheRightHand() {
        var skeleton=BlacklightSkeleton.get();
        for(String key:new String[]{"hold","carry_walk","carry_run"})for(float fraction:new float[]{0,.25F,.5F,.75F}) {
            String clip="consume_"+key+"_actor";
            var pose=MotionLibrary.pose(clip,MotionLibrary.clip(clip).duration()*fraction,false);
            var frame=com.codex.blacklight.combat.ConsumePlacementTrack.contactFrame(key,fraction).orElseThrow();
            var contact=new Vector3f((float)frame[0],(float)frame[1],(float)-frame[2]).mul(.9375F);
            float[] gaps=new float[2];int i=0;
            for(String side:new String[]{"R","L"}) {
                var wrist=skeleton.searchJointByName("Tool_"+side);
                var palm=com.codex.blacklight.client.animation.WeaponModelAnchors.get(com.codex.blacklight.power.PowerForm.CLAWS,
                        "palm_"+side,new Vector3f(side.equals("R")?-.00267F:.00267F,.32362F,.07666F));
                var end=new org.joml.Matrix4f(wrist.getLocalTransform()).invert().transformPosition(palm);
                gaps[i++]=skeleton.getBoundTransformFor(pose,wrist).transformPosition(end).mul(.9375F).distance(contact);
            }
            assertTrue(gaps[0]<gaps[1],key+" must retain its authored right-hand contact");
        }
    }

    @Test void heldCreatureNeverReplacesDistanceDrivenRunningLegs() {
        var skeleton=BlacklightSkeleton.get();
        for(String carry:new String[]{"consume_hold_actor","consume_carry_walk_actor","consume_carry_run_actor"}) {
            assertTrue(MotionLibrary.find(carry).isPresent(),carry);
            var hold=MotionLibrary.pose(carry,.2F,true);
            for(float time:new float[]{.03F,.15F,.33F,.51F}) {
                var movement=MotionLibrary.pose("basic_run",time,true);
                var combined=CarryPoseLayer.holdCreature(hold,movement,false,false);
                for(String joint:new String[]{"Root","Thigh_L","Thigh_R","Leg_L","Leg_R","Knee_L","Knee_R"}) {
                    var bone=skeleton.searchJointByName(joint);
                    assertTrue(skeleton.getBoundTransformFor(movement,bone).equals(skeleton.getBoundTransformFor(combined,bone),.00001F),carry+joint);
                }
                var waist=skeleton.getBoundTransformFor(movement,skeleton.torso).getTranslation(new Vector3f());
                assertTrue(waist.equals(skeleton.getBoundTransformFor(combined,skeleton.torso).getTranslation(new Vector3f()),.0001F),carry);
            }
        }
    }

    @Test void glideBodyLegsAndFreeArmKeepTheirFlightPoseWithEitherGrip() {
        var skeleton=BlacklightSkeleton.get();
        var hold=MotionLibrary.pose("consume_carry_run_actor",.2F,true);
        for(boolean right:new boolean[]{false,true})for(String glide:new String[]{"basic_glide_forward","basic_glide_backward","basic_glide_left","basic_glide_right"}) {
            var movement=MotionLibrary.pose(glide,.3F,true);
            var before=movement.copy();var oldHold=hold.copy();
            var combined=CarryPoseLayer.holdCreature(hold,movement,true,right);
            String free=right?"L":"R",grip=right?"R":"L";
            for(String joint:new String[]{"Root","Torso","Chest","Head","Thigh_L","Thigh_R","Leg_L","Leg_R","Hand_"+free,"Tool_"+free}) {
                var bone=skeleton.searchJointByName(joint);
                assertTrue(skeleton.getBoundTransformFor(movement,bone).equals(skeleton.getBoundTransformFor(combined,bone),.00001F),glide+joint);
            }
            assertTrue(combined.orElseEmpty("Arm_"+grip).toMatrix().equals(hold.orElseEmpty("Arm_"+grip).toMatrix(),.00001F));
            for(var bone:skeleton.bones()) {
                assertTrue(skeleton.getBoundTransformFor(before,bone).equals(skeleton.getBoundTransformFor(movement,bone),.00001F),"movement input changed");
                assertTrue(skeleton.getBoundTransformFor(oldHold,bone).equals(skeleton.getBoundTransformFor(hold,bone),.00001F),"hold input changed");
            }
        }
    }
}
