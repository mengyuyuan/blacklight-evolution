package com.codex.blacklight.power;
import com.codex.blacklight.state.GrowthProgress;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ConsumeProgressionTest {
    @Test void upgradesIncreaseThresholdAndMobilityWithoutLosingExistingSaveBits(){
        var before=GrowthProgress.EMPTY.buy(GrowthUpgrade.SLIME_DECAY_III).buy(GrowthUpgrade.VITALITY_IV);
        var first=before.buy(GrowthUpgrade.ASSIMILATION);
        var second=first.buy(GrowthUpgrade.ASSIMILATION_II).buy(GrowthUpgrade.SLIME_MOBILITY_III).buy(GrowthUpgrade.BIOMASS_METABOLISM);
        assertEquals(.15F,ConsumeProgression.healthLimit(before));
        assertEquals(.20F,ConsumeProgression.healthLimit(first));
        assertEquals(.25F,ConsumeProgression.healthLimit(second));
        assertEquals(60,ConsumeProgression.absorbTicks(before));
        assertEquals(48,ConsumeProgression.absorbTicks(first));
        assertEquals(36,ConsumeProgression.absorbTicks(second));
        assertTrue(second.owns(GrowthUpgrade.SLIME_DECAY_III));
        assertTrue(second.owns(GrowthUpgrade.VITALITY_IV));
        assertTrue(second.owns(GrowthUpgrade.BIOMASS_METABOLISM));
        assertEquals(.20F,ConsumeProgression.crawlSpeed(second));
        assertEquals(GrowthUpgrade.ASSIMILATION,GrowthUpgrade.ASSIMILATION_II.requires());
        int bits=0;for(var upgrade:GrowthUpgrade.values()){assertEquals(0,bits&upgrade.bit);bits|=upgrade.bit;}
        assertEquals(bits,GrowthUpgrade.knownMask());
    }
}
