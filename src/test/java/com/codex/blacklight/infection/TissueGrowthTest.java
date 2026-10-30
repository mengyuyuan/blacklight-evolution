package com.codex.blacklight.infection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TissueGrowthTest {
    @Test void visibleGenerationsIncludeInitialDevelopmentAndContinueWithoutResetting(){
        assertEquals(0,TissueGrowth.generation(11999));assertEquals(1,TissueGrowth.generation(12000));
        assertEquals(1,TissueGrowth.generation(23999));assertEquals(2,TissueGrowth.generation(24000));
        assertEquals(2,TissueGrowth.generation(47999));assertEquals(3,TissueGrowth.generation(48000));
        assertEquals(4,TissueGrowth.generation(72000));assertTrue(TissueGrowth.generation(Long.MAX_VALUE)>4);
        assertEquals(7,TissueMetabolism.phaseFood(12000));assertEquals(10,TissueMetabolism.phaseFood(24000));
    }
    @Test void loadedTimeContinuesPastTheOldFinalStageWithoutOverflow() {
        assertEquals(0,TissueGrowth.advance(-20,-1));
        assertEquals(100L+Integer.MAX_VALUE,TissueGrowth.advance(100,Integer.MAX_VALUE));
        assertEquals(48001,TissueGrowth.advance(48000,1));
        assertEquals(Long.MAX_VALUE,TissueGrowth.advance(Long.MAX_VALUE-5,20));
        assertEquals(Long.MAX_VALUE,TissueGrowth.advance(Long.MAX_VALUE,Long.MAX_VALUE));
        float previous=-1;
        for(int tick=-1;tick<=TissueGrowth.MATURITY_TICKS+1;tick++) {
            float current=TissueGrowth.progress(tick);
            assertTrue(current>=previous && current>=0 && current<=1);
            previous=current;
        }
        assertEquals(3,TissueGrowth.stage(Integer.MAX_VALUE));
    }
    @Test void lateGrowthPreservesMaturityAndHasSmoothBoundedJoins() {
        assertEquals(1,TissueGrowth.progress(TissueGrowth.ANCIENT_TICKS));
        assertEquals(0,TissueGrowth.overgrowth(TissueGrowth.MATURITY_TICKS));
        assertEquals(1,TissueGrowth.overgrowth(TissueGrowth.HYPERTROPHY_TICKS));
        assertEquals(2,TissueGrowth.overgrowth(Integer.MAX_VALUE));
        float previous=0;
        for(int age=0;age<=TissueGrowth.ANCIENT_TICKS+1;age++) {
            float growth=TissueGrowth.overgrowth(age);assertTrue(growth>=previous&&growth<=2);previous=growth;
        }
        for(int boundary:new int[]{TissueGrowth.MATURITY_TICKS,TissueGrowth.HYPERTROPHY_TICKS,TissueGrowth.ANCIENT_TICKS})
            assertEquals(TissueGrowth.overgrowth(boundary-1),TissueGrowth.overgrowth(boundary+1),.00001F);
        assertEquals(TissueGrowth.MATURITY_TICKS+1,TissueGrowth.advance(TissueGrowth.MATURITY_TICKS,1),"old saves continue from their existing mature age");
    }
    @Test void layoutIsPersistentButDiffersBetweenHosts() {
        boolean differs=false;
        for(int i=0;i<100;i++) {
            float sample=TissueGrowth.sample(700123,i);
            assertEquals(sample,TissueGrowth.sample(700123,i));
            assertTrue(sample>=0 && sample<1);
            differs|=sample!=TissueGrowth.sample(900321,i);
        }
        assertTrue(differs);
    }
}
