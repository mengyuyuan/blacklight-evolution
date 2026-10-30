package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.city.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.infection.OutbreakData;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;

/** Automatic intent for real native garage vehicles; the fleet remains the only driver and resource owner. */
public final class VehiclePatrolSystem {
    private static final Map<ServerLevel,Long> LAST=new WeakHashMap<>();
    private static final Map<ServerLevel,Integer> CURSORS=new WeakHashMap<>();
    private VehiclePatrolSystem(){}

    static boolean eligible(ServerLevel level,MilitaryVehicle vehicle){
        if(level==null||!level.getServer().isSameThread()||vehicle==null||vehicle.level()!=level
                ||level.getEntity(vehicle.getUUID())!=vehicle||vehicle.isRemoved()||vehicle.health()<=0
                ||!VehicleGarageService.automatic(vehicle)||!level.isPositionEntityTicking(vehicle.blockPosition()))return false;
        var garage=VehicleGarageService.data(level).garage(vehicle.currentHome()).orElse(null);
        if(garage==null||!garage.baseId().equals(vehicle.nativeOrigin())||garage.faction()!=vehicle.faction()
                ||!garage.dimension().equals(level.dimension().location().toString()))return false;
        var slot=garage.slots().get(vehicle.chassisId());
        if(slot==null||slot.state()!=VehicleGarageData.State.ISSUED||!vehicle.getUUID().equals(slot.vehicle()))return false;
        var base=MilitaryBaseAccess.find(level,garage.baseId()).orElse(null);
        if(base==null||!base.operational())return false;
        if(!(vehicle.occupant(VehicleDefinition.Job.DRIVER) instanceof MilitarySoldier driver)
                ||driver.faction()!=vehicle.faction()||!MilitaryVehicle.crewOperational(driver))return false;
        if(!vehicle.chassisId().equals("truck")&&!(vehicle.occupant(VehicleDefinition.Job.GUNNER) instanceof MilitarySoldier))return false;
        return vehicle.getPassengers().stream().allMatch(p->p instanceof MilitarySoldier s
                &&s.faction()==vehicle.faction()&&MilitaryVehicle.crewOperational(s)&&!MilitaryHomeSystem.ordered(s));
    }
    private static boolean allowed(ServerLevel level){
        return MilitaryCampaignSystem.enabled(level)&&MilitaryCampaignConfig.VEHICLE_PATROLS.get()
                &&OutbreakData.get(level.getServer()).active()
                &&level.getDifficulty()!=Difficulty.PEACEFUL&&level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
    }
    /** Budgeted metadata/loaded entity queries only; never requests a chunk or creates a chassis/crew. */
    public static void tick(ServerLevel level){
        if(level==null||!level.getServer().isSameThread()||level.getGameTime()%100!=0
                ||Objects.equals(LAST.put(level,level.getGameTime()),level.getGameTime()))return;
        var data=VehicleFleetSavedData.get(level);boolean allowed=allowed(level);int active=0;
        for(var fleet:data.fleets())if(fleet.automaticPatrol()){
            if(fleet.vehicles().size()!=1){fleet.automaticPatrol(false);data.setDirty();continue;}
            if(!(level.getEntity(fleet.vehicles().get(0)) instanceof MilitaryVehicle vehicle))continue;
            if(!VehicleGarageService.automatic(vehicle)||vehicle.getPassengers().stream().anyMatch(p->p instanceof MilitarySoldier s&&MilitaryHomeSystem.ordered(s))){
                fleet.automaticPatrol(false);data.setDirty();continue;
            }
            boolean ready=allowed&&active<MilitaryCampaignConfig.VEHICLE_PATROL_LIMIT.get()&&eligible(level,vehicle);
            if(!ready){
                if(fleet.mission()!=VehicleFleet.Mission.GUARD)VehicleFleetSystem.automaticMission(level,fleet.id(),"GUARD",vehicle.blockPosition());
                continue;
            }
            active++;
            if(fleet.mission()==VehicleFleet.Mission.GUARD&&!fleet.supplyReturn()){
                var garage=VehicleGarageService.data(level).garage(vehicle.currentHome()).orElse(null);
                VehicleGarageService.patrolDestination(level,vehicle,garage)
                        .ifPresent(at->VehicleFleetSystem.automaticMission(level,fleet.id(),"PATROL",at));
            }
        }
        if(!allowed||active>=MilitaryCampaignConfig.VEHICLE_PATROL_LIMIT.get()||level.players().isEmpty())return;
        var garages=VehicleGarageService.data(level).garages();if(garages.isEmpty())return;
        int first=Math.floorMod(CURSORS.getOrDefault(level,0),garages.size());CURSORS.put(level,(first+4)%garages.size());
        for(int i=0;i<Math.min(4,garages.size())&&active<MilitaryCampaignConfig.VEHICLE_PATROL_LIMIT.get();i++){
            var garage=garages.get((first+i)%garages.size());
            for(String chassis:List.of("apc","tank","truck")){
                if(active>=MilitaryCampaignConfig.VEHICLE_PATROL_LIMIT.get())break;
                var slot=garage.slots().get(chassis);
                if(slot.vehicle()==null||!(level.getEntity(slot.vehicle()) instanceof MilitaryVehicle vehicle)
                        ||data.vehicleFleet(vehicle.getUUID()).isPresent()||!eligible(level,vehicle)
                        ||!VehicleSupplyRules.readyToResume(vehicle.definition(),vehicle.inventory(),vehicle.health())
                        ||level.players().stream().noneMatch(p->!p.isSpectator()&&p.distanceToSqr(vehicle)<256*256))continue;
                var target=VehicleGarageService.patrolDestination(level,vehicle,garage);if(target.isEmpty())continue;
                var fleet=VehicleFleetSystem.createAutomaticPatrol(level,vehicle);if(fleet.isEmpty())continue;
                if(VehicleFleetSystem.automaticMission(level,fleet.get(),"PATROL",target.get()))active++;
            }
        }
    }
}
