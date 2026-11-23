package com.codex.blacklight.gametest;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.GrowthProgress;
import com.codex.blacklight.state.EvolutionMilestones;
import com.codex.blacklight.combat.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.*;
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class FeedbackSuggestionsGameTest {
    @GameTest(template="wall_test",timeoutTicks=60,batch="feedback_threshold")
    public static void humanAndLiquidFormsUseTheirOwnUpgrades(GameTestHelper h){
        var p=FeedbackLifecycleGameTest.player(h);var target=EntityType.ZOMBIE.create(h.getLevel());
        try{
            target.setPos(p.position());target.setNoAi(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            h.getLevel().addFreshEntity(target);target.setHealth(19);
            h.assertTrue(!ConsumeSystem.healthReady(p,target)&&BiomassSystem.edible(p,target),"19 of 100 exceeds the human 15 percent threshold but fits the liquid 20 health limit");
            com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.GROWTH,GrowthProgress.EMPTY.buy(GrowthUpgrade.ASSIMILATION));
            h.assertTrue(ConsumeSystem.healthReady(p,target)&&BiomassSystem.edible(p,target),"human assimilation permits 19 percent and the target remains under the liquid cap");
            target.setHealth(24);
            h.assertTrue(!ConsumeSystem.healthReady(p,target),"first tier cannot consume 24 percent");
            com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.GROWTH,com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.GROWTH).buy(GrowthUpgrade.ASSIMILATION_II));
            h.assertTrue(ConsumeSystem.healthReady(p,target)&&!BiomassSystem.edible(p,target),"24 of 100 fits the second human upgrade but exceeds the base liquid cap");
            ModAttachments.setData(p, ModAttachments.EVOLUTION_MILESTONES,ModAttachments.getData(p, ModAttachments.EVOLUTION_MILESTONES).grant(EvolutionMilestones.INFINITE));
            ModAttachments.setData(p, ModAttachments.INFINITE_EVOLUTION,ModAttachments.getData(p, ModAttachments.INFINITE_EVOLUTION).buy(EvolutionAttribute.BIOMASS_CONSUMPTION,2));
            h.assertTrue(BiomassSystem.edible(p,target),"two biomass ranks accept exactly 24 remaining health");
            target.setHealth(25);
            h.assertTrue(ConsumeSystem.healthReady(p,target)&&!BiomassSystem.edible(p,target),"25 percent still fits only the human upgrade");
            target.setHealth(26);h.assertTrue(!ConsumeSystem.healthReady(p,target),"upgrade still enforces weakening");
        }finally{target.discard();FeedbackLifecycleGameTest.cleanup(p);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="feedback_metabolism")
    public static void retiredMetabolismCannotRunFromAnOldSave(GameTestHelper h){
        var p=FeedbackLifecycleGameTest.player(h);
        try{
            var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);state.addBiomass(200);
            com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.GROWTH,new GrowthProgress(524288,0));
            GrowthSystem.updateHealth(p);
            p.setHealth(p.getMaxHealth()/2);
            float before=p.getHealth();
            int biomass=state.getBiomass();
            p.tickCount=20;
            GrowthSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
            h.assertTrue(p.getHealth()==before && state.getBiomass()==biomass,"retired skill cannot heal or spend biomass");
        }finally{FeedbackLifecycleGameTest.cleanup(p);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="feedback_claw_grace")
    public static void clawStartupProtectionEndsBeforeRecovery(GameTestHelper h){
        var p=FeedbackLifecycleGameTest.player(h);
        try{
            for(int i=0;i<61;i++)p.tick();p.setOnGround(true);p.setHealth(p.getMaxHealth());p.invulnerableTime=0;
            WeaponCombatSystem.requestSpecial(p);
            h.assertTrue(WeaponCombatSystem.groundSpikeGrace(p),"actual claw groundspike starts bounded protection");
            float health=p.getHealth();p.hurt(p.damageSources().generic(),1);
            h.assertTrue(p.getHealth()==health,"normal damage is rejected during startup");
            p.tickCount+=WeaponMove.CLAW_GROUNDSPIKE.start;
            h.assertTrue(!WeaponCombatSystem.groundSpikeGrace(p),"protection ends at eruption");
            p.invulnerableTime=0;p.hurt(p.damageSources().generic(),1);
            h.assertTrue(p.getHealth()<health,"normal damage returns after startup");
        }finally{WeaponCombatSystem.cancel(p);FeedbackLifecycleGameTest.cleanup(p);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="feedback_physiology")
    public static void ordinaryInfectedKeepsEnvironmentalDefenses(GameTestHelper h){
        var p=FeedbackLifecycleGameTest.player(h);
        try{
            for(int i=0;i<61;i++)p.tick();
            var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);state.setPrototypeMode(false);
            p.setHealth(p.getMaxHealth());p.invulnerableTime=0;
            float before=p.getHealth();p.hurt(p.damageSources().fall(),4);
            h.assertTrue(p.getHealth()==before,"ordinary infected is protected from falling");
            p.invulnerableTime=0;p.hurt(p.damageSources().onFire(),1);
            float loss=before-p.getHealth();
            h.assertTrue(Math.abs(loss-.2F*CombatScale.units(p))<.001F,"fire damage is reduced, not made immune");
            p.setHealth(p.getMaxHealth());p.invulnerableTime=0;before=p.getHealth();
            p.hurt(p.damageSources().generic(),1);
            h.assertTrue(Math.abs(before-p.getHealth()-.8F*CombatScale.units(p))<.001F,"ordinary defense leaves meaningful incoming damage");
            p.setHealth(p.getMaxHealth());p.invulnerableTime=0;before=p.getHealth();
            p.hurt(p.damageSources().genericKill(),1);
            h.assertTrue(p.getHealth()<before,"administrative damage bypasses viral protection");
        }finally{FeedbackLifecycleGameTest.cleanup(p);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="feedback_sweeper")
    public static void closeSweeperHitsExactlyNineTimes(GameTestHelper h) throws Exception {withoutActorHitstop(()->closeSweeperHitsExactlyNineTimesBody(h));}
    private static void closeSweeperHitsExactlyNineTimesBody(GameTestHelper h) throws Exception {
        var p=FeedbackLifecycleGameTest.player(h);var contacts=new java.util.concurrent.atomic.AtomicInteger();
        var target=new net.minecraft.world.entity.animal.Cow(EntityType.COW,h.getLevel()){
            @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){
                boolean hit=super.hurt(source,amount);if(hit&&source.getEntity()==p)contacts.incrementAndGet();return hit;
            }
        };
        try{
            for(int i=0;i<61;i++)p.tick();p.setOnGround(true);
            var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);state.unlock(PowerForm.WHIPFIST);state.activate(PowerForm.WHIPFIST);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);target.setNoAi(true);
            target.setPos(p.position().add(0,0,.7));h.getLevel().addFreshEntity(target);
            var start=WeaponCombatSystem.class.getDeclaredMethod("start",net.minecraft.server.level.ServerPlayer.class,WeaponMove.class,boolean.class,WeaponMove.Input.class,float.class);
            start.setAccessible(true);start.invoke(null,p,WeaponMove.WHIP_SWEEP,false,WeaponMove.Input.SPECIAL,0F);
            int began=p.tickCount;
            for(int t=0;t<=WeaponMove.WHIP_SWEEP.duration;t++){
                p.tickCount=began+t;p.setOnGround(true);target.setPos(p.position().add(0,0,.7));
                WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
            }
            h.assertTrue(contacts.get()==9,"one close target receives nine real damage contacts, got "+contacts.get());
        }finally{WeaponCombatSystem.cancel(p);target.discard();FeedbackLifecycleGameTest.cleanup(p);}h.succeed();
    }
    /** These tick-exact timelines predate hitstop; actor hitstop only extends busy time, so it is disabled here. */
    private interface Body{void run() throws Exception;}
    private static void withoutActorHitstop(Body body) throws Exception{
        boolean old=com.codex.blacklight.combat.WeaponCombatSystem.actorHitstop(false);
        try{body.run();}finally{com.codex.blacklight.combat.WeaponCombatSystem.actorHitstop(old);}
    }
}
