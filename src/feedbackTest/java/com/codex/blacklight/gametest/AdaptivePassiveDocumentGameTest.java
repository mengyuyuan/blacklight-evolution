package com.codex.blacklight.gametest;

import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModEffects;
import com.codex.blacklight.state.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;

/** Runs real Player.hurt and heal hooks, including Blacklight's HP conversion. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class AdaptivePassiveDocumentGameTest {
    private static ServerPlayer player(GameTestHelper h,boolean scaled,CombatSkill... skills){
        var profile=new GameProfile(UUID.randomUUID(),"PassiveQA");
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,ClientInformation.createDefault());
        p.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;
        p.setGameMode(GameType.SURVIVAL);
        for(int i=0;i<65;i++)p.tick();
        var state=new BlacklightState();state.infect();state.setPrototypeMode(true);state.addEvolutionPoints(10000);
        for(var skill:skills)h.assertTrue(state.buyCombatSkill(skill),"fixture skill purchase "+skill.id());
        p.setData(ModAttachments.PLAYER_STATE,state);
        p.setData(ModAttachments.EVOLUTION_MILESTONES,EvolutionMilestones.EMPTY.grant(EvolutionMilestones.PERCENT));
        CombatScale.attributes(p,scaled);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(scaled?200:1000);p.setHealth(1000);
        return p;
    }
    private static void near(GameTestHelper h,double got,double wanted,String why){
        h.assertTrue(Math.abs(got-wanted)<.002,why+" got="+got+" wanted="+wanted);
    }
    private static void hit(ServerPlayer p,float raw){p.invulnerableTime=0;p.hurt(p.damageSources().generic(),raw);}
    private static void pulse(ServerPlayer p){AdaptivePassiveSystem.tick(new PlayerTickEvent.Post(p));}
    private static void due(ServerPlayer p,int adaptation,int regeneration){
        p.setData(ModAttachments.ADAPTIVE_PASSIVES,new AdaptivePassiveState(0,adaptation,regeneration,p.level().getGameTime()+20));
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="document_adaptation")
    public static void adaptationOneFollowsThreeHits(GameTestHelper h){
        var p=player(h,false,CombatSkill.ADAPTATION);due(p,100,0);
        try{
            int[] health={715,442,187},stacks={146,189,227};
            for(int i=0;i<3;i++){hit(p,300);near(h,p.getHealth(),health[i],"actual HP hit "+i);near(h,AdaptivePassiveSystem.state(p).adaptation(),stacks[i],"adaptation layers "+i);}
        }finally{p.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="document_adaptation")
    public static void adaptationTwoRoundsHealthPercentBeforeMitigation(GameTestHelper h){
        var p=player(h,false,CombatSkill.ADAPTATION,CombatSkill.ADAPTATION_PERCENT);due(p,100,0);
        try{
            int[] health={799,619,457},stacks={124,142,155};
            for(int i=0;i<3;i++){hit(p,300);near(h,p.getHealth(),health[i],"actual HP hit "+i);near(h,AdaptivePassiveSystem.state(p).adaptation(),stacks[i],"adaptation II layers "+i);}
        }finally{p.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="document_healing")
    public static void scaledDamageAndHealingConvertActualHpOnlyOnce(GameTestHelper h){
        var p=player(h,true,CombatSkill.ADAPTATION,CombatSkill.TISSUE_REGENERATION);due(p,100,0);hit(p,60);
        near(h,p.getHealth(),715,"300 damage becomes 285 actual HP");
        near(h,AdaptivePassiveSystem.state(p).adaptation(),146,"accepted mitigation consumes layers");
        near(h,AdaptivePassiveSystem.state(p).regeneration(),48,"regeneration gains from post-mitigation 285 HP");
        h.runAfterDelay(20,()->{try{pulse(p);near(h,p.getHealth(),739,"48 layers restore 24 HP");near(h,AdaptivePassiveSystem.state(p).regeneration(),38,"24 HP spends ten layers");}finally{p.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=80,batch="document_healing")
    public static void secondTierUsesMissingHealthAndUpdatesEffectTier(GameTestHelper h){
        var p=player(h,false,CombatSkill.TISSUE_REGENERATION,CombatSkill.TISSUE_REGENERATION_PERCENT);
        p.setHealth(600);due(p,0,75);
        h.runAfterDelay(20,()->{pulse(p);near(h,p.getHealth(),750,"75 layers restore 37.5 percent of missing HP");near(h,AdaptivePassiveSystem.state(p).regeneration(),12,"150 HP costs 63 layers");
            var marker=p.getEffect(ModEffects.TISSUE_REGENERATION);
            near(h,marker.getAmplifier(),1,"second-tier marker");
            h.assertTrue(!marker.showIcon(),"Blacklight regeneration leaves the buff row clear");});
        h.runAfterDelay(40,()->{try{pulse(p);near(h,p.getHealth(),765,"12 layers restore six percent of missing HP");near(h,AdaptivePassiveSystem.state(p).regeneration(),5,"15 HP costs seven layers");}finally{p.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=80,batch="document_healing")
    public static void oneHeavyHitDoesNotHealItself(GameTestHelper h){
        var p=player(h,false,CombatSkill.TISSUE_REGENERATION,CombatSkill.TISSUE_REGENERATION_PERCENT);
        hit(p,600);
        near(h,p.getHealth(),400,"initial 600 HP loss");
        near(h,AdaptivePassiveSystem.state(p).regeneration(),100,"one hit earned 100 stacks");
        h.runAfterDelay(20,()->{
            pulse(p);
            near(h,p.getHealth(),640,"first pulse restores 240 HP");
            near(h,AdaptivePassiveSystem.state(p).regeneration(),0,"the pulse spends all earned stacks");
        });
        h.runAfterDelay(40,()->{try{pulse(p);near(h,p.getHealth(),640,"no deferred heal from that hit");}finally{p.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="document_healing")
    public static void fullHealthAndOverhealConsumeOnlyActualRestoration(GameTestHelper h){
        var full=player(h,false,CombatSkill.TISSUE_REGENERATION);due(full,0,100);
        var hurt=player(h,false,CombatSkill.TISSUE_REGENERATION);hurt.setHealth(990);due(hurt,0,100);
        h.runAfterDelay(20,()->{try{pulse(full);pulse(hurt);near(h,full.getHealth(),1000,"full HP unchanged");near(h,AdaptivePassiveSystem.state(full).regeneration(),99,"full HP still costs minimum one");
            near(h,hurt.getHealth(),1000,"overheal capped");near(h,AdaptivePassiveSystem.state(hurt).regeneration(),95,"ten restored HP costs five layers");}finally{full.discard();hurt.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="document_healing")
    public static void canceledHealingDoesNotConsumeTheRequestedAmount(GameTestHelper h){
        var p=player(h,false,CombatSkill.TISSUE_REGENERATION);p.setHealth(500);due(p,0,100);
        h.runAfterDelay(20,()->{
            Consumer<LivingHealEvent> cancel=e->{if(e.getEntity()==p)e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(cancel);
            try{pulse(p);near(h,p.getHealth(),500,"canceled heal cannot restore HP");near(h,AdaptivePassiveSystem.state(p).regeneration(),99,"only minimum cost when no HP restored");}
            finally{NeoForge.EVENT_BUS.unregister(cancel);p.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=90,batch="document_healing")
    public static void lastTwoLayersReachZeroWhileAdaptationHasNoTimedDecay(GameTestHelper h){
        var p=player(h,false,CombatSkill.ADAPTATION,CombatSkill.TISSUE_REGENERATION);p.setHealth(500);due(p,90,2);
        h.runAfterDelay(20,()->{pulse(p);near(h,AdaptivePassiveSystem.state(p).regeneration(),1,"two layers become one");});
        h.runAfterDelay(40,()->{pulse(p);near(h,AdaptivePassiveSystem.state(p).regeneration(),0,"last layer reaches zero");});
        h.runAfterDelay(60,()->{try{pulse(p);near(h,p.getHealth(),501.5,"no permanent leftover healing");near(h,AdaptivePassiveSystem.state(p).adaptation(),90,"no timed adaptation decay");}finally{p.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="document_adaptation")
    public static void oldHighStacksStillAllowDamageAndTissueGain(GameTestHelper h){
        var p=player(h,false,CombatSkill.ADAPTATION,CombatSkill.TISSUE_REGENERATION);p.setHealth(100);due(p,10000,0);
        try{hit(p,60);near(h,p.getHealth(),70,"half the hit reaches health");near(h,AdaptivePassiveSystem.state(p).adaptation(),10002,"five layers earned and three spent");near(h,AdaptivePassiveSystem.state(p).regeneration(),5,"thirty actual damage earns five layers");}
        finally{p.discard();}h.succeed();
    }
}
