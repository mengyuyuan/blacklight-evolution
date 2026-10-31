package com.codex.blacklight.military.vehicle;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class VehicleFuelDeactivationTest {
    @Test void nativeInitialIssueAllocatesAmmunitionOnceWithoutIssuingFuel() {
        var catalog=VehicleCatalog.loadDefault();
        for(String id:new String[]{"truck","apc","tank"}) {
            var definition=catalog.require(id);var inventory=new VehicleInventory(definition);
            assertTrue(inventory.issueInitialLoadout());
            assertEquals(0,inventory.fuelTicks(),"native initial issue must not mint fuel for "+id);
            assertEquals(definition.ammunitionCapacities(),inventory.snapshot().ammunition());
            var restored=VehicleInventory.restore(definition,inventory.snapshot());
            assertFalse(restored.issueInitialLoadout());assertEquals(inventory.snapshot(),restored.snapshot());
        }
    }
    @Test void legacyFuelSnapshotAndDormantFiniteFuelApiRemainCompatible() {
        var definition=VehicleCatalog.loadDefault().require("tank");var legacy=new VehicleInventory(definition);
        assertEquals(137,legacy.addFuel(137));var saved=legacy.snapshot();
        var restored=VehicleInventory.restore(definition,saved);
        assertEquals(saved,restored.snapshot());assertEquals(137,restored.fuelTicks());
        assertFalse(restored.issueInitialLoadout());assertEquals(137,restored.fuelTicks());
        assertTrue(restored.consumeFuel(7));assertEquals(130,restored.fuelTicks());
        assertFalse(restored.consumeFuel(131));assertEquals(130,restored.fuelTicks());
    }
}
