package com.codex.blacklight.combat;
import org.junit.jupiter.api.Test;
import com.codex.blacklight.power.*;
import com.codex.blacklight.state.BlacklightState;
import com.mojang.serialization.JsonOps;
import static org.junit.jupiter.api.Assertions.*;
class BlockThrowTest {
 @Test void unlockRequiresMusclemassAndSequentialPurchases(){
  var s=new BlacklightState();s.infect();s.addEvolutionPoints(2000);
  assertFalse(s.buyCombatSkill(CombatSkill.BLOCK_THROW_1));assertEquals(2000,s.getEvolutionPoints());
  s.unlock(PowerForm.MUSCLE_MASS);assertFalse(s.buyCombatSkill(CombatSkill.BLOCK_THROW_3));
  assertTrue(s.buyCombatSkill(CombatSkill.BLOCK_THROW_1));assertEquals(1,BlockThrowTuning.level(s.getCombatSkillMask()));
  assertFalse(s.buyCombatSkill(CombatSkill.BLOCK_THROW_1));assertTrue(s.buyCombatSkill(CombatSkill.BLOCK_THROW_2));assertTrue(s.buyCombatSkill(CombatSkill.BLOCK_THROW_3));
  assertEquals(1150,s.getEvolutionPoints());assertEquals(3,BlockThrowTuning.level(s.getCombatSkillMask()));
  var saved=BlacklightState.CODEC.encodeStart(JsonOps.INSTANCE,s).result().orElseThrow();var restored=BlacklightState.CODEC.parse(JsonOps.INSTANCE,saved).result().orElseThrow();
  assertEquals(3,BlockThrowTuning.level(restored.getCombatSkillMask()));
 }
 @Test void repeatedHeartbeatsNeverRestartCharge(){
  var c=new BlockThrowCharge();c.start(100);c.start(110);c.start(120);c.start(130);
  assertEquals(30,c.release(130));assertEquals(-1,c.release(131));
 }
 @Test void lostInputAndCancellationCannotReleaseStoredPower(){
  var c=new BlockThrowCharge();assertEquals(-1,c.release(100));c.start(100);c.tick(126);assertEquals(-1,c.release(127));
  c.start(150);c.cancel();assertEquals(-1,c.release(151));c.start(200);assertEquals(-1,c.release(199));
 }
 @Test void tapIsValidAndFullChargeIsBounded(){
  var c=new BlockThrowCharge();c.start(10);assertEquals(0,c.release(10));
  c.start(20);for(int t=30;t<=100;t+=10)c.start(t);assertEquals(30,c.release(101));
 }
 @Test void higherTierAndLongerChargeBothIncreaseDamageAndRange(){
  for(int tier=1;tier<=3;tier++){
   for(int ticks=1;ticks<=30;ticks++){
    assertTrue(BlockThrowTuning.speed(tier,ticks)>BlockThrowTuning.speed(tier,ticks-1));
    assertTrue(BlockThrowTuning.damage(tier,ticks)>BlockThrowTuning.damage(tier,ticks-1));
    if(tier>1){assertTrue(BlockThrowTuning.speed(tier,ticks)>BlockThrowTuning.speed(tier-1,ticks));assertTrue(BlockThrowTuning.damage(tier,ticks)>BlockThrowTuning.damage(tier-1,ticks));}
   }
   assertTrue(flatRange(tier,30)>flatRange(tier,0));
  }
  assertEquals(22,BlockThrowTuning.damage(1,30));assertEquals(50,BlockThrowTuning.damage(3,999));
  assertEquals(BlockThrowTuning.speed(1,0),BlockThrowTuning.speed(1,-30));assertEquals(0,BlockThrowTuning.damage(0,30));
 }
 private double flatRange(int tier,int charge){double x=0,y=1.6,v=BlockThrowTuning.speed(tier,charge),vy=0;for(int t=0;t<100&&y>0;t++){x+=v;y+=vy;v*=.99;vy=vy*.99-.045;}return x;}
}
