package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContactEffectsQueueTest {
    @Test void visibleContactGateDoesNotBlockOtherActorsOrReplay(){
        var q=new ContactEffectsQueue<String>();var shown=new ArrayList<String>();q.add(10,10,1,"flyer");q.add(10,10,2,"other");
        q.drain(10,v->!v.equals("flyer"),shown::add);assertEquals(List.of("other"),shown);assertEquals(1,q.size());
        q.drain(12,v->true,shown::add);assertEquals(List.of("other","flyer"),shown);q.drain(13,shown::add);assertEquals(2,shown.size());
    }
    @Test void hiddenContactGateStillExpiresAndWorldChangeClearsIt(){
        var q=new ContactEffectsQueue<String>();var shown=new ArrayList<String>();q.add(10,10,1,"hidden");q.drain(31,v->false,shown::add);assertEquals(0,q.size());assertTrue(shown.isEmpty());
        q.add(32,32,2,"old world");q.clear();q.drain(33,v->true,shown::add);assertTrue(shown.isEmpty());
    }
    @Test void longRunningWorldKeepsSubTickPrecision(){
        long tick=30_000_000;var q=new ContactEffectsQueue<String>();var shown=new ArrayList<String>();
        assertEquals(tick+.75,ContactEffectsQueue.renderTime(tick,.75F));
        q.add(tick,tick+1,1,"next");q.drain(ContactEffectsQueue.renderTime(tick,.75F),shown::add);assertTrue(shown.isEmpty());
        q.drain(ContactEffectsQueue.renderTime(tick+1,0),shown::add);assertEquals(List.of("next"),shown);
    }
    @Test void packetMustWaitForTheDisplayedContactFrame(){
        var q=new ContactEffectsQueue<String>();var shown=new ArrayList<String>();
        assertTrue(q.add(42,43,1,"contact"));q.drain(42.28,shown::add);assertTrue(shown.isEmpty());
        q.drain(42.999,shown::add);assertTrue(shown.isEmpty());q.drain(43.01,shown::add);assertEquals(List.of("contact"),shown);
        q.drain(44,shown::add);assertEquals(1,shown.size());
    }
    @Test void reorderedReceiptsStayChronologicalAndDuplicatesDoNotReplay(){
        var q=new ContactEffectsQueue<String>();var shown=new ArrayList<String>();
        assertTrue(q.add(40,43,2,"later"));assertTrue(q.add(40,41,1,"first"));
        assertFalse(q.add(40,43,2,"duplicate"));q.drain(43,shown::add);assertEquals(List.of("first","later"),shown);
        assertFalse(q.add(44,43,2,"replayed"));
    }
    @Test void staleOrUnboundedTimesCannotProduceEffects(){
        var q=new ContactEffectsQueue<String>();var shown=new ArrayList<String>();
        assertFalse(q.add(100,79,1,"stale"));assertFalse(q.add(100,301,2,"future"));assertFalse(q.add(Double.NaN,100,3,"nan"));
        assertTrue(q.add(100,100,4,"queued"));q.drain(121,shown::add);assertTrue(shown.isEmpty());assertEquals(0,q.size());
    }
    @Test void lateValidReceiptsDisplayOnceAndWorldResetClearsState(){
        var q=new ContactEffectsQueue<String>();var shown=new ArrayList<String>();
        assertTrue(q.add(45,43,1,"late"));q.drain(45.2,shown::add);assertEquals(List.of("late"),shown);
        q.add(45,46,2,"old world");q.clear();q.drain(46,shown::add);assertEquals(1,shown.size());
        assertTrue(q.add(10,11,1,"new world"));
    }
    @Test void overloadHasABoundedQueueAndRecoversAfterDrain(){
        var q=new ContactEffectsQueue<Integer>();var shown=new ArrayList<Integer>();
        for(int i=0;i<128;i++)assertTrue(q.add(0,1,i,i));assertFalse(q.add(0,1,128,128));
        q.drain(1,shown::add);assertEquals(128,shown.size());assertEquals(0,q.size());assertTrue(q.add(1,2,128,128));
    }
}
