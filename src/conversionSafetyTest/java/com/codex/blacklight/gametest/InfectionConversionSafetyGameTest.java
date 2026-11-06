package com.codex.blacklight.gametest;

import com.codex.blacklight.config.GameplayBalanceConfig;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModAttachments;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.common.MinecraftForge;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class InfectionConversionSafetyGameTest {
    private static Cow ready(GameTestHelper h,Cow cow,String tag){
        cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(h.absolutePos(new BlockPos(5,130,5)).getCenter());cow.addTag(tag);cow.setPersistenceRequired();h.getLevel().addFreshEntity(cow);
        h.assertTrue(BlacklightInfectionSystem.infect(cow,null),"source really becomes infected");
        ModAttachments.setData(cow,ModAttachments.INFECTION_START,-1L);return cow;
    }
    private static void automatic(Mob mob){BlacklightInfectionSystem.onInfectedMobTick(new com.codex.blacklight.combat.ForgeEntityTickEvent.Post(mob));}
    private static void direct(Mob mob)throws Exception{
        var method=BlacklightInfectionSystem.class.getDeclaredMethod("convertHost",Mob.class,ServerLevel.class);method.setAccessible(true);
        try{method.invoke(null,mob,(ServerLevel)mob.level());}catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof Error fatal)throw fatal;throw e;}
    }
    @GameTest(template="wall_test",timeoutTicks=100,batch="conversion_safety_burst")
    public static void ninetySixSimultaneousConversionsRespectOneTickBudgetAndEventuallyFinish(GameTestHelper h){
        int previous=GameplayBalanceConfig.CONVERSION_PER_TICK.get();GameplayBalanceConfig.CONVERSION_PER_TICK.set(4);
        String tag="conversion-burst-"+UUID.randomUUID();var originals=new ArrayList<Cow>();var replacements=new ArrayList<BlacklightInfectedHost>();
        Consumer<EntityJoinLevelEvent> observer=e->{if(e.getLevel()==h.getLevel()&&e.getEntity() instanceof BlacklightInfectedHost host&&host.getTags().contains(tag))replacements.add(host);};
        MinecraftForge.EVENT_BUS.addListener(observer);
        Runnable close=()->{MinecraftForge.EVENT_BUS.unregister(observer);originals.forEach(Entity::discard);replacements.forEach(Entity::discard);GameplayBalanceConfig.CONVERSION_PER_TICK.set(previous);};
        try{
            for(int i=0;i<96;i++){var cow=ready(h,EntityType.COW.create(h.getLevel()),tag);var center=h.absolutePos(new BlockPos(5,130,5));cow.setPos((center.getX()&~15)+2+(i%4)*3,center.getY()+(i/16)*3,(center.getZ()&~15)+2+((i/4)%4)*3);originals.add(cow);}
            for(var mob:originals)automatic(mob);
            h.assertTrue(replacements.size()<=4&&replacements.size()>0,"a ready cohort cannot all serialize and publish in one tick: "+replacements.size());
            h.runAfterDelay(2,()->{try{h.assertTrue(replacements.size()<=12,"real subsequent world ticks retain the shared budget");}catch(Throwable failure){close.run();throw failure;}});
            h.runAfterDelay(40,()->{try{h.assertTrue(replacements.size()==96&&originals.stream().allMatch(Entity::isRemoved),"deferred sources eventually complete without loss or starvation: "+replacements.size());h.succeed();}finally{close.run();}});
        }catch(Throwable failure){close.run();throw failure;}
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="conversion_safety_dimensions")
    public static void differentDimensionsShareTheSameServerQuota(GameTestHelper h){
        h.runAfterDelay(1,()->{
            int previous=GameplayBalanceConfig.CONVERSION_PER_TICK.get();GameplayBalanceConfig.CONVERSION_PER_TICK.set(2);
            try{var other=h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);h.assertTrue(other!=null,"real second server dimension exists");
                h.assertTrue(InfectionConversionWork.acquire(h.getLevel())&&InfectionConversionWork.acquire(other),"both dimensions use their first shared slots");
                h.assertTrue(!InfectionConversionWork.acquire(h.getLevel())&&!InfectionConversionWork.acquire(other),"switching dimensions cannot multiply the budget");h.succeed();
            }finally{GameplayBalanceConfig.CONVERSION_PER_TICK.set(previous);}
        });
    }
    private static final class BrokenSaveCow extends Cow {
        boolean broken=true,fatal;int saves;
        BrokenSaveCow(ServerLevel level){super(EntityType.COW,level);}
        @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);saves++;if(broken){if(fatal)throw new OutOfMemoryError("QA sentinel, no memory exhaustion");throw new IllegalStateException("QA third-party save failure");}}
    }
    @GameTest(template="wall_test",timeoutTicks=80,batch="conversion_safety_prepare")
    public static void failedNativeSaveKeepsOriginalGearAndCanRetryAfterTheCooldown(GameTestHelper h)throws Exception{
        int previous=GameplayBalanceConfig.CONVERSION_RETRY_TICKS.get();GameplayBalanceConfig.CONVERSION_RETRY_TICKS.set(20);
        var source=new BrokenSaveCow(h.getLevel());String tag="conversion-bad-save-"+UUID.randomUUID();ready(h,source,tag);source.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));
        var replacements=new ArrayList<BlacklightInfectedHost>();Consumer<EntityJoinLevelEvent> observer=e->{if(e.getEntity() instanceof BlacklightInfectedHost host&&host.getTags().contains(tag))replacements.add(host);};MinecraftForge.EVENT_BUS.addListener(observer);
        Runnable close=()->{MinecraftForge.EVENT_BUS.unregister(observer);source.discard();replacements.forEach(Entity::discard);GameplayBalanceConfig.CONVERSION_RETRY_TICKS.set(previous);};
        try{automatic(source);h.assertTrue(source.isAlive()&&!source.isRemoved()&&source.getMainHandItem().is(Items.DIAMOND_SWORD)&&replacements.isEmpty(),"preparation failure cannot remove the original or publish copied gear");
            int calls=source.saves;h.assertTrue(calls==1,"fault was really reached in the external save hook");automatic(source);h.assertTrue(source.saves==calls,"same-tick and cooldown callbacks do not retry serialization");
            source.broken=false;var saved=new CompoundTag();source.saveWithoutId(saved);var reloaded=EntityType.COW.create(h.getLevel());reloaded.load(saved);
            h.assertTrue(reloaded.getPersistentData().getLong("BlacklightConversionRetry")>h.getLevel().getGameTime(),"retry delay persists in a real entity save");reloaded.discard();
            h.runAfterDelay(25,()->{try{h.assertTrue(source.isRemoved()&&replacements.size()==1&&replacements.get(0).getMainHandItem().is(Items.DIAMOND_SWORD),"healthy retry transfers the original gear exactly once");h.succeed();}finally{close.run();}});
        }catch(Throwable failure){close.run();throw failure;}
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="conversion_safety_pause")
    public static void administratorCanPauseAndResumeAutomaticReplacementWithoutRemovingTheMob(GameTestHelper h){
        boolean previous=GameplayBalanceConfig.CONVERSION_ENABLED.get();GameplayBalanceConfig.CONVERSION_ENABLED.set(false);
        String tag="conversion-pause-"+UUID.randomUUID();var source=ready(h,EntityType.COW.create(h.getLevel()),tag);var replacements=new ArrayList<BlacklightInfectedHost>();
        Consumer<EntityJoinLevelEvent> observer=e->{if(e.getEntity() instanceof BlacklightInfectedHost host&&host.getTags().contains(tag))replacements.add(host);};MinecraftForge.EVENT_BUS.addListener(observer);
        Runnable close=()->{MinecraftForge.EVENT_BUS.unregister(observer);source.discard();replacements.forEach(Entity::discard);GameplayBalanceConfig.CONVERSION_ENABLED.set(previous);};
        try{automatic(source);h.assertTrue(source.isAlive()&&!source.isRemoved()&&replacements.isEmpty(),"pause retains the actual living source and infection");GameplayBalanceConfig.CONVERSION_ENABLED.set(true);
            h.runAfterDelay(2,()->{try{h.assertTrue(source.isRemoved()&&replacements.size()==1,"resume processes the same source normally");h.succeed();}finally{close.run();}});
        }catch(Throwable failure){close.run();throw failure;}
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="conversion_safety_fatal")
    public static void vanillaSaveWrapperCannotHideFatalJvmErrors(GameTestHelper h)throws Exception{
        var source=new BrokenSaveCow(h.getLevel());source.fatal=true;ready(h,source,"conversion-fatal");boolean propagated=false;
        try{try{direct(source);}catch(OutOfMemoryError expected){propagated=expected.getMessage().startsWith("QA sentinel");}
            h.assertTrue(propagated,"fatal VM failure must not be misreported as a recoverable creature failure");h.succeed();
        }finally{source.broken=false;source.discard();}
    }
}
