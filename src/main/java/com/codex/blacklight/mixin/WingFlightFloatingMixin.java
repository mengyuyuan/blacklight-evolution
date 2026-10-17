package com.codex.blacklight.mixin;
import com.codex.blacklight.movement.WingFlightSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class WingFlightFloatingMixin {
    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;
    @Shadow private int receivedMovePacketCount;
    @Shadow private int knownMovePacketCount;
    @Shadow private net.minecraft.world.phys.Vec3 awaitingPositionFromClient;
    @Unique private net.minecraft.world.phys.Vec3 blacklight$beforeFlightPacket;
    @Inject(method="handleMovePlayer",at=@At("HEAD"))
    private void blacklight$rememberAcceptedMotion(ServerboundMovePlayerPacket packet,CallbackInfo ci){
        if(player.server.isSameThread())blacklight$beforeFlightPacket=packet.hasPosition()&&WingFlightSystem.ownsTravel(player)?player.position():null;
    }
    @Inject(method="handleMovePlayer",at=@At("RETURN"))
    private void blacklight$reconcileFlightMotion(ServerboundMovePlayerPacket packet,CallbackInfo ci){
        if(!player.server.isSameThread()||blacklight$beforeFlightPacket==null)return;
        var before=blacklight$beforeFlightPacket;blacklight$beforeFlightPacket=null;
        if(awaitingPositionFromClient!=null||!WingFlightSystem.ownsTravel(player))return;
        // Reconcile to collision-validated displacement, including a stop at a wall or
        // client chunk boundary. Never keep predicting acceleration while the actor is stuck.
        var actual=player.position().subtract(before);
        double cap=com.codex.blacklight.movement.WingFlightTuning.tier(WingFlightSystem.tier(player)).boostSpeed();
        player.setDeltaMovement(actual.length()>cap?actual.normalize().scale(cap):actual);
        WingFlightSystem.acceptedMotion(player);
    }
    @ModifyConstant(method="handleMovePlayer",constant=@Constant(floatValue=100F))
    private float blacklight$flightPacketBudget(float vanilla){
        if(!player.server.isSameThread()||!WingFlightSystem.ownsTravel(player))return vanilla;
        return com.codex.blacklight.movement.WingFlightTuning.packetAllowance(vanilla,player.getDeltaMovement().length(),
                WingFlightSystem.tier(player),receivedMovePacketCount-knownMovePacketCount);
    }
    @Inject(method="handleMovePlayer",at=@At("TAIL"))
    private void blacklight$authorizedFlight(ServerboundMovePlayerPacket packet,CallbackInfo ci){
        if(player.server.isSameThread()&&WingFlightSystem.flying(player))clientIsFloating=false;
    }
}
