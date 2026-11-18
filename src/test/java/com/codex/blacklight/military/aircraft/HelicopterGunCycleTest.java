package com.codex.blacklight.military.aircraft;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterGunCycleTest {
    @Test void fourSecondBurstAndRestWithExistingFourTickRoundCadence(){
        var cycle=new HelicopterGunCycle();int shots=0;
        for(int tick=0;tick<320;tick++){
            if(tick>0)cycle.tick();
            if(tick%4==0&&cycle.ready()){cycle.shot();shots++;assertTrue(tick<80||tick>=160&&tick<240);}
            if(tick==79)assertEquals(20,shots);
            if(tick==159)assertEquals(20,shots);
        }
        assertEquals(40,shots);
    }
    @Test void savedRestCannotBeSkippedAndBoundaryReturnsToFire(){
        var original=new HelicopterGunCycle();original.shot();for(int i=0;i<100;i++)original.tick();
        var restored=new HelicopterGunCycle();restored.restore(original.burstTicks(),original.restTicks());
        for(int i=0;i<60;i++){assertFalse(restored.ready());restored.tick();}
        assertTrue(restored.ready());restored.shot();assertEquals(80,restored.burstTicks());
    }
    @Test void invalidSavedCountersAreBoundedAndRestWinsConflict(){
        var cycle=new HelicopterGunCycle();cycle.restore(Integer.MAX_VALUE,Integer.MAX_VALUE);
        assertEquals(0,cycle.burstTicks());assertEquals(80,cycle.restTicks());
        cycle.restore(-1,-1);assertTrue(cycle.ready());assertEquals(0,cycle.burstTicks());
        cycle.shot();cycle.reset();assertTrue(cycle.ready());assertEquals(0,cycle.burstTicks());
    }
}
