package com.codex.blacklight.gametest;

import com.codex.blacklight.config.InfectionEcologyConfig;
import com.codex.blacklight.infection.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class NativeItemFoodGameTest {
    private static final class Fixture {
        final GameTestHelper h;final NativeTestArena arena;final Mob actor;final FakePlayer player;
        final List<? extends String> old;final List<ItemEntity> drops=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h,TissueCore core){
            this.h=h;arena=NativeTestArena.open(h,0,15);old=InfectionEcologyConfig.PRESERVE.get();InfectionEcologyConfig.PRESERVE.set(List.of("minecraft:cow"));
            for(int x=1;x<12;x++)for(int z=1;z<12;z++){
                h.setBlock(new BlockPos(x,1,z),Blocks.STONE);for(int y=2;y<8;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
            }
            actor=h.spawn(EntityType.COW,5,2,5);actor.setNoAi(true);
            h.assertTrue(BlacklightInfectionSystem.infect(actor,null),"food fixture retains original cow");
            var tag=new CompoundTag();tag.putLong("Age",72000);tag.putString("Core",core.key());tag.putInt("Nutrition",0);
            actor.getPersistentData().put("BlacklightNativeInfection",tag);NativeInfection.tick(actor,h.getLevel());
            player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"NativeFoodFixture"));
            player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();player.setPos(actor.position().add(2,0,0));
            h.runAfterDelay(270,this::close);
        }
        InteractionResult interact(ItemStack stack){player.setItemInHand(InteractionHand.MAIN_HAND,stack);return player.interactOn(actor,InteractionHand.MAIN_HAND);}
        int queued(){return TissueDigestion.read(NativeTissueDigestion.saved(actor)).queued();}
        ItemEntity drop(Item item,int count){var entity=new ItemEntity(h.getLevel(),actor.getX(),actor.getY()+.1,actor.getZ(),new ItemStack(item,count));entity.setNoPickUpDelay();entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);h.getLevel().addFreshEntity(entity);drops.add(entity);return entity;}
        void forage(){NativeTissueFeeding.forget(actor);NativeTissueFeeding.tick(actor);}
        void close(){if(closed)return;closed=true;player.getInventory().clearContent();actor.discard();for(var drop:drops)drop.discard();arena.close();InfectionEcologyConfig.PRESERVE.set(old);}
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="native_food_source")
    public static void sourceBreedingAndBabyFeedingKeepTheirConsumptionAndNeverDoubleDebit(GameTestHelper h){
        var f=new Fixture(h,TissueCore.GIZZARD);
        try{
            var stack=new ItemStack(Items.WHEAT,6);
            h.assertTrue(f.interact(stack).consumesAction()&&((Animal)f.actor).isInLove(),"source breeding really runs");
            h.assertTrue(stack.getCount()==5&&f.queued()==1,"actual source item consumption yields exactly one portion and no second debit");
            ((Animal)f.actor).setAge(-24000);h.assertTrue(f.interact(stack).consumesAction()&&stack.getCount()==4&&f.queued()==2,"source baby feeding still works with its own stack use");
            int age=((Animal)f.actor).getAge();f.interact(stack);
            h.assertTrue(stack.getCount()==3&&f.queued()==2&&((Animal)f.actor).getAge()>age,"full Blacklight crop does not cancel source baby growth or fabricate another portion");h.succeed();
        }finally{f.close();}
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="native_food_fallback")
    public static void explicitSourcePassAllowsOneRealPortionAndFullCropStopsFurtherDebits(GameTestHelper h){
        var f=new Fixture(h,TissueCore.GIZZARD);
        try{
            var bread=new ItemStack(Items.BREAD,4);
            h.assertTrue(f.interact(bread).consumesAction()&&bread.getCount()==3&&f.queued()==1,"fallback debits one real item");
            f.interact(bread);h.assertTrue(bread.getCount()==2&&f.queued()==2,"second basic crop slot receives one item");
            h.assertTrue(!f.interact(bread).consumesAction()&&bread.getCount()==2&&f.queued()==2,"full crop leaves real stack untouched");
            var saved=new CompoundTag();f.actor.saveWithoutId(saved);f.actor.load(saved);
            h.assertTrue(f.queued()==2&&bread.getCount()==2,"reload restores food without paying it again");h.succeed();
        }finally{f.close();}
    }
    private static final class Cancel {
        final Entity target;Cancel(Entity target){this.target=target;}
        @SubscribeEvent public void cancel(PlayerInteractEvent.EntityInteract event){if(event.getTarget()==target){event.setCancellationResult(InteractionResult.PASS);event.setCanceled(true);}}
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="native_food_cancel")
    public static void realLoaderCancellationReturningPassCannotBeMistakenForPermission(GameTestHelper h){
        var f=new Fixture(h,TissueCore.GIZZARD);var cancel=new Cancel(f.actor);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(cancel);
        try{
            var bread=new ItemStack(Items.BREAD,3);f.interact(bread);
            h.assertTrue(bread.getCount()==3&&f.queued()==0,"cancelled loader interaction cannot run fallback or produce food");h.succeed();
        }finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);f.close();}
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="native_food_item_fail")
    public static void originalItemFailureCannotBecomeFallbackEvenWhenVanillaFinallyReturnsPass(GameTestHelper h){
        var f=new Fixture(h,TissueCore.GIZZARD);NativeFoodVetoRegistry.CALLS.set(0);
        try{
            var bread=new ItemStack(NativeFoodVetoRegistry.ITEM,3);
            f.interact(bread);h.assertTrue(NativeFoodVetoRegistry.CALLS.get()==1&&bread.getCount()==3&&f.queued()==0,"source item executes once and its veto is retained");h.succeed();
        }finally{f.close();}
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="native_food_remainder")
    public static void actualStewFallbackProducesOnlyItsActualContainer(GameTestHelper h){
        var f=new Fixture(h,TissueCore.GIZZARD);
        try{
            h.assertTrue(f.interact(new ItemStack(Items.MUSHROOM_STEW)).consumesAction()&&f.queued()==1,"real stew enters stomach");
            h.assertTrue(f.player.getMainHandItem().is(Items.BOWL)&&f.player.getMainHandItem().getCount()==1,"one consumed stew leaves one bowl");h.succeed();
        }finally{f.close();}
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="native_food_tags")
    public static void nativeFermentationUsesRealTagsAndMissingOrgansDoNotDigestFibres(GameTestHelper h){
        var f=new Fixture(h,TissueCore.MUSCLE);
        try{
            var leaves=new ItemStack(Items.OAK_LEAVES,3);h.assertTrue(!f.interact(leaves).consumesAction()&&leaves.getCount()==3&&f.queued()==0,"no ferment organ means no leaf meal");
            var data=f.actor.getPersistentData().getCompound("BlacklightNativeInfection");data.putString("Core",TissueCore.FERMENT.key());NativeTissue.forget(f.actor);
            h.assertTrue(f.interact(leaves).consumesAction()&&leaves.getCount()==2&&f.queued()==1,"functional ferment organ uses the actual leaf tag");
            h.assertTrue(NativeTissueDigestion.saved(f.actor).getList("Portions",10).getCompound(0).getString("Kind").equals("ferment"),"ferment input remains its own saved food type");h.succeed();
        }finally{f.close();}
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="native_food_drop_rules")
    public static void contactFoodRespectsPickupDelayOwnerGriefingAndFiniteStomach(GameTestHelper h){
        var f=new Fixture(h,TissueCore.GIZZARD);boolean grief=h.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        try{
            f.actor.setNoAi(false);f.actor.setTarget(null);var nav=f.actor.getNavigation();var control=f.actor.getMoveControl();var drop=f.drop(Items.BREAD,4);
            drop.setDefaultPickUpDelay();f.forage();h.assertTrue(f.queued()==0&&drop.getItem().getCount()==4,"pickup delay blocks scavenging");
            drop.setNoPickUpDelay();drop.setTarget(UUID.randomUUID());f.forage();h.assertTrue(f.queued()==0&&drop.getItem().getCount()==4,"reserved player item is not stolen");
            drop.setTarget(null);h.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,h.getLevel().getServer());f.forage();h.assertTrue(f.queued()==0,"mob-griefing prohibition blocks actual pickup");
            h.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,h.getLevel().getServer());f.forage();h.assertTrue(f.queued()==1&&drop.getItem().getCount()==3,"one real contact meal consumes one stack unit");
            NativeTissueDigestion.offer(f.actor,TissueDigestion.Kind.MEAT,8);f.forage();h.assertTrue(f.queued()==2&&drop.getItem().getCount()==3,"full crop does not consume a further contact item");
            h.assertTrue(nav==f.actor.getNavigation()&&control==f.actor.getMoveControl(),"foraging never replaces source navigation or control");h.succeed();
        }finally{h.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(grief,h.getLevel().getServer());f.close();}
    }
    @GameTest(template="wall_test",timeoutTicks=230,batch="native_food_automatic")
    public static void normalLoadedNativeTickFindsContactFoodWhileSourceGoalRemainsInCharge(GameTestHelper h){
        var f=new Fixture(h,TissueCore.GIZZARD);f.actor.setNoAi(false);var ticks=new AtomicInteger();
        var source=new net.minecraft.world.entity.ai.goal.Goal(){
            {setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
            @Override public boolean canUse(){return true;}
            @Override public boolean requiresUpdateEveryTick(){return true;}
            @Override public void tick(){ticks.incrementAndGet();}
        };
        f.actor.goalSelector.addGoal(0,source);var drop=new java.util.concurrent.atomic.AtomicReference<ItemEntity>();
        h.startSequence().thenWaitUntil(()->h.assertTrue(ticks.get()>5&&f.actor.tickCount>=10,"actual source entity is loaded and its original control is ticking"))
            .thenExecute(()->{drop.set(f.drop(Items.BREAD,4));NativeTissueFeeding.forget(f.actor);})
            .thenIdle(65).thenExecute(()->{try{
                var item=drop.get();
                h.assertTrue(f.queued()>0&&item.getItem().getCount()<4,"normal native loaded ticks consume real stationary contact food; queued="+f.queued()+" count="+item.getItem().getCount()+" actor="+f.actor.position()+" item="+item.position()+" free="+NativeTissueFeeding.free(f.actor)+" target="+f.actor.getTarget()+" tick="+f.actor.tickCount);
                h.assertTrue(ticks.get()>5&&f.actor.goalSelector.getAvailableGoals().stream().anyMatch(w->w.getGoal()==source),"source high-priority control remains live during contact feeding");h.succeed();
            }finally{f.close();}});
    }
}
