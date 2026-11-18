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

/** Directional blood droplets and sparse mist at the accepted receiving surface.
 * At most 128 active impacts; only visible, nearby geometry is submitted. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class InfectedImpactVisuals {
    private record Impact(InfectedImpactPayload data,double at,Vec3 victimAt,HydraWoundAnchor anchor){}
    private static final ArrayDeque<Impact> ACTIVE=new ArrayDeque<>();
    private static ClientLevel current;
    private static long accepted;
    private InfectedImpactVisuals(){}
    public static long received(){return accepted;}
    @SubscribeEvent public static void unload(net.minecraftforge.event.level.LevelEvent.Unload event){if(event.getLevel()==current){ACTIVE.clear();current=null;}}
    public static void accept(InfectedImpactPayload data){
        var level=Minecraft.getInstance().level;if(level!=null)acceptAt(data,level.getGameTime());
    }
    public static void acceptAt(InfectedImpactPayload data,double started){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||!Double.isFinite(started)||!Double.isFinite(data.x())||!Double.isFinite(data.y())||!Double.isFinite(data.z()))return;
        var point=new Vec3(data.x(),data.y(),data.z());if(mc.player.position().distanceToSqr(point)>96*96)return;
        if(current!=mc.level){ACTIVE.clear();current=mc.level;}
        while(ACTIVE.size()>=128)ACTIVE.removeFirst();var victim=mc.level.getEntity(data.victim());
        var anchor=victim instanceof com.codex.blacklight.combat.AnimatedHurtBody hydra&&hydra.animatedHurtBody()?HydraWoundAnchor.capture(hydra,point,new Vec3(data.dx(),data.dy(),data.dz()),(float)Math.max(0,Math.min(1,started-mc.level.getGameTime()))):null;
        ACTIVE.addLast(new Impact(data,started,victim==null?point:victim.getPosition((float)net.minecraft.util.Mth.clamp(started-mc.level.getGameTime(),0,1)),anchor));accepted++;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.level!=current){ACTIVE.clear();current=mc.level;return;}
        double partial=event.getPartialTick(),now=mc.level.getGameTime()+partial;
        ACTIVE.removeIf(i->now-i.at>InfectedAttackStyle.resolve(i.data.style()).lifetime||now<i.at);
        if(ACTIVE.isEmpty())return;
        var type=ImpactSprayMaterial.type();var buffers=com.codex.blacklight.client.ShaderPackCompat.buffers();var out=buffers.getBuffer(type);var pose=event.getPoseStack();var camera=event.getCamera().getPosition();
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        for(var impact:ACTIVE){var p=impact.data;var origin=new Vec3(p.x(),p.y(),p.z());
            if(origin.distanceToSqr(camera)>64*64||!event.getFrustum().isVisible(new net.minecraft.world.phys.AABB(origin.add(-3,-3,-3),origin.add(3,3,3))))continue;
            float age=(float)(now-impact.at);var style=InfectedAttackStyle.resolve(p.style());
            // First contact can follow a moving body briefly, then torn pieces become free.
            var victim=mc.level.getEntity(p.victim());if(impact.anchor!=null&&victim instanceof com.codex.blacklight.combat.AnimatedHurtBody hydra&&hydra.animatedHurtBody()){impact.anchor.update(hydra,(float)partial,age);origin=impact.anchor.point();}else if(victim!=null&&age<4){var delta=victim.getPosition((float)partial).subtract(impact.victimAt);if(delta.lengthSqr()<9)origin=origin.add(delta.scale(1-age/4));}
            var axis=impact.anchor==null?new Vec3(p.dx(),p.dy(),p.dz()):impact.anchor.normal();
            int light=LevelRenderer.getLightColor(mc.level,net.minecraft.core.BlockPos.containing(origin));
            ImpactSpray.flesh(pose,out,origin,axis,camera,style,age,p.size(),p.seed(),
                    victim instanceof com.codex.blacklight.entity.BlacklightHunter,light);
        }
        pose.popPose();buffers.endBatch(type);
    }
}
