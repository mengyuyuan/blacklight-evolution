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
    private record Impact(InfectedImpactPayload data,double at,Vec3 victimAt,HydraWoundAnchor anchor,SprayEmitterPath path,boolean hunter){}
    private static final ArrayDeque<Impact> ACTIVE=new ArrayDeque<>();
    private static ClientLevel current;
    private static long accepted;
    private InfectedImpactVisuals(){}
    public static long received(){return accepted;}
    @SubscribeEvent public static void unload(net.minecraftforge.event.level.LevelEvent.Unload event){if(event.getLevel()==current){ACTIVE.clear();current=null;}}
    public static void accept(InfectedImpactPayload data){
        var level=Minecraft.getInstance().level;if(level!=null)acceptAt(data,level.getGameTime());
    }
    /** The captured mesh has actually lost tissue. Its spray starts at a fixed world contact. */
    static void rupture(net.minecraft.world.entity.LivingEntity victim,Vec3 point,Vec3 travel,double radius){
        Vec3 axis=travel.normalize();
        var receipt=new InfectedImpactPayload(0,-1,InfectedImpactPayload.appearance(InfectedAttackStyle.BURST,
                com.codex.blacklight.combat.FleshStrike.MUSCLE,true),point.x,point.y,point.z,
                (float)axis.x,(float)axis.y,(float)axis.z,(float)Math.min(1.45,.72+radius),victim.getId()*73L+victim.level().getGameTime());
        accept(receipt);
        WoundBlood.contact(receipt,point,axis,victim);
        var chips=new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                net.minecraft.world.level.block.Blocks.NETHER_WART_BLOCK.defaultBlockState());
        var rng=victim.getRandom();
        for(int i=0;i<5;i++){
            Vec3 v=axis.scale(.11+.18*rng.nextDouble()).add((rng.nextDouble()-.5)*.19,.06+rng.nextDouble()*.14,(rng.nextDouble()-.5)*.19);
            victim.level().addParticle(chips,point.x,point.y,point.z,v.x,v.y,v.z);
        }
    }
    static void sharpSever(net.minecraft.world.entity.LivingEntity victim,Vec3 point,Vec3 travel,com.codex.blacklight.combat.FleshStrike strike){
        if(travel.lengthSqr()<1.0e-8 || victim.isInvisible() || !com.codex.blacklight.combat.PlayerHitEffects.fleshBody(victim)
                || com.codex.blacklight.combat.PlayerHitEffects.metalAt(victim,point))return;
        Vec3 axis=travel.normalize();
        var receipt=new InfectedImpactPayload(0,-1,InfectedImpactPayload.appearance(InfectedAttackStyle.TEAR,strike,false),
                point.x,point.y,point.z,(float)axis.x,(float)axis.y,(float)axis.z,1.35F,
                victim.getId()*127L+victim.level().getGameTime());
        accept(receipt);
        WoundBlood.contact(receipt,point,axis,victim);
    }
    public static void acceptAt(InfectedImpactPayload data,double started){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||!Double.isFinite(started)||!Double.isFinite(data.x())||!Double.isFinite(data.y())||!Double.isFinite(data.z()))return;
        var point=new Vec3(data.x(),data.y(),data.z());if(mc.player.position().distanceToSqr(point)>96*96)return;
        if(current!=mc.level){ACTIVE.clear();current=mc.level;}
        while(ACTIVE.size()>=128)ACTIVE.removeFirst();var victim=mc.level.getEntity(data.victim());
        var anchor=victim instanceof com.codex.blacklight.combat.AnimatedHurtBody hydra&&hydra.animatedHurtBody()?HydraWoundAnchor.capture(hydra,point,new Vec3(data.dx(),data.dy(),data.dz()),(float)Math.max(0,Math.min(1,started-mc.level.getGameTime()))):null;
        if (victim instanceof net.minecraft.world.entity.LivingEntity living) {
            if (living.isInvisible()) return;
            if (!com.codex.blacklight.combat.PlayerHitEffects.fleshBody(living)) {
                var item = living instanceof net.minecraft.world.entity.animal.SnowGolem ? net.minecraft.world.item.Items.SNOWBALL
                        : living instanceof net.minecraft.world.entity.monster.AbstractSkeleton ? net.minecraft.world.item.Items.BONE : null;
                if (item != null) {
                    var chip = new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, new net.minecraft.world.item.ItemStack(item));
                    for (int i=0;i<4;i++) mc.level.addParticle(chip, point.x,point.y,point.z,
                            data.dx()*.07+(mc.level.random.nextDouble()-.5)*.12,.06+mc.level.random.nextDouble()*.08,data.dz()*.07+(mc.level.random.nextDouble()-.5)*.12);
                }
                return;
            }
            com.codex.blacklight.client.animation.FleshRecoil.accept(living, data, started);
            WoundBlood.contact(data, point, new Vec3(data.dx(),data.dy(),data.dz()), living);
        }
        var axis=new Vec3(data.dx(),data.dy(),data.dz());
        var path=new SprayEmitterPath(anchor==null?point:anchor.point(),anchor==null?axis:anchor.normal(),victim!=null);
        ACTIVE.addLast(new Impact(data,started,victim==null?point:victim.getPosition((float)net.minecraft.util.Mth.clamp(started-mc.level.getGameTime(),0,1)),anchor,path,victim instanceof com.codex.blacklight.entity.BlacklightHunter));accepted++;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.level!=current){ACTIVE.clear();current=mc.level;return;}
        double partial=event.getPartialTick(),now=mc.level.getGameTime()+partial;
        ACTIVE.removeIf(i->now-i.at>InfectedAttackStyle.resolve(i.data.style()).lifetime);
        if(ACTIVE.isEmpty())return;
        var type=ImpactSprayMaterial.type();var buffers=com.codex.blacklight.client.ShaderPackCompat.buffers();var out=buffers.getBuffer(type);var pose=event.getPoseStack();var camera=event.getCamera().getPosition();
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        for(var impact:ACTIVE){var p=impact.data;var origin=new Vec3(p.x(),p.y(),p.z());
            float age=(float)(now-impact.at);
            if(age<0||age>14)continue;
            if(!impact.path.complete()){
                var victim=mc.level.getEntity(p.victim());
                var axis=new Vec3(p.dx(),p.dy(),p.dz());
                if(impact.anchor!=null&&victim instanceof com.codex.blacklight.combat.AnimatedHurtBody body&&body.animatedHurtBody()){
                    impact.anchor.update(body,(float)partial,age);
                    origin=impact.anchor.point();
                    axis=impact.anchor.normal();
                }else if(victim!=null){
                    origin=origin.add(victim.getPosition((float)partial).subtract(impact.victimAt));
                }else{
                    var last=impact.path.last();
                    origin=last.point();
                    axis=last.axis();
                }
                impact.path.sample(age,origin,axis);
            }
            var end=impact.path.last().point();
            var bounds=new net.minecraft.world.phys.AABB(impact.path.at(0).point(),end).inflate(3);
            if(bounds.distanceToSqr(camera)>64*64||!event.getFrustum().isVisible(bounds))continue;
            int light=LevelRenderer.getLightColor(mc.level,net.minecraft.core.BlockPos.containing(end));
            ImpactSpray.flesh(pose,out,impact.path,camera,InfectedAttackStyle.resolve(p.style()),p.strike(),
                    p.charged(),p.strike()==com.codex.blacklight.combat.FleshStrike.LASH && p.region()!=0,age,p.size(),p.seed(),impact.hunter,light);
        }
        pose.popPose();buffers.endBatch(type);
    }
}
