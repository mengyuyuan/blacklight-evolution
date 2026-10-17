package com.codex.blacklight.client.camera;

import com.codex.blacklight.mode.PrototypeModeSystem;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ClientTickEvent;

@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class PrototypeModeCamera {
    private static final ModePerspective perspective=new ModePerspective();
    @SubscribeEvent public static void tick(ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();var p=mc.player;
        if(HydraCinematicCamera.active())return;
        var before=mc.options.getCameraType();
        var after=perspective.update(before,p!=null&&PrototypeModeSystem.isActive(p),mc.options.keyTogglePerspective.isDown());
        if(after!=before)mc.options.setCameraType(after);
    }
}
