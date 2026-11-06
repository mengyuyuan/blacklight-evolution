package com.codex.blacklight.client;

import com.codex.blacklight.power.PrototypeMetabolism;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = "blacklight", value = Dist.CLIENT)
public final class PrototypeFoodHud {
    @SubscribeEvent
    public static void render(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.FOOD_LEVEL)
                && PrototypeMetabolism.hungerSuspended(Minecraft.getInstance().player)) event.setCanceled(true);
    }
}
