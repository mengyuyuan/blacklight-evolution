package com.codex.blacklight.gametest;

import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.*;

/** Real pre-publication join callbacks, not synthetic counter-only tests. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class OutbreakBirthReservationGameTest {
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final ServerLevel level;final BlockPos origin;final OutbreakData old;
        final int batch,dimension,local;final boolean spawning,enabled;final Difficulty difficulty;
        final Set<Mob> ownedMobs=new LinkedHashSet<>();final Map<BlockPos,BlockState> blocks=new LinkedHashMap<>();
        final Set<ChunkPos> forced=new HashSet<>();
        Fixture(GameTestHelper h){
            this.h=h;level=h.getLevel();origin=h.absolutePos(new BlockPos(5,100,5));
            old=OutbreakData.get(level.getServer());var active=new OutbreakData();active.start(level.getServer().overworld().getGameTime());level.getServer().overworld().getDataStorage().set("blacklight_outbreak",active);
            batch=OutbreakConfig.BATCH.get();dimension=OutbreakConfig.DIMENSION_CAP.get();local=OutbreakConfig.LOCAL_CAP.get();enabled=OutbreakConfig.ENABLED.get();
            spawning=level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);difficulty=level.getDifficulty();
            OutbreakConfig.ENABLED.set(true);OutbreakConfig.BATCH.set(16);OutbreakConfig.DIMENSION_CAP.set(128);OutbreakConfig.LOCAL_CAP.set(18);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,level.getServer());level.getServer().setDifficulty(Difficulty.NORMAL,true);
            floor(origin);
        }
        void floor(BlockPos at){
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
                var cp=new ChunkPos((at.getX()>>4)+x,(at.getZ()>>4)+z);
                if(!level.getForcedChunks().contains(cp.toLong())){level.setChunkForced(cp.x,cp.z,true);forced.add(cp);}
                level.getChunk(cp.x,cp.z);
            }
            for(int x=-4;x<=8;x++)for(int z=-4;z<=4;z++){
                var p=at.offset(x,-1,z);blocks.putIfAbsent(p,level.getBlockState(p));level.setBlock(p,Blocks.STONE.defaultBlockState(),2);
                for(int y=0;y<3;y++){var air=at.offset(x,y,z);blocks.putIfAbsent(air,level.getBlockState(air));level.setBlock(air,Blocks.AIR.defaultBlockState(),2);}
            }
        }
        void leaveOneDimensionSlot(){
            int existing=0;
            for(var entity:level.getAllEntities())
                if(entity instanceof Mob mob&&mob.isAlive()&&BlacklightInfectionSystem.isInfected(mob))existing++;
            OutbreakConfig.DIMENSION_CAP.set(existing+1);
        }
        Mob spawn(BlockPos pos){var m=WorldOutbreakSystem.spawnAt(level,pos,OutbreakRules.Tier.FRESH);if(m!=null){ownedMobs.add(m);m.setNoAi(true);}return m;}
        void run(Runnable action){h.runAfterDelay(OutbreakConfig.INTERVAL.get()+5,()->{try{action.run();}finally{close();}h.succeed();});}
        public void close(){
            for(var mob:ownedMobs)mob.discard();
            for(var e:blocks.entrySet())level.setBlock(e.getKey(),e.getValue(),2);for(var cp:forced)level.setChunkForced(cp.x,cp.z,false);
            level.getServer().overworld().getDataStorage().set("blacklight_outbreak",old);OutbreakConfig.BATCH.set(batch);OutbreakConfig.DIMENSION_CAP.set(dimension);OutbreakConfig.LOCAL_CAP.set(local);OutbreakConfig.ENABLED.set(enabled);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawning,level.getServer());level.getServer().setDifficulty(difficulty,true);
        }
    }
    @GameTest(template="wall_test",timeoutTicks=240,batch="birth_fixture_ownership")
    public static void birthCleanupPreservesUnrelatedMobsAddedDuringTheEncounter(GameTestHelper h){
        var f=new Fixture(h);
        h.runAfterDelay(OutbreakConfig.INTERVAL.get()+5,()->{
            var bystander=EntityType.COW.create(f.level);
            bystander.setPos(f.origin.getX()+4.5,f.origin.getY(),f.origin.getZ()+0.5);
            try{
                Mob owned;
                try{
                    h.assertTrue(f.level.addFreshEntity(bystander),"an independent resident joins the same area after the fixture starts");
                    owned=f.spawn(f.origin);
                    h.assertTrue(owned!=null,"the fixture publishes its own infected child");
                }finally{f.close();}
                h.assertTrue(owned.isRemoved()&&f.level.getEntity(bystander.getUUID())==bystander,
                        "closing an encounter removes its own births and leaves unrelated residents in the world");
            }finally{bystander.discard();}
            h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=240,batch="birth_pending_batch")
    public static void anActualReentrantJoinCannotObtainTheSameLastGlobalBirth(GameTestHelper h){var f=new Fixture(h);OutbreakConfig.BATCH.set(1);f.run(()->{
        int[] calls={0};Mob[] nested={null};Consumer<EntityJoinLevelEvent> callback=e->{if(e.getLevel()==f.level&&e.getEntity() instanceof com.codex.blacklight.entity.BlacklightCommonInfected&&calls[0]++==0)nested[0]=f.spawn(f.origin.east(5));};
        NeoForge.EVENT_BUS.addListener(callback);
        try{var child=f.spawn(f.origin);h.assertTrue(child!=null&&nested[0]==null&&calls[0]==1&&!WorldOutbreakSystem.populationRoom(f.level,f.origin.east(5)),"the unpublished parent owns the sole interval ticket before external callbacks can run another producer");}
        finally{NeoForge.EVENT_BUS.unregister(callback);}
    });}
    @GameTest(template="wall_test",timeoutTicks=240,batch="birth_pending_dimension")
    public static void pendingActualChildrenAlreadyOccupyTheDimensionPopulationCap(GameTestHelper h){var f=new Fixture(h);f.run(()->{
        f.leaveOneDimensionSlot();
        int[] calls={0};Mob[] nested={null};Consumer<EntityJoinLevelEvent> callback=e->{if(e.getLevel()==f.level&&e.getEntity() instanceof com.codex.blacklight.entity.BlacklightCommonInfected&&calls[0]++==0)nested[0]=f.spawn(f.origin.east(5));};
        NeoForge.EVENT_BUS.addListener(callback);
        try{h.assertTrue(f.spawn(f.origin)!=null&&nested[0]==null&&calls[0]==1,"a different producer sees the pending actual body even before it appears in getAllEntities");}
        finally{NeoForge.EVENT_BUS.unregister(callback);}
    });}
    @GameTest(template="wall_test",timeoutTicks=240,batch="birth_pending_local")
    public static void pendingBodiesOccupyTheirLocalAreaWithoutBlockingADistantLoadedDistrict(GameTestHelper h){var f=new Fixture(h);OutbreakConfig.LOCAL_CAP.set(1);var far=f.origin.east(224);f.floor(far);f.run(()->{
        int[] calls={0};Mob[] nearby={null},distant={null};Consumer<EntityJoinLevelEvent> callback=e->{if(e.getLevel()==f.level&&e.getEntity() instanceof com.codex.blacklight.entity.BlacklightCommonInfected&&calls[0]++==0){nearby[0]=f.spawn(f.origin.east(5));distant[0]=f.spawn(far);}};
        NeoForge.EVENT_BUS.addListener(callback);
        try{var parent=f.spawn(f.origin);h.assertTrue(parent!=null&&nearby[0]==null&&distant[0]!=null&&calls[0]==2,"pending bounding boxes close the nearby last slot while another real loaded district can still use its own local slot; parent="+(parent!=null)+" near="+(nearby[0]!=null)+" far="+(distant[0]!=null)+" callbacks="+calls[0]+" ticking="+f.level.isPositionEntityTicking(f.origin)+"/"+f.level.isPositionEntityTicking(far)+" farRoom="+WorldOutbreakSystem.populationRoom(f.level,far));}
        finally{NeoForge.EVENT_BUS.unregister(callback);}
    });}
    @GameTest(template="wall_test",timeoutTicks=240,batch="birth_pending_veto")
    public static void rejectedRealJoinsReleaseBothTheBirthTicketAndPopulationReservation(GameTestHelper h){var f=new Fixture(h);OutbreakConfig.BATCH.set(1);f.run(()->{
        f.leaveOneDimensionSlot();
        int[] vetoes={0};
        Consumer<EntityJoinLevelEvent> veto=e->{if(e.getLevel()==f.level&&e.getEntity() instanceof com.codex.blacklight.entity.BlacklightCommonInfected){vetoes[0]++;e.setCanceled(true);}};
        NeoForge.EVENT_BUS.addListener(veto);
        try{h.assertTrue(f.spawn(f.origin)==null&&f.spawn(f.origin.east(5))==null&&vetoes[0]==2&&WorldOutbreakSystem.populationRoom(f.level,f.origin),"actual canceled joins leave no phantom population or spent successful-birth allowance");}
        finally{NeoForge.EVENT_BUS.unregister(veto);}
        h.assertTrue(f.spawn(f.origin)!=null&&!WorldOutbreakSystem.populationRoom(f.level,f.origin.east(5)),"the released ticket can be accepted once in the same interval and is then spent");
    });}
    @GameTest(template="wall_test",timeoutTicks=240,batch="birth_pending_same_body")
    public static void theSameMobCannotReenterItsOwnUnfinishedAddition(GameTestHelper h){var f=new Fixture(h);f.run(()->{
        int[] calls={0};boolean[] nested={true};Consumer<EntityJoinLevelEvent> callback=e->{if(e.getLevel()==f.level&&e.getEntity() instanceof com.codex.blacklight.entity.BlacklightCommonInfected mob){calls[0]++;nested[0]=WorldOutbreakSystem.addScheduled(f.level,mob);}};
        NeoForge.EVENT_BUS.addListener(callback);
        try{var child=f.spawn(f.origin);h.assertTrue(child!=null&&!nested[0]&&calls[0]==1&&!WorldOutbreakSystem.addScheduled(f.level,child),"one real entity cannot recursively publish itself or consume two tickets after it already exists");}
        finally{NeoForge.EVENT_BUS.unregister(callback);}
    });}
    @GameTest(template="wall_test",timeoutTicks=300,batch="birth_pending_window")
    public static void acceptedBirthsReleaseOnlyTheirIntervalQuotaOnTheNextRealWindow(GameTestHelper h){var f=new Fixture(h);OutbreakConfig.BATCH.set(1);h.runAfterDelay(OutbreakConfig.INTERVAL.get()+5,()->{
        try{h.assertTrue(f.spawn(f.origin)!=null&&f.spawn(f.origin.east(5))==null,"the accepted child spends exactly the current window's birth quota");
            h.runAfterDelay(OutbreakConfig.INTERVAL.get()+1,()->{try{h.assertTrue(f.spawn(f.origin.east(5))!=null,"real server time opens the next window without a permanently held reservation");}finally{f.close();}h.succeed();});
        }catch(Throwable failure){f.close();throw failure;}
    });}
    @GameTest(template="wall_test",timeoutTicks=240,batch="birth_pending_unloaded")
    public static void theSharedPublicationEntryCannotGenerateAnUnloadedDestination(GameTestHelper h){var f=new Fixture(h);f.run(()->{
        var child=ModEntities.COMMON_INFECTED.get().create(f.level);child.setPos(2_000_000,100,2_000_000);int loaded=f.level.getChunkSource().getLoadedChunksCount();
        h.assertTrue(!WorldOutbreakSystem.addScheduled(f.level,child)&&loaded==f.level.getChunkSource().getLoadedChunksCount()&&WorldOutbreakSystem.populationRoom(f.level,f.origin),"an arbitrary unloaded publication attempt neither generates chunks nor spends a ticket");child.discard();
    });}
    @GameTest(template="wall_test",timeoutTicks=240,batch="birth_pending_join_exception")
    public static void aThrowingExternalJoinCallbackCannotLeaveAPermanentPendingSlot(GameTestHelper h){var f=new Fixture(h);OutbreakConfig.BATCH.set(1);f.run(()->{
        f.leaveOneDimensionSlot();
        Consumer<EntityJoinLevelEvent> callback=e->{if(e.getLevel()==f.level&&e.getEntity() instanceof com.codex.blacklight.entity.BlacklightCommonInfected)throw new IllegalStateException("expected isolated birth-reservation fixture exception");};
        NeoForge.EVENT_BUS.addListener(callback);boolean failed=false;
        try{f.spawn(f.origin);}catch(RuntimeException expected){failed=true;}finally{NeoForge.EVENT_BUS.unregister(callback);}
        boolean room=WorldOutbreakSystem.populationRoom(f.level,f.origin);var accepted=f.spawn(f.origin);
        h.assertTrue(failed&&room&&accepted!=null,"the finally path returns the failed publication's quota and actual population slot even when a listener throws; threw="+failed+" room="+room+" accepted="+accepted+" site="+f.level.getBlockState(f.origin));
    });}
    @GameTest(template="wall_test",timeoutTicks=240,batch="birth_pending_conversion_brood")
    public static void broodCannotUseTheSlotOwnedByAnActualNaturalConversion(GameTestHelper h){
        var f=new Fixture(h);f.run(()->{
            h.assertTrue(f.spawn(f.origin)!=null,"a local infected source is present");
            var cow=EntityType.COW.create(f.level);f.ownedMobs.add(cow);cow.setPos(f.origin.east(3).getBottomCenter());
            h.assertTrue(f.level.addFreshEntity(cow),"the susceptible resident is registered");
            f.leaveOneDimensionSlot();
            var brood=ModEntities.COMMON_INFECTED.get().create(f.level);f.ownedMobs.add(brood);brood.setPos(f.origin.east(6).getBottomCenter());
            int[] callbacks={0};
            Consumer<NaturalInfectionEvent> callback=event->{
                if(event.getEntity()!=cow)return;
                callbacks[0]++;
                h.assertTrue(!WorldOutbreakSystem.addBrood(f.level,brood),"a reentrant hive cannot take the converting resident's last slot");
                h.assertTrue(WorldOutbreakSystem.cityPopulationSlots(f.level,true)==0,"the population planner sees the same reserved infected slot");
            };
            NeoForge.EVENT_BUS.addListener(callback);
            try{
                h.assertTrue(BlacklightInfectionSystem.infectNaturally(cow,null)&&callbacks[0]==1,"the real natural conversion commits exactly once");
                h.assertTrue(f.level.getEntity(brood.getUUID())==null&&!WorldOutbreakSystem.broodRoom(f.level,f.origin),
                        "the original conversion fills the cap without also publishing a guard");
            }finally{NeoForge.EVENT_BUS.unregister(callback);}
        });
    }
    private static void encounter(GameTestHelper h,boolean hiveFirst){var f=new Fixture(h);OutbreakConfig.BATCH.set(1);h.runAfterDelay(OutbreakConfig.INTERVAL.get()+5,()->{
        for(int x=-15;x<=15;x++)for(int z=-15;z<=15;z++){var p=f.origin.offset(x,-1,z);f.blocks.putIfAbsent(p,f.level.getBlockState(p));f.level.setBlock(p,Blocks.STONE.defaultBlockState(),2);}
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(f.level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BirthHiveQA"));player.setGameMode(GameType.SURVIVAL);player.setPos(f.origin.east(3).getBottomCenter());f.level.addNewPlayer(player);
        var hive=ModEntities.BLACKLIGHT_HIVE.get().create(f.level);f.ownedMobs.add(hive);hive.setPos(f.origin.getBottomCenter());var tag=new net.minecraft.nbt.CompoundTag();hive.saveWithoutId(tag);tag.putInt("BlacklightWaveCooldown",0);tag.putFloat("BlacklightGrowth",1F);tag.putString("BlacklightBrood","common");hive.load(tag);f.level.addFreshEntity(hive);
        int[] calls={0};Mob[] nested={null};
        Consumer<EntityJoinLevelEvent> callback=e->{
            if(e.getLevel()!=f.level||!(e.getEntity() instanceof com.codex.blacklight.entity.BlacklightCommonInfected child))return;
            var data=child.getPersistentData();
            boolean ownGuard=data.hasUUID("BlacklightHiveGuard")&&hive.getUUID().equals(data.getUUID("BlacklightHiveGuard"));
            if(ownGuard)f.ownedMobs.add(child);
            if(!ownGuard&&!child.blockPosition().equals(f.origin.east(5)))return;
            if(++calls[0]!=1)return;
            if(hiveFirst)nested[0]=f.spawn(f.origin.east(5));
            else{hive.tickCount=20;hive.tick();}
        };
        NeoForge.EVENT_BUS.addListener(callback);
        Runnable cleanup=()->{NeoForge.EVENT_BUS.unregister(callback);f.level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);f.close();};
        try{
            if(hiveFirst){
                hive.tickCount=20;hive.tick();
                h.assertTrue(hive.hatchAge(0)==0&&hive.waves()==0,"a ripe hive opens before its guard is released");
            }else{
                h.assertTrue(f.spawn(f.origin.east(5))!=null,"the actual ambient producer owns the first ticket");
                h.assertTrue(hive.hatchAge(0)>com.codex.blacklight.entity.BlacklightHive.RELEASE_TICK,
                        "the reentrant hive cannot even begin a hatch while the ambient join owns the slot");
            }
            h.runAfterDelay(com.codex.blacklight.entity.BlacklightHive.RELEASE_TICK+2,()->{
                try{
                    var guards=f.level.getEntitiesOfClass(Mob.class,hive.getBoundingBox().inflate(24),m->m.getPersistentData().hasUUID("BlacklightHiveGuard")&&m.getPersistentData().getUUID("BlacklightHiveGuard").equals(hive.getUUID()));
                    h.assertTrue(calls[0]==1&&nested[0]==null&&guards.size()==(hiveFirst?1:0)&&hive.waves()==(hiveFirst?1:0),
                            "one producer publishes across the actual release frame; hiveFirst="+hiveFirst+" callbacks="+calls[0]+" guards="+guards.size()+" waves="+hive.waves());
                }finally{cleanup.run();}
                h.succeed();
            });
        }catch(RuntimeException|Error failure){cleanup.run();throw failure;}
    });}
    @GameTest(template="wall_test",timeoutTicks=240,batch="birth_pending_encounter")
    public static void aRealEncounterHivesGuardReservesTheTicketBeforeAnAmbientCallback(GameTestHelper h){encounter(h,true);}
    @GameTest(template="wall_test",timeoutTicks=240,batch="birth_pending_encounter_reverse")
    public static void aRealEncounterHiveCannotReenterAnAmbientProducersLastTicket(GameTestHelper h){encounter(h,false);}
}
