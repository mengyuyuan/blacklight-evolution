package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.mode.PrototypeModeSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Own locomotion follows the current phase; release clocks across mode and world changes. */
@EventBusSubscriber(modid=BlacklightNeoMod.MOD_ID,value=Dist.CLIENT)
public final class ClientClawMotionEvents {
    private static final Set<AbstractClientPlayer> OWNED=Collections.newSetFromMap(new WeakHashMap<>());
    private ClientClawMotionEvents() {}
    @SubscribeEvent public static void onClientTick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        OWNED.removeIf(player->{
            boolean remove=player.isRemoved()||player.level()!=mc.level||!PrototypeModeSystem.isActive(player);
            if(remove)PlayerMotionController.clear(player);
            return remove;
        });
        if(mc.level==null){ClientTargetLock.clear();return;}
        for(var player:mc.level.players())if(PrototypeModeSystem.isActive(player))OWNED.add(player);
        if(mc.player!=null&&!ClientTargetLock.active())ClientTargetLock.clear();
    }
}
