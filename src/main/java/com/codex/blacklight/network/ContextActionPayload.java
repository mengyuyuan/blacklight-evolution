package com.codex.blacklight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A single press; action and target are selected exclusively by the server. */
public record ContextActionPayload() implements CustomPacketPayload {
    public static final Type<ContextActionPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("blacklight","context_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ContextActionPayload> STREAM_CODEC=new StreamCodec<>() {
        public ContextActionPayload decode(RegistryFriendlyByteBuf buffer){return new ContextActionPayload();}
        public void encode(RegistryFriendlyByteBuf buffer,ContextActionPayload payload){}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
