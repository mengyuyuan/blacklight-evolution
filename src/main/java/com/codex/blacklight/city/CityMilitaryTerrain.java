package com.codex.blacklight.city;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.tags.BlockTags;

/** Immutable map sampling; no neighbour chunks are requested during structure selection. */
final class CityMilitaryTerrain {
    static boolean supported(ChunkGenerator generator){return generator instanceof BlacklightWorldGenerator||generator instanceof CityChunkGenerator city&&city.futian();}
    static CityMilitaryPlan.Survey survey(ChunkGenerator generator,long seed){
        return (x,z)->{
            if(generator instanceof BlacklightWorldGenerator){
                var c=WorldTerrain.column(seed,x,z);
                if(c.core==null)return new CityMilitaryPlan.Surface(c.ground,c.water>0,
                        !c.parts.isEmpty()||c.landmark!=null||c.bridge>0||c.arnis!=null||c.scenery&&c.top>c.ground+1,c.road>0);
            }
            return legacy(seed,x,z);
        };
    }
    /** Bounded coarse noise survey for new ordinary-world starts, not chunk requests. */
    static CityMilitaryPlan.Survey field(net.minecraft.world.level.levelgen.structure.Structure.GenerationContext context){
        var cache=new java.util.HashMap<Long,CityMilitaryPlan.Surface>();
        return (x,z)->{
            int sx=Math.floorDiv(x,8)*8,sz=Math.floorDiv(z,8)*8;long key=net.minecraft.world.level.ChunkPos.asLong(sx,sz);
            var saved=cache.get(key);if(saved!=null)return saved;
            if(cache.size()>=1024)return new CityMilitaryPlan.Surface(0,false,true,false);
            int ground=context.chunkGenerator().getFirstOccupiedHeight(sx,sz,net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG,context.heightAccessor(),context.randomState());
            int surface=context.chunkGenerator().getFirstOccupiedHeight(sx,sz,net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,context.heightAccessor(),context.randomState());
            var result=new CityMilitaryPlan.Surface(ground,surface>ground,false,false);cache.put(key,result);return result;
        };
    }
    private static CityMilitaryPlan.Surface legacy(long seed,int x,int z){
        if(!FutianMap.inside(x,z))return new CityMilitaryPlan.Surface(63,true,true,false);
        var column=FutianLayout.column(seed,x,z);BlockState floor=column.state(63);
        boolean water=!floor.getFluidState().isEmpty();
        boolean road=floor.is(Blocks.GRAY_CONCRETE)||floor.is(Blocks.BLACK_CONCRETE)||floor.is(Blocks.WHITE_CONCRETE);
        boolean blocked=Math.abs(x-670)<48&&Math.abs(z-820)<48;
        // Scan the complete proposed footprint later, including narrow map buildings.
        for(int y=64;y<=Math.min(column.top,100)&&!blocked;y++){
            var state=column.state(y);
            if(!state.isAir()&&!state.canBeReplaced()&&!state.is(BlockTags.LEAVES)&&!state.is(BlockTags.LOGS))blocked=true;
        }
        return new CityMilitaryPlan.Surface(63,water,blocked,road);
    }
    private CityMilitaryTerrain(){}
}
