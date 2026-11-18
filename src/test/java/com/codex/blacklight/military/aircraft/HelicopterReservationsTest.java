package com.codex.blacklight.military.aircraft;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source-owned volume lifecycle and exact containment boundaries. Not yet executed. */
class HelicopterReservationsTest {
    private static final UUID ONE=new UUID(1,1),TWO=new UUID(2,2);
    private static HelicopterStrafeCache.Snapshot path(Vec3 a,Vec3 b){return new HelicopterStrafeCache.Snapshot(new HelicopterStrafePath.Path(Vec3.ZERO,a,b,0),Vec3.ZERO,0,5,40,true);}
    @Test void strafeUsesHorizontalBoxAtTheThreeDimensionalMidpoint(){
        var volumes=new HelicopterReservations();assertTrue(volumes.reserveStrafe(ONE,path(new Vec3(-10,10,0),new Vec3(10,30,0)),5));
        assertEquals(new Vec3(0,20,0),volumes.volume(ONE).center());assertEquals(10,volumes.volume(ONE).lengthHalf());
        assertFalse(volumes.pointAvailable(new Vec3(10,25,5),0,null));assertTrue(volumes.pointAvailable(new Vec3(0,25.01,0),0,null));
        assertTrue(volumes.pointAvailable(new Vec3(0,20,0),0,ONE));
    }
    @Test void nonzeroRadiusUsesRoundedBoxCornersAndStrictTangency(){
        var volumes=new HelicopterReservations();volumes.reserveStrafe(ONE,path(new Vec3(-10,0,0),new Vec3(10,0,0)),5);
        assertTrue(volumes.pointAvailable(new Vec3(11,0,0),1,null));assertFalse(volumes.pointAvailable(new Vec3(10.99,0,0),1,null));
        assertTrue(volumes.pointAvailable(new Vec3(10.8,5.8,0),1,null),"not an independently inflated AABB");
    }
    @Test void updateUsesOnlyStartAndItsInclusiveOneUnitComponentTolerance(){
        var volumes=new HelicopterReservations();volumes.reserveStrafe(ONE,path(Vec3.ZERO,new Vec3(20,0,0)),5);var before=volumes.volume(ONE);
        volumes.reserveStrafe(ONE,path(new Vec3(1,1,1),new Vec3(100,10,0)),9);assertSame(before,volumes.volume(ONE));
        volumes.reserveStrafe(ONE,path(new Vec3(1.01,0,0),new Vec3(20,0,0)),5);assertNotSame(before,volumes.volume(ONE));
    }
    @Test void sphereBoundaryIsInclusiveAndRefreshDoesNotRotateOrFollowTinyMovements(){
        var volumes=new HelicopterReservations();volumes.reservePosition(ONE,Vec3.ZERO,5);var before=volumes.volume(ONE);
        assertFalse(volumes.pointAvailable(new Vec3(6,0,0),1,null));assertTrue(volumes.pointAvailable(new Vec3(6.01,0,0),1,null));
        volumes.reservePosition(ONE,new Vec3(1,1,1),7);assertSame(before,volumes.volume(ONE));
    }
    @Test void insertionDoesNotGrantAnExclusiveCollisionLockAndReleaseOnlyAffectsItsOwner(){
        var volumes=new HelicopterReservations();assertTrue(volumes.reservePosition(ONE,Vec3.ZERO,5));assertTrue(volumes.reservePosition(TWO,Vec3.ZERO,5));
        assertFalse(volumes.pointAvailable(Vec3.ZERO,0,ONE));volumes.release(ONE);assertFalse(volumes.pointAvailable(Vec3.ZERO,0,null));
        volumes.release(TWO);assertTrue(volumes.pointAvailable(Vec3.ZERO,0,null));
    }
    @Test void capacityIsFiftyOwnersAndReplacementDoesNotEvictOthers(){
        var volumes=new HelicopterReservations();for(int i=0;i<50;i++)assertTrue(volumes.reservePosition(new UUID(0,i),new Vec3(i*10,0,0),1));
        assertFalse(volumes.reservePosition(ONE,Vec3.ZERO,1));assertTrue(volumes.reservePosition(new UUID(0,0),new Vec3(500,0,0),1));assertEquals(50,volumes.size());
        volumes.discardMissing(id->id.equals(new UUID(0,0)));assertEquals(1,volumes.size());
    }
    @Test void openRunVolumeIncludesAimHeightAndUsesIndependentSideRadius(){
        var volumes=new HelicopterReservations();volumes.reservePosition(TWO,new Vec3(100,0,0),1);
        assertTrue(volumes.reserveOpen(ONE,new Vec3(0,30,-20),new Vec3(0,10,0),new Vec3(0,25,8),5,8));
        var volume=volumes.volume(ONE);assertEquals(new Vec3(0,20,-6),volume.center());
        assertEquals(19D,volume.lengthHalf());assertEquals(15D,volume.heightHalf());assertEquals(8F,volume.radius());
        assertFalse(volumes.pointAvailable(new Vec3(8,35,-6),0,null));assertTrue(volumes.pointAvailable(new Vec3(8.01,20,-6),0,null));
        assertTrue(volumes.pointAvailable(volume.center(),0,ONE));
        volumes.reserveStrafe(ONE,path(new Vec3(0,30,-20),new Vec3(0,25,8)),5);
        assertNotSame(volume,volumes.volume(ONE),"same-anchor tight action must not reuse an open action's three-height box");
        assertEquals(5D,volumes.volume(ONE).heightHalf());volumes.release(ONE);assertEquals(1,volumes.size());
    }
}
