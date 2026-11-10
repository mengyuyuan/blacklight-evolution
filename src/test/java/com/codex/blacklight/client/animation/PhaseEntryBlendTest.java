package com.codex.blacklight.client.animation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PhaseEntryBlendTest {
 @Test void blendStartsWhenFirstSeen(){
  // packet arrived 5 ticks after the server start: still blends from 0 instead of snapping to 1
  assertEquals(0F,PhaseEntryBlend.weight(105,105,0,4F),1e-6);
  assertEquals(.5F,PhaseEntryBlend.weight(105,107,0,4F),1e-6);
  assertEquals(1F,PhaseEntryBlend.weight(105,110,0,4F),1e-6);
 }
 @Test void zeroLengthIsImmediate(){assertEquals(1F,PhaseEntryBlend.weight(0,0,0,0),1e-6);}
}