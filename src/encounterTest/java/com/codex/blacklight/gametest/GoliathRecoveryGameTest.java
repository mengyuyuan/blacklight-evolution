package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.EncounterMove;
import com.codex.blacklight.combat.GoliathContact;
import com.codex.blacklight.combat.GoliathMotion;
import com.codex.blacklight.combat.GoliathRecovery;
import com.codex.blacklight.combat.GoliathPresentation;
import com.codex.blacklight.entity.EncounterKind;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.registry.EncounterEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("blacklight")
@PrefixGameTestTemplate(false)
public final class GoliathRecoveryGameTest {
    private static EncounterMob createGoliath(GameTestHelper helper) {
        return EncounterEntities.CREATURES.get(EncounterKind.GOLIATH).get().create(helper.getLevel());
    }

    @GameTest(template="blackwatch_arena", batch="goliath_wall_recovery", timeoutTicks=220)
    public static void physicalWallHitRecoversAndReturnsToAutonomousCombat(GameTestHelper helper) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            helper.setBlock(x, 1, z, Blocks.STONE);
            for (int y = 2; y < 24; y++) helper.setBlock(x, y, z, Blocks.AIR);
        }
        EncounterMob actor = createGoliath(helper);
        actor.setPos(helper.absoluteVec(new Vec3(8, 2, 8)));
        actor.setYRot(0);
        actor.yBodyRot = actor.yBodyRotO = 0;
        actor.goalSelector.removeAllGoals(goal -> goal instanceof WaterAvoidingRandomStrollGoal);
        actor.targetSelector.removeAllGoals(goal -> true);
        actor.setNoGravity(true);
        helper.getLevel().addFreshEntity(actor);
        var target = helper.spawn(EntityType.PIG, new Vec3(8, 2, 12));
        target.setNoAi(true);
        target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.setHealth(1000);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        helper.runAfterDelay(25, () -> helper.assertTrue(actor.begin(EncounterMove.CHARGE, target), "charge starts"));

        boolean[] wall = {false};
        boolean[] checkedExitSample = {false};
        boolean[] done = {false};
        long[] exposureStart = {-1};
        Vec3[] impactOrigin = {null};
        helper.onEachTick(() -> {
            if (done[0]) return;
            long now = helper.getLevel().getGameTime();
            if (!wall[0] && actor.goliathChargeSnapshot().phase() == 3) {
                var body = actor.getBoundingBox();
                int z = (int)Math.ceil(body.maxZ + .1);
                for (int x = (int)Math.floor(body.minX) - 2; x <= (int)Math.ceil(body.maxX) + 2; x++) {
                    for (int y = (int)Math.floor(body.minY); y <= (int)Math.ceil(body.maxY) + 2; y++) {
                        helper.getLevel().setBlockAndUpdate(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
                    }
                }
                wall[0] = true;
            }
            if (wall[0] && exposureStart[0] < 0 && actor.exposed()) {
                var tag = new CompoundTag();
                actor.saveWithoutId(tag);
                exposureStart[0] = tag.getLong("EncounterExposedStart");
                impactOrigin[0] = actor.position();
                helper.assertTrue(actor.exposedDuration() == GoliathRecovery.WALL_TICKS, "wall and pose share the synchronized duration");
                helper.assertTrue(actor.goliathCrashSample() != 0, "wall reaction retains the outgoing charge pose");
                helper.assertTrue(actor.move() == EncounterMove.NONE && actor.goliathChargeSnapshot().phase() == -1,
                    "physical contact ends the charge phase before it can hit again");
                // Put the enemy behind the boss, on the same side of the wall.
                target.setPos(actor.getX(), actor.getY(), actor.getZ() - 5.2);
                actor.setTarget(target);
            }
            if (exposureStart[0] < 0) return;
            long elapsed = now - exposureStart[0];
            if (elapsed < GoliathRecovery.WALL_TICKS) {
                helper.assertTrue(actor.exposed() && actor.move() == EncounterMove.NONE, "no attack starts during recovery");
                helper.assertTrue(actor.position().distanceTo(impactOrigin[0]) < .05, "charge root motion stays stopped");
                if (elapsed == 20) checkInterruptionAndHurtPose(helper, actor);
                if (elapsed == GoliathRecovery.WALL_TICKS - 1) {
                    checkedExitSample[0] = true;
                    helper.assertTrue(actor.exposedAge(1) == actor.exposedDuration(), "last interpolated frame reaches the end of the rise");
                }
                return;
            }
            helper.assertTrue(!actor.exposed(), "weak point window closes on the recovery deadline");
            if (actor.move() != EncounterMove.NONE) {
                helper.assertTrue(checkedExitSample[0], "the rise was sampled before returning to combat");
                helper.assertTrue(actor.goliathChargeSnapshot().phase() == -1, "next attack did not resume the interrupted charge");
                done[0] = true;
                actor.discard();
                target.discard();
                helper.succeed();
            }
        });
    }

    private static void checkInterruptionAndHurtPose(GameTestHelper helper, EncounterMob actor) {
        var pose = GoliathMotion.sample("exposed", 242 * 20F / 30F, false);
        var expected = GoliathContact.regions(pose, actor.position(), actor.yBodyRot, 0, GoliathPresentation.SCALE);
        var actual = actor.hurtRegions(0);
        helper.assertTrue(actual.get(0).bounds().getCenter().distanceTo(expected.get(0).bounds().getCenter()) < .001,
            "real weak point follows the source ground-contact hold");
        actor.cancelAttack();
        helper.assertTrue(actor.exposed() && actor.move() == EncounterMove.NONE && actor.goliathChargeSnapshot().phase() == -1,
            "attack cancellation does not restart or clear the wall reaction");

        var tag = new CompoundTag();
        actor.saveWithoutId(tag);
        EncounterMob restored = createGoliath(helper);
        restored.load(tag);
        helper.assertTrue(restored.exposed() && restored.move() == EncounterMove.NONE && restored.goliathChargeSnapshot().phase() == -1
            && restored.exposedAge(0) == actor.exposedAge(0) && restored.goliathCrashSample() == actor.goliathCrashSample(), "reload restores the same pose without replaying the charge");
        restored.setHealth(0);
        helper.assertTrue(!restored.exposed(), "death overrides the recovery state");
        restored.discard();
    }
}
