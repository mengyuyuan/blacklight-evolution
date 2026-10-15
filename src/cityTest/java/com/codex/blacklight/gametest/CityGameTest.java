package com.codex.blacklight.gametest;
import com.codex.blacklight.city.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public class CityGameTest {
    @GameTest(template="wall_test") public static void publicAxisRemainsWalkable(GameTestHelper h){
        h.assertTrue(FutianLayout.state(42,668,63,820).is(Blocks.SMOOTH_QUARTZ),"central pedestrian paving missing");
        for(int x=658;x<=678;x++)for(int z=725;z<=945;z++)for(int y=64;y<=67;y++)
            h.assertTrue(FutianLayout.state(42,x,y,z).isAir(),"pedestrian axis obstructed at "+x+","+y+","+z);
        h.assertTrue(FutianLayout.state(42,648,69,800).is(Blocks.SEA_LANTERN),"plaza lighting missing");
        h.assertTrue(FutianLayout.state(42,463,70,171).is(Blocks.YELLOW_CONCRETE),"concert hall tree support missing");
        h.assertTrue(FutianLayout.state(42,463,70,341).is(Blocks.QUARTZ_BLOCK),"library tree support missing");
        h.succeed();
    }
    @GameTest(template="wall_test") public static void civicCentreHasThinRoofAndOpenAtrium(GameTestHelper h){
        h.assertTrue(FutianLayout.state(42,638,100,625).is(Blocks.YELLOW_CONCRETE),"yellow cylindrical tower missing");
        h.assertTrue(FutianLayout.state(42,706,100,625).is(Blocks.RED_CONCRETE),"red square tower missing");
        h.assertTrue(FutianLayout.state(42,668,90,625).isAir(),"public atrium filled by building extrusion");
        h.assertTrue(FutianLayout.state(42,668,140,625).isAir(),"roof is a solid extruded wall");
        h.assertTrue(FutianLayout.state(42,668,145,625).is(Blocks.LIGHT_BLUE_CONCRETE),"central roof missing");
        for(int z=704;z<=719;z++)h.assertTrue(FutianLayout.state(42,668,63,z).isSolid(),"arrival staircase has hole");
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=600) public static void futianSceneryAndHiveExits(GameTestHelper h){
        var key=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation("blacklight","city_futian"));
        var city=h.getLevel().getServer().getLevel(key);h.assertTrue(city!=null&&CitySystem.futian(city),"missing Futian generator");
        // Actual generated chunks, including a landmark and block/chunk boundaries.
        for(int[] point:new int[][]{{670,820},{431,615},{639,639},{640,640},{1439,1231}}){
            int x=point[0],z=point[1];city.getChunk(x>>4,z>>4);
            for(int y:new int[]{0,62,63,64,65,85,110,150,250,319})h.assertTrue(city.getBlockState(new BlockPos(x,y,z)).equals(FutianLayout.state(city.getSeed(),x,y,z)),"Futian template mismatch");
            h.assertTrue(city.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)==city.getChunkSource().getGenerator().getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE,city,city.getChunkSource().randomState()),"Futian heightmap mismatch");
        }
        for(int i=0;i<2;i++)for(int n=0;n<7;n++){
            BlockPos p=CitySystem.port(city,i,n);city.getChunkAt(p);var state=city.getBlockState(p);
            h.assertTrue(state.is(CityBlocks.HATCH.get()),"Futian hive hatch missing");
            var outside=p.relative(state.getValue(CityHatchBlock.FACING),3).below();city.getChunkAt(outside);
            h.assertTrue(city.getBlockState(outside).isAir()&&city.getBlockState(outside.above()).isAir()&&city.getBlockState(outside.below()).isSolid(),"Futian hatch exit obstructed");
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=400) public static void futianSeedVariationProtectsCivicCentre(GameTestHelper h){
        var choices=new java.util.HashSet<String>();
        for(long seed=0;seed<30;seed++){
            choices.add(FutianLayout.hive(seed,0).name()+FutianLayout.hive(seed,1).name());
            h.assertTrue(FutianLayout.zone(seed,670,615)==CityLayout.Zone.NORMAL,"civic centre infected");
            h.assertTrue(FutianLayout.zone(seed,670,820)==CityLayout.Zone.NORMAL,"arrival plaza infected");
            for(int i=0;i<2;i++)for(var port:FutianLayout.hive(seed,i).ports()){
                h.assertTrue(FutianLayout.state(seed,port.pos().getX(),65,port.pos().getZ()).is(CityBlocks.HATCH.get()),"selected hive not generated");
            }
        }
        h.assertTrue(choices.size()>=8,"infection layout not varying");h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=400) public static void dimensionAndChunkSeams(GameTestHelper h){
        var city=h.getLevel().getServer().getLevel(CityTravel.CITY);
        h.assertTrue(city!=null,"city dimension missing");
        h.assertTrue(city.getChunkSource().getGenerator() instanceof CityChunkGenerator,"wrong generator");
        long seed=city.getSeed();
        for(int x:new int[]{15,16,63,64,127,128})for(int z:new int[]{15,16,63,64}){
            city.getChunk(x>>4,z>>4);
            for(int y:new int[]{0,59,63,64,68,74,90})h.assertTrue(city.getBlockState(new BlockPos(x,y,z)).equals(CityChunkGenerator.state(seed,x,y,z)),"chunk seam differs at "+x+","+y+","+z);
            int actual=city.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);
            int expected=city.getChunkSource().getGenerator().getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE,city,city.getChunkSource().randomState());
            h.assertTrue(actual==expected,"heightmap mismatch at "+x+","+z+": "+actual+" vs "+expected);
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=400) public static void hivePortsAndAccess(GameTestHelper h){
        var city=h.getLevel().getServer().getLevel(CityTravel.CITY);long seed=city.getSeed();
        for(int hive=0;hive<2;hive++){
            for(int i=0;i<7;i++){
                BlockPos p=CitySystem.port(seed,hive,i);city.getChunkAt(p);
                var state=city.getBlockState(p);h.assertTrue(state.is(CityBlocks.HATCH.get()),"missing hatch "+i);
                var outside=p.relative(state.getValue(CityHatchBlock.FACING),3).below();city.getChunkAt(outside);
                h.assertTrue(city.getBlockState(outside).isAir()&&city.getBlockState(outside.above()).isAir(),"spawn outside obstructed");
            }
            int x=CityLayout.hiveX(seed,hive)*64,z=CityLayout.hiveZ(seed,hive)*64;
            city.getChunkAt(new BlockPos(x+32,65,z+14));
            h.assertTrue(city.getBlockState(new BlockPos(x+32,65,z+14)).isAir(),"entrance blocked");
            for(int y=65;y<64+CityLayout.lot(seed,x/64,z/64).floors()*5;y++)h.assertTrue(CityChunkGenerator.state(seed,x+48,y,z+49).is(Blocks.LADDER),"service ladder interrupted");
        }
        h.succeed();
    }
    @GameTest(template="wall_test") public static void savedLifecycleAndSettings(GameTestHelper h){
        var data=new CityData();data.destroyedMask=1;data.brokenPorts[0]=127;data.facadeProgress[0]=87;
        data.infectedMask=1L<<50;data.spreadProgress[50]=122;data.populatedMask=1L<<63;data.population=false;data.spread=false;data.facade=false;data.nextSpread=9999;
        var restored=CityData.load(data.save(new CompoundTag()));
        h.assertTrue(restored.destroyed(0)&&!restored.destroyed(1),"destroyed hives revived");
        h.assertTrue(restored.infected(2,6)&&restored.spreadProgress[50]==122,"spread lost");
        h.assertTrue(restored.populatedMask==data.populatedMask&&restored.brokenPorts[0]==127&&restored.facadeProgress[0]==87,"progress lost");
        h.assertTrue(!restored.population&&!restored.spread&&!restored.facade&&restored.nextSpread==9999,"server switches not persisted");h.succeed();
    }
    @GameTest(template="wall_test") public static void windowsOnEveryFacade(GameTestHelper h){
        long seed=42;int cx=CityLayout.hiveX(seed,0)*64,cz=CityLayout.hiveZ(seed,0)*64;
        for(BlockPos p:new BlockPos[]{new BlockPos(cx+17,85,cz+14),new BlockPos(cx+17,85,cz+50),new BlockPos(cx+14,85,cz+17),new BlockPos(cx+50,85,cz+17)}){
            h.assertTrue(CityChunkGenerator.state(seed,p.getX(),p.getY(),p.getZ()).is(Blocks.CYAN_STAINED_GLASS),"missing facade window "+p);
        }
        h.succeed();
    }
}
