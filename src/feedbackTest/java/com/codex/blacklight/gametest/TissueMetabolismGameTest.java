package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class TissueMetabolismGameTest {
    private static long baselineDayTime;
    // These exact-cost examples isolate the base coefficient. World-day scaling
    // has separate rules tests and must not depend on the order of 400+ fixtures.
    @BeforeBatch(batch="tissue_metabolism") public static void baselineClock(net.minecraft.server.level.ServerLevel level){
        var overworld=level.getServer().overworld();baselineDayTime=overworld.getDayTime();overworld.setDayTime(0);
    }
    @AfterBatch(batch="tissue_metabolism") public static void restoreClock(net.minecraft.server.level.ServerLevel level){
        level.getServer().overworld().setDayTime(baselineDayTime);
    }

    private static BlacklightInfectedHost host(GameTestHelper h,TissueCore core){
        var level=h.getLevel();var host=ModEntities.INFECTED_HOST.get().create(level);
        host.inherit(EntityType.COW.create(level));long seed=1;while(TissueCore.forSeed(seed)!=core)seed++;
        long identity=UUID.randomUUID().getMostSignificantBits();host.setUUID(new UUID(identity,identity^seed));var pinned=new net.minecraft.nbt.CompoundTag();host.saveWithoutId(pinned);pinned.putInt("BlacklightTissueNutrition",0);pinned.getCompound("BlacklightHost").putString("NativeCore",core.key());host.readAdditionalSaveData(pinned);
        host.setNoAi(true);host.setPos(h.absolutePos(new BlockPos(5,30,5)).getCenter());
        level.addFreshEntity(host);return host;
    }
    private static Mob prey(GameTestHelper h,BlacklightInfectedHost host){
        var prey=EntityType.COW.create(h.getLevel());prey.setNoAi(true);prey.setPos(host.position().add(1,0,0));
        h.getLevel().addFreshEntity(prey);return prey;
    }
    private static void kill(BlacklightInfectedHost host,Mob prey){prey.hurt(prey.damageSources().mobAttack(host),10000);}
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue_metabolism")
    public static void onlyAcceptedOwnedKillsSupplyOneMeal(GameTestHelper h){
        var host=host(h,TissueCore.JAW);var live=prey(h,host);var food=prey(h,host);var foreign=prey(h,host);
        try{
            host.killedEntity(h.getLevel(),live);h.assertTrue(host.tissueNutrition()==0,"calling a kill callback on a live target cannot create food");
            kill(host,food);h.assertTrue(!food.isAlive()&&host.tissueNutrition()==4,"an actual lethal attack supplies one jaw-enhanced meal; nutrition="+host.tissueNutrition());
            host.killedEntity(h.getLevel(),food);h.assertTrue(host.tissueNutrition()==4,"repeated callbacks cannot feed twice");
            foreign.hurt(foreign.damageSources().generic(),10000);host.killedEntity(h.getLevel(),foreign);
            h.assertTrue(host.tissueNutrition()==4,"another cause of death cannot be claimed by a host");
        }finally{host.discard();live.discard();food.discard();foreign.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue_metabolism")
    public static void canceledDeathsAndInfectedAlliesCannotSupplyMeals(GameTestHelper h){
        var host=host(h,TissueCore.MUSCLE);var food=prey(h,host);var ally=host(h,TissueCore.CARAPACE);
        Consumer<LivingDeathEvent> veto=event->{if(event.getEntity()==food){event.setCanceled(true);food.setHealth(food.getMaxHealth());}};
        MinecraftForge.EVENT_BUS.addListener(veto);
        try{
            kill(host,food);h.assertTrue(food.isAlive()&&host.tissueNutrition()==0,"death rejection supplies nothing");
            kill(host,ally);h.assertTrue(host.tissueNutrition()==0,"infected faction cannot be farmed as ordinary nutrition");
        }finally{MinecraftForge.EVENT_BUS.unregister(veto);host.discard();food.discard();ally.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue_metabolism")
    public static void foodPromotesGrowthAndRecoveryButDoesNotResetInjury(GameTestHelper h){
        var host=host(h,TissueCore.MUSCLE);var food=prey(h,host);
        try{
            kill(host,food);h.assertTrue(host.tissueNutrition()==3,"a vanilla ten-health cow offers three reserve units; actual="+host.tissueNutrition());
            host.setHealth(host.getMaxHealth()*.5F);float injured=host.getHealth();long age=host.tissueAge();
            for(int i=0;i<20;i++)host.tick();
            h.assertTrue(host.tissueAge()==age+20+TissueMetabolism.BONUS_TICKS&&host.tissueNutrition()==3,"one young growth/repair second spends only a fraction of a reserve unit");
            h.assertTrue(host.getHealth()>injured&&host.getHealth()<host.getMaxHealth()*.6F,"nutrition heals modestly, not to full health");
            host.invulnerableTime=0;host.hurt(host.damageSources().generic(),1);injured=host.getHealth()/host.getMaxHealth();
            for(int i=0;i<20;i++)host.tick();
            h.assertTrue(Math.abs(host.getHealth()/host.getMaxHealth()-injured)<.00001&&host.tissueNutrition()==3,"recent damage suppresses meal regeneration without charging its repair cost");
            var saved=new CompoundTag();host.saveWithoutId(saved);var restored=ModEntities.INFECTED_HOST.get().create(h.getLevel());restored.load(saved);
            h.assertTrue(restored.tissueNutrition()==3&&restored.tissueAge()==host.tissueAge(),"age and unspent food survive save/load without offline growth");
            float health=restored.getHealth()/restored.getMaxHealth();for(int i=0;i<20;i++)restored.tick();
            h.assertTrue(Math.abs(restored.getHealth()/restored.getMaxHealth()-health)<.00001,"combat recovery delay also survives save/load");restored.discard();
        }finally{host.discard();food.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue_metabolism")
    public static void lateDevelopmentUsesFoodAndFractionalCostsSurviveReload(GameTestHelper h){
        var host=host(h,TissueCore.MUSCLE);
        try{
            host.advanceTissue(48000);var saved=new CompoundTag();host.saveWithoutId(saved);saved.putInt("BlacklightTissueNutrition",8);saved.putInt("BlacklightTissueNutritionDebt",65);host.load(saved);host.setHealth(host.getMaxHealth());
            long age=host.tissueAge();for(int i=0;i<200;i++)host.tick();
            host.saveWithoutId(saved);h.assertTrue(host.tissueNutrition()==7&&saved.getInt("BlacklightTissueNutritionDebt")==65&&host.tissueAge()==age+200+10*TissueMetabolism.BONUS_TICKS,"later generations still pay fractional food for actual accelerated development");
            host.setHealth(host.getMaxHealth()*.5F);for(int i=0;i<40;i++)host.tick();host.saveWithoutId(saved);
            h.assertTrue(host.tissueNutrition()==6&&saved.getInt("BlacklightTissueNutritionDebt")==25,"two actual repair/development seconds pay 60 hundredths, including the existing debt");
            var restored=ModEntities.INFECTED_HOST.get().create(h.getLevel());restored.load(saved);var again=new CompoundTag();restored.saveWithoutId(again);
            h.assertTrue(again.getInt("BlacklightTissueNutritionDebt")==25&&restored.tissueNutrition()==6,"reloading does not forgive repair/growth costs or refill food");restored.discard();
        }finally{host.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue_metabolism")
    public static void freshInfectionGetsBodyReserveOnce(GameTestHelper h){
        var mob=EntityType.COW.create(h.getLevel());var host=ModEntities.INFECTED_HOST.get().create(h.getLevel());host.inherit(mob);
        try{
            h.assertTrue(host.tissueNutrition()==8,"a newly infected cow carries a small body reserve");
            var saved=new CompoundTag();host.saveWithoutId(saved);saved.putInt("BlacklightTissueNutrition",0);host.load(saved);host.load(saved);
            h.assertTrue(host.tissueNutrition()==0,"an exhausted existing body cannot receive the initial reserve again by reloading");
        }finally{host.discard();mob.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue_metabolism")
    public static void combatGrowthSurvivesRepeatedUpdatesAndSaveWithoutCompounding(GameTestHelper h){
        var host=host(h,TissueCore.CARAPACE);
        try{
            float original=host.getMaxHealth();double attack=host.getAttributeValue(Attributes.ATTACK_DAMAGE);
            host.setHealth(original*.4F);host.advanceTissue(816000);
            float maximum=host.getMaxHealth(),injured=host.getHealth();double damage=host.getAttributeValue(Attributes.ATTACK_DAMAGE);
            h.assertTrue(maximum>original&&damage>attack&&host.getAttributeValue(Attributes.ARMOR)>1,"older organs improve real server attributes");
            h.assertTrue(Math.abs(injured/maximum-.4F)<.00001,"a new generation preserves injury fraction");
            for(int i=0;i<100;i++)host.advanceTissue(0);
            h.assertTrue(host.getMaxHealth()==maximum&&host.getAttributeValue(Attributes.ATTACK_DAMAGE)==damage&&host.getHealth()==injured,"updates replace four stable modifiers instead of stacking");
            var saved=new CompoundTag();host.saveWithoutId(saved);var restored=ModEntities.INFECTED_HOST.get().create(h.getLevel());restored.load(saved);restored.load(saved);
            h.assertTrue(Math.abs(restored.getMaxHealth()-maximum)<.001&&Math.abs(restored.getHealth()-injured)<.001,"reload neither heals nor multiplies evolved health");
            h.assertTrue(Math.abs(restored.getAttributeValue(Attributes.ATTACK_DAMAGE)-damage)<.000001,"derived damage survives reload exactly");
            saved.putInt("BlacklightTissueNutrition",Integer.MAX_VALUE);restored.load(saved);
            h.assertTrue(restored.tissueNutrition()==TissueMetabolism.MAX_RESERVE,"malformed reserves are bounded");restored.discard();
        }finally{host.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue_metabolism")
    public static void evolvedOrgansChangeActualServerDamage(GameTestHelper h){
        var young=host(h,TissueCore.MUSCLE);var old=host(h,TissueCore.MUSCLE);
        var first=prey(h,young);var second=prey(h,old);
        try{
            for(var target:java.util.List.of(first,second)){
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
                // Isolate attack growth from the separate probabilistic contact
                // infection, which changes health units during the same hit.
                target.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);
            }
            old.advanceTissue(816000);young.doHurtTarget(first);old.doHurtTarget(second);
            float basic=1000-first.getHealth(),evolved=1000-second.getHealth();
            h.assertTrue(basic>0&&evolved>basic*1.25F,"evolved attack attributes must increase real damage, not only a displayed number; basic="+basic+", evolved="+evolved);
            var armor=host(h,TissueCore.CARAPACE);
            try{
                armor.hurt(armor.damageSources().mobAttack(first),2);float unprotected=armor.getMaxHealth()-armor.getHealth();
                armor.advanceTissue(816000);armor.setHealth(armor.getMaxHealth());armor.invulnerableTime=0;
                armor.hurt(armor.damageSources().mobAttack(first),2);float protectedLoss=armor.getMaxHealth()-armor.getHealth();
                h.assertTrue(unprotected>0&&protectedLoss>0&&protectedLoss<unprotected,"an evolved carapace reduces accepted damage without making its host immune");
            }finally{armor.discard();}
        }finally{young.discard();old.discard();first.discard();second.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue_metabolism")
    public static void unfedAndMultipartCreaturesKeepTheirExistingProgression(GameTestHelper h){
        var host=host(h,TissueCore.MUSCLE);var boss=ModEntities.INFECTED_HOST.get().create(h.getLevel());
        try{
            long age=host.tissueAge();for(int i=0;i<20;i++)host.tick();
            h.assertTrue(host.tissueAge()==age+20&&host.tissueNutrition()==0,"no food still permits the existing loaded-time evolution");
            boss.inherit(EntityType.IRON_GOLEM.create(h.getLevel()));float health=boss.getMaxHealth();double damage=boss.getAttributeValue(Attributes.ATTACK_DAMAGE);
            boss.advanceTissue(10000000);
            h.assertTrue(boss.hasMultipartBody()&&boss.getMaxHealth()==health&&boss.getAttributeValue(Attributes.ATTACK_DAMAGE)==damage,"multipart encounter evolution never receives ordinary-host bonuses");
            var ordinary=EntityType.COW.create(h.getLevel());h.assertTrue(ordinary.getMaxHealth()==10,"ordinary, uninfected mobs remain unchanged");ordinary.discard();
        }finally{host.discard();boss.discard();}h.succeed();
    }
}
