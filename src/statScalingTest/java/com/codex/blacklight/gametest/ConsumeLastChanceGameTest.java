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
    @GameTest(template="wall_test",timeoutTicks=80,batch="consume_actual_moves")
    public static void authoredWeaponStrikesPreserveTheFirstLethalContact(GameTestHelper h){
        for(var form:List.of(PowerForm.CLAWS,PowerForm.BLADE,PowerForm.WHIPFIST,PowerForm.HAMMERFISTS,PowerForm.MUSCLE_MASS)){
            var p=player(h,true);var state=state(p);state.setPrototypeMode(true);state.unlock(form);state.activate(form);
            ModAttachments.setData(p,ModAttachments.EVOLUTION_MILESTONES,new com.codex.blacklight.state.EvolutionMilestones(1));
            ModAttachments.setData(p,ModAttachments.INFINITE_EVOLUTION,ModAttachments.getData(p,ModAttachments.INFINITE_EVOLUTION).buy(EvolutionAttribute.STRENGTH,1000));
            var origin=p.position();var m=EntityType.IRON_GOLEM.create(h.getLevel());
            m.setNoAi(true);m.setNoGravity(true);h.getLevel().addFreshEntity(m);
            try{
                for(int swing=0;swing<2;swing++){
                    p.setPos(origin);p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);p.setOnGround(true);
                    p.setYRot(0);p.setXRot(0);p.setYHeadRot(0);p.yBodyRot=p.yBodyRotO=0;
                    m.setPos(origin.add(0,0,form==PowerForm.WHIPFIST?4:1.8));m.invulnerableTime=0;
                    WeaponCombatSystem.request(p,false);
                    var move=ModAttachments.getData(p,ModAttachments.WEAPON_VISUAL).spec();
                    h.assertTrue(move!=null,form+" accepted the attack");
                    for(int i=0;i<move.duration+12 && WeaponCombatSystem.busy(p);i++){
                        p.tickCount++;WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
                    }
                    h.assertTrue(swing==0?m.getHealth()==1 && m.isAlive():!m.isAlive(),form+" swing "+swing+" actual health "+m.getHealth());
                    WeaponCombatSystem.cancel(p);p.tickCount+=25;
                }
            }finally{WeaponCombatSystem.cancel(p);p.discard();m.discard();}
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="consume_last_chance_reward")
    public static void weakenedCreatureSurvivesOnceAndOnlyTheRealKillPays(GameTestHelper h){
        var p=player(h,true);var m=mob(h);m.setHealth(15);var quote=AssimilationSystem.quote(p,m);kill(p,m);
        h.assertTrue(m.isAlive()&&m.getHealth()==1&&ConsumeLastChance.used(m),"first lethal hit at 15 percent leaves exactly one HP");
        h.runAfterDelay(3,()->{h.assertTrue(state(p).getBiomass()==0&&state(p).getEvolutionPoints()==0,"prevented death pays no kill reward");kill(p,m);h.assertTrue(!m.isAlive(),"a second lethal attack kills normally");});
        h.runAfterDelay(6,()->{try{h.assertTrue(state(p).getBiomass()==CombatRewards.fraction(quote.biomass(),.5)&&state(p).getEvolutionPoints()==CombatRewards.fraction(quote.ep(),.5),"one actual death pays exactly half base rewards");h.succeed();}finally{m.discard();p.discard();}});
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="consume_last_chance_boundary")
    public static void allFormsReserveLargePreyAndSmallPreyDieAtAnyHealth(GameTestHelper h){
        var p=player(h,true);var state=state(p);state.setPrototypeMode(true);
        var forms=List.of(PowerForm.CLAWS,PowerForm.BLADE,PowerForm.HAMMERFISTS,PowerForm.WHIPFIST,PowerForm.MUSCLE_MASS,PowerForm.NONE);
        try {
            for (var form:forms) {
                state.unlock(form);state.activate(form);
                var prey=mob(h);
                try {
                    kill(p,prey);
                    h.assertTrue(prey.getHealth()==1 && ImpactControl.stunned(prey),form+" full-health 100 HP creature enters opening");
                    prey.heal(20);h.assertTrue(prey.getHealth()==1,form+" retains one HP");
                    prey.invulnerableTime=0;prey.hurt(p.damageSources().playerAttack(p),.25F);
                    h.assertTrue(!prey.isAlive(),form+" next independent attack kills");
                } finally {prey.discard();}
            }
            for(float max:List.of(20F,49F,50F,51F))for(float start:List.of(1F,max)){
                var small=mob(h);
                try{small.getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);small.setHealth(start);
                    if(max<50)h.assertTrue(ConsumeSystem.healthReady(p,small),"low-health class directly consumable");
                    kill(p,small);
                    h.assertTrue(small.isAlive()==(max>50),"boundary at "+start+" / "+max);
                }finally{small.discard();}
            }
        } finally {p.discard();}h.succeed();
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
    public static void serverCanDisableTheLargePreyOpening(GameTestHelper h){
        var p=player(h,true);var disabled=mob(h);var tuned=mob(h);boolean enabled=GameplayBalanceConfig.CONSUME_LAST_CHANCE.get();
        try{GameplayBalanceConfig.CONSUME_LAST_CHANCE.set(false);disabled.setHealth(10);kill(p,disabled);h.assertTrue(!disabled.isAlive(),"server switch respected");
            GameplayBalanceConfig.CONSUME_LAST_CHANCE.set(true);tuned.getAttribute(Attributes.MAX_HEALTH).setBaseValue(50);tuned.setHealth(10);kill(p,tuned);h.assertTrue(!tuned.isAlive()&&!ConsumeLastChance.used(tuned),"50 HP is outside the enabled opening");
        }finally{GameplayBalanceConfig.CONSUME_LAST_CHANCE.set(enabled);disabled.discard();tuned.discard();p.discard();}h.succeed();
    }
}
