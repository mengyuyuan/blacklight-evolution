package com.codex.blacklight.combat;

import com.codex.blacklight.power.*;
import com.codex.blacklight.state.BlacklightState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuillHarvestTest {
    @Test void waveRoundsAcrossCarriersAndDoesNotOverflowAtMaximumRank(){
        var wave=new QuillFormation.Wave(40);
        assertEquals(2,wave.take(3)+wave.take(1)+wave.take(1));
        assertEquals(3_689_348_814_741_910_323L,new QuillFormation.Wave(40).take(Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE,new QuillFormation.Wave(100).take(Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE,QuillFormation.harvestCount(Long.MAX_VALUE));
        assertEquals(1,QuillFormation.harvestCount(0));
        assertEquals(65,QuillFormation.harvestCount(64));
    }

    @org.junit.jupiter.api.BeforeAll static void bootstrapMinecraft(){
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void highRanksAddUprightAndCrossedOrbits(){
        var flat=QuillFormation.offset(0,5,0,2,0);
        assertEquals(2,flat.x,1e-8);
        var upright=QuillFormation.offset(2,24,0,2,0);
        assertTrue(Math.abs(upright.y)>.8);
        var crossed=QuillFormation.offset(3,24,0,2,0);
        assertTrue(Math.abs(crossed.x)>.8 && Math.abs(crossed.z)>.7);
        for(int i=0;i<48;i++){
            var offset=QuillFormation.offset(i,48,50,2,35);
            assertTrue(offset.y>=-1 && offset.y<=1);
            for(int j=0;j<i;j++)assertTrue(offset.distanceToSqr(QuillFormation.offset(j,48,50,2,35))>1e-8);
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
