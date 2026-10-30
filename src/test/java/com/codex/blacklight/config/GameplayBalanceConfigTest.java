package com.codex.blacklight.config;

import com.codex.blacklight.infection.*;
import com.codex.blacklight.power.PlayerEvolutionConfig;
import com.codex.blacklight.state.EvolutionMilestones;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameplayBalanceConfigTest {
    @Test void thresholdsAreFiftyAndOneHundredFiftyAndOldFlagsCannotUnlockThem(){
        assertEquals(50,PlayerEvolutionConfig.PASSIVE_LEVEL.getDefault());
        assertEquals(150,PlayerEvolutionConfig.PERCENT_LEVEL.getDefault());
        var old=new EvolutionMilestones(7);
        assertTrue(old.has(EvolutionMilestones.INFINITE));
        assertFalse(old.has(EvolutionMilestones.PASSIVES));assertFalse(old.has(EvolutionMilestones.PERCENT));
        assertTrue(old.has(EvolutionMilestones.LEGACY_PERCENT));
    }
    @Test void weightedBirthsSupportTinyWeightsZerosAndNonHundredTotals(){
        assertEquals(OutbreakRules.Tier.GROWING,OutbreakRules.weighted(new int[]{0,1,0,0,0},0));
        assertEquals(OutbreakRules.Tier.GROWING,OutbreakRules.weighted(new int[]{0,1,0,0,0},.99999));
        assertEquals(OutbreakRules.Tier.FRESH,OutbreakRules.weighted(new int[]{10000,1,0,0,0},.5));
        assertEquals(OutbreakRules.Tier.GROWING,OutbreakRules.weighted(new int[]{10000,1,0,0,0},.999999));
        assertEquals(OutbreakRules.Tier.FRESH,OutbreakRules.weighted(new int[]{0,0,0,0,0},.9));
    }
    @Test void defaultsPreserveEcologyAndZeroReallyStopsTickScaling(){
        assertEquals(.08,GameplayBalanceConfig.value(GameplayBalanceConfig.CONTACT_CHANCE));
        assertEquals(16,InfectionRules.radius());assertEquals(.5,InfectionRules.splashChance(16));
        assertEquals(1,TissueMetabolism.worldSpeed(0));assertEquals(2,TissueMetabolism.worldSpeed(7*24000));
        assertEquals(0,TissueMetabolism.scaledTicks(20,0));
        assertEquals(12,com.codex.blacklight.city.DistrictThreat.ambientChance(com.codex.blacklight.city.DistrictThreat.Zone.GREEN));
        assertEquals(1,GameplayBalanceConfig.exposureChance(4));
        assertArrayEquals(new int[]{60,14,16,8,2},GameplayBalanceConfig.weights(4));
    }
    @Test void legacyConfigGetsNewFiftyAndOneHundredFiftyDefaults(){
        var config=com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        config.set("passiveAttributeRank",20);config.set("percentAdaptationAttributeRank",50);
        PlayerEvolutionConfig.SPEC.correct(config);
        assertEquals(50,(int)config.get("passiveTierOneAttributeRank"));assertEquals(150,(int)config.get("passiveTierTwoAttributeRank"));
        assertTrue(PlayerEvolutionConfig.SPEC.isCorrect(config));
    }
}
