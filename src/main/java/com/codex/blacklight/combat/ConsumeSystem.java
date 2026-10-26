package com.codex.blacklight.combat;

import com.codex.blacklight.entity.BlacklightHunter;
import com.codex.blacklight.animation.PlayerAction;
import com.codex.blacklight.animation.PlayerActions;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.phys.Vec3;
import com.codex.blacklight.power.PowerForm;
import net.minecraft.world.entity.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.*;

/** Server owns claims and rewards. Clients send intent only, never a target ID or reward. */
public final class ConsumeSystem {
    public static final int DURATION = 46;
    public static final double WHIP_GRAB_RANGE = 12;
    public static final float HEALTH_LIMIT = .15F;
    public static final float DIRECT_GRAB_MAX_HEALTH = 50F;
    private static final Map<ServerPlayer, Session> ACTIVE = new HashMap<>();
    public static final TagKey<EntityType<?>> CONSUMABLE = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("blacklight", "consumable"));
    public static final TagKey<EntityType<?>> UNCONSUMABLE = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("blacklight", "unconsumable"));
    public static final TagKey<EntityType<?>> PASSIVE = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("blacklight", "consumable_without_weakening"));
    private static final Map<Mob, Thrown> THROWN = new HashMap<>();
    private record Thrown(ServerPlayer owner, int remaining, Vec3 velocity,float damage) {}
    private static final class Session {
        final Mob victim; final PowerForm form; final float heal; final int biomass, ep; final boolean large; float facing;
        final com.codex.blacklight.power.AssimilationSystem.Yield reward;
        Vec3 start,lastPosition,consumeTargetStart; int started, stage=ConsumeVisual.CAPTURE,stuckTicks;double pullSpeed;boolean releaseLogged,consumeQueued;
        Session(Mob v, ServerPlayer p, PowerForm f) {
            victim=v; form=f; start=p.position(); started=p.tickCount; large=ConsumeSequence.large(v); facing=p.getYRot();
            reward=com.codex.blacklight.power.AssimilationSystem.quote(p,v);
            heal=reward.healing();biomass=reward.biomass();ep=reward.ep();
        }
        Mob victim(){return victim;} PowerForm form(){return form;}
        Vec3 start(){return start;} int started(){return started;}
        PowerForm animationForm(){return stage==ConsumeVisual.HUNTER_CONSUME?PowerForm.NONE:form;}
        int duration(){return stage==ConsumeVisual.HUNTER_CONSUME?HunterConsume.ACTOR_TICKS:ConsumeSequence.duration(animationForm(),large);}
        int completionTick(){return stage==ConsumeVisual.HUNTER_CONSUME?HunterConsume.COMPLETE_TICKS:ConsumeSequence.completionTick(animationForm(),large);}
        boolean committed(){return ConsumeVisual.consumeStage(stage);}
        float heal(){return heal;} int biomass(){return biomass;} int ep(){return ep;}
    }
    public static boolean holding(ServerPlayer player) {
        var session=ACTIVE.get(player);return session!=null&&ConsumeVisual.holding(session.stage);
    }
    private static void phase(ServerPlayer p,Session s,int stage) {
        phase(p,s,stage,0);
    }
    private static void phase(ServerPlayer p,Session s,int stage,int offset) {
        s.stage=stage;s.started=p.tickCount-offset;s.start=p.position();
        int duration=stage==ConsumeVisual.MOUNT?40:stage==ConsumeVisual.WHIP_PULL?100:stage==ConsumeVisual.CAPTURE?ConsumeVisual.captureDuration(s.form):stage==ConsumeVisual.THROW?14:s.duration();
        s.victim.setData(ModAttachments.CONSUME_VISUAL,new ConsumeVisual(p.getId(),p.level().getGameTime()-offset,duration,s.animationForm().ordinal(),stage,s.facing,s.start));
        s.victim.syncData(ModAttachments.CONSUME_VISUAL);
        p.setData(ModAttachments.GRAB_STAGE,stage);p.syncData(ModAttachments.GRAB_STAGE);
        ConsumeControl.releaseControls(p);ConsumeControl.releaseControls(s.victim);
        if(s.committed())p.setDeltaMovement(Vec3.ZERO);
    }
    private static void play(ServerPlayer player, PlayerAction action) {
        PlayerActions.get().play(player, action, 0);
    }
    /** Right click confirms a held target. It can never acquire or auto-consume a new target. */
    public static void consume(ServerPlayer player) {
        var s=ACTIVE.get(player);if(s==null||!valid(player,s))return;
        if(ConsumeVisual.acquiring(s.stage)){s.consumeQueued=true;return;}
        if(!ConsumeVisual.holding(s.stage))return;
        s.consumeQueued=false;
        // Recheck at confirmation: healing or changed attributes must not bypass the gate.
        if (!healthReady(player,s.victim)) { message(player,"consume_weaken"); return; }
        s.facing=player.getYRot();
        s.consumeTargetStart=s.victim.position();
        phase(player,s,s.victim instanceof BlacklightHunter?ConsumeVisual.HUNTER_CONSUME:s.large?ConsumeVisual.MOUNT_CONSUME:ConsumeVisual.CONSUME);
        play(player,s.large?PlayerAction.consumeLarge(s.animationForm()):PlayerAction.consume(s.form));
        com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Consume scene started: form={} target={} large={} clip={} duration={} origin={}",s.form,s.victim.getType(),s.large,ConsumeSequence.clipKey(s.animationForm(),s.large),s.duration(),s.start);
    }
    /** E again or primary attack starts a throw; release is synchronized to the arm swing. */
    public static void throwTarget(ServerPlayer player) {
        var s=ACTIVE.get(player);if(s==null||!ConsumeVisual.holding(s.stage)||!valid(player,s))return;
        if(s.large){cancel(player);return;}
        phase(player,s,ConsumeVisual.THROW);play(player,s.form==PowerForm.BLADE?PlayerAction.GRAB_BLADE_THROW:PlayerAction.GRAB_THROW);
    }
    private static boolean valid(ServerPlayer p,Session s) {
        var v=s.victim;var state=p.getData(ModAttachments.PLAYER_STATE);
        return p.isAlive()&&!p.isSpectator()&&!p.isPassenger()&&state.isInfected()&&state.isPrototypeMode()
                &&state.getActivePower()==s.form&&v.isAlive()&&!v.isRemoved()&&v.level()==p.level()
                &&!v.isInvulnerable()&&!v.isPassenger()&&!v.isVehicle()&&!v.isLeashed()
                &&ModAttachments.peekData(v, ModAttachments.CONSUME_VISUAL, ConsumeVisual.EMPTY).consumerId()==p.getId()
                &&(s.committed() || (s.large ? p.position().distanceToSqr(ConsumeSequence.mount(v))<=196 && hasGrabSight(p,v) : grabInReach(p,v,s.form==PowerForm.WHIPFIST && (s.stage==ConsumeVisual.CAPTURE||s.stage==ConsumeVisual.WHIP_PULL) ? WHIP_GRAB_RANGE+.5+com.codex.blacklight.power.AssimilationSystem.grabBonus(p) : s.stage==ConsumeVisual.CONSUME&&ConsumeSequence.paired(s.form,false)?10:3.5+com.codex.blacklight.power.AssimilationSystem.grabBonus(p))&&hasGrabSight(p,v)));
    }
    private static boolean hasGrabSight(ServerPlayer player,Mob target) {
        // Use the ability's explicit range, followed by a real block ray trace.
        // LivingEntity.hasLineOfSight has a shorter built-in distance cutoff.
        return player.level()==target.level() && player.level().clip(new net.minecraft.world.level.ClipContext(
                player.getEyePosition(),ConsumeSequence.large(target)?ConsumeSequence.contact(target):target.getEyePosition(),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,player)).getType()
                ==net.minecraft.world.phys.HitResult.Type.MISS;
    }
    private static double grabAim(ServerPlayer player,Mob target) {
        Vec3 point=ConsumeSequence.large(target)?ConsumeSequence.contact(target):target.getBoundingBox().getCenter();
        Vec3 delta=point.subtract(player.getEyePosition()).multiply(1,0,1);
        // Third-person movement changes body yaw independently of the view. Ignore
        // pitch for nearby short mobs; range and block sight still constrain capture.
        return delta.lengthSqr()<1e-6?1:com.codex.blacklight.movement.TraversalSteering.forward(player).dot(delta.normalize());
    }
    private static boolean grabInReach(ServerPlayer player,Mob target,double range){
        return player.level()==target.level()&&(ConsumeSequence.large(target)?player.getEyePosition().distanceToSqr(ConsumeSequence.contact(target)):
                CombatTargeting.distanceSquared(player.getEyePosition(),target.getBoundingBox()))<=range*range+1e-6;
    }
    /** Arrow knockback can briefly clear onGround without being an aerial move. */
    private static boolean canStartGroundGrab(ServerPlayer player) {
        if(player.onGround())return true;
        if(player.hurtTime<=0||player.isInWater()||player.isInLava()||player.getAbilities().flying
                ||Math.abs(player.getDeltaMovement().y)>.45)return false;
        var feet=player.getBoundingBox();
        var support=new net.minecraft.world.phys.AABB(feet.minX+.05,feet.minY-.65,feet.minZ+.05,
                feet.maxX-.05,feet.minY+.01,feet.maxZ-.05);
        return player.level().getBlockCollisions(player,support).iterator().hasNext();
    }
    private static Vec3 anchor(ServerPlayer p,Mob victim) {
        var s=ACTIVE.get(p);
        var visual=ModAttachments.peekData(victim, ModAttachments.CONSUME_VISUAL, ConsumeVisual.EMPTY);
        if(GrappleMotion.active(visual)){
            var nativeAnchor=ConsumePlacementTrack.carryAnchor(p.position(),p.getYRot(),victim,GrappleMotion.key(p,visual),GrappleMotion.progress(p,visual,0));
            if(nativeAnchor.isPresent())return nativeAnchor.get();
        }
        if(s!=null&&s.form==PowerForm.BLADE) {
            float progress=s.stage==ConsumeVisual.CONSUME?(p.tickCount-s.started)/(float)s.duration():0;
            return GrabPlacement.bladeAnchor(p.position(),p.getYRot(),p.getXRot(),victim.getBbHeight(),progress);
        }
        return GrabPlacement.anchor(p.position(),p.getYRot(),p.getXRot(),victim.getBbWidth(),victim.getBbHeight());
    }
    private static void carry(ServerPlayer p,Mob v,double speed) {
        Vec3 delta=anchor(p,v).subtract(v.position());
        v.move(MoverType.SELF,delta.length()>speed?delta.normalize().scale(speed):delta);
        v.setDeltaMovement(Vec3.ZERO);v.fallDistance=0;
        v.setYRot(p.getYRot()+(GrappleMotion.active(ModAttachments.peekData(v, ModAttachments.CONSUME_VISUAL, ConsumeVisual.EMPTY))?0:180));v.setYHeadRot(v.getYRot());v.yBodyRot=v.getYRot();
    }

    /** Use maximum health for the tier, current health only for the weakening threshold. */
    public static boolean healthReady(LivingEntity target) { return healthReady(null,target); }
    public static boolean healthReady(net.minecraft.world.entity.player.Player consumer,LivingEntity target) {
        float maximum=target.getMaxHealth();
        return target.isAlive() && Float.isFinite(maximum) && maximum>0
                && (com.codex.blacklight.power.CombatScale.originalMaximum(target)<DIRECT_GRAB_MAX_HEALTH || target.getHealth()<=maximum*(consumer==null?HEALTH_LIMIT:com.codex.blacklight.power.ConsumeProgression.healthLimit(com.codex.blacklight.power.ConsumeProgression.progress(consumer))))
                && com.codex.blacklight.power.AssimilationSystem.ready(target);
    }

    public static boolean eligible(Mob mob) {
        return com.codex.blacklight.power.BiomassSystem.claimant(mob)==null&&eligibleBiomass(mob);
    }
    public static boolean eligibleBiomass(Mob mob) {
        if(com.codex.blacklight.infection.TissueCompetition.controls(mob))return false;
        if(mob instanceof com.codex.blacklight.entity.BlacklightHydra hydra&&hydra.planted())return false;
        if(mob instanceof com.codex.blacklight.entity.HiveTendril)return false;
        return !THROWN.containsKey(mob) && mob.isAlive() && !mob.isRemoved() && !mob.isInvulnerable() && !mob.getType().is(UNCONSUMABLE)
                && !(mob instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither && wither.getInvulnerableTicks()>0)
                && !(mob instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon && dragon.getPhaseManager().getCurrentPhase().getPhase()==net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.DYING)
                && !ModAttachments.peekData(mob, ModAttachments.CONSUME_VISUAL, ConsumeVisual.EMPTY).active()
                && !(mob instanceof BlacklightHunter h && h.isBeingConsumed())
                && !mob.isPassenger() && !mob.isVehicle() && !mob.isLeashed()
                && !(mob instanceof TamableAnimal pet && pet.isTame())
                && !(mob instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse && horse.isTamed());
    }
    private ConsumeSystem() {}

    public static void request(ServerPlayer player) {
        if(com.codex.blacklight.power.BiomassSystem.active(player))return;
        if (holding(player)) { throwTarget(player); return; }
        var state = player.getData(ModAttachments.PLAYER_STATE);
        if (!player.isAlive() || player.isSpectator() || !state.isInfected() || !state.isPrototypeMode()
                || ACTIVE.containsKey(player) || player.isPassenger()) return;
        if (player.getData(ModAttachments.CLAW_COMBAT).action() != ClawCombatState.Action.IDLE
                || WeaponCombatSystem.busy(player)
                || player.getData(ModAttachments.MOVEMENT_STATE).chargeTicks() > 0
                || player.getData(ModAttachments.MOVEMENT_STATE).dashTicks() > 0
                || player.getData(ModAttachments.MOVEMENT_STATE).wallTraversal().attached()) {
            message(player,"grab_busy");return;
        }
        var form = state.getActivePower();
        double range = (form == PowerForm.WHIPFIST ? WHIP_GRAB_RANGE : 3)+com.codex.blacklight.power.AssimilationSystem.grabBonus(player);
        var locked=CombatTargeting.locked(player);
        Mob target;
        if(locked!=null) {
            target=locked instanceof Mob mob && eligible(mob) && grabInReach(player,mob,range)
                    && hasGrabSight(player,mob)?mob:null;
        } else {
            target=CombatTargeting.candidates(player,player.getBoundingBox().inflate(range),
                    h->h instanceof Mob mob&&eligible(mob)&&CombatTargeting.selectable(player,mob)&&grabInReach(player,mob,range)
                            &&hasGrabSight(player,mob)&&grabAim(player,mob)>.65).stream().map(Mob.class::cast)
                    .min(Comparator.<Mob>comparingDouble(h->-grabAim(player,h))
                            .thenComparingDouble(h->CombatTargeting.distanceSquared(player.getEyePosition(),h))
                            .thenComparingInt(Mob::getId)).orElse(null);
        }
        if (target == null) {
            if(locked==null&&BlockThrowSystem.canUse(player)){
                if(TerrainGrabSelection.start(player))return;
                message(player,TerrainGrabSelection.failureMessage(player));
            }else message(player,"consume_no_target");
            return;
        }
        if (!healthReady(player,target)) { message(player, "consume_weaken"); return; }
        if(!canStartGroundGrab(player)&&!ConsumeSequence.large(target)) {
            message(player,"grab_ground");return;
        }
        if (target instanceof BlacklightHunter hunter && !hunter.claim(player)) return;
        ImpactControl.cancel(target);
        Session session=new Session(target,player,form);ACTIVE.put(player,session);
        target.getNavigation().stop();target.setTarget(null);
        boolean heavy=form==PowerForm.WHIPFIST&&(target instanceof BlacklightHunter||target.getBbWidth()*target.getBbWidth()*target.getBbHeight()>4)&&player.distanceTo(target)>2.5;
        phase(player,session,session.large?ConsumeVisual.MOUNT:heavy?ConsumeVisual.WHIP_PULL:ConsumeVisual.CAPTURE);session.lastPosition=player.position();
        player.getData(ModAttachments.MOVEMENT_STATE).reset();
        player.setData(ModAttachments.CONSUMING, true); player.syncData(ModAttachments.CONSUMING);
        var direction = (session.large?ConsumeSequence.contact(target):target.position()).subtract(player.position());
        player.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        player.setYHeadRot(player.getYRot()); player.yBodyRot = player.getYRot();
        if(!session.large){target.setYRot(player.getYRot()+180); target.setYHeadRot(target.getYRot()); target.yBodyRot=target.getYRot();}
        play(player,form==PowerForm.BLADE?PlayerAction.GRAB_BLADE:form==PowerForm.WHIPFIST?PlayerAction.GRAB_WHIP:PlayerAction.GRAB_REACH);
        player.level().playSound(null, target.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, .9F, .65F);
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session claimed=ACTIVE.get(player);
        try(var motion=ConsumeControl.motion(player,claimed==null?null:claimed.victim)) {
            tickPlayer(player);
        }
    }
    private static void tickPlayer(ServerPlayer player) {
        var recovery=player.getData(ModAttachments.CONSUME_RECOVERY);
        if(recovery.active() && (recovery.phaseProgress(player.level().getGameTime(),0)>=1
                ||!player.isAlive()||!player.getData(ModAttachments.PLAYER_STATE).isPrototypeMode()
                ||player.getData(ModAttachments.WEAPON_VISUAL).spec()!=null||ModAttachments.peekData(player, ModAttachments.CONSUMING, false)))clearRecovery(player);
        Session session = ACTIVE.get(player); if (session == null) return;
        Mob target = session.victim();
        if (!valid(player,session) || (!session.large && session.stage!=ConsumeVisual.CONSUME && ConsumeVisual.blocksTravel(session.stage) && player.position().distanceToSqr(session.start)>1)) {
            cancel(player);return;
        }
        int elapsed=player.tickCount-session.started;
        if(ConsumeVisual.blocksTravel(session.stage))player.setDeltaMovement(0,player.getDeltaMovement().y,0);
        target.setDeltaMovement(Vec3.ZERO);
        if(session.large) {
            if(!mountTick(player,session,elapsed))return;
            finish(player,session);return;
        }
        if(session.stage==ConsumeVisual.WHIP_PULL){
            String branch=target instanceof BlacklightHunter?"hunter":"heavy";
            int limit=(int)Math.ceil(CombatMotion.whip(branch,"TimeEnd",5)*20)+14;
            if(elapsed>limit){cancel(player);return;}
            if(elapsed<14)return; // The extending arm must reach before hauling its owner.
            var delta=target.position().subtract(player.position());double distance=delta.horizontalDistance();
            float desired=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
            float yaw=CombatPursuit.turn(player.getYRot(),player.getYRot(),desired,(float)(CombatMotion.whip(branch,"FaceTargetMaxTurnRate",180)/20),180);
            player.setYRot(yaw);player.setYHeadRot(yaw);player.yBodyRot=yaw;
            if(distance<1.4&&Math.abs(delta.y)<1){
                player.setDeltaMovement(Vec3.ZERO);player.hurtMarked=true;
                phase(player,session,ConsumeVisual.CAPTURE,14);return;
            }
            if(player.horizontalCollision||player.position().distanceToSqr(session.lastPosition)<.0001)session.stuckTicks++;else session.stuckTicks=0;
            session.lastPosition=player.position();
            if(session.stuckTicks>=Math.ceil(CombatMotion.whip(branch,"MaxStuckTime",.5)*20)){cancel(player);return;}
            session.pullSpeed=Math.min(CombatMotion.whip(branch,"Velocity",40)*.87/20,
                    session.pullSpeed+CombatMotion.whip(branch,"Acceleration",10)*.87/400);
            var horizontal=CombatMotion.chase(delta,1.2,session.pullSpeed);
            // Bounded arc clears terrain; collision and LOS remain authoritative.
            double arc=Math.min(1.2,CombatMotion.whip(branch,"ParabolicConstant",.7)*Math.min(distance,4));
            player.setDeltaMovement(horizontal.x,Math.clamp((delta.y+arc)*.2,-.5,.4),horizontal.z);player.fallDistance=0;player.hurtMarked=true;
            return;
        }
        if(session.stage==ConsumeVisual.CAPTURE) {
            if(session.form==PowerForm.BLADE) {
                // Approach with the shoulder before the thrust; do not tow the victim during anticipation.
                if(elapsed<6) {
                    var approach=target.position().subtract(anchor(player,target)).multiply(1,0,1);
                    if(approach.length()>.35)player.move(MoverType.SELF,approach.normalize().scale(Math.min(.16,approach.length()-.35)));
                } else carry(player,target,.38);
            } else if(session.form!=PowerForm.WHIPFIST||elapsed>=14) {
                double speed=.24;
                if(session.form==PowerForm.WHIPFIST) {
                    // Reach, wrap, then reel in. The maximum-range target must arrive
                    // before HOLD, including vertical displacement to the hand anchor.
                    int remaining=Math.max(1,ConsumeVisual.captureDuration(session.form)-elapsed);
                    speed=Math.clamp(target.position().distanceTo(anchor(player,target))/remaining,.65,
                            CombatMotion.whip("extend","VelocityMin",45)*.87/20);
                }
                carry(player,target,speed);
            }
            if(elapsed>=ConsumeVisual.captureDuration(session.form)) {
                if(target.position().distanceToSqr(anchor(player,target))>.36){cancel(player);return;}
                phase(player,session,ConsumeVisual.HOLD);message(player,"grab_held");
                if(session.consumeQueued)consume(player);
            }
            return;
        }
        if(session.stage==ConsumeVisual.HOLD) {
            carry(player,target,3.2);
            if(target.position().distanceToSqr(anchor(player,target))>1.0){cancel(player);return;}
            return;
        }
        if(session.stage==ConsumeVisual.THROW) {
            carry(player,target,3.2);
            if(elapsed>=6) {
                ACTIVE.remove(player);release(target);unlock(player);
                boolean muscle=session.form==PowerForm.MUSCLE_MASS;
                target.setOnGround(false);target.setDeltaMovement(player.getLookAngle().scale(muscle?2.3:1.55).add(player.getDeltaMovement().multiply(.5,0,.5)).add(0,.28,0));target.hurtMarked=true;
                THROWN.put(target,new Thrown(player,40,target.getDeltaMovement(),muscle?12:6));
                target.setData(ModAttachments.CONSUME_VISUAL,new ConsumeVisual(player.getId(),player.level().getGameTime(),40,session.form.ordinal(),ConsumeVisual.FLIGHT));
                target.syncData(ModAttachments.CONSUME_VISUAL);
                player.level().playSound(null,target.blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,.8F,.7F);
            }
            return;
        }
        if(target instanceof BlacklightHunter hunter)hunter.setConsumeTicks(elapsed);
        if(ConsumeSequence.paired(session.form,false)) {
            player.setYRot(session.facing);player.setYHeadRot(session.facing);player.yBodyRot=session.facing;
            pairedPlacement(player,session,elapsed);
            consumeEffects(player,session,elapsed);
            if(elapsed>=session.completionTick())finish(player,session);
            return;
        }
        // The body follows the strike, instead of sinking before the wind-up has finished.
        int impact=ConsumeVisual.impactTick(session.form);
        int settleStart=impact-(session.form==PowerForm.HAMMERFISTS?3:1);
        if(session.form==PowerForm.BLADE)carry(player,target,.38);
        if(session.form!=PowerForm.BLADE && elapsed>=settleStart && elapsed<=impact+5) {
            Vec3 delta=player.position().subtract(target.position()).multiply(1,0,1);
            if(delta.length()>1.05)target.move(MoverType.SELF,delta.normalize().scale(Math.min(.18,delta.length()-1.05)));
            double drop=Math.min(session.form==PowerForm.HAMMERFISTS?.32:.16,Math.max(0,target.getY()-player.getY()));
            target.move(MoverType.SELF,new Vec3(0,-drop,0));
        }
        if (elapsed == impact) {
            player.level().playSound(null,target.blockPosition(),SoundEvents.PLAYER_ATTACK_STRONG,SoundSource.PLAYERS,
                    session.form==PowerForm.HAMMERFISTS?.95F:.7F,session.form==PowerForm.HAMMERFISTS?.55F:.8F);
        }
        if (session.form() == PowerForm.HAMMERFISTS && elapsed == impact) {
            var floor=target.blockPosition().below();
            player.serverLevel().sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                    net.minecraft.core.particles.ParticleTypes.BLOCK,player.level().getBlockState(floor)),
                    target.getX(),target.getY()+.08,target.getZ(),26,.45,.08,.45,.12);
            player.level().playSound(null,target.blockPosition(),SoundEvents.GENERIC_BIG_FALL,SoundSource.PLAYERS,1.15F,.6F);
        }
        // Range and sight were checked when committing; scene motion cannot cancel absorption.
        if (elapsed < session.completionTick()) return;
        finish(player,session);
    }
    private static boolean mountTick(ServerPlayer player,Session session,int elapsed) {
        Mob target=session.victim;
        Vec3 goal=ConsumeSequence.mount(target);
        if(session.committed())goal=ConsumePlacementTrack.actorAnchor(goal,session.facing,session.animationForm(),ModAttachments.peekData(target, ModAttachments.CONSUME_VISUAL, ConsumeVisual.EMPTY).motionProgress(player.level().getGameTime(),0),true);
        Vec3 delta=goal.subtract(player.position());
        if(delta.lengthSqr()>196){cancel(player);return false;}
        // Collision-aware ascent, never teleport through blocks or tow a giant to the hand.
        Vec3 before=player.position();
        double speed=session.stage==ConsumeVisual.MOUNT?.48:.65;
        player.move(MoverType.SELF,delta.length()>speed?delta.normalize().scale(speed):delta);
        player.fallDistance=0;player.setDeltaMovement(Vec3.ZERO);
        Vec3 facing=ConsumeSequence.contact(target).subtract(player.position());
        float yaw=(float)Math.toDegrees(Math.atan2(-facing.x,facing.z));
        player.setYRot(yaw);player.setYHeadRot(yaw);player.yBodyRot=yaw;
        // A server-owned attachment must also place the controlling client, not only observers.
        player.connection.teleport(player.getX(),player.getY(),player.getZ(),yaw,player.getXRot());
        if(!session.committed() && goal.distanceToSqr(player.position())>.25) {
            if(player.position().distanceToSqr(before)<.0001)session.stuckTicks++;else session.stuckTicks=0;
            if(session.stuckTicks>10||session.stage==ConsumeVisual.MOUNT&&elapsed>60)cancel(player);
            return false;
        }
        session.stuckTicks=0;
        if(session.stage==ConsumeVisual.MOUNT) {
            phase(player,session,ConsumeVisual.MOUNT_HOLD);message(player,"grab_mounted");
            if(session.consumeQueued)consume(player);
            return false;
        }
        if(session.stage==ConsumeVisual.MOUNT_HOLD)return false;
        consumeEffects(player,session,elapsed);
        return elapsed>=session.completionTick();
    }
    private static void pairedPlacement(ServerPlayer player,Session session,int elapsed) {
        Mob target=session.victim;
        float t=ConsumeSequence.motionProgress(session.form,elapsed/(float)session.duration(),session.duration(),false);
        Vec3 actorGoal=ConsumePlacementTrack.actorAnchor(session.start,session.facing,session.form,t,false);
        Vec3 before=player.position(),actorDelta=actorGoal.subtract(before);
        player.move(MoverType.SELF,actorDelta.length()>.8?actorDelta.normalize().scale(.8):actorDelta);
        player.fallDistance=0;player.setDeltaMovement(Vec3.ZERO);
        if(before.distanceToSqr(player.position())>.000001)player.connection.teleport(player.getX(),player.getY(),player.getZ(),session.facing,player.getXRot());
        Vec3 goal=ConsumePlacementTrack.anchor(session.start,session.facing,target,session.form,t).orElse(session.consumeTargetStart==null?target.position():session.consumeTargetStart);
        if(session.form==PowerForm.CLAWS&&t<ConsumePlacementTrack.gripEnd(session.form))
            goal=goal.add(player.position().subtract(actorGoal));
        Vec3 delta=goal.subtract(target.position());
        target.move(MoverType.SELF,delta.length()>1.25?delta.normalize().scale(1.25):delta);
        target.setDeltaMovement(Vec3.ZERO);target.fallDistance=0;
        target.setYRot(session.facing);target.setYHeadRot(session.facing);target.yBodyRot=session.facing;
        if(!session.releaseLogged&&t>=ConsumePlacementTrack.gripEnd(session.form)){
            session.releaseLogged=true;
            com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Consume receiver released: form={} target={} actorDisplacement={} receiverDisplacement={}",session.form,target.getType(),player.position().subtract(session.start),target.position().subtract(session.consumeTargetStart));
        }
    }
    private static void consumeEffects(ServerPlayer player,Session session,int elapsed) {
        float t=elapsed/(float)session.duration();
        PowerForm animationForm=session.animationForm();
        // Hunter camera-shake cue is at 1.2 s; sound/particles are Minecraft equivalents.
        int contact=session.stage==ConsumeVisual.HUNTER_CONSUME?24:Math.round(session.duration()*ConsumeSequence.contact(animationForm,session.large))-1;
        var receiverScene=ConsumeReceiverScene.profile(animationForm,session.large);
        Vec3 point=ConsumeSequence.contact(session.victim);
        if(receiverScene!=null?receiverScene.impactAt(elapsed):elapsed==contact) {
            boolean hammer=animationForm==PowerForm.HAMMERFISTS;
            player.level().playSound(null,session.victim.blockPosition(),hammer?SoundEvents.GENERIC_BIG_FALL:
                    animationForm==PowerForm.BLADE||animationForm==PowerForm.CLAWS?SoundEvents.PLAYER_ATTACK_SWEEP:SoundEvents.PLAYER_ATTACK_STRONG,
                    SoundSource.PLAYERS,1F,hammer?.6F:.8F);
            player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,point.x,point.y,point.z,12,.2,.2,.2,.12);
            session.victim.hurtTime=8;session.victim.hurtDuration=8;
            player.serverLevel().broadcastEntityEvent(session.victim,(byte)2);
            player.level().playSound(null,session.victim.blockPosition(),SoundEvents.PLAYER_ATTACK_KNOCKBACK,SoundSource.PLAYERS,.85F,hammer?.55F:.72F);
            if(hammer){
                var floor=session.victim.blockPosition().below();
                player.serverLevel().sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,player.level().getBlockState(floor)),
                        session.victim.getX(),session.victim.getY()+.1,session.victim.getZ(),24,.5,.1,.5,.1);
            }
        }
        if((receiverScene!=null?elapsed>=receiverScene.effectSeconds()*20:
                session.stage==ConsumeVisual.HUNTER_CONSUME?elapsed>=HunterConsume.EFFECT_REVERSE_SECONDS*20:t>.55F)&&elapsed%3==0) {
            Vec3 toward=player.position().add(0,1.1,0).subtract(point);
            for(int i=0;i<4;i++) {
                Vec3 p=point.add(toward.scale((elapsed%12+i*3)/12.0));
                player.serverLevel().sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.35F,.025F,.025F),1.25F),p.x,p.y,p.z,1,.05,.05,.05,0);
            }
        }
    }
    private static void finish(ServerPlayer player,Session session) {
        Mob target=session.victim;
        var identity=com.codex.blacklight.disguise.DisguiseIdentity.capture(target);
        com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Consume scene finished: form={} target={} large={} elapsed={}",session.form,target.getType(),session.large,player.tickCount-session.started);
        // Release before death callbacks; rewards are only paid once after successful completion.
        var recovery=ModAttachments.peekData(target, ModAttachments.CONSUME_VISUAL, ConsumeVisual.EMPTY);
        ACTIVE.remove(player);
        release(target);unlock(player);
        if(ConsumeSequence.boss(target)) {
            var source=target.damageSources().playerAttack(player);
            target.invulnerableTime=0;
            boolean accepted=target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon
                    ? dragon.hurt(dragon.head,source,Math.max(1000,dragon.getMaxHealth()*20))
                    : target.hurt(source,Math.max(1000,target.getMaxHealth()*20));
            // Zero HP (or the dragon's DYING phase) alone does not prove death was accepted.
            if(!accepted||!((com.codex.blacklight.mixin.LivingDeathAccess)target).blacklight$deathAccepted()){
                message(player,"consume_cancelled");return;
            }
        } else if(target instanceof BlacklightHunter hunter)hunter.beginConsumeRemains();
        else target.discard();
        player.setData(ModAttachments.CONSUME_RECOVERY,recovery);player.syncData(ModAttachments.CONSUME_RECOVERY);
        // Locomotion owns the base layer; the client samples the remaining source
        // pose independently, even after the receiver entity has disappeared.
        play(player,PlayerAction.CLAW_IDLE);
        if(!com.codex.blacklight.power.AssimilationSystem.settle(player,target,session.reward,true))return;
        com.codex.blacklight.disguise.DisguiseSystem.consumed(player,target);
        com.codex.blacklight.disguise.DisguiseSystem.captured(player,identity);
        player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, .8F, .55F);
        player.displayClientMessage(Component.translatable("message.blacklight.consume_complete", session.heal()*com.codex.blacklight.power.CombatScale.units(player), session.biomass(), session.ep()), true);
    }
    private static void clearRecovery(ServerPlayer player) {
        if (player.isRemoved()) return;
        if(!player.getData(ModAttachments.CONSUME_RECOVERY).active())return;
        player.setData(ModAttachments.CONSUME_RECOVERY,ConsumeVisual.EMPTY);player.syncData(ModAttachments.CONSUME_RECOVERY);
    }
    /** Super armor starts when a target is claimed, not only after consume confirmation. */
    public static boolean grappleProtected(LivingEntity entity) {
        if (!entity.isAlive() || entity.isRemoved()) return false;
        int stage=ModAttachments.peekData(entity, ModAttachments.GRAB_STAGE, ConsumeVisual.NONE);
        return ModAttachments.peekData(entity, ModAttachments.CONSUMING, false)
                && (ConsumeVisual.acquiring(stage)||ConsumeVisual.holding(stage)
                    ||ConsumeVisual.consumeStage(stage)||stage==ConsumeVisual.THROW);
    }
    public static boolean committed(LivingEntity entity) {
        if (!entity.isAlive() || entity.isRemoved()) return false;
        int stage=ModAttachments.peekData(entity, ModAttachments.GRAB_STAGE, ConsumeVisual.NONE);
        return ModAttachments.peekData(entity, ModAttachments.CONSUMING, false)&&ConsumeVisual.consumeStage(stage);
    }
    public static void cancel(ServerPlayer player) {
        clearRecovery(player);
        Session session = ACTIVE.remove(player);
        if (session == null) return;
        release(session.victim());
        unlock(player);
        if (!player.isRemoved() && player.isAlive() && player.getData(ModAttachments.PLAYER_STATE).isPrototypeMode())
            play(player, PlayerAction.CLAW_IDLE);
        message(player, "consume_cancelled");
    }
    private static void release(Mob victim) {
        if (victim.isRemoved()) return;
        if(victim instanceof BlacklightHunter hunter)hunter.release();
        victim.setData(ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY);
        if(!victim.isRemoved())victim.syncData(ModAttachments.CONSUME_VISUAL);
    }
    private static void unlock(ServerPlayer p) {
        if (p.isRemoved()) return;
        p.setData(ModAttachments.CONSUMING,false);p.syncData(ModAttachments.CONSUMING);
        p.setData(ModAttachments.GRAB_STAGE,ConsumeVisual.NONE);p.syncData(ModAttachments.GRAB_STAGE);
    }
    /** Own the brief ballistic step so navigation and pre-existing NoAI cannot swallow launch velocity. */
    @SubscribeEvent public static void thrown(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) {
        if(!(event.getEntity() instanceof Mob mob)||mob.level().isClientSide)return;
        var shot=THROWN.get(mob);if(shot==null)return;
        if(mob.isRemoved()||!mob.isAlive()||shot.remaining()<=0||shot.owner().level()!=mob.level()) {
            THROWN.remove(mob);release(mob);return;
        }
        event.setCanceled(true);
        Vec3 before=mob.position();
        mob.move(MoverType.SELF,shot.velocity());mob.setDeltaMovement(shot.velocity());mob.hurtMarked=true;mob.fallDistance=0;
        Vec3 step=mob.position().subtract(before);
        Vec3 from=before.add(0,mob.getBbHeight()*.5,0),to=mob.position().add(0,mob.getBbHeight()*.5,0);
        var hits=mob.level().getEntitiesOfClass(LivingEntity.class,mob.getBoundingBox().expandTowards(step.scale(-1)).inflate(.1),
                e->e!=shot.owner()&&e!=mob&&e.isAlive()&&!e.isSpectator()&&mob.hasLineOfSight(e)
                &&(!(e instanceof net.minecraft.world.entity.player.Player other)||shot.owner().canHarmPlayer(other)));
        var hit=hits.stream().filter(e->e.getBoundingBox().inflate(mob.getBbWidth()*.5).contains(from)
                ||e.getBoundingBox().inflate(mob.getBbWidth()*.5).clip(from,to).isPresent()).findFirst();
        if(hit.isPresent()||mob.horizontalCollision||mob.verticalCollision) {
            THROWN.remove(mob);release(mob);mob.setDeltaMovement(Vec3.ZERO);
            hit.ifPresent(e->e.hurt(mob.damageSources().thrown(mob,shot.owner()),shot.damage()));
            mob.hurt(mob.damageSources().thrown(mob,shot.owner()),shot.damage());
            mob.level().playSound(null,mob.blockPosition(),SoundEvents.GENERIC_BIG_FALL,SoundSource.PLAYERS,.8F,.8F);
        } else THROWN.put(mob,new Thrown(shot.owner(),shot.remaining()-1,shot.velocity().add(0,-.08,0).scale(.98),shot.damage()));
    }
    private static void message(ServerPlayer p, String key) { p.displayClientMessage(Component.translatable("message.blacklight." + key), true); }
    @SubscribeEvent public static void hurt(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0) return;
        // Ordinary damage cannot silently discard capture/hold. Death still ends it.
        if (event.getEntity() instanceof ServerPlayer p && !p.isAlive()) cancel(p);
        if (ModAttachments.peekData(event.getEntity(), ModAttachments.CONSUME_VISUAL, ConsumeVisual.EMPTY).active()) {
            for (var entry : List.copyOf(ACTIVE.entrySet())) if (entry.getValue().victim() == event.getEntity() && !event.getEntity().isAlive()) cancel(entry.getKey());
        }
    }
    /** Keep a confirmed scene intact. The actor still takes damage and can die. */
    @SubscribeEvent public static void protectReceiver(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if(ACTIVE.values().stream().anyMatch(s->s.committed()&&s.victim==event.getEntity()))event.setCanceled(true);
    }
    @SubscribeEvent public static void knockback(net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent event) {
        if(grappleProtected(event.getEntity())
                ||ACTIVE.values().stream().anyMatch(s->s.victim==event.getEntity()))event.setCanceled(true);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) cancel(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) cancel(p); }
    /** Freeze the captured mob without writing NoAI, gravity, or invulnerability into its saved NBT. */
    @SubscribeEvent public static void freeze(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof Mob mob)) return;
        var visual = ModAttachments.peekData(mob, ModAttachments.CONSUME_VISUAL, ConsumeVisual.EMPTY);
        if(visual.stage()==ConsumeVisual.FLIGHT) return;
        if (!visual.active()) return;
        boolean claimed = ACTIVE.values().stream().anyMatch(session -> session.victim() == mob);
        if (claimed) event.setCanceled(true);
        else {
            mob.setData(ModAttachments.CONSUME_VISUAL, ConsumeVisual.EMPTY); mob.syncData(ModAttachments.CONSUME_VISUAL);
            if (mob instanceof BlacklightHunter hunter) hunter.release();
        }
    }
    @SubscribeEvent public static void removed(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        THROWN.remove(event.getEntity());
        for (var entry : List.copyOf(ACTIVE.entrySet()))
            if (entry.getValue().victim() == event.getEntity() || entry.getKey() == event.getEntity()) cancel(entry.getKey());
    }
    @SubscribeEvent public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (var player : List.copyOf(ACTIVE.keySet())) cancel(player);
        THROWN.clear();
    }
}
