package com.codex.blacklight.gametest;

import com.codex.blacklight.config.GameplayBalanceConfig;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class InfectionStatScalingGameTest {
    private static Zombie source(GameTestHelper h,double hp,double attack){
        h.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        var source=new Zombie(EntityType.ZOMBIE,h.getLevel()){
            @Override public double getAttributeValue(Attribute key){return key==Attributes.ATTACK_DAMAGE?attack:super.getAttributeValue(key);}
        };
        source.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);source.setHealth(source.getMaxHealth());source.setNoAi(true);source.moveTo(h.absolutePos(new BlockPos(5,30,5)).getCenter());h.getLevel().addFreshEntity(source);return source;
    }
    private static BlacklightInfectedHost host(GameTestHelper h,Mob source){var host=ModEntities.INFECTED_HOST.get().create(h.getLevel());host.inherit(source);host.setNoAi(true);host.moveTo(h.absolutePos(new BlockPos(7,30,5)).getCenter());h.getLevel().addFreshEntity(host);return host;}
    private static void close(Mob... mobs){for(var m:mobs)if(m!=null)m.discard();}
    private static void near(GameTestHelper h,double actual,double expected,String message){h.assertTrue(Math.abs(actual-expected)<=Math.max(.01,Math.abs(expected)*1e-6),message+": actual="+actual+" expected="+expected);}
    @GameTest(template="wall_test",timeoutTicks=40,batch="infection_stat_curve")
    public static void initialGainIsContinuousAndHasNoHealthCeiling(GameTestHelper h){
        near(h,InfectionConversionMath.enhance(20,1000,.4,.05),28,"ordinary gain");
        near(h,InfectionConversionMath.enhance(1000,1000,.4,.05),1400,"threshold gain");
        near(h,InfectionConversionMath.enhance(10000,1000,.4,.05),10850,"high native health");
        near(h,InfectionConversionMath.enhance(1000000,1000,.4,.05),1050350,"million HP retains original strength");
        double previous=0;
        for(double hp:new double[]{1,20,999.999,1000,1000.001,10000,1000000,1000000000}){
            double value=InfectionConversionMath.enhance(hp,1000,.4,.05);h.assertTrue(value>=hp&&value>previous,"continuous increasing curve cannot cap or reduce original health");previous=value;
        }
        near(h,InfectionConversionMath.enhance(1000.001,1000,.4,.05)-InfectionConversionMath.enhance(999.999,1000,.4,.05),.00245,"no threshold jump");
        h.assertTrue(Double.isFinite(InfectionConversionMath.enhance(Double.POSITIVE_INFINITY,1000,.4,.05)),"nonfinite input cannot poison entity stats");h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="infection_stat_units")
    public static void highHealthInfectionAndConversionKeepNativeHpUnits(GameTestHelper h){
        var high=source(h,10000,500);var low=source(h,20,3);BlacklightInfectedHost converted=null,ordinary=null;
        try{
            high.setHealth(5000);h.assertTrue(BlacklightInfectionSystem.infect(high,null),"actual high-health infection accepted");
            near(h,CombatScale.units(high),1,"onset must not multiply native HP by five");near(h,high.getMaxHealth(),10006,"onset retains the existing six-HP infection effect");
            double nativeHealth=CombatScale.originalMaximum(high),expected=InfectionStatScaling.health(nativeHealth,InfectionStatScaling.rules());double fraction=high.getHealth()/high.getMaxHealth();
            converted=host(h,high);near(h,converted.getAttribute(Attributes.MAX_HEALTH).getBaseValue(),expected,"piecewise inherited HP");near(h,converted.getMaxHealth(),expected,"no extra fivefold conversion");near(h,converted.getHealth()/converted.getMaxHealth(),fraction,"conversion preserves missing-health fraction");
            near(h,converted.getAttributeValue(Attributes.ATTACK_DAMAGE),570,"high authored attack receives smaller excess gain");
            ordinary=host(h,low);near(h,ordinary.getAttribute(Attributes.MAX_HEALTH).getBaseValue(),28,"ordinary base unchanged");near(h,CombatScale.units(ordinary),5,"ordinary presentation unchanged");near(h,ordinary.getAttributeValue(Attributes.ATTACK_DAMAGE),4,"ordinary fallback bite unchanged");
        }finally{close(high,low,converted,ordinary);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="infection_stat_attack")
    public static void largeMeleeAndEarnedModifiersPassVanillaAttackClamp(GameTestHelper h){
        var donor=source(h,10000,50000);var host=host(h,donor);var target=source(h,1000000,1);
        try{
            near(h,host.getAttributeValue(Attributes.ATTACK_DAMAGE),55020,"native melee is not truncated at 2048");
            CombatScale.modifier(host.getAttribute(Attributes.ATTACK_DAMAGE),new net.minecraft.resources.ResourceLocation("blacklight","qa_unlimited_attack"),1,AttributeModifier.Operation.MULTIPLY_TOTAL);
            near(h,host.getAttributeValue(Attributes.ATTACK_DAMAGE),110040,"earned multiplicative attack continues beyond the vanilla clamp");
            target.getAttribute(Attributes.ARMOR).setBaseValue(0);
            float before=target.getHealth();h.assertTrue(host.doHurtTarget(target),"real melee accepted");near(h,before-target.getHealth(),110040,"actual target health reflects the large attack");
        }finally{close(donor,host,target);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="infection_stat_player")
    public static void strongOriginDoesNotUseTrashBudgetOrForceAOneShot(GameTestHelper h)throws Exception {
        var donor=source(h,10000,50000);var host=host(h,donor);
        var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"NativeThreatQA")){
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource s){return getAbilities().invulnerable&&!s.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);}
        };
        try{
            var grace=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");grace.setAccessible(true);grace.setInt(p,0);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            try{
                var cap=Class.forName("com.xiaoxiang.cultivation.cultivation.CultivationCapability");
                var data=((Optional<?>)cap.getMethod("get",net.minecraft.world.entity.player.Player.class).invoke(null,p)).orElseThrow();
                data.getClass().getMethod("setIdentityId",String.class).invoke(data,"mortal");
            }catch(ClassNotFoundException absent){}
            var state=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);
            p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(80);CombatScale.attributes(p,true);p.setHealth(p.getMaxHealth());
            float before=p.getHealth();h.assertTrue(host.doHurtTarget(p),"strong source can damage an infected survival player");float loss=before-p.getHealth();
            h.assertTrue(loss>10&&loss<=before*com.codex.blacklight.infection.EvolvedBalance.rules().hitFraction()+.1&&p.isAlive(),"large native attack uses the existing elite ceiling without being forced to one-shot: "+loss);
        }finally{close(donor,host);p.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="infection_stat_legacy")
    public static void legacySaveAndConfigChangesPreserveGrowthAndHealthFraction(GameTestHelper h){
        boolean enabled=GameplayBalanceConfig.INFECTION_STATS.get();double excess=GameplayBalanceConfig.INFECTION_HEALTH_EXCESS.get();
        var donor=source(h,10000,50000);BlacklightInfectedHost old=null,loaded=null;
        try{
            GameplayBalanceConfig.INFECTION_STATS.set(false);old=host(h,donor);old.advanceTissue(72000);old.setHealth(old.getMaxHealth()*.3F);
            var saved=new CompoundTag();old.saveWithoutId(saved);saved.getCompound("BlacklightHost").remove("InfectionSourceHealth");saved.getCompound("BlacklightHost").remove("InfectionSourceAttack");
            var genes=saved.getCompound("BlacklightTissueInheritance").copy();long age=old.tissueAge();
            GameplayBalanceConfig.INFECTION_STATS.set(true);loaded=ModEntities.INFECTED_HOST.get().create(h.getLevel());loaded.load(saved);loaded.setUUID(UUID.randomUUID());loaded.setNoAi(true);h.assertTrue(h.getLevel().addFreshEntity(loaded),"saved entity joins normally");
            near(h,loaded.infectionSourceHealth(),10000,"legacy original health recovered once");near(h,loaded.getAttribute(Attributes.MAX_HEALTH).getBaseValue(),10850,"legacy base converted");near(h,CombatScale.units(loaded),1,"legacy fivefold health removed");near(h,loaded.getHealth()/loaded.getMaxHealth(),.3,"missing health retained");
            var after=new CompoundTag();loaded.saveWithoutId(after);h.assertTrue(loaded.tissueAge()==age&&genes.equals(after.getCompound("BlacklightTissueInheritance")),"age and inherited organ progress survive migration");
            GameplayBalanceConfig.INFECTION_HEALTH_EXCESS.set(.01);loaded.refreshInfectionStats();near(h,loaded.getAttribute(Attributes.MAX_HEALTH).getBaseValue(),10490,"config edits reuse original source");near(h,loaded.getHealth()/loaded.getMaxHealth(),.3,"config reload cannot heal or kill");
            loaded.refreshInfectionStats();near(h,loaded.getAttribute(Attributes.MAX_HEALTH).getBaseValue(),10490,"repeated refresh never compounds gains");h.assertTrue(loaded.tissueAge()==age,"config edits never change evolution age");
        }finally{GameplayBalanceConfig.INFECTION_STATS.set(enabled);GameplayBalanceConfig.INFECTION_HEALTH_EXCESS.set(excess);close(donor,old,loaded);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="infection_stat_infinite")
    public static void lowOriginGrowingLargeKeepsItsUnlimitedModifiers(GameTestHelper h){
        var donor=source(h,20,3);var host=host(h,donor);
        try{
            CombatScale.modifier(host.getAttribute(Attributes.MAX_HEALTH),new net.minecraft.resources.ResourceLocation("blacklight","qa_unlimited_health"),999,AttributeModifier.Operation.MULTIPLY_TOTAL);
            float maximum=host.getMaxHealth();host.setHealth(maximum*.4F);host.refreshInfectionStats();CombatScale.synchronizeMob(host);
            near(h,host.getMaxHealth(),maximum,"earned enormous HP is not compressed as initial source HP");near(h,CombatScale.units(host),5,"low origin does not change units when its evolution grows");near(h,host.getHealth()/host.getMaxHealth(),.4,"growth health fraction stable");h.assertTrue(host.getMaxHealth()>100000,"no fixed evolution health ceiling");
        }finally{close(donor,host);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="infection_stat_authored")
    public static void sourceLessCommandAttributesAndAuthoredGiantsArePreserved(GameTestHelper h){
        var command=ModEntities.INFECTED_HOST.get().create(h.getLevel());var tag=new CompoundTag();command.getAttribute(Attributes.MAX_HEALTH).setBaseValue(77);command.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1234);command.saveWithoutId(tag);tag.remove("BlacklightHost");command.load(tag);
        var source=EntityType.WITHER.create(h.getLevel());var giant=ModEntities.INFECTED_HOST.get().create(h.getLevel());giant.inherit(source,true);
        try{
            near(h,command.getAttribute(Attributes.MAX_HEALTH).getBaseValue(),77,"manual summon health preserved");near(h,command.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue(),1234,"manual summon attack preserved");
            double base=giant.getAttribute(Attributes.MAX_HEALTH).getBaseValue();giant.refreshInfectionStats();near(h,giant.getAttribute(Attributes.MAX_HEALTH).getBaseValue(),base,"authored colossal encounter unchanged");h.assertTrue(giant.giantBoss(),"giant profile retained");
        }finally{close(command,source,giant);}h.succeed();
    }
}
