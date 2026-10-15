package com.codex.passives;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
@EventBusSubscriber(modid=PassivesMod.ID, value=Dist.CLIENT, bus=EventBusSubscriber.Bus.MOD)
public final class PassiveClientRegistration {
    @SubscribeEvent public static void setup(FMLClientSetupEvent e) {
        net.neoforged.fml.ModList.get().getModContainerById(PassivesMod.ID).orElseThrow()
            .registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class, (container, parent) -> new PassiveSettingsScreen(parent));
    }
}
