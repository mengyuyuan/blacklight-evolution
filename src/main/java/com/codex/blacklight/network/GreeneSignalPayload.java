package com.codex.blacklight.network;

import net.minecraft.network.FriendlyByteBuf;
import com.codex.blacklight.forge.codec.StreamCodec;
import com.codex.blacklight.forge.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Position of the nearest seeded Greene den while tracking it. */
public record GreeneSignalPayload(net.minecraft.core.BlockPos site) implements CustomPacketPayload {
    public static final Type<GreeneSignalPayload> TYPE=new Type<>(new ResourceLocation("blacklight","greene_signal"));
    public static final StreamCodec<FriendlyByteBuf,GreeneSignalPayload> STREAM_CODEC=StreamCodec.of(
            (buf, signal) -> buf.writeBlockPos(signal.site()),
            buf -> new GreeneSignalPayload(buf.readBlockPos()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
