package com.codex.blacklight.power;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Golden examples transcribed from the supplied Word document, not from the implementation. */
class AdaptivePassiveDocumentRegressionTest {
    @Test void adaptationOneFollowsAllThreeDocumentHits() {
        double health=1000;int layers=100;
        int[] hp={730,481,265},stacks={142,178,205};
        for(int i=0;i<3;i++) {
            double actual=300*AdaptivePassiveRules.remainingFraction(layers,health,1000,false);
            int after=AdaptivePassiveRules.adaptationAfterHit(layers,actual,300-actual);
            assertEquals(hp[i],health-actual,1e-8,"HP after document hit "+(i+1));
            assertEquals(stacks[i],after,"stacks after document hit "+(i+1));
            health-=actual;layers=after;
        }
    }
    @Test void adaptationTwoUsesCeilingHealthPercentageAndCorrectsTheDocumentArithmetic() {
        double health=1000;int layers=100;
        int[] hp={850,721,604},stacks={110,114,115};
        for(int i=0;i<3;i++) {
            double actual=300*AdaptivePassiveRules.remainingFraction(layers,health,1000,true);
            int after=AdaptivePassiveRules.adaptationAfterHit(layers,actual,300-actual);
            assertEquals(hp[i],health-actual,1e-8,"HP after corrected document hit "+(i+1));
            assertEquals(stacks[i],after,"stacks after corrected document hit "+(i+1));
            health-=actual;layers=after;
        }
        // 721 / 1000 rounds up to 73; 114 + 73 is 187, not the printed 186.
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
        assertEquals(1,AdaptivePassiveRules.regenerationAfterPulse(2));
        assertEquals(0,AdaptivePassiveRules.regenerationAfterPulse(1));
    }
}
