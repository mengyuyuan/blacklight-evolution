package com.codex.blacklight.gametest;

import com.codex.blacklight.city.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.*;

/** Same workload before and after optimization; timings are evidence, never flaky assertions. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public class CityPerformanceGameTest {
    @GameTest(template="wall_test",timeoutTicks=600)
    public static void measureCityQueries(GameTestHelper h) {
        var key=ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath("blacklight","city_futian"));
        var level=h.getLevel().getServer().getLevel(key);
        var generator=level.getChunkSource().getGenerator();
        var random=level.getChunkSource().randomState();
        long checksum=0;
        for(int pass=0;pass<6;pass++) {
            long start=System.nanoTime();
            for(int i=0;i<512;i++) {
                int x=415+i%128,z=543+i/128;
                checksum+=generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE,level,random);
            }
            long heights=System.nanoTime()-start;start=System.nanoTime();
            for(int i=0;i<256;i++) {
                int x=415+i%64,z=543+i/64;
                var column=generator.getBaseColumn(x,z,level,random);
                checksum+=column.getBlock(63).isAir()?0:1;
            }
            long columns=System.nanoTime()-start;
            System.out.printf(java.util.Locale.ROOT,"CITY_PERF pass=%d heights_ms=%.3f columns_ms=%.3f checksum=%d%n",pass,heights/1e6,columns/1e6,checksum);
        }
        h.assertTrue(checksum>0,"empty benchmark");h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=600)
    public static void compareChunkFilling(GameTestHelper h){
        var level=h.getLevel().getServer().getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath("blacklight","city_futian")));
        var generator=level.getChunkSource().getGenerator();
        int[][] samples={{41,39},{27,37},{29,11},{29,22},{10,60},{89,76}};
        for(int pass=0;pass<6;pass++){
            long scalar=0,optimized=0;
            for(int[] sample:samples){
                var cp=new net.minecraft.world.level.ChunkPos(sample[0],sample[1]);
                var oldChunk=new net.minecraft.world.level.chunk.ProtoChunk(cp,net.minecraft.world.level.chunk.UpgradeData.EMPTY,level,level.registryAccess().registryOrThrow(Registries.BIOME),null);
                var newChunk=new net.minecraft.world.level.chunk.ProtoChunk(cp,net.minecraft.world.level.chunk.UpgradeData.EMPTY,level,level.registryAccess().registryOrThrow(Registries.BIOME),null);
                long start=System.nanoTime();referenceFill(level.getSeed(),oldChunk);scalar+=System.nanoTime()-start;
                start=System.nanoTime();
                generator.fillFromNoise(net.minecraft.world.level.levelgen.blending.Blender.empty(),level.getChunkSource().randomState(),level.structureManager(),newChunk).join();
                optimized+=System.nanoTime()-start;
                if(pass==0){
                    var pos=new net.minecraft.core.BlockPos.MutableBlockPos();
                    for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<320;y++){
                        pos.set(cp.getMinBlockX()+x,y,cp.getMinBlockZ()+z);
                        h.assertTrue(oldChunk.getBlockState(pos).equals(newChunk.getBlockState(pos)),"chunk fill changed "+pos);
                    }
                }
            }
            System.out.printf(java.util.Locale.ROOT,"CITY_FILL pass=%d reference_ms=%.3f optimized_ms=%.3f chunks=%d%n",pass,scalar/1e6,optimized/1e6,samples.length);
        }
        h.succeed();
    }
    /** Pre-optimization fill loop retained only in QA, excluded from release jars. */
    private static void referenceFill(long seed,net.minecraft.world.level.chunk.ProtoChunk chunk){
        int bx=chunk.getPos().getMinBlockX(),bz=chunk.getPos().getMinBlockZ();
        var tile=FutianMap.tile(bx>>4,bz>>4);var pos=new net.minecraft.core.BlockPos.MutableBlockPos();
        var surface=chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        var floor=chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            int top=Math.max(tile.top(x,z),Math.max(FutianCivicCentre.top(bx+x,bz+z),FutianPublicSpace.top(bx+x,bz+z)));
            for(int y=0;y<=top;y++){
                var base=y<63?FutianMap.state(bx+x,y,bz+z):tile.state(x,y,z);
                var state=FutianLayout.overlay(seed,bx+x,y,bz+z,base);if(state.isAir())continue;
                chunk.setBlockState(pos.set(bx+x,y,bz+z),state,false);surface.update(x,y,z,state);floor.update(x,y,z,state);
            }
        }
    }
}
