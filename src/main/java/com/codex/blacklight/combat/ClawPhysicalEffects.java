package com.codex.blacklight.combat;

import com.codex.blacklight.network.PhysicalFeedbackPayload;
import com.codex.blacklight.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/** Directional biomass, impact and camera feedback for authoritative claw actions. */
public final class ClawPhysicalEffects {
    private static final DustParticleOptions BLOOD_RED =
            new DustParticleOptions(new Vector3f(0.78F, 0.006F, 0.01F), 1.05F);
    private static final DustParticleOptions BIOMASS_DARK =
            new DustParticleOptions(new Vector3f(0.035F, 0.001F, 0.003F), 0.92F);

    private ClawPhysicalEffects() {
    }

    public static void onDashStarted(ServerPlayer player, Vec3 forward) {
        Vec3 right = rightOf(forward);
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position().add(0.0D, 0.95D, 0.0D);
        for (int i = -4; i <= 4; i++) {
            Vec3 point = center.add(right.scale(i * 0.18D)).subtract(forward.scale(0.25D));
            level.sendParticles(i % 2 == 0 ? BLOOD_RED : BIOMASS_DARK,
                    point.x, point.y + Math.abs(i) * 0.055D, point.z,
                    2, 0.045D, 0.10D, 0.045D, 0.035D);
        }
        com.codex.blacklight.registry.ModWeaponSounds.play(player,com.codex.blacklight.power.PowerForm.CLAWS,"swing",player.position(),.75F,1F);
    }

    public static void onComboTargetHit(ServerPlayer player, LivingEntity target, boolean heavy) {
        ServerLevel level = player.serverLevel();
        Vec3 center = target.getBoundingBox().getCenter();
        level.sendParticles(BLOOD_RED, center.x, center.y, center.z,
                heavy ? 18 : 10, 0.34D, target.getBbHeight() * 0.30D, 0.34D, heavy ? 0.12D : 0.075D);
        level.sendParticles(BIOMASS_DARK, center.x, center.y, center.z,
                heavy ? 12 : 6, 0.30D, target.getBbHeight() * 0.26D, 0.30D, 0.055D);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, center.x, center.y, center.z,
                heavy ? 7 : 3, 0.28D, 0.3D, 0.28D, 0.10D);
    }

    public static void onComboImpact(ServerPlayer player, ClawCombatState.Action action,
                                     Vec3 forward, int hitCount) {
        boolean heavy = action == ClawCombatState.Action.AUTO_3;
        ServerLevel level = player.serverLevel();
        Vec3 right = rightOf(forward);
        Vec3 center = player.position().add(forward.scale(1.25D)).add(0.0D, 1.05D, 0.0D);
        int arcPoints = heavy ? 11 : 7;
        for (int i = 0; i < arcPoints; i++) {
            double normalized = i / (double)(arcPoints - 1) - 0.5D;
            Vec3 point = center.add(right.scale(normalized * (heavy ? 2.5D : 1.7D)))
                    .add(0.0D, (0.5D - Math.abs(normalized)) * 0.75D, 0.0D);
            level.sendParticles((i & 1) == 0 ? BLOOD_RED : BIOMASS_DARK,
                    point.x, point.y, point.z, heavy ? 2 : 1,
                    0.06D, 0.08D, 0.06D, 0.025D);
        }
        level.sendParticles(ModParticles.BIOMASS_SLASH.get(),
                center.x, center.y, center.z, heavy ? 2 : 1,
                heavy ? 0.28D : 0.08D, 0.14D, heavy ? 0.28D : 0.08D, 0.015D);
        com.codex.blacklight.registry.ModWeaponSounds.play(player,com.codex.blacklight.power.PowerForm.CLAWS,"swing",player.position(),.75F,1F);
        if (hitCount > 0) {
            feedback(player, heavy ? PhysicalFeedbackPayload.Effect.CLAW_HEAVY
                            : PhysicalFeedbackPayload.Effect.CLAW_HIT,
                    Math.min(1.25F, (heavy ? 0.95F : 0.55F) + (hitCount - 1) * 0.12F));
        }
    }

    public static void onDashingSliceImpact(ServerPlayer player, Vec3 forward, int hitCount) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position().add(forward.scale(0.85D)).add(0.0D, 1.0D, 0.0D);
        level.sendParticles(BLOOD_RED, center.x, center.y, center.z,
                24, 1.05D, 0.72D, 1.05D, 0.10D);
        level.sendParticles(BIOMASS_DARK, center.x, center.y, center.z,
                16, 0.92D, 0.62D, 0.92D, 0.065D);
        level.sendParticles(ModParticles.BIOMASS_SLASH.get(), center.x, center.y, center.z,
                3, 0.65D, 0.45D, 0.65D, 0.025D);
        com.codex.blacklight.registry.ModWeaponSounds.play(player,com.codex.blacklight.power.PowerForm.CLAWS,"swing",player.position(),.75F,1F);
        if (hitCount > 0) {
            feedback(player, PhysicalFeedbackPayload.Effect.CLAW_HEAVY,
                    Math.min(1.35F, 0.9F + hitCount * 0.1F));
        }
    }

    public static void onGroundSpikeImpact(ServerPlayer player, Vec3 center,
                                           double radius, BlockPos surface) {
        ServerLevel level = player.serverLevel();
        BlockState terrain = level.getBlockState(surface.below());
        BlockParticleOption terrainParticle = new BlockParticleOption(ParticleTypes.BLOCK, terrain);
        int[] ringPoints = {12, 18, 24};
        double[] ringScale = {0.28D, 0.58D, 0.92D};
        double phase = Math.toRadians(player.getYRot());
        for (int ring = 0; ring < ringPoints.length; ring++) {
            int points = ringPoints[ring];
            double ringRadius = radius * ringScale[ring];
            for (int point = 0; point < points; point++) {
                double angle = phase + Math.PI * 2.0D * point / points
                        + (ring & 1) * Math.PI / points;
                double x = center.x + Math.cos(angle) * ringRadius;
                double z = center.z + Math.sin(angle) * ringRadius;
                // count=0 gives fragments an explicit outward/upward velocity.
                level.sendParticles(terrainParticle, x, center.y + 0.08D, z,
                        0, Math.cos(angle) * 0.18D, 0.22D + ring * 0.045D,
                        Math.sin(angle) * 0.18D, 1.0D);
                if (point % 6 == 0) {
                    level.sendParticles(ParticleTypes.DUST_PLUME,
                            x, center.y + 0.09D, z, 1,
                            0.07D, 0.035D, 0.07D, 0.025D);
                }
            }
        }
        level.sendParticles(BLOOD_RED, center.x, center.y + 0.35D, center.z,
                10, radius * 0.38D, 0.24D, radius * 0.38D, 0.06D);
        level.sendParticles(BIOMASS_DARK, center.x, center.y + 0.3D, center.z,
                14, radius * 0.45D, 0.22D, radius * 0.45D, 0.045D);
        level.sendParticles(ModParticles.DESTRUCTION_BURST.get(),
                center.x, center.y + 0.2D, center.z, 5,
                radius * 0.28D, 0.22D, radius * 0.28D, 0.075D);
        level.sendParticles(ModParticles.IMPACT_SMOKE.get(),
                center.x, center.y + 0.3D, center.z, 3,
                radius * 0.18D, 0.12D, radius * 0.18D, 0.025D);
        level.playSound(null, surface, SoundEvents.SCULK_CATALYST_BLOOM,
                SoundSource.PLAYERS, 1.0F, 0.46F);
        level.playSound(null, surface, SoundEvents.MACE_SMASH_GROUND_HEAVY,
                SoundSource.PLAYERS, 1.25F, 0.62F);
        level.playSound(null, surface, SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 0.24F, 0.72F);
        feedback(player, PhysicalFeedbackPayload.Effect.GROUND_SPIKE, 1.4F);
    }

    private static Vec3 rightOf(Vec3 forward) {
        return new Vec3(-forward.z, 0.0D, forward.x);
    }

    private static void feedback(ServerPlayer player, PhysicalFeedbackPayload.Effect effect,
                                 float intensity) {
        PacketDistributor.sendToPlayer(player, new PhysicalFeedbackPayload(effect, intensity));
    }
}
