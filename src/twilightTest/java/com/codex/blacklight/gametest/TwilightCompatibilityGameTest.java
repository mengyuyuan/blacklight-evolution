package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.config.CrossModCombatConfig;
import com.codex.blacklight.integration.twilight.TwilightCombat;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

/** Requires the real optional mod. A missing Twilight JAR is a failure, not a skipped pass. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class TwilightCompatibilityGameTest {
    private record Fixture(ServerPlayer player, Mob boss) implements AutoCloseable {
        public void close() {
            boss.discard();
            player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
    }
    private static Fixture fixture(GameTestHelper h, String id) {
        var key=ResourceLocation.fromNamespaceAndPath("twilightforest",id);
        h.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(key),"real Twilight Forest must be installed: "+id);
        var boss=(Mob)BuiltInRegistries.ENTITY_TYPE.get(key).create(h.getLevel());
        h.assertTrue(boss.getClass().getName().startsWith("twilightforest."),"use the actual Twilight boss implementation");
        boss.setPos(h.absolutePos(new BlockPos(4,22,4)).getCenter());boss.setNoAi(true);
        h.getLevel().addFreshEntity(boss);
        var profile=new GameProfile(UUID.randomUUID(),"TwilightQA");
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,ClientInformation.createDefault()) {
            @Override public void teleportTo(double x,double y,double z){setPos(x,y,z);}
        };
        p.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;
        p.setPos(boss.position().add(0,0,3));h.getLevel().addNewPlayer(p);
        var state=p.getData(ModAttachments.PLAYER_STATE);
        state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.CLAWS);state.activate(PowerForm.CLAWS);
        if(id.equals("hydra"))boss.tick();
        boss.invulnerableTime=0;
        return new Fixture(p,boss);
    }
    private static DamageSource nativeHit(Fixture f) { return f.boss.damageSources().playerAttack(f.player); }
    private static int shield(Mob boss) {
        try{return (int)boss.getClass().getMethod("getShieldStrength").invoke(boss);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static void shield(Mob boss,int value) {
        try{boss.getClass().getMethod("setShieldStrength",int.class).invoke(boss,value);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void hydraRootTakesNativeDamageExactlyOnce(GameTestHelper h) {
        try(var f=fixture(h,"hydra")) {
            float before=f.boss.getHealth();
            h.assertTrue(f.boss.hurt(nativeHit(f),10),"native root hit must reach Hydra's normal damage pipeline");
            float dealt=before-f.boss.getHealth();
            h.assertTrue(Math.abs(dealt-20)<.01,"one outgoing multiplier, no part armor division or duplicate hit: "+dealt);
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void hydraPartTakesNativeDamageExactlyOnce(GameTestHelper h) {
        try(var f=fixture(h,"hydra")) {
            float before=f.boss.getHealth();
            h.assertTrue(f.boss.getParts()[0].hurt(nativeHit(f),10),"native part hit must reach parent health");
            h.assertTrue(Math.abs(before-f.boss.getHealth()-20)<.01,"part hit must not amplify or apply damage twice");
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void ordinaryHydraDamageKeepsOriginalRules(GameTestHelper h) {
        try(var f=fixture(h,"hydra")) {
            f.player.getData(ModAttachments.PLAYER_STATE).setPrototypeMode(false);
            float before=f.boss.getHealth();
            h.assertTrue(!f.boss.hurt(nativeHit(f),10)&&f.boss.getHealth()==before,"ordinary parent hits remain rejected");
            h.assertTrue(f.boss.getParts()[0].hurt(nativeHit(f),16),"ordinary physical part hits still work");
            h.assertTrue(before-f.boss.getHealth()<16,"ordinary part armor is retained");
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void shieldedLichTakesNativeDamageAndBreaksShield(GameTestHelper h) {
        try(var f=fixture(h,"lich")) {
            shield(f.boss,5);float before=f.boss.getHealth();
            h.assertTrue(f.boss.hurt(nativeHit(f),10),"Blacklight must damage the real shielded Lich");
            h.assertTrue(f.boss.getHealth()<before&&shield(f.boss)==0,"accepted native hit breaks shield and damages health");
            h.assertTrue(Math.abs(before-f.boss.getHealth()-20)<.01,"Lich native damage is amplified once");
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void ordinaryLichDamageKeepsShield(GameTestHelper h) {
        try(var f=fixture(h,"lich")) {
            shield(f.boss,5);f.player.getData(ModAttachments.PLAYER_STATE).setPrototypeMode(false);
            float before=f.boss.getHealth();
            h.assertTrue(!f.boss.hurt(nativeHit(f),10),"ordinary attack remains blocked");
            h.assertTrue(f.boss.getHealth()==before&&shield(f.boss)==5,"ordinary combat and shield puzzle are unchanged");
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void nativeDiveKillsHydraThroughRealDeathPipeline(GameTestHelper h) {
        try(var f=fixture(h,"hydra")) {
            h.assertTrue(f.boss.hurt(BulletdiveDamage.source(f.player),10000),"dive energy must reach Hydra");
            h.assertTrue(!f.boss.isAlive()&&((com.codex.blacklight.mixin.LivingDeathAccess)f.boss).blacklight$deathAccepted(),
                    "native dive must trigger accepted normal death, not remove the entity");
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void biomassCanExecuteHydra(GameTestHelper h) {
        try(var f=fixture(h,"hydra")) {
            f.boss.setPos(f.player.position());f.boss.tick();
            f.player.setData(ModAttachments.BIOMASS_FORM,new com.codex.blacklight.power.BiomassState(
                    com.codex.blacklight.power.BiomassState.ABSORBING,h.getLevel().getGameTime()-60,true,"","claws",f.boss.getId(),10));
            f.player.refreshDimensions();
            String diagnostic=" eligible="+ConsumeSystem.eligibleBiomass(f.boss)
                    +", edible="+com.codex.blacklight.power.BiomassSystem.edible(f.player,f.boss)
                    +", sight="+f.player.hasLineOfSight(f.boss)+", source="+TwilightCombat.applies(f.boss,nativeHit(f),1000)
                    +", max="+f.boss.getMaxHealth()+", before="+f.boss.getHealth();
            com.codex.blacklight.power.BiomassSystem.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(f.player));
            h.assertTrue(!f.boss.isAlive()&&com.codex.blacklight.power.BiomassSystem.state(f.player).consumed(),
                    "actual aggregate absorption must execute Hydra and commit reconstruction"+diagnostic+", after="+f.boss.getHealth()+", phase="+com.codex.blacklight.power.BiomassSystem.state(f.player).phase());
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void configuredOptOutRetainsBossMechanics(GameTestHelper h) {
        boolean previous=CrossModCombatConfig.TRUE_DAMAGE.get();
        try(var f=fixture(h,"hydra")) {
            CrossModCombatConfig.TRUE_DAMAGE.set(false);
            h.assertTrue(!f.boss.hurt(nativeHit(f),10),"server true-damage opt-out restores Hydra immunity");
        }finally{CrossModCombatConfig.TRUE_DAMAGE.set(previous);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void explicitInvulnerabilityAndAdministrativeSourcesStayOutsideBridge(GameTestHelper h) {
        try(var f=fixture(h,"lich")) {
            h.assertTrue(!TwilightCombat.applies(f.boss,f.boss.damageSources().genericKill(),10),"kill command is not a native combat hit");
            f.boss.setInvulnerable(true);float before=f.boss.getHealth();shield(f.boss,5);
            h.assertTrue(!f.boss.hurt(nativeHit(f),10)&&f.boss.getHealth()==before&&shield(f.boss)==5,
                    "explicit Invulnerable flag is respected");
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void exposedMultipartContactFindsOneParent(GameTestHelper h) {
        try(var f=fixture(h,"hydra")) {
            var part=f.boss.getParts()[0];part.setPos(f.boss.position().add(35,0,0));
            AABB contact=part.getBoundingBox().deflate(.1);
            h.assertTrue(!contact.intersects(f.boss.getBoundingBox()),"fixture reaches only a real remote body part");
            var targets=CombatTargeting.candidates(f.player,contact,v->v==f.boss);
            h.assertTrue(targets.size()==1&&targets.get(0)==f.boss,"remote contact finds one parent without duplicate per-part damage");
            h.assertTrue(CombatTargeting.hitboxes(f.boss).stream().anyMatch(contact::intersects),"target range uses actual part geometry");
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void cancelledDamageDoesNotBreakLichShield(GameTestHelper h) {
        try(var f=fixture(h,"lich")) {
            shield(f.boss,5);float before=f.boss.getHealth();
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent> cancel=e->{
                if(e.getEntity()==f.boss)e.setCanceled(true);
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancel);
            try {
                h.assertTrue(!f.boss.hurt(nativeHit(f),10),"normal damage-event cancellation must still work");
                h.assertTrue(f.boss.getHealth()==before&&shield(f.boss)==5,"rejected damage cannot remove shield");
            } finally {net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);}
        }h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=30)
    public static void attachedSlimeFollowsMultipartSurfaceButCannotCrossWalls(GameTestHelper h) {
        try(var f=fixture(h,"hydra")) {
            var part=f.boss.getParts()[0];var box=part.getBoundingBox();var at=box.getCenter();
            f.player.setPos(at.x,box.minY+.05,at.z);
            f.player.setData(ModAttachments.BIOMASS_FORM,new com.codex.blacklight.power.BiomassState(
                    com.codex.blacklight.power.BiomassState.ABSORBING,h.getLevel().getGameTime(),true,"","claws",f.boss.getId(),10));
            f.player.refreshDimensions();
            var start=f.player.blockPosition();
            for(int x=-1;x<=4;x++)for(int y=0;y<=1;y++)for(int z=-1;z<=1;z++)
                h.getLevel().setBlock(start.offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
            com.codex.blacklight.power.BiomassSystem.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(f.player));
            var before=f.player.position();part.setPos(part.position().add(1,0,0));
            com.codex.blacklight.power.BiomassSystem.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(f.player));
            h.assertTrue(com.codex.blacklight.power.BiomassSystem.state(f.player).phase()==com.codex.blacklight.power.BiomassState.ABSORBING
                    &&Math.abs(f.player.getX()-before.x-1)<.01,"acquired slime follows the actual moving part");
            h.assertTrue(com.codex.blacklight.power.BiomassSystem.claimant(f.boss)==f.player,"moving part retains its valid consume claim");
            before=f.player.position();
            h.getLevel().setBlock(f.player.blockPosition().offset(1,0,0),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
            part.setPos(part.position().add(2,0,0));
            com.codex.blacklight.power.BiomassSystem.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(f.player));
            h.assertTrue(f.player.position().equals(before)
                    &&com.codex.blacklight.power.BiomassSystem.state(f.player).phase()==com.codex.blacklight.power.BiomassState.CRAWLING
                    &&com.codex.blacklight.power.BiomassSystem.claimant(f.boss)==null,"a blocked attachment releases without teleporting through terrain");
        }h.succeed();
    }
}
