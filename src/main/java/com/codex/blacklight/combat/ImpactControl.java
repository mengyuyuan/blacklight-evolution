package com.codex.blacklight.combat;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.codex.blacklight.combat.ForgeEntityTickEvent;
import java.util.*;

/** Temporary control ends on recovery, capture, removal or server stop. Never changes NoAI. */
@EventBusSubscriber(modid="blacklight")
public final class ImpactControl {
    private record Control(boolean knockdown,long flightStarted,boolean airborne){}
    private static final Map<Mob,Control> ACTIVE=new IdentityHashMap<>();
    private ImpactControl(){}
    public static void begin(LivingEntity entity,ImpactProfile profile,float yaw){
        if (!ModAttachments.available(entity)) { if (entity instanceof Mob mob) ACTIVE.remove(mob); return; }
        if(ConsumeSystem.grappleProtected(entity)||ModAttachments.peekData(entity, ModAttachments.CONSUME_VISUAL).active())return;
        boolean hunterLight=entity instanceof com.codex.blacklight.entity.BlacklightHunter
                &&profile.kind()==HitReaction.STAGGER&&!profile.knockdown()&&entity.isAlive();
        if(hunterLight&&!((com.codex.blacklight.entity.BlacklightHunter)entity).acceptLightStagger())return;
        cancel(entity);
        if(!entity.isAlive()){
            publish(entity,new HitReaction(entity.level().getGameTime(),20,HitReaction.DEATH,yaw,ImpactProfile.FALL));return;
        }
        // Colossal bodies retain basic AI and gravity under ordinary player hits.
        // Their authored stagger/knockdown windows belong to the later boss moveset.
        if(entity instanceof com.codex.blacklight.entity.BlacklightInfectedHost host&&host.giantBoss())return;
        if(entity instanceof Mob mob){
            ACTIVE.put(mob,new Control(profile.knockdown(),entity.level().getGameTime(),!entity.onGround()));
            mob.getNavigation().stop();mob.setXxa(0);mob.setZza(0);
        }
        int duration=profile.stun();
        if(entity instanceof com.codex.blacklight.entity.BlacklightHunter){
            var source=ReceiverMotions.clip(ReceiverMotions.hitKey(entity,profile.kind(),profile.motion()));
            if(hunterLight)duration=HunterBalance.LIGHT_STAGGER_TICKS;
            else if(source!=null)duration=Math.max(duration,net.minecraft.util.Mth.clamp(Math.round(source.seconds()*20),10,70));
        }
        publish(entity,new HitReaction(entity.level().getGameTime(),duration,profile.kind(),yaw,profile.motion()));
    }
    private static void publish(LivingEntity entity,HitReaction state){com.codex.blacklight.registry.ModAttachments.setData(entity, ModAttachments.HIT_REACTION,state);com.codex.blacklight.registry.ModAttachments.syncData(entity, ModAttachments.HIT_REACTION);}
    public static void cancel(LivingEntity entity){
        if(entity instanceof Mob mob)ACTIVE.remove(mob);
        // Removal cleanup must release our reference, never read or revive invalid capabilities.
        if(!ModAttachments.available(entity))return;
        if(ModAttachments.peekData(entity, ModAttachments.HIT_REACTION).duration()>0)publish(entity,HitReaction.EMPTY);
    }
    public static boolean stunned(Mob mob){return !mob.level().isClientSide&&ACTIVE.containsKey(mob);}
    @SubscribeEvent public static void tick(ForgeEntityTickEvent.Post event){
        if(!(event.getEntity() instanceof Mob mob)||mob.level().isClientSide)return;
        if(!ModAttachments.available(mob)){ACTIVE.remove(mob);return;}
        var control=ACTIVE.get(mob);if(control==null)return;
        if(!mob.onGround()&&!control.airborne()){control=new Control(control.knockdown(),control.flightStarted(),true);ACTIVE.put(mob,control);}
        var hit=ModAttachments.peekData(mob, ModAttachments.HIT_REACTION);long now=mob.level().getGameTime(),age=now-hit.started();
        if(ModAttachments.peekData(mob, ModAttachments.CONSUME_VISUAL).active()){cancel(mob);return;}
        if(!mob.isAlive()){
            float yaw=hit.yaw();cancel(mob);publish(mob,new HitReaction(now,20,HitReaction.DEATH,yaw,ImpactProfile.FALL));return;
        }
        mob.getNavigation().stop();mob.setXxa(0);mob.setZza(0);
        if(hit.kind()==HitReaction.DOWN){
            var v=mob.getDeltaMovement();mob.setDeltaMovement(v.x*.45,v.y,v.z*.45);
            if(age>=hit.duration()){
                var rise=ReceiverMotions.clip(ReceiverMotions.hitKey(mob,HitReaction.GETUP,ImpactProfile.RISE));
                int duration=mob instanceof com.codex.blacklight.entity.BlacklightHunter&&rise!=null?Math.round(rise.seconds()*20):16;
                publish(mob,new HitReaction(now,duration,HitReaction.GETUP,hit.yaw(),ImpactProfile.RISE));
            }
        }else if(hit.kind()==HitReaction.GETUP){
            if(age>=hit.duration())cancel(mob);
        }else if(control.knockdown()&&age>=3&&(control.airborne()||age>=hit.duration())&&(mob.onGround()||mob.isInWater()||mob.isInLava())){
            publish(mob,new HitReaction(now,18,HitReaction.DOWN,hit.yaw(),ImpactProfile.DOWN));
            mob.setDeltaMovement(mob.getDeltaMovement().multiply(.3,1,.3));mob.hurtMarked=true;
            mob.level().playSound(null,mob.blockPosition(),net.minecraft.sounds.SoundEvents.GENERIC_SMALL_FALL,
                    net.minecraft.sounds.SoundSource.HOSTILE,.8F,.65F);
        }else if(age>=hit.duration()||now-control.flightStarted()>80)cancel(mob);
    }
    @SubscribeEvent public static void leave(net.minecraftforge.event.entity.EntityLeaveLevelEvent e){if(e.getEntity() instanceof Mob m&&!m.level().isClientSide)cancel(m);}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppingEvent e){for(var m:List.copyOf(ACTIVE.keySet()))cancel(m);}
}
