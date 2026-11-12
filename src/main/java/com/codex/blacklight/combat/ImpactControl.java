package com.codex.blacklight.combat;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.*;

/** Temporary control ends on recovery, capture, removal or server stop. Never changes NoAI. */
@EventBusSubscriber(modid="blacklight")
public final class ImpactControl {
    private record Control(boolean knockdown,long flightStarted,boolean airborne,long consumeUntil){
        boolean consume(long now){return now<consumeUntil;}
    }
    private static final Map<Mob,Control> ACTIVE=new IdentityHashMap<>();
    private ImpactControl(){}
    /** A confirmed low-health consume opening; uses the existing transient AI and hit-pose controller. */
    public static boolean consumeOpening(Mob mob,float yaw){
        if(mob.level().isClientSide||!mob.isAlive()||mob.isRemoved()||mob instanceof com.codex.blacklight.entity.BlacklightHive
                ||ConsumeSystem.grappleProtected(mob)||ModAttachments.peekData(mob,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY).active())return false;
        long now=mob.level().getGameTime();
        // Damage.Post can open this window inside hurt(), before WeaponImpact applies the hit.
        // Keep the opening separate from the current flinch: it is not impact immunity.
        if(stunned(mob)){
            var previous=ACTIVE.get(mob);
            ACTIVE.put(mob,new Control(previous.knockdown(),previous.flightStarted(),previous.airborne(),now+40));
            return true;
        }
        ACTIVE.put(mob,new Control(false,now,!mob.onGround(),now+40));
        mob.getNavigation().stop();mob.setXxa(0);mob.setZza(0);mob.setDeltaMovement(mob.getDeltaMovement().multiply(.15,1,.15));
        publish(mob,new HitReaction(now,40,HitReaction.STAGGER,yaw,ImpactProfile.BODY));return true;
    }
    public static boolean begin(LivingEntity entity,ImpactProfile profile,float yaw){
        if(entity.isRemoved()){if(entity instanceof Mob mob)ACTIVE.remove(mob);return false;}
        if(entity instanceof com.codex.blacklight.entity.BlacklightHive){cancel(entity);return false;}
        if(ConsumeSystem.grappleProtected(entity)||ModAttachments.peekData(entity, ModAttachments.CONSUME_VISUAL, ConsumeVisual.EMPTY).active())return false;
        long consumeUntil=0;
        if(entity instanceof Mob mob){stunned(mob);var control=ACTIVE.get(mob);if(control!=null)consumeUntil=control.consumeUntil();}
        boolean hunterLight=entity instanceof com.codex.blacklight.entity.BlacklightHunter
                &&profile.kind()==HitReaction.STAGGER&&!profile.knockdown()&&entity.isAlive();
        if(hunterLight&&!((com.codex.blacklight.entity.BlacklightHunter)entity).acceptLightStagger())return false;
        boolean evolved=entity.isAlive()&&entity instanceof com.codex.blacklight.entity.BlacklightInfectedHost host&&host.isEvolved();
        if(evolved&&!((com.codex.blacklight.entity.BlacklightInfectedHost)entity).acceptEvolvedImpact())return false;
        // Players get a short flinch with recovery immunity, so trading blows never chain-locks
        // either side and a committed attack keeps its animation.
        if(entity instanceof net.minecraft.server.level.ServerPlayer player&&player.isAlive()){
            var previous=ModAttachments.peekData(player,ModAttachments.HIT_REACTION,HitReaction.EMPTY);
            long now=player.level().getGameTime();int kind=PlayerPoise.kind(profile.kind());
            // Claw combos and grab/consume were missing here: the flinch replaced their pose for
            // every observer while the attack carried on, so fights looked like nothing played.
            boolean committed=WeaponCombatSystem.busy(player)||ConsumeSystem.active(player)
                    ||com.codex.blacklight.power.BiomassSystem.active(player)
                    ||player.getData(ModAttachments.CLAW_COMBAT).action()!=ClawCombatState.Action.IDLE;
            int ticks=PlayerPoise.duration(previous.started(),previous.duration(),now,kind,committed);
            if(ticks<=0)return false;
            int motion=profile.motion()==ImpactProfile.DOWN||profile.motion()==ImpactProfile.RISE?ImpactProfile.FALL:profile.motion();
            publish(player,new HitReaction(now,ticks,kind,yaw,motion));return true;
        }
        if(entity.isAlive()&&entity instanceof Mob mob&&!profile.knockdown()){
            var control=ACTIVE.get(mob);
            // A standing flinch must not turn a body already falling/down back into a walking mob.
            // WeaponImpact still applies this hit's impulse and hitstop to that body.
            if(control!=null&&control.knockdown()
                    &&entity.getData(ModAttachments.HIT_REACTION).kind()!=HitReaction.GETUP)return true;
        }
        cancel(entity);
        if(!entity.isAlive()){
            publish(entity,new HitReaction(entity.level().getGameTime(),20,HitReaction.DEATH,yaw,ImpactProfile.FALL));return true;
        }
        // Colossal bodies retain basic AI and gravity under ordinary player hits.
        // Their authored stagger/knockdown windows belong to the later boss moveset.
        if(entity instanceof com.codex.blacklight.entity.BlacklightInfectedHost host&&host.giantBoss())return false;
        // The Supreme Hunter runs its own poise, flinch and leg-collapse reactions.
        if(entity instanceof com.codex.blacklight.entity.BlacklightSupremeHunter)return false;
        if(entity instanceof Mob mob){
            ACTIVE.put(mob,new Control(profile.knockdown(),entity.level().getGameTime(),!entity.onGround(),consumeUntil));
            mob.getNavigation().stop();mob.setXxa(0);mob.setZza(0);
        }
        int duration=profile.stun();
        if(evolved)duration=profile.kind()==HitReaction.STAGGER?com.codex.blacklight.infection.EvolvedPoise.LIGHT_TICKS:com.codex.blacklight.infection.EvolvedPoise.HEAVY_TICKS;
        if(entity instanceof com.codex.blacklight.entity.BlacklightHunter){
            var source=ReceiverMotions.clip(ReceiverMotions.hitKey(entity,profile.kind(),profile.motion()));
            if(hunterLight)duration=HunterBalance.LIGHT_STAGGER_TICKS;
            else if(source!=null)duration=Math.max(duration,Math.clamp(Math.round(source.seconds()*20),10,70));
        }
        publish(entity,new HitReaction(entity.level().getGameTime(),duration,profile.kind(),yaw,profile.motion()));
        if(entity instanceof com.codex.blacklight.entity.MilitarySoldier soldier && stunned(soldier))
            soldier.suspendTactics();
        return true;
    }
    private static void publish(LivingEntity entity,HitReaction state){entity.setData(ModAttachments.HIT_REACTION,state);entity.syncData(ModAttachments.HIT_REACTION);}
    public static void cancel(LivingEntity entity){
        if(entity instanceof Mob mob)ACTIVE.remove(mob);
        if(entity.isRemoved())return;
        if(ModAttachments.peekData(entity, ModAttachments.HIT_REACTION, HitReaction.EMPTY).duration()>0)publish(entity,HitReaction.EMPTY);
    }
    public static boolean stunned(Mob mob){
        if(mob.level().isClientSide)return false;var control=ACTIVE.get(mob);if(control==null)return false;
        var hit=ModAttachments.peekData(mob,ModAttachments.HIT_REACTION,HitReaction.EMPTY);long now=mob.level().getGameTime();
        boolean evolvedLimit=!control.consume(now)&&mob instanceof com.codex.blacklight.entity.BlacklightInfectedHost host&&host.isEvolved()
                &&now-control.flightStarted()>=com.codex.blacklight.infection.EvolvedPoise.MAX_CONTROL_TICKS;
        if(!mob.isAlive()||mob.isRemoved()||hit.duration()<=0||now-control.flightStarted()>80||evolvedLimit
                ||!control.knockdown()&&now-hit.started()>=hit.duration()&&!control.consume(now)){
            cancel(mob);return false;
        }
        return true;
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof Mob mob)||mob.level().isClientSide)return;
        if(mob.isRemoved()){ACTIVE.remove(mob);return;}
        var control=ACTIVE.get(mob);if(control==null)return;
        if(!mob.onGround()&&!control.airborne()){control=new Control(control.knockdown(),control.flightStarted(),true,control.consumeUntil());ACTIVE.put(mob,control);}
        var hit=ModAttachments.peekData(mob, ModAttachments.HIT_REACTION, HitReaction.EMPTY);long now=mob.level().getGameTime(),age=now-hit.started();
        if(ModAttachments.peekData(mob, ModAttachments.CONSUME_VISUAL, ConsumeVisual.EMPTY).active()){cancel(mob);return;}
        if(!mob.isAlive()){
            float yaw=hit.yaw();cancel(mob);publish(mob,new HitReaction(now,20,HitReaction.DEATH,yaw,ImpactProfile.FALL));return;
        }
        if(!control.consume(now)&&mob instanceof com.codex.blacklight.entity.BlacklightInfectedHost host&&host.isEvolved()
                &&now-control.flightStarted()>=com.codex.blacklight.infection.EvolvedPoise.MAX_CONTROL_TICKS){cancel(mob);return;}
        mob.getNavigation().stop();mob.setXxa(0);mob.setZza(0);
        if(hit.kind()==HitReaction.DOWN){
            var v=mob.getDeltaMovement();mob.setDeltaMovement(v.x*.45,v.y,v.z*.45);
            if(age>=hit.duration()){
                var rise=ReceiverMotions.clip(ReceiverMotions.hitKey(mob,HitReaction.GETUP,ImpactProfile.RISE));
                int duration=mob instanceof com.codex.blacklight.entity.BlacklightHunter&&rise!=null?Math.round(rise.seconds()*20):16;
                publish(mob,new HitReaction(now,duration,HitReaction.GETUP,hit.yaw(),ImpactProfile.RISE));
            }
        }else if(hit.kind()==HitReaction.GETUP){
            if(age>=hit.duration())recover(mob,control,hit,now);
        }else if(control.knockdown()&&age>=3&&(control.airborne()||age>=hit.duration())&&(mob.onGround()||mob.isInWater()||mob.isInLava())){
            publish(mob,new HitReaction(now,HeavyBlow.recentlyThrown(mob)?32:18,HitReaction.DOWN,hit.yaw(),ImpactProfile.DOWN));
            mob.setDeltaMovement(mob.getDeltaMovement().multiply(.3,1,.3));mob.hurtMarked=true;
            mob.level().playSound(null,mob.blockPosition(),net.minecraft.sounds.SoundEvents.GENERIC_SMALL_FALL,
                    net.minecraft.sounds.SoundSource.HOSTILE,.8F,.65F);
        }else if(age>=hit.duration()||now-control.flightStarted()>80)recover(mob,control,hit,now);
    }
    private static void recover(Mob mob,Control control,HitReaction hit,long now){
        if(!control.consume(now)){cancel(mob);return;}
        // The weapon reaction keeps its own timing, then the unused opening resumes.
        // Repeated hits never extend the opening's original deadline.
        ACTIVE.put(mob,new Control(false,now,!mob.onGround(),control.consumeUntil()));
        publish(mob,new HitReaction(now,(int)(control.consumeUntil()-now),HitReaction.STAGGER,hit.yaw(),ImpactProfile.BODY));
    }
    @SubscribeEvent public static void leave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent e){if(e.getEntity() instanceof Mob m&&!m.level().isClientSide)cancel(m);}
    @SubscribeEvent public static void stop(net.neoforged.neoforge.event.server.ServerStoppingEvent e){for(var m:List.copyOf(ACTIVE.keySet()))cancel(m);}
}
