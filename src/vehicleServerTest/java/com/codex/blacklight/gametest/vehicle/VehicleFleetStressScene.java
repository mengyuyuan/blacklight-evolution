package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.*;
import com.codex.blacklight.military.vehicle.*;
import com.codex.blacklight.registry.ModEntities;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.LoggerFactory;

/** Disposable normal-server scene. Only prepare() may pave/force/spawn; sampling uses production AI. */
@EventBusSubscriber(modid="blacklight",value=net.neoforged.api.distmarker.Dist.DEDICATED_SERVER)
public final class VehicleFleetStressScene {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final org.slf4j.Logger LOG=LoggerFactory.getLogger(VehicleFleetStressScene.class);
    private static final int X=320000,Y=80,FOOT_X=X+136;
    private record VehicleEntry(UUID vehicle,UUID driver,UUID officer,UUID fleet,Vec3 origin,long fuel,Map<String,Integer> ammo){}
    private static final List<VehicleEntry> vehicles=new ArrayList<>();
    private static final Map<UUID,Vec3> infantry=new LinkedHashMap<>();
    private static final Set<UUID> movedFoot=new HashSet<>(),movedVehicles=new HashSet<>();
    private static final Map<UUID,Integer> firstMoved=new LinkedHashMap<>();
    private static final Map<UUID,Vec3> intervalOrigins=new HashMap<>();
    private static final Map<UUID,Integer> movingWindows=new LinkedHashMap<>();
    private static final Map<String,Set<Long>> forced=new LinkedHashMap<>();
    private static MinecraftServer server;
    private static ServerLevel level;
    private static JsonObject report;
    private static Path sceneFile,measurementFile;
    private static int tier,preparedAt=-1,lastWindow;
    private static boolean done;

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void tick(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("blacklight.vehicleFleetStressScene")||done)return;
        server=event.getServer();
        try{
            if(preparedAt<0)prepare();
            else poll();
        }catch(Throwable failure){fail(failure);}
    }
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void prepare()throws Exception{
        check(server instanceof net.minecraft.server.dedicated.DedicatedServer&&server.isSameThread(),"Requires a normal DedicatedServer, not accelerated GameTestServer");
        tier=Integer.getInteger("blacklight.vehicleFleetStress.vehicles",0);check(tier==3||tier==12||tier==24,"Choose exactly 3,12,24 real vehicles");
        check(Boolean.getBoolean("blacklight.vehicleFleetStress"),"The unchanged read-only observer must be enabled");
        level=Objects.requireNonNull(server.getLevel(Level.OVERWORLD));
        measurementFile=Path.of(System.getProperty("blacklight.vehicleFleetStress.file")).toAbsolutePath().normalize();
        sceneFile=Path.of(System.getProperty("blacklight.vehicleFleetStress.sceneFile")).toAbsolutePath().normalize();
        check(!Files.exists(sceneFile)&&!Files.exists(measurementFile),"Use a fresh tier world and preserve old evidence");
        check(level.getEntitiesOfClass(MilitaryVehicle.class,new net.minecraft.world.phys.AABB(X-256,Y-16,-256,X+256,Y+48,256)).isEmpty(),"Fresh isolated scene already contains a chassis");
        report=new JsonObject();report.addProperty("schemaVersion",1);report.addProperty("loader","neoforge-1.21.1");report.addProperty("pid",ProcessHandle.current().pid());
        report.addProperty("actualGenerator",level.getChunkSource().getGenerator().getClass().getName());report.addProperty("worldSeed",level.getSeed());report.addProperty("actualServerClass",server.getClass().getName());
        check(level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource,"The actual overworld generator must be flat");
        report.addProperty("startedUtc",Instant.now().toString());report.addProperty("status","PREPARING_DISPOSABLE_QA_SCENE");report.addProperty("tier",tier);
        report.addProperty("artificialManualScenario",true);report.addProperty("manual24ExceedsDefaultAutomatic12",tier==24);report.addProperty("productionAutomaticCapModified",false);
        report.addProperty("roadCount",24);report.addProperty("roadWidth",9);report.addProperty("roadLength",96);report.addProperty("turningPadWidth",21);
        report.addProperty("patrolLegBlocks",72);report.addProperty("sceneCenterX",X);report.addProperty("floorY",Y);
        report.addProperty("resourceIssue","QA once: real inventory.addFuel/addAmmunition; no subsequent refresh, garage or repair");write();
        // Setup-only forcing is explicit test preparation, never a production navigation operation.
        for(int cx=(X-128)>>4;cx<=(X+208)>>4;cx++)for(int cz=-18;cz<=18;cz++)level.setChunkForced(cx,cz,true);
        for(int lane=0;lane<24;lane++){
            int x=X+(lane%6)*32-80,z=(lane/6)*128-192;
            floor(x-4,x+4,z-48,z+47);
            floor(x-10,x+10,z-46,z-26);floor(x-10,x+10,z+26,z+46);
        }
        floor(FOOT_X-32,FOOT_X+32,-32,32);
        for(int i=0;i<tier;i++)spawnVehicle(i);
        spawnInfantry();
        MilitaryOrganizationSystem.data(level).setDirty();
        for(var dimension:server.getAllLevels())forced.put(dimension.dimension().location().toString(),Set.copyOf(dimension.getForcedChunks()));
        preparedAt=lastWindow=server.getTickCount();
        vehicles.forEach(entry->intervalOrigins.put(entry.vehicle(),entry.origin()));
        report.addProperty("preparedAtTick",preparedAt);report.addProperty("totalCrew",tier*2);report.addProperty("footInfantry",infantry.size());report.addProperty("totalActualVehicles",vehicles.size());
        report.addProperty("status","PRODUCTION_PATROL_RUNNING_OBSERVER_WARMUP");
        report.add("forcedAtSetupBoundary",JSON.toJsonTree(forced));
        var ids=new JsonArray();vehicles.forEach(entry->{var item=new JsonObject();item.addProperty("vehicle",entry.vehicle().toString());item.addProperty("driver",entry.driver().toString());item.addProperty("officer",entry.officer().toString());item.addProperty("fleet",entry.fleet().toString());item.addProperty("fuelOnce",entry.fuel());item.add("ammunitionOnce",JSON.toJsonTree(entry.ammo()));ids.add(item);});report.add("vehicles",ids);
        write();LOG.info("VEHICLE_FLEET_STRESS_SCENE prepared tier={} vehicles={} realFoot={} setupForcedChunks={}",tier,vehicles.size(),infantry.size(),level.getForcedChunks().size());
    }
    private static void floor(int minX,int maxX,int minZ,int maxZ){
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
            level.setBlock(new BlockPos(x,Y,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=Y+1;y<=Y+9;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
    }
    private static MilitarySoldier person(boolean officer,Vec3 position){
        MilitarySoldier person=officer?ModEntities.MILITARY_OFFICER.get().create(level):ModEntities.MILITARY_SOLDIER.get().create(level);
        check(person!=null,"Actual troop entity type missing");person.setAutomaticUnitJoin(false);person.setPersistenceRequired();person.setPos(position);person.initializeWeapon();
        check(!person.isNoAi(),"QA must never disable the real troop AI");return person;
    }
    private static void add(Entity entity){check(level.addFreshEntity(entity),"Real entity admission rejected: "+entity.getType());}
    private static void spawnVehicle(int index){
        int x=X+(index%6)*32-80,z=(index/6)*128-192;
        MilitaryVehicle vehicle=switch(index%3){case 0->ModEntities.MILITARY_TRUCK.get().create(level);case 1->ModEntities.MILITARY_APC.get().create(level);default->ModEntities.MILITARY_TANK.get().create(level);};
        check(vehicle!=null,"Actual chassis entity type missing");vehicle.setPos(x+.5,Y+1,z-36+.5);vehicle.setYRot(0);add(vehicle);
        vehicle.inventory().addFuel(vehicle.definition().maxFuelTicks());
        vehicle.definition().ammunitionCapacities().forEach((id,capacity)->vehicle.inventory().addAmmunition(id,capacity));
        var driver=person(false,vehicle.position());var officer=person(true,vehicle.position());add(driver);add(officer);
        check(driver.startRiding(vehicle)&&officer.startRiding(vehicle),"Actual driver and actual officer must enter real unforced seats");
        check(vehicle.occupant(VehicleDefinition.Job.DRIVER)==driver,"First real seat must be the actual driver");
        var fleet=VehicleFleetSystem.create(level,List.of(vehicle),officer.getUUID()).orElseThrow(()->new IllegalStateException("Real fleet creation rejected its mounted officer"));
        check(VehicleFleetSystem.setMission(level,fleet,"PATROL",new BlockPos(x,Y+1,z+36)),"Production PATROL order rejected");
        var stock=vehicle.inventory().snapshot();vehicles.add(new VehicleEntry(vehicle.getUUID(),driver.getUUID(),officer.getUUID(),fleet,vehicle.position(),stock.fuelTicks(),stock.ammunition()));
    }
    private static void spawnInfantry(){
        var org=MilitaryOrganizationSystem.organization(level);var unit=UUID.randomUUID();var anchor=new MilitaryOrder.Point(FOOT_X+.5,Y+1,.5);
        check(org.create(unit,MilitaryFaction.MILITARY,MilitaryUnitType.TACTICAL_SQUAD,"fleet-stress-real-foot",12,anchor),"Create the original personnel roster through production organization");
        MilitarySoldier captain=null;
        for(int i=0;i<12;i++){
            var soldier=person(i==0,new Vec3(FOOT_X+(i%4)*2-3,Y+1,(i/4)*2-2));
            if(i==0)captain=soldier;
            check(org.assign(unit,soldier.getUUID(),soldier.rank()),"Assign existing real infantry to personnel roster");soldier.setUnitId(unit);add(soldier);infantry.put(soldier.getUUID(),soldier.position());
        }
        check(org.appoint(unit,Objects.requireNonNull(captain).getUUID()),"Actual infantry officer appointment rejected");
        check(org.order(unit,new MilitaryOrder(MilitaryOrder.Mode.PATROL,null)),"Original personnel PATROL intent rejected");
        MilitaryOrganizationSystem.data(level).setCommanderManual(unit,true);
    }
    private static void poll()throws Exception{
        int now=server.getTickCount();
        check(now-preparedAt<=Integer.getInteger("blacklight.vehicleFleetStress.ticks",600)+1200,"Read-only observer did not finish within bounded scene lifetime");
        for(var dimension:server.getAllLevels()){
            var before=forced.get(dimension.dimension().location().toString());
            check(before!=null&&before.equals(dimension.getForcedChunks()),"Business code changed forced chunks after setup in "+dimension.dimension().location());
        }
        for(var entry:vehicles){
            check(level.getEntity(entry.vehicle()) instanceof MilitaryVehicle,"A real stress chassis unloaded or disappeared");
            var vehicle=(MilitaryVehicle)level.getEntity(entry.vehicle());
            check(vehicle.health()>0&&vehicle.fuelTicks()==entry.fuel(),"Vehicle health or dormant legacy fuel changed: "+entry.vehicle());
            check(vehicle.inventory().snapshot().ammunition().equals(entry.ammo()),"Idle patrol cannot refresh or spend its finite real ammunition");
            check(level.getEntity(entry.driver()) instanceof MilitarySoldier&&level.getEntity(entry.officer()) instanceof MilitarySoldier,"Original real crew missing");
            var driver=(MilitarySoldier)level.getEntity(entry.driver());var officer=(MilitarySoldier)level.getEntity(entry.officer());
            check(!driver.isNoAi()&&!officer.isNoAi()&&driver.getVehicle()==vehicle&&officer.getVehicle()==vehicle&&MilitaryVehicle.crewOperational(driver)&&MilitaryVehicle.crewOperational(officer),"Real crew control state changed");
            check(vehicle.occupant(VehicleDefinition.Job.DRIVER)==driver&&Objects.equals(VehicleFleetSystem.actingCommander(level,entry.fleet()),officer.getUUID()),"Production driver or formal mounted officer lost control");
            check(VehicleFleetSystem.status(level,entry.fleet()).equals("ACTIVE"),"Production PATROL stopped: "+VehicleFleetSystem.status(level,entry.fleet()));
            if(vehicle.position().distanceToSqr(entry.origin())>1){movedVehicles.add(entry.vehicle());firstMoved.putIfAbsent(entry.vehicle(),now-preparedAt);}
            if(now-lastWindow>=100){
                if(vehicle.position().distanceToSqr(intervalOrigins.get(entry.vehicle()))>1)movingWindows.merge(entry.vehicle(),1,Integer::sum);
                intervalOrigins.put(entry.vehicle(),vehicle.position());
            }
        }
        if(now-lastWindow>=100)lastWindow=now;
        for(var entry:infantry.entrySet()){
            check(level.getEntity(entry.getKey()) instanceof MilitarySoldier,"Original foot soldier unloaded or disappeared");
            var soldier=(MilitarySoldier)level.getEntity(entry.getKey());check(soldier.tacticsAvailable()&&!soldier.isPassenger(),"Original foot infantry stopped its real AI");
            if(soldier.position().distanceToSqr(entry.getValue())>1)movedFoot.add(entry.getKey());
        }
        if(!Files.isRegularFile(measurementFile))return;
        var measurement=JsonParser.parseString(Files.readString(measurementFile,StandardCharsets.UTF_8)).getAsJsonObject();String status=measurement.get("status").getAsString();
        if(status.equals("OBSERVED_FAIL"))throw new IllegalStateException("The unchanged observer rejected real measurements: "+measurement);
        if(!status.equals("OBSERVED_PASS"))return;
        check(movedVehicles.size()==tier&&movedFoot.size()==12,"Real movement coverage incomplete: vehicles="+movedVehicles.size()+", foot="+movedFoot.size());
        check(movingWindows.size()==tier&&movingWindows.values().stream().allMatch(windows->windows>=2),"Warmup-only movement cannot establish sustained vehicle pressure");
        check(measurement.get("sampledTicks").getAsInt()>=200&&measurement.get("actualInfantryPlanDelta").getAsLong()>0,"Real sample length or original infantry budget missing");
        var end=new JsonArray();for(var entry:vehicles){var v=(MilitaryVehicle)level.getEntity(entry.vehicle());var item=new JsonObject();item.addProperty("vehicle",entry.vehicle().toString());item.addProperty("fuelStart",entry.fuel());item.addProperty("fuelEnd",v.fuelTicks());item.addProperty("fleetStatus",VehicleFleetSystem.status(level,entry.fleet()));item.addProperty("mission","PATROL");end.add(item);}
        report.add("finiteResourcesAtEnd",end);report.add("firstRealMovementLatencyTicks",JSON.toJsonTree(firstMoved));report.add("moving100TickWindows",JSON.toJsonTree(movingWindows));
        report.addProperty("footMovedOverOneBlock",movedFoot.size());report.addProperty("vehiclesMovedOverOneBlock",movedVehicles.size());report.addProperty("forcedChunksUnchangedAfterSetup",true);
        report.addProperty("finishedUtc",Instant.now().toString());report.addProperty("status","SCENE_OBSERVED_PASS");report.addProperty("measurementFile",measurementFile.toString());write();done=true;
        LOG.info("VEHICLE_FLEET_STRESS_SCENE actual tier={} PASS scene={} measurements={}",tier,sceneFile,measurementFile);server.halt(false);
    }
    private static void fail(Throwable failure){
        done=true;LOG.error("VEHICLE_FLEET_STRESS_SCENE actual failure",failure);
        if(report!=null){report.addProperty("status","SCENE_OBSERVED_FAIL");report.addProperty("error",failure.toString());report.addProperty("finishedUtc",Instant.now().toString());try{write();}catch(Exception ignored){LOG.error("Unable to write scene failure",ignored);}}
        if(server!=null)server.halt(false);
    }
    private static void write()throws Exception{if(sceneFile.getParent()!=null)Files.createDirectories(sceneFile.getParent());Files.writeString(sceneFile,JSON.toJson(report),StandardCharsets.UTF_8);}
    private VehicleFleetStressScene(){}
}
