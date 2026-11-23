package com.codex.blacklight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GoddessStrugglePayload(int entityId,int sequence,int controls) implements CustomPacketPayload {
    public static final Type<GoddessStrugglePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("blacklight","goddess_struggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf,GoddessStrugglePayload> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_INT,GoddessStrugglePayload::entityId,ByteBufCodecs.VAR_INT,GoddessStrugglePayload::sequence,ByteBufCodecs.VAR_INT,GoddessStrugglePayload::controls,GoddessStrugglePayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
