package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

/** A real native worker gets a separate bounded delivery stage only after real box pickup. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehicleLogisticsDeadlineGameTest {
    @GameTest(template="blackwatch_arena",batch="vehicle6logistics_deadline_delivery",timeoutTicks=3400)
    public static void actualLatePickupKeepsItsOriginalPhysicalShellsPastOldDeadlineAndDeliversOnce(GameTestHelper h){new Scene(h,0).poll();}
    @GameTest(template="blackwatch_arena",batch="vehicle6logistics_deadline_expiry",timeoutTicks=3400)
    public static void actualPickedUpDeliveryStillExpiresAfterItsOneNewFiniteBudgetAndReleasesOnlyOriginalShells(GameTestHelper h){new Scene(h,1).poll();}
    @GameTest(template="blackwatch_arena",batch="vehicle6logistics_deadline_no_pickup",timeoutTicks=2400)
    public static void workerThatNeverReachesActualPickupStillExpiresOnItsOriginalBudgetWithoutBoxDebit(GameTestHelper h){new Scene(h,2).poll();}

    private static final class Scene implements AutoCloseable {
        final GameTestHelper h;final int mode;final VehicleGarageDispatchGameTest.Fixture f;
        final Map<UUID,ItemStack> main=new HashMap<>(),secondary=new HashMap<>();
        MilitaryVehicle tank;Container box;BlockPos boxPosition;MilitarySoldier worker;ItemEntity bundle;
        UUID[] people;long originalUntil,deliveryUntil,pickupAt;double originalSpeed;
        int elapsed,age;boolean prepared,closed,approachHeld,releasedToPickup,pickedUp,movementHeld,crossedOld;
        Scene(GameTestHelper h,int mode){this.h=h;this.mode=mode;f=VehicleGarageDispatchGameTest.Fixture.create(h.getLevel(),716800+mode*4096,false);}
        void poll(){
            if(closed)return;
            try{
                for(int task=0;task<256&&f.level.getChunkSource().pollTask();task++) { }
                if(!prepared){
                    h.assertTrue(++elapsed<400,"existing native fixture must genuinely muster before deadline behavior");
                    if(f.muster(elapsed)){prepare();prepared=true;}later();return;
                }
                h.assertTrue(++age<3000,"bounded real logistics deadline scenario timed out; "+diagnostic());
                h.assertTrue(box.getItem(0).getCount()+tank.inventory().ammunition("tank_he_shell")+physicalShells()==3,
                        "real box, chassis and unique physical item entities must always total exactly three shells; "+diagnostic());
                if(worker==null){
                    for(var soldier:f.people.values()){
                        var job=job(soldier);
                        if(job.hasUUID("Vehicle")&&job.getUUID("Vehicle").equals(tank.getUUID())){
                            worker=soldier;originalUntil=job.getLong("Until");originalSpeed=worker.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
                            h.assertTrue(originalUntil>now()&&originalUntil<=now()+1200,"actual service begins with the original bounded request budget");break;
                        }
                    }
                    later();return;
                }
                var job=job(worker);
                if(!approachHeld){
                    h.assertTrue(job.hasUUID("Vehicle")&&job.getUUID("Vehicle").equals(tank.getUUID()),"original worker must reach its real box approach before its request expires; "+diagnostic());
                    double distance=worker.distanceToSqr(Vec3.atCenterOf(boxPosition));
                    if(distance>9.5&&distance<=25){
                        h.assertTrue(VehicleLogisticsService.carried(worker)==null&&box.getItem(0).getCount()==3,"approach hold happens before any actual pickup or box debit");
                        holdMovement();approachHeld=true;log("APPROACH_HELD");
                    }
                    later();return;
                }
                if(!releasedToPickup&&!pickedUp){
                    h.assertTrue(tank.inventory().ammunition("tank_he_shell")==0&&physicalShells()==0&&box.getItem(0).getCount()==3,
                            "waiting near the original deadline cannot manufacture a cargo stack or debit stock");
                    if(mode==2){
                        if(now()<=originalUntil)h.assertTrue(job.getLong("Until")==originalUntil,"an unpicked request never renews itself each tick");
                        if(now()>originalUntil+2){
                            h.assertTrue((!worker.getPersistentData().contains(VehicleLogisticsService.JOB_TAG)||job.getLong("Until")!=originalUntil)&&VehicleLogisticsService.carried(worker)==null,
                                    "no actual pickup means this original request expires; a later separately requested job is not a delivery renewal");finish();return;
                        }
                    }else if(originalUntil-now()<=60){restoreMovement();releasedToPickup=true;log("RELEASED_NEAR_OLD_DEADLINE");}
                    later();return;
                }
                if(!pickedUp){
                    var cargo=VehicleLogisticsService.carried(worker);
                    h.assertTrue(now()<=originalUntil,"the original real worker must perform actual pickup before the old request deadline; "+diagnostic());
                    if(cargo!=null){
                        h.assertTrue(f.people.get(worker.getUUID())==worker&&!worker.isPassenger()&&!worker.isNoAi(),"same native AI worker remains eligible and is not a manufactured carrier");
                        h.assertTrue(box.getItem(0).isEmpty()&&tank.inventory().ammunition("tank_he_shell")==0&&cargo.getItem().getCount()==3,
                                "actual pickup removes exactly three shells from the real certified box into one real ItemEntity before delivery");
                        h.assertTrue(cargo.distanceToSqr(worker)<=16&&worker.distanceToSqr(Vec3.atCenterOf(boxPosition))<16,
                                "actual physical bundle and worker are beside the actual debited box");
                        h.assertTrue(job.hasUUID("Cargo")&&job.getUUID("Cargo").equals(cargo.getUUID())
                                &&cargo.getPersistentData().getUUID(VehicleLogisticsService.CARGO_TAG).equals(worker.getUUID()),
                                "the one actual package has the real two-way worker binding");
                        pickupAt=now();deliveryUntil=job.getLong("Until");bundle=cargo;pickedUp=true;
                        h.assertTrue(originalUntil-pickupAt<=60&&deliveryUntil>originalUntil&&deliveryUntil>=pickupAt+1195&&deliveryUntil<=pickupAt+1200,
                                "late actual pickup starts one new full but finite delivery budget, rather than inheriting an almost expired request; "+diagnostic());
                        holdMovement();log("PICKUP");
                    }
                    later();return;
                }
                if(mode==1){
                    if(now()<=deliveryUntil){
                        h.assertTrue(job.getLong("Until")==deliveryUntil&&VehicleLogisticsService.carried(worker)==bundle&&bundle.getItem().getCount()==3,
                                "held real delivery does not extend its deadline every tick or replace its physical package; "+diagnostic());
                    }else if(now()>deliveryUntil+2){
                        h.assertTrue(!worker.getPersistentData().contains(VehicleLogisticsService.JOB_TAG)&&!bundle.getPersistentData().contains(VehicleLogisticsService.CARGO_TAG)
                                &&!bundle.isRemoved()&&bundle.getItem().getCount()==3&&physicalShells()==3&&tank.inventory().ammunition("tank_he_shell")==0&&box.getItem(0).isEmpty(),
                                "the real new 1200-tick stage still expires and releases precisely the same original local shells without refund or remote delivery");finish();return;
                    }
                }else{
                    if(!crossedOld&&now()>originalUntil+5){
                        h.assertTrue(VehicleLogisticsService.carried(worker)==bundle&&job.getLong("Until")==deliveryUntil&&tank.inventory().ammunition("tank_he_shell")==0,
                                "same real package remains bound past the old request deadline while the new finite delivery budget owns it");
                        crossedOld=true;restoreMovement();log("PAST_OLD_DEADLINE");
                    }
                    if(crossedOld&&tank.inventory().ammunition("tank_he_shell")==3){
                        h.assertTrue(box.getItem(0).isEmpty()&&physicalShells()==0&&!worker.getPersistentData().contains(VehicleLogisticsService.JOB_TAG),
                                "normal native walking delivers the original three shells once, empties the physical package and finishes service");finish();return;
                    }
                    h.assertTrue(now()<=deliveryUntil,"real delivery must finish within its new finite stage; "+diagnostic());
                }
                later();
            }catch(Throwable failure){log("FAIL");close();throw failure instanceof GameTestAssertException game?game:new GameTestAssertException("deadline fixture/behavior error: "+failure);}
        }
        void prepare(){
            people=f.slots();f.attach();tank=ModEntities.MILITARY_TANK.get().create(f.level);
            tank.setPos(f.garageOrigin.getX()+15.5,81,f.garageOrigin.getZ()+12.5);
            tank.initializeNativeOrigin(f.baseId,f.level.dimension().location().toString());f.level.addFreshEntity(tank);f.vehicles.put("tank",tank.getUUID());
            var ledger=VehicleGarageService.data(f.level);
            h.assertTrue(ledger.reserve(f.garageId,"tank",tank.getUUID())&&ledger.issued(f.garageId,"tank",tank.getUUID())&&VehicleGarageService.registerParked(f.level,tank,f.garageId),
                    "existing shared native fixture registers this actual empty-ammunition tank in its supported bay");
            boxPosition=f.garageOrigin.offset(15,1,2);box=(Container)f.level.getBlockEntity(boxPosition);box.clearContent();
            box.setItem(0,new ItemStack(VehicleRegistry.item("tank_he_shell"),3));
            for(var s:f.people.values()){main.put(s.getUUID(),s.getMainHandItem().copy());secondary.put(s.getUUID(),s.getOffhandItem().copy());}
        }
        long now(){return f.level.getGameTime();}
        CompoundTag job(MilitarySoldier soldier){return soldier.getPersistentData().getCompound(VehicleLogisticsService.JOB_TAG);}
        void holdMovement(){
            // Disposable fixture only: keep the existing eligible native AI worker
            // at the observed approach/package, without NoAI or fake inventory.
            worker.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);worker.setDeltaMovement(Vec3.ZERO);movementHeld=true;
        }
        void restoreMovement(){if(worker!=null&&movementHeld){worker.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(originalSpeed);movementHeld=false;}}
        int physicalShells(){
            int total=0;for(var item:f.level.getEntitiesOfClass(ItemEntity.class,new AABB(f.garageOrigin).inflate(160)))
                if(item.getItem().is(VehicleRegistry.item("tank_he_shell")))total+=item.getItem().getCount();return total;
        }
        void later(){h.runAfterDelay(1,()->poll());}
        String diagnostic(){return "mode="+mode+" now="+now()+" original="+originalUntil+" delivery="+deliveryUntil+" worker="+(worker==null?"NONE":worker.getUUID()+"@"+worker.position()+" job="+job(worker))
                +" tank="+(tank==null?"NONE":tank.inventory().ammunition("tank_he_shell"))+" physical="+physicalShells();}
        void log(String phase){if(f.garageId!=null)org.slf4j.LoggerFactory.getLogger(VehicleLogisticsDeadlineGameTest.class).info("LOGISTICS_DEADLINE_QA {} {} {}",phase,diagnostic(),VehicleLogisticsService.diagnostic(f.level,f.garageId));}
        void finish(){
            h.assertTrue(Arrays.equals(people,f.slots()),"finite deadline handling never replaces or creates a native personnel UUID");
            for(var s:f.people.values())h.assertTrue(ItemStack.matches(main.get(s.getUUID()),s.getMainHandItem())&&ItemStack.matches(secondary.get(s.getUUID()),s.getOffhandItem()),
                    "deadline handling preserves each original native primary and secondary gun stack");
            close();h.succeed();
        }
        @Override public void close(){
            if(closed)return;closed=true;restoreMovement();for(var s:f.people.values())VehicleLogisticsService.cancel(s);
            if(f.garageId!=null)for(var slot:f.garage().slots().values())if(slot.vehicle()!=null)f.vehicles.put(slot.chassis(),slot.vehicle());
            for(var item:f.level.getEntitiesOfClass(ItemEntity.class,new AABB(f.garageOrigin).inflate(160)))item.discard();f.close();
        }
    }
    private VehicleLogisticsDeadlineGameTest() { }
}
