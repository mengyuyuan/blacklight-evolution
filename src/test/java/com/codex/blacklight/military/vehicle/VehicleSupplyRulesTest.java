package com.codex.blacklight.military.vehicle;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleSupplyRulesTest {
    private final VehicleCatalog catalog=VehicleCatalog.loadDefault();
    @Test void activeReturnCollectsOnlyTheRealItemsNeededForDepartureRatherThanFillingEverything() {
        var tank=catalog.require("tank");var stock=new VehicleInventory(tank);stock.addAmmunition("tank_he_shell",8);
        assertEquals(2,VehicleSupplyRules.resupplyDeficit(tank,stock,250,"tank_he_shell"));
        assertEquals(2,VehicleSupplyRules.resupplyDeficit(tank,stock,250,"tank_ap_shell"));
        assertEquals(1,VehicleSupplyRules.resupplyDeficit(tank,stock,250,"vehicle_repair_parts"));
        assertEquals(0,VehicleSupplyRules.resupplyDeficit(tank,stock,250,"machine_gun_round"));
        assertEquals(4,VehicleSupplyRules.deficit(tank,stock,250,"tank_he_shell"));
        stock.addAmmunition("tank_he_shell",2);
        assertEquals(0,VehicleSupplyRules.resupplyDeficit(tank,stock,270,"tank_he_shell"));
        assertEquals(0,VehicleSupplyRules.resupplyDeficit(tank,stock,270,"vehicle_repair_parts"));
    }
    @Test void sixTankShellsClearTheTriggerButDepartureWaitsForTenRealPrimaryRounds() {
        var tank=catalog.require("tank");var stock=new VehicleInventory(tank);stock.addAmmunition("tank_he_shell",6);stock.addAmmunition("machine_gun_round",240);
        assertFalse(VehicleSupplyRules.needsService(tank,stock,420));
        assertFalse(VehicleSupplyRules.readyToResume(tank,stock,420));
        stock.addAmmunition("tank_ap_shell",4);
        assertTrue(VehicleSupplyRules.readyToResume(tank,stock,420));
    }
    @Test void anUnarmedTruckNeedsSixtyPercentHealthForDepartureButNoImaginaryAmmunition() {
        var truck=catalog.require("truck");var stock=new VehicleInventory(truck);
        assertFalse(VehicleSupplyRules.readyToResume(truck,stock,59));
        assertTrue(VehicleSupplyRules.readyToResume(truck,stock,60));
    }
    @Test void ammunitionReturnAlsoChecksDepartureHealthWithoutRaisingTheHealthTrigger() {
        var tank=catalog.require("tank");var stock=new VehicleInventory(tank);stock.addAmmunition("tank_he_shell",10);
        assertFalse(VehicleSupplyRules.needsService(tank,stock,210));
        assertFalse(VehicleSupplyRules.readyToResume(tank,stock,210));
        assertTrue(VehicleSupplyRules.readyToResume(tank,stock,252));
    }
    @Test void intermediateApcStockAndHealthMustNotStartANewReturnForAnExistingGuard() {
        var apc=catalog.require("apc");var stock=new VehicleInventory(apc);stock.addAmmunition("autocannon_round",25);
        assertFalse(VehicleSupplyRules.needsService(apc,stock,120));
        assertFalse(VehicleSupplyRules.readyToResume(apc,stock,120));
        stock.addAmmunition("autocannon_round",35);
        assertTrue(VehicleSupplyRules.readyToResume(apc,stock,144));
    }
    @Test void tankCountsHeAndApTogetherButCoaxCannotHideAnEmptyMainGun() {
        var tank=catalog.require("tank");var stock=new VehicleInventory(tank);
        stock.addAmmunition("machine_gun_round",240);
        assertTrue(VehicleSupplyRules.emptyPrimary(tank,stock));
        stock.addAmmunition("tank_he_shell",4);
        assertTrue(VehicleSupplyRules.lowPrimary(tank,stock));
        assertFalse(VehicleSupplyRules.emptyPrimary(tank,stock));
        stock.addAmmunition("tank_ap_shell",1);
        assertFalse(VehicleSupplyRules.lowPrimary(tank,stock));
    }
    @Test void apcPrimaryThresholdIsTwentyPercentAndRocketsStayIndependent() {
        var apc=catalog.require("apc");var stock=new VehicleInventory(apc);
        stock.addAmmunition("rocket_round",4);stock.addAmmunition("autocannon_round",24);
        assertTrue(VehicleSupplyRules.lowPrimary(apc,stock));
        stock.addAmmunition("autocannon_round",1);
        assertFalse(VehicleSupplyRules.lowPrimary(apc,stock));
    }
    @Test void anUnarmedTruckIsNotAnEmptyWeaponAndLegacyFuelDoesNotAffectService() {
        var truck=catalog.require("truck");var stock=new VehicleInventory(truck);
        assertFalse(VehicleSupplyRules.lowPrimary(truck,stock));
        assertFalse(VehicleSupplyRules.emptyPrimary(truck,stock));
        assertFalse(VehicleSupplyRules.needsService(truck,stock,100));
        stock.addFuel(137);
        assertFalse(VehicleSupplyRules.needsService(truck,stock,100));
        assertEquals(0,VehicleSupplyRules.deficit(truck,stock,100,"military_fuel_can"));
    }
    @Test void onlyHealthStrictlyBelowThirtyFivePercentTriggersRepairReturn() {
        var truck=catalog.require("truck");var stock=new VehicleInventory(truck);
        assertFalse(VehicleSupplyRules.needsService(truck,stock,35));
        assertTrue(VehicleSupplyRules.needsService(truck,stock,34));
    }
    @Test void usefulItemsAreCountedWithoutConversionOrFuelAndFullStockIsIdempotent() {
        var tank=catalog.require("tank");var stock=new VehicleInventory(tank);
        stock.addAmmunition("tank_he_shell",7);
        assertEquals(5,VehicleSupplyRules.deficit(tank,stock,390,"tank_he_shell"));
        assertEquals(8,VehicleSupplyRules.deficit(tank,stock,390,"tank_ap_shell"));
        assertEquals(2,VehicleSupplyRules.deficit(tank,stock,390,"vehicle_repair_parts"));
        assertEquals(0,VehicleSupplyRules.deficit(tank,stock,390,"autocannon_round"));
        assertEquals(0,VehicleSupplyRules.deficit(tank,stock,390,"military_fuel_can"));
        stock.addAmmunition("tank_he_shell",5);
        assertEquals(0,VehicleSupplyRules.deficit(tank,stock,420,"tank_he_shell"));
        assertEquals(0,VehicleSupplyRules.deficit(tank,stock,420,"vehicle_repair_parts"));
    }
}
