package com.codex.blacklight.network;

import net.minecraft.network.FriendlyByteBuf;
import com.codex.blacklight.forge.codec.StreamCodec;
import com.codex.blacklight.forge.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DisguiseSelectPayload(int revision,int index) implements CustomPacketPayload {
    public static final Type<DisguiseSelectPayload> TYPE=new Type<>(new ResourceLocation("blacklight","disguise_select"));
    public static final StreamCodec<FriendlyByteBuf,DisguiseSelectPayload> STREAM_CODEC=new StreamCodec<>() {
        @Override public DisguiseSelectPayload decode(FriendlyByteBuf buf){return new DisguiseSelectPayload(buf.readVarInt(),buf.readVarInt());}
        @Override public void encode(FriendlyByteBuf buf,DisguiseSelectPayload p){buf.writeVarInt(p.revision);buf.writeVarInt(p.index);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
