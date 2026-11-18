package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.EncounterEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false) @Mod.EventBusSubscriber(modid="blacklight")
public final class GreeneCycleGameTest {
    private static final String NESTED_KILL_ARMED="BlacklightTestGreeneNestedKill",NESTED_KILL_RAN="BlacklightTestGreeneNestedKillRan";
    /** This QA-only source set is excluded from ordinary/release builds. The
     * marker belongs to exactly one fixture and is consumed before re-entry. */
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void nestedBypassFixture(LivingDamageEvent event){
        if(!(event.getEntity() instanceof EncounterMob mob)||!mob.getPersistentData().getBoolean(NESTED_KILL_ARMED))return;
        mob.getPersistentData().remove(NESTED_KILL_ARMED);mob.getPersistentData().putBoolean(NESTED_KILL_RAN,true);
        mob.hurt(mob.damageSources().genericKill(),Float.MAX_VALUE);
    }
    private static EncounterMob actor(GameTestHelper h){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<16;y++)h.setBlock(x,y,z,Blocks.AIR);}
        var mob=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(h.getLevel());
        // Explicit existing encounter fixture; fresh opening has its own regression.
        var standing=mob.greeneBattle().save();standing.putBoolean("OpeningPending",false);mob.greeneBattle().load(standing);
        mob.setPos(h.absoluteVec(new Vec3(8,2,8)));mob.setNoAi(true);mob.setPersistenceRequired();mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(.25);
        // Keep this health/pose arena local. Brood ownership/spawning has its
        // own fixtures; its source locators start outside this restriction.
        mob.restrictTo(mob.blockPosition(),10);h.getLevel().addFreshEntity(mob);return mob;
    }
    private static float units(EncounterMob mob){return com.codex.blacklight.power.CombatScale.units(mob);}
    private static float rawDamage(EncounterMob mob,float healthDamage){return healthDamage/units(mob);}
    private static void breakAll(GameTestHelper h,EncounterMob mob){for(int i=0;i<3;i++)h.assertTrue(mob.hurtPart(i,h.getLevel().damageSources().generic(),rawDamage(mob,mob.greeneSegmentMaximum())+1),"independent appendage "+i+" breaks");}
    private static void armour(GameTestHelper h,EncounterMob mob,int slot,float expected,String why){h.assertTrue(Math.abs(save(mob).getFloat("EncounterArmor"+slot)-expected)<.001,why);}
    private static CompoundTag save(EncounterMob mob){var tag=new CompoundTag();mob.saveWithoutId(tag);return tag;}
    private static void done(GameTestHelper h,Entity... entities){
        for(var owner:entities)if(owner instanceof EncounterMob mob&&mob.kind()==EncounterKind.ELIZABETH_GREENE)
            for(var child:h.getLevel().getEntities(mob,mob.getBoundingBox().inflate(80),e->e.getPersistentData().hasUUID("BlacklightGreeneOwner")&&e.getPersistentData().getUUID("BlacklightGreeneOwner").equals(mob.getUUID())))child.discard();
        for(var e:entities)e.discard();h.succeed();
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=40) public static void coreHitSplitsAcrossAttachedLimbsBeforeItsOwnImmunity(GameTestHelper h){
        var mob=actor(h);h.runAfterDelay(5,()->{
            float health=mob.getHealth();
            h.assertTrue(mob.hurt(h.getLevel().damageSources().generic(),100),"core hit reaches its health children");
            var saved=save(mob);
            for(int i=0;i<3;i++)armour(h,mob,i,mob.greeneSegmentMaximum()-20*units(mob),"each limb receives one third of the 60% raw share, converted once to health units");
            h.assertTrue(mob.getHealth()==health&&!mob.exposed(),"guarded core does not leak the old 8% chip damage");
            h.assertTrue(!mob.hurt(h.getLevel().damageSources().generic(),100),"rejected child hits do not manufacture own damage");
            checkReload(h,mob,false);done(h,mob);
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=40) public static void aCoolingLimbStillCountsInTheSameIncomingDamageDivisor(GameTestHelper h){
        var mob=actor(h);h.runAfterDelay(5,()->{
            h.assertTrue(mob.hurtPart(0,h.getLevel().damageSources().generic(),10),"first limb is cooling down");
            h.assertTrue(mob.hurt(h.getLevel().damageSources().generic(),100),"other two limbs can receive core redirect");
            var saved=save(mob);
            armour(h,mob,0,mob.greeneSegmentMaximum()-10*units(mob),"cooling limb rejects its share after the earlier converted direct hit");
            for(int i=1;i<3;i++)armour(h,mob,i,mob.greeneSegmentMaximum()-20*units(mob),"rejected raw share is not reassigned to available limbs");
            done(h,mob);
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=40) public static void detachedLimbLeavesTheMinecraftHealthChildSet(GameTestHelper h){
        var mob=actor(h);h.runAfterDelay(5,()->{
            h.assertTrue(mob.hurtPart(0,h.getLevel().damageSources().generic(),rawDamage(mob,mob.greeneSegmentMaximum())+1),"first limb detaches");
            h.assertTrue(mob.hurt(h.getLevel().damageSources().generic(),100),"remaining limbs receive redirect");
            var saved=save(mob);
            h.assertTrue(mob.broken(0)&&saved.getFloat("EncounterArmor0")==0,"detached proxy stays detached");
            for(int i=1;i<3;i++)armour(h,mob,i,mob.greeneSegmentMaximum()-30*units(mob),"two remaining proxies each receive raw30 converted to health units once");
            done(h,mob);
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=160) public static void evenHugeCoreHitsMustFinishCollapseBeforeOwnDamage(GameTestHelper h){
        var mob=actor(h);float[] before={0};
        h.runAfterDelay(5,()->{
            before[0]=mob.getHealth();
            h.assertTrue(mob.hurt(h.getLevel().damageSources().generic(),10000),"large hit breaks attached limbs");
            h.assertTrue(mob.greeneStage()==GreeneMotion.COLLAPSING&&mob.getHealth()==before[0],"breaking final limb does not unlock own damage in that same event");
            h.assertTrue(!mob.hurt(h.getLevel().damageSources().generic(),10000),"collapse still protects the core even above the former five-times-max shortcut");
        });
        h.runAfterDelay(130,()->{
            h.assertTrue(mob.greeneStage()==GreeneMotion.DOWN,"125-tick collapse is complete");
            float afterStun=mob.getHealth();mob.invulnerableTime=0;
            h.assertTrue(mob.hurt(h.getLevel().damageSources().generic(),rawDamage(mob,60)),"down core takes own damage");
            h.assertTrue(Math.abs(afterStun-mob.getHealth()-60)<.0001,"alwaysDoOwn uses all 60 after the separate scripted stun, not 40% or 8%");
            done(h,mob);
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=40) public static void redirectedHealthIsOnePhysicalContactRatherThanThreeFakeImpacts(GameTestHelper h){
        var mob=actor(h);int[] effects={0};
        h.runAfterDelay(5,()->{
            var region=mob.hurtRegions(0).stream().filter(r->!r.bone().startsWith("weak_")&&!r.boxes().isEmpty()).findFirst().orElseThrow();
            var contact=new HydraHurtContact.Contact(region.bone(),region.boxes().get(0).center,new Vec3(0,1,0),com.codex.blacklight.infection.InfectedAttackStyle.TEAR);
            int before=mob.incomingEffects();
            h.assertTrue(mob.hurtAt(h.getLevel().damageSources().generic(),100,contact),"real core surface receives redirected harm");
            h.assertTrue(mob.incomingEffects()==before+1,"one incoming surface produces one impact event");effects[0]=mob.incomingEffects();
            h.assertTrue(!mob.hurtAt(h.getLevel().damageSources().generic(),100,contact),"duplicate core hit is rejected by child cooldowns");
            h.assertTrue(mob.incomingEffects()==effects[0],"rejected redirect cannot emit another impact");
        });
        h.runAfterDelay(16,()->{
            h.assertTrue(mob.hurtPart(0,h.getLevel().damageSources().generic(),10),"direct weakpoint hit still works after its cooldown");
            h.assertTrue(mob.incomingEffects()==effects[0]+1,"direct weakpoint keeps its own physical feedback");done(h,mob);
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=40) public static void appendagesHaveIndependentHealthAndHitClocks(GameTestHelper h){
        var mob=actor(h);h.runAfterDelay(5,()->{float hp=mob.getHealth();for(int i=0;i<3;i++)h.assertTrue(mob.hurtPart(i,h.getLevel().damageSources().generic(),20),"same-tick distinct limb accepted");
            h.assertTrue(!mob.hurtPart(0,h.getLevel().damageSources().generic(),20),"same limb duplicate rejected");var tag=save(mob);
            for(int i=0;i<3;i++)armour(h,mob,i,mob.greeneSegmentMaximum()-20*units(mob),"only local armor reduced by one raw-to-health conversion");h.assertTrue(mob.getHealth()==hp&&!mob.exposed(),"body health separate from appendages");done(h,mob);});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=40) public static void brokenAppendageStopsReceivingWithoutRemovingOtherBodyRegions(GameTestHelper h){
        var mob=actor(h);h.runAfterDelay(5,()->{h.assertTrue(mob.hurtPart(1,h.getLevel().damageSources().generic(),rawDamage(mob,mob.greeneSegmentMaximum())+1),"one appendage breaks");mob.updateParts();
            h.assertTrue(!mob.getParts()[1].isPickable()&&mob.getParts()[0].isPickable()&&mob.getParts()[2].isPickable(),"separate pick proxies");
            h.assertTrue(mob.hurtRegions(0).get(1).boxes().isEmpty()&&!mob.hurtRegions(0).get(0).boxes().isEmpty(),"broken visible surface no longer blocks shots");
            h.assertTrue(mob.hurtRegions(0).stream().skip(3).anyMatch(r->!r.boxes().isEmpty()),"core remains reachable");done(h,mob);});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=60) public static void joinedHealthScaleAndLaterMaximumChangesPreserveArmourAndFloorFractions(GameTestHelper h){
        var mob=actor(h);
        h.runAfterDelay(5,()->{
            float maximum=mob.getMaxHealth(),segment=GreeneBattleHealth.sourceToMinecraft(500,maximum);
            h.assertTrue(Math.abs(mob.greeneSegmentMaximum()-segment)<.001,"joined entity uses actual scaled maxHealth for the source500/6000 tier");
            for(int i=0;i<3;i++)armour(h,mob,i,segment,"join cannot leave the constructor's pre-CombatScale armour amount");
            h.assertTrue(mob.hurtPart(0,h.getLevel().damageSources().generic(),rawDamage(mob,segment*.25F)),"actual direct hit removes one quarter of a source-scaled limb");
            armour(h,mob,0,segment*.75F,"part incoming damage converts to health units exactly once");
            var maximumAttribute=mob.getAttribute(Attributes.MAX_HEALTH);maximumAttribute.setBaseValue(maximumAttribute.getBaseValue()*2);
            h.runAfterDelay(2,()->{
                h.assertTrue(mob.getMaxHealth()>maximum,"fixture increases actual maxHealth after world join");
                armour(h,mob,0,mob.greeneSegmentMaximum()*.75F,"maxHealth change retains the damaged limb's fraction");
                for(int i=1;i<3;i++)armour(h,mob,i,mob.greeneSegmentMaximum(),"intact limbs scale with the same new basis");
                h.assertTrue(Math.abs(mob.greeneMinimumHealth()/mob.getMaxHealth()-1)<.00001,"guard floor follows the maximum without becoming a constructor-sized cap");
                h.runAfterDelay(10,()->{
                    breakAll(h,mob);float before=mob.getMaxHealth(),floor=mob.greeneMinimumHealth();
                    h.assertTrue(Math.abs(floor-GreeneBattleHealth.collapseFloor(1,before))<.001,"first collapse owns the source65.5% floor");
                    maximumAttribute.setBaseValue(maximumAttribute.getBaseValue()*1.5);
                    h.runAfterDelay(2,()->{
                        h.assertTrue(Math.abs(mob.greeneMinimumHealth()/mob.getMaxHealth()-floor/before)<.00001,"changing maximum during collapse preserves its existing floor fraction");
                        for(int i=0;i<3;i++)armour(h,mob,i,0,"rebasing health never regenerates a broken limb");done(h,mob);
                    });
                });
            });
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=220) public static void crossBasisReloadPreservesDamageFloorsAndStunSnapshotWithoutDoubleScaling(GameTestHelper h){
        var mob=actor(h);
        h.runAfterDelay(5,()->{
            float segment=mob.greeneSegmentMaximum();
            h.assertTrue(mob.hurtPart(0,h.getLevel().damageSources().generic(),rawDamage(mob,segment*.25F)),"fixture has a genuinely three-quarter-intact appendage");
            h.assertTrue(mob.hurtPart(1,h.getLevel().damageSources().generic(),rawDamage(mob,segment)+1),"fixture has a genuinely detached appendage");
            mob.setHealth(mob.getMaxHealth()*.35F);
            // A smaller destination maximum exposes clamping in the wrong
            // units: the stored .75 limb exceeds the new full .5-size limb.
            reloadAtDifferentHealthBasis(h,mob,.5);
            h.runAfterDelay(11,()->{
                for(int slot:new int[]{0,2})h.assertTrue(mob.hurtPart(slot,h.getLevel().damageSources().generic(),rawDamage(mob,mob.greeneSegmentMaximum())+1),"remaining limb breaks after its real cooldown");
                awaitStage(h,mob,GreeneBattleCycle.DOWN,()->{
                    var down=mob.greeneBattle().save();
                    h.assertTrue(down.getFloat("InitialStunnedHealth")>0&&!down.getBoolean("StunPending"),"actual DOWN supplies a nonzero pre-stun snapshot after the once-owned debit");
                    h.assertTrue(Math.abs(down.getFloat("MinimumHealth")-GreeneBattleHealth.collapseFloor(1,mob.getMaxHealth()))<.001,"actual first collapse supplies the source floor");
                    // Raising the saved floor above current HP during load
                    // must not itself perform a damage write or heal the core.
                    reloadAtDifferentHealthBasis(h,mob,2);
                    float health=mob.getHealth();var rebased=mob.greeneBattle().save();
                    h.runAfterDelay(2,()->{
                        var resumed=mob.greeneBattle().save();
                        h.assertTrue(mob.getHealth()==health&&mob.greeneBattle().stage()==GreeneBattleCycle.DOWN,"resumed DOWN neither heals to its rebased floor nor repeats scripted stun");
                        for(String key:new String[]{"HealthBasis","MinimumHealth","InitialStunnedHealth","WaveCount","ArmourWave","StunPending"})
                            h.assertTrue(rebased.get(key).equals(resumed.get(key)),"ordinary ticks cannot repeat cross-basis work for "+key);
                        done(h,mob);
                    });
                });
            });
        });
    }
    private static void reloadAtDifferentHealthBasis(GameTestHelper h,EncounterMob mob,double maximumFactor){
        var incoming=save(mob);var before=incoming.getCompound("GreeneBattle");
        float oldBasis=before.getFloat("HealthBasis"),health=incoming.getFloat("Health");
        var maximum=mob.getAttribute(Attributes.MAX_HEALTH);maximum.setBaseValue(maximum.getBaseValue()*maximumFactor);
        float destinationMaximum=mob.getMaxHealth();double ratio=(double)destinationMaximum/oldBasis;
        h.assertTrue(oldBasis>0&&destinationMaximum!=oldBasis&&health<=destinationMaximum,"fixture really loads across distinct health bases without vanilla max-health clipping");
        // Keep the cycle and limb snapshot in its original units, but load
        // the recipient's actual changed vanilla attributes through Entity.load.
        String attributes=incoming.contains("attributes",9)?"attributes":"Attributes";
        h.assertTrue(incoming.contains(attributes,9),"fixture retains this platform's actual vanilla attribute list");
        incoming.put(attributes,mob.getAttributes().save());
        for(int pass=0;pass<3;pass++){
            mob.load(incoming.copy());var saved=save(mob);var cycle=saved.getCompound("GreeneBattle");
            h.assertTrue(mob.getMaxHealth()==destinationMaximum&&cycle.getFloat("HealthBasis")==destinationMaximum,"load publishes the new basis exactly once on pass "+pass);
            h.assertTrue(mob.getHealth()==health,"cross-basis load must not heal or replay stun on pass "+pass);
            h.assertTrue(saved.getInt("EncounterBroken")==incoming.getInt("EncounterBroken"),"load preserves detached-limb identity on pass "+pass);
            for(int slot=0;slot<3;slot++)h.assertTrue(Math.abs(saved.getFloat("EncounterArmor"+slot)-incoming.getFloat("EncounterArmor"+slot)*ratio)<.001,"old-basis armor is read before rebasing, without refill or cumulative scaling: slot="+slot+" pass="+pass);
            for(String key:new String[]{"MinimumHealth","InitialStunnedHealth"})h.assertTrue(Math.abs(cycle.getFloat(key)-before.getFloat(key)*ratio)<.001,"preserve the saved proportion of "+key+" on pass "+pass);
        }
        var normalized=save(mob);mob.load(normalized.copy());mob.greeneBattle().refreshHealthScale();mob.greeneBattle().refreshHealthScale();
        var stable=save(mob);
        h.assertTrue(mob.getHealth()==health,"loading the already-rebased snapshot cannot heal");
        for(int slot=0;slot<3;slot++)h.assertTrue(stable.getFloat("EncounterArmor"+slot)==normalized.getFloat("EncounterArmor"+slot),"already-rebased armor is idempotent for slot "+slot);
        for(String key:new String[]{"HealthBasis","MinimumHealth","InitialStunnedHealth"})h.assertTrue(stable.getCompound("GreeneBattle").get(key).equals(normalized.getCompound("GreeneBattle").get(key)),"already-rebased cycle is idempotent for "+key);
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=50) public static void bodyHealthThresholdsDoNotReplaceTheAppendageCycle(GameTestHelper h){
        var mob=actor(h);mob.setNoAi(false);mob.setHealth(200);h.runAfterDelay(25,()->{h.assertTrue(mob.phase()==0&&!mob.exposed(),"low body health alone does not manufacture a cycle");done(h,mob);});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=700) public static void animatedReceivingBoxesDoNotMoveTheWorldOrigin(GameTestHelper h){
        var mob=actor(h);mob.setNoAi(false);Vec3 root=mob.position();
        h.runAfterDelay(30,()->{h.assertTrue(mob.position().distanceTo(root)<.01,"idle physics stays rooted");breakAll(h,mob);
            awaitStage(h,mob,GreeneBattleCycle.DOWN,()->{mob.invulnerableTime=0;h.assertTrue(mob.hurt(h.getLevel().damageSources().generic(),rawDamage(mob,10)),"down body hit remains accepted");mob.knockback(2,1,1);
                h.runAfterDelay(3,()->h.assertTrue(mob.position().distanceTo(root)<.01,"posed contact and knockback cannot move the exposure root"));});
            awaitStage(h,mob,GreeneBattleCycle.RISING,()->awaitAge(h,mob,GreeneBattleCycle.RISING,48,()->{
                h.assertTrue(mob.position().distanceTo(root)<.01,"fall, down and rise are local skeletal motion; later source submerge owns real vertical movement");done(h,mob);
            }));
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=1300) public static void collapseTimerStartsAtCollapseAndRestoresOnlyAfterTheChosenRecovery(GameTestHelper h){
        var mob=actor(h);long[] started={0};
        h.runAfterDelay(5,()->{
            breakAll(h,mob);started[0]=h.getLevel().getGameTime();
            h.assertTrue(mob.greeneStage()==GreeneMotion.COLLAPSING,"starts collapse");
            int recovery=GreeneMotion.COLLAPSE_TICKS+mob.greeneHoldTicks();
            if(com.codex.blacklight.config.GameplayBalanceConfig.value(com.codex.blacklight.config.GameplayBalanceConfig.GREENE_SOURCE_TIMER))
                h.assertTrue(recovery>=514&&recovery<=554,"native timeout includes collapse and signed one-second variation");
            h.runAfterDelay(recovery-1,()->h.assertTrue(mob.greeneStage()==GreeneMotion.DOWN,"no early timer transition"));
            h.runAfterDelay(recovery+1,()->h.assertTrue(mob.greeneStage()==GreeneMotion.RISING&&mob.broken(0)&&!mob.getParts()[0].isPickable(),"rise precedes shield restoration"));
            h.runAfterDelay(recovery+GreeneMotion.RISE_TICKS+1,()->{
                h.assertTrue(mob.greeneBattle().stage()==GreeneBattleCycle.SUBMERGING&&mob.broken(0)&&mob.broken(1)&&mob.broken(2),"50-tick rise enters submerge with the old limbs still broken");
            });
            h.runAfterDelay(recovery+GreeneMotion.RISE_TICKS+399,()->h.assertTrue(mob.greeneBattle().stage()==GreeneBattleCycle.UNDERGROUND&&mob.broken(0),"400-tick source wait includes the sinking clip and cannot regenerate early"));
            h.runAfterDelay(recovery+GreeneMotion.RISE_TICKS+401,()->{
                h.assertTrue(mob.greeneBattle().stage()==GreeneBattleCycle.EMERGE_WAIT&&!mob.broken(0)&&!mob.broken(1)&&!mob.broken(2),"next emergence, not recovery animation end, restores the limbs");
                h.assertTrue(mob.greeneBattle().waveCount()==2&&mob.greeneBattle().armourWave()==2,"one DOWN advances exactly one future armour wave");
                for(int i=0;i<3;i++)armour(h,mob,i,GreeneBattleHealth.sourceToMinecraft(700,mob.getMaxHealth()),"second source armour tier is 700/6000 of core max");
                awaitStage(h,mob,GreeneBattleCycle.GUARDED,()->done(h,mob));
            });
        });
        h.runAfterDelay(150,()->{
            h.assertTrue(mob.greeneStage()==GreeneMotion.DOWN&&mob.exposed(),"entered down loop");
            int chosen=mob.greeneHoldTicks();float hp=mob.getHealth();
            h.assertTrue(mob.hurt(h.getLevel().damageSources().generic(),rawDamage(mob,10))&&Math.abs(hp-mob.getHealth()-10)<.001,"exposed body accepts the full requested ten health units");
            h.assertTrue(mob.greeneHoldTicks()==chosen&&Math.abs(mob.exposedAge(0)-(h.getLevel().getGameTime()-started[0]))<.01,"ordinary hit neither resamples nor restarts timer");
        });
        h.runAfterDelay(310,()->h.assertTrue(mob.greeneStage()==GreeneMotion.DOWN&&mob.broken(0)&&mob.broken(1)&&mob.broken(2),"not restored at the old one-shot clip end"));
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=1650) public static void saveLoadPreservesPartialArmorAndEveryRecoveryStage(GameTestHelper h){
        var mob=actor(h);h.runAfterDelay(3,()->{h.assertTrue(mob.hurtPart(1,h.getLevel().damageSources().generic(),30),"partial damage");checkReload(h,mob,false);});
        h.runAfterDelay(20,()->{breakAll(h,mob);reloadStage(h,mob,0);});
    }
    private static void checkReload(GameTestHelper h,EncounterMob mob,boolean active){
        var tag=save(mob);var copy=EncounterEntities.CREATURES.get(mob.kind()).get().create(h.getLevel());copy.load(tag);var saved=save(copy);
        h.assertTrue(copy.exposed()==active&&copy.greeneStage()==mob.greeneStage(),"same recovery stage after load");h.assertTrue(copy.greeneHoldTicks()==mob.greeneHoldTicks(),"reload cannot draw a new collapse timer");if(active)h.assertTrue(Math.abs(copy.exposedAge(0)-mob.exposedAge(0))<.01,"same local animation progress");
        for(int i=0;i<3;i++)h.assertTrue(saved.getFloat("EncounterArmor"+i)==tag.getFloat("EncounterArmor"+i)&&copy.broken(i)==mob.broken(i),"load cannot refill a limb");copy.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
    }
    private static void awaitStage(GameTestHelper h,EncounterMob mob,int stage,Runnable action){
        h.assertTrue(mob.isAlive()&&!mob.isRemoved(),"cycle actor remains loaded and alive while waiting for stage "+stage);
        if(mob.greeneBattle().stage()==stage)action.run();else h.runAfterDelay(1,()->awaitStage(h,mob,stage,action));
    }
    private static void awaitAge(GameTestHelper h,EncounterMob mob,int stage,int age,Runnable action){
        h.assertTrue(mob.greeneBattle().stage()==stage,"stage "+stage+" must not end before local age "+age);
        if(mob.greeneBattle().age(0)>=age)action.run();else h.runAfterDelay(1,()->awaitAge(h,mob,stage,age,action));
    }
    private static final int[] RELOAD_STAGES={GreeneBattleCycle.COLLAPSING,GreeneBattleCycle.DOWN,GreeneBattleCycle.RISING,GreeneBattleCycle.SUBMERGING,GreeneBattleCycle.UNDERGROUND,GreeneBattleCycle.EMERGE_WAIT,GreeneBattleCycle.EMERGING,GreeneBattleCycle.GUARDED};
    private static void reloadStage(GameTestHelper h,EncounterMob mob,int index){
        awaitStage(h,mob,RELOAD_STAGES[index],()->{
            var tag=save(mob);var cycle=mob.greeneBattle().save();float health=mob.getHealth();Vec3 position=mob.position();
            // Actual unload/re-add retains identity and owned brood. Discard would
            // be task termination and is not a faithful chunk-unload fixture.
            mob.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.runAfterDelay(24,()->{
                var copy=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(h.getLevel());copy.load(tag);copy.restrictTo(h.absolutePos(new BlockPos(8,2,8)),10);
                var restored=copy.greeneBattle().save();
                for(String key:new String[]{"Stage","Age","ExposureAge","RecoveryRemaining","UndergroundRemaining","WaveCount","ArmourWave","HealthBasis","MinimumHealth","InitialStunnedHealth","StunPending","RecoveryPending","FloorPending","FloorDelay","CoreDamageable","RootControlled"})
                    h.assertTrue(cycle.get(key)!=null&&cycle.get(key).equals(restored.get(key)),"offline time cannot advance or replay "+key+" in stage "+RELOAD_STAGES[index]);
                h.assertTrue(copy.position().distanceTo(position)<.0001&&copy.getHealth()==health,"load preserves actual depth and never reapplies 8.5% stun");
                for(int i=0;i<3;i++)h.assertTrue(save(copy).getFloat("EncounterArmor"+i)==tag.getFloat("EncounterArmor"+i),"load preserves partial or broken source armour");
                h.assertTrue(h.getLevel().addFreshEntity(copy),"saved mother returns with the same UUID after real removal");
                h.runAfterDelay(2,()->{
                    h.assertTrue(copy.getHealth()==health&&copy.greeneBattle().waveCount()==cycle.getInt("WaveCount"),"resumed ticking neither duplicates stun nor increments the wave again");
                    if(index+1==RELOAD_STAGES.length)done(h,copy);else reloadStage(h,copy,index+1);
                });
            });
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=50) public static void loadDoesNotCatchUpAnUnobservedRecovery(GameTestHelper h){
        var mob=actor(h);CompoundTag[] saved={null},cycle={null};h.runAfterDelay(3,()->breakAll(h,mob));h.runAfterDelay(10,()->{saved[0]=save(mob);cycle[0]=mob.greeneBattle().save();});
        h.runAfterDelay(30,()->{var copy=EncounterEntities.CREATURES.get(mob.kind()).get().create(h.getLevel());copy.load(saved[0]);h.assertTrue(copy.exposedAge(0)==cycle[0].getInt("ExposureAge")&&copy.greeneBattle().age(0)==cycle[0].getInt("Age"),"elapsed unloaded time cannot jump directly to restored limbs");done(h,mob,copy);});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=300) public static void sameObjectLoadReleasesOldPhysicsAndHonorsIncomingNoGravity(GameTestHelper h){
        var mob=actor(h);
        h.runAfterDelay(5,()->{
            var guarded=save(mob);guarded.putBoolean("NoGravity",true);breakAll(h,mob);
            awaitStage(h,mob,GreeneBattleCycle.DOWN,()->{
                mob.invulnerableTime=0;mob.hurt(h.getLevel().damageSources().generic(),rawDamage(mob,mob.getMaxHealth()));
                awaitStage(h,mob,GreeneBattleCycle.SUBMERGING,()->awaitAge(h,mob,GreeneBattleCycle.SUBMERGING,25,()->{
                    h.assertTrue(mob.noPhysics&&mob.isNoGravity(),"the live object actually owns sinking physics before replacement");
                    mob.load(guarded);
                    h.assertTrue(mob.greeneBattle().stage()==GreeneBattleCycle.GUARDED&&!mob.noPhysics,"loading a root-uncontrolled snapshot releases the previous noPhysics ownership");
                    h.assertTrue(mob.isNoGravity(),"Entity.load must apply the incoming explicit NoGravity=true after releasing old ownership");
                    var cycle=mob.greeneBattle().save();
                    h.assertTrue(!cycle.getBoolean("RootControlled")&&!cycle.getBoolean("StunPending")&&!cycle.getBoolean("RecoveryPending")&&!cycle.getBoolean("FloorPending")&&cycle.getInt("EmergeElapsed")==-1,"no previous transition work survives the guarded replacement");
                    h.runAfterDelay(3,()->{h.assertTrue(mob.greeneBattle().stage()==GreeneBattleCycle.GUARDED&&!mob.noPhysics&&mob.isNoGravity()&&mob.getHealth()==guarded.getFloat("Health"),"new guarded physics and health survive resumed ticking");done(h,mob);});
                }));
            });
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=340) public static void sameObjectLegacyReadClearsPendingRecoveryAndOldRootOwnership(GameTestHelper h){
        var mob=actor(h);
        h.runAfterDelay(5,()->{
            var legacy=save(mob);legacy.remove("GreeneBattle");legacy.putInt("EncounterPhase",0);legacy.putFloat("Health",mob.getMaxHealth()*.8F);
            legacy.putBoolean("GreeneCycleActive",true);legacy.putInt("GreeneCycleAge",150);legacy.putInt("EncounterBroken",7);
            for(int i=0;i<3;i++)legacy.putFloat("EncounterArmor"+i,0);
            breakAll(h,mob);awaitStage(h,mob,GreeneBattleCycle.DOWN,()->{
                mob.invulnerableTime=0;mob.hurt(h.getLevel().damageSources().generic(),rawDamage(mob,mob.getMaxHealth()*.18F+.01F));
                h.assertTrue(mob.greeneBattle().save().getBoolean("RecoveryPending"),"real damage queues early recovery before the legacy read");
                mob.readAdditionalSaveData(legacy);
                h.assertTrue(mob.greeneBattle().stage()==GreeneBattleCycle.DOWN&&mob.greeneBattle().age(0)==25&&mob.greeneBattle().waveCount()==2,"legacy DOWN migrates its own local clock and next wave");
                h.assertTrue(!mob.greeneBattle().save().getBoolean("RecoveryPending")&&!mob.greeneBattle().save().getBoolean("StunPending"),"legacy migration cannot inherit queued recovery or add retroactive stun");
                mob.greeneBattle().tick();
                h.assertTrue(mob.greeneBattle().age(0)==25&&h.getLevel().getGameTime()-mob.greeneBattleData().getLong("Started")==25,"loading and observing the same world tick cannot advance server age ahead of its published client clock");
                h.runAfterDelay(2,()->{
                    h.assertTrue(mob.greeneBattle().stage()==GreeneBattleCycle.DOWN&&mob.getHealth()==legacy.getFloat("Health"),"resumed legacy DOWN neither jumps to rise nor repeats 8.5% damage");
                    mob.invulnerableTime=0;mob.hurt(h.getLevel().damageSources().generic(),rawDamage(mob,mob.getMaxHealth()*.27F));
                    awaitStage(h,mob,GreeneBattleCycle.SUBMERGING,()->awaitAge(h,mob,GreeneBattleCycle.SUBMERGING,25,()->{
                        h.assertTrue(mob.noPhysics&&mob.isNoGravity(),"second replacement begins from a real owned sink");
                        legacy.putBoolean("GreeneCycleActive",false);legacy.putInt("GreeneCycleAge",0);legacy.putInt("EncounterBroken",0);
                        legacy.putFloat("EncounterArmor0",45);legacy.putFloat("EncounterArmor1",90);legacy.putFloat("EncounterArmor2",90);
                        mob.readAdditionalSaveData(legacy);
                        var cycle=mob.greeneBattle().save();
                        h.assertTrue(mob.greeneBattle().stage()==GreeneBattleCycle.GUARDED&&mob.greeneBattle().age(0)==0&&!mob.noPhysics&&!mob.isNoGravity(),"inactive legacy data releases the old root and resets its stage");
                        h.assertTrue(mob.greeneBattle().waveCount()==1&&cycle.getInt("EmergeElapsed")==-1&&!cycle.getBoolean("CoreDamageable")&&!cycle.getBoolean("RootControlled"),"old wave, emergence callbacks and open-core ownership do not leak into the migrated guard");
                        armour(h,mob,0,mob.greeneSegmentMaximum()*.5F,"legacy45/90 preserves a half-damaged appendage at the new source ratio");
                        h.runAfterDelay(3,()->{h.assertTrue(mob.greeneBattle().stage()==GreeneBattleCycle.GUARDED&&mob.getHealth()==legacy.getFloat("Health"),"legacy guard remains stable after the replacement tick");done(h,mob);});
                    }));
                });
            });
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=220) public static void downStunIsOnceAndCountsTowardEarlyRecovery(GameTestHelper h){
        var mob=actor(h);float[] entry={0},afterStun={0};
        h.runAfterDelay(5,()->{
            entry[0]=mob.getHealth();breakAll(h,mob);
            awaitStage(h,mob,GreeneBattleCycle.DOWN,()->{
                afterStun[0]=mob.getHealth();var cycle=mob.greeneBattle().save();
                h.assertTrue(Math.abs(entry[0]-afterStun[0]-GreeneBattleHealth.stunDamage(entry[0],mob.getMaxHealth()))<.001,"DOWN debits source8.5% of actual max once, without applying CombatScale.units a second time");
                h.assertTrue(cycle.getFloat("InitialStunnedHealth")==entry[0]&&!cycle.getBoolean("StunPending"),"snapshot precedes the script damage and its ownership is consumed");
                h.assertTrue(mob.greeneBattle().waveCount()==2&&mob.greeneBattle().armourWave()==1,"DOWN increments the future wave without refilling current armour");
                h.runAfterDelay(22,()->{
                    h.assertTrue(mob.getHealth()==afterStun[0]&&mob.greeneBattle().stage()==GreeneBattleCycle.DOWN,"ordinary ticking cannot debit stun again");
                    h.assertTrue(mob.greeneBattle().save().getInt("RecoveryRemaining")>20,"early recovery is checked before the natural timer expires");
                    // Debit only the remainder: excluding the earlier stun from
                    // the 26% total would leave this entity DOWN until timeout.
                    float remainder=(float)(mob.getMaxHealth()*GreeneBattleHealth.RECOVERY_FRACTION)-(entry[0]-afterStun[0])+.01F;
                    mob.invulnerableTime=0;h.assertTrue(mob.hurt(h.getLevel().damageSources().generic(),rawDamage(mob,remainder)),"remaining exposure damage reaches the real core in health units");
                    h.runAfterDelay(2,()->{h.assertTrue(mob.greeneBattle().stage()==GreeneBattleCycle.RISING,"26% including stun requests recovery before the hold timer");done(h,mob);});
                });
            });
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=1100) public static void firstFloorSurvivesOverkillButTheSecondExposureCanDie(GameTestHelper h){
        var mob=actor(h);
        h.runAfterDelay(5,()->{breakAll(h,mob);awaitStage(h,mob,GreeneBattleCycle.DOWN,()->{
            mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION,200,0));mob.setAbsorptionAmount(4);
            h.assertTrue(mob.getAbsorptionAmount()>0,"fixture has real absorption before health deduction");
            mob.invulnerableTime=0;h.assertTrue(mob.hurt(h.getLevel().damageSources().generic(),rawDamage(mob,mob.getMaxHealth()*10)),"large ordinary hit enters the core damage pipeline");
            float floor=GreeneBattleHealth.collapseFloor(1,mob.getMaxHealth());
            h.assertTrue(mob.isAlive()&&Math.abs(mob.getHealth()-floor)<.001,"first source floor prevents death before fatal callbacks");
            h.assertTrue(mob.getAbsorptionAmount()==0,"absorption is spent before the same health floor on both platforms");
            awaitStage(h,mob,GreeneBattleCycle.GUARDED,()->{
                h.assertTrue(mob.greeneBattle().armourWave()==2,"actual next emergence restored source wave two");breakAll(h,mob);
                h.assertTrue(mob.greeneMinimumHealth()<0,"normal second collapse follows outer30, not the alternate outer16 positive floor");
                awaitStage(h,mob,GreeneBattleCycle.DOWN,()->{
                    mob.invulnerableTime=0;h.assertTrue(mob.hurt(h.getLevel().damageSources().generic(),rawDamage(mob,mob.getMaxHealth()*10)),"second exposed core accepts lethal ordinary damage");
                    h.assertTrue(!mob.isAlive()&&mob.getHealth()<=0,"negative source minimum does not impose another mandatory survival round");done(h,mob);
                });
            });
        });});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=180) public static void nestedBypassDeathCannotBeRaisedByTheOuterFloorWrite(GameTestHelper h){
        var mob=actor(h);h.runAfterDelay(5,()->{breakAll(h,mob);awaitStage(h,mob,GreeneBattleCycle.DOWN,()->{
            h.assertTrue(mob.greeneMinimumHealth()>0&&mob.isAlive(),"exercise a real positive first-round health floor");
            mob.invulnerableTime=0;mob.getPersistentData().putBoolean(NESTED_KILL_ARMED,true);
            mob.hurt(h.getLevel().damageSources().generic(),10);
            h.assertTrue(mob.getPersistentData().getBoolean(NESTED_KILL_RAN),"nested genericKill ran inside the actual core damage event");
            h.assertTrue(!mob.isAlive()&&mob.getHealth()==0,"outer ordinary actuallyHurt cannot raise a core killed by a nested bypass source");done(h,mob);
        });});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=850) public static void sourceSubmergeMovesTheRootAndEmergenceHandsOffToARealAttack(GameTestHelper h){
        var mob=actor(h);mob.goalSelector.removeAllGoals(g->true);mob.targetSelector.removeAllGoals(g->true);Vec3 surface=mob.position();
        double depth=GreeneBattleCycle.SOURCE_DEPTH*GreeneMotion.table().sourceUnitBlocks();
        h.runAfterDelay(5,()->{breakAll(h,mob);awaitStage(h,mob,GreeneBattleCycle.DOWN,()->{
            mob.invulnerableTime=0;h.assertTrue(mob.hurt(h.getLevel().damageSources().generic(),rawDamage(mob,mob.getMaxHealth())),"damage requests early recovery for the movement fixture");
            awaitStage(h,mob,GreeneBattleCycle.SUBMERGING,()->{
                awaitAge(h,mob,GreeneBattleCycle.SUBMERGING,16,()->{
                    h.assertTrue(Math.abs(mob.getY()-surface.y)<.001,"source sinking delay keeps the actual root at the surface");
                    h.assertTrue(!mob.greeneBattle().targetable()&&java.util.Arrays.stream(mob.getParts()).noneMatch(Entity::isPickable),"targeting closes at the source sink boundary");
                });
                awaitAge(h,mob,GreeneBattleCycle.SUBMERGING,25,()->h.assertTrue(mob.getY()<surface.y&&mob.position().subtract(surface).horizontalDistance()<.001,"sinking changes world Y without lateral drift"));
            });
            awaitStage(h,mob,GreeneBattleCycle.UNDERGROUND,()->{
                h.assertTrue(Math.abs(mob.getY()-(surface.y-depth))<.001&&mob.noPhysics&&mob.isNoGravity(),"real root reaches the source-scaled underground depth with owned physics");
                float health=mob.getHealth();h.assertTrue(!mob.hurt(h.getLevel().damageSources().generic(),100)&&mob.getHealth()==health,"hidden underground body rejects real incoming damage");
            });
            awaitStage(h,mob,GreeneBattleCycle.EMERGE_WAIT,()->{
                h.assertTrue(!mob.broken(0)&&!mob.greeneBattle().targetable()&&!mob.greeneBattle().canAttack(),"new armour alone cannot make the buried boss attackable or armed");
                mob.setNoAi(false); // Drain ordinary action cooldown before testing the source age80 gate.
                awaitStage(h,mob,GreeneBattleCycle.EMERGING,()->{
                    awaitAge(h,mob,GreeneBattleCycle.EMERGING,26,()->h.assertTrue(!mob.greeneBattle().targetable()&&java.util.Arrays.stream(mob.getParts()).noneMatch(Entity::isPickable),"no receiving proxy before the 27-tick source gate"));
                    awaitAge(h,mob,GreeneBattleCycle.EMERGING,28,()->h.assertTrue(Math.abs(mob.getY()-surface.y)<.001&&mob.greeneBattle().targetable()&&java.util.Arrays.stream(mob.getParts()).anyMatch(Entity::isPickable),"emergence restores root height and real targetable surfaces"));
                    awaitAge(h,mob,GreeneBattleCycle.EMERGING,79,()->{
                        var target=h.spawn(EntityType.PIG,new Vec3(8,2,14));target.setNoAi(true);target.setInvulnerable(true);
                        h.assertTrue(!mob.beginGreene(GreeneWeaponProgram.RIFLE,target),"visible target cannot bypass source attack handoff at age79");
                        awaitAge(h,mob,GreeneBattleCycle.EMERGING,80,()->{
                            h.assertTrue(mob.greeneBattle().canAttack()&&mob.beginGreene(GreeneWeaponProgram.RIFLE,target),"source attack gate starts a real committed rifle program");
                            h.assertTrue(mob.greeneBattle().stage()==GreeneBattleCycle.GUARDED&&mob.move()==EncounterMove.VOLLEY&&mob.greeneProgram()==GreeneWeaponProgram.RIFLE,"attack releases emergence root control and owns the action track");
                            h.assertTrue(mob.greeneBattle().handoffAge()>=80&&!mob.noPhysics&&!mob.isNoGravity(),"handoff retains the emergence pose age and restores prior physics");
                            h.runAfterDelay(2,()->{
                                h.assertTrue(Math.abs(mob.getY()-surface.y)<.001&&mob.move()==EncounterMove.VOLLEY,"post-handoff physics does not snap root height or cancel the attack");
                                float handoff=mob.greeneBattle().handoffAge(),elapsed=mob.greeneBattle().handoffElapsed(0);var snapshot=save(mob);
                                h.assertTrue(elapsed>=0&&elapsed<4,"save occurs during the actual four-tick emergence blend");mob.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                                h.runAfterDelay(24,()->{
                                    var copy=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(h.getLevel());copy.load(snapshot);copy.restrictTo(h.absolutePos(new BlockPos(8,2,8)),10);
                                    copy.goalSelector.removeAllGoals(g->true);copy.targetSelector.removeAllGoals(g->true);
                                    h.assertTrue(copy.greeneBattle().handoffAge()==handoff&&Math.abs(copy.greeneBattle().handoffElapsed(0)-elapsed)<.001,"offline time preserves the remaining blend instead of dropping or completing it");
                                    var synced=copy.greeneBattleData();
                                    h.assertTrue(synced.getFloat("HandoffAge")==handoff&&Math.abs(h.getLevel().getGameTime()-synced.getLong("HandoffAt")-elapsed)<.001,"republished client pose clock matches the restored server handoff");
                                    h.assertTrue(h.getLevel().addFreshEntity(copy),"handoff snapshot returns with the original UUID");
                                    h.runAfterDelay(1,()->{h.assertTrue(Math.abs(copy.greeneBattle().handoffElapsed(0)-(elapsed+1))<.001&&!copy.noPhysics&&Math.abs(copy.getY()-surface.y)<.001,"resumed blend advances one observed tick while root physics stays released");done(h,copy,target);});
                                });
                            });
                        });
                    });
                });
            });
        });});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=110) public static void breakingDefensesCancelsPendingProjectiles(GameTestHelper h){
        var mob=actor(h);mob.goalSelector.removeAllGoals(g->true);mob.targetSelector.removeAllGoals(g->true);mob.setNoAi(false);
        var target=h.spawn(EntityType.PIG,new Vec3(8,2,15));target.setNoAi(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);target.setHealth(100);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        int[] releasedCount={0};var released=new java.util.HashSet<java.util.UUID>();
        h.runAfterDelay(25,()->h.assertTrue(mob.beginGreene(GreeneWeaponProgram.RIFLE,target),"rifle windup begins"));
        h.runAfterDelay(43,()->{releasedCount[0]=mob.greeneShots();h.assertTrue(releasedCount[0]>0,"exercise cancellation after real socket emission");
            for(var shot:h.getLevel().getEntitiesOfClass(GreeneProjectile.class,mob.getBoundingBox().inflate(40),p->p.getOwner()==mob))released.add(shot.getUUID());
            breakAll(h,mob);h.assertTrue(mob.move()==EncounterMove.NONE,"collapse cancels the active shoot track");});
        h.runAfterDelay(90,()->{h.assertTrue(mob.greeneShots()==releasedCount[0],"collapse cannot emit any post-cancel shots");
            h.assertTrue(h.getLevel().getEntitiesOfClass(GreeneProjectile.class,mob.getBoundingBox().inflate(40),p->p.getOwner()==mob).stream().allMatch(p->released.contains(p.getUUID())),"only already released native projectiles may remain");done(h,mob,target);});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=50) public static void nativeRayHitsActualAppendageSurface(GameTestHelper h){
        var mob=actor(h);h.runAfterDelay(10,()->{mob.updateParts();var part=(GreenePart)mob.getParts()[0];var region=mob.hurtRegions(0).get(0);
            var box=region.boxes().stream().max(java.util.Comparator.comparingDouble(b->b.center.distanceToSqr(mob.position()))).orElseThrow();
            var from=box.center.add(box.center.subtract(mob.position()).normalize().scale(6));var to=box.center;
            var result=ProjectileUtil.getEntityHitResult(h.getLevel(),mob,from,to,new AABB(from,to).inflate(1),e->e==part,0);
            h.assertTrue(result!=null&&result.getEntity()==part&&part.clip(from,to).orElseThrow().distanceTo(result.getLocation())<.001,"native ray uses posed cuboids rather than fixed box corners");done(h,mob);});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=40) public static void nativeSpitIgnoresItsOwnersExtendedAppendages(GameTestHelper h){
        var mob=actor(h);EncounterProjectile[] shot={null};
        h.runAfterDelay(10,()->{var box=mob.hurtRegions(0).get(0).boxes().stream().max(java.util.Comparator.comparingDouble(b->b.center.y)).orElseThrow();
            var outward=box.center.subtract(mob.position()).multiply(1,0,1).normalize();var projectile=new EncounterProjectile(h.getLevel(),mob,false);projectile.setPos(box.center.add(outward.scale(4)));projectile.setNoGravity(true);projectile.setDeltaMovement(outward.scale(-2));h.getLevel().addFreshEntity(projectile);shot[0]=projectile;
        });
        h.runAfterDelay(15,()->{h.assertTrue(shot[0]!=null&&!shot[0].isRemoved(),"spit cannot pop against an owned appendage after leaving the root movement box");armour(h,mob,0,mob.greeneSegmentMaximum(),"owned spit leaves the source-scaled appendage intact");done(h,mob,shot[0]);});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=180) public static void radialWaveIsBlockedByCoverBelowTheBossEyeRay(GameTestHelper h){
        var mob=actor(h);mob.setPos(h.absoluteVec(new Vec3(8,2,4)));mob.goalSelector.removeAllGoals(g->true);mob.targetSelector.removeAllGoals(g->true);mob.setNoAi(false);
        var target=h.spawn(EntityType.PIG,new Vec3(8,2,12));target.setNoAi(true);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        var visible=h.spawn(EntityType.PIG,new Vec3(2,2,4));visible.setNoAi(true);visible.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        for(int x=4;x<=12;x++)for(int y=2;y<=3;y++)h.setBlock(x,y,8,Blocks.STONE);
        h.runAfterDelay(25,()->{h.assertTrue(mob.hasLineOfSight(target),"tall boss eye ray clears the two-block cover");
            h.assertTrue(SourceRadialWave.contact(SourceBossAction.get(mob.kind(),EncounterMove.HOWL),132,mob.position(),0,target.getBoundingBox())!=null,"covered target is within the converted final sphere, so distance alone cannot make the cover assertion pass");
            h.assertTrue(mob.begin(EncounterMove.HOWL,target),"wave windup begins");});
        h.runAfterDelay(165,()->{h.assertTrue(target.getHealth()==target.getMaxHealth(),"converted Hit origin is blocked by cover throughout all seven spheres");
            h.assertTrue(visible.getHealth()<visible.getMaxHealth(),"same MC living-target adapter still receives a converted sphere with no cover");done(h,mob,target,visible);});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=110) public static void radialWaveHitsRaisedTargetInsideItsFirstSphere(GameTestHelper h){
        var mob=actor(h);mob.goalSelector.removeAllGoals(g->true);mob.targetSelector.removeAllGoals(g->true);mob.setNoAi(false);
        double scale=GreeneMotion.table().sourceUnitBlocks();
        // Pigs exercise the existing MC living-target adapter, not a claim
        // about the native SimulatedObject|Prop category's complete membership.
        var target=h.spawn(EntityType.PIG,new Vec3(8,2+5.5*scale,11.25));target.setNoAi(true);target.setNoGravity(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40);target.setHealth(40);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        var oldUnscaled=h.spawn(EntityType.PIG,new Vec3(8,9,12));oldUnscaled.setNoAi(true);oldUnscaled.setNoGravity(true);oldUnscaled.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40);oldUnscaled.setHealth(40);oldUnscaled.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        h.runAfterDelay(25,()->{
            h.assertTrue(mob.distanceTo(target)>8*scale&&SourceRadialWave.contact(SourceBossAction.get(mob.kind(),EncounterMove.HOWL),67,mob.position(),0,target.getBoundingBox())!=null,"raised surface is reached from the scaled offset, although root distance exceeds the scaled radius");
            h.assertTrue(mob.begin(EncounterMove.HOWL,target),"wave windup begins");});
        h.runAfterDelay(91,()->h.assertTrue(target.getHealth()==40,"no hit before decoded first contact"));
        h.runAfterDelay(100,()->{h.assertTrue(target.getHealth()<40,"raised target within the converted first sphere receives damage");h.assertTrue(oldUnscaled.getHealth()==40,"old seven-block-high fixture is outside the first sphere after conversion");done(h,mob,target,oldUnscaled);});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=100) public static void nativeRifleReachesVisibleEnemyBeyondAppendages(GameTestHelper h){
        var mob=actor(h);mob.setPos(h.absoluteVec(new Vec3(8,2,5)));mob.goalSelector.removeAllGoals(g->true);mob.targetSelector.removeAllGoals(g->true);mob.setNoAi(false);
        var target=h.spawn(EntityType.PIG,new Vec3(8,2,14));target.setNoAi(true);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        h.runAfterDelay(25,()->h.assertTrue(mob.beginGreene(GreeneWeaponProgram.RIFLE,target),"source rifle program begins against an on-foot enemy"));
        h.runAfterDelay(38,()->h.assertTrue(target.getHealth()==target.getMaxHealth(),"no pre-release damage"));
        h.runAfterDelay(85,()->{h.assertTrue(mob.greeneProgram()==GreeneWeaponProgram.RIFLE&&mob.greeneShots()>0,"dedicated rifle socket emitted source missiles");
            h.assertTrue(target.getHealth()<target.getMaxHealth(),"native rifle reaches the enemy beyond the owner's appendages");done(h,mob,target);});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=45) public static void antiVehicleProgramsRejectAnOnFootTarget(GameTestHelper h){
        var mob=actor(h);mob.goalSelector.removeAllGoals(g->true);mob.targetSelector.removeAllGoals(g->true);mob.setNoAi(false);
        var target=h.spawn(EntityType.PIG,new Vec3(8,2,15));target.setNoAi(true);
        h.runAfterDelay(25,()->{h.assertTrue(!mob.begin(EncounterMove.ORBS,target),"legacy ORBS cannot manufacture a vehicle target from a walking enemy");
            h.assertTrue(!mob.beginGreene(GreeneWeaponProgram.ANTI_TANK,target)&&!mob.beginGreene(GreeneWeaponProgram.ANTI_AIR,target),"both native anti-vehicle programs require their corresponding hostile occupied hull");
            h.assertTrue(mob.beginGreene(GreeneWeaponProgram.RIFLE,target),"the rejected target is otherwise a valid on-foot enemy");done(h,mob,target);});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=35) public static void sourceMissileIgnoresItsOwnersExtendedAppendages(GameTestHelper h){
        var mob=actor(h);var target=h.spawn(EntityType.PIG,new Vec3(1,2,1));target.setNoAi(true);GreeneProjectile[] shot={null};Vec3[] origin={null};
        h.runAfterDelay(10,()->{mob.updateParts();var box=mob.hurtRegions(0).get(0).boxes().stream().max(java.util.Comparator.comparingDouble(b->b.center.y)).orElseThrow();
            var outward=box.center.subtract(mob.position()).multiply(1,0,1).normalize();h.assertTrue(outward.lengthSqr()>.5,"appendage extends away from the root");origin[0]=box.center;
            shot[0]=new GreeneProjectile(h.getLevel(),mob,GreeneMissileFlight.Ammo.RIFLE,GreeneMissileFlight.User.RIFLE,box.center,outward,target);h.getLevel().addFreshEntity(shot[0]);});
        h.runAfterDelay(12,()->{h.assertTrue(shot[0]!=null&&!shot[0].isRemoved()&&shot[0].position().distanceTo(origin[0])>2,"native missile leaves an owned posed cuboid instead of colliding with the owner's multipart proxy");
            armour(h,mob,0,mob.greeneSegmentMaximum(),"owned appendage receives no self-hit");done(h,mob,target,shot[0]);});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=100) public static void occupiedTankSelectsAntiTankAndReceivesHullDamage(GameTestHelper h){
        var mob=actor(h);mob.setPos(h.absoluteVec(new Vec3(8,2,3)));mob.goalSelector.removeAllGoals(g->true);mob.targetSelector.removeAllGoals(g->true);mob.setNoAi(false);
        var tank=com.codex.blacklight.registry.ModEntities.MILITARY_TANK.get().create(h.getLevel());tank.setPos(h.absoluteVec(new Vec3(8,2,12)));h.getLevel().addFreshEntity(tank);
        var crew=h.spawn(EntityType.PIG,new Vec3(8,2,12));crew.setNoAi(true);float health=tank.health();
        h.runAfterDelay(20,()->h.assertTrue(crew.startRiding(tank),"real living crew occupies the actual tank entity"));
        h.runAfterDelay(25,()->{h.assertTrue(mob.begin(EncounterMove.VOLLEY,crew),"normal living-target acquisition resolves the occupied hull");
            h.assertTrue(mob.move()==EncounterMove.ORBS&&mob.greeneProgram()==GreeneWeaponProgram.ANTI_TANK,"tank source branch replaces the on-foot program");});
        h.runAfterDelay(85,()->{h.assertTrue(mob.greeneShots()>0&&tank.health()<health,"actual native missile reaches an articulated armored hull");done(h,mob,crew,tank);});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=35) public static void shotgunProximityCannotDamageThroughSideCover(GameTestHelper h){
        var mob=actor(h);var target=h.spawn(EntityType.PIG,new Vec3(8,2,11.5));target.setNoAi(true);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        var visible=h.spawn(EntityType.PIG,new Vec3(2,2,11.5));visible.setNoAi(true);visible.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        for(int x=4;x<=12;x++)for(int y=2;y<=6;y++)h.setBlock(x,y,10,Blocks.STONE);
        GreeneProjectile[] shot={null},clearShot={null};
        h.runAfterDelay(10,()->{Vec3 from=h.absoluteVec(new Vec3(6,2.3,9.6)),to=from.add(2,0,0);
            h.assertTrue(to.distanceTo(target.position())<GreeneMissileFlight.Ammo.SHOTGUN.proximity,"end point triggers the source target-origin proximity threshold");
            h.assertTrue(h.getLevel().clip(new net.minecraft.world.level.ClipContext(to,target.getBoundingBox().getCenter(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mob)).getType()==HitResult.Type.BLOCK,"wall separates the missile endpoint from every nearby target surface");
            shot[0]=new GreeneProjectile(h.getLevel(),mob,GreeneMissileFlight.Ammo.SHOTGUN,GreeneMissileFlight.User.SHOTGUN,from,new Vec3(1,0,0),target);h.getLevel().addFreshEntity(shot[0]);
            clearShot[0]=new GreeneProjectile(h.getLevel(),mob,GreeneMissileFlight.Ammo.SHOTGUN,GreeneMissileFlight.User.SHOTGUN,h.absoluteVec(new Vec3(0,2.3,9.6)),new Vec3(1,0,0),visible);h.getLevel().addFreshEntity(clearShot[0]);});
        h.runAfterDelay(13,()->{h.assertTrue(shot[0]!=null&&shot[0].finished()&&!shot[0].isRemoved(),"proximity stops flight and retains the source final presentation");
            h.assertTrue(target.getHealth()==target.getMaxHealth(),"the MC proximity damage adapter respects the wall instead of directly hurting its target UUID");
            h.assertTrue(clearShot[0]!=null&&clearShot[0].finished()&&visible.getHealth()<visible.getMaxHealth(),"an equally near exposed target still receives the explicit MC proximity damage");});
        h.runAfterDelay(20,()->{h.assertTrue(shot[0].isRemoved()&&clearShot[0].isRemoved(),"both final presentations expire after their source lifetime");done(h,mob,target,visible);});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=35) public static void finalPresentationSurvivesReloadWithoutASecondHit(GameTestHelper h){
        var mob=actor(h);var target=h.spawn(EntityType.PIG,new Vec3(8,3,11));target.setNoAi(true);target.setNoGravity(true);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        GreeneProjectile[] shot={null},copy={null};float[] after={0};Vec3[] stopped={null};CompoundTag[] saved={null};
        h.runAfterDelay(10,()->{
            shot[0]=new GreeneProjectile(h.getLevel(),mob,GreeneMissileFlight.Ammo.SHOTGUN,GreeneMissileFlight.User.SHOTGUN,
                    h.absoluteVec(new Vec3(8,3.3,8)),new Vec3(0,0,1),target);h.getLevel().addFreshEntity(shot[0]);
        });
        h.runAfterDelay(13,()->{
            h.assertTrue(shot[0].finished()&&!shot[0].isRemoved()&&target.getHealth()<target.getMaxHealth(),"one actual contact entered final");
            after[0]=target.getHealth();stopped[0]=shot[0].position();saved[0]=new CompoundTag();shot[0].saveWithoutId(saved[0]);shot[0].discard();
            target.invulnerableTime=0;mob.discard(); // Neither invulnerability nor owner presence hides a replay bug.
        });
        h.runAfterDelay(14,()->{
            // Let normal entity removal release the UUID before loading it again.
            copy[0]=EncounterEntities.GREENE_PROJECTILE.get().create(h.getLevel());copy[0].load(saved[0]);h.assertTrue(h.getLevel().addFreshEntity(copy[0]),"saved projectile re-enters the level with its original UUID");
            h.assertTrue(copy[0].finished()&&copy[0].getDeltaMovement().lengthSqr()==0,"reload preserves stopped final state");
            h.assertTrue(Math.abs(copy[0].renderFlightSeconds(1)-shot[0].renderFlightSeconds(1))<.0001,"reload preserves spin phase clock");
        });
        h.runAfterDelay(15,()->{h.assertTrue(copy[0].position().distanceTo(stopped[0])<.001,"final cannot resume flight after load");h.assertTrue(target.getHealth()==after[0],"final does not repeat damage");});
        h.runAfterDelay(22,()->{h.assertTrue(copy[0].isRemoved(),"remaining final lifetime expires even without owner");done(h,target);});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=45) public static void ordinaryOnFootAttackUsesTheSourceDistanceBank(GameTestHelper h){
        var mob=actor(h);mob.goalSelector.removeAllGoals(g->true);mob.targetSelector.removeAllGoals(g->true);mob.setNoAi(false);
        var target=h.spawn(EntityType.PIG,new Vec3(8,2,15));target.setNoAi(true);
        h.runAfterDelay(25,()->{
            h.assertTrue(mob.begin(EncounterMove.VOLLEY,target),"ordinary acquisition starts an on-foot attack");
            h.assertTrue(mob.greeneProgram()==GreeneWeaponProgram.INSECT_SWARM,"source XZ band chooses InsectSwarm instead of round-robin DeathFlower");
            var tag=save(mob);h.assertTrue(!tag.contains("GreeneProgramSequence"),"selection no longer depends on saved rotation order");done(h,mob,target);
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=65) public static void removedTargetCancelsSourceWeaponWindup(GameTestHelper h){
        var mob=actor(h);mob.goalSelector.removeAllGoals(g->true);mob.targetSelector.removeAllGoals(g->true);mob.setNoAi(false);
        var target=h.spawn(EntityType.PIG,new Vec3(8,2,15));target.setNoAi(true);
        h.runAfterDelay(25,()->{h.assertTrue(mob.beginGreene(GreeneWeaponProgram.RIFLE,target),"windup starts with a loaded live target");target.discard();});
        h.runAfterDelay(50,()->{h.assertTrue(mob.move()==EncounterMove.NONE&&mob.greeneShots()==0,"target disappearance cancels the program before its first fire window");
            h.assertTrue(h.getLevel().getEntitiesOfClass(GreeneProjectile.class,mob.getBoundingBox().inflate(40),p->p.getOwner()==mob).isEmpty(),"no projectile is created with an orphaned committed target");done(h,mob);});
    }
}
