package com.codex.blacklight.animation;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;
class GroundGaitPoseTest {
 @Test void authoredWalkStopAndIdleKeepTheSameWorldHeading() {
  for(String key:new String[]{"basic_walk","basic_run","basic_brake","basic_sprint_brake","basic_idle","basic_idle_claw","basic_idle_hammer"})
   for(int i=0;i<=20;i++) {
    var pose=MotionLibrary.pose(key,MotionLibrary.duration(key)*i/20F,false);
    float height=pose.get("Root").translation().y;
    float lean=pose.get("Root").rotation().transform(new Vector3f(0,0,1)).y;
    GroundGaitPose.apply(pose,key,false);
    var forward=pose.get("Root").rotation().transform(new Vector3f(0,0,1));
    assertEquals(0,Math.atan2(forward.x,forward.z),.0001,key+"/"+i);
    assertEquals(height,pose.get("Root").translation().y);assertEquals(lean,forward.y,.0001);
   }
 }
 @Test void armorWalkingSlowsShortStepsWithoutChangingSprintOrStoppingClock() {
  double nominal=GroundGaitPose.nominal("basic_walk",MotionLibrary.nominalSpeed("basic_walk"),1,true);
  var clock=new LocomotionClock();clock.commit(0,"basic_walk",1.2F,true,false,0,nominal,1.65);
  for(int t=1;t<=20;t++)clock.commit(t,"basic_walk",1.2F,true,true,.16,nominal,1.65);
  assertEquals(1.6,clock.current(),.0001);
  double previous=clock.current();clock.commit(21,"basic_walk",1.2F,true,true,0,nominal,1.65);assertEquals(previous,clock.current());
  assertEquals(.3,GroundGaitPose.nominal("basic_run",.3,1,true));
  assertEquals(.056,GroundGaitPose.nominal("basic_walk",.056,1,false));
 }
 @Test void combatAndAirborneAuthoredOrientationsAreUntouched() {
  for(String key:new String[]{"basic_turn","basic_jump_running","basic_fall","basic_wall_run_up"}){
   var pose=MotionLibrary.pose(key,.1F,false);var before=pose.get("Root").toMatrix();
   GroundGaitPose.apply(pose,key,true);assertEquals(before,pose.get("Root").toMatrix());
  }
 }
}
