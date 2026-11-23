package com.codex.blacklight.mode;

import com.codex.blacklight.world.FieldNotes;
import com.codex.blacklight.combat.ClawCombatSystem;
import com.codex.blacklight.disguise.DisguiseSystem;
import com.codex.blacklight.animation.PlayerActions;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.power.PowerFormSystem;
import com.codex.blacklight.power.ParasiteSystem;
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
            FieldNotes.record(player, Component.translatable(
                    "message.blacklight.prototype_mode_requires_infection"));
            return;
        }
        if (!enabled) DisguiseSystem.endForModeExit(player);
        if (state.isPrototypeMode() == enabled) {
            // A client still displaying the old disguise can repeat its exit request.
            if (!enabled) {
                player.syncData(ModAttachments.DISGUISE_MORPH);
                player.syncData(ModAttachments.PLAYER_STATE);
            }
            return;
        }

        com.codex.blacklight.combat.WeaponCombatSystem.cancel(player);
        PowerForm previous=state.getActivePower();
        state.setPrototypeMode(enabled);
        if(enabled && !ParasiteSystem.allows(player,state.getActivePower()))state.activate(PowerForm.CLAWS);
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
        PowerForm weapon=enabled ? state.getActivePower() : previous;
        if(weapon==PowerForm.CLAWS)ClawCombatSystem.playPrototypeTransition(player, enabled);
        else {
            player.getData(ModAttachments.CLAW_COMBAT).reset();
            if(weapon!=PowerForm.NONE)PowerFormSystem.playWeaponMorph(player,weapon,enabled);
        }
        FieldNotes.record(player, Component.translatable(enabled
                ? "message.blacklight.prototype_mode_enabled"
                : "message.blacklight.prototype_mode_disabled"));
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
