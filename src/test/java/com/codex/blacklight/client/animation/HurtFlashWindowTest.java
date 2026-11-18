package com.codex.blacklight.client.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HurtFlashWindowTest {
    @Test void normalHitLastsTenTicksIndependentOfRenderPassCount(){
        var flash=new HurtFlashWindow(100,10);
        for(long tick=100;tick<110;tick++)for(int pass=0;pass<8;pass++)assertTrue(flash.active(tick));
        assertFalse(flash.active(110));
    }
    @Test void backwardsCorrectionPreservesOnlyTheUnspentDuration(){
        var flash=new HurtFlashWindow(100,10);
        assertTrue(flash.active(104));
        assertTrue(flash.active(20));
        assertTrue(flash.active(25));
        assertFalse(flash.active(26));
    }
    @Test void expiredFlashCannotBeRevivedByAnotherClockCorrection(){
        var flash=new HurtFlashWindow(100,10);
        assertFalse(flash.active(110));
        assertFalse(flash.active(0));
        assertFalse(flash.active(5));
    }
    @Test void correctionBeforeFirstDrawCannotStrandTheOldDeadline(){
        var flash=new HurtFlashWindow(500,10);
        assertTrue(flash.active(40));
        assertTrue(flash.active(49));
        assertFalse(flash.active(50));
        assertFalse(new HurtFlashWindow(20,10).active(400));
    }
}
