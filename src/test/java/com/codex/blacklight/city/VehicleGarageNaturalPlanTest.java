package com.codex.blacklight.city;

import com.codex.blacklight.military.vehicle.VehicleGarageData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleGarageNaturalPlanTest {
    private CityMilitaryPlan.Site base(int rotation) { return new CityMilitaryPlan.Site(0,0,63,rotation,false); }
    private CityMilitaryPlan.Survey road(int rotation) {
        return (x,z)->new CityMilitaryPlan.Surface(63,false,false,switch(rotation){case 1->x>=-9&&x<=-1;case 2->z>=-9&&z<=-1;case 3->x>=65&&x<=73;default->z>=65&&z<=73;});
    }
    @Test void newBaseAddsOneIndependentGarageOnlyAfterCompleteFootprintAndRoadSurvey() {
        assertEquals(new VehicleGarageData.Site(69,63,38,0),VehicleGaragePiece.Planner.find(base(0),null,2,2,true,road(0)).orElse(null));
    }
    @Test void aNarrowBuildingOnOneSideUsesTheOtherSideWithoutExpandingTheOriginalBase() {
        var road=road(0);CityMilitaryPlan.Survey survey=(x,z)->x==84&&z==50?new CityMilitaryPlan.Surface(63,false,true,false):road.at(x,z);
        assertEquals(new VehicleGarageData.Site(-35,63,38,0),VehicleGaragePiece.Planner.find(base(0),null,2,2,true,survey).orElse(null));
    }
    @Test void oldLoadedBaseNeverResurveysOrInventsAGaragePiece() {
        assertTrue(VehicleGaragePiece.Planner.find(base(0),null,2,2,false,(x,z)->{throw new AssertionError("old starts must not consult new map state");}).isEmpty());
    }
    @Test void waterExistingRoadAndUnevenExitRefuseGarageWithoutChangingBaseSelection() {
        assertTrue(VehicleGaragePiece.Planner.find(base(0),null,2,2,true,(x,z)->new CityMilitaryPlan.Surface(63,true,false,false)).isEmpty());
        assertTrue(VehicleGaragePiece.Planner.find(base(0),null,2,2,true,(x,z)->new CityMilitaryPlan.Surface(63,false,false,true)).isEmpty());
        var road=road(0);CityMilitaryPlan.Survey pit=(x,z)->z>=65?new CityMilitaryPlan.Surface(60,false,false,true):road.at(x,z);
        assertTrue(VehicleGaragePiece.Planner.find(base(0),null,2,2,true,pit).isEmpty());
    }
    @Test void checkpointAndStructureReferenceWindowRemainIndependentExclusions() {
        var checkpoint=new CityMilitaryPlan.Checkpoint(72,58,63,0);
        assertEquals(new VehicleGarageData.Site(-35,63,38,0),VehicleGaragePiece.Planner.find(base(0),checkpoint,2,2,true,road(0)).orElse(null));
        assertEquals(new VehicleGarageData.Site(-35,63,38,0),VehicleGaragePiece.Planner.find(base(0),null,-2,2,true,road(0)).orElse(null));
    }
    @Test void independentGarageRotationsPreserveAllThreeExistingRoadExits() {
        VehicleGarageData.Site[] expected={new VehicleGarageData.Site(69,63,38,0),new VehicleGarageData.Site(0,63,69,1),new VehicleGarageData.Site(-35,63,0,2),new VehicleGarageData.Site(38,63,-35,3)};
        for(int turn=0;turn<4;turn++)assertEquals(expected[turn],VehicleGaragePiece.Planner.find(base(turn),null,2,2,true,road(turn)).orElse(null));
    }
    @Test void newFieldBaseCanHaveAGarageWithoutAnExistingCityRoad(){
        CityMilitaryPlan.Survey field=(x,z)->new CityMilitaryPlan.Surface(63,false,false,false);
        assertTrue(VehicleGaragePiece.Planner.find(base(0),null,2,2,true,false,field).isPresent());
        assertTrue(VehicleGaragePiece.Planner.find(base(0),null,2,2,true,true,field).isEmpty());
        assertTrue(VehicleGaragePiece.Planner.find(base(0),null,2,2,false,false,field).isEmpty());
    }
    @Test void fieldApronKeepsWaterSteepTerrainAndSavedRotationBoundsSafe(){
        for(int turn=0;turn<4;turn++){
            CityMilitaryPlan.Survey flat=(x,z)->new CityMilitaryPlan.Surface(63,false,false,false);
            var site=VehicleGaragePiece.Planner.find(base(turn),null,2,2,true,false,flat).orElseThrow();
            var bounds=VehicleGaragePiece.Planner.bounds(site,true);
            for(int x:new int[]{0,30})for(int z:new int[]{0,42}){
                var point=site.world(x,z);assertTrue(bounds.isInside(new net.minecraft.core.BlockPos(point[0],63,point[1])));
            }
        }
        assertTrue(VehicleGaragePiece.Planner.find(base(0),null,2,2,true,false,(x,z)->new CityMilitaryPlan.Surface(63,true,false,false)).isEmpty());
        assertTrue(VehicleGaragePiece.Planner.find(base(0),null,2,2,true,false,(x,z)->new CityMilitaryPlan.Surface(59,false,false,false)).isEmpty());
    }

}

