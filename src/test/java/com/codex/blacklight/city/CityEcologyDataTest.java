package com.codex.blacklight.city;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CityEcologyDataTest {
    @Test void NewCitiesDoNotInheritOtherCityExposureOrOfflineTime(){
        var data=new CityEcologyData();var shenzhen=CityEcologyData.key("shenzhen",0,0);
        var losAngeles=CityEcologyData.key("los_angeles",0,0);
        for(int i=0;i<14;i++)data.advance(shenzhen,i,200);
        assertEquals(4,data.get(shenzhen).stage(4,0,200));
        var fresh=data.advance(losAngeles,Long.MAX_VALUE-2,20);
        assertEquals(20,fresh.exposure());assertEquals(0,fresh.stage(4,0,200));
        assertEquals(2,fresh.stage(4,2,200));
        assertEquals(0,fresh.stage(0,2,200));
    }
    @Test void DuplicatePlayersDoNotAccelerateDistrictAndClearingRemainsLocalAcrossSave(){
        var data=new CityEcologyData();var a=CityEcologyData.key("shenzhen",0,0);var b=CityEcologyData.key("los_angeles",0,0);
        data.advance(a,10,200);data.advance(a,10,200);data.advance(b,10,200);
        assertEquals(200,data.get(a).exposure());
        data.clear(a,600);
        var loaded=CityEcologyData.load(data.save(new CompoundTag(),null),null);
        assertEquals(600,loaded.get(a).suppression());assertEquals(1,loaded.get(a).cleared());
        assertEquals(0,loaded.get(a).stage(4,2,200));assertEquals(2,loaded.get(b).stage(4,2,200));
        loaded.advance(a,1_000_000,20);assertEquals(580,loaded.get(a).suppression());
        assertEquals(220,loaded.get(a).exposure());
    }
    @Test void NegativeCoordinatesAndLongRunningWorldsRemainSafe(){
        assertEquals(new CityEcologyData.Key("city",-1,-2),CityEcologyData.key("city",-1,-257));
        var rows=new net.minecraft.nbt.ListTag();var row=new CompoundTag();row.putString("city","city");
        row.putLong("exposure",Long.MAX_VALUE-1);rows.add(row);var tag=new CompoundTag();tag.put("districts",rows);
        var data=CityEcologyData.load(tag,null);var key=new CityEcologyData.Key("city",0,0);
        data.advance(key,1,200);assertEquals(Long.MAX_VALUE,data.get(key).exposure());
    }
}
