package com.codex.blacklight.infection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TissueMetabolismTest {
    @Test void reservePaysForActivityInsteadOfDecayingAtRest(){
        var idle=TissueMetabolism.budget(15,35,TissueMetabolism.demand(48000,false,false));
        assertEquals(new TissueMetabolism.Budget(15,35,false),idle);
        var b=new TissueMetabolism.Budget(8,0,false);
        for(int second=0;second<20;second++)b=TissueMetabolism.budget(b.reserve(),b.debt(),TissueMetabolism.demand(0,false,false));
        assertEquals(7,b.reserve());assertEquals(20,b.debt());
        assertEquals(10,TissueMetabolism.demand(48000,true,false));assertEquals(20,TissueMetabolism.demand(48000,false,true));
        assertFalse(TissueMetabolism.budget(0,95,30).supplied());
    }
    @Test void newInfectionsHaveABoundedBodyReserveAndFractionalCostsCannotBeForgiven(){
        assertEquals(8,TissueMetabolism.initialReserve(10));assertEquals(10,TissueMetabolism.initialReserve(20));
        assertEquals(24,TissueMetabolism.initialReserve(Float.MAX_VALUE));
        assertEquals(new TissueMetabolism.Budget(0,0,true,.5),TissueMetabolism.budget(1,95,10));
        var b=TissueMetabolism.budget(5,95,10);assertEquals(4,b.reserve());assertEquals(5,b.debt());
        assertTrue(TissueMetabolism.growing(48000,true));assertFalse(TissueMetabolism.growing(48000,false));
    }
    @Test void stageFoodIncreasesAndWorldDaysAccelerateBiologyWithoutMoreSimulationLoops(){
        assertEquals(4,TissueMetabolism.phaseFood(0));assertEquals(7,TissueMetabolism.phaseFood(12000));
        assertEquals(10,TissueMetabolism.phaseFood(24000));assertEquals(13,TissueMetabolism.phaseFood(48000));
        assertEquals(16,TissueMetabolism.phaseFood(72000));
        assertEquals(1,TissueMetabolism.worldSpeed(23999));assertEquals(2,TissueMetabolism.worldSpeed(7*24000));assertEquals(3,TissueMetabolism.worldSpeed(28*24000));
        assertEquals(60,TissueMetabolism.scaledTicks(20,TissueMetabolism.worldSpeed(28*24000)));
        assertTrue(TissueMetabolism.demand(72000,true,false,3)>TissueMetabolism.demand(48000,true,false,1));
        assertTrue(Double.isFinite(TissueMetabolism.worldSpeed(Long.MAX_VALUE)));
        assertEquals(0,TissueMetabolism.initialReserve(Float.NaN));
    }
    @Test void foodStorageAndEditedPreyNeverOverflowOrProduceUnlimitedMeals(){
        assertEquals(5,TissueMetabolism.meal(20,TissueCore.MUSCLE));
        assertEquals(7,TissueMetabolism.meal(20,TissueCore.JAW));
        assertEquals(60,TissueMetabolism.meal(Float.MAX_VALUE,TissueCore.JAW));
        for(float health:new float[]{0,-10,Float.NaN,Float.POSITIVE_INFINITY})assertEquals(0,TissueMetabolism.meal(health,TissueCore.JAW));
        assertEquals(TissueMetabolism.MAX_RESERVE,TissueMetabolism.add(Integer.MAX_VALUE,Integer.MAX_VALUE));
        assertEquals(0,TissueMetabolism.add(Integer.MIN_VALUE,-5));
        assertEquals(15,TissueMetabolism.add(15,-10));
    }
    @Test void combatIsContinuousMonotonicAndFiniteWithNoFinalGeneration(){
        for(long seed=0;seed<100;seed++){
            assertEquals(new TissueEvolution.Combat(0,0,0,0),TissueEvolution.combat(seed,0));
            assertTrue(TissueEvolution.combat(seed,12000).health()>=1.8,"mature organs already support a sustained fight");
            var previous=TissueEvolution.combat(seed,48000);
            for(int generation=1;generation<100;generation++){
                var current=TissueEvolution.combat(seed,48000+generation*24000L);
                assertTrue(current.health()>previous.health());assertTrue(current.damage()>previous.damage());
                assertTrue(current.armor()>previous.armor());assertTrue(current.knockback()>previous.knockback());
                previous=current;
            }
            var distant=TissueEvolution.combat(seed,Long.MAX_VALUE);
            assertTrue(Double.isFinite(distant.health())&&Double.isFinite(distant.damage())&&Double.isFinite(distant.armor()));
            assertTrue(distant.health()>previous.health()&&distant.damage()>previous.damage());
            assertTrue(distant.knockback()>=0&&distant.knockback()<.6);
            for(long age:new long[]{48000,72000,240000,816000}){
                var a=TissueEvolution.combat(seed,age-1);var b=TissueEvolution.combat(seed,age+1);
                assertEquals(a.health(),b.health(),.0002);assertEquals(a.damage(),b.damage(),.00003);
            }
        }
    }
    @Test void primaryOrganChangesCombatAllocationRatherThanRerollingItEveryGeneration(){
        double carapaceArmor=0,otherArmor=0,muscleDamage=0,otherDamage=0,crestResistance=0,otherResistance=0;
        int carapaceCount=0,muscleCount=0,crestCount=0;
        for(long seed=0;seed<1000;seed++){
            var growth=TissueEvolution.combat(seed,816000);var core=TissueCore.forSeed(seed);
            if(core==TissueCore.CARAPACE){carapaceArmor+=growth.armor();carapaceCount++;}else otherArmor+=growth.armor();
            if(core==TissueCore.MUSCLE){muscleDamage+=growth.damage();muscleCount++;}else otherDamage+=growth.damage();
            if(core==TissueCore.CREST){crestResistance+=growth.knockback();crestCount++;}else otherResistance+=growth.knockback();
        }
        assertTrue(carapaceArmor/carapaceCount>otherArmor/(1000-carapaceCount)*2);
        assertTrue(muscleDamage/muscleCount>otherDamage/(1000-muscleCount));
        assertTrue(crestResistance/crestCount>otherResistance/(1000-crestCount)*2);
    }
}
