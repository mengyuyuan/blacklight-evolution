package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.military.vehicle.VehicleDefinition.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehiclePhysicalContactTest {
    private static final Vec3 ZERO=new Vec3(0,0,0);
    @Test void sweptObjectHitsHullEvenWhenItsCenterRayMisses(){
        var hull=new VehicleHullShape(2,6,3);var from=new Vec3(1.4,1,-6);var to=new Vec3(1.4,1,6);
        assertTrue(hull.raycast(ZERO,0,from,new Vec3(0,0,1),12).isEmpty());
        assertEquals(2.5,hull.sweepBox(ZERO,0,from,to,new Vec3(.5,.5,.5)).orElseThrow(),1e-8);
    }
    @Test void rotatedHullDoesNotUseTheEmptyCornerOfItsWorldEnvelope(){
        var hull=new VehicleHullShape(2,6,3);
        assertTrue(hull.sweepBox(ZERO,45,new Vec3(2.7,1,-2.7),new Vec3(2.65,1,-2.65),new Vec3(.05,.05,.05)).isEmpty());
        assertTrue(hull.sweepBox(ZERO,45,new Vec3(0,1,-8),new Vec3(0,1,8),new Vec3(.4,.4,.4)).isPresent());
    }
    @Test void contactAtRestIsZeroAndMissingTargetRemainsEmpty(){
        var hull=new VehicleHullShape(2,6,3);var at=new Vec3(0,1,0);
        assertEquals(0,hull.sweepBox(ZERO,0,at,at,new Vec3(.1,.1,.1)).orElseThrow(),1e-8);
        assertTrue(hull.sweepBox(ZERO,0,new Vec3(8,1,0),new Vec3(8,1,1),new Vec3(.1,.1,.1)).isEmpty());
    }
    @Test void rejectsUnboundedOrNegativePhysicalSweeps(){
        var hull=new VehicleHullShape(2,6,3);
        assertThrows(IllegalArgumentException.class,()->hull.sweepBox(ZERO,0,ZERO,new Vec3(65,0,0),new Vec3(.1,.1,.1)));
        assertThrows(IllegalArgumentException.class,()->hull.sweepBox(ZERO,0,ZERO,ZERO,new Vec3(-1,1,1)));
    }
    @Test void nearestPhysicalSurfaceClampsToTheRealOrientedBody(){
        var hull=new VehicleHullShape(2,6,3);
        assertEquals(new Vec3(1,3,0),hull.closestPoint(ZERO,0,new Vec3(4,4,0)));
        var origin=new Vec3(10,5,-3);var expected=VehicleDefinition.rotateYaw(new Vec3(1,3,0),45);
        var actual=hull.closestPoint(origin,45,new Vec3(14,9,-7));
        assertEquals(origin.x()+expected.x(),actual.x(),1e-8);assertEquals(8,actual.y(),1e-8);assertEquals(origin.z()+expected.z(),actual.z(),1e-8);
    }
    @Test void nearestPhysicalSurfaceIncludesTheBarrelAimedTowardsTheAttacker(){
        var definition=VehicleCatalog.loadDefault().require("tank");var hull=VehicleHullShape.from(definition,180,0);
        var muzzle=definition.mountWorld("muzzle_primary",ZERO,0,180,0);
        var from=new Vec3(muzzle.x(),muzzle.y(),muzzle.z()-3);var actual=hull.closestPoint(ZERO,0,from);
        assertEquals(muzzle.x(),actual.x(),1e-8);assertEquals(muzzle.y(),actual.y(),1e-8);assertEquals(muzzle.z(),actual.z(),1e-8);
    }
}
