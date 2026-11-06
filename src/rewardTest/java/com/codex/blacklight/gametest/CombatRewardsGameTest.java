package com.codex.blacklight.gametest;

import com.codex.blacklight.config.GameplayBalanceConfig;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class CombatRewardsGameTest {
    private static ServerPlayer player(GameTestHelper h,boolean infected,boolean mode){
        h.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"RewardQA"));
        p.moveTo(h.absolutePos(new BlockPos(5,30,5)).getCenter());
        var state=state(p);if(infected)state.infect();state.setPrototypeMode(mode);
        state.addBiomass(-BlacklightState.MAX_BIOMASS);state.addEvolutionPoints(-Integer.MAX_VALUE);
        return p;
    }
    private static BlacklightState state(ServerPlayer p){return p.getData(ModAttachments.PLAYER_STATE);}
    private static Zombie mob(GameTestHelper h){var m=EntityType.ZOMBIE.create(h.getLevel());m.setNoAi(true);m.moveTo(h.absolutePos(new BlockPos(7,30,5)).getCenter());h.getLevel().addFreshEntity(m);return m;}
    private static void kill(ServerPlayer p,Mob m){m.invulnerableTime=0;m.hurt(p.damageSources().playerAttack(p),1000000);}
    private static int bio(AssimilationSystem.Yield q){return CombatRewards.fraction(q.biomass(),.5);}
    private static int ep(AssimilationSystem.Yield q){return CombatRewards.fraction(q.ep(),.5);}
    private static void expect(GameTestHelper h,ServerPlayer p,int biomass,int points){h.assertTrue(state(p).getBiomass()==biomass&&state(p).getEvolutionPoints()==points,"bank mismatch: bio="+state(p).getBiomass()+" EP="+state(p).getEvolutionPoints()+" expected="+biomass+"/"+points);}
    @GameTest(template="wall_test",timeoutTicks=40,batch="rewards_melee")
    public static void normalModeMeleePaysHalfWithoutConsumeProgress(GameTestHelper h){
        var p=player(h,true,false);var m=mob(h);var q=AssimilationSystem.quote(p,m);var growth=p.getData(ModAttachments.GROWTH);var research=p.getData(ModAttachments.ORGAN_RESEARCH);
        p.setHealth(p.getMaxHealth()*.5F);float health=p.getHealth();kill(p,m);
        h.runAfterDelay(3,()->{try{expect(h,p,bio(q),ep(q));h.assertTrue(p.getHealth()==health&&growth.equals(p.getData(ModAttachments.GROWTH))&&research.equals(p.getData(ModAttachments.ORGAN_RESEARCH)),"kills cannot heal, count as consumption or discover organs");h.succeed();}finally{m.discard();p.discard();}});
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="rewards_owned")
    public static void ownedProjectilesAndTntCreditTheirPlayer(GameTestHelper h){
        var p=player(h,true,true);var arrow=new Arrow(EntityType.ARROW,h.getLevel());arrow.setOwner(p);var a=mob(h);var b=mob(h);var q=AssimilationSystem.quote(p,a);
        a.hurt(p.damageSources().arrow(arrow,p),1000000);
        var tnt=new net.minecraft.world.entity.item.PrimedTnt(h.getLevel(),0,30,0,p);b.hurt(p.damageSources().explosion(tnt,p),1000000);
        h.runAfterDelay(3,()->{try{expect(h,p,2*bio(q),2*ep(q));h.succeed();}finally{a.discard();b.discard();arrow.discard();tnt.discard();p.discard();}});
    }
    @GameTest(template="wall_test",timeoutTicks=50,batch="rewards_cancel")
    public static void laterDeathVetoDoesNotPayOrPoisonFutureKill(GameTestHelper h){
        var p=player(h,true,true);var m=mob(h);var q=AssimilationSystem.quote(p,m);
        var veto=new Object(){@SubscribeEvent(priority=EventPriority.LOWEST) public void stop(LivingDeathEvent e){if(e.getEntity()==m){e.setCanceled(true);m.setHealth(1);}}};
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(veto);kill(p,m);
        h.runAfterDelay(3,()->{expect(h,p,0,0);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(veto);m.setHealth(m.getMaxHealth());kill(p,m);});
        h.runAfterDelay(6,()->{try{expect(h,p,bio(q),ep(q));h.succeed();}finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(veto);m.discard();p.discard();}});
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="rewards_consume")
    public static void completedAbsorptionWinsDeathCallbackWithoutDoublePayment(GameTestHelper h){
        var p=player(h,true,true);var m=mob(h);var q=AssimilationSystem.quote(p,m);kill(p,m);
        h.assertTrue(AssimilationSystem.settle(p,m,q,false),"successful consumption still settles in full");
        h.runAfterDelay(3,()->{try{expect(h,p,q.biomass(),q.ep());h.assertTrue(!AssimilationSystem.settle(p,m,q,false)&&!AssimilationSystem.settleKill(p,m,bio(q),ep(q)),"all callbacks share one victim settlement");h.succeed();}finally{m.discard();p.discard();}});
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="rewards_ineligible")
    public static void environmentalAdministrativeAndUninfectedKillsPayNothing(GameTestHelper h){
        var p=player(h,true,true);var ordinary=player(h,false,false);var a=mob(h);var b=mob(h);var c=mob(h);
        a.hurt(a.damageSources().generic(),1000000);kill(ordinary,b);c.hurt(p.damageSources().playerAttack(p),1);c.invulnerableTime=0;c.hurt(c.damageSources().genericKill(),1000000);
        h.runAfterDelay(3,()->{try{expect(h,p,0,0);expect(h,ordinary,0,0);h.succeed();}finally{a.discard();b.discard();c.discard();p.discard();ordinary.discard();}});
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="rewards_players")
    public static void multiplayerRewardsOnlyTheActualKiller(GameTestHelper h){
        var a=player(h,true,true);var b=player(h,true,true);var m=mob(h);var q=AssimilationSystem.quote(b,m);
        m.hurt(a.damageSources().playerAttack(a),1);kill(b,m);
        h.runAfterDelay(3,()->{try{expect(h,a,0,0);expect(h,b,bio(q),ep(q));h.succeed();}finally{m.discard();a.discard();b.discard();}});
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="rewards_burst")
    public static void areaKillsAndRepeatedDeathCallbacksSettleEachVictimOnce(GameTestHelper h){
        var p=player(h,true,true);var mobs=new ArrayList<Zombie>();for(int i=0;i<32;i++)mobs.add(mob(h));var q=AssimilationSystem.quote(p,mobs.get(0));for(var m:mobs)kill(p,m);
        h.runAfterDelay(3,()->{expect(h,p,Math.min(BlacklightState.MAX_BIOMASS,32*bio(q)),32*ep(q));for(var m:mobs)net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new LivingDeathEvent(m,p.damageSources().playerAttack(p)));});
        h.runAfterDelay(6,()->{try{expect(h,p,Math.min(BlacklightState.MAX_BIOMASS,32*bio(q)),32*ep(q));h.succeed();}finally{mobs.forEach(Entity::discard);p.discard();}});
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="rewards_settings")
    public static void switchAndIndependentFractionsAreServerControlled(GameTestHelper h){
        var p=player(h,true,true);var a=mob(h);var b=mob(h);var q=AssimilationSystem.quote(p,b);
        boolean enabled=GameplayBalanceConfig.KILL_REWARDS.get();double biomass=GameplayBalanceConfig.KILL_BIOMASS.get(),points=GameplayBalanceConfig.KILL_EP.get();
        GameplayBalanceConfig.KILL_REWARDS.set(false);kill(p,a);
        h.runAfterDelay(3,()->{expect(h,p,0,0);GameplayBalanceConfig.KILL_REWARDS.set(true);GameplayBalanceConfig.KILL_BIOMASS.set(.25);GameplayBalanceConfig.KILL_EP.set(0D);kill(p,b);});
        h.runAfterDelay(6,()->{try{expect(h,p,CombatRewards.fraction(q.biomass(),.25),0);h.succeed();}finally{GameplayBalanceConfig.KILL_REWARDS.set(enabled);GameplayBalanceConfig.KILL_BIOMASS.set(biomass);GameplayBalanceConfig.KILL_EP.set(points);a.discard();b.discard();p.discard();}});
    }
}
