package com.codex.blacklight.city;

import com.codex.blacklight.infection.*;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.registry.ModEntities;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class CityHiveSupplyGameTest {
    @GameTest(template="wall_test",timeoutTicks=170,batch="audit_city_brood_removed")
    public static void aPortDestroyedDuringBirthCannotCommitOrCrashItsProducer(GameTestHelper h){
        var f=new Fixture(h);h.runAfterDelay(100,()->{
            f.stock().receive(40);f.tick();f.data.hiveBroodTicks[0]=200;int before=f.stock().reserve();Mob[] published={null};
            java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> callback=e->{
                if(e.getLevel()==f.level&&e.getEntity() instanceof com.codex.blacklight.entity.BlacklightCommonInfected mob){
                    published[0]=mob;f.set(f.port(0),Blocks.AIR.defaultBlockState());f.data.destroyedMask|=1;
                }
            };
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(callback);
            try{
                var child=CityHiveSupply.hatch(f.level,f.data,0,f.port(0),OutbreakRules.Tier.FRESH);
                h.assertTrue(published[0]!=null,"a real birth reaches the external join listener");
                h.assertTrue(child==null&&published[0].isRemoved()&&f.data.hiveBirths[0]==0&&f.stock().reserve()==before,
                        "destroyed producer rolls back its newborn and material before reopening a nonexistent hatch");
            }finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(callback);f.close();}
            h.succeed();
        });
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final ServerLevel level;final CityData data,old;final OutbreakData outbreak;final FakePlayer player,observer;
        final boolean spawning,grief,enabled,citySpawns;final net.minecraft.world.Difficulty difficulty;
        final Map<BlockPos,BlockState> changed=new LinkedHashMap<>();final Set<ChunkPos> forced=new LinkedHashSet<>();final Set<Integer> originalEntities=new HashSet<>();
        boolean closed;
        final java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> joinTrace;
        Fixture(GameTestHelper helper){
            h=helper;level=h.getLevel().getServer().getLevel(CityTravel.WORLD);if(level==null)throw new AssertionError("the real blacklight world dimension is missing");
            // This opt-in component fixture needs a known empty population scene.
            // Earlier whole-world tests may legitimately create city residents.
            // Reset only the disposable sprinting GameTest server, never a live
            // integrated/dedicated world. Density tests add their real actors below.
            if(level.getServer() instanceof GameTestServer){
                var previous=new java.util.ArrayList<Mob>();for(var entity:level.getAllEntities())if(entity instanceof Mob mob)previous.add(mob);
                previous.forEach(Mob::discard);
            }
            for(var e:level.getAllEntities())originalEntities.add(e.getId());old=CityData.get(level);data=new CityData();data.population=true;data.populatedMask=-1;data.spread=false;data.facade=false;data.destroyedMask=2;level.getDataStorage().set("blacklight_city",data);
            // Forge restores entity sections asynchronously after chunks become
            // available. Clearing only getAllEntities above misses old bodies
            // still in that queue. Keep this component fixture empty of earlier
            // batches, while every genuine new birth still uses the real caps
            // and all external join callbacks.
            joinTrace=e->{if(e.getLevel()==level&&e.getEntity() instanceof Mob&&e.loadedFromDisk()
                    &&level.getServer() instanceof GameTestServer)e.setCanceled(true);};
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(joinTrace);
            outbreak=OutbreakData.get(level.getServer());var active=new OutbreakData();active.start(level.getServer().overworld().getGameTime());level.getServer().overworld().getDataStorage().set("blacklight_outbreak",active);
            spawning=level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);grief=level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);enabled=OutbreakConfig.ENABLED.get();citySpawns=CityConfig.SPAWNS.get();difficulty=level.getDifficulty();
            OutbreakConfig.ENABLED.set(true);CityConfig.SPAWNS.set(true);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,level.getServer());level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,level.getServer());
            for(int i=0;i<7;i++){
                var p=port(i);var cp=new ChunkPos(p);for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)forced.add(new ChunkPos(cp.x+dx,cp.z+dz));level.getChunkAt(p);
                h.assertTrue(level.getBlockState(p).is(CityBlocks.HATCH.get()),"real generated world-map hive port missing at "+p);
                var outside=outside(i);for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
                    set(outside.offset(x,-1,z),Blocks.STONE.defaultBlockState());for(int y=0;y<8;y++)set(outside.offset(x,y,z),Blocks.AIR.defaultBlockState());
                }
            }
            for(var cp:forced)level.setChunkForced(cp.x,cp.z,true);
            // These component tests own their manual 20-tick clock. The observer supplies
            // real chunk simulation, but must not also start a second production scheduler.
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(CitySystem.class);
            observer=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"CitySupplyObserver"));observer.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);observer.setPos(port(0).getCenter().add(0,8,0));level.addNewPlayer(observer);
            player=FakePlayerFactory.get(level,new GameProfile(UUID.fromString("22ad1749-c8c4-4b7c-a80e-3856e323acbe"),"CitySupplyQA"));player.getAbilities().instabuild=false;face(0);
            com.codex.blacklight.gametest.ForgeTestLifecycle.info(h).addListener(new GameTestListener(){
                public void testStructureLoaded(GameTestInfo info){}
                public void testPassed(GameTestInfo info){close();}
                public void testFailed(GameTestInfo info){close();}
                public void testAddedForRerun(GameTestInfo original,GameTestInfo retry){}
            });
        }
        BlockPos port(int i){return CitySystem.port(level,0,i);}
        BlockPos outside(int i){var p=port(i);return p.relative(level.getBlockState(p).getValue(CityHatchBlock.FACING),3).below();}
        void face(int i){var p=outside(i);com.codex.blacklight.gametest.ForgeTestPositions.moveTo(player,net.minecraft.world.phys.Vec3.atBottomCenterOf(p),0,0);}
        void set(BlockPos p,BlockState state){changed.putIfAbsent(p.immutable(),level.getBlockState(p));level.setBlock(p,state,3);}
        ColonySupply stock(){return data.hiveSupply[0];}
        boolean feed(ItemStack stack){face(0);player.setItemInHand(InteractionHand.MAIN_HAND,stack);return CityHiveSupply.feed(level,port(0),player,InteractionHand.MAIN_HAND);}
        void tick(){CityHiveSupply.tick(level,data,0);}
        String birthStatus(){
            int total=0,infected=0,local=0;var area=new AABB(outside(0)).inflate(96);
            for(var e:level.getAllEntities())if(e instanceof Mob mob&&mob.isAlive()){
                total++;if(BlacklightInfectionSystem.isInfected(mob)){infected++;if(mob.getBoundingBox().intersects(area))local++;}
            }
            return "active="+WorldOutbreakSystem.active(level)+" difficulty="+level.getDifficulty()+" spawning="+level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                    +" sanctuary="+WorldOutbreakSystem.sanctuary(level,outside(0))+" citySpawns="+CityConfig.SPAWNS.get()+" population="+CityData.get(level).population
                    +" total="+total+" infected="+infected+" dimensionCap="+OutbreakConfig.DIMENSION_CAP.get()+" residentCap="+CityConfig.MOB_LIMIT.get()
                    +" local="+local+" localCap="+CityEcology.populationCap(level,outside(0))+" batch="+OutbreakConfig.BATCH.get();
        }
        void schedule(int from,int through){for(int t=from;t<=through;t+=20)h.runAfterDelay(t,this::tick);}
        BlacklightInfectedHost carrier(){
            var host=ModEntities.INFECTED_HOST.get().create(level);long id=host.getId();host.setUUID(new UUID(id,id^42));host.inherit(EntityType.COW.create(level));var tag=new CompoundTag();host.saveWithoutId(tag);tag.getCompound("BlacklightHost").putString("NativeCore",TissueCore.VASCULAR.key());tag.putInt("BlacklightTissueNutrition",400);host.readAdditionalSaveData(tag);host.advanceTissue(216000);host.setHealth(host.getMaxHealth());host.setNoAi(false);host.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);com.codex.blacklight.gametest.ForgeTestPositions.moveTo(host,net.minecraft.world.phys.Vec3.atBottomCenterOf(outside(0)),0,0);host.setOnGround(true);level.addFreshEntity(host);return host;
        }
        public void close(){
            if(closed)return;closed=true;
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(joinTrace);
            level.removePlayerImmediately(observer,Entity.RemovalReason.DISCARDED);
            var cleanup=new java.util.ArrayList<Entity>();level.getAllEntities().forEach(cleanup::add);for(var e:cleanup)if((e instanceof Mob||e instanceof ItemEntity)&&!originalEntities.contains(e.getId()))e.discard();
            for(var entry:changed.entrySet())level.setBlock(entry.getKey(),entry.getValue(),3);
            for(var cp:forced)level.setChunkForced(cp.x,cp.z,false);
            level.getDataStorage().set("blacklight_city",old);level.getServer().overworld().getDataStorage().set("blacklight_outbreak",outbreak);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawning,level.getServer());level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(grief,level.getServer());OutbreakConfig.ENABLED.set(enabled);CityConfig.SPAWNS.set(citySpawns);level.getServer().setDifficulty(difficulty,true);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(CitySystem.class);
        }
    }
    @GameTest(template="wall_test",timeoutTicks=420,batch="city_supply_empty")
    public static void anUnfedAuthoredHiveCannotGenerateBroodFromLoadedTime(GameTestHelper h){var f=new Fixture(h);f.schedule(100,340);h.runAfterDelay(360,()->{try{h.assertTrue(f.stock().reserve()==0&&f.stock().queued()==0&&f.data.hiveLoadedTicks[0]==0&&f.data.hiveBroodTicks[0]==0&&f.data.hiveBirths[0]==0,"loaded time without actual food cannot mature or produce a free child");h.assertTrue(CityHiveSupply.hatch(f.level,f.data,0,f.port(0),OutbreakRules.Tier.FRESH)==null,"the old authored producer cannot bypass its empty resource chain");}finally{f.close();}h.succeed();});}
    @GameTest(template="wall_test",timeoutTicks=820,batch="city_supply_actual_food")
    public static void oneRealMealDigestsThenPaysForOneAppropriateChildAndStopsWhenEmpty(GameTestHelper h){
        var f=new Fixture(h);var meal=com.codex.blacklight.gametest.ForgeTestFood.stack(1,7,true);
        h.runAfterDelay(90,()->{h.assertTrue(f.feed(meal)&&meal.isEmpty()&&f.stock().queued()==1&&f.stock().reserve()==0,"one real survival food stack enters the shared stomach without instant nutrient credit");});f.schedule(100,760);
        h.runAfterDelay(230,()->h.assertTrue(f.stock().reserve()==0&&!CityHiveSupply.ready(f.data,0),"partially digested food cannot fund premature brood"));
        h.runAfterDelay(510,()->{int before=f.stock().reserve();var child=CityHiveSupply.hatch(f.level,f.data,0,f.port(0),OutbreakRules.Tier.FRESH);h.assertTrue(child instanceof com.codex.blacklight.entity.BlacklightCommonInfected&&before-f.stock().reserve()==12&&f.data.hiveBirths[0]==1,"one actual early-stage child consumes exactly its shared twelve nutrient cost");child.setNoAi(true);h.assertTrue(CityHiveSupply.hatch(f.level,f.data,0,f.port(1),OutbreakRules.Tier.FRESH)==null,"a second mouth cannot immediately replay the same organism's brood work");});
        h.runAfterDelay(780,()->{try{h.assertTrue(f.stock().reserve()==0&&f.data.hiveBirths[0]==1&&!CityHiveSupply.ready(f.data,0),"the remaining real nutrients pay maintenance and empty reserves stop new production");}finally{f.close();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=170,batch="city_supply_container")
    public static void aHiveReturnsTheRealFoodContainerAndRejectsUnregisteredHatches(GameTestHelper h){var f=new Fixture(h);h.runAfterDelay(100,()->{try{
        var soup=new ItemStack(Items.MUSHROOM_STEW);h.assertTrue(f.feed(soup)&&f.player.getMainHandItem().is(Items.BOWL)&&f.stock().queued()==1,"the survival inventory receives the actual bowl");
        var extra=f.outside(0).above(4);f.set(extra,CityBlocks.HATCH.get().defaultBlockState());com.codex.blacklight.gametest.ForgeTestPositions.moveTo(f.player,net.minecraft.world.phys.Vec3.atBottomCenterOf(extra).add(0,0,-2),0,0);f.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BEEF));
        h.assertTrue(!CityHiveSupply.feed(f.level,extra,f.player,InteractionHand.MAIN_HAND)&&f.player.getMainHandItem().getCount()==1&&f.stock().queued()==1,"a decorative or player-added hatch cannot impersonate a registered authored organism");
    }finally{f.close();}h.succeed();});}
    @GameTest(template="wall_test",timeoutTicks=170,batch="city_supply_wall")
    public static void authoredHiveMouthsCannotConsumeProtectedDropsOrHandsThroughARealWall(GameTestHelper h){var f=new Fixture(h);h.runAfterDelay(100,()->{try{
        var p=f.port(0);var facing=f.level.getBlockState(p).getValue(CityHatchBlock.FACING);var wall=p.relative(facing);for(int y=-1;y<=2;y++)f.set(wall.above(y),Blocks.GLASS.defaultBlockState());
        var point=Vec3.atCenterOf(p.relative(facing,2));var item=new ItemEntity(f.level,point.x,point.y,point.z,new ItemStack(Items.BEEF,2));item.setNoGravity(true);item.setDeltaMovement(Vec3.ZERO);f.level.addFreshEntity(item);
        var food=new ItemStack(Items.BEEF);h.assertTrue(!f.feed(food)&&food.getCount()==1,"the real wall blocks hand feeding without consuming the slot");
        ColonyFeeding.scavenge(f.level,p,f.stock(),0);h.assertTrue(item.getItem().getCount()==2&&f.stock().queued()==0&&f.stock().reserve()==0,"the common local scavenging path cannot swallow the protected stack through glass");
    }finally{f.close();}h.succeed();});}
    @GameTest(template="wall_test",timeoutTicks=170,batch="city_supply_shared")
    public static void sevenMouthsUseOneReserveAndCannotRepeatDigestionOrBroodWithinTheSameTick(GameTestHelper h){var f=new Fixture(h);h.runAfterDelay(100,()->{try{
        f.stock().offer(TissueDigestion.Kind.MEAT,20);f.stock().receive(40);f.tick();var state=f.stock().write();f.tick();h.assertTrue(f.stock().write().equals(state)&&f.data.hiveLoadedTicks[0]==20&&f.data.hiveSustained[0],"duplicate same-tick updates neither advance digestion nor erase the first valid supply decision");
        for(int i=0;i<7;i++)h.assertTrue(CityHiveSupply.supply(f.level,f.port(i))==f.stock()&&ColonyNutrients.resolve(f.level,f.port(i)).stock()==f.stock(),"every live port resolves to the very same store");
        f.data.hiveBroodTicks[0]=200;int before=f.stock().reserve();h.assertTrue(CityHiveSupply.hatch(f.level,f.data,0,f.port(0),OutbreakRules.Tier.FRESH)!=null,"the one funded brood is real");
        for(int i=1;i<7;i++)h.assertTrue(CityHiveSupply.hatch(f.level,f.data,0,f.port(i),OutbreakRules.Tier.FRESH)==null,"the remaining six mouths cannot duplicate brood work");h.assertTrue(before-f.stock().reserve()==12&&f.data.hiveBirths[0]==1,"the single material commit is conserved");
    }finally{f.close();}h.succeed();});}
    @GameTest(template="wall_test",timeoutTicks=170,batch="city_supply_refund")
    public static void aBlockedCityBirthRefundsBothItsRealNutrientsAndItsLoadedBroodWork(GameTestHelper h){var f=new Fixture(h);h.runAfterDelay(100,()->{try{
        f.stock().receive(40);f.tick();f.data.hiveBroodTicks[0]=200;var p=f.outside(0);f.set(p,Blocks.STONE.defaultBlockState());int before=f.stock().reserve();
        h.assertTrue(CityHiveSupply.hatch(f.level,f.data,0,f.port(0),OutbreakRules.Tier.FRESH)==null&&f.stock().reserve()==before&&f.data.hiveBroodTicks[0]==200&&f.data.hiveBirths[0]==0,"a real collision rejection restores the reserved material and ready brood");
        f.set(p,Blocks.AIR.defaultBlockState());h.assertTrue(CityHiveSupply.hatch(f.level,f.data,0,f.port(0),OutbreakRules.Tier.FRESH)!=null&&f.stock().reserve()==before-12,"opening the actual outlet allows exactly one paid child");
    }finally{f.close();}h.succeed();});}
    @GameTest(template="wall_test",timeoutTicks=170,batch="city_supply_cleared")
    public static void aClearedAuthoredHiveCannotBeRefedOrTransportedIntoByReplacingItsSevenBlocks(GameTestHelper h){var f=new Fixture(h);h.runAfterDelay(100,()->{try{
        f.stock().receive(40);for(int i=0;i<7;i++)f.set(f.port(i),Blocks.AIR.defaultBlockState());CitySystem.updateLoadedCity(f.level,220);h.assertTrue(f.data.destroyed(0)&&f.data.brokenPorts[0]==127,"the actual seven missing mouths clear the organism");
        for(int i=0;i<7;i++)f.set(f.port(i),FutianLayout.state(f.level.getSeed(),f.port(i).getX(),f.port(i).getY(),f.port(i).getZ()));
        var food=new ItemStack(Items.BEEF);h.assertTrue(!f.feed(food)&&food.getCount()==1&&ColonyNutrients.resolve(f.level,f.port(0))==null,"replacing a block cannot erase clearing history or accept a remote gift");
        h.assertTrue(CityHiveSupply.hatch(f.level,f.data,0,f.port(0),OutbreakRules.Tier.FRESH)==null&&f.stock().reserve()==40,"cleared production remains stopped with saved material unchanged");
    }finally{f.close();}h.succeed();});}
    @GameTest(template="wall_test",timeoutTicks=180,batch="city_supply_carrier")
    public static void aRealMatureCarrierTransfersItsOwnExistingNutritionIntoTheAuthoredHive(GameTestHelper h){var f=new Fixture(h);var carrier=new BlacklightInfectedHost[1];h.runAfterDelay(100,()->{carrier[0]=f.carrier();h.assertTrue(carrier[0].vascular().startAt(carrier[0],f.port(0)),"the mature living organ connects to an actual authored mouth");});h.runAfterDelay(118,()->{var c=carrier[0];int income=c.vascular().delivered();h.assertTrue(income>0&&f.stock().reserve()==income&&c.tissueNutrition()+income==398&&f.stock().queued()==0,"the one conserved gift enters the hive store rather than inventing a food portion or a parallel core");});h.runAfterDelay(148,()->{try{h.assertTrue(!carrier[0].vascular().active()&&f.stock().reserve()==carrier[0].vascular().delivered(),"the living duct retracts without replaying its committed gift");}finally{f.close();}h.succeed();});}
    @GameTest(template="wall_test",timeoutTicks=170,batch="city_supply_carrier_cut")
    public static void removingTheAuthoredMouthCancelsAnExtendingCarrierBeforeItCanPayADeadStore(GameTestHelper h){var f=new Fixture(h);var carrier=new BlacklightInfectedHost[1];h.runAfterDelay(100,()->{carrier[0]=f.carrier();h.assertTrue(carrier[0].vascular().startAt(carrier[0],f.port(0)),"the actual connection begins before the cut");});h.runAfterDelay(106,()->f.set(f.port(0),Blocks.AIR.defaultBlockState()));h.runAfterDelay(124,()->{try{h.assertTrue(!carrier[0].vascular().active()&&carrier[0].vascularVisual().isEmpty()&&f.stock().reserve()==0&&carrier[0].tissueNutrition()<=398,"a removed real mouth immediately invalidates transport and retracts without crediting a stale hive");}finally{f.close();}h.succeed();});}
    @GameTest(template="wall_test",timeoutTicks=400,batch="city_supply_join_reentrant")
    public static void anActualJoinCallbackCannotClaimTheSameHiveBroodThroughAnotherMouth(GameTestHelper h){var f=new Fixture(h);h.startSequence().thenIdle(100).thenWaitUntil(()->{
        // The shared dimension can still have birth tickets spent by the preceding
        // batch, and forced chunks enter entity ticking asynchronously. This test
        // exercises join reentry, so wait for a legal first birth without bypassing
        // either production guard or weakening the callback/material assertions.
        h.assertTrue(CityHiveSupply.live(f.level,f.data,CityHiveSupply.port(f.level,f.port(0))),"waiting for the actual hive mouth to enter entity ticking");
        h.assertTrue(WorldOutbreakSystem.populationRoom(f.level,f.outside(0)),"waiting for a real shared population/birth slot: "+f.birthStatus());
    }).thenExecute(()->{
        f.stock().receive(40);f.tick();f.data.hiveBroodTicks[0]=200;int before=f.stock().reserve();var callbacks=new int[1];var replay=new Mob[1];
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> callback=e->{if(e.getLevel()==f.level&&e.getEntity() instanceof com.codex.blacklight.entity.BlacklightCommonInfected){callbacks[0]++;replay[0]=CityHiveSupply.hatch(f.level,f.data,0,f.port(1),OutbreakRules.Tier.FRESH);}};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(callback);
        try{var child=CityHiveSupply.hatch(f.level,f.data,0,f.port(0),OutbreakRules.Tier.FRESH);h.assertTrue(child!=null&&callbacks[0]==1&&replay[0]==null&&f.data.hiveBirths[0]==1&&f.stock().reserve()==before-12,"a real external join listener cannot replay reserved work or duplicate its material commit: child="+child+" callbacks="+callbacks[0]+" replay="+replay[0]+" births="+f.data.hiveBirths[0]+" reserve="+f.stock().reserve()+" before="+before+" brood="+f.data.hiveBroodTicks[0]);}
        finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(callback);f.close();}h.succeed();
    });}
    @GameTest(template="wall_test",timeoutTicks=170,batch="city_supply_join_veto")
    public static void anExternalJoinVetoRefundsTheCityHivesRealMaterialAndBroodReservation(GameTestHelper h){var f=new Fixture(h);h.runAfterDelay(100,()->{
        f.stock().receive(40);f.tick();f.data.hiveBroodTicks[0]=200;int before=f.stock().reserve();
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> veto=e->{if(e.getLevel()==f.level&&e.getEntity() instanceof com.codex.blacklight.entity.BlacklightCommonInfected)e.setCanceled(true);};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(veto);
        try{h.assertTrue(CityHiveSupply.hatch(f.level,f.data,0,f.port(0),OutbreakRules.Tier.FRESH)==null&&f.stock().reserve()==before&&f.data.hiveBroodTicks[0]==200&&f.data.hiveBirths[0]==0,"a true join cancellation refunds both paid inputs and cannot count a nonexistent child");}
        finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(veto);f.close();}h.succeed();
    });}
    @GameTest(template="wall_test",timeoutTicks=240,batch="city_supply_global_ticket")
    public static void anotherProducerCannotSpendTheLastBirthTicketInsideAnActualWorldSpawnCallback(GameTestHelper h){var f=new Fixture(h);int batch=OutbreakConfig.BATCH.get();OutbreakConfig.BATCH.set(1);h.runAfterDelay(100,()->{
        f.stock().receive(40);f.tick();f.data.hiveBroodTicks[0]=200;int before=f.stock().reserve();Mob[] nested={null};int[] calls={0};
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> callback=e->{if(e.getLevel()==f.level&&e.getEntity() instanceof com.codex.blacklight.entity.BlacklightCommonInfected&&calls[0]++==0)nested[0]=CityHiveSupply.hatch(f.level,f.data,0,f.port(1),OutbreakRules.Tier.FRESH);};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(callback);
        try{var child=WorldOutbreakSystem.spawnAt(f.level,f.outside(0),OutbreakRules.Tier.FRESH);h.assertTrue(child!=null&&calls[0]==1&&nested[0]==null&&f.stock().reserve()==before&&f.data.hiveBroodTicks[0]==200&&f.data.hiveBirths[0]==0,"a real ambient entry reserves the shared ticket before another actual paid city producer can reenter; the rejected hive keeps its food and brood");}
        finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(callback);OutbreakConfig.BATCH.set(batch);f.close();}h.succeed();
    });}
    @GameTest(template="wall_test",timeoutTicks=240,batch="city_supply_resident_ticket")
    public static void cityResidentsAlsoReserveTheSamePendingAllMobPopulationSlot(GameTestHelper h){var f=new Fixture(h);int limit=CityConfig.MOB_LIMIT.get();h.runAfterDelay(100,()->{
        int count=0;for(var e:f.level.getAllEntities())if(e instanceof Mob m&&m.isAlive())count++;CityConfig.MOB_LIMIT.set(count+1);
        var parent=EntityType.COW.create(f.level);parent.setNoAi(true);parent.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(f.outside(0)));var child=EntityType.COW.create(f.level);child.setNoAi(true);child.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(f.outside(1)));
        int[] calls={0};boolean[] nested={true};java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> callback=e->{if(e.getLevel()==f.level&&e.getEntity()==parent){calls[0]++;nested[0]=WorldOutbreakSystem.addScheduled(f.level,child);}};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(callback);
        try{h.assertTrue(WorldOutbreakSystem.addScheduled(f.level,parent)&&calls[0]==1&&!nested[0]&&WorldOutbreakSystem.populationRoom(f.level,f.outside(1)),"an unpublished resident reserves its own pool without stealing the independent infected pool");}
        finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(callback);if(!child.isRemoved())child.discard();CityConfig.MOB_LIMIT.set(limit);f.close();}h.succeed();
    });}
    @GameTest(template="wall_test",timeoutTicks=240,batch="density_independent_pools")
    public static void aFullResidentPoolAllowsRealInfectedBirthsAndReservesBothPoolsDuringJoins(GameTestHelper h){var f=new Fixture(h);int limit=CityConfig.MOB_LIMIT.get(),cap=OutbreakConfig.DIMENSION_CAP.get();h.runAfterDelay(100,()->{
        CityConfig.MOB_LIMIT.set(1);OutbreakConfig.DIMENSION_CAP.set(2);
        var resident=EntityType.COW.create(f.level);resident.setNoAi(true);resident.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(f.outside(0)));Mob[] nested={null};
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> callback=e->{if(e.getLevel()==f.level&&e.getEntity()==resident)nested[0]=WorldOutbreakSystem.spawnAt(f.level,f.outside(1),OutbreakRules.Tier.FRESH);};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(callback);
        try{
            h.assertTrue(WorldOutbreakSystem.addScheduled(f.level,resident)&&nested[0]!=null,"a real infected producer can use its own slot inside the final resident join callback");
            h.assertTrue(!CitySystem.hasPopulationRoom(f.level,DistrictThreat.Zone.GREEN)&&CitySystem.hasPopulationRoom(f.level,DistrictThreat.Zone.RED),"full green resident districts are skipped while red districts retain their own infection slot");
            var extraResident=EntityType.COW.create(f.level);extraResident.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(f.outside(2)));h.assertTrue(!WorldOutbreakSystem.addScheduled(f.level,extraResident),"a full resident pool cannot borrow infection slots");
            h.assertTrue(WorldOutbreakSystem.spawnAt(f.level,f.outside(2),OutbreakRules.Tier.FRESH)!=null,"the second real infection slot is independent of the full resident pool");
            h.assertTrue(WorldOutbreakSystem.spawnAt(f.level,f.outside(3),OutbreakRules.Tier.FRESH)==null,"the independent infection cap is still enforced");
        }finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(callback);CityConfig.MOB_LIMIT.set(limit);OutbreakConfig.DIMENSION_CAP.set(cap);f.close();}h.succeed();
    });}
}
