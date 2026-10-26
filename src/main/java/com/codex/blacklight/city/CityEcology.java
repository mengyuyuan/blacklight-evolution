package com.codex.blacklight.city;

import com.codex.blacklight.infection.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Common ecology for authored and future atlas cities; no terrain generation or map mutation. */
public final class CityEcology {
    private CityEcology(){}
    public static String atlasCityId(int x,int z){return WorldAtlas.regionId(x,z);}
    public static String cityId(ServerLevel level,BlockPos pos){
        if(level.dimension().equals(CityTravel.WORLD))return atlasCityId(pos.getX(),pos.getZ());
        if(level.dimension().equals(CityTravel.CITY))return "legacy_city";
        return level.dimension().location().toString();
    }
    public static CityEcologyData.Key key(ServerLevel level,BlockPos pos){
        return CityEcologyData.key(cityId(level,pos),pos.getX(),pos.getZ());
    }
    public static int authoredStage(ServerLevel level,BlockPos pos){
        if(!CityTravel.isCity(level.dimension()))return 0;
        var zone=level.dimension().equals(CityTravel.WORLD)?WorldTerrain.zone(level.getSeed(),pos.getX(),pos.getZ())
                :CitySystem.futian(level)?FutianLayout.zone(level.getSeed(),pos.getX(),pos.getZ())
                :CityLayout.zone(level.getSeed(),Math.floorDiv(pos.getX(),64),Math.floorDiv(pos.getZ(),64));
        return switch(zone){case INFECTED->2;case CONFLICT,EDGE->1;default->0;};
    }
    public static void advance(ServerLevel level,BlockPos pos){
        // Non-city terrain uses the shared outbreak clock directly.
        if(CityTravel.isCity(level.dimension()))for(int x:new int[]{-96,0,96})for(int z:new int[]{-96,0,96}){
            var near=pos.offset(x,0,z);
            if(level.getChunkSource().getChunkNow(near.getX()>>4,near.getZ()>>4)!=null)
                CityEcologyData.get(level).advance(key(level,near),level.getGameTime(),OutbreakConfig.INTERVAL.get());
        }
    }
    public static int stage(ServerLevel level,BlockPos pos){
        int global=WorldOutbreakSystem.stage(level);
        if(!CityTravel.isCity(level.dimension()))return global;
        var district=CityEcologyData.get(level).get(key(level,pos));
        return district==null?Math.min(global,authoredStage(level,pos))
                :district.stage(global,authoredStage(level,pos),OutbreakConfig.DAY_TICKS.get());
    }
    public static void cleared(ServerLevel level,BlockPos pos){
        if(CityTravel.isCity(level.dimension()))CityEcologyData.get(level).clear(key(level,pos),OutbreakConfig.DAY_TICKS.get()*3L);
    }
}
