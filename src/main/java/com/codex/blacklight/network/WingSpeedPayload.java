package com.codex.blacklight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Personal speed ceiling; cannot raise an upgrade's or the loaded-chunk safety limit. */
public record WingSpeedPayload(int percent) implements CustomPacketPayload {
    public static final Type<WingSpeedPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("blacklight","wing_speed"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WingSpeedPayload> STREAM_CODEC=StreamCodec.of(
            (buffer,value)->buffer.writeVarInt(value.percent),buffer->new WingSpeedPayload(buffer.readVarInt()));
    public static boolean valid(int percent){return percent>=10&&percent<=100;}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
