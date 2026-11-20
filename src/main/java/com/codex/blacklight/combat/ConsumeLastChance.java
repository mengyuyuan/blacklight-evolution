package com.codex.blacklight.combat;

import com.codex.blacklight.config.GameplayBalanceConfig;
import com.codex.blacklight.entity.BackspineQuill;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageTypes;
import com.codex.blacklight.registry.ModAttachments;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** One saved opportunity on the first qualifying lethal hit from any weapon. */
@EventBusSubscriber(modid="blacklight")
public final class ConsumeLastChance {
    private static final String USED="BlacklightConsumeLastChanceUsed";
    private static final String WAITING="BlacklightConsumeAwaitingFinisher";
    private static final String LAST_ATTACK="BlacklightConsumeOpeningAttack";
    public static boolean awaitingFinisher(Entity entity) {
        return entity instanceof Mob mob && mob.getMaxHealth() >= ConsumeSystem.DIRECT_GRAB_MAX_HEALTH && mob.getHealth() <= 1
                && mob.getPersistentData().getBoolean(WAITING) && GameplayBalanceConfig.value(GameplayBalanceConfig.CONSUME_LAST_CHANCE);
    }
    private static UUID actionId(DamageSource source) {
        if (source.getDirectEntity() instanceof BackspineQuill quill) return quill.volleyId();
        if (source.getDirectEntity() instanceof ServerPlayer player && source.is(DamageTypes.PLAYER_ATTACK))
            return WeaponCombatSystem.currentAttackId(player);
        return null;
    }
    private record Hit(DamageSource source,long tick) {}
    private static final Map<Mob,Hit> HITS=new WeakHashMap<>();
    private static final ThreadLocal<Set<Mob>> EXECUTING=ThreadLocal.withInitial(()->Collections.newSetFromMap(new IdentityHashMap<>()));
    private ConsumeLastChance() {}

    public static boolean executing(Mob target){return EXECUTING.get().contains(target);}
    public static boolean used(Mob target){return target.getPersistentData().getBoolean(USED);}
    /** Infection replaces an entity; it must not renew its spent opportunity. */
    public static void inherit(Mob source,Mob replacement){if(used(source))replacement.getPersistentData().putBoolean(USED,true);}
    /** A completed absorption executes normally, including bosses and slime prey.
     * The scope ends even if another mod throws or cancels the execution. */
    public static boolean execute(Mob target,BooleanSupplier execution){
        var scope=EXECUTING.get();boolean added=scope.add(target);
        try{return execution.getAsBoolean();}
        finally{if(added)scope.remove(target);if(scope.isEmpty())EXECUTING.remove();}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void incoming(LivingIncomingDamageEvent event){
        if(!(event.getEntity() instanceof Mob target)||target.level().isClientSide)return;
        HITS.remove(target);
        if (awaitingFinisher(target) && !executing(target) && target.getPersistentData().hasUUID(LAST_ATTACK)
                && target.getPersistentData().getUUID(LAST_ATTACK).equals(actionId(event.getSource()))) {
            event.setCanceled(true);
            return;
        }
        if(event.isCanceled()||event.getAmount()<=0||!GameplayBalanceConfig.value(GameplayBalanceConfig.CONSUME_LAST_CHANCE)
                ||used(target)||EXECUTING.get().contains(target)||CrossModCombat.administrative(event.getSource())
                ||!ownedHit(target,event.getSource()))return;
        float maximum=target.getMaxHealth();
        if(Float.isFinite(maximum) && maximum>=ConsumeSystem.DIRECT_GRAB_MAX_HEALTH && target.getHealth()>0
                &&ConsumeSystem.eligibleBiomass(target))
            HITS.put(target,new Hit(event.getSource(),target.level().getGameTime()));
    }
    private static boolean ownedHit(Mob target,DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer player)
            return player.isAlive() && !player.isSpectator() && player.level()==target.level()
                    && player.getData(ModAttachments.PLAYER_STATE).isInfected();
        // A saved parasite still ticks when its owner logs out or changes dimensions.
        return source.getEntity()==null && source.is(QuillHarvest.DRAIN) && QuillHarvest.mark(target).hasUUID("owner");
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void death(LivingDeathEvent event){
        if(!(event.getEntity() instanceof Mob target)||target.level().isClientSide)return;
        var hit=HITS.remove(target);
        if(event.isCanceled()||hit==null||hit.source()!=event.getSource()||hit.tick()!=target.level().getGameTime()
                ||used(target)||EXECUTING.get().contains(target)
                ||!GameplayBalanceConfig.value(GameplayBalanceConfig.CONSUME_LAST_CHANCE))return;
        target.getPersistentData().putBoolean(USED,true);
        target.getPersistentData().putBoolean(WAITING,true);
        UUID attack=actionId(hit.source());
        if (attack!=null) target.getPersistentData().putUUID(LAST_ATTACK,attack);
        target.setHealth(1);
        float yaw=hit.source().getEntity()==null?target.getYRot():hit.source().getEntity().getYRot();
        ImpactControl.consumeOpening(target,yaw);
        event.setCanceled(true);
    }
    @SubscribeEvent public static void heal(net.neoforged.neoforge.event.entity.living.LivingHealEvent event) {
        if (!event.getEntity().level().isClientSide && awaitingFinisher(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void finish(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre event) {
        if (awaitingFinisher(event.getEntity()) && event.getSource().getEntity()!=null && event.getNewDamage()>0
                && event.getNewDamage()<1) event.setNewDamage(1);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){HITS.keySet().removeIf(m->m.getServer()==event.getServer());}
}
