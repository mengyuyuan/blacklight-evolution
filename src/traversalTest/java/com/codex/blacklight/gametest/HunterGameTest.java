package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.ConsumeSystem;
import com.codex.blacklight.entity.BlacklightHunter;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.registry.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HunterGameTest {
    @GameTest(template="wall_test", timeoutTicks=100)
    public static void infectionCreatesOneIndependentSpecies(GameTestHelper h) {
        var z=h.spawn(EntityType.ZOMBIE, 3,2,3);z.setNoAi(true);z.setInvulnerable(true);z.setPersistenceRequired();
        z.setCustomName(Component.literal("conversion-check"));z.setHealth(10);z.setItemSlot(EquipmentSlot.MAINHAND,Items.IRON_SWORD.getDefaultInstance());
        var box=z.getBoundingBox().inflate(3);
        h.assertTrue(BlacklightInfectionSystem.infect(z,null),"infection must start");
        float expectedRatio = z.getHealth() / z.getMaxHealth();
        h.runAfterDelay(35,()->h.assertTrue(!z.isRemoved(),"must wait for infection animation"));
        h.runAfterDelay(65,()->{
            var list=h.getLevel().getEntitiesOfClass(com.codex.blacklight.entity.BlacklightInfectedHost.class,box);
            h.assertTrue(z.isRemoved() && list.size()==1,"must replace zombie exactly once");
            var hunter=list.getFirst();
            h.assertTrue(Math.abs(hunter.getHealth()/hunter.getMaxHealth()-expectedRatio)<.01 && hunter.isPersistenceRequired(),"conversion must preserve health ratio and persistence");
            h.assertTrue(hunter.getMainHandItem().is(Items.IRON_SWORD) && hunter.getName().getString().equals("conversion-check"),"name and equipment must survive");
            h.assertTrue(BlacklightInfectionSystem.isInfected(hunter) && !hunter.hasEffect(ModEffects.BLACKLIGHT_INFECTION),"new species is inherently infected, without buff");
            h.assertTrue(!BlacklightInfectionSystem.infect(hunter,null),"hunter cannot be reinfected");h.succeed();
        });
    }
    @GameTest(template="wall_test", timeoutTicks=100)
    public static void removedInfectionDoesNotConvert(GameTestHelper h) {
        var z=h.spawn(EntityType.ZOMBIE,3,2,3);z.setNoAi(true);z.setInvulnerable(true);
        BlacklightInfectionSystem.infect(z,null);z.removeEffect(ModEffects.BLACKLIGHT_INFECTION);
        h.runAfterDelay(65,()->{h.assertTrue(z.isAlive()&&!z.isRemoved(),"cancelled infection must retain zombie");h.succeed();});
    }
    private static ServerPlayer player(GameTestHelper h) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"consume-test"));
        p.setPos(h.absoluteVec(new Vec3(3,2,3)));p.setYRot(0);p.setXRot(0);p.setOnGround(true);p.tickCount=100;
        var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);p.setData(ModAttachments.PLAYER_STATE,state);p.setHealth(10);
        return p;
    }
    private static BlacklightHunter victim(GameTestHelper h) {
        var target=h.spawn(ModEntities.BLACKLIGHT_HUNTER.get(),3,2,5);target.setNoAi(true);target.setHealth(10);return target;
    }
    @GameTest(template="wall_test", timeoutTicks=30)
    public static void consumptionClaimsAndPaysOnlyOnce(GameTestHelper h) {
        var p=player(h);var target=victim(h);int biomass=p.getData(ModAttachments.PLAYER_STATE).getBiomass();
        ConsumeSystem.request(p);h.assertTrue(target.isBeingConsumed(),"weakened nearby target must be grabbed");
        var other=player(h);ConsumeSystem.request(other);
        h.assertTrue(!other.getData(ModAttachments.CONSUMING),"second player cannot steal claim");
        for(int i=0;i<com.codex.blacklight.combat.ConsumeVisual.captureDuration(p.getData(ModAttachments.PLAYER_STATE).getActivePower());i++){p.tickCount++;ConsumeSystem.tick(new PlayerTickEvent.Post(p));}
        ConsumeSystem.consume(p);ConsumeSystem.consume(p);
        for(int i=0;i<com.codex.blacklight.combat.HunterConsume.COMPLETE_TICKS-1;i++){p.tickCount++;ConsumeSystem.tick(new PlayerTickEvent.Post(p));}
        h.assertTrue(!target.isRemoved()&&p.getHealth()==10,"no early removal or reward");
        p.tickCount++;ConsumeSystem.tick(new PlayerTickEvent.Post(p));ConsumeSystem.tick(new PlayerTickEvent.Post(p));
        h.assertTrue(target.isConsumeRemains()&&p.getHealth()==18,"completed consumption removes victim and heals");
        h.assertTrue(p.getData(ModAttachments.PLAYER_STATE).getBiomass()==biomass+120 && p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints()==30,"reward exactly once");
        h.assertTrue(!p.getData(ModAttachments.CONSUMING),"action lock must clear");h.succeed();
    }
    @GameTest(template="wall_test", timeoutTicks=30)
    public static void healthyFarAndInterruptedTargetsGiveNoReward(GameTestHelper h) {
        var p=player(h);var target=victim(h);target.setNoAi(false);target.setHealth(48);
        ConsumeSystem.request(p);h.assertTrue(target.isBeingConsumed(),"RC16 hunter original health below 50 allows direct capture");
        ConsumeSystem.cancel(p);
        target.setHealth(10);ConsumeSystem.request(p);h.assertTrue(target.isBeingConsumed(),"weak target can be claimed");
        var saved=new net.minecraft.nbt.CompoundTag();target.addAdditionalSaveData(saved);
        h.assertTrue(!saved.getBoolean("NoAI"),"transient grab must not persist disabled AI");
        p.setPos(p.position().add(8,0,0));ConsumeSystem.tick(new PlayerTickEvent.Post(p));
        h.assertTrue(target.isAlive()&&!target.isBeingConsumed()&&!target.isNoAi(),"interruption must release AI");
        ConsumeSystem.request(p);h.assertTrue(!target.isBeingConsumed(),"cannot consume across distance");
        h.assertTrue(p.getHealth()==10&&p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints()==0,"interruption gives no reward");h.succeed();
    }

    @GameTest(template="wall_test", timeoutTicks=60)
    public static void heavySwipeHasOneServerImpact(GameTestHelper h) {
        for(int x=1;x<8;x++)for(int z=1;z<8;z++)h.setBlock(x,1,z,net.minecraft.world.level.block.Blocks.STONE);
        var hunter=h.spawn(ModEntities.BLACKLIGHT_HUNTER.get(),3,2,3);
        var victim=h.spawn(EntityType.VILLAGER,3,2,4);victim.setNoAi(true);
        hunter.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(3);
        var hits=new java.util.concurrent.atomic.AtomicInteger();var total=new java.util.concurrent.atomic.AtomicReference<Float>(0F);
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post> listener=e->{
            if(e.getEntity()==victim&&e.getSource().getEntity()==hunter&&e.getNewDamage()>0){hits.incrementAndGet();total.set(total.get()+e.getNewDamage());}
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        hunter.setTarget(victim);h.assertTrue(hunter.beginAttack(com.codex.blacklight.combat.CreatureAttack.SWIPE,victim),"explicit swipe starts");
        h.runAfterDelay(30,()->{
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
            h.assertTrue(hits.get()==1&&Math.abs(total.get()-14)<.001,"one declared 14-point swipe, independent of subsequent infection HP rescaling: hits="+hits+" damage="+total);
            hunter.discard();victim.discard();h.succeed();
        });
    }

    @GameTest(template="wall_test", timeoutTicks=60)
    public static void hunterLightStaggerIsBoundedAndHeavyControlStillWorks(GameTestHelper h){
        for(int x=1;x<8;x++)for(int z=1;z<8;z++)h.setBlock(x,1,z,net.minecraft.world.level.block.Blocks.STONE);
        var hunter=h.spawn(ModEntities.BLACKLIGHT_HUNTER.get(),3,2,3);
        var victim=h.spawn(EntityType.VILLAGER,3,2,4);victim.setNoAi(true);var p=player(h);
        h.assertTrue(hunter.beginAttack(com.codex.blacklight.combat.CreatureAttack.SWIPE,victim),"hunter begins committed attack");
        h.runAfterDelay(5,()->{
            com.codex.blacklight.combat.WeaponImpact.apply(p,hunter,com.codex.blacklight.combat.WeaponMove.CLAW_AUTO1,new Vec3(0,0,1),false);
            h.assertTrue(com.codex.blacklight.combat.ImpactControl.stunned(hunter),"first light hit gets the bounded stagger");
        });
        h.runAfterDelay(17,()->{
            h.assertTrue(!com.codex.blacklight.combat.ImpactControl.stunned(hunter),"short light stagger recovers");
            com.codex.blacklight.combat.WeaponImpact.apply(p,hunter,com.codex.blacklight.combat.WeaponMove.CLAW_AUTO1,new Vec3(0,0,1),false);
            h.assertTrue(!com.codex.blacklight.combat.ImpactControl.stunned(hunter),"repeated light hits cannot permanently stun-lock the hunter");
            com.codex.blacklight.combat.WeaponImpact.apply(p,hunter,com.codex.blacklight.combat.WeaponMove.MUSCLE_UPPERCUT,new Vec3(0,0,1),false);
            h.assertTrue(com.codex.blacklight.combat.ImpactControl.stunned(hunter),"heavy launch still interrupts within the light-hit cooldown");
        });
        h.runAfterDelay(20,()->{h.assertTrue(!hunter.committedAttack(),"stunned hunter cannot restart an attack");hunter.discard();victim.discard();h.succeed();});
    }

    @GameTest(template="wall_test", timeoutTicks=30)
    public static void damageInterruptsAndReloadRestoresAi(GameTestHelper h) {
        var p=player(h);var target=victim(h);target.setNoAi(false);ConsumeSystem.request(p);
        h.assertTrue(target.isBeingConsumed(),"claim required before interrupt test");
        var tag=new net.minecraft.nbt.CompoundTag();target.saveWithoutId(tag);
        var restored=ModEntities.BLACKLIGHT_HUNTER.get().create(h.getLevel());restored.load(tag);
        h.assertTrue(!restored.isBeingConsumed()&&!restored.isNoAi(),"reload clears transient capture and restores original AI");
        h.assertTrue(target.hurt(target.damageSources().generic(),1),"real damage must reach the victim");
        h.assertTrue(target.isBeingConsumed()&&p.getData(ModAttachments.CONSUMING),"RC49 nonlethal damage keeps the claim");
        target.kill();p.tickCount++;ConsumeSystem.tick(new PlayerTickEvent.Post(p));
        h.assertTrue(!p.getData(ModAttachments.CONSUMING),"death releases capture without rewards");
        h.assertTrue(p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints()==0,"interrupted consumption must not pay");h.succeed();
    }
}
