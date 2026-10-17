package com.codex.blacklight.infection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InfectionRulesTest {
 @Test void deliberateVialOutbreakIsWideAndFallsOffWithoutChangingContactSpread() {
  assertEquals(16,InfectionRules.VIAL_RADIUS);
  float last=1;
  for(int i=0;i<=64;i++){
   float chance=InfectionRules.splashChance(i/4.0);
   assertTrue(chance>=.5F&&chance<=last);last=chance;
  }
  assertEquals(1,InfectionRules.splashChance(0));
  assertEquals(.5F,InfectionRules.splashChance(16));
  assertEquals(.08F,InfectionRules.CONTACT_CHANCE);
  assertEquals(200,InfectionRules.CONTACT_COOLDOWN_TICKS);
 }
}
