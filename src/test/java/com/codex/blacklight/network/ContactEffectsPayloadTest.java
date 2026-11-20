package com.codex.blacklight.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContactEffectsPayloadTest {
    private static FriendlyByteBuf buffer(){return new FriendlyByteBuf(Unpooled.buffer());}
    @Test void playerStrikeRetainsContactTypeAndChargeOnTheOriginalWireLayout(){
        var style=com.codex.blacklight.infection.InfectedAttackStyle.TEAR;
        for(var strike:com.codex.blacklight.combat.FleshStrike.values())for(boolean charged:new boolean[]{false,true}){
            int appearance=InfectedImpactPayload.appearance(style,strike,charged);
            var packet=new InfectedImpactPayload(12,13,appearance,1,81,2,0,1,0,.6F,72);
            var b=buffer();try{
                InfectedImpactPayload.STREAM_CODEC.encode(b,packet);
                var decoded=InfectedImpactPayload.STREAM_CODEC.decode(b);
                assertEquals(style.ordinal(),decoded.style());
                assertEquals(strike,decoded.strike());
                assertEquals(charged&&strike!=com.codex.blacklight.combat.FleshStrike.GENERIC,decoded.charged());
                assertFalse(b.isReadable());
            }finally{b.release();}
        }
        for(int legacy=0;legacy<=5;legacy++){
            var packet=new InfectedImpactPayload(1,2,legacy,0,0,0,0,1,0,1,3);
            assertEquals(legacy,packet.style());assertEquals(0,packet.strike().id);assertFalse(packet.charged());
        }
    }
    @Test void hammerReceiptPreservesTheStruckPartWithoutChangingItsMaterialOrCharge(){
        int base=InfectedImpactPayload.appearance(com.codex.blacklight.infection.InfectedAttackStyle.CRUSH,com.codex.blacklight.combat.FleshStrike.HAMMER,true);
        for(int region:new int[]{0,1,2,4,8,16,32}){
            var packet=new InfectedImpactPayload(1,2,InfectedImpactPayload.hammerRegion(base,region),1,2,3,0,1,0,1,9);
            assertEquals(region,packet.region());assertTrue(packet.charged());assertEquals(com.codex.blacklight.combat.FleshStrike.HAMMER,packet.strike());
        }
    }
    @Test void allContactKindsRoundTripTogetherWithTimeMaterialsAndSound(){
        var tissue=new InfectedImpactPayload(12,13,0,1,81,2,0,1,0,.6F,72);
        for(int kind=0;kind<=ContactEffectsPayload.SOUND;kind++){
            var flash=new CombatImpactPayload(1,81,2,3.2F,kind==0);
            var p=new ContactEffectsPayload(123,42,kind,flash,kind==1||kind==2?tissue:null,true,
                kind==0?List.of(new ContactEffectsPayload.Surface(2,80.5,2,24,1,6,1.25)):List.of(),
                List.of(new ContactEffectsPayload.Sound(new ResourceLocation("blacklight","infection.ground"),1,.8F)));
            var b=buffer();try{ContactEffectsPayload.STREAM_CODEC.encode(b,p);assertEquals(p,ContactEffectsPayload.STREAM_CODEC.decode(b));assertFalse(b.isReadable());}finally{b.release();}
        }
    }
    @Test void decoderRejectsOversizedOrNegativeSampleCountBeforeAllocation(){
        for(int count:new int[]{-1,49,Integer.MAX_VALUE}){
            var b=buffer();try{
                b.writeLong(1);b.writeLong(1);b.writeVarInt(0);CombatImpactPayload.STREAM_CODEC.encode(b,new CombatImpactPayload(0,0,0,1,true));
                b.writeBoolean(false);b.writeBoolean(true);b.writeVarInt(count);
                assertThrows(IllegalArgumentException.class,()->ContactEffectsPayload.STREAM_CODEC.decode(b));
            }finally{b.release();}
        }
    }
    @Test void impossibleTerrainAndMissingAcceptedHitCannotBePresented(){
        var flash=new CombatImpactPayload(0,80,0,3,true);
        assertThrows(IllegalArgumentException.class,()->new ContactEffectsPayload(1,1,0,flash,null,true,List.of(new ContactEffectsPayload.Surface(0,100,0,1,0,0,0)),List.of()));
        assertThrows(IllegalArgumentException.class,()->new ContactEffectsPayload(1,1,1,flash,null,false,List.of(),List.of()));
        assertThrows(IllegalArgumentException.class,()->new ContactEffectsPayload(1,1,0,flash,null,true,List.of(),List.of(new ContactEffectsPayload.Sound(new ResourceLocation("minecraft","test"),Float.NaN,1))));
    }
}
