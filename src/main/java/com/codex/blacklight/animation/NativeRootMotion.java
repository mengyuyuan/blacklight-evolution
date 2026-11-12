package com.codex.blacklight.animation;

import com.codex.blacklight.combat.ClawAttackMotion;
import com.codex.blacklight.combat.ClawCombatState;
import com.codex.blacklight.combat.CombatTargeting;
import com.codex.blacklight.combat.LocalWeaponMotion;
import com.codex.blacklight.combat.WeaponMove;
import com.codex.blacklight.network.NativeRootMotionPayload;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Coord drives the physical actor once, on the server, before that tick's swept contacts. */
public final class NativeRootMotion {
    /** Committed-swing travel multiplier per hit tier, applied on top of the authored clip path. */
    public static double travelScale(com.codex.blacklight.combat.WeaponMove move) {
        return switch (com.codex.blacklight.combat.ImpactTier.of(move)) { case LIGHT -> 1.12; case MEDIUM -> 1.2; case HEAVY -> 1.32; case FINISHER -> 1.4; };
    }
    private NativeRootMotion() {}

    public static boolean controlsGroundRoot(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.isPassenger() || !player.onGround()
                || player.getAbilities().flying || player.isInWater() || player.isInLava()
                || !com.codex.blacklight.mode.PrototypeModeSystem.isActive(player)
                || player.getData(ModAttachments.CONSUMING)) return false;
        var visual = player.getData(ModAttachments.WEAPON_VISUAL);
        var move = visual.spec();
        if (move != null) return !move.aerial() && !visual.traveling()
                && player.level().getGameTime() - visual.started() <= (visual.charging() ? 180 : move.duration);
        return switch (player.getData(ModAttachments.CLAW_COMBAT).action()) {
            case AUTO_1, AUTO_2, AUTO_3 -> true;
            default -> false;
        };
    }

    public static Vec3 clipStep(MatrixMotionClip clip, float previous, float current, float yaw) {
        if (current <= previous || !clip.joints().contains("Coord")) return Vec3.ZERO;
        var from = clip.sample("Coord", Math.max(0, previous), false);
        var to = clip.sample("Coord", Math.max(0, current), false);
        // Coord is authored in Blender's Z-up space. Convert its independent
        // displacement just like the root calibration: +Y is forward, +Z is up.
        double right = to.m30() - from.m30(), forward = to.m31() - from.m31();
        double angle = Math.toRadians(yaw), sin = Math.sin(angle), cos = Math.cos(angle);
        // The same actor-local right/forward basis is used by authored contact boxes.
        return new Vec3(-cos * right - sin * forward, 0, -sin * right + cos * forward);
    }

    public static Vec3 standOff(Vec3 step, Vec3 towardTarget, double available) {
        var horizontal = towardTarget.multiply(1, 0, 1);
        double distance = horizontal.length();
        if (distance < .0001) return step;
        var toward = horizontal.scale(1 / distance);
        double advance = step.dot(toward);
        return advance > Math.max(0, available) ? step.subtract(toward.scale(advance - Math.max(0, available))) : step;
    }

    public static void weapon(ServerPlayer player, WeaponMove move, int elapsedTick, float offset, float yaw) {
        var visual = player.getData(ModAttachments.WEAPON_VISUAL);
        // These actions already have authoritative flight/approach/ballistic velocity.
        if (move.aerial() || move == WeaponMove.BLADE_RUSH || visual.charging() || visual.traveling()) return;
        var definition = LocalWeaponMotion.find(move.id).orElseThrow(
                () -> new IllegalStateException("Missing independent motion: " + move.id));
        float previous = weaponClock(elapsedTick - 1, offset, definition.transitionSeconds());
        float current = weaponClock(elapsedTick, offset, definition.transitionSeconds());
        // Committed swings travel further into the target; heavier tiers carry more body.
        var step = clipStep(MotionLibrary.clip(move.id), previous, current, yaw)
                .scale(travelScale(move));
        if (!move.rush() && !move.quake()) {
            var target = player.level().getEntity(visual.targetId());
            if (target instanceof LivingEntity living && living.isAlive()) {
                var delta = living.position().subtract(player.position());
                double spacing = Math.max(.8, (player.getBbWidth() + living.getBbWidth()) * .5 + .15);
                step = standOff(step, delta, Math.max(0, delta.horizontalDistance() - spacing));
            }
        }
        apply(player, step);
    }

    public static float weaponClock(int elapsedTick,float offset,float transition) {
        float correction=offset-Math.round(offset*20)/20F;
        return Math.max(offset,elapsedTick/20F-transition+correction);
    }

    /** The displacement callback owns the same tick as its sharp-edge sweep.
     * A velocity packet lets client frame/packet timing move this attack an extra
     * tick, changing which tissue the otherwise identical ribbon intersects. */
    public static void bladeRush(ServerPlayer player,Vec3 direction,double distance) {
        player.setDeltaMovement(0,player.getDeltaMovement().y,0);
        player.hurtMarked=true;
        apply(player,direction.scale(distance));
    }

    public static void claw(ServerPlayer player, ClawCombatState combat) {
        double distance = ClawAttackMotion.forwardStep(combat.action(), combat.elapsedTicks());
        var direction = new Vec3(combat.dashX(), 0, combat.dashZ());
        if (direction.horizontalDistanceSqr() < .000001) return;
        var step = direction.normalize().scale(distance);
        var target = CombatTargeting.locked(player);
        if (target != null && target.isAlive()) {
            var delta = target.position().subtract(player.position());
            double spacing = Math.max(.8, (player.getBbWidth() + target.getBbWidth()) * .5 + .15);
            step = standOff(step, delta, Math.max(0, delta.horizontalDistance() - spacing));
        }
        // Legacy claws keep the live walk/run beneath their additive authored lunge.
        // Since this window suppresses stale absolute packets, its ordinary step is owned here too.
        if (controlsGroundRoot(player)) {
            var movement = player.getData(ModAttachments.MOVEMENT_STATE);
            var input = movement.wallInput();
            if (input.forward() != 0 || input.sideways() != 0) {
                float yaw = movement.cameraYaw(player.getYRot())
                        + (float)Math.toDegrees(Math.atan2(input.sideways(), input.forward()));
                double speed = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) * 2.16;
                if (input.sprint() && input.forward() > 0)
                    speed = Math.max(speed, player.getDeltaMovement().horizontalDistance());
                step = step.add(Vec3.directionFromRotation(0, yaw).scale(speed));
            }
        }
        apply(player, step);
    }

    private static void apply(ServerPlayer player, Vec3 step) {
        if (!player.isAlive()||player.isSpectator()||step.horizontalDistanceSqr() < 1e-10) return;
        var floor=player.level().getBlockState(net.minecraft.core.BlockPos.containing(
                player.getX(),player.getBoundingBox().minY-1,player.getZ()));
        boolean soulSpeed=floor.is(net.minecraft.tags.BlockTags.SOUL_SPEED_BLOCKS)
                &&net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentLevel(
                        player.level().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SOUL_SPEED),player)>0;
        if(!soulSpeed)step=step.scale(floor.getBlock().getSpeedFactor());
        var before = player.position();
        boolean grounded = player.onGround();
        player.move(MoverType.SELF, step);
        // A horizontal Coord step must not remove grounded jump/landing eligibility.
        if (grounded && player.level().getBlockCollisions(player,
                player.getBoundingBox().move(0, -.01, 0)).iterator().hasNext()) player.setOnGround(true);
        player.connection.resetPosition();
        if (player.position().distanceToSqr(before) < 1e-10) return;
        // Unregistered utility actors have no chunk subscription to update.
        if(player.serverLevel().players().contains(player))player.serverLevel().getChunkSource().move(player);
        var action = player.getData(ModAttachments.PLAYER_ACTION);
        PacketDistributor.sendToPlayer(player, new NativeRootMotionPayload(player.level().getGameTime(),
                action.revision(), player.getX(), player.getY(), player.getZ()));
    }
}
