package com.codex.blacklight.gametest;

import com.codex.blacklight.config.InfectionEcologyConfig;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModEffects;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class NativeOrganActionsGameTest {
    private static final class Fixture {
        final GameTestHelper h;final List<? extends String> preserve;final Mob actor,prey;final NativeTestArena arena;boolean closed;
        Fixture(GameTestHelper h,TissueCore core){
            this.h=h;arena=NativeTestArena.open(h,0,20);preserve=InfectionEcologyConfig.PRESERVE.get();InfectionEcologyConfig.PRESERVE.set(List.of("minecraft:cow"));
            for(int x=1;x<15;x++)for(int z=1;z<15;z++){
                h.setBlock(new BlockPos(x,1,z),Blocks.STONE);for(int y=2;y<9;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
            }
            actor=EntityType.COW.create(h.getLevel());actor.setPos(h.absolutePos(new BlockPos(4,2,6)).getBottomCenter());h.getLevel().addFreshEntity(actor);
            h.assertTrue(BlacklightInfectionSystem.infect(actor,null),"native actor infected");
            var data=new CompoundTag();data.putLong("Age",72000);data.putString("Core",core.key());data.putInt("Nutrition",2400);
            actor.getPersistentData().put("BlacklightNativeInfection",data);NativeInfection.tick(actor,h.getLevel());
            double gap=core==TissueCore.HORN?5:core==TissueCore.TENDON||core==TissueCore.TONGUE?3:0;
            prey=EntityType.PIG.create(h.getLevel());prey.setPos(actor.position().add(.9+gap,0,0));prey.setNoAi(true);
            prey.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);prey.setHealth(10000);h.getLevel().addFreshEntity(prey);actor.setTarget(prey);
            h.runAfterDelay(185,this::close);
        }
        void close(){if(closed)return;closed=true;TissueCompetition.interrupt(actor);NativeTissueAbilityGoal.clear(actor);actor.discard();prey.discard();arena.close();InfectionEcologyConfig.PRESERVE.set(preserve);}
    }
    private static void action(GameTestHelper h,TissueCore core){
        var f=new Fixture(h,core);var nav=f.actor.getNavigation();var brain=f.actor.getBrain();var control=f.actor.getMoveControl();
        h.startSequence().thenWaitUntil(()->h.assertTrue(TissueActions.core(f.actor)==core,"real cooperative goal starts native "+core))
            .thenExecute(()->{
                h.assertTrue(TissueActions.target(f.actor)==f.prey.getId()&&TissueActions.elapsed(f.actor,0)>=0,"shared phase/target is synchronized");
                h.assertTrue(NativeTissue.nutrition(f.actor)<2400,"organ action pays nutrition");
                var saved=new CompoundTag();f.actor.saveWithoutId(saved);h.assertTrue(!saved.toString().contains("OrganAction"),"action target/clock is not saved as a replayable native scene");
            }).thenIdle(TissueOrganCycle.cycle(core).end()+5).thenExecute(()->{
                try{
                    h.assertTrue(f.prey.getHealth()<10000,"native "+core+" causes real accepted health loss, not only a playing action; pos="+f.actor.position()+" prey="+f.prey.position());
                    h.assertTrue(TissueActions.core(f.actor)==null&&TissueGrapples.owner(f.prey)==null,"action has finite withdrawal/recovery and releases its real tether");
                    if(core==TissueCore.CNIDOCYTE)h.assertTrue(f.prey.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"the real stinging-cell contact also applies its slowing effect");
                    h.assertTrue(f.actor.getNavigation()==nav&&f.actor.getBrain()==brain&&f.actor.getMoveControl()==control,"native navigation/brain/controller instances remain intact");
                    h.succeed();
                }finally{f.close();}
            });
    }
    @GameTest(template="wall_test",timeoutTicks=200,batch="native_organ_tendon") public static void tendonLeapsAndHits(GameTestHelper h){action(h,TissueCore.TENDON);}
    @GameTest(template="wall_test",timeoutTicks=200,batch="native_organ_hydraulic") public static void hydraulicStrikesAndHits(GameTestHelper h){action(h,TissueCore.HYDRAULIC);}
    @GameTest(template="wall_test",timeoutTicks=200,batch="native_organ_hydrostat") public static void hydrostatGripsCrushesAndReleases(GameTestHelper h){action(h,TissueCore.HYDROSTAT);}
    @GameTest(template="wall_test",timeoutTicks=200,batch="native_organ_blade") public static void bladeCleavesAndHits(GameTestHelper h){action(h,TissueCore.BLADE);}
    @GameTest(template="wall_test",timeoutTicks=200,batch="native_organ_chela") public static void chelaGripsCrushesAndReleases(GameTestHelper h){action(h,TissueCore.CHELA);}
    @GameTest(template="wall_test",timeoutTicks=200,batch="native_organ_horn") public static void hornChargesAndHits(GameTestHelper h){action(h,TissueCore.HORN);}
    @GameTest(template="wall_test",timeoutTicks=200,batch="native_organ_saddle") public static void saddleImpactsAndHits(GameTestHelper h){action(h,TissueCore.SADDLE);}
    @GameTest(template="wall_test",timeoutTicks=200,batch="native_organ_club") public static void clubImpactsAndHits(GameTestHelper h){action(h,TissueCore.CLUB);}
    @GameTest(template="wall_test",timeoutTicks=200,batch="native_organ_tongue") public static void tonguePullsCrushesAndReleases(GameTestHelper h){action(h,TissueCore.TONGUE);}
    @GameTest(template="wall_test",timeoutTicks=200,batch="native_organ_cnidocyte") public static void cnidocyteEnvenomsAndHits(GameTestHelper h){action(h,TissueCore.CNIDOCYTE);}
    @GameTest(template="wall_test",timeoutTicks=200,batch="native_organ_extraoral") public static void extraoralDosesEnzymesAndReleases(GameTestHelper h){action(h,TissueCore.EXTRAORAL);}
    @GameTest(template="wall_test",timeoutTicks=200,batch="native_organ_priority")
    public static void nativeEmergencyGoalKeepsPriorityThenOriginalActorUsesAnOrgan(GameTestHelper h){
        var f=new Fixture(h,TissueCore.BLADE);var locked=new AtomicBoolean(true);var ticks=new AtomicInteger();
        var original=new Goal(){
            {setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
            @Override public boolean canUse(){return locked.get();}
            @Override public boolean canContinueToUse(){return locked.get();}
            @Override public boolean requiresUpdateEveryTick(){return true;}
            @Override public void tick(){ticks.incrementAndGet();f.actor.getNavigation().stop();}
        };
        f.actor.goalSelector.addGoal(0,original);
        h.runAfterDelay(25,()->{
            h.assertTrue(ticks.get()>5&&TissueActions.core(f.actor)==null&&f.prey.getHealth()==10000,"source high-priority control blocks organ action and contact");locked.set(false);
        });
        h.runAfterDelay(100,()->{
            try{
                h.assertTrue(f.prey.getHealth()<10000&&f.actor.goalSelector.getAvailableGoals().stream().anyMatch(w->w.getGoal()==original),"after source releases control, shared organ works without replacing the source goal");h.succeed();
            }finally{f.close();}
        });
    }
    @GameTest(template="wall_test",timeoutTicks=200,batch="native_organ_reloaded")
    public static void reloadingNativeGrasperReleasesBothTransientActionAndRealHold(GameTestHelper h){
        var f=new Fixture(h,TissueCore.HYDROSTAT);
        h.startSequence().thenWaitUntil(()->h.assertTrue(TissueGrapples.owner(f.prey)==f.actor,"native action establishes a real live hold"))
            .thenExecute(()->{
                try{
                    var saved=new CompoundTag();f.actor.saveWithoutId(saved);f.actor.load(saved);
                    h.assertTrue(TissueActions.core(f.actor)==null&&TissueGrapples.owner(f.prey)==null,"in-place native reload drops the transient action and tether");h.succeed();
                }finally{f.close();}
            });
    }
    @GameTest(template="wall_test",timeoutTicks=200,batch="native_organ_cure")
    public static void realHeavyHitAndCureCannotLeaveAnOwnedOrganAttack(GameTestHelper h){
        var f=new Fixture(h,TissueCore.BLADE);
        h.startSequence().thenWaitUntil(()->h.assertTrue(TissueActions.core(f.actor)==TissueCore.BLADE,"native action starts"))
            .thenExecute(()->{
                try{
                    float before=f.actor.getHealth();f.actor.hurt(f.actor.damageSources().generic(),f.actor.getMaxHealth()*.4F);
                    h.assertTrue(f.actor.getHealth()<before&&TissueActions.core(f.actor)==null,"actual heavy health receipt interrupts native action");
                    f.actor.removeEffect(ModEffects.BLACKLIGHT_INFECTION);NativeInfection.clear(f.actor);
                    h.assertTrue(f.actor.goalSelector.getAvailableGoals().stream().noneMatch(w->w.getGoal() instanceof NativeTissueAbilityGoal)&&NativeTissue.snapshot(f.actor).isEmpty(),"cure removes only owned ability goal/transient presentation");h.succeed();
                }finally{f.close();}
            });
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="native_organ_damage_origin")
    public static void onlyBlacklightIssuedHitsCarryOrganProvenance(GameTestHelper h){
        var f=new Fixture(h,TissueCore.MUSCLE);
        try{
            var raw=f.actor.damageSources().mobAttack(f.actor);var own=TissueAttackSource.mobAttack(f.actor);
            h.assertTrue(!com.codex.blacklight.combat.CrossModCombat.nativeAttack(raw)&&com.codex.blacklight.combat.CrossModCombat.nativeAttack(own),"original native attacks keep their origin; explicitly issued organ hits belong to Blacklight");
            h.assertTrue(own.typeHolder()==raw.typeHolder()&&own.getEntity()==f.actor&&own.getDirectEntity()==f.actor&&own.getSourcePosition().equals(raw.getSourcePosition()),"the actual damage ID, owner, direct actor and position stay unchanged");
            h.assertTrue(own.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)==raw.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)&&own.is(net.minecraft.tags.DamageTypeTags.BYPASSES_EFFECTS)==raw.is(net.minecraft.tags.DamageTypeTags.BYPASSES_EFFECTS),"provenance itself does not add true damage or bypass any shield/event");
            h.assertTrue(com.codex.blacklight.combat.CrossModCombat.nativeAttack(com.codex.blacklight.combat.CrossModCombat.offensiveSource(own)),"an optional existing offensive wrapper preserves the explicit origin");h.succeed();
        }finally{f.close();}
    }

    @GameTest(template="wall_test",timeoutTicks=200,batch="native_organ_outer_false")
    public static void anOuterFalseReturnWithRealHealthLossStillReceivesItsStingingEffect(GameTestHelper h){
        var f=new Fixture(h,TissueCore.CNIDOCYTE);var saw=new AtomicBoolean();
        var target=new net.minecraft.world.entity.animal.Pig(EntityType.PIG,h.getLevel()){
            @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){
                super.hurt(source,amount);if(source instanceof TissueAttackSource)saw.set(true);return false;
            }
        };
        target.setPos(f.prey.position());f.prey.discard();target.setNoAi(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);h.getLevel().addFreshEntity(target);f.actor.setTarget(target);
        h.startSequence().thenWaitUntil(()->h.assertTrue(target.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"completed false-return override with real damage triggers the owned sting effect"))
            .thenExecute(()->{try{h.assertTrue(saw.get()&&target.getHealth()<10000,"real foreign-style override returned false and still lost health");h.succeed();}finally{target.discard();f.close();}});
    }
    @GameTest(template="wall_test",timeoutTicks=200,batch="native_organ_veto")
    public static void aRealVetoCannotGainAStingOrFakeContactDamage(GameTestHelper h){
        var f=new Fixture(h,TissueCore.CNIDOCYTE);var saw=new AtomicBoolean();
        var target=new net.minecraft.world.entity.animal.Pig(EntityType.PIG,h.getLevel()){
            @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){
                if(source instanceof TissueAttackSource){saw.set(true);return false;}return super.hurt(source,amount);
            }
        };
        target.setPos(f.prey.position());f.prey.discard();target.setNoAi(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);h.getLevel().addFreshEntity(target);f.actor.setTarget(target);
        h.runAfterDelay(65,()->{try{
            h.assertTrue(saw.get()&&target.getHealth()==10000&&!target.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"real veto remains honored with no invented health damage or secondary sting");h.succeed();
        }finally{target.discard();f.close();}});
    }
    @GameTest(template="wall_test",timeoutTicks=200,batch="native_organ_equal_priority")
    public static void stationaryOrganAndEqualPriorityNativeMeleeCooperateWithoutStoppingNavigation(GameTestHelper h){
        var f=new Fixture(h,TissueCore.BLADE);NativeTissueAbilityGoal.clear(f.actor);NativeTissue.spend(f.actor,2400);var ticks=new AtomicInteger();
        var destination=net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,2,6)));
        var original=new Goal(){
            {setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
            @Override public boolean canUse(){return true;}
            @Override public boolean canContinueToUse(){return true;}
            @Override public boolean requiresUpdateEveryTick(){return true;}
            @Override public void tick(){ticks.incrementAndGet();f.actor.getNavigation().moveTo(destination.x,destination.y,destination.z,.01);}
        };
        f.actor.goalSelector.addGoal(1,original);NativeTissueAbilityGoal.maintain(f.actor);
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.actor.onGround()&&ticks.get()>2&&!f.actor.getNavigation().isDone(),"source establishes a real path before the organ is funded; position="+f.actor.position()+" tick="+f.actor.tickCount))
            .thenExecute(()->NativeTissue.feed(f.actor,2400))
            .thenWaitUntil(()->h.assertTrue(TissueActions.core(f.actor)==TissueCore.BLADE&&NativeTissueAbilityGoal.active(f.actor)
                    &&ticks.get()>1&&f.actor.goalSelector.getAvailableGoals().stream().anyMatch(w->w.isRunning()&&w.getGoal()==original),"source melee and owned stationary organ are actually running together"))
            .thenExecute(()->{try{
                h.assertTrue(!f.actor.getNavigation().isDone(),"stationary supplement does not stop real source navigation; position="+f.actor.position()+" ground="+f.actor.onGround()+" ticks="+ticks.get()+" path="+f.actor.getNavigation().getPath());
            }catch(Throwable failure){f.close();throw failure;}})
            .thenIdle(22).thenExecute(()->{try{
                h.assertTrue(ticks.get()>20&&f.prey.getHealth()<10000&&!f.actor.getNavigation().isDone(),"original movement continues through a real organ hit");h.succeed();
            }finally{f.close();}});
    }
}
