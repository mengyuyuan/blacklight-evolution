package com.codex.blacklight.mode;

import com.codex.blacklight.combat.ClawCombatSystem;
import com.codex.blacklight.animation.PlayerActions;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server authority for switching the player between vanilla and Prototype rules. */
public final class PrototypeModeSystem {
    private PrototypeModeSystem() {
    }

    public static void handle(ServerPlayer player, boolean enabled) {
        if(com.codex.blacklight.power.BiomassSystem.active(player))return;
        BlacklightState state = player.getData(ModAttachments.PLAYER_STATE);
        if (!state.isInfected()) {
            player.displayClientMessage(Component.translatable(
                    "message.blacklight.prototype_mode_requires_infection"), true);
            return;
        }
        if (state.isPrototypeMode() == enabled) return;

        com.codex.blacklight.combat.WeaponCombatSystem.cancel(player);
        state.setPrototypeMode(enabled);
        player.setData(ModAttachments.PLAYER_STATE, state);
        player.syncData(ModAttachments.PLAYER_STATE);
        player.getData(ModAttachments.MOVEMENT_STATE).reset();
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
        BlacklightState state = player.getData(ModAttachments.PLAYER_STATE);
        return state.isInfected() && state.isPrototypeMode();
    }

    /** Keeps the presentation engine aligned so B is the only authoritative mode switch. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BlacklightState state = player.getData(ModAttachments.PLAYER_STATE);
        synchronizePresentationMode(player, state.isInfected() && state.isPrototypeMode());
    }

    private static void synchronizePresentationMode(ServerPlayer player, boolean prototypeMode) {
        PlayerActions.get().synchronizeMode(player, prototypeMode);
    }
}
