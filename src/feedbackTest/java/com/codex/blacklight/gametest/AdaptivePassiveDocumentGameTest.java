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
    public static void adaptationOneFollowsThreeDocumentHits(GameTestHelper h){
        var p=player(h,false,CombatSkill.ADAPTATION);due(p,100,0);
        try{
            int[] health={730,481,265},stacks={142,178,205};
            for(int i=0;i<3;i++){hit(p,300);near(h,p.getHealth(),health[i],"actual HP hit "+i);near(h,AdaptivePassiveSystem.state(p).adaptation(),stacks[i],"adaptation layers "+i);}
        }finally{p.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="document_adaptation")
    public static void adaptationTwoRoundsHealthPercentBeforeMitigation(GameTestHelper h){
        var p=player(h,false,CombatSkill.ADAPTATION,CombatSkill.ADAPTATION_PERCENT);due(p,100,0);
        try{
            int[] health={850,721,604},stacks={110,114,115};
            for(int i=0;i<3;i++){hit(p,300);near(h,p.getHealth(),health[i],"actual HP hit "+i);near(h,AdaptivePassiveSystem.state(p).adaptation(),stacks[i],"adaptation II layers "+i);}
        }finally{p.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="document_healing")
    public static void scaledDamageAndHealingConvertActualHpOnlyOnce(GameTestHelper h){
        var p=player(h,true,CombatSkill.ADAPTATION,CombatSkill.TISSUE_REGENERATION);due(p,100,0);hit(p,60);
        near(h,p.getHealth(),730,"300 damage becomes 270 actual HP");
        near(h,AdaptivePassiveSystem.state(p).adaptation(),142,"accepted mitigation consumes layers");
        near(h,AdaptivePassiveSystem.state(p).regeneration(),45,"regeneration gains from post-mitigation 270 HP");
        h.runAfterDelay(20,()->{try{pulse(p);near(h,p.getHealth(),775,"45 HP is not multiplied to 225");near(h,AdaptivePassiveSystem.state(p).regeneration(),36,"20 percent of restored HP consumed");}finally{p.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=80,batch="document_healing")
    public static void secondTierUsesMissingHealthAndUpdatesEffectTier(GameTestHelper h){
        var p=player(h,false,CombatSkill.TISSUE_REGENERATION,CombatSkill.TISSUE_REGENERATION_PERCENT);
        p.setHealth(600);due(p,0,75);
        h.runAfterDelay(20,()->{pulse(p);near(h,p.getHealth(),900,"75 percent of missing 400 HP");near(h,AdaptivePassiveSystem.state(p).regeneration(),15,"actual 300 HP costs 60 layers");
            near(h,p.getEffect(ModEffects.TISSUE_REGENERATION).getAmplifier(),1,"second-tier marker");});
        h.runAfterDelay(40,()->{try{pulse(p);near(h,p.getHealth(),915,"15 percent of missing 100 HP");near(h,AdaptivePassiveSystem.state(p).regeneration(),12,"actual 15 HP costs three layers");}finally{p.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="document_healing")
    public static void fullHealthAndOverhealConsumeOnlyActualRestoration(GameTestHelper h){
        var full=player(h,false,CombatSkill.TISSUE_REGENERATION);due(full,0,100);
        var hurt=player(h,false,CombatSkill.TISSUE_REGENERATION);hurt.setHealth(990);due(hurt,0,100);
        h.runAfterDelay(20,()->{try{pulse(full);pulse(hurt);near(h,full.getHealth(),1000,"full HP unchanged");near(h,AdaptivePassiveSystem.state(full).regeneration(),99,"full HP still costs minimum one");
            near(h,hurt.getHealth(),1000,"overheal capped");near(h,AdaptivePassiveSystem.state(hurt).regeneration(),98,"only ten restored HP costs two");}finally{full.discard();hurt.discard();}h.succeed();});
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
        h.runAfterDelay(60,()->{try{pulse(p);near(h,p.getHealth(),503,"no permanent leftover healing");near(h,AdaptivePassiveSystem.state(p).adaptation(),90,"no timed adaptation decay");}finally{p.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="document_adaptation")
    public static void fullMitigationSpendsLayersWithoutGeneratingTissue(GameTestHelper h){
        var p=player(h,false,CombatSkill.ADAPTATION,CombatSkill.TISSUE_REGENERATION);p.setHealth(10);due(p,1000,0);
        try{hit(p,60);near(h,p.getHealth(),10,"100 percent mitigation");near(h,AdaptivePassiveSystem.state(p).adaptation(),994,"fully blocked 60 HP costs six");near(h,AdaptivePassiveSystem.state(p).regeneration(),0,"zero actual damage earns no regeneration");}
        finally{p.discard();}h.succeed();
    }
}
