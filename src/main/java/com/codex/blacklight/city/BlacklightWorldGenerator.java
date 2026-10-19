package com.codex.blacklight.city;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** Stable world-map dimension generator; cities are atlas regions, not separate generator types. */
public final class BlacklightWorldGenerator extends FlatLevelSource {
    public static final MapCodec<BlacklightWorldGenerator> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
        FlatLevelGeneratorSettings.CODEC.fieldOf("settings").forGetter(BlacklightWorldGenerator::settings)).apply(i,BlacklightWorldGenerator::new));
    private volatile long seed;
    public BlacklightWorldGenerator(FlatLevelGeneratorSettings settings){super(settings);}
    @Override protected MapCodec<? extends ChunkGenerator> codec(){return CODEC;}
    @Override public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> lookup,RandomState random,long seed){this.seed=seed;return super.createState(lookup,random,seed);}
    @Override public void applyBiomeDecoration(WorldGenLevel level,ChunkAccess chunk,StructureManager structures){}
    @Override public int getMinY(){return 0;}
    @Override public int getGenDepth(){return WorldTerrain.HEIGHT;}
    @Override public int getSeaLevel(){return WorldTerrain.SEA;}
    @Override public int getSpawnHeight(LevelHeightAccessor h){return 64;}
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Blender b,RandomState r,StructureManager m,ChunkAccess chunk){
        var columns=WorldTerrain.chunk(seed,chunk.getPos().x,chunk.getPos().z);
        var pos=new BlockPos.MutableBlockPos();
        var surface=chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        var floor=chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        int bx=chunk.getPos().getMinBlockX(),bz=chunk.getPos().getMinBlockZ();
        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            var column=columns[z*16+x];int top=Math.min(column.top,chunk.getMaxBuildHeight()-1);
            int surfaceY=-1,floorY=-1;BlockState surfaceState=null,floorState=null;
            for(int y=Math.max(0,chunk.getMinBuildHeight());y<=top;y++){
                BlockState state=column.state(y);if(state.isAir())continue;
                chunk.setBlockState(pos.set(bx+x,y,bz+z),state,false);
                if(Heightmap.Types.WORLD_SURFACE_WG.isOpaque().test(state)){surfaceY=y;surfaceState=state;}
                if(Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(state)){floorY=y;floorState=state;}
            }
            // Newly created terrain columns need only their final height, not an update per block.
            if(surfaceY>=0)surface.update(x,surfaceY,z,surfaceState);
            if(floorY>=0)floor.update(x,floorY,z,floorState);
        }
        return CompletableFuture.completedFuture(chunk);
    }
    @Override public int getBaseHeight(int x,int z,Heightmap.Types type,LevelHeightAccessor h,RandomState random){
        var column=WorldTerrain.column(seed,x,z);
        for(int y=Math.min(column.top,h.getMaxBuildHeight()-1);y>=h.getMinBuildHeight();y--)if(type.isOpaque().test(column.state(y)))return y+1;
        return h.getMinBuildHeight();
    }
    @Override public NoiseColumn getBaseColumn(int x,int z,LevelHeightAccessor h,RandomState random){
        var column=WorldTerrain.column(seed,x,z);BlockState[] a=new BlockState[h.getHeight()];
        for(int i=0;i<a.length;i++)a[i]=column.state(h.getMinBuildHeight()+i);
        return new NoiseColumn(h.getMinBuildHeight(),a);
    }
}
