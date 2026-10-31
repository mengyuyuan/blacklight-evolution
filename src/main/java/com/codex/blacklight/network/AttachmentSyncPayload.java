package com.codex.blacklight.network;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.forge.CustomPacketPayload;
import com.codex.blacklight.forge.ForgePayloadRegistrar;
import com.codex.blacklight.forge.codec.StreamCodec;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server authority only; UUID and dimension prevent a reused entity id receiving stale state. */
public record AttachmentSyncPayload(int entityId, UUID uuid, ResourceLocation dimension, Map<String, byte[]> fields)
        implements CustomPacketPayload {
    public static final int MAX_FIELDS=64;
    public static final Type<AttachmentSyncPayload> TYPE = new Type<>(new ResourceLocation(BlacklightNeoMod.MOD_ID, "entity_state"));
    public static final StreamCodec<FriendlyByteBuf, AttachmentSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                if(packet.fields.size()>MAX_FIELDS)throw new EncoderException("Invalid Blacklight attachment count");
                buffer.writeVarInt(packet.entityId); buffer.writeUUID(packet.uuid);
                buffer.writeResourceLocation(packet.dimension); buffer.writeVarInt(packet.fields.size());
                packet.fields.forEach((name, value) -> { buffer.writeUtf(name, 64); buffer.writeByteArray(value); });
            }, buffer -> {
                int id = buffer.readVarInt(); UUID uuid = buffer.readUUID();
                ResourceLocation dimension = buffer.readResourceLocation();
                int count = buffer.readVarInt();
                if (count < 0 || count > MAX_FIELDS) throw new DecoderException("Invalid Blacklight attachment count");
                Map<String, byte[]> fields = new LinkedHashMap<>();
                for (int i = 0; i < count; i++) {
                    String name = buffer.readUtf(64);
                    byte[] value = buffer.readByteArray(ForgePayloadRegistrar.MAX_PACKET_BYTES);
                    if (fields.putIfAbsent(name, value) != null) throw new DecoderException("Duplicate Blacklight attachment");
                }
                return new AttachmentSyncPayload(id, uuid, dimension, Map.copyOf(fields));
            });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
