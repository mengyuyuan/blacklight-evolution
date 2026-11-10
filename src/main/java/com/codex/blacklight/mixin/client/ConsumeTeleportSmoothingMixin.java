package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.ClientConsumeActor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A server grapple placement that matches the local prediction keeps its interpolation. */
@Mixin(ClientPacketListener.class)
public abstract class ConsumeTeleportSmoothingMixin {
    @Unique private Vec3 blacklight$position,blacklight$old;
    @Unique private boolean blacklight$captured;

    @Inject(method="handleMovePlayer",at=@At("HEAD"))
    private void blacklight$capture(ClientboundPlayerPositionPacket packet,CallbackInfo ci){
        var mc=Minecraft.getInstance();var player=mc.player;
        blacklight$captured=player!=null&&mc.isSameThread()&&ClientConsumeActor.predicting();
        if(!blacklight$captured)return;
        blacklight$position=player.position();
        blacklight$old=new Vec3(player.xo,player.yo,player.zo);
    }

    @Inject(method="handleMovePlayer",at=@At("RETURN"))
    private void blacklight$keepPrediction(ClientboundPlayerPositionPacket packet,CallbackInfo ci){
        if(!blacklight$captured)return;
        blacklight$captured=false;
        var player=Minecraft.getInstance().player;
        if(player==null||!ClientConsumeActor.accept(player.position()))return;
        // Vanilla acknowledged the teleport already; only the rendered root keeps the prediction.
        player.setPos(blacklight$position.x,blacklight$position.y,blacklight$position.z);
        player.xo=blacklight$old.x;player.yo=blacklight$old.y;player.zo=blacklight$old.z;
        player.xOld=blacklight$old.x;player.yOld=blacklight$old.y;player.zOld=blacklight$old.z;
    }
}