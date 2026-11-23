package com.codex.blacklight.power;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdaptivePassiveRulesTest {
    @Test void firstTierMitigatesPartOfALowHealthHit() {
        double actual = 60 * AdaptivePassiveRules.remainingFraction(90, 10, 1000, false);
        assertEquals(33, actual, 1e-10);
        assertEquals(93, AdaptivePassiveRules.adaptationAfterHit(90, actual, 60-actual));
        assertEquals(48, AdaptivePassiveRules.mitigation(85,4,1000,false));
    }
    @Test void adaptationGainsFromActualDamageAndAlsoPaysItsMinimumCost() {
        assertEquals(9,AdaptivePassiveRules.adaptationAfterHit(0,60,0));
        assertEquals(0,AdaptivePassiveRules.adaptationAfterHit(0,.1,0));
        assertEquals(0,AdaptivePassiveRules.adaptationAfterHit(0,0,0));
    }
    @Test void secondTierUsesPercentNumberNotFractionOrMissingHealth() {
        assertEquals(.35,AdaptivePassiveRules.remainingFraction(99,10,1000,true),1e-12);
        assertEquals(.41,AdaptivePassiveRules.remainingFraction(90,10,100,true),1e-12);
        assertEquals(.35,AdaptivePassiveRules.remainingFraction(99,.1,1000,true),1e-12);
        assertEquals(94,AdaptivePassiveRules.adaptationAfterHit(99,.6,59.4));
    }
    @Test void tissueUsesActualDamageAndCeilingConsumptionAfterHealing() {
        assertEquals(11,AdaptivePassiveRules.regenerationAfterHit(10,6));
        int count=AdaptivePassiveRules.regenerationAfterHit(100,300);
        assertEquals(150,count);count=AdaptivePassiveRules.regenerationAfterPulse(count,150);
        assertEquals(87,count);assertEquals(68,AdaptivePassiveRules.regenerationAfterPulse(count,43.5));
        assertEquals(0,AdaptivePassiveRules.regenerationAfterPulse(1,1));
        assertEquals(1,AdaptivePassiveRules.regenerationAfterPulse(2,2));
        assertEquals(1,AdaptivePassiveRules.regenerationAfterHit(0,3));
    }
    @Test void roundingAndOverflowCannotProduceNegativeStacks() {
        assertEquals(0,AdaptivePassiveRules.adaptationAfterHit(2,1,1000));
        assertEquals(Integer.MAX_VALUE,AdaptivePassiveRules.regenerationAfterHit(Integer.MAX_VALUE,1000));
        assertEquals(Integer.MAX_VALUE,AdaptivePassiveRules.adaptationAfterHit(Integer.MAX_VALUE,1,0));
        assertEquals(6,AdaptivePassiveRules.ceil(5.5));
        assertEquals(0,AdaptivePassiveRules.ceil(Double.NaN));
    }
    @Test void healingUsesActualRestorationAndKeepsTheMinimumCostAtFullHealth() {
        assertEquals(10,AdaptivePassiveRules.healing(100,990,1000,false));
        assertEquals(95,AdaptivePassiveRules.regenerationAfterPulse(100,10));
        assertEquals(99,AdaptivePassiveRules.regenerationAfterPulse(100,0));
    }
    @Test void secondTierRegenerationFollowsTheMissingHealthExamples() {
        assertEquals(150,AdaptivePassiveRules.healing(75,600,1000,true));
        assertEquals(12,AdaptivePassiveRules.regenerationAfterPulse(75,150));
        assertEquals(15,AdaptivePassiveRules.healing(12,750,1000,true));
        assertEquals(5,AdaptivePassiveRules.regenerationAfterPulse(12,15));
    }
    @Test void oldHighStackCountsCannotGrantImmunity() {
        assertEquals(50,AdaptivePassiveRules.mitigation(1000,10,1000,false));
        assertEquals(.5,AdaptivePassiveRules.remainingFraction(1000,10,1000,false));
        assertEquals(994,AdaptivePassiveRules.adaptationAfterHit(1000,0,60));
        assertEquals(0,AdaptivePassiveRules.regenerationAfterHit(0,0));
    }
    @Test void wholeHealthPercentIsNotRoundedUpTwiceByFloatingPointOrder() {
        // 7 / 100 * 100 can produce 7.000000000000001; the document denominator is 7.
        assertEquals(33,AdaptivePassiveRules.mitigation(7,7,100,true));
        assertEquals(31,AdaptivePassiveRules.mitigation(7,7.01,100,true));
    }
    @Test void singleHitCannotRestoreItselfAcrossSeveralPulses() {
        int layers = AdaptivePassiveRules.regenerationAfterHit(0, 600);
        assertEquals(100, layers);
        assertEquals(50, AdaptivePassiveRules.healing(layers, 3600, 4200, false));
        float healed = AdaptivePassiveRules.healing(layers, 3600, 4200, true);
        assertEquals(240, healed);
        assertEquals(0, AdaptivePassiveRules.regenerationAfterPulse(layers, healed));
        assertEquals(240, AdaptivePassiveRules.healing(Integer.MAX_VALUE, 3600, 4200, true));
        assertEquals(65, AdaptivePassiveRules.mitigation(Integer.MAX_VALUE, 1, 4200, true));
    }
}
