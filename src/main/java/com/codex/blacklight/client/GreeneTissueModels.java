package com.codex.blacklight.client;

import com.codex.blacklight.item.GreeneTissueItem;
import com.codex.blacklight.registry.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid="blacklight", value=Dist.CLIENT, bus=EventBusSubscriber.Bus.MOD)
public final class GreeneTissueModels {
    private GreeneTissueModels() {}

    @SubscribeEvent public static void register(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            var open = ResourceLocation.fromNamespaceAndPath("blacklight", "tissue_open");
            for (var item : java.util.List.of(ModItems.GREENE_TISSUE.get(), ModItems.MOTHER_CORE.get())) {
                ItemProperties.register(item, open, (stack, level, holder, seed) -> {
                    if (holder == null || !holder.isUsingItem() || holder.getUseItem() != stack) return 0;
                    return GreeneTissueItem.opening(holder.getTicksUsingItem()) * .5F;
                });
            }
        });
    }
}
