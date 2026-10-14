package com.codex.blacklight.mode;

import com.codex.blacklight.combat.ClawCombatSystem;
import com.codex.blacklight.animation.PlayerActions;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;

/** Server authority for switching the player between vanilla and Prototype rules. */
public final class PrototypeModeSystem {
    private PrototypeModeSystem() {
    }

    public static void handle(ServerPlayer player, boolean enabled) {
        if(com.codex.blacklight.power.BiomassSystem.active(player))return;
        BlacklightState state = com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE);
        if (!state.isInfected()) {
            player.displayClientMessage(Component.translatable(
                    "message.blacklight.prototype_mode_requires_infection"), true);
            return;
        }
        if (state.isPrototypeMode() == enabled) return;

        com.codex.blacklight.combat.WeaponCombatSystem.cancel(player);
        state.setPrototypeMode(enabled);
        com.codex.blacklight.registry.ModAttachments.setData(player, ModAttachments.PLAYER_STATE, state);
        com.codex.blacklight.registry.ModAttachments.syncData(player, ModAttachments.PLAYER_STATE);
        com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.MOVEMENT_STATE).reset();
        player.stopUsingItem();
        if (enabled) {
            player.closeContainer();
        } else {
            var velocity = player.getDeltaMovement();
            var horizontal = velocity.multiply(1.0D, 0.0D, 1.0D);
            if (horizontal.length() > 0.35D) horizontal = horizontal.normalize().scale(0.35D);
            player.setDeltaMovement(horizontal.x, velocity.y, horizontal.z);
            player.hurtMarked = true;
        }
        synchronizePresentationMode(player, enabled);
        ClawCombatSystem.playPrototypeTransition(player, enabled);
        player.displayClientMessage(Component.translatable(enabled
                ? "message.blacklight.prototype_mode_enabled"
                : "message.blacklight.prototype_mode_disabled"), true);
    }

    public static boolean isActive(net.minecraft.world.entity.player.Player player) {
        if (player == null || !player.isAlive() || !ModAttachments.available(player)) return false;
        BlacklightState state = com.codex.blacklight.registry.ModAttachments.peekData(player, ModAttachments.PLAYER_STATE);
        return state.isInfected() && state.isPrototypeMode();
    }

    /** Keeps the presentation engine aligned so B is the only authoritative mode switch. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent event) {
        if (!com.codex.blacklight.registry.ModAttachments.available(event.player)) { return; }
        if(event.phase != net.minecraftforge.event.TickEvent.Phase.END)return;
        if (!(event.player instanceof ServerPlayer player)) return;
        BlacklightState state = com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE);
        synchronizePresentationMode(player, state.isInfected() && state.isPrototypeMode());
    }

    private static void synchronizePresentationMode(ServerPlayer player, boolean prototypeMode) {
        PlayerActions.get().synchronizeMode(player, prototypeMode);
    }
}
