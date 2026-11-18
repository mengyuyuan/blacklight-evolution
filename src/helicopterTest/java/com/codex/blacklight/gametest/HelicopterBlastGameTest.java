package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.HellfireBlastEntity;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterBlastGameTest {
    private static final class Fixture {
        final GameTestHelper h;final HelicopterPilotGameTest.Fixture area;final Vec3 center;
        final BlacklightCommonInfected[] targets=new BlacklightCommonInfected[5];final float[] health=new float[5];final Vec3[] offsets={new Vec3(.8,0,0),new Vec3(0,0,3),new Vec3(-6,0,0),new Vec3(0,0,-8),new Vec3(11,0,0)};
        BlackwatchSoldier owner;HellfireBlastEntity blast;MilitaryVehicle tank,friend;boolean ready,done,changed,checked;int frame;float ownerHealth,tankHealth,friendHealth;
        Fixture(GameTestHelper h,int index){this.h=h;area=new HelicopterPilotGameTest.Fixture(h,280000+index*512);center=new Vec3(area.x,100,0);}
        boolean ready(){
            if(ready)return true;if(!area.level.isPositionEntityTicking(BlockPos.containing(center)))return false;ready=true;
            owner=ModEntities.BLACKWATCH_SOLDIER.get().create(area.level);owner.setPos(center.add(-16,0,-16));owner.setNoAi(true);owner.setNoGravity(true);owner.setAutomaticUnitJoin(false);area.level.addFreshEntity(owner);area.entities.add(owner);
            for(int i=0;i<5;i++){var t=ModEntities.COMMON_INFECTED.get().create(area.level);t.setNoAi(true);t.setNoGravity(true);t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);t.setHealth(1000);targets[i]=t;position(i);area.level.addFreshEntity(t);area.entities.add(t);}return true;
        }
        void position(int i){var t=targets[i];if(t.getVehicle()==null){t.setPos(center.add(offsets[i]).add(0,-t.getBbHeight()*.5,0));t.setDeltaMovement(Vec3.ZERO);}t.invulnerableTime=0;}
        void launch(){blast=HellfireBlastEntity.start(area.level,owner,center,new Vec3(0,1,0));h.assertTrue(blast!=null,"actual server blast registered");area.entities.add(blast);}
        void loss(int i,float expected){h.assertTrue(Math.abs(health[i]-targets[i].getHealth()-expected)<.02,"target "+i+" loss expected="+expected+" actual="+(health[i]-targets[i].getHealth())+" blastAge="+blast.blastAge());}
        void pass(String name){done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.info("HELLFIRE_BLAST_VERIFIED case={} age={} health={} removed={}",name,blast.blastAge(),Arrays.stream(targets).map(t->t.getHealth()).toList(),blast.isRemoved());area.close();h.succeed();}
    }
    private static void run(GameTestHelper h,int index,Consumer<Fixture> step){var f=new Fixture(h,index);h.onEachTick(()->{if(f.done)return;try{if(!f.ready())return;for(int i=0;i<5;i++)f.position(i);if(f.frame<5){for(int i=0;i<5;i++)f.health[i]=f.targets[i].getHealth();f.ownerHealth=f.owner.getHealth();}step.accept(f);f.frame++;}catch(RuntimeException ex){f.done=true;f.area.close();throw ex;}});}
    @GameTest(template="helicopter_arena",batch="blastRings",timeoutTicks=260)
    public static void realGrowingRingsUseSourceRatiosAndNeverStackEvenWithoutHurtImmunity(GameTestHelper h){run(h,0,f->{
        if(f.frame==5)f.launch();if(f.blast==null)return;int age=f.blast.blastAge();
        f.loss(0,100);f.loss(1,age>=1?20:0);f.loss(2,age>=2?7:0);f.loss(3,age>=4?3:0);f.loss(4,0);
        if(age>=10)f.pass("four-rings-once");
    });}
    @GameTest(template="helicopter_arena",batch="blastReload",timeoutTicks=280)
    public static void realSavedBlastKeepsItsContactSetAndCanHitALateEnteringTarget(GameTestHelper h){run(h,1,f->{
        if(f.frame==5)f.launch();if(f.blast==null)return;int age=f.blast.blastAge();
        if(age==1&&!f.changed){var tag=new CompoundTag();f.blast.saveWithoutId(tag);f.blast.discard();tag.putUUID("UUID",UUID.randomUUID());f.blast=ModEntities.HELLFIRE_BLAST.get().create(f.area.level);f.blast.load(tag);h.assertTrue(f.area.level.addFreshEntity(f.blast),"restored native blast");f.area.entities.add(f.blast);f.changed=true;}
        if(age==2&&!f.checked){f.offsets[4]=new Vec3(0,0,6);f.position(4);f.checked=true;}
        if(age>=8){h.assertTrue(f.changed&&f.checked,"reload and late entry happened");f.loss(0,100);f.loss(1,20);f.loss(2,7);f.loss(3,3);f.loss(4,7);f.pass("reload-and-late-entry");}
    });}
    @GameTest(template="helicopter_arena",batch="blastWall",timeoutTicks=260)
    public static void laterRingsRespectAnActualWallAndCannotHitTheirOriginator(GameTestHelper h){run(h,2,f->{
        if(f.frame==1){f.offsets[3]=new Vec3(8,0,0);f.owner.setPos(f.center.add(0,-1,.5));for(int y=96;y<=104;y++)for(int z=-3;z<=3;z++)f.area.level.setBlock(new BlockPos(f.area.x+4,y,z),Blocks.BEDROCK.defaultBlockState(),2);}
        f.owner.setDeltaMovement(Vec3.ZERO);f.owner.invulnerableTime=0;
        if(f.frame==5)f.launch();if(f.blast!=null&&f.blast.blastAge()>=8){f.loss(0,100);f.loss(1,20);f.loss(2,7);f.loss(3,0);f.loss(4,0);h.assertTrue(f.owner.getHealth()==f.ownerHealth,"originator remains protected across all rings");f.pass("wall-and-owner-protection");}
    });}
    @GameTest(template="helicopter_arena",batch="blastOwner",timeoutTicks=260)
    public static void removingTheDamageOwnerCancelsPendingOuterDamage(GameTestHelper h){run(h,3,f->{
        if(f.frame==5){f.launch();f.owner.discard();}
        if(f.blast!=null&&f.frame>=10){h.assertTrue(f.blast.isRemoved(),"orphaned blast removed");f.loss(0,100);for(int i=1;i<5;i++)f.loss(i,0);f.pass("missing-owner");}
    });}
    @GameTest(template="helicopter_arena",batch="blastExpiry",timeoutTicks=320)
    public static void realBlastExpiresAtThreeSecondsAndAStaleReloadCannotReplayDamage(GameTestHelper h){run(h,4,f->{
        if(f.frame==5){f.launch();var tag=new CompoundTag();f.blast.saveWithoutId(tag);tag.putUUID("UUID",UUID.randomUUID());tag.putLong("BlastLastTick",f.area.level.getGameTime()-100);var stale=ModEntities.HELLFIRE_BLAST.get().create(f.area.level);stale.load(tag);f.area.level.addFreshEntity(stale);f.area.entities.add(stale);}
        if(f.blast!=null&&f.blast.blastAge()>=59&&!f.blast.isRemoved())f.checked=true;
        if(f.frame>=68){h.assertTrue(f.checked&&f.blast.isRemoved(),"native entity remains through final lifetime then removes");f.loss(0,100);f.loss(1,20);f.loss(2,7);f.loss(3,3);f.loss(4,0);f.pass("lifetime-and-stale-reload");}
    });}
    @GameTest(template="helicopter_arena",batch="blastHulls",timeoutTicks=280)
    public static void outerRingHitsTheRealTankHullOnceAndKeepsBothCrewsProtected(GameTestHelper h){run(h,5,f->{
        if(f.frame==1){
            f.tank=ModEntities.BLACKWATCH_TANK.get().create(f.area.level);f.tank.setPos(f.center.add(8,-1,0));f.area.level.addFreshEntity(f.tank);f.area.entities.add(f.tank);h.assertTrue(f.targets[2].startRiding(f.tank,true),"infected crew boards hostile tank");f.tankHealth=f.tank.health();
            f.friend=ModEntities.BLACKWATCH_TANK.get().create(f.area.level);f.friend.setPos(f.center.add(-8,-1,0));f.area.level.addFreshEntity(f.friend);f.area.entities.add(f.friend);h.assertTrue(f.owner.startRiding(f.friend,true),"originator boards protected tank");f.friendHealth=f.friend.health();
        }
        if(f.tank!=null){f.tank.setPos(f.center.add(8,-1,0));f.tank.setDeltaMovement(Vec3.ZERO);f.friend.setPos(f.center.add(-8,-1,0));f.friend.setDeltaMovement(Vec3.ZERO);}
        if(f.frame==5){
            var shape=com.codex.blacklight.military.vehicle.VehicleHullShape.from(f.tank.definition(),f.tank.turretYaw(),f.tank.gunPitch());
            var surface=shape.closestPoint(new com.codex.blacklight.military.vehicle.VehicleDefinition.Vec3(f.tank.getX(),f.tank.getY(),f.tank.getZ()),f.tank.getYRot(),new com.codex.blacklight.military.vehicle.VehicleDefinition.Vec3(f.center.x,f.center.y,f.center.z));
            double distance=f.center.distanceTo(new Vec3(surface.x(),surface.y(),surface.z()));h.assertTrue(distance>4&&distance<7,"actual oriented hull lies in source third ring: "+distance);f.launch();
        }
        if(f.blast!=null&&f.blast.blastAge()>=8){h.assertTrue(Math.abs(f.tankHealth-f.tank.health()-.7)<.01,"outer 1.4 damage passes heavy armor once");h.assertTrue(f.friend.health()==f.friendHealth,"allied hull remains protected in later rings");f.loss(2,0);h.assertTrue(f.owner.getHealth()==f.ownerHealth,"seated crews cannot receive simultaneous hull and living damage");f.pass("outer-ring-hulls");}
    });}
}
