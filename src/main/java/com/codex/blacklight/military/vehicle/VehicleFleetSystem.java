package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.*;
import java.util.*;
import java.lang.ref.WeakReference;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Server-only fleet coordinator. UUID lookup never loads entities, chunks, bases, or dimensions. */
public final class VehicleFleetSystem {
    private static final int ROAD_STEP=2;
    private static final Map<ServerLevel,Runtime> RUNTIMES=new WeakHashMap<>();
    private static final Map<MinecraftServer,VehicleWorkBudget> BUDGETS=new WeakHashMap<>();
    private static final class Driving {
        final ArrayDeque<Vec3> route=new ArrayDeque<>();
        Vec3 destination,lastPosition;
        long failureSince=-1,lastProgress;
        int failures;
        void reset(){route.clear();destination=null;failureSince=-1;failures=0;lastPosition=null;}
    }
    private static final class Combat {
        final Map<UUID,Vec3> goals=new LinkedHashMap<>();
        final float yaw;
        long parkedSince=-1;
        Combat(float yaw){this.yaw=yaw;}
    }
    private static final class Runtime {
        final VehicleFleetSavedData data;
        final Map<UUID,Driving> driving=new HashMap<>();
        final Map<UUID,Combat> combat=new HashMap<>();
        final Map<UUID,UUID> commanders=new HashMap<>(),targets=new HashMap<>();
        final Map<UUID,Long> scans=new HashMap<>();
        final Map<UUID,Long> homeChecks=new HashMap<>();
        final Map<UUID,VehicleGarageService.GarageView> homes=new HashMap<>();
        final Map<UUID,Deployment> deployed=new HashMap<>();
        final Map<UUID,WeakReference<VehicleThreatGoal>> deploymentGoals=new HashMap<>();
        final Set<UUID> recalling=new HashSet<>();
        final Map<UUID,Boolean> patrolBack=new HashMap<>();
        final Map<UUID,Set<UUID>> resupplying=new HashMap<>();
        final Map<UUID,String> supplyReports=new HashMap<>();
        long tick=Long.MIN_VALUE;
        Runtime(ServerLevel level){data=VehicleFleetSavedData.get(level);}
    }
    private record Request(VehicleFleet fleet,MilitaryVehicle vehicle,Vec3 destination,double speed){}
    private record Node(int x,int z,int y,int heading,double cost,double estimate,Node parent,Vec3 position){}
    private record Cell(int x,int z,int y,int heading){}
    private record Search(List<Vec3> route,int nodes){}
    private record Deployment(UUID fleet,UUID vehicle,UUID unit,String originalOrder,Vec3 objective){}
    public record DeploymentView(MilitaryVehicle vehicle,Vec3 objective,boolean returning){}
    private static Runtime runtime(ServerLevel level){return RUNTIMES.computeIfAbsent(level,Runtime::new);}
    public static VehicleWorkBudget budget(ServerLevel level){return BUDGETS.computeIfAbsent(level.getServer(),ignored->new VehicleWorkBudget());}
    public static List<VehicleFleet> fleets(ServerLevel level){return runtime(level).data.fleets();}
    public static String status(ServerLevel level,UUID fleet){return runtime(level).data.fleet(fleet).map(f->f.status().name()).orElse("MISSING");}
    public static UUID actingCommander(ServerLevel level,UUID fleet){return runtime(level).commanders.get(fleet);}
    public static String logisticsStatus(ServerLevel level,UUID fleet){return runtime(level).supplyReports.getOrDefault(fleet,"NONE");}
    static boolean returningForSupplies(ServerLevel level,MilitaryVehicle vehicle){
        var state=RUNTIMES.get(level);return state!=null&&state.data.vehicleFleet(vehicle.getUUID()).map(VehicleFleet::supplyReturn).orElse(false);
    }
    static boolean serviceReady(ServerLevel level,MilitaryVehicle vehicle){
        var state=RUNTIMES.get(level);if(state==null)return true;
        var fleet=state.data.vehicleFleet(vehicle.getUUID()).orElse(null);if(fleet==null)return true;
        if(returning(state,fleet)&&returnHome(state,fleet,vehicle)){
            var home=state.homes.get(vehicle.getUUID());return home!=null&&vehicle.position().distanceToSqr(Vec3.atBottomCenterOf(home.position()))<.0025;
        }
        return fleet.status()!=VehicleFleet.Status.ACTIVE;
    }
    public static String diagnostic(ServerLevel level,UUID fleetId,UUID vehicleId){
        var state=RUNTIMES.get(level);if(state==null)return "FLEET_RUNTIME_MISSING";
        var fleet=state.data.fleet(fleetId).orElse(null);var drive=state.driving.get(vehicleId);
        return "fleet="+fleetId+" mission="+(fleet==null?"MISSING":fleet.mission())+" target="+(fleet==null?null:fleet.target())+" status="+(fleet==null?null:fleet.status())
                +" logistics="+state.supplyReports.get(fleetId)+" home="+state.homes.get(vehicleId)+" driving="+(drive==null?"NONE":"destination="+drive.destination+", size="+drive.route.size()+", next="+drive.route.peekFirst()+", last="+drive.route.peekLast()+", failures="+drive.failures+", lastProgress="+drive.lastProgress);
    }
    public static Optional<UUID> create(ServerLevel level,List<MilitaryVehicle> selected,UUID formalCommander){
        return create(level,selected,formalCommander,false);
    }
    /** A validated street reinforcement or issued native chassis uses its real seated driver. */
    static Optional<UUID> createAutomaticPatrol(ServerLevel level,MilitaryVehicle vehicle){
        if(!VehiclePatrolSystem.eligible(level,vehicle))return Optional.empty();
        var driver=(MilitarySoldier)vehicle.occupant(VehicleDefinition.Job.DRIVER);
        return create(level,List.of(vehicle),driver.getUUID(),true);
    }
    private static Optional<UUID> create(ServerLevel level,List<MilitaryVehicle> selected,UUID formalCommander,boolean automatic){
        if(level==null||selected==null||selected.isEmpty()||selected.size()>4||formalCommander==null)return Optional.empty();
        var vehicles=new ArrayList<>(selected);if(new HashSet<>(vehicles).size()!=vehicles.size()||vehicles.stream().anyMatch(Objects::isNull))return Optional.empty();
        var faction=vehicles.get(0).faction();var data=runtime(level).data;
        for(var vehicle:vehicles)if(vehicle.level()!=level||level.getEntity(vehicle.getUUID())!=vehicle||vehicle.isRemoved()||vehicle.health()<=0||vehicle.faction()!=faction||data.vehicleFleet(vehicle.getUUID()).isPresent())return Optional.empty();
        var crew=crew(vehicles,faction);var officer=crew.get(formalCommander);
        if(officer==null||!automatic&&(officer.rank().ordinal()<MilitaryRank.LIEUTENANT.ordinal()||officer.commandRole()!=MilitaryRole.OFFICER&&officer.commandRole()!=MilitaryRole.COMMANDER))return Optional.empty();
        vehicles.sort(Comparator.comparingInt((MilitaryVehicle v)->v.chassisId().equals("apc")?0:v.chassisId().equals("truck")?1:2).thenComparing(MilitaryVehicle::getUUID));
        var units=crew.values().stream().map(MilitarySoldier::unitId).filter(Objects::nonNull).distinct().sorted().toList();
        var leader=vehicles.get(0);var id=UUID.randomUUID();
        var fleet=new VehicleFleet(id,faction,vehicles.stream().map(Entity::getUUID).toList(),units,formalCommander,point(leader.position()));
        fleet.automaticPatrol(automatic);
        fleet.personnelOrder(unitOrder(level,fleet,officer).map(Object::toString).orElse(""));
        return data.add(fleet)?Optional.of(id):Optional.empty();
    }
    public static boolean setMission(ServerLevel level,UUID id,String mode,BlockPos target){
        return order(level,id,mode,target,false);
    }
    static boolean automaticMission(ServerLevel level,UUID id,String mode,BlockPos target){
        if(!runtime(level).data.fleet(id).map(VehicleFleet::automaticPatrol).orElse(false))return false;
        return order(level,id,mode,target,true);
    }
    private static boolean order(ServerLevel level,UUID id,String mode,BlockPos target,boolean automatic){
        var state=runtime(level);var fleet=state.data.fleet(id).orElse(null);if(fleet==null||mode==null)return false;
        VehicleFleet.Mission mission;
        try{mission=VehicleFleet.Mission.valueOf(mode.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException invalid){return false;}
        if(!fleet.order(mission,target==null?null:new MilitaryOrder.Point(target.getX()+.5,target.getY(),target.getZ()+.5)))return false;
        if(!automatic)fleet.automaticPatrol(false); // Any explicit order permanently takes ownership from the scheduler.
        if(state.deployed.values().stream().anyMatch(d->d.fleet.equals(id)))state.recalling.add(id);
        state.resupplying.remove(id);state.supplyReports.remove(id);clearRoutes(state,fleet);state.patrolBack.remove(id);state.data.setDirty();return true;
    }
    public static boolean remove(ServerLevel level,UUID id){
        var state=runtime(level);var fleet=state.data.fleet(id).orElse(null);if(fleet==null)return false;
        state.resupplying.remove(id);state.supplyReports.remove(id);clearRoutes(state,fleet);fleet.vehicles().forEach(budget(level)::cancel);state.commanders.remove(id);state.targets.remove(id);state.scans.remove(id);state.patrolBack.remove(id);state.deployed.entrySet().removeIf(e->e.getValue().fleet.equals(id));state.recalling.remove(id);return state.data.remove(id);
    }
    private static void clearRoutes(Runtime state,VehicleFleet fleet){state.combat.remove(fleet.id());for(var id:fleet.vehicles()){var drive=state.driving.remove(id);if(drive!=null)drive.reset();state.homeChecks.remove(id);state.homes.remove(id);}}
    private static Map<UUID,MilitarySoldier> crew(List<MilitaryVehicle> vehicles,MilitaryFaction faction){
        var result=new LinkedHashMap<UUID,MilitarySoldier>();
        for(var vehicle:vehicles)for(var passenger:vehicle.getPassengers())if(passenger instanceof MilitarySoldier soldier&&soldier.faction()==faction&&passenger.getVehicle()==vehicle&&MilitaryVehicle.crewOperational(soldier))result.put(soldier.getUUID(),soldier);
        return result;
    }
    private static boolean occupied(MilitaryVehicle vehicle){return vehicle.controlLocked()||vehicle.playerOwner()!=null||vehicle.getPassengers().stream().anyMatch(Player.class::isInstance);}
    private static boolean needsHome(MilitaryVehicle vehicle){return !vehicle.nativeOrigin().isEmpty()||!vehicle.currentHome().isEmpty();}
    private static boolean returning(Runtime state,VehicleFleet fleet){return fleet.mission()==VehicleFleet.Mission.RETURN||fleet.supplyReturn();}
    private static boolean returnHome(Runtime state,VehicleFleet fleet,MilitaryVehicle vehicle){
        return needsHome(vehicle)&&(!state.resupplying.containsKey(fleet.id())||state.resupplying.get(fleet.id()).contains(vehicle.getUUID()));
    }
    private static boolean needsService(MilitaryVehicle vehicle){return VehicleSupplyRules.needsService(vehicle.definition(),vehicle.inventory(),vehicle.health());}
    private static boolean readyToResume(MilitaryVehicle vehicle){return VehicleSupplyRules.readyToResume(vehicle.definition(),vehicle.inventory(),vehicle.health());}
    private static Set<UUID> departureShortages(List<MilitaryVehicle> vehicles){return vehicles.stream().filter(VehicleGarageService::automatic).filter(v->!readyToResume(v)).map(Entity::getUUID).collect(java.util.stream.Collectors.toSet());}
    private static String waitingForDeparture(List<MilitaryVehicle> vehicles){
        boolean ammo=vehicles.stream().anyMatch(v->VehicleSupplyRules.resumeNeedsPrimary(v.definition(),v.inventory()));
        boolean repair=vehicles.stream().anyMatch(v->VehicleSupplyRules.resumeNeedsRepair(v.definition(),v.health()));
        return ammo?(repair?"WAITING_FOR_PRIMARY_AMMUNITION_AND_REPAIR_PARTS":"WAITING_FOR_PRIMARY_AMMUNITION"):repair?"WAITING_FOR_REPAIR_PARTS":"WAITING_FOR_REAL_DELIVERY";
    }
    private static boolean driverReady(MilitaryVehicle vehicle,MilitaryFaction faction){return vehicle.occupant(VehicleDefinition.Job.DRIVER) instanceof MilitarySoldier soldier&&soldier.faction()==faction&&soldier.getVehicle()==vehicle&&MilitaryVehicle.crewOperational(soldier);}
    private static Optional<MilitaryOrder> unitOrder(ServerLevel level,VehicleFleet fleet,MilitarySoldier commander){
        var org=MilitaryOrganizationSystem.organization(level);
        if(commander!=null&&commander.unitId()!=null){var order=org.effectiveOrder(commander.unitId());if(order.isPresent())return order;}
        for(var unit:fleet.units()){var order=org.effectiveOrder(unit);if(order.isPresent())return order;}return Optional.empty();
    }
    private static void inheritOrder(ServerLevel level,Runtime state,VehicleFleet fleet,MilitarySoldier commander){
        var order=unitOrder(level,fleet,commander);String signature=order.map(Object::toString).orElse("");
        if(signature.equals(fleet.personnelOrder()))return;
        fleet.personnelOrder(signature);state.data.setDirty();if(order.isEmpty())return;
        fleet.automaticPatrol(false);
        var intent=order.get();var mode=switch(intent.mode()){case PATROL->VehicleFleet.Mission.PATROL;case MOVE,REGROUP->VehicleFleet.Mission.MOVE;case RETURN->VehicleFleet.Mission.RETURN;case GUARD->VehicleFleet.Mission.GUARD;};
        if(fleet.order(mode,intent.mode()==MilitaryOrder.Mode.REGROUP&&intent.target()==null?fleet.returnPoint():intent.target())){if(state.deployed.values().stream().anyMatch(d->d.fleet.equals(fleet.id())))state.recalling.add(fleet.id());state.resupplying.remove(fleet.id());state.supplyReports.remove(fleet.id());clearRoutes(state,fleet);state.patrolBack.remove(fleet.id());}
    }
    private static void status(Runtime state,VehicleFleet fleet,VehicleFleet.Status next){if(fleet.status()!=next){fleet.status(next);state.data.setDirty();}}
    /** Register exactly once from the loaded dimension's POST tick. Safe against duplicate dispatch. */
    public static void tick(ServerLevel level){
        var state=runtime(level);long now=level.getServer().getTickCount();if(state.tick==now)return;state.tick=now;
        for(var id:List.copyOf(state.deployed.keySet()))if(level.getEntity(id) instanceof MilitarySoldier soldier)deployment(soldier);else state.deployed.remove(id);
        state.recalling.removeIf(id->state.deployed.values().stream().noneMatch(d->d.fleet.equals(id)));
        var budget=budget(level);var requests=new LinkedHashMap<UUID,Request>();
        for(var fleet:state.data.fleets()){
            var vehicles=new ArrayList<MilitaryVehicle>();
            for(var id:fleet.vehicles())if(level.getEntity(id) instanceof MilitaryVehicle vehicle&&!vehicle.isRemoved()&&vehicle.health()>0&&vehicle.faction()==fleet.faction())vehicles.add(vehicle);
            if(vehicles.isEmpty()){state.commanders.remove(fleet.id());status(state,fleet,VehicleFleet.Status.UNLOADED);continue;}
            var crew=crew(vehicles,fleet.faction());var eligible=new HashMap<UUID,Integer>();crew.forEach((id,s)->eligible.put(id,s.rank().ordinal()));
            UUID acting=VehicleFleet.chooseCommander(fleet.formalCommander(),eligible);
            if(acting==null){state.commanders.remove(fleet.id());status(state,fleet,VehicleFleet.Status.NO_COMMANDER);park(vehicles);continue;}
            state.commanders.put(fleet.id(),acting);inheritOrder(level,state,fleet,crew.get(acting));
            if(vehicles.stream().anyMatch(VehicleFleetSystem::occupied)){status(state,fleet,VehicleFleet.Status.PLAYER_CONTROL);if(fleet.supplyReturn()){fleet.supplyReturn(false);state.data.setDirty();}state.resupplying.remove(fleet.id());state.supplyReports.put(fleet.id(),"PLAYER_CONTROL");clearRoutes(state,fleet);for(var vehicle:vehicles)if(!occupied(vehicle))vehicle.setDeltaMovement(Vec3.ZERO);continue;}
            if(fleet.supplyReturn()&&!state.resupplying.containsKey(fleet.id())){
                if(vehicles.size()!=fleet.vehicles().size()){status(state,fleet,VehicleFleet.Status.UNLOADED);park(vehicles);continue;}
                var pending=departureShortages(vehicles);
                if(pending.isEmpty()){fleet.supplyReturn(false);state.data.setDirty();state.supplyReports.put(fleet.id(),"RESUMED_ORIGINAL_ORDER");}
                else{state.resupplying.put(fleet.id(),Set.copyOf(pending));state.supplyReports.put(fleet.id(),"RETURNING_FOR_SUPPLIES");}
            }
            if(state.resupplying.containsKey(fleet.id())&&!vehicles.stream().map(Entity::getUUID).collect(java.util.stream.Collectors.toSet()).containsAll(state.resupplying.get(fleet.id()))){status(state,fleet,VehicleFleet.Status.UNLOADED);state.supplyReports.put(fleet.id(),"SUPPLY_VEHICLE_UNLOADED");park(vehicles);continue;}
            boolean blocked=fleet.status()==VehicleFleet.Status.BLOCKED||fleet.vehicles().stream().map(state.driving::get).filter(Objects::nonNull).anyMatch(d->d.failures>=3);
            LivingEntity threat=target(level,state,fleet,vehicles,now);
            if(fleet.mission()!=VehicleFleet.Mission.RETURN&&!state.resupplying.containsKey(fleet.id())){
                var needy=vehicles.stream().filter(VehicleGarageService::automatic).filter(VehicleFleetSystem::needsService).map(Entity::getUUID).collect(java.util.stream.Collectors.toSet());
                boolean urgent=threat!=null&&vehicles.stream().anyMatch(v->v.distanceToSqr(threat)<=16*16);
                boolean empty=vehicles.stream().anyMatch(v->needy.contains(v.getUUID())&&VehicleSupplyRules.emptyPrimary(v.definition(),v.inventory()));
                if(!needy.isEmpty()&&(!urgent||empty)){
                    clearRoutes(state,fleet);fleet.supplyReturn(true);state.data.setDirty();state.resupplying.put(fleet.id(),Set.copyOf(departureShortages(vehicles)));state.supplyReports.put(fleet.id(),"RETURNING_FOR_SUPPLIES");
                    blocked=false;
                    if(state.deployed.values().stream().anyMatch(d->d.fleet.equals(fleet.id())))state.recalling.add(fleet.id());
                }
            }
            if(threat!=null&&!state.resupplying.containsKey(fleet.id())){
                park(vehicles);if(!blocked)status(state,fleet,VehicleFleet.Status.ACTIVE);
                for(var vehicle:vehicles)VehicleThreatBridge.engage(vehicle,threat);
                if(combatDeployment(fleet)&&!state.recalling.contains(fleet.id())){
                    if(fleet.vehicles().size()==1){deployPassengers(level,state,fleet,vehicles,threat);continue;}
                    if(blocked){status(state,fleet,VehicleFleet.Status.BLOCKED);continue;}
                    if(vehicles.size()!=fleet.vehicles().size()){status(state,fleet,VehicleFleet.Status.UNLOADED);continue;}
                    if(vehicles.stream().anyMatch(v->!driverReady(v,fleet.faction()))){status(state,fleet,VehicleFleet.Status.NO_DRIVER);continue;}
                    if(state.deployed.values().stream().anyMatch(d->d.fleet.equals(fleet.id())))continue;
                    var combat=state.combat.get(fleet.id());
                    if(combat==null){
                        clearRoutes(state,fleet);var lead=vehicles.get(0);var anchor=lead.position();var direction=threat.position().subtract(anchor);
                        float yaw=direction.horizontalDistanceSqr()<1e-4?lead.getYRot():(float)Math.toDegrees(Math.atan2(direction.x,direction.z));
                        combat=new Combat(yaw);double angle=Math.toRadians(yaw);
                        // Keep the lead at the contact; alternate fixed 14-block slots on either side.
                        for(int i=0;i<vehicles.size();i++){
                            double offset=i==0?0:14*((i+1)/2)*(i%2==0?1:-1);
                            combat.goals.put(vehicles.get(i).getUUID(),anchor.add(Math.cos(angle)*offset,0,-Math.sin(angle)*offset));
                        }
                        state.combat.put(fleet.id(),combat);
                    }
                    boolean settled=true,departing=false;double speed=vehicles.stream().mapToDouble(v->v.definition().maxSpeed()).min().orElse(.1);
                    for(var vehicle:vehicles){
                        var goal=combat.goals.get(vehicle.getUUID());float turn=Mth.wrapDegrees(combat.yaw-vehicle.getYRot());
                        boolean arrived=vehicle.position().subtract(goal).horizontalDistanceSqr()<.04;
                        if(arrived&&Math.abs(turn)<=.5)continue;
                        settled=false;if(departing)continue;departing=true;
                        var drive=state.driving.computeIfAbsent(vehicle.getUUID(),ignored->new Driving());
                        if(arrived){
                            drive.route.clear();var result=vehicle.moveVehicle(0,Mth.clamp(turn,-3,3));
                            if(result.reason()!=VehicleKinematics.StopReason.NONE)failed(state,fleet,drive,now);
                            else{drive.failures=0;drive.failureSince=-1;}
                        }else{
                            drive.destination=goal;requests.put(vehicle.getUUID(),new Request(fleet,vehicle,goal,speed));
                            if(drive.route.isEmpty())budget.enqueue(vehicle.getUUID(),now);
                        }
                    }
                    if(!settled)combat.parkedSince=-1;
                    else if(combat.parkedSince<0)combat.parkedSince=now;
                    else if(now>combat.parkedSince)deployPassengers(level,state,fleet,vehicles,threat);
                }
                continue;
            }
            if(state.combat.containsKey(fleet.id()))clearRoutes(state,fleet);
            if(combatDeployment(fleet)&&state.deployed.values().stream().anyMatch(d->d.fleet.equals(fleet.id())))state.recalling.add(fleet.id());
            if(state.recalling.contains(fleet.id())){status(state,fleet,VehicleFleet.Status.PARKED);park(vehicles);continue;}
            if(blocked){status(state,fleet,VehicleFleet.Status.BLOCKED);park(vehicles);continue;}
            if(vehicles.stream().anyMatch(v->!driverReady(v,fleet.faction()))){status(state,fleet,VehicleFleet.Status.NO_DRIVER);park(vehicles);continue;}
            var docked=returning(state,fleet)?serviceReturn(level,state,vehicles,now):Set.<UUID>of();
            if(state.resupplying.containsKey(fleet.id())&&docked.containsAll(state.resupplying.get(fleet.id()))){
                var supplied=vehicles.stream().filter(v->returnHome(state,fleet,v)).toList();
                if(supplied.stream().allMatch(VehicleFleetSystem::readyToResume)&&supplied.stream().noneMatch(v->VehicleLogisticsService.servicing(level,v))){
                    fleet.supplyReturn(false);state.data.setDirty();state.resupplying.remove(fleet.id());clearRoutes(state,fleet);state.supplyReports.put(fleet.id(),"RESUMED_ORIGINAL_ORDER");
                }else state.supplyReports.put(fleet.id(),waitingForDeparture(supplied));
                status(state,fleet,VehicleFleet.Status.PARKED);park(vehicles);continue;
            }
            if(fleet.mission()==VehicleFleet.Mission.GUARD&&!returning(state,fleet)){status(state,fleet,VehicleFleet.Status.PARKED);park(vehicles);continue;}
            var lead=vehicles.get(0);var destination=vector(fleet.target());
            var returnGoals=new HashMap<UUID,Vec3>();
            if(returning(state,fleet))for(int i=0;i<vehicles.size();i++){
                var vehicle=vehicles.get(i);
                if(returnHome(state,fleet,vehicle)){
                    if(now-state.homeChecks.getOrDefault(vehicle.getUUID(),Long.MIN_VALUE/2)>=20){
                        var selected=state.homes.get(vehicle.getUUID());state.homeChecks.put(vehicle.getUUID(),now);
                        if(selected!=null&&!VehicleGarageService.homeAvailable(level,vehicle,selected.id()))state.homes.remove(vehicle.getUUID());
                    }
                    var home=state.homes.get(vehicle.getUUID());returnGoals.put(vehicle.getUUID(),home==null?vehicle.position():Vec3.atBottomCenterOf(home.position()));
                }else{
                    double angle=Math.toRadians(lead.getYRot());returnGoals.put(vehicle.getUUID(),state.resupplying.containsKey(fleet.id())?vehicle.position():vector(fleet.returnPoint()).add(-Math.sin(angle)*i*10,0,-Math.cos(angle)*i*10));
                }
            }
            if(returning(state,fleet))destination=returnGoals.get(lead.getUUID());
            if(!returning(state,fleet)&&fleet.mission()==VehicleFleet.Mission.PATROL&&state.patrolBack.getOrDefault(fleet.id(),false))destination=vector(fleet.returnPoint());
            if(lead.position().distanceToSqr(destination)<4){
                if(!returning(state,fleet)&&fleet.mission()==VehicleFleet.Mission.PATROL){state.patrolBack.put(fleet.id(),!state.patrolBack.getOrDefault(fleet.id(),false));clearRoutes(state,fleet);destination=state.patrolBack.get(fleet.id())?vector(fleet.returnPoint()):vector(fleet.target());}
                else if(!state.resupplying.containsKey(fleet.id())&&(returning(state,fleet)?vehicles.stream().allMatch(v->(needsHome(v)?docked.contains(v.getUUID()):v.position().distanceToSqr(returnGoals.get(v.getUUID()))<4)):formationSettled(vehicles))){status(state,fleet,VehicleFleet.Status.COMPLETE);if(state.deployed.values().stream().anyMatch(d->d.fleet.equals(fleet.id())))state.recalling.add(fleet.id());park(vehicles);continue;}
            }
            double speed=vehicles.stream().mapToDouble(v->v.definition().maxSpeed()).min().orElse(.1);
            status(state,fleet,VehicleFleet.Status.ACTIVE);
            MilitaryVehicle previous=null;
            for(var vehicle:vehicles){
                Vec3 goal=destination;
                if(previous==null&&fleet.mission()!=VehicleFleet.Mission.PATROL&&!returning(state,fleet)&&vehicle.position().distanceToSqr(goal)<4){vehicle.setDeltaMovement(Vec3.ZERO);previous=vehicle;continue;}
                if(returning(state,fleet)){goal=returnGoals.get(vehicle.getUUID());if(returnHome(state,fleet,vehicle)?docked.contains(vehicle.getUUID()):vehicle.position().distanceToSqr(goal)<4){vehicle.setDeltaMovement(Vec3.ZERO);previous=vehicle;continue;}}
                else if(previous!=null){
                    double separation=vehicle.position().distanceTo(previous.position());
                    double spacing=Math.max(8,(Math.max(previous.getBoundingBox().getXsize(),previous.getBoundingBox().getZsize())+Math.max(vehicle.getBoundingBox().getXsize(),vehicle.getBoundingBox().getZsize()))/2+1);
                    if(separation<=spacing+.8){vehicle.setDeltaMovement(Vec3.ZERO);previous=vehicle;continue;}
                    Vec3 heading=previous.position().subtract(vehicle.position()).normalize();goal=previous.position().subtract(heading.scale(spacing));
                }
                var drive=state.driving.computeIfAbsent(vehicle.getUUID(),ignored->new Driving());
                if(drive.destination==null||drive.destination.distanceToSqr(goal)>16){drive.route.clear();drive.destination=goal;}
                requests.put(vehicle.getUUID(),new Request(fleet,vehicle,goal,speed));
                if(drive.route.isEmpty())budget.enqueue(vehicle.getUUID(),now);
                previous=vehicle;
            }
        }
        for(var fleet:state.data.fleets())for(var id:fleet.vehicles())if(!requests.containsKey(id))budget.cancel(id);
        for(var id:budget.drainEligible(now,requests.keySet())){
            var request=requests.get(id);if(request==null)continue;
            var drive=state.driving.get(id);
            if(returning(state,request.fleet)&&returnHome(state,request.fleet,request.vehicle)&&!state.homes.containsKey(id)){
                var candidateVehicle=request.vehicle;boolean supply=request.fleet.supplyReturn();
                var candidates=VehicleGarageService.homeCandidates(level,candidateVehicle).stream().filter(g->!supply||VehicleLogisticsService.hasResumeStock(level,candidateVehicle,g.id())).toList();
                if(candidates.isEmpty()){status(state,request.fleet,VehicleFleet.Status.PARKED);state.supplyReports.put(request.fleet.id(),"NO_LOADED_MATCHING_STOCK");continue;}
                var home=candidates.get(Math.floorMod(drive.failures,candidates.size()));
                state.supplyReports.put(request.fleet.id(),"RETURNING_FOR_SUPPLIES");
                request=new Request(request.fleet,request.vehicle,Vec3.atBottomCenterOf(home.position()),request.speed);requests.put(id,request);drive.destination=request.destination;state.homes.put(id,home);
            }
            if(returning(state,request.fleet)&&returnHome(state,request.fleet,request.vehicle)&&request.vehicle.position().distanceToSqr(request.destination)<.0025&&VehicleGarageService.parked(level,request.vehicle,state.homes.get(id).id())){
                drive.route.clear();request.vehicle.setDeltaMovement(Vec3.ZERO);continue;
            }
            var search=search(request.vehicle,request.destination,returning(state,request.fleet)&&returnHome(state,request.fleet,request.vehicle));budget.recordSearch(search.nodes);
            if(search.route.isEmpty()){if(returning(state,request.fleet)&&returnHome(state,request.fleet,request.vehicle))state.homes.remove(id);failed(state,request.fleet,drive,now);}
            else{drive.route.addAll(search.route);drive.lastPosition=request.vehicle.position();drive.lastProgress=now;}
        }
        for(var request:requests.values()){
            if(request.fleet.status()==VehicleFleet.Status.BLOCKED)continue;
            if(returning(state,request.fleet)&&request.fleet.vehicles().stream().anyMatch(id->level.getEntity(id) instanceof MilitaryVehicle v&&returnHome(state,request.fleet,v)&&!state.homes.containsKey(id))){request.vehicle.setDeltaMovement(Vec3.ZERO);status(state,request.fleet,VehicleFleet.Status.PARKED);continue;}
            var drive=state.driving.get(request.vehicle.getUUID());if(drive.route.isEmpty())continue;
            while(!drive.route.isEmpty()){
                boolean docking=returning(state,request.fleet)&&returnHome(state,request.fleet,request.vehicle)&&drive.route.size()==1&&drive.route.peekFirst().distanceToSqr(request.destination)<.01;
                if(request.vehicle.position().distanceToSqr(drive.route.peekFirst())>=(docking?.0025:.04))break;drive.route.removeFirst();
            }
            if(drive.route.isEmpty())continue;
            Vec3 delta=drive.route.peekFirst().subtract(request.vehicle.position());
            float yaw=(float)Math.toDegrees(Math.atan2(delta.x,delta.z));float turn=Mth.wrapDegrees(yaw-request.vehicle.getYRot());
            var result=request.vehicle.moveVehicle(Math.abs(turn)>8?0:Math.min(request.speed,Math.sqrt(delta.horizontalDistanceSqr())),Mth.clamp(turn,-3,3));
            if(result.reason()!=VehicleKinematics.StopReason.NONE){
                drive.route.clear();failed(state,request.fleet,drive,now);
            }else if(drive.lastPosition==null||drive.lastPosition.distanceToSqr(request.vehicle.position())>=.25){drive.lastPosition=request.vehicle.position();drive.lastProgress=now;drive.failures=0;drive.failureSince=-1;}
            else if(now-drive.lastProgress>=100){drive.route.clear();status(state,request.fleet,VehicleFleet.Status.BLOCKED);}
        }
    }
    private static void failed(Runtime state,VehicleFleet fleet,Driving drive,long now){
        if(state.resupplying.containsKey(fleet.id()))state.supplyReports.put(fleet.id(),"SUPPLY_ROUTE_BLOCKED");
        if(drive.failureSince<0)drive.failureSince=now;
        if(++drive.failures>=3||now-drive.failureSince>=100)status(state,fleet,VehicleFleet.Status.BLOCKED);
    }
    private static void park(List<MilitaryVehicle> vehicles){for(var v:vehicles)if(!occupied(v))v.setDeltaMovement(Vec3.ZERO);}
    private static Set<UUID> serviceReturn(ServerLevel level,Runtime state,List<MilitaryVehicle> vehicles,long now){
        var docked=new HashSet<UUID>();
        for(var vehicle:vehicles){
            var home=state.homes.get(vehicle.getUUID());
            if(home!=null&&serviceReady(level,vehicle)&&VehicleGarageService.registerParked(level,vehicle,home.id()))docked.add(vehicle.getUUID());
            if(now%20==0&&serviceReady(level,vehicle)&&!vehicle.currentHome().isEmpty())VehicleGarageService.requestService(level,vehicle,vehicle.currentHome());
        }
        return docked;
    }
    private static boolean formationSettled(List<MilitaryVehicle> vehicles){
        for(int i=1;i<vehicles.size();i++){var a=vehicles.get(i-1);var b=vehicles.get(i);double spacing=Math.max(8,(Math.max(a.getBoundingBox().getXsize(),a.getBoundingBox().getZsize())+Math.max(b.getBoundingBox().getXsize(),b.getBoundingBox().getZsize()))/2+1);if(a.position().distanceTo(b.position())>spacing+2)return false;}return true;
    }
    private static LivingEntity target(ServerLevel level,Runtime state,VehicleFleet fleet,List<MilitaryVehicle> vehicles,long now){
        UUID previous=state.targets.get(fleet.id());
        if(previous!=null){var entity=level.getEntity(previous);if(entity instanceof LivingEntity living){
            if(!living.isAlive()){fleet.resolvedContact();state.data.setDirty();}
            else if(vehicles.stream().anyMatch(v->VehicleThreatBridge.legalTarget(v,living)))return living;
        }state.targets.remove(fleet.id());}
        if(now-state.scans.getOrDefault(fleet.id(),Long.MIN_VALUE/2)<20)return null;
        state.scans.put(fleet.id(),now);
        for(var vehicle:vehicles){var found=VehicleThreatBridge.find(vehicle);if(found.isPresent()){state.targets.put(fleet.id(),found.get().getUUID());fleet.contact();state.data.setDirty();return found.get();}}
        return null;
    }
    private static boolean combatDeployment(VehicleFleet fleet){return fleet.mission()==VehicleFleet.Mission.CLEAR_AREA||fleet.automaticPatrol()&&fleet.ambientPatrol()&&fleet.mission()==VehicleFleet.Mission.PATROL;}
    private static void deployPassengers(ServerLevel level,Runtime state,VehicleFleet fleet,List<MilitaryVehicle> vehicles,LivingEntity target){
        for(var vehicle:vehicles)if(!occupied(vehicle))for(var passenger:List.copyOf(vehicle.getPassengers()))if(passenger instanceof MilitarySoldier soldier&&soldier.faction()==fleet.faction()&&MilitaryVehicle.crewOperational(soldier)&&soldier.commandRole()==MilitaryRole.TROOPER){
            var seat=vehicle.definition().seats().stream().filter(s->s.id().equals(vehicle.seatOf(soldier.getUUID()))).findFirst().orElse(null);
            if(seat!=null&&seat.job()==VehicleDefinition.Job.PASSENGER&&soldier.validTarget(target)&&state.deployed.size()<128){
                String order=soldier.unitId()==null?"":MilitaryOrganizationSystem.organization(level).effectiveOrder(soldier.unitId()).map(Object::toString).orElse("");
                soldier.stopRiding();state.deployed.put(soldier.getUUID(),new Deployment(fleet.id(),vehicle.getUUID(),soldier.unitId(),order,target.position()));
                var reference=state.deploymentGoals.get(soldier.getUUID());var goal=reference==null?null:reference.get();
                if(goal==null||!goal.controls(soldier)){goal=new VehicleThreatGoal(soldier);soldier.goalSelector.addGoal(1,goal);state.deploymentGoals.put(soldier.getUUID(),new WeakReference<>(goal));}
                soldier.setTarget(target);
            }
        }
    }
    /** Temporary deployment is a loaded runtime lease; it cannot rewrite a saved roster or home. */
    public static DeploymentView deployment(MilitarySoldier soldier){
        if(!(soldier.level() instanceof ServerLevel level))return null;
        var state=RUNTIMES.get(level);if(state==null)return null;var deployed=state.deployed.get(soldier.getUUID());if(deployed==null)return null;
        var fleet=state.data.fleet(deployed.fleet).orElse(null);var entity=level.getEntity(deployed.vehicle);
        String order=soldier.unitId()==null?"":MilitaryOrganizationSystem.organization(level).effectiveOrder(soldier.unitId()).map(Object::toString).orElse("");
        if(fleet==null||!(entity instanceof MilitaryVehicle vehicle)||vehicle.isRemoved()||vehicle.health()<=0||vehicle.faction()!=soldier.faction()
                ||occupied(vehicle)||!MilitaryVehicle.crewOperational(soldier)||soldier.isPassenger()||!Objects.equals(deployed.unit,soldier.unitId())||!deployed.originalOrder.equals(order)
                ||soldier.distanceToSqr(vehicle)>48*48||!state.commanders.containsKey(fleet.id())
                ||vehicle.getPassengers().stream().noneMatch(p->p instanceof MilitarySoldier s&&s.faction()==fleet.faction()&&MilitaryVehicle.crewOperational(s))){
            state.deployed.remove(soldier.getUUID());soldier.getNavigation().stop();soldier.setTarget(null);return null;
        }
        return new DeploymentView(vehicle,deployed.objective,state.recalling.contains(fleet.id())||state.resupplying.containsKey(fleet.id())||!combatDeployment(fleet)||fleet.status()==VehicleFleet.Status.COMPLETE||fleet.status()==VehicleFleet.Status.BLOCKED);
    }
    public static boolean deploymentAllows(MilitarySoldier soldier,Vec3 position){
        var deployment=deployment(soldier);return deployment!=null&&position!=null&&position.distanceToSqr(deployment.vehicle.position())<=32*32
                &&VehicleKinematics.loaded(soldier.level(),new net.minecraft.world.phys.AABB(soldier.position(),position).inflate(.1));
    }
    public static void finishDeployment(MilitarySoldier soldier){
        if(soldier.level() instanceof ServerLevel level){var state=RUNTIMES.get(level);if(state!=null)state.deployed.remove(soldier.getUUID());}
    }
    private static MilitaryOrder.Point point(Vec3 p){return new MilitaryOrder.Point(p.x,p.y,p.z);}
    private static Vec3 vector(MilitaryOrder.Point p){return new Vec3(p.x(),p.y(),p.z());}

    /** A* has no chunk-loading world access. Every edge checks full hull/turn and nine supports. */
    private static Search search(MilitaryVehicle vehicle,Vec3 destination,boolean docking){
        Vec3 start=vehicle.position();Vec3 direction=destination.subtract(start);double horizontal=Math.sqrt(direction.horizontalDistanceSqr());
        Vec3 goal=horizontal>88?start.add(direction.scale(88/horizontal)):destination;
        var queue=new PriorityQueue<Node>(Comparator.comparingDouble(Node::estimate).thenComparingDouble(Node::cost));
        var best=new HashMap<Cell,Double>();int heading=Math.floorMod(Math.round(vehicle.getYRot()/45),8);
        var first=new Node(0,0,Mth.floor(start.y*2),heading,0,start.distanceTo(goal),null,start);queue.add(first);best.put(new Cell(0,0,first.y,heading),0D);
        int visited=0;var shape=VehicleHullShape.from(vehicle.definition(),vehicle.turretYaw(),vehicle.gunPitch());
        while(!queue.isEmpty()&&visited<VehicleWorkBudget.MAX_NODES){
            var node=queue.poll();var cell=new Cell(node.x,node.z,node.y,node.heading);if(node.cost>best.getOrDefault(cell,Double.POSITIVE_INFINITY))continue;visited++;
            if(node.position.distanceToSqr(goal)<=16){
                Vec3 tail=node.position.distanceToSqr(goal)<=1e-8?node.position:edge(vehicle,shape,node.position,goal,node.parent==null?vehicle.getYRot():node.heading*45);
                if(!docking||tail!=null&&tail.distanceToSqr(goal)<=1e-8){
                    var points=new ArrayDeque<Vec3>();for(var current=node;current.parent!=null;current=current.parent)points.addFirst(current.position);
                    if(docking&&node.position.distanceToSqr(goal)>1e-8)points.add(goal);
                    else if(points.isEmpty()&&node.position.distanceToSqr(goal)>1e-4&&tail!=null)points.add(goal);
                    return new Search(List.copyOf(points),visited);
                }
            }
            for(int next=0;next<8;next++){
                int dx=(int)Math.round(Math.sin(Math.toRadians(next*45))),dz=(int)Math.round(Math.cos(Math.toRadians(next*45)));
                int nx=node.x+dx,nz=node.z+dz;if(nx*nx+nz*nz>(VehicleWorkBudget.SEARCH_RADIUS/ROAD_STEP)*(VehicleWorkBudget.SEARCH_RADIUS/ROAD_STEP))continue;
                var candidate=new Vec3(start.x+nx*ROAD_STEP,node.position.y,start.z+nz*ROAD_STEP);
                Vec3 at=edge(vehicle,shape,node.position,candidate,node.parent==null?vehicle.getYRot():node.heading*45);if(at==null)continue;
                double cost=node.cost+node.position.distanceTo(at)+Math.abs(Mth.wrapDegrees(next*45-node.heading*45))*.02;
                var key=new Cell(nx,nz,Mth.floor(at.y*2),next);if(cost>=best.getOrDefault(key,Double.POSITIVE_INFINITY))continue;
                best.put(key,cost);queue.add(new Node(nx,nz,key.y,next,cost,cost+at.distanceTo(goal),node,at));
            }
        }
        return new Search(List.of(),visited);
    }
    private static Vec3 edge(MilitaryVehicle vehicle,VehicleHullShape shape,Vec3 from,Vec3 to,float oldYaw){
        var delta=to.subtract(from);float yaw=(float)Math.toDegrees(Math.atan2(delta.x,delta.z));
        for(var envelope:shape.sweepRotation(local(from),oldYaw,oldYaw+Mth.wrapDegrees(yaw-oldYaw)))if(VehicleKinematics.checkEnvelope(vehicle,envelope)!=VehicleKinematics.StopReason.NONE)return null;
        Vec3 previous=from;int steps=Math.max(1,(int)Math.ceil(delta.horizontalDistance()/1));
        for(int i=1;i<=steps;i++){
            double t=(double)i/steps,x=from.x+delta.x*t,z=from.z+delta.z*t;
            double ground=ground(vehicle,x,previous.y,z,yaw);if(!Double.isFinite(ground))return null;
            var at=new Vec3(x,ground,z);var bend=ground>previous.y?new Vec3(previous.x,ground,previous.z):new Vec3(x,previous.y,z);
            for(var envelope:shape.sweepTranslation(local(previous),local(bend),yaw))if(VehicleKinematics.checkEnvelope(vehicle,envelope)!=VehicleKinematics.StopReason.NONE)return null;
            for(var envelope:shape.sweepTranslation(local(bend),local(at),yaw))if(VehicleKinematics.checkEnvelope(vehicle,envelope)!=VehicleKinematics.StopReason.NONE)return null;
            previous=at;
        }
        return previous;
    }
    private static double ground(MilitaryVehicle vehicle,double x,double y,double z,float yaw){
        var level=(ServerLevel)vehicle.level();var view=new RoadView(level);double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        double angle=Math.toRadians(yaw),sin=Math.sin(angle),cos=Math.cos(angle);
        for(double dx:new double[]{-vehicle.definition().width()/2+1e-5,0,vehicle.definition().width()/2-1e-5})
            for(double dz:new double[]{-vehicle.definition().length()/2+1e-5,0,vehicle.definition().length()/2-1e-5}){
                double px=x+dx*cos+dz*sin,pz=z+dz*cos-dx*sin;var chunk=level.getChunkSource().getChunkNow(Mth.floor(px)>>4,Mth.floor(pz)>>4);if(chunk==null)return Double.NaN;
                double top=Double.NEGATIVE_INFINITY;int bx=Mth.floor(px),bz=Mth.floor(pz);
                for(int by=Mth.floor(y-1)-1;by<=Mth.floor(y+1);by++){
                    var position=new BlockPos(bx,by,bz);var state=chunk.getBlockState(position);if(!state.getFluidState().isEmpty())return Double.NaN;
                    for(var box:state.getCollisionShape(view,position).toAabbs()){
                        double height=by+box.maxY;if(height<y-1-1e-5||height>y+1+1e-5)continue;
                        if(px>=bx+box.minX-1e-5&&px<=bx+box.maxX+1e-5&&pz>=bz+box.minZ-1e-5&&pz<=bz+box.maxZ+1e-5)top=Math.max(top,height);
                    }
                }
                if(view.unknownRead)return Double.NaN;
                if(!Double.isFinite(top))return Double.NaN;min=Math.min(min,top);max=Math.max(max,top);
            }
        return max-min<=1+1e-5?max:Double.NaN;
    }
    private static final class RoadView implements net.minecraft.world.level.BlockGetter {
        private final ServerLevel level;boolean unknownRead;
        RoadView(ServerLevel level){this.level=level;}
        @Override public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos position){
            if(level.isOutsideBuildHeight(position))return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            var chunk=level.getChunkSource().getChunkNow(position.getX()>>4,position.getZ()>>4);
            if(chunk==null){unknownRead=true;return net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState();}return chunk.getBlockState(position);
        }
        @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos position){return getBlockState(position).getFluidState();}
        @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos position){
            var chunk=level.getChunkSource().getChunkNow(position.getX()>>4,position.getZ()>>4);
            if(chunk==null){unknownRead=true;return null;}return chunk.getBlockEntity(position,net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK);
        }
        @Override public int getHeight(){return level.getHeight();}
        @Override public int getMinBuildHeight(){return level.getMinBuildHeight();}
    }
    private static VehicleDefinition.Vec3 local(Vec3 p){return new VehicleDefinition.Vec3(p.x,p.y,p.z);}
    private VehicleFleetSystem(){}
}
