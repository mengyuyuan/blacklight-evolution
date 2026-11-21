package com.codex.blacklight.client.renderer;

import static com.codex.blacklight.client.renderer.ConsumeImpactResponse.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ConsumeImpactResponseTest {
    @Test void responseFollowsEachImplementedContactAndSkipsPiercingAndGroundedMoves(){
        for(String c:new String[]{"normal","normal_head_stomp","normal_beatdown"})assertNull(profile(c,0),c);
        assertNull(profile("muscle_heart",0));
        assertEquals(Impact.GRIP,profile("muscle_heart",1));
        for(String c:new String[]{"hammer","hammer_smush"})assertEquals(Impact.HAMMER,profile(c,0));
        assertEquals(Impact.GRIP,profile("claws_head",0));
        assertEquals(Impact.KNEE,profile("normal_knee_head",0));
        assertEquals(Impact.LASH_FIRST,profile("whip_lash",0));assertEquals(Impact.LASH_RETURN,profile("whip_lash",1));
        for(String c:new String[]{"claws","blade","whip","muscle"})assertEquals(Impact.CUT,profile(c,0),c);
        for(String c:new String[]{"stealth_neck","stealth_bearhug","stealth_nelson","stealth_guillotine","stealth_jugular"})
            assertEquals(Impact.GRAPPLE,profile(c,0));
    }
    @Test void everyResponseStopsAtContactThenReturnsMonotonicallyToTheActualClock(){
        for(var hit:Impact.values()){
            assertEquals(0,resume(hit,0));assertEquals(0,resume(hit,hit.stop));
            double past=0;
            for(double age=0;age<12;age+=.05){
                double next=resume(hit,age);assertTrue(next>=past&&next<=1);past=next;
            }
            assertEquals(1,past);
        }
        assertTrue(resume(Impact.LASH_FIRST,2.6)<.32);
        assertTrue(resume(Impact.LASH_FIRST,5.7)>.999);
    }
    @Test void upperBodyLoadsFirstTransmitsToChestAndDecays(){
        for(var hit:new Impact[]{Impact.HAMMER,Impact.GRIP,Impact.KNEE,Impact.LASH_RETURN}){
            assertEquals(0,displacement(hit,hit.stop,1));
            assertTrue(displacement(hit,hit.stop+.25,1)>0);
            assertEquals(0,displacement(hit,hit.stop+.25,32));
            assertTrue(displacement(hit,hit.stop+1.33,32)>.055);
            assertTrue(displacement(hit,hit.stop+3.8,32)<0);
            assertEquals(0,displacement(hit,hit.stop+7,1));assertEquals(0,displacement(hit,hit.stop+7,32));
        }
    }
}
