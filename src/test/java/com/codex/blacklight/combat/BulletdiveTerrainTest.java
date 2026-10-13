package com.codex.blacklight.combat;

import java.time.Duration;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BulletdiveTerrainTest {
    @Test void actualHeightContinuesGrowingBeyondFormerPowerCaps() {
        var prior=BulletdiveTerrain.plan(0);
        for(int height=1;height<=20_000;height++) {
            var plan=BulletdiveTerrain.plan(height);
            assertTrue(plan.radius()>=prior.radius());assertTrue(plan.depth()>=prior.depth());
            assertTrue(plan.energy()>=prior.energy());assertTrue(plan.budget()>=prior.budget());
            assertEquals(height,plan.height());prior=plan;
        }
        var oldEdge=BulletdiveTerrain.plan(512);var higher=BulletdiveTerrain.plan(4096);
        var veryHigh=BulletdiveTerrain.plan(1_000_000);
        assertTrue(oldEdge.radius()>12);
        assertTrue(higher.depth()>10&&higher.budget()>4096);
        assertTrue(veryHigh.radius()>higher.radius()&&veryHigh.depth()>higher.depth());
        assertTrue(veryHigh.energy()>higher.energy()&&veryHigh.budget()>higher.budget());
        assertEquals(1_000_000,veryHigh.height());
        assertEquals((long)Math.floor(veryHigh.energy()),veryHigh.budget());
        assertEquals(0,BulletdiveTerrain.plan(6).budget());
        assertEquals(0,BulletdiveTerrain.plan(Double.NaN).budget());
        assertEquals(0,BulletdiveTerrain.plan(Double.POSITIVE_INFINITY).budget());
        assertEquals(0,BulletdiveTerrain.plan(-50).budget());
    }
    @Test void bowlGetsShallowerOutwardAndNeverExtendsOutsideItsCircle() {
        var plan=BulletdiveTerrain.plan(120);int prior=plan.depth();
        for(long distance=0;distance<=(long)plan.radius()*plan.radius();distance++) {
            int depth=BulletdiveTerrain.columnDepth(plan,distance);
            assertTrue(depth<=prior&&depth>=0);prior=depth;
        }
        assertEquals(plan.depth(),BulletdiveTerrain.columnDepth(plan,0));
        assertEquals(0,BulletdiveTerrain.columnDepth(plan,(long)plan.radius()*plan.radius()));
    }
    @Test void hardnessLimitsStrengthButContainersAndMineableBlastResistantBlocksQualify() {
        var low=BulletdiveTerrain.plan(12);var high=BulletdiveTerrain.plan(80);
        assertTrue(BulletdiveTerrain.canBreak(low,0,0,.5F,2,false));
        assertFalse(BulletdiveTerrain.canBreak(low,0,0,5,6,false));
        assertTrue(BulletdiveTerrain.canBreak(high,0,0,5,6,false));
        assertFalse(BulletdiveTerrain.canBreak(high,0,0,-1,0,false));
        assertTrue(BulletdiveTerrain.canBreak(high,0,0,.1F,1200,false));
        assertTrue(BulletdiveTerrain.canBreak(high,0,0,2.5F,12.5F,true));
        assertFalse(BulletdiveTerrain.canBreak(high,0,0,50,1200,false));
        assertTrue(BulletdiveTerrain.canBreak(BulletdiveTerrain.plan(500),0,0,50,1200,false));
        assertFalse(BulletdiveTerrain.canBreak(high,0,0,Float.NaN,1,false));
        assertFalse(BulletdiveTerrain.canBreak(high,0,0,.1F,Float.NaN,false));
        assertTrue(BulletdiveTerrain.hardnessCost(5)>BulletdiveTerrain.hardnessCost(.5F));
        assertTrue(Double.isFinite(BulletdiveTerrain.hardnessCost(Float.MAX_VALUE)));
        assertFalse(BulletdiveTerrain.canBreak(high,0,0,1000,6,false));
        assertTrue(BulletdiveTerrain.canBreak(BulletdiveTerrain.plan(1_000_000),0,0,1000,6,false));
    }
    @Test void dropProducingBreaksHaveASeparateGlobalSliceLimitAndResumeNextTick() {
        var clock=new AtomicLong();int remaining=35,total=0,ticks=0;
        while(remaining>0){
            var budget=new BulletdiveTerrain.TickBudget(BulletdiveTerrain.MAX_WORK_PER_TICK,
                    BulletdiveTerrain.MAX_BREAKS_PER_TICK,3_000_000,clock::get);
            int thisTick=0;
            while(remaining>0&&budget.tryCandidate()){
                // Each operation represents one indivisible vanilla destroyBlock
                // call with loot; no silent remainder-removal branch is needed.
                budget.recordBreak();thisTick++;total++;remaining--;
            }
            assertTrue(thisTick<=32);ticks++;
        }
        assertEquals(35,total);assertEquals(2,ticks);
    }
    @Test void nonBreakingCandidatesCannotRunPastTheCandidateOrTimeBudget() {
        var clock=new AtomicLong(10);
        var candidates=new BulletdiveTerrain.TickBudget(192,8,3_000_000,clock::get);
        int visited=0;while(candidates.tryCandidate())visited++;
        assertEquals(192,visited);
        var deadline=new BulletdiveTerrain.TickBudget(192,8,3_000_000,clock::get);
        assertTrue(deadline.tryCandidate());deadline.recordBreak();
        // A vanilla loot/protection callback may overrun; finish that atomic
        // break, then stop before starting any additional block or chunk probe.
        clock.addAndGet(3_000_001);
        assertFalse(deadline.tryCandidate());
    }
    @Test void nanotimeWrapDoesNotTurnTheWorkSliceIntoAnUnlimitedLoop() {
        var clock=new AtomicLong(Long.MAX_VALUE-10);
        var budget=new BulletdiveTerrain.TickBudget(192,8,30,clock::get);
        clock.set(Long.MIN_VALUE+10);
        assertTrue(budget.tryCandidate());
        clock.set(Long.MIN_VALUE+20);
        assertFalse(budget.tryCandidate());
    }
    @Test void repeatedSessionsCannotAccumulateActiveJobsOrRemoveTheirReplacement() {
        var tasks=new BulletdiveTerrain.OwnerTasks<Integer>();
        var first=UUID.randomUUID();var second=UUID.randomUUID();
        var canceled=new AtomicInteger();
        tasks.install(first,0,old->fail("empty owner canceled a task"));
        tasks.install(second,-1,old->fail("other owner canceled a task"));
        for(int session=1;session<=10_000;session++){
            int expectedPrevious=session-1;
            tasks.install(first,session,old->{
                assertEquals(expectedPrevious,old);
                // The old task's finish() callback runs during installation.
                tasks.complete(first,old);canceled.incrementAndGet();
            });
            assertEquals(2,tasks.size());assertEquals(session,tasks.current(first));
            assertEquals(-1,tasks.current(second));
        }
        assertEquals(10_000,canceled.get());
        tasks.complete(first,10_000);assertEquals(1,tasks.size());
        tasks.clear();assertTrue(tasks.snapshot().isEmpty());
    }
    @Test void loadedColumnStreamIsUniqueCircularAndNearToFarAcrossChunkBoundaries() {
        var plan=BulletdiveTerrain.plan(80);
        var cursor=new BulletdiveTerrain.ColumnCursor(plan,0,0,(x,z)->x>=-1&&x<=0&&z>=-1&&z<=0);
        var positions=new HashSet<String>();long prior=-1;int steps=0;
        while(!cursor.exhausted()){
            assertTrue(++steps<250,"cursor did not terminate over four loaded chunks");
            var column=cursor.step();if(column==null)continue;
            assertEquals((long)column.x()*column.x()+(long)column.z()*column.z(),column.distanceSquared());
            assertTrue(column.distanceSquared()>=prior,"stream escaped radial order");
            assertTrue(column.distanceSquared()<(long)plan.radius()*plan.radius());
            assertTrue(BulletdiveTerrain.columnDepth(plan,column.distanceSquared())>0);
            assertTrue(positions.add(column.x()+","+column.z()),"duplicate column");
            prior=column.distanceSquared();
        }
        assertTrue(positions.contains("0,0"));assertEquals(193,positions.size());
    }
    @Test void enormousFallScansOnlyItsLoadedConnectedEnvironment() {
        assertTimeoutPreemptively(Duration.ofSeconds(2),()->{
            var plan=BulletdiveTerrain.plan(1_000_000_000_000D);
            assertTrue(plan.radius()>700_000&&plan.depth()>9_000&&plan.budget()>Integer.MAX_VALUE);
            var calls=new AtomicInteger();
            var cursor=new BulletdiveTerrain.ColumnCursor(plan,8,8,(x,z)->{
                calls.incrementAndGet();return x==0&&z==0||x==1&&z==0||x==0&&z==1||x==100&&z==100;
            });
            long prior=-1;int columns=0,steps=0;
            while(!cursor.exhausted()){
                assertTrue(++steps<800,"stream traversed the huge theoretical radius or jumped an unloaded gap");
                var column=cursor.step();if(column==null)continue;
                assertTrue(column.distanceSquared()>=prior);prior=column.distanceSquared();columns++;
            }
            assertEquals(3*256,columns);
            assertEquals(10,calls.get());assertEquals(10,cursor.trackedChunks());
        });
    }
    @Test void distanceAndBowlArithmeticRemainValidBeyondIntSquaredRange() {
        var plan=BulletdiveTerrain.plan(1e15);
        long squared=(long)plan.radius()*plan.radius();assertTrue(squared>Integer.MAX_VALUE);
        assertEquals(plan.depth(),BulletdiveTerrain.columnDepth(plan,0));
        assertTrue(BulletdiveTerrain.columnDepth(plan,squared/2)>0);
        assertEquals(0,BulletdiveTerrain.columnDepth(plan,squared));
        assertEquals(0,BulletdiveTerrain.columnDepth(plan,-1));
        var largest=BulletdiveTerrain.plan(Double.MAX_VALUE);
        assertEquals(Double.MAX_VALUE,largest.height());assertTrue(Double.isFinite(largest.energy()));
        assertTrue(BulletdiveTerrain.columnDepth(largest,(long)Integer.MAX_VALUE*Integer.MAX_VALUE/2)>0);
    }
    @Test void largeWorldCoordinatesPreserveSmallLocalOffsets() {
        int x=29_000_001,z=-29_000_001;
        var cursor=new BulletdiveTerrain.ColumnCursor(BulletdiveTerrain.plan(1e12),x,z,
                (chunkX,chunkZ)->chunkX==(x>>4)&&chunkZ==(z>>4));
        int columns=0,steps=0;
        while(!cursor.exhausted()){
            assertTrue(++steps<270);var column=cursor.step();if(column==null)continue;
            assertTrue(Math.abs(column.x())<=15&&Math.abs(column.z())<=15);
            assertEquals((long)column.x()*column.x()+(long)column.z()*column.z(),column.distanceSquared());columns++;
        }
        assertEquals(256,columns);
    }
}
