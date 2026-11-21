package com.codex.blacklight.entity;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class SupremeHunterEmergenceGameTest {
    @GameTest(template="lost_convoy", batch="hunter_release", timeoutTicks=300)
    public static void blockedReleaseSurvivesReloadAndSpawnsOnce(GameTestHelper h) {
        var level = h.getLevel();
        var anchor = h.absolutePos(new BlockPos(10, 1, 10));
        var pos = new BlockPos(anchor.getX(), 230, anchor.getZ());
        var operator = UUID.randomUUID();
        h.startSequence().thenWaitUntil(() -> h.assertTrue(level.isPositionEntityTicking(pos), "release chunk is ticking"))
                .thenExecute(() -> {
                    var data = SupremeHunterEmergence.load(new CompoundTag());
                    data.schedule(Vec3.atBottomCenterOf(pos), operator, level.getGameTime());
                    data.tick(level);
                    var saved = data.save(new CompoundTag());
                    var requests = saved.getList("Pending", Tag.TAG_COMPOUND);
                    h.assertTrue(requests.size() == 1, "blocked release is retained");
                    var request = requests.getCompound(0);
                    var id = request.getUUID("Boss");
                    h.assertTrue(operator.equals(request.getUUID("Operator")) && request.getLong("Due") > level.getGameTime(),
                            "blocked release saves the operator and a future retry");
                    var reloaded = SupremeHunterEmergence.load(saved);
                    level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
                    h.runAfterDelay(101, () -> {
                        reloaded.tick(level);
                        var boss = level.getEntity(id);
                        h.assertTrue(boss instanceof BlacklightSupremeHunter, "restored release spawns the original request's boss");
                        h.assertTrue(reloaded.save(new CompoundTag()).getList("Pending", Tag.TAG_COMPOUND).isEmpty(),
                                "successful release clears the queue");
                        reloaded.tick(level);
                        h.assertTrue(level.getEntity(id) == boss, "next update preserves the same entity");
                        boss.discard();
                        level.removeBlock(pos.below(), false);
                        h.succeed();
                    });
                });
    }
}
