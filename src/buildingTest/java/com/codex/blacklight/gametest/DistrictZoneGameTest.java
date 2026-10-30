package com.codex.blacklight.gametest;

import com.codex.blacklight.city.*;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModEntities;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Explicit long scheduling intervals test real inputs; not default-time ecology. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class DistrictZoneGameTest {
    private static void architecture(GameTestHelper h,net.minecraft.server.level.ServerLevel level){
        var f=new BuildingAssimilationGameTest.Fixture(h,level);int interval=OutbreakConfig.INTERVAL.get();
        f.run(()->{try{
            OutbreakConfig.INTERVAL.set(24000);
            var key=CityEcology.key(level,f.origin);var data=CityEcologyData.get(level);
            h.assertTrue(CityEcology.zone(level,f.origin)==DistrictThreat.Zone.GREEN,"a new unexposed ordinary district is green");
            int chunks=level.getChunkSource().getLoadedChunksCount();
            CityEcology.advanceBatch(level,List.of(f.origin,f.origin));
            var district=data.get(key);h.assertTrue(district!=null&&district.zone()==DistrictThreat.Zone.RED,"an actual supported living architectural core provides pressure; pressure="+(district==null?-1:district.pressure()));
            int pressure=district.pressure();long exposure=district.exposure();
            CityEcology.advanceBatch(level,List.of(f.origin));
            h.assertTrue(district.pressure()==pressure&&district.exposure()==exposure,"two nearby players or reentrant observation cannot duplicate work");
            var remote=f.origin.offset(1_000_000,0,1_000_000);h.assertTrue(level.getChunkSource().getChunkNow(remote.getX()>>4,remote.getZ()>>4)==null,"remote fixture starts unloaded");
            CityEcology.advanceBatch(level,List.of(remote));
            h.assertTrue(level.getChunkSource().getLoadedChunksCount()==chunks&&data.get(CityEcology.key(level,remote))==null,"frontier accounting never requests remote chunks or records unseen cells");
            var saved=CityEcologyData.load(data.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
            h.assertTrue(saved.get(key).pressure()==pressure&&saved.get(key).zone()==DistrictThreat.Zone.RED,"actual dimension ecology survives NBT serialization");
            level.getDataStorage().set("blacklight_city_ecology",saved);
            level.setBlock(f.origin,Blocks.AIR.defaultBlockState(),3);
            h.assertTrue(f.colony.dead,"destroying the actual source retires its saved colony");
            if(MilitaryCampaignSystem.enabled(level)) {
                h.assertTrue(saved.get(key).pressure()<pressure,"clearing a source in a campaign dimension must reduce recorded infection pressure");
                int after=saved.get(key).pressure();BuildingInfectionSystem.destroy(level,f.origin);
                h.assertTrue(saved.get(key).pressure()==after,"the same dead source cannot award a second pressure reduction");
            } else h.assertTrue(saved.get(key).pressure()==pressure&&CityEcology.zone(level,f.origin)==DistrictThreat.Zone.RED,"dimensions outside the military campaign preserve infection history after source removal");
        }finally{OutbreakConfig.INTERVAL.set(interval);}});
    }
    @GameTest(template="wall_test",timeoutTicks=250,batch="district_architecture_overworld")
    public static void actualArchitecturalPressureAndHistoryWorkInTheOverworld(GameTestHelper h){architecture(h,h.getLevel());}
    @GameTest(template="wall_test",timeoutTicks=650,batch="district_architecture_nether")
    public static void actualArchitecturalPressureAndHistoryWorkInTheNether(GameTestHelper h){architecture(h,h.getLevel().getServer().getLevel(Level.NETHER));}
    @GameTest(template="wall_test",timeoutTicks=650,batch="district_architecture_world")
    public static void actualArchitecturalPressureAndHistoryWorkInTheBlacklightWorld(GameTestHelper h){architecture(h,h.getLevel().getServer().getLevel(CityTravel.WORLD));}

    @GameTest(template="wall_test",timeoutTicks=250,batch="district_living_source")
    public static void actualLivingHostsDrivePressureAndRemovalCannotAwardFalseCleansing(GameTestHelper h){
        var f=new BuildingAssimilationGameTest.Fixture(h,h.getLevel());int interval=OutbreakConfig.INTERVAL.get();
        f.run(()->{try{
            // Isolate the host input from the architectural source in this case.
            f.colony.dead=true;OutbreakConfig.INTERVAL.set(24000);
            var hosts=new ArrayList<BlacklightInfectedHost>();
            for(int i=0;i<2;i++){var host=ModEntities.INFECTED_HOST.get().create(f.level);host.setPos(f.origin.north(2).east(i*3).getBottomCenter());host.inherit(EntityType.PIG.create(f.level));host.advanceTissue(48000);host.setNoAi(true);host.setNoGravity(true);f.level.addFreshEntity(host);f.born.add(host);hosts.add(host);}
            CityEcology.advanceBatch(f.level,List.of(f.origin));var d=CityEcologyData.get(f.level).get(CityEcology.key(f.level,f.origin));
            h.assertTrue(d!=null&&d.zone()==DistrictThreat.Zone.RED,"real mature host presence, not a mocked count, creates pressure");
            int before=d.pressure();hosts.forEach(BlacklightInfectedHost::discard);h.assertTrue(d.pressure()==before,"discard or unload is not a player cleansing kill");
            h.assertTrue(CityEcology.populationCap(f.level,f.origin)==OutbreakConfig.LOCAL_CAP.get(),"red uses the configured population budget");
            var green=f.origin.offset(512,0,512);h.assertTrue(CityEcology.populationCap(f.level,green)==Math.min(8,OutbreakConfig.LOCAL_CAP.get()),"an unexposed district keeps its quiet population budget without deleting existing bodies");
        }finally{OutbreakConfig.INTERVAL.set(interval);}});
    }
    @GameTest(template="wall_test",timeoutTicks=400,batch="district_loaded_frontier")
    public static void actualLoadedFrontierAdvancesOneDistrictWithoutSameBatchCascade(GameTestHelper h){
        var f=new BuildingAssimilationGameTest.Fixture(h,h.getLevel());int interval=OutbreakConfig.INTERVAL.get();
        int boundary=(Math.floorDiv(f.origin.getX(),CityEcologyData.CELL)+1)*CityEcologyData.CELL;
        var left=new BlockPos(boundary-8,f.origin.getY(),f.origin.getZ());var right=left.east(16);
        for(var p:List.of(left,right))for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            var cp=new net.minecraft.world.level.ChunkPos((p.getX()>>4)+x,(p.getZ()>>4)+z);
            if(!f.level.getForcedChunks().contains(cp.toLong())){f.forced.add(cp);f.level.setChunkForced(cp.x,cp.z,true);}f.level.getChunk(cp.x,cp.z);
        }
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.level.isPositionEntityTicking(left)&&f.level.isPositionEntityTicking(right),"waiting for the two actual occupied frontier chunks"))
          .thenExecute(()->{
            OutbreakConfig.INTERVAL.set(24000);
            for(int i=0;i<2;i++){var host=ModEntities.INFECTED_HOST.get().create(f.level);host.setPos(left.east(i*2).getBottomCenter());host.inherit(EntityType.PIG.create(f.level));host.advanceTissue(48000);host.setNoAi(true);host.setNoGravity(true);f.level.addFreshEntity(host);f.born.add(host);}
            CityEcology.advanceBatch(f.level,List.of(left,right,left));
            var data=CityEcologyData.get(f.level);
            h.assertTrue(data.get(CityEcology.key(f.level,left)).zone()==DistrictThreat.Zone.RED,"the real source side becomes red");
            h.assertTrue(data.get(CityEcology.key(f.level,right)).pressure()==0,"a newly red cell cannot cascade during the same snapshot");
          }).thenIdle(1).thenExecute(()->{try{
            CityEcology.advanceBatch(f.level,List.of(left,right));var d=CityEcologyData.get(f.level).get(CityEcology.key(f.level,right));
            h.assertTrue(d.pressure()==2400&&d.zone()==DistrictThreat.Zone.YELLOW,"one loaded red neighbour supplies one actual scheduled frontier, pressure="+d.pressure());
            long exposure=d.exposure();CityEcology.advanceBatch(f.level,List.of(right,left,right));
            h.assertTrue(d.pressure()==2400&&d.exposure()==exposure,"repeated player order cannot multiply the same frontier tick");
        }finally{OutbreakConfig.INTERVAL.set(interval);f.close();}h.succeed();});
    }
}
