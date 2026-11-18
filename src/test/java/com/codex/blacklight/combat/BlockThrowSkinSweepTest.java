package com.codex.blacklight.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Analytic cargo/skin fixtures: expected contacts come from explicit planes,
 * not a second implementation of the continuous SAT under test. */
final class BlockThrowSkinSweepTest {
    private static final Vec3 X=new Vec3(1,0,0),Y=new Vec3(0,1,0),Z=new Vec3(0,0,1);
    private static final Vec3 UNIT_HALF=new Vec3(1,1,1);
    private static HydraContact.Box cube(Vec3 origin){return HydraContact.oriented(origin,UNIT_HALF,X,Y,Z);}
    private static HydraContact.Box shear(Vec3 origin){
        // Local (u,v,w) maps to (u+v,v,w): the left face is x-y=-1.
        return HydraContact.affine(origin,UNIT_HALF,X,new Vec3(1,1,0),Z);
    }
    private static void point(Vec3 expected,Vec3 actual,double tolerance){
        assertEquals(expected.x,actual.x,tolerance,"x");
        assertEquals(expected.y,actual.y,tolerance,"y");
        assertEquals(expected.z,actual.z,tolerance,"z");
    }

    @Test void axisAlignedSweepFindsTheFirstFaceAndExactEndContact(){
        var skin=cube(Vec3.ZERO);var half=new Vec3(.5,.25,.75);
        var hit=skin.sweepBox(new Vec3(-3,0,0),new Vec3(3,0,0),half);
        // Cargo's right face first reaches skin x=-1 when its centre is -1.5.
        assertNotNull(hit);assertEquals(.25,hit.fraction(),1e-12);
        point(new Vec3(-1.5,0,0),hit.center(),1e-12);
        point(new Vec3(-1,0,0),hit.point(),1e-12);
        point(new Vec3(-1,0,0),hit.normal(),1e-12);
        var end=skin.sweepBox(new Vec3(-3,0,0),new Vec3(-1.5,0,0),half);
        assertNotNull(end);assertEquals(1,end.fraction(),1e-12);
        assertNull(skin.sweepBox(new Vec3(-3,0,0),new Vec3(-1.5001,0,0),half),
                "stopping short of physical contact must not round up to a hit");
    }

    @Test void thinRotatedLimbRejectsItsEmptyBroadphaseCorner(){
        double k=Math.sqrt(.5);
        var skin=HydraContact.oriented(Vec3.ZERO,new Vec3(2,.2,.12),
                new Vec3(k,0,k),Y,new Vec3(-k,0,k));
        var from=new Vec3(1,-2,-1);var to=new Vec3(1,2,-1);
        assertTrue(skin.bounds.clip(from,to).isPresent(),"fixture crosses the enclosing AABB");
        // Along (-x+z)/sqrt(2), the cargo stays at -sqrt(2). Its half
        // projection is .05*sqrt(2), far outside the actual limb half .12.
        assertTrue(Math.sqrt(2)-.05*Math.sqrt(2)>.12);
        assertNull(skin.sweepBox(from,to,new Vec3(.05,.1,.05)));
    }

    @Test void cargoEdgeHitsWhileItsCenterRayMisses(){
        var skin=cube(Vec3.ZERO);var from=new Vec3(-3,1.25,0);var to=new Vec3(3,1.25,0);
        assertTrue(skin.clip(from,to).isEmpty(),"centre remains above the skin's y=1 face");
        var hit=skin.sweepBox(from,to,new Vec3(.5,.25,.5));
        // The cargo's bottom face lies exactly on y=1; its leading edge
        // reaches (-1,1,0) at the same x=-1.5 centre as the straight face case.
        assertNotNull(hit);assertEquals(.25,hit.fraction(),1e-12);
        point(new Vec3(-1.5,1.25,0),hit.center(),1e-12);
        point(new Vec3(-1,1,0),hit.point(),1e-12);
        assertNull(skin.sweepBox(from.add(0,.0001,0),to.add(0,.0001,0),new Vec3(.5,.25,.5)),
                "a real gap above the limb must remain a miss");
    }

    @Test void shearedFaceReportsTheActualSharedCargoCorner(){
        var skin=shear(Vec3.ZERO);var half=new Vec3(.375,.125,.25);
        var hit=skin.sweepBox(new Vec3(-3,0,0),new Vec3(3,0,0),half);
        // Cargo maximises x-y at (centre.x+.375, centre.y-.125).
        // That corner reaches x-y=-1 at centre.x=-1.5, independently
        // fixing both TOI and the unique XY contact (-1.125,-.125).
        assertNotNull(hit);assertEquals(.25,hit.fraction(),1e-12);
        point(new Vec3(-1.5,0,0),hit.center(),1e-12);
        point(new Vec3(-1.125,-.125,0),hit.point(),1e-10);
        point(new Vec3(-Math.sqrt(.5),Math.sqrt(.5),0),hit.normal(),1e-12);
        assertEquals(-1,hit.point().x-hit.point().y,1e-10,"point lies on the real sheared skin face");
        assertEquals(half.x,hit.point().x-hit.center().x,1e-10,"point also lies on the cargo leading face");
        assertEquals(-half.y,hit.point().y-hit.center().y,1e-10,"point also lies on the cargo bottom face");
    }

    @Test void initialOverlapIsAcceptedAtZeroBeforeMovingAway(){
        var skin=cube(Vec3.ZERO);var from=new Vec3(1.2,0,0);var half=new Vec3(.25,.25,.25);
        // Cargo already occupies x=.95..1.45, overlapping skin x<=1.
        var hit=skin.sweepBox(from,new Vec3(4,0,0),half);
        assertNotNull(hit);assertEquals(0,hit.fraction(),1e-12);
        point(from,hit.center(),1e-12);point(new Vec3(1,0,0),hit.point(),1e-12);
        point(X,hit.normal(),1e-12);
        var stationary=skin.sweepBox(from,from,half);
        assertNotNull(stationary);assertEquals(0,stationary.fraction(),1e-12);
        point(new Vec3(1,0,0),stationary.point(),1e-12);
    }

    @Test void aReceiverAlreadyBehindTheCargoCannotBeHitByMovingAway(){
        var skin=cube(Vec3.ZERO);var half=new Vec3(.5,.5,.5);
        assertNull(skin.sweepBox(new Vec3(3,0,0),new Vec3(9,0,0),half),
                "intersection of the infinite line behind t=0 is not a swept hit");
        assertNull(skin.sweepBox(new Vec3(-3,0,0),new Vec3(-9,0,0),half));
        assertNull(skin.sweepBox(new Vec3(3,0,0),new Vec3(3,0,0),half),"stationary separated cargo stays clear");
    }

    @Test void theSameTrajectoryKeepsItsTimeAndSkinPointAtFarWorldCoordinates(){
        var origin=new Vec3(29_000_000,64,-29_000_000);var half=new Vec3(.375,.125,.25);
        var local=shear(Vec3.ZERO).sweepBox(new Vec3(-3,0,0),new Vec3(3,0,0),half);
        var far=shear(origin).sweepBox(origin.add(-3,0,0),origin.add(3,0,0),half);
        assertNotNull(local);assertNotNull(far);
        assertEquals(.25,far.fraction(),1e-10);assertEquals(local.fraction(),far.fraction(),1e-10);
        point(origin.add(-1.5,0,0),far.center(),1e-8);
        point(origin.add(-1.125,-.125,0),far.point(),2e-8);
        point(local.point().add(origin),far.point(),2e-8);
        point(local.normal(),far.normal(),1e-10);
        assertNull(cube(origin).sweepBox(origin.add(3,0,0),origin.add(9,0,0),half));
    }
}
