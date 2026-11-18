package com.codex.blacklight.military.aircraft;

import com.codex.blacklight.network.HelicopterControlPayload;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterControlPayloadTest {
    @Test void switchAndFireSurviveTheActualWireCodec(){
        var input=new HelicopterControlPayload(UUID.randomUUID(),UUID.randomUUID(),81,0,0,-10,35,6);
        assertTrue(input.valid());var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{HelicopterControlPayload.STREAM_CODEC.encode(buffer,input);var decoded=HelicopterControlPayload.STREAM_CODEC.decode(buffer);assertEquals(input,decoded);assertTrue(decoded.valid());assertEquals(0,buffer.readableBytes());}
        finally{buffer.release();}
    }
    @Test void onlyKnownActionsAndBoundedIntentReachTheAuthority(){
        UUID aircraft=UUID.randomUUID(),lease=UUID.randomUUID();
        for(int rejected:new int[]{8,128,-1})assertFalse(new HelicopterControlPayload(aircraft,lease,0,0,0,0,0,rejected).valid());
        assertTrue(new HelicopterControlPayload(aircraft,lease,0,1,-1,180,-90,119).valid());
        assertFalse(new HelicopterControlPayload(aircraft,lease,0,0,0,Double.NaN,0,4).valid());
        assertFalse(new HelicopterControlPayload(aircraft,lease,-1,0,0,0,0,4).valid());
    }
}
