package com.codex.blacklight.combat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlayerPoiseTest {
 @Test void firstHitStaggersBriefly(){
  assertEquals(PlayerPoise.LIGHT_TICKS,PlayerPoise.duration(0,0,100,HitReaction.STAGGER,false));
  assertEquals(PlayerPoise.HEAVY_TICKS,PlayerPoise.duration(0,0,100,HitReaction.LAUNCH,false));
 }
 @Test void attackingPlayerKeepsSwinging(){
  assertEquals(0,PlayerPoise.duration(0,0,100,HitReaction.HEAVY,true));
 }
 @Test void repeatedHitsCannotChainLock(){
  // light reaction at 100 for 6 ticks: further light hits are ignored until 120
  assertEquals(0,PlayerPoise.duration(100,6,104,HitReaction.STAGGER,false));
  assertEquals(0,PlayerPoise.duration(100,6,119,HitReaction.STAGGER,false));
  assertEquals(PlayerPoise.LIGHT_TICKS,PlayerPoise.duration(100,6,120,HitReaction.STAGGER,false));
  // a heavy hit may land after recovery but never during the running reaction
  assertEquals(0,PlayerPoise.duration(100,6,103,HitReaction.HEAVY,false));
  assertEquals(PlayerPoise.HEAVY_TICKS,PlayerPoise.duration(100,6,108,HitReaction.HEAVY,false));
 }
 @Test void knockdownBecomesHeavyFlinch(){
  assertEquals(HitReaction.HEAVY,PlayerPoise.kind(HitReaction.DOWN));
  assertEquals(HitReaction.LAUNCH,PlayerPoise.kind(HitReaction.LAUNCH));
 }
}