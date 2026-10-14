package com.codex.blacklight.combat;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.entity.GroundSpikeEntity;
import com.codex.blacklight.animation.PlayerAction;
import com.codex.blacklight.animation.PlayerActions;
import com.codex.blacklight.network.ClawActionPayload;
import com.codex.blacklight.network.ClawAnimationPayload;
import com.codex.blacklight.power.ClawUpgrade;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModEntities;
import com.codex.blacklight.state.BlacklightState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import com.codex.blacklight.forge.PacketDistributor;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class ClawCombatSystem {
    private static final int COMBO_GRACE_TICKS = 16;

    /** Per-combat-state targets already struck during the current dashing slice. */
    private static final Map<ClawCombatState, Set<LivingEntity>> DASH_HITS = new WeakHashMap<>();

    private ClawCombatSystem() {
    }

    public static void handle(ServerPlayer player, ClawActionPayload.Action request) {
        var heldBlock=BlockThrowSystem.held(player);
        if(heldBlock!=null){
            if(request==ClawActionPayload.Action.GRAB||request==ClawActionPayload.Action.THROW||request==ClawActionPayload.Action.ATTACK)heldBlock.throwFrom(player);
            else if(request==ClawActionPayload.Action.CONSUME)heldBlock.drop();
            return;
        }
        if (request == ClawActionPayload.Action.GRAB) { ConsumeSystem.request(player); return; }
        if (request == ClawActionPayload.Action.CONSUME) { ConsumeSystem.consume(player); return; }
        if (request == ClawActionPayload.Action.THROW) { ConsumeSystem.throwTarget(player); return; }
        if (com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.CONSUMING)) return;
        if (request == ClawActionPayload.Action.CYCLE_FORM) { com.codex.blacklight.power.PowerFormSystem.cycle(player); return; }
        BlacklightState state = com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE);
        ClawCombatState combat = com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.CLAW_COMBAT);
        BlacklightNeoMod.LOGGER.debug("Claw request {} from {} (power={}, action={})",
                request, player.getGameProfile().getName(), state.getActivePower().id(), combat.action());

        if (request == ClawActionPayload.Action.TOGGLE_CLAWS) {
            if(WeaponCombatSystem.busy(player))return;
            // Per-player minimum interval prevents morph-spam sound/particle floods.
            if (!combat.canToggleTransform(player.tickCount)) return;
            toggleClaws(player, state, combat);
            return;
        }

        if(WeaponMove.supports(state.getActivePower())) {
            if(request==ClawActionPayload.Action.ATTACK||request==ClawActionPayload.Action.SPRINT_ATTACK||request==ClawActionPayload.Action.DASHING_SLICE)WeaponCombatSystem.request(player,false);
            else if(request==ClawActionPayload.Action.WEAPON_SPECIAL||request==ClawActionPayload.Action.GROUND_SPIKE||request==ClawActionPayload.Action.GROUND_SPIKE_CHARGE_START)WeaponCombatSystem.requestSpecial(player);
            return;
        }
        if (!canUseClaws(state)) {
            player.displayClientMessage(Component.translatable("message.blacklight.claws_required"), true);
            return;
        }

        // Any accepted claw action cancels the short post-impact movement damping.
        // The visual landing recovery can still blend out underneath the attack.
        com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.MOVEMENT_STATE).cancelLandingRecovery();

        switch (request) {
            case ATTACK -> requestAttack(player, combat);
            case GROUND_SPIKE, GROUND_SPIKE_CHARGE_START ->
                    requestGroundSpikeCharge(player, combat);
            case GROUND_SPIKE_CHARGE_RELEASE -> {
                // Legacy RMB-up packets are deliberately ignored: the server erupts
                // exactly at the authored plant marker, so a physical release must
                // never cancel or double-trigger the one-shot action.
            }
            case SPRINT_ATTACK -> requestSprintAttack(player, combat);
            case DASHING_SLICE -> requestDashingSlice(player, state, combat);
            default -> { }
        }
    }

    private static void toggleClaws(ServerPlayer player, BlacklightState state, ClawCombatState combat) {
        if (!state.isInfected() || !state.hasUnlocked(PowerForm.CLAWS)) {
            player.displayClientMessage(Component.translatable("message.blacklight.not_infected"), true);
            return;
        }
        if (!state.isPrototypeMode()) {
            player.displayClientMessage(Component.translatable(
                    "message.blacklight.prototype_mode_required"), true);
            return;
        }

        boolean activate = state.getActivePower() != PowerForm.CLAWS;
        setClawsActive(player, state, activate);
        combat.markTransformToggled(player.tickCount);
    }

    private static void setClawsActive(ServerPlayer player, BlacklightState state,
                                       boolean activate) {
        if(activate)com.codex.blacklight.disguise.DisguiseSystem.visiblePower(player);
        state.activate(activate ? PowerForm.CLAWS : PowerForm.NONE);
        com.codex.blacklight.registry.ModAttachments.setData(player, ModAttachments.PLAYER_STATE, state);
        com.codex.blacklight.registry.ModAttachments.syncData(player, ModAttachments.PLAYER_STATE);
        playPrototypeTransition(player, activate);
        player.displayClientMessage(Component.translatable(
                activate ? "message.blacklight.claws_enabled" : "message.blacklight.claws_disabled"), true);
    }

    /** Plays the biological arm transition after a server-authoritative mode change. */
    public static void playPrototypeTransition(ServerPlayer player, boolean activate) {
        ClawCombatState combat = com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.CLAW_COMBAT);
        combat.reset();
        combat.begin(ClawCombatState.Action.TRANSFORM);
        play(player, PlayerAction.TRANSFORM_CLAWS);
        player.level().playSound(null, player.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM,
                SoundSource.PLAYERS, 0.8F, activate ? 0.72F : 0.55F);
        ServerLevel level = player.serverLevel();
        level.sendParticles(new DustParticleOptions(new Vector3f(0.72F, 0.015F, 0.02F), 1.45F),
                player.getX(), player.getY() + 1.0D, player.getZ(), activate ? 14 : 8,
                0.32D, 0.45D, 0.32D, 0.012D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                player.getX(), player.getY() + 0.95D, player.getZ(), activate ? 4 : 2,
                0.28D, 0.4D, 0.28D, 0.01D);
    }

    private static void requestAttack(ServerPlayer player, ClawCombatState combat) {
        if (combat.action() == ClawCombatState.Action.IDLE) {
            startNextAttack(player, combat);
        } else if (isComboAttack(combat.action())) {
            combat.queueAttack();
        }
    }

    private static void requestGroundSpikeCharge(ServerPlayer player, ClawCombatState combat) {
        if (combat.action() != ClawCombatState.Action.IDLE) return;
        if (!player.onGround()) {
            player.displayClientMessage(Component.translatable("message.blacklight.groundspike_ground_only"), true);
            return;
        }
        combat.beginGroundSpikeCharge();
        lockGroundSpikePose(player);
        play(player, PlayerAction.CLAW_GROUNDSPIKE);
        BlacklightNeoMod.LOGGER.debug("Groundspike charge started for {}",
                player.getGameProfile().getName());
    }

    private static void releaseGroundSpike(ServerPlayer player, ClawCombatState combat) {
        if (!player.onGround()) {
            combat.finish(0);
            return;
        }
        combat.beginGroundSpikeRecovery();
        lockGroundSpikePose(player);
        eruptGroundSpike(player);
        BlacklightNeoMod.LOGGER.debug("Groundspike released for {}",
                player.getGameProfile().getName());
    }

    private static void requestDashingSlice(ServerPlayer player, BlacklightState state,
                                            ClawCombatState combat) {
        boolean canBlendFromAttack = isComboAttack(combat.action())
                || combat.action() == ClawCombatState.Action.SPRINT_ATTACK;
        if (combat.action() != ClawCombatState.Action.IDLE && !canBlendFromAttack) return;
        if (!state.hasClawUpgrade(ClawUpgrade.DASHING_SLICE)) {
            player.displayClientMessage(Component.translatable(
                    "message.blacklight.dashing_slice_locked"), true);
            return;
        }

        if (canBlendFromAttack) combat.finish(0);

        Vec3 forward = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        combat.beginDash(forward.x, forward.z);
        player.setDeltaMovement(0.0D, Math.max(0.0D, player.getDeltaMovement().y), 0.0D);
        play(player, PlayerAction.CLAW_DASHING_SLICE);
        ClawPhysicalEffects.onDashStarted(player, forward);
    }

    /** Short left-click while running: a flowing slash that preserves forward momentum. */
    private static void requestSprintAttack(ServerPlayer player, ClawCombatState combat) {
        if (combat.action() != ClawCombatState.Action.IDLE || !player.onGround()) return;
        Vec3 forward = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        combat.beginSprintAttack(forward.x, forward.z);
        play(player, PlayerAction.CLAW_SPRINT_ATTACK);
        ClawPhysicalEffects.onDashStarted(player, forward);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent event) {if(event.phase != net.minecraftforge.event.TickEvent.Phase.END)return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if(WeaponCombatSystem.busy(player))return;

        if (com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.CONSUMING)) return;
        BlacklightState state = com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE);
        ClawCombatState combat = com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.CLAW_COMBAT);
        if (!canUseClaws(state)) {
            combat.reset();
            return;
        }

        if (combat.action() == ClawCombatState.Action.IDLE) {
            combat.tickIdle();
            return;
        }

        combat.advance();
        if (isComboAttack(combat.action())) {
            com.codex.blacklight.animation.NativeRootMotion.claw(player, combat);
            CombatAnimationSpec spec = attackSpec(combat.action());
            if (!combat.impactTriggered() && spec.isActive(combat.elapsedTicks())) {
                combat.markImpactTriggered();
                performClawHit(player, state, combat);
            }
            if (combat.queuedAttack() && combat.elapsedTicks() >= spec.cancelStart()) {
                startNextAttack(player, combat);
            } else if (combat.elapsedTicks() >= spec.duration()) {
                combat.finish(COMBO_GRACE_TICKS);
            }
        } else if (combat.action() == ClawCombatState.Action.GROUND_SPIKE_CHARGING) {
            lockGroundSpikePose(player);
            // A one-frame onGround flicker must not cancel the planted action; the
            // charged jump uses the same coyote-style grace on the movement side.
            if (!player.onGround()) {
                if (combat.observeGroundSpikeOffGround() > 2) combat.finish(0);
            } else {
                combat.observeGroundSpikeOnGround();
                if (combat.elapsedTicks() >= GroundSpikeRules.PLANT_TICKS) {
                    // Server-authoritative automatic eruption at the exact 2x-speed
                    // authored contact frame. Physical RMB release is irrelevant.
                    releaseGroundSpike(player, combat);
                }
            }
        } else if (combat.action() == ClawCombatState.Action.GROUND_SPIKE) {
            lockGroundSpikePose(player);
            // Hold through the eruption, then through the complete reversed hand
            // withdrawal. Movement resumes only after the claw is back out of the floor.
            int recoveryTicks = GroundSpikeEntity.ERUPTION_DURATION_TICKS
                    + GroundSpikeRules.WITHDRAW_TICKS;
            if (combat.elapsedTicks() >= recoveryTicks) combat.finish(0);
        } else if (combat.action() == ClawCombatState.Action.SPRINT_ATTACK) {
            tickSprintAttack(player, state, combat);
        } else if (combat.action() == ClawCombatState.Action.DASHING_SLICE) {
            tickDashingSlice(player, state, combat);
        } else if (combat.action() == ClawCombatState.Action.TRANSFORM
                && combat.elapsedTicks() >= CombatAnimationSpec.TRANSFORM_CLAWS.duration()) {
            combat.finish(0);
        }
    }

    private static void tickDashingSlice(ServerPlayer player, BlacklightState state,
                                         ClawCombatState combat) {
        CombatAnimationSpec spec = CombatAnimationSpec.CLAW_DASHING_SLICE;
        int elapsed = combat.elapsedTicks();
        if (elapsed >= spec.startupEnd() && elapsed < spec.activeEnd()) {
            double distance = state.hasClawUpgrade(ClawUpgrade.POUNCE) ? 7.5D : 6.0D;
            double step = distance / (spec.activeEnd() - spec.startupEnd());
            player.move(MoverType.SELF, new Vec3(combat.dashX() * step, 0.0D,
                    combat.dashZ() * step));

            // Sweep every active tick so the full 6.0-7.5 block dash connects.
            // The previous single-frame box only covered the first ~3.25 blocks
            // and let the player pass through targets in the back half of the dash.
            boolean firstSweep = !combat.impactTriggered();
            if (firstSweep) {
                combat.markImpactTriggered();
            }
            Set<LivingEntity> alreadyHit = DASH_HITS.computeIfAbsent(combat,
                    ignored -> new HashSet<>());
            AABB sweep = player.getBoundingBox().inflate(1.25D, 0.6D, 1.25D);
            for (LivingEntity target : CombatTargeting.candidates(player, sweep,
                     target -> CombatTargeting.damageable(player,target) && CombatTargeting.visibleContact(player,target,sweep)
                             && !alreadyHit.contains(target))) {
                alreadyHit.add(target);
                if (target.hurt(player.damageSources().playerAttack(player),
                        clawDamage(state, 16.0F))) {
                    if(!CombatTargeting.multipart(target))target.push(combat.dashX() * 0.9D, 0.3D, combat.dashZ() * 0.9D);
                    ClawPhysicalEffects.onComboTargetHit(player, target, true);
                }
            }
            // The slash trail and impact feedback fire on the first sweep tick, in
            // sync with the visible hit, instead of waiting for the dash to end.
            if (firstSweep) {
                ClawPhysicalEffects.onDashingSliceImpact(player,
                        new Vec3(combat.dashX(), 0.0D, combat.dashZ()), alreadyHit.size());
            }
        }
        if (elapsed >= spec.duration()) {
            DASH_HITS.remove(combat);
            combat.finish(0);
        }
    }

    private static void tickSprintAttack(ServerPlayer player, BlacklightState state,
                                         ClawCombatState combat) {
        CombatAnimationSpec spec = CombatAnimationSpec.CLAW_SPRINT_ATTACK;
        int elapsed = combat.elapsedTicks();
        Vec3 forward = new Vec3(combat.dashX(), 0.0D, combat.dashZ());
        if (elapsed >= spec.startupEnd() && elapsed < spec.activeEnd()) {
            double step = 1.8D / (spec.activeEnd() - spec.startupEnd());
            player.move(MoverType.SELF, forward.scale(step));
            if (!combat.impactTriggered()) {
                combat.markImpactTriggered();
                AABB sweep = player.getBoundingBox().inflate(0.95D, 0.55D, 0.95D)
                        .expandTowards(forward.scale(1.8D));
                int hitCount = 0;
                for (LivingEntity target : CombatTargeting.candidates(player, sweep,
                        target -> CombatTargeting.damageable(player,target) && CombatTargeting.visibleContact(player,target,sweep)).stream()
                        .sorted(CombatTargeting.contactPriority(player))
                        .limit(3).toList()) {
                    if (target.hurt(player.damageSources().playerAttack(player),
                            clawDamage(state, 9.0F))) {
                        if(!CombatTargeting.multipart(target))target.push(forward.x * 0.48D, 0.14D, forward.z * 0.48D);
                        hitCount++;
                        ClawPhysicalEffects.onComboTargetHit(player, target, false);
                    }
                }
                ClawPhysicalEffects.onComboImpact(player,
                        ClawCombatState.Action.AUTO_2, forward, hitCount);
            }
        }
        if (elapsed >= spec.duration()) combat.finish(COMBO_GRACE_TICKS);
    }

    private static void startNextAttack(ServerPlayer player, ClawCombatState combat) {
        ClawCombatState.Action action = combat.beginNextAttack();
        Vec3 forward = horizontalLook(player);
        combat.setAttackDirection(forward.x, forward.z);
        BlacklightNeoMod.LOGGER.debug("Starting claw combo action {} for {}",
                action, player.getGameProfile().getName());
        PlayerAction animation = switch (action) {
            case AUTO_1 -> PlayerAction.CLAW_AUTO_1;
            case AUTO_2 -> PlayerAction.CLAW_AUTO_2;
            default -> PlayerAction.CLAW_AUTO_3;
        };
        play(player, animation);
    }

    private static Vec3 horizontalLook(ServerPlayer player) {
        Vec3 forward = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (forward.lengthSqr() < 1.0E-5D) return new Vec3(0.0D, 0.0D, 1.0D);
        return forward.normalize();
    }

    private static void performClawHit(ServerPlayer player, BlacklightState state,
                                       ClawCombatState combat) {
        ClawCombatState.Action action = combat.action();
        // Lock the swing to the facing captured on its first frame so a mid-startup
        // turn cannot detach the damage sweep from the visible swing direction.
        Vec3 forward = new Vec3(combat.dashX(), 0.0D, combat.dashZ());
        if (forward.lengthSqr() < 1.0E-5D) forward = new Vec3(0.0D, 0.0D, 1.0D);
        forward = forward.normalize();
        double reach = ClawDamageRules.comboReach(action)*AttackReach.scale(player);
        AABB sweep = player.getBoundingBox().inflate(0.85D, 0.55D, 0.85D)
                .expandTowards(forward.scale(reach));
        Vec3 attackOrigin = player.position().add(0.0D, player.getBbHeight() * 0.55D, 0.0D);
        Vec3 attackForward = forward;
        float damage = clawDamage(state, ClawDamageRules.comboBaseDamage(action));
        int maxTargets = ClawDamageRules.comboMaxTargets(action);

        var targets = CombatTargeting.candidates(player, sweep,
                        target -> CombatTargeting.damageable(player,target) && CombatTargeting.visibleContact(player,target,sweep))
                .stream()
                .filter(target -> {
                    Vec3 direction = CombatTargeting.closestPoint(attackOrigin,target).subtract(attackOrigin)
                            .multiply(1.0D, 0.0D, 1.0D);
                    return direction.lengthSqr() < 1.0E-5D
                            || attackForward.dot(direction.normalize()) >= 0.15D;
                })
                .sorted(CombatTargeting.contactPriority(player))
                .limit(maxTargets)
                .toList();

        double knockback = action == ClawCombatState.Action.AUTO_3 ? 0.65D : 0.32D;
        int hitCount = 0;
        for (LivingEntity target : targets) {
            if (target.hurt(player.damageSources().playerAttack(player), damage)) {
                if(!CombatTargeting.multipart(target))target.push(forward.x * knockback,
                        action == ClawCombatState.Action.AUTO_3 ? 0.28D : 0.08D,
                        forward.z * knockback);
                hitCount++;
                ClawPhysicalEffects.onComboTargetHit(player, target,
                        action == ClawCombatState.Action.AUTO_3);
            }
        }
        ClawPhysicalEffects.onComboImpact(player, action, forward, hitCount);
    }

    static void eruptGroundSpike(ServerPlayer player) {
        if (!canUseClaws(com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE))) return;
        ServerLevel level = player.serverLevel();
        BlacklightState state = com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE);
        double rangeMultiplier = (state.hasClawUpgrade(ClawUpgrade.RANGE) ? 1.25D : 1.0D)*AttackReach.scale(player);
        var weapon = com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.WEAPON_VISUAL);
        Vec3 forward = weapon.spec() == WeaponMove.CLAW_GROUNDSPIKE
                ? Vec3.directionFromRotation(0, weapon.yaw()) : player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (forward.lengthSqr() < 0.001D) forward = new Vec3(0.0D, 0.0D, 1.0D);
        forward = forward.normalize();
        LivingEntity lockedTarget = lockedGroundSpikeTarget(player);
        if (CombatTargeting.locked(player)!=null && lockedTarget==null) return;
        Vec3 requested = lockedTarget == null
                ? player.position().add(forward.scale(
                GroundSpikeRules.BASE_CAST_DISTANCE * rangeMultiplier))
                : CombatTargeting.positionNear(player.position(),lockedTarget);
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(requested));
        double x = surface.getX() + 0.5D;
        double y = surface.getY();
        double z = surface.getZ() + 0.5D;

        double visualRadius = GroundSpikeRules.BASE_VISUAL_RADIUS * rangeMultiplier;
        spawnGroundSpikeField(level, new Vec3(x, y, z), player.getYRot(), visualRadius);

        double radius = GroundSpikeRules.BASE_DAMAGE_RADIUS * rangeMultiplier;
        AABB hitBox = new AABB(x - radius, y - 0.5D, z - radius,
                x + radius, y + 6.5D, z + radius);
        for (LivingEntity target : CombatTargeting.candidates(player, hitBox,
                target -> CombatTargeting.damageable(player,target))) {
            if (target.hurt(player.damageSources().playerAttack(player),
                    clawDamage(state, 14.0F))) {
                Vec3 away = target.position().subtract(x, y, z);
                if (away.lengthSqr() < 1.0E-5D) {
                    // A target standing exactly on the spike center gets a benign
                    // vertical pop instead of relying on Vec3.normalize() semantics.
                    double yaw = Math.toRadians(player.getYRot());
                    away = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
                } else {
                    away = away.normalize();
                }
                WeaponImpact.apply(player,target,WeaponMove.CLAW_GROUNDSPIKE,away.multiply(1,0,1).normalize(),false);
            }
        }
        for (Entity loose : level.getEntities(player, hitBox,
                entity -> entity instanceof ItemEntity || entity instanceof ExperienceOrb)) {
            Vec3 away = loose.position().subtract(x, loose.getY(), z)
                    .multiply(1.0D, 0.0D, 1.0D);
            if (away.lengthSqr() < 1.0E-5D) {
                double yaw = Math.toRadians(player.getYRot());
                away = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
            } else {
                away = away.normalize();
            }
            loose.push(away.x * 0.72D, 0.46D, away.z * 0.72D);
        }

        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(surface.below())),
                x, y + 0.18D, z, 64, radius * 0.60D, 0.40D,
                radius * 0.60D, 0.19D);
        level.playSound(null, surface, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 0.58F);
        ClawPhysicalEffects.onGroundSpikeImpact(player, new Vec3(x, y, z), radius, surface);
    }

    private static void spawnGroundSpikeField(ServerLevel level, Vec3 center,
                                               float baseYaw, double fieldRadius) {
        spawnGroundSpikeVisual(level, center.x, center.z, baseYaw);
        int[] ringCounts = {6};
        double[] ringScales = {1.0D};
        double yawOffset = Math.toRadians(baseYaw);
        for (int ring = 0; ring < ringCounts.length; ring++) {
            int count = ringCounts[ring];
            double ringRadius = fieldRadius * ringScales[ring];
            // A single broad ring leaves deliberate gaps between the oversized
            // clusters instead of turning the attack into a dense spike carpet.
            double stagger = Math.PI / count;
            for (int index = 0; index < count; index++) {
                double angle = yawOffset + stagger + Math.PI * 2.0D * index / count;
                double x = center.x + Math.cos(angle) * ringRadius;
                double z = center.z + Math.sin(angle) * ringRadius;
                spawnGroundSpikeVisual(level, x, z,
                        (float)Math.toDegrees(angle) + 90.0F);
            }
        }
    }

    private static void spawnGroundSpikeVisual(ServerLevel level, double x, double z,
                                               float yaw) {
        BlockPos surface = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(x, level.getMinBuildHeight(), z));
        GroundSpikeEntity visual = new GroundSpikeEntity(ModEntities.GROUND_SPIKE.get(), level);
        visual.moveTo(surface.getX() + 0.5D, surface.getY(), surface.getZ() + 0.5D,
                yaw, 0.0F);
        level.addFreshEntity(visual);
    }

    /** Epic Fight already synchronizes its lock-on target to the server player patch. */
    private static LivingEntity lockedGroundSpikeTarget(ServerPlayer player) {
        LivingEntity target = CombatTargeting.locked(player);
        double maxDistance = GroundSpikeRules.MAX_LOCKED_TARGET_DISTANCE*AttackReach.scale(player);
        if (target == null || !CombatTargeting.inReach(player,target,maxDistance)
                || !CombatTargeting.visible(player,target)) return null;
        return target;
    }

    private static void lockGroundSpikePose(ServerPlayer player) {
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(0.0D, velocity.y, 0.0D);
        player.hurtMarked = true;
    }

    private static boolean canUseClaws(BlacklightState state) {
        return state.isInfected() && state.isPrototypeMode()
                && state.getActivePower() == PowerForm.CLAWS;
    }

    private static boolean isComboAttack(ClawCombatState.Action action) {
        return action == ClawCombatState.Action.AUTO_1
                || action == ClawCombatState.Action.AUTO_2
                || action == ClawCombatState.Action.AUTO_3;
    }

    private static CombatAnimationSpec attackSpec(ClawCombatState.Action action) {
        return switch (action) {
            case AUTO_1 -> CombatAnimationSpec.CLAW_AUTO_1;
            case AUTO_2 -> CombatAnimationSpec.CLAW_AUTO_2;
            default -> CombatAnimationSpec.CLAW_AUTO_3;
        };
    }

    private static float clawDamage(BlacklightState state, float baseDamage) {
        return ClawDamageRules.applyDamageUpgrade(baseDamage,
                state.hasClawUpgrade(ClawUpgrade.DAMAGE));
    }

    private static void play(ServerPlayer player, PlayerAction animation) {
        if (animation == null) return;
        PlayerActions.get().play(player, animation, 0.0F);
        ClawAnimationPayload.Animation visual = ClawAnimationPayload.Animation.from(animation);
        if (visual != null) {
            // The presentation backend synchronizes spectators. This explicit self payload is
            // a reliable visual acknowledgement for the controlling client, whose input
            // and living-motion hooks can otherwise race the ordinary animator packet.
            PacketDistributor.sendToPlayer(player, new ClawAnimationPayload(visual));
        }
    }
}
