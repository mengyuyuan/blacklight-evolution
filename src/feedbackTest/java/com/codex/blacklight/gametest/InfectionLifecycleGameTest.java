package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class InfectionLifecycleGameTest {
    @GameTest(template="wall_test",timeoutTicks=60,batch="lifecycle_sparse_growth")
    public static void MatureOrganDevelopmentBatchesPayloadsAndKeepsPaidWorkOnReload(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(5,140,5));
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
        var host=ModEntities.INFECTED_HOST.get().create(level);host.setPos(origin.getBottomCenter());host.inherit(EntityType.COW.create(level));host.advanceTissue(72000);
        var genes=new TissueInheritance(Map.of(TissueCore.BLADE,new TissueInheritance.Organ(100,0,0,0,1,71)),0,0,false,null,1,1);
        var tag=new CompoundTag();host.saveWithoutId(tag);tag.putInt("BlacklightTissueNutrition",2400);tag.put("BlacklightTissueInheritance",TissueInheritanceCodec.write(genes));host.load(tag);host.setNoAi(true);
        BlacklightInfectedHost restored=null;
        try{
            for(int t=0;t<20;t++)host.tick();host.saveWithoutId(tag);
            h.assertTrue(tag.getInt("BlacklightTissueCultivation")==20&&host.tissueInheritance().revision()==1,"one paid second accumulates without a full ancestry publication");
            restored=ModEntities.INFECTED_HOST.get().create(level);restored.load(tag);restored.setNoAi(true);
            for(int t=0;t<1160;t++)restored.tick();
            h.assertTrue(restored.tissueInheritance().revision()==1,"the next 58 seconds still retain the unchanged genetic snapshot");
            for(int t=0;t<20;t++)restored.tick();restored.saveWithoutId(tag);
            h.assertTrue(restored.tissueInheritance().revision()==2&&Math.abs(restored.tissueInheritance().organ(TissueCore.BLADE).integrated()-101)<.00001&&tag.getInt("BlacklightTissueCultivation")==0,"the saved first second contributes to exactly one paid minute of organ development");
        }finally{host.discard();if(restored!=null)restored.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=220,batch="lifecycle_multiplayer_vials")
    public static void DifferentPlayersShatterVialsWithoutResettingTheClockOrMultiplyingBirths(GameTestHelper h){
        var level=h.getLevel();var storage=level.getServer().overworld().getDataStorage();var previous=OutbreakData.get(level.getServer());
        int batch=OutbreakConfig.BATCH.get();boolean spawning=level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        var difficulty=level.getDifficulty();var entities=new ArrayList<Entity>();
        var base=h.absolutePos(new BlockPos(5,130,5));
        // GameTest roots are not chunk-aligned. Keep every projectile and birth
        // in the same ticking chunk instead of relying on a neighbouring ticket.
        var origin=new BlockPos((base.getX()>>4)*16+4,base.getY(),(base.getZ()>>4)*16+8);
        var first=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"VialFirst"));
        var second=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"VialLater"));
        first.setPos(origin.getBottomCenter().add(0,0,32));second.setPos(origin.getBottomCenter().add(0,0,-32));
        Runnable close=()->{entities.forEach(Entity::discard);storage.set("blacklight_outbreak",previous);OutbreakConfig.BATCH.set(batch);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawning,level.getServer());level.getServer().setDifficulty(difficulty,true);};
        storage.set("blacklight_outbreak",new OutbreakData());OutbreakConfig.BATCH.set(2);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,level.getServer());level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        for(int x=-4;x<=16;x++)for(int z=-4;z<=4;z++)level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
        var vial1=new com.codex.blacklight.entity.BlacklightVirusVialEntity(level,first);vial1.setPos(origin.getBottomCenter().add(0,2,0));vial1.setDeltaMovement(0,-1,0);level.addFreshEntity(vial1);entities.add(vial1);
        h.runAfterDelay(12,()->{
            try{
                var clock=OutbreakData.get(level.getServer());long started=clock.save(new CompoundTag(),level.registryAccess()).getLong("started");
                h.assertTrue(clock.active()&&vial1.isRemoved(),"first player's real impact activates the shared save");
                var vial2=new com.codex.blacklight.entity.BlacklightVirusVialEntity(level,second);vial2.setPos(origin.east(8).getBottomCenter().add(0,2,0));vial2.setDeltaMovement(0,-1,0);level.addFreshEntity(vial2);entities.add(vial2);
                h.runAfterDelay(12,()->{
                    try{
                        h.assertTrue(vial2.isRemoved()&&OutbreakData.get(level.getServer())==clock&&clock.save(new CompoundTag(),level.registryAccess()).getLong("started")==started,"a different player's later impact keeps the same original clock; removed="+vial2.isRemoved()+" ticking="+level.isPositionEntityTicking(vial2.blockPosition())+" position="+vial2.position()+" sameClock="+(OutbreakData.get(level.getServer())==clock)+" first="+started+" now="+OutbreakData.get(level.getServer()).save(new CompoundTag(),level.registryAccess()).getLong("started"));
                        h.runAfterDelay(OutbreakConfig.INTERVAL.get()+1,()->{
                            try{
                                for(int x:new int[]{0,5}){var mob=WorldOutbreakSystem.spawnAt(level,origin.east(x),OutbreakRules.Tier.FRESH);h.assertTrue(mob!=null,"shared window accepts its two real births");mob.setNoAi(true);entities.add(mob);}
                                h.assertTrue(WorldOutbreakSystem.spawnAt(level,origin.east(10),OutbreakRules.Tier.FRESH)==null&&!WorldOutbreakSystem.populationRoom(level,origin.east(10)),"another producer/player cannot obtain a separate full birth allowance");
                            }finally{close.run();}h.succeed();
                        });
                    }catch(Throwable failure){close.run();throw failure;}
                });
            }catch(Throwable failure){close.run();throw failure;}
        });
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="lifecycle_dormancy")
    public static void DormantCoreCannotGrowOrBroodAndClearedHistoryFreesLiveSlots(GameTestHelper h){
        var level=h.getLevel();var data=BuildingInfectionData.get(level);
        var previous=new ArrayList<>(data.colonies);var cleared=new LinkedHashSet<>(data.clearedSites);
        var storage=level.getServer().overworld().getDataStorage();var original=OutbreakData.get(level.getServer());
        boolean grief=level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        var origin=h.absolutePos(new BlockPos(5,100,5));
        try{
            data.colonies.clear();data.clearedSites.clear();storage.set("blacklight_outbreak",new OutbreakData());
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,level.getServer());
            level.setBlock(origin.below(),Blocks.STONE.defaultBlockState(),2);
            level.setBlock(origin,ModBlocks.BUILDING_CORE.get().defaultBlockState(),3);
            var colony=data.colonies.getFirst();colony.stage=3;
            h.assertTrue(BuildingInfectionSystem.advance(level)==0&&colony.activeTicks==0&&!BuildingInfectionSystem.hatch(level,colony),"dormant building neither advances nor hatches before the first vial");
            var active=new OutbreakData();active.start(level.getServer().overworld().getGameTime());storage.set("blacklight_outbreak",active);
            h.assertTrue(BuildingInfectionSystem.advance(level)>0&&colony.activeTicks==5,"release resumes actual loaded growth");
            level.removeBlock(origin,false);
            h.assertTrue(data.activeColonies()==0&&data.clearedSites.contains(origin.asLong()),"clearing keeps a compact tombstone");
            h.assertTrue(!BuildingInfectionSystem.canRegister(level,origin),"the cleared building cannot be reseeded");
            h.assertTrue(BuildingInfectionSystem.canRegister(level,origin.east(220)),"a different building still has a live slot");
        }finally{
            level.removeBlock(origin,false);data.colonies.clear();data.colonies.addAll(previous);data.clearedSites.clear();data.clearedSites.addAll(cleared);
            storage.set("blacklight_outbreak",original);level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(grief,level.getServer());
        }h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=60,batch="lifecycle_shared_cap")
    public static void AmbientAndBroodBirthsUseTheSameDimensionCap(GameTestHelper h){
        var level=h.getLevel();var storage=level.getServer().overworld().getDataStorage();var original=OutbreakData.get(level.getServer());
        var active=new OutbreakData();active.start(level.getServer().overworld().getGameTime());storage.set("blacklight_outbreak",active);
        int cap=OutbreakConfig.DIMENSION_CAP.get();var residents=new ArrayList<Mob>();
        try{
            OutbreakConfig.DIMENSION_CAP.set(1);
            var resident=ModEntities.COMMON_INFECTED.get().create(level);resident.setNoAi(true);resident.setPos(h.absolutePos(new BlockPos(5,90,5)).getCenter());level.addFreshEntity(resident);residents.add(resident);
            h.assertTrue(!WorldOutbreakSystem.populationRoom(level,h.absolutePos(new BlockPos(5,90,5))),"ambient and building producers both see the actual occupied shared cap");
        }finally{residents.forEach(Entity::discard);OutbreakConfig.DIMENSION_CAP.set(cap);storage.set("blacklight_outbreak",original);}h.succeed();
    }

    /** Old ages/food are accelerated fixtures. Captures and deaths are real;
     * no qualified flag, meal count or inherited-organ inventory is prefilled. */
    @GameTest(template="wall_test",timeoutTicks=1600,batch="lifecycle_six_meals")
    public static void SixAcceptedCapturesIntegrateThenAutomaticallyBecomeAnEvolved(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(5,100,5));
        for(int x=-14;x<=14;x++)for(int z=-14;z<=14;z++)level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
        var actors=new ArrayList<BlacklightInfectedHost>();
        class Trial {
            int meals;
            BlacklightInfectedHost body(EntityType<? extends Mob> type,TissueCore core,long age){
                var host=ModEntities.INFECTED_HOST.get().create(level);host.setPos(origin.getBottomCenter());host.inherit(type.create(level));
                var tag=new CompoundTag();host.saveWithoutId(tag);tag.getCompound("BlacklightHost").putString("NativeCore",core.key());tag.putInt("BlacklightTissueNutrition",2400);host.load(tag);
                host.advanceTissue(age);host.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);level.addFreshEntity(host);actors.add(host);return host;
            }
            final BlacklightInfectedHost actor=body(EntityType.COW,TissueCore.MUSCLE,504000);
            void close(){for(var host:actors){TissueCompetition.interrupt(host);host.discard();}}
            void next(){
                try{
                    if(meals==6){
                        actor.setTarget(null);for(int t=0;t<240;t++)actor.tick();
                        h.assertTrue(actor.tissueInheritance().meals()==6&&actor.tissueInheritance().acquiredOrgans()==6,"six personally accepted captures preserve six actual donor organs");
                        h.assertTrue(actor.isEvolved()&&!actor.isMetamorphosing(),"integrated organs trigger and complete the actual automatic human metamorphosis");
                        var saved=new CompoundTag();actor.saveWithoutId(saved);var restored=ModEntities.INFECTED_HOST.get().create(level);restored.load(saved);
                        h.assertTrue(restored.isEvolved()&&restored.tissueInheritance().meals()==6,"reload preserves the evolved body and actual consumption history");restored.discard();close();h.succeed();return;
                    }
                    var core=new TissueCore[]{TissueCore.CARAPACE,TissueCore.JAW,TissueCore.GRASP}[meals%3];
                    var prey=body(meals%2==0?EntityType.PIG:EntityType.SPIDER,core,10_000_000);
                    prey.setPos(actor.position().add(0,0,(actor.getBbWidth()+prey.getBbWidth())*.5+.05));
                    prey.setHealth(prey.getMaxHealth()*.10F);actor.setNoAi(false);actor.setTarget(prey);
                    h.assertTrue(TissueCompetition.tryStart(actor,prey),"real weakened mature donor can be captured at meal "+meals);
                    h.runAfterDelay(102,()->{
                        try{
                            h.assertTrue(!prey.isAlive()&&actor.tissueInheritance().meals()==meals+1,"the real accepted death settles once");
                            actor.setNoAi(true);for(int tick=0;tick<3600;tick++)actor.tick();actor.setNoAi(false);
                            h.assertTrue(actor.tissueInheritance().readyToHunt(actor.tissueAge()),"new organs finish paid integration before the next consumption");
                            if(meals<5)h.assertTrue(!actor.isEvolved(),"fewer than six real captures cannot produce a human body");
                            meals++;h.runAfterDelay(101,this::next);
                        }catch(Throwable failure){close();throw failure;}
                    });
                }catch(Throwable failure){close();throw failure;}
            }
        }
        new Trial().next();
    }
}
