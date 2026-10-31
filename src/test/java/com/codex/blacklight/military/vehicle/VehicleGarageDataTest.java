package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.military.MilitaryFaction;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleGarageDataTest {
    private VehicleGarageData fresh() {
        var data = new VehicleGarageData();
        assertTrue(data.register("garage", "base", "minecraft:overworld", MilitaryFaction.MILITARY, new VehicleGarageData.Site(100, 80, 100, 0)));
        return data;
    }
    @Test void unknownOrUnloadedReservedVehicleNeverReopensItsInitialSlot() {
        var data = fresh(); var id = UUID.randomUUID();
        assertTrue(data.reserve("garage", "tank", id));
        var restored = VehicleGarageData.restore(data.snapshot());
        assertFalse(restored.reserve("garage", "tank", UUID.randomUUID()));
        assertEquals(id, restored.garage("garage").orElseThrow().slots().get("tank").vehicle());
        assertEquals(VehicleGarageData.State.RESERVED, restored.garage("garage").orElseThrow().slots().get("tank").state());
    }
    @Test void issuedAndDestroyedVehiclesPermanentlyConsumeTheirThreeSeparateInitialSlots() {
        var data = fresh(); var tank = UUID.randomUUID(); var truck = UUID.randomUUID();
        assertTrue(data.reserve("garage", "tank", tank)); assertTrue(data.issued("garage", "tank", tank));
        assertTrue(data.reserve("garage", "truck", truck)); assertTrue(data.issued("garage", "truck", truck));
        assertTrue(data.lost(tank)); assertFalse(data.lost(tank));
        var restored = VehicleGarageData.restore(data.snapshot());
        assertFalse(restored.reserve("garage", "tank", UUID.randomUUID()));
        assertFalse(restored.reserve("garage", "truck", UUID.randomUUID()));
        assertTrue(restored.reserve("garage", "apc", UUID.randomUUID()));
        assertEquals(VehicleGarageData.State.LOST, restored.garage("garage").orElseThrow().slots().get("tank").state());
    }
    @Test void registeringAgainCannotResetSlotsOrReuseOneVehicleIdentity() {
        var data = fresh(); var id = UUID.randomUUID(); assertTrue(data.reserve("garage", "truck", id));
        assertFalse(data.register("garage", "base", "minecraft:overworld", MilitaryFaction.MILITARY, new VehicleGarageData.Site(100, 80, 100, 0)));
        assertFalse(data.reserve("garage", "apc", id));
        assertFalse(data.issued("garage", "truck", UUID.randomUUID()));
        assertFalse(data.reserve("garage", "helicopter", UUID.randomUUID()));
        assertEquals(id, data.garage("garage").orElseThrow().slots().get("truck").vehicle());
    }
    @Test void absentOldWorldFieldsLoadEmptyAndCannotDispatchVehicles() {
        var data = VehicleGarageData.restore(new VehicleGarageData.Snapshot(1, List.of()));
        assertTrue(data.garages().isEmpty());
        assertFalse(data.reserve("old-base", "tank", UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> VehicleGarageData.restore(new VehicleGarageData.Snapshot(99, List.of())));
    }
    @Test void malformedSavedSlotCannotBeReinterpretedAsAFreeInitialSlot() {
        var data = fresh(); var garage = data.garage("garage").orElseThrow();
        var slots = new HashMap<>(garage.slots()); slots.put("tank", new VehicleGarageData.Slot("tank", null, VehicleGarageData.State.ISSUED));
        var broken = new VehicleGarageData.Garage(garage.id(), garage.baseId(), garage.dimension(), garage.faction(), garage.site(), slots);
        assertThrows(IllegalArgumentException.class, () -> VehicleGarageData.restore(new VehicleGarageData.Snapshot(1, List.of(broken))));
        assertThrows(IllegalArgumentException.class, () -> VehicleGarageData.restore(new VehicleGarageData.Snapshot(1, List.of(garage, garage))));
    }
}
