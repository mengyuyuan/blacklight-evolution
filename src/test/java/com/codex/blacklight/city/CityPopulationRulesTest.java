package com.codex.blacklight.city;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CityPopulationRulesTest {
    @Test void fullCivilianPoolCannotBlockAnIndependentInfectedPool(){assertEquals(128,CityPopulationRules.slots(48,0,48,128,true));assertEquals(0,CityPopulationRules.slots(48,0,48,128,false));}
    @Test void fullInfectedPoolStillLeavesRoomForResidents(){assertEquals(48,CityPopulationRules.slots(128,128,48,128,false));assertEquals(0,CityPopulationRules.slots(128,128,48,128,true));}
    @Test void legacyExcessResidentsAreKeptButCombinedCeilingStillBoundsNewInfected(){assertEquals(96,CityPopulationRules.slots(80,0,48,128,true));assertEquals(0,CityPopulationRules.slots(176,96,48,128,true));assertEquals(0,CityPopulationRules.slots(200,0,48,128,true));}
    @Test void zeroRetainsTheOldDisableContractAndCorruptCountsDoNotOverflow(){assertEquals(0,CityPopulationRules.slots(0,0,0,128,true));assertEquals(0,CityPopulationRules.slots(0,0,48,0,true));assertEquals(Integer.MAX_VALUE,CityPopulationRules.slots(0,0,Integer.MAX_VALUE,Integer.MAX_VALUE,true));assertEquals(0,CityPopulationRules.slots(1,1,48,1,true));}
    @Test void regionalDensityKeepsGreenLowAndRedHigher(){assertEquals(1,com.codex.blacklight.config.GameplayBalanceConfig.birthsPerPlayer(DistrictThreat.Zone.GREEN));assertEquals(2,com.codex.blacklight.config.GameplayBalanceConfig.birthsPerPlayer(DistrictThreat.Zone.YELLOW));assertEquals(3,com.codex.blacklight.config.GameplayBalanceConfig.birthsPerPlayer(DistrictThreat.Zone.RED));assertEquals(20,DistrictThreat.populationCap(DistrictThreat.Zone.YELLOW,32));assertEquals(32,DistrictThreat.populationCap(DistrictThreat.Zone.RED,32));}
}
