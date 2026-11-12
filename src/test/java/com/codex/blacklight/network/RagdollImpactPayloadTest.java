package com.codex.blacklight.network;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RagdollImpactPayloadTest {
    private static RegistryFriendlyByteBuf malformedTimedSpan(double time,int count){
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        b.writeVarInt(1);b.writeVarInt(1);b.writeVarInt(60);b.writeVarInt(0);b.writeBoolean(true);b.writeBoolean(true);b.writeVarInt(32);
        b.writeDouble(0);b.writeDouble(0);b.writeDouble(0);b.writeVarInt(0);b.writeVarInt(1);
        b.writeVarInt(32);b.writeFloat(0);b.writeFloat(0);b.writeFloat(0);b.writeFloat(0);b.writeFloat(1);b.writeFloat(0);
        b.writeLong(1);b.writeUtf("edge",120);b.writeVarInt(1);
        b.writeDouble(time);b.writeDouble(0);b.writeDouble(0);b.writeDouble(0);b.writeFloat(180);b.writeVarInt(count);
        if(count==2)for(int i=0;i<6;i++)b.writeFloat(i==4?1:0);
        b.writeVarInt(0);return b;
    }
    @Test void decoderRejectsBadClockAndProfileCountsBeforeTheyReachPhysics(){
        for(double time:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-2}){
            var b=malformedTimedSpan(time,2);try{assertThrows(IllegalArgumentException.class,()->RagdollImpactPayload.CODEC.decode(b));}finally{b.release();}
        }
        for(int count:new int[]{-1,0,1,7,Integer.MAX_VALUE}){
            var b=malformedTimedSpan(10,count);try{assertThrows(IllegalArgumentException.class,()->RagdollImpactPayload.CODEC.decode(b));}finally{b.release();}
        }
    }
    @Test void timedSamplesRoundTripTheirOwnWorldFrames(){
        var origin=new Vec3(29_000_000.125,251,29_000_000.25);
        var path=List.of(new RagdollImpactPayload.BladeSpan(List.of(Vec3.ZERO,new Vec3(1,0,0)),12.125,origin,90),
                new RagdollImpactPayload.BladeSpan(List.of(Vec3.ZERO,new Vec3(1,0,0)),12.25,origin.add(.125,0,0),95));
        var packet=new RagdollImpactPayload(1,1,60,0,true,true,32,0,0,0,List.of(),
                List.of(new RagdollImpactPayload.CutPlane(32,Vec3.ZERO,new Vec3(0,1,0),1,"edge",path)),List.of(),12);
        var bytes=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try{RagdollImpactPayload.CODEC.encode(bytes,packet);assertEquals(packet,RagdollImpactPayload.CODEC.decode(bytes));assertEquals(0,bytes.readableBytes());}
        finally{bytes.release();}
    }
    @Test void invalidSampleFramesAndNonMonotonicPathsAreRejected(){
        var points=List.of(Vec3.ZERO,new Vec3(1,0,0));
        assertThrows(IllegalArgumentException.class,()->new RagdollImpactPayload.BladeSpan(points,Double.NaN,Vec3.ZERO,0));
        assertThrows(IllegalArgumentException.class,()->new RagdollImpactPayload.BladeSpan(points,-2,Vec3.ZERO,0));
        assertThrows(IllegalArgumentException.class,()->new RagdollImpactPayload.BladeSpan(points,1,new Vec3(33_000_000,0,0),0));
        assertThrows(IllegalArgumentException.class,()->new RagdollImpactPayload.BladeSpan(points,1,Vec3.ZERO,Float.NaN));
        var a=new RagdollImpactPayload.BladeSpan(points,1,Vec3.ZERO,0);var b=new RagdollImpactPayload.BladeSpan(points,2,Vec3.ZERO,0);
        assertThrows(IllegalArgumentException.class,()->new RagdollImpactPayload.CutPlane(32,Vec3.ZERO,new Vec3(0,1,0),1,"edge",List.of(b,a)));
        assertThrows(IllegalArgumentException.class,()->new RagdollImpactPayload.CutPlane(32,Vec3.ZERO,new Vec3(0,1,0),1,"edge",List.of(new RagdollImpactPayload.BladeSpan(points),a)));
    }
    @Test void firstImpactAndLaterBladesKeepTheirDistinctWireMeaning(){
        var first=new RagdollImpactPayload(42,1,60,1,true,false,0,.125,.25,.5,List.of(),List.of(),
                List.of(new RagdollImpactPayload.Wound(32,new Vec3(0,1,0),new Vec3(0,0,1),1)));
        var planes=List.of(
                new RagdollImpactPayload.CutPlane(0,new Vec3(-.125,1,0),new Vec3(1,0,0),73,"Index long blade / Hand_R",
                        List.of(new RagdollImpactPayload.BladeSpan(List.of(new Vec3(0,0,0),new Vec3(.25,.5,0),new Vec3(0,1,0))),new RagdollImpactPayload.BladeSpan(List.of(new Vec3(0,0,1),new Vec3(.25,.5,1),new Vec3(0,1,1))))),
                new RagdollImpactPayload.CutPlane(0,new Vec3(.125,1,0),new Vec3(1,0,0)));
        var later=new RagdollImpactPayload(42,1,60,0,true,true,32,0,0,0,List.of(),planes,List.of());
        var bytes=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            RagdollImpactPayload.CODEC.encode(bytes,first);RagdollImpactPayload.CODEC.encode(bytes,later);
            assertEquals(first,RagdollImpactPayload.CODEC.decode(bytes));
            var received=RagdollImpactPayload.CODEC.decode(bytes);
            assertEquals(later,received);assertTrue(received.refinement());
            assertEquals(planes,received.planes());assertEquals(0,bytes.readableBytes());
        } finally {bytes.release();}
    }
    @Test void profilesAreBoundedAndCannotChangeTopologyMidStroke(){
        assertThrows(IllegalArgumentException.class,()->new RagdollImpactPayload.BladeSpan(List.of(Vec3.ZERO)));
        assertThrows(IllegalArgumentException.class,()->new RagdollImpactPayload.BladeSpan(java.util.Collections.nCopies(7,Vec3.ZERO)));
        var a=new RagdollImpactPayload.BladeSpan(Vec3.ZERO,new Vec3(0,1,0));
        var b=new RagdollImpactPayload.BladeSpan(List.of(Vec3.ZERO,new Vec3(1,1,0),new Vec3(0,2,0)));
        assertThrows(IllegalArgumentException.class,()->new RagdollImpactPayload.CutPlane(0,Vec3.ZERO,new Vec3(1,0,0),1,"test",List.of(a,b)));
        assertFalse(a.near(b));
    }
}
