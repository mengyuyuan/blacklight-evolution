package com.codex.blacklight.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AttachmentSyncPayloadTest {
    @Test void expandedLoginSnapshotRoundTripsWithoutDisconnect() {
        var fields=new LinkedHashMap<String,byte[]>();
        com.codex.blacklight.registry.ModAttachments.types().forEach((name,type)->{
            if(type.syncCodec()!=null)fields.put(name,new byte[]{(byte)fields.size()});
        });
        assertTrue(fields.size()>32,"the real current login schema must exercise the old 32-field disconnect");
        var packet=new AttachmentSyncPayload(7,UUID.randomUUID(),new ResourceLocation("minecraft","overworld"),fields);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            AttachmentSyncPayload.STREAM_CODEC.encode(buffer,packet);
            var decoded=AttachmentSyncPayload.STREAM_CODEC.decode(buffer);
            assertEquals(packet.uuid(),decoded.uuid());assertEquals(packet.entityId(),decoded.entityId());
            assertEquals(fields.keySet(),decoded.fields().keySet());
            fields.forEach((key,value)->assertArrayEquals(value,decoded.fields().get(key)));
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }
    @Test void oversizedSnapshotStillRejected() {
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(7);buffer.writeUUID(UUID.randomUUID());
            buffer.writeResourceLocation(new ResourceLocation("minecraft","overworld"));buffer.writeVarInt(AttachmentSyncPayload.MAX_FIELDS+1);
            assertThrows(DecoderException.class,()->AttachmentSyncPayload.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
}
