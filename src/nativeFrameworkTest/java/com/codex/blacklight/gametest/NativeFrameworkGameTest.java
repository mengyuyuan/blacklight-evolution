package com.codex.blacklight.gametest;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.config.InfectionEcologyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class NativeFrameworkGameTest {
    private static boolean installed(Mob mob){return mob.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal().getClass().getEnclosingClass()==NativeInfectionCombat.class);}
    @GameTest(template="wall_test",timeoutTicks=170,batch="framework_fallback_combat")
    public static void unarmedPreservedAnimalPursuesBitesAndCuresWithoutReplacingItsAi(GameTestHelper h){
        var old=InfectionEcologyConfig.PRESERVE.get();
        InfectionEcologyConfig.PRESERVE.set(java.util.List.of("minecraft:cow"));
        for(int x=0;x<14;x++)for(int z=0;z<10;z++)h.setBlock(x,1,z,Blocks.STONE);
        var source=h.spawn(EntityType.COW,3,2,4);var prey=h.spawn(EntityType.VILLAGER,8,2,4);prey.setNoAi(true);
        var id=source.getUUID();var nav=source.getNavigation();var move=source.getMoveControl();var brain=source.getBrain();
        var goals=source.goalSelector.getAvailableGoals().stream().map(g->g.getGoal()).toList();
        var start=source.position();float before=prey.getHealth();
        h.assertTrue(BlacklightInfectionSystem.infect(source,null),"fixture animal is infected through real infection entry");
        NativeInfection.tick(source,h.getLevel());
        h.assertTrue(NativeInfectionCombat.eligible(source)&&installed(source),"unarmed animal gains one cooperative goal");
        h.runAfterDelay(120,()->{try{
            h.assertTrue(prey.getHealth()<before,"actual AI pursuit and contact strike must damage the target; health="+prey.getHealth()+" position="+source.position());
            h.assertTrue(source.position().distanceToSqr(start)>.5,"fallback must pursue, not hit at arbitrary range");
            h.assertTrue(source.getUUID().equals(id)&&source.getNavigation()==nav&&source.getMoveControl()==move&&source.getBrain()==brain,"native AI/controller identities remain intact");
            h.assertTrue(source.goalSelector.getAvailableGoals().stream().map(g->g.getGoal()).toList().containsAll(goals),"no native goal is removed");
            source.removeEffect(com.codex.blacklight.registry.ModEffects.BLACKLIGHT_INFECTION);NativeInfection.clear(source);
            h.assertTrue(!installed(source),"cure removes owned attack goal");h.succeed();
        }finally{InfectionEcologyConfig.PRESERVE.set(old);source.discard();prey.discard();}});
    }
    @GameTest(template="wall_test",batch="framework_native_priority")
    public static void rangedAndFlyingControllersAreNotReplacedAndNativeTargetsWin(GameTestHelper h){
        var old=InfectionEcologyConfig.PRESERVE.get();
        InfectionEcologyConfig.PRESERVE.set(java.util.List.of("minecraft:skeleton","minecraft:phantom","minecraft:cow"));
        var ranged=h.spawn(EntityType.SKELETON,3,2,3);var flyer=h.spawn(EntityType.PHANTOM,5,6,5);var cow=h.spawn(EntityType.COW,7,2,3);
        var target=h.spawn(EntityType.PIG,8,2,5);target.setNoAi(true);
        var rangedNav=ranged.getNavigation();var rangedMove=ranged.getMoveControl();var flight=flyer.getMoveControl();
        var nativeGoals=ranged.goalSelector.getAvailableGoals().stream().map(g->g.getGoal()).toList();
        try{
            BlacklightInfectionSystem.infect(ranged,null);BlacklightInfectionSystem.infect(flyer,null);BlacklightInfectionSystem.infect(cow,null);
            ranged.setTarget(target);flyer.setTarget(target);cow.setTarget(target);
            NativeInfection.tick(ranged,h.getLevel());NativeInfection.tick(flyer,h.getLevel());NativeInfection.tick(cow,h.getLevel());
            h.assertTrue(!installed(ranged)&&!installed(flyer),"native ranged and aerial combat never get passive fallback");
            h.assertTrue(ranged.getNavigation()==rangedNav&&ranged.getMoveControl()==rangedMove&&flyer.getMoveControl()==flight,"original flight/navigation controls survive infection");
            h.assertTrue(ranged.getTarget()==target&&flyer.getTarget()==target&&cow.getTarget()==target,"existing native targets are untouched");
            h.assertTrue(ranged.goalSelector.getAvailableGoals().stream().map(g->g.getGoal()).toList().containsAll(nativeGoals),"native bow goal remains registered");
            NativeInfectionCombat.update(cow);NativeInfectionCombat.update(cow);
            h.assertTrue(cow.goalSelector.getAvailableGoals().stream().filter(g->g.getGoal().getClass().getEnclosingClass()==NativeInfectionCombat.class).count()==1,"repeated updates cannot duplicate the goal");
            h.succeed();
        }finally{InfectionEcologyConfig.PRESERVE.set(old);ranged.discard();flyer.discard();cow.discard();target.discard();}
    }
    @GameTest(template="wall_test",batch="framework_policy_disable")
    public static void fallbackCanBeDisabledAndNeverAttacksInfectedAllies(GameTestHelper h){
        var old=InfectionEcologyConfig.PRESERVE.get();boolean enabled=InfectionEcologyConfig.NATIVE_FALLBACK.get();
        InfectionEcologyConfig.PRESERVE.set(java.util.List.of("minecraft:cow"));
        var cow=h.spawn(EntityType.COW,3,2,3);var ally=h.spawn(EntityType.COW,4,2,4);
        try{
            BlacklightInfectionSystem.infect(cow,null);BlacklightInfectionSystem.infect(ally,null);NativeInfection.tick(cow,h.getLevel());
            h.assertTrue(!NativeInfectionCombat.validTarget(cow,ally)&&!NativeInfectionCombat.validTarget(cow,cow),"infected allies/self cannot be hunted");
            InfectionEcologyConfig.NATIVE_FALLBACK.set(false);NativeInfectionCombat.update(cow);
            h.assertTrue(!installed(cow),"server policy immediately removes owned fallback when synchronized");h.succeed();
        }finally{InfectionEcologyConfig.PRESERVE.set(old);InfectionEcologyConfig.NATIVE_FALLBACK.set(enabled);cow.discard();ally.discard();}
    }

    @GameTest(template="wall_test",timeoutTicks=70,batch="framework_diagnostic_receipts")
    public static void scopedCommandRecordsRealDamageAndExpiresWithoutChangingCombat(GameTestHelper h) throws Exception {
        var target=h.spawn(EntityType.COW,3,2,3);target.setNoAi(true);
        var source=h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withPermission(2);
        var dispatcher=h.getLevel().getServer().getCommands().getDispatcher();
        var config=com.codex.blacklight.config.CrossModCombatConfig.NATIVE_DIAGNOSTICS;boolean old=config.get();config.set(false);
        try {
            h.assertTrue(dispatcher.execute("blacklight compat trace "+target.getUUID()+" 1",source)==1,"real command arms one-second observation");
            target.hurt(h.getLevel().damageSources().generic(),2);
            var trace=com.codex.blacklight.combat.NativeHitObservation.last(target).orElseThrow();
            h.assertTrue(trace.result()==com.codex.blacklight.combat.NativeHitObservation.Result.HEALTH&&trace.before().health()>trace.after().health(),"ordinary real damage receipt shows health loss");
            h.assertTrue(dispatcher.execute("blacklight compat inspect "+target.getUUID(),source)==1,"console can inspect native stats and receipt");
            h.runAfterDelay(25,()->{try{
                var previous=com.codex.blacklight.combat.NativeHitObservation.last(target).orElseThrow();target.invulnerableTime=0;
                h.assertTrue(target.hurt(h.getLevel().damageSources().generic(),1),"ordinary combat still works after watch expires");
                h.assertTrue(com.codex.blacklight.combat.NativeHitObservation.last(target).orElseThrow()==previous,"expired watch does not record new unrelated hits");
                h.assertTrue(dispatcher.execute("blacklight compat stop "+target.getUUID(),source)==1&&com.codex.blacklight.combat.NativeHitObservation.last(target).isEmpty(),"stop removes receipts");h.succeed();
            }catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new IllegalStateException(e);}finally{config.set(old);target.discard();}});
        }catch(Exception e){config.set(old);target.discard();throw e;}
    }
    @GameTest(template="wall_test",batch="framework_diagnostic_isolation")
    public static void diagnosingForeignHurtNeverChangesItsReturnContract(GameTestHelper h){
        var target=h.spawn(EntityType.COW,3,2,3);target.setNoAi(true);
        var observation=com.codex.blacklight.combat.NativeHitObservation.class;
        try{
            h.assertTrue(com.codex.blacklight.combat.NativeHitObservation.watch(target,10),"watch can be armed without global configuration");
            var receipt=com.codex.blacklight.combat.NativeHitObservation.begin(target,h.getLevel().damageSources().generic(),2);
            h.assertTrue(receipt!=null,"watched foreign hit is observed");
            target.setHealth(target.getHealth()-2);
            h.assertTrue(!receipt.finish(false),"diagnostic observation must not normalize a non-Blacklight hurt return");
            target.discard();h.assertTrue(com.codex.blacklight.combat.NativeHitObservation.last(target).isEmpty(),"removed entity has no inspectable receipt");h.succeed();
        }finally{com.codex.blacklight.combat.NativeHitObservation.forget(target);target.discard();}
    }

    @GameTest(template="wall_test",batch="framework_native_teardown")
    public static void removedNativeEntityCannotTickOrRecreateInfectionData(GameTestHelper h){
        var cow=h.spawn(EntityType.COW,3,2,3);
        var old=InfectionEcologyConfig.PRESERVE.get();InfectionEcologyConfig.PRESERVE.set(java.util.List.of("minecraft:cow"));
        try{
            BlacklightInfectionSystem.infect(cow,null);NativeInfection.tick(cow,h.getLevel());
            var data=cow.getPersistentData().getCompound("BlacklightNativeInfection").copy();cow.discard();
            NativeInfection.tick(cow,h.getLevel());NativeInfection.clear(cow);
            h.assertTrue(cow.getPersistentData().getCompound("BlacklightNativeInfection").equals(data),"late native infection callback must not rewrite removed entity storage");
            h.assertTrue(NativeInfection.age(cow)==0&&!NativeInfection.preserve(null),"presentation reads unavailable native age without recreating storage");h.succeed();
        }finally{InfectionEcologyConfig.PRESERVE.set(old);cow.discard();}
    }
}
