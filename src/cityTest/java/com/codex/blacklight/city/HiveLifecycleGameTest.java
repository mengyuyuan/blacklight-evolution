package com.codex.blacklight.city;

import com.codex.blacklight.config.GameplayBalanceConfig;
import com.codex.blacklight.infection.OutbreakConfig;
import com.codex.blacklight.infection.OutbreakData;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public class HiveLifecycleGameTest {
    @GameTest(template="wall_test",timeoutTicks=500,batch="city_hive_mutation")
    public static void sevenPortsStopProductionAndSpread(GameTestHelper h){
        var f=new Fixture(h);
        h.startSequence().thenWaitUntil(()->{
            for(var p:f.ports.keySet())
                h.assertTrue(f.level.isPositionEntityTicking(p),"waiting for generated hive port to tick at "+p);
        }).thenExecute(()->{
            f.level.getAllEntities().forEach(e->{if(e instanceof Mob)f.originalMobs.add(e.getUUID());});
            f.data.hiveSupply[0].receive(128);
            f.data.hiveBroodTicks[0]=CityConfig.HIVE_BROOD_INTERVAL.get();
        }).thenWaitUntil(()->{
            h.assertTrue(f.data.hiveSustained[0]&&f.data.hiveBroodTicks[0]==0,
                    "waiting for a real city tick to reject the ready brood at zero chance");
        }).thenExecute(()->{
            h.assertTrue(f.data.hiveBirths[0]==0&&f.data.hiveSupply[0].reserve()==128,
                    "rejected brood recorded a birth or spent its material");
            GameplayBalanceConfig.BROOD_CHANCE.set(1d);
            f.data.hiveBroodTicks[0]=CityConfig.HIVE_BROOD_INTERVAL.get();
        }).thenWaitUntil(()->{
            h.assertTrue(f.data.hiveBirths[0]>0,"funded live hive did not publish a birth on a city tick");
        }).thenExecute(()->{
            h.assertTrue(f.data.hiveBirths[0]==1&&f.data.hiveBirths[1]==0&&f.data.hiveSupply[0].reserve()<128,
                    "the emitting hive must pay for and count exactly one child");
            for(int i=0;i<7;i++){
                var port=CitySystem.port(f.level,0,i);
                var state=f.level.getBlockState(port);
                if(!state.getValue(CityHatchBlock.OPEN))continue;
                var outside=port.relative(state.getValue(CityHatchBlock.FACING),3).below();
                var children=f.level.getEntitiesOfClass(Mob.class,new AABB(outside).inflate(2),
                        mob->mob.isAlive()&&mob.getTags().contains("BlacklightCityPopulation")&&!f.originalMobs.contains(mob.getUUID()));
                h.assertTrue(children.size()==1,"expected one new city mob at the emitting hatch "+port);
                f.child=children.get(0);
            }
            h.assertTrue(f.child!=null,"paid birth did not open its hatch with a live child outside");
            for(int i=0;i<6;i++)f.level.destroyBlock(CitySystem.port(f.level,0,i),false);
        }).thenWaitUntil(()->{
            h.assertTrue(f.data.brokenPorts[0]==63,"waiting for six broken ports to be recorded");
        }).thenExecute(()->{
            h.assertTrue(!f.data.destroyed(0),"hive died before the seventh port");
            f.level.destroyBlock(CitySystem.port(f.level,0,6),false);
            for(int i=0;i<7;i++)f.level.destroyBlock(CitySystem.port(f.level,1,i),false);
            for(int hive=0;hive<2;hive++){
                f.data.hiveSupply[hive].receive(128);
                f.data.hiveBroodTicks[hive]=CityConfig.HIVE_BROOD_INTERVAL.get();
            }
            f.infectedAtDestruction=f.data.infectedMask;
            f.data.spread=true;
            f.data.nextSpread=0;
        }).thenWaitUntil(()->{
            h.assertTrue(f.data.nextSpread>f.level.getGameTime(),"waiting for the real spread scheduler after destruction");
        }).thenExecute(()->{
            h.assertTrue(f.data.destroyedMask==3&&f.data.brokenPorts[0]==127&&f.data.brokenPorts[1]==127,
                    "both hives must deactivate when their seventh port breaks");
            h.assertTrue(f.child.isAlive()&&f.level.getEntity(f.child.getUUID())==f.child,"destroying its hive removed the existing child");
        }).thenIdle(40).thenExecute(()->{
            h.assertTrue(f.data.infectedMask==f.infectedAtDestruction,"destroyed hives continued spreading");
            h.assertTrue(f.data.hiveBirths[0]==1&&f.data.hiveBirths[1]==0,"destroyed hives produced another brood");
        }).thenSucceed();
    }

    private static final class Fixture {
        final ServerLevel level;
        final FakePlayer observer;
        final Map<BlockPos,BlockState> ports=new LinkedHashMap<>();
        final Set<ChunkPos> forced=new LinkedHashSet<>();
        final Set<UUID> originalMobs=new HashSet<>();
        final CityData oldCity,data=new CityData();
        final OutbreakData oldOutbreak;
        final CityEcologyData oldEcology;
        final MilitaryCampaignData oldCampaign;
        final Difficulty difficulty;
        final boolean spawnRule,griefRule,outbreakEnabled,spawnsEnabled,spreadEnabled;
        final double broodChance;
        long infectedAtDestruction;
        Mob child;

        Fixture(GameTestHelper h){
            level=h.getLevel().getServer().getLevel(CityTravel.WORLD);
            var server=level.getServer();
            oldCity=CityData.get(level);
            oldOutbreak=OutbreakData.get(server);
            oldEcology=CityEcologyData.get(level);
            oldCampaign=MilitaryCampaignData.get(level);
            difficulty=level.getDifficulty();
            spawnRule=level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
            griefRule=level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
            outbreakEnabled=OutbreakConfig.ENABLED.get();
            spawnsEnabled=CityConfig.SPAWNS.get();
            spreadEnabled=CityConfig.SPREAD.get();
            broodChance=GameplayBalanceConfig.BROOD_CHANCE.get();
            observer=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"CityHiveTest"));

            h.testInfo.addListener(new GameTestListener(){
                public void testStructureLoaded(GameTestInfo info){}
                public void testPassed(GameTestInfo info,GameTestRunner runner){close();}
                public void testFailed(GameTestInfo info,GameTestRunner runner){close();}
                public void testAddedForRerun(GameTestInfo original,GameTestInfo retry,GameTestRunner runner){}
            });
            var active=new OutbreakData();
            active.start(server.overworld().getGameTime());
            server.overworld().getDataStorage().set("blacklight_outbreak",active);
            data.facade=false;
            data.spread=false;
            data.populatedMask=-1;
            level.getDataStorage().set("blacklight_city",data);
            level.getDataStorage().set("blacklight_city_ecology",new CityEcologyData());
            level.getDataStorage().set("blacklight_military_campaign",new MilitaryCampaignData());
            OutbreakConfig.ENABLED.set(true);
            CityConfig.SPAWNS.set(true);
            CityConfig.SPREAD.set(true);
            GameplayBalanceConfig.BROOD_CHANCE.set(0d);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,server);
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,server);
            server.setDifficulty(Difficulty.NORMAL,true);
            for(int hive=0;hive<2;hive++)for(int i=0;i<7;i++){
                var p=CitySystem.port(level,hive,i);
                var cp=new ChunkPos(p);
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                    var neighbor=new ChunkPos(cp.x+dx,cp.z+dz);
                    if(level.setChunkForced(neighbor.x,neighbor.z,true))forced.add(neighbor);
                }
                ports.put(p,level.getBlockState(p));
                level.setBlock(p,FutianLayout.state(level.getSeed(),p.getX(),p.getY(),p.getZ()),3);
            }
            var port=CitySystem.port(level,0,0);
            // Nearby creative players count for ordinary ambient retention without provoking the brood.
            observer.setGameMode(GameType.CREATIVE);
            observer.setPos(port.getX()+.5,70,port.getZ()+.5);
            level.addNewPlayer(observer);
        }
        void close(){
            var server=level.getServer();
            if(child!=null)child.discard();
            level.removePlayerImmediately(observer,Entity.RemovalReason.DISCARDED);
            ports.forEach((p,state)->level.setBlock(p,state,3));
            for(var cp:forced)level.setChunkForced(cp.x,cp.z,false);
            level.getDataStorage().set("blacklight_city",oldCity);
            level.getDataStorage().set("blacklight_city_ecology",oldEcology);
            level.getDataStorage().set("blacklight_military_campaign",oldCampaign);
            server.overworld().getDataStorage().set("blacklight_outbreak",oldOutbreak);
            GameplayBalanceConfig.BROOD_CHANCE.set(broodChance);
            OutbreakConfig.ENABLED.set(outbreakEnabled);
            CityConfig.SPAWNS.set(spawnsEnabled);
            CityConfig.SPREAD.set(spreadEnabled);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawnRule,server);
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefRule,server);
            server.setDifficulty(difficulty,true);
        }
    }
}
