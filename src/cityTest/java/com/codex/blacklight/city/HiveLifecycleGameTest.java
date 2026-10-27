package com.codex.blacklight.city;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public class HiveLifecycleGameTest {
    @GameTest(template="wall_test",timeoutTicks=500,batch="city_hive_mutation")
    public static void sevenPortsStopProductionAndSpread(GameTestHelper h){
        var key=CityTravel.WORLD;
        var level=h.getLevel().getServer().getLevel(key);
        var storage=level.getServer().overworld().getDataStorage();
        var original=com.codex.blacklight.infection.OutbreakData.get(level.getServer());
        var outbreak=new com.codex.blacklight.infection.OutbreakData();outbreak.start(level.getServer().overworld().getGameTime());storage.set("blacklight_outbreak",outbreak);
        var spawnRule=level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING);
        boolean oldSpawning=spawnRule.get(),oldPopulation=CityConfig.SPAWNS.get();var oldDifficulty=level.getServer().getWorldData().getDifficulty();
        spawnRule.set(true,level.getServer());CityConfig.SPAWNS.set(true);level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        var oldCity=CityData.get(level);var data=new CityData();level.getDataStorage().set("blacklight_city",data);data.population=true;data.facade=false;data.spread=false;data.populatedMask=-1;
        data.destroyedMask=0;java.util.Arrays.fill(data.brokenPorts,0);
        var forced=new java.util.HashSet<net.minecraft.world.level.ChunkPos>();
        for(int hive=0;hive<2;hive++)for(int i=0;i<7;i++){
            var p=CitySystem.port(level,hive,i);var cp=new net.minecraft.world.level.ChunkPos(p);
            level.getChunkAt(p);level.setBlock(p,FutianLayout.state(level.getSeed(),p.getX(),p.getY(),p.getZ()),3);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)forced.add(new net.minecraft.world.level.ChunkPos(cp.x+dx,cp.z+dz));
        }
        for(var cp:forced)level.setChunkForced(cp.x,cp.z,true);
        var player=FakePlayerFactory.get(level,new GameProfile(java.util.UUID.fromString("cc9c8931-40de-4b8d-a393-260990a3afef"),"CityHiveTest"));
        var port=CitySystem.port(level,0,0);player.setPos(port.getX()+.5,65,port.getZ()+.5);level.addNewPlayer(player);
        h.runAfterDelay(100,()->{
            try {
            long before=mobs(level);
            // This lifecycle fixture isolates destruction with finite, already digested food.
            // Survival-slot consumption and real digestion are checked by CityHiveSupplyGameTest.
            data.hiveSupply[0].receive(64);data.hiveBroodTicks[0]=200;
            CitySystem.updateLoadedCity(level,200);
            h.assertTrue(mobs(level)>before,"live hive did not produce a mob in loaded chunks after outbreak release with spawning enabled");
            for(int i=0;i<6;i++)level.destroyBlock(CitySystem.port(level,0,i),false);
            CitySystem.updateLoadedCity(level,220);
            h.assertTrue(!data.destroyed(0),"hive died before seventh port");
            level.destroyBlock(CitySystem.port(level,0,6),false);
            CitySystem.updateLoadedCity(level,240);
            h.assertTrue(data.destroyed(0)&&data.brokenPorts[0]==127,"seventh port did not deactivate hive");
            long alive=mobs(level);
            for(int i=0;i<7;i++)level.destroyBlock(CitySystem.port(level,1,i),false);
            data.spread=true;data.nextSpread=0;long infection=data.infectedMask;
            CitySystem.updateLoadedCity(level,400);CitySystem.updateLoadedCity(level,600);
            h.assertTrue(data.destroyedMask==3&&data.infectedMask==infection,"destroyed hives continued expanding");
            h.assertTrue(mobs(level)==alive,"destroyed hives produced mobs or removed existing survivors");
            } finally {
            level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);
            for(var cp:forced)level.setChunkForced(cp.x,cp.z,false);
            // Restore the generated ports so a repeated suite does not inherit our destruction.
            for(int hive=0;hive<2;hive++)for(int i=0;i<7;i++){
                var pos=CitySystem.port(level,hive,i);
                level.setBlock(pos,FutianLayout.state(level.getSeed(),pos.getX(),pos.getY(),pos.getZ()),3);
            }
            data.destroyedMask=0;java.util.Arrays.fill(data.brokenPorts,0);data.population=false;data.spread=false;data.setDirty();
            level.getDataStorage().set("blacklight_city",oldCity);
            storage.set("blacklight_outbreak",original);
            spawnRule.set(oldSpawning,level.getServer());CityConfig.SPAWNS.set(oldPopulation);level.getServer().setDifficulty(oldDifficulty,true);
            }
            h.succeed();
        });
    }
    private static long mobs(net.minecraft.server.level.ServerLevel level){
        long count=0;for(var e:level.getAllEntities())if(e instanceof Mob&&e.isAlive())count++;return count;
    }
}
