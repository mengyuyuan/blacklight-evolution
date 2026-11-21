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
    private static void drawWounds(EncounterMob boss,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var data=boss.spiderData();int meals=data.getInt("Meals");
        if(data.contains("BlastAt")){
            pose.pushPose();pose.translate(data.getDouble("WoundX"),data.getDouble("WoundY"),data.getDouble("WoundZ"));
            QuillLesion.spider(pose,buffers,light,boss.tickCount+partial,17,4.1,.8);
            pose.popPose();
        }
        float age=boss.level().getGameTime()-data.getLong("LastTumor")+partial;
        for(int i=0;i<meals;i++){
            float fade=meals<3?1:net.minecraft.util.Mth.clamp((21+i*2-age)/8F,0,1);
            if(fade==0)continue;
            float growth=i==meals-1?net.minecraft.util.Mth.clamp((age+1)/6F,0,1):1;
            pose.pushPose();
            pose.translate(i==0?1.64:i==1?.32:1.04,i==2?3.72:4.56,i==2?-1.25:-2.21);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-80));
            QuillLesion.spider(pose,buffers,light,boss.tickCount+partial,i,2.9*growth*fade,meals==3?.8:0);
            pose.popPose();
        }
        for(int i=0;i<8;i++)if(boss.spider.broken(i)){
            String joint="tibia_"+(i<4?"L":"R")+(i%4+1);
            var pivot=com.codex.blacklight.combat.SpiderAnatomy.PIVOTS.get(joint);
            pose.pushPose();pose.mulPoseMatrix(boss.spider.jointMatrix(new org.joml.Matrix4f(),joint,partial));
            pose.translate(pivot.x,pivot.y,pivot.z);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(i<4?63:-63));
            QuillLesion.spider(pose,buffers,light,boss.tickCount+partial,i,3.8,.2);
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
        if(boss.greeneStage()==com.codex.blacklight.combat.GreeneBattleCycle.MOLTING){
            GreeneMoltRenderer.draw(boss,partial,stack,light);
            return;
        }
        var form=GreeneForm.of(boss);
        var transform=boss.spider.first()?com.codex.blacklight.combat.SpiderAnatomy.root(boss,partial):form.transform(GreeneMotion.table().transforms(GreeneMotion.pose(boss,partial)),boss.skinYaw(partial));
        stack.pushPose();
        stack.translate(0,boss.greeneHover(partial),0);
        com.codex.blacklight.client.ClientPoseMatrices.multiply(stack,transform);
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
        if(!boss.spider.first() && boss.spiderData().contains("HuskYaw")){
            var s=boss.spiderData();var origin=boss.getPosition(partial);var shell=boss.greeneSpiderShell();
            stack.pushPose();stack.translate(s.getDouble("HuskX")-origin.x,s.getDouble("HuskY")-origin.y,s.getDouble("HuskZ")-origin.z);
            stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-s.getFloat("HuskYaw")));stack.scale(1.84F,.52F,1.84F);
            shell.husk=true;
            try{
                spider.render(shell,180,partial,stack,buffers,light);
                drawWounds(boss,partial,stack,buffers,light);
            }finally{shell.husk=false;stack.popPose();}
        }
        if(boss.spider.first()){
            var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
            var origin=boss.getPosition(partial);
            for(int slot=-1;slot<3;slot++){
                var path=boss.spider.rootPath(slot,partial);
                for(int k=1;k<path.size();k++){
                    double u=(double)k/(path.size()-1);
                    var a=path.get(k-1).subtract(origin);var b=path.get(k).subtract(origin);
                    double width=slot<0?.26*(1-.82*u):.23*(1-.32*u);
                    ConsumedMobVisuals.tube(stack,out,light,a,b,width,k%4==1?0xffb35b54:0xff61343c);
                }
            }
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
