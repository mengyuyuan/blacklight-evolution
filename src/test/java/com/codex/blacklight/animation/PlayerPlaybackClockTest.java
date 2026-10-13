package com.codex.blacklight.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerPlaybackClockTest {
    private static PlayerMotionController.Clock clock(float seconds) {
        var clock=new PlayerMotionController.Clock();clock.at=100;clock.seconds=seconds;return clock;
    }

    @Test void renderingAnEarlierPartialAfterALaterOneDoesNotHoldThePoseAtTheLaterTime() {
        var clock=clock(.4F);
        assertEquals(.445F,clock.time(100.9),.000001F);
        assertEquals(.41F,clock.time(100.2),.000001F);
        assertEquals(.445F,clock.time(100.9),.000001F);
        assertEquals(.4F,clock.seconds);assertEquals(100,clock.at);
    }

    @Test void chargeTickSeekIsIndependentOfHowManyFramesWereRenderedBetweenTicks() {
        var sparse=clock(.5F);var dense=clock(.5F);
        sparse.time(100.95);
        for(int frame=0;frame<120;frame++)dense.time(100+frame/120D);
        assertEquals(sparse.time(101),dense.time(101),.000001F);
        // A gameplay command owns the new origin; rendering only samples it.
        sparse.seconds=dense.seconds=.5625F;sparse.at=dense.at=101;
        assertEquals(.5725F,dense.time(101.2),.000001F);
        assertEquals(sparse.time(101.8),dense.time(101.8),.000001F);
    }

    @Test void pauseAndReverseCommitOnceWithoutDependenceOnTheMostRecentRenderQuery() {
        var clock=clock(.4F);clock.time(100.9);
        clock.commit(101);clock.paused=true;
        assertEquals(.45F,clock.time(120),.000001F);
        clock.commit(120);clock.paused=false;clock.reverse=true;
        assertEquals(.4F,clock.time(121),.000001F);
        clock.time(121.8);
        assertEquals(.425F,clock.time(120.5),.000001F);
        assertEquals(0,clock.time(200));
    }

    @Test void anEndPassCannotRetireAClockBeforeAnEarlierPassSamplesTheSameTick() {
        var clock=clock(.49F);
        assertTrue(clock.finished(100.9,false,.5F));
        assertFalse(clock.finished(100.1,false,.5F));
        assertEquals(.495F,clock.time(100.1),.000001F);
        assertTrue(clock.finished(101,false,.5F));
        clock.seconds=.5F;clock.paused=true;
        assertFalse(clock.finished(120,false,.5F),"paused end frames remain held");
    }
}
