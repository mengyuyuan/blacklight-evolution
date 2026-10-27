package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.BlacklightCommonInfected;
import com.codex.blacklight.client.model.CommonInfectedModel;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

/** Independent civilian appearance; old common_infected saves use it without replacing their entities. */
public final class CommonInfectedRenderer extends HumanoidMobRenderer<BlacklightCommonInfected,CommonInfectedModel> {
    private static final ResourceLocation[] TEXTURES=java.util.stream.IntStream.range(0,4)
            .mapToObj(i->ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/infected_civilian_"+i+".png")).toArray(ResourceLocation[]::new);
    public CommonInfectedRenderer(EntityRendererProvider.Context context) {
        super(context,new CommonInfectedModel(CommonInfectedModel.layer().bakeRoot()),.45F);
    }
    @Override public ResourceLocation getTextureLocation(BlacklightCommonInfected entity) {
        return TEXTURES[entity.appearance()];
    }
    @Override public void render(BlacklightCommonInfected e,float yaw,float partial,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light){
        super.render(e,yaw,partial,pose,buffers,light);
        if(e.attackStarted()<0)return;
        pose.pushPose();pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-net.minecraft.util.Mth.rotLerp(partial,e.yBodyRotO,e.yBodyRot)));pose.scale(-1,-1,1);pose.translate(0,-1.501,0);
        model.translateToHand(e.attackHand()==0?net.minecraft.world.entity.HumanoidArm.RIGHT:net.minecraft.world.entity.HumanoidArm.LEFT,pose);
        InfectedLimbTrails.capture(e,e.attackHand(),pose.last(),new net.minecraft.world.phys.Vec3(0,.625,0));pose.popPose();
    }
}
