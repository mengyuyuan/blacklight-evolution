package com.codex.blacklight.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Short-lived, damage-free visual. Groundspike damage is calculated once by the server. */
public final class GroundSpikeEntity extends Entity implements GeoEntity {
    public static final int ERUPTION_DURATION_TICKS = 22;
    private static final RawAnimation ERUPT = RawAnimation.begin().thenPlay("animation.blacklight.ground_spike.erupt");
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public GroundSpikeEntity(EntityType<? extends GroundSpikeEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > ERUPTION_DURATION_TICKS) discard();
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "erupt", 0, state -> state.setAndContinue(ERUPT)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
