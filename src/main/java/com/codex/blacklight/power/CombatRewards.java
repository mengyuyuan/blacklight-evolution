package com.codex.blacklight.power;

import com.codex.blacklight.combat.CrossModCombat;
import com.codex.blacklight.config.GameplayBalanceConfig;
import com.codex.blacklight.entity.BlacklightHive;
import com.codex.blacklight.registry.ModAttachments;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Commit after death listeners and consume transactions have finished. No pickup entities. */
@EventBusSubscriber(modid="blacklight")
public final class CombatRewards {
    private record Pending(ServerPlayer player, Mob victim, LivingDeathEvent event, int biomass, int ep) {}
    private static final Map<MinecraftServer,Map<UUID,Pending>> PENDING=new IdentityHashMap<>();
    private CombatRewards() {}

    public static int fraction(int full, double ratio) {
        if(full<=0||!Double.isFinite(ratio)||ratio<=0)return 0;
        return (int)Math.min(Integer.MAX_VALUE,Math.round(full*Math.min(1,ratio)));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if(event.isCanceled()||!(event.getEntity() instanceof Mob victim)||victim.level().isClientSide
                ||victim instanceof BlacklightHive||CrossModCombat.administrative(event.getSource())
                ||!GameplayBalanceConfig.KILL_REWARDS.get())return;
        // Owned projectiles/TNT keep their causing player; normal delayed kills use vanilla credit.
        var credited=com.codex.blacklight.combat.CombatOwnership.player(event.getSource()) instanceof ServerPlayer owner?owner:victim.getKillCredit();
        if(!(credited instanceof ServerPlayer player)||!player.isAlive()||player.isSpectator()
                ||player.level()!=victim.level()||!ModAttachments.available(player)
                ||!ModAttachments.getData(player,ModAttachments.PLAYER_STATE).isInfected())return;
        var quote=AssimilationSystem.quote(player,victim);
        int biomass=fraction(quote.biomass(),GameplayBalanceConfig.KILL_BIOMASS.get());
        int ep=fraction(quote.ep(),GameplayBalanceConfig.KILL_EP.get());
        if(biomass==0&&ep==0)return;
        PENDING.computeIfAbsent(player.server,s->new HashMap<>())
                .putIfAbsent(victim.getUUID(),new Pending(player,victim,event,biomass,ep));
    }
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase==TickEvent.Phase.END)flush(event.getServer());
    }
    private static void flush(MinecraftServer server) {
        var pending=PENDING.remove(server);if(pending==null)return;
        var changed=new HashSet<ServerPlayer>();
        for(var reward:pending.values())
            if(!reward.event().isCanceled()&&AssimilationSystem.settleKill(reward.player(),reward.victim(),reward.biomass(),reward.ep(),false))
                changed.add(reward.player());
        // One state packet per recipient, even for a large area attack.
        for(var player:changed)ModAttachments.syncData(player,ModAttachments.PLAYER_STATE);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){PENDING.remove(event.getServer());}
}
