package com.codex.blacklight.client.model;

import com.codex.blacklight.entity.BlackwatchSoldier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;

/** Minecraft cuboids: helmet, respirator, carrier, knee guards and a hand-mounted rifle. */
public final class BlackwatchModel extends HumanoidModel<BlackwatchSoldier> {
    public BlackwatchModel(ModelPart root){super(root);hat.visible=false;}
    public static LayerDefinition layer(boolean heavy){
        var mesh=HumanoidModel.createMesh(CubeDeformation.NONE,0);var root=mesh.getRoot();
        root.getChild("head").addOrReplaceChild("helmet",CubeListBuilder.create().texOffs(0,32).addBox(-4.4F,-8.6F,-4.4F,8.8F,5,8.8F),PartPose.ZERO);
        root.getChild("head").addOrReplaceChild("respirator",CubeListBuilder.create().texOffs(40,32).addBox(-3,-3.5F,-5,6,3,2).texOffs(40,40).addBox(-2,-5.3F,-4.6F,4,1.5F,1),PartPose.ZERO);
        root.getChild("body").addOrReplaceChild("carrier",CubeListBuilder.create().texOffs(0,48).addBox(-4.5F,1,-3,9,8,6).texOffs(32,48).addBox(-3.5F,4,-4,3,4,1).addBox(.5F,4,-4,3,4,1),PartPose.ZERO);
        root.getChild("right_arm").addOrReplaceChild("rifle",CubeListBuilder.create().texOffs(32,0).addBox(-2,6,-3,2,7,3).texOffs(48,16).addBox(-1.5F,13,-2.5F,1,6,1).texOffs(48,24).addBox(-1.8F,9,0,1.6F,3,3),PartPose.ZERO);
        if(heavy){
            for(String arm:new String[]{"right_arm","left_arm"})root.getChild(arm).addOrReplaceChild("pauldron",CubeListBuilder.create().texOffs(0,32).addBox(-3.5F,-2.5F,-3,7,5,6),PartPose.ZERO);
            for(String leg:new String[]{"right_leg","left_leg"})root.getChild(leg).addOrReplaceChild("knee",CubeListBuilder.create().texOffs(40,32).addBox(-2.4F,5,-3,4.8F,4,1.5F),PartPose.ZERO);
        }
        return LayerDefinition.create(mesh,64,64);
    }
    @Override public void setupAnim(BlackwatchSoldier e,float swing,float amount,float age,float yaw,float pitch){
        super.setupAnim(e,swing,amount,age,yaw,pitch);
        if(e.phase()==0){rightArm.xRot=-.35F;return;}
        float reload=e.phase()==3?1:0;
        rightArm.xRot=-1.35F+head.xRot*.7F+reload*.45F;rightArm.yRot=head.yRot*.6F-.12F;
        leftArm.xRot=-1.25F+head.xRot*.7F-reload*.4F;leftArm.yRot=.55F+head.yRot*.6F;
        if(e.phase()==2)rightArm.xRot+=(float)Math.sin(age*2)*.035F;
    }
}
