package com.codex.blacklight.firearms;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="blacklight",value=net.minecraftforge.api.distmarker.Dist.CLIENT,bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class FirearmsClient {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void renderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(com.codex.blacklight.registry.ModEntities.MILITARY_ORDNANCE.get(),com.codex.blacklight.client.renderer.OrdnanceRenderer::new);}
}
