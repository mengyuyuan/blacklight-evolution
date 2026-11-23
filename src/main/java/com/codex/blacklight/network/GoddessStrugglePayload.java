package com.codex.blacklight.network;

import net.minecraft.network.FriendlyByteBuf;
import com.codex.blacklight.forge.codec.ByteBufCodecs;
import com.codex.blacklight.forge.codec.StreamCodec;
import com.codex.blacklight.forge.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GoddessStrugglePayload(int entityId,int sequence,int controls) implements CustomPacketPayload {
    public static final Type<GoddessStrugglePayload> TYPE=new Type<>(new ResourceLocation("blacklight","goddess_struggle"));
    public static final StreamCodec<FriendlyByteBuf,GoddessStrugglePayload> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_INT,GoddessStrugglePayload::entityId,ByteBufCodecs.VAR_INT,GoddessStrugglePayload::sequence,ByteBufCodecs.VAR_INT,GoddessStrugglePayload::controls,GoddessStrugglePayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
