package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GreeneRoarTimingTest {
    @Test void dwellUsesCloseEntryAndStrictTriggerWithOneUnitHysteresis(){
        var clock=new GreeneRoarTiming();clock.observe(0,true,36);
        for(int t=1;t<120;t++)clock.observe(t,true,36);
        assertFalse(clock.ready(34,false,false));
        clock.observe(120,true,36);
        assertFalse(clock.ready(35,false,false));
        assertTrue(clock.ready(Math.nextDown(35D),false,false));
        assertEquals(120,clock.snapshot().closeTicks());
    }
    @Test void leavingCloseDistanceOrEngagementRequiresANewDwell(){
        var clock=new GreeneRoarTiming();
        for(int t=0;t<=130;t++)clock.observe(t,true,20);
        clock.observe(131,true,Math.nextUp(36D));
        clock.observe(132,true,20);assertFalse(clock.ready(20,false,false));
        for(int t=133;t<=252;t++)clock.observe(t,true,20);
        assertTrue(clock.ready(20,false,false));
        clock.observe(253,false,20);assertFalse(clock.ready(20,false,false));
    }
    @Test void fifteenSecondRechargeStartsAtEntryRatherThanAfterTheClip(){
        var clock=new GreeneRoarTiming();
        for(int t=0;t<=120;t++)clock.observe(t,true,20);
        clock.started();assertEquals(300,clock.snapshot().rechargeTicks());
        for(int t=121;t<=419;t++)clock.observe(t,true,20);
        assertFalse(clock.ready(20,false,false));
        clock.observe(420,true,20);assertTrue(clock.ready(20,false,false));
    }
    @Test void duplicateCallsAndUnloadedTimeDoNotAdvanceAnObservedClock(){
        var clock=new GreeneRoarTiming();clock.observe(5,true,10);clock.started();
        clock.observe(5,true,10);assertEquals(300,clock.snapshot().rechargeTicks());
        var saved=clock.snapshot();clock.restore(saved);clock.observe(5,true,10);
        assertEquals(saved,clock.snapshot(),"same-object reload cannot count its current tick again");
        var restored=new GreeneRoarTiming();restored.restore(clock.snapshot());
        restored.observe(100000,true,10);
        assertEquals(299,restored.snapshot().rechargeTicks());
        assertEquals(1,restored.snapshot().closeTicks());
        restored.observe(100000,true,10);assertEquals(299,restored.snapshot().rechargeTicks());
    }
    @Test void mechanicalPriorityDevastatorAndScriptForbidDoNotSpendReadiness(){
        var clock=new GreeneRoarTiming();
        for(int t=0;t<=120;t++)clock.observe(t,true,10);
        assertFalse(clock.ready(10,true,false));assertFalse(clock.ready(10,false,true));
        clock.forbidden(true);assertFalse(clock.ready(10,false,false));
        clock.forbidden(false);assertTrue(clock.ready(10,false,false));
        for(double distance:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,35,36})assertFalse(clock.ready(distance,false,false));
        clock.restore(new GreeneRoarTiming.Snapshot(Integer.MAX_VALUE,-10,false,true));
        assertEquals(new GreeneRoarTiming.Snapshot(0,0,false,true),clock.snapshot());
    }
}
