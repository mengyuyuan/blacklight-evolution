package com.codex.blacklight.military.vehicle;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class VehicleAimTest {
    @Test void anglesMatchNativeLookDirection(){
        Vec3 from=new Vec3(1,2,3);
        for(float yaw:new float[]{-170,-90,0,45,120})for(float pitch:new float[]{-20,0,10}){
            Vec3 to=from.add(Vec3.directionFromRotation(pitch,yaw).scale(30));
            assertEquals(0,net.minecraft.util.Mth.wrapDegrees(VehicleAim.yaw(from,to)-yaw),1e-2);
            assertEquals(pitch,VehicleAim.pitch(from,to),1e-2);
        }
    }
    @Test void barrelOffsetChangesTheAngleToTheSamePoint(){
        Vec3 point=new Vec3(0,0,20);
        assertEquals(0,VehicleAim.pitch(new Vec3(0,0,0),point),1e-4);
        assertTrue(VehicleAim.pitch(new Vec3(0,3,0),point)>8);
    }
}
