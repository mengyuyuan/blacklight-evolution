package com.codex.blacklight.combat;

import com.codex.blacklight.power.*;
import com.codex.blacklight.state.BlacklightState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuillHarvestTest {
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
