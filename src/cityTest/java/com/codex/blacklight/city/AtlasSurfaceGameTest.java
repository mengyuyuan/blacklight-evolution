package com.codex.blacklight.city;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class AtlasSurfaceGameTest {
    @GameTest(template="wall_test",timeoutTicks=600)
    public static void generatedStreetsFollowTheApproachAndTheFoundationStaysStructural(GameTestHelper h){
        var world=h.getLevel().getServer().getLevel(CityTravel.WORLD);
        long seed=world.getSeed();
        // This bridge used to put the deck at Y121 where the connecting street was at Y83.
        int last=-1;
        for(int step=-4;step<=32;step++){
            int x=-1750-step,z=(int)Math.floor(-2254.1-step*(3.8/58.8));
            var column=WorldTerrain.column(seed,x,z);
            int deck=column.bridge==0?column.ground:column.bridge;
            var pavement=column.state(deck);
            h.assertTrue(column.road==6&&(pavement.is(Blocks.GRAY_CONCRETE)||pavement.is(Blocks.WHITE_CONCRETE)),
                    "missing bridge/road pavement at "+x+","+z+" y="+deck+" "+pavement);
            h.assertTrue(last<0||Math.abs(deck-last)<=1,"disconnected approach at "+x+","+z+" elevations "+last+"/"+deck);
            last=deck;
        }
        var road=WorldTerrain.column(seed,1535,2005);
        h.assertTrue(road.road==6 && road.ground<=WorldAtlas.ground(1535,2005)-8,"isolated urban road crest remains");
        var building=WorldTerrain.column(seed,-364,-4686);
        h.assertTrue(building.ground<=172 && building.state(200).isAir() && !building.state(243).isAir(),
                "a downslope building floor raised a seventy-block artificial hill");
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=600)
    public static void roadsKeepClearanceThroughBuildingsAndUnderUpperDecks(GameTestHelper h){
        var world=h.getLevel().getServer().getLevel(CityTravel.WORLD);long seed=world.getSeed();
        var street=WorldTerrain.column(seed,1538,1604);
        h.assertTrue(street.atGrade&&!street.parts.isEmpty(),"the mapped street runs through a building footprint");
        for(int dy=1;dy<=3;dy++)h.assertTrue(street.state(street.ground+dy).isAir(),"a building floor or wall closes the street");
        int x=3466,z=1608;var crossing=WorldTerrain.column(seed,x,z);
        int lower=crossing.bridge-5;
        for(int deck:new int[]{lower,crossing.bridge}){
            var state=crossing.state(deck);
            h.assertTrue((state.is(Blocks.GRAY_CONCRETE)||state.is(Blocks.WHITE_CONCRETE))&&crossing.state(deck+1).isAir()
                    &&crossing.state(deck+2).isAir(),"one of the two mapped viaducts is missing or blocked at "+deck);
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=600)
    public static void realChunksKeepInlandDepressionsDryAndContainMappedWater(GameTestHelper h){
        var world=h.getLevel().getServer().getLevel(CityTravel.WORLD);var generator=world.getChunkSource().getGenerator();
        for(int[] sample:new int[][]{{-11088,2832,0},{13096144,-1278960,0},{-9397,-170,1},{-17197,-8714,1},{500000,500000,1}}){
            int x=sample[0],z=sample[1];var column=WorldTerrain.column(world.getSeed(),x,z);
            var cp=new ChunkPos(Math.floorDiv(x,16),Math.floorDiv(z,16));
            var chunk=new ProtoChunk(cp,UpgradeData.EMPTY,world,world.registryAccess().registryOrThrow(Registries.BIOME),null);
            generator.fillFromNoise(net.minecraft.world.level.levelgen.blending.Blender.empty(),world.getChunkSource().randomState(),world.structureManager(),chunk).join();
            if(sample[2]==0){
                h.assertTrue(column.water==0 && chunk.getBlockState(new BlockPos(x,63,z)).isAir()
                        && chunk.getBlockState(new BlockPos(x,column.ground,z)).getFluidState().isEmpty(),"an enclosed inland hollow filled with ocean water");
            }else{
                h.assertTrue(column.water>column.ground && chunk.getBlockState(new BlockPos(x,column.water,z)).is(Blocks.WATER),
                        "mapped reservoir or unmapped sea lost its water: "+x+","+z);
                if(x==-9397)h.assertTrue(column.water<=63 && chunk.getBlockState(new BlockPos(x,95,z)).isAir(),
                        "the lake rose above its surveyed bank");
            }
        }
        h.succeed();
    }
}
