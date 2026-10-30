package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.forge.BlacklightAttachments;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraftforge.gametest.*;

/** Real Forge entity/capability lifecycle; never shipped in the mod JAR. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class EntityRemovalGameTest {
    private static void finalDeathTick(GameTestHelper h, EntityType<? extends Mob> type) {
        finalDeathTick(h,type,true);
    }
    private static void finalDeathTick(GameTestHelper h, EntityType<? extends Mob> type,boolean noAi) {
        var mob = h.spawn(type, 3, 2, 3);
        mob.setNoAi(noAi);
        h.assertTrue(mob.getCapability(BlacklightAttachments.CAPABILITY).isPresent(), "live entity owns capability");
        ModAttachments.getData(mob, ModAttachments.CONSUME_VISUAL);
        mob.setHealth(0);
        mob.deathTime = 19;
        // Exercise the redirected server tick and all post-tick subscribers too.
        h.getLevel().tickNonPassenger(mob);
        h.assertTrue(mob.isRemoved(), "vanilla final death tick must remove mob");
        h.assertTrue(!mob.getCapability(BlacklightAttachments.CAPABILITY).isPresent(), "removed capability stays invalid");
        h.assertTrue(!ImpactControl.stunned(mob), "removed mob cannot retain stun");
        h.succeed();
    }

    @GameTest(template="wall_test", timeoutTicks=20)
    public static void bruteFinalDeathTick(GameTestHelper h) { finalDeathTick(h, ModEntities.BLACKLIGHT_BRUTE.get()); }
    @GameTest(template="wall_test", timeoutTicks=20)
    public static void stalkerFinalDeathTick(GameTestHelper h) { finalDeathTick(h, ModEntities.BLACKLIGHT_STALKER.get()); }
    @GameTest(template="wall_test", timeoutTicks=20)
    public static void spitterFinalDeathTick(GameTestHelper h) { finalDeathTick(h, ModEntities.BLACKLIGHT_SPITTER.get()); }
    @GameTest(template="wall_test", timeoutTicks=20)
    public static void hostFinalDeathTick(GameTestHelper h) { finalDeathTick(h, ModEntities.INFECTED_HOST.get()); }
    @GameTest(template="wall_test", timeoutTicks=20)
    public static void hunterFinalDeathTick(GameTestHelper h) { finalDeathTick(h, ModEntities.BLACKLIGHT_HUNTER.get()); }
    @GameTest(template="wall_test", timeoutTicks=20)
    public static void commonInfectedFinalDeathTick(GameTestHelper h) { finalDeathTick(h, ModEntities.COMMON_INFECTED.get()); }
    @GameTest(template="wall_test", timeoutTicks=20)
    public static void commonInfectedWithAiFinalDeathTick(GameTestHelper h) { finalDeathTick(h, ModEntities.COMMON_INFECTED.get(),false); }

    @GameTest(template="wall_test", timeoutTicks=20)
    public static void cancelAfterRemovalKeepsCapabilityInvalid(GameTestHelper h) {
        var mob = h.spawn(EntityType.PIG, 3, 2, 3);
        mob.setNoAi(true);
        ImpactControl.begin(mob, ImpactProfile.of(WeaponMove.HAMMER_JAB), 0);
        h.assertTrue(ImpactControl.stunned(mob), "fixture starts stunned");
        mob.discard();
        ImpactControl.cancel(mob);
        h.assertTrue(!ImpactControl.stunned(mob), "removal releases runtime control");
        h.assertTrue(!mob.getCapability(BlacklightAttachments.CAPABILITY).isPresent(), "cleanup cannot revive entity");
        h.succeed();
    }

    @GameTest(template="wall_test", timeoutTicks=30)
    public static void liveMutantStateAndStunStillWork(GameTestHelper h) {
        var mob = h.spawn(ModEntities.BLACKLIGHT_BRUTE.get(), 3, 2, 3);
        mob.setNoAi(true);
        ImpactControl.begin(mob, ImpactProfile.of(WeaponMove.HAMMER_JAB), 0);
        h.assertTrue(ImpactControl.stunned(mob), "live entity gets stun");
        h.getLevel().tickNonPassenger(mob);
        h.assertTrue(ModAttachments.getData(mob, ModAttachments.HIT_REACTION).duration() > 0, "live state remains usable");
        ImpactControl.cancel(mob);
        h.assertTrue(ModAttachments.getData(mob, ModAttachments.HIT_REACTION).duration() == 0, "live cancellation clears replicated pose");
        mob.discard();
        h.succeed();
    }
}
