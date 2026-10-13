package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** Opt-in integration checks against the installed, real Spore mod. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class SporeCompatibilityGameTest {
    private static Mob human(GameTestHelper h) {
        var id=ResourceLocation.fromNamespaceAndPath("spore","inf_human");
        h.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(id), "actual Spore mod must be loaded");
        for(int x=0;x<8;x++) for(int z=0;z<8;z++) {
            h.setBlock(x,1,z,Blocks.STONE);
            h.setBlock(x,5,z,Blocks.STONE);
        }
        var mob=(Mob)h.spawn(BuiltInRegistries.ENTITY_TYPE.get(id),3,2,3);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        h.assertTrue(mob.getClass().getName().contains("Spore"), "fixture is the Spore implementation");
        return mob;
    }
    private static <T> void state(Entity entity, java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<T>> key,T value) {
        entity.setData(key,value);
    }
    private static boolean has(Entity entity, java.util.function.Supplier<? extends net.neoforged.neoforge.attachment.AttachmentType<?>> key) {
        return entity.hasData(key.get());
    }

    @GameTest(template="wall_test",timeoutTicks=30)
    public static void realSporeMeleeAndKnockback(GameTestHelper h) {
        var attacker=human(h);
        var victim=h.spawn(EntityType.VILLAGER,3,2,4);
        victim.setNoAi(true);
        float before=victim.getHealth();
        attacker.doHurtTarget(victim);
        h.assertTrue(victim.getHealth()<before,"Spore melee must deal damage");
        attacker.setDeltaMovement(Vec3.ZERO);
        attacker.knockback(.4,1,0);
        h.assertTrue(attacker.getDeltaMovement().lengthSqr()>0,"ungrabbed Spore mob keeps normal knockback");
        h.assertTrue(!ConsumeSystem.grappleProtected(attacker),"ordinary Spore mob is not grapple protected");
        h.assertTrue(!has(attacker,ModAttachments.CONSUMING),"passive hooks do not allocate consuming state");
        h.assertTrue(!has(attacker,ModAttachments.CONSUME_VISUAL),"passive hooks do not allocate consume visuals");
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=160)
    public static void realSporeAttackGoalTicks(GameTestHelper h) {
        var attacker=human(h);
        var victim=h.spawn(EntityType.VILLAGER,3,2,4);
        victim.setNoAi(true);
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        victim.setHealth(200);
        attacker.setNoAi(false);
        attacker.setTarget(victim);
        h.runAtTickTime(100,()->{
            h.assertTrue(victim.getHealth()<200,"Spore's actual attack goal must land a hit during entity ticks");
            h.succeed();
        });
    }

    @GameTest(template="wall_test",timeoutTicks=30)
    public static void activeGrappleProtectionStillWorks(GameTestHelper h) {
        var mob=human(h);
        state(mob,ModAttachments.CONSUMING,true);
        state(mob,ModAttachments.GRAB_STAGE,ConsumeVisual.HOLD);
        h.assertTrue(ConsumeSystem.grappleProtected(mob),"active hold keeps its protection");
        mob.setDeltaMovement(Vec3.ZERO);
        mob.knockback(.4,1,0);
        h.assertTrue(mob.getDeltaMovement().lengthSqr()==0,"hold suppresses knockback");
        state(mob,ModAttachments.CONSUMING,false);
        state(mob,ModAttachments.GRAB_STAGE,ConsumeVisual.NONE);
        mob.knockback(.4,1,0);
        h.assertTrue(mob.getDeltaMovement().lengthSqr()>0,"release restores knockback");
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=30)
    public static void sporeRemovalReleasesControl(GameTestHelper h) {
        var mob=human(h);
        ImpactControl.begin(mob,ImpactProfile.of(WeaponMove.HAMMER_JAB),0);
        h.assertTrue(ImpactControl.stunned(mob),"real Spore mob accepts Blacklight impact control");
        mob.discard();
        ImpactControl.cancel(mob);
        h.assertTrue(!ImpactControl.stunned(mob),"removed mob is released");
        h.assertTrue(!ConsumeSystem.grappleProtected(mob),"removed mob is not protected");
        h.assertTrue(!ConsumeSystem.committed(mob),"removed mob is not consuming");
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=120)
    public static void blacklightCanInfectAndConvertSpore(GameTestHelper h) {
        var mob=human(h);
        h.assertTrue(BlacklightInfectionSystem.canInfect(mob),"Spore is not blacklisted from infection");
        h.assertTrue(BlacklightInfectionSystem.infect(mob,null),"Spore must accept the Blacklight infection");
        h.runAtTickTime(70,()->{
            h.assertTrue(mob.isRemoved(),"original Spore host is replaced after incubation");
            var hosts=h.getLevel().getEntitiesOfClass(BlacklightInfectedHost.class,mob.getBoundingBox().inflate(4),
                    host->host.speciesId().equals("spore:inf_human"));
            h.assertTrue(!hosts.isEmpty(),"converted host retains Spore species identity");
            h.succeed();
        });
    }

}
