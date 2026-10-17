package com.codex.blacklight.mixin;

import com.codex.blacklight.city.HydraTransport;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Paired transit is positioned by the server, never by stale client movement packets. */
@Mixin(value=ServerGamePacketListenerImpl.class,priority=1100)
public abstract class HydraServerMovementMixin {
    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;
    @Inject(method="handleMovePlayer",at=@At("HEAD"),cancellable=true)
    private void blacklight$serverOwnedGrip(ServerboundMovePlayerPacket packet,CallbackInfo ci){
        if(!player.server.isSameThread()||!HydraTransport.active(player))return;
        HydraTransport.place(player);player.connection.resetPosition();clientIsFloating=false;ci.cancel();
    }
}
