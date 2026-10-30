package com.codex.blacklight.city;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.common.NeoForge;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class CitySpawnAuditGameTest {
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final ServerLevel level;final BlockPos center;
        final boolean spawning;final int limit;final Map<BlockPos,BlockState> terrain=new LinkedHashMap<>();
        final Set<ChunkPos> forced=new LinkedHashSet<>();final Set<Integer> original=new HashSet<>();
        Fixture(GameTestHelper helper){
            h=helper;level=h.getLevel();center=h.absolutePos(new BlockPos(8,70,8));
            spawning=level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);limit=CityConfig.MOB_LIMIT.get();
            for(var e:level.getAllEntities())original.add(e.getId());
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
                var cp=new ChunkPos((center.getX()>>4)+x,(center.getZ()>>4)+z);
                if(!level.getForcedChunks().contains(cp.toLong())){forced.add(cp);level.setChunkForced(cp.x,cp.z,true);}
                level.getChunk(cp.x,cp.z);
            }
            for(int x=-9;x<=9;x++)for(int z=-4;z<=4;z++)for(int y=-1;y<9;y++)
                set(center.offset(x,y,z),y==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,level.getServer());CityConfig.MOB_LIMIT.set(200);
        }
        int count(){int n=0;for(var e:level.getAllEntities())if(e instanceof Mob&&e.isAlive())n++;return n;}
        void set(BlockPos p,BlockState state){terrain.putIfAbsent(p.immutable(),level.getBlockState(p));level.setBlock(p,state,3);}
        void run(Runnable check){h.startSequence().thenWaitUntil(()->h.assertTrue(level.isPositionEntityTicking(center),"fixture chunks must really be entity ticking")).thenIdle(61).thenExecute(()->{try{check.run();}finally{close();}h.succeed();});}
        public void close(){
            var cleanup=new java.util.ArrayList<Entity>();level.getAllEntities().forEach(cleanup::add);for(var e:cleanup)if(e instanceof Mob&&!original.contains(e.getId()))e.discard();
            for(var e:terrain.entrySet())level.setBlock(e.getKey(),e.getValue(),3);
            for(var cp:forced)level.setChunkForced(cp.x,cp.z,false);
            CityConfig.MOB_LIMIT.set(limit);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawning,level.getServer());
        }
    }
    @GameTest(template="wall_test",timeoutTicks=400,batch="spawn_last_slots")
    public static void cityCohortUsesTheLastTwoRealPopulationSlots(GameTestHelper h){var f=new Fixture(h);f.run(()->{
        int before=f.count();CityConfig.MOB_LIMIT.set(before+2);
        h.assertTrue(CitySystem.populateDistrict(f.level,f.center,DistrictThreat.Zone.GREEN)==2&&f.count()==before+2,"two real remaining slots must fill without requiring a whole four-body wave");
        h.assertTrue(CitySystem.populateDistrict(f.level,f.center,DistrictThreat.Zone.GREEN)==0,"a second call cannot exceed the actual city cap");
    });}
    @GameTest(template="wall_test",timeoutTicks=400,batch="spawn_head_water")
    public static void cityBirthRejectsHeadDeepWaterEvenWhenItsFeetAreDry(GameTestHelper h){var f=new Fixture(h);f.run(()->{
        f.set(f.center.above(),Blocks.WATER.defaultBlockState());int before=f.count();
        h.assertTrue(f.level.getFluidState(f.center).isEmpty(),"the regression case has dry feet and submerged head");
        h.assertTrue(!CitySystem.spawn(f.level,EntityType.VILLAGER,f.center,false)&&f.count()==before,"whole-body liquid prevents publishing a drowning city resident");
        f.set(f.center.above(),Blocks.AIR.defaultBlockState());
        h.assertTrue(CitySystem.spawn(f.level,EntityType.VILLAGER,f.center,false)&&f.count()==before+1,"the same supported dry space remains a valid real birth");
    });}
    @GameTest(template="wall_test",timeoutTicks=400,batch="spawn_disabled")
    public static void disablingMobSpawningStopsBothCityGroupsAndIndividualBirths(GameTestHelper h){var f=new Fixture(h);f.run(()->{
        f.level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,f.level.getServer());int before=f.count();
        h.assertTrue(CitySystem.populateDistrict(f.level,f.center,DistrictThreat.Zone.GREEN)==0&&!CitySystem.spawn(f.level,EntityType.VILLAGER,f.center,false)&&f.count()==before,"doMobSpawning=false must stop actual publication at both scheduled entry points");
    });}
    @GameTest(template="wall_test",timeoutTicks=400,batch="spawn_join_veto")
    public static void aRealJoinVetoCannotTurnARejectedCityCohortIntoASuccess(GameTestHelper h){var f=new Fixture(h);f.run(()->{
        int before=f.count();Consumer<EntityJoinLevelEvent> veto=e->{if(e.getLevel()==f.level&&e.getEntity() instanceof net.minecraft.world.entity.npc.Villager)e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(veto);
        try{h.assertTrue(CitySystem.populateDistrict(f.level,f.center,DistrictThreat.Zone.GREEN)==0&&f.count()==before,"actual third-party join cancellation produces no bodies and no successful cohort");}finally{NeoForge.EVENT_BUS.unregister(veto);}
    });}
    @GameTest(template="wall_test",timeoutTicks=400,batch="spawn_unloaded")
    public static void citySpawnCannotLoadRemoteChunksToMakeAnOutlet(GameTestHelper h){var f=new Fixture(h);f.run(()->{
        var remote=f.center.offset(2000000,0,2000000);int chunks=f.level.getChunkSource().getLoadedChunksCount();
        h.assertTrue(!CitySystem.spawn(f.level,EntityType.VILLAGER,remote,false)&&f.level.getChunkSource().getLoadedChunksCount()==chunks,"a rejected distant birth does not load terrain");
    });}
    private static void redSetup(Fixture f,Runnable check){
        var server=f.level.getServer();var old=com.codex.blacklight.infection.OutbreakData.get(server);var active=new com.codex.blacklight.infection.OutbreakData();active.start(server.overworld().getGameTime());server.overworld().getDataStorage().set("blacklight_outbreak",active);
        var c=com.codex.blacklight.config.GameplayBalanceConfig.TIER_WEIGHTS;int[][] weights=new int[5][5];boolean guarantee=com.codex.blacklight.config.GameplayBalanceConfig.LINEAGE_GUARANTEES.get(),enabled=com.codex.blacklight.infection.OutbreakConfig.ENABLED.get();int green=com.codex.blacklight.config.GameplayBalanceConfig.GREEN_CHANCE.get();int batch=com.codex.blacklight.infection.OutbreakConfig.BATCH.get();var difficulty=f.level.getDifficulty();
        try{
            com.codex.blacklight.infection.OutbreakConfig.ENABLED.set(true);com.codex.blacklight.config.GameplayBalanceConfig.LINEAGE_GUARANTEES.set(false);com.codex.blacklight.config.GameplayBalanceConfig.GREEN_CHANCE.set(100);server.setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
            for(int s=0;s<5;s++)for(int t=0;t<5;t++){weights[s][t]=c[s][t].get();c[s][t].set(t==1?100:0);}check.run();
        }finally{
            for(int s=0;s<5;s++)for(int t=0;t<5;t++)c[s][t].set(weights[s][t]);com.codex.blacklight.config.GameplayBalanceConfig.GREEN_CHANCE.set(green);com.codex.blacklight.config.GameplayBalanceConfig.LINEAGE_GUARANTEES.set(guarantee);com.codex.blacklight.infection.OutbreakConfig.ENABLED.set(enabled);com.codex.blacklight.infection.OutbreakConfig.BATCH.set(batch);server.setDifficulty(difficulty,true);server.overworld().getDataStorage().set("blacklight_outbreak",old);
        }
    }
    @GameTest(template="wall_test",timeoutTicks=400,batch="spawn_red_lineage")
    public static void redInitialCityCohortsUseTheConfiguredDevelopingLineage(GameTestHelper h){var f=new Fixture(h);f.run(()->redSetup(f,()->{
        com.codex.blacklight.infection.OutbreakConfig.BATCH.set(4);int born=CitySystem.populateDistrict(f.level,f.center,DistrictThreat.Zone.RED);int hosts=0,common=0;
        for(var e:f.level.getAllEntities())if(e instanceof Mob&&!f.original.contains(e.getId())){if(e instanceof com.codex.blacklight.entity.BlacklightInfectedHost)hosts++;if(e instanceof com.codex.blacklight.entity.BlacklightCommonInfected)common++;}
        h.assertTrue(born>0&&hosts==born&&common==0,"initial red-city population must honor real developing-species weights instead of hard-coding ordinary infected; born="+born+" hosts="+hosts+" common="+common);
    }));}
    @GameTest(template="wall_test",timeoutTicks=400,batch="spawn_red_budget")
    public static void initialRedCohortsCannotSpendAnotherProducersReservedBirthTicket(GameTestHelper h){var f=new Fixture(h);f.run(()->redSetup(f,()->{
        com.codex.blacklight.infection.OutbreakConfig.BATCH.set(1);
        h.assertTrue(CitySystem.populateDistrict(f.level,f.center,DistrictThreat.Zone.RED)==1,"the four-member cohort must still use the actual one-birth shared budget");
        h.assertTrue(com.codex.blacklight.infection.WorldOutbreakSystem.spawnAt(f.level,f.center.east(7),com.codex.blacklight.infection.OutbreakRules.Tier.FRESH)==null,"another producer cannot exceed the already-spent real scheduler window");
    }));}

    @GameTest(template="wall_test",timeoutTicks=400,batch="spawn_structure_rule")
    public static void freshStructureGuardsHonorSpawningRulesWithoutDeletingSavedBodies(GameTestHelper h){var f=new Fixture(h);f.run(()->redSetup(f,()->{
        var host=com.codex.blacklight.registry.ModEntities.INFECTED_HOST.get().create(f.level);host.setPos(f.center.getBottomCenter());host.inherit(EntityType.ZOMBIE.create(f.level));host.setNoAi(true);host.getPersistentData().putBoolean("BlacklightColossalHive",true);
        f.level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,f.level.getServer());
        h.assertTrue(!f.level.addFreshEntity(host),"the actual structure join listener rejects fresh guards when natural spawning is disabled");
        var saved=new EntityJoinLevelEvent(host,f.level,true);com.codex.blacklight.infection.WorldOutbreakSystem.naturalGuard(saved);
        h.assertTrue(!saved.isCanceled(),"the spawning rule must not delete existing guards reloaded from disk");
        f.level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,f.level.getServer());
        h.assertTrue(f.level.addFreshEntity(host),"reenabling spawning admits the same early-stage guard after release");
        host.discard();f.level.getServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true);
        var peaceful=com.codex.blacklight.registry.ModEntities.INFECTED_HOST.get().create(f.level);peaceful.setPos(f.center.getBottomCenter());peaceful.inherit(EntityType.ZOMBIE.create(f.level));peaceful.getPersistentData().putBoolean("BlacklightColossalHive",true);
        h.assertTrue(!f.level.addFreshEntity(peaceful),"peaceful difficulty also rejects fresh structural guards at the real publication event");
    }));}
    private static <T> void setting(Map<net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?>,Object> old,net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<T> key,T value){old.putIfAbsent(key,key.get());key.set(value);}
    @GameTest(template="wall_test",timeoutTicks=500,batch="spawn_actual_ambient_loop")
    public static void actualServerTicksReplenishInfectedOnlyAfterMobSpawningIsEnabled(GameTestHelper h){ambientLoop(h,1,8);}
    @GameTest(template="wall_test",timeoutTicks=500,batch="spawn_actual_dense_loop")
    public static void denseAmbientBirthsShareTheActualWindowBudget(GameTestHelper h){ambientLoop(h,3,2);}
    private static void ambientLoop(GameTestHelper h,int perPlayer,int batch){
        var f=new Fixture(h);var level=f.level;var server=level.getServer();var old=com.codex.blacklight.infection.OutbreakData.get(server);var ecology=CityEcologyData.get(level);var difficulty=level.getDifficulty();
        var active=new com.codex.blacklight.infection.OutbreakData();active.start(server.overworld().getGameTime());server.overworld().getDataStorage().set("blacklight_outbreak",active);level.getDataStorage().set("blacklight_city_ecology",new CityEcologyData());
        var values=new LinkedHashMap<net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?>,Object>();
        setting(values,com.codex.blacklight.infection.OutbreakConfig.ENABLED,true);setting(values,com.codex.blacklight.infection.OutbreakConfig.INTERVAL,20);setting(values,com.codex.blacklight.infection.OutbreakConfig.BATCH,batch);setting(values,com.codex.blacklight.config.GameplayBalanceConfig.GREEN_BIRTHS,perPlayer);
        setting(values,com.codex.blacklight.config.GameplayBalanceConfig.SPAWN_MIN,12);setting(values,com.codex.blacklight.config.GameplayBalanceConfig.SPAWN_MAX,12);setting(values,com.codex.blacklight.config.GameplayBalanceConfig.SPAWN_SAFETY,4);
        setting(values,com.codex.blacklight.config.GameplayBalanceConfig.GREEN_CHANCE,100);setting(values,com.codex.blacklight.config.GameplayBalanceConfig.SEED_CHANCE,0D);setting(values,com.codex.blacklight.config.GameplayBalanceConfig.EXPOSURE_BASE,0D);setting(values,com.codex.blacklight.config.GameplayBalanceConfig.EXPOSURE_PER_STAGE,0D);setting(values,com.codex.blacklight.config.GameplayBalanceConfig.LINEAGE_GUARANTEES,false);
        for(int stage=0;stage<5;stage++)for(int tier=0;tier<5;tier++)setting(values,com.codex.blacklight.config.GameplayBalanceConfig.TIER_WEIGHTS[stage][tier],tier==0?100:0);
        server.setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
            var cp=new ChunkPos((f.center.getX()>>4)+x,(f.center.getZ()>>4)+z);if(!level.getForcedChunks().contains(cp.toLong())){f.forced.add(cp);level.setChunkForced(cp.x,cp.z,true);}level.getChunk(cp.x,cp.z);
        }
        for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++)for(int y=-1;y<5;y++)f.set(f.center.offset(x,y,z),y==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        var bornWindows=new java.util.HashMap<Long,Integer>();java.util.function.Consumer<EntityJoinLevelEvent> countBirths=e->{if(e.getLevel()==level&&e.getEntity() instanceof com.codex.blacklight.entity.BlacklightCommonInfected)bornWindows.merge(Math.floorDiv(level.getGameTime(),20),1,Integer::sum);};NeoForge.EVENT_BUS.addListener(countBirths);
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"AmbientSpawnQA"));player.setGameMode(GameType.SURVIVAL);player.setPos(f.center.getBottomCenter());player.setNoGravity(true);level.addNewPlayer(player);
        Runnable close=()->{NeoForge.EVENT_BUS.unregister(countBirths);level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);f.close();for(var e:values.entrySet())((net.neoforged.neoforge.common.ModConfigSpec.ConfigValue)e.getKey()).set(e.getValue());server.setDifficulty(difficulty,true);server.overworld().getDataStorage().set("blacklight_outbreak",old);level.getDataStorage().set("blacklight_city_ecology",ecology);};
        java.util.function.LongSupplier infected=()->level.getEntitiesOfClass(Mob.class,new net.minecraft.world.phys.AABB(f.center).inflate(64),m->m.isAlive()&&!f.original.contains(m.getId())&&com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(m)).size();
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.isPositionEntityTicking(f.center),"actual player neighborhood must simulate"))
            .thenIdle(61).thenExecute(()->{try{h.assertTrue(infected.getAsLong()==0,"real scheduled ticks respect the disabled spawning rule");level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,server);}catch(Throwable t){close.run();throw t;}})
            .thenIdle(61).thenExecute(()->{try{h.assertTrue(bornWindows.values().stream().allMatch(n->n<=Math.min(perPlayer,batch))&&bornWindows.values().stream().anyMatch(n->n==Math.min(perPlayer,batch)),"real event windows enforce per-player and shared budgets: "+bornWindows);h.assertTrue(infected.getAsLong()>0,"the real server event loop must publish infected near a living survival player without calling the spawn helper; actual="+infected.getAsLong());}finally{close.run();}h.succeed();});
    }
}
