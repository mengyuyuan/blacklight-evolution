package com.codex.blacklight.military.aircraft;
import java.util.*;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterRouteTest {
    private static HelicopterRoute finish(HelicopterRoute search){for(int i=0;i<1200&&search.status()==HelicopterRoute.Status.SEARCHING;i++)search.advance(6);return search;}
    private static boolean crosses(Vec3 a,Vec3 b,AABB obstacle){return obstacle.contains(a)||obstacle.contains(b)||obstacle.clip(a,b).isPresent();}
    @Test void clearNearbyDestinationUsesOneContinuousSegment(){
        var goal=new Vec3(6,1,0);var route=new HelicopterRoute(Vec3.ZERO,goal,(a,b)->true);
        assertEquals(HelicopterRoute.Status.READY,route.status());assertEquals(List.of(goal),route.path());assertEquals(0,route.expanded());
    }
    @Test void blockedDirectRouteBendsAroundAnActualTallObstacle(){
        var wall=new AABB(-2,-100,-10,2,100,10);var start=new Vec3(-24,0,0);var goal=new Vec3(24,0,0);
        var route=finish(new HelicopterRoute(start,goal,(a,b)->!crosses(a,b,wall)));
        assertEquals(HelicopterRoute.Status.READY,route.status());assertTrue(route.path().stream().anyMatch(p->Math.abs(p.z)>10));
        Vec3 before=start;for(var point:route.path()){assertFalse(crosses(before,point,wall));before=point;}assertEquals(goal,before);
    }
    @Test void aLongLowBarrierRequiresAThreeDimensionalClimb(){
        var wall=new AABB(-2,-100,-100,2,6,100);var route=finish(new HelicopterRoute(new Vec3(-20,0,0),new Vec3(20,0,0),(a,b)->!crosses(a,b,wall)));
        assertEquals(HelicopterRoute.Status.READY,route.status());assertTrue(route.path().stream().anyMatch(p->p.y>6));
    }
    @Test void cornerAndThinWallsCannotBeSkippedBetweenGridNodes(){
        var wall=new AABB(-.1,-100,-10,.1,100,10);var start=new Vec3(-18,0,0);var route=finish(new HelicopterRoute(start,new Vec3(18,0,0),(a,b)->!crosses(a,b,wall)));
        assertEquals(HelicopterRoute.Status.READY,route.status());Vec3 before=start;for(var point:route.path()){assertFalse(crosses(before,point,wall));before=point;}
    }
    @Test void eachAdvanceHasAStrictQueryAndExpansionBound(){
        var route=new HelicopterRoute(new Vec3(-30,0,0),new Vec3(30,0,0),(a,b)->a.x*b.x>=0);
        for(int i=0;i<30&&route.status()==HelicopterRoute.Status.SEARCHING;i++){
            int queries=route.queries(),expanded=route.expanded();int spent=route.advance(3);
            assertTrue(spent<=3&&route.expanded()-expanded<=3);assertTrue(route.queries()-queries<=21);
        }
        int before=route.queries();assertEquals(0,route.advance(0));assertEquals(before,route.queries());
    }
    @Test void unreachableGoalTerminatesAtItsFiniteNodeBudget(){
        var route=finish(new HelicopterRoute(new Vec3(-20,0,0),new Vec3(20,0,0),(a,b)->a.x*b.x>=0&&!(a.x<0&&b.x==0)));
        assertEquals(HelicopterRoute.Status.FAILED,route.status());assertTrue(route.expanded()<=HelicopterRoute.MAX_NODES);assertTrue(route.path().isEmpty());
    }
    @Test void unavailableDestinationNeverBeginsExploringUnknownTerrain(){
        var seen=new ArrayList<Vec3>();var goal=new Vec3(100,0,0);var route=new HelicopterRoute(Vec3.ZERO,goal,(a,b)->{seen.add(a);return false;});
        assertEquals(HelicopterRoute.Status.FAILED,route.status());assertEquals(List.of(goal),seen);assertEquals(0,route.advance(6));
    }
    @Test void invalidOrUnboundedRequestsDoNotQueryTheWorld(){
        for(var goal:List.of(new Vec3(Double.NaN,0,0),new Vec3(1000,0,0))){var route=new HelicopterRoute(Vec3.ZERO,goal,(a,b)->{fail("invalid query reached terrain");return true;});assertEquals(HelicopterRoute.Status.FAILED,route.status());}
    }
}
