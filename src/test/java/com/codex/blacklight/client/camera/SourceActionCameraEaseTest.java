package com.codex.blacklight.client.camera;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SourceActionCameraEaseTest {
 @Test void easeStartsAndEndsAtRest(){
  assertEquals(0,SourceActionCamera.ease(0),1e-9);assertEquals(1,SourceActionCamera.ease(1),1e-9);
  assertEquals(.5,SourceActionCamera.ease(.5),1e-9);
  // zero slope at both ends: a tiny step barely moves the camera
  assertTrue(SourceActionCamera.ease(.02)<.002);assertTrue(1-SourceActionCamera.ease(.98)<.002);
  assertEquals(0,SourceActionCamera.ease(-1),1e-9);assertEquals(1,SourceActionCamera.ease(2),1e-9);
 }
 @Test void shortSourcePushesAreStretched(){
  assertEquals(SourceActionCamera.MIN_IN_SECONDS,SourceActionCamera.inSeconds(.0667),1e-9);
  assertEquals(.4,SourceActionCamera.inSeconds(.4),1e-9);
 }
}