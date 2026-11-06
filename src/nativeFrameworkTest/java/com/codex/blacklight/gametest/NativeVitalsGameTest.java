package com.codex.blacklight.gametest;

import com.codex.blacklight.config.InfectionEcologyConfig;
import com.codex.blacklight.infection.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class NativeVitalsGameTest {
    private static final class Fixture {
        final GameTestHelper h;final Mob actor;final List<? extends String> old;final NativeTestArena arena;boolean closed;
        Fixture(GameTestHelper h,TissueCore core){
            this.h=h;arena=NativeTestArena.open(h,0,15);old=InfectionEcologyConfig.PRESERVE.get();InfectionEcologyConfig.PRESERVE.set(List.of("minecraft:cow"));
            for(int x=2;x<=7;x++)for(int y=2;y<=6;y++)for(int z=2;z<=7;z++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
            actor=h.spawn(EntityType.COW,4,2,4);actor.setNoAi(true);
            h.assertTrue(BlacklightInfectionSystem.infect(actor,null),"native physiology keeps the original animal");
            var data=new CompoundTag();data.putLong("Age",72000);data.putString("Core",core.key());data.putInt("Nutrition",120);
            actor.getPersistentData().put("BlacklightNativeInfection",data);NativeInfection.tick(actor,h.getLevel());actor.setHealth(actor.getMaxHealth());h.runAfterDelay(170,this::close);
        }
        void submerge(){for(int x=3;x<=5;x++)for(int y=2;y<=5;y++)for(int z=3;z<=5;z++)h.setBlock(new BlockPos(x,y,z),Blocks.WATER);actor.setAirSupply(1);}
        void close(){if(closed)return;closed=true;actor.discard();arena.close();InfectionEcologyConfig.PRESERVE.set(old);}
    }
    @GameTest(template="wall_test",timeoutTicks=100,batch="native_vitals_real_gills")
    public static void nativeGillsReallyBreatheUnderwaterAndSpendFoodWithoutReplacingTheSource(GameTestHelper h){
        var f=new Fixture(h,TissueCore.GILL_LAMELLA);f.submerge();var brain=f.actor.getBrain();var navigation=f.actor.getNavigation();
        h.runAfterDelay(45,()->{try{
            var saved=NativeTissueVitals.saved(f.actor);
            h.assertTrue(f.actor.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)&&f.actor.getAirSupply()>1,"real submerged breathing refills original animal air");
            h.assertTrue(saved.getInt("GillCharge")>0&&NativeTissue.nutrition(f.actor)<120,"gill gas exchange uses real stored nutrition");
            h.assertTrue(f.actor.getHealth()/f.actor.getMaxHealth()>.99&&f.actor.getBrain()==brain&&f.actor.getNavigation()==navigation,"paid breathing keeps health and original brain/navigation; health="+f.actor.getHealth()+"/"+f.actor.getMaxHealth()+" air="+f.actor.getAirSupply()+" state="+NativeTissueVitals.saved(f.actor)+" visual="+NativeTissue.snapshot(f.actor).getCompound("Vitals"));h.succeed();
        }finally{f.close();}});
    }
    @GameTest(template="wall_test",timeoutTicks=160,batch="native_vitals_heart")
    public static void nativeHeartSpendsOxygenAndFoodOnlyOnRecoveryAndPersistsWithoutOfflineWork(GameTestHelper h){
        var f=new Fixture(h,TissueCore.HEART);
        h.runAfterDelay(120,()->{try{
            int oxygen=NativeTissueVitals.saved(f.actor).getInt("Oxygen"),food=NativeTissue.nutrition(f.actor);
            int recovery=TissueActions.recover(f.actor,100);
            h.assertTrue(oxygen>=18&&recovery<100&&recovery>=4&&NativeTissue.nutrition(f.actor)==food-1,"actual air exchange enables paid waiting-period recovery; oxygen="+oxygen+" recovery="+recovery+" food="+food+" after="+NativeTissue.nutrition(f.actor)+" state="+NativeTissueVitals.saved(f.actor)+" visual="+NativeTissue.snapshot(f.actor).getCompound("Vitals"));
            h.assertTrue(NativeTissueVitals.saved(f.actor).getInt("Oxygen")==oxygen-18,"recovery consumes shared oxygen instead of speeding source animation frames");
            f.actor.invulnerableTime=0;f.actor.hurt(f.actor.damageSources().generic(),4);
            h.assertTrue(NativeTissueVitals.saved(f.actor).getFloat("Strain")>0,"real accepted health damage creates circulatory strain");
            var before=NativeTissueVitals.saved(f.actor);var full=new CompoundTag();f.actor.saveWithoutId(full);f.actor.load(full);
            h.assertTrue(NativeTissueVitals.saved(f.actor).equals(before),"in-place native reload restores oxygen/strain with no offline advancement");h.succeed();
        }finally{f.close();}});
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="native_vitals_lung_capacity")
    public static void nativeLungCapacityIsAnOwnedAugmentationAndCureRestoresTheOriginal(GameTestHelper h){
        var f=new Fixture(h,TissueCore.LUNG);
        try{
            int capacity=f.actor.getMaxAirSupply();h.assertTrue(capacity>300&&capacity<=24000,"functional lung expands the original base air capacity");
            var saved=new CompoundTag();f.actor.saveWithoutId(saved);f.actor.load(saved);h.assertTrue(f.actor.getMaxAirSupply()==capacity,"save/reload cannot compound lung capacity");
            f.actor.removeEffect(com.codex.blacklight.registry.ModEffects.BLACKLIGHT_INFECTION);NativeInfection.clear(f.actor);
            h.assertTrue(f.actor.getMaxAirSupply()==300&&NativeTissueVitals.saved(f.actor).isEmpty(),"cure restores base capacity without retaining owned physiology");h.succeed();
        }finally{f.close();}
    }
    @GameTest(template="wall_test",timeoutTicks=100,batch="native_vitals_external_breathing")
    public static void originalWaterBreathingProtectionRemainsEffectiveWithoutInventedGillCharges(GameTestHelper h){
        var f=new Fixture(h,TissueCore.HEART);f.actor.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WATER_BREATHING,200));f.submerge();
        h.runAfterDelay(45,()->{try{
            h.assertTrue(f.actor.getAirSupply()>=1&&f.actor.getHealth()/f.actor.getMaxHealth()>.99,"source/vanilla water protection still controls real breathing; health="+f.actor.getHealth()+"/"+f.actor.getMaxHealth()+" air="+f.actor.getAirSupply()+" state="+NativeTissueVitals.saved(f.actor));
            h.assertTrue(NativeTissueVitals.saved(f.actor).getInt("GillCharge")==0&&NativeTissue.snapshot(f.actor).getCompound("Vitals").getString("Route").equals("EXTERNAL"),"external protection is recorded without fabricating a gill organ");h.succeed();
        }finally{f.close();}});
    }
}
