package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.config.GameplayBalanceConfig;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.state.BlacklightState;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class ConsumeLastChanceGameTest {
    private static BlacklightState state(ServerPlayer p){return ModAttachments.getData(p,ModAttachments.PLAYER_STATE);}
    private static ServerPlayer player(GameTestHelper h,boolean infected){
        h.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"LastChanceQA"));
        p.moveTo(h.absolutePos(new BlockPos(5,30,5)).getCenter());if(infected)state(p).infect();
        state(p).setPrototypeMode(false);state(p).addBiomass(-BlacklightState.MAX_BIOMASS);state(p).addEvolutionPoints(-Integer.MAX_VALUE);return p;
    }
    private static Cow mob(GameTestHelper h){var m=EntityType.COW.create(h.getLevel());m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);m.setHealth(100);m.setNoAi(true);m.moveTo(h.absolutePos(new BlockPos(7,30,5)).getCenter());h.getLevel().addFreshEntity(m);return m;}
    private static void kill(ServerPlayer p,Mob m){m.invulnerableTime=0;m.hurt(p.damageSources().playerAttack(p),1000000);}
    @GameTest(template="wall_test",timeoutTicks=45,batch="consume_last_chance_reward")
    public static void weakenedCreatureSurvivesOnceAndOnlyTheRealKillPays(GameTestHelper h){
        var p=player(h,true);var m=mob(h);m.setHealth(15);var quote=AssimilationSystem.quote(p,m);kill(p,m);
        h.assertTrue(m.isAlive()&&m.getHealth()==1&&ConsumeLastChance.used(m),"first lethal hit at 15 percent leaves exactly one HP");
        h.runAfterDelay(3,()->{h.assertTrue(state(p).getBiomass()==0&&state(p).getEvolutionPoints()==0,"prevented death pays no kill reward");kill(p,m);h.assertTrue(!m.isAlive(),"a second lethal attack kills normally");});
        h.runAfterDelay(6,()->{try{h.assertTrue(state(p).getBiomass()==CombatRewards.fraction(quote.biomass(),.5)&&state(p).getEvolutionPoints()==CombatRewards.fraction(quote.ep(),.5),"one actual death pays exactly half base rewards");h.succeed();}finally{m.discard();p.discard();}});
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="consume_last_chance_boundary")
    public static void fullHealthAndAboveThresholdOneShotsStayNormalKills(GameTestHelper h){
        var p=player(h,true);var full=mob(h);var above=mob(h);var weak=mob(h);
        try{above.setHealth(15.1F);weak.setHealth(14.9F);kill(p,full);kill(p,above);kill(p,weak);
            h.assertTrue(!full.isAlive()&&!above.isAlive()&&!ConsumeLastChance.used(full)&&!ConsumeLastChance.used(above),"protection cannot turn trash clearing into mandatory double hits");
            h.assertTrue(weak.isAlive()&&weak.getHealth()==1,"below threshold protection works");
        }finally{full.discard();above.discard();weak.discard();p.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="consume_last_chance_save")
    public static void spentChanceSurvivesReloadAndInfectionReplacement(GameTestHelper h){
        var p=player(h,true);var m=mob(h);Cow restored=null;com.codex.blacklight.entity.BlacklightInfectedHost host=null;
        try{m.setHealth(10);kill(p,m);var saved=new CompoundTag();m.saveWithoutId(saved);restored=EntityType.COW.create(h.getLevel());restored.load(saved);restored.setUUID(UUID.randomUUID());h.getLevel().addFreshEntity(restored);
            h.assertTrue(ConsumeLastChance.used(restored),"saved entity does not regain its opportunity");
            host=ModEntities.INFECTED_HOST.get().create(h.getLevel());host.inherit(m);host.setNoAi(true);h.getLevel().addFreshEntity(host);
            h.assertTrue(ConsumeLastChance.used(host),"conversion retains the original spent chance");kill(p,restored);kill(p,host);
            h.assertTrue(!restored.isAlive()&&!host.isAlive(),"neither loading nor changing species creates another lock");
        }finally{m.discard();if(restored!=null)restored.discard();if(host!=null)host.discard();p.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="consume_last_chance_owned")
    public static void ownedArrowWorksButEnvironmentCommandsAndOrdinaryPlayersDoNot(GameTestHelper h){
        var p=player(h,true);var ordinary=player(h,false);var owned=mob(h);var env=mob(h);var admin=mob(h);var plain=mob(h);var arrow=new Arrow(EntityType.ARROW,h.getLevel());arrow.setOwner(p);
        try{for(var m:List.of(owned,env,admin,plain))m.setHealth(10);
            owned.hurt(p.damageSources().arrow(arrow,p),1000000);env.hurt(env.damageSources().generic(),1000000);admin.hurt(admin.damageSources().genericKill(),1000000);kill(ordinary,plain);
            h.assertTrue(owned.isAlive()&&owned.getHealth()==1,"owned projectile retains the player's opportunity");
            h.assertTrue(!env.isAlive()&&!admin.isAlive()&&!plain.isAlive(),"unrelated damage and administrative deaths are unaffected");
        }finally{for(var m:List.of(owned,env,admin,plain))m.discard();arrow.discard();p.discard();ordinary.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="consume_last_chance_execute")
    public static void absorptionExecutionBypassesProtectionAndScopeAlwaysCloses(GameTestHelper h){
        var p=player(h,true);var prey=mob(h);var second=mob(h);
        try{prey.setHealth(10);h.assertTrue(ConsumeLastChance.execute(prey,()->prey.hurt(p.damageSources().playerAttack(p),1000000)),"execution accepted");
            h.assertTrue(!prey.isAlive()&&!ConsumeLastChance.used(prey),"completed absorption is not turned into a failed one-HP execution");
            try{ConsumeLastChance.execute(second,()->{throw new IllegalStateException("QA execution callback");});}catch(IllegalStateException expected){}
            second.setHealth(10);kill(p,second);h.assertTrue(second.isAlive()&&ConsumeLastChance.used(second),"an external execution exception cannot leak a permanent bypass");
        }finally{prey.discard();second.discard();p.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="consume_last_chance_cancel")
    public static void nonLethalAndCanceledHitsDoNotSpendTheOpportunity(GameTestHelper h){
        var p=player(h,true);var m=mob(h);m.setHealth(15);
        var veto=new Object(){@SubscribeEvent(priority=EventPriority.LOWEST)public void hurt(LivingHurtEvent e){if(e.getEntity()==m)e.setCanceled(true);}};
        try{m.hurt(p.damageSources().playerAttack(p),1);h.assertTrue(m.isAlive()&&!ConsumeLastChance.used(m),"non-lethal damage does not spend protection");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(veto);kill(p,m);h.assertTrue(!ConsumeLastChance.used(m),"a canceled attack does not spend protection");net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(veto);
            kill(p,m);h.assertTrue(m.isAlive()&&m.getHealth()==1&&ConsumeLastChance.used(m),"a later real lethal hit still gets its single opportunity");
        }finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(veto);m.discard();p.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="consume_last_chance_config")
    public static void serverCanDisableOrChangeTheHealthThreshold(GameTestHelper h){
        var p=player(h,true);var disabled=mob(h);var tuned=mob(h);boolean enabled=GameplayBalanceConfig.CONSUME_LAST_CHANCE.get();double threshold=GameplayBalanceConfig.CONSUME_LAST_CHANCE_THRESHOLD.get();
        try{GameplayBalanceConfig.CONSUME_LAST_CHANCE.set(false);disabled.setHealth(10);kill(p,disabled);h.assertTrue(!disabled.isAlive(),"server switch respected");
            GameplayBalanceConfig.CONSUME_LAST_CHANCE.set(true);GameplayBalanceConfig.CONSUME_LAST_CHANCE_THRESHOLD.set(.2);tuned.setHealth(20);kill(p,tuned);h.assertTrue(tuned.isAlive()&&tuned.getHealth()==1,"server threshold respected");
        }finally{GameplayBalanceConfig.CONSUME_LAST_CHANCE.set(enabled);GameplayBalanceConfig.CONSUME_LAST_CHANCE_THRESHOLD.set(threshold);disabled.discard();tuned.discard();p.discard();}h.succeed();
    }
}
