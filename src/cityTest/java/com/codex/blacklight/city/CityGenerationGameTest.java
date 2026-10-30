package com.codex.blacklight.city;

import net.minecraft.gametest.framework.*;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public class CityGenerationGameTest {
    private static net.minecraft.server.level.ServerLevel city(GameTestHelper h){
        return h.getLevel().getServer().getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation("blacklight","city_futian")));
    }
    @GameTest(template="wall_test",timeoutTicks=600)
    public static void optimizedColumnsKeepBlocksAndHeights(GameTestHelper h){
        var level=city(h);var generator=level.getChunkSource().getGenerator();
        var points=new java.util.ArrayList<BlockPos>();
        for(int x=-1;x<=1440;x+=47)for(int z=-1;z<=1232;z+=53)points.add(new BlockPos(x,0,z));
        for(int[] p:new int[][]{{668,625},{638,625},{706,625},{668,820},{463,171},{463,341},{1439,1231},{1440,1232}})points.add(new BlockPos(p[0],0,p[1]));
        for(int hive=0;hive<2;hive++)for(int i=0;i<7;i++)points.add(CitySystem.port(level,hive,i));
        for(var p:points){
            var column=generator.getBaseColumn(p.getX(),p.getZ(),level,level.getChunkSource().randomState());
            int[] tops=new int[Heightmap.Types.values().length];
            for(int y=0;y<320;y++){
                var expected=FutianLayout.state(level.getSeed(),p.getX(),y,p.getZ());
                h.assertTrue(column.getBlock(y).equals(expected),"column mismatch "+p+" y="+y);
                for(var type:Heightmap.Types.values())if(type.isOpaque().test(expected))tops[type.ordinal()]=y+1;
            }
            for(var type:Heightmap.Types.values())h.assertTrue(generator.getBaseHeight(p.getX(),p.getZ(),type,level,level.getChunkSource().randomState())==tops[type.ordinal()],"height mismatch "+p+" "+type);
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=1400,batch="city_pregen_lifecycle")
    public static void pregenPausesResumesPreservesEditsAndReleasesTickets(GameTestHelper h){
        var level=city(h);CityPregenerator.start(level,1);var data=CityGenerationData.get(level);
        data.cursor=47;data.setDirty();
        var cell=CityGenerationPlan.cells(1).get(47);var marker=new BlockPos(cell.x()*16,210,cell.z()*16);
        level.setBlock(marker,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
        CityPregenerator.pause(level);
        var saved=CityGenerationData.load(data.save(new CompoundTag()));
        h.assertTrue(!saved.running&&saved.cursor==47&&saved.mode==1,"pause/progress did not persist");
        CityPregenerator.tick(level);h.assertTrue(!CityPregenerator.hasTicket(level)&&data.cursor==47,"paused job advanced");
        CityPregenerator.resume(level);
        com.codex.blacklight.gametest.ForgeTestLifecycle.info(h).addListener(new GameTestListener(){
            public void testStructureLoaded(GameTestInfo info){}
            public void testPassed(GameTestInfo info){CityPregenerator.unload(level);}
            public void testFailed(GameTestInfo info){
                com.codex.blacklight.BlacklightNeoMod.LOGGER.warn("Pregen lifecycle fixture failed: {}",CityPregenerator.diagnostics(level));
                CityPregenerator.pause(level);
            }
            public void testAddedForRerun(GameTestInfo original,GameTestInfo retry){}
        });
        h.onEachTick(()->{
            // Sleeping 50ms INSIDE the measured tick makes Forge's 35ms busy
            // guard correctly pause forever. A small yield lets real chunk IO
            // progress without manufacturing a permanently busy server.
            java.util.concurrent.locks.LockSupport.parkNanos(10_000_000L);
            CityPregenerator.tick(level);
            h.assertTrue(data.failure.isEmpty(),"pregen failed: "+data.failure);
            if(data.cursor==49&&!data.running){
                h.assertTrue(!CityPregenerator.hasTicket(level),"pregen leaked ticket");
                h.assertTrue(level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK),"pregen overwrote player edit");
                level.setBlock(marker,Blocks.AIR.defaultBlockState(),3);CityPregenerator.unload(level);h.succeed();
            }
        });
    }
}
