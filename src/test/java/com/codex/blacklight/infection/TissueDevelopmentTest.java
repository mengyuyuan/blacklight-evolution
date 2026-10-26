package com.codex.blacklight.infection;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TissueDevelopmentTest {
    @Test void PaidGrowthDevelopsInheritedOrgansWithoutInventingMealsOrDonors(){
        var organ=new TissueInheritance.Organ(100,0,0,0,TissueInheritance.BLADE,42,1);
        var before=new TissueInheritance(Map.of(TissueCore.BLADE,organ),6,123,false,null,1,6);
        var growing=before;
        for(int second=0;second<1200;second++)growing=growing.cultivate(20,TissueCore.MUSCLE,72000,TissueInheritance.Rules.DEFAULT);
        assertEquals(120,growing.organ(TissueCore.BLADE).integrated(),.000001);
        assertTrue(growing.scaleFactor()>before.scaleFactor());
        assertEquals(6,growing.meals());assertEquals(6,growing.acquiredOrgans());assertEquals(123,growing.points());
        assertEquals(42,growing.organ(TissueCore.BLADE).variant());
        assertEquals(TissueInheritance.BLADE,growing.organ(TissueCore.BLADE).weapons());
        assertFalse(growing.qualified());assertSame(before,before.cultivate(0,TissueCore.MUSCLE,72000,TissueInheritance.Rules.DEFAULT));
        var forming=new TissueInheritance(Map.of(TissueCore.BLADE,new TissueInheritance.Organ(100,20,10,10,1,42)),0,0,false,null,1);
        assertSame(forming,forming.cultivate(20,TissueCore.MUSCLE,72000,TissueInheritance.Rules.DEFAULT));
    }
    @Test void MatureFunctionalOrgansCanSupplyTheEvolvedsStructuralRequirements(){
        var organs=new EnumMap<TissueCore,TissueInheritance.Organ>(TissueCore.class);
        for(var core:new TissueCore[]{TissueCore.TENDON,TissueCore.HORN,TissueCore.PHARYNX,TissueCore.BLADE})
            organs.put(core,new TissueInheritance.Organ(300,0,0,0,1,core.ordinal()));
        var six=new TissueInheritance(organs,6,1000,false,null,1,6).digest(0,true,1,72000,TissueInheritance.Rules.DEFAULT,TissueCore.CREST);
        assertTrue(EvolvedForm.eligible(six,72000));
        var five=new TissueInheritance(organs,5,1000,false,null,1,5).digest(0,true,1,72000,TissueInheritance.Rules.DEFAULT,TissueCore.CREST);
        assertFalse(EvolvedForm.eligible(five,72000));
        organs.put(TissueCore.PHARYNX,new TissueInheritance.Organ(300,0,0,1,1,1));
        var immature=new TissueInheritance(organs,6,1000,false,null,1,6).digest(0,true,1,72000,TissueInheritance.Rules.DEFAULT,TissueCore.CREST);
        assertFalse(EvolvedForm.eligible(immature,72000));
    }
}
