package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.VehicleOrdnanceProjectile;
import com.codex.blacklight.military.aircraft.HelicopterMissileFlight;
import com.codex.blacklight.registry.ModEntities;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterHellfireGameTest {
    private static final class Fixture {
        final GameTestHelper h;final HelicopterPilotGameTest.Fixture area;final Vec3 start;
        BlackwatchSoldier owner;BlackwatchHelicopter origin;BlacklightCommonInfected target;VehicleOrdnanceProjectile missile,second;
        boolean initialized,done,reloaded,checked;int frame;float health;Vec3 expected,frozen;
        Fixture(GameTestHelper h,int index){this.h=h;area=new HelicopterPilotGameTest.Fixture(h,270000+index*512);start=new Vec3(area.x,100,0);
            for(int cx=(area.x>>4)-4;cx<=(area.x>>4)+4;cx++)for(int cz=-4;cz<=4;cz++){long key=ChunkPos.asLong(cx,cz);area.forced.putIfAbsent(key,area.level.getForcedChunks().contains(key));area.level.setChunkForced(cx,cz,true);}
        }
        boolean ready(){if(initialized)return true;if(!area.level.isPositionEntityTicking(BlockPos.containing(start))||!area.level.isPositionEntityTicking(BlockPos.containing(start.add(60,0,30))))return false;
            initialized=true;origin=area.craft(16);origin.setPos(start.add(-10,0,-12));
            owner=ModEntities.BLACKWATCH_SOLDIER.get().create(area.level);owner.setPos(start.add(-15,-19,-10));owner.setNoAi(true);owner.setAutomaticUnitJoin(false);area.level.addFreshEntity(owner);area.entities.add(owner);
            target=ModEntities.COMMON_INFECTED.get().create(area.level);target.setNoAi(true);target.setNoGravity(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);targetAt(new Vec3(40,0,0));area.level.addFreshEntity(target);area.entities.add(target);owner.setTarget(target);return true;
        }
        void targetAt(Vec3 offset){target.setPos(start.add(offset).add(0,-target.getBbHeight()*.5,0));target.setDeltaMovement(Vec3.ZERO);}
        void launch(Vec3 inherited){missile=new VehicleOrdnanceProjectile(area.level,owner,origin,"helicopter_hellfire");missile.setPos(start);missile.launchHellfire(new Vec3(1,0,0),inherited,target,target.getBoundingBox().getCenter(),false);h.assertTrue(area.level.addFreshEntity(missile),"native Hellfire spawned");area.entities.add(missile);}
        void pass(String proof){done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.info("HELI_HELLFIRE_VERIFIED case={} frame={} age={} position={} targetHealth={} lost={}",proof,frame,missile==null?-1:missile.flightAge(),missile==null?start:missile.position(),target.getHealth(),missile!=null&&missile.trackingLost());area.close();h.succeed();}
    }
    private static void run(GameTestHelper h,int index,Consumer<Fixture> task){var f=new Fixture(h,index);h.onEachTick(()->{if(f.done)return;try{if(f.ready()){if(f.frame<=2)f.health=f.target.getHealth();task.accept(f);f.frame++;}}catch(RuntimeException ex){f.done=true;f.area.close();throw ex;}});}
    @GameTest(template="helicopter_arena",batch="hellfireCold",timeoutTicks=350)
    public static void nativeColdLaunchInheritsMotionAndWaitsHalfASecondBeforeTracking(GameTestHelper h){run(h,0,f->{
        if(f.missile==null){f.targetAt(new Vec3(40,0,12));f.launch(new Vec3(.2,0,.1));}
        int age=f.missile.flightAge();
        if(age==1){Vec3 moved=f.missile.position().subtract(f.start);h.assertTrue(Math.abs(moved.x-.2)<1e-6&&Math.abs(moved.y+.07)<1e-6&&Math.abs(moved.z-.1)<1e-6,"actual first native tick preserves inherited velocity and cold gravity");f.checked=true;}
        if(age>0&&age<10)h.assertTrue(f.missile.flightHeading().distanceTo(new Vec3(1,0,0))<1e-7,"no pre-delay homing");
        if(age==10){h.assertTrue(f.checked&&f.missile.flightHeading().z>0&&f.missile.getDeltaMovement().length()>.2,"actual native projectile accelerates and starts bounded tracking");f.pass("cold-delay");}
    });}
    @GameTest(template="helicopter_arena",batch="hellfireMoving",timeoutTicks=400)
    public static void realGuidedMissileHitsAMovingTargetOutsideItsLaunchRay(GameTestHelper h){run(h,1,f->{
        f.targetAt(new Vec3(40,0,f.frame*.3));f.target.setDeltaMovement(0,0,.3);
        if(f.missile==null)f.launch(Vec3.ZERO);
        if(f.frame>2&&f.target.getHealth()<f.health){h.assertTrue(f.missile.isRemoved()&&f.target.position().z>4&&f.missile.position().z>4,"moving target and actual missile both left the launch ray");double expected=20*com.codex.blacklight.power.CombatScale.units(f.target);h.assertTrue(Math.abs(f.health-f.target.getHealth()-expected)<.01,"one direct Hellfire hit passes through the real health-unit conversion exactly once, expected="+expected+" actual="+(f.health-f.target.getHealth()));f.pass("moving-intercept");}
    });}
    @GameTest(template="helicopter_arena",batch="hellfireReload",timeoutTicks=450)
    public static void realProjectileReloadPreservesFlightAndHitsWithoutRestarting(GameTestHelper h){run(h,2,f->{
        f.targetAt(new Vec3(60,0,16));if(f.missile==null)f.launch(new Vec3(.1,0,0));
        if(!f.reloaded&&f.missile.flightAge()==14){
            var saved=new CompoundTag();f.missile.saveWithoutId(saved);var state=HelicopterMissileFlight.load(saved.getCompound("HellfireFlight"));
            f.expected=f.missile.position().add(state.step(f.missile.position(),f.target.getBoundingBox().getCenter(),Vec3.ZERO));
            f.missile.discard();saved.putUUID("UUID",UUID.randomUUID());f.missile=ModEntities.VEHICLE_ORDNANCE.get().create(f.area.level);f.missile.load(saved);h.assertTrue(f.area.level.addFreshEntity(f.missile),"saved native missile restored");f.area.entities.add(f.missile);f.reloaded=true;
        }else if(f.reloaded&&!f.checked&&f.missile.flightAge()==15){h.assertTrue(f.missile.position().distanceTo(f.expected)<1e-6,"first post-load native position matches uninterrupted physics");f.checked=true;}
        if(f.frame>2&&f.target.getHealth()<f.health){h.assertTrue(f.checked&&f.missile.isRemoved(),"restored actual projectile continued through impact");f.pass("reload-intercept");}
    });}
    @GameTest(template="helicopter_arena",batch="hellfireLost",timeoutTicks=350)
    public static void removedTargetDoesNotTransferLockToANewNearbyEnemy(GameTestHelper h){run(h,3,f->{
        if(f.missile==null){f.targetAt(new Vec3(60,0,18));f.launch(Vec3.ZERO);}
        if(!f.checked&&f.missile.flightAge()==12){f.target.discard();f.frozen=f.missile.flightHeading();
            var other=ModEntities.COMMON_INFECTED.get().create(f.area.level);other.setPos(f.start.add(30,0,-20));other.setNoAi(true);other.setNoGravity(true);f.area.level.addFreshEntity(other);f.area.entities.add(other);f.owner.setTarget(other);f.checked=true;}
        if(f.checked&&f.missile.flightAge()>=18){h.assertTrue(f.missile.trackingLost()&&!f.missile.isRemoved()&&f.missile.flightHeading().distanceTo(f.frozen)<1e-7,"missing original target permanently loses guidance and keeps coasting");f.pass("removed-target");}
    });}
    @GameTest(template="helicopter_arena",batch="hellfireRear",timeoutTicks=350)
    public static void targetCrossingBehindCannotMakeTheMissileLoopBack(GameTestHelper h){run(h,4,f->{
        if(f.missile==null){f.targetAt(new Vec3(60,0,0));f.launch(Vec3.ZERO);}
        int age=f.missile.flightAge();if(age==12){f.targetAt(new Vec3(-10,0,0));f.frozen=f.missile.flightHeading();}
        if(age==14){h.assertTrue(f.missile.trackingLost(),"source minimum heading dot rejects rear target");f.targetAt(new Vec3(50,0,20));}
        if(age==19){h.assertTrue(f.missile.flightHeading().distanceTo(f.frozen)<1e-7,"lost guidance cannot silently reacquire when target returns");f.pass("rear-lock-loss");}
    });}
    @GameTest(template="helicopter_arena",batch="hellfireWall",timeoutTicks=400)
    public static void aWallInsertedDuringFlightRemainsTheFirstNativeCollision(GameTestHelper h){run(h,5,f->{
        if(f.missile==null)f.launch(Vec3.ZERO);
        if(!f.checked&&f.missile.flightAge()==12){for(int y=96;y<=104;y++)for(int z=-6;z<=6;z++)f.area.level.setBlock(new BlockPos(f.area.x+20,y,z),Blocks.BEDROCK.defaultBlockState(),2);f.checked=true;}
        if(f.checked&&f.missile.isRemoved()){h.assertTrue(f.missile.position().x<f.area.x+23.1&&f.target.getHealth()==f.health,"guided projectile stops at the real wall before damaging the target; removed position includes native tick's final displacement");f.pass("native-wall");}
    });}
    @GameTest(template="helicopter_arena",batch="hellfireExpiry",timeoutTicks=320)
    public static void expiredAndCorruptSavedFlightCannotContinueOrDealDamage(GameTestHelper h){run(h,6,f->{
        if(f.missile==null){
            f.launch(Vec3.ZERO);var saved=new CompoundTag();f.missile.saveWithoutId(saved);saved.getCompound("HellfireFlight").putInt("Age",199);f.missile.load(saved);
            saved.putUUID("UUID",UUID.randomUUID());saved.getCompound("HellfireFlight").putDouble("VelocityX",Double.NaN);f.second=ModEntities.VEHICLE_ORDNANCE.get().create(f.area.level);f.second.load(saved);f.area.level.addFreshEntity(f.second);f.area.entities.add(f.second);
        }
        if(f.frame==1){h.assertTrue(!f.missile.isRemoved()&&f.missile.flightAge()==200,"last legal flight step retained");f.checked=true;}
        if(f.frame>=4){h.assertTrue(f.checked&&f.missile.isRemoved()&&f.second.isRemoved()&&f.target.getHealth()==f.health,"age and corrupted flight fail closed without damage");f.pass("expiry-corruption");}
    });}
    @GameTest(template="helicopter_arena",batch="hellfireLoaded",timeoutTicks=300)
    public static void guidanceDoesNotGenerateUnloadedFlightChunks(GameTestHelper h){run(h,7,f->{
        Vec3 at=f.start.add(5000,0,0);var pos=BlockPos.containing(at);h.assertTrue(!f.area.level.hasChunkAt(pos),"flight boundary begins unloaded");
        f.missile=new VehicleOrdnanceProjectile(f.area.level,f.owner,f.origin,"helicopter_hellfire");f.missile.setPos(at);f.missile.launchHellfire(new Vec3(1,0,0),Vec3.ZERO,null,at.add(100,0,0),false);
        // Directly exercise the actual server tick without registering an entity
        // in an unloaded chunk (registration itself could request that chunk).
        f.missile.tick();h.assertTrue(f.missile.isRemoved()&&!f.area.level.hasChunkAt(pos),"source flight cannot force-load missing collision data");f.pass("unloaded-boundary");
    });}
}
