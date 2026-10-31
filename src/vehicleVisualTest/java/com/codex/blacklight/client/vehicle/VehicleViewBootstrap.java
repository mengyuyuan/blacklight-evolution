package com.codex.blacklight.client.vehicle;

@net.neoforged.fml.common.EventBusSubscriber(modid="blacklight",value=net.neoforged.api.distmarker.Dist.CLIENT,bus=net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public final class VehicleViewBootstrap {
    @net.neoforged.bus.api.SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event){event.enqueueWork(VehicleViewSmoke::init);}
    private VehicleViewBootstrap(){}
}
