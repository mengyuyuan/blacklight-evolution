package com.codex.blacklight.client.renderer;

import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;
import java.util.*;

/** Short, translucent ribbons sampled from the actual rendered weapon joints. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class WeaponTrailRenderer {
    private record Sample(double time,long action,int move,Vec3 inner,Vec3 tip){}
    private static final Map<Player,List<ArrayDeque<Sample>>> TRAILS=new WeakHashMap<>();
    public static void capture(AbstractClientPlayer p,PoseStack stack,boolean left,com.codex.blacklight.client.model.ClawArmModel model){
        if(p.getData(ModAttachments.PLAYER_STATE).getActivePower()!=PowerForm.BLADE){capture(p,stack,left);return;}
        for(int i=0;i<2;i++){
            String prefix=i==0?"trail":"reverse";
            var root=model.anchor(prefix+"Root_R",new Vector3f(0,.3F,0));
            var tip=model.anchor(prefix+"Tip_R",new Vector3f(0,1.1F,0));
            captureWorld(p,i==0?0:4,BlacklightBoneRendering.worldPoint(stack,grown(p,root,left)),BlacklightBoneRendering.worldPoint(stack,grown(p,tip,left)));
        }
    }
    public static void capture(AbstractClientPlayer p,PoseStack stack,boolean left){
        var form=p.getData(ModAttachments.PLAYER_STATE).getActivePower();
        if(form==PowerForm.WHIPFIST)return;
        if(form==PowerForm.BLADE){
            captureBlade(p,stack,0,-.09F,.32F,-.09F,.026729F,1.107F,.026729F);
            captureBlade(p,stack,4,-.09F,.12F,-.09F,-.045821F,-.414F,-.045821F);
            return;
        }
        float tip=form==PowerForm.BLADE?1.10F:form==PowerForm.CLAWS?1.05F:form==PowerForm.HAMMERFISTS?.79F:form==PowerForm.NONE?.53F:.67F;
        captureLocal(p,stack,left?1:0,form==PowerForm.HAMMERFISTS?.30F:.40F,tip);
    }
    private static Vector3f grown(AbstractClientPlayer p,Vector3f point,boolean left){
        return WeaponScaleRenderer.sample(p,net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false))[left?1:0].point(point);
    }
    private static void captureBlade(AbstractClientPlayer p,PoseStack stack,int slot,float x,float y,float z,float tx,float ty,float tz){
        var root=com.codex.blacklight.client.model.BladeShape.point(x,y,z);
        var tip=com.codex.blacklight.client.model.BladeShape.point(tx,ty,tz);
        var a=BlacklightBoneRendering.worldPoint(stack,grown(p,new Vector3f((float)root.x,(float)root.y,(float)root.z),false));
        var b=BlacklightBoneRendering.worldPoint(stack,grown(p,new Vector3f((float)tip.x,(float)tip.y,(float)tip.z),false));
        captureWorld(p,slot,a,b);
    }
    public static void captureLocal(AbstractClientPlayer p,PoseStack stack,int slot,float inner,float tip){
        var form=p.getData(ModAttachments.PLAYER_STATE).getActivePower();
        var a=BlacklightBoneRendering.worldPoint(stack,slot<2?grown(p,new Vector3f(0,inner,0),slot==1):new Vector3f(0,inner,0));
        var b=BlacklightBoneRendering.worldPoint(stack,slot<2?grown(p,new Vector3f(form==PowerForm.BLADE?-.08F:0,tip,form==PowerForm.BLADE?-.08F:.1F),slot==1):new Vector3f(form==PowerForm.BLADE?-.08F:0,tip,form==PowerForm.BLADE?-.08F:.1F));
        captureWorld(p,slot,a,b);
    }
    public static void captureWorld(Player p,boolean left,Vec3 inner,Vec3 tip){
        captureWorld(p,left?1:0,inner,tip);
    }
    private static void captureWorld(Player p,int slot,Vec3 inner,Vec3 tip){
        var mc=Minecraft.getInstance();var visual=p.getData(ModAttachments.WEAPON_VISUAL);var move=visual.spec();
        double now=p.level().getGameTime()+mc.getTimer().getGameTimeDeltaPartialTick(false);
        var lists=TRAILS.computeIfAbsent(p,k->List.of(new ArrayDeque<>(),new ArrayDeque<>(),new ArrayDeque<>(),new ArrayDeque<>(),new ArrayDeque<>()));var list=lists.get(slot);
        double attackTick=visual.attackPose()>=0?visual.attackPose()*20:now-visual.started();
        if(move==null||visual.charging()||visual.traveling()||attackTick<move.start-1||attackTick>move.end+1)return;
        if(move.form==PowerForm.BLADE&&move.contactIndex((int)Math.floor(attackTick))<0)return;
        if(move.form!=PowerForm.WHIPFIST){
            // The added reach is a visible cutting/pressure wake, rooted at the actual limb.
            tip=com.codex.blacklight.combat.AttackReach.visualTip(p,p.getPosition(mc.getTimer().getGameTimeDeltaPartialTick(false)),tip);
            var hit=p.level().clip(new net.minecraft.world.level.ClipContext(inner,tip,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            if(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK)tip=hit.getLocation();
        }
        while(!list.isEmpty()&&now-list.getFirst().time>3.6)list.removeFirst();
        if(!list.isEmpty()){
            var last=list.getLast();if(now==last.time)return;
            if(now<last.time||tip.distanceToSqr(last.tip)>16||last.action!=visual.started()||last.move!=visual.move()||now-last.time>1.5){list.clear();}
            else if(now-last.time<.08)return;
        }
        list.add(new Sample(now,visual.started(),visual.move(),inner,tip));while(list.size()>45)list.removeFirst();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null){TRAILS.clear();return;}
        double now=mc.level.getGameTime()+e.getPartialTick().getGameTimeDeltaPartialTick(false);
        var type=RenderType.entityTranslucent(ConsumedMobVisuals.WHITE);var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(type);
        var stack=e.getPoseStack();var camera=e.getCamera().getPosition();stack.pushPose();stack.translate(-camera.x,-camera.y,-camera.z);
        TRAILS.entrySet().removeIf(entry->entry.getKey().isRemoved()||entry.getKey().level()!=mc.level);
        for(var entry:TRAILS.entrySet())for(var samples:entry.getValue()){
            while(!samples.isEmpty()&&now-samples.getFirst().time>3.6)samples.removeFirst();
            Sample previous=null;for(var sample:samples){
                if(now-sample.time>3.6)continue;
                if(previous!=null&&sample.tip.distanceToSqr(previous.tip)>.0009){
                    boolean hammer=entry.getKey().getData(ModAttachments.PLAYER_STATE).getActivePower()==PowerForm.HAMMERFISTS;
                    boolean blade=entry.getKey().getData(ModAttachments.PLAYER_STATE).getActivePower()==PowerForm.BLADE;
                    if(blade){
                        ribbon(out,stack,previous,sample,now,.18,.64,3.6,130,0x4c171b);
                        ribbon(out,stack,previous,sample,now,.48,1.0,2.8,175,0xc7b5b0);
                        ribbon(out,stack,previous,sample,now,.86,1.0,1.3,200,0xffe4d4);
                        previous=sample;continue;
                    }
                    int alpha=(int)((hammer?140:95)*Math.clamp(1-(now-sample.time)/3.6,0,1));
                    int priorAlpha=(int)((hammer?140:95)*Math.clamp(1-(now-previous.time)/3.6,0,1));
                    int rgb=hammer?0x514843:0xb9aca8;
                    var oldMiddle=previous.inner.lerp(previous.tip,.55);var middle=sample.inner.lerp(sample.tip,.55);
                    // Feather both edges; a hard rectangular sheet looks like another weapon.
                    vertex(out,stack,previous.inner,rgb);vertex(out,stack,oldMiddle,priorAlpha<<24|rgb);
                    vertex(out,stack,middle,alpha<<24|rgb);vertex(out,stack,sample.inner,rgb);
                    vertex(out,stack,oldMiddle,priorAlpha<<24|rgb);vertex(out,stack,previous.tip,rgb);
                    vertex(out,stack,sample.tip,rgb);vertex(out,stack,middle,alpha<<24|rgb);
                }previous=sample;
            }
        }
        stack.popPose();buffers.endBatch(type);
    }
    private static void ribbon(com.mojang.blaze3d.vertex.VertexConsumer out,PoseStack stack,Sample old,Sample next,double now,double from,double to,double life,int opacity,int rgb){
        int a=(int)(opacity*Math.clamp(1-(now-old.time)/life,0,1)),b=(int)(opacity*Math.clamp(1-(now-next.time)/life,0,1));
        double mid=(from+to)*.5;
        vertex(out,stack,old.inner.lerp(old.tip,from),rgb);vertex(out,stack,old.inner.lerp(old.tip,mid),a<<24|rgb);
        vertex(out,stack,next.inner.lerp(next.tip,mid),b<<24|rgb);vertex(out,stack,next.inner.lerp(next.tip,from),rgb);
        vertex(out,stack,old.inner.lerp(old.tip,mid),a<<24|rgb);vertex(out,stack,old.inner.lerp(old.tip,to),rgb);
        vertex(out,stack,next.inner.lerp(next.tip,to),rgb);vertex(out,stack,next.inner.lerp(next.tip,mid),b<<24|rgb);
    }
    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer out,PoseStack stack,Vec3 point,int color){
        out.addVertex(stack.last(),(float)point.x,(float)point.y,(float)point.z).setColor(color).setUv(.5F,.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xf000f0).setNormal(stack.last(),0,1,0);
    }
}
