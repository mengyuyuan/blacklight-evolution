package com.codex.blacklight.client;

import com.codex.blacklight.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid="blacklight", value=Dist.CLIENT, bus=EventBusSubscriber.Bus.MOD)
public final class PassiveEffectDisplay {
    private PassiveEffectDisplay() {}

    @SubscribeEvent
    public static void register(RegisterClientExtensionsEvent event) {
        event.registerMobEffect(new IClientMobEffectExtensions() {
            @Override public boolean isVisibleInInventory(MobEffectInstance instance) { return false; }
            @Override public boolean isVisibleInGui(MobEffectInstance instance) { return false; }
        }, ModEffects.ADAPTATION, ModEffects.TISSUE_REGENERATION);
    }
}
