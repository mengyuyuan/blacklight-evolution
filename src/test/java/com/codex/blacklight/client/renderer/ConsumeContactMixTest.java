package com.codex.blacklight.client.renderer;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsumeContactMixTest {
    private static final ConsumeContactMix.Strike HIT=new ConsumeContactMix.Strike(4,15,100,0);
    @Test void oneImpactRegardlessOfPacketArrivalPoseRedrawOrEffectType(){
        var mix=new ConsumeContactMix<String>();var output=new ArrayList<String>();
        mix.server(HIT,"offscreen",110);
        mix.local(HIT,"slash",111,output::add);mix.local(HIT,"cleave",111,output::add);
        mix.tick(113,output::add);mix.server(HIT,"echo",114);mix.tick(118,output::add);
        assertEquals(List.of("slash"),output);
    }
    @Test void offscreenObserverAndFirstPersonReceiveOneEventAndPauseDoesNotAdvanceTheClock(){
        var mix=new ConsumeContactMix<String>();var output=new ArrayList<String>();
        mix.server(HIT,"first contact",110);mix.server(HIT,"duplicate",110);
        for(int i=0;i<60;i++)mix.tick(110,output::add);
        assertEquals(List.of(),output);mix.tick(112,output::add);
        mix.local(HIT,"late geometry",113,output::add);
        assertEquals(List.of("first contact"),output);
    }
    @Test void consecutiveVictimsCombosAndNewExecutionsHaveIndependentBeats(){
        var mix=new ConsumeContactMix<String>();var output=new ArrayList<String>();
        mix.local(HIT,"start",110,output::add);
        mix.local(new ConsumeContactMix.Strike(4,15,100,1),"combo",120,output::add);
        mix.local(new ConsumeContactMix.Strike(5,15,100,0),"next body",121,output::add);
        mix.local(new ConsumeContactMix.Strike(4,15,125,0),"new execution",126,output::add);
        mix.clear();mix.local(HIT,"new level",110,output::add);
        assertEquals(List.of("start","combo","next body","new execution","new level"),output);
    }
}
