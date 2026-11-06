package com.codex.blacklight.client;

import com.codex.blacklight.power.PrototypeMetabolism;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

@EventBusSubscriber(modid = "blacklight", value = Dist.CLIENT)
public final class PrototypeFoodHud {
    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                && PrototypeMetabolism.hungerSuspended(Minecraft.getInstance().player)) event.setCanceled(true);
    }
}
