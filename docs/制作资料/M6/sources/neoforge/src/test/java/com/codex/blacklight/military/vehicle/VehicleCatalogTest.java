package com.codex.blacklight.military.vehicle;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.codex.blacklight.military.vehicle.VehicleDefinition.Vec3;

class VehicleCatalogTest {
    static String config() throws Exception {
        try (var stream = VehicleCatalogTest.class.getResourceAsStream("/data/blacklight/military/vehicles.json")) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    static JsonObject configTree() throws Exception { return JsonParser.parseString(config()).getAsJsonObject(); }
    static JsonObject first(JsonObject tree) { return tree.getAsJsonArray("vehicles").get(0).getAsJsonObject(); }

    @Test void threeChassisExposeTheirOwnSeatsBoundsAndTwoLiveries() {
        var catalog = VehicleCatalog.loadDefault();
        assertEquals(Set.of("truck", "apc", "tank"), catalog.definitions().keySet());
        double[][] expected = {{2.9,7.5,3.7,100,.28,12000,8},{3.3,7,3.3,240,.20,9000,8},{4,8,3.4,420,.16,7200,3}};
        int i = 0;
        for (String id : new String[]{"truck", "apc", "tank"}) {
            var d = catalog.require(id); var e = expected[i++];
            assertEquals(e[0], d.width()); assertEquals(e[1], d.length()); assertEquals(e[2], d.height());
            assertEquals(e[3], d.maxHealth()); assertEquals(e[4], d.maxSpeed());
            assertEquals((long)e[5], d.maxFuelTicks()); assertEquals((int)e[6], d.seats().size());
            assertEquals(Set.of("military", "blackwatch"), Set.copyOf(d.liveries()));
            assertEquals(d.seats().size(), d.seats().stream().map(VehicleDefinition.Seat::id).distinct().count());
            d.seats().forEach(s -> assertTrue(d.anchors().containsKey(s.anchorId())));
            assertThrows(UnsupportedOperationException.class, () -> d.anchors().clear());
        }
        assertThrows(IllegalArgumentException.class, () -> catalog.require("misspelled_tank"));
    }
    @Test void malformedAndUnboundedConfigurationFailsClosed() throws Exception {
        for (String field : new String[]{"width","length","height","maxHealth","maxSpeed","maxFuelTicks"}) {
            for (double invalid : new double[]{-1,0,Double.NaN,Double.POSITIVE_INFINITY,1e12}) {
                var root = configTree(); first(root).addProperty(field, invalid);
                assertThrows(IllegalArgumentException.class, () -> VehicleCatalog.fromJson(root.toString()), field + "=" + invalid);
            }
        }
        var root = configTree(); root.addProperty("schemaVersion", 99);
        assertThrows(IllegalArgumentException.class, () -> VehicleCatalog.fromJson(root.toString()));
    }
    @Test void duplicateSeatsUnknownMountReferencesAndNegativeStockAreRejected() throws Exception {
        var duplicate = configTree(); var seats = first(duplicate).getAsJsonArray("seats"); seats.add(seats.get(0).deepCopy());
        assertThrows(IllegalArgumentException.class, () -> VehicleCatalog.fromJson(duplicate.toString()));
        var missing = configTree(); first(missing).getAsJsonArray("seats").get(0).getAsJsonObject().addProperty("anchorId", "missing_anchor");
        assertThrows(IllegalArgumentException.class, () -> VehicleCatalog.fromJson(missing.toString()));
        var negative = configTree(); negative.getAsJsonArray("vehicles").get(1).getAsJsonObject().getAsJsonObject("ammunitionCapacities").addProperty("autocannon_round", -1);
        assertThrows(IllegalArgumentException.class, () -> VehicleCatalog.fromJson(negative.toString()));
        var wrongMount = configTree(); first(wrongMount).addProperty("mountsResource", "../../outside.json");
        assertThrows(IllegalArgumentException.class, () -> VehicleCatalog.fromJson(wrongMount.toString()));
    }
    @Test void bodyAndGunMountsUseTheSameAuthoringPivots() {
        var tank = VehicleCatalog.loadDefault().require("tank");
        var origin = new Vec3(10,64,20);
        assertEquals(new Vec3(0,1.88214286,0), tank.turretPivot());
        assertEquals(new Vec3(0,2.55,1.16923077), tank.gunPivot());
        // Independent authoring literals: yaw90 maps (x,z) to (z,-x).
        var seat = tank.mountWorld("seat_driver", origin, 90, 0, 0);
        assertEquals(12.03076923, seat.x(), 1e-9); assertEquals(64.06071429, seat.y(), 1e-9); assertEquals(20, seat.z(), 1e-9);
        var muzzle = tank.mountWorld("muzzle_primary", origin, 0, 90, 0);
        assertEquals(16.03076923, muzzle.x(), 1e-9); assertEquals(66.55, muzzle.y(), 1e-9); assertEquals(20, muzzle.z(), 1e-9);
        // Pitch90 lowers the 6.03076923-1.16923077 barrel span about the real breech.
        var lowered = tank.mountWorld("muzzle_primary", origin, 0, 0, 90);
        assertEquals(10, lowered.x(), 1e-9); assertEquals(61.68846154, lowered.y(), 1e-9); assertEquals(21.16923077, lowered.z(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> tank.mountWorld("missing", origin, 0,0,0));
        assertThrows(IllegalArgumentException.class, () -> tank.mountWorld("seat_driver", origin, Double.NaN,0,0));
    }
    @Test void allAuthoredSeatBasesAndOpticalPortsKeepTheirLiteralCoordinatesAndFrames() {
        String[] chassis = {"truck", "apc", "tank"};
        String[][] seats = {
            {"driver","officer","passenger_1","passenger_2","passenger_3","passenger_4","passenger_5","passenger_6"},
            {"driver","gunner","passenger_1","passenger_2","passenger_3","passenger_4","passenger_5","passenger_6"},
            {"driver","gunner","commander"}
        };
        // Actual Entity feet and separate optics from the 4cca authoring source, in blocks.
        double[][][] bases = {
            {{-.51555556,.89,2.05645161},{.51555556,.89,2.05645161},
             {-.53703704,1.053125,-.72580645},{.53703704,1.053125,-.72580645},
             {-.53703704,1.053125,-1.69354839},{.53703704,1.053125,-1.69354839},
             {-.53703704,1.053125,-2.66129032},{.53703704,1.053125,-2.66129032}},
            {{-.69193548,.23571429,2.11206897},{.26612903,.53035714,.18103448},
             {-.74516129,.23571429,-.84482759},{.74516129,.23571429,-.84482759},
             {-.74516129,.23571429,-1.75},{.74516129,.23571429,-1.75},
             {-.74516129,.23571429,-2.65517241},{.74516129,.23571429,-2.65517241}},
            {{0,.06071429,2.03076923},{-.52941176,.54642857,.12307692},{.58823529,.72857143,-.55384615}}
        };
        double[][][] optics = {
            {{-.51555556,2.51,2.05645161},{.51555556,2.51,2.05645161},
             {-.53703704,2.673125,-.72580645},{.53703704,2.673125,-.72580645},
             {-.53703704,2.673125,-1.69354839},{.53703704,2.673125,-1.69354839},
             {-.53703704,2.673125,-2.66129032},{.53703704,2.673125,-2.66129032}},
            {{-.69193548,2.15,1.91},{0,2.88,.17},
             {-1.45,1.85571429,-.84482759},{1.45,1.85571429,-.84482759},
             {-1.45,1.85571429,-1.75},{1.45,1.85571429,-1.75},
             {-1.45,1.85571429,-2.65517241},{1.45,1.85571429,-2.65517241}},
            {{0,1.98,3.29},{.55294118,3.35142857,-.22},{.55294118,3.35142857,-.22}}
        };
        var catalog = VehicleCatalog.loadDefault();
        for (int c=0;c<chassis.length;c++) {
            var definition = catalog.require(chassis[c]);
            assertEquals(Set.of(seats[c]), definition.seats().stream().map(VehicleDefinition.Seat::id).collect(java.util.stream.Collectors.toSet()));
            for (int i=0;i<seats[c].length;i++) {
                var base = definition.anchors().get("seat_"+seats[c][i]);
                var optic = definition.anchors().get("camera_"+seats[c][i]);
                var b = bases[c][i]; var o = optics[c][i];
                assertNotNull(base); assertNotNull(optic);
                assertEquals(VehicleDefinition.Frame.BODY, base.frame());
                assertEquals(new Vec3(b[0],b[1],b[2]), base.position(), chassis[c]+" "+seats[c][i]+" feet");
                boolean turret = c>0 && (seats[c][i].equals("gunner") || seats[c][i].equals("commander"));
                assertEquals(turret?VehicleDefinition.Frame.TURRET_YAW:VehicleDefinition.Frame.BODY, optic.frame());
                assertEquals(new Vec3(o[0],o[1],o[2]), optic.position(), chassis[c]+" "+seats[c][i]+" optic");
            }
        }
    }
    @Test void tankShellConfigurationCannotSplitOneBreechOrGiveItTwoReloadIntervals() throws Exception {
        var split=configTree(); var splitWeapons=split.getAsJsonArray("vehicles").get(2).getAsJsonObject().getAsJsonArray("weapons");
        splitWeapons.get(1).getAsJsonObject().addProperty("cooldownGroup","tank_main_typo");
        assertThrows(IllegalArgumentException.class,()->VehicleCatalog.fromJson(split.toString()));
        var unknown=configTree(); var unknownWeapons=unknown.getAsJsonArray("vehicles").get(2).getAsJsonObject().getAsJsonArray("weapons");
        unknownWeapons.get(0).getAsJsonObject().addProperty("cooldownGroup","invented_breech");
        unknownWeapons.get(1).getAsJsonObject().addProperty("cooldownGroup","invented_breech");
        assertThrows(IllegalArgumentException.class,()->VehicleCatalog.fromJson(unknown.toString()));
        var mismatch=configTree(); var mismatchWeapons=mismatch.getAsJsonArray("vehicles").get(2).getAsJsonObject().getAsJsonArray("weapons");
        mismatchWeapons.get(1).getAsJsonObject().addProperty("cooldownTicks",1);
        assertThrows(IllegalArgumentException.class,()->VehicleCatalog.fromJson(mismatch.toString()));
        var adjusted=configTree(); var adjustedWeapons=adjusted.getAsJsonArray("vehicles").get(2).getAsJsonObject().getAsJsonArray("weapons");
        adjustedWeapons.get(0).getAsJsonObject().addProperty("cooldownTicks",120);
        adjustedWeapons.get(1).getAsJsonObject().addProperty("cooldownTicks",120);
        var inventory=new VehicleInventory(VehicleCatalog.fromJson(adjusted.toString()).require("tank")); inventory.issueInitialLoadout();
        assertTrue(inventory.tryFire("tank_he",0)); assertFalse(inventory.tryFire("tank_ap",119)); assertTrue(inventory.tryFire("tank_ap",120));
    }
}
