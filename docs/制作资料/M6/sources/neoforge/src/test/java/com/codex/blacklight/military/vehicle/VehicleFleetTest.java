package com.codex.blacklight.military.vehicle;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class VehicleFleetTest {
    private static UUID elect(UUID formal,Map<UUID,Integer> loaded){
        try{return (UUID)Class.forName("com.codex.blacklight.military.vehicle.VehicleFleet").getMethod("chooseCommander",UUID.class,Map.class).invoke(null,formal,loaded);}
        catch(ReflectiveOperationException failure){throw new AssertionError("production fleet commander election missing",failure);}
    }
    @Test void formalAppointmentSurvivesHigherRankWhileItsRealOfficerIsOperational(){
        var formal=new UUID(0,3);var general=new UUID(0,2);
        assertEquals(formal,elect(formal,Map.of(formal,3,general,7)));
    }
    @Test void unavailableFormalOfficerUsesHighestLoadedRankWithStableUuidTie(){
        var formal=new UUID(0,9);var first=new UUID(0,1);var second=new UUID(0,2);var junior=new UUID(0,3);
        assertEquals(first,elect(formal,Map.of(second,7,junior,3,first,7)));
        assertNull(elect(formal,Map.of()));
    }
    @Test void patrolWithoutExplicitEndpointUsesAnActualRouteInsteadOfZeroLengthReplanning(){
        var origin=new com.codex.blacklight.military.MilitaryOrder.Point(0,81,0);
        var fleet=new VehicleFleet(UUID.randomUUID(),com.codex.blacklight.military.MilitaryFaction.MILITARY,List.of(UUID.randomUUID()),List.of(),UUID.randomUUID(),origin);
        assertTrue(fleet.order(VehicleFleet.Mission.PATROL,null));assertNotEquals(origin,fleet.target());
    }
}
