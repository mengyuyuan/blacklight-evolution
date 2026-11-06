package com.codex.blacklight.combat;

import com.codex.blacklight.config.GameplayBalanceConfig;
import com.codex.blacklight.registry.ModAttachments;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** One saved opportunity for an already weakened, consumable creature. */
@EventBusSubscriber(modid="blacklight")
public final class ConsumeLastChance {
    private static final String USED="BlacklightConsumeLastChanceUsed";
    private record Hit(DamageSource source,long tick) {}
    private static final Map<Mob,Hit> HITS=new WeakHashMap<>();
    private static final ThreadLocal<Set<Mob>> EXECUTING=ThreadLocal.withInitial(()->Collections.newSetFromMap(new IdentityHashMap<>()));
    private ConsumeLastChance() {}

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
    public static void incoming(LivingHurtEvent event){
        if(!(event.getEntity() instanceof Mob target)||target.level().isClientSide)return;
        HITS.remove(target);
        if(event.isCanceled()||event.getAmount()<=0||!GameplayBalanceConfig.value(GameplayBalanceConfig.CONSUME_LAST_CHANCE)
                ||used(target)||EXECUTING.get().contains(target)||CrossModCombat.administrative(event.getSource())
                ||!(event.getSource().getEntity() instanceof ServerPlayer player)||!player.isAlive()||player.isSpectator()
                ||player.level()!=target.level()||!ModAttachments.available(player)
                ||!ModAttachments.getData(player,ModAttachments.PLAYER_STATE).isInfected())return;
        float maximum=target.getMaxHealth(),health=target.getHealth();
        // Snapshot BEFORE the hit. A full-health one-shot is still a normal kill.
        if(Float.isFinite(maximum)&&maximum>0&&health>0
                &&health<=maximum*GameplayBalanceConfig.value(GameplayBalanceConfig.CONSUME_LAST_CHANCE_THRESHOLD)
                &&ConsumeSystem.eligibleBiomass(target))
            HITS.put(target,new Hit(event.getSource(),target.level().getGameTime()));
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void death(LivingDeathEvent event){
        if(!(event.getEntity() instanceof Mob target)||target.level().isClientSide)return;
        var hit=HITS.remove(target);
        if(event.isCanceled()||hit==null||hit.source()!=event.getSource()||hit.tick()!=target.level().getGameTime()
                ||used(target)||EXECUTING.get().contains(target)
                ||!GameplayBalanceConfig.value(GameplayBalanceConfig.CONSUME_LAST_CHANCE))return;
        target.getPersistentData().putBoolean(USED,true);
        target.setHealth(1);
        event.setCanceled(true);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){HITS.keySet().removeIf(m->m.getServer()==event.getServer());}
}
