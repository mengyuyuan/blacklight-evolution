package com.codex.blacklight.client;

import com.codex.blacklight.power.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid="blacklight", value=Dist.CLIENT)
public final class AdaptivePassiveHud {
    private AdaptivePassiveHud() {}
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        if (!com.codex.blacklight.config.PassiveHudConfig.SHOW_STACKS.get()) return;
        var mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || !p.isAlive() || p.isSpectator() || mc.options.hideGui || mc.screen != null) return;
        var state = AdaptivePassiveSystem.state(p);
        var g = event.getGuiGraphics(); int y = mc.getWindow().getGuiScaledHeight() - 65;
        if (AdaptivePassiveSystem.active(p, AdaptivePassiveState.ADAPTATION)) {
            long percent = Math.round(100 * (1 - AdaptivePassiveRules.remainingFraction(state.adaptation(), p.getHealth(), p.getMaxHealth(), AdaptivePassiveSystem.percentBased(p))));
            g.drawString(mc.font, Component.translatable("hud.blacklight.adaptation", state.adaptation(), percent), 8, y, 0xFF9AD9EC, true); y -= 12;
        }
        if (AdaptivePassiveSystem.active(p, AdaptivePassiveState.REGENERATION))
            g.drawString(mc.font, Component.translatable("hud.blacklight.tissue_regeneration", state.regeneration()), 8, y, 0xFFEB9C9D, true);
    }
}
