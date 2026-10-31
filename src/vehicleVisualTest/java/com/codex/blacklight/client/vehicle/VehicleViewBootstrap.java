package com.codex.blacklight.client.vehicle;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="blacklight",value=net.minecraftforge.api.distmarker.Dist.CLIENT,bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class VehicleViewBootstrap {
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event){event.enqueueWork(VehicleViewSmoke::init);}
    private VehicleViewBootstrap(){}
}
