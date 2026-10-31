package com.codex.blacklight.military.vehicle;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleGarageBoardingTest {
    @Test void reachableGroundCrewPrecedeTowerGuardsRegardlessOfNativeSlotOrder() {
        var tower=java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
        var groundFar=java.util.UUID.fromString("00000000-0000-0000-0000-000000000002");
        var groundNear=java.util.UUID.fromString("00000000-0000-0000-0000-000000000003");
        var positions=new java.util.LinkedHashMap<java.util.UUID,Vec3>();
        positions.put(tower,new Vec3(1,88,1));positions.put(groundFar,new Vec3(30,81,30));positions.put(groundNear,new Vec3(4,81,4));
        assertEquals(java.util.List.of(groundNear,groundFar,tower),VehicleGarageService.prioritizeCrew(positions,new Vec3(0,81,0)));
    }
    @Test void fullRegistrationLedgerRejectsPlacementBeforePhysicalTemplatesAndBoxesAreWritten() {
        var ledger=new VehicleGarageData();
        assertTrue(VehicleGarageService.canAddGarage(ledger));
        for(int i=0;i<2048;i++)assertTrue(ledger.register("garage"+i,"base","minecraft:overworld",com.codex.blacklight.military.MilitaryFaction.MILITARY,new VehicleGarageData.Site(i*40,80,0,0)));
        assertFalse(VehicleGarageService.canAddGarage(ledger));
    }
    @Test void finalBoardingWaypointDoesNotStopTheSoldierOutsideThreeBlockReach() {
        var side=new Vec3(2.8,81,0);var outside=new Vec3(3.3,81,0);
        assertFalse(VehicleGarageService.reachedBoardingWaypoint(outside,side,true));
        assertTrue(VehicleGarageService.reachedBoardingWaypoint(outside,side,false));
        assertTrue(VehicleGarageService.reachedBoardingWaypoint(new Vec3(2.9,81,0),side,true));
    }
}
