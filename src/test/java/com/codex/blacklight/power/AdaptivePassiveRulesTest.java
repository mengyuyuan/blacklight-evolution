package com.codex.blacklight.power;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdaptivePassiveRulesTest {
    @Test void sponsorBaseExample() {
        double actual = 60 * AdaptivePassiveRules.remainingFraction(90, 10, 1000, false);
        assertEquals(6, actual, 1e-10);
        assertEquals(86, AdaptivePassiveRules.adaptationAfterHit(90, actual, 60-actual));
        assertEquals(96, Math.round(100*(1-AdaptivePassiveRules.remainingFraction(86,4,1000,false))));
    }
    @Test void adaptationAddsOnePerEffectiveHitNotDamageDividedBySix() {
        assertEquals(1,AdaptivePassiveRules.adaptationAfterHit(0,60,0));
        assertEquals(1,AdaptivePassiveRules.adaptationAfterHit(0,.1,0));
        assertEquals(0,AdaptivePassiveRules.adaptationAfterHit(0,0,0));
    }
    @Test void sponsorUpgradedExampleUsesPercentNumberNotFractionOrMissingHealth() {
        assertEquals(.01,AdaptivePassiveRules.remainingFraction(99,10,1000,true),1e-12);
        assertEquals(.1,AdaptivePassiveRules.remainingFraction(90,10,100,true),1e-12);
        assertTrue(AdaptivePassiveRules.remainingFraction(99,.1,1000,true)>0);
        assertEquals(94,AdaptivePassiveRules.adaptationAfterHit(99,.6,59.4));
    }
    @Test void tissueUsesPreAdaptationDamageAndDecaysAfterHealing() {
        int count=AdaptivePassiveRules.regenerationAfterHit(10,60);
        assertEquals(20,count); count=AdaptivePassiveRules.regenerationAfterPulse(count);
        assertEquals(16,count); assertEquals(13,AdaptivePassiveRules.regenerationAfterPulse(count));
        assertEquals(1,AdaptivePassiveRules.regenerationAfterPulse(1));
        assertEquals(2,AdaptivePassiveRules.regenerationAfterPulse(2));
        assertEquals(1,AdaptivePassiveRules.regenerationAfterHit(0,3));
    }
    @Test void roundingAndOverflowCannotProduceNegativeStacks() {
        assertEquals(0,AdaptivePassiveRules.adaptationAfterHit(2,1,1000));
        assertEquals(Integer.MAX_VALUE,AdaptivePassiveRules.regenerationAfterHit(Integer.MAX_VALUE,1000));
        assertEquals(Integer.MAX_VALUE,AdaptivePassiveRules.adaptationAfterHit(Integer.MAX_VALUE,1,0));
        assertEquals(6,AdaptivePassiveRules.rounded(5.5));
        assertEquals(0,AdaptivePassiveRules.rounded(Double.NaN));
    }
}
