package com.codex.blacklight.combat;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class WhipPitchTest {
 @Test void straightReleaseKeepsWristAndStopsAtItsPhysicalLimit(){
  var wrist=new Vec3(.3,6,0);
  var path=List.of(wrist,wrist.add(.4,-.3,7),wrist.add(0,-.6,14));
  var limb=new Vec3(3,1,20);
  double cap=WhipCurveContact.straightReach(1.25);
  assertSame(path,WhipCurveContact.seatStraightTip(path,limb,0,cap));
  var full=WhipCurveContact.seatStraightTip(path,limb,1,cap);
  assertEquals(wrist,full.get(0));
  assertTrue(full.get(2).distanceTo(limb)<1e-8);
  var far=WhipCurveContact.seatStraightTip(path,limb.add(0,0,50),1,cap);
  assertEquals(cap,far.get(2).distanceTo(wrist),1e-8);
  var halfway=WhipCurveContact.seatStraightTip(path,limb,.5,cap);
  assertEquals(wrist.distanceTo(limb)*.5,wrist.distanceTo(halfway.get(2)),1e-8);
 }
 @Test void flatAimKeepsAuthoredLash(){
  assertEquals(0F,WhipCurveContact.effectivePitch(8F));
  var pts=List.of(new Vec3(0,1.4,5));
  assertSame(pts,WhipCurveContact.pitched(pts,Vec3.ZERO,0,0,1.4));
 }
 @Test void downwardAimLowersTheTip(){
  // yaw 0 faces +Z; a tip 5 m ahead at shoulder height, aimed 90 down, ends 5 m below the shoulder
  var tip=WhipCurveContact.pitched(List.of(new Vec3(0,1.4,5)),Vec3.ZERO,0,90,1.4).get(0);
  assertEquals(1.4-5,tip.y,1e-6);assertEquals(0,tip.z,1e-6);
 }
 @Test void upwardAimRaisesTheTip(){
  var tip=WhipCurveContact.pitched(List.of(new Vec3(0,1.4,5)),Vec3.ZERO,0,-45,1.4).get(0);
  assertTrue(tip.y>4.8&&tip.z>3.4&&tip.z<3.6);
 }
 @Test void pitchIsClamped(){assertEquals(WhipCurveContact.PITCH_LIMIT,WhipCurveContact.effectivePitch(120F));}
}
