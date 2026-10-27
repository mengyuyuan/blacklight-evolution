package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModEntities;
import com.codex.blacklight.registry.ModBlocks;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class TissueVascularGameTest {
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final ServerLevel level;final BuildingInfectionData data;final BlockPos origin;final BuildingInfectionData.Colony colony;
        final List<BuildingInfectionData.Colony> old;final Set<Long> cleared;final OutbreakData outbreak;final boolean grief,spawns,volume;final List<BlacklightInfectedHost> hosts=new ArrayList<>();
        Fixture(GameTestHelper h){
            this.h=h;level=h.getLevel();data=BuildingInfectionData.get(level);old=new ArrayList<>(data.colonies);cleared=new LinkedHashSet<>(data.clearedSites);data.colonies.clear();data.clearedSites.clear();outbreak=OutbreakData.get(level.getServer());var active=new OutbreakData();active.start(level.getServer().overworld().getGameTime());level.getServer().overworld().getDataStorage().set("blacklight_outbreak",active);
            grief=level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);spawns=level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);volume=BuildingInfectionConfig.VOLUME.get();BuildingInfectionConfig.VOLUME.set(false);level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
            for(int x=1;x<=9;x++)for(int z=1;z<=9;z++){h.setBlock(new BlockPos(x,1,z),Blocks.STONE);for(int y=2;y<=8;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);}
            origin=h.absolutePos(new BlockPos(5,2,6));for(int x=1;x<=9;x++)for(int y=2;y<=7;y++)h.setBlock(new BlockPos(x,y,7),Blocks.STONE_BRICKS);
            level.setBlock(origin,ModBlocks.BUILDING_CORE.get().defaultBlockState().setValue(com.codex.blacklight.world.BuildingInfectionCoreBlock.FACING,Direction.NORTH),3);colony=data.colonies.getFirst();colony.frontier.clear();
        }
        BlacklightInfectedHost host(double x,double z,boolean moving){
            var host=ModEntities.INFECTED_HOST.get().create(level);long id=host.getId();host.setUUID(new UUID(id,id^42));host.inherit(EntityType.COW.create(level));var saved=new CompoundTag();host.saveWithoutId(saved);saved.getCompound("BlacklightHost").putString("NativeCore",TissueCore.VASCULAR.key());saved.putInt("BlacklightTissueNutrition",400);host.readAdditionalSaveData(saved);host.advanceTissue(216000);host.setHealth(host.getMaxHealth());host.setNoAi(false);if(!moving)host.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);host.moveTo(h.absolutePos(new BlockPos(0,2,0)).getBottomCenter().add(x,0,z),0,0);host.setOnGround(true);level.addFreshEntity(host);hosts.add(host);return host;
        }
        void start(BlacklightInfectedHost host){h.assertTrue(host.vascular().start(host,colony),"the mature real organ starts its own paid duct");h.assertTrue(host.tissueNutrition()==398,"opening the connection pays two existing nutrients");}
        public void close(){for(var host:hosts)host.discard();colony.dead=true;level.removeBlock(origin,false);data.colonies.clear();data.colonies.addAll(old);data.clearedSites.clear();data.clearedSites.addAll(cleared);data.setDirty();level.getServer().overworld().getDataStorage().set("blacklight_outbreak",outbreak);level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(grief,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawns,level.getServer());BuildingInfectionConfig.VOLUME.set(volume);}
    }
    @GameTest(template="wall_test",timeoutTicks=65,batch="vascular_actual_transfer")
    public static void oneRealConnectionTransfersExistingNutritionOnceAndFullyRetracts(GameTestHelper h){
        var f=new Fixture(h);var host=f.host(5,2,false);f.start(host);
        h.runAfterDelay(9,()->h.assertTrue(f.colony.supply.reserve()==0&&host.vascular().active(),"an extending duct does not transfer before contact"));
        h.runAfterDelay(18,()->{int n=host.vascular().delivered();h.assertTrue(n>0&&f.colony.supply.reserve()==n&&host.tissueNutrition()+n==398,"actual recipient income equals donor loss, with only two nutrients used to open the connection");h.assertTrue(host.tissueInheritance().meals()==0&&f.colony.supply.queued()==0,"transferring digested material does not invent meals or mature captures");});
        h.runAfterDelay(46,()->{try{h.assertTrue(!host.vascular().active()&&host.vascularVisual().isEmpty()&&host.vascular().cooldown()>0,"connection ends after its own full retraction and cooldown");h.assertTrue(f.colony.supply.reserve()==host.vascular().delivered(),"the later part of the action cannot transfer the same reserve again");}finally{f.close();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=125,batch="vascular_actual_ai")
    public static void anIdleMatureCarrierActuallyNavigatesToTheCoreAndSelectsItsOwnConnection(GameTestHelper h){
        var f=new Fixture(h);var host=f.host(1.5,1,true);var initial=host.position();var started=new boolean[1];for(int t=1;t<=95;t++)h.runAfterDelay(t,()->{if(host.vascular().active())started[0]=true;});
        h.runAfterDelay(100,()->{try{h.assertTrue(started[0]&&host.position().distanceToSqr(initial)>.04&&host.vascular().delivered()>0&&f.colony.supply.reserve()>0,"real unforced navigation and AI approach lead to an actual delivery: pos="+host.position()+" nutrition="+host.tissueNutrition()+" delivered="+host.vascular().delivered());}finally{f.close();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="vascular_wall")
    public static void aNewSolidWallCancelsTheConnectionBeforeAnyMaterialCrossesIt(GameTestHelper h){
        var f=new Fixture(h);var host=f.host(5,2,false);f.start(host);h.runAfterDelay(6,()->{for(int x=3;x<=7;x++)for(int y=2;y<=5;y++)h.setBlock(new BlockPos(x,y,4),Blocks.GLASS);});
        h.runAfterDelay(10,()->h.assertTrue(!host.vascular().active()&&host.vascularVisual().contains("CancelledAt")&&host.vascularVisual().getFloat("CancelFrom")>0,"cancellation releases gameplay control but synchronizes a short closing motion from the actual reached length"));
        h.runAfterDelay(18,()->{try{h.assertTrue(!host.vascular().active()&&host.vascularVisual().isEmpty()&&f.colony.supply.reserve()==0&&host.tissueNutrition()==398,"a solid path obstruction interrupts the real paid duct without a remote gift");}finally{f.close();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="vascular_hurt")
    public static void anAcceptedHitInterruptsTheDuctInsteadOfGrantingTransportInvulnerability(GameTestHelper h){
        var f=new Fixture(h);var host=f.host(5,2,false);f.start(host);h.runAfterDelay(6,()->{float old=host.getHealth();h.assertTrue(host.hurt(f.level.damageSources().generic(),4)&&host.getHealth()<old,"the connected organism actually takes an accepted hit");});
        h.runAfterDelay(18,()->{try{h.assertTrue(!host.vascular().active()&&f.colony.supply.reserve()==0&&host.vascular().cooldown()>0,"taking real damage breaks the current delivery and cannot mint material");}finally{f.close();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="vascular_core_removed")
    public static void destroyingTheRealCoreDropsTheDuctWithoutRetargetingItsAlreadyPaidAction(GameTestHelper h){var f=new Fixture(h);var host=f.host(5,2,false);f.start(host);h.runAfterDelay(6,()->f.level.removeBlock(f.origin,false));h.runAfterDelay(18,()->{try{h.assertTrue(f.colony.dead&&!host.vascular().active()&&host.vascularVisual().isEmpty()&&f.colony.supply.reserve()==0,"a removed core cancels the active destination instead of receiving a stale or redirected gift");}finally{f.close();}h.succeed();});}
    @GameTest(template="wall_test",timeoutTicks=60,batch="vascular_save")
    public static void reloadAfterContactKeepsTheDebitedFoodAndCannotRepeatTheCommittedGift(GameTestHelper h){var f=new Fixture(h);var host=f.host(5,2,false);f.start(host);var received=new int[1];h.runAfterDelay(18,()->{received[0]=f.colony.supply.reserve();h.assertTrue(received[0]>0,"the real gift committed before saving");var t=new CompoundTag();host.saveWithoutId(t);int food=host.tissueNutrition();host.readAdditionalSaveData(t);h.assertTrue(host.tissueNutrition()==food&&!host.vascular().active()&&host.vascular().cooldown()>=150,"reload keeps debit and saved cooldown while cancelling a live connection");});h.runAfterDelay(42,()->{try{h.assertTrue(f.colony.supply.reserve()==received[0]&&host.vascularVisual().isEmpty(),"reloading cannot replay a previously committed transfer");}finally{f.close();}h.succeed();});}
    @GameTest(template="wall_test",timeoutTicks=35,batch="vascular_capacity")
    public static void fullDestinationAndLowDonorReserveCannotChargeAnOpeningFee(GameTestHelper h){try(var f=new Fixture(h)){var host=f.host(5,2,false);f.colony.supply.receive(TissueMetabolism.MAX_RESERVE);h.assertTrue(!host.vascular().start(host,f.colony)&&host.tissueNutrition()==400,"a full recipient cannot consume an opening fee");f.colony.supply.pay(TissueMetabolism.MAX_RESERVE);host.spendTissueNutrition(304);h.assertTrue(!host.vascular().start(host,f.colony)&&host.tissueNutrition()==96,"the donor's protected own reserve cannot be donated or spent to open a useless duct");}h.succeed();}
    @GameTest(template="wall_test",timeoutTicks=35,batch="vascular_search_budget")
    public static void manyCarriersShareTheSameBoundedSearchBudgetInOneActualServerTick(GameTestHelper h){try(var f=new Fixture(h)){int started=0;for(int i=0;i<6;i++){var host=f.host(4+(i%2)*2,2+(i/2),false);host.vascular().tick(host);if(host.vascular().active())started++;}h.assertTrue(started>0&&started<=4,"real carrier controllers share four core searches per dimension tick, rather than multiplying a scan per organism: "+started);}h.succeed();}
}
