package com.codex.blacklight.network;

import net.minecraft.network.FriendlyByteBuf;
import com.codex.blacklight.forge.codec.StreamCodec;
import com.codex.blacklight.forge.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A single press; action and target are selected exclusively by the server. */
public record ContextActionPayload() implements CustomPacketPayload {
    public static final Type<ContextActionPayload> TYPE=new Type<>(new ResourceLocation("blacklight","context_action"));
    public static final StreamCodec<FriendlyByteBuf,ContextActionPayload> STREAM_CODEC=new StreamCodec<>() {
        public ContextActionPayload decode(FriendlyByteBuf buffer){return new ContextActionPayload();}
        public void encode(FriendlyByteBuf buffer,ContextActionPayload payload){}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
