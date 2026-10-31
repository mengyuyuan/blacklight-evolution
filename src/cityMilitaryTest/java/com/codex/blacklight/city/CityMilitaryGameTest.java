package com.codex.blacklight.city;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.firearms.FirearmItem;
import com.codex.blacklight.firearms.FirearmsRegistry;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class CityMilitaryGameTest {
    @GameTest(template="blackwatch_arena",batch="cityMilitary",timeoutTicks=300)
    public static void nativeRoadCheckpointKeepsLaneOpenAndUsesFiniteBasePosts(GameTestHelper h){
        var level=h.getLevel().getServer().getLevel(CityTravel.CITY);var site=new CityMilitaryPlan.Site(14400,0,80,0,false);var checkpoint=new CityMilitaryPlan.Checkpoint(14424,80,80,0);
        var piece=new CityMilitaryPiece(site,checkpoint);var bounds=piece.getBoundingBox();
        piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),level.random,bounds,new net.minecraft.world.level.ChunkPos(site.x()>>4,0),BlockPos.ZERO);
        for(int z=0;z<17;z++)for(int y=1;y<3;y++){int[] p=checkpoint.world(8,z);h.assertTrue(level.getBlockState(new BlockPos(p[0],80+y,p[1])).isAir(),"checkpoint leaves a continuous two-block-high road passage");}
        String id=CityMilitaryService.register(level,site,checkpoint);
        for(int x=(bounds.minX()>>4)-1;x<=(bounds.maxX()>>4)+1;x++)for(int z=(bounds.minZ()>>4)-1;z<=(bounds.maxZ()>>4)+1;z++)level.setChunkForced(x,z,true);
        int[] p=checkpoint.world(3,6);var post=new BlockPos(p[0],81,p[1]);
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.isPositionEntityTicking(post),"checkpoint posts must tick"))
                .thenExecute(()->{
                    CityMilitaryService.maintain(level,java.util.List.of(new net.minecraft.world.phys.Vec3(site.x()+32,81,50)));
                    var base=CityMilitaryData.get(level).bases.get(id);h.assertTrue(java.util.Arrays.stream(base.slots).filter(java.util.Objects::nonNull).count()==2,"checkpoint reuses finite base budget instead of NBT-spawned extra guards");
                    var soldier=(MilitarySoldier)level.getEntity(base.slots[0]);h.assertTrue(soldier!=null&&soldier.getPersistentData().getLong(CityMilitaryService.POST_TAG)==post.asLong(),"actual first guard belongs to saved road post");
                    var saved=CityMilitaryData.load(CityMilitaryData.get(level).save(new CompoundTag()));h.assertTrue(checkpoint.equals(saved.bases.get(id).checkpoint),"road checkpoint persists with its owning base");h.succeed();
                });
    }
    @GameTest(template="blackwatch_arena",batch="cityMilitary",timeoutTicks=1200)
    public static void baseGuardsHoldPostsAndReturnAfterSupply(GameTestHelper h){
        var level=h.getLevel().getServer().getLevel(CityTravel.CITY);var site=new CityMilitaryPlan.Site(14000,0,80,0,false);CityMilitaryPlacement.place(level,site);
        for(int x=(site.x()>>4)-1;x<=((site.x()+site.width()-1)>>4)+1;x++)for(int z=-1;z<=5;z++)level.setChunkForced(x,z,true);
        var definition=LargeBaseBlueprint.get(false);var first=definition.guards().get(0);int[] p=site.world(first.x(),first.z());var post=new BlockPos(p[0],site.floor()+first.y(),p[1]);
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.isPositionEntityTicking(post),"post fixture must tick"))
                .thenExecute(()->{
                    var base=CityMilitaryData.get(level).bases.get(CityMilitaryService.register(level,site));CityMilitaryService.maintain(level,java.util.List.of(new net.minecraft.world.phys.Vec3(site.x()+32,81,32)));
                    var soldier=(MilitarySoldier)level.getEntity(base.slots[0]);h.assertTrue(soldier!=null,"real posted guard exists");

                    h.runAfterDelay(220,()->{
                        h.assertTrue(soldier.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(post))<4,"guard must retain entry post instead of gathering at center pos="+soldier.position()+" expected="+post+" post="+CityMilitaryService.post(soldier));
                        var message=CityMilitaryService.status(level,soldier.position()).get(0);h.assertTrue(message.contains("WATCH"),"idle base reports watch state");
                        var enemy=ModEntities.INFECTED_HOST.get().create(level);enemy.inherit(net.minecraft.world.entity.EntityType.COW.create(level));enemy.moveTo(soldier.getX()+4,81,soldier.getZ(),0,0);enemy.setNoAi(true);level.addFreshEntity(enemy);soldier.setTarget(enemy);
                        h.assertTrue(CityMilitaryService.status(level,soldier.position()).get(0).contains("ENGAGED"),"actual hostile target changes base state");enemy.discard();soldier.setTarget(null);
                        var saved=soldier.saveWithoutId(new CompoundTag());saved.getCompound("MilitaryLoadout").getCompound("Reserves").putInt(((FirearmItem)soldier.getMainHandItem().getItem()).definition().ammoId(),0);soldier.load(saved);
                        h.assertTrue(soldier.reserveAmmo()==0,"posted guard has an actual typed reserve deficit before seeking supply");
                        for(int[] local:definition.ammunition()){int[] cp=site.world(local[0],local[2]);var box=(ChestBlockEntity)level.getBlockEntity(new BlockPos(cp[0],81,cp[1]));box.unpackLootTable(null);box.clearContent();}
                        var ammo=new BlockPos(site.x()+52,81,12);var chest=(ChestBlockEntity)level.getBlockEntity(ammo);chest.setItem(0,new ItemStack(FirearmsRegistry.ammo("rifle_round"),7));
                        soldier.moveTo(site.x()+52.5,81,23.5,0,0);soldier.getNavigation().stop();
                        h.startSequence().thenWaitUntil(()->h.assertTrue(soldier.reserveAmmo()==7,"guard obtains finite supplies"))
                                .thenWaitUntil(()->h.assertTrue(soldier.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(post))<4,"resupplied guard returns to its persisted post pos="+soldier.position()+" reserve="+soldier.reserveAmmo()+" target="+soldier.getTarget()+" post="+CityMilitaryService.post(soldier)))
                                .thenExecute(()->{soldier.discard();h.succeed();});
                    });
                });
    }
    @GameTest(template="blackwatch_arena",batch="cityMilitary",timeoutTicks=1200)
    public static void cityNativePlacementPipelineAndDiscoveryKeepEdits(GameTestHelper h){
        var level=h.getLevel().getServer().getLevel(CityTravel.CITY);h.assertTrue(level!=null,"actual city dimension exists in the fixture");
        var survey=CityMilitaryTerrain.survey(level.getChunkSource().getGenerator(),level.getSeed());
        var placement=new net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement(24,12,net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType.LINEAR,26102471);
        net.minecraft.world.level.ChunkPos start=null;
        for(int rx=0;rx<4&&start==null;rx++)for(int rz=0;rz<4&&start==null;rz++){
            var candidate=placement.getPotentialStructureChunk(level.getSeed(),rx*24,rz*24);
            boolean bw=((level.getSeed()^candidate.toLong())&1)!=0;
            if(CityMilitaryPlan.find(level.getSeed(),candidate.x,candidate.z,bw,true,survey).isPresent())start=candidate;
        }
        h.assertTrue(start!=null,"actual Futian map has a safe road-adjacent 65-block plot");
        var chunk=level.getChunk(start.x,start.z);
        // Vanilla GameTestServer intentionally disables structures in its WorldOptions.
        // Exercise its native start/piece APIs here; ordinary runServer audits automatic generation.
        boolean explicit=!level.getServer().getWorldData().worldGenOptions().generateStructures();
        if(explicit)level.getChunkSource().getGenerator().createStructures(level.registryAccess(),level.getChunkSource().getGeneratorState(),level.structureManager(),chunk,level.getStructureManager());
        var structure=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).get(new net.minecraft.resources.ResourceLocation("blacklight","city_military_base"));
        var generated=chunk.getStartForStructure(structure);
        h.assertTrue(generated!=null&&generated.isValid(),"native structure starts are enabled in the actual city generator");
        var piece=(CityMilitaryPiece)generated.getPieces().get(0);var site=piece.site();
        for(int x=site.x()>>4;x<=(site.x()+site.width()-1)>>4;x++)for(int z=site.z()>>4;z<=(site.z()+site.depth()-1)>>4;z++){
            level.getChunk(x,z);
            if(explicit)piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),level.random,new net.minecraft.world.level.levelgen.structure.BoundingBox(x*16,level.getMinBuildHeight(),z*16,x*16+15,level.getMaxBuildHeight()-1,z*16+15),new net.minecraft.world.level.ChunkPos(x,z),BlockPos.ZERO);
        }
        var ammo=LargeBaseBlueprint.get(site.blackwatch()).ammunition().get(0);int[] world=site.world(ammo[0],ammo[2]);
        h.assertTrue(level.getBlockEntity(new BlockPos(world[0],site.floor()+ammo[1],world[1])) instanceof ChestBlockEntity,"cross-chunk native placement contains real ammunition depot");
        var marker=new BlockPos(site.x()+30,site.floor()+1,site.z()+30);level.setBlock(marker,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
        String id=CityMilitaryService.register(level,site);CityMilitaryService.register(level,site);level.getChunk(start.x,start.z);
        h.assertTrue(level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK)&&CityMilitaryData.get(level).bases.containsKey(id),"discovery and repeat chunk reads preserve later edits");h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="cityMilitary",timeoutTicks=650)
    public static void soldierWalksIntoAmmoDepotAndConsumesActualStore(GameTestHelper h){
        var level=h.getLevel().getServer().getLevel(CityTravel.CITY);var site=new CityMilitaryPlan.Site(13600,0,80,0,false);String id=CityMilitaryPlacement.place(level,site);
        for(int x=(site.x()>>4)-1;x<=((site.x()+site.width()-1)>>4)+1;x++)for(int z=-1;z<=5;z++)level.setChunkForced(x,z,true);
        // Exactly seven matching rounds are available across the registered base stores.
        for(var local:LargeBaseBlueprint.get(false).ammunition()){
            int[] point=site.world(local[0],local[2]);var box=(ChestBlockEntity)level.getBlockEntity(new BlockPos(point[0],site.floor()+local[1],point[1]));box.unpackLootTable(null);
            for(int slot=0;slot<box.getContainerSize();slot++)if(box.getItem(slot).is(FirearmsRegistry.ammo("rifle_round")))box.setItem(slot,ItemStack.EMPTY);
        }
        var at=new BlockPos(site.x()+52,81,12);var chest=(ChestBlockEntity)level.getBlockEntity(at);chest.unpackLootTable(null);chest.clearContent();chest.setItem(0,new ItemStack(FirearmsRegistry.ammo("rifle_round"),7));
        var soldier=ModEntities.MILITARY_SOLDIER.get().create(level);soldier.moveTo(site.x()+52.5,81,23.5,0,0);soldier.setPersistenceRequired();soldier.setAutomaticUnitJoin(false);soldier.getPersistentData().putString(CityMilitaryService.BASE_TAG,id);level.addFreshEntity(soldier);soldier.initializeWeapon();
        var saved=soldier.saveWithoutId(new CompoundTag());saved.getCompound("MilitaryLoadout").getCompound("Reserves").putInt(((FirearmItem)soldier.getMainHandItem().getItem()).definition().ammoId(),0);soldier.load(saved);
        h.assertTrue(soldier.reserveAmmo()==0,"depot walking fixture starts with an empty actual typed reserve");
        double initial=soldier.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(at));h.assertTrue(initial>9,"fixture starts outside supply radius and doorway");
        for(int elapsed=60;elapsed<=600;elapsed+=60){final int sample=elapsed;h.runAtTickTime(sample,()->org.slf4j.LoggerFactory.getLogger(CityMilitaryGameTest.class).info("CITY_SUPPLY_DIAGNOSTIC tick={} {}",sample,supplyDiagnostic(soldier,at)));}
        h.startSequence().thenWaitUntil(()->{
            h.assertTrue(soldier.reserveAmmo()==7,"real AI must enter the depot and take seven rounds; "+supplyDiagnostic(soldier,at));
            h.assertTrue(chest.getItem(0).isEmpty()&&soldier.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(at))<=9,"actual store depleted only after reachable arrival");
        }).thenExecute(()->{soldier.discard();h.succeed();});
    }
    private static String supplyRouteDiagnostic(MilitarySoldier soldier){
        try{
            var field=CityMilitaryService.SupplyGoal.class.getDeclaredField("waypoints");field.setAccessible(true);
            for(var wrapped:soldier.goalSelector.getAvailableGoals())if(wrapped.isRunning()&&wrapped.getGoal() instanceof CityMilitaryService.SupplyGoal){var route=(java.util.Deque<?>)field.get(wrapped.getGoal());var targetField=CityMilitaryService.SupplyGoal.class.getDeclaredField("target");targetField.setAccessible(true);var selected=(BlockPos)targetField.get(wrapped.getGoal());return "remaining="+route.size()+"/next="+route.peek()+"/selectedBox="+selected+"/standingSelected="+(selected==null?"NONE":com.codex.blacklight.military.MilitaryFormationSystem.standing(soldier,net.minecraft.world.phys.Vec3.atBottomCenterOf(selected)));}
            return "NOT_RUNNING";
        }catch(ReflectiveOperationException failure){return "DIAGNOSTIC_ERROR:"+failure.getClass().getSimpleName();}
    }
    private static String supplyDiagnostic(MilitarySoldier soldier,BlockPos chest){
        var nav=soldier.getNavigation();var path=nav.getPath();
        return "time="+soldier.level().getGameTime()+" position="+soldier.position()+" reserve="+soldier.reserveAmmo()+" magazine="+soldier.magazineAmmo()+" box_distance="+soldier.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(chest))
            +" box="+((soldier.level().getBlockEntity(chest) instanceof ChestBlockEntity box)?box.getItem(0):"MISSING")+" target="+soldier.getTarget()+" standingAtChest="+com.codex.blacklight.military.MilitaryFormationSystem.standing(soldier,net.minecraft.world.phys.Vec3.atBottomCenterOf(chest))+" chestHeightmap="+soldier.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,chest.getX(),chest.getZ())
            +" running="+soldier.goalSelector.getAvailableGoals().stream().filter(goal->goal.isRunning()).map(goal->goal.getPriority()+":"+goal.getGoal().getClass().getSimpleName()).toList()
            +" supplyRoute="+supplyRouteDiagnostic(soldier)+" navDone="+nav.isDone()+" path="+(path==null?"NONE":path.getTarget()+"/"+path.getNextNodeIndex()+"/"+path.getNodeCount()+"/reachable="+path.canReach())+" metrics="+com.codex.blacklight.military.MilitaryFormationSystem.metrics((net.minecraft.server.level.ServerLevel)soldier.level())+" "+MilitaryHomeSystem.describe(soldier);
    }

    @GameTest(template="blackwatch_arena",batch="cityMilitary",timeoutTicks=300)
    public static void garrisonBudgetAndSavedSlotsDoNotDuplicate(GameTestHelper h){
        var level=h.getLevel().getServer().getLevel(CityTravel.CITY);var site=new CityMilitaryPlan.Site(13200,0,80,0,false);CityMilitaryPlacement.place(level,site);
        for(int x=(site.x()>>4)-1;x<=((site.x()+site.width()-1)>>4)+1;x++)for(int z=-1;z<=5;z++)level.setChunkForced(x,z,true);
        var first=LargeBaseBlueprint.get(false).guards().get(0);int[] p=site.world(first.x(),first.z());var position=new BlockPos(p[0],site.floor()+first.y(),p[1]);
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.isPositionEntityTicking(position),"garrison fixture chunks must be entity ticking"))
                .thenExecute(()->{
                    var observers=java.util.List.of(new net.minecraft.world.phys.Vec3(site.x()+32,81,32));
                    var data=CityMilitaryData.get(level);var base=data.bases.get(CityMilitaryService.register(level,site));
                    CityMilitaryService.maintain(level,observers);long issued=java.util.Arrays.stream(base.slots).filter(java.util.Objects::nonNull).count();
                    h.assertTrue(issued==2,"only two garrison slots can be issued in one maintenance pass");
                    var saved=CityMilitaryData.load(data.save(new CompoundTag()));
                    h.assertTrue(java.util.Arrays.equals(saved.bases.get(base.id).slots,base.slots),"issued UUIDs survive SavedData round trip");
                    var id=base.slots[0];var soldier=(MilitarySoldier)level.getEntity(id);h.assertTrue(soldier!=null&&soldier.unitId()!=null,"guard is a real organized soldier");soldier.discard();
                    h.assertTrue((base.losses&1)!=0,"permanent loss is recorded without treating unloading as death");
                    CityMilitaryService.maintain(level,observers);h.assertTrue(base.slots[0].equals(id)&&level.getEntity(id)==null,"lost issued slot is not replaced or refilled");
                    int old=CityConfig.MILITARY_GARRISON_LIMIT.get();CityConfig.MILITARY_GARRISON_LIMIT.set(0);try{long before=java.util.Arrays.stream(base.slots).filter(java.util.Objects::nonNull).count();CityMilitaryService.maintain(level,observers);h.assertTrue(java.util.Arrays.stream(base.slots).filter(java.util.Objects::nonNull).count()==before,"zero population budget blocks new guards");}finally{CityConfig.MILITARY_GARRISON_LIMIT.set(old);}
                    h.succeed();
                });
    }
    @GameTest(template="blackwatch_arena",batch="cityMilitary",timeoutTicks=300)
    public static void largeBasesPlaceRotatedWithRealFiniteAmmunition(GameTestHelper h){
        for(int turn=0;turn<4;turn++){
            var site=new CityMilitaryPlan.Site(12000+turn*128,0,80,turn,turn%2==1);
            CityMilitaryPlacement.place(h.getLevel(),site);
            var definition=LargeBaseBlueprint.get(site.blackwatch());
            int[] coordinate=definition.ammunition().get(0),point=site.world(coordinate[0],coordinate[2]);
            var chest=(ChestBlockEntity)h.getLevel().getBlockEntity(new BlockPos(point[0],81,point[1]));
            h.assertTrue(chest!=null,"rotated real ammunition chest exists "+turn);
            chest.unpackLootTable(null);int rounds=0;
            for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).is(FirearmsRegistry.ammo("rifle_round")))rounds+=chest.getItem(slot).getCount();
            h.assertTrue(rounds>0&&rounds<=1728,"finite actual ammunition loot exists");
            int[] entrance=site.world(32,definition.depth()-1);
            h.assertTrue(h.getLevel().getBlockState(new BlockPos(entrance[0],81,entrance[1])).isAir(),"entrance remains open after rotation");
            h.assertTrue(CityMilitaryData.get(h.getLevel()).bases.containsKey(CityMilitaryService.register(h.getLevel(),site)),"base registered exactly once");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="cityMilitary",timeoutTicks=100)
    public static void placementRejectsOccupiedVolumeWithoutPartialOverwrite(GameTestHelper h){
        var site=new CityMilitaryPlan.Site(12800,0,80,0,false);var existing=new BlockPos(12834,81,33);
        h.getLevel().setBlock(existing,Blocks.CHEST.defaultBlockState(),3);
        var chest=(ChestBlockEntity)h.getLevel().getBlockEntity(existing);chest.setItem(0,new ItemStack(net.minecraft.world.item.Items.DIAMOND,7));
        boolean refused=false;try{CityMilitaryPlacement.place(h.getLevel(),site);}catch(IllegalArgumentException expected){refused=true;}
        h.assertTrue(refused&&chest.getItem(0).getCount()==7,"existing container rejects the entire placement");
        h.assertTrue(h.getLevel().getBlockState(new BlockPos(12800,80,0)).isAir(),"no partial floor placed on refusal");h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="cityMilitary",timeoutTicks=50)
    public static void physicalSupplyDebitsOnlyAcceptedRoundsAndCannotCrossWalls(GameTestHelper h){
        var level=h.getLevel();var at=h.absolutePos(new BlockPos(5,2,5));h.setBlock(5,1,5,Blocks.STONE);h.setBlock(5,2,5,Blocks.CHEST);
        var chest=(ChestBlockEntity)level.getBlockEntity(at);chest.setItem(0,new ItemStack(FirearmsRegistry.ammo("rifle_round"),12));
        MilitarySoldier soldier=ModEntities.MILITARY_SOLDIER.get().create(level);soldier.moveTo(at.getX()+.5,at.getY(),at.getZ()+2.5,0,0);level.addFreshEntity(soldier);soldier.initializeWeapon();
        var saved=soldier.saveWithoutId(new CompoundTag());saved.getCompound("MilitaryLoadout").getCompound("Reserves").putInt(((FirearmItem)soldier.getMainHandItem().getItem()).definition().ammoId(),85);soldier.load(saved);
        h.assertTrue(soldier.reserveAmmo()==85,"physical transfer fixture starts exactly five rounds below its issued cap");
        h.assertTrue(CityMilitaryService.transfer(level,soldier,at)==5&&chest.getItem(0).getCount()==7&&soldier.reserveAmmo()==90,"supply conservation and finite cap");
        saved=soldier.saveWithoutId(new CompoundTag());saved.getCompound("MilitaryLoadout").getCompound("Reserves").putInt(((FirearmItem)soldier.getMainHandItem().getItem()).definition().ammoId(),0);soldier.load(saved);
        h.assertTrue(soldier.reserveAmmo()==0,"blocked chest fixture retains an actual typed reserve deficit");
        level.setBlock(at.offset(0,0,1),Blocks.STONE.defaultBlockState(),3);level.setBlock(at.offset(0,1,1),Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(CityMilitaryService.transfer(level,soldier,at)==0&&chest.getItem(0).getCount()==7,"walls block access to the real chest");
        soldier.discard();h.succeed();
    }
}
