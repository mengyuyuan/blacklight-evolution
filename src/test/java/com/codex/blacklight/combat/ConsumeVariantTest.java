package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsumeVariantTest {
    @Test void bothAlternativesAreSelectedWhileLargeTargetsRetainTheirOwnScenes(){
        for(var form:new PowerForm[]{PowerForm.CLAWS,PowerForm.WHIPFIST}) {
            var random=RandomSource.create(4812);var seen=new java.util.HashSet<Integer>();
            for(int i=0;i<128;i++)seen.add(ConsumeVariant.choose(form,false,random));
            assertEquals(java.util.Set.of(0,1),seen);
            for(int i=0;i<128;i++)assertEquals(0,ConsumeVariant.choose(form,true,random));
        }
        assertEquals(0,ConsumeVariant.choose(PowerForm.BLADE,false,RandomSource.create()));
    }
    @Test void selectedPairAndClockSurviveActualAttachmentEncoding(){
        for(var form:new PowerForm[]{PowerForm.CLAWS,PowerForm.WHIPFIST})for(int variant:new int[]{0,1}) {
            var data=new ConsumeVisual(7,100,ConsumeSequence.duration(form,false,variant),form.ordinal(),ConsumeVisual.CONSUME,123,new Vec3(1,2,3),variant);
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try {
                ConsumeVisual.CODEC.encode(buffer,data);var copy=ConsumeVisual.CODEC.decode(buffer);
                assertEquals(data,copy);assertEquals(0,buffer.readableBytes());
                for(int tick=0;tick<data.duration();tick++)assertEquals(data.motionProgress(100+tick,.5F),copy.motionProgress(100+tick,.5F));
            }finally{buffer.release();}
        }
    }
    @Test void completeLegacyAndNewPairsKeepTheirOwnEventsAndPlacement(){
        for(var form:new PowerForm[]{PowerForm.CLAWS,PowerForm.WHIPFIST})for(int variant:new int[]{0,1}) {
            String key=ConsumeSequence.key(form,variant);
            assertTrue(ConsumeSequence.paired(form,false,variant),key);
            assertNotNull(ConsumeReceiverScene.profile(form,false,variant),key);
            assertTrue(ConsumePlacementTrack.projection(key,.5F).isPresent(),key);
            assertTrue(ConsumePlacementTrack.contactFrame(key,.5F).isPresent(),key);
            assertTrue(ConsumeSequence.completionTick(form,false,variant)<ConsumeSequence.duration(form,false,variant));
        }
        assertEquals("SEVER",ConsumeReceiverScene.profile(PowerForm.CLAWS,false,0).style());
        assertEquals("HEAD_CRUSH",ConsumeReceiverScene.profile(PowerForm.CLAWS,false,1).style());
        assertEquals("SEVER",ConsumeReceiverScene.profile(PowerForm.WHIPFIST,false,0).style());
        assertEquals("LASH",ConsumeReceiverScene.profile(PowerForm.WHIPFIST,false,1).style());
        assertFalse(ConsumePlacementTrack.headGrip(PowerForm.CLAWS,0));
        assertTrue(ConsumePlacementTrack.headGrip(PowerForm.CLAWS,1));
        assertTrue(Float.isNaN(ConsumeSequence.splitSeconds(PowerForm.CLAWS,false,1)));
        assertTrue(ConsumeSequence.receiverProgress(PowerForm.CLAWS,1,false,0)<1);
        assertEquals(1,ConsumeSequence.receiverProgress(PowerForm.CLAWS,1,false,1));
        assertNotEquals(ConsumeReceiverScene.clipSeconds(PowerForm.CLAWS,0),ConsumeReceiverScene.clipSeconds(PowerForm.CLAWS,1));
    }
    @Test void invalidOrUnrelatedVariantsCannotSelectAnotherWeaponsScene(){
        assertEquals(0,new ConsumeVisual(1,0,40,PowerForm.CLAWS.ordinal(),ConsumeVisual.CONSUME,0,Vec3.ZERO,97).variant());
        assertEquals(0,new ConsumeVisual(1,0,40,PowerForm.BLADE.ordinal(),ConsumeVisual.CONSUME,0,Vec3.ZERO,1).variant());
        assertEquals("large_claws",ConsumeSequence.clipKey(PowerForm.CLAWS,true,1));
    }
}
