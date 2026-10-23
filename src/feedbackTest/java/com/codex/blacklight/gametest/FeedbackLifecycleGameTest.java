package com.codex.blacklight.gametest;

import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.entity.BlacklightHive;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

/** Website #16/#17/#20. Actual server damage, death, attachment and reconstruction paths. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class FeedbackLifecycleGameTest {
    static ServerPlayer player(GameTestHelper h) {
        var profile = new GameProfile(UUID.randomUUID(), "FeedbackLife");
        var p = new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile) {
            @Override public void teleportTo(double x,double y,double z) { setPos(x,y,z); }
        };
        p.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;
        p.setPos(h.absolutePos(new BlockPos(4,22,4)).getCenter().add(0,-.5,0));
        for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)
            h.getLevel().setBlock(p.blockPosition().offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
        h.getLevel().addNewPlayer(p);
        var state=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);
        state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.CLAWS);state.activate(PowerForm.CLAWS);
        return p;
    }
    static void cleanup(ServerPlayer p) { p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED); }
    private static void tick(ServerPlayer p) { BiomassSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p)); }
    private static void finish(ServerPlayer p,Mob target) {
        target.setPos(p.position());target.setNoAi(true);p.serverLevel().addFreshEntity(target);
        ModAttachments.setData(p,ModAttachments.BIOMASS_FORM,new BiomassState(BiomassState.ABSORBING,
                p.level().getGameTime()-BiomassState.ABSORB_TICKS,true,"","claws",target.getId(),10));
        p.refreshDimensions();tick(p);
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="feedback_hive_death")
    public static void hiveDeathCannotReAddBossBar(GameTestHelper h) throws Exception {
        var p=player(h);var hive=ModEntities.BLACKLIGHT_HIVE.get().create(h.getLevel());
        hive.setPos(p.position().add(4,0,0));h.getLevel().addFreshEntity(hive);
        var field=BlacklightHive.class.getDeclaredField("boss");field.setAccessible(true);
        var boss=(ServerBossEvent)field.get(hive);
        hive.tickCount=20;hive.tick();
        h.assertTrue(boss.getPlayers().contains(p),"living hive tracks nearby player");
        hive.hurt(hive.damageSources().genericKill(),Float.MAX_VALUE);
        hive.tickCount=20;hive.tick();
        boolean clean=boss.getPlayers().isEmpty();
        hive.discard();cleanup(p);
        h.assertTrue(clean,"dead hive must not re-add its empty boss bar on audience refresh");h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="feedback_execution")
    public static void completedAbsorptionPassesExecutionThreshold(GameTestHelper h) {
        var p=player(h);
        Cow cow=new Cow(EntityType.COW,h.getLevel()) {
            @Override public boolean hurt(DamageSource source,float amount) {
                return super.hurt(source,amount<10000?Math.min(1,amount):amount);
            }
        };
        finish(p,cow);
        boolean committed=!cow.isAlive()&&BiomassSystem.state(p).consumed();
        cow.discard();cleanup(p);
        h.assertTrue(committed,"completed meal must not become an ordinary capped chip hit");h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="feedback_reject")
    public static void rejectedMealReleasesClaimAndNextMealWorks(GameTestHelper h) {
        var p=player(h);int before=ModAttachments.getData(p,ModAttachments.PLAYER_STATE).getBiomass();
        Cow protectedCow=new Cow(EntityType.COW,h.getLevel()) {
            @Override public boolean hurt(DamageSource source,float amount) { return false; }
        };
        finish(p,protectedCow);
        h.assertTrue(protectedCow.isAlive()&&BiomassSystem.claimant(protectedCow)==null
                &&BiomassSystem.state(p).phase()==BiomassState.CRAWLING,"rejected damage releases prey and player");
        h.assertTrue(ModAttachments.getData(p,ModAttachments.PLAYER_STATE).getBiomass()==before,"rejected meal cannot award biomass");
        h.assertTrue(!BiomassSystem.edible(p,protectedCow),"immune target is temporarily skipped, rather than immediately recaptured");
        protectedCow.discard();
        var cow=EntityType.COW.create(h.getLevel());finish(p,cow);
        boolean recovered=BiomassSystem.state(p).consumed()&&!cow.isAlive();
        cow.discard();cleanup(p);h.assertTrue(recovered,"later prey remains consumable after a rejected meal");h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="feedback_mound")
    public static void preyDeathBlocksCannotTrapReformingPlayer(GameTestHelper h) {
        var p=player(h);
        Cow cow=new Cow(EntityType.COW,h.getLevel()) {
            @Override public void die(DamageSource source) {
                super.die(source);
                if(!isAlive())for(int y=0;y<3;y++)level().setBlock(blockPosition().above(y),Blocks.STONE.defaultBlockState(),2);
            }
        };
        finish(p,cow);
        h.assertTrue(BiomassSystem.state(p).consumed(),"death confirms meal");
        ModAttachments.setData(p,ModAttachments.BIOMASS_FORM,BiomassSystem.state(p).reform(h.getLevel().getGameTime()-BiomassState.REFORM_TICKS));
        tick(p);
        boolean restored=!BiomassSystem.active(p)&&h.getLevel().noCollision(p);
        cow.discard();cleanup(p);h.assertTrue(restored,"corpse blocks cannot embed the restored body");h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="feedback_wall")
    public static void contactVisibilityStillRejectsWalls(GameTestHelper h) {
        var p=player(h);
        ModAttachments.setData(p,ModAttachments.BIOMASS_FORM,new BiomassState(BiomassState.CRAWLING,0,true,"","claws",-1));
        p.refreshDimensions();
        var cow=EntityType.COW.create(h.getLevel());cow.setPos(p.position().add(1.7,0,0));h.getLevel().addFreshEntity(cow);
        for(int y=0;y<3;y++)h.getLevel().setBlock(p.blockPosition().offset(1,y,0),Blocks.STONE.defaultBlockState(),2);
        boolean blocked=!BiomassSystem.edible(p,cow);
        cow.discard();cleanup(p);h.assertTrue(blocked,"nearby prey behind a solid wall cannot be claimed");h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="feedback_death_veto")
    public static void cancelledPreyDeathCannotCommitMeal(GameTestHelper h) {
        var p=player(h);var cow=EntityType.COW.create(h.getLevel());
        java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingDeathEvent> veto=event->{
            if(event.getEntity()==cow){event.setCanceled(true);cow.setHealth(5);}
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(veto);
        try {
            finish(p,cow);
            h.assertTrue(cow.isAlive()&&!cow.isInvisible()&&!BiomassSystem.state(p).consumed()
                    &&BiomassSystem.claimant(cow)==null,"cancelled death keeps the prey alive and releases the meal");
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(veto);
            cow.discard();cleanup(p);
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="feedback_ceiling")
    public static void lowCeilingDoesNotTeleportThroughWall(GameTestHelper h) {
        var p=player(h);
        ModAttachments.setData(p,ModAttachments.BIOMASS_FORM,new BiomassState(BiomassState.REFORMING,
                h.getLevel().getGameTime()-40,true,"","claws",-1,10));p.refreshDimensions();
        BiomassReformation.prepare(p);var before=p.position();
        h.getLevel().setBlock(p.blockPosition().above(),Blocks.STONE.defaultBlockState(),2);
        tick(p);
        boolean waiting=BiomassSystem.state(p).phase()==BiomassState.READY&&p.position().equals(before);
        cleanup(p);h.assertTrue(waiting,"low tunnel remains crawlable instead of teleporting through its wall");h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=100,batch="feedback_player_death")
    public static void fatalSlimeThenRealDeathCanSaveAndReleaseTarget(GameTestHelper h) {
        var p=player(h);
        // Exercise the real LivingDeath event (including all other mod subscribers).
        p.setHealth(0);p.die(p.damageSources().generic());
        h.assertTrue(p.isAlive()&&BiomassSystem.state(p).fatal(),"lethal ordinary damage becomes death slime");
        var cow=EntityType.COW.create(h.getLevel());cow.setPos(p.position());h.getLevel().addFreshEntity(cow);
        ModAttachments.setData(p,ModAttachments.BIOMASS_FORM,BiomassSystem.state(p).absorb(cow.getId(),h.getLevel().getGameTime()));
        p.setHealth(0);p.die(p.damageSources().genericKill());
        h.assertTrue(!p.isAlive()&&!BiomassSystem.active(p)&&BiomassSystem.claimant(cow)==null,"actual death clears slime and victim claim");
        var saved=new net.minecraft.nbt.CompoundTag();p.saveWithoutId(saved);
        h.assertTrue(saved.contains("Health"),"dead player serializes without a missing attachment or crash");
        var reloaded=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"FeedbackReload"));
        reloaded.connection=p.connection;reloaded.load(saved);
        h.assertTrue(!BiomassSystem.active(reloaded)&&reloaded.getHealth()==0,"dead-player save reloads without reviving the stale form");
        cow.discard();cleanup(p);h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=100,batch="feedback_spore")
    public static void realSporeStahlAbsorption(GameTestHelper h) {
        var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("spore","stahl");
        var type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        // Run with the official optional Spore JAR to execute this integration fixture.
        if(type.isEmpty()){h.succeed();return;}
        var p=player(h);var mob=(Mob)type.get().create(h.getLevel());
        mob.setPos(p.position());mob.setNoAi(true);h.getLevel().addFreshEntity(mob);
        // The real boss positions its damageable parts during its first tick.
        mob.tick();mob.setHealth(mob.getMaxHealth()*.14F);
        // Multipart targets expose body parts above their root's feet. Contact a real
        // part, rather than asserting that empty space under the boss is edible.
        var part=com.codex.blacklight.combat.CombatTargeting.hitboxes(mob).get(0);
        p.setPos((part.minX+part.maxX)/2,Math.floor(part.minY),part.minZ-.45);
        for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)
            h.getLevel().setBlock(p.blockPosition().offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
        ModAttachments.setData(p,ModAttachments.BIOMASS_FORM,new BiomassState(BiomassState.ABSORBING,h.getLevel().getGameTime()-60,true,"","claws",mob.getId(),10));
        p.refreshDimensions();
        String before=" eligible="+com.codex.blacklight.combat.ConsumeSystem.eligibleBiomass(mob)
                +", edible="+BiomassSystem.edible(p,mob)+", damageable="+com.codex.blacklight.combat.CombatTargeting.damageable(p,mob)
                +", sight="+p.hasLineOfSight(mob)+", max="+mob.getMaxHealth()+", beforeHP="+mob.getHealth()
                +", ready="+com.codex.blacklight.combat.ConsumeSystem.healthReady(p,mob)
                +", multipart="+com.codex.blacklight.combat.CombatTargeting.multipart(mob)
                +", boxes="+com.codex.blacklight.combat.CombatTargeting.hitboxes(mob)+", playerBox="+p.getBoundingBox();
        tick(p);
        boolean committed=BiomassSystem.state(p).consumed()&&!mob.isAlive();
        String diagnostic=before+" phase="+BiomassSystem.state(p).phase()+", health="+mob.getHealth()+", removed="+mob.isRemoved()+", dead="+((com.codex.blacklight.mixin.LivingDeathAccess)mob).blacklight$deathAccepted()+", player="+p.position();
        if(committed) {
            ModAttachments.setData(p,ModAttachments.BIOMASS_FORM,BiomassSystem.state(p).reform(h.getLevel().getGameTime()-40));
            tick(p);
            committed=!BiomassSystem.active(p)&&h.getLevel().noCollision(p);
        }
        mob.discard();cleanup(p);
        h.assertTrue(committed,"real Spore stahl completes absorption and body reconstruction"+diagnostic+", finalPhase="+BiomassSystem.state(p).phase());h.succeed();
    }
}
