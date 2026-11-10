package com.codex.blacklight.movement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WallTraversalTest {
    private static final WallTraversal.Surface WALL=new WallTraversal.Surface(true,true,false,0,-1,-1);
    private static final WallTraversal.Surface GAP=new WallTraversal.Surface(false,false,false,0,0,-1);
    private static WallTraversal.Input input(int forward,int sideways,boolean sprint,boolean jump) {
        return new WallTraversal.Input(forward,sideways,sprint,jump,false);
    }
    private WallTraversal.Frame step(WallTraversal wall,WallTraversal.Input input) {
        return wall.tick(input,WALL,50,0,.36,1);
    }
    @Test void runStopClimbAndAccelerateWithoutLosingTheWall() {
        var wall=new WallTraversal();
        var run=step(wall,input(1,0,true,false));
        assertEquals(WallTraversal.Mode.RUN,run.mode());
        for(int tick=0;tick<200;tick++) {
            var hold=step(wall,input(0,0,false,false));
            assertEquals(WallTraversal.Mode.CLING,hold.mode());assertEquals(0,hold.y());assertTrue(hold.z()>0);
        }
        var climb=step(wall,input(1,0,false,false));
        assertEquals(WallTraversal.Mode.CLIMB,climb.mode());assertTrue(climb.y()>0 && climb.y()<run.y()/2);
        var resumed=step(wall,input(1,0,true,false));
        assertEquals(WallTraversal.Mode.RUN,resumed.mode());assertTrue(resumed.y()>climb.y()*2);
    }
    @Test void backAndJumpTurnsAwayAndSpringsOff() {
        var wall=new WallTraversal();step(wall,input(1,0,false,false));
        var kick=step(wall,input(-1,0,false,true));
        assertTrue(kick.handled());assertTrue(wall.kickedOff());assertEquals(WallTraversal.Mode.NONE,kick.mode());
        assertTrue(kick.z()<-.5,"springs away from the wall, not into it");assertTrue(kick.y()>.9);
        assertFalse(wall.attached());
    }
    @Test void kicksCanChainWithoutAnyLimit() {
        var wall=new WallTraversal();int kicks=0;
        for(int round=0;round<8;round++){
            for(int t=0;t<4;t++)step(wall,input(0,0,false,false));
            step(wall,input(1,0,false,false));
            step(wall,input(-1,0,false,true));if(wall.kickedOff())kicks++;
            step(wall,input(-1,0,false,false));
        }
        assertEquals(8,kicks);
    }
    @Test void wallJumpGoesUpwardAndReturnsToRunningWithoutKickingAway() {
        var wall=new WallTraversal();var run=step(wall,input(1,0,true,false));
        for(int t=0;t<20;t++)assertEquals(WallTraversal.Mode.RUN,step(wall,input(1,0,true,true)).mode());
        assertEquals(20,wall.chargeTicks());
        var jump=step(wall,input(1,0,true,false));
        assertEquals(WallTraversal.Mode.LEAP,jump.mode());assertTrue(jump.y()>run.y());assertTrue(jump.z()>0);
        for(int t=0;t<7;t++)assertEquals(WallTraversal.Mode.LEAP,step(wall,input(1,0,true,false)).mode());
        assertEquals(WallTraversal.Mode.RUN,step(wall,input(1,0,true,false)).mode());
        step(wall,input(1,0,true,true));
        assertEquals(WallTraversal.Mode.LEAP,step(wall,input(1,0,true,false)).mode());
    }
    @Test void climbCanMoveDownAndAcrossTheWallWithoutDiagonalSpeedBoost() {
        var wall=new WallTraversal();step(wall,input(1,0,false,false));
        assertTrue(step(wall,input(-1,0,false,false)).y()<0);
        var right=step(wall,input(0,1,false,false));assertTrue(right.x()<0);assertEquals(0,right.y());
        var diagonal=step(wall,input(1,1,false,false));
        assertEquals(Math.abs(right.x()),Math.hypot(diagonal.x(),diagonal.y()),1e-9);
        var east=new WallTraversal();
        var rotated=east.tick(input(1,1,false,false),new WallTraversal.Surface(true,true,false,-1,0,-1),50,0,.36,1);
        assertEquals(diagonal.y(),rotated.y());assertTrue(rotated.x()>0 && rotated.z()>0);
    }
    @Test void losingTheWallDropsButClearingItsTopCarriesThePlayerOntoTheRoof() {
        var wall=new WallTraversal();step(wall,input(1,0,true,false));
        var top=wall.tick(input(1,0,true,false),new WallTraversal.Surface(false,true,false,0,-1,-1,false,false,50.5),50,0,.36,1);
        assertEquals(WallTraversal.Mode.TOP,top.mode());assertTrue(top.z()>0 && top.y()>0);
        for(int t=0;t<7;t++)assertTrue(wall.tick(input(1,0,true,false),GAP,51,0,.36,1).handled());
        assertEquals(WallTraversal.Mode.TOP,wall.mode());
        var landed=wall.tick(input(1,0,true,false),new WallTraversal.Surface(false,false,false,0,-1,-1,true,true,50.5),50.5,0,.36,1);
        assertFalse(landed.handled());assertTrue(wall.finishedOnTop());
        assertEquals(WallTraversal.Mode.NONE,wall.mode());
        step(wall,input(1,0,true,false));assertFalse(wall.tick(input(0,0,false,false),GAP,51,0,.36,1).handled());
    }
    @Test void stoppingUnderAnOverhangDoesNotTriggerAnAutomaticVault() {
        var ceiling=new WallTraversal.Surface(true,true,true,0,-1,-1);
        var wall=new WallTraversal();step(wall,input(1,0,true,false));
        for(int i=0;i<50;i++)assertEquals(WallTraversal.Mode.CLING,wall.tick(input(0,0,false,false),ceiling,50,0,.36,1).mode());
        WallTraversal.Frame frame=null;
        for(int i=0;i<3;i++)frame=wall.tick(input(1,0,true,false),ceiling,50,0,.36,1);
        assertEquals(WallTraversal.Mode.VAULT,frame.mode());
    }
    @Test void detachIsExplicitAndCannotImmediatelyRelatch() {
        var wall=new WallTraversal();step(wall,input(1,0,true,false));
        var released=step(wall,new WallTraversal.Input(1,0,true,false,true));
        assertEquals(WallTraversal.Mode.NONE,released.mode());assertTrue(released.z()<0);
        for(int i=0;i<8;i++)assertFalse(step(wall,input(1,0,true,false)).handled());
        assertTrue(step(wall,input(1,0,true,false)).handled());
    }
    @Test void walkingAwayFromAWallCannotStartClimbing() {
        var wall=new WallTraversal();
        assertFalse(wall.tick(input(1,0,true,false),new WallTraversal.Surface(true,true,false,0,-1,.8),50,0,.36,1).handled());
        assertFalse(step(wall,input(-1,0,true,false)).handled());
    }
}
