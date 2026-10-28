package com.codex.blacklight.infection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutbreakRulesTest {
    @Test void PopulationFloorsKeepFoodAndMakeSpaceForActualEvolvingLineages(){
        assertEquals(OutbreakRules.Tier.FRESH,OutbreakRules.populationTier(4,5,0,0,0,6,18,99));
        assertEquals(OutbreakRules.Tier.GROWING,OutbreakRules.populationTier(1,6,0,0,0,6,18,0));
        assertEquals(OutbreakRules.Tier.MATURE,OutbreakRules.populationTier(2,6,0,0,0,6,18,0));
        assertEquals(OutbreakRules.Tier.ANCIENT,OutbreakRules.populationTier(3,6,2,2,0,6,18,0));
        assertEquals(OutbreakRules.Tier.FRESH,OutbreakRules.populationTier(4,10,5,4,2,6,18,0));
        assertEquals(OutbreakRules.Tier.FRESH,OutbreakRules.populationTier(4,1,0,0,0,6,1,0));
    }
    @Test void StagesUseElapsedReleaseTimeAndExactDayBoundaries(){
        assertEquals(0,OutbreakRules.stage(23999,24000));assertEquals(1,OutbreakRules.stage(24000,24000));
        assertEquals(2,OutbreakRules.stage(3*24000,24000));assertEquals(3,OutbreakRules.stage(7*24000,24000));
        assertEquals(4,OutbreakRules.stage(14*24000,24000));assertEquals(4,OutbreakRules.stage(Long.MAX_VALUE,24000));
        assertEquals(0,OutbreakRules.stage(-1,24000));
    }
    @Test void EveryLateStageKeepsEarlyInfectedAndAddsMatureBodiesInsteadOfOnlyFreshSpawns(){
        for(int stage=0;stage<=4;stage++){
            var tiers=new java.util.HashSet<OutbreakRules.Tier>();
            for(int i=0;i<100;i++)tiers.add(OutbreakRules.choose(stage,i));
            assertTrue(tiers.contains(OutbreakRules.Tier.FRESH));
            assertEquals(stage>=2,tiers.contains(OutbreakRules.Tier.HUNTER));
            assertEquals(stage>=3,tiers.contains(OutbreakRules.Tier.ANCIENT));
        }
    }
    @Test void BirthAgesHaveActualMatureAndAncientOrgans(){
        assertEquals(0,OutbreakRules.tissueAge(OutbreakRules.Tier.FRESH));
        assertEquals(1,TissueGrowth.generation(OutbreakRules.tissueAge(OutbreakRules.Tier.MATURE)));
        assertTrue(TissueGrowth.generation(OutbreakRules.tissueAge(OutbreakRules.Tier.ANCIENT))>=3);
    }
    @Test void LateWorldKeepsOrdinaryInfectedMajorityForBattleResources(){
        for(int stage=0;stage<=4;stage++){
            int common=0;
            for(int roll=0;roll<100;roll++)if(OutbreakRules.choose(stage,roll)==OutbreakRules.Tier.FRESH)common++;
            assertTrue(common>=60,"high stages must not exhaust ordinary consumable enemies");
        }
    }
    @Test void AdditionalBottlesDoNotResetTheClockAndSavingPreservesIt(){
        var data=new OutbreakData();assertTrue(data.start(100));assertFalse(data.start(2000));assertEquals(1900,data.elapsed(2000));
        var copy=OutbreakData.load(data.save(new net.minecraft.nbt.CompoundTag(),null),null);
        assertTrue(copy.active());assertEquals(1900,copy.elapsed(2000));assertEquals(0,copy.elapsed(50));
    }
    @Test void EarlyPopulationReservesFoodButAlsoStartsRealGrowingLineages(){
        assertEquals(OutbreakRules.Tier.FRESH,OutbreakRules.populationTier(0,5,0,0,0,6,18,99));
        assertEquals(OutbreakRules.Tier.GROWING,OutbreakRules.populationTier(0,6,0,0,0,6,18,0));
        int ordinary=0,growing=0;
        for(int roll=0;roll<100;roll++){
            var tier=OutbreakRules.populationTier(0,10,3,0,0,6,18,roll);
            if(tier==OutbreakRules.Tier.FRESH)ordinary++;
            else if(tier==OutbreakRules.Tier.GROWING)growing++;
            else fail("early ecology must not jump to mature hunters or ancient bodies");
        }
        assertEquals(75,ordinary);assertEquals(25,growing);
        assertEquals(0,OutbreakRules.lineageFloor(4,6,6));
        assertEquals(2,OutbreakRules.lineageFloor(0,6,18));
        assertEquals(5,OutbreakRules.lineageFloor(4,6,18));
        assertEquals(OutbreakRules.Tier.GROWING,OutbreakRules.ambientTier(0,0,0,0,0,6,18,99));
        assertEquals(OutbreakRules.Tier.FRESH,OutbreakRules.ambientTier(0,0,0,0,0,6,18,0));
        assertEquals(OutbreakRules.Tier.FRESH,OutbreakRules.ambientTier(0,0,0,0,0,6,6,99));
    }
}
