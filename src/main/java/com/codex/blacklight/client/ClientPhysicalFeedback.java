package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.movement.MovementTuningSettings;
import com.codex.blacklight.movement.PrototypeLocomotion;
import com.codex.blacklight.power.MovementUpgrade;
import com.codex.blacklight.network.PhysicalFeedbackPayload;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Bounded, short-lived camera impulses driven by authoritative server actions. */
@EventBusSubscriber(modid = BlacklightNeoMod.MOD_ID, value = Dist.CLIENT)
public final class ClientPhysicalFeedback {
    private static float previousStrength;
    private static float strength;
    private static float fovImpulse;
    private static float previousFovImpulse;
    private static float previousVerticalKick;
    private static float verticalKick;
    private static float previousRunStrength;
    private static float runStrength;
    private static float previousWallStrength;
    private static float wallStrength;
    private static int ticksRemaining;
    private static int phase;
    private static float bladeKick,previousBladeKick;

    private ClientPhysicalFeedback() {
    }

    public static void accept(PhysicalFeedbackPayload payload) {
        if(com.codex.blacklight.client.camera.PrototypeChaseCamera.ownsView()) {
            com.codex.blacklight.client.camera.PrototypeChaseCamera.impact(payload);return;
        }
        float effectScale = switch (payload.effect()) {
            case LIGHT_LANDING -> 0.22F;
            case JUMP_LAUNCH, CLAW_HIT -> 0.38F;
            case AIR_DASH, HEAVY_LANDING -> 0.72F;
            case CLAW_HEAVY -> 0.88F;
            case DEVASTATING_LANDING, GROUND_SPIKE -> 1.0F;
            case SPRINT_COLLISION -> 0.82F;
            case SPRINT_START -> 0.55F;
            case WALL_RUN_START -> 0.72F;
            case WALL_MANTLE -> 0.82F;
            case BLADE_CUT, BLADE_RETURN -> .55F;
            case BLADE_HEAVY -> .90F;
        };
        if(payload.effect()==PhysicalFeedbackPayload.Effect.BLADE_CUT||payload.effect()==PhysicalFeedbackPayload.Effect.BLADE_RETURN||payload.effect()==PhysicalFeedbackPayload.Effect.BLADE_HEAVY){
            bladeKick=(payload.effect()==PhysicalFeedbackPayload.Effect.BLADE_RETURN?-1:1)*payload.intensity();
            previousBladeKick=bladeKick;
        }
        strength = Math.max(strength, payload.intensity() * effectScale);
        previousStrength = Math.max(previousStrength, strength);
        fovImpulse = Math.max(fovImpulse, payload.intensity() * effectScale);
        if (payload.effect() == PhysicalFeedbackPayload.Effect.GROUND_SPIKE
                || payload.effect() == PhysicalFeedbackPayload.Effect.HEAVY_LANDING
                || payload.effect() == PhysicalFeedbackPayload.Effect.DEVASTATING_LANDING
                || payload.effect() == PhysicalFeedbackPayload.Effect.SPRINT_COLLISION
                || payload.effect() == PhysicalFeedbackPayload.Effect.WALL_RUN_START
                || payload.effect() == PhysicalFeedbackPayload.Effect.WALL_MANTLE) {
            float kickScale = switch (payload.effect()) {
                case GROUND_SPIKE -> 2.15F;
                case DEVASTATING_LANDING -> 1.62F;
                case HEAVY_LANDING -> 0.82F;
                case SPRINT_COLLISION -> 1.05F;
                case WALL_RUN_START -> 0.72F;
                case WALL_MANTLE -> 1.18F;
                default -> 0.0F;
            };
            verticalKick = Math.max(verticalKick, payload.intensity() * kickScale);
            previousVerticalKick = Math.max(previousVerticalKick, verticalKick);
        }
        int duration = switch (payload.effect()) {
            case GROUND_SPIKE, DEVASTATING_LANDING -> 10;
            case HEAVY_LANDING -> 8;
            case SPRINT_COLLISION -> 6;
            case SPRINT_START -> 7;
            case WALL_RUN_START -> 8;
            case WALL_MANTLE -> 7;
            case BLADE_CUT,BLADE_RETURN -> 3;
            case BLADE_HEAVY -> 5;
            default -> 7;
        };
        ticksRemaining = Math.max(ticksRemaining, duration);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                ||!com.codex.blacklight.mode.PrototypeModeSystem.isActive(minecraft.player)
                ||com.codex.blacklight.client.camera.PrototypeChaseCamera.ownsView()) {
            reset();
            return;
        }
        previousStrength = strength;
        previousVerticalKick = verticalKick;
        previousRunStrength = runStrength;
        previousWallStrength = wallStrength;
        previousFovImpulse = fovImpulse;
        previousBladeKick=bladeKick;bladeKick*=.4F;
        if (ticksRemaining > 0) {
            ticksRemaining--;
            strength *= 0.72F;
            fovImpulse *= 0.68F;
            phase++;
        } else {
            strength = 0.0F;
            fovImpulse = 0.0F;
        }
        verticalKick *= 0.56F;

        float runTarget = 0.0F;
        var blacklight = minecraft.player.getData(ModAttachments.PLAYER_STATE);
        if (blacklight.isInfected() && blacklight.isPrototypeMode()
                && minecraft.player.onGround()
                && ClientMovementEvents.isRunningForAttack(minecraft)
                && !ClientClawEvents.isGroundSpikeActionActive()) {
            runTarget = PrototypeLocomotion.sprintVisualIntensity(
                    minecraft.player.getDeltaMovement().horizontalDistance(),
                    MovementTuningSettings.sprintMaxSpeed()
                            * MovementUpgrade.sprintMultiplier(blacklight.getMovementUpgradeMask()));
        }
        float smoothing = runTarget > runStrength ? 0.28F : 0.16F;
        runStrength += (runTarget - runStrength) * smoothing;
        float wallTarget = ClientMovementEvents.isWallRunning() ? 1.0F : 0.0F;
        wallStrength += (wallTarget - wallStrength) * (wallTarget > wallStrength ? 0.36F : 0.24F);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if(com.codex.blacklight.client.camera.CameraCompatibility.externalOwnsView())return;
        if(com.codex.blacklight.client.camera.HydraCinematicCamera.active())return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || event.getCamera().getEntity() != minecraft.player) return;
        if (!com.codex.blacklight.mode.PrototypeModeSystem.isActive(minecraft.player)) return;
        if(com.codex.blacklight.client.camera.PrototypeChaseCamera.ownsView())return;
        float partial = (float)event.getPartialTick();
        float amplitude = Mth.lerp(partial, previousStrength, strength);
        float kick = Mth.lerp(partial, previousVerticalKick, verticalKick);
        float running = Mth.lerp(partial, previousRunStrength, runStrength);
        float wall = Mth.lerp(partial, previousWallStrength, wallStrength);
        float blade=Mth.lerp(partial,previousBladeKick,bladeKick);
        if (amplitude < 0.001F && kick < 0.001F && running < 0.001F && wall < 0.001F&&Math.abs(blade)<.001F) return;
        double time = phase + partial;
        double stride = (minecraft.player.tickCount + partial) * 1.08D;
        event.setPitch(event.getPitch() + kick-Math.abs(blade)*.5F
                + (float)Math.sin(time * 2.73D) * amplitude * 1.35F
                + (float)Math.sin(stride * 2.0D) * running * 0.34F
                + (float)Math.sin(stride * 2.4D) * wall * 0.42F);
        event.setYaw(event.getYaw() + (float)Math.cos(time * 2.11D) * amplitude * 0.62F
                + (float)Math.sin(stride) * running * 0.14F);
        event.setRoll(event.getRoll() +blade*1.15F+ (float)Math.sin(time * 3.37D) * amplitude * 0.82F
                + (float)Math.cos(stride) * running * 0.68F
                + (float)Math.sin(stride * 1.2D) * wall * 0.92F);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if(!event.usedConfiguredFov()||com.codex.blacklight.client.camera.CameraCompatibility.externalOwnsView())return;
        if(com.codex.blacklight.client.camera.HydraCinematicCamera.active())return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || event.getCamera().getEntity() != minecraft.player
                ||!com.codex.blacklight.mode.PrototypeModeSystem.isActive(minecraft.player)
                || (fovImpulse < 0.001F && runStrength < 0.001F
                && wallStrength < 0.001F)) return;
        if(com.codex.blacklight.client.camera.PrototypeChaseCamera.ownsView())return;
        float partial = (float)event.getPartialTick();
        float running = Mth.lerp(partial, previousRunStrength, runStrength);
        float wall = Mth.lerp(partial, previousWallStrength, wallStrength);
        float impulse = Mth.lerp(partial, previousFovImpulse, fovImpulse);
        event.setFOV(event.getFOV() + impulse * 1.65D + running * 4.5D + wall * 3.2D);
    }

    private static void reset() {
        previousStrength = 0.0F;
        strength = 0.0F;
        fovImpulse = 0.0F;
        previousFovImpulse = 0.0F;
        previousVerticalKick = 0.0F;
        verticalKick = 0.0F;
        previousRunStrength = 0.0F;
        runStrength = 0.0F;
        previousWallStrength = 0.0F;
        wallStrength = 0.0F;
        ticksRemaining = 0;
        phase = 0;
        bladeKick=previousBladeKick=0;
    }
}
