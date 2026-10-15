package com.codex.passives;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
@EventBusSubscriber(modid=PassivesMod.ID)
public final class PassiveEvents {
    @SubscribeEvent public static void tick(PlayerTickEvent.Post e) { if (e.getEntity() instanceof ServerPlayer p) PassiveSystem.tick(p); }
    @SubscribeEvent public static void server(ServerTickEvent.Post e) { PassiveSystem.finishDeaths(); }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent e) { if (!e.isCanceled()) PassiveSystem.queueDeath(e.getEntity(), e.getSource()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        // Copy on death as well as the End return. Never clone from client packets.
        var saved = e.getOriginal().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).copy();
        e.getEntity().getPersistentData().put(Player.PERSISTED_NBT_TAG, saved);
        if (e.getEntity() instanceof ServerPlayer p) HealthGrowth.apply(p, PassiveData.read(p).bonusHealth);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) PassiveSystem.sync(p, true); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) { if (e.getEntity() instanceof ServerPlayer p) PassiveSystem.sync(p, true); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) PassiveSystem.sync(p, true); }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { PassiveSystem.clear(); }
}
