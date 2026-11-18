package com.codex.blacklight.combat;
import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class BruteContactTest {
    private static AABB cow(double z){return new AABB(-.45,0,z-.45,.45,1.4,z+.45);}
    @Test void onlyVisibleFistsOwnHammerContact(){
        var all=BruteContact.body(MutantMove.BRUTE_HAMMER,0,Vec3.ZERO,0);
        var right=all.stream().filter(s->s.bone().equals("right_forearm")).toList();
        assertFalse(right.isEmpty());assertTrue(right.stream().allMatch(s->BruteContact.owns(MutantMove.BRUTE_HAMMER,s)));
        assertTrue(all.stream().filter(s->!s.bone().equals("right_forearm")).noneMatch(s->BruteContact.owns(MutantMove.BRUTE_HAMMER,s)));
        assertTrue(all.stream().filter(s->BruteContact.owns(MutantMove.BRUTE_HAMMER,s)).allMatch(s->s.bone().equals("right_forearm")));
    }
    @Test void hammerReachesCloseVictimButCannotUseItsOldCone(){
        assertTrue(BruteContact.canReach(MutantMove.BRUTE_HAMMER,Vec3.ZERO,0,cow(1.65)));
        assertFalse(BruteContact.canReach(MutantMove.BRUTE_HAMMER,Vec3.ZERO,0,cow(2.8)));
        assertFalse(BruteContact.canReach(MutantMove.BRUTE_HAMMER,Vec3.ZERO,0,cow(-1.65)));
    }
    @Test void slamDescendsToForwardGroundAfterItsTelegraph(){
        var high=BruteContact.boxes(MutantMove.BRUTE_SLAM,18,Vec3.ZERO,0,true);assertTrue(high.stream().allMatch(b->b.bounds.minY>2.4));
        var low=BruteContact.boxes(MutantMove.BRUTE_SLAM,26,Vec3.ZERO,0,true);assertTrue(low.stream().anyMatch(b->b.bounds.minY<.01));
        assertTrue(low.stream().allMatch(b->b.center.z>.5),"fists land forward of the torso");
    }
    @Test void sweepIncludesThinVictimBetweenFrames(){
        var fist=BruteContact.body(MutantMove.BRUTE_HAMMER,18.375F,Vec3.ZERO,0).stream().filter(s->s.fist()&&s.bone().equals("right_forearm")).findFirst().orElseThrow();
        var tiny=new AABB(fist.box().center,fist.box().center).inflate(.01);
        var hit=BruteContact.contact(MutantMove.BRUTE_HAMMER,18,19,Vec3.ZERO,Vec3.ZERO,0,tiny);assertNotNull(hit);assertTrue(tiny.inflate(.0001).contains(hit.point()));
    }
    @Test void chargeContactIncludesTravelAndRespectsInactiveClock(){
        var from=new Vec3(0,0,2);var to=new Vec3(0,0,2.34);var hit=BruteContact.contact(MutantMove.BRUTE_CHARGE,17,18,from,to,0,cow(3.4));assertNotNull(hit);
        assertNull(BruteContact.contact(MutantMove.BRUTE_CHARGE,5,6,from,to,0,cow(3.4)));
        assertNull(BruteContact.contact(MutantMove.BRUTE_CHARGE,34,35,from,to,0,cow(3.4)));
    }
    @Test void sweptBodyStopsAtThinObstacleBeforeItsNextPose(){
        var wall=new AABB(-4,0,2,4,5,2.125);
        var stop=HydraContact.obstacle(t->BruteContact.boxes(MutantMove.BRUTE_CHARGE,(float)t,new Vec3(0,0,(t-12)*1.2),0,false),12,13,List.of(wall));
        assertNotNull(stop);assertTrue(stop.safeAge()>=12&&stop.safeAge()<13);
        assertTrue(BruteContact.boxes(MutantMove.BRUTE_CHARGE,stop.safeAge(),new Vec3(0,0,(stop.safeAge()-12)*1.2),0,false).stream().noneMatch(b->b.touches(wall.deflate(.001))));
    }
    @Test void rotatedReachAndLargeWorldCoordinatesRemainStable(){
        var origin=new Vec3(4000000.125,84.5,-8000000.375);var local=BruteContact.body(MutantMove.BRUTE_HAMMER,18,Vec3.ZERO,90);var world=BruteContact.body(MutantMove.BRUTE_HAMMER,18,origin,90);
        for(int i=0;i<local.size();i++)assertEquals(0,world.get(i).box().center.subtract(origin).distanceTo(local.get(i).box().center),1e-8);
        assertTrue(BruteContact.canReach(MutantMove.BRUTE_HAMMER,origin,90,new AABB(origin.add(-2.1,0,-.45),origin.add(-1.2,1.4,.45))));
    }
    @Test void groundRecoveryClearsFlatAndRaisedSurfacesWithoutAnotherOverheadWindup(){
        for(double height:new double[]{0,.125,.5,.75,1}){
            var floor=new AABB(-4,-1,-4,4,0,4);
            var raised=new AABB(-1.5,0,.5,1.5,height,1.5);
            var surfaces=height==0?List.of(floor):List.of(floor,raised);
            var stop=HydraContact.obstacle(t->BruteContact.boxes(MutantMove.BRUTE_SLAM,(float)t,Vec3.ZERO,0,true),19,28,surfaces);
            assertNotNull(stop,"surface "+height);
            for(float elapsed=0;elapsed<=BruteRecovery.TICKS;elapsed+=.125F){
                var body=BruteContact.body(BruteRecovery.pose(stop.safeAge(),elapsed),Vec3.ZERO,0);
                for(var part:body){
                    for(var surface:surfaces)assertFalse(part.box().touches(surface.deflate(.004)),"recovery penetrates height="+height+" elapsed="+elapsed+" bone="+part.bone());
                    if(part.fist())assertTrue(part.box().center.y<2.3,"recovery must stay below the overhead windup: height="+height+" elapsed="+elapsed+" bone="+part.bone()+" center="+part.box().center);
                }
            }
        }
    }
    @Test void recoveryStartsAtActualContactAndEndsInIdleOnAnyTrackingFrame(){
        for(float contactAge:new float[]{23.593506F,25.192383F}){
            var entry=CreaturePoseTable.BRUTE.sample("ground_slam",contactAge);
            var first=BruteRecovery.pose(contactAge,0);var held=BruteRecovery.pose(contactAge,BruteRecovery.HOLD);
            var idle=CreaturePoseTable.BRUTE.sample("idle",0);var end=BruteRecovery.pose(contactAge,BruteRecovery.TICKS+4);
            for(int b=0;b<entry.length;b++){assertArrayEquals(entry[b],first[b]);assertArrayEquals(entry[b],held[b]);assertArrayEquals(idle[b],end[b],.00001F);}
            var before=BruteRecovery.pose(contactAge,7.999F);var after=BruteRecovery.pose(contactAge,8.001F);
            for(int b=0;b<before.length;b++)assertArrayEquals(before[b],after[b],.00001F);
        }
    }
}
