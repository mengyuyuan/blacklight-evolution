package com.codex.blacklight.gametest;

import com.codex.blacklight.animation.ScapulaVolley;
import com.codex.blacklight.combat.BackspineCombat;
import com.codex.blacklight.combat.HeavyBlow;
import com.codex.blacklight.power.CombatSkill;
import com.codex.blacklight.power.CombatScale;
import com.codex.blacklight.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import com.codex.blacklight.combat.ImpactTier;
import com.codex.blacklight.combat.WeaponMove;
import com.codex.blacklight.entity.BackspineQuill;
import com.codex.blacklight.power.PowerForm;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaternionf;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class CombatFeedbackContactGameTest {
    @GameTest(template="wall_test", batch="feedback_quick_focus", timeoutTicks=180)
    public static void threeQuillsShareTwelveDamageOnOneTarget(GameTestHelper h) { fan(h, 1, false); }

    @GameTest(template="wall_test", batch="feedback_full_focus", timeoutTicks=180)
    public static void fiveQuillsShareTwentyOneDamageOnOneTarget(GameTestHelper h) { fan(h, 2, false); }

    @GameTest(template="wall_test", batch="feedback_full_spread", timeoutTicks=180)
    public static void separatedTargetsTakeOnlyTheQuillThatHitsThem(GameTestHelper h) { fan(h, 2, true); }

    private static void fan(GameTestHelper h, int pairs, boolean spread) {
        var origin = h.absolutePos(new BlockPos(3, 40, 3));
        var chunk = new ChunkPos(origin);
        // Keep all five targets and their three-block flight paths inside the ticking test chunk.
        var first = new BlockPos(chunk.getMinBlockX() + 2, origin.getY(), chunk.getMinBlockZ() + 9);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(h.getLevel().isPositionEntityTicking(first),
                "waiting for the volley lane to start ticking")).thenExecute(() -> fireFan(h, pairs, spread, first));
    }

    private static void fireFan(GameTestHelper h, int pairs, boolean spread, BlockPos first) {
        var shooter = MovingCombatGameTest.player(h, PowerForm.BACKSPINES);
        float total = pairs == 1 ? 12 : 21;
        var victims = new IronGolem[spread ? pairs * 2 + 1 : 1];
        for (int i = 0; i < victims.length; i++) {
            var victim = EntityType.IRON_GOLEM.create(h.getLevel());
            var position = first.east(i * 2);
            victim.moveTo(position.getX() + .5, position.getY(), position.getZ() + .5, 0, 0);
            victim.setNoAi(true);
            victim.setNoGravity(true);
            victim.setPersistenceRequired();
            h.assertTrue(h.getLevel().addFreshEntity(victim), "registered volley target " + i);
            MovingCombatGameTest.prepareDamageTarget(victim);
            victims[i] = victim;
        }
        for (int slot = -pairs; slot <= pairs; slot++) {
            var victim = victims[spread ? slot + pairs : 0];
            var q = new BackspineQuill(h.getLevel(), shooter, ScapulaVolley.damage(total, slot, pairs), slot < 0 ? 1 : 0);
            q.setHeavy(true);
            q.setVolleySlot(slot);
            q.setPos(victim.position().add(0, 1.2, -3));
            q.launch(new Vec3(0, 0, 1), 7, 1.6F, 0, 0, new Quaternionf());
            h.getLevel().addFreshEntity(q);
        }
        h.runAfterDelay(12, () -> {
            for (int i = 0; i < victims.length; i++) {
                var victim = victims[i];
                float expected = (spread ? ScapulaVolley.damage(total, i - pairs, pairs) : total) * CombatScale.UNITS;
                float lost = victim.getMaxHealth() - victim.getHealth();
                h.assertTrue(Math.abs(lost - expected) < .001F, "target " + i + " expected " + expected + " lost " + lost);
                victim.discard();
            }
            h.succeed();
        });
    }

    @GameTest(template="wall_test", batch="feedback_thrown_wall", timeoutTicks=55)
    public static void thrownBodyTakesOneSecondaryHitAtTheWall(GameTestHelper h) {
        var attacker = MovingCombatGameTest.player(h, PowerForm.HAMMERFISTS);
        for (int x = 2; x < 6; x++)
            for (int y = 9; y < 14; y++) h.setBlock(x, y, 5, Blocks.IRON_BLOCK);
        var body = h.spawn(EntityType.PIG, 3, 10, 3);
        body.setNoGravity(true);
        float start = MovingCombatGameTest.prepareDamageTarget(body);
        h.assertTrue(HeavyBlow.apply(attacker, body, WeaponMove.HAMMER_HOOK, ImpactTier.HEAVY, 0,
                new Vec3(0, 0, 1), true), "the authored hook must put a small body in flight");
        h.runAfterDelay(12, () -> {
            float after = body.getHealth();
            h.assertTrue(after < start && after > 0, "expected wall impact at " + body.position() + ", health " + after);
            h.runAfterDelay(8, () -> {
                h.assertTrue(body.getHealth() == after, "the same wall must not damage the body again");
                body.discard();
                h.succeed();
            });
        });
    }

    @GameTest(template="wall_test", batch="feedback_shoulder_relay", timeoutTicks=45)
    public static void movingRelayChangesRealShotTiming(GameTestHelper h) {
        // The same three taps arrive before the first shoulder has finished firing.
        IntPredicate taps = t -> t == 0 || t == 2 || t == 4;
        var plain = shots(h, 0, true, taps);
        var standing = shots(h, 1, false, taps);
        var moving = shots(h, 1, true, taps);
        h.assertTrue(plain.equals(standing) && plain.size() == 2 && moving.size() == 3
                && moving.get(1) == plain.get(1) - 2,
                "moving relay must advance the second shoulder and preserve the third tap: " + plain + " / " + standing + " / " + moving);
        // Let go just after the first shot, then compare the last retained tick and the first expired tick.
        var basic = shots(h, 1, false, t -> t <= 5 || t == 14);
        var retained = shots(h, 2, false, t -> t <= 5 || t == 14);
        var expired = shots(h, 2, false, t -> t <= 5 || t == 15);
        h.assertTrue(basic.size() == 2 && retained.size() == 2 && expired.size() == 2
                && retained.get(1) == 15 && basic.get(1) > 15 && expired.get(1) > 16,
                "loaded quill must fire in one tick only inside its eight-tick window: " + basic + " / " + retained + " / " + expired);
        h.succeed();
    }

    private static List<Integer> shots(GameTestHelper h, int tier, boolean moving, IntPredicate click) {
        var p = MovingCombatGameTest.player(h, PowerForm.BACKSPINES);
        var state = p.getData(ModAttachments.PLAYER_STATE);
        state.addEvolutionPoints(550);
        if (tier >= 1) h.assertTrue(state.buyCombatSkill(CombatSkill.QUILL_RELAY_1), "relay I setup");
        if (tier >= 2) h.assertTrue(state.buyCombatSkill(CombatSkill.QUILL_RELAY_2), "relay II setup");
        p.getData(ModAttachments.MOVEMENT_STATE).acceptInput(false, false, 0, moving ? 1 : 0, false, false);
        List<Integer> results = new ArrayList<>();
        for (int t = 0; t <= 25; t++) {
            p.tickCount++;
            BackspineCombat.input(p, click.test(t), false);
            BackspineCombat.tick(p);
            var emitted = h.getLevel().getEntitiesOfClass(BackspineQuill.class, p.getBoundingBox().inflate(8), q -> q.getOwner() == p);
            for (var quill : emitted) {
                results.add(t);
                quill.discard();
            }
        }
        BackspineCombat.cancel(p);
        return results;
    }
}
