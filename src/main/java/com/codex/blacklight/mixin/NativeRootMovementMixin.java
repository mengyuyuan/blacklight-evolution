package com.codex.blacklight.mixin;

import com.codex.blacklight.animation.NativeRootMotion;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A committed ground step cannot be rolled back by an older client absolute-position packet. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class NativeRootMovementMixin {
    @Shadow public ServerPlayer player;
    @Shadow private net.minecraft.world.phys.Vec3 awaitingPositionFromClient;

    @Unique private boolean blacklight$ownsRootPosition(){
        if(player.isRemoved()||!player.isAlive())return false;
        return NativeRootMotion.controlsGroundRoot(player)
                ||com.codex.blacklight.city.HydraTransport.active(player)
                ||com.codex.blacklight.combat.ConsumeSystem.committed(player)
                ||com.codex.blacklight.power.BiomassSystem.state(player).immobilized()
                ||com.codex.blacklight.combat.DevastatorSystem.controlsLandingPosition(player);
    }

    @Inject(method="resetPosition",at=@At("RETURN"))
    private void blacklight$reconcilePendingCorrection(CallbackInfo ci){
        // resetPosition updates vanilla's movement baselines, but leaves the
        // outstanding teleport acknowledgement pointing at the old air position.
        // Keep its ID and vanilla acknowledgement handling; only reconcile its
        // position when our server-owned action has committed a collision result.
        if(player.server.isSameThread()&&awaitingPositionFromClient!=null&&blacklight$ownsRootPosition())
            awaitingPositionFromClient=player.position();
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void blacklight$serverRootPosition(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        // Vanilla schedules this handler onto the server thread; never mutate entities on Netty.
        if (!player.server.isSameThread() || !blacklight$ownsRootPosition()) return;
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
