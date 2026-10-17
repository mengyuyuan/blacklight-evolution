package com.codex.blacklight.combat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BlockThrowReleaseLeaseTest {
 @Test void renewedReleaseRetainsChargeWhileAliveAndRecoversAsTapAfterTimeout(){
  var charge=new BlockThrowCharge();charge.start(10);charge.start(20);
  charge.start(25);assertEquals(15,charge.release(25));assertEquals(-1,charge.release(25));
  charge.start(40);charge.tick(80);assertFalse(charge.active());
  charge.start(80);assertEquals(0,charge.release(80));assertFalse(charge.active());
 }
}
