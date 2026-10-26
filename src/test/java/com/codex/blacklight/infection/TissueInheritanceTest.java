package com.codex.blacklight.infection;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TissueInheritanceTest {
    @org.junit.jupiter.api.Test void onlyFullyDevelopedAndDigestedHostsCanStartAnotherHunt(){
        org.junit.jupiter.api.Assertions.assertFalse(TissueInheritance.EMPTY.readyToHunt(TissueGrowth.MATURITY_TICKS));
        org.junit.jupiter.api.Assertions.assertTrue(TissueInheritance.EMPTY.readyToHunt(TissueGrowth.ANCIENT_TICKS));
        var pending=new TissueInheritance(java.util.Map.of(TissueCore.BLADE,new TissueInheritance.Organ(10,20,3600,3600,1,42)),1,20,false,null,1);
        org.junit.jupiter.api.Assertions.assertFalse(pending.readyToHunt(504000));
        org.junit.jupiter.api.Assertions.assertTrue(pending.digest(3600,true,1,504000,TissueInheritance.Rules.DEFAULT).readyToHunt(504000));
        var forming=new TissueInheritance(java.util.Map.of(),0,0,false,null,0,0,java.util.Map.of(),java.util.Map.of("minecraft:zombie",new TissueInheritance.BodySource(0,42,600,1)));
        org.junit.jupiter.api.Assertions.assertFalse(forming.readyToHunt(504000));
        org.junit.jupiter.api.Assertions.assertTrue(forming.digest(600,true,1,504000,TissueInheritance.Rules.DEFAULT).readyToHunt(504000));
    }
    private static long seed(TissueCore core){long seed=0;while(TissueCore.forSeed(seed)!=core)seed++;return seed;}
    private static final long MUSCLE=seed(TissueCore.MUSCLE),GRASP=seed(TissueCore.GRASP);
    @Test void fusionUsesEffectiveDonorStrengthAndExistingCommittedStrength(){
        var value=TissueInheritance.EMPTY.inherit(UUID.randomUUID(),TissueInheritance.EMPTY,MUSCLE,216000,MUSCLE,1080000,TissueInheritance.Rules.DEFAULT);
        assertEquals(93.3333333333,value.organ(TissueCore.MUSCLE).pending(),.000001);
        assertEquals(0,value.organ(TissueCore.MUSCLE).integrated());
        var newCore=TissueInheritance.EMPTY.inherit(UUID.randomUUID(),TissueInheritance.EMPTY,MUSCLE,216000,GRASP,504000,TissueInheritance.Rules.DEFAULT);
        assertEquals(105,newCore.organ(TissueCore.GRASP).pending(),.000001);
        assertEquals(.2,newCore.organ(TissueCore.GRASP).maturity(),.000001);
    }
    @Test void aVictimContributesOneMealRegardlessOfItsAncestryOrOrganCount(){
        var donor=new TissueInheritance(Map.of(TissueCore.JAW,new TissueInheritance.Organ(300,0,0,0,0,1)),999,99999,true,null,80);
        var operation=UUID.randomUUID();var value=TissueInheritance.EMPTY.inherit(operation,donor,MUSCLE,216000,GRASP,504000,TissueInheritance.Rules.DEFAULT);
        assertEquals(1,value.meals());assertEquals(2,value.organs().size());assertEquals(2,value.acquiredOrgans());assertFalse(value.qualified());
        assertSame(value,value.inherit(operation,donor,MUSCLE,216000,GRASP,504000,TissueInheritance.Rules.DEFAULT));
    }
    @Test void feedingDoesNotInstantlyGiveAttributesOrCopyAnAlreadyQualifiedDonor(){
        var donor=new TissueInheritance(Map.of(),1000,100000,true,null,1);
        var value=TissueInheritance.EMPTY.inherit(UUID.randomUUID(),donor,MUSCLE,216000,GRASP,504000,TissueInheritance.Rules.DEFAULT);
        assertEquals(TissueInheritance.EMPTY.combat(),value.combat());assertFalse(value.qualified());
        assertEquals(0,value.strength(TissueCore.GRASP,TissueCore.MUSCLE,216000));
        var fed=value.digest(1800,true,MUSCLE,216000,TissueInheritance.Rules.DEFAULT);
        assertEquals(52.5,fed.organ(TissueCore.GRASP).integrated(),.000001);assertEquals(.6,fed.organ(TissueCore.GRASP).maturity(),.000001);
        var starved=value.digest(9000,false,MUSCLE,216000,TissueInheritance.Rules.DEFAULT);
        assertEquals(fed.organs(),starved.organs());
        var mature=value.digest(18000,false,MUSCLE,216000,TissueInheritance.Rules.DEFAULT);
        assertEquals(105,mature.organ(TissueCore.GRASP).integrated(),.000001);assertTrue(mature.organ(TissueCore.GRASP).mature());
    }
    @Test void repeatedSmallDonorsNeverWeakenAnOrganOrGrantUnlimitedInstantPendingStrength(){
        var value=TissueInheritance.EMPTY;
        double previous=0;
        for(int i=0;i<1000;i++){
            value=value.inherit(UUID.randomUUID(),TissueInheritance.EMPTY,MUSCLE,216000,GRASP,72000,TissueInheritance.Rules.DEFAULT);
            double strength=value.organ(TissueCore.GRASP).pending();assertTrue(strength>previous);previous=strength;
        }
        assertTrue(previous<35000);assertEquals(0,value.organ(TissueCore.GRASP).integrated());
        value=value.digest(3600,true,MUSCLE,216000,TissueInheritance.Rules.DEFAULT);
        assertTrue(value.strength(TissueCore.GRASP,TissueCore.MUSCLE,216000)>300);
        var copy=value.digest(0,true,MUSCLE,216000,TissueInheritance.Rules.DEFAULT);assertSame(value,copy);
    }
    private static TissueInheritance gate(long count,double points,boolean immature,TissueCore missing){
        var organs=new EnumMap<TissueCore,TissueInheritance.Organ>(TissueCore.class);
        for(var core:new TissueCore[]{TissueCore.CARAPACE,TissueCore.JAW,TissueCore.GRASP})if(core!=missing)
            organs.put(core,new TissueInheritance.Organ(300,0,0,immature?1:0,0,core.ordinal()));
        return new TissueInheritance(organs,count,points,false,null,1,count).digest(0,true,MUSCLE,504000,TissueInheritance.Rules.DEFAULT);
    }
    @Test void qualificationRequiresSixMaturePersonalMealsWithoutForcingBalancedLegacyOrgans(){
        assertTrue(gate(6,1000,false,null).qualified());
        assertFalse(gate(5,1000,false,null).qualified());assertTrue(gate(6,0,false,null).qualified());
        assertFalse(gate(6,1000,true,null).qualified());
        for(var core:new TissueCore[]{TissueCore.CARAPACE,TissueCore.JAW,TissueCore.GRASP})assertTrue(gate(6,1000,false,core).qualified());
        assertFalse(TissueInheritance.EMPTY.digest(3600,true,MUSCLE,Long.MAX_VALUE,TissueInheritance.Rules.DEFAULT).qualified());
    }
    @Test void milestoneLatchesAndContinuesAcquiringAfterQualification(){
        var value=gate(6,1000,false,null);
        var more=value.inherit(UUID.randomUUID(),TissueInheritance.EMPTY,MUSCLE,504000,GRASP,504000,TissueInheritance.Rules.DEFAULT).digest(3600,true,MUSCLE,504000,TissueInheritance.Rules.DEFAULT);
        assertTrue(more.qualified());assertEquals(7,more.meals());assertEquals(7,more.acquiredOrgans());assertTrue(more.organ(TissueCore.GRASP).integrated()>300);
    }
    @Test void exactlyFifteenPercentDeadAndInvalidTargetsAreRejected(){
        assertTrue(TissueInheritance.weakened(14.99,100));assertFalse(TissueInheritance.weakened(15,100));
        assertFalse(TissueInheritance.weakened(0,100));assertFalse(TissueInheritance.weakened(-1,100));
        assertFalse(TissueInheritance.weakened(Double.NaN,100));assertFalse(TissueInheritance.weakened(1,Double.POSITIVE_INFINITY));
    }
    @Test void compatibleVolumeAllowsWideSpidersWithoutAllowingGiantUnrelatedBodies(){
        assertTrue(TissueInheritance.sizeFits(1.1,3.5,3,1.9));
        assertTrue(TissueInheritance.sizeFits(2,2,1,4));
        assertFalse(TissueInheritance.sizeFits(1,2,8,8));
        assertFalse(TissueInheritance.sizeFits(1,2,Double.NaN,1));
        assertFalse(TissueInheritance.sizeFits(0,2,1,1));
    }
    @Test void combatIsFiniteMonotoneAndIndependentOfNativeAgeModifiers(){
        var value=TissueInheritance.EMPTY;double damage=0,health=0;
        for(int i=0;i<50;i++){
            value=value.inherit(UUID.randomUUID(),TissueInheritance.EMPTY,MUSCLE,216000,MUSCLE,1080000,TissueInheritance.Rules.DEFAULT).digest(3600,true,MUSCLE,216000,TissueInheritance.Rules.DEFAULT);
            assertTrue(value.combat().damage()>damage);assertTrue(value.combat().health()>health);
            damage=value.combat().damage();health=value.combat().health();assertTrue(Double.isFinite(damage));
        }
        assertEquals(0,TissueInheritance.EMPTY.combat().health());
        var slot=new TissueInheritance.Organ(Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NaN,255,1);
        assertEquals(0,slot.integrated());assertEquals(0,slot.pending());assertEquals(7,slot.weapons());
        assertTrue(Double.isFinite(TissueInheritance.nativeStrength(Long.MAX_VALUE)));
    }
    @Test void armourDoesNotInventAnOffensiveWeaponAndPartialOrgansRemainUndeployed(){
        var armour=new TissueInheritance(Map.of(TissueCore.CARAPACE,new TissueInheritance.Organ(1000,0,0,0,0,1)),1,500,false,null,1);
        assertEquals(TissueInheritance.nativeWeapons(MUSCLE,216000),armour.weapons(MUSCLE,216000));
        var partial=new TissueInheritance(Map.of(TissueCore.JAW,new TissueInheritance.Organ(300,0,0,1,TissueInheritance.CLAWS,1)),1,500,false,null,1);
        assertEquals(0,partial.weapons(MUSCLE,0)&TissueInheritance.CLAWS);
    }
    @Test void organsKeepTheirActualDonorBlueprintWhenAFusedCreatureIsConsumed(){
        var donor=new TissueInheritance(Map.of(TissueCore.JAW,new TissueInheritance.Organ(300,0,0,0,0,927,2)),9,900,false,null,4);
        var inherited=TissueInheritance.EMPTY.inherit(UUID.randomUUID(),donor,MUSCLE,216000,GRASP,504000,TissueInheritance.Rules.DEFAULT,1);
        assertEquals(2,inherited.organ(TissueCore.JAW).donorPlan());assertEquals(927,inherited.organ(TissueCore.JAW).variant());
        assertEquals(1,inherited.organ(TissueCore.GRASP).donorPlan());assertEquals(1,inherited.meals());
        var mature=inherited.digest(3600,true,MUSCLE,216000,TissueInheritance.Rules.DEFAULT);
        assertEquals(2,mature.organ(TissueCore.JAW).donorPlan());
    }
    @Test void sixActualDonorsAreSixOrgansEvenWhenSomeCategoriesRepeat(){
        var value=TissueInheritance.EMPTY;
        TissueCore[] sources={TissueCore.GRASP,TissueCore.CARAPACE,TissueCore.JAW,TissueCore.MUSCLE,TissueCore.CREST,TissueCore.GRASP};
        for(int i=0;i<sources.length;i++){
            var operation=UUID.randomUUID();value=value.inherit(operation,TissueInheritance.EMPTY,MUSCLE,216000,seed(sources[i]),504000,TissueInheritance.Rules.DEFAULT,i%3);
            assertEquals(i+1,value.meals());assertEquals(i+1,value.acquiredOrgans());
            assertSame(value,value.inherit(operation,TissueInheritance.EMPTY,MUSCLE,216000,seed(sources[i]),504000,TissueInheritance.Rules.DEFAULT));
            value=value.digest(3600,true,MUSCLE,216000,TissueInheritance.Rules.DEFAULT);
        }
        assertEquals(5,value.organs().size());assertEquals(6,value.acquiredOrgans());
        assertTrue(value.qualified(),"six actually consumed, integrated mature sources unlock the bias-selected next form");
    }
    @Test void fiveAcquiredOrgansCannotAdvanceEvenWithHugeAncestryAndPerfectStats(){
        var ready=gate(6,1000,false,null);
        var five=new TissueInheritance(ready.organs(),999,999999,true,null,1,5).digest(0,true,MUSCLE,504000,TissueInheritance.Rules.DEFAULT);
        assertFalse(five.qualified());
        var six=new TissueInheritance(ready.organs(),6,1000,false,null,1,6).digest(0,true,MUSCLE,504000,TissueInheritance.Rules.DEFAULT);
        assertTrue(six.qualified());
        var legacy=new TissueInheritance(ready.organs(),999,999999,true,null,1);
        assertEquals(3,legacy.acquiredOrgans());assertFalse(legacy.qualified(),"old saves cannot invent missing source history");
    }
    @Test void unreceivedOrImmatureDonatedOrgansCannotMintProgress(){
        var noFusion=TissueInheritance.EMPTY.inherit(UUID.randomUUID(),TissueInheritance.EMPTY,MUSCLE,216000,GRASP,504000,new TissueInheritance.Rules(0,300,6,1000));
        assertEquals(0,noFusion.acquiredOrgans());
        var donor=new TissueInheritance(Map.of(TissueCore.JAW,new TissueInheritance.Organ(300,0,0,10,0,2)),999,99999,false,null,1,999);
        var received=TissueInheritance.EMPTY.inherit(UUID.randomUUID(),donor,MUSCLE,216000,GRASP,504000,TissueInheritance.Rules.DEFAULT);
        assertEquals(1,received.acquiredOrgans(),"only the donor's mature born organ is counted; never its ancestor total");
        assertEquals(2,received.organs().size(),"immature tissue can still contribute slowly without qualifying as a collected mature source");
    }
}
