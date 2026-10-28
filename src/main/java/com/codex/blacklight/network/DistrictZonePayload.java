package com.codex.blacklight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Read-only server snapshot. Clients cannot submit threat or progression. */
public record DistrictZonePayload(ResourceLocation dimension,boolean active,int zone,int stage) implements CustomPacketPayload {
    public static final Type<DistrictZonePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("blacklight","district_zone"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DistrictZonePayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->{b.writeResourceLocation(v.dimension);b.writeBoolean(v.active);b.writeVarInt(v.zone);b.writeVarInt(v.stage);},
            b->new DistrictZonePayload(b.readResourceLocation(),b.readBoolean(),Math.clamp(b.readVarInt(),0,2),Math.clamp(b.readVarInt(),0,4)));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
