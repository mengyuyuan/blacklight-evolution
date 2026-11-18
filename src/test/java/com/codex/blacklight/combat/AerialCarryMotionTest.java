package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AerialCarryMotionTest {
    @Test void airborneEntryClampsRatherThanLoopingTheSourceTransition(){
        var visual=new ConsumeVisual(7,100,AerialCarryMotion.holdDuration(),PowerForm.NONE.ordinal(),ConsumeVisual.AIR_HOLD,0,Vec3.ZERO);
        assertTrue(ConsumeVisual.holding(visual.stage()));assertTrue(GrappleMotion.active(visual));
        assertFalse(visual.consuming());assertFalse(ConsumeVisual.blocksInput(visual.stage()));
        assertEquals(0,AerialCarryMotion.holdProgress(visual,100,0));
        assertEquals(1,AerialCarryMotion.holdProgress(visual,100+visual.duration()*3,0));
    }
    @Test void aerialThrowDoesNotConsumeOrImmobilizeFallingActor(){
        var visual=new ConsumeVisual(7,100,AerialCarryMotion.duration(),PowerForm.NONE.ordinal(),ConsumeVisual.AIR_THROW,35,Vec3.ZERO);
        assertFalse(visual.consuming());assertTrue(GrappleMotion.active(visual));
        assertTrue(ConsumeVisual.blocksInput(visual.stage()));
        assertFalse(ConsumeVisual.blocksTravel(visual.stage()));
        assertFalse(ConsumeVisual.holding(visual.stage()));
        assertEquals(0,AerialCarryMotion.progress(visual,100,0));
        assertEquals(1,AerialCarryMotion.progress(visual,100+visual.duration(),0));
        float before=AerialCarryMotion.progress(visual,100+AerialCarryMotion.releaseTick()-1,0);
        float at=AerialCarryMotion.progress(visual,100+AerialCarryMotion.releaseTick(),0);
        assertTrue(before<at&&at<1);
    }
    @Test void aerialPhaseAndSharedClockSurviveActualWireCodec(){
        var visual=new ConsumeVisual(8,140,AerialCarryMotion.duration(),PowerForm.MUSCLE_MASS.ordinal(),ConsumeVisual.AIR_THROW,145,new Vec3(1,4,7));
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{
            ConsumeVisual.CODEC.encode(buffer,visual);var copy=ConsumeVisual.CODEC.decode(buffer);
            assertEquals(visual,copy);assertEquals(0,buffer.readableBytes());
            for(int i=0;i<=visual.duration();i++)assertEquals(AerialCarryMotion.progress(visual,140+i,.5F),AerialCarryMotion.progress(copy,140+i,.5F));
        }finally{buffer.release();}
    }
    @Test void allAerialCarryPairsHaveMatchingSourceClocksAndPlacement(){
        for(String key:new String[]{"carry_sprint","carry_air","air_throw"}){
            var actor=LocalVisualMotion.find("consume_"+key+"_actor").orElseThrow();
            var victim=LocalVisualMotion.find("consume_"+key+"_victim").orElseThrow();
            assertEquals(actor.durationTicks(),victim.durationTicks());
            assertEquals(actor.animation().getAsJsonArray("animation").get(0).getAsJsonObject().get("time"),victim.animation().getAsJsonArray("animation").get(0).getAsJsonObject().get("time"));
            assertTrue(ConsumePlacementTrack.contactFrame(key,.5F).isPresent());
            assertTrue(ConsumePlacementTrack.projection(key,.5F).isPresent());
        }
        assertTrue(AerialCarryMotion.available());
        assertTrue(AerialCarryMotion.releaseTick()<AerialCarryMotion.duration());
    }
}
