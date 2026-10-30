package com.codex.blacklight.infection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TissueLateBodyTest {
    @Test void approvedMatureBodyStaysExactAndLateGrowthIsContinuous() {
        for(long age:new long[]{Long.MIN_VALUE,-1,0,12000,24000,48000})
            assertEquals(0,TissueLateBody.progress(age));
        float previous=0;
        for(long age=48000;age<=144000;age+=100){
            float next=TissueLateBody.progress(age);
            assertTrue(next>=previous&&next<=1);
            assertTrue(next-previous<.002F);
            previous=next;
        }
        assertEquals(1,TissueLateBody.progress(144000));
        assertEquals(1,TissueLateBody.progress(Long.MAX_VALUE));
        assertTrue(TissueLateBody.progress(96000)>.49F);
    }
}
