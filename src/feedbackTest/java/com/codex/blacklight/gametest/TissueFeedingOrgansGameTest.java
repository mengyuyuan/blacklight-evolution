package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModEntities;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class TissueFeedingOrgansGameTest {
    private static BlacklightInfectedHost actor(GameTestHelper h,TissueCore core,boolean water){
        for(int x=1;x<=9;x++)for(int z=1;z<=9;z++){h.setBlock(new BlockPos(x,1,z),Blocks.STONE);for(int y=2;y<=6;y++)h.setBlock(new BlockPos(x,y,z),water&&y<6?Blocks.WATER:Blocks.AIR);}
        var host=ModEntities.INFECTED_HOST.get().create(h.getLevel());host.inherit((water?EntityType.COD:EntityType.COW).create(h.getLevel()));
        var tag=new CompoundTag();host.saveWithoutId(tag);tag.getCompound("BlacklightHost").putString("NativeCore",core.key());tag.putInt("BlacklightTissueNutrition",water?0:100);host.readAdditionalSaveData(tag);host.advanceTissue(216000);
        host.setNoAi(true);host.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);host.moveTo(h.absolutePos(new BlockPos(4,water?3:2,5)).getBottomCenter(),-90,0);host.setOnGround(!water);h.getLevel().addFreshEntity(host);return host;
    }
    private static Mob prey(GameTestHelper h,BlacklightInfectedHost host){
        var target=EntityType.HUSK.create(h.getLevel());target.setPersistenceRequired();target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);target.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        target.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);target.moveTo(host.position().add(1.8,0,0));h.getLevel().addFreshEntity(target);return target;
    }
    private static void start(GameTestHelper h,BlacklightInfectedHost host,Mob target){
        host.setNoAi(false);host.setTarget(target);int food=host.tissueNutrition();h.assertTrue(host.organAbilities().tryStart(target)&&host.organActionCore()==TissueCore.EXTRAORAL,"the real gland begins a contact action");
        h.assertTrue(host.tissueNutrition()==food-4,"one enzyme action pays four real nutrients up front");
    }
    @GameTest(template="wall_test",timeoutTicks=70,batch="extraoral_contact")
    public static void extraoralGlandKeepsRealContactPulsesTwiceAndReleases(GameTestHelper h){
        var host=actor(h,TissueCore.EXTRAORAL,false);var target=prey(h,host);start(h,host,target);var first=new float[]{1000};
        h.runAfterDelay(18,()->h.assertTrue(target.getHealth()==1000,"the paid windup cannot damage a prey before contact"));
        h.runAfterDelay(23,()->{h.assertTrue(TissueGrapples.owner(target)==host&&target.getHealth()<1000,"the first dose requires an owned real contact");first[0]=target.getHealth();});
        h.runAfterDelay(34,()->h.assertTrue(target.getHealth()<first[0],"a later simulation-time enzyme pulse inflicts real damage"));
        h.runAfterDelay(39,()->{h.assertTrue(TissueGrapples.owner(target)==null,"the oral seal releases during retraction");host.setTarget(null);target.setPos(target.position().add(5,0,0));});
        h.runAfterDelay(58,()->{try{h.assertTrue(host.organActionCore()==null&&!target.isNoAi()&&!target.isPassenger(),"recovery ends without permanent AI or riding changes");}finally{host.discard();target.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="extraoral_obstruction")
    public static void aWallBreaksTheOralSealAndStopsFurtherEnzymeDamage(GameTestHelper h){
        var host=actor(h,TissueCore.EXTRAORAL,false);var target=prey(h,host);start(h,host,target);
        h.runAfterDelay(23,()->{
            h.assertTrue(target.getHealth()<1000,"the initial dose actually hit before obstruction");float before=target.getHealth();
            // Leave room for an actual wall: a contact-distance block would suffocate the prey.
            target.setPos(h.absolutePos(new BlockPos(7,2,5)).getBottomCenter());
            h.assertTrue(TissueGrapples.owner(target)==host,"the unobstructed claim remains valid before the wall is inserted");
            for(int z=3;z<=7;z++)for(int y=2;y<=6;y++)h.setBlock(new BlockPos(6,y,z),Blocks.STONE);
            h.runAfterDelay(14,()->{try{h.assertTrue(TissueGrapples.owner(target)==null&&target.getHealth()==before,"the second dose cannot cross the newly placed wall; before="+before+" now="+target.getHealth()+" host="+host.position()+" target="+target.position()+" los="+host.hasLineOfSight(target)+" owner="+TissueGrapples.owner(target));}finally{host.discard();target.discard();}h.succeed();});
        });
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="extraoral_kill")
    public static void lethalContactQueuesOneActualMealWithoutAwardingMatureCaptureCredit(GameTestHelper h){
        var host=actor(h,TissueCore.EXTRAORAL,false);var target=prey(h,host);target.setHealth(1);
        boolean loot=h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(false,h.getLevel().getServer());
        try{start(h,host,target);}catch(Throwable ex){h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(loot,h.getLevel().getServer());throw ex;}
        h.runAfterDelay(24,()->{try{
            h.assertTrue(!target.isAlive()&&host.digestion().queued()==1&&host.tissueNutrition()<100,"the owned lethal dose puts one meal into digestion; ordinary item drops are disabled only in this fixture to exclude a separate legitimate scavenged meal");
            host.killedEntity(h.getLevel(),target);h.assertTrue(host.digestion().queued()==1&&host.tissueInheritance().meals()==0,"one death cannot pay twice or become a fake mature organ capture");
            h.assertTrue(host.organActionCore()==TissueCore.EXTRAORAL&&host.organActionElapsed(0)>=TissueOrganCycle.cycle(TissueCore.EXTRAORAL).retract(),"a dead prey begins synchronized closing instead of popping the mouth shut");
        }finally{h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(loot,h.getLevel().getServer());}});
        h.runAfterDelay(42,()->{try{h.assertTrue(host.organActionCore()==null,"the paid kill action finishes its full closing phase");}finally{host.discard();target.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=450,batch="gill_filter_input")
    public static void realWaterAndFilterableItemsAreRequiredAndThePortionActuallyDigests(GameTestHelper h){
        var host=actor(h,TissueCore.GILL_RAKER,true);
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"RakerFixture"));player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();player.setPos(host.position().add(2,0,0));
        h.runAfterDelay(3,()->{
            var kelp=new ItemStack(Items.KELP,4);player.setItemInHand(InteractionHand.MAIN_HAND,kelp);
            h.assertTrue(host.isInWater()&&host.interact(player,InteractionHand.MAIN_HAND).consumesAction()&&kelp.getCount()==3,"a real submerged filter consumes exactly one tagged aquatic input");
            h.assertTrue(host.tissueNutrition()==0,"filtering first stores a portion, not immediate nutrients");
        });
        h.succeedWhen(()->{h.assertTrue(host.tickCount>20&&host.digestion().queued()==0&&host.tissueNutrition()>0,"the filtered item completes actual loaded digestion ticks");host.discard();});
    }
    @GameTest(template="wall_test",timeoutTicks=35,batch="gill_filter_dry")
    public static void aDryRakerCannotConvertArbitraryItemsOrAirIntoFood(GameTestHelper h){
        var host=actor(h,TissueCore.GILL_RAKER,false);
        h.runAfterDelay(3,()->{try{h.assertTrue(!host.isInWater()&&TissueFeeding.food(host,new ItemStack(Items.KELP))==null&&TissueFeeding.food(host,new ItemStack(Items.STONE))==null,"a dry sieve cannot filter kelp or stone");h.assertTrue(host.tissueNutrition()<=100&&host.digestion().queued()==0,"idle time does not produce food");}finally{host.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=90,batch="gill_filter_scavenge")
    public static void waterFilteringConsumesAnExistingSubmergedDropWithoutSpawningResources(GameTestHelper h){
        var host=actor(h,TissueCore.GILL_RAKER,true);var drop=new ItemEntity(h.getLevel(),host.getX()+1.8,host.getY(),host.getZ(),new ItemStack(Items.KELP,3));drop.setNoGravity(true);drop.setDeltaMovement(Vec3.ZERO);h.getLevel().addFreshEntity(drop);
        h.runAfterDelay(3,()->{h.assertTrue(drop.isInWater(),"the fixture is an actual submerged drop");host.setNoAi(false);host.setTarget(null);TissueFeeding.scavenge(host,0);});
        h.runAfterDelay(35,()->{try{h.assertTrue(drop.getItem().getCount()<3&&host.digestion().queued()>0&&host.tissueInheritance().meals()==0,"the raker consumes a real waterborne item and does not mint capture milestones");}finally{host.discard();drop.discard();}h.succeed();});
    }
}
