package com.codex.blacklight.animation;

import com.codex.blacklight.combat.LocalWeaponMotion;
import com.codex.blacklight.combat.WeaponMove;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Independent server animation authority. It never delegates movement or combat to another mod. */
public final class NativePlayerActionBackend implements PlayerActionBackend {
    @Override public void playVisual(ServerPlayer player,String clipId,float transitionSeconds) {
        publish(player,MotionLibrary.canonical(clipId),Math.max(0,transitionSeconds),0,false);
    }
    @Override public void play(ServerPlayer player, PlayerAction action, float transitionSeconds) {
        if (action == null) return;
        String id = MotionLibrary.resolve(action);
        boolean loop = switch (action) {
            case IDLE, CLAW_IDLE, CLAW_WALK, CLAW_SPRINT, WALL_RUN, WALL_CLIMB, WALL_CLING,
                    GLIDE, GRAB_HOLD, GRAB_BLADE_HOLD -> true;
            default -> false;
        };
        publish(player, id, Math.max(0, transitionSeconds), 0, loop);
    }

    @Override public void playWeapon(ServerPlayer player, WeaponMove move, boolean charging, float clipOffsetSeconds) {
        if (move == null) return;
        String id = charging ? LocalWeaponMotion.find(move.id).flatMap(LocalWeaponMotion.Definition::charge)
                .orElseThrow(() -> new IllegalStateException("Missing charge for " + move.id)).animation() : move.id;
        publish(player, id, MotionLibrary.transition(id), Math.max(0, clipOffsetSeconds), false);
    }

    private static void publish(ServerPlayer player, String id, float transition, float offset, boolean loop) {
        float duration = LandingMotionPolicy.leaseDuration(id,MotionLibrary.duration(id));
        var previous = player.getData(ModAttachments.PLAYER_ACTION);
        var state = new NativeActionState(id, player.level().getGameTime(), Math.min(duration, offset),
                transition, duration, player.getYRot(), loop, -1, previous.revision() + 1);
        player.setData(ModAttachments.PLAYER_ACTION, state);
        player.syncData(ModAttachments.PLAYER_ACTION);
    }

    @Override public void holdJumpCharge(ServerPlayer player, float progress, boolean fullyCharged) {
        var old = player.getData(ModAttachments.PLAYER_ACTION);
        if (!old.clipId().equals(MotionLibrary.resolve(PlayerAction.CHARGED_JUMP))) return;
        float hold = Math.clamp(old.duration() * Math.clamp(progress, 0, 1), 0, Math.max(0, old.duration() - .001F));
        if (old.heldTime() == hold) return;
        player.setData(ModAttachments.PLAYER_ACTION, new NativeActionState(old.clipId(), old.started(),
                old.clipOffset(), old.transition(), old.duration(), old.yaw(), false, hold, old.revision()));
        player.syncData(ModAttachments.PLAYER_ACTION);
    }

    @Override public void resume(ServerPlayer player) {
        var old = player.getData(ModAttachments.PLAYER_ACTION);
        if (old.heldTime() < 0) return;
        player.setData(ModAttachments.PLAYER_ACTION, new NativeActionState(old.clipId(), player.level().getGameTime(),
                old.heldTime(), 0, old.duration(), old.yaw(), old.looping(), -1, old.revision()));
        player.syncData(ModAttachments.PLAYER_ACTION);
    }

    @Override public void synchronizeMode(ServerPlayer player, boolean enabled) {
        if (!enabled) {
            clear(player);
            com.codex.blacklight.combat.CombatTargeting.requestLock(player, -1);
        }
    }

    public static void clear(ServerPlayer player) {
        var old = player.getData(ModAttachments.PLAYER_ACTION);
        if (old.clipId().isEmpty()) return;
        player.setData(ModAttachments.PLAYER_ACTION,
                new NativeActionState("", 0, 0, 0, 0, 0, false, -1, old.revision() + 1));
        if (!player.isRemoved()) player.syncData(ModAttachments.PLAYER_ACTION);
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var state = player.getData(ModAttachments.PLAYER_ACTION);
        if (state.clipId().isEmpty()) return;
        if(!player.isAlive()||player.isSpectator()){clear(player);return;}
        if(LandingMotionPolicy.isLanding(state.clipId())) {
            var velocity=player.getDeltaMovement();
            boolean grounded=player.onGround()||velocity.y<=.035
                    &&com.codex.blacklight.movement.TerrainTraversal.supportBelow(player,.15);
            boolean moving=player.getData(ModAttachments.MOVEMENT_STATE).wallInput().moving()
                    ||velocity.horizontalDistance()>.12;
            if(LandingMotionPolicy.shouldRelease(state.clipId(),state.seconds(player.level().getGameTime()),grounded,moving)) {
                clear(player);return;
            }
        }
        if (state.clipId().equals(MotionLibrary.resolve(PlayerAction.CLAW_GROUNDSPIKE))
                && player.getData(ModAttachments.WEAPON_VISUAL).spec() == null) {
            var combat = player.getData(ModAttachments.CLAW_COMBAT);
            float hold = switch (combat.action()) {
                case GROUND_SPIKE_CHARGING -> Math.min(.55F, combat.elapsedTicks() / 20F);
                case GROUND_SPIKE -> Math.max(0, .55F - Math.max(0,combat.elapsedTicks()
                        -com.codex.blacklight.entity.GroundSpikeEntity.ERUPTION_DURATION_TICKS) / 20F);
                default -> -1;
            };
            if (hold >= 0) {
                hold = Math.min(hold, state.duration());
                if (state.heldTime() != hold) {
                    player.setData(ModAttachments.PLAYER_ACTION, new NativeActionState(state.clipId(), state.started(),
                            state.clipOffset(), state.transition(), state.duration(), state.yaw(), false, hold, state.revision()));
                    player.syncData(ModAttachments.PLAYER_ACTION);
                }
                return;
            }
            if (state.heldTime() >= 0) { clear(player); return; }
        }
        // Charge, pursuit and paired consume have their own authoritative phase clock.
        if (!state.active(player.level().getGameTime())
                && player.getData(ModAttachments.WEAPON_VISUAL).spec() == null
                && !player.getData(ModAttachments.CONSUMING)) clear(player);
    }

    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target) target.syncData(ModAttachments.PLAYER_ACTION);
    }

    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { clear(player); com.codex.blacklight.combat.CombatTargeting.requestLock(player,-1); }
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { clear(player); com.codex.blacklight.combat.CombatTargeting.requestLock(player,-1); }
    }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { clear(player); com.codex.blacklight.combat.CombatTargeting.requestLock(player,-1); }
    }
}
