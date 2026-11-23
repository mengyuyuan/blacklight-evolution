package com.codex.blacklight.power;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Server skill balance across consecutive hits and regeneration pulses. */
class AdaptivePassiveDocumentRegressionTest {
    @Test void adaptationOneFollowsThreeHits() {
        double health=1000;int layers=100;
        int[] hp={715,442,187},stacks={146,189,227};
        for(int i=0;i<3;i++) {
            double actual=300*AdaptivePassiveRules.remainingFraction(layers,health,1000,false);
            int after=AdaptivePassiveRules.adaptationAfterHit(layers,actual,300-actual);
            assertEquals(hp[i],health-actual,1e-8,"HP after tier I hit "+(i+1));
            assertEquals(stacks[i],after,"stacks after tier I hit "+(i+1));
            health-=actual;layers=after;
        }
    }
    @Test void adaptationTwoUsesCeilingHealthPercentage() {
        double health=1000;int layers=100;
        int[] hp={799,619,457},stacks={124,142,155};
        for(int i=0;i<3;i++) {
            double actual=300*AdaptivePassiveRules.remainingFraction(layers,health,1000,true);
            int after=AdaptivePassiveRules.adaptationAfterHit(layers,actual,300-actual);
            assertEquals(hp[i],health-actual,1e-8,"HP after tier II hit "+(i+1));
            assertEquals(stacks[i],after,"stacks after tier II hit "+(i+1));
            health-=actual;layers=after;
        }
    }
    @Test void adaptationConsumesAtLeastOneEvenForSmallPrevention() {
        assertEquals(90,AdaptivePassiveRules.adaptationAfterHit(90,.1,.9));
        assertEquals(85,AdaptivePassiveRules.adaptationAfterHit(90,6,54));
        assertEquals(142,AdaptivePassiveRules.adaptationAfterHit(100,270,30));
    }
    @Test void regenerationGainsCeilingActualDamageIncludingSmallEffectiveHits() {
        assertEquals(1,AdaptivePassiveRules.regenerationAfterHit(0,.1));
        assertEquals(11,AdaptivePassiveRules.regenerationAfterHit(10,6));
    }
    @Test void lastRegenerationStacksEventuallyReachZero() {
        assertEquals(1,AdaptivePassiveRules.regenerationAfterPulse(2, 1));
        assertEquals(0,AdaptivePassiveRules.regenerationAfterPulse(1, 0.5));
    }
}
