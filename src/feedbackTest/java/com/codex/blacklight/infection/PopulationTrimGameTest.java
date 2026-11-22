package com.codex.blacklight.infection;

import com.codex.blacklight.registry.ModEntities;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("blacklight")
@PrefixGameTestTemplate(false)
public final class PopulationTrimGameTest {
    @GameTest(template="wall_test", batch="ambient_population_trim", timeoutTicks=450)
    public static void removesSixteenPerPassAndLeavesProtectedMobs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos near = new BlockPos(origin.getX(), level.getMinBuildHeight() + 10, origin.getZ());
        BlockPos far = near.above(OutbreakConfig.TRIM_DISTANCE.get() + 8);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.isPositionEntityTicking(origin) && level.getGameTime() % 200 == 0,
                    "waiting for a scheduled population trim");
        }).thenExecute(() -> {
            var viewer = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "trim-observer"));
            viewer.setGameMode(GameType.SURVIVAL);
            viewer.moveTo(near.getX()+0.5, near.getY(), near.getZ()+0.5, 0, 0);
            level.addNewPlayer(viewer);
            List<Mob> fixture = new ArrayList<>();
            try {
                var nearby = spawn(level, near, fixture);
                var named = spawn(level, far, fixture);
                named.setCustomName(Component.literal("Protected"));
                var fighter = spawn(level, far, fixture);
                fighter.setTarget(viewer);
                var unrelated = spawn(level, far, fixture);
                unrelated.getPersistentData().remove(PopulationGuard.AMBIENT);
                var mount = spawn(level, far, fixture);
                var rider = spawn(level, far, fixture);
                helper.assertTrue(rider.startRiding(mount, true), "mounted pair established");
                var eligible = new ArrayList<Mob>();
                for (int i = 0; i < 25; i++) eligible.add(spawn(level, far, fixture));

                var initial = new ArrayList<Mob>();
                for (var entity : level.getAllEntities()) {
                    if (entity instanceof Mob mob && mob.isAlive()) initial.add(mob);
                }
                PopulationGuard.trim(level);
                helper.assertTrue(initial.stream().filter(Entity::isRemoved).count() == 16,
                        "one pass removes exactly sixteen live mobs when at least 25 qualify");
                for (int i = 0; i <= initial.size() / 16 && eligible.stream().anyMatch(Mob::isAlive); i++) {
                    PopulationGuard.trim(level);
                }
                helper.assertTrue(eligible.stream().allMatch(Entity::isRemoved), "later passes remove remaining idle births");
                helper.assertTrue(List.of(nearby, named, fighter, unrelated, mount, rider).stream().allMatch(Mob::isAlive),
                        "nearby, named, fighting, nonambient and mounted mobs survive every pass");
            } finally {
                for (Mob mob : fixture) mob.discard();
                level.removePlayerImmediately(viewer, Entity.RemovalReason.DISCARDED);
            }
        }).thenSucceed();
    }

    private static Mob spawn(ServerLevel level, BlockPos position, List<Mob> fixture) {
        var mob = ModEntities.COMMON_INFECTED.get().create(level);
        mob.moveTo(position.getX()+0.5, position.getY(), position.getZ()+0.5, 0, 0);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.getPersistentData().putBoolean(PopulationGuard.AMBIENT, true);
        if (!level.addFreshEntity(mob)) throw new AssertionError("failed to register population fixture");
        fixture.add(mob);
        return mob;
    }
}
