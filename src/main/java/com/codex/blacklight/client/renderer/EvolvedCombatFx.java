package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.EvolvedAttack;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;
import java.util.*;

/** Short ribbons use the final posed blade surface, including render interpolation and body scale. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class EvolvedCombatFx {
    private record Sample(double time,long start,String action,Vec3 inner,Vec3 tip){}
    private static final class Arm {final ArrayDeque<Sample> trail=new ArrayDeque<>();Vec3 inner,tip;double at;float charge,deploy;}
    private static final Map<BlacklightInfectedHost,Arm[]> STATES=new WeakHashMap<>();
    private EvolvedCombatFx(){}
    static void capture(BlacklightInfectedHost host,float partial,PoseStack pose,int side,Vector3f inner,Vector3f tip){
        if(host.isInvisible()||host.isMetamorphosing())return;
        var mc=Minecraft.getInstance();var camera=mc.gameRenderer.getMainCamera().getPosition();
        var a=pose.last().pose().transformPosition(new Vector3f(inner));var b=pose.last().pose().transformPosition(new Vector3f(tip));
        var state=STATES.computeIfAbsent(host,k->new Arm[]{new Arm(),new Arm()})[side];
        double now=host.level().getGameTime()+partial;String action=host.evolvedAction();float age=host.evolvedActionElapsed(partial);
        state.inner=new Vec3(a.x,a.y,a.z).add(camera);state.tip=new Vec3(b.x,b.y,b.z).add(camera);state.at=now;
        state.charge=EvolvedAttack.charge(action,age)*(action.equals("thrust_left")?(side==1?1:0):side==0?1:0);
        float deployment=host.evolvedDeployment(partial);state.deploy=deployment>0&&deployment<1?(float)Math.sin(deployment*Math.PI):0;
        var samples=state.trail;while(!samples.isEmpty()&&now-samples.getFirst().time>3.2)samples.removeFirst();
        if(!EvolvedAttack.trail(action,age,side))return;
        long start=Math.round(now-age);
        if(!samples.isEmpty()){
            var old=samples.getLast();if(now-old.time<.08)return;
            if(now<old.time||now-old.time>1.5||!old.action.equals(action)||old.start!=start||old.tip.distanceToSqr(state.tip)>16)samples.clear();
        }
        samples.add(new Sample(now,start,action,state.inner,state.tip));while(samples.size()>40)samples.removeFirst();
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null){STATES.clear();return;}
        STATES.entrySet().removeIf(e->e.getKey().isRemoved()||e.getKey().level()!=mc.level);
        if(STATES.isEmpty())return;
        double now=mc.level.getGameTime()+event.getPartialTick();
        var type=RenderType.entityTranslucentEmissive(ConsumedMobVisuals.WHITE);var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(type);
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        for(var entry:STATES.entrySet()){
            if(entry.getKey().isInvisible())continue;
            for(var arm:entry.getValue()){
                while(!arm.trail.isEmpty()&&now-arm.trail.getFirst().time>3.2)arm.trail.removeFirst();
                Sample previous=null;
                for(var sample:arm.trail){
                    if(previous!=null&&sample.tip.distanceToSqr(previous.tip)>.0004){
                        ribbon(out,pose,previous,sample,now,.18,.72,0x67171e,120);
                        ribbon(out,pose,previous,sample,now,.52,1,0xcab7af,150);
                        ribbon(out,pose,previous,sample,now,.88,1,0xffe3d0,195);
                    }previous=sample;
                }
                if(now-arm.at>1||arm.inner==null)continue;
                float amount=Math.max(arm.charge,arm.deploy);if(amount<.01)continue;
                var axis=arm.tip.subtract(arm.inner);if(axis.lengthSqr()<.001)continue;
                var right=axis.normalize().cross(new Vec3(0,1,0));if(right.lengthSqr()<.01)right=axis.normalize().cross(new Vec3(1,0,0));right=right.normalize();var up=axis.normalize().cross(right);
                Vec3 before=null;double phase=now*.35;
                for(int i=0;i<=10;i++){
                    double t=i/10D,radius=.018+.02*Math.sin(Math.PI*t);var point=arm.inner.lerp(arm.tip,.12+t*.73).add(right.scale(Math.cos(t*9+phase)*radius)).add(up.scale(Math.sin(t*9+phase)*radius));
                    if(before!=null)ConsumedMobVisuals.tube(pose,out,LightTexture.FULL_BRIGHT,before,point,.009*amount,((int)(amount*185)<<24)|0xd72d32);
                    before=point;
                }
            }
        }
        pose.popPose();buffers.endBatch(type);
    }
    private static void ribbon(VertexConsumer out,PoseStack pose,Sample a,Sample b,double now,double start,double end,int rgb,int alpha){
        int old=(int)(alpha*com.codex.blacklight.forge.WingMath.clamp(1-(now-a.time)/3.2,0,1)),next=(int)(alpha*com.codex.blacklight.forge.WingMath.clamp(1-(now-b.time)/3.2,0,1));double middle=(start+end)*.5;
        vertex(out,pose,a.inner.lerp(a.tip,start),rgb);vertex(out,pose,a.inner.lerp(a.tip,middle),old<<24|rgb);vertex(out,pose,b.inner.lerp(b.tip,middle),next<<24|rgb);vertex(out,pose,b.inner.lerp(b.tip,start),rgb);
        vertex(out,pose,a.inner.lerp(a.tip,middle),old<<24|rgb);vertex(out,pose,a.inner.lerp(a.tip,end),rgb);vertex(out,pose,b.inner.lerp(b.tip,end),rgb);vertex(out,pose,b.inner.lerp(b.tip,middle),next<<24|rgb);
    }
    private static void vertex(VertexConsumer out,PoseStack pose,Vec3 p,int color){out.vertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z).color(color).uv(.5F,.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(pose.last().normal(),0,1,0).endVertex();}
}
