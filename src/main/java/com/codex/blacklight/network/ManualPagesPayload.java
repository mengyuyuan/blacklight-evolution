package com.codex.blacklight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record ManualPagesPayload(boolean handbook, List<String> lines) implements CustomPacketPayload {
    public static final int MAX_LINE = 1536;
    public static final Type<ManualPagesPayload> TYPE = new Type<>(com.codex.blacklight.item.SurvivalManualItem.BOOK);
    public static final StreamCodec<RegistryFriendlyByteBuf,ManualPagesPayload> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> { buf.writeBoolean(msg.handbook); buf.writeCollection(msg.lines,(b,s) -> b.writeUtf(s,MAX_LINE)); },
            buf -> new ManualPagesPayload(buf.readBoolean(),buf.readCollection(
                    n -> { if(n<0 || n>40) throw new IllegalArgumentException("Invalid note count"); return new java.util.ArrayList<>(n); },
                    b -> b.readUtf(MAX_LINE))));
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
}
