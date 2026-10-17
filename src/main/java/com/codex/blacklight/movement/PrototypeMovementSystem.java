package com.codex.blacklight.movement;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.client.tuning.AnimationPoseSettings;
import com.codex.blacklight.combat.ClawCombatState;
import com.codex.blacklight.combat.ClawDamageRules;
import com.codex.blacklight.combat.WeaponCombatSystem;
import com.codex.blacklight.animation.PlayerAction;
import com.codex.blacklight.animation.PlayerActions;
import com.codex.blacklight.mode.PrototypeModeSystem;
import com.codex.blacklight.network.ClawAnimationPayload;
import com.codex.blacklight.network.LandingVisualPayload;
import com.codex.blacklight.power.MovementUpgrade;
import com.codex.blacklight.power.ClawUpgrade;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-authoritative traversal controller shared by every Blacklight power form. */
public final class PrototypeMovementSystem {
    private static final DustParticleOptions CHARGED_TENDRIL_DARK =
            new DustParticleOptions(new Vector3f(0.055F, 0.002F, 0.004F), 0.72F);
    private static final DustParticleOptions CHARGED_TENDRIL_RED =
            new DustParticleOptions(new Vector3f(0.62F, 0.008F, 0.012F), 0.58F);

    private PrototypeMovementSystem() { }

    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onHopDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !PrototypeModeSystem.isActive(player)
                || !player.isAlive() || player.isPassenger() || player.isSpectator()
                || event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        var action=player.getData(ModAttachments.PLAYER_ACTION);
        if (player.getData(ModAttachments.MOVEMENT_STATE).hopInvulnerable()
                && action.clipId().equals("basic_dodge_roll") && action.active(player.level().getGameTime())
                && !WeaponCombatSystem.busy(player) && !com.codex.blacklight.city.HydraTransport.active(player))
            event.setCanceled(true);
    }

    /** Prototype physiology converts a hard fall into the outward landing impact. */
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BlacklightState blacklight = player.getData(ModAttachments.PLAYER_STATE);
        if (blacklight.isInfected() && blacklight.isPrototypeMode()) {
            event.setDamageMultiplier(0.0F);
        }
    }

    /** Handles edge-triggered inputs immediately instead of waiting for a later player tick. */
    public static void acceptInput(ServerPlayer player, boolean jumpHeld, boolean dashPressed,
                                   int forward, int sideways, boolean sprinting, boolean detach) {
        if (com.codex.blacklight.city.HydraTransport.active(player)||com.codex.blacklight.power.BiomassSystem.active(player) || !PrototypeModeSystem.isActive(player) || !player.isAlive()
                || player.isSpectator() || player.isDeadOrDying()
                || player.isPassenger() || player.isFallFlying() || player.getAbilities().flying
                || player.isInLava()) {
            // Inactive/dead/lava movement never arms traversal.
            return;
        }
        if (player.getData(ModAttachments.CONSUMING)&&com.codex.blacklight.combat.ConsumeVisual.blocksInput(player.getData(ModAttachments.GRAB_STAGE))) return;
        if(player.getData(ModAttachments.GRAB_STAGE)==com.codex.blacklight.combat.ConsumeVisual.THROW){jumpHeld=false;dashPressed=false;}
        if(WingFlightSystem.eligible(player)&&!WeaponCombatSystem.busy(player)){WingFlightSystem.input(player,jumpHeld,forward,sideways,sprinting,detach);return;}
        MovementState movement = player.getData(ModAttachments.MOVEMENT_STATE);
        boolean wasJumpHeld = movement.jumpHeld();
        if(player.isInWater()&&!WaterRunning.onSurface(player)) {
            if(movement.chargeTicks()>0)unpauseServerAnimator(player);
            movement.acceptInput(false,false,forward,sideways,sprinting,detach);
            movement.jumpInput().reset();movement.cancelCharge();movement.consumeDashRequest();
            return;
        }
        movement.acceptInput(jumpHeld, dashPressed, forward, sideways, sprinting, detach);
        movement.jumpInput().acceptInput(jumpHeld, player, movement.wallTraversal().attached());
        if (WeaponCombatSystem.busy(player)) {
            movement.jumpInput().cancelPendingCharge();
            WeaponCombatSystem.movementInput(player, jumpHeld && !wasJumpHeld);
            // Keep current movement intent for after the action, but do not queue
            // traversal edges that would replace its animation or movement track.
            movement.consumeDashRequest();
            return;
        }
        if(movement.hopping()){movement.jumpInput().cancelPendingCharge();return;}
        if(dashPressed&&player.onGround()&&movement.canHop()&&!jumpHeld
                &&!movement.wallTraversal().attached()&&player.getData(ModAttachments.GRAB_STAGE)==0){
            startTacticalHop(player,movement);movement.consumeDashRequest();return;
        }
        if (!movement.wallTraversal().attached() && movement.chargeTicks() == 0
                && movement.jumpInput().consumeChargeRequest(player)) {
            beginChargedJump(player, movement);
        } else if (wasJumpHeld && !jumpHeld && movement.chargeTicks() > 0) {
            releaseChargedJump(player, movement);
        }
    }

    private static void beginChargedJump(ServerPlayer player, MovementState movement) {
        if(movement.sprinting()&&movement.forwardHeld())movement.beginRunningCharge();else movement.beginCharge();
        // The crouch is an ActionAnimation played from the server: every client
        // sees it and Epic Fight's own tick never replaces an action with IDLE.
        // The explicit self payload is the same acknowledgement the groundspike
        // relies on — the controlling client's living-motion hooks otherwise race
        // the ordinary animator sync packet and the action never sticks locally.
        if(!movement.runningCharge()) {
            if(player.onGround()){
                player.setDeltaMovement(0,player.getDeltaMovement().y,0);
                player.hurtMarked=true;
            }
            play(player, PlayerAction.CHARGED_JUMP);
            PacketDistributor.sendToPlayer(player, new ClawAnimationPayload(ClawAnimationPayload.Animation.CHARGED_JUMP));
        }
        syncCharge(player,movement.chargeTicks());
        BlacklightNeoMod.LOGGER.debug("Started charged jump for {}",
                player.getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if(com.codex.blacklight.power.BiomassSystem.active(player)||WingFlightSystem.flying(player))return;
        if (player.getData(ModAttachments.CONSUMING)&&com.codex.blacklight.combat.ConsumeVisual.blocksInput(player.getData(ModAttachments.GRAB_STAGE))) return;
        MovementState movement = player.getData(ModAttachments.MOVEMENT_STATE);
        BlacklightState blacklight = player.getData(ModAttachments.PLAYER_STATE);
        if (!blacklight.isInfected() || !blacklight.isPrototypeMode()
                || player.isPassenger() || player.isFallFlying()
                || player.getAbilities().flying || player.isSpectator()
                || !player.isAlive() || player.isDeadOrDying()
                || player.isInWater() && !WaterRunning.onSurface(player) || player.isInLava()) {
            if (movement.chargeTicks() > 0) {
                // An interrupted charge must drop the action pose immediately instead
                // of letting the crouch clip play out to its 2.05 s end.
                unpauseServerAnimator(player);
                play(player, PlayerAction.JUMP_FALL);
            }
            // Infected but not in Prototype mode right now: restore vanilla stepping.
            if (blacklight.isInfected()) {
                var stepHeight = player.getAttribute(Attributes.STEP_HEIGHT);
                if (stepHeight != null && stepHeight.getBaseValue() != 0.6D) {
                    stepHeight.setBaseValue(0.6D);
                }
            }
            var waterInput=movement.wallInput();
            float waterYaw=movement.cameraYaw(player.getYRot());
            boolean swimming=blacklight.isInfected()&&blacklight.isPrototypeMode()&&player.isInWater()
                    &&player.isAlive()&&!player.isSpectator()&&!player.isPassenger()&&!player.getAbilities().flying;
            movement.reset();
            if(swimming) {
                movement.acceptInput(false,false,waterInput.forward(),waterInput.sideways(),waterInput.sprint(),waterInput.detach());
                movement.setCameraYaw(waterYaw);
                WaterRunning.riseToSurface(player);
            }
            clearGlideTrail(player);
            syncCharge(player,0);
            syncWallMotion(player, movement.wallTraversal());
            return;
        }

        // Prototype physiology steps straight over curbs, cars and one-block ledges.
        var stepHeight = player.getAttribute(Attributes.STEP_HEIGHT);
        double terrainStepHeight = com.codex.blacklight.power.UtilityPowerSystem.parkourAllowed(player) ? 1.0D : .6D;
        if (stepHeight != null && stepHeight.getBaseValue() != terrainStepHeight) {
            stepHeight.setBaseValue(terrainStepHeight);
        }
        int upgradeMask = blacklight.getMovementUpgradeMask();
        movement.tickHop();
        movement.setMaxAirDashes(MovementUpgrade.airDashCharges(upgradeMask));
        movement.tickWallJumpCooldown();
        MovementTuningSettings.load();
        movement.jumpInput().observeGround(player.onGround());
        if (!movement.jumpInput().canGlide(player) || WeaponCombatSystem.busy(player)
                || movement.wallTraversal().attached() || movement.dashTicks() > 0) {
            clearGlideTrail(player);
        }
        LandingImpact.Observation landing = movement.observeLanding(
                player.onGround(), player.getDeltaMovement().y, player.getY());
        if (WeaponCombatSystem.busy(player)) {
            movement.cancelHop();
            movement.jumpInput().cancelPendingCharge();
            // Weapon actions own their Coord (or their explicit airborne velocity).
            // Consume landing/input edges without a delayed jump, air dash, landing
            // animation, sprint impulse or wall attachment stealing that ownership.
            if (player.onGround()) movement.land();
            if (movement.chargeTicks() > 0) {
                movement.cancelCharge();
                unpauseServerAnimator(player);
            }
            movement.consumeDashRequest();
            movement.endDash();
            AIR_POUNCE_HITS.remove(player);
            movement.cancelLandingRecovery();
            movement.setRunningAnimation(false);
            movement.updateSprintCollision(false);
            movement.wallTraversal().reset();
            syncCharge(player,0);
            syncWallMotion(player, movement.wallTraversal());
            movement.finishInputTick();
            return;
        }
        if (player.onGround()) {
            movement.land();
            LandingImpact.Profile impact = WaterRunning.onSurface(player) ? LandingImpact.Profile.NONE
                    : PrototypeMovementEffects.onLanding(player, landing);
            LandingImpact.Strength strength = impact.strength();
            if (strength != LandingImpact.Strength.NONE) {
                movement.beginLandingRecovery(strength);
                play(player, strength==LandingImpact.Strength.LIGHT
                        &&player.getDeltaMovement().horizontalDistance()>.12
                        ?PlayerAction.LAND_MOVING:landingAnimation(strength));
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                        new LandingVisualPayload(player.getId(), strength,
                                player.blockPosition().below(),
                                (float)impact.fallDistance(), (float)impact.radius(),
                                player.getUUID().getMostSignificantBits()
                                        ^ player.level().getGameTime()));
            }
        }

        Vec3 forward = horizontalForward(player);
        if(movement.hopping()){
            movement.consumeDashRequest();movement.wallTraversal().reset();syncWallMotion(player,movement.wallTraversal());
            movement.finishInputTick();return;
        }
        // The groundspike action owns the body from charge to eruption: no traversal
        // impulses may fight the planted pose, or a sprinting player would keep
        // sliding forward while the spikes erupt around a moving center.
        var spikeAction = player.getData(ModAttachments.CLAW_COMBAT).action();
        if (spikeAction == ClawCombatState.Action.GROUND_SPIKE_CHARGING
                || spikeAction == ClawCombatState.Action.GROUND_SPIKE) {
            movement.wallTraversal().reset();
            syncWallMotion(player, movement.wallTraversal());
            movement.finishInputTick();
            return;
        }
        if (movement.consumeDashRequest() && !player.onGround() && movement.canAirDash()) {
            // An airborne pounce homes on the Epic Fight lock-on target when one is
            // held, exactly like Prototype's air pounce; otherwise it follows the
            // player's view direction.
            Vec3 dashDirection = lockedDashDirection(player, forward);
            movement.beginDash(dashDirection.x, dashDirection.z);
            player.setDeltaMovement(dashDirection.x * MovementTuningSettings.airDashSpeed(),
                    Math.max(0.04D, player.getDeltaMovement().y),
                    dashDirection.z * MovementTuningSettings.airDashSpeed());
            player.hurtMarked = true;
            play(player, PlayerAction.AIR_DASH);
            PrototypeMovementEffects.onAirDashStarted(player, dashDirection);
        }

        if (movement.dashTicks() > 0) {
            movement.wallTraversal().reset();
            syncWallMotion(player, movement.wallTraversal());
            Vec3 velocity = player.getDeltaMovement();
            double speed = MovementTuningSettings.airDashSpeed();
            LivingEntity lockedTarget = getLockedTarget(player);
            if (lockedTarget != null) {
                // Ballistic pounce: steer at the prey but clamp every tick's step to
                // the remaining distance, so the pounce can never cross the target
                // and flip direction — the old full-speed homing oscillated through
                // victims at 1.45 blocks per tick.
                Vec3 from = player.position().add(0.0D, player.getBbHeight() * 0.5D, 0.0D);
                Vec3 delta = lockedTarget.getBoundingBox().getCenter().subtract(from);
                double distance = delta.length();
                double hitRange = 0.5D * (player.getBbWidth() + lockedTarget.getBbWidth())
                        + 0.9D;
                if (distance <= hitRange) {
                    // Contact: resolve the slash and arrest the pounce on the spot
                    // instead of hovering several blocks away from the prey.
                    player.setDeltaMovement(0.0D, Math.min(velocity.y, 0.0D), 0.0D);
                    player.hurtMarked = true;
                    sweepAirPounceHits(player);
                    movement.endDash();
                    finishDashPose(player);
                    movement.finishInputTick();
                    return;
                }
                Vec3 direction = delta.normalize();
                double step = Math.min(speed, Math.max(0.05D, distance - hitRange * 0.35D));
                double climb = Mth.clamp(direction.y * step, -0.55D, 0.30D);
                player.setDeltaMovement(direction.x * step, climb, direction.z * step);
                player.resetFallDistance();
                player.hurtMarked = true;
                sweepAirPounceHits(player);
                movement.tickDash();
                if(movement.dashTicks()==0)finishDashPose(player);
                PrototypeMovementEffects.tickAirDash(player,
                        horizontalOf(direction, movement));
                movement.finishInputTick();
                return;
            }
            // No lock-on: straight dash along the captured view direction.
            Vec3 dashDirection = new Vec3(movement.dashDirectionX(), 0.0D,
                    movement.dashDirectionZ());
            player.setDeltaMovement(dashDirection.x * speed,
                    Math.max(-0.02D, velocity.y), dashDirection.z * speed);
            sweepAirPounceHits(player);
            movement.tickDash();
            if(movement.dashTicks()==0)finishDashPose(player);
            player.resetFallDistance();
            player.hurtMarked = true;
            PrototypeMovementEffects.tickAirDash(player, dashDirection);
            movement.finishInputTick();
            return;
        }
        AIR_POUNCE_HITS.remove(player);

        // Prototype-style traversal gives an encountered wall priority over jump
        // charging, so sprinting into a facade never freezes at the wall.
        boolean wallRunning = !WingFlightSystem.eligible(player)&&tickWallRun(player, movement, forward, upgradeMask);
        if (!wallRunning) tickChargedJump(player, movement);
        syncCharge(player,Math.max(movement.chargeTicks(),movement.wallTraversal().visualChargeTicks()));
        if (!wallRunning && movement.chargeTicks() > 0 && !movement.runningCharge()) {
            movement.finishInputTick();
            return;
        }

        if (!wallRunning) {
            tickGlide(player, movement, forward, upgradeMask);
            tickSprint(player, movement, forward, upgradeMask);
        }
        applyLandingRecovery(player, movement);
        movement.finishInputTick();
    }

    private static void tickChargedJump(ServerPlayer player, MovementState movement) {
        if (movement.chargeTicks() <= 0 && movement.jumpInput().consumeChargeRequest(player)) {
            beginChargedJump(player, movement);
        }
        if (movement.chargeTicks() <= 0) return;

        // Never manufacture a grounded state. If the player walks or is pushed off an
        // edge while charging, cancel the charge and let normal falling physics resume.
        if (!player.onGround()) {
            // Epic Fight and the client movement packet can make onGround flicker for
            // one or two ticks when the charge pose starts.  A small coyote window keeps
            // that transient from deleting every jump while still allowing a real ledge
            // fall to resume naturally without manufacturing a grounded state.
            if (movement.observeChargeOffGround()
                    > PrototypeLocomotion.CHARGE_GROUND_GRACE_TICKS) {
                movement.cancelCharge();
                unpauseServerAnimator(player);
                play(player, PlayerAction.JUMP_FALL);
                BlacklightNeoMod.LOGGER.debug(
                        "Cancelled charged jump for {} after sustained loss of ground",
                        player.getGameProfile().getName());
            }
            return;
        }
        movement.observeChargeOnGround();

        if (movement.jumpHeld()) {
            int maximum = MovementTuningSettings.jumpChargeTicks();
            boolean becameFullyCharged = movement.tickCharge(maximum);
            // Drive the server-side action to the same charge ratio and freeze it
            // there. Left alone the server animator would advance the clip to its
            // total time, trip isEnd() and synchronize a living-motion swap that
            // pops every client off the held crouch frame.
            if(!movement.runningCharge()) {
                holdServerChargePose(player, movement, maximum);
                Vec3 velocity = player.getDeltaMovement();
                // A stationary charge is planted, not a slowly braking walk.
                // Preserve vertical physics so removed support still causes a fall.
                player.setDeltaMovement(0.0D, velocity.y, 0.0D);
                player.hurtMarked = true;
            }
            if (movement.isFullyCharged(maximum)) {
                if (becameFullyCharged) {
                    BlacklightNeoMod.LOGGER.debug("Charged jump reached maximum for {} and is holding",
                            player.getGameProfile().getName());
                    player.level().playSound(null, player.blockPosition(),
                            SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS,
                            0.72F, 0.58F);
                }
                // Tendrils are attached to animated leg joints on the client.
            }
            return;
        }
    }

    private static void releaseChargedJump(ServerPlayer player, MovementState movement) {
        unpauseServerAnimator(player);
        int held = movement.releaseCharge();
        syncCharge(player,0);
        // MC tap threshold; the original FIG charge unit is not claimed to be seconds.
        if(held<=3){
            // Jump remains jump. Evasion is a separate short Run release.
            player.setDeltaMovement(player.getDeltaMovement().x,.42,player.getDeltaMovement().z);
            player.setOnGround(false);player.hurtMarked=true;
            play(player,PlayerAction.JUMP_RISE);
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
            return;
        }
        double charge = Math.min(1.0D, held / (double) MovementTuningSettings.jumpChargeTicks());
        double jumpVelocity = MovementTuningSettings.jumpLaunchVelocity(charge,
                player.getData(ModAttachments.PLAYER_STATE).getMovementUpgradeMask());
        Vec3 forward = horizontalForward(player);
        double launch = 0.20D + charge * 0.42D;
        var velocity=player.getDeltaMovement();
        player.setDeltaMovement(velocity.x+forward.x*launch,jumpVelocity,velocity.z+forward.z*launch);
        player.setOnGround(false);
        player.hurtMarked = true;
        play(player, PlayerAction.JUMP_RISE);
        PrototypeMovementEffects.onChargedJumpReleased(player, charge);
        BlacklightNeoMod.LOGGER.debug("Released charged jump for {} at {} ticks (velocity={})",
                player.getGameProfile().getName(), held, jumpVelocity);
    }

    private static void startTacticalHop(ServerPlayer player,MovementState movement){
        var input=movement.wallInput();double yaw=Math.toRadians(movement.cameraYaw(player.getYRot()));
        double x=-Math.sin(yaw)*input.forward()-Math.cos(yaw)*input.sideways();
        double z=Math.cos(yaw)*input.forward()-Math.sin(yaw)*input.sideways();
        var direction=new Vec3(x,0,z);
        if(direction.lengthSqr()>.01)direction=direction.normalize();
        else direction=new Vec3(Math.sin(yaw),0,-Math.cos(yaw));
        movement.beginHop();movement.endDash();movement.wallTraversal().reset();unpauseServerAnimator(player);syncCharge(player,0);
        player.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));
        player.yBodyRot=player.getYRot();
        // Low forward dive: ~0.32 blocks of rise, not the vanilla 1.25-block jump.
        player.setDeltaMovement(direction.scale(.62).add(0,.19,0));
        player.setOnGround(false);player.hurtMarked=true;
        play(player,PlayerAction.TACTICAL_HOP);
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
    }

    private static final Map<ServerPlayer, WallTraversal.Mode> WALL_MODES = new WeakHashMap<>();

    private static boolean tickWallRun(ServerPlayer player, MovementState movement,
                                       Vec3 forward, int upgradeMask) {
        WallTraversal wall = movement.wallTraversal();
        WallTraversal.Mode previous = wall.mode();
        if(com.codex.blacklight.combat.ShieldBorerSystem.groundDrive(player,movement.wallInput().sprint(),movement.wallInput().forward())){
            wall.reset();syncWallMotion(player,wall);return false;
        }
        WallTraversal.Frame frame = wall.tick(movement.wallInput(), WallContacts.sample(player, wall),
                player.getY(), player.getDeltaMovement().horizontalDistance(),
                MovementTuningSettings.wallClimbVelocity(), MovementUpgrade.wallMultiplier(upgradeMask));
        if (frame.handled()) {
            if (movement.jumpHeld()) movement.jumpInput().claimCharge();
            if (movement.chargeTicks() > 0) {
                movement.cancelCharge(); unpauseServerAnimator(player);
            }
            movement.cancelLandingRecovery();
            player.setDeltaMovement(frame.x(), frame.y(), frame.z());
            player.setOnGround(false); player.resetFallDistance(); player.hurtMarked = true;
            player.setSprinting(movement.sprinting() && (frame.mode() == WallTraversal.Mode.RUN
                    || frame.mode() == WallTraversal.Mode.LEAP || WallTraversal.crossingTop(frame.mode())));
            movement.setMode(switch (frame.mode()) {
                case RUN -> MovementState.Mode.WALL_RUN;
                case LEAP, MANTLE, VAULT, TOP, PARKOUR -> MovementState.Mode.WALL_LEAP;
                case CLING, CLIMB -> MovementState.Mode.WALL_CLIMB;
                default -> MovementState.Mode.NORMAL;
            });
            if (frame.mode()!=previous) {
                play(player, wallAnimation(frame.mode()));
                Vec3 into=new Vec3(-wall.normalX(),0,-wall.normalZ());
                if (frame.mode()==WallTraversal.Mode.LEAP || frame.mode()==WallTraversal.Mode.VAULT)
                    PrototypeMovementEffects.onWallMantle(player,into);
                else if (previous==WallTraversal.Mode.NONE && frame.mode()!=WallTraversal.Mode.PARKOUR)
                    PrototypeMovementEffects.onWallRunStarted(player,into);
            }
            if (frame.mode()==WallTraversal.Mode.RUN)
                PrototypeMovementEffects.tickWallRun(player,new Vec3(-wall.normalX(),0,-wall.normalZ()));
        } else if (previous!=WallTraversal.Mode.NONE) {
            movement.setMode(MovementState.Mode.NORMAL);
            if (wall.finishedOnTop() || player.onGround()) {
                boolean running = movement.sprinting() && movement.forwardHeld();
                player.setSprinting(running);
                movement.setRunningAnimation(running);
                play(player, running ? PlayerAction.CLAW_SPRINT
                        : movement.wallInput().moving() ? PlayerAction.CLAW_WALK
                        : PlayerAction.CLAW_IDLE);
            } else {
                play(player,PlayerAction.JUMP_FALL);
            }
        }
        syncWallMotion(player,wall);
        return frame.handled();
    }

    private static PlayerAction wallAnimation(WallTraversal.Mode mode) {
        return switch (mode) {
            case RUN -> PlayerAction.WALL_RUN;
            case CLING -> PlayerAction.WALL_CLING;
            case CLIMB -> PlayerAction.WALL_CLIMB;
            case LEAP -> PlayerAction.WALL_LEAP;
            case MANTLE -> PlayerAction.WALL_MANTLE;
            case VAULT -> PlayerAction.WALL_VAULT;
            case TOP -> PlayerAction.WALL_TOP;
            case PARKOUR -> PlayerAction.PARKOUR_SMALL;
            default -> PlayerAction.JUMP_RISE;
        };
    }

    private static void syncWallMotion(ServerPlayer player, WallTraversal wall) {
        WallPoseState.set(player,wall.mode(),wall.normalX(),wall.normalZ());
        WallTraversal.Mode previous=WALL_MODES.put(player,wall.mode());
        if (previous!=wall.mode() || (wall.attached() && player.tickCount%10==0))
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    new com.codex.blacklight.network.WallMotionPayload(player.getId(),wall.mode().ordinal(),(float)wall.normalX(),(float)wall.normalZ()));
    }

    private static void clearGlideTrail(ServerPlayer player) {
        if (player.getData(ModAttachments.GLIDE_UNTIL) != 0L) {
            player.setData(ModAttachments.GLIDE_UNTIL, 0L);
            player.syncData(ModAttachments.GLIDE_UNTIL);
        }
    }

    private static void tickGlide(ServerPlayer player, MovementState movement,
                                  Vec3 forward, int upgradeMask) {
        Vec3 velocity = player.getDeltaMovement();
        if (com.codex.blacklight.power.UtilityPowerSystem.active(player,com.codex.blacklight.power.PowerForm.ARMOR)
                || movement.chargeTicks() > 0 || !movement.jumpInput().canGlide(player)) {
            movement.setMode(MovementState.Mode.NORMAL);
            movement.setGlideSpeed(0);
            clearGlideTrail(player);
            return;
        }
        double cruise = MovementTuningSettings.glideMaxSpeed()
                * MovementUpgrade.glideSpeedScale(upgradeMask);
        boolean entering = movement.mode() != MovementState.Mode.GLIDE;
        boolean braking = movement.brakingGlide();
        double actual = velocity.horizontalDistance();
        // Vanilla air drag runs before this hook. Retain the authoritative glide
        // speed, and ease inherited dash/sprint momentum down instead of clipping it.
        double speed = entering ? Math.max(actual, cruise * (braking ? .45 : .85))
                : Math.max(actual, movement.glideSpeed());
        double desired = cruise * (braking ? .45 : 1);
        speed = speed > desired ? Math.max(desired, speed * (braking ? .90 : .987))
                : Math.min(desired, speed + cruise * .065);
        Vec3 direction = actual > .08 ? new Vec3(velocity.x / actual, 0, velocity.z / actual) : forward;
        // Bound the turn rate; looking backwards must not cancel the velocity vector.
        double angle = Math.atan2(direction.x * forward.z - direction.z * forward.x,
                direction.dot(forward));
        double turn = Math.clamp(angle, -.14, .14);
        double cos = Math.cos(turn), sin = Math.sin(turn);
        Vec3 horizontal = new Vec3(direction.x * cos - direction.z * sin, 0,
                direction.x * sin + direction.z * cos).scale(speed);
        player.setDeltaMovement(horizontal.x,
                Math.max(velocity.y, -MovementTuningSettings.glideFallSpeed()
                        * MovementUpgrade.glideFallScale(upgradeMask)), horizontal.z);
        player.resetFallDistance();
        player.hurtMarked = true;
        movement.setMode(MovementState.Mode.GLIDE);
        movement.setGlideSpeed(speed);
        if (entering) PrototypeMovementEffects.onGlideStarted(player, horizontal.normalize());
        if (entering || player.tickCount % 3 == 0) {
            player.setData(ModAttachments.GLIDE_UNTIL, player.level().getGameTime() + 5);
            player.syncData(ModAttachments.GLIDE_UNTIL);
        }
        PrototypeMovementEffects.tickGlide(player, horizontal.normalize());
    }

    private static void tickSprint(ServerPlayer player, MovementState movement,
                                   Vec3 forward, int upgradeMask) {
        boolean shouldSprint = (player.onGround() || WaterRunning.onSurface(player))
                && movement.sprinting() && movement.forwardHeld();
        if (!shouldSprint) {
            movement.clearSprintDrive();
            movement.updateSprintCollision(false);
            boolean shortStep = movement.sprinting() && movement.forwardHeld()
                    && player.getDeltaMovement().y <= .035 && player.getDeltaMovement().y >= -.5
                    && TerrainTraversal.supportBelow(player, 1.05);
            if (movement.runningAnimation() && !shortStep) {
                movement.setRunningAnimation(false);
            }
            player.setSprinting(movement.sprinting() && player.onGround());
            return;
        }
        player.setSprinting(true);
        if (!movement.runningAnimation()) {
            movement.setRunningAnimation(true);
            BlacklightNeoMod.LOGGER.debug("Started Prototype sprint for {}",
                    player.getGameProfile().getName());
            PrototypeMovementEffects.onSprintStarted(player, forward);
        }
        Vec3 velocity = player.getDeltaMovement();
          double max = MovementTuningSettings.sprintMaxSpeed()
                  * MovementUpgrade.sprintMultiplier(upgradeMask);
          if(com.codex.blacklight.power.UtilityPowerSystem.active(player,com.codex.blacklight.power.PowerForm.ARMOR))max*=.75;
        var input=movement.wallInput();
        Vec3 horizontal=TraversalSteering.sprint(player,velocity,max,MovementTuningSettings.sprintAcceleration(),input.forward(),input.sideways());
        double impactSpeed = horizontal.horizontalDistance();
        Vec3 collisionStart = player.position().add(0.0D, 0.82D, 0.0D);
        boolean colliding = player.level().clip(new ClipContext(
                collisionStart, collisionStart.add(forward.scale(0.72D)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType()
                != HitResult.Type.MISS;
        colliding = colliding && !TerrainTraversal.canStepPast(player, forward, .72);
        if (colliding) {
            // Collision resolution already stops movement. A server-only 72% brake
            // fought local prediction and also treated one-block steps as impacts.
            if (movement.updateSprintCollision(true)
                    && impactSpeed >= PrototypeLocomotion.RUN_ANIMATION_SPEED) {
                PrototypeMovementEffects.onSprintCollision(player, forward, impactSpeed);
            }
        } else {
            movement.updateSprintCollision(false);
        }
        player.setDeltaMovement(horizontal.x, velocity.y, horizontal.z);
        player.hurtMarked = true;
        movement.setMode(MovementState.Mode.NORMAL);
        PrototypeMovementEffects.tickSprint(player, forward, impactSpeed);
        if (impactSpeed >= BODY_CHECK_MIN_SPEED) {
            sweepSprintBodyCheck(player, forward, impactSpeed);
        }
    }

    private static Vec3 horizontalForward(ServerPlayer player) {
        return TraversalSteering.forward(player);
    }

    /** Horizontal part of a (possibly vertical) homing direction, for trail effects. */
    private static Vec3 horizontalOf(Vec3 direction, MovementState movement) {
        Vec3 horizontal = direction.multiply(1.0D, 0.0D, 1.0D);
        if (horizontal.lengthSqr() < 1.0E-5D) {
            return new Vec3(movement.dashDirectionX(), 0.0D, movement.dashDirectionZ());
        }
        return horizontal.normalize();
    }

    /** Air pounces prefer the Epic Fight lock-on target over the raw view direction. */
    private static Vec3 lockedDashDirection(ServerPlayer player, Vec3 fallback) {
        LivingEntity target = getLockedTarget(player);
        if (target == null) {
            BlacklightNeoMod.LOGGER.debug("Air pounce uses view direction (no locked target)");
            return fallback;
        }
        Vec3 direction = target.position().subtract(player.position())
                .multiply(1.0D, 0.0D, 1.0D);
        if (direction.lengthSqr() < 1.0E-5D) return fallback;
        direction = direction.normalize();
        BlacklightNeoMod.LOGGER.debug("Air pounce homes on locked target {} dir=({}, {})",
                target.getName().getString(), direction.x, direction.z);
        return direction;
    }

    /** The living target the player currently locks on, or null when none is valid. */
    private static LivingEntity getLockedTarget(ServerPlayer player) {
        LivingEntity target = com.codex.blacklight.combat.CombatTargeting.locked(player);
        if (target == null || !com.codex.blacklight.combat.CombatTargeting.inReach(player,target,64)
                || !com.codex.blacklight.combat.CombatTargeting.visible(player,target)) return null;
        return target;
    }

    /** Per-player targets already clawed during the current air pounce. */
    private static final Map<ServerPlayer, Set<LivingEntity>> AIR_POUNCE_HITS = new WeakHashMap<>();

    /** Super-sprint body-check: minimum speed and per-target hit cooldown. */
    private static final double BODY_CHECK_MIN_SPEED = 0.45D;
    private static final int BODY_CHECK_COOLDOWN_TICKS = 12;
    private static final Map<ServerPlayer, Map<UUID, Integer>> BODY_CHECK_HITS = new WeakHashMap<>();

    /**
     * A Prototype sprinting at super speed shoulders mobs out of the way: victims in
     * the path take speed-scaled damage and are launched forward-off their feet.
     */
    private static void sweepSprintBodyCheck(ServerPlayer player, Vec3 forward, double speed) {
        float intensity = PrototypeLocomotion.cinematicSpeedRatio(speed,
                MovementTuningSettings.sprintMaxSpeed() * MovementUpgrade.sprintMultiplier(
                        player.getData(ModAttachments.PLAYER_STATE).getMovementUpgradeMask()));
        AABB sweep = player.getBoundingBox().inflate(0.35D, 0.10D, 0.35D)
                .expandTowards(forward.scale(0.55D));
        Map<UUID, Integer> cooldowns = BODY_CHECK_HITS.computeIfAbsent(player,
                ignored -> new HashMap<>());
        int hits = 0;
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(
                LivingEntity.class, sweep,
                target -> com.codex.blacklight.combat.CombatTargeting.damageable(player,target)
                        && com.codex.blacklight.combat.CombatTargeting.visible(player,target)).stream()
                        .sorted(com.codex.blacklight.combat.CombatTargeting.contactPriority(player)).toList()) {
            Integer last = cooldowns.get(target.getUUID());
            if (last != null && player.tickCount - last < BODY_CHECK_COOLDOWN_TICKS) continue;
            float damage = 5.0F + 9.0F * intensity;
            if (!target.hurt(player.damageSources().playerAttack(player), damage)) continue;
            cooldowns.put(target.getUUID(), player.tickCount);
            double shove = 0.85D + 0.85D * intensity;
            target.push(forward.x * shove, 0.32D + 0.30D * intensity, forward.z * shove);
            target.hurtMarked = true;
            PrototypeMovementEffects.onSprintBodyCheck(player, target, forward, intensity);
            if (++hits >= 3) break;
        }
        if (cooldowns.size() > 24) {
            cooldowns.entrySet().removeIf(entry -> player.tickCount - entry.getValue() > 200);
        }
    }

    /** Airborne claw pounce deals damage to whatever the player sweeps through. */
    private static void sweepAirPounceHits(ServerPlayer player) {
        BlacklightState state = player.getData(ModAttachments.PLAYER_STATE);
        if (state.getActivePower() != PowerForm.CLAWS) return;
        Set<LivingEntity> hit = AIR_POUNCE_HITS.computeIfAbsent(player,
                ignored -> new HashSet<>());
        AABB sweep = player.getBoundingBox().inflate(0.95D, 0.55D, 0.95D);
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(
                LivingEntity.class, sweep,
                target -> com.codex.blacklight.combat.CombatTargeting.damageable(player,target)
                        && com.codex.blacklight.combat.CombatTargeting.visible(player,target) && !hit.contains(target))) {
            hit.add(target);
            float damage = ClawDamageRules.applyDamageUpgrade(12.0F,
                    state.hasClawUpgrade(ClawUpgrade.DAMAGE));
            if (target.hurt(player.damageSources().playerAttack(player), damage)) {
                Vec3 push = target.position().subtract(player.position())
                        .multiply(1.0D, 0.0D, 1.0D);
                if (push.lengthSqr() < 1.0E-5D) {
                    push = new Vec3(0.0D, 0.0D, 1.0D);
                } else {
                    push = push.normalize();
                }
                target.push(push.x * 0.85D, 0.42D, push.z * 0.85D);
            }
        }
    }

    private static PlayerAction landingAnimation(
            LandingImpact.Strength strength) {
        return switch (strength) {
            case LIGHT -> PlayerAction.LAND_LIGHT;
            case HEAVY -> PlayerAction.LAND_HEAVY;
            case DEVASTATING -> PlayerAction.LAND_DEVASTATING;
            default -> null;
        };
    }

    private static void syncCharge(ServerPlayer player,int charge){
        if(player.getData(ModAttachments.JUMP_CHARGE)!=charge){player.setData(ModAttachments.JUMP_CHARGE,charge);player.syncData(ModAttachments.JUMP_CHARGE);}
    }

    private static void finishDashPose(ServerPlayer player){
        if(!player.onGround())
            play(player,PlayerAction.AIR_DASH_BRAKE);
    }

    private static void applyLandingRecovery(ServerPlayer player, MovementState movement) {
        if (movement.landingRecoveryTicks() <= 0) return;
        Vec3 velocity = player.getDeltaMovement();
        double damping = movement.landingStrength() == LandingImpact.Strength.DEVASTATING
                ? 0.52D : 0.68D;
        player.setDeltaMovement(velocity.x * damping, velocity.y, velocity.z * damping);
        player.hurtMarked = true;
        movement.tickLandingRecovery();
    }

    /**
     * Drives the server-side charge action to the current charge ratio and freezes
     * it there, epsilon-short of the total time so the animation player never trips
     * isEnd(). Every charge-exit path must call unpauseServerAnimator.
     */
    private static void holdServerChargePose(ServerPlayer player, MovementState movement,
                                             int maximum) {
        PlayerActions.get().holdJumpCharge(player,
                Math.min(1.0F, movement.chargeTicks() / (float) maximum), movement.isFullyCharged(maximum));
    }

    /** Releases a charge pose through the selected presentation engine. */
    private static void unpauseServerAnimator(ServerPlayer player) {
        PlayerActions.get().resume(player);
    }

    /** Draws two animated biological helices around the legs while maximum charge is held. */
    private static void emitFullChargeTendrils(ServerPlayer player) {
        Vec3 forward = horizontalForward(player);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        double phase = player.tickCount * 0.42D;
        for (int leg = -1; leg <= 1; leg += 2) {
            Vec3 center = player.position().add(right.scale(leg * 0.16D));
            for (int segment = 0; segment < 7; segment++) {
                double angle = phase + segment * 0.92D + (leg > 0 ? Math.PI : 0.0D);
                double radius = 0.19D + 0.025D * Math.sin(phase * 0.7D + segment);
                Vec3 radial = right.scale(Math.cos(angle) * radius)
                        .add(forward.scale(Math.sin(angle) * radius));
                double y = player.getY() + 0.12D + segment * 0.17D;
                player.serverLevel().sendParticles(
                        segment % 3 == 0 ? CHARGED_TENDRIL_RED : CHARGED_TENDRIL_DARK,
                        center.x + radial.x, y, center.z + radial.z,
                        1, 0.012D, 0.018D, 0.012D, 0.0D);
            }
        }
    }

    private static void play(ServerPlayer player,
                             PlayerAction animation) {
        if (animation == null) return;
        PlayerActions.get().play(player, animation, 0.08F);
    }
}
