package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumeLandingClockTest {
    @Test void waitForSupportBeforeTheFirstStrikeAndResumeAtTheSameFrame(){
        for(int variant:new int[]{ConsumeVariant.BEATDOWN,ConsumeVariant.HEAD_STOMP}){
            var waiting=new ConsumeVisual(1,100,90,PowerForm.NONE.ordinal(),ConsumeVisual.CONSUME,
                    0,Vec3.ZERO,variant,-1);
            int gate=ConsumeVisual.groundGate(waiting.form(),variant);
            assertEquals(gate-.5F,waiting.elapsed(100+gate-1,.5F));
            assertEquals(gate,waiting.elapsed(100+gate+5,.75F));
            assertEquals(1,ConsumePlacementTrack.groundWeight(PowerForm.NONE,variant,
                    waiting.motionProgress(100+gate+5,0)),1e-5);
            var resumed=waiting.landed(5);
            assertEquals(gate,resumed.elapsed(100+gate+5,0));
            assertEquals(gate+1.25F,resumed.elapsed(100+gate+6,.25F));
        }
    }

    @Test void uprightConsumesKeepTheirAuthoredClock(){
        var normal=new ConsumeVisual(1,100,75,PowerForm.NONE.ordinal(),ConsumeVisual.CONSUME,
                0,Vec3.ZERO,ConsumeVariant.DEFAULT);
        assertEquals(-1,ConsumeVisual.groundGate(normal.form(),normal.variant()));
        assertEquals(20.5F,normal.elapsed(120,.5F));
    }
    @Test void beatdownKeepsItsAirborneApexAndPlantsBeforeTheFirstPunch() {
        int variant=ConsumeVariant.BEATDOWN;
        var form=PowerForm.NONE;
        float seconds=ConsumeReceiverScene.clipSeconds(form,variant);
        assertEquals(0,ConsumePlacementTrack.groundWeight(form,variant,(24F/30)/seconds),.0001);
        assertEquals(1,ConsumePlacementTrack.groundWeight(form,variant,1F/seconds),.0001);
        var profile=ConsumeReceiverScene.profile(form,false,variant);
        assertEquals(java.util.List.of(50,60,70,82,99),
                profile.hitSeconds().stream().map(t->Math.round(30*t)).toList());
        assertEquals(115,Math.round(30*ConsumeReceiverScene.absorptionSeconds(form,variant)));
        assertTrue(ConsumeReceiverScene.beatdownRightHand(3));
        assertTrue(ConsumeReceiverScene.beatdownRightHand(4));
    }
}
