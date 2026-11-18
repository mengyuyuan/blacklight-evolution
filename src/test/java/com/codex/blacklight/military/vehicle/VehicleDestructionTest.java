package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.network.VehicleDestructionPayload;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleDestructionTest {
    @Test void fireStopsBeforeSmokeAndWreckExpire(){
        assertEquals(1,VehicleDestruction.flame(0));assertTrue(VehicleDestruction.flame(60)<1);
        assertEquals(0,VehicleDestruction.flame(120));assertEquals(0,VehicleDestruction.flame(-1));
        assertTrue(VehicleDestruction.BURN_TICKS<VehicleDestruction.SMOKE_TICKS);
        assertTrue(VehicleDestruction.SMOKE_TICKS<VehicleDestruction.WRECK_TICKS);
        assertEquals(.5F,VehicleDestruction.fade(340));assertEquals(0,VehicleDestruction.fade(360));
    }
    @Test void detachedRotorFallsBouncesAndComesToRest(){
        var v=new Vec3(1.8,2.5,-.8);double previous=0;
        for(int i=0;i<=3600;i++){
            var p=VehicleDestruction.fragment(i/10F,v,-3.65);
            assertTrue(p.y>=-3.650001);assertTrue(Double.isFinite(p.lengthSqr()));
            if(i>0)assertTrue(Math.abs(p.y-previous)<.06,"continuous ballistic path");previous=p.y;
        }
        assertEquals(-3.65,VehicleDestruction.fragment(180,v,-3.65).y,1e-8);
        assertEquals(VehicleDestruction.fragment(180,v,-3.65),VehicleDestruction.fragment(360,v,-3.65));
    }
    @Test void everyGroundChassisHasADistinctDestructionProfile(){
        for(String id:new String[]{"truck","apc","tank"})assertEquals(id,VehicleDestruction.Chassis.ground(id).id);
        assertThrows(IllegalArgumentException.class,()->VehicleDestruction.Chassis.ground("helicopter"));
        assertThrows(IllegalArgumentException.class,()->VehicleDestruction.Chassis.ground("uav"));
        assertThrows(IllegalArgumentException.class,()->VehicleDestruction.Chassis.ground("unknown"));
    }
    private VehicleDestructionPayload packet(int chassis,double x,float yaw){return new VehicleDestructionPayload(42,chassis,true,x,80,0,yaw,20,10,false,123,4);}
    @Test void receiptRejectsInvalidChassisAndNonFinitePose(){
        for(int i=0;i<5;i++)assertTrue(packet(i,10,130).valid());
        assertFalse(packet(-1,0,0).valid());assertFalse(packet(5,0,0).valid());assertFalse(packet(0,Double.NaN,0).valid());
        assertFalse(packet(0,30000101,0).valid());assertFalse(packet(0,0,Float.POSITIVE_INFINITY).valid());
    }
    @Test void sourceUavFireFadesBeforeLateSmokeAndWreckEnd(){
        var kind=VehicleDestruction.Chassis.UAV;
        assertEquals(0,VehicleDestruction.flame(kind,0));assertEquals(1,VehicleDestruction.flame(kind,4));
        assertEquals(1,VehicleDestruction.flame(kind,533));assertTrue(VehicleDestruction.flame(kind,600)>0);
        assertEquals(0,VehicleDestruction.flame(kind,667));assertEquals(1667,VehicleDestruction.smokeTicks(kind));
        assertEquals(1,VehicleDestruction.fade(kind,1667));assertTrue(VehicleDestruction.fade(kind,1750)>0);
        assertEquals(0,VehicleDestruction.fade(kind,1800));assertEquals(1800,VehicleDestruction.wreckTicks(kind));
        for(var ground:new VehicleDestruction.Chassis[]{VehicleDestruction.Chassis.TRUCK,VehicleDestruction.Chassis.APC,VehicleDestruction.Chassis.TANK,VehicleDestruction.Chassis.HELICOPTER})
            assertEquals(360,VehicleDestruction.wreckTicks(ground));
    }
}
