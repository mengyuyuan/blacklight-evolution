package com.codex.blacklight.gametest;

import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModBlocks;
import com.codex.blacklight.world.BuildingInfectionCoreBlock;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class ColonySupplyGameTest {
    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;final BuildingInfectionData data;final BlockPos origin;final BuildingInfectionData.Colony colony;final FakePlayer player;
        final List<BuildingInfectionData.Colony> old;final Set<Long> cleared;final OutbreakData outbreak;final boolean grief,spawns;final net.minecraft.world.Difficulty difficulty;
        Fixture(GameTestHelper h){
            level=h.getLevel();data=BuildingInfectionData.get(level);old=new ArrayList<>(data.colonies);cleared=new LinkedHashSet<>(data.clearedSites);data.colonies.clear();data.clearedSites.clear();
            outbreak=OutbreakData.get(level.getServer());var active=new OutbreakData();active.start(level.getServer().overworld().getGameTime());level.getServer().overworld().getDataStorage().set("blacklight_outbreak",active);
            grief=level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);spawns=level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);difficulty=level.getDifficulty();level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
            origin=h.absolutePos(new BlockPos(5,3,5));for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<=16;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
            for(int x=-4;x<=4;x++)for(int y=0;y<=15;y++)level.setBlock(origin.offset(x,y,1),Blocks.STONE_BRICKS.defaultBlockState(),2);
            level.setBlock(origin,ModBlocks.BUILDING_CORE.get().defaultBlockState().setValue(BuildingInfectionCoreBlock.FACING,Direction.NORTH),3);colony=data.colonies.get(0);
            player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"ColonyFeeder"));player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();player.setPos(Vec3.atBottomCenterOf(origin.north(2)));
        }
        boolean feed(ItemStack stack){player.setItemInHand(InteractionHand.MAIN_HAND,stack);return ColonyFeeding.feed(level,origin,player,InteractionHand.MAIN_HAND);}
        public void close(){colony.dead=true;level.removeBlock(origin,false);for(var mob:level.getEntitiesOfClass(Mob.class,new AABB(origin).inflate(12)))mob.discard();for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(origin).inflate(6)))item.discard();player.getInventory().clearContent();data.colonies.clear();data.colonies.addAll(old);data.clearedSites.clear();data.clearedSites.addAll(cleared);data.setDirty();level.getServer().overworld().getDataStorage().set("blacklight_outbreak",outbreak);level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(grief,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawns,level.getServer());level.getServer().setDifficulty(difficulty,true);}
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="colony_no_food")
    public static void anEmptyActualCoreCannotMatureGrowOrProduceFromWorldTimeAlone(GameTestHelper h){var f=new Fixture(h);h.runAfterDelay(35,()->{try{h.assertTrue(f.colony.activeTicks==0&&f.colony.surfaces.isEmpty()&&f.colony.supply.reserve()==0&&!BuildingInfectionSystem.hatch(f.level,f.colony),"empty real core remains dormant instead of converting time into architecture or mobs");}finally{f.close();}h.succeed();});}

    @GameTest(template="wall_test",timeoutTicks=310,batch="colony_actual_food")
    public static void anActualConsumedFoodComponentDigestsThenPaysForRealFacadeGrowth(GameTestHelper h){
        var f=new Fixture(h);var food=new ItemStack(Items.BEEF,2);h.assertTrue(f.feed(food)&&food.getCount()==1&&f.colony.supply.queued()==1,"the survival player's actual slot loses exactly one meal");
        h.runAfterDelay(150,()->h.assertTrue(f.colony.supply.reserve()==0&&f.colony.activeTicks==0&&f.colony.supply.queued()==1,"stored food cannot fund construction before actual digestion work"));
        h.runAfterDelay(260,()->{try{h.assertTrue(f.colony.supply.queued()==0&&f.colony.activeTicks>0&&!f.colony.surfaces.isEmpty(),"real loaded digestion produces finite nutrition and actual new tissue");h.assertTrue(f.colony.supply.reserve()+f.colony.surfaces.size()<=6,"construction consumes the same six nutrients from the one beef");}finally{f.close();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="colony_remainders")
    public static void aColonyReturnsTheRealSoupBowlAndRejectsStoneAndItsOwnGeneratedBlocks(GameTestHelper h){
        try(var f=new Fixture(h)){h.assertTrue(f.feed(new ItemStack(Items.MUSHROOM_STEW))&&f.player.getMainHandItem().is(Items.BOWL)&&f.colony.supply.queued()==1,"one actual soup yields one actual bowl");var stone=new ItemStack(Items.STONE,3);h.assertTrue(!f.feed(stone)&&stone.getCount()==3,"stone remains unchanged");var tissue=new ItemStack(ModBlocks.TISSUE_ATTACHMENT.get());h.assertTrue(!f.feed(tissue)&&tissue.getCount()==1,"generated colony blocks are not recyclable food");}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=300,batch="colony_real_drops")
    public static void theCoreCanConsumeANearbyExistingDropAndDoesNotCreateFoodOutOfAir(GameTestHelper h){
        var f=new Fixture(h);var p=Vec3.atCenterOf(f.origin.north(2));var drop=new ItemEntity(f.level,p.x,p.y,p.z,new ItemStack(Items.BEEF,3));drop.setNoGravity(true);drop.setDeltaMovement(Vec3.ZERO);f.level.addFreshEntity(drop);
        h.runAfterDelay(35,()->h.assertTrue(drop.getItem().getCount()<3&&f.colony.supply.queued()>0,"actual local mouth consumes one of the existing food items"));
        h.runAfterDelay(260,()->{try{h.assertTrue(!f.colony.surfaces.isEmpty(),"scavenged food eventually grows actual tissue");}finally{f.close();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=95,batch="colony_food_wall")
    public static void theCoreCannotEatThroughARealWall(GameTestHelper h){var f=new Fixture(h);for(int x=-3;x<=3;x++)for(int y=0;y<=3;y++)f.level.setBlock(f.origin.offset(x,y,-1),Blocks.GLASS.defaultBlockState(),3);var p=Vec3.atCenterOf(f.origin.north(2));var drop=new ItemEntity(f.level,p.x,p.y,p.z,new ItemStack(Items.BEEF,3));drop.setNoGravity(true);drop.setDeltaMovement(Vec3.ZERO);f.level.addFreshEntity(drop);h.runAfterDelay(70,()->{try{h.assertTrue(drop.getItem().getCount()==3&&f.colony.supply.queued()==0&&f.colony.surfaces.isEmpty(),"a solid wall blocks the mouth without consuming or converting a protected stack");}finally{f.close();}h.succeed();});}

    @GameTest(template="wall_test",timeoutTicks=290,batch="colony_paid_brood")
    public static void aMatureCorePaysActualDigestedNutrientsForOneStageAppropriateChildAndStopsWhenEmpty(GameTestHelper h){
        var f=new Fixture(h);f.colony.stage=3;f.colony.activeTicks=12000;f.colony.frontier.clear();boolean volume=BuildingInfectionConfig.VOLUME.get();BuildingInfectionConfig.VOLUME.set(false);
        var food=ForgeTestFood.stack(1,6,true);h.assertTrue(f.feed(food)&&food.isEmpty(),"a component-defined meal, including modded food values, is actually consumed");
        h.runAfterDelay(230,()->{try{h.assertTrue(f.colony.supply.reserve()==12&&WorldOutbreakSystem.stage(f.level)==0,"this fresh outbreak fixture isolates twelve nutrients with no construction frontier");// The fresh-outbreak component needs an empty local cohort. Earlier suites
            // can leave native common infected, which legitimately changes the selected brood
            // tier (and its price) even when global time is fresh. Do not alter production quotas.
            if(!(f.level.getServer() instanceof GameTestServer))throw new AssertionError("disposable GameTest server required");
            f.level.getEntitiesOfClass(Mob.class,new AABB(f.origin).inflate(96)).forEach(Mob::discard);
            h.assertTrue(com.codex.blacklight.city.CityEcology.stage(f.level,f.origin)==0,"fresh local outbreak required");
            f.level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,f.level.getServer());h.assertTrue(BuildingInfectionSystem.hatch(f.level,f.colony)&&f.colony.births==1&&f.colony.supply.reserve()==0,"one actual early common infected consumes its full twelve nutrient production cost");h.assertTrue(f.level.getEntitiesOfClass(com.codex.blacklight.entity.BlacklightCommonInfected.class,new AABB(f.origin).inflate(10)).size()==1&&!BuildingInfectionSystem.hatch(f.level,f.colony),"empty reserves cannot create a second child or a premature hunter");}finally{BuildingInfectionConfig.VOLUME.set(volume);f.close();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=75,batch="colony_food_save")
    public static void aColonySaveKeepsPartialDigestionWithoutMaturingAnUnloadedBuilding(GameTestHelper h){var f=new Fixture(h);h.assertTrue(f.feed(new ItemStack(Items.BEEF)),"actual input enters saved digestion");h.runAfterDelay(60,()->{try{var saved=f.data.save(new CompoundTag());var copy=BuildingInfectionData.load(saved).colonies.get(0);h.assertTrue(copy.supply.write().equals(f.colony.supply.write())&&copy.activeTicks==f.colony.activeTicks&&copy.supply.reserve()==0,"remaining food work and paid growth age survive a full colony codec unchanged");}finally{f.close();}h.succeed();});}
    @GameTest(template="wall_test",timeoutTicks=65,batch="colony_mouth_migrate")
    public static void anOldStarvedCoreReopensOnlyItsOwnMouthUsingTheSharedWorkBudget(GameTestHelper h){
        var f=new Fixture(h);var mass=ModBlocks.BUILDING_BIOMASS.get().defaultBlockState().setValue(com.codex.blacklight.world.BuildingBiomassBlock.CORNERS,255);
        for(int y=0;y<2;y++)f.level.setBlock(f.origin.north().above(y),mass,3);f.colony.massBlocks=2;var architecture=f.origin.north(2).east();f.level.setBlock(architecture,Blocks.STONE_BRICKS.defaultBlockState(),3);
        h.runAfterDelay(35,()->{try{h.assertTrue(f.level.getBlockState(f.origin.north()).isAir()&&f.level.getBlockState(f.origin.north().above()).isAir()&&f.colony.massBlocks==0&&f.colony.supply.reserve()==0&&f.colony.activeTicks==0,"old own obstructions clear without feeding or growing the starved building");h.assertTrue(f.level.getBlockState(architecture).is(Blocks.STONE_BRICKS)&&f.feed(new ItemStack(Items.BEEF)),"unrelated architecture stays intact and the real feeding interaction is available again");}finally{f.close();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=65,batch="colony_unpaid_upkeep")
    public static void insufficientLargeColonyMaintenanceCannotBeBypassedBySpendingItsLastMaterialOnGrowth(GameTestHelper h){
        var f=new Fixture(h);var prepared=new CompoundTag();prepared.putInt("Reserve",4);prepared.putInt("Upkeep",200);f.colony.supply=ColonySupply.read(prepared);f.colony.massBlocks=2048;
        h.runAfterDelay(30,()->{try{h.assertTrue(f.colony.activeTicks==0&&f.colony.surfaces.isEmpty()&&f.colony.supply.reserve()==4,"a loaded large-colony ledger owes five nutrients and cannot evade upkeep by constructing with its last four");}finally{f.close();}h.succeed();});
    }
}
