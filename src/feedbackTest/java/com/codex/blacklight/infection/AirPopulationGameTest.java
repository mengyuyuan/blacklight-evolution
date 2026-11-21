package com.codex.blacklight.infection;

import com.codex.blacklight.city.CityEcology;
import com.codex.blacklight.city.CityEcologyData;
import com.codex.blacklight.config.GameplayBalanceConfig;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.ModEntities;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class AirPopulationGameTest {
    private static final class World implements AutoCloseable {
        final ServerLevel level;
        final BlockPos center;
        final ArrayList<ChunkPos> forced=new ArrayList<>();
        final ArrayList<Entity> bodies=new ArrayList<>();
        final Map<BlockPos,net.minecraft.world.level.block.state.BlockState> blocks=new HashMap<>();
        final OutbreakData old;
        final CityEcologyData city;
        final boolean spawning;
        final Difficulty difficulty;

        World(GameTestHelper h,int radius,int height) {
            level=h.getLevel();
            var chunk=new ChunkPos(h.absolutePos(new BlockPos(4,1,4)));
            center=new BlockPos(chunk.getMiddleBlockX(),height,chunk.getMiddleBlockZ());
            for(int x=chunk.x-radius;x<=chunk.x+radius;x++)for(int z=chunk.z-radius;z<=chunk.z+radius;z++){
                var pos=new ChunkPos(x,z);
                if(level.setChunkForced(x,z,true))forced.add(pos);
                level.getChunk(x,z);
            }
            old=OutbreakData.get(level.getServer());
            city=CityEcologyData.get(level);
            difficulty=level.getDifficulty();
            spawning=level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
            level.getServer().setDifficulty(Difficulty.NORMAL,true);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,level.getServer());
            var isolated=new OutbreakData();isolated.start(0);isolated.markSeeded(level.dimension().location().toString());
            level.getServer().overworld().getDataStorage().set("blacklight_outbreak",isolated);
            level.getDataStorage().set("blacklight_city_ecology",new CityEcologyData());
        }
        void block(BlockPos pos,net.minecraft.world.level.block.Block block){
            blocks.putIfAbsent(pos,level.getBlockState(pos));
            level.setBlock(pos,block.defaultBlockState(),2);
        }
        public void close(){
            blocks.forEach((pos,state)->level.setBlock(pos,state,2));
            for(var body:bodies) {
                if(body instanceof net.minecraft.server.level.ServerPlayer p)level.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);
                else body.discard();
            }
            level.getServer().overworld().getDataStorage().set("blacklight_outbreak",old);
            level.getDataStorage().set("blacklight_city_ecology",city);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawning,level.getServer());
            level.getServer().setDifficulty(difficulty,true);
            for(var chunk:forced)level.setChunkForced(chunk.x,chunk.z,false);
        }
    }

    @GameTest(template="wall_test",batch="air_population_outbreak",timeoutTicks=140)
    public static void flyersAreRealAirborneBirthsWithRegionalLimits(GameTestHelper h){
        var w=new World(h,2,295);
        h.runAfterDelay(25,()->{
            try(w){
                h.assertTrue(!AirPopulation.wantsFlyer(0,0) && AirPopulation.wantsFlyer(1,9)
                        && !AirPopulation.wantsFlyer(1,10) && AirPopulation.wantsFlyer(2,19)
                        && AirPopulation.wantsFlyer(3,29),"later infection stages admit more airborne births");
                w.block(w.center.above(12),Blocks.STONE);
                h.assertTrue(!AirPopulation.spawnFlyer(w.level,w.center),"an enclosed column cannot produce a flyer");
                w.block(w.center.above(12),Blocks.AIR);
                w.block(w.center.above(6),Blocks.OAK_LEAVES);
                for(var pos:List.of(w.center,w.center.east(22))){
                    if(!AirPopulation.spawnFlyer(w.level,pos))h.assertTrue(false,"scheduled flyer "+flightConditions(w,pos));
                    var at=w.level.getEntitiesOfClass(EncounterMob.class,new AABB(pos).inflate(4,20,4),m->m.kind()==EncounterKind.FLYER);
                    w.bodies.addAll(at);
                }
                var flyers=w.level.getEntitiesOfClass(EncounterMob.class,new AABB(w.center).inflate(28),m->m.kind()==EncounterKind.FLYER);
                h.assertTrue(flyers.size()==2 && flyers.stream().allMatch(m->m.getY()>=w.center.getY()+5 && m.isNoGravity()
                        && m.getPersistentData().getBoolean("blacklight:outbreak_spawn")),"both are live ambient flyers above ground");
                h.assertTrue(flyers.stream().anyMatch(m->m.getBlockX()==w.center.getX() && m.getY()>w.center.getY()+6),"the woodland flyer clears the canopy");
                h.assertTrue(!AirPopulation.spawnFlyer(w.level,w.center.west(22)),"the area's third flyer is refused");
            }
            h.succeed();
        });
    }

    private static String flightConditions(World w,BlockPos pos){
        var probe=com.codex.blacklight.registry.EncounterEntities.CREATURES.get(EncounterKind.FLYER).get().create(w.level);
        return "at "+pos+" budget="+WorldOutbreakSystem.populationRoom(w.level,pos.above(7))
                +" existing="+w.level.getEntitiesOfClass(EncounterMob.class,new AABB(pos).inflate(80),m->m.isAlive()&&m.kind()==EncounterKind.FLYER).size()
                +" sky="+AirPopulation.openSky(w.level,pos)+" air="+AirPopulation.placeInOpenAir(w.level,probe,pos.above(7))
                +" ticking="+w.level.isPositionEntityTicking(pos)+" protected="+!com.codex.blacklight.compat.NaturalInfectionProtection.allowed(w.level,pos);
    }

    @GameTest(template="wall_test",batch="air_population_patrol",timeoutTicks=140)
    public static void aYellowDistrictProducesOneUnownedReconPatrol(GameTestHelper h){
        var w=new World(h,4,90);
        h.runAfterDelay(30,()->{
            try(w){
                for(int x=-64;x<=64;x+=8)for(int z=-64;z<=64;z+=8)CityEcology.release(w.level,w.center.offset(x,0,z));
                var heights=new ArrayList<Integer>();
                for(int dx=-40;dx<=40;dx+=10)for(int dz=-40;dz<=40;dz+=10)
                    heights.add(w.level.getHeight(Heightmap.Types.MOTION_BLOCKING,w.center.getX()+dx,w.center.getZ()+dz));
                Collections.sort(heights);
                var surface=new BlockPos(w.center.getX(),heights.get(heights.size()/2),w.center.getZ());
                var observers=List.of(Vec3.atCenterOf(surface));
                AirPopulation.patrol(w.level,observers);
                AirPopulation.patrol(w.level,observers);
                var drones=w.level.getEntitiesOfClass(MilitaryUav.class,new AABB(surface).inflate(90));
                w.bodies.addAll(drones);
                h.assertTrue(drones.size()==1 && drones.get(0).owner()==null && w.level.canSeeSky(drones.get(0).blockPosition()),
                        "a repeat patrol should leave one unowned drone under open sky: count="+drones.size()+" surface="+surface);
            }
            h.succeed();
        });
    }

    @GameTest(template="wall_test",batch="air_population_hatch",timeoutTicks=160)
    public static void aSavedFlyerSacHatchesThenAdaptsToTheDistrict(GameTestHelper h){
        var w=new World(h,1,165);
        var viewer=FakePlayerFactory.get(w.level,new GameProfile(UUID.randomUUID(),"brood-air-qa"));
        viewer.setInvulnerable(true);viewer.setNoGravity(true);
        viewer.setPos(Vec3.atCenterOf(w.center.north(10)));w.level.addNewPlayer(viewer);w.bodies.add(viewer);
        var hive=ModEntities.BLACKLIGHT_HIVE.get().create(w.level);
        w.block(w.center.below(),Blocks.STONE);
        hive.setPos(Vec3.atBottomCenterOf(w.center));
        var state=new CompoundTag();hive.addAdditionalSaveData(state);
        state.putString("BlacklightBrood","flyer");state.remove("BlacklightBroodStage");
        state.putFloat("BlacklightGrowth",1);state.putInt("BlacklightWaveCooldown",0);
        hive.readAdditionalSaveData(state);w.level.addFreshEntity(hive);w.bodies.add(hive);
        h.runAfterDelay(55,()->{
            try{
                var young=w.level.getEntitiesOfClass(EncounterMob.class,new AABB(w.center).inflate(48),
                        m->m.kind()==EncounterKind.FLYER && m.getPersistentData().hasUUID("BlacklightHiveGuard")
                                && hive.getUUID().equals(m.getPersistentData().getUUID("BlacklightHiveGuard")));
                w.bodies.addAll(young);
                h.assertTrue(hive.waves()==1 && young.size()==1,"the ripe sac publishes its own live airborne guard once");
                var saved=new CompoundTag();hive.addAdditionalSaveData(saved);
                h.assertTrue(saved.contains("BlacklightBroodStage") && saved.getString("BlacklightBrood").equals("flyer"),"old brood save adopted its district without rerolling");
                saved.putInt("BlacklightBroodStage",4);hive.readAdditionalSaveData(saved);
            } catch(Throwable failed){w.close();throw failed;}
        });
        h.runAfterDelay(85,()->{
            try(w){
                h.assertTrue(!hive.brood().equals("flyer"),"the same sac updates its production when its district returns to the initial stage");
            }
            h.succeed();
        });
    }

    @GameTest(template="wall_test",batch="air_population_radio",timeoutTicks=180)
    public static void groundPatrolCallsItsOwnCrewedAirSupport(GameTestHelper h){
        var w=new World(h,4,200);
        h.runAfterDelay(25,()->{
            try{
                for(int x=-28;x<=28;x++)for(int z=-20;z<=32;z++)w.block(w.center.offset(x,-1,z),Blocks.STONE);
                var soldier=ModEntities.BLACKWATCH_SOLDIER.get().create(w.level);
                soldier.setAutomaticUnitJoin(false);soldier.setPos(Vec3.atBottomCenterOf(w.center));
                soldier.initializeNativeLoadout("ground-radio-patrol");w.level.addFreshEntity(soldier);w.bodies.add(soldier);
                var target=ModEntities.COMMON_INFECTED.get().create(w.level);
                target.setNoAi(true);target.setInvulnerable(true);target.setPos(Vec3.atBottomCenterOf(w.center.south(14)));
                w.level.addFreshEntity(target);w.bodies.add(target);soldier.setTarget(target);
                soldier.getPersistentData().putLong("UavLeaseUntil",w.level.getGameTime()+6000);
                soldier.tickCount=20;
                h.assertTrue(com.codex.blacklight.military.MilitaryTactics.tick(soldier,target)&&soldier.radioStarted()>=0,
                        "a ground trooper starts the radio even with an outstanding UAV lease");
                h.runAfterDelay(90,()->{
                    try(w){
                        var report=soldier.getPersistentData();
                        h.assertTrue(report.hasUUID("AirSupportCraft"),"patrol completes the call and dispatches a helicopter: "+report.getString("AirSupportLastResult"));
                        var entity=w.level.getEntity(report.getUUID("AirSupportCraft"));
                        h.assertTrue(entity instanceof BlackwatchHelicopter,"the dispatch ID resolves to a live helicopter");
                        var craft=(BlackwatchHelicopter)entity;
                        w.bodies.addAll(craft.getPassengers());w.bodies.add(craft);
                        h.assertTrue(craft.getPassengers().size()>=2 && craft.combat().hostile(target),"the aircraft has flight crew and recognizes the reported contact");
                    }
                    h.succeed();
                });
            }catch(Throwable failure){w.close();throw failure;}
        });
    }

    @GameTest(template="wall_test",batch="air_outbreak_clock",timeoutTicks=6600)
    public static void outbreakClockSpawnsFlyersAroundAPresentPlayer(GameTestHelper h){
        var w=new World(h,4,130);
        var level=w.level;
        int day=OutbreakConfig.DAY_TICKS.get(), interval=OutbreakConfig.INTERVAL.get();
        int first=GameplayBalanceConfig.STAGE_DAYS[0].get(), yellow=GameplayBalanceConfig.YELLOW_CHANCE.get();
        int min=GameplayBalanceConfig.SPAWN_MIN.get(), max=GameplayBalanceConfig.SPAWN_MAX.get();
        OutbreakConfig.DAY_TICKS.set(200);OutbreakConfig.INTERVAL.set(20);
        GameplayBalanceConfig.STAGE_DAYS[0].set(1);GameplayBalanceConfig.YELLOW_CHANCE.set(100);
        GameplayBalanceConfig.SPAWN_MIN.set(27);GameplayBalanceConfig.SPAWN_MAX.set(32);
        for(int x=-38;x<=38;x++)for(int z=-38;z<=38;z++)w.block(w.center.offset(x,-1,z),Blocks.STONE);
        var viewer=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"wild-flyer-qa"));
        viewer.setInvulnerable(true);viewer.setNoGravity(true);viewer.setPos(Vec3.atBottomCenterOf(w.center));level.addNewPlayer(viewer);w.bodies.add(viewer);
        boolean[] done={false};int[] cycles={0},born={0};
        Runnable finish=()->{
            OutbreakConfig.DAY_TICKS.set(day);OutbreakConfig.INTERVAL.set(interval);GameplayBalanceConfig.STAGE_DAYS[0].set(first);
            GameplayBalanceConfig.YELLOW_CHANCE.set(yellow);GameplayBalanceConfig.SPAWN_MIN.set(min);GameplayBalanceConfig.SPAWN_MAX.set(max);
            w.close();
        };
        h.onEachTick(()->{
            if(done[0]||level.getGameTime()%20!=1)return;
            cycles[0]++;
            for(int x=-40;x<=40;x+=8)for(int z=-40;z<=40;z+=8){
                var near=w.center.offset(x,0,z);CityEcology.release(level,near);InfectionEcology.released(level,near);
            }
            var ambient=level.getEntitiesOfClass(Mob.class,new AABB(w.center).inflate(65),m->m.getPersistentData().getBoolean(PopulationGuard.AMBIENT));
            for(var mob:ambient)if(mob instanceof EncounterMob flyer && flyer.kind()==EncounterKind.FLYER){
                done[0]=true;w.bodies.addAll(ambient);
                try{h.assertTrue(flyer.isNoGravity() && flyer.getY()>=w.center.getY()+5,"wild flyer is flying above the physical surface");}finally{finish.run();}
                h.succeed();return;
            }
            born[0]+=ambient.size();
            // Observe repeated ordinary batches without saturating the real local population budget.
            ambient.forEach(Mob::discard);
            if(cycles[0]>290){done[0]=true;String reason="cycles="+cycles[0]+" ordinary births="+born[0]+" stage="+CityEcology.stage(level,w.center);finish.run();h.assertTrue(false,reason);}
        });
    }
}
