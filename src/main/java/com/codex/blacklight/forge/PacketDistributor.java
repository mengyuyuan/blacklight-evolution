package com.codex.blacklight.forge;

import com.codex.blacklight.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;

/** Project call sites share these three delivery operations; all use Forge SimpleChannel. */
public final class PacketDistributor {
    private PacketDistributor() {}

    public static void sendToServer(CustomPacketPayload payload) {
        ForgePayloadRegistrar.requireDirection(payload, NetworkDirection.PLAY_TO_SERVER);
        ModNetwork.channel().sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ForgePayloadRegistrar.requireDirection(payload, NetworkDirection.PLAY_TO_CLIENT);
        ModNetwork.channel().send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToPlayersNear(net.minecraft.server.level.ServerLevel level,ServerPlayer excluded,double x,double y,double z,double radius,CustomPacketPayload payload){
        ForgePayloadRegistrar.requireDirection(payload,NetworkDirection.PLAY_TO_CLIENT);
        if(!Double.isFinite(radius)||radius<0||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return;
        double squared=radius*radius;
        for(var player:level.players())if(player!=excluded&&player.distanceToSqr(x,y,z)<=squared)sendToPlayer(player,payload);
    }

    public static void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) {
        ForgePayloadRegistrar.requireDirection(payload, NetworkDirection.PLAY_TO_CLIENT);
        if (entity.level().isClientSide) return;
        ModNetwork.channel().send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), payload);
    }
}
