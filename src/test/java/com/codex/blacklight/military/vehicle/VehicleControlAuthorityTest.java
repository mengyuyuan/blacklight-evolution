package com.codex.blacklight.military.vehicle;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.codex.blacklight.military.vehicle.VehicleControlAuthority.Input;
import com.codex.blacklight.military.vehicle.VehicleControlAuthority.Session;

class VehicleControlAuthorityTest {
    final UUID vehicle = new UUID(0,1), driver = new UUID(0,2), gunner = new UUID(0,3), stranger = new UUID(0,4);
    final VehicleSeats seats = new VehicleSeats(VehicleCatalog.loadDefault().require("tank"));
    final VehicleControlAuthority authority = new VehicleControlAuthority(vehicle,"test:world",seats);
    Session session(UUID player) { return new Session(player,"test:world",vehicle,true); }
    UUID boardDriver() { assertTrue(seats.board(driver,"driver",Set.of(driver))); return authority.grant(session(driver)); }
    Input input(UUID lease,long sequence) { return new Input(vehicle,lease,sequence,1,0,0,0,0); }

    @Test void actualPassengerMembershipAndSeatExclusivityAreRequired() {
        assertFalse(seats.board(driver,"driver",Set.of())); assertNull(authority.grant(session(driver)));
        assertTrue(seats.board(driver,"driver",Set.of(driver,gunner)));
        assertFalse(seats.board(gunner,"driver",Set.of(driver,gunner)));
        assertFalse(seats.board(driver,"gunner",Set.of(driver,gunner)));
        assertEquals(driver,seats.occupant("driver")); assertNull(seats.occupant("gunner"));
        assertThrows(UnsupportedOperationException.class, () -> seats.occupants().clear());
    }
    @Test void oldDuplicateAndReorderedMessagesCannotControlTheCurrentDriver() {
        var lease = boardDriver(); assertNotNull(lease);
        assertTrue(authority.accept(session(driver),input(lease,7),10));
        assertFalse(authority.accept(session(driver),input(lease,7),11));
        assertFalse(authority.accept(session(driver),input(lease,6),11));
        assertFalse(authority.accept(session(driver),input(UUID.randomUUID(),8),11));
        assertEquals(7,authority.state(session(driver),11).sequence());
    }
    @Test void connectedIdentityCannotImpersonateTheLeaseOwner() {
        var lease = boardDriver();
        assertFalse(authority.accept(session(stranger),input(lease,1),0));
        assertTrue(authority.accept(session(driver),input(lease,1),0));
        assertFalse(authority.accept(new Session(driver,"test:elsewhere",vehicle,true),input(lease,2),1));
        assertTrue(authority.state(session(driver),1).brake());
    }
    @Test void fiveTicksWithoutInputReleasesAccelerationAndFire() {
        var lease = boardDriver();
        assertTrue(authority.accept(session(driver),new Input(vehicle,lease,0,1,1,20,10,1),30));
        assertFalse(authority.state(session(driver),34).brake());
        var expired = authority.state(session(driver),35);
        assertTrue(expired.brake()); assertEquals(0,expired.throttle()); assertEquals(0,expired.steering()); assertFalse(expired.primaryFire());
        assertEquals(20,expired.aimYaw()); assertEquals(10,expired.aimPitch());
    }
    @Test void twentyPacketsInAnyTwentyTickWindowIsTheHardLimit() {
        var lease=boardDriver();
        for(int i=0;i<20;i++) assertTrue(authority.accept(session(driver),input(lease,i),0));
        assertFalse(authority.accept(session(driver),input(lease,20),19));
        assertTrue(authority.accept(session(driver),input(lease,20),20));
    }
    @Test void invalidInputsCannotPoisonAValidLeaseOrConsumeItsSequence() {
        var lease=boardDriver();
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,2,-2})
            assertFalse(authority.accept(session(driver),new Input(vehicle,lease,1,invalid,0,0,0,0),0));
        assertFalse(authority.accept(session(driver),new Input(vehicle,lease,1,0,0,Double.NaN,0,0),0));
        assertFalse(authority.accept(session(driver),new Input(vehicle,lease,1,0,0,0,91,0),0));
        assertFalse(authority.accept(session(driver),new Input(new UUID(0,99),lease,1,0,0,0,0,0),0));
        assertTrue(authority.accept(session(driver),input(lease,1),0));
    }
    @Test void seatMovesAreAtomicAndInvalidateThePreviousLease() {
        var lease=boardDriver();
        assertTrue(seats.board(gunner,"gunner",Set.of(driver,gunner)));
        assertFalse(seats.switchSeat(driver,"gunner",Set.of(driver,gunner)));
        assertEquals("driver",seats.seatOf(driver));
        assertFalse(authority.accept(session(driver),input(lease,1),0));
        var next=authority.grant(session(driver)); assertNotEquals(lease,next);
        assertTrue(authority.accept(session(driver),new Input(vehicle,next,1,0,0,0,0,1),0));
        assertFalse(authority.state(session(driver),0).primaryFire());
        assertTrue(authority.accept(session(driver),input(next,2),0));
        var gunLease=authority.grant(session(gunner));
        assertTrue(authority.accept(session(gunner),new Input(vehicle,gunLease,0,0,0,0,0,1),0));
        assertFalse(authority.accept(session(gunner),input(gunLease,1),1));
    }
    @Test void removalDeathDimensionChangeAndDisconnectImmediatelyRevokeInput() {
        var lease=boardDriver(); assertTrue(authority.accept(session(driver),input(lease,0),0));
        assertTrue(authority.state(new Session(driver,"test:world",null,true),1).brake());
        assertFalse(authority.accept(session(driver),input(lease,1),1));
        lease=authority.grant(session(driver)); assertTrue(authority.accept(session(driver),input(lease,0),2));
        assertTrue(authority.state(new Session(driver,"test:world",vehicle,false),2).brake());
        lease=authority.grant(session(driver)); authority.revoke(driver);
        assertFalse(authority.accept(session(driver),input(lease,0),3));
        seats.reconcile(Set.of()); assertNull(seats.seatOf(driver));
        assertNull(authority.grant(session(driver)));
    }
    @Test void driverFireBitsCannotStarveDrivingOrBorrowTheOccupiedGunnersWeapons() {
        boardDriver(); assertTrue(seats.board(gunner,"gunner",Set.of(driver,gunner)));
        var driverLease=authority.grant(session(driver));var gunnerLease=authority.grant(session(gunner));
        for(int tick=0;tick<=12;tick+=2) {
            assertTrue(authority.accept(session(driver),new Input(vehicle,driverLease,tick,1,-.5,20,10,7),tick),
                    "a valid driver keeps steering even when its mouse also requests both weapons");
            var state=authority.state(session(driver),tick);
            assertEquals(1,state.throttle());assertEquals(-.5,state.steering());assertFalse(state.brake());
            assertFalse(state.primaryFire());assertFalse(state.secondaryFire());
            assertEquals(0,state.actions()&3,"unauthorized fire bits must not survive in accepted state");
        }
        assertEquals(1,authority.state(session(driver),15).throttle());
        assertTrue(authority.accept(session(gunner),new Input(vehicle,gunnerLease,0,0,0,20,10,3),12));
        assertTrue(authority.state(session(gunner),12).primaryFire());assertTrue(authority.state(session(gunner),12).secondaryFire());
        assertTrue(authority.state(session(driver),17).brake(),"real input loss still expires after five ticks");
    }
    @Test void switchingSeatAndReconcilingNeverCreatesAbsentPassengers() {
        boardDriver(); assertTrue(seats.switchSeat(driver,"commander",Set.of(driver)));
        assertNull(seats.occupant("driver")); assertEquals(driver,seats.occupant("commander"));
        assertNull(authority.grant(session(driver)));
        seats.reconcile(Set.of(gunner)); assertTrue(seats.occupants().isEmpty());
    }
    @Test void declaredMenuAndBrakeActionsSurviveValidationButUnknownBitsAreRejected() {
        var lease=boardDriver();
        assertTrue(authority.accept(session(driver),new Input(vehicle,lease,0,0,0,0,0,12),0));
        assertEquals(12,authority.state(session(driver),0).actions());
        assertTrue(authority.accept(session(driver),new Input(vehicle,lease,1,1,1,0,0,16),1));
        assertTrue(authority.state(session(driver),1).brake()); assertEquals(0,authority.state(session(driver),1).throttle());
        assertFalse(authority.accept(session(driver),new Input(vehicle,lease,2,0,0,0,0,32),2));
    }
}
