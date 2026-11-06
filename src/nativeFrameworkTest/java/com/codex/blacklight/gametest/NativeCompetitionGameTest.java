package com.codex.blacklight.gametest;

import com.codex.blacklight.config.InfectionEcologyConfig;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class NativeCompetitionGameTest {
    private static List<? extends String> preserve(){
        var old=InfectionEcologyConfig.PRESERVE.get();InfectionEcologyConfig.PRESERVE.set(List.of("minecraft:cow","minecraft:pig","minecraft:sheep","minecraft:iron_golem"));return old;
    }
    private static NativeTestArena floor(GameTestHelper h){
        var arena=NativeTestArena.open(h,0,20);
        for(int x=1;x<15;x++)for(int z=1;z<15;z++){
            h.setBlock(new BlockPos(x,1,z),Blocks.STONE);for(int y=2;y<10;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }return arena;
    }
    private static Mob nativeMob(GameTestHelper h,EntityType<? extends Mob> type,TissueCore core,double x,double z){
        var mob=type.create(h.getLevel());mob.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,2,2))).add(x,0,z));
        h.getLevel().addFreshEntity(mob);BlacklightInfectionSystem.infect(mob,null);
        var state=new CompoundTag();state.putLong("Age",72000);state.putString("Core",core.key());state.putInt("Nutrition",200);
        mob.getPersistentData().put("BlacklightNativeInfection",state);NativeInfection.tick(mob,h.getLevel());
        mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);return mob;
    }
    private static BlacklightInfectedHost converted(GameTestHelper h,TissueCore core,double x,double z){
        var mob=ModEntities.INFECTED_HOST.get().create(h.getLevel());mob.inherit(EntityType.COW.create(h.getLevel()));
        var tag=new CompoundTag();mob.saveWithoutId(tag);tag.getCompound("BlacklightHost").putString("NativeCore",core.key());mob.readAdditionalSaveData(tag);
        mob.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,2,2))).add(x,0,z));mob.advanceTissue(72000);
        mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);h.getLevel().addFreshEntity(mob);return mob;
    }
    private static void weak(Mob prey){prey.setHealth(prey.getMaxHealth()*.1F);}
    private static void clean(Mob... mobs){for(var m:mobs){TissueCompetition.interrupt(m);m.discard();}}
    @GameTest(template="wall_test",timeoutTicks=200,batch="native_competition_settlements")
    public static void realDeathsSettleBothNativeAndConvertedDirectionsExactlyOnce(GameTestHelper h){
        var old=preserve();var arena=floor(h);
        var a=nativeMob(h,EntityType.COW,TissueCore.MUSCLE,2,2);var b=nativeMob(h,EntityType.PIG,TissueCore.GRASP,2.8,2);
        var c=nativeMob(h,EntityType.IRON_GOLEM,TissueCore.MUSCLE,7,2);var d=converted(h,TissueCore.JAW,7.8,2);
        var e=converted(h,TissueCore.MUSCLE,2,8);var f=nativeMob(h,EntityType.SHEEP,TissueCore.CARAPACE,2.8,8);
        var actors=List.of(a,c,e);var donors=List.of(b,d,f);var all=new Mob[]{a,b,c,d,e,f};
        // Real AI starts a capture from a live entity tick, not immediately after
        // publication into an asynchronously loaded GameTest border chunk.
        for(var mob:all)mob.setNoAi(true);
        h.runAfterDelay(195,()->{clean(all);InfectionEcologyConfig.PRESERVE.set(old);arena.close();});
        h.startSequence().thenWaitUntil(()->h.assertTrue(Arrays.stream(all).allMatch(m->m.tickCount>0&&h.getLevel().isPositionEntityTicking(m.blockPosition())),"all mixed-scene actors must actually tick before the scene starts"))
            .thenExecute(()->{
        for(var mob:all)mob.setNoAi(false);
        try{
            for(int i=0;i<3;i++){
                var actor=actors.get(i);var prey=donors.get(i);weak(prey);
                h.assertTrue(TissueCompetition.tryStart(actor,prey),"mixed/native live capture starts: "+i
                        +" subjects="+(TissueSubject.of(actor)!=null)+"/"+(TissueSubject.of(prey)!=null)
                        +" participants="+TissueCompetition.participant(actor)+"/"+TissueCompetition.participant(prey)
                        +" contender="+TissueCompetition.contender(actor,prey)+" widths="+actor.getBbWidth()+"/"+prey.getBbWidth()
                        +" heights="+actor.getBbHeight()+"/"+prey.getBbHeight()+" los="+actor.hasLineOfSight(prey)
                        +" contact="+actor.getBoundingBox().inflate(.75,.35,.75).intersects(prey.getBoundingBox())
                        +" health="+prey.getHealth()+"/"+prey.getMaxHealth());
            }
        }catch(Throwable failure){clean(all);InfectionEcologyConfig.PRESERVE.set(old);arena.close();throw failure;}
        h.runAfterDelay(105,()->{
            try{
                for(int i=0;i<3;i++){
                    var actor=actors.get(i);var prey=donors.get(i);var genes=TissueSubject.of(actor).inheritance();
                    h.assertTrue(!prey.isAlive()&&prey.getKillCredit()==actor&&genes.meals()==1&&genes.acquiredOrgans()==1,
                            "accepted actual death credits one donor in direction "+i+" alive="+prey.isAlive()+" meals="+genes.meals()
                            +" controlled="+TissueCompetition.controls(actor)+"/"+TissueCompetition.controls(prey)
                            +" positions="+actor.position()+"/"+prey.position()+" ticks="+actor.tickCount+"/"+prey.tickCount
                            +" nativeScene="+NativeTissue.snapshot(prey)+" health="+prey.getHealth()+"/"+prey.getMaxHealth());
                    h.assertTrue(genes.organs().values().stream().anyMatch(o->o.pending()>0),"genes integrate gradually, not a fake instant level");
                    h.assertTrue(!TissueCompetition.controls(actor)&&!TissueCompetition.controls(prey),"both claims released");
                    TissueCompetition.tick(actor);h.assertTrue(TissueSubject.of(actor).inheritance().meals()==1,"repeat ticks cannot replay settlement");
                }
                h.assertTrue(a.getType()==EntityType.COW&&c.getType()==EntityType.IRON_GOLEM,"original runtime entities and types were retained");
                var incoming=a.damageSources().mobAttack(b);
                h.assertTrue(TissueCompetition.finalDamage(a,incoming,10)<10,"real accepted consumption has its configured temporary recovery protection");
                a.removeEffect(com.codex.blacklight.registry.ModEffects.BLACKLIGHT_INFECTION.get());NativeInfection.clear(a);
                h.assertTrue(TissueCompetition.finalDamage(a,incoming,10)==10,"cure also removes its own transient recovery protection");h.succeed();
            }finally{clean(all);InfectionEcologyConfig.PRESERVE.set(old);arena.close();}
        });
        });
    }
    @GameTest(template="wall_test",timeoutTicks=145,batch="native_competition_veto")
    public static void nativeDeathVetoPaysNothingAndRestoresSourceGoalOwnership(GameTestHelper h){
        var old=preserve();var arena=floor(h);var actor=nativeMob(h,EntityType.COW,TissueCore.MUSCLE,2,2);var prey=nativeMob(h,EntityType.PIG,TissueCore.JAW,2.8,2);
        var sourceGoals=List.copyOf(actor.goalSelector.getAvailableGoals()).stream().map(w->w.getGoal()).filter(g->!g.getClass().getName().contains("com.codex.blacklight.infection.Native")).toList();
        Consumer<LivingDeathEvent> veto=event->{if(event.getEntity()==prey){event.setCanceled(true);prey.setHealth(prey.getMaxHealth());}};
        MinecraftForge.EVENT_BUS.addListener(veto);weak(prey);
        h.assertTrue(TissueCompetition.tryStart(actor,prey),"native capture starts before actual veto");
        h.runAfterDelay(105,()->{
            try{
                h.assertTrue(prey.isAlive()&&NativeTissue.inheritance(actor).meals()==0&&!TissueCompetition.controls(prey),"cancelled death pays no organ and releases claims");
                h.assertTrue(!prey.getPersistentData().getBoolean("BlacklightCannibalSettled"),"settlement marker is rolled back");
                actor.removeEffect(com.codex.blacklight.registry.ModEffects.BLACKLIGHT_INFECTION.get());NativeInfection.clear(actor);
                h.assertTrue(sourceGoals.stream().allMatch(g->actor.goalSelector.getAvailableGoals().stream().anyMatch(w->w.getGoal()==g)),"source goal instances survive capture and cure");
                h.assertTrue(actor.goalSelector.getAvailableGoals().stream().noneMatch(w->w.getGoal().getClass().getName().contains("NativeTissueCompetitionGoal")),"cure removes only our reversible competition goals");h.succeed();
            }finally{MinecraftForge.EVENT_BUS.unregister(veto);clean(actor,prey);InfectionEcologyConfig.PRESERVE.set(old);arena.close();}
        });
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="native_competition_lifecycle")
    public static void realNativeHeavyDamageReloadAndTargetUnloadCannotLeaveClaims(GameTestHelper h){
        var old=preserve();var arena=floor(h);var actor=nativeMob(h,EntityType.COW,TissueCore.MUSCLE,2,2);var prey=nativeMob(h,EntityType.PIG,TissueCore.JAW,2.8,2);
        try{
            weak(prey);h.assertTrue(TissueCompetition.tryStart(actor,prey),"capture starts");
            float before=actor.getHealth();actor.hurt(actor.damageSources().generic(),actor.getMaxHealth()*.4F);
            h.assertTrue(actor.getHealth()<before&&!TissueCompetition.controls(actor)&&!TissueCompetition.controls(prey),"a real heavy native hit interrupts both sides without granting immunity");
            h.assertTrue(NativeTissue.inheritance(actor).meals()==0,"interrupt pays no genes");
            var other=nativeMob(h,EntityType.COW,TissueCore.MUSCLE,2,2);
            try{
                h.assertTrue(TissueCompetition.tryStart(other,prey),"new owner starts after release");
                var saved=new CompoundTag();prey.saveWithoutId(saved);prey.load(saved);
                h.assertTrue(!TissueCompetition.controls(other)&&!TissueCompetition.controls(prey),"reloading a native instance releases both claims");
                h.assertTrue(NativeTissue.snapshot(other).getInt("CannibalTarget")==-1,"native presentation is reset too");
                var third=nativeMob(h,EntityType.COW,TissueCore.MUSCLE,2,2);
                try{
                    h.assertTrue(TissueCompetition.tryStart(third,prey),"another capture starts");prey.discard();
                    h.assertTrue(!TissueCompetition.controls(third)&&NativeTissue.inheritance(third).meals()==0,"native target unload releases immediately and never pays a donor");
                }finally{clean(third);}
            }finally{clean(other);}
            h.succeed();
        }finally{clean(actor,prey);InfectionEcologyConfig.PRESERVE.set(old);arena.close();}
    }
    @GameTest(template="wall_test",timeoutTicks=180,batch="native_competition_autonomous")
    public static void matureUnarmedNativeHostsChooseAndAttackWithoutAnAdminTarget(GameTestHelper h){
        var old=preserve();var arena=floor(h);var a=nativeMob(h,EntityType.COW,TissueCore.MUSCLE,2,2);var b=nativeMob(h,EntityType.COW,TissueCore.MUSCLE,2.8,2);
        h.runAfterDelay(120,()->{
            try{
                h.assertTrue(a.getTarget()==b||b.getTarget()==a||TissueCompetition.controls(a)||TissueCompetition.controls(b),"native mature competition selects its own prey");
                h.assertTrue(a.getHealth()<a.getMaxHealth()||b.getHealth()<b.getMaxHealth()||!a.isAlive()||!b.isAlive(),"cooperative unarmed goal deals actual damage, not just a target or animation");h.succeed();
            }finally{clean(a,b);InfectionEcologyConfig.PRESERVE.set(old);arena.close();}
        });
    }
    @GameTest(template="wall_test",timeoutTicks=1500,batch="native_competition_six_real_meals")
    public static void sixRealCapturesIntegrateSeparateOrgansBeforeTheNextHunt(GameTestHelper h){
        var old=preserve();var speed=com.codex.blacklight.config.GameplayBalanceConfig.INTEGRATION_SPEED.get();
        com.codex.blacklight.config.GameplayBalanceConfig.INTEGRATION_SPEED.set(100D);
        var arena=floor(h);var actor=nativeMob(h,EntityType.IRON_GOLEM,TissueCore.MUSCLE,4,4);
        var spawned=new ArrayList<Mob>();spawned.add(actor);
        var cores=new TissueCore[]{TissueCore.JAW,TissueCore.CARAPACE,TissueCore.GRASP,TissueCore.CREST,TissueCore.BLADE,TissueCore.HORN};
        Runnable cleanup=()->{clean(spawned.toArray(Mob[]::new));InfectionEcologyConfig.PRESERVE.set(old);arena.close();com.codex.blacklight.config.GameplayBalanceConfig.INTEGRATION_SPEED.set(speed);};
        class Meals {
            void next(int index){
                try{
                    var subject=TissueSubject.of(actor);
                    h.assertTrue(subject!=null&&subject.inheritance().readyToHunt(subject.age()),"previous meal has fully formed before capture "+index);
                    var prey=index%2==0?nativeMob(h,EntityType.COW,cores[index],4.8,4):converted(h,cores[index],4.8,4);
                    spawned.add(prey);weak(prey);
                    h.assertTrue(TissueCompetition.tryStart(actor,prey),"real capture "+index+" starts without direct inheritance calls");
                    h.runAfterDelay(110,()->{
                        try{
                            var genes=NativeTissue.inheritance(actor);
                            h.assertTrue(!prey.isAlive()&&prey.getKillCredit()==actor&&genes.meals()==index+1,
                                    "only accepted death awards real meal "+index+"; meals="+genes.meals());
                            if(index<5)h.runAfterDelay(110,()->next(index+1));
                            else{
                                h.assertTrue(genes.acquiredOrgans()>=6&&genes.organs().keySet().containsAll(List.of(cores)),"six real donors retain six separate organ categories");
                                var saved=new CompoundTag();actor.saveWithoutId(saved);var restored=EntityType.IRON_GOLEM.create(h.getLevel());
                                try{
                                    restored.load(saved);h.assertTrue(NativeTissue.inheritance(restored).meals()==6&&NativeTissue.inheritance(restored).acquiredOrgans()>=6,
                                            "real six-capture inventory survives actual entity NBT roundtrip");
                                }finally{restored.discard();}
                                h.runAfterDelay(120,()->{try{
                                    var finalSubject=TissueSubject.of(actor);var finalGenes=NativeTissue.inheritance(actor);
                                    h.assertTrue(finalSubject!=null&&finalGenes.readyToHunt(finalSubject.age())&&finalGenes.hasMatureInheritance()
                                            &&java.util.Arrays.stream(cores).allMatch(core->finalSubject.power(core)>0),"all six real acquired organs finish integration and have functional power");
                                    h.assertTrue(actor.getType()==EntityType.IRON_GOLEM&&!finalSubject.evolved(),"source-preservation route retains native anatomy and skills after the terminal threshold; it does not fake a humanoid conversion");
                                    h.succeed();
                                }finally{cleanup.run();}});
                            }
                        }catch(Throwable failure){cleanup.run();throw failure;}
                    });
                }catch(Throwable failure){cleanup.run();throw failure;}
            }
        }
        new Meals().next(0);
    }

    @GameTest(template="wall_test",timeoutTicks=200,batch="native_competition_inertia")
    public static void acceptedSceneStopsExistingHorizontalInertiaButKeepsGravityAndLaterReleasesMotion(GameTestHelper h){
        var old=preserve();var arena=floor(h);
        var actor=nativeMob(h,EntityType.COW,TissueCore.MUSCLE,2,2);var prey=nativeMob(h,EntityType.PIG,TissueCore.GRASP,2.8,2);
        actor.setNoAi(true);prey.setNoAi(true);var closed=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable close=()->{if(closed.getAndSet(true))return;clean(actor,prey);InfectionEcologyConfig.PRESERVE.set(old);arena.close();};h.runAfterDelay(195,close);
        h.startSequence().thenWaitUntil(()->h.assertTrue(actor.tickCount>0&&prey.tickCount>0&&h.getLevel().isPositionEntityTicking(prey.blockPosition()),"both real source actors are entity-ticking"))
            .thenExecute(()->{try{
                actor.setNoAi(false);prey.setNoAi(false);weak(prey);
                actor.setDeltaMovement(new net.minecraft.world.phys.Vec3(.3,-.08,.1));prey.setDeltaMovement(new net.minecraft.world.phys.Vec3(-.2,-.08,-.1));
                h.assertTrue(TissueCompetition.tryStart(actor,prey),"real contact starts an accepted scene");
                h.assertTrue(actor.getDeltaMovement().horizontalDistanceSqr()<1e-12&&prey.getDeltaMovement().horizontalDistanceSqr()<1e-12,
                        "accepted scene must arrest preexisting horizontal inertia even with nonzero gravity; motion="+actor.getDeltaMovement()+"/"+prey.getDeltaMovement());
                h.assertTrue(actor.getDeltaMovement().y==-.08&&prey.getDeltaMovement().y==-.08,"owned arrest preserves gravity rather than suspending both mobs");
                TissueCompetition.interrupt(actor);var next=new net.minecraft.world.phys.Vec3(.12,-.08,0);actor.setDeltaMovement(next);
                h.assertTrue(actor.getDeltaMovement().equals(next)&&!TissueCompetition.controls(actor)&&!TissueCompetition.controls(prey),"interrupt releases control and later source motion remains usable");h.succeed();
            }finally{close.run();}});
    }
}
