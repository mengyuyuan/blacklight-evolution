package com.codex.blacklight.client.audio;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.level.LevelEvent;

/** Release channels immediately when the client world detaches. */
@Mod.EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class GreeneProjectileAudioEvents {
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel().isClientSide())GreeneProjectileAudio.clear();}
    private GreeneProjectileAudioEvents(){}
}
