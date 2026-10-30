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
import net.minecraftforge.gametest.*;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.common.MinecraftForge;

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
        h.assertTrue(CitySystem.populateDistrict(f.level,f.center,CityLayout.Zone.NORMAL)==2&&f.count()==before+2,"two real remaining slots must fill without requiring a whole four-body wave");
        h.assertTrue(CitySystem.populateDistrict(f.level,f.center,CityLayout.Zone.NORMAL)==0,"a second call cannot exceed the actual city cap");
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
        h.assertTrue(CitySystem.populateDistrict(f.level,f.center,CityLayout.Zone.NORMAL)==0&&!CitySystem.spawn(f.level,EntityType.VILLAGER,f.center,false)&&f.count()==before,"doMobSpawning=false must stop actual publication at both scheduled entry points");
    });}
    @GameTest(template="wall_test",timeoutTicks=400,batch="spawn_join_veto")
    public static void aRealJoinVetoCannotTurnARejectedCityCohortIntoASuccess(GameTestHelper h){var f=new Fixture(h);f.run(()->{
        int before=f.count();Consumer<EntityJoinLevelEvent> veto=e->{if(e.getLevel()==f.level&&e.getEntity() instanceof net.minecraft.world.entity.npc.Villager)e.setCanceled(true);};MinecraftForge.EVENT_BUS.addListener(veto);
        try{h.assertTrue(CitySystem.populateDistrict(f.level,f.center,CityLayout.Zone.NORMAL)==0&&f.count()==before,"actual third-party join cancellation produces no bodies and no successful cohort");}finally{MinecraftForge.EVENT_BUS.unregister(veto);}
    });}
    @GameTest(template="wall_test",timeoutTicks=400,batch="spawn_unloaded")
    public static void citySpawnCannotLoadRemoteChunksToMakeAnOutlet(GameTestHelper h){var f=new Fixture(h);f.run(()->{
        var remote=f.center.offset(2000000,0,2000000);int chunks=f.level.getChunkSource().getLoadedChunksCount();
        h.assertTrue(!CitySystem.spawn(f.level,EntityType.VILLAGER,remote,false)&&f.level.getChunkSource().getLoadedChunksCount()==chunks,"a rejected distant birth does not load terrain");
    });}
    @GameTest(template="wall_test",timeoutTicks=400,batch="spawn_finite_hive_rule")
    public static void disablingMobSpawningAlsoPausesFiniteHiveReinforcements(GameTestHelper h){var f=new Fixture(h);f.run(()->{
        var hive=com.codex.blacklight.registry.ModEntities.BLACKLIGHT_HIVE.get().create(f.level);hive.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(f.center));hive.setNoAi(true);hive.tickCount=1;
        for(int x=-13;x<=13;x++)for(int z=-13;z<=13;z++){f.set(f.center.offset(x,-1,z),Blocks.STONE.defaultBlockState());for(int y=0;y<5;y++)f.set(f.center.offset(x,y,z),Blocks.AIR.defaultBlockState());}
        try{
            var tag=new net.minecraft.nbt.CompoundTag();hive.saveWithoutId(tag);tag.putInt("BlacklightWaveCooldown",0);hive.readAdditionalSaveData(tag);f.level.addFreshEntity(hive);
            var field=hive.getClass().getDeclaredField("boss");field.setAccessible(true);var boss=(net.minecraft.server.level.ServerBossEvent)field.get(hive);
            boss.addPlayer(net.minecraftforge.common.util.FakePlayerFactory.get(f.level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BroodRuleQA")));
            var difficulty=f.level.getDifficulty();f.level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
            try{int before=f.count();f.level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,f.level.getServer());hive.tick();
                h.assertTrue(f.count()==before,"a live encounter with viewers cannot bypass disabled mob spawning");
                f.level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,f.level.getServer());hive.tick();
                h.assertTrue(f.count()>before,"reenabling the rule permits the same funded finite reinforcement wave");
            }finally{f.level.getServer().setDifficulty(difficulty,true);}
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    });}

}
