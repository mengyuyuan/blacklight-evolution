package com.codex.blacklight.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LairSitingTest {
    @Test void floorFollowsLowestGround(){
        assertEquals(64-LairSiting.DROP,LairSiting.floor(64,80,-64));
        assertEquals(58-LairSiting.DROP,LairSiting.floor(64,58,-64));
    }
    @Test void rejectsNearBedrockAndOverlongShafts(){
        assertEquals(Integer.MIN_VALUE,LairSiting.floor(30,30,0));
        assertTrue(LairSiting.floor(63,63,0)>0);
        assertEquals(Integer.MIN_VALUE,LairSiting.floor(140,60,-64));
        assertTrue(LairSiting.floor(100,70,-64)!=Integer.MIN_VALUE);
    }
    @Test void chanceGrowsAndCaps(){
        assertEquals(.003,LairSiting.chance(0,.003,.015),1e-9);
        assertEquals(.009,LairSiting.chance(2,.003,.015),1e-9);
        assertEquals(.015,LairSiting.chance(50,.003,.015),1e-9);
        assertEquals(.003,LairSiting.chance(Double.NaN,.003,.015),1e-9);
    }
    @Test void offsetsStartAtCentreAndCoverTheChunkArea(){
        var o=LairSiting.offsets();
        assertArrayEquals(new int[]{0,0},o.get(0));
        assertEquals(49,o.size());
        for(int i=1;i<o.size();i++)assertTrue(o.get(i-1)[0]*o.get(i-1)[0]+o.get(i-1)[1]*o.get(i-1)[1]<=o.get(i)[0]*o.get(i)[0]+o.get(i)[1]*o.get(i)[1]);
    }
}
