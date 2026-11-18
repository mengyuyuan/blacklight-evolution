package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.EncounterMove;
import com.codex.blacklight.entity.EncounterKind;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.registry.EncounterEntities;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("blacklight")
@PrefixGameTestTemplate(false)
public final class GoliathTerrainGameTest {
    private static EncounterMob spawn(GameTestHelper test, double z) {
        for (int x = 0; x < 16; x++) for (int column = 0; column < 16; column++) {
            test.setBlock(x, 1, column, Blocks.STONE);
            for (int y = 2; y < 24; y++) test.setBlock(x, y, column, Blocks.AIR);
        }
        var boss = EncounterEntities.CREATURES.get(EncounterKind.GOLIATH).get().create(test.getLevel());
        boss.setPos(test.absoluteVec(new Vec3(8, 2, z)));
        boss.setYRot(0);
        boss.yBodyRot = boss.yBodyRotO = 0;
        boss.goalSelector.removeAllGoals(goal -> true);
        boss.targetSelector.removeAllGoals(goal -> true);
        test.getLevel().addFreshEntity(boss);
        return boss;
    }

    @GameTest(template="blackwatch_arena", batch="goliath_terrain", timeoutTicks=30)
    public static void fourBlockWallStopsMovementAndThreeBlockLedgeClears(GameTestHelper test) {
        var boss = spawn(test, 6);
        for (int x = 3; x <= 12; x++) for (int z = 10; z <= 12; z++) {
            for (int y = 2; y < 6; y++) test.setBlock(x, y, z, Blocks.STONE);
        }
        test.runAfterDelay(10, () -> {
            test.assertTrue(boss.onGround(), "test starts from physical ground support");
            test.assertTrue(Math.abs(boss.getBbHeight() - 11) < .01, "movement box has the new height");
            Vec3 before = boss.position();
            boss.move(MoverType.SELF, new Vec3(0, -.08, 2));
            test.assertTrue(Math.abs(boss.getY() - before.y) < .05 && boss.getZ() < before.z + 1.2,
                "a four-block wall obstructs movement");

            for (int x = 3; x <= 12; x++) for (int z = 10; z <= 12; z++) test.setBlock(x, 5, z, Blocks.AIR);
            before = boss.position();
            boss.move(MoverType.SELF, new Vec3(0, -.08, 2));
            test.assertTrue(Math.abs(boss.getY() - before.y - 3) < .05 && boss.getZ() > before.z + 1.95,
                "after removing the top block the boss steps up three blocks at full horizontal travel");
            boss.discard();
            test.succeed();
        });
    }

    @GameTest(template="blackwatch_arena", batch="goliath_terrain", timeoutTicks=140)
    public static void groundNavigationTakesAThreeBlockLedge(GameTestHelper test) {
        var boss = spawn(test, 6);
        for (int x = 0; x < 16; x++) for (int z = 10; z < 19; z++) {
            for (int y = 2; y < 5; y++) test.setBlock(x, y, z, Blocks.STONE);
        }
        Vec3 start = boss.position();
        Vec3 destination = test.absoluteVec(new Vec3(8, 5, 14));
        var fromChunk = new ChunkPos(BlockPos.containing(start));
        var toChunk = new ChunkPos(BlockPos.containing(destination));
        var forced = new ArrayList<ChunkPos>();
        // The waypoint can cross a chunk boundary within the arena.
        for (int x = fromChunk.x - 1; x <= toChunk.x + 1; x++) {
            for (int z = fromChunk.z - 1; z <= toChunk.z + 1; z++) {
                if (test.getLevel().setChunkForced(x, z, true))
                    forced.add(new ChunkPos(x, z));
            }
        }
        test.runAfterDelay(10, () -> test.assertTrue(
            boss.getNavigation().moveTo(destination.x, destination.y, destination.z, 1),
            "ground navigation finds a route up the three-block ledge"));
        test.succeedWhen(() -> {
            Vec3 travel = boss.position().subtract(start);
            // A wide actor is considered at a waypoint before its center reaches it.
            test.assertTrue(Math.abs(travel.y - 3) < .05 && travel.z > 1,
                "the boss walks onto the ledge using its own navigation; displacement " + travel);
            boss.discard();
            for (var chunk : forced) test.getLevel().setChunkForced(chunk.x, chunk.z, false);
        });
    }

    @GameTest(template="blackwatch_arena", batch="goliath_terrain", timeoutTicks=125)
    public static void chargeStepsUpWithoutCancellingAsWallImpact(GameTestHelper test) {
        var boss = spawn(test, 8);
        var target = test.spawn(EntityType.PIG, new Vec3(8, 2, 13));
        target.setNoAi(true);
        target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.setHealth(1000);
        test.runAfterDelay(25, () ->
            test.assertTrue(boss.begin(EncounterMove.CHARGE, target), "committed source charge starts on flat ground"));

        Vec3[] approach = {null};
        boolean[] completed = {false};
        test.onEachTick(() -> {
            if (completed[0]) return;
            if (approach[0] == null && boss.goliathChargeSnapshot().phase() == 3) {
                approach[0] = boss.position();
                var body = boss.getBoundingBox();
                int y = (int)Math.floor(body.minY + .01);
                int z = (int)Math.ceil(body.maxZ + .1);
                for (int x = (int)Math.floor(body.minX) - 1; x <= (int)Math.ceil(body.maxX) + 1; x++) {
                    for (int dz = 0; dz < 3; dz++) for (int dy = 0; dy < 3; dy++)
                        test.getLevel().setBlockAndUpdate(new BlockPos(x, y + dy, z + dz), Blocks.STONE.defaultBlockState());
                }
            }
            if (approach[0] == null) return;
            test.assertTrue(!boss.exposed(), "a three-block ledge does not trigger the wall reaction");
            if (boss.getY() < approach[0].y + 2.9) return;
            test.assertTrue(boss.move() == EncounterMove.CHARGE && boss.getZ() > approach[0].z + .2,
                "physical step preserves the forward charge");
            completed[0] = true;
            boss.discard();
            target.discard();
            test.succeed();
        });
    }
}
