package com.codex.blacklight.military.aircraft;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterFlightSlideTest {
    @Test void pilotCanLiftWhilePressingForwardAgainstAWall(){
        var request=new Vec3(0,.035,.035);
        assertEquals(new Vec3(0,.035,0),HelicopterFlight.slide(request,v->v.z<=0));
    }
    @Test void pilotCanMoveForwardWhileHoldingDescendOnThePad(){
        assertEquals(new Vec3(0,0,.05),HelicopterFlight.slide(new Vec3(0,-.035,.05),v->v.y>=0));
    }
    @Test void blockedAxesDoNotBypassACeilingOrPierceTheWall(){
        assertEquals(Vec3.ZERO,HelicopterFlight.slide(new Vec3(0,.03,.03),v->v.y<=0&&v.z<=0));
        var safe=HelicopterFlight.slide(new Vec3(0,.03,.05),v->v.y<=.01&&v.z<=.02);
        assertTrue(safe.y<=.01&&safe.z<=.02);
    }
}
