package com.codex.blacklight.mode;

import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.world.BossSiteBlock;
import net.minecraft.core.BlockPos;
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

    /** Mission reports and placement tools retain their use action in Prototype mode. */
    public static boolean placementItem(Player player,net.minecraft.world.item.ItemStack stack){
        return stack.getItem() instanceof com.codex.blacklight.item.EncounterDossierItem
                || com.codex.blacklight.military.vehicle.VehicleRegistry.isVehicleTool(stack)
                ||player.isCreative()&&stack.getItem() instanceof net.minecraft.world.item.SpawnEggItem;
    }
    public static boolean holdingPlacementItem(Player player){
        return player!=null&&(placementItem(player,player.getMainHandItem())||placementItem(player,player.getOffhandItem()));
    }

    /** Allow the encounter console while prototype combat owns ordinary empty-hand use. */
    public static boolean canOperateConsole(Player player, BlockPos pos) {
        if(player==null || player.isSpectator() || player.isPassenger()) return false;
        var state=player.level().getBlockState(pos);
        if(!(state.getBlock() instanceof BossSiteBlock) || player.getData(ModAttachments.CONSUMING)) return false;
        return (player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty())
                || state.getValue(BossSiteBlock.MOTHER) && BossSiteBlock.hasPumpKey(player);
    }

    /** Disabling pickup leaves the dropped item intact; enabling it keeps vanilla ownership/delay rules. */
    @SubscribeEvent
    public static void onItemPickup(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre event) {
        if (isLocked(event.getPlayer()) && !event.getPlayer().getData(
                com.codex.blacklight.registry.ModAttachments.SKILL_SETTINGS).prototypeItemPickup()) {
            event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!isLocked(event.getEntity())) return;
        event.setCanceled(true);
        notifyClient(event.getEntity());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isLocked(event.getEntity()) || canOperateConsole(event.getEntity(),event.getPos())) return;
        if(!event.getEntity().isSpectator()&&holdingPlacementItem(event.getEntity())){
            if(!placementItem(event.getEntity(),event.getItemStack())){
                // Vanilla tries the main hand first. Yield without using it so the offhand tool can run.
                event.setCancellationResult(InteractionResult.PASS);event.setCanceled(true);return;
            }
            // Allow the actual item-use path, without also opening/using the clicked survival block.
            event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);return;
        }
        if(com.codex.blacklight.firearms.FirearmsCombat.holdingFirearm(event.getEntity())){
            // Suppress block interaction while permitting vanilla's follow-up item-use path.
            event.setCancellationResult(InteractionResult.PASS);event.setCanceled(true);return;
        }
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
        notifyClient(event.getEntity());
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if(isLocked(event.getEntity())&&!event.getEntity().isSpectator()&&holdingPlacementItem(event.getEntity())){
            if(!placementItem(event.getEntity(),event.getItemStack())){
                event.setCancellationResult(InteractionResult.PASS);event.setCanceled(true);
            }
            return;
        }
        if (event.getItemStack().getItem() instanceof com.codex.blacklight.firearms.FirearmItem) return;
        if (event.getItemStack().getItem() instanceof com.codex.blacklight.item.ParasiteSyringeItem) return;
        if (!isLocked(event.getEntity())) return;
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
        notifyClient(event.getEntity());
    }

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if(holdingPlacementItem(event.getEntity()))return;
        // RightClickEmpty is not cancellable in NeoForge 21.1; empty-hand swings
        // carry no survival interaction, so a notice is the only useful handling.
        if (!isLocked(event.getEntity())) return;
        notifyClient(event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if(com.codex.blacklight.military.vehicle.VehicleDamageBridge.vehicle(event.getTarget())!=null||event.getTarget() instanceof com.codex.blacklight.entity.BlackwatchHelicopter)return;
        if(event.getItemStack().getItem() instanceof com.codex.blacklight.item.ParasiteSyringeItem)return;
        if (!isLocked(event.getEntity())) return;
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
        notifyClient(event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if(com.codex.blacklight.military.vehicle.VehicleDamageBridge.vehicle(event.getTarget())!=null||event.getTarget() instanceof com.codex.blacklight.entity.BlackwatchHelicopter)return;
        if(event.getItemStack().getItem() instanceof com.codex.blacklight.item.ParasiteSyringeItem)return;
        if (!isLocked(event.getEntity())) return;
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
        notifyClient(event.getEntity());
    }

    /** Prototype combat is sent through its own server-timed actions, never vanilla weapons. */
    @SubscribeEvent
    public static void onVanillaAttack(AttackEntityEvent event) {
        if(com.codex.blacklight.military.vehicle.VehicleDamageBridge.ownsAttackEvent(event))return;
        if (!isLocked(event.getEntity())) return;
        event.setCanceled(true);
        notifyClient(event.getEntity());
    }

    /** Server-side backstop for packets or other mods that bypass normal interaction events. */
    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (isLocked(event.getPlayer())&&!com.codex.blacklight.firearms.FirearmBlockDamage.ownsBreakEvent(event)&&!com.codex.blacklight.combat.BulletdiveTerrain.ownsBreakEvent(event)&&!com.codex.blacklight.combat.BlockThrowSystem.ownsBreakEvent(event)
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
