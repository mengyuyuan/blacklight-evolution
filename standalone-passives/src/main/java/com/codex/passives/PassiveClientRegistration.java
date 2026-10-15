package com.codex.passives;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
@EventBusSubscriber(modid=PassivesMod.ID, value=Dist.CLIENT, bus=EventBusSubscriber.Bus.MOD)
public final class PassiveClientRegistration {
    @SubscribeEvent public static void setup(FMLClientSetupEvent e) {
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(
            net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new PassiveSettingsScreen(parent)));
    }
}
