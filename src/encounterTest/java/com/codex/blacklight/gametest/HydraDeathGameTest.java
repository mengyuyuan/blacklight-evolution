package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.HydraDeath;
import com.codex.blacklight.entity.BlacklightHydra;
import com.codex.blacklight.firearms.OrdnanceProjectile;
import com.codex.blacklight.firearms.WeaponDefinition;
import com.codex.blacklight.mixin.LivingDeathAccess;
import com.codex.blacklight.registry.ModEntities;
import java.util.Arrays;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.*;

/** Pending real damage/lifetime/load regressions. Never run by the asset exporter. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false) @EventBusSubscriber(modid="blacklight")
public final class HydraDeathGameTest {
    private static BlacklightHydra canceled;
    private static BlacklightHydra repeated,replaced,recordDrops;
    private static CompoundTag replacement,dropSnapshot;
    private static int repeatedEvents,dropEvents;
    private static boolean deathAccepted(LivingEntity entity){
        return ((LivingDeathAccess)entity).blacklight$deathAccepted();
    }
    @SubscribeEvent public static void cancelFixtureDeath(LivingDeathEvent event){
        if(event.getEntity()==canceled)event.setCanceled(true);
        if(event.getEntity()==repeated){repeatedEvents++;if(repeatedEvents==1)repeated.die(event.getSource());}
        if(event.getEntity()==replaced){var actor=replaced;var state=replacement;replaced=null;replacement=null;actor.load(state.copy());}
    }
    @SubscribeEvent public static void saveDuringRewardFixture(LivingDropsEvent event){
        if(event.getEntity()==recordDrops){dropEvents++;dropSnapshot=save(recordDrops);}
    }
    private static BlacklightHydra actor(GameTestHelper h,int x){
        var hydra=ModEntities.BLACKLIGHT_HYDRA.get().create(h.getLevel());hydra.setNoAi(true);hydra.setNoGravity(true);hydra.setBodySize(2);
        hydra.setPos(h.absoluteVec(new Vec3(x,5,12)));hydra.setYRot(37);hydra.yBodyRot=hydra.yBodyRotO=37;
        h.getLevel().addFreshEntity(hydra);return hydra;
    }
    private static CompoundTag save(Entity entity){var tag=new CompoundTag();entity.saveWithoutId(tag);return tag;}
    private static void kill(GameTestHelper h,BlacklightHydra hydra){
        h.assertTrue(hydra.hurt(hydra.damageSources().genericKill(),hydra.getMaxHealth()*10),"fatal real damage accepted");
        h.assertTrue(!hydra.isAlive()&&hydra.nativeDying()&&hydra.nativeDeathAge(0)==0,"accepted ordinary death starts source playback exactly once");
    }
    private static void awaitAge(GameTestHelper h,BlacklightHydra hydra,int age,Runnable then){
        h.assertTrue(!hydra.isRemoved(),"Hydra remains present until the complete source chain finishes");
        if(hydra.nativeDeathAge(0)>=age)then.run();else h.runAfterDelay(1,()->awaitAge(h,hydra,age,then));
    }
    @GameTest(template="blackwatch_facility_arena",timeoutTicks=130)
    public static void normalKillFinishesDeathThenSinksBeforeRemoval(GameTestHelper h){
        var hydra=actor(h,14);Vec3 root=hydra.position();kill(h,hydra);
        awaitAge(h,hydra,24,()->{
            h.assertTrue(!hydra.isRemoved()&&hydra.getParts().length>0&&Arrays.stream(hydra.getParts()).noneMatch(Entity::isPickable),"past vanilla20ticks, corpse remains visible but cannot receive another hit");
            awaitAge(h,hydra,86,()->{
                h.assertTrue(hydra.position().distanceToSqr(root)<1e-10,"root is stable; actual source skeletal exit lowers the body");
                h.assertTrue(hydra.hurtRegions(0).stream().allMatch(r->r.bounds().maxY<root.y),"rendered receiving cubes are underground before removal");
                awaitAge(h,hydra,HydraDeath.TOTAL_TICKS-1,()->h.runAfterDelay(2,()->{
                    h.assertTrue(hydra.isRemoved(),"source sink-node Death finally removes Hydra");h.succeed();
                }));
            });
        });
    }
    @GameTest(template="blackwatch_facility_arena",timeoutTicks=100)
    public static void sameObjectLoadPreservesSinkAgeAndHealthyLoadClearsDeath(GameTestHelper h){
        var hydra=actor(h,14);var healthy=save(hydra);kill(h,hydra);
        awaitAge(h,hydra,72,()->{
            var saved=save(hydra);var pose=hydra.motionPose(0);float age=hydra.nativeDeathAge(0);
            hydra.load(saved.copy());
            h.assertTrue(hydra.nativeDeathAge(0)==age,"loading an accepted corpse resumes the same observed age");
            for(int i=0;i<pose.length;i++)h.assertTrue(Arrays.equals(pose[i],hydra.motionPose(0)[i]),"source sink pose does not restart at bone"+i);
            hydra.load(healthy.copy());
            h.assertTrue(hydra.isAlive()&&!hydra.nativeDying(),"same-object healthy load clears stale death state");
            kill(h,hydra);hydra.discard();h.succeed();
        });
    }
    @GameTest(template="blackwatch_facility_arena",timeoutTicks=30)
    public static void canceledDeathCannotStartNativePresentation(GameTestHelper h){
        var hydra=actor(h,14);canceled=hydra;
        try{hydra.hurt(hydra.damageSources().genericKill(),hydra.getMaxHealth()*10);}
        finally{canceled=null;}
        h.assertTrue(!hydra.nativeDying(),"zero HP from canceled LivingDeath is not an accepted source death");
        hydra.setHealth(hydra.getMaxHealth());hydra.invulnerableTime=0;kill(h,hydra);hydra.discard();h.succeed();
    }
    @GameTest(template="blackwatch_facility_arena",timeoutTicks=30)
    public static void ordinaryExplosionDoesNotGetMisclassifiedAsThermobaric(GameTestHelper h){
        var normal=actor(h,10);var thermal=actor(h,20);var owner=EntityType.PIG.create(h.getLevel());owner.setPos(h.absoluteVec(new Vec3(4,5,4)));h.getLevel().addFreshEntity(owner);
        var rocket=new OrdnanceProjectile(h.getLevel(),owner,WeaponDefinition.Kind.ROCKET);
        var thermobaric=new OrdnanceProjectile(h.getLevel(),owner,WeaponDefinition.Kind.THERMOBARIC);
        try{
            normal.hurt(normal.damageSources().explosion(rocket,owner),10000);
            thermal.hurt(thermal.damageSources().explosion(thermobaric,owner),10000);
            h.assertTrue(!normal.isAlive()&&normal.nativeDying()&&normal.nativeDeathKind()==HydraDeath.NORMAL,"ordinary explosive kill uses the proven normal FIG branch");
            h.assertTrue(!thermal.isAlive()&&thermal.nativeDying()&&thermal.nativeDeathKind()==HydraDeath.THERMOBARIC,"explicit thermobaric kind selects its separate source group clock, never the normal sink clock");h.succeed();
        }finally{normal.discard();thermal.discard();owner.discard();rocket.discard();thermobaric.discard();}
    }
    @GameTest(template="blackwatch_facility_arena",timeoutTicks=30)
    public static void reentrantDieFromDeathEventDoesNotDuplicateEventsOrRewards(GameTestHelper h){
        var hydra=actor(h,14);repeated=recordDrops=hydra;repeatedEvents=dropEvents=0;dropSnapshot=null;
        try{
            kill(h,hydra);hydra.die(hydra.damageSources().genericKill());
            h.assertTrue(repeatedEvents==1&&dropEvents==1,"same-generation recursive or repeated die produces one accepted callback and one reward transaction");
            h.assertTrue(hydra.nativeDeathAge(0)==0,"recursive die does not restart/advance source presentation");h.succeed();
        }finally{repeated=recordDrops=null;hydra.discard();}
    }
    @GameTest(template="blackwatch_facility_arena",timeoutTicks=30)
    public static void healthyLoadInsideUncanceledDeathEventRejectsTheStaleKill(GameTestHelper h){
        var hydra=actor(h,14);replacement=save(hydra);replaced=recordDrops=hydra;dropEvents=0;dropSnapshot=null;
        try{
            hydra.hurt(hydra.damageSources().genericKill(),10000);
            h.assertTrue(replaced==null&&hydra.isAlive()&&!hydra.nativeDying(),"event callback restored the same object without canceling the event");
            h.assertTrue(!deathAccepted(hydra)&&dropEvents==0,"post-event guard prevents stale dead flag, kill score and reward body");
            hydra.invulnerableTime=0;kill(h,hydra);
            h.assertTrue(dropEvents==1,"the loaded living generation can subsequently accept one actual death");h.succeed();
        }finally{replaced=recordDrops=null;replacement=null;hydra.discard();}
    }
    @GameTest(template="blackwatch_facility_arena",timeoutTicks=30)
    public static void rewardCallbackSaveAlreadyContainsAcceptedNativeDeathClock(GameTestHelper h){
        var hydra=actor(h,14);recordDrops=hydra;dropEvents=0;dropSnapshot=null;BlacklightHydra restored=null;
        try{
            kill(h,hydra);
            h.assertTrue(dropEvents==1&&dropSnapshot!=null&&dropSnapshot.contains("HydraNativeDeathAge",3),"reward callback sees the committed source clock, not an untagged legacy corpse");
            restored=ModEntities.BLACKLIGHT_HYDRA.get().create(h.getLevel());restored.load(dropSnapshot.copy());
            h.assertTrue(restored.nativeDying()&&restored.nativeDeathAge(0)==0&&!restored.isAlive(),"snapshot taken inside accepted rewards resumes the complete source death chain");
            h.assertTrue(deathAccepted(restored)&&dropEvents==1,"restore does not invoke another reward transaction");h.succeed();
        }finally{recordDrops=null;hydra.discard();if(restored!=null)restored.discard();}
    }
    @GameTest(template="blackwatch_facility_arena",timeoutTicks=65)
    public static void thermobaricBodyHidesBeforeRemovalAndReloadKeepsItsType(GameTestHelper h){
        var hydra=actor(h,14);var owner=EntityType.PIG.create(h.getLevel());owner.setPos(h.absoluteVec(new Vec3(4,5,4)));h.getLevel().addFreshEntity(owner);
        var projectile=new OrdnanceProjectile(h.getLevel(),owner,WeaponDefinition.Kind.THERMOBARIC);
        recordDrops=hydra;dropEvents=0;dropSnapshot=null;
        try{
            h.assertTrue(hydra.hurt(hydra.damageSources().explosion(projectile,owner),10000),"explicit thermobaric fatal hit accepted");
            h.assertTrue(dropEvents==1&&dropSnapshot!=null&&dropSnapshot.getInt("HydraNativeDeathKind")==HydraDeath.THERMOBARIC,"special type is committed before reward callback saves age0");
        }finally{recordDrops=null;owner.discard();projectile.discard();}
        awaitAge(h,hydra,23,()->{
            h.assertTrue(!hydra.nativeDeathBodyHidden(0),"nine body skins remain shown before source hide time");
            awaitAge(h,hydra,24,()->{
                h.assertTrue(hydra.nativeDeathBodyHidden(0)&&!hydra.isRemoved(),"body-only hide is not the entity Death track");
                h.assertTrue(Arrays.stream(hydra.getParts()).noneMatch(Entity::isPickable),"accepted corpse remains unpickable independent of later visibility change");
                var saved=save(hydra);float age=hydra.nativeDeathAge(0);var pose=hydra.motionPose(0);hydra.load(saved.copy());
                h.assertTrue(hydra.nativeDeathKind()==HydraDeath.THERMOBARIC&&hydra.nativeDeathAge(0)==age&&hydra.nativeDeathBodyHidden(0),"reload does not flash body, restart group or switch to normal sink");
                for(int i=0;i<pose.length;i++)h.assertTrue(Arrays.equals(pose[i],hydra.motionPose(0)[i]),"thermal death pose resumes at bone"+i);
                awaitAge(h,hydra,39,()->h.runAfterDelay(2,()->{
                    h.assertTrue(hydra.isRemoved(),"nested Death local1s removes at total2s rather than vanilla20 or ordinary103ticks");h.succeed();
                }));
            });
        });
    }
    @GameTest(template="blackwatch_facility_arena",timeoutTicks=30)
    public static void oldOrdinaryAgesAndInvalidSavedTypesDoNotBecomeThermobaric(GameTestHelper h){
        var hydra=actor(h,14);var healthy=save(hydra);kill(h,hydra);var saved=save(hydra);
        saved.putInt("HydraNativeDeathAge",72);saved.remove("HydraNativeDeathKind");hydra.load(saved.copy());
        h.assertTrue(hydra.nativeDying()&&hydra.nativeDeathKind()==HydraDeath.NORMAL&&hydra.nativeDeathAge(0)==72&&!hydra.nativeDeathBodyHidden(0),"previous age-only schema means accepted ordinary death");
        saved.putInt("HydraNativeDeathKind",99);hydra.load(saved.copy());h.assertTrue(!hydra.nativeDying(),"unknown present type is not guessed as another source branch");
        saved.putString("HydraNativeDeathKind","1");hydra.load(saved.copy());h.assertTrue(!hydra.nativeDying(),"wrong tag type cannot select thermal presentation");
        saved.putInt("HydraNativeDeathKind",HydraDeath.THERMOBARIC);saved.putInt("HydraNativeDeathAge",-1);hydra.load(saved.copy());h.assertTrue(!hydra.nativeDying(),"negative accepted age cannot restart a fresh death");
        saved.putInt("HydraNativeDeathAge",200);hydra.load(saved.copy());h.assertTrue(hydra.nativeDeathAge(0)==40&&hydra.nativeDeathBodyHidden(0),"oversized age is capped at its own branch end");
        hydra.load(healthy.copy());h.assertTrue(hydra.isAlive()&&!hydra.nativeDying()&&!hydra.nativeDeathBodyHidden(0),"healthy same-object generation clears special visibility");
        hydra.discard();h.succeed();
    }
}
