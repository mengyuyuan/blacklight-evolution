package com.codex.blacklight.power;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Server-authoritative upgrade purchases driven by the upgrade panel. */
public final class UpgradeSystem {
    private UpgradeSystem() {
    }

    public static void purchase(ServerPlayer player, String upgradeId) {
        if(RicochetRules.ID.equals(upgradeId)){ProjectileDefenseSystem.purchase(player);return;}
        var growth=GrowthUpgrade.byId(upgradeId);
        if(growth!=null){GrowthSystem.purchase(player,growth);return;}
        BlacklightState state = com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE);
        var combat = CombatSkill.byId(upgradeId);
        if (combat != null) {
            if (!state.buyCombatSkill(combat)) { deny(player, upgradeId, combat.epCost()); return; }
            confirm(player, state, upgradeId); return;
        }

        var form = PowerForm.byId(upgradeId);
        if (PowerFormSystem.supported(form) && form != PowerForm.NONE) {
            if (!state.buy(form)) { deny(player, upgradeId, form.epCost()); return; }
            confirm(player, state, upgradeId); return;
        }
        ClawUpgrade claw = ClawUpgrade.byId(upgradeId);
        if (claw != null) {
            if (!state.buyClawUpgrade(claw)) {
                deny(player, upgradeId, claw.epCost());
                return;
            }
            confirm(player, state, upgradeId);
            return;
        }
        MovementUpgrade movement = MovementUpgrade.byId(upgradeId);
        if (movement == null) {
            BlacklightNeoMod.LOGGER.warn("Unknown upgrade id {} from {}",
                    upgradeId, player.getGameProfile().getName());
            return;
        }
        if (!state.buyMovementUpgrade(movement)) {
            deny(player, upgradeId, movement.epCost());
            return;
        }
        confirm(player, state, upgradeId);
    }

    private static void confirm(ServerPlayer player, BlacklightState state, String upgradeId) {
        com.codex.blacklight.registry.ModAttachments.setData(player, ModAttachments.PLAYER_STATE, state);
        com.codex.blacklight.registry.ModAttachments.syncData(player, ModAttachments.PLAYER_STATE);
        player.displayClientMessage(Component.translatable(
                "message.blacklight.upgrade_purchased",
                Component.translatable("upgrade.blacklight." + upgradeId)), true);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 0.65F, 0.94F);
        BlacklightNeoMod.LOGGER.debug("Upgrade {} purchased by {} (EP left {})",
                upgradeId, player.getGameProfile().getName(), state.getEvolutionPoints());
    }

    private static void deny(ServerPlayer player, String upgradeId, int epCost) {
        player.displayClientMessage(Component.translatable(
                "message.blacklight.upgrade_denied",
                Component.translatable("upgrade.blacklight." + upgradeId), epCost), true);
    }
}
