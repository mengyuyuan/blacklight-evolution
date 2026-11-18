package com.codex.blacklight.military.aircraft;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterCircleFiringTest {
    @Test void savedValleyOrbitMovesOutwardIntoItsUnchangedGunEnvelope(){
        var circle=new HelicopterCircleStrafe();circle.begin(.5);
        var target=new Vec3(0,71,0);var position=new Vec3(0,90.128141,23.05);
        assertFalse(HelicopterGunEnvelope.permits(HelicopterGunEnvelope.Profile.ORDINARY_127,position,target,180,0,false));
        var move=circle.firingMotion(position,target,84,false,new Vec3(0,0,-1));
        assertNotNull(move);assertTrue(move.request().z>0,"rise13 should increase orbit radius instead of circling forever at23");
        var firingPosition=new Vec3(0,90,29);
        assertTrue(HelicopterGunEnvelope.permits(HelicopterGunEnvelope.Profile.ORDINARY_127,firingPosition,target,180,0,false));
        assertTrue(move.minimumHeight()>=19&&move.maximumHeight()<=23,"do not descend through the authored terrain margin");
    }
    @Test void upperTerrainBandDescendsOnlyToTheShootablePart(){
        var circle=new HelicopterCircleStrafe();circle.begin(.25);
        var move=circle.firingMotion(new Vec3(0,94,29),new Vec3(0,71,0),84,false,new Vec3(0,0,-1));
        assertNotNull(move);assertTrue(move.request().y<0);assertTrue(move.maximumHeight()>move.minimumHeight());
    }
    @Test void impossibleTerrainBandReleasesCircleInsteadOfRelaxingGunLimits(){
        assertFalse(HelicopterCircleStrafe.firingBandAvailable(81,99,false));
        assertTrue(HelicopterCircleStrafe.firingBandAvailable(81,81,true));
        assertFalse(HelicopterCircleStrafe.firingBandAvailable(81,Double.NaN,false));
    }
    @Test void ordinaryFlatOrbitRetainsTheSourceMovement(){
        var circle=new HelicopterCircleStrafe();circle.begin(.75);
        var position=new Vec3(25,8,0);var target=Vec3.ZERO;var forward=new Vec3(0,0,1);
        assertEquals(circle.motion(position,target,0,false,forward),circle.firingMotion(position,target,0,false,forward));
    }
}
