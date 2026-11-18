package com.codex.blacklight.military.aircraft;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source-derived cache transition cases. Not run during the code-only phase. */
class HelicopterStrafeCacheTest {
    private static final Vec3 CRAFT=new Vec3(0,30,-15),TARGET=Vec3.ZERO;
    private static final class Grid implements HelicopterStrafePath.Grid {
        int reads;
        public float cellSize(){return 1;}
        public float originX(){return -160;}
        public float originZ(){return -160;}
        public float height(int x,int z){reads++;return 0;}
    }
    private static HelicopterStrafeCache.Snapshot query(HelicopterStrafeCache cache,Grid grid){return cache.query(grid,CRAFT,TARGET,.5F,2,5,40,false);}
    @Test void unqueriedCandidateStaysAtNegativeAge(){
        var cache=new HelicopterStrafeCache();cache.advance(20);
        assertEquals(-1F,cache.candidate().age());assertFalse(cache.candidate().valid());assertFalse(cache.committed().valid());
    }
    @Test void sourceTimeToleranceIsInclusiveAndOnlyCandidateAges(){
        var cache=new HelicopterStrafeCache();var grid=new Grid();assertTrue(query(cache,grid).valid());cache.commit();int reads=grid.reads;
        cache.advance(.5F);query(cache,grid);assertEquals(reads,grid.reads);assertEquals(.5F,cache.candidate().age());assertEquals(0F,cache.committed().age());
        cache.advance(.001F);query(cache,grid);assertTrue(grid.reads>reads);assertEquals(0F,cache.candidate().age());
    }
    @Test void maximumAndFarthestDoNotBelongToTheReuseKey(){
        var cache=new HelicopterStrafeCache();var grid=new Grid();var first=query(cache,grid);int reads=grid.reads;
        assertSame(first,cache.query(grid,CRAFT,TARGET,.5F,2,5,80,true));assertEquals(reads,grid.reads);assertEquals(40,cache.candidate().maximum());
        var next=cache.query(grid,CRAFT,TARGET,.5F,2,6,80,true);assertTrue(grid.reads>reads);assertEquals(6,next.minimum());assertEquals(80,next.maximum());
    }
    @Test void movementToleranceComparesGroundAndIncludesVerticalDistance(){
        var cache=new HelicopterStrafeCache();var grid=new Grid();query(cache,grid);int reads=grid.reads;
        cache.query(grid,CRAFT,new Vec3(0,2,0),.5F,2,5,40,false);assertEquals(reads,grid.reads);
        cache.query(grid,CRAFT,new Vec3(0,2.01,0),.5F,2,5,40,false);assertTrue(grid.reads>reads);
    }
    @Test void commitmentIsAnIndependentSnapshotOfTheSelectedCandidate(){
        var cache=new HelicopterStrafeCache();var grid=new Grid();var first=query(cache,grid);assertSame(first,cache.commit());
        cache.query(grid,CRAFT,new Vec3(20,0,0),.5F,2,5,40,false);
        assertEquals(TARGET,cache.committed().targetAtPlan());assertEquals(first.path(),cache.committed().path());assertNotEquals(first.path(),cache.candidate().path());
    }
    @Test void missingTargetInvalidatesWithoutClearingTheCachedClockOrForcingARescan(){
        var cache=new HelicopterStrafeCache();var grid=new Grid();query(cache,grid);cache.commit();cache.advance(.25F);int reads=grid.reads;
        var lost=cache.query(grid,CRAFT,null,.5F,2,5,40,false);assertFalse(lost.valid());assertEquals(.25F,lost.age());assertTrue(cache.committed().valid());
        assertFalse(query(cache,grid).valid());assertEquals(reads,grid.reads);
        cache.advance(.3F);assertTrue(query(cache,grid).valid());
    }
    @Test void failedCorridorRetainsItsGroundOutputAndIsCachedUntilToleranceExpires(){
        var cache=new HelicopterStrafeCache();var grid=new Grid();
        var failed=cache.query(grid,CRAFT,new Vec3(20,0,0),.5F,2,5,6,false);int reads=grid.reads;
        assertFalse(failed.valid());assertEquals(new Vec3(20,0,0),failed.path().targetGround());
        assertSame(failed,cache.query(grid,CRAFT,new Vec3(20,0,0),.5F,2,5,6,false));assertEquals(reads,grid.reads);
        cache.advance(.51F);assertTrue(cache.query(grid,CRAFT,new Vec3(20,0,0),.5F,2,-1,40,false).valid());
    }
}
