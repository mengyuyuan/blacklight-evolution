package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.city.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.military.MilitaryRole;
import com.codex.blacklight.military.vehicle.*;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStoppingEvent;
/** Opt-in normal-server two-PID audit. JSON checkpoints are expectations until verify says PASS. */
@EventBusSubscriber(modid="blacklight",value=net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER)
public final class VehicleLogisticsServerAudit {
    private enum Stage { MUSTER, DISPATCH, OFFICER, CARGO, STOP_PENDING, RELOAD, RESUME, DONE }
    private static final String LOADER="neoforge-1.21.1";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(VehicleLogisticsServerAudit.class);
    private static Stage stage;private static boolean verifying;private static int begun,readyAt=-1;
    private static VehicleGarageDispatchGameTest.Fixture fixture;
    private static JsonObject report;private static Path evidence;
    private static UUID fleetId,cargoId,carrierId;private static MilitarySoldier officer;private static Goal officerWalk;

    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!Boolean.getBoolean("blacklight.vehicleLogisticsAudit")||stage==Stage.DONE)return;
        var server=event.getServer();
        try{if(stage==null)initialize(server);poll(server);}catch(Throwable failure){fail(server,failure);}
    }
    private static void initialize(MinecraftServer server)throws Exception{
        check(server.isDedicatedServer()&&server.isSameThread(),"Use a normal dedicated server on its actual server thread");
        String phase=System.getProperty("blacklight.vehicleLogisticsAudit.phase","");check(phase.equals("prepare")||phase.equals("verify"),"Choose prepare or verify");verifying=phase.equals("verify");
        evidence=Path.of(System.getProperty("blacklight.vehicleLogisticsAudit.file","vehicle-logistics-audit.json")).toAbsolutePath().normalize();begun=server.getTickCount();
        var level=server.getLevel(Level.OVERWORLD);check(level!=null,"Missing overworld");
        if(!verifying){
            check(!Files.exists(evidence),"Use a fresh audit directory; preserve old evidence");report=new JsonObject();
            report.addProperty("schemaVersion",1);report.addProperty("loader",LOADER);report.addProperty("preparePid",ProcessHandle.current().pid());report.addProperty("seed",level.getSeed());report.addProperty("startedUtc",Instant.now().toString());
            report.addProperty("status","PREPARING_REAL_NATIVE_CARGO");fixture=VehicleGarageDispatchGameTest.Fixture.create(level,492000,false);
            report.addProperty("fixtureX",fixture.x);report.addProperty("base",fixture.baseId);stage=Stage.MUSTER;write();
        }else{
            report=JsonParser.parseString(Files.readString(evidence,StandardCharsets.UTF_8)).getAsJsonObject();
            check(report.get("schemaVersion").getAsInt()==1&&report.get("status").getAsString().equals("PREPARE_STOP_OBSERVED"),"Prepare must observe real server-stop cargo release before verify");
            check(report.get("loader").getAsString().equals(LOADER)&&report.get("seed").getAsLong()==level.getSeed(),"Wrong loader or world");
            check(report.get("preparePid").getAsLong()!=ProcessHandle.current().pid(),"Verify must be a second OS process");
            fixture=VehicleGarageDispatchGameTest.Fixture.reopen(level,report.get("fixtureX").getAsInt(),false,report.get("base").getAsString(),report.get("garage").getAsString());
            for(var entry:report.getAsJsonObject("vehicles").entrySet())fixture.vehicles.put(entry.getKey(),UUID.fromString(entry.getValue().getAsString()));
            fleetId=UUID.fromString(report.get("fleet").getAsString());cargoId=UUID.fromString(report.get("cargoUuid").getAsString());carrierId=UUID.fromString(report.get("carrierUuid").getAsString());
            report.addProperty("verifyPid",ProcessHandle.current().pid());stage=Stage.RELOAD;
        }
    }
    private static void poll(MinecraftServer server)throws Exception{
        int elapsed=server.getTickCount()-begun;check(elapsed<(verifying?2200:5000),"Audit timed out at "+stage);
        for(int i=0;i<256&&fixture.level.getChunkSource().pollTask();i++) { }
        if(elapsed%200==0){
            LOG.info("LOGISTICS_TWO_PID_QA phase={} stage={} elapsed={}",verifying?"verify":"prepare",stage,elapsed);
            if(stage==Stage.OFFICER&&officerWalk instanceof OfficerWalk walk)LOG.info("LOGISTICS_TWO_PID_QA officer={} next={} remaining={} goals={}",officer.position(),walk.route.peek(),walk.route.size(),officer.goalSelector.getAvailableGoals().stream().filter(g->g.isRunning()).map(g->g.getPriority()+":"+g.getGoal().getClass().getSimpleName()).toList());
        }
        switch(stage){
            case MUSTER -> {
                if(fixture.muster(elapsed)){fixture.attach();report.addProperty("garage",fixture.garageId);report.add("people",peopleIds());stage=Stage.DISPATCH;}
            }
            case DISPATCH -> {
                fixture.observeWalking();if(!fixture.dispatched())return;fixture.verifyDispatch();
                officer=fixture.people.values().stream().filter(s->s.commandRole()==MilitaryRole.OFFICER&&!s.isPassenger()).findFirst().orElseThrow();
                officerWalk=new OfficerWalk(officer,fixture.vehicle("tank"));officer.goalSelector.addGoal(1,officerWalk);stage=Stage.OFFICER;
            }
            case OFFICER -> {
                var tank=fixture.vehicle("tank");if(officer.distanceToSqr(tank)>9)return;
                check(officer.startRiding(tank),"Existing native officer could not board the actual commander seat");officer.goalSelector.removeGoal(officerWalk);officer.getNavigation().stop();prepareCargo();stage=Stage.CARGO;
            }
            case CARGO -> {
                var tank=fixture.vehicle("tank");
                for(var soldier:fixture.people.values()){
                    var item=VehicleLogisticsService.carried(soldier);if(item==null||item.getItem().getCount()!=2||tank.inventory().ammunition("tank_he_shell")!=8||fixture.level.getEntity(item.getUUID())!=item||item.tickCount==0)continue;
                    check(kind(item.getItem()).equals("blacklight:tank_he_shell")&&source().isEmpty(),"Capture must follow the second actual box debit");
                    check(!VehicleSupplyRules.needsService(tank.definition(),tank.inventory(),tank.health())&&!VehicleSupplyRules.readyToResume(tank.definition(),tank.inventory(),tank.health()),"Capture must be above trigger and below departure watermark");
                    check(fleet().supplyReturn()&&soldier.distanceToSqr(Vec3.atCenterOf(fixture.garageOrigin.offset(15,1,2)))<=10,"Real return/carrier must be at the authenticated source");
                    cargoId=item.getUUID();carrierId=soldier.getUUID();report.addProperty("cargoUuid",cargoId.toString());report.addProperty("carrierUuid",carrierId.toString());
                    report.add("cargoStack",stack(item.getItem()));report.add("cargoPosition",point(item.position()));report.add("vehicleAtStop",vehicle(tank));report.add("order",order());
                    report.addProperty("sourceAfterDebit",sourceCount());report.addProperty("inFlightCount",item.getItem().getCount());report.addProperty("vehicleAdded",tank.inventory().ammunition("tank_he_shell"));
                    check(sourceCount()+item.getItem().getCount()+tank.inventory().ammunition("tank_he_shell")==10,"Selected ten source shells must be conserved before saving");
                    var marker=fixture.garageOrigin.offset(-6,1,30);fixture.level.setBlock(marker,Blocks.DIAMOND_BLOCK.defaultBlockState(),2);report.add("marker",point(Vec3.atLowerCornerOf(marker)));
                    report.addProperty("status","PREPARE_STOP_PENDING");report.addProperty("model","normal stop releases the original physical bundle locally; no virtual cargo restoration");write();
                    stage=Stage.STOP_PENDING;fixture.restoreSettings();server.halt(false);return;
                }
            }
            case RELOAD -> {
                if(!loaded()){check(elapsed<600,"Saved native people, vehicle or original cargo did not load");return;}
                if(readyAt<0)readyAt=elapsed;if(elapsed-readyAt<80)return;
                var tank=fixture.vehicle("tank");var item=(ItemEntity)fixture.level.getEntity(cargoId);
                check(vehicle(tank).equals(report.getAsJsonObject("vehicleAtStop")),"Vehicle identity/legacy fuel/real inventory changed across processes");
                check(order().equals(report.getAsJsonObject("order"))&&fleet().supplyReturn(),"Original mission/target or partial-return latch changed across processes");
                check(peopleIds().equals(report.getAsJsonArray("people"))&&contents(stash()).equals(report.getAsJsonArray("stash")),"Native UUIDs or relocated finite source stacks changed");
                check(stack(item.getItem()).equals(report.getAsJsonObject("cargoStack"))&&!item.getPersistentData().contains(VehicleLogisticsService.CARGO_TAG)&&!item.isNoGravity(),"The original released item must persist as ordinary local stock");
                check(!((MilitarySoldier)fixture.level.getEntity(carrierId)).getPersistentData().contains(VehicleLogisticsService.JOB_TAG),"Stopped worker must not retain a duplicate cargo binding");
                check(item.position().distanceToSqr(readPoint(report.getAsJsonArray("cargoPosition")))<=16,"Saved cargo moved remotely");
                check(looseShells()==2&&sourceCount()==0&&tank.inventory().ammunition("tank_he_shell")==8,"Restart duplicated, swallowed or remotely delivered source shells");
                check(fixture.level.getBlockState(BlockPos.containing(readPoint(report.getAsJsonArray("marker")))).is(Blocks.DIAMOND_BLOCK),"Saved world marker changed or fixture was rebuilt");
                report.addProperty("dropVerifiedInSecondPid",true);report.addProperty("loadedLooseShells",looseShells());
                check(item.position().distanceToSqr(Vec3.atCenterOf(fixture.garageOrigin.offset(15,1,2)))<=16,"Recover only the actual nearby dropped bundle");
                // QA models a local pickup/deposit of the same stack, after durability has been checked.
                var original=item.getItem();source().setItem(0,original);item.setItem(ItemStack.EMPTY);item.discard();source().setChanged();
                report.addProperty("recoveredOriginalCount",original.getCount());report.addProperty("status","VERIFYING_REAL_RECOVERY_AND_ORIGINAL_MISSION");write();stage=Stage.RESUME;
            }
            case RESUME -> {
                var tank=fixture.vehicle("tank");check(order().equals(report.getAsJsonObject("order")),"Supply recovery replaced the original order");
                check(tank.fuelTicks()==137,"Fuel-free service/driving consumed the dormant saved fuel");
                if(tank.inventory().ammunition("tank_he_shell")!=10||fleet().supplyReturn()||tank.getZ()<=fixture.garageOrigin.getZ()+14.5)return;
                check(sourceCount()==0&&looseShells()==0&&contents(stash()).equals(report.getAsJsonArray("stash")),"Recovery must use only the original two dropped shells");
                report.addProperty("status","PASS");report.addProperty("originalMissionResumed",true);report.addProperty("finalSelectedShellCount",10);report.add("finalVehicle",vehicle(tank));report.addProperty("verifiedUtc",Instant.now().toString());write();
                LOG.info("LOGISTICS_TWO_PID_QA PASS preparePid={} verifyPid={} evidence={}",report.get("preparePid"),report.get("verifyPid"),evidence);fixture.restoreSettings();stage=Stage.DONE;server.halt(false);
            }
            default -> { }
        }
    }
    private static void prepareCargo(){
        var tank=fixture.vehicle("tank");var source=source();ItemStack ten=ItemStack.EMPTY;
        for(int slot=0;slot<source.getContainerSize();slot++)if(kind(source.getItem(slot)).equals("blacklight:tank_he_shell")&&source.getItem(slot).getCount()>=10){ten=source.removeItem(slot,10);break;}
        check(ten.getCount()==10,"Actual generated garage must contain ten finite HE shells");
        for(int slot=0;slot<source.getContainerSize();slot++){
            var original=source.removeItemNoUpdate(slot);if(original.isEmpty())continue;int empty=-1;
            for(int at=0;at<stash().getContainerSize();at++)if(stash().getItem(at).isEmpty()){empty=at;break;}
            check(empty>=0,"Real truck box lacks room for the remaining original source stacks");stash().setItem(empty,original);
        }
        source.setItem(0,ten);source.setChanged();stash().setChanged();report.add("stash",contents(stash()));report.addProperty("selectedSourceShells",10);
        long time=fixture.level.getGameTime();
        for(var weapon:tank.definition().weapons().values())if(VehicleSupplyRules.primaryAmmunition(tank.definition()).contains(weapon.ammunitionId())){
            while(tank.inventory().ammunition(weapon.ammunitionId())>0){check(tank.inventory().tryFire(weapon.id(),time),"Finite fixture primary debit failed");time+=weapon.cooldownTicks()+1;}
        }
        check(tank.inventory().addFuel(137)==137,"Fresh native vehicle must have zero active fuel before the explicit legacy-state fixture");tank.syncVehicleState();
        fleetId=VehicleFleetSystem.create(fixture.level,List.of(tank),officer.getUUID()).orElseThrow();
        check(VehicleFleetSystem.setMission(fixture.level,fleetId,"MOVE",fixture.garageOrigin.offset(15,1,44)),"Native fleet rejected the original audit MOVE");report.addProperty("fleet",fleetId.toString());
        var vehicles=new JsonObject();fixture.vehicles.forEach((kind,id)->vehicles.addProperty(kind,id.toString()));report.add("vehicles",vehicles);
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void stopping(ServerStoppingEvent event){
        if(!Boolean.getBoolean("blacklight.vehicleLogisticsAudit")||stage!=Stage.STOP_PENDING)return;
        try{
            var item=fixture.level.getEntity(cargoId);check(item instanceof ItemEntity,"Original cargo must still be a real entity at normal stopping");var cargo=(ItemEntity)item;
            check(!cargo.getPersistentData().contains(VehicleLogisticsService.CARGO_TAG)&&!cargo.isNoGravity(),"Production server-stop cleanup must release the original cargo locally");
            check(!((MilitarySoldier)fixture.level.getEntity(carrierId)).getPersistentData().contains(VehicleLogisticsService.JOB_TAG),"Normal stop must clear the worker binding");
            check(stack(cargo.getItem()).equals(report.getAsJsonObject("cargoStack"))&&looseShells()==2&&sourceCount()==0,"Normal stop duplicated or swallowed cargo");
            report.addProperty("prepareStopObserved",true);report.addProperty("status","PREPARE_STOP_OBSERVED");report.addProperty("stoppedUtc",Instant.now().toString());write();stage=Stage.DONE;
            LOG.info("LOGISTICS_TWO_PID_QA PREPARE_STOP_OBSERVED {}; run verify in a new process using this same directory",evidence);
        }catch(Throwable failure){fail(event.getServer(),failure);}
    }
    private static boolean loaded(){
        if(!(fixture.level.getEntity(cargoId) instanceof ItemEntity))return false;
        for(UUID id:fixture.vehicles.values())if(!(fixture.level.getEntity(id) instanceof MilitaryVehicle))return false;
        for(UUID id:fixture.slots())if(!(fixture.level.getEntity(id) instanceof MilitarySoldier soldier))return false;else fixture.people.put(id,soldier);
        return true;
    }
    private static Container source(){return (Container)fixture.level.getBlockEntity(fixture.garageOrigin.offset(15,1,2));}
    private static Container stash(){return (Container)fixture.level.getBlockEntity(fixture.garageOrigin.offset(5,1,2));}
    private static int sourceCount(){int count=0;for(int i=0;i<source().getContainerSize();i++)if(kind(source().getItem(i)).equals("blacklight:tank_he_shell"))count+=source().getItem(i).getCount();return count;}
    private static int looseShells(){int count=0;for(var item:fixture.level.getEntitiesOfClass(ItemEntity.class,new AABB(fixture.garageOrigin).inflate(128)))if(kind(item.getItem()).equals("blacklight:tank_he_shell"))count+=item.getItem().getCount();return count;}
    private static String kind(ItemStack stack){return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();}
    private static JsonObject stack(ItemStack stack){var result=new JsonObject();result.addProperty("item",kind(stack));result.addProperty("count",stack.getCount());result.addProperty("nbt",stack.save(fixture.level.registryAccess()).toString());return result;}
    private static JsonArray contents(Container box){var result=new JsonArray();for(int i=0;i<box.getContainerSize();i++)result.add(box.getItem(i).isEmpty()?JsonNull.INSTANCE:stack(box.getItem(i)));return result;}
    private static JsonArray peopleIds(){var result=new JsonArray();for(UUID id:fixture.slots())result.add(String.valueOf(id));return result;}
    private static VehicleFleet fleet(){return VehicleFleetSystem.fleets(fixture.level).stream().filter(f->f.id().equals(fleetId)).findFirst().orElseThrow();}
    private static JsonObject order(){var fleet=fleet();var result=new JsonObject();result.addProperty("mission",fleet.mission().name());result.add("target",point(new Vec3(fleet.target().x(),fleet.target().y(),fleet.target().z())));return result;}
    private static JsonObject vehicle(MilitaryVehicle vehicle){var result=new JsonObject();result.addProperty("uuid",vehicle.getUUID().toString());result.addProperty("health",vehicle.health());result.addProperty("origin",vehicle.nativeOrigin());result.addProperty("home",vehicle.currentHome());result.add("inventory",JSON.toJsonTree(vehicle.inventory().snapshot()));return result;}
    private static JsonArray point(Vec3 p){var result=new JsonArray();result.add(p.x);result.add(p.y);result.add(p.z);return result;}
    private static Vec3 readPoint(JsonArray value){return new Vec3(value.get(0).getAsDouble(),value.get(1).getAsDouble(),value.get(2).getAsDouble());}
    private static void check(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
    private static void write()throws Exception{Files.writeString(evidence,JSON.toJson(report)+"\n",StandardCharsets.UTF_8);}
    private static void fail(MinecraftServer server,Throwable failure){
        LOG.error("LOGISTICS_TWO_PID_QA FAIL stage={}",stage,failure);
        try{if(report!=null){report.addProperty("status","FAIL");report.addProperty("failure",failure.toString());write();}}catch(Exception secondary){LOG.error("LOGISTICS_TWO_PID_QA evidence write failed",secondary);}
        if(fixture!=null)fixture.restoreSettings();stage=Stage.DONE;server.halt(false);
    }
    private static final class OfficerWalk extends Goal {
        final MilitarySoldier soldier;final Deque<Vec3> route=new ArrayDeque<>();int ticks;
        OfficerWalk(MilitarySoldier soldier,MilitaryVehicle tank){
            this.soldier=soldier;var apron=Vec3.atCenterOf(fixture.garageOrigin).add(15,.5,32);
            route.addAll(MilitaryBaseAccess.approach(soldier,apron));route.add(apron.add(2.8,0,0));
            route.add(tank.position().add(2.8,0,0));setFlags(EnumSet.of(Flag.MOVE));
        }
        @Override public boolean canUse(){return soldier.tacticsAvailable()&&soldier.getTarget()==null;}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){while(!route.isEmpty()&&soldier.distanceToSqr(route.peek())<(route.size()==1?.04:1))route.remove();if(route.isEmpty())return;
            if(route.size()==1&&soldier.distanceToSqr(route.peek())<1){soldier.getNavigation().stop();var p=route.peek();soldier.getMoveControl().setWantedPosition(p.x,p.y,p.z,1);}
            else if(ticks++%10==0)BlackwatchSquadSystem.navigate(soldier,route.peek(),1);
        }
        @Override public void stop(){soldier.getNavigation().stop();}
    }
    private VehicleLogisticsServerAudit() { }
}
