package com.codex.blacklight.infection;

import com.codex.blacklight.city.*;
import com.codex.blacklight.registry.ModBlocks;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A transport destination always resolves to the actual organism's own conserved store. */
public final class ColonyNutrients {
    private ColonyNutrients(){}
    public record Mouth(BlockPos pos,ColonySupply stock,Runnable changed){}
    public static boolean isPort(BlockState state){return state.is(ModBlocks.BUILDING_CORE.get())||state.is(CityBlocks.HATCH.get());}
    public static Mouth resolve(ServerLevel level,BlockPos pos){
        if(!level.hasChunkAt(pos)||!level.isPositionEntityTicking(pos))return null;var state=level.getBlockState(pos);
        if(state.is(ModBlocks.BUILDING_CORE.get())){
            if(!BuildingInfectionConfig.ENABLED.get()||!BuildingInfectionSystem.allowedSite(level,pos))return null;var data=BuildingInfectionData.get(level);
            var colony=data.colonies.stream().filter(c->!c.dead&&c.origin.equals(pos)).findFirst().orElse(null);
            return colony==null?null:new Mouth(pos.immutable(),colony.supply,data::setDirty);
        }
        if(state.is(CityBlocks.HATCH.get())){var stock=CityHiveSupply.supply(level,pos);return stock==null?null:new Mouth(pos.immutable(),stock,()->CityData.get(level).setDirty());}
        return null;
    }
    public static List<Mouth> nearby(ServerLevel level,Vec3 from,double radius){
        var result=new ArrayList<Mouth>();double range=radius*radius;
        for(var colony:BuildingInfectionData.get(level).colonies)if(!colony.dead&&from.distanceToSqr(Vec3.atCenterOf(colony.origin))<=range){var node=resolve(level,colony.origin);if(node!=null)result.add(node);}
        if(CityTravel.isCity(level.dimension()))for(int hive=0;hive<2;hive++)for(int port=0;port<7;port++){
            var pos=CitySystem.port(level,hive,port);if(from.distanceToSqr(Vec3.atCenterOf(pos))<=range){var node=resolve(level,pos);if(node!=null)result.add(node);}
        }return result;
    }
}
