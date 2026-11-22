package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.GreeneForm;
import com.codex.blacklight.combat.GreeneMotion;
import net.minecraft.world.phys.Vec3;
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
    private final SpiderAttackEffects attackEffects=new SpiderAttackEffects();
    private final GreeneNursery nursery=new GreeneNursery();
    public GreeneEncounterRenderer(EntityRendererProvider.Context context) {
        super(context,EncounterMob::asset);
        spider=new GreeneSpiderRenderer(context);
        goddess=new GreeneGoddessRenderer(context,true);
    }
    @Override public void renderCubesOfBone(PoseStack stack,GeoBone bone,VertexConsumer out,int light,int overlay,int color) {
        if(!getAnimatable().greeneOutbreak() || bone.getName().startsWith("weak_") && bone.getName().charAt(5)-'0'==getAnimatable().greeneStrikingSlot()) super.renderCubesOfBone(stack,bone,out,light,overlay,color);
    }
    private static void drawWounds(EncounterMob boss,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var data=boss.spiderData();int meals=data.getInt("Meals");
        SpiderLegInjury.draw(boss,partial,pose,buffers,light);
        SpiderTissue.mouth(boss,pose,buffers,light,partial);
        if(boss.spider.live() && data.contains("Swallowed")){
            float t=boss.level().getGameTime()-data.getLong("Swallowed")+partial;
            if(t>=0 && t<18){
                float u=net.minecraft.util.Mth.clamp(t/17,0,1);
                // A single swallowed bolus passes from the aperture into the abdominal wall.
                var at=new Vec3(1.04,4.12,-2.62).lerp(new Vec3(.82,4.75,-.60),u);
                float pulse=(float)Math.sin(Math.PI*u);
                pose.pushPose();pose.translate(at.x,at.y,at.z);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-68));
                QuillLesion.spider(pose,buffers,light,boss.tickCount+partial,8,2.1*pulse,.68*pulse);
                pose.popPose();
            }
        }
        float blast=boss.spider.woundOpenness("BlastClosed",partial);
        if(data.contains("BlastAt") && blast>0){
            pose.pushPose();pose.translate(data.getDouble("WoundX"),data.getDouble("WoundY"),data.getDouble("WoundZ"));
            QuillLesion.spider(pose,buffers,light,boss.tickCount+partial,17,4.1*blast,.8*blast);
            pose.popPose();
        }
        float age=boss.level().getGameTime()-data.getLong("LastTumor")+partial;
        for(int i=0;i<meals;i++){
            float fade=meals<3?1:net.minecraft.util.Mth.clamp((17+i*5-age)/5F,0,1);
            if(fade==0)continue;
            float growth=i==meals-1?net.minecraft.util.Mth.clamp((age+1)/6F,0,1):1;
            pose.pushPose();
            var target=com.codex.blacklight.combat.SpiderPhase.tumorSite(i);
            var travel=i==meals-1?Math.max(0,1-Math.min(1,age/7)):0;
            var at=target.lerp(new Vec3(1.04,4.12,-2.82),travel*travel);
            pose.translate(at.x,at.y,at.z);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-80));
            QuillLesion.spider(pose,buffers,light,boss.tickCount+partial,i,2.9*growth*fade,meals==3?.8:0);
            pose.popPose();
        }
        for(int i=0;i<8;i++)if(boss.spider.broken(i)){
            float open=boss.spider.woundOpenness("Closed"+i,partial);
            if(open<=0)continue;
            String joint="tibia_"+(i<4?"L":"R")+(i%4+1);
            var pivot=com.codex.blacklight.combat.SpiderAnatomy.PIVOTS.get(joint);
            pose.pushPose();pose.mulPose(boss.spider.jointMatrix(new org.joml.Matrix4f(),joint,partial));
            pose.translate(pivot.x,pivot.y,pivot.z);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(i<4?63:-63));
            pose.scale(open,open,open);
            boolean severed=boss.spider.severed(i);
            QuillLesion.spider(pose,buffers,light,boss.tickCount+partial,i,severed?3.0:1.35,severed?.2:.08);
            if(severed)SpiderTissue.stump(boss,i,pose,buffers,light,partial);
            pose.popPose();
        }
    }
    @Override public void render(EncounterMob boss,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light) {
        if(!boss.greeneOutbreak()) {
            super.render(boss,yaw,partial,stack,buffers,light);
            return;
        }
        light=LightTexture.pack(Math.max(11,LightTexture.block(light)),LightTexture.sky(light));
        GreeneMoltRenderer.warm();goddess.warm();
        if(boss.spider.first() || boss.greeneStage()==com.codex.blacklight.combat.GreeneBattleCycle.MOLTING)
            nursery.draw(boss,partial,stack,buffers,light);
        if(boss.greeneStage()==com.codex.blacklight.combat.GreeneBattleCycle.MOLTING){
            GreeneMoltRenderer.draw(boss,partial,stack,light);
            return;
        }
        var form=GreeneForm.of(boss);
        var transform=boss.spider.first()?com.codex.blacklight.combat.SpiderAnatomy.root(boss,partial):form.transform(GreeneMotion.table().transforms(GreeneMotion.pose(boss,partial)),boss.skinYaw(partial));
        stack.pushPose();
        stack.translate(0,boss.greeneHover(partial),0);
        stack.mulPose(transform);
        if(form==GreeneForm.SPIDER) {
            var shell=boss.greeneSpiderShell();
            shell.tickCount=boss.tickCount;
            spider.render(shell,180,partial,stack,buffers,light);
            drawWounds(boss,partial,stack,buffers,light);
        } else {
            float flash=boss.greeneFlash(3,partial);
            int overlay=net.minecraft.client.renderer.texture.OverlayTexture.pack(net.minecraft.client.renderer.texture.OverlayTexture.u(flash*.55F),net.minecraft.client.renderer.texture.OverlayTexture.v(boss.hurtTime>0));
            goddess.draw(boss.tickCount+partial,stack,buffers,light,overlay);
        }
        stack.popPose();
        if(boss.spider.first()){
            var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
            var origin=boss.getPosition(partial);
            attackEffects.draw(boss,partial,stack,out,light);
            var seed=boss.spider.actions.flyingSeed(partial);
            if(seed!=null){
                var local=seed.subtract(origin);
                stack.pushPose();stack.translate(local.x,local.y-.26,local.z);
                stack.mulPose(com.mojang.math.Axis.YP.rotation((boss.spider.actions.age(partial)-17)*.37F));
                stack.scale(.64F,.72F,.64F);
                BlackTendrilMesh.shell(GreeneTendrilPodRenderer.surface(stack,out,light),12,1.4,28,0);
                stack.popPose();
            }
            GreeneMoltRenderer.drawCombatTendrils(boss,partial,stack,buffers,light);
            return;
        }
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
