package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.model.BlacklightHunterModel;
import com.codex.blacklight.entity.BlacklightHunter;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class BlacklightHunterRenderer extends AnatomicalGeoRenderer<BlacklightHunter> {
    private static final ResourceLocation WHITE = new ResourceLocation("blacklight", "textures/entity/claw_white.png");
    private static final ResourceLocation SKIN = new ResourceLocation("blacklight", "textures/entity/hunter_connected.png");
    public BlacklightHunterRenderer(EntityRendererProvider.Context context) { super(context, new BlacklightHunterModel()); shadowRadius = .65F; }
    @Override public void render(BlacklightHunter entity, float yaw, float partial, PoseStack stack, MultiBufferSource buffer, int light) {
        var owner = entity.level().getEntity(entity.consumerId());
        var visual = com.codex.blacklight.registry.ModAttachments.getData(entity, com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL);
        float t = visual.active() ? visual.progress(entity.level().getGameTime(), partial) : 0;
        Vec3 delta = owner == null ? Vec3.ZERO : owner.getPosition(partial).subtract(entity.getPosition(partial));
        stack.pushPose();
        var placement=com.codex.blacklight.client.ClientCarryPlacement.consumeOffset(entity,partial);
        if(visual.active())stack.translate(placement.x,placement.y,placement.z);
        ConsumeFracture fragments=ConsumeFracture.visible(entity,partial)?new ConsumeFracture(entity,partial,stack,buffer,ConsumeAbsorptionFx.destination(entity,partial,placement)):null;
        if (visual.active()) com.codex.blacklight.client.animation.ConsumeVictimPose.body(entity, stack, partial);
        else if(!com.codex.blacklight.client.animation.NativeReceiverPose.available(entity,partial))com.codex.blacklight.client.animation.HitBodyPose.apply(entity,stack,partial);
        try{if(t<.995F)super.render(entity, yaw, partial, stack, fragments==null?buffer:fragments, light);if(fragments!=null)fragments.finish();}finally{stack.popPose();}
        if(owner!=null && visual.active())ConsumedMobVisuals.whip(entity,stack,buffer.getBuffer(RenderType.entityCutoutNoCull(WHITE)),light,delta,ConsumedMobVisuals.tetherProgress(entity,partial));
    }
    @Override public void renderRecursively(com.mojang.blaze3d.vertex.PoseStack pose, com.codex.blacklight.entity.BlacklightHunter entity,
            software.bernie.geckolib.cache.object.GeoBone bone, net.minecraft.client.renderer.RenderType type,
            net.minecraft.client.renderer.MultiBufferSource buffers, com.mojang.blaze3d.vertex.VertexConsumer out,
            boolean reRender,float partial,int light,int overlay,float red,float green,float blue,float alpha){
        int color=((int)(alpha*255)<<24)|((int)(red*255)<<16)|((int)(green*255)<<8)|(int)(blue*255);
        if(bone.getParent()==null&&!bone.isHidden()){
            var mesh=com.codex.blacklight.client.model.ConnectedCreatureMesh.get("hunter");
            if(mesh!=null)mesh.render(bone,pose,buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(SKIN)),light,overlay,color);
        }
        super.renderRecursively(pose,entity,bone,type,buffers,out,reRender,partial,light,overlay,red,green,blue,alpha);
    }
}
