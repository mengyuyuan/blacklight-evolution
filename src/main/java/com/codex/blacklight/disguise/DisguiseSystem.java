package com.codex.blacklight.disguise;

import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.power.UtilityPowerSystem;
import com.codex.blacklight.combat.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import com.codex.blacklight.combat.ForgeDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import java.util.List;

/** Appearance, witnessed hostility, and Minecraft faction camouflage are separate decisions. */
public final class DisguiseSystem {
    private DisguiseSystem(){}
    public static boolean active(Player p){
        var s=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);
        return p.isAlive()&&s.isInfected()&&s.isPrototypeMode()&&s.isDisguised()
                &&com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.DISGUISE_IDENTITY).valid();
    }
    private static boolean enabled(Player p){var s=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);return p.isAlive()&&s.isInfected()&&s.isPrototypeMode();}
    public static boolean morphing(Player p){return com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.DISGUISE_MORPH).active(p.level().getGameTime());}
    public static void captured(ServerPlayer p,DisguiseIdentity identity){
        if(!identity.valid())return;
        boolean seen=active(p)&&!witnesses(p).isEmpty();
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.DISGUISE_IDENTITY,identity);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.DISGUISE_IDENTITY);
        DisguiseAwareness.newIdentity(p);
        var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);state.captureIdentity(identity.type());
        if(seen)DisguiseAwareness.expose(p,true);
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,state);syncAlert(p);
        p.displayClientMessage(Component.translatable("message.blacklight.identity_captured",identity.entityType().getDescription()),true);
    }
    public static void request(ServerPlayer p){
        if(!enabled(p)||p.isSpectator())return;
        var s=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);var identity=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.DISGUISE_IDENTITY);
        if(!identity.valid()){message(p,"disguise_missing");return;}
        if(morphing(p)||com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CONSUMING)||WeaponCombatSystem.busy(p)
                ||com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CLAW_COMBAT).action()!=ClawCombatState.Action.IDLE
                ||com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.MOVEMENT_STATE).wallTraversal().attached()||p.isPassenger()){
            message(p,"disguise_busy");return;
        }
        DisguiseAwareness.data(p);
        boolean entering=!s.isDisguised();
        if(entering){
            s.activate(PowerForm.NONE);
            int mask=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.UTILITY_POWERS)&~UtilityPowerSystem.DEFENSE;
            com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.UTILITY_POWERS,mask);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.UTILITY_POWERS);
            com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.POWER_MORPH,com.codex.blacklight.power.PowerMorph.NONE);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.POWER_MORPH);
        }
        var knowledge=DisguiseAwareness.data(p);
        knowledge.putBoolean("MorphSeen",!witnesses(p).isEmpty());knowledge.putBoolean("MorphReported",false);
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.CONSUME_RECOVERY,ConsumeVisual.EMPTY);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.CONSUME_RECOVERY);
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,s);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.PLAYER_STATE);
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.DISGUISE_MORPH,new DisguiseMorph(p.level().getGameTime(),entering));com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.DISGUISE_MORPH);
        p.level().playSound(null,p.blockPosition(),SoundEvents.SCULK_CATALYST_BLOOM,SoundSource.PLAYERS,.65F,entering?.8F:1.15F);
    }
    /** Visible weapons/defence need Alex's body; returning to it is not itself a global alarm. */
    public static void visiblePower(ServerPlayer p){
        if(!enabled(p))return;
        var s=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);boolean changed=s.isDisguised()||morphing(p);
        boolean seen=!witnesses(p).isEmpty();
        if(seen){DisguiseAwareness.expose(p,s.isDisguised());DisguiseAwareness.expose(p,false);}
        if(changed){
            s.setDisguised(false);DisguiseAwareness.advanceEpoch(p);
            com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.DISGUISE_MORPH,DisguiseMorph.EMPTY);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.DISGUISE_MORPH);
            message(p,seen?"disguise_exposed":"disguise_power_body");
        }
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,s);syncAlert(p);
    }
    /** Consumption completes without a damage event for ordinary receivers. */
    public static void consumed(ServerPlayer p,LivingEntity victim){hostileAct(p,victim,false);}
    private static void hostileAct(ServerPlayer p,LivingEntity victim){
        hostileAct(p,victim,true);
    }
    private static void hostileAct(ServerPlayer p,LivingEntity victim,boolean retaliate){
        if(!enabled(p))return;
        var observers=witnesses(p).stream().filter(mob->mob!=victim).toList();
        // A victim recognizes its attacker even when facing away at contact.
        boolean victimKnows=retaliate&&victim.isAlive()&&!victim.isRemoved()
                &&victim instanceof Mob mob&&!mob.isNoAi()&&recognizer(mob)&&victim.distanceToSqr(p)<=24*24&&mob.hasLineOfSight(p);
        if(!victimKnows&&observers.isEmpty())return;
        boolean disguised=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).isDisguised();
        boolean newlyExposed=DisguiseAwareness.exposure(p,disguised)==0;
        DisguiseAwareness.expose(p,disguised);
        if(morphing(p))DisguiseAwareness.data(p).putBoolean("MorphSeen",true);
        syncAlert(p);
        if(victimKnows&&!p.isCreative()&&!p.isSpectator()&&victim instanceof Mob mob){
            mob.setLastHurtByMob(p);mob.setTarget(p);
            if(mob instanceof net.minecraft.world.entity.NeutralMob neutral){
                neutral.setPersistentAngerTarget(p.getUUID());neutral.startPersistentAngerTimer();
            }
            var brain=mob.getBrain();
            if(brain.checkMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET,
                    net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED))
                brain.setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET,p);
        }
        if(newlyExposed&&disguised)message(p,"disguise_exposed");
    }
    private static boolean recognizer(Mob mob){
        return mob instanceof com.codex.blacklight.entity.BlackwatchSoldier || mob instanceof Monster&&BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace().equals("minecraft");
    }
    private static boolean sees(Mob mob,ServerPlayer p){
        if(!mob.isAlive()||mob.isNoAi()||!recognizer(mob)||!mob.hasLineOfSight(p))return false;
        Vec3 direction=p.getEyePosition().subtract(mob.getEyePosition());
        return tracking(mob,p)||direction.lengthSqr()<4||mob.getViewVector(1).dot(direction.normalize())>=.25;
    }
    private static List<Mob> witnesses(ServerPlayer p){
        return p.serverLevel().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(24),m->m.distanceToSqr(p)<=24*24&&sees(m,p));
    }
    private static boolean pursued(ServerPlayer p){
        return !p.serverLevel().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(24),
                m->m.isAlive()&&!m.isNoAi()&&recognizer(m)&&tracking(m,p)&&m.distanceToSqr(p)<=24*24&&m.hasLineOfSight(p)).isEmpty();
    }
    private static boolean tracking(Mob mob,Player p){
        return mob.getTarget()==p||memory(mob,net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET).orElse(null)==p;
    }
    private static <T> java.util.Optional<T> memory(Mob mob,net.minecraft.world.entity.ai.memory.MemoryModuleType<T> type){
        return mob.getBrain().checkMemory(type,net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)
                ?mob.getBrain().getMemory(type):java.util.Optional.empty();
    }
    private static boolean factionMatches(Mob mob,Player p){
        if(mob instanceof com.codex.blacklight.entity.BlackwatchSoldier){
            var identity=ModAttachments.getData(p,ModAttachments.DISGUISE_IDENTITY);
            return identity.family().equals("blackwatch")||identity.family().equals("civilian")||identity.family().equals("animal");
        }
        if(!BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace().equals("minecraft"))return false;
        var id=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.DISGUISE_IDENTITY);
        return switch(id.family()){
            case "undead"->mob instanceof Zombie||mob instanceof AbstractSkeleton;
            case "illager"->mob instanceof AbstractIllager;
            case "animal"->mob instanceof Monster&&!(mob instanceof Guardian)&&!(mob instanceof Endermite)
                    &&!(mob instanceof Silverfish)&&!(mob instanceof PiglinBrute);
            default->mob.getType()==id.entityType();
        };
    }
    public static boolean fooled(Mob mob,Player p){
        return active(p)&&DisguiseAwareness.exposure(p,true)==0&&factionMatches(mob,p);
    }
    private static void dropFooledTargets(ServerPlayer p){
        if(!active(p))return;
        for(var mob:p.serverLevel().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(24),v->fooled(v,p))){
            boolean pursuing=tracking(mob,p);
            if(mob.getTarget()==p)mob.setTarget(null);
            if(mob.getLastHurtByMob()==p)mob.setLastHurtByMob(null);
            if(mob instanceof net.minecraft.world.entity.NeutralMob neutral&&p.getUUID().equals(neutral.getPersistentAngerTarget())){
                neutral.setPersistentAngerTarget(null);neutral.setRemainingPersistentAngerTime(0);
            }
            var brain=mob.getBrain();var target=net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET;
            if(memory(mob,target).orElse(null)==p)brain.eraseMemory(target);
            var angry=net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT;
            if(memory(mob,angry).filter(p.getUUID()::equals).isPresent())brain.eraseMemory(angry);
            var hurt=net.minecraft.world.entity.ai.memory.MemoryModuleType.HURT_BY_ENTITY;
            if(memory(mob,hurt).orElse(null)==p)brain.eraseMemory(hurt);
            if(pursuing)mob.getNavigation().stop();
        }
    }
    private static void syncAlert(ServerPlayer p){
        var s=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);
        s.raiseAlert(DisguiseAwareness.exposure(p,s.isDisguised())-s.getAlert());
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,s);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.PLAYER_STATE);
    }
    @SubscribeEvent public static void target(LivingChangeTargetEvent e){
        if(e.getEntity() instanceof Mob mob&&e.getNewTarget() instanceof Player p&&fooled(mob,p))
            e.setNewTarget(null);
    }
    @SubscribeEvent public static void damage(ForgeDamageEvent.Post e){
        if(e.getNewDamage()<=0||!(e.getSource().getEntity() instanceof ServerPlayer p)||e.getEntity()==p)return;
        var direct=e.getSource().getDirectEntity();
        if(direct instanceof Projectile shot){if(!DisguiseAwareness.sameShotIdentity(shot,p))return;}
        else if(direct!=p||!e.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK))return;
        hostileAct(p,e.getEntity());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void projectile(EntityJoinLevelEvent e){
        if(!e.getLevel().isClientSide()&&!e.isCanceled()&&!e.loadedFromDisk()
                &&e.getEntity() instanceof Projectile shot&&shot.getOwner() instanceof ServerPlayer p)
            DisguiseAwareness.shot(shot,p);
    }
    @SubscribeEvent public static void tick(PlayerTickEvent e){if(e.phase != net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!(e.player instanceof ServerPlayer p))return;
        var s=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);var m=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.DISGUISE_MORPH);
        long now=p.level().getGameTime();
        if(!enabled(p)){
            if(s.isDisguised()){
                s.setDisguised(false);DisguiseAwareness.advanceEpoch(p);
                com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,s);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.PLAYER_STATE);
            }
            if(m.started()>=0){com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.DISGUISE_MORPH,DisguiseMorph.EMPTY);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.DISGUISE_MORPH);}
            if(!s.isInfected()){
                p.getPersistentData().remove(DisguiseAwareness.KEY);
                if(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.DISGUISE_IDENTITY).valid()){
                    com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.DISGUISE_IDENTITY,DisguiseIdentity.EMPTY);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.DISGUISE_IDENTITY);
                }
            }
            return;
        }
        if(p.getPersistentData().contains(DisguiseAwareness.KEY)
                &&DisguiseAwareness.data(p).getBoolean("LastForm")!=s.isDisguised())DisguiseAwareness.advanceEpoch(p);
        if(active(p)&&(s.getActivePower()!=PowerForm.NONE||(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.UTILITY_POWERS)&UtilityPowerSystem.DEFENSE)!=0)){
            visiblePower(p);return;
        }
        if(m.started()>=0){
            if(s.getActivePower()!=PowerForm.NONE||(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.UTILITY_POWERS)&UtilityPowerSystem.DEFENSE)!=0){visiblePower(p);return;}
            var awareness=DisguiseAwareness.data(p);
            if(!witnesses(p).isEmpty())awareness.putBoolean("MorphSeen",true);
            boolean seen=awareness.getBoolean("MorphSeen");
            if(now-m.started()>=DisguiseMorph.CONTACT&&s.isDisguised()!=m.entering()){
                s.setDisguised(m.entering());DisguiseAwareness.advanceEpoch(p);
                if(seen){DisguiseAwareness.expose(p,false);DisguiseAwareness.expose(p,true);}
                com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,s);syncAlert(p);
                if(!seen&&DisguiseAwareness.exposure(p,m.entering())==0)dropFooledTargets(p);
                message(p,seen?"disguise_exposed":DisguiseAwareness.exposure(p,m.entering())>0?"disguise_known"
                        :m.entering()?"disguise_enabled":"disguise_disabled");
                awareness.putBoolean("MorphReported",seen);
            }
            if(seen&&now-m.started()>=DisguiseMorph.CONTACT&&!awareness.getBoolean("MorphReported")){
                DisguiseAwareness.expose(p,false);DisguiseAwareness.expose(p,true);
                awareness.putBoolean("MorphReported",true);syncAlert(p);message(p,"disguise_exposed");
            }
            if(!m.active(now)){com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.DISGUISE_MORPH,DisguiseMorph.EMPTY);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.DISGUISE_MORPH);}
            else if(p.tickCount%2==0)p.serverLevel().sendParticles(new DustParticleOptions(new org.joml.Vector3f(.24F,.025F,.035F),.65F),
                    p.getX(),p.getY()+p.getBbHeight()*.55,p.getZ(),5,.25,.55,.25,0);
        }
        if(p.tickCount%10!=0)return;
        if(p.getPersistentData().contains(DisguiseAwareness.KEY)
                &&(DisguiseAwareness.exposure(p,false)>0||DisguiseAwareness.exposure(p,true)>0)&&!pursued(p)){
            DisguiseAwareness.decay(p);
            if(s.getAlert()!=DisguiseAwareness.exposure(p,s.isDisguised()))syncAlert(p);
        }
        dropFooledTargets(p);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){
        if(e.isWasDeath())e.getEntity().getPersistentData().remove(DisguiseAwareness.KEY);
        else if(e.getOriginal().getPersistentData().contains(DisguiseAwareness.KEY))
            e.getEntity().getPersistentData().put(DisguiseAwareness.KEY,e.getOriginal().getPersistentData().getCompound(DisguiseAwareness.KEY).copy());
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){
        if(e.getEntity() instanceof ServerPlayer p){DisguiseAwareness.advanceEpoch(p);syncAlert(p);}
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){
        if(e.getEntity() instanceof ServerPlayer p){
            if(e.isEndConquered()){DisguiseAwareness.advanceEpoch(p);syncAlert(p);return;}
            var s=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);s.setDisguised(false);s.raiseAlert(-100);
            p.getPersistentData().remove(DisguiseAwareness.KEY);
            com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,s);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.PLAYER_STATE);
            com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.DISGUISE_MORPH,DisguiseMorph.EMPTY);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.DISGUISE_MORPH);
            com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.DISGUISE_IDENTITY);
        }
    }
    private static void message(ServerPlayer p,String key){p.displayClientMessage(Component.translatable("message.blacklight."+key),true);}
}
