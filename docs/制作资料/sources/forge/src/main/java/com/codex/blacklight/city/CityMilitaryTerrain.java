package com.codex.blacklight.city;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.tags.BlockTags;

/** Forge city sampling reads immutable Futian columns; it never requests neighbour chunks. */
final class CityMilitaryTerrain {
    static boolean supported(ChunkGenerator generator){return generator instanceof CityChunkGenerator city&&city.futian();}
    static CityMilitaryPlan.Survey survey(ChunkGenerator generator,long seed){
        return (x,z)->legacy(seed,x,z);
    }
    private static CityMilitaryPlan.Surface legacy(long seed,int x,int z){
        if(!FutianMap.inside(x,z))return new CityMilitaryPlan.Surface(63,true,true,false);
        var column=FutianLayout.column(seed,x,z);BlockState floor=column.state(63);
        boolean water=!floor.getFluidState().isEmpty();
        boolean road=floor.is(Blocks.GRAY_CONCRETE)||floor.is(Blocks.BLACK_CONCRETE)||floor.is(Blocks.WHITE_CONCRETE);
        boolean blocked=Math.abs(x-670)<48&&Math.abs(z-820)<48;
        // Full footprint validation also rejects narrow buildings between coarse probes.
        for(int y=64;y<=Math.min(column.top,100)&&!blocked;y++){ var state=column.state(y); if(!state.isAir()&&!state.canBeReplaced()&&!state.is(BlockTags.LEAVES)&&!state.is(BlockTags.LOGS))blocked=true; }
        return new CityMilitaryPlan.Surface(63,water,blocked,road);
    }
    private CityMilitaryTerrain(){}
}

