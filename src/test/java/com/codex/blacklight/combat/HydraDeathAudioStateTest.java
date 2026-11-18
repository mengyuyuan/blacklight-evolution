package com.codex.blacklight.combat;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.codex.blacklight.combat.HydraDeathAudioState.Cue.*;

class HydraDeathAudioStateTest {
    @Test void observedDeathUsesSourceCrossingsWithoutRepeatingSameAge(){
        var state=new HydraDeathAudioState(0,-1);
        assertEquals(List.of(ROAR),state.observe(0,0).stream().map(HydraDeathAudioState.Event::cue).toList());
        assertTrue(state.observe(0,0).isEmpty());
        for(int i=1;i<30;i++)assertTrue(state.observe(0,i).isEmpty());
        assertEquals(WHOOSH,state.observe(0,30).get(0).cue());
        for(int i=31;i<40;i++)state.observe(0,i);
        var second=state.observe(0,40).get(0);assertEquals(ROAR,second.cue());assertEquals(.6F,second.minimumPitch());
        for(int i=41;i<60;i++)state.observe(0,i);
        assertEquals(WHOOSH,state.observe(0,60).get(0).cue());
    }
    @Test void lateTrackingAndLargeClockJumpsDoNotReplayHistoricalOneShots(){
        var state=new HydraDeathAudioState(0,40);assertTrue(state.observe(0,40).isEmpty());
        assertTrue(state.observe(0,69).isEmpty());
        assertFalse(state.debris());state.observe(0,70);assertTrue(state.debris());
        state.observe(0,103);assertFalse(state.debris());
    }
    @Test void thermalHasOneRoarAndNeverOrdinaryWhooshOrSinkLoop(){
        var state=new HydraDeathAudioState(0,-1);
        assertEquals(6576,state.observe(1,0).get(0).sourceOffset());
        for(int i=1;i<=40;i++){assertTrue(state.observe(1,i).isEmpty());assertFalse(state.debris());}
    }
    @Test void replacementRebasesAndNewObservedLifeCanEmitANewDeath(){
        var state=new HydraDeathAudioState(0,75);
        assertTrue(state.replaced(0,10));assertTrue(state.observe(0,10).isEmpty());assertFalse(state.debris());
        assertTrue(state.observe(0,-1).isEmpty());assertFalse(state.debris());
        assertEquals(ROAR,state.observe(0,1).get(0).cue());
        state.observe(0,39);assertEquals(7384,state.observe(0,41).get(0).sourceOffset());
    }
}
