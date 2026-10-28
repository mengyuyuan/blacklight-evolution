package com.codex.blacklight.city;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DistrictThreatTest {
    @Test void LivingSourcesAndNeighbourPressureDriveBothGrowthAndRecovery(){
        assertTrue(new DistrictThreat.Signal(8,2,0,1,0,0).rate()>0);
        assertTrue(new DistrictThreat.Signal(0,0,0,0,1,0).rate()>0,"one red neighbour can start a slow frontier");
        assertEquals(0,new DistrictThreat.Signal(0,0,0,0,0,1).rate(),"one yellow cell does not infect the whole map alone");
        assertTrue(DistrictThreat.Signal.EMPTY.rate()<0);
        long grown=DistrictThreat.advance(0,24000,new DistrictThreat.Signal(18,4,1,2,0,0));
        assertTrue(grown>=DistrictThreat.RED*20L);
        assertTrue(DistrictThreat.advance(grown,200,DistrictThreat.Signal.EMPTY)<grown);
        assertEquals(0,DistrictThreat.advance(0,200,DistrictThreat.Signal.EMPTY));
    }
    @Test void ThresholdHysteresisDoesNotFlickerOrKeepACompletelyCleansedCellRed(){
        var zone=DistrictThreat.zone(DistrictThreat.Zone.GREEN,2000);assertEquals(DistrictThreat.Zone.YELLOW,zone);
        assertEquals(zone,DistrictThreat.zone(zone,1999));assertEquals(DistrictThreat.Zone.GREEN,DistrictThreat.zone(zone,1599));
        zone=DistrictThreat.zone(zone,6000);assertEquals(DistrictThreat.Zone.RED,zone);
        assertEquals(zone,DistrictThreat.zone(zone,5999));assertEquals(DistrictThreat.Zone.YELLOW,DistrictThreat.zone(zone,5399));
        assertEquals(DistrictThreat.Zone.GREEN,DistrictThreat.zone(zone,0));
    }
    @Test void GlobalOldAgeNeverMakesAGreenCellSpawnElitesOrSkipsTheGlobalGate(){
        for(int global=0;global<=4;global++)assertEquals(0,DistrictThreat.stage(DistrictThreat.Zone.GREEN,global,4));
        assertEquals(1,DistrictThreat.stage(DistrictThreat.Zone.YELLOW,4,4));
        assertEquals(0,DistrictThreat.stage(DistrictThreat.Zone.RED,0,4));
        assertEquals(2,DistrictThreat.stage(DistrictThreat.Zone.RED,4,0));
        assertEquals(4,DistrictThreat.stage(DistrictThreat.Zone.RED,4,4));
    }
    @Test void LocalPopulationAndFrequencyKeepGreenQuietWithoutDeletingBodies(){
        assertEquals(8,DistrictThreat.populationCap(DistrictThreat.Zone.GREEN,18));
        assertEquals(14,DistrictThreat.populationCap(DistrictThreat.Zone.YELLOW,18));
        assertEquals(18,DistrictThreat.populationCap(DistrictThreat.Zone.RED,18));
        for(var zone:DistrictThreat.Zone.values())assertEquals(3,DistrictThreat.populationCap(zone,3));
        assertTrue(DistrictThreat.ambientChance(DistrictThreat.Zone.GREEN)<DistrictThreat.ambientChance(DistrictThreat.Zone.YELLOW));
        assertEquals(100,DistrictThreat.ambientChance(DistrictThreat.Zone.RED));
    }
    @Test void OneDistrictSharedByTwoPlayersAccruesOnlyOneObservationAndSurvivesNBT(){
        var data=new CityEcologyData();var key=CityEcologyData.key("shenzhen",5,5);
        var signal=new DistrictThreat.Signal(12,2,0,1,0,0);
        data.observe(key,20,60,0,24000,signal);long exposure=data.get(key).exposure();int pressure=data.get(key).pressure();
        data.observe(key,20,60,0,24000,signal);
        assertEquals(exposure,data.get(key).exposure());assertEquals(pressure,data.get(key).pressure());
        var copy=CityEcologyData.load(data.save(new CompoundTag(),null),null);
        assertEquals(pressure,copy.get(key).pressure());assertEquals(data.get(key).zone(),copy.get(key).zone());
        copy.observe(key,Long.MAX_VALUE-1,60,0,24000,DistrictThreat.Signal.EMPTY);
        assertEquals(exposure+60,copy.get(key).exposure());assertTrue(copy.get(key).pressure()<=pressure,"offline elapsed ticks are not replayed");
    }
    @Test void VialsStartNewLocationsWithoutResettingExposureOrAddingCleansingRewards(){
        var data=new CityEcologyData();var a=CityEcologyData.key("shenzhen",0,0);var b=CityEcologyData.key("los_angeles",0,0);
        data.release(a);data.observe(a,100,60,0,24000,DistrictThreat.Signal.EMPTY);long age=data.get(a).exposure();
        data.release(a);assertEquals(age,data.get(a).exposure());assertEquals(DistrictThreat.Zone.YELLOW,data.get(a).zone());
        data.release(b);assertEquals(0,data.get(b).exposure());
        int pressure=data.get(a).pressure();data.clear(a,600);
        assertEquals(pressure,data.get(a).pressure(),"legacy clear does not implement a new zone reward");
        assertEquals(600,data.get(a).suppression());assertEquals(DistrictThreat.Zone.YELLOW,data.get(b).zone());
    }
    @Test void LegacyDistrictHistoryMigratesAndKeepsExistingSuppressionWithoutNewZoneClearing(){
        var data=new CityEcologyData();var key=CityEcologyData.key("city",0,0);
        data.advance(key,1,24000);var legacy=data.save(new CompoundTag(),null);
        var copy=CityEcologyData.load(legacy,null);assertFalse(copy.get(key).observed());
        copy.observe(key,2,60,2,24000,DistrictThreat.Signal.EMPTY);assertEquals(DistrictThreat.Zone.RED,copy.get(key).zone());
        int pressure=copy.get(key).pressure();copy.clear(key,600);copy=CityEcologyData.load(copy.save(new CompoundTag(),null),null);
        assertEquals(pressure,copy.get(key).pressure());assertEquals(DistrictThreat.Zone.RED,copy.get(key).zone());
        assertEquals(1,copy.get(key).cleared());
    }
    @Test void ExtremeSignalAndTimeValuesRemainFiniteAndBounded(){
        var huge=new DistrictThreat.Signal(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE);
        assertTrue(huge.rate()>0&&huge.rate()<100);
        assertEquals(DistrictThreat.MAXIMUM*20L,DistrictThreat.advance(Long.MAX_VALUE,Integer.MAX_VALUE,huge));
        assertEquals(0,DistrictThreat.advance(Long.MIN_VALUE,Integer.MAX_VALUE,DistrictThreat.Signal.EMPTY));
    }
    @Test void ANewDistrictCannotInventLegacyPressureFromItsFirstLongSchedulingInterval(){
        var data=new CityEcologyData();var key=CityEcologyData.key("new-city",0,0);
        data.observe(key,1,24000,0,200,DistrictThreat.Signal.EMPTY);
        assertEquals(24000,data.get(key).exposure());assertEquals(0,data.get(key).pressure());
        assertEquals(DistrictThreat.Zone.GREEN,data.get(key).zone());
    }
}
