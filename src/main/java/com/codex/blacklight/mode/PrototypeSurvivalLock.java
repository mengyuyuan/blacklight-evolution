package com.codex.blacklight.mode;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Map;
import java.util.WeakHashMap;

/** Prevents vanilla survival interactions while Prototype rules own the player. */
public final class PrototypeSurvivalLock {
    private static final Map<Player, Integer> LAST_CLIENT_NOTICE = new WeakHashMap<>();

    private PrototypeSurvivalLock() {
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!isLocked(event.getEntity())) return;
        event.setCanceled(true);
        notifyClient(event.getEntity());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isLocked(event.getEntity())) return;
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
        notifyClient(event.getEntity());
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!isLocked(event.getEntity())) return;
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
        notifyClient(event.getEntity());
    }

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        // RightClickEmpty is not cancellable in NeoForge 21.1; empty-hand swings
        // carry no survival interaction, so a notice is the only useful handling.
        if (!isLocked(event.getEntity())) return;
        notifyClient(event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!isLocked(event.getEntity())) return;
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
        notifyClient(event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!isLocked(event.getEntity())) return;
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
        notifyClient(event.getEntity());
    }

    /** Prototype combat is sent through its own server-timed actions, never vanilla weapons. */
    @SubscribeEvent
    public static void onVanillaAttack(AttackEntityEvent event) {
        if (!isLocked(event.getEntity())) return;
        event.setCanceled(true);
        notifyClient(event.getEntity());
    }

    /** Server-side backstop for packets or other mods that bypass normal interaction events. */
    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (isLocked(event.getPlayer())&&!com.codex.blacklight.combat.BulletdiveTerrain.ownsBreakEvent(event)&&!com.codex.blacklight.combat.BlockThrowSystem.ownsBreakEvent(event)
                &&!com.codex.blacklight.combat.ShieldBorerSystem.ownsBreakEvent(event)
                &&!com.codex.blacklight.power.BiomassTerrain.ownsBreakEvent(event)) event.setCanceled(true);
    }

    /** Server-side backstop for direct placement paths. */
    @SubscribeEvent
    public static void onPlaceBlock(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && isLocked(player)
                && !com.codex.blacklight.power.BiomassTerrain.ownsPlaceEvent(event)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onModifyBlockWithTool(BlockEvent.BlockToolModificationEvent event) {
        Player player = event.getPlayer();
        if (player != null && isLocked(player)) event.setCanceled(true);
    }

    public static boolean isLocked(Player player) {
        return player != null && PrototypeModeSystem.isActive(player);
    }

    public static void notifyClient(Player player) {
        int now = player.tickCount;
        int last = LAST_CLIENT_NOTICE.getOrDefault(player, Integer.MIN_VALUE / 2);
        if (now - last < 20) return;
        LAST_CLIENT_NOTICE.put(player, now);
        Component message = Component.translatable("message.blacklight.survival_locked");
        if (player.level().isClientSide()) {
            player.displayClientMessage(message, true);
        } else {
            player.displayClientMessage(message, true);
        }
    }
}
