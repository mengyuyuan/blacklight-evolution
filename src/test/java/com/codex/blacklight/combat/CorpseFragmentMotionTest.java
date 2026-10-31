package com.codex.blacklight.combat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CorpseFragmentMotionTest {
    @Test void aHorizontalTorsoGetsAWaistCutInsteadOfLosingOnlyItsHead(){
        var normal=CorpseFragmentMotion.torsoNormal(new org.joml.Quaternionf().rotationX(-(float)Math.PI/2));
        assertEquals(0,normal.x,1e-5);assertEquals(0,normal.y,1e-5);assertEquals(1,normal.z,1e-5);
    }
    @Test void aSeveredPieceFallsAndStopsAtTheGroundInsteadOfHangingOrClippingThroughIt(){
        assertEquals(0,CorpseFragmentMotion.drop(0,1));
        assertTrue(CorpseFragmentMotion.drop(.3F,1)<-.3F);
        assertEquals(-1,CorpseFragmentMotion.drop(1,1));
        assertEquals(-1,CorpseFragmentMotion.drop(10,1));
        assertEquals(.1F,CorpseFragmentMotion.drop(.3F,-.1F));
    }
    @Test void theDetachedShapeRemainsReadableBeforeItContracts(){
        assertEquals(0,CorpseFragmentMotion.shrink(.1F));
        assertTrue(CorpseFragmentMotion.shrink(.5F)>0);
        assertEquals(1,CorpseFragmentMotion.shrink(1));
    }
}
