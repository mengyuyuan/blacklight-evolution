package com.codex.blacklight.power;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.BiomassHusk;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Same player and inventory throughout. Only the server may claim/kill a victim or restore a body. */
public final class BiomassSystem {
    private BiomassSystem(){}
    private record RejectedPrey(java.util.UUID target,long until){}
    private static final java.util.Map<ServerPlayer,RejectedPrey> REJECTED=new java.util.WeakHashMap<>();
    public static BiomassState state(Entity p){return ModAttachments.peekData(p,ModAttachments.BIOMASS_FORM,BiomassState.EMPTY);}
    public static boolean active(Entity p){return state(p).active();}
    private static void publish(ServerPlayer p,BiomassState value){
        var previous=state(p);
        if(value.phase()!=BiomassState.ABSORBING||previous.victim()!=value.victim())BiomassAttachment.clear(p);
        if(!value.consumed())BiomassReformation.clear(p);
        if(!value.active())REJECTED.remove(p);

        boolean resize=previous.active()!=value.active();
        if(previous.victim()!=value.victim()&&p.level().getEntity(previous.victim()) instanceof Mob oldTarget)
            oldTarget.removeEffect(com.codex.blacklight.registry.ModEffects.BIOMASS_CONTACT);
        p.setData(ModAttachments.BIOMASS_FORM,value);
        if(value.phase()==BiomassState.ABSORBING){
            ConsumeControl.releaseControls(p);p.setDeltaMovement(Vec3.ZERO);
            if(p.level().getEntity(value.victim()) instanceof Mob target){
                BiomassAttachment.attach(p,target);
                ConsumeControl.releaseControls(target);target.setDeltaMovement(Vec3.ZERO);
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.codex.blacklight.registry.ModEffects.BIOMASS_CONTACT,8,0,false,true,true));
            }
        }
        if(resize)p.refreshDimensions();
    }
    public static boolean begin(ServerPlayer p,boolean fatal){
        var power=p.getData(ModAttachments.PLAYER_STATE);
        if(active(p)||!power.isInfected()||!power.isPrototypeMode()||p.isSpectator()||p.isRemoved())return false;
        if(!fatal&&(com.codex.blacklight.city.HydraTransport.active(p)||!p.isAlive()||p.isPassenger()||p.isSleeping()||p.isFallFlying()
                ||p.getData(ModAttachments.CONSUMING)||WeaponCombatSystem.busy(p)||DevastatorSystem.busy(p)))return false;
        if(fatal&&!power.spendBiomass(Math.max(0,Math.min(1000,p.level().getGameRules().getInt(BiomassRules.COST)))))return false;
        if(fatal){var carrier=com.codex.blacklight.city.HydraTransport.carrier(p);if(carrier!=null)carrier.abortTransport(p);}
        String oldPower=power.getActivePower().id(),shell="";
        if(!fatal){
            var husk=new BiomassHusk(ModEntities.BIOMASS_HUSK.get(),p.level());
            husk.initialize(p);if(!p.serverLevel().addFreshEntity(husk))return false;
            shell=husk.getUUID().toString();
        }
        var heldBlock=BlockThrowSystem.held(p);if(heldBlock!=null)heldBlock.drop();
        ConsumeSystem.cancel(p);WeaponCombatSystem.cancel(p);WeaponCombatSystem.cancelHeldInput(p);
        p.getData(ModAttachments.CLAW_COMBAT).reset();p.getData(ModAttachments.MOVEMENT_STATE).reset();
        p.setData(ModAttachments.JUMP_CHARGE,0);p.syncData(ModAttachments.JUMP_CHARGE);
        p.setData(ModAttachments.PLAYER_ACTION,com.codex.blacklight.animation.NativeActionState.EMPTY);p.syncData(ModAttachments.PLAYER_ACTION);
        power.activate(PowerForm.NONE);power.setDisguised(false);p.syncData(ModAttachments.PLAYER_STATE);
        p.setData(ModAttachments.UTILITY_POWERS,0);p.syncData(ModAttachments.UTILITY_POWERS);
        p.stopRiding();p.stopUsingItem();p.getAbilities().flying=false;p.onUpdateAbilities();p.setSprinting(false);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
        if(fatal){p.setHealth(Math.max(1,p.getMaxHealth()*.05F));p.clearFire();p.invulnerableTime=40;}
        p.getPersistentData().remove("blacklightSlimeTrail");
        publish(p,new BiomassState(fatal?BiomassState.SHATTERING:BiomassState.CRAWLING,p.level().getGameTime(),fatal,shell,oldPower,-1));
        return true;
    }
    /** Recall a voluntary separation. No healing, kill, reward or eruption occurs. */
    public static boolean cancel(ServerPlayer p){
        var s=state(p);
        if(!s.active()||!p.isAlive()||p.isSpectator())return false;
        if(!s.recallable()){
            p.displayClientMessage(Component.translatable(s.fatal()?"message.blacklight.biomass_need_prey":"message.blacklight.biomass_reforming"),true);
            return false;
        }
        BiomassHusk husk=null;
        try{var found=p.serverLevel().getEntity(java.util.UUID.fromString(s.shell()));if(found instanceof BiomassHusk h)husk=h;}catch(IllegalArgumentException ignored){}
        if(husk==null||!husk.isAlive()||husk.erupted()||!p.getUUID().equals(husk.owner())){
            p.displayClientMessage(Component.translatable("message.blacklight.biomass_no_shell"),true);return false;
        }
        Vec3 at=husk.position();
        if(!p.level().noCollision(p,new AABB(at.x-.3,at.y,at.z-.3,at.x+.3,at.y+1.8,at.z+.3))){
            p.displayClientMessage(Component.translatable("message.blacklight.biomass_blocked_shell"),true);return false;
        }
        // Removing the shell before publishing HUMAN avoids its automatic burst.
        // Clearing the transient claim immediately releases an unfinished victim.
        husk.discard();p.teleportTo(at.x,at.y,at.z);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
        var power=p.getData(ModAttachments.PLAYER_STATE);power.activate(PowerForm.byId(s.previousPower()));
        publish(p,BiomassState.EMPTY);p.syncData(ModAttachments.PLAYER_STATE);
        p.displayClientMessage(Component.translatable("message.blacklight.biomass_recalled"),true);
        return true;
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void death(LivingDeathEvent e){
        if(e.isCanceled()||!(e.getEntity() instanceof ServerPlayer p))return;
        if(active(p)){publish(p,BiomassState.EMPTY);return;}
        if(!e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)&&begin(p,true))e.setCanceled(true);
    }
    public static boolean edible(ServerPlayer p,Mob victim){
        var rejected=REJECTED.get(p);
        if(rejected!=null){
            if(p.level().getGameTime()>=rejected.until())REJECTED.remove(p);
            else if(rejected.target().equals(victim.getUUID()))return false;
        }
        return !(victim instanceof BiomassHusk)&&ConsumeSystem.eligibleBiomass(victim)
                &&ConsumeSystem.healthReady(p,victim)
                &&(claimant(victim)==null||claimant(victim)==p)&&CombatTargeting.damageable(p,victim)
                &&inContact(p,victim)
                &&(CombatTargeting.multipart(victim)?CombatTargeting.visibleContact(p,victim,p.getBoundingBox().inflate(1.15,.65,1.15))
                    :CombatTargeting.visible(p,p.getEyePosition(),victim.getBoundingBox()));
    }
    private static boolean inContact(Player player,LivingEntity victim){
        var contact=player.getBoundingBox().inflate(1.15,.65,1.15);
        return CombatTargeting.hitboxes(victim).stream().anyMatch(contact::intersects);

    }
    /** A claim is transient and valid only while its live player is absorbing nearby. */
    public static Player claimant(Entity victim){
        if(!(victim instanceof Mob))return null;
        for(var p:victim.level().players()){var s=state(p);
            if(p.isAlive()&&s.phase()==BiomassState.ABSORBING&&s.victim()==victim.getId()
                    &&(inContact(p,(LivingEntity)victim)||p instanceof ServerPlayer server&&BiomassAttachment.attached(server,(Mob)victim)))return p;
        }return null;
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post e){
        if(!(e.getEntity() instanceof ServerPlayer p)||!active(p))return;
        try(var motion=ConsumeControl.motion(p,p.level().getEntity(state(p).victim()))) {
            tickPlayer(p);
        }
    }
    private static void tickPlayer(ServerPlayer p){
        if(!p.isAlive())return;
        if(!p.getData(ModAttachments.PLAYER_STATE).isInfected()) {publish(p,BiomassState.EMPTY);return;}
        var s=state(p);long now=p.level().getGameTime();
        if(s.phase()==BiomassState.SHATTERING){
            p.setDeltaMovement(0,Math.min(0,p.getDeltaMovement().y),0);p.fallDistance=0;p.invulnerableTime=Math.max(p.invulnerableTime,10);
            if(now-s.changedAt()>=BiomassState.SHATTER_TICKS)publish(p,s.crawl(now));
            return;
        }
        if(!s.consumed()){
            float before=BiomassEvolution.scale(p);
            s=s.aged();BiomassSync.storeAge(p,s);
            if(before!=BiomassEvolution.scale(p))p.refreshDimensions();
            int lifetime=BiomassEvolution.lifetime(p);
            if(lifetime>=0&&s.age()>=lifetime){
                if(s.recallable()&&cancel(p))return;
                p.displayClientMessage(Component.translatable("message.blacklight.biomass_expired"),true);
                // Real damage/death events preserve other mods' resurrection and death vetoes.
                p.hurt(p.damageSources().genericKill(),Float.MAX_VALUE);return;
            }
            if(s.fatal()&&s.phase()==BiomassState.CRAWLING)BiomassTerrain.trail(p);
        }
        p.fallDistance=0;p.setSprinting(false);p.stopUsingItem();
        p.getData(ModAttachments.MOVEMENT_STATE).reset();
        if(s.immobilized())p.setDeltaMovement(0,p.getDeltaMovement().y,0);
        if(s.phase()==BiomassState.CRAWLING){
            // Serial server ticks plus the claim below prevent two players consuming one mob.
            var prey=new java.util.ArrayList<Mob>();
            for(var candidate:CombatTargeting.candidates(p,p.getBoundingBox().inflate(1.15,.65,1.15),
                    v->v instanceof Mob mob&&edible(p,mob)))prey.add((Mob)candidate);
            prey.sort(java.util.Comparator.comparingDouble(p::distanceToSqr));
            if(!prey.isEmpty()){
                Mob target=prey.get(0);
                boolean claimed=p.server.getPlayerList().getPlayers().stream().anyMatch(other->other!=p&&other.level()==p.level()
                    &&state(other).phase()==BiomassState.ABSORBING&&state(other).victim()==target.getId());
                if(!claimed&&now-s.changedAt()>=12){publish(p,s.absorb(target.getId(),now));
                    p.displayClientMessage(Component.translatable("message.blacklight.biomass_contact",target.getDisplayName()),true);}
            }
        }else if(s.phase()==BiomassState.ABSORBING){
            Entity entity=p.level().getEntity(s.victim());
            if(!(entity instanceof Mob target)||!BiomassAttachment.follow(p,target)||!edible(p,target)){publish(p,s.crawl(now));return;}
            target.getNavigation().stop();target.setDeltaMovement(Vec3.ZERO);
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.codex.blacklight.registry.ModEffects.BIOMASS_CONTACT,8,0,false,true,true));
            if(now-s.changedAt()<ConsumeProgression.absorbTicks(p))return;
            BiomassReformation.prepare(p);
            var consumeReward=AssimilationSystem.quote(p,target);
            target.invulnerableTime=0;
            // This is the completed consumption execution, not a normal attack.
            // Spore's calamities explicitly permit >= 10000 execution hits past their cap.
            float execution=(float)Math.min(Float.MAX_VALUE,Math.max(10000D,(double)target.getMaxHealth()*20));
            boolean accepted=target.hurt(target.damageSources().playerAttack(p),execution);
            // Respect invulnerability, region protections, death vetoes and resurrection mods.
            if(!accepted||!((com.codex.blacklight.mixin.LivingDeathAccess)target).blacklight$deathAccepted()){
                // Do not immediately reclaim the same immune target and starve other nearby prey.
                REJECTED.put(p,new RejectedPrey(target.getUUID(),now+BiomassState.ABSORB_TICKS));
                publish(p,s.crawl(now));return;
            }
            target.setInvisible(true);
            AssimilationSystem.settle(p,target,consumeReward,false);
            Vec3 destination=BiomassReformation.completionPosition(p,target.position());
            p.teleportTo(destination.x,destination.y,destination.z);
            p.setHealth(p.getMaxHealth());p.clearFire();p.invulnerableTime=40;
            publish(p,s.reform(now));
            burstLoadedHusk(p,s);
        }else if(now-s.changedAt()>=BiomassState.REFORM_TICKS){
            Vec3 room=standingRoom(p);
            if(room==null){if(s.phase()!=BiomassState.READY)publish(p,s.ready(now));return;}
            p.teleportTo(room.x,room.y,room.z);
            var power=p.getData(ModAttachments.PLAYER_STATE);power.activate(PowerForm.byId(s.previousPower()));
            publish(p,BiomassState.EMPTY);p.syncData(ModAttachments.PLAYER_STATE);
            p.displayClientMessage(Component.translatable("message.blacklight.biomass_restored"),true);
        }
    }
    private static Vec3 standingRoom(ServerPlayer p){
        return BiomassReformation.standingRoom(p);
    }

    private static void burstLoadedHusk(ServerPlayer p,BiomassState s){
        if(s.shell().isEmpty())return;
        try {var uuid=java.util.UUID.fromString(s.shell());for(var level:p.server.getAllLevels())
            if(level.getEntity(uuid) instanceof BiomassHusk husk)husk.burst(p);
        }catch(IllegalArgumentException ignored){}
    }
    private static void recoverSession(ServerPlayer p){
        var s=state(p);
        if(s.active()){
            // A committed kill must never need a second victim after relogging.
            publish(p,s.phase()==BiomassState.SHATTERING?new BiomassState(s.phase(),p.level().getGameTime(),s.fatal(),s.shell(),s.previousPower(),-1,s.age()):s.consumed()?s.reform(p.level().getGameTime()):s.crawl(p.level().getGameTime()));
            p.refreshDimensions();
        }
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){
        if(e.getEntity() instanceof ServerPlayer p&&state(p).active()){
            publish(p,state(p).afterRespawn(p.level().getGameTime()));p.refreshDimensions();
        }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)recoverSession(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)recoverSession(p);}
}
