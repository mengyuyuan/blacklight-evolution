package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.GreeneForm;
import com.codex.blacklight.combat.GreeneMotion;
import com.codex.blacklight.entity.EncounterMob;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.GeoBone;

/** Draw the authored shell on the fighting entity, retaining its three independently hittable organs. */
public final class GreeneEncounterRenderer extends BlacklightCreatureRenderer<EncounterMob> {
    private final GreeneSpiderRenderer spider;
    private final GreeneGoddessRenderer goddess;
    public GreeneEncounterRenderer(EntityRendererProvider.Context context) {
        super(context,EncounterMob::asset);
        spider=new GreeneSpiderRenderer(context);
        goddess=new GreeneGoddessRenderer(context);
    }
    @Override public void renderCubesOfBone(PoseStack stack,GeoBone bone,VertexConsumer out,int light,int overlay,float red,float green,float blue,float alpha) {
        if(bone.getName().startsWith("weak_")) super.renderCubesOfBone(stack,bone,out,light,overlay,red,green,blue,alpha);
    }
    @Override public void render(EncounterMob boss,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light) {
        var form=GreeneForm.of(boss);
        var transform=form.transform(GreeneMotion.table().transforms(GreeneMotion.pose(boss,partial)),boss.skinYaw(partial));
        stack.pushPose();
        com.codex.blacklight.client.ClientPoseMatrices.multiply(stack,transform);
        if(form==GreeneForm.SPIDER) {
            var shell=boss.greeneSpiderShell();
            shell.tickCount=boss.tickCount;
            spider.render(shell,180,partial,stack,buffers,light);
        } else {
            float flash=boss.greeneFlash(3,partial);
            int overlay=net.minecraft.client.renderer.texture.OverlayTexture.pack(net.minecraft.client.renderer.texture.OverlayTexture.u(flash*.55F),net.minecraft.client.renderer.texture.OverlayTexture.v(boss.hurtTime>0));
            goddess.draw(boss.tickCount+partial,stack,buffers,light,overlay);
        }
        stack.popPose();
        super.render(boss,yaw,partial,stack,buffers,light);
    }
}
