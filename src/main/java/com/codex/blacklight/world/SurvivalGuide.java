package com.codex.blacklight.world;

import com.codex.blacklight.power.GrowthSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** The root advancement persists the one-time introduction across sessions/death. */
public final class SurvivalGuide {
    private SurvivalGuide() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var root = player.server.getAdvancements().getAdvancement(new ResourceLocation("blacklight", "root"));
        if (root == null || player.getAdvancements().getOrStartProgress(root).isDone()) return;
        GrowthSystem.award(player, "root");
    }
}
