package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.MilitaryFormationSystem;
import com.codex.blacklight.military.vehicle.VehicleFleetSystem;
import com.codex.blacklight.military.vehicle.VehicleWorkBudget;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.slf4j.LoggerFactory;

/** Opt-in observer only: an external disposable QA scene must supply the real moving fleets and infantry. */
@EventBusSubscriber(modid="blacklight",value=net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER)
public final class VehicleFleetStressAudit {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final org.slf4j.Logger LOG=LoggerFactory.getLogger(VehicleFleetStressAudit.class);
    private static final String PREFIX="blacklight.vehicleFleetStress.";
    private static final Map<UUID,Vec3> origins=new LinkedHashMap<>();
    private static final Set<UUID> movedVehicles=new HashSet<>();
    private static final List<Long> nanos=new ArrayList<>();
    private static long tickStart,startedWall,plansBefore,nodesBefore,previousPlans,peakPlans,infantryPlansBefore;
    private static int firstTick=-1,matchingAt=-1,expected,sampleTicks,minInfantry;
    private static boolean recording,done;
    private static JsonObject report;
    private static AABB area;
    private static Path output;

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void before(net.minecraftforge.event.TickEvent.ServerTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.START)return;
        if(Boolean.getBoolean("blacklight.vehicleFleetStress")&&!done)tickStart=System.nanoTime();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void after(net.minecraftforge.event.TickEvent.ServerTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!Boolean.getBoolean("blacklight.vehicleFleetStress")||done)return;
        try{sample(event.getServer(),System.nanoTime()-tickStart);}catch(Throwable failure){finishFailure(failure);}
    }
    private static int integer(String name,int fallback,int min,int max){
        int value=Integer.getInteger(PREFIX+name,fallback);
        if(value<min||value>max)throw new IllegalArgumentException(name+" outside "+min+".."+max);return value;
    }
    private static void initialize(MinecraftServer server){
        if(!(server instanceof net.minecraft.server.dedicated.DedicatedServer)||!server.isSameThread())throw new IllegalStateException("Use a normal dedicated server on its server thread");
        expected=integer("vehicles",3,3,24);if(expected!=3&&expected!=12&&expected!=24)throw new IllegalArgumentException("Choose actual 3,12,24 vehicle tier");
        sampleTicks=integer("ticks",600,200,12000);minInfantry=integer("infantry",12,1,256);
        int x=integer("x",0,-29999000,29999000),z=integer("z",0,-29999000,29999000),y=integer("y",80,-64,320),radius=integer("radius",128,16,256);
        area=new AABB(x-radius,y-16,z-radius,x+radius,y+48,z+radius);
        output=Path.of(System.getProperty(PREFIX+"file","vehicle-fleet-stress-"+expected+".json")).toAbsolutePath().normalize();
        if(Files.exists(output))throw new IllegalStateException("Preserve prior evidence; choose a fresh output path: "+output);
        firstTick=server.getTickCount();report=new JsonObject();
        report.addProperty("schemaVersion",1);report.addProperty("loader","neoforge-1.21.1");report.addProperty("pid",ProcessHandle.current().pid());report.addProperty("startedUtc",Instant.now().toString());
        report.addProperty("expectedVehicles",expected);report.addProperty("minimumFootInfantry",minInfantry);report.addProperty("requestedSampleTicks",sampleTicks);
        report.addProperty("os",System.getProperty("os.name")+" "+System.getProperty("os.arch"));report.addProperty("cpu",System.getenv("PROCESSOR_IDENTIFIER"));
        report.addProperty("logicalProcessors",Runtime.getRuntime().availableProcessors());report.addProperty("jvm",System.getProperty("java.vm.name")+" "+System.getProperty("java.version"));
        report.addProperty("maxHeapBytes",Runtime.getRuntime().maxMemory());
        report.addProperty("area",area.toString());report.addProperty("inputLatency","UNMEASURED: no real client input stream is observed");report.addProperty("mutatesWorld",false);
        report.addProperty("sameVehiclePlanInterval","UNMEASURED: public budget counters expose aggregate plans, not per-vehicle grants");
        report.addProperty("status","WAITING_FOR_EXTERNALLY_PREPARED_REAL_SCENE");write();
    }
    private static void sample(MinecraftServer server,long elapsed)throws Exception{
        if(firstTick<0)initialize(server);
        var level=server.getLevel(Level.OVERWORLD);if(level==null)throw new IllegalStateException("Overworld unavailable");
        var vehicles=level.getEntitiesOfClass(MilitaryVehicle.class,area,v->v.isAlive()&&!v.isRemoved());
        var soldiers=level.getEntitiesOfClass(MilitarySoldier.class,area,s->s.isAlive()&&!s.isRemoved());
        long foot=soldiers.stream().filter(s->!s.isPassenger()).count();
        boolean scene=vehicles.size()==expected&&foot>=minInfantry;
        if(!recording){
            if(!scene)matchingAt=-1;else if(matchingAt<0)matchingAt=server.getTickCount();
            if(!scene||server.getTickCount()-matchingAt<100){if(server.getTickCount()-firstTick>6000)throw new IllegalStateException("No stable external scene: vehicles="+vehicles.size()+", foot="+foot);return;}
            vehicles.forEach(v->origins.put(v.getUUID(),v.position()));
            var budget=VehicleFleetSystem.budget(level);previousPlans=plansBefore=budget.totalPlans();nodesBefore=budget.totalNodes();startedWall=System.nanoTime();recording=true;
            infantryPlansBefore=MilitaryFormationSystem.metrics(level).plans();
            report.addProperty("status","OBSERVING_REAL_SERVER_TICKS");report.addProperty("sampleStartTick",server.getTickCount());report.add("initialVehicles",JSON.toJsonTree(origins.keySet()));
            report.addProperty("initialTotalInfantry",soldiers.size());report.addProperty("initialFootInfantry",foot);write();return;
        }
        if(!scene||vehicles.stream().anyMatch(v->!origins.containsKey(v.getUUID())))throw new IllegalStateException("Real scene identities/count changed during sample");
        vehicles.stream().filter(v->v.position().distanceToSqr(origins.get(v.getUUID()))>1).forEach(v->movedVehicles.add(v.getUUID()));
        var budget=VehicleFleetSystem.budget(level);long delta=budget.totalPlans()-previousPlans;previousPlans=budget.totalPlans();peakPlans=Math.max(peakPlans,delta);
        if(delta<0||delta>VehicleWorkBudget.PLANS_PER_TICK||budget.maxSearchNodes()>VehicleWorkBudget.MAX_NODES)throw new IllegalStateException("Actual vehicle work budget exceeded");
        nanos.add(elapsed);if(nanos.size()<sampleTicks)return;
        long moved=movedVehicles.size();
        report.addProperty("sampleEndTick",server.getTickCount());report.addProperty("sampledTicks",nanos.size());report.addProperty("actualVehicles",vehicles.size());report.addProperty("finalFootInfantry",foot);
        report.addProperty("vehiclesMovedOverOneBlock",moved);report.addProperty("actualPlanDelta",budget.totalPlans()-plansBefore);report.addProperty("actualNodeDelta",budget.totalNodes()-nodesBefore);
        report.addProperty("peakPlansPerObservedTick",peakPlans);report.addProperty("maxActualSearchNodes",budget.maxSearchNodes());report.add("infantryBudget",JSON.toJsonTree(MilitaryFormationSystem.metrics(level)));
        long infantryPlans=MilitaryFormationSystem.metrics(level).plans()-infantryPlansBefore;report.addProperty("actualInfantryPlanDelta",infantryPlans);
        var sorted=nanos.stream().mapToLong(Long::longValue).sorted().toArray();long total=nanos.stream().mapToLong(Long::longValue).sum();
        report.addProperty("meanTickMs",total/(double)nanos.size()/1_000_000);report.addProperty("p50TickMs",sorted[(sorted.length-1)/2]/1_000_000.);
        report.addProperty("p95TickMs",sorted[(int)Math.ceil(sorted.length*.95)-1]/1_000_000.);report.addProperty("maxTickMs",sorted[sorted.length-1]/1_000_000.);
        report.addProperty("sampleWallMs",(System.nanoTime()-startedWall)/1_000_000.);
        if(moved!=expected||budget.totalPlans()==plansBefore||infantryPlans<=0)throw new IllegalStateException("Idle/stuck scene cannot establish an active fleet+infantry stress result: moved="+moved+", infantryPlans="+infantryPlans);
        report.addProperty("status","OBSERVED_PASS");report.addProperty("finishedUtc",Instant.now().toString());done=true;write();LOG.info("VEHICLE_FLEET_STRESS evidence={}",output);
    }
    private static void finishFailure(Throwable failure){
        done=true;LOG.error("VEHICLE_FLEET_STRESS failed",failure);
        if(report==null||output==null)return;report.addProperty("status","OBSERVED_FAIL");report.addProperty("error",failure.toString());
        try{write();}catch(Exception writeFailure){LOG.error("VEHICLE_FLEET_STRESS could not save evidence",writeFailure);}
    }
    private static void write(){
        try{var parent=output.getParent();if(parent!=null)Files.createDirectories(parent);Files.writeString(output,JSON.toJson(report),StandardCharsets.UTF_8);}
        catch(Exception failure){throw new IllegalStateException("Cannot write fleet stress evidence",failure);}
    }
    private VehicleFleetStressAudit(){}
}
