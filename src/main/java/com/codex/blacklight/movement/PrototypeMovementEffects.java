package com.codex.blacklight.movement;

import com.codex.blacklight.network.PhysicalFeedbackPayload;
import com.codex.blacklight.power.MovementUpgrade;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/** Server-side, performance-bounded physical feedback for Prototype traversal. */
public final class PrototypeMovementEffects {
    private static final DustParticleOptions BIOMASS_RED =
            new DustParticleOptions(new Vector3f(0.64F, 0.008F, 0.012F), 0.92F);
    private static final DustParticleOptions BIOMASS_DARK =
            new DustParticleOptions(new Vector3f(0.045F, 0.002F, 0.004F), 0.78F);

    private PrototypeMovementEffects() {
    }

    public static void onChargedJumpReleased(ServerPlayer player, double charge) {
        ServerLevel level = player.serverLevel();
        // Radial directed burst: the ground debris is blown OUTWARD by the launch
        // instead of drifting up in place, which is what made the old puff read as
        // a cheap effect rather than an explosion of the surface itself.
        BlockParticleOption terrain = terrainParticle(player);
        int rays = 6 + (int)Math.round(charge * 8.0D);
        double phase = (player.level().getGameTime() & 7L) * 0.17D;
        for (int ray = 0; ray < rays; ray++) {
            double angle = phase + Math.PI * 2.0D * ray / rays;
            double dirX = Math.cos(angle);
            double dirZ = Math.sin(angle);
            double outward = 0.20D + charge * 0.22D;
            double up = 0.16D + charge * 0.24D + (ray % 3) * 0.03D;
            spawnDirected(level, terrain,
                    player.getX() + dirX * 0.30D, player.getY() + 0.10D,
                    player.getZ() + dirZ * 0.30D,
                    dirX * outward, up, dirZ * outward);
        }
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.12D, player.getZ(),
                4 + (int)(charge * 7.0D), 0.34D, 0.08D, 0.34D, 0.055D + charge * 0.035D);
        level.sendParticles(BIOMASS_RED, player.getX(), player.getY() + 0.25D, player.getZ(),
                4 + (int)(charge * 9.0D), 0.30D, 0.34D, 0.30D, 0.045D);
        level.playSound(null, player.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM,
                SoundSource.PLAYERS, 0.45F + (float)charge * 0.5F,
                1.08F - (float)charge * 0.28F);
        feedback(player, PhysicalFeedbackPayload.Effect.JUMP_LAUNCH,
                0.35F + (float)charge * 0.55F);
    }

    public static void onAirDashStarted(ServerPlayer player, Vec3 forward) {
        ServerLevel level = player.serverLevel();
        Vec3 origin = player.position().add(0.0D, 1.0D, 0.0D).subtract(forward.scale(0.65D));
        level.sendParticles(ParticleTypes.CLOUD, origin.x, origin.y, origin.z,
                14, 0.38D, 0.48D, 0.38D, 0.13D);
        level.sendParticles(BIOMASS_RED, origin.x, origin.y, origin.z,
                18, 0.32D, 0.52D, 0.32D, 0.08D);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.0F, 0.52F);
        feedback(player, PhysicalFeedbackPayload.Effect.AIR_DASH, 0.92F);
    }

    public static void tickAirDash(ServerPlayer player, Vec3 forward) {
        ServerLevel level = player.serverLevel();
        Vec3 tail = player.position().add(0.0D, 0.95D, 0.0D).subtract(forward.scale(0.85D));
        level.sendParticles(BIOMASS_DARK, tail.x, tail.y, tail.z,
                5, 0.24D, 0.34D, 0.24D, 0.025D);
        level.sendParticles(ParticleTypes.CLOUD, tail.x, tail.y, tail.z,
                2, 0.18D, 0.25D, 0.18D, 0.018D);
    }

    public static void tickWallRun(ServerPlayer player, Vec3 forward) {
        if (player.tickCount % 2 != 0) return;
        ServerLevel level = player.serverLevel();
        double footHeight = ((player.tickCount / 2) & 1) == 0 ? 0.38D : 0.82D;
        Vec3 contact = player.position().add(forward.scale(0.34D)).add(0.0D, footHeight, 0.0D);
        BlockPos wallPos = BlockPos.containing(contact);
        BlockState wallState = level.getBlockState(wallPos);
        if (wallState.isAir()) wallState = level.getBlockState(player.blockPosition().below());
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, wallState),
                contact.x, contact.y, contact.z, 8, 0.24D, 0.26D, 0.24D, 0.085D);
        level.sendParticles(ParticleTypes.CLOUD, contact.x, contact.y, contact.z,
                3, 0.18D, 0.20D, 0.18D, 0.035D);
        level.sendParticles(BIOMASS_RED, contact.x, contact.y, contact.z,
                4, 0.16D, 0.24D, 0.16D, 0.035D);
        if (player.tickCount % 4 == 0) {
            level.sendParticles(ModParticles.IMPACT_SMOKE.get(),
                    contact.x, contact.y, contact.z, 1,
                    0.05D, 0.10D, 0.05D, 0.01D);
            level.playSound(null, wallPos, wallState.getSoundType().getStepSound(),
                    SoundSource.PLAYERS, 0.42F, 0.72F + level.random.nextFloat() * 0.18F);
        }
    }

    public static void onWallRunStarted(ServerPlayer player, Vec3 wallDirection) {
        ServerLevel level = player.serverLevel();
        Vec3 contact = player.position().add(wallDirection.scale(0.34D)).add(0.0D, 0.82D, 0.0D);
        BlockPos wallPos = BlockPos.containing(contact);
        BlockState wallState = level.getBlockState(wallPos);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, wallState),
                contact.x, contact.y, contact.z, 18,
                0.30D, 0.48D, 0.30D, 0.14D);
        level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE,
                contact.x, contact.y, contact.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ModParticles.DESTRUCTION_BURST.get(),
                contact.x, contact.y, contact.z, 2,
                0.10D, 0.22D, 0.10D, 0.025D);
        level.playSound(null, wallPos, SoundEvents.MACE_SMASH_GROUND,
                SoundSource.PLAYERS, 0.62F, 0.82F);
        feedback(player, PhysicalFeedbackPayload.Effect.WALL_RUN_START, 0.86F);
    }

    public static void onWallMantle(ServerPlayer player, Vec3 wallDirection) {
        ServerLevel level = player.serverLevel();
        Vec3 edge = player.position().add(wallDirection.scale(0.34D)).add(0.0D, 0.22D, 0.0D);
        level.sendParticles(ParticleTypes.CLOUD, edge.x, edge.y, edge.z,
                12, 0.32D, 0.16D, 0.32D, 0.08D);
        level.sendParticles(BIOMASS_RED, edge.x, edge.y, edge.z,
                10, 0.24D, 0.20D, 0.24D, 0.06D);
        level.sendParticles(ParticleTypes.SMALL_GUST, edge.x, edge.y, edge.z,
                3, 0.24D, 0.16D, 0.24D, 0.0D);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.7F, 0.78F);
        feedback(player, PhysicalFeedbackPayload.Effect.WALL_MANTLE, 0.92F);
    }

    public static void onGlideStarted(ServerPlayer player, Vec3 forward) {
        var level = player.serverLevel();
        var tail = player.position().add(0, 1, 0).subtract(forward.scale(.4));
        level.sendParticles(ParticleTypes.CLOUD, tail.x, tail.y, tail.z, 9, .45, .18, .45, .035);
        level.sendParticles(BIOMASS_RED, tail.x, tail.y, tail.z, 12, .4, .4, .4, .025);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, .65F, .7F);
    }

    public static void tickGlide(ServerPlayer player, Vec3 forward) {
        if (player.tickCount % 2 != 0) return;
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        for (int sign : new int[]{-1, 1}) {
            Vec3 tail = player.position().add(0, .82, 0)
                    .subtract(forward.scale(.40)).add(side.scale(sign * .38));
            Vec3 drift = forward.scale(-.12).add(0, -.015, 0);
            spawnDirected(player.serverLevel(), BIOMASS_DARK, tail.x, tail.y, tail.z, drift.x, drift.y, drift.z);
            spawnDirected(player.serverLevel(), BIOMASS_RED, tail.x, tail.y, tail.z, drift.x * .7, 0, drift.z * .7);
        }
    }

    public static void onSprintStarted(ServerPlayer player, Vec3 forward) {
        ServerLevel level = player.serverLevel();
        Vec3 burst = player.position().subtract(forward.scale(0.30D));
        level.sendParticles(terrainParticle(player), burst.x, player.getY() + 0.08D, burst.z,
                16, 0.42D, 0.10D, 0.42D, 0.13D);
        level.sendParticles(ParticleTypes.CLOUD, burst.x, player.getY() + 0.16D, burst.z,
                10, 0.38D, 0.12D, 0.38D, 0.10D);
        level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE,
                burst.x, player.getY() + 0.35D, burst.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(BIOMASS_RED, burst.x, player.getY() + 0.30D, burst.z,
                8, 0.30D, 0.28D, 0.30D, 0.055D);
        level.sendParticles(ModParticles.SPRINT_SHOCKWAVE.get(),
                player.getX(), player.getY() + 0.035D, player.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.72F, 0.62F);
        level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_STEP,
                SoundSource.PLAYERS, 0.42F, 0.88F);
        feedback(player, PhysicalFeedbackPayload.Effect.SPRINT_START, 0.95F);
    }

    public static void tickSprint(ServerPlayer player, Vec3 forward, double speed) {
        float intensity = PrototypeLocomotion.sprintVisualIntensity(speed,
                MovementTuningSettings.sprintMaxSpeed() * MovementUpgrade.sprintMultiplier(
                        player.getData(ModAttachments.PLAYER_STATE).getMovementUpgradeMask()));
        if (intensity > 0.22F && player.tickCount % 2 == 0) {
            pushSprintSlipstream(player, forward, intensity);
        }
        int interval = intensity > 0.72F ? 2 : intensity > 0.34F ? 3 : 4;
        if (player.tickCount % interval != 0) return;
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        double side = ((player.tickCount / interval) & 1) == 0 ? -0.18D : 0.18D;
        Vec3 foot = player.position().subtract(forward.scale(0.22D)).add(right.scale(side));
        ServerLevel level = player.serverLevel();
        if (WaterRunning.onSurface(player)) {
            level.sendParticles(ParticleTypes.SPLASH, foot.x, player.getY() + .025, foot.z,
                    5 + Math.round(intensity * 7), .22, .035, .22, .06);
            if (player.tickCount % (interval * 2) == 0)
                level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_SPLASH,
                        SoundSource.PLAYERS, .25F, 1.3F);
            return;
        }
        int footDebris = 4 + Math.round(intensity * 7.0F);
        level.sendParticles(terrainParticle(player), foot.x, player.getY() + 0.07D, foot.z,
                footDebris, 0.18D + intensity * 0.16D,
                0.045D, 0.18D + intensity * 0.16D, 0.05D + intensity * 0.065D);
        Vec3 tail = player.position().subtract(forward.scale(0.58D));
        if (player.isInWater()) {
            level.sendParticles(ParticleTypes.SPLASH, foot.x, player.getY() + 0.12D, foot.z,
                    5 + Math.round(intensity * 8.0F), 0.28D, 0.08D, 0.28D,
                    0.10D + intensity * 0.08D);
        } else if (intensity > 0.08F) {
            level.sendParticles(ParticleTypes.CLOUD, tail.x, player.getY() + 0.12D, tail.z,
                    2 + Math.round(intensity * 3.0F), 0.28D, 0.06D, 0.28D,
                    0.02D + intensity * 0.035D);
        }
        if (intensity > 0.30F) {
            level.sendParticles(BIOMASS_RED, foot.x, player.getY() + 0.11D, foot.z,
                    1 + Math.round(intensity * 2.0F), 0.12D, 0.06D, 0.12D, 0.018D);
            level.sendParticles(BIOMASS_DARK, tail.x, player.getY() + 0.32D, tail.z,
                    2, 0.22D, 0.18D, 0.22D, 0.022D);
        }
        if (player.tickCount % (interval * 2) == 0) {
            level.sendParticles(ModParticles.SPRINT_SHOCKWAVE.get(),
                    foot.x, player.getY() + 0.035D, foot.z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        if (intensity > 0.58F) {
            level.sendParticles(ParticleTypes.DUST_PLUME, foot.x,
                    player.getY() + 0.06D, foot.z, 1,
                    0.08D, 0.02D, 0.08D, 0.012D);
            level.sendParticles(ParticleTypes.SMALL_GUST, tail.x,
                    player.getY() + 0.45D, tail.z, 1,
                    0.18D, 0.28D, 0.18D, 0.0D);
            level.sendParticles(ParticleTypes.WHITE_ASH,
                    player.getX() + forward.x * 0.35D, player.getY() + 1.05D,
                    player.getZ() + forward.z * 0.35D,
                    3, 0.52D, 0.62D, 0.52D, 0.035D + intensity * 0.025D);
            if (player.tickCount % 4 == 0) {
                level.sendParticles(ModParticles.IMPACT_SMOKE.get(), foot.x,
                        player.getY() + 0.08D, foot.z, 1,
                        0.08D, 0.025D, 0.08D, 0.012D);
            }
        }
        if (player.tickCount % (interval * 2) == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_STEP,
                    SoundSource.PLAYERS, 0.12F + intensity * 0.26F,
                    1.22F - intensity * 0.36F);
        }
    }

    public static void onSprintCollision(ServerPlayer player, Vec3 forward, double speed) {
        float intensity = PrototypeLocomotion.sprintVisualIntensity(speed,
                MovementTuningSettings.sprintMaxSpeed());
        ServerLevel level = player.serverLevel();
        Vec3 contact = player.position().add(forward.scale(0.62D)).add(0.0D, 0.9D, 0.0D);
        BlockPos wall = BlockPos.containing(contact);
        BlockState wallState = level.getBlockState(wall);
        if (wallState.isAir()) {
            wall = BlockPos.containing(contact.add(0.0D, -0.65D, 0.0D));
            wallState = level.getBlockState(wall);
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, wallState),
                contact.x, contact.y, contact.z,
                12 + Math.round(intensity * 18.0F), 0.34D, 0.55D, 0.34D,
                0.10D + intensity * 0.08D);
        level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE,
                contact.x, contact.y, contact.z, 1,
                0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ModParticles.DESTRUCTION_BURST.get(),
                contact.x, contact.y, contact.z, 2,
                0.16D, 0.28D, 0.16D, 0.035D);
        level.playSound(null, wall, SoundEvents.MACE_SMASH_GROUND_HEAVY,
                SoundSource.PLAYERS, 0.48F + intensity * 0.45F,
                0.72F - intensity * 0.16F);
        feedback(player, PhysicalFeedbackPayload.Effect.SPRINT_COLLISION,
                0.55F + intensity * 0.65F);
    }

    private static void pushSprintSlipstream(ServerPlayer player, Vec3 forward, float intensity) {
        ServerLevel level = player.serverLevel();
        AABB wake = player.getBoundingBox().inflate(1.15D, 0.35D, 1.15D)
                .move(forward.scale(0.38D));
        for (Entity entity : level.getEntities(player, wake, entity ->
                entity instanceof LivingEntity || entity instanceof ItemEntity
                        || entity instanceof ExperienceOrb || entity instanceof Projectile)) {
            if (entity instanceof LivingEntity living && player.isAlliedTo(living)) continue;
            Vec3 side = entity.position().subtract(player.position()).multiply(1.0D, 0.0D, 1.0D);
            if (side.lengthSqr() < 1.0E-5D) side = new Vec3(-forward.z, 0.0D, forward.x);
            else side = side.normalize();
            double drive = 0.035D + intensity * 0.09D;
            double spread = 0.025D + intensity * 0.055D;
            entity.push(forward.x * drive + side.x * spread,
                    0.015D + intensity * 0.025D,
                    forward.z * drive + side.z * spread);
        }
    }

    public static LandingImpact.Profile onLanding(ServerPlayer player,
                                                  LandingImpact.Observation observation) {
        LandingImpact.Profile impact = LandingImpact.profile(
                observation.fallDistance(), observation.downwardSpeed());
        LandingImpact.Strength strength = impact.strength();
        if (strength == LandingImpact.Strength.NONE) return impact;

        double radius = impact.radius();
        int rings = switch (strength) {
            case LIGHT -> 1;
            case HEAVY -> 2;
            case DEVASTATING -> 3;
            default -> 0;
        };
        ServerLevel level = player.serverLevel();
        BlockPos centerSurface = surfaceNear(level, player.blockPosition().below(),
                player.getX(), player.getZ());
        double phase = Math.toRadians(player.getYRot())
                + (player.level().getGameTime() & 7L) * 0.17D;
        int debrisTotal = 0;
        int chunkBudget = switch (strength) {
            case HEAVY -> 5;
            case DEVASTATING -> 9;
            default -> 0;
        };
        for (int ring = 1; ring <= rings; ring++) {
            double ringRadius = radius * ring / rings;
            int points = Math.min(24, 8 + ring * 5
                    + (strength == LandingImpact.Strength.DEVASTATING ? 3 : 0));
            for (int point = 0; point < points; point++) {
                double angle = phase + Math.PI * 2.0D * point / points
                        + (ring & 1) * Math.PI / points;
                double jitter = 0.88D + ((point * 37 + ring * 13) % 11) / 50.0D;
                double distance = ringRadius * jitter;
                double x = player.getX() + Math.cos(angle) * distance;
                double z = player.getZ() + Math.sin(angle) * distance;
                BlockPos surface = surfaceNear(level, centerSurface, x, z);
                BlockState state = level.getBlockState(surface);
                double dirX = Math.cos(angle);
                double dirZ = Math.sin(angle);
                // The debris flies outward and up off the crack ring instead of
                // appearing in place: directed velocity is what sells the impact.
                spawnDirected(level, new BlockParticleOption(ParticleTypes.BLOCK, state),
                        x, surface.getY() + 1.04D, z,
                        dirX * (0.16D + impact.intensity() * 0.16D),
                        0.20D + impact.intensity() * 0.22D + (point % 3) * 0.035D,
                        dirZ * (0.16D + impact.intensity() * 0.16D));
                if (chunkBudget > 0 && (point & 1) == 0) {
                    // Real 3D ground chunks tossed on short ballistic arcs; the
                    // tracker discards them mid-air before they can ever land.
                    LandingDebrisSystem.toss(level, x, surface.getY() + 1.03D, z, state,
                            dirX * (0.10D + impact.intensity() * 0.10D),
                            0.30D + impact.intensity() * 0.10D + (point % 3) * 0.03D,
                            dirZ * (0.10D + impact.intensity() * 0.10D),
                            9 + (point * 7) % 4);
                    chunkBudget--;
                }
                debrisTotal++;
            }
        }
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.12D, player.getZ(),
                Math.max(5, debrisTotal / 2), radius * 0.46D, 0.12D,
                radius * 0.46D, 0.055D + impact.intensity() * 0.025D);
        level.sendParticles(ParticleTypes.DUST_PLUME,
                player.getX(), player.getY() + 0.09D, player.getZ(),
                1 + rings, radius * 0.24D, 0.06D, radius * 0.24D, 0.025D);
        if (strength != LandingImpact.Strength.LIGHT) {
            level.sendParticles(BIOMASS_RED, player.getX(), player.getY() + 0.18D, player.getZ(),
                    Math.max(8, debrisTotal / 2), radius * 0.45D, 0.18D,
                    radius * 0.45D, 0.045D);
            level.sendParticles(ModParticles.DESTRUCTION_BURST.get(),
                    player.getX(), player.getY() + 0.12D, player.getZ(),
                    strength == LandingImpact.Strength.DEVASTATING ? 5 : 3,
                    radius * 0.26D, 0.12D, radius * 0.26D, 0.065D);
            level.sendParticles(ModParticles.IMPACT_SMOKE.get(),
                    player.getX(), player.getY() + 0.16D, player.getZ(),
                    strength == LandingImpact.Strength.DEVASTATING ? 3 : 2,
                    radius * 0.18D, 0.08D, radius * 0.18D, 0.018D);
            level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE,
                    player.getX(), player.getY() + 0.18D, player.getZ(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        applyLandingPhysics(player, impact);

        PhysicalFeedbackPayload.Effect feedback = switch (strength) {
            case LIGHT -> PhysicalFeedbackPayload.Effect.LIGHT_LANDING;
            case HEAVY -> PhysicalFeedbackPayload.Effect.HEAVY_LANDING;
            case DEVASTATING -> PhysicalFeedbackPayload.Effect.DEVASTATING_LANDING;
            default -> PhysicalFeedbackPayload.Effect.LIGHT_LANDING;
        };
        float intensity = impact.intensity();
        level.playSound(null, centerSurface,
                strength == LandingImpact.Strength.LIGHT
                        ? SoundEvents.PLAYER_SMALL_FALL : SoundEvents.PLAYER_BIG_FALL,
                SoundSource.PLAYERS, 0.7F + intensity * 0.35F,
                strength == LandingImpact.Strength.DEVASTATING ? 0.62F : 0.82F);
        level.playSound(null, centerSurface, SoundEvents.RAVAGER_STEP,
                SoundSource.PLAYERS, 0.35F + intensity * 0.42F,
                0.92F - Math.min(0.3F, intensity * 0.15F));
        if (strength != LandingImpact.Strength.LIGHT) {
            level.playSound(null, centerSurface, SoundEvents.MACE_SMASH_GROUND_HEAVY,
                    SoundSource.PLAYERS, 0.62F + intensity * 0.35F,
                    strength == LandingImpact.Strength.DEVASTATING ? 0.55F : 0.72F);
        }
        if (strength == LandingImpact.Strength.DEVASTATING) {
            level.playSound(null, centerSurface, SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.PLAYERS, Math.min(1.5F, 0.55F + intensity * 0.48F), 0.58F);
            level.playSound(null, centerSurface, SoundEvents.WARDEN_SONIC_BOOM,
                    SoundSource.PLAYERS, 0.16F + intensity * 0.08F, 0.68F);
        }
        feedback(player, feedback, intensity);
        return impact;
    }

    private static void applyLandingPhysics(ServerPlayer player, LandingImpact.Profile impact) {
        ServerLevel level = player.serverLevel();
        double radius = impact.radius();
        double centerX = player.getX();
        double centerZ = player.getZ();
        AABB hitBox = new AABB(centerX - radius, player.getY() - 1.25D, centerZ - radius,
                centerX + radius, player.getY() + 3.5D, centerZ + radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, hitBox,
                target -> com.codex.blacklight.combat.CombatTargeting.damageable(player,target)
                        && com.codex.blacklight.combat.CombatTargeting.visible(player,target))) {
            if (!player.isAlive() || player.isRemoved() || player.level() != level) return;
            Vec3 away = radialDirection(player, target, centerX, centerZ);
            double distance = Math.sqrt(target.distanceToSqr(centerX, target.getY(), centerZ));
            if (distance > radius) continue;
            double falloff = 1.0D - distance / Math.max(0.01D, radius);
            float damage = (float)(impact.damage() * (0.38D + falloff * 0.62D));
            if (!target.hurt(player.damageSources().playerAttack(player), damage)) continue;
            // Damage callbacks may invalidate either actor; rejected hits cannot
            // bypass protection by applying their physical attack separately.
            if (!player.isAlive() || player.isRemoved() || player.level() != level) return;
            if (target.isRemoved() || target.level() != level) continue;
            double impulse = impact.knockback() * (0.52D + falloff * 0.48D);
            target.push(away.x * impulse, 0.18D + impulse * 0.48D, away.z * impulse);
            target.hurtMarked = true;
        }

        for (Entity loose : level.getEntities(player, hitBox,
                entity -> entity instanceof ItemEntity || entity instanceof ExperienceOrb
                        || entity instanceof Projectile)) {
            Vec3 away = radialDirection(player, loose, centerX, centerZ);
            double distance = Math.sqrt(loose.distanceToSqr(centerX, loose.getY(), centerZ));
            if (distance > radius) continue;
            double falloff = 1.0D - distance / Math.max(0.01D, radius);
            double impulse = impact.knockback() * (0.68D + falloff * 0.62D);
            loose.push(away.x * impulse, 0.22D + impulse * 0.38D, away.z * impulse);
        }
    }

    private static Vec3 radialDirection(ServerPlayer player, Entity entity,
                                        double centerX, double centerZ) {
        Vec3 away = new Vec3(entity.getX() - centerX, 0.0D, entity.getZ() - centerZ);
        if (away.lengthSqr() >= 1.0E-5D) return away.normalize();
        double yaw = Math.toRadians(player.getYRot());
        return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
    }

    private static BlockPos surfaceNear(ServerLevel level, BlockPos origin,
                                        double x, double z) {
        int blockX = (int)Math.floor(x);
        int blockZ = (int)Math.floor(z);
        for (int offset = 2; offset >= -3; offset--) {
            BlockPos candidate = new BlockPos(blockX, origin.getY() + offset, blockZ);
            BlockState state = level.getBlockState(candidate);
            if (!state.isAir() && !state.getCollisionShape(level, candidate).isEmpty()) {
                return candidate;
            }
        }
        return origin;
    }

    private static BlockParticleOption terrainParticle(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(player.blockPosition().below());
        return new BlockParticleOption(ParticleTypes.BLOCK, state);
    }

    /** A super-sprint shoulder hit: biomass splash, knockback thump, camera kick. */
    public static void onSprintBodyCheck(ServerPlayer player, LivingEntity target,
                                         Vec3 forward, float intensity) {
        ServerLevel level = player.serverLevel();
        Vec3 contact = target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D);
        level.sendParticles(BIOMASS_RED, contact.x, contact.y, contact.z,
                6 + Math.round(intensity * 8.0F), 0.24D, 0.30D, 0.24D, 0.05D);
        level.sendParticles(ParticleTypes.CLOUD, contact.x, contact.y, contact.z,
                3, 0.20D, 0.22D, 0.20D, 0.03D);
        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                SoundSource.PLAYERS, 0.55F + 0.40F * intensity, 0.85F - 0.15F * intensity);
        feedback(player, PhysicalFeedbackPayload.Effect.SPRINT_COLLISION,
                0.30F + 0.45F * intensity);
    }

    /**
     * Sends a single particle with an exact velocity. With count == 0 the vanilla
     * packet treats the spread fields as the velocity vector instead.
     */
    private static void spawnDirected(ServerLevel level, ParticleOptions type,
                                      double x, double y, double z,
                                      double vx, double vy, double vz) {
        level.sendParticles(type, x, y, z, 0, vx, vy, vz, 1.0D);
    }

    private static void feedback(ServerPlayer player, PhysicalFeedbackPayload.Effect effect,
                                 float intensity) {
        PacketDistributor.sendToPlayer(player, new PhysicalFeedbackPayload(effect, intensity));
    }
}
