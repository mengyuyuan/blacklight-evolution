package com.codex.blacklight.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocomotionClockTest {
    private static LocomotionClock exercise(int framesPerSecond) {
        var clock=new LocomotionClock();clock.commit(0,"sprint",.53333336F,true,false,0,1.0/3);
        double frame=0;
        for(int tick=1;tick<=100;tick++) {
            // Identical actor displacement includes acceleration, sprint and reverse travel.
            double signedDistance=tick<=20?tick*.02:tick<=70?.7:-.35;
            clock.commit(tick,"sprint",.53333336F,true,false,Math.abs(signedDistance),1.0/3);
            while(frame<tick) {
                clock.sampleTime((float)(frame-Math.floor(frame)));
                frame+=20.0/framesPerSecond;
            }
            // Extra renderer layers and camera callers also query the same interval.
            clock.sampleTime(.8F);clock.sampleTime(.2F);clock.sampleTime(.8F);
        }
        return clock;
    }
    @Test void actorDistanceHasSamePhaseAt30And120Fps() {
        var slow=exercise(30);var fast=exercise(120);
        assertEquals(slow.current(),fast.current(),.000000001);
        assertEquals(slow.sampleTime(.4F),fast.sampleTime(.4F));
        double traveled=0;
        for(int tick=1;tick<=100;tick++)traveled+=tick<=20?tick*.02:tick<=70?.7:.35;
        assertEquals(traveled/((1.0/3)*20),slow.current(),.000000001);
    }
    @Test void repeatedAndReversedPartialQueriesCannotAdvanceClock() {
        var clock=new LocomotionClock();clock.commit(0,"walk",1,true,false,0,.1);
        clock.commit(1,"walk",1,true,false,.1,.1);
        float first=clock.sampleTime(.8F);
        assertEquals(.01F,clock.sampleTime(.2F),.000001);
        assertEquals(first,clock.sampleTime(.8F));
        assertEquals(.05,clock.current(),.000001);
        assertFalse(clock.commit(1,"walk",1,true,false,1,.1));
        assertFalse(clock.commit(0,"walk",1,true,false,1,.1));
        assertEquals(.05,clock.current(),.000001);
    }
    @Test void gaitChangePreservesNormalizedFootPhase() {
        var clock=new LocomotionClock();clock.commit(0,"walk",1,true,false,0,.1);
        clock.commit(1,"walk",1,true,false,.5,.1);
        clock.commit(2,"run",.5F,true,true,.2,.2);
        assertEquals(.125F,clock.sampleTime(0),.000001);
        assertEquals(.175F,clock.sampleTime(1),.000001);
    }
    @Test void stoppedStrideDoesNotKeepMarchingAtMinimumCadence() {
        var clock=new LocomotionClock();clock.commit(0,"walk",1,true,false,0,.1);
        clock.commit(1,"walk",1,true,false,.1,.1);
        double stopped=clock.current();
        clock.commit(2,"walk",1,true,false,0,.1);
        assertEquals(stopped,clock.current());
        assertEquals(clock.sampleTime(0),clock.sampleTime(1));
    }
    @Test void longSessionModuloKeepsOneRenderFrameOfPrecision() {
        var clock=new LocomotionClock();clock.commit(0,"sprint",.53333336F,true,false,0,.1);
        clock.commit(20_000_000,"sprint",.53333336F,true,false,2_000_000,.1);
        clock.commit(20_000_001,"sprint",.53333336F,true,false,.1,.1);
        assertNotEquals(clock.sampleTime(.1F),clock.sampleTime(.43333334F));
    }
    @Test void upgradedSprintCannotCompressSourceCycleIntoAStrobe() {
        var clock=new LocomotionClock();
        clock.commit(0,"basic_sprint_fastest",.4F,true,false,0,.484);
        for(int tick=1;tick<=20;tick++)
            clock.commit(tick,"basic_sprint_fastest",.4F,true,false,3,.484);
        assertEquals(1,clock.current(),.000001);
        double stopped=clock.current();
        clock.commit(21,"basic_sprint_fastest",.4F,true,false,0,.484);
        assertEquals(stopped,clock.current());
    }
    @Test void slowerSprintStillTracksTravelBelowAuthoredCadence() {
        var clock=new LocomotionClock();
        clock.commit(0,"basic_sprint",.53333336F,true,false,0,.333);
        clock.commit(1,"basic_sprint",.53333336F,true,false,.1665,.333);
        assertEquals(.025,clock.current(),.000001);
    }
}
