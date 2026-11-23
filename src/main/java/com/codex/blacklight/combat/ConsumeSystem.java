package com.codex.blacklight.combat;

import com.codex.blacklight.entity.BlacklightHunter;
import com.codex.blacklight.animation.PlayerAction;
import com.codex.blacklight.animation.PlayerActions;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
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
    private record Absorbing(ServerPlayer owner,Session scene) {}
    private static final Map<Mob,Absorbing> ABSORBING=new IdentityHashMap<>();
    public static final TagKey<EntityType<?>> CONSUMABLE = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("blacklight", "consumable"));
    public static final TagKey<EntityType<?>> UNCONSUMABLE = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("blacklight", "unconsumable"));
    public static final TagKey<EntityType<?>> PASSIVE = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("blacklight", "consumable_without_weakening"));
    private static final Map<Mob, Thrown> THROWN = new HashMap<>();
    @SubscribeEvent public static void settleSpiderMouths(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event){
        if(event.getLevel() instanceof ServerLevel level)SpiderPhase.settle(level);
    }
    public static boolean inFlight(Mob mob){return THROWN.containsKey(mob);}
    private record Thrown(ServerPlayer owner, int remaining, Vec3 velocity,float damage,float charge) {}
    /** Same scale as block throws so the two charge bars feel alike. */
    public static final int THROW_CHARGE_TICKS=BlockThrowTuning.MAX_CHARGE_TICKS;
    static float chargeFraction(int ticks){return Math.max(0,Math.min(1,ticks/(float)THROW_CHARGE_TICKS));}
    /** Left button down while holding a body: start or renew the wind-up. */
    public static void implantHeld(ServerPlayer player){
        var s=ACTIVE.get(player);
        if(s==null || s.large || !ConsumeVisual.carrying(s.stage) || !valid(player,s) || !SpiderPhase.edible(s.victim))return;
        if(SpiderPhase.implant(s.victim)){
            QuillHarvest.plantHeld(player,s.victim);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.blacklight.greene_implanted"),true);
        }
    }
    public static void startThrowCharge(ServerPlayer player){
        var s=ACTIVE.get(player);if (s == null || s.large || s.consumeQueued || s.throwAim != null
                || !(s.stage == ConsumeVisual.CAPTURE || ConsumeVisual.carrying(s.stage)) || !valid(player,s)) return;
        long now=player.level().getGameTime();
        if(s.chargeStart<0||now-s.chargeInput>25){
            s.chargeStart=now;
            player.setData(ModAttachments.GRAB_THROW_CHARGE,now);player.syncData(ModAttachments.GRAB_THROW_CHARGE);
            player.level().playSound(null,player.blockPosition(),SoundEvents.SLIME_SQUISH,SoundSource.PLAYERS,.7F,.6F);
        }
        s.chargeInput=now;
    }
    public static void releaseThrowCharge(ServerPlayer player){
        var s=ACTIVE.get(player);if(s==null)return;
        long now=player.level().getGameTime();
        if (s.chargeStart < 0 || now - s.chargeInput > 25 || !valid(player,s)
                || !(s.stage == ConsumeVisual.CAPTURE || ConsumeVisual.carrying(s.stage))) {
            clearThrowCharge(player,s);
            return;
        }
        s.throwCharge = (int)Math.min(THROW_CHARGE_TICKS, Math.max(0, now - s.chargeStart));
        clearThrowCharge(player,s);
        s.throwAim = selectThrowAim(player, s.victim);
        if (ConsumeVisual.carrying(s.stage)) throwTarget(player);
    }
    public static void cancelThrowCharge(ServerPlayer player){var s=ACTIVE.get(player);if(s!=null)clearThrowCharge(player,s);}
    private static void clearThrowCharge(ServerPlayer player,Session s){
        s.chargeStart=-1;s.chargeInput=-1;
        if(player.getData(ModAttachments.GRAB_THROW_CHARGE)>=0){player.setData(ModAttachments.GRAB_THROW_CHARGE,-1L);player.syncData(ModAttachments.GRAB_THROW_CHARGE);}
    }
    private static final class Session {
        final ConsumeContactDamage contactDamage = new ConsumeContactDamage();
        final Mob victim; final PowerForm form; final float heal; final int biomass, ep; final boolean large, smallDirect; float facing;
        final com.codex.blacklight.power.AssimilationSystem.Yield reward;
        Vec3 start,lastPosition,consumeTargetStart; int started, stage=ConsumeVisual.CAPTURE,stuckTicks,variant;double pullSpeed;boolean releaseLogged,consumeQueued;
        /** Held-throw charge, server game time; repeat START packets renew the lease like block throws. */
        boolean travelRequested, grabQueued, bodyWeaveStarted;
        ThrowAim throwAim;
        long chargeStart=-1,chargeInput=-1;int throwCharge; int landingDelay;
        Session(Mob v, ServerPlayer p, PowerForm f) {
            victim=v; form=f; start=p.position(); started=p.tickCount; large=ConsumeSequence.large(v); facing=ConsumeSequence.bruteMount(v)?v.yBodyRot:p.getYRot();
            smallDirect=smallDirectReceiver(v);
            reward=com.codex.blacklight.power.AssimilationSystem.quote(p,v);
            heal=reward.healing();biomass=reward.biomass();ep=reward.ep();
        }
        Mob victim(){return victim;} PowerForm form(){return form;}
        Vec3 start(){return start;} int started(){return started;}
        boolean gripSound,absorbSound;
        int returnSoundTick;
        String sound(String phase){
            int audioStage=victim instanceof BlacklightHunter ? ConsumeVisual.HUNTER_CONSUME : stage;
            return ConsumeImpactKind.forPhase(form,large,audioStage,variant,phase);
        }
        PowerForm animationForm(){return stage==ConsumeVisual.HUNTER_CONSUME?PowerForm.NONE:form;}
        int duration(){return stage==ConsumeVisual.AIR_HOLD?AerialCarryMotion.holdDuration():stage==ConsumeVisual.AIR_THROW?AerialCarryMotion.duration():stage==ConsumeVisual.GRAB_ATTACK?HeldSlamMotion.duration():stage==ConsumeVisual.HUNTER_CONSUME?HunterConsume.ACTOR_TICKS:ConsumeSequence.duration(animationForm(),large,variant);}
        int completionTick(){return stage==ConsumeVisual.HUNTER_CONSUME?HunterConsume.COMPLETE_TICKS:ConsumeSequence.completionTick(animationForm(),large,variant);}
        boolean committed(){return ConsumeVisual.consumeStage(stage);}
        float heal(){return heal;} int biomass(){return biomass;} int ep(){return ep;}
    }
    /** Travel input is sampled while the source strike still owns the body. */
    public static void travelIntent(ServerPlayer player,boolean moving){
        var s=ACTIVE.get(player);
        if(s!=null)s.travelRequested=moving;
    }
    /** Direct-grab class, evaluated before the receiver starts shrinking or moving. */
    public static boolean smallDirectReceiver(LivingEntity target) {
        return !ConsumeSequence.large(target)&&!ConsumeSequence.boss(target)
                &&!(target instanceof com.codex.blacklight.entity.BlacklightInfectedHost host
                    &&com.codex.blacklight.power.AssimilationSystem.requiresCoreWeakening(host))
                &&canGrabAtFullHealth(target);
    }

    private static boolean canGrabAtFullHealth(LivingEntity target) {
        if(target instanceof com.codex.blacklight.entity.MilitarySoldier
                &&!com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(target))return true;
        return com.codex.blacklight.power.CombatScale.originalMaximum(target)<DIRECT_GRAB_MAX_HEALTH
                &&!(target instanceof com.codex.blacklight.entity.BlacklightInfectedHost host&&host.isEvolved());
    }
    private static boolean smallDirectScene(Session s) {
        return s.smallDirect&&s.stage==ConsumeVisual.CONSUME&&!ConsumeVariant.stealth(s.form,s.variant);
    }
    private static int mobileAt(Session s) {
        if(!smallDirectScene(s)||s.landingDelay<0)return Integer.MAX_VALUE;
        var profile=ConsumeReceiverScene.profile(s.form,false,s.variant);
        double at=ConsumeReceiverScene.absorptionSeconds(s.form,s.variant);
        if(profile!=null)for(float hit:profile.hitSeconds())at=Math.max(at,hit);
        return Double.isFinite(at)&&at>0?(int)Math.ceil(at*20-1e-4):Integer.MAX_VALUE;
    }
    private static void startTail(ServerPlayer p,Session s){
        var mob=s.victim;
        var visual=mob.getData(ModAttachments.CONSUME_VISUAL).mobileTail();
        var occupied=new java.util.BitSet();
        for(var old:ABSORBING.values())if(old.owner()==p){
            var existing=old.scene().victim.getData(ModAttachments.MOBILE_CONSUME_CARRY);
            if(existing.ownerId()==p.getId())occupied.set(existing.slot());
        }
        int slot=occupied.nextClearBit(0);
        var carry=MobileConsumeCarry.start(p,mob,visual,slot);
        mob.setData(ModAttachments.MOBILE_CONSUME_CARRY,carry);mob.syncData(ModAttachments.MOBILE_CONSUME_CARRY);
        ACTIVE.remove(p);ABSORBING.put(mob,new Absorbing(p,s));
        s.stage=ConsumeVisual.MOBILE_CONSUME;
        mob.setData(ModAttachments.CONSUME_VISUAL,visual);mob.syncData(ModAttachments.CONSUME_VISUAL);
        p.setData(ModAttachments.MOBILE_CONSUME_START,p.level().getGameTime());p.syncData(ModAttachments.MOBILE_CONSUME_START);
        unlock(p);play(p,PlayerAction.CLAW_IDLE);
        if(s.grabQueued)request(p,true);
    }
    private static void tickTails(ServerPlayer p){
        if(ABSORBING.isEmpty())return;
        for(var tail:List.copyOf(ABSORBING.values())){
            if(tail.owner()!=p)continue;
            var s=tail.scene();var mob=s.victim;
            if(!ModAttachments.available(mob)){ABSORBING.remove(mob);continue;}
            var visual=mob.getData(ModAttachments.CONSUME_VISUAL);
            if(!p.isAlive()||p.isRemoved()||p.isSpectator()||!p.getData(ModAttachments.PLAYER_STATE).isPrototypeMode()
                    ||p.isPassenger()||!mob.isAlive()||mob.isRemoved()||mob.level()!=p.level()||mob.isInvulnerable()||mob.isPassenger()||mob.isVehicle()||mob.isLeashed()
                    ||visual.consumerId()!=p.getId()||visual.stage()!=ConsumeVisual.MOBILE_CONSUME){
                ABSORBING.remove(mob);
                if(visual.consumerId()==p.getId()&&visual.stage()==ConsumeVisual.MOBILE_CONSUME)release(mob);
                continue;
            }
            var carry=MobileConsumeCarry.state(mob,visual);
            if(carry!=null){
                Vec3 goal=carry.feet(p.position(),p.yBodyRot,p,mob,visual,p.level().getGameTime());
                Vec3 delta=goal.subtract(mob.position());
                try(var owned=ConsumeControl.motion(mob)){
                    mob.move(MoverType.SELF,delta.lengthSqr()>9?delta.normalize().scale(3):delta);
                    mob.setDeltaMovement(Vec3.ZERO);mob.fallDistance=0;
                }
                mob.setYRot(p.yBodyRot);mob.setYHeadRot(p.yBodyRot);mob.yBodyRot=p.yBodyRot;
            }
            triggerBodyWeave(p,s,p.tickCount-s.started-s.landingDelay);
            if(p.tickCount-s.started-s.landingDelay>=s.completionTick()&&ABSORBING.remove(mob,tail))finish(p,s);
        }
    }
    private static void cancelTails(ServerPlayer p){
        if(!p.isRemoved() && p.getData(ModAttachments.CONSUME_BODY_VISUAL).active()){
            p.setData(ModAttachments.CONSUME_BODY_VISUAL,ConsumeVisual.EMPTY);p.syncData(ModAttachments.CONSUME_BODY_VISUAL);
        }
        if (!p.isRemoved() && p.getData(ModAttachments.CONSUME_RESIDUE_VISUAL).active()) {
            p.setData(ModAttachments.CONSUME_RESIDUE_VISUAL, ConsumeVisual.EMPTY);
            p.syncData(ModAttachments.CONSUME_RESIDUE_VISUAL);
        }
        for(var tail:List.copyOf(ABSORBING.values()))if(tail.owner()==p){
            ABSORBING.remove(tail.scene().victim);release(tail.scene().victim);
        }
        if(!p.isRemoved()&&p.getData(ModAttachments.MOBILE_CONSUME_START)>=0){
            p.setData(ModAttachments.MOBILE_CONSUME_START,-1L);p.syncData(ModAttachments.MOBILE_CONSUME_START);
        }
    }
    public static boolean active(ServerPlayer player){return ACTIVE.containsKey(player);}
    static int mealStage(ServerPlayer player,Mob prey){
        var active=ACTIVE.get(player);
        if(active!=null && active.victim==prey)return active.stage;
        var mobile=ABSORBING.get(prey);
        return mobile!=null && mobile.owner()==player ? ConsumeVisual.MOBILE_CONSUME : ConsumeVisual.NONE;
    }
    public static int sessionAge(ServerPlayer player){var s=ACTIVE.get(player);return s==null?-1:player.tickCount-s.started;}
    public static boolean holding(ServerPlayer player) {
        var session=ACTIVE.get(player);return session!=null&&ConsumeVisual.holding(session.stage);
    }
    /** Existing E claim -> C attack. No acquisition, healing, biomass or assimilation here. */
    public static void attackHeld(ServerPlayer player){
        var s=ACTIVE.get(player);
        if(s==null||s.stage!=ConsumeVisual.HOLD||s.large||!valid(player,s)||!player.onGround()
                ||player.isInWaterOrBubble()||!ConsumeSequence.humanoid(s.victim)||s.victim.isBaby()
                ||s.form!=PowerForm.NONE&&s.form!=PowerForm.MUSCLE_MASS||!HeldSlamMotion.available()
                ||WeaponCombatSystem.busy(player)||com.codex.blacklight.power.BiomassSystem.active(player)
                ||com.codex.blacklight.city.HydraTransport.active(player))return;
        clearThrowCharge(player,s);clearRecovery(player);s.consumeQueued=false;
        s.variant=ConsumeVariant.DEFAULT;s.facing=player.getYRot();s.consumeTargetStart=s.victim.position();
        phase(player,s,ConsumeVisual.GRAB_ATTACK);
        PlayerActions.get().playVisual(player,"consume_leg_slam_actor",0);
    }
    private static void phase(ServerPlayer p,Session s,int stage) {
        phase(p,s,stage,0);
    }
    private static void phase(ServerPlayer p,Session s,int stage,int offset) {
        s.stage=stage;s.started=p.tickCount-offset;s.start=p.position();
        if(ConsumeSequence.bruteMount(s.victim)&&ConsumeSequence.mountedStage(stage))ConsumeSequence.mountFacing(s.victim,s.facing);
        int duration=stage==ConsumeVisual.MOUNT?40:stage==ConsumeVisual.WHIP_PULL?100:stage==ConsumeVisual.CAPTURE?ConsumeVisual.captureDuration(s.form):stage==ConsumeVisual.THROW?14:s.duration();
        s.victim.setData(ModAttachments.CONSUME_VISUAL,new ConsumeVisual(p.getId(),p.level().getGameTime()-offset,duration,s.animationForm().ordinal(),stage,s.facing,s.start,s.variant,s.landingDelay));
        s.victim.syncData(ModAttachments.CONSUME_VISUAL);
        p.setData(ModAttachments.GRAB_STAGE,stage);p.syncData(ModAttachments.GRAB_STAGE);
        ConsumeControl.releaseControls(p);ConsumeControl.releaseControls(s.victim);
        if(!s.gripSound&&(ConsumeVisual.holding(stage)||s.committed())){
            s.gripSound=true;
            ConsumeSounds.phase(p,s.sound("grip"),ConsumeSequence.contact(s.victim),.94F,1F);
        }
        if(s.committed())p.setDeltaMovement(Vec3.ZERO);
    }
    private static void play(ServerPlayer player, PlayerAction action) {
        PlayerActions.get().play(player, action, 0);
    }
    /** Right click confirms a held target. It can never acquire or auto-consume a new target. */
    public static void consume(ServerPlayer player) {
        var s=ACTIVE.get(player);if(s==null||!valid(player,s))return;
        if (ConsumeVisual.acquiring(s.stage)) {
            clearThrowCharge(player,s);
            s.throwAim = null;
            s.consumeQueued = true;
            return;
        }
        if(!ConsumeVisual.holding(s.stage))return;
        s.consumeQueued=false;
        // Recheck at confirmation: healing or changed attributes must not bypass the gate.
        if (!healthReady(player,s.victim)) { message(player,"consume_weaken"); return; }
        beginConsume(player,s,ConsumeVariant.choose(s.form,s.large,player.getRandom()));
    }
    private static void beginConsume(ServerPlayer player,Session s,int variant){
        if(s.victim instanceof com.codex.blacklight.world.GreeneHost host) {
            if(!host.beginEscape(player)) { cancel(player); return; }
            ACTIVE.remove(player);
            clearThrowCharge(player,s);
            clearRecovery(player);
            release(host);
            unlock(player);
            play(player,PlayerAction.GRAB_REACH);
            return;
        }
        if(!ConsumeSequence.bruteMount(s.victim))s.facing=player.getYRot();
        s.consumeTargetStart=s.victim.position();
        s.variant=variant;
        if(s.form==PowerForm.NONE&&(!ConsumeSequence.humanoid(s.victim)||s.victim.isBaby())
                ||!ConsumeSequence.paired(s.form,s.large,s.variant))s.variant=ConsumeVariant.DEFAULT;
        int gate=ConsumeVisual.groundGate(s.form.ordinal(),s.variant);
        s.landingDelay=!s.large && gate>=0?-1:0;
        clearThrowCharge(player,s);
        phase(player,s,s.victim instanceof BlacklightHunter?ConsumeVisual.HUNTER_CONSUME:s.large?ConsumeVisual.MOUNT_CONSUME:ConsumeVisual.CONSUME);
        s.returnSoundTick=ConsumeSequence.returnSoundTick(s.animationForm(),s.variant,s.stage,s.duration(),s.completionTick());
        if(!s.large&&ConsumeVariant.alternatives(s.form))
            PlayerActions.get().playVisual(player,"consume_"+ConsumeSequence.clipKey(s.animationForm(),false,s.variant)+"_actor",0);
        else play(player,s.large?PlayerAction.consumeLarge(s.animationForm()):PlayerAction.consume(s.form));
        com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Consume scene started: form={} target={} large={} clip={} duration={} origin={}",s.form,s.victim.getType(),s.large,ConsumeSequence.clipKey(s.animationForm(),s.large,s.variant),s.duration(),s.start);
    }
    /** Primary attack starts the throw. The arm swing owns the physical release frame. */
    public static void throwTarget(ServerPlayer player) {
        var s=ACTIVE.get(player);if(s==null||!ConsumeVisual.holding(s.stage)||!valid(player,s))return;
        if(s.large){cancel(player);return;}
        if (s.throwAim == null) s.throwAim = selectThrowAim(player,s.victim);
        boolean aerial=AerialCarryMotion.airborne(player)&&ConsumeSequence.humanoid(s.victim)&&!s.victim.isBaby()
                &&(s.form==PowerForm.NONE||s.form==PowerForm.MUSCLE_MASS)&&AerialCarryMotion.available();
        if(aerial){
            clearRecovery(player);s.facing=player.getYRot();s.variant=ConsumeVariant.DEFAULT;
            phase(player,s,ConsumeVisual.AIR_THROW);
            PlayerActions.get().playVisual(player,"consume_air_throw_actor",0);
        }else{
            phase(player,s,ConsumeVisual.THROW);play(player,s.form==PowerForm.BLADE?PlayerAction.GRAB_BLADE_THROW:PlayerAction.GRAB_THROW);
        }
    }
    private static boolean valid(ServerPlayer p,Session s) {
        if(!ModAttachments.available(p)||s==null||!ModAttachments.available(s.victim))return false;
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
    private static boolean grabCandidate(ServerPlayer p,Mob mob,double range){
        return !(mob instanceof com.codex.blacklight.entity.EncounterMob boss && boss.spider.live())
                && eligible(mob) && CombatTargeting.selectable(p,mob) && grabInReach(p,mob,range) && hasGrabSight(p,mob);
    }
    private static boolean withinGrabArc(ServerPlayer p,Mob mob){
        double alignment=grabAim(p,mob);
        if(alignment>.65)return true;
        double gap=CombatTargeting.distanceSquared(p.getEyePosition(),mob.getBoundingBox());
        return gap<=1.25*1.25 || (alignment>.17 && gap<=3*3);
    }
    private static boolean inSpiderFight(ServerPlayer p){
        return !p.serverLevel().getEntitiesOfClass(com.codex.blacklight.entity.EncounterMob.class,
                p.getBoundingBox().inflate(60),b->b.spider.live()).isEmpty();
    }
    private static boolean readyToGrab(ServerPlayer p,Mob mob){
        return healthReady(p,mob) || (mob instanceof com.codex.blacklight.entity.BlacklightInfectedHost host
                && host.isEvolved() && !host.giantBoss() && host.getHealth()<=host.getMaxHealth()*.35F && inSpiderFight(p));
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
    private record ThrowAim(Vec3 look, LivingEntity target, TargetSelection selected) {}

    /** Take one sight picture on button release, before the hand starts moving. */
    private static ThrowAim selectThrowAim(ServerPlayer player, Mob body) {
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        LivingEntity locked = CombatTargeting.locked(player);
        Vec3 end = eye.add(look.scale(48));
        if (locked != null && locked != body && CombatTargeting.selectable(player,locked)
                && CombatTargeting.visibleWithinRange(player,locked,48)){
            var part=CombatTargeting.selectionFor(player,locked);
            if(locked instanceof com.codex.blacklight.entity.EncounterMob boss && boss.spider.feeding()
                    && part!=null && part.region()==SpiderAnatomy.RIM && SpiderPhase.evolved(body)
                    && body.getPersistentData().getBoolean(SpiderPhase.TUMOR)){
                var sight=TargetSelection.aimedBoss(player,List.of(boss),eye,end,1,48);
                if(sight!=null && sight.region()==SpiderAnatomy.MOUTH)part=sight;
            }
            return new ThrowAim(look,locked,part);
        }

        LivingEntity aimed = null;
        double best = Double.POSITIVE_INFINITY;
        for (LivingEntity candidate : CombatTargeting.candidates(player, player.getBoundingBox().inflate(48),
                e -> e != body && CombatTargeting.selectable(player,e) && CombatTargeting.visibleWithinRange(player,e,48))) {
            // Direct intersections sort in front-to-back order. Only assist close to the reticle.
            double score = Double.POSITIVE_INFINITY;
            for (var box : CombatTargeting.hitboxes(candidate)) {
                var hit = box.clip(eye,end);
                if (box.contains(eye)) score = 0;
                else if (hit.isPresent() && CombatTargeting.clearRay(player,eye,hit.get()))
                    score = Math.min(score, eye.distanceToSqr(hit.get()));
            }
            if (!Double.isFinite(score)) {
                Vec3 delta = CombatTargeting.lockPoint(player,candidate).subtract(eye);
                double alignment = delta.normalize().dot(look);
                if (alignment < .992) continue;
                score = 48*48 + 1 + (1-alignment)*100000 + delta.length()/48;
            }
            if (score < best || score == best && aimed != null && candidate.getId() < aimed.getId()) {
                best = score;
                aimed = candidate;
            }
        }
        if(aimed instanceof com.codex.blacklight.entity.EncounterMob boss && boss.spider.live()){
            var part=TargetSelection.aimedBoss(player,List.of(boss),eye,end,1,48);
            // An aimed offer must go to the sighted skin, including when the player has no lock.
            return new ThrowAim(look,part==null?null:boss,part);
        }
        return new ThrowAim(look,aimed,null);
    }

    /** The body starts at the moving hand, with gravity and drag applied once per game tick. */
    private static Vec3 aimThrow(ServerPlayer p,Mob body,ThrowAim intent,double speed,float charge){
        Vec3 from=body.position().add(0,body.getBbHeight()*.5,0),look=intent.look();
        Vec3 carried=p.getDeltaMovement().multiply(.5,0,.5);
        Vec3 free=look.scale(speed).add(carried).add(0,(.28-.1*charge)*(1-Math.abs(look.y)),0);
        LivingEntity target=intent.target();
        if(target!=null && (!CombatTargeting.selectable(p,target)||!CombatTargeting.visibleWithinRange(p,target,48)))target=null;
        if(target==null)return free;
        Vec3 aim=intent.selected()!=null && intent.selected().selectable(p)?intent.selected().point(p):CombatTargeting.lockPoint(p,target);
        if(target instanceof com.codex.blacklight.entity.EncounterMob boss && boss.spider.live()
                && intent.selected()!=null && intent.selected().regional()){
            if(intent.selected().region()==SpiderAnatomy.MOUTH)aim=boss.spider.mouth(1);
            // Reach the selected surface of the giant with a low arc while still sweeping all intervening obstacles.
            speed=Math.max(speed,Math.min(3.0,from.distanceTo(aim)/9.0));
        }
        Vec3 motion=target.onGround()?target.getDeltaMovement().multiply(1,0,1):Vec3.ZERO;
        double sum=0,drop=0,pow=1,fall=0;
        for(int n=1;n<=40;n++){
            sum+=pow;drop+=fall;pow*=.98;fall=(fall+.08)*.98;
            Vec3 need=aim.add(motion.scale(n)).subtract(from).add(0,drop,0).scale(1/sum);
            if(need.length()<=speed)return need;
        }
        // Out of reach: throw along the camera, never straight into the floor at the player's feet.
        return free;
    }
    private static Vec3 anchor(ServerPlayer p,Mob victim) {
        var s=ACTIVE.get(p);
        var visual=ModAttachments.peekData(victim, ModAttachments.CONSUME_VISUAL, ConsumeVisual.EMPTY);
        if(GrappleMotion.active(visual)){
            var nativeAnchor=GrappleMotion.carryAnchor(p,victim,visual,p.position(),p.getYRot(),0);
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
        v.setYRot(p.getYRot()+GrappleMotion.receiverTurn(v,ModAttachments.peekData(v, ModAttachments.CONSUME_VISUAL, ConsumeVisual.EMPTY)));v.setYHeadRot(v.getYRot());v.yBodyRot=v.getYRot();
    }

    /** Ordinary human soldiers retain their grab class regardless of armour/health tuning. */
    public static boolean healthReady(LivingEntity target) { return healthReady(null,target); }
    public static boolean healthReady(net.minecraft.world.entity.player.Player consumer,LivingEntity target) {
        // The claimant keeps the window it opened; claim() itself marks the boss as being consumed.
        if(target instanceof BlacklightHunter infected&&infected.parasite())return infected.parasiteLocked()||consumer!=null&&infected.isAlive()&&infected.consumerId()==consumer.getId();
        if(target instanceof com.codex.blacklight.entity.BlacklightSupremeHunter supreme)return supreme.consumable()||consumer!=null&&supreme.isAlive()&&supreme.consumerId()==consumer.getId();
        if(target instanceof com.codex.blacklight.entity.EncounterMob boss&&boss.kind()==com.codex.blacklight.entity.EncounterKind.GOLIATH)return boss.goliathFinisherReady();
        float maximum=target.getMaxHealth();
        return target.isAlive() && Float.isFinite(maximum) && maximum>0
                && (canGrabAtFullHealth(target)
                    || target.getHealth()<=maximum*(consumer==null?HEALTH_LIMIT:com.codex.blacklight.power.ConsumeProgression.healthLimit(com.codex.blacklight.power.ConsumeProgression.progress(consumer))))
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

    public static void request(ServerPlayer player) {request(player,false);}
    private static void request(ServerPlayer player,boolean queuedGrab) {
        if(com.codex.blacklight.power.BiomassSystem.active(player))return;
        var acquiring=ACTIVE.get(player);
        if(acquiring!=null&&acquiring.stage==ConsumeVisual.CONSUME){
            int elapsed=player.tickCount-acquiring.started-Math.max(0,acquiring.landingDelay);
            if(elapsed+4>=mobileAt(acquiring)){acquiring.grabQueued=true;acquiring.travelRequested=true;}
            return;
        }
        if(acquiring!=null&&ConsumeVisual.acquiring(acquiring.stage)){cancel(player);return;}
        if (acquiring != null && ConsumeVisual.holding(acquiring.stage)) {
            if (acquiring.stage == ConsumeVisual.MOUNT_HOLD) cancel(player);
            return;
        }
        var state = player.getData(ModAttachments.PLAYER_STATE);
        if (!player.isAlive() || player.isSpectator() || !state.isInfected() || !state.isPrototypeMode()
                || ACTIVE.containsKey(player) || player.isPassenger()) return;
        if (state.getActivePower() == PowerForm.BACKSPINES) {
            QuillHarvest.recall(player);
            return;
        }
        if (player.getData(ModAttachments.CLAW_COMBAT).action() != ClawCombatState.Action.IDLE
                || WeaponCombatSystem.busy(player)
                || player.getData(ModAttachments.MOVEMENT_STATE).chargeTicks() > 0
                || player.getData(ModAttachments.MOVEMENT_STATE).dashTicks() > 0
                || player.getData(ModAttachments.MOVEMENT_STATE).wallTraversal().attached()) {
            message(player,"grab_busy");return;
        }
        var form = state.getActivePower();
        double range = (form == PowerForm.WHIPFIST ? WHIP_GRAB_RANGE : 3)+com.codex.blacklight.power.AssimilationSystem.grabBonus(player);
        // A lock supplies combat aim; it must not steal an explicit terrain grab.
        // A real creature intersecting the view ray before that block keeps grab priority.
        if(!queuedGrab&&BlockThrowSystem.canUse(player)&&TerrainGrabSelection.targetsTerrain(player)){
            if(!TerrainGrabSelection.start(player))message(player,TerrainGrabSelection.failureMessage(player));
            return;
        }
        var locked=CombatTargeting.locked(player);
        var previous=ABSORBING.get(locked);
        if(previous!=null&&previous.owner()==player)locked=null;
        // A boss part can remain the throw destination while the hand picks up nearby cargo.
        Mob target = locked instanceof Mob mob && grabCandidate(player,mob,range) ? mob : null;
        if(target==null){
            var nearby=CombatTargeting.candidates(player,player.getBoundingBox().inflate(range),
                    e->e instanceof Mob mob && grabCandidate(player,mob,range) && withinGrabArc(player,mob))
                    .stream().map(Mob.class::cast)
                    .sorted(Comparator.<Mob>comparingDouble(m->-grabAim(player,m))
                            .thenComparingDouble(m->CombatTargeting.distanceSquared(player.getEyePosition(),m))
                            .thenComparingInt(Mob::getId)).toList();
            for(var candidate:nearby){
                if(target==null)target=candidate; // Still explain the health threshold if none can be held.
                if(readyToGrab(player,candidate)){target=candidate;break;}
            }
        }
        if (target == null) {
            if(queuedGrab)return;
            if(BlockThrowSystem.canUse(player)){
                if(TerrainGrabSelection.start(player))return;
                message(player,TerrainGrabSelection.failureMessage(player));
            }else message(player,"consume_no_target");
            return;
        }
        if (!readyToGrab(player,target)) {
            message(player,SpiderPhase.evolved(target) && inSpiderFight(player) ? "greene_grab_weaken" : "consume_weaken");
            return;
        }
        if(!canStartGroundGrab(player)&&!ConsumeSequence.large(target)) {
            message(player,"grab_ground");return;
        }
        if (target instanceof BlacklightHunter hunter && !hunter.claim(player)) return;
        if (target instanceof com.codex.blacklight.entity.BlacklightSupremeHunter supreme && !supreme.claim(player)) return;
        // Check awareness before clearing the target/attack state for the claim.
        boolean stealth=!(target instanceof com.codex.blacklight.world.GreeneHost) && StealthConsume.canStart(player,target,form);
        ImpactControl.cancel(target);
        if(target instanceof com.codex.blacklight.entity.BlacklightCommonInfected civilian)civilian.cancelAttack();
        if(target instanceof com.codex.blacklight.entity.BlacklightMutant mutant)mutant.cancelAttack();
        if(target instanceof com.codex.blacklight.entity.EncounterMob encounter)encounter.cancelAttack();
        Session session=new Session(target,player,form);ACTIVE.put(player,session);
        var travel=player.getData(ModAttachments.MOVEMENT_STATE).wallInput();
        session.travelRequested=travel.forward()!=0||travel.sideways()!=0;
        target.getNavigation().stop();target.setTarget(null);
        var selected=CombatTargeting.selectionFor(player,target);
        boolean pull=form==PowerForm.WHIPFIST&&player.distanceTo(target)>2.5
                &&(target instanceof BlacklightHunter||target.getBbWidth()*target.getBbWidth()*target.getBbHeight()>4)
                &&!ConsumeSequence.boss(target)&&(selected==null||!selected.regional());
        phase(player,session,pull?ConsumeVisual.WHIP_PULL:session.large?ConsumeVisual.MOUNT:ConsumeVisual.CAPTURE);session.lastPosition=player.position();
        player.getData(ModAttachments.MOVEMENT_STATE).reset();
        player.setData(ModAttachments.CONSUMING, true); player.syncData(ModAttachments.CONSUMING);
        var direction = (session.large?ConsumeSequence.contact(target):target.position()).subtract(player.position());
        player.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        player.setYHeadRot(player.getYRot()); player.yBodyRot = player.getYRot();
        if(stealth){
            beginConsume(player,session,ConsumeVariant.chooseStealth(player.getRandom()));
            return;
        }
        if(!session.large){target.setYRot(player.getYRot()+180); target.setYHeadRot(target.getYRot()); target.yBodyRot=target.getYRot();}
        play(player,form==PowerForm.BLADE?PlayerAction.GRAB_BLADE:form==PowerForm.WHIPFIST?PlayerAction.GRAB_WHIP:PlayerAction.GRAB_REACH);
        if (form == PowerForm.WHIPFIST)
            com.codex.blacklight.registry.ModWeaponSounds.play(player, form, "swing", player.getEyePosition(), .52F, 1.16F);

    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session claimed=ACTIVE.get(player);
        try(var motion=ConsumeControl.motion(player,claimed==null?null:claimed.victim)) {
            tickPlayer(player);
        }
        // New paired motion may move this same player before the old receiver settles.
        tickTails(player);
    }
    private static void tickPlayer(ServerPlayer player) {
        var body=player.getData(ModAttachments.CONSUME_BODY_VISUAL);
        if(body.active() && (!player.isAlive() || !player.getData(ModAttachments.PLAYER_STATE).isPrototypeMode()
                ||body.elapsed(player.level().getGameTime(),0)>ConsumeGiverEffect.tick(ConsumeGiverEffect.bodyEnd(body)))){
            player.setData(ModAttachments.CONSUME_BODY_VISUAL,ConsumeVisual.EMPTY);player.syncData(ModAttachments.CONSUME_BODY_VISUAL);
        }
        var residue = player.getData(ModAttachments.CONSUME_RESIDUE_VISUAL);
        if (residue.active() && (!player.isAlive() || !player.getData(ModAttachments.PLAYER_STATE).isPrototypeMode()
                || residue.elapsed(player.level().getGameTime(), 0) >= ConsumeGiverEffect.tick(ConsumeGiverEffect.residueEnd(residue)))) {
            player.setData(ModAttachments.CONSUME_RESIDUE_VISUAL, ConsumeVisual.EMPTY);
            player.syncData(ModAttachments.CONSUME_RESIDUE_VISUAL);
        }
        var recovery=player.getData(ModAttachments.CONSUME_RECOVERY);
        if(recovery.active() && (recovery.phaseProgress(player.level().getGameTime(),0)>=1
                ||!player.isAlive()||!player.getData(ModAttachments.PLAYER_STATE).isPrototypeMode()
                ||player.getData(ModAttachments.WEAPON_VISUAL).spec()!=null||ModAttachments.peekData(player, ModAttachments.CONSUMING, false)))clearRecovery(player);
        Session session = ACTIVE.get(player); if (session == null) return;
        Mob target = session.victim();
        // Capture locks travel input, so drift here comes from outside: explosion lift, a launch,
        // another player's heavy hit. Pull the actor back before the reach check instead of dropping the grab.
        if(!session.large && session.stage==ConsumeVisual.CAPTURE && player.isAlive() && player.position().distanceToSqr(session.start)>1){
            // setPos moves the server body; teleportTo also corrects the owning client.
            player.setPos(session.start);player.teleportTo(session.start.x,session.start.y,session.start.z);
            player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;
        }
        if (!valid(player,session)) {
            cancel(player);return;
        }
        // Publish the airborne entry once. The source float is a transition, not
        // a loop; both clients hold its final pose until landing or throwing.
        if(session.stage==ConsumeVisual.HOLD||session.stage==ConsumeVisual.AIR_HOLD){
            boolean airborne=AerialCarryMotion.airborne(player);
            if(airborne&&session.stage==ConsumeVisual.HOLD)phase(player,session,ConsumeVisual.AIR_HOLD);
            else if(!airborne&&session.stage==ConsumeVisual.AIR_HOLD)phase(player,session,ConsumeVisual.HOLD);
        }
        int elapsed=player.tickCount-session.started;
        if(ConsumeVisual.blocksTravel(session.stage))player.setDeltaMovement(0,player.getDeltaMovement().y,0);
        target.setDeltaMovement(Vec3.ZERO);
        if(session.stage==ConsumeVisual.GRAB_ATTACK){tickHeldAttack(player,session,elapsed);return;}
        if (session.form == PowerForm.WHIPFIST && elapsed == 14
                && (session.stage == ConsumeVisual.WHIP_PULL || session.stage == ConsumeVisual.CAPTURE)
                && player.hasLineOfSight(target))
            com.codex.blacklight.registry.ModWeaponSounds.play(player, session.form, "hit", target.getBoundingBox().getCenter(), .64F, .82F);
        if(session.stage==ConsumeVisual.WHIP_PULL){
            String branch=target instanceof BlacklightHunter?"hunter":"heavy";
            int limit=(int)Math.ceil(CombatMotion.whip(branch,"TimeEnd",5)*20)+14;
            if(elapsed>limit){cancel(player);return;}
            if(elapsed<14)return; // The extending arm must reach before hauling its owner.
            var delta=target.position().subtract(player.position());double distance=delta.horizontalDistance();
            float desired=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
            float yaw=CombatPursuit.turn(player.getYRot(),player.getYRot(),desired,(float)(CombatMotion.whip(branch,"FaceTargetMaxTurnRate",180)/20),180);
            player.setYRot(yaw);player.setYHeadRot(yaw);player.yBodyRot=yaw;
            double stop=Math.max(1.2,(target.getBbWidth()+player.getBbWidth())*.5+.1);
            if(distance<stop+.25&&Math.abs(delta.y)<Math.max(1,target.getBbHeight()*.5)){
                player.setDeltaMovement(Vec3.ZERO);player.hurtMarked=true;
                session.stuckTicks=0;
                if(session.large)phase(player,session,ConsumeVisual.MOUNT);
                else phase(player,session,ConsumeVisual.CAPTURE,14);
                return;
            }
            if(player.horizontalCollision||player.position().distanceToSqr(session.lastPosition)<.0001)session.stuckTicks++;else session.stuckTicks=0;
            session.lastPosition=player.position();
            if(session.stuckTicks>=Math.ceil(CombatMotion.whip(branch,"MaxStuckTime",.5)*20)){cancel(player);return;}
            // The fixed source pull window must allow a clear, in-range hunter to be reached.
            // Seed just enough speed to close the remaining gap within that time budget.
            if(session.pullSpeed==0)session.pullSpeed=Math.max(0,distance-stop)/Math.max(1,limit-elapsed);
            session.pullSpeed=Math.min(CombatMotion.whip(branch,"Velocity",40)*.87/20,
                    session.pullSpeed+CombatMotion.whip(branch,"Acceleration",10)*.87/400);
            var horizontal=CombatMotion.chase(delta,stop,session.pullSpeed);
            // Bounded arc clears terrain; collision and LOS remain authoritative.
            double arc=Math.min(1.2,CombatMotion.whip(branch,"ParabolicConstant",.7)*Math.min(distance,4));
            player.setDeltaMovement(horizontal.x,Math.clamp((delta.y+arc)*.2,-.5,.4),horizontal.z);player.fallDistance=0;player.hurtMarked=true;
            return;
        }
        if(session.large) {
            if(!mountTick(player,session,elapsed))return;
            finish(player,session);return;
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
                phase(player,session,ConsumeVisual.HOLD);
                if(SpiderPhase.edible(target))player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.blacklight.greene_grip_hint",net.minecraft.network.chat.Component.keybind("key.blacklight.disguise")),true);
                else message(player,"grab_held");
                if(session.consumeQueued)consume(player);
                else if(session.throwAim != null)throwTarget(player);
            }
            return;
        }
        if(session.stage==ConsumeVisual.HOLD||session.stage==ConsumeVisual.AIR_HOLD) {
            carry(player,target,3.2);
            if(session.chargeStart>=0){
                long now=player.level().getGameTime();
                if(now-session.chargeInput>25)clearThrowCharge(player,session);
                else if(player.level() instanceof net.minecraft.server.level.ServerLevel level&&(now-session.chargeStart)%4==0){
                    // Wind-up: the held body strains and bleeds a little more as the charge grows.
                    float f=chargeFraction((int)(now-session.chargeStart));var c=target.position().add(0,target.getBbHeight()*.5,0);
                    level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState()),c.x,c.y,c.z,2+(int)(6*f),.2,.2,.2,.05);
                    if(f>=1&&now-session.chargeStart==THROW_CHARGE_TICKS)level.playSound(null,player.blockPosition(),SoundEvents.PLAYER_ATTACK_STRONG,SoundSource.PLAYERS,1F,.55F);
                }
            }
            if(target.position().distanceToSqr(anchor(player,target))>1.0){cancel(player);return;}
            return;
        }
        if(session.stage==ConsumeVisual.THROW||session.stage==ConsumeVisual.AIR_THROW) {
            boolean aerial=session.stage==ConsumeVisual.AIR_THROW;
            if(aerial){player.setYRot(session.facing);player.setYHeadRot(session.facing);player.yBodyRot=session.facing;}
            carry(player,target,3.2);
            // A wall must not leave the body behind while the animation releases it remotely.
            if(aerial&&target.position().distanceToSqr(anchor(player,target))>1){cancel(player);return;}
            if(elapsed>=(aerial?AerialCarryMotion.releaseTick():6)) {
                if(aerial){
                    player.setData(ModAttachments.CONSUME_RECOVERY,target.getData(ModAttachments.CONSUME_VISUAL));
                    player.syncData(ModAttachments.CONSUME_RECOVERY);
                }
                ACTIVE.remove(player);release(target);unlock(player);clearThrowCharge(player,session);
                if(aerial)play(player,PlayerAction.CLAW_IDLE);
                boolean muscle=session.form==PowerForm.MUSCLE_MASS;float charge=chargeFraction(session.throwCharge);
                // A full wind-up throws ~1.6x faster and deals ~2.5x damage; a tap keeps the old throw.
                double speed=(muscle?2.3:1.55)*(1+.6*charge);float damage=(muscle?12:6)*(1+1.5F*charge);
                target.setOnGround(false);target.setDeltaMovement(aimThrow(player,target,session.throwAim,speed,charge));target.hurtMarked=true;
                THROWN.put(target,new Thrown(player,40,target.getDeltaMovement(),damage,charge));
                if(charge>=.9F&&player.level() instanceof net.minecraft.server.level.ServerLevel)
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new com.codex.blacklight.network.PhysicalFeedbackPayload(com.codex.blacklight.network.PhysicalFeedbackPayload.Effect.CLAW_HEAVY,1.2F));
                target.setData(ModAttachments.CONSUME_VISUAL,new ConsumeVisual(player.getId(),player.level().getGameTime(),40,session.form.ordinal(),ConsumeVisual.FLIGHT));
                target.syncData(ModAttachments.CONSUME_VISUAL);
                RagdollImpact.send(target,ImpactProfile.FALL,55,false,target.getDeltaMovement());
                if (session.form == PowerForm.WHIPFIST)
                    com.codex.blacklight.registry.ModWeaponSounds.play(player, session.form, "swing", target.getBoundingBox().getCenter(), .58F, 1.28F);
                else player.level().playSound(null,target.blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,.8F,.7F);
            }
            return;
        }
        if(target instanceof BlacklightHunter hunter)hunter.setConsumeTicks(elapsed);
        if(ConsumeSequence.paired(session.form,false,session.variant)) {
            player.setYRot(session.facing);player.setYHeadRot(session.facing);player.yBodyRot=session.facing;
            if(session.landingDelay<0){
                int gate=ConsumeVisual.groundGate(session.form.ordinal(),session.variant);
                if(elapsed>=gate){
                    pairedPlacement(player,session,gate);
                    float t=ConsumeSequence.motionProgress(session.form,gate/(float)session.duration(),session.duration(),false,session.variant);
                    var floor=ConsumePlacementTrack.landingContact(target,target.position(),session.facing,session.variant,t,player.getY());
                    if(floor.isPresent()){
                        double delta=floor.get().y-target.getY();
                        target.move(MoverType.SELF,new Vec3(0,Math.max(-.32,Math.min(.32,delta)),0));
                    }
                    if(floor.isEmpty() || Math.abs(target.getY()-floor.get().y)>.055){
                        if(elapsed-gate>=10)cancel(player);
                        return;
                    }
                    session.landingDelay=elapsed-gate;
                    var landed=ModAttachments.peekData(target,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY).landed(session.landingDelay);
                    target.setData(ModAttachments.CONSUME_VISUAL,landed);target.syncData(ModAttachments.CONSUME_VISUAL);
                    Vec3 at=floor.get();
                    var landing=new com.codex.blacklight.network.ConsumeContactSoundPayload(target.getId(),player.getId(),
                            landed.started(),com.codex.blacklight.network.ConsumeContactSoundPayload.LANDING,"body_land",at);
                    for (ServerPlayer listener:player.serverLevel().players())
                        if(listener==player || listener.position().distanceToSqr(at)<32*32)net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(listener,landing);
                    return;
                }
            }
            elapsed-=Math.max(0,session.landingDelay);
            triggerBodyWeave(player,session,elapsed);
            pairedPlacement(player,session,elapsed);
            consumeEffects(player,session,elapsed);
            if(elapsed>=session.completionTick())finish(player,session);
            else if((session.travelRequested||session.grabQueued)&&elapsed>=mobileAt(session))startTail(player,session);
            return;
        }
        triggerBodyWeave(player,session,elapsed);
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
        consumeEffects(player,session,elapsed);
        if (session.form() == PowerForm.HAMMERFISTS && elapsed == impact) {
            TerrainImpactEffects.heldBodyFloor(target, 26);
        }
        // Range and sight were checked when committing; scene motion cannot cancel absorption.
        if(elapsed>=session.completionTick())finish(player,session);
        else if((session.travelRequested||session.grabQueued)&&elapsed>=mobileAt(session))startTail(player,session);
    }
    private static void triggerBodyWeave(ServerPlayer player,Session session,int elapsed){
        if (!session.absorbSound && elapsed>=session.returnSoundTick) {
            session.absorbSound=true;
            ConsumeSounds.phase(player,session.sound("absorb"),ConsumeSequence.contact(session.victim),1F,1F);
        }
        if (session.bodyWeaveStarted || !session.smallDirect) return;
        var scene = session.victim.getData(ModAttachments.CONSUME_VISUAL);
        float start = ConsumeGiverEffect.exteriorStart(scene);
        if (!Float.isFinite(start) || elapsed < ConsumeGiverEffect.tick(start)) return;
        session.bodyWeaveStarted = true;
        publishCoat(player, scene);
    }
    private static void publishCoat(ServerPlayer player,ConsumeVisual scene){
        var current=player.getData(ModAttachments.CONSUME_BODY_VISUAL);
        // A slower receiver from a chain must never replace its successor on the player.
        if(current.active() && current.started()>=scene.started())return;
        ConsumeSounds.phase(player,"weave",player.position().add(0,player.getBbHeight()*.7,0),.86F,1F);
        player.setData(ModAttachments.CONSUME_BODY_VISUAL,scene);player.syncData(ModAttachments.CONSUME_BODY_VISUAL);
    }
    private static void tickHeldAttack(ServerPlayer player,Session session,int elapsed){
        var target=session.victim;
        if(!player.onGround()||player.position().distanceToSqr(session.start)>9){cancel(player);return;}
        var visual=target.getData(ModAttachments.CONSUME_VISUAL);
        float t=HeldSlamMotion.progress(visual,player.level().getGameTime(),0);
        Vec3 actorGoal=HeldSlamMotion.actor(session.start,session.facing,t);
        Vec3 delta=actorGoal.subtract(player.position());
        player.move(MoverType.SELF,delta.length()>.6?delta.normalize().scale(.6):delta);
        player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;
        player.setYRot(session.facing);player.setYHeadRot(session.facing);player.yBodyRot=session.facing;
        if(delta.lengthSqr()>.000001)player.connection.teleport(player.getX(),player.getY(),player.getZ(),session.facing,player.getXRot());
        Vec3 goal=ConsumePlacementTrack.pairedGoal(HeldSlamMotion.victim(session.start,session.facing,target,t),player.position(),actorGoal);
        Vec3 travel=goal.subtract(target.position());
        target.move(MoverType.SELF,travel.length()>1.25?travel.normalize().scale(1.25):travel);
        target.setDeltaMovement(Vec3.ZERO);target.fallDistance=0;
        target.setYRot(session.facing);target.setYHeadRot(session.facing);target.yBodyRot=session.facing;
        // A wall must not create a remote hit at an unreachable authored position.
        if(goal.distanceToSqr(target.position())>1.5*1.5){cancel(player);return;}
        if(elapsed<HeldSlamMotion.IMPACT_TICK)return;
        // Detach first: damage/death callbacks may otherwise re-enter and cancel this scene.
        ACTIVE.remove(player);clearThrowCharge(player,session);
        player.setData(ModAttachments.CONSUME_RECOVERY,visual);player.syncData(ModAttachments.CONSUME_RECOVERY);
        release(target);unlock(player);play(player,PlayerAction.CLAW_IDLE);
        Vec3 center=target.position();float damage=session.form==PowerForm.MUSCLE_MASS?18:10;
        if(target.hurt(target.damageSources().playerAttack(player),damage)){
            if(target.isAlive())ImpactControl.begin(target,new ImpactProfile(HitReaction.DOWN,ImpactProfile.DOWN,32,0,0,true),session.facing);
            RagdollImpact.send(target,ImpactProfile.FALL,45,false,new Vec3(0,-.25,0));
        }
        // Small, server-selected splash. The held body is explicitly excluded.
        for(var other:player.serverLevel().getEntitiesOfClass(LivingEntity.class,target.getBoundingBox().inflate(2,.65,2),
                e->e!=target&&e!=player&&CombatTargeting.damageable(player,e))){
            if(other.position().distanceToSqr(center)>4||!CombatTargeting.visible(player,other))continue;
            if(other.hurt(other.damageSources().playerAttack(player),damage*.35F))
                WeaponImpact.contact(player,other,ImpactTier.HEAVY,false,com.codex.blacklight.network.PhysicalFeedbackPayload.Effect.CLAW_HEAVY);
        }
        TerrainImpactEffects.heldBodyFloor(target, 18);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new com.codex.blacklight.network.PhysicalFeedbackPayload(com.codex.blacklight.network.PhysicalFeedbackPayload.Effect.CLAW_HEAVY,.7F));
    }
    private static boolean mountTick(ServerPlayer player,Session session,int elapsed) {
        Mob target=session.victim;
        boolean fixedFacing=ConsumeSequence.bruteMount(target);
        if(fixedFacing)ConsumeSequence.mountFacing(target,session.facing);
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
        float yaw=fixedFacing&&session.stage!=ConsumeVisual.MOUNT?session.facing:(float)Math.toDegrees(Math.atan2(-facing.x,facing.z));
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
        triggerBodyWeave(player,session,elapsed);
        return elapsed>=session.completionTick();
    }
    private static void pairedPlacement(ServerPlayer player,Session session,int elapsed) {
        Mob target=session.victim;
        float t=ConsumeSequence.motionProgress(session.form,elapsed/(float)session.duration(),session.duration(),false,session.variant);
        Vec3 actorGoal=ConsumePlacementTrack.actorAnchor(session.start,session.facing,session.form,t,false,session.variant);
        Vec3 before=player.position(),actorDelta=actorGoal.subtract(before);
        player.move(MoverType.SELF,actorDelta.length()>.8?actorDelta.normalize().scale(.8):actorDelta);
        player.fallDistance=0;player.setDeltaMovement(Vec3.ZERO);
        if(before.distanceToSqr(player.position())>.000001)player.connection.teleport(player.getX(),player.getY(),player.getZ(),session.facing,player.getXRot());
        Vec3 goal=ConsumePlacementTrack.anchor(session.start,session.facing,target,session.form,t,session.variant).orElse(session.consumeTargetStart==null?target.position():session.consumeTargetStart);
        goal=ConsumePlacementTrack.pairedGoal(goal,player.position(),actorGoal);
        int gate=ConsumeVisual.groundGate(session.form.ordinal(),session.variant);
        if(gate>=0 && elapsed>=gate){
            var surface=ConsumePlacementTrack.landingSurface(target,goal,session.facing,session.variant,t,player.getY());
            if(surface.isPresent())goal=new Vec3(goal.x,surface.getAsDouble(),goal.z);
        }
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
        var receiverScene=ConsumeReceiverScene.profile(animationForm,session.large,session.variant);
        Vec3 point=ConsumeSequence.contact(session.victim);
        if(!session.large && !ConsumeSequence.paired(session.form,false,session.variant)){
            receiverScene=null;
            contact=ConsumeVisual.impactTick(session.form);
        } else if (receiverScene == null && !session.large && animationForm == PowerForm.BLADE)
            contact = Math.round(20 * ConsumeSequence.splitSeconds(animationForm, false, session.variant));
        var scene=session.victim.getData(ModAttachments.CONSUME_VISUAL);
        int ordinal = session.contactDamage.strike(player, session.victim, receiverScene, contact, elapsed, scene);
        if (ordinal >= 0) {
            String sound=ConsumeImpactKind.forContact(scene,ordinal);
            if (!session.large && ConsumeSequence.paired(animationForm,false,session.variant)){
                var socket=ConsumePlacementTrack.impactSocket(player,scene,0);
                if(socket!=null && socket.distanceToSqr(session.victim.position())<9)point=socket;
            }
            var audio=new com.codex.blacklight.network.ConsumeContactSoundPayload(session.victim.getId(),player.getId(),
                    scene.started(),ordinal,sound,point);
            for (ServerPlayer listener:player.serverLevel().players())
                if(listener==player || listener.position().distanceToSqr(point)<32*32)net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(listener,audio);
        }
        if(ConsumeSequence.mountedStage(session.stage)&&(receiverScene!=null?elapsed>=receiverScene.effectSeconds()*20:
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
        boolean paired=session.stage!=ConsumeVisual.MOBILE_CONSUME;
        if(paired){
            ACTIVE.remove(player,session);
            var recovery=target.getData(ModAttachments.CONSUME_VISUAL);
            player.setData(ModAttachments.CONSUME_RECOVERY,recovery);player.syncData(ModAttachments.CONSUME_RECOVERY);
            unlock(player);
        }
        var settledScene = target.getData(ModAttachments.CONSUME_VISUAL);
        release(target);
        if(!defeatForConsume(player,target)){
            if(paired)clearRecovery(player);
            message(player,"consume_cancelled");return;
        }
        if(paired)play(player,PlayerAction.CLAW_IDLE);
        if(!com.codex.blacklight.power.AssimilationSystem.settle(player,target,session.reward,true))return;
        GoddessBrood.settled(player,target);
        if (session.smallDirect && Float.isFinite(ConsumeGiverEffect.exteriorStart(settledScene))
                && settledScene.started() >= player.getData(ModAttachments.CONSUME_RESIDUE_VISUAL).started()) {
            player.setData(ModAttachments.CONSUME_RESIDUE_VISUAL, settledScene);
            player.syncData(ModAttachments.CONSUME_RESIDUE_VISUAL);
        }
        com.codex.blacklight.city.MilitaryCampaignSystem.consumed(player,target);
        com.codex.blacklight.disguise.DisguiseSystem.consumed(player,target);
        com.codex.blacklight.disguise.DisguiseSystem.captured(player,identity);
        ConsumeSounds.phase(player,session.sound("finish"),player.position().add(0,1,0),.96F,1F);
        player.displayClientMessage(Component.translatable("message.blacklight.consume_complete", session.heal()*com.codex.blacklight.power.CombatScale.units(player), session.biomass(), session.ep()), true);
    }
    /** Run the death callback while the absorber and the type of execution are still known. */
    public static boolean executeConsumption(ServerPlayer player, Mob target, float damage) {
        var source = target.damageSources().playerAttack(player);
        target.invulnerableTime = 0;
        return ConsumeLastChance.execute(target, () -> {
            if (target instanceof com.codex.blacklight.entity.BlacklightSupremeHunter supreme)
                return supreme.finishConsumption(player, source, damage);
            if (target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon)
                return dragon.hurt(dragon.head, source, damage);
            return target.hurt(source, damage);
        });
    }

    static boolean defeatForConsume(ServerPlayer player,Mob target) {
        if(ConsumeSequence.boss(target) || target instanceof com.codex.blacklight.entity.BlacklightHive) {
            boolean accepted = executeConsumption(player, target, Math.max(1000, target.getMaxHealth()*20));
            // Zero HP (or the dragon's DYING phase) alone does not prove death was accepted.
            if(!accepted||!((com.codex.blacklight.mixin.LivingDeathAccess)target).blacklight$deathAccepted()){
                return false;
            }
        } else if(target instanceof BlacklightHunter hunter){
            if(hunter.parasite())com.codex.blacklight.power.ParasiteSystem.consumedInfectedHunter(player);
            hunter.beginConsumeRemains();
        }
        else {
            if(target instanceof com.codex.blacklight.world.GreeneHost) return false;
            target.discard();
        }
        return true;
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
                    ||ConsumeVisual.consumeStage(stage)||stage==ConsumeVisual.THROW||stage==ConsumeVisual.GRAB_ATTACK||stage==ConsumeVisual.AIR_THROW);
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
        var visibleCoat=player.getData(ModAttachments.CONSUME_BODY_VISUAL);
        if(session.bodyWeaveStarted && visibleCoat.active() && visibleCoat.started()==session.victim.getData(ModAttachments.CONSUME_VISUAL).started()){
            player.setData(ModAttachments.CONSUME_BODY_VISUAL,ConsumeVisual.EMPTY);player.syncData(ModAttachments.CONSUME_BODY_VISUAL);
        }
        clearThrowCharge(player,session);
        release(session.victim());
        unlock(player);
        if (!player.isRemoved() && player.isAlive() && player.getData(ModAttachments.PLAYER_STATE).isPrototypeMode())
            play(player, PlayerAction.CLAW_IDLE);
        message(player, "consume_cancelled");
    }
    /** Escape a pending grapple without interrupting a committed consume scene. */
    public static boolean releaseUncommitted(ServerPlayer player,boolean onlyMounted){
        var session=ACTIVE.get(player);
        if(session==null||session.committed()||onlyMounted&&!session.large)return false;
        cancel(player);return true;
    }
    private static void release(Mob victim) {
        if (victim.isRemoved()) return;
        if(victim.getData(ModAttachments.MOBILE_CONSUME_CARRY).ownerId()>=0){
            victim.setData(ModAttachments.MOBILE_CONSUME_CARRY,MobileConsumeCarry.EMPTY);victim.syncData(ModAttachments.MOBILE_CONSUME_CARRY);
        }
        if(victim instanceof BlacklightHunter hunter)hunter.release();
        if(victim instanceof com.codex.blacklight.entity.BlacklightSupremeHunter supreme)supreme.release();
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
        if(mob.isRemoved()||!mob.isAlive()||shot.remaining()<=0||!shot.owner().isAlive()||shot.owner().isRemoved()||shot.owner().isSpectator()||shot.owner().level()!=mob.level()) {
            THROWN.remove(mob);release(mob);return;
        }
        event.setCanceled(true);
        Vec3 before=mob.position();
        Vec3 from=before.add(0,mob.getBbHeight()*.5,0),requested=from.add(shot.velocity());
        Vec3 half=new Vec3(mob.getBbWidth()*.5,mob.getBbHeight()*.5,mob.getBbWidth()*.5);
        if(!Double.isFinite(shot.velocity().lengthSqr())||shot.velocity().lengthSqr()>64*64||!BlockThrowCollision.loaded(mob.level(),from,requested,half)){
            THROWN.remove(mob);release(mob);mob.setDeltaMovement(Vec3.ZERO);return;
        }
        Vec3 wall=BlockThrowCollision.block(mob.level(),mob,from,requested,half),to=wall==null?requested:wall;
        var hit=BlockThrowCollision.entity(mob.level(),mob,from,to,half,
                entity->entity instanceof LivingEntity living&&entity!=mob&&CombatTargeting.damageable(shot.owner(),living));
        var mechanical=com.codex.blacklight.military.vehicle.VehiclePhysicalDamageBridge.nearest((net.minecraft.server.level.ServerLevel)mob.level(),mob,from,to,half);
        double wallDistance=wall==null?Double.POSITIVE_INFINITY:from.distanceTo(wall);
        if(hit!=null&&from.distanceTo(hit.getLocation())>=wallDistance-1e-5)hit=null;
        if(mechanical!=null&&mechanical.distance()>=wallDistance-1e-5)mechanical=null;
        if(mechanical!=null&&hit!=null){if(mechanical.distance()<=from.distanceTo(hit.getLocation())+1e-5)hit=null;else mechanical=null;}
        Vec3 contact=mechanical!=null?mechanical.point():hit!=null?hit.getLocation():to;
        mob.move(MoverType.SELF,contact.subtract(from));mob.setDeltaMovement(shot.velocity());mob.hurtMarked=true;mob.fallDistance=0;
        // Native movement may stop first at an entity or a conservative rotated hull envelope.
        // A later exact geometric contact must not deal remote damage past that real stop.
        boolean stoppedBeforeContact=mob.position().add(0,mob.getBbHeight()*.5,0).distanceToSqr(contact)>.03*.03;
        if(stoppedBeforeContact){hit=null;mechanical=null;}
        if(stoppedBeforeContact||hit!=null||mechanical!=null||wall!=null||mob.horizontalCollision||mob.verticalCollision) {
            THROWN.remove(mob);release(mob);mob.setDeltaMovement(Vec3.ZERO);
            if(hit instanceof BlockThrowCollision.AnimatedHit posed && hit.getEntity() instanceof com.codex.blacklight.entity.EncounterMob boss && boss.spider.live()){
                int part=SpiderAnatomy.NAMES.indexOf(posed.contact().bone());
                if(part>=0 && boss.spider.receiveThrown(mob,part,mob.damageSources().thrown(mob,shot.owner()),from.distanceTo(contact)/Math.max(1e-6,shot.velocity().length())))return;
            }
            boolean full=shot.charge()>=.9F;var tier=full?ImpactTier.FINISHER:ImpactTier.HEAVY;
            if(hit!=null&&BlockThrowCollision.hurt(hit,mob.damageSources().thrown(mob,shot.owner()),shot.damage())&&hit.getEntity() instanceof LivingEntity struck){
                boolean physical=RagdollImpact.supportedBody(struck)&&ImpactControl.begin(struck,
                        new ImpactProfile(HitReaction.HEAVY,ImpactProfile.FALL,40,.5,.25,true),mob.getYRot());
                if(physical){
                    double resist=Math.clamp(1-struck.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE),0,1);
                    Vec3 push=shot.velocity().scale(.35*resist);
                    struck.setDeltaMovement(push.x,Math.max(.15*resist,push.y),push.z);struck.setOnGround(false);struck.hurtMarked=true;
                }
                WeaponImpact.contact(shot.owner() instanceof ServerPlayer owner?owner:null,struck,tier,true,com.codex.blacklight.network.PhysicalFeedbackPayload.Effect.CLAW_HEAVY);
                if(physical||!struck.isAlive())RagdollImpact.send(struck,ImpactProfile.FALL,45,false,HitStop.launchVelocity(struck));
                if(!RagdollImpact.supportedBody(struck)&&(full||shot.damage()>=struck.getMaxHealth()*1.5F))HeavyBlow.shatter(struck,shot.velocity());
            }
            if(mechanical!=null)com.codex.blacklight.military.vehicle.VehicleDamageBridge.hurtPhysical(shot.owner(),mechanical.vehicle(),shot.damage(),mob,mechanical.point());
            // The thrown body takes the same hit; a charged throw that kills it bursts it against the wall.
            // A full wind-up is always lethal for the (already grabbable) body.
            // Charged throws are executions: the one-time last-chance save must not keep the body intact.
            float selfDamage=full?Math.max(shot.damage(),mob.getHealth()+1):shot.damage();
            if(shot.charge()>=.5F)ConsumeLastChance.execute(mob,()->mob.hurt(mob.damageSources().thrown(mob,shot.owner()),selfDamage));
            else mob.hurt(mob.damageSources().thrown(mob,shot.owner()),selfDamage);
            boolean slam=hit!=null||mechanical!=null||mob.horizontalCollision&&shot.velocity().horizontalDistance()>.8;
            if(mob.isAlive())ImpactControl.begin(mob,new ImpactProfile(HitReaction.DOWN,ImpactProfile.DOWN,32,0,0,true),mob.getYRot());
            RagdollImpact.send(mob,ImpactProfile.FALL,48,false,shot.velocity().scale(.18));
            if(!RagdollImpact.supportedBody(mob)&&shot.charge()>=.5F&&(slam||full))HeavyBlow.shatter(mob,shot.velocity());
            if(full&&shot.owner() instanceof ServerPlayer owner)net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(owner,new com.codex.blacklight.network.PhysicalFeedbackPayload(com.codex.blacklight.network.PhysicalFeedbackPayload.Effect.CLAW_HEAVY,1.6F));
            mob.level().playSound(null,mob.blockPosition(),SoundEvents.GENERIC_BIG_FALL,SoundSource.PLAYERS,.8F+.5F*shot.charge(),.8F-.2F*shot.charge());
        } else THROWN.put(mob,new Thrown(shot.owner(),shot.remaining()-1,shot.velocity().add(0,-.08,0).scale(.98),shot.damage(),shot.charge()));
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
        if(ConsumeContactDamage.owns(event.getEntity(), event.getSource())) return;
        if(ABSORBING.containsKey(event.getEntity())||ACTIVE.values().stream().anyMatch(s->s.committed()&&s.victim==event.getEntity()))event.setCanceled(true);
    }
    @SubscribeEvent public static void knockback(net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent event) {
        if(ABSORBING.containsKey(event.getEntity())||grappleProtected(event.getEntity())
                ||ACTIVE.values().stream().anyMatch(s->s.victim==event.getEntity()))event.setCanceled(true);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p){cancelTails(p);cancel(p);} }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p){cancelTails(p);cancel(p);} }
    /** Freeze the captured mob without writing NoAI, gravity, or invulnerability into its saved NBT. */
    @SubscribeEvent public static void freeze(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof Mob mob)) return;
        var visual = ModAttachments.peekData(mob, ModAttachments.CONSUME_VISUAL, ConsumeVisual.EMPTY);
        if(visual.stage()==ConsumeVisual.FLIGHT) return;
        if (!visual.active()) return;
        boolean claimed = ABSORBING.containsKey(mob)||ACTIVE.values().stream().anyMatch(session -> session.victim() == mob);
        if (claimed) event.setCanceled(true);
        else {
            mob.setData(ModAttachments.CONSUME_VISUAL, ConsumeVisual.EMPTY); mob.syncData(ModAttachments.CONSUME_VISUAL);
            if (mob instanceof BlacklightHunter hunter) hunter.release();
        }
    }
    @SubscribeEvent public static void removed(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        THROWN.remove(event.getEntity());
        if(event.getEntity() instanceof ServerPlayer p)cancelTails(p);
        else if(event.getEntity() instanceof Mob mob)ABSORBING.remove(mob);
        for (var entry : List.copyOf(ACTIVE.entrySet()))
            if (entry.getValue().victim() == event.getEntity() || entry.getKey() == event.getEntity()) cancel(entry.getKey());
    }
    @SubscribeEvent public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (var player : List.copyOf(ACTIVE.keySet())) cancel(player);
        for(var tail:List.copyOf(ABSORBING.values()))cancelTails(tail.owner());
        THROWN.clear();
    }
}
