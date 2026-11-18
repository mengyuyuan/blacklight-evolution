package com.codex.blacklight.military.vehicle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VehicleCombatSpacingTest {
    @Test void closeThreatRegroupsAndDoesNotOscillateAtTenBlocks(){
        assertTrue(VehicleCombatSpacing.wantsRetreat(9.9,false));assertFalse(VehicleCombatSpacing.wantsRetreat(10,false));
        assertTrue(VehicleCombatSpacing.wantsRetreat(12,true));assertFalse(VehicleCombatSpacing.wantsRetreat(15,true));
        assertFalse(VehicleCombatSpacing.wantsRetreat(Double.NaN,true));assertFalse(VehicleCombatSpacing.wantsRetreat(-1,true));
    }
}
