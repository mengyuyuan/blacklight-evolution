package com.codex.blacklight.infection;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PopulationGuardTest {
    @Test void capsStopAtTheLimitAndZeroDisables(){
        assertTrue(PopulationGuard.below(23,24));
        assertFalse(PopulationGuard.below(24,24));
        assertTrue(PopulationGuard.below(500,0));
    }
    @Test void onlyIdleAmbientBirthsFarFromPlayersAreTrimmed(){
        assertTrue(PopulationGuard.trimmable(true,false,false,false,97*97,96));
        assertFalse(PopulationGuard.trimmable(true,false,false,false,60*60,96));
        assertFalse(PopulationGuard.trimmable(false,false,false,false,200*200,96));
        assertFalse(PopulationGuard.trimmable(true,true,false,false,200*200,96));
        assertFalse(PopulationGuard.trimmable(true,false,true,false,200*200,96));
        assertFalse(PopulationGuard.trimmable(true,false,false,true,200*200,96));
    }
}
