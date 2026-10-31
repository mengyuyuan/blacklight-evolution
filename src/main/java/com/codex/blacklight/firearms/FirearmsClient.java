package com.codex.blacklight.firearms;
@net.neoforged.fml.common.EventBusSubscriber(modid="blacklight",value=net.neoforged.api.distmarker.Dist.CLIENT,bus=net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public final class FirearmsClient {
    @net.neoforged.bus.api.SubscribeEvent
    public static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(com.codex.blacklight.registry.ModEntities.MILITARY_ORDNANCE.get(),com.codex.blacklight.client.renderer.OrdnanceRenderer::new);}
}
