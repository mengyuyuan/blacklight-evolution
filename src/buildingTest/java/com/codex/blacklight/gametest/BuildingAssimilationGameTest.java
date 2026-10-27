package com.codex.blacklight.gametest;

import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModBlocks;
import com.codex.blacklight.world.TissueAttachmentBlock;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class BuildingAssimilationGameTest {
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final ServerLevel level;final BuildingInfectionData data,old;final BuildingInfectionData.Colony colony;final BlockPos origin;
        final OutbreakData outbreak;final com.codex.blacklight.city.CityEcologyData ecology;
        final com.codex.blacklight.city.CityData city;
        final boolean grief,spawns,enabled;final int work;final Map<BlockPos,BlockState> terrain=new LinkedHashMap<>();final Set<ChunkPos> forced=new HashSet<>();
        final List<net.minecraft.world.entity.Entity> born=new ArrayList<>();
        net.neoforged.neoforge.common.util.FakePlayer observer;boolean closed;
        Fixture(GameTestHelper h,ServerLevel target){
            this.h=h;level=Objects.requireNonNull(target,"actual test dimension missing");var base=h.absolutePos(new BlockPos(5,100,5));origin=level==h.getLevel()?new BlockPos((base.getX()>>4)*16+8,base.getY(),(base.getZ()>>4)*16+8):new BlockPos(level.dimension().equals(com.codex.blacklight.city.CityTravel.WORLD)?1_000_008:520,80,520);
            old=BuildingInfectionData.get(level);data=new BuildingInfectionData();level.getDataStorage().set("blacklight_building_infection",data);
            if(com.codex.blacklight.city.CityTravel.isCity(level.dimension())){city=com.codex.blacklight.city.CityData.get(level);var isolated=new com.codex.blacklight.city.CityData();isolated.destroyedMask=3;isolated.nextSpread=Long.MAX_VALUE;isolated.populatedMask=-1;isolated.facade=false;level.getDataStorage().set("blacklight_city",isolated);}else city=null;
            ecology=com.codex.blacklight.city.CityEcologyData.get(level);level.getDataStorage().set("blacklight_city_ecology",new com.codex.blacklight.city.CityEcologyData());
            outbreak=OutbreakData.get(level.getServer());var active=new OutbreakData();active.start(level.getServer().overworld().getGameTime());level.getServer().overworld().getDataStorage().set("blacklight_outbreak",active);
            grief=level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);spawns=level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);enabled=BuildingInfectionConfig.ASSIMILATE.get();work=BuildingInfectionConfig.WORK.get();
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());BuildingInfectionConfig.ASSIMILATE.set(true);
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var cp=new ChunkPos((origin.getX()>>4)+x,(origin.getZ()>>4)+z);if(!level.getForcedChunks().contains(cp.toLong())){forced.add(cp);level.setChunkForced(cp.x,cp.z,true);}level.getChunk(cp.x,cp.z);}
            if(level!=h.getLevel()){
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)level.getChunk((origin.getX()>>4)+x,(origin.getZ()>>4)+z);
                observer=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ArchitectureQA"));observer.setGameMode(GameType.SPECTATOR);observer.setPos(origin.getCenter().add(0,8,-3));level.addNewPlayer(observer);
            }
            for(int x=-10;x<=10;x++)for(int z=-4;z<=2;z++)for(int y=-1;y<=26;y++)block(origin.offset(x,y,z),y==-1?Blocks.STONE.defaultBlockState():z==1?Blocks.STONE_BRICKS.defaultBlockState():Blocks.AIR.defaultBlockState());
            block(origin,ModBlocks.BUILDING_CORE.get().defaultBlockState());h.assertTrue(data.colonies.size()==1,"actual supported architectural core registered in "+level.dimension().location());colony=data.colonies.getFirst();colony.stage=1;
        }
        void block(BlockPos pos,BlockState state){terrain.putIfAbsent(pos.immutable(),level.getBlockState(pos));level.setBlock(pos,state,3);}
        BlockPos contact(int x,BlockState original){var pos=origin.offset(x,3,1);block(pos,original);var patch=pos.north();block(patch,ModBlocks.TISSUE_ATTACHMENT.get().defaultBlockState().setValue(TissueAttachmentBlock.FACING,Direction.SOUTH).setValue(TissueAttachmentBlock.AGE,1));colony.surfaces.add(patch.asLong());return pos;}
        void run(Runnable check){h.startSequence().thenWaitUntil(()->h.assertTrue(level.isPositionEntityTicking(origin)&&level.isPositionEntityTicking(origin.north(2)),"waiting for actual dimension/root/outlet activation; dimension="+level.dimension().location()+" root="+level.isPositionEntityTicking(origin)+" outlet="+level.isPositionEntityTicking(origin.north(2))+" forcedRoot="+level.getForcedChunks().contains(new ChunkPos(origin).toLong())+" forcedOutlet="+level.getForcedChunks().contains(new ChunkPos(origin.north(2)).toLong())+" loaded="+level.getChunkSource().getLoadedChunksCount())).thenExecute(()->{try{check.run();}finally{close();}h.succeed();});}
        public void close(){if(closed)return;closed=true;if(observer!=null)level.removePlayerImmediately(observer,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);born.forEach(net.minecraft.world.entity.Entity::discard);colony.dead=true;for(var e:terrain.entrySet())level.setBlock(e.getKey(),e.getValue(),3);for(var cp:forced)level.setChunkForced(cp.x,cp.z,false);level.getDataStorage().set("blacklight_building_infection",old);if(city!=null)level.getDataStorage().set("blacklight_city",city);level.getDataStorage().set("blacklight_city_ecology",ecology);level.getServer().overworld().getDataStorage().set("blacklight_outbreak",outbreak);level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(grief,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawns,level.getServer());BuildingInfectionConfig.ASSIMILATE.set(enabled);BuildingInfectionConfig.WORK.set(work);}
    }
    private static void dimension(GameTestHelper h,ServerLevel level){var f=new Fixture(h,level);var material=level.dimension().equals(Level.END)?Blocks.END_STONE.defaultBlockState():level.dimension().equals(Level.NETHER)?Blocks.NETHER_BRICKS.defaultBlockState():Blocks.QUARTZ_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.Z);var wall=f.contact(2,material);f.run(()->{
        f.colony.supply.receive(12);boolean converted=BuildingAssimilation.step(f.level,f.colony);h.assertTrue(converted&&f.colony.supply.reserve()==9&&f.colony.assimilation.cells.size()==1,"one real contacted wall consumes exactly three existing nutrients in "+f.level.dimension().location()+"; changed="+converted+" reserve="+f.colony.supply.reserve()+" ledger="+f.colony.assimilation.cells.size()+" patch="+f.level.getBlockState(wall.north())+" wall="+f.level.getBlockState(wall)+" ticking="+f.level.isPositionEntityTicking(wall)+" active="+WorldOutbreakSystem.active(f.level)+" stage="+f.colony.stage+" grief="+f.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING));
        var tissue=f.level.getBlockState(wall);h.assertTrue(tissue.is(ModBlocks.HIVE_CARAPACE.get())||tissue.is(ModBlocks.HIVE_SAC.get()),"the architectural block itself changes into structural tissue");h.assertTrue(tissue.isFaceSturdy(f.level,wall,Direction.NORTH),"assimilation keeps the physical load-bearing face for pillars and attached growth");
        var saved=BuildingInfectionData.load(f.data.save(new CompoundTag(),f.level.registryAccess()),f.level.registryAccess());h.assertTrue(saved.colonies.size()==1&&saved.colonies.getFirst().assimilation.cells.size()==1,"the dimension-local original palette and consumed coordinate survive actual NBT serialization");
        f.colony.stage=3;f.colony.supply.receive(3);f.level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,f.level.getServer());var before=new HashSet<Integer>();for(var entity:f.level.getAllEntities())before.add(entity.getId());
        boolean hatched=BuildingInfectionSystem.hatch(f.level,f.colony);for(var entity:f.level.getAllEntities())if(!before.contains(entity.getId())&&entity.getPersistentData().getBoolean("blacklight:outbreak_spawn"))f.born.add(entity);
        h.assertTrue(hatched&&f.born.size()==1&&f.born.getFirst() instanceof com.codex.blacklight.entity.BlacklightCommonInfected&&f.colony.supply.reserve()==0,"a prepared mature architectural organism pays its remaining twelve nutrients for one actual early-stage infected in "+f.level.dimension().location());
    });}
    @GameTest(template="wall_test",timeoutTicks=220,batch="assimilation_overworld")
    public static void contactedOverworldArchitectureReallyBecomesPaidLoadBearingTissue(GameTestHelper h){dimension(h,h.getLevel());}
    @GameTest(template="wall_test",timeoutTicks=600,batch="assimilation_nether")
    public static void theSameArchitecturalOrganismWorksInTheActualNether(GameTestHelper h){dimension(h,h.getLevel().getServer().getLevel(Level.NETHER));}
    @GameTest(template="wall_test",timeoutTicks=600,batch="assimilation_end")
    public static void theSameArchitecturalOrganismWorksInTheActualEnd(GameTestHelper h){dimension(h,h.getLevel().getServer().getLevel(Level.END));}
    @GameTest(template="wall_test",timeoutTicks=600,batch="assimilation_world")
    public static void theSameArchitecturalOrganismWorksInTheActualBlacklightWorld(GameTestHelper h){dimension(h,h.getLevel().getServer().getLevel(com.codex.blacklight.city.CityTravel.WORLD));}
    @GameTest(template="wall_test",timeoutTicks=220,batch="assimilation_ownership")
    public static void emptyStockYoungTissueAndDetachedCoordinatesCannotEatBuildings(GameTestHelper h){var f=new Fixture(h,h.getLevel());var wall=f.contact(2,Blocks.STONE_BRICKS.defaultBlockState());f.run(()->{
        h.assertTrue(!BuildingAssimilation.step(f.level,f.colony)&&f.level.getBlockState(wall).is(Blocks.STONE_BRICKS),"empty stock cannot consume architecture from time alone");f.colony.supply.receive(12);f.block(wall.north(),f.level.getBlockState(wall.north()).setValue(TissueAttachmentBlock.AGE,0));
        h.assertTrue(!BuildingAssimilation.step(f.level,f.colony)&&f.colony.supply.reserve()==12,"unmatured attachment cannot assimilate a support");
        f.colony.surfaces.clear();f.colony.surfaces.add(f.origin.east(2_000_000).asLong());int chunks=f.level.getChunkSource().getLoadedChunksCount();h.assertTrue(!BuildingAssimilation.step(f.level,f.colony)&&f.level.getChunkSource().getLoadedChunksCount()==chunks&&f.colony.supply.reserve()==12,"no disconnected remote scan loads chunks or spends resources");
    });}
    @GameTest(template="wall_test",timeoutTicks=220,batch="assimilation_protection")
    public static void actualPlacementVetoReturnsTheConsumedMaterialCostAndKeepsTheBuilding(GameTestHelper h){var f=new Fixture(h,h.getLevel());var wall=f.contact(2,Blocks.STONE_BRICKS.defaultBlockState());f.run(()->{
        f.colony.supply.receive(12);Consumer<BlockEvent.EntityPlaceEvent> veto=e->{if(e.getPos().equals(wall))e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(veto);
        try{h.assertTrue(!BuildingAssimilation.step(f.level,f.colony)&&f.level.getBlockState(wall).is(Blocks.STONE_BRICKS)&&f.colony.supply.reserve()==12&&f.colony.assimilation.empty(),"the actual protection event restores the original support and refunds exactly its paid material");}finally{NeoForge.EVENT_BUS.unregister(veto);}
        f.level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,f.level.getServer());h.assertTrue(!BuildingAssimilation.step(f.level,f.colony),"disabled griefing pauses structural consumption");f.level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,f.level.getServer());BuildingInfectionConfig.ASSIMILATE.set(false);h.assertTrue(!BuildingAssimilation.step(f.level,f.colony),"the independent structural-consumption setting also pauses it");
    });}
    @GameTest(template="wall_test",timeoutTicks=220,batch="assimilation_inventory")
    public static void assimilationDoesNotConsumeAnInventoryOrAnUnbreakableSupport(GameTestHelper h){var f=new Fixture(h,h.getLevel());var wall=f.contact(2,Blocks.CHEST.defaultBlockState());f.run(()->{
        f.colony.supply.receive(12);var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)f.level.getBlockEntity(wall);chest.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));h.assertTrue(!BuildingAssimilation.step(f.level,f.colony)&&!chest.getItem(0).isEmpty(),"inventory contents cannot be erased by natural tissue assimilation");f.block(wall,Blocks.BEDROCK.defaultBlockState());h.assertTrue(!BuildingAssimilation.step(f.level,f.colony)&&f.colony.supply.reserve()==12,"unbreakable architecture cannot be used as a resource sink");
    });}
    @GameTest(template="wall_test",timeoutTicks=220,batch="assimilation_recovery")
    public static void clearingAndReloadRestoreOnlyUnchangedRecordedArchitecture(GameTestHelper h){var f=new Fixture(h,h.getLevel());var a=f.contact(-3,Blocks.STONE_BRICKS.defaultBlockState());var b=f.contact(0,Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.X));var original=Blocks.QUARTZ_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.Z);var c=f.contact(3,original);f.run(()->{
        f.colony.supply.receive(12);for(int i=0;i<3;i++)h.assertTrue(BuildingAssimilation.step(f.level,f.colony),"three actual contacted supports assimilate before clearing");f.level.removeBlock(f.origin,false);
        var restored=BuildingInfectionData.load(f.data.save(new CompoundTag(),f.level.registryAccess()),f.level.registryAccess());h.assertTrue(restored.activeColonies()==0&&restored.colonies.size()==1&&restored.colonies.getFirst().dead&&restored.clearedSites.contains(f.origin.asLong()),"a dead organism retains its bounded restoration ledger across save without becoming a live producer");f.level.getDataStorage().set("blacklight_building_infection",restored);
        f.level.setBlock(a,Blocks.GOLD_BLOCK.defaultBlockState(),3);f.level.removeBlock(b,false);for(int i=0;i<4;i++)BuildingInfectionSystem.advance(f.level);
        h.assertTrue(f.level.getBlockState(a).is(Blocks.GOLD_BLOCK)&&f.level.getBlockState(b).isAir()&&f.level.getBlockState(c).equals(original)&&restored.colonies.isEmpty()&&!BuildingInfectionSystem.canRegister(f.level,f.origin),"clearing restores the exact untouched pillar axis, preserves player edits and mined cells, and keeps the reseeding tombstone");
    });}
    @GameTest(template="wall_test",timeoutTicks=220,batch="assimilation_work")
    public static void assimilationAndExternalCallbacksCannotMultiplyTheSharedBuildingWorkBudget(GameTestHelper h){var f=new Fixture(h,h.getLevel());f.contact(-3,Blocks.STONE_BRICKS.defaultBlockState());f.contact(0,Blocks.STONE_BRICKS.defaultBlockState());f.contact(3,Blocks.STONE_BRICKS.defaultBlockState());f.run(()->{
        f.colony.supply.receive(30);f.colony.workTurns=3;BuildingInfectionConfig.WORK.set(1);int[] nested={-1};Consumer<BlockEvent.EntityPlaceEvent> callback=e->{if(e.getPlacedBlock().is(ModBlocks.HIVE_CARAPACE.get())||e.getPlacedBlock().is(ModBlocks.HIVE_SAC.get()))nested[0]=BuildingInfectionSystem.advance(f.level);};NeoForge.EVENT_BUS.addListener(callback);
        try{int used=BuildingInfectionSystem.advance(f.level);h.assertTrue(used==1&&nested[0]==0&&f.colony.assimilation.cells.size()==1&&f.colony.supply.reserve()==27,"one genuine shared operation consumes one actual wall; an event cannot recurse into a fresh dimension work allowance");}finally{NeoForge.EVENT_BUS.unregister(callback);}
    });}
    @GameTest(template="wall_test",timeoutTicks=220,batch="assimilation_solid_recovery")
    public static void clearedColumnsAndAttachedSkinRetreatWithinTheWorkBudgetAcrossReload(GameTestHelper h){var f=new Fixture(h,h.getLevel());f.contact(2,Blocks.STONE_BRICKS.defaultBlockState());f.run(()->{
        f.colony.supply.receive(768);f.colony.activeTicks=24000;
        var stem=new BuildingBiomassField.Stem(List.of(new BuildingBiomassField.Point(.5,0,.5),new BuildingBiomassField.Point(.5,4,-2.5)),1.6,.7,0,1000,-1);
        f.colony.mass=new BuildingBiomassPlan(List.of(stem),List.of(f.origin.offset(0,4,1)));var cells=List.copyOf(f.colony.mass.cells());
        for(int i=0;i<cells.size()+1;i++)BuildingBiomassSystem.step(f.level,f.colony);
        var edited=cells.stream().map(c->f.origin.offset(c.x(),c.y(),c.z())).filter(p->f.level.getBlockState(p).is(ModBlocks.BUILDING_BIOMASS.get())).findFirst().orElseThrow();
        h.assertTrue(f.colony.massBlocks>5,"an actual solid collagen column exists before the source is cleared");f.level.setBlock(edited,Blocks.GOLD_BLOCK.defaultBlockState(),3);f.level.removeBlock(f.origin,false);BuildingInfectionConfig.WORK.set(1);
        h.assertTrue(BuildingInfectionSystem.advance(f.level)<=1&&f.colony.hasRemains(),"clearing cannot erase a complete solid organism in one work operation");
        var tag=f.data.save(new CompoundTag(),f.level.registryAccess());var restored=BuildingInfectionData.load(tag,f.level.registryAccess());h.assertTrue(restored.colonies.size()==1&&restored.colonies.getFirst().dead&&restored.colonies.getFirst().mass!=null,"a dead solid organism and its actual cleanup progress remain saved until retreat finishes");f.level.getDataStorage().set("blacklight_building_infection",restored);
        for(int i=0;i<cells.size()+20;i++)h.assertTrue(BuildingInfectionSystem.advance(f.level)<=1,"each restored cleanup step uses the same one-operation dimension budget");
        h.assertTrue(restored.colonies.isEmpty()&&f.level.getBlockState(edited).is(Blocks.GOLD_BLOCK)&&cells.stream().map(c->f.origin.offset(c.x(),c.y(),c.z())).noneMatch(p->f.level.getBlockState(p).is(ModBlocks.BUILDING_BIOMASS.get())),"the solid body actually retreats and releases its saved slot without erasing the player's replacement");
    });}
    @GameTest(template="wall_test",timeoutTicks=220,batch="assimilation_recovery_veto")
    public static void protectedRestorationPausesWithoutLosingItsDeadLedgerOrRecursingWork(GameTestHelper h){var f=new Fixture(h,h.getLevel());var wall=f.contact(2,Blocks.STONE_BRICKS.defaultBlockState());f.run(()->{
        f.colony.supply.receive(12);h.assertTrue(BuildingAssimilation.step(f.level,f.colony),"the original wall is actually assimilated first");var skin=f.level.getBlockState(wall);f.level.removeBlock(f.origin,false);BuildingInfectionConfig.WORK.set(1);int[] nested={-1};
        Consumer<BlockEvent.EntityPlaceEvent> veto=e->{if(e.getPos().equals(wall)&&e.getPlacedBlock().is(Blocks.STONE_BRICKS)){nested[0]=BuildingInfectionSystem.advance(f.level);e.setCanceled(true);}};NeoForge.EVENT_BUS.addListener(veto);
        try{h.assertTrue(BuildingInfectionSystem.advance(f.level)==1&&nested[0]==0&&f.level.getBlockState(wall).equals(skin)&&f.data.colonies.size()==1&&!f.colony.assimilation.empty(),"a genuine restore veto keeps the exact tissue and original material record; nested work is rejected");}finally{NeoForge.EVENT_BUS.unregister(veto);}
        f.level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,f.level.getServer());h.assertTrue(BuildingInfectionSystem.advance(f.level)==0&&f.level.getBlockState(wall).equals(skin),"disabled griefing also pauses cleanup");f.level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,f.level.getServer());for(int i=0;i<4;i++)BuildingInfectionSystem.advance(f.level);h.assertTrue(f.level.getBlockState(wall).is(Blocks.STONE_BRICKS)&&f.data.colonies.isEmpty(),"removing the actual veto permits the recorded architecture and attached skin to finish recovery");
    });}
    private static boolean hatch(Fixture f){var ids=new HashSet<Integer>();for(var e:f.level.getAllEntities())ids.add(e.getId());boolean ok=BuildingInfectionSystem.hatch(f.level,f.colony);for(var e:f.level.getAllEntities())if(!ids.contains(e.getId())&&e.getPersistentData().getBoolean("blacklight:outbreak_spawn"))f.born.add(e);return ok;}
    private static void brood(Fixture f){f.colony.stage=3;f.colony.broodTicks=600;f.colony.supply.receive(96);f.level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,f.level.getServer());}
    @GameTest(template="wall_test",timeoutTicks=220,batch="assimilation_brood_exit")
    public static void structuralHatchingSkipsABodySizedSealedPocketAndFindsAWalkableOutlet(GameTestHelper h){var f=new Fixture(h,h.getLevel());f.run(()->{
        var pocket=f.origin.north(2);for(var dir:Direction.Plane.HORIZONTAL)for(int y=0;y<3;y++)f.block(pocket.relative(dir).above(y),Blocks.STONE.defaultBlockState());f.block(pocket.above(2),Blocks.STONE.defaultBlockState());
        var probe=com.codex.blacklight.registry.ModEntities.COMMON_INFECTED.get().create(f.level);probe.setPos(pocket.getBottomCenter());h.assertTrue(f.level.noCollision(probe)&&!WorldOutbreakSystem.hasWalkout(f.level,probe),"the old body-only test accepts a sealed standing pocket, while actual walking space rejects it");probe.discard();
        brood(f);h.assertTrue(hatch(f)&&f.born.size()==1&&WorldOutbreakSystem.hasWalkout(f.level,(net.minecraft.world.entity.Mob)f.born.getFirst())&&f.born.getFirst().distanceToSqr(pocket.getCenter())>1,"the real building producer skips that pocket and spends its food once at a clear outlet");
    });}
    @GameTest(template="wall_test",timeoutTicks=220,batch="assimilation_brood_reentry")
    public static void anActualEntityJoinCannotRepeatOneBuildingsPreparedBroodWork(GameTestHelper h){var f=new Fixture(h,h.getLevel());f.run(()->{
        brood(f);int[] calls={0};boolean[] replay={true};Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> callback=e->{if(e.getLevel()==f.level&&e.getEntity() instanceof com.codex.blacklight.entity.BlacklightCommonInfected){calls[0]++;replay[0]=BuildingInfectionSystem.hatch(f.level,f.colony);}};NeoForge.EVENT_BUS.addListener(callback);
        try{h.assertTrue(hatch(f)&&calls[0]==1&&!replay[0]&&f.colony.births==1&&f.colony.broodTicks==0&&f.colony.supply.reserve()==84,"one paid architectural brood is reserved before actual external callbacks and cannot replay");}finally{NeoForge.EVENT_BUS.unregister(callback);}
    });}
    @GameTest(template="wall_test",timeoutTicks=220,batch="assimilation_brood_veto")
    public static void canceledBuildingBirthsReturnTheirFoodAndRetainAlreadyPaidBroodWork(GameTestHelper h){var f=new Fixture(h,h.getLevel());f.run(()->{
        brood(f);Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> veto=e->{if(e.getLevel()==f.level&&e.getEntity() instanceof com.codex.blacklight.entity.BlacklightCommonInfected)e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(veto);
        try{h.assertTrue(!hatch(f)&&f.colony.supply.reserve()==96&&f.colony.broodTicks==600&&f.colony.births==0,"actual publication rejection cannot eat paid food or erase the building's ready brood");}finally{NeoForge.EVENT_BUS.unregister(veto);}
    });}
    @GameTest(template="wall_test",timeoutTicks=220,batch="assimilation_brood_source_lost")
    public static void clearingTheActualSourceInsideAJoinCallbackCannotLeaveAGhostBrood(GameTestHelper h){var f=new Fixture(h,h.getLevel());f.run(()->{
        brood(f);Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> cut=e->{if(e.getLevel()==f.level&&e.getEntity() instanceof com.codex.blacklight.entity.BlacklightCommonInfected){f.level.removeBlock(f.origin,false);f.data.save(new CompoundTag(),f.level.registryAccess());}};NeoForge.EVENT_BUS.addListener(cut);
        try{h.assertTrue(!hatch(f)&&f.born.isEmpty()&&f.colony.births==0&&f.colony.dead&&f.colony.supply.reserve()==96,"source removal during the actual publication event discards the provisional newborn, refunds its food and cannot count a ghost birth");}finally{NeoForge.EVENT_BUS.unregister(cut);}
    });}
}
