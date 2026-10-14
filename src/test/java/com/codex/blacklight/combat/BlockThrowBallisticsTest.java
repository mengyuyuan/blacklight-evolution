package com.codex.blacklight.combat;
import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.Vec3;
import static org.junit.jupiter.api.Assertions.*;
class BlockThrowBallisticsTest {
 @Test void predictionMatchesEveryDiscreteFlightStep(){
  var origin=new Vec3(3,72,-9);var velocity=new Vec3(1.2,.7,2.1);var p=origin;var v=velocity;
  for(int t=0;t<=100;t++){assertEquals(0,p.distanceTo(BlockThrowBallistics.at(origin,velocity,t)),1e-9);p=p.add(v);v=BlockThrowBallistics.nextVelocity(v,false);}
 }
 @Test void movingTargetInterceptUsesExactDragAndGravity(){
  var from=new Vec3(0,80,0);var target=new Vec3(4,79,18);
  for(var motion:new Vec3[]{Vec3.ZERO,new Vec3(.15,0,-.05)})for(double speed:new double[]{1.2,2,3.5}){
   var shot=BlockThrowBallistics.solve(from,target,motion,speed);assertNotNull(shot);
   assertEquals(speed,shot.velocity().length(),1e-6);
   assertEquals(0,BlockThrowBallistics.at(from,shot.velocity(),shot.ticks()).distanceTo(target.add(motion.scale(shot.ticks()))),1e-8);
  }
 }
 @Test void impossibleAndInvalidRequestsHaveNoSolution(){
  assertNull(BlockThrowBallistics.solve(Vec3.ZERO,new Vec3(0,9999,10),Vec3.ZERO,2));
  assertNull(BlockThrowBallistics.solve(Vec3.ZERO,new Vec3(Double.NaN,0,0),Vec3.ZERO,2));
  assertNull(BlockThrowBallistics.solve(Vec3.ZERO,new Vec3(1,0,0),Vec3.ZERO,Double.POSITIVE_INFINITY));
  assertNull(BlockThrowBallistics.solve(Vec3.ZERO,new Vec3(1,0,0),Vec3.ZERO,0));
 }
 @Test void waterLosesHorizontalSpeedButKeepsGravity(){var v=new Vec3(1,0,0);assertEquals(.8,BlockThrowBallistics.nextVelocity(v,true).x);assertEquals(-.045,BlockThrowBallistics.nextVelocity(v,true).y);}
}
