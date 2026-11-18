package com.codex.blacklight.military.aircraft;

import java.util.function.DoubleSupplier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source-derived destination scenarios; intentionally unrun in the code-only phase. */
class HelicopterCombatDestinationTest {
    private static final Vec3 CRAFT=new Vec3(0,40,50);
    private static class World implements HelicopterCombatDestination.Environment {
        float ground,area;int areas,available,routes;boolean reserved,blocked;
        public float groundAt(Vec3 point){return ground;}
        public float areaHeight(Vec3 point,float width){assertEquals(18F,width);areas++;return area;}
        public boolean pointAvailable(Vec3 point,float radius){assertEquals(9F,radius);available++;return !reserved;}
        public boolean clearRoute(Vec3 point){routes++;return !blocked;}
    }
    private static Vec3 find(World world){return HelicopterCombatDestination.find(CRAFT,Vec3.ZERO,false,false,()->.5,world);}

    @Test void flatGroundUsesAllTwentyCandidatesAndSkipsFallback(){
        var world=new World();assertEquals(new Vec3(0,8,17.5),find(world));assertEquals(20,world.areas);assertEquals(20,world.routes);
    }
    @Test void targetClassesRetainTheirAuthoredRadiiAndTerrainFloors(){
        assertEquals(new Vec3(0,15,27.5),HelicopterCombatDestination.find(CRAFT,Vec3.ZERO,true,false,()->.5,new World()));
        assertEquals(new Vec3(0,8,17.5),HelicopterCombatDestination.find(CRAFT,Vec3.ZERO,false,true,()->.5,new World()));
    }
    @Test void radiusIsLinearAndAngleIsRelativeToTargetToCraft(){
        DoubleSupplier random=new DoubleSupplier(){int n;public double getAsDouble(){return n++%2==0?.25:.5;}};
        var point=HelicopterCombatDestination.find(new Vec3(10,30,20),new Vec3(10,12,-20),false,false,random,new World());
        assertEquals(new Vec3(10,8,-6.25),point);
    }
    @Test void lowestValidScansPastFirstAndKeepsFirstEqualHeight(){
        var world=new World(){public float areaHeight(Vec3 point,float width){super.areaHeight(point,width);return areas==1?22:0;}};
        DoubleSupplier random=new DoubleSupplier(){int n;public double getAsDouble(){int draw=n++;return draw%2==1?.5:draw==0?.1:draw==2?.9:.8;}};
        assertEquals(new Vec3(0,8,23.5),HelicopterCombatDestination.find(CRAFT,Vec3.ZERO,false,false,random,world));
        assertEquals(20,world.areas);
    }
    @Test void reservedPrimaryCandidatesAllowTheSeparateFallbackRound(){
        var world=new World(){public boolean pointAvailable(Vec3 point,float radius){super.pointAvailable(point,radius);return available>20;}};
        assertEquals(new Vec3(0,8,22.5),find(world));assertEquals(40,world.areas);assertEquals(20,world.routes);
    }
    @Test void blockedOrReservedCandidatesNeverBecomeDestinations(){
        var blocked=new World();blocked.blocked=true;assertNull(find(blocked));assertEquals(40,blocked.routes);
        var reserved=new World();reserved.reserved=true;assertNull(find(reserved));assertEquals(40,reserved.available);assertEquals(0,reserved.routes);
    }
    @Test void unavailableGroundOrAreaFailsClosed(){
        var ground=new World();ground.ground=Float.NaN;assertNull(find(ground));assertEquals(0,ground.areas);
        var area=new World();area.area=Float.NaN;assertNull(find(area));assertEquals(40,area.areas);assertEquals(0,area.routes);
    }
    @Test void airHeightCheckRaisesToHighTargetInsteadOfUsingTerrainDifference(){
        assertEquals(new Vec3(0,200,17.5),HelicopterCombatDestination.find(CRAFT,new Vec3(0,200,0),false,false,()->.5,new World()));
        var world=new World();world.area=45;assertEquals(new Vec3(0,53,22.5),find(world));assertEquals(40,world.areas);
        world=new World();world.area=200;assertNull(find(world));
    }
    @Test void absoluteCeilingIsInclusiveAfterAirHeightCorrection(){
        var world=new World();world.area=387;
        assertEquals(395D,HelicopterCombatDestination.find(CRAFT,new Vec3(0,395,0),false,false,()->.5,world).y);
        world.area=388;assertNull(HelicopterCombatDestination.find(CRAFT,new Vec3(0,395,0),false,false,()->.5,world));
    }
    @Test void minimumMovementIncludesHeightAndAcceptsEquality(){
        assertEquals(new Vec3(0,8,17.5),HelicopterCombatDestination.find(new Vec3(0,0,17.5),Vec3.ZERO,false,false,()->.5,new World()));
        assertEquals(new Vec3(0,8,22.5),HelicopterCombatDestination.find(new Vec3(0,8,17.5),Vec3.ZERO,false,false,()->.5,new World()));
    }
    @Test void relocationReturnsFirstValidWithoutSearchingForALowerPoint(){
        var world=new World(){public float areaHeight(Vec3 point,float width){super.areaHeight(point,width);return areas==1?12:0;}};
        assertEquals(new Vec3(0,20,20),HelicopterCombatDestination.reposition(CRAFT,Vec3.ZERO,()->.5,world));
        assertEquals(1,world.areas);assertEquals(1,world.routes);
    }
    @Test void relocationHeightFailureUsesTwentyAttemptLowestValidFallback(){
        var world=new World(){public float areaHeight(Vec3 point,float width){super.areaHeight(point,width);return areas<=11?20:0;}};
        assertEquals(new Vec3(0,8,22.5),HelicopterCombatDestination.reposition(CRAFT,Vec3.ZERO,()->.5,world));
        assertEquals(30,world.areas);assertEquals(20,world.routes);
    }
    @Test void relocationStopsAfterTenPrimaryAndTwentyFallbackFailures(){
        var world=new World();world.blocked=true;
        assertNull(HelicopterCombatDestination.reposition(CRAFT,Vec3.ZERO,()->.5,world));
        assertEquals(30,world.areas);assertEquals(30,world.routes);
    }
}
