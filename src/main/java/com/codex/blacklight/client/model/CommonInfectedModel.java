package com.codex.blacklight.client.model;

import com.codex.blacklight.entity.BlacklightCommonInfected;
import com.codex.blacklight.infection.CommonInfectedMotion;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.HumanoidArm;

/** Block-person proportions, continuous clothing UVs and a few connected, early-stage tissue patches. */
public final class CommonInfectedModel extends HumanoidModel<BlacklightCommonInfected>{
    private final ModelPart elbowR,elbowL,kneeR,kneeL;
    public CommonInfectedModel(ModelPart root){super(root);elbowR=rightArm.getChild("forearm");elbowL=leftArm.getChild("forearm");kneeR=rightLeg.getChild("shin");kneeL=leftLeg.getChild("shin");}
    public static LayerDefinition layer(){
        var mesh=HumanoidModel.createMesh(CubeDeformation.NONE,0);var root=mesh.getRoot();
        var right=root.addOrReplaceChild("right_arm",CubeListBuilder.create().texOffs(40,16).addBox(-3,-2,-2,4,6,4),PartPose.offset(-5,2,0));
        right.addOrReplaceChild("forearm",CubeListBuilder.create().texOffs(40,22).addBox(-3,0,-2,4,6,4),PartPose.offset(0,4,0));
        var left=root.addOrReplaceChild("left_arm",CubeListBuilder.create().texOffs(32,48).addBox(-1,-2,-2,4,6,4),PartPose.offset(5,2,0));
        var forearm=left.addOrReplaceChild("forearm",CubeListBuilder.create().texOffs(32,54).addBox(-1,0,-2,4,6,4),PartPose.offset(0,4,0));
        var rleg=root.addOrReplaceChild("right_leg",CubeListBuilder.create().texOffs(0,16).addBox(-2,0,-2,4,6,4),PartPose.offset(-1.9F,12,0));
        rleg.addOrReplaceChild("shin",CubeListBuilder.create().texOffs(0,22).addBox(-2,0,-2,4,6,4),PartPose.offset(0,6,0));
        var lleg=root.addOrReplaceChild("left_leg",CubeListBuilder.create().texOffs(16,48).addBox(-2,0,-2,4,6,4),PartPose.offset(1.9F,12,0));
        lleg.addOrReplaceChild("shin",CubeListBuilder.create().texOffs(16,54).addBox(-2,0,-2,4,6,4),PartPose.offset(0,6,0));
        root.getChild("body").addOrReplaceChild("sternum",CubeListBuilder.create().texOffs(64,0).addBox(.3F,2,-2.35F,3.7F,5.8F,.8F)
                .texOffs(64,16).addBox(2,1.3F,-2.65F,2,3,.9F),PartPose.ZERO);
        root.getChild("body").addOrReplaceChild("back_tissue",CubeListBuilder.create().texOffs(64,0).addBox(-2,1,1.8F,3,7,.8F),PartPose.ZERO);
        left.addOrReplaceChild("shoulder_tissue",CubeListBuilder.create().texOffs(80,0).addBox(-1.2F,-2.3F,-2.3F,4.5F,3.7F,4.6F),PartPose.ZERO);
        forearm.addOrReplaceChild("hand_tissue",CubeListBuilder.create().texOffs(80,16).addBox(-1.25F,2,-2.2F,4.5F,4,4.4F)
                .texOffs(64,16).addBox(-.7F,5.8F,-2.5F,1,1.5F,1).addBox(1,5.8F,-2.5F,1,1.5F,1),PartPose.ZERO);
        root.getChild("head").addOrReplaceChild("temple",CubeListBuilder.create().texOffs(64,24).addBox(3.75F,-6,-3.5F,.5F,4,3),PartPose.ZERO);
        return LayerDefinition.create(mesh,128,64);
    }
    @Override public void setupAnim(BlacklightCommonInfected e,float swing,float amount,float age,float yaw,float pitch){
        head.getAllParts().forEach(ModelPart::resetPose);body.getAllParts().forEach(ModelPart::resetPose);
        for(var limb:new ModelPart[]{rightArm,leftArm,rightLeg,leftLeg})limb.getAllParts().forEach(ModelPart::resetPose);
        hat.resetPose();hat.visible=true;
        float partial=com.codex.blacklight.forge.WingMath.clamp(age-e.tickCount,0,1);
        float attack=e.attackStarted()<0?-1:(float)(e.level().getGameTime()-e.attackStarted())+partial;
        var p=CommonInfectedMotion.sample(age,swing,amount,e.isAggressive(),attack,e.attackHand());
        head.yRot=yaw*((float)Math.PI/180);head.xRot=pitch*((float)Math.PI/180)+p.head();head.zRot=p.roll();
        body.xRot=p.torso();body.yRot=p.twist();body.zRot=p.roll();
        // Shoulders follow the leaning chest, while elbows and knees retain their own local pivots.
        float sin=(float)Math.sin(body.xRot),cos=(float)Math.cos(body.xRot);
        body.y=12-12*cos+p.bob();body.z=-12*sin;
        head.y=body.y;head.z=body.z;
        rightArm.y=leftArm.y=body.y+2*cos;
        rightArm.x=-5*(float)Math.cos(p.twist());leftArm.x=-rightArm.x;
        rightArm.z=body.z+2*sin+5*(float)Math.sin(p.twist());leftArm.z=body.z+2*sin-5*(float)Math.sin(p.twist());
        rightArm.xRot=p.armR()+body.xRot;leftArm.xRot=p.armL()+body.xRot;
        rightArm.zRot=.06F+p.roll();leftArm.zRot=-.12F+p.roll();
        elbowR.xRot=p.elbowR();elbowL.xRot=p.elbowL();
        rightLeg.xRot=p.legR();leftLeg.xRot=p.legL();kneeR.xRot=p.kneeR();kneeL.xRot=p.kneeL();
        hat.copyFrom(head);
        if(e.isPassenger()){rightLeg.xRot=leftLeg.xRot=-1.1F;rightLeg.yRot=.25F;leftLeg.yRot=-.25F;kneeR.xRot=kneeL.xRot=.5F;}
        if(e.hurtTime>0){float flinch=(float)Math.sin((e.hurtTime-partial)*Math.PI/10)*.16F;body.xRot-=flinch;head.xRot+=flinch;}
    }
    @Override public void translateToHand(HumanoidArm side,PoseStack stack){
        super.translateToHand(side,stack);
        var lower=side==HumanoidArm.RIGHT?elbowR:elbowL;
        stack.translate(0,.25,0);stack.mulPose(Axis.XP.rotation(lower.xRot));stack.translate(0,-.25,0);
    }
}
