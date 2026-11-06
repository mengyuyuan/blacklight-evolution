package com.codex.blacklight.gametest;

import com.codex.blacklight.infection.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

/** Actual optional entity overrides; NoAI isolates physiology here. Separate
 * framework tests exercise original pursuit, contact and selector cooperation. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class ActualNativePhysiologyGameTest {
    private static final class Fixture {
        final GameTestHelper h;final Mob actor;final NativeTestArena arena;
        final Object navigation,brain,controller;boolean closed;
        Fixture(GameTestHelper h,String id,TissueCore core){
            this.h=h;arena=NativeTestArena.open(h,0,15);
            for(int x=2;x<=12;x++)for(int z=2;z<=12;z++){
                h.setBlock(new BlockPos(x,1,z),Blocks.STONE);for(int y=2;y<10;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
            }
            var key=new ResourceLocation(id);h.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(key),"actual optional type is installed: "+id);
            var entity=BuiltInRegistries.ENTITY_TYPE.get(key).create(h.getLevel());h.assertTrue(entity instanceof Mob,"actual entity is a mob: "+id);actor=(Mob)entity;
            actor.setPos(h.absolutePos(new BlockPos(5,2,5)).getCenter());actor.setNoAi(true);h.getLevel().addFreshEntity(actor);
            navigation=actor.getNavigation();brain=actor.getBrain();controller=actor.getMoveControl();
            h.assertTrue(BlacklightInfectionSystem.infect(actor,null)&&NativeInfection.active(actor),"actual source is infected in place");NativeInfection.tick(actor,h.getLevel());
            var data=actor.getPersistentData().getCompound("BlacklightNativeInfection");data.putLong("Age",72000);data.putString("Core",core.key());data.putInt("Nutrition",120);
            actor.getPersistentData().put("BlacklightNativeInfection",data);actor.tickCount+=20;NativeInfection.tick(actor,h.getLevel());actor.setHealth(actor.getMaxHealth());
            actor.getPersistentData().putString("NativePhysiologyForeignData","preserved");h.runAfterDelay(285,this::close);
        }
        void retained(){h.assertTrue(actor.getNavigation()==navigation&&actor.getBrain()==brain&&actor.getMoveControl()==controller
                &&actor.getPersistentData().getString("NativePhysiologyForeignData").equals("preserved"),"actual source controllers and foreign state remain the original objects/data");}
        void close(){if(closed)return;closed=true;actor.discard();arena.close();}
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="actual_native_born_defense")
    public static void actualBornBodyRunsRealPlateDefenceInsideItsOriginalDamageOverride(GameTestHelper h){
        var f=new Fixture(h,"born_in_chaos_v1:decaying_zombie",TissueCore.OSTEODERM);var attacker=h.spawn(EntityType.PIG,5,2,9);attacker.setNoAi(true);
        try{
            f.actor.yBodyRot=0;attacker.setPos(f.actor.position().add(0,0,-3));f.actor.invulnerableTime=0;
            float health=f.actor.getHealth();f.actor.hurt(f.actor.damageSources().mobAttack(attacker),10);
            h.assertTrue(f.actor.getHealth()<health&&NativeTissueDefense.saved(f.actor).getFloat("Bone")<1,"actual Born override receives a real directional hit and consumes plate integrity");f.retained();h.succeed();
        }finally{attacker.discard();f.close();}
    }
    @GameTest(template="wall_test",timeoutTicks=300,batch="actual_native_spore_digestion")
    public static void actualSporeSourceProcessesItsGenuineAcceptedMealThroughTheSharedDigestiveOrgan(GameTestHelper h){
        var f=new Fixture(h,"spore:inf_human",TissueCore.GIZZARD);var prey=h.spawn(EntityType.PIG,8,2,5);prey.setNoAi(true);NativeTissue.spend(f.actor,NativeTissue.nutrition(f.actor));
        try{prey.hurt(TissueAttackSource.mobAttack(f.actor),10000);h.assertTrue(!prey.isAlive()&&NativeTissue.nutrition(f.actor)==0,"actual Spore actor has an accepted corpse without prepaid nutrition");}
        catch(Throwable failure){prey.discard();f.close();throw failure;}
        h.runAfterDelay(3,()->{try{h.assertTrue(NativeTissueDigestion.saved(f.actor).getList("Portions",10).size()==1,"actual source receives the real timed portion once");}catch(Throwable failure){prey.discard();f.close();throw failure;}});
        h.runAfterDelay(260,()->{try{
            h.assertTrue(NativeTissueDigestion.saved(f.actor).getList("Portions",10).isEmpty()&&NativeTissue.nutrition(f.actor)>0,"actual source completes digestion only through loaded work");f.retained();h.succeed();
        }finally{prey.discard();f.close();}});
    }
    @GameTest(template="wall_test",timeoutTicks=300,batch="actual_native_spore_circulation")
    public static void actualSporeRangedBodyAccumulatesGasExchangeAndPaysForOwnOrganRecovery(GameTestHelper h){
        var f=new Fixture(h,"spore:spitter",TissueCore.HEART);
        h.runAfterDelay(240,()->{try{
            int oxygen=NativeTissueVitals.saved(f.actor).getInt("Oxygen"),food=NativeTissue.nutrition(f.actor);int result=TissueActions.recover(f.actor,100);
            h.assertTrue(oxygen>=18&&result<100&&NativeTissue.nutrition(f.actor)==food-1,"actual foreign ranged body uses shared oxygen/food for organ recovery; oxygen="+oxygen+" recovery="+result);
            f.retained();var originalClass=f.actor.getClass();
            var state=NativeTissueVitals.saved(f.actor);var nbt=new CompoundTag();f.actor.saveWithoutId(nbt);f.actor.load(nbt);
            h.assertTrue(NativeTissueVitals.saved(f.actor).equals(state),"foreign body reload retains paid circulation state without copying foreign capabilities");
            h.assertTrue(f.actor.getClass()==originalClass&&f.actor.isNoAi()&&f.actor.getPersistentData().getString("NativePhysiologyForeignData").equals("preserved"),"source-owned reload retains the native type and foreign data; its normal Brain reconstruction is allowed");h.succeed();
        }finally{f.close();}});
    }

    @GameTest(template="wall_test",timeoutTicks=40,batch="actual_native_item_food")
    public static void actualOptionalBodiesAcceptRealPlayerFoodWithoutReplacingSourceControllers(GameTestHelper h){
        for(int i=0;i<2;i++){
            var f=new Fixture(h,i==0?"born_in_chaos_v1:decaying_zombie":"spore:inf_human",i==0?TissueCore.FERMENT:TissueCore.GIZZARD);
            var player=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"ActualNativeFood"));
            try{
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.getInventory().clearContent();player.setPos(f.actor.position().add(2,0,0));
                var stack=new net.minecraft.world.item.ItemStack(i==0?net.minecraft.world.item.Items.OAK_LEAVES:net.minecraft.world.item.Items.MUSHROOM_STEW,i==0?3:1);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
                var result=player.interactOn(f.actor,net.minecraft.world.InteractionHand.MAIN_HAND);
                var digestion=NativeTissueDigestion.saved(f.actor);
                h.assertTrue(result.consumesAction()&&digestion.getList("Portions",10).size()==1,"actual optional body accepts one real player food portion: "+f.actor.getType());
                h.assertTrue(i==0?stack.getCount()==2:player.getMainHandItem().is(net.minecraft.world.item.Items.BOWL),"actual optional stack debit/container is correct");
                f.retained();var save=new CompoundTag();f.actor.saveWithoutId(save);f.actor.load(save);
                h.assertTrue(NativeTissueDigestion.saved(f.actor).equals(digestion)&&f.actor.getPersistentData().getString("NativePhysiologyForeignData").equals("preserved"),"actual foreign reload preserves stomach and original foreign data with no second food credit");
            }finally{player.getInventory().clearContent();f.close();}
        }
        h.succeed();
    }
}
