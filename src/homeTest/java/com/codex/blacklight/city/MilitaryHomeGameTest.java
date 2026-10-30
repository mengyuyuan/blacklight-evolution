package com.codex.blacklight.city;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.military.*;
import com.codex.blacklight.registry.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** Loaded real worlds/entities. Future home fields are observed through actual saved entity data. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryHomeGameTest {
    private static final String HOME="BlacklightMilitaryHome",FIELD="BlacklightMilitaryFieldAnchor",DIM="BlacklightMilitaryFieldDimension",MODE="BlacklightMilitaryHomeMode";

    @GameTest(template="blackwatch_arena",batch="homes5",timeoutTicks=180)
    public static void independentSoldierAcquiresSameFactionBaseWithoutConsumingItsNativeSlots(GameTestHelper h){
        var f=new Fixture(h,42000,false,true);var spawn=new BlockPos(42082,81,70);f.fieldFloor(spawn,15);
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.ticking(),"actual base core and patrol chunks must tick"))
            .thenExecute(()->{
                var soldier=f.soldier(false,spawn);f.primary=soldier;f.initialReserve=soldier.reserveAmmo();
                h.assertTrue(!soldier.getPersistentData().contains(CityMilitaryService.BASE_TAG),"actual standalone spawn has no native origin base");
            }).thenIdle(45).thenExecute(()->{
                var soldier=f.primary;
                h.assertTrue(f.id.equals(soldier.getPersistentData().getString(HOME)),"standalone soldier must acquire an operational same-faction home after initialization");
                h.assertTrue(!soldier.getPersistentData().contains(CityMilitaryService.BASE_TAG)&&issued(f.base())==0,"external patrol does not consume or pretend to be a native issuance slot");
                h.assertTrue(soldier.reserveAmmo()==f.initialReserve,"home assignment cannot grant free ammunition");
            }).thenExecute(f::close).thenSucceed();
    }

    @GameTest(template="blackwatch_arena",batch="homes5",timeoutTicks=180)
    public static void noBaseCreatesPersistentFieldPatrolAtTheSpawnAnchor(GameTestHelper h){
        var f=new Fixture(h,44000,false,false);var spawn=new BlockPos(44032,81,32);f.fieldFloor(spawn,16);
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.level.isPositionEntityTicking(spawn),"field fixture must actually tick"))
            .thenExecute(()->{f.primary=f.soldier(false,spawn);f.initialReserve=f.primary.reserveAmmo();})
            .thenIdle(45).thenExecute(()->{
                var soldier=f.primary;var data=soldier.getPersistentData();
                h.assertTrue(data.contains(FIELD)&&data.getLong(FIELD)==spawn.asLong(),"a soldier with no loaded valid base keeps its actual original spawn as field anchor");
                h.assertTrue(data.getString(HOME).isEmpty()&&data.getString(DIM).equals(f.level.dimension().location().toString()),"field patrol stays in its own dimension without inventing a base");
                h.assertTrue(soldier.isPersistenceRequired()&&soldier.reserveAmmo()==f.initialReserve,"independent patrol persists without refilling ammunition");
                var saved=soldier.saveWithoutId(new CompoundTag());var copy=ModEntities.MILITARY_SOLDIER.get().create(f.level);copy.load(saved);
                h.assertTrue(copy.getPersistentData().getLong(FIELD)==spawn.asLong()&&copy.getPersistentData().getString(DIM).equals(data.getString(DIM)),"real entity serialization preserves the field patrol anchor and dimension");
            }).thenExecute(f::close).thenSucceed();
    }

    @GameTest(template="blackwatch_arena",batch="homes5",timeoutTicks=180)
    public static void aNearbyOppositeFactionBaseDoesNotClaimAnIndependentSoldier(GameTestHelper h){
        var f=new Fixture(h,46000,true,true);var spawn=new BlockPos(46082,81,70);f.fieldFloor(spawn,15);
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.ticking(),"opposite-faction base cores must actually tick"))
            .thenExecute(()->f.primary=f.soldier(false,spawn)).thenIdle(45).thenExecute(()->{
                var data=f.primary.getPersistentData();
                h.assertTrue(data.getString(HOME).isEmpty()&&data.contains(FIELD),"a military soldier must use field patrol instead of binding to the nearby Blackwatch base");
                h.assertTrue(issued(f.base())==0,"an outsider cannot consume opposite-faction native slots");
            }).thenExecute(f::close).thenSucceed();
    }

    @GameTest(template="blackwatch_arena",batch="homes5",timeoutTicks=650)
    public static void anIndependentSoldierWalksToItsHomeDepotAndDebitsActualRounds(GameTestHelper h){independentSupply(h,48000,52);}
    @GameTest(template="blackwatch_arena",batch="homes5",timeoutTicks=650)
    public static void anIndependentSoldierWalksToTheSecondDepotBelowItsSolidRoofAndDebitsSevenRounds(GameTestHelper h){independentSupply(h,76000,54);}
    private static void independentSupply(GameTestHelper h,int x,int selectedDepot){
        var f=new Fixture(h,x,false,true);var spawn=new BlockPos(x+52,81,24);var at=new BlockPos(x+selectedDepot,81,12);
        // This is an exactly-seven-round finite-source fixture. Other registered stores must not
        // silently supply random matching loot while the original source is being verified.
        for(var local:LargeBaseBlueprint.get(false).ammunition()){
            int[] point=f.site.world(local[0],local[2]);var box=(ChestBlockEntity)f.level.getBlockEntity(new BlockPos(point[0],f.site.floor()+local[1],point[1]));box.unpackLootTable(null);
            if(selectedDepot==54)box.clearContent();else for(int slot=0;slot<box.getContainerSize();slot++)if(box.getItem(slot).is(FirearmsRegistry.ammo("rifle_round")))box.setItem(slot,ItemStack.EMPTY);
        }
        var chest=(ChestBlockEntity)f.level.getBlockEntity(at);chest.clearContent();chest.setItem(0,new ItemStack(FirearmsRegistry.ammo("rifle_round"),7));
        for(int elapsed=60;elapsed<=600;elapsed+=60){final int sample=elapsed;h.runAtTickTime(sample,()->{if(f.primary!=null)org.slf4j.LoggerFactory.getLogger(MilitaryHomeGameTest.class).info("HOME5_SUPPLY_DIAGNOSTIC tick={} {}",sample,supplyDiagnostic(f.primary,at));});}
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.ticking(),"supply base must actually tick"))
            .thenExecute(()->{
                f.primary=f.soldier(false,spawn);var saved=f.primary.saveWithoutId(new CompoundTag());saved.getCompound("MilitaryLoadout").getCompound("Reserves").putInt(((FirearmItem)f.primary.getMainHandItem().getItem()).definition().ammoId(),0);f.primary.load(saved);
                h.assertTrue(f.primary.reserveAmmo()==0,"independent supply fixture starts with an empty actual typed reserve");
                h.assertTrue(f.primary.distanceToSqr(Vec3.atCenterOf(at))>9&&!f.primary.getPersistentData().contains(CityMilitaryService.BASE_TAG),"empty standalone soldier starts outside transfer range and has no native-base shortcut");
                if(selectedDepot==54){
                    h.assertTrue(f.level.getBlockState(at.above(5)).is(Blocks.SMOOTH_STONE)&&f.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,at.getX(),at.getZ())==87,"second actual depot retains its original solid roof and heightmap87");
                    h.assertTrue(new BlockPos(x+54,87,12).equals(MilitaryFormationSystem.standing(f.primary,Vec3.atBottomCenterOf(at))),"generic container-cell standing still points at the original roof; only supply interaction routing may avoid it");
                }
            }).thenWaitUntil(()->{
                h.assertTrue(f.primary.reserveAmmo()==7,"independent soldier must actually bind, walk into the depot, and obtain its seven finite rounds; "+supplyDiagnostic(f.primary,at));
                h.assertTrue(chest.getItem(0).isEmpty()&&f.primary.distanceToSqr(Vec3.atCenterOf(at))<=9,"the chest is debited only after physical arrival");
                if(selectedDepot==54)h.assertTrue(f.primary.getY()<at.getY()+1,"second-depot withdrawal happens beside the ground-level container, not from its roof");
                h.assertTrue(f.id.equals(f.primary.getPersistentData().getString(HOME))&&!f.primary.getPersistentData().contains(CityMilitaryService.BASE_TAG),"supply uses current home without forging origin ownership");
            }).thenExecute(f::close).thenSucceed();
    }

    @GameTest(template="blackwatch_arena",batch="cores5",timeoutTicks=180)
    public static void missingEveryRealCommandTerminalImmediatelyPausesNewIssuance(GameTestHelper h){
        var f=new Fixture(h,50000,false,true);f.destroyCores();
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.ticking(),"destroyed-core base posts must tick"))
            .thenIdle(25).thenExecute(()->{
                CityMilitaryService.maintain(f.level,List.of(f.observer()));
                h.assertTrue(issued(f.base())==0,"all six actual command terminals missing must pause first native issuance during the observation window");
            }).thenExecute(f::close).thenSucceed();
    }

    @GameTest(template="blackwatch_arena",batch="cores5",timeoutTicks=180)
    public static void oneSurvivingRealCommandTerminalKeepsNativeIssuanceOperational(GameTestHelper h){
        var f=new Fixture(h,52000,true,true);var cores=f.cores();for(int i=0;i<5;i++)f.level.destroyBlock(cores.get(i),false);
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.ticking(),"one-core fixture posts must tick"))
            .thenIdle(25).thenExecute(()->{
                h.assertTrue(f.level.getBlockState(cores.get(5)).is(MilitaryFacilityBlocks.COMMAND_TERMINAL.get()),"the sixth original NBT terminal remains real");
                CityMilitaryService.maintain(f.level,List.of(f.observer()));
                h.assertTrue(issued(f.base())==2,"one surviving command terminal keeps the original two-per-pass contract");
            }).thenExecute(f::close).thenSucceed();
    }

    @GameTest(template="blackwatch_arena",batch="cores5",timeoutTicks=420)
    public static void repairedCommandCoreRetainsIssuedUUIDsLossesAndFiniteStore(GameTestHelper h){
        var f=new Fixture(h,54000,false,true);var chest=(ChestBlockEntity)f.level.getBlockEntity(new BlockPos(54052,81,12));chest.unpackLootTable(null);chest.clearContent();chest.setItem(0,new ItemStack(FirearmsRegistry.ammo("rifle_round"),7));
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.ticking(),"repair fixture must tick"))
            .thenExecute(()->{
                CityMilitaryService.maintain(f.level,List.of(f.observer()));h.assertTrue(issued(f.base())==2,"fixture first issues exactly two real soldiers");
                f.nativeIds=f.base().slots.clone();f.level.getEntity(f.nativeIds[0]).discard();f.destroyCores();
            }).thenIdle(225).thenExecute(()->{
                CityMilitaryService.maintain(f.level,List.of(f.observer()));
                h.assertTrue(issued(f.base())==2,"confirmed command-core failure must not fill more original slots");
                f.level.setBlock(f.cores().get(0),MilitaryFacilityBlocks.COMMAND_TERMINAL.get().defaultBlockState(),3);
            }).thenIdle(25).thenExecute(()->{
                CityMilitaryService.maintain(f.level,List.of(f.observer()));
                h.assertTrue(issued(f.base())==4&&f.nativeIds[0].equals(f.base().slots[0])&&f.nativeIds[1].equals(f.base().slots[1]),"repair resumes only never-issued slots and never replaces original UUIDs");
                h.assertTrue((f.base().losses&1)!=0&&f.level.getEntity(f.nativeIds[0])==null&&chest.getItem(0).getCount()==7,"repair retains death ledger and actual seven-round chest instead of rebuilding loot");
                var restored=CityMilitaryData.load(CityMilitaryData.get(f.level).save(new CompoundTag(),f.level.registryAccess()),f.level.registryAccess());
                h.assertTrue(Arrays.equals(restored.bases.get(f.id).slots,f.base().slots)&&(restored.bases.get(f.id).losses&1)!=0,"existing issuance and death accounting survive real SavedData serialization");
            }).thenExecute(f::close).thenSucceed();
    }

    @GameTest(template="blackwatch_arena",batch="homes5Orders",timeoutTicks=650)
    public static void anExplicitUnitMoveOverridesPersistedTwelveBlockFieldPatrol(GameTestHelper h){
        var f=new Fixture(h,56000,false,false);var spawn=new BlockPos(56032,81,32);f.fieldFloor(spawn,44);var target=spawn.offset(32,0,0);
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.level.isPositionEntityTicking(spawn)&&f.level.isPositionEntityTicking(target),"order corridor must actually tick"))
            .thenExecute(()->{
                f.primary=f.soldier(false,spawn);f.primary.setAutomaticUnitJoin(false);var tag=f.primary.getPersistentData();tag.putString(MODE,"field");tag.putLong(FIELD,spawn.asLong());tag.putString(DIM,f.level.dimension().location().toString());
                f.unit=UUID.randomUUID();var org=MilitaryOrganizationSystem.organization(f.level);
                h.assertTrue(org.create(f.unit,MilitaryFaction.MILITARY,MilitaryUnitType.TACTICAL_SQUAD,"home-order",8,new MilitaryOrder.Point(spawn.getX(),spawn.getY(),spawn.getZ()))&&org.assign(f.unit,f.primary.getUUID(),f.primary.rank()),"fixture owns a real explicit military unit");
                f.primary.setUnitId(f.unit);f.order=new MilitaryOrder(MilitaryOrder.Mode.MOVE,new MilitaryOrder.Point(target.getX()+.5,target.getY(),target.getZ()+.5));
                h.assertTrue(org.order(f.unit,f.order),"real order is committed");
            }).thenWaitUntil(()->h.assertTrue(f.primary.getX()>spawn.getX()+20,"explicit unit march must leave the field patrol twelve-block radius"))
            .thenExecute(()->{
                h.assertTrue(f.order.equals(MilitaryOrganizationSystem.organization(f.level).effectiveOrder(f.unit).orElse(null))&&f.primary.unitId().equals(f.unit),"home AI preserves explicit order and roster");
                h.assertTrue(f.primary.getPersistentData().getLong(FIELD)==spawn.asLong(),"temporary marching does not erase the persisted patrol anchor");
            }).thenExecute(f::close).thenSucceed();
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
        return "time="+soldier.level().getGameTime()+" position="+soldier.position()+" reserve="+soldier.reserveAmmo()+" magazine="+soldier.magazineAmmo()+" box_distance="+soldier.distanceToSqr(Vec3.atCenterOf(chest))
            +" box="+((soldier.level().getBlockEntity(chest) instanceof ChestBlockEntity box)?box.getItem(0):"MISSING")+" target="+soldier.getTarget()+" standingAtChest="+com.codex.blacklight.military.MilitaryFormationSystem.standing(soldier,net.minecraft.world.phys.Vec3.atBottomCenterOf(chest))+" chestHeightmap="+soldier.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,chest.getX(),chest.getZ())
            +" running="+soldier.goalSelector.getAvailableGoals().stream().filter(goal->goal.isRunning()).map(goal->goal.getPriority()+":"+goal.getGoal().getClass().getSimpleName()).toList()
            +" supplyRoute="+supplyRouteDiagnostic(soldier)+" navDone="+nav.isDone()+" path="+(path==null?"NONE":path.getTarget()+"/"+path.getNextNodeIndex()+"/"+path.getNodeCount()+"/reachable="+path.canReach())+" metrics="+MilitaryFormationSystem.metrics((ServerLevel)soldier.level())+" "+MilitaryHomeSystem.describe(soldier);
    }

    private static long issued(CityMilitaryData.Base base){return Arrays.stream(base.slots).filter(Objects::nonNull).count();}
    static void cleanupOnFailure(GameTestHelper h,Runnable cleanup){
        try{
            var field=GameTestHelper.class.getDeclaredField("testInfo");field.setAccessible(true);var info=(GameTestInfo)field.get(h);
            info.addListener((GameTestListener)java.lang.reflect.Proxy.newProxyInstance(GameTestListener.class.getClassLoader(),new Class<?>[]{GameTestListener.class},(proxy,method,args)->switch(method.getName()){
                case "testFailed" -> {cleanup.run();yield null;}case "hashCode" -> System.identityHashCode(proxy);case "equals" -> proxy==args[0];case "toString" -> "home5-cleanup";default -> null;
            }));
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot install home QA cleanup before forcing chunks",failure);}
    }
    static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final ServerLevel level;final CityMilitaryPlan.Site site;String id="";final Map<Long,Boolean> forced=new LinkedHashMap<>();final List<Entity> entities=new ArrayList<>();final AtomicBoolean closed=new AtomicBoolean();
        MilitarySoldier primary;int initialReserve;UUID[] nativeIds;UUID unit;MilitaryOrder order;
        Fixture(GameTestHelper h,int x,boolean blackwatch,boolean place){
            this.h=h;level=h.getLevel();site=new CityMilitaryPlan.Site(x,0,80,0,blackwatch);cleanupOnFailure(h,this::close);
            forceRect(x-16,-16,x+96,site.depth()+32);
            if(place){id=CityMilitaryPlacement.place(level,site);for(var at:cores())h.assertTrue(level.getBlockState(at).is(MilitaryFacilityBlocks.COMMAND_TERMINAL.get()),"actual NBT contains each of the six promised command terminals at "+at);}
        }
        void forceRect(int minX,int minZ,int maxX,int maxZ){for(int x=minX>>4;x<=maxX>>4;x++)for(int z=minZ>>4;z<=maxZ>>4;z++){var chunk=new net.minecraft.world.level.ChunkPos(x,z);if(!forced.containsKey(chunk.toLong()))forced.put(chunk.toLong(),level.getForcedChunks().contains(chunk.toLong()));level.setChunkForced(x,z,true);}}
        void fieldFloor(BlockPos around,int radius){forceRect(around.getX()-radius-16,around.getZ()-radius-16,around.getX()+radius+16,around.getZ()+radius+16);for(int x=around.getX()-radius;x<=around.getX()+radius;x++)for(int z=around.getZ()-radius;z<=around.getZ()+radius;z++)if(id.isEmpty()||x<site.x()||x>=site.x()+site.width()||z<site.z()||z>=site.z()+site.depth())level.setBlock(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState(),2);}
        CityMilitaryData.Base base(){return CityMilitaryData.get(level).bases.get(id);}
        Vec3 observer(){return new Vec3(site.x()+site.width()/2.,81,site.z()+site.depth()/2.);}
        List<BlockPos> cores(){var result=new ArrayList<BlockPos>();for(int y:new int[]{2,7})for(int x:new int[]{11,13,15}){int[] at=site.world(x,9);result.add(new BlockPos(at[0],site.floor()+y,at[1]));}return result;}
        void destroyCores(){for(var at:cores()){h.assertTrue(level.destroyBlock(at,false),"the actual command terminal is removed by ordinary world destruction");}}
        boolean ticking(){if(!level.isPositionEntityTicking(new BlockPos(site.x()+32,81,32)))return false;for(var at:cores())if(!level.isPositionEntityTicking(at))return false;return true;}
        MilitarySoldier soldier(boolean blackwatch,BlockPos at){var soldier=(blackwatch?ModEntities.BLACKWATCH_SOLDIER:ModEntities.MILITARY_SOLDIER).get().create(level);soldier.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);h.assertTrue(level.addFreshEntity(soldier),"real independent soldier joins the actual level");entities.add(soldier);soldier.initializeWeapon();return soldier;}
        @Override public void close(){if(!closed.compareAndSet(false,true))return;var base=base();CityMilitaryData.get(level).bases.remove(id);if(base!=null)for(var uuid:base.slots)if(uuid!=null&&level.getEntity(uuid)!=null)level.getEntity(uuid).discard();for(var entity:entities)if(!entity.isRemoved())entity.discard();if(unit!=null)MilitaryOrganizationSystem.organization(level).removeUnit(unit);for(var entry:forced.entrySet()){var chunk=new net.minecraft.world.level.ChunkPos(entry.getKey());level.setChunkForced(chunk.x,chunk.z,entry.getValue());}}
    }
    private MilitaryHomeGameTest(){}
}
