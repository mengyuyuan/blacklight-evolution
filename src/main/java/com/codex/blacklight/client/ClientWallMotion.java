package com.codex.blacklight.client;

import com.codex.blacklight.movement.WallTraversal;
import com.codex.blacklight.network.WallMotionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import java.util.HashMap;
import java.util.Map;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;

/** Explicit wall modes for observers; the controlling client predicts the same state machine. */
@EventBusSubscriber(modid="blacklight", value=Dist.CLIENT)
public final class ClientWallMotion {
    private record Entry(WallTraversal.Mode mode, long expires) { }
    private static final Map<Integer,Entry> MODES=new HashMap<>();
    private static ClientLevel level;
    private ClientWallMotion() { }
    private static void checkLevel() {
        if(level!=Minecraft.getInstance().level) { MODES.clear();level=Minecraft.getInstance().level; }
    }
    @SubscribeEvent public static void tick(ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        checkLevel();
        if(level!=null)MODES.entrySet().removeIf(entry -> level.getGameTime()>entry.getValue().expires());
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        MODES.clear();level=null;
    }
    public static void accept(WallMotionPayload payload) {
        checkLevel();if(level==null)return;
        var entity=level.getEntity(payload.entityId());
        if(entity instanceof AbstractClientPlayer player && player!=Minecraft.getInstance().player)
            com.codex.blacklight.movement.WallPoseState.set(player,payload.wallMode(),payload.normalX(),payload.normalZ());
        if(payload.wallMode()==WallTraversal.Mode.NONE)MODES.remove(payload.entityId());
        else MODES.put(payload.entityId(),new Entry(payload.wallMode(),level.getGameTime()+40));
    }
    public static WallTraversal.Mode modeFor(AbstractClientPlayer player) {
        checkLevel();
        if(player==Minecraft.getInstance().player)return ClientMovementEvents.wallMode();
        Entry entry=MODES.get(player.getId());
        if(entry==null || level==null || level.getGameTime()>entry.expires())return WallTraversal.Mode.NONE;
        return entry.mode();
    }
}
