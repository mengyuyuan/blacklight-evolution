package com.codex.blacklight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Position of the nearest seeded Greene den while tracking it. */
public record GreeneSignalPayload(net.minecraft.core.BlockPos site) implements CustomPacketPayload {
    public static final Type<GreeneSignalPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("blacklight","greene_signal"));
    public static final StreamCodec<RegistryFriendlyByteBuf,GreeneSignalPayload> STREAM_CODEC=StreamCodec.of(
            (buf, signal) -> buf.writeBlockPos(signal.site()),
            buf -> new GreeneSignalPayload(buf.readBlockPos()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
