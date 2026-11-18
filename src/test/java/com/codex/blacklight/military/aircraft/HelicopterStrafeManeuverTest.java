package com.codex.blacklight.military.aircraft;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pending execution. Expectations come from the cited source branches and
 * literal constants, not from snapshots of the Java implementation. */
class HelicopterStrafeManeuverTest {
    private static final Vec3 TARGET=new Vec3(0,64,0);
    private static HelicopterStrafePath.Path path(){
        return new HelicopterStrafePath.Path(TARGET,new Vec3(0,64,-10),new Vec3(0,64,10),0);
    }
    private static HelicopterStrafeManeuver.Route prepare(){
        return HelicopterStrafeManeuver.prepareRoute(path(),TARGET,64,false);
    }
    private static HelicopterStrafeManeuver.Route tight(){
        return HelicopterStrafeManeuver.tightRoute(path(),TARGET,64,64,false);
    }

    @Test void sourceRaisesCorridorBeforeAddingStartRiseAndConstructingIntermediate(){
        var normal=prepare();assertNotNull(normal);
        assertEquals(new Vec3(0,92,-10),normal.start());
        assertEquals(new Vec3(0,80,10),normal.end());
        assertEquals(new Vec3(0,80,0),normal.aim());
        assertEquals(new Vec3(0,80,5),normal.intermediate());
        var hydra=HelicopterStrafeManeuver.prepareRoute(path(),TARGET,64,true);
        assertEquals(101,hydra.start().y);assertEquals(89,hydra.aim().y);
        var uneven=HelicopterStrafeManeuver.tightRoute(path(),TARGET,64,95,false);
        assertEquals(new Vec3(0,92,-10),uneven.start());
        assertEquals(new Vec3(0,95,10),uneven.end());
        assertEquals(80,uneven.intermediate().y);
        assertEquals(2,uneven.intermediate().z,1e-9); // normalized (0,15,20)*15.
        assertNull(HelicopterStrafeManeuver.prepareRoute(path(),TARGET,Float.NaN,false));
        assertNull(HelicopterStrafeManeuver.tightRoute(path(),TARGET,64,Float.NaN,false));
    }

    @Test void sourceNormalizationKeepsShortNonzeroDirectionsAndRejectsOverflow(){
        var shortPath=new HelicopterStrafePath.Path(TARGET,TARGET,new Vec3(0,64,.00005),0);
        var route=HelicopterStrafeManeuver.prepareRoute(shortPath,TARGET,64,false);
        assertNotNull(route);
        assertEquals(15,route.intermediate().z,1e-9); // 107badf0: length squared exceeds 1e-10.
        var overflow=new HelicopterStrafePath.Path(TARGET,new Vec3(-Double.MAX_VALUE,64,0),new Vec3(Double.MAX_VALUE,64,0),0);
        assertNull(HelicopterStrafeManeuver.prepareRoute(overflow,TARGET,64,false));
    }

    @Test void nativeDistanceHelperKeepsItsAsymmetricEndpointAndSquaredDeltaGate(){
        Vec3 start=new Vec3(0,20,0),end=new Vec3(10,40,0);
        assertEquals(13,HelicopterStrafeManeuver.distanceSquared(start,end,new Vec3(-2,900,3)));
        assertEquals(9,HelicopterStrafeManeuver.distanceSquared(start,end,new Vec3(5,900,3)));
        assertEquals(113,HelicopterStrafeManeuver.distanceSquared(start,end,new Vec3(12,50,3)));
        var route=prepare();
        assertTrue(route.accepts(new Vec3(10,74,0))); // Both inclusive tolerance boundaries.
        assertFalse(route.accepts(new Vec3(10.01,64,0)));
        assertFalse(route.accepts(new Vec3(0,74.01,0)));
        var displaced=HelicopterStrafeManeuver.prepareRoute(path(),new Vec3(7,64,0),64,false);
        assertTrue(displaced.accepts(new Vec3(-7,64,0)),"equal squared distances despite 14-unit displacement");
    }

    @Test void prepareWaitStartsOnlyAfterCompletionAndAlignmentAndHasStrictThreshold(){
        var action=new HelicopterStrafeManeuver.Prepare(prepare(),TARGET,.5);
        action.tick(9,TARGET,false,true);assertEquals(HelicopterStrafeManeuver.Prepare.Stage.APPROACH,action.stage());
        action.tick(9,TARGET,true,true);assertEquals(HelicopterStrafeManeuver.Prepare.Stage.ALIGN,action.stage());
        action.tick(9,TARGET,true,false);assertEquals(HelicopterStrafeManeuver.Prepare.Stage.ALIGN,action.stage());
        action.tick(9,TARGET,true,true);assertEquals(0,action.elapsed());
        action.tick(.25F,TARGET,true,true);action.tick(.25F,TARGET,true,true);
        assertEquals(HelicopterStrafeManeuver.Prepare.Stage.WAIT,action.stage());
        action.tick(.001F,TARGET,true,true);assertEquals(HelicopterStrafeManeuver.Prepare.Stage.READY,action.stage());
        var moved=new HelicopterStrafeManeuver.Prepare(prepare(),TARGET,0);
        moved.tick(.05F,new Vec3(0,90,0),true,true);
        assertEquals(HelicopterStrafeManeuver.Prepare.Stage.ABORTED,moved.stage());
    }

    @Test void tightRunsReturnOnceAndChecksFortySquaredOnlyOnOutbound(){
        var action=new HelicopterStrafeManeuver.Tight(tight(),TARGET,.5,true);
        assertEquals(action.route().end(),action.destination());assertTrue(action.performingRun());
        action.tick(new Vec3(6,80,0),TARGET,false,false);assertTrue(action.performingRun()); //36 <=40.
        action.tick(action.route().end(),TARGET,true,false);
        assertEquals(HelicopterStrafeManeuver.Tight.Stage.TURN,action.stage());assertFalse(action.performingRun());
        assertNull(action.destination());
        action.tick(action.route().end(),TARGET,false,true); // No second movement-finished gate on turn.
        assertEquals(HelicopterStrafeManeuver.Tight.Stage.RETURN,action.stage());
        assertEquals(action.route().start(),action.destination());
        action.tick(new Vec3(7,80,0),TARGET,false,false); // No outbound-only40 check on return.
        assertEquals(HelicopterStrafeManeuver.Tight.Stage.RETURN,action.stage());
        action.tick(action.route().start(),TARGET,true,false);
        assertEquals(HelicopterStrafeManeuver.Tight.Stage.DONE,action.stage());assertFalse(action.performingRun());
        var offPath=new HelicopterStrafeManeuver.Tight(tight(),TARGET,0,true);
        offPath.tick(new Vec3(7,80,0),TARGET,false,false); //49 >40, not a forty-unit corridor.
        assertEquals(HelicopterStrafeManeuver.Tight.Stage.ABORTED,offPath.stage());
        var single=new HelicopterStrafeManeuver.Tight(tight(),TARGET,0,false);
        single.tick(single.route().end(),TARGET,true,true);
        assertEquals(HelicopterStrafeManeuver.Tight.Stage.DONE,single.stage());
    }

    @Test void lostTargetAndMalformedInputsCannotLeaveAnActiveRun(){
        var action=new HelicopterStrafeManeuver.Tight(tight(),TARGET,0,true);
        action.tick(action.route().start(),null,false,false);
        assertEquals(HelicopterStrafeManeuver.Tight.Stage.ABORTED,action.stage());
        assertFalse(HelicopterStrafeManeuver.aligned(Vec3.ZERO,new Vec3(0,0,1)));
        assertTrue(HelicopterStrafeManeuver.aligned(new Vec3(0,0,1),new Vec3(0,500,1)));
        assertFalse(HelicopterStrafeManeuver.aligned(new Vec3(0,0,-1),new Vec3(0,500,1)));
        assertFalse(HelicopterStrafeManeuver.sourceMovementFinished(2));
        assertFalse(HelicopterStrafeManeuver.sourceMovementFinished(3));
        assertTrue(HelicopterStrafeManeuver.sourceMovementFinished(0));
        assertThrows(IllegalArgumentException.class,()->HelicopterStrafeManeuver.speed(Double.NaN));
    }
}
