package com.codex.blacklight.infection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TissueDevelopmentTest {
    @Test void fractionalUnfedProgressIsConservedAcrossLoadedBatches(){
        var state=new TissueDevelopment.Step(0,TissueInheritance.EMPTY,0,0,0,new double[4],0);
        for(int i=0;i<200;i++)state=TissueDevelopment.advance(state.age(),state.inheritance(),5,TissueCore.MUSCLE,
                state.nutrition(),state.debt(),state.cultivation(),state.fractions(),20,.002,true,false,TissueInheritance.Rules.DEFAULT);
        assertEquals(8,state.age()+state.fractions()[0],1e-9);
        assertEquals(0,state.nutrition());assertEquals(0,state.repairFraction());
        var one=TissueDevelopment.advance(0,TissueInheritance.EMPTY,5,TissueCore.MUSCLE,0,0,0,
                new double[4],4000,.002,true,false,TissueInheritance.Rules.DEFAULT);
        assertEquals(one.age()+one.fractions()[0],state.age()+state.fractions()[0],1e-9);
    }
    @Test void noElapsedTicksCannotChargeFoodOrGrantOfflineWorkAndSnapshotsOwnTheirClocks(){
        double[] clocks={.2,.3,.4,.5};
        var step=TissueDevelopment.advance(48000,TissueInheritance.EMPTY,6,TissueCore.JAW,20,31,45,clocks,
                0,1000,true,true,TissueInheritance.Rules.DEFAULT);
        assertEquals(48000,step.age());assertEquals(20,step.nutrition());assertEquals(31,step.debt());
        assertEquals(45,step.cultivation());assertEquals(0,step.repairFraction());
        clocks[0]=.9;assertEquals(.2,step.fractions()[0]);
        var exposed=step.fractions();exposed[1]=.8;assertEquals(.3,step.fractions()[1]);
    }
    @Test void saturatedAgeStaysFiniteWithoutCappingAcquiredEvolution(){
        var step=TissueDevelopment.advance(Long.MAX_VALUE-1,TissueInheritance.EMPTY,9,TissueCore.MUSCLE,
                0,0,0,new double[4],20,1,true,false,TissueInheritance.Rules.DEFAULT);
        assertEquals(Long.MAX_VALUE,step.age());assertSame(TissueInheritance.EMPTY,step.inheritance());
        assertTrue(TissueGrowth.generation(step.age())>1_000_000);
    }
}
