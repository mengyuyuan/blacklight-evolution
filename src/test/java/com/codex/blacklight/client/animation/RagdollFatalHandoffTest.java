package com.codex.blacklight.client.animation;

import com.codex.blacklight.network.RagdollImpactPayload;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RagdollFatalHandoffTest {
    private static RagdollImpactPayload packet(int ticks,boolean refinement,long at,boolean fatal){
        return new RagdollImpactPayload(42,1,ticks,0,false,refinement,0,0,.25,0,List.of(),List.of(),List.of(),at,fatal);
    }
    @Test void onlyAuthoritativeDeathIsHeldAndFeedbackCannotStartACorpse(){
        var handoff=new RagdollFatalHandoff();
        handoff.add(packet(60,false,120,false));
        assertTrue(handoff.impacts().isEmpty());
        var refinement=packet(60,true,121,true);var feedback=packet(8,false,122,true);
        handoff.add(refinement);handoff.add(feedback);
        assertEquals(List.of(refinement,feedback),handoff.impacts());
        assertTrue(handoff.bodyInputs().isEmpty());
    }
    @Test void lateFirstImpactStartsBeforeEarlierRefinementsWithoutChangingTheirClocks(){
        var handoff=new RagdollFatalHandoff();
        var refinement=packet(60,true,201,true);var full=packet(60,false,200,true);var later=packet(60,true,202,true);
        handoff.add(refinement);handoff.add(full);handoff.add(later);
        var inputs=handoff.bodyInputs();
        assertEquals(List.of(full,refinement,later),inputs);
        assertSame(full,inputs.get(0));assertSame(refinement,inputs.get(1));assertSame(later,inputs.get(2));
        assertEquals(200,inputs.get(0).at());assertTrue(inputs.get(0).fatal());
        assertEquals(List.of(refinement,full,later),handoff.impacts());
    }
    @Test void refinementBurstCannotCrowdOutTheFirstBodyImpulse(){
        var handoff=new RagdollFatalHandoff();
        for(int i=0;i<20;i++)handoff.add(packet(60,true,300+i,true));
        assertEquals(8,handoff.impacts().size());assertTrue(handoff.bodyInputs().isEmpty());
        var full=packet(60,false,299,true);handoff.add(full);
        assertEquals(8,handoff.bodyInputs().size());assertSame(full,handoff.bodyInputs().get(0));
        for(int i=0;i<20;i++)handoff.add(packet(60,true,400+i,true));
        assertSame(full,handoff.bodyInputs().get(0));assertEquals(299,handoff.bodyInputs().get(0).at());
    }
    @Test void normalDeathThenRefinementKeepsOriginalOrder(){
        var handoff=new RagdollFatalHandoff();var full=packet(60,false,500,true);var refinement=packet(60,true,501,true);
        handoff.add(full);handoff.add(refinement);
        assertEquals(List.of(full,refinement),handoff.bodyInputs());
    }
}
