package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.combat.GoddessAttacks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/** The victim first wedges both hands under the thumb, then braces and tumbles upon release. */
public final class GoddessThrowActor {
    private static float ease(float t,float a,float b){float s=Mth.clamp((t-a)/(b-a),0,1);return s*s*(3-2*s);}
    private static void joint(BlacklightPose pose,String id,float x,float y,float z){
        pose.orElseEmpty(id).rotation().rotateXYZ(x,y,z);
    }
    public static BlacklightPose sample(GoddessAttacks attack,float partial){
        float h=attack.age(partial)-attack.rush.grip();
        float side=attack.rush.arm()==1?1:-1;
        float brace=ease(h,0,6)*(1-ease(h,40,47));
        float push=ease(h,7,14)-.8F*ease(h,16,22)+ease(h,24,31)-ease(h,35,44);
        float fly=ease(h,43,48), kick=(float)Math.sin(h*.28F)*brace;
        var pose=new BlacklightPose();
        joint(pose,"Root",-.14F*brace+.87F*fly,side*.27F*brace,side*(.32F*brace-.76F*fly));
        joint(pose,"Torso",-.3F*brace+.12F*push,.25F*side*push,side*.18F*kick);
        joint(pose,"Head",.20F*push-.27F*brace,side*.43F*brace,0);
        joint(pose,"Arm_R",.9F*brace+.21F*push,-.28F*side*brace,.50F*brace+.95F*fly);
        joint(pose,"Arm_L",1.19F*brace+.28F*push,-.38F*side*brace,-.66F*brace-.53F*fly);
        joint(pose,"Hand_R",.94F*brace-.54F*push,0,0);
        joint(pose,"Hand_L",.86F*brace-.4F*push,0,0);
        joint(pose,"Thigh_R",.68F*brace+.32F*kick-.37F*fly,0,.22F*brace);
        joint(pose,"Thigh_L",.53F*brace-.35F*kick+.62F*fly,0,-.17F*brace);
        joint(pose,"Leg_R",-.98F*brace+.23F*kick,0,0);
        joint(pose,"Leg_L",-1.12F*brace-.21F*kick-.55F*fly,0,0);
        return pose;
    }
    public static void hand(PoseStack stack,HumanoidArm arm,GoddessAttacks attack,float partial){
        float h=attack.age(partial)-attack.rush.grip();
        float press=ease(h,2,9)*(1-ease(h,41,48)), push=(float)Math.sin(h*.33)*press;
        int side=arm==HumanoidArm.RIGHT?1:-1;
        stack.translate(-side*.16F*press,.14F*press,.06F*push);
        stack.mulPose(Axis.ZP.rotationDegrees(side*(-23*press+5*push)));
        stack.mulPose(Axis.XP.rotationDegrees(-28*press-8*push));
    }
    private GoddessThrowActor(){}
}
