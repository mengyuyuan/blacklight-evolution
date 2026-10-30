package com.codex.blacklight.client.renderer;

import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.network.InfectedImpactPayload;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import java.util.*;

/** Contiguous tissue fibres, bone splinters and torn volumes, not red billboard explosions.
 * At most 128 active impacts; only visible, nearby geometry is submitted. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class InfectedImpactVisuals {
    private record Impact(InfectedImpactPayload data,double at,Vec3 victimAt){}
    private static final ArrayDeque<Impact> ACTIVE=new ArrayDeque<>();
    private static ClientLevel current;
    private static long accepted;
    private InfectedImpactVisuals(){}
    public static long received(){return accepted;}
    @SubscribeEvent public static void unload(net.minecraftforge.event.level.LevelEvent.Unload event){if(event.getLevel()==current){ACTIVE.clear();current=null;}}
    public static void accept(InfectedImpactPayload data){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||!Double.isFinite(data.x())||!Double.isFinite(data.y())||!Double.isFinite(data.z()))return;
        var point=new Vec3(data.x(),data.y(),data.z());if(mc.player.position().distanceToSqr(point)>96*96)return;
        if(current!=mc.level){ACTIVE.clear();current=mc.level;}
        while(ACTIVE.size()>=128)ACTIVE.removeFirst();var victim=mc.level.getEntity(data.victim());
        ACTIVE.addLast(new Impact(data,mc.level.getGameTime(),victim==null?point:victim.position()));accepted++;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.level!=current){ACTIVE.clear();current=mc.level;return;}
        double partial=event.getPartialTick(),now=mc.level.getGameTime()+partial;
        ACTIVE.removeIf(i->now-i.at>InfectedAttackStyle.resolve(i.data.style()).lifetime||now<i.at);
        if(ACTIVE.isEmpty())return;
        var type=RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE);var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(type);var pose=event.getPoseStack();var camera=event.getCamera().getPosition();
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        for(var impact:ACTIVE){var p=impact.data;var origin=new Vec3(p.x(),p.y(),p.z());
            if(origin.distanceToSqr(camera)>64*64||!event.getFrustum().isVisible(new net.minecraft.world.phys.AABB(origin.add(-3,-3,-3),origin.add(3,3,3))))continue;
            float age=(float)(now-impact.at);var style=InfectedAttackStyle.resolve(p.style());float life=age/style.lifetime;
            // First contact can follow a moving body briefly, then torn pieces become free.
            var victim=mc.level.getEntity(p.victim());if(victim!=null&&age<4){var delta=victim.getPosition((float)partial).subtract(impact.victimAt);if(delta.lengthSqr()<9)origin=origin.add(delta.scale(1-age/4));}
            var axis=new Vec3(p.dx(),p.dy(),p.dz()).normalize();if(axis.lengthSqr()<.01)axis=new Vec3(0,1,0);var u=axis.cross(new Vec3(0,1,0));if(u.lengthSqr()<.01)u=new Vec3(1,0,0);u=u.normalize();var v=axis.cross(u);
            draw(pose,out,origin,axis,u,v,style,age,life,p.size(),p.seed());
        }
        pose.popPose();buffers.endBatch(type);
    }
    private static void draw(PoseStack pose,VertexConsumer out,Vec3 centre,Vec3 axis,Vec3 u,Vec3 v,InfectedAttackStyle style,float age,float life,float size,long seed){
        double reveal=Math.min(1,age/3),retract=1-smooth((life-.45)/.55),scale=size*retract;
        if(scale<=.001)return;
        int fibres=switch(style){case TEAR->3;case WHIP->4;case BURST->6;case PIERCE->3;default->2;};
        for(int arm=0;arm<fibres;arm++){
            double phase=arm*Math.PI*2/fibres+unit(seed+arm)*.9;
            var radial=u.scale(Math.cos(phase)).add(v.scale(Math.sin(phase)));
            double length=switch(style){case TEAR->.45+unit(seed+arm*7)*.6;case WHIP->1.15;case BURST->1.65;case PIERCE->1;default->.45;};
            var end=centre.add(radial.scale(length*size*reveal)).add(axis.scale((style==InfectedAttackStyle.PIERCE?.8:.25)*size*reveal));
            Vec3 before=centre;
            for(int i=1;i<=9;i++){
                double t=i/9D,bend=Math.sin(Math.PI*t)*size*(style==InfectedAttackStyle.TEAR?.25:.12);
                var point=centre.lerp(end,t).add(axis.scale(bend)).add(v.scale(Math.sin(t*6+phase+age*.25)*bend*.3));
                double width=(style==InfectedAttackStyle.PIERCE?.07:.045)*scale*(1-t*.9);
                ConsumedMobVisuals.tube(pose,out,0x00c000c0,before,point,width,arm%2==0?0xff492431:0xff852f3b);before=point;
            }
        }
        if(style==InfectedAttackStyle.BURST)InfectionCombatVisuals.flesh(pose,out,0x00b000b0,centre,.32*scale*(.7+reveal),1);
        if(style==InfectedAttackStyle.PIERCE){
            var start=centre.subtract(axis.scale(.3*scale));var tip=centre.add(axis.scale(.7*scale));
            ConsumedMobVisuals.tube(pose,out,0x00d000d0,start,centre,.075*scale,0xffc5b5a3);ConsumedMobVisuals.tube(pose,out,0x00d000d0,centre,tip,.022*scale,0xffd7c7ae);
        }
        if(style==InfectedAttackStyle.CRUSH||style==InfectedAttackStyle.RESONANCE){
            double radius=(style==InfectedAttackStyle.CRUSH?1.6:1.0)*size*Math.min(1,age/6);
            InfectionCombatVisuals.ring(pose,out,centre,axis,radius,.024*retract,style==InfectedAttackStyle.CRUSH?0xff8c3d48:0xffa9bfb5,20);
        }
        // Broad tissue/bone shreds separate after tension, with independent ballistic fall.
        int pieces=style==InfectedAttackStyle.CRUSH?9:style==InfectedAttackStyle.BURST?7:4;
        for(int i=0;i<pieces;i++){
            double a=unit(seed+101+i*13)*Math.PI*2,speed=(.025+unit(seed+i*19)*.05)*size;
            var velocity=u.scale(Math.cos(a)*speed).add(v.scale(Math.sin(a)*speed)).add(axis.scale(.025*size));
            var point=centre.add(velocity.scale(age)).add(0,.018*age-.003*age*age,0);
            double w=(.035+unit(seed+i*31)*.07)*scale;InfectionCombatVisuals.box(pose,out,0x00c000c0,point,w*(i%2==0?2.4:1),w*.5,w,i%3==0?0xffbcae9b:i%2==0?0xff7e2b36:0xff37252d);
        }
    }
    private static double smooth(double t){t=com.codex.blacklight.forge.WingMath.clamp(t,0,1);return t*t*(3-2*t);}
    private static double unit(long seed){seed=(seed^(seed>>>30))*0xbf58476d1ce4e5b9L;seed=(seed^(seed>>>27))*0x94d049bb133111ebL;return ((seed^(seed>>>31))>>>11)*0x1.0p-53;}
}
