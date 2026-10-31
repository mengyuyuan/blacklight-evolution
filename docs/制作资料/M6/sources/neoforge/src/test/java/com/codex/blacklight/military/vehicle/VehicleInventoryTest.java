package com.codex.blacklight.military.vehicle;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VehicleInventoryTest {
    VehicleDefinition tank() { return VehicleCatalog.loadDefault().require("tank"); }
    @Test void anEmptyChassisDoesNotMintAmmunitionOrFuel() {
        var inventory = new VehicleInventory(tank());
        assertEquals(0, inventory.fuelTicks()); assertEquals(0, inventory.ammunition("tank_he_shell"));
        assertFalse(inventory.tryFire("tank_he", 0));
        assertFalse(inventory.consumeFuel(1));
    }
    @Test void oneExplicitInitialAllocationCannotBeRepeatedAfterSpendingAndReloading() {
        var d = tank(); var inventory = new VehicleInventory(d);
        assertTrue(inventory.issueInitialLoadout());
        assertEquals(12, inventory.ammunition("tank_he_shell")); assertEquals(8, inventory.ammunition("tank_ap_shell"));
        assertEquals(240, inventory.ammunition("machine_gun_round")); assertEquals(0, inventory.fuelTicks());
        assertEquals(7200, inventory.addFuel(7200));
        assertTrue(inventory.tryFire("tank_he", 40)); assertTrue(inventory.consumeFuel(500));
        var restored = VehicleInventory.restore(d, inventory.snapshot());
        assertFalse(restored.issueInitialLoadout());
        assertEquals(11, restored.ammunition("tank_he_shell")); assertEquals(6700, restored.fuelTicks());
        assertFalse(restored.tryFire("tank_he", 119));
        assertTrue(restored.tryFire("tank_he", 120));
    }
    @Test void matchingResourcesAreCappedWithoutConvertingOtherAmmunition() {
        var inventory = new VehicleInventory(tank());
        assertEquals(12, inventory.addAmmunition("tank_he_shell", 99));
        assertEquals(8, inventory.addAmmunition("tank_ap_shell", 99));
        assertEquals(0, inventory.addAmmunition("rocket_round", 4));
        assertEquals(0, inventory.addAmmunition("tank_he_shell", 1));
        assertEquals(7200, inventory.addFuel(Long.MAX_VALUE)); assertEquals(0, inventory.addFuel(1));
        assertTrue(inventory.tryFire("tank_he", 0)); assertEquals(11, inventory.ammunition("tank_he_shell"));
        assertEquals(8, inventory.ammunition("tank_ap_shell"));
        assertThrows(IllegalArgumentException.class, () -> inventory.addFuel(-1));
        assertThrows(IllegalArgumentException.class, () -> inventory.addAmmunition("tank_he_shell", -1));
        assertThrows(IllegalArgumentException.class, () -> inventory.consumeFuel(-1));
    }
    @Test void tankShellSelectionSharesOneRealBreechCooldown() {
        var inventory = new VehicleInventory(tank()); inventory.issueInitialLoadout();
        assertTrue(inventory.tryFire("tank_he", 0));
        assertFalse(inventory.tryFire("tank_ap", 1));
        assertTrue(inventory.tryFire("tank_ap", 80));
        assertEquals(11, inventory.ammunition("tank_he_shell")); assertEquals(7, inventory.ammunition("tank_ap_shell"));
    }
    @Test void invalidPersistedResourcesCannotBeClampedIntoASecondStockTruth() {
        var d = tank();
        assertThrows(IllegalArgumentException.class, () -> VehicleInventory.restore(d, new VehicleInventory.Snapshot(1,"apc",false,0,Map.of(),Map.of())));
        assertThrows(IllegalArgumentException.class, () -> VehicleInventory.restore(d, new VehicleInventory.Snapshot(99,"tank",false,0,Map.of(),Map.of())));
        assertThrows(IllegalArgumentException.class, () -> VehicleInventory.restore(d, new VehicleInventory.Snapshot(1,"tank",false,-1,Map.of(),Map.of())));
        assertThrows(IllegalArgumentException.class, () -> VehicleInventory.restore(d, new VehicleInventory.Snapshot(1,"tank",false,0,Map.of("tank_he_shell",13),Map.of())));
        assertThrows(IllegalArgumentException.class, () -> VehicleInventory.restore(d, new VehicleInventory.Snapshot(1,"tank",false,0,Map.of("rocket_round",1),Map.of())));
        assertThrows(IllegalArgumentException.class, () -> VehicleInventory.restore(d, new VehicleInventory.Snapshot(1,"tank",false,0,Map.of(),Map.of("tank_he",-1L))));
    }
    @Test void autocannonAndRocketHaveSeparateFiniteCapacitiesAndIntervals() {
        var inventory = new VehicleInventory(VehicleCatalog.loadDefault().require("apc")); inventory.issueInitialLoadout();
        assertTrue(inventory.tryFire("autocannon", 0)); assertFalse(inventory.tryFire("autocannon",5));
        assertTrue(inventory.tryFire("autocannon",6)); assertEquals(118,inventory.ammunition("autocannon_round"));
        for (int i=0;i<4;i++) assertTrue(inventory.tryFire("vehicle_rocket",i*30));
        assertFalse(inventory.tryFire("vehicle_rocket",120)); assertEquals(0,inventory.ammunition("rocket_round"));
    }
    @Test void manualSuppliesAndPersistedAllocationFlagCannotBeUsedToMintANewLoadout() {
        var d=tank(); var inventory=new VehicleInventory(d);
        assertEquals(1,inventory.addAmmunition("tank_he_shell",1)); assertTrue(inventory.tryFire("tank_he",0));
        assertFalse(inventory.issueInitialLoadout()); assertEquals(0,inventory.fuelTicks());
        assertThrows(IllegalArgumentException.class,()->VehicleInventory.restore(d,new VehicleInventory.Snapshot(1,"tank",false,1,Map.of(),Map.of())));
        assertThrows(IllegalArgumentException.class,()->VehicleInventory.restore(d,new VehicleInventory.Snapshot(1,"tank",false,0,Map.of("tank_he_shell",1),Map.of())));
    }
    @Test void schemaOneCannotLoseCooldownKeysAndSilentlyReloadTheBreech() {
        var d=tank(); var original=new VehicleInventory(d); original.issueInitialLoadout();
        assertTrue(original.tryFire("tank_he",0)); var saved=original.snapshot();
        assertThrows(IllegalArgumentException.class,()->VehicleInventory.restore(d,
                new VehicleInventory.Snapshot(1,"tank",true,saved.fuelTicks(),saved.ammunition(),Map.of())));
        assertThrows(IllegalArgumentException.class,()->VehicleInventory.restore(d,
                new VehicleInventory.Snapshot(1,"tank",true,saved.fuelTicks(),saved.ammunition(),Map.of("coaxial_machine_gun",0L))));
        var missingSecondary=new java.util.HashMap<>(saved.cooldownUntil()); missingSecondary.remove("coaxial_machine_gun");
        assertThrows(IllegalArgumentException.class,()->VehicleInventory.restore(d,
                new VehicleInventory.Snapshot(1,"tank",true,saved.fuelTicks(),saved.ammunition(),missingSecondary)));
        var valid=VehicleInventory.restore(d,saved);
        assertFalse(valid.tryFire("tank_ap",1)); assertEquals(80,valid.cooldownUntil("tank_ap"));
        assertEquals(11,valid.ammunition("tank_he_shell")); assertFalse(valid.issueInitialLoadout());
        var unissued=VehicleInventory.restore(d,new VehicleInventory(d).snapshot());
        assertTrue(unissued.issueInitialLoadout());
    }
}
