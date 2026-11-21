package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.GreeneForm;
import com.codex.blacklight.combat.GreeneMotion;
import com.codex.blacklight.combat.GreeneFeedingRoots;
import com.codex.blacklight.entity.EncounterMob;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.GeoBone;

/** Draw the authored shell on the fighting entity, revealing its striking limb during the matching contact animation. */
public final class GreeneEncounterRenderer extends BlacklightCreatureRenderer<EncounterMob> {
    private final GreeneSpiderRenderer spider;
    private final GreeneGoddessRenderer goddess;
    public GreeneEncounterRenderer(EntityRendererProvider.Context context) {
        super(context,EncounterMob::asset);
        spider=new GreeneSpiderRenderer(context);
        goddess=new GreeneGoddessRenderer(context,true);
    }
    @Override public void renderCubesOfBone(PoseStack stack,GeoBone bone,VertexConsumer out,int light,int overlay,float red,float green,float blue,float alpha) {
        if(!getAnimatable().greeneOutbreak() || bone.getName().startsWith("weak_") && bone.getName().charAt(5)-'0'==getAnimatable().greeneStrikingSlot()) super.renderCubesOfBone(stack,bone,out,light,overlay,red,green,blue,alpha);
    }
    @Override public void render(EncounterMob boss,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light) {
        if(!boss.greeneOutbreak()) {
            super.render(boss,yaw,partial,stack,buffers,light);
            return;
        }
        GreeneMoltRenderer.warm();goddess.warm();
        if(boss.greeneStage()==com.codex.blacklight.combat.GreeneBattleCycle.MOLTING){
            GreeneMoltRenderer.draw(boss,partial,stack,light);
            return;
        }
        var form=GreeneForm.of(boss);
        var transform=form.transform(GreeneMotion.table().transforms(GreeneMotion.pose(boss,partial)),boss.skinYaw(partial));
        stack.pushPose();
        stack.translate(0,boss.greeneHover(partial),0);
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
        if(GreeneFeedingRoots.active(boss)){
            var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
            var base=boss.getPosition(partial);
            for(int slot=0;slot<3;slot++)if(!boss.broken(slot)){
                var path=GreeneFeedingRoots.points(boss,slot,partial);
                for(int i=0;i<8;i++){
                    var a=path.get(i).subtract(base);var b=path.get(i+1).subtract(base);
                    int color=(i==6||i==3)?0xffb04742:0xff662d37;
                    ConsumedMobVisuals.tube(stack,out,light,a,b,GreeneFeedingRoots.radius(i),color);
                }
            }
        }
        stack.pushPose();stack.translate(0,boss.greeneHover(partial),0);
        super.render(boss,yaw,partial,stack,buffers,light);stack.popPose();
    }
}
