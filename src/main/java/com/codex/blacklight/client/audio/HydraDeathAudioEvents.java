package com.codex.blacklight.client.audio;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;

@Mod.EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class HydraDeathAudioEvents {
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.END)HydraDeathAudio.tick();}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel().isClientSide())HydraDeathAudio.clear();}
    private HydraDeathAudioEvents(){}
}
