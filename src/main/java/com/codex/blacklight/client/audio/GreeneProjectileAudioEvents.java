package com.codex.blacklight.client.audio;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Release channels immediately when the client world detaches. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class GreeneProjectileAudioEvents {
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel().isClientSide())GreeneProjectileAudio.clear();}
    private GreeneProjectileAudioEvents(){}
}
