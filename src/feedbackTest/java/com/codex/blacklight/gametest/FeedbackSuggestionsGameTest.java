package com.codex.blacklight.gametest;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.GrowthProgress;
import com.codex.blacklight.combat.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class FeedbackSuggestionsGameTest {
    @GameTest(template="wall_test",timeoutTicks=60,batch="feedback_threshold")
    public static void upgradedThresholdIsSharedByHumanAndLiquidForms(GameTestHelper h){
        var p=FeedbackLifecycleGameTest.player(h);var target=EntityType.ZOMBIE.create(h.getLevel());
        try{
            target.setPos(p.position());target.setNoAi(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            h.getLevel().addFreshEntity(target);target.setHealth(19);
            h.assertTrue(!ConsumeSystem.healthReady(p,target)&&!BiomassSystem.edible(p,target),"baseline requires 15 percent");
            p.setData(ModAttachments.GROWTH,GrowthProgress.EMPTY.buy(GrowthUpgrade.ASSIMILATION));
            h.assertTrue(ConsumeSystem.healthReady(p,target)&&BiomassSystem.edible(p,target),"first upgrade permits 20 percent in both forms");
            target.setHealth(24);
            h.assertTrue(!ConsumeSystem.healthReady(p,target),"first tier cannot consume 24 percent");
            p.setData(ModAttachments.GROWTH,p.getData(ModAttachments.GROWTH).buy(GrowthUpgrade.ASSIMILATION_II));
            h.assertTrue(ConsumeSystem.healthReady(p,target)&&BiomassSystem.edible(p,target),"second upgrade permits 25 percent");
            target.setHealth(26);h.assertTrue(!ConsumeSystem.healthReady(p,target),"upgrade still enforces weakening");
        }finally{target.discard();FeedbackLifecycleGameTest.cleanup(p);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="feedback_metabolism")
    public static void metabolismPaysForHealingWithoutCreatingCriticalMass(GameTestHelper h){
        var p=FeedbackLifecycleGameTest.player(h);
        try{
            var state=p.getData(ModAttachments.PLAYER_STATE);state.addBiomass(200);
            p.setData(ModAttachments.GROWTH,GrowthProgress.EMPTY.buy(GrowthUpgrade.BIOMASS_METABOLISM));
            p.setHealth(p.getMaxHealth()/2);float before=p.getHealth();int biomass=state.getBiomass();
            GrowthSystem.metabolize(p);
            h.assertTrue(p.getHealth()>before&&state.getBiomass()==biomass-10,"only a delivered healing pulse costs biomass");
            h.assertTrue(p.getData(ModAttachments.CRITICAL_MASS).reserve()==0,"metabolism cannot farm critical mass");
            p.setHealth(p.getMaxHealth());biomass=state.getBiomass();GrowthSystem.metabolize(p);
            h.assertTrue(state.getBiomass()==biomass,"full health consumes no biomass");
            state.setPrototypeMode(false);p.setHealth(p.getMaxHealth()/2);before=p.getHealth();GrowthSystem.metabolize(p);
            h.assertTrue(p.getHealth()==before&&state.getBiomass()==biomass,"ordinary mode cannot run the prototype metabolism");
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
            var state=p.getData(ModAttachments.PLAYER_STATE);state.setPrototypeMode(false);
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
    public static void closeSweeperHitsExactlyNineTimes(GameTestHelper h) throws Exception {
        var p=FeedbackLifecycleGameTest.player(h);var contacts=new java.util.concurrent.atomic.AtomicInteger();
        var target=new net.minecraft.world.entity.animal.Cow(EntityType.COW,h.getLevel()){
            @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){
                boolean hit=super.hurt(source,amount);if(hit&&source.getEntity()==p)contacts.incrementAndGet();return hit;
            }
        };
        try{
            for(int i=0;i<61;i++)p.tick();p.setOnGround(true);
            var state=p.getData(ModAttachments.PLAYER_STATE);state.unlock(PowerForm.WHIPFIST);state.activate(PowerForm.WHIPFIST);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);target.setNoAi(true);
            target.setPos(p.position().add(0,0,.7));h.getLevel().addFreshEntity(target);
            var start=WeaponCombatSystem.class.getDeclaredMethod("start",net.minecraft.server.level.ServerPlayer.class,WeaponMove.class,boolean.class,WeaponMove.Input.class,float.class);
            start.setAccessible(true);start.invoke(null,p,WeaponMove.WHIP_SWEEP,false,WeaponMove.Input.SPECIAL,0F);
            int began=p.tickCount;
            for(int t=0;t<=WeaponMove.WHIP_SWEEP.duration;t++){
                p.tickCount=began+t;p.setOnGround(true);target.setPos(p.position().add(0,0,.7));
                WeaponCombatSystem.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
            }
            h.assertTrue(contacts.get()==9,"one close target receives nine real damage contacts, got "+contacts.get());
        }finally{WeaponCombatSystem.cancel(p);target.discard();FeedbackLifecycleGameTest.cleanup(p);}h.succeed();
    }
}
