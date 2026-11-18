package com.codex.blacklight.client.renderer;

import java.util.*;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RagdollTerrainSupportTest {
    @Test void groundedHandsStaySupportedWhenAShorterUpwardEscapeExists(){
        var skin=new ArrayList<>(box(new Vec3(-.8,.003,-1.5),new Vec3(.8,.65,-.8)));
        skin.addAll(box(new Vec3(-.25,.3,-.4),new Vec3(.25,.8,.6)));
        var terrain=List.of(new AABB(-3,-1,-3,3,0,3),new AABB(-2,0,0,2,.5,2));
        var shift=RagdollTerrainSupport.settle(skin,terrain);
        assertEquals(0,shift.x,1e-8);assertEquals(0,shift.y,1e-8);assertEquals(-.601,shift.z,1e-8);
        assertEquals(Vec3.ZERO,RagdollTerrainSupport.settle(skin.stream().map(p->p.add(shift)).toList(),terrain));
    }
    @Test void adjacentSlabsHaveNoExitThroughTheirInternalSeam(){
        var skin=new ArrayList<>(box(new Vec3(-.8,.003,-1.5),new Vec3(.8,.65,-.8)));
        skin.addAll(box(new Vec3(-.1,.067,-.4),new Vec3(.1,.8,.3)));
        var terrain=List.of(new AABB(-3,-1,-3,3,0,3),new AABB(-1,0,0,0,.5,2),new AABB(0,0,0,1,.5,2));
        var shift=RagdollTerrainSupport.settle(skin,terrain);
        assertEquals(0,shift.x,1e-8);assertEquals(0,shift.y,1e-8);assertEquals(-.301,shift.z,1e-8);
        assertEquals(Vec3.ZERO,RagdollTerrainSupport.settle(skin.stream().map(p->p.add(shift)).toList(),terrain));
    }
    @Test void raisedFootBeyondTheSlabDoesNotBlockAShallowSideEscape(){
        var skin=new ArrayList<>(box(new Vec3(-.8,.003,-1.5),new Vec3(.8,.65,-.8)));
        skin.addAll(box(new Vec3(-.25,.067,-.4),new Vec3(.25,.8,.077)));
        skin.addAll(box(new Vec3(-.25,.6,-.2),new Vec3(.25,.95,1.2)));
        var terrain=List.of(new AABB(-3,-1,-3,3,0,3),new AABB(-2,0,0,2,.5,2));
        var shift=RagdollTerrainSupport.settle(skin,terrain);
        assertEquals(0,shift.x,1e-8);assertEquals(0,shift.y,1e-8);assertEquals(-.078,shift.z,1e-8);
        assertEquals(Vec3.ZERO,RagdollTerrainSupport.settle(skin.stream().map(p->p.add(shift)).toList(),terrain));
    }
    @Test void lowSkinAwayFromTheStepDoesNotOverstateVerticalSupport(){
        var skin=new ArrayList<>(box(new Vec3(-.2,.003,-2),new Vec3(.2,.6,-1.2)));
        skin.addAll(box(new Vec3(-.2,.45,-.2),new Vec3(.2,1.1,.2)));
        var terrain=List.of(new AABB(-1,0,-1,1,.5,1));
        var shift=RagdollTerrainSupport.settle(skin,terrain);
        assertEquals(0,shift.x,1e-8);assertEquals(.051,shift.y,1e-8);assertEquals(0,shift.z,1e-8);
        assertEquals(Vec3.ZERO,RagdollTerrainSupport.settle(skin.stream().map(p->p.add(shift)).toList(),terrain));
    }
    @Test void shallowToeAcrossSlabEdgeDoesNotLiftGroundedHands(){
        var skin=new ArrayList<>(box(new Vec3(-.8,.003,-1.5),new Vec3(.8,.65,-.8)));
        skin.addAll(box(new Vec3(-.25,.067,-.4),new Vec3(.25,.8,.077)));
        var terrain=List.of(new AABB(-3,-1,-3,3,0,3),new AABB(-2,0,0,2,.5,2));
        var shift=RagdollTerrainSupport.settle(skin,terrain);
        assertEquals(0,shift.x,1e-8);assertEquals(0,shift.y,1e-8);assertEquals(-.078,shift.z,1e-8);
        var settled=skin.stream().map(p->p.add(shift)).toList();
        assertEquals(Vec3.ZERO,RagdollTerrainSupport.correction(settled,terrain));
        assertEquals(.003,settled.stream().mapToDouble(p->p.y).min().orElseThrow(),1e-8);
    }
    @Test void landingOnTheMiddleOfTheStepKeepsVerticalSupport(){
        var skin=box(new Vec3(-.2,.45,-.2),new Vec3(.2,1.1,.2));
        var terrain=List.of(new AABB(-1,0,-1,1,.5,1));
        var shift=RagdollTerrainSupport.settle(skin,terrain);
        assertEquals(new Vec3(0,.051,0).distanceTo(shift),0,1e-8);
        assertEquals(Vec3.ZERO,RagdollTerrainSupport.settle(skin.stream().map(p->p.add(shift)).toList(),terrain));
    }
    @Test void clearingAnEdgeAlsoHonorsTheLowerFloor(){
        var skin=new ArrayList<>(box(new Vec3(-.8,-.02,-1.5),new Vec3(.8,.65,-.8)));
        skin.addAll(box(new Vec3(-.25,.067,-.4),new Vec3(.25,.8,.077)));
        var terrain=List.of(new AABB(-3,-1,-3,3,0,3),new AABB(-2,0,0,2,.5,2));
        var shift=RagdollTerrainSupport.settle(skin,terrain);
        assertEquals(.021,shift.y,1e-8);assertEquals(-.078,shift.z,1e-8);
        assertEquals(Vec3.ZERO,RagdollTerrainSupport.correction(skin.stream().map(p->p.add(shift)).toList(),terrain));
    }
    private static List<Vec3> box(Vec3 min,Vec3 max){return FiniteMeshCutTest.box(min,max,1).stream().flatMap(List::stream).toList();}
    @Test void slabUnderTheEdgeSupportsSkinWhoseCenterIsOverAir(){
        var skin=box(new Vec3(-.4,.3,-.4),new Vec3(.4,.7,.4));
        assertEquals(.201,RagdollTerrainSupport.lift(skin,List.of(new AABB(.2,0,-1,1,.5,1))),1e-8);
    }
    @Test void clipsSlopedSkinToTheActualStepFootprint(){
        var skin=List.of(new Vec3(0,.2,0),new Vec3(1,1.2,0),new Vec3(1,1.2,1),new Vec3(0,.2,1));
        assertEquals(.051,RagdollTerrainSupport.lift(skin,List.of(new AABB(.75,0,0,2,1,1))),1e-8);
    }
    @Test void emptyCornersOfRotatedSkinDoNotLiftTheBody(){
        var skin=List.of(new Vec3(-1,.4,0),new Vec3(0,.4,-1),new Vec3(1,.4,0),new Vec3(0,.4,1));
        assertEquals(0,RagdollTerrainSupport.lift(skin,List.of(new AABB(.8,0,.8,1,.5,1))));
    }
    @Test void highestTouchedStepWinsWithoutUsingTheLowestPointElsewhere(){
        var skin=box(new Vec3(-.4,.3,-.4),new Vec3(.4,1.2,.4));
        assertEquals(.701,RagdollTerrainSupport.lift(skin,List.of(new AABB(.2,0,-1,1,.5,1),new AABB(-1,0,-1,-.2,1,1))),1e-8);
    }
    @Test void highWallAndOverheadCeilingAreNotSupport(){
        var skin=box(new Vec3(-.4,.3,-.4),new Vec3(.4,.7,.4));
        assertEquals(0,RagdollTerrainSupport.lift(skin,List.of(new AABB(.2,0,-1,1,3,1),new AABB(-1,1,-1,1,2,1))));
    }
    @Test void separateSkinIslandsDoNotFillTheirGap(){
        var skin=new ArrayList<>(box(new Vec3(-2,.3,-.2),new Vec3(-1,.7,.2)));
        skin.addAll(box(new Vec3(1,.3,-.2),new Vec3(2,.7,.2)));
        assertEquals(0,RagdollTerrainSupport.lift(skin,List.of(new AABB(-.5,0,-.5,.5,.5,.5))));
    }
    @Test void sidewaysWallContactMovesSidewaysRatherThanOntoTheWall(){
        var skin=box(new Vec3(-1.14,1.2,-.4),new Vec3(.2,2.2,.4));
        var shift=RagdollTerrainSupport.correction(skin,List.of(new AABB(-2,0,-2,-1,4,2)));
        assertEquals(.141,shift.x,1e-8);assertEquals(0,shift.y);assertEquals(0,shift.z);
    }
    @Test void realSkinIsUsedInsteadOfEmptyRotatedBounds(){
        var skin=List.of(new Vec3(-1,.4,0),new Vec3(0,.4,-1),new Vec3(1,.4,0),new Vec3(0,.4,1));
        assertEquals(Vec3.ZERO,RagdollTerrainSupport.correction(skin,List.of(new AABB(.8,0,.8,1,.5,1))));
    }
    @Test void floorContactUsesSmallUpwardEscapeAndPreservesRestingSkin(){
        var skin=box(new Vec3(-.4,-.06,-.4),new Vec3(.4,.7,.4));var floor=List.of(new AABB(-2,-1,-2,2,0,2));
        var shift=RagdollTerrainSupport.correction(skin,floor);assertEquals(.061,shift.y,1e-8);
        assertEquals(Vec3.ZERO,RagdollTerrainSupport.correction(skin.stream().map(p->p.add(shift)).toList(),floor));
    }
}
