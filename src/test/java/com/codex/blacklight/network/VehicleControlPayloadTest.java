package com.codex.blacklight.network;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleControlPayloadTest {
    private static final UUID VEHICLE=UUID.randomUUID(), LEASE=UUID.randomUUID();
    private static VehicleControlPayload input(double f,double s,double yaw,double pitch,int actions) {
        return new VehicleControlPayload(VEHICLE,LEASE,73,f,s,yaw,pitch,actions);
    }
    @Test void realWireCodecPreservesOnlyIntentAndLease() {
        var sent=input(-.7,.9,-179.9,64,3);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try { VehicleControlPayload.STREAM_CODEC.encode(buffer,sent);
            assertEquals(sent,VehicleControlPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0,buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test void finiteBoundaryInputsAndAllFiveKnownActionsAreAccepted() {
        assertTrue(input(1,-1,-180,90,31).valid());
        assertTrue(input(0,0,180,-90,0).valid());
    }
    @Test void nonfiniteOrOutOfRangeControlCannotReachVehicle() {
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,1.00001,-1.00001}) {
            assertFalse(input(bad,0,0,0,0).valid());
            assertFalse(input(0,bad,0,0,0).valid());
        }
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-180.1,180.1})
            assertFalse(input(0,0,bad,0,0).valid());
        for(double bad:new double[]{Double.NaN,Double.NEGATIVE_INFINITY,-90.1,90.1})
            assertFalse(input(0,0,0,bad,0).valid());
    }
    @Test void malformedLeaseSequenceAndUnknownActionsAreRejected() {
        assertFalse(new VehicleControlPayload(null,LEASE,0,0,0,0,0,0).valid());
        assertFalse(new VehicleControlPayload(VEHICLE,null,0,0,0,0,0,0).valid());
        assertFalse(new VehicleControlPayload(VEHICLE,LEASE,-1,0,0,0,0,0).valid());
        assertFalse(input(0,0,0,0,32).valid());
        assertFalse(input(0,0,0,0,-1).valid());
    }
    @Test void missingServerConnectionCannotControlAnything() {
        assertFalse(input(1,0,0,0,1).handle(null));
    }
}
