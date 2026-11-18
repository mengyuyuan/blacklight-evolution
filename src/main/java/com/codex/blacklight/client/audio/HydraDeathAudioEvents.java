package com.codex.blacklight.client.audio;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class HydraDeathAudioEvents {
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){HydraDeathAudio.tick();}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel().isClientSide())HydraDeathAudio.clear();}
    private HydraDeathAudioEvents(){}
}
