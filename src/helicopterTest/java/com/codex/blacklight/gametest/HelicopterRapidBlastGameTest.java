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
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterRapidBlastGameTest {
    private static final class Fixture {
        final GameTestHelper h;final HelicopterPilotGameTest.Fixture area;final Vec3 center;
        final BlacklightCommonInfected[] targets=new BlacklightCommonInfected[4];final float[] health=new float[4];final Vec3[] offsets={new Vec3(1,0,0),new Vec3(0,0,3),new Vec3(-5,0,0),new Vec3(8,0,0)};
        BlackwatchSoldier owner;HellfireBlastEntity blast,stale;MilitaryVehicle tank,friend;boolean ready,done,late;int frame,reloads;float ownerHealth,tankHealth,friendHealth;
        Fixture(GameTestHelper h,int index){this.h=h;area=new HelicopterPilotGameTest.Fixture(h,310000+index*512);center=new Vec3(area.x,100,0);}
        boolean ready(){
            if(ready)return true;if(!area.level.isPositionEntityTicking(BlockPos.containing(center)))return false;ready=true;
            owner=ModEntities.BLACKWATCH_SOLDIER.get().create(area.level);owner.setPos(center.add(-16,0,-16));owner.setNoAi(true);owner.setNoGravity(true);owner.setAutomaticUnitJoin(false);area.level.addFreshEntity(owner);area.entities.add(owner);
            for(int i=0;i<4;i++){var t=ModEntities.COMMON_INFECTED.get().create(area.level);t.setNoAi(true);t.setNoGravity(true);t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);t.setHealth(1000);targets[i]=t;position(i);area.level.addFreshEntity(t);area.entities.add(t);}return true;
        }
        void position(int i){var t=targets[i];if(t.getVehicle()==null){t.setPos(center.add(offsets[i]).add(0,-t.getBbHeight()*.5,0));t.setDeltaMovement(Vec3.ZERO);}t.invulnerableTime=0;}
        void launch(){blast=HellfireBlastEntity.startRapid(area.level,owner,center,new Vec3(0,1,0));h.assertTrue(blast!=null&&blast.rapidBlast(),"actual rapid blast registered with separate profile");area.entities.add(blast);}
        void reload(){var tag=new CompoundTag();blast.saveWithoutId(tag);blast.discard();tag.putUUID("UUID",UUID.randomUUID());blast=ModEntities.HELLFIRE_BLAST.get().create(area.level);blast.load(tag);h.assertTrue(blast.rapidBlast()&&area.level.addFreshEntity(blast),"actual persisted70mm profile restored");area.entities.add(blast);reloads++;}
        void loss(int i,float expected){h.assertTrue(Math.abs(health[i]-targets[i].getHealth()-expected)<.02,"target"+i+" expected="+expected+" actual="+(health[i]-targets[i].getHealth())+" age="+blast.blastAge()+" source="+(targets[i].getLastDamageSource()==null?"none":targets[i].getLastDamageSource().getMsgId()));}
        void pass(String name){done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.info("RAPID_BLAST_VERIFIED case={} age={} health={} reloads={} removed={}",name,blast.blastAge(),Arrays.stream(targets).map(t->t.getHealth()).toList(),reloads,blast.isRemoved());area.close();h.succeed();}
    }
    private static void run(GameTestHelper h,int index,Consumer<Fixture> step){var f=new Fixture(h,index);h.onEachTick(()->{if(f.done)return;try{if(!f.ready())return;for(int i=0;i<4;i++)f.position(i);if(f.frame<5){for(int i=0;i<4;i++)f.health[i]=f.targets[i].getHealth();f.ownerHealth=f.owner.getHealth();}step.accept(f);f.frame++;}catch(RuntimeException ex){f.done=true;f.area.close();throw ex;}});}
    @GameTest(template="helicopter_arena",batch="rapidBlastRings",timeoutTicks=250)
    public static void delayedTwoRingsHitOnceDespiteResetHurtImmunityAndExpireAtOneSecond(GameTestHelper h){run(h,0,f->{
        if(f.frame==5)f.launch();if(f.blast==null)return;int age=f.blast.blastAge();
        f.loss(0,age>=9?20:0);f.loss(1,age>=10?10:0);f.loss(2,age>=10?10:0);f.loss(3,0);
        if(age>=20){h.assertTrue(f.blast.isRemoved(),"source Destroy1s removes actual entity");f.pass("delayed-two-rings-once");}
    });}
    @GameTest(template="helicopter_arena",batch="rapidBlastReload",timeoutTicks=250)
    public static void reloadAcrossDelayAndInnerContactPreservesTwoRingHistory(GameTestHelper h){run(h,1,f->{
        if(f.frame==5)f.launch();if(f.blast==null)return;int age=f.blast.blastAge();
        if(age==8&&f.reloads==0||age==9&&f.reloads==1)f.reload();
        if(age==10&&!f.late){f.offsets[3]=new Vec3(0,0,5);f.position(3);f.late=true;}
        if(age>=13){h.assertTrue(f.reloads==2&&f.late,"both native reloads and late entry occurred");f.loss(0,20);f.loss(1,10);f.loss(2,10);f.loss(3,10);f.pass("reload-delay-contact-late-entry");}
    });}
    @GameTest(template="helicopter_arena",batch="rapidBlastProtection",timeoutTicks=250)
    public static void delayedRingsRespectWallActualTankSurfaceAndProtectedCrews(GameTestHelper h){run(h,2,f->{
        if(f.frame==1){
            f.offsets[1]=new Vec3(0,0,4);f.position(1);
            f.tank=ModEntities.BLACKWATCH_TANK.get().create(f.area.level);f.tank.setPos(f.center.add(6.5,-1,0));f.area.level.addFreshEntity(f.tank);f.area.entities.add(f.tank);h.assertTrue(f.targets[2].startRiding(f.tank,true),"hostile tank crew boards");f.tankHealth=f.tank.health();
            f.friend=ModEntities.BLACKWATCH_TANK.get().create(f.area.level);f.friend.setPos(f.center.add(-6.5,-1,0));f.area.level.addFreshEntity(f.friend);f.area.entities.add(f.friend);h.assertTrue(f.owner.startRiding(f.friend,true),"originator boards protected hull");f.friendHealth=f.friend.health();
            for(int x=-2;x<=2;x++)for(int y=96;y<=104;y++)f.area.level.setBlock(new BlockPos(f.area.x+x,y,2),Blocks.BEDROCK.defaultBlockState(),2);
            h.assertTrue(f.area.level.noCollision(f.targets[1]),"occluded fixture body is behind wall, not suffocating inside it");
        }
        if(f.tank!=null){f.tank.setPos(f.center.add(6.5,-1,0));f.tank.setDeltaMovement(Vec3.ZERO);f.friend.setPos(f.center.add(-6.5,-1,0));f.friend.setDeltaMovement(Vec3.ZERO);}
        if(f.frame==5)f.launch();if(f.blast!=null&&f.blast.blastAge()>=13){
            f.loss(0,20);f.loss(1,0);f.loss(2,0);f.loss(3,0);h.assertTrue(Math.abs(f.tankHealth-f.tank.health()-1)<.01,"actual outer-ring tank surface takes2 MC damage through heavy armor once");
            h.assertTrue(f.friend.health()==f.friendHealth&&f.owner.getHealth()==f.ownerHealth,"delayed ring protects allied hull and both mounted crews");f.pass("wall-hull-crew-protection");}
    });}
    @GameTest(template="helicopter_arena",batch="rapidBlastOwner",timeoutTicks=250)
    public static void missingOwnerAndStaleReloadNeverDeliverPendingDamage(GameTestHelper h){run(h,3,f->{
        if(f.frame==5){f.launch();var tag=new CompoundTag();f.blast.saveWithoutId(tag);tag.putUUID("UUID",UUID.randomUUID());tag.putLong("BlastLastTick",f.area.level.getGameTime()-30);f.stale=ModEntities.HELLFIRE_BLAST.get().create(f.area.level);f.stale.load(tag);f.area.level.addFreshEntity(f.stale);f.area.entities.add(f.stale);}
        if(f.frame==8)f.owner.discard();
        if(f.blast!=null&&f.frame>=20){h.assertTrue(f.stale.isRemoved()&&f.blast.isRemoved(),"stale and orphaned native timelines discard");for(int i=0;i<4;i++)f.loss(i,0);f.pass("missing-owner-stale-load");}
    });}
}
