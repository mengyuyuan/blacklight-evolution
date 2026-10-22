package com.codex.blacklight.mixin;

import com.codex.blacklight.animation.NativeRootMotion;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A committed ground step cannot be rolled back by an older client absolute-position packet. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class NativeRootMovementMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void blacklight$serverRootPosition(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        // Vanilla schedules this handler onto the server thread; never mutate entities on Netty.
        if (!player.server.isSameThread() || !(NativeRootMotion.controlsGroundRoot(player)
                ||com.codex.blacklight.city.HydraTransport.active(player)
                ||com.codex.blacklight.combat.ConsumeSystem.committed(player)
                ||com.codex.blacklight.power.BiomassSystem.state(player).immobilized()
                ||com.codex.blacklight.combat.DevastatorSystem.controlsLandingPosition(player))) return;
        float yaw = packet.getYRot(player.getYRot()), pitch = packet.getXRot(player.getXRot());
        // Preserve vanilla's malformed-packet disconnect path.
        if (!Double.isFinite(packet.getX(0)) || !Double.isFinite(packet.getY(0))
                || !Double.isFinite(packet.getZ(0)) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) return;
        player.setYRot(Mth.wrapDegrees(yaw));
        player.setXRot(Math.clamp(Mth.wrapDegrees(pitch), -90, 90));
        player.setYHeadRot(player.getYRot());
        ci.cancel();
    }
}
