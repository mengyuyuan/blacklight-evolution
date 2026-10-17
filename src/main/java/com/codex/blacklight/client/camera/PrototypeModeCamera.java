package com.codex.blacklight.client.camera;

import com.codex.blacklight.mode.PrototypeModeSystem;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class PrototypeModeCamera {
    private static final ModePerspective perspective=new ModePerspective();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();var p=mc.player;
        if(HydraCinematicCamera.active())return;
        var before=mc.options.getCameraType();
        var after=perspective.update(before,p!=null&&PrototypeModeSystem.isActive(p),mc.options.keyTogglePerspective.isDown());
        if(after!=before)mc.options.setCameraType(after);
    }
}
