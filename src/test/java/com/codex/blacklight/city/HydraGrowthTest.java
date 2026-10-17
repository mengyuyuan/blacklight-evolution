package com.codex.blacklight.city;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HydraGrowthTest {
    @Test void timeAloneNeverOpensEntrance(){assertFalse(HydraGrowth.mature(Integer.MAX_VALUE,0));assertEquals(0,HydraGrowth.progress(2400,0));}
    @Test void feedingAloneDoesNotSkipGrowth(){assertEquals(.5F,HydraGrowth.progress(1200,5));assertFalse(HydraGrowth.mature(2399,5));assertTrue(HydraGrowth.mature(2400,5));}
    @Test void corruptAndOldNbtValuesAreBounded(){assertEquals(0,HydraGrowth.progress(-8,-1));assertEquals(1,HydraGrowth.progress(Integer.MAX_VALUE,Integer.MAX_VALUE));}
    @Test void infectionFootprintIsFinite(){int n=0;for(int x=-100;x<=100;x++)for(int z=-100;z<=100;z++)if(HydraGrowth.inside(x,z))n++;assertEquals(113,n);assertFalse(HydraGrowth.inside(6,1));}
}
