package com.codex.passives;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PassiveRulesTest {
    @Test void firstTierDocumentThreeHits() {
        double hp=1000; int stacks=100;
        int[] rates={10,17,28}, layers={142,178,205}; double[] health={730,481,265};
        for(int i=0;i<3;i++) { int rate=PassiveRules.mitigation(stacks,hp,1000,1); assertEquals(rates[i],rate);
            double actual=300*(100-rate)/100.0; stacks=PassiveRules.adaptationAfter(stacks,actual,300-actual); hp-=actual;
            assertEquals(layers[i],stacks); assertEquals(health[i],hp,1e-7); }
    }
    @Test void secondTierCorrectsDocumentThirdRow() {
        double hp=1000; int stacks=100;
        int[] rates={50,57,61}, layers={110,114,115}; double[] health={850,721,604};
        for(int i=0;i<3;i++) { int rate=PassiveRules.mitigation(stacks,hp,1000,2); assertEquals(rates[i],rate);
            double actual=300*(100-rate)/100.0; stacks=PassiveRules.adaptationAfter(stacks,actual,300-actual); hp-=actual;
            assertEquals(layers[i],stacks); assertEquals(health[i],hp,1e-7); }
    }
    @Test void regenUsesActualDamageNotBlockedDamage() { assertEquals(1,PassiveRules.gain(6)); assertEquals(10,PassiveRules.gain(60)); assertEquals(1,PassiveRules.gain(.1)); assertEquals(0,PassiveRules.gain(0)); }
    @Test void firstTierPulseAndTailDecay() { assertEquals(150,PassiveRules.healing(150,500,1000,1)); assertEquals(120,PassiveRules.afterHeal(150,150)); assertEquals(1,PassiveRules.afterHeal(2,2)); assertEquals(0,PassiveRules.afterHeal(1,1)); }
    @Test void secondTierMissingHealthPulse() { assertEquals(300,PassiveRules.healing(75,600,1000,2)); assertEquals(15,PassiveRules.afterHeal(75,300)); assertEquals(15,PassiveRules.healing(15,900,1000,2)); assertEquals(12,PassiveRules.afterHeal(15,15)); }
    @Test void noOverhealAndActualRecoveryCost() { assertEquals(5,PassiveRules.healing(150,995,1000,1)); assertEquals(149,PassiveRules.afterHeal(150,5)); assertEquals(149,PassiveRules.afterHeal(150,0)); assertEquals(20,PassiveRules.healing(200,980,1000,2)); }
    @Test void flatKillRewardCapsPerVictim() { assertEquals(6,PassiveRules.killGain(100)); assertEquals(30,PassiveRules.killGain(500)); assertEquals(30,PassiveRules.killGain(1000000)); assertEquals(1,PassiveRules.killGain(1)); }
    @Test void boundedLayersAndHealthPercentFloor() { assertEquals(Integer.MAX_VALUE,PassiveRules.add(Integer.MAX_VALUE,5)); assertEquals(99,PassiveRules.mitigation(99,.1,1000,2)); assertEquals(100,PassiveRules.mitigation(Integer.MAX_VALUE,.1,1000,2)); assertEquals(0,PassiveRules.ceil(Double.NaN)); }
}
