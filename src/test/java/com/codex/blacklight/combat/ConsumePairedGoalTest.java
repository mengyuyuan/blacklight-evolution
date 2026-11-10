package com.codex.blacklight.combat;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ConsumePairedGoalTest {
 @Test void receiverFollowsBlockedActor(){
  // actor stopped by a wall 0.6 short of its authored root
  var goal=ConsumePlacementTrack.pairedGoal(new Vec3(2,0,0),new Vec3(0.4,0,0),new Vec3(1,0,0));
  assertEquals(1.4,goal.x,1e-9);
 }
 @Test void alignedActorChangesNothing(){
  var goal=ConsumePlacementTrack.pairedGoal(new Vec3(2,1,3),new Vec3(1,0,0),new Vec3(1,0,0));
  assertEquals(new Vec3(2,1,3),goal);
 }
 @Test void brokenPairIsClamped(){
  var goal=ConsumePlacementTrack.pairedGoal(Vec3.ZERO,new Vec3(50,0,0),Vec3.ZERO);
  assertEquals(ConsumePlacementTrack.MAX_ACTOR_DRIFT,goal.x,1e-9);
 }
}