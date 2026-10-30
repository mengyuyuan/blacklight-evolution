package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.military.vehicle.*;
import com.codex.blacklight.registry.ModEntities;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.city.MilitaryBaseAccess;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehicleLogisticsGameTest {
    @GameTest(template="blackwatch_arena",batch="vehicle6logistics",timeoutTicks=140)
    public static void automaticGarageCannotTransferRealStockWithoutAnExistingServiceSoldier(GameTestHelper h) {
        var f=new VehicleGarageGameTest.Fixture(h,341000);
        String garage=f.attach(h);
        var vehicle=ModEntities.MILITARY_TANK.get().create(f.level);
        vehicle.setPos(f.x+15.5,81,12.5);f.level.addFreshEntity(vehicle);f.entities.add(vehicle);
        vehicle.initializeNativeOrigin(f.base,f.level.dimension().location().toString());
        h.assertTrue(VehicleGarageService.registerParked(f.level,vehicle,garage),"real stationary tank is associated with its valid bay");
        var ledger=VehicleGarageService.data(f.level);
        h.assertTrue(ledger.reserve(garage,"tank",vehicle.getUUID())&&ledger.issued(garage,"tank",vehicle.getUUID()),"fixture records the existing tank once without issuing people");
        vehicle.inventory().addFuel(137);long legacyFuel=vehicle.fuelTicks();
        var box=(Container)f.level.getBlockEntity(f.origin.offset(15,1,2));box.clearContent();
        box.setItem(0,new ItemStack(VehicleRegistry.item("tank_he_shell"),3));
        box.setItem(1,new ItemStack(VehicleRegistry.item("military_fuel_can"),2));
        h.assertTrue(com.codex.blacklight.city.MilitaryBaseAccess.idleCrew(f.level,f.base,32,java.util.Set.of()).isEmpty(),"fixture has no real idle native soldier");
        awaitTank(h,f,vehicle,()->h.runAfterDelay(25,()->{
            try(f) {
                h.assertTrue(vehicle.inventory().ammunition("tank_he_shell")==0&&box.getItem(0).getCount()==3,
                        "automatic ammunition logistics must wait for a real service soldier instead of moving HE remotely; HE="
                                +vehicle.inventory().ammunition("tank_he_shell")+" boxHE="+box.getItem(0).getCount());
                h.assertTrue(vehicle.fuelTicks()==legacyFuel&&box.getItem(1).getCount()==2,
                        "legacy fuel is inactive and must remain untouched by automatic logistics; fuel="
                                +vehicle.fuelTicks()+" expected="+legacyFuel+" boxFuel="+box.getItem(1).getCount());
            }
            h.succeed();
        }),80);
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6logistics_walk",timeoutTicks=2200)
    public static void realNativeWorkerCarriesFiniteHeApCoaxAndRepairWithoutTouchingSecondaryGuns(GameTestHelper h){new Scene(h,0).poll();}
    @GameTest(template="blackwatch_arena",batch="vehicle6logistics_cancel",timeoutTicks=2200)
    public static void cancelledCarrierDropsTheOriginalItemsLocallyWithoutRefundOrPlayerRequisition(GameTestHelper h){new Scene(h,1).poll();}
    @GameTest(template="blackwatch_arena",batch="vehicle6logistics_death",timeoutTicks=2200)
    public static void deadCarrierLeavesExactlyTheOriginalPhysicalAmmunition(GameTestHelper h){new Scene(h,2).poll();}
    @GameTest(template="blackwatch_arena",batch="vehicle6logistics_nbt",timeoutTicks=2200)
    public static void realInFlightItemNbtHandoffDeliversOnceWithoutAnotherBoxDebit(GameTestHelper h){new Scene(h,3).poll();}
    @GameTest(template="blackwatch_arena",batch="vehicle6logistics_return",timeoutTicks=2400)
    public static void lowAmmunitionNativeFleetReturnsWithoutFuelAndResumesTheSameSavedMove(GameTestHelper h){new Scene(h,4).poll();}
    @GameTest(template="blackwatch_arena",batch="vehicle6logistics_no_stock",timeoutTicks=700)
    public static void noMatchingLoadedStockParksTheNativeFleetAndPreservesItsOriginalOrder(GameTestHelper h){new Scene(h,5).poll();}
    @GameTest(template="blackwatch_arena",batch="vehicle6logistics_withdraw",timeoutTicks=700)
    public static void emptyMainGunWithdrawsFromAnUrgentLegalContactInsteadOfCharging(GameTestHelper h){new Scene(h,6).poll();}
    @GameTest(template="blackwatch_arena",batch="vehicle6logistics_damage",timeoutTicks=2400)
    public static void damagedNativeFleetConsumesRealRepairPartsThenResumesTheSameSavedMove(GameTestHelper h){new Scene(h,8).poll();}
    @GameTest(template="blackwatch_arena",batch="vehicle6logistics_partial_ammo",timeoutTicks=2400)
    public static void sixRealShellsKeepTheFleetParkedBelowDepartureWatermarkWithItsOriginalOrder(GameTestHelper h){new Scene(h,7).poll();}
    @GameTest(template="blackwatch_arena",batch="vehicle6logistics_partial_repair",timeoutTicks=2400)
    public static void twoRealRepairPartsKeepTheFleetParkedBelowDepartureHealthWithItsOriginalOrder(GameTestHelper h){new Scene(h,9).poll();}
    @GameTest(template="blackwatch_arena",batch="vehicle6logistics_departure_health",timeoutTicks=2400)
    public static void ammunitionReturnUsesRealRepairPartsToPassTheDepartureHealthCheck(GameTestHelper h){new Scene(h,10).poll();}
    @GameTest(template="blackwatch_arena",batch="vehicle6logistics_apc",timeoutTicks=140)
    public static void actualApcBayAcceptsFiniteAutocannonAndRocketsWhileHeAndFuelStayUntouched(GameTestHelper h){
        try(var f=new VehicleGarageGameTest.Fixture(h,394000)){
            String garage=f.attach(h);var apc=ModEntities.MILITARY_APC.get().create(f.level);apc.setPos(f.x+25.5,81,12.5);f.level.addFreshEntity(apc);f.entities.add(apc);
            var box=(Container)f.level.getBlockEntity(f.origin.offset(25,1,2));box.clearContent();
            box.setItem(0,new ItemStack(VehicleRegistry.item("autocannon_round"),5));box.setItem(1,new ItemStack(com.codex.blacklight.firearms.FirearmsRegistry.ammo("rocket_round"),2));
            box.setItem(2,new ItemStack(VehicleRegistry.item("tank_he_shell")));box.setItem(3,new ItemStack(VehicleRegistry.item("military_fuel_can")));
            h.assertTrue(VehicleGarageService.transfer(f.level,apc,garage)==7&&apc.inventory().ammunition("autocannon_round")==5&&apc.inventory().ammunition("rocket_round")==2,"actual APC accepts exactly its two finite ammunition kinds");
            h.assertTrue(box.getItem(2).getCount()==1&&box.getItem(3).getCount()==1&&VehicleGarageService.transfer(f.level,apc,garage)==0,"wrong HE and dormant fuel cannot be consumed or converted on repeat");
        }h.succeed();
    }

    /** The normal finite base-maintenance entry creates all personnel; this fixture creates no soldier. */
    private static final class Scene implements AutoCloseable {
        final GameTestHelper h;final int mode;final VehicleGarageDispatchGameTest.Fixture f;
        final Map<UUID,ItemStack> main=new HashMap<>(),secondary=new HashMap<>();
        MilitaryVehicle tank;Container box;UUID fleet;MilitarySoldier carrier;ItemEntity bundle;
        MilitarySoldier officer;
        Vec3 outside,last;com.codex.blacklight.military.MilitaryOrder.Point orderTarget;Entity threat;
        UUID[] nativeSlots;
        int elapsed,age,settled;boolean prepared,sawCargo,acted,closed,returned;
        Scene(GameTestHelper h,int mode){this.h=h;this.mode=mode;f=VehicleGarageDispatchGameTest.Fixture.create(h.getLevel(),352000+mode*4096,false);}
        void poll(){
            if(closed)return;
            try{
                for(int i=0;i<256&&f.level.getChunkSource().pollTask();i++) { }
                if(!prepared){
                    h.assertTrue(++elapsed<400,"real logistics garrison must become entity-ticking and muster before behavior assertions");
                    if(f.muster(elapsed)){prepare();prepared=true;logDiagnostic("PREPARED");}
                    later();return;
                }
                age++;if(age%200==0||age==1900)logDiagnostic("WAIT");
                h.assertTrue(age<1900,"real service timed out: mode="+mode+" tank="+tank.position()+" HE="+tank.inventory().ammunition("tank_he_shell")+" job="+(carrier==null?"NONE":carrier.getPersistentData().getCompound(VehicleLogisticsService.JOB_TAG))+"; see LOGISTICS_REVISION_QA");
                if(mode>=4&&fleet==null){
                    boolean ready=f.level.getEntity(tank.getUUID())==tank&&tank.tickCount>0&&f.level.isPositionEntityTicking(tank.blockPosition());
                    if(mode==6)ready&=f.level.getEntity(threat.getUUID())==threat&&threat.tickCount>0;
                    if(ready)startFleet();else h.assertTrue(age<80,"fleet fixture entities must be tracked and ticking before the return/contact behavior check");
                    later();return;
                }
                for(var soldier:f.people.values()){
                    var item=VehicleLogisticsService.carried(soldier);if(item==null)continue;
                    if(mode==0&&sawCargo)h.assertTrue(soldier==carrier,"the original worker must finish finite HE/AP/coax/repair trips before being released for vehicle recruitment");
                    h.assertTrue(f.people.get(soldier.getUUID())==soldier&&!soldier.isVehicle()&&!soldier.isPassenger(),"cargo uses the same real native worker without changing passenger/vehicle identity");
                    h.assertTrue(item.getItem().getCount()>0&&item.getItem().getCount()<=8&&item.distanceToSqr(soldier)<=16,"real bounded item bundle remains physically beside its carrier");
                    if(!sawCargo){
                        h.assertTrue(soldier.distanceToSqr(Vec3.atCenterOf(f.garageOrigin.offset(15,1,2)))<16,"first observed actual debit happens beside the certified box");
                        if(fleet!=null)h.assertTrue(tank.position().distanceToSqr(new Vec3(f.garageOrigin.getX()+15.5,81,f.garageOrigin.getZ()+12.5))<.0025,"a returning fleet must finish the certified docking leg before a worker picks up resources");
                        carrier=soldier;bundle=item;sawCargo=true;logDiagnostic("PICKUP");
                    }
                }
                if(fleet!=null){
                    var intent=VehicleFleetSystem.fleets(f.level).stream().filter(v->v.id().equals(fleet)).findFirst().orElseThrow();
                    h.assertTrue(intent.mission()==(mode==6?VehicleFleet.Mission.CLEAR_AREA:VehicleFleet.Mission.MOVE)&&intent.target().equals(orderTarget),"resource diversion must never rewrite the saved mission or target");
                    h.assertTrue(tank.position().distanceTo(last)<=tank.definition().maxSpeed()+.0001,"return/resume moves the actual hull without teleporting");last=tank.position();
                    h.assertTrue(tank.fuelTicks()==0&&VehicleFleetSystem.budget(f.level).plansThisTick()<=2,"fuel-free movement keeps old fuel exact and shares the original global planning budget");
                    if(mode==5&&age>=100){
                        h.assertTrue(tank.position().distanceToSqr(outside)<.0001&&VehicleFleetSystem.logisticsStatus(f.level,fleet).equals("NO_LOADED_MATCHING_STOCK"),"no useful loaded stock must park and report while retaining the original order");finish();return;
                    }
                    if(mode==6&&age>=100){
                        h.assertTrue(tank.getZ()<outside.z-4&&tank.distanceToSqr(threat)>outside.distanceToSqr(threat.position()),"an empty main gun must withdraw toward the depot rather than charging an urgent legal infected contact");finish();return;
                    }
                    if(VehicleGarageService.parked(f.level,tank,f.garageId))returned=true;
                    if(mode==4&&returned&&sawCargo&&tank.inventory().ammunition("tank_he_shell")==10&&VehicleFleetSystem.logisticsStatus(f.level,fleet).equals("RESUMED_ORIGINAL_ORDER")&&tank.getZ()>f.garageOrigin.getZ()+16){finish();return;}
                    if(mode==8&&returned&&sawCargo&&tank.health()==260&&VehicleFleetSystem.logisticsStatus(f.level,fleet).equals("RESUMED_ORIGINAL_ORDER")&&tank.getZ()>f.garageOrigin.getZ()+16){h.assertTrue(box.getItem(4).isEmpty(),"seven real repair parts must be consumed to restore 140 HP");finish();return;}
                    if(mode==10&&returned&&sawCargo&&tank.inventory().ammunition("tank_he_shell")==10&&tank.health()==270&&VehicleFleetSystem.logisticsStatus(f.level,fleet).equals("RESUMED_ORIGINAL_ORDER")&&tank.getZ()>f.garageOrigin.getZ()+16){h.assertTrue(box.getItem(4).isEmpty(),"the ammunition return consumes three actual parts for its departure health check");finish();return;}
                    if((mode==7||mode==9)&&returned&&sawCargo&&!VehicleLogisticsService.servicing(f.level,tank)&&(mode==7?tank.inventory().ammunition("tank_he_shell")==6:tank.health()==160)){
                        h.assertTrue(intent.supplyReturn()&&!VehicleSupplyRules.needsService(tank.definition(),tank.inventory(),tank.health()),"the return remains latched after its low trigger clears");
                        h.assertTrue(tank.position().distanceToSqr(new Vec3(f.garageOrigin.getX()+15.5,81,f.garageOrigin.getZ()+12.5))<.0025&&VehicleFleetSystem.status(f.level,fleet).equals("PARKED"),"finite insufficient stock must keep the actual hull parked");
                        h.assertTrue(VehicleFleetSystem.logisticsStatus(f.level,fleet).equals(mode==7?"WAITING_FOR_PRIMARY_AMMUNITION":"WAITING_FOR_REPAIR_PARTS"),"waiting report identifies the missing departure material");
                        if(++settled>=60){h.assertTrue(box.getItem(mode==7?0:4).isEmpty(),"only the finite source stock was consumed");finish();return;}
                    }
                }
                if(sawCargo&&!acted&&(mode==1||mode==2||mode==3)){
                    h.assertTrue(tank.inventory().ammunition("tank_he_shell")==0&&box.getItem(0).isEmpty(),"pickup debits the actual box before a later physical delivery");acted=true;
                    if(mode==1){tank.setPlayerOwner(UUID.randomUUID());VehicleLogisticsService.cancel(carrier);}
                    if(mode==2){tank.setPlayerOwner(UUID.randomUUID());carrier.hurt(f.level.damageSources().genericKill(),10000);}
                    if(mode==3){
                        var saved=new net.minecraft.nbt.CompoundTag();h.assertTrue(bundle.save(saved),"actual in-flight item must produce real persistable entity NBT");var id=bundle.getUUID();
                        bundle.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                        restoreBundle(saved,id,80);
                    }
                }
                if(acted&&(mode==1||mode==2)&&++settled>=20){
                    h.assertTrue(tank.inventory().ammunition("tank_he_shell")==0&&box.getItem(0).isEmpty()&&loose("tank_he_shell")==3,"cancel/death leaves exactly three original shells locally, with no remote delivery or refund");
                    h.assertTrue(!carrier.getPersistentData().contains(VehicleLogisticsService.JOB_TAG)&&!bundle.getPersistentData().contains(VehicleLogisticsService.CARGO_TAG),"carrier cancellation releases both metadata bindings");finish();return;
                }
                if((mode==0||mode==3)&&sawCargo&&tank.inventory().ammunition("tank_he_shell")==3){
                    if(mode==0&&(tank.inventory().ammunition("tank_ap_shell")!=2||tank.inventory().ammunition("machine_gun_round")!=4||tank.health()!=tank.maxHealth()-10)){later();return;}
                    if(++settled>=25){h.assertTrue(loose("tank_he_shell")==0&&box.getItem(0).isEmpty(),"completed finite transfer and repeated ticks cannot recreate the delivered shells");finish();return;}
                }
                later();
            }catch(Throwable failure){logDiagnostic("FAIL");close();throw failure instanceof GameTestAssertException game?game:new GameTestAssertException("logistics fixture/behavior error: "+failure);}
        }
        void prepare(){
            nativeSlots=f.slots();
            f.attach();tank=ModEntities.MILITARY_TANK.get().create(f.level);tank.setPos(f.garageOrigin.getX()+15.5,81,f.garageOrigin.getZ()+12.5);
            tank.initializeNativeOrigin(f.baseId,f.level.dimension().location().toString());f.level.addFreshEntity(tank);f.vehicles.put("tank",tank.getUUID());
            var ledger=VehicleGarageService.data(f.level);
            h.assertTrue(ledger.reserve(f.garageId,"tank",tank.getUUID())&&ledger.issued(f.garageId,"tank",tank.getUUID())&&VehicleGarageService.registerParked(f.level,tank,f.garageId),"fixture registers one existing supported native tank in the real bay");
            box=(Container)f.level.getBlockEntity(f.garageOrigin.offset(15,1,2));box.clearContent();
            if(mode!=5&&mode!=8&&mode!=9)box.setItem(0,new ItemStack(VehicleRegistry.item("tank_he_shell"),mode==7?6:mode>=4?10:3));
            box.setItem(1,new ItemStack(VehicleRegistry.item("military_fuel_can"),2));
            box.setItem(5,new ItemStack(VehicleRegistry.item("autocannon_round"),5));
            if(mode==0){box.setItem(2,new ItemStack(VehicleRegistry.item("tank_ap_shell"),2));box.setItem(3,new ItemStack(com.codex.blacklight.firearms.FirearmsRegistry.ammo("machine_gun_round"),4));box.setItem(4,new ItemStack(VehicleRegistry.item("vehicle_repair_parts")));tank.hurt(f.level.damageSources().generic(),30);}
            if(mode==8||mode==9){tank.inventory().addAmmunition("tank_he_shell",12);tank.inventory().addAmmunition("tank_ap_shell",8);tank.hurt(f.level.damageSources().generic(),300);box.setItem(4,new ItemStack(VehicleRegistry.item("vehicle_repair_parts"),mode==8?7:2));}
            if(mode==10){tank.hurt(f.level.damageSources().generic(),210);box.setItem(4,new ItemStack(VehicleRegistry.item("vehicle_repair_parts"),3));}
            for(var s:MilitaryBaseAccess.idleCrew(f.level,f.baseId,32,Set.of()))if(s.getOffhandItem().isEmpty())s.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(com.codex.blacklight.firearms.FirearmsRegistry.weapon("military_carbine")));
            for(var s:f.people.values()){main.put(s.getUUID(),s.getMainHandItem().copy());secondary.put(s.getUUID(),s.getOffhandItem().copy());}
            if(mode>=4){
                var crew=MilitaryBaseAccess.idleCrew(f.level,f.baseId,32,Set.of());h.assertTrue(crew.size()>=2,"original native roster contains two eligible actual crew");
                officer=f.people.values().stream().filter(s->s.commandRole()==com.codex.blacklight.military.MilitaryRole.OFFICER||s.commandRole()==com.codex.blacklight.military.MilitaryRole.COMMANDER).findFirst().orElseThrow();
                tank.setPos(tank.getX(),81,f.garageOrigin.getZ()+38.5);tank.setYRot(180);
                h.assertTrue(crew.get(0).startRiding(tank)&&crew.get(1).startRiding(tank)&&officer.startRiding(tank),"existing native driver gunner and officer occupy real seats");
                h.assertTrue(VehicleKinematics.placementReady(tank),"outside fixture starts with real full-hull clearance and support");outside=tank.position();last=outside;
                if(mode==6){
                    var infected=ModEntities.INFECTED_HOST.get().create(f.level);infected.setNoAi(true);infected.setPersistenceRequired();infected.setInvulnerable(true);infected.setPos(tank.getX()+10,81,tank.getZ());f.level.addFreshEntity(infected);threat=infected;
                }
            }
        }
        void startFleet(){
            if(mode==6)h.assertTrue(threat instanceof net.minecraft.world.entity.LivingEntity living&&VehicleThreatBridge.legalTarget(tank,living)&&tank.distanceToSqr(threat)<=16*16,"urgent withdrawal fixture has a real legal nearby infected contact before fleet activation");
            fleet=VehicleFleetSystem.create(f.level,List.of(tank),officer.getUUID()).orElseThrow(()->new GameTestAssertException("tracked native crewed tank must form a real fleet"));
            h.assertTrue(VehicleFleetSystem.setMission(f.level,fleet,mode==6?"CLEAR_AREA":"MOVE",new BlockPos(f.garageOrigin.getX()+15,81,f.garageOrigin.getZ()+44)),"real fleet accepts the saved original mission");
            orderTarget=VehicleFleetSystem.fleets(f.level).stream().filter(v->v.id().equals(fleet)).findFirst().orElseThrow().target();
        }
        void restoreBundle(net.minecraft.nbt.CompoundTag saved,UUID id,int remaining){
            h.runAfterDelay(1,()->{
                if(closed)return;
                if(f.level.getEntity(id)!=null){h.assertTrue(remaining>0,"old cargo UUID must leave the actual index before the NBT handoff");restoreBundle(saved,id,remaining-1);return;}
                var restored=EntityType.loadEntityRecursive(saved,f.level,entity->entity);
                h.assertTrue(restored instanceof ItemEntity&&restored.getUUID().equals(id)&&f.level.addFreshEntity(restored),"actual cargo NBT transfers the same item UUID only after the original entity leaves");bundle=(ItemEntity)restored;
            });
        }
        int loose(String kind){int count=0;for(var item:f.level.getEntitiesOfClass(ItemEntity.class,new AABB(f.garageOrigin).inflate(160)))if(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).getPath().equals(kind))count+=item.getItem().getCount();return count;}
        void later(){h.runAfterDelay(1,()->poll());}
        void logDiagnostic(String phase){
            var log=org.slf4j.LoggerFactory.getLogger(VehicleLogisticsGameTest.class);
            if(tank==null||f.garageId==null)return;
            log.info("LOGISTICS_REVISION_QA {} mode={} age={} {}",phase,mode,age,VehicleGarageService.diagnostic(f.level,tank,f.garageId));
            log.info("LOGISTICS_REVISION_QA {} mode={} {}",phase,mode,VehicleLogisticsService.diagnostic(f.level,f.garageId));
            if(fleet!=null)log.info("LOGISTICS_REVISION_QA {} mode={} {}",phase,mode,VehicleFleetSystem.diagnostic(f.level,fleet,tank.getUUID()));
        }
        void finish(){
            for(var s:f.people.values())if(s.isAlive()){h.assertTrue(ItemStack.matches(main.get(s.getUUID()),s.getMainHandItem())&&ItemStack.matches(secondary.get(s.getUUID()),s.getOffhandItem()),"logistics must preserve the actual main and secondary gun stacks");}
            h.assertTrue(box.getItem(1).getCount()==2&&box.getItem(5).getCount()==5,"inactive fuel and wrong ammunition remain untouched");
            h.assertTrue(Arrays.equals(nativeSlots,f.slots()),"service never creates or replaces a native personnel UUID");close();h.succeed();
        }
        @Override public void close(){
            if(closed)return;closed=true;if(fleet!=null)VehicleFleetSystem.remove(f.level,fleet);if(threat!=null)threat.discard();
            for(var s:f.people.values())VehicleLogisticsService.cancel(s);
            if(f.garageId!=null){for(var slot:f.garage().slots().values())if(slot.vehicle()!=null)f.vehicles.put(slot.chassis(),slot.vehicle());}
            for(var item:f.level.getEntitiesOfClass(ItemEntity.class,new AABB(f.garageOrigin).inflate(160)))item.discard();f.close();
        }
    }
    private static void awaitTank(GameTestHelper h,VehicleGarageGameTest.Fixture f,
            com.codex.blacklight.entity.MilitaryVehicle vehicle,Runnable ready,int remaining) {
        h.runAfterDelay(1,()->{
            for(int task=0;task<256&&f.level.getChunkSource().pollTask();task++) { }
            if(f.level.getEntity(vehicle.getUUID())==vehicle&&vehicle.tickCount>0
                    &&f.level.isPositionEntityTicking(vehicle.blockPosition())) { ready.run();return; }
            if(remaining==0) {
                try(f) { h.assertTrue(false,"logistics fixture tank must be tracked and entity-ticking before the behavior check; ticks="+vehicle.tickCount); }
                return;
            }
            awaitTank(h,f,vehicle,ready,remaining-1);
        });
    }
    private VehicleLogisticsGameTest() { }
}
