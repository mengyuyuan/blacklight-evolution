package com.codex.blacklight.infection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TissueCoreTest {
    @Test void primaryOrganStaysStrongestEvenDuringLaterTransitions(){
        for(long seed=0;seed<50;seed++)for(long age=72000;age<600000;age+=2711){
            var s=TissueEvolution.sample(seed,age);float[] traits={s.muscle(),s.carapace(),s.jaw(),s.grasp(),s.crest()};
            int primary=TissueCore.forSeed(seed).ordinal();
            for(int i=0;i<5;i++)if(i!=primary)assertTrue(traits[primary]>traits[i]);
        }
    }
    @Test void specimenThresholdsAndUnboundedAgeYieldHaveFiniteReturns(){
        long[] thresholds={72000,240000,816000};
        for(int i=0;i<3;i++){assertEquals(i,TissueCore.quality(thresholds[i]-1));assertEquals(i+1,TissueCore.quality(thresholds[i]));}
        float previous=1;for(long age:new long[]{0,12000,48000,72000,240000,816000,Long.MAX_VALUE}){
            float value=TissueCore.yieldMultiplier(age);assertTrue(Float.isFinite(value)&&value>=previous&&value<=3);previous=value;
        }
    }
}
