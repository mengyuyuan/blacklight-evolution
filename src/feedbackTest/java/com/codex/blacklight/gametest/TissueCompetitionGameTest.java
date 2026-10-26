package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.combat.ConsumeSystem;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class TissueCompetitionGameTest {
    @GameTest(template="wall_test",timeoutTicks=35,batch="tissue_competition")
    public static void structuralFrameAndItsHealthSurviveARealEntitySave(GameTestHelper h){
        var original=host(h,TissueCore.MUSCLE,504000,5);original.setNoAi(true);BlacklightInfectedHost restored=null;
        try{
            var frame=original.tissueFrame();h.assertTrue(frame.x>1&&frame.y>1&&frame.z>1,"the mature body develops several axes");
            original.setHealth(original.getMaxHealth()*.4F);var saved=new CompoundTag();original.saveWithoutId(saved);
            restored=ModEntities.INFECTED_HOST.get().create(h.getLevel());restored.load(saved);
            h.assertTrue(restored.tissueFrame().distance(frame)<.0001F&&Math.abs(restored.getBbWidth()-original.getBbWidth())<.001F,"saved axes restore the actual authoritative collider");
            h.assertTrue(Math.abs(restored.getMaxHealth()-original.getMaxHealth())<.01F&&Math.abs(restored.getHealth()-original.getHealth())<.01F,"saving the structural body preserves maximum and current health without granting a reload heal");
        }finally{clean(original);if(restored!=null)restored.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=35,batch="tissue_competition")
    public static void structuralExpansionWaitsForActualRoom(GameTestHelper h){
        var actor=ModEntities.INFECTED_HOST.get().create(h.getLevel());actor.inherit(EntityType.ZOMBIE.create(h.getLevel()));actor.setNoAi(true);
        var pinned=new CompoundTag();actor.saveWithoutId(pinned);pinned.getCompound("BlacklightHost").putString("NativeCore",TissueCore.CHELA.key());actor.readAdditionalSaveData(pinned);
        var location=h.absolutePos(new BlockPos(5,2,5));actor.setPos(location.getX()+.65,location.getY(),location.getZ()+.5);h.getLevel().addFreshEntity(actor);
        for(int y=2;y<=7;y++)h.setBlock(new BlockPos(6,y,5),Blocks.STONE);
        try{
            h.assertTrue(h.getLevel().noCollision(actor,actor.getBoundingBox()),"the original body fits beside the wall");actor.advanceTissue(504000);
            var constrained=actor.tissueFrame();float health=actor.getMaxHealth();
            h.assertTrue(constrained.x==1&&h.getLevel().noCollision(actor,actor.getBoundingBox()),"the broader frame does not grow into a solid wall");
            for(int y=2;y<=7;y++)h.setBlock(new BlockPos(6,y,5),Blocks.AIR);actor.advanceTissue(0);
            h.assertTrue(actor.tissueFrame().x>constrained.x&&actor.getMaxHealth()>health,"available room permits the structural mass and its associated health to develop together");
        }finally{clean(actor);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=35,batch="tissue_competition")
    public static void unfinishedBodiesAndNewOrgansCannotInitiateAnotherConsumption(GameTestHelper h){
        var young=host(h,TissueCore.MUSCLE,TissueGrowth.MATURITY_TICKS,5);var prey=host(h,TissueCore.GRASP,504000,7);
        var developing=host(h,TissueCore.MUSCLE,216000,6);
        try{
            prey.setHealth(prey.getMaxHealth()*.1F);
            h.assertTrue(!TissueCompetition.participant(young)&&!TissueCompetition.tryStart(young,prey),"first mature stage cannot start predation before its final growth shape");
            var genome=new TissueInheritance(Map.of(TissueCore.BLADE,new TissueInheritance.Organ(10,20,3600,3600,TissueInheritance.BLADE,42)),1,20,false,null,1);
            var tag=new CompoundTag();developing.saveWithoutId(tag);tag.put("BlacklightTissueInheritance",TissueInheritanceCodec.write(genome));developing.load(tag);
            h.assertTrue(TissueCompetition.participant(developing)&&!TissueCompetition.tryStart(developing,prey),"an adult with new organs still forming or digesting cannot start its next consumption");
        }finally{clean(young,prey,developing);}h.succeed();
    }
    private static BlacklightInfectedHost host(GameTestHelper h,TissueCore core,long age,int x){
        for(int bx=2;bx<=12;bx++)for(int z=2;z<=9;z++)h.setBlock(new BlockPos(bx,1,z),Blocks.STONE);
        var host=ModEntities.INFECTED_HOST.get().create(h.getLevel());host.inherit(EntityType.COW.create(h.getLevel()));
        long seed=0;while(TissueCore.forSeed(seed)!=core)seed++;
        long identity=UUID.randomUUID().getMostSignificantBits();host.setUUID(new UUID(identity,identity^seed));var pinned=new net.minecraft.nbt.CompoundTag();host.saveWithoutId(pinned);pinned.getCompound("BlacklightHost").putString("NativeCore",core.key());host.readAdditionalSaveData(pinned);
        host.advanceTissue(age);host.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        host.setPos(h.absolutePos(new BlockPos(x,2,5)).getBottomCenter());h.getLevel().addFreshEntity(host);return host;
    }
    private static BlacklightInfectedHost[] pair(GameTestHelper h){
        var actor=host(h,TissueCore.MUSCLE,216000,5);var prey=host(h,TissueCore.GRASP,504000,7);
        prey.setHealth(prey.getMaxHealth()*.10F);actor.setTarget(prey);return new BlacklightInfectedHost[]{actor,prey};
    }
    private static void clean(BlacklightInfectedHost... hosts){for(var host:hosts){TissueCompetition.release(host);host.discard();}}
    @GameTest(template="wall_test",timeoutTicks=140,batch="tissue_competition")
    public static void equallyOldMatureHostsActuallyCompeteWithoutPlayerDamage(GameTestHelper h){
        var actor=host(h,TissueCore.MUSCLE,72000,5);var other=host(h,TissueCore.MUSCLE,72000,7);
        h.runAfterDelay(100,()->{
            try{
                h.assertTrue(actor.getHealth()<actor.getMaxHealth()||other.getHealth()<other.getMaxHealth()
                        ||TissueCompetition.controls(actor)||TissueCompetition.controls(other),"natural bounded AI finds another mature host and lands real combat damage without an administrator setting a target");
            }finally{clean(actor,other);}h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=140,batch="tissue_competition")
    public static void aLiveCaptureSettlesOnceAndDigestsGradually(GameTestHelper h){
        var pair=pair(h);var actor=pair[0];var prey=pair[1];
        h.assertTrue(TissueCompetition.tryStart(actor,prey),"weakened mature infected hosts can be captured on contact");
        h.assertTrue(!ConsumeSystem.eligible(prey)&&!ConsumeSystem.eligible(actor),"players cannot steal either participant");
        h.runAfterDelay(105,()->{
            try{
                h.assertTrue(!prey.isAlive()&&actor.tissueInheritance().meals()==1&&actor.tissueInheritance().acquiredOrgans()==1,"one real accepted death produces exactly one personal meal and one native source organ; alive="+prey.isAlive()+" meals="+actor.tissueInheritance().meals());
                var organ=actor.tissueInheritance().organ(TissueCore.GRASP);
                h.assertTrue(organ!=null&&organ.pending()>0&&organ.integrated()<organ.pending(),"new organs integrate over time instead of instantly becoming full strength");
                actor.killedEntity(h.getLevel(),prey);
                h.assertTrue(actor.tissueInheritance().meals()==1&&actor.tissueInheritance().acquiredOrgans()==1&&!TissueCompetition.controls(actor),"normal/repeated death callbacks cannot mint organ counts or leave a claim");
            }finally{clean(pair);}h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=35,batch="tissue_competition")
    public static void healRecoveryAndCompetingCapturesReleaseWithoutReward(GameTestHelper h){
        var pair=pair(h);var other=host(h,TissueCore.JAW,216000,6);
        try{
            h.assertTrue(TissueCompetition.tryStart(pair[0],pair[1]),"first actor owns capture");
            h.assertTrue(!TissueCompetition.tryStart(other,pair[1]),"a second actor cannot consume the same target");
            pair[1].setHealth(pair[1].getMaxHealth()*.3F);TissueCompetition.tick(pair[0]);
            h.assertTrue(!TissueCompetition.controls(pair[0])&&!TissueCompetition.controls(pair[1])&&pair[0].tissueInheritance().meals()==0,"healing above threshold aborts and releases both actors");
            h.assertTrue(ConsumeSystem.eligible(pair[1]),"released prey is available for player grab again");
        }finally{clean(pair);clean(other);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=140,batch="tissue_competition")
    public static void aDeathVetoCannotPayOrLeaveTheTargetClaimed(GameTestHelper h){
        var pair=pair(h);Consumer<LivingDeathEvent> veto=e->{if(e.getEntity()==pair[1]){e.setCanceled(true);pair[1].setHealth(pair[1].getMaxHealth());}};
        NeoForge.EVENT_BUS.addListener(veto);h.assertTrue(TissueCompetition.tryStart(pair[0],pair[1]),"capture starts");
        h.runAfterDelay(105,()->{
            try{
                h.assertTrue(pair[1].isAlive()&&pair[0].tissueInheritance().meals()==0,"a rejected execution grants no organs, points or meals");
                h.assertTrue(pair[0].tissueInheritance().organs().isEmpty()&&!TissueCompetition.controls(pair[1]),"cancelled death leaves no pending genes or permanent control");
            }finally{NeoForge.EVENT_BUS.unregister(veto);clean(pair);}h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=35,batch="tissue_competition")
    public static void realHeavyDamageInterruptsWithoutInvulnerability(GameTestHelper h){
        var pair=pair(h);
        try{
            h.assertTrue(TissueCompetition.tryStart(pair[0],pair[1]),"capture starts");
            float before=pair[0].getHealth();pair[0].hurt(pair[0].damageSources().generic(),pair[0].getMaxHealth()*.4F);
            h.assertTrue(pair[0].getHealth()<before,"consumer remains damageable");
            h.assertTrue(!TissueCompetition.controls(pair[0])&&!TissueCompetition.controls(pair[1])&&pair[0].tissueInheritance().meals()==0,"a heavy accepted hit cancels capture and pays nothing");
        }finally{clean(pair);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=35,batch="tissue_competition")
    public static void interruptedScenesDoNotResumeOrPayAfterReload(GameTestHelper h){
        var pair=pair(h);BlacklightInfectedHost restored=null;
        try{
            h.assertTrue(TissueCompetition.tryStart(pair[0],pair[1]),"capture starts");
            var saved=new CompoundTag();pair[0].saveWithoutId(saved);clean(pair[0]);
            restored=ModEntities.INFECTED_HOST.get().create(h.getLevel());restored.load(saved);
            h.assertTrue(restored.cannibalTargetId()==-1&&restored.tissueInheritance().meals()==0,"unfinished sessions load unlocked and cannot replay a reward");
            h.assertTrue(!TissueCompetition.controls(pair[1]),"unloading the actor immediately releases its victim");
            var genome=new TissueInheritance(Map.of(TissueCore.GRASP,new TissueInheritance.Organ(120,90,700,300,TissueInheritance.TENDRIL,42)),4,250,false,UUID.randomUUID(),8,6);
            var roundtrip=TissueInheritanceCodec.read(TissueInheritanceCodec.write(genome));
            h.assertTrue(roundtrip.organs().equals(genome.organs())&&roundtrip.meals()==4&&roundtrip.acquiredOrgans()==6&&roundtrip.lastOperation().equals(genome.lastOperation()),"integrated/pending organs, acquired source count, maturity and settlement ID survive NBT exactly");
            var legacy=TissueInheritanceCodec.write(genome);legacy.remove("AcquiredOrgans");legacy.putBoolean("Qualified",true);
            var migrated=TissueInheritanceCodec.read(legacy);
            h.assertTrue(migrated.acquiredOrgans()==1&&!migrated.qualified(),"an old save retains its known organ and cannot invent six source organs");
        }finally{clean(pair);if(restored!=null)restored.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=35,batch="tissue_competition")
    public static void inPlaceReloadAlsoReleasesAnUnfinishedCapture(GameTestHelper h){
        var pair=pair(h);
        try{
            h.assertTrue(TissueCompetition.tryStart(pair[0],pair[1]),"capture starts");
            var saved=new CompoundTag();pair[1].saveWithoutId(saved);pair[1].load(saved);
            h.assertTrue(!TissueCompetition.controls(pair[0])&&!TissueCompetition.controls(pair[1])
                    &&pair[0].cannibalTargetId()==-1&&pair[0].tissueInheritance().meals()==0,"loading either participant in place releases both ends and cannot replay a settlement");
        }finally{clean(pair);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=35,batch="tissue_competition")
    public static void deadOrExactlyThresholdPreyCannotBeConsumed(GameTestHelper h){
        var pair=pair(h);
        try{
            pair[1].setHealth(pair[1].getMaxHealth()*.15F);
            h.assertTrue(!TissueCompetition.tryStart(pair[0],pair[1]),"the trigger is strictly below fifteen percent");
            pair[1].setHealth(0);
            h.assertTrue(!TissueCompetition.tryStart(pair[0],pair[1])&&pair[0].tissueInheritance().meals()==0,"a corpse is not a living consumption");
        }finally{clean(pair);}h.succeed();
    }
}
