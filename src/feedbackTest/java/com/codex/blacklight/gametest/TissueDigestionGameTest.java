package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModEntities;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class TissueDigestionGameTest {
    private static BlacklightInfectedHost host(GameTestHelper h,TissueCore core){
        for(int x=1;x<=9;x++)for(int z=1;z<=9;z++){h.setBlock(new BlockPos(x,1,z),Blocks.STONE);for(int y=2;y<10;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);}
        var host=ModEntities.INFECTED_HOST.get().create(h.getLevel());host.inherit(EntityType.COW.create(h.getLevel()));
        var saved=new CompoundTag();host.saveWithoutId(saved);saved.getCompound("BlacklightHost").putString("NativeCore",core.key());saved.putInt("BlacklightTissueNutrition",0);host.readAdditionalSaveData(saved);
        host.advanceTissue(216000);host.setNoAi(true);host.setPos(h.absolutePos(new BlockPos(5,2,5)).getBottomCenter());h.getLevel().addFreshEntity(host);return host;
    }
    private static FakePlayer player(GameTestHelper h,BlacklightInfectedHost host){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"DigestiveFixture"));p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.setPos(host.position().add(2,0,0));return p;
    }
    private static boolean feed(BlacklightInfectedHost host,FakePlayer player,ItemStack stack){
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);return host.interact(player,InteractionHand.MAIN_HAND).consumesAction();
    }
    private static ItemStack customFood(int count,int nutrition){
        var stack=new ItemStack(Items.PAPER,count);stack.set(DataComponents.FOOD,new FoodProperties.Builder().nutrition(nutrition).saturationModifier(0).build());return stack;
    }
    @GameTest(template="wall_test",timeoutTicks=25,batch="digestive_input")
    public static void gizzardUsesRealFoodTagsAndNeverConsumesStoneOrFullCropInput(GameTestHelper h){
        var host=host(h,TissueCore.GIZZARD);var p=player(h,host);var stone=new ItemStack(Items.STONE,8);
        h.assertTrue(!feed(host,p,stone)&&stone.getCount()==8,"stone is not nutrition");
        var wheat=new ItemStack(Items.WHEAT,8);h.assertTrue(feed(host,p,wheat)&&wheat.getCount()==7,"one tagged grain is actually consumed");
        h.assertTrue(feed(host,p,wheat)&&wheat.getCount()==6,"the second basic crop slot accepts one portion");
        h.assertTrue(!feed(host,p,wheat)&&wheat.getCount()==6&&host.digestion().queued()==2,"a full crop leaves the original stack untouched");
        h.assertTrue(host.tissueNutrition()==0&&host.tissueInheritance().meals()==0,"eating does not instantly pay nutrition or mature capture milestones");host.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=650,batch="digestive_grinding")
    public static void gizzardEventuallyGrindsAnActualWheatPortionIntoUsableNutrition(GameTestHelper h){
        var host=host(h,TissueCore.GIZZARD);var p=player(h,host);h.assertTrue(feed(host,p,new ItemStack(Items.WHEAT)),"real interaction accepts wheat");
        h.runAfterDelay(80,()->h.assertTrue(host.tissueNutrition()==0&&host.digestion().remainingWork()>0,"coarse food is still being ground, not instantly awarded"));
        h.succeedWhen(()->{
            h.assertTrue(host.digestion().queued()==0&&host.tissueNutrition()>0,"actual loaded entity ticks finish the grinder and pay nutrition");
            h.assertTrue(host.digestiveVisual().getInt("Queued")==0,"the server publishes the real empty crop");host.discard();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=3200,batch="digestive_fermentation")
    public static void fermentationOfRealLeavesIsSlowAndFinishesThroughTheActualMetabolism(GameTestHelper h){
        var host=host(h,TissueCore.FERMENT);var p=player(h,host);h.assertTrue(feed(host,p,new ItemStack(Items.OAK_LEAVES)),"the actual server tag permits leaf matter");
        h.runAfterDelay(200,()->h.assertTrue(host.tissueNutrition()==0&&host.digestion().queued()==1,"fermentation is slower than an ordinary meal"));
        h.succeedWhen(()->{h.assertTrue(host.digestion().queued()==0&&host.tissueNutrition()>0,"the loaded fermentation chamber eventually produces a real meal");host.discard();});
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="digestive_storage")
    public static void cropStoresMoreActualPortionsAndReturnsExactlyOneSoupBowl(GameTestHelper h){
        var host=host(h,TissueCore.CROP);var p=player(h,host);int capacity=host.digestiveProfile().capacity();h.assertTrue(capacity>2,"a working crop increases storage capacity");
        h.assertTrue(feed(host,p,new ItemStack(Items.MUSHROOM_STEW)),"soup is real edible input");
        h.assertTrue(p.getMainHandItem().is(Items.BOWL)&&p.getMainHandItem().getCount()==1,"the real consumed soup returns exactly one bowl");
        var food=customFood(capacity+1,3);for(int i=1;i<capacity;i++)h.assertTrue(feed(host,p,food),"each additional crop slot stores an actual item");
        int before=food.getCount();h.assertTrue(!feed(host,p,food)&&food.getCount()==before,"the expanded crop still has a real limit");
        var saved=new CompoundTag();host.saveWithoutId(saved);var clone=ModEntities.INFECTED_HOST.get().create(h.getLevel());clone.load(saved);
        h.assertTrue(clone.digestion().queued()==capacity&&clone.tissueNutrition()==host.tissueNutrition(),"saved portions survive without giving an immediate reload reward");clone.discard();host.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=350,batch="digestive_efficiency")
    public static void absorptiveGutImprovesActualComponentFoodInsteadOfRequiringASpeciesOrItemWhitelist(GameTestHelper h){
        // The 10-block template owns its entity-ticking chunks. Keep both live subjects inside it.
        var plain=host(h,TissueCore.MUSCLE);var improved=host(h,TissueCore.ABSORPTIVE);improved.setPos(plain.position().add(3,0,0));
        var p=player(h,plain);h.assertTrue(feed(plain,p,customFood(1,10)),"an unknown item's real FOOD component is recognized");p.setPos(improved.position().add(2,0,0));h.assertTrue(feed(improved,p,customFood(1,10)),"the same component reaches the enhanced gut");
        h.succeedWhen(()->{
            h.assertTrue(plain.digestion().queued()==0&&improved.digestion().queued()==0,"both actual meals finish; plain="+plain.digestion().write()+" alive="+plain.isAlive()+" removed="+plain.isRemoved()+" improved="+improved.digestion().write()+" alive="+improved.isAlive()+" removed="+improved.isRemoved());
            h.assertTrue(improved.tissueNutrition()>plain.tissueNutrition(),"absorption pays extra only for a real processed portion; plain="+plain.tissueNutrition()+" improved="+improved.tissueNutrition());
            plain.discard();improved.discard();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=300,batch="digestive_enzyme")
    public static void pancreaticFoodProcessingSpendsRealReserveAndKeepsItsUnusedEnzymes(GameTestHelper h){
        var host=host(h,TissueCore.PANCREAS);var saved=new CompoundTag();host.saveWithoutId(saved);saved.putInt("BlacklightTissueNutrition",1);host.readAdditionalSaveData(saved);
        var p=player(h,host);h.assertTrue(feed(host,p,new ItemStack(Items.BEEF)),"a real meat portion is accepted");
        h.runAfterDelay(25,()->{
            h.assertTrue(host.tissueNutrition()==0&&host.digestion().enzymes()>0,"one real stored unit created an enzyme batch");
            var partial=new CompoundTag();host.saveWithoutId(partial);var clone=ModEntities.INFECTED_HOST.get().create(h.getLevel());clone.load(partial);
            h.assertTrue(clone.digestion().enzymes()==host.digestion().enzymes()&&Math.abs(clone.digestion().remainingWork()-host.digestion().remainingWork())<.001,"enzymes and processing time survive the entity codec");clone.discard();
        });
        h.succeedWhen(()->{h.assertTrue(host.digestion().queued()==0&&host.tissueNutrition()>0,"the enzyme-processed meat actually replenishes nutrition");host.discard();});
    }
    @GameTest(template="wall_test",timeoutTicks=400,batch="digestive_lipid")
    public static void fatBodyStoresActualMealOverflowAndLaterReleasesItsSavedEnergy(GameTestHelper h){
        var host=host(h,TissueCore.FAT);var saved=new CompoundTag();host.saveWithoutId(saved);saved.putInt("BlacklightTissueNutrition",2398);host.readAdditionalSaveData(saved);
        var p=player(h,host);h.assertTrue(feed(host,p,customFood(1,10)),"overflow begins as an actual portion");
        h.runAfterDelay(220,()->{
            h.assertTrue(host.digestion().fat()>0,"processed meal overflow became saved lipid tissue");int before=host.digestion().fat();
            var depleted=new CompoundTag();host.saveWithoutId(depleted);depleted.putInt("BlacklightTissueNutrition",0);host.readAdditionalSaveData(depleted);
            h.runAfterDelay(45,()->{try{h.assertTrue(host.digestion().fat()<before&&host.tissueNutrition()>0,"depleted reserve receives energy from the saved fat, not time alone");}finally{host.discard();}h.succeed();});
        });
    }
    @GameTest(template="wall_test",timeoutTicks=90,batch="digestive_scavenging")
    public static void aHungryLoadedHostCanScavengeARealNearbyEdibleStack(GameTestHelper h){
        var host=host(h,TissueCore.GIZZARD);host.setNoAi(false);host.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
        var drop=new ItemEntity(h.getLevel(),host.getX()+.6,host.getY(),host.getZ(),new ItemStack(Items.WHEAT,3));drop.setNoGravity(true);drop.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);h.getLevel().addFreshEntity(drop);
        h.runAfterDelay(70,()->{try{h.assertTrue((!drop.isAlive()||drop.getItem().getCount()<3)&&host.digestion().queued()>0,"a local real item was eaten and is being processed");h.assertTrue(host.tissueInheritance().meals()==0,"scavenging never counts as a mature organ capture");}finally{drop.discard();host.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=25,batch="digestive_claim")
    public static void feedingCannotBypassAnOccupiedCombatAction(GameTestHelper h){
        var host=host(h,TissueCore.GIZZARD);var p=player(h,host);host.setNoAi(false);host.setOrganAction(TissueCore.CLUB,p.getId(),net.minecraft.world.phys.Vec3.ZERO);
        var food=new ItemStack(Items.WHEAT,4);h.assertTrue(!feed(host,p,food)&&food.getCount()==4&&host.digestion().queued()==0,"a busy attack mouth cannot consume another player's food");host.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="digestive_owned_kill")
    public static void realOwnedKillsEnterTheGutOnceWithoutInventingMatureCaptures(GameTestHelper h){
        var host=host(h,TissueCore.GIZZARD);var prey=EntityType.COW.create(h.getLevel());prey.setNoAi(true);prey.setPos(host.position().add(1,0,0));h.getLevel().addFreshEntity(prey);
        try{
            host.killedEntity(h.getLevel(),prey);h.assertTrue(host.digestion().queued()==0,"a live target is not a meal");
            prey.hurt(prey.damageSources().mobAttack(host),10000);
            h.assertTrue(!prey.isAlive()&&host.digestion().queued()==1&&host.tissueNutrition()==0,"an accepted actual kill queues a portion instead of instantly paying energy");
            host.killedEntity(h.getLevel(),prey);h.assertTrue(host.digestion().queued()==1&&host.tissueInheritance().meals()==0,"repeated kill callbacks do not duplicate food or count as mature organ captures");
        }finally{host.discard();prey.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="digestive_dimension_budget")
    public static void multipleHostsShareOneRealScavengingQueryBudget(GameTestHelper h){
        var hosts=new ArrayList<BlacklightInfectedHost>();for(int i=0;i<9;i++)hosts.add(host(h,TissueCore.GIZZARD));
        var first=hosts.getFirst();var drop=new ItemEntity(h.getLevel(),first.getX()+.6,first.getY(),first.getZ(),new ItemStack(Items.WHEAT,64));drop.setNoGravity(true);drop.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);h.getLevel().addFreshEntity(drop);
        h.runAfterDelay(1,()->{try{
            long deferred=0;for(var host:hosts){host.setNoAi(false);deferred=TissueFeeding.scavenge(host,0);}
            h.assertTrue(hosts.stream().mapToInt(host->host.digestion().queued()).sum()==8&&drop.getItem().getCount()==56,"nine hosts share eight actual queries and exactly eight consumed items in the same dimension tick");
            h.assertTrue(deferred>h.getLevel().getGameTime(),"the ninth query is deferred instead of opening a private budget");
        }finally{for(var host:hosts)host.discard();drop.discard();}h.succeed();});
    }
}
