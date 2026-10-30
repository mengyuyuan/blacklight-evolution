package com.codex.blacklight.client;

import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.military.vehicle.VehicleCatalog;
import com.codex.blacklight.military.vehicle.VehicleSeats;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleClientInputTest {
    @Test void prototypeDriverAndGunnerKeepVehicleMouseInsteadOfPersonalCombat(){
        for(var form:new PowerForm[]{PowerForm.NONE,PowerForm.CLAWS,PowerForm.BLADE,PowerForm.HAMMERFISTS,PowerForm.WHIPFIST,PowerForm.MUSCLE_MASS})
            assertFalse(new PersonalCombatInput(true,true,form,true,false,true).active(),"mounted vehicle input must win for "+form);
    }
    @Test void emptyHandShiftUseOnAVehicleKeepsTheRealEntityInteraction(){
        assertFalse(new PersonalCombatInput(true,true,PowerForm.NONE,true,false,true).active(),"empty-hand prototype must be able to request actual vehicle extraction");
        assertFalse(new PersonalCombatInput(true,true,PowerForm.CLAWS,true,false,true).active(),"claw selection cannot turn that interaction into a ground spike");
    }
    @Test void ordinaryOnFootPersonalCombatRetainsItsExistingEligibility(){
        for(var form:new PowerForm[]{PowerForm.NONE,PowerForm.CLAWS,PowerForm.BLADE,PowerForm.HAMMERFISTS,PowerForm.WHIPFIST,PowerForm.MUSCLE_MASS})
            assertTrue(new PersonalCombatInput(true,true,form,true,false,false).active(),"nonvehicle input remains personal for "+form);
        assertFalse(new PersonalCombatInput(false,true,PowerForm.NONE,true,false,false).active());
        assertFalse(new PersonalCombatInput(true,false,PowerForm.CLAWS,true,false,false).active());
        assertFalse(new PersonalCombatInput(true,true,PowerForm.NONE,false,false,false).active());
        assertFalse(new PersonalCombatInput(true,true,PowerForm.CLAWS,true,true,false).active());
    }
    @Test void synchronizedGunnerSeatRemovesOnlyTheDriversFireActions(){
        var definition=VehicleCatalog.loadDefault().require("tank");var seats=new VehicleSeats(definition);
        var driver=new UUID(0,1);var gunner=new UUID(0,2);
        assertTrue(seats.board(driver,"driver",Set.of(driver,gunner)));
        assertTrue(seats.board(gunner,"gunner",Set.of(driver,gunner)));
        var input=new VehicleControls.SeatInput(definition,seats.seatOf(driver),seats.occupants().keySet());
        assertTrue(input.driver());assertEquals(0,input.fireActions(true,true));
        assertEquals(3,new VehicleControls.SeatInput(definition,seats.seatOf(gunner),seats.occupants().keySet()).fireActions(true,true));
        seats.remove(gunner);
        assertEquals(3,new VehicleControls.SeatInput(definition,seats.seatOf(driver),seats.occupants().keySet()).fireActions(true,true));
    }
    @Test void commanderPassengerMissingSeatAndUnarmedTruckNeverSendFireActions(){
        var catalog=VehicleCatalog.loadDefault();
        assertEquals(0,new VehicleControls.SeatInput(catalog.require("tank"),"commander",Set.of("driver","commander")).fireActions(true,true));
        assertEquals(0,new VehicleControls.SeatInput(catalog.require("apc"),"passenger_1",Set.of("driver","passenger_1")).fireActions(true,true));
        assertEquals(0,new VehicleControls.SeatInput(catalog.require("tank"),null,Set.of("driver")).fireActions(true,true));
        assertEquals(0,new VehicleControls.SeatInput(catalog.require("truck"),"driver",Set.of("driver")).fireActions(true,true));
    }
}
