package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.city.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.military.MilitaryFaction;
import com.codex.blacklight.military.vehicle.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

/** Real hulls, real seated passengers and the registered fleet tick; no garage fixture. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class RedZoneVehicleSpawnGameTest {
    @SuppressWarnings("unchecked") private static Optional<MilitaryVehicle> spawn(ServerLevel level,String chassis,BlockPos at){
        try{
            var method=Class.forName("com.codex.blacklight.military.vehicle.RedZoneVehicleSpawns")
                    .getDeclaredMethod("spawn",ServerLevel.class,String.class,MilitaryFaction.class,BlockPos.class,float.class);
            method.setAccessible(true);return (Optional<MilitaryVehicle>)method.invoke(null,level,chassis,MilitaryFaction.BLACKWATCH,at,0f);
        }catch(ReflectiveOperationException failure){throw new GameTestAssertException("real red-zone spawning failed: "+failure);}
    }
    private static final class Rules implements AutoCloseable {
        final ServerLevel level;final boolean enabled,spawning,outbreak,patrols;final int limit,personnel,interval;
        final OutbreakData previousOutbreak;
        Rules(ServerLevel level){
            this.level=level;enabled=MilitaryCampaignConfig.ENABLED.get();outbreak=OutbreakConfig.ENABLED.get();patrols=MilitaryCampaignConfig.VEHICLE_PATROLS.get();interval=MilitaryCampaignConfig.VEHICLE_PATROL_INTERVAL.get();
            previousOutbreak=OutbreakData.load(OutbreakData.get(level.getServer()).save(new net.minecraft.nbt.CompoundTag()));
            spawning=level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
            limit=MilitaryCampaignConfig.VEHICLE_PATROL_LIMIT.get();personnel=CityConfig.MILITARY_GARRISON_LIMIT.get();
            MilitaryCampaignConfig.ENABLED.set(true);MilitaryCampaignConfig.VEHICLE_PATROLS.set(true);
            OutbreakConfig.ENABLED.set(true);MilitaryCampaignConfig.VEHICLE_PATROL_LIMIT.set(8);CityConfig.MILITARY_GARRISON_LIMIT.set(200);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,level.getServer());
            OutbreakData.get(level.getServer()).start(level.getServer().overworld().getGameTime());
        }
        void red(BlockPos at){CityEcologyData.get(level).observe(CityEcology.key(level,at),level.getGameTime(),1,2,24000,DistrictThreat.Signal.EMPTY);}
        @Override public void close(){MilitaryCampaignConfig.ENABLED.set(enabled);OutbreakConfig.ENABLED.set(outbreak);MilitaryCampaignConfig.VEHICLE_PATROLS.set(patrols);MilitaryCampaignConfig.VEHICLE_PATROL_INTERVAL.set(interval);
            MilitaryCampaignConfig.VEHICLE_PATROL_LIMIT.set(limit);CityConfig.MILITARY_GARRISON_LIMIT.set(personnel);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawning,level.getServer());
            previousOutbreak.setDirty();level.getServer().overworld().getDataStorage().set("blacklight_outbreak",previousOutbreak);}
    }
    private static void collect(MilitaryVehicleGameTest.Fixture f,MilitaryVehicle vehicle){f.entities.addAll(vehicle.getPassengers());f.entities.add(vehicle);}
    @GameTest(template="blackwatch_arena",batch="redzone_random_tick",timeoutTicks=900)
    public static void registeredServerTickRandomlySpawnsNearPlayerWithoutAnyLocalGarage(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,915200);var rules=new Rules(f.level);
        var center=new BlockPos(f.x+128,0,128);rules.red(center);
        MilitaryCampaignConfig.VEHICLE_PATROL_INTERVAL.set(100);
        // Deliberately prepare disposable flat QA chunks; production spawning must not load any more.
        for(int cx=(center.getX()>>4)-8;cx<=(center.getX()>>4)+8;cx++)for(int cz=0;cz<=16;cz++){
            long key=net.minecraft.world.level.ChunkPos.asLong(cx,cz);
            f.forced.putIfAbsent(key,f.level.getForcedChunks().contains(key));f.level.getChunk(cx,cz);f.level.setChunkForced(cx,cz,true);
        }
        int y=f.level.getChunkSource().getChunkNow(center.getX()>>4,8).getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,center.getX(),128)+1;
        var player=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(player);player.setPos(center.getX()+.5,y,128.5);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        h.assertTrue(VehicleGarageService.data(f.level).garages().stream().noneMatch(g->Math.abs(g.site().x()-center.getX())<512),"no local garage exists for this real random patrol");
        awaitRandom(h,f,rules,center,800);
    }
    private static void awaitRandom(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,Rules rules,BlockPos center,int left){
        h.runAfterDelay(10,()->{
            var vehicles=new ArrayList<MilitaryVehicle>();
            for(var entity:f.level.getAllEntities())if(entity instanceof MilitaryVehicle v&&v.blockPosition().distSqr(center)<256*256
                    &&VehicleFleetSavedData.get(f.level).vehicleFleet(v.getUUID()).map(VehicleFleet::ambientPatrol).orElse(false))vehicles.add(v);
            if(!vehicles.isEmpty()){
                var vehicle=vehicles.get(0);var start=vehicle.position();collect(f,vehicle);
                awaitMotion(h,f,rules,center,vehicle,start,240);return;
            }
            if(left<=0){try(f;rules){h.assertTrue(false,"no random red-zone vehicle appeared during 800 actual server ticks");}return;}
            awaitRandom(h,f,rules,center,left-10);
        });
    }
    private static void awaitMotion(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,Rules rules,BlockPos center,MilitaryVehicle vehicle,net.minecraft.world.phys.Vec3 start,int left){
        h.runAfterDelay(10,()->{
            double moved=vehicle.position().subtract(start).horizontalDistance();
            if(moved>4||left<=0){try(f;rules){
                for(var entity:f.level.getAllEntities())if(entity instanceof MilitaryVehicle v&&v.blockPosition().distSqr(center)<256*256&&VehicleFleetSavedData.get(f.level).vehicleFleet(v.getUUID()).map(VehicleFleet::ambientPatrol).orElse(false))collect(f,v);
                var fleet=VehicleFleetSavedData.get(f.level).vehicleFleet(vehicle.getUUID()).orElseThrow();
                h.assertTrue(moved>4,"real registered random patrol must drive; moved="+moved+" "+VehicleFleetSystem.diagnostic(f.level,fleet.id(),vehicle.getUUID()));
                h.assertTrue(vehicle.getPassengers().size()==vehicle.definition().seats().size(),"random generation supplies all actual crew seats");
            }h.succeed();return;}
            awaitMotion(h,f,rules,center,vehicle,start,left-10);
        });
    }
    @GameTest(template="blackwatch_arena",batch="redzone_truck",timeoutTicks=400)
    public static void noGarageTruckHasSixRearTroopsAndActuallyDrives(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,910000);var rules=new Rules(f.level);var at=new BlockPos(f.x,81,0);rules.red(at);
        VehicleTestReadiness.await(h,f,()->{
            var truck=spawn(f.level,"truck",at).orElseThrow(()->new GameTestAssertException("red-zone truck must spawn without any base or garage"));collect(f,truck);
            h.assertTrue(truck.getPassengers().size()==8,"real truck has driver, officer and six actual rear soldiers");
            long rear=truck.getPassengers().stream().filter(p->truck.seatOf(p.getUUID()).startsWith("passenger_")).count();
            h.assertTrue(rear==6&&truck.nativeOrigin().isEmpty()&&truck.currentHome().isEmpty(),"six rear seats are occupied and no fake garage provenance exists");
            var fleet=VehicleFleetSavedData.get(f.level).vehicleFleet(truck.getUUID()).orElseThrow();
            h.assertTrue(fleet.ambientPatrol()&&fleet.automaticPatrol(),"actual durable fleet owns the incoming patrol");
            var initial=truck.position();
            h.runAfterDelay(55,()->{try(f;rules){
                h.assertTrue(truck.position().subtract(initial).horizontalDistance()>4,"registered server fleet tick moves the troop transport");
                h.assertTrue(truck.getPassengers().size()==8,"driving preserves real people rather than drawing decorative soldiers");
            }h.succeed();});
        });
    }
    @GameTest(template="blackwatch_arena",batch="redzone_tank",timeoutTicks=400)
    public static void redZoneTankDrivesWithRealDriverAndGunner(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,911000);var rules=new Rules(f.level);var at=new BlockPos(f.x,81,0);rules.red(at);
        VehicleTestReadiness.await(h,f,()->{
            var tank=spawn(f.level,"tank",at).orElseThrow(()->new GameTestAssertException("red-zone tank must spawn without a garage"));collect(f,tank);
            h.assertTrue(tank.occupant(VehicleDefinition.Job.DRIVER) instanceof MilitarySoldier&&tank.occupant(VehicleDefinition.Job.GUNNER) instanceof MilitarySoldier,"real driver and gunner occupy tank");
            var initial=tank.position();h.runAfterDelay(60,()->{try(f;rules){h.assertTrue(tank.position().subtract(initial).horizontalDistance()>4,"actual tank patrol moves horizontally");}h.succeed();});
        });
    }
    @GameTest(template="blackwatch_arena",batch="redzone_rejection",timeoutTicks=400)
    public static void greenDisabledAndBlockedSitesNeverProducePartialVehiclesOrCrew(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,912000);var rules=new Rules(f.level);var at=new BlockPos(f.x,81,0);
        VehicleTestReadiness.await(h,f,()->{try(f;rules){
            h.assertTrue(CityEcology.zone(f.level,at)==DistrictThreat.Zone.GREEN&&spawn(f.level,"truck",at).isEmpty(),"green zone cannot receive an ambient chassis");
            rules.red(at);f.level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,f.level.getServer());
            h.assertTrue(spawn(f.level,"truck",at).isEmpty(),"gamerule disables the real spawn transaction");
            f.level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,f.level.getServer());
            f.level.setBlock(at,Blocks.STONE.defaultBlockState(),2);
            var before=new HashSet<UUID>();f.level.getAllEntities().forEach(e->before.add(e.getUUID()));
            h.assertTrue(spawn(f.level,"truck",at).isEmpty(),"blocked hull cannot spawn");
            var after=new HashSet<UUID>();f.level.getAllEntities().forEach(e->after.add(e.getUUID()));
            h.assertTrue(before.equals(after),"rejection leaves no chassis or partial squad");
        }h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="redzone_limit",timeoutTicks=400)
    public static void persistedPatrolSlotPreventsDuplicationAndDeathReleasesIt(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,913000);var rules=new Rules(f.level);var at=new BlockPos(f.x,81,0);rules.red(at);
        VehicleTestReadiness.await(h,f,()->{try(f;rules){
            int existing=(int)VehicleFleetSavedData.get(f.level).fleets().stream().filter(VehicleFleet::automaticPatrol).count();
            MilitaryCampaignConfig.VEHICLE_PATROL_LIMIT.set(existing+1);
            var truck=spawn(f.level,"truck",at).orElseThrow();collect(f,truck);
            var data=VehicleFleetSavedData.get(f.level);var id=data.vehicleFleet(truck.getUUID()).orElseThrow().id();
            var saved=VehicleFleetSavedData.load(data.save(new net.minecraft.nbt.CompoundTag()));
            h.assertTrue(saved.fleet(id).orElseThrow().ambientPatrol(),"ambient reservation survives actual SavedData roundtrip");
            h.assertTrue(spawn(f.level,"apc",at.offset(6,0,0)).isEmpty(),"existing patrol consumes dimension budget before new crew is created");
            truck.discard();h.assertTrue(data.fleet(id).isEmpty(),"real entity discard releases its ambient fleet slot");
        }h.succeed();});
    }
}
