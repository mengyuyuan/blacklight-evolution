package com.codex.blacklight.military.aircraft;
import com.codex.blacklight.military.vehicle.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterAuthorityTest {
    @Test void liftIsOptInAndExpiresWithServerInput(){
        UUID craft=UUID.randomUUID(),pilot=UUID.randomUUID();var seats=new VehicleSeats(List.of(new VehicleDefinition.Seat("pilot",VehicleDefinition.Job.DRIVER,"pilot")));
        seats.board(pilot,"pilot",Set.of(pilot));var session=new VehicleControlAuthority.Session(pilot,"overworld",craft,true);
        var ground=new VehicleControlAuthority(craft,"overworld",seats);var groundLease=ground.grant(session);
        assertFalse(ground.accept(session,new VehicleControlAuthority.Input(craft,groundLease,0,0,0,0,0,32),0));
        var air=new VehicleControlAuthority(craft,"overworld",seats,true);var lease=air.grant(session);
        assertTrue(air.accept(session,new VehicleControlAuthority.Input(craft,lease,0,1,0,0,0,37),10));
        assertEquals(37,air.state(session,14).actions());var expired=air.state(session,15);
        assertEquals(0,expired.actions());assertEquals(0,expired.throttle());assertFalse(expired.primaryFire());assertTrue(expired.brake());
    }
    @Test void copilotOnlyControlsWeaponsAndSeatChangeInvalidatesTokens(){
        UUID craft=UUID.randomUUID(),pilot=UUID.randomUUID(),gunner=UUID.randomUUID();var actual=Set.of(pilot,gunner);
        var seats=new VehicleSeats(List.of(new VehicleDefinition.Seat("pilot",VehicleDefinition.Job.DRIVER,"pilot"),new VehicleDefinition.Seat("copilot",VehicleDefinition.Job.GUNNER,"copilot")));
        seats.board(pilot,"pilot",actual);seats.board(gunner,"copilot",actual);var air=new VehicleControlAuthority(craft,"overworld",seats,true);
        var driver=new VehicleControlAuthority.Session(pilot,"overworld",craft,true);var weapon=new VehicleControlAuthority.Session(gunner,"overworld",craft,true);var dl=air.grant(driver);var gl=air.grant(weapon);
        assertFalse(air.accept(weapon,new VehicleControlAuthority.Input(craft,gl,0,0,0,0,0,64),0));
        assertTrue(air.accept(weapon,new VehicleControlAuthority.Input(craft,gl,0,0,0,0,0,7),0));assertTrue(air.state(weapon,0).secondaryFire());assertEquals(7,air.state(weapon,0).actions());
        assertTrue(air.accept(driver,new VehicleControlAuthority.Input(craft,dl,0,0,0,0,0,39),0));assertFalse(air.state(driver,0).primaryFire());assertEquals(32,air.state(driver,0).actions());
        seats.remove(gunner);assertFalse(air.accept(driver,new VehicleControlAuthority.Input(craft,dl,1,0,0,0,0,1),1));
    }
}
