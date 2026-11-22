package com.codex.blacklight.combat;

import com.codex.blacklight.power.*;
import com.codex.blacklight.state.BlacklightState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuillHarvestTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrapMinecraft(){
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void capacityLifetimeAndVolleyRoundIndependently(){
        assertEquals(6,QuillFormation.capacity(0));
        assertEquals(70,QuillFormation.capacity(64));
        assertEquals(Long.MAX_VALUE,QuillFormation.capacity(Long.MAX_VALUE));
        assertEquals(240,QuillFormation.extendLife(240,0));
        assertEquals(300,QuillFormation.extendLife(240,3));
        assertEquals(Long.MAX_VALUE,QuillFormation.extendLife(240,Long.MAX_VALUE));
        assertEquals(5,QuillFormation.volleySize(11,40));
        assertEquals(11,QuillFormation.volleySize(11,100));
        assertEquals(Integer.MAX_VALUE,QuillFormation.volleySize(Integer.MAX_VALUE,100));
    }

    @Test void highRanksAddUprightAndCrossedOrbits(){
        var flat=QuillFormation.offset(0,5,0,2);
        assertEquals(2,flat.x,1e-8);
        var upright=QuillFormation.offset(2,24,0,2);
        assertTrue(Math.abs(upright.y)>.8);
        var crossed=QuillFormation.offset(3,24,0,2);
        assertTrue(Math.abs(crossed.x)>.8 && Math.abs(crossed.z)>.7);
        for(int i=0;i<48;i++){
            var offset=QuillFormation.offset(i,48,50,2);
            assertTrue(offset.y>=-1 && offset.y<=1);
            for(int j=0;j<i;j++)assertTrue(offset.distanceToSqr(QuillFormation.offset(j,48,50,2))>1e-8);
        }
    }
    @Test void eachMarkDrainsOneHundredthOfOnePercentPerSecond() {
        assertEquals(.002,QuillHarvest.drainFraction(10,.01,1,2),1e-12);
        assertEquals(.012,QuillHarvest.drainFraction(10,.01,1,12),1e-12);
        assertEquals(.0001, QuillHarvest.drainFraction(1,.01,.25,1),1e-12);
        assertEquals(2, 2000*QuillHarvest.drainFraction(10,.01,.25,1),1e-12);
        assertEquals(.00125, QuillHarvest.drainFraction(10,.01,.25,2),1e-12);
        assertTrue(QuillHarvest.drainFraction(10,.01,.25,100)> .02);
    }
    @Test void harvestUpgradesSpendEpOnceAndRequireTheOrgan(){
        var s=new BlacklightState();s.infect();s.addEvolutionPoints(1000);
        assertFalse(s.buyCombatSkill(CombatSkill.QUILL_HARVEST));
        s.unlock(PowerForm.BACKSPINES);
        assertTrue(s.buyCombatSkill(CombatSkill.QUILL_HARVEST));
        int after=s.getEvolutionPoints();
        assertFalse(s.buyCombatSkill(CombatSkill.QUILL_HARVEST));
        assertEquals(after,s.getEvolutionPoints());
        assertEquals(350,CombatSkill.QUILL_HARVEST.epCost());
    }
}
