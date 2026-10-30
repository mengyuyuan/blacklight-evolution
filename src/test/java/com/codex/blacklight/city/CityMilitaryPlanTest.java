package com.codex.blacklight.city;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CityMilitaryPlanTest {
    private static final CityMilitaryPlan.Survey FLAT=(x,z)->new CityMilitaryPlan.Surface(64,false,false,false);
    @Test void deterministicForSameSeedAndChunk(){
        assertEquals(CityMilitaryPlan.find(42,0,0,false,false,FLAT),CityMilitaryPlan.find(42,0,0,false,false,FLAT));
    }
    @Test void footprintIncludesTheLargerBlackwatchCompound(){
        var s=CityMilitaryPlan.find(42,0,0,true,false,FLAT).orElseThrow();
        assertEquals(65*81,s.width()*s.depth());assertEquals(64,s.floor());
    }
    @Test void waterIsNeverReclaimedForABase(){
        assertTrue(CityMilitaryPlan.find(42,0,0,false,false,(x,z)->new CityMilitaryPlan.Surface(63,true,false,false)).isEmpty());
    }
    @Test void existingBuildingsAreNeverCleared(){
        assertTrue(CityMilitaryPlan.find(42,0,0,false,false,(x,z)->new CityMilitaryPlan.Surface(64,false,true,false)).isEmpty());
    }
    @Test void steepTerrainIsRejected(){
        assertTrue(CityMilitaryPlan.find(42,0,0,false,false,(x,z)->new CityMilitaryPlan.Surface(64+Math.floorMod(x,17),false,false,false)).isEmpty());
    }
    @Test void citySitesRequireAnAdjacentRoad(){
        assertTrue(CityMilitaryPlan.find(42,0,0,false,true,FLAT).isEmpty());
    }
    @Test void smallUnsampledBuildingIsStillProtected(){
        var s=CityMilitaryPlan.find(42,0,0,false,false,FLAT).orElseThrow();
        assertFalse(CityMilitaryPlan.validFootprint(s,(x,z)->new CityMilitaryPlan.Surface(64,false,x==s.x()+3&&z==s.z()+5,false)));
    }
    @Test void quarterTurnCoordinatesStayInsideTheSite(){
        for(int rotation=0;rotation<4;rotation++){
            var s=new CityMilitaryPlan.Site(10,20,64,rotation,true);
            for(int x:new int[]{0,64})for(int z:new int[]{0,80}){
                var p=s.world(x,z);assertTrue(p[0]>=s.x()&&p[0]<s.x()+s.width());assertTrue(p[1]>=s.z()&&p[1]<s.z()+s.depth());
            }
        }
    }
    @Test void searchesTheWholeSafeNativeReferenceRangeWithoutOverlappingNeighbourRegions(){
        var site=CityMilitaryPlan.find(42,0,0,false,false,(x,z)->new CityMilitaryPlan.Surface(64,false,x< -96||x> -32||z< -32||z>32,false)).orElseThrow();
        assertEquals(-96,site.x());assertTrue(site.x()+site.width()-1<=111&&site.z()+site.depth()-1<=111);
    }
    @Test void cityCheckpointUsesASafeRoadAndLeavesMainBaseAlone(){
        var base=new CityMilitaryPlan.Site(-32,-32,64,0,false);
        var point=CityMilitaryPlan.checkpoint(base,0,0,(x,z)->new CityMilitaryPlan.Surface(64,false,false,z>=46&&z<=50)).orElseThrow();
        assertTrue(point.z()>base.z()+base.depth()-1);assertEquals(1,point.rotation());
        assertTrue(point.x()>=-96&&point.x()+16<=111&&point.z()+16<=111);
    }
    @Test void checkpointCannotClearBuildingOrWater(){
        var base=new CityMilitaryPlan.Site(-32,-32,64,0,false);
        assertTrue(CityMilitaryPlan.checkpoint(base,0,0,(x,z)->new CityMilitaryPlan.Surface(64,false,z>32,z>=46&&z<=50)).isEmpty());
        assertTrue(CityMilitaryPlan.checkpoint(base,0,0,(x,z)->new CityMilitaryPlan.Surface(64,true,false,true)).isEmpty());
    }
    public static void main(String[] args){
        var t=new CityMilitaryPlanTest();t.deterministicForSameSeedAndChunk();t.footprintIncludesTheLargerBlackwatchCompound();
        t.waterIsNeverReclaimedForABase();t.existingBuildingsAreNeverCleared();t.steepTerrainIsRejected();
        t.citySitesRequireAnAdjacentRoad();t.smallUnsampledBuildingIsStillProtected();t.quarterTurnCoordinatesStayInsideTheSite();
        t.searchesTheWholeSafeNativeReferenceRangeWithoutOverlappingNeighbourRegions();
        t.cityCheckpointUsesASafeRoadAndLeavesMainBaseAlone();t.checkpointCannotClearBuildingOrWater();
        System.out.println("CityMilitaryPlan: 11 behavioral checks passed");
    }
}
