package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.city.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.military.MilitaryFaction;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Incoming street patrols: a real crewed vehicle, independent of base/garage generation. */
final class RedZoneVehicleSpawns {
    private static final String MARKER="BlacklightRedZonePatrol", DIMENSION="BlacklightPatrolDimension",
            BORN="BlacklightPatrolBorn", CREW="BlacklightPatrolVehicle";
    private RedZoneVehicleSpawns(){}

    static boolean automatic(MilitaryVehicle vehicle){
        return vehicle.getPersistentData().getBoolean(MARKER)
                &&vehicle.getPersistentData().getString(DIMENSION).equals(vehicle.level().dimension().location().toString())
                &&vehicle.playerOwner()==null&&!vehicle.controlLocked()
                &&vehicle.getPassengers().stream().noneMatch(Player.class::isInstance);
    }
    static Optional<BlockPos> destination(ServerLevel level,MilitaryVehicle vehicle){
        // Certify a short, actually loaded lane with the same full-hull checks as driving.
        double angle=Math.toRadians(vehicle.getYRot());
        for(int distance:new int[]{32,24,16}){
            var target=vehicle.position().add(Math.sin(angle)*distance,0,Math.cos(angle)*distance);
            if(VehicleGarageService.lane(level,vehicle,vehicle.position(),target))return Optional.of(BlockPos.containing(target));
        }
        return Optional.empty();
    }
    static void tick(ServerLevel level,int active){
        var data=VehicleFleetSavedData.get(level);
        // Unloaded ambient identities still occupy a slot. Walking away or reloading cannot duplicate them.
        int unloaded=0;
        for(var fleet:data.fleets())if(fleet.ambientPatrol()&&fleet.automaticPatrol()){
            var entity=level.getEntity(fleet.vehicles().get(0));
            if(!(entity instanceof MilitaryVehicle vehicle)){unloaded++;continue;}
            if(!automatic(vehicle)||vehicle.hasCustomName())continue;
            long age=Math.max(0,level.getGameTime()-vehicle.getPersistentData().getLong(BORN));
            if(age>=MilitaryCampaignConfig.FIELD_LIFETIME.get()
                    &&level.players().stream().noneMatch(p->!p.isSpectator()&&p.distanceToSqr(vehicle)<48*48)
                    &&crew(level,vehicle).stream().noneMatch(s->s.isAlive()&&(!MilitaryVehicle.crewOperational(s)||s.getTarget()!=null||MilitaryHomeSystem.ordered(s)))){
                retire(level,vehicle);
            }
        }
        if(active+unloaded>=MilitaryCampaignConfig.VEHICLE_PATROL_LIMIT.get()
                ||!data.claimPatrolAttempt(level.getGameTime(),MilitaryCampaignConfig.VEHICLE_PATROL_INTERVAL.get()))return;
        var players=level.players().stream().filter(p->!p.isSpectator()&&p.isAlive()).toList();
        if(players.isEmpty())return;
        var player=players.get(level.random.nextInt(players.size()));
        String chassis=switch(level.random.nextInt(4)){case 0,1->"truck";case 2->"apc";default->"tank";};
        var faction=level.random.nextInt(3)==0?MilitaryFaction.MILITARY:MilitaryFaction.BLACKWATCH;
        for(int attempt=0;attempt<12;attempt++){
            double angle=level.random.nextDouble()*Math.PI*2,range=48+level.random.nextInt(33);
            int x=player.getBlockX()+(int)Math.round(Math.cos(angle)*range),z=player.getBlockZ()+(int)Math.round(Math.sin(angle)*range);
            var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);if(chunk==null)continue;
            int y=chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)+1;
            var pos=new BlockPos(x,y,z);
            if(!level.isPositionEntityTicking(pos)||CityEcology.zone(level,pos)!=DistrictThreat.Zone.RED
                    ||level.players().stream().anyMatch(p->!p.isSpectator()&&p.distanceToSqr(Vec3.atBottomCenterOf(pos))<32*32))continue;
            if(!MilitaryCampaignSystem.streetSurface(level,x,y,z))continue;
            float yaw=90*level.random.nextInt(4);
            if(spawn(level,chassis,faction,pos,yaw).isPresent())return;
        }
    }
    /** One atomic server transaction; cancellation never leaves a chassis or partial squad behind. */
    static Optional<MilitaryVehicle> spawn(ServerLevel level,String chassis,MilitaryFaction faction,BlockPos pos,float yaw){
        if(!level.getServer().isSameThread()||!VehiclePatrolSystem.allowed(level)
                ||!level.isPositionEntityTicking(pos)||CityEcology.zone(level,pos)!=DistrictThreat.Zone.RED)return Optional.empty();
        var data=VehicleFleetSavedData.get(level);
        long reserved=data.fleets().stream().filter(VehicleFleet::automaticPatrol)
                .filter(f->f.ambientPatrol()||f.vehicles().stream().anyMatch(id->level.getEntity(id) instanceof MilitaryVehicle v&&VehiclePatrolSystem.eligible(level,v))).count();
        if(reserved>=MilitaryCampaignConfig.VEHICLE_PATROL_LIMIT.get())return Optional.empty();
        var vehicle=VehicleRegistry.type(faction,chassis).create(level);if(vehicle==null)return Optional.empty();
        vehicle.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);vehicle.setYRot(yaw);
        if(!VehicleKinematics.placementReady(vehicle)||!level.getEntities(vehicle,vehicle.getBoundingBox().inflate(1)).isEmpty())return Optional.empty();
        var target=destination(level,vehicle);if(target.isEmpty())return Optional.empty();
        int personnel=0;for(var entity:level.getAllEntities())if(entity instanceof MilitarySoldier soldier&&soldier.isAlive())personnel++;
        if(personnel+vehicle.definition().seats().size()>CityConfig.MILITARY_GARRISON_LIMIT.get())return Optional.empty();
        var soldiers=new ArrayList<MilitarySoldier>();
        for(var seat:vehicle.definition().seats()){
            var type=faction==MilitaryFaction.BLACKWATCH
                    ?(seat.job()==VehicleDefinition.Job.OFFICER?ModEntities.BLACKWATCH_OFFICER.get():ModEntities.BLACKWATCH_SOLDIER.get())
                    :(seat.job()==VehicleDefinition.Job.OFFICER?ModEntities.MILITARY_OFFICER.get():ModEntities.MILITARY_SOLDIER.get());
            var soldier=type.create(level);if(soldier==null)return Optional.empty();
            soldier.setPos(vehicle.position());soldier.setAutomaticUnitJoin(false);
            var tag=soldier.getPersistentData();tag.putUUID(CREW,vehicle.getUUID());
            tag.putBoolean("BlacklightCampaignField",true);
            tag.putString(MilitaryHomeSystem.MODE_TAG,"field");tag.putLong(MilitaryHomeSystem.FIELD_ANCHOR_TAG,pos.asLong());
            tag.putString(MilitaryHomeSystem.FIELD_DIMENSION_TAG,level.dimension().location().toString());
            if(!soldier.initializeNativeLoadout("street:"+vehicle.getUUID()+":"+seat.id()))return Optional.empty();
            soldiers.add(soldier);
        }
        var marker=vehicle.getPersistentData();marker.putBoolean(MARKER,true);
        marker.putString(DIMENSION,level.dimension().location().toString());marker.putLong(BORN,level.getGameTime());
        vehicle.inventory().issueInitialLoadout();vehicle.syncVehicleState();
        if(!level.addFreshEntity(vehicle)){vehicle.discard();return Optional.empty();}
        for(var soldier:soldiers)if(!level.addFreshEntity(soldier)||!soldier.startRiding(vehicle)){
            soldiers.forEach(Entity::discard);vehicle.discard();return Optional.empty();
        }
        var fleet=VehicleFleetSystem.createAutomaticPatrol(level,vehicle);
        if(fleet.isEmpty()){soldiers.forEach(Entity::discard);vehicle.discard();return Optional.empty();}
        data.fleet(fleet.get()).orElseThrow().ambientPatrol(true);data.setDirty();
        VehicleFleetSystem.automaticMission(level,fleet.get(),"PATROL",target.get());
        return Optional.of(vehicle);
    }
    private static List<MilitarySoldier> crew(ServerLevel level,MilitaryVehicle vehicle){
        var result=new ArrayList<MilitarySoldier>();
        for(var entity:level.getAllEntities())if(entity instanceof MilitarySoldier soldier
                &&soldier.getPersistentData().hasUUID(CREW)&&soldier.getPersistentData().getUUID(CREW).equals(vehicle.getUUID()))result.add(soldier);
        return result;
    }
    private static void retire(ServerLevel level,MilitaryVehicle vehicle){
        for(var soldier:crew(level,vehicle))if(!MilitaryHomeSystem.ordered(soldier)&&!soldier.hasCustomName())soldier.discard();
        vehicle.discard();
    }
    static void removed(ServerLevel level,MilitaryVehicle vehicle,Entity.RemovalReason reason){
        if(reason==Entity.RemovalReason.UNLOADED_TO_CHUNK||reason==Entity.RemovalReason.UNLOADED_WITH_PLAYER)return;
        var data=VehicleFleetSavedData.get(level);
        data.vehicleFleet(vehicle.getUUID()).filter(VehicleFleet::ambientPatrol).ifPresent(fleet->VehicleFleetSystem.remove(level,fleet.id()));
    }
}
