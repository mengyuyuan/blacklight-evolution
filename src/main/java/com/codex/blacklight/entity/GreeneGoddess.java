package com.codex.blacklight.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Command-summoned second form for viewing the animated assembly at its native scale. */
public final class GreeneGoddess extends Entity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.greene_goddess.idle");
    private static final RawAnimation STRUGGLE = RawAnimation.begin().thenLoop("animation.greene_goddess.struggle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public GreeneGoddess(EntityType<? extends GreeneGoddess> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 0, state -> state.setAndContinue(IDLE)));
        controllers.add(new AnimationController<>(this, "colony", 0, state -> state.setAndContinue(STRUGGLE)));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // The streamer extends to the side of the origin and rotates with the entity.
    @Override public AABB getBoundingBoxForCulling() {
        return new AABB(getX()-15, getY()-1, getZ()-15, getX()+15, getY()+26, getZ()+15);
    }

    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 256.0 * 256.0; }
}
