package com.codex.blacklight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DisguiseSelectPayload(int revision,int index) implements CustomPacketPayload {
    public static final Type<DisguiseSelectPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("blacklight","disguise_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DisguiseSelectPayload> STREAM_CODEC=new StreamCodec<>() {
        @Override public DisguiseSelectPayload decode(RegistryFriendlyByteBuf buf){return new DisguiseSelectPayload(buf.readVarInt(),buf.readVarInt());}
        @Override public void encode(RegistryFriendlyByteBuf buf,DisguiseSelectPayload p){buf.writeVarInt(p.revision);buf.writeVarInt(p.index);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
