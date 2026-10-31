package com.codex.blacklight.animation;

import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * GeckoLib animation carrier for the biological overlay. The independent
 * player pose engine owns the armature; this object owns morph bones,
 * tentacles and keyed effects.
 */
public final class PrototypeAvatar implements GeoAnimatable {
    public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.blacklight.idle");
    public static final RawAnimation SPRINT = RawAnimation.begin().thenLoop("animation.blacklight.sprint");
    public static final RawAnimation WALL_RUN = RawAnimation.begin().thenLoop("animation.blacklight.wall_run");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean moving;
    private boolean sprinting;
    private boolean wallRunning;
    // Game-time source injected by the rendering host each frame. Wall-clock time
    // would keep looping animations running while the pause menu is open and would
    // drift from the server tick rate.
    private double clientTick;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<PrototypeAvatar> locomotion = new AnimationController<>(this, "locomotion", 4, state -> {
            if (wallRunning) return state.setAndContinue(WALL_RUN);
            if (sprinting) return state.setAndContinue(SPRINT);
            return state.setAndContinue(IDLE);
        });

        AnimationController<PrototypeAvatar> actions = new AnimationController<>(this, "actions", 2,
                state -> software.bernie.geckolib.core.object.PlayState.STOP)
                .triggerableAnim("transform_claws", RawAnimation.begin().thenPlay("animation.blacklight.transform_claws"))
                .triggerableAnim("claw_combo", RawAnimation.begin().thenPlay("animation.blacklight.claw_combo"))
                .triggerableAnim("claw_groundspike", RawAnimation.begin().thenPlay("animation.blacklight.claw_groundspike"))
                .triggerableAnim("consume", RawAnimation.begin().thenPlay("animation.blacklight.consume"))
                .triggerableAnim("air_dash", RawAnimation.begin().thenPlay("animation.blacklight.air_dash"));

        controllers.add(locomotion);
        controllers.add(actions);
    }

    public void updateLocomotion(boolean moving, boolean sprinting, boolean wallRunning) {
        this.moving = moving;
        this.sprinting = sprinting;
        this.wallRunning = wallRunning;
    }

    /** Called by the render host once per client frame with game time (not wall clock). */
    public void setClientTick(double tick) {
        this.clientTick = tick;
    }

    public boolean isMoving() {
        return moving;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object context) {
        return clientTick;
    }
}
